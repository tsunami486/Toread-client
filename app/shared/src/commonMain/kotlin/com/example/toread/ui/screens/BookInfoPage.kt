package com.example.toread.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.library.BookLocalAnnotations
import com.example.toread.library.BookshelfBook
import com.example.toread.library.BookshelfRepository
import com.example.toread.platform.BookFileStorage
import com.example.toread.platform.PlatformBackHandler
import com.example.toread.platform.PlatformFile
import com.example.toread.platform.PlatformFilePicker
import com.example.toread.ui.icons.ToreadSymbolIcon
import com.example.toread.ui.icons.ToreadSymbols
import com.example.toread.ui.text.ToreadStrings
import kotlinx.coroutines.launch

/**
 * Book information editing, following the two design sheets: a stacked layout on phones and a
 * two-column layout from 768dp up. Geometry (card sizes, radii, field heights, spacing) is taken from
 * the sheets; the colors come from the app theme so the page also works in the light theme.
 */
private val BookInfoContentMaxWidth = 840.dp
private val BookInfoWideBreakpoint = 768.dp
private val BookInfoCoverCardWidth = 240.dp
private val BookInfoFieldHeight = 42.dp
private val BookInfoNoteHeight = 96.dp
private val BookInfoCompactNoteHeight = 74.dp
private val CoverImageExtensions = listOf("jpg", "jpeg", "png", "webp")

