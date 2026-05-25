package com.example.toread.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.reader.ReaderDocument
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

private val ChapterTitleRegex = Regex(
    pattern = """^(第[0-9一二三四五六七八九十百千万零〇两]+[章节回卷部篇].*|Chapter\s+\d+.*|序章|序言|楔子|引子|尾声|后记).*$""",
    option = RegexOption.IGNORE_CASE,
)

internal data class ReaderPageSlice(
    val chapterTitle: String,
    val startOffset: Int,
    val endOffset: Int,
    val lines: List<ReaderPageLine> = emptyList(),
)

internal data class ReaderPageLine(
    val startOffset: Int,
    val endOffset: Int,
    val shouldJustify: Boolean,
)

internal fun ReaderPageLine.textFrom(sourceText: String): String {
    if (sourceText.isEmpty()) return ""
    val start = startOffset.coerceIn(0, sourceText.length)
    val end = endOffset.coerceIn(start, sourceText.length)
    return sourceText.substring(start, end)
}

internal fun ReaderPageSlice.textFrom(sourceText: String): String {
    if (sourceText.isEmpty()) return ""
    val start = startOffset.coerceIn(0, sourceText.length)
    val end = endOffset.coerceIn(start, sourceText.length)
    return sourceText.substring(start, end).trim()
}

internal data class ReaderPaginationSpec(
    val fallbackTitle: String,
    val bodyWidthPx: Int,
    val bodyHeightPx: Int,
    val fontPx: Float,
    val lineHeightPx: Float,
    val linesPerPage: Int,
)

@Composable
internal fun rememberReaderPaginationSpec(
    title: String,
    isAndroid: Boolean,
    isDesktop: Boolean,
    maxWidthPx: Int,
    maxHeightPx: Int,
): ReaderPaginationSpec {
    val density = LocalDensity.current
    return remember(title, isAndroid, isDesktop, maxWidthPx, maxHeightPx, density) {
        val fallbackTitle = title.ifBlank { "正文" }
        val bodyFontSize = if (isAndroid) 28.sp else 22.sp
        val bodyLineHeight = if (isAndroid) 46.sp else 34.sp
        val bodyWidthPx: Int
        val bodyHeightPx: Int
        if (isDesktop) {
            val verticalChromePx = with(density) { (24.dp * 2 + 46.dp + 28.dp).toPx().roundToInt() }
            val pageOuterHeightPx = (maxHeightPx - verticalChromePx)
                .coerceAtLeast(with(density) { 420.dp.toPx().roundToInt() })
            val spreadOuterWidthPx = (pageOuterHeightPx * DesktopSpreadAspectRatio).roundToInt()
            bodyWidthPx = ((spreadOuterWidthPx - with(density) { (34.dp * 2 + 30.dp + 1.dp).toPx().roundToInt() }) / 2)
                .coerceAtLeast(240)
            bodyHeightPx = (pageOuterHeightPx - with(density) { 60.dp.toPx().roundToInt() })
                .coerceAtLeast(260)
        } else {
            bodyWidthPx = (maxWidthPx - with(density) { 48.dp.toPx().roundToInt() }).coerceAtLeast(220)
            bodyHeightPx = (maxHeightPx - with(density) { 48.dp.toPx().roundToInt() })
                .coerceAtLeast(260)
        }
        val fontPx = with(density) { bodyFontSize.toPx() }.coerceAtLeast(1f)
        val lineHeightPx = with(density) { bodyLineHeight.toPx() }.coerceAtLeast(fontPx)
        val lineSafetyMargin = if (isDesktop) 2 else 0
        val linesPerPage = ((bodyHeightPx / lineHeightPx).toInt() - lineSafetyMargin).coerceAtLeast(1)

        ReaderPaginationSpec(
            fallbackTitle = fallbackTitle,
            bodyWidthPx = bodyWidthPx,
            bodyHeightPx = bodyHeightPx,
            fontPx = fontPx,
            lineHeightPx = lineHeightPx,
            linesPerPage = linesPerPage,
        )
    }
}

