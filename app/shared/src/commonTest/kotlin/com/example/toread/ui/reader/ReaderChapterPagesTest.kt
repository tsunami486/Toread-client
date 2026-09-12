package com.example.toread.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderChapterPagesTest {
    @Test
    fun positionCountsPagesOfTheChapterThatOwnsTheVisiblePage() {
        // Chapter 4 owns pages 2..4, chapter 5 owns 5..6.
        val pages = listOf(
            page(chapterOrd = 3),
            page(chapterOrd = 4),
            page(chapterOrd = 4),
            page(chapterOrd = 4),
            page(chapterOrd = 5),
            page(chapterOrd = 5),
        )

        assertEquals(ReaderChapterPagePosition(1, 3), chapterPagePosition(pages, 1))
        assertEquals(ReaderChapterPagePosition(2, 3), chapterPagePosition(pages, 2))
        assertEquals(ReaderChapterPagePosition(3, 3), chapterPagePosition(pages, 3))
        assertEquals(ReaderChapterPagePosition(1, 2), chapterPagePosition(pages, 4))
        assertEquals(ReaderChapterPagePosition(2, 2), chapterPagePosition(pages, 5))
    }

    @Test
    fun positionIsMissingOutsideThePageList() {
        val pages = listOf(page(chapterOrd = 0))

        assertNull(chapterPagePosition(emptyList(), 0))
        assertNull(chapterPagePosition(pages, -1))
        assertNull(chapterPagePosition(pages, 1))
    }

    @Test
    fun draggingMapsAChapterPageBackToItsGlobalIndex() {
        val pages = listOf(
            page(chapterOrd = 3),
            page(chapterOrd = 4),
            page(chapterOrd = 4),
            page(chapterOrd = 4),
            page(chapterOrd = 5),
        )

        assertEquals(1, pageIndexForChapterPage(pages, pageIndex = 2, chapterPageNumber = 1))
        assertEquals(3, pageIndexForChapterPage(pages, pageIndex = 2, chapterPageNumber = 3))
        // Never crosses into the neighbouring chapter.
        assertNull(pageIndexForChapterPage(pages, pageIndex = 2, chapterPageNumber = 4))
        assertNull(pageIndexForChapterPage(pages, pageIndex = 2, chapterPageNumber = 0))
        assertNull(pageIndexForChapterPage(pages, pageIndex = 9, chapterPageNumber = 1))
    }

    private fun page(chapterOrd: Int): ReaderPageSlice =
        ReaderPageSlice(
            chapterTitle = "第 ${chapterOrd + 1} 章",
            startOffset = chapterOrd * 1_000,
            endOffset = chapterOrd * 1_000 + 900,
            chapterOrd = chapterOrd,
        )
}
