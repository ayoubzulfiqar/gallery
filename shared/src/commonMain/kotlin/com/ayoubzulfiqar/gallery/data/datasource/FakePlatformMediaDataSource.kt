package com.ayoubzulfiqar.gallery.data.datasource

import com.ayoubzulfiqar.gallery.core.io.ByteArraySink
import com.ayoubzulfiqar.gallery.core.io.ByteArraySource
import com.ayoubzulfiqar.gallery.core.io.ByteSink
import com.ayoubzulfiqar.gallery.core.io.ByteSource
import com.ayoubzulfiqar.gallery.domain.model.ExifMetadata
import com.ayoubzulfiqar.gallery.domain.model.MediaDetails
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory library used by tests, previews and platforms without a native media store integration.
 * Content is synthetic but real bytes, so vault and import flows can be exercised end to end.
 */
class FakePlatformMediaDataSource(
    override val capabilities: MediaCapabilities = MediaCapabilities(isDemoLibrary = true)
) : PlatformMediaDataSource {
    private val _mediaFlow = MutableStateFlow<List<MediaItem>>(emptyList())
    private val content = mutableMapOf<String, ByteArray>()
    private var importCounter = 0

    /** Test hooks. */
    var cancelNextDelete: Boolean = false
    var failImports: Boolean = false
    var refreshCount: Int = 0
        private set

    val currentItems: List<MediaItem> get() = _mediaFlow.value

    fun populateWithSyntheticMedia(count: Int) {
        val now = 1730000000000L
        val dayMillis = 86_400_000L
        val generated = (0 until count).map { i ->
            val isVideo = i > 0 && i % 10 == 0
            val dayOffset = (i / 15) * dayMillis
            val folderName = when (i % 3) {
                0 -> "Camera"
                1 -> "Screenshots"
                else -> "Download"
            }
            val name = "IMG_20261103_${i.toString().padStart(4, '0')}.${if (isVideo) "mp4" else "jpg"}"
            MediaItem(
                id = "media_$i",
                uri = "demo://media/$i",
                displayName = name,
                mimeType = if (isVideo) "video/mp4" else "image/jpeg",
                mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                dateTakenMillis = now - dayOffset - (i % 15) * 60_000L,
                dateModifiedMillis = now - dayOffset,
                width = if (isVideo) 1920 else 4032,
                height = if (isVideo) 1080 else 3024,
                sizeBytes = SYNTHETIC_SIZE.toLong(),
                durationMs = if (isVideo) 15_400L else null,
                latitude = 37.7749 + (i % 5) * 0.01,
                longitude = -122.4194 + (i % 5) * 0.01,
                bucketId = "bucket_${folderName.lowercase()}",
                bucketName = folderName,
                relativePath = if (folderName == "Camera") "DCIM/Camera/" else "Pictures/$folderName/"
            )
        }
        generated.forEach { content[it.id] = syntheticBytes(it.id) }
        _mediaFlow.value = generated.sortedByDescending { it.dateTakenMillis }
    }

    fun setItems(items: List<MediaItem>) {
        items.forEach { content.getOrPut(it.id) { syntheticBytes(it.id) } }
        _mediaFlow.value = items.sortedByDescending { it.dateTakenMillis }
    }

    fun contentOf(id: String): ByteArray? = content[id]?.copyOf()

    override fun observeMedia(): Flow<List<MediaItem>> = _mediaFlow.asStateFlow()

    override fun refresh() {
        refreshCount++
    }

    override suspend fun loadDetails(item: MediaItem): MediaDetails {
        val current = _mediaFlow.value.firstOrNull { it.id == item.id } ?: item
        val exif = if (current.isVideo) {
            ExifMetadata(latitude = current.latitude, longitude = current.longitude)
        } else {
            ExifMetadata(
                make = "Demo",
                model = "Sample Camera",
                iso = 100,
                aperture = 1.8,
                shutterSpeed = "1/250",
                focalLength = 6.9,
                latitude = current.latitude,
                longitude = current.longitude,
                pixelWidth = current.width,
                pixelHeight = current.height
            )
        }
        return MediaDetails(current, exif)
    }

    override suspend fun deleteMedia(items: List<MediaItem>, mode: DeleteMode): DeleteResult {
        if (cancelNextDelete) {
            cancelNextDelete = false
            return DeleteResult(emptySet(), cancelled = true)
        }
        val ids = items.map { it.id }.toSet()
        val existing = _mediaFlow.value.map { it.id }.toSet()
        _mediaFlow.update { current -> current.filterNot { it.id in ids } }
        ids.forEach { content.remove(it) }
        return DeleteResult(ids intersect existing)
    }

    override suspend fun openContent(item: MediaItem): ByteSource {
        val bytes = content[item.id] ?: throw NoSuchElementException("No content for ${item.id}")
        return ByteArraySource(bytes)
    }

    override suspend fun createThumbnail(item: MediaItem, maxDimension: Int): ByteArray? =
        content[item.id]?.copyOf(minOf(64, content[item.id]!!.size))

    override suspend fun importMedia(request: MediaImportRequest, writer: suspend (ByteSink) -> Unit): ImportResult {
        if (failImports) return ImportResult.Failure("Import disabled")
        val sink = ByteArraySink()
        try {
            writer(sink)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportResult.Failure(e.message ?: "Import failed", e)
        }
        val id = "imported_${importCounter++}"
        val bytes = sink.toByteArray()
        content[id] = bytes
        val item = MediaItem(
            id = id,
            uri = "demo://media/$id",
            displayName = request.displayName,
            mimeType = request.mimeType,
            mediaType = request.mediaType,
            dateTakenMillis = request.dateTakenMillis,
            dateModifiedMillis = request.dateTakenMillis,
            width = 0,
            height = 0,
            sizeBytes = bytes.size.toLong(),
            bucketId = "bucket_restored",
            bucketName = request.relativePath?.trimEnd('/')?.substringAfterLast('/') ?: "Restored",
            relativePath = request.relativePath
        )
        _mediaFlow.update { (it + item).sortedByDescending { m -> m.dateTakenMillis } }
        return ImportResult.Success(item.uri)
    }

    private fun syntheticBytes(id: String): ByteArray {
        val seed = "SYNTHETIC-MEDIA:$id:".encodeToByteArray()
        return ByteArray(SYNTHETIC_SIZE) { seed[it % seed.size] }
    }

    private companion object {
        const val SYNTHETIC_SIZE = 2048
    }
}
