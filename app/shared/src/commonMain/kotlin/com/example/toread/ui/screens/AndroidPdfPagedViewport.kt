package com.example.toread.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.toread.reader.PdfDocumentRenderer
import com.example.toread.settings.PdfReaderMode
import com.example.toread.ui.reader.PdfPageFitSize
import com.example.toread.ui.reader.PdfPageSpread
import com.example.toread.ui.reader.PdfPanOffset
import com.example.toread.ui.reader.clampPdfPan
import com.example.toread.ui.reader.fitPdfPageInside
import com.example.toread.ui.reader.pdfPanAfterZoomAtPoint
import com.example.toread.ui.reader.pdfPagedLogicalDeltaForDrag
import com.example.toread.ui.reader.pdfPagedLogicalDeltaForTap
import com.example.toread.ui.reader.pdfPagedTargetSlotSign
import com.example.toread.ui.reader.pdfPageSpread
import com.example.toread.ui.reader.pdfRenderOrder
import com.example.toread.ui.reader.pdfSpreadAnchorAt
import com.example.toread.ui.reader.pdfSpreadIsResolved
import com.example.toread.ui.text.ToreadStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class AndroidPdfPagedViewportState(
    val pageCount: Int,
    val requestedPageIndex: Int,
    val isDoublePage: Boolean,
    val showCoverAlone: Boolean,
    val rightToLeft: Boolean,
    val readerMode: PdfReaderMode,
    val controlsVisible: Boolean,
    val tapToTurnPagesEnabled: Boolean,
    val reverseTapToTurnPages: Boolean,
    val pageAnimationEnabled: Boolean,
    val doubleTapZoomEnabled: Boolean,
    val longPressZoomEnabled: Boolean,
    val zoomDismissGeneration: Int,
)

