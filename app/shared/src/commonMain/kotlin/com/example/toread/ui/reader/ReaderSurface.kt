package com.example.toread.ui.reader

import androidx.compose.ui.graphics.Color
import com.example.toread.ui.theme.ToreadMinimumContrastRatio
import com.example.toread.ui.theme.contrastRatio

/**
 * The reading surface's own day/night palette.
 *
 * The page must never take its background or text color from the app theme: the reader can be in day
 * mode while the app is dark (and the other way round), and borrowing the app colors left the page
 * painted in the app's dark background while the panel, the top bar and the chapter drawer had already
 * switched. One [nightMode] flag therefore selects the whole surface, so tapping 日间/夜间 switches the
 * text *and* the paper behind it in the same frame.
 */
internal data class ReaderSurfaceColors(
    val background: Color,
    val text: Color,
) {
    companion object {
        val Day = ReaderSurfaceColors(
            background = LightReaderPage,
            text = DarkReaderText,
        )
        val Night = ReaderSurfaceColors(
            background = DarkReaderPage,
            text = LightReaderText,
        )

        /** 高对比度深色主题: an AMOLED panel can switch truly black pixels off. */
        val NightHighContrast = ReaderSurfaceColors(
            background = Color(0xFF000000),
            text = Color(0xFFFFFFFF),
        )

        /** Chapter titles and other dimmed page content. */
        val DayMuted = Color(0xFF6A6771)
        val NightMuted = Color(0xFF9AA2AD)
    }
}

/** Resolves the reading surface for the reader's own day/night state, never for the app theme. */
internal fun readerSurfaceColors(
    nightMode: Boolean,
    highContrastDark: Boolean = false,
): ReaderSurfaceColors = when {
    !nightMode -> ReaderSurfaceColors.Day
    highContrastDark -> ReaderSurfaceColors.NightHighContrast
    else -> ReaderSurfaceColors.Night
}

private val LightReaderPage = Color(0xFFFAF8FC)
private val DarkReaderText = Color(0xFF1B1B20)
private val DarkReaderPage = Color(0xFF0F1214)
private val LightReaderText = Color(0xFFE4EAF0)

/** Both reader surfaces must keep body text readable; asserted by `ReaderSurfaceTest`. */
internal fun ReaderSurfaceColors.isReadable(): Boolean =
    contrastRatio(background, text) >= ToreadMinimumContrastRatio
