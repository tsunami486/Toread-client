package com.example.toread.ui.reader

import com.example.toread.library.BookshelfBook
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.reader.BookLocator
import com.example.toread.reader.EpubLocator
import com.example.toread.reader.PdfLocator
import com.example.toread.reader.TxtLocator
import com.example.toread.progress.SaveProgressResult
import com.example.toread.sync.ProgressSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal fun BookLocator.toPageIndex(
    pages: List<ReaderPageSlice>,
): Int {
    val pageCount = pages.size
    if (pageCount <= 1) return 0
    return when (this) {
        is TxtLocator -> pages.pageIndexForCharOffset(charOffset)
        is PdfLocator -> pageIndex.coerceIn(0, pageCount - 1)
        is EpubLocator -> pages.pageIndexForEpubContentLocator(this)
            ?: charOffset
                ?.let { pages.pageIndexForCharOffset(it) }
            ?: pages.pageIndexForEpubChapter(this, allowNearestChapter = false)
            ?: toProgressPageIndex(pageCount)
    }
}

internal fun BookLocator.toPdfPageIndex(pageCount: Int): Int {
    if (pageCount <= 1) return 0
    return when (this) {
        is PdfLocator -> pageIndex.coerceIn(0, pageCount - 1)
        else -> toProgressPageIndex(pageCount)
    }
}

internal fun normalizePageIndex(index: Int, pageCount: Int, isDesktop: Boolean): Int {
    if (pageCount <= 1) return 0
    val bounded = index.coerceIn(0, pageCount - 1)
    return if (isDesktop) bounded - (bounded % 2) else bounded
}

internal fun Int.pageProgress(pageCount: Int): Double =
    if (pageCount <= 1) 0.0 else toDouble() / (pageCount - 1).toDouble()

private fun BookLocator.toProgressPageIndex(pageCount: Int): Int =
    (globalProgress.coerceIn(0.0, 1.0) * (pageCount - 1)).roundToInt()

private fun List<ReaderPageSlice>.pageIndexForCharOffset(charOffset: Long): Int {
    if (isEmpty()) return 0
    val target = charOffset.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val exactIndex = indexOfFirst { page ->
        val hasContentRange = page.endOffset > page.startOffset
        if (hasContentRange) {
            target >= page.startOffset && target < page.endOffset
        } else {
            target == page.startOffset
        }
    }
    if (exactIndex >= 0) return exactIndex

    val nearestPrevious = indexOfLast { page -> page.startOffset <= target }
    return nearestPrevious.coerceIn(0, lastIndex)
}

private fun List<ReaderPageSlice>.pageIndexForEpubContentLocator(locator: EpubLocator): Int? {
    val href = locator.chapterHref.takeIf { it.isNotBlank() && it != "local-text" } ?: return null
    val unitIndex = locator.unitIndex ?: return null
    val textOffset = locator.textOffset ?: 0
    val exactIndex = indexOfFirst { page ->
        val start = page.startLocator
        val end = page.endLocator
        if (page.chapterHref != href) {
            false
        } else {
            val targetVsStart = compareEpubPosition(
                firstUnitIndex = unitIndex,
                firstTextOffset = textOffset,
                secondUnitIndex = start.unitIndex,
                secondTextOffset = start.textOffset,
            )
            val targetVsEnd = compareEpubPosition(
                firstUnitIndex = unitIndex,
                firstTextOffset = textOffset,
                secondUnitIndex = end.unitIndex,
                secondTextOffset = end.textOffset,
            )
            val emptyLogicalRange =
                start.unitIndex == end.unitIndex &&
                    start.textOffset == end.textOffset
            targetVsStart >= 0 && if (emptyLogicalRange) {
                targetVsEnd == 0
            } else {
                targetVsEnd < 0
            }
        }
    }
    if (exactIndex >= 0) return exactIndex
    return indexOfLast { page ->
        val start = page.startLocator
        page.chapterHref == href &&
            compareEpubPosition(
                firstUnitIndex = start.unitIndex,
                firstTextOffset = start.textOffset,
                secondUnitIndex = unitIndex,
                secondTextOffset = textOffset,
            ) <= 0
    }.takeIf { it >= 0 }
}

private fun compareEpubPosition(
    firstUnitIndex: Int,
    firstTextOffset: Int,
    secondUnitIndex: Int,
    secondTextOffset: Int,
): Int =
    when {
        firstUnitIndex != secondUnitIndex -> firstUnitIndex.compareTo(secondUnitIndex)
        else -> firstTextOffset.compareTo(secondTextOffset)
    }

