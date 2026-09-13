package com.example.toread.library

/**
 * Shelf pinning.
 *
 * A pinned book is an arrangement of *this* device's shelf, so the order lives in the device-level
 * settings rather than in the book row: nothing here is synced, and a pin can never fight a remote
 * metadata update. The stored value is the ordered list of pinned book hashes, newest pin first; the
 * helpers below keep it consistent with what the shelf actually contains.
 */

/** Pinned books first in pin order; every other book keeps the order the shelf already had. */
internal fun List<BookshelfBook>.withPinnedFirst(pinnedBookHashes: List<String>): List<BookshelfBook> {
    if (pinnedBookHashes.isEmpty()) return this
    val pinOrder = pinnedBookHashes.withIndex().associate { (index, hash) -> hash to index }
    val (pinned, rest) = partition { it.bookHash in pinOrder }
    if (pinned.isEmpty()) return this
    return pinned.sortedBy { pinOrder.getValue(it.bookHash) } + rest
}

/** Pins [bookHash] to the front, or unpins it when it is already pinned. */
internal fun toggledPinOrder(pinnedBookHashes: List<String>, bookHash: String): List<String> =
    if (bookHash in pinnedBookHashes) {
        pinnedBookHashes.filterNot { it == bookHash }
    } else {
        listOf(bookHash) + pinnedBookHashes
    }

/**
 * Drops hashes the shelf no longer has, so a deleted book cannot keep an invisible pin that a later
 * re-import would silently inherit.
 */
internal fun prunedPinOrder(
    pinnedBookHashes: List<String>,
    books: List<BookshelfBook>,
): List<String> {
    val knownHashes = books.mapTo(mutableSetOf()) { it.bookHash }
    return pinnedBookHashes.filter { it in knownHashes }
}
