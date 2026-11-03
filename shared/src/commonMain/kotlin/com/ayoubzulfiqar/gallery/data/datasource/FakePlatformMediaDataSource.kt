package com.ayoubzulfiqar.gallery.data.datasource

import com.ayoubzulfiqar.gallery.domain.model.ExifMetadata
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakePlatformMediaDataSource : PlatformMediaDataSource {
    private val _mediaFlow = MutableStateFlow<List<MediaItem>>(emptyList())

    fun populateWithSyntheticMedia(count: Int) {
        val now = 1730000000000L
        val dayMillis = 86_400_000L
        val generated = (0 until count).map { i ->
            val isVideo = i > 0 && i % 10 == 0
            val dayOffset = (i / 15) * dayMillis
            MediaItem(
                id = "media_$i",
                uri = "file:///storage/emulated/0/DCIM/Camera/IMG_20261103_${i.toString().padStart(4, '0')}.${if (isVideo) "mp4" else "jpg"}",
                displayName = "IMG_20261103_${i.toString().padStart(4, '0')}.${if (isVideo) "mp4" else "jpg"}",
                mimeType = if (isVideo) "video/mp4" else "image/jpeg",
                mediaType = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                dateTakenMillis = now - dayOffset - (i % 15) * 60_000L,
                dateModifiedMillis = now - dayOffset,
                width = if (isVideo) 1920 else 4032,
                height = if (isVideo) 1080 else 3024,
                sizeBytes = if (isVideo) 45_000_000L else 4_200_000L,
                durationMs = if (isVideo) 15_400L else null,
                latitude = 37.7749 + (i % 5) * 0.01,
                longitude = -122.4194 + (i % 5) * 0.01
            )
        }
        _mediaFlow.value = generated
    }

    override fun observeMedia(batchSize: Int): Flow<List<MediaItem>> = _mediaFlow.asStateFlow()

    override suspend fun queryMediaWindow(offset: Int, limit: Int): List<MediaItem> {
        val current = _mediaFlow.value
        if (offset >= current.size) return emptyList()
        val toIndex = minOf(offset + limit, current.size)
        return current.subList(offset, toIndex)
    }

    override suspend fun deleteMedia(ids: List<String>): Boolean {
        val toRemove = ids.toSet()
        _mediaFlow.update { current ->
            current.filterNot { it.id in toRemove }
        }
        return true
    }

    override suspend fun fetchExif(id: String): ExifMetadata? {
        val item = _mediaFlow.value.find { it.id == id } ?: return null
        return ExifMetadata(
            make = "Google",
            model = "Pixel 9 Pro",
            lensModel = "Pixel 9 Pro Main Camera 24mm eq",
            iso = 50,
            aperture = 1.68,
            shutterSpeed = "1/500",
            focalLength = 6.9,
            flash = "Off",
            whiteBalance = "Auto",
            colorSpace = "sRGB",
            latitude = item.latitude,
            longitude = item.longitude,
            altitude = 15.2,
            address = "San Francisco, California"
        )
    }
}
