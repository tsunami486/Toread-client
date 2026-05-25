package com.example.toread.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.reader.ReaderDocument
import kotlinx.coroutines.yield
import kotlin.math.max
import kotlin.math.roundToInt

private const val PreferredBreakSearchWindow = 14
private const val MinimumJustifyTokenCount = 6

private val ChapterTitleRegex = Regex(
    pattern = """^\s*((第\s*[0-9一二三四五六七八九十百千万零〇两]+\s*[章节回卷部篇集].*)|([卷部篇集]\s*[0-9一二三四五六七八九十百千万零〇两]+.*)|((chapter|section|part|volume)\s+[0-9a-zivxlcdm]+.*)|(序章|序言|前言|楔子|引子|尾声|后记|番外).*)\s*$""",
    option = RegexOption.IGNORE_CASE,
)

internal data class ReaderPageSlice(
    val chapterTitle: String,
    val startOffset: Int,
    val endOffset: Int,
    val chapterOrd: Int = 0,
    val startLocator: ReaderTextLocator = ReaderTextLocator(chapterOrd = chapterOrd, absoluteOffset = startOffset),
    val endLocator: ReaderTextLocator = ReaderTextLocator(chapterOrd = chapterOrd, absoluteOffset = endOffset),
    val lines: List<ReaderTextLineInfo> = emptyList(),
)

internal data class ReaderTextLocator(
    val chapterOrd: Int = 0,
    val paragraphIndex: Int = 0,
    val elementIndex: Int = 0,
    val charIndex: Int = 0,
    val absoluteOffset: Int = 0,
)

internal data class ReaderTextLineInfo(
    val startOffset: Int,
    val endOffset: Int,
    val text: String = "",
    val tokens: List<String> = emptyList(),
    val chapterOrd: Int = 0,
    val chapterTitle: String = "",
    val paragraphIndex: Int = 0,
    val startLocator: ReaderTextLocator = ReaderTextLocator(chapterOrd = chapterOrd, paragraphIndex = paragraphIndex, absoluteOffset = startOffset),
    val endLocator: ReaderTextLocator = ReaderTextLocator(chapterOrd = chapterOrd, paragraphIndex = paragraphIndex, absoluteOffset = endOffset),
    val widthPx: Float = 0f,
    val availableWidthPx: Float = 1f,
    val contentHeightPx: Float = 0f,
    val lineHeightPx: Float = 0f,
    val firstLineIndentPx: Float = 0f,
    val fillRatio: Float = 0f,
    val shouldJustify: Boolean = false,
    val isParagraphFirst: Boolean = false,
    val isParagraphLast: Boolean = false,
    val isBlank: Boolean = false,
)

internal typealias ReaderPageLine = ReaderTextLineInfo

internal fun ReaderTextLineInfo.textFrom(sourceText: String): String {
    if (text.isNotEmpty()) return text
    if (sourceText.isEmpty()) return ""
    val start = startOffset.coerceIn(0, sourceText.length)
    val end = endOffset.coerceIn(start, sourceText.length)
    return sourceText.substring(start, end)
}

