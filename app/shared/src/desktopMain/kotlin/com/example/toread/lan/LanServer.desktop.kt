package com.example.toread.lan

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

/**
 * The serving half of the LAN exchange, on NanoHTTPD: about a hundred kilobytes, no coroutine server, and
 * one request thread per transfer is exactly the load a two-device transfer has.
 *
 * Every request is checked before it can read anything:
 *  1. `GET` only — the peer can never write to this device;
 *  2. the pairing code in [LanTokenHeader] must match;
 *  3. a book file is only served when the resolved path is inside the app's own books directory.
 */
internal actual class LanServer actual constructor() {
    actual suspend fun start(
        request: LanServeRequest,
        indexProvider: suspend () -> List<LanBookSummary>,
        fileResolver: (bookHash: String) -> String?,
    ): LanServerHandle {
        val handler = LanNanoServer(
            port = request.port,
            request = request,
            indexProvider = indexProvider,
            fileResolver = fileResolver,
        )
        handler.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        val baseUrl = "http://${lanIpv4Addresses().firstOrNull() ?: "127.0.0.1"}:${request.port}"
        return object : LanServerHandle {
            override val baseUrl: String = baseUrl
            override val port: Int = request.port
            override fun stop() = handler.stop()
        }
    }
}

private class LanNanoServer(
    port: Int,
    private val request: LanServeRequest,
    private val indexProvider: suspend () -> List<LanBookSummary>,
    private val fileResolver: (String) -> String?,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.GET) {
            return text(Response.Status.METHOD_NOT_ALLOWED, "GET only")
        }
        val presented = session.headers
            .entries
            .firstOrNull { it.key.equals(LanTokenHeader, ignoreCase = true) }
            ?.value
        if (!LanPairingCode.matches(request.token, presented.orEmpty())) {
            return text(Response.Status.UNAUTHORIZED, "pairing code required")
        }

        val uri = session.uri.trimEnd('/')
        return when {
            uri == LanPaths.Info -> serveInfo()
            uri == LanPaths.Books -> serveBooks()
            uri.startsWith(LanPaths.Books) && uri.endsWith("/file") -> {
                val hash = uri.removePrefix(LanPaths.Books).trim('/').removeSuffix("/file")
                serveFile(hash)
            }
            else -> text(Response.Status.NOT_FOUND, "unknown path")
        }
    }

    private fun serveInfo(): Response {
        val books = runBlocking { indexProvider() }
        val body = LanJson.encodeToString(
            LanInfoResponse(
                deviceName = request.deviceName,
                platform = request.platform,
                appVersion = request.appVersion,
                bookCount = books.size,
            ),
        )
        return text(Response.Status.OK, body)
    }

    private fun serveBooks(): Response {
        val books = runBlocking { indexProvider() }
        val body = LanJson.encodeToString(LanBookListResponse(books = books))
        return text(Response.Status.OK, body)
    }

    private fun serveFile(bookHash: String): Response {
        if (bookHash.isBlank() || bookHash.any { it == '/' || it == '\\' || it == ':' }) {
            return text(Response.Status.BAD_REQUEST, "bad hash")
        }
        val resolved = fileResolver(bookHash)
            ?: return text(Response.Status.NOT_FOUND, "no such book")
        val file = runCatching { File(resolved).canonicalFile }.getOrNull()
            ?: return text(Response.Status.NOT_FOUND, "no such book")
        // Defence in depth: a stored path can only ever point inside the app's own books directory.
        if (!file.isFile || !file.toPath().isInsideBooksRoot()) {
            return text(Response.Status.NOT_FOUND, "no such book")
        }
        val length = file.length()
        val stream: InputStream = file.inputStream()
        return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", stream, length)
    }

    private fun text(status: Response.Status, message: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", message)
}

private fun Path.isInsideBooksRoot(): Boolean {
    val root = runCatching { booksRoot().toRealPath() }.getOrNull() ?: return false
    val target = runCatching { toRealPath() }.getOrNull() ?: return false
    return target.startsWith(root)
}

private fun booksRoot(): Path =
    Paths.get(System.getProperty("user.home"), ".toread", "books")

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

/**
 * Scratch files for incoming transfers live next to the library but outside it, so a half-received book
 * can never be mistaken for an imported one, and are deleted as soon as the import answers.
 */
internal actual class LanTempFiles actual constructor() {
    actual suspend fun open(fileName: String): LanTempWriter {
        val directory = Paths.get(System.getProperty("user.home"), ".toread", "lan-incoming")
        Files.createDirectories(directory)
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        val file = directory.resolve("${UUID.randomUUID()}-$safeName")
        val output = FileOutputStream(file.toFile())
        return object : LanTempWriter {
            override val path: String = file.toAbsolutePath().toString()

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
        runCatching { Files.deleteIfExists(Paths.get(path)) }
    }
}

internal actual fun lanServeRequestFromEnvironment(): LanServeRequest? {
    val port = System.getenv("TOREAD_LAN_SERVE_PORT")?.trim()?.toIntOrNull() ?: return null
    if (port !in 1..65535) return null
    val code = System.getenv("TOREAD_LAN_SERVE_TOKEN")?.trim().orEmpty()
    if (!LanPairingCode.isValid(code)) return null
    return LanServeRequest(
        port = port,
        token = LanPairingCode.normalize(code),
        deviceName = System.getenv("TOREAD_LAN_DEVICE_NAME")?.trim()?.takeIf { it.isNotBlank() }
            ?: "Toread Desktop",
        platform = "desktop",
        appVersion = com.example.toread.config.GeneratedToreadBuildConfig.appVersion,
    )
}
