package com.example.toread.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

object AndroidBackHandlerBridge {
    private var backHandler: (() -> Unit)? = null

    fun dispatchBack(): Boolean {
        val handler = backHandler ?: return false
        handler()
        return true
    }

    fun setBackHandler(handler: (() -> Unit)?) {
        backHandler = handler
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val currentOnBack = rememberUpdatedState(onBack)

    DisposableEffect(enabled) {
        if (enabled) {
            AndroidBackHandlerBridge.setBackHandler { currentOnBack.value() }
        }

        onDispose {
            if (enabled) {
                AndroidBackHandlerBridge.setBackHandler(null)
            }
        }
    }
}
