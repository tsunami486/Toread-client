package com.example.toread

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.toread.database.AndroidAppContext
import com.example.toread.platform.AndroidBackHandlerBridge
import com.example.toread.platform.AndroidFileAccessBridge
import com.example.toread.platform.AndroidPlatformFilePickerBridge
import com.example.toread.platform.AndroidSystemBarsBridge
import com.example.toread.platform.PlatformFile
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private var pendingPick: CompletableDeferred<List<PlatformFile>>? = null

    private val openBookFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        pendingPick?.complete(uris.map { uri -> uri.toPlatformFile() })
        pendingPick = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AndroidAppContext.initialize(applicationContext)
        AndroidFileAccessBridge.openInputStream = { file ->
            file.uri?.let { uri -> contentResolver.openInputStream(Uri.parse(uri)) }
        }
        AndroidPlatformFilePickerBridge.pickFiles = { extensions ->
            val deferred = CompletableDeferred<List<PlatformFile>>()
            pendingPick = deferred
            openBookFiles.launch(extensions.toMimeTypes())
            deferred.await()
        }
        AndroidSystemBarsBridge.hideSystemBars = {
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        }
        AndroidSystemBarsBridge.showSystemBars = {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (AndroidBackHandlerBridge.dispatchBack()) return

                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            },
        )

        setContent {
            App()
        }
    }

    private fun Uri.toPlatformFile(): PlatformFile {
        val metadata = queryOpenableMetadata(this)
        return PlatformFile(
            displayName = metadata.displayName ?: lastPathSegment ?: "selected-book",
            uri = toString(),
            mimeType = contentResolver.getType(this),
            sizeBytes = metadata.sizeBytes,
        )
    }

    private fun queryOpenableMetadata(uri: Uri): OpenableMetadata {
        var displayName: String? = null
        var sizeBytes: Long? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            displayName = cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME)
            sizeBytes = cursor.getLongOrNull(OpenableColumns.SIZE)
        }
        return OpenableMetadata(displayName, sizeBytes)
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && moveToFirst()) getString(index) else null
    }

    private fun Cursor.getLongOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && moveToFirst() && !isNull(index)) getLong(index) else null
    }

    private fun List<String>.toMimeTypes(): Array<String> {
        val mimeTypes = flatMap { extension ->
            when (extension.lowercase()) {
                "epub" -> listOf("application/epub+zip", "application/x-epub+zip")
                "pdf" -> listOf("application/pdf")
                "txt" -> listOf("text/plain")
                else -> emptyList()
            }
        }.distinct()
        return if (mimeTypes.isEmpty()) arrayOf("*/*") else mimeTypes.toTypedArray()
    }

    private data class OpenableMetadata(
        val displayName: String?,
        val sizeBytes: Long?,
    )
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
