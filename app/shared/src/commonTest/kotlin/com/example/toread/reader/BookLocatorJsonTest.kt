package com.example.toread.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BookLocatorJsonTest {
    @Test
    fun epubLocatorRoundTripsThroughJson() {
        val locator = EpubLocator(
            chapterHref = "chapter_12.xhtml",
            chapterIndex = 12,
            chapterProgress = 0.42,
            globalProgress = 0.67,
            cfi = null,
        )

        val encoded = BookLocatorJson.encode(locator)
        val decoded = BookLocatorJson.decode(encoded)

        assertTrue(encoded.contains(""""type":"epub""""))
        assertEquals(locator, decoded)
    }

    @Test
    fun pdfLocatorRoundTripsThroughJson() {
        val decoded = BookLocatorJson.decode(
            BookLocatorJson.encode(PdfLocator(pageIndex = 4, pageOffsetY = 0.2, globalProgress = 0.5)),
        )

        assertIs<PdfLocator>(decoded)
        assertEquals(0.5, decoded.globalProgress)
    }

    @Test
    fun txtLocatorRoundTripsThroughJson() {
        val decoded = BookLocatorJson.decode(
            BookLocatorJson.encode(TxtLocator(charOffset = 1200, paragraphIndex = 12, globalProgress = 0.25)),
        )

        assertIs<TxtLocator>(decoded)
        assertEquals(0.25, decoded.globalProgress)
    }
}
