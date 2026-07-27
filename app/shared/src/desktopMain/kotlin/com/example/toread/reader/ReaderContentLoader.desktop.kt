package com.example.toread.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipFile

private val EpubScriptTagRegex = Regex("""(?is)<script.*?</script>""")
private val EpubStyleTagRegex = Regex("""(?is)<style.*?</style>""")
private val EpubAnyTagRegex = Regex("""(?is)<[^>]+>""")
private val EpubBodyRegex = Regex("""(?is)<body\b[^>]*>(.*?)</body>""")
private val EpubTagNameRegex = Regex("""^</?\s*([A-Za-z0-9:_-]+)""")
private val EpubRootFileRegex = Regex("""(?is)<rootfile\b([^>]*)>""")
private val EpubManifestItemRegex = Regex("""(?is)<item\b([^>]*)/?>""")
private val EpubSpineRegex = Regex("""(?is)<spine\b([^>]*)>""")
private val EpubSpineItemRegex = Regex("""(?is)<itemref\b([^>]*)/?>""")
private val EpubNcxNavPointRegex = Regex("""(?is)<navPoint\b[^>]*>(.*?)</navPoint>""")
private val EpubNcxLabelRegex = Regex("""(?is)<navLabel\b[^>]*>.*?<text\b[^>]*>(.*?)</text>.*?</navLabel>""")
private val EpubNcxContentRegex = Regex("""(?is)<content\b([^>]*)/?>""")
private val EpubNavLinkRegex = Regex("""(?is)<a\b([^>]*)>(.*?)</a>""")
private val EpubWhitespaceRegex = Regex("""\s+""")
private val EpubXmlAttributeRegex = Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*("([^"]*)"|'([^']*)')""")
private val EpubHexEntityRegex = Regex("""&#x([0-9a-fA-F]+);""")
private val EpubDecimalEntityRegex = Regex("""&#(\d+);""")
private val EpubBreakTagRegex = Regex("""(?i)<br\s*/?>""")
private val EpubClosingBlockTagRegex = Regex("""(?i)</(p|div|section|article|h[1-6]|li|tr)>""")
private val EpubHorizontalWhitespaceRegex = Regex("""[ \t]+""")
private val EpubExtraLineBreakRegex = Regex("""\n{3,}""")
private val EpubPositiveNumberRegex = Regex("""([0-9.]+)""")
private val EpubClosingBlockTags = setOf("p", "div", "section", "article", "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr")

actual class ReaderContentLoader actual constructor() {
    private val publicationCacheLock = Any()

    @Volatile
    private var cachedPublication: IndexedEpubPublication? = null

    actual suspend fun load(filePath: String, format: String, title: String): ReaderDocument = withContext(Dispatchers.IO) {
        val normalizedFormat = format.lowercase()
        when (normalizedFormat) {
            "txt" -> ReaderDocument(title, normalizedFormat, decodeText(Files.readAllBytes(Paths.get(filePath))))
            "epub" -> loadFirstReadableEpubChapter(filePath, title)
            "pdf" -> ReaderDocument(title, normalizedFormat, "", "PDF 渲染将在后续接入，目前已完成本地导入与书架打开。")
            else -> ReaderDocument(title, normalizedFormat, "", "暂不支持阅读 .$format 文件。")
        }
    }

    actual suspend fun loadEpubPublicationIndex(
        filePath: String,
        title: String,
    ): EpubPublicationIndex? = withContext(Dispatchers.IO) {
        publicationFor(filePath, title)?.index
    }

    actual suspend fun loadEpubChapter(
        filePath: String,
        title: String,
        chapterOrd: Int,
    ): ReaderDocument? = withContext(Dispatchers.IO) {
        val publication = publicationFor(filePath, title) ?: return@withContext null
        val chapter = publication.chaptersByOrd[chapterOrd] ?: return@withContext null
        readEpubChapter(filePath, title, publication, chapter)
    }

    private suspend fun loadFirstReadableEpubChapter(
        filePath: String,
        title: String,
    ): ReaderDocument {
        val publication = publicationFor(filePath, title)
            ?: return unreadableEpubDocument(title, "无法读取 EPUB 目录结构。")
        for (chapter in publication.index.chapters) {
            currentCoroutineContext().ensureActive()
            readEpubChapter(
                filePath = filePath,
                title = title,
                publication = publication,
                chapter = publication.chaptersByOrd.getValue(chapter.ord),
            )?.let { return it }
        }
        return unreadableEpubDocument(title, "未能从 EPUB 中抽取正文文本。")
    }

    private suspend fun publicationFor(
        filePath: String,
        title: String,
    ): IndexedEpubPublication? {
        cachedPublication
            ?.takeIf { cached -> cached.filePath == filePath && cached.index.title == title }
            ?.let { return it }

        currentCoroutineContext().ensureActive()
        val loaded = ZipFile(filePath).use { zip ->
            buildEpubPublicationIndex(
                zip = zip,
                filePath = filePath,
                title = title,
            )
        } ?: return null
        currentCoroutineContext().ensureActive()
        return synchronized(publicationCacheLock) {
            cachedPublication
                ?.takeIf { cached -> cached.filePath == filePath && cached.index.title == title }
                ?: loaded.also { cachedPublication = it }
        }
    }
}

