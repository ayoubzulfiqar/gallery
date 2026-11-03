package com.ayoubzulfiqar.gallery.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ayoubzulfiqar.gallery.ui.theme.GalleryIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImageRequest(
    val uri: String,
    /** Longest edge in pixels the caller needs; loaders downsample to at least this size. */
    val maxDimension: Int,
    val isVideo: Boolean = false,
    val mimeType: String? = null
)

/** Decodes media into bitmaps. Implementations must be main-safe (do their own dispatching). */
interface PlatformImageLoader {
    /** Longest edge used for full-screen viewing on this device. */
    val fullImageMaxDimension: Int get() = 2048
    val memoryCacheBytes: Long get() = 48L * 1024 * 1024

    suspend fun load(request: ImageRequest): ImageBitmap?
}

object NoOpImageLoader : PlatformImageLoader {
    override suspend fun load(request: ImageRequest): ImageBitmap? = null
}

val LocalImageLoader = staticCompositionLocalOf<PlatformImageLoader> { NoOpImageLoader }

/** Decodes encoded image bytes (JPEG/PNG/WebP/HEIC where supported), downsampling to [maxDimension]. */
expect fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap?

/** Process-wide LRU of decoded bitmaps, accessed only from the main thread. */
internal object ImageMemoryCache {
    private val entries = LinkedHashMap<String, ImageBitmap>()
    private var currentBytes = 0L
    var maxBytes: Long = 48L * 1024 * 1024

    operator fun get(key: String): ImageBitmap? {
        val value = entries.remove(key) ?: return null
        entries[key] = value // move to most-recently-used
        return value
    }

    fun put(key: String, bitmap: ImageBitmap) {
        val size = bitmap.byteSize()
        if (size > maxBytes / 4) return // never let one huge bitmap evict everything
        entries.remove(key)?.let { currentBytes -= it.byteSize() }
        entries[key] = bitmap
        currentBytes += size
        val iterator = entries.entries.iterator()
        while (currentBytes > maxBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            currentBytes -= eldest.value.byteSize()
            iterator.remove()
        }
    }

    fun clear() {
        entries.clear()
        currentBytes = 0
    }

    private fun ImageBitmap.byteSize(): Long = width.toLong() * height * 4
}

const val THUMBNAIL_DIMENSION = 384

private sealed interface ImageState {
    data object Loading : ImageState

    /** [fresh] is true when the bitmap was decoded after the tile appeared (eligible for a fade-in). */
    class Ready(val bitmap: ImageBitmap, val fresh: Boolean) : ImageState
    data object Failed : ImageState
}

/**
 * Loads [uri] through [LocalImageLoader] with memory caching, a neutral placeholder while loading
 * and a broken-image indicator on failure (instead of an endless placeholder).
 */
@Composable
fun AsyncMediaImage(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isThumbnail: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    isVideo: Boolean = false,
    mimeType: String? = null,
    colorFilter: ColorFilter? = null,
    /** Exact decode size in pixels (e.g. the grid cell size); overrides [isThumbnail]'s default. */
    requestedSize: Int? = null
) {
    val loader = LocalImageLoader.current
    val maxDimension = requestedSize ?: if (isThumbnail) THUMBNAIL_DIMENSION else loader.fullImageMaxDimension
    val cacheKey = "$uri|$maxDimension"
    // Cache hits render immediately with no coroutine at all; only misses launch a load.
    val stateHolder = remember(cacheKey, loader) {
        mutableStateOf<ImageState>(ImageMemoryCache[cacheKey]?.let { ImageState.Ready(it, fresh = false) } ?: ImageState.Loading)
    }
    if (stateHolder.value is ImageState.Loading) {
        LaunchedEffect(cacheKey, loader) {
            ImageMemoryCache.maxBytes = loader.memoryCacheBytes
            stateHolder.value = try {
                val bitmap = loader.load(ImageRequest(uri, maxDimension, isVideo, mimeType))
                if (bitmap != null) {
                    ImageMemoryCache.put(cacheKey, bitmap)
                    ImageState.Ready(bitmap, fresh = true)
                } else {
                    ImageState.Failed
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                ImageState.Failed
            }
        }
    }
    val state = stateHolder.value
    ImageStateContent(state, uri, contentDescription, contentScale, colorFilter, modifier)
}

/**
 * Displays an image whose encoded bytes come from [loadBytes] (e.g. decrypted vault content).
 * Bitmaps are kept only in composition memory, never in the shared cache or on disk.
 */
@Composable
fun EncryptedImage(
    key: String,
    contentDescription: String?,
    loadBytes: suspend () -> ByteArray?,
    modifier: Modifier = Modifier,
    maxDimension: Int = THUMBNAIL_DIMENSION,
    contentScale: ContentScale = ContentScale.Crop
) {
    val state by produceState<ImageState>(ImageState.Loading, key) {
        value = try {
            val bytes = loadBytes()
            val bitmap = bytes?.let { withContext(Dispatchers.Default) { decodeImageBitmap(it, maxDimension) } }
            bytes?.fill(0)
            if (bitmap != null) ImageState.Ready(bitmap, fresh = true) else ImageState.Failed
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            ImageState.Failed
        }
    }
    ImageStateContent(state, key, contentDescription, contentScale, null, modifier)
}

/** Loads a bitmap for custom drawing (editor). Returns null while loading or on failure. */
@Composable
fun rememberMediaBitmap(uri: String, maxDimension: Int, isVideo: Boolean = false): ImageBitmap? {
    val loader = LocalImageLoader.current
    val cacheKey = "$uri|$maxDimension"
    val bitmap by produceState(ImageMemoryCache[cacheKey], cacheKey, loader) {
        if (value != null) return@produceState
        value = try {
            loader.load(ImageRequest(uri, maxDimension, isVideo))?.also { ImageMemoryCache.put(cacheKey, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }
    return bitmap
}

/**
 * Lean renderer used for every tile: the placeholder is painted with drawBehind (no extra layout
 * node) and freshly decoded images fade in through graphicsLayer, so the animation runs in the
 * draw phase without recomposing. Cached images appear instantly.
 */
@Composable
private fun ImageStateContent(
    state: ImageState,
    seed: String,
    contentDescription: String?,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    modifier: Modifier
) {
    val placeholder = remember(seed) {
        val hue = (seed.hashCode() and 0xFF) / 255f
        Color.hsv(hue * 360f, 0.18f, 0.30f)
    }
    Box(modifier = modifier.drawBehind { if (state !is ImageState.Ready) drawRect(placeholder) }) {
        when (state) {
            is ImageState.Ready -> {
                // Only animate images that arrived after first composition; cache hits skip the fade.
                val fade = remember(state.bitmap) { Animatable(if (state.fresh) 0f else 1f) }
                if (state.fresh) LaunchedEffect(state.bitmap) { fade.animateTo(1f, tween(140)) }
                Image(
                    bitmap = state.bitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
                    contentScale = contentScale,
                    colorFilter = colorFilter
                )
            }
            ImageState.Loading -> Unit
            ImageState.Failed -> Icon(
                imageVector = GalleryIcons.BrokenImage,
                contentDescription = "Preview unavailable",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.Center).size(22.dp)
            )
        }
    }
}
