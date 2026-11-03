package com.ayoubzulfiqar.gallery.editor

import com.ayoubzulfiqar.gallery.domain.model.ColorAdjustments
import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditPipelineTest {

    @Test
    fun sliderDragIsASingleUndoStep() {
        val pipeline = EditPipeline()
        for (i in 1..50) pipeline.previewAdjustments(ColorAdjustments(brightness = i / 100f))
        pipeline.commitPreview()
        assertEquals(0.5f, pipeline.currentRecipe.adjustments.brightness)
        pipeline.undo()
        assertEquals(0f, pipeline.currentRecipe.adjustments.brightness, "one undo reverts the whole drag")
        assertFalse(pipeline.canUndo)
        pipeline.redo()
        assertEquals(0.5f, pipeline.currentRecipe.adjustments.brightness)
    }

    @Test
    fun noOpChangesDoNotPolluteHistoryAndHistoryIsCapped() {
        val pipeline = EditPipeline(maxHistory = 5)
        pipeline.setFilterPreset(null)
        pipeline.reset()
        assertFalse(pipeline.canUndo)
        repeat(20) { pipeline.rotateBy90() }
        var undos = 0
        while (pipeline.canUndo) {
            pipeline.undo()
            undos++
        }
        assertEquals(5, undos)
    }

    @Test
    fun rotationNormalizesAndResetIsUndoable() {
        val pipeline = EditPipeline()
        repeat(5) { pipeline.rotateBy90() }
        assertEquals(90f, pipeline.currentRecipe.rotationDegrees)
        pipeline.rotateBy90(clockwise = false)
        pipeline.rotateBy90(clockwise = false)
        assertEquals(270f, pipeline.currentRecipe.rotationDegrees)
        pipeline.toggleFlipVertical()
        assertTrue(pipeline.isModified)
        pipeline.reset()
        assertEquals(EditRecipe(), pipeline.currentRecipe)
        pipeline.undo()
        assertTrue(pipeline.currentRecipe.flipVertical)
    }

    private fun outputAspect(recipe: EditRecipe, w: Int, h: Int): Float {
        val (ow, oh) = EditRenderMath.outputSize(w, h, recipe)
        return ow.toFloat() / oh
    }

    @Test
    fun cropPresetsProduceTheRequestedOutputAspect() {
        val w = 4000
        val h = 3000
        for (target in listOf(1f, 16f / 9f, 9f / 16f, 3f / 4f)) {
            for (rotation in listOf(0f, 90f)) {
                val crop = EditRenderMath.centeredCrop(target, w.toFloat() / h, rotation)
                val recipe = EditRecipe(cropRect = crop, rotationDegrees = rotation)
                val actual = outputAspect(recipe, w, h)
                assertTrue(abs(actual - target) / target < 0.01f, "target=$target rotation=$rotation actual=$actual")
            }
        }
        assertNull(EditRenderMath.centeredCrop(4f / 3f, 4f / 3f, 0f), "already that shape: no crop")
    }

    @Test
    fun cropStaysInsideImageBounds() {
        val crop = assertNotNull(EditRenderMath.centeredCrop(1f, 0.33333334f, 0f))
        assertTrue(crop.left >= 0f && crop.right <= 1f && crop.top >= 0f && crop.bottom <= 1f)
    }

    @Test
    fun colorMatrixIsNullForUneditedImages() {
        assertNull(EditRenderMath.colorMatrix(EditRecipe()))
        val identity = ColorMatrixHelper.buildColorMatrix(ColorAdjustments())
        assertTrue(identity.contentEquals(ColorMatrixHelper.identityMatrix()))
        assertNotNull(EditRenderMath.colorMatrix(EditRecipe(filterPresetId = "VIVID")))
        assertNotNull(EditRenderMath.colorMatrix(EditRecipe(adjustments = ColorAdjustments(saturation = 0f))))
        assertEquals(0f, EditRenderMath.vignetteEdgeAlpha(EditRecipe()))
    }
}
