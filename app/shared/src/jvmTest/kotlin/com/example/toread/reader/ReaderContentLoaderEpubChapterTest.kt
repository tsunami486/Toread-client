package com.example.toread.reader

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderContentLoaderEpubChapterTest {
    @Test
    fun publicationIndexContainsCanonicalMetadataForEverySpineChapter() =
        withTestEpub { epubPath ->
            val index = runBlocking {
                ReaderContentLoader().loadEpubPublicationIndex(
                    filePath = epubPath.toString(),
                    title = "Index test",
                )
            }

            val publication = assertNotNull(index)
            assertEquals("Index test", publication.title)
            assertEquals(5, publication.spineItemCount)
            assertEquals(listOf(0, 1, 2, 3, 4), publication.chapters.map(EpubChapterMetadata::ord))
            assertEquals(
                (1..5).map { chapterNumber -> "OEBPS/chapter-$chapterNumber.xhtml" },
                publication.chapters.map(EpubChapterMetadata::href),
            )
            assertEquals(
                (1..5).map { chapterNumber -> "Chapter $chapterNumber" },
                publication.chapters.map(EpubChapterMetadata::title),
            )
            assertTrue(publication.chapters.all { chapter -> chapter.contentWeight > 0L })
            assertEquals(
                publication.chapters.sumOf(EpubChapterMetadata::contentWeight),
                publication.totalContentWeight,
            )
        }

    @Test
    fun loadEpubChapterReadsOnlyRequestedChapterAndKeepsStableLocatorMetadata() =
        withTestEpub { epubPath ->
            val loader = ReaderContentLoader()
            runBlocking {
                assertNotNull(
                    loader.loadEpubPublicationIndex(
                        filePath = epubPath.toString(),
                        title = "Chapter test",
                    ),
                )
            }

            val document = runBlocking {
                loader.loadEpubChapter(
                    filePath = epubPath.toString(),
                    title = "Chapter test",
                    chapterOrd = 2,
                )
            }

            val chapterDocument = assertNotNull(document)
            val chapter = assertNotNull(chapterDocument.flow).chapters.single()
            assertEquals(2, chapter.ord)
            assertEquals("OEBPS/chapter-3.xhtml", chapter.href)
            assertEquals("Chapter 3", chapter.title)
            assertTrue(chapterDocument.text.contains("Chapter 3 body."))
            assertFalse(chapterDocument.text.contains("Chapter 2 body."))
            assertFalse(chapterDocument.text.contains("Chapter 4 body."))
            chapter.blocks.forEach { block ->
                assertEquals(2, block.chapterOrd)
                assertEquals("OEBPS/chapter-3.xhtml", block.chapterHref)
            }
            val image = chapter.blocks.filterIsInstance<ReaderContentBlock.Image>().single()
            assertEquals("OEBPS/images/figure-3.png", image.resource.href)
            assertEquals("image/png", image.resource.mediaType)
        }

    @Test
    fun emptyChapterKeepsItsRawSpineOrdinalButHasNoReadableDocument() =
        withTestEpub(emptyChapterNumber = 2) { epubPath ->
            val loader = ReaderContentLoader()
            val index = runBlocking {
                loader.loadEpubPublicationIndex(
                    filePath = epubPath.toString(),
                    title = "Empty chapter test",
                )
            }

            assertEquals(listOf(0, 1, 2, 3, 4), assertNotNull(index).chapters.map(EpubChapterMetadata::ord))
            assertNull(
                runBlocking {
                    loader.loadEpubChapter(
                        filePath = epubPath.toString(),
                        title = "Empty chapter test",
                        chapterOrd = 1,
                    )
                },
            )
            val followingChapter = assertNotNull(
                runBlocking {
                    loader.loadEpubChapter(
                        filePath = epubPath.toString(),
                        title = "Empty chapter test",
                        chapterOrd = 2,
                    )
                },
            )
            val followingFlow = assertNotNull(followingChapter.flow)
            assertEquals(2, followingFlow.chapters.single().ord)
            assertEquals("OEBPS/chapter-3.xhtml", followingFlow.chapters.single().href)
        }

    @Test
    fun compatibilityLoadReturnsOnlyTheFirstReadableChapter() =
        withTestEpub(emptyChapterNumber = 1) { epubPath ->
            val document = runBlocking {
                ReaderContentLoader().load(
                    filePath = epubPath.toString(),
                    format = "epub",
                    title = "Compatibility test",
                )
            }

            val chapters = assertNotNull(document.flow).chapters
            assertEquals(1, chapters.size)
            assertEquals(1, chapters.single().ord)
            assertEquals("OEBPS/chapter-2.xhtml", chapters.single().href)
            assertTrue(document.text.contains("Chapter 2 body."))
            assertFalse(document.text.contains("Chapter 3 body."))
        }

    @Test
    fun unknownChapterOrdinalDoesNotFallBackToAnotherChapter() =
        withTestEpub { epubPath ->
            assertNull(
                runBlocking {
                    ReaderContentLoader().loadEpubChapter(
                        filePath = epubPath.toString(),
                        title = "Unknown chapter test",
                        chapterOrd = 99,
                    )
                },
            )
        }

    private fun withTestEpub(
        emptyChapterNumber: Int? = null,
        block: (Path) -> Unit,
    ) {
        val epubPath = Files.createTempFile("toread-epub-chapter", ".epub")
        try {
            createTestEpub(epubPath, emptyChapterNumber)
            block(epubPath)
        } finally {
            Files.deleteIfExists(epubPath)
        }
    }

    private fun createTestEpub(
        path: Path,
        emptyChapterNumber: Int?,
    ) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putTextEntry("mimetype", "application/epub+zip")
            zip.putTextEntry(
                "META-INF/container.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile
                            full-path="OEBPS/content.opf"
                            media-type="application/oebps-package+xml"
                        />
                    </rootfiles>
                </container>
                """.trimIndent(),
            )
            zip.putTextEntry(
                "OEBPS/content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Chapter fixture</dc:title>
                    </metadata>
                    <manifest>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        ${(1..5).joinToString("\n") { chapterNumber ->
                            val href = if (chapterNumber == 3) {
                                "./chapter%2D3.xhtml"
                            } else {
                                "chapter-$chapterNumber.xhtml"
                            }
                            """<item id="chapter-$chapterNumber" href="$href" media-type="application/xhtml+xml"/>"""
                        }}
                        <item id="figure-3" href="images/figure%2D3.png" media-type="image/png"/>
                    </manifest>
                    <spine>
                        ${(1..5).joinToString("\n") { chapterNumber ->
                            """<itemref idref="chapter-$chapterNumber"/>"""
                        }}
                    </spine>
                </package>
                """.trimIndent(),
            )
            zip.putTextEntry(
                "OEBPS/nav.xhtml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                    <body>
                        <nav epub:type="toc">
                            <ol>
                                ${(1..5).joinToString("\n") { chapterNumber ->
                                    val href = if (chapterNumber == 3) {
                                        "./chapter%2D3.xhtml#section"
                                    } else {
                                        "chapter-$chapterNumber.xhtml"
                                    }
                                    """<li><a href="$href">Chapter $chapterNumber</a></li>"""
                                }}
                            </ol>
                        </nav>
                    </body>
                </html>
                """.trimIndent(),
            )
            (1..5).forEach { chapterNumber ->
                val body = when {
                    chapterNumber == emptyChapterNumber -> ""
                    chapterNumber == 3 ->
                        """<p id="section">Chapter 3 body.</p><img src="images/figure%2D3.png" alt="Figure 3"/>"""
                    else -> "<p>Chapter $chapterNumber body.</p>"
                }
                zip.putTextEntry(
                    "OEBPS/chapter-$chapterNumber.xhtml",
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                        <head><title>Chapter $chapterNumber</title></head>
                        <body>$body</body>
                    </html>
                    """.trimIndent(),
                )
            }
            zip.putBytesEntry(
                name = "OEBPS/images/figure-3.png",
                bytes = byteArrayOf(0x01, 0x02, 0x03),
            )
        }
    }

    private fun ZipOutputStream.putTextEntry(name: String, text: String) {
        putBytesEntry(name, text.toByteArray(Charsets.UTF_8))
    }

    private fun ZipOutputStream.putBytesEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}
