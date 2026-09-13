package com.example.toread.library

import kotlin.test.Test
import kotlin.test.assertEquals

class BookshelfPinningTest {
    @Test
    fun pinnedBooksLeadInPinOrderAndTheRestKeepsItsOrder() {
        val books = listOf(book("a"), book("b"), book("c"), book("d"))

        assertEquals(
            listOf("c", "a", "b", "d"),
            books.withPinnedFirst(listOf("c", "a")).map { it.bookHash },
        )
    }

    @Test
    fun aShelfWithNothingPinnedIsLeftUntouched() {
        val books = listOf(book("a"), book("b"))

        assertEquals(listOf("a", "b"), books.withPinnedFirst(emptyList()).map { it.bookHash })
        assertEquals(
            listOf("a", "b"),
            books.withPinnedFirst(listOf("missing")).map { it.bookHash },
        )
    }

    @Test
    fun pinningPutsTheBookFirstAndPinningItAgainReleasesIt() {
        assertEquals(listOf("c", "a", "b"), toggledPinOrder(listOf("a", "b"), "c"))
        assertEquals(listOf("b"), toggledPinOrder(listOf("a", "b"), "a"))
        assertEquals(listOf("a"), toggledPinOrder(emptyList(), "a"))
    }

    @Test
    fun hashesOfBooksThatLeftTheShelfAreForgotten() {
        val books = listOf(book("a"), book("c"))

        // "b" was deleted, so it must not keep an invisible pin that a later import would inherit.
        assertEquals(listOf("c", "a"), prunedPinOrder(listOf("c", "b", "a"), books))
        assertEquals(emptyList(), prunedPinOrder(listOf("b"), books))
    }

    private fun book(hash: String) = BookshelfBook(
        bookHash = hash,
        title = "书 $hash",
        author = null,
        format = "epub",
        filePath = "/books/$hash/original.epub",
        coverPath = null,
        fileSize = 1024L,
        progress = 0.0,
        lastReadAt = null,
        syncStatus = "synced",
        bookSyncStatus = "synced",
        progressSyncStatus = null,
    )
}
