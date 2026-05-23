package com.example.toread

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.example.toread.importing.BookImportResult
import com.example.toread.importing.BookImportService
import com.example.toread.importing.createToreadDatabase
import com.example.toread.library.BookshelfBook
import com.example.toread.library.BookshelfRepository
import com.example.toread.platform.SystemBarsController
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.reader.BookLocator
import com.example.toread.reader.EpubLocator
import com.example.toread.reader.PdfLocator
import com.example.toread.reader.ReaderContentLoader
import com.example.toread.reader.ReaderDocument
import com.example.toread.reader.TxtLocator
import com.example.toread.settings.AuthMode
import com.example.toread.settings.LocalSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.min
import kotlin.math.roundToInt

private const val ASeriesPaperAspectRatio = 0.7071f
private const val DesktopSpreadAspectRatio = ASeriesPaperAspectRatio * 2f

private val ChapterTitleRegex = Regex(
    pattern = """^(第[0-9一二三四五六七八九十百千万零〇两]+[章节回卷部篇].*|Chapter\s+\d+.*|序章|序言|楔子|引子|尾声|后记).*$""",
    option = RegexOption.IGNORE_CASE,
)

private enum class ToreadScreen {
    CheckingAuth,
    Auth,
    Bookshelf,
    Import,
    Reader,
    Settings,
}

@Composable
@Preview
fun App() {
    ToreadApp()
}