actual class EpubImageBytesLoader actual constructor() {
    actual suspend fun load(resource: EpubImageResource): ByteArray? = withContext(Dispatchers.IO) {
        ZipFile(resource.bookFilePath).use { zip ->
            val entry = zip.getEntry(resource.href) ?: return@withContext null
            zip.getInputStream(entry).use { it.readBytes() }
        }
    }
}

private data class ResolvedEpubSpineEntry(
    val ordinalHint: Int,
    val entryPath: String,
    val archiveEntryName: String,
    val navigationTitle: String?,
)

private data class IndexedEpubPublication(
    val filePath: String,
    val index: EpubPublicationIndex,
    val chaptersByOrd: Map<Int, ResolvedEpubSpineEntry>,
    val manifestByPath: Map<String, EpubManifestItem>,
)

private suspend fun buildEpubPublicationIndex(
    zip: ZipFile,
    filePath: String,
    title: String,
): IndexedEpubPublication? {
    currentCoroutineContext().ensureActive()
    val epubPackage = readEpubPackage(zip) ?: return null
    val chapterTitles = readEpubChapterTitles(zip, epubPackage)
    val manifestByPath = epubPackage.manifest.values.associateBy { item ->
        resolveEpubPath(epubPackage.basePath, item.href)
    }
    val chapters = mutableListOf<EpubChapterMetadata>()
    val chaptersByOrd = mutableMapOf<Int, ResolvedEpubSpineEntry>()
    epubPackage.spineIds.forEachIndexed { ordinal, id ->
        currentCoroutineContext().ensureActive()
        val item = epubPackage.manifest[id] ?: return@forEachIndexed
        val resolvedPath = resolveEpubPath(epubPackage.basePath, item.href)
        val resolvedEntry = zip.getEntry(resolvedPath)
        val archiveEntry = resolvedEntry ?: zip.getEntry(item.href) ?: return@forEachIndexed
        val canonicalPath = if (resolvedEntry != null) {
            resolvedPath
        } else {
            resolveEpubPath(basePath = "", href = archiveEntry.name)
        }
        val navigationTitle = (
            chapterTitles[canonicalPath]
                ?: chapterTitles[resolvedPath]
            )
            ?.takeIf { chapterTitle -> chapterTitle.isNotBlank() }
        val metadata = EpubChapterMetadata(
            ord = ordinal,
            href = canonicalPath,
            title = navigationTitle ?: "第 ${ordinal + 1} 章",
            contentWeight = archiveEntry.estimatedContentWeight(),
        )
        chapters += metadata
        chaptersByOrd[ordinal] = ResolvedEpubSpineEntry(
            ordinalHint = ordinal,
            entryPath = canonicalPath,
            archiveEntryName = archiveEntry.name,
            navigationTitle = navigationTitle,
        )
    }
    currentCoroutineContext().ensureActive()
    return IndexedEpubPublication(
        filePath = filePath,
        index = EpubPublicationIndex(
            title = title,
            spineItemCount = epubPackage.spineIds.size,
            chapters = chapters,
        ),
        chaptersByOrd = chaptersByOrd,
        manifestByPath = manifestByPath,
    )
}

