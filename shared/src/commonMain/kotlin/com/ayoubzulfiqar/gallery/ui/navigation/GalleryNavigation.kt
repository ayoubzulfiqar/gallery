package com.ayoubzulfiqar.gallery.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.runtime.mutableStateListOf

sealed class GalleryDestination {
    object Timeline : GalleryDestination()
    data class Viewer(val mediaId: String) : GalleryDestination()
    data class Editor(val mediaId: String) : GalleryDestination()
    object Vault : GalleryDestination()
    object Albums : GalleryDestination()
    data class FolderDetail(val bucketId: String, val folderName: String) : GalleryDestination()
    object Explore : GalleryDestination()
}

class GalleryNavState(initialDestination: GalleryDestination = GalleryDestination.Timeline) {
    private val backStack = mutableStateListOf<GalleryDestination>()

    var currentDestination by mutableStateOf(initialDestination)
        private set

    val canNavigateBack: Boolean
        get() = backStack.isNotEmpty()

    fun navigateTo(destination: GalleryDestination) {
        if (currentDestination != destination) {
            backStack.add(currentDestination)
            currentDestination = destination
        }
    }

    fun replaceWith(destination: GalleryDestination) {
        currentDestination = destination
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
