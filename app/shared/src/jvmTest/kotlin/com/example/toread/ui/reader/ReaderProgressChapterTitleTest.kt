package com.example.toread.ui.reader

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.toread.database.ToreadDatabase
import com.example.toread.library.BookshelfBook
import com.example.toread.progress.ProgressClock
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.reader.TxtLocator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReaderProgressChapterTitleTest {
    @Test
    fun txtProgressPersistsRealChapterTitle() = runBlocking {
        val database = testDatabase()
        insertBook(database, bookHash = "hash_real_title", title = "测试书籍")
        val repository = ReadingProgressRepository(database, FixedClock)
        val book = book(bookHash = "hash_real_title", title = "测试书籍")

        saveReaderProgress(
            scope = this,
            progressRepository = repository,
            progressSyncScheduler = null,
            remoteSyncPolicy = ReaderProgressRemoteSyncPolicy.RequestFlush,
            book = book,
            pageIndex = 0,
            pages = listOf(
                ReaderPageSlice(
                    chapterTitle = "  第一章\r\n雨夜  ",
                    startOffset = 20,
                    endOffset = 100,
                ),
            ),
            sourceLength = 1_000,
            deviceId = "desktop",
        )?.join()

        val locator = assertIs<TxtLocator>(repository.localProgressSnapshot(book.bookHash)?.locator)
        assertEquals("第一章 雨夜", locator.chapterTitle)
    }

    @Test
    fun txtProgressDoesNotPersistBookTitleUsedAsGenericFallback() = runBlocking {
        val database = testDatabase()
        insertBook(database, bookHash = "hash_fallback_title", title = "测试书籍")
        val repository = ReadingProgressRepository(database, FixedClock)
        val book = book(bookHash = "hash_fallback_title", title = "测试书籍")

        saveReaderProgress(
            scope = this,
            progressRepository = repository,
            progressSyncScheduler = null,
            remoteSyncPolicy = ReaderProgressRemoteSyncPolicy.RequestFlush,
            book = book,
            pageIndex = 0,
            pages = listOf(
                ReaderPageSlice(
                    chapterTitle = "测试书籍",
                    startOffset = 20,
                    endOffset = 100,
                ),
            ),
            sourceLength = 1_000,
            deviceId = "desktop",
        )?.join()

        val locator = assertIs<TxtLocator>(repository.localProgressSnapshot(book.bookHash)?.locator)
        assertNull(locator.chapterTitle)
    }

    @Test
    fun txtProgressKeepsBookMatchingTitleWhenPaginatorMarkedItAsChapter() = runBlocking {
        val database = testDatabase()
        insertBook(database, bookHash = "hash_marked_title", title = "序章")
        val repository = ReadingProgressRepository(database, FixedClock)
        val book = book(bookHash = "hash_marked_title", title = "序章")

        saveReaderProgress(
            scope = this,
            progressRepository = repository,
            progressSyncScheduler = null,
            remoteSyncPolicy = ReaderProgressRemoteSyncPolicy.RequestFlush,
            book = book,
            pageIndex = 0,
            pages = listOf(
                ReaderPageSlice(
                    chapterTitle = "序章",
                    startOffset = 0,
                    endOffset = 80,
                    lines = listOf(
                        ReaderTextLineInfo(
                            startOffset = 0,
                            endOffset = 2,
                            text = "序章",
                            chapterTitle = "序章",
                            isChapterTitle = true,
                        ),
                    ),
                ),
            ),
            sourceLength = 1_000,
            deviceId = "desktop",
        )?.join()

        val locator = assertIs<TxtLocator>(repository.localProgressSnapshot(book.bookHash)?.locator)
        assertEquals("序章", locator.chapterTitle)
    }

    private fun testDatabase(): ToreadDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToreadDatabase.Schema.create(driver)
        return ToreadDatabase(driver)
    }

    private fun insertBook(database: ToreadDatabase, bookHash: String, title: String) {
        database.toreadDatabaseQueries.insertBook(
            id = "book_$bookHash",
            book_hash = bookHash,
            title = title,
            author = null,
            format = "txt",
            file_path = "/tmp/$bookHash.txt",
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

    private fun book(bookHash: String, title: String): BookshelfBook = BookshelfBook(
        bookHash = bookHash,
        title = title,
        author = null,
        format = "txt",
        filePath = "/tmp/$bookHash.txt",
        coverPath = null,
        fileSize = 100,
        progress = 0.0,
        lastReadAt = Timestamp,
        syncStatus = "synced",
        bookSyncStatus = "synced",
        progressSyncStatus = null,
    )

    private object FixedClock : ProgressClock {
        override fun nowIsoString(): String = "2026-08-14T00:01:00Z"
    }

    private companion object {
        const val Timestamp = "2026-08-14T00:00:00Z"
    }
}
