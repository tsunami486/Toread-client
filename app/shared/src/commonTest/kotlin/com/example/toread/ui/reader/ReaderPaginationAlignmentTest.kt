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
            !line.isParagraphFirst &&
                !line.isParagraphLast &&
                line.fillRatio >= spec.justifyMinFillRatio &&
                line.tokens.size >= 6
        })
        assertTrue(lines.all { line ->
            line.startOffset in 0..source.length && line.endOffset in line.startOffset..source.length
        })

        val shortTailLine = lines.first { it.textFrom(source).contains("短尾") }
        assertFalse(shortTailLine.shouldJustify)
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

        val firstChapterLine = lines.first { it.chapterOrd == 0 }
        assertEquals(0, firstChapterLine.startLocator.paragraphIndex)
        assertTrue(firstChapterLine.isParagraphFirst)
        assertTrue(firstChapterLine.firstLineIndentPx > 0f)
    }
}
