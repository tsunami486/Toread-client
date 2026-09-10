package com.example.toread.sync

import com.example.toread.database.Sync_queue
import com.example.toread.database.ToreadDatabase
import com.example.toread.logging.ToreadClientLog
import com.example.toread.progress.PlatformProgressClock
import com.example.toread.progress.ProgressClock
import com.example.toread.progress.ReadingProgressJson
import com.example.toread.progress.ReadingProgressSyncPayload
import com.example.toread.progress.isIsoTimestampAfter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncQueueProcessor(
    private val database: ToreadDatabase,
    private val progressUploader: ReadingProgressSyncUploader,
    private val clock: ProgressClock = PlatformProgressClock(),
    private val bookMetadataUploader: BookMetadataSyncUploader? = null,
) {
    private val flushMutex = Mutex()

    suspend fun flushPending(
        limit: Int = DefaultFlushLimit,
        stopOnFailure: Boolean = false,
    ): SyncRunSummary = flushMutex.withLock {
        val now = clock.nowIsoString()
        val dueItems = database.toreadDatabaseQueries
            .selectPendingSyncQueue(now)
            .executeAsList()
        val items = coalesceReadingProgressItems(dueItems, now)
            .take(limit.coerceAtLeast(1))

        var uploaded = 0
        var failed = 0
        var skipped = 0
        var lastError: String? = null

        for (item in items) {
            when (item.entity_type) {
                ReadingProgressEntityType -> {
                    when (val result = uploadReadingProgress(item)) {
                        QueueItemResult.Uploaded -> uploaded++
                        is QueueItemResult.Failed -> {
                            failed++
                            lastError = result.message
                            if (stopOnFailure) break
                        }
                        is QueueItemResult.Unavailable -> {
                            skipped++
                            lastError = result.reason
                            break
                        }
                    }
                }
                BookMetadataEntityType -> {
                    when (val result = uploadBookMetadata(item)) {
                        QueueItemResult.Uploaded -> uploaded++
                        is QueueItemResult.Failed -> {
                            failed++
                            lastError = result.message
                            if (stopOnFailure) break
                        }
                        is QueueItemResult.Unavailable -> {
                            skipped++
                            lastError = result.reason
                            break
                        }
                    }
                }
                else -> {
                    val failedAt = clock.nowIsoString()
                    val message = "暂不支持的同步实体：${item.entity_type}"
                    database.toreadDatabaseQueries.markSyncQueueItemFailed(
                        last_error = message,
                        updated_at = failedAt,
                        next_retry_at = null,
                        id = item.id,
                    )
                    failed++
                    lastError = message
                    if (stopOnFailure) break
                }
            }
        }

        SyncRunSummary(
            scanned = dueItems.size,
            uploaded = uploaded,
            failed = failed,
            skipped = skipped,
            lastError = lastError,
        )
    }

    private suspend fun uploadReadingProgress(item: Sync_queue): QueueItemResult {
        val payload = runCatching { ReadingProgressJson.decode(item.payload_json) }.getOrElse { throwable ->
            val message = "阅读进度同步数据无法解析：${throwable.message ?: "未知错误"}"
            markQueueItemFailed(item, message)
            return QueueItemResult.Failed(message)
        }

        return when (val result = progressUploader.upload(payload)) {
            is SyncUploadResult.Uploaded -> {
                val completedAt = clock.nowIsoString()
                database.toreadDatabaseQueries.transaction {
                    database.toreadDatabaseQueries.markSyncQueueItemDone(
                        updated_at = completedAt,
                        id = item.id,
                    )
                    database.toreadDatabaseQueries.markProgressUploaded(
                        server_updated_at = result.progress.updatedAt,
                        book_hash = payload.bookHash,
                        local_updated_at = payload.updatedAt,
                    )
                }
                ToreadClientLog.info(
                    LogArea,
                    "progress.put.ok",
                    "itemId" to item.id,
                    "bookHash" to payload.bookHash,
                    "localUpdatedAt" to payload.updatedAt,
                    "serverUpdatedAt" to result.progress.updatedAt,
                    "serverProgress" to result.progress.progress,
                )
                QueueItemResult.Uploaded
            }
            is SyncUploadResult.Failed -> {
                markProgressFailed(item, payload, result.message)
                QueueItemResult.Failed(result.message)
            }
            is SyncUploadResult.Unavailable -> {
                logUnavailable(item, result.reason)
                QueueItemResult.Unavailable(result.reason)
            }
        }
    }

    private suspend fun uploadBookMetadata(item: Sync_queue): QueueItemResult {
        val uploader = bookMetadataUploader ?: run {
            val reason = "书籍元数据同步器未初始化"
            logUnavailable(item, reason)
            return QueueItemResult.Unavailable(reason)
        }
        val payload = runCatching { BookMetadataJson.decode(item.payload_json) }.getOrElse { throwable ->
            val message = "书籍同步数据无法解析：${throwable.message ?: "未知错误"}"
            markBookMetadataFailed(item, null, message)
            return QueueItemResult.Failed(message)
        }

        return when (val result = uploader.upload(payload)) {
            is BookMetadataUploadResult.Uploaded -> {
                val completedAt = clock.nowIsoString()
                database.toreadDatabaseQueries.transaction {
                    database.toreadDatabaseQueries.markSyncQueueItemDone(
                        updated_at = completedAt,
                        id = item.id,
                    )
                    database.toreadDatabaseQueries.updateBookSyncStatus(
                        sync_status = SyncStatus.Synced.value,
                        updated_at = result.book.updatedAt,
                        book_hash = payload.bookHash,
                    )
                }
                ToreadClientLog.info(
                    LogArea,
                    "book.put.ok",
                    "itemId" to item.id,
                    "bookHash" to payload.bookHash,
                    "localUpdatedAt" to payload.updatedAt,
                    "serverUpdatedAt" to result.book.updatedAt,
                )
                QueueItemResult.Uploaded
            }
            is BookMetadataUploadResult.Failed -> {
                markBookMetadataFailed(item, payload, result.message)
                QueueItemResult.Failed(result.message)
            }
            is BookMetadataUploadResult.Unavailable -> {
                logUnavailable(item, result.reason)
                QueueItemResult.Unavailable(result.reason)
            }
        }
    }

    private fun markProgressFailed(
        item: Sync_queue,
        payload: ReadingProgressSyncPayload,
        message: String,
    ) {
        val failedAt = clock.nowIsoString()
        database.toreadDatabaseQueries.transaction {
            database.toreadDatabaseQueries.markSyncQueueItemFailed(
                last_error = message,
                updated_at = failedAt,
                next_retry_at = null,
                id = item.id,
            )
            database.toreadDatabaseQueries.updateProgressSyncStatus(
                sync_status = SyncStatus.Failed.value,
                book_hash = payload.bookHash,
                updated_at = payload.updatedAt,
            )
        }
        ToreadClientLog.warn(
            LogArea,
            "progress.put.fail",
            "itemId" to item.id,
            "bookHash" to payload.bookHash,
            "localUpdatedAt" to payload.updatedAt,
            "error" to message,
        )
    }

    private fun markBookMetadataFailed(
        item: Sync_queue,
        payload: BookMetadataSyncPayload?,
        message: String,
    ) {
        val failedAt = clock.nowIsoString()
        database.toreadDatabaseQueries.transaction {
            database.toreadDatabaseQueries.markSyncQueueItemFailed(
                last_error = message,
                updated_at = failedAt,
                next_retry_at = null,
                id = item.id,
            )
            database.toreadDatabaseQueries.updateBookSyncStatus(
                sync_status = SyncStatus.Failed.value,
                updated_at = failedAt,
                book_hash = payload?.bookHash ?: item.entity_id,
            )
        }
        ToreadClientLog.warn(
            LogArea,
            "book.put.fail",
            "itemId" to item.id,
            "bookHash" to (payload?.bookHash ?: item.entity_id),
            "localUpdatedAt" to payload?.updatedAt,
            "error" to message,
        )
    }

    private fun markQueueItemFailed(item: Sync_queue, message: String) {
        database.toreadDatabaseQueries.markSyncQueueItemFailed(
            last_error = message,
            updated_at = clock.nowIsoString(),
            next_retry_at = null,
            id = item.id,
        )
    }

    private fun logUnavailable(item: Sync_queue, reason: String) {
        ToreadClientLog.warn(
            LogArea,
            "queue.flush.skip",
            "itemId" to item.id,
            "entityType" to item.entity_type,
            "reason" to reason,
        )
    }

    private fun coalesceReadingProgressItems(
        items: List<Sync_queue>,
        updatedAt: String,
    ): List<Sync_queue> {
        val passthroughItems = mutableListOf<Sync_queue>()
        val latestProgressItems = linkedMapOf<String, Pair<Sync_queue, ReadingProgressSyncPayload>>()
        val supersededIds = mutableListOf<String>()

        items.forEach { item ->
            if (item.entity_type != ReadingProgressEntityType) {
                passthroughItems += item
                return@forEach
            }

            val payload = runCatching { ReadingProgressJson.decode(item.payload_json) }.getOrNull()
            if (payload == null) {
                passthroughItems += item
                return@forEach
            }

            val current = latestProgressItems[payload.bookHash]
            if (current == null || isIsoTimestampAfter(payload.updatedAt, current.second.updatedAt)) {
                if (current != null) {
                    supersededIds += current.first.id
                }
                latestProgressItems[payload.bookHash] = item to payload
            } else {
                supersededIds += item.id
            }
        }

        if (supersededIds.isNotEmpty()) {
            database.toreadDatabaseQueries.transaction {
                supersededIds.forEach { id ->
                    database.toreadDatabaseQueries.markSyncQueueItemSuperseded(
                        updated_at = updatedAt,
                        id = id,
                    )
                }
            }
            ToreadClientLog.info(
                LogArea,
                "queue.progress.coalesce",
                "superseded" to supersededIds.size,
                "kept" to latestProgressItems.size,
            )
        }

        val progressItems = latestProgressItems.values
            .map { it.first }
            .sortedByDescending { it.created_at }
        return progressItems + passthroughItems.sortedBy { it.created_at }
    }

    private companion object {
        const val DefaultFlushLimit = 20
        const val ReadingProgressEntityType = "reading_progress"
        const val BookMetadataEntityType = "book_metadata"
        const val LogArea = "sync"
    }
}

private sealed interface QueueItemResult {
    data object Uploaded : QueueItemResult
    data class Failed(val message: String) : QueueItemResult
    data class Unavailable(val reason: String) : QueueItemResult
}

interface ReadingProgressSyncUploader {
    suspend fun upload(payload: ReadingProgressSyncPayload): SyncUploadResult
}

sealed interface SyncUploadResult {
    data class Uploaded(val progress: RemoteReadingProgress) : SyncUploadResult
    data class Unavailable(val reason: String) : SyncUploadResult
    data class Failed(val message: String) : SyncUploadResult
}

data class SyncRunSummary(
    val scanned: Int,
    val uploaded: Int,
    val failed: Int,
    val skipped: Int,
    val lastError: String?,
)

private enum class SyncStatus(val value: String) {
    Synced("synced"),
    Failed("failed"),
}
