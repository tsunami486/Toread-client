package com.example.toread.reader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class BookLocator {
    abstract val globalProgress: Double
}

@Serializable
@SerialName("epub")
data class EpubLocator(
    val chapterHref: String,
    val chapterIndex: Int,
    val chapterProgress: Double,
    override val globalProgress: Double,
    val charOffset: Long? = null,
    val unitIndex: Int? = null,
    val textOffset: Int? = null,
    val cfi: String? = null,
    val chapterTitle: String? = null,
) : BookLocator()

@Serializable
@SerialName("pdf")
data class PdfLocator(
    val pageIndex: Int,
    val pageOffsetY: Double,
    override val globalProgress: Double,
) : BookLocator()

@Serializable
@SerialName("txt")
data class TxtLocator(
    val charOffset: Long,
    val paragraphIndex: Int? = null,
    override val globalProgress: Double,
    val chapterTitle: String? = null,
) : BookLocator()

internal const val LocatorChapterTitleMaxLength = 160

internal fun String?.toLocatorChapterTitle(): String? {
    val source = this ?: return null
    val normalized = buildString(capacity = minOf(source.length, LocatorChapterTitleMaxLength)) {
        var pendingSpace = false
        for (character in source) {
            if (character.isWhitespace()) {
                pendingSpace = isNotEmpty()
                continue
            }
            if (pendingSpace) {
                if (length >= LocatorChapterTitleMaxLength) break
                append(' ')
                pendingSpace = false
            }
            if (length >= LocatorChapterTitleMaxLength) break
            append(character)
        }
    }
    return normalized.takeIf(String::isNotEmpty)
}

internal fun String?.toEpubLocatorChapterTitle(chapterIndex: Int): String? =
    toLocatorChapterTitle()?.takeUnless { title ->
        title == "第 ${chapterIndex.coerceAtLeast(0) + 1} 章"
    }

object BookLocatorJson {
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(locator: BookLocator): String =
        json.encodeToString(BookLocator.serializer(), locator)

    fun decode(value: String): BookLocator =
        json.decodeFromString(BookLocator.serializer(), value)

    fun decodeOrNull(value: String?): BookLocator? =
        value
            ?.takeIf(String::isNotBlank)
            ?.let { encoded ->
                try {
                    decode(encoded)
                } catch (_: Exception) {
                    null
                }
            }
}
