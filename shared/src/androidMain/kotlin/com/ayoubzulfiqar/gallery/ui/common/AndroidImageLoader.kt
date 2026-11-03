package com.ayoubzulfiqar.gallery.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * MediaStore-backed image loading. Decoding is bounded to a few parallel workers so fast scrolling
 * through thousands of thumbnails cannot spike memory, and cancelled requests abort the platform
 * thumbnail load via [CancellationSignal].
 */
class AndroidImageLoader(private val context: Context) : PlatformImageLoader {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(4)

    override val fullImageMaxDimension: Int = context.resources.displayMetrics.let { metrics ->
        // Enough detail for ~2x zoom without decoding 50 MP originals into memory.
        min(4096, max(metrics.widthPixels, metrics.heightPixels) * 2)
    }

    // Tiles are decoded at cell size, so a quarter of the heap holds several screens of thumbnails.
    override val memoryCacheBytes: Long = Runtime.getRuntime().maxMemory() / 4

    override suspend fun load(request: ImageRequest): ImageBitmap? {
        val uri = Uri.parse(request.uri)
        if (uri.scheme != "content" && uri.scheme != "file") return null
        return withContext(decodeDispatcher) {
            val signal = CancellationSignal()
            val registration = currentCoroutineContext()[Job]?.invokeOnCompletion { cause -> if (cause != null) signal.cancel() }
            try {
                val bitmap = when {
                    request.maxDimension <= THUMBNAIL_DIMENSION * 2 ->
                        AndroidBitmaps.thumbnail(context, uri, request.isVideo, request.maxDimension, signal)
                            ?.let { coverScale(it, request.maxDimension) }
                    request.isVideo ->
                        AndroidBitmaps.thumbnail(context, uri, isVideo = true, size = request.maxDimension, signal = signal)
                    else -> AndroidBitmaps.decodeImage(context.contentResolver, uri, request.maxDimension)
                }
                // Start the GPU texture upload now, on this worker, instead of during the frame.
                bitmap?.prepareToDraw()
                bitmap?.asImageBitmap()
            } catch (_: android.os.OperationCanceledException) {
                null
            } catch (_: OutOfMemoryError) {
                null
            } catch (_: SecurityException) {
                null // permission revoked while browsing
            } finally {
                registration?.dispose()
            }
        }
    }

    /**
     * MediaStore only downsamples thumbnails by integer factors, so a 270 px cell can receive a
     * 512 px bitmap. Scale so the short side matches the cell (cover-fit keeps center-crop sharp).
     */
    private fun coverScale(bitmap: Bitmap, target: Int): Bitmap {
        val shortSide = min(bitmap.width, bitmap.height)
        if (shortSide <= target * 5 / 4) return bitmap
        val scale = target.toFloat() / shortSide
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
