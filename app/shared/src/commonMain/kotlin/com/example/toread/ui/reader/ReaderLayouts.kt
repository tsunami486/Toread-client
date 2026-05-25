package com.example.toread.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.library.BookshelfBook
import com.example.toread.platform.rememberSystemBarInsets
import com.example.toread.ui.components.ScreenColumn
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
                Text("‹ ${book.title}", color = Color(0xFFE4EAEC))
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
                    .background(Color(0xFF171D1E))
                    .padding(horizontal = 34.dp, vertical = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                ReaderTextPage(
                    page = pages.getOrNull(pageIndex) ?: ReaderPageSlice(book.title, 0, 0),
                    sourceText = sourceText,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    textColor = Color(0xFFF3F7F8),
                    bodyFontSize = 22.sp,
                    bodyLineHeight = 34.sp,
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF2A3234)),
                )
                ReaderTextPage(
                    page = pages.getOrNull(pageIndex + 1) ?: ReaderPageSlice(book.title, 0, 0),
                    sourceText = sourceText,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    textColor = Color(0xFFF3F7F8),
                    bodyFontSize = 22.sp,
                    bodyLineHeight = 34.sp,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("第 ${pageIndex + 1}-${min(pageIndex + 2, pages.size)} 页", color = Color(0xFFB8C2C5))
            Text("${(progress * 100).roundToInt()}%", color = Color(0xFFB8C2C5))
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
    onToggleControls: () -> Unit,
) {
    val systemBarInsets = rememberSystemBarInsets()
    val topInset = systemBarInsets.top
    val bottomInset = systemBarInsets.bottom
    val readerTopPadding = if (controlsVisible) 24.dp + topInset else 24.dp
    val readerBottomPadding = if (controlsVisible) 24.dp + bottomInset else 24.dp
    val leftTapSource = remember { MutableInteractionSource() }
    val centerTapSource = remember { MutableInteractionSource() }
    val rightTapSource = remember { MutableInteractionSource() }

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
            bodyFontSize = 28.sp,
            bodyLineHeight = 46.sp,
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
                ReaderPanelButton(label = "目录", icon = "☰")
                ReaderPanelButton(label = "夜间", icon = "☾")
                ReaderPanelButton(label = "设置", icon = "⚙")
            }
        }
    }
}

@Composable
private fun ReaderPanelButton(label: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(icon, fontSize = 28.sp)
        Text(label, style = MaterialTheme.typography.labelMedium)
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
    val pageLines = page.lines.ifEmpty {
        listOf(ReaderPageLine(page.startOffset, page.endOffset, shouldJustify = false))
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val density = LocalDensity.current
        val lineHeightPx = with(density) { bodyLineHeight.toPx() }
        val availableHeightPx = with(density) { maxHeight.toPx() }
        val rawExtraGapPx = if (pageLines.size > 1) {
            (availableHeightPx - lineHeightPx * pageLines.size) / (pageLines.size - 1)
        } else {
            0f
        }
        val extraLineGapPx = rawExtraGapPx
            .takeIf { it > 0f && it <= lineHeightPx * 0.75f }
            ?: 0f
        val lineHeightDp = with(density) { lineHeightPx.toDp() }
        val extraLineGapDp = with(density) { extraLineGapPx.toDp() }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(extraLineGapDp),
        ) {
            pageLines.forEach { line ->
                val lineText = line.textFrom(sourceText)
                if (lineText.isBlank()) {
                    Box(modifier = Modifier.fillMaxWidth().height(lineHeightDp))
                } else {
                    ReaderTextLine(
                        text = lineText,
                        shouldJustify = line.shouldJustify,
                        textColor = textColor,
                        bodyFontSize = bodyFontSize,
                        bodyLineHeight = bodyLineHeight,
                        lineHeight = lineHeightDp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderTextLine(
    text: String,
    shouldJustify: Boolean,
    textColor: Color,
    bodyFontSize: TextUnit,
    bodyLineHeight: TextUnit,
    lineHeight: androidx.compose.ui.unit.Dp,
) {
    val canJustify = shouldJustify && text.length > 1
    if (canJustify) {
        Row(
            modifier = Modifier.fillMaxWidth().height(lineHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            text.forEach { char ->
                Text(
                    text = char.toString(),
                    color = textColor,
                    fontSize = bodyFontSize,
                    lineHeight = bodyLineHeight,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().height(lineHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = bodyFontSize,
                lineHeight = bodyLineHeight,
                letterSpacing = 0.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
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
