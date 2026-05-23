package com.example.toread.progress

import java.time.Instant

actual class PlatformProgressClock actual constructor() : ProgressClock {
    override fun nowIsoString(): String = Instant.now().toString()
}