internal fun ReaderPageSlice.textFrom(sourceText: String): String {
    if (lines.isNotEmpty()) return lines.joinToString("") { it.text }.trim()
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
    val justifyMinFillRatio: Float = 0.88f,
    val paragraphFirstLineIndentPx: Float = fontPx * 2f,
    val minimumLineHeightPx: Float = lineHeightPx,
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

    val textDocument = text.toReaderTextDocument(fallbackTitle)
    val measurePool = ReaderCharacterMeasurePool(spec)
    val pages = mutableListOf<ReaderPageSlice>()
    val pageLines = mutableListOf<ReaderTextLineInfo>()
    var pageHeightPx = 0f

    suspend fun flushPage(force: Boolean = false) {
        if (pageLines.isEmpty() && !force) return
        val firstLine = pageLines.firstOrNull()
        val lastLine = pageLines.lastOrNull()
        pages += ReaderPageSlice(
            chapterTitle = firstLine?.chapterTitle ?: fallbackTitle,
            startOffset = firstLine?.startOffset ?: 0,
            endOffset = lastLine?.endOffset ?: firstLine?.startOffset ?: 0,
            chapterOrd = firstLine?.chapterOrd ?: 0,
            startLocator = firstLine?.startLocator ?: ReaderTextLocator(),
            endLocator = lastLine?.endLocator ?: firstLine?.startLocator ?: ReaderTextLocator(),
            lines = pageLines.toList(),
        )
        pageLines.clear()
        pageHeightPx = 0f
        onPagesChanged(pages.toList())
        if (pages.size % 16 == 0) {
            yield()
        }
    }

    suspend fun appendLine(line: ReaderTextLineInfo) {
        val lineHeight = line.lineHeightPx.coerceAtLeast(spec.minimumLineHeightPx * 0.5f)
        if (pageLines.isNotEmpty() && pageHeightPx + lineHeight > spec.bodyHeightPx) {
            flushPage()
        }
        pageLines += line
        pageHeightPx += lineHeight
    }

    textDocument.chapters.forEachIndexed { chapterIndex, chapter ->
        chapter.paragraphs.forEach { paragraph ->
            paragraph.toTextLines(spec, measurePool).forEach { line ->
                appendLine(line)
            }
        }
        if (chapterIndex < textDocument.chapters.lastIndex && pageLines.isNotEmpty()) {
            appendLine(chapter.trailingBlankLine(spec))
        }
    }

    flushPage()
    return pages.ifEmpty { listOf(ReaderPageSlice(fallbackTitle, 0, 0)) }
}

private fun ReaderParagraph.toTextLines(
    spec: ReaderPaginationSpec,
    measurePool: ReaderCharacterMeasurePool,
): List<ReaderTextLineInfo> {
    if (isBlank || text.isBlank()) {
        return listOf(blankLine(spec))
    }

    val glyphs = toGlyphs(measurePool)
    if (glyphs.isEmpty()) {
        return listOf(blankLine(spec))
    }

    val lines = mutableListOf<ReaderTextLineInfo>()
    var lineStart = glyphs.skipWhitespace(0)
    var index = lineStart
    var width = 0f
    var lineOrdinal = 0

    fun currentLineLimit(): Float =
        (spec.bodyWidthPx - if (lineOrdinal == 0) spec.paragraphFirstLineIndentPx else 0f)
            .coerceAtLeast(spec.bodyWidthPx * 0.5f)

    fun emitLine(endExclusive: Int) {
        val trimmedEnd = glyphs.trimTrailingWhitespace(lineStart, endExclusive)
        if (trimmedEnd > lineStart) {
            lines += createTextLineInfo(
                glyphs = glyphs.subList(lineStart, trimmedEnd),
                spec = spec,
                lineOrdinal = lineOrdinal,
                availableWidthPx = currentLineLimit(),
            )
            lineOrdinal++
        }
        lineStart = glyphs.skipWhitespace(endExclusive)
        index = lineStart
        width = 0f
    }

    while (index < glyphs.size) {
        val glyph = glyphs[index]
        val lineLimit = currentLineLimit()
        val wouldOverflow = width > 0f && width + glyph.metric.widthPx > lineLimit
        if (wouldOverflow) {
            var breakAt = index
            if (isReaderLeadingPunctuation(glyph.char) && index > lineStart) {
                breakAt = index + 1
            } else {
                val preferred = glyphs.findPreferredLineBreak(lineStart, index, lineLimit, spec)
                if (preferred > lineStart) {
                    breakAt = preferred
                }
            }
            emitLine(breakAt.coerceAtMost(glyphs.size))
        } else {
            width += glyph.metric.widthPx
            index++
        }
    }

    if (lineStart < glyphs.size) {
        emitLine(glyphs.size)
    }

    val lastIndex = lines.lastIndex
    return lines.mapIndexed { indexInParagraph, line ->
        val isFirst = indexInParagraph == 0
        val isLast = indexInParagraph == lastIndex
        line.copy(
            isParagraphFirst = isFirst,
            isParagraphLast = isLast,
            shouldJustify = !isFirst &&
                !isLast &&
                line.fillRatio >= spec.justifyMinFillRatio &&
                line.tokens.size >= MinimumJustifyTokenCount,
        )
    }
}

