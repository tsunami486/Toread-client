package com.example.toread.lan

import com.example.toread.importing.BookImportResult
import com.example.toread.importing.BookImportService
import com.example.toread.library.BookshelfRepository
import com.example.toread.platform.PlatformFile

/**
 * The one place that knows how a LAN exchange hangs together.
 *
 * It stays free of Compose so the flow can be exercised without a window: the page only renders what
 * this returns. Serving and pulling share the same index code, so both devices describe their shelf in
 * exactly the same terms.
 */
internal class LanSyncEngine(
    private val bookshelfRepository: BookshelfRepository,
    private val importService: BookImportService,
    private val server: LanServer = LanServer(),
    private val transfer: LanTransferClient = LanTransferClient(),
    private val tempFiles: LanTempFiles = LanTempFiles(),
) {
    fun localBooks(): List<LanBookSummary> =
        bookshelfRepository.listBooks().toLanBookSummaries()

    fun localHashes(): Set<String> =
        bookshelfRepository.listBooks().mapTo(mutableSetOf()) { it.bookHash }

    /**
     * Starts serving this device's shelf, read-only.
     *
     * The resolver only ever answers with a path the shelf already stores, and the server re-checks that
     * the path is inside the app's own books directory before serving a byte of it.
     */
    suspend fun startServing(request: LanServeRequest): LanServerHandle =
        server.start(
            request = request,
            indexProvider = { localBooks() },
            fileResolver = { bookHash ->
                bookshelfRepository.listBooks().firstOrNull { it.bookHash == bookHash }?.filePath
            },
        )

    /** Everything the peer offers: its identity plus its whole index. */
    suspend fun fetchPeer(address: LanPeerAddress): LanPeerResult<LanPeerInfo> {
        val info = when (val result = transfer.fetchInfo(address)) {
            is LanPeerResult.Failure -> return result
            is LanPeerResult.Success -> result.value
        }
        if (info.protocol != LanProtocolVersion) {
            return LanPeerResult.Failure(LanClientError.Incompatible(info.protocol))
        }
        return when (val books = transfer.fetchBooks(address)) {
            is LanPeerResult.Failure -> books
            is LanPeerResult.Success ->
                LanPeerResult.Success(
                    LanPeerInfo(
                        deviceName = info.deviceName,
                        platform = info.platform,
                        appVersion = info.appVersion,
                        books = books.value,
                    ),
                )
        }
    }

    /**
     * Pulls one book and lets the normal importer take it from there.
     *
     * The received file is staged, hashed and checked against the advertised SHA-256 first, then handed
     * to [BookImportService] as an ordinary picked file: dedupe, private-directory copy, cover, blurb and
     * the `book_metadata` queue item all come from the path every other import uses.
     */
    suspend fun receive(
        address: LanPeerAddress,
        book: LanBookSummary,
        onBytes: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): LanPeerResult<BookImportResult> {
        val downloaded = when (val result = transfer.download(address, book) { received ->
            onBytes(received, book.sizeBytes)
        }) {
            is LanPeerResult.Failure -> return result
            is LanPeerResult.Success -> result.value
        }
        return try {
            val results = importService.importFiles(
                listOf(PlatformFile(displayName = downloaded.fileName, path = downloaded.tempPath)),
            )
            val imported = results.firstOrNull()
                ?: BookImportResult.Failed(downloaded.fileName, "导入没有返回结果")
            LanPeerResult.Success(imported)
        } finally {
            tempFiles.delete(downloaded.tempPath)
        }
    }
}

/**
 * Verification hook: serve the shelf on a fixed port and code taken from the environment, so the
 * protocol can be exercised against a real running app over a real socket (see `tools/dev/README.md`).
 *
 * Returns null unless `TOREAD_LAN_SERVE_PORT` is set, so an ordinary launch never opens a port. The
 * value is read once at startup and never shown in the UI.
 */
internal expect fun lanServeRequestFromEnvironment(): LanServeRequest?
