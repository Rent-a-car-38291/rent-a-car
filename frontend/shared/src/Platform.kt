package com.avans.rentacar

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
