package com.example.toread.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProgressSyncScheduler(
    private val scope: CoroutineScope,
    private val syncQueueProcessorProvider: () -> SyncQueueProcessor?,
    private val debounceMillis: Long = DefaultDebounceMillis,
) {
    private var debounceJob: Job? = null

    fun scheduleDebounced() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMillis)
            debounceJob = null
            syncQueueProcessorProvider()?.flushPending()
        }
    }

    fun flushNow(): Job {
        debounceJob?.cancel()
        debounceJob = null
        return scope.launch {
            syncQueueProcessorProvider()?.flushPending()
        }
    }

    private companion object {
        const val DefaultDebounceMillis = 10_000L
    }
}
