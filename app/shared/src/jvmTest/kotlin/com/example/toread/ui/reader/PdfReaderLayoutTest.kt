package com.example.toread.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfReaderLayoutTest {
    @Test
    fun fitInsideAlwaysKeepsTheWholePageWithinViewport() {
        assertEquals(
            PdfPageFitSize(widthPx = 300, heightPx = 450),
            fitPdfPageInside(
                sourceWidthPx = 600,
                sourceHeightPx = 900,
                viewportWidthPx = 300,
                viewportHeightPx = 600,
            ),
        )
        assertEquals(
            PdfPageFitSize(widthPx = 300, heightPx = 200),
            fitPdfPageInside(
                sourceWidthPx = 900,
                sourceHeightPx = 600,
                viewportWidthPx = 300,
                viewportHeightPx = 600,
            ),
        )
        assertEquals(
            PdfPageFitSize(widthPx = 250, heightPx = 500),
            fitPdfPageInside(
                sourceWidthPx = 600,
                sourceHeightPx = 1_200,
                viewportWidthPx = 400,
                viewportHeightPx = 500,
            ),
        )
    }

    @Test
    fun coverCanRemainSingleBeforeDoublePageSpreads() {
        val cover = pdfPageSpread(
            pageIndex = 0,
            pageCount = 6,
            isDoublePage = true,
            showCoverAlone = true,
            rightToLeft = false,
        )
        val middle = pdfPageSpread(
            pageIndex = 2,
            pageCount = 6,
            isDoublePage = true,
            showCoverAlone = true,
            rightToLeft = false,
        )
        val last = pdfPageSpread(
            pageIndex = 5,
            pageCount = 6,
            isDoublePage = true,
            showCoverAlone = true,
            rightToLeft = false,
        )

        assertEquals(listOf(0), cover.pageIndices)
        assertEquals(0, cover.spreadIndex)
        assertEquals(4, cover.spreadCount)
        assertEquals(1, middle.anchorPageIndex)
        assertEquals(listOf(1, 2), middle.pageIndices)
        assertEquals(1, middle.spreadIndex)
        assertEquals(5, last.anchorPageIndex)
        assertEquals(listOf(5), last.pageIndices)
        assertEquals(5, last.progressPageIndex)
    }

    @Test
    fun rightToLeftOnlyReversesVisualOrder() {
        val spread = pdfPageSpread(
            pageIndex = 2,
            pageCount = 6,
            isDoublePage = true,
            showCoverAlone = false,
            rightToLeft = true,
        )

        assertEquals(2, spread.anchorPageIndex)
        assertEquals(listOf(3, 2), spread.pageIndices)
        assertEquals(3, spread.progressPageIndex)
    }

    @Test
    fun lastDoublePageSpreadReportsTheLastVisiblePageAsProgress() {
        val spread = pdfPageSpread(
            pageIndex = 5,
            pageCount = 6,
            isDoublePage = true,
            showCoverAlone = false,
            rightToLeft = false,
        )

        assertEquals(4, spread.anchorPageIndex)
        assertEquals(listOf(4, 5), spread.pageIndices)
        assertEquals(5, spread.progressPageIndex)
        assertEquals(2, spread.spreadIndex)
        assertEquals(3, spread.spreadCount)
    }

    @Test
    fun renderOrderPrefetchesAWholeSpreadInNavigationDirection() {
        val spread = pdfPageSpread(
            pageIndex = 2,
            pageCount = 8,
            isDoublePage = true,
            showCoverAlone = false,
            rightToLeft = false,
        )

        assertEquals(
            listOf(2, 3, 4, 5),
            pdfRenderOrder(
                spread = spread,
                pageCount = 8,
                isDoublePage = true,
                showCoverAlone = false,
                prefetchDirection = 1,
                maxCachedPages = 4,
            ),
        )
        assertEquals(
            listOf(2, 3, 0, 1),
            pdfRenderOrder(
                spread = spread,
                pageCount = 8,
                isDoublePage = true,
                showCoverAlone = false,
                prefetchDirection = -1,
                maxCachedPages = 4,
            ),
        )
    }

    @Test
    fun spreadOnlyBecomesDisplayableAfterEveryPageIsResolved() {
        val pages = listOf(4, 5)

        assertEquals(
            false,
            pdfSpreadIsResolved(
                pageIndices = pages,
                renderedPageIndices = setOf(4),
            ),
        )
        assertEquals(
            true,
            pdfSpreadIsResolved(
                pageIndices = pages.reversed(),
                renderedPageIndices = setOf(4, 5),
            ),
        )
    }

    @Test
    fun horizontalNavigationFollowsReadingDirection() {
        assertEquals(-1, pdfHorizontalNavigationDelta(moveRight = false, rightToLeft = false))
        assertEquals(1, pdfHorizontalNavigationDelta(moveRight = true, rightToLeft = false))
        assertEquals(1, pdfHorizontalNavigationDelta(moveRight = false, rightToLeft = true))
        assertEquals(-1, pdfHorizontalNavigationDelta(moveRight = true, rightToLeft = true))
    }

    @Test
    fun androidTapZonesKeepPageTurnsAwayFromTheCenter() {
        assertEquals(
            PdfReaderTapAction.PreviousPage,
            pdfReaderTapAction(
                xPx = 299f,
                viewportWidthPx = 1_000f,
                isZoomed = false,
                controlsVisible = false,
            ),
        )
        assertEquals(
            PdfReaderTapAction.ToggleControls,
            pdfReaderTapAction(
                xPx = 300f,
                viewportWidthPx = 1_000f,
                isZoomed = false,
                controlsVisible = false,
            ),
        )
        assertEquals(
            PdfReaderTapAction.ToggleControls,
            pdfReaderTapAction(
                xPx = 699f,
                viewportWidthPx = 1_000f,
                isZoomed = false,
                controlsVisible = false,
            ),
        )
        assertEquals(
            PdfReaderTapAction.NextPage,
            pdfReaderTapAction(
                xPx = 700f,
                viewportWidthPx = 1_000f,
                isZoomed = false,
                controlsVisible = false,
            ),
        )
    }

    @Test
    fun androidTapZonesGiveControlsAndZoomPriority() {
        assertEquals(
            PdfReaderTapAction.ToggleControls,
            pdfReaderTapAction(
                xPx = 50f,
                viewportWidthPx = 1_000f,
                isZoomed = false,
                controlsVisible = true,
            ),
        )
        assertEquals(
            PdfReaderTapAction.None,
            pdfReaderTapAction(
                xPx = 50f,
                viewportWidthPx = 1_000f,
                isZoomed = true,
                controlsVisible = false,
            ),
        )
        assertEquals(
            PdfReaderTapAction.ToggleControls,
            pdfReaderTapAction(
                xPx = 500f,
                viewportWidthPx = 1_000f,
                isZoomed = true,
                controlsVisible = false,
            ),
        )
    }

    @Test
    fun androidSwipeRequiresAHorizontalThresholdAtFitScale() {
        assertEquals(
            PdfReaderSwipeAction.None,
            pdfReaderSwipeAction(
                dragXPx = -119f,
                dragYPx = 0f,
                viewportWidthPx = 1_000f,
                minimumTurnDistancePx = 56f,
                isZoomed = false,
            ),
        )
        assertEquals(
            PdfReaderSwipeAction.NextPage,
            pdfReaderSwipeAction(
                dragXPx = -120f,
                dragYPx = 0f,
                viewportWidthPx = 1_000f,
                minimumTurnDistancePx = 56f,
                isZoomed = false,
            ),
        )
        assertEquals(
            PdfReaderSwipeAction.PreviousPage,
            pdfReaderSwipeAction(
                dragXPx = 120f,
                dragYPx = 0f,
                viewportWidthPx = 1_000f,
                minimumTurnDistancePx = 56f,
                isZoomed = false,
            ),
        )
        assertEquals(
            PdfReaderSwipeAction.None,
            pdfReaderSwipeAction(
                dragXPx = -500f,
                dragYPx = 0f,
                viewportWidthPx = 1_000f,
                minimumTurnDistancePx = 56f,
                isZoomed = true,
            ),
        )
        assertEquals(
            PdfReaderSwipeAction.None,
            pdfReaderSwipeAction(
                dragXPx = 120f,
                dragYPx = 121f,
                viewportWidthPx = 1_000f,
                minimumTurnDistancePx = 56f,
                isZoomed = false,
            ),
        )
        assertEquals(
            PdfReaderSwipeAction.None,
            pdfReaderSwipeAction(
                dragXPx = -55f,
                dragYPx = 0f,
                viewportWidthPx = 100f,
                minimumTurnDistancePx = 56f,
                isZoomed = false,
            ),
        )
        assertEquals(
            PdfReaderSwipeAction.NextPage,
            pdfReaderSwipeAction(
                dragXPx = -56f,
                dragYPx = 0f,
                viewportWidthPx = 100f,
                minimumTurnDistancePx = 56f,
                isZoomed = false,
            ),
        )
    }

    @Test
    fun androidPanIsClampedToTheScaledPageBounds() {
        assertEquals(
            PdfPanOffset(xPx = 60f, yPx = 0f),
            clampPdfPan(
                xPx = 500f,
                yPx = -500f,
                scale = 2f,
                contentWidthPx = 600f,
                contentHeightPx = 900f,
                viewportWidthPx = 1_080f,
                viewportHeightPx = 1_800f,
            ),
        )
        assertEquals(
            PdfPanOffset(xPx = 0f, yPx = 0f),
            clampPdfPan(
                xPx = 500f,
                yPx = -500f,
                scale = 1f,
                contentWidthPx = 600f,
                contentHeightPx = 900f,
                viewportWidthPx = 1_080f,
                viewportHeightPx = 1_800f,
            ),
        )
    }

    @Test
    fun doubleTapZoomKeepsTheFocusedPointStableWithinBounds() {
        assertEquals(
            PdfPanOffset(xPx = -250f, yPx = 0f),
            pdfPanAfterZoomAtPoint(
                currentPan = PdfPanOffset(xPx = 0f, yPx = 0f),
                currentScale = 1f,
                targetScale = 2f,
                focusXPx = 750f,
                focusYPx = 500f,
                contentWidthPx = 800f,
                contentHeightPx = 500f,
                viewportWidthPx = 1_000f,
                viewportHeightPx = 1_000f,
            ),
        )
    }

    @Test
    fun continuousAnchorTracksTheHiddenFractionOfThePhysicalPage() {
        assertEquals(
            PdfContinuousAnchor(pageIndex = 2, pageOffsetY = 0.25),
            pdfContinuousAnchor(
                visiblePages = listOf(
                    PdfContinuousVisiblePage(
                        pageIndex = 2,
                        offsetPx = -250,
                        sizePx = 1_000,
                    ),
                    PdfContinuousVisiblePage(
                        pageIndex = 3,
                        offsetPx = 758,
                        sizePx = 1_000,
                    ),
                ),
                viewportStartOffsetPx = 0,
                pageCount = 10,
            ),
        )
        assertEquals(
            PdfContinuousAnchor(pageIndex = 3, pageOffsetY = 0.0),
            pdfContinuousAnchor(
                visiblePages = listOf(
                    PdfContinuousVisiblePage(
                        pageIndex = 2,
                        offsetPx = -1_000,
                        sizePx = 900,
                    ),
                    PdfContinuousVisiblePage(
                        pageIndex = 3,
                        offsetPx = 20,
                        sizePx = 1_000,
                    ),
                ),
                viewportStartOffsetPx = 0,
                pageCount = 10,
            ),
        )
    }

    @Test
    fun continuousAnchorNormalizesPageEndAndMalformedOffsets() {
        assertEquals(
            PdfContinuousAnchor(pageIndex = 5, pageOffsetY = 0.0),
            normalizePdfContinuousAnchor(
                pageIndex = 4,
                pageOffsetY = 1.0,
                pageCount = 8,
            ),
        )
        assertEquals(
            PdfContinuousAnchor(pageIndex = 7, pageOffsetY = 1.0),
            normalizePdfContinuousAnchor(
                pageIndex = 20,
                pageOffsetY = 4.0,
                pageCount = 8,
            ),
        )
        assertEquals(
            PdfContinuousAnchor(pageIndex = 0, pageOffsetY = 0.0),
            normalizePdfContinuousAnchor(
                pageIndex = -4,
                pageOffsetY = Double.NaN,
                pageCount = 8,
            ),
        )
    }

    @Test
    fun continuousRenderOrderKeepsTheAnchorAndNeverExceedsTheWindow() {
        assertEquals(
            listOf(5, 6, 4, 7),
            pdfContinuousRenderOrder(
                anchorPageIndex = 5,
                visiblePageIndices = listOf(4, 5, 6, 7, 8),
                scrollDirection = 1,
                pageCount = 20,
                maxCachedPages = 4,
            ),
        )
        assertEquals(
            listOf(0, 1, 2, 3),
            pdfContinuousRenderOrder(
                anchorPageIndex = 0,
                visiblePageIndices = listOf(0),
                scrollDirection = 1,
                pageCount = 10,
                maxCachedPages = 4,
            ),
        )
        assertEquals(
            listOf(9, 8, 7, 6),
            pdfContinuousRenderOrder(
                anchorPageIndex = 9,
                visiblePageIndices = listOf(9),
                scrollDirection = -1,
                pageCount = 10,
                maxCachedPages = 4,
            ),
        )
    }

    @Test
    fun continuousProgressUsesThePageFractionWithoutLayoutPixels() {
        assertEquals(
            0.3,
            pdfContinuousGlobalProgress(
                pageIndex = 1,
                pageOffsetY = 0.5,
                pageCount = 5,
            ),
            absoluteTolerance = 0.000_001,
        )
        assertEquals(
            1.0,
            pdfContinuousGlobalProgress(
                pageIndex = 4,
                pageOffsetY = 1.0,
                pageCount = 5,
            ),
        )
        assertEquals(
            0.5,
            pdfContinuousGlobalProgress(
                pageIndex = 0,
                pageOffsetY = 0.5,
                pageCount = 1,
            ),
        )
    }

    @Test
    fun continuousRestoreOffsetStaysInsideTheRequestedItem() {
        assertEquals(0, pdfContinuousScrollOffsetPx(pageOffsetY = -1.0, itemSizePx = 100))
        assertEquals(25, pdfContinuousScrollOffsetPx(pageOffsetY = 0.25, itemSizePx = 100))
        assertEquals(99, pdfContinuousScrollOffsetPx(pageOffsetY = 1.0, itemSizePx = 100))
        assertEquals(0, pdfContinuousScrollOffsetPx(pageOffsetY = 0.5, itemSizePx = 0))
    }

    @Test
    fun continuousRenderWidthUsesStableBoundedBuckets() {
        assertEquals(
            0,
            pdfContinuousRenderWidthBucket(
                widthPx = 0,
                minimumWidthPx = 128,
                maximumWidthPx = 2_048,
                bucketSizePx = 64,
            ),
        )
        assertEquals(
            128,
            pdfContinuousRenderWidthBucket(
                widthPx = 100,
                minimumWidthPx = 128,
                maximumWidthPx = 2_048,
                bucketSizePx = 64,
            ),
        )
        assertEquals(
            1_088,
            pdfContinuousRenderWidthBucket(
                widthPx = 1_080,
                minimumWidthPx = 128,
                maximumWidthPx = 2_048,
                bucketSizePx = 64,
            ),
        )
        assertEquals(
            2_048,
            pdfContinuousRenderWidthBucket(
                widthPx = 3_000,
                minimumWidthPx = 128,
                maximumWidthPx = 2_048,
                bucketSizePx = 64,
            ),
        )
    }

    @Test
    fun pagedTargetSlotsRespectHorizontalRtlButNotVerticalMode() {
        assertEquals(1, pdfPagedTargetSlotSign(1, isVertical = false, rightToLeft = false))
        assertEquals(-1, pdfPagedTargetSlotSign(1, isVertical = false, rightToLeft = true))
        assertEquals(-1, pdfPagedTargetSlotSign(-1, isVertical = false, rightToLeft = false))
        assertEquals(1, pdfPagedTargetSlotSign(-1, isVertical = false, rightToLeft = true))
        assertEquals(1, pdfPagedTargetSlotSign(1, isVertical = true, rightToLeft = true))
        assertEquals(-1, pdfPagedTargetSlotSign(-1, isVertical = true, rightToLeft = true))
    }

    @Test
    fun pagedDragMappingCoversHorizontalRtlAndVerticalReading() {
        assertEquals(
            1,
            pdfPagedLogicalDeltaForDrag(-120f, isVertical = false, rightToLeft = false),
        )
        assertEquals(
            -1,
            pdfPagedLogicalDeltaForDrag(-120f, isVertical = false, rightToLeft = true),
        )
        assertEquals(
            1,
            pdfPagedLogicalDeltaForDrag(-120f, isVertical = true, rightToLeft = true),
        )
        assertEquals(
            -1,
            pdfPagedLogicalDeltaForDrag(120f, isVertical = true, rightToLeft = false),
        )
        assertEquals(
            0,
            pdfPagedLogicalDeltaForDrag(Float.NaN, isVertical = false, rightToLeft = false),
        )
    }

    @Test
    fun pagedTapMappingSupportsRtlReverseCenterAndZoomConsumption() {
        fun tap(
            position: Float,
            isVertical: Boolean = false,
            rightToLeft: Boolean = false,
            reverse: Boolean = false,
            enabled: Boolean = true,
            zoomed: Boolean = false,
        ): Int? = pdfPagedLogicalDeltaForTap(
            positionOnAxisPx = position,
            viewportExtentPx = 100f,
            isVertical = isVertical,
            rightToLeft = rightToLeft,
            tapToTurnPagesEnabled = enabled,
            reverseTapToTurnPages = reverse,
            isZoomed = zoomed,
        )

        assertEquals(-1, tap(10f))
        assertEquals(1, tap(90f))
        assertEquals(1, tap(10f, rightToLeft = true))
        assertEquals(-1, tap(90f, rightToLeft = true))
        assertEquals(1, tap(10f, reverse = true))
        assertEquals(-1, tap(10f, isVertical = true, rightToLeft = true))
        assertEquals(null, tap(50f))
        assertEquals(null, tap(10f, enabled = false))
        assertEquals(0, tap(10f, zoomed = true))
        assertEquals(
            null,
            pdfPagedLogicalDeltaForTap(
                positionOnAxisPx = Float.NaN,
                viewportExtentPx = 100f,
                isVertical = false,
                rightToLeft = false,
                tapToTurnPagesEnabled = true,
                reverseTapToTurnPages = false,
                isZoomed = false,
            ),
        )
    }
}
