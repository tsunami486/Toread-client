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

    fun setLoggedIn(email: String?) {
        saveSetting(AuthModeKey, AuthMode.LoggedIn.storedValue)
        saveSetting(UserEmailKey, email.orEmpty())
    }

    fun setGuestMode() {
        saveSetting(AuthModeKey, AuthMode.Guest.storedValue)
        saveSetting(UserEmailKey, "")
    }

    fun clearAuthMode() {
        saveSetting(AuthModeKey, "")
        saveSetting(UserEmailKey, "")
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
