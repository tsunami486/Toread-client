package com.example.toread.network

import com.example.toread.auth.ServerConnectionResult
import com.example.toread.progress.ProgressClock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ServerConnectionMonitorTest {
    @Test
    fun oneFailedCheckIsConfirmedWithoutPublishingOffline() = withMonitorScope { scope ->
        val calls = AtomicInteger()
        val observedStates = Channel<ServerConnectionState>(capacity = Channel.UNLIMITED)
        val monitor = createMonitor(
            scope = scope,
            healthCheck = {
                when (calls.incrementAndGet()) {
                    1 -> ServerConnectionResult.Success
                    2 -> ServerConnectionResult.Failure("transient")
                    else -> ServerConnectionResult.Success
                }
            },
        )
        val collector = scope.launch {
            monitor.state.collect { observedStates.send(it) }
        }

        try {
            monitor.start()
            val firstOnline = observedStates.awaitOnline()

            monitor.triggerCheck("test")

            withTimeout(TestTimeoutMillis) {
                while (true) {
                    val state = observedStates.receive()
                    assertFalse(state is ServerConnectionState.Offline)
                    if (state is ServerConnectionState.Online && state.checkedAt != firstOnline.checkedAt) {
                        break
                    }
                }
            }
            assertTrue(calls.get() >= 3)
        } finally {
            monitor.stop()
            collector.cancel()
        }
    }

    @Test
    fun twoConsecutiveFailedChecksPublishOffline() = withMonitorScope { scope ->
        val calls = AtomicInteger()
        val observedStates = Channel<ServerConnectionState>(capacity = Channel.UNLIMITED)
        val monitor = createMonitor(
            scope = scope,
            healthCheck = {
                if (calls.incrementAndGet() == 1) {
                    ServerConnectionResult.Success
                } else {
                    ServerConnectionResult.Failure("offline")
                }
            },
        )
        val collector = scope.launch {
            monitor.state.collect { observedStates.send(it) }
        }

        try {
            monitor.start()
            observedStates.awaitOnline()

            monitor.triggerCheck("test")

            val offline = withTimeout(TestTimeoutMillis) {
                while (true) {
                    val state = observedStates.receive()
                    if (state is ServerConnectionState.Offline) {
                        return@withTimeout state
                    }
                }
                error("unreachable")
            }
            assertIs<ServerConnectionState.Offline>(offline)
            assertTrue(calls.get() >= 3)
        } finally {
            monitor.stop()
            collector.cancel()
        }
    }

    @Test
    fun firstFailureUsesDedicatedConfirmationDelayBeforePublishingOffline() = withMonitorScope { scope ->
        val calls = AtomicInteger()
        val callTimes = Channel<Long>(capacity = Channel.UNLIMITED)
        val monitor = createMonitor(
            scope = scope,
            healthCheck = {
                calls.incrementAndGet()
                callTimes.send(System.nanoTime())
                ServerConnectionResult.Failure("offline")
            },
            confirmationDelayMillis = 37L,
        )

        try {
            monitor.start()
            val firstCall = withTimeout(TestTimeoutMillis) { callTimes.receive() }
            val secondCall = withTimeout(TestTimeoutMillis) { callTimes.receive() }
            val elapsedMillis = (secondCall - firstCall) / 1_000_000L
            assertTrue(elapsedMillis >= 25L, "confirmation ran after only ${elapsedMillis}ms")
            assertIs<ServerConnectionState.Offline>(monitor.state.value)
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun explicitCheckInterruptsConfirmationDelayWithoutAddingAnExtraCheck() = withMonitorScope { scope ->
        val calls = AtomicInteger()
        val monitor = createMonitor(
            scope = scope,
            healthCheck = {
                calls.incrementAndGet()
                ServerConnectionResult.Failure("offline")
            },
            confirmationDelayMillis = 60_000L,
            retryDelaysMillis = listOf(60_000L),
        )

        try {
            monitor.start()
            withTimeout(TestTimeoutMillis) {
                while (calls.get() < 1) {
                    kotlinx.coroutines.yield()
                }
            }

            monitor.triggerCheck("sync_failed")

            withTimeout(TestTimeoutMillis) {
                while (calls.get() < 2) {
                    kotlinx.coroutines.yield()
                }
            }
            kotlinx.coroutines.delay(50L)
            assertEquals(2, calls.get())
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun offlineBackoffNeverExceedsConfiguredPollCeiling() = withMonitorScope { scope ->
        val calls = AtomicInteger()
        val lastCallNanos = AtomicLong()
        val monitor = createMonitor(
            scope = scope,
            healthCheck = {
                lastCallNanos.set(System.nanoTime())
                calls.incrementAndGet()
                ServerConnectionResult.Failure("offline")
            },
            retryDelaysMillis = listOf(5L, 20L, 60_000L),
            offlinePollCeilingMillis = 20L,
        )

        try {
            monitor.start()
            withTimeout(TestTimeoutMillis) {
                while (calls.get() < 5) {
                    kotlinx.coroutines.yield()
                }
            }
            assertTrue(lastCallNanos.get() > 0L)
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun explicitCheckInterruptsLongOfflineBackoff() = withMonitorScope { scope ->
        val calls = AtomicInteger()
        val monitor = createMonitor(
            scope = scope,
            healthCheck = {
                calls.incrementAndGet()
                ServerConnectionResult.Failure("offline")
            },
            retryDelaysMillis = listOf(60_000L),
            offlinePollCeilingMillis = 60_000L,
        )

        try {
            monitor.start()
            withTimeout(TestTimeoutMillis) {
                while (calls.get() < 2) {
                    kotlinx.coroutines.yield()
                }
            }

            monitor.triggerCheck("sync_failed")

            withTimeout(TestTimeoutMillis) {
                while (calls.get() < 3) {
                    kotlinx.coroutines.yield()
                }
            }
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun unexpectedHealthCheckExceptionDoesNotStopMonitoring() = withMonitorScope { scope ->
        val calls = AtomicInteger()
        val monitor = createMonitor(
            scope = scope,
            healthCheck = {
                if (calls.incrementAndGet() == 1) {
                    error("unexpected")
                }
                ServerConnectionResult.Success
            },
            retryDelaysMillis = listOf(10L),
        )

        try {
            monitor.start()
            withTimeout(TestTimeoutMillis) {
                while (monitor.state.value !is ServerConnectionState.Online) {
                    kotlinx.coroutines.yield()
                }
            }
            assertTrue(calls.get() >= 2)
        } finally {
            monitor.stop()
        }
    }

    private fun createMonitor(
        scope: CoroutineScope,
        healthCheck: suspend () -> ServerConnectionResult,
        retryDelaysMillis: List<Long> = listOf(10L, 60_000L),
        offlinePollCeilingMillis: Long = 60_000L,
        confirmationDelayMillis: Long = 0L,
    ): ServerConnectionMonitor =
        ServerConnectionMonitor(
            scope = scope,
            healthCheck = healthCheck,
            clock = IncrementingClock(),
            onlinePollMillis = 60_000L,
            retryDelaysMillis = retryDelaysMillis,
            offlinePollCeilingMillis = offlinePollCeilingMillis,
            confirmationDelayMillis = confirmationDelayMillis,
        )

    private suspend fun Channel<ServerConnectionState>.awaitOnline(): ServerConnectionState.Online =
        withTimeout(TestTimeoutMillis) {
            while (true) {
                val state = receive()
                if (state is ServerConnectionState.Online) {
                    return@withTimeout state
                }
            }
            error("unreachable")
        }

    private fun withMonitorScope(block: suspend (CoroutineScope) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            kotlinx.coroutines.runBlocking {
                block(scope)
            }
        } finally {
            scope.cancel()
        }
    }

    private class IncrementingClock : ProgressClock {
        private val value = AtomicInteger()

        override fun nowIsoString(): String = value.incrementAndGet().toString()
    }

    private companion object {
        const val TestTimeoutMillis = 2_000L
    }
}
