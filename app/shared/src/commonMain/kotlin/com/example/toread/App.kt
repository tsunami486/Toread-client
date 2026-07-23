package com.example.toread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.toread.auth.AuthClient
import com.example.toread.auth.AuthClientResult
import com.example.toread.config.ServerBaseUrlConfig
import com.example.toread.importing.BookImportService
import com.example.toread.importing.createToreadDatabase
import com.example.toread.library.BookshelfBook
import com.example.toread.library.BookshelfRepository
import com.example.toread.network.ServerConnectionMonitor
import com.example.toread.network.ServerConnectionState
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
import com.example.toread.sync.SyncRunSummary
import com.example.toread.sync.SyncQueueProcessor
import com.example.toread.ui.screens.AuthPage
import com.example.toread.ui.screens.BookshelfLayoutMode
import com.example.toread.ui.screens.BookshelfPage
import com.example.toread.ui.screens.ImportPage
import com.example.toread.ui.screens.ReaderPage
import com.example.toread.ui.screens.SettingsPage
import com.example.toread.ui.text.ToreadStrings
import com.example.toread.ui.theme.ToreadTheme
import kotlinx.coroutines.flow.collect
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
    var autoSyncEnabled by remember { mutableStateOf(true) }
    var bookshelfLayoutMode by remember { mutableStateOf(BookshelfLayoutMode.Grid) }
    var syncRunning by remember { mutableStateOf(false) }
    var serverConnectionState by remember {
        mutableStateOf<ServerConnectionState>(ServerConnectionState.Checking(startedAt = ""))
    }
    var previousServerConnectionState by remember { mutableStateOf<ServerConnectionState?>(null) }

    fun showAppMessage(message: String) {
        appScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(ToreadStrings.safeUserVisibleMessage(message))
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
    val serverBaseUrl = remember(platform.kind) { ServerBaseUrlConfig.resolve(platform.kind) }
    val authClient = remember(settingsRepository, tokenStore, serverBaseUrl) {
        if (settingsRepository != null) {
            AuthClient(
                baseUrlProvider = { serverBaseUrl },
                tokenStore = tokenStore,
            )
        } else {
            null
        }
    }
    val serverConnectionMonitor = remember(appScope, authClient) {
        authClient?.let { client ->
            ServerConnectionMonitor(
                scope = appScope,
                healthCheck = { client.checkServerConnection() },
            )
        }
    }
    val progressSyncUploader = remember(settingsRepository, tokenStore, serverBaseUrl) {
        if (settingsRepository != null) {
            HttpReadingProgressSyncUploader(
                baseUrlProvider = { serverBaseUrl },
                accessTokenProvider = { tokenStore.getToken() },
            )
        } else {
            null
        }
    }
    val bookMetadataSyncUploader = remember(settingsRepository, tokenStore, serverBaseUrl) {
        if (settingsRepository != null) {
            HttpBookMetadataSyncUploader(
                baseUrlProvider = { serverBaseUrl },
                accessTokenProvider = { tokenStore.getToken() },
            )
        } else {
            null
        }
    }
    val remoteProgressSource = remember(settingsRepository, tokenStore, serverBaseUrl) {
        if (settingsRepository != null) {
            HttpReadingProgressRemoteSource(
                baseUrlProvider = { serverBaseUrl },
                accessTokenProvider = { tokenStore.getToken() },
            )
        } else {
            null
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
    val progressSyncScheduler = remember(appScope, syncQueueProcessor, autoSyncEnabled, serverConnectionMonitor) {
        ProgressSyncScheduler(
            scope = appScope,
            syncQueueProcessorProvider = { if (autoSyncEnabled) syncQueueProcessor else null },
            onFlushFinished = { summary ->
                when {
                    summary.uploaded > 0 -> serverConnectionMonitor?.markOnline("auto_progress_flush")
                    (summary.failed > 0 || summary.skipped > 0) && summary.lastError.shouldTriggerServerCheck() -> {
                        serverConnectionMonitor?.triggerCheck("auto_progress_flush")
                    }
                }
            },
        )
    }

    suspend fun pullRemoteProgressForBooks(): RemoteProgressPullSummary {
        val source = remoteProgressSource
            ?: return RemoteProgressPullSummary(unavailableReason = ToreadStrings.RemoteProgressSourceUnavailable)
        val repository = progressRepository
            ?: return RemoteProgressPullSummary(unavailableReason = ToreadStrings.LocalProgressServiceUnavailable)
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

    suspend fun refreshAuthSessionIfNeeded() {
        val repository = settingsRepository ?: return
        val client = authClient ?: return
        if (repository.currentAuthMode() != AuthMode.LoggedIn) return

        val refreshToken = repository.refreshToken()
        if (refreshToken.isNullOrBlank()) return

        when (val result = client.refresh(refreshToken)) {
            is AuthClientResult.Success -> {
                repository.setLoggedIn(result.session.email)
                repository.setRefreshToken(result.session.refreshToken)
                serverConnectionMonitor?.markOnline("auth.refresh")
            }
            is AuthClientResult.Failure -> {
                if (result.isInvalidRefreshToken()) {
                    tokenStore.clear()
                    repository.clearAuthMode()
                    showAppMessage(ToreadStrings.LoginExpired)
                    return
                }
                showAppMessage(ToreadStrings.refreshAuthFailed(result.message))
                if (result.message.shouldTriggerServerCheck()) {
                    serverConnectionMonitor?.triggerCheck("auth_refresh_failed")
                }
            }
        }
    }

    fun updateConnectionAfterSync(
        summary: SyncRunSummary?,
        remoteSummary: RemoteProgressPullSummary,
        source: String,
    ) {
        when {
            summary?.uploaded?.let { it > 0 } == true ||
                remoteSummary.checked > 0 && remoteSummary.failed == 0 && remoteSummary.unavailableReason == null -> {
                serverConnectionMonitor?.markOnline(source)
            }
            summary?.failed?.let { it > 0 } == true ||
                summary?.skipped?.let { it > 0 } == true ||
                remoteSummary.failed > 0 ||
                remoteSummary.unavailableReason != null -> {
                if (
                    summary?.lastError.shouldTriggerServerCheck() ||
                    remoteSummary.lastError.shouldTriggerServerCheck() ||
                    remoteSummary.unavailableReason.shouldTriggerServerCheck()
                ) {
                    serverConnectionMonitor?.triggerCheck(source)
                }
            }
        }
    }

    fun runSyncAndRefresh(
        showResult: Boolean = true,
        source: String = "manual_refresh",
    ) {
        if (syncRunning) return
        syncRunning = true
        appScope.launch {
            try {
                bookshelfRepository?.refreshMissingEpubCovers()
                bookshelfRepository?.refreshMissingEpubMetadata()
                bookshelfRepository?.ensurePendingBookMetadataQueued()
                val summary = syncQueueProcessor?.flushPending(limit = Int.MAX_VALUE)
                val remoteSummary = pullRemoteProgressForBooks()
                updateConnectionAfterSync(summary, remoteSummary, source)
                val resultMessage = when {
                    summary == null -> ToreadStrings.refreshFailed(ToreadStrings.SyncServiceUnavailable)
                    summary.failed > 0 -> ToreadStrings.refreshFailed(summary.lastError ?: ToreadStrings.UnknownError)
                    summary.skipped > 0 -> ToreadStrings.refreshFailed(summary.lastError ?: ToreadStrings.CurrentOffline)
                    remoteSummary.unavailableReason != null -> ToreadStrings.refreshFailed(remoteSummary.unavailableReason)
                    remoteSummary.failed > 0 -> ToreadStrings.refreshFailed(
                        remoteSummary.lastError ?: ToreadStrings.RemoteProgressFetchFailed,
                    )
                    remoteSummary.applied > 0 -> ToreadStrings.remoteProgressUpdated(remoteSummary.applied)
                    else -> ToreadStrings.RefreshSuccess
                }
                if (showResult) {
                    showAppMessage(resultMessage)
                }
                refreshKey++
            } catch (throwable: Throwable) {
                serverConnectionMonitor?.triggerCheck(source)
                if (showResult) {
                    showAppMessage(ToreadStrings.refreshFailed(ToreadStrings.RefreshUnexpectedFailure))
                }
            } finally {
                syncRunning = false
            }
        }
    }

    DisposableEffect(progressSyncScheduler, serverConnectionMonitor) {
        AppBackgroundNotifier.setOnEnterBackground {
            serverConnectionMonitor?.triggerCheck("app_background")
            progressSyncScheduler.flushNow()
        }
        onDispose {
            AppBackgroundNotifier.setOnEnterBackground(null)
        }
    }

    DisposableEffect(serverConnectionMonitor) {
        serverConnectionMonitor?.start()
        onDispose {
            serverConnectionMonitor?.stop()
        }
    }

    LaunchedEffect(serverConnectionMonitor) {
        val monitor = serverConnectionMonitor
        if (monitor == null) {
            serverConnectionState = ServerConnectionState.Offline(
                reason = ToreadStrings.ServerConnectionMonitorUnavailable,
                checkedAt = "",
                retrying = false,
            )
            return@LaunchedEffect
        }
        monitor.state.collect { state ->
            serverConnectionState = state
        }
    }

    LaunchedEffect(databaseResult.exceptionOrNull()) {
        databaseResult.exceptionOrNull()?.let {
            showAppMessage(ToreadStrings.databaseInitFailed(it.message))
        }
    }

    LaunchedEffect(settingsRepository) {
        dynamicColorEnabled = settingsRepository?.dynamicColorEnabled() ?: false
        autoSyncEnabled = settingsRepository?.autoSyncEnabled() ?: true
        bookshelfLayoutMode = BookshelfLayoutMode.fromStoredValue(settingsRepository?.bookshelfLayoutMode())
            ?: BookshelfLayoutMode.Grid
    }

    LaunchedEffect(serverConnectionState, settingsRepository, authClient, autoSyncEnabled) {
        val previous = previousServerConnectionState
        previousServerConnectionState = serverConnectionState

        when (serverConnectionState) {
            is ServerConnectionState.Offline -> {
                if (previous !is ServerConnectionState.Offline) {
                    showAppMessage(ToreadStrings.ServerOfflineSnackbar)
                }
            }
            is ServerConnectionState.Online -> {
                val wasOffline = previous is ServerConnectionState.Offline
                val shouldRefreshAuth = previous == null ||
                    previous is ServerConnectionState.Checking ||
                    wasOffline
                if (wasOffline) {
                    showAppMessage(ToreadStrings.ServerReconnectedSnackbar)
                }
                if (shouldRefreshAuth) {
                    refreshAuthSessionIfNeeded()
                }
                if (wasOffline && autoSyncEnabled) {
                    runSyncAndRefresh(showResult = false, source = "server_reconnected")
                }
            }
            is ServerConnectionState.Checking -> Unit
        }
    }

    LaunchedEffect(bookshelfRepository) {
        val repository = bookshelfRepository ?: return@LaunchedEffect
        val repaired = runCatching {
            val updated = repository.refreshMissingEpubMetadata()
            if (updated > 0) repository.ensurePendingBookMetadataQueued()
            updated
        }
            .onFailure { showAppMessage(ToreadStrings.readBookshelfFailed(it.message)) }
            .getOrDefault(0)
        if (repaired > 0) refreshKey++
    }

    LaunchedEffect(bookshelfRepository, refreshKey) {
        books = runCatching { bookshelfRepository?.listBooks().orEmpty() }
            .onFailure { showAppMessage(ToreadStrings.readBookshelfFailed(it.message)) }
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
                            isDesktop = platform.kind == PlatformKind.Desktop,
                            layoutMode = bookshelfLayoutMode,
                            onLayoutModeChange = { mode ->
                                bookshelfLayoutMode = mode
                                settingsRepository?.setBookshelfLayoutMode(mode.storedValue)
                            },
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
                            autoSyncEnabled = autoSyncEnabled,
                            onDynamicColorEnabledChange = { enabled ->
                                settingsRepository?.setDynamicColorEnabled(enabled)
                                dynamicColorEnabled = enabled
                            },
                            onAutoSyncEnabledChange = { enabled ->
                                settingsRepository?.setAutoSyncEnabled(enabled)
                                autoSyncEnabled = enabled
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
                            onClearCache = {
                                showAppMessage(ToreadStrings.NoSafeCacheToClear)
                            },
                        )
                    }
                }
                val offlineState = serverConnectionState as? ServerConnectionState.Offline
//                if (offlineState != null && !isReaderScreen) {
//                    ServerConnectionOfflineBanner(
//                        state = offlineState,
//                        modifier = Modifier
//                            .align(Alignment.TopCenter)
//                            .padding(horizontal = 16.dp, vertical = 72.dp),
//                    )
//                }
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

@Composable
private fun ServerConnectionOfflineBanner(
    state: ServerConnectionState.Offline,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = ToreadStrings.offlineBanner(state.reason),
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun appContentPadding(platformKind: PlatformKind, isReaderScreen: Boolean): Modifier =
    when {
        isReaderScreen -> Modifier
        platformKind == PlatformKind.Desktop -> Modifier.padding(horizontal = 24.dp)
        else -> Modifier.windowInsetsPadding(
            WindowInsets.safeContent.union(
                WindowInsets(left = 24.dp, right = 24.dp),
            ),
        )
    }

private fun String?.shouldTriggerServerCheck(): Boolean {
    val message = this?.trim().orEmpty()
    if (message.isBlank()) return false
    return !message.contains("access token", ignoreCase = true) &&
        !message.contains("登录", ignoreCase = true) &&
        !message.contains("token", ignoreCase = true)
}

private fun AuthClientResult.Failure.isInvalidRefreshToken(): Boolean =
    error == "invalid_refresh_token" ||
        message.contains("refresh token 无效", ignoreCase = true) ||
        message.contains("refresh token", ignoreCase = true) && message.contains("过期")

private data class RemoteProgressPullSummary(
    val checked: Int = 0,
    val applied: Int = 0,
    val notFound: Int = 0,
    val failed: Int = 0,
    val lastError: String? = null,
    val unavailableReason: String? = null,
)
