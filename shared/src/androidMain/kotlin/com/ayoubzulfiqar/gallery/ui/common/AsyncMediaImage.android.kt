package com.ayoubzulfiqar.gallery.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object BitmapMemoryCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024 * 16) // At least 16MB

    val cache = object : LruCache<String, ImageBitmap>(cacheSize) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 4) / 1024
        }
    }
}

@Composable
actual fun AsyncMediaImage(
    uri: String,
    contentDescription: String?,
    modifier: Modifier,
    isThumbnail: Boolean,
    contentScale: ContentScale
) {
    val context = LocalContext.current
    val cacheKey = if (isThumbnail) "${uri}_thumb" else "${uri}_full"

    var imageBitmap by remember(cacheKey) {
        mutableStateOf(BitmapMemoryCache.cache.get(cacheKey))
    }

    LaunchedEffect(cacheKey) {
        if (imageBitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            loadBitmap(context, uri, isThumbnail)
        }
        if (loaded != null) {
            val composeBitmap = loaded.asImageBitmap()
            BitmapMemoryCache.cache.put(cacheKey, composeBitmap)
            imageBitmap = composeBitmap
        }
    }

    Crossfade(targetState = imageBitmap, modifier = modifier) { bmp ->
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            val tintHue = (uri.hashCode() and 0xFF) / 255f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.hsv(tintHue * 360f, 0.28f, 0.32f))
            )
        }
    }
}

private fun loadBitmap(context: Context, uriString: String, isThumbnail: Boolean): Bitmap? {
    if (!uriString.startsWith("content://") && !uriString.startsWith("file://")) {
        return null
    }
    return try {
        val parsedUri = Uri.parse(uriString)
        if (isThumbnail) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.loadThumbnail(parsedUri, Size(360, 360), null)
                } catch (_: Throwable) {
                    decodeSampled(context, parsedUri, 360, 360)
                }
            } else {
                decodeSampled(context, parsedUri, 360, 360)
            }
        } else {
            decodeSampled(context, parsedUri, 1920, 1920)
        }
    } catch (_: Throwable) {
        null
    }
}

private fun decodeSampled(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (_: Throwable) {
        null
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
