package com.example.toread.importing

import com.example.toread.categories.BookCategoryEntry
import com.example.toread.categories.BookCategoryInput
import com.example.toread.categories.BodyCategoryEvidence
import com.example.toread.categories.autoCategoryTags
import com.example.toread.categories.bodyTextCategoryEvidence
import com.example.toread.categories.joinTagList
import com.example.toread.database.DatabaseDriverFactory
import com.example.toread.database.ToreadDatabase
import com.example.toread.logging.ToreadClientLog
import com.example.toread.metadata.BookInsight
import com.example.toread.metadata.BookLocalAnnotations
import com.example.toread.metadata.encodeBookLocalAnnotations
import com.example.toread.metadata.encodeStoredBookInsight
import com.example.toread.platform.BookFileStorage
import com.example.toread.platform.FileHasher
import com.example.toread.platform.PlatformFile
import com.example.toread.platform.PlatformFilePicker
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.sync.BookMetadataJson
import com.example.toread.sync.BookMetadataSyncPayload
import com.example.toread.ui.text.ToreadStrings
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * File extensions the importer accepts. The desktop drag-and-drop target filters dropped files with
 * the same set, and [validateImportFile] stays the authority once a file reaches the importer.
 */
internal val supportedBookExtensions: Set<String> = setOf("epub", "pdf", "txt")

class BookImportService(
    private val database: ToreadDatabase,
    private val filePicker: PlatformFilePicker = PlatformFilePicker(),
    private val fileHasher: FileHasher = FileHasher(),
    private val bookFileStorage: BookFileStorage = BookFileStorage(),
    private val metadataParser: BookMetadataParser = BasicBookMetadataParser(
        categorySettings = LocalSettingsRepository(database),
    ),
    private val coverExtractor: BookCoverExtractor = BookCoverExtractor(),
    private val clock: ImportClock = PlatformImportClock(),
) {
    suspend fun importSelectedFiles(): List<BookImportResult> =
        importFiles(filePicker.pickFiles(supportedBookExtensions.toList()))

    suspend fun importFiles(files: List<PlatformFile>): List<BookImportResult> =
        files.map { file ->
            // One unreadable item must not take the rest of a multi-file drop down with it.
            try {
                importFile(file)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                BookImportResult.Failed(
                    fileName = file.displayName,
                    reason = throwable.message ?: ToreadStrings.ImportFailedGeneric,
                )
            }
        }

    suspend fun importFile(file: PlatformFile): BookImportResult {
        val validation = validateImportFile(file)
        if (validation is ImportFileValidation.Invalid) {
            return BookImportResult.Failed(file.displayName, validation.reason)
        }

        val extension = (validation as ImportFileValidation.Valid).extension
        val bookHash = fileHasher.sha256(file)
        val existing = database.toreadDatabaseQueries.selectBookByHash(bookHash).executeAsOneOrNull()
        if (existing != null) {
            return BookImportResult.Duplicate(bookHash, existing.title)
        }

        val storedFile = bookFileStorage.copyToPrivateBookDirectory(file, bookHash, extension)
        val metadata = metadataParser.parse(file, extension, storedFile.path)
        val coverPath = coverExtractor.extractCover(
            bookFilePath = storedFile.path,
            bookHash = bookHash,
            format = extension,
        )
        val now = clock.nowIsoString()
        val syncPayload = BookMetadataJson.encode(
            BookMetadataSyncPayload(
                bookHash = bookHash,
                title = metadata.title,
                author = metadata.author,
                format = extension,
                metadataJson = BookMetadataJson.parseMetadataJson(metadata.metadataJson),
                updatedAt = now,
            ),
        )

        database.toreadDatabaseQueries.transaction {
            database.toreadDatabaseQueries.insertBook(
                id = "book_$bookHash",
                book_hash = bookHash,
                title = metadata.title,
                author = metadata.author,
                format = extension,
                file_path = storedFile.path,
                cover_path = coverPath,
                file_size = storedFile.sizeBytes,
                metadata_json = metadata.metadataJson,
                reading_status = "unread",
                last_read_at = null,
                sync_status = "pending",
                created_at = now,
                updated_at = now,
                archived_at = null,
            )
            database.toreadDatabaseQueries.insertSyncQueueItem(
                id = "book_${bookHash}_$now",
                entity_type = "book_metadata",
                entity_id = bookHash,
                action = "upsert",
                payload_json = syncPayload,
                sync_status = "pending",
                retry_count = 0,
                last_error = null,
                created_at = now,
                updated_at = now,
                next_retry_at = null,
            )
        }

        return BookImportResult.Imported(
            bookHash = bookHash,
            title = metadata.title,
            storedPath = storedFile.path,
        )
    }
}

