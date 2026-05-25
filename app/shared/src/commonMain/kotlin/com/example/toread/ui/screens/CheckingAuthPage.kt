package com.example.toread.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.example.toread.ui.components.ScreenColumn

@Composable
internal fun CheckingAuthPage() {
    ScreenColumn {
        Text("正在启动", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("正在读取本地登录状态。")
    }
}
