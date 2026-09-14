package com.example.toread.importing

import com.example.toread.metadata.BookInsightSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The extraction pass is judged on the shape of a real self-made Chinese EPUB: a Doukan-style book
 * whose OPF metadata carries no description at all, whose `Description.xhtml` is a 制作说明 page, and
 * whose actual blurb sits in `Introduction.xhtml` under the navigation label 内容简介.
 */
class BookInsightExtractionTest {
    @Test
    fun theNavigationLabelFindsTheBlurbAndTheProductionNotesAreNotIt() {
        val insight = assertNotNull(extractEpubInsight(bookArchive(), fileName = "诡秘之主.epub"))

        val description = assertNotNull(insight.description)
        assertTrue(description.contains("蒸汽与机械的浪潮"), description)
        assertTrue(description.contains("愚者"), description)
        assertTrue(
            description.none { it == '制' && description.contains("制作说明") },
            "the 制作说明 page must never become the description: $description",
        )
        assertEquals(BookInsightSource.EpubFrontMatter, insight.descriptionSource)
        assertTrue(insight.descriptionOrigin.orEmpty().contains("内容简介"), insight.descriptionOrigin.orEmpty())
    }

    @Test
    fun packageMetadataIsPreferredOverTheFrontMatter() {
        val archive = bookArchive(
            opfMetadata = """
                <dc:description>出版方给的正式简介，足够长到能被当成一段简介使用。</dc:description>
                <dc:subject>奇幻</dc:subject>
                <dc:subject>FIC009000</dc:subject>
                <meta name="calibre:series" content="诡秘世界"/>
            """.trimIndent(),
        )

        val insight = assertNotNull(extractEpubInsight(archive, fileName = null))

        assertEquals("出版方给的正式简介，足够长到能被当成一段简介使用。", insight.description)
        assertEquals(BookInsightSource.EpubPackage, insight.descriptionSource)
        assertEquals(listOf("奇幻"), insight.subjects)
        assertEquals("诡秘世界", insight.series)
    }

    @Test
    fun aBookWithoutADescriptionPageIsLeftAlone() {
        val archive = FakeBookArchive(
            mapOf(
                "META-INF/container.xml" to containerXml(),
                "OEBPS/content.opf" to opfXml(
                    manifest = """
                        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                        <item id="Chapter_0001.xhtml" href="Text/Chapter_0001.xhtml" media-type="application/xhtml+xml"/>
                    """.trimIndent(),
                    spine = """<itemref idref="Chapter_0001.xhtml"/>""",
                ),
                "OEBPS/toc.ncx" to ncxXml("""<navPoint><navLabel><text>第一章</text></navLabel><content src="Text/Chapter_0001.xhtml"/></navPoint>"""),
                "OEBPS/Text/Chapter_0001.xhtml" to xhtml("第一章", "这是正文的第一段，讲的是故事本身，而不是简介。"),
            ),
        )

        assertNull(extractEpubInsight(archive, fileName = "无名之书.epub"))
    }

    @Test
    fun onlyTheFrontMatterWindowIsEverOpened() {
        // A late chapter whose file name merely looks like an introduction must not be read.
        val archive = FakeBookArchive(
            mapOf(
                "META-INF/container.xml" to containerXml(),
                "OEBPS/content.opf" to opfXml(
                    manifest = """
                        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                        <item id="Chapter_0001.xhtml" href="Text/Chapter_0001.xhtml" media-type="application/xhtml+xml"/>
                        <item id="Chapter_0002.xhtml" href="Text/Chapter_0002.xhtml" media-type="application/xhtml+xml"/>
                        <item id="Chapter_0003.xhtml" href="Text/Chapter_0003.xhtml" media-type="application/xhtml+xml"/>
                        <item id="Chapter_0004.xhtml" href="Text/Chapter_0004.xhtml" media-type="application/xhtml+xml"/>
                        <item id="Chapter_0005.xhtml" href="Text/Chapter_0005.xhtml" media-type="application/xhtml+xml"/>
                        <item id="Chapter_0006.xhtml" href="Text/Chapter_0006.xhtml" media-type="application/xhtml+xml"/>
                        <item id="Chapter_0007.xhtml" href="Text/Chapter_0007.xhtml" media-type="application/xhtml+xml"/>
                        <item id="Chapter_0008.xhtml" href="Text/Chapter_0008.xhtml" media-type="application/xhtml+xml"/>
                        <item id="Chapter_0009.xhtml" href="Text/introduction-late.xhtml" media-type="application/xhtml+xml"/>
                    """.trimIndent(),
                    spine = (1..9).joinToString("\n") { index ->
                        val id = if (index == 9) "Chapter_0009.xhtml" else "Chapter_000$index.xhtml"
                        """<itemref idref="$id"/>"""
                    },
                ),
                "OEBPS/toc.ncx" to ncxXml(""),
                "OEBPS/Text/introduction-late.xhtml" to xhtml(
                    "第九十九章",
                    "这一章只是名字长得像简介，其实是正文里的一段故事内容而已。",
                ),
            ),
        )

        assertNull(extractEpubInsight(archive, fileName = null))
    }

