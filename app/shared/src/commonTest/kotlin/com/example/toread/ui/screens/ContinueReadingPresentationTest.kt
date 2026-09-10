package com.example.toread.ui.screens

import com.example.toread.library.BookshelfBook
import com.example.toread.reader.BookLocator
import com.example.toread.reader.EpubChapterMetadata
import com.example.toread.reader.EpubLocator
import com.example.toread.reader.EpubPublicationIndex
import com.example.toread.reader.PdfLocator
import com.example.toread.reader.TxtLocator
import com.example.toread.ui.text.ToreadStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContinueReadingPresentationTest {
    @Test
    fun epubUsesTitleStoredInLocator() {
        val presentation = continueReadingPresentation(
            book(
                format = "epub",
                locator = epubLocator(chapterTitle = "第十二章 雨夜"),
            ),
            resolvedEpubChapterTitle = "不应覆盖的新标题",
        )

        assertEquals("第十二章 雨夜", presentation.locationLabel)
        assertEquals(ToreadStrings.ContinueReading, presentation.actionLabel)
    }

    @Test
    fun legacyEpubUsesTitleResolvedFromPublicationIndex() {
        val presentation = continueReadingPresentation(
            book(format = "epub", locator = epubLocator(chapterTitle = null)),
            resolvedEpubChapterTitle = "第十二章 雨夜",
        )

        assertEquals("第十二章 雨夜", presentation.locationLabel)
    }

    @Test
    fun epubWithoutTrustworthyTitleOmitsLocationInsteadOfRepeatingProgress() {
        val presentation = continueReadingPresentation(
            book(
                format = "epub",
                progress = 0.426,
                locator = epubLocator(chapterTitle = " \r\n "),
            ),
        )

        assertNull(presentation.locationLabel)
        assertEquals(ToreadStrings.ContinueReading, presentation.actionLabel)
    }

    @Test
    fun pdfUsesOneBasedPhysicalPageNumber() {
        val presentation = continueReadingPresentation(
            book(
                format = "pdf",
                locator = PdfLocator(pageIndex = 8, pageOffsetY = 0.6, globalProgress = 0.45),
            ),
        )

        assertEquals(ToreadStrings.chapterPage(8), presentation.locationLabel)
    }

    @Test
    fun invalidNegativePdfPageIsClampedToFirstPage() {
        val presentation = continueReadingPresentation(
            book(
                format = "pdf",
                locator = PdfLocator(pageIndex = -3, pageOffsetY = 0.0, globalProgress = 0.0),
            ),
        )

        assertEquals(ToreadStrings.chapterPage(0), presentation.locationLabel)
    }

    @Test
    fun txtUsesRealChapterTitleAndOtherwiseOmitsLocation() {
        val withTitle = continueReadingPresentation(
            book(
                format = "txt",
                locator = TxtLocator(
                    charOffset = 1200,
                    paragraphIndex = null,
                    globalProgress = 0.25,
                    chapterTitle = "  第三章\r\n重逢  ",
                ),
            ),
        )
        val withoutTitle = continueReadingPresentation(
            book(
                format = "txt",
                progress = 0.25,
                locator = TxtLocator(charOffset = 1200, globalProgress = 0.25),
            ),
        )

        assertEquals("第三章 重逢", withTitle.locationLabel)
        assertNull(withoutTitle.locationLabel)
        assertEquals(ToreadStrings.ContinueReading, withoutTitle.actionLabel)
    }

    @Test
    fun txtBookTitleFallbackIsNotShownAsAChapter() {
        val presentation = continueReadingPresentation(
            book(
                format = "txt",
                progress = 0.25,
                locator = TxtLocator(
                    charOffset = 1200,
                    globalProgress = 0.25,
                    chapterTitle = "测试书籍",
                ),
            ),
        )

        assertNull(presentation.locationLabel)
    }

    @Test
    fun legacyEpubTitleResolvesByHrefBeforePhysicalOrdinal() {
        val publication = publication(
            EpubChapterMetadata(ord = 2, href = "OPS/early.xhtml", title = "第二章", contentWeight = 10),
            EpubChapterMetadata(ord = 12, href = "OPS/chapter.xhtml", title = "第十二章", contentWeight = 10),
        )
        val locator = epubLocator(
            chapterHref = "OPS/chapter.xhtml",
            chapterIndex = 2,
            chapterTitle = null,
        )

        assertEquals("第十二章", resolveContinueReadingEpubChapterTitle(locator, publication))
    }

    @Test
    fun legacyEpubNormalizesEquivalentHrefBeforeUsingPhysicalOrdinal() {
        val publication = publication(
            EpubChapterMetadata(ord = 2, href = "OPS/wrong.xhtml", title = "错误序号标题", contentWeight = 10),
            EpubChapterMetadata(ord = 12, href = "OPS/Text/雨夜.xhtml", title = "第十二章 雨夜", contentWeight = 10),
        )
        val locator = epubLocator(
            chapterHref = "OPS/./Sections/../Text/%E9%9B%A8%E5%A4%9C.xhtml?source=legacy#reading-position",
            chapterIndex = 2,
            chapterTitle = null,
        )

        assertEquals("第十二章 雨夜", resolveContinueReadingEpubChapterTitle(locator, publication))
    }

    @Test
    fun legacyEpubTitleFallsBackToPhysicalOrdinalWhenHrefCannotMatch() {
        val publication = publication(
            EpubChapterMetadata(ord = 3, href = "OPS/chapter.xhtml", title = "真实目录标题", contentWeight = 10),
        )
        val locator = epubLocator(
            chapterHref = "OPS/renamed.xhtml",
            chapterIndex = 3,
            chapterTitle = null,
        )

        assertEquals("真实目录标题", resolveContinueReadingEpubChapterTitle(locator, publication))
    }

    @Test
    fun synthesizedPhysicalEpubChapterTitleIsNotShownToUser() {
        val publication = publication(
            EpubChapterMetadata(ord = 2, href = "OPS/chapter.xhtml", title = "第 3 章", contentWeight = 10),
        )

        assertNull(
            resolveContinueReadingEpubChapterTitle(
                epubLocator(chapterHref = "OPS/chapter.xhtml", chapterIndex = 2, chapterTitle = null),
                publication,
            ),
        )
    }

    private fun epubLocator(
        chapterHref: String = "OPS/chapter.xhtml",
        chapterIndex: Int = 12,
        chapterTitle: String?,
    ): EpubLocator = EpubLocator(
        chapterHref = chapterHref,
        chapterIndex = chapterIndex,
        chapterProgress = 0.4,
        globalProgress = 0.42,
        unitIndex = 8,
        textOffset = 120,
        chapterTitle = chapterTitle,
    )

    private fun publication(vararg chapters: EpubChapterMetadata): EpubPublicationIndex =
        EpubPublicationIndex(
            title = "测试书籍",
            spineItemCount = chapters.size,
            chapters = chapters.toList(),
        )

    private fun book(
        format: String,
        progress: Double = 0.42,
        locator: BookLocator?,
    ): BookshelfBook = BookshelfBook(
        bookHash = "hash",
        title = "测试书籍",
        author = "作者",
        format = format,
        filePath = "/tmp/book.$format",
        coverPath = null,
        fileSize = 100,
        progress = progress,
        lastReadAt = "2026-08-14T00:00:00Z",
        syncStatus = "synced",
        bookSyncStatus = "synced",
        progressSyncStatus = "synced",
        locator = locator,
    )
}
