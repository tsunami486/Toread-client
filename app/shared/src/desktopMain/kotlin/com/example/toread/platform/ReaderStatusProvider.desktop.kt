package com.example.toread.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
internal actual fun rememberReaderStatusProvider(): ReaderStatusProvider =
    remember {
        ReaderStatusProvider {
            ReaderStatusSnapshot(
                clockText = LocalTime.now().format(ReaderClockFormatter),
                batteryPercent = null,
            )
        }
    }

private val ReaderClockFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm")
