package com.example.toread.library

import com.example.toread.database.ToreadDatabase
import com.example.toread.reader.BookLocator
import com.example.toread.reader.BookLocatorJson

class BookshelfRepository(
    private val database: ToreadDatabase,
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
                fileSize = row.file_size,
                progress = progress?.progress ?: 0.0,
                lastReadAt = row.last_read_at,
                syncStatus = row.sync_status,
            )
        }

    fun progressLocator(bookHash: String): BookLocator? =
        database.toreadDatabaseQueries
            .selectProgressByBookHash(bookHash)
            .executeAsOneOrNull()
            ?.locator_json
            ?.let { runCatching { BookLocatorJson.decode(it) }.getOrNull() }
}

data class BookshelfBook(
    val bookHash: String,
    val title: String,
    val author: String?,
    val format: String,
    val filePath: String,
    val fileSize: Long,
    val progress: Double,
    val lastReadAt: String?,
    val syncStatus: String,
)
