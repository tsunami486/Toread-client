package com.example.toread.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.toread.platform.SystemBarInsets
import com.example.toread.settings.AndroidPdfReaderPreferences
import com.example.toread.settings.PdfBookPreferences
import com.example.toread.settings.PdfOrientationMode
import com.example.toread.settings.PdfReaderMode
import com.example.toread.ui.icons.ToreadSymbolIcon
import com.example.toread.ui.icons.ToreadSymbols
import com.example.toread.ui.text.ToreadStrings
import kotlin.math.roundToInt

/**
 * Hoisted UI state for the Android PDF reader chrome.
 *
 * Page indices are zero-based physical PDF page indices. The slider represents
 * the caller's navigation unit (a page, spread, or continuous-mode anchor), so
 * its value and range deliberately stay independent from [currentPageIndex].
 */
internal data class AndroidPdfReaderChromeState(
    val title: String,
    val controlsVisible: Boolean,
    val settingsPanelVisible: Boolean,
    val currentPageIndex: Int,
    val pageCount: Int,
    val visiblePageIndices: List<Int>,
    val sliderPreviewPageIndices: List<Int>,
    val sliderValue: Float,
    val sliderValueRange: ClosedFloatingPointRange<Float>,
    val sliderEnabled: Boolean,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
    val autoPageTurning: Boolean,
    val preferences: AndroidPdfReaderPreferences,
    val bookPreferences: PdfBookPreferences,
    val statusInfo: AndroidPdfReaderStatusInfo? = null,
    val availableReaderModes: List<PdfReaderMode> = PdfReaderMode.entries,
    val showSpreadSettings: Boolean =
        bookPreferences.readerMode != PdfReaderMode.ContinuousVertical,
    val showPagedInteractionSettings: Boolean =
        bookPreferences.readerMode != PdfReaderMode.ContinuousVertical,
)

/**
 * Status text is supplied by the platform/session owner so this common UI does
 * not poll clocks or Android battery APIs by itself.
 */
internal data class AndroidPdfReaderStatusInfo(
    val timeText: String? = null,
    val batteryText: String? = null,
) {
    val displayText: String
        get() = listOfNotNull(
            timeText?.takeIf(String::isNotBlank),
            batteryText?.takeIf(String::isNotBlank),
        ).joinToString(separator = " · ")
}

internal data class AndroidPdfReaderChromeActions(
    val onBack: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissSettings: () -> Unit,
    val onFirstPage: () -> Unit,
    val onPreviousPage: () -> Unit,
    val onNextPage: () -> Unit,
    val onLastPage: () -> Unit,
    val onSliderPreview: (Float) -> Unit,
    val onSliderCommit: () -> Unit,
    val onAutoPageTurningChange: (Boolean) -> Unit,
    val onReaderModeChange: (PdfReaderMode) -> Unit,
    val onLandscapeDoublePageEnabledChange: (Boolean) -> Unit,
    val onRightToLeftChange: (Boolean) -> Unit,
    val onCoverAloneChange: (Boolean) -> Unit,
    val onOrientationModeChange: (PdfOrientationMode) -> Unit,
    val onVolumeKeysEnabledChange: (Boolean) -> Unit,
    val onTapToTurnPagesEnabledChange: (Boolean) -> Unit,
    val onReverseTapToTurnPagesChange: (Boolean) -> Unit,
    val onPageAnimationEnabledChange: (Boolean) -> Unit,
    val onDoubleTapZoomEnabledChange: (Boolean) -> Unit,
    val onLongPressZoomEnabledChange: (Boolean) -> Unit,
    val onShowPageNumberChange: (Boolean) -> Unit,
    val onShowStatusInfoChange: (Boolean) -> Unit,
    val onAutoPageTurningIntervalSecondsChange: (Int) -> Unit,
)

