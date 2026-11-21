package com.ayoubzulfiqar.gallery.ui.common

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes encoded bytes and downsamples to [maxDimension] so a 50 MP vault preview does not keep a
 * 200 MB bitmap alive. (The decode itself is still full-resolution transiently; a codec-level
 * downsample can replace it when PhotoKit integration lands.)
 */
actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? = try {
    val full = Image.makeFromEncoded(bytes)
    val longest = max(full.width, full.height)
    if (longest <= maxDimension) {
        full.toComposeImageBitmap()
    } else {
        val scale = maxDimension.toFloat() / longest
        val width = (full.width * scale).roundToInt().coerceAtLeast(1)
        val height = (full.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap()
        scaled.allocPixels(ImageInfo.makeN32Premul(width, height))
        val canvas = Canvas(scaled)
        canvas.scale(scale, scale)
        canvas.drawImage(full, 0f, 0f, null)
        full.close()
        Image.makeFromBitmap(scaled).toComposeImageBitmap()
    }
} catch (_: Exception) {
    null
}
