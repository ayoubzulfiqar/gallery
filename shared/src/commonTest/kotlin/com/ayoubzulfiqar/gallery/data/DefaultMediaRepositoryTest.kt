package com.ayoubzulfiqar.gallery.data

import com.ayoubzulfiqar.gallery.data.datasource.FakePlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.data.repository.DefaultMediaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultMediaRepositoryTest {
    @Test
    fun testFavoriteToggleAndTimelineGrouping() = runTest {
        val platformSource = FakePlatformMediaDataSource()
        platformSource.populateWithSyntheticMedia(count = 20)
        val localStore = LocalMetadataStore()
        val repo = DefaultMediaRepository(platformSource, localStore)

        val items = repo.getMediaFlow().first()
        val firstId = items.first().id

        // Toggle favorite
        val isFav = repo.toggleFavorite(firstId)
        assertTrue(isFav)
        assertTrue(repo.getMediaFlow().first().first { it.id == firstId }.isFavorite)

        // Timeline grouping
        val groups = repo.getTimelineGroups()
        assertTrue(groups.isNotEmpty())
        assertEquals(20, groups.sumOf { it.items.size })
    }
}