private fun ReaderParagraph.createTextLineInfo(
    glyphs: List<ReaderGlyph>,
    spec: ReaderPaginationSpec,
    lineOrdinal: Int,
    availableWidthPx: Float,
): ReaderTextLineInfo {
    val startGlyph = glyphs.first()
    val endGlyph = glyphs.last()
    val widthPx = glyphs.fold(0f) { sum, glyph -> sum + glyph.metric.widthPx }
    val contentHeightPx = glyphs.fold(0f) { height, glyph -> max(height, glyph.metric.heightPx) }
        .coerceAtLeast(spec.fontPx)
    val lineHeightPx = max(spec.minimumLineHeightPx, contentHeightPx + (spec.lineHeightPx - spec.fontPx).coerceAtLeast(0f))
    val text = glyphs.joinToString("") { it.char.toString() }

    return ReaderTextLineInfo(
        startOffset = startGlyph.absoluteStart,
        endOffset = endGlyph.absoluteEnd,
        text = text,
        tokens = glyphs.toDrawTokens(),
        chapterOrd = chapterOrd,
        chapterTitle = chapterTitle,
        paragraphIndex = paragraphIndex,
        startLocator = startGlyph.locator,
        endLocator = endGlyph.endLocator(),
        widthPx = widthPx,
        availableWidthPx = availableWidthPx,
        contentHeightPx = contentHeightPx,
        lineHeightPx = lineHeightPx,
        firstLineIndentPx = if (lineOrdinal == 0) spec.paragraphFirstLineIndentPx else 0f,
        fillRatio = (widthPx / availableWidthPx).coerceIn(0f, 1.25f),
    )
}

private fun ReaderParagraph.blankLine(spec: ReaderPaginationSpec): ReaderTextLineInfo {
    val locator = ReaderTextLocator(
        chapterOrd = chapterOrd,
        paragraphIndex = paragraphIndex,
        absoluteOffset = absoluteStart,
    )
    return ReaderTextLineInfo(
        startOffset = absoluteStart,
        endOffset = absoluteEnd,
        text = "",
        tokens = emptyList(),
        chapterOrd = chapterOrd,
        chapterTitle = chapterTitle,
        paragraphIndex = paragraphIndex,
        startLocator = locator,
        endLocator = locator.copy(absoluteOffset = absoluteEnd),
        contentHeightPx = 0f,
        lineHeightPx = spec.lineHeightPx * 0.65f,
        isParagraphFirst = true,
        isParagraphLast = true,
        isBlank = true,
    )
}

private fun ReaderChapter.trailingBlankLine(spec: ReaderPaginationSpec): ReaderTextLineInfo {
    val locator = ReaderTextLocator(
        chapterOrd = ord,
        paragraphIndex = paragraphs.lastOrNull()?.paragraphIndex ?: 0,
        absoluteOffset = absoluteEnd,
    )
    return ReaderTextLineInfo(
        startOffset = absoluteEnd,
        endOffset = absoluteEnd,
        chapterOrd = ord,
        chapterTitle = title,
        paragraphIndex = locator.paragraphIndex,
        startLocator = locator,
        endLocator = locator,
        lineHeightPx = spec.lineHeightPx * 0.65f,
        isParagraphFirst = true,
        isParagraphLast = true,
        isBlank = true,
    )
}

