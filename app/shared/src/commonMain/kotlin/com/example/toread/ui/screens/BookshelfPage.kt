package com.example.toread.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.library.BookshelfBook
import kotlin.math.roundToInt

private val BookshelfBackground = Color(0xFFFAF7FC)
private val BookshelfPrimary = Color(0xFF6F4BB2)
private val BookshelfCard = Color(0xFFFFFFFF)
private val BookshelfWeakBackground = Color(0xFFF1ECF6)
private val BookshelfText = Color(0xFF1F1B24)
private val BookshelfSecondaryText = Color(0xFF6B6472)
private val BookshelfBorder = Color(0xFFE2DAEA)
private val SyncSuccess = Color(0xFF2E7D32)
private val SyncPending = Color(0xFF8A6D1D)
private val SyncFailed = Color(0xFFC62828)

private enum class BookshelfFilter(val label: String) {
    All("全部"),
    Reading("在读"),
    Unread("未读"),
    Finished("已读"),
}

@Composable
internal fun BookshelfPage(
    books: List<BookshelfBook>,
    onImport: () -> Unit,
    onOpenBook: (BookshelfBook) -> Unit,
    onSettings: () -> Unit,
    onSync: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(BookshelfFilter.All) }
    val pendingSyncCount = books.count { it.syncStatus.isPendingSync() }
    val continueBook = remember(books) {
        books
            .filter { it.lastReadAt != null || it.progress > 0.0 }
            .maxByOrNull { it.lastReadAt.orEmpty() }
    }
    val visibleBooks = remember(books, query, selectedFilter) {
        val keyword = query.trim()
        books
            .asSequence()
            .filter { book -> book.matchesFilter(selectedFilter) }
            .filter { book ->
                keyword.isBlank() ||
                    book.title.contains(keyword, ignoreCase = true) ||
                    book.author.orEmpty().contains(keyword, ignoreCase = true) ||
                    book.fileName().contains(keyword, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<BookshelfBook> { it.lastReadAt != null }
                    .thenByDescending { it.lastReadAt.orEmpty() },
            )
            .toList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BookshelfBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BookshelfHeader(
                bookCount = books.size,
                pendingSyncCount = pendingSyncCount,
                onSync = onSync,
                onSettings = onSettings,
            )
            BookshelfSearchBox(query = query, onQueryChange = { query = it })
            Button(
                onClick = onImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BookshelfPrimary),
            ) {
                Text("+  导入 EPUB / PDF / TXT", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
            continueBook?.let { book ->
                ContinueReadingSection(book = book, onOpenBook = onOpenBook)
            }
            Text(
                text = "全部书籍",
                color = BookshelfText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            BookshelfFilters(selectedFilter = selectedFilter, onSelectFilter = { selectedFilter = it })

            if (visibleBooks.isEmpty()) {
                EmptyBookshelfCard(
                    hasBooks = books.isNotEmpty(),
                    onImport = onImport,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    visibleBooks.forEach { book ->
                        BookCard(book = book, onOpenBook = onOpenBook)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookshelfHeader(
    bookCount: Int,
    pendingSyncCount: Int,
    onSync: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("书架", color = BookshelfText, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "本地书库 · $bookCount 本书 · $pendingSyncCount 项等待同步",
                color = BookshelfSecondaryText,
                fontSize = 15.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeaderIconButton(text = "↻", onClick = onSync)
            HeaderIconButton(text = "⚙", onClick = onSettings)
        }
    }
}

@Composable
private fun HeaderIconButton(text: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BookshelfWeakBackground)
            .padding(horizontal = 5.dp),
    ) {
        Text(text = text, color = BookshelfText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BookshelfSearchBox(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("搜索书名、作者或文件名", color = BookshelfSecondaryText) },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        leadingIcon = { Text("⌕", color = BookshelfSecondaryText, fontSize = 28.sp) },
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .background(BookshelfCard, RoundedCornerShape(24.dp)),
    )
}

@Composable
private fun ContinueReadingSection(book: BookshelfBook, onOpenBook: (BookshelfBook) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("继续阅读")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BookshelfCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBook(book) }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookCover(book = book)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(
                            book.title,
                            color = BookshelfText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(book.metaLine(), color = BookshelfSecondaryText, fontSize = 14.sp)
                        Text(book.progressLabel(), color = BookshelfPrimary, fontSize = 14.sp)
                        ProgressLine(progress = book.progress)
                        Text("上次阅读：${book.lastReadLabel()}", color = BookshelfSecondaryText, fontSize = 13.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    ReaderActionButton(
                        text = "继续阅读",
                        onClick = { onOpenBook(book) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(24.dp)
                .background(BookshelfPrimary, RoundedCornerShape(4.dp)),
        )
        Text(text, color = BookshelfText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BookshelfFilters(
    selectedFilter: BookshelfFilter,
    onSelectFilter: (BookshelfFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BookshelfFilter.entries.forEach { filter ->
            val selected = filter == selectedFilter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (selected) BookshelfPrimary else BookshelfBackground)
                    .border(1.dp, if (selected) BookshelfPrimary else BookshelfBorder, RoundedCornerShape(22.dp))
                    .clickable { onSelectFilter(filter) }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = filter.label,
                    color = if (selected) Color.White else BookshelfSecondaryText,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun EmptyBookshelfCard(hasBooks: Boolean, onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BookshelfCard),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (hasBooks) "没有符合条件的书" else "还没有可阅读的书",
                color = BookshelfText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (hasBooks) "换个关键词或筛选项试试。" else "导入 EPUB、PDF 或 TXT 文件后会显示在这里。",
                color = BookshelfSecondaryText,
            )
            Button(
                onClick = onImport,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BookshelfPrimary),
            ) {
                Text("导入书籍")
            }
        }
    }
}

@Composable
private fun BookCard(book: BookshelfBook, onOpenBook: (BookshelfBook) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BookshelfCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenBook(book) }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(book = book)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        book.title,
                        color = BookshelfText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(book.metaLine(), color = BookshelfSecondaryText, fontSize = 14.sp)
                    Text(book.progressLabel(), color = BookshelfPrimary, fontSize = 14.sp)
                    ProgressLine(progress = book.progress)
                    SyncStatusLabel(status = book.syncStatus)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ReaderActionButton(
                    text = if (book.progress > 0.0) "继续阅读" else "打开",
                    onClick = { onOpenBook(book) },
                )
            }
        }
    }
}

@Composable
private fun BookCover(book: BookshelfBook) {
    val format = book.format.uppercase()
    val titleMark = book.title.firstOrNull { it.isLetterOrDigit() }?.toString() ?: format
    val coverBrush = when (book.format.lowercase()) {
        "epub" -> Brush.linearGradient(listOf(Color(0xFFD9D0EA), Color(0xFF8A6CC5)))
        "pdf" -> Brush.linearGradient(listOf(Color(0xFFE8D7D2), Color(0xFFB56B5B)))
        else -> Brush.linearGradient(listOf(Color(0xFFDFD7EF), Color(0xFF7D64B4)))
    }

    Box(
        modifier = Modifier
            .width(72.dp)
            .height(104.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(coverBrush)
            .border(1.dp, BookshelfBorder, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = titleMark,
                color = Color.White,
                fontSize = if (titleMark.length > 1) 18.sp else 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(format, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, letterSpacing = 3.sp)
        }
    }
}

@Composable
private fun ProgressLine(progress: Double) {
    LinearProgressIndicator(
        progress = { progress.toFloat().coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = BookshelfPrimary,
        trackColor = BookshelfWeakBackground,
    )
}

@Composable
private fun SyncStatusLabel(status: String) {
    val color = when (status.normalizedSyncStatus()) {
        "synced" -> SyncSuccess
        "failed" -> SyncFailed
        else -> SyncPending
    }
    Text(text = status.displaySyncStatus(), color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun ReaderActionButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .height(34.dp)
            .widthIn(min = 76.dp, max = 104.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BookshelfPrimary),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private fun BookshelfBook.matchesFilter(filter: BookshelfFilter): Boolean =
    when (filter) {
        BookshelfFilter.All -> true
        BookshelfFilter.Reading -> progress > 0.0 && progress < 0.995
        BookshelfFilter.Unread -> progress <= 0.0
        BookshelfFilter.Finished -> progress >= 0.995
    }

private fun BookshelfBook.metaLine(): String =
    "${format.uppercase()} · ${author ?: "未知作者"}"

private fun BookshelfBook.progressLabel(): String =
    "已读 ${(progress.coerceIn(0.0, 1.0) * 100).roundToInt()}%"

private fun BookshelfBook.lastReadLabel(): String =
    if (lastReadAt.isNullOrBlank()) "尚未开始" else "刚刚"

private fun BookshelfBook.fileName(): String =
    filePath.substringAfterLast('/').substringAfterLast('\\')

private fun String.normalizedSyncStatus(): String =
    trim().lowercase()

private fun String.isPendingSync(): Boolean =
    normalizedSyncStatus() == "pending"

private fun String.displaySyncStatus(): String =
    when (normalizedSyncStatus()) {
        "synced" -> "已同步"
        "failed" -> "同步失败"
        "pending" -> "等待同步"
        else -> "等待同步"
    }
