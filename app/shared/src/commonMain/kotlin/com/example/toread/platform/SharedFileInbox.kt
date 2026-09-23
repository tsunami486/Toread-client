package com.example.toread.platform

import androidx.compose.runtime.mutableStateListOf

/**
 * Files the operating system handed to the app instead of the user picking them in-app:
 * a file-manager drop on desktop, and an "open with" / share intent on Android.
 *
 * [com.example.toread.App] drains this into the same `BookImportService` the picker uses, so
 * there is only ever one import path. The list is snapshot state so offering a file from the
 * platform side recomposes the app and triggers the drain.
 */
object SharedFileInbox {
    private val pending = mutableStateListOf<PlatformFile>()

    /** Files waiting to be imported; read from composition to react to new arrivals. */
    val size: Int get() = pending.size

    fun offer(files: List<PlatformFile>) {
        if (files.isNotEmpty()) pending.addAll(files)
    }

    /** Takes everything queued and clears it, so one batch is imported exactly once. */
    fun drain(): List<PlatformFile> {
        val files = pending.toList()
        pending.clear()
        return files
    }
}