@Composable
internal fun AndroidPdfPagedViewport(
    renderer: PdfDocumentRenderer,
    state: AndroidPdfPagedViewportState,
    prefetchDirection: Int,
    onPageRequested: (pageIndex: Int, navigationDirection: Int) -> Unit,
    onDisplayedSpreadChanged: (PdfPageSpread) -> Unit,
    onZoomActiveChange: (Boolean) -> Unit,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val requestedSpread = pdfPageSpread(
        pageIndex = state.requestedPageIndex,
        pageCount = state.pageCount,
        isDoublePage = state.isDoublePage,
        showCoverAlone = state.showCoverAlone,
        rightToLeft = state.rightToLeft,
    )
    var renderedPages by remember(renderer) {
        mutableStateOf<Map<Int, ImageBitmap>>(emptyMap())
    }
    var cachedRenderSize by remember(renderer) {
        mutableStateOf<AndroidPdfRenderSize?>(null)
    }
    var displayedSpread by remember(renderer) {
        mutableStateOf<AndroidPdfDisplayedSpread?>(null)
    }
    var transitionSpread by remember(renderer) {
        mutableStateOf<AndroidPdfDisplayedSpread?>(null)
    }
    var transitionSlotSign by remember(renderer) { mutableIntStateOf(0) }
    var targetRenderError by remember(renderer) { mutableStateOf<String?>(null) }
    var renderRetryGeneration by remember(renderer) { mutableIntStateOf(0) }
    var viewportSize by remember(renderer) { mutableStateOf(IntSize.Zero) }
    var zoomScale by remember(renderer) { mutableFloatStateOf(1f) }
    var panOffset by remember(renderer) { mutableStateOf(Offset.Zero) }
    var isPinching by remember(renderer) { mutableStateOf(false) }
    var isDraggingPage by remember(renderer) { mutableStateOf(false) }
    var pageDragOffset by remember(renderer) { mutableFloatStateOf(0f) }
    var pageDragLogicalDelta by remember(renderer) { mutableIntStateOf(0) }
    var isPageAnimating by remember(renderer) { mutableStateOf(false) }
    var isTemporaryZoom by remember(renderer) { mutableStateOf(false) }
    val animatedPageOffset = remember(renderer) { Animatable(0f) }
    val latestRenderedPages = rememberUpdatedState(renderedPages)
    val latestDisplayedSpread = rememberUpdatedState(displayedSpread)
    val latestViewportSize = rememberUpdatedState(viewportSize)
    val latestOnZoomActiveChange = rememberUpdatedState(onZoomActiveChange)

    DisposableEffect(renderer) {
        onDispose {
            latestOnZoomActiveChange.value(false)
        }
    }

    val displayedKey = displayedSpread?.spread?.pageIndices
    val contentFitSize = remember(displayedSpread, viewportSize) {
        displayedSpread?.combinedFitSize(viewportSize)
            ?: PdfPageFitSize(
                widthPx = viewportSize.width.coerceAtLeast(1),
                heightPx = viewportSize.height.coerceAtLeast(1),
            )
    }
    val mainAxisExtent = when (state.readerMode) {
        PdfReaderMode.PagedVertical -> viewportSize.height.toFloat()
        else -> viewportSize.width.toFloat()
    }.coerceAtLeast(1f)
    val visiblePageOffset = if (isDraggingPage) {
        pageDragOffset
    } else {
        animatedPageOffset.value
    }

    fun spreadAtDelta(
        from: PdfPageSpread,
        delta: Int,
    ): PdfPageSpread? {
        val targetSpreadIndex = from.spreadIndex + delta
        if (targetSpreadIndex !in 0 until from.spreadCount) return null
        val targetPageIndex = pdfSpreadAnchorAt(
            spreadIndex = targetSpreadIndex,
            pageCount = state.pageCount,
            isDoublePage = state.isDoublePage,
            showCoverAlone = state.showCoverAlone,
        )
        return pdfPageSpread(
            pageIndex = targetPageIndex,
            pageCount = state.pageCount,
            isDoublePage = state.isDoublePage,
            showCoverAlone = state.showCoverAlone,
            rightToLeft = state.rightToLeft,
        )
    }

    fun resolvedSpread(
        spread: PdfPageSpread,
        pages: Map<Int, ImageBitmap> = renderedPages,
        renderSize: AndroidPdfRenderSize? = cachedRenderSize,
    ): AndroidPdfDisplayedSpread? {
        val size = renderSize ?: return null
        if (!pdfSpreadIsResolved(spread.pageIndices, pages.keys)) return null
        return AndroidPdfDisplayedSpread(
            spread = spread,
            pages = spread.pageIndices.map { pageIndex ->
                AndroidPdfDisplayedPage(
                    pageIndex = pageIndex,
                    image = checkNotNull(pages[pageIndex]),
                )
            },
            renderSize = size,
        )
    }

    val gestureCandidate = pageDragLogicalDelta
        .takeIf { it != 0 }
        ?.let { logicalDelta ->
            displayedSpread
                ?.spread
                ?.let { spreadAtDelta(it, logicalDelta) }
                ?.let { resolvedSpread(it) }
        }
    val enteringSpread = transitionSpread ?: gestureCandidate
    val enteringSlotSign = when {
        transitionSpread != null -> transitionSlotSign
        gestureCandidate != null -> pdfPagedTargetSlotSign(
            logicalDelta = pageDragLogicalDelta,
            isVertical = state.readerMode == PdfReaderMode.PagedVertical,
            rightToLeft = state.rightToLeft,
        )
        else -> 0
    }

    LaunchedEffect(displayedKey, state.zoomDismissGeneration) {
        zoomScale = 1f
        panOffset = Offset.Zero
        isTemporaryZoom = false
        latestOnZoomActiveChange.value(false)
    }

    LaunchedEffect(viewportSize, contentFitSize, zoomScale) {
        val clamped = clampPdfPan(
            xPx = panOffset.x,
            yPx = panOffset.y,
            scale = zoomScale,
            contentWidthPx = contentFitSize.widthPx.toFloat(),
            contentHeightPx = contentFitSize.heightPx.toFloat(),
            viewportWidthPx = viewportSize.width.toFloat(),
            viewportHeightPx = viewportSize.height.toFloat(),
        )
        panOffset = Offset(clamped.xPx, clamped.yPx)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it },
    ) {
        val targetWidthPx = (
            if (state.isDoublePage && requestedSpread.pageIndices.size > 1) {
                constraints.maxWidth / 2
            } else {
                constraints.maxWidth
            }
            ).coerceIn(AndroidPdfPagedConfig.MinRenderDimensionPx, AndroidPdfPagedConfig.MaxRenderDimensionPx)
        val targetHeightPx = constraints.maxHeight.coerceIn(
            AndroidPdfPagedConfig.MinRenderDimensionPx,
            AndroidPdfPagedConfig.MaxRenderDimensionPx,
        )
        val renderSize = AndroidPdfRenderSize(targetWidthPx, targetHeightPx)
        val activePrefetchDirection = pageDragLogicalDelta.takeIf { it != 0 }
            ?: prefetchDirection
        val renderOrder = pdfRenderOrder(
            spread = requestedSpread,
            pageCount = state.pageCount,
            isDoublePage = state.isDoublePage,
            showCoverAlone = state.showCoverAlone,
            prefetchDirection = activePrefetchDirection,
            maxCachedPages = AndroidPdfPagedConfig.MaxCachedPages,
        )

        LaunchedEffect(
            renderer,
            state.pageCount,
            requestedSpread,
            renderSize,
            renderOrder,
            renderRetryGeneration,
            state.readerMode,
            state.rightToLeft,
            state.pageAnimationEnabled,
            mainAxisExtent,
        ) {
            val oldDisplayedPages = displayedSpread
                ?.pages
                ?.map(AndroidPdfDisplayedPage::pageIndex)
                .orEmpty()
            var nextCache = if (cachedRenderSize == renderSize) {
                renderedPages
                    .filterKeys { it in (oldDisplayedPages + requestedSpread.pageIndices) }
                    .toMutableMap()
            } else {
                mutableMapOf()
            }
            cachedRenderSize = renderSize
            renderedPages = nextCache.toMap()
            targetRenderError = null

            for (targetPage in requestedSpread.pageIndices) {
                if (nextCache[targetPage] != null) continue
                val image = try {
                    renderer.renderPage(
                        pageIndex = targetPage,
                        targetWidthPx = renderSize.widthPx,
                        targetHeightPx = renderSize.heightPx,
                    )
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    targetRenderError = ToreadStrings.openBookFailed(throwable.message)
                    return@LaunchedEffect
                }
                currentCoroutineContext().ensureActive()
                nextCache[targetPage] = image
                renderedPages = nextCache.toMap()
            }

            val targetDisplay = resolvedSpread(
                spread = requestedSpread,
                pages = nextCache,
                renderSize = renderSize,
            ) ?: return@LaunchedEffect
            val oldDisplay = displayedSpread
            val isSameRenderedSpread =
                oldDisplay?.spread?.pageIndices == targetDisplay.spread.pageIndices &&
                    oldDisplay.renderSize == targetDisplay.renderSize
            if (!isSameRenderedSpread && !isPageAnimating) {
                val logicalDelta = targetDisplay.spread.spreadIndex -
                    (oldDisplay?.spread?.spreadIndex ?: targetDisplay.spread.spreadIndex)
                val canAnimate =
                    oldDisplay != null &&
                        abs(logicalDelta) == 1 &&
                        state.pageAnimationEnabled &&
                        mainAxisExtent > 1f
                if (canAnimate) {
                    isPageAnimating = true
                    transitionSlotSign = pdfPagedTargetSlotSign(
                        logicalDelta = logicalDelta,
                        isVertical = state.readerMode == PdfReaderMode.PagedVertical,
                        rightToLeft = state.rightToLeft,
                    )
                    transitionSpread = targetDisplay
                    try {
                        animatedPageOffset.snapTo(0f)
                        animatedPageOffset.animateTo(
                            targetValue = -transitionSlotSign * mainAxisExtent,
                            animationSpec = tween(AndroidPdfPagedConfig.PageAnimationMillis),
                        )
                        currentCoroutineContext().ensureActive()
                        displayedSpread = targetDisplay
                        onDisplayedSpreadChanged(targetDisplay.spread)
                    } finally {
                        withContext(NonCancellable) {
                            animatedPageOffset.snapTo(0f)
                        }
                        transitionSpread = null
                        transitionSlotSign = 0
                        isPageAnimating = false
                    }
                } else {
                    displayedSpread = targetDisplay
                    onDisplayedSpreadChanged(targetDisplay.spread)
                }
            }
            targetRenderError = null

            // The previous displayed spread is transition-only. Release its
            // cache entries immediately after the complete target commits so
            // the bounded four-page window has room for a complete adjacent
            // double-page spread.
            nextCache = nextCache
                .filterKeys { it in requestedSpread.pageIndices }
                .toMutableMap()
            renderedPages = nextCache.toMap()

            for (prefetchPage in renderOrder) {
                if (prefetchPage in requestedSpread.pageIndices || nextCache[prefetchPage] != null) {
                    continue
                }
                if (nextCache.size >= AndroidPdfPagedConfig.MaxCachedPages) break
                val image = try {
                    renderer.renderPage(
                        pageIndex = prefetchPage,
                        targetWidthPx = renderSize.widthPx,
                        targetHeightPx = renderSize.heightPx,
                    )
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    continue
                }
                currentCoroutineContext().ensureActive()
                nextCache[prefetchPage] = image
                renderedPages = nextCache.toMap()
            }
            nextCache = nextCache
                .filterKeys { it in renderOrder }
                .entries
                .take(AndroidPdfPagedConfig.MaxCachedPages)
                .associateTo(linkedMapOf()) { it.toPair() }
            renderedPages = nextCache.toMap()
        }

        val currentDisplay = displayedSpread
        when {
            currentDisplay != null -> {
                enteringSpread?.let { nextDisplay ->
                    AndroidPdfSpreadLayer(
                        display = nextDisplay,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val targetOffset =
                                    (enteringSlotSign * mainAxisExtent) + visiblePageOffset
                                if (state.readerMode == PdfReaderMode.PagedVertical) {
                                    translationY = targetOffset
                                } else {
                                    translationX = targetOffset
                                }
                            },
                    )
                }
                AndroidPdfSpreadLayer(
                    display = currentDisplay,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (state.readerMode == PdfReaderMode.PagedVertical) {
                                translationY = visiblePageOffset
                            } else {
                                translationX = visiblePageOffset
                            }
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX += panOffset.x
                            translationY += panOffset.y
                        },
                )
            }

            targetRenderError == null -> AndroidPdfWholeViewportLoading()
        }
    }

    val inputEnabled =
        displayedSpread != null &&
            !isPageAnimating &&
            displayedSpread?.spread?.anchorPageIndex == requestedSpread.anchorPageIndex
    val pageTurnThreshold = mainAxisExtent * AndroidPdfPagedConfig.PageTurnThresholdFraction
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(
                displayedKey,
                viewportSize,
                contentFitSize,
                inputEnabled,
            ) {
                if (!inputEnabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var sawMultiplePointers = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            sawMultiplePointers = true
                            isPinching = true
                            val oldScale = zoomScale
                            val nextScale = (oldScale * event.calculateZoom()).coerceIn(
                                1f,
                                AndroidPdfPagedConfig.MaxZoomScale,
                            )
                            val centroid = event.changes
                                .filter { it.pressed }
                                .map { it.position }
                                .fold(Offset.Zero, Offset::plus)
                                .let { sum ->
                                    val count = event.changes.count { it.pressed }.coerceAtLeast(1)
                                    Offset(sum.x / count, sum.y / count)
                                }
                            val focusedPan = pdfPanAfterZoomAtPoint(
                                currentPan = PdfPanOffset(panOffset.x, panOffset.y),
                                currentScale = oldScale,
                                targetScale = nextScale,
                                focusXPx = centroid.x,
                                focusYPx = centroid.y,
                                contentWidthPx = contentFitSize.widthPx.toFloat(),
                                contentHeightPx = contentFitSize.heightPx.toFloat(),
                                viewportWidthPx = viewportSize.width.toFloat(),
                                viewportHeightPx = viewportSize.height.toFloat(),
                            )
                            val eventPan = event.calculatePan()
                            val clampedPan = clampPdfPan(
                                xPx = focusedPan.xPx + eventPan.x,
                                yPx = focusedPan.yPx + eventPan.y,
                                scale = nextScale,
                                contentWidthPx = contentFitSize.widthPx.toFloat(),
                                contentHeightPx = contentFitSize.heightPx.toFloat(),
                                viewportWidthPx = viewportSize.width.toFloat(),
                                viewportHeightPx = viewportSize.height.toFloat(),
                            )
                            if (nextScale <= 1f + AndroidPdfPagedConfig.ZoomEpsilon) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                latestOnZoomActiveChange.value(false)
                            } else {
                                zoomScale = nextScale
                                panOffset = Offset(clampedPan.xPx, clampedPan.yPx)
                                latestOnZoomActiveChange.value(true)
                            }
                            event.changes.forEach { change ->
                                if (change.position != change.previousPosition) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (sawMultiplePointers) isPinching = false
                }
            }
            .pointerInput(
                displayedKey,
                viewportSize,
                inputEnabled,
                state.readerMode,
                state.rightToLeft,
                state.pageAnimationEnabled,
            ) {
                if (!inputEnabled) return@pointerInput
                var dragX = 0f
                var dragY = 0f
                var panGesture = false
                val velocityTracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = {
                        dragX = 0f
                        dragY = 0f
                        velocityTracker.resetTracking()
                        panGesture = zoomScale > 1f + AndroidPdfPagedConfig.ZoomEpsilon
                        if (!panGesture && !isPinching) {
                            isDraggingPage = true
                            pageDragOffset = 0f
                            pageDragLogicalDelta = 0
                        }
                    },
                    onDragCancel = {
                        velocityTracker.resetTracking()
                        scope.launch {
                            isDraggingPage = false
                            try {
                                animatedPageOffset.snapTo(pageDragOffset)
                                animatedPageOffset.animateTo(
                                    0f,
                                    tween(AndroidPdfPagedConfig.PageAnimationMillis),
                                )
                            } finally {
                                withContext(NonCancellable) {
                                    animatedPageOffset.snapTo(0f)
                                }
                                pageDragOffset = 0f
                                pageDragLogicalDelta = 0
                            }
                        }
                    },
                    onDragEnd = {
                        if (panGesture || isPinching) {
                            velocityTracker.resetTracking()
                            if (isDraggingPage) {
                                isDraggingPage = false
                                pageDragOffset = 0f
                                pageDragLogicalDelta = 0
                                scope.launch {
                                    withContext(NonCancellable) {
                                        animatedPageOffset.snapTo(0f)
                                    }
                                }
                            }
                            return@detectDragGestures
                        }
                        val releasedOffset = pageDragOffset
                        val releasedRawOffset =
                            if (state.readerMode == PdfReaderMode.PagedVertical) {
                                dragY
                            } else {
                                dragX
                            }
                        val logicalDelta = pageDragLogicalDelta
                        val releaseVelocity = velocityTracker.calculateVelocity()
                        velocityTracker.resetTracking()
                        val mainAxisVelocity =
                            if (state.readerMode == PdfReaderMode.PagedVertical) {
                                releaseVelocity.y
                            } else {
                                releaseVelocity.x
                            }
                        val current = latestDisplayedSpread.value
                        val target = current
                            ?.spread
                            ?.let { spreadAtDelta(it, logicalDelta) }
                        val cachedTarget = target?.let { candidate ->
                            val size = cachedRenderSize
                            if (
                                size != null &&
                                pdfSpreadIsResolved(
                                    candidate.pageIndices,
                                    latestRenderedPages.value.keys,
                                )
                            ) {
                                AndroidPdfDisplayedSpread(
                                    spread = candidate,
                                    pages = candidate.pageIndices.map { pageIndex ->
                                        AndroidPdfDisplayedPage(
                                            pageIndex,
                                            checkNotNull(latestRenderedPages.value[pageIndex]),
                                        )
                                    },
                                    renderSize = size,
                                )
                            } else {
                                null
                            }
                        }
                        val shouldTurn =
                            logicalDelta != 0 &&
                                (
                                    abs(releasedRawOffset) >= pageTurnThreshold ||
                                        (
                                            abs(mainAxisVelocity) >=
                                                AndroidPdfPagedConfig
                                                    .MinimumFlingVelocityPxPerSecond &&
                                                mainAxisVelocity * releasedRawOffset > 0f
                                            )
                                    )
                        scope.launch {
                            isDraggingPage = false
                            try {
                                animatedPageOffset.snapTo(releasedOffset)
                                if (shouldTurn && target != null && cachedTarget != null) {
                                    isPageAnimating = true
                                    val slotSign = pdfPagedTargetSlotSign(
                                        logicalDelta = logicalDelta,
                                        isVertical =
                                            state.readerMode == PdfReaderMode.PagedVertical,
                                        rightToLeft = state.rightToLeft,
                                    )
                                    transitionSlotSign = slotSign
                                    transitionSpread = cachedTarget
                                    if (state.pageAnimationEnabled) {
                                        animatedPageOffset.animateTo(
                                            -slotSign * latestViewportSize.value.mainAxis(
                                                state.readerMode,
                                            ),
                                            tween(AndroidPdfPagedConfig.PageAnimationMillis),
                                        )
                                    }
                                    currentCoroutineContext().ensureActive()
                                    displayedSpread = cachedTarget
                                    onPageRequested(target.anchorPageIndex, logicalDelta)
                                    onDisplayedSpreadChanged(target)
                                } else {
                                    animatedPageOffset.animateTo(
                                        0f,
                                        tween(AndroidPdfPagedConfig.PageAnimationMillis),
                                    )
                                    currentCoroutineContext().ensureActive()
                                    if (shouldTurn && target != null) {
                                        onPageRequested(target.anchorPageIndex, logicalDelta)
                                    }
                                }
                            } finally {
                                withContext(NonCancellable) {
                                    animatedPageOffset.snapTo(0f)
                                }
                                transitionSpread = null
                                transitionSlotSign = 0
                                pageDragOffset = 0f
                                pageDragLogicalDelta = 0
                                isPageAnimating = false
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (isPinching) return@detectDragGestures
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                        if (panGesture) {
                            val clamped = clampPdfPan(
                                xPx = panOffset.x + dragAmount.x,
                                yPx = panOffset.y + dragAmount.y,
                                scale = zoomScale,
                                contentWidthPx = contentFitSize.widthPx.toFloat(),
                                contentHeightPx = contentFitSize.heightPx.toFloat(),
                                viewportWidthPx = viewportSize.width.toFloat(),
                                viewportHeightPx = viewportSize.height.toFloat(),
                            )
                            panOffset = Offset(clamped.xPx, clamped.yPx)
                        } else {
                            val mainDrag = if (state.readerMode == PdfReaderMode.PagedVertical) {
                                dragY
                            } else {
                                dragX
                            }
                            val logicalDelta = pdfPagedLogicalDeltaForDrag(
                                dragOffsetPx = mainDrag,
                                isVertical =
                                    state.readerMode == PdfReaderMode.PagedVertical,
                                rightToLeft = state.rightToLeft,
                            )
                            val candidate = latestDisplayedSpread.value
                                ?.spread
                                ?.let { spreadAtDelta(it, logicalDelta) }
                            pageDragLogicalDelta = if (candidate == null) 0 else logicalDelta
                            val candidateReady = candidate != null &&
                                pdfSpreadIsResolved(
                                    candidate.pageIndices,
                                    latestRenderedPages.value.keys,
                                )
                            pageDragOffset = when {
                                candidate == null ->
                                    mainDrag * AndroidPdfPagedConfig.EdgeResistance
                                candidateReady ->
                                    mainDrag.coerceIn(-mainAxisExtent, mainAxisExtent)
                                else ->
                                    mainDrag * AndroidPdfPagedConfig.UnresolvedPageResistance
                            }
                        }
                    },
                )
            }
            .pointerInput(
                displayedKey,
                viewportSize,
                inputEnabled,
                state.controlsVisible,
                state.tapToTurnPagesEnabled,
                state.reverseTapToTurnPages,
                state.doubleTapZoomEnabled,
                state.longPressZoomEnabled,
                state.readerMode,
                state.rightToLeft,
            ) {
                if (!inputEnabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        if (isTemporaryZoom) {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                            isTemporaryZoom = false
                            latestOnZoomActiveChange.value(false)
                        }
                    },
                    onLongPress = { focus ->
                        if (
                            state.longPressZoomEnabled &&
                            zoomScale <= 1f + AndroidPdfPagedConfig.ZoomEpsilon
                        ) {
                            val targetScale = AndroidPdfPagedConfig.TemporaryZoomScale
                            val nextPan = pdfPanAfterZoomAtPoint(
                                currentPan = PdfPanOffset(0f, 0f),
                                currentScale = 1f,
                                targetScale = targetScale,
                                focusXPx = focus.x,
                                focusYPx = focus.y,
                                contentWidthPx = contentFitSize.widthPx.toFloat(),
                                contentHeightPx = contentFitSize.heightPx.toFloat(),
                                viewportWidthPx = viewportSize.width.toFloat(),
                                viewportHeightPx = viewportSize.height.toFloat(),
                            )
                            zoomScale = targetScale
                            panOffset = Offset(nextPan.xPx, nextPan.yPx)
                            isTemporaryZoom = true
                            latestOnZoomActiveChange.value(true)
                        }
                    },
                    onDoubleTap = { focus ->
                        if (!state.doubleTapZoomEnabled) return@detectTapGestures
                        if (zoomScale > 1f + AndroidPdfPagedConfig.ZoomEpsilon) {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                            latestOnZoomActiveChange.value(false)
                        } else {
                            val targetScale = AndroidPdfPagedConfig.DoubleTapZoomScale
                            val nextPan = pdfPanAfterZoomAtPoint(
                                currentPan = PdfPanOffset(panOffset.x, panOffset.y),
                                currentScale = zoomScale,
                                targetScale = targetScale,
                                focusXPx = focus.x,
                                focusYPx = focus.y,
                                contentWidthPx = contentFitSize.widthPx.toFloat(),
                                contentHeightPx = contentFitSize.heightPx.toFloat(),
                                viewportWidthPx = viewportSize.width.toFloat(),
                                viewportHeightPx = viewportSize.height.toFloat(),
                            )
                            zoomScale = targetScale
                            panOffset = Offset(nextPan.xPx, nextPan.yPx)
                            latestOnZoomActiveChange.value(true)
                        }
                    },
                    onTap = { position ->
                        if (state.controlsVisible) {
                            onToggleControls()
                            return@detectTapGestures
                        }
                        val isVertical =
                            state.readerMode == PdfReaderMode.PagedVertical
                        val logicalDelta = pdfPagedLogicalDeltaForTap(
                            positionOnAxisPx = if (isVertical) position.y else position.x,
                            viewportExtentPx = if (isVertical) {
                                viewportSize.height.toFloat()
                            } else {
                                viewportSize.width.toFloat()
                            },
                            isVertical = isVertical,
                            rightToLeft = state.rightToLeft,
                            tapToTurnPagesEnabled = state.tapToTurnPagesEnabled,
                            reverseTapToTurnPages = state.reverseTapToTurnPages,
                            isZoomed = zoomScale >
                                1f + AndroidPdfPagedConfig.ZoomEpsilon,
                        )
                        when (logicalDelta) {
                            null -> onToggleControls()
                            0 -> Unit
                            else -> {
                                val current = displayedSpread?.spread ?: return@detectTapGestures
                                val target = spreadAtDelta(current, logicalDelta)
                                    ?: return@detectTapGestures
                                onPageRequested(target.anchorPageIndex, logicalDelta)
                            }
                        }
                    },
                )
            },
    )

    targetRenderError?.let { message ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = MaterialTheme.shapes.large,
                shadowElevation = 8.dp,
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = { renderRetryGeneration++ }) {
                        Text(ToreadStrings.Retry)
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidPdfSpreadLayer(
    display: AndroidPdfDisplayedSpread,
    modifier: Modifier,
) {
    val hasTwoPages = display.pages.size > 1
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        display.pages.forEachIndexed { displayIndex, page ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = when {
                    !hasTwoPages -> Alignment.Center
                    displayIndex == 0 -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                },
            ) {
                AndroidPdfPageImage(page)
            }
        }
    }
}

