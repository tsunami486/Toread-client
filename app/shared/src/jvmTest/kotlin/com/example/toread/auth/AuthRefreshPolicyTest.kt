package com.example.toread.auth

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRefreshPolicyTest {
    @Test
    fun deadlineJitterOnlyEverDelaysARenewalAndStaysWithinItsCap() {
        // Zero jitter keeps the caller's own window exactly.
        assertEquals(
            RefreshWindowMillis,
            withAuthRefreshDeadlineJitter(RefreshWindowMillis, jitterMillis = 0L),
        )
        // A negative draw is clamped to zero instead of shortening the window, which would renew early.
        assertEquals(
            RefreshWindowMillis,
            withAuthRefreshDeadlineJitter(RefreshWindowMillis, jitterMillis = -1L),
        )
        assertEquals(
            RefreshWindowMillis,
            withAuthRefreshDeadlineJitter(RefreshWindowMillis, jitterMillis = -JitterMillis),
        )
        // Anything above the cap is clamped, so one draw can never delay a renewal out of its window.
        assertEquals(
            RefreshWindowMillis + AuthRefreshDeadlineJitterMillis,
            withAuthRefreshDeadlineJitter(
                RefreshWindowMillis,
                jitterMillis = AuthRefreshDeadlineJitterMillis,
            ),
        )
        assertEquals(
            RefreshWindowMillis + AuthRefreshDeadlineJitterMillis,
            withAuthRefreshDeadlineJitter(
                RefreshWindowMillis,
                jitterMillis = AuthRefreshDeadlineJitterMillis + 1L,
            ),
        )

        // The default argument is this process's own draw, which is itself inside the cap.
        val scheduled = withAuthRefreshDeadlineJitter(RefreshWindowMillis)
        assertTrue(scheduled >= RefreshWindowMillis)
        assertTrue(scheduled <= RefreshWindowMillis + AuthRefreshDeadlineJitterMillis)
        assertTrue(authRefreshDeadlineJitterMillis in 0L..AuthRefreshDeadlineJitterMillis)
    }

    @Test
    fun theDeadlineJitterDrawIsDeterministicForASeedAndStaysWithinTheCap() {
        val seed = 20260723
        // The same seed always produces the same delay, which is what makes a schedule reproducible.
        assertEquals(
            drawAuthRefreshDeadlineJitter(Random(seed)),
            drawAuthRefreshDeadlineJitter(Random(seed)),
        )

        val draws = List(JitterDraws) { offset -> drawAuthRefreshDeadlineJitter(Random(seed + offset)) }
        draws.forEach { draw ->
            assertTrue(draw >= 0L, "a draw must never be negative")
            assertTrue(draw <= AuthRefreshDeadlineJitterMillis, "a draw must stay inside the cap")
        }
        // Different seeds do spread out, so the draw is a real delay rather than a constant.
        assertTrue(draws.distinct().size > 1)
    }

    @Test
    fun tokenHintAcceptsOnlyTheThreeKnownWireValues() {
        assertEquals(AuthTokenHint.Superseded, AuthTokenHint.fromWire("superseded"))
        assertEquals(AuthTokenHint.ReplayExhausted, AuthTokenHint.fromWire("replay_exhausted"))
        assertEquals(AuthTokenHint.NoLongerValid, AuthTokenHint.fromWire("no_longer_valid"))

        // Case and surrounding whitespace are tolerated, because the field is server-supplied text.
        assertEquals(AuthTokenHint.Superseded, AuthTokenHint.fromWire("  SUPERSEDED  "))
        assertEquals(AuthTokenHint.ReplayExhausted, AuthTokenHint.fromWire("Replay_Exhausted"))
        assertEquals(AuthTokenHint.NoLongerValid, AuthTokenHint.fromWire("\tno_longer_valid\n"))

        // Anything else is dropped instead of stored, so a server cannot write arbitrary text here.
        listOf(
            null,
            "",
            "   ",
            "expired",
            "unknown",
            "rejected",
            "replay-exhausted",
            "superseded superseded",
        ).forEach { value ->
            assertNull(AuthTokenHint.fromWire(value), "value '$value' must not be accepted")
        }

        assertEquals(
            setOf("superseded", "replay_exhausted", "no_longer_valid"),
            AuthTokenHint.entries.map { hint -> hint.wireValue }.toSet(),
        )
    }

    private companion object {
        const val RefreshWindowMillis = 15 * 60 * 1000L
        const val JitterMillis = 5 * 1000L
        const val JitterDraws = 64
    }
}