private fun ReaderParagraph.toGlyphs(measurePool: ReaderCharacterMeasurePool): List<ReaderGlyph> {
    val glyphs = mutableListOf<ReaderGlyph>()
    var index = 0
    var elementIndex = 0
    while (index < text.length) {
        val char = text[index]
        if (char.isLatinWordChar()) {
            val elementStart = index
            while (index < text.length && text[index].isLatinWordChar()) {
                val current = text[index]
                glyphs += ReaderGlyph(
                    char = current,
                    absoluteStart = absoluteStart + index,
                    absoluteEnd = absoluteStart + index + 1,
                    locator = ReaderTextLocator(
                        chapterOrd = chapterOrd,
                        paragraphIndex = paragraphIndex,
                        elementIndex = elementIndex,
                        charIndex = index - elementStart,
                        absoluteOffset = absoluteStart + index,
                    ),
                    metric = measurePool.measure(current),
                    elementKind = ReaderTextElementKind.LatinWord,
                )
                index++
            }
            elementIndex++
        } else {
            glyphs += ReaderGlyph(
                char = char,
                absoluteStart = absoluteStart + index,
                absoluteEnd = absoluteStart + index + 1,
                locator = ReaderTextLocator(
                    chapterOrd = chapterOrd,
                    paragraphIndex = paragraphIndex,
                    elementIndex = elementIndex,
                    charIndex = 0,
                    absoluteOffset = absoluteStart + index,
                ),
                metric = measurePool.measure(char),
                elementKind = char.toElementKind(),
            )
            elementIndex++
            index++
        }
    }
    return glyphs
}

private fun ReaderGlyph.endLocator(): ReaderTextLocator =
    locator.copy(
        charIndex = locator.charIndex + 1,
        absoluteOffset = absoluteEnd,
    )

private fun List<ReaderGlyph>.toDrawTokens(): List<String> {
    val tokens = mutableListOf<String>()
    val builder = StringBuilder()
    var currentElementIndex: Int? = null

    fun flushBuilder() {
        if (builder.isNotEmpty()) {
            tokens += builder.toString()
            builder.clear()
        }
        currentElementIndex = null
    }

    for (glyph in this) {
        if (glyph.char.isWhitespace()) {
            flushBuilder()
            continue
        }
        if (glyph.elementKind == ReaderTextElementKind.LatinWord) {
            if (currentElementIndex == glyph.locator.elementIndex) {
                builder.append(glyph.char)
            } else {
                flushBuilder()
                currentElementIndex = glyph.locator.elementIndex
                builder.append(glyph.char)
            }
        } else {
            flushBuilder()
            tokens += glyph.char.toString()
        }
    }
    flushBuilder()
    return tokens
}

private fun List<ReaderGlyph>.skipWhitespace(start: Int): Int {
    var index = start
    while (index < size && this[index].char.isWhitespace()) {
        index++
    }
    return index
}

private fun List<ReaderGlyph>.trimTrailingWhitespace(start: Int, endExclusive: Int): Int {
    var index = endExclusive
    while (index > start && this[index - 1].char.isWhitespace()) {
        index--
    }
    return index
}

private fun List<ReaderGlyph>.findPreferredLineBreak(
    start: Int,
    endExclusive: Int,
    lineLimitPx: Float,
    spec: ReaderPaginationSpec,
): Int {
    val searchStart = (endExclusive - PreferredBreakSearchWindow).coerceAtLeast(start + 1)
    for (index in endExclusive downTo searchStart) {
        val char = this[index - 1].char
        if (char.isWhitespace() || char.isReaderBreakPunctuation()) {
            val width = rangeWidth(start, index)
            if (width >= lineLimitPx * spec.justifyMinFillRatio) {
                return index
            }
        }
    }
    return -1
}

