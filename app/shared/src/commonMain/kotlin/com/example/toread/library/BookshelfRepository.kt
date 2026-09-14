package com.example.toread.library

import com.example.toread.categories.BookCategoryInput
import com.example.toread.categories.autoCategoryTags
import com.example.toread.categories.joinTagList
import com.example.toread.database.ToreadDatabase
import com.example.toread.importing.BookCoverExtractor
import com.example.toread.importing.BookInsightExtractor
import com.example.toread.importing.EpubMetadataExtractor
import com.example.toread.importing.buildLocalImportMetadataJson
import com.example.toread.logging.ToreadClientLog
import com.example.toread.metadata.BookInsight
import com.example.toread.metadata.BookLocalAnnotations
import com.example.toread.metadata.StoredBookInsight
import com.example.toread.metadata.encodeBookLocalAnnotations
import com.example.toread.metadata.encodeStoredBookInsight
import com.example.toread.metadata.hasCurrentBookInsight
import com.example.toread.metadata.parseBookLocalAnnotations
import com.example.toread.metadata.parseStoredBookInsight
import com.example.toread.platform.BookFileStorage
import com.example.toread.progress.PlatformProgressClock
import com.example.toread.progress.ProgressClock
import com.example.toread.reader.BookLocator
import com.example.toread.reader.BookLocatorJson
import com.example.toread.sync.BookMetadataJson
import com.example.toread.sync.BookMetadataSyncPayload
import com.example.toread.settings.LocalSettingsRepository

