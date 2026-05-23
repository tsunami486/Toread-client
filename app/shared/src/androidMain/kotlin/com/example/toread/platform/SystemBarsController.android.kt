package com.example.toread.platform

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
