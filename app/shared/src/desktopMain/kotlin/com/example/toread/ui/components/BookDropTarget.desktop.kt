package com.example.toread.ui.components

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import com.example.toread.platform.PlatformFile
import com.example.toread.platform.platformFileFromPath

/**
 * Desktop implementation: the window's AWT drag-and-drop manager routes OS drops to the modifier, and
 * only a file list claims the drag, so dragging text or an image into the app keeps its normal
 * behaviour instead of opening a drop overlay.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun Modifier.bookFileDropTarget(
    enabled: Boolean,
    onFilesDropped: (List<PlatformFile>) -> Unit,
    onDragActiveChange: (Boolean) -> Unit,
): Modifier {
    val currentOnFilesDropped by rememberUpdatedState(onFilesDropped)
    val currentOnDragActiveChange by rememberUpdatedState(onDragActiveChange)
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val paths = event.dragData().filePaths()
                if (paths.isEmpty()) return false
                currentOnFilesDropped(paths.map(::platformFileFromPath))
                return true
            }

            override fun onEntered(event: DragAndDropEvent) {
                if (event.dragData().filePaths().isNotEmpty()) currentOnDragActiveChange(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                currentOnDragActiveChange(false)
            }

            override fun onEnded(event: DragAndDropEvent) {
                currentOnDragActiveChange(false)
            }
        }
    }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> enabled && event.dragData().filePaths().isNotEmpty() },
        target = dropTarget,
    )
}

/** File paths carried by the drag, or an empty list when the payload is not a file list. */
@OptIn(ExperimentalComposeUiApi::class)
private fun DragData?.filePaths(): List<String> =
    (this as? DragData.FilesList)?.readFiles().orEmpty()
