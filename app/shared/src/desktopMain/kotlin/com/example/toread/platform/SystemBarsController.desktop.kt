package com.example.toread.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

actual object SystemBarsController {
    actual fun hideSystemBars() = Unit
    actual fun showSystemBars() = Unit
}

@Composable
actual fun rememberSystemBarInsets(): SystemBarInsets = SystemBarInsets(
    top = 0.dp,
    bottom = 0.dp,
)
