package com.example.toread.ui.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.toread.logging.ToreadClientLog
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Swipe paging for the Android EPUB/TXT reflow reader.
 *
 * The pager only consumes the pages the host already materialized (the current page plus its
 * neighbours inside the bounded reflow window), so it never widens the window, starts its own
 * parsing, or adds a second page cache. Page turns are delegated to the host callbacks, which keeps
 * the existing local-first progress and sync path untouched.
 *
 * A turn is a small state machine: the target page is resolved and rendered before the animation
 * starts, is locked for the whole turn, and the swap at the end resets the offset in the same state
 * write as the page change. Nothing about the transition depends on when the host publishes its new
 * window, which is what keeps a scrolling EPUB window from showing the page after next.
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

internal enum class ReflowFlipDirection {
    Forward,
    Backward,
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

/**
 * Horizontal tracking for one drag gesture.
 *
 * [trackedFromX] stays null while the accumulated movement is still vertical and is set once the
 * movement becomes horizontally dominant; the effective drag is measured from that point. The
 * decision is re-evaluated on every event, so a gesture that opens with a little vertical drift
 * still drags the page instead of staying frozen for its whole length.
 */
internal data class ReflowDragTracking(
    val trackedFromX: Float? = null,
) {
    val isTracking: Boolean get() = trackedFromX != null

    fun update(totalX: Float, totalY: Float): ReflowDragTracking =
        if (isTracking || abs(totalX) <= abs(totalY)) this else ReflowDragTracking(totalX)

    fun effectiveDragXPx(totalX: Float): Float = trackedFromX?.let { totalX - it } ?: 0f
}

/**
 * Pager-owned flip state.
 *
 * [isAnimating] debounces input for the whole turn, [incomingSlice] is the pre-rendered page that
 * slides in and stays locked until the turn ends, and [offsetPx] is the only value that moves.
 */
internal data class ReflowFlipState(
    val offsetPx: Float = 0f,
    val direction: ReflowFlipDirection? = null,
    val isAnimating: Boolean = false,
    val isDragging: Boolean = false,
    val incomingSlice: ReaderPageSlice? = null,
) {
    /** A committed turn owns the screen until it finishes; new input must not interrupt it. */
    val isBusy: Boolean get() = isAnimating
}

internal fun ReflowFlipState.startDrag(): ReflowFlipState =
    if (isBusy) this else copy(isDragging = true)

internal fun ReflowFlipState.dragTo(offsetPx: Float): ReflowFlipState =
    if (isDragging) copy(offsetPx = offsetPx) else this

internal fun ReflowFlipState.endDrag(): ReflowFlipState = copy(isDragging = false)

/** Locks the pre-rendered target and blocks new input until the turn settles. */
internal fun ReflowFlipState.beginTurn(
    direction: ReflowFlipDirection,
    target: ReaderPageSlice?,
): ReflowFlipState =
    if (isBusy) {
        this
    } else {
        // Keeps the released drag offset so the animation continues from where the finger let go.
        copy(
            direction = direction,
            incomingSlice = target,
            isAnimating = true,
            isDragging = false,
        )
    }

/** Atomic swap: the incoming page becomes the shown page and the offset returns to rest at once. */
internal fun ReflowFlipState.finishTurn(): ReflowFlipState = ReflowFlipState()

/** Abandoned turn: the page stays where it was and input is released. */
internal fun ReflowFlipState.cancelTurn(): ReflowFlipState = ReflowFlipState()

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
    const val TapTurnAnimationMillis = 150

    /** Spring-back for a rejected or unavailable turn. */
    const val CancelAnimationMillis = 160

