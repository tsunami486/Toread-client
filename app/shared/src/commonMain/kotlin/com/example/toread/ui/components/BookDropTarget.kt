package com.example.toread.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toread.platform.PlatformFile
import com.example.toread.ui.icons.ToreadSymbolIcon
import com.example.toread.ui.icons.ToreadSymbols
import com.example.toread.ui.text.ToreadStrings

/**
 * Accepts books dragged in from the operating system, e.g. from Explorer or Finder.
 *
 * Desktop only: [bookFileDropTarget] is a no-op on Android, where books arrive through the system
 * picker or a share intent. Being an expect function keeps the drop handling out of the platform
 * entry point, so the overlay and the import itself stay in the shared app shell.
 */
@Composable
internal expect fun Modifier.bookFileDropTarget(
    enabled: Boolean,
    onFilesDropped: (List<PlatformFile>) -> Unit,
    onDragActiveChange: (Boolean) -> Unit,
): Modifier

/** Shown while a drag hovers the window: it states what a release would do right now. */
@Composable
internal fun BookDropOverlay(
    isImporting: Boolean,
    fileCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToreadSymbolIcon(
                    symbol = if (isImporting) ToreadSymbols.HourglassEmpty else ToreadSymbols.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = if (isImporting) {
                        ToreadStrings.DropBooksImporting
                    } else {
                        ToreadStrings.DropBooksTitle
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (fileCount > 1) {
                        ToreadStrings.dropBooksCount(fileCount)
                    } else {
                        ToreadStrings.DropBooksDescription
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
