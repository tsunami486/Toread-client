package com.example.toread.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.categories.BookCategoryInput
import com.example.toread.categories.BookCategorySource
import com.example.toread.categories.BookCategorySuggestion
import com.example.toread.categories.MaximumCategoryLength
import com.example.toread.categories.appendTag
import com.example.toread.categories.builtInCategoryKeywords
import com.example.toread.categories.parseTagList
import com.example.toread.categories.recordCategoryMemory
import com.example.toread.categories.suggestBookCategories
import com.example.toread.categories.toggleTag
import com.example.toread.library.BookshelfBook
import com.example.toread.library.BookshelfRepository
import com.example.toread.metadata.BookLocalAnnotations
import com.example.toread.platform.BookFileStorage
import com.example.toread.platform.ImagePickResult
import com.example.toread.platform.PlatformBackHandler
import com.example.toread.platform.PlatformFile
import com.example.toread.platform.PlatformImagePicker
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.ui.icons.ToreadSymbolIcon
import com.example.toread.ui.icons.ToreadSymbols
import com.example.toread.ui.reader.ReaderSheetCornerRadius
import com.example.toread.ui.text.ToreadStrings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource

/**
 * Book information editing, following the two design sheets: a stacked layout on phones and a
 * two-column layout from 768dp up. Geometry (card sizes, radii, field heights, spacing) is taken from
 * the sheets; the colors come from the app theme so the page also works in the light theme.
 */
private val BookInfoContentMaxWidth = 840.dp
private val BookInfoWideBreakpoint = 768.dp
private val BookInfoCoverCardWidth = 240.dp
private val BookInfoFieldHeight = 42.dp
// 简介 grows with its text (`heightIn(min = …)`), so these are only the empty-state heights: a
// description-filled field must not show a big blank box, and a long 简介 must not be cut off.
private val BookInfoNoteMinHeight = 72.dp
private val BookInfoCompactNoteMinHeight = 64.dp
private val CoverImageExtensions = listOf("jpg", "jpeg", "png", "webp")

