package com.example.toread.platform

import androidx.compose.runtime.Composable

internal enum class ReaderOrientationMode {
    Auto,
    Portrait,
    Landscape,
}

internal enum class ReaderVolumeKey {
    Up,
    Down,
}

@Composable
internal expect fun ReaderDeviceControls(
    enabled: Boolean,
    orientationMode: ReaderOrientationMode,
    volumeKeysEnabled: Boolean,
    onVolumeKey: (ReaderVolumeKey) -> Unit,
    onAppBackground: () -> Unit,
)