private fun List<ReaderPageSlice>.pageIndexForEpubChapter(
    locator: EpubLocator,
    allowNearestChapter: Boolean,
): Int? {
    if (isEmpty()) return null
    val href = locator.chapterHref.takeIf { it.isNotBlank() && it != "local-text" }
    val matchingIndices = indices.filter { index ->
        val page = this[index]
        when {
            href != null -> page.chapterHref == href
            else -> page.chapterOrd == locator.chapterIndex
        }
    }.ifEmpty {
        indices.filter { index -> this[index].chapterOrd == locator.chapterIndex }
    }
    val candidateIndices = if (matchingIndices.isNotEmpty() || !allowNearestChapter) {
        matchingIndices
    } else {
        val nearestChapterOrd = minByOrNull { page ->
            kotlin.math.abs(page.chapterOrd - locator.chapterIndex)
        }?.chapterOrd ?: return null
        indices.filter { index -> this[index].chapterOrd == nearestChapterOrd }
    }
    if (candidateIndices.isEmpty()) return null
    val chapterProgress = locator.chapterProgress
        .takeIf(Double::isFinite)
        ?.coerceIn(0.0, 1.0)
        ?: 0.0
    val offset = (chapterProgress * (candidateIndices.size - 1)).roundToInt()
    return candidateIndices[offset]
}

private fun List<ReaderPageSlice>.epubLocatorForPage(
    pageIndex: Int,
): EpubLocator {
    val boundedPageIndex = pageIndex.coerceIn(0, lastIndex.coerceAtLeast(0))
    val page = getOrNull(boundedPageIndex)
    val chapterProgress = chapterProgressAt(boundedPageIndex)
    return EpubLocator(
        chapterHref = page?.startLocator?.chapterHref
            ?: page?.chapterHref
            ?: "local-text",
        chapterIndex = page?.chapterOrd ?: boundedPageIndex,
        chapterProgress = chapterProgress,
        globalProgress = boundedPageIndex.pageProgress(size),
        charOffset = null,
        unitIndex = page?.startLocator?.unitIndex,
        textOffset = page?.startLocator?.textOffset,
    )
}

private fun List<ReaderPageSlice>.chapterProgressAt(pageIndex: Int): Double {
    val page = getOrNull(pageIndex) ?: return 0.0
    fun isSameChapter(candidate: ReaderPageSlice): Boolean =
        if (page.chapterHref != null) {
            candidate.chapterHref == page.chapterHref
        } else {
            candidate.chapterOrd == page.chapterOrd
        }

    var firstPageIndex = pageIndex
    while (firstPageIndex > 0 && isSameChapter(this[firstPageIndex - 1])) {
        firstPageIndex--
    }
    var lastPageIndex = pageIndex
    while (lastPageIndex < lastIndex && isSameChapter(this[lastPageIndex + 1])) {
        lastPageIndex++
    }
    return (pageIndex - firstPageIndex).pageProgress(lastPageIndex - firstPageIndex + 1)
}

internal fun saveReaderProgress(
    scope: CoroutineScope,
    progressRepository: ReadingProgressRepository?,
    progressSyncScheduler: ProgressSyncScheduler?,
    remoteSyncPolicy: ReaderProgressRemoteSyncPolicy,
    book: BookshelfBook,
    pageIndex: Int,
    pages: List<ReaderPageSlice>,
    sourceLength: Int,
    deviceId: String,
    onSyncFinished: (() -> Unit)? = null,
): Job? {
    val repository = progressRepository ?: return null
    val pageCount = pages.size
    val boundedPageIndex = pageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
    val page = pages.getOrNull(boundedPageIndex)
    val charOffset = page?.startOffset ?: 0
    val globalProgress = boundedPageIndex.pageProgress(pageCount)
    val textProgress = if (sourceLength > 0) {
        charOffset.toDouble().coerceIn(0.0, sourceLength.toDouble()) / sourceLength.toDouble()
    } else {
        globalProgress
    }
    val locator = when (book.format.lowercase()) {
        "epub" -> pages.epubLocatorForPage(pageIndex = boundedPageIndex)
        "pdf" -> PdfLocator(pageIndex = boundedPageIndex, pageOffsetY = 0.0, globalProgress = globalProgress)
        else -> TxtLocator(
            charOffset = charOffset.toLong(),
            paragraphIndex = null,
            globalProgress = textProgress,
        )
    }
    return saveReaderLocatorProgress(
        scope = scope,
        progressRepository = repository,
        progressSyncScheduler = progressSyncScheduler,
        remoteSyncPolicy = remoteSyncPolicy,
        book = book,
        locator = locator,
        deviceId = deviceId,
        onSyncFinished = onSyncFinished,
    )
}

