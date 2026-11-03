package com.ayoubzulfiqar.gallery.data.repository

import com.ayoubzulfiqar.gallery.data.datasource.PlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.domain.model.ExifMetadata
import com.ayoubzulfiqar.gallery.domain.model.MediaFolder
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaTimelineGroup
import com.ayoubzulfiqar.gallery.domain.repository.MediaRepository
import com.ayoubzulfiqar.gallery.domain.usecase.GroupMediaByTimelineUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DefaultMediaRepository(
    private val platformSource: PlatformMediaDataSource,
    private val localStore: LocalMetadataStore,
    private val timelineGrouper: GroupMediaByTimelineUseCase = GroupMediaByTimelineUseCase()
) : MediaRepository {

    override fun getMediaFlow(): Flow<List<MediaItem>> {
        return combine(
            platformSource.observeMedia(),
            localStore.favorites,
            localStore.tags,
            localStore.vaultedIds
        ) { platformItems, favorites, tagsMap, vaultedIds ->
            platformItems
                .filter { it.id !in vaultedIds }
                .map { item ->
                    item.copy(
                        isFavorite = item.id in favorites,
                        tags = tagsMap[item.id] ?: emptySet()
                    )
                }
        }
    }

    override fun getFoldersFlow(): Flow<List<MediaFolder>> {
        return getMediaFlow().map { items ->
            items
                .groupBy { it.bucketId ?: it.bucketName ?: "Others" }
                .map { (key, groupItems) ->
                    val name = groupItems.firstOrNull()?.bucketName ?: key
                    val sorted = groupItems.sortedByDescending { it.dateModifiedMillis }
                    val cover = sorted.firstOrNull()?.uri ?: ""
                    MediaFolder(
                        id = key,
                        name = name,
                        coverUri = cover,
                        itemCount = sorted.size,
                        items = sorted
                    )
                }
                .sortedWith(
                    compareByDescending<MediaFolder> {
                        it.name.equals("Camera", ignoreCase = true)
                    }.thenByDescending {
                        it.name.contains("Screenshot", ignoreCase = true)
                    }.thenByDescending {
                        it.items.firstOrNull()?.dateModifiedMillis ?: 0L
                    }
                )
        }
    }

    override suspend fun getTimelineGroups(): List<MediaTimelineGroup> {
        val items = getMediaFlow().first()
        return timelineGrouper(items)
    }

    override suspend fun toggleFavorite(id: String): Boolean {
        return localStore.toggleFavorite(id)
    }

    override suspend fun getExif(id: String): ExifMetadata? {
        return platformSource.fetchExif(id)
    }

    override suspend fun deleteMedia(ids: List<String>): Boolean {
        return platformSource.deleteMedia(ids)
    }

    override suspend fun addTag(id: String, tag: String) {
        localStore.addTag(id, tag)
    }

    override suspend fun removeTag(id: String, tag: String) {
        localStore.removeTag(id, tag)
    }
}
