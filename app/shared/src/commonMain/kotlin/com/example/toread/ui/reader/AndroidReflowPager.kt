package com.example.toread.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Swipe paging for the Android EPUB/TXT reflow reader.
 *
 * The pager only consumes the pages the host already materialized (the current page plus its
 * neighbours inside the bounded reflow window), so it never widens the window, starts its own
 * parsing, or adds a second page cache. Page turns are delegated to the host callbacks, which keeps
 * the existing local-first progress and sync path untouched.
 */
internal enum class ReflowSwipeAction {
    Previous,
    Next,
    Cancel,
}

internal enum class ReflowTapAction {
    Previous,
    ToggleControls,
    Next,
}

internal data class ReflowPagerPages(
    val current: ReaderPageSlice?,
    val previous: ReaderPageSlice?,
    val next: ReaderPageSlice?,
)

/** Which directions may turn and whether their neighbour page is available for a sliding preview. */
internal data class ReflowDragBounds(
    val canTurnNext: Boolean,
    val canTurnPrevious: Boolean,
    val hasNextPreview: Boolean,
    val hasPreviousPreview: Boolean,
)

internal object ReflowPagerConfig {
    /** Turn distance also scales with the page width so wide pages behave like narrow ones. */
    const val TurnDistanceFraction = 0.16f

    /** A quick flick past this speed turns the page even when the travel stays under the distance rule. */
    const val MinimumFlingVelocityPxPerSecond = 900f

    /** Kept in step with the Android PDF paged viewport so both readers feel the same. */
    const val EdgeResistance = 0.22f
    const val UnresolvedNeighbourResistance = 0.35f

    /** A dragged turn slides the page under the finger and keeps its momentum. */
    const val CommitAnimationMillis = 220

    /** A tap has no drag momentum, so it slides the whole page in one short, even step. */
    const val TapTurnAnimationMillis = 50

    /** Spring-back for a rejected or unavailable turn. */
    const val CancelAnimationMillis = 160

    /** How long a committed turn waits for the host to publish the new page before reverting. */
    const val PageChangeTimeoutMillis = 250L

    const val PreviousTapFraction = 1f / 3f
    const val NextTapFraction = 2f / 3f

    val MinimumTurnDistance = 56.dp
}

/**
 * Decides whether releasing a horizontal drag turns the page.
 *
 * Distance wins over velocity when both qualify, and a mostly vertical drag never turns a page.
 */
internal fun reflowSwipeAction(
    dragXPx: Float,
    dragYPx: Float,
    pageWidthPx: Float,
    velocityPxPerSecond: Float,
    minimumTurnDistancePx: Float,
    canPrevious: Boolean,
    canNext: Boolean,
): ReflowSwipeAction {
    if (pageWidthPx <= 0f) return ReflowSwipeAction.Cancel
    if (abs(dragXPx) <= abs(dragYPx)) return ReflowSwipeAction.Cancel

    val turnDistance = maxOf(
        minimumTurnDistancePx.coerceAtLeast(0f),
        pageWidthPx * ReflowPagerConfig.TurnDistanceFraction,
    )
    val travelledFarEnough = abs(dragXPx) >= turnDistance
    val flickedFastEnough =
        abs(velocityPxPerSecond) >= ReflowPagerConfig.MinimumFlingVelocityPxPerSecond
    if (!travelledFarEnough && !flickedFastEnough) return ReflowSwipeAction.Cancel

    val towardsNext = if (travelledFarEnough) dragXPx < 0f else velocityPxPerSecond < 0f
    return when {
        towardsNext -> if (canNext) ReflowSwipeAction.Next else ReflowSwipeAction.Cancel
        else -> if (canPrevious) ReflowSwipeAction.Previous else ReflowSwipeAction.Cancel
    }
}

/** Tap zones keep the left/center/right thirds the reader had before it supported dragging. */
internal fun reflowTapAction(horizontalFraction: Float): ReflowTapAction =
    when {
        horizontalFraction < ReflowPagerConfig.PreviousTapFraction -> ReflowTapAction.Previous
        horizontalFraction > ReflowPagerConfig.NextTapFraction -> ReflowTapAction.Next
        else -> ReflowTapAction.ToggleControls
    }

/**
 * Offset for the page under the finger.
 *
 * A direction without a neighbour page resists the drag and a direction without a turn stays
 * heavily damped, so a page can never expose an empty half or scroll past its own edge.
 */