@Composable
internal fun BookInfoPage(
    book: BookshelfBook?,
    bookshelfRepository: BookshelfRepository?,
    settingsRepository: LocalSettingsRepository? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDelete: (BookshelfBook) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var title by remember(book?.bookHash) { mutableStateOf(book?.title.orEmpty()) }
    var author by remember(book?.bookHash) { mutableStateOf(book?.author.orEmpty()) }
    var tags by remember(book?.bookHash) { mutableStateOf(book?.localTags.orEmpty()) }
    var notes by remember(book?.bookHash) { mutableStateOf(book?.localNotes.orEmpty()) }
    // What is already persisted, so an automatically filled 简介 does not read as an unsaved edit.
    var savedNotes by remember(book?.bookHash) { mutableStateOf(book?.localNotes.orEmpty()) }
    var savedTags by remember(book?.bookHash) { mutableStateOf(book?.localTags.orEmpty()) }
    // Whether the automatic extraction pass has already visited this book.
    var insightChecked by remember(book?.bookHash) {
        mutableStateOf(book?.importedInsight?.checked == true)
    }
    // The categories the book itself declares, used as a chip source until the pass refreshes them.
    var importedTags by remember(book?.bookHash) {
        mutableStateOf(book?.importedInsight?.subjects.orEmpty())
    }
    // Categories the sampled body text supported, with the keyword that carried each one.
    var importedBodyEvidence by remember(book?.bookHash) {
        mutableStateOf(book?.importedInsight?.bodyEvidence.orEmpty())
    }
    var categoryDictionary by remember(book?.bookHash) {
        mutableStateOf(settingsRepository?.categoryDictionary().orEmpty())
    }
    var categoryMemory by remember(book?.bookHash) {
        mutableStateOf(settingsRepository?.categoryMemory().orEmpty())
    }
    var tagsExpanded by remember(book?.bookHash) { mutableStateOf(false) }
    var customTagDialogVisible by remember(book?.bookHash) { mutableStateOf(false) }
    var customTagInput by remember(book?.bookHash) { mutableStateOf("") }
    var customTagError by remember(book?.bookHash) { mutableStateOf<String?>(null) }
    // The cover to save: the book's own file path, a freshly copied image, or null for the generated one.
    var coverPath by remember(book?.bookHash) { mutableStateOf(book?.coverPath) }
    var saving by remember(book?.bookHash) { mutableStateOf(false) }
    var message by remember(book?.bookHash) { mutableStateOf<String?>(null) }
    var coverSheetVisible by remember(book?.bookHash) { mutableStateOf(false) }
    var permissionDialogVisible by remember(book?.bookHash) { mutableStateOf(false) }
    var confirmSaveVisible by remember(book?.bookHash) { mutableStateOf(false) }

    val savingEnabled = book != null && bookshelfRepository != null && title.isNotBlank() && !saving
    val previewBook = book?.let { current ->
        current.copy(
            title = title.ifBlank { current.title },
            coverPath = coverPath,
        )
    }
    val hasUnsavedChanges = book != null && (
        title != book.title ||
            author != book.author.orEmpty() ||
            tags != savedTags ||
            notes != savedNotes ||
            coverPath != book.coverPath
        )
    val selectedTags = remember(tags) { parseTagList(tags) }
    val categorySuggestions = remember(title, author, tags, notes, importedTags, categoryDictionary, categoryMemory) {
        suggestBookCategories(
            input = BookCategoryInput(
                title = title,
                author = author,
                tags = tags,
                description = notes,
            ),
            userEntries = categoryDictionary,
            memoryEntries = categoryMemory,
        )
    }
    // What the sampled **body text** supported, as its own suggestions: the reason names the keyword
    // that carried it, so a body-derived chip never looks like a fact.
    val bodySuggestions = remember(importedBodyEvidence) {
        importedBodyEvidence.map { evidence ->
            BookCategorySuggestion(
                category = evidence.category,
                reason = ToreadStrings.bodyCategoryReason(evidence.keyword),
                source = BookCategorySource.BuiltInDictionary,
                score = 0,
            )
        }
    }
    val allSuggestions = remember(categorySuggestions, bodySuggestions) {
        (categorySuggestions + bodySuggestions).distinctBy { it.category }
    }
    // Everything the reader may pick from. The order never depends on what is selected — the chips
    // must stay where they are and only light up when tapped — so it is built from inputs that do not
    // move: the categories the book declares, the reader's rule categories, the shipped categories,
    // and finally this book's own tags (a brand-new custom tag is appended, never inserted).
    val categoryCatalog = remember(importedTags, importedBodyEvidence, categoryDictionary, tags) {
        buildList {
            addAll(importedTags)
            addAll(importedBodyEvidence.map { it.category })
            addAll(categoryDictionary.map { it.category })
            addAll(builtInCategoryKeywords.map { (category, _) -> category })
            addAll(parseTagList(tags))
        }
            .map(String::trim)
            .filter { it.isNotBlank() }
            .distinct()
    }
    // Read once per book: the settings page is the only place that changes it.
    val autoCategorizeEnabled = remember(book?.bookHash) {
        settingsRepository?.autoCategorizeEnabled() == true
    }

    /**
     * Reading the description out of the book is silent and automatic: this page only notices that the
     * book has not been visited yet and runs the pass once. The repository persists what it finds —
     * 简介 gets the description when the reader has not written one, 分类/标签 never — so the fields
     * below simply pick up the new value and no extraction UI exists.
     */
    LaunchedEffect(book?.bookHash, insightChecked) {
        val target = book ?: return@LaunchedEffect
        val repository = bookshelfRepository ?: return@LaunchedEffect
        if (insightChecked) return@LaunchedEffect
        val refreshed = repository.extractBookInsight(target.bookHash) ?: return@LaunchedEffect
        insightChecked = refreshed.insight.checked
        importedTags = refreshed.insight.subjects
        importedBodyEvidence = refreshed.insight.bodyEvidence
        if (notes.isBlank() && refreshed.notes.isNotBlank()) {
            notes = refreshed.notes
            savedNotes = refreshed.notes
        }
        if (tags.isBlank() && refreshed.tags.isNotBlank()) {
            tags = refreshed.tags
            savedTags = refreshed.tags
        }
    }

    /** Leaving the page asks about unsaved edits instead of dropping them silently. */
    fun requestBack() {
        keyboardController?.hide()
        focusManager.clearFocus()
        if (hasUnsavedChanges && !saving) confirmSaveVisible = true else onBack()
    }

    PlatformBackHandler(enabled = true, onBack = ::requestBack)

    /**
     * A picked image is copied into the book's own directory right away: on Android the picker returns a
     * content URI, which the cover loader cannot read, so this is also what makes the new cover show up
     * on this page immediately. Cancelling the page leaves only an unused file in the book's directory.
     */
    fun pickCover() {
        val target = book ?: return
        coverSheetVisible = false
        scope.launch {
            when (val result = PlatformImagePicker().pickImage()) {
                is ImagePickResult.Picked -> {
                    val stored = BookFileStorage().copyCoverToBookDirectory(
                        file = result.file,
                        bookHash = target.bookHash,
                        extension = result.file.coverExtension(),
                    )
                    if (stored == null) {
                        message = ToreadStrings.BookInfoCoverFailed
                    } else {
                        coverPath = stored
                        message = null
                    }
                }

                ImagePickResult.PermissionRequired -> permissionDialogVisible = true
                ImagePickResult.Cancelled -> Unit
            }
        }
    }

    /**
     * Remembers the categories the reader just saved, so a later suggestion for the same author or
     * series already knows the answer. Device-level and local: nothing here is synced.
     */
    fun rememberSavedCategories() {
        val store = settingsRepository ?: return
        val updated = recordCategoryMemory(
            existing = store.categoryMemory(),
            input = BookCategoryInput(
                title = title,
                author = author,
                tags = tags,
                description = notes,
            ),
            savedTags = tags,
        )
        if (updated == categoryMemory) return
        store.setCategoryMemory(updated)
        categoryMemory = updated
    }

    /**
     * The reader's own label (借来的、在读…) is in no catalog, so the custom chip opens a one-field
     * dialog instead of leaving them without a way in.
     */
    fun openCustomTagDialog() {
        keyboardController?.hide()
        focusManager.clearFocus()
        customTagInput = ""
        customTagError = null
        customTagDialogVisible = true
    }

    fun save() {
        val target = book ?: return
        val repository = bookshelfRepository ?: return
        if (!savingEnabled) return
        keyboardController?.hide()
        focusManager.clearFocus()
        saving = true
        message = null
        scope.launch {
            // The cover was already copied into the book's directory when it was picked.
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
            repository.updateBookCoverPath(target.bookHash, coverPath)
            rememberSavedCategories()
            savedNotes = notes
            savedTags = tags
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
                        onBack = ::requestBack,
                        onSave = ::save,
                    )
                } else {
                    CompactBookInfoTopBar(
                        savingEnabled = savingEnabled,
                        saving = saving,
                        onBack = ::requestBack,
                        onSave = ::save,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        // A tap that no field claimed puts the keyboard away and drops the selection.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        },
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
                                selectedTags = selectedTags,
                                suggestions = allSuggestions,
                                catalog = categoryCatalog,
                                tagsExpanded = tagsExpanded,
                                showSuggestions = !autoCategorizeEnabled,
                                notes = notes,
                                onTitleChange = { title = it },
                                onAuthorChange = { author = it },
                                onToggleTagsExpanded = { tagsExpanded = !tagsExpanded },
                                onToggleTag = { toggled -> tags = toggleTag(tags, toggled) },
                                onAddCustomTag = { openCustomTagDialog() },
                                onNotesChange = { notes = it },
                                onPickCover = ::pickCover,
                                onUseGeneratedCover = {
                                    coverPath = null
                                    message = null
                                },
                            )
                        } else {
                            CompactCoverSection(
                                book = previewBook,
                                onOpenCoverActions = { coverSheetVisible = true },
                            )
                            BookInfoFields(
                                title = title,
                                author = author,
                                selectedTags = selectedTags,
                                suggestions = allSuggestions,
                                catalog = categoryCatalog,
                                tagsExpanded = tagsExpanded,
                                showSuggestions = !autoCategorizeEnabled,
                                notes = notes,
                                noteHeight = BookInfoCompactNoteMinHeight,
                                onTitleChange = { title = it },
                                onAuthorChange = { author = it },
                                onToggleTagsExpanded = { tagsExpanded = !tagsExpanded },
                                onToggleTag = { toggled -> tags = toggleTag(tags, toggled) },
                                onAddCustomTag = { openCustomTagDialog() },
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

        if (coverSheetVisible) {
            CoverActionSheet(
                onDismiss = { coverSheetVisible = false },
                onPickFromGallery = ::pickCover,
                onUseGeneratedCover = {
                    coverSheetVisible = false
                    coverPath = null
                    message = null
                },
            )
        }

        if (permissionDialogVisible) {
            AlertDialog(
                onDismissRequest = { permissionDialogVisible = false },
                title = { Text(ToreadStrings.GalleryPermissionTitle) },
                text = { Text(ToreadStrings.GalleryPermissionMessage) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            permissionDialogVisible = false
                            PlatformImagePicker().openAppSettings()
                        },
                    ) {
                        Text(ToreadStrings.GalleryPermissionGrant)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { permissionDialogVisible = false }) {
                        Text(ToreadStrings.DialogCancel)
                    }
                },
            )
        }

        if (confirmSaveVisible) {
            AlertDialog(
                onDismissRequest = { confirmSaveVisible = false },
                title = { Text(ToreadStrings.BookInfoUnsavedTitle) },
                text = { Text(ToreadStrings.BookInfoUnsavedMessage) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmSaveVisible = false
                            save()
                        },
                    ) {
                        Text(ToreadStrings.BookInfoSave)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                confirmSaveVisible = false
                                onBack()
                            },
                        ) {
                            Text(ToreadStrings.BookInfoDiscardChanges)
                        }
                        TextButton(onClick = { confirmSaveVisible = false }) {
                            Text(ToreadStrings.DialogCancel)
                        }
                    }
                },
            )
        }

        if (customTagDialogVisible) {
            AlertDialog(
                onDismissRequest = { customTagDialogVisible = false },
                title = { Text(ToreadStrings.BookInfoTagsCustomTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = customTagInput,
                            onValueChange = { customTagInput = it },
                            singleLine = true,
                            label = { Text(ToreadStrings.BookInfoTagsCustomLabel) },
                            placeholder = { Text(ToreadStrings.BookInfoTagsCustomPlaceholder) },
                        )
                        customTagError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val clean = customTagInput.trim()
                            when {
                                clean.isEmpty() -> customTagError = ToreadStrings.BookInfoTagsCustomEmpty
                                clean.length > MaximumCategoryLength -> {
                                    customTagError = ToreadStrings.bookInfoTagsCustomTooLong(
                                        MaximumCategoryLength,
                                    )
                                }
                                else -> {
                                    tags = appendTag(tags, clean)
                                    customTagDialogVisible = false
                                }
                            }
                        },
                    ) {
                        Text(ToreadStrings.DialogConfirm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { customTagDialogVisible = false }) {
                        Text(ToreadStrings.DialogCancel)
                    }
                },
            )
        }
    }
}

