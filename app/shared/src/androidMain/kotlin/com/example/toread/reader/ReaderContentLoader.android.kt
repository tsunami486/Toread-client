package com.example.toread.reader

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile

actual class ReaderContentLoader actual constructor() {
    actual suspend fun load(filePath: String, format: String, title: String): ReaderDocument {
        val normalizedFormat = format.lowercase()
        return when (normalizedFormat) {
            "txt" -> ReaderDocument(title, normalizedFormat, decodeText(File(filePath).readBytes()))
            "epub" -> ReaderDocument(title, normalizedFormat, extractEpubText(filePath))
            "pdf" -> ReaderDocument(title, normalizedFormat, "", "PDF 渲染将在后续接入，目前已完成本地导入与书架打开。")
            else -> ReaderDocument(title, normalizedFormat, "", "暂不支持阅读 .$format 文件。")
        }
    }
}

private fun extractEpubText(filePath: String): String {
    ZipFile(filePath).use { zip ->
        val container = zip.getEntry("META-INF/container.xml")
            ?: return "无法找到 EPUB container.xml。"
        val containerXml = zip.getInputStream(container).use { decodeText(it.readBytes()) }
        val opfPath = Regex("""full-path=["']([^"']+)["']""")
            .find(containerXml)
            ?.groupValues
            ?.getOrNull(1)
            ?: return "无法找到 EPUB OPF 文件。"

        val opfEntry = zip.getEntry(opfPath) ?: return "无法读取 EPUB OPF 文件。"
        val opfXml = zip.getInputStream(opfEntry).use { decodeText(it.readBytes()) }
        val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }

        val manifest = Regex(
            """<item\b[^>]*\bid=["']([^"']+)["'][^>]*\bhref=["']([^"']+)["'][^>]*>""",
            RegexOption.IGNORE_CASE,
        ).findAll(opfXml).associate { match ->
            match.groupValues[1] to match.groupValues[2]
        }
        val spineIds = Regex("""<itemref\b[^>]*\bidref=["']([^"']+)["'][^>]*/?>""", RegexOption.IGNORE_CASE)
            .findAll(opfXml)
            .map { it.groupValues[1] }
            .toList()

        return spineIds.mapNotNull { id ->
            val href = manifest[id] ?: return@mapNotNull null
            val entryPath = listOfNotNull(basePath, href).joinToString("/")
            val entry = zip.getEntry(entryPath) ?: zip.getEntry(href) ?: return@mapNotNull null
            zip.getInputStream(entry).use { stripHtml(decodeText(it.readBytes())) }
        }.joinToString("\n\n").ifBlank { "未能从 EPUB 中抽取正文文本。" }
    }
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
    value
        .replace(Regex("""(?is)<script.*?</script>"""), "")
        .replace(Regex("""(?is)<style.*?</style>"""), "")
        .replace(Regex("""(?i)<br\s*/?>"""), "\n")
        .replace(Regex("""(?i)</p>"""), "\n\n")
        .replace(Regex("""<[^>]+>"""), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace(Regex("""[ \t]+"""), " ")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
