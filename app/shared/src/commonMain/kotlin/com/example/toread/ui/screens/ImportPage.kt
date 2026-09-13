package com.example.toread.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.importing.BookImportResult
import com.example.toread.importing.BookImportService
import com.example.toread.platform.PlatformBackHandler
import com.example.toread.ui.icons.ToreadSymbolIcon
import com.example.toread.ui.icons.ToreadSymbols
import com.example.toread.ui.text.ToreadStrings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import toread.client.app.shared.generated.resources.Res
import toread.client.app.shared.generated.resources.illustration_import_books

@Composable
internal fun ImportPage(
    importService: BookImportService?,
    onImported: () -> Unit,
    onBack: () -> Unit,
    supportsFileDrop: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ImportUiState>(ImportUiState.Waiting) }
    val colors = importPageColors()

    fun beginImport() {
        val service = importService
        if (service == null) {
            state = ImportUiState.Failure(ToreadStrings.ImportServiceUnavailable)
            return
        }

        state = ImportUiState.Importing
        scope.launch {
            state = runCatching { service.importSelectedFiles() }
                .fold(
                    onSuccess = { results ->
                        if (results.any { it is BookImportResult.Imported }) {
                            onImported()
                        }
                        results.toImportUiState()
                    },
                    onFailure = { error ->
                        ImportUiState.Failure(
                            ToreadStrings.safeUserVisibleMessage(
                                error.message,
                                fallback = ToreadStrings.ImportFailedGeneric,
                            ),
                        )
                    },
                )
        }
    }

    PlatformBackHandler(enabled = true, onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ImportHeader(onBack = onBack)
            //ImportHero(colors = colors)
            ImportActionCard(
                state = state,
                importEnabled = importService != null,
                onChooseFile = ::beginImport,
                colors = colors,
                supportsFileDrop = supportsFileDrop,
            )
            ImportStatusCard(
                state = state,
                colors = colors,
            )
            //ImportTipCard()
        }
    }
}

@Composable
private fun ImportHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp),
        ) {
            ToreadSymbolIcon(
                symbol = ToreadSymbols.ArrowBack,
                contentDescription = ToreadStrings.BackToBookshelf,
                tint = MaterialTheme.colorScheme.onBackground,
                autoMirror = true,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = ToreadStrings.ImportTitle,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ImportHero(colors: ImportPageColors) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = ToreadStrings.ImportHeroDescription,
            color = colors.textSecondary,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )
    }
}

@Composable
private fun ImportActionCard(
    state: ImportUiState,
    importEnabled: Boolean,
    onChooseFile: () -> Unit,
    colors: ImportPageColors,
    supportsFileDrop: Boolean,
) {
    ImportCard(colors = colors) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.illustration_import_books),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = ToreadStrings.ImportChooseTitle,
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = ToreadStrings.ImportChooseDescription,
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                )
            }
            ImportActionButtons(
                state = state,
                importEnabled = importEnabled,
                onChooseFile = onChooseFile,
                colors = colors,
            )
            Text(
                text = if (supportsFileDrop) {
                    ToreadStrings.ImportChooseDropHint
                } else {
                    ToreadStrings.ImportChooseHint
                },
                color = colors.textSecondary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ImportStatusCard(
    state: ImportUiState,
    colors: ImportPageColors,
) {
    ImportCard(colors = colors) {
        ImportStatusBody(state = state, colors = colors)
    }
}

@Composable
private fun ImportStatusBody(state: ImportUiState, colors: ImportPageColors) {
    val content = state.content(colors)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(content.backgroundColor),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ImportRoundIcon(
            symbol = content.symbol,
            backgroundColor = content.backgroundColor,
            iconTint = content.titleColor,
            size = 72.dp,
            iconSize = 34.dp,
            autoMirror = content.autoMirror,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = content.title,
                color = content.titleColor,
                fontSize = 19.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = content.message,
                color = colors.textSecondary,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )
            if (state is ImportUiState.Importing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = colors.primary,
                    trackColor = colors.primary.copy(alpha = 0.18f),
                )
            }
        }
    }
}

