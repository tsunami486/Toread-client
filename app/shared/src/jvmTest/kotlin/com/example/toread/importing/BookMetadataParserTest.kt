package com.example.toread.importing

import com.example.toread.platform.PlatformFile
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookMetadataParserTest {
    @Test
    fun epubMetadataParserReadsOpfTitleAndCreators() = runBlocking {
        val epubPath = Files.createTempFile("toread-metadata", ".epub")
        createMinimalEpub(epubPath)

        val metadata = BasicBookMetadataParser().parse(
            file = PlatformFile(displayName = "fallback.epub", path = epubPath.toString()),
            extension = "epub",
            storedFilePath = epubPath.toString(),
        )

        assertEquals("龙族 & 测试", metadata.title)
        assertEquals("江南、测试作者", metadata.author)
        assertContains(metadata.metadataJson, "\"metadata_source\":\"epub_opf\"")
        assertContains(metadata.metadataJson, "\"epub_author\":\"江南、测试作者\"")
    }

    @Test
    fun nonEpubMetadataParserKeepsFileNameFallbackAndNullAuthor() = runBlocking {
        val metadata = BasicBookMetadataParser().parse(
            file = PlatformFile(displayName = "本地文本.txt", path = null),
            extension = "txt",
            storedFilePath = "",
        )

        assertEquals("本地文本", metadata.title)
        assertNull(metadata.author)
        assertContains(metadata.metadataJson, "\"format\":\"txt\"")
    }

    private fun createMinimalEpub(path: Path) {
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
                <?xml version="1.0" encoding="UTF-8"?>
                <package version="3.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="book-id">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>龙族 &amp; 测试</dc:title>
                        <dc:creator id="creator">江南</dc:creator>
                        <dc:creator>测试作者</dc:creator>
                    </metadata>
                    <manifest/>
                    <spine/>
                </package>
                """.trimIndent(),
            )
        }
    }

    private fun ZipOutputStream.putTextEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