private suspend fun readEpubChapter(
    filePath: String,
    title: String,
    publication: IndexedEpubPublication,
    chapter: ResolvedEpubSpineEntry,
): ReaderDocument? =
    ZipFile(filePath).use { zip ->
        extractEpubChapterDocument(
            zip = zip,
            filePath = filePath,
            title = title,
            chapter = chapter,
            manifestByPath = publication.manifestByPath,
        )
    }

private suspend fun extractEpubChapterDocument(
    zip: ZipFile,
    filePath: String,
    title: String,
    chapter: ResolvedEpubSpineEntry,
    manifestByPath: Map<String, EpubManifestItem>,
): ReaderDocument? {
    currentCoroutineContext().ensureActive()
    val entry = zip.getEntry(chapter.archiveEntryName) ?: return null
    val html = zip.getInputStream(entry).use { decodeText(it.readBytes()) }
    currentCoroutineContext().ensureActive()

    val documentText = StringBuilder()
    val body = stripHtml(html)
    val prependTitle =
        chapter.navigationTitle != null &&
            shouldPrependChapterTitle(chapter.navigationTitle, body)
    val titleStartOffset = documentText.length
    val titleEndOffset = if (prependTitle) {
        documentText.append(chapter.navigationTitle)
        val endOffset = documentText.length
        documentText.append("\n\n")
        endOffset
    } else {
        titleStartOffset
    }
    val extracted = extractEpubFlowBlocks(
        html = html,
        bookFilePath = filePath,
        chapterOrd = chapter.ordinalHint,
        chapterHref = chapter.entryPath,
        chapterTitle = chapter.navigationTitle ?: "第 ${chapter.ordinalHint + 1} 章",
        chapterBasePath = chapter.entryPath.substringBeforeLast('/', missingDelimiterValue = ""),
        manifestByPath = manifestByPath,
        documentText = documentText,
    )
    currentCoroutineContext().ensureActive()
    if (extracted.blocks.isEmpty()) return null

    documentText.trimTrailingWhitespace()
    return ReaderDocument(
        title = title,
        format = "epub",
        text = documentText.toString(),
        flow = ReaderFlowDocument(
            chapters = listOf(
                ReaderFlowChapter(
                    ord = chapter.ordinalHint,
                    href = chapter.entryPath,
                    title = chapter.navigationTitle ?: "第 ${chapter.ordinalHint + 1} 章",
                    titleStartOffset = titleStartOffset,
                    titleEndOffset = titleEndOffset,
                    blocks = extracted.blocks,
                ),
            ),
        ),
    )
}

private fun java.util.zip.ZipEntry.estimatedContentWeight(): Long =
    size
        .takeIf { value -> value > 0L }
        ?: compressedSize.takeIf { value -> value > 0L }
        ?: 1L

private fun unreadableEpubDocument(title: String, message: String): ReaderDocument =
    ReaderDocument(
        title = title,
        format = "epub",
        text = "",
        message = message,
    )

private data class EpubFlowExtraction(
    val blocks: List<ReaderContentBlock>,
)

