package com.example.toread.ui.reader

import com.example.toread.reader.BookLocator
import com.example.toread.reader.EpubChapterMetadata
import com.example.toread.reader.EpubLocator
import com.example.toread.reader.EpubPublicationIndex
import com.example.toread.reader.ReaderContentBlock
import com.example.toread.reader.ReaderDocument
import com.example.toread.reader.toEpubLocatorChapterTitle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

internal const val AndroidEpubResidentPageLimit = 5
internal const val DesktopEpubResidentPageLimit = 6
internal const val EpubChapterDocumentCacheLimit = 3
internal const val EpubNextChapterPrefetchPageLimit = 2

internal data class EpubPageFrame(
    val page: ReaderPageSlice,
)

internal data class EpubReflowState(
    val pages: List<EpubPageFrame> = emptyList(),
    val pageIndex: Int = 0,
    val chapters: List<EpubChapterMetadata> = emptyList(),
    val currentChapterOrd: Int = 0,
    val locator: EpubLocator? = null,
    val progress: Double = 0.0,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val generation: Long = 0L,
    val residentPageCount: Int = 0,
    val cachedChapterCount: Int = 0,
    val prefetchedNextChapterOrd: Int? = null,
)

/**
 * Bounded, cursor-driven EPUB pagination session.
 *
 * The publication directory is kept in memory, but chapter XHTML and laid-out
 * pages are loaded only around the active cursor. No method in this class calls
 * the full-document paginator.
 */
