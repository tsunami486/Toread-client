package com.example.toread.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Opens a book's context menu on a mouse right click, the way each platform expects next to the long
 * press that `combinedClickable` already handles.
 *
 * The menu opens on the *release*: opening it on the press leaves the button still held while the menu
 * is already under the cursor, and releasing then activates whichever item appeared there. Consuming
 * the press and the release keeps the right click from also counting as a tap.
 */
internal fun Modifier.bookContextMenuGesture(
    enabled: Boolean,
    onOpenMenu: () -> Unit,
): Modifier = this.pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitPointerEventScope {
        var secondaryPressed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            when {
                event.type == PointerEventType.Press && event.buttons.isSecondaryPressed -> {
                    secondaryPressed = true
                    event.changes.forEach { change -> change.consume() }
                }

                event.type == PointerEventType.Release && secondaryPressed -> {
                    secondaryPressed = false
                    event.changes.forEach { change -> change.consume() }
                    onOpenMenu()
                }

                event.type == PointerEventType.Exit -> secondaryPressed = false
            }
        }
    }
}
