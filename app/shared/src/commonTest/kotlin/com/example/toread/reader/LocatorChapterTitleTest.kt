package com.example.toread.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocatorChapterTitleTest {
    @Test
    fun missingOrBlankTitleIsNotPersisted() {
        assertNull(null.toLocatorChapterTitle())
        assertNull(" \r\n\t ".toLocatorChapterTitle())
    }

    @Test
    fun whitespaceIsCollapsedForDisplayAndSync() {
        assertEquals(
            "第十二章 雨夜 重逢",
            "  第十二章\r\n雨夜\t  重逢  ".toLocatorChapterTitle(),
        )
    }

    @Test
    fun persistedTitleIsBounded() {
        val title = "章".repeat(200).toLocatorChapterTitle()

        assertEquals(160, title?.length)
        assertEquals("章".repeat(160), title)
    }

    @Test
    fun synthesizedPhysicalEpubTitleIsNotPersisted() {
        assertNull("第 3 章".toEpubLocatorChapterTitle(chapterIndex = 2))
        assertEquals("第三章 雨夜", "第三章 雨夜".toEpubLocatorChapterTitle(chapterIndex = 2))
    }
}