class BookshelfRepository(
    private val database: ToreadDatabase,
    private val coverExtractor: BookCoverExtractor = BookCoverExtractor(),
    private val epubMetadataExtractor: EpubMetadataExtractor = EpubMetadataExtractor(),
    private val bookFileStorage: BookFileStorage = BookFileStorage(),
    private val bookInsightExtractor: BookInsightExtractor = BookInsightExtractor(),
    private val clock: ProgressClock = PlatformProgressClock(),
    private val settingsRepository: LocalSettingsRepository = LocalSettingsRepository(database),
) {
    fun listBooks(): List<BookshelfBook> =
        database.toreadDatabaseQueries.selectAllBooks().executeAsList().map { row ->
            val progress = database.toreadDatabaseQueries
                .selectProgressByBookHash(row.book_hash)
                .executeAsOneOrNull()
            val annotations = parseBookLocalAnnotations(row.metadata_json)

            BookshelfBook(
                bookHash = row.book_hash,
                title = row.title,
                author = row.author,
                format = row.format,
                filePath = row.file_path,
                coverPath = row.cover_path,
                fileSize = row.file_size,
                progress = progress?.progress ?: 0.0,
                locator = BookLocatorJson.decodeOrNull(progress?.locator_json),
                lastReadAt = row.last_read_at,
                syncStatus = combineSyncStatus(row.sync_status, progress?.sync_status),
                bookSyncStatus = row.sync_status,
                progressSyncStatus = progress?.sync_status,
                localTags = annotations.tags,
                localNotes = annotations.notes,
                importedInsight = parseStoredBookInsight(row.metadata_json),
            )
        }

    suspend fun refreshMissingEpubCovers(): Int {
        val books = database.toreadDatabaseQueries.selectAllBooks().executeAsList()
        var updated = 0
        books
            .filter { it.format.equals("epub", ignoreCase = true) && it.cover_path.isNullOrBlank() }
            .forEach { book ->
                val coverPath = coverExtractor.extractCover(
                    bookFilePath = book.file_path,
                    bookHash = book.book_hash,
                    format = book.format,
                )
                if (!coverPath.isNullOrBlank()) {
                    database.toreadDatabaseQueries.updateBookCoverPath(
                        cover_path = coverPath,
                        updated_at = clock.nowIsoString(),
                        book_hash = book.book_hash,
                    )
                    updated++
                }
            }
        return updated
    }

    suspend fun refreshMissingEpubMetadata(): Int {
        val books = database.toreadDatabaseQueries.selectAllBooks().executeAsList()
        var updated = 0
        books
            .filter { it.format.equals("epub", ignoreCase = true) && it.author.isNullOrBlank() }
            .forEach { book ->
                val metadata = epubMetadataExtractor.extract(book.file_path, book.format) ?: return@forEach
                val title = metadata.title?.takeIf { it.isNotBlank() } ?: book.title
                val author = metadata.author?.takeIf { it.isNotBlank() } ?: return@forEach
                val now = clock.nowIsoString()
                database.toreadDatabaseQueries.updateBookMetadata(
                    title = title,
                    author = author,
                    // Re-extracting metadata must not drop the user's own tags and note.
                    metadata_json = encodeBookLocalAnnotations(
                        metadataJson = buildLocalImportMetadataJson(book.format, metadata),
                        annotations = parseBookLocalAnnotations(book.metadata_json),
                    ),
                    sync_status = "pending",
                    updated_at = now,
                    book_hash = book.book_hash,
                )
                updated++
            }
        return updated
    }

    /**
     * Runs one insight extraction pass for [bookHash] — the automatic path the book info page uses
     * when it opens a book that has not been visited yet.
     *
     * Besides the `imported_*` keys it fills 简介 with the description it found, and only when the
     * reader has not written anything there. 分类/标签 is only touched when 自动分类 is on and the
     * book still has no category of its own.
     */
    suspend fun extractBookInsight(bookHash: String): BookInsightRefresh? {
        val book = database.toreadDatabaseQueries
            .selectBookByHash(bookHash)
            .executeAsOneOrNull()
            ?: return null
        // The stored copy is named after the book hash, so only the file itself can be consulted.
        val insight = bookInsightExtractor.extract(
            bookFilePath = book.file_path,
            format = book.format,
            fileName = null,
        )
        val metadataJson = storeBookInsight(
            bookHash = book.book_hash,
            title = book.title,
            author = book.author,
            format = book.format,
            metadataJson = book.metadata_json,
            insight = insight,
        )
        ToreadClientLog.info(
            "import",
            "import.insight_extracted",
            "format" to book.format,
            "source" to insight?.descriptionSource?.storedValue,
            "description_chars" to insight?.description?.length,
            "tags" to insight?.subjects?.size,
            "bookHashPrefix" to book.book_hash.take(8),
        )
        return BookInsightRefresh(
            insight = parseStoredBookInsight(metadataJson),
            notes = parseBookLocalAnnotations(metadataJson).notes,
            tags = parseBookLocalAnnotations(metadataJson).tags,
        )
    }

    /**
     * Visits books imported before the extraction existed — or before it learned a new source — and
     * stores what their own files say. Bounded per call so a manual refresh over a large shelf stays
     * quick; a book is only ever visited once per extractor version.
     */
    suspend fun backfillMissingBookInsights(limit: Int = DefaultInsightBackfillLimit): Int {
        if (limit <= 0) return 0
        val books = database.toreadDatabaseQueries.selectAllBooks().executeAsList()
        val autoCategorize = settingsRepository.autoCategorizeEnabled()
        var updated = 0
        for (book in books) {
            if (updated >= limit) break
            if (hasCurrentBookInsight(book.metadata_json, autoCategorize)) continue
            val insight = bookInsightExtractor.extract(
                bookFilePath = book.file_path,
                format = book.format,
                fileName = null,
            )
            storeBookInsight(
                bookHash = book.book_hash,
                title = book.title,
                author = book.author,
                format = book.format,
                metadataJson = book.metadata_json,
                insight = insight,
            )
            updated++
            ToreadClientLog.info(
                "import",
                "import.insight_backfilled",
                "format" to book.format,
                "source" to insight?.descriptionSource?.storedValue,
                "description_chars" to insight?.description?.length,
                "tags" to insight?.subjects?.size,
                "bookHashPrefix" to book.book_hash.take(8),
            )
        }
        return updated
    }

    /**
     * 自动分类: writes 分类/标签 for a book that has none, using the shared rule in
     * [autoCategoryTags] — the categories the book itself declares first, then one category the
     * lexicon is clearly ahead on. Returns the input unchanged when there is nothing worth writing.
     */
    private fun autoCategorizedMetadataJson(
        metadataJson: String,
        title: String,
        author: String?,
        insight: BookInsight?,
    ): String {
        val autoTags = autoCategoryTags(
            input = BookCategoryInput(
                title = title,
                author = author,
                series = insight?.series,
                description = insight?.description,
            ),
            declaredSubjects = insight?.subjects.orEmpty(),
            userEntries = settingsRepository.categoryDictionary(),
            memoryEntries = settingsRepository.categoryMemory(),
        )
        if (autoTags.isEmpty()) return metadataJson
        ToreadClientLog.info(
            "import",
            "import.auto_categorized",
            "tags" to autoTags.size,
            "source" to if (insight?.subjects.isNullOrEmpty()) "lexicon" else "book",
        )
        return encodeBookLocalAnnotations(
            metadataJson = metadataJson,
            annotations = parseBookLocalAnnotations(metadataJson).copy(tags = joinTagList(autoTags)),
        )
    }

    /**
     * Writes [insight] into the book's metadata JSON and queues the metadata upsert, exactly like an
     * edit does, so the extracted description reaches the other devices through the normal sync path.
     *
     * The description is also filled into 简介, but only while that field still belongs to the
     * extractor: it is written when it is empty, and refreshed when it still holds exactly the
     * description a previous pass wrote — so a better extractor can correct its own text — while a
     * note the reader typed is never touched. 分类/标签 stays the reader's: it is only written by
     * 自动分类, and only while the book has no category at all.
     */
    private fun storeBookInsight(
        bookHash: String,
        title: String,
        author: String?,
        format: String,
        metadataJson: String,
        insight: BookInsight?,
    ): String {
        val previousDescription = parseStoredBookInsight(metadataJson).description
        val autoCategorize = settingsRepository.autoCategorizeEnabled()
        val withInsight = encodeStoredBookInsight(
            metadataJson = metadataJson,
            insight = insight,
            autoCategorize = autoCategorize,
        )
        val description = insight?.description?.trim().orEmpty()
        val annotations = parseBookLocalAnnotations(withInsight)
        val notesAreOurs = annotations.notes.isBlank() ||
            (previousDescription != null && annotations.notes == previousDescription)
        val withNotes = if (description.isNotEmpty() && notesAreOurs) {
            encodeBookLocalAnnotations(
                metadataJson = withInsight,
                annotations = annotations.copy(notes = description),
            )
        } else {
            withInsight
        }
        val updatedMetadataJson = if (autoCategorize && annotations.tags.isBlank()) {
            autoCategorizedMetadataJson(withNotes, title = title, author = author, insight = insight)
        } else {
            withNotes
        }
        val now = clock.nowIsoString()
        database.toreadDatabaseQueries.transaction {
            database.toreadDatabaseQueries.updateBookMetadata(
                title = title,
                author = author,
                metadata_json = updatedMetadataJson,
                sync_status = "pending",
                updated_at = now,
                book_hash = bookHash,
            )
            database.toreadDatabaseQueries.markSyncQueueItemsSupersededForEntity(
                updated_at = now,
                entity_type = BookMetadataEntityType,
                entity_id = bookHash,
            )
            database.toreadDatabaseQueries.insertSyncQueueItem(
                id = "book_${bookHash}_${now}_insight",
                entity_type = BookMetadataEntityType,
                entity_id = bookHash,
                action = "upsert",
                payload_json = BookMetadataJson.encode(
                    BookMetadataSyncPayload(
                        bookHash = bookHash,
                        title = title,
                        author = author,
                        format = format,
                        metadataJson = BookMetadataJson.parseMetadataJson(updatedMetadataJson),
                        updatedAt = now,
                    ),
                ),
                sync_status = "pending",
                retry_count = 0,
                last_error = null,
                created_at = now,
                updated_at = now,
                next_retry_at = null,
            )
        }
        return updatedMetadataJson
    }

    fun ensurePendingBookMetadataQueued(): Int {
        val books = database.toreadDatabaseQueries.selectAllBooks().executeAsList()
        val now = clock.nowIsoString()
        var queued = 0

        database.toreadDatabaseQueries.transaction {
            books
                .filter { it.sync_status.normalizedSyncStatus() != "synced" }
                .forEach { book ->
                    val activeQueue = database.toreadDatabaseQueries
                        .selectActiveSyncQueueItemsByEntity(BookMetadataEntityType, book.book_hash)
                        .executeAsList()
                    if (activeQueue.isNotEmpty()) return@forEach

                    val payload = BookMetadataJson.encode(
                        BookMetadataSyncPayload(
                            bookHash = book.book_hash,
                            title = book.title,
                            author = book.author,
                            format = book.format,
                            metadataJson = BookMetadataJson.parseMetadataJson(book.metadata_json),
                            updatedAt = book.updated_at,
                        ),
                    )
                    database.toreadDatabaseQueries.insertSyncQueueItem(
                        id = "book_${book.book_hash}_${now}_repair_$queued",
                        entity_type = BookMetadataEntityType,
                        entity_id = book.book_hash,
                        action = "upsert",
                        payload_json = payload,
                        sync_status = "pending",
                        retry_count = 0,
                        last_error = null,
                        created_at = now,
                        updated_at = now,
                        next_retry_at = null,
                    )
                    queued++
                }
        }
        return queued
    }

    fun progressLocator(bookHash: String): BookLocator? =
        database.toreadDatabaseQueries
            .selectProgressByBookHash(bookHash)
            .executeAsOneOrNull()
            ?.locator_json
            .let(BookLocatorJson::decodeOrNull)

    /**
     * Renames a book locally and queues the metadata upsert, exactly like an import does, so an edited
     * title or author reaches the other devices instead of drifting away from them.
     */
    suspend fun updateBookInfo(
        bookHash: String,
        title: String,
        author: String?,
        annotations: BookLocalAnnotations? = null,
    ): Boolean {
        val book = database.toreadDatabaseQueries
            .selectBookByHash(bookHash)
            .executeAsOneOrNull()
            ?: return false
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return false
        val cleanAuthor = author?.trim()?.takeIf { it.isNotBlank() }
        val metadataJson = annotations
            ?.let { encodeBookLocalAnnotations(book.metadata_json, it) }
            ?: book.metadata_json
        val now = clock.nowIsoString()

        database.toreadDatabaseQueries.transaction {
            database.toreadDatabaseQueries.updateBookMetadata(
                title = cleanTitle,
                author = cleanAuthor,
                metadata_json = metadataJson,
                sync_status = "pending",
                updated_at = now,
                book_hash = bookHash,
            )
            database.toreadDatabaseQueries.markSyncQueueItemsSupersededForEntity(
                updated_at = now,
                entity_type = BookMetadataEntityType,
                entity_id = bookHash,
            )
            database.toreadDatabaseQueries.insertSyncQueueItem(
                id = "book_${bookHash}_${now}_edit",
                entity_type = BookMetadataEntityType,
                entity_id = bookHash,
                action = "upsert",
                payload_json = BookMetadataJson.encode(
                    BookMetadataSyncPayload(
                        bookHash = bookHash,
                        title = cleanTitle,
                        author = cleanAuthor,
                        format = book.format,
                        metadataJson = BookMetadataJson.parseMetadataJson(metadataJson),
                        updatedAt = now,
                    ),
                ),
                sync_status = "pending",
                retry_count = 0,
                last_error = null,
                created_at = now,
                updated_at = now,
                next_retry_at = null,
            )
        }
        return true
    }

    /**
     * Points the book at a user-chosen cover, or back at the generated one when [coverPath] is null.
     * Covers stay local and are never uploaded, so nothing is queued here.
     */
    fun updateBookCoverPath(bookHash: String, coverPath: String?): Boolean {
        val book = database.toreadDatabaseQueries
            .selectBookByHash(bookHash)
            .executeAsOneOrNull()
            ?: return false
        database.toreadDatabaseQueries.updateBookCoverPath(
            cover_path = coverPath,
            updated_at = clock.nowIsoString(),
            book_hash = bookHash,
        )
        return true
    }

    /**
     * Removes a book from this device only: its rows, its pending sync items and its private directory.
     * The server keeps its copy of the metadata, so re-importing the same file starts a fresh sync.
     */
    suspend fun deleteBookLocally(bookHash: String): Boolean {
        val book = database.toreadDatabaseQueries
            .selectBookByHash(bookHash)
            .executeAsOneOrNull()
            ?: return false

        database.toreadDatabaseQueries.transaction {
            database.toreadDatabaseQueries.deleteProgressByBookHash(bookHash)
            database.toreadDatabaseQueries.deleteBookmarksByBookHash(bookHash)
            database.toreadDatabaseQueries.deleteNotesByBookHash(bookHash)
            database.toreadDatabaseQueries.deleteSyncQueueItemsByEntity(bookHash)
            database.toreadDatabaseQueries.deleteBookByHash(bookHash)
        }
        bookFileStorage.deleteBookDirectory(bookHash = bookHash, bookFilePath = book.file_path)
        return true
    }

    private fun combineSyncStatus(bookSyncStatus: String, progressSyncStatus: String?): String =
        when {
            bookSyncStatus.normalizedSyncStatus() == "failed" ||
                progressSyncStatus.normalizedSyncStatus() == "failed" -> "failed"
            bookSyncStatus.normalizedSyncStatus() == "pending" ||
                progressSyncStatus.normalizedSyncStatus() == "pending" -> "pending"
            else -> "synced"
        }

    private fun String?.normalizedSyncStatus(): String =
        orEmpty().trim().lowercase()

    private companion object {
        const val BookMetadataEntityType = "book_metadata"

        /** How many books one manual refresh may open for the insight backfill. */
        const val DefaultInsightBackfillLimit = 8
    }
}

/**
 * What one automatic extraction pass left in the book's record: the stored candidate insight and the
 * 简介/分类 values that are now persisted, so the book info page can show them without reloading.
 */
data class BookInsightRefresh(
    val insight: StoredBookInsight,
    val notes: String,
    val tags: String,
)

data class BookshelfBook(
    val bookHash: String,
    val title: String,
    val author: String?,
    val format: String,
    val filePath: String,
    val coverPath: String?,
    val fileSize: Long,
    val progress: Double,
    val lastReadAt: String?,
    val syncStatus: String,
    val bookSyncStatus: String,
    val progressSyncStatus: String?,
    val locator: BookLocator? = null,
    /** The user's own tags and note, edited on the book info page; kept in the local metadata JSON. */
    val localTags: String = "",
    val localNotes: String = "",
    /** The candidate description/tags the importer read out of the book file itself. */
    val importedInsight: StoredBookInsight = StoredBookInsight(),
)
