package com.example.toread.importing

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.toread.database.ToreadDatabase
import com.example.toread.platform.PlatformFile
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The drop path hands the importer a batch straight from the file manager, so a single unreadable item
 * must not take the whole drop down with it.
 */
class BookImportBatchTest {
    @Test
    fun oneUnreadableFileDoesNotAbortTheRestOfTheBatch() = runBlocking {
        val missing = PlatformFile(
            displayName = "缺失.epub",
            path = "C:\\definitely\\missing\\book.epub",
        )
        val readable = Files.createTempFile("toread-batch", ".txt")
        Files.writeString(readable, "拖拽导入批处理验证")

        try {
            val results = BookImportService(testDatabase()).importFiles(
                listOf(
                    missing,
                    PlatformFile(
                        displayName = readable.fileName.toString(),
                        path = readable.toString(),
                    ),
                ),
            )

            assertEquals(2, results.size)
            assertTrue(results[0] is BookImportResult.Failed, "results=$results")
            assertTrue(results[1] is BookImportResult.Imported, "results=$results")
            assertTrue(
                (results[0] as BookImportResult.Failed).reason.isNotBlank(),
                "a failure must carry a readable reason",
            )
        } finally {
            Files.deleteIfExists(readable)
        }
    }

    private fun testDatabase(): ToreadDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToreadDatabase.Schema.create(driver)
        return ToreadDatabase(driver)
    }
}
