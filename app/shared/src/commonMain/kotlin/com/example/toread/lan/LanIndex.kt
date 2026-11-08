package com.example.toread.lan

import com.example.toread.library.BookshelfBook

/** One book the serving side advertises, mapped from the shelf record the app already keeps. */
internal fun BookshelfBook.toLanBookSummary(): LanBookSummary =
    LanBookSummary(
        bookHash = bookHash,
        title = title,
        author = author,
        format = format,
        sizeBytes = fileSize,
        progress = progress,
        lastReadAt = lastReadAt,
    )

internal fun List<BookshelfBook>.toLanBookSummaries(): List<LanBookSummary> = map { it.toLanBookSummary() }

/**
 * Books the peer has and this device does not.
 *
 * The hash is the whole comparison: the shelf already stores the SHA-256 as a unique key, so "missing"
 * needs no filename heuristics and cannot misfire on a renamed copy of the same file.
 */
internal fun missingBooks(
    peerBooks: List<LanBookSummary>,
    localHashes: Set<String>,
): List<LanBookSummary> = peerBooks.filterNot { it.bookHash in localHashes }