internal suspend fun ReaderDocument.paginateForReader(
    spec: ReaderPaginationSpec,
    onPagesChanged: (List<ReaderPageSlice>) -> Unit = {},
): List<ReaderPageSlice> {
    val fallbackTitle = title.ifBlank { spec.fallbackTitle }
    if (!isReadable) return listOf(ReaderPageSlice(fallbackTitle, 0, 0))

    val pages = mutableListOf<ReaderPageSlice>()
    val pageLines = mutableListOf<ReaderLine>()
    val blocks = text.toChapterBlocks(spec.fallbackTitle)
    suspend fun flushPage(force: Boolean = false) {
        if (pageLines.isEmpty() && !force) return
        val firstLine = pageLines.firstOrNull()
        val lastLine = pageLines.lastOrNull()
        pages += ReaderPageSlice(
            chapterTitle = firstLine?.chapterTitle ?: spec.fallbackTitle,
            startOffset = firstLine?.absoluteStart ?: 0,
            endOffset = lastLine?.absoluteEnd ?: firstLine?.absoluteStart ?: 0,
            lines = pageLines.map { line ->
                ReaderPageLine(
                    startOffset = line.absoluteStart,
                    endOffset = line.absoluteEnd,
                    shouldJustify = line.shouldJustify,
                )
            },
        )
        pageLines.clear()
        onPagesChanged(pages.toList())
        if (pages.size % 16 == 0) {
            yield()
        }
    }

    suspend fun appendLine(line: ReaderLine) {
        if (pageLines.size >= spec.linesPerPage) {
            flushPage()
        }
        pageLines += line
    }

    blocks.forEachIndexed { blockIndex, block ->
        block.forEachReaderLine(spec) { line ->
            appendLine(line)
        }
        if (blockIndex < blocks.lastIndex && pageLines.isNotEmpty()) {
            appendLine(
                ReaderLine(
                    chapterTitle = block.title,
                    absoluteStart = block.absoluteStart + block.body.length,
                    absoluteEnd = block.absoluteStart + block.body.length,
                    shouldJustify = false,
                ),
            )
        }
    }
    flushPage()
    return pages.ifEmpty { listOf(ReaderPageSlice(fallbackTitle, 0, 0)) }
}

private data class ReaderLine(
    val chapterTitle: String,
    val absoluteStart: Int,
    val absoluteEnd: Int,
    val shouldJustify: Boolean,
)

private suspend fun ChapterBlock.forEachReaderLine(
    spec: ReaderPaginationSpec,
    emit: suspend (ReaderLine) -> Unit,
) {
    var paragraphStart = 0
    while (paragraphStart <= body.length) {
        val nextBreak = body.indexOf('\n', paragraphStart)
        val paragraphEnd = if (nextBreak < 0) body.length else nextBreak
        if (paragraphEnd == paragraphStart) {
            emit(
                ReaderLine(
                    chapterTitle = title,
                    absoluteStart = absoluteStart + paragraphStart,
                    absoluteEnd = (absoluteStart + paragraphEnd + 1).coerceAtMost(absoluteStart + body.length),
                    shouldJustify = false,
                ),
            )
        } else if (!body.isBlankRange(paragraphStart, paragraphEnd)) {
            body.forEachReaderLine(
                chapterTitle = title,
                absoluteStart = absoluteStart,
                start = paragraphStart,
                end = paragraphEnd,
                spec = spec,
                emit = emit,
            )
        }
        if (nextBreak < 0) break
        paragraphStart = paragraphEnd + 1
    }
}

