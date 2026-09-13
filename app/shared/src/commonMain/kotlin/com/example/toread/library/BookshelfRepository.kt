package com.example.toread.library

import com.example.toread.database.ToreadDatabase
import com.example.toread.importing.BookCoverExtractor
import com.example.toread.importing.EpubMetadataExtractor
import com.example.toread.importing.buildLocalImportMetadataJson
import com.example.toread.platform.BookFileStorage
import com.example.toread.progress.PlatformProgressClock
import com.example.toread.progress.ProgressClock
import com.example.toread.reader.BookLocator
import com.example.toread.reader.BookLocatorJson
import com.example.toread.sync.BookMetadataJson
import com.example.toread.sync.BookMetadataSyncPayload

class BookshelfRepository(
    private val database: ToreadDatabase,
    private val coverExtractor: BookCoverExtractor = BookCoverExtractor(),
    private val epubMetadataExtractor: EpubMetadataExtractor = EpubMetadataExtractor(),
    private val bookFileStorage: BookFileStorage = BookFileStorage(),
    private val clock: ProgressClock = PlatformProgressClock(),
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
    }
}

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
)
