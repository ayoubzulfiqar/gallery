package com.ayoubzulfiqar.gallery.data.repository

import com.ayoubzulfiqar.gallery.data.datasource.DeleteMode
import com.ayoubzulfiqar.gallery.data.datasource.DeleteResult
import com.ayoubzulfiqar.gallery.data.datasource.MediaCapabilities
import com.ayoubzulfiqar.gallery.data.datasource.PlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.domain.model.LibraryState
import com.ayoubzulfiqar.gallery.domain.model.MediaDetails
import com.ayoubzulfiqar.gallery.domain.model.MediaFolder
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update

/**
 * Owns the one and only subscription to the platform library. Every screen observes the same
 * shared stream, so the media store is scanned once per change instead of once per collector.
 */
class DefaultMediaRepository(
    private val platformSource: PlatformMediaDataSource,
    private val localStore: LocalMetadataStore,
    scope: CoroutineScope,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : MediaRepository {

    private val hiddenIds = MutableStateFlow<Set<String>>(emptySet())

    /** Deleted items stay hidden until the platform library confirms they are gone (no flicker back). */
    private val recentlyDeleted = MutableStateFlow<Set<String>>(emptySet())

    override val capabilities: MediaCapabilities get() = platformSource.capabilities

    private val rawLibrary: Flow<LibraryState> = platformSource.observeMedia()
        .onEach { items ->
            if (recentlyDeleted.value.isNotEmpty()) {
                val present = items.mapTo(HashSet()) { it.id }
                recentlyDeleted.update { pending -> pending.filterTo(HashSet()) { it in present } }
            }
        }
        .map { LibraryState(items = it, isLoaded = true) }
        .onStart { emit(LibraryState(isLoaded = false)) }

    override val library: Flow<LibraryState> = combine(
        rawLibrary,
        localStore.favorites,
        localStore.tags,
        combine(hiddenIds, recentlyDeleted) { hidden, deleted -> if (deleted.isEmpty()) hidden else hidden + deleted }
    ) { state, favorites, tagsMap, hidden ->
        val items = state.items.asSequence()
            .filter { it.id !in hidden }
            .map { item ->
                val isFavorite = item.id in favorites
                val tags = tagsMap[item.id] ?: emptySet()
                if (item.isFavorite == isFavorite && item.tags == tags) item else item.copy(isFavorite = isFavorite, tags = tags)
            }
            .toList()
            .sortedByDescending { it.dateTakenMillis } // stable: keeps platform order for ties
        LibraryState(items = items, isLoaded = state.isLoaded)
    }
        .distinctUntilChanged()
        .flowOn(computeDispatcher)
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    override val folders: Flow<List<MediaFolder>> = library
        .map { state -> buildFolders(state.items) }
        .distinctUntilChanged()
        .flowOn(computeDispatcher)
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    override fun refresh() = platformSource.refresh()

    override suspend fun toggleFavorite(id: String): Boolean = localStore.toggleFavorite(id)

    override suspend fun setFavorite(ids: Collection<String>, favorite: Boolean) = localStore.setFavorite(ids, favorite)

    override suspend fun loadDetails(item: MediaItem): MediaDetails = platformSource.loadDetails(item)

    override suspend fun deleteMedia(items: List<MediaItem>, mode: DeleteMode): DeleteResult {
        if (items.isEmpty()) return DeleteResult(emptySet())
        val result = platformSource.deleteMedia(items, mode)
        if (result.deletedIds.isNotEmpty()) {
            recentlyDeleted.update { it + result.deletedIds }
            localStore.forget(result.deletedIds)
            platformSource.refresh()
        }
        return result
    }

    override suspend fun addTag(id: String, tag: String) = localStore.addTag(id, tag)

    override suspend fun removeTag(id: String, tag: String) = localStore.removeTag(id, tag)

    override fun hide(ids: Collection<String>) = hiddenIds.update { it + ids }

    override fun unhide(ids: Collection<String>) = hiddenIds.update { it - ids.toSet() }

    companion object {
        fun buildFolders(items: List<MediaItem>): List<MediaFolder> =
            items.groupBy { it.folderKey }
                .map { (key, groupItems) ->
                    // Items are already newest first.
                    MediaFolder(
                        id = key,
                        name = groupItems.firstNotNullOfOrNull { it.bucketName?.takeIf(String::isNotBlank) }
                            ?: if (key == MediaItem.UNKNOWN_FOLDER_KEY) "Other" else key,
                        coverUri = groupItems.first().uri,
                        itemCount = groupItems.size,
                        items = groupItems
                    )
                }
                .sortedWith(
                    compareByDescending<MediaFolder> { it.name.equals("Camera", ignoreCase = true) }
                        .thenByDescending { it.name.contains("Screenshot", ignoreCase = true) }
                        .thenByDescending { it.items.first().dateTakenMillis }
                        .thenBy { it.name.lowercase() }
                )
    }
}
