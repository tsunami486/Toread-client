package com.example.toread.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.toread.database.ToreadDatabase
import com.example.toread.metadata.BookLocalAnnotations
import com.example.toread.progress.ProgressClock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookshelfRepositoryEditTest {
    @Test
    fun editingATitleQueuesTheMetadataUpsertAndSupersedesThePendingOne() = runBlocking {
        val database = testDatabase()
        insertBook(database, hash = "hash-1", title = "原名", author = "原作家", syncStatus = "pending")
        database.toreadDatabaseQueries.insertSyncQueueItem(
            id = "old",
            entity_type = "book_metadata",
            entity_id = "hash-1",
            action = "upsert",
            payload_json = "{}",
            sync_status = "pending",
            retry_count = 0,
            last_error = null,
            created_at = FixedTimestamp,
            updated_at = FixedTimestamp,
            next_retry_at = null,
        )
        val repository = BookshelfRepository(database, clock = FixedClock)

        assertTrue(repository.updateBookInfo("hash-1", " 新名 ", " 新作家 "))

        val book = repository.listBooks().single()
        assertEquals("新名", book.title)
        assertEquals("新作家", book.author)
        assertEquals("pending", book.bookSyncStatus)

        val queue = database.toreadDatabaseQueries
            .selectPendingSyncQueue(next_retry_at = "2026-09-13T23:59:59Z")
            .executeAsList()
        val metadataItems = queue.filter { it.entity_type == "book_metadata" }
        assertEquals(1, metadataItems.size, "queue=$queue")
        assertTrue(metadataItems.single().payload_json.contains("新名"))
        assertEquals(
            "superseded",
            database.toreadDatabaseQueries.selectSyncQueueItemById("old").executeAsOne().sync_status,
        )
    }

    @Test
    fun aBlankTitleIsRefusedAndClearingTheAuthorIsAllowed() = runBlocking {
        val database = testDatabase()
        insertBook(database, hash = "hash-2", title = "书名", author = "作者")
        val repository = BookshelfRepository(database, clock = FixedClock)

        assertEquals(false, repository.updateBookInfo("hash-2", "   ", null))
        assertEquals(false, repository.updateBookInfo("missing", "书名", null))
        assertEquals("书名", repository.listBooks().single().title)

        assertTrue(repository.updateBookInfo("hash-2", "书名", "   "))
        assertEquals(null, repository.listBooks().single().author)
    }

    @Test
    fun theCoverCanBeReplacedAndResetToTheGeneratedOne() = runBlocking {
        val database = testDatabase()
        insertBook(database, hash = "hash-3", title = "书名")
        val repository = BookshelfRepository(database, clock = FixedClock)

        assertTrue(repository.updateBookCoverPath("hash-3", "/covers/picked.png"))
        assertEquals("/covers/picked.png", repository.listBooks().single().coverPath)

        assertTrue(repository.updateBookCoverPath("hash-3", null))
        assertEquals(null, repository.listBooks().single().coverPath)
        assertEquals(false, repository.updateBookCoverPath("missing", "/x.png"))
    }

    @Test
    fun deletingABookLocallyRemovesItsRowsAndItsQueuedWork() = runBlocking {
        val database = testDatabase()
        insertBook(database, hash = "hash-4", title = "要删的书")
        database.toreadDatabaseQueries.insertProgress(
            id = "progress-hash-4",
            book_hash = "hash-4",
            format = "epub",
            locator_json = "{}",
            progress = 0.5,
            device_id = "test",
            sync_status = "pending",
            created_at = FixedTimestamp,
            updated_at = FixedTimestamp,
        )
        database.toreadDatabaseQueries.insertSyncQueueItem(
            id = "queue-hash-4",
            entity_type = "reading_progress",
            entity_id = "hash-4",
            action = "upsert",
            payload_json = "{}",
            sync_status = "pending",
            retry_count = 0,
            last_error = null,
            created_at = FixedTimestamp,
            updated_at = FixedTimestamp,
            next_retry_at = null,
        )
        val repository = BookshelfRepository(database, clock = FixedClock)

        assertTrue(repository.deleteBookLocally("hash-4"))

        assertEquals(emptyList(), repository.listBooks())
        assertEquals(
            null,
            database.toreadDatabaseQueries.selectProgressByBookHash("hash-4").executeAsOneOrNull(),
        )
        assertEquals(
            null,
            database.toreadDatabaseQueries.selectSyncQueueItemById("queue-hash-4").executeAsOneOrNull(),
        )
        assertEquals(false, repository.deleteBookLocally("hash-4"))
    }

    @Test
    fun tagsAndTheNoteAreStoredLocallyAndTravelWithTheSyncPayload() = runBlocking {
        val database = testDatabase()
        insertBook(database, hash = "hash-5", title = "书名", metadataJson = """{"source":"local_import"}""")
        val repository = BookshelfRepository(database, clock = FixedClock)

        assertTrue(
            repository.updateBookInfo(
                bookHash = "hash-5",
                title = "书名",
                author = null,
                annotations = BookLocalAnnotations(tags = "悬疑, 连载", notes = "读到第三章"),
            ),
        )

        val book = repository.listBooks().single()
        assertEquals("悬疑, 连载", book.localTags)
        assertEquals("读到第三章", book.localNotes)

        val payload = database.toreadDatabaseQueries
            .selectActiveSyncQueueItemsByEntity("book_metadata", "hash-5")
            .executeAsList()
            .single()
            .payload_json
        assertTrue(payload.contains("悬疑, 连载"), payload)
        assertTrue(payload.contains("local_import"), payload)
    }

    private fun testDatabase(): ToreadDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToreadDatabase.Schema.create(driver)
        return ToreadDatabase(driver)
    }

    private fun insertBook(
        database: ToreadDatabase,
        hash: String,
        title: String,
        author: String? = null,
        syncStatus: String = "synced",
        metadataJson: String = "{}",
    ) {
        database.toreadDatabaseQueries.insertBook(
            id = "book_$hash",
            book_hash = hash,
            title = title,
            author = author,
            format = "epub",
            file_path = "/books/$hash/original.epub",
            cover_path = null,
            file_size = 1024L,
            metadata_json = metadataJson,
            reading_status = "unread",
            last_read_at = null,
            sync_status = syncStatus,
            created_at = FixedTimestamp,
            updated_at = FixedTimestamp,
            archived_at = null,
        )
    }

    private object FixedClock : ProgressClock {
        override fun nowIsoString(): String = FixedTimestamp
    }

    private companion object {
        const val FixedTimestamp = "2026-09-13T12:00:00Z"
    }
}
