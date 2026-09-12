package com.example.toread.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Reading surface used when the reader's own day/night switch is set to night. */
internal val ReaderNightBackground = Color(0xFF0F1214)
internal val ReaderNightText = Color(0xFFE4EAF0)

/** The reading panel's slider thumb: a plain white circle instead of the Material bar. */
internal val ReaderSliderThumbSize = 18.dp

internal const val ASeriesPaperAspectRatio = 0.7071f
internal const val DesktopSpreadAspectRatio = ASeriesPaperAspectRatio * 2f

internal data class ReaderTypographySpec(
    val bodyFontSize: TextUnit,
    val bodyLineHeight: TextUnit,
    val bodyLetterSpacing: TextUnit,
    val chapterTitleFontSize: TextUnit = bodyFontSize * 1.35f,
    val chapterTitleLineHeight: TextUnit = bodyLineHeight * 1.35f,
)

internal val DesktopReaderTypography = ReaderTypographySpec(
    bodyFontSize = 28.6.sp,
    bodyLineHeight = 39.sp,
    bodyLetterSpacing = 0.5.sp,
)

internal val AndroidReaderTypography = ReaderTypographySpec(
    bodyFontSize = 20.sp,
    bodyLineHeight = 25.sp,
    bodyLetterSpacing = 0.sp,
)
