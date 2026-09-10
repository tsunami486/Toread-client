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
import com.example.toread.auth.AuthSession
import com.example.toread.settings.LocalSettingsRepository
import com.example.toread.ui.components.ScreenColumn
import com.example.toread.ui.text.ToreadStrings
import kotlinx.coroutines.launch

@Composable
internal fun AuthPage(
    settingsRepository: LocalSettingsRepository?,
    authClient: AuthClient?,
    onAuthenticated: suspend (AuthSession) -> Unit,
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
            text = if (isRegister) ToreadStrings.RegisterTitle else ToreadStrings.LoginTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(ToreadStrings.AuthDescription)
        authError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(ToreadStrings.EmailLabel) },
            singleLine = true,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(ToreadStrings.PasswordLabel) },
            singleLine = true,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = settingsRepository != null && authClient != null && !isSubmitting,
                onClick = {
                    val trimmedEmail = email.trim()
                    if (trimmedEmail.isBlank() || password.isBlank()) {
                        authError = ToreadStrings.EmailPasswordRequired
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
                                onAuthenticated(result.session)
                            }
                            is AuthClientResult.Failure -> {
                                authError = ToreadStrings.safeUserVisibleMessage(
                                    message = result.message,
                                    fallback = ToreadStrings.AuthServiceUnavailable,
                                )
                            }
                            null -> {
                                authError = ToreadStrings.AuthServiceUnavailable
                            }
                        }
                        isSubmitting = false
                    }
                },
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(if (isRegister) ToreadStrings.RegisterAndEnter else ToreadStrings.LoginAction)
                }
            }
            OutlinedButton(
                enabled = !isSubmitting,
                onClick = {
                    authError = null
                    isRegister = !isRegister
                },
            ) {
                Text(if (isRegister) ToreadStrings.GoLogin else ToreadStrings.GoRegister)
            }
            TextButton(
                enabled = settingsRepository != null && !isSubmitting,
                onClick = {
                    onGuestMode()
                },
            ) {
                Text(ToreadStrings.GuestMode)
            }
        }
    }
}
