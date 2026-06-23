package com.example.toread.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val ToreadLightColorScheme = lightColorScheme(
    primary = Color(0xFF4E5F9F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE2FF),
    onPrimaryContainer = Color(0xFF09164A),
    secondary = Color(0xFF675D71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEDFF8),
    onSecondaryContainer = Color(0xFF22182B),
    tertiary = Color(0xFF006B5B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF7EF8DD),
    onTertiaryContainer = Color(0xFF00201A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF8FC),
    onBackground = Color(0xFF1B1B20),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1B1B20),
    surfaceVariant = Color(0xFFE5E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC8C5D0),
    inverseSurface = Color(0xFF303035),
    inverseOnSurface = Color(0xFFF2EFF4),
)

private val ToreadDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF1D2E6D),
    primaryContainer = Color(0xFF354683),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFFD2C3DC),
    onSecondary = Color(0xFF372D41),
    secondaryContainer = Color(0xFF4E4358),
    onSecondaryContainer = Color(0xFFEEDFF8),
    tertiary = Color(0xFF62DBC2),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005144),
    onTertiaryContainer = Color(0xFF7EF8DD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E1E8),
    surface = Color(0xFF1A1B20),
    onSurface = Color(0xFFE4E1E8),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF928F9A),
    outlineVariant = Color(0xFF47464F),
    inverseSurface = Color(0xFFE4E1E8),
    inverseOnSurface = Color(0xFF303035),
)

@Immutable
internal data class ToreadExtraColors(
    val syncSuccess: Color,
    val syncPending: Color,
    val syncFailed: Color,
    val readerPage: Color,
    val readerDivider: Color,
    val readerScrim: Color,
    val readChapter: Color,
    val currentChapter: Color,
    val unreadChapter: Color,
    val epubCoverStart: Color,
    val epubCoverEnd: Color,
    val pdfCoverStart: Color,
    val pdfCoverEnd: Color,
    val txtCoverStart: Color,
    val txtCoverEnd: Color,
)

private val LightExtraColors = ToreadExtraColors(
    syncSuccess = Color(0xFF2E7D32),
    syncPending = Color(0xFF8A6D1D),
    syncFailed = Color(0xFFC62828),
    readerPage = Color(0xFFFFFBFF),
    readerDivider = Color(0xFFD8D4DE),
    readerScrim = Color.Black.copy(alpha = 0.42f),
    readChapter = Color(0xFF8B8792),
    currentChapter = Color(0xFF8A6218),
    unreadChapter = Color(0xFF24232A),
    epubCoverStart = Color(0xFFDDE2FF),
    epubCoverEnd = Color(0xFF6878C2),
    pdfCoverStart = Color(0xFFFFDAD2),
    pdfCoverEnd = Color(0xFFC06F5F),
    txtCoverStart = Color(0xFFEEDFF8),
    txtCoverEnd = Color(0xFF806A9A),
)

private val DarkExtraColors = ToreadExtraColors(
    syncSuccess = Color(0xFFA7DFA4),
    syncPending = Color(0xFFE0C16E),
    syncFailed = Color(0xFFFFB4AB),
    readerPage = Color(0xFF171A1F),
    readerDivider = Color(0xFF30343B),
    readerScrim = Color.Black.copy(alpha = 0.55f),
    readChapter = Color(0xFF747985),
    currentChapter = Color(0xFFD8B66A),
    unreadChapter = Color(0xFFE4E8EE),
    epubCoverStart = Color(0xFF404B82),
    epubCoverEnd = Color(0xFF8795E2),
    pdfCoverStart = Color(0xFF6E372D),
    pdfCoverEnd = Color(0xFFE1A092),
    txtCoverStart = Color(0xFF52425F),
    txtCoverEnd = Color(0xFFBFA6D6),
)

internal val LocalToreadExtraColors = staticCompositionLocalOf { LightExtraColors }

internal val MaterialTheme.toreadColors: ToreadExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalToreadExtraColors.current

@Composable
internal fun ToreadTheme(
    useDynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (useDynamicColor) {
        platformDynamicColorScheme(darkTheme)
    } else {
        null
    } ?: toreadColorScheme(darkTheme)
    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors

    androidx.compose.runtime.CompositionLocalProvider(LocalToreadExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

private fun toreadColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) ToreadDarkColorScheme else ToreadLightColorScheme
