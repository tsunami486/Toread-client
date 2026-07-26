package com.example.toread.ui.reader

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PdfPageFitSize(
    val widthPx: Int,
    val heightPx: Int,
)

internal fun fitPdfPageInside(
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
): PdfPageFitSize {
    val safeViewportWidth = viewportWidthPx.coerceAtLeast(1)
    val safeViewportHeight = viewportHeightPx.coerceAtLeast(1)
    if (sourceWidthPx <= 0 || sourceHeightPx <= 0) {
        return PdfPageFitSize(
            widthPx = safeViewportWidth,
            heightPx = safeViewportHeight,
        )
    }

    val scale = min(
        safeViewportWidth.toDouble() / sourceWidthPx.toDouble(),
        safeViewportHeight.toDouble() / sourceHeightPx.toDouble(),
    )
    return PdfPageFitSize(
        widthPx = (sourceWidthPx * scale)
            .roundToInt()
            .coerceIn(1, safeViewportWidth),
        heightPx = (sourceHeightPx * scale)
            .roundToInt()
            .coerceIn(1, safeViewportHeight),
    )
}

internal data class PdfPageSpread(
    val anchorPageIndex: Int,
    val pageIndices: List<Int>,
    val spreadIndex: Int,
    val spreadCount: Int,
) {
    val progressPageIndex: Int
        get() = pageIndices.maxOrNull() ?: anchorPageIndex
}

internal fun pdfPageSpread(
    pageIndex: Int,
    pageCount: Int,
    isDoublePage: Boolean,
    showCoverAlone: Boolean,
    rightToLeft: Boolean,
): PdfPageSpread {
    if (pageCount <= 0) {
        return PdfPageSpread(
            anchorPageIndex = 0,
            pageIndices = emptyList(),
            spreadIndex = 0,
            spreadCount = 0,
        )
    }

    val anchor = normalizePdfPageIndex(
        index = pageIndex,
        pageCount = pageCount,
        isDoublePage = isDoublePage,
        showCoverAlone = showCoverAlone,
    )
    val pageIndices = buildList {
        add(anchor)
        val keepCoverSingle = showCoverAlone && anchor == 0
        if (isDoublePage && !keepCoverSingle && anchor + 1 < pageCount) {
            add(anchor + 1)
        }
    }.let { pages ->
        if (rightToLeft && pages.size > 1) pages.reversed() else pages
    }
    val spreadIndex = when {
        !isDoublePage -> anchor
        showCoverAlone && anchor > 0 -> 1 + ((anchor - 1) / 2)
        else -> anchor / 2
    }
    return PdfPageSpread(
        anchorPageIndex = anchor,
        pageIndices = pageIndices,
        spreadIndex = spreadIndex,
        spreadCount = pdfSpreadCount(
            pageCount = pageCount,
            isDoublePage = isDoublePage,
            showCoverAlone = showCoverAlone,
        ),
    )
}

internal fun normalizePdfPageIndex(
    index: Int,
    pageCount: Int,
    isDoublePage: Boolean,
    showCoverAlone: Boolean,
): Int {
    if (pageCount <= 1) return 0
    val bounded = index.coerceIn(0, pageCount - 1)
    if (!isDoublePage) return bounded
    if (showCoverAlone && bounded > 0) {
        return bounded - ((bounded - 1) % 2)
    }
    return bounded - (bounded % 2)
}

internal fun pdfSpreadAnchorAt(
    spreadIndex: Int,
    pageCount: Int,
    isDoublePage: Boolean,
    showCoverAlone: Boolean,
): Int {
    val spreadCount = pdfSpreadCount(
        pageCount = pageCount,
        isDoublePage = isDoublePage,
        showCoverAlone = showCoverAlone,
    )
    if (spreadCount <= 0) return 0
    val boundedSpread = spreadIndex.coerceIn(0, spreadCount - 1)
    val requestedPage = when {
        !isDoublePage -> boundedSpread
        showCoverAlone && boundedSpread > 0 -> 1 + ((boundedSpread - 1) * 2)
        else -> boundedSpread * 2
    }
    return normalizePdfPageIndex(
        index = requestedPage,
        pageCount = pageCount,
        isDoublePage = isDoublePage,
        showCoverAlone = showCoverAlone,
    )
}

