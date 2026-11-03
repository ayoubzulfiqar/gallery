package com.ayoubzulfiqar.gallery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.ayoubzulfiqar.gallery.di.EditedImageExporter
import com.ayoubzulfiqar.gallery.di.UserMessenger
import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import com.ayoubzulfiqar.gallery.domain.model.MediaFilter
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaScope
import com.ayoubzulfiqar.gallery.domain.model.TimelineDensity
import com.ayoubzulfiqar.gallery.domain.model.VaultState
import com.ayoubzulfiqar.gallery.domain.usecase.VaultTransferCoordinator
import com.ayoubzulfiqar.gallery.data.datasource.ImportResult
import com.ayoubzulfiqar.gallery.testutil.TestGraph
import com.ayoubzulfiqar.gallery.testutil.media
import com.ayoubzulfiqar.gallery.testutil.runViewModelTest
import com.ayoubzulfiqar.gallery.ui.editor.AdjustmentParam
import com.ayoubzulfiqar.gallery.ui.editor.CropPreset
import com.ayoubzulfiqar.gallery.ui.editor.EditorEvent
import com.ayoubzulfiqar.gallery.ui.editor.PhotoEditorViewModel
import com.ayoubzulfiqar.gallery.ui.navigation.GalleryDestination
import com.ayoubzulfiqar.gallery.ui.navigation.GalleryNavState
import com.ayoubzulfiqar.gallery.ui.navigation.GalleryTab
import com.ayoubzulfiqar.gallery.ui.timeline.TimelineViewModel
import com.ayoubzulfiqar.gallery.ui.vault.PinMode
import com.ayoubzulfiqar.gallery.ui.vault.VaultViewModel
import com.ayoubzulfiqar.gallery.ui.viewer.ViewerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UiStateTest {

    // region navigation

    private class Probe : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    private fun GalleryNavState.probeInCurrentEntry(): Probe =
        ViewModelProvider.create(currentEntry, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = Probe() as T
        })[Probe::class]

    @Test
    fun tabSwitchingNeverGrowsTheBackStack() {
        val nav = GalleryNavState()
        repeat(10) {
            nav.switchTab(GalleryTab.ALBUMS)
            nav.switchTab(GalleryTab.VAULT)
            nav.switchTab(GalleryTab.PHOTOS)
        }
        assertEquals(1, nav.entries.size)
        assertFalse(nav.canNavigateBack, "back from Photos root exits the app")

        nav.switchTab(GalleryTab.ALBUMS)
        assertTrue(nav.canNavigateBack)
        assertTrue(nav.navigateBack())
        assertEquals(GalleryDestination.Timeline, nav.currentDestination, "back from another tab returns to Photos")
    }

    @Test
    fun poppedScreensClearTheirViewModels() {
        val nav = GalleryNavState()
        nav.navigateTo(GalleryDestination.FolderDetail("bucket", "Camera"))
        val folderProbe = nav.probeInCurrentEntry()
        nav.navigateTo(GalleryDestination.Viewer("1"))
        val viewerProbe = nav.probeInCurrentEntry()

        nav.navigateBack()
        assertTrue(viewerProbe.cleared)
        assertFalse(folderProbe.cleared)

        nav.navigateTo(GalleryDestination.Viewer("2"))
        val replaced = nav.probeInCurrentEntry()
        nav.replaceWith(GalleryDestination.Editor("2"))
        assertTrue(replaced.cleared)

        nav.switchTab(GalleryTab.VAULT)
        assertTrue(folderProbe.cleared, "switching tabs disposes the old stack")
        assertEquals(4, nav.drainRemovedKeys().size) // viewer popped, viewer replaced, folder + editor on tab switch
        assertTrue(nav.drainRemovedKeys().isEmpty())
    }

    @Test
    fun navigatingToATabRootSwitchesTabs() {
        val nav = GalleryNavState()
        nav.navigateTo(GalleryDestination.Viewer("1"))
        nav.navigateTo(GalleryDestination.Vault)
        assertEquals(1, nav.entries.size)
        assertEquals(GalleryTab.VAULT, nav.currentTab)
    }

    // endregion

    @Test
    fun viewerStaysOnTheCurrentPhotoWhenTheListChanges() = runViewModelTest { vms ->
        val graph = TestGraph(this, (1..5).map { media("m$it", takenMillis = 1000L - it) })
        val viewer = vms.track(ViewerViewModel(graph.repository, initialItemId = "m2", scope = MediaScope.Library(), computeDispatcher = graph.dispatcher))
        assertEquals("m2", viewer.uiState.first { !it.isLoading }.currentItem?.id)

        viewer.onPageChanged(3) // user swipes to m4
        viewer.toggleFavorite() // list re-emits
        val afterFavorite = viewer.uiState.first { it.currentItem?.isFavorite == true }
        assertEquals("m4", afterFavorite.currentItem?.id, "must not jump back to the photo that was opened")

        graph.source.setItems(graph.source.currentItems + media("newest", takenMillis = 5000L))
        assertEquals("m4", viewer.uiState.first { it.items.size == 6 }.currentItem?.id, "insertions before do not move the view")

        graph.repository.deleteMedia(graph.source.currentItems.filter { it.id == "m4" })
        val afterDelete = viewer.uiState.first { s -> s.items.none { it.id == "m4" } }
        assertEquals("m5", afterDelete.currentItem?.id, "the next photo takes the deleted one's place")
    }

    @Test
    fun allTimelineShowsPhotosFirstWithVideosSectionAtTheBottom() = runViewModelTest { vms ->
        val graph = TestGraph(
            this,
            listOf(
                media("newVideo", 9_000, mime = "video/mp4"), media("p1", 8_000), media("oldVideo", 1_000, mime = "video/mp4"),
                media("p2", 7_000)
            )
        )
        val timeline = vms.track(TimelineViewModel(graph.repository, graph.store, computeDispatcher = graph.dispatcher))
        val job = launch { timeline.uiState.collect {} }
        val state = timeline.uiState.first { !it.isLoading }
        assertEquals(listOf("p1", "p2", "newVideo", "oldVideo"), state.items.map { it.id }, "newest video still below every photo")
        val sectionStart = state.groups.indexOfFirst { it.sectionTitle != null }
        assertEquals("Videos", state.groups[sectionStart].sectionTitle)
        assertEquals("2 videos", state.groups[sectionStart].sectionSubtitle)
        assertTrue(state.groups.take(sectionStart).all { g -> g.items.none { it.isVideo } })
        assertTrue(state.groups.drop(sectionStart).all { g -> g.items.all { it.isVideo } })
        assertEquals(state.groups.size, state.groups.map { it.key }.toSet().size, "photo and video group keys never collide")

        timeline.setFilter(MediaFilter.FAVORITES)
        assertTrue(timeline.uiState.first { it.items.isEmpty() }.groups.none { it.sectionTitle != null })

        // The viewer pages in exactly the grid's order.
        val viewer = vms.track(ViewerViewModel(graph.repository, "p2", MediaScope.Library(MediaFilter.ALL), graph.dispatcher))
        val viewerState = viewer.uiState.first { !it.isLoading }
        assertEquals(listOf("p1", "p2", "newVideo", "oldVideo"), viewerState.items.map { it.id })
        assertEquals(1, viewerState.currentIndex)
        job.cancel()
    }

    @Test
    fun viewerPagesOnlyWithinItsScopeAndClosesWhenEmpty() = runViewModelTest { vms ->
        val graph = TestGraph(this, listOf(media("c1", bucket = "Camera"), media("s1", bucket = "Screenshots"), media("c2", 500, bucket = "Camera")))
        val viewer = vms.track(ViewerViewModel(graph.repository, "c1", MediaScope.Folder("bucket_camera", MediaFilter.ALL), graph.dispatcher))
        assertEquals(listOf("c1", "c2"), viewer.uiState.first { !it.isLoading }.items.map { it.id })

        graph.repository.deleteMedia(graph.source.currentItems.filter { it.bucketName == "Camera" })
        assertTrue(viewer.uiState.first { it.isEmpty }.isEmpty)
    }

    @Test
    fun timelineFiltersSelectionAndPersistsDensity() = runViewModelTest { vms ->
        val graph = TestGraph(this, listOf(media("p1", 300), media("v1", 200, mime = "video/mp4"), media("p2", 100)))
        val timeline = vms.track(TimelineViewModel(graph.repository, graph.store, computeDispatcher = graph.dispatcher))
        val job = launch { timeline.uiState.collect {} }

        assertEquals(3, timeline.uiState.first { !it.isLoading }.totalCount)
        timeline.setFilter(MediaFilter.PHOTOS)
        assertEquals(listOf("p1", "p2"), timeline.uiState.first { it.totalCount == 2 }.items.map { it.id })

        timeline.selectAll()
        assertEquals(setOf("p1", "p2"), timeline.selectedIds.value)
        timeline.toggleFavoriteForSelection()
        assertEquals(setOf("p1", "p2"), graph.store.favorites.value)
        timeline.toggleFavoriteForSelection()
        assertTrue(graph.store.favorites.value.isEmpty(), "second tap unfavorites when all were favorites")

        graph.repository.deleteMedia(graph.source.currentItems.filter { it.id == "p1" })
        timeline.uiState.first { it.totalCount == 1 }
        assertEquals(setOf("p2"), timeline.selectedIds.value, "deleted items leave the selection")

        timeline.setDensity(TimelineDensity.DAYS)
        timeline.stepDensity(zoomIn = true)
        timeline.stepDensity(zoomIn = true)
        assertEquals(TimelineDensity.MASONRY, graph.store.timelineDensity.value, "clamped at the largest size")
        job.cancel()
    }

    @Test
    fun vaultSetupUnlockAndQueuedMove() = runViewModelTest { vms ->
        val graph = TestGraph(this, listOf(media("secret")))
        val messenger = UserMessenger()
        val vm = vms.track(VaultViewModel(graph.vault, VaultTransferCoordinator(graph.source, graph.repository, graph.vault), messenger))
        vm.uiState.first { it.vaultState == VaultState.NotSetUp }
        assertEquals(PinMode.SETUP_ENTER, vm.uiState.value.pinMode)

        "1234".forEach(vm::onDigit)
        vm.onSubmit()
        assertTrue(vm.uiState.value.isPinError, "sequential PIN rejected")

        "2580".forEach(vm::onDigit)
        vm.onSubmit()
        assertEquals(PinMode.SETUP_CONFIRM, vm.uiState.value.pinMode)
        "2581".forEach(vm::onDigit)
        vm.onSubmit()
        assertEquals(PinMode.SETUP_ENTER, vm.uiState.value.pinMode, "mismatch restarts setup")

        "2580".forEach(vm::onDigit)
        vm.onSubmit()
        "2580".forEach(vm::onDigit)
        vm.onSubmit()
        vm.uiState.first { it.vaultState == VaultState.Unlocked }

        vm.lock()
        val locked = vm.uiState.first { it.vaultState is VaultState.Locked }
        assertEquals(4, locked.expectedPinLength)

        vm.queueMove(graph.repository.library.first { it.isLoaded }.items)
        assertEquals(1, vm.uiState.value.pendingMoveCount)
        "2580".forEach(vm::onDigit) // auto-submits at the expected length
        val done = vm.uiState.first { it.items.size == 1 && it.transfer == null }
        assertEquals("secret.jpg", done.items.single().displayName)
        assertTrue(graph.source.currentItems.isEmpty())

        // The full-screen preview hides the app's nav bar and must never outlive the session.
        vm.openPreview(done.items.single().id)
        assertEquals("secret.jpg", vm.uiState.value.previewItem?.displayName)
        vm.lock()
        val relocked = vm.uiState.first { it.vaultState is VaultState.Locked }
        assertEquals(null, relocked.previewItemId)
    }

    @Test
    fun editorCoalescesSlidersAndRespectsAspect() = runViewModelTest { vms ->
        val exported = mutableListOf<EditRecipe>()
        val exporter = object : EditedImageExporter {
            override suspend fun export(item: MediaItem, recipe: EditRecipe): ImportResult {
                exported += recipe
                return ImportResult.Success("demo://saved")
            }
        }
        val vm = vms.track(PhotoEditorViewModel(media("img", width = 4000, height = 3000), exporter))
        for (v in 1..20) vm.previewAdjustment(AdjustmentParam.CONTRAST, 1f + v / 40f)
        vm.commitAdjustment()
        assertEquals(1.5f, vm.uiState.value.recipe.adjustments.contrast)
        vm.undo()
        assertEquals(1f, vm.uiState.value.recipe.adjustments.contrast)
        assertFalse(vm.uiState.value.canUndo)

        vm.setCropPreset(CropPreset.SQUARE)
        val crop = vm.uiState.value.recipe.cropRect!!
        val outputAspect = ((crop.right - crop.left) * 4000) / ((crop.bottom - crop.top) * 3000)
        assertEquals(1f, outputAspect, 0.01f)

        vm.save()
        assertIs<EditorEvent.Saved>(vm.events.first())
        assertEquals(1, exported.size)

        val readOnly = vms.track(PhotoEditorViewModel(media("img"), exporter = null))
        readOnly.save()
        assertIs<EditorEvent.Error>(readOnly.events.first())
    }
}
