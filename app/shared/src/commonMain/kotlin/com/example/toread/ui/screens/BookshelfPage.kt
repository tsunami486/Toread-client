package com.example.toread.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.toread.library.BookshelfBook
import com.example.toread.platform.CoverImageBytesLoader
import com.example.toread.ui.text.ToreadStrings
import com.example.toread.ui.theme.toreadColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.roundToInt

private enum class BookshelfFilter(val label: String) {
    All(ToreadStrings.FilterAll),
    Reading(ToreadStrings.FilterReading),
    Unread(ToreadStrings.FilterUnread),
    Finished(ToreadStrings.FilterFinished),
}

private enum class BookshelfLayoutMode {
    List,
    Grid,
}

private sealed interface BookshelfGridEntry {
    data class Book(val book: BookshelfBook) : BookshelfGridEntry
    data object Import : BookshelfGridEntry
}

private data class BookshelfSpacingSpec(
    val listCardSpacing: Dp,
    val gridHorizontalSpacing: Dp,
    val gridVerticalSpacing: Dp,
    val gridColumnCount: Int,
)

@Composable
internal fun BookshelfPage(
    books: List<BookshelfBook>,
    isDesktop: Boolean = false,
    onImport: () -> Unit,
    onOpenBook: (BookshelfBook) -> Unit,
    onSettings: () -> Unit,
    onSync: () -> Unit = {},
    isRefreshing: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(BookshelfFilter.All) }
    var layoutMode by remember { mutableStateOf(BookshelfLayoutMode.Grid) }
    val spacingSpec = remember(isDesktop) {
        if (isDesktop) {
            BookshelfSpacingSpec(
                listCardSpacing = 18.dp,
                gridHorizontalSpacing = 30.dp,
                gridVerticalSpacing = 36.dp,
                gridColumnCount = 5,
            )
        } else {
            BookshelfSpacingSpec(
                listCardSpacing = 12.dp,
                gridHorizontalSpacing = 18.dp,
                gridVerticalSpacing = 22.dp,
                gridColumnCount = 3,
            )
        }
    }
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
                searchVisible = searchVisible,
                onSearchClick = { searchVisible = !searchVisible },
                onSync = onSync,
                onSettings = onSettings,
                isRefreshing = isRefreshing,
            )
            if (searchVisible) {
                BookshelfSearchBox(query = query, onQueryChange = { query = it })
            }
            continueBook?.let { book ->
                ContinueReadingSection(book = book, onOpenBook = onOpenBook)
            }
            BookshelfSectionHeader(
                selectedFilter = selectedFilter,
                layoutMode = layoutMode,
                onSelectFilter = { selectedFilter = it },
                onToggleLayoutMode = {
                    layoutMode = when (layoutMode) {
                        BookshelfLayoutMode.Grid -> BookshelfLayoutMode.List
                        BookshelfLayoutMode.List -> BookshelfLayoutMode.Grid
                    }
                },
            )

            if (visibleBooks.isEmpty()) {
                EmptyBookshelfCard(
                    hasBooks = books.isNotEmpty(),
                )
            }
            when (layoutMode) {
                BookshelfLayoutMode.List -> BookshelfList(
                    books = visibleBooks,
                    cardSpacing = spacingSpec.listCardSpacing,
                    onOpenBook = onOpenBook,
                    onImport = onImport,
                )
                BookshelfLayoutMode.Grid -> BookshelfGrid(
                    books = visibleBooks,
                    spacingSpec = spacingSpec,
                    onOpenBook = onOpenBook,
                    onImport = onImport,
                )
            }
        }
    }
}

@Composable
private fun BookshelfList(
    books: List<BookshelfBook>,
    cardSpacing: Dp,
    onOpenBook: (BookshelfBook) -> Unit,
    onImport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(cardSpacing)) {
        books.forEach { book ->
            BookCard(book = book, onOpenBook = onOpenBook)
        }
        AddBookListCard(onImport = onImport)
    }
}