internal class EpubReflowSession(
    private val publication: EpubPublicationIndex,
    private val chapterLoader: suspend (Int) -> ReaderDocument?,
) {
    private val mutex = Mutex()
    private val chapterCache = LinkedHashMap<Int, ReaderDocument>()
    private val unreadableChapterOrds = mutableSetOf<Int>()
    private val frames = mutableListOf<EpubPageFrame>()
    private var activeIndex = 0
    private var visiblePageCount = 1
    private var spec: ReaderPaginationSpec? = null
    private var generation = 0L
    private var publicationStartReached = false
    private var publicationEndReached = false
    private var prefetchedChapterOrd: Int? = null
    private var prefetchedChapterFrames: List<EpubPageFrame> = emptyList()

    var state: EpubReflowState = EpubReflowState(chapters = publication.chapters)
        private set

    suspend fun open(
        locator: BookLocator?,
        spec: ReaderPaginationSpec,
        visiblePageCount: Int,
    ): EpubReflowState = mutex.withLock {
        generation++
        this.spec = spec
        this.visiblePageCount = visiblePageCount.coerceIn(1, 2)
        frames.clear()
        activeIndex = 0
        publicationStartReached = false
        publicationEndReached = false
        prefetchedChapterOrd = null
        prefetchedChapterFrames = emptyList()
        state = state.copy(isLoading = true, error = null, generation = generation)

        val targetChapter = publication.resolveChapter(locator as? EpubLocator)
        if (targetChapter == null) {
            return@withLock publishError("EPUB 没有可读取的章节")
        }
        val (resolvedChapter, document) = loadNearestReadableChapter(targetChapter)
            ?: return@withLock publishError("EPUB 没有可读取的正文")
        val start = document.resolveOpeningCursor(locator as? EpubLocator)
            ?: document.epubChapterStartLocator()
            ?: return@withLock publishError("章节没有可定位的内容")

        val backward = if (
            start.logicalCompare(document.epubChapterStartLocator() ?: start) > 0
        ) {
            document.paginateEpubBackward(
                spec = spec,
                endLocator = start,
                maxPages = pagesBeforeLimit(),
            )
        } else {
            EpubPageBatch(emptyList(), reachedChapterStart = true, reachedChapterEnd = false)
        }
        frames += backward.pages.map(::EpubPageFrame)
        activeIndex = frames.size

        val forward = document.paginateEpubForward(
            spec = spec,
            startLocator = start,
            maxPages = visiblePageCount() + pagesAfterLimit(),
        )
        frames += forward.pages.map(::EpubPageFrame)
        publicationStartReached = backward.reachedChapterStart &&
            publication.previousChapter(resolvedChapter.ord) == null
        publicationEndReached = forward.reachedChapterEnd &&
            publication.nextChapter(resolvedChapter.ord) == null

        ensureBackwardWindow()
        ensureForwardWindow()
        trimWindow()
        publish()
    }

    suspend fun next(): EpubReflowState = mutex.withLock {
        if (spec == null) return@withLock state
        if (frames.isEmpty()) return@withLock state
        ensureForwardWindow(extraPages = visiblePageCount())
        val target = activeIndex + visiblePageCount()
        if (target >= frames.size) return@withLock publish()
        activeIndex = target
        touchChapter(frames[activeIndex].page.chapterOrd)
        trimWindow()
        ensureForwardWindow()
        ensureBackwardWindow()
        trimWindow()
        generation++
        publish()
    }

    suspend fun previous(): EpubReflowState = mutex.withLock {
        if (spec == null) return@withLock state
        if (frames.isEmpty()) return@withLock state
        ensureBackwardWindow(extraPages = visiblePageCount())
        val target = (activeIndex - visiblePageCount()).coerceAtLeast(0)
        if (target == activeIndex) return@withLock publish()
        activeIndex = target
        touchChapter(frames[activeIndex].page.chapterOrd)
        trimWindow()
        ensureBackwardWindow()
        ensureForwardWindow()
        trimWindow()
        generation++
        publish()
    }

    suspend fun jumpToChapter(chapterOrd: Int): EpubReflowState = mutex.withLock {
        val currentSpec = spec ?: return@withLock state
        val chapter = publication.chapters.firstOrNull { it.ord == chapterOrd }
            ?: return@withLock state
        generation++
        frames.clear()
        activeIndex = 0
        publicationStartReached = false
        publicationEndReached = false
        prefetchedChapterFrames = emptyList()
        prefetchedChapterOrd = null
        val (resolvedChapter, document) = loadNearestReadableChapter(chapter)
            ?: return@withLock publishError("EPUB 没有可读取的正文")
        val start = document.epubChapterStartLocator()
            ?: return@withLock publishError("章节没有可定位的内容")
        val batch = document.paginateEpubForward(
            spec = currentSpec,
            startLocator = start,
            maxPages = visiblePageCount() + pagesAfterLimit(),
        )
        frames += batch.pages.map(::EpubPageFrame)
        publicationStartReached = resolvedChapter == publication.chapters.firstOrNull()
        publicationEndReached = batch.reachedChapterEnd &&
            publication.nextChapter(resolvedChapter.ord) == null
        ensureBackwardWindow()
        ensureForwardWindow()
        trimWindow()
        publish()
    }

    suspend fun jumpToLocator(locator: EpubLocator): EpubReflowState {
        val currentSpec = mutex.withLock { spec } ?: return state
        val slots = mutex.withLock { visiblePageCount }
        return open(locator, currentSpec, slots)
    }

    suspend fun reflow(
        spec: ReaderPaginationSpec,
        visiblePageCount: Int,
    ): EpubReflowState {
        val anchor = mutex.withLock { currentLocator() }
        return open(anchor, spec, visiblePageCount)
    }

    suspend fun preloadFollowingChapter(): EpubReflowState = mutex.withLock {
        if (frames.isNotEmpty()) {
            prefetchNextChapter()
        }
        publish()
    }

    private fun visiblePageCount(): Int = visiblePageCount

    private fun pagesBeforeLimit(): Int = 2

    private fun pagesAfterLimit(): Int = 2

    private fun residentPageLimit(): Int =
        if (visiblePageCount == 2) DesktopEpubResidentPageLimit else AndroidEpubResidentPageLimit

    private suspend fun ensureForwardWindow(extraPages: Int = 0) {
        val neededAfter = visiblePageCount() + pagesAfterLimit() + extraPages
        var guard = 0
        while (frames.size - activeIndex < neededAfter && !publicationEndReached && guard++ < 8) {
            val previousSize = frames.size
            appendForward((neededAfter - (frames.size - activeIndex)).coerceAtLeast(1))
            if (frames.size == previousSize && !publicationEndReached) break
        }
    }

    private suspend fun appendForward(maxPages: Int) {
        val currentSpec = spec ?: return
        val lastFrame = frames.lastOrNull()
        if (lastFrame == null) {
            publicationEndReached = true
            return
        }
        val chapterOrd = lastFrame.page.chapterOrd
        val document = loadChapter(chapterOrd)
        val chapterEnd = document?.epubChapterEndLocator()
        if (
            document != null &&
            chapterEnd != null &&
            lastFrame.page.endLocator.logicalCompare(chapterEnd) < 0
        ) {
            val batch = document.paginateEpubForward(
                spec = currentSpec,
                startLocator = lastFrame.page.endLocator,
                maxPages = maxPages,
            )
            appendDistinct(batch.pages.map(::EpubPageFrame))
            if (!batch.reachedChapterEnd) return
            val remaining = maxPages - batch.pages.size
            if (remaining <= 0) return
            appendNextChapter(chapterOrd, remaining)
            return
        }

        appendNextChapter(chapterOrd, maxPages)
    }

    private suspend fun appendNextChapter(
        chapterOrd: Int,
        maxPages: Int,
    ) {
        val currentSpec = spec ?: return
        val nextReadable = loadNextReadableChapter(chapterOrd)
        if (nextReadable == null) {
            publicationEndReached = true
            return
        }
        val (nextChapter, nextDocument) = nextReadable
        val prefetched = if (prefetchedChapterOrd == nextChapter.ord) {
            prefetchedChapterFrames
        } else {
            emptyList()
        }
        if (prefetched.isNotEmpty()) {
            appendDistinct(prefetched.take(maxPages))
            prefetchedChapterFrames = prefetched.drop(maxPages)
            if (prefetchedChapterFrames.isEmpty()) prefetchedChapterOrd = null
            return
        }
        val nextStart = nextDocument.epubChapterStartLocator() ?: return
        val batch = nextDocument.paginateEpubForward(
            spec = currentSpec,
            startLocator = nextStart,
            maxPages = maxPages,
        )
        appendDistinct(batch.pages.map(::EpubPageFrame))
        publicationEndReached = batch.reachedChapterEnd &&
            publication.nextChapter(nextChapter.ord) == null
    }

    private suspend fun ensureBackwardWindow(extraPages: Int = 0) {
        val neededBefore = pagesBeforeLimit() + extraPages
        var guard = 0
        while (activeIndex < neededBefore && !publicationStartReached && guard++ < 8) {
            val previousSize = frames.size
            prependBackward((neededBefore - activeIndex).coerceAtLeast(1))
            if (frames.size == previousSize && !publicationStartReached) break
        }
    }

    private suspend fun prependBackward(maxPages: Int) {
        val currentSpec = spec ?: return
        val firstFrame = frames.firstOrNull()
        if (firstFrame == null) {
            publicationStartReached = true
            return
        }
        val chapterOrd = firstFrame.page.chapterOrd
        val document = loadChapter(chapterOrd)
        val chapterStart = document?.epubChapterStartLocator()
        if (
            document != null &&
            chapterStart != null &&
            firstFrame.page.startLocator.logicalCompare(chapterStart) > 0
        ) {
            val batch = document.paginateEpubBackward(
                spec = currentSpec,
                endLocator = firstFrame.page.startLocator,
                maxPages = maxPages,
            )
            prependDistinct(batch.pages.map(::EpubPageFrame))
            if (!batch.reachedChapterStart) return
            val remaining = maxPages - batch.pages.size
            if (remaining <= 0) return
            prependPreviousChapter(chapterOrd, remaining)
            return
        }

        prependPreviousChapter(chapterOrd, maxPages)
    }

    private suspend fun prependPreviousChapter(
        chapterOrd: Int,
        maxPages: Int,
    ) {
        val currentSpec = spec ?: return
        val previousReadable = loadPreviousReadableChapter(chapterOrd)
        if (previousReadable == null) {
            publicationStartReached = true
            return
        }
        val (previousChapter, previousDocument) = previousReadable
        val previousEnd = previousDocument.epubChapterEndLocator() ?: return
        val batch = previousDocument.paginateEpubBackward(
            spec = currentSpec,
            endLocator = previousEnd,
            maxPages = maxPages,
        )
        prependDistinct(batch.pages.map(::EpubPageFrame))
        publicationStartReached = batch.reachedChapterStart &&
            publication.previousChapter(previousChapter.ord) == null
    }

    private fun appendDistinct(newFrames: List<EpubPageFrame>) {
        newFrames.forEach { candidate ->
            val last = frames.lastOrNull()
            if (last == null || !last.page.sameLogicalPage(candidate.page)) {
                frames += candidate
            }
        }
    }

    private fun prependDistinct(newFrames: List<EpubPageFrame>) {
        var added = 0
        newFrames.asReversed().forEach { candidate ->
            val first = frames.firstOrNull()
            if (first == null || !first.page.sameLogicalPage(candidate.page)) {
                frames.add(0, candidate)
                added++
            }
        }
        activeIndex += added
    }

    private fun trimWindow() {
        val keepBefore = pagesBeforeLimit()
        while (activeIndex > keepBefore) {
            frames.removeAt(0)
            activeIndex--
        }
        val keepThrough = activeIndex + visiblePageCount() + pagesAfterLimit()
        while (frames.size > keepThrough) {
            frames.removeAt(frames.lastIndex)
        }
        while (frames.size > residentPageLimit()) {
            if (activeIndex > 0) {
                frames.removeAt(0)
                activeIndex--
            } else {
                frames.removeAt(frames.lastIndex)
            }
        }
        activeIndex = activeIndex.coerceIn(0, frames.lastIndex.coerceAtLeast(0))
    }

    private suspend fun prefetchNextChapter() {
        val currentOrd = frames.getOrNull(activeIndex)?.page?.chapterOrd ?: return
        val nextReadable = loadNextReadableChapter(currentOrd)
        if (nextReadable == null) {
            prefetchedChapterOrd = null
            prefetchedChapterFrames = emptyList()
            return
        }
        val (nextChapter, document) = nextReadable
        if (frames.any { it.page.chapterOrd == nextChapter.ord }) {
            prefetchedChapterOrd = null
            prefetchedChapterFrames = emptyList()
            touchChapter(currentOrd)
            return
        }
        if (prefetchedChapterOrd == nextChapter.ord && prefetchedChapterFrames.isNotEmpty()) return
        val currentSpec = spec ?: return
        val start = document.epubChapterStartLocator() ?: return
        val batch = document.paginateEpubForward(
            spec = currentSpec,
            startLocator = start,
            maxPages = EpubNextChapterPrefetchPageLimit,
        )
        prefetchedChapterOrd = nextChapter.ord
        prefetchedChapterFrames = batch.pages.map(::EpubPageFrame)
        touchChapter(currentOrd)
    }

    private suspend fun loadChapter(chapterOrd: Int): ReaderDocument? {
        if (chapterOrd in unreadableChapterOrds) return null
        chapterCache.remove(chapterOrd)?.let { cached ->
            chapterCache[chapterOrd] = cached
            return cached
        }
        val loaded = chapterLoader(chapterOrd) ?: run {
            unreadableChapterOrds += chapterOrd
            return null
        }
        chapterCache[chapterOrd] = loaded
        while (chapterCache.size > EpubChapterDocumentCacheLimit) {
            val eldest = chapterCache.keys.firstOrNull() ?: break
            chapterCache.remove(eldest)
        }
        return loaded
    }

    private suspend fun loadNearestReadableChapter(
        preferred: EpubChapterMetadata,
    ): Pair<EpubChapterMetadata, ReaderDocument>? {
        loadChapter(preferred.ord)?.let { return preferred to it }
        loadNextReadableChapter(preferred.ord)?.let { return it }
        return loadPreviousReadableChapter(preferred.ord)
    }

    private suspend fun loadNextReadableChapter(
        chapterOrd: Int,
    ): Pair<EpubChapterMetadata, ReaderDocument>? {
        var candidate = publication.nextChapter(chapterOrd)
        while (candidate != null) {
            loadChapter(candidate.ord)?.let { return candidate to it }
            candidate = publication.nextChapter(candidate.ord)
        }
        return null
    }

    private suspend fun loadPreviousReadableChapter(
        chapterOrd: Int,
    ): Pair<EpubChapterMetadata, ReaderDocument>? {
        var candidate = publication.previousChapter(chapterOrd)
        while (candidate != null) {
            loadChapter(candidate.ord)?.let { return candidate to it }
            candidate = publication.previousChapter(candidate.ord)
        }
        return null
    }

    private fun touchChapter(chapterOrd: Int) {
        val document = chapterCache.remove(chapterOrd) ?: return
        chapterCache[chapterOrd] = document
    }

    private fun publish(): EpubReflowState {
        val locator = currentLocator()
        val currentOrd = frames.getOrNull(activeIndex)?.page?.chapterOrd
            ?: publication.chapters.firstOrNull()?.ord
            ?: 0
        state = EpubReflowState(
            pages = frames.toList(),
            pageIndex = activeIndex,
            chapters = publication.chapters,
            currentChapterOrd = currentOrd,
            locator = locator,
            progress = locator?.globalProgress ?: 0.0,
            canPrevious = activeIndex > 0 || !publicationStartReached,
            canNext = activeIndex + visiblePageCount() < frames.size || !publicationEndReached,
            isLoading = false,
            error = null,
            generation = generation,
            residentPageCount = frames.size + prefetchedChapterFrames.size,
            cachedChapterCount = chapterCache.size,
            prefetchedNextChapterOrd = prefetchedChapterOrd,
        )
        return state
    }

    private fun publishError(message: String): EpubReflowState {
        state = state.copy(
            isLoading = false,
            error = message,
            generation = generation,
            residentPageCount = frames.size + prefetchedChapterFrames.size,
            cachedChapterCount = chapterCache.size,
        )
        return state
    }

    private fun currentLocator(): EpubLocator? {
        val page = frames.getOrNull(activeIndex)?.page ?: return null
        val document = chapterCache[page.chapterOrd]
        val chapterProgress = document?.chapterProgress(page.startLocator) ?: 0.0
        return publication.toEpubLocator(page.startLocator, chapterProgress)
    }
}

