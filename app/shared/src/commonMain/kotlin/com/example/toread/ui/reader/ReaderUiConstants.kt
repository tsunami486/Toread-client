package com.example.toread.ui.reader

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

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
