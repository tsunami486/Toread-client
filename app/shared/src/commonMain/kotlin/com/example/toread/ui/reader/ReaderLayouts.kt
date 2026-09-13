package com.example.toread.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.library.BookshelfBook
import com.example.toread.reader.EpubImageBytesLoader
import com.example.toread.platform.rememberSystemBarInsets
import com.example.toread.ui.components.ScreenColumn
import com.example.toread.ui.icons.ToreadSymbolIcon
import com.example.toread.ui.icons.ToreadSymbols
import com.example.toread.ui.text.ToreadStrings
import com.example.toread.ui.theme.ToreadTheme
import com.example.toread.ui.theme.toreadColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.roundToInt

private val DesktopReaderBackground = Color(0xFF0F1214)
private val DesktopReaderToolbarText = Color(0xFFAEB7BF)
private val DesktopReaderControlBackground = Color(0xFF1A1F23)
private val DesktopReaderPageBackground = Color(0xFF1B2023)
private val DesktopReaderPageText = Color(0xFFE4EAF0)
private val DesktopReaderDivider = Color(0xFF2A3136)
private val DesktopReaderMutedText = Color(0xFF9AA5AE)
private val DesktopReaderDrawerText = Color(0xFFDCE4EA)

private data class DesktopReaderPalette(
    val background: Color,
    val toolbarText: Color,
    val controlBackground: Color,
    val pageBackground: Color,
    val pageText: Color,
    val divider: Color,
    val mutedText: Color,
    val drawerText: Color,
    val scrim: Color,
)

private val DesktopNightReaderPalette = DesktopReaderPalette(
    background = DesktopReaderBackground,
    toolbarText = DesktopReaderToolbarText,
    controlBackground = DesktopReaderControlBackground,
    pageBackground = DesktopReaderPageBackground,
    pageText = DesktopReaderPageText,
    divider = DesktopReaderDivider,
    mutedText = DesktopReaderMutedText,
    drawerText = DesktopReaderDrawerText,
    scrim = Color.Black.copy(alpha = 0.48f),
)

private val DesktopLightReaderPalette = DesktopReaderPalette(
    background = Color(0xFFECEFF1),
    toolbarText = Color(0xFF4B5560),
    controlBackground = Color(0xFFD9DEE4),
    pageBackground = Color(0xFFF8F7F2),
    pageText = Color(0xFF202428),
    divider = Color(0xFFD4D2CB),
    mutedText = Color(0xFF66717C),
    drawerText = Color(0xFF23282E),
    scrim = Color.Black.copy(alpha = 0.22f),
)

@Composable
internal fun DesktopReaderLayout(
    book: BookshelfBook,
    sourceText: String,
    pages: List<ReaderPageSlice>,
    pageIndex: Int,
    progress: Double,
    isDoublePage: Boolean,
    isNightMode: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onTogglePageMode: () -> Unit,
    onToggleNightMode: () -> Unit,
    onChapterDrawerClosed: () -> Unit,
    chapterEntries: List<ReaderChapterEntry>? = null,
    currentChapterOrd: Int? = null,
    canPrevious: Boolean = pageIndex > 0,
    canNext: Boolean = if (isDoublePage) pageIndex + 2 <= pages.lastIndex else pageIndex < pages.lastIndex,
    onSelectChapterOrd: ((Int) -> Unit)? = null,
    showChapterPageNumbers: Boolean = true,
) {
    val palette = if (isNightMode) DesktopNightReaderPalette else DesktopLightReaderPalette
    val derivedChapterEntries = remember(pages) { pages.readerChapterEntries() }
    val displayedChapterEntries = chapterEntries ?: derivedChapterEntries
    val displayedCurrentChapterOrd = currentChapterOrd
        ?: pages.getOrNull(pageIndex)?.chapterOrd
        ?: displayedChapterEntries.lastOrNull { it.pageIndex <= pageIndex }?.chapterOrd
        ?: 0
    var chapterDrawerVisible by remember { mutableStateOf(false) }
    var chapterDrawerWasVisible by remember { mutableStateOf(false) }

    LaunchedEffect(chapterDrawerVisible) {
        if (chapterDrawerWasVisible && !chapterDrawerVisible) {
            onChapterDrawerClosed()
        }
        chapterDrawerWasVisible = chapterDrawerVisible
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 72.dp, top = 24.dp, end = 72.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DesktopReaderTopBar(
                bookTitle = book.title,
                isDoublePage = isDoublePage,
                isNightMode = isNightMode,
                palette = palette,
                onBack = onBack,
                onOpenChapterDrawer = { chapterDrawerVisible = true },
                onTogglePageMode = onTogglePageMode,
                onToggleNightMode = onToggleNightMode,
            )
            DesktopReaderSpread(
                bookTitle = book.title,
                sourceText = sourceText,
                pages = pages,
                pageIndex = pageIndex,
                progress = progress,
                isDoublePage = isDoublePage,
                palette = palette,
                canPrevious = canPrevious,
                canNext = canNext,
                onPrevious = onPrevious,
                onNext = onNext,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .aspectRatio(if (isDoublePage) DesktopSpreadAspectRatio else ASeriesPaperAspectRatio)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (chapterDrawerVisible) {
            DesktopChapterDrawer(
                bookTitle = book.title,
                chapters = displayedChapterEntries,
                currentChapterOrd = displayedCurrentChapterOrd,
                palette = palette,
                showChapterPageNumbers = showChapterPageNumbers,
                onDismiss = { chapterDrawerVisible = false },
                onSelectChapter = { chapter ->
                    chapterDrawerVisible = false
                    onSelectChapterOrd?.invoke(chapter.chapterOrd) ?: onGoToPage(chapter.pageIndex)
                },
            )
        }
    }
}

@Composable
private fun DesktopReaderTopBar(
    bookTitle: String,
    isDoublePage: Boolean,
    isNightMode: Boolean,
    palette: DesktopReaderPalette,
    onBack: () -> Unit,
    onOpenChapterDrawer: () -> Unit,
    onTogglePageMode: () -> Unit,
    onToggleNightMode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 1240.dp)
            .height(36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            ToreadSymbolIcon(
                symbol = ToreadSymbols.ArrowBack,
                contentDescription = ToreadStrings.BackToBookshelf,
                tint = palette.toolbarText,
                modifier = Modifier.size(18.dp),
                autoMirror = true,
            )
            Text(
                text = bookTitle,
                color = palette.toolbarText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopReaderToolbarButton(
                label = ToreadStrings.Catalog,
                symbol = ToreadSymbols.Menu,
                palette = palette,
                onClick = onOpenChapterDrawer,
            )
            DesktopReaderPageModeButton(
                isDoublePage = isDoublePage,
                palette = palette,
                onClick = onTogglePageMode,
            )
            DesktopReaderToolbarButton(
                label = if (isNightMode) ToreadStrings.SwitchLightMode else ToreadStrings.SwitchDarkMode,
                symbol = if (isNightMode) ToreadSymbols.LightMode else ToreadSymbols.DarkMode,
                palette = palette,
                onClick = onToggleNightMode,
            )
            DesktopReaderToolbarButton(label = ToreadStrings.Font, symbol = ToreadSymbols.TextFields, palette = palette)
            DesktopReaderToolbarButton(label = ToreadStrings.DecreaseFontSize, symbol = ToreadSymbols.Remove, palette = palette)
            DesktopReaderToolbarButton(label = ToreadStrings.IncreaseFontSize, symbol = ToreadSymbols.Add, palette = palette)
        }
    }
}

