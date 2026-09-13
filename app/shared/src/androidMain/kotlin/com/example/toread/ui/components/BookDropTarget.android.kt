package com.example.toread.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.toread.platform.PlatformFile

/**
 * Android keeps its own path: books arrive through the system picker or a share intent, and an
 * in-app drag is not part of the import flow, so this modifier deliberately does nothing.
 */
@Composable
internal actual fun Modifier.bookFileDropTarget(
    enabled: Boolean,
    onFilesDropped: (List<PlatformFile>) -> Unit,
    onDragActiveChange: (Boolean) -> Unit,
): Modifier = this
