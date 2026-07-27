package com.example.toread.ui.reader

import com.example.toread.reader.EpubLocator
import com.example.toread.reader.ReaderContentLoader
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EpubLargeBookSmokeTest {
    @Test
    fun configuredLargeBookUsesOnlyBoundedChapterAndPageWindows() {
        val configuredPath = System.getenv(LargeEpubPathEnvironmentVariable)
            ?.takeIf(String::isNotBlank)
            ?: return
        val path = Path.of(configuredPath)
        assertTrue(Files.isRegularFile(path), "Large EPUB fixture does not exist: $path")

        val loader = ReaderContentLoader()
        val loadedChapterOrds = mutableListOf<Int>()
        var finalState: EpubReflowState? = null
        var chapterCount = 0
        val elapsedMillis = measureTimeMillis {
            runBlocking {
                val publication = assertNotNull(
                    loader.loadEpubPublicationIndex(
                        filePath = path.toString(),
                        title = "Large EPUB smoke test",
                    ),
                )
                chapterCount = publication.chapters.size
                assertTrue(chapterCount > 100)
                val target = publication.chapters[publication.chapters.size / 2]
                val session = EpubReflowSession(publication) { chapterOrd ->
                    loadedChapterOrds += chapterOrd
                    loader.loadEpubChapter(
                        filePath = path.toString(),
                        title = "Large EPUB smoke test",
                        chapterOrd = chapterOrd,
                    )
                }
                val spec = ReaderPaginationSpec(
                    fallbackTitle = "正文",
                    bodyWidthPx = 720,
                    bodyHeightPx = 1_100,
                    fontPx = 24f,
                    lineHeightPx = 38f,
                    linesPerPage = 28,
                )
                var state = session.open(
                    locator = EpubLocator(
                        chapterHref = target.href,
                        chapterIndex = target.ord,
                        chapterProgress = 0.35,
                        globalProgress = 0.5,
                    ),
                    spec = spec,
                    visiblePageCount = 1,
                )
                assertTrue(state.pages.size <= AndroidEpubResidentPageLimit)
                state = session.preloadFollowingChapter()
                assertTrue(
                    state.residentPageCount <=
                        AndroidEpubResidentPageLimit + EpubNextChapterPrefetchPageLimit,
                )
                assertTrue(state.cachedChapterCount <= EpubChapterDocumentCacheLimit)
                assertTrue(state.locator?.charOffset == null)

                repeat(20) {
                    state = session.next()
                    assertTrue(state.pages.size <= AndroidEpubResidentPageLimit)
                    assertTrue(state.cachedChapterCount <= EpubChapterDocumentCacheLimit)
                }
                publication.chapters
                    .filterIndexed { index, _ -> index % (chapterCount / 4).coerceAtLeast(1) == 0 }
                    .take(4)
                    .forEach { chapter ->
                        state = session.jumpToChapter(chapter.ord)
                        state = session.preloadFollowingChapter()
                        assertTrue(state.pages.size <= AndroidEpubResidentPageLimit)
                        assertTrue(
                            state.residentPageCount <=
                                AndroidEpubResidentPageLimit + EpubNextChapterPrefetchPageLimit,
                        )
                        assertTrue(state.cachedChapterCount <= EpubChapterDocumentCacheLimit)
                    }
                finalState = state
            }
        }

        val verifiedState = assertNotNull(finalState)
        assertTrue(
            loadedChapterOrds.distinct().size < chapterCount / 10,
            "Smoke test unexpectedly opened too many chapter XHTML entries",
        )
        println(
            "largeEpub chapters=$chapterCount loadedDistinct=${loadedChapterOrds.distinct().size} " +
                "residentPages=${verifiedState.residentPageCount} " +
                "maxHeapMb=${Runtime.getRuntime().maxMemory() / (1024 * 1024)} " +
                "elapsedMs=$elapsedMillis",
        )
    }

    private companion object {
        const val LargeEpubPathEnvironmentVariable = "TOREAD_LARGE_EPUB_TEST_PATH"
    }
}