@Composable
fun ToreadApp() {
    val platform = remember { getPlatform() }
    var screen by remember { mutableStateOf(ToreadScreen.CheckingAuth) }
    var selectedBook by remember { mutableStateOf<BookshelfBook?>(null) }
    var books by remember { mutableStateOf<List<BookshelfBook>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var databaseStatus by remember { mutableStateOf<String?>(null) }

    val database = remember {
        runCatching { createToreadDatabase() }
            .onFailure { databaseStatus = "本地数据库初始化失败：${it.message ?: "未知错误"}" }
            .getOrNull()
    }
    val importService = remember(database) { database?.let { BookImportService(it) } }
    val bookshelfRepository = remember(database) { database?.let { BookshelfRepository(it) } }
    val progressRepository = remember(database) { database?.let { ReadingProgressRepository(it) } }
    val settingsRepository = remember(database) { database?.let { LocalSettingsRepository(it) } }
    val readerContentLoader = remember { ReaderContentLoader() }

    LaunchedEffect(settingsRepository) {
        screen = when (settingsRepository?.currentAuthMode()) {
            AuthMode.LoggedIn,
            AuthMode.Guest -> ToreadScreen.Bookshelf
            null -> ToreadScreen.Auth
        }
    }

    LaunchedEffect(bookshelfRepository, refreshKey) {
        books = runCatching { bookshelfRepository?.listBooks().orEmpty() }
            .onFailure { databaseStatus = "读取书架失败：${it.message ?: "未知错误"}" }
            .getOrDefault(emptyList())
    }

    val isReaderScreen = screen == ToreadScreen.Reader

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(if (isReaderScreen) Modifier else Modifier.safeContentPadding()),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                databaseStatus?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                    HorizontalDivider()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isReaderScreen) 0.dp else 24.dp),
                ) {
                    when (screen) {
                        ToreadScreen.CheckingAuth -> CheckingAuthPage()
                        ToreadScreen.Auth -> AuthPage(
                            settingsRepository = settingsRepository,
                            onDone = {
                                refreshKey++
                                screen = ToreadScreen.Bookshelf
                            },
                        )
                        ToreadScreen.Bookshelf -> BookshelfPage(
                            books = books,
                            onImport = { screen = ToreadScreen.Import },
                            onOpenBook = { book ->
                                selectedBook = book
                                screen = ToreadScreen.Reader
                            },
                            onSettings = { screen = ToreadScreen.Settings },
                        )
                        ToreadScreen.Import -> ImportPage(
                            importService = importService,
                            onImported = { refreshKey++ },
                            onDone = {
                                refreshKey++
                                screen = ToreadScreen.Bookshelf
                            },
                        )
                        ToreadScreen.Reader -> ReaderPage(
                            book = selectedBook,
                            platform = platform,
                            bookshelfRepository = bookshelfRepository,
                            progressRepository = progressRepository,
                            readerContentLoader = readerContentLoader,
                            onBack = {
                                refreshKey++
                                screen = ToreadScreen.Bookshelf
                            },
                        )
                        ToreadScreen.Settings -> SettingsPage(
                            settingsRepository = settingsRepository,
                            onBack = {
                                refreshKey++
                                screen = ToreadScreen.Bookshelf
                            },
                            onLogout = {
                                settingsRepository?.clearAuthMode()
                                selectedBook = null
                                screen = ToreadScreen.Auth
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckingAuthPage() {
    ScreenColumn {
        Text("正在启动", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("正在读取本地登录状态。")
    }
}

@Composable
private fun AuthPage(settingsRepository: LocalSettingsRepository?, onDone: () -> Unit) {
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    ScreenColumn {
        Text(
            text = if (isRegister) "注册 Toread" else "登录 Toread",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text("Toread 是导入型阅读器，只读取你主动选择的本地 EPUB、PDF 和 TXT 文件。")
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("邮箱") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = settingsRepository != null,
                onClick = {
                    settingsRepository?.setLoggedIn(email.trim())
                    onDone()
                },
            ) {
                Text(if (isRegister) "注册并进入" else "登录")
            }
            OutlinedButton(onClick = { isRegister = !isRegister }) {
                Text(if (isRegister) "去登录" else "去注册")
            }
            TextButton(
                enabled = settingsRepository != null,
                onClick = {
                    settingsRepository?.setGuestMode()
                    onDone()
                },
            ) {
                Text("游客进入")
            }
        }
    }
}

@Composable
private fun BookshelfPage(
    books: List<BookshelfBook>,
    onImport: () -> Unit,
    onOpenBook: (BookshelfBook) -> Unit,
    onSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredBooks = remember(books, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            books
        } else {
            books.filter { book ->
                book.title.contains(keyword, ignoreCase = true) ||
                    book.author.orEmpty().contains(keyword, ignoreCase = true) ||
                    book.format.contains(keyword, ignoreCase = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenColumn {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("书架", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Button(onClick = onImport) {
                    Text("导入")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索本地书架") },
                singleLine = true,
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            )

            if (filteredBooks.isEmpty()) {
                EmptyBookshelfCard(onImport = onImport)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    filteredBooks.forEach { book ->
                        BookRow(book = book, onOpenBook = onOpenBook)
                    }
                }
            }
        }

        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(56.dp),
        ) {
            Text("⚙", fontSize = 28.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun EmptyBookshelfCard(onImport: () -> Unit) {
    Card(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("还没有可阅读的书", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("导入 EPUB、PDF 或 TXT 文件后会显示在这里。")
            Button(onClick = onImport) {
                Text("选择本地文件")
            }
        }
    }
}

@Composable
private fun BookRow(book: BookshelfBook, onOpenBook: (BookshelfBook) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenBook(book) }
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book = book)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${book.format.uppercase()} · ${book.author ?: "未知作者"}")
                LinearProgressIndicator(
                    progress = { book.progress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("进度 ${(book.progress * 100).roundToInt()}% · 同步状态 ${book.syncStatus}")
            }
            Button(onClick = { onOpenBook(book) }) {
                Text("打开")
            }
        }
    }
}

@Composable
private fun BookCover(book: BookshelfBook) {
    val format = book.format.lowercase()
    val isTxt = format == "txt"
    val coverColor = when (format) {
        "epub" -> Color(0xFF456F5B)
        "pdf" -> Color(0xFF7B4E42)
        else -> Color.White
    }
    val textColor = if (isTxt) Color.Transparent else Color.White

    Box(
        modifier = Modifier
            .width(76.dp)
            .height(112.dp)
            .background(coverColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!isTxt) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(format.uppercase(), color = textColor, fontWeight = FontWeight.Bold)
                Text(
                    book.title,
                    color = textColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ImportPage(importService: BookImportService?, onImported: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("等待选择本地文件。") }

    ScreenColumn {
        Text("导入书籍", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("支持 EPUB、PDF、TXT。只读取你主动选择的本地文件。")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = importService != null,
                onClick = {
                    val service = importService ?: return@Button
                    status = "正在打开文件选择器..."
                    scope.launch {
                        status = runCatching { service.importSelectedFiles() }
                            .fold(
                                onSuccess = { results ->
                                    onImported()
                                    results.toImportStatus()
                                },
                                onFailure = { error -> "导入失败：${error.message ?: "未知错误"}" },
                            )
                    }
                },
            ) {
                Text("选择本地文件")
            }
            OutlinedButton(onClick = onDone) {
                Text("返回书架")
            }
        }
        Card(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("导入状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(status)
            }
        }
    }
}

private fun List<BookImportResult>.toImportStatus(): String {
    if (isEmpty()) return "没有选择文件。"

    val imported = count { it is BookImportResult.Imported }
    val duplicates = count { it is BookImportResult.Duplicate }
    val failed = filterIsInstance<BookImportResult.Failed>()
    return buildString {
        append("导入成功：$imported")
        if (duplicates > 0) append("，已在书架中：$duplicates")
        if (failed.isNotEmpty()) {
            append("，失败：${failed.size}。")
            append(failed.joinToString { "${it.fileName}：${it.reason}" })
        }
    }
}

@Composable
private fun ReaderPage(
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

@Composable
private fun DesktopReaderLayout(
    book: BookshelfBook,
    sourceText: String,
    pages: List<ReaderPageSlice>,
    pageIndex: Int,
    progress: Double,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 72.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("‹ ${book.title}", color = Color(0xFFE4EAEC))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onPrevious, enabled = pageIndex > 0) {
                    Text("上一页")
                }
                FilledTonalButton(onClick = onNext, enabled = pageIndex + 2 <= pages.lastIndex) {
                    Text("下一页")
                }
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .aspectRatio(DesktopSpreadAspectRatio)
                .align(Alignment.CenterHorizontally),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF171D1E))
                    .padding(horizontal = 34.dp, vertical = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                ReaderTextPage(
                    page = pages.getOrNull(pageIndex) ?: ReaderPageSlice(book.title, 0, 0),
                    sourceText = sourceText,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    textColor = Color(0xFFF3F7F8),
                    bodyFontSize = 22.sp,
                    bodyLineHeight = 34.sp,
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF2A3234)),
                )
                ReaderTextPage(
                    page = pages.getOrNull(pageIndex + 1) ?: ReaderPageSlice(book.title, 0, 0),
                    sourceText = sourceText,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    textColor = Color(0xFFF3F7F8),
                    bodyFontSize = 22.sp,
                    bodyLineHeight = 34.sp,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("第 ${pageIndex + 1}-${min(pageIndex + 2, pages.size)} 页", color = Color(0xFFB8C2C5))
            Text("${(progress * 100).roundToInt()}%", color = Color(0xFFB8C2C5))
        }
    }
}

@Composable
private fun AndroidReaderLayout(
    book: BookshelfBook,
    sourceText: String,
    pages: List<ReaderPageSlice>,
    pageIndex: Int,
    progress: Double,
    controlsVisible: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleControls: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ReaderTextPage(
            page = pages.getOrNull(pageIndex) ?: ReaderPageSlice(book.title, 0, 0),
            sourceText = sourceText,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 34.dp),
            textColor = MaterialTheme.colorScheme.onBackground,
            bodyFontSize = 28.sp,
            bodyLineHeight = 46.sp,
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onPrevious))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onToggleControls))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onNext))
        }

        if (controlsVisible) {
            AndroidReaderTopBar(book = book, onBack = onBack)
            AndroidReaderBottomPanel(
                progress = progress,
                canPrevious = pageIndex > 0,
                canNext = pageIndex < pages.lastIndex,
                onPrevious = onPrevious,
                onNext = onNext,
            )
        }
    }
}

