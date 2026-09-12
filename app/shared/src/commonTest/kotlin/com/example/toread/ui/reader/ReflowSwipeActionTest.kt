package com.example.toread.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReflowSwipeActionTest {
    @Test
    fun shortSlowDragSpringsBack() {
        assertEquals(
            ReflowSwipeAction.Cancel,
            action(dragXPx = -20f, dragYPx = 2f, velocityPxPerSecond = 100f),
        )
    }

    @Test
    fun dragPastTheDistanceThresholdTurnsThePage() {
        // 16% of a 1000px page is 160px, which is above the 56px minimum.
        assertEquals(ReflowSwipeAction.Next, action(dragXPx = -200f))
        assertEquals(ReflowSwipeAction.Previous, action(dragXPx = 200f))
    }

    @Test
    fun minimumDistanceDecidesOnNarrowPages() {
        // 16% of 200px is 32px, so the 56px minimum is what commits.
        assertEquals(ReflowSwipeAction.Cancel, action(dragXPx = -40f, pageWidthPx = 200f))
        assertEquals(ReflowSwipeAction.Next, action(dragXPx = -60f, pageWidthPx = 200f))
    }

    @Test
    fun quickFlickTurnsThePageUnderTheDistanceThreshold() {
        assertEquals(
            ReflowSwipeAction.Next,
            action(dragXPx = -30f, velocityPxPerSecond = -1_200f),
        )
        assertEquals(
            ReflowSwipeAction.Previous,
            action(dragXPx = 30f, velocityPxPerSecond = 1_200f),
        )
        assertEquals(
            ReflowSwipeAction.Cancel,
            action(dragXPx = -30f, velocityPxPerSecond = -400f),
        )
    }

    @Test
    fun distanceWinsOverContradictingVelocity() {
        assertEquals(
            ReflowSwipeAction.Next,
            action(dragXPx = -400f, velocityPxPerSecond = 1_500f),
        )
    }

    @Test
    fun mostlyVerticalDragNeverTurnsThePage() {
        assertEquals(
            ReflowSwipeAction.Cancel,
            action(dragXPx = -400f, dragYPx = -500f, velocityPxPerSecond = -2_000f),
        )
    }

    @Test
    fun edgesNeverCommit() {
        assertEquals(ReflowSwipeAction.Cancel, action(dragXPx = -400f, canNext = false))
        assertEquals(ReflowSwipeAction.Cancel, action(dragXPx = 400f, canPrevious = false))
    }

    @Test
    fun zeroWidthPageNeverTurns() {
        assertEquals(ReflowSwipeAction.Cancel, action(dragXPx = -400f, pageWidthPx = 0f))
    }

    @Test
    fun dragOffsetFollowsTheFingerWhenTheNeighbourIsReady() {
        assertEquals(-300f, reflowDragOffsetPx(0f, -300f, PageWidthPx, readyBounds))
        assertEquals(300f, reflowDragOffsetPx(0f, 300f, PageWidthPx, readyBounds))
    }

    @Test
    fun dragOffsetResistsDirectionsWithoutANeighbourPreview() {
        val unresolvedNext = readyBounds.copy(hasNextPreview = false)
        val offset = reflowDragOffsetPx(0f, -300f, PageWidthPx, unresolvedNext)
        assertTrue(offset < 0f && offset > -300f)

        val unresolvedPrevious = readyBounds.copy(hasPreviousPreview = false)
        val previousOffset = reflowDragOffsetPx(0f, 300f, PageWidthPx, unresolvedPrevious)
        assertTrue(previousOffset > 0f && previousOffset < 300f)
    }

    @Test
    fun dragOffsetResistsDirectionsThatCannotTurn() {
        val edge = readyBounds.copy(canTurnNext = false)
        val offset = reflowDragOffsetPx(0f, -300f, PageWidthPx, edge)
        assertTrue(offset < 0f && offset > -300f)
    }

    @Test
    fun dragOffsetNeverLeavesThePage() {
        assertEquals(PageWidthPx, reflowDragOffsetPx(0f, 5_000f, PageWidthPx, readyBounds))
        assertEquals(-PageWidthPx, reflowDragOffsetPx(0f, -5_000f, PageWidthPx, readyBounds))
        assertEquals(0f, reflowDragOffsetPx(0f, -300f, 0f, readyBounds))
    }

    @Test
    fun tapZonesKeepTheLeftCenterRightThirds() {
        assertEquals(ReflowTapAction.Previous, reflowTapAction(0f))
        assertEquals(ReflowTapAction.Previous, reflowTapAction(0.32f))
        assertEquals(ReflowTapAction.ToggleControls, reflowTapAction(0.34f))
        assertEquals(ReflowTapAction.ToggleControls, reflowTapAction(0.5f))
        assertEquals(ReflowTapAction.ToggleControls, reflowTapAction(0.66f))
        assertEquals(ReflowTapAction.Next, reflowTapAction(0.68f))
        assertEquals(ReflowTapAction.Next, reflowTapAction(1f))
    }

    private fun action(
        dragXPx: Float,
        dragYPx: Float = 0f,
        pageWidthPx: Float = PageWidthPx,
        velocityPxPerSecond: Float = 0f,
        canPrevious: Boolean = true,
        canNext: Boolean = true,
    ): ReflowSwipeAction =
        reflowSwipeAction(
            dragXPx = dragXPx,
            dragYPx = dragYPx,
            pageWidthPx = pageWidthPx,
            velocityPxPerSecond = velocityPxPerSecond,
            minimumTurnDistancePx = MinimumTurnDistancePx,
            canPrevious = canPrevious,
            canNext = canNext,
        )

    private companion object {
        const val PageWidthPx = 1_000f
        const val MinimumTurnDistancePx = 56f
        val readyBounds = ReflowDragBounds(
            canTurnNext = true,
            canTurnPrevious = true,
            hasNextPreview = true,
            hasPreviousPreview = true,
        )
    }
}
