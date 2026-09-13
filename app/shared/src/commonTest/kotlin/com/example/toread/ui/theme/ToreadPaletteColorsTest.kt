package com.example.toread.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.toread.settings.ToreadPalette
import com.example.toread.settings.ToreadThemeMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToreadPaletteColorsTest {
    @Test
    fun everyPaletteKeepsItsTextReadableInBothSchemes() {
        ToreadPalette.entries.forEach { palette ->
            listOf(true, false).forEach { darkTheme ->
                val scheme = toreadColorScheme(
                    palette = palette,
                    darkTheme = darkTheme,
                    highContrastDark = false,
                )
                assertReadable(palette, darkTheme, "primary", scheme.primary, scheme.onPrimary)
                assertReadable(
                    palette,
                    darkTheme,
                    "primaryContainer",
                    scheme.primaryContainer,
                    scheme.onPrimaryContainer,
                )
                assertReadable(palette, darkTheme, "secondary", scheme.secondary, scheme.onSecondary)
                assertReadable(palette, darkTheme, "tertiary", scheme.tertiary, scheme.onTertiary)
                assertReadable(
                    palette,
                    darkTheme,
                    "background",
                    scheme.background,
                    scheme.onBackground,
                )
                assertReadable(palette, darkTheme, "surface", scheme.surface, scheme.onSurface)
            }
        }
    }

    @Test
    fun theBrandPaletteKeepsTheHandTunedScheme() {
        val light = toreadColorScheme(ToreadPalette.Indigo, darkTheme = false, highContrastDark = false)
        val dark = toreadColorScheme(ToreadPalette.Indigo, darkTheme = true, highContrastDark = false)

        assertEquals(Color(0xFF4E5F9F), light.primary)
        assertEquals(Color(0xFFFAF8FC), light.background)
        assertEquals(Color(0xFFFFFBFF), light.surface)
        assertEquals(Color(0xFFB9C3FF), dark.primary)
        assertEquals(Color(0xFF121318), dark.background)
    }

    @Test
    fun aChosenPaletteReplacesTheAccentButKeepsSurfacesTuned() {
        val brand = toreadColorScheme(ToreadPalette.Indigo, darkTheme = false, highContrastDark = false)
        val teal = toreadColorScheme(ToreadPalette.Teal, darkTheme = false, highContrastDark = false)

        assertTrue(teal.primary != brand.primary)
        assertTrue(teal.primaryContainer != brand.primaryContainer)
        assertTrue(teal.surfaceContainer != brand.surfaceContainer)
        // Roles no palette owns stay from the tuned scheme, so error and inverse states keep theirs.
        assertEquals(brand.error, teal.error)
        assertEquals(brand.inverseSurface, teal.inverseSurface)
    }

    @Test
    fun highContrastDarkDropsSurfacesOntoBlack() {
        val high = toreadColorScheme(ToreadPalette.Indigo, darkTheme = true, highContrastDark = true)
        val normal = toreadColorScheme(ToreadPalette.Indigo, darkTheme = true, highContrastDark = false)

        assertEquals(Color(0xFF000000), high.background)
        assertEquals(Color(0xFFFFFFFF), high.onBackground)
        assertTrue(high.surface.relativeLuminance() < normal.surface.relativeLuminance())
        // The flag only means something in a dark theme, so a light scheme must ignore it.
        assertEquals(
            normal.primary,
            toreadColorScheme(ToreadPalette.Indigo, darkTheme = true, highContrastDark = false).primary,
        )
        assertEquals(
            toreadColorScheme(ToreadPalette.Indigo, darkTheme = false, highContrastDark = false),
            toreadColorScheme(ToreadPalette.Indigo, darkTheme = false, highContrastDark = true),
        )
    }

    @Test
    fun themeModeOnlyFollowsTheSystemWhenAutomatic() {
        assertTrue(ToreadThemeMode.System.isDark(systemInDarkTheme = true))
        assertTrue(!ToreadThemeMode.System.isDark(systemInDarkTheme = false))
        assertTrue(ToreadThemeMode.Dark.isDark(systemInDarkTheme = false))
        assertTrue(!ToreadThemeMode.Light.isDark(systemInDarkTheme = true))

        assertEquals(ToreadThemeMode.Dark, ToreadThemeMode.fromStoredValue("dark"))
        assertNull(ToreadThemeMode.fromStoredValue("not-a-mode"))
        assertNull(ToreadThemeMode.fromStoredValue(null))
        assertEquals(ToreadPalette.Teal, ToreadPalette.fromStoredValue("teal"))
        assertNull(ToreadPalette.fromStoredValue("not-a-palette"))
    }

    @Test
    fun hslConversionRoundTripsEveryPaletteSeed() {
        ToreadPalette.entries.forEach { palette ->
            val hsl = palette.seed.toHsl()
            val restored = hslColor(hsl.hue, hsl.saturation, hsl.lightness)

            assertTrue(
                abs(restored.red - palette.seed.red) < 0.01f &&
                    abs(restored.green - palette.seed.green) < 0.01f &&
                    abs(restored.blue - palette.seed.blue) < 0.01f,
                "${palette.storedValue} did not survive the HSL round trip",
            )
        }
    }

    @Test
    fun contrastGuardsPickTheReadableTextColor() {
        assertTrue(contrastRatio(Color.Black, Color.White) > 20f)
        assertEquals(Color.White, Color(0xFF204080).readableContentColor())
        assertEquals(Color.Black, Color(0xFFF0E0A0).readableContentColor())

        // A mid yellow stays unreadable on white until the guard darkens it.
        val guarded = Color(0xFFE0D000).darkenedUntilReadable(Color.White)
        assertTrue(contrastRatio(guarded, Color.White) >= ToreadMinimumContrastRatio)
    }

    private fun assertReadable(
        palette: ToreadPalette,
        darkTheme: Boolean,
        role: String,
        background: Color,
        content: Color,
    ) {
        assertTrue(
            contrastRatio(background, content) >= ToreadMinimumContrastRatio,
            "${palette.storedValue} dark=$darkTheme $role contrast was " +
                contrastRatio(background, content),
        )
    }
}
