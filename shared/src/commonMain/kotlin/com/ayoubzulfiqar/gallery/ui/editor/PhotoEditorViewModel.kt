package com.ayoubzulfiqar.gallery.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayoubzulfiqar.gallery.data.datasource.ImportResult
import com.ayoubzulfiqar.gallery.di.EditedImageExporter
import com.ayoubzulfiqar.gallery.domain.model.ColorAdjustments
import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.editor.EditPipeline
import com.ayoubzulfiqar.gallery.editor.EditRenderMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EditorTab {
    FILTERS,
    ADJUST,
    CROP,
    ROTATE
}

enum class AdjustmentParam(val label: String, val range: ClosedFloatingPointRange<Float>, val neutral: Float) {
    BRIGHTNESS("Brightness", -1f..1f, 0f),
    CONTRAST("Contrast", 0f..2f, 1f),
    SATURATION("Saturation", 0f..2f, 1f),
    WARMTH("Warmth", -1f..1f, 0f),
    VIGNETTE("Vignette", 0f..1f, 0f);

    fun read(adjustments: ColorAdjustments): Float = when (this) {
        BRIGHTNESS -> adjustments.brightness
        CONTRAST -> adjustments.contrast
        SATURATION -> adjustments.saturation
        WARMTH -> adjustments.warmth
        VIGNETTE -> adjustments.vignette
    }

    fun write(adjustments: ColorAdjustments, value: Float): ColorAdjustments {
        val v = value.coerceIn(range)
        return when (this) {
            BRIGHTNESS -> adjustments.copy(brightness = v)
            CONTRAST -> adjustments.copy(contrast = v)
            SATURATION -> adjustments.copy(saturation = v)
            WARMTH -> adjustments.copy(warmth = v)
            VIGNETTE -> adjustments.copy(vignette = v)
        }
    }
}

enum class CropPreset(val label: String, val aspect: Float?) {
    ORIGINAL("Original", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_FOUR("3:4", 3f / 4f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    NINE_SIXTEEN("9:16", 9f / 16f)
}

data class EditorUiState(
    val recipe: EditRecipe = EditRecipe(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isModified: Boolean = false,
    val activeTab: EditorTab = EditorTab.ADJUST,
    val activeParam: AdjustmentParam = AdjustmentParam.BRIGHTNESS,
    val activeCrop: CropPreset = CropPreset.ORIGINAL,
    val isSaving: Boolean = false,
    val canSave: Boolean = true
)

sealed interface EditorEvent {
    data class Saved(val message: String) : EditorEvent
    data class Error(val message: String) : EditorEvent
}

class PhotoEditorViewModel(
    val mediaItem: MediaItem,
    private val exporter: EditedImageExporter?,
    initialRecipe: EditRecipe = EditRecipe()
) : ViewModel() {

    private val pipeline = EditPipeline(initialRecipe)

    private val _uiState = MutableStateFlow(EditorUiState(recipe = initialRecipe, canSave = exporter != null))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    fun setTab(tab: EditorTab) = _uiState.update { it.copy(activeTab = tab) }

    fun selectParam(param: AdjustmentParam) {
        pipeline.commitPreview()
        _uiState.update { it.copy(activeParam = param) }
        sync()
    }

    /** Live slider movement: previewed, not yet an undo step. */
    fun previewAdjustment(param: AdjustmentParam, value: Float) {
        pipeline.previewAdjustments(param.write(pipeline.currentRecipe.adjustments, value))
        sync()
    }

    /** Slider released: the whole drag becomes one undo step. */
    fun commitAdjustment() {
        pipeline.commitPreview()
        sync()
    }

    fun resetAdjustment(param: AdjustmentParam) {
        pipeline.updateAdjustments(param.write(pipeline.currentRecipe.adjustments, param.neutral))
        sync()
    }

    fun applyFilter(filterId: String?) {
        pipeline.setFilterPreset(filterId)
        sync()
    }

    fun rotate(clockwise: Boolean = true) {
        // Keep the chosen output aspect when the picture turns sideways.
        pipeline.rotateBy90(clockwise)
        reapplyCrop()
        sync()
    }

    fun toggleFlipHorizontal() {
        pipeline.toggleFlipHorizontal()
        sync()
    }

    fun toggleFlipVertical() {
        pipeline.toggleFlipVertical()
        sync()
    }

    fun setCropPreset(preset: CropPreset) {
        _uiState.update { it.copy(activeCrop = preset) }
        pipeline.setCrop(cropFor(preset))
        sync()
    }

    fun undo() {
        pipeline.undo()
        sync()
    }

    fun redo() {
        pipeline.redo()
        sync()
    }

    fun reset() {
        pipeline.reset()
        _uiState.update { it.copy(activeCrop = CropPreset.ORIGINAL) }
        sync()
    }

    fun save() {
        val target = exporter
        if (target == null) {
            _events.trySend(EditorEvent.Error("Saving edits isn't supported on this device yet"))
            return
        }
        if (_uiState.value.isSaving) return
        pipeline.commitPreview()
        val recipe = pipeline.currentRecipe
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = try {
                target.export(mediaItem, recipe)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportResult.Failure(e.message ?: "Could not save the edited photo", e)
            }
            _uiState.update { it.copy(isSaving = false) }
            _events.send(
                when (result) {
                    is ImportResult.Success -> EditorEvent.Saved("Saved a copy to your gallery")
                    is ImportResult.Failure -> EditorEvent.Error(result.message)
                }
            )
        }
    }

    private fun reapplyCrop() {
        val preset = _uiState.value.activeCrop
        if (preset.aspect != null) pipeline.setCrop(cropFor(preset))
    }

    private fun cropFor(preset: CropPreset) = preset.aspect?.let { aspect ->
        val imageAspect = if (mediaItem.displayWidth > 0 && mediaItem.displayHeight > 0) {
            mediaItem.displayWidth.toFloat() / mediaItem.displayHeight
        } else {
            1f
        }
        EditRenderMath.centeredCrop(aspect, imageAspect, pipeline.currentRecipe.rotationDegrees)
    }

    private fun sync() {
        _uiState.update {
            it.copy(
                recipe = pipeline.currentRecipe,
                canUndo = pipeline.canUndo,
                canRedo = pipeline.canRedo,
                isModified = pipeline.isModified
            )
        }
    }
}
