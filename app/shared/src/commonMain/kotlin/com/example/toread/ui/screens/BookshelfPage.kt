package com.example.toread.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.library.BookshelfBook
import com.example.toread.platform.CoverImageBytesLoader
import com.example.toread.ui.theme.toreadColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.roundToInt

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
    isRefreshing: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(BookshelfFilter.All) }
    val pendingSyncCount = books.count { it.syncStatus.isPendingSync() }
    val failedSyncCount = books.count { it.syncStatus.normalizedSyncStatus() == "failed" }
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
            .background(MaterialTheme.colorScheme.background),
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
                failedSyncCount = failedSyncCount,
                onSync = onSync,
                onSettings = onSettings,
                isRefreshing = isRefreshing,
            )
            BookshelfSearchBox(query = query, onQueryChange = { query = it })
            Button(
                onClick = onImport,
                modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("导入 EPUB / PDF / TXT", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
            continueBook?.let { book ->
                ContinueReadingSection(book = book, onOpenBook = onOpenBook)
            }
            Text(
                text = "全部书籍",
                color = MaterialTheme.colorScheme.onSurface,
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
    failedSyncCount: Int,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    isRefreshing: Boolean,
) {
    val syncSummary = buildList {
        add("$pendingSyncCount 项等待同步")
        if (failedSyncCount > 0) add("$failedSyncCount 项同步失败")
    }.joinToString(" · ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("书架", color = MaterialTheme.colorScheme.onSurface, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "本地书库 · $bookCount 本书 · $syncSummary",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RefreshHeaderIconButton(
                isRefreshing = isRefreshing,
                onClick = onSync,
            )
            HeaderIconButton(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "设置",
                onClick = onSettings,
            )
        }
    }
}

@Composable
private fun RefreshHeaderIconButton(
    isRefreshing: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        enabled = !isRefreshing,
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "刷新",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookshelfSearchBox(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("搜索书名、作者或文件名", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)),
    )
}

@Composable
private fun ContinueReadingSection(book: BookshelfBook, onOpenBook: (BookshelfBook) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("继续阅读")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(book.metaLine(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text(book.progressLabel(), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        ProgressLine(progress = book.progress)
                        Text("上次阅读：${book.lastReadLabel()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
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
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
        )
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
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
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(22.dp),
                    )
                    .clickable { onSelectFilter(filter) }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = filter.label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (hasBooks) "没有符合条件的书" else "还没有可阅读的书",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (hasBooks) "换个关键词或筛选项试试。" else "导入 EPUB、PDF 或 TXT 文件后会显示在这里。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onImport,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(book.metaLine(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(book.progressLabel(), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
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
    val toreadColors = MaterialTheme.toreadColors
    val coverBrush = when (book.format.lowercase()) {
        "epub" -> Brush.linearGradient(listOf(toreadColors.epubCoverStart, toreadColors.epubCoverEnd))
        "pdf" -> Brush.linearGradient(listOf(toreadColors.pdfCoverStart, toreadColors.pdfCoverEnd))
        else -> Brush.linearGradient(listOf(toreadColors.txtCoverStart, toreadColors.txtCoverEnd))
    }
    val coverLoader = remember { CoverImageBytesLoader() }
    var coverBitmap by remember(book.coverPath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(book.coverPath) {
        coverBitmap = null
        val coverPath = book.coverPath?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        coverBitmap = withContext(Dispatchers.Default) {
            runCatching {
                coverLoader.load(coverPath)?.decodeToImageBitmap()
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .width(72.dp)
            .height(104.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(coverBrush)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val loadedCover = coverBitmap
        if (loadedCover != null) {
            Image(
                bitmap = loadedCover,
                contentDescription = "${book.title} 封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
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
}

@Composable
private fun ProgressLine(progress: Double) {
    LinearProgressIndicator(
        progress = { progress.toFloat().coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
private fun SyncStatusLabel(status: String) {
    val color = when (status.normalizedSyncStatus()) {
        "synced" -> MaterialTheme.toreadColors.syncSuccess
        "failed" -> MaterialTheme.toreadColors.syncFailed
        else -> MaterialTheme.toreadColors.syncPending
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
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
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
