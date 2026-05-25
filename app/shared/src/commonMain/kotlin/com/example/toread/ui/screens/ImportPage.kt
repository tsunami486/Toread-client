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
import com.example.toread.ui.components.ScreenColumn
import kotlinx.coroutines.launch

@Composable
internal fun ImportPage(importService: BookImportService?, onImported: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("等待选择本地文件。") }

    ScreenColumn {
        Text("导入书籍", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("支持 EPUB、PDF、TXT。只读取你主动选择的本地文件。")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = importService != null,
                onClick = {
                    val service = importService ?: return@Button
                    status = "正在打开文件选择器..."
                    scope.launch {
                        status = runCatching { service.importSelectedFiles() }
                            .fold(
                                onSuccess = { results ->
                                    onImported()
                                    results.toImportStatus()
                                },
                                onFailure = { error -> "导入失败：${error.message ?: "未知错误"}" },
                            )
                    }
                },
            ) {
                Text("选择本地文件")
            }
            OutlinedButton(onClick = onDone) {
                Text("返回书架")
            }
        }
        Card(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("导入状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(status)
            }
        }
    }
}

private fun List<BookImportResult>.toImportStatus(): String {
    if (isEmpty()) return "没有选择文件。"

    val imported = count { it is BookImportResult.Imported }
    val duplicates = count { it is BookImportResult.Duplicate }
    val failed = filterIsInstance<BookImportResult.Failed>()
    return buildString {
        append("导入成功：$imported")
        if (duplicates > 0) append("，已在书架中：$duplicates")
        if (failed.isNotEmpty()) {
            append("，失败：${failed.size}。")
            append(failed.joinToString { "${it.fileName}：${it.reason}" })
        }
    }
}
