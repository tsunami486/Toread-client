package com.example.toread.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toread.platform.PlatformBackHandler
import com.example.toread.settings.AuthMode
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.ui.components.ScreenColumn

@Composable
internal fun SettingsPage(
    settingsRepository: LocalSettingsRepository?,
    dynamicColorEnabled: Boolean,
    defaultServerBaseUrl: String,
    onDynamicColorEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    var serverUrl by remember(settingsRepository, defaultServerBaseUrl) {
        mutableStateOf(settingsRepository?.serverBaseUrl(defaultServerBaseUrl) ?: defaultServerBaseUrl)
    }
    PlatformBackHandler(enabled = true, onBack = onBack)

    val authLabel = when (settingsRepository?.currentAuthMode()) {
        AuthMode.LoggedIn -> settingsRepository.userEmail()?.let { "已登录：$it" } ?: "已登录"
        AuthMode.Guest -> "游客模式"
        null -> "未登录"
    }

    ScreenColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onBack) {
                Text("返回书架")
            }
        }
        Text(authLabel)
        OutlinedTextField(
            value = serverUrl,
            onValueChange = {
                serverUrl = it
                settingsRepository?.setServerBaseUrl(it)
            },
            label = { Text("服务器地址") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
        )
        Row(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("动态取色", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = dynamicColorEnabled,
                onCheckedChange = onDynamicColorEnabledChange,
                enabled = settingsRepository != null,
            )
        }
        Text("同步：阅读进度已接入 Ktor 服务，缺少登录 token 时会排队等待。")
        Text("本地存储：应用私有目录。")
        OutlinedButton(onClick = onLogout) {
            Text("退出登录状态")
        }
    }
}
