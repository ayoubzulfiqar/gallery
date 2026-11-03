package com.ayoubzulfiqar.gallery.ui.editor

import androidx.lifecycle.ViewModel
import com.ayoubzulfiqar.gallery.domain.model.CropRect
import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.editor.EditPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EditorTab {
    FILTERS,
    ADJUST,
    CROP,
    ROTATE
}

class PhotoEditorViewModel(
    val mediaItem: MediaItem,
    initialRecipe: EditRecipe = EditRecipe()
) : ViewModel() {

    private val pipeline = EditPipeline(initialRecipe)

    private val _currentRecipe = MutableStateFlow(pipeline.currentRecipe)
    val currentRecipe: StateFlow<EditRecipe> = _currentRecipe.asStateFlow()

    private val _canUndo = MutableStateFlow(pipeline.canUndo)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(pipeline.canRedo)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _activeTab = MutableStateFlow(EditorTab.ADJUST)
    val activeTab: StateFlow<EditorTab> = _activeTab.asStateFlow()

    fun setTab(tab: EditorTab) {
        _activeTab.value = tab
    }

    fun updateBrightness(value: Float) {
        val currentAdj = _currentRecipe.value.adjustments
        pipeline.updateAdjustments(currentAdj.copy(brightness = value))
        syncState()
    }

    fun updateContrast(value: Float) {
        val currentAdj = _currentRecipe.value.adjustments
        pipeline.updateAdjustments(currentAdj.copy(contrast = value))
        syncState()
    }

    fun updateSaturation(value: Float) {
        val currentAdj = _currentRecipe.value.adjustments
        pipeline.updateAdjustments(currentAdj.copy(saturation = value))
        syncState()
    }

    fun updateWarmth(value: Float) {
        val currentAdj = _currentRecipe.value.adjustments
        pipeline.updateAdjustments(currentAdj.copy(warmth = value))
        syncState()
    }

    fun updateVignette(value: Float) {
        val currentAdj = _currentRecipe.value.adjustments
        pipeline.updateAdjustments(currentAdj.copy(vignette = value))
        syncState()
    }

    fun applyFilter(filterId: String?) {
        pipeline.setFilterPreset(filterId)
        syncState()
    }

    fun rotate90() {
        pipeline.rotateBy90()
        syncState()
    }

    fun toggleFlipHorizontal() {
        pipeline.toggleFlipHorizontal()
        syncState()
    }

    fun setCropPreset(aspectRatio: Float?) {
        if (aspectRatio == null) {
            pipeline.setCrop(null)
        } else {
            // Normalized center-crop for aspect ratio
            val crop = if (aspectRatio >= 1.0f) {
                val heightFraction = (1.0f / aspectRatio).coerceIn(0.1f, 1.0f)
                val top = (1.0f - heightFraction) / 2f
                CropRect(0f, top, 1f, top + heightFraction)
            } else {
                val widthFraction = aspectRatio.coerceIn(0.1f, 1.0f)
                val left = (1.0f - widthFraction) / 2f
                CropRect(left, 0f, left + widthFraction, 1f)
            }
            pipeline.setCrop(crop)
        }
        syncState()
    }

    fun undo() {
        pipeline.undo()
        syncState()
    }

    fun redo() {
        pipeline.redo()
        syncState()
    }

    fun reset() {
        pipeline.reset()
        syncState()
    }

    private fun syncState() {
        _currentRecipe.value = pipeline.currentRecipe
        _canUndo.value = pipeline.canUndo
        _canRedo.value = pipeline.canRedo
    }
}
