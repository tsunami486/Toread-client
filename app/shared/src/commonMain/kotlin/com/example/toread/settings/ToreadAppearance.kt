package com.example.toread.settings

import androidx.compose.ui.graphics.Color

/**
 * How the app chooses between its light and dark color schemes.
 *
 * [System] follows the platform setting, which is what the reader means by 夜间模式跟随系统: with it
 * on, the reading surface follows the app theme instead of a pinned in-reader choice.
 */
enum class ToreadThemeMode(val storedValue: String) {
    Light("light"),
    Dark("dark"),
    System("system"),
    ;

    /** Resolves this mode against the platform setting. */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        Light -> false
        Dark -> true
        System -> systemInDarkTheme
    }

    companion object {
        fun fromStoredValue(value: String?): ToreadThemeMode? =
            entries.firstOrNull { it.storedValue == value }
    }
}

/**
 * Accent palettes offered while dynamic color is off.
 *
 * [seed] is the hue every role in the light and dark schemes is derived from, so a palette never has
 * to hand-pick a dozen colors and every entry keeps the same contrast relationships. [Indigo] is the
 * hand-tuned brand scheme the app shipped before palettes existed and stays untouched.
 */
enum class ToreadPalette(val storedValue: String, val seed: Color) {
    Indigo("indigo", Color(0xFF4E5F9F)),
    Green("green", Color(0xFF2F6B4F)),
    Teal("teal", Color(0xFF1F6A63)),
    Cyan("cyan", Color(0xFF00697A)),
    Sky("sky", Color(0xFF3A6EA8)),
    Violet("violet", Color(0xFF5B54A8)),
    Purple("purple", Color(0xFF7A4E9E)),
    Pink("pink", Color(0xFFA8447A)),
    Peach("peach", Color(0xFFB4574B)),
    Orange("orange", Color(0xFFA65B18)),
    Amber("amber", Color(0xFF8A6A18)),
    Slate("slate", Color(0xFF4A5568)),
    ;

    companion object {
        fun fromStoredValue(value: String?): ToreadPalette? =
            entries.firstOrNull { it.storedValue == value }
    }
}

/**
 * Appearance settings owned by the 主题与色彩 page: all device-level, never synced, and applied by the
 * app root so a change is visible everywhere at once.
 */
data class ToreadAppearancePreferences(
    val themeMode: ToreadThemeMode = ToreadThemeMode.System,
    val dynamicColorEnabled: Boolean = false,
    val palette: ToreadPalette = ToreadPalette.Indigo,
    val highContrastDarkTheme: Boolean = false,
    val readerNightModeFollowSystem: Boolean = true,
)
