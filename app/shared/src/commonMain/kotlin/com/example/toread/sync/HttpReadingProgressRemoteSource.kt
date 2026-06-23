package com.example.toread.sync

import com.example.toread.reader.BookLocator
import com.example.toread.reader.BookLocatorJson
import io.ktor.client.HttpClient
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
        install(ContentNegotiation) {
            json(RemoteProgressJson)
        }
    },
) : ReadingProgressRemoteSource {
    override suspend fun fetch(bookHash: String): RemoteProgressFetchResult {
        val baseUrl = baseUrlProvider().trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return RemoteProgressFetchResult.Unavailable("服务器地址为空")
        }

        val accessToken = accessTokenProvider()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return RemoteProgressFetchResult.Unavailable("缺少 access token")

        return runCatching {
            val response = client.get("$baseUrl/progress/$bookHash") {
                bearerAuth(accessToken)
            }
            when {
                response.status == HttpStatusCode.NotFound -> RemoteProgressFetchResult.NotFound
                response.status.isSuccess() -> {
                    val body = response.bodyAsText()
                    val progress = RemoteProgressJson.decodeFromString(ProgressResponse.serializer(), body)
                    RemoteProgressFetchResult.Found(progress.toRemoteReadingProgress())
                }
                else -> RemoteProgressFetchResult.Failed(
                    "HTTP ${response.status.value}: ${response.bodyAsText().take(240)}",
                )
            }
        }.getOrElse { throwable ->
            RemoteProgressFetchResult.Failed(throwable.message ?: throwable::class.simpleName ?: "远端进度请求失败")
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
