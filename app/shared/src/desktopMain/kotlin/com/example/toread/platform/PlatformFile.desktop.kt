package com.example.toread.platform

import java.awt.FileDialog
import java.awt.Frame
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

actual class PlatformFilePicker actual constructor() {
    actual suspend fun pickFiles(extensions: List<String>): List<PlatformFile> {
        val dialog = FileDialog(null as Frame?, "导入本地书籍", FileDialog.LOAD).apply {
            isMultipleMode = true
            file = extensions.joinToString(";") { "*.$it" }
            isVisible = true
        }

        return dialog.files.orEmpty().map { file ->
            platformFileFromPath(file.toPath().toAbsolutePath().toString())
        }
    }
}

/**
 * Describes a local file for the importer. Shared by the file picker and the drag-and-drop target so a
 * dropped file arrives with exactly the same name, MIME type and size a picked one does.
 */
internal fun platformFileFromPath(path: String): PlatformFile {
    val resolved = resolveLocalFilePath(path)
    return PlatformFile(
        displayName = resolved?.fileName?.toString() ?: fallbackDisplayName(path),
        path = resolved?.toString() ?: path.trim(),
        mimeType = resolved?.let { runCatching { Files.probeContentType(it) }.getOrNull() },
        sizeBytes = resolved?.let { runCatching { Files.size(it) }.getOrNull() },
    )
}

/**
 * Resolves what a platform hands us into a real path.
 *
 * A desktop drop delivers **`file:` URIs** (`file:/E:/Downloads/书名%20(全8册).epub`), not paths — the
 * picker delivers plain paths. `Paths.get` rejects a URI with `Illegal char <:>`, so the URI form has to
 * go through `Paths.get(URI)`, which also percent-decodes the name. Anything unresolvable returns null
 * and the caller keeps the raw string, so a bad item is reported per file instead of throwing.
 */
internal fun resolveLocalFilePath(pathOrUri: String): Path? {
    val trimmed = pathOrUri.trim().trim('"')
    if (trimmed.isEmpty()) return null
    val parsed = runCatching {
        if (trimmed.startsWith("file:", ignoreCase = true)) {
            Paths.get(URI(trimmed))
        } else {
            Paths.get(trimmed)
        }
    }.getOrNull()
    return (parsed ?: windowsFileUriPath(trimmed))?.toAbsolutePath()
}

/**
 * Last resort for a `file:` string that is not a valid URI — an encoder that left spaces raw, or a bare
 * `file:C:\books\a.epub`. Only drive-letter candidates are accepted, so a Unix path or a UNC share can
 * never be silently mangled into a relative one.
 */
private fun windowsFileUriPath(value: String): Path? {
    if (!value.startsWith("file:", ignoreCase = true)) return null
    val candidate = value.substringAfter(':').trimStart('/')
    if (candidate.length < 2 || candidate[1] != ':') return null
    return runCatching { Paths.get(candidate) }.getOrNull()
}

private fun fallbackDisplayName(pathOrUri: String): String {
    val trimmed = pathOrUri.trim().trim('"')
    val lastSegment = trimmed.substringAfterLast('/').substringAfterLast('\\')
    if (!trimmed.startsWith("file:", ignoreCase = true)) return lastSegment
    val decoded = windowsFileUriPath(trimmed)?.fileName?.toString()
        ?: runCatching { Paths.get(URI(lastSegment)).fileName?.toString() }.getOrNull()
    return decoded ?: lastSegment
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
        val bookDirectory = bookDirectory(bookHash)
        Files.createDirectories(bookDirectory)
        val destination = bookDirectory.resolve("original.$extension")
        Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)

        return StoredBookFile(
            path = destination.toAbsolutePath().toString(),
            sizeBytes = Files.size(destination),
        )
    }

    actual suspend fun copyCoverToBookDirectory(
        file: PlatformFile,
        bookHash: String,
        extension: String,
    ): String? {
        val source = resolveFilePathForCopy(file) ?: return null
        val destination = bookDirectory(bookHash).resolve("cover.$extension")
        return runCatching {
            Files.createDirectories(destination.parent)
            Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            destination.toAbsolutePath().toString()
        }.getOrNull()
    }

    actual suspend fun deleteBookDirectory(bookHash: String, bookFilePath: String) {
        val root = booksRoot()
        val directory = bookDirectory(bookHash)
        runCatching {
            // Only ever delete inside the app's own books root, and only the book's own directory.
            val normalizedRoot = root.toAbsolutePath().normalize()
            val normalizedDirectory = directory.toAbsolutePath().normalize()
            if (normalizedDirectory.parent != normalizedRoot) return
            Files.walk(normalizedDirectory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
        // A book file that somehow lives outside the private directory is left on disk on purpose.
    }
}

private fun booksRoot(): Path =
    Paths.get(System.getProperty("user.home"), ".toread", "books")

private fun bookDirectory(bookHash: String): Path = booksRoot().resolve(bookHash)

private fun resolveFilePathForCopy(file: PlatformFile): Path? {
    file.path?.let { raw ->
        resolveLocalFilePath(raw)?.let { return it }
    }
    return file.uri?.let { uri -> runCatching { Paths.get(URI(uri)) }.getOrNull() }
}

actual class CoverImageBytesLoader actual constructor() {
    actual suspend fun load(path: String): ByteArray? =
        runCatching {
            val source = Paths.get(path)
            if (Files.isRegularFile(source)) Files.readAllBytes(source) else null
        }.getOrNull()
}
