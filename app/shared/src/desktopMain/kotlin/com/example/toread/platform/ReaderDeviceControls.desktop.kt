package com.example.toread.platform

import androidx.compose.runtime.Composable

@Composable
internal actual fun ReaderDeviceControls(
    enabled: Boolean,
    orientationMode: ReaderOrientationMode,
    volumeKeysEnabled: Boolean,
    onVolumeKey: (ReaderVolumeKey) -> Unit,
    onAppBackground: () -> Unit,
) {
}
