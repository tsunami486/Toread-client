package com.example.toread.platform

import androidx.compose.runtime.Composable

internal data class ReaderStatusSnapshot(
    val clockText: String,
    val batteryPercent: Int?,
)

internal fun interface ReaderStatusProvider {
    fun currentStatus(): ReaderStatusSnapshot
}

@Composable
internal expect fun rememberReaderStatusProvider(): ReaderStatusProvider
