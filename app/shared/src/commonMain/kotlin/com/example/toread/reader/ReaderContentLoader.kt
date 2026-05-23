package com.example.toread.reader

data class ReaderDocument(
    val title: String,
    val format: String,
    val text: String,
    val message: String? = null,
) {
    val isReadable: Boolean = text.isNotBlank()
}

expect class ReaderContentLoader() {
    suspend fun load(filePath: String, format: String, title: String): ReaderDocument
}
