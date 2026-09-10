package com.example.toread

import com.example.toread.settings.AuthMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStartupFlowTest {
    @Test
    fun missingAuthChoiceOpensAuthenticationBeforeBookshelf() {
        assertEquals(
            ToreadStartupPlan(
                screen = ToreadScreen.Auth,
                automaticRefresh = AutomaticBookshelfRefresh.None,
            ),
            initialToreadStartupPlan(authMode = null, autoSyncEnabled = true),
        )
    }

    @Test
    fun persistedGuestChoiceEntersBookshelfWithLocalRefreshOnly() {
        assertEquals(
            ToreadStartupPlan(
                screen = ToreadScreen.Bookshelf,
                automaticRefresh = AutomaticBookshelfRefresh.LocalOnly,
            ),
            initialToreadStartupPlan(AuthMode.Guest, autoSyncEnabled = true),
        )
    }

    @Test
    fun persistedLoginWaitsForSessionRestoreBeforeRemoteRefresh() {
        assertEquals(
            ToreadStartupPlan(
                screen = ToreadScreen.Bookshelf,
                automaticRefresh = AutomaticBookshelfRefresh.RemoteAfterSessionRestore,
            ),
            initialToreadStartupPlan(AuthMode.LoggedIn, autoSyncEnabled = true),
        )
    }

    @Test
    fun disabledAutoSyncKeepsStartupRefreshLocal() {
        assertEquals(
            AutomaticBookshelfRefresh.LocalOnly,
            initialToreadStartupPlan(AuthMode.LoggedIn, autoSyncEnabled = false).automaticRefresh,
        )
    }

    @Test
    fun completedLoginFlushesRemotelyEvenWhenBackgroundAutoSyncIsDisabled() {
        assertEquals(
            AutomaticBookshelfRefresh.RemoteAfterSessionRestore,
            automaticBookshelfRefreshAfterAuthChoice(AuthMode.LoggedIn),
        )
        assertEquals(
            AutomaticBookshelfRefresh.LocalOnly,
            automaticBookshelfRefreshAfterAuthChoice(AuthMode.Guest),
        )
    }

    @Test
    fun accessTokenRefreshIsScheduledBeforeExpiry() {
        assertEquals(55 * 60_000L, accessTokenRefreshDelayMillis(expiresInSeconds = 3_600L))
        assertEquals(5_000L, accessTokenRefreshDelayMillis(expiresInSeconds = 10L))
        assertEquals(1_000L, accessTokenRefreshDelayMillis(expiresInSeconds = 1L))
    }

    @Test
    fun ambiguousRefreshFailuresBackOffUpToFiveMinutes() {
        assertEquals(30_000L, authRefreshRetryDelayMillis(retryAttempt = 0))
        assertEquals(30_000L, authRefreshRetryDelayMillis(retryAttempt = 1))
        assertEquals(60_000L, authRefreshRetryDelayMillis(retryAttempt = 2))
        assertEquals(120_000L, authRefreshRetryDelayMillis(retryAttempt = 3))
        assertEquals(300_000L, authRefreshRetryDelayMillis(retryAttempt = 4))
        assertEquals(300_000L, authRefreshRetryDelayMillis(retryAttempt = 20))
    }

    @Test
    fun refreshRetriesStayInsideTheServerReplayGraceWindow() {
        val firstEightRetriesMillis = (1..8).sumOf { attempt ->
            authRefreshRetryDelayMillis(retryAttempt = attempt)
        }
        assertTrue(firstEightRetriesMillis < OneHourMillis)
    }

    private companion object {
        const val OneHourMillis = 60 * 60_000L
    }
}
