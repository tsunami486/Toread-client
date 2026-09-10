package com.example.toread.network

import com.example.toread.auth.ServerConnectionResult
import com.example.toread.logging.ToreadClientLog
import com.example.toread.progress.PlatformProgressClock
import com.example.toread.progress.ProgressClock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class ServerConnectionMonitor(
    private val scope: CoroutineScope,
    private val healthCheck: suspend () -> ServerConnectionResult,
    private val clock: ProgressClock = PlatformProgressClock(),
    private val onlinePollMillis: Long = DefaultOnlinePollMillis,
    private val retryDelaysMillis: List<Long> = DefaultRetryDelaysMillis,
    private val offlinePollCeilingMillis: Long = DefaultOfflinePollCeilingMillis,
    private val failuresBeforeOffline: Int = DefaultFailuresBeforeOffline,
    private val confirmationDelayMillis: Long = DefaultConfirmationDelayMillis,
) {
    init {
        require(onlinePollMillis > 0) {
            "onlinePollMillis must be positive"
        }
        require(offlinePollCeilingMillis > 0) {
            "offlinePollCeilingMillis must be positive"
        }
        require(failuresBeforeOffline > 0) {
            "failuresBeforeOffline must be positive"
        }
        require(confirmationDelayMillis >= 0) {
            "confirmationDelayMillis must not be negative"
        }
    }

    private val _state = MutableStateFlow<ServerConnectionState>(
        ServerConnectionState.Checking(startedAt = clock.nowIsoString()),
    )
    val state: StateFlow<ServerConnectionState> = _state.asStateFlow()

    private val checkMutex = Mutex()
    private val checkRequests = Channel<String>(capacity = Channel.CONFLATED)
    private var monitorJob: Job? = null
    private var retryIndex = 0
    private var consecutiveFailures = 0

    fun start() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            var trigger = "poll"
            while (isActive) {
                val outcome = try {
                    checkNow(trigger = trigger)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exception: Exception) {
                    recordUnexpectedFailure(trigger, exception)
                }
                if (
                    !outcome.succeeded &&
                    outcome.consecutiveFailures < failuresBeforeOffline
                ) {
                    trigger = awaitNextTrigger(confirmationDelayMillis)
                    continue
                }
                trigger = awaitNextTrigger(nextWaitMillis(outcome))
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun triggerCheck(reason: String) {
        checkRequests.trySend(reason)
    }

    fun markOnline(source: String) {
        scope.launch {
            checkMutex.withLock {
                consecutiveFailures = 0
                retryIndex = 0
                updateState(
                    ServerConnectionState.Online(checkedAt = clock.nowIsoString()),
                    trigger = source,
                )
            }
        }
    }

    fun markOffline(reason: String, source: String) {
        scope.launch {
            checkMutex.withLock {
                consecutiveFailures = failuresBeforeOffline
                updateState(
                    ServerConnectionState.Offline(
                        reason = reason,
                        checkedAt = clock.nowIsoString(),
                        retrying = monitorJob?.isActive == true,
                    ),
                    trigger = source,
                )
            }
            triggerCheck(source)
        }
    }

    private suspend fun checkNow(trigger: String): ConnectionCheckOutcome =
        checkMutex.withLock {
            if (_state.value is ServerConnectionState.Checking) {
                updateState(
                    ServerConnectionState.Checking(startedAt = clock.nowIsoString()),
                    trigger = trigger,
                )
            }
            val checkedAt = clock.nowIsoString()
            val result = healthCheck()
            val nextState = when (result) {
                ServerConnectionResult.Success -> {
                    consecutiveFailures = 0
                    retryIndex = 0
                    ServerConnectionState.Online(checkedAt = checkedAt)
                }
                is ServerConnectionResult.Failure -> {
                    consecutiveFailures++
                    if (consecutiveFailures >= failuresBeforeOffline) {
                        ServerConnectionState.Offline(
                            reason = result.message,
                            checkedAt = checkedAt,
                            retrying = true,
                        )
                    } else {
                        _state.value
                    }
                }
            }
            updateState(nextState, trigger = trigger)
            ConnectionCheckOutcome(
                succeeded = result == ServerConnectionResult.Success,
                consecutiveFailures = consecutiveFailures,
            )
        }

    private suspend fun awaitNextTrigger(waitMillis: Long): String =
        withTimeoutOrNull(waitMillis) {
            checkRequests.receive()
        } ?: "poll"

    private fun nextWaitMillis(outcome: ConnectionCheckOutcome): Long =
        when {
            outcome.succeeded -> onlinePollMillis
            else -> nextRetryDelayMillis()
        }

    private fun nextRetryDelayMillis(): Long {
        val delays = retryDelaysMillis.ifEmpty { listOf(onlinePollMillis) }
        val delayMillis = delays[retryIndex.coerceAtMost(delays.lastIndex)]
            .coerceIn(1L, offlinePollCeilingMillis)
        if (retryIndex < delays.lastIndex) {
            retryIndex++
        }
        return delayMillis
    }

    private fun recordUnexpectedFailure(
        trigger: String,
        throwable: Throwable,
    ): ConnectionCheckOutcome {
        consecutiveFailures++
        val nextState = if (consecutiveFailures >= failuresBeforeOffline) {
            ServerConnectionState.Offline(
                reason = UnexpectedHealthCheckFailureMessage,
                checkedAt = clock.nowIsoString(),
                retrying = true,
            )
        } else {
            _state.value
        }
        ToreadClientLog.warn(
            LogArea,
            "server.connection.check_failed",
            "trigger" to trigger,
            "errorType" to (throwable::class.simpleName ?: "unknown"),
        )
        updateState(nextState, trigger = trigger)
        return ConnectionCheckOutcome(
            succeeded = false,
            consecutiveFailures = consecutiveFailures,
        )
    }

    private fun updateState(nextState: ServerConnectionState, trigger: String) {
        val previous = _state.value
        _state.value = nextState
        if (previous.connectionKey() == nextState.connectionKey()) return

        ToreadClientLog.info(
            LogArea,
            "server.connection.state",
            "trigger" to trigger,
            "from" to previous.logName(),
            "to" to nextState.logName(),
            "reason" to (nextState as? ServerConnectionState.Offline)?.reason,
        )
    }

    private companion object {
        const val LogArea = "network"
        const val DefaultOnlinePollMillis = 30_000L
        const val DefaultOfflinePollCeilingMillis = 30_000L
        const val DefaultFailuresBeforeOffline = 2
        const val DefaultConfirmationDelayMillis = 1_000L
        const val UnexpectedHealthCheckFailureMessage = "服务器连接检查失败，请稍后重试。"
        val DefaultRetryDelaysMillis = listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L)
    }
}

private data class ConnectionCheckOutcome(
    val succeeded: Boolean,
    val consecutiveFailures: Int,
)

sealed interface ServerConnectionState {
    data class Checking(val startedAt: String) : ServerConnectionState
    data class Online(val checkedAt: String) : ServerConnectionState
    data class Offline(
        val reason: String,
        val checkedAt: String,
        val retrying: Boolean,
    ) : ServerConnectionState
}

fun ServerConnectionState.isOffline(): Boolean =
    this is ServerConnectionState.Offline

private fun ServerConnectionState.connectionKey(): String =
    when (this) {
        is ServerConnectionState.Checking -> "checking"
        is ServerConnectionState.Online -> "online"
        is ServerConnectionState.Offline -> "offline"
    }

private fun ServerConnectionState.logName(): String = connectionKey()
