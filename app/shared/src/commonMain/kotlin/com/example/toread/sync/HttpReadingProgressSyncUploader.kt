package com.example.toread.sync

import com.example.toread.progress.ReadingProgressSyncPayload
import com.example.toread.reader.BookLocatorJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class HttpReadingProgressSyncUploader(
    private val baseUrlProvider: () -> String,
    private val accessTokenProvider: suspend () -> String?,
    private val client: HttpClient = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = SyncHttpTimeoutMillis
            requestTimeoutMillis = SyncHttpTimeoutMillis
            socketTimeoutMillis = SyncHttpTimeoutMillis
        }
        install(ContentNegotiation) {
            json(SyncHttpJson)
        }
    },
) : ReadingProgressSyncUploader {
    override suspend fun upload(payload: ReadingProgressSyncPayload): SyncUploadResult {
        val baseUrl = baseUrlProvider().trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return SyncUploadResult.Unavailable(SyncServiceUnavailableMessage)
        }

        val accessToken = accessTokenProvider()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return SyncUploadResult.Unavailable(LoginRequiredForSyncMessage)

        return runCatching {
            val response = client.put("$baseUrl/progress/${payload.bookHash}") {
                bearerAuth(accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(payload.toRequest())
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val progress = SyncHttpJson
                    .decodeFromString(ProgressResponse.serializer(), body)
                    .toRemoteReadingProgress()
                SyncUploadResult.Uploaded(progress)
            } else {
                SyncUploadResult.Failed(SyncFailedMessage)
            }
        }.getOrElse { throwable ->
            SyncUploadResult.Failed(SyncFailedMessage)
        }
    }
}

@Serializable
private data class UpsertProgressRequest(
    @SerialName("locator_json")
    val locatorJson: JsonObject,
    @SerialName("progress_percent")
    val progressPercent: Double,
)

private fun ReadingProgressSyncPayload.toRequest(): UpsertProgressRequest =
    UpsertProgressRequest(
        locatorJson = SyncHttpJson.parseToJsonElement(BookLocatorJson.encode(locator)).jsonObject,
        progressPercent = progress,
    )

private val SyncHttpJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

private const val SyncHttpTimeoutMillis = 3_000L
private const val SyncServiceUnavailableMessage = "同步服务暂不可用，请稍后重试。"
private const val LoginRequiredForSyncMessage = "请先登录后再同步。"
private const val SyncFailedMessage = "同步失败，请稍后重试。"
