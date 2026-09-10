package com.example.toread.ui.screens

import com.example.toread.library.BookshelfBook
import com.example.toread.ui.text.ToreadStrings

internal data class FallbackCoverPresentation(
    val mark: String,
    val paletteIndex: Int,
)

internal fun selectContinueReadingBook(books: List<BookshelfBook>): BookshelfBook? =
    books
        .asSequence()
        .filter { it.progress < FinishedProgressThreshold }
        .filter { it.lastReadAt != null || it.progress > 0.0 }
        .maxWithOrNull(
            compareBy<BookshelfBook> { it.lastReadAt != null }
                .thenBy { it.lastReadAt.orEmpty() },
        )

internal fun fallbackCoverPresentation(
    title: String,
    format: String,
    bookHash: String,
): FallbackCoverPresentation {
    val titleMark = title
        .filter(Char::isLetterOrDigit)
        .take(FallbackCoverMarkLength)
    val formatMark = format
        .filter(Char::isLetterOrDigit)
        .uppercase()
        .take(FallbackCoverMarkLength)
    return FallbackCoverPresentation(
        mark = titleMark.ifEmpty { formatMark.ifEmpty { ToreadStrings.FallbackCoverUnknownMark } },
        paletteIndex = stablePaletteIndex(bookHash),
    )
}

private fun stablePaletteIndex(bookHash: String): Int {
    val stableHash = bookHash.fold(0) { hash, character ->
        hash * 31 + character.code
    }
    return (stableHash and Int.MAX_VALUE) % FallbackCoverPaletteCount
}

private const val FinishedProgressThreshold = 0.995
private const val FallbackCoverMarkLength = 2
private const val FallbackCoverPaletteCount = 3