private fun extractEpubFlowBlocks(
    html: String,
    bookFilePath: String,
    chapterOrd: Int,
    chapterHref: String,
    chapterTitle: String,
    chapterBasePath: String,
    manifestByPath: Map<String, EpubManifestItem>,
    documentText: StringBuilder,
): EpubFlowExtraction {
    val body = extractHtmlBody(html)
        .replace(EpubScriptTagRegex, "")
        .replace(EpubStyleTagRegex, "")
    val blocks = mutableListOf<ReaderContentBlock>()
    val paragraph = StringBuilder()
    var unitIndex = 0

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        paragraph.clear()
        if (text.isBlank()) return
        appendParagraphSeparator(documentText)
        val start = documentText.length
        documentText.append(text)
        val end = documentText.length
        documentText.append("\n\n")
        blocks += ReaderContentBlock.Text(
            chapterOrd = chapterOrd,
            chapterHref = chapterHref,
            chapterTitle = chapterTitle,
            unitIndex = unitIndex++,
            absoluteStart = start,
            absoluteEnd = end,
            text = text,
        )
    }

    fun appendDecodedText(rawText: String) {
        val decoded = decodeXmlEntities(rawText)
        decoded.forEach { char ->
            if (char.isEpubCollapsibleWhitespace()) {
                if (paragraph.isNotEmpty() && paragraph[paragraph.lastIndex] != ' ') {
                    paragraph.append(' ')
                }
            } else {
                paragraph.append(char)
            }
        }
    }

    EpubAnyTagRegex.findAll(body).fold(0) { lastIndex, match ->
        appendDecodedText(body.substring(lastIndex, match.range.first))
        val tag = match.value
        val name = tagName(tag)
        when {
            name == "img" || name == "image" -> {
                flushParagraph()
                val attrs = parseXmlAttributes(tag)
                val rawSrc = attrs["src"] ?: attrs["xlink:href"] ?: attrs["href"]
                val imagePath = rawSrc
                    ?.let(::decodeXmlEntities)
                    ?.let { resolveEpubPath(chapterBasePath, it) }
                if (imagePath != null) {
                    val manifestItem = manifestByPath[imagePath]
                    val offset = documentText.length
                    blocks += ReaderContentBlock.Image(
                        chapterOrd = chapterOrd,
                        chapterHref = chapterHref,
                        chapterTitle = chapterTitle,
                        unitIndex = unitIndex++,
                        absoluteStart = offset,
                        absoluteEnd = offset,
                        resource = EpubImageResource(
                            bookFilePath = bookFilePath,
                            href = imagePath,
                            mediaType = manifestItem?.mediaType,
                            alt = attrs["alt"]?.let(::decodeXmlEntities),
                            widthPx = parseImageDimension(attrs, "width"),
                            heightPx = parseImageDimension(attrs, "height"),
                        ),
                    )
                }
            }
            name == "br" -> {
                if (paragraph.isNotEmpty() && paragraph[paragraph.lastIndex] != ' ') {
                    paragraph.append(' ')
                }
            }
            tag.isClosingBlockTag(name) -> flushParagraph()
        }
        match.range.last + 1
    }.let { lastIndex ->
        appendDecodedText(body.substring(lastIndex))
    }
    flushParagraph()
    return EpubFlowExtraction(blocks)
}

private data class EpubPackage(
    val basePath: String,
    val manifest: Map<String, EpubManifestItem>,
    val spineIds: List<String>,
    val tocId: String?,
)

private data class EpubManifestItem(
    val id: String,
    val href: String,
    val mediaType: String?,
    val properties: String?,
)

private fun readEpubPackage(zip: ZipFile): EpubPackage? {
    val container = zip.getEntry("META-INF/container.xml") ?: return null
    val containerXml = zip.getInputStream(container).use { decodeText(it.readBytes()) }
    val rootFileTag = EpubRootFileRegex.find(containerXml)?.groupValues?.getOrNull(1)
    val opfPath = rootFileTag
        ?.let(::parseXmlAttributes)
        ?.get("full-path")
        ?.let(::decodeXmlEntities)
        ?.let(::percentDecode)
        ?: return null

    val opfEntry = zip.getEntry(opfPath) ?: return null
    val opfXml = zip.getInputStream(opfEntry).use { decodeText(it.readBytes()) }
    val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        .orEmpty()
    val manifest = EpubManifestItemRegex
        .findAll(opfXml)
        .mapNotNull { match ->
            val attrs = parseXmlAttributes(match.groupValues[1])
            val id = attrs["id"] ?: return@mapNotNull null
            val href = attrs["href"] ?: return@mapNotNull null
            id to EpubManifestItem(
                id = id,
                href = decodeXmlEntities(href),
                mediaType = attrs["media-type"],
                properties = attrs["properties"],
            )
        }
        .toMap()
    val spineTag = EpubSpineRegex.find(opfXml)?.groupValues?.getOrNull(1)
    val tocId = spineTag?.let(::parseXmlAttributes)?.get("toc")
    val spineIds = EpubSpineItemRegex
        .findAll(opfXml)
        .mapNotNull { parseXmlAttributes(it.groupValues[1])["idref"] }
        .toList()

    return EpubPackage(
        basePath = basePath,
        manifest = manifest,
        spineIds = spineIds,
        tocId = tocId,
    )
}

