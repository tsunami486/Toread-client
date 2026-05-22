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
}
