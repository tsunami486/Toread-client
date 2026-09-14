package com.example.toread.importing

import com.example.toread.categories.MaximumCategoryLength

/**
 * EPUB package reading for the import-time insight pass.
 *
 * This is deliberately smaller than the reader's publication index: the importer only needs the
 * container, the OPF metadata, the navigation labels and — for at most a handful of front-matter
 * documents — one XHTML file. No spine chapter is ever parsed here.
 */

internal data class EpubInsightManifestItem(
    val id: String,
    val href: String,
    val mediaType: String?,
    val properties: String?,
)

internal data class EpubInsightPackage(
    val basePath: String,
    val opfXml: String,
    val manifestById: Map<String, EpubInsightManifestItem>,
    val spineIds: List<String>,
) {
    /**
     * Manifest items keyed by normalized package-relative path.
     *
     * Built once: a novel has thousands of spine entries, and resolving every manifest href for
     * every one of them would make the lookup quadratic.
     */
    private val manifestByPathKey: Map<String, EpubInsightManifestItem> by lazy {
        manifestById.values.associateBy { item ->
            resolveInsightPath(basePath, item.href).toInsightPathKey()
        }
    }

    /** Manifest hrefs in spine order, normalized to package-relative paths. */
    fun spineHrefs(): List<String> = spineIds.mapNotNull { id ->
        manifestById[id]?.let { item -> resolveInsightPath(basePath, item.href) }
    }

    fun manifestItemForPath(path: String): EpubInsightManifestItem? =
        manifestByPathKey[path.toInsightPathKey()]
}

internal fun readEpubInsightPackage(archive: BookArchive): EpubInsightPackage? {
    val containerEntry = archive.findEntry("META-INF/container.xml") ?: return null
    val containerXml = archive.readBytes(containerEntry)?.decodeToString() ?: return null
    val rootFileAttributes = Regex("""(?is)<rootfile\b([^>]*)>""")
        .find(containerXml)
        ?.groupValues
        ?.getOrNull(1)
        ?: return null
    val opfPath = parseXmlAttributes(rootFileAttributes)["full-path"]
        ?.let(::decodeXmlEntities)
        ?.let(::percentDecodeInsightPath)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val opfEntry = archive.findEntry(opfPath) ?: return null
    val opfXml = archive.readBytes(opfEntry)?.decodeToString() ?: return null
    val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        .orEmpty()

    val manifestById = Regex("""(?is)<item\b([^>]*)/?>""")
        .findAll(opfXml)
        .mapNotNull { match ->
            val attributes = parseXmlAttributes(match.groupValues[1])
            val id = attributes["id"] ?: return@mapNotNull null
            val href = attributes["href"]?.let(::decodeXmlEntities) ?: return@mapNotNull null
            id to EpubInsightManifestItem(
                id = id,
                href = href,
                mediaType = attributes["media-type"],
                properties = attributes["properties"],
            )
        }
        .toMap()

    val spineIds = Regex("""(?is)<itemref\b([^>]*)/?>""")
        .findAll(opfXml)
        .mapNotNull { match ->
            parseXmlAttributes(match.groupValues[1])["idref"]?.let(::decodeXmlEntities)
        }
        .toList()

    return EpubInsightPackage(
        basePath = basePath,
        opfXml = opfXml,
        manifestById = manifestById,
        spineIds = spineIds,
    )
}

internal fun BookArchive.findEntry(path: String): String? {
    if (path.isBlank()) return null
    entryNames.firstOrNull { it == path }?.let { return it }
    val key = path.toInsightPathKey()
    return entryNames.firstOrNull { it.toInsightPathKey() == key }
}

internal data class EpubOpfInsight(
    val description: String?,
    val subjects: List<String>,
    val series: String?,
)

/** One `<meta>` element: EPUB2 writes `content="..."`, EPUB3 writes the value as element text. */
private data class EpubMetaEntry(
    val attributes: Map<String, String>,
    val text: String?,
)

