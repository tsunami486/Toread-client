package com.example.toread.ui.reader

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.ui.text.ToreadStrings

/**
 * Day/night state of an open book, shared by the TXT and EPUB hosts.
 *
 * While [followsSystem] is on, the reading surface follows the app theme (which itself may follow the
 * platform), and the device-level manual choice stays unused. The first manual switch has to be
 * confirmed, because it is what stops the reading surface from following the app theme; the choice can
 * be handed back on the 主题与色彩 settings page.
 */
@Stable
internal class ReaderNightModeState(
    private val settingsRepository: LocalSettingsRepository?,
    followsSystem: Boolean,
    manualNightMode: Boolean,
) {
    var followsSystem by mutableStateOf(followsSystem)
        private set

    var manualNightMode by mutableStateOf(manualNightMode)
        private set

    var isConfirmingManualSwitch by mutableStateOf(false)
        private set

    /** The reader's day/night button: asks for confirmation while the surface still follows the theme. */
    fun toggle() {
        if (followsSystem) {
            isConfirmingManualSwitch = true
        } else {
            applyManualNightMode(enabled = !manualNightMode)
        }
    }

    /** Confirmed manual switch: stop following the app theme and pin [nightMode]. */
    fun confirmManualSwitch(nightMode: Boolean) {
        isConfirmingManualSwitch = false
        followsSystem = false
        settingsRepository?.setReaderNightModeFollowSystem(false)
        applyManualNightMode(enabled = nightMode)
    }

    fun dismissManualSwitch() {
        isConfirmingManualSwitch = false
    }

    private fun applyManualNightMode(enabled: Boolean) {
        manualNightMode = enabled
        settingsRepository?.setReaderNightModeEnabled(enabled)
    }
}

@Composable
internal fun rememberReaderNightModeState(
    settingsRepository: LocalSettingsRepository?,
    bookKey: String,
): ReaderNightModeState = remember(bookKey, settingsRepository) {
    ReaderNightModeState(
        settingsRepository = settingsRepository,
        followsSystem = settingsRepository
            ?.appearancePreferences()
            ?.readerNightModeFollowSystem
            ?: true,
        manualNightMode = settingsRepository?.readerNightModeEnabled() ?: false,
    )
}

/** Confirmation shown before a manual switch stops the reading surface from following the app theme. */
@Composable
internal fun ReaderNightModeConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ToreadStrings.SwitchNightModeTitle) },
        text = { Text(ToreadStrings.SwitchNightModeFollowSystemMessage) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(ToreadStrings.DialogConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(ToreadStrings.DialogCancel)
            }
        },
    )
}
