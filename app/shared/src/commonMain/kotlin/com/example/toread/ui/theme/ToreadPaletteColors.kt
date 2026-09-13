package com.example.toread.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.toread.settings.ToreadPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Accent color math for the non-dynamic palettes.
 *
 * Every palette defines a single seed hue; the roles the app paints with are derived from it with
 * fixed tonal steps plus a readability guard, so no palette can end up with unreadable text and all of
 * them keep the same structure. This mirrors what Material's tonal palettes do without pulling in a
 * color-appearance model, and it keeps the derivation testable in common code.
 */

/** Roles derived from a palette seed. Only the accent and neutral-tint roles are computed; the rest of */
internal class ToreadPaletteRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outline: Color,
    val outlineVariant: Color,
)

private const val LightBackgroundTone = 0.98f
private const val LightSurfaceTone = 0.995f
private const val LightPrimaryTone = 0.42f
private const val LightContainerTone = 0.90f
private const val DarkBackgroundTone = 0.07f
private const val DarkSurfaceTone = 0.11f
private const val DarkPrimaryTone = 0.78f
private const val DarkContainerTone = 0.30f

/** Contrast every foreground/background pair in a scheme must reach (WCAG AA for body text). */
internal const val ToreadMinimumContrastRatio = 4.5f

/**
 * Derives the accent roles for [palette]. Returns null for the hand-tuned brand palette, which uses the
 * scheme written in `ToreadTheme.kt` unchanged.
 */
internal fun paletteRoles(palette: ToreadPalette, darkTheme: Boolean): ToreadPaletteRoles? {
    if (palette == ToreadPalette.Indigo) return null
    val hsl = palette.seed.toHsl()
    val hue = hsl.hue
    val saturation = hsl.saturation.coerceIn(0.25f, 0.85f)
    val secondaryHue = hue + 24f
    val tertiaryHue = hue + 58f

    if (darkTheme) {
        val background = hslColor(hue, 0.12f, DarkBackgroundTone)
        val surface = hslColor(hue, 0.12f, DarkSurfaceTone)
        val primary = hslColor(hue, saturation, DarkPrimaryTone)
            .lightenedUntilReadable(background)
        val primaryContainer = hslColor(hue, saturation.coerceAtMost(0.6f), DarkContainerTone)
        val secondary = hslColor(secondaryHue, saturation * 0.45f, DarkPrimaryTone)
            .lightenedUntilReadable(background)
        val secondaryContainer = hslColor(
            secondaryHue,
            saturation * 0.35f,
            DarkContainerTone,
        )
        val tertiary = hslColor(tertiaryHue, saturation * 0.75f, DarkPrimaryTone)
            .lightenedUntilReadable(background)
        val tertiaryContainer = hslColor(tertiaryHue, saturation * 0.55f, DarkContainerTone)
        return ToreadPaletteRoles(
            primary = primary,
            onPrimary = primary.readableContentColor(),
            primaryContainer = primaryContainer,
            onPrimaryContainer = primaryContainer.readableContentColor(),
            secondary = secondary,
            onSecondary = secondary.readableContentColor(),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = secondaryContainer.readableContentColor(),
            tertiary = tertiary,
            onTertiary = tertiary.readableContentColor(),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = tertiaryContainer.readableContentColor(),
            background = background,
            onBackground = hslColor(hue, 0.12f, 0.92f),
            surface = surface,
            onSurface = hslColor(hue, 0.12f, 0.92f),
            surfaceVariant = hslColor(hue, 0.14f, 0.20f),
            onSurfaceVariant = hslColor(hue, 0.10f, 0.78f),
            surfaceContainerLowest = hslColor(hue, 0.14f, 0.05f),
            surfaceContainerLow = hslColor(hue, 0.14f, 0.09f),
            surfaceContainer = hslColor(hue, 0.13f, 0.13f),
            surfaceContainerHigh = hslColor(hue, 0.12f, 0.17f),
            surfaceContainerHighest = hslColor(hue, 0.11f, 0.22f),
            outline = hslColor(hue, 0.10f, 0.60f),
            outlineVariant = hslColor(hue, 0.12f, 0.30f),
        )
    }

    val background = hslColor(hue, 0.14f, LightBackgroundTone)
    val surface = hslColor(hue, 0.10f, LightSurfaceTone)
    val primary = hslColor(hue, saturation, LightPrimaryTone)
        .darkenedUntilReadable(background)
    val primaryContainer = hslColor(hue, saturation.coerceAtMost(0.7f), LightContainerTone)
    val secondary = hslColor(secondaryHue, saturation * 0.45f, LightPrimaryTone)
        .darkenedUntilReadable(background)
    val secondaryContainer = hslColor(secondaryHue, saturation * 0.35f, LightContainerTone)
    val tertiary = hslColor(tertiaryHue, saturation * 0.75f, LightPrimaryTone)
        .darkenedUntilReadable(background)
    val tertiaryContainer = hslColor(tertiaryHue, saturation * 0.55f, LightContainerTone)
    return ToreadPaletteRoles(
        primary = primary,
        onPrimary = primary.readableContentColor(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = primaryContainer.readableContentColor(),
        secondary = secondary,
        onSecondary = secondary.readableContentColor(),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = secondaryContainer.readableContentColor(),
        tertiary = tertiary,
        onTertiary = tertiary.readableContentColor(),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = tertiaryContainer.readableContentColor(),
        background = background,
        onBackground = hslColor(hue, 0.18f, 0.13f),
        surface = surface,
        onSurface = hslColor(hue, 0.18f, 0.13f),
        surfaceVariant = hslColor(hue, 0.16f, 0.92f),
        onSurfaceVariant = hslColor(hue, 0.12f, 0.34f),
        surfaceContainerLowest = hslColor(hue, 0.10f, 1.0f),
        surfaceContainerLow = hslColor(hue, 0.12f, 0.975f),
        surfaceContainer = hslColor(hue, 0.14f, 0.955f),
        surfaceContainerHigh = hslColor(hue, 0.15f, 0.935f),
        surfaceContainerHighest = hslColor(hue, 0.16f, 0.915f),
        outline = hslColor(hue, 0.10f, 0.52f),
        outlineVariant = hslColor(hue, 0.14f, 0.80f),
    )
}

/** Two swatch colors for the palette grid: the light and dark tone the palette produces. */
internal fun paletteSwatchColors(palette: ToreadPalette): Pair<Color, Color> {
    val hsl = palette.seed.toHsl()
    val hue = hsl.hue
    val saturation = hsl.saturation.coerceIn(0.25f, 0.85f)
    return hslColor(hue, saturation * 0.55f, 0.86f) to hslColor(hue, saturation, LightPrimaryTone)
}

/** WCAG relative luminance. */
internal fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

/** WCAG contrast ratio: 1.0 for identical colors, 21.0 for black on white. */
internal fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.relativeLuminance()
    val secondLuminance = second.relativeLuminance()
    return (max(firstLuminance, secondLuminance) + 0.05f) /
        (min(firstLuminance, secondLuminance) + 0.05f)
}

