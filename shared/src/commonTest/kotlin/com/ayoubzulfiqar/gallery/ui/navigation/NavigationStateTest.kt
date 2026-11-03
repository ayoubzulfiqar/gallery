package com.ayoubzulfiqar.gallery.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationStateTest {
    @Test
    fun testDestinationTransitions() {
        val navState = GalleryNavState(initialDestination = GalleryDestination.Timeline)
        assertEquals(GalleryDestination.Timeline, navState.currentDestination)

        navState.navigateTo(GalleryDestination.Viewer(mediaId = "123"))
        assertEquals(GalleryDestination.Viewer(mediaId = "123"), navState.currentDestination)

        navState.navigateBack()
        assertEquals(GalleryDestination.Timeline, navState.currentDestination)
    }
}
