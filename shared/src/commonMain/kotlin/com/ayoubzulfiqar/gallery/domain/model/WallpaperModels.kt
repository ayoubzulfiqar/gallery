package com.ayoubzulfiqar.gallery.domain.model

/**
 * Region of the (EXIF-oriented) source image to apply as wallpaper, in source pixels.
 * Produced by the in-app crop screen; the platform applies exactly this rect.
 */
data class WallpaperCropRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** Which wallpaper surface(s) the crop is applied to. */
enum class WallpaperTarget { HOME, LOCK, BOTH }

/**
 * A complete wallpaper application request. [rect] is expressed in the pixel space of the
 * preview bitmap the user aligned ([basisWidth] x [basisHeight]); the platform scales it to
 * the full-resolution decode, so preview downsampling never shifts the chosen region.
 */
data class WallpaperCropRequest(
    val rect: WallpaperCropRect,
    val basisWidth: Int,
    val basisHeight: Int,
    val target: WallpaperTarget
)