internal fun savePdfReaderProgress(
    scope: CoroutineScope,
    progressRepository: ReadingProgressRepository?,
    progressSyncScheduler: ProgressSyncScheduler?,
    remoteSyncPolicy: ReaderProgressRemoteSyncPolicy,
    book: BookshelfBook,
    pageIndex: Int,
    pageCount: Int,
    progressPageIndex: Int = pageIndex,
    pageOffsetY: Double = 0.0,
    globalProgressOverride: Double? = null,
    deviceId: String,
    onSyncFinished: (() -> Unit)? = null,
): Job? {
    val repository = progressRepository ?: return null
    return saveReaderLocatorProgress(
        scope = scope,
        progressRepository = repository,
        progressSyncScheduler = progressSyncScheduler,
        remoteSyncPolicy = remoteSyncPolicy,
        book = book,
        locator = pdfProgressLocator(
            pageIndex = pageIndex,
            pageCount = pageCount,
            progressPageIndex = progressPageIndex,
            pageOffsetY = pageOffsetY,
            globalProgressOverride = globalProgressOverride,
        ),
        deviceId = deviceId,
        onSyncFinished = onSyncFinished,
    )
}

internal fun saveEpubReaderProgress(
    scope: CoroutineScope,
    progressRepository: ReadingProgressRepository?,
    progressSyncScheduler: ProgressSyncScheduler?,
    remoteSyncPolicy: ReaderProgressRemoteSyncPolicy,
    book: BookshelfBook,
    locator: EpubLocator,
    deviceId: String,
    onSyncFinished: (() -> Unit)? = null,
): Job? {
    val repository = progressRepository ?: return null
    return saveReaderLocatorProgress(
        scope = scope,
        progressRepository = repository,
        progressSyncScheduler = progressSyncScheduler,
        remoteSyncPolicy = remoteSyncPolicy,
        book = book,
        locator = locator,
        deviceId = deviceId,
        onSyncFinished = onSyncFinished,
    )
}

internal fun pdfProgressLocator(
    pageIndex: Int,
    pageCount: Int,
    progressPageIndex: Int = pageIndex,
    pageOffsetY: Double = 0.0,
    globalProgressOverride: Double? = null,
): PdfLocator {
    val lastPageIndex = (pageCount - 1).coerceAtLeast(0)
    val boundedPageIndex = pageIndex.coerceIn(0, lastPageIndex)
    val boundedProgressPageIndex = progressPageIndex.coerceIn(0, lastPageIndex)
    return PdfLocator(
        pageIndex = boundedPageIndex,
        pageOffsetY = pageOffsetY
            .takeIf(Double::isFinite)
            ?.coerceIn(0.0, 1.0)
            ?: 0.0,
        globalProgress = globalProgressOverride
            ?.takeIf(Double::isFinite)
            ?.coerceIn(0.0, 1.0)
            ?: boundedProgressPageIndex.pageProgress(pageCount),
    )
}

private fun saveReaderLocatorProgress(
    scope: CoroutineScope,
    progressRepository: ReadingProgressRepository,
    progressSyncScheduler: ProgressSyncScheduler?,
    remoteSyncPolicy: ReaderProgressRemoteSyncPolicy,
    book: BookshelfBook,
    locator: BookLocator,
    deviceId: String,
    onSyncFinished: (() -> Unit)?,
): Job =
    scope.launch {
        val result = progressRepository.saveLocalProgress(
            bookHash = book.bookHash,
            format = book.format,
            locator = locator,
            deviceId = deviceId,
        )
        if (result is SaveProgressResult.Saved) {
            when (remoteSyncPolicy) {
                ReaderProgressRemoteSyncPolicy.Debounced -> progressSyncScheduler?.scheduleDebounced()
                ReaderProgressRemoteSyncPolicy.Immediate -> progressSyncScheduler?.flushNow()?.join()
            }
            onSyncFinished?.invoke()
        }
    }

internal enum class ReaderProgressRemoteSyncPolicy {
    Debounced,
    Immediate,
}