/**
 * Reads the publication's own metadata: `dc:description`, `dc:subject` (EPUB2 and the EPUB3
 * `dcterms:subject` spelling), the calibre series/tags metas and a plain `keywords` meta.
 */
internal fun parseEpubOpfInsight(opfXml: String): EpubOpfInsight {
    val metadataXml = Regex("""(?is)<(?:[A-Za-z_][\w.-]*:)?metadata\b[^>]*>(.*?)</(?:[A-Za-z_][\w.-]*:)?metadata>""")
        .find(opfXml)
        ?.groupValues
        ?.getOrNull(1)
        ?: opfXml

    val metaEntries = Regex("""(?is)<meta\b([^>]*?)(?:/>|>(.*?)</meta>|>)""")
        .findAll(metadataXml)
        .map { match ->
            EpubMetaEntry(
                attributes = parseXmlAttributes(match.groupValues[1]),
                text = match.groupValues.getOrNull(2)?.toXmlTextValue(),
            )
        }
        .toList()

    fun metaValuesFor(names: Set<String>): List<String> =
        metaEntries.mapNotNull { entry ->
            val property = (entry.attributes["property"] ?: entry.attributes["name"] ?: return@mapNotNull null)
                .substringAfter(':')
                .lowercase()
            if (property !in names) return@mapNotNull null
            val value = entry.attributes["content"]?.let(::decodeXmlEntities) ?: entry.text
            value?.takeIf { it.isNotBlank() }
        }

    val description = (
        simpleTagValues(metadataXml, "description") + metaValuesFor(setOf("description"))
        )
        .firstOrNull { it.length >= MinimumBlurbLength }
        ?.let { cleanBlurbText(it) }

    val subjects = (
        simpleTagValues(metadataXml, "subject") +
            metaValuesFor(setOf("subject", "dcterms.subject"))
        )
        .flatMap(::splitSubjectValue)
        .map { it.trim() }
        .filter { it.isNotBlank() && it.length <= MaximumCategoryLength }
        .filterNot(::isSubjectCode)
        .distinct()

    val series = metaValuesFor(setOf("series", "belongs-to-collection")).firstOrNull()
        ?: simpleTagValues(metadataXml, "series").firstOrNull()

    return EpubOpfInsight(
        description = description,
        subjects = subjects,
        series = series?.trim()?.takeIf { it.isNotBlank() },
    )
}

/** `dc:subject` is sometimes one comma-separated line and sometimes the calibre `a, b` spelling. */
private fun splitSubjectValue(value: String): List<String> =
    value.split(',', '，', ';', '；', '/', '|')

/** Drops BISAC-style codes such as `FIC009000`, which are useless as a reader-facing category. */
private fun isSubjectCode(value: String): Boolean {
    val code = value.trim()
    if (code.length < 4) return false
    if (!code.all { it.isLetterOrDigit() }) return false
    val letters = code.count { it.isLetter() }
    val digits = code.count { it.isDigit() }
    return letters >= 2 && digits >= 3
}

private fun simpleTagValues(xml: String, localName: String): List<String> =
    Regex("""(?is)<(?:[A-Za-z_][\w.-]*:)?$localName\b[^>]*>(.*?)</(?:[A-Za-z_][\w.-]*:)?$localName>""")
        .findAll(xml)
        .mapNotNull { match -> match.groupValues[1].toXmlTextValue() }
        .toList()

/**
 * Every navigation label of the publication, keyed by the normalized document path it points at.
 * EPUB2 NCX (`navLabel`/`content src`) and EPUB3 navigation documents (`<a href>`) are both read,
 * starting with the package's own `toc`/`nav` declarations.
 */
