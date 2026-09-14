package com.example.toread.categories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookCategoryLexiconTest {
    @Test
    fun builtInDictionarySuggestsFromTitleAndDescription() {
        val suggestions = suggestBookCategories(
            BookCategoryInput(
                title = "诡秘之主",
                description = "蒸汽与机械的浪潮中，谁能触及非凡？魔药，占卜，诅咒，封印物……",
            ),
        )

        assertEquals("奇幻", suggestions.first().category)
        assertTrue(suggestions.size <= 4, "suggestions=$suggestions")
        assertTrue(suggestions.all { it.source == BookCategorySource.BuiltInDictionary })
    }

    @Test
    fun theReadersOwnRuleOutranksTheBuiltInDictionary() {
        val suggestions = suggestBookCategories(
            input = BookCategoryInput(title = "蒸汽朋克手记", description = "蒸汽与齿轮"),
            userEntries = listOf(BookCategoryEntry(keyword = "蒸汽", category = "蒸汽朋克")),
        )

        assertEquals("蒸汽朋克", suggestions.first().category)
        assertEquals(BookCategorySource.UserDictionary, suggestions.first().source)
        assertTrue(suggestions.first().reason.contains("你的词典"), suggestions.first().reason)
    }

    @Test
    fun aRememberedAuthorWinsOverEveryKeyword() {
        val suggestions = suggestBookCategories(
            input = BookCategoryInput(
                title = "长夜余火",
                author = "爱潜水的乌贼",
                description = "魔药与封印物",
            ),
            memoryEntries = listOf(BookCategoryEntry(keyword = "爱潜水的乌贼", category = "克苏鲁")),
        )

        assertEquals("克苏鲁", suggestions.first().category)
        assertEquals(BookCategorySource.UserMemory, suggestions.first().source)
        assertTrue(suggestions.first().reason.contains("作者"), suggestions.first().reason)
    }

    @Test
    fun aCategoryAlreadyInTheTagsIsNeverSuggestedBack() {
        val suggestions = suggestBookCategories(
            BookCategoryInput(title = "诡秘之主", tags = "奇幻, 连载", description = "魔药与封印物"),
        )

        assertFalse(suggestions.any { it.category == "奇幻" }, "suggestions=$suggestions")
    }

    @Test
    fun suggestionsAreRankedAndCapped() {
        val suggestions = suggestBookCategories(
            input = BookCategoryInput(
                title = "星际修仙",
                description = "机甲、宇宙、元婴、渡劫、宗门、飞升",
            ),
            limit = 2,
        )

        assertEquals(2, suggestions.size)
        assertTrue(suggestions[0].score >= suggestions[1].score)
    }

    @Test
    fun dictionaryLinesRoundTripAndMalformedOnesAreDropped() {
        val entries = listOf(
            BookCategoryEntry(keyword = "诡秘", category = "克苏鲁"),
            BookCategoryEntry(keyword = "蒸汽", category = "蒸汽朋克"),
        )

        val encoded = encodeCategoryEntries(entries)
        assertEquals(entries, parseCategoryEntries(encoded))
        assertTrue(parseCategoryEntries("没有分隔符的一行").isEmpty())
        assertTrue(parseCategoryEntries("=缺关键词").isEmpty())
        assertTrue(parseCategoryEntries("短=x").isEmpty())
        assertTrue(parseCategoryEntries(null).isEmpty())
    }

    @Test
    fun savingACategoryRemembersTheAuthorAndTheSeries() {
        val input = BookCategoryInput(title = "诡秘之主", author = "爱潜水的乌贼", series = "诡秘世界")

        val memory = recordCategoryMemory(
            existing = emptyList(),
            input = input,
            savedTags = "奇幻, 克苏鲁",
        )

        assertTrue(memory.contains(BookCategoryEntry("爱潜水的乌贼", "奇幻")), "memory=$memory")
        assertTrue(memory.contains(BookCategoryEntry("爱潜水的乌贼", "克苏鲁")), "memory=$memory")
        assertTrue(memory.contains(BookCategoryEntry("诡秘世界", "奇幻")), "memory=$memory")
        assertEquals(4, memory.size, "memory=$memory")
    }

    @Test
    fun theLatestChoiceForAnAuthorReplacesTheEarlierOne() {
        val input = BookCategoryInput(title = "诡秘之主", author = "爱潜水的乌贼", series = "诡秘世界")

        val memory = recordCategoryMemory(
            existing = recordCategoryMemory(emptyList(), input, "奇幻, 克苏鲁"),
            input = input,
            savedTags = "奇幻",
        )

        assertTrue(memory.contains(BookCategoryEntry("爱潜水的乌贼", "奇幻")), "memory=$memory")
        assertFalse(
            memory.contains(BookCategoryEntry("爱潜水的乌贼", "克苏鲁")),
            "a corrected choice must not leave the old one behind: $memory",
        )
        assertEquals(2, memory.size, "memory=$memory")
    }

    @Test
    fun savingNothingNewKeepsTheMemoryAsItIs() {
        val input = BookCategoryInput(title = "无名", author = null, series = null)

        assertTrue(recordCategoryMemory(emptyList(), input, "奇幻").isEmpty())
        assertEquals(
            listOf(BookCategoryEntry("某作者", "奇幻")),
            recordCategoryMemory(listOf(BookCategoryEntry("某作者", "奇幻")), input, "奇幻"),
        )
    }

    @Test
    fun removingAMemoryRuleKeepsTheOthers() {
        val memory = listOf(
            BookCategoryEntry("爱潜水的乌贼", "奇幻"),
            BookCategoryEntry("爱潜水的乌贼", "克苏鲁"),
        )

        assertEquals(
            listOf(BookCategoryEntry("爱潜水的乌贼", "克苏鲁")),
            removeCategoryMemory(memory, BookCategoryEntry("爱潜水的乌贼", "奇幻")),
        )
    }

    @Test
    fun bodyTextEvidenceNeedsRepetitionAndPrefersTheReadersOwnRules() {
        // One passing mention is not a genre.
        assertTrue(bodyTextCategoryEvidence("他随手翻开一本武侠小说。").isEmpty())

        // A keyword that repeats in the narrative does count.
        val evidence = bodyTextCategoryEvidence(
            "他踏入宗门，宗门的长老看着他。宗门规矩森严，谁也不敢违背宗门的意志。",
        )
        assertEquals("仙侠", evidence.first().category)
        assertEquals("宗门", evidence.first().keyword)
        assertTrue(evidence.first().score > 0, "evidence=$evidence")

        // The reader's own rule scores higher than the shipped keyword it overlaps.
        val userRule = bodyTextCategoryEvidence(
            text = "宗门 宗门 宗门",
            userEntries = listOf(BookCategoryEntry(keyword = "宗门", category = "修仙流")),
        )
        assertEquals("修仙流", userRule.first().category)
        assertTrue(userRule.first().score > evidence.first().score, "evidence=$userRule")
    }

    @Test
    fun aConfidentBodyCategoryNeedsToBeStrictlyAhead() {
        val one = bodyTextCategoryEvidence("宗门 宗门 宗门 宗门 宗门").map { it.category }
        assertEquals(listOf("仙侠"), one)
        assertNotNull(confidentBodyCategory(bodyTextCategoryEvidence("宗门 宗门 宗门 宗门 宗门")))

        // Two categories with the same repetition leave the answer open.
        val tie = bodyTextCategoryEvidence("宗门 宗门 宗门 宗门 宗门 机甲 机甲 机甲 机甲 机甲")
        assertNull(confidentBodyCategory(tie))
    }

    @Test
    fun aBodyCategoryCanBeTheLastResortForAutoCategoryTags() {
        val evidence = bodyTextCategoryEvidence("宗门 宗门 宗门 宗门 宗门")

        // Title and blurb say nothing, the book declares nothing → the body decides.
        assertEquals(
            listOf("仙侠"),
            autoCategoryTags(
                input = BookCategoryInput(title = "无名之书", description = "一段平静的日常。"),
                declaredSubjects = emptyList(),
                bodyEvidence = evidence,
            ),
        )
        // A declared category still wins over the body.
        assertEquals(
            listOf("悬疑"),
            autoCategoryTags(
                input = BookCategoryInput(title = "无名之书"),
                declaredSubjects = listOf("悬疑"),
                bodyEvidence = evidence,
            ),
        )
    }

    @Test
    fun appendingATagNeverDuplicatesIt() {
        assertEquals("悬疑, 仙侠", appendTag("悬疑", "仙侠"))
        assertEquals("悬疑, 仙侠", appendTag("悬疑, 仙侠", " 仙侠 "))
        assertEquals("悬疑, 仙侠", appendTag(" 悬疑 , ", "仙侠"))
        assertEquals("悬疑", appendTag("悬疑", " "))
    }
}