fun createBookImportService(): BookImportService {
    return BookImportService(createToreadDatabase())
}

fun createToreadDatabase(): ToreadDatabase =
    ToreadDatabase(DatabaseDriverFactory().createDriver())

sealed interface BookImportResult {
    data class Imported(
        val bookHash: String,
        val title: String,
        val storedPath: String,
    ) : BookImportResult

    data class Duplicate(
        val bookHash: String,
        val title: String,
    ) : BookImportResult

    data class Failed(
        val fileName: String,
        val reason: String,
    ) : BookImportResult
}

private sealed interface ImportFileValidation {
    data class Valid(val extension: String) : ImportFileValidation
    data class Invalid(val reason: String) : ImportFileValidation
}

private fun validateImportFile(file: PlatformFile): ImportFileValidation {
    val extension = file.extension()
        ?: return ImportFileValidation.Invalid("缺少文件扩展名")

    if (extension !in supportedBookExtensions) {
        return ImportFileValidation.Invalid("暂不支持 .$extension 文件")
    }

    val mimeType = file.mimeType?.lowercase()?.substringBefore(";")?.trim()
    val isKnownCompatibleMime = mimeType == null ||
        mimeType.isBlank() ||
        mimeType == "application/octet-stream" ||
        isAcceptedMimeType(extension, mimeType)

    if (!isKnownCompatibleMime) {
        return ImportFileValidation.Invalid("文件 MIME 类型不匹配：$mimeType")
    }

    return ImportFileValidation.Valid(extension)
}

private fun PlatformFile.extension(): String? {
    val name = path?.substringAfterLast('\\')?.substringAfterLast('/')
        ?: displayName
    return name.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.lowercase()
}

private fun isAcceptedMimeType(extension: String, mimeType: String): Boolean = when (extension) {
    "epub" -> mimeType in setOf(
        "application/epub",
        "application/epub+zip",
        "application/x-epub+zip",
        "application/zip",
        "application/x-zip",
        "application/x-zip-compressed",
    )
    "pdf" -> mimeType == "application/pdf"
    "txt" -> mimeType == "text/plain" || mimeType.startsWith("text/")
    else -> false
}

data class BookMetadata(
    val title: String,
    val author: String?,
    val metadataJson: String,
)

interface BookMetadataParser {
    suspend fun parse(file: PlatformFile, extension: String, storedFilePath: String): BookMetadata
}

