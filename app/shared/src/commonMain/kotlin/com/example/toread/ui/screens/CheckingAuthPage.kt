package com.example.toread.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.example.toread.ui.components.ScreenColumn
import com.example.toread.ui.text.ToreadStrings

@Composable
internal fun CheckingAuthPage() {
    ScreenColumn {
        Text(ToreadStrings.CheckingStartupTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(ToreadStrings.CheckingAuthState)
    }
}
