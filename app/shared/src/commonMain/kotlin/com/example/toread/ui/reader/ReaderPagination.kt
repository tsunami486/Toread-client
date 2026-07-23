package com.example.toread.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.toread.reader.EpubImageResource
import com.example.toread.reader.ReaderContentBlock
import com.example.toread.reader.ReaderDocument
import com.example.toread.reader.ReaderFlowDocument
import kotlinx.coroutines.yield
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PreferredBreakSearchWindow = 14
private const val MinimumJustifyTokenCount = 6
private const val ReaderHanReferenceChar = '汉'
private const val ParagraphMetricWidthTolerancePx = 0.5f
private const val ChapterTitleUnitIndex = -1

private val ReaderMetricWarmupChars = (
    "$ReaderHanReferenceChar" +
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" +
        "，。！？；：、（）【】《》〈〉「」『』“”‘’—…·￥" +
        "！＂＃＄％＆＇（）＊＋，－．／：；＜＝＞？＠［＼］＾＿｀｛｜｝～" +
        ",.!?;:\"'`()[]{}<>-/\\|_~@#$%^&*+= "
).toSet()

private val ChapterTitleRegex = Regex(
    pattern = """^\s*((第\s*[0-9一二三四五六七八九十百千万零〇两]+\s*[章节回卷部篇集幕].*)|([卷部篇集]\s*[0-9一二三四五六七八九十百千万零〇两]+.*)|((chapter|section|part|volume)\s+[0-9a-zivxlcdm]+.*)|(序章|序言|前言|楔子|引子|尾声|后记|番外).*)\s*$""",
    option = RegexOption.IGNORE_CASE,
)
private const val ChineseChapterNumberChars = "一二三四五六七八九十百千万零〇两"
private const val ChineseChapterUnitChars = "章节回卷部篇集幕"
private val ChineseStandaloneChapterPrefixes = listOf("序章", "序言", "前言", "楔子", "引子", "尾声", "后记", "番外")

internal data class ReaderPageSlice(
    val chapterTitle: String,
    val startOffset: Int,
    val endOffset: Int,
    val chapterOrd: Int = 0,
    val chapterHref: String? = null,
    val startLocator: ReaderTextLocator = ReaderTextLocator(chapterOrd = chapterOrd, absoluteOffset = startOffset),
    val endLocator: ReaderTextLocator = ReaderTextLocator(chapterOrd = chapterOrd, absoluteOffset = endOffset),
    val lines: List<ReaderTextLineInfo> = emptyList(),
    val items: List<ReaderPageItem> = lines.map { ReaderPageItem.TextLine(it) },
)

internal sealed class ReaderPageItem {
    abstract val chapterOrd: Int
    abstract val chapterHref: String?
    abstract val chapterTitle: String
    abstract val startOffset: Int
    abstract val endOffset: Int
    abstract val startLocator: ReaderTextLocator
    abstract val endLocator: ReaderTextLocator

    data class TextLine(
        val line: ReaderTextLineInfo,
    ) : ReaderPageItem() {
        override val chapterOrd: Int = line.chapterOrd
        override val chapterHref: String? = line.chapterHref
        override val chapterTitle: String = line.chapterTitle
        override val startOffset: Int = line.startOffset
        override val endOffset: Int = line.endOffset
        override val startLocator: ReaderTextLocator = line.startLocator
        override val endLocator: ReaderTextLocator = line.endLocator
    }

    data class ImageBlock(
        val image: ReaderImageBlockInfo,
    ) : ReaderPageItem() {
        override val chapterOrd: Int = image.chapterOrd
        override val chapterHref: String? = image.chapterHref
        override val chapterTitle: String = image.chapterTitle
        override val startOffset: Int = image.startOffset
        override val endOffset: Int = image.endOffset
        override val startLocator: ReaderTextLocator = image.startLocator
        override val endLocator: ReaderTextLocator = image.endLocator
    }
}

internal data class ReaderImageBlockInfo(
    val resource: EpubImageResource,
    val chapterOrd: Int,
    val chapterHref: String?,
    val chapterTitle: String,
    val unitIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val heightPx: Float,
    val availableWidthPx: Float,
    val startLocator: ReaderTextLocator = ReaderTextLocator(
        chapterOrd = chapterOrd,
        chapterHref = chapterHref,
        unitIndex = unitIndex,
        absoluteOffset = startOffset,
    ),
    val endLocator: ReaderTextLocator = startLocator.copy(absoluteOffset = endOffset),
)

internal data class ReaderChapterEntry(
    val chapterOrd: Int,
    val title: String,
    val pageIndex: Int,
)

internal fun List<ReaderPageSlice>.readerChapterEntries(): List<ReaderChapterEntry> {
    val seenChapterOrd = mutableSetOf<Int>()
    return mapIndexedNotNull { pageIndex, page ->
        val titleLine = page.lines.firstOrNull { it.isChapterTitle }
        val chapterOrd = titleLine?.chapterOrd ?: page.chapterOrd
        if (!seenChapterOrd.add(chapterOrd)) return@mapIndexedNotNull null
        val title = titleLine?.chapterTitle?.takeIf { it.isNotBlank() }
            ?: page.chapterTitle.takeIf { it.isNotBlank() }
            ?: titleLine?.text
            ?: "正文"
        ReaderChapterEntry(
            chapterOrd = chapterOrd,
            title = title,
            pageIndex = pageIndex,
        )
    }
}