class BasicBookMetadataParser(
    private val epubMetadataExtractor: EpubMetadataExtractor = EpubMetadataExtractor(),
    private val bookInsightExtractor: BookInsightExtractor = BookInsightExtractor(),
    /** Reads 自动分类 and the reader's own category rules; null keeps everything suggestion-only. */
    private val categorySettings: LocalSettingsRepository? = null,
) : BookMetadataParser {
    override suspend fun parse(file: PlatformFile, extension: String, storedFilePath: String): BookMetadata {
        val fallbackTitle = file.displayName
            .substringBeforeLast('.', missingDelimiterValue = file.displayName)
            .ifBlank { "未命名" }
        val epubMetadata = epubMetadataExtractor.extract(storedFilePath, extension)
        val insight = bookInsightExtractor.extract(
            bookFilePath = storedFilePath,
            format = extension,
            fileName = file.displayName,
        )
        val title = epubMetadata?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val author = epubMetadata?.author?.takeIf { it.isNotBlank() }
            ?: insight?.authorHint?.takeIf { it.isNotBlank() }
        val autoCategorize = categorySettings?.autoCategorizeEnabled() == true
        val dictionary = categorySettings?.categoryDictionary().orEmpty()
        // Scored once and stored: with 自动分类 off it is what the book info page shows as
        // 正文多次出现「…」 suggestions, with it on it is the last-resort classification source.
        val bodyEvidence = bodyTextCategoryEvidence(text = insight?.bodySample, userEntries = dictionary)
        return BookMetadata(
            title = title,
            author = author,
            metadataJson = applyAutoCategories(
                metadataJson = buildLocalImportMetadataJson(
                    extension = extension,
                    epubMetadata = epubMetadata,
                    insight = insight,
                    autoCategorize = autoCategorize,
                    bodyEvidence = bodyEvidence.map { evidence ->
                        BookCategoryEntry(keyword = evidence.keyword, category = evidence.category)
                    },
                ),
                title = title,
                author = author,
                insight = insight,
                dictionary = dictionary,
                bodyEvidence = bodyEvidence,
            ),
        ).also { metadata ->
            ToreadClientLog.info(
                "import",
                "import.insight_extracted",
                "format" to extension,
                "source" to insight?.descriptionSource?.storedValue,
                "description_chars" to insight?.description?.length,
                "tags" to insight?.subjects?.size,
                "series" to (insight?.series != null),
                "author_hint" to (insight?.authorHint != null),
                "title" to (epubMetadata?.title != null),
            )
        }
    }

    /**
     * 自动分类 at import time, off by default: the categories the book's own file declares first, then
     * one category the title/series/blurb is clearly ahead on, and — for a book whose metadata says
     * nothing — one category the **sampled body text** is clearly ahead on. A fresh book has no
     * category yet, so there is nothing to overwrite.
     */
    private fun applyAutoCategories(
        metadataJson: String,
        title: String,
        author: String?,
        insight: BookInsight?,
        dictionary: List<BookCategoryEntry>,
        bodyEvidence: List<BodyCategoryEvidence>,
    ): String {
        val settings = categorySettings ?: return metadataJson
        if (!settings.autoCategorizeEnabled()) return metadataJson
        val autoTags = autoCategoryTags(
            input = BookCategoryInput(
                title = title,
                author = author,
                series = insight?.series,
                description = insight?.description,
            ),
            declaredSubjects = insight?.subjects.orEmpty(),
            userEntries = dictionary,
            memoryEntries = settings.categoryMemory(),
            bodyEvidence = bodyEvidence,
        )
        if (autoTags.isEmpty()) return metadataJson
        ToreadClientLog.info(
            "import",
            "import.auto_categorized",
            "format" to "local_import",
            "tags" to autoTags.size,
            "source" to if (insight?.subjects.isNullOrEmpty()) "lexicon" else "book",
        )
        return encodeBookLocalAnnotations(
            metadataJson = metadataJson,
            annotations = BookLocalAnnotations(tags = joinTagList(autoTags)),
        )
    }
}

private val importMetadataJson = Json { encodeDefaults = true }

internal fun buildLocalImportMetadataJson(extension: String, epubMetadata: EpubImportMetadata?): String =
    importMetadataJson.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("source", "local_import")
            put("format", extension)
            if (epubMetadata != null) {
                put("metadata_source", "epub_opf")
                epubMetadata.title?.takeIf { it.isNotBlank() }?.let { put("epub_title", it) }
                epubMetadata.author?.takeIf { it.isNotBlank() }?.let { put("epub_author", it) }
            }
        },
    )

/**
 * The metadata JSON a fresh import writes: the import metadata, the extracted candidate insight,
 * and — only for a brand-new book, which has nothing to lose — the description prefilled into 简介.
 *
 * [autoCategorize] is recorded in the marker as well, so the pass that runs when 自动分类 is switched
 * on later is not skipped for books imported while it was off. A later pass over an existing book
 * must not come through here either; it keeps whatever the reader already wrote.
 */
internal fun buildLocalImportMetadataJson(
    extension: String,
    epubMetadata: EpubImportMetadata?,
    insight: BookInsight?,
    autoCategorize: Boolean = false,
    bodyEvidence: List<BookCategoryEntry> = emptyList(),
): String {
    val base = buildLocalImportMetadataJson(extension, epubMetadata)
    val withInsight = encodeStoredBookInsight(
        metadataJson = base,
        insight = insight,
        autoCategorize = autoCategorize,
        bodyEvidence = bodyEvidence,
    )
    val prefill = insight.toPrefilledAnnotations() ?: return withInsight
    return encodeBookLocalAnnotations(metadataJson = withInsight, annotations = prefill)
}

/** The description a fresh import may show right away, or null when there is nothing to fill. */
private fun BookInsight?.toPrefilledAnnotations(): BookLocalAnnotations? {
    val description = this?.description?.trim().orEmpty()
    if (description.isEmpty()) return null
    return BookLocalAnnotations(notes = description)
}

expect class PlatformImportClock() : ImportClock

interface ImportClock {
    fun nowIsoString(): String
}