    @Test
    fun opfInsightReadsSubjectsSeriesAndDescription() {
        val insight = parseEpubOpfInsight(
            """
            <package>
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:subject>悬疑, 推理</dc:subject>
                <dc:subject>FIC022000</dc:subject>
                <meta property="dcterms:subject">克苏鲁</meta>
                <meta name="calibre:series" content="侦探系列"/>
                <dc:description>一段足够长的简介文本，用来验证元数据里的简介能被读出来。</dc:description>
              </metadata>
            </package>
            """.trimIndent(),
        )

        assertEquals(listOf("悬疑", "推理", "克苏鲁"), insight.subjects)
        assertEquals("侦探系列", insight.series)
        assertEquals("一段足够长的简介文本，用来验证元数据里的简介能被读出来。", insight.description)
    }

    @Test
    fun productionNotesAreRejectedAsABlurb() {
        val html = xhtml(
            "制作说明",
            """
            本书采用某某的精校文本为底本，由某某进行封面设计、排版。制作完成，只做排版交流，请勿商业传播！
            注：为获得最佳阅读效果，请使用多看阅读打开，并在设置中将排版设为“原版”。
            """.trimIndent(),
        )

        assertNull(extractBlurbFromXhtml(html))
    }

    @Test
    fun aBlurbKeepsItsParagraphsAndDropsTheNoiseLines() {
        val html = xhtml(
            "",
            """
            蒸汽与机械的浪潮中，谁能触及非凡？
            本书由某某扫描录入，仅供交流，请勿传播！
            枪械，大炮，巨舰，飞空艇，差分机；魔药，占卜，诅咒。
            更多精彩，请关注公众号。
            """.trimIndent(),
        )

        val blurb = assertNotNull(extractBlurbFromXhtml(html))
        assertTrue(blurb.contains("蒸汽与机械的浪潮中"), blurb)
        assertTrue(blurb.contains("枪械，大炮"), blurb)
        assertTrue(!blurb.contains("公众号"), blurb)
        assertTrue(!blurb.contains("仅供交流"), blurb)
    }

    @Test
    fun aLongBlurbIsTruncatedAtAReadableBoundary() {
        val paragraph = "这是一段很长的简介文字。"
        val blurb = assertNotNull(cleanBlurbText(paragraph.repeat(400)))

        assertTrue(blurb.length <= 1200, "length=${blurb.length}")
        assertTrue(blurb.endsWith("。"), blurb.takeLast(8))
    }

    @Test
    fun aPlainTextMarkerStartsTheBlurbAndTheStoryEndsIt() {
        val text = """
            书名：某书
            作者：某人

            内容简介：
            蒸汽与机械的浪潮中，谁能触及非凡？历史和黑暗的迷雾里，又是谁在耳语？
            我从诡秘中醒来，睁眼看见这个世界。

            第一章 醒来
            这里是正文，不属于简介。
        """.trimIndent()

        val blurb = assertNotNull(extractMarkedBlurb(text))
        assertTrue(blurb.contains("蒸汽与机械的浪潮中"), blurb)
        assertTrue(!blurb.contains("这里是正文"), blurb)
    }

    @Test
    fun plainTextWithoutAMarkerOffersNoDescription() {
        val text = "第一章 醒来\n这里是正文，虽然也可能很像简介，但没有标记就不猜。"

        assertNull(extractMarkedBlurb(text))
    }

