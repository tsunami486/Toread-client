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
) : BookLocator()

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
}
