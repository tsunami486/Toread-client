package com.example.toread.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

object AndroidReaderDeviceControlsBridge {
    private data class VolumeKeyHandler(
        val token: Any,
        val onVolumeKey: (ReaderVolumeKey) -> Unit,
    )

    private var volumeKeyHandler: VolumeKeyHandler? = null

    internal fun registerVolumeKeyHandler(onVolumeKey: (ReaderVolumeKey) -> Unit): Any {
        val token = Any()
        volumeKeyHandler = VolumeKeyHandler(
            token = token,
            onVolumeKey = onVolumeKey,
        )
        return token
    }

    internal fun unregisterVolumeKeyHandler(token: Any) {
        if (volumeKeyHandler?.token === token) {
            volumeKeyHandler = null
        }
    }

    fun dispatchVolumeKey(keyCode: Int): Boolean {
        val volumeKey = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> ReaderVolumeKey.Up
            KeyEvent.KEYCODE_VOLUME_DOWN -> ReaderVolumeKey.Down
            else -> return false
        }
        val handler = volumeKeyHandler ?: return false
        handler.onVolumeKey(volumeKey)
        return true
    }
}

@Composable
internal actual fun ReaderDeviceControls(
    enabled: Boolean,
    orientationMode: ReaderOrientationMode,
    volumeKeysEnabled: Boolean,
    onVolumeKey: (ReaderVolumeKey) -> Unit,
    onAppBackground: () -> Unit,
) {
    val currentOnVolumeKey = rememberUpdatedState(onVolumeKey)
    val currentOnAppBackground = rememberUpdatedState(onAppBackground)
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val originalRequestedOrientation = remember(activity, enabled) {
        activity?.requestedOrientation?.takeIf { enabled }
    }

    SideEffect {
        if (enabled && activity != null) {
            val requestedOrientation = orientationMode.toRequestedOrientation()
            if (activity.requestedOrientation != requestedOrientation) {
                activity.requestedOrientation = requestedOrientation
            }
        }
    }

    DisposableEffect(activity, enabled, originalRequestedOrientation) {
        onDispose {
            if (
                enabled &&
                activity != null &&
                originalRequestedOrientation != null &&
                activity.requestedOrientation != originalRequestedOrientation
            ) {
                activity.requestedOrientation = originalRequestedOrientation
            }
        }
    }

    DisposableEffect(lifecycleOwner, enabled, volumeKeysEnabled) {
        var token: Any? = null

        fun unregisterVolumeKeyHandler() {
            token?.let(AndroidReaderDeviceControlsBridge::unregisterVolumeKeyHandler)
            token = null
        }

        fun updateVolumeKeyHandler() {
            val shouldRegister =
                enabled &&
                    volumeKeysEnabled &&
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (shouldRegister && token == null) {
                token = AndroidReaderDeviceControlsBridge.registerVolumeKeyHandler { key ->
                    currentOnVolumeKey.value(key)
                }
            } else if (!shouldRegister) {
                unregisterVolumeKeyHandler()
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            updateVolumeKeyHandler()
            if (enabled && event == Lifecycle.Event.ON_STOP) {
                currentOnAppBackground.value()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        updateVolumeKeyHandler()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregisterVolumeKeyHandler()
        }
    }
}

private fun ReaderOrientationMode.toRequestedOrientation(): Int = when (this) {
    ReaderOrientationMode.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    ReaderOrientationMode.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    ReaderOrientationMode.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> {
        val base = baseContext
        if (base === this) null else base.findActivity()
    }
    else -> null
}