internal fun epubNavigationTitles(
    archive: BookArchive,
    packageDocument: EpubInsightPackage,
): Map<String, String> {
    val titles = mutableMapOf<String, String>()

    fun register(href: String, title: String) {
        val clean = title.toXmlTextValue() ?: return
        if (clean.isBlank()) return
        val path = resolveInsightPath(packageDocument.basePath, href)
        if (path.isBlank()) return
        titles.putIfAbsent(path.toInsightPathKey(), clean)
    }

    val ncxItems = packageDocument.manifestById.values.filter { item ->
        item.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) ||
            item.href.substringAfterLast('.').equals("ncx", ignoreCase = true)
    }
    val navItems = packageDocument.manifestById.values.filter { item ->
        item.properties
            ?.split(Regex("""\s+"""))
            ?.any { it.equals("nav", ignoreCase = true) } == true
    }

    (ncxItems + navItems).forEach { item ->
        val entry = archive.findEntry(resolveInsightPath(packageDocument.basePath, item.href))
            ?: return@forEach
        val xml = archive.readBytes(entry)?.decodeToString() ?: return@forEach
        if (item in ncxItems) {
            Regex("""(?is)<navPoint\b(.*?)</navPoint>""").findAll(xml).forEach { navPoint ->
                val block = navPoint.value
                val label = Regex("""(?is)<navLabel\b[^>]*>(.*?)</navLabel>""")
                    .find(block)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let { inner ->
                        Regex("""(?is)<text\b[^>]*>(.*?)</text>""")
                            .find(inner)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?: inner
                    }
                val source = Regex("""(?is)<content\b([^>]*)/?>""")
                    .find(block)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::parseXmlAttributes)
                    ?.get("src")
                    ?.let(::decodeXmlEntities)
                if (label != null && source != null) {
                    register(href = source.substringBefore('#'), title = label)
                }
            }
        } else {
            Regex("""(?is)<a\b([^>]*)>(.*?)</a>""").findAll(xml).forEach { anchor ->
                val href = parseXmlAttributes(anchor.groupValues[1])["href"]
                    ?.let(::decodeXmlEntities)
                    ?.substringBefore('#')
                    ?: return@forEach
                register(href = href, title = anchor.groupValues[2])
            }
        }
    }
    return titles
}

internal data class EpubFrontMatterDescription(
    val text: String,
    val origin: String,
    val href: String,
)

private val DescriptionTitleKeywords = listOf(
    "内容简介",
    "作品简介",
    "书籍简介",
    "图书简介",
    "内容提要",
    "内容概述",
    "内容介绍",
    "内容摘要",
    "故事简介",
    "简介",
    "摘要",
    "文案",
)

private val DescriptionFileHints = listOf(
    "introduction",
    "description",
    "intro",
    "abstract",
    "summary",
    "blurb",
    "jianjie",
    "neirong",
)

/** Front matter that is never a book description, however promising its file name looks. */
private val ExcludedTitleKeywords = listOf(
    "制作说明",
    "制作",
    "声明",
    "版权",
    "目录",
    "封面",
    "公众号",
    "前言",
    "序言",
    "自序",
    "后记",
    "附录",
    "作者简介",
)

/** Only this many front-matter documents are ever opened before the search gives up. */
private const val MaximumFrontMatterCandidates = 4

/**
 * Finds and reads the publication's own 「内容简介」 page.
 *
 * The navigation label wins over the file name: a Doukan-style book happily names a 制作说明 page
 * `Description.xhtml`, while its real blurb sits in `Introduction.xhtml` under the label 内容简介.
 */
