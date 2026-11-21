package com.ayoubzulfiqar.gallery.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayoubzulfiqar.gallery.data.datasource.DeleteMode
import com.ayoubzulfiqar.gallery.di.UserMessenger
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaTimelineGroup
import com.ayoubzulfiqar.gallery.domain.model.TimelineDensity
import com.ayoubzulfiqar.gallery.domain.repository.MediaRepository
import com.ayoubzulfiqar.gallery.domain.usecase.GroupMediaByTimelineUseCase
import com.ayoubzulfiqar.gallery.ui.timeline.SelectionController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrashUiState(
    val items: List<MediaItem> = emptyList(),
    val groups: List<MediaTimelineGroup> = emptyList(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false
) {
    /** Soonest system expiry among trashed items, when the platform reports one. */
    val nextExpiryMillis: Long? get() = items.mapNotNull { it.trashedExpiresMillis }.minOrNull()
}

/**
 * The system trash, browsable and reversible. Restoring puts items back in the library;
 * deleting here is permanent (the items are already out of the library).
 */
class TrashViewModel(
    private val repository: MediaRepository,
    private val messenger: UserMessenger,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val grouper: GroupMediaByTimelineUseCase = GroupMediaByTimelineUseCase()
) : ViewModel() {

    private val selection = SelectionController()
    val selectedIds: StateFlow<Set<String>> = selection.selected

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TrashUiState> = repository.trashed
        .mapLatest { items ->
            TrashUiState(
                items = items,
                groups = grouper(items, TimelineDensity.MONTHS),
                isLoading = false,
                isEmpty = items.isEmpty()
            )
        }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                if (selection.isActive && !state.isLoading) selection.retainOnly(state.items.mapTo(HashSet()) { it.id })
            }
        }
    }

    fun toggleSelection(id: String) = selection.toggle(id)
    fun toggleGroupSelection(group: MediaTimelineGroup) = selection.toggleAll(group.items.map { it.id })
    fun selectAll() = selection.select(uiState.value.items.map { it.id })
    fun clearSelection() = selection.clear()

    fun selectedItems(): List<MediaItem> {
        val ids = selectedIds.value
        return uiState.value.items.filter { it.id in ids }
    }

    fun restoreSelected() {
        val items = selectedItems()
        if (items.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            val result = repository.restoreFromTrash(items)
            when {
                result.cancelled -> Unit
                result.restoredIds.isNotEmpty() -> messenger.show(restoredMessage(result.restoredIds.size))
                else -> messenger.show(result.errorMessage ?: "Couldn't restore the items")
            }
        }
    }

    fun deleteSelected() {
        val items = selectedItems()
        if (items.isEmpty()) return
        clearSelection()
        viewModelScope.launch { deletePermanently(items) }
    }

    fun emptyTrash() {
        val items = uiState.value.items
        if (items.isEmpty()) return
        clearSelection()
        viewModelScope.launch { deletePermanently(items) }
    }

    private suspend fun deletePermanently(items: List<MediaItem>) {
        val result = repository.deleteMedia(items, DeleteMode.PERMANENT)
        when {
            result.cancelled -> Unit
            result.deletedIds.size == items.size ->
                messenger.show(if (items.size == 1) "Permanently deleted 1 item" else "Permanently deleted ${items.size} items")
            result.deletedIds.isNotEmpty() ->
                messenger.show("Deleted ${result.deletedIds.size} of ${items.size}. ${result.errorMessage ?: ""}".trim())
            else -> messenger.show(result.errorMessage ?: "Couldn't delete the items")
        }
    }

    private fun restoredMessage(count: Int): String =
        if (count == 1) "Restored 1 item to your gallery" else "Restored $count items to your gallery"
}