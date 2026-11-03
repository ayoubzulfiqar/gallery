package com.ayoubzulfiqar.gallery.editor

import com.ayoubzulfiqar.gallery.domain.model.ColorAdjustments
import com.ayoubzulfiqar.gallery.domain.model.CropRect
import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Non-destructive edit history.
 *
 * Continuous gestures (slider drags) are previewed with [previewAdjustments] and folded into a
 * single undo step by [commitPreview], so one drag is one undo instead of hundreds.
 */
class EditPipeline(
    private val initialRecipe: EditRecipe = EditRecipe(),
    private val maxHistory: Int = 100
) {
    private val undoStack = ArrayDeque<EditRecipe>()
    private val redoStack = ArrayDeque<EditRecipe>()
    private var gestureBase: EditRecipe? = null

    var currentRecipe: EditRecipe = initialRecipe
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty() || (gestureBase != null && gestureBase != currentRecipe)
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isModified: Boolean get() = currentRecipe != initialRecipe

    fun previewAdjustments(adjustments: ColorAdjustments) {
        if (gestureBase == null) gestureBase = currentRecipe
        currentRecipe = currentRecipe.copy(adjustments = adjustments)
    }

    fun commitPreview() {
        val base = gestureBase ?: return
        gestureBase = null
        if (base != currentRecipe) record(base)
    }

    fun updateAdjustments(adjustments: ColorAdjustments) {
        commitPreview()
        pushState(currentRecipe.copy(adjustments = adjustments))
    }

    fun setCrop(cropRect: CropRect?) {
        commitPreview()
        pushState(currentRecipe.copy(cropRect = cropRect))
    }

    fun rotateBy90(clockwise: Boolean = true) {
        commitPreview()
        val delta = if (clockwise) 90f else -90f
        pushState(currentRecipe.copy(rotationDegrees = normalizeDegrees(currentRecipe.rotationDegrees + delta)))
    }

    fun toggleFlipHorizontal() {
        commitPreview()
        pushState(currentRecipe.copy(flipHorizontal = !currentRecipe.flipHorizontal))
    }

    fun toggleFlipVertical() {
        commitPreview()
        pushState(currentRecipe.copy(flipVertical = !currentRecipe.flipVertical))
    }

    fun setFilterPreset(filterId: String?) {
        commitPreview()
        pushState(currentRecipe.copy(filterPresetId = filterId))
    }

    fun undo() {
        commitPreview()
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(currentRecipe)
        currentRecipe = previous
    }

    fun redo() {
        commitPreview()
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(currentRecipe)
        currentRecipe = next
    }

    /** Back to the unedited image; undoable. */
    fun reset() {
        commitPreview()
        pushState(EditRecipe())
    }

    private fun pushState(newRecipe: EditRecipe) {
        if (newRecipe == currentRecipe) return
        record(currentRecipe)
        currentRecipe = newRecipe
    }

    private fun record(previous: EditRecipe) {
        undoStack.addLast(previous)
        while (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
    }

    companion object {
        fun normalizeDegrees(degrees: Float): Float {
            val snapped = (degrees / 90f).roundToInt() * 90
            return (((snapped % 360) + 360) % 360).toFloat()
        }
    }
}

/** Geometry and colour math shared by the live preview and the full-resolution export. */
object EditRenderMath {

    fun isSideways(rotationDegrees: Float): Boolean =
        EditPipeline.normalizeDegrees(rotationDegrees).toInt() % 180 == 90

    /**
     * Normalized, centred crop (in un-rotated image space) whose *output* — after the recipe's
     * rotation — has [targetAspect] (width / height).
     */
    fun centeredCrop(targetAspect: Float, imageAspect: Float, rotationDegrees: Float): CropRect? {
        if (targetAspect <= 0f || imageAspect <= 0f) return null
        val sourceAspect = if (isSideways(rotationDegrees)) 1f / targetAspect else targetAspect
        if (abs(sourceAspect - imageAspect) / imageAspect < 0.005f) return null // already that shape
        return if (imageAspect > sourceAspect) {
            val widthFraction = (sourceAspect / imageAspect).coerceIn(0.01f, 1f)
            val left = ((1f - widthFraction) / 2f).coerceIn(0f, 1f)
            CropRect(left, 0f, (left + widthFraction).coerceAtMost(1f), 1f)
        } else {
            val heightFraction = (imageAspect / sourceAspect).coerceIn(0.01f, 1f)
            val top = ((1f - heightFraction) / 2f).coerceIn(0f, 1f)
            CropRect(0f, top, 1f, (top + heightFraction).coerceAtMost(1f))
        }
    }

    /** Pixel size of the edited output for a source of [width] x [height]. */
    fun outputSize(width: Int, height: Int, recipe: EditRecipe): Pair<Int, Int> {
        val crop = recipe.cropRect
        val w = if (crop == null) width else ((crop.right - crop.left) * width).roundToInt().coerceAtLeast(1)
        val h = if (crop == null) height else ((crop.bottom - crop.top) * height).roundToInt().coerceAtLeast(1)
        return if (isSideways(recipe.rotationDegrees)) h to w else w to h
    }

    /** Combined 4x5 colour matrix (filter first, then manual adjustments), or null for identity. */
    fun colorMatrix(recipe: EditRecipe): FloatArray? {
        val adjustments = ColorMatrixHelper.buildColorMatrix(recipe.adjustments)
        val filter = recipe.filterPresetId?.let { FilterPresets.getById(it) }?.colorMatrix
        val combined = if (filter != null) ColorMatrixHelper.multiply(adjustments, filter) else adjustments
        return if (combined.contentEquals(ColorMatrixHelper.identityMatrix())) null else combined
    }

    /** Edge darkening alpha at the image corners for the vignette amount. */
    fun vignetteEdgeAlpha(recipe: EditRecipe): Float = (recipe.adjustments.vignette.coerceIn(0f, 1f) * 0.75f)
}
