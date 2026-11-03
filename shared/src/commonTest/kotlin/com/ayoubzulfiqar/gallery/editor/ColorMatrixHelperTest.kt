package com.ayoubzulfiqar.gallery.editor

import com.ayoubzulfiqar.gallery.domain.model.ColorAdjustments
import com.ayoubzulfiqar.gallery.domain.model.CropRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorMatrixHelperTest {
    @Test
    fun testIdentityMatrixWhenAdjustmentsAreDefault() {
        val matrix = ColorMatrixHelper.buildColorMatrix(ColorAdjustments())
        assertEquals(20, matrix.size)
        // Red diagonal 1, Green diagonal 1, Blue diagonal 1, Alpha diagonal 1
        assertEquals(1f, matrix[0])
        assertEquals(1f, matrix[6])
        assertEquals(1f, matrix[12])
        assertEquals(1f, matrix[18])
    }

    @Test
    fun testEditPipelineUndoRedo() {
        val pipeline = EditPipeline()
        assertEquals(0f, pipeline.currentRecipe.rotationDegrees)

        pipeline.rotateBy90()
        assertEquals(90f, pipeline.currentRecipe.rotationDegrees)
        assertTrue(pipeline.canUndo)

        pipeline.undo()
        assertEquals(0f, pipeline.currentRecipe.rotationDegrees)
        assertTrue(pipeline.canRedo)

        pipeline.redo()
        assertEquals(90f, pipeline.currentRecipe.rotationDegrees)
    }
}
