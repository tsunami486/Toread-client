package com.example.toread.reader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

actual class PdfDocumentRenderer actual constructor() {
    private val operationLock = ReentrantLock()
    private var document: PDDocument? = null
    private var renderer: PDFRenderer? = null

    actual suspend fun open(filePath: String): PdfDocumentInfo = withContext(Dispatchers.IO) {
        val operationContext = currentCoroutineContext()
        operationLock.withLock {
            operationContext.ensureActive()
            closeLocked()

            val file = File(filePath)
            if (!file.isFile || !file.canRead()) {
                throw PdfDocumentOpenException("无法读取 PDF 文件，请确认文件仍然存在且有访问权限。")
            }

            try {
                val loadedDocument = Loader.loadPDF(file)
                var retainDocument = false
                try {
                    if (loadedDocument.isEncrypted) {
                        throw PdfDocumentOpenException("该 PDF 已加密或受密码保护，当前版本暂不支持打开。")
                    }
                    if (loadedDocument.numberOfPages <= 0) {
                        throw PdfDocumentOpenException("该 PDF 没有可阅读的页面。")
                    }

                    document = loadedDocument
                    renderer = PDFRenderer(loadedDocument).apply {
                        setSubsamplingAllowed(true)
                    }
                    retainDocument = true
                    PdfDocumentInfo(pageCount = loadedDocument.numberOfPages)
                } finally {
                    if (!retainDocument) {
                        runCatching { loadedDocument.close() }
                    }
                }
            } catch (error: PdfDocumentOpenException) {
                throw error
            } catch (error: InvalidPasswordException) {
                throw PdfDocumentOpenException(
                    message = "该 PDF 已加密或受密码保护，当前版本暂不支持打开。",
                    cause = error,
                )
            } catch (error: IOException) {
                throw PdfDocumentOpenException(
                    message = "无法打开 PDF，文件可能已损坏或无法读取。",
                    cause = error,
                )
            } catch (error: RuntimeException) {
                throw PdfDocumentOpenException(
                    message = "无法解析 PDF，文件可能已损坏或格式不受支持。",
                    cause = error,
                )
            }
        }
    }

    actual suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        targetHeightPx: Int,
    ): ImageBitmap = withContext(Dispatchers.IO) {
        val operationContext = currentCoroutineContext()
        operationLock.withLock {
            operationContext.ensureActive()
            if (targetWidthPx <= 0 || targetHeightPx <= 0) {
                throw PdfPageRenderException("PDF 渲染区域的宽度和高度必须大于零。")
            }

            val currentDocument = document
                ?: throw PdfPageRenderException("PDF 尚未打开，无法渲染页面。")
            val currentRenderer = renderer
                ?: throw PdfPageRenderException("PDF 渲染器尚未初始化。")
            if (pageIndex !in 0 until currentDocument.numberOfPages) {
                throw PdfPageRenderException(
                    "PDF 页码超出范围：第 ${pageIndex + 1} 页，共 ${currentDocument.numberOfPages} 页。",
                )
            }

            try {
                val page = currentDocument.getPage(pageIndex)
                val cropBox = page.cropBox
                val isQuarterTurn = Math.floorMod(page.rotation, 180) == 90
                val pageWidth = if (isQuarterTurn) cropBox.height else cropBox.width
                val pageHeight = if (isQuarterTurn) cropBox.width else cropBox.height
                if (
                    pageWidth <= 0f ||
                    pageHeight <= 0f ||
                    !pageWidth.isFinite() ||
                    !pageHeight.isFinite()
                ) {
                    throw PdfPageRenderException("PDF 第 ${pageIndex + 1} 页的页面尺寸无效。")
                }

                val scale = min(
                    targetWidthPx.toFloat() / pageWidth,
                    targetHeightPx.toFloat() / pageHeight,
                )
                if (scale <= 0f || !scale.isFinite()) {
                    throw PdfPageRenderException("PDF 第 ${pageIndex + 1} 页的渲染比例无效。")
                }

                currentRenderer
                    .renderImage(pageIndex, scale, ImageType.RGB)
                    .toComposeImageBitmap()
            } catch (error: PdfPageRenderException) {
                throw error
            } catch (error: IOException) {
                throw PdfPageRenderException(
                    message = "无法渲染 PDF 第 ${pageIndex + 1} 页，文件可能已损坏。",
                    cause = error,
                )
            } catch (error: RuntimeException) {
                throw PdfPageRenderException(
                    message = "渲染 PDF 第 ${pageIndex + 1} 页失败。",
                    cause = error,
                )
            }
        }
    }

    actual fun close() {
        operationLock.withLock {
            closeLocked()
        }
    }

    private fun closeLocked() {
        val currentDocument = document
        renderer = null
        document = null
        try {
            currentDocument?.close()
        } catch (_: Exception) {
            // References are already cleared, so close() stays safe to call repeatedly.
        }
    }
}
