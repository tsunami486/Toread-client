package com.example.toread.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import com.example.toread.platform.SystemBarsController
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.reader.BookLocator
import com.example.toread.reader.ReaderContentLoader
import com.example.toread.reader.ReaderDocument
import com.example.toread.ui.components.ScreenColumn
import com.example.toread.ui.reader.AndroidReaderLayout
import com.example.toread.ui.reader.DesktopReaderLayout
import com.example.toread.ui.reader.ReaderMessage
import com.example.toread.ui.reader.ReaderPageSlice
import com.example.toread.ui.reader.normalizePageIndex
import com.example.toread.ui.reader.pageProgress
import com.example.toread.ui.reader.paginateForReader
import com.example.toread.ui.reader.rememberReaderPaginationSpec
import com.example.toread.ui.reader.saveReaderProgress
import com.example.toread.ui.reader.toPageIndex
import kotlin.math.roundToInt
@Composable
internal fun ReaderPage(
    book: BookshelfBook?,
    platform: Platform,
    bookshelfRepository: BookshelfRepository?,
    progressRepository: ReadingProgressRepository?,
    readerContentLoader: ReaderContentLoader,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isAndroid = platform.kind == PlatformKind.Android
    val isDesktop = platform.kind == PlatformKind.Desktop
    val density = LocalDensity.current
    var document by remember(book?.bookHash) { mutableStateOf<ReaderDocument?>(null) }
    var restoredLocator by remember(book?.bookHash) { mutableStateOf<BookLocator?>(null) }
    var restoredForMeasuredPages by remember(book?.bookHash) { mutableStateOf(false) }
    var pageIndex by remember(book?.bookHash) { mutableIntStateOf(0) }
    var error by remember(book?.bookHash) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(book?.bookHash) { mutableStateOf(false) }
    var measuredPages by remember(book?.bookHash) { mutableStateOf<List<ReaderPageSlice>>(emptyList()) }
    var paginationMessage by remember(book?.bookHash) { mutableStateOf<String?>(null) }
    var paginationComplete by remember(book?.bookHash) { mutableStateOf(false) }
    var desktopReaderFocusReady by remember(book?.bookHash) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(book?.bookHash) {
        if (book == null) return@LaunchedEffect
        document = null
        error = null
        restoredLocator = null
        restoredForMeasuredPages = false
        runCatching {
            readerContentLoader.load(book.filePath, book.format, book.title)
        }.fold(
            onSuccess = { loaded ->
                document = loaded
                restoredLocator = bookshelfRepository?.progressLocator(book.bookHash)
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
    fun exitReaderWithoutProgress() {
        if (isAndroid) SystemBarsController.showSystemBars()
        onBack()
    }

    val readerModifier = Modifier
        .fillMaxSize()

    Box(
        modifier = readerModifier.background(
            if (isDesktop) Color(0xFF101415) else MaterialTheme.colorScheme.background,
        ),
    ) {
        when {
            error != null -> ReaderMessage(message = error.orEmpty(), onBack = ::exitReaderWithoutProgress)
            loadedDocument == null -> ReaderMessage(message = "正在读取本地文件...", onBack = ::exitReaderWithoutProgress)
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
                )

                LaunchedEffect(loadedDocument.text, spec) {
                    paginationMessage = "正在分页本地文本..."
                    measuredPages = emptyList()
                    paginationComplete = false
                    restoredForMeasuredPages = false
                    desktopReaderFocusReady = false
                    var lastPublishedPageCount = 0
                    measuredPages = loadedDocument.paginateForReader(spec) { partialPages ->
                        if (partialPages.size <= 4 || partialPages.size - lastPublishedPageCount >= 24) {
                            measuredPages = partialPages
                            lastPublishedPageCount = partialPages.size
                            paginationMessage = "正在分页：${partialPages.size} 页"
                        }
                    }
                    paginationComplete = true
                    paginationMessage = null
                }

                val pages = measuredPages

                LaunchedEffect(book.bookHash, pages.size, restoredForMeasuredPages, isDesktop, paginationComplete) {
                    if (!restoredForMeasuredPages && pages.isNotEmpty() && paginationComplete) {
                        pageIndex = normalizePageIndex(
                            restoredLocator?.toPageIndex(pages.size) ?: 0,
                            pages.size,
                            isDesktop,
                        )
                        restoredForMeasuredPages = true
                    }
                }

                val currentPageIndex = pageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
                val progress = currentPageIndex.pageProgress(pages.size)

                fun saveCurrentProgress(targetPage: Int = currentPageIndex) {
                    saveReaderProgress(
                        scope = scope,
                        progressRepository = progressRepository,
                        book = book,
                        pageIndex = targetPage,
                        pageCount = pages.size,
                        charOffset = pages.getOrNull(targetPage)?.startOffset ?: 0,
                        deviceId = platform.name,
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
                    saveCurrentProgress()
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
                    val message = paginationMessage
                    if (pages.isEmpty()) {
                        ReaderMessage(message = message ?: "正在分页本地文本...", onBack = ::exitReaderWithoutProgress)
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