internal fun pdfRenderOrder(
    spread: PdfPageSpread,
    pageCount: Int,
    isDoublePage: Boolean,
    showCoverAlone: Boolean,
    prefetchDirection: Int,
    maxCachedPages: Int,
): List<Int> {
    if (pageCount <= 0 || maxCachedPages <= 0) return emptyList()
    return buildList {
        addAll(spread.pageIndices)
        val primaryDirection = if (prefetchDirection < 0) -1 else 1
        listOf(primaryDirection, -primaryDirection).forEach { direction ->
            val targetSpreadIndex = spread.spreadIndex + direction
            if (targetSpreadIndex !in 0 until spread.spreadCount) return@forEach
            val targetAnchor = pdfSpreadAnchorAt(
                spreadIndex = targetSpreadIndex,
                pageCount = pageCount,
                isDoublePage = isDoublePage,
                showCoverAlone = showCoverAlone,
            )
            addAll(
                pdfPageSpread(
                    pageIndex = targetAnchor,
                    pageCount = pageCount,
                    isDoublePage = isDoublePage,
                    showCoverAlone = showCoverAlone,
                    rightToLeft = false,
                ).pageIndices,
            )
        }
    }.distinct().take(maxCachedPages)
}

internal fun pdfSpreadIsResolved(
    pageIndices: List<Int>,
    renderedPageIndices: Set<Int>,
): Boolean =
    pageIndices.isNotEmpty() &&
        pageIndices.all { pageIndex -> pageIndex in renderedPageIndices }

internal fun pdfHorizontalNavigationDelta(
    moveRight: Boolean,
    rightToLeft: Boolean,
): Int = if (moveRight != rightToLeft) 1 else -1

internal enum class PdfReaderTapAction {
    PreviousPage,
    ToggleControls,
    NextPage,
    None,
}

internal fun pdfReaderTapAction(
    xPx: Float,
    viewportWidthPx: Float,
    isZoomed: Boolean,
    controlsVisible: Boolean,
): PdfReaderTapAction {
    if (controlsVisible) return PdfReaderTapAction.ToggleControls
    if (viewportWidthPx <= 0f) return PdfReaderTapAction.None

    val horizontalFraction = (xPx / viewportWidthPx).coerceIn(0f, 1f)
    return when {
        isZoomed && horizontalFraction in 0.3f..0.7f ->
            PdfReaderTapAction.ToggleControls
        isZoomed ->
            PdfReaderTapAction.None
        horizontalFraction < 0.3f ->
            PdfReaderTapAction.PreviousPage
        horizontalFraction < 0.7f ->
            PdfReaderTapAction.ToggleControls
        else ->
            PdfReaderTapAction.NextPage
    }
}

internal enum class PdfReaderSwipeAction {
    PreviousPage,
    NextPage,
    None,
}

internal fun pdfReaderSwipeAction(
    dragXPx: Float,
    dragYPx: Float,
    viewportWidthPx: Float,
    minimumTurnDistancePx: Float,
    isZoomed: Boolean,
): PdfReaderSwipeAction {
    if (isZoomed || viewportWidthPx <= 0f) return PdfReaderSwipeAction.None
    val turnDistance = maxOf(
        minimumTurnDistancePx.coerceAtLeast(0f),
        viewportWidthPx * 0.12f,
    )
    if (
        abs(dragXPx) < turnDistance ||
        abs(dragXPx) <= abs(dragYPx)
    ) {
        return PdfReaderSwipeAction.None
    }
    return if (dragXPx < 0f) {
        PdfReaderSwipeAction.NextPage
    } else {
        PdfReaderSwipeAction.PreviousPage
    }
}

internal data class PdfPanOffset(
    val xPx: Float,
    val yPx: Float,
)

internal fun clampPdfPan(
    xPx: Float,
    yPx: Float,
    scale: Float,
    contentWidthPx: Float,
    contentHeightPx: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
): PdfPanOffset {
    val safeScale = scale.coerceAtLeast(1f)
    val maxPanX = (
        (contentWidthPx.coerceAtLeast(0f) * safeScale) -
            viewportWidthPx.coerceAtLeast(0f)
        ).div(2f).coerceAtLeast(0f)
    val maxPanY = (
        (contentHeightPx.coerceAtLeast(0f) * safeScale) -
            viewportHeightPx.coerceAtLeast(0f)
        ).div(2f).coerceAtLeast(0f)
    return PdfPanOffset(
        xPx = if (maxPanX == 0f) 0f else xPx.coerceIn(-maxPanX, maxPanX),
        yPx = if (maxPanY == 0f) 0f else yPx.coerceIn(-maxPanY, maxPanY),
    )
}

