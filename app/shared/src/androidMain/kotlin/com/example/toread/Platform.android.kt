package com.example.toread

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val kind: PlatformKind = PlatformKind.Android
}

actual fun getPlatform(): Platform = AndroidPlatform()
