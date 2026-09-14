package com.example.toread.library

import com.example.toread.metadata.BookLocalAnnotations
import com.example.toread.metadata.encodeBookLocalAnnotations
import com.example.toread.metadata.parseBookLocalAnnotations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookLocalAnnotationsTest {
    @Test
    fun annotationsSurviveARoundTrip() {
        val metadataJson = encodeBookLocalAnnotations(
            metadataJson = """{"source":"local_import","format":"epub"}""",
            annotations = BookLocalAnnotations(tags = " 悬疑, 仙侠 ", notes = " 读到第三章 "),
        )

        val parsed = parseBookLocalAnnotations(metadataJson)
        assertEquals("悬疑, 仙侠", parsed.tags)
        assertEquals("读到第三章", parsed.notes)
    }

    @Test
    fun rewritingKeepsTheImportMetadataUntouched() {
        val metadataJson = encodeBookLocalAnnotations(
            metadataJson = """{"source":"local_import","format":"epub","epub_title":"原名"}""",
            annotations = BookLocalAnnotations(tags = "标签", notes = "备注"),
        )

        assertTrue(metadataJson.contains("\"source\":\"local_import\""), metadataJson)
        assertTrue(metadataJson.contains("\"epub_title\":\"原名\""), metadataJson)
        assertTrue(metadataJson.contains("\"format\":\"epub\""), metadataJson)
    }

    @Test
    fun clearingAnAnnotationDropsItsKey() {
        val written = encodeBookLocalAnnotations(
            metadataJson = "{}",
            annotations = BookLocalAnnotations(tags = "标签", notes = "备注"),
        )
        val cleared = encodeBookLocalAnnotations(
            metadataJson = written,
            annotations = BookLocalAnnotations(tags = "", notes = ""),
        )

        assertFalse(cleared.contains("local_tags"), cleared)
        assertFalse(cleared.contains("local_notes"), cleared)
        assertTrue(parseBookLocalAnnotations(cleared).isEmpty)
    }

    @Test
    fun metadataThatIsNotJsonIsReplacedInsteadOfCrashing() {
        val encoded = encodeBookLocalAnnotations(
            metadataJson = "not json",
            annotations = BookLocalAnnotations(tags = "标签", notes = ""),
        )

        assertEquals("标签", parseBookLocalAnnotations(encoded).tags)
        assertTrue(parseBookLocalAnnotations("not json").isEmpty)
        assertTrue(parseBookLocalAnnotations(null).isEmpty)
    }
}
