package com.example.toread.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.example.toread.Platform
import com.example.toread.PlatformKind
import com.example.toread.library.BookshelfBook
import com.example.toread.library.BookshelfRepository
import com.example.toread.logging.ToreadClientLog
import com.example.toread.platform.PlatformBackHandler
import com.example.toread.platform.SystemBarsController
import com.example.toread.platform.rememberSystemBarInsets
import com.example.toread.progress.ApplyProgressResult
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.progress.ReadingProgressSnapshot
import com.example.toread.progress.isIsoTimestampAfter
import com.example.toread.reader.BookLocator
import com.example.toread.reader.ReaderContentLoader
import com.example.toread.reader.ReaderDocument
import com.example.toread.reader.TxtLocator
import com.example.toread.sync.ProgressSyncScheduler
import com.example.toread.sync.ReadingProgressRemoteSource
import com.example.toread.sync.RemoteProgressFetchResult
import com.example.toread.sync.RemoteReadingProgress
import com.example.toread.ui.components.ScreenColumn
import com.example.toread.ui.reader.AndroidReaderLayout
import com.example.toread.ui.reader.DesktopReaderLayout
import com.example.toread.ui.reader.ReaderMessage
import com.example.toread.ui.reader.ReaderPageSlice
import com.example.toread.ui.reader.ReaderProgressRemoteSyncPolicy
import com.example.toread.ui.reader.normalizePageIndex
import com.example.toread.ui.reader.pageProgress
import com.example.toread.ui.reader.paginateForReader
import com.example.toread.ui.reader.rememberReaderPaginationSpec
import com.example.toread.ui.reader.saveReaderProgress
import com.example.toread.ui.reader.toPageIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
@Composable
internal fun ReaderPage(
    book: BookshelfBook?,
    platform: Platform,
    bookshelfRepository: BookshelfRepository?,
    progressRepository: ReadingProgressRepository?,
    progressSyncScheduler: ProgressSyncScheduler?,
    remoteProgressSource: ReadingProgressRemoteSource?,
    progressSaveScope: CoroutineScope,
    readerContentLoader: ReaderContentLoader,
    onProgressSyncFinished: () -> Unit = {},
    onBack: () -> Unit,
) {
    val isAndroid = platform.kind == PlatformKind.Android
    val isDesktop = platform.kind == PlatformKind.Desktop
    val density = LocalDensity.current
    val systemBarInsets = rememberSystemBarInsets()
    val topSystemBarReservePx = if (isAndroid) {
        with(density) { systemBarInsets.top.toPx().roundToInt() }
    } else {
        0
    }
    val bottomGestureReservePx = if (isAndroid) {
        with(density) { systemBarInsets.bottom.toPx().roundToInt() }
    } else {
        0
    }
    var document by remember(book?.bookHash) { mutableStateOf<ReaderDocument?>(null) }
    var restoredLocator by remember(book?.bookHash) { mutableStateOf<BookLocator?>(null) }
    var restoredForMeasuredPages by remember(book?.bookHash) { mutableStateOf(false) }
    var pageIndex by remember(book?.bookHash) { mutableIntStateOf(0) }
    var error by remember(book?.bookHash) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(book?.bookHash) { mutableStateOf(false) }
    var measuredPages by remember(book?.bookHash) { mutableStateOf<List<ReaderPageSlice>>(emptyList()) }
    var paginationComplete by remember(book?.bookHash) { mutableStateOf(false) }
    var desktopReaderFocusReady by remember(book?.bookHash) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    fun exitReaderWithoutProgress() {
        if (isAndroid) SystemBarsController.showSystemBars()
        onBack()
    }

    PlatformBackHandler(enabled = isAndroid) {
        val currentBook = book
        if (currentBook != null && measuredPages.isNotEmpty()) {
            val targetPage = pageIndex.coerceIn(0, measuredPages.lastIndex.coerceAtLeast(0))
            saveReaderProgress(
                scope = progressSaveScope,
                progressRepository = progressRepository,
                progressSyncScheduler = progressSyncScheduler,
                remoteSyncPolicy = ReaderProgressRemoteSyncPolicy.Immediate,
                book = currentBook,
                pageIndex = targetPage,
                pageCount = measuredPages.size,
                charOffset = measuredPages.getOrNull(targetPage)?.startOffset ?: 0,
                chapterOrd = measuredPages.getOrNull(targetPage)?.chapterOrd,
                startLocator = measuredPages.getOrNull(targetPage)?.startLocator,
                chapterHref = measuredPages.getOrNull(targetPage)?.chapterHref,
                sourceLength = document?.text?.length ?: 0,
                deviceId = platform.name,
                onSyncFinished = onProgressSyncFinished,
            )
        }
        exitReaderWithoutProgress()
    }

    LaunchedEffect(book?.bookHash) {
        if (book == null) return@LaunchedEffect
        document = null
        error = null
        restoredLocator = null
        restoredForMeasuredPages = false
        val openingProgress = resolveOpeningProgress(
            book = book,
            progressRepository = progressRepository,
            remoteProgressSource = remoteProgressSource,
        )
        runCatching {
            withContext(Dispatchers.Default) {
                readerContentLoader.load(book.filePath, book.format, book.title)
            }
        }.fold(
            onSuccess = { loaded ->
                document = loaded
                restoredLocator = openingProgress?.locator
                    ?: bookshelfRepository?.progressLocator(book.bookHash)
            },
            onFailure = { throwable ->
                error = "打开失败：${throwable.message ?: "未知错误"}"
            },
        )
    }

    LaunchedEffect(isAndroid, book?.bookHash) {
        if (isAndroid && book != null) {
            SystemBarsController.hideSystemBars()
        }
    }

    DisposableEffect(isAndroid, book?.bookHash) {
        onDispose {
            if (isAndroid) SystemBarsController.showSystemBars()
        }
    }

    if (book == null) {
        ScreenColumn {
            Text("阅读", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("请先从书架打开一本书。")
            OutlinedButton(onClick = onBack) {
                Text("返回书架")
            }
        }
        return
    }

    val loadedDocument = document

    val readerModifier = Modifier
        .fillMaxSize()

    Box(
        modifier = readerModifier.background(MaterialTheme.colorScheme.background),
    ) {
        when {
            error != null -> ReaderMessage(message = error.orEmpty(), onBack = ::exitReaderWithoutProgress)
            loadedDocument == null -> ReaderLoadingIndicator()
            !loadedDocument.isReadable -> ReaderMessage(
                message = loadedDocument.message ?: "当前格式暂不能直接阅读。",
                onBack = ::exitReaderWithoutProgress,
            )
            else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val spec = rememberReaderPaginationSpec(
                    title = loadedDocument.title,
                    isAndroid = isAndroid,
                    isDesktop = isDesktop,
                    maxWidthPx = with(density) { maxWidth.toPx().roundToInt() },
                    maxHeightPx = with(density) { maxHeight.toPx().roundToInt() },
                    topReservedPx = topSystemBarReservePx,
                    bottomReservedPx = bottomGestureReservePx,
                )

                LaunchedEffect(loadedDocument.text, spec) {
                    measuredPages = emptyList()
                    paginationComplete = false
                    restoredForMeasuredPages = false
                    desktopReaderFocusReady = false
                    measuredPages = withContext(Dispatchers.Default) {
                        loadedDocument.paginateForReader(spec)
                    }
                    paginationComplete = true
                }

                val pages = measuredPages

                LaunchedEffect(book.bookHash, pages.size, restoredForMeasuredPages, isDesktop, paginationComplete) {
                    if (!restoredForMeasuredPages && pages.isNotEmpty() && paginationComplete) {
                        val requestedPage = restoredLocator?.toPageIndex(pages) ?: 0
                        val normalizedPage = normalizePageIndex(
                            requestedPage,
                            pages.size,
                            isDesktop,
                        )
                        val chosenPage = pages.getOrNull(normalizedPage)
                        pageIndex = normalizedPage
                        ToreadClientLog.info(
                            LogArea,
                            "reader.seek",
                            "bookHash" to book.bookHash,
                            "locator" to restoredLocator.describeLocator(),
                            "targetCharOffset" to restoredLocator.readerCharOffset(),
                            "requestedPage" to requestedPage,
                            "chosenPage" to normalizedPage,
                            "chosenPageStartOffset" to chosenPage?.startOffset,
                            "chosenPageEndOffset" to chosenPage?.endOffset,
                            "pageCount" to pages.size,
                            "platform" to if (isDesktop) "desktop" else "android",
                        )
                        restoredForMeasuredPages = true
                    }
                }

                val currentPageIndex = pageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
                val progress = currentPageIndex.pageProgress(pages.size)

                fun saveCurrentProgress(
                    targetPage: Int = currentPageIndex,
                    remoteSyncPolicy: ReaderProgressRemoteSyncPolicy = ReaderProgressRemoteSyncPolicy.Debounced,
                ) {
                    saveReaderProgress(
                        scope = progressSaveScope,
                        progressRepository = progressRepository,
                        progressSyncScheduler = progressSyncScheduler,
                        remoteSyncPolicy = remoteSyncPolicy,
                        book = book,
                        pageIndex = targetPage,
                        pageCount = pages.size,
                        charOffset = pages.getOrNull(targetPage)?.startOffset ?: 0,
                        chapterOrd = pages.getOrNull(targetPage)?.chapterOrd,
                        startLocator = pages.getOrNull(targetPage)?.startLocator,
                        chapterHref = pages.getOrNull(targetPage)?.chapterHref,
                        sourceLength = loadedDocument.text.length,
                        deviceId = platform.name,
                        onSyncFinished = if (remoteSyncPolicy == ReaderProgressRemoteSyncPolicy.Immediate) {
                            onProgressSyncFinished
                        } else {
                            null
                        },
                    )
                }

                fun goToPage(target: Int) {
                    if (pages.isEmpty()) return
                    val normalized = normalizePageIndex(target, pages.size, isDesktop)
                    if (normalized == pageIndex) return
                    pageIndex = normalized
                    saveCurrentProgress(normalized)
                    if (isAndroid && !controlsVisible) {
                        SystemBarsController.hideSystemBars()
                    }
                }

                fun exitReader() {
                    saveCurrentProgress(remoteSyncPolicy = ReaderProgressRemoteSyncPolicy.Immediate)
                    exitReaderWithoutProgress()
                }

                val contentModifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isDesktop) {
                            Modifier
                                .focusRequester(focusRequester)
                                .onGloballyPositioned { desktopReaderFocusReady = true }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            goToPage(pageIndex - 2)
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            goToPage(pageIndex + 2)
                                            true
                                        }
                                        else -> false
                                    }
                                }
                        } else {
                            Modifier
                        },
                    )

                LaunchedEffect(isDesktop, desktopReaderFocusReady, pages.isNotEmpty(), book.bookHash) {
                    if (isDesktop && desktopReaderFocusReady && pages.isNotEmpty()) {
                        focusRequester.requestFocus()
                    }
                }

                Box(modifier = contentModifier) {
                    if (pages.isEmpty()) {
                        ReaderLoadingIndicator()
                    } else if (isDesktop) {
                        DesktopReaderLayout(
                            book = book,
                            sourceText = loadedDocument.text,
                            pages = pages,
                            pageIndex = currentPageIndex,
                            progress = progress,
                            onBack = ::exitReader,
                            onPrevious = { goToPage(pageIndex - 2) },
                            onNext = { goToPage(pageIndex + 2) },
                        )
                    } else {
                        AndroidReaderLayout(
                            book = book,
                            sourceText = loadedDocument.text,
                            pages = pages,
                            pageIndex = currentPageIndex,
                            progress = progress,
                            controlsVisible = controlsVisible,
                            onBack = ::exitReader,
                            onPrevious = { goToPage(pageIndex - 1) },
                            onNext = { goToPage(pageIndex + 1) },
                            onGoToPage = { targetPage -> goToPage(targetPage) },
                            onToggleControls = {
                                controlsVisible = !controlsVisible
                                if (controlsVisible) {
                                    SystemBarsController.showSystemBars()
                                } else {
                                    SystemBarsController.hideSystemBars()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderLoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

private suspend fun resolveOpeningProgress(
    book: BookshelfBook,
    progressRepository: ReadingProgressRepository?,
    remoteProgressSource: ReadingProgressRemoteSource?,
): ChosenReaderProgress? {
    val bookHash = book.bookHash
    ToreadClientLog.info(LogArea, "reader.open.start", "bookHash" to bookHash)

    val localProgress = progressRepository?.localProgressSnapshot(bookHash)

    val remoteResult = remoteProgressSource?.fetch(bookHash)
        ?: RemoteProgressFetchResult.Unavailable("远端进度源未初始化")

    val remoteProgress = (remoteResult as? RemoteProgressFetchResult.Found)?.progress
    ToreadClientLog.info(
        LogArea,
        "reader.open.progress",
        "bookHash" to bookHash,
        "fetchStatus" to remoteResult.fetchStatus(),
        "fetchError" to remoteResult.fetchError(),
        "localUpdatedAt" to localProgress?.updatedAt,
        "localProgress" to localProgress?.progress,
        "localSyncStatus" to localProgress?.syncStatus,
        "localLocator" to localProgress?.locator.describeLocator(),
        "remoteUpdatedAt" to remoteProgress?.updatedAt,
        "remoteProgress" to remoteProgress?.progress,
        "remoteLocator" to remoteProgress?.locator.describeLocator(),
    )

    val chosen = when {
        remoteProgress != null && remoteProgress.isNewerThan(localProgress) -> {
            val applied = progressRepository?.applyRemoteProgress(
                bookHash = bookHash,
                format = book.format,
                locator = remoteProgress.locator,
                deviceId = null,
                updatedAt = remoteProgress.updatedAt,
            )
            when (applied) {
                is ApplyProgressResult.Applied,
                null -> ChosenReaderProgress(
                    source = "remote",
                    locator = remoteProgress.locator,
                    updatedAt = remoteProgress.updatedAt,
                    progress = remoteProgress.progress,
                )
                is ApplyProgressResult.IgnoredOlder -> {
                    val latestLocal = progressRepository.localProgressSnapshot(bookHash)
                    ChosenReaderProgress(
                        source = "local_after_remote_ignored",
                        locator = latestLocal?.locator,
                        updatedAt = latestLocal?.updatedAt,
                        progress = latestLocal?.progress,
                    )
                }
            }
        }
        localProgress?.locator != null -> ChosenReaderProgress(
            source = "local",
            locator = localProgress.locator,
            updatedAt = localProgress.updatedAt,
            progress = localProgress.progress,
        )
        else -> null
    }

    ToreadClientLog.info(
        LogArea,
        "reader.open.choose",
        "bookHash" to bookHash,
        "source" to chosen?.source,
        "chosenUpdatedAt" to chosen?.updatedAt,
        "chosenProgress" to chosen?.progress,
        "chosenLocator" to chosen?.locator.describeLocator(),
    )
    return chosen
}

private data class ChosenReaderProgress(
    val source: String,
    val locator: BookLocator?,
    val updatedAt: String?,
    val progress: Double?,
)

private fun RemoteReadingProgress.isNewerThan(localProgress: ReadingProgressSnapshot?): Boolean =
    localProgress == null || isIsoTimestampAfter(updatedAt, localProgress.updatedAt)

private fun RemoteProgressFetchResult.fetchStatus(): String =
    when (this) {
        is RemoteProgressFetchResult.Found -> "found"
        RemoteProgressFetchResult.NotFound -> "not_found"
        is RemoteProgressFetchResult.Unavailable -> "unavailable"
        is RemoteProgressFetchResult.Failed -> "failed"
    }

private fun RemoteProgressFetchResult.fetchError(): String? =
    when (this) {
        is RemoteProgressFetchResult.Unavailable -> reason
        is RemoteProgressFetchResult.Failed -> message
        else -> null
    }

private fun BookLocator?.describeLocator(): String =
    when (this) {
        is com.example.toread.reader.EpubLocator ->
            "epub(chapterHref=$chapterHref, chapterIndex=$chapterIndex, chapterProgress=$chapterProgress, charOffset=$charOffset, unitIndex=$unitIndex, textOffset=$textOffset, globalProgress=$globalProgress, cfi=$cfi)"
        is com.example.toread.reader.PdfLocator ->
            "pdf(pageIndex=$pageIndex, pageOffsetY=$pageOffsetY, globalProgress=$globalProgress)"
        is com.example.toread.reader.TxtLocator ->
            "txt(charOffset=$charOffset, paragraphIndex=$paragraphIndex, globalProgress=$globalProgress)"
        null -> "none"
    }

private fun BookLocator?.readerCharOffset(): Long? =
    when (this) {
        is com.example.toread.reader.EpubLocator -> charOffset
        is com.example.toread.reader.TxtLocator -> charOffset
        else -> null
    }

private const val LogArea = "sync"
