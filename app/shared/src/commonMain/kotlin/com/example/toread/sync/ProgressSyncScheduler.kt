package com.example.toread.sync

import com.example.toread.logging.ToreadClientLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Wakes the sync queue as soon as a local write enqueues work.
 *
 * Requests are generation based: a request that arrives during a flush causes exactly one
 * follow-up pass over the latest queue state. An active HTTP upload is never cancelled.
 */
class ProgressSyncScheduler(
    private val scope: CoroutineScope,
    private val flushPending: suspend () -> SyncRunSummary?,
    private val onFlushFinished: (SyncRunSummary) -> Unit = {},
) {
    private val stateMutex = Mutex()
    private var requestedGeneration = 0L
    private var completedGeneration = 0L
    private var workerJob: Job? = null
    private val waiters = mutableListOf<FlushWaiter>()

    /** Requests an immediate, asynchronous queue pass. */
    fun requestFlush(): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        stateMutex.withLock {
            requestedGeneration++
            ensureWorkerLocked()
        }
    }

    /**
     * Requests a fresh queue pass and completes only after that generation has been attempted.
     * This is used by reader exit/background paths that must not miss the latest local save.
     */
    fun flushNow(): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        val completion = CompletableDeferred<Unit>()
        stateMutex.withLock {
            val targetGeneration = ++requestedGeneration
            waiters += FlushWaiter(targetGeneration, completion)
            ensureWorkerLocked()
        }
        completion.await()
    }

    private fun ensureWorkerLocked() {
        if (workerJob?.isActive == true) return
        workerJob = scope.launch { drainRequestedGenerations() }
    }

    private suspend fun drainRequestedGenerations() {
        val currentJob = currentCoroutineContext()[Job]
        try {
            while (true) {
                val targetGeneration = stateMutex.withLock {
                    if (completedGeneration >= requestedGeneration) {
                        if (workerJob === currentJob) workerJob = null
                        return
                    }
                    requestedGeneration
                }

                try {
                    flushPending()?.let(onFlushFinished)
                } catch (cancelled: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw cancelled
                    ToreadClientLog.warn(
                        LogArea,
                        "queue.flush.cancelled",
                        "error" to (cancelled.message ?: cancelled::class.simpleName),
                    )
                } catch (exception: Exception) {
                    ToreadClientLog.warn(
                        LogArea,
                        "queue.flush.unexpected",
                        "error" to (exception.message ?: exception::class.simpleName),
                    )
                }

                val completedWaiters = stateMutex.withLock {
                    completedGeneration = maxOf(completedGeneration, targetGeneration)
                    val completed = waiters.filter { it.targetGeneration <= completedGeneration }
                    waiters.removeAll(completed.toSet())
                    completed
                }
                completedWaiters.forEach { it.completion.complete(Unit) }
            }
        } finally {
            stateMutex.withLock {
                if (workerJob === currentJob) workerJob = null
            }
        }
    }

    private data class FlushWaiter(
        val targetGeneration: Long,
        val completion: CompletableDeferred<Unit>,
    )

    private companion object {
        const val LogArea = "sync"
    }
}
