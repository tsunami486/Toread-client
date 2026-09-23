package com.example.toread

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.toread.database.AndroidAppContext
import com.example.toread.platform.AppBackgroundNotifier
import com.example.toread.platform.AndroidBackHandlerBridge
import com.example.toread.platform.AndroidFileAccessBridge
import com.example.toread.platform.AndroidPlatformFilePickerBridge
import com.example.toread.platform.AndroidPlatformImagePickerBridge
import com.example.toread.platform.AndroidReaderDeviceControlsBridge
import com.example.toread.platform.AndroidSystemBarsBridge
import com.example.toread.platform.ImagePickResult
import com.example.toread.platform.PlatformFile
import com.example.toread.platform.SharedFileInbox
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private var pendingPick: CompletableDeferred<List<PlatformFile>>? = null
    private var pendingImagePick: CompletableDeferred<ImagePickResult>? = null

    private val openBookFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        pendingPick?.complete(uris.map { uri -> uri.toPlatformFile() })
        pendingPick = null
    }

    private val pickGalleryImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        pendingImagePick?.complete(uri.toImagePickResult())
        pendingImagePick = null
    }

    private val getGalleryImage = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        pendingImagePick?.complete(uri.toImagePickResult())
        pendingImagePick = null
    }

    private val requestGalleryPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val deferred = pendingImagePick
        when {
            !granted -> {
                pendingImagePick = null
                deferred?.complete(ImagePickResult.PermissionRequired)
            }
            // Access granted: ask the documents provider for an image, which is the only path left for a
            // device without the system photo picker.
            else -> getGalleryImage.launch("image/*")
        }
    }

    private fun Uri?.toImagePickResult(): ImagePickResult =
        this?.let { ImagePickResult.Picked(it.toPlatformFile()) } ?: ImagePickResult.Cancelled

    /** The system photo picker needs no permission; older devices fall back to a runtime request. */
    private fun launchImagePick() {
        val hasPhotoPicker = runCatching {
            ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(this)
        }.getOrDefault(false)
        if (hasPhotoPicker) {
            pickGalleryImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        } else if (hasGalleryPermission()) {
            getGalleryImage.launch("image/*")
        } else {
            requestGalleryPermission.launch(galleryPermission())
        }
    }

    private fun galleryPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun hasGalleryPermission(): Boolean =
        checkSelfPermission(galleryPermission()) == PackageManager.PERMISSION_GRANTED

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
        AndroidPlatformImagePickerBridge.pickImage = {
            val deferred = CompletableDeferred<ImagePickResult>()
            pendingImagePick = deferred
            launchImagePick()
            deferred.await()
        }
        AndroidPlatformImagePickerBridge.openAppSettings = {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                ),
            )
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

        runToreadAndroidApp()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Books another app handed over: "open with" (ACTION_VIEW) and share (ACTION_SEND /
     * ACTION_SEND_MULTIPLE). The URIs go into [SharedFileInbox] and are imported through the same
     * service as the in-app picker. Read access to a foreign content URI lasts as long as this
     * activity, and the import copies the file into the private book directory right away.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        val uris = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(intent.streamUri())
            Intent.ACTION_SEND_MULTIPLE -> intent.streamUris()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        SharedFileInbox.offer(uris.map { uri -> uri.toPlatformFile() })
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun Intent.streamUris(): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }

    override fun onStop() {
        AppBackgroundNotifier.notifyEnterBackground()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        AppBackgroundNotifier.notifyEnterForeground()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (AndroidReaderDeviceControlsBridge.dispatchVolumeKey(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
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
                "jpg", "jpeg" -> listOf("image/jpeg")
                "png" -> listOf("image/png")
                "webp" -> listOf("image/webp")
                else -> emptyList()
            }
        }.distinct()
        return if (mimeTypes.isEmpty()) arrayOf("*/*") else mimeTypes.toTypedArray()
    }

    private data class OpenableMetadata(
        val displayName: String?,
        val sizeBytes: Long?,
    )

    private fun runToreadAndroidApp() {
        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
