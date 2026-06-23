package com.example.toread.sync

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

class HttpBookMetadataSyncUploader(
    private val baseUrlProvider: () -> String,
    private val accessTokenProvider: suspend () -> String?,
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(BookSyncHttpJson)
        }
    },
) : BookMetadataSyncUploader {
    override suspend fun upload(payload: BookMetadataSyncPayload): BookMetadataUploadResult {
        val baseUrl = baseUrlProvider().trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return BookMetadataUploadResult.Unavailable("服务器地址为空")
        }

        val accessToken = accessTokenProvider()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return BookMetadataUploadResult.Unavailable("缺少 access token，保留同步队列等待登录")

        return runCatching {
            val response = client.put("$baseUrl/books/${payload.bookHash}") {
                bearerAuth(accessToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(payload.toRequest())
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val book = BookSyncHttpJson
                    .decodeFromString(BookResponse.serializer(), body)
                    .toRemoteBookMetadata()
                BookMetadataUploadResult.Uploaded(book)
            } else {
                val body = response.bodyAsText().take(240)
                BookMetadataUploadResult.Failed("HTTP ${response.status.value}: $body")
            }
        }.getOrElse { throwable ->
            BookMetadataUploadResult.Failed(throwable.message ?: throwable::class.simpleName ?: "书籍同步请求失败")
        }
    }
}

interface BookMetadataSyncUploader {
    suspend fun upload(payload: BookMetadataSyncPayload): BookMetadataUploadResult
}

sealed interface BookMetadataUploadResult {
    data class Uploaded(val book: RemoteBookMetadata) : BookMetadataUploadResult
    data class Unavailable(val reason: String) : BookMetadataUploadResult
    data class Failed(val message: String) : BookMetadataUploadResult
}

data class RemoteBookMetadata(
    val bookHash: String,
    val title: String,
    val author: String?,
    val format: String,
    val metadataJson: JsonObject,
    val updatedAt: String,
)

@Serializable
private data class UpsertBookRequest(
    val title: String,
    val author: String? = null,
    @SerialName("file_type")
    val fileType: String,
    @SerialName("metadata_json")
    val metadataJson: JsonObject,
)

@Serializable
private data class BookResponse(
    @SerialName("book_hash")
    val bookHash: String,
    val title: String,
    val author: String? = null,
    @SerialName("file_type")
    val fileType: String,
    @SerialName("metadata_json")
    val metadataJson: JsonObject,
    @SerialName("updated_at")
    val updatedAt: String,
)

private fun BookMetadataSyncPayload.toRequest(): UpsertBookRequest =
    UpsertBookRequest(
        title = title,
        author = author,
        fileType = format.uppercase(),
        metadataJson = metadataJson,
    )

private fun BookResponse.toRemoteBookMetadata(): RemoteBookMetadata =
    RemoteBookMetadata(
        bookHash = bookHash,
        title = title,
        author = author,
        format = fileType,
        metadataJson = metadataJson,
        updatedAt = updatedAt,
    )

private val BookSyncHttpJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
