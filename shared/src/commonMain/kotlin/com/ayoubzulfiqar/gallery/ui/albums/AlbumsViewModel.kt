package com.ayoubzulfiqar.gallery.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayoubzulfiqar.gallery.domain.model.MediaFolder
import com.ayoubzulfiqar.gallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AlbumsUiState(
    val folders: List<MediaFolder> = emptyList(),
    val totalPhotosCount: Int = 0,
    val totalVideosCount: Int = 0,
    val isLoading: Boolean = true
)

class AlbumsViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    val allFolders: StateFlow<List<MediaFolder>> = repository.getFoldersFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<AlbumsUiState> = combine(
        allFolders,
        _searchQuery
    ) { folders, query ->
        val filtered = if (query.isBlank()) {
            folders
        } else {
            folders.filter { it.name.contains(query, ignoreCase = true) }
        }
        val totalPhotos = folders.sumOf { f -> f.items.count { it.mediaType.name == "IMAGE" } }
        val totalVideos = folders.sumOf { f -> f.items.count { it.mediaType.name == "VIDEO" } }

        AlbumsUiState(
            folders = filtered,
            totalPhotosCount = totalPhotos,
            totalVideosCount = totalVideos,
            isLoading = folders.isEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AlbumsUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
}
