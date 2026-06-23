package com.example.toread.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class BookMetadataSyncPayload(
    val bookHash: String,
    val title: String,
    val author: String?,
    val format: String,
    val metadataJson: JsonObject,
    val updatedAt: String,
)

object BookMetadataJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(payload: BookMetadataSyncPayload): String =
        json.encodeToString(BookMetadataSyncPayload.serializer(), payload)

    fun decode(value: String): BookMetadataSyncPayload =
        json.decodeFromString(BookMetadataSyncPayload.serializer(), value)

    fun parseMetadataJson(value: String): JsonObject =
        runCatching { json.parseToJsonElement(value) as? JsonObject }
            .getOrNull()
            ?: JsonObject(emptyMap())
}
