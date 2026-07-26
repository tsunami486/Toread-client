package com.example.toread.ui.reader

import com.example.toread.reader.EpubLocator
import com.example.toread.reader.PdfLocator
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

    @Test
    fun pdfLocatorPageIndexIsClampedToDocumentBounds() {
        assertEquals(
            0,
            PdfLocator(pageIndex = -4, pageOffsetY = 0.0, globalProgress = 0.8)
                .toPdfPageIndex(pageCount = 6),
        )
        assertEquals(
            3,
            PdfLocator(pageIndex = 3, pageOffsetY = 0.0, globalProgress = 0.0)
                .toPdfPageIndex(pageCount = 6),
        )
        assertEquals(
            5,
            PdfLocator(pageIndex = 20, pageOffsetY = 0.0, globalProgress = 0.0)
                .toPdfPageIndex(pageCount = 6),
        )
        assertEquals(
            0,
            PdfLocator(pageIndex = 3, pageOffsetY = 0.0, globalProgress = 0.5)
                .toPdfPageIndex(pageCount = 0),
        )
    }

    @Test
    fun pdfProgressLocatorPreservesContinuousOffsetAndProgressOverride() {
        assertEquals(
            PdfLocator(
                pageIndex = 3,
                pageOffsetY = 0.25,
                globalProgress = 0.325,
            ),
            pdfProgressLocator(
                pageIndex = 3,
                pageCount = 10,
                pageOffsetY = 0.25,
                globalProgressOverride = 0.325,
            ),
        )
    }

    @Test
    fun pdfProgressLocatorClampsInvalidContinuousValues() {
        assertEquals(
            PdfLocator(
                pageIndex = 4,
                pageOffsetY = 1.0,
                globalProgress = 1.0,
            ),
            pdfProgressLocator(
                pageIndex = 99,
                pageCount = 5,
                pageOffsetY = 8.0,
                globalProgressOverride = 4.0,
            ),
        )
    }

    @Test
    fun pdfProgressLocatorRejectsNonFiniteContinuousValues() {
        assertEquals(
            PdfLocator(
                pageIndex = 2,
                pageOffsetY = 0.0,
                globalProgress = 0.5,
            ),
            pdfProgressLocator(
                pageIndex = 2,
                pageCount = 5,
                pageOffsetY = Double.NaN,
                globalProgressOverride = Double.POSITIVE_INFINITY,
            ),
        )
    }

    @Test
    fun normalizePageIndexKeepsSinglePageAndAlignsDesktopSpread() {
        assertEquals(3, normalizePageIndex(index = 3, pageCount = 6, isDesktop = false))
        assertEquals(2, normalizePageIndex(index = 3, pageCount = 6, isDesktop = true))
        assertEquals(4, normalizePageIndex(index = 99, pageCount = 5, isDesktop = true))
        assertEquals(0, normalizePageIndex(index = -2, pageCount = 5, isDesktop = true))
        assertEquals(0, normalizePageIndex(index = 4, pageCount = 1, isDesktop = true))
    }
}
