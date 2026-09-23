package com.example.toread.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.toread.database.ToreadDatabase
import com.example.toread.library.BookshelfRepository
import com.example.toread.progress.ProgressClock
import com.example.toread.progress.ReadingProgressJson
import com.example.toread.progress.ReadingProgressSyncPayload
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.progress.SaveProgressResult
import com.example.toread.reader.TxtLocator
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SyncQueueProcessorTest {
    @Test
    fun successfulReadingProgressUploadMarksQueueAndProgressSynced() = runBlocking {
        val database = testDatabase()
        insertBook(database, "hash_sync_success")
        val progressRepository = ReadingProgressRepository(
            database,
            FixedSyncClock("2026-06-09T01:00:00Z"),
        )
        val saved = progressRepository.saveLocalProgress(
            bookHash = "hash_sync_success",
            format = "txt",
            locator = TxtLocator(charOffset = 128, globalProgress = 0.5),
            deviceId = "desktop",
        )
        assertIs<SaveProgressResult.Saved>(saved)

        val serverUpdatedAt = "2026-06-09T01:01:00.123Z"
        val uploader = RecordingUploader(uploadedProgress("hash_sync_success", serverUpdatedAt, 0.5))
        val processor = SyncQueueProcessor(database, uploader, FixedSyncClock("2026-06-09T01:01:00Z"))

        val summary = processor.flushPending()

        assertEquals(1, summary.scanned)
        assertEquals(1, summary.uploaded)
        assertEquals("hash_sync_success", uploader.uploadedPayloads.single().bookHash)
        val queue = database.toreadDatabaseQueries
            .selectSyncQueueItemById("progress_hash_sync_success_2026-06-09T01:00:00Z")
            .executeAsOne()
        assertEquals("synced", queue.sync_status)
        val progress = database.toreadDatabaseQueries
            .selectProgressByBookHash("hash_sync_success")
            .executeAsOne()
        assertEquals("synced", progress.sync_status)
        assertEquals(serverUpdatedAt, progress.updated_at)
    }

    @Test
    fun failedReadingProgressUploadKeepsRetryStateAndCanRetry() = runBlocking {
        val database = testDatabase()
        insertBook(database, "hash_sync_failed")
        val progressRepository = ReadingProgressRepository(
            database,
            FixedSyncClock("2026-06-09T02:00:00Z"),
        )
        progressRepository.saveLocalProgress(
            bookHash = "hash_sync_failed",
            format = "txt",
            locator = TxtLocator(charOffset = 256, globalProgress = 0.8),
            deviceId = "android",
        )

        val uploader = RecordingUploader(SyncUploadResult.Failed("network unavailable"))
        val processor = SyncQueueProcessor(database, uploader, FixedSyncClock("2026-06-09T02:01:00Z"))

        val failedSummary = processor.flushPending()

        assertEquals(1, failedSummary.failed)
        val failedQueue = database.toreadDatabaseQueries
            .selectSyncQueueItemById("progress_hash_sync_failed_2026-06-09T02:00:00Z")
            .executeAsOne()
        assertEquals("failed", failedQueue.sync_status)
        assertEquals(1, failedQueue.retry_count)
        assertEquals("network unavailable", failedQueue.last_error)
        assertEquals("failed", database.toreadDatabaseQueries
            .selectProgressByBookHash("hash_sync_failed")
            .executeAsOne()
            .sync_status)

        uploader.nextResult = uploadedProgress("hash_sync_failed", "2026-06-09T02:02:00.456Z", 0.8)
        val retrySummary = processor.flushPending()

        assertEquals(1, retrySummary.uploaded)
        val retriedQueue = database.toreadDatabaseQueries
            .selectSyncQueueItemById("progress_hash_sync_failed_2026-06-09T02:00:00Z")
            .executeAsOne()
        assertEquals("synced", retriedQueue.sync_status)
        assertEquals(1, retriedQueue.retry_count)
        assertEquals("synced", database.toreadDatabaseQueries
            .selectProgressByBookHash("hash_sync_failed")
            .executeAsOne()
            .sync_status)
    }

    @Test
    fun unauthorizedUploadLeavesTheItemPendingAndAsksForASessionRefresh() = runBlocking {
        val database = testDatabase()
        insertBook(database, "hash_sync_unauthorized")
        val progressRepository = ReadingProgressRepository(
            database,
            FixedSyncClock("2026-06-09T02:30:00Z"),
        )
        progressRepository.saveLocalProgress(
            bookHash = "hash_sync_unauthorized",
            format = "txt",
            locator = TxtLocator(charOffset = 64, globalProgress = 0.2),
            deviceId = "android",
        )

        val uploader = RecordingUploader(SyncUploadResult.Unauthorized("token expired"))
        val processor = SyncQueueProcessor(database, uploader, FixedSyncClock("2026-06-09T02:31:00Z"))

        val summary = processor.flushPending()

        // A 401 is the token's problem, not the payload's: the item must stay pending (not failed, not
        // "synced") so the next flush after the session refresh uploads it, and the caller must be told.
        assertEquals(1, summary.unauthorized)
        assertEquals(0, summary.failed)
        assertEquals(0, summary.uploaded)
        val queue = database.toreadDatabaseQueries
            .selectSyncQueueItemById("progress_hash_sync_unauthorized_2026-06-09T02:30:00Z")
            .executeAsOne()
        assertEquals("pending", queue.sync_status)
        assertEquals(0, queue.retry_count)
    }

    @Test
    fun flushPendingUploadsOnlyLatestReadingProgressForEachBook() = runBlocking {
        val database = testDatabase()
        insertBook(database, "hash_sync_coalesce")
        insertProgressQueueItem(
            database = database,
            id = "progress_hash_sync_coalesce_2026-06-09T03:00:00Z",
            bookHash = "hash_sync_coalesce",
            updatedAt = "2026-06-09T03:00:00Z",
            charOffset = 100,
            progress = 0.1,
        )
        insertProgressQueueItem(
            database = database,
            id = "progress_hash_sync_coalesce_2026-06-09T03:00:01Z",
            bookHash = "hash_sync_coalesce",
            updatedAt = "2026-06-09T03:00:01Z",
            charOffset = 200,
            progress = 0.2,
        )
        insertProgressQueueItem(
            database = database,
            id = "progress_hash_sync_coalesce_2026-06-09T03:00:02Z",
            bookHash = "hash_sync_coalesce",
            updatedAt = "2026-06-09T03:00:02Z",
            charOffset = 300,
            progress = 0.3,
        )

        val uploader = RecordingUploader(uploadedProgress("hash_sync_coalesce", "2026-06-09T03:01:00Z", 0.3))
        val processor = SyncQueueProcessor(database, uploader, FixedSyncClock("2026-06-09T03:01:00Z"))

        val summary = processor.flushPending()

        assertEquals(3, summary.scanned)
        assertEquals(1, summary.uploaded)
        assertEquals("2026-06-09T03:00:02Z", uploader.uploadedPayloads.single().updatedAt)
        assertEquals("superseded", database.toreadDatabaseQueries
            .selectSyncQueueItemById("progress_hash_sync_coalesce_2026-06-09T03:00:00Z")
            .executeAsOne()
            .sync_status)
        assertEquals("superseded", database.toreadDatabaseQueries
            .selectSyncQueueItemById("progress_hash_sync_coalesce_2026-06-09T03:00:01Z")
            .executeAsOne()
            .sync_status)
        assertEquals("synced", database.toreadDatabaseQueries
            .selectSyncQueueItemById("progress_hash_sync_coalesce_2026-06-09T03:00:02Z")
            .executeAsOne()
            .sync_status)
    }

    @Test
    fun successfulBookMetadataUploadMarksQueueAndBookSynced() = runBlocking {
        val database = testDatabase()
        insertBook(database, "hash_book_sync", syncStatus = "pending")
        insertBookQueueItem(
            database = database,
            id = "book_hash_book_sync_2026-06-09T04:00:00Z",
            bookHash = "hash_book_sync",
            updatedAt = "2026-06-09T04:00:00Z",
        )
        val bookUploader = RecordingBookUploader(
            BookMetadataUploadResult.Uploaded(
                RemoteBookMetadata(
                    bookHash = "hash_book_sync",
                    title = "Book hash_book_sync",
                    author = null,
                    format = "TXT",
                    metadataJson = JsonObject(emptyMap()),
                    updatedAt = "2026-06-09T04:01:00Z",
                ),
            ),
        )
        val processor = SyncQueueProcessor(
            database = database,
            progressUploader = RecordingUploader(SyncUploadResult.Failed("progress not used")),
            clock = FixedSyncClock("2026-06-09T04:01:00Z"),
            bookMetadataUploader = bookUploader,
        )

        val summary = processor.flushPending()

        assertEquals(1, summary.scanned)
        assertEquals(1, summary.uploaded)
        assertEquals("hash_book_sync", bookUploader.uploadedPayloads.single().bookHash)
        assertEquals("synced", database.toreadDatabaseQueries
            .selectSyncQueueItemById("book_hash_book_sync_2026-06-09T04:00:00Z")
            .executeAsOne()
            .sync_status)
        val book = database.toreadDatabaseQueries
            .selectBookByHash("hash_book_sync")
            .executeAsOne()
        assertEquals("synced", book.sync_status)
        assertEquals("2026-06-09T04:01:00Z", book.updated_at)
    }

    @Test
    fun refreshRepairQueuesLegacyPendingBookMetadata() = runBlocking {
        val database = testDatabase()
        insertBook(database, "hash_legacy_pending", syncStatus = "pending")
        val repository = BookshelfRepository(
            database = database,
            clock = FixedSyncClock("2026-06-09T05:00:00Z"),
        )

        val repaired = repository.ensurePendingBookMetadataQueued()

        assertEquals(1, repaired)
        val activeQueue = database.toreadDatabaseQueries
            .selectActiveSyncQueueItemsByEntity("book_metadata", "hash_legacy_pending")
            .executeAsList()
        assertEquals(1, activeQueue.size)

        val bookUploader = RecordingBookUploader(
            BookMetadataUploadResult.Uploaded(
                RemoteBookMetadata(
                    bookHash = "hash_legacy_pending",
                    title = "Book hash_legacy_pending",
                    author = null,
                    format = "TXT",
                    metadataJson = JsonObject(emptyMap()),
                    updatedAt = "2026-06-09T05:01:00Z",
                ),
            ),
        )
        val processor = SyncQueueProcessor(
            database = database,
            progressUploader = RecordingUploader(SyncUploadResult.Failed("progress not used")),
            clock = FixedSyncClock("2026-06-09T05:01:00Z"),
            bookMetadataUploader = bookUploader,
        )

        val summary = processor.flushPending(limit = Int.MAX_VALUE)

        assertEquals(1, summary.uploaded)
        assertEquals("synced", repository.listBooks().single().syncStatus)
    }

    @Test
    fun concurrentFlushesNeverUploadTheSameQueueItemAtTheSameTime() = runBlocking {
        val database = testDatabase()
        insertBook(database, "hash_single_flight")
        val progressRepository = ReadingProgressRepository(
            database,
            FixedSyncClock("2026-06-09T06:00:00Z"),
        )
        progressRepository.saveLocalProgress(
            bookHash = "hash_single_flight",
            format = "txt",
            locator = TxtLocator(charOffset = 600, globalProgress = 0.6),
            deviceId = "desktop",
        )
        val uploader = GatedUploader("hash_single_flight")
        val processor = SyncQueueProcessor(
            database,
            uploader,
            FixedSyncClock("2026-06-09T06:01:00Z"),
        )

        val first = async { processor.flushPending() }
        uploader.firstStarted.await()
        val second = async { processor.flushPending() }
        assertEquals(1, uploader.calls.get())

        uploader.releaseFirst.complete(Unit)
        assertEquals(1, first.await().uploaded)
        assertEquals(0, second.await().uploaded)
        assertEquals(1, uploader.calls.get())
        assertEquals(1, uploader.maxConcurrentCalls.get())
    }

    @Test
    fun automaticFlushPrioritizesNewestProgressAndStopsAfterFailure() = runBlocking {
        val database = testDatabase()
        insertBook(database, "hash_old_failed")
        insertBook(database, "hash_latest_failed")
        insertProgressQueueItem(
            database = database,
            id = "progress_hash_old_failed_2026-06-09T07:00:00Z",
            bookHash = "hash_old_failed",
            updatedAt = "2026-06-09T07:00:00Z",
            charOffset = 100,
            progress = 0.1,
        )
        insertProgressQueueItem(
            database = database,
            id = "progress_hash_latest_failed_2026-06-09T07:00:01Z",
            bookHash = "hash_latest_failed",
            updatedAt = "2026-06-09T07:00:01Z",
            charOffset = 900,
            progress = 0.9,
        )
        val uploader = RecordingUploader(SyncUploadResult.Failed("offline"))
        val processor = SyncQueueProcessor(
            database,
            uploader,
            FixedSyncClock("2026-06-09T07:01:00Z"),
        )

        val summary = processor.flushPending(stopOnFailure = true)

        assertEquals(1, summary.failed)
        assertEquals(1, uploader.uploadedPayloads.size)
        assertEquals("hash_latest_failed", uploader.uploadedPayloads.single().bookHash)
        assertEquals("pending", database.toreadDatabaseQueries
            .selectSyncQueueItemById("progress_hash_old_failed_2026-06-09T07:00:00Z")
            .executeAsOne()
            .sync_status)
    }

    private fun testDatabase(): ToreadDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToreadDatabase.Schema.create(driver)
        return ToreadDatabase(driver)
    }

    private fun insertBook(database: ToreadDatabase, bookHash: String, syncStatus: String = "synced") {
        database.toreadDatabaseQueries.insertBook(
            id = "book_$bookHash",
            book_hash = bookHash,
            title = "Book $bookHash",
            author = null,
            format = "txt",
            file_path = "/tmp/$bookHash.txt",
            cover_path = null,
            file_size = 100,
            metadata_json = "{}",
            reading_status = "unread",
            last_read_at = null,
            sync_status = syncStatus,
            created_at = "2026-06-09T00:00:00Z",
            updated_at = "2026-06-09T00:00:00Z",
            archived_at = null,
        )
    }

    private fun insertBookQueueItem(
        database: ToreadDatabase,
        id: String,
        bookHash: String,
        updatedAt: String,
    ) {
        val payload = BookMetadataJson.encode(
            BookMetadataSyncPayload(
                bookHash = bookHash,
                title = "Book $bookHash",
                author = null,
                format = "txt",
                metadataJson = JsonObject(emptyMap()),
                updatedAt = updatedAt,
            ),
        )
        database.toreadDatabaseQueries.insertSyncQueueItem(
            id = id,
            entity_type = "book_metadata",
            entity_id = bookHash,
            action = "upsert",
            payload_json = payload,
            sync_status = "pending",
            retry_count = 0,
            last_error = null,
            created_at = updatedAt,
            updated_at = updatedAt,
            next_retry_at = null,
        )
    }

    private fun insertProgressQueueItem(
        database: ToreadDatabase,
        id: String,
        bookHash: String,
        updatedAt: String,
        charOffset: Long,
        progress: Double,
    ) {
        val payload = ReadingProgressJson.encode(
            ReadingProgressSyncPayload(
                bookHash = bookHash,
                format = "txt",
                locator = TxtLocator(charOffset = charOffset, globalProgress = progress),
                progress = progress,
                deviceId = "desktop",
                updatedAt = updatedAt,
            ),
        )
        database.toreadDatabaseQueries.insertSyncQueueItem(
            id = id,
            entity_type = "reading_progress",
            entity_id = bookHash,
            action = "upsert",
            payload_json = payload,
            sync_status = "pending",
            retry_count = 0,
            last_error = null,
            created_at = updatedAt,
            updated_at = updatedAt,
            next_retry_at = null,
        )
    }
}