internal fun epubFrontMatterDescription(
    archive: BookArchive,
    packageDocument: EpubInsightPackage,
    navigationTitles: Map<String, String>,
): EpubFrontMatterDescription? {
    val spineHrefs = packageDocument.spineHrefs()
    data class Candidate(val href: String, val score: Int, val origin: String)

    val candidates = spineHrefs.mapIndexedNotNull { index, href ->
        val item = packageDocument.manifestItemForPath(href) ?: return@mapIndexedNotNull null
        if (item.mediaType?.contains("xhtml", ignoreCase = true) != true &&
            item.mediaType?.contains("html", ignoreCase = true) != true
        ) {
            return@mapIndexedNotNull null
        }
        if (item.properties?.split(Regex("""\s+"""))?.any { it.equals("nav", ignoreCase = true) } == true) {
            return@mapIndexedNotNull null
        }
        val title = navigationTitles[href.toInsightPathKey()]
        if (title != null && ExcludedTitleKeywords.any { title.contains(it) }) {
            return@mapIndexedNotNull null
        }
        val titleMatch = title?.let { label ->
            DescriptionTitleKeywords.firstOrNull { label.contains(it) }
        }
        val fileHint = DescriptionFileHints.firstOrNull { hint ->
            href.substringAfterLast('/').contains(hint, ignoreCase = true) ||
                item.id.contains(hint, ignoreCase = true)
        }
        // A file-name hint only counts in the front matter; a label is trusted anywhere.
        if (titleMatch == null && (fileHint == null || index >= MaximumFrontMatterSpineIndex)) {
            return@mapIndexedNotNull null
        }
        val score = (if (titleMatch != null) 100 else 0) + (if (fileHint != null) 50 else 0) - index
        val origin = titleMatch?.let { "「$it」页" } ?: "前置页「${href.substringAfterLast('/')}」"
        Candidate(href = href, score = score, origin = origin)
    }

    return candidates
        .sortedByDescending { it.score }
        .take(MaximumFrontMatterCandidates)
        .firstNotNullOfOrNull { candidate ->
            val entry = archive.findEntry(candidate.href) ?: return@firstNotNullOfOrNull null
            val html = archive.readBytes(entry)?.decodeToString() ?: return@firstNotNullOfOrNull null
            val text = extractBlurbFromXhtml(html) ?: return@firstNotNullOfOrNull null
            EpubFrontMatterDescription(
                text = text,
                origin = candidate.origin,
                href = candidate.href,
            )
        }
}

/** A file-name hint is only believed this early in the spine; after that the book has started. */
private const val MaximumFrontMatterSpineIndex = 8

/**
 * Turns one XHTML document into a description, or into `null` when it is not one.
 *
 * Production notes, 公众号 pages and 求订阅 blocks are dropped line by line instead of being handed
 * to the reader as a blurb, and a document that is mostly such lines is rejected outright.
 */
internal fun extractBlurbFromXhtml(html: String): String? {
    val withoutScripts = html
        .replace(Regex("""(?is)<script\b.*?</script>"""), " ")
        .replace(Regex("""(?is)<style\b.*?</style>"""), " ")
        .replace(Regex("""(?is)<!--.*?-->"""), " ")
    val body = Regex("""(?is)<body\b[^>]*>(.*?)</body>""")
        .find(withoutScripts)
        ?.groupValues
        ?.getOrNull(1)
        ?: withoutScripts
    val withBreaks = body
        .replace(Regex("""(?is)<br\b[^>]*/?>"""), "\n")
        .replace(Regex("""(?is)</(p|div|h[1-6]|li|tr|section|article|blockquote)\s*>"""), "\n")
        .replace(Regex("""(?is)<[^>]+>"""), " ")
        .let(::decodeXmlEntities)
    return cleanBlurbText(withBreaks)
}

private val BlurbNoiseKeywords = listOf(
    "制作说明",
    "制作组",
    "校对",
    "精校",
    "排版",
    "底本",
    "录入",
    "整理制作",
    "请勿",
    "仅供",
    "交流",
    "公众号",
    "微信号",
    "qq群",
    "书友群",
    "求订阅",
    "求月票",
    "求推荐票",
    "求收藏",
    "更新时间",
    "本书由",
    "转载",
    "免责",
    "版权",
    "侵删",
    "更多精彩",
    "电子书",
    "多看阅读",
    "阅读效果",
    "手机阅读",
    "扫描",
    "http://",
    "https://",
    "www.",
    "注：为获得最佳",
    // The author/status/word-count line a self-made book prints above its blurb, e.g.
    // 「佛前献花·已完结·500万字」.
    "已完结",
    "连载中",
    "万字",
    "总字数",
)

