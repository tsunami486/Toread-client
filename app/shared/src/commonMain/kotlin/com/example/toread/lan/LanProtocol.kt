package com.example.toread.lan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Wire contract for the local-network book exchange.
 *
 * This is deliberately *not* the server sync protocol: it never touches the API server, it moves the
 * user's own book files between two devices the user explicitly paired, and it is read-only on the
 * serving side. Keeping it separate means LAN transfer can land (and be tested) without touching the
 * sync queue, the auth stack, or the server contract.
 *
 * One device **serves** the books it already has; the other **pulls** the ones it is missing. Pulling
 * keeps the pairing one-way and the serving side read-only, which is what makes it safe to put this
 * behind a switch that is off by default.
 */
internal const val LanProtocolVersion = 1

/** Short-lived shared secret from the pairing code; every request must carry it. */
internal const val LanTokenHeader = "X-Toread-Lan-Token"

internal object LanPaths {
    const val Info = "/lan/v1/info"
    const val Books = "/lan/v1/books"

    fun bookFile(bookHash: String): String = "/lan/v1/books/$bookHash/file"
}

@Serializable
internal data class LanInfoResponse(
    val protocol: Int = LanProtocolVersion,
    @SerialName("device_name") val deviceName: String,
    @SerialName("platform") val platform: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("book_count") val bookCount: Int,
)

/**
 * One book as the serving device advertises it.
 *
 * `bookHash` is the SHA-256 of the file (the same identity the shelf uses), so the pulling side can
 * decide "I already have this" without downloading a byte. `sizeBytes` is only a hint for the progress
 * UI: the size actually received and the digest computed from it are the authority.
 */
@Serializable
internal data class LanBookSummary(
    @SerialName("book_hash") val bookHash: String,
    val title: String,
    val author: String? = null,
    val format: String,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    val progress: Double = 0.0,
    @SerialName("last_read_at") val lastReadAt: String? = null,
)

@Serializable
internal data class LanBookListResponse(
    val books: List<LanBookSummary> = emptyList(),
)

/** Errors the pulling side needs to tell apart, without inventing a second vocabulary. */
internal sealed interface LanClientError {
    /** The peer answered, but refused the code. */
    data object Unauthorized : LanClientError

    /** Nothing usable at that address: wrong IP, port closed, or not a Toread peer. */
    data class Unreachable(val reason: String) : LanClientError

    /** A peer that speaks a newer protocol than this build understands. */
    data class Incompatible(val peerProtocol: Int) : LanClientError

    /** The transfer itself failed (connection lost mid-file, digest mismatch). */
    data class TransferFailed(val reason: String) : LanClientError
}

/**
 * Pairing code: six characters from an alphabet without `0/O`, `1/I/L` or `2/Z`, because the code gets
 * read aloud, typed on a desktop keyboard, and occasionally dictated over a phone call.
 */
internal object LanPairingCode {
    internal const val Length = 6
    private const val Alphabet = "3456789ABCDEFGHJKMNPQRSTVWXY"
    private val separatorPattern = Regex("[\\s\\-_:]+")

    fun generate(random: Random = Random.Default): String =
        buildString(Length) { repeat(Length) { append(Alphabet[random.nextInt(Alphabet.length)]) } }

    /** Uppercases and drops separators, so "ab-cd ef" and "ABCDEF" are the same code. */
    fun normalize(input: String): String =
        input.replace(separatorPattern, "").trim().uppercase().filter { it.isLetterOrDigit() }

    /** Constant-time compare: the code is a shared secret, so length leaks are worth avoiding. */
    fun matches(expected: String, presented: String): Boolean {
        val left = normalize(expected)
        val right = normalize(presented)
        if (left.length != right.length) return false
        var difference = 0
        for (index in left.indices) {
            difference = difference or (left[index].code xor right[index].code)
        }
        return difference == 0
    }

    /** A code is usable once it is long enough; short codes are refused instead of silently accepted. */
    fun isValid(code: String): Boolean = normalize(code).length >= Length
}

/** What the serving side needs in order to answer requests. */
internal data class LanServeRequest(
    val port: Int,
    val token: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
)

/** The serving side has no implementation on this platform yet. */
internal class LanServerUnsupportedException :
    IllegalStateException("Lan serving is not implemented on this platform yet")

/** A running server: the UI only needs where it listens, and how to stop it. */
internal interface LanServerHandle {
    /** e.g. `http://192.168.1.7:47901`, for the other device to type in. */
    val baseUrl: String

    val port: Int

    fun stop()
}

/**
 * Serves the local shelf over the LAN.
 *
 * Implementations must: bind to the requested port only, refuse every request without a matching
 * [LanTokenHeader], serve `GET` only, and never expose a path outside the app's own books directory.
 */
internal expect class LanServer() {
    suspend fun start(
        request: LanServeRequest,
        indexProvider: suspend () -> List<LanBookSummary>,
        fileResolver: (bookHash: String) -> String?,
    ): LanServerHandle
}

/** IPv4 addresses this device is reachable at on its local networks, for the pairing screen. */
internal expect fun lanIpv4Addresses(): List<String>
