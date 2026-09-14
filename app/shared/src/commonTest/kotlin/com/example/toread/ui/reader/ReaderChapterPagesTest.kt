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

    @Test
    fun aReleasedSeekIsSettledOnlyWhenTheReaderPublishesIt() {
        val seek = ChapterPageSeek(chapterOrd = 4, target = 12)

        // Still on the page the reader showed when the seek was released.
        assertEquals(false, chapterPageSeekSettled(seek, 4, ReaderChapterPagePosition(5, 31)))
        // The reader published the requested page.
        assertEquals(true, chapterPageSeekSettled(seek, 4, ReaderChapterPagePosition(12, 31)))
        // Another page of the same chapter is not the requested one.
        assertEquals(false, chapterPageSeekSettled(seek, 4, ReaderChapterPagePosition(11, 31)))
        // The chapter changed, so the pending target belongs to a chapter the reader has left.
        assertEquals(true, chapterPageSeekSettled(seek, 5, ReaderChapterPagePosition(12, 31)))
        // No measurement yet: keep waiting instead of flashing back.
        assertEquals(false, chapterPageSeekSettled(seek, 4, null))
    }

    @Test
    fun aReleasedSeekKeepsShowingTheRequestedPageUntilTheReaderPublishesIt() {
        val hostPage = ReaderChapterPagePosition(5, 31)
        val seek = ChapterPageSeek(chapterOrd = 4, target = 12)

        // Following the reader, dragging, released while the reader is still on the old page, and
        // finally the reader's own page once it arrives.
        assertEquals(5, chapterSliderPage(null, null, hostPage))
        assertEquals(12, chapterSliderPage(12, null, hostPage))
        assertEquals(12, chapterSliderPage(null, seek, hostPage))
        assertEquals(12, chapterSliderPage(null, seek, ReaderChapterPagePosition(12, 31)))
    }

    @Test
    fun aMismatchingOrdinalCannotRetireASeekTheReaderHasNotMovedFor() {
        // The row a stale release would produce: an ordinal that no longer matches, released while the
        // reader was still showing page 5.
        val seek = ChapterPageSeek(chapterOrd = 4, target = 12, originPage = 5)

        // The reader has not moved off the released page, so the latch has to hold: dropping it here is
        // exactly the flash back to the page the reader still displays.
        assertEquals(false, chapterPageSeekSettled(seek, 5, ReaderChapterPagePosition(5, 31)))
        // Once the reader really moved, the mismatching ordinal retires the wait.
        assertEquals(true, chapterPageSeekSettled(seek, 5, ReaderChapterPagePosition(1, 12)))
        // Publishing the target always settles it.
        assertEquals(true, chapterPageSeekSettled(seek, 5, ReaderChapterPagePosition(12, 31)))
    }

    private fun page(chapterOrd: Int): ReaderPageSlice =
        ReaderPageSlice(
            chapterTitle = "第 ${chapterOrd + 1} 章",
            startOffset = chapterOrd * 1_000,
            endOffset = chapterOrd * 1_000 + 900,
            chapterOrd = chapterOrd,
        )
}
