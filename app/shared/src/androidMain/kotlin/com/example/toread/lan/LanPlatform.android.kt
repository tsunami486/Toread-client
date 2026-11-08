package com.example.toread.lan

import com.example.toread.database.AndroidAppContext
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

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

/**
 * Incoming books are staged in the app cache directory, then verified and imported exactly like a picked
 * file. Cache is the right home for a scratch file: it is private to the app, it survives nothing, and the
 * received copy under `files/books/<hash>` becomes the only lasting one.
 */
internal actual class LanTempFiles actual constructor() {
    actual suspend fun open(fileName: String): LanTempWriter {
        val directory = File(AndroidAppContext.applicationContext.cacheDir, "lan-incoming")
        directory.mkdirs()
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        val file = File(directory, "${UUID.randomUUID()}-$safeName")
        val output = FileOutputStream(file)
        return object : LanTempWriter {
            override val path: String = file.absolutePath

            override suspend fun write(buffer: ByteArray, offset: Int, count: Int) {
                output.write(buffer, offset, count)
            }

            override suspend fun close() {
                output.flush()
                output.close()
            }
        }
    }

    actual suspend fun delete(path: String) {
        runCatching { File(path).delete() }
    }
}

internal actual fun lanIpv4Addresses(): List<String> =
    prioritizedLanAddresses(rawIpv4Addresses())

private fun rawIpv4Addresses(): List<String> =
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
