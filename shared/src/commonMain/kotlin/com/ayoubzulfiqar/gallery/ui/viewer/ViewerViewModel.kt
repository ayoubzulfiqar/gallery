package com.ayoubzulfiqar.gallery.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayoubzulfiqar.gallery.domain.model.ExifMetadata
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewerViewModel(
    private val repository: MediaRepository,
    private val initialItemId: String
) : ViewModel() {

    private val _isChromeVisible = MutableStateFlow(true)
    val isChromeVisible: StateFlow<Boolean> = _isChromeVisible.asStateFlow()

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentExif = MutableStateFlow<ExifMetadata?>(null)
    val currentExif: StateFlow<ExifMetadata?> = _currentExif.asStateFlow()

    val currentItem: StateFlow<MediaItem?> = MutableStateFlow<MediaItem?>(null)

    init {
        viewModelScope.launch {
            repository.getMediaFlow().collect { list ->
                _items.value = list
                val idx = list.indexOfFirst { it.id == initialItemId }
                if (idx != -1) {
                    _currentIndex.value = idx
                }
                updateExif()
            }
        }
    }

    fun getCurrentItem(): MediaItem? {
        val list = _items.value
        val idx = _currentIndex.value
        return if (idx in list.indices) list[idx] else null
    }

    fun toggleChrome() {
        _isChromeVisible.value = !_isChromeVisible.value
    }

    fun setIndex(newIndex: Int) {
        val list = _items.value
        if (newIndex in list.indices) {
            _currentIndex.value = newIndex
            updateExif()
        }
    }

    fun toggleFavorite() {
        val item = getCurrentItem() ?: return
        viewModelScope.launch {
            repository.toggleFavorite(item.id)
        }
    }

    fun deleteCurrent(onDeleted: () -> Unit) {
        val item = getCurrentItem() ?: return
        viewModelScope.launch {
            repository.deleteMedia(listOf(item.id))
            onDeleted()
        }
    }

    private fun updateExif() {
        val item = getCurrentItem() ?: return
        viewModelScope.launch {
            _currentExif.value = repository.getExif(item.id)
        }
    }
}