    @Test
    fun fileNamesGiveUpTheirGenreAndTheirAuthor() {
        assertEquals(
            listOf("奇幻"),
            filenameSubjects("【奇幻】诡秘之主 (爱潜水的乌贼).epub"),
        )
        assertEquals(listOf("都市"), filenameSubjects("[都市异能] 某书.txt"))
        assertEquals(emptyList(), filenameSubjects("诡秘之主 (爱潜水的乌贼).epub"))

        assertEquals("爱潜水的乌贼", filenameAuthorHint("诡秘之主 (爱潜水的乌贼).epub"))
        assertEquals("江南", filenameAuthorHint("龙族 - 江南.txt"))
        // The source suffix a downloaded file carries is not an author.
        assertEquals(
            "爱潜水的乌贼",
            filenameAuthorHint("诡秘之主 (爱潜水的乌贼) (z-library.sk, 1lib.sk, z-lib.sk).epub"),
        )
        assertNull(filenameAuthorHint("诡秘之主 (全8册).epub"))
        assertEquals("爱潜水的乌贼", filenameAuthorHint("诡秘之主 - 爱潜水的乌贼.epub"))
    }

    @Test
    fun plainTextInsightCombinesTheMarkerWithTheFileName() {
        val insight = assertNotNull(
            extractPlainTextInsight(
                fileName = "【仙侠】剑来 (烽火戏诸侯).txt",
                text = """
                    内容简介：
                    大千世界，无奇不有。这一段的文字足够长，可以被当成一段真正的简介来使用。

                    第一章 惊蛰
                    正文开始。
                """.trimIndent(),
            ),
        )

        assertEquals(BookInsightSource.TxtMarker, insight.descriptionSource)
        assertEquals(listOf("仙侠"), insight.subjects)
        assertEquals("烽火戏诸侯", insight.authorHint)
    }

    @Test
    fun theHeadingAndTheStatusLineAboveItAreNotPartOfTheDescription() {
        // A self-made book prints 「作者·状态·字数」 above a 内容简介 heading; neither belongs to the
        // description, which starts after the heading.
        val html = xhtml(
            "",
            """
            佛前献花·已完结·500万字
            内容简介：
            五浊恶世，地狱已空，厉鬼复苏，人间如狱。
            这个世界鬼出现了……那么神又在哪里？求神救世，可世上已无神，只有鬼。
            一张诡异的羊皮卷，一只窥视黑暗的眼睛，这是一个活下来的人经历的故事。
            """.trimIndent(),
        )

        val blurb = assertNotNull(extractBlurbFromXhtml(html))
        assertTrue(blurb.startsWith("五浊恶世"), blurb)
        assertTrue(!blurb.contains("内容简介"), blurb)
        assertTrue(!blurb.contains("已完结"), blurb)
        assertTrue(!blurb.contains("佛前献花"), blurb)
        assertTrue(blurb.contains("一张诡异的羊皮卷"), blurb)
    }

    @Test
    fun aHeadingThatSharesItsLineKeepsOnlyTheTextAfterIt() {
        val blurb = assertNotNull(
            cleanBlurbText("内容简介：五浊恶世，地狱已空，厉鬼复苏，人间如狱。这个世界鬼出现了。"),
        )

        assertTrue(blurb.startsWith("五浊恶世"), blurb)
        assertTrue(!blurb.contains("内容简介"), blurb)
    }

    @Test
    fun aStatusLineWithoutAHeadingIsDroppedToo() {
        val blurb = assertNotNull(
            cleanBlurbText(
                """
                佛前献花·已完结·500万字
                五浊恶世，地狱已空，厉鬼复苏，人间如狱。这个世界鬼出现了，那么神又在哪里？
                """.trimIndent(),
            ),
        )

        assertEquals("五浊恶世，地狱已空，厉鬼复苏，人间如狱。这个世界鬼出现了，那么神又在哪里？", blurb)
    }

    @Test
    fun aDescriptionBlockEndsAtTheNextSection() {
        val blurb = assertNotNull(
            cleanBlurbText(
                """
                内容简介：
                五浊恶世，地狱已空，厉鬼复苏，人间如狱。这个世界鬼出现了，那么神又在哪里？
                作者简介
                佛前献花，网络小说作者，代表作若干。
                """.trimIndent(),
            ),
        )

        assertTrue(blurb.startsWith("五浊恶世"), blurb)
        assertTrue(!blurb.contains("作者简介"), blurb)
        assertTrue(!blurb.contains("代表作"), blurb)
    }