private fun readEpubChapterTitles(zip: ZipFile, epubPackage: EpubPackage): Map<String, String> {
    val ncxTitles = readNcxChapterTitles(zip, epubPackage)
    if (ncxTitles.isNotEmpty()) return ncxTitles
    return readNavChapterTitles(zip, epubPackage)
}

private fun readNcxChapterTitles(zip: ZipFile, epubPackage: EpubPackage): Map<String, String> {
    val ncxItem = epubPackage.tocId?.let { epubPackage.manifest[it] }
        ?: epubPackage.manifest.values.firstOrNull { it.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) }
        ?: return emptyMap()
    val ncxPath = resolveEpubPath(epubPackage.basePath, ncxItem.href)
    val ncxEntry = zip.getEntry(ncxPath) ?: zip.getEntry(ncxItem.href) ?: return emptyMap()
    val ncxXml = zip.getInputStream(ncxEntry).use { decodeText(it.readBytes()) }
    val ncxBasePath = ncxPath.substringBeforeLast('/', missingDelimiterValue = "")
    return EpubNcxNavPointRegex
        .findAll(ncxXml)
        .mapNotNull { match ->
            val navPoint = match.groupValues[1]
            val title = EpubNcxLabelRegex
                .find(navPoint)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::stripHtml)
                ?.takeIf { it.isNotBlank() }
            val srcTag = EpubNcxContentRegex.find(navPoint)?.groupValues?.getOrNull(1)
            val src = srcTag?.let(::parseXmlAttributes)?.get("src")?.let(::decodeXmlEntities)
            val path = src?.let { resolveEpubPath(ncxBasePath, it) }
            if (title != null && path != null) path to title else null
        }
        .distinctBy { it.first }
        .toMap()
}

private fun readNavChapterTitles(zip: ZipFile, epubPackage: EpubPackage): Map<String, String> {
    val navItem = epubPackage.manifest.values.firstOrNull { item ->
        item.properties
            ?.split(EpubWhitespaceRegex)
            ?.any { it.equals("nav", ignoreCase = true) } == true
    } ?: return emptyMap()
    val navPath = resolveEpubPath(epubPackage.basePath, navItem.href)
    val navEntry = zip.getEntry(navPath) ?: zip.getEntry(navItem.href) ?: return emptyMap()
    val navHtml = zip.getInputStream(navEntry).use { decodeText(it.readBytes()) }
    val navBasePath = navPath.substringBeforeLast('/', missingDelimiterValue = "")
    return EpubNavLinkRegex
        .findAll(navHtml)
        .mapNotNull { match ->
            val href = parseXmlAttributes(match.groupValues[1])["href"]?.let(::decodeXmlEntities)
            val title = stripHtml(match.groupValues[2]).takeIf { it.isNotBlank() }
            val path = href?.let { resolveEpubPath(navBasePath, it) }
            if (title != null && path != null) path to title else null
        }
        .distinctBy { it.first }
        .toMap()
}

private fun shouldPrependChapterTitle(title: String, body: String): Boolean {
    val normalizedTitle = title.trim()
    if (normalizedTitle.isBlank()) return false
    val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
    return firstLine == null || !firstLine.startsWith(normalizedTitle)
}

private fun appendParagraphSeparator(builder: StringBuilder) {
    if (builder.isNotEmpty() && !builder.endsWith("\n\n")) {
        builder.append("\n\n")
    }
}

private fun extractHtmlBody(value: String): String =
    EpubBodyRegex
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?: value

private fun tagName(tag: String): String? =
    EpubTagNameRegex
        .find(tag.trim())
        ?.groupValues
        ?.getOrNull(1)
        ?.substringAfter(':')
        ?.lowercase()

private fun String.isClosingBlockTag(name: String?): Boolean =
    startsWith("</") && name in EpubClosingBlockTags

private fun parseImageDimension(attrs: Map<String, String>, name: String): Int? {
    attrs[name]?.let { direct ->
        direct.extractPositiveNumber()?.let { return it }
    }
    val style = attrs["style"] ?: return null
    return Regex("""(?i)\b$name\s*:\s*([0-9.]+)\s*px\b""")
        .find(style)
        ?.groupValues
        ?.getOrNull(1)
        ?.toFloatOrNull()
        ?.toInt()
        ?.takeIf { it > 0 }
}

private fun String.extractPositiveNumber(): Int? =
    EpubPositiveNumberRegex
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toFloatOrNull()
        ?.toInt()
        ?.takeIf { it > 0 }

private fun resolveEpubPath(basePath: String, href: String): String {
    val cleanHref = percentDecode(href.substringBefore('#').substringBefore('?'))
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

private fun parseXmlAttributes(value: String): Map<String, String> =
    EpubXmlAttributeRegex
        .findAll(value)
        .associate { match ->
            val attrValue = match.groupValues[3].ifEmpty { match.groupValues[4] }
            match.groupValues[1] to attrValue
        }

private fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val builder = StringBuilder(value.length)
    val bytes = ByteArrayOutputStream()

    fun flushBytes() {
        if (bytes.size() == 0) return
        builder.append(bytes.toByteArray().toString(Charsets.UTF_8))
        bytes.reset()
    }

    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '%' && index + 2 < value.length) {
            val decoded = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (decoded != null) {
                bytes.write(decoded)
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

private fun decodeXmlEntities(value: String): String {
    if ('&' !in value) return value
    return value
        .replace(EpubHexEntityRegex) { match ->
            decodeXmlCodePoint(match.groupValues[1].toIntOrNull(16))
        }
        .replace(EpubDecimalEntityRegex) { match ->
            decodeXmlCodePoint(match.groupValues[1].toIntOrNull())
        }
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

private fun decodeXmlCodePoint(codePoint: Int?): String =
    if (codePoint != null && java.lang.Character.isValidCodePoint(codePoint)) {
        String(java.lang.Character.toChars(codePoint))
    } else {
        ""
    }

private fun decodeText(bytes: ByteArray): String {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
    }
    return tryDecode(bytes, Charsets.UTF_8)
        ?: tryDecode(bytes, Charset.forName("GBK"))
        ?: bytes.toString(Charsets.UTF_8)
}

private fun tryDecode(bytes: ByteArray, charset: Charset): String? =
    try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

private fun stripHtml(value: String): String =
    decodeXmlEntities(
        value
            .replace(EpubScriptTagRegex, "")
            .replace(EpubStyleTagRegex, "")
            .replace(EpubBreakTagRegex, "\n")
            .replace(EpubClosingBlockTagRegex, "\n\n")
            .replace(EpubAnyTagRegex, ""),
    )
        .replace(EpubHorizontalWhitespaceRegex, " ")
        .replace(EpubExtraLineBreakRegex, "\n\n")
        .trim()

private fun Char.isEpubCollapsibleWhitespace(): Boolean =
    this == ' ' || this == '\t' || this == '\n' || this == '\r' || this == '\u000B' || this == '\u000C'

private fun StringBuilder.trimTrailingWhitespace() {
    while (isNotEmpty() && this[lastIndex].isWhitespace()) {
        setLength(length - 1)
    }
}