@Composable
private fun BookshelfGrid(
    books: List<BookshelfBook>,
    spacingSpec: BookshelfSpacingSpec,
    onOpenBook: (BookshelfBook) -> Unit,
    onImport: () -> Unit,
) {
    val entries = remember(books) {
        books.map { BookshelfGridEntry.Book(it) } + BookshelfGridEntry.Import
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacingSpec.gridVerticalSpacing)) {
        entries.chunked(spacingSpec.gridColumnCount).forEach { rowEntries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacingSpec.gridHorizontalSpacing),
            ) {
                rowEntries.forEach { entry ->
                    Box(modifier = Modifier.weight(1f)) {
                        when (entry) {
                            is BookshelfGridEntry.Book -> GridBookCard(
                                book = entry.book,
                                onOpenBook = onOpenBook,
                            )
                            BookshelfGridEntry.Import -> AddBookGridCard(onImport = onImport)
                        }
                    }
                }
                repeat(spacingSpec.gridColumnCount - rowEntries.size) {
                    Spacer(modifier = Modifier.weight(1f))
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
    searchVisible: Boolean,
    onSearchClick: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    isRefreshing: Boolean,
) {
    val syncSummary = ToreadStrings.syncPendingSummary(pendingSyncCount, failedSyncCount)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ToreadStrings.BookshelfTitle, color = MaterialTheme.colorScheme.onSurface, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(
                text = ToreadStrings.bookshelfSummary(bookCount, syncSummary),
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
                imageVector = Icons.Outlined.Search,
                contentDescription = if (searchVisible) ToreadStrings.HideSearch else ToreadStrings.Search,
                onClick = onSearchClick,
            )
            HeaderIconButton(
                imageVector = Icons.Outlined.Settings,
                contentDescription = ToreadStrings.Settings,
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
                contentDescription = ToreadStrings.Refresh,
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
        placeholder = { Text(ToreadStrings.SearchBooksPlaceholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = ToreadStrings.Search,
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
        SectionTitle(ToreadStrings.ContinueReading)
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
                        Text(ToreadStrings.lastReadAt(book.lastReadLabel()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.End,
//                ) {
//                    ReaderActionButton(
//                        text = ToreadStrings.ContinueReading,
//                        onClick = { onOpenBook(book) },
//                    )
//                }
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
private fun BookshelfSectionHeader(
    selectedFilter: BookshelfFilter,
    layoutMode: BookshelfLayoutMode,
    onSelectFilter: (BookshelfFilter) -> Unit,
    onToggleLayoutMode: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle(text = ToreadStrings.BooksSection)
            Box {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    val arrows = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                    Icon(
                        imageVector = arrows,
                        contentDescription = ToreadStrings.FilterBooks,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    BookshelfFilter.entries.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.label) },
                            onClick = {
                                onSelectFilter(filter)
                                expanded = false
                            },
                        )
                    }
                }
            }
            if (selectedFilter != BookshelfFilter.All) {
                Text(
                    text = selectedFilter.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        }
        HeaderIconButton(
            imageVector = when (layoutMode) {
                BookshelfLayoutMode.List -> Icons.Outlined.GridView
                BookshelfLayoutMode.Grid -> Icons.Outlined.ViewDay
            },
            contentDescription = when (layoutMode) {
                BookshelfLayoutMode.List -> ToreadStrings.SwitchToGridView
                BookshelfLayoutMode.Grid -> ToreadStrings.SwitchToListView
            },
            onClick = onToggleLayoutMode,
        )
    }
}

@Composable
private fun EmptyBookshelfCard(hasBooks: Boolean) {
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
                if (hasBooks) ToreadStrings.NoMatchingBooks else ToreadStrings.NoReadableBooks,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (hasBooks) ToreadStrings.TryAnotherSearch else ToreadStrings.EmptyBookshelfHint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddBookListCard(onImport: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onImport() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = ToreadStrings.ImportBooks,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                modifier = Modifier.size(46.dp),
            )
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
        }
    }
}

@Composable
private fun GridBookCard(
    book: BookshelfBook,
    onOpenBook: (BookshelfBook) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenBook(book) },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        BookCover(
            book = book,
            showSyncBadge = true,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f),
        )
        Text(
            text = book.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.gridMetaLine(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddBookGridCard(onImport: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable { onImport() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = ToreadStrings.ImportBooks,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
            modifier = Modifier.size(52.dp),
        )
    }
}

@Composable
private fun BookCover(
    book: BookshelfBook,
    modifier: Modifier = Modifier
        .width(72.dp)
        .height(104.dp),
    showSyncBadge: Boolean = false,
) {
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
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(coverBrush)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val loadedCover = coverBitmap
        if (loadedCover != null) {
            Image(
                bitmap = loadedCover,
                contentDescription = ToreadStrings.bookCoverContentDescription(book.title),
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
        if (showSyncBadge) {
            SyncStatusBadge(
                status = book.syncStatus,
                modifier = Modifier
                    .align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun SyncStatusBadge(status: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(25.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.26f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = status.syncStatusIcon(),
            contentDescription = status.displaySyncStatus(),
            tint = Color.White.copy(alpha = 0.74f),
            modifier = Modifier.size(16.dp),
        )
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

private fun BookshelfBook.matchesFilter(filter: BookshelfFilter): Boolean =
    when (filter) {
        BookshelfFilter.All -> true
        BookshelfFilter.Reading -> progress > 0.0 && progress < 0.995
        BookshelfFilter.Unread -> progress <= 0.0
        BookshelfFilter.Finished -> progress >= 0.995
    }

private fun BookshelfBook.metaLine(): String =
    "${format.uppercase()} · ${author ?: ToreadStrings.UnknownAuthor}"

private fun BookshelfBook.gridMetaLine(): String =
    "${progressGridLabel()} · ${format.uppercase()}"

private fun BookshelfBook.progressLabel(): String =
    ToreadStrings.bookProgress(progress)

private fun BookshelfBook.progressGridLabel(): String =
    when {
        progress <= 0.0 -> ToreadStrings.Unread
        progress >= 0.995 -> ToreadStrings.ReadFinished
        else -> ToreadStrings.bookGridProgress(progress)
    }

private fun BookshelfBook.lastReadLabel(): String =
    if (lastReadAt.isNullOrBlank()) ToreadStrings.NotStarted else ToreadStrings.JustNow

private fun BookshelfBook.fileName(): String =
    filePath.substringAfterLast('/').substringAfterLast('\\')

private fun String.normalizedSyncStatus(): String =
    trim().lowercase()

private fun String.isPendingSync(): Boolean =
    normalizedSyncStatus() == "pending"

private fun String.displaySyncStatus(): String =
    when (normalizedSyncStatus()) {
        "synced" -> ToreadStrings.Synced
        "failed" -> ToreadStrings.SyncFailed
        "pending" -> ToreadStrings.WaitingSync
        else -> ToreadStrings.WaitingSync
    }

private fun String.syncStatusIcon(): ImageVector =
    when (normalizedSyncStatus()) {
        "synced" -> Icons.Outlined.CloudDone
        "failed" -> Icons.Outlined.CloudOff
        else -> Icons.Default.CloudSync
    }