@Composable
private fun DesktopReaderToolbarButton(
    label: String,
    symbol: DrawableResource,
    palette: DesktopReaderPalette,
    onClick: () -> Unit = {},
    autoMirror: Boolean = false,
) {
    DesktopReaderToolbarButtonFrame(
        label = label,
        palette = palette,
        onClick = onClick,
    ) {
        ToreadSymbolIcon(
            symbol = symbol,
            contentDescription = label,
            tint = palette.toolbarText,
            modifier = Modifier.size(18.dp),
            autoMirror = autoMirror,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopReaderToolbarButtonFrame(
    label: String,
    palette: DesktopReaderPalette,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(label)
            }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(34.dp)
                .background(palette.background, RoundedCornerShape(50)),
        ) {
            icon()
        }
    }
}

@Composable
private fun DesktopReaderToolbarButton(
    label: String,
    symbol: ImageVector,
    palette: DesktopReaderPalette,
    onClick: () -> Unit = {},
    autoMirror: Boolean = false,
) {
    DesktopReaderToolbarButtonFrame(
        label = label,
        palette = palette,
        onClick = onClick,
    ) {
        ToreadSymbolIcon(
            symbol = symbol,
            contentDescription = label,
            tint = palette.toolbarText,
            modifier = Modifier.size(20.dp),
            autoMirror = autoMirror,
        )
    }
}

@Composable
private fun DesktopReaderPageModeButton(
    isDoublePage: Boolean,
    palette: DesktopReaderPalette,
    onClick: () -> Unit,
) {
    if (isDoublePage) {
        DesktopReaderToolbarButton(
            label = ToreadStrings.SwitchSinglePageReading,
            symbol = ToreadSymbols.Article,
            palette = palette,
            onClick = onClick,
            autoMirror = true,
        )
    } else {
        DesktopReaderToolbarButton(
            label = ToreadStrings.SwitchDoublePageReading,
            symbol = ToreadSymbols.TwoPager,
            palette = palette,
            onClick = onClick,
        )
    }
}

@Composable
private fun DesktopReaderSpread(
    bookTitle: String,
    sourceText: String,
    pages: List<ReaderPageSlice>,
    pageIndex: Int,
    progress: Double,
    isDoublePage: Boolean,
    palette: DesktopReaderPalette,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = DesktopReaderTypography
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = palette.pageBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp, top = 30.dp, end = 32.dp, bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                ReaderTextPage(
                    page = pages.getOrNull(pageIndex) ?: ReaderPageSlice(bookTitle, 0, 0),
                    sourceText = sourceText,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    textColor = palette.pageText,
                    bodyFontSize = typography.bodyFontSize,
                    bodyLineHeight = typography.bodyLineHeight,
                    bodyFontWeight = FontWeight.Medium,
                    bodyLetterSpacing = typography.bodyLetterSpacing,
                    bodyTextAlign = TextAlign.Start,
                )
                if (isDoublePage) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(palette.divider),
                    )
                    ReaderTextPage(
                        page = pages.getOrNull(pageIndex + 1) ?: ReaderPageSlice(bookTitle, 0, 0),
                        sourceText = sourceText,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        textColor = palette.pageText,
                        bodyFontSize = typography.bodyFontSize,
                        bodyLineHeight = typography.bodyLineHeight,
                        bodyFontWeight = FontWeight.Medium,
                        bodyLetterSpacing = typography.bodyLetterSpacing,
                        bodyTextAlign = TextAlign.Start,
                    )
                }
            }
            DesktopReaderBottomBar(
                progress = progress,
                canPrevious = canPrevious,
                canNext = canNext,
                palette = palette,
                onPrevious = onPrevious,
                onNext = onNext,
            )
        }
    }
}

