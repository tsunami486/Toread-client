package com.example.toread.ui.screens

import com.example.toread.library.BookshelfBook
import com.example.toread.reader.EpubLocator
import com.example.toread.reader.EpubPublicationIndex
import com.example.toread.reader.PdfLocator
import com.example.toread.reader.TxtLocator
import com.example.toread.reader.toEpubLocatorChapterTitle
import com.example.toread.reader.toEpubHrefLookupKey
import com.example.toread.reader.toLocatorChapterTitle
import com.example.toread.ui.text.ToreadStrings
import kotlin.math.abs

internal data class ContinueReadingPresentation(
    val locationLabel: String?,
    val actionLabel: String,
)

internal fun continueReadingPresentation(
    book: BookshelfBook,
    resolvedEpubChapterTitle: String? = null,
): ContinueReadingPresentation {
    val locationLabel = when (val locator = book.locator) {
        is EpubLocator -> locator.chapterTitle.toEpubLocatorChapterTitle(locator.chapterIndex)
            ?: resolvedEpubChapterTitle.toEpubLocatorChapterTitle(locator.chapterIndex)
        is PdfLocator -> ToreadStrings.chapterPage(locator.pageIndex.coerceAtLeast(0))
        is TxtLocator -> locator.chapterTitle
            .toLocatorChapterTitle()
            ?.takeUnless { chapterTitle ->
                chapterTitle.equals(book.title.toLocatorChapterTitle(), ignoreCase = true)
            }
        null -> null
    }
    return ContinueReadingPresentation(
        locationLabel = locationLabel,
        actionLabel = ToreadStrings.ContinueReading,
    )
}

internal fun resolveContinueReadingEpubChapterTitle(
    locator: EpubLocator,
    publication: EpubPublicationIndex,
): String? {
    if (publication.chapters.isEmpty()) return null
    val chapter = locator.chapterHref
        .takeIf { it.isNotBlank() && it != LocalTextChapterHref }
        ?.let { href ->
            val hrefLookupKey = href.toEpubHrefLookupKey()
            publication.chapters
                .filter { it.href.toEpubHrefLookupKey() == hrefLookupKey }
                .minByOrNull { abs(it.ord - locator.chapterIndex) }
        }
        ?: publication.chapters.firstOrNull { it.ord == locator.chapterIndex }
        ?: return null

    return chapter.title.toEpubLocatorChapterTitle(chapter.ord)
}
private const val LocalTextChapterHref = "local-text"
