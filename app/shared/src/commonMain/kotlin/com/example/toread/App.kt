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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.toread.auth.AuthClient
import com.example.toread.auth.AuthClientResult
import com.example.toread.importing.BookImportService
import com.example.toread.importing.createToreadDatabase
import com.example.toread.library.BookshelfBook
import com.example.toread.library.BookshelfRepository
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.platform.AppBackgroundNotifier
import com.example.toread.platform.SecureTokenStore
import com.example.toread.reader.ReaderContentLoader
import com.example.toread.settings.AuthMode
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.sync.HttpReadingProgressSyncUploader
import com.example.toread.sync.HttpReadingProgressRemoteSource
import com.example.toread.sync.HttpBookMetadataSyncUploader
import com.example.toread.sync.ProgressSyncScheduler
import com.example.toread.sync.SyncQueueProcessor
import com.example.toread.ui.screens.AuthPage
import com.example.toread.ui.screens.BookshelfPage
import com.example.toread.ui.screens.CheckingAuthPage
import com.example.toread.ui.screens.ImportPage
import com.example.toread.ui.screens.ReaderPage
import com.example.toread.ui.screens.SettingsPage
import com.example.toread.ui.theme.ToreadTheme
import kotlinx.coroutines.launch

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
    val appScope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(ToreadScreen.CheckingAuth) }
    var selectedBook by remember { mutableStateOf<BookshelfBook?>(null) }
    var books by remember { mutableStateOf<List<BookshelfBook>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var databaseStatus by remember { mutableStateOf<String?>(null) }
    var dynamicColorEnabled by remember { mutableStateOf(false) }
    var syncRunning by remember { mutableStateOf(false) }

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
    val tokenStore = remember { SecureTokenStore() }
    val defaultServerBaseUrl = remember(platform.kind) {
        when (platform.kind) {
            PlatformKind.Android -> "http://10.0.2.2:8080"
            PlatformKind.Desktop -> "http://localhost:8080"
        }
    }
    val authClient = remember(settingsRepository, tokenStore, defaultServerBaseUrl) {
        settingsRepository?.let { repository ->
            AuthClient(
                baseUrlProvider = { repository.serverBaseUrl(defaultServerBaseUrl) },
                tokenStore = tokenStore,
            )
        }
    }
    val progressSyncUploader = remember(settingsRepository, tokenStore, defaultServerBaseUrl) {
        settingsRepository?.let { repository ->
            HttpReadingProgressSyncUploader(
                baseUrlProvider = { repository.serverBaseUrl(defaultServerBaseUrl) },
                accessTokenProvider = { tokenStore.getToken() },
            )
        }
    }
    val bookMetadataSyncUploader = remember(settingsRepository, tokenStore, defaultServerBaseUrl) {
        settingsRepository?.let { repository ->
            HttpBookMetadataSyncUploader(
                baseUrlProvider = { repository.serverBaseUrl(defaultServerBaseUrl) },
                accessTokenProvider = { tokenStore.getToken() },
            )
        }
    }
    val remoteProgressSource = remember(settingsRepository, tokenStore, defaultServerBaseUrl) {
        settingsRepository?.let { repository ->
            HttpReadingProgressRemoteSource(
                baseUrlProvider = { repository.serverBaseUrl(defaultServerBaseUrl) },
                accessTokenProvider = { tokenStore.getToken() },
            )
        }
    }
    val syncQueueProcessor = remember(database, progressSyncUploader, bookMetadataSyncUploader) {
        if (database != null && progressSyncUploader != null) {
            SyncQueueProcessor(
                database = database,
                progressUploader = progressSyncUploader,
                bookMetadataUploader = bookMetadataSyncUploader,
            )
        } else {
            null
        }
    }
    val progressSyncScheduler = remember(appScope, syncQueueProcessor) {
        ProgressSyncScheduler(
            scope = appScope,
            syncQueueProcessorProvider = { syncQueueProcessor },
        )
    }

    fun runSyncAndRefresh() {
        if (syncRunning) return
        syncRunning = true
        appScope.launch {
            try {
                bookshelfRepository?.refreshMissingEpubCovers()
                bookshelfRepository?.ensurePendingBookMetadataQueued()
                val summary = syncQueueProcessor?.flushPending(limit = Int.MAX_VALUE)
                if (summary != null && summary.failed > 0) {
                    databaseStatus = "同步失败：${summary.lastError ?: "请稍后重试"}"
                } else if (summary != null && summary.skipped > 0) {
                    databaseStatus = "同步暂未执行：${summary.lastError ?: "请检查登录状态或服务器设置"}"
                } else if (summary != null && summary.uploaded > 0) {
                    databaseStatus = null
                }
                refreshKey++
            } finally {
                syncRunning = false
            }
        }
    }

    DisposableEffect(progressSyncScheduler) {
        AppBackgroundNotifier.setOnEnterBackground {
            progressSyncScheduler.flushNow()
        }
        onDispose {
            AppBackgroundNotifier.setOnEnterBackground(null)
        }
    }

    LaunchedEffect(settingsRepository, authClient) {
        dynamicColorEnabled = settingsRepository?.dynamicColorEnabled() ?: false
        if (settingsRepository?.currentAuthMode() == AuthMode.LoggedIn) {
            val refreshToken = settingsRepository.refreshToken()
            if (authClient != null && !refreshToken.isNullOrBlank()) {
                when (val result = authClient.refresh(refreshToken)) {
                    is AuthClientResult.Success -> {
                        settingsRepository.setLoggedIn(result.session.email)
                        settingsRepository.setRefreshToken(result.session.refreshToken)
                    }
                    is AuthClientResult.Failure -> {
                        databaseStatus = "刷新登录状态失败：${result.message}"
                    }
                }
            }
        }
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

    ToreadTheme(useDynamicColor = dynamicColorEnabled) {
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
                        .fillMaxSize(),
                ) {
                    when (screen) {
                        ToreadScreen.CheckingAuth -> CheckingAuthPage()
                        ToreadScreen.Auth -> AuthPage(
                            settingsRepository = settingsRepository,
                            authClient = authClient,
                            onAuthenticated = {
                                refreshKey++
                                screen = ToreadScreen.Bookshelf
                                runSyncAndRefresh()
                            },
                            onGuestMode = {
                                appScope.launch {
                                    tokenStore.clear()
                                    settingsRepository?.setGuestMode()
                                    refreshKey++
                                    screen = ToreadScreen.Bookshelf
                                }
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
                            onSync = { runSyncAndRefresh() },
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
                            progressSyncScheduler = progressSyncScheduler,
                            remoteProgressSource = remoteProgressSource,
                            progressSaveScope = appScope,
                            readerContentLoader = readerContentLoader,
                            onProgressSyncFinished = { refreshKey++ },
                            onBack = {
                                refreshKey++
                                screen = ToreadScreen.Bookshelf
                            },
                        )
                        ToreadScreen.Settings -> SettingsPage(
                            settingsRepository = settingsRepository,
                            dynamicColorEnabled = dynamicColorEnabled,
                            defaultServerBaseUrl = defaultServerBaseUrl,
                            onDynamicColorEnabledChange = { enabled ->
                                settingsRepository?.setDynamicColorEnabled(enabled)
                                dynamicColorEnabled = enabled
                            },
                            onBack = {
                                refreshKey++
                                screen = ToreadScreen.Bookshelf
                            },
                            onLogout = {
                                appScope.launch {
                                    tokenStore.clear()
                                    settingsRepository?.clearAuthMode()
                                    selectedBook = null
                                    screen = ToreadScreen.Auth
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
