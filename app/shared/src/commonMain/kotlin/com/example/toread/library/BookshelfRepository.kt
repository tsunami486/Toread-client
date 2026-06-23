package com.example.toread.library

import com.example.toread.database.ToreadDatabase
import com.example.toread.importing.BookCoverExtractor
import com.example.toread.progress.PlatformProgressClock
import com.example.toread.progress.ProgressClock
import com.example.toread.reader.BookLocator
import com.example.toread.reader.BookLocatorJson
import com.example.toread.sync.BookMetadataJson
import com.example.toread.sync.BookMetadataSyncPayload

class BookshelfRepository(
    private val database: ToreadDatabase,
    private val coverExtractor: BookCoverExtractor = BookCoverExtractor(),
    private val clock: ProgressClock = PlatformProgressClock(),
) {
    fun listBooks(): List<BookshelfBook> =
        database.toreadDatabaseQueries.selectAllBooks().executeAsList().map { row ->
            val progress = database.toreadDatabaseQueries
                .selectProgressByBookHash(row.book_hash)
                .executeAsOneOrNull()

            BookshelfBook(
                bookHash = row.book_hash,
                title = row.title,
                author = row.author,
                format = row.format,
                filePath = row.file_path,
                coverPath = row.cover_path,
                fileSize = row.file_size,
                progress = progress?.progress ?: 0.0,
                lastReadAt = row.last_read_at,
                syncStatus = combineSyncStatus(row.sync_status, progress?.sync_status),
                bookSyncStatus = row.sync_status,
                progressSyncStatus = progress?.sync_status,
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
            ?.let { runCatching { BookLocatorJson.decode(it) }.getOrNull() }

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
)
