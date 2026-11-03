package com.ayoubzulfiqar.gallery

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform