package com.example.toread.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile

actual class ReaderContentLoader actual constructor() {
    actual suspend fun load(filePath: String, format: String, title: String): ReaderDocument = withContext(Dispatchers.IO) {
        val normalizedFormat = format.lowercase()
        when (normalizedFormat) {
            "txt" -> ReaderDocument(title, normalizedFormat, decodeText(File(filePath).readBytes()))
            "epub" -> extractEpubDocument(filePath, normalizedFormat, title)
            "pdf" -> ReaderDocument(title, normalizedFormat, "", "PDF 渲染将在后续接入，目前已完成本地导入与书架打开。")
            else -> ReaderDocument(title, normalizedFormat, "", "暂不支持阅读 .$format 文件。")
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

private fun extractEpubDocument(filePath: String, format: String, title: String): ReaderDocument {
    ZipFile(filePath).use { zip ->
        val epubPackage = readEpubPackage(zip) ?: return ReaderDocument(title, format, "", "无法读取 EPUB 目录结构。")
        val chapterTitles = readEpubChapterTitles(zip, epubPackage)
        val manifestByPath = epubPackage.manifest.values.associateBy { item ->
            resolveEpubPath(epubPackage.basePath, item.href)
        }
        val documentText = StringBuilder()
        val chapters = mutableListOf<ReaderFlowChapter>()

        epubPackage.spineIds.forEach { id ->
            val item = epubPackage.manifest[id] ?: return@forEach
            val entryPath = resolveEpubPath(epubPackage.basePath, item.href)
            val entry = zip.getEntry(entryPath) ?: zip.getEntry(item.href) ?: return@forEach
            val html = zip.getInputStream(entry).use { decodeText(it.readBytes()) }
            val body = stripHtml(html)
            val chapterTitle = chapterTitles[entryPath]?.takeIf { it.isNotBlank() }
            val displayTitle = chapterTitle ?: "第 ${chapters.size + 1} 章"
            val titleStartOffset = documentText.length
            var titleEndOffset = titleStartOffset
            if (chapterTitle != null && shouldPrependChapterTitle(chapterTitle, body)) {
                appendParagraphSeparator(documentText)
                val actualTitleStart = documentText.length
                documentText.append(chapterTitle)
                titleEndOffset = documentText.length
                documentText.append("\n\n")
                val extracted = extractEpubFlowBlocks(
                    html = html,
                    bookFilePath = filePath,
                    chapterOrd = chapters.size,
                    chapterHref = entryPath,
                    chapterTitle = displayTitle,
                    chapterBasePath = entryPath.substringBeforeLast('/', missingDelimiterValue = ""),
                    manifestByPath = manifestByPath,
                    documentText = documentText,
                )
                if (extracted.blocks.isNotEmpty()) {
                    chapters += ReaderFlowChapter(
                        ord = chapters.size,
                        href = entryPath,
                        title = displayTitle,
                        titleStartOffset = actualTitleStart,
                        titleEndOffset = titleEndOffset,
                        blocks = extracted.blocks,
                    )
                }
            } else {
                val extracted = extractEpubFlowBlocks(
                    html = html,
                    bookFilePath = filePath,
                    chapterOrd = chapters.size,
                    chapterHref = entryPath,
                    chapterTitle = displayTitle,
                    chapterBasePath = entryPath.substringBeforeLast('/', missingDelimiterValue = ""),
                    manifestByPath = manifestByPath,
                    documentText = documentText,
                )
                if (extracted.blocks.isNotEmpty()) {
                    chapters += ReaderFlowChapter(
                        ord = chapters.size,
                        href = entryPath,
                        title = displayTitle,
                        titleStartOffset = titleStartOffset,
                        titleEndOffset = titleStartOffset,
                        blocks = extracted.blocks,
                    )
                }
            }
        }

        val text = documentText.toString().trimEnd()
        return if (text.isBlank() && chapters.isEmpty()) {
            ReaderDocument(title, format, "未能从 EPUB 中抽取正文文本。")
        } else {
            ReaderDocument(
                title = title,
                format = format,
                text = text,
                flow = ReaderFlowDocument(chapters),
            )
        }
    }
}

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
        .replace(Regex("""(?is)<script.*?</script>"""), "")
        .replace(Regex("""(?is)<style.*?</style>"""), "")
    val blocks = mutableListOf<ReaderContentBlock>()
    val paragraph = StringBuilder()
    var unitIndex = 0

    fun flushParagraph() {
        val text = paragraph.toString()
            .replace(Regex("""[ \t]+"""), " ")
            .trim()
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
        val normalized = decodeXmlEntities(rawText)
            .replace(Regex("""\s+"""), " ")
        if (normalized.isNotBlank()) {
            paragraph.append(normalized)
        }
    }

    Regex("""(?is)<[^>]+>""").findAll(body).fold(0) { lastIndex, match ->
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
            name == "br" -> paragraph.append(' ')
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
    val rootFileTag = Regex("""(?is)<rootfile\b([^>]*)>""").find(containerXml)?.groupValues?.getOrNull(1)
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
    val manifest = Regex("""(?is)<item\b([^>]*)/?>""")
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
    val spineTag = Regex("""(?is)<spine\b([^>]*)>""").find(opfXml)?.groupValues?.getOrNull(1)
    val tocId = spineTag?.let(::parseXmlAttributes)?.get("toc")
    val spineIds = Regex("""(?is)<itemref\b([^>]*)/?>""")
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
    return Regex("""(?is)<navPoint\b[^>]*>(.*?)</navPoint>""")
        .findAll(ncxXml)
        .mapNotNull { match ->
            val navPoint = match.groupValues[1]
            val title = Regex("""(?is)<navLabel\b[^>]*>.*?<text\b[^>]*>(.*?)</text>.*?</navLabel>""")
                .find(navPoint)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::stripHtml)
                ?.takeIf { it.isNotBlank() }
            val srcTag = Regex("""(?is)<content\b([^>]*)/?>""").find(navPoint)?.groupValues?.getOrNull(1)
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
            ?.split(Regex("""\s+"""))
            ?.any { it.equals("nav", ignoreCase = true) } == true
    } ?: return emptyMap()
    val navPath = resolveEpubPath(epubPackage.basePath, navItem.href)
    val navEntry = zip.getEntry(navPath) ?: zip.getEntry(navItem.href) ?: return emptyMap()
    val navHtml = zip.getInputStream(navEntry).use { decodeText(it.readBytes()) }
    val navBasePath = navPath.substringBeforeLast('/', missingDelimiterValue = "")
    return Regex("""(?is)<a\b([^>]*)>(.*?)</a>""")
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
    Regex("""(?is)<body\b[^>]*>(.*?)</body>""")
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?: value

private fun tagName(tag: String): String? =
    Regex("""^</?\s*([A-Za-z0-9:_-]+)""")
        .find(tag.trim())
        ?.groupValues
        ?.getOrNull(1)
        ?.substringAfter(':')
        ?.lowercase()

private fun String.isClosingBlockTag(name: String?): Boolean =
    startsWith("</") && name in setOf("p", "div", "section", "article", "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr")

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
    Regex("""([0-9.]+)""")
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
    Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*("([^"]*)"|'([^']*)')""")
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

private fun decodeXmlEntities(value: String): String =
    value
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { match ->
            decodeXmlCodePoint(match.groupValues[1].toIntOrNull(16))
        }
        .replace(Regex("""&#(\d+);""")) { match ->
            decodeXmlCodePoint(match.groupValues[1].toIntOrNull())
        }
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

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
            .replace(Regex("""(?is)<script.*?</script>"""), "")
            .replace(Regex("""(?is)<style.*?</style>"""), "")
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)</(p|div|section|article|h[1-6]|li|tr)>"""), "\n\n")
            .replace(Regex("""<[^>]+>"""), ""),
    )
        .replace(Regex("""[ \t]+"""), " ")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
