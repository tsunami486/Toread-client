package com.example.toread

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.toread.importing.BookImportResult
import com.example.toread.importing.BookImportService
import com.example.toread.importing.createToreadDatabase
import com.example.toread.library.BookshelfBook
import com.example.toread.library.BookshelfRepository
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.reader.BookLocator
import com.example.toread.reader.EpubLocator
import com.example.toread.reader.PdfLocator
import com.example.toread.reader.ReaderContentLoader
import com.example.toread.reader.ReaderDocument
import com.example.toread.reader.TxtLocator
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ReaderPageChars = 2600

private enum class ToreadScreen(val label: String) {
    Splash("启动"),
    Login("登录"),
    Bookshelf("书架"),
    Import("导入"),
    Reader("阅读"),
    Settings("设置"),
}

@Composable
@Preview
fun App() {
    ToreadApp()
}

@Composable
fun ToreadApp() {
    var screen by remember { mutableStateOf(ToreadScreen.Splash) }
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
    val readerContentLoader = remember { ReaderContentLoader() }

    LaunchedEffect(bookshelfRepository, refreshKey) {
        books = runCatching { bookshelfRepository?.listBooks().orEmpty() }
            .onFailure { databaseStatus = "读取书架失败：${it.message ?: "未知错误"}" }
            .getOrDefault(emptyList())
    }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(current = screen, onNavigate = { screen = it })
                HorizontalDivider()
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
                        .padding(24.dp),
                ) {
                    when (screen) {
                        ToreadScreen.Splash -> SplashPage(
                            onEnter = { screen = ToreadScreen.Bookshelf },
                            onLogin = { screen = ToreadScreen.Login },
                        )
                        ToreadScreen.Login -> LoginPage(onDone = { screen = ToreadScreen.Bookshelf })
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
                            bookshelfRepository = bookshelfRepository,
                            progressRepository = progressRepository,
                            readerContentLoader = readerContentLoader,
                            onBack = {
                                refreshKey++
                                screen = ToreadScreen.Bookshelf
                            },
                        )
                        ToreadScreen.Settings -> SettingsPage(onBack = { screen = ToreadScreen.Bookshelf })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(current: ToreadScreen, onNavigate: (ToreadScreen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Toread", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("本地导入型阅读器", style = MaterialTheme.typography.bodyMedium)
            }
            Text(getPlatform().name, style = MaterialTheme.typography.labelMedium)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToreadScreen.entries.forEach { item ->
                if (item == current) {
                    Button(onClick = { onNavigate(item) }) {
                        Text(item.label)
                    }
                } else {
                    OutlinedButton(onClick = { onNavigate(item) }) {
                        Text(item.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashPage(onEnter: () -> Unit, onLogin: () -> Unit) {
    ScreenColumn {
        Text("开始阅读", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text("导入你本地已有的 EPUB、PDF 或 TXT 文件，在本机阅读并保存进度。")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onEnter) {
                Text("进入书架")
            }
            OutlinedButton(onClick = onLogin) {
                Text("登录")
            }
        }
    }
}

@Composable
private fun LoginPage(onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    ScreenColumn {
        Text("登录", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
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
            Button(onClick = onDone) {
                Text("登录")
            }
            OutlinedButton(onClick = onDone) {
                Text("离线进入")
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

    ScreenColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("书架", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSettings) {
                    Text("设置")
                }
                Button(onClick = onImport) {
                    Text("导入")
                }
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
            Card(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("还没有可阅读的书籍", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text("导入 EPUB、PDF 或 TXT 文件后，会在这里显示。TXT 可以直接阅读全文，EPUB 会做基础文本抽取。")
                    Button(onClick = onImport) {
                        Text("选择本地文件")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                filteredBooks.forEach { book ->
                    BookRow(book = book, onOpenBook = onOpenBook)
                }
            }
        }
    }
}

@Composable
private fun BookRow(book: BookshelfBook, onOpenBook: (BookshelfBook) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${book.format.uppercase()} · ${book.author ?: "未知作者"}")
                }
                Button(onClick = { onOpenBook(book) }) {
                    Text("打开")
                }
            }
            LinearProgressIndicator(
                progress = { book.progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("进度 ${(book.progress * 100).roundToInt()}% · 同步状态 ${book.syncStatus}")
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
    bookshelfRepository: BookshelfRepository?,
    progressRepository: ReadingProgressRepository?,
    readerContentLoader: ReaderContentLoader,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var document by remember(book?.bookHash) { mutableStateOf<ReaderDocument?>(null) }
    var pageIndex by remember(book?.bookHash) { mutableIntStateOf(0) }
    var error by remember(book?.bookHash) { mutableStateOf<String?>(null) }

    LaunchedEffect(book?.bookHash) {
        if (book == null) return@LaunchedEffect
        document = null
        error = null
        runCatching {
            readerContentLoader.load(book.filePath, book.format, book.title)
        }.fold(
            onSuccess = { loaded ->
                document = loaded
                val pageCount = loaded.pages().size
                val locator = bookshelfRepository?.progressLocator(book.bookHash)
                pageIndex = locator?.toPageIndex(pageCount) ?: 0
            },
            onFailure = { throwable ->
                error = "打开失败：${throwable.message ?: "未知错误"}"
            },
        )
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
    val pages = loadedDocument?.pages().orEmpty()
    val currentPageText = pages.getOrNull(pageIndex).orEmpty()
    val progress = if (pages.size <= 1) 0.0 else pageIndex.toDouble() / (pages.size - 1).toDouble()

    ScreenColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(book.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("${book.format.uppercase()} · 第 ${pageIndex + 1} / ${pages.size.coerceAtLeast(1)} 页")
            }
            OutlinedButton(onClick = onBack) {
                Text("返回书架")
            }
        }

        LinearProgressIndicator(progress = { progress.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())

        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    error != null -> Text(error ?: "")
                    loadedDocument == null -> Text("正在读取本地文件...")
                    !loadedDocument.isReadable -> Text(loadedDocument.message ?: "当前格式暂不能直接阅读。")
                    else -> Text(currentPageText, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                enabled = pageIndex > 0,
                onClick = {
                    pageIndex--
                    saveReaderProgress(scope, progressRepository, book, pageIndex, pages.size)
                },
            ) {
                Text("上一页")
            }
            Button(
                enabled = pageIndex < pages.lastIndex,
                onClick = {
                    pageIndex++
                    saveReaderProgress(scope, progressRepository, book, pageIndex, pages.size)
                },
            ) {
                Text("下一页")
            }
            TextButton(
                enabled = loadedDocument?.isReadable == true,
                onClick = { saveReaderProgress(scope, progressRepository, book, pageIndex, pages.size) },
            ) {
                Text("保存进度")
            }
        }
    }
}

private fun ReaderDocument.pages(): List<String> {
    if (!isReadable) return listOf(message ?: "")
    return text.chunked(ReaderPageChars).ifEmpty { listOf("") }
}

private fun BookLocator.toPageIndex(pageCount: Int): Int {
    if (pageCount <= 1) return 0
    return (globalProgress.coerceIn(0.0, 1.0) * (pageCount - 1)).roundToInt()
}

private fun saveReaderProgress(
    scope: kotlinx.coroutines.CoroutineScope,
    progressRepository: ReadingProgressRepository?,
    book: BookshelfBook,
    pageIndex: Int,
    pageCount: Int,
) {
    val repository = progressRepository ?: return
    scope.launch {
        val globalProgress = if (pageCount <= 1) 0.0 else pageIndex.toDouble() / (pageCount - 1).toDouble()
        val locator = when (book.format.lowercase()) {
            "epub" -> EpubLocator(
                chapterHref = "local-text",
                chapterIndex = pageIndex,
                chapterProgress = 0.0,
                globalProgress = globalProgress,
            )
            "pdf" -> PdfLocator(pageIndex = pageIndex, pageOffsetY = 0.0, globalProgress = globalProgress)
            else -> TxtLocator(
                charOffset = (pageIndex * ReaderPageChars).toLong(),
                paragraphIndex = null,
                globalProgress = globalProgress,
            )
        }
        repository.saveLocalProgress(
            bookHash = book.bookHash,
            format = book.format,
            locator = locator,
            deviceId = getPlatform().name,
        )
    }
}

@Composable
private fun SettingsPage(onBack: () -> Unit) {
    var serverUrl by remember { mutableStateOf("http://localhost:8080") }

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
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("服务器地址") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
        )
        Text("同步：已预留")
        Text("本地存储：应用私有目录")
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
