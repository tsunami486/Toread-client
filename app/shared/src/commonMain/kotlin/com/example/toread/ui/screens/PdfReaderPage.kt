package com.example.toread.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.toread.Platform
import com.example.toread.PlatformKind
import com.example.toread.library.BookshelfBook
import com.example.toread.library.BookshelfRepository
import com.example.toread.logging.ToreadClientLog
import com.example.toread.platform.PlatformBackHandler
import com.example.toread.platform.ReaderDeviceControls
import com.example.toread.platform.ReaderOrientationMode
import com.example.toread.platform.ReaderStatusSnapshot
import com.example.toread.platform.ReaderVolumeKey
import com.example.toread.platform.SystemBarsController
import com.example.toread.platform.rememberReaderStatusProvider
import com.example.toread.platform.rememberSystemBarInsets
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.reader.PdfDocumentInfo
import com.example.toread.reader.PdfDocumentRenderer
import com.example.toread.reader.PdfLocator
import com.example.toread.settings.AndroidPdfReaderPreferences
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.settings.PdfBookPreferences
import com.example.toread.settings.PdfOrientationMode
import com.example.toread.settings.PdfReaderMode
import com.example.toread.sync.ProgressSyncScheduler
import com.example.toread.sync.ReadingProgressRemoteSource
import com.example.toread.ui.icons.ToreadSymbolIcon
import com.example.toread.ui.icons.ToreadSymbols
import com.example.toread.ui.reader.PdfPageSpread
import com.example.toread.ui.reader.PdfPanOffset
import com.example.toread.ui.reader.PdfReaderSwipeAction
import com.example.toread.ui.reader.PdfReaderTapAction
import com.example.toread.ui.reader.ReaderProgressRemoteSyncPolicy
import com.example.toread.ui.reader.ReaderMessage
import com.example.toread.ui.reader.clampPdfPan
import com.example.toread.ui.reader.fitPdfPageInside
import com.example.toread.ui.reader.pageProgress
import com.example.toread.ui.reader.pdfContinuousGlobalProgress
import com.example.toread.ui.reader.pdfHorizontalNavigationDelta
import com.example.toread.ui.reader.pdfPanAfterZoomAtPoint
import com.example.toread.ui.reader.pdfPageSpread
import com.example.toread.ui.reader.pdfReaderSwipeAction
import com.example.toread.ui.reader.pdfReaderTapAction
import com.example.toread.ui.reader.pdfRenderOrder
import com.example.toread.ui.reader.pdfSpreadIsResolved
import com.example.toread.ui.reader.pdfSpreadAnchorAt
import com.example.toread.ui.reader.normalizePdfPageIndex
import com.example.toread.ui.reader.savePdfReaderProgress
import com.example.toread.ui.reader.toPdfPageIndex
import com.example.toread.ui.text.ToreadStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun PdfReaderPage(
    book: BookshelfBook,
    platform: Platform,
    bookshelfRepository: BookshelfRepository?,
    progressRepository: ReadingProgressRepository?,
    progressSyncScheduler: ProgressSyncScheduler?,
    remoteProgressSource: ReadingProgressRemoteSource?,
    settingsRepository: LocalSettingsRepository?,
    progressSaveScope: CoroutineScope,
    onProgressSyncFinished: () -> Unit,
    onBack: () -> Unit,
) {
    val isAndroid = platform.kind == PlatformKind.Android
    val isDesktop = platform.kind == PlatformKind.Desktop
    val colorScheme = MaterialTheme.colorScheme
    val palette = PdfReaderPalette(
        background = colorScheme.background,
        toolbarBackground = colorScheme.surface.copy(alpha = 0.94f),
        toolbarText = colorScheme.onSurface,
        secondaryText = colorScheme.onSurfaceVariant,
        disabledText = colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        floatingBackground = colorScheme.inverseSurface,
        floatingText = colorScheme.inverseOnSurface,
    )
    val renderer = remember(book.bookHash) { PdfDocumentRenderer() }
    val readerStatusProvider = rememberReaderStatusProvider()
    val systemBarInsets = rememberSystemBarInsets()
    val windowSize = LocalWindowInfo.current.containerSize
    val isWindowLandscape = windowSize.width > windowSize.height
    val initialAndroidPreferences = remember(book.bookHash, settingsRepository) {
        if (isAndroid) {
            settingsRepository?.androidPdfReaderPreferences()
                ?: AndroidPdfReaderPreferences()
        } else {
            AndroidPdfReaderPreferences()
        }
    }
    val initialBookPreferences = remember(book.bookHash, settingsRepository) {
        if (isAndroid) {
            settingsRepository?.pdfBookPreferences(book.bookHash)
                ?: PdfBookPreferences()
        } else {
            PdfBookPreferences()
        }
    }
    var documentInfo by remember(book.bookHash) { mutableStateOf<PdfDocumentInfo?>(null) }
    var restoredProgress by remember(book.bookHash) { mutableStateOf<ChosenReaderProgress?>(null) }
    var pageIndex by remember(book.bookHash) { mutableIntStateOf(0) }
    var displayedPageIndex by remember(book.bookHash) { mutableIntStateOf(0) }
    var displayedPageIndices by remember(book.bookHash) { mutableStateOf(listOf(0)) }
    var displayedPageOffsetY by remember(book.bookHash) { mutableStateOf(0.0) }
    var continuousTargetPageOffsetY by remember(book.bookHash) { mutableStateOf(0.0) }
    var hasCommittedDisplay by remember(book.bookHash) { mutableStateOf(false) }
    var openError by remember(book.bookHash) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(book.bookHash) { mutableStateOf(false) }
    var androidSettingsPanelVisible by remember(book.bookHash) { mutableStateOf(false) }
    var androidAutoPageTurning by remember(book.bookHash) { mutableStateOf(false) }
    var androidSliderValue by remember(book.bookHash) { mutableFloatStateOf(0f) }
    var androidContinuousSeekGeneration by remember(book.bookHash) { mutableIntStateOf(0) }
    var androidContinuousRetryGeneration by remember(book.bookHash) { mutableIntStateOf(0) }
    var androidInteractionGeneration by remember(book.bookHash) { mutableIntStateOf(0) }
    var androidZoomActive by remember(book.bookHash) { mutableStateOf(false) }
    var androidZoomDismissGeneration by remember(book.bookHash) { mutableIntStateOf(0) }
    var androidReaderStatus by remember(book.bookHash) {
        mutableStateOf<ReaderStatusSnapshot?>(null)
    }
    var desktopDoublePageEnabled by remember(book.bookHash) { mutableStateOf(isDesktop) }
    var desktopRightToLeftEnabled by remember(book.bookHash) { mutableStateOf(false) }
    var desktopShowCoverAlone by remember(book.bookHash) { mutableStateOf(isDesktop) }
    var androidLandscapeDoublePageEnabled by remember(
        book.bookHash,
        settingsRepository,
    ) {
        mutableStateOf(initialAndroidPreferences.landscapeDoublePageEnabled)
    }
    var androidRightToLeftEnabled by remember(book.bookHash, settingsRepository) {
        mutableStateOf(initialBookPreferences.rightToLeft)
    }
    var androidShowCoverAlone by remember(book.bookHash, settingsRepository) {
        mutableStateOf(initialBookPreferences.coverAlone)
    }
    var androidOrientationMode by remember(book.bookHash, settingsRepository) {
        mutableStateOf(initialAndroidPreferences.orientationMode)
    }
    var androidVolumeKeysEnabled by remember(book.bookHash, settingsRepository) {
        mutableStateOf(initialAndroidPreferences.volumeKeysEnabled)
    }
    var androidTapToTurnPagesEnabled by remember(book.bookHash, settingsRepository) {
        mutableStateOf(initialAndroidPreferences.tapToTurnPagesEnabled)
    }
    var androidReverseTapToTurnPages by remember(book.bookHash, settingsRepository) {
        mutableStateOf(initialAndroidPreferences.reverseTapToTurnPages)
    }
    var androidPageAnimationEnabled by remember(book.bookHash, settingsRepository) {
        mutableStateOf(initialAndroidPreferences.pageAnimationEnabled)
    }
    var androidDoubleTapZoomEnabled by remember(book.bookHash, settingsRepository) {
        mutableStateOf(initialAndroidPreferences.doubleTapZoomEnabled)
    }
    var androidLongPressZoomEnabled by remember(book.bookHash, settingsRepository) {
        mutableStateOf(initialAndroidPreferences.longPressZoomEnabled)
    }
    var androidShowPageNumber by remember(book.bookHash, settingsRepository) {
        mutableStateOf(initialAndroidPreferences.showPageNumber)
    }
    var androidShowStatusInfo by remember(book.bookHash, settingsRepository) {
        mutableStateOf(initialAndroidPreferences.showStatusInfo)
    }
    var androidAutoPageTurningIntervalSeconds by remember(book.bookHash, settingsRepository) {
        mutableIntStateOf(initialAndroidPreferences.autoPageTurningIntervalSeconds)
    }
    var androidReaderMode by remember(book.bookHash, settingsRepository) {
        mutableStateOf(initialBookPreferences.readerMode)
    }
    var hasUserNavigated by remember(book.bookHash) { mutableStateOf(false) }
    var prefetchDirection by remember(book.bookHash) { mutableIntStateOf(1) }
    var desktopFocusReady by remember(book.bookHash) { mutableStateOf(false) }
    val desktopFocusRequester = remember(book.bookHash) { FocusRequester() }
    fun currentDoublePageEnabled(): Boolean = when {
        isDesktop -> desktopDoublePageEnabled
        isAndroid ->
            androidReaderMode != PdfReaderMode.ContinuousVertical &&
                isWindowLandscape &&
                androidLandscapeDoublePageEnabled
        else -> false
    }
    fun currentRightToLeftEnabled(): Boolean = when {
        isDesktop -> desktopRightToLeftEnabled
        isAndroid -> androidRightToLeftEnabled
        else -> false
    }
    fun currentCoverAloneEnabled(): Boolean = when {
        isDesktop -> desktopShowCoverAlone
        isAndroid ->
            androidReaderMode != PdfReaderMode.ContinuousVertical &&
                androidShowCoverAlone
        else -> false
    }
    val isEffectiveDoublePage = currentDoublePageEnabled()
    val isEffectiveRightToLeft = currentRightToLeftEnabled()
    val isEffectiveCoverAlone = currentCoverAloneEnabled()

    fun spreadFor(
        pageCount: Int,
        targetPageIndex: Int = pageIndex,
        isDoublePage: Boolean = currentDoublePageEnabled(),
        showCoverAlone: Boolean = currentCoverAloneEnabled(),
    ): PdfPageSpread = pdfPageSpread(
        pageIndex = targetPageIndex,
        pageCount = pageCount,
        isDoublePage = isDoublePage,
        showCoverAlone = showCoverAlone,
        rightToLeft = currentRightToLeftEnabled(),
    )

    fun savePositionProgress(
        anchorPageIndex: Int,
        progressPageIndex: Int,
        pageOffsetY: Double,
        remoteSyncPolicy: ReaderProgressRemoteSyncPolicy,
    ) {
        val pageCount = documentInfo?.pageCount ?: return
        savePdfReaderProgress(
            scope = progressSaveScope,
            progressRepository = progressRepository,
            progressSyncScheduler = progressSyncScheduler,
            remoteSyncPolicy = remoteSyncPolicy,
            book = book,
            pageIndex = anchorPageIndex,
            pageCount = pageCount,
            progressPageIndex = progressPageIndex,
            pageOffsetY = pageOffsetY,
            globalProgressOverride = if (
                isAndroid &&
                androidReaderMode == PdfReaderMode.ContinuousVertical
            ) {
                pdfContinuousGlobalProgress(
                    pageIndex = anchorPageIndex,
                    pageOffsetY = pageOffsetY,
                    pageCount = pageCount,
                )
            } else {
                null
            },
            deviceId = platform.name,
            onSyncFinished = if (remoteSyncPolicy == ReaderProgressRemoteSyncPolicy.Immediate) {
                onProgressSyncFinished
            } else {
                null
            },
        )
    }

    fun saveCurrentProgress(
        remoteSyncPolicy: ReaderProgressRemoteSyncPolicy,
    ) {
        val pageCount = documentInfo?.pageCount ?: return
        if (isAndroid && androidReaderMode == PdfReaderMode.ContinuousVertical) {
            val anchor = if (hasCommittedDisplay) displayedPageIndex else pageIndex
            savePositionProgress(
                anchorPageIndex = anchor,
                progressPageIndex = anchor,
                pageOffsetY = if (hasCommittedDisplay) displayedPageOffsetY else 0.0,
                remoteSyncPolicy = remoteSyncPolicy,
            )
        } else {
            val anchor = if (isAndroid && hasCommittedDisplay) {
                displayedPageIndex
            } else {
                pageIndex
            }
            val spread = spreadFor(pageCount, targetPageIndex = anchor)
            savePositionProgress(
                anchorPageIndex = spread.anchorPageIndex,
                progressPageIndex = spread.progressPageIndex,
                pageOffsetY = 0.0,
                remoteSyncPolicy = remoteSyncPolicy,
            )
        }
    }

    fun exitReader() {
        saveCurrentProgress(ReaderProgressRemoteSyncPolicy.Immediate)
        if (isAndroid) SystemBarsController.showSystemBars()
        onBack()
    }

    fun goToPage(
        target: Int,
        navigationDirection: Int = 0,
        targetPageOffsetY: Double = 0.0,
    ) {
        val pageCount = documentInfo?.pageCount ?: return
        val normalized = normalizePdfPageIndex(
            index = target,
            pageCount = pageCount,
            isDoublePage = currentDoublePageEnabled(),
            showCoverAlone = currentCoverAloneEnabled(),
        )
        val isContinuousSeek =
            isAndroid && androidReaderMode == PdfReaderMode.ContinuousVertical
        val currentContinuousAnchor = if (hasCommittedDisplay) {
            displayedPageIndex
        } else {
            pageIndex
        }
        val currentContinuousOffset = if (hasCommittedDisplay) {
            displayedPageOffsetY
        } else {
            continuousTargetPageOffsetY
        }
        if (
            (
                !isContinuousSeek &&
                    normalized == pageIndex
                ) ||
            (
                isContinuousSeek &&
                    normalized == currentContinuousAnchor &&
                    targetPageOffsetY == currentContinuousOffset
                )
        ) {
            return
        }
        if (navigationDirection != 0) {
            prefetchDirection = if (navigationDirection < 0) -1 else 1
        }
        hasUserNavigated = true
        pageIndex = normalized
        if (isAndroid) {
            androidInteractionGeneration++
            androidZoomActive = false
            androidZoomDismissGeneration++
        }
        if (isContinuousSeek) {
            continuousTargetPageOffsetY = targetPageOffsetY.coerceIn(0.0, 1.0)
            androidContinuousSeekGeneration++
        }
        if (isDesktop) {
            saveCurrentProgress(ReaderProgressRemoteSyncPolicy.Debounced)
        }
        if (isAndroid && !controlsVisible) {
            SystemBarsController.hideSystemBars()
        }
    }

    fun goToSpread(targetSpreadIndex: Int) {
        val pageCount = documentInfo?.pageCount ?: return
        val currentSpread = spreadFor(
            pageCount = pageCount,
            targetPageIndex = if (isAndroid && hasCommittedDisplay) {
                displayedPageIndex
            } else {
                pageIndex
            },
        )
        val boundedTarget = targetSpreadIndex.coerceIn(0, (currentSpread.spreadCount - 1).coerceAtLeast(0))
        val targetPage = pdfSpreadAnchorAt(
            spreadIndex = boundedTarget,
            pageCount = pageCount,
            isDoublePage = currentDoublePageEnabled(),
            showCoverAlone = currentCoverAloneEnabled(),
        )
        goToPage(
            target = targetPage,
            navigationDirection = boundedTarget.compareTo(currentSpread.spreadIndex),
        )
    }

    fun moveBySpread(delta: Int) {
        val pageCount = documentInfo?.pageCount ?: return
        val spread = spreadFor(
            pageCount = pageCount,
            targetPageIndex = if (isAndroid && hasCommittedDisplay) {
                displayedPageIndex
            } else {
                pageIndex
            },
        )
        goToSpread(spread.spreadIndex + delta)
    }

    fun toggleDesktopPageMode() {
        val pageCount = documentInfo?.pageCount ?: return
        val nextDoublePage = !desktopDoublePageEnabled
        desktopDoublePageEnabled = nextDoublePage
        hasUserNavigated = true
        pageIndex = normalizePdfPageIndex(
            index = pageIndex,
            pageCount = pageCount,
            isDoublePage = nextDoublePage,
            showCoverAlone = desktopShowCoverAlone,
        )
        saveCurrentProgress(ReaderProgressRemoteSyncPolicy.Debounced)
    }

    fun toggleDesktopReadingDirection() {
        desktopRightToLeftEnabled = !desktopRightToLeftEnabled
    }

    fun toggleDesktopCoverMode() {
        val pageCount = documentInfo?.pageCount ?: return
        desktopShowCoverAlone = !desktopShowCoverAlone
        hasUserNavigated = true
        pageIndex = normalizePdfPageIndex(
            index = pageIndex,
            pageCount = pageCount,
            isDoublePage = desktopDoublePageEnabled,
            showCoverAlone = desktopShowCoverAlone,
        )
        saveCurrentProgress(ReaderProgressRemoteSyncPolicy.Debounced)
    }

    fun setAndroidLandscapePageMode(enabled: Boolean) {
        androidLandscapeDoublePageEnabled = enabled
        settingsRepository?.setPdfAndroidLandscapeDoublePageEnabled(enabled)
        if (
            isWindowLandscape &&
            androidReaderMode != PdfReaderMode.ContinuousVertical
        ) {
            val pageCount = documentInfo?.pageCount ?: return
            pageIndex = normalizePdfPageIndex(
                index = if (hasCommittedDisplay) displayedPageIndex else pageIndex,
                pageCount = pageCount,
                isDoublePage = enabled,
                showCoverAlone = androidShowCoverAlone,
            )
        }
    }

    fun toggleAndroidLandscapePageMode() {
        setAndroidLandscapePageMode(!androidLandscapeDoublePageEnabled)
    }

    fun setAndroidReadingDirection(enabled: Boolean) {
        androidRightToLeftEnabled = enabled
        settingsRepository?.setPdfBookRightToLeft(book.bookHash, enabled)
    }

    fun toggleAndroidReadingDirection() {
        setAndroidReadingDirection(!androidRightToLeftEnabled)
    }

    fun setAndroidCoverMode(enabled: Boolean) {
        androidShowCoverAlone = enabled
        settingsRepository?.setPdfBookCoverAlone(book.bookHash, enabled)
        if (isEffectiveDoublePage) {
            val pageCount = documentInfo?.pageCount ?: return
            pageIndex = normalizePdfPageIndex(
                index = if (hasCommittedDisplay) displayedPageIndex else pageIndex,
                pageCount = pageCount,
                isDoublePage = true,
                showCoverAlone = enabled,
            )
        }
    }

    fun toggleAndroidCoverMode() {
        setAndroidCoverMode(!androidShowCoverAlone)
    }

    fun setAndroidOrientationMode(mode: PdfOrientationMode) {
        androidOrientationMode = mode
        settingsRepository?.setPdfAndroidOrientationMode(mode)
    }

    fun cycleAndroidOrientationMode() {
        setAndroidOrientationMode(
            when (androidOrientationMode) {
                PdfOrientationMode.System -> PdfOrientationMode.Portrait
                PdfOrientationMode.Portrait -> PdfOrientationMode.Landscape
                PdfOrientationMode.Landscape -> PdfOrientationMode.System
            },
        )
    }

    fun setAndroidVolumeKeys(enabled: Boolean) {
        androidVolumeKeysEnabled = enabled
        settingsRepository?.setPdfAndroidVolumeKeysEnabled(enabled)
    }

    fun toggleAndroidVolumeKeys() {
        setAndroidVolumeKeys(!androidVolumeKeysEnabled)
    }

    fun setAndroidReaderMode(mode: PdfReaderMode) {
        if (mode == androidReaderMode) return
        androidAutoPageTurning = false
        androidZoomActive = false
        androidZoomDismissGeneration++
        androidReaderMode = mode
        settingsRepository?.setPdfBookReaderMode(book.bookHash, mode)
        val pageCount = documentInfo?.pageCount ?: return
        val currentPhysicalPage = if (hasCommittedDisplay) displayedPageIndex else pageIndex
        pageIndex = normalizePdfPageIndex(
            index = currentPhysicalPage,
            pageCount = pageCount,
            isDoublePage = currentDoublePageEnabled(),
            showCoverAlone = currentCoverAloneEnabled(),
        )
        displayedPageOffsetY = 0.0
        continuousTargetPageOffsetY = 0.0
        if (mode == PdfReaderMode.ContinuousVertical) {
            androidContinuousSeekGeneration++
        }
    }

    fun moveByReadingUnit(delta: Int) {
        if (androidReaderMode == PdfReaderMode.ContinuousVertical) {
            val pageCount = documentInfo?.pageCount ?: return
            val currentPage = if (hasCommittedDisplay) displayedPageIndex else pageIndex
            goToPage(
                target = (currentPage + delta).coerceIn(0, pageCount - 1),
                navigationDirection = delta,
            )
        } else {
            moveBySpread(delta)
        }
    }

    ReaderDeviceControls(
        enabled = isAndroid,
        orientationMode = androidOrientationMode.toReaderOrientationMode(),
        volumeKeysEnabled = androidVolumeKeysEnabled,
        onVolumeKey = { key ->
            when (key) {
                ReaderVolumeKey.Down -> moveByReadingUnit(1)
                ReaderVolumeKey.Up -> moveByReadingUnit(-1)
            }
        },
        onAppBackground = {
            androidAutoPageTurning = false
            androidZoomActive = false
            androidZoomDismissGeneration++
            saveCurrentProgress(ReaderProgressRemoteSyncPolicy.Immediate)
        },
    )

    PlatformBackHandler(enabled = isAndroid) {
        when {
            androidSettingsPanelVisible -> androidSettingsPanelVisible = false
            androidZoomActive -> {
                androidZoomActive = false
                androidZoomDismissGeneration++
            }
            else -> exitReader()
        }
    }

    LaunchedEffect(book.bookHash) {
        documentInfo = null
        openError = null
        pageIndex = 0
        displayedPageIndex = 0
        displayedPageIndices = listOf(0)
        displayedPageOffsetY = 0.0
        hasCommittedDisplay = false
        androidSettingsPanelVisible = false
        androidAutoPageTurning = false
        androidSliderValue = 0f
        androidContinuousSeekGeneration = 0
        androidContinuousRetryGeneration = 0
        androidInteractionGeneration = 0
        androidZoomActive = false
        androidZoomDismissGeneration = 0
        androidReaderStatus = null
        hasUserNavigated = false
        restoredProgress = resolveLocalOpeningProgress(
            book = book,
            progressRepository = progressRepository,
        ) ?: bookshelfRepository?.progressLocator(book.bookHash)?.let { locator ->
            ChosenReaderProgress(
                source = "bookshelf",
                locator = locator,
                updatedAt = null,
                progress = locator.globalProgress,
            )
        }

        runCatching { renderer.open(book.filePath) }
            .onSuccess { info ->
                documentInfo = info
            }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                openError = ToreadStrings.openBookFailed(throwable.message)
            }
    }

    LaunchedEffect(book.bookHash, remoteProgressSource, progressRepository) {
        val remote = resolveRemoteOpeningProgress(
            book = book,
            progressRepository = progressRepository,
            remoteProgressSource = remoteProgressSource,
        ) ?: return@LaunchedEffect
        if (!hasUserNavigated) {
            restoredProgress = remote
        } else {
            ToreadClientLog.info(
                PdfLogArea,
                "reader.open.remote.defer_seek",
                "bookHash" to book.bookHash,
                "source" to remote.source,
                "chosenUpdatedAt" to remote.updatedAt,
            )
        }
    }

    LaunchedEffect(
        documentInfo?.pageCount,
        restoredProgress?.locator,
        isEffectiveDoublePage,
        isEffectiveCoverAlone,
        androidReaderMode,
    ) {
        val pageCount = documentInfo?.pageCount ?: return@LaunchedEffect
        val requestedPage = if (hasUserNavigated) {
            pageIndex
        } else {
            restoredProgress?.locator?.toPdfPageIndex(pageCount) ?: 0
        }
        val chosenPage = normalizePdfPageIndex(
            index = requestedPage,
            pageCount = pageCount,
            isDoublePage = isEffectiveDoublePage,
            showCoverAlone = isEffectiveCoverAlone,
        )
        if (
            isAndroid &&
            androidZoomActive &&
            (!hasCommittedDisplay || chosenPage != displayedPageIndex)
        ) {
            androidZoomActive = false
            androidZoomDismissGeneration++
        }
        pageIndex = chosenPage
        if (isAndroid && androidReaderMode == PdfReaderMode.ContinuousVertical) {
            continuousTargetPageOffsetY = if (hasUserNavigated) {
                continuousTargetPageOffsetY
            } else {
                (restoredProgress?.locator as? PdfLocator)
                    ?.pageOffsetY
                    ?.coerceIn(0.0, 1.0)
                    ?: 0.0
            }
            androidContinuousSeekGeneration++
        }
        ToreadClientLog.info(
            PdfLogArea,
            "reader.seek",
            "bookHash" to book.bookHash,
            "format" to "pdf",
            "requestedPage" to requestedPage,
            "chosenPage" to chosenPage,
            "pageCount" to pageCount,
            "platform" to if (isDesktop) "desktop" else "android",
        )
    }

    LaunchedEffect(isAndroid, book.bookHash) {
        if (isAndroid) SystemBarsController.hideSystemBars()
    }

    LaunchedEffect(isAndroid, androidShowStatusInfo, readerStatusProvider) {
        if (!isAndroid || !androidShowStatusInfo) {
            androidReaderStatus = null
            return@LaunchedEffect
        }
        while (true) {
            androidReaderStatus = readerStatusProvider.currentStatus()
            delay(PdfReaderDemoConfig.AndroidStatusRefreshMillis)
        }
    }

    LaunchedEffect(
        isAndroid,
        controlsVisible,
        androidSettingsPanelVisible,
        androidInteractionGeneration,
    ) {
        if (!isAndroid || !controlsVisible || androidSettingsPanelVisible) {
            return@LaunchedEffect
        }
        delay(PdfReaderDemoConfig.AndroidControlsAutoHideMillis)
        controlsVisible = false
        SystemBarsController.hideSystemBars()
    }

    LaunchedEffect(
        isAndroid,
        androidAutoPageTurning,
        androidAutoPageTurningIntervalSeconds,
        displayedPageIndex,
        displayedPageIndices,
        androidReaderMode,
        androidInteractionGeneration,
        androidZoomActive,
        isEffectiveDoublePage,
        isEffectiveCoverAlone,
        documentInfo?.pageCount,
    ) {
        if (!isAndroid || !androidAutoPageTurning || androidZoomActive) {
            return@LaunchedEffect
        }
        val pageCount = documentInfo?.pageCount ?: return@LaunchedEffect
        delay(androidAutoPageTurningIntervalSeconds * 1_000L)
        val canAdvance = if (androidReaderMode == PdfReaderMode.ContinuousVertical) {
            displayedPageIndex < pageCount - 1
        } else {
            val current = spreadFor(pageCount, targetPageIndex = displayedPageIndex)
            current.spreadIndex < current.spreadCount - 1
        }
        if (canAdvance) {
            moveByReadingUnit(1)
        } else {
            androidAutoPageTurning = false
        }
    }

    LaunchedEffect(isDesktop, desktopFocusReady, documentInfo?.pageCount) {
        if (isDesktop && desktopFocusReady && documentInfo != null) {
            desktopFocusRequester.requestFocus()
        }
    }

    DisposableEffect(renderer, isAndroid) {
        onDispose {
            progressSaveScope.launch(Dispatchers.IO) {
                renderer.close()
            }
            if (isAndroid) SystemBarsController.showSystemBars()
        }
    }

    val info = documentInfo
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .then(
                if (isDesktop) {
                    Modifier
                        .focusRequester(desktopFocusRequester)
                        .onGloballyPositioned { desktopFocusReady = true }
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Escape -> {
                                    exitReader()
                                    true
                                }
                                Key.DirectionLeft -> {
                                    moveBySpread(
                                        pdfHorizontalNavigationDelta(
                                            moveRight = false,
                                            rightToLeft = desktopRightToLeftEnabled,
                                        ),
                                    )
                                    true
                                }
                                Key.DirectionRight -> {
                                    moveBySpread(
                                        pdfHorizontalNavigationDelta(
                                            moveRight = true,
                                            rightToLeft = desktopRightToLeftEnabled,
                                        ),
                                    )
                                    true
                                }
                                else -> false
                            }
                        }
                        .pointerInput(
                            pageIndex,
                            desktopDoublePageEnabled,
                            desktopShowCoverAlone,
                        ) {
                            var accumulatedDelta = 0f
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type != PointerEventType.Scroll) continue
                                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    accumulatedDelta += delta
                                    if (
                                        abs(accumulatedDelta) >=
                                        PdfReaderDemoConfig.MouseWheelTurnThreshold
                                    ) {
                                        moveBySpread(if (accumulatedDelta > 0f) 1 else -1)
                                        accumulatedDelta = 0f
                                    }
                                }
                            }
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        when {
            openError != null -> ReaderMessage(message = openError.orEmpty(), onBack = ::exitReader)
            info == null -> PdfLoadingIndicator()
            else -> {
                if (isDesktop) {
                    val spread = spreadFor(info.pageCount)
                    val previousSpreadIndex = (spread.spreadIndex - 1).coerceAtLeast(0)
                    val nextSpreadIndex = (spread.spreadIndex + 1)
                        .coerceAtMost((spread.spreadCount - 1).coerceAtLeast(0))
                    val canGoPrevious = previousSpreadIndex != spread.spreadIndex
                    val canGoNext = nextSpreadIndex != spread.spreadIndex
                    val horizontalLeftDelta = pdfHorizontalNavigationDelta(
                        moveRight = false,
                        rightToLeft = isEffectiveRightToLeft,
                    )
                    val horizontalRightDelta = -horizontalLeftDelta
                    PdfReaderViewport(
                        renderer = renderer,
                        documentInfo = info,
                        spread = spread,
                        isDoublePage = isEffectiveDoublePage,
                        isAndroid = false,
                        showCoverAlone = isEffectiveCoverAlone,
                        prefetchDirection = prefetchDirection,
                        palette = palette,
                        controlsVisible = controlsVisible,
                        canGoLeft = if (horizontalLeftDelta < 0) {
                            canGoPrevious
                        } else {
                            canGoNext
                        },
                        canGoRight = if (horizontalRightDelta < 0) {
                            canGoPrevious
                        } else {
                            canGoNext
                        },
                        onLeft = { moveBySpread(horizontalLeftDelta) },
                        onRight = { moveBySpread(horizontalRightDelta) },
                        onToggleControls = {
                            controlsVisible = !controlsVisible
                        },
                    )
                    PdfReaderControls(
                        modifier = Modifier.fillMaxSize(),
                        book = book,
                        spread = spread,
                        pageCount = info.pageCount,
                        isDesktop = true,
                        isDoublePage = isEffectiveDoublePage,
                        isRightToLeft = isEffectiveRightToLeft,
                        showCoverAlone = isEffectiveCoverAlone,
                        androidLandscapeDoublePageEnabled =
                            androidLandscapeDoublePageEnabled,
                        androidOrientationMode = androidOrientationMode,
                        androidVolumeKeysEnabled = androidVolumeKeysEnabled,
                        palette = palette,
                        visible = controlsVisible,
                        bottomInset = 0.dp,
                        canGoPrevious = canGoPrevious,
                        canGoNext = canGoNext,
                        onBack = ::exitReader,
                        onFirst = { goToSpread(0) },
                        onPrevious = { goToSpread(previousSpreadIndex) },
                        onNext = { goToSpread(nextSpreadIndex) },
                        onLast = { goToSpread(spread.spreadCount - 1) },
                        onSelectSpread = ::goToSpread,
                        onTogglePageMode = ::toggleDesktopPageMode,
                        onToggleReadingDirection = ::toggleDesktopReadingDirection,
                        onToggleCoverMode = ::toggleDesktopCoverMode,
                        onCycleAndroidOrientationMode = ::cycleAndroidOrientationMode,
                        onToggleAndroidVolumeKeys = ::toggleAndroidVolumeKeys,
                    )
                } else {
                    val navigationPageIndex = if (hasCommittedDisplay) {
                        displayedPageIndex
                    } else {
                        pageIndex
                    }
                    val navigationSpread = spreadFor(
                        pageCount = info.pageCount,
                        targetPageIndex = navigationPageIndex,
                    )
                    val usesPhysicalPageUnits =
                        androidReaderMode == PdfReaderMode.ContinuousVertical
                    val navigationUnitIndex = if (usesPhysicalPageUnits) {
                        navigationPageIndex
                    } else {
                        navigationSpread.spreadIndex
                    }
                    val navigationUnitCount = if (usesPhysicalPageUnits) {
                        info.pageCount
                    } else {
                        navigationSpread.spreadCount
                    }.coerceAtLeast(1)
                    val canGoPrevious = navigationUnitIndex > 0
                    val canGoNext = navigationUnitIndex < navigationUnitCount - 1

                    LaunchedEffect(
                        navigationUnitIndex,
                        navigationUnitCount,
                        androidReaderMode,
                    ) {
                        androidSliderValue = navigationUnitIndex.toFloat()
                    }

                    fun requestNavigationUnit(targetUnit: Int) {
                        val boundedTarget = targetUnit.coerceIn(0, navigationUnitCount - 1)
                        if (usesPhysicalPageUnits) {
                            goToPage(
                                target = boundedTarget,
                                navigationDirection = boundedTarget.compareTo(navigationUnitIndex),
                            )
                        } else {
                            goToSpread(boundedTarget)
                        }
                    }

                    fun setControlsVisibility(visible: Boolean) {
                        controlsVisible = visible
                        androidInteractionGeneration++
                        if (visible) {
                            SystemBarsController.showSystemBars()
                        } else {
                            androidSettingsPanelVisible = false
                            SystemBarsController.hideSystemBars()
                        }
                    }

                    val toggleControls = {
                        setControlsVisibility(!controlsVisible)
                    }
                    val currentAndroidPreferences = AndroidPdfReaderPreferences(
                        landscapeDoublePageEnabled = androidLandscapeDoublePageEnabled,
                        volumeKeysEnabled = androidVolumeKeysEnabled,
                        orientationMode = androidOrientationMode,
                        tapToTurnPagesEnabled = androidTapToTurnPagesEnabled,
                        reverseTapToTurnPages = androidReverseTapToTurnPages,
                        pageAnimationEnabled = androidPageAnimationEnabled,
                        doubleTapZoomEnabled = androidDoubleTapZoomEnabled,
                        longPressZoomEnabled = androidLongPressZoomEnabled,
                        showPageNumber = androidShowPageNumber,
                        showStatusInfo = androidShowStatusInfo,
                        autoPageTurningIntervalSeconds =
                            androidAutoPageTurningIntervalSeconds,
                    )
                    val currentBookPreferences = PdfBookPreferences(
                        rightToLeft = androidRightToLeftEnabled,
                        coverAlone = androidShowCoverAlone,
                        readerMode = androidReaderMode,
                    )
                    val boundedPreviewUnit = androidSliderValue
                        .roundToInt()
                        .coerceIn(0, navigationUnitCount - 1)
                    val previewPageIndices = if (usesPhysicalPageUnits) {
                        listOf(boundedPreviewUnit)
                    } else {
                        val previewAnchor = pdfSpreadAnchorAt(
                            spreadIndex = boundedPreviewUnit,
                            pageCount = info.pageCount,
                            isDoublePage = isEffectiveDoublePage,
                            showCoverAlone = isEffectiveCoverAlone,
                        )
                        spreadFor(
                            pageCount = info.pageCount,
                            targetPageIndex = previewAnchor,
                        ).pageIndices
                    }

                    if (androidReaderMode == PdfReaderMode.ContinuousVertical) {
                        AndroidPdfContinuousViewport(
                            renderer = renderer,
                            pageCount = info.pageCount,
                            targetPageIndex = pageIndex,
                            targetPageOffsetY = continuousTargetPageOffsetY,
                            seekGeneration = androidContinuousSeekGeneration,
                            zoomDismissGeneration = androidZoomDismissGeneration,
                            renderRetryGeneration = androidContinuousRetryGeneration,
                            showPageNumber = false,
                            doubleTapZoomEnabled = androidDoubleTapZoomEnabled,
                            longPressZoomEnabled = androidLongPressZoomEnabled,
                            onToggleControls = toggleControls,
                            onUserInteraction = {
                                hasUserNavigated = true
                                androidInteractionGeneration++
                            },
                            onZoomActiveChange = { active ->
                                androidZoomActive = active
                            },
                            onPositionChanged = { positionPageIndex, pageOffsetY, settled ->
                                val pageChanged =
                                    !hasCommittedDisplay ||
                                        displayedPageIndex != positionPageIndex
                                displayedPageIndex = positionPageIndex
                                displayedPageIndices = listOf(positionPageIndex)
                                displayedPageOffsetY = pageOffsetY
                                hasCommittedDisplay = true
                                if (hasUserNavigated && (pageChanged || settled)) {
                                    savePositionProgress(
                                        anchorPageIndex = positionPageIndex,
                                        progressPageIndex = positionPageIndex,
                                        pageOffsetY = pageOffsetY,
                                        remoteSyncPolicy =
                                            ReaderProgressRemoteSyncPolicy.Debounced,
                                    )
                                }
                            },
                        )
                    } else {
                        AndroidPdfPagedViewport(
                            renderer = renderer,
                            state = AndroidPdfPagedViewportState(
                                pageCount = info.pageCount,
                                requestedPageIndex = pageIndex,
                                isDoublePage = isEffectiveDoublePage,
                                showCoverAlone = isEffectiveCoverAlone,
                                rightToLeft = isEffectiveRightToLeft,
                                readerMode = androidReaderMode,
                                controlsVisible = controlsVisible,
                                tapToTurnPagesEnabled = androidTapToTurnPagesEnabled,
                                reverseTapToTurnPages = androidReverseTapToTurnPages,
                                pageAnimationEnabled = androidPageAnimationEnabled,
                                doubleTapZoomEnabled = androidDoubleTapZoomEnabled,
                                longPressZoomEnabled = androidLongPressZoomEnabled,
                                zoomDismissGeneration = androidZoomDismissGeneration,
                            ),
                            prefetchDirection = prefetchDirection,
                            onPageRequested = { requestedPage, direction ->
                                goToPage(requestedPage, direction)
                            },
                            onDisplayedSpreadChanged = { committedSpread ->
                                displayedPageIndex = committedSpread.anchorPageIndex
                                displayedPageIndices = committedSpread.pageIndices
                                displayedPageOffsetY = 0.0
                                hasCommittedDisplay = true
                                if (hasUserNavigated) {
                                    savePositionProgress(
                                        anchorPageIndex = committedSpread.anchorPageIndex,
                                        progressPageIndex = committedSpread.progressPageIndex,
                                        pageOffsetY = 0.0,
                                        remoteSyncPolicy =
                                            ReaderProgressRemoteSyncPolicy.Debounced,
                                    )
                                }
                            },
                            onZoomActiveChange = { active ->
                                androidZoomActive = active
                            },
                            onToggleControls = toggleControls,
                        )
                    }

                    AndroidPdfReaderChrome(
                        state = AndroidPdfReaderChromeState(
                            title = book.title,
                            controlsVisible = controlsVisible,
                            settingsPanelVisible = androidSettingsPanelVisible,
                            currentPageIndex = navigationPageIndex,
                            pageCount = info.pageCount,
                            visiblePageIndices = if (hasCommittedDisplay) {
                                displayedPageIndices
                            } else {
                                navigationSpread.pageIndices
                            },
                            sliderPreviewPageIndices = previewPageIndices,
                            sliderValue = androidSliderValue,
                            sliderValueRange = 0f..(navigationUnitCount - 1).toFloat(),
                            sliderEnabled = navigationUnitCount > 1,
                            canGoPrevious = canGoPrevious,
                            canGoNext = canGoNext,
                            autoPageTurning = androidAutoPageTurning,
                            preferences = currentAndroidPreferences,
                            bookPreferences = currentBookPreferences,
                            statusInfo = androidReaderStatus?.let { status ->
                                AndroidPdfReaderStatusInfo(
                                    timeText = status.clockText,
                                    batteryText = status.batteryPercent?.let { percent ->
                                        "$percent%"
                                    },
                                )
                            },
                            showSpreadSettings =
                                androidReaderMode != PdfReaderMode.ContinuousVertical,
                        ),
                        actions = AndroidPdfReaderChromeActions(
                            onBack = ::exitReader,
                            onOpenSettings = {
                                androidZoomActive = false
                                androidZoomDismissGeneration++
                                androidSettingsPanelVisible = true
                                setControlsVisibility(true)
                            },
                            onDismissSettings = {
                                androidSettingsPanelVisible = false
                                androidInteractionGeneration++
                            },
                            onFirstPage = { requestNavigationUnit(0) },
                            onPreviousPage = {
                                requestNavigationUnit(navigationUnitIndex - 1)
                            },
                            onNextPage = {
                                requestNavigationUnit(navigationUnitIndex + 1)
                            },
                            onLastPage = {
                                requestNavigationUnit(navigationUnitCount - 1)
                            },
                            onSliderPreview = { value ->
                                androidSliderValue = value
                                androidInteractionGeneration++
                            },
                            onSliderCommit = {
                                requestNavigationUnit(androidSliderValue.roundToInt())
                            },
                            onAutoPageTurningChange = { enabled ->
                                androidAutoPageTurning = enabled && canGoNext
                                androidInteractionGeneration++
                            },
                            onReaderModeChange = ::setAndroidReaderMode,
                            onLandscapeDoublePageEnabledChange =
                                ::setAndroidLandscapePageMode,
                            onRightToLeftChange = ::setAndroidReadingDirection,
                            onCoverAloneChange = ::setAndroidCoverMode,
                            onOrientationModeChange = ::setAndroidOrientationMode,
                            onVolumeKeysEnabledChange = ::setAndroidVolumeKeys,
                            onTapToTurnPagesEnabledChange = { enabled ->
                                androidTapToTurnPagesEnabled = enabled
                                settingsRepository
                                    ?.setPdfAndroidTapToTurnPagesEnabled(enabled)
                            },
                            onReverseTapToTurnPagesChange = { enabled ->
                                androidReverseTapToTurnPages = enabled
                                settingsRepository?.setPdfAndroidReverseTapToTurnPages(enabled)
                            },
                            onPageAnimationEnabledChange = { enabled ->
                                androidPageAnimationEnabled = enabled
                                settingsRepository?.setPdfAndroidPageAnimationEnabled(enabled)
                            },
                            onDoubleTapZoomEnabledChange = { enabled ->
                                androidDoubleTapZoomEnabled = enabled
                                settingsRepository?.setPdfAndroidDoubleTapZoomEnabled(enabled)
                            },
                            onLongPressZoomEnabledChange = { enabled ->
                                androidLongPressZoomEnabled = enabled
                                settingsRepository?.setPdfAndroidLongPressZoomEnabled(enabled)
                            },
                            onShowPageNumberChange = { enabled ->
                                androidShowPageNumber = enabled
                                settingsRepository?.setPdfAndroidShowPageNumber(enabled)
                            },
                            onShowStatusInfoChange = { enabled ->
                                androidShowStatusInfo = enabled
                                settingsRepository?.setPdfAndroidShowStatusInfo(enabled)
                            },
                            onAutoPageTurningIntervalSecondsChange = { seconds ->
                                androidAutoPageTurningIntervalSeconds = seconds
                                settingsRepository
                                    ?.setPdfAndroidAutoPageTurningIntervalSeconds(seconds)
                            },
                        ),
                        systemBarInsets = systemBarInsets,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfReaderViewport(
    renderer: PdfDocumentRenderer,
    documentInfo: PdfDocumentInfo,
    spread: PdfPageSpread,
    isDoublePage: Boolean,
    isAndroid: Boolean,
    showCoverAlone: Boolean,
    prefetchDirection: Int,
    palette: PdfReaderPalette,
    controlsVisible: Boolean,
    canGoLeft: Boolean,
    canGoRight: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onToggleControls: () -> Unit,
) {
    val density = LocalDensity.current
    var cachedRenderSize by remember(renderer) { mutableStateOf<PdfRenderSize?>(null) }
    var renderedPages by remember(renderer) { mutableStateOf<Map<Int, ImageBitmap>>(emptyMap()) }
    var displayedSpread by remember(renderer) { mutableStateOf<PdfDisplayedSpread?>(null) }
    var targetRenderError by remember(renderer) { mutableStateOf<String?>(null) }
    var renderRetryGeneration by remember(renderer) { mutableIntStateOf(0) }
    var viewportSize by remember(renderer) { mutableStateOf(IntSize.Zero) }
    var androidZoomScale by remember(renderer) { mutableFloatStateOf(1f) }
    var androidPanOffset by remember(renderer) { mutableStateOf(Offset.Zero) }
    val displayedImage = displayedSpread?.pages?.singleOrNull()?.image
    val fittedContentSize = remember(displayedImage, viewportSize) {
        fitPdfPageInside(
            sourceWidthPx = displayedImage?.width ?: 0,
            sourceHeightPx = displayedImage?.height ?: 0,
            viewportWidthPx = viewportSize.width,
            viewportHeightPx = viewportSize.height,
        )
    }
    val displayedPageKey = displayedSpread?.pages?.map(PdfDisplayedPage::pageIndex)

    LaunchedEffect(displayedPageKey) {
        androidZoomScale = 1f
        androidPanOffset = Offset.Zero
    }

    LaunchedEffect(viewportSize, fittedContentSize) {
        val clampedPan = clampPdfPan(
            xPx = androidPanOffset.x,
            yPx = androidPanOffset.y,
            scale = androidZoomScale,
            contentWidthPx = fittedContentSize.widthPx.toFloat(),
            contentHeightPx = fittedContentSize.heightPx.toFloat(),
            viewportWidthPx = viewportSize.width.toFloat(),
            viewportHeightPx = viewportSize.height.toFloat(),
        )
        androidPanOffset = Offset(clampedPan.xPx, clampedPan.yPx)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isAndroid) 8.dp else 16.dp)
            .onSizeChanged { viewportSize = it },
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx().roundToInt() }
        val viewportHeightPx = with(density) { maxHeight.toPx().roundToInt() }
        val maxRenderDimensionPx = if (isAndroid) {
            PdfReaderDemoConfig.AndroidMaxRenderDimensionPx
        } else {
            PdfReaderDemoConfig.DesktopMaxRenderDimensionPx
        }
        val hasTwoVisiblePages = isDoublePage && spread.pageIndices.size > 1
        val targetWidthPx = (
            if (hasTwoVisiblePages) viewportWidthPx / 2 else viewportWidthPx
            ).coerceIn(PdfReaderDemoConfig.MinRenderDimensionPx, maxRenderDimensionPx)
        val targetHeightPx = viewportHeightPx
            .coerceIn(PdfReaderDemoConfig.MinRenderDimensionPx, maxRenderDimensionPx)
        val renderSize = PdfRenderSize(targetWidthPx, targetHeightPx)
        val targetPages = spread.pageIndices
        val renderOrder = pdfRenderOrder(
            spread = spread,
            pageCount = documentInfo.pageCount,
            isDoublePage = isDoublePage,
            showCoverAlone = showCoverAlone,
            prefetchDirection = prefetchDirection,
            maxCachedPages = PdfReaderDemoConfig.MaxCachedPages,
        )

        LaunchedEffect(
            renderer,
            documentInfo.pageCount,
            renderOrder,
            renderSize,
            renderRetryGeneration,
        ) {
            var nextCache = if (cachedRenderSize == renderSize) {
                renderedPages.filterKeys { it in targetPages }.toMutableMap()
            } else {
                mutableMapOf()
            }
            cachedRenderSize = renderSize
            renderedPages = nextCache.toMap()
            targetRenderError = null

            for (targetPage in targetPages) {
                if (nextCache[targetPage] != null) continue
                val image = try {
                    renderer.renderPage(
                        pageIndex = targetPage,
                        targetWidthPx = renderSize.widthPx,
                        targetHeightPx = renderSize.heightPx,
                    )
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    targetRenderError = ToreadStrings.openBookFailed(throwable.message)
                    return@LaunchedEffect
                }
                currentCoroutineContext().ensureActive()
                nextCache[targetPage] = image
                renderedPages = nextCache.toMap()
            }

            if (!pdfSpreadIsResolved(targetPages, nextCache.keys)) {
                return@LaunchedEffect
            }
            currentCoroutineContext().ensureActive()
            displayedSpread = PdfDisplayedSpread(
                pages = targetPages.map { pageIndex ->
                    PdfDisplayedPage(
                        pageIndex = pageIndex,
                        image = checkNotNull(nextCache[pageIndex]),
                    )
                },
            )
            targetRenderError = null

            for (prefetchPage in renderOrder) {
                if (prefetchPage in targetPages || nextCache[prefetchPage] != null) continue
                val image = try {
                    renderer.renderPage(
                        pageIndex = prefetchPage,
                        targetWidthPx = renderSize.widthPx,
                        targetHeightPx = renderSize.heightPx,
                    )
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    continue
                }
                currentCoroutineContext().ensureActive()
                nextCache[prefetchPage] = image
                renderedPages = nextCache.toMap()
            }
            nextCache = nextCache
                .filterKeys { it in renderOrder }
                .toMutableMap()
            renderedPages = nextCache.toMap()
        }

        val currentDisplay = displayedSpread
        when {
            currentDisplay != null -> {
                val hasTwoDisplayedPages = currentDisplay.pages.size > 1
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isAndroid) {
                                Modifier.graphicsLayer {
                                    scaleX = androidZoomScale
                                    scaleY = androidZoomScale
                                    translationX = androidPanOffset.x
                                    translationY = androidPanOffset.y
                                }
                            } else {
                                Modifier
                            },
                        ),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!hasTwoDisplayedPages) {
                        Spacer(modifier = Modifier.weight(if (isAndroid) 0.02f else 0.16f))
                    }
                    currentDisplay.pages.forEachIndexed { displayIndex, displayedPage ->
                        PdfPageFrame(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            image = displayedPage.image,
                            pageIndex = displayedPage.pageIndex,
                            alignment = when {
                                !hasTwoDisplayedPages -> Alignment.Center
                                displayIndex == 0 -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            },
                            showPageBadge = isAndroid,
                            palette = palette,
                        )
                    }
                    if (!hasTwoDisplayedPages) {
                        Spacer(modifier = Modifier.weight(if (isAndroid) 0.02f else 0.16f))
                    }
                }
            }
            targetRenderError == null -> PdfLoadingIndicator()
        }
    }

    if (isAndroid) {
        val minimumSwipeDistancePx = with(density) {
            PdfReaderDemoConfig.AndroidSwipeMinDistanceDp.dp.toPx()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    displayedPageKey,
                    viewportSize,
                    fittedContentSize,
                ) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val nextScale = (
                                    androidZoomScale * event.calculateZoom()
                                    ).coerceIn(
                                    minimumValue = 1f,
                                    maximumValue = PdfReaderDemoConfig.AndroidMaxZoomScale,
                                )
                                val eventPan = event.calculatePan()
                                if (
                                    nextScale <=
                                    1f + PdfReaderDemoConfig.AndroidZoomEpsilon
                                ) {
                                    androidZoomScale = 1f
                                    androidPanOffset = Offset.Zero
                                } else {
                                    val clampedPan = clampPdfPan(
                                        xPx = androidPanOffset.x + eventPan.x,
                                        yPx = androidPanOffset.y + eventPan.y,
                                        scale = nextScale,
                                        contentWidthPx = fittedContentSize.widthPx.toFloat(),
                                        contentHeightPx = fittedContentSize.heightPx.toFloat(),
                                        viewportWidthPx = viewportSize.width.toFloat(),
                                        viewportHeightPx = viewportSize.height.toFloat(),
                                    )
                                    androidZoomScale = nextScale
                                    androidPanOffset = Offset(
                                        x = clampedPan.xPx,
                                        y = clampedPan.yPx,
                                    )
                                }
                                event.changes.forEach { change ->
                                    if (change.position != change.previousPosition) {
                                        change.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(
                    displayedPageKey,
                    viewportSize,
                    fittedContentSize,
                    canGoLeft,
                    canGoRight,
                ) {
                    var dragX = 0f
                    var dragY = 0f
                    var isPanGesture = false
                    detectDragGestures(
                        onDragStart = {
                            dragX = 0f
                            dragY = 0f
                            isPanGesture =
                                androidZoomScale >
                                1f + PdfReaderDemoConfig.AndroidZoomEpsilon
                        },
                        onDragCancel = {
                            dragX = 0f
                            dragY = 0f
                        },
                        onDragEnd = {
                            if (!isPanGesture) {
                                when (
                                    pdfReaderSwipeAction(
                                        dragXPx = dragX,
                                        dragYPx = dragY,
                                        viewportWidthPx = viewportSize.width.toFloat(),
                                        minimumTurnDistancePx = minimumSwipeDistancePx,
                                        isZoomed = androidZoomScale >
                                            1f + PdfReaderDemoConfig.AndroidZoomEpsilon,
                                    )
                                ) {
                                    PdfReaderSwipeAction.PreviousPage -> {
                                        if (canGoLeft) onLeft()
                                    }
                                    PdfReaderSwipeAction.NextPage -> {
                                        if (canGoRight) onRight()
                                    }
                                    PdfReaderSwipeAction.None -> Unit
                                }
                            }
                            dragX = 0f
                            dragY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (isPanGesture) {
                                val clampedPan = clampPdfPan(
                                    xPx = androidPanOffset.x + dragAmount.x,
                                    yPx = androidPanOffset.y + dragAmount.y,
                                    scale = androidZoomScale,
                                    contentWidthPx = fittedContentSize.widthPx.toFloat(),
                                    contentHeightPx = fittedContentSize.heightPx.toFloat(),
                                    viewportWidthPx = viewportSize.width.toFloat(),
                                    viewportHeightPx = viewportSize.height.toFloat(),
                                )
                                androidPanOffset = Offset(
                                    x = clampedPan.xPx,
                                    y = clampedPan.yPx,
                                )
                            } else {
                                dragX += dragAmount.x
                                dragY += dragAmount.y
                            }
                        },
                    )
                }
                .pointerInput(
                    displayedPageKey,
                    viewportSize,
                    fittedContentSize,
                    controlsVisible,
                    canGoLeft,
                    canGoRight,
                ) {
                    detectTapGestures(
                        onDoubleTap = { focus ->
                            if (
                                androidZoomScale >
                                1f + PdfReaderDemoConfig.AndroidZoomEpsilon
                            ) {
                                androidZoomScale = 1f
                                androidPanOffset = Offset.Zero
                            } else {
                                val nextScale =
                                    PdfReaderDemoConfig.AndroidDoubleTapZoomScale
                                val nextPan = pdfPanAfterZoomAtPoint(
                                    currentPan = PdfPanOffset(
                                        xPx = androidPanOffset.x,
                                        yPx = androidPanOffset.y,
                                    ),
                                    currentScale = androidZoomScale,
                                    targetScale = nextScale,
                                    focusXPx = focus.x,
                                    focusYPx = focus.y,
                                    contentWidthPx = fittedContentSize.widthPx.toFloat(),
                                    contentHeightPx = fittedContentSize.heightPx.toFloat(),
                                    viewportWidthPx = viewportSize.width.toFloat(),
                                    viewportHeightPx = viewportSize.height.toFloat(),
                                )
                                androidZoomScale = nextScale
                                androidPanOffset = Offset(
                                    x = nextPan.xPx,
                                    y = nextPan.yPx,
                                )
                            }
                        },
                        onTap = { position ->
                            when (
                                pdfReaderTapAction(
                                    xPx = position.x,
                                    viewportWidthPx = viewportSize.width.toFloat(),
                                    isZoomed = androidZoomScale >
                                        1f + PdfReaderDemoConfig.AndroidZoomEpsilon,
                                    controlsVisible = controlsVisible,
                                )
                            ) {
                                PdfReaderTapAction.PreviousPage -> {
                                    if (canGoLeft) onLeft()
                                }
                                PdfReaderTapAction.ToggleControls -> onToggleControls()
                                PdfReaderTapAction.NextPage -> {
                                    if (canGoRight) onRight()
                                }
                                PdfReaderTapAction.None -> Unit
                            }
                        },
                    )
                },
        )
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .clickable(enabled = canGoLeft, onClick = onLeft),
            )
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .clickable(onClick = onToggleControls),
            )
            Box(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .clickable(
                        enabled = canGoRight,
                        onClick = onRight,
                    ),
            )
        }
    }
    targetRenderError?.let { message ->
        PdfRenderErrorOverlay(
            message = message,
            palette = palette,
            onRetry = { renderRetryGeneration++ },
        )
    }
}

