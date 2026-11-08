package com.example.toread.lan

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Android still has to be wired up: the serving side needs a session that survives screen rotation and a
 * foreground-service story for long transfers, and that is deliberately a separate step from proving the
 * protocol works. Until then the app reports "暂不支持" instead of silently doing nothing.
 */
internal actual class LanServer actual constructor() {
    actual suspend fun start(
        request: LanServeRequest,
        indexProvider: suspend () -> List<LanBookSummary>,
        fileResolver: (bookHash: String) -> String?,
    ): LanServerHandle = throw LanServerUnsupportedException()
}

internal actual class LanTempFiles actual constructor() {
    actual suspend fun open(fileName: String): LanTempWriter = throw LanServerUnsupportedException()

    /** Nothing was staged, so there is nothing to clean up. */
    actual suspend fun delete(path: String) = Unit
}

internal actual fun lanIpv4Addresses(): List<String> =
    runCatching {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
            .map { it.hostAddress.orEmpty() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }.getOrDefault(emptyList())

/** No debug serving hook on Android: the phone should never open a port because of an environment value. */
internal actual fun lanServeRequestFromEnvironment(): LanServeRequest? = null