private fun EpubPublicationIndex.resolveChapter(locator: EpubLocator?): EpubChapterMetadata? {
    if (chapters.isEmpty()) return null
    if (locator == null) return chapters.first()
    val matchingHref = locator.chapterHref
        .takeIf { it.isNotBlank() && it != "local-text" }
        ?.let { href -> chapters.filter { it.href == href } }
        .orEmpty()
    if (matchingHref.isNotEmpty()) {
        return matchingHref.minByOrNull { abs(it.ord - locator.chapterIndex) }
    }
    return chapters.firstOrNull { it.ord == locator.chapterIndex }
        ?: chapters.minByOrNull { abs(it.ord - locator.chapterIndex) }
}

private fun EpubPublicationIndex.previousChapter(chapterOrd: Int): EpubChapterMetadata? {
    val index = chapters.indexOfFirst { it.ord == chapterOrd }
    return chapters.getOrNull(index - 1)
}

private fun EpubPublicationIndex.nextChapter(chapterOrd: Int): EpubChapterMetadata? {
    val index = chapters.indexOfFirst { it.ord == chapterOrd }
    return chapters.getOrNull(index + 1)
}

private fun ReaderDocument.resolveOpeningCursor(locator: EpubLocator?): ReaderTextLocator? {
    val start = epubChapterStartLocator() ?: return null
    if (locator == null) return start
    val chapter = flow?.chapters?.firstOrNull() ?: return start
    if (
        locator.chapterIndex != chapter.ord &&
        locator.chapterHref.takeIf { it.isNotBlank() && it != "local-text" } != chapter.href
    ) {
        return start
    }
    val unitIndex = locator.unitIndex
    val textOffset = locator.textOffset
    if (unitIndex != null && textOffset != null) {
        if (
            unitIndex == -1 &&
            chapter.titleEndOffset <= chapter.titleStartOffset
        ) {
            return start
        }
        val block = chapter.blocks.firstOrNull { it.unitIndex == unitIndex }
        val absoluteOffset = when (block) {
            is ReaderContentBlock.Text -> block.absoluteStart +
                textOffset.coerceIn(0, block.text.length)
            is ReaderContentBlock.Image -> block.absoluteStart
            null -> if (unitIndex == -1) {
                chapter.titleStartOffset + textOffset.coerceIn(0, chapter.title.length)
            } else {
                start.absoluteOffset
            }
        }
        return start.copy(
            unitIndex = unitIndex,
            textOffset = textOffset.coerceAtLeast(0),
            absoluteOffset = absoluteOffset,
        )
    }
    return locatorAtChapterProgress(locator.chapterProgress)
}

