package com.example.toread.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.toread.library.BookshelfBook
import com.example.toread.platform.CoverImageBytesLoader
import com.example.toread.reader.EpubLocator
import com.example.toread.reader.PdfLocator
import com.example.toread.reader.ReaderContentLoader
import com.example.toread.reader.TxtLocator
import com.example.toread.ui.icons.ToreadSymbolIcon
import com.example.toread.ui.icons.ToreadSymbols
import com.example.toread.ui.text.ToreadStrings
import com.example.toread.ui.theme.ToreadTheme
import com.example.toread.ui.theme.toreadColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.decodeToImageBitmap

private enum class BookshelfFilter(val label: String) {
    All(ToreadStrings.FilterAll),
    Reading(ToreadStrings.FilterReading),
    Unread(ToreadStrings.FilterUnread),
    Finished(ToreadStrings.FilterFinished),
}

private enum class BookshelfSortMode(val label: String) {
    RecentRead(ToreadStrings.SortRecentRead),
    Title(ToreadStrings.SortTitle),
    Author(ToreadStrings.SortAuthor),
    Progress(ToreadStrings.SortProgress),
    Format(ToreadStrings.SortFormat),
    FileSize(ToreadStrings.SortFileSize),
}

internal enum class BookshelfLayoutMode(val storedValue: String) {
    List("list"),
    Grid("grid"),
    ;

    companion object {
        fun fromStoredValue(value: String?): BookshelfLayoutMode? =
            entries.firstOrNull { it.storedValue == value }
    }
}

private const val ListBookCoverAspectRatio = 72f / 104f
private val ListBookCardHeight = 156.dp
private val ListBookDetailsMaxWidth = 280.dp
private val ContinueReadingWideBreakpoint = 600.dp
private val BookshelfHorizontalPadding = 16.dp

