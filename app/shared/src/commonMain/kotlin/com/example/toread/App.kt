package com.example.toread

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.toread.auth.AuthClient
import com.example.toread.auth.AuthClientResult
import com.example.toread.auth.AuthDiagnostics
import com.example.toread.auth.AuthRefreshTrigger
import com.example.toread.auth.ambiguousRetryDelayMillis
import com.example.toread.auth.isConfirmedInvalidRefreshToken
import com.example.toread.auth.maySpendRefreshRotation
import com.example.toread.config.ServerBaseUrlConfig
import com.example.toread.importing.BookImportResult
import com.example.toread.importing.BookImportService
import com.example.toread.importing.createToreadDatabase
import com.example.toread.importing.droppedBookFiles
import com.example.toread.importing.droppedImportMessage
import com.example.toread.library.BookshelfBook
import com.example.toread.library.BookshelfRepository
import com.example.toread.library.prunedPinOrder
import com.example.toread.library.toggledPinOrder
import com.example.toread.logging.ToreadClientLog
import com.example.toread.network.ServerConnectionMonitor
import com.example.toread.network.ServerConnectionState
import com.example.toread.progress.ApplyProgressResult
import com.example.toread.progress.PlatformProgressClock
import com.example.toread.progress.ReadingProgressRepository
import com.example.toread.platform.AppBackgroundNotifier
import com.example.toread.platform.PlatformFile
import com.example.toread.platform.SecureTokenStore
import com.example.toread.platform.SharedFileInbox
import com.example.toread.reader.ReaderContentLoader
import com.example.toread.settings.AuthMode
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.settings.ToreadAppearancePreferences
import com.example.toread.sync.HttpReadingProgressSyncUploader
import com.example.toread.sync.HttpReadingProgressRemoteSource
import com.example.toread.sync.HttpBookMetadataSyncUploader
import com.example.toread.sync.ProgressSyncScheduler
import com.example.toread.sync.RemoteProgressFetchResult
import com.example.toread.sync.SyncRunSummary
import com.example.toread.sync.SyncQueueProcessor
import com.example.toread.ui.screens.AuthPage
import com.example.toread.ui.screens.BookInfoPage
import com.example.toread.ui.screens.BookshelfLayoutMode
import com.example.toread.ui.screens.BookshelfPage
import com.example.toread.ui.screens.CheckingAuthPage
import com.example.toread.ui.screens.ImportPage
import com.example.toread.ui.screens.ReaderPage
import com.example.toread.ui.screens.SettingsPage
import com.example.toread.ui.components.BookDropOverlay
import com.example.toread.ui.components.bookFileDropTarget
import com.example.toread.ui.text.ToreadStrings
import com.example.toread.ui.theme.ToreadTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal enum class ToreadScreen {
    CheckingAuth,
    Auth,
    Bookshelf,
    Import,
    BookInfo,
    Reader,
    Settings,
}

internal enum class AutomaticBookshelfRefresh {
    None,
    LocalOnly,
    RemoteAfterSessionRestore,
}

internal data class ToreadStartupPlan(
    val screen: ToreadScreen,
    val automaticRefresh: AutomaticBookshelfRefresh,
)