internal data class ReaderTextLocator(
    val chapterOrd: Int = 0,
    val chapterHref: String? = null,
    val unitIndex: Int = 0,
    val paragraphIndex: Int = 0,
    val elementIndex: Int = 0,
    val charIndex: Int = 0,
    val textOffset: Int = 0,
    val absoluteOffset: Int = 0,
)

internal data class ReaderTextLineInfo(
    val startOffset: Int,
    val endOffset: Int,
    val text: String = "",
    val tokens: List<String> = emptyList(),
    val chapterOrd: Int = 0,
    val chapterHref: String? = null,
    val chapterTitle: String = "",
    val unitIndex: Int = 0,
    val paragraphIndex: Int = 0,
    val startLocator: ReaderTextLocator = ReaderTextLocator(
        chapterOrd = chapterOrd,
        chapterHref = chapterHref,
        unitIndex = unitIndex,
        paragraphIndex = paragraphIndex,
        absoluteOffset = startOffset,
    ),
    val endLocator: ReaderTextLocator = ReaderTextLocator(
        chapterOrd = chapterOrd,
        chapterHref = chapterHref,
        unitIndex = unitIndex,
        paragraphIndex = paragraphIndex,
        absoluteOffset = endOffset,
    ),
    val widthPx: Float = 0f,
    val availableWidthPx: Float = 1f,
    val contentHeightPx: Float = 0f,
    val lineHeightPx: Float = 0f,
    val firstLineIndentPx: Float = 0f,
    val fillRatio: Float = 0f,
    val extraTokenSpacingPx: Float = 0f,
    val shouldJustify: Boolean = false,
    val isParagraphFirst: Boolean = false,
    val isParagraphLast: Boolean = false,
    val isBlank: Boolean = false,
    val isChapterTitle: Boolean = false,
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
    val bodyFontSize: TextUnit = AndroidReaderTypography.bodyFontSize,
    val bodyLineHeight: TextUnit = AndroidReaderTypography.bodyLineHeight,
    val bodyLetterSpacing: TextUnit = AndroidReaderTypography.bodyLetterSpacing,
    val density: Density? = null,
    val fontFamilyResolver: FontFamily.Resolver? = null,
    val chapterTitleFontPx: Float = fontPx * 1.35f,
    val chapterTitleLineHeightPx: Float = lineHeightPx * 1.35f,
    val chapterTitleFontSize: TextUnit = bodyFontSize * 1.35f,
    val chapterTitleLineHeight: TextUnit = bodyLineHeight * 1.35f,
    val measureFontWeight: FontWeight = FontWeight.Normal,
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
    topReservedPx: Int = 0,
    bottomReservedPx: Int = 0,
): ReaderPaginationSpec {
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    return remember(
        title,
        isAndroid,
        isDesktop,
        maxWidthPx,
        maxHeightPx,
        topReservedPx,
        bottomReservedPx,
        density,
        fontFamilyResolver,
    ) {
        val fallbackTitle = title.ifBlank { "正文" }
        val typography = if (isAndroid) AndroidReaderTypography else DesktopReaderTypography
        val bodyFontSize = typography.bodyFontSize
        val bodyLineHeight = typography.bodyLineHeight
        val chapterTitleFontSize = typography.chapterTitleFontSize
        val chapterTitleLineHeight = typography.chapterTitleLineHeight
        val bodyWidthPx: Int
        val bodyHeightPx: Int
        if (isDesktop) {
            val verticalChromePx = with(density) { (24.dp * 2 + 46.dp + 28.dp).toPx().roundToInt() }
            val spreadHorizontalPaddingPx = with(density) { (60.dp * 2).toPx().roundToInt() }
            val spreadVerticalPaddingPx = with(density) { (60.dp * 2).toPx().roundToInt() }
            val spreadCenterGapPx = with(density) { (15.dp + 1.dp).toPx().roundToInt() }
            val pageOuterHeightPx = (maxHeightPx - verticalChromePx)
                .coerceAtLeast(with(density) { 420.dp.toPx().roundToInt() })
            val spreadOuterWidthPx = (pageOuterHeightPx * DesktopSpreadAspectRatio).roundToInt()
            bodyWidthPx = ((spreadOuterWidthPx - spreadHorizontalPaddingPx - spreadCenterGapPx) / 2)
                .coerceAtLeast(240)
            bodyHeightPx = (pageOuterHeightPx - spreadVerticalPaddingPx)
                .coerceAtLeast(260)
        } else {
            bodyWidthPx = (maxWidthPx - with(density) { 48.dp.toPx().roundToInt() }).coerceAtLeast(220)
            bodyHeightPx = (
                maxHeightPx -
                    with(density) { 50.dp.toPx().roundToInt() } -
                    topReservedPx -
                    bottomReservedPx
                )
                .coerceAtLeast(260)
        }
        val fontPx = with(density) { bodyFontSize.toPx() }.coerceAtLeast(1f)
        val lineHeightPx = with(density) { bodyLineHeight.toPx() }.coerceAtLeast(fontPx)
        val chapterTitleFontPx = with(density) { chapterTitleFontSize.toPx() }.coerceAtLeast(fontPx)
        val chapterTitleLineHeightPx = with(density) { chapterTitleLineHeight.toPx() }
            .coerceAtLeast(chapterTitleFontPx)
        val lineSafetyMargin = if (isDesktop) 2 else 0
        val linesPerPage = ((bodyHeightPx / lineHeightPx).toInt() - lineSafetyMargin).coerceAtLeast(1)

        ReaderPaginationSpec(
            fallbackTitle = fallbackTitle,
            bodyWidthPx = bodyWidthPx,
            bodyHeightPx = bodyHeightPx,
            fontPx = fontPx,
            lineHeightPx = lineHeightPx,
            linesPerPage = linesPerPage,
            bodyFontSize = bodyFontSize,
            bodyLineHeight = bodyLineHeight,
            bodyLetterSpacing = typography.bodyLetterSpacing,
            density = density,
            fontFamilyResolver = fontFamilyResolver,
            chapterTitleFontPx = chapterTitleFontPx,
            chapterTitleLineHeightPx = chapterTitleLineHeightPx,
            chapterTitleFontSize = chapterTitleFontSize,
            chapterTitleLineHeight = chapterTitleLineHeight,
        )
    }
}

