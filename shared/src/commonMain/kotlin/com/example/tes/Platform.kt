package com.example.tes

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform