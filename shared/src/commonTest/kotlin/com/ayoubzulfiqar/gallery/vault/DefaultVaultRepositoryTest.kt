package com.ayoubzulfiqar.gallery.vault

import com.ayoubzulfiqar.gallery.core.crypto.PureAesGcm
import com.ayoubzulfiqar.gallery.core.io.ByteArraySink
import com.ayoubzulfiqar.gallery.core.io.ByteArraySource
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.domain.model.UnlockResult
import com.ayoubzulfiqar.gallery.domain.model.VaultError
import com.ayoubzulfiqar.gallery.domain.model.VaultState
import com.ayoubzulfiqar.gallery.vault.data.DefaultVaultRepository
import com.ayoubzulfiqar.gallery.vault.data.VaultConfig
import com.ayoubzulfiqar.gallery.vault.storage.DeviceKeyProtector
import com.ayoubzulfiqar.gallery.vault.storage.InMemoryVaultStorage
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultVaultRepositoryTest {

    private val pin = "2580"
    private var now = 1_000_000L
    private val storage = InMemoryVaultStorage()

    private fun TestScope.newRepository(
        protector: DeviceKeyProtector = com.ayoubzulfiqar.gallery.vault.storage.PassthroughDeviceKeyProtector()
    ) = DefaultVaultRepository(
        storage = storage,
        deviceKeyProtector = protector,
        aead = PureAesGcm(),
        config = VaultConfig(pbkdf2Iterations = 50, freeAttempts = 3, segmentSize = 64),
        clock = { now },
        dispatcher = StandardTestDispatcher(testScheduler)
    )

    private fun media(id: String, mime: String = "image/jpeg") = MediaItem(
        id = id,
        uri = "content://media/$id",
        displayName = "$id.jpg",
        mimeType = mime,
        mediaType = MediaType.fromMimeType(mime),
        dateTakenMillis = 42L,
        dateModifiedMillis = 42L,
        width = 4000,
        height = 3000,
        sizeBytes = 0,
        orientation = 90,
        bucketName = "Camera",
        relativePath = "DCIM/Camera/"
    )

    private suspend fun DefaultVaultRepository.exportBytes(id: String): ByteArray =
        ByteArraySink().also { export(id, it) }.toByteArray()

    @Test
    fun fullLifecyclePersistsAcrossInstances() = runTest {
        val repo = newRepository()
        repo.initialize()
        assertEquals(VaultState.NotSetUp, repo.state.value)

        repo.setUp(pin)
        assertEquals(VaultState.Unlocked, repo.state.value)

        val payload = Random(1).nextBytes(1000)
        val added = repo.add(media("photo_1"), ByteArraySource(payload), thumbnail = byteArrayOf(1, 2, 3))
        assertEquals(1000L, added.sizeBytes)
        assertEquals(90, added.orientation)
        assertEquals("DCIM/Camera/", added.relativePath)
        assertContentEquals(payload, repo.exportBytes(added.id))
        assertContentEquals(byteArrayOf(1, 2, 3), repo.readThumbnail(added.id))

        // Nothing stored on disk may contain the plaintext.
        storage.paths.forEach { path ->
            val data = storage.read(path)!!
            assertFalse(data.decodeToString().contains("photo_1.jpg"), "plaintext name leaked in $path")
        }

        repo.lock()
        assertIs<VaultState.Locked>(repo.state.value)
        assertTrue(repo.items.value.isEmpty())
        assertNull(repo.readThumbnail(added.id))
        assertFailsWith<VaultError.VaultLocked> { repo.exportBytes(added.id) }

        // A fresh instance (app restart) sees the same vault.
        val reopened = newRepository()
        reopened.initialize()
        val locked = reopened.state.value
        assertIs<VaultState.Locked>(locked)
        assertEquals(4, locked.pinLength)
        assertEquals(UnlockResult.Success, reopened.unlock(pin))
        assertEquals(listOf(added), reopened.items.value)
        assertContentEquals(payload, reopened.exportBytes(added.id))

        reopened.remove(listOf(added.id))
        assertTrue(reopened.items.value.isEmpty())
        assertTrue(storage.list(DefaultVaultRepository.BLOBS).isEmpty())
        assertTrue(storage.list(DefaultVaultRepository.THUMBS).isEmpty())
    }

    @Test
    fun weakOrMalformedPinsAreRejectedAtSetup() = runTest {
        val repo = newRepository()
        repo.initialize()
        for (weak in listOf("1234", "0000", "9876", "12", "12a4", "123456789")) {
            assertFailsWith<VaultError.InvalidPin>(weak) { repo.setUp(weak) }
        }
        assertEquals(VaultState.NotSetUp, repo.state.value)
    }

    @Test
    fun wrongPinsTriggerPersistentExponentialLockout() = runTest {
        val repo = newRepository()
        repo.initialize()
        repo.setUp(pin)
        repo.lock()

        assertEquals(UnlockResult.WrongPin(2), repo.unlock("1111"))
        assertEquals(UnlockResult.WrongPin(1), repo.unlock("2222"))
        val lockedOut = repo.unlock("3333")
        assertIs<UnlockResult.LockedOut>(lockedOut)
        assertEquals(now + 30_000, lockedOut.untilMillis)

        // Even the correct PIN is refused during lockout, also after an app restart.
        assertIs<UnlockResult.LockedOut>(repo.unlock(pin))
        val restarted = newRepository()
        restarted.initialize()
        assertIs<UnlockResult.LockedOut>(restarted.unlock(pin))

        now += 30_001
        val next = restarted.unlock("4444")
        assertIs<UnlockResult.LockedOut>(next)
        assertEquals(now + 60_000, next.untilMillis, "back-off must grow")

        now += 60_001
        assertEquals(UnlockResult.Success, restarted.unlock(pin))
        restarted.lock()
        assertEquals(UnlockResult.WrongPin(2), restarted.unlock("1111"), "counter resets after success")
    }

    @Test
    fun changePinRewrapsKeyWithoutReEncryptingItems() = runTest {
        val repo = newRepository()
        repo.initialize()
        repo.setUp(pin)
        val added = repo.add(media("a"), ByteArraySource(byteArrayOf(9, 9, 9)), null)
        val blobBefore = storage.read(DefaultVaultRepository.blobPath(added.id))!!

        assertIs<UnlockResult.WrongPin>(repo.changePin("1111", "86420"))
        assertEquals(VaultState.Unlocked, repo.state.value, "wrong current PIN must not lock an open vault")
        assertEquals(UnlockResult.Success, repo.changePin(pin, "86420"))
        assertContentEquals(blobBefore, storage.read(DefaultVaultRepository.blobPath(added.id)))

        repo.lock()
        assertIs<UnlockResult.WrongPin>(repo.unlock(pin))
        assertEquals(UnlockResult.Success, repo.unlock("86420"))
        assertContentEquals(byteArrayOf(9, 9, 9), repo.exportBytes(added.id))
    }

    @Test
    fun tamperedBlobIsReportedAsIntegrityFailure() = runTest {
        val repo = newRepository()
        repo.initialize()
        repo.setUp(pin)
        val added = repo.add(media("t"), ByteArraySource(Random(3).nextBytes(300)), null)
        storage.corrupt(DefaultVaultRepository.blobPath(added.id), -5)
        assertFailsWith<VaultError.IntegrityCompromised> { repo.exportBytes(added.id) }
    }

    @Test
    fun corruptedIndexFallsBackToBackupAndOrphansAreAdopted() = runTest {
        val repo = newRepository()
        repo.initialize()
        repo.setUp(pin)
        val first = repo.add(media("first"), ByteArraySource(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1)), null)
        val second = repo.add(media("second"), ByteArraySource(byteArrayOf(5, 6, 7)), null)
        repo.lock()

        // Destroy both index copies: content must still be recovered from the encrypted files.
        storage.corrupt(DefaultVaultRepository.INDEX, 20)
        storage.corrupt(DefaultVaultRepository.INDEX_BACKUP, 20)
        val repaired = newRepository()
        repaired.initialize()
        assertEquals(UnlockResult.Success, repaired.unlock(pin))
        assertEquals(setOf(first.id, second.id), repaired.items.value.map { it.id }.toSet())
        val recoveredJpeg = repaired.items.value.first { it.id == first.id }
        assertEquals("image/jpeg", recoveredJpeg.mimeType)
        assertContentEquals(byteArrayOf(5, 6, 7), repaired.exportBytes(second.id))
    }

    @Test
    fun indexEntriesWithoutFilesArePruned() = runTest {
        val repo = newRepository()
        repo.initialize()
        repo.setUp(pin)
        val kept = repo.add(media("kept"), ByteArraySource(byteArrayOf(1)), null)
        val lost = repo.add(media("lost"), ByteArraySource(byteArrayOf(2)), null)
        repo.lock()
        storage.delete(DefaultVaultRepository.blobPath(lost.id))

        val reopened = newRepository()
        reopened.initialize()
        reopened.unlock(pin)
        assertEquals(listOf(kept.id), reopened.items.value.map { it.id })
    }

    @Test
    fun headerFromAnotherDeviceIsUnavailable() = runTest {
        val repo = newRepository()
        repo.initialize()
        repo.setUp(pin)
        repo.lock()

        val foreignDevice = object : DeviceKeyProtector {
            override val isHardwareBacked = true
            override fun protect(plaintext: ByteArray) = plaintext
            override fun unprotect(data: ByteArray): ByteArray = throw IllegalStateException("key mismatch")
        }
        val other = newRepository(foreignDevice)
        other.initialize()
        assertIs<VaultState.Unavailable>(other.state.value)
        assertIs<UnlockResult.Failure>(other.unlock(pin))
    }

    @Test
    fun resetDestroysEverything() = runTest {
        val repo = newRepository()
        repo.initialize()
        repo.setUp(pin)
        repo.add(media("x"), ByteArraySource(byteArrayOf(1, 2)), byteArrayOf(3))
        repo.reset()
        assertEquals(VaultState.NotSetUp, repo.state.value)
        assertTrue(storage.paths.isEmpty())
    }

    @Test
    fun operationsRequireAnUnlockedVault() = runTest {
        val repo = newRepository()
        repo.initialize()
        assertFailsWith<VaultError.NotSetUp> { repo.add(media("n"), ByteArraySource(byteArrayOf(1)), null) }
        repo.setUp(pin)
        repo.lock()
        assertFailsWith<VaultError.VaultLocked> { repo.add(media("n"), ByteArraySource(byteArrayOf(1)), null) }
        assertTrue(storage.list(DefaultVaultRepository.BLOBS).isEmpty(), "failed adds must not leave files behind")
    }
}
