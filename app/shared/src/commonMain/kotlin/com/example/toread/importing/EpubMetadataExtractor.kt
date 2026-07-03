package com.example.toread.importing

data class EpubImportMetadata(
    val title: String?,
    val author: String?,
)

expect class EpubMetadataExtractor() {
    suspend fun extract(bookFilePath: String, format: String): EpubImportMetadata?
}

internal fun parseEpubOpfMetadata(opfXml: String): EpubImportMetadata {
    val metadataXml = Regex("""(?is)<(?:[A-Za-z_][\w.-]*:)?metadata\b[^>]*>(.*?)</(?:[A-Za-z_][\w.-]*:)?metadata>""")
        .find(opfXml)
        ?.groupValues
        ?.getOrNull(1)
        ?: opfXml
    val title = simpleTagValues(metadataXml, "title").firstOrNull()
    val authors = simpleTagValues(metadataXml, "creator") + metaPropertyValues(metadataXml, "creator")
    return EpubImportMetadata(
        title = title,
        author = authors.distinct().takeIf { it.isNotEmpty() }?.joinToString("、"),
    )
}

private fun simpleTagValues(xml: String, localName: String): List<String> =
    Regex("""(?is)<(?:[A-Za-z_][\w.-]*:)?$localName\b[^>]*>(.*?)</(?:[A-Za-z_][\w.-]*:)?$localName>""")
        .findAll(xml)
        .mapNotNull { match -> match.groupValues[1].toXmlTextValue() }
        .toList()

private fun metaPropertyValues(xml: String, localName: String): List<String> =
    Regex("""(?is)<meta\b([^>]*)>(.*?)</meta>""")
        .findAll(xml)
        .mapNotNull { match ->
            val attrs = parseXmlAttributes(match.groupValues[1])
            val property = attrs["property"] ?: attrs["name"] ?: return@mapNotNull null
            val normalized = property.substringAfter(':').lowercase()
            match.groupValues[2].toXmlTextValue().takeIf { normalized == localName.lowercase() }
        }
        .toList()

private fun String.toXmlTextValue(): String? =
    replace(Regex("""(?is)<[^>]+>"""), " ")
        .let(::decodeXmlEntities)
        .replace(Regex("""\s+"""), " ")
        .trim()
        .takeIf { it.isNotBlank() }

internal fun parseXmlAttributes(value: String): Map<String, String> =
    Regex("""([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*("([^"]*)"|'([^']*)')""")
        .findAll(value)
        .associate { match ->
            val attrValue = match.groupValues[3].ifEmpty { match.groupValues[4] }
            match.groupValues[1] to decodeXmlEntities(attrValue)
        }

internal fun decodeXmlEntities(value: String): String =
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
