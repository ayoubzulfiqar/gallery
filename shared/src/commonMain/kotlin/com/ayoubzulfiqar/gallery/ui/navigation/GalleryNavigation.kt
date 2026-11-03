package com.ayoubzulfiqar.gallery.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

sealed class GalleryDestination {
    object Timeline : GalleryDestination()
    data class Viewer(val mediaId: String) : GalleryDestination()
    data class Editor(val mediaId: String) : GalleryDestination()
    object Vault : GalleryDestination()
    object Albums : GalleryDestination()
    object Explore : GalleryDestination()
}

class GalleryNavState(initialDestination: GalleryDestination = GalleryDestination.Timeline) {
    private val backStack = mutableListOf<GalleryDestination>()

    var currentDestination by mutableStateOf(initialDestination)
        private set

    fun navigateTo(destination: GalleryDestination) {
        if (currentDestination != destination) {
            backStack.add(currentDestination)
            currentDestination = destination
        }
    }

    fun navigateBack(): Boolean {
        if (backStack.isNotEmpty()) {
            currentDestination = backStack.removeAt(backStack.lastIndex)
            return true
        }
        return false
    }
}

@Composable
fun rememberGalleryNavState(
    initialDestination: GalleryDestination = GalleryDestination.Timeline
): GalleryNavState = remember {
    GalleryNavState(initialDestination)
}
