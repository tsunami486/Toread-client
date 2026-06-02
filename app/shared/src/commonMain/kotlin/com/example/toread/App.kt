package com.example.toread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.toread.importing.BookImportService
import com.example.toread.importing.createToreadDatabase
import com.example.toread.library.BookshelfBook
import com.example.toread.library.BookshelfRepository
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.reader.ReaderContentLoader
import com.example.toread.settings.AuthMode
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.ui.screens.AuthPage
import com.example.toread.ui.screens.BookshelfPage
import com.example.toread.ui.screens.CheckingAuthPage
import com.example.toread.ui.screens.ImportPage
import com.example.toread.ui.screens.ReaderPage
import com.example.toread.ui.screens.SettingsPage

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
                        .padding(if (isReaderScreen) 0.dp else 12.dp),
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
                            onSync = { refreshKey++ },
                        )
                        ToreadScreen.Import -> ImportPage(
                            importService = importService,
                            onImported = { refreshKey++ },
                            onBack = {
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
