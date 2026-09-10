package com.example.toread.sync

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressSyncSchedulerTest {
    @Test
    fun requestFlushStartsWithoutDebounce() = withSchedulerScope { scope ->
        val started = CompletableDeferred<Unit>()
        val scheduler = ProgressSyncScheduler(
            scope = scope,
            flushPending = {
                started.complete(Unit)
                EmptySummary
            },
        )

        scheduler.requestFlush()

        withTimeout(TestTimeoutMillis) { started.await() }
    }

    @Test
    fun requestsDuringFlushAreCoalescedIntoOneFollowUpPass() = withSchedulerScope { scope ->
        val calls = AtomicInteger(0)
        val activeCalls = AtomicInteger(0)
        val maxConcurrentCalls = AtomicInteger(0)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val scheduler = ProgressSyncScheduler(
            scope = scope,
            flushPending = {
                val active = activeCalls.incrementAndGet()
                maxConcurrentCalls.updateAndGet { previous -> maxOf(previous, active) }
                try {
                    when (calls.incrementAndGet()) {
                        1 -> {
                            firstStarted.complete(Unit)
                            releaseFirst.await()
                        }
                        2 -> secondStarted.complete(Unit)
                    }
                    EmptySummary
                } finally {
                    activeCalls.decrementAndGet()
                }
            },
        )

        scheduler.requestFlush()
        withTimeout(TestTimeoutMillis) { firstStarted.await() }
        repeat(20) { scheduler.requestFlush() }
        assertEquals(1, calls.get())

        releaseFirst.complete(Unit)
        withTimeout(TestTimeoutMillis) { secondStarted.await() }

        assertEquals(2, calls.get())
        assertEquals(1, maxConcurrentCalls.get())
    }

    @Test
    fun flushNowDuringActiveFlushWaitsForFollowUpPass() = withSchedulerScope { scope ->
        val calls = AtomicInteger(0)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val scheduler = ProgressSyncScheduler(
            scope = scope,
            flushPending = {
                when (calls.incrementAndGet()) {
                    1 -> {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                    2 -> {
                        secondStarted.complete(Unit)
                        releaseSecond.await()
                    }
                }
                EmptySummary
            },
        )

        scheduler.requestFlush()
        withTimeout(TestTimeoutMillis) { firstStarted.await() }
        val forcedFlush = scheduler.flushNow()
        assertFalse(forcedFlush.isCompleted)

        releaseFirst.complete(Unit)
        withTimeout(TestTimeoutMillis) { secondStarted.await() }
        assertFalse(forcedFlush.isCompleted)

        releaseSecond.complete(Unit)
        withTimeout(TestTimeoutMillis) { forcedFlush.join() }
        assertTrue(forcedFlush.isCompleted)
        assertEquals(2, calls.get())
    }

    @Test
    fun unavailableAutomaticSyncCompletesAttemptWithoutBusyLoop() = withSchedulerScope { scope ->
        val calls = AtomicInteger(0)
        val scheduler = ProgressSyncScheduler(
            scope = scope,
            flushPending = {
                calls.incrementAndGet()
                null
            },
        )

        withTimeout(TestTimeoutMillis) { scheduler.flushNow().join() }
        assertEquals(1, calls.get())
        withTimeout(TestTimeoutMillis) { scheduler.flushNow().join() }

        assertEquals(2, calls.get())
    }

    @Test
    fun localFlushCancellationDoesNotStrandWaiterOrFutureRequests() = withSchedulerScope { scope ->
        val calls = AtomicInteger(0)
        val scheduler = ProgressSyncScheduler(
            scope = scope,
            flushPending = {
                if (calls.incrementAndGet() == 1) throw CancellationException("local timeout")
                EmptySummary
            },
        )

        withTimeout(TestTimeoutMillis) { scheduler.flushNow().join() }
        withTimeout(TestTimeoutMillis) { scheduler.flushNow().join() }

        assertEquals(2, calls.get())
    }

    private fun withSchedulerScope(block: suspend (CoroutineScope) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            withTimeout(TestTimeoutMillis) { block(scope) }
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val TestTimeoutMillis = 5_000L
        val EmptySummary = SyncRunSummary(
            scanned = 0,
            uploaded = 0,
            failed = 0,
            skipped = 0,
            lastError = null,
        )
    }
}
