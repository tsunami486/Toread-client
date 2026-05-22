package com.example.toread.platform

import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

actual class PlatformFilePicker actual constructor() {
    actual suspend fun pickFiles(extensions: List<String>): List<PlatformFile> {
        val dialog = FileDialog(null as Frame?, "Import local books", FileDialog.LOAD).apply {
            isMultipleMode = true
            file = extensions.joinToString(";") { "*.$it" }
            isVisible = true
        }

        return dialog.files.orEmpty().map { file ->
            val path = file.toPath()
            PlatformFile(
                displayName = file.name,
                path = path.toAbsolutePath().toString(),
                mimeType = runCatching { Files.probeContentType(path) }.getOrNull(),
                sizeBytes = runCatching { Files.size(path) }.getOrNull(),
            )
        }
    }
}

actual class SecureTokenStore actual constructor() {
    private var token: String? = null

    actual suspend fun saveToken(token: String) {
        this.token = token
    }

    actual suspend fun getToken(): String? = token

    actual suspend fun clear() {
        token = null
    }
}

actual class FileHasher actual constructor() {
    actual suspend fun sha256(file: PlatformFile): String {
        val path = file.path?.let { Paths.get(it) }
            ?: error("Desktop file path is required for hashing.")
        return sha256(path)
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

actual class BookFileStorage actual constructor() {
    actual suspend fun copyToPrivateBookDirectory(
        file: PlatformFile,
        bookHash: String,
        extension: String,
    ): StoredBookFile {
        val source = file.path?.let { Paths.get(it) }
            ?: error("Desktop file path is required for copying.")
        val bookDirectory = Paths.get(System.getProperty("user.home"), ".toread", "books", bookHash)
        Files.createDirectories(bookDirectory)
        val destination = bookDirectory.resolve("original.$extension")
        Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)

        return StoredBookFile(
            path = destination.toAbsolutePath().toString(),
            sizeBytes = Files.size(destination),
        )
    }
}
