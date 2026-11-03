package com.ayoubzulfiqar.gallery.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaTimelineGroup
import com.ayoubzulfiqar.gallery.domain.repository.MediaRepository
import com.ayoubzulfiqar.gallery.ui.gesture.TimelineDensity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimelineUiState(
    val groups: List<MediaTimelineGroup> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = true
)

class TimelineViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _density = MutableStateFlow(TimelineDensity.DAYS)
    val density: StateFlow<TimelineDensity> = _density.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    val uiState: StateFlow<TimelineUiState> = repository.getMediaFlow()
        .combine(MutableStateFlow(false)) { items, _ ->
            val groups = repository.getTimelineGroups()
            TimelineUiState(
                groups = groups,
                totalCount = items.size,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TimelineUiState(isLoading = true)
        )

    fun setDensity(newDensity: TimelineDensity) {
        _density.value = newDensity
    }

    fun toggleSelection(id: String) {
        val current = _selectedIds.value
        if (id in current) {
            val updated = current - id
            _selectedIds.value = updated
            if (updated.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            _selectedIds.value = current + id
            _isSelectionMode.value = true
        }
    }

    fun selectRange(ids: Collection<String>) {
        _selectedIds.value = _selectedIds.value + ids
        if (_selectedIds.value.isNotEmpty()) {
            _isSelectionMode.value = true
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            repository.toggleFavorite(id)
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val toDelete = _selectedIds.value.toList()
            repository.deleteMedia(toDelete)
            clearSelection()
        }
    }
}
