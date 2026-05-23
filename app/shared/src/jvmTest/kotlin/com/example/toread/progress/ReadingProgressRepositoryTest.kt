package com.example.toread.progress

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.toread.database.ToreadDatabase
import com.example.toread.reader.TxtLocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReadingProgressRepositoryTest {
    @Test
    fun saveLocalProgressWritesProgressAndSyncQueue() {
        val database = testDatabase()
        insertBook(database, "hash_a")
        val repository = ReadingProgressRepository(database, FixedProgressClock("2026-05-23T01:02:03Z"))

        val result = repository.saveLocalProgress(
            bookHash = "hash_a",
            format = "txt",
            locator = TxtLocator(charOffset = 42, paragraphIndex = 2, globalProgress = 0.4),
            deviceId = "desktop",
        )

        assertIs<SaveProgressResult.Saved>(result)
        val progress = database.toreadDatabaseQueries.selectProgressByBookHash("hash_a").executeAsOne()
        assertEquals(0.4, progress.progress)
        assertEquals("pending", progress.sync_status)

        val queueItems = database.toreadDatabaseQueries
            .selectPendingSyncQueue("2026-05-23T01:02:03Z")
            .executeAsList()
        assertEquals(1, queueItems.size)
        assertEquals("reading_progress", queueItems.single().entity_type)
    }

    @Test
    fun remoteProgressUsesLatestUpdatedAtWins() {
        val database = testDatabase()
        insertBook(database, "hash_b")
        val repository = ReadingProgressRepository(database, FixedProgressClock("2026-05-23T02:00:00Z"))

        repository.applyRemoteProgress(
            bookHash = "hash_b",
            format = "txt",
            locator = TxtLocator(charOffset = 100, globalProgress = 0.1),
            deviceId = "android",
            updatedAt = "2026-05-23T02:00:00Z",
        )

        val ignored = repository.applyRemoteProgress(
            bookHash = "hash_b",
            format = "txt",
            locator = TxtLocator(charOffset = 900, globalProgress = 0.9),
            deviceId = "desktop",
            updatedAt = "2026-05-23T01:00:00Z",
        )

        assertIs<ApplyProgressResult.IgnoredOlder>(ignored)
        val progress = database.toreadDatabaseQueries.selectProgressByBookHash("hash_b").executeAsOne()
        assertEquals(0.1, progress.progress)
        assertEquals("2026-05-23T02:00:00Z", progress.updated_at)
    }

    private fun testDatabase(): ToreadDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToreadDatabase.Schema.create(driver)
        return ToreadDatabase(driver)
    }

    private fun insertBook(database: ToreadDatabase, bookHash: String) {
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
            sync_status = "synced",
            created_at = "2026-05-23T00:00:00Z",
            updated_at = "2026-05-23T00:00:00Z",
            archived_at = null,
        )
    }
}

private class FixedProgressClock(private val value: String) : ProgressClock {
    override fun nowIsoString(): String = value
}
