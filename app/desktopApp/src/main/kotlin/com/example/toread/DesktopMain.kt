package com.example.toread

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.toread.platform.AppBackgroundNotifier

fun main() = runToreadDesktopApp()

fun runToreadDesktopApp() = application {
    Window(
        onCloseRequest = {
            AppBackgroundNotifier.notifyEnterBackgroundAndWait()
            exitApplication()
        },
        title = "Toread",
    ) {
        App()
    }
}
