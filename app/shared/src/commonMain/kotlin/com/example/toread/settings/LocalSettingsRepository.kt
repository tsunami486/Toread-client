package com.example.toread.settings

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

    fun serverBaseUrl(defaultValue: String): String =
        database.toreadDatabaseQueries
            .getSetting(ServerBaseUrlKey)
            .executeAsOneOrNull()
            ?.value_
            ?.takeIf { it.isNotBlank() }
            ?: defaultValue

    fun setServerBaseUrl(value: String) {
        saveSetting(ServerBaseUrlKey, value.trim())
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        saveSetting(DynamicColorKey, enabled.toString())
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        saveSetting(AutoSyncKey, enabled.toString())
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
        const val ServerBaseUrlKey = "server_base_url"
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
