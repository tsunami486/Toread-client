package com.example.toread.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.ui.components.ScreenColumn

@Composable
internal fun AuthPage(settingsRepository: LocalSettingsRepository?, onDone: () -> Unit) {
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    ScreenColumn {
        Text(
            text = if (isRegister) "注册 Toread" else "登录 Toread",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text("Toread 是导入型阅读器，只读取你主动选择的本地 EPUB、PDF 和 TXT 文件。")
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("邮箱") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = settingsRepository != null,
                onClick = {
                    settingsRepository?.setLoggedIn(email.trim())
                    onDone()
                },
            ) {
                Text(if (isRegister) "注册并进入" else "登录")
            }
            OutlinedButton(onClick = { isRegister = !isRegister }) {
                Text(if (isRegister) "去登录" else "去注册")
            }
            TextButton(
                enabled = settingsRepository != null,
                onClick = {
                    settingsRepository?.setGuestMode()
                    onDone()
                },
            ) {
                Text("游客进入")
            }
        }
    }
}
