package com.example.toread.importing

expect class BookCoverExtractor() {
    suspend fun extractCover(
        bookFilePath: String,
        bookHash: String,
        format: String,
    ): String?
}