/**
 * Venera-inspired, independently implemented Android reader chrome.
 *
 * This composable only draws overlays. Its caller remains responsible for the
 * PDF canvas, system-bar visibility, rendering, progress, and session state.
 */
@Composable
internal fun AndroidPdfReaderChrome(
    state: AndroidPdfReaderChromeState,
    actions: AndroidPdfReaderChromeActions,
    systemBarInsets: SystemBarInsets,
    modifier: Modifier = Modifier,
) {
    val animationMillis = AndroidPdfReaderChromeDefaults.AnimationMillis
    val fadeSpec = tween<Float>(durationMillis = animationMillis)
    val pageLabel = state.visiblePageLabel()
    val sliderPreviewLabel = state.sliderPreviewLabel()
    val statusText = state.statusInfo?.displayText.orEmpty()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !state.controlsVisible && state.preferences.showPageNumber,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 20.dp,
                    bottom = systemBarInsets.bottom + 18.dp,
                ),
            enter = fadeIn(fadeSpec),
            exit = fadeOut(fadeSpec),
        ) {
            ReaderFloatingBadge(text = pageLabel)
        }

        AnimatedVisibility(
            visible = !state.controlsVisible &&
                state.preferences.showStatusInfo &&
                statusText.isNotBlank(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = systemBarInsets.bottom + 18.dp,
                ),
            enter = fadeIn(fadeSpec),
            exit = fadeOut(fadeSpec),
        ) {
            ReaderFloatingBadge(text = statusText)
        }

        AnimatedVisibility(
            visible = state.controlsVisible,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            enter = slideInVertically(
                animationSpec = tween(durationMillis = animationMillis),
                initialOffsetY = { -it },
            ) + fadeIn(fadeSpec),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = animationMillis),
                targetOffsetY = { -it },
            ) + fadeOut(fadeSpec),
        ) {
            AndroidPdfReaderTopBar(
                title = state.title,
                pageLabel = pageLabel,
                topInset = systemBarInsets.top,
                onBack = actions.onBack,
                onOpenSettings = actions.onOpenSettings,
            )
        }

        AnimatedVisibility(
            visible = state.controlsVisible,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = tween(durationMillis = animationMillis),
                initialOffsetY = { it },
            ) + fadeIn(fadeSpec),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = animationMillis),
                targetOffsetY = { it },
            ) + fadeOut(fadeSpec),
        ) {
            AndroidPdfReaderBottomBar(
                state = state,
                sliderPreviewLabel = sliderPreviewLabel,
                bottomInset = systemBarInsets.bottom,
                actions = actions,
            )
        }

        val scrimInteractionSource = remember { MutableInteractionSource() }
        AnimatedVisibility(
            visible = state.settingsPanelVisible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(fadeSpec),
            exit = fadeOut(fadeSpec),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = scrimInteractionSource,
                        indication = null,
                        onClick = actions.onDismissSettings,
                    ),
            )
        }

        val settingsPanelWidth = minOf(
            maxWidth,
            AndroidPdfReaderChromeDefaults.SettingsPanelMaxWidth,
        )
        AnimatedVisibility(
            visible = state.settingsPanelVisible,
            modifier = Modifier
                .fillMaxHeight()
                .width(settingsPanelWidth)
                .align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                animationSpec = tween(durationMillis = animationMillis),
                initialOffsetX = { it },
            ) + fadeIn(fadeSpec),
            exit = slideOutHorizontally(
                animationSpec = tween(durationMillis = animationMillis),
                targetOffsetX = { it },
            ) + fadeOut(fadeSpec),
        ) {
            AndroidPdfReaderSettingsPanel(
                state = state,
                actions = actions,
                systemBarInsets = systemBarInsets,
            )
        }
    }
}

