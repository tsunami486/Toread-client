package com.example.toread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.toread.auth.AuthClient
import com.example.toread.auth.AuthClientResult
import com.example.toread.auth.ServerConnectionResult
import com.example.toread.importing.BookImportService
import com.example.toread.importing.createToreadDatabase
import com.example.toread.library.BookshelfBook
import com.example.toread.library.BookshelfRepository
import com.example.toread.progress.ApplyProgressResult
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
import com.example.toread.sync.RemoteProgressFetchResult
import com.example.toread.sync.SyncQueueProcessor
import com.example.toread.ui.screens.AuthPage
import com.example.toread.ui.screens.BookshelfPage
import com.example.toread.ui.screens.ImportPage
import com.example.toread.ui.screens.ReaderPage
import com.example.toread.ui.screens.SettingsPage
import com.example.toread.ui.theme.ToreadTheme
import kotlinx.coroutines.launch

private enum class ToreadScreen {
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
    val snackbarHostState = remember { SnackbarHostState() }
    var screen by remember { mutableStateOf(ToreadScreen.Bookshelf) }
    var selectedBook by remember { mutableStateOf<BookshelfBook?>(null) }
    var books by remember { mutableStateOf<List<BookshelfBook>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var dynamicColorEnabled by remember { mutableStateOf(false) }
    var syncRunning by remember { mutableStateOf(false) }

    fun showAppMessage(message: String) {
        appScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val databaseResult = remember { runCatching { createToreadDatabase() } }
    val database = databaseResult.getOrNull()
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

    suspend fun pullRemoteProgressForBooks(): RemoteProgressPullSummary {
        val source = remoteProgressSource
            ?: return RemoteProgressPullSummary(unavailableReason = "远端进度服务未初始化")
        val repository = progressRepository
            ?: return RemoteProgressPullSummary(unavailableReason = "本地进度服务未初始化")
        val localBooks = books.ifEmpty { bookshelfRepository?.listBooks().orEmpty() }

        var applied = 0
        var notFound = 0
        var failed = 0
        var lastError: String? = null

        for (book in localBooks) {
            when (val result = source.fetch(book.bookHash)) {
                is RemoteProgressFetchResult.Found -> {
                    val applyResult = repository.applyRemoteProgress(
                        bookHash = book.bookHash,
                        format = book.format,
                        locator = result.progress.locator,
                        deviceId = null,
                        updatedAt = result.progress.updatedAt,
                    )
                    if (applyResult is ApplyProgressResult.Applied) {
                        applied++
                    }
                }
                RemoteProgressFetchResult.NotFound -> notFound++
                is RemoteProgressFetchResult.Unavailable -> {
                    return RemoteProgressPullSummary(
                        checked = applied + notFound + failed,
                        applied = applied,
                        notFound = notFound,
                        failed = failed,
                        unavailableReason = result.reason,
                    )
                }
                is RemoteProgressFetchResult.Failed -> {
                    failed++
                    lastError = result.message
                }
            }
        }

        return RemoteProgressPullSummary(
            checked = localBooks.size,
            applied = applied,
            notFound = notFound,
            failed = failed,
            lastError = lastError,
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
                val remoteSummary = pullRemoteProgressForBooks()
                val resultMessage = when {
                    summary == null -> "刷新失败：同步服务未初始化"
                    summary.failed > 0 -> "刷新失败：${summary.lastError ?: "请稍后重试"}"
                    summary.skipped > 0 -> "刷新失败：${summary.lastError ?: "当前为离线状态"}"
                    remoteSummary.unavailableReason != null -> "刷新失败：${remoteSummary.unavailableReason}"
                    remoteSummary.failed > 0 -> "刷新失败：${remoteSummary.lastError ?: "云端进度获取失败"}"
                    remoteSummary.applied > 0 -> "刷新成功：已更新 ${remoteSummary.applied} 本书的云端进度"
                    else -> "刷新成功"
                }
                showAppMessage(resultMessage)
                refreshKey++
            } catch (throwable: Throwable) {
                showAppMessage("刷新失败：${throwable.message ?: "未知错误"}")
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

    LaunchedEffect(databaseResult.exceptionOrNull()) {
        databaseResult.exceptionOrNull()?.let {
            showAppMessage("本地数据库初始化失败：${it.message ?: "未知错误"}")
        }
    }

    LaunchedEffect(settingsRepository, authClient) {
        dynamicColorEnabled = settingsRepository?.dynamicColorEnabled() ?: false
        if (settingsRepository == null || authClient == null) return@LaunchedEffect

        when (authClient.checkServerConnection()) {
            ServerConnectionResult.Success -> Unit
            is ServerConnectionResult.Failure -> {
                showAppMessage("连接服务器失败，当前为离线状态。")
                return@LaunchedEffect
            }
        }

        if (settingsRepository.currentAuthMode() == AuthMode.LoggedIn) {
            val refreshToken = settingsRepository.refreshToken()
            if (!refreshToken.isNullOrBlank()) {
                when (val result = authClient.refresh(refreshToken)) {
                    is AuthClientResult.Success -> {
                        settingsRepository.setLoggedIn(result.session.email)
                        settingsRepository.setRefreshToken(result.session.refreshToken)
                    }
                    is AuthClientResult.Failure -> {
                        showAppMessage("刷新登录状态失败：${result.message}")
                    }
                }
            }
        }
    }

    LaunchedEffect(bookshelfRepository, refreshKey) {
        books = runCatching { bookshelfRepository?.listBooks().orEmpty() }
            .onFailure { showAppMessage("读取书架失败：${it.message ?: "未知错误"}") }
            .getOrDefault(emptyList())
    }

    val isReaderScreen = screen == ToreadScreen.Reader

    ToreadTheme(useDynamicColor = dynamicColorEnabled) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(appContentPadding(platform.kind, isReaderScreen)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    when (screen) {
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
                            isRefreshing = syncRunning,
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
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }
}

private fun appContentPadding(platformKind: PlatformKind, isReaderScreen: Boolean): Modifier =
    when {
        isReaderScreen -> Modifier
        platformKind == PlatformKind.Desktop -> Modifier.padding(24.dp)
        else -> Modifier.safeContentPadding()
    }

private data class RemoteProgressPullSummary(
    val checked: Int = 0,
    val applied: Int = 0,
    val notFound: Int = 0,
    val failed: Int = 0,
    val lastError: String? = null,
    val unavailableReason: String? = null,
)
