package com.ayoubzulfiqar.gallery.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.ayoubzulfiqar.gallery.domain.model.MediaScope

sealed interface GalleryDestination {
    data object Timeline : GalleryDestination
    data object Albums : GalleryDestination
    data object Vault : GalleryDestination
    data class Viewer(val mediaId: String, val scope: MediaScope = MediaScope.Library()) : GalleryDestination
    data class Editor(val mediaId: String) : GalleryDestination
    data class FolderDetail(val bucketId: String, val folderName: String) : GalleryDestination
}

enum class GalleryTab(val label: String, val root: GalleryDestination) {
    PHOTOS("Photos", GalleryDestination.Timeline),
    ALBUMS("Albums", GalleryDestination.Albums),
    VAULT("Vault", GalleryDestination.Vault)
}

/**
 * One back-stack entry. Owns a [ViewModelStore] so screen-scoped view models are cleared exactly
 * when the entry leaves the stack (no leaked collectors), and a stable [key] for saved UI state
 * (scroll positions survive returning from the viewer).
 */
class NavEntry internal constructor(
    val key: String,
    val destination: GalleryDestination
) : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/**
 * Tab-rooted back stack: switching tabs replaces the stack instead of growing it, back from a
 * non-default tab returns to Photos, and back from Photos leaves the app.
 */
class GalleryNavState(initialTab: GalleryTab = GalleryTab.PHOTOS) {
    private var nextId = 0L
    private val stack = mutableStateListOf(rootEntry(initialTab))
    private val removedKeys = mutableListOf<String>()

    val entries: List<NavEntry> get() = stack

    val currentEntry: NavEntry get() = stack.last()

    val currentDestination: GalleryDestination get() = currentEntry.destination

    val currentTab: GalleryTab
        get() = GalleryTab.entries.firstOrNull { it.root == stack.first().destination } ?: GalleryTab.PHOTOS

    val isAtTabRoot: Boolean get() = stack.size == 1

    /** True while the system back gesture should be handled in-app rather than closing the app. */
    val canNavigateBack: Boolean get() = stack.size > 1 || currentTab != GalleryTab.PHOTOS

    fun navigateTo(destination: GalleryDestination) {
        if (destination == currentDestination) return
        GalleryTab.entries.firstOrNull { it.root == destination }?.let {
            switchTab(it)
            return
        }
        stack.add(NavEntry("entry-${nextId++}", destination))
    }

    /** Replaces the current (non-root) screen, e.g. viewer → vault after moving the photo away. */
    fun replaceWith(destination: GalleryDestination) {
        GalleryTab.entries.firstOrNull { it.root == destination }?.let {
            switchTab(it)
            return
        }
        if (stack.size > 1) dispose(stack.removeAt(stack.lastIndex))
        stack.add(NavEntry("entry-${nextId++}", destination))
    }

    fun navigateBack(): Boolean {
        if (stack.size > 1) {
            dispose(stack.removeAt(stack.lastIndex))
            return true
        }
        if (currentTab != GalleryTab.PHOTOS) {
            switchTab(GalleryTab.PHOTOS)
            return true
        }
        return false
    }

    fun switchTab(tab: GalleryTab) {
        if (stack.size == 1 && stack[0].destination == tab.root) return
        val old = stack.toList()
        stack.clear()
        stack.add(rootEntry(tab))
        // Tab roots keep their saved state (scroll position) across tab switches.
        old.forEach { if (!it.key.startsWith(ROOT_PREFIX)) dispose(it) }
    }

    /** Keys whose saved UI state can be discarded; drained by the host. */
    fun drainRemovedKeys(): List<String> = removedKeys.toList().also { removedKeys.clear() }

    /** Clears every entry (host destroyed). */
    fun clear() {
        stack.forEach { it.viewModelStore.clear() }
    }

    private fun dispose(entry: NavEntry) {
        entry.viewModelStore.clear()
        removedKeys += entry.key
    }

    private fun rootEntry(tab: GalleryTab) = NavEntry("$ROOT_PREFIX${tab.name}", tab.root)

    private companion object {
        const val ROOT_PREFIX = "root-"
    }
}

/** Host-scoped holder so the back stack and its view models survive configuration changes. */
class NavigationHolderViewModel : ViewModel() {
    val navState = GalleryNavState()

    override fun onCleared() {
        navState.clear()
    }
}