@Composable
private fun AndroidReaderTopBar(book: BookshelfBook, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("‹")
        }
        Text(
            book.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun BoxScope.AndroidReaderBottomPanel(
    progress: Double,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .align(Alignment.BottomCenter),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPrevious, enabled = canPrevious) {
                    Text("上一页")
                }
                Text("${(progress * 100).roundToInt()}%")
                TextButton(onClick = onNext, enabled = canNext) {
                    Text("下一页")
                }
            }
            LinearProgressIndicator(
                progress = { progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderPanelButton(label = "目录", icon = "☰")
                ReaderPanelButton(label = "夜间", icon = "☾")
                ReaderPanelButton(label = "设置", icon = "⚙")
            }
        }
    }
}

@Composable
private fun ReaderPanelButton(label: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(icon, fontSize = 28.sp)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ReaderTextPage(
    page: ReaderPageSlice,
    sourceText: String,
    modifier: Modifier,
    textColor: Color,
    bodyFontSize: TextUnit,
    bodyLineHeight: TextUnit,
) {
    Text(
        modifier = modifier.clipToBounds(),
        text = page.textFrom(sourceText),
        color = textColor,
        fontSize = bodyFontSize,
        lineHeight = bodyLineHeight,
        letterSpacing = 0.sp,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun ReaderMessage(message: String, onBack: () -> Unit) {
    ScreenColumn {
        Text("阅读", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(message)
        OutlinedButton(onClick = onBack) {
            Text("返回书架")
        }
    }
}

private data class ReaderPageSlice(
    val chapterTitle: String,
    val startOffset: Int,
    val endOffset: Int,
)

private fun ReaderPageSlice.textFrom(sourceText: String): String {
    if (sourceText.isEmpty()) return ""
    val start = startOffset.coerceIn(0, sourceText.length)
    val end = endOffset.coerceIn(start, sourceText.length)
    return sourceText.substring(start, end).trim()
}

private data class ReaderPaginationSpec(
    val fallbackTitle: String,
    val bodyWidthPx: Int,
    val bodyHeightPx: Int,
    val fontPx: Float,
    val lineHeightPx: Float,
    val linesPerPage: Int,
)

@Composable
private fun rememberReaderPaginationSpec(
    title: String,
    isAndroid: Boolean,
    isDesktop: Boolean,
    maxWidthPx: Int,
    maxHeightPx: Int,
): ReaderPaginationSpec {
    val density = LocalDensity.current
    return remember(title, isAndroid, isDesktop, maxWidthPx, maxHeightPx, density) {
        val fallbackTitle = title.ifBlank { "正文" }
        val bodyFontSize = if (isAndroid) 28.sp else 22.sp
        val bodyLineHeight = if (isAndroid) 46.sp else 34.sp
        val bodyWidthPx: Int
        val bodyHeightPx: Int
        if (isDesktop) {
            val verticalChromePx = with(density) { (24.dp * 2 + 46.dp + 28.dp).toPx().roundToInt() }
            val pageOuterHeightPx = (maxHeightPx - verticalChromePx).coerceAtLeast(with(density) { 420.dp.toPx().roundToInt() })
            val spreadOuterWidthPx = (pageOuterHeightPx * DesktopSpreadAspectRatio).roundToInt()
            bodyWidthPx = ((spreadOuterWidthPx - with(density) { (34.dp * 2 + 30.dp + 1.dp).toPx().roundToInt() }) / 2)
                .coerceAtLeast(240)
            bodyHeightPx = (pageOuterHeightPx - with(density) { 60.dp.toPx().roundToInt() })
                .coerceAtLeast(260)
        } else {
            bodyWidthPx = (maxWidthPx - with(density) { 48.dp.toPx().roundToInt() }).coerceAtLeast(220)
            bodyHeightPx = (maxHeightPx - with(density) { 68.dp.toPx().roundToInt() })
                .coerceAtLeast(260)
        }
        val fontPx = with(density) { bodyFontSize.toPx() }.coerceAtLeast(1f)
        val lineHeightPx = with(density) { bodyLineHeight.toPx() }.coerceAtLeast(fontPx)
        val lineSafetyMargin = if (isDesktop) 2 else 1
        val linesPerPage = ((bodyHeightPx / lineHeightPx).toInt() - lineSafetyMargin).coerceAtLeast(1)

        ReaderPaginationSpec(
            fallbackTitle = fallbackTitle,
            bodyWidthPx = bodyWidthPx,
            bodyHeightPx = bodyHeightPx,
            fontPx = fontPx,
            lineHeightPx = lineHeightPx,
            linesPerPage = linesPerPage,
        )
    }
}

private suspend fun ReaderDocument.paginateForReader(
    spec: ReaderPaginationSpec,
    onPagesChanged: (List<ReaderPageSlice>) -> Unit = {},
): List<ReaderPageSlice> {
    val fallbackTitle = title.ifBlank { "正文" }
    if (!isReadable) return listOf(ReaderPageSlice(fallbackTitle, 0, 0))

    val pages = mutableListOf<ReaderPageSlice>()
    val pageLines = mutableListOf<ReaderLine>()
    val blocks = text.toChapterBlocks(spec.fallbackTitle)
    suspend fun flushPage(force: Boolean = false) {
        if (pageLines.isEmpty() && !force) return
        val firstLine = pageLines.firstOrNull()
        val lastLine = pageLines.lastOrNull()
        pages += ReaderPageSlice(
            chapterTitle = firstLine?.chapterTitle ?: spec.fallbackTitle,
            startOffset = firstLine?.absoluteStart ?: 0,
            endOffset = lastLine?.absoluteEnd ?: firstLine?.absoluteStart ?: 0,
        )
        pageLines.clear()
        onPagesChanged(pages.toList())
        if (pages.size % 16 == 0) {
            yield()
        }
    }

    suspend fun appendLine(line: ReaderLine) {
        if (pageLines.size >= spec.linesPerPage) {
            flushPage()
        }
        pageLines += line
    }

    blocks.forEachIndexed { blockIndex, block ->
        block.forEachReaderLine(spec) { line ->
            appendLine(line)
        }
        if (blockIndex < blocks.lastIndex && pageLines.isNotEmpty()) {
            appendLine(
                ReaderLine(
                    chapterTitle = block.title,
                    absoluteStart = block.absoluteStart + block.body.length,
                    absoluteEnd = block.absoluteStart + block.body.length,
                ),
            )
        }
    }
    flushPage()
    return pages.ifEmpty { listOf(ReaderPageSlice(fallbackTitle, 0, 0)) }
}

private data class ReaderLine(
    val chapterTitle: String,
    val absoluteStart: Int,
    val absoluteEnd: Int,
)

private suspend fun ChapterBlock.forEachReaderLine(
    spec: ReaderPaginationSpec,
    emit: suspend (ReaderLine) -> Unit,
) {
    var paragraphStart = 0
    while (paragraphStart <= body.length) {
        val nextBreak = body.indexOf('\n', paragraphStart)
        val paragraphEnd = if (nextBreak < 0) body.length else nextBreak
        if (paragraphEnd == paragraphStart) {
            emit(
                ReaderLine(
                    chapterTitle = title,
                    absoluteStart = absoluteStart + paragraphStart,
                    absoluteEnd = (absoluteStart + paragraphEnd + 1).coerceAtMost(absoluteStart + body.length),
                ),
            )
        } else if (!body.isBlankRange(paragraphStart, paragraphEnd)) {
            body.forEachReaderLine(
                chapterTitle = title,
                absoluteStart = absoluteStart,
                start = paragraphStart,
                end = paragraphEnd,
                spec = spec,
                emit = emit,
            )
        }
        if (nextBreak < 0) break
        paragraphStart = paragraphEnd + 1
    }
}

private suspend fun String.forEachReaderLine(
    chapterTitle: String,
    absoluteStart: Int,
    start: Int,
    end: Int,
    spec: ReaderPaginationSpec,
    emit: suspend (ReaderLine) -> Unit,
) {
    var lineStart = start
    var index = start
    var width = 0f

    suspend fun emitLine(endExclusive: Int) {
        val trimmedEnd = trimEndIndex(lineStart, endExclusive)
        if (trimmedEnd > lineStart) {
            emit(
                ReaderLine(
                    chapterTitle = chapterTitle,
                    absoluteStart = absoluteStart + lineStart,
                    absoluteEnd = absoluteStart + trimmedEnd,
                ),
            )
        }
        lineStart = endExclusive.skipLeadingWhitespace(this, end)
        index = lineStart
        width = 0f
    }

    while (index < end) {
        val char = this[index]
        val charWidth = spec.readerCharWidth(char)
        val wouldOverflow = width > 0f && width + charWidth > spec.bodyWidthPx
        if (wouldOverflow) {
            var breakAt = index
            if (isReaderLeadingPunctuation(char) && index > lineStart) {
                breakAt = index + 1
            } else {
                val preferred = findPreferredLineBreak(lineStart, index)
                if (preferred > lineStart) {
                    breakAt = preferred
                }
            }
            emitLine(breakAt.coerceAtMost(end))
        } else {
            width += charWidth
            index++
        }
    }

    if (lineStart < end) {
        emitLine(end)
    }
}

private fun String.isBlankRange(start: Int, end: Int): Boolean {
    for (index in start until end) {
        if (!this[index].isWhitespace()) return false
    }
    return true
}

private fun String.trimEndIndex(start: Int, end: Int): Int {
    var index = end
    while (index > start && this[index - 1].isWhitespace()) {
        index--
    }
    return index
}

private fun String.findPreferredLineBreak(start: Int, endExclusive: Int): Int {
    val searchStart = (endExclusive - 14).coerceAtLeast(start + 1)
    for (index in endExclusive downTo searchStart) {
        val char = this[index - 1]
        if (char.isWhitespace() || char in "，。！？；、,.!?;:") return index
    }
    return endExclusive
}

private fun ReaderPaginationSpec.readerCharWidth(char: Char): Float =
    when {
        char == '\t' -> fontPx * 2f
        char == ' ' -> fontPx * 0.45f
        char.code <= 0x007F -> fontPx * 0.58f
        char.isHalfWidthPunctuation() -> fontPx * 0.56f
        char.isCjkPunctuation() -> fontPx * 0.82f
        else -> fontPx
    }

private fun Char.isHalfWidthPunctuation(): Boolean =
    this in "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

private fun Char.isCjkPunctuation(): Boolean =
    this in "，。！？；：“”‘’（）《》〈〉【】「」『』、…—"

private fun isReaderLeadingPunctuation(char: Char): Boolean =
    char in "，。！？；：、,.!?;:)]}）】》〉」』"

private fun Int.skipLeadingWhitespace(source: String, limit: Int = source.length): Int {
    var index = this
    while (index < limit && source[index].isWhitespace()) {
        index++
    }
    return index
}

private data class ChapterBlock(
    val title: String,
    val body: String,
    val absoluteStart: Int,
)

private fun String.toChapterBlocks(fallbackTitle: String): List<ChapterBlock> {
    val normalized = replace("\r\n", "\n").replace('\r', '\n')
    val blocks = mutableListOf<ChapterBlock>()
    var chapterTitle = fallbackTitle
    val buffer = StringBuilder()
    var blockStart = 0
    var absoluteIndex = 0

    fun flush() {
        val body = buffer.toString().trim()
        if (body.isNotEmpty()) {
            blocks += ChapterBlock(chapterTitle, body, blockStart)
        }
        buffer.clear()
    }

    normalized.lineSequence().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val lineWithBreak = "$line\n"
        val trimmed = line.trim()
        if (trimmed.isLikelyChapterTitle()) {
            flush()
            chapterTitle = trimmed
            blockStart = absoluteIndex + lineWithBreak.length
        } else {
            if (buffer.isEmpty()) blockStart = absoluteIndex
            buffer.append(lineWithBreak)
        }
        absoluteIndex += lineWithBreak.length
    }

    flush()
    return blocks.ifEmpty { listOf(ChapterBlock(fallbackTitle, normalized, 0)) }
}

private fun String.isLikelyChapterTitle(): Boolean =
    length in 2..42 && ChapterTitleRegex.matches(this)

private fun BookLocator.toPageIndex(pageCount: Int): Int {
    if (pageCount <= 1) return 0
    return (globalProgress.coerceIn(0.0, 1.0) * (pageCount - 1)).roundToInt()
}

private fun normalizePageIndex(index: Int, pageCount: Int, isDesktop: Boolean): Int {
    if (pageCount <= 1) return 0
    val bounded = index.coerceIn(0, pageCount - 1)
    return if (isDesktop) bounded - (bounded % 2) else bounded
}

private fun Int.pageProgress(pageCount: Int): Double =
    if (pageCount <= 1) 0.0 else toDouble() / (pageCount - 1).toDouble()

private fun saveReaderProgress(
    scope: CoroutineScope,
    progressRepository: ReadingProgressRepository?,
    book: BookshelfBook,
    pageIndex: Int,
    pageCount: Int,
    charOffset: Int,
    deviceId: String,
) {
    val repository = progressRepository ?: return
    scope.launch {
        val globalProgress = pageIndex.pageProgress(pageCount)
        val locator = when (book.format.lowercase()) {
            "epub" -> EpubLocator(
                chapterHref = "local-text",
                chapterIndex = pageIndex,
                chapterProgress = 0.0,
                globalProgress = globalProgress,
            )
            "pdf" -> PdfLocator(pageIndex = pageIndex, pageOffsetY = 0.0, globalProgress = globalProgress)
            else -> TxtLocator(
                charOffset = charOffset.toLong(),
                paragraphIndex = null,
                globalProgress = globalProgress,
            )
        }
        repository.saveLocalProgress(
            bookHash = book.bookHash,
            format = book.format,
            locator = locator,
            deviceId = deviceId,
        )
    }
}

@Composable
private fun SettingsPage(
    settingsRepository: LocalSettingsRepository?,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    var serverUrl by remember { mutableStateOf("http://localhost:8080") }
    val authLabel = when (settingsRepository?.currentAuthMode()) {
        AuthMode.LoggedIn -> settingsRepository.userEmail()?.let { "已登录：$it" } ?: "已登录"
        AuthMode.Guest -> "游客模式"
        null -> "未登录"
    }

    ScreenColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onBack) {
                Text("返回书架")
            }
        }
        Text(authLabel)
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("服务器地址") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
        )
        Text("同步：后续接入 Ktor 服务。")
        Text("本地存储：应用私有目录。")
        OutlinedButton(onClick = onLogout) {
            Text("退出登录状态")
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}