/** The cover actions live behind the cover itself now: tapping it offers these two choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverActionSheet(
    onDismiss: () -> Unit,
    onPickFromGallery: () -> Unit,
    onUseGeneratedCover: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // The app's own sheet surface: the reader's chapter drawer paints `surface` with rounded top
        // corners and this handle, while Material's default sheet would come up on the lighter
        // `surfaceContainerLow` and read as a different panel.
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(
            topStart = ReaderSheetCornerRadius,
            topEnd = ReaderSheetCornerRadius,
        ),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            CoverActionRow(
                symbol = ToreadSymbols.Photo,
                title = ToreadStrings.BookInfoCoverPick,
                description = ToreadStrings.BookInfoCoverPickHint,
                onClick = onPickFromGallery,
            )
            CoverActionRow(
                symbol = ToreadSymbols.Article,
                title = ToreadStrings.BookInfoCoverGenerated,
                description = ToreadStrings.BookInfoCoverGeneratedHint,
                onClick = onUseGeneratedCover,
            )
        }
    }
}

/** One row of [CoverActionSheet]; its container stays transparent so it sits on the sheet surface. */
@Composable
private fun CoverActionRow(
    symbol: DrawableResource,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(title, color = MaterialTheme.colorScheme.onSurface)
        },
        supportingContent = {
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            ToreadSymbolIcon(
                symbol = symbol,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
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
    onOpenCoverActions: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(2.dp))
        // No separate buttons: the cover itself is the control and takes the space they used to hold.
        CoverPreview(
            book = book,
            width = 150.dp,
            height = 200.dp,
            cornerRadius = 8.dp,
            onClick = onOpenCoverActions,
        )
    }
}

