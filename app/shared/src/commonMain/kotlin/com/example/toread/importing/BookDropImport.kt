package com.example.toread.importing

import com.example.toread.platform.PlatformFile
import com.example.toread.ui.text.ToreadStrings

/**
 * Drag-and-drop import, shared by the desktop drop target and its tests.
 *
 * Dropping is a second entry point into the same importer the file picker uses, so the split is:
 * these helpers decide *what* was dropped and how to describe the outcome, and [BookImportService]
 * stays the only thing that validates, hashes, copies and records a book.
 */

/** True when the file name carries an extension the importer accepts. */
internal fun String.isSupportedBookFileName(): Boolean =
    substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .let { it.isNotBlank() && it in supportedBookExtensions }

/**
 * Keeps the dropped files that look like books, in drop order.
 *
 * Files that do not are reported as skipped instead of being handed to the importer, because the
 * importer would answer each of them with a failure and turn one drag into a wall of errors.
 */
internal fun List<PlatformFile>.droppedBookFiles(): List<PlatformFile> =
    filter { file -> (file.path ?: file.displayName).isSupportedBookFileName() }

/** One line describing what a drop did, for the snackbar. */
internal fun droppedImportMessage(
    results: List<BookImportResult>,
    skippedCount: Int = 0,
): String {
    val imported = results.count { it is BookImportResult.Imported }
    val duplicates = results.count { it is BookImportResult.Duplicate }
    val failures = results.filterIsInstance<BookImportResult.Failed>()
    val summary = when {
        results.isEmpty() -> ToreadStrings.ImportFailedGeneric
        imported == 0 && duplicates == 0 && failures.isNotEmpty() ->
            ToreadStrings.importDropFailed(
                reason = ToreadStrings.importFailedMessage(failures.first().reason),
                count = failures.size,
            )
        else -> ToreadStrings.importedSummary(
            imported = imported,
            duplicates = duplicates,
            failed = failures.size,
        )
    }
    val skipped = skippedCount
        .takeIf { it > 0 }
        ?.let(ToreadStrings::droppedUnsupportedFiles)
    return listOfNotNull(summary, skipped).joinToString(" ")
}
