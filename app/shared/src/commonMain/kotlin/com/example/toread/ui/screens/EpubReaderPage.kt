package com.example.toread.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.toread.Platform
import com.example.toread.PlatformKind
import com.example.toread.library.BookshelfBook
import com.example.toread.library.BookshelfRepository
import com.example.toread.platform.PlatformBackHandler
import com.example.toread.platform.SystemBarsController
import com.example.toread.platform.rememberSystemBarInsets
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.reader.BookLocator
import com.example.toread.reader.EpubLocator
import com.example.toread.reader.EpubPublicationIndex
import com.example.toread.reader.ReaderContentLoader
import com.example.toread.sync.ProgressSyncScheduler
import com.example.toread.sync.ReadingProgressRemoteSource
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.ui.reader.AndroidReaderLayout
import com.example.toread.ui.reader.DesktopReaderLayout
import com.example.toread.ui.reader.EpubReflowSession
import com.example.toread.ui.reader.EpubReflowState
import com.example.toread.ui.reader.ReaderChapterEntry
import com.example.toread.ui.reader.ReaderChapterPagePosition
import com.example.toread.ui.reader.ReaderMessage
import com.example.toread.ui.reader.ReaderNightModeConfirmDialog
import com.example.toread.ui.reader.ReaderProgressRemoteSyncPolicy
import com.example.toread.ui.reader.rememberReaderNightModeState
import com.example.toread.ui.reader.rememberReaderPaginationSpec
import com.example.toread.ui.reader.saveEpubReaderProgress
import com.example.toread.ui.theme.toreadColors
import com.example.toread.ui.theme.toreadIsDark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun EpubReaderPage(
    book: BookshelfBook,
    platform: Platform,
    bookshelfRepository: BookshelfRepository?,
    progressRepository: ReadingProgressRepository?,
    progressSyncScheduler: ProgressSyncScheduler?,
    remoteProgressSource: ReadingProgressRemoteSource?,
    progressSaveScope: CoroutineScope,
    readerContentLoader: ReaderContentLoader,
    settingsRepository: LocalSettingsRepository? = null,
    onProgressSyncFinished: () -> Unit,
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
    val uiScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var publication by remember(book.bookHash) { mutableStateOf<EpubPublicationIndex?>(null) }
    var requestedLocator by remember(book.bookHash) { mutableStateOf<BookLocator?>(null) }
    var seekGeneration by remember(book.bookHash) { mutableIntStateOf(0) }
    var state by remember(book.bookHash) { mutableStateOf(EpubReflowState()) }
    var error by remember(book.bookHash) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(book.bookHash) { mutableStateOf(false) }
    var hasUserNavigated by remember(book.bookHash) { mutableStateOf(false) }
    val nightModeState = rememberReaderNightModeState(
        settingsRepository = settingsRepository,
        bookKey = book.bookHash,
    )
    val nightMode = if (nightModeState.followsSystem) {
        MaterialTheme.toreadIsDark
    } else {
        nightModeState.manualNightMode
    }
    var desktopDoublePageEnabled by remember(book.bookHash) { mutableStateOf(true) }
    var desktopNightModeEnabled by remember(book.bookHash) { mutableStateOf(true) }
    var desktopReaderFocusReady by remember(book.bookHash) { mutableStateOf(false) }
    var chapterPages by remember(book.bookHash) { mutableStateOf<ReaderChapterPagePosition?>(null) }

    val session = remember(book.bookHash, publication, readerContentLoader) {
        publication?.let { index ->
            EpubReflowSession(
                publication = index,
                chapterLoader = { chapterOrd ->
                    readerContentLoader.loadEpubChapter(
                        filePath = book.filePath,
                        title = book.title,
                        chapterOrd = chapterOrd,
                    )
                },
            )
        }
    }
    val chapterEntries = remember(publication) {
        publication
            ?.chapters
            .orEmpty()
            .map { chapter ->
                ReaderChapterEntry(
                    chapterOrd = chapter.ord,
                    title = chapter.title,
                    pageIndex = 0,
                )
            }
    }

    fun saveLocator(
        locator: EpubLocator?,
        remoteSyncPolicy: ReaderProgressRemoteSyncPolicy,
    ) {
        if (locator == null) return
        saveEpubReaderProgress(
            scope = progressSaveScope,
            progressRepository = progressRepository,
            progressSyncScheduler = progressSyncScheduler,
            remoteSyncPolicy = remoteSyncPolicy,
            book = book,
            locator = locator,
            deviceId = platform.name,
            onSyncFinished = if (remoteSyncPolicy == ReaderProgressRemoteSyncPolicy.FinalFlush) {
                onProgressSyncFinished
            } else {
                null
            },
        )
    }

    fun exitReader() {
        saveLocator(state.locator, ReaderProgressRemoteSyncPolicy.FinalFlush)
        if (isAndroid) SystemBarsController.showSystemBars()
        onBack()
    }

    PlatformBackHandler(enabled = isAndroid, onBack = ::exitReader)

    LaunchedEffect(book.bookHash) {
        publication = null
        requestedLocator = null
        state = EpubReflowState()
        error = null
        hasUserNavigated = false
        val localProgress = resolveLocalOpeningProgress(
            book = book,
            progressRepository = progressRepository,
        )
        requestedLocator = localProgress?.locator
            ?: bookshelfRepository?.progressLocator(book.bookHash)
        seekGeneration++
        try {
            publication = withContext(Dispatchers.Default) {
                readerContentLoader.loadEpubPublicationIndex(
                    filePath = book.filePath,
                    title = book.title,
                )
            } ?: run {
                error = "无法读取 EPUB 目录"
                null
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            error = "打开 EPUB 失败：${throwable.message ?: "未知错误"}"
        }
    }

    LaunchedEffect(book.bookHash, remoteProgressSource, progressRepository) {
        val remote = resolveRemoteOpeningProgress(
            book = book,
            progressRepository = progressRepository,
            remoteProgressSource = remoteProgressSource,
        ) ?: return@LaunchedEffect
        if (!hasUserNavigated && remote.locator is EpubLocator) {
            requestedLocator = remote.locator
            seekGeneration++
        }
    }

    LaunchedEffect(isAndroid, book.bookHash) {
        if (isAndroid) SystemBarsController.hideSystemBars()
    }
    DisposableEffect(isAndroid, book.bookHash) {
        onDispose {
            if (isAndroid) SystemBarsController.showSystemBars()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (nightMode) {
                    MaterialTheme.toreadColors.readerNightPage
                } else {
                    MaterialTheme.colorScheme.background
                },
            ),
    ) {
        val currentPublication = publication
        val currentSession = session
        when {
            error != null -> ReaderMessage(message = error.orEmpty(), onBack = onBack)
            currentPublication == null || currentSession == null -> ReaderLoadingIndicator()
            else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val spec = rememberReaderPaginationSpec(
                    title = currentPublication.title.ifBlank { book.title },
                    isAndroid = isAndroid,
                    isDesktop = isDesktop,
                    maxWidthPx = with(density) { maxWidth.toPx().roundToInt() },
                    maxHeightPx = with(density) { maxHeight.toPx().roundToInt() },
                    topReservedPx = topSystemBarReservePx,
                    bottomReservedPx = bottomGestureReservePx,
                )
                val visiblePages = if (isDesktop && desktopDoublePageEnabled) 2 else 1
                var appliedSeekGeneration by remember(currentSession) { mutableIntStateOf(-1) }
                var opened by remember(currentSession) { mutableStateOf(false) }

                LaunchedEffect(currentSession, spec, visiblePages, seekGeneration) {
                    val shouldApplyRequestedLocator = appliedSeekGeneration != seekGeneration
                    val anchor = if (shouldApplyRequestedLocator) {
                        requestedLocator
                    } else {
                        state.locator ?: requestedLocator
                    }
                    state = try {
                        withContext(Dispatchers.Default) {
                            currentSession.open(
                                locator = anchor,
                                spec = spec,
                                visiblePageCount = visiblePages,
                            )
                        }
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        state.copy(
                            isLoading = false,
                            error = throwable.message ?: "动态排版失败",
                        )
                    }
                    opened = true
                    appliedSeekGeneration = seekGeneration
                }

                LaunchedEffect(
                    currentSession,
                    opened,
                    state.generation,
                    state.currentChapterOrd,
                ) {
                    if (!opened || state.pages.isEmpty()) return@LaunchedEffect
                    val expectedGeneration = state.generation
                    val prefetched = withContext(Dispatchers.Default) {
                        currentSession.preloadFollowingChapter()
                    }
                    if (prefetched.generation == expectedGeneration) {
                        state = prefetched
                    }
                }

                // The chapter page count needs the whole chapter paginated once, so it is measured
                // only while the reading panel is visible and reused until the chapter or spec changes.
                LaunchedEffect(currentSession, controlsVisible, state.currentChapterOrd, state.generation) {
                    if (!controlsVisible) {
                        chapterPages = null
                        return@LaunchedEffect
                    }
                    chapterPages = withContext(Dispatchers.Default) {
                        currentSession.chapterPagePosition()
                    }
                }

                fun navigate(
                    action: suspend EpubReflowSession.() -> EpubReflowState,
                ) {
                    hasUserNavigated = true
                    uiScope.launch {
                        val next = try {
                            withContext(Dispatchers.Default) {
                                currentSession.action()
                            }
                        } catch (throwable: Throwable) {
                            if (throwable is CancellationException) throw throwable
                            state.copy(
                                isLoading = false,
                                error = throwable.message ?: "动态排版失败",
                            )
                        }
                        state = next
                        saveLocator(next.locator, ReaderProgressRemoteSyncPolicy.RequestFlush)
                        if (isAndroid && !controlsVisible) {
                            SystemBarsController.hideSystemBars()
                        }
                    }
                }

                fun jumpToChapter(chapterOrd: Int) {
                    navigate { this.jumpToChapter(chapterOrd) }
                }

                val pages = state.pages.map { it.page }
                val currentPageIndex = state.pageIndex.coerceIn(
                    0,
                    pages.lastIndex.coerceAtLeast(0),
                )
                val contentModifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isDesktop) {
                            Modifier
                                .focusRequester(focusRequester)
                                .onGloballyPositioned { desktopReaderFocusReady = true }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyUp) {
                                        return@onPreviewKeyEvent false
                                    }
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            navigate { previous() }
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            navigate { next() }
                                            true
                                        }
                                        else -> false
                                    }
                                }
                        } else {
                            Modifier
                        },
                    )

                LaunchedEffect(
                    isDesktop,
                    desktopReaderFocusReady,
                    pages.isNotEmpty(),
                    book.bookHash,
                ) {
                    if (isDesktop && desktopReaderFocusReady && pages.isNotEmpty()) {
                        focusRequester.requestFocus()
                    }
                }

                Box(modifier = contentModifier) {
                    when {
                        state.error != null -> ReaderMessage(
                            message = state.error.orEmpty(),
                            onBack = ::exitReader,
                        )
                        state.isLoading || pages.isEmpty() -> ReaderLoadingIndicator()
                        isDesktop -> DesktopReaderLayout(
                            book = book,
                            sourceText = "",
                            pages = pages,
                            pageIndex = currentPageIndex,
                            progress = state.progress,
                            isDoublePage = desktopDoublePageEnabled,
                            isNightMode = desktopNightModeEnabled,
                            onBack = ::exitReader,
                            onPrevious = { navigate { previous() } },
                            onNext = { navigate { next() } },
                            onGoToPage = {},
                            onTogglePageMode = {
                                desktopDoublePageEnabled = !desktopDoublePageEnabled
                            },
                            onToggleNightMode = {
                                desktopNightModeEnabled = !desktopNightModeEnabled
                            },
                            onChapterDrawerClosed = {
                                if (desktopReaderFocusReady) focusRequester.requestFocus()
                            },
                            chapterEntries = chapterEntries,
                            currentChapterOrd = state.currentChapterOrd,
                            canPrevious = state.canPrevious,
                            canNext = state.canNext,
                            onSelectChapterOrd = ::jumpToChapter,
                            showChapterPageNumbers = false,
                        )
                        else -> AndroidReaderLayout(
                            book = book,
                            sourceText = "",
                            pages = pages,
                            pageIndex = currentPageIndex,
                            progress = state.progress,
                            controlsVisible = controlsVisible,
                            onBack = ::exitReader,
                            onPrevious = { navigate { previous() } },
                            onNext = { navigate { next() } },
                            onGoToPage = {},
                            onToggleControls = {
                                controlsVisible = !controlsVisible
                                if (controlsVisible) {
                                    SystemBarsController.showSystemBars()
                                } else {
                                    SystemBarsController.hideSystemBars()
                                }
                            },
                            chapterEntries = chapterEntries,
                            currentChapterOrd = state.currentChapterOrd,
                            canPrevious = state.canPrevious,
                            canNext = state.canNext,
                            onSelectChapterOrd = ::jumpToChapter,
                            chapterPages = chapterPages,
                            onSeekChapterPage = { chapterPage ->
                                navigate { jumpToChapterPage(chapterPage) }
                            },
                            nightMode = nightMode,
                            onToggleNightMode = nightModeState::toggle,
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
