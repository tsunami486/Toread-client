package com.example.toread.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookLocatorJsonTest {
    @Test
    fun epubLocatorRoundTripsThroughJson() {
        val locator = EpubLocator(
            chapterHref = "chapter_12.xhtml",
            chapterIndex = 12,
            chapterProgress = 0.42,
            globalProgress = 0.67,
            chapterTitle = "第十二章 雨夜",
            charOffset = 3456,
            unitIndex = 8,
            textOffset = 120,
            cfi = null,
        )

        val encoded = BookLocatorJson.encode(locator)
        val decoded = BookLocatorJson.decode(encoded)

        assertTrue(encoded.contains(""""type":"epub""""))
        assertEquals(locator, decoded)
    }

    @Test
    fun oldEpubLocatorJsonDecodesWithoutCharOffset() {
        val decoded = BookLocatorJson.decode(
            """{"type":"epub","chapterHref":"chapter_12.xhtml","chapterIndex":12,"chapterProgress":0.42,"globalProgress":0.67,"cfi":null}""",
        )

        val locator = assertIs<EpubLocator>(decoded)
        assertNull(locator.charOffset)
        assertNull(locator.unitIndex)
        assertNull(locator.textOffset)
        assertNull(locator.chapterTitle)
        assertEquals(0.67, locator.globalProgress)
    }

    @Test
    fun pdfLocatorRoundTripsThroughJson() {
        val locator = PdfLocator(
            pageIndex = 4,
            pageOffsetY = 0.2,
            globalProgress = 0.5,
        )
        val decoded = BookLocatorJson.decode(
            BookLocatorJson.encode(locator),
        )

        assertEquals(locator, assertIs<PdfLocator>(decoded))
    }

    @Test
    fun txtLocatorRoundTripsThroughJson() {
        val decoded = BookLocatorJson.decode(
            BookLocatorJson.encode(
                TxtLocator(
                    charOffset = 1200,
                    paragraphIndex = 12,
                    globalProgress = 0.25,
                    chapterTitle = "第三章 重逢",
                ),
            ),
        )

        val locator = assertIs<TxtLocator>(decoded)
        assertEquals(0.25, locator.globalProgress)
        assertEquals("第三章 重逢", locator.chapterTitle)
    }

    @Test
    fun oldTxtLocatorJsonDecodesWithoutChapterTitle() {
        val decoded = BookLocatorJson.decode(
            """{"type":"txt","charOffset":1200,"paragraphIndex":12,"globalProgress":0.25}""",
        )

        val locator = assertIs<TxtLocator>(decoded)
        assertNull(locator.chapterTitle)
        assertEquals(1200, locator.charOffset)
    }

    @Test
    fun decodeOrNullReturnsLocatorForValidJson() {
        val decoded = BookLocatorJson.decodeOrNull(
            """{"type":"pdf","pageIndex":8,"pageOffsetY":0.0,"globalProgress":0.45}""",
        )

        val locator = assertIs<PdfLocator>(assertNotNull(decoded))
        assertEquals(8, locator.pageIndex)
    }

    @Test
    fun decodeOrNullDegradesBlankAndMalformedJsonToNull() {
        assertNull(BookLocatorJson.decodeOrNull(null))
        assertNull(BookLocatorJson.decodeOrNull("  "))
        assertNull(BookLocatorJson.decodeOrNull("not-json"))
        assertNull(BookLocatorJson.decodeOrNull("""{"type":"unknown"}"""))
    }
}
