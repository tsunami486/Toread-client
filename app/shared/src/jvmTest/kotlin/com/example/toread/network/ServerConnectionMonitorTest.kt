package com.example.toread.network

import com.example.toread.auth.ServerConnectionResult
import com.example.toread.progress.ProgressClock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
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

    private fun createMonitor(
        scope: CoroutineScope,
        healthCheck: suspend () -> ServerConnectionResult,
    ): ServerConnectionMonitor =
        ServerConnectionMonitor(
            scope = scope,
            healthCheck = healthCheck,
            clock = IncrementingClock(),
            onlinePollMillis = 60_000L,
            retryDelaysMillis = listOf(10L, 60_000L),
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
