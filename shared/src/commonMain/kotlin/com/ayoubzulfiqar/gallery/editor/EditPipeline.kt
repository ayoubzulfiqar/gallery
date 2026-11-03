package com.ayoubzulfiqar.gallery.editor

import com.ayoubzulfiqar.gallery.domain.model.ColorAdjustments
import com.ayoubzulfiqar.gallery.domain.model.CropRect
import com.ayoubzulfiqar.gallery.domain.model.EditRecipe

class EditPipeline(initialRecipe: EditRecipe = EditRecipe()) {
    private val undoStack = mutableListOf<EditRecipe>()
    private val redoStack = mutableListOf<EditRecipe>()

    var currentRecipe: EditRecipe = initialRecipe
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun updateAdjustments(adjustments: ColorAdjustments) {
        pushState(currentRecipe.copy(adjustments = adjustments))
    }

    fun setCrop(cropRect: CropRect?) {
        pushState(currentRecipe.copy(cropRect = cropRect))
    }

    fun rotateBy90() {
        val newRotation = (currentRecipe.rotationDegrees + 90f) % 360f
        pushState(currentRecipe.copy(rotationDegrees = newRotation))
    }

    fun toggleFlipHorizontal() {
        pushState(currentRecipe.copy(flipHorizontal = !currentRecipe.flipHorizontal))
    }

    fun toggleFlipVertical() {
        pushState(currentRecipe.copy(flipVertical = !currentRecipe.flipVertical))
    }

    fun setFilterPreset(filterId: String?) {
        pushState(currentRecipe.copy(filterPresetId = filterId))
    }

    fun undo() {
        if (!canUndo) return
        redoStack.add(currentRecipe)
        currentRecipe = undoStack.removeAt(undoStack.lastIndex)
    }

    fun redo() {
        if (!canRedo) return
        undoStack.add(currentRecipe)
        currentRecipe = redoStack.removeAt(redoStack.lastIndex)
    }

    fun reset() {
        pushState(EditRecipe())
    }

    private fun pushState(newRecipe: EditRecipe) {
        undoStack.add(currentRecipe)
        redoStack.clear()
        currentRecipe = newRecipe
    }
}
