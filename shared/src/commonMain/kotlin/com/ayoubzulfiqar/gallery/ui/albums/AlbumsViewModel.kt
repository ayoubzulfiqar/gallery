package com.ayoubzulfiqar.gallery.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayoubzulfiqar.gallery.domain.model.MediaFilter
import com.ayoubzulfiqar.gallery.domain.model.MediaFolder
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaTimelineGroup
import com.ayoubzulfiqar.gallery.domain.model.TimelineDensity
import com.ayoubzulfiqar.gallery.domain.repository.MediaRepository
import com.ayoubzulfiqar.gallery.domain.usecase.GroupMediaByTimelineUseCase
import com.ayoubzulfiqar.gallery.ui.timeline.SelectionController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlbumsUiState(
    val folders: List<MediaFolder> = emptyList(),
    val totalFolderCount: Int = 0,
    val totalPhotosCount: Int = 0,
    val totalVideosCount: Int = 0,
    val isLoading: Boolean = true
)

class AlbumsViewModel(
    repository: MediaRepository,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<AlbumsUiState> = combine(repository.library, repository.folders, _searchQuery) { library, folders, query ->
        val trimmed = query.trim()
        AlbumsUiState(
            folders = if (trimmed.isEmpty()) folders else folders.filter { it.name.contains(trimmed, ignoreCase = true) },
            totalFolderCount = folders.size,
            totalPhotosCount = library.items.count { !it.isVideo },
            totalVideosCount = library.items.count { it.isVideo },
            isLoading = !library.isLoaded
        )
    }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumsUiState())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query.take(100)
    }
}

data class FolderDetailUiState(
    val folderName: String,
    val groups: List<MediaTimelineGroup> = emptyList(),
    val items: List<MediaItem> = emptyList(),
    val folderItemCount: Int = 0,
    val isLoading: Boolean = true,
    /** The folder no longer exists (all items deleted or moved elsewhere). */
    val isGone: Boolean = false
)

class FolderDetailViewModel(
    private val repository: MediaRepository,
    val folderId: String,
    initialName: String,
    private val grouper: GroupMediaByTimelineUseCase = GroupMediaByTimelineUseCase(),
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _filter = MutableStateFlow(MediaFilter.ALL)
    val filter: StateFlow<MediaFilter> = _filter.asStateFlow()

    private val selection = SelectionController()
    val selectedIds: StateFlow<Set<String>> = selection.selected

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FolderDetailUiState> = combine(repository.library, _filter, ::Pair).mapLatest { (library, filter) ->
        val folderItems = library.items.filter { it.folderKey == folderId }
        val visible = folderItems.filter(filter::matches)
        FolderDetailUiState(
            folderName = folderItems.firstNotNullOfOrNull { it.bucketName } ?: initialName,
            groups = grouper(visible, TimelineDensity.MONTHS),
            items = visible,
            folderItemCount = folderItems.size,
            isLoading = !library.isLoaded,
            isGone = library.isLoaded && folderItems.isEmpty()
        )
    }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderDetailUiState(initialName))

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                if (selection.isActive && !state.isLoading) selection.retainOnly(state.items.mapTo(HashSet()) { it.id })
            }
        }
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

    fun toggleFavoriteForSelection() {
        val items = selectedItems()
        if (items.isEmpty()) return
        val makeFavorite = !items.all { it.isFavorite }
        viewModelScope.launch { repository.setFavorite(items.map { it.id }, makeFavorite) }
    }
}
