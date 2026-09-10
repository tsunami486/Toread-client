package com.example.toread.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.toread.database.ToreadDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookshelfRepositoryTest {
    @Test
    fun malformedProgressLocatorDoesNotPreventBookshelfLoading() {
        val database = testDatabase()
        insertBook(database, bookHash = "hash_bad_locator")
        database.toreadDatabaseQueries.insertProgress(
            id = "progress_hash_bad_locator",
            book_hash = "hash_bad_locator",
            format = "epub",
            locator_json = "not-json",
            progress = 0.42,
            device_id = "desktop",
            sync_status = "synced",
            created_at = Timestamp,
            updated_at = Timestamp,
        )

        val book = BookshelfRepository(database).listBooks().single()

        assertEquals("hash_bad_locator", book.bookHash)
        assertEquals(0.42, book.progress)
        assertNull(book.locator)
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
            format = "epub",
            file_path = "/tmp/$bookHash.epub",
            cover_path = null,
            file_size = 100,
            metadata_json = "{}",
            reading_status = "reading",
            last_read_at = Timestamp,
            sync_status = "synced",
            created_at = Timestamp,
            updated_at = Timestamp,
            archived_at = null,
        )
    }

    private companion object {
        const val Timestamp = "2026-08-14T00:00:00Z"
    }
}
