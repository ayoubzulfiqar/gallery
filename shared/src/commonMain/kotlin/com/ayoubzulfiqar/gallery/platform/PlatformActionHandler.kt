package com.ayoubzulfiqar.gallery.platform

interface PlatformActionHandler {
    fun launchGoogleLens(contentUri: String): Boolean
    fun setAsWallpaper(contentUri: String)
    fun shareSanitized(contentUri: String, mimeType: String)
    fun playVideo(contentUri: String): Boolean
}

class NoOpPlatformActionHandler : PlatformActionHandler {
    override fun launchGoogleLens(contentUri: String): Boolean = true
    override fun setAsWallpaper(contentUri: String) {}
    override fun shareSanitized(contentUri: String, mimeType: String) {}
    override fun playVideo(contentUri: String): Boolean = true
}
