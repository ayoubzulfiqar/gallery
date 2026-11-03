package com.ayoubzulfiqar.gallery.domain

import com.ayoubzulfiqar.gallery.domain.model.ColorAdjustments
import com.ayoubzulfiqar.gallery.domain.model.CropRect
import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaModelsTest {
    @Test
    fun testMediaItemCreationAndDefaults() {
        val item = MediaItem(
            id = "media_1",
            uri = "content://media/1",
            displayName = "photo.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE,
            dateTakenMillis = 1700000000000L,
            dateModifiedMillis = 1700000000000L,
            width = 4000,
            height = 3000,
            sizeBytes = 2048000L
        )
        assertEquals("media_1", item.id)
        assertFalse(item.isFavorite)
        assertFalse(item.isVaulted)
        assertEquals(0, item.orientation)
    }

    @Test
    fun testEditRecipeClampingAndDefaults() {
        val adjustments = ColorAdjustments(brightness = 0.5f, contrast = 1.2f)
        val recipe = EditRecipe(
            cropRect = CropRect(0.1f, 0.1f, 0.9f, 0.9f),
            adjustments = adjustments,
            rotationDegrees = 90f
        )
        assertEquals(90f, recipe.rotationDegrees)
        assertEquals(0.5f, recipe.adjustments.brightness)
        assertEquals(1.2f, recipe.adjustments.contrast)
    }
}
