package com.ayoubzulfiqar.gallery.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayoubzulfiqar.gallery.domain.model.MediaDetails
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaScope
import com.ayoubzulfiqar.gallery.domain.repository.MediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewerUiState(
    val items: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = true,
    /** The scope became empty (everything deleted/moved): the screen should close. */
    val isEmpty: Boolean = false
) {
    val currentItem: MediaItem? get() = items.getOrNull(currentIndex)
}

sealed interface DetailsState {
    data object Hidden : DetailsState
    data object Loading : DetailsState
    data class Loaded(val details: MediaDetails) : DetailsState
    data class Failed(val message: String) : DetailsState
}

class ViewerViewModel(
    private val repository: MediaRepository,
    initialItemId: String,
    private val scope: MediaScope,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _isChromeVisible = MutableStateFlow(true)
    val isChromeVisible: StateFlow<Boolean> = _isChromeVisible.asStateFlow()

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val _details = MutableStateFlow<DetailsState>(DetailsState.Hidden)
    val details: StateFlow<DetailsState> = _details.asStateFlow()

    /** Item the user is looking at; survives list changes so the pager never jumps back. */
    private var anchorId: String = initialItemId
    private var detailsJob: Job? = null

    init {
        viewModelScope.launch {
            repository.library
                .filter { it.isLoaded }
                // Scope filtering/ordering of large libraries runs off the main thread.
                .map { library -> scope.arrange(library.items) }
                .flowOn(computeDispatcher)
                .collect { items -> applyItems(items) }
        }
    }

    private fun applyItems(items: List<MediaItem>) {
        val previous = _uiState.value
        val index = resolveIndex(items, previous)
        items.getOrNull(index)?.let { anchorId = it.id }
        _uiState.value = ViewerUiState(
            items = items,
            currentIndex = index,
            isLoading = false,
            isEmpty = items.isEmpty()
        )
        refreshDetailsIfVisible()
    }

    private fun resolveIndex(items: List<MediaItem>, previous: ViewerUiState): Int {
        if (items.isEmpty()) return 0
        val anchored = items.indexOfFirst { it.id == anchorId }
        if (anchored >= 0) return anchored
        // The current item vanished (deleted/moved): show the item that took its place.
        return if (previous.items.isEmpty()) 0 else previous.currentIndex.coerceIn(0, items.lastIndex)
    }

    fun currentItem(): MediaItem? = _uiState.value.currentItem

    fun onPageChanged(index: Int) {
        val state = _uiState.value
        val item = state.items.getOrNull(index) ?: return
        if (index == state.currentIndex && item.id == anchorId) return
        anchorId = item.id
        _uiState.value = state.copy(currentIndex = index)
        refreshDetailsIfVisible()
    }

    fun toggleChrome() = _isChromeVisible.update { !it }

    fun setChromeVisible(visible: Boolean) {
        _isChromeVisible.value = visible
    }

    fun toggleFavorite() {
        val item = currentItem() ?: return
        viewModelScope.launch { repository.toggleFavorite(item.id) }
    }

    fun showDetails() {
        _details.value = DetailsState.Loading
        loadDetails()
    }

    fun hideDetails() {
        detailsJob?.cancel()
        _details.value = DetailsState.Hidden
    }

    private fun refreshDetailsIfVisible() {
        if (_details.value != DetailsState.Hidden) loadDetails()
    }

    private fun loadDetails() {
        val item = currentItem() ?: return
        detailsJob?.cancel() // never let a slow lookup for a previous page overwrite the current one
        detailsJob = viewModelScope.launch {
            _details.value = try {
                DetailsState.Loaded(repository.loadDetails(item))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DetailsState.Failed(e.message ?: "Details unavailable")
            }
        }
    }
}
