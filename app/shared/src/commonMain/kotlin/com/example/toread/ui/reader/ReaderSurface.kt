package com.example.toread.ui.reader

import androidx.compose.ui.graphics.Color
import com.example.toread.settings.ToreadPalette
import com.example.toread.ui.theme.ToreadMinimumContrastRatio
import com.example.toread.ui.theme.contrastRatio
import com.example.toread.ui.theme.toreadColorScheme

/**
 * The reading surface's own day/night colors, taken from the palette the reader chose in 主题与色彩.
 *
 * The page follows that palette like the rest of the app, but it still must never take its colors from
 * the *active* app theme: the reader can be in day mode while the app is dark (and the other way
 * round), and borrowing the app colors once left the page painted in the app's dark background while
 * the panel, the top bar and the chapter drawer had already switched. Day is therefore always the
 * palette's light scheme and night always its dark scheme (true black in 高对比度深色主题), and the one
 * [nightMode] flag switches the paper and the body text together.
 */
internal data class ReaderSurfaceColors(
    val background: Color,
    val text: Color,
)

/** Resolves the reading surface for the reader's own day/night state and the app's palette. */
internal fun readerSurfaceColors(
    nightMode: Boolean,
    highContrastDark: Boolean = false,
    palette: ToreadPalette = ToreadPalette.Indigo,
): ReaderSurfaceColors {
    val scheme = toreadColorScheme(
        palette = palette,
        darkTheme = nightMode,
        highContrastDark = highContrastDark,
    )
    return ReaderSurfaceColors(background = scheme.background, text = scheme.onBackground)
}

/** Book text must stay readable on the paper in both modes; asserted by `ReaderSurfaceTest`. */
internal fun ReaderSurfaceColors.isReadable(): Boolean =
    contrastRatio(background, text) >= ToreadMinimumContrastRatio