@Composable
private fun ImportActionButtons(
    state: ImportUiState,
    importEnabled: Boolean,
    onChooseFile: () -> Unit,
    colors: ImportPageColors,
) {
    when (state) {
        ImportUiState.Waiting -> ImportPrimaryActionButton(
            text = ToreadStrings.ChooseLocalFile,
            enabled = importEnabled,
            colors = colors,
            onClick = onChooseFile,
        )
        ImportUiState.Importing -> ImportPrimaryActionButton(
            text = ToreadStrings.ImportingAction,
            enabled = false,
            colors = colors,
            onClick = onChooseFile,
        )
        ImportUiState.NoSelection,
        is ImportUiState.Failure,
        -> ImportPrimaryActionButton(
            text = ToreadStrings.RechooseLocalFile,
            enabled = importEnabled,
            colors = colors,
            onClick = onChooseFile,
        )
        is ImportUiState.Success,
        is ImportUiState.Duplicate,
        -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ImportPrimaryActionButton(
                    text = ToreadStrings.ContinueChooseFile,
                    enabled = importEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = colors,
                    onClick = onChooseFile,
                )
            }
        }
    }
}

@Composable
private fun ImportPrimaryActionButton(
    text: String,
    enabled: Boolean,
    colors: ImportPageColors,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.primary.copy(alpha = 0.42f),
            disabledContentColor = colors.onPrimary.copy(alpha = 0.72f),
        ),
    ) {
        ToreadSymbolIcon(
            symbol = ToreadSymbols.FolderOpen,
            contentDescription = null,
            tint = colors.onPrimary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ImportTipCard() {
    val colors = importPageColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.tipBackground),
        border = BorderStroke(1.dp, colors.tipBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ImportRoundIcon(
                symbol = ToreadSymbols.TipsAndUpdates,
                backgroundColor = colors.cardBackground.copy(alpha = 0.72f),
                iconTint = colors.primary,
                size = 52.dp,
                iconSize = 28.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = ToreadStrings.ImportTipTitle,
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = ToreadStrings.ImportTipDescription,
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                )
            }
        }
    }
}

@Composable
private fun ImportCard(colors: ImportPageColors, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = BorderStroke(1.dp, colors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Box(modifier = Modifier.padding(24.dp)) {
            content()
        }
    }
}

@Composable
private fun ImportRoundIcon(
    symbol: DrawableResource,
    backgroundColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    autoMirror: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.62f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ToreadSymbolIcon(
            symbol = symbol,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
            autoMirror = autoMirror,
        )
    }
}

private sealed interface ImportUiState {
    data object Waiting : ImportUiState
    data object Importing : ImportUiState
    data object NoSelection : ImportUiState
    data class Success(val title: String?, val count: Int) : ImportUiState
    data class Duplicate(val title: String?, val count: Int) : ImportUiState
    data class Failure(val message: String) : ImportUiState
}

private data class ImportStatusContent(
    val symbol: DrawableResource,
    val title: String,
    val message: String,
    val titleColor: Color,
    val backgroundColor: Color,
    val iconBackgroundColor: Color,
    val autoMirror: Boolean = false,
)

