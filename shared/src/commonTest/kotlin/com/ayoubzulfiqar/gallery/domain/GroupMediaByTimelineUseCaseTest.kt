package com.ayoubzulfiqar.gallery.domain

import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.domain.usecase.GroupMediaByTimelineUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupMediaByTimelineUseCaseTest {
    @Test
    fun testEmptyListReturnsEmptyGroups() {
        val grouper = GroupMediaByTimelineUseCase()
        val groups = grouper(emptyList())
        assertTrue(groups.isEmpty())
    }

    @Test
    fun testGroupingByDateTaken() {
        val grouper = GroupMediaByTimelineUseCase()
        val item1 = MediaItem(
            id = "1",
            uri = "uri1",
            displayName = "p1.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE,
            dateTakenMillis = 1730635200000L, // 2024-11-03
            dateModifiedMillis = 1730635200000L,
            width = 100,
            height = 100,
            sizeBytes = 100
        )
        val item2 = item1.copy(id = "2", dateTakenMillis = 1730635300000L)
        val item3 = item1.copy(id = "3", dateTakenMillis = 1700000000000L) // different day

        val groups = grouper(listOf(item1, item2, item3))
        assertEquals(2, groups.size)
        assertEquals(2, groups[0].items.size)
        assertEquals(1, groups[1].items.size)
    }
}
