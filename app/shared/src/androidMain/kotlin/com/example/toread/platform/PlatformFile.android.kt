package com.example.toread.platform

import com.example.toread.database.AndroidAppContext
import java.io.File
import java.security.MessageDigest

object AndroidPlatformFilePickerBridge {
    var pickFiles: (suspend (List<String>) -> List<PlatformFile>)? = null
}

object AndroidFileAccessBridge {
    var openInputStream: ((PlatformFile) -> java.io.InputStream?)? = null
}

actual class PlatformFilePicker actual constructor() {
    actual suspend fun pickFiles(extensions: List<String>): List<PlatformFile> =
        AndroidPlatformFilePickerBridge.pickFiles?.invoke(extensions).orEmpty()
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
        val inputStream = AndroidFileAccessBridge.openInputStream?.invoke(file)
            ?: error("Android file access is not configured.")
        return inputStream.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}

actual class BookFileStorage actual constructor() {
    actual suspend fun copyToPrivateBookDirectory(
        file: PlatformFile,
        bookHash: String,
        extension: String,
    ): StoredBookFile {
        val inputStream = AndroidFileAccessBridge.openInputStream?.invoke(file)
            ?: error("Android file access is not configured.")
        val bookDirectory = File(AndroidAppContext.applicationContext.filesDir, "books/$bookHash")
        bookDirectory.mkdirs()
        val destination = File(bookDirectory, "original.$extension")

        inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return StoredBookFile(
            path = destination.absolutePath,
            sizeBytes = destination.length(),
        )
    }

    actual suspend fun copyCoverToBookDirectory(
        file: PlatformFile,
        bookHash: String,
        extension: String,
    ): String? {
        val inputStream = AndroidFileAccessBridge.openInputStream?.invoke(file) ?: return null
        val bookDirectory = File(AndroidAppContext.applicationContext.filesDir, "books/$bookHash")
        return runCatching {
            bookDirectory.mkdirs()
            val destination = File(bookDirectory, "cover.$extension")
            inputStream.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            destination.absolutePath
        }.getOrNull()
    }

    actual suspend fun deleteBookDirectory(bookHash: String, bookFilePath: String) {
        val booksRoot = File(AndroidAppContext.applicationContext.filesDir, "books")
        val directory = File(booksRoot, bookHash)
        runCatching {
            // Only ever delete inside the app's own books root, and only the book's own directory.
            if (directory.canonicalFile.parentFile != booksRoot.canonicalFile) return
            directory.deleteRecursively()
        }
    }
}

actual class CoverImageBytesLoader actual constructor() {
    actual suspend fun load(path: String): ByteArray? =
        runCatching { File(path).takeIf { it.isFile }?.readBytes() }.getOrNull()
}
