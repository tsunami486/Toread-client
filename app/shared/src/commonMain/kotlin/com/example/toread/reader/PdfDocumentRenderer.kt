package com.example.toread.reader

import androidx.compose.ui.graphics.ImageBitmap

data class PdfDocumentInfo(
    val pageCount: Int,
)

class PdfDocumentOpenException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class PdfPageRenderException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

expect class PdfDocumentRenderer() {
    suspend fun open(filePath: String): PdfDocumentInfo

    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        targetHeightPx: Int,
    ): ImageBitmap

    fun close()
}
