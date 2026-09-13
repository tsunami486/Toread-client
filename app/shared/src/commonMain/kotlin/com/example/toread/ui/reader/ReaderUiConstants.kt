package com.example.toread.ui.reader

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Corner radius of the reader's bottom sheets (the reading panel and the chapter drawer). Only the top
 * corners are rounded and the sheets sit flush against the screen edges.
 */
internal val ReaderSheetCornerRadius = 20.dp

/**
 * The reading panel's slider thumb: a plain white circle instead of the Material bar. The track uses
 * the same height, so the thumb and the passed part of the track read as one capsule.
 */
internal val ReaderSliderThumbSize = 18.dp

/** How long the panel keeps a released chapter-page seek pinned before following the reader again. */
internal const val ReaderChapterSeekLatchMillis = 2_000L

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
