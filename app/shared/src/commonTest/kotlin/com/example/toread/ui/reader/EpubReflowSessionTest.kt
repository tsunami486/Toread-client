package com.example.toread.ui.reader

import com.example.toread.reader.EpubChapterMetadata
import com.example.toread.reader.EpubLocator
import com.example.toread.reader.EpubPublicationIndex
import com.example.toread.reader.ReaderContentBlock
import com.example.toread.reader.ReaderDocument
import com.example.toread.reader.ReaderFlowChapter
import com.example.toread.reader.ReaderFlowDocument
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EpubReflowSessionTest {
    @Test
    fun forwardPaginationStopsAtLimitAndChainsPageCursors() = runBlocking {
        val document = chapterDocument(
            ord = 0,
            paragraphCount = 1,
            charactersPerParagraph = 4_000,
        )

        val batch = document.paginateEpubForward(
            spec = compactSpec,
            startLocator = document.epubChapterStartLocator(),
            maxPages = 3,
        )

        assertEquals(3, batch.pages.size)
        assertFalse(batch.reachedChapterEnd)
        batch.pages.zipWithNext().forEach { (current, next) ->
            assertEquals(current.endLocator, next.startLocator)
        }
    }

    @Test
    fun backwardPaginationEndsAtRequestedCursorAndOnlyReturnsBoundedSuffix() = runBlocking {
        val document = chapterDocument(
            ord = 4,
            paragraphCount = 1,
            charactersPerParagraph = 20_000,
        )
        val chapter = document.flow!!.chapters.single()
        val block = chapter.blocks.single() as ReaderContentBlock.Text
        val end = ReaderTextLocator(
            chapterOrd = chapter.ord,
            chapterHref = chapter.href,
            unitIndex = block.unitIndex,
            textOffset = 15_000,
            absoluteOffset = block.absoluteStart + 15_000,
        )

        val batch = document.paginateEpubBackward(
            spec = compactSpec,
            endLocator = end,
            maxPages = 2,
        )

        assertEquals(2, batch.pages.size)
        assertEquals(end, batch.pages.last().endLocator)
        assertFalse(batch.reachedChapterStart)
        assertTrue(batch.pages.first().startLocator.textOffset > 0)
        assertTrue(
            batch.pages.sumOf { page -> page.lines.sumOf { it.text.length } } <
                block.text.length / 10,
        )
    }

    @Test
    fun androidSessionKeepsPagesAndChapterDocumentsBoundedAcrossManyTurns() = runBlocking {
        val fixture = publicationFixture(chapterCount = 8)
        val session = EpubReflowSession(fixture.publication, fixture.documents::get)

        var state = session.open(
            locator = null,
            spec = compactSpec,
            visiblePageCount = 1,
        )
        repeat(80) { turn ->
            assertAndroidBounds(state)
            if (turn % 5 == 0) {
                state = session.preloadFollowingChapter()
                assertAndroidBounds(state)
                assertTrue(
                    state.residentPageCount <=
                        AndroidEpubResidentPageLimit + EpubNextChapterPrefetchPageLimit,
                )
            }
            if (state.canNext) state = session.next()
        }

        assertAndroidBounds(state)
        assertTrue(state.currentChapterOrd > fixture.publication.chapters.first().ord)
    }

    @Test
    fun desktopSessionKeepsPagesAndChapterDocumentsBoundedAcrossManyTurns() = runBlocking {
        val fixture = publicationFixture(chapterCount = 8)
        val session = EpubReflowSession(fixture.publication, fixture.documents::get)

        var state = session.open(
            locator = null,
            spec = compactSpec,
            visiblePageCount = 2,
        )
        repeat(50) {
            assertTrue(state.pages.size <= DesktopEpubResidentPageLimit)
            assertTrue(state.cachedChapterCount <= EpubChapterDocumentCacheLimit)
            if (state.canNext) state = session.next()
        }

        assertTrue(state.pages.size <= DesktopEpubResidentPageLimit)
        assertTrue(state.cachedChapterCount <= EpubChapterDocumentCacheLimit)
        assertTrue(state.currentChapterOrd > fixture.publication.chapters.first().ord)
    }

    @Test
    fun sessionTurnsAcrossChapterEdgesAndSupportsChapterJump() = runBlocking {
        val fixture = publicationFixture(
            chapterCount = 5,
            paragraphCount = 1,
            charactersPerParagraph = 12,
        )
        val session = EpubReflowSession(fixture.publication, fixture.documents::get)
        val spaciousSpec = compactSpec.copy(
            bodyWidthPx = 1_000,
            bodyHeightPx = 1_000,
            linesPerPage = 20,
        )

        var state = session.open(
            locator = null,
            spec = spaciousSpec,
            visiblePageCount = 1,
        )
        assertEquals(0, state.currentChapterOrd)

        state = session.next()
        assertEquals(1, state.currentChapterOrd)

        state = session.previous()
        assertEquals(0, state.currentChapterOrd)

        state = session.jumpToChapter(3)
        assertEquals(3, state.currentChapterOrd)
        assertEquals(chapterHref(3), state.locator?.chapterHref)

        state = session.previous()
        assertEquals(2, state.currentChapterOrd)
    }

    @Test
    fun followingChapterPreloadIsRecordedAndHasAFixedResidentPageBound() = runBlocking {
        val fixture = publicationFixture(chapterCount = 4)
        val session = EpubReflowSession(fixture.publication, fixture.documents::get)

        var state = session.open(
            locator = null,
            spec = compactSpec,
            visiblePageCount = 1,
        )
        assertTrue(state.pages.all { it.page.chapterOrd == 0 })

        state = session.preloadFollowingChapter()

        assertEquals(1, state.prefetchedNextChapterOrd)
        assertTrue(state.pages.size <= AndroidEpubResidentPageLimit)
        assertTrue(
            state.residentPageCount <=
                AndroidEpubResidentPageLimit + EpubNextChapterPrefetchPageLimit,
        )
        assertTrue(state.residentPageCount > state.pages.size)
        assertTrue(state.cachedChapterCount <= EpubChapterDocumentCacheLimit)
    }

    @Test
    fun sessionPublishesStrongEpubLocatorWithoutWindowLocalCharOffset() = runBlocking {
        val fixture = publicationFixture(chapterCount = 4)
        val chapter = fixture.documents.getValue(2).flow!!.chapters.single()
        val block = chapter.blocks.first() as ReaderContentBlock.Text
        val restored = EpubLocator(
            chapterHref = chapter.href.orEmpty(),
            chapterIndex = chapter.ord,
            chapterProgress = 0.5,
            globalProgress = 0.5,
            charOffset = 123_456,
            unitIndex = block.unitIndex,
            textOffset = 37,
        )
        val session = EpubReflowSession(fixture.publication, fixture.documents::get)

        val locator = assertNotNull(
            session.open(
                locator = restored,
                spec = compactSpec,
                visiblePageCount = 1,
            ).locator,
        )

        assertEquals(chapter.href, locator.chapterHref)
        assertEquals(chapter.ord, locator.chapterIndex)
        assertEquals(block.unitIndex, locator.unitIndex)
        assertEquals(37, locator.textOffset)
        assertEquals(null, locator.charOffset)
    }

    private fun assertAndroidBounds(state: EpubReflowState) {
        assertTrue(state.pages.size <= AndroidEpubResidentPageLimit)
        assertTrue(state.cachedChapterCount <= EpubChapterDocumentCacheLimit)
    }

    private data class PublicationFixture(
        val publication: EpubPublicationIndex,
        val documents: Map<Int, ReaderDocument>,
    )

    private fun publicationFixture(
        chapterCount: Int,
        paragraphCount: Int = 1,
        charactersPerParagraph: Int = 80,
    ): PublicationFixture {
        val documents = (0 until chapterCount).associateWith { ord ->
            chapterDocument(
                ord = ord,
                paragraphCount = paragraphCount,
                charactersPerParagraph = charactersPerParagraph,
            )
        }
        return PublicationFixture(
            publication = EpubPublicationIndex(
                title = "动态排版测试",
                spineItemCount = chapterCount,
                chapters = (0 until chapterCount).map { ord ->
                    EpubChapterMetadata(
                        ord = ord,
                        href = chapterHref(ord),
                        title = chapterTitle(ord),
                        contentWeight = documents.getValue(ord).text.length.toLong(),
                    )
                },
            ),
            documents = documents,
        )
    }

    private fun chapterDocument(
        ord: Int,
        paragraphCount: Int,
        charactersPerParagraph: Int,
    ): ReaderDocument {
        val title = chapterTitle(ord)
        val href = chapterHref(ord)
        var absoluteOffset = title.length + 1
        val blocks = (0 until paragraphCount).map { unitIndex ->
            val prefix = "段${unitIndex}："
            val body = buildString(charactersPerParagraph) {
                while (length < charactersPerParagraph) {
                    append("天地玄黄宇宙洪荒")
                }
            }.take(charactersPerParagraph)
            val text = prefix + body
            ReaderContentBlock.Text(
                chapterOrd = ord,
                chapterHref = href,
                chapterTitle = title,
                unitIndex = unitIndex,
                absoluteStart = absoluteOffset,
                absoluteEnd = absoluteOffset + text.length,
                text = text,
            ).also {
                absoluteOffset += text.length + 1
            }
        }
        return ReaderDocument(
            title = "动态排版测试",
            format = "epub",
            text = buildString {
                append(title)
                blocks.forEach { block ->
                    append('\n')
                    append(block.text)
                }
            },
            flow = ReaderFlowDocument(
                chapters = listOf(
                    ReaderFlowChapter(
                        ord = ord,
                        href = href,
                        title = title,
                        titleStartOffset = 0,
                        titleEndOffset = title.length,
                        blocks = blocks,
                    ),
                ),
            ),
        )
    }

    private fun chapterTitle(ord: Int): String = "第${ord + 1}章"

    private fun chapterHref(ord: Int): String = "text/chapter-$ord.xhtml"

    private val compactSpec = ReaderPaginationSpec(
        fallbackTitle = "动态排版测试",
        bodyWidthPx = 96,
        bodyHeightPx = 64,
        fontPx = 20f,
        lineHeightPx = 32f,
        linesPerPage = 2,
        paragraphFirstLineIndentPx = 0f,
    )
}
