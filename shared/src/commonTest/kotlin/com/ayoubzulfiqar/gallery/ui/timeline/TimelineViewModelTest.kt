package com.ayoubzulfiqar.gallery.ui.timeline

import com.ayoubzulfiqar.gallery.data.datasource.FakePlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.data.repository.DefaultMediaRepository
import com.ayoubzulfiqar.gallery.ui.gesture.TimelineDensity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelineViewModelTest {
    @Test
    fun testSelectionAndDensityChange() = runTest {
        val platformSource = FakePlatformMediaDataSource()
        platformSource.populateWithSyntheticMedia(count = 10)
        val repo = DefaultMediaRepository(platformSource, LocalMetadataStore())
        val viewModel = TimelineViewModel(repo)

        viewModel.setDensity(TimelineDensity.MONTHS)
        assertEquals(TimelineDensity.MONTHS, viewModel.density.value)

        viewModel.toggleSelection("item_1")
        assertTrue(viewModel.selectedIds.value.contains("item_1"))
        assertTrue(viewModel.isSelectionMode.value)

        viewModel.clearSelection()
        assertFalse(viewModel.isSelectionMode.value)
    }
}