internal fun pdfPanAfterZoomAtPoint(
    currentPan: PdfPanOffset,
    currentScale: Float,
    targetScale: Float,
    focusXPx: Float,
    focusYPx: Float,
    contentWidthPx: Float,
    contentHeightPx: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
): PdfPanOffset {
    val safeCurrentScale = currentScale.coerceAtLeast(1f)
    val safeTargetScale = targetScale.coerceAtLeast(1f)
    val scaleRatio = safeTargetScale / safeCurrentScale
    val candidateX =
        ((1f - scaleRatio) * (focusXPx - (viewportWidthPx / 2f))) +
            (scaleRatio * currentPan.xPx)
    val candidateY =
        ((1f - scaleRatio) * (focusYPx - (viewportHeightPx / 2f))) +
            (scaleRatio * currentPan.yPx)
    return clampPdfPan(
        xPx = candidateX,
        yPx = candidateY,
        scale = safeTargetScale,
        contentWidthPx = contentWidthPx,
        contentHeightPx = contentHeightPx,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
    )
}

internal fun pdfPagedTargetSlotSign(
    logicalDelta: Int,
    isVertical: Boolean,
    rightToLeft: Boolean,
): Int {
    if (logicalDelta == 0) return 0
    val logicalSign = if (logicalDelta < 0) -1 else 1
    return if (!isVertical && rightToLeft) -logicalSign else logicalSign
}

internal fun pdfPagedLogicalDeltaForDrag(
    dragOffsetPx: Float,
    isVertical: Boolean,
    rightToLeft: Boolean,
): Int {
    if (dragOffsetPx == 0f || dragOffsetPx.isNaN()) return 0
    val visualDelta = if (dragOffsetPx < 0f) 1 else -1
    return if (!isVertical && rightToLeft) -visualDelta else visualDelta
}

/**
 * Returns null for the central controls zone, 0 for a consumed zoomed edge tap,
 * and -1/+1 for a logical previous/next request.
 */
internal fun pdfPagedLogicalDeltaForTap(
    positionOnAxisPx: Float,
    viewportExtentPx: Float,
    isVertical: Boolean,
    rightToLeft: Boolean,
    tapToTurnPagesEnabled: Boolean,
    reverseTapToTurnPages: Boolean,
    isZoomed: Boolean,
): Int? {
    if (!tapToTurnPagesEnabled) return null
    if (
        viewportExtentPx <= 0f ||
        viewportExtentPx.isNaN() ||
        positionOnAxisPx.isNaN()
    ) {
        return null
    }
    val fraction = (positionOnAxisPx / viewportExtentPx).coerceIn(0f, 1f)
    if (fraction in 0.3f..0.7f) return null
    if (isZoomed) return 0

    var logicalDelta = if (fraction < 0.3f) -1 else 1
    if (!isVertical && rightToLeft) logicalDelta = -logicalDelta
    if (reverseTapToTurnPages) logicalDelta = -logicalDelta
    return logicalDelta
}

internal data class PdfContinuousVisiblePage(
    val pageIndex: Int,
    val offsetPx: Int,
    val sizePx: Int,
)

internal data class PdfContinuousAnchor(
    val pageIndex: Int,
    val pageOffsetY: Double,
)

internal fun pdfContinuousAnchor(
    visiblePages: List<PdfContinuousVisiblePage>,
    viewportStartOffsetPx: Int,
    pageCount: Int,
): PdfContinuousAnchor? {
    if (pageCount <= 0) return null
    val boundedPages = visiblePages
        .asSequence()
        .filter { page -> page.pageIndex in 0 until pageCount }
        .sortedBy(PdfContinuousVisiblePage::offsetPx)
        .toList()
    if (boundedPages.isEmpty()) return null

    val measurablePages = boundedPages.filter { page -> page.sizePx > 0 }
    if (measurablePages.isEmpty()) {
        return PdfContinuousAnchor(
            pageIndex = boundedPages.first().pageIndex,
            pageOffsetY = 0.0,
        )
    }

    val readingPage = measurablePages.firstOrNull { page ->
        page.offsetPx.toLong() + page.sizePx.toLong() > viewportStartOffsetPx.toLong()
    } ?: measurablePages.last()
    val hiddenPagePixels = (viewportStartOffsetPx - readingPage.offsetPx)
        .coerceIn(0, readingPage.sizePx)
    return normalizePdfContinuousAnchor(
        pageIndex = readingPage.pageIndex,
        pageOffsetY = hiddenPagePixels.toDouble() / readingPage.sizePx.toDouble(),
        pageCount = pageCount,
    )
}

