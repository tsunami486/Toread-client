package com.example.toread.config

import com.example.toread.PlatformKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerBaseUrlConfigTest {
    @Test
    fun blankConfigurationUsesPlatformDevelopmentDefaults() {
        assertEquals(
            "http://10.0.2.2:8080",
            resolveServerBaseUrl(PlatformKind.Android, "  "),
        )
        assertEquals(
            "http://localhost:8080",
            resolveServerBaseUrl(PlatformKind.Desktop, ""),
        )
    }

    @Test
    fun configuredUrlIsNormalizedAndSharedByBothPlatforms() {
        val configuredBaseUrl = "  https://toread-api.example.ts.net///  "

        assertEquals(
            "https://toread-api.example.ts.net",
            resolveServerBaseUrl(PlatformKind.Android, configuredBaseUrl),
        )
        assertEquals(
            "https://toread-api.example.ts.net",
            resolveServerBaseUrl(PlatformKind.Desktop, configuredBaseUrl),
        )
    }
}