@Composable
private fun CoverPreview(
    book: BookshelfBook?,
    width: Dp,
    height: Dp,
    cornerRadius: Dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .hoverable(interactionSource = interactionSource)
            .clickable(onClick = onClick),
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
    selectedTags: List<String>,
    suggestions: List<BookCategorySuggestion>,
    catalog: List<String>,
    tagsExpanded: Boolean,
    showSuggestions: Boolean,
    notes: String,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onToggleTagsExpanded: () -> Unit,
    onToggleTag: (String) -> Unit,
    onAddCustomTag: () -> Unit,
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
                    onClick = onPickCover,
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
                    selectedTags = selectedTags,
                    suggestions = suggestions,
                    catalog = catalog,
                    tagsExpanded = tagsExpanded,
                    showSuggestions = showSuggestions,
                    notes = notes,
                    noteHeight = BookInfoNoteMinHeight,
                    onTitleChange = onTitleChange,
                    onAuthorChange = onAuthorChange,
                    onToggleTagsExpanded = onToggleTagsExpanded,
                    onToggleTag = onToggleTag,
                    onAddCustomTag = onAddCustomTag,
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
    selectedTags: List<String>,
    suggestions: List<BookCategorySuggestion>,
    catalog: List<String>,
    tagsExpanded: Boolean,
    showSuggestions: Boolean,
    notes: String,
    noteHeight: Dp,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onToggleTagsExpanded: () -> Unit,
    onToggleTag: (String) -> Unit,
    onAddCustomTag: () -> Unit,
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
        // 分类/标签 is a chip picker, not a text box: the first row shows what this book is already
        // filed under (or the top suggestions while it has none) and the arrow opens the full catalog.
        TagChipsField(
            selectedTags = selectedTags,
            suggestions = suggestions,
            catalog = catalog,
            expanded = tagsExpanded,
            showSuggestions = showSuggestions,
            onToggleExpanded = onToggleTagsExpanded,
            onToggleTag = onToggleTag,
            onAddCustomTag = onAddCustomTag,
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

/** The 分类/标签 chips: one row by default, the whole catalog once the arrow is tapped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChipsField(
    selectedTags: List<String>,
    suggestions: List<BookCategorySuggestion>,
    catalog: List<String>,
    expanded: Boolean,
    showSuggestions: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleTag: (String) -> Unit,
    onAddCustomTag: () -> Unit,
) {
    val selectedKeys = selectedTags.map { it.lowercase() }.toSet()
    val isSelected: (String) -> Boolean = { tag -> tag.lowercase() in selectedKeys }
    // The collapsed row is the book's own categories; a book that has none yet shows the top
    // suggestions instead, so the row is never an empty strip.
    val firstRowTags = selectedTags.ifEmpty { suggestions.map { it.category }.take(FirstRowSuggestionCount) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = ToreadStrings.BookInfoTagsLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (firstRowTags.isEmpty()) {
                Text(
                    text = ToreadStrings.BookInfoTagsEmptyHint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    firstRowTags.forEach { tag ->
                        TagChip(
                            label = tag,
                            selected = isSelected(tag),
                            onClick = { onToggleTag(tag) },
                        )
                    }
                }
            }
            IconButton(onClick = onToggleExpanded, modifier = Modifier.size(32.dp)) {
                ToreadSymbolIcon(
                    symbol = if (expanded) {
                        ToreadSymbols.KeyboardArrowUp
                    } else {
                        ToreadSymbols.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) {
                        ToreadStrings.BookInfoTagsCollapseHint
                    } else {
                        ToreadStrings.BookInfoTagsExpandHint
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (expanded && showSuggestions && suggestions.isNotEmpty()) {
            // 自动分类 off: the lexicon's proposals get their own row above the catalog, so the
            // catalog below keeps the same fixed order every time it is opened.
            Text(
                text = ToreadStrings.BookInfoTagsSuggestionsLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { suggestion ->
                    TagChip(
                        label = suggestion.category,
                        selected = isSelected(suggestion.category),
                        onClick = { onToggleTag(suggestion.category) },
                    )
                }
            }
        }
        if (expanded) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                catalog.forEach { tag ->
                    TagChip(
                        label = tag,
                        selected = isSelected(tag),
                        onClick = { onToggleTag(tag) },
                    )
                }
                // The catalog cannot cover a reader's own labels (借来的、在读…), so the last chip
                // opens a one-field dialog instead of leaving them without a way in.
                TagChip(
                    label = ToreadStrings.BookInfoTagsCustomChip,
                    selected = false,
                    onClick = onAddCustomTag,
                )
            }
        }
    }
}

/** One selectable category; the selected ones carry the accent colour. */
@Composable
private fun TagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontSize = 12.sp,
        )
    }
}

/** How many suggestions the collapsed row falls back to while the book has no category yet. */
private const val FirstRowSuggestionCount = 3

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
                // 简介 is a description, not a caption: the field starts tall and keeps growing with
                // its content instead of hiding the text behind an inner scroll, so a whole blurb is
                // readable at once. Single-line fields keep their fixed height.
                .then(
                    if (singleLine) {
                        Modifier.height(height)
                    } else {
                        Modifier.heightIn(min = height)
                    },
                )
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
                    // The hint is background text, not content: it disappears while the field is selected
                    // and stays quiet while it is not.
                    if (value.isEmpty() && !focused) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
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