@Composable
private fun AndroidPdfPageImage(page: AndroidPdfDisplayedPage) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val fit = fitPdfPageInside(
            sourceWidthPx = page.image.width,
            sourceHeightPx = page.image.height,
            viewportWidthPx = constraints.maxWidth,
            viewportHeightPx = constraints.maxHeight,
        )
        Surface(
            modifier = Modifier.size(
                width = with(androidx.compose.ui.platform.LocalDensity.current) {
                    fit.widthPx.toDp()
                },
                height = with(androidx.compose.ui.platform.LocalDensity.current) {
                    fit.heightPx.toDp()
                },
            ),
            color = Color.White,
            shape = RectangleShape,
        ) {
            Image(
                bitmap = page.image,
                contentDescription = ToreadStrings.chapterPage(page.pageIndex),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun AndroidPdfWholeViewportLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = ToreadStrings.PdfRendering,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

private fun AndroidPdfDisplayedSpread.combinedFitSize(viewportSize: IntSize): PdfPageFitSize {
    val combinedWidth = pages.sumOf { it.image.width }
    val combinedHeight = pages.maxOfOrNull { it.image.height } ?: 0
    return fitPdfPageInside(
        sourceWidthPx = combinedWidth,
        sourceHeightPx = combinedHeight,
        viewportWidthPx = viewportSize.width,
        viewportHeightPx = viewportSize.height,
    )
}

private fun IntSize.mainAxis(readerMode: PdfReaderMode): Float =
    if (readerMode == PdfReaderMode.PagedVertical) {
        height.toFloat()
    } else {
        width.toFloat()
    }.coerceAtLeast(1f)

private data class AndroidPdfRenderSize(
    val widthPx: Int,
    val heightPx: Int,
)

private data class AndroidPdfDisplayedPage(
    val pageIndex: Int,
    val image: ImageBitmap,
)

private data class AndroidPdfDisplayedSpread(
    val spread: PdfPageSpread,
    val pages: List<AndroidPdfDisplayedPage>,
    val renderSize: AndroidPdfRenderSize,
)

private object AndroidPdfPagedConfig {
    const val MinRenderDimensionPx = 128
    const val MaxRenderDimensionPx = 2_048
    const val MaxCachedPages = 4
    const val PageAnimationMillis = 200
    const val PageTurnThresholdFraction = 0.16f
    const val MinimumFlingVelocityPxPerSecond = 900f
    const val EdgeResistance = 0.22f
    const val UnresolvedPageResistance = 0.35f
    const val DoubleTapZoomScale = 2f
    const val TemporaryZoomScale = 1.75f
    const val MaxZoomScale = 3f
    const val ZoomEpsilon = 0.01f
}