@Composable
private fun AndroidPdfReaderTopBar(
    title: String,
    pageLabel: String,
    topInset: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
        shadowElevation = 4.dp,
    ) {
        Column {
            Spacer(modifier = Modifier.height(topInset))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AndroidPdfReaderChromeDefaults.TopBarHeight)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    ToreadSymbolIcon(
                        symbol = ToreadSymbols.ArrowBack,
                        contentDescription = ToreadStrings.BackToBookshelf,
                        autoMirror = true,
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = pageLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                IconButton(onClick = onOpenSettings) {
                    ToreadSymbolIcon(
                        symbol = ToreadSymbols.Settings,
                        contentDescription = ToreadStrings.ReaderSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun AndroidPdfReaderBottomBar(
    state: AndroidPdfReaderChromeState,
    sliderPreviewLabel: String,
    bottomInset: androidx.compose.ui.unit.Dp,
    actions: AndroidPdfReaderChromeActions,
) {
    val horizontalReading = state.bookPreferences.readerMode == PdfReaderMode.PagedHorizontal
    val sliderReversed = horizontalReading && state.bookPreferences.rightToLeft
    val previousSymbol = if (sliderReversed) {
        ToreadSymbols.KeyboardArrowRight
    } else {
        ToreadSymbols.KeyboardArrowLeft
    }
    val nextSymbol = if (sliderReversed) {
        ToreadSymbols.KeyboardArrowLeft
    } else {
        ToreadSymbols.KeyboardArrowRight
    }
    val safeSliderRange = state.sliderValueRange.safeSliderRange()
    val safeSliderValue = state.sliderValue.coerceIn(
        minimumValue = safeSliderRange.start,
        maximumValue = safeSliderRange.endInclusive,
    )

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 8.dp,
                    top = 6.dp,
                    end = 8.dp,
                    bottom = bottomInset + 6.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = actions.onFirstPage,
                    enabled = state.canGoPrevious,
                ) {
                    Text(ToreadStrings.FirstPage)
                }
                IconButton(
                    onClick = actions.onPreviousPage,
                    enabled = state.canGoPrevious,
                ) {
                    ToreadSymbolIcon(
                        symbol = previousSymbol,
                        contentDescription = ToreadStrings.PreviousPage,
                    )
                }
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (sliderReversed) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
                ) {
                    Slider(
                        value = safeSliderValue,
                        onValueChange = actions.onSliderPreview,
                        onValueChangeFinished = actions.onSliderCommit,
                        valueRange = safeSliderRange,
                        enabled = state.sliderEnabled &&
                            state.sliderValueRange.endInclusive >
                            state.sliderValueRange.start,
                        modifier = Modifier.weight(1f),
                    )
                }
                IconButton(
                    onClick = actions.onNextPage,
                    enabled = state.canGoNext,
                ) {
                    ToreadSymbolIcon(
                        symbol = nextSymbol,
                        contentDescription = ToreadStrings.NextPage,
                    )
                }
                TextButton(
                    onClick = actions.onLastPage,
                    enabled = state.canGoNext,
                ) {
                    Text(ToreadStrings.LastPage)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = sliderPreviewLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                FilterChip(
                    selected = state.autoPageTurning,
                    onClick = {
                        actions.onAutoPageTurningChange(!state.autoPageTurning)
                    },
                    label = {
                        Text(
                            if (state.autoPageTurning) {
                                ToreadStrings.AutoPageTurningOn
                            } else {
                                ToreadStrings.AutoPageTurning
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReaderFloatingBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.small,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AndroidPdfReaderSettingsPanel(
    state: AndroidPdfReaderChromeState,
    actions: AndroidPdfReaderChromeActions,
    systemBarInsets: SystemBarInsets,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(systemBarInsets.top))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AndroidPdfReaderChromeDefaults.TopBarHeight)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ToreadStrings.PdfReaderSettings,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = actions.onDismissSettings) {
                    Text(ToreadStrings.Close)
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ReaderSettingSectionTitle(ToreadStrings.ReadingMode)
                Column(modifier = Modifier.selectableGroup()) {
                    state.availableReaderModes.forEach { mode ->
                        ReaderSettingRadioRow(
                            title = mode.displayName(),
                            selected = state.bookPreferences.readerMode == mode,
                            onSelect = { actions.onReaderModeChange(mode) },
                        )
                    }
                }

                if (state.showSpreadSettings) {
                    ReaderSettingSectionTitle(ToreadStrings.PdfSpreadSettings)
                    ReaderSettingSwitchRow(
                        title = ToreadStrings.LandscapeDoublePage,
                        checked = state.preferences.landscapeDoublePageEnabled,
                        onCheckedChange =
                            actions.onLandscapeDoublePageEnabledChange,
                    )
                    ReaderSettingSwitchRow(
                        title = ToreadStrings.RightToLeftReading,
                        checked = state.bookPreferences.rightToLeft,
                        onCheckedChange = actions.onRightToLeftChange,
                    )
                    ReaderSettingSwitchRow(
                        title = ToreadStrings.CoverAlone,
                        checked = state.bookPreferences.coverAlone,
                        enabled = state.preferences.landscapeDoublePageEnabled,
                        onCheckedChange = actions.onCoverAloneChange,
                    )
                }

                ReaderSettingSectionTitle(ToreadStrings.ScreenOrientation)
                Column(modifier = Modifier.selectableGroup()) {
                    PdfOrientationMode.entries.forEach { mode ->
                        ReaderSettingRadioRow(
                            title = mode.displayName(),
                            selected = state.preferences.orientationMode == mode,
                            onSelect = { actions.onOrientationModeChange(mode) },
                        )
                    }
                }

                ReaderSettingSectionTitle(ToreadStrings.ReaderInteraction)
                if (state.showPagedInteractionSettings) {
                    ReaderSettingSwitchRow(
                        title = ToreadStrings.TapToTurnPages,
                        checked = state.preferences.tapToTurnPagesEnabled,
                        onCheckedChange = actions.onTapToTurnPagesEnabledChange,
                    )
                    ReaderSettingSwitchRow(
                        title = ToreadStrings.ReverseTapToTurnPages,
                        checked = state.preferences.reverseTapToTurnPages,
                        enabled = state.preferences.tapToTurnPagesEnabled,
                        onCheckedChange = actions.onReverseTapToTurnPagesChange,
                    )
                    ReaderSettingSwitchRow(
                        title = ToreadStrings.PageAnimation,
                        checked = state.preferences.pageAnimationEnabled,
                        onCheckedChange = actions.onPageAnimationEnabledChange,
                    )
                }
                ReaderSettingSwitchRow(
                    title = ToreadStrings.DoubleTapToZoom,
                    checked = state.preferences.doubleTapZoomEnabled,
                    onCheckedChange = actions.onDoubleTapZoomEnabledChange,
                )
                ReaderSettingSwitchRow(
                    title = ToreadStrings.LongPressToZoom,
                    checked = state.preferences.longPressZoomEnabled,
                    onCheckedChange = actions.onLongPressZoomEnabledChange,
                )
                ReaderSettingSwitchRow(
                    title = ToreadStrings.TurnPagesWithVolumeKeys,
                    subtitle = ToreadStrings.VolumeKeysDefaultOffHint,
                    checked = state.preferences.volumeKeysEnabled,
                    onCheckedChange = actions.onVolumeKeysEnabledChange,
                )

                ReaderSettingSectionTitle(ToreadStrings.ReaderDisplay)
                ReaderSettingSwitchRow(
                    title = ToreadStrings.ShowPageNumber,
                    checked = state.preferences.showPageNumber,
                    onCheckedChange = actions.onShowPageNumberChange,
                )
                ReaderSettingSwitchRow(
                    title = ToreadStrings.ShowReaderStatusInfo,
                    checked = state.preferences.showStatusInfo,
                    onCheckedChange = actions.onShowStatusInfoChange,
                )

                ReaderSettingSectionTitle(ToreadStrings.AutoPageTurning)
                ReaderIntervalSetting(
                    seconds = state.preferences.autoPageTurningIntervalSeconds,
                    onSecondsChange =
                        actions.onAutoPageTurningIntervalSecondsChange,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(systemBarInsets.bottom))
        }
    }
}

@Composable
private fun ReaderSettingSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ReaderSettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

@Composable
private fun ReaderSettingRadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ReaderIntervalSetting(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
) {
    val boundedSeconds = seconds.coerceIn(
        AndroidPdfReaderChromeDefaults.MinAutoPageTurningSeconds,
        AndroidPdfReaderChromeDefaults.MaxAutoPageTurningSeconds,
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ToreadStrings.AutoPageTurningInterval,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = ToreadStrings.seconds(boundedSeconds),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = boundedSeconds.toFloat(),
            onValueChange = { value ->
                onSecondsChange(
                    value.roundToInt().coerceIn(
                        AndroidPdfReaderChromeDefaults.MinAutoPageTurningSeconds,
                        AndroidPdfReaderChromeDefaults.MaxAutoPageTurningSeconds,
                    ),
                )
            },
            valueRange =
                AndroidPdfReaderChromeDefaults.MinAutoPageTurningSeconds.toFloat()..
                    AndroidPdfReaderChromeDefaults.MaxAutoPageTurningSeconds.toFloat(),
            steps = AndroidPdfReaderChromeDefaults.AutoPageTurningSliderSteps,
        )
    }
}

private fun AndroidPdfReaderChromeState.visiblePageLabel(): String =
    pageRangeLabel(
        pageIndices = visiblePageIndices,
        fallbackPageIndex = currentPageIndex,
    )

private fun AndroidPdfReaderChromeState.sliderPreviewLabel(): String =
    pageRangeLabel(
        pageIndices = sliderPreviewPageIndices,
        fallbackPageIndex = currentPageIndex,
    )

private fun AndroidPdfReaderChromeState.pageRangeLabel(
    pageIndices: List<Int>,
    fallbackPageIndex: Int,
): String {
    if (pageCount <= 0) return "0 / 0"
    val safeIndices = pageIndices
        .asSequence()
        .filter { it in 0 until pageCount }
        .distinct()
        .sorted()
        .toList()
        .ifEmpty { listOf(fallbackPageIndex.coerceIn(0, pageCount - 1)) }
    return ToreadStrings.pdfPageRange(safeIndices, pageCount)
}

private fun ClosedFloatingPointRange<Float>.safeSliderRange():
    ClosedFloatingPointRange<Float> =
    if (endInclusive > start) {
        this
    } else {
        start..(start + 1f)
    }

private fun PdfReaderMode.displayName(): String = when (this) {
    PdfReaderMode.PagedHorizontal -> ToreadStrings.PdfReaderModePagedHorizontal
    PdfReaderMode.PagedVertical -> ToreadStrings.PdfReaderModePagedVertical
    PdfReaderMode.ContinuousVertical -> ToreadStrings.PdfReaderModeContinuousVertical
}

private fun PdfOrientationMode.displayName(): String = when (this) {
    PdfOrientationMode.System -> ToreadStrings.OrientationSystem
    PdfOrientationMode.Portrait -> ToreadStrings.OrientationPortrait
    PdfOrientationMode.Landscape -> ToreadStrings.OrientationLandscape
}

private object AndroidPdfReaderChromeDefaults {
    const val AnimationMillis = 180
    const val MinAutoPageTurningSeconds = 1
    const val MaxAutoPageTurningSeconds = 20
    const val AutoPageTurningSliderSteps =
        MaxAutoPageTurningSeconds - MinAutoPageTurningSeconds - 1

    val TopBarHeight = 56.dp
    val SettingsPanelMaxWidth = 400.dp
}
