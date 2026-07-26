package com.example.toread.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.toread.reader.PdfDocumentRenderer
import com.example.toread.ui.reader.PdfContinuousAnchor
import com.example.toread.ui.reader.PdfContinuousVisiblePage
import com.example.toread.ui.reader.PdfPanOffset
import com.example.toread.ui.reader.clampPdfPan
import com.example.toread.ui.reader.fitPdfPageInside
import com.example.toread.ui.reader.normalizePdfContinuousAnchor
import com.example.toread.ui.reader.pdfPanAfterZoomAtPoint
import com.example.toread.ui.reader.pdfContinuousAnchor
import com.example.toread.ui.reader.pdfContinuousRenderOrder
import com.example.toread.ui.reader.pdfContinuousRenderWidthBucket
import com.example.toread.ui.reader.pdfContinuousScrollOffsetPx
import com.example.toread.ui.text.ToreadStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Windowed Android PDF gallery for continuous vertical reading.
 *
 * [seekGeneration] must change for every external seek. Visible-position
 * updates deliberately do not seek the list again, which keeps scroll updates
 * from feeding back into navigation.
 */
@Composable
internal fun AndroidPdfContinuousViewport(
    renderer: PdfDocumentRenderer,
    pageCount: Int,
    targetPageIndex: Int,
    targetPageOffsetY: Double,
    seekGeneration: Int,
    zoomDismissGeneration: Int,
    renderRetryGeneration: Int,
    showPageNumber: Boolean,
    doubleTapZoomEnabled: Boolean,
    longPressZoomEnabled: Boolean,
    onToggleControls: () -> Unit,
    onUserInteraction: () -> Unit,
    onZoomActiveChange: (Boolean) -> Unit,
    onPositionChanged: (
        pageIndex: Int,
        pageOffsetY: Double,
        settled: Boolean,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val currentOnToggleControls by rememberUpdatedState(onToggleControls)
    val currentOnUserInteraction by rememberUpdatedState(onUserInteraction)
    val currentOnPositionChanged by rememberUpdatedState(onPositionChanged)
    val currentOnZoomActiveChange by rememberUpdatedState(onZoomActiveChange)
    var viewportSize by remember(renderer) { mutableStateOf(IntSize.Zero) }
    var renderedPages by remember(renderer) {
        mutableStateOf<Map<Int, PdfContinuousRenderedPage>>(emptyMap())
    }
    val pageAspectRatios = remember(renderer) { mutableStateMapOf<Int, Float>() }
    val renderErrors = remember(renderer) { mutableStateMapOf<Int, String>() }
    val localRetryGenerations = remember(renderer) { mutableStateMapOf<Int, Int>() }
    var scrollDirection by remember(renderer) { mutableIntStateOf(1) }
    var zoomedPageIndex by remember(renderer) { mutableStateOf<Int?>(null) }
    var zoomInitialScale by remember(renderer) { mutableStateOf(ContinuousPdfDoubleTapZoomScale) }
    var zoomInitialFocus by remember(renderer) { mutableStateOf<Offset?>(null) }
    var temporaryZoomActive by remember(renderer) { mutableStateOf(false) }

    DisposableEffect(renderer) {
        onDispose {
            currentOnZoomActiveChange(false)
        }
    }

    LaunchedEffect(
        renderer,
        seekGeneration,
        targetPageIndex,
        targetPageOffsetY,
        zoomDismissGeneration,
    ) {
        if (zoomedPageIndex != null) {
            zoomedPageIndex = null
            zoomInitialFocus = null
            temporaryZoomActive = false
            currentOnZoomActiveChange(false)
        }
    }

    val horizontalPaddingPx = with(density) {
        (ContinuousPdfHorizontalPadding * 2).roundToPx()
    }
    val maximumPageWidthPx = with(density) {
        ContinuousPdfMaximumPageWidth.roundToPx()
    }
    val availablePageWidthPx = (
        viewportSize.width - horizontalPaddingPx
        ).coerceAtMost(maximumPageWidthPx)
    val renderWidthPx = pdfContinuousRenderWidthBucket(
        widthPx = availablePageWidthPx,
        minimumWidthPx = ContinuousPdfMinimumRenderDimensionPx,
        maximumWidthPx = ContinuousPdfMaximumRenderDimensionPx,
        bucketSizePx = ContinuousPdfRenderWidthBucketPx,
    )

    val visiblePages by remember(listState, pageCount) {
        derivedStateOf {
            listState.visibleContinuousPdfPages(pageCount)
        }
    }
    val visibleAnchor by remember(listState, pageCount) {
        derivedStateOf {
            pdfContinuousAnchor(
                visiblePages = listState.visibleContinuousPdfPages(pageCount),
                viewportStartOffsetPx = listState.layoutInfo.viewportStartOffset,
                pageCount = pageCount,
            )
        }
    }
    val baseRenderOrder by remember(
        listState,
        pageCount,
        targetPageIndex,
        scrollDirection,
    ) {
        derivedStateOf {
            pdfContinuousRenderOrder(
                anchorPageIndex = visibleAnchor?.pageIndex ?: targetPageIndex,
                visiblePageIndices = visiblePages.map(PdfContinuousVisiblePage::pageIndex),
                scrollDirection = scrollDirection,
                pageCount = pageCount,
                maxCachedPages = ContinuousPdfMaximumCachedPages,
            )
        }
    }
    val renderOrder = remember(baseRenderOrder, zoomedPageIndex, pageCount) {
        buildList {
            zoomedPageIndex
                ?.takeIf { it in 0 until pageCount }
                ?.let(::add)
            addAll(baseRenderOrder)
        }.distinct().take(ContinuousPdfMaximumCachedPages)
    }
    val currentRenderedPages by rememberUpdatedState(renderedPages)
    val currentVisibleAnchor by rememberUpdatedState(visibleAnchor)
    val currentZoomedPageIndex by rememberUpdatedState(zoomedPageIndex)
    val retryKey = renderOrder.map { pageIndex ->
        localRetryGenerations[pageIndex] ?: 0
    }

    LaunchedEffect(renderer, pageCount) {
        renderedPages = emptyMap()
        pageAspectRatios.clear()
        renderErrors.clear()
        localRetryGenerations.clear()
    }

    LaunchedEffect(
        renderer,
        pageCount,
        renderOrder,
        renderWidthPx,
        renderRetryGeneration,
        retryKey,
    ) {
        if (pageCount <= 0 || renderOrder.isEmpty() || renderWidthPx <= 0) {
            renderedPages = emptyMap()
            return@LaunchedEffect
        }

        var nextCache = renderedPages
            .filter { (pageIndex, page) ->
                pageIndex in renderOrder && page.renderWidthPx == renderWidthPx
            }
            .toMutableMap()
        renderedPages = nextCache.toMap()
        renderErrors.keys
            .filter { pageIndex -> pageIndex !in renderOrder }
            .forEach { pageIndex -> renderErrors.remove(pageIndex) }

        for (pageIndex in renderOrder) {
            currentCoroutineContext().ensureActive()
            if (nextCache[pageIndex] != null) {
                renderErrors.remove(pageIndex)
                continue
            }

            while (nextCache.size >= ContinuousPdfMaximumCachedPages) {
                val evictionCandidate = nextCache.keys
                    .maxByOrNull { cachedPage -> renderOrder.indexOf(cachedPage) }
                    ?: break
                nextCache.remove(evictionCandidate)
            }
            renderedPages = nextCache.toMap()

            val image = try {
                renderer.renderPage(
                    pageIndex = pageIndex,
                    targetWidthPx = renderWidthPx,
                    targetHeightPx = ContinuousPdfMaximumRenderDimensionPx,
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                renderErrors[pageIndex] = ToreadStrings.openBookFailed(throwable.message)
                continue
            }
            currentCoroutineContext().ensureActive()

            if (image.width > 0 && image.height > 0) {
                pageAspectRatios[pageIndex] = (
                    image.width.toFloat() / image.height.toFloat()
                    ).coerceIn(
                    ContinuousPdfMinimumAspectRatio,
                    ContinuousPdfMaximumAspectRatio,
                )
            }
            nextCache[pageIndex] = PdfContinuousRenderedPage(
                image = image,
                renderWidthPx = renderWidthPx,
            )
            check(nextCache.size <= ContinuousPdfMaximumCachedPages)
            renderedPages = nextCache.toMap()
            renderErrors.remove(pageIndex)
        }
    }

    LaunchedEffect(
        renderer,
        pageCount,
        seekGeneration,
        targetPageIndex,
        targetPageOffsetY,
    ) {
        if (pageCount <= 0) return@LaunchedEffect
        val target = normalizePdfContinuousAnchor(
            pageIndex = targetPageIndex,
            pageOffsetY = targetPageOffsetY,
            pageCount = pageCount,
        )
        listState.scrollToItem(target.pageIndex)
        listState.restoreContinuousPdfOffset(target)

        if (
            target.pageOffsetY > 0.0 &&
            pageAspectRatios[target.pageIndex] == null &&
            renderErrors[target.pageIndex] == null
        ) {
            snapshotFlow { pageAspectRatios[target.pageIndex] }
                .filterNotNull()
                .first()
            listState.restoreContinuousPdfOffset(target)
        }
    }

    LaunchedEffect(listState, pageCount) {
        var previousPageIndex: Int? = null
        snapshotFlow {
            pdfContinuousAnchor(
                visiblePages = listState.visibleContinuousPdfPages(pageCount),
                viewportStartOffsetPx = listState.layoutInfo.viewportStartOffset,
                pageCount = pageCount,
            )
        }
            .filterNotNull()
            .collect { anchor ->
                val previousPage = previousPageIndex
                if (previousPage != null && previousPage != anchor.pageIndex) {
                    scrollDirection = if (anchor.pageIndex < previousPage) -1 else 1
                }
                currentOnPositionChanged(
                    anchor.pageIndex,
                    anchor.pageOffsetY,
                    false,
                )
                previousPageIndex = anchor.pageIndex
            }
    }

    LaunchedEffect(listState, pageCount) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { scrolling -> !scrolling }
            .collect {
                val anchor = pdfContinuousAnchor(
                    visiblePages = listState.visibleContinuousPdfPages(pageCount),
                    viewportStartOffsetPx = listState.layoutInfo.viewportStartOffset,
                    pageCount = pageCount,
                ) ?: return@collect
                currentOnPositionChanged(
                    anchor.pageIndex,
                    anchor.pageOffsetY,
                    true,
                )
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { viewportSize = it },
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = zoomedPageIndex == null,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        var accumulatedMovement = Offset.Zero
                        var interactionReported = false
                        do {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            event.changes.forEach { change ->
                                accumulatedMovement +=
                                    change.position - change.previousPosition
                            }
                            if (
                                !interactionReported &&
                                kotlin.math.abs(accumulatedMovement.y) >=
                                viewConfiguration.touchSlop &&
                                kotlin.math.abs(accumulatedMovement.y) >
                                kotlin.math.abs(accumulatedMovement.x)
                            ) {
                                interactionReported = true
                                currentOnUserInteraction()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var pinchScale = ContinuousPdfPinchEntryZoomScale
                        var pinchPageIndex: Int? = null
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val pressedPointers = event.changes.filter { it.pressed }
                                val centroid = pressedPointers
                                    .map { it.position }
                                    .fold(Offset.Zero, Offset::plus)
                                    .let { sum ->
                                        Offset(
                                            x = sum.x / pressedPointers.size,
                                            y = sum.y / pressedPointers.size,
                                        )
                                    }
                                val anchorPage = pinchPageIndex
                                    ?: listState.pageAtViewportY(centroid.y)
                                    ?: currentVisibleAnchor?.pageIndex
                                if (
                                    anchorPage != null &&
                                    currentRenderedPages[anchorPage] != null
                                ) {
                                    pinchPageIndex = anchorPage
                                    pinchScale = (
                                        pinchScale * event.calculateZoom()
                                        ).coerceIn(
                                        ContinuousPdfPinchEntryZoomScale,
                                        ContinuousPdfMaximumZoomScale,
                                    )
                                    zoomInitialScale = pinchScale
                                    zoomInitialFocus = centroid
                                    temporaryZoomActive = false
                                    if (currentZoomedPageIndex == null) {
                                        zoomedPageIndex = anchorPage
                                        currentOnZoomActiveChange(true)
                                    }
                                    event.changes.forEach { change ->
                                        if (change.position != change.previousPosition) {
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(
                    doubleTapZoomEnabled,
                    longPressZoomEnabled,
                ) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            if (temporaryZoomActive) {
                                zoomedPageIndex = null
                                zoomInitialFocus = null
                                temporaryZoomActive = false
                                currentOnZoomActiveChange(false)
                            }
                        },
                        onLongPress = { position ->
                            if (!longPressZoomEnabled) return@detectTapGestures
                            val page = listState.pageAtViewportY(position.y)
                                ?: currentVisibleAnchor?.pageIndex
                                ?: return@detectTapGestures
                            if (currentRenderedPages[page] != null) {
                                zoomInitialScale = ContinuousPdfTemporaryZoomScale
                                zoomInitialFocus = position
                                temporaryZoomActive = true
                                zoomedPageIndex = page
                                currentOnZoomActiveChange(true)
                            }
                        },
                        onDoubleTap = { position ->
                            if (!doubleTapZoomEnabled) return@detectTapGestures
                            val page = listState.pageAtViewportY(position.y)
                                ?: currentVisibleAnchor?.pageIndex
                                ?: return@detectTapGestures
                            if (currentRenderedPages[page] != null) {
                                zoomInitialScale = ContinuousPdfDoubleTapZoomScale
                                zoomInitialFocus = position
                                temporaryZoomActive = false
                                zoomedPageIndex = page
                                currentOnZoomActiveChange(true)
                            }
                        },
                        onTap = { currentOnToggleControls() },
                    )
                },
            contentPadding = PaddingValues(vertical = ContinuousPdfVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(ContinuousPdfPageSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(
                count = pageCount.coerceAtLeast(0),
                key = { pageIndex -> pageIndex },
            ) { pageIndex ->
                val renderedPage = renderedPages[pageIndex]
                val aspectRatio = (
                    pageAspectRatios[pageIndex] ?: ContinuousPdfDefaultPageAspectRatio
                    ).coerceIn(
                    ContinuousPdfMinimumAspectRatio,
                    ContinuousPdfMaximumAspectRatio,
                )
                AndroidContinuousPdfPage(
                    pageIndex = pageIndex,
                    image = renderedPage?.image,
                    aspectRatio = aspectRatio,
                    error = renderErrors[pageIndex],
                    showPageNumber = showPageNumber,
                    onRetry = {
                        renderErrors.remove(pageIndex)
                        renderedPages = renderedPages - pageIndex
                        localRetryGenerations[pageIndex] =
                            (localRetryGenerations[pageIndex] ?: 0) + 1
                    },
                )
            }
        }

        val zoomedPage = zoomedPageIndex
        val zoomedImage = zoomedPage?.let { renderedPages[it]?.image }
        if (zoomedPage != null && zoomedImage != null) {
            AndroidContinuousPdfZoomOverlay(
                pageIndex = zoomedPage,
                image = zoomedImage,
                initialScale = zoomInitialScale,
                initialFocus = zoomInitialFocus,
                onClose = {
                    zoomedPageIndex = null
                    zoomInitialFocus = null
                    temporaryZoomActive = false
                    currentOnZoomActiveChange(false)
                },
                onToggleControls = currentOnToggleControls,
            )
        }
    }
}

@Composable
private fun AndroidContinuousPdfZoomOverlay(
    pageIndex: Int,
    image: ImageBitmap,
    initialScale: Float,
    initialFocus: Offset?,
    onClose: () -> Unit,
    onToggleControls: () -> Unit,
) {
    var viewportSize by remember(pageIndex) { mutableStateOf(IntSize.Zero) }
    var zoomScale by remember(pageIndex) {
        mutableStateOf(initialScale.coerceIn(1f, ContinuousPdfMaximumZoomScale))
    }
    var panOffset by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    val fittedSize = remember(image, viewportSize) {
        fitPdfPageInside(
            sourceWidthPx = image.width,
            sourceHeightPx = image.height,
            viewportWidthPx = viewportSize.width,
            viewportHeightPx = viewportSize.height,
        )
    }

    LaunchedEffect(pageIndex, initialScale, initialFocus, viewportSize, fittedSize) {
        val targetScale = initialScale.coerceIn(1f, ContinuousPdfMaximumZoomScale)
        val focus = initialFocus ?: Offset(
            x = viewportSize.width / 2f,
            y = viewportSize.height / 2f,
        )
        val initialPan = pdfPanAfterZoomAtPoint(
            currentPan = PdfPanOffset(0f, 0f),
            currentScale = 1f,
            targetScale = targetScale,
            focusXPx = focus.x,
            focusYPx = focus.y,
            contentWidthPx = fittedSize.widthPx.toFloat(),
            contentHeightPx = fittedSize.heightPx.toFloat(),
            viewportWidthPx = viewportSize.width.toFloat(),
            viewportHeightPx = viewportSize.height.toFloat(),
        )
        zoomScale = targetScale
        panOffset = Offset(initialPan.xPx, initialPan.yPx)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { viewportSize = it }
            .pointerInput(pageIndex, viewportSize, fittedSize) {
                detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                    val oldScale = zoomScale
                    val nextScale = (oldScale * gestureZoom).coerceIn(
                        1f,
                        ContinuousPdfMaximumZoomScale,
                    )
                    val focusedPan = pdfPanAfterZoomAtPoint(
                        currentPan = PdfPanOffset(panOffset.x, panOffset.y),
                        currentScale = oldScale,
                        targetScale = nextScale,
                        focusXPx = centroid.x,
                        focusYPx = centroid.y,
                        contentWidthPx = fittedSize.widthPx.toFloat(),
                        contentHeightPx = fittedSize.heightPx.toFloat(),
                        viewportWidthPx = viewportSize.width.toFloat(),
                        viewportHeightPx = viewportSize.height.toFloat(),
                    )
                    val clampedPan = clampPdfPan(
                        xPx = focusedPan.xPx + gesturePan.x,
                        yPx = focusedPan.yPx + gesturePan.y,
                        scale = nextScale,
                        contentWidthPx = fittedSize.widthPx.toFloat(),
                        contentHeightPx = fittedSize.heightPx.toFloat(),
                        viewportWidthPx = viewportSize.width.toFloat(),
                        viewportHeightPx = viewportSize.height.toFloat(),
                    )
                    zoomScale = nextScale
                    panOffset = if (nextScale <= 1f + ContinuousPdfZoomEpsilon) {
                        Offset.Zero
                    } else {
                        Offset(clampedPan.xPx, clampedPan.yPx)
                    }
                }
            }
            .pointerInput(pageIndex, zoomScale) {
                detectTapGestures(
                    onDoubleTap = {
                        if (zoomScale > 1f + ContinuousPdfZoomEpsilon) {
                            onClose()
                        } else {
                            zoomScale = ContinuousPdfDoubleTapZoomScale
                            panOffset = Offset.Zero
                        }
                    },
                    onTap = {
                        onClose()
                        onToggleControls()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = image,
            contentDescription = ToreadStrings.chapterPage(pageIndex),
            modifier = Modifier
                .size(
                    width = with(LocalDensity.current) { fittedSize.widthPx.toDp() },
                    height = with(LocalDensity.current) { fittedSize.heightPx.toDp() },
                )
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun AndroidContinuousPdfPage(
    pageIndex: Int,
    image: ImageBitmap?,
    aspectRatio: Float,
    error: String?,
    showPageNumber: Boolean,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = ContinuousPdfHorizontalPadding)
            .widthIn(max = ContinuousPdfMaximumPageWidth)
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        color = if (image != null) Color.White else MaterialTheme.colorScheme.surfaceVariant,
        shape = androidx.compose.ui.graphics.RectangleShape,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                image != null -> {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                error != null -> {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = onRetry) {
                            Text(ToreadStrings.Retry)
                        }
                    }
                }
                else -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (showPageNumber) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = "${pageIndex + 1}",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

private fun LazyListState.visibleContinuousPdfPages(
    pageCount: Int,
): List<PdfContinuousVisiblePage> =
    layoutInfo.visibleItemsInfo.mapNotNull { item ->
        item.index
            .takeIf { pageIndex -> pageIndex in 0 until pageCount }
            ?.let { pageIndex ->
                PdfContinuousVisiblePage(
                    pageIndex = pageIndex,
                    offsetPx = item.offset,
                    sizePx = item.size,
                )
            }
    }

private fun LazyListState.pageAtViewportY(yPx: Float): Int? =
    layoutInfo.visibleItemsInfo
        .firstOrNull { item ->
            yPx >= item.offset && yPx < item.offset + item.size
        }
        ?.index

private suspend fun LazyListState.restoreContinuousPdfOffset(
    anchor: PdfContinuousAnchor,
) {
    val item = snapshotFlow {
        layoutInfo.visibleItemsInfo.firstOrNull { it.index == anchor.pageIndex }
    }
        .filterNotNull()
        .first()
    scrollToItem(
        index = anchor.pageIndex,
        scrollOffset = pdfContinuousScrollOffsetPx(
            pageOffsetY = anchor.pageOffsetY,
            itemSizePx = item.size,
        ),
    )
}

private data class PdfContinuousRenderedPage(
    val image: ImageBitmap,
    val renderWidthPx: Int,
)

private const val ContinuousPdfMaximumCachedPages = 4
private const val ContinuousPdfMinimumRenderDimensionPx = 128
private const val ContinuousPdfMaximumRenderDimensionPx = 2_048
private const val ContinuousPdfRenderWidthBucketPx = 64
private const val ContinuousPdfDefaultPageAspectRatio = 0.707_106_77f
private const val ContinuousPdfMinimumAspectRatio = 0.1f
private const val ContinuousPdfMaximumAspectRatio = 10f
private const val ContinuousPdfDoubleTapZoomScale = 2f
private const val ContinuousPdfTemporaryZoomScale = 1.75f
private const val ContinuousPdfPinchEntryZoomScale = 1.25f
private const val ContinuousPdfMaximumZoomScale = 3f
private const val ContinuousPdfZoomEpsilon = 0.01f
private val ContinuousPdfMaximumPageWidth = 840.dp
private val ContinuousPdfHorizontalPadding = 8.dp
private val ContinuousPdfVerticalPadding = 8.dp
private val ContinuousPdfPageSpacing = 8.dp