@Composable
internal fun BookInfoPage(
    book: BookshelfBook?,
    bookshelfRepository: BookshelfRepository?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDelete: (BookshelfBook) -> Unit = {},
) {
    PlatformBackHandler(enabled = true, onBack = onBack)

    val scope = rememberCoroutineScope()
    var title by remember(book?.bookHash) { mutableStateOf(book?.title.orEmpty()) }
    var author by remember(book?.bookHash) { mutableStateOf(book?.author.orEmpty()) }
    var tags by remember(book?.bookHash) { mutableStateOf(book?.localTags.orEmpty()) }
    var notes by remember(book?.bookHash) { mutableStateOf(book?.localNotes.orEmpty()) }
    var pickedCover by remember(book?.bookHash) { mutableStateOf<PlatformFile?>(null) }
    var useGeneratedCover by remember(book?.bookHash) { mutableStateOf(false) }
    var saving by remember(book?.bookHash) { mutableStateOf(false) }
    var message by remember(book?.bookHash) { mutableStateOf<String?>(null) }

    val savingEnabled = book != null && bookshelfRepository != null && title.isNotBlank() && !saving
    val previewBook = book?.let { current ->
        current.copy(
            title = title.ifBlank { current.title },
            coverPath = when {
                useGeneratedCover -> null
                pickedCover != null -> pickedCover?.path
                else -> current.coverPath
            },
        )
    }

    fun pickCover() {
        scope.launch {
            val picked = PlatformFilePicker().pickFiles(CoverImageExtensions).firstOrNull()
            if (picked != null) {
                pickedCover = picked
                useGeneratedCover = false
                message = null
            }
        }
    }

    fun save() {
        val target = book ?: return
        val repository = bookshelfRepository ?: return
        if (!savingEnabled) return
        saving = true
        message = null
        scope.launch {
            val storedCover = when {
                pickedCover == null -> if (useGeneratedCover) null else target.coverPath
                else -> {
                    val picked = requireNotNull(pickedCover)
                    BookFileStorage().copyCoverToBookDirectory(
                        file = picked,
                        bookHash = target.bookHash,
                        extension = picked.coverExtension(),
                    )
                }
            }
            if (pickedCover != null && storedCover == null) {
                saving = false
                message = ToreadStrings.BookInfoCoverFailed
                return@launch
            }

            val saved = repository.updateBookInfo(
                bookHash = target.bookHash,
                title = title,
                author = author,
                annotations = BookLocalAnnotations(tags = tags, notes = notes),
            )
            if (!saved) {
                saving = false
                message = ToreadStrings.BookInfoSaveFailed
                return@launch
            }
            repository.updateBookCoverPath(target.bookHash, storedCover)
            saving = false
            onSaved()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= BookInfoWideBreakpoint
            Column(modifier = Modifier.fillMaxSize()) {
                if (isWide) {
                    WideBookInfoTopBar(
                        savingEnabled = savingEnabled,
                        saving = saving,
                        onBack = onBack,
                        onSave = ::save,
                    )
                } else {
                    CompactBookInfoTopBar(
                        savingEnabled = savingEnabled,
                        saving = saving,
                        onBack = onBack,
                        onSave = ::save,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = BookInfoContentMaxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = if (isWide) 20.dp else 18.dp)
                            .padding(top = if (isWide) 32.dp else 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isWide) 24.dp else 18.dp),
                    ) {
                        if (isWide) {
                            WideEditorRow(
                                book = previewBook,
                                title = title,
                                author = author,
                                tags = tags,
                                notes = notes,
                                onTitleChange = { title = it },
                                onAuthorChange = { author = it },
                                onTagsChange = { tags = it },
                                onNotesChange = { notes = it },
                                onPickCover = ::pickCover,
                                onUseGeneratedCover = {
                                    pickedCover = null
                                    useGeneratedCover = true
                                    message = null
                                },
                            )
                        } else {
                            CompactCoverSection(
                                book = previewBook,
                                onPickCover = ::pickCover,
                                onUseGeneratedCover = {
                                    pickedCover = null
                                    useGeneratedCover = true
                                    message = null
                                },
                            )
                            BookInfoFields(
                                title = title,
                                author = author,
                                tags = tags,
                                notes = notes,
                                noteHeight = BookInfoCompactNoteHeight,
                                onTitleChange = { title = it },
                                onAuthorChange = { author = it },
                                onTagsChange = { tags = it },
                                onNotesChange = { notes = it },
                            )
                        }

                        message?.let { text ->
                            Text(
                                text = text,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                            )
                        }

                        BookInfoMetaCard(
                            book = book,
                            rows = if (isWide) {
                                listOf(
                                    ToreadStrings.BookInfoMetaFormat to metaFormat(book, withVersion = true),
                                    ToreadStrings.BookInfoMetaPath to book?.filePath.orEmpty(),
                                    ToreadStrings.BookInfoMetaSize to metaSize(book),
                                )
                            } else {
                                listOf(
                                    ToreadStrings.BookInfoMetaFormat to metaFormat(book, withVersion = true),
                                    ToreadStrings.BookInfoMetaSize to metaSize(book),
                                    ToreadStrings.BookInfoMetaPath to book?.filePath.orEmpty(),
                                )
                            },
                            compact = !isWide,
                        )

                        if (isWide) {
                            WideDangerCard(
                                enabled = book != null,
                                onDelete = { book?.let(onDelete) },
                            )
                        } else {
                            CompactDangerAction(
                                enabled = book != null,
                                onDelete = { book?.let(onDelete) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactBookInfoTopBar(
    savingEnabled: Boolean,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                ToreadSymbolIcon(
                    symbol = ToreadSymbols.ArrowBack,
                    contentDescription = ToreadStrings.BackToBookshelf,
                    autoMirror = true,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = ToreadStrings.BookInfoTitle,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PrimaryPillButton(
                label = if (saving) ToreadStrings.SavingShort else ToreadStrings.BookInfoSaveShort,
                enabled = savingEnabled,
                height = 28.dp,
                horizontalPadding = 14.dp,
                onClick = onSave,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        ShelfDivider()
    }
}

@Composable
private fun WideBookInfoTopBar(
    savingEnabled: Boolean,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(min = 160.dp)
                    .clickable(onClick = onBack),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToreadSymbolIcon(
                    symbol = ToreadSymbols.ArrowBack,
                    contentDescription = ToreadStrings.BackToBookshelf,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                    autoMirror = true,
                )
                Text(
                    text = ToreadStrings.Bookshelf,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
            Text(
                text = ToreadStrings.BookInfoTitle,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier.widthIn(min = 160.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                PrimaryPillButton(
                    label = if (saving) ToreadStrings.Saving else ToreadStrings.BookInfoSave,
                    enabled = savingEnabled,
                    height = 32.dp,
                    horizontalPadding = 20.dp,
                    onClick = onSave,
                )
            }
        }
        HorizontalHairline(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun PrimaryPillButton(
    label: String,
    enabled: Boolean,
    height: Dp,
    horizontalPadding: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(height)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CompactCoverSection(
    book: BookshelfBook?,
    onPickCover: () -> Unit,
    onUseGeneratedCover: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(modifier = Modifier.height(2.dp))
        CoverPreview(
            book = book,
            width = 110.dp,
            height = 148.dp,
            cornerRadius = 6.dp,
            onPickCover = onPickCover,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverChip(label = ToreadStrings.BookInfoCoverPick, onClick = onPickCover)
            CoverChip(
                label = ToreadStrings.BookInfoCoverGeneratedShort,
                onClick = onUseGeneratedCover,
            )
        }
    }
}

@Composable
private fun CoverChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun CoverPreview(
    book: BookshelfBook?,
    width: Dp,
    height: Dp,
    cornerRadius: Dp,
    onPickCover: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .hoverable(interactionSource = interactionSource)
            .clickable(onClick = onPickCover),
        contentAlignment = Alignment.Center,
    ) {
        if (book != null) {
            BookCover(
                book = book,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius)),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
        // The sheets show the replace-cover affordance as a hover mask; touch users get the chips.
        if (isHovered) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    ToreadSymbolIcon(
                        symbol = ToreadSymbols.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = ToreadStrings.BookInfoCoverHint,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun WideEditorRow(
    book: BookshelfBook?,
    title: String,
    author: String,
    tags: String,
    notes: String,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onPickCover: () -> Unit,
    onUseGeneratedCover: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BookInfoCard(modifier = Modifier.width(BookInfoCoverCardWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CoverPreview(
                    book = book,
                    width = 150.dp,
                    height = 200.dp,
                    cornerRadius = 8.dp,
                    onPickCover = onPickCover,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SecondaryActionButton(
                        label = ToreadStrings.BookInfoCoverUpload,
                        onClick = onPickCover,
                    )
                    SecondaryActionButton(
                        label = ToreadStrings.BookInfoCoverGenerated,
                        onClick = onUseGeneratedCover,
                    )
                }
                Text(
                    text = ToreadStrings.BookInfoCoverFormats,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        BookInfoCard(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                BookInfoFields(
                    title = title,
                    author = author,
                    tags = tags,
                    notes = notes,
                    noteHeight = BookInfoNoteHeight,
                    onTitleChange = onTitleChange,
                    onAuthorChange = onAuthorChange,
                    onTagsChange = onTagsChange,
                    onNotesChange = onNotesChange,
                )
            }
        }
    }
}

@Composable
private fun SecondaryActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun BookInfoFields(
    title: String,
    author: String,
    tags: String,
    notes: String,
    noteHeight: Dp,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BookInfoField(
            label = ToreadStrings.BookInfoTitleLabel,
            value = title,
            placeholder = ToreadStrings.BookInfoTitlePlaceholder,
            onValueChange = onTitleChange,
            height = BookInfoFieldHeight,
        )
        BookInfoField(
            label = ToreadStrings.BookInfoAuthorLabel,
            value = author,
            placeholder = ToreadStrings.BookInfoAuthorPlaceholder,
            onValueChange = onAuthorChange,
            height = BookInfoFieldHeight,
        )
        BookInfoField(
            label = ToreadStrings.BookInfoTagsLabel,
            value = tags,
            placeholder = ToreadStrings.BookInfoTagsPlaceholder,
            onValueChange = onTagsChange,
            height = BookInfoFieldHeight,
        )
        BookInfoField(
            label = ToreadStrings.BookInfoNotesLabel,
            value = notes,
            placeholder = ToreadStrings.BookInfoNotesPlaceholder,
            onValueChange = onNotesChange,
            height = noteHeight,
            singleLine = false,
        )
    }
}

@Composable
private fun BookInfoField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    height: Dp,
    singleLine: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(
                    width = if (focused) 1.5.dp else 1.dp,
                    color = if (focused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}

@Composable
private fun BookInfoCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        content()
    }
}

@Composable
private fun BookInfoMetaCard(
    book: BookshelfBook?,
    rows: List<Pair<String, String>>,
    compact: Boolean,
) {
    BookInfoCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 16.dp else 24.dp,
                    vertical = if (compact) 16.dp else 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
        ) {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    HorizontalHairline(color = MaterialTheme.colorScheme.outlineVariant)
                }
                MetaRow(
                    label = label,
                    value = value.ifBlank { ToreadStrings.BookInfoUnknownValue },
                )
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (label.length > 6) 12.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WideDangerCard(enabled: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
            .dashedRoundedBorder(
                color = MaterialTheme.colorScheme.error,
                cornerRadius = 14.dp,
            )
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = ToreadStrings.DeleteBookRemoveTitle,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = ToreadStrings.DeleteBookRemoveDescription,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (enabled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                    },
                )
                .clickable(enabled = enabled, onClick = onDelete)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ToreadStrings.DeleteBookRemoveAction,
                color = MaterialTheme.colorScheme.onError,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CompactDangerAction(enabled: Boolean, onDelete: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
            .dashedRoundedBorder(
                color = MaterialTheme.colorScheme.error,
                cornerRadius = 8.dp,
            )
            .clickable(enabled = enabled, onClick = onDelete),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ToreadStrings.DeleteBookRemoveActionLong,
            color = MaterialTheme.colorScheme.error,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HorizontalHairline(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

@Composable
private fun ShelfDivider() {
    HorizontalHairline(color = MaterialTheme.colorScheme.outlineVariant)
}

/** The sheets draw both danger zones with a dashed outline; Compose needs it drawn by hand. */
private fun Modifier.dashedRoundedBorder(
    color: androidx.compose.ui.graphics.Color,
    cornerRadius: Dp,
): Modifier = drawBehind {
    val strokeWidth = 1.dp.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
                0f,
            ),
        ),
    )
}

private fun metaFormat(book: BookshelfBook?, withVersion: Boolean): String =
    book?.format?.uppercase().orEmpty()

private fun metaSize(book: BookshelfBook?): String =
    book?.let { ToreadStrings.bookFileSizeLabel(it.fileSize) }.orEmpty()

private fun PlatformFile.coverExtension(): String {
    val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension.takeIf { it in CoverImageExtensions } ?: "jpg"
}
