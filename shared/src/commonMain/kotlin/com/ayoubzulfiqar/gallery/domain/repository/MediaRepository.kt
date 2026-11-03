package com.ayoubzulfiqar.gallery.domain.repository

import com.ayoubzulfiqar.gallery.domain.model.ExifMetadata
import com.ayoubzulfiqar.gallery.domain.model.MediaFolder
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaTimelineGroup
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getMediaFlow(): Flow<List<MediaItem>>
    fun getFoldersFlow(): Flow<List<MediaFolder>>
    suspend fun getTimelineGroups(): List<MediaTimelineGroup>
    suspend fun toggleFavorite(id: String): Boolean
    suspend fun getExif(id: String): ExifMetadata?
    suspend fun deleteMedia(ids: List<String>): Boolean
    suspend fun addTag(id: String, tag: String)
    suspend fun removeTag(id: String, tag: String)
}
