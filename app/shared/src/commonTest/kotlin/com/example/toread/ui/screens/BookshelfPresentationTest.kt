package com.example.toread.ui.screens

import androidx.compose.ui.unit.dp
import com.example.toread.library.BookshelfBook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookshelfPresentationTest {
    @Test
    fun responsiveSpecUsesExactSizeClassBoundaries() {
        val compact = bookshelfResponsiveSpec(599.dp)
        val mediumStart = bookshelfResponsiveSpec(600.dp)
        val mediumEnd = bookshelfResponsiveSpec(839.dp)
        val expanded = bookshelfResponsiveSpec(840.dp)

        assertEquals(BookshelfWidthClass.Compact, compact.sizeClass)
        assertEquals(140.dp, compact.minGridCardWidth)
        assertEquals(BookshelfWidthClass.Medium, mediumStart.sizeClass)
        assertEquals(BookshelfWidthClass.Medium, mediumEnd.sizeClass)
        assertEquals(150.dp, mediumStart.minGridCardWidth)
        assertEquals(BookshelfWidthClass.Expanded, expanded.sizeClass)
        assertTrue(expanded.persistentSearch)
        assertEquals(280.dp, expanded.persistentSearchWidth)
        assertEquals(1200.dp, expanded.contentMaxWidth)
        assertEquals(800.dp, expanded.constrainedSectionMaxWidth)
        assertTrue(!compact.persistentSearch)
        assertTrue(!mediumStart.persistentSearch)
    }

    @Test
    fun continueReadingSelectsLatestEligibleUnfinishedBook() {
        val selected = selectContinueReadingBook(
            listOf(
                book(hash = "unread", progress = 0.0, lastReadAt = "2026-08-01T00:00:00Z"),
                book(hash = "inactive", progress = 0.0, lastReadAt = null),
                book(hash = "99x", progress = 0.994, lastReadAt = "2026-08-04T00:00:00Z"),
                book(hash = "threshold", progress = 0.995, lastReadAt = "2026-08-05T00:00:00Z"),
                book(hash = "finished", progress = 1.0, lastReadAt = "2026-08-06T00:00:00Z"),
            ),
        )

        assertEquals("99x", selected?.bookHash)
    }

    @Test
    fun continueReadingAllowsProgressWithoutTimestamp() {
        assertEquals(
            "started",
            selectContinueReadingBook(
                listOf(book(hash = "started", progress = 0.2, lastReadAt = null)),
            )?.bookHash,
        )
    }

    @Test
    fun zeroProgressBookWithAReadTimestampRemainsEligible() {
        assertEquals(
            "opened",
            selectContinueReadingBook(
                listOf(book(hash = "opened", progress = 0.0, lastReadAt = "2026-08-01T00:00:00Z")),
            )?.bookHash,
        )
    }

    @Test
    fun continueReadingIsHiddenWhenEveryBookIsFinishedOrInactive() {
        assertNull(
            selectContinueReadingBook(
                listOf(
                    book(hash = "inactive", progress = 0.0, lastReadAt = null),
                    book(hash = "threshold", progress = 0.995, lastReadAt = "2026-08-05T00:00:00Z"),
                    book(hash = "finished", progress = 1.0, lastReadAt = "2026-08-06T00:00:00Z"),
                ),
            ),
        )
    }

    @Test
    fun fallbackCoverUsesFirstTwoReadableCharactersAndFormatFallback() {
        assertEquals(
            "神秘",
            fallbackCoverPresentation(
                title = "《神秘-复苏》",
                format = "epub",
                bookHash = "hash",
            ).mark,
        )
        assertEquals(
            "EP",
            fallbackCoverPresentation(
                title = " 《》— ",
                format = "epub",
                bookHash = "hash",
            ).mark,
        )
    }

    @Test
    fun fallbackCoverPaletteIsStableAndBoundedToThreeGroups() {
        val first = fallbackCoverPresentation("书", "txt", "stable-hash")
        val second = fallbackCoverPresentation("另一本书", "pdf", "stable-hash")
        val allGroups = listOf("a", "b", "c")
            .map { hash -> fallbackCoverPresentation("书", "txt", hash).paletteIndex }
            .toSet()

        assertEquals(first.paletteIndex, second.paletteIndex)
        assertEquals(setOf(0, 1, 2), allGroups)
        assertTrue(first.paletteIndex in 0..2)
    }

    private fun book(
        hash: String,
        progress: Double,
        lastReadAt: String?,
    ): BookshelfBook = BookshelfBook(
        bookHash = hash,
        title = "测试书籍",
        author = "作者",
        format = "epub",
        filePath = "/tmp/$hash.epub",
        coverPath = null,
        fileSize = 100,
        progress = progress,
        lastReadAt = lastReadAt,
        syncStatus = "synced",
        bookSyncStatus = "synced",
        progressSyncStatus = "synced",
    )
}
