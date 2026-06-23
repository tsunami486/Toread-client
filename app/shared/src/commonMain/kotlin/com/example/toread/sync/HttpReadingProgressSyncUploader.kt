package com.example.toread.sync

import com.example.toread.progress.ReadingProgressSyncPayload
import com.example.toread.reader.BookLocatorJson
import io.ktor.client.HttpClient
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
        install(ContentNegotiation) {
            json(SyncHttpJson)
        }
    },
) : ReadingProgressSyncUploader {
    override suspend fun upload(payload: ReadingProgressSyncPayload): SyncUploadResult {
        val baseUrl = baseUrlProvider().trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return SyncUploadResult.Unavailable("服务器地址为空")
        }

        val accessToken = accessTokenProvider()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return SyncUploadResult.Unavailable("缺少 access token，保留同步队列等待登录")

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
                val body = response.bodyAsText().take(240)
                SyncUploadResult.Failed("HTTP ${response.status.value}: $body")
            }
        }.getOrElse { throwable ->
            SyncUploadResult.Failed(throwable.message ?: throwable::class.simpleName ?: "同步请求失败")
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
