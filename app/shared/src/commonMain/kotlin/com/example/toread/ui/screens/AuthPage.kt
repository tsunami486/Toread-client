package com.example.toread.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toread.auth.AuthClient
import com.example.toread.auth.AuthClientResult
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.ui.components.ScreenColumn
import kotlinx.coroutines.launch

@Composable
internal fun AuthPage(
    settingsRepository: LocalSettingsRepository?,
    authClient: AuthClient?,
    onAuthenticated: () -> Unit,
    onGuestMode: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    ScreenColumn {
        Text(
            text = if (isRegister) "注册 Toread" else "登录 Toread",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text("Toread 是导入型阅读器，只读取你主动选择的本地 EPUB、PDF 和 TXT 文件。")
        authError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
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
                enabled = settingsRepository != null && authClient != null && !isSubmitting,
                onClick = {
                    val trimmedEmail = email.trim()
                    if (trimmedEmail.isBlank() || password.isBlank()) {
                        authError = "请输入邮箱和密码。"
                        return@Button
                    }

                    scope.launch {
                        isSubmitting = true
                        authError = null
                        val result = if (isRegister) {
                            authClient?.register(trimmedEmail, password)
                        } else {
                            authClient?.login(trimmedEmail, password)
                        }
                        when (result) {
                            is AuthClientResult.Success -> {
                                settingsRepository?.setLoggedIn(result.session.email)
                                settingsRepository?.setRefreshToken(result.session.refreshToken)
                                onAuthenticated()
                            }
                            is AuthClientResult.Failure -> {
                                authError = result.message
                            }
                            null -> {
                                authError = "认证服务未初始化。"
                            }
                        }
                        isSubmitting = false
                    }
                },
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(if (isRegister) "注册并进入" else "登录")
                }
            }
            OutlinedButton(
                enabled = !isSubmitting,
                onClick = {
                    authError = null
                    isRegister = !isRegister
                },
            ) {
                Text(if (isRegister) "去登录" else "去注册")
            }
            TextButton(
                enabled = settingsRepository != null && !isSubmitting,
                onClick = {
                    onGuestMode()
                },
            ) {
                Text("游客进入")
            }
        }
    }
}
