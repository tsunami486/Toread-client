package com.example.toread.metadata

import com.example.toread.categories.BookCategoryEntry
import com.example.toread.categories.encodeCategoryEntries
import com.example.toread.categories.parseCategoryEntries
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What the importer read out of the book file itself — the description and the category-ish tags a
 * publication carries in its own package metadata, or the 「内容简介」 front-matter page it ships
 * with — together with the metadata-JSON keys it lives under.
 *
 * This package is the neutral floor both the importer and the bookshelf build on: the importer
 * writes these keys, the bookshelf reads them, and neither has to know about the other.
 */

/** Where an extracted value came from. The stored strings are persisted in the metadata JSON. */
enum class BookInsightSource(val storedValue: String) {
    /** `<dc:description>` / EPUB3 description metadata. */
    EpubPackage("epub_package"),

    /** A front-matter document the navigation calls 内容简介 and friends. */
    EpubFrontMatter("epub_front_matter"),

    /** A 「内容简介」 marker inside a plain-text book. */
    TxtMarker("txt_marker"),

    /** The PDF information dictionary (desktop only; Android's renderer exposes no metadata). */
    PdfInfo("pdf_info"),

    /** Bracketed genre words in the file name. */
    FileName("file_name"),
    ;

    companion object {
        fun fromStoredValue(value: String?): BookInsightSource? =
            entries.firstOrNull { it.storedValue == value }
    }
}

/**
 * One extraction result.
 *
 * [descriptionOrigin] is the human-readable hint of the exact place the text came from — the
 * navigation label, the OPF field or the file name — so the book info page can say where a
 * suggestion came from instead of presenting it as fact.
 */
data class BookInsight(
    val description: String? = null,
    val descriptionSource: BookInsightSource? = null,
    val descriptionOrigin: String? = null,
    val subjects: List<String> = emptyList(),
    val series: String? = null,
    val authorHint: String? = null,
    /**
     * A bounded sample of the book's **story text**, used only to infer a category — never stored in
     * the book's metadata and never shown to the reader. Null when the format cannot offer one.
     */
    val bodySample: String? = null,
) {
    val isEmpty: Boolean
        get() = description.isNullOrBlank() &&
            subjects.isEmpty() &&
            series.isNullOrBlank() &&
            authorHint.isNullOrBlank()
}

/**
 * The insight as it is stored in the book's metadata JSON.
 *
 * These keys are *candidates*: `local_tags` / `local_notes` belong to the reader, so a later
 * extraction pass (a manual refresh, or the button on the book info page) only ever rewrites the
 * `imported_*` keys. A fresh import may prefill the reader fields because a brand-new book has
 * nothing there yet.
 */
data class StoredBookInsight(
    val description: String? = null,
    val descriptionSource: BookInsightSource? = null,
    val descriptionOrigin: String? = null,
    val subjects: List<String> = emptyList(),
    val series: String? = null,
    /** Categories the sampled **body text** supported, kept as `关键词 -> 分类` pairs. */
    val bodyEvidence: List<BookCategoryEntry> = emptyList(),
    /** True once an extraction pass ran at the current version, even when it found nothing. */
    val checked: Boolean = false,
    /** Whether the pass that wrote this also ran 自动分类, so flipping the switch re-visits the book. */
    val autoCategorized: Boolean = false,
) {
    val isEmpty: Boolean
        get() = description.isNullOrBlank() && subjects.isEmpty() && series.isNullOrBlank()

    fun toInsight(): BookInsight = BookInsight(
        description = description,
        descriptionSource = descriptionSource,
        descriptionOrigin = descriptionOrigin,
        subjects = subjects,
        series = series,
    )
}

/**
 * Bumped when the extractor learns to read something new, so old books are visited once more.
 *
 * Version 2 drops the 「内容简介：」 heading and the author/status line printed above it from the
 * description, so books filled by version 1 are re-read once.
 */
internal const val ImportedInsightVersion = "2"

internal const val ImportedInsightVersionKey = "imported_insight_version"
internal const val ImportedAutoCategorizeKey = "imported_auto_categorize"
internal const val ImportedDescriptionKey = "imported_description"
internal const val ImportedDescriptionSourceKey = "imported_description_source"
internal const val ImportedDescriptionOriginKey = "imported_description_origin"
internal const val ImportedTagsKey = "imported_tags"
internal const val ImportedBodyTagsKey = "imported_body_tags"
internal const val ImportedSeriesKey = "imported_series"

