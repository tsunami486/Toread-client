package com.example.toread.config

import com.example.toread.PlatformKind

internal object ServerBaseUrlConfig {
    fun resolve(platformKind: PlatformKind): String =
        resolveServerBaseUrl(
            platformKind = platformKind,
            configuredBaseUrl = GeneratedToreadBuildConfig.serverBaseUrl,
        )
}

internal fun resolveServerBaseUrl(
    platformKind: PlatformKind,
    configuredBaseUrl: String,
): String {
    val normalizedBaseUrl = configuredBaseUrl.trim().trimEnd('/')
    if (normalizedBaseUrl.isNotBlank()) return normalizedBaseUrl

    return when (platformKind) {
        PlatformKind.Android -> AndroidEmulatorServerBaseUrl
        PlatformKind.Desktop -> DesktopServerBaseUrl
    }
}

private const val AndroidEmulatorServerBaseUrl = "http://10.0.2.2:8080"
private const val DesktopServerBaseUrl = "http://localhost:8080"
