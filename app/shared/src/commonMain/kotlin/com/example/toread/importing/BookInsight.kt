package com.example.toread.importing

import com.example.toread.categories.MaximumCategoryLength
import com.example.toread.metadata.BookInsight
import com.example.toread.metadata.BookInsightSource

/**
 * Reading a book's own description and categories out of the user's local file.
 *
 * Three sources, in the order they are trusted: the publication's package metadata (EPUB
 * `dc:description` / `dc:subject` / calibre tags, a PDF information dictionary), the 「内容简介」
 * front-matter page or text marker the book ships with, and finally the file name. Everything is
 * local — nothing is fetched — and a book that carries none of these is left without a candidate
 * instead of being guessed at.
 */

/** Read-only access to the entries of a local book archive, so the parsing stays shared. */
internal interface BookArchive {
    val entryNames: List<String>

    fun readBytes(entryName: String): ByteArray?
}

/**
 * The one entry point both platforms implement: open the local file, hand its bytes to the shared
 * parsers, and return what the book itself says about its description and categories.
 */
expect class BookInsightExtractor() {
    suspend fun extract(
        bookFilePath: String,
        format: String,
        fileName: String?,
    ): BookInsight?
}

/** Reads an EPUB's own package metadata and, when it has none, its 「内容简介」 front matter. */
internal fun extractEpubInsight(archive: BookArchive, fileName: String?): BookInsight? {
    val packageDocument = readEpubInsightPackage(archive) ?: return null
    val opfInsight = parseEpubOpfInsight(packageDocument.opfXml)
    val navigationTitles = epubNavigationTitles(archive, packageDocument)

    val frontMatter = epubFrontMatterDescription(
        archive = archive,
        packageDocument = packageDocument,
        navigationTitles = navigationTitles,
    )
    // The story text, sampled only to infer a category (see sampleEpubBodyText for its bounds); a
    // document already read as the description is not sampled again.
    val bodySample = sampleEpubBodyText(
        archive = archive,
        packageDocument = packageDocument,
        skipHrefs = setOfNotNull(frontMatter?.href?.toInsightPathKey()),
    )

    val insight = BookInsight(
        description = opfInsight.description ?: frontMatter?.text,
        descriptionSource = when {
            opfInsight.description != null -> BookInsightSource.EpubPackage
            frontMatter != null -> BookInsightSource.EpubFrontMatter
            else -> null
        },
        descriptionOrigin = when {
            opfInsight.description != null -> OpfDescriptionOrigin
            frontMatter != null -> frontMatter.origin
            else -> null
        },
        subjects = (opfInsight.subjects + filenameSubjects(fileName))
            .distinct()
            .take(MaximumInsightTags),
        series = opfInsight.series,
        authorHint = filenameAuthorHint(fileName),
        bodySample = bodySample,
    )
    return insight.takeIf { !it.isEmpty }
}

private const val OpfDescriptionOrigin = "出版方元数据"

/** Extracts the insight a plain-text book can offer: bracketed genres plus a 内容简介 marker. */
internal fun extractPlainTextInsight(fileName: String?, text: String): BookInsight? {
    val marked = extractMarkedBlurb(text)
    val insight = BookInsight(
        description = marked,
        descriptionSource = if (marked != null) BookInsightSource.TxtMarker else null,
        descriptionOrigin = if (marked != null) "文本内「内容简介」" else null,
        subjects = filenameSubjects(fileName).take(MaximumInsightTags),
        authorHint = filenameAuthorHint(fileName),
        // The head of the file the caller already read, which is the story as much as the 简介 block.
        bodySample = text.takeIf { it.isNotBlank() },
    )
    return insight.takeIf { !it.isEmpty }
}

/** Reads the document information a PDF engine could hand over; Android's renderer hands over none. */
internal fun pdfInsight(
    subject: String?,
    keywords: String?,
    fileName: String?,
    bodySample: String? = null,
): BookInsight? {
    val description = subject?.trim()?.takeIf { it.length >= MinimumBlurbLength }
    val subjects = keywords
        .orEmpty()
        .split(',', '，', ';', '；', '\n')
        .map(String::trim)
        .filter { it.isNotBlank() && it.length <= MaximumCategoryLength }
    val insight = BookInsight(
        description = description,
        descriptionSource = if (description != null) BookInsightSource.PdfInfo else null,
        descriptionOrigin = if (description != null) "PDF 文档信息" else null,
        subjects = (subjects + filenameSubjects(fileName)).distinct().take(MaximumInsightTags),
        authorHint = filenameAuthorHint(fileName),
        bodySample = bodySample?.takeIf { it.isNotBlank() },
    )
    return insight.takeIf { !it.isEmpty }
}

/** One chapter of sampled text is capped by the sampler, so a PDF sample uses the same budget. */
internal const val MaximumBodySampleChars = 96 * 1024

/** How many leading pages a PDF body sample may read before it stops. */
internal const val MaximumPdfSamplePages = 10

internal const val MaximumInsightTags = 8
internal const val MinimumBlurbLength = 24
internal const val MaximumBlurbLength = 1200
