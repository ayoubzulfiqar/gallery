package com.ayoubzulfiqar.gallery.data.datasource

import com.ayoubzulfiqar.gallery.domain.model.ExifMetadata
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface PlatformMediaDataSource {
    fun observeMedia(batchSize: Int = 100): Flow<List<MediaItem>>
    suspend fun queryMediaWindow(offset: Int, limit: Int): List<MediaItem>
    suspend fun deleteMedia(ids: List<String>): Boolean
    suspend fun fetchExif(id: String): ExifMetadata?
}
