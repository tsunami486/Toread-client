package com.example.toread.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

actual class PdfDocumentRenderer actual constructor() {
    private val operationLock = ReentrantLock()
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    actual suspend fun open(filePath: String): PdfDocumentInfo = withContext(Dispatchers.IO) {
        val operationContext = currentCoroutineContext()
        operationLock.withLock {
            operationContext.ensureActive()
            closeLocked()

            if (filePath.isBlank()) {
                throw PdfDocumentOpenException("无法打开 PDF：文件路径为空。")
            }

            val file = File(filePath)
            if (!file.exists()) {
                throw PdfDocumentOpenException("无法打开 PDF：文件不存在。")
            }
            if (!file.isFile) {
                throw PdfDocumentOpenException("无法打开 PDF：指定路径不是文件。")
            }
            if (!file.canRead()) {
                throw PdfDocumentOpenException("无法打开 PDF：没有文件读取权限。")
            }

            var openedDescriptor: ParcelFileDescriptor? = null
            var openedRenderer: PdfRenderer? = null
            try {
                openedDescriptor = ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                )
                openedRenderer = PdfRenderer(openedDescriptor)

                if (openedRenderer.pageCount <= 0) {
                    throw PdfDocumentOpenException("无法打开 PDF：文档中没有可阅读的页面。")
                }

                fileDescriptor = openedDescriptor
                renderer = openedRenderer
                openedDescriptor = null
                openedRenderer = null

                PdfDocumentInfo(pageCount = renderer!!.pageCount)
            } catch (error: PdfDocumentOpenException) {
                closeOpenedResources(openedRenderer, openedDescriptor, error)
                throw error
            } catch (error: Exception) {
                closeOpenedResources(openedRenderer, openedDescriptor, error)
                throw PdfDocumentOpenException(
                    message = "无法打开 PDF：文件可能已损坏、已加密或格式不受支持。",
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
            val activeRenderer = renderer
                ?: throw PdfPageRenderException("无法渲染 PDF 页面：尚未打开文档。")

            if (targetWidthPx <= 0 || targetHeightPx <= 0) {
                throw PdfPageRenderException("无法渲染 PDF 页面：目标尺寸必须大于 0。")
            }
            if (pageIndex !in 0 until activeRenderer.pageCount) {
                throw PdfPageRenderException(
                    "无法渲染 PDF 页面：页码超出范围，有效范围为 0 到 ${activeRenderer.pageCount - 1}。",
                )
            }

            var bitmap: Bitmap? = null
            try {
                activeRenderer.openPage(pageIndex).use { page ->
                    if (page.width <= 0 || page.height <= 0) {
                        throw PdfPageRenderException(
                            "无法渲染 PDF 第 ${pageIndex + 1} 页：页面尺寸无效。",
                        )
                    }

                    val scale = min(
                        targetWidthPx.toFloat() / page.width.toFloat(),
                        targetHeightPx.toFloat() / page.height.toFloat(),
                    )
                    val bitmapWidth = ceil(page.width * scale)
                        .toInt()
                        .coerceIn(1, targetWidthPx)
                    val bitmapHeight = ceil(page.height * scale)
                        .toInt()
                        .coerceIn(1, targetHeightPx)
                    bitmap = Bitmap.createBitmap(
                        bitmapWidth,
                        bitmapHeight,
                        Bitmap.Config.ARGB_8888,
                    ).apply {
                        eraseColor(Color.WHITE)
                    }

                    val transform = Matrix().apply {
                        setScale(scale, scale)
                    }

                    page.render(
                        bitmap,
                        null,
                        transform,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                    )
                }

                checkNotNull(bitmap).asImageBitmap()
            } catch (error: PdfPageRenderException) {
                bitmap?.recycle()
                throw error
            } catch (error: Exception) {
                bitmap?.recycle()
                throw PdfPageRenderException(
                    message = "无法渲染 PDF 第 ${pageIndex + 1} 页。",
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
        val rendererToClose = renderer
        val descriptorToClose = fileDescriptor
        renderer = null
        fileDescriptor = null

        try {
            rendererToClose?.close()
        } catch (_: Exception) {
            // The file descriptor still needs to be closed if PdfRenderer.close() fails.
        }

        try {
            descriptorToClose?.close()
        } catch (_: Exception) {
            // All references are already cleared, so repeated close() calls remain safe.
        }
    }

    private fun closeOpenedResources(
        openedRenderer: PdfRenderer?,
        openedDescriptor: ParcelFileDescriptor?,
        originalError: Throwable,
    ) {
        try {
            openedRenderer?.close()
        } catch (closeError: Exception) {
            originalError.addSuppressed(closeError)
        }

        try {
            openedDescriptor?.close()
        } catch (closeError: Exception) {
            originalError.addSuppressed(closeError)
        }
    }
}
