package com.example.toread.platform

data class PlatformFile(
    val displayName: String,
    val path: String? = null,
    val uri: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)

expect class PlatformFilePicker() {
    suspend fun pickFiles(extensions: List<String> = listOf("epub", "pdf", "txt")): List<PlatformFile>
}

/** What the platform handed back after the user was asked to choose an image. */
sealed interface ImagePickResult {
    data class Picked(val file: PlatformFile) : ImagePickResult

    data object Cancelled : ImagePickResult

    /**
     * The platform needs the gallery permission before it can show images. The caller explains why and
     * offers to open this app's settings page.
     */
    data object PermissionRequired : ImagePickResult
}

/**
 * Opens the system gallery for a single image — covers come from the gallery, not from the documents
 * picker that imports books.
 */
expect class PlatformImagePicker() {
    suspend fun pickImage(): ImagePickResult

    /** Opens this app's system settings page, so a denied gallery permission can be granted. */
    fun openAppSettings()
}

expect class SecureTokenStore() {
    suspend fun saveToken(token: String)
    suspend fun getToken(): String?
    suspend fun clear()
}

expect class FileHasher() {
    suspend fun sha256(file: PlatformFile): String
}

data class StoredBookFile(
    val path: String,
    val sizeBytes: Long,
)

expect class BookFileStorage() {
    suspend fun copyToPrivateBookDirectory(
        file: PlatformFile,
        bookHash: String,
        extension: String,
    ): StoredBookFile

    /** Copies a user-picked image into the book's own directory and returns its stored path. */
    suspend fun copyCoverToBookDirectory(
        file: PlatformFile,
        bookHash: String,
        extension: String,
    ): String?

    /** Removes the book's private directory after the book is deleted from the shelf. */
    suspend fun deleteBookDirectory(bookHash: String, bookFilePath: String)
}

expect class CoverImageBytesLoader() {
    suspend fun load(path: String): ByteArray?
}
