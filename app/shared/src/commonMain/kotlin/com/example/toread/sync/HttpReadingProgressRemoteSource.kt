package com.example.toread.sync

import com.example.toread.reader.BookLocator
import com.example.toread.reader.BookLocatorJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class HttpReadingProgressRemoteSource(
    private val baseUrlProvider: () -> String,
    private val accessTokenProvider: suspend () -> String?,
    private val client: HttpClient = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = RemoteProgressHttpTimeoutMillis
            requestTimeoutMillis = RemoteProgressHttpTimeoutMillis
            socketTimeoutMillis = RemoteProgressHttpTimeoutMillis
        }
        install(ContentNegotiation) {
            json(RemoteProgressJson)
        }
    },
) : ReadingProgressRemoteSource {
    override suspend fun fetch(bookHash: String): RemoteProgressFetchResult {
        val baseUrl = baseUrlProvider().trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return RemoteProgressFetchResult.Unavailable(RemoteProgressUnavailableMessage)
        }

        val accessToken = accessTokenProvider()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return RemoteProgressFetchResult.Unavailable(LoginRequiredForSyncMessage)

        return runCatching {
            val response = client.get("$baseUrl/progress/$bookHash") {
                bearerAuth(accessToken)
            }
            when {
                response.status == HttpStatusCode.NotFound -> RemoteProgressFetchResult.NotFound
                response.status == HttpStatusCode.Unauthorized ->
                    RemoteProgressFetchResult.Unauthorized(RemoteProgressUnauthorizedMessage)
                response.status.isSuccess() -> {
                    val body = response.bodyAsText()
                    val progress = RemoteProgressJson.decodeFromString(ProgressResponse.serializer(), body)
                    RemoteProgressFetchResult.Found(progress.toRemoteReadingProgress())
                }
                else -> RemoteProgressFetchResult.Failed(RemoteProgressFetchFailedMessage)
            }
        }.getOrElse { throwable ->
            RemoteProgressFetchResult.Failed(RemoteProgressFetchFailedMessage)
        }
    }
}

interface ReadingProgressRemoteSource {
    suspend fun fetch(bookHash: String): RemoteProgressFetchResult
}

sealed interface RemoteProgressFetchResult {
    data class Found(val progress: RemoteReadingProgress) : RemoteProgressFetchResult
    data object NotFound : RemoteProgressFetchResult
    data class Unavailable(val reason: String) : RemoteProgressFetchResult
    data class Unauthorized(val reason: String) : RemoteProgressFetchResult
    data class Failed(val message: String) : RemoteProgressFetchResult
}

data class RemoteReadingProgress(
    val bookHash: String,
    val locator: BookLocator,
    val progress: Double,
    val updatedAt: String,
)

@Serializable
internal data class ProgressResponse(
    @SerialName("book_hash")
    val bookHash: String,
    @SerialName("locator_json")
    val locatorJson: JsonObject,
    @SerialName("progress_percent")
    val progressPercent: Double,
    @SerialName("updated_at")
    val updatedAt: String,
)

internal fun ProgressResponse.toRemoteReadingProgress(): RemoteReadingProgress =
    RemoteReadingProgress(
        bookHash = bookHash,
        locator = BookLocatorJson.decode(RemoteProgressJson.encodeToString(locatorJson)),
        progress = progressPercent,
        updatedAt = updatedAt,
    )

private val RemoteProgressJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * The compensation GET runs in the background and its result is optional, but it still deserves the
 * same weak-network budget as the other calls: 3 seconds reported "云端进度暂不可用" for a slow answer.
 */
private const val RemoteProgressHttpTimeoutMillis = 15_000L
private const val RemoteProgressUnavailableMessage = "云端进度暂不可用，请稍后重试。"
private const val RemoteProgressUnauthorizedMessage = "登录状态已失效，正在重新登录。"
private const val RemoteProgressFetchFailedMessage = "云端进度获取失败，请稍后重试。"
private const val LoginRequiredForSyncMessage = "请先登录后再同步。"
