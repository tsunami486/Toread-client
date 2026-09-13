package com.example.toread.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.toread.settings.ToreadPalette

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

/**
 * High-contrast dark: the chrome goes to true black so an AMOLED panel can switch those pixels off.
 * The reading surface's own black comes from `ReaderSurfaceColors.NightHighContrast`.
 */
private val HighContrastDarkExtraColors = DarkExtraColors.copy(
    readerPage = Color(0xFF000000),
    readerDivider = Color(0xFF2A2A2A),
    readerScrim = Color.Black.copy(alpha = 0.72f),
    readChapter = Color(0xFF9A9A9A),
    unreadChapter = Color(0xFFFFFFFF),
)

internal val LocalToreadExtraColors = staticCompositionLocalOf { LightExtraColors }

internal val MaterialTheme.toreadColors: ToreadExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalToreadExtraColors.current

/**
 * Whether the theme currently in scope is dark. Reader surfaces read this to follow the app theme
 * while their day/night switch is still on 跟随系统, without re-resolving the theme mode themselves.
 */
internal val LocalToreadDarkTheme = staticCompositionLocalOf { false }

internal val MaterialTheme.toreadIsDark: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalToreadDarkTheme.current

@Composable
internal fun ToreadTheme(
    useDynamicColor: Boolean,
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ToreadPalette = ToreadPalette.Indigo,
    highContrastDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (useDynamicColor) {
        platformDynamicColorScheme(darkTheme)
    } else {
        null
    } ?: toreadColorScheme(
        palette = palette,
        darkTheme = darkTheme,
        highContrastDark = highContrastDark,
    )
    val extraColors = if (darkTheme && highContrastDark) {
        HighContrastDarkExtraColors
    } else if (darkTheme) {
        DarkExtraColors
    } else {
        LightExtraColors
    }

    CompositionLocalProvider(
        LocalToreadExtraColors provides extraColors,
        LocalToreadDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

/**
 * The scheme the app paints with. The brand palette keeps the hand-tuned scheme; every other palette
 * only replaces the accent and neutral-tint roles, so the surfaces M3 components expect keep their
 * tuned values. High-contrast dark then drops the dark scheme onto true black.
 */
internal fun toreadColorScheme(
    palette: ToreadPalette,
    darkTheme: Boolean,
    highContrastDark: Boolean,
): ColorScheme {
    val base = if (darkTheme) ToreadDarkColorScheme else ToreadLightColorScheme
    val roles = paletteRoles(palette = palette, darkTheme = darkTheme)
    val accent = if (roles == null) {
        base
    } else {
        base.copy(
            primary = roles.primary,
            onPrimary = roles.onPrimary,
            primaryContainer = roles.primaryContainer,
            onPrimaryContainer = roles.onPrimaryContainer,
            secondary = roles.secondary,
            onSecondary = roles.onSecondary,
            secondaryContainer = roles.secondaryContainer,
            onSecondaryContainer = roles.onSecondaryContainer,
            tertiary = roles.tertiary,
            onTertiary = roles.onTertiary,
            tertiaryContainer = roles.tertiaryContainer,
            onTertiaryContainer = roles.onTertiaryContainer,
            background = roles.background,
            onBackground = roles.onBackground,
            surface = roles.surface,
            onSurface = roles.onSurface,
            surfaceVariant = roles.surfaceVariant,
            onSurfaceVariant = roles.onSurfaceVariant,
            surfaceContainerLowest = roles.surfaceContainerLowest,
            surfaceContainerLow = roles.surfaceContainerLow,
            surfaceContainer = roles.surfaceContainer,
            surfaceContainerHigh = roles.surfaceContainerHigh,
            surfaceContainerHighest = roles.surfaceContainerHighest,
            outline = roles.outline,
            outlineVariant = roles.outlineVariant,
        )
    }
    if (!darkTheme || !highContrastDark) return accent
    // Pure black saves power on AMOLED panels; surfaces stay a hair above it so cards keep an edge.
    return accent.copy(
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF0A0A0A),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF1C1C1C),
        onSurfaceVariant = Color(0xFFD6D6D6),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF060606),
        surfaceContainer = Color(0xFF0A0A0A),
        surfaceContainerHigh = Color(0xFF111111),
        surfaceContainerHighest = Color(0xFF161616),
        outline = Color(0xFF8F8F8F),
        outlineVariant = Color(0xFF3A3A3A),
        inverseSurface = Color(0xFFFFFFFF),
        inverseOnSurface = Color(0xFF000000),
    )
}
