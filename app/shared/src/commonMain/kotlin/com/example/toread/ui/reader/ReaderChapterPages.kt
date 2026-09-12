package com.example.toread.ui.reader

/**
 * Where the visible page sits inside its chapter.
 *
 * [pageNumber] is one-based, so the first page of a chapter reads as 1 / N.
 */
internal data class ReaderChapterPagePosition(
    val pageNumber: Int,
    val pageCount: Int,
) {
    init {
        require(pageCount >= 1) { "pageCount must be positive" }
        require(pageNumber in 1..pageCount) { "pageNumber must be inside the chapter" }
    }
}

/**
 * Chapter-relative position of [pageIndex] inside the fully paginated [pages] list.
 *
 * Returns null when the list is empty or the index is outside it. Chapter membership comes from the
 * pagination's own `chapterOrd`, so consecutive pages of the same chapter stay one block even when a
 * chapter is split across the reflow window.
 */
internal fun chapterPagePosition(
    pages: List<ReaderPageSlice>,
    pageIndex: Int,
): ReaderChapterPagePosition? {
    if (pageIndex !in pages.indices) return null
    val chapterOrd = pages[pageIndex].chapterOrd
    val firstIndex = chapterFirstPageIndex(pages, pageIndex) ?: return null
    val lastIndex = pages.indexOfLast { it.chapterOrd == chapterOrd }
    if (lastIndex < firstIndex) return null
    return ReaderChapterPagePosition(
        pageNumber = pageIndex - firstIndex + 1,
        pageCount = lastIndex - firstIndex + 1,
    )
}

/** Global page index of the [chapterPageNumber]-th page of the chapter that contains [pageIndex]. */
internal fun pageIndexForChapterPage(
    pages: List<ReaderPageSlice>,
    pageIndex: Int,
    chapterPageNumber: Int,
): Int? {
    if (chapterPageNumber < 1) return null
    val firstIndex = chapterFirstPageIndex(pages, pageIndex) ?: return null
    val target = firstIndex + chapterPageNumber - 1
    if (target !in pages.indices) return null
    return target.takeIf { pages[it].chapterOrd == pages[pageIndex].chapterOrd }
}

/** First page index of the chapter containing [pageIndex]. */
internal fun chapterFirstPageIndex(
    pages: List<ReaderPageSlice>,
    pageIndex: Int,
): Int? {
    if (pageIndex !in pages.indices) return null
    val chapterOrd = pages[pageIndex].chapterOrd
    var index = pageIndex
    while (index > 0 && pages[index - 1].chapterOrd == chapterOrd) {
        index--
    }
    return index
}
