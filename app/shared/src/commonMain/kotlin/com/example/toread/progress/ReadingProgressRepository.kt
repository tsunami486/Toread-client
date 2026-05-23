package com.example.toread.progress

import com.example.toread.database.ToreadDatabase
import com.example.toread.reader.BookLocator
import com.example.toread.reader.BookLocatorJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ReadingProgressRepository(
    private val database: ToreadDatabase,
    private val clock: ProgressClock = PlatformProgressClock(),
) {
    fun saveLocalProgress(
        bookHash: String,
        format: String,
        locator: BookLocator,
        deviceId: String?,
    ): SaveProgressResult {
        val updatedAt = clock.nowIsoString()
        val existing = database.toreadDatabaseQueries
            .selectProgressByBookHash(bookHash)
            .executeAsOneOrNull()

        if (existing != null && existing.updated_at > updatedAt) {
            return SaveProgressResult.IgnoredOlder(existing.updated_at)
        }

        val locatorJson = BookLocatorJson.encode(locator)
        val progress = locator.globalProgress.coerceIn(0.0, 1.0)
        val payloadJson = ReadingProgressJson.encode(
            ReadingProgressSyncPayload(
                bookHash = bookHash,
                format = format,
                locator = locator,
                progress = progress,
                deviceId = deviceId,
                updatedAt = updatedAt,
            ),
        )

        database.toreadDatabaseQueries.transaction {
            if (existing == null) {
                database.toreadDatabaseQueries.insertProgress(
                    id = "progress_$bookHash",
                    book_hash = bookHash,
                    format = format,
                    locator_json = locatorJson,
                    progress = progress,
                    device_id = deviceId,
                    sync_status = SyncStatus.Pending.value,
                    created_at = updatedAt,
                    updated_at = updatedAt,
                )
            } else {
                database.toreadDatabaseQueries.updateProgress(
                    locator_json = locatorJson,
                    progress = progress,
                    device_id = deviceId,
                    sync_status = SyncStatus.Pending.value,
                    updated_at = updatedAt,
                    book_hash = bookHash,
                )
            }

            database.toreadDatabaseQueries.updateBookLastReadAt(
                last_read_at = updatedAt,
                book_hash = bookHash,
            )

            database.toreadDatabaseQueries.insertSyncQueueItem(
                id = "progress_${bookHash}_$updatedAt",
                entity_type = "reading_progress",
                entity_id = bookHash,
                action = "upsert",
                payload_json = payloadJson,
                sync_status = SyncStatus.Pending.value,
                retry_count = 0,
                last_error = null,
                created_at = updatedAt,
                updated_at = updatedAt,
                next_retry_at = null,
            )
        }

        return SaveProgressResult.Saved(updatedAt)
    }

    fun applyRemoteProgress(
        bookHash: String,
        format: String,
        locator: BookLocator,
        deviceId: String?,
        updatedAt: String,
    ): ApplyProgressResult {
        val existing = database.toreadDatabaseQueries
            .selectProgressByBookHash(bookHash)
            .executeAsOneOrNull()

        if (existing != null && existing.updated_at > updatedAt) {
            return ApplyProgressResult.IgnoredOlder(existing.updated_at)
        }

        val locatorJson = BookLocatorJson.encode(locator)
        val progress = locator.globalProgress.coerceIn(0.0, 1.0)

        if (existing == null) {
            database.toreadDatabaseQueries.insertProgress(
                id = "progress_$bookHash",
                book_hash = bookHash,
                format = format,
                locator_json = locatorJson,
                progress = progress,
                device_id = deviceId,
                sync_status = SyncStatus.Synced.value,
                created_at = updatedAt,
                updated_at = updatedAt,
            )
        } else {
            database.toreadDatabaseQueries.updateProgress(
                locator_json = locatorJson,
                progress = progress,
                device_id = deviceId,
                sync_status = SyncStatus.Synced.value,
                updated_at = updatedAt,
                book_hash = bookHash,
            )
        }

        return ApplyProgressResult.Applied(updatedAt)
    }
}

sealed interface SaveProgressResult {
    data class Saved(val updatedAt: String) : SaveProgressResult
    data class IgnoredOlder(val currentUpdatedAt: String) : SaveProgressResult
}

sealed interface ApplyProgressResult {
    data class Applied(val updatedAt: String) : ApplyProgressResult
    data class IgnoredOlder(val currentUpdatedAt: String) : ApplyProgressResult
}

private enum class SyncStatus(val value: String) {
    Pending("pending"),
    Synced("synced"),
}

@Serializable
data class ReadingProgressSyncPayload(
    val bookHash: String,
    val format: String,
    val locator: BookLocator,
    val progress: Double,
    val deviceId: String?,
    val updatedAt: String,
)

object ReadingProgressJson {
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(payload: ReadingProgressSyncPayload): String =
        json.encodeToString(ReadingProgressSyncPayload.serializer(), payload)

    fun decode(value: String): ReadingProgressSyncPayload =
        json.decodeFromString(ReadingProgressSyncPayload.serializer(), value)
}

expect class PlatformProgressClock() : ProgressClock

interface ProgressClock {
    fun nowIsoString(): String
}
