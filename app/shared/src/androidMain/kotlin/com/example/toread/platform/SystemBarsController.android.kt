package com.example.toread.platform

import android.os.Build
import android.view.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView

object AndroidSystemBarsBridge {
    var hideSystemBars: (() -> Unit)? = null
    var showSystemBars: (() -> Unit)? = null
}

actual object SystemBarsController {
    actual fun hideSystemBars() {
        AndroidSystemBarsBridge.hideSystemBars?.invoke()
    }

    actual fun showSystemBars() {
        AndroidSystemBarsBridge.showSystemBars?.invoke()
    }
}

@Composable
actual fun rememberSystemBarInsets(): SystemBarInsets {
    val view = LocalView.current
    val density = LocalDensity.current
    val rootInsets = view.rootWindowInsets
    val topPx: Int
    val bottomPx: Int

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val statusBars = rootInsets?.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
        val navigationBars = rootInsets?.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
        topPx = statusBars?.top ?: 0
        bottomPx = navigationBars?.bottom ?: 0
    } else {
        @Suppress("DEPRECATION")
        topPx = rootInsets?.systemWindowInsetTop ?: 0
        @Suppress("DEPRECATION")
        bottomPx = rootInsets?.systemWindowInsetBottom ?: 0
    }

    return with(density) {
        SystemBarInsets(
            top = topPx.toDp(),
            bottom = bottomPx.toDp(),
        )
    }
}
