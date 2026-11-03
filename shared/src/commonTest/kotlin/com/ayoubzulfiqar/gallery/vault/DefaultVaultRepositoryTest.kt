package com.ayoubzulfiqar.gallery.vault

import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.vault.data.DefaultVaultRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultVaultRepositoryTest {
    @Test
    fun testUnlockLockAndEncryptedItemLifecycle() = runTest {
        val repo = DefaultVaultRepository(defaultPin = "9876")
        assertFalse(repo.isVaultUnlocked())

        // Wrong pin
        assertFalse(repo.unlockVault("0000"))
        assertFalse(repo.isVaultUnlocked())

        // Correct pin
        assertTrue(repo.unlockVault("9876"))
        assertTrue(repo.isVaultUnlocked())

        val item = MediaItem(
            id = "photo_99",
            uri = "file://secret.jpg",
            displayName = "secret.jpg",
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE,
            dateTakenMillis = 1000L,
            dateModifiedMillis = 1000L,
            width = 100,
            height = 100,
            sizeBytes = 12
        )
        val payload = "TopSecretImageBytes".encodeToByteArray()

        val vaultEntry = repo.moveToVault(item, payload)
        assertEquals("vault_photo_99", vaultEntry.id)

        val vaulted = repo.getVaultItems()
        assertEquals(1, vaulted.size)
        assertTrue(vaulted.first().isVaulted)

        val restoredBytes = repo.restoreFromVault(vaultEntry.id)
        assertContentEquals(payload, restoredBytes)

        assertEquals(0, repo.getVaultItems().size)

        repo.lockVault()
        assertFalse(repo.isVaultUnlocked())
    }
}
