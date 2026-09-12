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

/**
 * A chapter-page seek the reading panel committed on release and the reader has not published yet.
 *
 * The panel keeps showing [target] until the reader reports it, because the seek is asynchronous:
 * dropping the released value immediately would flash the thumb back to the page the reader still
 * displays before the requested page arrives. [chapterOrd] scopes the wait to the chapter the seek was
 * made in, so jumping to another chapter can never leave a stale target behind.
 */
internal data class ChapterPageSeek(
    val chapterOrd: Int,
    val target: Int,
)

/** True once the reader published the seek's target or left the chapter the seek was made in. */
internal fun chapterPageSeekSettled(
    seek: ChapterPageSeek,
    currentChapterOrd: Int,
    chapterPages: ReaderChapterPagePosition?,
): Boolean = currentChapterOrd != seek.chapterOrd || chapterPages?.pageNumber == seek.target

/**
 * Page the reading panel shows: the thumb being dragged, the page a released seek is still waiting for,
 * or the reader's own page. The middle term is what keeps a seek from flashing back to the page the
 * reader has not left yet.
 */
internal fun chapterSliderPage(
    draggedPage: Int?,
    pendingSeek: ChapterPageSeek?,
    chapterPages: ReaderChapterPagePosition?,
): Int? = draggedPage ?: pendingSeek?.target ?: chapterPages?.pageNumber

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
