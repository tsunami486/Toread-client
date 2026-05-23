package com.example.toread

interface Platform {
    val name: String
    val kind: PlatformKind
}

enum class PlatformKind {
    Android,
    Desktop,
}

expect fun getPlatform(): Platform
