package com.example.toread.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toread.importing.BookImportResult
import com.example.toread.importing.BookImportService
import com.example.toread.platform.PlatformBackHandler
import com.example.toread.ui.components.ScreenColumn
import com.example.toread.ui.text.ToreadStrings
import kotlinx.coroutines.launch

@Composable
internal fun ImportPage(importService: BookImportService?, onImported: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(ToreadStrings.ImportWaiting) }

    PlatformBackHandler(enabled = true, onBack = onBack)

    ScreenColumn {
        Text(ToreadStrings.ImportTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(ToreadStrings.SupportedImportFormats)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = importService != null,
                onClick = {
                    val service = importService ?: return@Button
                    status = ToreadStrings.OpeningFilePicker
                    scope.launch {
                        status = runCatching { service.importSelectedFiles() }
                            .fold(
                                onSuccess = { results ->
                                    onImported()
                                    results.toImportStatus()
                                },
                                onFailure = { error -> ToreadStrings.importFailed(error.message) },
                            )
                    }
                },
            ) {
                Text(ToreadStrings.ChooseLocalFile)
            }
            OutlinedButton(onClick = onBack) {
                Text(ToreadStrings.BackToBookshelf)
            }
        }
        Card(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(ToreadStrings.ImportStatusTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(status)
            }
        }
    }
}

private fun List<BookImportResult>.toImportStatus(): String {
    if (isEmpty()) return ToreadStrings.NoFileSelected

    val imported = count { it is BookImportResult.Imported }
    val duplicates = count { it is BookImportResult.Duplicate }
    val failed = filterIsInstance<BookImportResult.Failed>()
    return buildString {
        append(ToreadStrings.importedSummary(imported, duplicates, failed.size))
        if (failed.isNotEmpty()) {
            append(failed.joinToString { "${it.fileName}：${it.reason}" })
        }
    }
}
