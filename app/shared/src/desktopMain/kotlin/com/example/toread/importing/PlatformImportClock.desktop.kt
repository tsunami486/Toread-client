package com.example.toread.importing

import java.time.Instant

actual class PlatformImportClock actual constructor() : ImportClock {
    override fun nowIsoString(): String = Instant.now().toString()
}
