package com.example.toread.importing

import com.example.toread.categories.MaximumCategoryLength
import com.example.toread.categories.builtInCategoryEntries

/**
 * The insight a file name and a plain-text book can offer.
 *
 * A TXT has no package metadata at all, so the only local signals are the bracketed genre words a
 * reader's own file names carry (`【玄幻】书名 - 作者.txt`) and a 「内容简介」 marker inside the text.
 * Neither is invented: when a file has no marker and no bracketed genre, nothing is reported.
 */

/** Headings that introduce a description; the longest spelling wins, so 内容简介 beats 简介. */
internal val BlurbMarkerTitles = listOf(
    "内容简介",
    "作品简介",
    "书籍简介",
    "图书简介",
    "内容提要",
    "内容概述",
    "内容介绍",
    "故事简介",
    "内容摘要",
    "简介",
    "文案",
)

/** A description block ends where the story starts or the next metadata section begins. */
internal val BlurbStopPatterns = listOf(
    Regex("""^第\s*[0-9一二三四五六七八九十百千零两]+\s*[章节卷回部篇]"""),
    Regex("""^(正文|序章|楔子|尾声|番外)"""),
    Regex("""^(作者简介|作者的话|作者感言|目录|标签|分类|最新章节|上一篇|下一篇|返回目录|加入书签)"""),
    Regex("""^(作者|字数|状态|类型|来源|标签|分类|更新时间)\s*[:：]"""),
)

private const val MaximumBlurbMarkerLines = 40

/**
 * Reads the block that follows a standalone 「内容简介」/「作品简介」 marker line.
 *
 * The marker has to be a line of its own (optionally followed by a colon): a paragraph that merely
 * mentions the word 简介 must not start a description block.
 */
internal fun extractMarkedBlurb(text: String): String? {
    val lines = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .map { line -> line.replace(Regex("""[ \t\u3000]+"""), " ").trim() }
    if (lines.isEmpty()) return null

    val markerIndex = lines.indexOfFirst { line -> line.matchesBlurbMarker() }
    if (markerIndex < 0) return null

    val collected = mutableListOf<String>()
    var collectedLength = 0
    var index = markerIndex + 1
    while (index < lines.size && collected.size < MaximumBlurbMarkerLines) {
        val line = lines[index]
        if (line.startsBlurbSectionBreak()) break
        if (line.matchesBlurbMarker()) break
        if (line.isNotBlank()) {
            collected += line
            collectedLength += line.length
            if (collectedLength >= MaximumBlurbLength) break
        } else if (collectedLength >= MinimumBlurbLength) {
            break
        }
        index++
    }
    if (collected.isEmpty()) return null
    return cleanBlurbText(collected.joinToString("\n"))
}

/** True for a 「内容简介」/「作品简介」 heading line, with or without a trailing colon or brackets. */
internal fun String.matchesBlurbMarker(): Boolean {
    val clean = trimBlurbDecoration()
    return BlurbMarkerTitles.any { marker ->
        clean.equals(marker, ignoreCase = true) ||
            clean.startsWith("$marker：") ||
            clean.startsWith("$marker:")
    }
}

/** The description itself when the heading and the text share a line: `内容简介：正文…`. */
internal fun String.afterBlurbMarker(): String {
    val clean = trimBlurbDecoration()
    val marker = BlurbMarkerTitles.firstOrNull { clean.startsWith(it, ignoreCase = true) } ?: return ""
    return clean.substring(marker.length).trimStart('：', ':', ' ', '\u3000')
}

/** True where a different metadata section starts, so the description block has ended. */
internal fun String.startsBlurbSectionBreak(): Boolean =
    BlurbStopPatterns.any { pattern -> pattern.containsMatchIn(this) }

private fun String.trimBlurbDecoration(): String =
    trim().trimStart('【', '[', '「', '《').trimEnd('】', ']', '」', '》').trim()

private val BracketPairs = listOf('【' to '】', '[' to ']', '（' to '）', '(' to ')', '「' to '」', '《' to '》')

/** The genre words the reader's own file name spells out in brackets, mapped to known categories. */
internal fun filenameSubjects(fileName: String?): List<String> {
    val base = fileName?.toBookNameWithoutExtension() ?: return emptyList()
    return bracketedSegments(base)
        .mapNotNull { segment -> segment.toKnownCategory() }
        .distinct()
        .take(MaximumInsightTags)
}

private fun String.toKnownCategory(): String? {
    val clean = trim()
    if (clean.length < 2 || clean.length > MaximumCategoryLength) return null
    if (clean.any { it.isDigit() }) return null
    val entry = builtInCategoryEntries.firstOrNull { rule ->
        clean.equals(rule.keyword, ignoreCase = true) || clean.equals(rule.category, ignoreCase = true)
    } ?: builtInCategoryEntries.firstOrNull { rule ->
        rule.keyword.length >= 2 && clean.contains(rule.keyword, ignoreCase = true)
    }
    return entry?.category
}

/**
 * The author a file name spells out, e.g. `诡秘之主 (爱潜水的乌贼).epub` or `龙族 - 江南.txt`.
 *
 * A source suffix such as `(z-library.sk, 1lib.sk)` never passes: a domain, a file-count bracket
 * (`(全8册)`) or a format word is not a person.
 */
internal fun filenameAuthorHint(fileName: String?): String? {
    val base = fileName?.toBookNameWithoutExtension() ?: return null
    val bracketed = bracketedSegments(base)
        .filter { it.isPlausibleAuthorName() }
        .lastOrNull()
    if (bracketed != null) return bracketed

    val separatorTail = listOf(" - ", " － ", " – ", " _ ").firstNotNullOfOrNull { separator ->
        base.substringAfterLast(separator, missingDelimiterValue = "")
            .takeIf { it.isNotBlank() && it != base }
    }
    return separatorTail
        ?.removeSurrounding("(", ")")
        ?.removeSurrounding("（", "）")
        ?.trim()
        ?.takeIf { it.isPlausibleAuthorName() }
}

private fun String.isPlausibleAuthorName(): Boolean {
    val clean = trim()
    if (clean.length < 2 || clean.length > 16) return false
    if (clean.any { it.isDigit() }) return false
    if (clean.any { it in "./\\@:：,，;；|" }) return false
    if (clean.none { it.code > 0x2e80 }) return false
    if (AuthorNoiseWords.any { clean.contains(it) }) return false
    return true
}

private val AuthorNoiseWords = listOf(
    "册",
    "合集",
    "全集",
    "完结",
    "精校",
    "校对",
    "扫描",
    "排版",
    "出版",
    "中文",
    "简体",
    "繁体",
    "修订",
    "插图",
    "第",
    "卷",
    "典藏",
    "epub",
    "txt",
    "pdf",
    "z-lib",
    "library",
    "扫描版",
)

internal fun String?.toBookNameWithoutExtension(): String? {
    val name = this
        ?.substringAfterLast('\\')
        ?.substringAfterLast('/')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return name.substringBeforeLast('.', missingDelimiterValue = name)
}

private fun bracketedSegments(value: String): List<String> {
    val segments = mutableListOf<String>()
    var index = 0
    while (index < value.length) {
        val open = value[index]
        val close = BracketPairs.firstOrNull { it.first == open }?.second
        if (close == null) {
            index++
            continue
        }
        val end = value.indexOf(close, startIndex = index + 1)
        if (end < 0) {
            index++
            continue
        }
        segments += value.substring(index + 1, end)
        index = end + 1
    }
    return segments
}
