package com.example.toread.importing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

actual class BookCoverExtractor actual constructor() {
    actual suspend fun extractCover(
        bookFilePath: String,
        bookHash: String,
        format: String,
    ): String? = withContext(Dispatchers.IO) {
        if (!format.equals("epub", ignoreCase = true)) return@withContext null
        ZipFile(bookFilePath).use { zip ->
            val epubPackage = readEpubPackage(zip) ?: return@withContext null
            val coverItem = findCoverItem(epubPackage) ?: return@withContext null
            val coverPath = resolveEpubPath(epubPackage.basePath, coverItem.href)
            val entry = zip.getEntry(coverPath) ?: zip.getEntry(coverItem.href) ?: return@withContext null
            val extension = coverExtension(coverItem.mediaType, coverPath)
            val target = File(bookFilePath).parentFile?.resolve("cover.$extension") ?: return@withContext null
            zip.getInputStream(entry).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.absolutePath
        }
    }
}

private data class EpubPackage(
    val basePath: String,
    val manifest: Map<String, EpubManifestItem>,
    val coverId: String?,
)

private data class EpubManifestItem(
    val id: String,
    val href: String,
    val mediaType: String?,
    val properties: String?,
)

private fun readEpubPackage(zip: ZipFile): EpubPackage? {
    val container = zip.getEntry("META-INF/container.xml") ?: return null
    val containerXml = zip.getInputStream(container).use { it.readBytes().toString(Charsets.UTF_8) }
    val rootFileTag = Regex("""(?is)<rootfile\b([^>]*)>""").find(containerXml)?.groupValues?.getOrNull(1)
    val opfPath = rootFileTag
        ?.let(::parseXmlAttributes)
        ?.get("full-path")
        ?.let(::decodeXmlEntities)
        ?.let(::percentDecode)
        ?: return null
    val opfEntry = zip.getEntry(opfPath) ?: return null
    val opfXml = zip.getInputStream(opfEntry).use { it.readBytes().toString(Charsets.UTF_8) }
    val basePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        .orEmpty()
    val coverId = Regex("""(?is)<meta\b([^>]*)/?>""")
        .findAll(opfXml)
        .map { parseXmlAttributes(it.groupValues[1]) }
        .firstOrNull { it["name"].equals("cover", ignoreCase = true) }
        ?.get("content")
        ?.let(::decodeXmlEntities)
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
    return EpubPackage(basePath = basePath, manifest = manifest, coverId = coverId)
}

private fun findCoverItem(epubPackage: EpubPackage): EpubManifestItem? =
    epubPackage.coverId?.let { epubPackage.manifest[it] }
        ?: epubPackage.manifest.values.firstOrNull { item ->
            item.properties
                ?.split(Regex("""\s+"""))
                ?.any { it.equals("cover-image", ignoreCase = true) } == true
        }
        ?: epubPackage.manifest.values.firstOrNull { item ->
            item.isImage() && item.href.substringAfterLast('/').contains("cover", ignoreCase = true)
        }
        ?: epubPackage.manifest.values.firstOrNull { it.isImage() }

private fun EpubManifestItem.isImage(): Boolean =
    mediaType?.startsWith("image/", ignoreCase = true) == true

private fun coverExtension(mediaType: String?, href: String): String =
    when (mediaType?.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> href.substringAfterLast('.', missingDelimiterValue = "jpg")
            .substringBefore('?')
            .lowercase()
            .takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif") }
            ?: "jpg"
    }.let { if (it == "jpeg") "jpg" else it }

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
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString().orEmpty()
        }
        .replace(Regex("""&#(\d+);""")) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
        }
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
