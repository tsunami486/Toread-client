package com.example.toread.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

expect object SystemBarsController {
    fun hideSystemBars()
    fun showSystemBars()
}

data class SystemBarInsets(
    val top: Dp,
    val bottom: Dp,
)

@Composable
expect fun rememberSystemBarInsets(): SystemBarInsets
