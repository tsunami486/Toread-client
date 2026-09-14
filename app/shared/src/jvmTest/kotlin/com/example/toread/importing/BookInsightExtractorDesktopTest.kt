package com.example.toread.importing

import com.example.toread.metadata.BookInsightSource
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentInformation

/**
 * The desktop actual, exercised against real files: a zip on disk, a GBK plain-text file and a PDF
 * whose information dictionary carries the description.
 */
class BookInsightExtractorDesktopTest {
    @Test
    fun readsTheBlurbOutOfARealEpubArchive() = runBlocking {
        val directory = Files.createTempDirectory("toread-insight")
        val epub = directory.resolve("诡秘之主 (爱潜水的乌贼).epub")
        try {
            writeDoukanStyleEpub(epub)

            val insight = assertNotNull(
                BookInsightExtractor().extract(epub.toString(), "epub", epub.fileName.toString()),
            )

            val description = assertNotNull(insight.description)
            assertTrue(description.contains("蒸汽与机械的浪潮"), description)
            assertTrue(!description.contains("排版"), description)
            assertEquals(BookInsightSource.EpubFrontMatter, insight.descriptionSource)
            assertEquals("爱潜水的乌贼", insight.authorHint)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun readsAGbkPlainTextMarkerAndTheGenreInItsFileName() = runBlocking {
        val directory = Files.createTempDirectory("toread-insight-txt")
        val txt = directory.resolve("【仙侠】剑来 (烽火戏诸侯).txt")
        try {
            Files.write(
                txt,
                """
                书名：剑来
                内容简介：
                大千世界，无奇不有。这一段的文字足够长，可以被当成一段简介来使用。

                第一章 惊蛰
                正文开始。
                """.trimIndent().toByteArray(Charset.forName("GBK")),
            )

            val insight = assertNotNull(
                BookInsightExtractor().extract(txt.toString(), "txt", txt.fileName.toString()),
            )

            assertTrue(insight.description.orEmpty().contains("大千世界"), insight.description.orEmpty())
            assertEquals(BookInsightSource.TxtMarker, insight.descriptionSource)
            assertEquals(listOf("仙侠"), insight.subjects)
            assertEquals("烽火戏诸侯", insight.authorHint)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun readsThePdfInformationDictionary() = runBlocking {
        val directory = Files.createTempDirectory("toread-insight-pdf")
        val pdf = directory.resolve("某本图册.pdf")
        try {
            PDDocument().use { document ->
                val information = PDDocumentInformation()
                information.subject = "一本摄影图册的说明文字，长度足够被当作一段简介来使用。"
                information.keywords = "摄影, 图集"
                document.documentInformation = information
                document.save(pdf.toFile())
            }

            val insight = assertNotNull(
                BookInsightExtractor().extract(pdf.toString(), "pdf", pdf.fileName.toString()),
            )

            assertTrue(insight.description.orEmpty().contains("图册的说明"), insight.description.orEmpty())
            assertEquals(BookInsightSource.PdfInfo, insight.descriptionSource)
            assertEquals(listOf("摄影", "图集"), insight.subjects)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun anUnreadableFileReturnsNothingInsteadOfFailing() = runBlocking {
        assertNull(
            BookInsightExtractor().extract(
                bookFilePath = "C:\\definitely\\missing\\book.epub",
                format = "epub",
                fileName = "book.epub",
            ),
        )
        assertNull(
            BookInsightExtractor().extract(
                bookFilePath = "C:\\definitely\\missing\\book.pdf",
                format = "pdf",
                fileName = "book.pdf",
            ),
        )
    }
}

private fun writeDoukanStyleEpub(path: Path) {
    ZipOutputStream(Files.newOutputStream(path)).use { zip ->
        zip.putTextEntry(
            "META-INF/container.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
            """.trimIndent(),
        )
        zip.putTextEntry(
            "OEBPS/content.opf",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <package version="2.0" unique-identifier="duokan-book-id" xmlns="http://www.idpf.org/2007/opf">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                <dc:title>诡秘之主</dc:title>
                <dc:creator opf:role="aut">爱潜水的乌贼</dc:creator>
                <dc:language>zh-CN</dc:language>
                <dc:identifier id="duokan-book-id" opf:scheme="UUID">urn:uuid:d03c0f88-7d98-4f40-b89d-7c658660af7d</dc:identifier>
              </metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="Description.xhtml" href="Text/Description.xhtml" media-type="application/xhtml+xml"/>
                <item id="Introduction.xhtml" href="Text/Introduction.xhtml" media-type="application/xhtml+xml"/>
                <item id="Chapter_0001.xhtml" href="Text/Chapter_0001.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx">
                <itemref idref="Description.xhtml"/>
                <itemref idref="Introduction.xhtml"/>
                <itemref idref="Chapter_0001.xhtml"/>
              </spine>
              <guide>
                <reference type="cover" title="封面" href="Text/Cover.xhtml"/>
              </guide>
            </package>
            """.trimIndent(),
        )
        zip.putTextEntry(
            "OEBPS/toc.ncx",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <navMap>
                <navPoint><navLabel><text>制作说明</text></navLabel><content src="Text/Description.xhtml"/></navPoint>
                <navPoint><navLabel><text>内容简介</text></navLabel><content src="Text/Introduction.xhtml"/></navPoint>
                <navPoint><navLabel><text>第一部 小丑</text></navLabel><content src="Text/Chapter_0001.xhtml"/></navPoint>
              </navMap>
            </ncx>
            """.trimIndent(),
        )
        zip.putTextEntry(
            "OEBPS/Text/Description.xhtml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><body>
            <h1>制作说明</h1>
            <p>本书采用某某的精校文本为底本，由某某进行封面设计、排版。制作完成，只做排版交流，请勿商业传播！</p>
            </body></html>
            """.trimIndent(),
        )
        zip.putTextEntry(
            "OEBPS/Text/Introduction.xhtml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><body>
            <p>蒸汽与机械的浪潮中，谁能触及非凡？历史和黑暗的迷雾里，又是谁在耳语？我从诡秘中醒来，睁眼看见这个世界：</p>
            <p>枪械，大炮，巨舰，飞空艇，差分机；魔药，占卜，诅咒，倒吊人，封印物……光明依旧照耀，神秘从未远离，这是一段“愚者”的传说。</p>
            </body></html>
            """.trimIndent(),
        )
        zip.putTextEntry(
            "OEBPS/Text/Chapter_0001.xhtml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><body><p>这是正文的第一段，不是简介。</p></body></html>
            """.trimIndent(),
        )
    }
}

private fun ZipOutputStream.putTextEntry(name: String, text: String) {
    putNextEntry(ZipEntry(name))
    write(text.toByteArray(Charsets.UTF_8))
    closeEntry()
}
