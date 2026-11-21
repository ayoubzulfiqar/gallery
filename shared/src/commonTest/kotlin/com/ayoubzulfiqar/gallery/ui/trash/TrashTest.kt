package com.ayoubzulfiqar.gallery.ui.trash

import com.ayoubzulfiqar.gallery.data.datasource.DeleteMode
import com.ayoubzulfiqar.gallery.data.datasource.FakePlatformMediaDataSource
import com.ayoubzulfiqar.gallery.data.datasource.MediaCapabilities
import com.ayoubzulfiqar.gallery.data.local.LocalMetadataStore
import com.ayoubzulfiqar.gallery.data.repository.DefaultMediaRepository
import com.ayoubzulfiqar.gallery.di.UserMessenger
import com.ayoubzulfiqar.gallery.testutil.TestGraph
import com.ayoubzulfiqar.gallery.testutil.media
import com.ayoubzulfiqar.gallery.testutil.runViewModelTest
import com.ayoubzulfiqar.gallery.ui.navigation.GalleryDestination
import com.ayoubzulfiqar.gallery.ui.navigation.GalleryNavState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The system trash must be browsable and reversible: deleting to trash may never be a
 * one-way door the app promises ("Moved to trash") but cannot open again.
 */
class TrashTest {

    private fun trashSource(count: Int = 6) = FakePlatformMediaDataSource(
        MediaCapabilities(isDemoLibrary = true, hasTrash = true)
    ).apply { populateWithSyntheticMedia(count) }

    @Test
    fun trashDeleteMovesItemsOutOfLibraryIntoTrashKeepingContent() = runTest {
        val source = trashSource()
        val targets = source.currentItems.take(2)
        val result = source.deleteMedia(targets, DeleteMode.TRASH)
        assertEquals(targets.map { it.id }.toSet(), result.deletedIds)
        assertFalse(source.currentItems.any { it.id in result.deletedIds })
        val trashed = source.observeTrashed().first()
        assertEquals(targets.map { it.id }.toSet(), trashed.map { it.id }.toSet())
        // Content survives so a restore can bring the exact bytes back.
        assertTrue(source.contentOf(targets.first().id) != null)
    }

    @Test
    fun restoreFromTrashReturnsItemsToTheLibrary() = runTest {
        val source = trashSource()
        val targets = source.currentItems.take(2)
        source.deleteMedia(targets, DeleteMode.TRASH)
        val trashed = source.observeTrashed().first()
        val result = source.restoreFromTrash(trashed)
        assertEquals(targets.map { it.id }.toSet(), result.restoredIds)
        assertFalse(result.cancelled)
        assertTrue(source.observeTrashed().first().isEmpty())
        assertEquals(6, source.currentItems.size)
    }

    @Test
    fun restoreCanBeDeclinedAndChangesNothing() = runTest {
        val source = trashSource()
        source.deleteMedia(source.currentItems.take(1), DeleteMode.TRASH)
        source.cancelNextRestore = true
        val result = source.restoreFromTrash(source.observeTrashed().first())
        assertTrue(result.cancelled)
        assertTrue(result.restoredIds.isEmpty())
        assertEquals(1, source.observeTrashed().first().size)
    }

    @Test
    fun permanentDeleteRemovesTrashedContentForGood() = runTest {
        val source = trashSource()
        val target = source.currentItems.first()
        source.deleteMedia(listOf(target), DeleteMode.TRASH)
        val trashed = source.observeTrashed().first()
        val result = source.deleteMedia(trashed, DeleteMode.PERMANENT)
        assertEquals(setOf(target.id), result.deletedIds)
        assertTrue(source.observeTrashed().first().isEmpty())
        assertNull(source.contentOf(target.id))
    }