internal suspend fun ReaderDocument.paginateForReader(
    spec: ReaderPaginationSpec,
    onPagesChanged: (List<ReaderPageSlice>) -> Unit = {},
): List<ReaderPageSlice> {
    val fallbackTitle = title.ifBlank { spec.fallbackTitle }
    if (!isReadable) return listOf(ReaderPageSlice(fallbackTitle, 0, 0))

    val textDocument = flow?.toReaderTextDocument(fallbackTitle) ?: text.toReaderTextDocument(fallbackTitle)
    val measurePool = ReaderCharacterMeasurePool(spec)
    val chapterTitleSpec = spec.forChapterTitle()
    val chapterTitleMeasurePool = ReaderCharacterMeasurePool(chapterTitleSpec)
    val pages = mutableListOf<ReaderPageSlice>()
    val pageItems = mutableListOf<ReaderPageItem>()
    var pageHeightPx = 0f

    fun lineHeightForPaging(line: ReaderTextLineInfo): Float =
        line.lineHeightPx.coerceAtLeast(spec.minimumLineHeightPx * 0.5f)

    fun itemHeightForPaging(item: ReaderPageItem): Float =
        when (item) {
            is ReaderPageItem.TextLine -> lineHeightForPaging(item.line)
            is ReaderPageItem.ImageBlock -> item.image.heightPx
        }

    fun trimTrailingBlankLines() {
        while ((pageItems.lastOrNull() as? ReaderPageItem.TextLine)?.line?.isBlank == true) {
            pageHeightPx -= itemHeightForPaging(pageItems.removeAt(pageItems.lastIndex))
        }
        if (pageHeightPx < 0f) {
            pageHeightPx = 0f
        }
    }

    suspend fun flushPage(force: Boolean = false) {
        trimTrailingBlankLines()
        if (pageItems.isEmpty() && !force) return
        val firstItem = pageItems.firstOrNull()
        val lastItem = pageItems.lastOrNull()
        val pageLines = pageItems.mapNotNull { (it as? ReaderPageItem.TextLine)?.line }
        pages += ReaderPageSlice(
            chapterTitle = firstItem?.chapterTitle ?: fallbackTitle,
            startOffset = firstItem?.startOffset ?: 0,
            endOffset = lastItem?.endOffset ?: firstItem?.startOffset ?: 0,
            chapterOrd = firstItem?.chapterOrd ?: 0,
            chapterHref = firstItem?.chapterHref,
            startLocator = firstItem?.startLocator ?: ReaderTextLocator(),
            endLocator = lastItem?.endLocator ?: firstItem?.startLocator ?: ReaderTextLocator(),
            lines = pageLines.toList(),
            items = pageItems.toList(),
        )
        pageItems.clear()
        pageHeightPx = 0f
        onPagesChanged(pages.toList())
        if (pages.size % 16 == 0) {
            yield()
        }
    }

    suspend fun appendItem(item: ReaderPageItem) {
        var pendingItem = item
        while (true) {
            val itemHeight = itemHeightForPaging(pendingItem)
            val isBlankLine = (pendingItem as? ReaderPageItem.TextLine)?.line?.isBlank == true
            if (isBlankLine) {
                if (pageItems.isNotEmpty() && pageHeightPx + itemHeight <= spec.bodyHeightPx) {
                    pageItems += pendingItem
                    pageHeightPx += itemHeight
                }
                return
            }
            if (pageItems.isEmpty() || pageHeightPx + itemHeight <= spec.bodyHeightPx) {
                pageItems += pendingItem
                pageHeightPx += itemHeight
                return
            }

            trimTrailingBlankLines()
            if (pageItems.isEmpty() || pageHeightPx + itemHeight <= spec.bodyHeightPx) {
                pageItems += pendingItem
                pageHeightPx += itemHeight
                return
            }

            flushPage()
            if (pendingItem is ReaderPageItem.TextLine && pendingItem.line.lineHeightPx > spec.bodyHeightPx) {
                pendingItem = ReaderPageItem.TextLine(pendingItem.line.copy(lineHeightPx = spec.bodyHeightPx.toFloat()))
            } else if (pendingItem is ReaderPageItem.ImageBlock && pendingItem.image.heightPx > spec.bodyHeightPx) {
                pendingItem = ReaderPageItem.ImageBlock(pendingItem.image.copy(heightPx = spec.bodyHeightPx.toFloat()))
            }
        }
    }

    textDocument.chapters.forEachIndexed { chapterIndex, chapter ->
        if (chapterIndex > 0 && pageItems.isNotEmpty()) {
            flushPage()
        }
        chapter.titleLines(chapterTitleSpec, chapterTitleMeasurePool).forEach { titleLine ->
            appendItem(ReaderPageItem.TextLine(titleLine))
        }
        chapter.blocks.forEach { block ->
            when (block) {
                is ReaderChapterBlock.Paragraph -> {
                    block.paragraph.toTextLines(spec, measurePool).forEach { line ->
                        appendItem(ReaderPageItem.TextLine(line))
                    }
                }
                is ReaderChapterBlock.Image -> appendItem(ReaderPageItem.ImageBlock(block.image.toImageBlockInfo(spec)))
            }
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
                measurePool = measurePool,
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
        val glyphWidth = glyphs.contextualWidthIncrement(index, lineStart, measurePool)
        val candidateWidth = width + glyphWidth
        val wouldOverflow = width > 0f && candidateWidth > lineLimit + ParagraphMetricWidthTolerancePx
        if (wouldOverflow) {
            var breakAt = index
            val preferred = glyphs.findPreferredLineBreak(lineStart, index, lineLimit, spec, measurePool)
            if (preferred > lineStart) {
                breakAt = preferred
            }
            breakAt = glyphs.moveBreakBeforeLineStartProhibitedChar(lineStart, breakAt)
            emitLine(breakAt.coerceAtMost(glyphs.size))
        } else {
            width = candidateWidth
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
        val shouldJustify = !isLast &&
            line.fillRatio >= spec.justifyMinFillRatio &&
            line.tokens.size >= MinimumJustifyTokenCount
        line.copy(
            isParagraphFirst = isFirst,
            isParagraphLast = isLast,
            shouldJustify = shouldJustify,
            extraTokenSpacingPx = if (shouldJustify) {
                line.calculateTokenSpacingPx()
            } else {
                0f
            },
        )
    }.fitLineWidths()
}

private fun ReaderParagraph.createTextLineInfo(
    glyphs: List<ReaderGlyph>,
    spec: ReaderPaginationSpec,
    lineOrdinal: Int,
    availableWidthPx: Float,
    measurePool: ReaderCharacterMeasurePool,
): ReaderTextLineInfo {
    val startGlyph = glyphs.first()
    val endGlyph = glyphs.last()
    val widthPx = glyphs.rangeWidth(0, glyphs.size, measurePool)
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
        chapterHref = chapterHref,
        chapterTitle = chapterTitle,
        unitIndex = unitIndex,
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

private fun ReaderPaginationSpec.forChapterTitle(): ReaderPaginationSpec =
    copy(
        fontPx = chapterTitleFontPx,
        lineHeightPx = chapterTitleLineHeightPx,
        bodyFontSize = chapterTitleFontSize,
        bodyLineHeight = chapterTitleLineHeight,
        measureFontWeight = FontWeight.Bold,
        paragraphFirstLineIndentPx = 0f,
        minimumLineHeightPx = chapterTitleLineHeightPx,
    )

private fun ReaderChapter.titleLines(
    spec: ReaderPaginationSpec,
    measurePool: ReaderCharacterMeasurePool,
): List<ReaderTextLineInfo> {
    if (titleStartOffset >= titleEndOffset || title.isBlank()) return emptyList()
    return ReaderParagraph(
        chapterOrd = ord,
        chapterHref = href,
        chapterTitle = title,
        unitIndex = ChapterTitleUnitIndex,
        paragraphIndex = 0,
        absoluteStart = titleStartOffset,
        absoluteEnd = titleEndOffset,
        text = title,
        isBlank = false,
    ).toTextLines(spec, measurePool).map { line ->
        line.copy(
            shouldJustify = false,
            extraTokenSpacingPx = 0f,
            isChapterTitle = true,
        )
    }
}

private fun ReaderParagraph.blankLine(spec: ReaderPaginationSpec): ReaderTextLineInfo {
    val locator = ReaderTextLocator(
        chapterOrd = chapterOrd,
        chapterHref = chapterHref,
        unitIndex = unitIndex,
        paragraphIndex = paragraphIndex,
        absoluteOffset = absoluteStart,
    )
    return ReaderTextLineInfo(
        startOffset = absoluteStart,
        endOffset = absoluteEnd,
        text = "",
        tokens = emptyList(),
        chapterOrd = chapterOrd,
        chapterHref = chapterHref,
        chapterTitle = chapterTitle,
        unitIndex = unitIndex,
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
    val lastParagraph = blocks
        .mapNotNull { (it as? ReaderChapterBlock.Paragraph)?.paragraph }
        .lastOrNull()
    val locator = ReaderTextLocator(
        chapterOrd = ord,
        chapterHref = href,
        unitIndex = lastParagraph?.unitIndex ?: 0,
        paragraphIndex = lastParagraph?.paragraphIndex ?: 0,
        absoluteOffset = absoluteEnd,
    )
    return ReaderTextLineInfo(
        startOffset = absoluteEnd,
        endOffset = absoluteEnd,
        chapterOrd = ord,
        chapterHref = href,
        chapterTitle = title,
        unitIndex = locator.unitIndex,
        paragraphIndex = locator.paragraphIndex,
        startLocator = locator,
        endLocator = locator,
        lineHeightPx = spec.lineHeightPx * 0.65f,
        isParagraphFirst = true,
        isParagraphLast = true,
        isBlank = true,
    )
}

private fun ReaderTextLineInfo.calculateTokenSpacingPx(): Float =
    if (tokens.size <= 1) {
        0f
    } else {
        (availableWidthPx - widthPx) / (tokens.size - 1)
    }

private fun ReaderTextLineInfo.adjustedWidthPx(): Float =
    widthPx + if (shouldJustify && tokens.size > 1) {
        extraTokenSpacingPx * (tokens.size - 1)
    } else {
        0f
    }

private fun ReaderTextLineInfo.isWidthOverflowing(): Boolean =
    adjustedWidthPx() > availableWidthPx + 0.5f

private fun List<ReaderTextLineInfo>.fitLineWidths(): List<ReaderTextLineInfo> {
    var fitted = this
    repeat(8) {
        val overflowingLines = fitted.filter { it.isWidthOverflowing() }
        if (overflowingLines.isEmpty()) return fitted
        var changed = false
        fitted = fitted.map { line ->
            if (line.shouldJustify && !line.isWidthOverflowing()) {
                val adjusted = line.copy(extraTokenSpacingPx = line.calculateTokenSpacingPx())
                changed = changed || adjusted.extraTokenSpacingPx != line.extraTokenSpacingPx
                adjusted
            } else {
                line
            }
        }
        if (!changed) return fitted
    }
    return fitted
}

private fun List<ReaderTextLineInfo>.totalLineHeightPx(): Float =
    fold(0f) { sum, line -> sum + line.lineHeightPx }

private fun ReaderParagraph.toGlyphs(measurePool: ReaderCharacterMeasurePool): List<ReaderGlyph> {
    return text.toChapterTextGlyphs(
        chapterOrd = chapterOrd,
        chapterHref = chapterHref,
        unitIndex = unitIndex,
        paragraphIndex = paragraphIndex,
        absoluteStart = absoluteStart,
        measurePool = measurePool,
    )
}

private fun String.toChapterTextGlyphs(
    chapterOrd: Int,
    chapterHref: String?,
    unitIndex: Int,
    paragraphIndex: Int,
    absoluteStart: Int,
    measurePool: ReaderCharacterMeasurePool,
): List<ReaderGlyph> {
    val glyphs = mutableListOf<ReaderGlyph>()
    var index = 0
    var elementIndex = 0
    while (index < length) {
        val char = this[index]
        if (char.isLatinWordChar()) {
            val elementStart = index
            while (index < length && this[index].isLatinWordChar()) {
                val current = this[index]
                glyphs += ReaderGlyph(
                    char = current,
                    absoluteStart = absoluteStart + index,
                    absoluteEnd = absoluteStart + index + 1,
                    locator = ReaderTextLocator(
                        chapterOrd = chapterOrd,
                        chapterHref = chapterHref,
                        unitIndex = unitIndex,
                        paragraphIndex = paragraphIndex,
                        elementIndex = elementIndex,
                        charIndex = index - elementStart,
                        textOffset = index,
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
                    chapterHref = chapterHref,
                    unitIndex = unitIndex,
                    paragraphIndex = paragraphIndex,
                    elementIndex = elementIndex,
                    charIndex = 0,
                    textOffset = index,
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
        textOffset = locator.textOffset + 1,
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
    measurePool: ReaderCharacterMeasurePool,
): Int {
    val searchStart = (endExclusive - PreferredBreakSearchWindow).coerceAtLeast(start + 1)
    for (index in endExclusive downTo searchStart) {
        val char = this[index - 1].char
        if (
            char.isReaderSentenceEndingPunctuation() &&
            index < size &&
            this[index].char.isReaderClosingDoubleQuote()
        ) {
            continue
        }
        if (char.isWhitespace() || char.isReaderBreakPunctuation()) {
            val width = rangeWidth(start, index, measurePool)
            if (width >= lineLimitPx * spec.justifyMinFillRatio) {
                return index
            }
        }
    }
    return -1
}

private fun List<ReaderGlyph>.moveBreakBeforeLineStartProhibitedChar(
    lineStart: Int,
    breakAt: Int,
): Int {
    var adjusted = breakAt.coerceAtMost(size)
    while (
        adjusted > lineStart + 1 &&
        adjusted < size &&
        this[adjusted].char.isReaderLineStartProhibited()
    ) {
        adjusted--
    }
    return if (
        adjusted > lineStart &&
        adjusted < size &&
        !this[adjusted].char.isReaderLineStartProhibited()
    ) {
        adjusted
    } else {
        breakAt
    }
}

private fun List<ReaderGlyph>.contextualWidthIncrement(
    index: Int,
    lineStart: Int,
    measurePool: ReaderCharacterMeasurePool,
): Float {
    val previous = getOrNull(index - 1)
    val current = this[index]
    val pairMetric = if (previous != null && index > lineStart) {
        measurePool.measureSentenceEndingQuotePair(previous.char, current.char)
    } else {
        null
    }
    return pairMetric?.let { it.widthPx - previous!!.metric.widthPx } ?: current.metric.widthPx
}

private fun List<ReaderGlyph>.rangeWidth(
    start: Int,
    endExclusive: Int,
    measurePool: ReaderCharacterMeasurePool,
): Float {
    var width = 0f
    for (index in start until endExclusive) {
        width += contextualWidthIncrement(index, start, measurePool)
    }
    return width
}

private data class ReaderTextDocument(
    val chapters: List<ReaderChapter>,
)

private data class ReaderChapter(
    val ord: Int,
    val href: String?,
    val title: String,
    val titleStartOffset: Int,
    val titleEndOffset: Int,
    val absoluteEnd: Int,
    val blocks: List<ReaderChapterBlock>,
)

private sealed class ReaderChapterBlock {
    data class Paragraph(
        val paragraph: ReaderParagraph,
    ) : ReaderChapterBlock()

    data class Image(
        val image: ReaderChapterImage,
    ) : ReaderChapterBlock()
}

private data class ReaderParagraph(
    val chapterOrd: Int,
    val chapterHref: String?,
    val chapterTitle: String,
    val unitIndex: Int,
    val paragraphIndex: Int,
    val absoluteStart: Int,
    val absoluteEnd: Int,
    val text: String,
    val isBlank: Boolean,
)

private data class ReaderChapterImage(
    val resource: EpubImageResource,
    val chapterOrd: Int,
    val chapterHref: String?,
    val chapterTitle: String,
    val unitIndex: Int,
    val absoluteStart: Int,
    val absoluteEnd: Int,
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
        val blocks = rawLines.mapIndexed { index, line ->
            ReaderChapterBlock.Paragraph(
                ReaderParagraph(
                    chapterOrd = ord,
                    chapterHref = null,
                    chapterTitle = chapterTitle,
                    unitIndex = index,
                    paragraphIndex = index,
                    absoluteStart = line.start,
                    absoluteEnd = line.end,
                    text = line.text,
                    isBlank = line.text.isBlank(),
                ),
            )
        }
        val absoluteEnd = rawLines.lastOrNull()?.end ?: chapterTitleEnd
        chapters += ReaderChapter(
            ord = ord,
            href = null,
            title = chapterTitle,
            titleStartOffset = chapterTitleStart,
            titleEndOffset = chapterTitleEnd,
            absoluteEnd = absoluteEnd,
            blocks = blocks,
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
                    href = null,
                    title = fallbackTitle,
                    titleStartOffset = 0,
                    titleEndOffset = 0,
                    absoluteEnd = normalized.length,
                    blocks = listOf(
                        ReaderChapterBlock.Paragraph(
                            ReaderParagraph(
                                chapterOrd = 0,
                                chapterHref = null,
                                chapterTitle = fallbackTitle,
                                unitIndex = 0,
                                paragraphIndex = 0,
                                absoluteStart = 0,
                                absoluteEnd = normalized.length,
                                text = normalized,
                                isBlank = normalized.isBlank(),
                            ),
                        ),
                    ),
                ),
            )
        },
    )
}

private fun ReaderFlowDocument.toReaderTextDocument(fallbackTitle: String): ReaderTextDocument =
    ReaderTextDocument(
        chapters = chapters.map { chapter ->
            val blocks = chapter.blocks.mapIndexed { paragraphIndex, block ->
                when (block) {
                    is ReaderContentBlock.Text -> ReaderChapterBlock.Paragraph(
                        ReaderParagraph(
                            chapterOrd = block.chapterOrd,
                            chapterHref = block.chapterHref,
                            chapterTitle = block.chapterTitle,
                            unitIndex = block.unitIndex,
                            paragraphIndex = paragraphIndex,
                            absoluteStart = block.absoluteStart,
                            absoluteEnd = block.absoluteEnd,
                            text = block.text,
                            isBlank = block.text.isBlank(),
                        ),
                    )
                    is ReaderContentBlock.Image -> ReaderChapterBlock.Image(
                        ReaderChapterImage(
                            resource = block.resource,
                            chapterOrd = block.chapterOrd,
                            chapterHref = block.chapterHref,
                            chapterTitle = block.chapterTitle,
                            unitIndex = block.unitIndex,
                            absoluteStart = block.absoluteStart,
                            absoluteEnd = block.absoluteEnd,
                        ),
                    )
                }
            }
            ReaderChapter(
                ord = chapter.ord,
                href = chapter.href,
                title = chapter.title.ifBlank { fallbackTitle },
                titleStartOffset = chapter.titleStartOffset,
                titleEndOffset = chapter.titleEndOffset,
                absoluteEnd = blocks.lastOrNull()?.endOffset ?: chapter.titleEndOffset,
                blocks = blocks,
            )
        }.ifEmpty {
            listOf(
                ReaderChapter(
                    ord = 0,
                    href = null,
                    title = fallbackTitle,
                    titleStartOffset = 0,
                    titleEndOffset = 0,
                    absoluteEnd = 0,
                    blocks = emptyList(),
                ),
            )
        },
    )

private val ReaderChapterBlock.endOffset: Int
    get() = when (this) {
        is ReaderChapterBlock.Paragraph -> paragraph.absoluteEnd
        is ReaderChapterBlock.Image -> image.absoluteEnd
    }

private fun ReaderChapterImage.toImageBlockInfo(spec: ReaderPaginationSpec): ReaderImageBlockInfo {
    val maxHeight = spec.bodyHeightPx * 0.72f
    val targetHeight = if (resource.widthPx != null && resource.heightPx != null) {
        val ratio = resource.heightPx.toFloat() / resource.widthPx.toFloat()
        (spec.bodyWidthPx * ratio).coerceIn(spec.lineHeightPx * 3f, maxHeight)
    } else {
        min(spec.bodyWidthPx * 0.62f, maxHeight).coerceAtLeast(spec.lineHeightPx * 3f)
    }
    val locator = ReaderTextLocator(
        chapterOrd = chapterOrd,
        chapterHref = chapterHref,
        unitIndex = unitIndex,
        absoluteOffset = absoluteStart,
    )
    return ReaderImageBlockInfo(
        resource = resource,
        chapterOrd = chapterOrd,
        chapterHref = chapterHref,
        chapterTitle = chapterTitle,
        unitIndex = unitIndex,
        startOffset = absoluteStart,
        endOffset = absoluteEnd,
        heightPx = targetHeight,
        availableWidthPx = spec.bodyWidthPx.toFloat(),
        startLocator = locator,
        endLocator = locator,
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
    private val sentenceEndingPunctuationCache = mutableMapOf<Char, ReaderCharacterMetric>()
    private val sentenceEndingQuotePairCache = mutableMapOf<String, ReaderCharacterPairMetric>()
    private val hanMetric: ReaderCharacterMetric

    init {
        ReaderMetricWarmupChars.forEach { char ->
            cache[char] = char.measureForReader(spec)
        }
        hanMetric = cache.getValue(ReaderHanReferenceChar)
    }

    fun measure(char: Char): ReaderCharacterMetric =
        if (char.isReaderHanChar()) {
            hanMetric.copy(char = char)
        } else {
            cache.getOrPut(char) { char.measureForReader(spec) }
        }

    fun measureSentenceEndingQuotePair(
        punctuation: Char,
        quote: Char,
    ): ReaderCharacterPairMetric? {
        if (!punctuation.isReaderSentenceEndingPunctuation() || !quote.isReaderClosingDoubleQuote()) {
            return null
        }
        val key = "$punctuation$quote"
        return sentenceEndingQuotePairCache.getOrPut(key) {
            val punctuationMetric = measure(punctuation)
            val trimmedPunctuationMetric = sentenceEndingPunctuationCache.getOrPut(punctuation) {
                punctuation.measureSentenceEndingPunctuationForReader(spec, punctuationMetric)
            }
            val quoteMetric = measure(quote)
            val pairWidth = punctuationMetric.widthPx
            ReaderCharacterPairMetric(
                first = punctuation,
                second = quote,
                widthPx = pairWidth,
                firstWidthPx = trimmedPunctuationMetric.widthPx,
                secondWidthPx = quoteMetric.widthPx,
                secondOverlapPx = (trimmedPunctuationMetric.widthPx + quoteMetric.widthPx - pairWidth)
                    .coerceAtLeast(0f),
            )
        }
    }
}

internal data class ReaderCharacterMetric(
    val char: Char,
    val widthPx: Float,
    val heightPx: Float,
    val elementKind: ReaderTextElementKind,
)

private data class ReaderCharacterPairMetric(
    val first: Char,
    val second: Char,
    val widthPx: Float,
    val firstWidthPx: Float,
    val secondWidthPx: Float,
    val secondOverlapPx: Float,
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
    val paragraphMetric = measureForReaderWithParagraph(spec)
    if (paragraphMetric != null) return paragraphMetric

    return measureForReaderByFormula(spec)
}

private fun Char.measureForReaderWithParagraph(spec: ReaderPaginationSpec): ReaderCharacterMetric? {
    val kind = toElementKind()
    if (kind == ReaderTextElementKind.ZeroWidth) return null

    val density = spec.density ?: return null
    val fontFamilyResolver = spec.fontFamilyResolver ?: return null
    return runCatching {
        val text = toString()
        val style = TextStyle(
            fontSize = spec.bodyFontSize,
            lineHeight = spec.bodyLineHeight,
            fontWeight = spec.measureFontWeight,
            letterSpacing = spec.bodyLetterSpacing,
        )
        val intrinsics = ParagraphIntrinsics(
            text = text,
            style = style,
            annotations = emptyList(),
            density = density,
            fontFamilyResolver = fontFamilyResolver,
        )
        val paragraph = Paragraph(
            paragraphIntrinsics = intrinsics,
            constraints = Constraints(maxWidth = ceil(intrinsics.maxIntrinsicWidth).toInt().coerceAtLeast(1)),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        val bounds = paragraph.getBoundingBox(0)
        val fallback = measureForReaderByFormula(spec)
        val width = max(paragraph.getLineWidth(0), bounds.right - bounds.left)
            .takeIf { it > 0f }
            ?: fallback.widthPx
        val height = (bounds.bottom - bounds.top)
            .takeIf { it > 0f }
            ?: fallback.heightPx
        ReaderCharacterMetric(
            char = this,
            widthPx = width,
            heightPx = height,
            elementKind = kind,
        )
    }.getOrNull()
}

private fun Char.measureSentenceEndingPunctuationForReader(
    spec: ReaderPaginationSpec,
    fallback: ReaderCharacterMetric,
): ReaderCharacterMetric {
    val density = spec.density ?: return fallback
    val fontFamilyResolver = spec.fontFamilyResolver ?: return fallback
    return runCatching {
        val style = TextStyle(
            fontSize = spec.bodyFontSize,
            lineHeight = spec.bodyLineHeight,
            fontWeight = spec.measureFontWeight,
            letterSpacing = spec.bodyLetterSpacing,
        )
        val intrinsics = ParagraphIntrinsics(
            text = toString(),
            style = style,
            annotations = emptyList(),
            density = density,
            fontFamilyResolver = fontFamilyResolver,
        )
        val paragraph = Paragraph(
            paragraphIntrinsics = intrinsics,
            constraints = Constraints(maxWidth = ceil(intrinsics.maxIntrinsicWidth).toInt().coerceAtLeast(1)),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        val bounds = paragraph.getBoundingBox(0)
        val trimmedWidth = (bounds.right - bounds.left)
            .takeIf { it > 0f }
            ?.coerceIn(fallback.widthPx * 0.35f, fallback.widthPx)
            ?: fallback.widthPx
        fallback.copy(widthPx = trimmedWidth)
    }.getOrElse {
        fallback
    }
}

private fun Char.measureForReaderByFormula(spec: ReaderPaginationSpec): ReaderCharacterMetric {
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

private fun Char.isReaderHanChar(): Boolean {
    val code = code
    return code in 0x3400..0x4DBF ||
        code in 0x4E00..0x9FFF ||
        code in 0xF900..0xFAFF
}

private fun Char.isReaderBreakPunctuation(): Boolean =
    this in "，。！？；、,.!?;:"

private fun Char.isReaderSentenceEndingPunctuation(): Boolean =
    this in "。！？.!?…"

private fun Char.isReaderClosingDoubleQuote(): Boolean =
    this == '”'

private fun Char.isReaderLineStartProhibited(): Boolean =
    this in "，。！？；：、,.!?;:)]}）】》〉」』’”`"

private fun String.isLikelyChapterTitle(): Boolean {
    val trimmed = trim()
    if (trimmed.length !in 2..48) return false
    return trimmed.isLikelyChineseChapterTitle() || ChapterTitleRegex.matches(trimmed)
}

private fun String.isLikelyChineseChapterTitle(): Boolean {
    if (ChineseStandaloneChapterPrefixes.any { startsWith(it) }) return true
    if (startsWith("第")) {
        var index = 1
        while (index < length && this[index].isWhitespace()) index++
        val numberStart = index
        while (index < length && this[index].isChapterNumberChar()) index++
        if (index == numberStart) return false
        while (index < length && this[index].isWhitespace()) index++
        return index < length && this[index] in ChineseChapterUnitChars
    }
    val firstChar = firstOrNull()
    if (firstChar != null && firstChar in ChineseChapterUnitChars) {
        var index = 1
        while (index < length && this[index].isWhitespace()) index++
        val numberStart = index
        while (index < length && this[index].isChapterNumberChar()) index++
        return index > numberStart
    }
    return false
}

private fun Char.isChapterNumberChar(): Boolean =
    isDigit() || this in ChineseChapterNumberChars