@Composable
private fun PdfRenderErrorOverlay(
    message: String,
    palette: PdfReaderPalette,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = palette.toolbarBackground,
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = message,
                    color = palette.toolbarText,
                )
                PdfDesktopTextButton(
                    onClick = onRetry,
                    palette = palette,
                ) {
                    Text(ToreadStrings.Retry)
                }
            }
        }
    }
}

@Composable
private fun PdfPageFrame(
    modifier: Modifier,
    image: ImageBitmap,
    pageIndex: Int,
    alignment: Alignment,
    showPageBadge: Boolean,
    palette: PdfReaderPalette,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = alignment,
    ) {
        val pageModifier = if (image.height > 0) {
            val fitSize = fitPdfPageInside(
                sourceWidthPx = image.width,
                sourceHeightPx = image.height,
                viewportWidthPx = with(density) { maxWidth.toPx().roundToInt() },
                viewportHeightPx = with(density) { maxHeight.toPx().roundToInt() },
            )
            Modifier.size(
                width = with(density) { fitSize.widthPx.toDp() },
                height = with(density) { fitSize.heightPx.toDp() },
            )
        } else {
            Modifier.fillMaxSize()
        }
        Surface(
            modifier = pageModifier,
            color = Color.White,
            shape = RectangleShape,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = image,
                    contentDescription = ToreadStrings.chapterPage(pageIndex),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                if (showPageBadge) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                        color = palette.floatingBackground,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = "${pageIndex + 1}",
                            color = palette.floatingText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfReaderControls(
    modifier: Modifier,
    book: BookshelfBook,
    spread: PdfPageSpread,
    pageCount: Int,
    isDesktop: Boolean,
    isDoublePage: Boolean,
    isRightToLeft: Boolean,
    showCoverAlone: Boolean,
    androidLandscapeDoublePageEnabled: Boolean,
    androidOrientationMode: PdfOrientationMode,
    androidVolumeKeysEnabled: Boolean,
    palette: PdfReaderPalette,
    visible: Boolean,
    bottomInset: androidx.compose.ui.unit.Dp,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onBack: () -> Unit,
    onFirst: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit,
    onSelectSpread: (Int) -> Unit,
    onTogglePageMode: () -> Unit,
    onToggleReadingDirection: () -> Unit,
    onToggleCoverMode: () -> Unit,
    onCycleAndroidOrientationMode: () -> Unit,
    onToggleAndroidVolumeKeys: () -> Unit,
) {
    if (isDesktop) {
        DesktopPdfReaderControls(
            modifier = modifier,
            book = book,
            spread = spread,
            pageCount = pageCount,
            isDoublePage = isDoublePage,
            isRightToLeft = isRightToLeft,
            showCoverAlone = showCoverAlone,
            palette = palette,
            visible = visible,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            onBack = onBack,
            onFirst = onFirst,
            onPrevious = onPrevious,
            onNext = onNext,
            onLast = onLast,
            onSelectSpread = onSelectSpread,
            onTogglePageMode = onTogglePageMode,
            onToggleReadingDirection = onToggleReadingDirection,
            onToggleCoverMode = onToggleCoverMode,
        )
    } else {
        AndroidPdfReaderControls(
            modifier = modifier,
            book = book,
            spread = spread,
            pageCount = pageCount,
            visible = visible,
            bottomInset = bottomInset,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            onBack = onBack,
            onPrevious = onPrevious,
            onNext = onNext,
            onSelectSpread = onSelectSpread,
            landscapeDoublePageEnabled = androidLandscapeDoublePageEnabled,
            isDoublePage = isDoublePage,
            isRightToLeft = isRightToLeft,
            showCoverAlone = showCoverAlone,
            orientationMode = androidOrientationMode,
            volumeKeysEnabled = androidVolumeKeysEnabled,
            onTogglePageMode = onTogglePageMode,
            onToggleReadingDirection = onToggleReadingDirection,
            onToggleCoverMode = onToggleCoverMode,
            onCycleOrientationMode = onCycleAndroidOrientationMode,
            onToggleVolumeKeys = onToggleAndroidVolumeKeys,
        )
    }
}

@Composable
private fun DesktopPdfReaderControls(
    modifier: Modifier,
    book: BookshelfBook,
    spread: PdfPageSpread,
    pageCount: Int,
    isDoublePage: Boolean,
    isRightToLeft: Boolean,
    showCoverAlone: Boolean,
    palette: PdfReaderPalette,
    visible: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onBack: () -> Unit,
    onFirst: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit,
    onSelectSpread: (Int) -> Unit,
    onTogglePageMode: () -> Unit,
    onToggleReadingDirection: () -> Unit,
    onToggleCoverMode: () -> Unit,
) {
    var selectedSpread by remember(spread.spreadIndex, spread.spreadCount) {
        mutableFloatStateOf(spread.spreadIndex.toFloat())
    }
    val animationSpec = tween<Float>(PdfReaderDemoConfig.ToolbarAnimationMillis)
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = !visible,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 18.dp),
            enter = fadeIn(animationSpec),
            exit = fadeOut(animationSpec),
        ) {
            Surface(
                color = palette.floatingBackground,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = ToreadStrings.pdfPageRange(spread.pageIndices, pageCount),
                    color = palette.floatingText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            enter = slideInVertically(
                animationSpec = tween(PdfReaderDemoConfig.ToolbarAnimationMillis),
                initialOffsetY = { -it },
            ) + fadeIn(animationSpec),
            exit = slideOutVertically(
                animationSpec = tween(PdfReaderDemoConfig.ToolbarAnimationMillis),
                targetOffsetY = { -it },
            ) + fadeOut(animationSpec),
        ) {
            Surface(
                color = palette.toolbarBackground,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onBack) {
                        ToreadSymbolIcon(
                            symbol = ToreadSymbols.ArrowBack,
                            contentDescription = ToreadStrings.BackToBookshelf,
                            tint = palette.toolbarText,
                            autoMirror = true,
                        )
                    }
                    Text(
                        text = book.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.toolbarText,
                    )
                    Text(
                        text = ToreadStrings.pdfPageRange(spread.pageIndices, pageCount),
                        color = palette.secondaryText,
                    )
                    PdfDesktopTextButton(onClick = onTogglePageMode, palette = palette) {
                        Text(if (isDoublePage) ToreadStrings.DoublePage else ToreadStrings.SinglePage)
                    }
                    PdfDesktopTextButton(onClick = onToggleReadingDirection, palette = palette) {
                        Text(
                            if (isRightToLeft) {
                                ToreadStrings.RightToLeft
                            } else {
                                ToreadStrings.LeftToRight
                            },
                        )
                    }
                    if (isDoublePage) {
                        PdfDesktopTextButton(onClick = onToggleCoverMode, palette = palette) {
                            Text(
                                if (showCoverAlone) {
                                    ToreadStrings.CoverAlone
                                } else {
                                    ToreadStrings.CoverPaired
                                },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = tween(PdfReaderDemoConfig.ToolbarAnimationMillis),
                initialOffsetY = { it },
            ) + fadeIn(animationSpec),
            exit = slideOutVertically(
                animationSpec = tween(PdfReaderDemoConfig.ToolbarAnimationMillis),
                targetOffsetY = { it },
            ) + fadeOut(animationSpec),
        ) {
            Surface(
                color = palette.toolbarBackground,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PdfDesktopTextButton(
                        onClick = onFirst,
                        enabled = canGoPrevious,
                        palette = palette,
                    ) {
                        Text(ToreadStrings.FirstPage)
                    }
                    PdfDesktopTextButton(
                        onClick = onPrevious,
                        enabled = canGoPrevious,
                        palette = palette,
                    ) {
                        ToreadSymbolIcon(
                            symbol = if (isRightToLeft) {
                                ToreadSymbols.KeyboardArrowRight
                            } else {
                                ToreadSymbols.KeyboardArrowLeft
                            },
                            contentDescription = ToreadStrings.PreviousPage,
                            tint = palette.toolbarText,
                        )
                    }
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isRightToLeft) {
                            LayoutDirection.Rtl
                        } else {
                            LayoutDirection.Ltr
                        },
                    ) {
                        Slider(
                            value = selectedSpread,
                            onValueChange = { selectedSpread = it },
                            onValueChangeFinished = {
                                onSelectSpread(selectedSpread.roundToInt())
                            },
                            valueRange = 0f..(spread.spreadCount - 1)
                                .coerceAtLeast(1)
                                .toFloat(),
                            enabled = spread.spreadCount > 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = ToreadStrings.pdfPageRange(spread.pageIndices, pageCount),
                        color = palette.toolbarText,
                    )
                    PdfDesktopTextButton(
                        onClick = onNext,
                        enabled = canGoNext,
                        palette = palette,
                    ) {
                        ToreadSymbolIcon(
                            symbol = if (isRightToLeft) {
                                ToreadSymbols.KeyboardArrowLeft
                            } else {
                                ToreadSymbols.KeyboardArrowRight
                            },
                            contentDescription = ToreadStrings.NextPage,
                            tint = palette.toolbarText,
                        )
                    }
                    PdfDesktopTextButton(
                        onClick = onLast,
                        enabled = canGoNext,
                        palette = palette,
                    ) {
                        Text(ToreadStrings.LastPage)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfDesktopTextButton(
    onClick: () -> Unit,
    palette: PdfReaderPalette,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = palette.toolbarText,
            disabledContentColor = palette.disabledText,
        ),
        content = { content() },
    )
}

@Composable
private fun AndroidPdfReaderControls(
    modifier: Modifier,
    book: BookshelfBook,
    spread: PdfPageSpread,
    pageCount: Int,
    visible: Boolean,
    bottomInset: androidx.compose.ui.unit.Dp,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectSpread: (Int) -> Unit,
    landscapeDoublePageEnabled: Boolean,
    isDoublePage: Boolean,
    isRightToLeft: Boolean,
    showCoverAlone: Boolean,
    orientationMode: PdfOrientationMode,
    volumeKeysEnabled: Boolean,
    onTogglePageMode: () -> Unit,
    onToggleReadingDirection: () -> Unit,
    onToggleCoverMode: () -> Unit,
    onCycleOrientationMode: () -> Unit,
    onToggleVolumeKeys: () -> Unit,
) {
    if (!visible) return
    var selectedSpread by remember(spread.spreadIndex, spread.spreadCount) {
        mutableFloatStateOf(spread.spreadIndex.toFloat())
    }
    val previewSpreadIndex = selectedSpread
        .roundToInt()
        .coerceIn(0, (spread.spreadCount - 1).coerceAtLeast(0))
    val previewPageIndex = pdfPageSpread(
        pageIndex = pdfSpreadAnchorAt(
            spreadIndex = previewSpreadIndex,
            pageCount = pageCount,
            isDoublePage = isDoublePage,
            showCoverAlone = showCoverAlone,
        ),
        pageCount = pageCount,
        isDoublePage = isDoublePage,
        showCoverAlone = showCoverAlone,
        rightToLeft = false,
    ).progressPageIndex
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onBack) {
                    Text(ToreadStrings.BackToBookshelf)
                }
                Text(
                    text = book.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(ToreadStrings.pdfPageRange(spread.pageIndices, pageCount))
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 18.dp,
                    top = 10.dp,
                    end = 18.dp,
                    bottom = bottomInset + 10.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextButton(onClick = onTogglePageMode) {
                        Text(
                            if (landscapeDoublePageEnabled) {
                                ToreadStrings.LandscapeDoublePage
                            } else {
                                ToreadStrings.LandscapeSinglePage
                            },
                        )
                    }
                    TextButton(onClick = onToggleReadingDirection) {
                        Text(
                            if (isRightToLeft) {
                                ToreadStrings.RightToLeft
                            } else {
                                ToreadStrings.LeftToRight
                            },
                        )
                    }
                    TextButton(onClick = onToggleCoverMode) {
                        Text(
                            if (showCoverAlone) {
                                ToreadStrings.CoverAlone
                            } else {
                                ToreadStrings.CoverPaired
                            },
                        )
                    }
                    TextButton(onClick = onCycleOrientationMode) {
                        Text(
                            when (orientationMode) {
                                PdfOrientationMode.System ->
                                    ToreadStrings.OrientationSystem
                                PdfOrientationMode.Portrait ->
                                    ToreadStrings.OrientationPortrait
                                PdfOrientationMode.Landscape ->
                                    ToreadStrings.OrientationLandscape
                            },
                        )
                    }
                    TextButton(onClick = onToggleVolumeKeys) {
                        Text(
                            if (volumeKeysEnabled) {
                                ToreadStrings.VolumeKeysOn
                            } else {
                                ToreadStrings.VolumeKeysOff
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onPrevious, enabled = canGoPrevious) {
                        Text(ToreadStrings.PreviousPage)
                    }
                    Text(ToreadStrings.readerPercent(previewPageIndex.pageProgress(pageCount)))
                    TextButton(
                        onClick = onNext,
                        enabled = canGoNext,
                    ) {
                        Text(ToreadStrings.NextPage)
                    }
                }
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (isRightToLeft) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
                ) {
                    Slider(
                        value = selectedSpread,
                        onValueChange = { selectedSpread = it },
                        onValueChangeFinished = {
                            onSelectSpread(selectedSpread.roundToInt())
                        },
                        valueRange = 0f..(spread.spreadCount - 1)
                            .coerceAtLeast(1)
                            .toFloat(),
                        enabled = spread.spreadCount > 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfLoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = ToreadStrings.PdfRendering,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

private fun PdfOrientationMode.toReaderOrientationMode(): ReaderOrientationMode = when (this) {
    PdfOrientationMode.System -> ReaderOrientationMode.Auto
    PdfOrientationMode.Portrait -> ReaderOrientationMode.Portrait
    PdfOrientationMode.Landscape -> ReaderOrientationMode.Landscape
}

private data class PdfRenderSize(
    val widthPx: Int,
    val heightPx: Int,
)

private data class PdfDisplayedPage(
    val pageIndex: Int,
    val image: ImageBitmap,
)

private data class PdfDisplayedSpread(
    val pages: List<PdfDisplayedPage>,
)

private data class PdfReaderPalette(
    val background: Color,
    val toolbarBackground: Color,
    val toolbarText: Color,
    val secondaryText: Color,
    val disabledText: Color,
    val floatingBackground: Color,
    val floatingText: Color,
)

private object PdfReaderDemoConfig {
    const val MinRenderDimensionPx = 128
    const val AndroidMaxRenderDimensionPx = 2_048
    const val DesktopMaxRenderDimensionPx = 3_072
    const val MaxCachedPages = 4
    const val ToolbarAnimationMillis = 180
    const val MouseWheelTurnThreshold = 1f
    const val AndroidSwipeMinDistanceDp = 56
    const val AndroidDoubleTapZoomScale = 2f
    const val AndroidMaxZoomScale = 3f
    const val AndroidZoomEpsilon = 0.01f
    const val AndroidControlsAutoHideMillis = 3_500L
    const val AndroidStatusRefreshMillis = 60_000L
}

private const val PdfLogArea = "sync"