private fun List<ReaderGlyph>.rangeWidth(start: Int, endExclusive: Int): Float {
    var width = 0f
    for (index in start until endExclusive) {
        width += this[index].metric.widthPx
    }
    return width
}

private data class ReaderTextDocument(
    val chapters: List<ReaderChapter>,
)

private data class ReaderChapter(
    val ord: Int,
    val title: String,
    val titleStartOffset: Int,
    val titleEndOffset: Int,
    val absoluteEnd: Int,
    val paragraphs: List<ReaderParagraph>,
)

private data class ReaderParagraph(
    val chapterOrd: Int,
    val chapterTitle: String,
    val paragraphIndex: Int,
    val absoluteStart: Int,
    val absoluteEnd: Int,
    val text: String,
    val isBlank: Boolean,
)

private fun String.toReaderTextDocument(fallbackTitle: String): ReaderTextDocument {
    val normalized = replace("\n", "\n").replace('\r', '\n')
    val chapters = mutableListOf<ReaderChapter>()
    val rawLines = mutableListOf<RawTextLine>()
    var chapterTitle = fallbackTitle
    var chapterTitleStart = 0
    var chapterTitleEnd = 0

    fun flushChapter() {
        if (rawLines.isEmpty()) return
        val ord = chapters.size
        val paragraphs = rawLines.mapIndexed { index, line ->
            ReaderParagraph(
                chapterOrd = ord,
                chapterTitle = chapterTitle,
                paragraphIndex = index,
                absoluteStart = line.start,
                absoluteEnd = line.end,
                text = line.text,
                isBlank = line.text.isBlank(),
            )
        }
        val absoluteEnd = rawLines.lastOrNull()?.end ?: chapterTitleEnd
        chapters += ReaderChapter(
            ord = ord,
            title = chapterTitle,
            titleStartOffset = chapterTitleStart,
            titleEndOffset = chapterTitleEnd,
            absoluteEnd = absoluteEnd,
            paragraphs = paragraphs,
        )
        rawLines.clear()
    }

    var lineStart = 0
    while (lineStart <= normalized.length) {
        val breakIndex = normalized.indexOf('\n', lineStart)
        val lineEnd = if (breakIndex < 0) normalized.length else breakIndex
        val lineText = normalized.substring(lineStart, lineEnd).trimEnd()
        val trimmed = lineText.trim()
        if (trimmed.isLikelyChapterTitle()) {
            flushChapter()
            chapterTitle = trimmed
            chapterTitleStart = lineStart
            chapterTitleEnd = lineEnd
        } else {
            rawLines += RawTextLine(
                start = lineStart,
                end = lineEnd,
                text = lineText,
            )
        }
        if (breakIndex < 0) break
        lineStart = breakIndex + 1
    }
    flushChapter()

    return ReaderTextDocument(
        chapters = chapters.ifEmpty {
            listOf(
                ReaderChapter(
                    ord = 0,
                    title = fallbackTitle,
                    titleStartOffset = 0,
                    titleEndOffset = 0,
                    absoluteEnd = normalized.length,
                    paragraphs = listOf(
                        ReaderParagraph(
                            chapterOrd = 0,
                            chapterTitle = fallbackTitle,
                            paragraphIndex = 0,
                            absoluteStart = 0,
                            absoluteEnd = normalized.length,
                            text = normalized,
                            isBlank = normalized.isBlank(),
                        ),
                    ),
                ),
            )
        },
    )
}

private data class RawTextLine(
    val start: Int,
    val end: Int,
    val text: String,
)

private class ReaderCharacterMeasurePool(
    private val spec: ReaderPaginationSpec,
) {
    private val cache = mutableMapOf<Char, ReaderCharacterMetric>()

    fun measure(char: Char): ReaderCharacterMetric =
        cache.getOrPut(char) { char.measureForReader(spec) }
}

internal data class ReaderCharacterMetric(
    val char: Char,
    val widthPx: Float,
    val heightPx: Float,
    val elementKind: ReaderTextElementKind,
)

