package com.example.toread.lan

import com.example.toread.platform.FileHasher
import com.example.toread.platform.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json

internal sealed interface LanPeerResult<out T> {
    data class Success<T>(val value: T) : LanPeerResult<T>

    data class Failure(val error: LanClientError) : LanPeerResult<Nothing>
}

/** Where the peer listens, plus the code that unlocks it. */
internal data class LanPeerAddress(val baseUrl: String, val code: String) {
    companion object {
        /**
         * Accepts what a person actually types: `192.168.1.7:47901`, `192.168.1.7`, or a full URL.
         * The scheme is only ever `http`, because this link is plain LAN traffic by design.
         */
        fun parse(host: String, code: String): LanPeerAddress? {
            val trimmed = host.trim().trimEnd('/')
            if (trimmed.isEmpty() || !LanPairingCode.isValid(code)) return null
            val withScheme = when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                else -> "http://$trimmed"
            }
            val authority = withScheme.substringAfter("://")
            if (authority.isBlank() || authority.contains('/') || authority.contains(' ')) return null
            // Plain HTTP is only allowed to the local network, so the address must say so. Without this the
            // app could be pointed at any cleartext host on the internet.
            val host = authority.substringBefore(':')
            if (!isPrivateOrLoopbackHost(host)) return null
            return LanPeerAddress(baseUrl = withScheme, code = LanPairingCode.normalize(code))
        }
    }
}

internal data class LanPeerInfo(
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val books: List<LanBookSummary>,
)

internal data class LanDownloadedBook(
    val fileName: String,
    val tempPath: String,
    val sha256: String,
    val bytes: Long,
)

/**
 * The pulling half of the LAN exchange.
 *
 * Only `GET`s: this side never writes to the peer, so a stale code or a wrong address cannot damage the
 * other device. Every response is checked against the token and the protocol version before its body is
 * trusted, and the downloaded file is re-hashed locally instead of believing the advertised digest.
 */
