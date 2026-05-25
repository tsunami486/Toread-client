package com.example.toread.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.library.BookshelfBook
import com.example.toread.ui.components.ScreenColumn
import kotlin.math.roundToInt

@Composable
internal fun BookshelfPage(
    books: List<BookshelfBook>,
    onImport: () -> Unit,
    onOpenBook: (BookshelfBook) -> Unit,
    onSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredBooks = remember(books, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            books
        } else {
            books.filter { book ->
                book.title.contains(keyword, ignoreCase = true) ||
                    book.author.orEmpty().contains(keyword, ignoreCase = true) ||
                    book.format.contains(keyword, ignoreCase = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenColumn {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("书架", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Button(onClick = onImport) {
                    Text("导入")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索本地书架") },
                singleLine = true,
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            )

            if (filteredBooks.isEmpty()) {
                EmptyBookshelfCard(onImport = onImport)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    filteredBooks.forEach { book ->
                        BookRow(book = book, onOpenBook = onOpenBook)
                    }
                }
            }
        }

        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(56.dp),
        ) {
            Text("⚙", fontSize = 28.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun EmptyBookshelfCard(onImport: () -> Unit) {
    Card(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("还没有可阅读的书", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("导入 EPUB、PDF 或 TXT 文件后会显示在这里。")
            Button(onClick = onImport) {
                Text("选择本地文件")
            }
        }
    }
}

@Composable
private fun BookRow(book: BookshelfBook, onOpenBook: (BookshelfBook) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenBook(book) }
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book = book)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${book.format.uppercase()} · ${book.author ?: "未知作者"}")
                LinearProgressIndicator(
                    progress = { book.progress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("进度 ${(book.progress * 100).roundToInt()}% · 同步状态 ${book.syncStatus}")
            }
            Button(onClick = { onOpenBook(book) }) {
                Text("打开")
            }
        }
    }
}

@Composable
private fun BookCover(book: BookshelfBook) {
    val format = book.format.lowercase()
    val isTxt = format == "txt"
    val coverColor = when (format) {
        "epub" -> Color(0xFF456F5B)
        "pdf" -> Color(0xFF7B4E42)
        else -> Color.White
    }
    val textColor = if (isTxt) Color.Transparent else Color.White

    Box(
        modifier = Modifier
            .width(76.dp)
            .height(112.dp)
            .background(coverColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!isTxt) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(format.uppercase(), color = textColor, fontWeight = FontWeight.Bold)
                Text(
                    book.title,
                    color = textColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
