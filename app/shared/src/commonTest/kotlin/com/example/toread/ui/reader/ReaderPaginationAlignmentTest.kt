package com.example.toread.ui.reader

import com.example.toread.reader.ReaderDocument
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
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
        assertTrue(lines.all { line ->
            line.startOffset in 0..source.length && line.endOffset in line.startOffset..source.length
        })

        val shortTailLine = lines.first { it.textFrom(source).contains("短尾") }
        assertFalse(shortTailLine.shouldJustify)
    }
}