    /**
     * How long a committed turn waits for the host to publish the new page before reverting.
     *
     * The pre-rendered page is already on screen at this point, so waiting through a chapter-boundary
     * load keeps the transition seamless; reverting instead of waiting would jump back and forth.
     */
    const val PageChangeTimeoutMillis = 1_000L

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
 * Hosts the visible reflow page plus both neighbours, follows the finger horizontally, and turns the
 * page through [onPrevious]/[onNext] once a drag or tap commits.
 *
 * Both neighbours stay composed offscreen, so the page a turn needs is already laid out before its
 * animation starts and the swap at the end is a pure state change.
 */
@Composable
internal fun AndroidReflowPager(
    pages: ReflowPagerPages,
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

    val scope = rememberCoroutineScope()
    var flipState by remember { mutableStateOf(ReflowFlipState()) }
    var animationJob by remember { mutableStateOf<Job?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val pageWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val minimumTurnDistancePx = with(density) { ReflowPagerConfig.MinimumTurnDistance.toPx() }
        val visibleOffsetProvider = { flipState.offsetPx }

        suspend fun animateOffsetTo(target: Float, durationMillis: Int) {
            animate(
                initialValue = flipState.offsetPx,
                targetValue = target,
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = FastOutSlowInEasing,
                ),
            ) { value, _ ->
                flipState = flipState.copy(offsetPx = value)
            }
        }

        fun requestTurn(direction: ReflowFlipDirection) {
            when (direction) {
                ReflowFlipDirection.Forward -> latestOnNext()
                ReflowFlipDirection.Backward -> latestOnPrevious()
            }
        }

        /**
         * A turn may only start while the host still shows the page this pager draws.
         *
         * [ReflowFlipState.isBusy] covers the whole turn, including the wait for the host to publish
         * the new page, so every gesture that arrives while the displayed page is ahead of the host
         * index is rejected instead of computing a target from that stale index.
         */
        fun canStartTurn(gesture: String): Boolean {
            if (!flipState.isBusy) return true
            ToreadClientLog.info(
                FlipLogArea,
                "reader.flip_blocked",
                "gesture" to gesture,
                "direction" to flipState.direction?.logName(),
                "isAnimating" to flipState.isAnimating,
                "isDragging" to flipState.isDragging,
            )
            return false
        }

        /** Waits (bounded) for the host to publish a page other than the one a turn started from. */
        suspend fun awaitHostPageChange(from: ReaderPageSlice?): Boolean =
            withTimeoutOrNull(ReflowPagerConfig.PageChangeTimeoutMillis) {
                snapshotFlow { latestPages.current }.first { it !== from }
            } != null

        /** Spring-back for a rejected drag. A running turn owns the offset and settles itself. */
        fun settleBack() {
            if (flipState.isBusy) return
            animationJob?.cancel()
            animationJob = scope.launch {
                animateOffsetTo(0f, ReflowPagerConfig.CancelAnimationMillis)
            }
        }

        /**
         * Runs a turn and guarantees the busy flag is released.
         *
         * A cancelled turn (a competing gesture, composition disposal) must never leave input
         * blocked, so the flag is cleared in `finally` whenever the body did not clear it already.
         */
        fun launchTurn(block: suspend CoroutineScope.() -> Unit) {
            animationJob?.cancel()
            animationJob = scope.launch {
                try {
                    block()
                } finally {
                    if (flipState.isAnimating) {
                        flipState = flipState.cancelTurn()
                    }
                }
            }
        }

        fun runTurn(direction: ReflowFlipDirection, commitMillis: Int) {
            if (!canStartTurn(gesture = "turn")) return
            val target = when (direction) {
                ReflowFlipDirection.Forward -> latestPages.next
                ReflowFlipDirection.Backward -> latestPages.previous
            }
            val hostSliceAtStart = latestPages.current
            ToreadClientLog.info(
                FlipLogArea,
                "reader.flip_start",
                "direction" to direction.logName(),
                "hasPreview" to (target != null),
                "commitMillis" to commitMillis,
            )
            flipState = flipState.beginTurn(direction = direction, target = target)
            launchTurn {
                if (target == null) {
                    // Sliding without a preview would expose an empty half: the host turns the page
                    // right away and the current page settles back. Input stays blocked until the host
                    // publishes, otherwise a second gesture would compute its target from the index
                    // the host has not advanced past yet.
                    requestTurn(direction)
                    animateOffsetTo(0f, ReflowPagerConfig.CancelAnimationMillis)
                    val published = awaitHostPageChange(hostSliceAtStart)
                    ToreadClientLog.info(
                        FlipLogArea,
                        "reader.flip_settled",
                        "direction" to direction.logName(),
                        "outcome" to if (published) "host_published" else "host_timeout",
                        "slid" to false,
                    )
                    flipState = flipState.cancelTurn()
                    return@launchTurn
                }
                val targetOffset = when (direction) {
                    ReflowFlipDirection.Forward -> -pageWidthPx
                    ReflowFlipDirection.Backward -> pageWidthPx
                }
                animateOffsetTo(targetOffset, commitMillis)
                requestTurn(direction)
                val published = awaitHostPageChange(hostSliceAtStart)
                ToreadClientLog.info(
                    FlipLogArea,
                    "reader.flip_settled",
                    "direction" to direction.logName(),
                    "outcome" to if (published) "swapped" else "reverted",
                    "slid" to true,
                )
                if (published) {
                    // The host already shows the pre-rendered page, so zeroing the offset here
                    // draws exactly the same pixels as the last animation frame.
                    flipState = flipState.finishTurn()
                } else {
                    animateOffsetTo(0f, ReflowPagerConfig.CancelAnimationMillis)
                    flipState = flipState.cancelTurn()
                }
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
                            var blocked = false
                            var totalX = 0f
                            var totalY = 0f
                            var startOffsetPx = 0f
                            var tracking = ReflowDragTracking()
                            val velocityTracker = VelocityTracker()
                            detectDragGestures(
                                onDragStart = {
                                    // Re-checked here as well as on release, so a gesture that starts
                                    // during a turn never moves the page either.
                                    blocked = !canStartTurn(gesture = "drag")
                                    if (!blocked) {
                                        animationJob?.cancel()
                                        totalX = 0f
                                        totalY = 0f
                                        startOffsetPx = flipState.offsetPx
                                        tracking = ReflowDragTracking()
                                        velocityTracker.resetTracking()
                                        flipState = flipState.startDrag()
                                    }
                                },
                                onDragCancel = {
                                    velocityTracker.resetTracking()
                                    if (!blocked) {
                                        flipState = flipState.endDrag()
                                        settleBack()
                                    }
                                },
                                onDragEnd = {
                                    if (blocked) return@detectDragGestures
                                    val velocity = velocityTracker.calculateVelocity().x
                                    velocityTracker.resetTracking()
                                    flipState = flipState.endDrag()
                                    val dragX = tracking.effectiveDragXPx(totalX)
                                    val action = reflowSwipeAction(
                                        dragXPx = dragX,
                                        // Tracking already decided the gesture is horizontal, so the
                                        // vertical component must not veto it here.
                                        dragYPx = 0f,
                                        pageWidthPx = pageWidthPx,
                                        velocityPxPerSecond = velocity,
                                        minimumTurnDistancePx = minimumTurnDistancePx,
                                        canPrevious = latestCanPrevious,
                                        canNext = latestCanNext,
                                    )
                                    ToreadClientLog.info(
                                        FlipLogArea,
                                        "reader.flip_gesture",
                                        "action" to action.logName(),
                                        "dragX" to dragX.roundToInt(),
                                        "dragY" to totalY.roundToInt(),
                                        "velocity" to velocity.roundToInt(),
                                        "tracked" to tracking.isTracking,
                                        "canNext" to latestCanNext,
                                        "canPrevious" to latestCanPrevious,
                                        "hasNextPreview" to (latestPages.next != null),
                                        "hasPreviousPreview" to (latestPages.previous != null),
                                    )
                                    when (action) {
                                        ReflowSwipeAction.Next ->
                                            runTurn(
                                                direction = ReflowFlipDirection.Forward,
                                                commitMillis = ReflowPagerConfig.CommitAnimationMillis,
                                            )

                                        ReflowSwipeAction.Previous ->
                                            runTurn(
                                                direction = ReflowFlipDirection.Backward,
                                                commitMillis = ReflowPagerConfig.CommitAnimationMillis,
                                            )

                                        ReflowSwipeAction.Cancel -> settleBack()
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (blocked) return@detectDragGestures
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    totalX += dragAmount.x
                                    totalY += dragAmount.y
                                    // Follow the finger once the movement is horizontally dominant,
                                    // and re-evaluate on every event instead of latching an axis on
                                    // the first one: a gesture that opens with a little vertical drift
                                    // must still drag the page.
                                    tracking = tracking.update(totalX = totalX, totalY = totalY)
                                    if (tracking.isTracking) {
                                        flipState = flipState.dragTo(
                                            offsetPx = reflowDragOffsetPx(
                                                startOffsetPx = startOffsetPx,
                                                dragXPx = tracking.effectiveDragXPx(totalX),
                                                pageWidthPx = pageWidthPx,
                                                bounds = ReflowDragBounds(
                                                    canTurnNext = latestCanNext,
                                                    canTurnPrevious = latestCanPrevious,
                                                    hasNextPreview = latestPages.next != null,
                                                    hasPreviousPreview = latestPages.previous != null,
                                                ),
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
                                when (reflowTapAction(fraction)) {
                                    ReflowTapAction.Previous -> {
                                        if (canStartTurn(gesture = "tap")) {
                                            runTurn(
                                                direction = ReflowFlipDirection.Backward,
                                                commitMillis = ReflowPagerConfig.TapTurnAnimationMillis,
                                            )
                                        }
                                    }

                                    ReflowTapAction.Next -> {
                                        if (canStartTurn(gesture = "tap")) {
                                            runTurn(
                                                direction = ReflowFlipDirection.Forward,
                                                commitMillis = ReflowPagerConfig.TapTurnAnimationMillis,
                                            )
                                        }
                                    }

                                    ReflowTapAction.ToggleControls -> latestOnTapCenter()
                                }
                            }
                        }
                    },
                ),
        ) {
            val shownSlice = pages.current
            // The locked incoming page wins for the whole turn, so a window that re-centers itself
            // while the animation runs cannot change what slides in.
            val forwardSlice = flipState.incomingSlice
                ?.takeIf { flipState.direction == ReflowFlipDirection.Forward }
                ?: pages.next
            val backwardSlice = flipState.incomingSlice
                ?.takeIf { flipState.direction == ReflowFlipDirection.Backward }
                ?: pages.previous
            if (backwardSlice != null) {
                ReflowPagerSlot(
                    offsetProvider = { visibleOffsetProvider() - pageWidthPx },
                ) {
                    pageContent(backwardSlice)
                }
            }
            if (forwardSlice != null) {
                ReflowPagerSlot(
                    offsetProvider = { visibleOffsetProvider() + pageWidthPx },
                ) {
                    pageContent(forwardSlice)
                }
            }
            if (shownSlice != null) {
                ReflowPagerSlot(offsetProvider = visibleOffsetProvider) {
                    pageContent(shownSlice)
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

private fun ReflowFlipDirection.logName(): String =
    when (this) {
        ReflowFlipDirection.Forward -> "forward"
        ReflowFlipDirection.Backward -> "backward"
    }

private fun ReflowSwipeAction.logName(): String =
    when (this) {
        ReflowSwipeAction.Next -> "next"
        ReflowSwipeAction.Previous -> "previous"
        ReflowSwipeAction.Cancel -> "cancel"
    }

private const val FlipLogArea = "reader"
