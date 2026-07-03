package com.example.toread.importing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile

actual class EpubMetadataExtractor actual constructor() {
    actual suspend fun extract(bookFilePath: String, format: String): EpubImportMetadata? =
        withContext(Dispatchers.IO) {
            if (!format.equals("epub", ignoreCase = true)) return@withContext null
            runCatching {
                ZipFile(bookFilePath).use { zip ->
                    val opfXml = readOpfXml(zip) ?: return@use null
                    parseEpubOpfMetadata(opfXml)
                }
            }.getOrNull()
        }
}

private fun readOpfXml(zip: ZipFile): String? {
    val container = zip.getEntry("META-INF/container.xml") ?: return null
    val containerXml = zip.getInputStream(container).use { it.readBytes().toString(Charsets.UTF_8) }
    val rootFileTag = Regex("""(?is)<rootfile\b([^>]*)>""").find(containerXml)?.groupValues?.getOrNull(1)
    val opfPath = rootFileTag
        ?.let(::parseXmlAttributes)
        ?.get("full-path")
        ?.let(::percentDecode)
        ?: return null
    val opfEntry = zip.getEntry(opfPath) ?: return null
    return zip.getInputStream(opfEntry).use { it.readBytes().toString(Charsets.UTF_8) }
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
