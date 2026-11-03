package com.ayoubzulfiqar.gallery.data

import com.ayoubzulfiqar.gallery.data.datasource.FakePlatformMediaDataSource
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakePlatformMediaDataSourceTest {
    @Test
    fun testWindowedPaginationAndDeletion() = runTest {
        val fakeSource = FakePlatformMediaDataSource()
        fakeSource.populateWithSyntheticMedia(count = 250)

        val window = fakeSource.queryMediaWindow(offset = 0, limit = 50)
        assertEquals(50, window.size)
        assertEquals(MediaType.IMAGE, window.first().mediaType)

        val observed = fakeSource.observeMedia().first()
        assertEquals(250, observed.size)

        val deleted = fakeSource.deleteMedia(listOf(window.first().id))
        assertTrue(deleted)
        assertEquals(249, fakeSource.observeMedia().first().size)
    }
}