private fun ImportUiState.content(colors: ImportPageColors): ImportStatusContent =
    when (this) {
        ImportUiState.Waiting -> ImportStatusContent(
            symbol = ToreadSymbols.HourglassEmpty,
            title = ToreadStrings.ImportWaitingTitle,
            message = ToreadStrings.ImportWaitingDescription,
            titleColor = colors.primary,
            backgroundColor = colors.cardBackground,
            iconBackgroundColor = colors.iconBackground,
        )
        ImportUiState.Importing -> ImportStatusContent(
            symbol = ToreadSymbols.Assessment,
            title = ToreadStrings.ImportingTitle,
            message = ToreadStrings.ImportingDescription,
            titleColor = colors.primary,
            backgroundColor = colors.cardBackground,
            iconBackgroundColor = colors.iconBackground,
        )
        ImportUiState.NoSelection -> ImportStatusContent(
            symbol = ToreadSymbols.HourglassEmpty,
            title = ToreadStrings.ImportNoSelectionTitle,
            message = ToreadStrings.ImportNoSelectionDescription,
            titleColor = colors.textPrimary,
            backgroundColor = colors.cardBackground,
            iconBackgroundColor = colors.iconBackground,
        )
        is ImportUiState.Success -> ImportStatusContent(
            symbol = ToreadSymbols.MenuBook,
            title = ToreadStrings.ImportSuccessTitle,
            message = ToreadStrings.importSuccessMessage(title, count),
            titleColor = colors.success,
            backgroundColor = colors.cardBackground,
            iconBackgroundColor = colors.success.copy(alpha = 0.14f),
            autoMirror = true,
        )
        is ImportUiState.Duplicate -> ImportStatusContent(
            symbol = ToreadSymbols.MenuBook,
            title = ToreadStrings.ImportDuplicateTitle,
            message = ToreadStrings.importDuplicateMessage(title, count),
            titleColor = colors.primary,
            backgroundColor = colors.cardBackground,
            iconBackgroundColor = colors.iconBackground,
            autoMirror = true,
        )
        is ImportUiState.Failure -> ImportStatusContent(
            symbol = ToreadSymbols.Assessment,
            title = ToreadStrings.ImportFailedTitle,
            message = ToreadStrings.importFailedMessage(message),
            titleColor = colors.error,
            backgroundColor = colors.cardBackground,
            iconBackgroundColor = colors.error.copy(alpha = 0.14f),
        )
    }

private fun List<BookImportResult>.toImportUiState(): ImportUiState {
    if (isEmpty()) return ImportUiState.NoSelection

    val imported = filterIsInstance<BookImportResult.Imported>()
    val duplicates = filterIsInstance<BookImportResult.Duplicate>()
    val failed = filterIsInstance<BookImportResult.Failed>()
    return when {
        imported.isNotEmpty() -> ImportUiState.Success(
            title = imported.singleOrNull()?.title,
            count = imported.size,
        )
        duplicates.isNotEmpty() && failed.isEmpty() -> ImportUiState.Duplicate(
            title = duplicates.singleOrNull()?.title,
            count = duplicates.size,
        )
        failed.isNotEmpty() -> ImportUiState.Failure(failed.toFailureMessage())
        else -> ImportUiState.NoSelection
    }
}

private fun List<BookImportResult.Failed>.toFailureMessage(): String =
    when (size) {
        0 -> ToreadStrings.ImportFailedGeneric
        1 -> first().reason
        else -> "${size} 个文件导入失败：${first().reason}"
    }

private data class ImportPageColors(
    val pageBackground: Color,
    val primary: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val success: Color,
    val error: Color,
    val iconBackground: Color,
    val tipBackground: Color,
    val tipBorder: Color,
)

@Composable
private fun importPageColors(): ImportPageColors {
    val colorScheme = MaterialTheme.colorScheme
    return ImportPageColors(
        pageBackground = colorScheme.background,
        primary = colorScheme.primary,
        onPrimary = colorScheme.onPrimary,
        textPrimary = colorScheme.onSurface,
        textSecondary = colorScheme.onSurfaceVariant,
        cardBackground = colorScheme.surface,
        cardBorder = colorScheme.outlineVariant,
        success = colorScheme.tertiary,
        error = colorScheme.error,
        iconBackground = colorScheme.primaryContainer.copy(alpha = 0.72f),
        tipBackground = colorScheme.tertiaryContainer.copy(alpha = 0.34f),
        tipBorder = colorScheme.tertiary.copy(alpha = 0.30f),
    )
}
