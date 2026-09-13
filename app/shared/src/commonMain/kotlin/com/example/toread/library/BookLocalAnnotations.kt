package com.example.toread.library

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The user's own annotations for a book — its tags and a free-form note — kept inside the book's
 * metadata JSON next to the import metadata.
 *
 * They are edited locally and travel with the normal `book_metadata` upsert, so nothing else in the
 * metadata JSON may be dropped when they are rewritten.
 */
data class BookLocalAnnotations(
    val tags: String = "",
    val notes: String = "",
) {
    val isEmpty: Boolean get() = tags.isBlank() && notes.isBlank()
}

internal const val LocalTagsKey = "local_tags"
internal const val LocalNotesKey = "local_notes"

private val annotationsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

internal fun parseBookLocalAnnotations(metadataJson: String?): BookLocalAnnotations {
    val root = metadataJson.asJsonObjectOrNull()
    return BookLocalAnnotations(
        tags = root?.get(LocalTagsKey).asStringOrEmpty(),
        notes = root?.get(LocalNotesKey).asStringOrEmpty(),
    )
}

/** Rewrites the metadata JSON with [annotations], leaving every other key exactly as it was. */
internal fun encodeBookLocalAnnotations(
    metadataJson: String?,
    annotations: BookLocalAnnotations,
): String {
    val existing = metadataJson.asJsonObjectOrNull()
    val merged = buildJsonObject {
        existing?.forEach { (key, value) ->
            if (key != LocalTagsKey && key != LocalNotesKey) put(key, value)
        }
        annotations.tags.trim().takeIf { it.isNotEmpty() }?.let { put(LocalTagsKey, it) }
        annotations.notes.trim().takeIf { it.isNotEmpty() }?.let { put(LocalNotesKey, it) }
    }
    return annotationsJson.encodeToString(JsonObject.serializer(), merged)
}

private fun String?.asJsonObjectOrNull(): JsonObject? =
    runCatching { annotationsJson.parseToJsonElement(orEmpty()) as? JsonObject }.getOrNull()

private fun kotlinx.serialization.json.JsonElement?.asStringOrEmpty(): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
