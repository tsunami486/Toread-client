package com.example.toread.ui.reader

import com.example.toread.library.BookshelfBook
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.reader.BookLocator
import com.example.toread.reader.EpubLocator
import com.example.toread.reader.PdfLocator
import com.example.toread.reader.TxtLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
internal fun BookLocator.toPageIndex(pageCount: Int): Int {
    if (pageCount <= 1) return 0
    return (globalProgress.coerceIn(0.0, 1.0) * (pageCount - 1)).roundToInt()
}

internal fun normalizePageIndex(index: Int, pageCount: Int, isDesktop: Boolean): Int {
    if (pageCount <= 1) return 0
    val bounded = index.coerceIn(0, pageCount - 1)
    return if (isDesktop) bounded - (bounded % 2) else bounded
}

internal fun Int.pageProgress(pageCount: Int): Double =
    if (pageCount <= 1) 0.0 else toDouble() / (pageCount - 1).toDouble()

internal fun saveReaderProgress(
    scope: CoroutineScope,
    progressRepository: ReadingProgressRepository?,
    book: BookshelfBook,
    pageIndex: Int,
    pageCount: Int,
    charOffset: Int,
    deviceId: String,
) {
    val repository = progressRepository ?: return
    scope.launch {
        val globalProgress = pageIndex.pageProgress(pageCount)
        val locator = when (book.format.lowercase()) {
            "epub" -> EpubLocator(
                chapterHref = "local-text",
                chapterIndex = pageIndex,
                chapterProgress = 0.0,
                globalProgress = globalProgress,
            )
            "pdf" -> PdfLocator(pageIndex = pageIndex, pageOffsetY = 0.0, globalProgress = globalProgress)
            else -> TxtLocator(
                charOffset = charOffset.toLong(),
                paragraphIndex = null,
                globalProgress = globalProgress,
            )
        }
        repository.saveLocalProgress(
            bookHash = book.bookHash,
            format = book.format,
            locator = locator,
            deviceId = deviceId,
        )
    }
}