@Composable
private fun DesktopReaderBottomBar(
    progress: Double,
    canPrevious: Boolean,
    canNext: Boolean,
    palette: DesktopReaderPalette,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = canPrevious,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = palette.toolbarText,
                disabledContentColor = palette.mutedText.copy(alpha = 0.45f),
            ),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            ToreadSymbolIcon(
                symbol = ToreadSymbols.KeyboardArrowLeft,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                autoMirror = true,
            )
            Text(ToreadStrings.PreviousPage)
        }
        Text(
            text = progress.toReaderPercentText(),
            color = palette.mutedText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.Center),
        )
        OutlinedButton(
            onClick = onNext,
            enabled = canNext,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = palette.toolbarText,
                disabledContentColor = palette.mutedText.copy(alpha = 0.45f),
            ),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Text(ToreadStrings.NextPage)
            ToreadSymbolIcon(
                symbol = ToreadSymbols.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                autoMirror = true,
            )
        }
    }
}

private fun Double.toReaderPercentText(): String {
    val scaled = (coerceIn(0.0, 1.0) * 10000).roundToInt()
    val whole = scaled / 100
    val fraction = (scaled % 100).toString().padStart(2, '0')
    return "$whole.$fraction%"
}

@Composable
private fun BoxScope.DesktopChapterDrawer(
    bookTitle: String,
    chapters: List<ReaderChapterEntry>,
    currentChapterOrd: Int,
    palette: DesktopReaderPalette,
    showChapterPageNumbers: Boolean,
    onDismiss: () -> Unit,
    onSelectChapter: (ReaderChapterEntry) -> Unit,
) {
    val chapterListState = rememberLazyListState()
    val currentChapterIndex = chapters.indexOfFirst { it.chapterOrd == currentChapterOrd }

    LaunchedEffect(currentChapterIndex, chapters.size) {
        if (currentChapterIndex >= 0) {
            chapterListState.scrollItemToCenter(currentChapterIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.scrim)
            .clickable(onClick = onDismiss),
    )
    Card(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(380.dp),
        shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.pageBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, bottom = 22.dp),
        ) {
            Text(
                text = ToreadStrings.Catalog,
                color = palette.drawerText,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = bookTitle,
                color = palette.mutedText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 14.dp),
            )
            Text(
                text = ToreadStrings.readChapterSummary(
                    read = chapters.count { it.chapterOrd < currentChapterOrd },
                    total = chapters.size,
                ),
                color = palette.mutedText,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = palette.divider,
            )
            if (chapters.isEmpty()) {
                Text(
                    text = ToreadStrings.EmptyChapterList,
                    color = palette.mutedText,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp),
                )
            } else {
                ReaderChapterList(
                    state = chapterListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp),
                    trackColor = palette.divider.copy(alpha = 0.55f),
                    thumbColor = palette.mutedText.copy(alpha = 0.72f),
                ) {
                    items(chapters, key = { it.chapterOrd }) { chapter ->
                        DesktopChapterRow(
                            chapter = chapter,
                            currentChapterOrd = currentChapterOrd,
                            palette = palette,
                            showPageNumber = showChapterPageNumbers,
                            onSelectChapter = onSelectChapter,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopChapterRow(
    chapter: ReaderChapterEntry,
    currentChapterOrd: Int,
    palette: DesktopReaderPalette,
    showPageNumber: Boolean,
    onSelectChapter: (ReaderChapterEntry) -> Unit,
) {
    val isCurrent = chapter.chapterOrd == currentChapterOrd
    val textColor = when {
        chapter.chapterOrd < currentChapterOrd -> palette.mutedText
        isCurrent -> MaterialTheme.toreadColors.currentChapter
        else -> palette.drawerText
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(
                if (isCurrent) MaterialTheme.toreadColors.currentChapter.copy(alpha = 0.12f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable { onSelectChapter(chapter) }
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(
            text = chapter.title,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showPageNumber) {
            Text(
                text = ToreadStrings.chapterPage(chapter.pageIndex),
                color = palette.mutedText,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun AndroidReaderLayout(
    book: BookshelfBook,
    sourceText: String,
    pages: List<ReaderPageSlice>,
    pageIndex: Int,
    progress: Double,
    controlsVisible: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onToggleControls: () -> Unit,
    chapterEntries: List<ReaderChapterEntry>? = null,
    currentChapterOrd: Int? = null,
    canPrevious: Boolean = pageIndex > 0,
    canNext: Boolean = pageIndex < pages.lastIndex,
    onSelectChapterOrd: ((Int) -> Unit)? = null,
    chapterPages: ReaderChapterPagePosition? = null,
    onSeekChapterPage: ((Int) -> Unit)? = null,
    nightMode: Boolean = false,
    onToggleNightMode: (() -> Unit)? = null,
    readerSurface: ReaderSurfaceColors = ReaderSurfaceColors.Day,
) {
    val typography = AndroidReaderTypography
    val systemBarInsets = rememberSystemBarInsets()
    val topInset = systemBarInsets.top
    val bottomInset = systemBarInsets.bottom
    val readerTopPadding = 10.dp + topInset
    val readerBottomPadding = 10.dp + bottomInset
    val derivedChapterEntries = remember(pages) { pages.readerChapterEntries() }
    val displayedChapterEntries = chapterEntries ?: derivedChapterEntries
    val displayedCurrentChapterOrd = currentChapterOrd
        ?: pages.getOrNull(pageIndex)?.chapterOrd
        ?: displayedChapterEntries.lastOrNull { it.pageIndex <= pageIndex }?.chapterOrd
        ?: 0
    // The panel steps chapters rather than pages: the slider already covers page seeking, and the
    // chapter list is the same source the drawer uses, so both stay consistent.
    val (previousChapterEntry, nextChapterEntry) = remember(
        displayedChapterEntries,
        displayedCurrentChapterOrd,
    ) {
        displayedChapterEntries.lastOrNull { it.chapterOrd < displayedCurrentChapterOrd } to
            displayedChapterEntries.firstOrNull { it.chapterOrd > displayedCurrentChapterOrd }
    }
    val goToChapterEntry: (ReaderChapterEntry) -> Unit = { chapter ->
        onSelectChapterOrd?.invoke(chapter.chapterOrd) ?: onGoToPage(chapter.pageIndex)
    }
    var chapterDrawerVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidReflowPager(
            pages = ReflowPagerPages(
                current = pages.getOrNull(pageIndex) ?: ReaderPageSlice(book.title, 0, 0),
                previous = pages.getOrNull(pageIndex - 1),
                next = pages.getOrNull(pageIndex + 1),
            ),
            canPrevious = canPrevious,
            canNext = canNext,
            onPrevious = onPrevious,
            onNext = onNext,
            onTapCenter = onToggleControls,
            enabled = !chapterDrawerVisible,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ReaderTextPage(
                page = page,
                sourceText = sourceText,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 24.dp,
                        top = readerTopPadding,
                        end = 24.dp,
                        bottom = readerBottomPadding,
                    ),
                textColor = readerSurface.text,
                bodyFontSize = typography.bodyFontSize,
                bodyLineHeight = typography.bodyLineHeight,
                bodyLetterSpacing = typography.bodyLetterSpacing,
            )
        }

        if (controlsVisible) {
            // The top bar belongs to the reading surface too, so it follows the reader's own switch.
            ToreadTheme(useDynamicColor = false, darkTheme = nightMode) {
                AndroidReaderTopBar(book = book, onBack = onBack)
            }
            AndroidReaderBottomPanel(
                progress = progress,
                chapterPages = chapterPages,
                canPreviousChapter = previousChapterEntry != null,
                canNextChapter = nextChapterEntry != null,
                onPreviousChapter = { previousChapterEntry?.let(goToChapterEntry) },
                onNextChapter = { nextChapterEntry?.let(goToChapterEntry) },
                onSeekChapterPage = onSeekChapterPage,
                currentChapterOrd = displayedCurrentChapterOrd,
                nightMode = nightMode,
                onToggleNightMode = onToggleNightMode,
                onOpenChapterList = { chapterDrawerVisible = true },
            )
        }
        if (chapterDrawerVisible) {
            // Same rule as the bottom panel: the drawer belongs to the reader chrome, so it follows
            // the reader's day/night switch rather than the app theme.
            ToreadTheme(useDynamicColor = false, darkTheme = nightMode) {
                AndroidChapterDrawer(
                    bookTitle = book.title,
                    chapters = displayedChapterEntries,
                    currentChapterOrd = displayedCurrentChapterOrd,
                    onDismiss = { chapterDrawerVisible = false },
                    onSelectChapter = { chapter ->
                        chapterDrawerVisible = false
                        onSelectChapterOrd?.invoke(chapter.chapterOrd) ?: onGoToPage(chapter.pageIndex)
                    },
                )
            }
        }
    }
}

@Composable
private fun AndroidReaderTopBar(book: BookshelfBook, onBack: () -> Unit) {
    val topInset = rememberSystemBarInsets().top

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(start = 14.dp, top = topInset + 10.dp, end = 14.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            ToreadSymbolIcon(
                symbol = ToreadSymbols.ArrowBack,
                contentDescription = ToreadStrings.BackToBookshelf,
                autoMirror = true,
            )
        }
        Text(
            book.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            // Explicit: this bar may sit in the reader's night theme while the app theme is light, and
            // the inherited content color would still come from the app theme.
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun BoxScope.AndroidReaderBottomPanel(
    progress: Double,
    chapterPages: ReaderChapterPagePosition?,
    canPreviousChapter: Boolean,
    canNextChapter: Boolean,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSeekChapterPage: ((Int) -> Unit)?,
    currentChapterOrd: Int,
    nightMode: Boolean,
    onToggleNightMode: (() -> Unit)?,
    onOpenChapterList: () -> Unit,
) {
    val bottomInset = rememberSystemBarInsets().bottom
    // The panel follows the reader's own day/night switch instead of the app theme, so it keeps
    // matching the page surface while the reader is in night mode.
    var draggedPage by remember { mutableStateOf<Int?>(null) }
    // The reader publishes a seeked page asynchronously, so the panel keeps showing the released page
    // until the reader reports it. Dropping it on release would flash the thumb back to the page the
    // reader still displays and only then jump to the target.
    var pendingSeek by remember { mutableStateOf<ChapterPageSeek?>(null) }
    val shownPage = chapterSliderPage(
        draggedPage = draggedPage,
        pendingSeek = pendingSeek,
        chapterPages = chapterPages,
    )

    LaunchedEffect(pendingSeek, chapterPages, currentChapterOrd) {
        val seek = pendingSeek ?: return@LaunchedEffect
        if (chapterPageSeekSettled(seek, currentChapterOrd, chapterPages)) pendingSeek = null
    }
    LaunchedEffect(pendingSeek) {
        val seek = pendingSeek ?: return@LaunchedEffect
        delay(ReaderChapterSeekLatchMillis)
        // The reader never published the requested page, so follow it again instead of freezing.
        if (pendingSeek == seek) pendingSeek = null
    }

    ToreadTheme(useDynamicColor = false, darkTheme = nightMode) {
        Card(
            // Flush with the screen edges: only the top corners are rounded and the system bar inset is
            // paid inside the card, so no background shows through around the panel.
            shape = RoundedCornerShape(
                topStart = ReaderSheetCornerRadius,
                topEnd = ReaderSheetCornerRadius,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 18.dp,
                    top = 16.dp,
                    end = 18.dp,
                    bottom = bottomInset + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReaderChapterStepButton(
                        description = ToreadStrings.PreviousChapter,
                        symbol = ToreadSymbols.KeyboardDoubleArrowLeft,
                        enabled = canPreviousChapter,
                        onClick = onPreviousChapter,
                    )
                    Text(
                        text = if (chapterPages != null && shownPage != null) {
                            ToreadStrings.chapterPageProgress(shownPage, chapterPages.pageCount)
                        } else {
                            ToreadStrings.readerPercent(progress)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    ReaderChapterStepButton(
                        description = ToreadStrings.NextChapter,
                        symbol = ToreadSymbols.KeyboardDoubleArrowRight,
                        enabled = canNextChapter,
                        onClick = onNextChapter,
                    )
                }
                ChapterPageSlider(
                    chapterPages = chapterPages,
                    shownPage = shownPage,
                    bookProgress = progress,
                    onDragPage = { draggedPage = it },
                    onSeekChapterPage = { target ->
                        // Hold the released page until the reader publishes it, then follow the reader.
                        pendingSeek = ChapterPageSeek(chapterOrd = currentChapterOrd, target = target)
                        onSeekChapterPage?.invoke(target)
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReaderPanelButton(label = ToreadStrings.Catalog, symbol = ToreadSymbols.Menu, onClick = onOpenChapterList)
                    ReaderPanelButton(
                        label = if (nightMode) ToreadStrings.DayMode else ToreadStrings.NightMode,
                        symbol = if (nightMode) ToreadSymbols.LightMode else ToreadSymbols.DarkMode,
                        onClick = onToggleNightMode ?: {},
                    )
                    ReaderPanelButton(label = ToreadStrings.ReaderSettings, symbol = ToreadSymbols.Settings)
                }
            }
        }
    }
}

/**
 * Chapter step for the reading panel: icon only, so the panel row keeps exactly one text label (the
 * chapter page position). The description stays for accessibility, and `IconButton` supplies the
 * disabled tint while the book has no neighbouring chapter.
 */
@Composable
private fun ReaderChapterStepButton(
    description: String,
    symbol: DrawableResource,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        ToreadSymbolIcon(
            symbol = symbol,
            contentDescription = description,
            autoMirror = true,
        )
    }
}

/**
 * Draggable chapter-page slider drawn as one capsule: the track and the plain white circular thumb
 * share a single height, so the part the reader has already passed runs straight into the thumb
 * instead of stopping beside it. The seek is committed on release only, so dragging never restarts
 * pagination per frame.
 *
 * Falls back to the book-wide progress bar while the chapter page count is still being measured.
 */
@Composable
private fun ChapterPageSlider(
    chapterPages: ReaderChapterPagePosition?,
    shownPage: Int?,
    bookProgress: Double,
    onDragPage: (Int?) -> Unit,
    onSeekChapterPage: ((Int) -> Unit)?,
) {
    if (chapterPages == null || shownPage == null || onSeekChapterPage == null) {
        LinearProgressIndicator(
            progress = { bookProgress.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    if (chapterPages.pageCount <= 1) {
        LinearProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val activeTrackColor = MaterialTheme.colorScheme.primary
    val inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)

    Slider(
        value = shownPage.toFloat(),
        onValueChange = { value ->
            onDragPage(value.roundToInt().coerceIn(1, chapterPages.pageCount))
        },
        valueRange = 1f..chapterPages.pageCount.toFloat(),
        onValueChangeFinished = {
            onDragPage(null)
            if (shownPage != chapterPages.pageNumber) {
                onSeekChapterPage(shownPage)
            }
        },
        thumb = {
            // No outline: the thumb is the rounded cap of the track, not a separate handle.
            Box(
                modifier = Modifier
                    .size(ReaderSliderThumbSize)
                    .background(Color.White, CircleShape),
            )
        },
        track = { sliderState ->
            val range = sliderState.valueRange
            val fraction = (
                (sliderState.value - range.start) / (range.endInclusive - range.start)
                ).coerceIn(0f, 1f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ReaderSliderThumbSize),
            ) {
                val radius = size.height / 2f
                val capsule = CornerRadius(radius, radius)
                drawRoundRect(color = inactiveTrackColor, cornerRadius = capsule)
                // The thumb travels between the capsule's rounded ends, so the passed part has to end
                // exactly at the thumb's centre; the seam stays hidden under the thumb.
                drawRoundRect(
                    color = activeTrackColor,
                    size = Size(
                        width = radius + (size.width - 2f * radius) * fraction,
                        height = size.height,
                    ),
                    cornerRadius = capsule,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReaderPanelButton(label: String, symbol: DrawableResource, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ToreadSymbolIcon(
            symbol = symbol,
            contentDescription = label,
            modifier = Modifier.height(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BoxScope.AndroidChapterDrawer(
    bookTitle: String,
    chapters: List<ReaderChapterEntry>,
    currentChapterOrd: Int,
    onDismiss: () -> Unit,
    onSelectChapter: (ReaderChapterEntry) -> Unit,
) {
    val bottomInset = rememberSystemBarInsets().bottom
    val chapterListState = rememberLazyListState()
    val currentChapterIndex = chapters.indexOfFirst { it.chapterOrd == currentChapterOrd }

    LaunchedEffect(currentChapterIndex, chapters.size) {
        if (currentChapterIndex >= 0) {
            chapterListState.scrollItemToCenter(currentChapterIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.toreadColors.readerScrim)
            .clickable(onClick = onDismiss),
    )
    Card(
        // Same shape rule as the reading panel: rounded top corners, flush with the other edges.
        shape = RoundedCornerShape(
            topStart = ReaderSheetCornerRadius,
            topEnd = ReaderSheetCornerRadius,
        ),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.74f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 10.dp, bottom = bottomInset + 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                    .align(Alignment.CenterHorizontally),
            )
            Text(
                text = bookTitle,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, top = 26.dp, end = 24.dp),
            )
            Text(
                text = ToreadStrings.readChapterSummary(
                    read = chapters.count { it.chapterOrd < currentChapterOrd },
                    total = chapters.size,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
                modifier = Modifier.padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 14.dp),
            )
            if (chapters.isEmpty()) {
                Text(
                    text = ToreadStrings.EmptyChapterList,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp),
                )
            } else {
                ReaderChapterList(
                    state = chapterListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
                    thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                ) {
                    items(chapters, key = { it.chapterOrd }) { chapter ->
                        AndroidChapterRow(
                            chapter = chapter,
                            currentChapterOrd = currentChapterOrd,
                            onSelectChapter = onSelectChapter,
                        )
                    }
                }
            }
        }
    }
}

private suspend fun LazyListState.scrollItemToCenter(index: Int) {
    scrollToItem(index)
    val itemInfo = snapshotFlow {
        layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    }.filterNotNull().first()
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val itemCenter = itemInfo.offset + itemInfo.size / 2
    scrollBy((itemCenter - viewportCenter).toFloat())
}

@Composable
private fun ReaderChapterList(
    state: LazyListState,
    contentPadding: PaddingValues,
    trackColor: Color,
    thumbColor: Color,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            content = content,
        )
        ReaderChapterScrollbar(
            state = state,
            trackColor = trackColor,
            thumbColor = thumbColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(24.dp),
        )
    }
}

private data class ReaderChapterScrollbarMetrics(
    val totalItems: Int,
    val visibleFraction: Float,
    val scrollFraction: Float,
    val averageItemHeight: Float,
    val maxFirstItemPosition: Float,
)

private fun LazyListState.chapterScrollbarMetrics(): ReaderChapterScrollbarMetrics? {
    val currentLayoutInfo = layoutInfo
    val visibleItems = currentLayoutInfo.visibleItemsInfo
    val totalItems = currentLayoutInfo.totalItemsCount
    if (totalItems <= 0 || visibleItems.isEmpty() || (!canScrollBackward && !canScrollForward)) {
        return null
    }

    val viewportHeight = (currentLayoutInfo.viewportEndOffset - currentLayoutInfo.viewportStartOffset).toFloat()
    val averageItemHeight = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
    val estimatedContentHeight = averageItemHeight * totalItems
    if (viewportHeight <= 0f || estimatedContentHeight <= viewportHeight) return null

    val visibleItemEstimate = viewportHeight / averageItemHeight
    val firstVisibleItem = visibleItems.first()
    val firstItemHiddenFraction = if (firstVisibleItem.size > 0) {
        ((currentLayoutInfo.viewportStartOffset - firstVisibleItem.offset).toFloat() / firstVisibleItem.size)
            .coerceIn(0f, 1f)
    } else {
        0f
    }
    val maxFirstItemPosition = (totalItems - visibleItemEstimate).coerceAtLeast(0f)
    val estimatedPosition = firstVisibleItem.index + firstItemHiddenFraction
    val scrollFraction = when {
        !canScrollBackward -> 0f
        !canScrollForward -> 1f
        maxFirstItemPosition <= 0f -> 0f
        else -> (estimatedPosition / maxFirstItemPosition).coerceIn(0f, 1f)
    }

    return ReaderChapterScrollbarMetrics(
        totalItems = totalItems,
        visibleFraction = (viewportHeight / estimatedContentHeight).coerceIn(0f, 1f),
        scrollFraction = scrollFraction,
        averageItemHeight = averageItemHeight,
        maxFirstItemPosition = maxFirstItemPosition,
    )
}

private fun LazyListState.requestChapterScrollFraction(
    fraction: Float,
    metrics: ReaderChapterScrollbarMetrics,
) {
    val clampedFraction = fraction.coerceIn(0f, 1f)
    when {
        clampedFraction <= 0f -> requestScrollToItem(0)
        clampedFraction >= 1f -> requestScrollToItem(metrics.totalItems - 1)
        else -> {
            val targetPosition = metrics.maxFirstItemPosition * clampedFraction
            val targetIndex = targetPosition.toInt().coerceIn(0, metrics.totalItems - 1)
            val targetOffset = ((targetPosition - targetIndex) * metrics.averageItemHeight).roundToInt()
            requestScrollToItem(targetIndex, targetOffset)
        }
    }
}

@Composable
private fun ReaderChapterScrollbar(
    state: LazyListState,
    trackColor: Color,
    thumbColor: Color,
    modifier: Modifier = Modifier,
) {
    val metrics by remember(state) {
        derivedStateOf { state.chapterScrollbarMetrics() }
    }
    val currentMetrics = metrics ?: return
    var dragFraction by remember { mutableStateOf(currentMetrics.scrollFraction) }

    BoxWithConstraints(modifier = modifier) {
        val thumbHeight = (maxHeight * currentMetrics.visibleFraction)
            .coerceAtLeast(32.dp)
            .coerceAtMost(maxHeight)
        val thumbTravel = maxHeight - thumbHeight
        val thumbOffset = thumbTravel * currentMetrics.scrollFraction
        val thumbTravelPx = with(LocalDensity.current) { thumbTravel.toPx() }
        val draggableState = rememberDraggableState { delta ->
            if (thumbTravelPx > 0f) {
                dragFraction = (dragFraction + delta / thumbTravelPx).coerceIn(0f, 1f)
                state.requestChapterScrollFraction(dragFraction, currentMetrics)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        dragFraction = metrics?.scrollFraction ?: currentMetrics.scrollFraction
                    },
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(trackColor, RoundedCornerShape(50)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = thumbOffset)
                    .width(3.dp)
                    .height(thumbHeight)
                    .background(thumbColor, RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun AndroidChapterRow(
    chapter: ReaderChapterEntry,
    currentChapterOrd: Int,
    onSelectChapter: (ReaderChapterEntry) -> Unit,
) {
    val textColor = when {
        chapter.chapterOrd < currentChapterOrd -> MaterialTheme.toreadColors.readChapter
        chapter.chapterOrd == currentChapterOrd -> MaterialTheme.toreadColors.currentChapter
        else -> MaterialTheme.toreadColors.unreadChapter
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectChapter(chapter) }
            .padding(vertical = 18.dp),
    ) {
        Text(
            text = chapter.title,
            color = textColor,
            fontSize = 21.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 18.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun ReaderTextPage(
    page: ReaderPageSlice,
    sourceText: String,
    modifier: Modifier,
    textColor: Color,
    bodyFontSize: TextUnit,
    bodyLineHeight: TextUnit,
    bodyFontWeight: FontWeight = FontWeight.Normal,
    bodyLetterSpacing: TextUnit = 0.sp,
    bodyTextAlign: TextAlign = TextAlign.Start,
) {
    val fallbackLine = page.lines.ifEmpty {
        listOf(
            ReaderTextLineInfo(
                startOffset = page.startOffset,
                endOffset = page.endOffset,
                chapterOrd = page.chapterOrd,
                chapterTitle = page.chapterTitle,
                lineHeightPx = with(LocalDensity.current) { bodyLineHeight.toPx() },
            ),
        )
    }
    val pageItems = page.items.ifEmpty {
        fallbackLine.map { ReaderPageItem.TextLine(it) }
    }

    val density = LocalDensity.current
    val baseLineHeightPx = with(density) { bodyLineHeight.toPx() }
    val renderItems = pageItems.map { item ->
        when (item) {
            is ReaderPageItem.TextLine -> {
                val lineHeightPx = item.line.lineHeightPx.coerceAtLeast(baseLineHeightPx * 0.5f)
                ReaderRenderItem.Text(
                    line = item.line,
                    text = item.line.textFrom(sourceText),
                    heightPx = lineHeightPx,
                    heightDp = with(density) { lineHeightPx.toDp() },
                )
            }
            is ReaderPageItem.ImageBlock -> {
                val heightPx = item.image.heightPx.coerceAtLeast(baseLineHeightPx * 2f)
                ReaderRenderItem.Image(
                    image = item.image,
                    heightPx = heightPx,
                    heightDp = with(density) { heightPx.toDp() },
                )
            }
        }
    }
    val imageLoader = remember { EpubImageBytesLoader() }

    Layout(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        content = {
            renderItems.forEach { renderItem ->
                when (renderItem) {
                    is ReaderRenderItem.Text -> {
                        if (renderItem.text.isBlank()) {
                            Box(modifier = Modifier.fillMaxWidth().height(renderItem.heightDp))
                        } else {
                            ReaderTextLine(
                                text = renderItem.text,
                                shouldJustify = renderItem.line.shouldJustify,
                                isChapterTitle = renderItem.line.isChapterTitle,
                                firstLineIndentPx = renderItem.line.firstLineIndentPx,
                                extraTokenSpacingPx = renderItem.line.extraTokenSpacingPx,
                                textColor = textColor,
                                bodyFontSize = bodyFontSize,
                                bodyLineHeight = bodyLineHeight,
                                bodyFontWeight = bodyFontWeight,
                                bodyLetterSpacing = bodyLetterSpacing,
                                bodyTextAlign = bodyTextAlign,
                                lineHeight = renderItem.heightDp,
                            )
                        }
                    }
                    is ReaderRenderItem.Image -> ReaderImageBlock(
                        image = renderItem.image,
                        imageLoader = imageLoader,
                        height = renderItem.heightDp,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val itemHeightsPx = renderItems.map { item ->
            item.heightPx.roundToInt().coerceAtLeast(1)
        }
        val totalItemHeightPx = itemHeightsPx.sum()
        val rawExtraGapPx = if (itemHeightsPx.size > 1) {
            (constraints.maxHeight - totalItemHeightPx).toFloat() / (itemHeightsPx.size - 1)
        } else {
            0f
        }
        val extraLineGapPx = rawExtraGapPx
            .takeIf { it > 0f && it <= baseLineHeightPx * 0.75f }
            ?: 0f
        val placeables = measurables.mapIndexed { index, measurable ->
            val itemHeightPx = itemHeightsPx.getOrElse(index) { baseLineHeightPx.roundToInt().coerceAtLeast(1) }
            measurable.measure(
                constraints.copy(
                    minWidth = constraints.maxWidth,
                    maxWidth = constraints.maxWidth,
                    minHeight = itemHeightPx,
                    maxHeight = itemHeightPx,
                ),
            )
        }

        layout(width = constraints.maxWidth, height = constraints.maxHeight) {
            var y = 0f
            placeables.forEach { placeable ->
                placeable.placeRelative(0, y.roundToInt())
                y += placeable.height + extraLineGapPx
            }
        }
    }
}

private sealed class ReaderRenderItem {
    abstract val heightPx: Float
    abstract val heightDp: androidx.compose.ui.unit.Dp

    data class Text(
        val line: ReaderTextLineInfo,
        val text: String,
        override val heightPx: Float,
        override val heightDp: androidx.compose.ui.unit.Dp,
    ) : ReaderRenderItem()

    data class Image(
        val image: ReaderImageBlockInfo,
        override val heightPx: Float,
        override val heightDp: androidx.compose.ui.unit.Dp,
    ) : ReaderRenderItem()
}

@Composable
private fun ReaderImageBlock(
    image: ReaderImageBlockInfo,
    imageLoader: EpubImageBytesLoader,
    height: androidx.compose.ui.unit.Dp,
) {
    var bitmap by remember(image.resource) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(image.resource) { mutableStateOf(false) }

    LaunchedEffect(image.resource) {
        failed = false
        bitmap = withContext(Dispatchers.Default) {
            runCatching {
                imageLoader.load(image.resource)?.decodeToImageBitmap()
            }.getOrNull()
        }
        failed = bitmap == null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap,
                contentDescription = image.resource.alt,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (failed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = image.resource.alt?.takeIf { it.isNotBlank() } ?: ToreadStrings.ImageUnavailable,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ReaderTextLine(
    text: String,
    shouldJustify: Boolean,
    isChapterTitle: Boolean,
    firstLineIndentPx: Float,
    extraTokenSpacingPx: Float,
    textColor: Color,
    bodyFontSize: TextUnit,
    bodyLineHeight: TextUnit,
    bodyFontWeight: FontWeight,
    bodyLetterSpacing: TextUnit,
    bodyTextAlign: TextAlign,
    lineHeight: androidx.compose.ui.unit.Dp,
) {
    val density = LocalDensity.current
    val indent = with(density) { firstLineIndentPx.toDp() }
    val justifyTokens = remember(text, shouldJustify, isChapterTitle) {
        if (shouldJustify && !isChapterTitle) text.toReaderDrawTokens() else emptyList()
    }
    val canJustify = !isChapterTitle && shouldJustify && justifyTokens.size > 1
    val displayFontSize = if (isChapterTitle) bodyFontSize * 1.35f else bodyFontSize
    val displayLineHeight = if (isChapterTitle) bodyLineHeight * 1.35f else bodyLineHeight
    val displayFontWeight = if (isChapterTitle) FontWeight.Bold else bodyFontWeight
    if (canJustify) {
        ReaderJustifiedLine(
            tokens = justifyTokens,
            indentPx = firstLineIndentPx,
            estimatedTokenSpacingPx = extraTokenSpacingPx,
            textColor = textColor,
            bodyFontSize = bodyFontSize,
            bodyLineHeight = bodyLineHeight,
            bodyFontWeight = bodyFontWeight,
            bodyLetterSpacing = bodyLetterSpacing,
            lineHeight = lineHeight,
        )
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().height(lineHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = displayFontSize,
                lineHeight = displayLineHeight,
                fontWeight = displayFontWeight,
                letterSpacing = bodyLetterSpacing,
                textAlign = bodyTextAlign,
                modifier = Modifier.padding(start = indent),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun ReaderJustifiedLine(
    tokens: List<String>,
    indentPx: Float,
    estimatedTokenSpacingPx: Float,
    textColor: Color,
    bodyFontSize: TextUnit,
    bodyLineHeight: TextUnit,
    bodyFontWeight: FontWeight,
    bodyLetterSpacing: TextUnit,
    lineHeight: androidx.compose.ui.unit.Dp,
) {
    Layout(
        modifier = Modifier.fillMaxWidth().height(lineHeight),
        content = {
            tokens.forEach { token ->
                Text(
                    text = token,
                    color = textColor,
                    fontSize = bodyFontSize,
                    lineHeight = bodyLineHeight,
                    fontWeight = bodyFontWeight,
                    letterSpacing = bodyLetterSpacing,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val contentWidth = placeables.sumOf { it.width }
        val height = constraints.maxHeight
        val indent = indentPx.roundToInt()
        val measuredSpacing = if (placeables.size > 1 && constraints.maxWidth > 0) {
            (constraints.maxWidth - indent - contentWidth).toFloat() / (placeables.size - 1)
        } else {
            estimatedTokenSpacingPx
        }
        layout(width = constraints.maxWidth, height = height) {
            var x = indent.toFloat()
            placeables.forEach { placeable ->
                val y = ((height - placeable.height) / 2).coerceAtLeast(0)
                placeable.placeRelative(x.roundToInt(), y)
                x += placeable.width + measuredSpacing
            }
        }
    }
}

@Composable
internal fun ReaderMessage(message: String, onBack: () -> Unit) {
    ScreenColumn {
        Text(ToreadStrings.Reader, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(message)
        OutlinedButton(onClick = onBack) {
            Text(ToreadStrings.BackToBookshelf)
        }
    }
}
