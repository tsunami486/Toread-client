package com.example.toread.metadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookInsightMetadataTest {
    @Test
    fun anInsightSurvivesARoundTrip() {
        val metadataJson = encodeStoredBookInsight(
            metadataJson = """{"source":"local_import","format":"epub"}""",
            insight = BookInsight(
                description = "蒸汽与机械的浪潮中，谁能触及非凡？",
                descriptionSource = BookInsightSource.EpubFrontMatter,
                descriptionOrigin = "「内容简介」页",
                subjects = listOf("奇幻", "克苏鲁"),
                series = "诡秘世界",
            ),
        )

        val parsed = parseStoredBookInsight(metadataJson)
        assertEquals("蒸汽与机械的浪潮中，谁能触及非凡？", parsed.description)
        assertEquals(BookInsightSource.EpubFrontMatter, parsed.descriptionSource)
        assertEquals("「内容简介」页", parsed.descriptionOrigin)
        assertEquals(listOf("奇幻", "克苏鲁"), parsed.subjects)
        assertEquals("诡秘世界", parsed.series)
        assertTrue(parsed.checked)
    }

    @Test
    fun writingAnInsightKeepsTheReadersOwnAnnotations() {
        val existing = encodeBookLocalAnnotations(
            metadataJson = """{"source":"local_import","format":"epub"}""",
            annotations = BookLocalAnnotations(tags = "悬疑", notes = "读到这里了"),
        )

        val written = encodeStoredBookInsight(
            metadataJson = existing,
            insight = BookInsight(description = "一段足够长的候选简介文本，用来验证不会被覆盖。"),
        )

        assertTrue(written.contains("\"local_tags\":\"悬疑\""), written)
        assertTrue(written.contains("\"local_notes\":\"读到这里了\""), written)
        assertTrue(written.contains("\"source\":\"local_import\""), written)
        assertTrue(parseStoredBookInsight(written).checked)
        assertEquals("悬疑", parseBookLocalAnnotations(written).tags)
    }

    @Test
    fun anEmptyPassIsStillRecordedSoItIsNotRetriedForever() {
        val written = encodeStoredBookInsight(metadataJson = "{}", insight = null)

        assertTrue(hasCurrentBookInsight(written))
        assertTrue(parseStoredBookInsight(written).isEmpty)
        assertFalse(written.contains("imported_description"), written)
    }

    @Test
    fun rewritingAnInsightDropsTheKeysItNoLongerHas() {
        val first = encodeStoredBookInsight(
            metadataJson = "{}",
            insight = BookInsight(description = "旧简介", subjects = listOf("奇幻"), series = "旧系列"),
        )

        val second = encodeStoredBookInsight(
            metadataJson = first,
            insight = BookInsight(subjects = listOf("悬疑")),
        )

        val parsed = parseStoredBookInsight(second)
        assertFalse(second.contains("imported_description"), second)
        assertFalse(second.contains("旧系列"), second)
        assertEquals(listOf("悬疑"), parsed.subjects)
    }

    @Test
    fun metadataThatIsNotJsonIsReplacedInsteadOfCrashing() {
        assertTrue(parseStoredBookInsight("not json").isEmpty)
        assertFalse(parseStoredBookInsight(null).checked)
        assertTrue(hasCurrentBookInsight(encodeStoredBookInsight("not json", BookInsight())))
    }
}