    @Test
    fun aHeadingAloneIsNotADescription() {
        assertNull(cleanBlurbText("内容简介："))
        assertNull(cleanBlurbText("内容简介：\n作者：佛前献花"))
    }

    private fun bookArchive(opfMetadata: String = ""): BookArchive = FakeBookArchive(
        mapOf(
            "META-INF/container.xml" to containerXml(),
            "OEBPS/content.opf" to opfXml(
                manifest = """
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="Description.xhtml" href="Text/Description.xhtml" media-type="application/xhtml+xml"/>
                    <item id="Introduction.xhtml" href="Text/Introduction.xhtml" media-type="application/xhtml+xml"/>
                    <item id="Chapter_0001.xhtml" href="Text/Chapter_0001.xhtml" media-type="application/xhtml+xml"/>
                """.trimIndent(),
                spine = """
                    <itemref idref="Description.xhtml"/>
                    <itemref idref="Introduction.xhtml"/>
                    <itemref idref="Chapter_0001.xhtml"/>
                """.trimIndent(),
                metadata = opfMetadata,
            ),
            "OEBPS/toc.ncx" to ncxXml(
                """
                <navPoint><navLabel><text>制作说明</text></navLabel><content src="Text/Description.xhtml"/></navPoint>
                <navPoint><navLabel><text>内容简介</text></navLabel><content src="Text/Introduction.xhtml"/></navPoint>
                <navPoint><navLabel><text>第一部 小丑</text></navLabel><content src="Text/Chapter_0001.xhtml"/></navPoint>
                """.trimIndent(),
            ),
            "OEBPS/Text/Description.xhtml" to xhtml(
                "制作说明",
                "本书采用某某的精校文本为底本，由某某进行封面设计、排版。制作完成，只做排版交流，请勿商业传播！",
            ),
            "OEBPS/Text/Introduction.xhtml" to xhtml(
                "",
                """
                蒸汽与机械的浪潮中，谁能触及非凡？历史和黑暗的迷雾里，又是谁在耳语？我从诡秘中醒来，睁眼看见这个世界：
                枪械，大炮，巨舰，飞空艇，差分机；魔药，占卜，诅咒，倒吊人，封印物……光明依旧照耀，神秘从未远离，这是一段“愚者”的传说。
                """.trimIndent(),
            ),
            "OEBPS/Text/Chapter_0001.xhtml" to xhtml(
                "第一章",
                "这是正文的第一段，讲的是故事，不是简介，因此即使前面都没有找到简介也不该用它。",
            ),
        ),
    )
}

private class FakeBookArchive(private val entries: Map<String, String>) : BookArchive {
    override val entryNames: List<String> = entries.keys.toList()

    override fun readBytes(entryName: String): ByteArray? = entries[entryName]?.toByteArray()
}

private fun containerXml(): String = """
    <?xml version="1.0" encoding="UTF-8"?>
    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
        <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
        </rootfiles>
    </container>
""".trimIndent()

private fun opfXml(
    manifest: String,
    spine: String,
    metadata: String = "",
): String = """
    <?xml version="1.0" encoding="utf-8"?>
    <package version="2.0" xmlns="http://www.idpf.org/2007/opf">
      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
        <dc:title>诡秘之主</dc:title>
        <dc:creator opf:role="aut">爱潜水的乌贼</dc:creator>
        <dc:language>zh-CN</dc:language>
    $metadata
      </metadata>
      <manifest>
    $manifest
      </manifest>
      <spine toc="ncx">
    $spine
      </spine>
    </package>
""".trimIndent()

private fun ncxXml(navPoints: String): String = """
    <?xml version="1.0" encoding="utf-8"?>
    <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
      <navMap>
    $navPoints
      </navMap>
    </ncx>
""".trimIndent()

private fun xhtml(title: String, body: String): String = """
    <?xml version="1.0" encoding="utf-8"?>
    <html xmlns="http://www.w3.org/1999/xhtml">
    <head><title>$title</title></head>
    <body>
    ${body.lines().joinToString("\n") { line -> if (line.isBlank()) "" else "<p>$line</p>" }}
    </body>
    </html>
""".trimIndent()
