package com.example.toread

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform