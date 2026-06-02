package com.example.toread.ui.reader

import com.example.toread.reader.ReaderDocument
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPaginationAlignmentTest {
    @Test
    fun paginationKeepsLineOffsetsForJustifiedRendering() = runBlocking {
        val source = listOf(
            "这是一段用于分页的中文文本，长度要足够拆成多行，非段尾行应该交给渲染层做两端对齐。",
            "短尾。",
        ).joinToString("\n")
        val document = ReaderDocument(title = "测试", format = "txt", text = source)
        val spec = ReaderPaginationSpec(
            fallbackTitle = "测试",
            bodyWidthPx = 120,
            bodyHeightPx = 120,
            fontPx = 20f,
            lineHeightPx = 32f,
            linesPerPage = 3,
        )

        val pages = document.paginateForReader(spec)
        val lines = pages.flatMap { it.lines }

        assertTrue(lines.isNotEmpty())
        assertTrue(lines.any { it.shouldJustify })
        assertTrue(lines.any { it.isParagraphFirst })
        assertTrue(lines.any { it.isParagraphLast })
        assertTrue(lines.filter { it.shouldJustify }.all { line ->
            line.tokens.size > 1 &&
                (line.extraTokenSpacingPx != 0f || line.adjustedWidthPxForTest() <= line.availableWidthPx + 0.5f)
        })
        assertTrue(lines.all { line ->
            line.startOffset in 0..source.length && line.endOffset in line.startOffset..source.length
        })

        val shortTailLine = lines.first { it.textFrom(source).contains("短尾") }
        assertFalse(shortTailLine.shouldJustify)
    }

    @Test
    fun paragraphFirstLineJustifiesAfterTheIndent() = runBlocking {
        val source = "天地玄黄宇宙洪荒日月盈昃辰宿列张寒来暑往秋收冬藏"
        val document = ReaderDocument(title = "测试", format = "txt", text = source)
        val spec = ReaderPaginationSpec(
            fallbackTitle = "测试",
            bodyWidthPx = 190,
            bodyHeightPx = 180,
            fontPx = 20f,
            lineHeightPx = 32f,
            linesPerPage = 5,
            paragraphFirstLineIndentPx = 40f,
        )

        val firstLine = document.paginateForReader(spec)
            .flatMap { it.lines }
            .first { it.isParagraphFirst }

        assertFalse(firstLine.isParagraphLast)
        assertTrue(firstLine.shouldJustify)
        assertEquals(150f, firstLine.availableWidthPx)
        assertTrue(firstLine.extraTokenSpacingPx > 0f)
        assertTrue(firstLine.adjustedWidthPxForTest() <= firstLine.availableWidthPx + 0.5f)
    }

    @Test
    fun paginationStoresChapterOrdAndParagraphLocators() = runBlocking {
        val source = listOf(
            "第一章 起点",
            "路明非站在门口，看见一行中文和 English words 混在一起。",
            "第二章 继续",
            "新的章节继续向前。",
        ).joinToString("\n")
        val document = ReaderDocument(title = "测试", format = "txt", text = source)
        val spec = ReaderPaginationSpec(
            fallbackTitle = "测试",
            bodyWidthPx = 180,
            bodyHeightPx = 180,
            fontPx = 20f,
            lineHeightPx = 32f,
            linesPerPage = 5,
        )

        val pages = document.paginateForReader(spec)
        val lines = pages.flatMap { it.lines }.filterNot { it.isBlank }

        assertTrue(lines.any { it.chapterOrd == 0 && it.chapterTitle == "第一章 起点" })
        assertTrue(lines.any { it.chapterOrd == 1 && it.chapterTitle == "第二章 继续" })
        assertTrue(lines.all { it.startLocator.chapterOrd == it.chapterOrd })
        assertTrue(lines.all { it.startLocator.absoluteOffset == it.startOffset })

        val firstChapterTitleLine = lines.first { it.chapterOrd == 0 && it.isChapterTitle }
        assertEquals("第一章 起点", firstChapterTitleLine.text)
        assertEquals(0f, firstChapterTitleLine.firstLineIndentPx)
        assertTrue(firstChapterTitleLine.lineHeightPx == spec.chapterTitleLineHeightPx)

        val firstChapterBodyLine = lines.first { it.chapterOrd == 0 && !it.isChapterTitle }
        assertEquals(0, firstChapterBodyLine.startLocator.paragraphIndex)
        assertTrue(firstChapterBodyLine.isParagraphFirst)
        assertTrue(firstChapterBodyLine.firstLineIndentPx > 0f)
    }

    @Test
    fun eachChapterStartsOnNewPageWithLargeTitleLine() = runBlocking {
        val source = listOf(
            "第一章 起点",
            "起点正文起点正文起点正文起点正文。",
            "第二章 继续",
            "继续正文继续正文继续正文继续正文。",
        ).joinToString("\n")
        val document = ReaderDocument(title = "测试", format = "txt", text = source)
        val spec = ReaderPaginationSpec(
            fallbackTitle = "测试",
            bodyWidthPx = 180,
            bodyHeightPx = 220,
            fontPx = 20f,
            lineHeightPx = 32f,
            linesPerPage = 6,
        )

        val pages = document.paginateForReader(spec)
        val chapterStartPages = pages.filter { it.lines.firstOrNull()?.isChapterTitle == true }

        assertEquals(2, chapterStartPages.size)
        assertEquals("第一章 起点", chapterStartPages[0].lines.first().text)
        assertEquals("第二章 继续", chapterStartPages[1].lines.first().text)
        assertTrue(chapterStartPages.all { it.lines.first().lineHeightPx == spec.chapterTitleLineHeightPx })
        assertTrue(chapterStartPages.all { it.lines.first().contentHeightPx == spec.chapterTitleFontPx })
        assertTrue(pages.none { page -> page.lines.map { it.chapterOrd }.distinct().size > 1 })
    }

    @Test
    fun chapterEntriesUseFirstRecognizedChapterPages() = runBlocking {
        val source = listOf(
            "第一章 起点",
            "起点正文起点正文起点正文起点正文。",
            "第二章 继续",
            "继续正文继续正文继续正文继续正文。",
            "第三章 归途",
            "归途正文归途正文归途正文归途正文。",
        ).joinToString("\n")
        val document = ReaderDocument(title = "测试", format = "txt", text = source)
        val spec = ReaderPaginationSpec(
            fallbackTitle = "测试",
            bodyWidthPx = 180,
            bodyHeightPx = 160,
            fontPx = 20f,
            lineHeightPx = 32f,
            linesPerPage = 4,
        )

        val pages = document.paginateForReader(spec)
        val chapters = pages.readerChapterEntries()

        assertEquals(listOf("第一章 起点", "第二章 继续", "第三章 归途"), chapters.map { it.title })
        assertEquals(listOf(0, 1, 2), chapters.map { it.chapterOrd })
        assertTrue(chapters.zipWithNext().all { (previous, next) -> previous.pageIndex < next.pageIndex })
        assertTrue(chapters.all { chapter -> pages[chapter.pageIndex].lines.first().isChapterTitle })
    }

    @Test
    fun trailingPunctuationOverflowBreaksToNextLineBeforeSpacingAdjustment() = runBlocking {
        val source = "第一章 测试\n前文前文前文。"
        val document = ReaderDocument(title = "测试", format = "txt", text = source)
        val spec = ReaderPaginationSpec(
            fallbackTitle = "测试",
            bodyWidthPx = 170,
            bodyHeightPx = 180,
            fontPx = 20f,
            lineHeightPx = 32f,
            linesPerPage = 5,
        )

        val lines = document.paginateForReader(spec).flatMap { it.lines }
        val punctuationLine = lines.first { it.text.endsWith("。") }

        assertTrue(punctuationLine.widthPx <= punctuationLine.availableWidthPx + 0.5f)
        assertFalse(punctuationLine.extraTokenSpacingPx < 0f)
        assertTrue(punctuationLine.adjustedWidthPxForTest() <= punctuationLine.availableWidthPx + 0.5f)
    }

    @Test
    fun closingBacktickDoesNotStartLineWhenItOverflows() = runBlocking {
        val source = "他说`甲乙丙丁`后续内容"
        val document = ReaderDocument(title = "测试", format = "txt", text = source)
        val spec = ReaderPaginationSpec(
            fallbackTitle = "测试",
            bodyWidthPx = 126,
            bodyHeightPx = 180,
            fontPx = 20f,
            lineHeightPx = 32f,
            linesPerPage = 5,
            paragraphFirstLineIndentPx = 0f,
        )

        val lines = document.paginateForReader(spec).flatMap { it.lines }.filterNot { it.isBlank }

        assertTrue(lines.size > 1)
        assertTrue(lines.none { it.text.startsWith("`") })
        assertTrue(lines.any { it.text.startsWith("丁`") })
    }

    @Test
    fun sentenceEndingPunctuationAndClosingQuoteShareOnePunctuationWidth() = runBlocking {
        val source = "前文前文前文。”后续内容"
        val document = ReaderDocument(title = "测试", format = "txt", text = source)
        val spec = ReaderPaginationSpec(
            fallbackTitle = "测试",
            bodyWidthPx = 140,
            bodyHeightPx = 180,
            fontPx = 20f,
            lineHeightPx = 32f,
            linesPerPage = 5,
            paragraphFirstLineIndentPx = 0f,
        )

        val lines = document.paginateForReader(spec).flatMap { it.lines }.filterNot { it.isBlank }

        assertEquals("前文前文前文。”", lines.first().text)
        assertEquals(140f, lines.first().widthPx)
        assertFalse(lines.any { it.text.startsWith("”") })
    }

    @Test
    fun pageHeightOverflowMovesLineToNextPageBeforeSpacingAdjustment() = runBlocking {
        val source = listOf(
            "第一章 测试",
            "第一段第一段第一段第一段第一段第一段。",
            "",
            "第二段第二段第二段第二段第二段第二段。",
        ).joinToString("\n")
        val document = ReaderDocument(title = "测试", format = "txt", text = source)
        val spec = ReaderPaginationSpec(
            fallbackTitle = "测试",
            bodyWidthPx = 120,
            bodyHeightPx = 84,
            fontPx = 20f,
            lineHeightPx = 32f,
            linesPerPage = 10,
        )

        val pages = document.paginateForReader(spec)

        assertTrue(pages.size > 1)
        assertTrue(pages.all { page ->
            page.lines.fold(0f) { sum, line -> sum + line.lineHeightPx } <= spec.bodyHeightPx + 0.5f
        })
    }

    @Test
    fun blankParagraphDoesNotBecomePageTopLine() = runBlocking {
        val source = "甲\n\n乙"
        val document = ReaderDocument(title = "测试", format = "txt", text = source)
        val spec = ReaderPaginationSpec(
            fallbackTitle = "测试",
            bodyWidthPx = 200,
            bodyHeightPx = 32,
            fontPx = 20f,
            lineHeightPx = 32f,
            linesPerPage = 1,
            paragraphFirstLineIndentPx = 0f,
        )

        val pages = document.paginateForReader(spec)

        assertTrue(pages.size > 1)
        assertTrue(pages.all { page -> page.lines.firstOrNull()?.isBlank != true })
        assertEquals(listOf("甲", "乙"), pages.flatMap { page -> page.lines.map { it.text } })
    }

    @Test
    fun blankParagraphDoesNotRemainAsPageBottomLine() = runBlocking {
        val source = "甲\n\n乙"
        val document = ReaderDocument(title = "测试", format = "txt", text = source)
        val spec = ReaderPaginationSpec(
            fallbackTitle = "测试",
            bodyWidthPx = 200,
            bodyHeightPx = 55,
            fontPx = 20f,
            lineHeightPx = 32f,
            linesPerPage = 2,
            paragraphFirstLineIndentPx = 0f,
        )

        val pages = document.paginateForReader(spec)

        assertTrue(pages.size > 1)
        assertTrue(pages.all { page -> page.lines.lastOrNull()?.isBlank != true })
        assertEquals(listOf("甲", "乙"), pages.flatMap { page -> page.lines.map { it.text } })
    }
}

private fun ReaderTextLineInfo.adjustedWidthPxForTest(): Float =
    widthPx + if (shouldJustify && tokens.size > 1) {
        extraTokenSpacingPx * (tokens.size - 1)
    } else {
        0f
    }