private fun ReaderDocument.locatorAtChapterProgress(progress: Double): ReaderTextLocator? {
    val chapter = flow?.chapters?.firstOrNull() ?: return null
    val titleWeight = if (chapter.titleEndOffset > chapter.titleStartOffset) {
        chapter.title.length
    } else {
        0
    }
    val blockWeights = chapter.blocks.map { block ->
        when (block) {
            is ReaderContentBlock.Text -> block.text.length.coerceAtLeast(1)
            is ReaderContentBlock.Image -> 1
        }
    }
    val total = (titleWeight + blockWeights.sum()).coerceAtLeast(1)
    var target = (
        progress.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
        ) * total
    if (target < titleWeight && titleWeight > 0) {
        return ReaderTextLocator(
            chapterOrd = chapter.ord,
            chapterHref = chapter.href,
            unitIndex = -1,
            textOffset = target.toInt().coerceIn(0, chapter.title.length),
            absoluteOffset = chapter.titleStartOffset + target.toInt(),
        )
    }
    target -= titleWeight
    chapter.blocks.forEachIndexed { index, block ->
        val weight = blockWeights[index]
        if (target < weight || index == chapter.blocks.lastIndex) {
            return when (block) {
                is ReaderContentBlock.Text -> {
                    val offset = target.toInt().coerceIn(0, block.text.length)
                    ReaderTextLocator(
                        chapterOrd = chapter.ord,
                        chapterHref = chapter.href,
                        unitIndex = block.unitIndex,
                        textOffset = offset,
                        absoluteOffset = block.absoluteStart + offset,
                    )
                }
                is ReaderContentBlock.Image -> ReaderTextLocator(
                    chapterOrd = chapter.ord,
                    chapterHref = chapter.href,
                    unitIndex = block.unitIndex,
                    textOffset = 0,
                    absoluteOffset = block.absoluteStart,
                )
            }
        }
        target -= weight
    }
    return epubChapterEndLocator()
}

