package com.ayoubzulfiqar.gallery.domain

import com.ayoubzulfiqar.gallery.domain.model.VaultError
import com.ayoubzulfiqar.gallery.domain.usecase.TransferProgress
import com.ayoubzulfiqar.gallery.domain.usecase.VaultTransferCoordinator
import com.ayoubzulfiqar.gallery.testutil.TestGraph
import com.ayoubzulfiqar.gallery.testutil.media
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VaultTransferCoordinatorTest {

    @Test
    fun moveEncryptsRealContentThenDeletesOriginals() = runTest {
        val graph = TestGraph(this, listOf(media("a"), media("b"), media("keep")))
        val coordinator = VaultTransferCoordinator(graph.source, graph.repository, graph.vault)
        graph.vault.initialize()
        graph.vault.setUp("2580")
        val originalBytes = assertNotNull(graph.source.contentOf("a"))
        val toMove = graph.repository.library.first { it.isLoaded }.items.filter { it.id != "keep" }

        val progress = mutableListOf<TransferProgress>()
        val report = coordinator.moveToVault(toMove) { progress += it }

        assertEquals(2, report.succeeded)
        assertEquals(listOf(TransferProgress(1, 2), TransferProgress(2, 2)), progress)
        assertEquals(listOf("keep"), graph.source.currentItems.map { it.id })
        val vaulted = graph.vault.items.value.first { it.originalId == "a" }
        assertContentEquals(originalBytes, graph.vault.readContent(vaulted.id, Long.MAX_VALUE), "the vault holds the real bytes")
        assertTrue(vaulted.hasThumbnail)
    }

    @Test
    fun decliningDeletionRollsBackSoNothingIsDuplicatedOrLost() = runTest {
        val graph = TestGraph(this, listOf(media("a")))
        val coordinator = VaultTransferCoordinator(graph.source, graph.repository, graph.vault)
        graph.vault.initialize()
        graph.vault.setUp("2580")
        graph.source.cancelNextDelete = true

        val report = coordinator.moveToVault(graph.repository.library.first { it.isLoaded }.items)

        assertEquals(0, report.succeeded)
        assertEquals(1, report.keptInGallery)
        assertTrue(report.cancelled)
        assertTrue(graph.vault.items.value.isEmpty(), "encrypted copy removed")
        assertEquals(listOf("a"), graph.source.currentItems.map { it.id }, "original untouched")
        assertEquals(listOf("a"), graph.repository.library.first { it.items.isNotEmpty() }.items.map { it.id }, "unhidden again")
    }

    @Test
    fun moveIntoLockedVaultFailsWithoutTouchingOriginals() = runTest {
        val graph = TestGraph(this, listOf(media("a")))
        val coordinator = VaultTransferCoordinator(graph.source, graph.repository, graph.vault)
        graph.vault.initialize()
        graph.vault.setUp("2580")
        graph.vault.lock()
        assertFailsWith<VaultError.VaultLocked> {
            coordinator.moveToVault(graph.repository.library.first { it.isLoaded }.items)
        }
        assertEquals(1, graph.source.currentItems.size)
    }

    @Test
    fun restoreRoundTripsContentAndMetadata() = runTest {
        val graph = TestGraph(this, listOf(media("a")))
        val coordinator = VaultTransferCoordinator(graph.source, graph.repository, graph.vault)
        graph.vault.initialize()
        graph.vault.setUp("2580")
        val original = assertNotNull(graph.source.contentOf("a"))
        coordinator.moveToVault(graph.repository.library.first { it.isLoaded }.items)
        assertTrue(graph.source.currentItems.isEmpty())

        val report = coordinator.restore(graph.vault.items.value)

        assertEquals(1, report.succeeded)
        assertTrue(graph.vault.items.value.isEmpty())
        val restored = graph.source.currentItems.single()
        assertEquals("a.jpg", restored.displayName)
        assertEquals("DCIM/Camera/", restored.relativePath)
        assertContentEquals(original, graph.source.contentOf(restored.id))
    }

    @Test
    fun failedRestoreKeepsTheVaultCopy() = runTest {
        val graph = TestGraph(this, listOf(media("a")))
        val coordinator = VaultTransferCoordinator(graph.source, graph.repository, graph.vault)
        graph.vault.initialize()
        graph.vault.setUp("2580")
        coordinator.moveToVault(graph.repository.library.first { it.isLoaded }.items)
        graph.source.failImports = true

        val report = coordinator.restore(graph.vault.items.value)

        assertEquals(1, report.failed)
        assertEquals(1, graph.vault.items.value.size, "never delete the only copy when restoring fails")
    }
}
