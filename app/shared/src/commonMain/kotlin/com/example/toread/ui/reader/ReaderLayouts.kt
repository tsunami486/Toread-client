package com.example.toread.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import com.example.toread.ui.text.ToreadStrings
import com.example.toread.ui.theme.toreadColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
) {
    val palette = if (isNightMode) DesktopNightReaderPalette else DesktopLightReaderPalette
    val chapterEntries = remember(pages) { pages.readerChapterEntries() }
    val currentChapterOrd = pages.getOrNull(pageIndex)?.chapterOrd
        ?: chapterEntries.lastOrNull { it.pageIndex <= pageIndex }?.chapterOrd
        ?: 0
    var chapterDrawerVisible by remember { mutableStateOf(false) }

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
                chapters = chapterEntries,
                currentChapterOrd = currentChapterOrd,
                palette = palette,
                onDismiss = { chapterDrawerVisible = false },
                onSelectChapter = { chapter ->
                    chapterDrawerVisible = false
                    onGoToPage(chapter.pageIndex)
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = ToreadStrings.BackToBookshelf,
                tint = palette.toolbarText,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = bookTitle,
                color = palette.toolbarText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopReaderToolbarButton(
                label = ToreadStrings.Catalog,
                imageVector = Icons.Default.Menu,
                palette = palette,
                onClick = onOpenChapterDrawer,
            )
            DesktopReaderToolbarButton(
                label = if (isDoublePage) {
                    ToreadStrings.SwitchSinglePageReading
                } else {
                    ToreadStrings.SwitchDoublePageReading
                },
                imageVector = if (isDoublePage) Icons.AutoMirrored.Outlined.Article else Icons.Outlined.ViewColumn,
                palette = palette,
                onClick = onTogglePageMode,
            )
            DesktopReaderToolbarButton(
                label = if (isNightMode) ToreadStrings.SwitchLightMode else ToreadStrings.SwitchDarkMode,
                imageVector = if (isNightMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                palette = palette,
                onClick = onToggleNightMode,
            )
            DesktopReaderToolbarButton(label = ToreadStrings.Font, imageVector = Icons.Outlined.TextFields, palette = palette)
            DesktopReaderToolbarButton(label = ToreadStrings.DecreaseFontSize, imageVector = Icons.Default.Remove, palette = palette)
            DesktopReaderToolbarButton(label = ToreadStrings.IncreaseFontSize, imageVector = Icons.Default.Add, palette = palette)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopReaderToolbarButton(
    label: String,
    imageVector: ImageVector,
    palette: DesktopReaderPalette,
    onClick: () -> Unit = {},
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
                .background(palette.controlBackground, RoundedCornerShape(50)),
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = label,
                tint = palette.toolbarText,
                modifier = Modifier.size(18.dp),
            )
        }
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
                canPrevious = pageIndex > 0,
                canNext = if (isDoublePage) pageIndex + 2 <= pages.lastIndex else pageIndex < pages.lastIndex,
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
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
    onDismiss: () -> Unit,
    onSelectChapter: (ReaderChapterEntry) -> Unit,
) {
    val chapterListState = rememberLazyListState()
    val currentChapterIndex = chapters.indexOfFirst { it.chapterOrd == currentChapterOrd }

    LaunchedEffect(currentChapterIndex, chapters.size) {
        if (currentChapterIndex >= 0) {
            chapterListState.animateScrollToItem((currentChapterIndex - 4).coerceAtLeast(0))
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
                .padding(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 22.dp),
        ) {
            Text(
                text = ToreadStrings.Catalog,
                color = palette.drawerText,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = bookTitle,
                color = palette.mutedText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
            )
            Text(
                text = ToreadStrings.readChapterSummary(
                    read = chapters.count { it.chapterOrd < currentChapterOrd },
                    total = chapters.size,
                ),
                color = palette.mutedText,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            HorizontalDivider(color = palette.divider)
            if (chapters.isEmpty()) {
                Text(
                    text = ToreadStrings.EmptyChapterList,
                    color = palette.mutedText,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(
                    state = chapterListState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp),
                ) {
                    items(chapters, key = { it.chapterOrd }) { chapter ->
                        DesktopChapterRow(
                            chapter = chapter,
                            currentChapterOrd = currentChapterOrd,
                            palette = palette,
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
        Text(
            text = ToreadStrings.chapterPage(chapter.pageIndex),
            color = palette.mutedText,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
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
) {
    val typography = AndroidReaderTypography
    val systemBarInsets = rememberSystemBarInsets()
    val topInset = systemBarInsets.top
    val bottomInset = systemBarInsets.bottom
    val readerTopPadding = 10.dp + topInset
    val readerBottomPadding = 10.dp + bottomInset
    val leftTapSource = remember { MutableInteractionSource() }
    val centerTapSource = remember { MutableInteractionSource() }
    val rightTapSource = remember { MutableInteractionSource() }
    val chapterEntries = remember(pages) { pages.readerChapterEntries() }
    val currentChapterOrd = pages.getOrNull(pageIndex)?.chapterOrd
        ?: chapterEntries.lastOrNull { it.pageIndex <= pageIndex }?.chapterOrd
        ?: 0
    var chapterDrawerVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        ReaderTextPage(
            page = pages.getOrNull(pageIndex) ?: ReaderPageSlice(book.title, 0, 0),
            sourceText = sourceText,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    top = readerTopPadding,
                    end = 24.dp,
                    bottom = readerBottomPadding,
            ),
            textColor = MaterialTheme.colorScheme.onBackground,
            bodyFontSize = typography.bodyFontSize,
            bodyLineHeight = typography.bodyLineHeight,
            bodyLetterSpacing = typography.bodyLetterSpacing,
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = leftTapSource,
                        indication = null,
                        onClick = onPrevious,
                    ),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = centerTapSource,
                        indication = null,
                        onClick = onToggleControls,
                    ),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = rightTapSource,
                        indication = null,
                        onClick = onNext,
                    ),
            )
        }

        if (controlsVisible) {
            AndroidReaderTopBar(book = book, onBack = onBack)
            AndroidReaderBottomPanel(
                progress = progress,
                canPrevious = pageIndex > 0,
                canNext = pageIndex < pages.lastIndex,
                onPrevious = onPrevious,
                onNext = onNext,
                onOpenChapterList = { chapterDrawerVisible = true },
            )
        }
        if (chapterDrawerVisible) {
            AndroidChapterDrawer(
                bookTitle = book.title,
                chapters = chapterEntries,
                currentChapterOrd = currentChapterOrd,
                onDismiss = { chapterDrawerVisible = false },
                onSelectChapter = { chapter ->
                    chapterDrawerVisible = false
                    onGoToPage(chapter.pageIndex)
                },
            )
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = ToreadStrings.BackToBookshelf,
            )
        }
        Text(
            book.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun BoxScope.AndroidReaderBottomPanel(
    progress: Double,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenChapterList: () -> Unit,
) {
    val bottomInset = rememberSystemBarInsets().bottom

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = bottomInset + 12.dp)
            .align(Alignment.BottomCenter),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPrevious, enabled = canPrevious) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    Text(ToreadStrings.PreviousPage)
                }
                Text(ToreadStrings.readerPercent(progress))
                TextButton(onClick = onNext, enabled = canNext) {
                    Text(ToreadStrings.NextPage)
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
            LinearProgressIndicator(
                progress = { progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderPanelButton(label = ToreadStrings.Catalog, imageVector = Icons.Default.Menu, onClick = onOpenChapterList)
                ReaderPanelButton(label = ToreadStrings.NightMode, imageVector = Icons.Outlined.DarkMode)
                ReaderPanelButton(label = ToreadStrings.ReaderSettings, imageVector = Icons.Outlined.Settings)
            }
        }
    }
}

@Composable
private fun ReaderPanelButton(label: String, imageVector: ImageVector, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = imageVector,
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.toreadColors.readerScrim)
            .clickable(onClick = onDismiss),
    )
    Card(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.74f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = bottomInset + 12.dp),
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
                modifier = Modifier.padding(top = 26.dp),
            )
            Text(
                text = ToreadStrings.readChapterSummary(
                    read = chapters.count { it.chapterOrd < currentChapterOrd },
                    total = chapters.size,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 14.dp),
            )
            if (chapters.isEmpty()) {
                Text(
                    text = ToreadStrings.EmptyChapterList,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
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
                                tokens = renderItem.line.tokens,
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
    tokens: List<String>,
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
    val justifyTokens = tokens.ifEmpty { text.map { it.toString() } }
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
