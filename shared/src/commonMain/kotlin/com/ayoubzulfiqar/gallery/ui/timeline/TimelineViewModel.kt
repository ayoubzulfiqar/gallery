package com.ayoubzulfiqar.gallery.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.domain.model.MediaFilter
import com.ayoubzulfiqar.gallery.domain.model.MediaFolder
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaScope
import com.ayoubzulfiqar.gallery.domain.model.MediaTimelineGroup
import com.ayoubzulfiqar.gallery.domain.model.TimelineDensity
import com.ayoubzulfiqar.gallery.domain.repository.MediaRepository
import com.ayoubzulfiqar.gallery.domain.usecase.GroupMediaByTimelineUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Multi-select state shared by every media grid. */
class SelectionController {
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    val isActive: Boolean get() = _selected.value.isNotEmpty()

    fun toggle(id: String) = _selected.update { if (id in it) it - id else it + id }

    /** Selects all [ids], or deselects them all if every one is already selected. */
    fun toggleAll(ids: Collection<String>) = _selected.update { current ->
        if (ids.isNotEmpty() && current.containsAll(ids)) current - ids.toSet() else current + ids
    }

    fun select(ids: Collection<String>) = _selected.update { it + ids }

    fun clear() {
        _selected.value = emptySet()
    }

    /** Drops selected ids that no longer exist (deleted elsewhere, moved to vault, filtered out). */
    fun retainOnly(validIds: Set<String>) = _selected.update { current ->
        if (current.all { it in validIds }) current else current.filterTo(HashSet()) { it in validIds }
    }
}

data class TimelineUiState(
    val groups: List<MediaTimelineGroup> = emptyList(),
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    /** The whole library is empty (as opposed to the current filter matching nothing). */
    val libraryIsEmpty: Boolean = false
) {
    val totalCount: Int get() = items.size
}

class TimelineViewModel(
    private val repository: MediaRepository,
    private val localStore: LocalMetadataStore,
    private val grouper: GroupMediaByTimelineUseCase = GroupMediaByTimelineUseCase(),
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    val density: StateFlow<TimelineDensity> = localStore.timelineDensity

    private val _filter = MutableStateFlow(MediaFilter.ALL)
    val filter: StateFlow<MediaFilter> = _filter.asStateFlow()

    private val selection = SelectionController()
    val selectedIds: StateFlow<Set<String>> = selection.selected

    val quickFolders: StateFlow<List<MediaFolder>> = repository.folders
        .map { it.take(QUICK_FOLDER_COUNT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TimelineUiState> = combine(repository.library, _filter, density, ::Triple)
        // A newer library snapshot (e.g. full scan after the fast first page) cancels stale work.
        .mapLatest { (library, filter, density) ->
            val ordered = MediaScope.Library(filter).arrange(library.items)
            TimelineUiState(
                groups = buildGroups(ordered, filter, density),
                items = ordered,
                isLoading = !library.isLoaded,
                libraryIsEmpty = library.isLoaded && library.items.isEmpty()
            )
        }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    /**
     * "All" shows every photo first, then a separate Videos section at the bottom. The two
     * partitions are grouped concurrently on the compute pool.
     */
    private suspend fun buildGroups(
        ordered: List<MediaItem>,
        filter: MediaFilter,
        density: TimelineDensity
    ): List<MediaTimelineGroup> {
        if (filter != MediaFilter.ALL) return grouper(ordered, density)
        val firstVideo = ordered.indexOfFirst { it.isVideo }
        if (firstVideo < 0) return grouper(ordered, density)
        val photos = ordered.subList(0, firstVideo)
        val videos = ordered.subList(firstVideo, ordered.size)
        return coroutineScope {
            val photoGroups = async { grouper(photos, density, keyPrefix = "photo-") }
            val videoGroups = async { grouper(videos, density, keyPrefix = "video-") }
            val videoSection = videoGroups.await().mapIndexed { index, group ->
                if (index == 0) {
                    group.copy(sectionTitle = "Videos", sectionSubtitle = if (videos.size == 1) "1 video" else "${videos.size} videos")
                } else {
                    group
                }
            }
            photoGroups.await() + videoSection
        }
    }

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                if (selection.isActive && !state.isLoading) selection.retainOnly(state.items.mapTo(HashSet()) { it.id })
            }
        }
    }

    fun setDensity(newDensity: TimelineDensity) = localStore.setTimelineDensity(newDensity)

    /** Pinch handling: zooming in shows larger thumbnails, zooming out denser ones. */
    fun stepDensity(zoomIn: Boolean) {
        val order = TimelineDensity.entries
        val index = order.indexOf(density.value) + if (zoomIn) 1 else -1
        setDensity(order[index.coerceIn(0, order.lastIndex)])
    }

    fun setFilter(filter: MediaFilter) {
        _filter.value = filter
        selection.clear()
    }

    fun toggleSelection(id: String) = selection.toggle(id)

    fun toggleGroupSelection(group: MediaTimelineGroup) = selection.toggleAll(group.items.map { it.id })

    fun selectAll() = selection.select(uiState.value.items.map { it.id })

    fun clearSelection() = selection.clear()

    fun selectedItems(): List<MediaItem> {
        val ids = selectedIds.value
        return uiState.value.items.filter { it.id in ids }
    }

    /** Favorites the selection, or unfavorites it when everything selected is already a favorite. */
    fun toggleFavoriteForSelection() {
        val items = selectedItems()
        if (items.isEmpty()) return
        val makeFavorite = !items.all { it.isFavorite }
        viewModelScope.launch { repository.setFavorite(items.map { it.id }, makeFavorite) }
    }

    private companion object {
        const val QUICK_FOLDER_COUNT = 10
    }
}
