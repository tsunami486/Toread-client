package com.example.toread.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
internal expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?
