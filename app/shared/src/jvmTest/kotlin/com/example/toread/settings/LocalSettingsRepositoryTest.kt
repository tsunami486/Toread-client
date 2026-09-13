package com.example.toread.settings

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.toread.database.ToreadDatabase
import com.example.toread.progress.ProgressClock
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalSettingsRepositoryTest {
    @Test
    fun authChoiceRoundTripsAndCanBeCleared() {
        val repository = testRepository()

        assertEquals(null, repository.currentAuthMode())

        repository.setGuestMode()
        assertEquals(AuthMode.Guest, repository.currentAuthMode())

        repository.setLoggedIn("reader@example.com")
        repository.setRefreshToken("refresh-token")
        assertEquals(AuthMode.LoggedIn, repository.currentAuthMode())
        assertEquals("reader@example.com", repository.userEmail())
        assertEquals("refresh-token", repository.refreshToken())

        repository.clearAuthMode()
        assertEquals(null, repository.currentAuthMode())
        assertEquals(null, repository.userEmail())
        assertEquals(null, repository.refreshToken())
    }

    @Test
    fun readerNightModeDefaultsOffAndRoundTrips() {
        val repository = testRepository()

        assertEquals(false, repository.readerNightModeEnabled())

        repository.setReaderNightModeEnabled(true)
        assertEquals(true, repository.readerNightModeEnabled())

        repository.setReaderNightModeEnabled(false)
        assertEquals(false, repository.readerNightModeEnabled())
    }

    @Test
    fun appearancePreferencesDefaultToFollowingTheSystemAndRoundTrip() {
        val repository = testRepository()

        val defaults = repository.appearancePreferences()
        assertEquals(ToreadThemeMode.System, defaults.themeMode)
        assertEquals(false, defaults.dynamicColorEnabled)
        assertEquals(ToreadPalette.Indigo, defaults.palette)
        assertEquals(false, defaults.highContrastDarkTheme)
        assertEquals(true, defaults.readerNightModeFollowSystem)

        val chosen = ToreadAppearancePreferences(
            themeMode = ToreadThemeMode.Dark,
            dynamicColorEnabled = true,
            palette = ToreadPalette.Teal,
            highContrastDarkTheme = true,
            readerNightModeFollowSystem = false,
        )
        repository.setAppearancePreferences(chosen)
        assertEquals(chosen, repository.appearancePreferences())

        // The reader stores the same follow-system flag through its own confirmation dialog.
        repository.setReaderNightModeFollowSystem(true)
        assertEquals(true, repository.appearancePreferences().readerNightModeFollowSystem)
    }

    @Test
    fun appearanceChangesLeaveUnrelatedSettingsAlone() {
        val repository = testRepository()

        repository.setAutoSyncEnabled(false)
        repository.setAppearancePreferences(
            ToreadAppearancePreferences(themeMode = ToreadThemeMode.Light),
        )

        assertEquals(false, repository.autoSyncEnabled())
        assertEquals(ToreadThemeMode.Light, repository.appearancePreferences().themeMode)
    }

    @Test
    fun pdfPreferencesUseSafeDefaultsWhenNoSettingsExist() {
        val repository = testRepository()

        assertEquals(
            AndroidPdfReaderPreferences(
                landscapeDoublePageEnabled = true,
                volumeKeysEnabled = false,
                orientationMode = PdfOrientationMode.System,
            ),
            repository.androidPdfReaderPreferences(),
        )
        assertEquals(
            PdfBookPreferences(
                rightToLeft = false,
                coverAlone = true,
            ),
            repository.pdfBookPreferences("hash_default"),
        )
    }

    @Test
    fun androidPdfPreferencesRoundTripAsLocalSettings() {
        val database = testDatabase()
        val repository = LocalSettingsRepository(database, FixedSettingsClock)

        repository.setPdfAndroidLandscapeDoublePageEnabled(false)
        repository.setPdfAndroidVolumeKeysEnabled(true)
        repository.setPdfAndroidOrientationMode(PdfOrientationMode.Landscape)
        repository.setPdfAndroidTapToTurnPagesEnabled(false)
        repository.setPdfAndroidReverseTapToTurnPages(true)
        repository.setPdfAndroidPageAnimationEnabled(false)
        repository.setPdfAndroidDoubleTapZoomEnabled(false)
        repository.setPdfAndroidLongPressZoomEnabled(false)
        repository.setPdfAndroidShowPageNumber(false)
        repository.setPdfAndroidShowStatusInfo(false)
        repository.setPdfAndroidAutoPageTurningIntervalSeconds(12)

        assertEquals(
            AndroidPdfReaderPreferences(
                landscapeDoublePageEnabled = false,
                volumeKeysEnabled = true,
                orientationMode = PdfOrientationMode.Landscape,
                tapToTurnPagesEnabled = false,
                reverseTapToTurnPages = true,
                pageAnimationEnabled = false,
                doubleTapZoomEnabled = false,
                longPressZoomEnabled = false,
                showPageNumber = false,
                showStatusInfo = false,
                autoPageTurningIntervalSeconds = 12,
            ),
            repository.androidPdfReaderPreferences(),
        )
        assertEquals(
            "local",
            database.toreadDatabaseQueries
                .getSetting("reader.pdf.android.orientation_mode")
                .executeAsOne()
                .sync_status,
        )
    }

    @Test
    fun pdfBookPreferencesAreIsolatedByBookHash() {
        val repository = testRepository()

        repository.setPdfBookRightToLeft("hash_a", true)
        repository.setPdfBookCoverAlone("hash_a", false)
        repository.setPdfBookReaderMode("hash_a", PdfReaderMode.ContinuousVertical)

        assertEquals(
            PdfBookPreferences(
                rightToLeft = true,
                coverAlone = false,
                readerMode = PdfReaderMode.ContinuousVertical,
            ),
            repository.pdfBookPreferences("hash_a"),
        )
        assertEquals(
            PdfBookPreferences(rightToLeft = false, coverAlone = true),
            repository.pdfBookPreferences("hash_b"),
        )
    }

    @Test
    fun malformedPdfSettingsFallBackWithoutBroadParsing() {
        val database = testDatabase()
        saveRawSetting(
            database = database,
            key = "reader.pdf.android.landscape_double_page_enabled",
            value = "TRUE",
        )
        saveRawSetting(
            database = database,
            key = "reader.pdf.android.volume_keys_enabled",
            value = "yes",
        )
        saveRawSetting(
            database = database,
            key = "reader.pdf.android.orientation_mode",
            value = "sensor_landscape",
        )
        saveRawSetting(
            database = database,
            key = "reader.pdf.android.auto_page_turning_interval_seconds",
            value = "60",
        )
        saveRawSetting(
            database = database,
            key = "reader.pdf.book.hash_invalid.right_to_left",
            value = "1",
        )
        saveRawSetting(
            database = database,
            key = "reader.pdf.book.hash_invalid.cover_alone",
            value = "",
        )
        saveRawSetting(
            database = database,
            key = "reader.pdf.book.hash_invalid.reader_mode",
            value = "webtoon",
        )

        val repository = LocalSettingsRepository(database, FixedSettingsClock)

        assertEquals(
            AndroidPdfReaderPreferences(),
            repository.androidPdfReaderPreferences(),
        )
        assertEquals(
            PdfBookPreferences(),
            repository.pdfBookPreferences("hash_invalid"),
        )
    }

    @Test
    fun autoPageTurningIntervalRejectsValuesOutsideSupportedRange() {
        val repository = testRepository()

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.setPdfAndroidAutoPageTurningIntervalSeconds(0)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            repository.setPdfAndroidAutoPageTurningIntervalSeconds(21)
        }
    }

    @Test
    fun pdfReaderEnumStoredValuesRoundTrip() {
        assertEquals(
            PdfReaderMode.PagedVertical,
            PdfReaderMode.fromStoredValue(PdfReaderMode.PagedVertical.storedValue),
        )
        assertEquals(
            PdfOrientationMode.Portrait,
            PdfOrientationMode.fromStoredValue(PdfOrientationMode.Portrait.storedValue),
        )
    }

    private fun testRepository(): LocalSettingsRepository =
        LocalSettingsRepository(testDatabase(), FixedSettingsClock)

    private fun testDatabase(): ToreadDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ToreadDatabase.Schema.create(driver)
        return ToreadDatabase(driver)
    }

    private fun saveRawSetting(
        database: ToreadDatabase,
        key: String,
        value: String,
    ) {
        database.toreadDatabaseQueries.upsertSetting(
            key = key,
            value_ = value,
            sync_status = "local",
            created_at = FixedTimestamp,
            updated_at = FixedTimestamp,
        )
    }

    private object FixedSettingsClock : ProgressClock {
        override fun nowIsoString(): String = FixedTimestamp
    }

    private companion object {
        const val FixedTimestamp = "2026-07-25T00:00:00Z"
    }
}
