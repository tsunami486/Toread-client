package com.example.toread.platform

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

object AppBackgroundNotifier {
    private var onEnterBackground: (() -> Job?)? = null
    private var onEnterForeground: (() -> Unit)? = null

    fun setOnEnterBackground(callback: (() -> Job?)?) {
        onEnterBackground = callback
    }

    fun notifyEnterBackground() {
        onEnterBackground?.invoke()
    }

    fun setOnEnterForeground(callback: (() -> Unit)?) {
        onEnterForeground = callback
    }

    fun notifyEnterForeground() {
        onEnterForeground?.invoke()
    }

    fun notifyEnterBackgroundAndWait(timeoutMillis: Long = 1_500L) {
        val job = onEnterBackground?.invoke() ?: return
        runBlocking {
            withTimeoutOrNull(timeoutMillis) {
                job.join()
            }
        }
    }
}