internal fun reflowDragOffsetPx(
    startOffsetPx: Float,
    dragXPx: Float,
    pageWidthPx: Float,
    bounds: ReflowDragBounds,
): Float {
    if (pageWidthPx <= 0f) return 0f
    val requestedTowardsNext = startOffsetPx + dragXPx < 0f
    val resistance = when {
        requestedTowardsNext && !bounds.canTurnNext -> ReflowPagerConfig.EdgeResistance
        !requestedTowardsNext && !bounds.canTurnPrevious -> ReflowPagerConfig.EdgeResistance
        requestedTowardsNext && !bounds.hasNextPreview -> ReflowPagerConfig.UnresolvedNeighbourResistance
        !requestedTowardsNext && !bounds.hasPreviousPreview -> ReflowPagerConfig.UnresolvedNeighbourResistance
        else -> 1f
    }
    return (startOffsetPx + dragXPx * resistance).coerceIn(-pageWidthPx, pageWidthPx)
}

/**
 * Hosts one reflow page plus its neighbours, follows the finger horizontally, and turns the page
 * through [onPrevious]/[onNext] once the drag or tap commits.
 *
 * [pageIdentity] identifies the visible page. The pager state is keyed on it so a page turn drops the
 * transient offset while composing the new page: the neighbour slots shift by one page, and an offset
 * kept for even a single frame would reveal the page after next.
 */