internal fun normalizePdfContinuousAnchor(
    pageIndex: Int,
    pageOffsetY: Double,
    pageCount: Int,
): PdfContinuousAnchor {
    if (pageCount <= 0) {
        return PdfContinuousAnchor(pageIndex = 0, pageOffsetY = 0.0)
    }
    val boundedPageIndex = pageIndex.coerceIn(0, pageCount - 1)
    val boundedOffset = pageOffsetY
        .takeIf { offset -> offset.isFinite() }
        ?.coerceIn(0.0, 1.0)
        ?: 0.0
    return if (
        boundedOffset >= PdfContinuousNextPageOffsetThreshold &&
        boundedPageIndex < pageCount - 1
    ) {
        PdfContinuousAnchor(
            pageIndex = boundedPageIndex + 1,
            pageOffsetY = 0.0,
        )
    } else {
        PdfContinuousAnchor(
            pageIndex = boundedPageIndex,
            pageOffsetY = boundedOffset,
        )
    }
}

internal fun pdfContinuousRenderOrder(
    anchorPageIndex: Int,
    visiblePageIndices: List<Int>,
    scrollDirection: Int,
    pageCount: Int,
    maxCachedPages: Int,
): List<Int> {
    if (pageCount <= 0 || maxCachedPages <= 0) return emptyList()
    val anchor = anchorPageIndex.coerceIn(0, pageCount - 1)
    val direction = if (scrollDirection < 0) -1 else 1
    return buildList {
        add(anchor)
        visiblePageIndices
            .asSequence()
            .filter { pageIndex -> pageIndex in 0 until pageCount }
            .distinct()
            .filter { pageIndex -> pageIndex != anchor }
            .sortedWith(
                compareBy<Int> { pageIndex -> abs(pageIndex - anchor) }
                    .thenBy { pageIndex ->
                        if ((pageIndex - anchor) * direction > 0) 0 else 1
                    },
            )
            .forEach { pageIndex ->
                if (size < maxCachedPages) add(pageIndex)
            }

        var distance = 1
        while (size < maxCachedPages && distance < pageCount) {
            val forwardPage = anchor + (direction * distance)
            val backwardPage = anchor - (direction * distance)
            if (forwardPage in 0 until pageCount && forwardPage !in this) {
                add(forwardPage)
            }
            if (
                size < maxCachedPages &&
                backwardPage in 0 until pageCount &&
                backwardPage !in this
            ) {
                add(backwardPage)
            }
            distance++
        }
    }.take(maxCachedPages)
}

internal fun pdfContinuousGlobalProgress(
    pageIndex: Int,
    pageOffsetY: Double,
    pageCount: Int,
): Double {
    if (pageCount <= 0) return 0.0
    val anchor = normalizePdfContinuousAnchor(
        pageIndex = pageIndex,
        pageOffsetY = pageOffsetY,
        pageCount = pageCount,
    )
    return (
        (anchor.pageIndex.toDouble() + anchor.pageOffsetY) /
            pageCount.toDouble()
        ).coerceIn(0.0, 1.0)
}

internal fun pdfContinuousScrollOffsetPx(
    pageOffsetY: Double,
    itemSizePx: Int,
): Int {
    if (itemSizePx <= 0) return 0
    val boundedOffset = pageOffsetY
        .takeIf { offset -> offset.isFinite() }
        ?.coerceIn(0.0, PdfContinuousMaximumRestorableOffset)
        ?: 0.0
    return (boundedOffset * itemSizePx.toDouble())
        .roundToInt()
        .coerceIn(0, itemSizePx - 1)
}

internal fun pdfContinuousRenderWidthBucket(
    widthPx: Int,
    minimumWidthPx: Int,
    maximumWidthPx: Int,
    bucketSizePx: Int,
): Int {
    if (widthPx <= 0 || minimumWidthPx <= 0 || maximumWidthPx < minimumWidthPx) return 0
    val safeBucketSize = bucketSizePx.coerceAtLeast(1)
    val boundedWidth = widthPx.coerceIn(minimumWidthPx, maximumWidthPx)
    val roundedWidth = (
        (boundedWidth + (safeBucketSize / 2)) /
            safeBucketSize
        ) * safeBucketSize
    return roundedWidth.coerceIn(minimumWidthPx, maximumWidthPx)
}

private fun pdfSpreadCount(
    pageCount: Int,
    isDoublePage: Boolean,
    showCoverAlone: Boolean,
): Int {
    if (pageCount <= 0) return 0
    if (!isDoublePage) return pageCount
    if (!showCoverAlone) return (pageCount + 1) / 2
    val pagesAfterCover = pageCount - 1
    return 1 + ((pagesAfterCover + 1) / 2)
}

private const val PdfContinuousNextPageOffsetThreshold = 0.999_999
private const val PdfContinuousMaximumRestorableOffset = 0.999_999
