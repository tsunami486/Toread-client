package com.example.toread.ui.reader

import com.example.toread.reader.EpubLocator
import com.example.toread.reader.TxtLocator
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderProgressTest {
    @Test
    fun txtLocatorUsesCharOffsetInsteadOfGlobalProgress() {
        val pages = listOf(
            ReaderPageSlice(chapterTitle = "正文", startOffset = 0, endOffset = 100),
            ReaderPageSlice(chapterTitle = "正文", startOffset = 100, endOffset = 200),
            ReaderPageSlice(chapterTitle = "正文", startOffset = 200, endOffset = 300),
        )
        val locator = TxtLocator(charOffset = 250, globalProgress = 0.0)

        assertEquals(2, locator.toPageIndex(pages))
    }

    @Test
    fun txtLocatorAtPageBoundaryChoosesPageStartingAtOffset() {
        val pages = listOf(
            ReaderPageSlice(chapterTitle = "正文", startOffset = 0, endOffset = 100),
            ReaderPageSlice(chapterTitle = "正文", startOffset = 100, endOffset = 200),
            ReaderPageSlice(chapterTitle = "正文", startOffset = 200, endOffset = 300),
        )
        val locator = TxtLocator(charOffset = 200, globalProgress = 0.1)

        assertEquals(2, locator.toPageIndex(pages))
    }

    @Test
    fun epubLocatorUsesCharOffsetInsteadOfPageIndex() {
        val pages = listOf(
            ReaderPageSlice(chapterTitle = "正文", startOffset = 0, endOffset = 100),
            ReaderPageSlice(chapterTitle = "正文", startOffset = 100, endOffset = 200),
            ReaderPageSlice(chapterTitle = "正文", startOffset = 200, endOffset = 300),
        )
        val locator = EpubLocator(
            chapterHref = "local-text",
            chapterIndex = 0,
            chapterProgress = 0.0,
            globalProgress = 0.0,
            charOffset = 250,
        )

        assertEquals(2, locator.toPageIndex(pages))
    }

    @Test
    fun epubLocatorPrefersContentUnitOverCharOffset() {
        val pages = listOf(
            ReaderPageSlice(
                chapterTitle = "第一章",
                startOffset = 0,
                endOffset = 100,
                chapterHref = "chapter-a.xhtml",
                startLocator = ReaderTextLocator(chapterHref = "chapter-a.xhtml", unitIndex = 0, textOffset = 0),
                endLocator = ReaderTextLocator(chapterHref = "chapter-a.xhtml", unitIndex = 0, textOffset = 100),
            ),
            ReaderPageSlice(
                chapterTitle = "第二章",
                startOffset = 100,
                endOffset = 200,
                chapterHref = "chapter-b.xhtml",
                startLocator = ReaderTextLocator(chapterHref = "chapter-b.xhtml", unitIndex = 3, textOffset = 0),
                endLocator = ReaderTextLocator(chapterHref = "chapter-b.xhtml", unitIndex = 3, textOffset = 100),
            ),
        )
        val locator = EpubLocator(
            chapterHref = "chapter-b.xhtml",
            chapterIndex = 1,
            chapterProgress = 0.0,
            globalProgress = 0.0,
            charOffset = 20,
            unitIndex = 3,
            textOffset = 40,
        )

        assertEquals(1, locator.toPageIndex(pages))
    }

    @Test
    fun oldEpubLocatorWithoutCharOffsetFallsBackToGlobalProgress() {
        val pages = listOf(
            ReaderPageSlice(chapterTitle = "正文", startOffset = 0, endOffset = 100),
            ReaderPageSlice(chapterTitle = "正文", startOffset = 100, endOffset = 200),
            ReaderPageSlice(chapterTitle = "正文", startOffset = 200, endOffset = 300),
        )
        val locator = EpubLocator(
            chapterHref = "local-text",
            chapterIndex = 2,
            chapterProgress = 0.0,
            globalProgress = 0.0,
        )

        assertEquals(0, locator.toPageIndex(pages))
    }
}
