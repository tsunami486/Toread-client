package com.example.toread.ui.reader

import androidx.compose.ui.graphics.Color
import com.example.toread.ui.theme.relativeLuminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderSurfaceTest {
    @Test
    fun aManualDaySwitchRepaintsThePaperAndTheTextTogether() {
        val day = readerSurfaceColors(nightMode = false)
        val night = readerSurfaceColors(nightMode = true)

        // Both halves of the surface change, so the page can never keep the app theme's own colors.
        assertTrue(day.background != night.background)
        assertTrue(day.text != night.text)
        assertTrue(day.isReadable())
        assertTrue(night.isReadable())
    }

    @Test
    fun dayIsAlwaysLightAndNightIsAlwaysDark() {
        // The regression this guards: with a dark app theme the reader's day surface used to keep the
        // app's dark background, so only the panel and the top bar looked switched.
        val day = readerSurfaceColors(nightMode = false)
        val night = readerSurfaceColors(nightMode = true)

        assertTrue(
            day.background.relativeLuminance() > 0.8f,
            "day reading surface was not light: ${day.background}",
        )
        assertTrue(
            night.background.relativeLuminance() < 0.1f,
            "night reading surface was not dark: ${night.background}",
        )
    }

    @Test
    fun highContrastOnlyAppliesToTheNightSurface() {
        assertEquals(Color(0xFF000000), readerSurfaceColors(nightMode = true, highContrastDark = true).background)
        assertEquals(Color(0xFFFFFFFF), readerSurfaceColors(nightMode = true, highContrastDark = true).text)
        assertEquals(
            readerSurfaceColors(nightMode = false),
            readerSurfaceColors(nightMode = false, highContrastDark = true),
        )
    }
}
