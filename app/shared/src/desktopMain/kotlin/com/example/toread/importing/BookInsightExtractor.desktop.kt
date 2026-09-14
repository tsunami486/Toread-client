package com.example.toread.importing

import com.example.toread.metadata.BookInsight
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader

/**
 * Desktop implementation: the shared parsers do the thinking, this only opens the local file.
 *
 * EPUB and TXT need no dependency beyond the JDK; the PDF information dictionary is read with the
 * PDFBox the reader already ships.
 */
actual class BookInsightExtractor actual constructor() {
    actual suspend fun extract(
        bookFilePath: String,
        format: String,
        fileName: String?,
    ): BookInsight? = withContext(Dispatchers.IO) {
        runCatching {
            when (format.lowercase()) {
                "epub" -> ZipFile(bookFilePath).use { zip ->
                    extractEpubInsight(archive = ZipBookArchive(zip), fileName = fileName)
                }
                "txt" -> extractPlainTextInsight(
                    fileName = fileName,
                    text = readPlainTextHead(bookFilePath),
                )
                "pdf" -> readPdfInsight(bookFilePath, fileName)
                else -> null
            }
        }.getOrNull()
    }
}

internal class ZipBookArchive(private val zip: ZipFile) : BookArchive {
    override val entryNames: List<String> = zip.entries().toList().map { entry -> entry.name }

    override fun readBytes(entryName: String): ByteArray? =
        zip.getEntry(entryName)?.let { entry ->
            zip.getInputStream(entry).use { input -> input.readBytes() }
        }
}

/** How much of a plain-text book is scanned for a 内容简介 marker; the rest is never read. */
internal const val MaximumTxtInsightBytes = 512 * 1024

private fun readPlainTextHead(path: String): String {
    val file = File(path)
    if (!file.isFile) return ""
    return FileInputStream(file).use { input ->
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (buffer.size() < MaximumTxtInsightBytes) {
            val read = input.read(chunk, 0, minOf(chunk.size, MaximumTxtInsightBytes - buffer.size()))
            if (read <= 0) break
            buffer.write(chunk, 0, read)
        }
        decodePlainText(buffer.toByteArray())
    }
}

/** Mirrors the reader's text decoding: UTF-8 with BOM, then UTF-8, then GBK, then a lossy UTF-8. */
internal fun decodePlainText(bytes: ByteArray): String {
    if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
    ) {
        return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
    }
    return tryDecodePlainText(bytes, Charsets.UTF_8)
        ?: tryDecodePlainText(bytes, Charset.forName("GBK"))
        ?: bytes.toString(Charsets.UTF_8)
}

private fun tryDecodePlainText(bytes: ByteArray, charset: Charset): String? =
    try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

private fun readPdfInsight(path: String, fileName: String?): BookInsight? {
    val file = File(path)
    if (!file.isFile) return null
    return Loader.loadPDF(file).use { document ->
        val information = runCatching { document.documentInformation }.getOrNull()
        pdfInsight(
            subject = information?.subject,
            keywords = information?.keywords,
            fileName = fileName,
        )
    }
}