private fun uploadedProgress(
    bookHash: String,
    updatedAt: String,
    progress: Double,
): SyncUploadResult.Uploaded =
    SyncUploadResult.Uploaded(
        RemoteReadingProgress(
            bookHash = bookHash,
            locator = TxtLocator(charOffset = (progress * 1000).toLong(), globalProgress = progress),
            progress = progress,
            updatedAt = updatedAt,
        ),
    )

private class RecordingUploader(
    var nextResult: SyncUploadResult,
) : ReadingProgressSyncUploader {
    val uploadedPayloads = mutableListOf<ReadingProgressSyncPayload>()

    override suspend fun upload(payload: ReadingProgressSyncPayload): SyncUploadResult {
        uploadedPayloads += payload
        return nextResult
    }
}

private class RecordingBookUploader(
    var nextResult: BookMetadataUploadResult,
) : BookMetadataSyncUploader {
    val uploadedPayloads = mutableListOf<BookMetadataSyncPayload>()

    override suspend fun upload(payload: BookMetadataSyncPayload): BookMetadataUploadResult {
        uploadedPayloads += payload
        return nextResult
    }
}

private class GatedUploader(
    private val bookHash: String,
) : ReadingProgressSyncUploader {
    val calls = AtomicInteger(0)
    val maxConcurrentCalls = AtomicInteger(0)
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    private val activeCalls = AtomicInteger(0)

    override suspend fun upload(payload: ReadingProgressSyncPayload): SyncUploadResult {
        val active = activeCalls.incrementAndGet()
        maxConcurrentCalls.updateAndGet { previous -> maxOf(previous, active) }
        return try {
            calls.incrementAndGet()
            firstStarted.complete(Unit)
            releaseFirst.await()
            uploadedProgress(bookHash, "2026-06-09T06:02:00Z", payload.progress)
        } finally {
            activeCalls.decrementAndGet()
        }
    }
}

private class FixedSyncClock(private val value: String) : ProgressClock {
    override fun nowIsoString(): String = value
}
