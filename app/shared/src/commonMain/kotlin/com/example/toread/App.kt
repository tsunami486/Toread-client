package com.example.toread

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.toread.importing.BookImportResult
import com.example.toread.importing.BookImportService
import com.example.toread.importing.createBookImportService
import kotlinx.coroutines.launch

private enum class ToreadScreen(val label: String) {
    Splash("Start"),
    Login("Login"),
    Bookshelf("Bookshelf"),
    Import("Import"),
    Reader("Reader"),
    Settings("Settings"),
}

@Composable
@Preview
fun App() {
    ToreadApp()
}

@Composable
fun ToreadApp() {
    var screen by remember { mutableStateOf(ToreadScreen.Splash) }
    val importService = remember { runCatching { createBookImportService() }.getOrNull() }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(current = screen, onNavigate = { screen = it })
                HorizontalDivider()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                ) {
                    when (screen) {
                        ToreadScreen.Splash -> SplashPage(
                            onEnter = { screen = ToreadScreen.Bookshelf },
                            onLogin = { screen = ToreadScreen.Login },
                        )
                        ToreadScreen.Login -> LoginPage(onDone = { screen = ToreadScreen.Bookshelf })
                        ToreadScreen.Bookshelf -> BookshelfPage(
                            onImport = { screen = ToreadScreen.Import },
                            onOpenReader = { screen = ToreadScreen.Reader },
                            onSettings = { screen = ToreadScreen.Settings },
                        )
                        ToreadScreen.Import -> ImportPage(
                            importService = importService,
                            onDone = { screen = ToreadScreen.Bookshelf },
                        )
                        ToreadScreen.Reader -> ReaderPlaceholderPage(onBack = { screen = ToreadScreen.Bookshelf })
                        ToreadScreen.Settings -> SettingsPage(onBack = { screen = ToreadScreen.Bookshelf })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(current: ToreadScreen, onNavigate: (ToreadScreen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Toread", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Import-only reader", style = MaterialTheme.typography.bodyMedium)
            }
            Text(getPlatform().name, style = MaterialTheme.typography.labelMedium)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToreadScreen.entries.forEach { item ->
                if (item == current) {
                    Button(onClick = { onNavigate(item) }) {
                        Text(item.label)
                    }
                } else {
                    OutlinedButton(onClick = { onNavigate(item) }) {
                        Text(item.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashPage(onEnter: () -> Unit, onLogin: () -> Unit) {
    ScreenColumn {
        Text("Start", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text("Local EPUB, PDF, and TXT reading with device sync.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onEnter) {
                Text("Open bookshelf")
            }
            OutlinedButton(onClick = onLogin) {
                Text("Sign in")
            }
        }
    }
}

@Composable
private fun LoginPage(onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    ScreenColumn {
        Text("Login", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDone) {
                Text("Sign in")
            }
            OutlinedButton(onClick = onDone) {
                Text("Offline")
            }
        }
    }
}

@Composable
private fun BookshelfPage(onImport: () -> Unit, onOpenReader: () -> Unit, onSettings: () -> Unit) {
    ScreenColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Bookshelf", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSettings) {
                    Text("Settings")
                }
                Button(onClick = onImport) {
                    Text("Import")
                }
            }
        }

        OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text("Local search") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
        )

        Card(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Recent reading", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("No books yet.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onImport) {
                        Text("Import files")
                    }
                    OutlinedButton(onClick = onOpenReader) {
                        Text("Reader preview")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportPage(importService: BookImportService?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Waiting for local files.") }

    ScreenColumn {
        Text("Import", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("EPUB, PDF, TXT")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = importService != null,
                onClick = {
                    val service = importService ?: return@Button
                    status = "Opening file picker..."
                    scope.launch {
                        status = runCatching { service.importSelectedFiles() }
                            .fold(
                                onSuccess = { results -> results.toImportStatus() },
                                onFailure = { error -> "Import failed: ${error.message ?: "unknown error"}" },
                            )
                    }
                },
            ) {
                Text("Choose local files")
            }
            OutlinedButton(onClick = onDone) {
                Text("Done")
            }
        }
        Card(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Import queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(status)
            }
        }
    }
}

private fun List<BookImportResult>.toImportStatus(): String {
    if (isEmpty()) return "No files selected."

    val imported = count { it is BookImportResult.Imported }
    val duplicates = count { it is BookImportResult.Duplicate }
    val failed = filterIsInstance<BookImportResult.Failed>()
    return buildString {
        append("Imported: $imported")
        if (duplicates > 0) append(", already in bookshelf: $duplicates")
        if (failed.isNotEmpty()) {
            append(", failed: ${failed.size}")
            append(". ")
            append(failed.joinToString { "${it.fileName}: ${it.reason}" })
        }
    }
}

@Composable
private fun ReaderPlaceholderPage(onBack: () -> Unit) {
    ScreenColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Reader", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onBack) {
                Text("Bookshelf")
            }
        }

        Card(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("Reading surface", style = MaterialTheme.typography.titleLarge)
                Text("Chapter and page rendering will land here.")
                LinearProgressIndicator(progress = { 0.35f }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {}) {
                        Text("Previous")
                    }
                    Button(onClick = {}) {
                        Text("Next")
                    }
                    TextButton(onClick = {}) {
                        Text("Bookmark")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(onBack: () -> Unit) {
    var serverUrl by remember { mutableStateOf("http://localhost:8080") }

    ScreenColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onBack) {
                Text("Bookshelf")
            }
        }
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
        )
        Text("Sync: enabled")
        Text("Storage: app data")
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}