/**
 * Cleans already-tag-stripped text into a description: drops the noise lines, keeps the reading
 * order, rejects documents that are mostly noise and caps the result at [MaximumBlurbLength].
 *
 * A 「内容简介：」 heading is a heading, not description text: everything up to and including it —
 * the author, status and word-count line a self-made book prints above it — is dropped, and the
 * block ends where the next metadata section starts.
 */
internal fun cleanBlurbText(raw: String): String? {
    val lines = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace('\u00a0', ' ')
        .split('\n')
        .map { line -> line.replace(Regex("""\s+"""), " ").trim() }
        .filter { it.isNotBlank() }
    if (lines.isEmpty()) return null

    val markerIndex = lines.indexOfFirst { line -> line.matchesBlurbMarker() }
    val body = when {
        markerIndex < 0 -> lines
        else -> {
            // `内容简介：正文…` on one line keeps the text that follows the heading.
            val remainder = lines[markerIndex].afterBlurbMarker()
            if (remainder.isNotBlank()) {
                listOf(remainder) + lines.drop(markerIndex + 1)
            } else {
                lines.drop(markerIndex + 1)
            }
        }
    }
    val descriptionLines = body.takeWhile { line -> !line.startsBlurbSectionBreak() }
    if (descriptionLines.isEmpty()) return null

    val kept = descriptionLines.filterNot { it.isBlurbNoiseLine() }
    val text = kept.joinToString("\n").trim()
    if (text.length < MinimumBlurbLength) return null
    if (kept.size * 2 < descriptionLines.size && text.length < MinimumBlurbLength * 3) return null
    return text.truncateAtReadableBoundary(MaximumBlurbLength)
}

private fun String.isBlurbNoiseLine(): Boolean {
    val lowered = lowercase()
    if (BlurbNoiseKeywords.any { lowered.contains(it) }) return true
    if (lowered.startsWith("ps:") || lowered.startsWith("ps：")) return true
    // A line without a single letter or CJK character carries no description.
    return none { it.isLetter() || it.code > 0x2e80 }
}

/** Cuts at the last sentence or line end inside the budget instead of mid-word. */
private fun String.truncateAtReadableBoundary(limit: Int): String {
    if (length <= limit) return this
    val head = substring(0, limit)
    val boundary = head.indexOfLast { it == '\n' || it == '。' || it == '！' || it == '？' || it == '.' }
    val cut = if (boundary >= limit / 2) head.substring(0, boundary + 1) else head
    return cut.trim()
}

internal fun resolveInsightPath(basePath: String, href: String): String {
    val cleanHref = percentDecodeInsightPath(href.substringBefore('#').substringBefore('?'))
    if (cleanHref.isBlank()) return ""
    val rawPath = if (basePath.isBlank()) cleanHref else "$basePath/$cleanHref"
    val parts = mutableListOf<String>()
    rawPath.split('/').forEach { part ->
        when {
            part.isBlank() || part == "." -> Unit
            part == ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
    }
    return parts.joinToString("/")
}

/** Normalizes a path so an href spelled differently from the archive entry still matches. */
internal fun String.toInsightPathKey(): String =
    percentDecodeInsightPath(this.substringBefore('#').substringBefore('?')).replace('\\', '/')

internal fun percentDecodeInsightPath(value: String): String {
    if ('%' !in value) return value
    val builder = StringBuilder(value.length)
    val bytes = mutableListOf<Byte>()

    fun flushBytes() {
        if (bytes.isEmpty()) return
        builder.append(bytes.toByteArray().decodeToString())
        bytes.clear()
    }

    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '%' && index + 2 < value.length) {
            val decoded = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (decoded != null) {
                bytes += decoded.toByte()
                index += 3
                continue
            }
        }
        flushBytes()
        builder.append(char)
        index++
    }
    flushBytes()
    return builder.toString()
}

private fun String?.toXmlTextValue(): String? =
    this
        ?.replace(Regex("""(?is)<[^>]+>"""), " ")
        ?.let(::decodeXmlEntities)
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