    @Test
    fun withoutTrashCapabilityDeleteIsPermanentAndTrashStaysEmpty() = runTest {
        val source = FakePlatformMediaDataSource().apply { populateWithSyntheticMedia(4) }
        assertFalse(source.capabilities.hasTrash)
        val target = source.currentItems.first()
        val result = source.deleteMedia(listOf(target), DeleteMode.TRASH)
        assertEquals(setOf(target.id), result.deletedIds)
        assertTrue(source.observeTrashed().first().isEmpty())
        assertNull(source.contentOf(target.id))
    }

    @Test
    fun repositoryRestoreUnhidesItemsInTheLibrary() = runTest {
        val graph = TestGraph(
            this,
            listOf(media("a", 300), media("b", 200), media("c", 100)),
            capabilities = MediaCapabilities(isDemoLibrary = true, hasTrash = true)
        )
        val repo = graph.repository
        val items = repo.library.first { it.isLoaded }.items
        repo.deleteMedia(items.filter { it.id != "c" }, DeleteMode.TRASH)
        assertEquals(listOf("c"), repo.library.first { it.items.size == 1 }.items.map { it.id })
        val trashed = repo.trashed.first { it.size == 2 }
        assertEquals(setOf("a", "b"), trashed.map { it.id }.toSet())

        val restored = repo.restoreFromTrash(trashed)
        assertEquals(setOf("a", "b"), restored.restoredIds)
        assertEquals(3, repo.library.first { it.items.size == 3 }.items.size)
        assertTrue(repo.trashed.first { it.isEmpty() }.isEmpty())
    }

    @Test
    fun trashViewModelRestoresSelectionAndReportsIt() = runViewModelTest { tracker ->
        val graph = TestGraph(
            this,
            listOf(media("a", 300), media("b", 200)),
            capabilities = MediaCapabilities(isDemoLibrary = true, hasTrash = true)
        )
        graph.repository.deleteMedia(graph.repository.library.first { it.isLoaded }.items, DeleteMode.TRASH)
        val messenger = UserMessenger()
        val vm = tracker.track(TrashViewModel(graph.repository, messenger, UnconfinedTestDispatcher(testScheduler)))
        val state = vm.uiState.first { it.items.size == 2 }
        assertEquals(2, state.items.size)
        assertFalse(state.isEmpty)

        vm.toggleSelection("a")
        vm.toggleSelection("b")
        assertEquals(setOf("a", "b"), vm.selectedIds.value)
        vm.restoreSelected()
        assertTrue(vm.uiState.first { it.isEmpty }.isEmpty)
        assertTrue(vm.selectedIds.value.isEmpty())
        assertEquals(2, graph.repository.library.first { it.items.size == 2 }.items.size)
    }

    @Test
    fun trashViewModelEmptyTrashDeletesEverythingPermanently() = runViewModelTest { tracker ->
        val graph = TestGraph(
            this,
            listOf(media("a", 300), media("b", 200)),
            capabilities = MediaCapabilities(isDemoLibrary = true, hasTrash = true)
        )
        graph.repository.deleteMedia(graph.repository.library.first { it.isLoaded }.items, DeleteMode.TRASH)
        val vm = tracker.track(TrashViewModel(graph.repository, UserMessenger(), UnconfinedTestDispatcher(testScheduler)))
        vm.uiState.first { it.items.size == 2 }
        vm.emptyTrash()
        assertTrue(vm.uiState.first { it.isEmpty }.isEmpty)
        assertTrue(graph.repository.library.first { it.isLoaded }.items.isEmpty())
        assertNull(graph.source.contentOf("a"))
    }

    @Test
    fun trashDestinationPushesAndPopsLikeAnyScreen() {
        val nav = GalleryNavState()
        nav.navigateTo(GalleryDestination.Trash)
        assertEquals(GalleryDestination.Trash, nav.currentDestination)
        assertTrue(nav.canNavigateBack)
        assertTrue(nav.navigateBack())
        assertEquals(GalleryDestination.Timeline, nav.currentDestination)
    }
}