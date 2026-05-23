package com.example.toread.importing

import com.example.toread.database.DatabaseDriverFactory
import com.example.toread.database.ToreadDatabase
import com.example.toread.platform.BookFileStorage
import com.example.toread.platform.FileHasher
import com.example.toread.platform.PlatformFile
import com.example.toread.platform.PlatformFilePicker

private val supportedExtensions = setOf("epub", "pdf", "txt")

class BookImportService(
    private val database: ToreadDatabase,
    private val filePicker: PlatformFilePicker = PlatformFilePicker(),
    private val fileHasher: FileHasher = FileHasher(),
    private val bookFileStorage: BookFileStorage = BookFileStorage(),
    private val metadataParser: BookMetadataParser = BasicBookMetadataParser(),
    private val clock: ImportClock = PlatformImportClock(),
) {
    suspend fun importSelectedFiles(): List<BookImportResult> =
        importFiles(filePicker.pickFiles(supportedExtensions.toList()))

    suspend fun importFiles(files: List<PlatformFile>): List<BookImportResult> =
        files.map { file -> importFile(file) }

    suspend fun importFile(file: PlatformFile): BookImportResult {
        val validation = validateImportFile(file)
        if (validation is ImportFileValidation.Invalid) {
            return BookImportResult.Failed(file.displayName, validation.reason)
        }

        val extension = (validation as ImportFileValidation.Valid).extension
        val bookHash = fileHasher.sha256(file)
        val existing = database.toreadDatabaseQueries.selectBookByHash(bookHash).executeAsOneOrNull()
        if (existing != null) {
            return BookImportResult.Duplicate(bookHash, existing.title)
        }

        val storedFile = bookFileStorage.copyToPrivateBookDirectory(file, bookHash, extension)
        val metadata = metadataParser.parse(file, extension)
        val now = clock.nowIsoString()

        database.toreadDatabaseQueries.insertBook(
            id = "book_$bookHash",
            book_hash = bookHash,
            title = metadata.title,
            author = metadata.author,
            format = extension,
            file_path = storedFile.path,
            cover_path = null,
            file_size = storedFile.sizeBytes,
            metadata_json = metadata.metadataJson,
            reading_status = "unread",
            last_read_at = null,
            sync_status = "pending",
            created_at = now,
            updated_at = now,
            archived_at = null,
        )

        return BookImportResult.Imported(
            bookHash = bookHash,
            title = metadata.title,
            storedPath = storedFile.path,
        )
    }
}

fun createBookImportService(): BookImportService {
    return BookImportService(createToreadDatabase())
}

fun createToreadDatabase(): ToreadDatabase =
    ToreadDatabase(DatabaseDriverFactory().createDriver())

sealed interface BookImportResult {
    data class Imported(
        val bookHash: String,
        val title: String,
        val storedPath: String,
    ) : BookImportResult

    data class Duplicate(
        val bookHash: String,
        val title: String,
    ) : BookImportResult

    data class Failed(
        val fileName: String,
        val reason: String,
    ) : BookImportResult
}

private sealed interface ImportFileValidation {
    data class Valid(val extension: String) : ImportFileValidation
    data class Invalid(val reason: String) : ImportFileValidation
}

private fun validateImportFile(file: PlatformFile): ImportFileValidation {
    val extension = file.extension()
        ?: return ImportFileValidation.Invalid("缺少文件扩展名")

    if (extension !in supportedExtensions) {
        return ImportFileValidation.Invalid("暂不支持 .$extension 文件")
    }

    val mimeType = file.mimeType?.lowercase()?.substringBefore(";")?.trim()
    val isKnownCompatibleMime = mimeType == null ||
        mimeType.isBlank() ||
        mimeType == "application/octet-stream" ||
        isAcceptedMimeType(extension, mimeType)

    if (!isKnownCompatibleMime) {
        return ImportFileValidation.Invalid("文件 MIME 类型不匹配：$mimeType")
    }

    return ImportFileValidation.Valid(extension)
}

private fun PlatformFile.extension(): String? {
    val name = path?.substringAfterLast('\\')?.substringAfterLast('/')
        ?: displayName
    return name.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.lowercase()
}

private fun isAcceptedMimeType(extension: String, mimeType: String): Boolean = when (extension) {
    "epub" -> mimeType in setOf(
        "application/epub+zip",
        "application/x-epub+zip",
        "application/zip",
        "application/x-zip",
        "application/x-zip-compressed",
    )
    "pdf" -> mimeType == "application/pdf"
    "txt" -> mimeType == "text/plain" || mimeType.startsWith("text/")
    else -> false
}

data class BookMetadata(
    val title: String,
    val author: String?,
    val metadataJson: String,
)

interface BookMetadataParser {
    suspend fun parse(file: PlatformFile, extension: String): BookMetadata
}

class BasicBookMetadataParser : BookMetadataParser {
    override suspend fun parse(file: PlatformFile, extension: String): BookMetadata {
        val fallbackTitle = file.displayName
            .substringBeforeLast('.', missingDelimiterValue = file.displayName)
            .ifBlank { "未命名" }
        return BookMetadata(
            title = fallbackTitle,
            author = null,
            metadataJson = """{"source":"local_import","format":"$extension"}""",
        )
    }
}

expect class PlatformImportClock() : ImportClock

interface ImportClock {
    fun nowIsoString(): String
}
