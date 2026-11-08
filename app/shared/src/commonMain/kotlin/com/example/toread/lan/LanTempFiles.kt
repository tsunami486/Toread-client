package com.example.toread.lan

/**
 * A scratch file for one incoming transfer.
 *
 * The bytes are streamed straight to disk instead of being buffered in memory: a PDF or a long EPUB can
 * be a hundred megabytes, and the whole point of pulling a book is that it is a file, not a payload.
 * The writer exists so the common transfer code does not need any platform file type — the platform
 * only says *where* scratch files live.
 */
internal interface LanTempWriter {
    val path: String

    suspend fun write(buffer: ByteArray, offset: Int, count: Int)

    suspend fun close()
}

/**
 * Where an incoming transfer is staged before it is verified and imported.
 *
 * Stage-then-verify matters: the finished file is hashed with the same [FileHasher] the importer uses,
 * and only then handed to `BookImportService`. A peer that lies about a hash, or a connection that
 * truncates the stream, therefore produces a refused file rather than a broken book on the shelf.
 */
internal expect class LanTempFiles() {
    suspend fun open(fileName: String): LanTempWriter

    suspend fun delete(path: String)
}