private fun ReaderDocument.chapterProgress(locator: ReaderTextLocator): Double {
    val chapter = flow?.chapters?.firstOrNull() ?: return 0.0
    val titleWeight = if (chapter.titleEndOffset > chapter.titleStartOffset) {
        chapter.title.length
    } else {
        0
    }
    var before = 0L
    var position = 0L
    if (locator.unitIndex == -1) {
        position = locator.textOffset.coerceIn(0, titleWeight).toLong()
    } else {
        before += titleWeight
        for (block in chapter.blocks) {
            val weight = when (block) {
                is ReaderContentBlock.Text -> block.text.length.coerceAtLeast(1)
                is ReaderContentBlock.Image -> 1
            }
            if (block.unitIndex < locator.unitIndex) {
                before += weight
            } else if (block.unitIndex == locator.unitIndex) {
                position = when (block) {
                    is ReaderContentBlock.Text -> locator.textOffset
                        .coerceIn(0, block.text.length)
                        .toLong()
                    is ReaderContentBlock.Image -> 0L
                }
                break
            }
        }
    }
    val total = (
        titleWeight.toLong() +
            chapter.blocks.sumOf { block ->
                when (block) {
                    is ReaderContentBlock.Text -> block.text.length.coerceAtLeast(1).toLong()
                    is ReaderContentBlock.Image -> 1L
                }
            }
        ).coerceAtLeast(1L)
    return ((before + position).toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
}

private fun EpubPublicationIndex.toEpubLocator(
    cursor: ReaderTextLocator,
    chapterProgress: Double,
): EpubLocator {
    val chapter = chapters.firstOrNull { it.ord == cursor.chapterOrd }
    val totalWeight = totalContentWeight.coerceAtLeast(0L)
    val globalProgress = if (totalWeight > 0L && chapter != null) {
        val before = chapters
            .asSequence()
            .takeWhile { it.ord != chapter.ord }
            .sumOf(EpubChapterMetadata::contentWeight)
        (
            before.toDouble() +
                chapter.contentWeight.coerceAtLeast(0L) * chapterProgress
            ) / totalWeight.toDouble()
    } else {
        val index = chapters.indexOfFirst { it.ord == cursor.chapterOrd }.coerceAtLeast(0)
        (index + chapterProgress) / chapters.size.coerceAtLeast(1).toDouble()
    }
    return EpubLocator(
        chapterHref = cursor.chapterHref ?: chapter?.href.orEmpty(),
        chapterIndex = cursor.chapterOrd,
        chapterProgress = chapterProgress.coerceIn(0.0, 1.0),
        globalProgress = globalProgress.coerceIn(0.0, 1.0),
        charOffset = null,
        unitIndex = cursor.unitIndex,
        textOffset = cursor.textOffset,
        chapterTitle = chapter?.title.toEpubLocatorChapterTitle(cursor.chapterOrd),
    )
}

private fun ReaderPageSlice.sameLogicalPage(other: ReaderPageSlice): Boolean =
    startLocator.sameLogicalPosition(other.startLocator) &&
        endLocator.sameLogicalPosition(other.endLocator)

private fun ReaderTextLocator.sameLogicalPosition(other: ReaderTextLocator): Boolean =
    chapterOrd == other.chapterOrd &&
        chapterHref == other.chapterHref &&
        unitIndex == other.unitIndex &&
        textOffset == other.textOffset

private fun ReaderTextLocator.logicalCompare(other: ReaderTextLocator): Int =
    when {
        chapterOrd != other.chapterOrd -> chapterOrd.compareTo(other.chapterOrd)
        unitIndex != other.unitIndex -> unitIndex.compareTo(other.unitIndex)
        else -> textOffset.compareTo(other.textOffset)
    }
