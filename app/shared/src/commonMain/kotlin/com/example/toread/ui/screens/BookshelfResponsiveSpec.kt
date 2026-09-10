package com.example.toread.ui.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class BookshelfWidthClass {
    Compact,
    Medium,
    Expanded,
}

internal data class BookshelfResponsiveSpec(
    val sizeClass: BookshelfWidthClass,
    val contentMaxWidth: Dp,
    val constrainedSectionMaxWidth: Dp,
    val minGridCardWidth: Dp,
    val gridHorizontalSpacing: Dp,
    val gridVerticalSpacing: Dp,
    val listSpacing: Dp,
    val persistentSearch: Boolean,
    val persistentSearchWidth: Dp,
)

internal fun bookshelfResponsiveSpec(availableWidth: Dp): BookshelfResponsiveSpec {
    val widthClass = when {
        availableWidth < MediumWidthBreakpoint -> BookshelfWidthClass.Compact
        availableWidth < ExpandedWidthBreakpoint -> BookshelfWidthClass.Medium
        else -> BookshelfWidthClass.Expanded
    }
    return BookshelfResponsiveSpec(
        sizeClass = widthClass,
        contentMaxWidth = ContentMaxWidth,
        constrainedSectionMaxWidth = ConstrainedSectionMaxWidth,
        minGridCardWidth = when (widthClass) {
            BookshelfWidthClass.Compact -> CompactGridMinItemWidth
            BookshelfWidthClass.Medium,
            BookshelfWidthClass.Expanded,
            -> WideGridMinItemWidth
        },
        gridHorizontalSpacing = GridHorizontalGap,
        gridVerticalSpacing = GridVerticalGap,
        listSpacing = ListSpacing,
        persistentSearch = widthClass == BookshelfWidthClass.Expanded,
        persistentSearchWidth = PersistentSearchWidth,
    )
}

private val MediumWidthBreakpoint = 600.dp
private val ExpandedWidthBreakpoint = 840.dp
private val ContentMaxWidth = 1200.dp
private val ConstrainedSectionMaxWidth = 800.dp
private val CompactGridMinItemWidth = 140.dp
private val WideGridMinItemWidth = 150.dp
private val GridHorizontalGap = 16.dp
private val GridVerticalGap = 20.dp
private val ListSpacing = 12.dp
private val PersistentSearchWidth = 280.dp
