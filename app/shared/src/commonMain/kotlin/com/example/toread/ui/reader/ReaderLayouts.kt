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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.library.BookshelfBook
import com.example.toread.reader.EpubImageBytesLoader
import com.example.toread.platform.rememberSystemBarInsets
import com.example.toread.ui.components.ScreenColumn
import com.example.toread.ui.theme.toreadColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.min
import kotlin.math.roundToInt
@Composable
internal fun DesktopReaderLayout(
    book: BookshelfBook,
    sourceText: String,
    pages: List<ReaderPageSlice>,
    pageIndex: Int,
    progress: Double,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 72.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("‹ ${book.title}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onPrevious, enabled = pageIndex > 0) {
                    Text("上一页")
                }
                FilledTonalButton(onClick = onNext, enabled = pageIndex + 2 <= pages.lastIndex) {
                    Text("下一页")
                }
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .aspectRatio(DesktopSpreadAspectRatio)
                .align(Alignment.CenterHorizontally),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.toreadColors.readerPage)
                    .padding(horizontal = 34.dp, vertical = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                ReaderTextPage(
                    page = pages.getOrNull(pageIndex) ?: ReaderPageSlice(book.title, 0, 0),
                    sourceText = sourceText,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    textColor = MaterialTheme.colorScheme.onSurface,
                    bodyFontSize = 22.sp,
                    bodyLineHeight = 34.sp,
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.toreadColors.readerDivider),
                )
                ReaderTextPage(
                    page = pages.getOrNull(pageIndex + 1) ?: ReaderPageSlice(book.title, 0, 0),
                    sourceText = sourceText,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    textColor = MaterialTheme.colorScheme.onSurface,
                    bodyFontSize = 22.sp,
                    bodyLineHeight = 34.sp,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("第 ${pageIndex + 1}-${min(pageIndex + 2, pages.size)} 页", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(progress * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
) {
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
            bodyFontSize = 20.sp,
            bodyLineHeight = 25.sp,
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
            Text("‹")
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
                    Text("上一页")
                }
                Text("${(progress * 100).roundToInt()}%")
                TextButton(onClick = onNext, enabled = canNext) {
                    Text("下一页")
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
                ReaderPanelButton(label = "目录", icon = "☰", onClick = onOpenChapterList)
                ReaderPanelButton(label = "夜间", icon = "☾")
                ReaderPanelButton(label = "设置", icon = "⚙")
            }
        }
    }
}

@Composable
private fun ReaderPanelButton(label: String, icon: String, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(icon, fontSize = 28.sp)
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
                text = "已读 ${chapters.count { it.chapterOrd < currentChapterOrd }} / 共 ${chapters.size} 章",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 14.dp),
            )
            if (chapters.isEmpty()) {
                Text(
                    text = "尚未识别到章节",
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
                    text = image.resource.alt?.takeIf { it.isNotBlank() } ?: "图片无法显示",
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
    lineHeight: androidx.compose.ui.unit.Dp,
) {
    val density = LocalDensity.current
    val indent = with(density) { firstLineIndentPx.toDp() }
    val justifyTokens = tokens.ifEmpty { text.map { it.toString() } }
    val canJustify = !isChapterTitle && shouldJustify && justifyTokens.size > 1
    val displayFontSize = if (isChapterTitle) bodyFontSize * 1.35f else bodyFontSize
    val displayLineHeight = if (isChapterTitle) bodyLineHeight * 1.35f else bodyLineHeight
    val displayFontWeight = if (isChapterTitle) FontWeight.Bold else FontWeight.Normal
    if (canJustify) {
        ReaderJustifiedLine(
            tokens = justifyTokens,
            indentPx = firstLineIndentPx,
            estimatedTokenSpacingPx = extraTokenSpacingPx,
            textColor = textColor,
            bodyFontSize = bodyFontSize,
            bodyLineHeight = bodyLineHeight,
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
                letterSpacing = 0.sp,
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
                    letterSpacing = 0.sp,
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
        Text("阅读", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(message)
        OutlinedButton(onClick = onBack) {
            Text("返回书架")
        }
    }
}
