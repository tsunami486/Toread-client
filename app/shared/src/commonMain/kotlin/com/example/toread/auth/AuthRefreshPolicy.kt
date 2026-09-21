package com.example.toread.auth

import com.example.toread.authRefreshRetryDelayMillis

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

private fun isWithin(elapsedMillis: Long?, windowMillis: Long): Boolean =
    elapsedMillis != null && elapsedMillis < windowMillis
