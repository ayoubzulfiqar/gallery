package com.ayoubzulfiqar.gallery.ui.common

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/** Bitmap decoding shared by the image loader, thumbnails, vault previews and the editor export. */
internal object AndroidBitmaps {

    /**
     * Decodes an image downsampled so its longest edge is about [maxDimension], with EXIF
     * orientation applied. [software] forces a mutable-compatible software bitmap (export path).
     */
    fun decodeImage(resolver: ContentResolver, uri: Uri, maxDimension: Int, software: Boolean = false): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return decodeWithImageDecoder(ImageDecoder.createSource(resolver, uri), maxDimension, software)
            } catch (_: Exception) {
                // Fall through to BitmapFactory for formats ImageDecoder rejects.
            }
        }
        return decodeWithBitmapFactory({ resolver.openInputStream(uri) }, maxDimension)
    }

    fun decodeBytes(bytes: ByteArray, maxDimension: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return decodeWithImageDecoder(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), maxDimension, software = false)
            } catch (_: Exception) {
            }
        }
        return decodeWithBitmapFactory({ ByteArrayInputStream(bytes) }, maxDimension)
    }

    fun thumbnail(context: Context, uri: Uri, isVideo: Boolean, size: Int, signal: CancellationSignal?): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return context.contentResolver.loadThumbnail(uri, Size(size, size), signal)
            } catch (e: android.os.OperationCanceledException) {
                throw e
            } catch (_: Exception) {
                // Some providers/formats have no thumbnail support; decode instead.
            }
        }
        return if (isVideo) videoFrame(context, uri, size) else decodeImage(context.contentResolver, uri, size)
    }

    fun videoFrame(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                if (w > 0 && h > 0) {
                    val scale = minOf(1f, maxDimension.toFloat() / max(w, h))
                    retriever.getScaledFrameAtTime(
                        1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        (w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1)
                    )
                } else {
                    retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } else {
                retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            frame?.let { scaleDown(it, maxDimension) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeWithImageDecoder(source: ImageDecoder.Source, maxDimension: Int, software: Boolean): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > maxDimension) {
                val scale = maxDimension.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1)
                )
            }
            if (software) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }

    private fun decodeWithBitmapFactory(open: () -> InputStream?, maxDimension: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            val orientation = open()?.use { stream ->
                runCatching { ExifInterface(stream).let { it.rotationDegrees to it.isFlipped } }.getOrNull()
            }
            val rotated = applyOrientation(decoded, orientation?.first ?: 0, orientation?.second ?: false)
            scaleDown(rotated, maxDimension)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun applyOrientation(bitmap: Bitmap, degrees: Int, flipped: Boolean): Bitmap {
        if (degrees == 0 && !flipped) return bitmap
        val matrix = Matrix().apply {
            if (flipped) postScale(-1f, 1f)
            postRotate(degrees.toFloat())
        }
        val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (result !== bitmap) bitmap.recycle()
        return result
    }

    fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}

actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? =
    AndroidBitmaps.decodeBytes(bytes, maxDimension)?.asImageBitmap()
