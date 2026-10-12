package com.example.toread.auth

import com.example.toread.authRefreshRetryDelayMillis
import kotlin.random.Random

/**
 * How often a *trigger*, as opposed to a deadline, is allowed to spend a real refresh rotation.
 *
 * The app refreshes on several triggers: the access token's expiry schedule, the retry schedule, an
 * online transition of the connection monitor, returning to the foreground, and the first successful
 * health check after startup. On a weak network the monitor flips offline/online every few seconds, and
 * every one of those flips used to force a rotation — each of which the same weak link can lose mid
 * flight. A floor keeps a flapping link from spending the chain's replay budget on triggers that the
 * still-valid access token did not need.
 *
 * This never delays a refresh that is actually required: a session without an access token, or one
 * whose token is inside the expiry safety window, refreshes immediately.
 */
internal const val AuthRefreshTriggerMinIntervalMillis = 60_000L

/**
 * Floor between two refresh attempts whose outcome was ambiguous.
 *
 * A timeout, a dropped connection, or a lost response all mean "the server may already have rotated".
 * Retrying those too quickly is what burned two rotations inside a minute on a lossy link, and two
 * consecutive lost responses were enough to end the session before the server learned to walk its own
 * replay lineage.
 */
internal const val AuthRefreshAmbiguousRetryFloorMillis = 45_000L

/** Refresh triggers that only exist because the environment changed, never because a token expired. */
internal enum class AuthRefreshTrigger {
    /** The access token is at (or past) its renewal point; always allowed through. */
    Deadline,

    /** A reconnect, a return to the foreground, or the first healthy check of this launch. */
    Environment,

    /** Nothing usable is in memory yet: the app has no access token to work with. */
    SessionMissing,
}

/**
 * Whether a trigger may spend a rotation right now.
 *
 * @param trigger what asked for the refresh.
 * @param millisSinceLastSuccessfulRefresh how long ago the last successful rotation was, or null when
 *   this process has not refreshed yet.
 * @param millisSinceLastAttempt how long ago the last refresh attempt finished, or null when none has.
 * @param previousAttemptAmbiguous whether that last attempt may already have rotated server-side.
 * @param accessTokenWithinSafetyWindow whether the in-memory access token is close enough to expiry
 *   that a rotation is worth spending now.
 */
internal fun maySpendRefreshRotation(
    trigger: AuthRefreshTrigger,
    millisSinceLastSuccessfulRefresh: Long?,
    millisSinceLastAttempt: Long?,
    previousAttemptAmbiguous: Boolean,
    accessTokenWithinSafetyWindow: Boolean,
): Boolean = when (trigger) {
    AuthRefreshTrigger.SessionMissing -> true
    AuthRefreshTrigger.Deadline -> true
    AuthRefreshTrigger.Environment -> when {
        accessTokenWithinSafetyWindow -> true
        // Never rotated in this process yet (a fresh launch, or the session was just restored).
        millisSinceLastSuccessfulRefresh == null -> true
        isWithin(millisSinceLastAttempt, AuthRefreshAmbiguousRetryFloorMillis) &&
            previousAttemptAmbiguous -> false
        else -> !isWithin(millisSinceLastSuccessfulRefresh, AuthRefreshTriggerMinIntervalMillis)
    }
}

/** Delay before retrying an ambiguous refresh, never shorter than the ambiguous floor. */
internal fun ambiguousRetryDelayMillis(retryAttempt: Int): Long =
    maxOf(authRefreshRetryDelayMillis(retryAttempt), AuthRefreshAmbiguousRetryFloorMillis)

/**
 * How much later than its safety window one process may renew its access token.
 *
 * The safety window is derived from the server's `expires_in`, so devices that received their tokens at
 * the same moment renew at the same moment too. That synchronisation costs little for one reader with two
 * devices, and it costs a burst of rotations on a shared or throttled link; a small per-process draw
 * spreads them out. It only ever *delays* a renewal, and the window it delays into is the safety margin
 * the schedule already keeps, so a token is still renewed well before it expires.
 */
internal const val AuthRefreshDeadlineJitterMillis = 120_000L

/** This process's renewal draw, taken once so every schedule of one launch is spread the same way. */
internal val authRefreshDeadlineJitterMillis: Long by lazy { drawAuthRefreshDeadlineJitter() }

/** Draws a renewal jitter in `0..AuthRefreshDeadlineJitterMillis`, for tests and for [authRefreshDeadlineJitterMillis]. */
internal fun drawAuthRefreshDeadlineJitter(random: Random = Random.Default): Long =
    random.nextLong(AuthRefreshDeadlineJitterMillis + 1L)

/** The renewal delay for one schedule: the caller's window plus this process's jitter, bounded. */
internal fun withAuthRefreshDeadlineJitter(
    delayMillis: Long,
    jitterMillis: Long = authRefreshDeadlineJitterMillis,
): Long =
    delayMillis + jitterMillis.coerceIn(0L, AuthRefreshDeadlineJitterMillis)

private fun isWithin(elapsedMillis: Long?, windowMillis: Long): Boolean =
    elapsedMillis != null && elapsedMillis < windowMillis