private data class PendingAutomaticRefresh(
    val source: String,
    val localRefreshPending: Boolean,
    val remoteRefreshPending: Boolean,
)

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
    var screen by remember { mutableStateOf(ToreadScreen.CheckingAuth) }
    var selectedBook by remember { mutableStateOf<BookshelfBook?>(null) }
    var books by remember { mutableStateOf<List<BookshelfBook>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var pendingAutomaticRefresh by remember { mutableStateOf<PendingAutomaticRefresh?>(null) }
    var authSessionReady by remember { mutableStateOf(false) }
    var authSessionGeneration by remember { mutableIntStateOf(0) }
    var authIdentityGeneration by remember { mutableIntStateOf(0) }
    var authSessionExpiresInSeconds by remember { mutableStateOf<Long?>(null) }
    var scheduledAuthRefreshRetry by remember { mutableStateOf(false) }
    var authRefreshRetryAttempt by remember { mutableIntStateOf(0) }
    // Rotation bookkeeping for weak networks: a flapping link used to spend a rotation on every
    // reconnect, and every rotation is one the same link can lose. See `AuthRefreshPolicy`.
    var lastRefreshSuccessAt by remember { mutableStateOf<TimeMark?>(null) }
    var lastRefreshAttemptAt by remember { mutableStateOf<TimeMark?>(null) }
    var lastRefreshAttemptAmbiguous by remember { mutableStateOf(false) }
    var accessTokenRenewalAt by remember { mutableStateOf<TimeMark?>(null) }
    var appearance by remember { mutableStateOf(ToreadAppearancePreferences()) }
    // Shelf pinning is a device-level arrangement: the hashes live in the settings, newest pin first.
    var pinnedBookHashes by remember { mutableStateOf<List<String>>(emptyList()) }
    var bookPendingDelete by remember { mutableStateOf<BookshelfBook?>(null) }
    val autoSyncEnabledState = remember { mutableStateOf(true) }
    var autoSyncEnabled by autoSyncEnabledState
    var bookshelfLayoutMode by remember { mutableStateOf(BookshelfLayoutMode.Grid) }
    var syncRunning by remember { mutableStateOf(false) }
    var serverConnectionState by remember {
        mutableStateOf<ServerConnectionState>(ServerConnectionState.Checking(startedAt = ""))
    }
    var previousServerConnectionState by remember { mutableStateOf<ServerConnectionState?>(null) }

    fun showAppMessage(message: String) {
        if(screen!= ToreadScreen.Reader) {
            appScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(ToreadStrings.safeUserVisibleMessage(message))
            }
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
    val authRefreshMutex = remember { Mutex() }
    val serverBaseUrl = remember(platform.kind) { ServerBaseUrlConfig.resolve(platform.kind) }
    val authClient = remember(settingsRepository, serverBaseUrl) {
        if (settingsRepository != null) {
            AuthClient(
                baseUrlProvider = { serverBaseUrl },
            )
        } else {
            null
        }
    }
    // Device-local history of auth events, so an incident on a weak network can be explained after the
    // fact without a console (Android has logcat but needs a cable; the packaged desktop build has none).
    val authDiagnostics = remember(settingsRepository) {
        settingsRepository?.let { repository ->
            AuthDiagnostics(
                read = { repository.authDiagnostics() },
                write = { entries -> repository.setAuthDiagnostics(entries) },
                nowIsoString = { PlatformProgressClock().nowIsoString() },
            )
        }
    }

    fun millisSince(mark: TimeMark?): Long? = mark?.elapsedNow()?.inWholeMilliseconds

    /** Whether the in-memory access token has reached the point where a rotation is worth spending. */
    fun accessTokenNeedsRenewal(): Boolean =
        !authSessionReady || accessTokenRenewalAt?.hasPassedNow() ?: true

    fun refreshTriggerAllowed(trigger: AuthRefreshTrigger): Boolean =
        maySpendRefreshRotation(
            trigger = trigger,
            millisSinceLastSuccessfulRefresh = millisSince(lastRefreshSuccessAt),
            millisSinceLastAttempt = millisSince(lastRefreshAttemptAt),
            previousAttemptAmbiguous = lastRefreshAttemptAmbiguous,
            accessTokenWithinSafetyWindow = accessTokenNeedsRenewal(),
        )

    /** Logs why a non-deadline trigger was refused, so a flapping session is explainable afterwards. */
    fun logRefreshSkipped(trigger: AuthRefreshTrigger, reason: String) {
        ToreadClientLog.info(
            AuthLogArea,
            "auth.refresh_skipped",
            "reason" to reason,
            "trigger" to trigger.name.lowercase(),
            "sinceSuccessMs" to millisSince(lastRefreshSuccessAt),
            "sinceAttemptMs" to millisSince(lastRefreshAttemptAt),
            "ambiguous" to lastRefreshAttemptAmbiguous,
        )
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
    val progressSyncScheduler = remember(appScope, syncQueueProcessor, serverConnectionMonitor) {
        ProgressSyncScheduler(
            scope = appScope,
            flushPending = {
                if (autoSyncEnabledState.value) {
                    syncQueueProcessor?.flushPending(stopOnFailure = true)
                } else {
                    null
                }
            },
            onFlushFinished = { summary ->
                when {
                    summary.uploaded > 0 -> serverConnectionMonitor?.markOnline("auto_queue_flush")
                    (summary.failed > 0 || summary.skipped > 0) && summary.lastError.shouldTriggerServerCheck() -> {
                        serverConnectionMonitor?.triggerCheck("auto_queue_flush")
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

    suspend fun refreshAuthSessionIfNeeded(
        force: Boolean = false,
        reason: String = AuthRefreshReasonUnspecified,
    ): Boolean {
        val requestedIdentityGeneration = authIdentityGeneration
        val requestedRefreshToken = settingsRepository?.refreshToken()
        return authRefreshMutex.withLock {
            val repository = settingsRepository ?: return@withLock false
            val client = authClient ?: return@withLock false
            if (authIdentityGeneration != requestedIdentityGeneration) {
                return@withLock false
            }
            if (repository.currentAuthMode() != AuthMode.LoggedIn) {
                authSessionReady = false
                return@withLock false
            }

            val refreshToken = repository.refreshToken()
            if (
                refreshToken != requestedRefreshToken &&
                authSessionReady
            ) {
                return@withLock true
            }
            if (!force && authSessionReady && !tokenStore.getToken().isNullOrBlank()) {
                return@withLock true
            }
            if (refreshToken.isNullOrBlank()) {
                tokenStore.clear()
                repository.clearAuthMode()
                authSessionReady = false
                authSessionExpiresInSeconds = null
                scheduledAuthRefreshRetry = false
                authRefreshRetryAttempt = 0
                pendingAutomaticRefresh = null
                selectedBook = null
                screen = ToreadScreen.Auth
                ToreadClientLog.warn(
                    AuthLogArea,
                    "auth.session_cleared",
                    "reason" to reason,
                    "cause" to "missing_refresh_token",
                )
                showAppMessage(ToreadStrings.LoginExpired)
                return@withLock false
            }

            val result = client.refresh(refreshToken)
            logAuthRefreshResult(
                reason = reason,
                result = result,
                retryAttempt = authRefreshRetryAttempt,
                diagnostics = authDiagnostics,
                sinceLastSuccessMillis = millisSince(lastRefreshSuccessAt),
            )
            when (result) {
                is AuthClientResult.Success -> {
                    if (
                        authIdentityGeneration != requestedIdentityGeneration ||
                        repository.currentAuthMode() != AuthMode.LoggedIn ||
                        repository.refreshToken() != refreshToken
                    ) {
                        return@withLock false
                    }
                    tokenStore.saveToken(result.session.accessToken)
                    repository.setLoggedIn(result.session.email)
                    repository.setRefreshToken(result.session.refreshToken)
                    authSessionReady = true
                    authSessionExpiresInSeconds = result.session.expiresInSeconds
                    scheduledAuthRefreshRetry = false
                    authRefreshRetryAttempt = 0
                    authSessionGeneration++
                    // Rotation bookkeeping: the next environment trigger must not spend another
                    // rotation before this one is old enough, and the deadline comes from the server's
                    // own expires_in rather than from a client guess.
                    val now = TimeSource.Monotonic.markNow()
                    lastRefreshAttemptAt = now
                    lastRefreshAttemptAmbiguous = false
                    lastRefreshSuccessAt = now
                    accessTokenRenewalAt = now +
                        accessTokenRefreshDelayMillis(result.session.expiresInSeconds).milliseconds
                    if (
                        autoSyncEnabled &&
                        pendingAutomaticRefresh?.remoteRefreshPending != true
                    ) {
                        pendingAutomaticRefresh = createPendingAutomaticRefresh(
                            source = "auth_session_refreshed",
                            mode = AutomaticBookshelfRefresh.RemoteAfterSessionRestore,
                        ).copy(localRefreshPending = false)
                    }
                    serverConnectionMonitor?.markOnline("auth.refresh")
                    true
                }
                is AuthClientResult.Failure -> {
                    if (
                        authIdentityGeneration != requestedIdentityGeneration ||
                        repository.currentAuthMode() != AuthMode.LoggedIn ||
                        repository.refreshToken() != refreshToken
                    ) {
                        return@withLock false
                    }
                    lastRefreshAttemptAt = TimeSource.Monotonic.markNow()
                    // A response that never arrived is ambiguous: the server may already have rotated,
                    // so the next attempt has to wait out the ambiguous floor instead of burning the
                    // chain's replay budget within the same minute.
                    lastRefreshAttemptAmbiguous = result.httpStatus == null
                    if (result.isConfirmedInvalidRefreshToken()) {
                        tokenStore.clear()
                        repository.clearAuthMode()
                        authSessionReady = false
                        authSessionExpiresInSeconds = null
                        scheduledAuthRefreshRetry = false
                        authRefreshRetryAttempt = 0
                        pendingAutomaticRefresh = null
                        selectedBook = null
                        screen = ToreadScreen.Auth
                        ToreadClientLog.warn(
                            AuthLogArea,
                            "auth.session_cleared",
                            "reason" to reason,
                            "cause" to "invalid_refresh_token",
                            // Tells "we lost responses for too long / ran past the replay grace" apart
                            // from "someone invalidated the chain": both end in the same verdict.
                            "sinceSuccessMs" to millisSince(lastRefreshSuccessAt),
                            "ambiguousAttempts" to authRefreshRetryAttempt,
                        )
                        authDiagnostics?.record(
                            event = "session_cleared",
                            reason = reason,
                            cause = "invalid_refresh_token",
                            extra = arrayOf(
                                "sinceSuccessMs" to millisSince(lastRefreshSuccessAt),
                                "ambiguousAttempts" to authRefreshRetryAttempt,
                            ),
                        )
                        showAppMessage(ToreadStrings.LoginExpiredLocalDataSafe)
                        false
                    } else {
                        authSessionReady = false
                        scheduledAuthRefreshRetry = true
                        authRefreshRetryAttempt += 1
                        authSessionGeneration++
                        showAppMessage(ToreadStrings.refreshAuthFailed(result.message))
                        if (result.message.shouldTriggerServerCheck()) {
                            serverConnectionMonitor?.triggerCheck("auth_refresh_failed")
                        }
                        false
                    }
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
        includeLocalRefresh: Boolean = true,
        includeRemoteSync: Boolean = true,
    ) {
        if (syncRunning) return
        syncRunning = true
        appScope.launch {
            try {
                if (includeLocalRefresh) {
                    bookshelfRepository?.refreshMissingEpubCovers()
                    bookshelfRepository?.refreshMissingEpubMetadata()
                    bookshelfRepository?.backfillMissingBookInsights()
                    bookshelfRepository?.ensurePendingBookMetadataQueued()
                }
                val summary = if (includeRemoteSync) {
                    syncQueueProcessor?.flushPending(limit = Int.MAX_VALUE)
                } else {
                    null
                }
                val remoteSummary = if (includeRemoteSync) {
                    pullRemoteProgressForBooks()
                } else {
                    RemoteProgressPullSummary()
                }
                if (includeRemoteSync) {
                    updateConnectionAfterSync(summary, remoteSummary, source)
                }
                val resultMessage = when {
                    !includeRemoteSync -> ToreadStrings.RefreshSuccess
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
                if (includeRemoteSync) {
                    serverConnectionMonitor?.triggerCheck(source)
                }
                if (showResult) {
                    showAppMessage(ToreadStrings.refreshFailed(ToreadStrings.RefreshUnexpectedFailure))
                }
            } finally {
                syncRunning = false
            }
        }
    }

    LaunchedEffect(pendingAutomaticRefresh, authSessionReady, syncRunning) {
        val pending = pendingAutomaticRefresh ?: return@LaunchedEffect
        if (syncRunning) return@LaunchedEffect

        val canRunRemoteRefresh = pending.remoteRefreshPending && authSessionReady
        val includeLocalRefresh: Boolean
        val includeRemoteSync: Boolean
        when {
            pending.localRefreshPending && (!pending.remoteRefreshPending || canRunRemoteRefresh) -> {
                includeLocalRefresh = true
                includeRemoteSync = canRunRemoteRefresh
                pendingAutomaticRefresh = null
            }
            pending.localRefreshPending -> {
                includeLocalRefresh = true
                includeRemoteSync = false
                pendingAutomaticRefresh = pending.copy(localRefreshPending = false)
            }
            canRunRemoteRefresh -> {
                includeLocalRefresh = false
                includeRemoteSync = true
                pendingAutomaticRefresh = null
            }
            else -> return@LaunchedEffect
        }
        runSyncAndRefresh(
            showResult = false,
            source = pending.source,
            includeLocalRefresh = includeLocalRefresh,
            includeRemoteSync = includeRemoteSync,
        )
    }

    LaunchedEffect(
        authSessionGeneration,
        authSessionReady,
        authSessionExpiresInSeconds,
        scheduledAuthRefreshRetry,
        authRefreshRetryAttempt,
        settingsRepository,
    ) {
        if (settingsRepository?.currentAuthMode() != AuthMode.LoggedIn) {
            return@LaunchedEffect
        }

        val delayMillis = if (scheduledAuthRefreshRetry) {
            // An ambiguous attempt may already have rotated server-side, so its retry waits out the
            // longer floor; a rejected/expired attempt keeps the plain backoff.
            if (lastRefreshAttemptAmbiguous) {
                ambiguousRetryDelayMillis(authRefreshRetryAttempt)
            } else {
                authRefreshRetryDelayMillis(authRefreshRetryAttempt)
            }
        } else {
            if (!authSessionReady) return@LaunchedEffect
            accessTokenRefreshDelayMillis(authSessionExpiresInSeconds ?: return@LaunchedEffect)
        }
        delay(delayMillis)
        refreshAuthSessionIfNeeded(
            force = true,
            reason = if (scheduledAuthRefreshRetry) {
                AuthRefreshReasonRetry
            } else {
                AuthRefreshReasonTokenExpiry
            },
        )
    }

    DisposableEffect(progressSyncScheduler, serverConnectionMonitor, settingsRepository, autoSyncEnabled) {
        AppBackgroundNotifier.setOnEnterBackground {
            progressSyncScheduler.flushNow()
        }
        AppBackgroundNotifier.setOnEnterForeground {
            // Health check first, refresh second: firing both at once meant the refresh raced the very
            // check that would have told it the link is not usable yet, and every such race lost a
            // rotation on a weak network.
            serverConnectionMonitor?.triggerCheck("app_foreground")
            appScope.launch {
                val monitor = serverConnectionMonitor
                val online = monitor == null || withTimeoutOrNull(ForegroundOnlineWaitMillis) {
                    monitor.state.first { it is ServerConnectionState.Online }
                } != null
                if (!online) return@launch
                if (settingsRepository?.currentAuthMode() != AuthMode.LoggedIn) return@launch
                if (!refreshTriggerAllowed(AuthRefreshTrigger.Environment)) {
                    // The in-memory token is still good enough; do not spend a rotation on a reconnect.
                    logRefreshSkipped(AuthRefreshTrigger.Environment, AuthRefreshReasonForeground)
                    return@launch
                }
                authSessionReady = false
                val restored = refreshAuthSessionIfNeeded(
                    force = true,
                    reason = AuthRefreshReasonForeground,
                )
                if (restored && autoSyncEnabled) {
                    pendingAutomaticRefresh = createPendingAutomaticRefresh(
                        source = "app_foreground",
                        mode = AutomaticBookshelfRefresh.RemoteAfterSessionRestore,
                    ).copy(localRefreshPending = false)
                }
            }
        }
        onDispose {
            AppBackgroundNotifier.setOnEnterBackground(null)
            AppBackgroundNotifier.setOnEnterForeground(null)
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
        val repository = settingsRepository
        appearance = repository?.appearancePreferences() ?: ToreadAppearancePreferences()
        pinnedBookHashes = repository?.pinnedBookHashes().orEmpty()
        val storedAutoSyncEnabled = repository?.autoSyncEnabled() ?: true
        autoSyncEnabled = storedAutoSyncEnabled
        bookshelfLayoutMode = BookshelfLayoutMode.fromStoredValue(repository?.bookshelfLayoutMode())
            ?: BookshelfLayoutMode.Grid

        val authMode = repository?.currentAuthMode()
        authSessionReady = false
        authSessionExpiresInSeconds = null
        val startupPlan = initialToreadStartupPlan(
            authMode = authMode,
            autoSyncEnabled = storedAutoSyncEnabled,
        )
        screen = startupPlan.screen
        if (startupPlan.automaticRefresh != AutomaticBookshelfRefresh.None) {
            pendingAutomaticRefresh = createPendingAutomaticRefresh(
                source = "app_start",
                mode = startupPlan.automaticRefresh,
            )
        }
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
                val hadPendingRemoteRefresh =
                    pendingAutomaticRefresh?.remoteRefreshPending == true
                val shouldRefreshAuth = previous == null ||
                    previous is ServerConnectionState.Checking ||
                    wasOffline ||
                    hadPendingRemoteRefresh && !authSessionReady ||
                    scheduledAuthRefreshRetry
                if (wasOffline) {
                    showAppMessage(ToreadStrings.ServerReconnectedSnackbar)
                }
                if (
                    wasOffline &&
                    autoSyncEnabled &&
                    settingsRepository?.currentAuthMode() == AuthMode.LoggedIn &&
                    !hadPendingRemoteRefresh
                ) {
                    authSessionReady = false
                    pendingAutomaticRefresh = createPendingAutomaticRefresh(
                        source = "server_reconnected",
                        mode = AutomaticBookshelfRefresh.RemoteAfterSessionRestore,
                    )
                }
                if (shouldRefreshAuth) {
                    // A deadline or a missing token goes through immediately; a plain reconnect has to
                    // pass the trigger floor, because a flapping link reconnects every few seconds and
                    // each reconnect used to spend a rotation the same link could lose.
                    val trigger = if (!authSessionReady) {
                        AuthRefreshTrigger.SessionMissing
                    } else {
                        AuthRefreshTrigger.Environment
                    }
                    if (authSessionReady && !refreshTriggerAllowed(trigger)) {
                        logRefreshSkipped(trigger, AuthRefreshReasonServerOnline)
                    } else {
                        val restored = refreshAuthSessionIfNeeded(
                            force = wasOffline || !authSessionReady,
                            reason = AuthRefreshReasonServerOnline,
                        )
                        if (
                            restored &&
                            wasOffline &&
                            autoSyncEnabled &&
                            settingsRepository?.currentAuthMode() == AuthMode.LoggedIn &&
                            pendingAutomaticRefresh?.remoteRefreshPending != true
                        ) {
                            pendingAutomaticRefresh = createPendingAutomaticRefresh(
                                source = "server_reconnected",
                                mode = AutomaticBookshelfRefresh.RemoteAfterSessionRestore,
                            ).copy(localRefreshPending = false)
                        }
                    }
                }
            }
            is ServerConnectionState.Checking -> Unit
        }
    }

    LaunchedEffect(bookshelfRepository, refreshKey) {
        books = runCatching { bookshelfRepository?.listBooks().orEmpty() }
            .onFailure { showAppMessage(ToreadStrings.readBookshelfFailed(it.message)) }
            .getOrDefault(emptyList())
    }

    val isReaderScreen = screen == ToreadScreen.Reader
    val isBookshelfScreen = screen == ToreadScreen.Bookshelf

    // Drag-and-drop import: the desktop shell accepts books dropped from the file manager while the
    // library screens are open. The drop runs through the same import service the picker uses.
    var bookDropActive by remember { mutableStateOf(false) }
    var bookDropImporting by remember { mutableStateOf(false) }
    var bookDropFileCount by remember { mutableIntStateOf(0) }
    val bookDropEnabled = platform.kind == PlatformKind.Desktop &&
        importService != null &&
        (isBookshelfScreen || screen == ToreadScreen.Import)

    fun togglePinnedBook(book: BookshelfBook) {
        val updated = toggledPinOrder(pinnedBookHashes, book.bookHash)
        pinnedBookHashes = updated
        settingsRepository?.setPinnedBookHashes(updated)
    }

    fun deleteBook(book: BookshelfBook) {
        val repository = bookshelfRepository
        bookPendingDelete = null
        appScope.launch {
            val deleted = repository != null &&
                runCatching { repository.deleteBookLocally(book.bookHash) }.getOrDefault(false)
            if (!deleted) {
                showAppMessage(ToreadStrings.DeleteBookFailed)
                return@launch
            }
            val remaining = books.filterNot { it.bookHash == book.bookHash }
            val prunedPins = prunedPinOrder(pinnedBookHashes, remaining)
            if (prunedPins != pinnedBookHashes) {
                pinnedBookHashes = prunedPins
                settingsRepository?.setPinnedBookHashes(prunedPins)
            }
            if (selectedBook?.bookHash == book.bookHash) selectedBook = null
            refreshKey++
            showAppMessage(ToreadStrings.DeleteBookDone)
        }
    }

    fun importDroppedBooks(droppedFiles: List<PlatformFile>) {        val service = importService
        if (service == null) {
            showAppMessage(ToreadStrings.ImportServiceUnavailable)
            return
        }
        val books = droppedFiles.droppedBookFiles()
        val skipped = droppedFiles.size - books.size
        if (books.isEmpty()) {
            showAppMessage(ToreadStrings.droppedUnsupportedFiles(skipped))
            return
        }

        bookDropImporting = true
        appScope.launch {
            val results = runCatching { service.importFiles(books) }
            bookDropImporting = false
            val message = results.fold(
                onSuccess = { droppedImportMessage(it, skippedCount = skipped) },
                onFailure = { error ->
                    ToreadStrings.importFailed(
                        ToreadStrings.safeUserVisibleMessage(
                            error.message,
                            fallback = ToreadStrings.ImportFailedGeneric,
                        ),
                    )
                },
            )
            if (results.getOrNull()?.any { it is BookImportResult.Imported } == true) {
                progressSyncScheduler.requestFlush()
                refreshKey++
            }
            showAppMessage(message)
        }
    }

    // Books the operating system handed us rather than the user picking them in-app: an Android
    // "open with" / share intent. The inbox is snapshot state, so an arrival recomposes and this
    // drains it exactly once, through the same import service as the picker and the desktop drop.
    val sharedFileCount = SharedFileInbox.size
    LaunchedEffect(sharedFileCount) {
        if (sharedFileCount > 0) importDroppedBooks(SharedFileInbox.drain())
    }

    ToreadTheme(
        useDynamicColor = appearance.dynamicColorEnabled,
        darkTheme = appearance.themeMode.isDark(isSystemInDarkTheme()),
        palette = appearance.palette,
        highContrastDark = appearance.highContrastDarkTheme,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                // Ahead of the content padding, so a book can be released anywhere in the window and
                // not only inside the margins non-bookshelf screens keep.
                .bookFileDropTarget(
                    enabled = bookDropEnabled,
                    onFilesDropped = { files ->
                        bookDropActive = false
                        bookDropFileCount = files.size
                        importDroppedBooks(files)
                    },
                    onDragActiveChange = { active ->
                        bookDropActive = active && bookDropEnabled
                    },
                )
                .background(MaterialTheme.colorScheme.background)
                .then(appContentPadding(platform.kind, isReaderScreen, isBookshelfScreen)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    when (screen) {
                        ToreadScreen.CheckingAuth -> CheckingAuthPage()
                        ToreadScreen.Auth -> AuthPage(
                            settingsRepository = settingsRepository,
                            authClient = authClient,
                            onAuthenticated = { session ->
                                authIdentityGeneration++
                                tokenStore.saveToken(session.accessToken)
                                settingsRepository?.setLoggedIn(session.email)
                                settingsRepository?.setRefreshToken(session.refreshToken)
                                refreshKey++
                                authSessionReady = true
                                authSessionExpiresInSeconds = session.expiresInSeconds
                                scheduledAuthRefreshRetry = false
                                authRefreshRetryAttempt = 0
                                authSessionGeneration++
                                pendingAutomaticRefresh = createPendingAutomaticRefresh(
                                    source = "auth_completed",
                                    mode = automaticBookshelfRefreshAfterAuthChoice(AuthMode.LoggedIn),
                                )
                                screen = ToreadScreen.Bookshelf
                            },
                            onGuestMode = {
                                appScope.launch {
                                    authIdentityGeneration++
                                    tokenStore.clear()
                                    settingsRepository?.setGuestMode()
                                    refreshKey++
                                    authSessionReady = false
                                    authSessionExpiresInSeconds = null
                                    scheduledAuthRefreshRetry = false
                                    authRefreshRetryAttempt = 0
                                    pendingAutomaticRefresh = createPendingAutomaticRefresh(
                                        source = "guest_entry",
                                        mode = automaticBookshelfRefreshAfterAuthChoice(AuthMode.Guest),
                                    )
                                    screen = ToreadScreen.Bookshelf
                                }
                            },
                        )
                        ToreadScreen.Bookshelf -> BookshelfPage(
                            books = books,
                            readerContentLoader = readerContentLoader,
                            isDesktop = platform.kind == PlatformKind.Desktop,
                            layoutMode = bookshelfLayoutMode,
                            onLayoutModeChange = { mode ->
                                bookshelfLayoutMode = mode
                                settingsRepository?.setBookshelfLayoutMode(mode.storedValue)
                            },
                            onImport = { screen = ToreadScreen.Import },
                            pinnedBookHashes = pinnedBookHashes,
                            onTogglePin = { book -> togglePinnedBook(book) },
                            onEditBookInfo = { book ->
                                selectedBook = book
                                screen = ToreadScreen.BookInfo
                            },
                            onDeleteBook = { book -> bookPendingDelete = book },
                            onOpenBook = { book ->
                                selectedBook = book
                                screen = ToreadScreen.Reader
                            },
                            onSettings = { screen = ToreadScreen.Settings },
                            onSync = { runSyncAndRefresh() },
                            isRefreshing = syncRunning,
                        )
                        ToreadScreen.BookInfo -> BookInfoPage(
                            book = selectedBook,
                            bookshelfRepository = bookshelfRepository,
                            settingsRepository = settingsRepository,
                            onSaved = {
                                refreshKey++
                                screen = ToreadScreen.Bookshelf
                                showAppMessage(ToreadStrings.BookInfoSaved)
                            },
                            onDelete = { book -> bookPendingDelete = book },
                            onBack = {
                                refreshKey++
                                screen = ToreadScreen.Bookshelf
                            },
                        )
                        ToreadScreen.Import -> ImportPage(
                            importService = importService,
                            supportsFileDrop = platform.kind == PlatformKind.Desktop,
                            onImported = {
                                progressSyncScheduler.requestFlush()
                                refreshKey++
                            },
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
                            settingsRepository = settingsRepository,
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
                            appearance = appearance,
                            autoSyncEnabled = autoSyncEnabled,
                            bookshelfLayoutMode = bookshelfLayoutMode,
                            onAppearanceChange = { updated ->
                                appearance = updated
                                settingsRepository?.setAppearancePreferences(updated)
                            },
                            onBookshelfLayoutModeChange = { mode ->
                                bookshelfLayoutMode = mode
                                settingsRepository?.setBookshelfLayoutMode(mode.storedValue)
                            },
                            onAutoSyncEnabledChange = { enabled ->
                                settingsRepository?.setAutoSyncEnabled(enabled)
                                autoSyncEnabled = enabled
                                if (enabled) progressSyncScheduler.requestFlush()
                            },
                            onBack = {
                                refreshKey++
                                screen = ToreadScreen.Bookshelf
                            },
                            onLogout = {
                                appScope.launch {
                                    authIdentityGeneration++
                                    tokenStore.clear()
                                    settingsRepository?.clearAuthMode()
                                    selectedBook = null
                                    authSessionReady = false
                                    authSessionExpiresInSeconds = null
                                    scheduledAuthRefreshRetry = false
                                    authRefreshRetryAttempt = 0
                                    pendingAutomaticRefresh = null
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
                if (bookDropActive || bookDropImporting) {
                    BookDropOverlay(
                        isImporting = bookDropImporting,
                        fileCount = bookDropFileCount,
                    )
                }
            }
        }

        bookPendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { bookPendingDelete = null },
                title = { Text(ToreadStrings.DeleteBookTitle) },
                text = { Text(ToreadStrings.deleteBookMessage(target.title)) },
                confirmButton = {
                    TextButton(onClick = { deleteBook(target) }) {
                        Text(
                            text = ToreadStrings.DeleteBookConfirm,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { bookPendingDelete = null }) {
                        Text(ToreadStrings.DialogCancel)
                    }
                },
            )
        }
    }
}

internal fun initialToreadStartupPlan(
    authMode: AuthMode?,
    autoSyncEnabled: Boolean,
): ToreadStartupPlan =
    when (authMode) {
        null -> ToreadStartupPlan(
            screen = ToreadScreen.Auth,
            automaticRefresh = AutomaticBookshelfRefresh.None,
        )
        AuthMode.Guest -> ToreadStartupPlan(
            screen = ToreadScreen.Bookshelf,
            automaticRefresh = AutomaticBookshelfRefresh.LocalOnly,
        )
        AuthMode.LoggedIn -> ToreadStartupPlan(
            screen = ToreadScreen.Bookshelf,
            automaticRefresh = automaticBookshelfRefresh(authMode, autoSyncEnabled),
        )
    }

internal fun automaticBookshelfRefresh(
    authMode: AuthMode,
    autoSyncEnabled: Boolean,
): AutomaticBookshelfRefresh =
    if (authMode == AuthMode.LoggedIn && autoSyncEnabled) {
        AutomaticBookshelfRefresh.RemoteAfterSessionRestore
    } else {
        AutomaticBookshelfRefresh.LocalOnly
    }

internal fun automaticBookshelfRefreshAfterAuthChoice(authMode: AuthMode): AutomaticBookshelfRefresh =
    when (authMode) {
        AuthMode.LoggedIn -> AutomaticBookshelfRefresh.RemoteAfterSessionRestore
        AuthMode.Guest -> AutomaticBookshelfRefresh.LocalOnly
    }

internal fun accessTokenRefreshDelayMillis(expiresInSeconds: Long): Long {
    val ttlMillis = expiresInSeconds.coerceAtLeast(1L)
        .coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
    val safetyWindowMillis = minOf(
        AccessTokenRefreshMaxSafetyWindowMillis,
        (ttlMillis / 10L).coerceAtLeast(AccessTokenRefreshMinSafetyWindowMillis),
    )
    return (ttlMillis - safetyWindowMillis).coerceAtLeast(AccessTokenRefreshMinimumDelayMillis)
}

/**
 * Backoff for retrying a refresh whose outcome stayed ambiguous (timeout, dropped response,
 * malformed body). The server keeps a consumed refresh token replayable for one hour while its
 * replacement is unused, so a retry inside that window recovers the session instead of ending it.
 */
internal fun authRefreshRetryDelayMillis(retryAttempt: Int): Long {
    val index = (retryAttempt - 1).coerceIn(0, AuthRefreshRetryDelaysMillis.lastIndex)
    return AuthRefreshRetryDelaysMillis[index]
}

private fun logAuthRefreshResult(
    reason: String,
    result: AuthClientResult,
    retryAttempt: Int,
    diagnostics: AuthDiagnostics?,
    sinceLastSuccessMillis: Long?,
) {
    val failure = result as? AuthClientResult.Failure
    val outcome = when {
        result is AuthClientResult.Success -> "ok"
        failure?.isConfirmedInvalidRefreshToken() == true -> "rejected"
        else -> "ambiguous"
    }
    ToreadClientLog.info(
        AuthLogArea,
        "auth.refresh_result",
        "reason" to reason,
        "outcome" to outcome,
        "httpStatus" to failure?.httpStatus,
        "errorCode" to failure?.error,
        "retryAttempt" to retryAttempt,
        "sinceSuccessMs" to sinceLastSuccessMillis,
    )
    diagnostics?.record(
        event = "refresh_result",
        reason = reason,
        cause = outcome,
        extra = arrayOf(
            "httpStatus" to failure?.httpStatus,
            "errorCode" to failure?.error,
            "retryAttempt" to retryAttempt,
            "sinceSuccessMs" to sinceLastSuccessMillis,
        ),
    )
}

private fun createPendingAutomaticRefresh(
    source: String,
    mode: AutomaticBookshelfRefresh,
): PendingAutomaticRefresh =
    PendingAutomaticRefresh(
        source = source,
        localRefreshPending = mode != AutomaticBookshelfRefresh.None,
        remoteRefreshPending = mode == AutomaticBookshelfRefresh.RemoteAfterSessionRestore,
    )

private const val AuthLogArea = "auth"
private const val AuthRefreshReasonUnspecified = "unspecified"
private const val AuthRefreshReasonTokenExpiry = "access_token_expiry"
private const val AuthRefreshReasonRetry = "auth_refresh_retry"
private const val AuthRefreshReasonForeground = "app_foreground"
private const val AuthRefreshReasonServerOnline = "server_online"
private val AuthRefreshRetryDelaysMillis = listOf(30_000L, 60_000L, 120_000L, 300_000L)

/** How long the foreground refresh waits for the health check before giving up on this resume. */
private const val ForegroundOnlineWaitMillis = 8_000L
private const val AccessTokenRefreshMinSafetyWindowMillis = 5_000L
private const val AccessTokenRefreshMaxSafetyWindowMillis = 5 * 60_000L
private const val AccessTokenRefreshMinimumDelayMillis = 1_000L

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
private fun appContentPadding(
    platformKind: PlatformKind,
    isReaderScreen: Boolean,
    isBookshelfScreen: Boolean,
): Modifier =
    when {
        isReaderScreen -> Modifier
        isBookshelfScreen && platformKind == PlatformKind.Desktop -> Modifier
        isBookshelfScreen -> Modifier.windowInsetsPadding(WindowInsets.safeContent)
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

private data class RemoteProgressPullSummary(
    val checked: Int = 0,
    val applied: Int = 0,
    val notFound: Int = 0,
    val failed: Int = 0,
    val lastError: String? = null,
    val unavailableReason: String? = null,
)
