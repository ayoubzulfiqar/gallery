package com.ayoubzulfiqar.gallery.ui.editor

import com.ayoubzulfiqar.gallery.data.datasource.ImportResult
import com.ayoubzulfiqar.gallery.di.EditedImageExporter
import com.ayoubzulfiqar.gallery.domain.model.EditRecipe
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.testutil.media
import com.ayoubzulfiqar.gallery.testutil.runViewModelTest
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

class EditorSaveTest {

    private class ThrowingExporter(private val error: Throwable) : EditedImageExporter {
        override suspend fun export(item: MediaItem, recipe: EditRecipe): ImportResult = throw error
    }

    @Test
    fun outOfMemoryDuringExportBecomesAnErrorEventNotACrash() = runViewModelTest { tracker ->
        // A 6144x6144 ARGB render can exhaust memory on small devices; the user must get a
        // message, not a process crash.
        val vm = tracker.track(PhotoEditorViewModel(media("a"), ThrowingExporter(OutOfMemoryError("bitmap"))))
        vm.save()
        val event = vm.events.first()
        assertIs<EditorEvent.Error>(event)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun ordinaryExportFailureStillReportsAndResetsSaving() = runViewModelTest { tracker ->
        val vm = tracker.track(PhotoEditorViewModel(media("a"), ThrowingExporter(IllegalStateException("disk full"))))
        vm.save()
        assertIs<EditorEvent.Error>(vm.events.first())
        assertFalse(vm.uiState.value.isSaving)
    }
}