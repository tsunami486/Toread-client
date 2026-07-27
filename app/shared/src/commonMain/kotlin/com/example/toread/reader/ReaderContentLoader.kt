package com.example.toread.reader

data class ReaderDocument(
    val title: String,
    val format: String,
    val text: String,
    val message: String? = null,
    val flow: ReaderFlowDocument? = null,
) {
    val isReadable: Boolean = text.isNotBlank() || flow?.chapters?.isNotEmpty() == true
}

data class EpubPublicationIndex(
    val title: String,
    val spineItemCount: Int,
    val chapters: List<EpubChapterMetadata>,
) {
    val totalContentWeight: Long = chapters.sumOf(EpubChapterMetadata::contentWeight)
}

data class EpubChapterMetadata(
    val ord: Int,
    val href: String,
    val title: String,
    val contentWeight: Long,
)

data class ReaderFlowDocument(
    val chapters: List<ReaderFlowChapter>,
)

data class ReaderFlowChapter(
    val ord: Int,
    val href: String?,
    val title: String,
    val titleStartOffset: Int,
    val titleEndOffset: Int,
    val blocks: List<ReaderContentBlock>,
)

sealed class ReaderContentBlock {
    abstract val chapterOrd: Int
    abstract val chapterHref: String?
    abstract val chapterTitle: String
    abstract val unitIndex: Int
    abstract val absoluteStart: Int
    abstract val absoluteEnd: Int

    data class Text(
        override val chapterOrd: Int,
        override val chapterHref: String?,
        override val chapterTitle: String,
        override val unitIndex: Int,
        override val absoluteStart: Int,
        override val absoluteEnd: Int,
        val text: String,
    ) : ReaderContentBlock()

    data class Image(
        override val chapterOrd: Int,
        override val chapterHref: String?,
        override val chapterTitle: String,
        override val unitIndex: Int,
        override val absoluteStart: Int,
        override val absoluteEnd: Int,
        val resource: EpubImageResource,
    ) : ReaderContentBlock()
}

data class EpubImageResource(
    val bookFilePath: String,
    val href: String,
    val mediaType: String? = null,
    val alt: String? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
)

expect class ReaderContentLoader() {
    suspend fun load(filePath: String, format: String, title: String): ReaderDocument

    suspend fun loadEpubPublicationIndex(
        filePath: String,
        title: String,
    ): EpubPublicationIndex?

    suspend fun loadEpubChapter(
        filePath: String,
        title: String,
        chapterOrd: Int,
    ): ReaderDocument?
}

expect class EpubImageBytesLoader() {
    suspend fun load(resource: EpubImageResource): ByteArray?
}
