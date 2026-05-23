package com.example.toread

class DesktopPlatform : Platform {
    override val name: String = "Desktop ${System.getProperty("os.name")}"
    override val kind: PlatformKind = PlatformKind.Desktop
}

actual fun getPlatform(): Platform = DesktopPlatform()