@Composable
internal fun BookshelfPage(
    books: List<BookshelfBook>,
    readerContentLoader: ReaderContentLoader? = null,
    isDesktop: Boolean = false,
    layoutMode: BookshelfLayoutMode = BookshelfLayoutMode.Grid,
    onLayoutModeChange: (BookshelfLayoutMode) -> Unit = {},
    onImport: () -> Unit,
    onOpenBook: (BookshelfBook) -> Unit,
    onSettings: () -> Unit,
    onSync: () -> Unit = {},
    isRefreshing: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(BookshelfFilter.All) }
    var selectedSortMode by remember { mutableStateOf(BookshelfSortMode.RecentRead) }
    val pendingSyncCount = books.count { it.syncStatus.isPendingSync() }
    val failedSyncCount = books.count { it.syncStatus.normalizedSyncStatus() == "failed" }
    val continueBook = remember(books) { selectContinueReadingBook(books) }
    val visibleBooks = remember(books, query, selectedFilter, selectedSortMode) {
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
            .sortedByMode(selectedSortMode)
            .toList()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val responsiveSpec = remember(maxWidth) { bookshelfResponsiveSpec(maxWidth) }
        LaunchedEffect(responsiveSpec.persistentSearch) {
            if (!responsiveSpec.persistentSearch && query.isNotBlank()) {
                searchVisible = true
            }
        }
        val columns = when (layoutMode) {
            BookshelfLayoutMode.Grid -> GridCells.Adaptive(responsiveSpec.minGridCardWidth)
            BookshelfLayoutMode.List -> GridCells.Fixed(1)
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyVerticalGrid(
                columns = columns,
                modifier = Modifier
                    .widthIn(max = responsiveSpec.contentMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = BookshelfHorizontalPadding,
                    top = 18.dp,
                    end = BookshelfHorizontalPadding,
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(responsiveSpec.gridHorizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(
                    if (layoutMode == BookshelfLayoutMode.Grid) {
                        responsiveSpec.gridVerticalSpacing
                    } else {
                        responsiveSpec.listSpacing
                    },
                ),
            ) {
                item(key = "bookshelf-header", span = { GridItemSpan(maxLineSpan) }) {
                    BookshelfHeader(
                        bookCount = books.size,
                        pendingSyncCount = pendingSyncCount,
                        failedSyncCount = failedSyncCount,
                        searchVisible = searchVisible,
                        persistentSearch = responsiveSpec.persistentSearch,
                        persistentSearchWidth = responsiveSpec.persistentSearchWidth,
                        query = query,
                        onQueryChange = { query = it },
                        onSearchClick = { searchVisible = !searchVisible },
                        onSync = onSync,
                        onSettings = onSettings,
                        isRefreshing = isRefreshing,
                    )
                }
                if (!responsiveSpec.persistentSearch && searchVisible) {
                    item(key = "bookshelf-search", span = { GridItemSpan(maxLineSpan) }) {
                        ConstrainedShelfSection(responsiveSpec.constrainedSectionMaxWidth) {
                            BookshelfSearchBox(
                                query = query,
                                onQueryChange = { query = it },
                            )
                        }
                    }
                }
                continueBook?.let { book ->
                    item(key = "continue-${book.bookHash}", span = { GridItemSpan(maxLineSpan) }) {
                        ConstrainedShelfSection(responsiveSpec.constrainedSectionMaxWidth) {
                            ContinueReadingSection(
                                book = book,
                                readerContentLoader = readerContentLoader,
                                onOpenBook = onOpenBook,
                            )
                        }
                    }
                }
                item(key = "bookshelf-section-header", span = { GridItemSpan(maxLineSpan) }) {
                    BookshelfSectionHeader(
                        selectedFilter = selectedFilter,
                        selectedSortMode = selectedSortMode,
                        layoutMode = layoutMode,
                        onSelectFilter = { selectedFilter = it },
                        onSelectSortMode = { selectedSortMode = it },
                        onToggleLayoutMode = {
                            onLayoutModeChange(
                                when (layoutMode) {
                                    BookshelfLayoutMode.Grid -> BookshelfLayoutMode.List
                                    BookshelfLayoutMode.List -> BookshelfLayoutMode.Grid
                                },
                            )
                        },
                    )
                }
                if (visibleBooks.isEmpty()) {
                    item(key = "bookshelf-empty", span = { GridItemSpan(maxLineSpan) }) {
                        ConstrainedShelfSection(responsiveSpec.constrainedSectionMaxWidth) {
                            EmptyBookshelfCard(hasBooks = books.isNotEmpty())
                        }
                    }
                }
                when (layoutMode) {
                    BookshelfLayoutMode.List -> {
                        items(
                            items = visibleBooks,
                            key = { book -> "list-${book.bookHash}" },
                        ) { book ->
                            ConstrainedShelfSection(responsiveSpec.constrainedSectionMaxWidth) {
                                BookCard(
                                    book = book,
                                    enableHover = isDesktop,
                                    onOpenBook = onOpenBook,
                                )
                            }
                        }
                        item(key = "list-import") {
                            ConstrainedShelfSection(responsiveSpec.constrainedSectionMaxWidth) {
                                AddBookListCard(onImport = onImport)
                            }
                        }
                    }
                    BookshelfLayoutMode.Grid -> {
                        items(
                            items = visibleBooks,
                            key = { book -> "grid-${book.bookHash}" },
                        ) { book ->
                            GridBookCard(
                                book = book,
                                enableHover = isDesktop,
                                onOpenBook = onOpenBook,
                            )
                        }
                        item(key = "grid-import") {
                            AddBookGridCard(onImport = onImport)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConstrainedShelfSection(
    maxWidth: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun BookshelfHeader(
    bookCount: Int,
    pendingSyncCount: Int,
    failedSyncCount: Int,
    searchVisible: Boolean,
    persistentSearch: Boolean,
    persistentSearchWidth: Dp,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    isRefreshing: Boolean,
) {
    val syncSummary = ToreadStrings.syncPendingSummary(pendingSyncCount, failedSyncCount)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = ToreadStrings.BookshelfTitle,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (persistentSearch) {
                    BookshelfSearchBox(
                        query = query,
                        onQueryChange = onQueryChange,
                        modifier = Modifier.width(persistentSearchWidth),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                RefreshHeaderIconButton(
                    isRefreshing = isRefreshing,
                    onClick = onSync,
                )
                if (!persistentSearch) {
                    HeaderIconButton(
                        symbol = ToreadSymbols.Search,
                        contentDescription = if (searchVisible) ToreadStrings.HideSearch else ToreadStrings.Search,
                        onClick = onSearchClick,
                    )
                }
                HeaderIconButton(
                    symbol = ToreadSymbols.Settings,
                    contentDescription = ToreadStrings.Settings,
                    onClick = onSettings,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = ToreadStrings.bookshelfSummary(bookCount, syncSummary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth(),
                softWrap = true,
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
        modifier = Modifier.size(48.dp),
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            ToreadSymbolIcon(
                symbol = ToreadSymbols.Refresh,
                contentDescription = ToreadStrings.Refresh,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    symbol: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        ToreadSymbolIcon(
            symbol = symbol,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookshelfSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(ToreadStrings.SearchBooksPlaceholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        leadingIcon = {
            ToreadSymbolIcon(
                symbol = ToreadSymbols.Search,
                contentDescription = ToreadStrings.Search,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp)),
    )
}

@Composable
private fun ContinueReadingSection(
    book: BookshelfBook,
    readerContentLoader: ReaderContentLoader?,
    onOpenBook: (BookshelfBook) -> Unit,
) {
    val epubLocator = book.locator as? EpubLocator
    var resolvedEpubChapterTitle by remember(book.bookHash, epubLocator) { mutableStateOf<String?>(null) }
    LaunchedEffect(book.bookHash, epubLocator, readerContentLoader) {
        resolvedEpubChapterTitle = null
        val loader = readerContentLoader ?: return@LaunchedEffect
        val locator = epubLocator?.takeIf { it.chapterTitle.isNullOrBlank() } ?: return@LaunchedEffect
        resolvedEpubChapterTitle = try {
            loader.loadEpubPublicationIndex(
                filePath = book.filePath,
                title = book.title,
            )?.let { publication ->
                resolveContinueReadingEpubChapterTitle(locator, publication)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }
    val presentation = continueReadingPresentation(
        book = book,
        resolvedEpubChapterTitle = resolvedEpubChapterTitle,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(ToreadStrings.ContinueReading)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(ListBookCardHeight),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clickable(
                        onClickLabel = ToreadStrings.ContinueReading,
                        onClick = { onOpenBook(book) },
                    ),
            ) {
                val showActionButton = maxWidth >= ContinueReadingWideBreakpoint
                val fontScale = LocalDensity.current.fontScale
                val useCompactTextLayout = !showActionButton || fontScale > 1f
                val hideMetaForLargeText = fontScale >= 1.6f && presentation.locationLabel != null
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    BookCover(
                        book = book,
                        showSyncBadge = true,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(ListBookCoverAspectRatio),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(
                            if (useCompactTextLayout) 2.dp else 4.dp,
                        ),
                    ) {
                        Text(
                            text = book.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = if (useCompactTextLayout) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!hideMetaForLargeText) {
                            Text(
                                text = book.metaLine(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (presentation.locationLabel != null) {
                            Text(
                                text = presentation.locationLabel,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = if (fontScale >= 1.3f) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProgressLine(
                                progress = book.progress,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = book.progressPercentLabel(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    if (showActionButton) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .height(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(start = 14.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = ToreadStrings.ContinueReading,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            ToreadSymbolIcon(
                                symbol = ToreadSymbols.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                                autoMirror = true,
                            )
                        }
                    } else {
                        ToreadSymbolIcon(
                            symbol = ToreadSymbols.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .size(24.dp),
                            autoMirror = true,
                        )
                    }
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
private fun BookshelfSectionHeader(
    selectedFilter: BookshelfFilter,
    selectedSortMode: BookshelfSortMode,
    layoutMode: BookshelfLayoutMode,
    onSelectFilter: (BookshelfFilter) -> Unit,
    onSelectSortMode: (BookshelfSortMode) -> Unit,
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
                    val arrow = if (expanded) ToreadSymbols.KeyboardArrowUp else ToreadSymbols.KeyboardArrowDown
                    ToreadSymbolIcon(
                        symbol = arrow,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BookshelfSortMenu(
                selectedSortMode = selectedSortMode,
                onSelectSortMode = onSelectSortMode,
            )
            HeaderIconButton(
                symbol = when (layoutMode) {
                    BookshelfLayoutMode.List -> ToreadSymbols.GridView
                    BookshelfLayoutMode.Grid -> ToreadSymbols.ViewDay
                },
                contentDescription = when (layoutMode) {
                    BookshelfLayoutMode.List -> ToreadStrings.SwitchToGridView
                    BookshelfLayoutMode.Grid -> ToreadStrings.SwitchToListView
                },
                onClick = onToggleLayoutMode,
            )
        }
    }
}

@Composable
private fun BookshelfSortMenu(
    selectedSortMode: BookshelfSortMode,
    onSelectSortMode: (BookshelfSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = ToreadStrings.SortBooks,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            ToreadSymbolIcon(
                symbol = if (expanded) ToreadSymbols.KeyboardArrowUp else ToreadSymbols.KeyboardArrowDown,
                contentDescription = selectedSortMode.label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            BookshelfSortMode.entries.forEach { sortMode ->
                DropdownMenuItem(
                    text = { Text(sortMode.label) },
                    onClick = {
                        onSelectSortMode(sortMode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyBookshelfCard(hasBooks: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp),
            contentAlignment = Alignment.Center,
        ) {
            ToreadSymbolIcon(
                symbol = ToreadSymbols.Add,
                contentDescription = ToreadStrings.ImportBooks,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                modifier = Modifier.size(46.dp),
            )
        }
    }
}

@Composable
private fun BookCard(
    book: BookshelfBook,
    enableHover: Boolean,
    onOpenBook: (BookshelfBook) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = (enableHover && isHovered) || isFocused
    val useCompactTextLayout = LocalDensity.current.fontScale >= 1.3f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(ListBookCardHeight),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 4.dp else 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .hoverable(interactionSource = interactionSource, enabled = enableHover)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = { onOpenBook(book) },
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            BookCover(
                book = book,
                showSyncBadge = true,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(ListBookCoverAspectRatio),
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f, fill = false)
                    .widthIn(max = ListBookDetailsMaxWidth),
                verticalArrangement = Arrangement.spacedBy(if (useCompactTextLayout) 2.dp else 7.dp),
            ) {
                Text(
                    book.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (useCompactTextLayout) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.metaLine(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.progressLabel(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ProgressLine(progress = book.progress)
            }
        }
    }
}

@Composable
private fun GridBookCard(
    book: BookshelfBook,
    enableHover: Boolean,
    onOpenBook: (BookshelfBook) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = (enableHover && isHovered) || isFocused
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isHighlighted) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    Color.Transparent
                },
            )
            .hoverable(interactionSource = interactionSource, enabled = enableHover)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onOpenBook(book) },
            )
            .padding(6.dp),
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
            .padding(6.dp)
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(8.dp),
            )
            .clickable { onImport() },
        contentAlignment = Alignment.Center,
    ) {
        ToreadSymbolIcon(
            symbol = ToreadSymbols.Add,
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
    val fallbackPresentation = remember(book.title, book.format, book.bookHash) {
        fallbackCoverPresentation(
            title = book.title,
            format = book.format,
            bookHash = book.bookHash,
        )
    }
    val toreadColors = MaterialTheme.toreadColors
    val coverPalettes = listOf(
        listOf(toreadColors.epubCoverStart, toreadColors.epubCoverEnd),
        listOf(toreadColors.pdfCoverStart, toreadColors.pdfCoverEnd),
        listOf(toreadColors.txtCoverStart, toreadColors.txtCoverEnd),
    )
    val coverBrush = Brush.linearGradient(
        coverPalettes[fallbackPresentation.paletteIndex.coerceIn(coverPalettes.indices)],
    )
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.10f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = fallbackPresentation.mark,
                        color = Color.White,
                        fontSize = if (fallbackPresentation.mark.length > 1) 28.sp else 38.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
        ToreadSymbolIcon(
            symbol = status.syncStatusIcon(),
            contentDescription = status.displaySyncStatus(),
            tint = Color.White.copy(alpha = 0.74f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ProgressLine(
    progress: Double,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val clampedProgress = progress.toFloat().coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clampedProgress)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private fun BookshelfBook.matchesFilter(filter: BookshelfFilter): Boolean =
    when (filter) {
        BookshelfFilter.All -> true
        BookshelfFilter.Reading -> progress > 0.0 && progress < 0.995
        BookshelfFilter.Unread -> progress <= 0.0
        BookshelfFilter.Finished -> progress >= 0.995
    }

private fun Sequence<BookshelfBook>.sortedByMode(sortMode: BookshelfSortMode): Sequence<BookshelfBook> =
    sortedWith(sortMode.comparator())

private fun BookshelfSortMode.comparator(): Comparator<BookshelfBook> =
    when (this) {
        BookshelfSortMode.RecentRead ->
            compareByDescending<BookshelfBook> { it.lastReadAt != null }
                .thenByDescending { it.lastReadAt.orEmpty() }
                .thenBy { it.title.sortKey() }
        BookshelfSortMode.Title ->
            compareBy<BookshelfBook> { it.title.sortKey() }
                .thenBy { it.author.sortKey() }
        BookshelfSortMode.Author ->
            compareBy<BookshelfBook> { it.author.isNullOrBlank() }
                .thenBy { it.author.sortKey() }
                .thenBy { it.title.sortKey() }
        BookshelfSortMode.Progress ->
            compareByDescending<BookshelfBook> { it.progress }
                .thenBy { it.title.sortKey() }
        BookshelfSortMode.Format ->
            compareBy<BookshelfBook> { it.format.sortKey() }
                .thenBy { it.title.sortKey() }
        BookshelfSortMode.FileSize ->
            compareByDescending<BookshelfBook> { it.fileSize }
                .thenBy { it.title.sortKey() }
    }

private fun String?.sortKey(): String =
    orEmpty().trim().lowercase()

private fun BookshelfBook.metaLine(): String =
    "${format.uppercase()} · ${author ?: ToreadStrings.UnknownAuthor}"

private fun BookshelfBook.gridMetaLine(): String =
    "${progressGridLabel()} · ${format.uppercase()}"

private fun BookshelfBook.progressLabel(): String =
    ToreadStrings.bookProgress(progress)

private fun BookshelfBook.progressPercentLabel(): String =
    ToreadStrings.bookProgressPercent(progress)

private fun BookshelfBook.progressGridLabel(): String =
    when {
        progress <= 0.0 -> ToreadStrings.Unread
        progress >= 0.995 -> ToreadStrings.ReadFinished
        else -> ToreadStrings.bookGridProgress(progress)
    }

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

private fun String.syncStatusIcon(): DrawableResource =
    when (normalizedSyncStatus()) {
        "synced" -> ToreadSymbols.CloudDone
        "failed" -> ToreadSymbols.CloudOff
        else -> ToreadSymbols.CloudSync
    }

@Preview(name = ToreadStrings.BookshelfPreviewCompactLight, widthDp = 360, heightDp = 800)
@Composable
private fun BookshelfCompactPreview() {
    BookshelfPreviewContent(darkTheme = false, isDesktop = false)
}

@Preview(name = ToreadStrings.BookshelfPreviewMediumDark, widthDp = 600, heightDp = 800)
@Composable
private fun BookshelfMediumPreview() {
    BookshelfPreviewContent(darkTheme = true, isDesktop = false)
}

@Preview(name = ToreadStrings.BookshelfPreviewExpandedLight, widthDp = 840, heightDp = 800)
@Composable
private fun BookshelfExpandedPreview() {
    BookshelfPreviewContent(darkTheme = false, isDesktop = true)
}

@Preview(name = ToreadStrings.BookshelfPreviewDesktopDark, widthDp = 1280, heightDp = 800)
@Composable
private fun BookshelfDesktopPreview() {
    BookshelfPreviewContent(darkTheme = true, isDesktop = true)
}

@Preview(name = ToreadStrings.BookshelfPreviewUltrawideLight, widthDp = 1920, heightDp = 800)
@Composable
private fun BookshelfUltrawidePreview() {
    BookshelfPreviewContent(darkTheme = false, isDesktop = true)
}

@Composable
private fun BookshelfPreviewContent(
    darkTheme: Boolean,
    isDesktop: Boolean,
) {
    ToreadTheme(useDynamicColor = false, darkTheme = darkTheme) {
        BookshelfPage(
            books = previewBooks(),
            readerContentLoader = null,
            isDesktop = isDesktop,
            layoutMode = BookshelfLayoutMode.Grid,
            onImport = {},
            onOpenBook = {},
            onSettings = {},
        )
    }
}

private fun previewBooks(): List<BookshelfBook> = listOf(
    previewBook(
        hash = "preview-mystery",
        title = ToreadStrings.BookshelfPreviewLongTitle,
        author = ToreadStrings.BookshelfPreviewAuthor,
        format = "epub",
        progress = 0.63,
        lastReadAt = "2026-08-17T10:00:00Z",
        locator = EpubLocator(
            chapterHref = "OPS/chapter-42.xhtml",
            chapterIndex = 42,
            chapterProgress = 0.4,
            globalProgress = 0.63,
            chapterTitle = ToreadStrings.BookshelfPreviewLongChapterTitle,
        ),
    ),
    previewBook(
        hash = "preview-dragon",
        title = ToreadStrings.BookshelfPreviewDragonTitle,
        author = ToreadStrings.BookshelfPreviewDragonAuthor,
        format = "epub",
        progress = 0.88,
        lastReadAt = "2026-08-16T10:00:00Z",
    ),
    previewBook(
        hash = "preview-java",
        title = ToreadStrings.BookshelfPreviewJavaTitle,
        author = null,
        format = "pdf",
        progress = 0.556,
        lastReadAt = "2026-08-15T10:00:00Z",
        locator = PdfLocator(pageIndex = 126, pageOffsetY = 0.0, globalProgress = 0.556),
    ),
    previewBook(
        hash = "preview-gu",
        title = ToreadStrings.BookshelfPreviewTxtTitle,
        author = ToreadStrings.UnknownAuthor,
        format = "txt",
        progress = 0.951,
        lastReadAt = "2026-08-14T10:00:00Z",
        locator = TxtLocator(charOffset = 9510L, paragraphIndex = 321, globalProgress = 0.951),
    ),
    previewBook(
        hash = "preview-finished",
        title = ToreadStrings.BookshelfPreviewFinishedTitle,
        author = ToreadStrings.AppName,
        format = "epub",
        progress = 1.0,
        lastReadAt = "2026-08-17T11:00:00Z",
    ),
    previewBook(
        hash = "preview-unread",
        title = ToreadStrings.BookshelfPreviewUnreadTitle,
        author = ToreadStrings.BookshelfPreviewAuthor,
        format = "txt",
        progress = 0.0,
        lastReadAt = null,
    ),
    previewBook(
        hash = "preview-design",
        title = ToreadStrings.BookshelfPreviewDesignTitle,
        author = ToreadStrings.BookshelfPreviewDesignAuthor,
        format = "pdf",
        progress = 0.24,
        lastReadAt = "2026-08-13T10:00:00Z",
    ),
    previewBook(
        hash = "preview-eighth",
        title = ToreadStrings.BookshelfPreviewEighthTitle,
        author = ToreadStrings.BookshelfPreviewAuthor,
        format = "epub",
        progress = 0.12,
        lastReadAt = "2026-08-12T10:00:00Z",
    ),
)

private fun previewBook(
    hash: String,
    title: String,
    author: String?,
    format: String,
    progress: Double,
    lastReadAt: String?,
    locator: com.example.toread.reader.BookLocator? = null,
): BookshelfBook = BookshelfBook(
    bookHash = hash,
    title = title,
    author = author,
    format = format,
    filePath = "preview.$format",
    coverPath = null,
    fileSize = 0L,
    progress = progress,
    lastReadAt = lastReadAt,
    syncStatus = "synced",
    bookSyncStatus = "synced",
    progressSyncStatus = "synced",
    locator = locator,
)