private val insightJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

fun parseStoredBookInsight(metadataJson: String?): StoredBookInsight {
    val root = metadataJson.asInsightJsonObject()
    return StoredBookInsight(
        description = root?.get(ImportedDescriptionKey).asInsightString(),
        descriptionSource = BookInsightSource.fromStoredValue(
            root?.get(ImportedDescriptionSourceKey).asInsightString(),
        ),
        descriptionOrigin = root?.get(ImportedDescriptionOriginKey).asInsightString(),
        subjects = root
            ?.get(ImportedTagsKey)
            .asInsightString()
            .orEmpty()
            .split(',', '，')
            .map(String::trim)
            .filter { it.isNotBlank() },
        series = root?.get(ImportedSeriesKey).asInsightString(),
        bodyEvidence = parseCategoryEntries(root?.get(ImportedBodyTagsKey).asInsightString()),
        checked = root?.get(ImportedInsightVersionKey).asInsightString() == ImportedInsightVersion,
        autoCategorized = root?.get(ImportedAutoCategorizeKey).asInsightString() == "true",
    )
}

/**
 * True when a pass at the current version already ran for this book **with the same 自动分类
 * setting**: flipping the switch changes the answer, so every book is visited once more and can pick
 * the new behaviour up (or shed what the old one wrote).
 */
fun hasCurrentBookInsight(metadataJson: String?, autoCategorize: Boolean = false): Boolean {
    val stored = parseStoredBookInsight(metadataJson)
    return stored.checked && stored.autoCategorized == autoCategorize
}

/**
 * Rewrites the metadata JSON with [insight], leaving every other key — the user's annotations and
 * the import metadata — exactly as it was. A null [insight] still marks the pass as done.
 */
fun encodeStoredBookInsight(
    metadataJson: String?,
    insight: BookInsight?,
    checked: Boolean = true,
    autoCategorize: Boolean = false,
    bodyEvidence: List<BookCategoryEntry> = emptyList(),
): String {
    val existing = metadataJson.asInsightJsonObject()
    val insightKeys = setOf(
        ImportedInsightVersionKey,
        ImportedAutoCategorizeKey,
        ImportedDescriptionKey,
        ImportedDescriptionSourceKey,
        ImportedDescriptionOriginKey,
        ImportedTagsKey,
        ImportedBodyTagsKey,
        ImportedSeriesKey,
    )
    val merged = buildJsonObject {
        existing?.forEach { (key, value) ->
            if (key !in insightKeys) put(key, value)
        }
        if (checked) {
            put(ImportedInsightVersionKey, ImportedInsightVersion)
            put(ImportedAutoCategorizeKey, autoCategorize.toString())
        }
        insight?.description?.trim()?.takeIf { it.isNotEmpty() }?.let {
            put(ImportedDescriptionKey, it)
        }
        insight?.descriptionSource?.let { put(ImportedDescriptionSourceKey, it.storedValue) }
        insight?.descriptionOrigin?.trim()?.takeIf { it.isNotEmpty() }?.let {
            put(ImportedDescriptionOriginKey, it)
        }
        insight?.subjects
            ?.map(String::trim)
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?.let { put(ImportedTagsKey, it.joinToString(", ")) }
        insight?.series?.trim()?.takeIf { it.isNotEmpty() }?.let { put(ImportedSeriesKey, it) }
        bodyEvidence
            .map { entry -> BookCategoryEntry(entry.keyword.trim(), entry.category.trim()) }
            .filter { entry -> entry.keyword.isNotEmpty() && entry.category.isNotEmpty() }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { put(ImportedBodyTagsKey, encodeCategoryEntries(it)) }
    }
    return insightJson.encodeToString(JsonObject.serializer(), merged)
}

internal fun String?.asInsightJsonObject(): JsonObject? =
    runCatching { insightJson.parseToJsonElement(orEmpty()) as? JsonObject }.getOrNull()

internal fun JsonElement?.asInsightString(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
