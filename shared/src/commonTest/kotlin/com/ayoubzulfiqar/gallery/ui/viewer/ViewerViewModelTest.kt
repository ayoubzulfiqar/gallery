package com.ayoubzulfiqar.gallery.ui.viewer

import com.ayoubzulfiqar.gallery.data.datasource.FakePlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.data.repository.DefaultMediaRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewerViewModelTest {
    @Test
    fun testChromeVisibilityToggleAndNavigation() = runTest {
        val platformSource = FakePlatformMediaDataSource()
        platformSource.populateWithSyntheticMedia(count = 5)
        val repo = DefaultMediaRepository(platformSource, LocalMetadataStore())
        val viewModel = ViewerViewModel(repo, initialItemId = "media_2")

        assertTrue(viewModel.isChromeVisible.value)
        viewModel.toggleChrome()
        assertFalse(viewModel.isChromeVisible.value)
    }
}
