package com.example.toread.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReflowFlipStateTest {
    @Test
    fun aRunningTurnDebouncesEveryNewTurn() {
        val running = ReflowFlipState().beginTurn(
            direction = ReflowFlipDirection.Forward,
            target = pageB,
        )

        assertTrue(running.isBusy)
        // A second gesture during the animation must not start another turn.
        assertEquals(
            running,
            running.beginTurn(direction = ReflowFlipDirection.Forward, target = pageC),
        )
        assertEquals(running, running.startDrag())
    }

    @Test
    fun aTurnLocksThePreRenderedTargetUntilItFinishes() {
        val running = ReflowFlipState().beginTurn(
            direction = ReflowFlipDirection.Forward,
            target = pageB,
        )

        assertEquals(pageB, running.incomingSlice)
        assertEquals(ReflowFlipDirection.Forward, running.direction)
        assertTrue(running.isAnimating)
    }

    @Test
    fun finishingATurnReturnsToRestInOneState() {
        val before = ReflowFlipState().beginTurn(
            direction = ReflowFlipDirection.Forward,
            target = pageB,
        )
        val dragged = before.copy(offsetPx = -PageWidthPx)

        // One state write carries both the page change and the offset reset, so the frame that shows
        // the new page already shows it at rest.
        assertEquals(ReflowFlipState(), dragged.finishTurn())
    }

    @Test
    fun cancellingATurnAlsoReturnsToRest() {
        val cancelled = ReflowFlipState()
            .beginTurn(direction = ReflowFlipDirection.Backward, target = pageA)
            .copy(offsetPx = 400f)
            .cancelTurn()

        assertEquals(ReflowFlipState(), cancelled)
    }

    @Test
    fun aTurnWithoutAPreviewStillReleasesInputWhenItSettles() {
        val running = ReflowFlipState().beginTurn(
            direction = ReflowFlipDirection.Forward,
            target = null,
        )

        assertNull(running.incomingSlice)
        assertTrue(running.isBusy)
        assertFalse(running.cancelTurn().isBusy)
    }

    @Test
    fun aTurnStartsFromWhereTheFingerLetGo() {
        val released = ReflowFlipState()
            .startDrag()
            .dragTo(offsetPx = -240f)
            .endDrag()

        val running = released.beginTurn(
            direction = ReflowFlipDirection.Forward,
            target = pageB,
        )

        assertEquals(-240f, running.offsetPx)
        assertFalse(running.isDragging)
    }

    @Test
    fun draggingOnlyMovesThePageWhileDragging() {
        assertEquals(ReflowFlipState(), ReflowFlipState().dragTo(offsetPx = -120f))

        val dragged = ReflowFlipState().startDrag().dragTo(offsetPx = -120f)
        assertTrue(dragged.isDragging)
        assertEquals(-120f, dragged.offsetPx)

        val released = dragged.endDrag()
        assertFalse(released.isDragging)
        assertEquals(-120f, released.offsetPx)
    }

    private companion object {
        const val PageWidthPx = 1_000f
        val pageA = ReaderPageSlice(chapterTitle = "第一章", startOffset = 0, endOffset = 900)
        val pageB = ReaderPageSlice(chapterTitle = "第一章", startOffset = 900, endOffset = 1_800)
        val pageC = ReaderPageSlice(chapterTitle = "第二章", startOffset = 0, endOffset = 900)
    }
}