private suspend fun String.forEachReaderLine(
    chapterTitle: String,
    absoluteStart: Int,
    start: Int,
    end: Int,
    spec: ReaderPaginationSpec,
    emit: suspend (ReaderLine) -> Unit,
) {
    var lineStart = start
    var index = start
    var width = 0f

    suspend fun emitLine(endExclusive: Int) {
        val trimmedEnd = trimEndIndex(lineStart, endExclusive)
        if (trimmedEnd > lineStart) {
            emit(
                ReaderLine(
                    chapterTitle = chapterTitle,
                    absoluteStart = absoluteStart + lineStart,
                    absoluteEnd = absoluteStart + trimmedEnd,
                    shouldJustify = trimmedEnd < end,
                ),
            )
        }
        lineStart = endExclusive.skipLeadingWhitespace(this, end)
        index = lineStart
        width = 0f
    }

    while (index < end) {
        val char = this[index]
        val charWidth = spec.readerCharWidth(char)
        val wouldOverflow = width > 0f && width + charWidth > spec.bodyWidthPx
        if (wouldOverflow) {
            var breakAt = index
            if (isReaderLeadingPunctuation(char) && index > lineStart) {
                breakAt = index + 1
            } else {
                val preferred = findPreferredLineBreak(lineStart, index)
                if (preferred > lineStart) {
                    breakAt = preferred
                }
            }
            emitLine(breakAt.coerceAtMost(end))
        } else {
            width += charWidth
            index++
        }
    }

    if (lineStart < end) {
        emitLine(end)
    }
}

private fun String.isBlankRange(start: Int, end: Int): Boolean {
    for (index in start until end) {
        if (!this[index].isWhitespace()) return false
    }
    return true
}

private fun String.trimEndIndex(start: Int, end: Int): Int {
    var index = end
    while (index > start && this[index - 1].isWhitespace()) {
        index--
    }
    return index
}

private fun String.findPreferredLineBreak(start: Int, endExclusive: Int): Int {
    val searchStart = (endExclusive - 14).coerceAtLeast(start + 1)
    for (index in endExclusive downTo searchStart) {
        val char = this[index - 1]
        if (char.isWhitespace() || char in "，。！？；、,.!?;:") return index
    }
    return endExclusive
}

private fun ReaderPaginationSpec.readerCharWidth(char: Char): Float =
    when {
        char == '\t' -> fontPx * 2f
        char == ' ' -> fontPx * 0.45f
        char.code <= 0x007F -> fontPx * 0.58f
        char.isHalfWidthPunctuation() -> fontPx * 0.56f
        char.isCjkPunctuation() -> fontPx * 0.82f
        else -> fontPx
    }

private fun Char.isHalfWidthPunctuation(): Boolean =
    this in "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

private fun Char.isCjkPunctuation(): Boolean =
    this in "，。！？；：“”‘’（）《》〈〉【】「」『』、…—"

private fun isReaderLeadingPunctuation(char: Char): Boolean =
    char in "，。！？；：、,.!?;:)]}）】》〉」』"

private fun Int.skipLeadingWhitespace(source: String, limit: Int = source.length): Int {
    var index = this
    while (index < limit && source[index].isWhitespace()) {
        index++
    }
    return index
}

private data class ChapterBlock(
    val title: String,
    val body: String,
    val absoluteStart: Int,
)

private fun String.toChapterBlocks(fallbackTitle: String): List<ChapterBlock> {
    val normalized = replace("\r\n", "\n").replace('\r', '\n')
    val blocks = mutableListOf<ChapterBlock>()
    var chapterTitle = fallbackTitle
    val buffer = StringBuilder()
    var blockStart = 0
    var absoluteIndex = 0

    fun flush() {
        val body = buffer.toString().trim()
        if (body.isNotEmpty()) {
            blocks += ChapterBlock(chapterTitle, body, blockStart)
        }
        buffer.clear()
    }

    normalized.lineSequence().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val lineWithBreak = "$line\n"
        val trimmed = line.trim()
        if (trimmed.isLikelyChapterTitle()) {
            flush()
            chapterTitle = trimmed
            blockStart = absoluteIndex + lineWithBreak.length
        } else {
            if (buffer.isEmpty()) blockStart = absoluteIndex
            buffer.append(lineWithBreak)
        }
        absoluteIndex += lineWithBreak.length
    }

    flush()
    return blocks.ifEmpty { listOf(ChapterBlock(fallbackTitle, normalized, 0)) }
}

private fun String.isLikelyChapterTitle(): Boolean =
    length in 2..42 && ChapterTitleRegex.matches(this)