internal enum class ReaderTextElementKind {
    FullWidth,
    HalfWidth,
    LatinWord,
    Whitespace,
    ZeroWidth,
}

private data class ReaderGlyph(
    val char: Char,
    val absoluteStart: Int,
    val absoluteEnd: Int,
    val locator: ReaderTextLocator,
    val metric: ReaderCharacterMetric,
    val elementKind: ReaderTextElementKind,
)

private fun Char.measureForReader(spec: ReaderPaginationSpec): ReaderCharacterMetric {
    val kind = toElementKind()
    val width = when (kind) {
        ReaderTextElementKind.ZeroWidth -> 0f
        ReaderTextElementKind.Whitespace -> if (this == '\u3000') spec.fontPx else spec.fontPx * 0.45f
        ReaderTextElementKind.FullWidth -> spec.fontPx
        ReaderTextElementKind.LatinWord -> spec.fontPx * latinWidthRatio()
        ReaderTextElementKind.HalfWidth -> spec.fontPx * halfWidthRatio()
    }
    val height = when (kind) {
        ReaderTextElementKind.ZeroWidth -> 0f
        ReaderTextElementKind.Whitespace -> spec.fontPx
        ReaderTextElementKind.FullWidth -> spec.fontPx
        ReaderTextElementKind.LatinWord -> spec.fontPx * 0.92f
        ReaderTextElementKind.HalfWidth -> spec.fontPx * 0.9f
    }
    return ReaderCharacterMetric(
        char = this,
        widthPx = width,
        heightPx = height,
        elementKind = kind,
    )
}

private fun Char.toElementKind(): ReaderTextElementKind =
    when {
        isZeroWidthChar() -> ReaderTextElementKind.ZeroWidth
        isWhitespace() -> ReaderTextElementKind.Whitespace
        isLatinWordChar() -> ReaderTextElementKind.LatinWord
        isFullWidthByUnicode() -> ReaderTextElementKind.FullWidth
        else -> ReaderTextElementKind.HalfWidth
    }

private fun Char.latinWidthRatio(): Float =
    when (this) {
        'i', 'l', 'I', 'j', 't', 'f', 'r' -> 0.34f
        'm', 'w', 'M', 'W' -> 0.9f
        else -> if (isUpperCase()) 0.68f else if (isDigit()) 0.58f else 0.56f
    }

private fun Char.halfWidthRatio(): Float =
    when (this) {
        '.', ',', '\'', '`', ':', ';', '!', '|', '·' -> 0.28f
        '"', '(', ')', '[', ']', '{', '}' -> 0.38f
        '-', '/', '\\' -> 0.42f
        else -> 0.56f
    }

private fun Char.isLatinWordChar(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '_' || this == '\''

private fun Char.isZeroWidthChar(): Boolean {
    val code = code
    return code in 0x0300..0x036F ||
        code in 0x200B..0x200F ||
        code in 0xFE00..0xFE0F ||
        code == 0xFEFF
}

private fun Char.isFullWidthByUnicode(): Boolean {
    val code = code
    return code in 0x1100..0x11FF ||
        code in 0x2E80..0xA4CF ||
        code in 0xAC00..0xD7AF ||
        code in 0xF900..0xFAFF ||
        code in 0xFE10..0xFE1F ||
        code in 0xFE30..0xFE6F ||
        code in 0xFF01..0xFF60 ||
        code in 0xFFE0..0xFFE6 ||
        code in 0x1F300..0x1FAFF
}

private fun Char.isReaderBreakPunctuation(): Boolean =
    this in "，。！？；、,.!?;:"

private fun isReaderLeadingPunctuation(char: Char): Boolean =
    char in "，。！？；：、,.!?;:)]}）】》〉」』"

private fun String.isLikelyChapterTitle(): Boolean =
    length in 2..48 && ChapterTitleRegex.matches(this)
