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
import androidx.compose.runtime.snapshots.Snapshot
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
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.settings.ToreadAppearancePreferences
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
import com.example.toread.ui.reader.ReaderNightModeConfirmDialog
import com.example.toread.ui.reader.ReaderPageSlice
import com.example.toread.ui.reader.ReaderProgressRemoteSyncPolicy
import com.example.toread.ui.reader.chapterPagePosition
import com.example.toread.ui.reader.normalizePageIndex
import com.example.toread.ui.reader.pageIndexForChapterPage
import com.example.toread.ui.reader.pageProgress
import com.example.toread.ui.reader.paginateForReader
import com.example.toread.ui.reader.rememberReaderNightModeState
import com.example.toread.ui.reader.rememberReaderPaginationSpec
import com.example.toread.ui.reader.readerSurfaceColors
import com.example.toread.ui.reader.saveReaderProgress
import com.example.toread.ui.reader.toPageIndex
import com.example.toread.ui.theme.toreadIsDark
import com.example.toread.ui.text.ToreadStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
@Composable
internal fun ReaderPage(
    book: BookshelfBook?,
    platform: Platform,
    bookshelfRepository: BookshelfRepository?,
    progressRepository: ReadingProgressRepository?,
    progressSyncScheduler: ProgressSyncScheduler?,
    remoteProgressSource: ReadingProgressRemoteSource?,
    settingsRepository: LocalSettingsRepository?,
    progressSaveScope: CoroutineScope,
    readerContentLoader: ReaderContentLoader,
    onProgressSyncFinished: () -> Unit = {},
    onBack: () -> Unit,
) {
    if (book?.format?.equals("pdf", ignoreCase = true) == true) {
        PdfReaderPage(
            book = book,
            platform = platform,
            bookshelfRepository = bookshelfRepository,
            progressRepository = progressRepository,
            progressSyncScheduler = progressSyncScheduler,
            remoteProgressSource = remoteProgressSource,
            settingsRepository = settingsRepository,
            progressSaveScope = progressSaveScope,
            onProgressSyncFinished = onProgressSyncFinished,
            onBack = onBack,
        )
        return
    }
    if (book?.format?.equals("epub", ignoreCase = true) == true) {
        EpubReaderPage(
            book = book,
            platform = platform,
            bookshelfRepository = bookshelfRepository,
            progressRepository = progressRepository,
            progressSyncScheduler = progressSyncScheduler,
            remoteProgressSource = remoteProgressSource,
            progressSaveScope = progressSaveScope,
            readerContentLoader = readerContentLoader,
            settingsRepository = settingsRepository,
            onProgressSyncFinished = onProgressSyncFinished,
            onBack = onBack,
        )
        return
    }

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
    var paginatedDocument by remember(book?.bookHash) { mutableStateOf<ReaderDocument?>(null) }
    var restoredLocator by remember(book?.bookHash) { mutableStateOf<BookLocator?>(null) }
    var restoredForMeasuredPages by remember(book?.bookHash) { mutableStateOf(false) }
    var pageIndex by remember(book?.bookHash) { mutableIntStateOf(0) }
    var error by remember(book?.bookHash) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(book?.bookHash) { mutableStateOf(false) }
    var measuredPages by remember(book?.bookHash) { mutableStateOf<List<ReaderPageSlice>>(emptyList()) }
    var paginationComplete by remember(book?.bookHash) { mutableStateOf(false) }
    var desktopReaderFocusReady by remember(book?.bookHash) { mutableStateOf(false) }
    var hasUserNavigated by remember(book?.bookHash) { mutableStateOf(false) }
    val nightModeState = rememberReaderNightModeState(
        settingsRepository = settingsRepository,
        bookKey = book?.bookHash.orEmpty(),
    )
    val nightMode = if (nightModeState.followsSystem) {
        MaterialTheme.toreadIsDark
    } else {
        nightModeState.manualNightMode
    }
    // The reading surface paints itself from this, so day/night also switches the paper under the text,
    // and the page's colors come from the palette the reader chose in 主题与色彩.
    val readerAppearance = remember(book?.bookHash) {
        settingsRepository?.appearancePreferences() ?: ToreadAppearancePreferences()
    }
    val readerSurface = readerSurfaceColors(
        nightMode = nightMode,
        highContrastDark = readerAppearance.highContrastDarkTheme,
        palette = readerAppearance.palette,
    )
    var desktopDoublePageEnabled by remember(book?.bookHash) { mutableStateOf(true) }
    var desktopNightModeEnabled by remember(book?.bookHash) { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    fun exitReaderWithoutProgress() {
        if (isAndroid) SystemBarsController.showSystemBars()
        onBack()
    }

    PlatformBackHandler(enabled = isAndroid) {
        val currentBook = book
        val displayedDocument = paginatedDocument ?: document
        if (
            currentBook != null &&
            measuredPages.isNotEmpty()
        ) {
            val targetPage = pageIndex.coerceIn(0, measuredPages.lastIndex.coerceAtLeast(0))
            saveReaderProgress(
                scope = progressSaveScope,
                progressRepository = progressRepository,
                progressSyncScheduler = progressSyncScheduler,
                remoteSyncPolicy = ReaderProgressRemoteSyncPolicy.FinalFlush,
                book = currentBook,
                pageIndex = targetPage,
                pages = measuredPages,
                sourceLength = displayedDocument?.text?.length ?: 0,
                deviceId = platform.name,
                onSyncFinished = onProgressSyncFinished,
            )
        }
        exitReaderWithoutProgress()
    }

    LaunchedEffect(book?.bookHash) {
        if (book == null) return@LaunchedEffect
        document = null
        paginatedDocument = null
        error = null
        restoredLocator = null
        restoredForMeasuredPages = false
        hasUserNavigated = false
        val openingProgress = resolveLocalOpeningProgress(
            book = book,
            progressRepository = progressRepository,
        )
        restoredLocator = openingProgress?.locator
            ?: bookshelfRepository?.progressLocator(book.bookHash)

        try {
            val loaded = withContext(Dispatchers.Default) {
                readerContentLoader.load(book.filePath, book.format, book.title)
            }
            document = loaded
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            error = ToreadStrings.openBookFailed(throwable.message)
        }
    }

    LaunchedEffect(book?.bookHash, remoteProgressSource, progressRepository) {
        if (book == null) return@LaunchedEffect
        val remoteOpeningProgress = resolveRemoteOpeningProgress(
            book = book,
            progressRepository = progressRepository,
            remoteProgressSource = remoteProgressSource,
        ) ?: return@LaunchedEffect

        val remoteLocator = remoteOpeningProgress.locator
        if (!hasUserNavigated && remoteLocator != null) {
            restoredLocator = remoteLocator
            restoredForMeasuredPages = false
        } else {
            ToreadClientLog.info(
                LogArea,
                "reader.open.remote.defer_seek",
                "bookHash" to book.bookHash,
                "source" to remoteOpeningProgress.source,
                "chosenUpdatedAt" to remoteOpeningProgress.updatedAt,
            )
        }
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
            Text(ToreadStrings.Reader, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(ToreadStrings.SelectBookBeforeReading)
            OutlinedButton(onClick = onBack) {
                Text(ToreadStrings.BackToBookshelf)
            }
        }
        return
    }

    val loadedDocument = document

    val readerModifier = Modifier
        .fillMaxSize()

    Box(
        modifier = readerModifier.background(readerSurface.background),
    ) {
        when {
            error != null -> ReaderMessage(message = error.orEmpty(), onBack = ::exitReaderWithoutProgress)
            loadedDocument == null -> ReaderLoadingIndicator()
            !loadedDocument.isReadable -> ReaderMessage(
                message = loadedDocument.message ?: ToreadStrings.UnsupportedReadableFormat,
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

                val desktopPageStep = if (isDesktop && desktopDoublePageEnabled) 2 else 1
                val normalizeForDoublePage = isDesktop && desktopDoublePageEnabled

                LaunchedEffect(loadedDocument.text, spec) {
                    val hadDisplayedPages = paginatedDocument != null && measuredPages.isNotEmpty()
                    if (!hadDisplayedPages) {
                        measuredPages = emptyList()
                        paginationComplete = false
                        desktopReaderFocusReady = false
                    }
                    val nextPages = try {
                        withContext(Dispatchers.Default) {
                            loadedDocument.paginateForReader(spec)
                        }
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        ToreadClientLog.warn(
                            LogArea,
                            "reader.pagination.fail",
                            "bookHash" to book.bookHash,
                            "format" to loadedDocument.format,
                            "error" to throwable.message,
                        )
                        if (!hadDisplayedPages) {
                            error = ToreadStrings.openBookFailed(throwable.message)
                        }
                        return@LaunchedEffect
                    }
                    if (nextPages.isEmpty()) {
                        if (!hadDisplayedPages) {
                            error = ToreadStrings.openBookFailed("分页结果为空")
                        }
                        return@LaunchedEffect
                    }

                    val previousPages = measuredPages
                    val previousPageIndex = pageIndex.coerceIn(
                        0,
                        previousPages.lastIndex.coerceAtLeast(0),
                    )
                    val requestedLocator = if (
                        hasUserNavigated &&
                        previousPages.isNotEmpty()
                    ) {
                        TxtLocator(
                            charOffset = previousPages[previousPageIndex].startOffset.toLong(),
                            globalProgress = if (loadedDocument.text.isNotEmpty()) {
                                previousPages[previousPageIndex]
                                    .startOffset
                                    .toDouble()
                                    .div(loadedDocument.text.length.toDouble())
                                    .coerceIn(0.0, 1.0)
                            } else {
                                previousPageIndex.pageProgress(previousPages.size)
                            },
                        )
                    } else {
                        restoredLocator
                    }
                    val requestedPage = requestedLocator?.toPageIndex(nextPages) ?: 0
                    val normalizeForCurrentPageMode =
                        isDesktop && desktopDoublePageEnabled
                    val normalizedPage = normalizePageIndex(
                        requestedPage,
                        nextPages.size,
                        normalizeForCurrentPageMode,
                    )
                    val chosenPage = nextPages.getOrNull(normalizedPage)

                    Snapshot.withMutableSnapshot {
                        restoredLocator = requestedLocator
                        pageIndex = normalizedPage
                        paginatedDocument = loadedDocument
                        measuredPages = nextPages
                        restoredForMeasuredPages = true
                        paginationComplete = true
                    }
                    ToreadClientLog.info(
                        LogArea,
                        "reader.seek",
                        "bookHash" to book.bookHash,
                        "locator" to requestedLocator.describeLocator(),
                        "targetCharOffset" to requestedLocator.readerCharOffset(),
                        "requestedPage" to requestedPage,
                        "chosenPage" to normalizedPage,
                        "chosenPageStartOffset" to chosenPage?.startOffset,
                        "chosenPageEndOffset" to chosenPage?.endOffset,
                        "pageCount" to nextPages.size,
                        "platform" to if (isDesktop) "desktop" else "android",
                    )
                }

                val pages = measuredPages
                val displayedDocument = paginatedDocument ?: loadedDocument

                LaunchedEffect(
                    book.bookHash,
                    pages.size,
                    restoredForMeasuredPages,
                    normalizeForDoublePage,
                    paginationComplete,
                ) {
                    if (!restoredForMeasuredPages && pages.isNotEmpty() && paginationComplete) {
                        val locator = restoredLocator
                        val requestedPage = locator?.toPageIndex(pages) ?: 0
                        val normalizedPage = normalizePageIndex(
                            requestedPage,
                            pages.size,
                            normalizeForDoublePage,
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
                    remoteSyncPolicy: ReaderProgressRemoteSyncPolicy = ReaderProgressRemoteSyncPolicy.RequestFlush,
                ) {
                    saveReaderProgress(
                        scope = progressSaveScope,
                        progressRepository = progressRepository,
                        progressSyncScheduler = progressSyncScheduler,
                        remoteSyncPolicy = remoteSyncPolicy,
                        book = book,
                        pageIndex = targetPage,
                        pages = pages,
                        sourceLength = displayedDocument.text.length,
                        deviceId = platform.name,
                        onSyncFinished = if (remoteSyncPolicy == ReaderProgressRemoteSyncPolicy.FinalFlush) {
                            onProgressSyncFinished
                        } else {
                            null
                        },
                    )
                }

                fun goToPage(target: Int) {
                    if (paginatedDocument !== displayedDocument || measuredPages !== pages) return
                    if (pages.isEmpty()) return
                    val normalized = normalizePageIndex(target, pages.size, normalizeForDoublePage)
                    if (normalized == pageIndex) return
                    hasUserNavigated = true
                    pageIndex = normalized
                    saveCurrentProgress(normalized)
                    if (isAndroid && !controlsVisible) {
                        SystemBarsController.hideSystemBars()
                    }
                }

                fun exitReader() {
                    val currentDocument = paginatedDocument ?: document
                    val currentPages = measuredPages
                    if (
                        currentDocument != null &&
                        currentPages.isNotEmpty()
                    ) {
                        val targetPage = pageIndex.coerceIn(
                            0,
                            currentPages.lastIndex.coerceAtLeast(0),
                        )
                        saveReaderProgress(
                            scope = progressSaveScope,
                            progressRepository = progressRepository,
                            progressSyncScheduler = progressSyncScheduler,
                            remoteSyncPolicy = ReaderProgressRemoteSyncPolicy.FinalFlush,
                            book = book,
                            pageIndex = targetPage,
                            pages = currentPages,
                            sourceLength = currentDocument.text.length,
                            deviceId = platform.name,
                            onSyncFinished = onProgressSyncFinished,
                        )
                    }
                    exitReaderWithoutProgress()
                }

                fun toggleDesktopPageMode() {
                    val nextDoublePage = !desktopDoublePageEnabled
                    desktopDoublePageEnabled = nextDoublePage
                    val currentPages = measuredPages
                    if (currentPages.isNotEmpty()) {
                        pageIndex = normalizePageIndex(
                            pageIndex,
                            currentPages.size,
                            isDesktop && nextDoublePage,
                        )
                    }
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
                                            goToPage(pageIndex - desktopPageStep)
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            goToPage(pageIndex + desktopPageStep)
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
                            sourceText = displayedDocument.text,
                            pages = pages,
                            pageIndex = currentPageIndex,
                            progress = progress,
                            isDoublePage = desktopDoublePageEnabled,
                            isNightMode = desktopNightModeEnabled,
                            onBack = ::exitReader,
                            onPrevious = { goToPage(pageIndex - desktopPageStep) },
                            onNext = { goToPage(pageIndex + desktopPageStep) },
                            onGoToPage = { targetPage -> goToPage(targetPage) },
                            onTogglePageMode = ::toggleDesktopPageMode,
                            onToggleNightMode = { desktopNightModeEnabled = !desktopNightModeEnabled },
                            onChapterDrawerClosed = {
                                if (desktopReaderFocusReady) {
                                    focusRequester.requestFocus()
                                }
                            },
                        )
                    } else {
                        AndroidReaderLayout(
                            book = book,
                            sourceText = displayedDocument.text,
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
                            chapterPages = chapterPagePosition(pages, currentPageIndex),
                            onSeekChapterPage = { chapterPage ->
                                pageIndexForChapterPage(pages, currentPageIndex, chapterPage)
                                    ?.let(::goToPage)
                            },
                            nightMode = nightMode,
                            onToggleNightMode = nightModeState::toggle,
                            readerSurface = readerSurface,
                            appearance = readerAppearance,
                        )
                    }
                }
            }
        }
    }

    if (nightModeState.isConfirmingManualSwitch) {
        ReaderNightModeConfirmDialog(
            onConfirm = { nightModeState.confirmManualSwitch(nightMode = !nightMode) },
            onDismiss = nightModeState::dismissManualSwitch,
        )
    }
}

@Composable
internal fun ReaderLoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

internal fun resolveLocalOpeningProgress(
    book: BookshelfBook,
    progressRepository: ReadingProgressRepository?,
): ChosenReaderProgress? {
    val bookHash = book.bookHash
    ToreadClientLog.info(LogArea, "reader.open.start", "bookHash" to bookHash)

    val localProgress = progressRepository?.localProgressSnapshot(bookHash)
    val chosen = localProgress?.locator?.let { locator ->
        ChosenReaderProgress(
            source = "local",
            locator = locator,
            updatedAt = localProgress.updatedAt,
            progress = localProgress.progress,
        )
    }

    ToreadClientLog.info(
        LogArea,
        "reader.open.local_choose",
        "bookHash" to bookHash,
        "localUpdatedAt" to localProgress?.updatedAt,
        "localProgress" to localProgress?.progress,
        "localSyncStatus" to localProgress?.syncStatus,
        "localLocator" to localProgress?.locator.describeLocator(),
        "source" to chosen?.source,
    )
    return chosen
}

internal suspend fun resolveRemoteOpeningProgress(
    book: BookshelfBook,
    progressRepository: ReadingProgressRepository?,
    remoteProgressSource: ReadingProgressRemoteSource?,
): ChosenReaderProgress? {
    val bookHash = book.bookHash
    val localProgress = progressRepository?.localProgressSnapshot(bookHash)

    val remoteResult = if (remoteProgressSource == null) {
        RemoteProgressFetchResult.Unavailable("远端进度源未初始化")
    } else {
        withTimeoutOrNull(RemoteProgressFetchTimeoutMillis) {
            remoteProgressSource.fetch(bookHash)
        } ?: RemoteProgressFetchResult.Failed("远端进度请求超时")
    }

    val remoteProgress = (remoteResult as? RemoteProgressFetchResult.Found)?.progress
    ToreadClientLog.info(
        LogArea,
        "reader.open.remote_progress",
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
        else -> null
    }

    ToreadClientLog.info(
        LogArea,
        "reader.open.remote_choose",
        "bookHash" to bookHash,
        "source" to chosen?.source,
        "chosenUpdatedAt" to chosen?.updatedAt,
        "chosenProgress" to chosen?.progress,
        "chosenLocator" to chosen?.locator.describeLocator(),
    )
    return chosen
}

internal data class ChosenReaderProgress(
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
private const val RemoteProgressFetchTimeoutMillis = 2_500L