@Composable
internal fun AndroidReflowPager(
    pages: ReflowPagerPages,
    pageIdentity: Any,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTapCenter: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pageContent: @Composable (ReaderPageSlice) -> Unit,
) {
    val latestPages by rememberUpdatedState(pages)
    val latestCanPrevious by rememberUpdatedState(canPrevious)
    val latestCanNext by rememberUpdatedState(canNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnTapCenter by rememberUpdatedState(onTapCenter)
    val latestIdentity by rememberUpdatedState(pageIdentity)

    key(pageIdentity) {
        val scope = rememberCoroutineScope()
        val animatedOffset = remember { Animatable(0f) }
        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        var isDragging by remember { mutableStateOf(false) }
        var settleJob by remember { mutableStateOf<Job?>(null) }

        BoxWithConstraints(modifier = modifier) {
            val density = LocalDensity.current
            val pageWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val minimumTurnDistancePx =
                with(density) { ReflowPagerConfig.MinimumTurnDistance.toPx() }
            val visibleOffsetProvider = { if (isDragging) dragOffsetPx else animatedOffset.value }

            suspend fun animateOffsetTo(target: Float, durationMillis: Int) =
                animatedOffset.animateTo(
                    targetValue = target,
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                )

            fun requestTurn(action: ReflowSwipeAction) {
                when (action) {
                    ReflowSwipeAction.Next -> latestOnNext()
                    ReflowSwipeAction.Previous -> latestOnPrevious()
                    ReflowSwipeAction.Cancel -> Unit
                }
            }

            fun runTurn(
                action: ReflowSwipeAction,
                fromDragOffset: Boolean,
                commitMillis: Int,
            ) {
                val startIdentity = latestIdentity
                settleJob?.cancel()
                settleJob = scope.launch {
                    try {
                        if (fromDragOffset) {
                            animatedOffset.snapTo(dragOffsetPx)
                        }
                        when (action) {
                            ReflowSwipeAction.Cancel ->
                                animateOffsetTo(0f, ReflowPagerConfig.CancelAnimationMillis)

                            ReflowSwipeAction.Next,
                            ReflowSwipeAction.Previous,
                            -> {
                                val hasNeighbourPreview = if (action == ReflowSwipeAction.Next) {
                                    latestPages.next != null
                                } else {
                                    latestPages.previous != null
                                }
                                if (!hasNeighbourPreview) {
                                    // Sliding without a preview would expose an empty half, so the
                                    // host turns the page immediately and the page springs back.
                                    requestTurn(action)
                                    animateOffsetTo(0f, ReflowPagerConfig.CancelAnimationMillis)
                                    return@launch
                                }
                                val target = if (action == ReflowSwipeAction.Next) {
                                    -pageWidthPx
                                } else {
                                    pageWidthPx
                                }
                                animateOffsetTo(target, commitMillis)
                                requestTurn(action)
                                val published = withTimeoutOrNull(
                                    ReflowPagerConfig.PageChangeTimeoutMillis,
                                ) {
                                    snapshotFlow { latestIdentity }.first { it != startIdentity }
                                } != null
                                if (!published) {
                                    animateOffsetTo(0f, ReflowPagerConfig.CancelAnimationMillis)
                                }
                            }
                        }
                    } finally {
                        withContext(NonCancellable) {
                            if (!isDragging) {
                                animatedOffset.snapTo(0f)
                                dragOffsetPx = 0f
                            }
                        }
                    }
                }
            }

            fun turnByTap(action: ReflowTapAction) {
                when (action) {
                    ReflowTapAction.Previous -> runTurn(
                        action = ReflowSwipeAction.Previous,
                        fromDragOffset = false,
                        commitMillis = ReflowPagerConfig.TapTurnAnimationMillis,
                    )

                    ReflowTapAction.Next -> runTurn(
                        action = ReflowSwipeAction.Next,
                        fromDragOffset = false,
                        commitMillis = ReflowPagerConfig.TapTurnAnimationMillis,
                    )

                    ReflowTapAction.ToggleControls -> latestOnTapCenter()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (!enabled) {
                            Modifier
                        } else {
                            Modifier.pointerInput(pageWidthPx, minimumTurnDistancePx) {
                                var totalX = 0f
                                var totalY = 0f
                                var startOffsetPx = 0f
                                var horizontalLocked = false
                                var verticalLocked = false
                                val touchSlop = viewConfiguration.touchSlop
                                val velocityTracker = VelocityTracker()
                                detectDragGestures(
                                    onDragStart = {
                                        settleJob?.cancel()
                                        totalX = 0f
                                        totalY = 0f
                                        startOffsetPx = animatedOffset.value
                                        dragOffsetPx = startOffsetPx
                                        horizontalLocked = false
                                        verticalLocked = false
                                        velocityTracker.resetTracking()
                                        isDragging = true
                                    },
                                    onDragCancel = {
                                        velocityTracker.resetTracking()
                                        isDragging = false
                                        runTurn(
                                            action = ReflowSwipeAction.Cancel,
                                            fromDragOffset = true,
                                            commitMillis = ReflowPagerConfig.CommitAnimationMillis,
                                        )
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        val velocity = velocityTracker.calculateVelocity().x
                                        velocityTracker.resetTracking()
                                        runTurn(
                                            action = reflowSwipeAction(
                                                dragXPx = totalX,
                                                dragYPx = totalY,
                                                pageWidthPx = pageWidthPx,
                                                velocityPxPerSecond = velocity,
                                                minimumTurnDistancePx = minimumTurnDistancePx,
                                                canPrevious = latestCanPrevious,
                                                canNext = latestCanNext,
                                            ),
                                            fromDragOffset = true,
                                            commitMillis = ReflowPagerConfig.CommitAnimationMillis,
                                        )
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        velocityTracker.addPosition(
                                            change.uptimeMillis,
                                            change.position,
                                        )
                                        totalX += dragAmount.x
                                        totalY += dragAmount.y
                                        if (!horizontalLocked && !verticalLocked) {
                                            when {
                                                abs(totalX) > abs(totalY) -> horizontalLocked = true
                                                abs(totalY) > touchSlop -> verticalLocked = true
                                            }
                                        }
                                        if (horizontalLocked) {
                                            dragOffsetPx = reflowDragOffsetPx(
                                                startOffsetPx = startOffsetPx,
                                                dragXPx = totalX,
                                                pageWidthPx = pageWidthPx,
                                                bounds = ReflowDragBounds(
                                                    canTurnNext = latestCanNext,
                                                    canTurnPrevious = latestCanPrevious,
                                                    hasNextPreview = latestPages.next != null,
                                                    hasPreviousPreview = latestPages.previous != null,
                                                ),
                                            )
                                        }
                                    },
                                )
                            }
                        },
                    )
                    .then(
                        if (!enabled) {
                            Modifier
                        } else {
                            Modifier.pointerInput(pageWidthPx) {
                                detectTapGestures { tapOffset ->
                                    val fraction = (tapOffset.x / pageWidthPx).coerceIn(0f, 1f)
                                    turnByTap(reflowTapAction(fraction))
                                }
                            }
                        },
                    ),
            ) {
                val previousPage = pages.previous
                val nextPage = pages.next
                val currentPage = pages.current
                if (previousPage != null) {
                    ReflowPagerSlot(
                        offsetProvider = { visibleOffsetProvider() - pageWidthPx },
                    ) {
                        pageContent(previousPage)
                    }
                }
                if (nextPage != null) {
                    ReflowPagerSlot(
                        offsetProvider = { visibleOffsetProvider() + pageWidthPx },
                    ) {
                        pageContent(nextPage)
                    }
                }
                if (currentPage != null) {
                    ReflowPagerSlot(offsetProvider = visibleOffsetProvider) {
                        pageContent(currentPage)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReflowPagerSlot(
    offsetProvider: () -> Float,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetProvider().roundToInt(), 0) },
    ) {
        content()
    }
}
