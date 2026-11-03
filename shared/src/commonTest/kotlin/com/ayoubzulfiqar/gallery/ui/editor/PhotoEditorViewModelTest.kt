package com.ayoubzulfiqar.gallery.ui.editor

import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoEditorViewModelTest {
    @Test
    fun testEditorAdjustmentUpdateAndFilterApplication() {
        val sampleItem = MediaItem(
            id = "test_img",
            uri = "file://test.jpg",
            displayName = "test.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE,
            dateTakenMillis = 1000L,
            dateModifiedMillis = 1000L,
            width = 1920,
            height = 1080,
            sizeBytes = 1024L
        )
        val viewModel = PhotoEditorViewModel(sampleItem)
        viewModel.updateBrightness(0.3f)
        assertEquals(0.3f, viewModel.currentRecipe.value.adjustments.brightness)

        viewModel.applyFilter("VIVID")
        assertEquals("VIVID", viewModel.currentRecipe.value.filterPresetId)
        assertTrue(viewModel.canUndo.value)
    }
}
