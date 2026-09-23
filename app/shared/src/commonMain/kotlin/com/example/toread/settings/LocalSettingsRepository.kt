package com.example.toread.settings

import com.example.toread.categories.BookCategoryEntry
import com.example.toread.categories.encodeCategoryEntries
import com.example.toread.categories.parseCategoryEntries
import com.example.toread.database.ToreadDatabase
import com.example.toread.progress.PlatformProgressClock
import com.example.toread.progress.ProgressClock

class LocalSettingsRepository(
    private val database: ToreadDatabase,
    private val clock: ProgressClock = PlatformProgressClock(),
) {
    fun currentAuthMode(): AuthMode? =
        database.toreadDatabaseQueries
            .getSetting(AuthModeKey)
            .executeAsOneOrNull()
            ?.value_
            ?.let(AuthMode::fromStoredValue)

    fun userEmail(): String? =
        database.toreadDatabaseQueries
            .getSetting(UserEmailKey)
            .executeAsOneOrNull()
            ?.value_
            ?.takeIf { it.isNotBlank() }

    fun refreshToken(): String? =
        database.toreadDatabaseQueries
            .getSetting(RefreshTokenKey)
            .executeAsOneOrNull()
            ?.value_
            ?.takeIf { it.isNotBlank() }

    fun dynamicColorEnabled(): Boolean =
        database.toreadDatabaseQueries
            .getSetting(DynamicColorKey)
            .executeAsOneOrNull()
            ?.value_
            ?.toBooleanStrictOrNull()
            ?: false

    fun autoSyncEnabled(): Boolean =
        database.toreadDatabaseQueries
            .getSetting(AutoSyncKey)
            .executeAsOneOrNull()
            ?.value_
            ?.toBooleanStrictOrNull()
            ?: true

    fun bookshelfLayoutMode(): String? =
        database.toreadDatabaseQueries
            .getSetting(BookshelfLayoutModeKey)
            .executeAsOneOrNull()
            ?.value_
            ?.takeIf { it.isNotBlank() }

    /** Day/night for the Android EPUB/TXT reading surface; device-level and never synced. */
    fun readerNightModeEnabled(): Boolean =
        readBooleanSetting(key = ReaderNightModeKey, defaultValue = false)

    /**
     * Appearance settings for the whole app. [ToreadAppearancePreferences.readerNightModeFollowSystem]
     * keeps [readerNightModeEnabled] unused until the reader stops following the app theme.
     */
    fun appearancePreferences(): ToreadAppearancePreferences =
        ToreadAppearancePreferences(
            themeMode = ToreadThemeMode.fromStoredValue(readSetting(ThemeModeKey))
                ?: ToreadThemeMode.System,
            dynamicColorEnabled = dynamicColorEnabled(),
            palette = ToreadPalette.fromStoredValue(readSetting(ThemePaletteKey))
                ?: ToreadPalette.Indigo,
            highContrastDarkTheme = readBooleanSetting(
                key = ThemeHighContrastDarkKey,
                defaultValue = false,
            ),
            readerNightModeFollowSystem = readBooleanSetting(
                key = ReaderNightFollowSystemKey,
                defaultValue = true,
            ),
        )

    fun setAppearancePreferences(preferences: ToreadAppearancePreferences) {
        saveSetting(ThemeModeKey, preferences.themeMode.storedValue)
        saveSetting(DynamicColorKey, preferences.dynamicColorEnabled.toString())
        saveSetting(ThemePaletteKey, preferences.palette.storedValue)
        saveSetting(ThemeHighContrastDarkKey, preferences.highContrastDarkTheme.toString())
        saveSetting(
            ReaderNightFollowSystemKey,
            preferences.readerNightModeFollowSystem.toString(),
        )
    }

    fun setReaderNightModeFollowSystem(enabled: Boolean) {
        saveSetting(ReaderNightFollowSystemKey, enabled.toString())
    }

    /**
     * Books pinned to the front of the shelf, most recently pinned first. Device-level: pinning is a
     * shelf arrangement on this device and is never synced.
     */
    fun pinnedBookHashes(): List<String> =
        readSetting(PinnedBooksKey)
            ?.split('\n')
            ?.map(String::trim)
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

    fun setPinnedBookHashes(bookHashes: List<String>) {
        saveSetting(
            PinnedBooksKey,
            bookHashes.map(String::trim).filter { it.isNotBlank() }.distinct().joinToString("\n"),
        )
    }

    /**
     * The last auth events, newest last, one compact line each. Device-level diagnostics only: they are
     * never synced and never contain a token, so they can be read back from the database after an
     * incident on a device that cannot be attached to a debugger.
     */
    fun authDiagnostics(): List<String> =
        readSetting(AuthDiagnosticsKey)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun setAuthDiagnostics(entries: List<String>) {
        saveSetting(
            AuthDiagnosticsKey,
            entries.filter { it.isNotBlank() }.joinToString("\n"),
        )
    }

    fun androidPdfReaderPreferences(): AndroidPdfReaderPreferences =
        AndroidPdfReaderPreferences(
            landscapeDoublePageEnabled = readBooleanSetting(
                key = PdfAndroidLandscapeDoublePageKey,
                defaultValue = true,
            ),
            volumeKeysEnabled = readBooleanSetting(
                key = PdfAndroidVolumeKeysKey,
                defaultValue = false,
            ),
            orientationMode = PdfOrientationMode.fromStoredValue(
                readSetting(PdfAndroidOrientationModeKey),
            ) ?: PdfOrientationMode.System,
            tapToTurnPagesEnabled = readBooleanSetting(
                key = PdfAndroidTapToTurnPagesKey,
                defaultValue = true,
            ),
            reverseTapToTurnPages = readBooleanSetting(
                key = PdfAndroidReverseTapToTurnPagesKey,
                defaultValue = false,
            ),
            pageAnimationEnabled = readBooleanSetting(
                key = PdfAndroidPageAnimationKey,
                defaultValue = true,
            ),
            doubleTapZoomEnabled = readBooleanSetting(
                key = PdfAndroidDoubleTapZoomKey,
                defaultValue = true,
            ),
            longPressZoomEnabled = readBooleanSetting(
                key = PdfAndroidLongPressZoomKey,
                defaultValue = true,
            ),
            showPageNumber = readBooleanSetting(
                key = PdfAndroidShowPageNumberKey,
                defaultValue = true,
            ),
            showStatusInfo = readBooleanSetting(
                key = PdfAndroidShowStatusInfoKey,
                defaultValue = true,
            ),
            autoPageTurningIntervalSeconds = readIntSetting(
                key = PdfAndroidAutoPageTurningIntervalKey,
                defaultValue = DefaultPdfAutoPageTurningIntervalSeconds,
                range = PdfAutoPageTurningIntervalRange,
            ),
        )

    fun pdfBookPreferences(bookHash: String): PdfBookPreferences =
        PdfBookPreferences(
            rightToLeft = readBooleanSetting(
                key = pdfBookSettingKey(bookHash, PdfBookRightToLeftSuffix),
                defaultValue = false,
            ),
            coverAlone = readBooleanSetting(
                key = pdfBookSettingKey(bookHash, PdfBookCoverAloneSuffix),
                defaultValue = true,
            ),
            readerMode = PdfReaderMode.fromStoredValue(
                readSetting(pdfBookSettingKey(bookHash, PdfBookReaderModeSuffix)),
            ) ?: PdfReaderMode.PagedHorizontal,
        )

    /**
     * The reader's own `关键词=分类` rules for the local category suggestions, and the categories
     * this device has already learned from the reader's own edits. Both are device-level: they
     * describe how this reader likes their shelf arranged, so they are never synced.
     */
    fun categoryDictionary(): List<BookCategoryEntry> =
        parseCategoryEntries(readSetting(CategoryDictionaryKey))

    fun setCategoryDictionary(entries: List<BookCategoryEntry>) {
        saveSetting(CategoryDictionaryKey, encodeCategoryEntries(entries))
    }

    fun categoryMemory(): List<BookCategoryEntry> =
        parseCategoryEntries(readSetting(CategoryMemoryKey))

    fun setCategoryMemory(entries: List<BookCategoryEntry>) {
        saveSetting(CategoryMemoryKey, encodeCategoryEntries(entries))
    }

    /**
     * Whether 自动分类 may write 分类/标签 on its own: the categories the book itself declares, plus
     * one category when the lexicon is clearly ahead. Device-level and never synced.
     */
    fun autoCategorizeEnabled(): Boolean =
        readBooleanSetting(key = AutoCategorizeKey, defaultValue = false)

    fun setAutoCategorizeEnabled(enabled: Boolean) {
        saveSetting(AutoCategorizeKey, enabled.toString())
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        saveSetting(DynamicColorKey, enabled.toString())
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        saveSetting(AutoSyncKey, enabled.toString())
    }

    fun setReaderNightModeEnabled(enabled: Boolean) {
        saveSetting(ReaderNightModeKey, enabled.toString())
    }

    fun setBookshelfLayoutMode(value: String) {
        saveSetting(BookshelfLayoutModeKey, value)
    }

    fun setPdfAndroidLandscapeDoublePageEnabled(enabled: Boolean) {
        saveSetting(PdfAndroidLandscapeDoublePageKey, enabled.toString())
    }

    fun setPdfAndroidVolumeKeysEnabled(enabled: Boolean) {
        saveSetting(PdfAndroidVolumeKeysKey, enabled.toString())
    }

    fun setPdfAndroidOrientationMode(mode: PdfOrientationMode) {
        saveSetting(PdfAndroidOrientationModeKey, mode.storedValue)
    }

    fun setPdfAndroidTapToTurnPagesEnabled(enabled: Boolean) {
        saveSetting(PdfAndroidTapToTurnPagesKey, enabled.toString())
    }

    fun setPdfAndroidReverseTapToTurnPages(enabled: Boolean) {
        saveSetting(PdfAndroidReverseTapToTurnPagesKey, enabled.toString())
    }

    fun setPdfAndroidPageAnimationEnabled(enabled: Boolean) {
        saveSetting(PdfAndroidPageAnimationKey, enabled.toString())
    }

    fun setPdfAndroidDoubleTapZoomEnabled(enabled: Boolean) {
        saveSetting(PdfAndroidDoubleTapZoomKey, enabled.toString())
    }

    fun setPdfAndroidLongPressZoomEnabled(enabled: Boolean) {
        saveSetting(PdfAndroidLongPressZoomKey, enabled.toString())
    }

    fun setPdfAndroidShowPageNumber(enabled: Boolean) {
        saveSetting(PdfAndroidShowPageNumberKey, enabled.toString())
    }

    fun setPdfAndroidShowStatusInfo(enabled: Boolean) {
        saveSetting(PdfAndroidShowStatusInfoKey, enabled.toString())
    }

    fun setPdfAndroidAutoPageTurningIntervalSeconds(seconds: Int) {
        require(seconds in PdfAutoPageTurningIntervalRange) {
            "PDF auto page turning interval must be in $PdfAutoPageTurningIntervalRange"
        }
        saveSetting(PdfAndroidAutoPageTurningIntervalKey, seconds.toString())
    }

    fun setPdfBookRightToLeft(bookHash: String, enabled: Boolean) {
        saveSetting(
            key = pdfBookSettingKey(bookHash, PdfBookRightToLeftSuffix),
            value = enabled.toString(),
        )
    }

    fun setPdfBookCoverAlone(bookHash: String, enabled: Boolean) {
        saveSetting(
            key = pdfBookSettingKey(bookHash, PdfBookCoverAloneSuffix),
            value = enabled.toString(),
        )
    }

    fun setPdfBookReaderMode(bookHash: String, mode: PdfReaderMode) {
        saveSetting(
            key = pdfBookSettingKey(bookHash, PdfBookReaderModeSuffix),
            value = mode.storedValue,
        )
    }

    fun setLoggedIn(email: String?) {
        saveSetting(AuthModeKey, AuthMode.LoggedIn.storedValue)
        saveSetting(UserEmailKey, email.orEmpty())
    }

    fun setRefreshToken(refreshToken: String) {
        saveSetting(RefreshTokenKey, refreshToken)
    }

    fun setGuestMode() {
        saveSetting(AuthModeKey, AuthMode.Guest.storedValue)
        saveSetting(UserEmailKey, "")
        saveSetting(RefreshTokenKey, "")
    }

    fun clearAuthMode() {
        saveSetting(AuthModeKey, "")
        saveSetting(UserEmailKey, "")
        saveSetting(RefreshTokenKey, "")
    }

    private fun readSetting(key: String): String? =
        database.toreadDatabaseQueries
            .getSetting(key)
            .executeAsOneOrNull()
            ?.value_

    private fun readBooleanSetting(key: String, defaultValue: Boolean): Boolean =
        readSetting(key)?.toBooleanStrictOrNull() ?: defaultValue

    private fun readIntSetting(
        key: String,
        defaultValue: Int,
        range: IntRange,
    ): Int = readSetting(key)
        ?.toIntOrNull()
        ?.takeIf { it in range }
        ?: defaultValue

    private fun pdfBookSettingKey(bookHash: String, suffix: String): String {
        val normalizedBookHash = bookHash.trim()
        require(normalizedBookHash.isNotEmpty()) { "bookHash must not be blank" }
        return "$PdfBookKeyPrefix$normalizedBookHash.$suffix"
    }

    private fun saveSetting(key: String, value: String) {
        val now = clock.nowIsoString()
        database.toreadDatabaseQueries.upsertSetting(
            key = key,
            value_ = value,
            sync_status = "local",
            created_at = now,
            updated_at = now,
        )
    }

    private companion object {
        const val AuthModeKey = "auth_mode"
        const val UserEmailKey = "user_email"
        const val RefreshTokenKey = "refresh_token"
        const val DynamicColorKey = "dynamic_color_enabled"
        const val AutoSyncKey = "auto_sync_enabled"
        const val BookshelfLayoutModeKey = "bookshelf_layout_mode"
        const val ThemeModeKey = "theme_mode"
        const val ThemePaletteKey = "theme_palette"
        const val ThemeHighContrastDarkKey = "theme.high_contrast_dark"
        const val ReaderNightModeKey = "reader.night_mode_enabled"
        const val ReaderNightFollowSystemKey = "reader.night_mode_follow_system"
        const val PinnedBooksKey = "bookshelf.pinned_hashes"
        const val AuthDiagnosticsKey = "auth.diagnostics"
        const val CategoryDictionaryKey = "library.category_dictionary"
        const val CategoryMemoryKey = "library.category_memory"
        const val AutoCategorizeKey = "library.auto_categorize_enabled"
        const val PdfAndroidLandscapeDoublePageKey =
            "reader.pdf.android.landscape_double_page_enabled"
        const val PdfAndroidVolumeKeysKey = "reader.pdf.android.volume_keys_enabled"
        const val PdfAndroidOrientationModeKey = "reader.pdf.android.orientation_mode"
        const val PdfAndroidTapToTurnPagesKey = "reader.pdf.android.tap_to_turn_pages_enabled"
        const val PdfAndroidReverseTapToTurnPagesKey = "reader.pdf.android.reverse_tap_to_turn_pages"
        const val PdfAndroidPageAnimationKey = "reader.pdf.android.page_animation_enabled"
        const val PdfAndroidDoubleTapZoomKey = "reader.pdf.android.double_tap_zoom_enabled"
        const val PdfAndroidLongPressZoomKey = "reader.pdf.android.long_press_zoom_enabled"
        const val PdfAndroidShowPageNumberKey = "reader.pdf.android.show_page_number"
        const val PdfAndroidShowStatusInfoKey = "reader.pdf.android.show_status_info"
        const val PdfAndroidAutoPageTurningIntervalKey =
            "reader.pdf.android.auto_page_turning_interval_seconds"
        const val PdfBookKeyPrefix = "reader.pdf.book."
        const val PdfBookRightToLeftSuffix = "right_to_left"
        const val PdfBookCoverAloneSuffix = "cover_alone"
        const val PdfBookReaderModeSuffix = "reader_mode"
        const val DefaultPdfAutoPageTurningIntervalSeconds = 5
        val PdfAutoPageTurningIntervalRange = 1..20
    }
}

enum class AuthMode(val storedValue: String) {
    LoggedIn("logged_in"),
    Guest("guest"),
    ;

    companion object {
        fun fromStoredValue(value: String): AuthMode? =
            entries.firstOrNull { it.storedValue == value }
    }
}