internal class LanTransferClient(
    private val http: HttpClient = defaultLanHttpClient(),
    private val tempFiles: LanTempFiles = LanTempFiles(),
    private val fileHasher: FileHasher = FileHasher(),
) {
    suspend fun fetchInfo(address: LanPeerAddress): LanPeerResult<LanInfoResponse> =
        requestJson(address, LanPaths.Info) { body ->
            runCatching { LanJson.decodeFromString(LanInfoResponse.serializer(), body) }.getOrNull()
        }

    suspend fun fetchBooks(address: LanPeerAddress): LanPeerResult<List<LanBookSummary>> =
        requestJson(address, LanPaths.Books) { body ->
            runCatching { LanJson.decodeFromString(LanBookListResponse.serializer(), body) }
                .getOrNull()
                ?.books
        }

    /**
     * Streams one book to a scratch file and verifies it.
     *
     * @param onBytes received byte count, for a progress line that reflects the actual transfer rather
     *   than the advertised size.
     */
    suspend fun download(
        address: LanPeerAddress,
        book: LanBookSummary,
        onBytes: (received: Long) -> Unit = {},
    ): LanPeerResult<LanDownloadedBook> {
        val fileName = importFileName(book.title, book.format)
        return try {
            val response = http.get("${address.baseUrl}${LanPaths.bookFile(book.bookHash)}") {
                header(LanTokenHeader, address.code)
                timeout {
                    connectTimeoutMillis = LanConnectTimeoutMillis
                    // A whole book over Wi-Fi is a long request: fail on a dead socket, not on total time.
                    requestTimeoutMillis = LanDownloadTimeoutMillis
                    socketTimeoutMillis = LanSocketTimeoutMillis
                }
            }
            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                return LanPeerResult.Failure(LanClientError.Unauthorized)
            }
            if (!response.status.isSuccess()) {
                return LanPeerResult.Failure(
                    LanClientError.TransferFailed("对端返回 ${response.status.value}"),
                )
            }

            // Staging can fail for local reasons (unsupported platform, read-only cache). That is not a
            // network problem, and reporting it as "unreachable" sent the reader chasing the peer.
            val writer = try {
                tempFiles.open(fileName)
            } catch (unsupported: LanServerUnsupportedException) {
                return LanPeerResult.Failure(
                    LanClientError.TransferFailed("当前平台暂不支持接收文件"),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                return LanPeerResult.Failure(
                    LanClientError.TransferFailed(error.message ?: "无法写入暂存文件"),
                )
            }
            var received = 0L
            try {
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(LanChunkBytes)
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read > 0) {
                        writer.write(buffer, 0, read)
                        received += read
                        onBytes(received)
                    }
                }
            } finally {
                writer.close()
            }

            val digest = fileHasher.sha256(PlatformFile(displayName = fileName, path = writer.path))
            if (!digest.equals(book.bookHash, ignoreCase = true)) {
                tempFiles.delete(writer.path)
                return LanPeerResult.Failure(
                    LanClientError.TransferFailed("文件校验失败（对端哈希 ${book.bookHash.take(12)}…，实际 ${digest.take(12)}…）"),
                )
            }
            LanPeerResult.Success(
                LanDownloadedBook(
                    fileName = fileName,
                    tempPath = writer.path,
                    sha256 = digest,
                    bytes = received,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            LanPeerResult.Failure(
                LanClientError.Unreachable(throwable.message ?: "连接对端失败"),
            )
        }
    }

    private suspend fun <T> requestJson(
        address: LanPeerAddress,
        path: String,
        decode: (String) -> T?,
    ): LanPeerResult<T> =
        try {
            val response = http.get("${address.baseUrl}$path") {
                header(LanTokenHeader, address.code)
                timeout {
                    connectTimeoutMillis = LanConnectTimeoutMillis
                    requestTimeoutMillis = LanIndexTimeoutMillis
                    socketTimeoutMillis = LanIndexTimeoutMillis
                }
            }
            when {
                response.status == HttpStatusCode.Unauthorized ||
                    response.status == HttpStatusCode.Forbidden ->
                    LanPeerResult.Failure(LanClientError.Unauthorized)

                !response.status.isSuccess() ->
                    LanPeerResult.Failure(LanClientError.Unreachable("对端返回 ${response.status.value}"))

                else -> {
                    val body = response.bodyAsText()
                    val decoded = decode(body)
                    if (decoded == null) {
                        LanPeerResult.Failure(LanClientError.Unreachable("对端响应无法解析"))
                    } else {
                        LanPeerResult.Success(decoded)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            LanPeerResult.Failure(LanClientError.Unreachable(throwable.message ?: "连接对端失败"))
        }
}

/**
 * A file name that is safe to hand to the importer: no separators, always a supported extension.
 *
 * The hash, not the name, decides identity, so a lossy title is harmless — but the name still has to end
 * in a format the importer accepts, or the received file would be rejected after a full transfer.
 */
/**
 * `localhost` or a literal private IPv4 address (10/8, 172.16/12, 192.168/16, 127/8).
 *
 * The LAN link is plain HTTP, so the reachable set is deliberately narrow: a host name is refused because
 * it can resolve anywhere, and a public literal is refused because cleartext must never leave the LAN.
 */
internal fun isPrivateOrLoopbackHost(host: String): Boolean {
    val normalized = host.trim().lowercase()
    if (normalized == "localhost") return true
    val parts = normalized.split('.')
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    return when {
        octets[0] == 127 -> true
        octets[0] == 10 -> true
        octets[0] == 172 && octets[1] in 16..31 -> true
        octets[0] == 192 && octets[1] == 168 -> true
        else -> false
    }
}

internal fun importFileName(title: String, format: String): String {
    val extension = format.trim().lowercase().takeIf { it in setOf("epub", "pdf", "txt") } ?: "epub"
    val cleaned = title
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(80)
        .ifBlank { "book" }
    return "$cleaned.$extension"
}

internal fun defaultLanHttpClient(): HttpClient = HttpClient {
    install(HttpTimeout) {
        connectTimeoutMillis = LanConnectTimeoutMillis
        requestTimeoutMillis = LanDownloadTimeoutMillis
        socketTimeoutMillis = LanSocketTimeoutMillis
    }
}

internal val LanJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

private const val LanChunkBytes = 64 * 1024
private const val LanConnectTimeoutMillis = 8_000L
private const val LanIndexTimeoutMillis = 10_000L
internal const val LanSocketTimeoutMillis = 60_000L
internal const val LanDownloadTimeoutMillis = 30 * 60 * 1000L