/** Black or white, whichever reads better on [this]. */
internal fun Color.readableContentColor(): Color =
    if (contrastRatio(Color.Black, this) >= contrastRatio(Color.White, this)) {
        Color.Black
    } else {
        Color.White
    }

/** Keeps the hue and saturation and darkens until [this] reaches [target] against [background]. */
internal fun Color.darkenedUntilReadable(
    background: Color,
    target: Float = ToreadMinimumContrastRatio,
): Color = toneUntilReadable(background = background, target = target, darken = true)

/** Keeps the hue and saturation and lightens until [this] reaches [target] against [background]. */
internal fun Color.lightenedUntilReadable(
    background: Color,
    target: Float = ToreadMinimumContrastRatio,
): Color = toneUntilReadable(background = background, target = target, darken = false)

/**
 * Reads the palette roles and reports whether the pair a component paints together is readable. Used by
 * both the derivation and its tests, so a palette can never ship white-on-yellow text.
 */
internal fun Color.ensureReadableAgainst(
    background: Color,
    target: Float = ToreadMinimumContrastRatio,
): Color {
    if (contrastRatio(this, background) >= target) return this
    return if (relativeLuminance() > background.relativeLuminance()) {
        darkenedUntilReadable(background, target)
    } else {
        lightenedUntilReadable(background, target)
    }
}

private fun Color.toneUntilReadable(
    background: Color,
    target: Float,
    darken: Boolean,
): Color {
    var tone = toHsl()
    var candidate = this
    var guard = 0
    while (contrastRatio(candidate, background) < target && guard++ < 60) {
        val step = if (darken) -0.015f else 0.015f
        val lightness = (tone.lightness + step).coerceIn(0f, 1f)
        if (lightness == tone.lightness) break
        tone = tone.copy(lightness = lightness)
        candidate = hslColor(tone.hue, tone.saturation, tone.lightness)
    }
    return candidate
}

internal data class HslColor(val hue: Float, val saturation: Float, val lightness: Float)

internal fun Color.toHsl(): HslColor {
    val red = red
    val green = green
    val blue = blue
    val maximum = max(red, max(green, blue))
    val minimum = min(red, min(green, blue))
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2f
    if (delta <= 0.0001f) return HslColor(hue = 0f, saturation = 0f, lightness = lightness)
    val saturation = if (lightness > 0.5f) {
        delta / (2f - maximum - minimum)
    } else {
        delta / (maximum + minimum)
    }
    val rawHue = when (maximum) {
        red -> ((green - blue) / delta) % 6f
        green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    } * 60f
    return HslColor(
        hue = if (rawHue < 0f) rawHue + 360f else rawHue,
        saturation = saturation.coerceIn(0f, 1f),
        lightness = lightness.coerceIn(0f, 1f),
    )
}

internal fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation.coerceIn(0f, 1f)
    val section = normalizedHue / 60f
    val second = chroma * (1f - abs(section % 2f - 1f))
    val (red, green, blue) = when {
        section < 1f -> Triple(chroma, second, 0f)
        section < 2f -> Triple(second, chroma, 0f)
        section < 3f -> Triple(0f, chroma, second)
        section < 4f -> Triple(0f, second, chroma)
        section < 5f -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    val match = (lightness - chroma / 2f).coerceIn(0f, 1f)
    return Color(
        red = (red + match).coerceIn(0f, 1f),
        green = (green + match).coerceIn(0f, 1f),
        blue = (blue + match).coerceIn(0f, 1f),
        alpha = 1f,
    )
}
