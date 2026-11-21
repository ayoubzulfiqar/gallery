package com.ayoubzulfiqar.gallery.vault

import com.ayoubzulfiqar.gallery.core.crypto.Aead
import com.ayoubzulfiqar.gallery.core.crypto.PureAesGcm
import com.ayoubzulfiqar.gallery.core.io.BinaryWriter
import com.ayoubzulfiqar.gallery.core.io.ByteArraySource
import com.ayoubzulfiqar.gallery.core.io.MalformedDataException
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.MediaType
import com.ayoubzulfiqar.gallery.domain.model.UnlockResult
import com.ayoubzulfiqar.gallery.vault.data.DefaultVaultRepository
import com.ayoubzulfiqar.gallery.vault.data.VaultConfig
import com.ayoubzulfiqar.gallery.vault.storage.InMemoryVaultStorage
import com.ayoubzulfiqar.gallery.vault.storage.PassthroughDeviceKeyProtector
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Defense-in-depth around the vault's persisted formats and state. */
class VaultHardeningTest {

    private val pin = "2580"
    private var now = 1_000_000L
    private val storage = InMemoryVaultStorage()

    private fun TestScope.newRepository() = DefaultVaultRepository(
        storage = storage,
        deviceKeyProtector = PassthroughDeviceKeyProtector(),
        aead = PureAesGcm(),
        config = VaultConfig(pbkdf2Iterations = 50, freeAttempts = 3, segmentSize = 64),
        clock = { now },
        dispatcher = StandardTestDispatcher(testScheduler)
    )

    private fun media(id: String) = MediaItem(
        id = id, uri = "content://media/$id", displayName = "$id.bin", mimeType = "application/octet-stream",
        mediaType = MediaType.IMAGE, dateTakenMillis = 1L, dateModifiedMillis = 1L,
        width = 0, height = 0, sizeBytes = 0
    )

    private fun headerBytes(iterations: Int, saltSize: Int): ByteArray = BinaryWriter(128)
        .raw("GVH1".encodeToByteArray()).byte(1).byte(1)
        .int(iterations).byte(4)
        .bytes(ByteArray(saltSize))
        .bytes(ByteArray(Aead.NONCE_SIZE))
        .bytes(ByteArray(Aead.KEY_SIZE + Aead.TAG_SIZE))
        .toByteArray()

    @Test
    fun headerWithAbsurdIterationCountIsRejected() {
        // A forged header must not be able to hang the KDF for hours.
        assertFailsWith<MalformedDataException> {
            DefaultVaultRepository.VaultHeader.decode(headerBytes(iterations = Int.MAX_VALUE, saltSize = 16))
        }
    }

    @Test
    fun headerWithWrongSaltLengthIsRejected() {
        assertFailsWith<MalformedDataException> {
            DefaultVaultRepository.VaultHeader.decode(headerBytes(iterations = 150_000, saltSize = 8))
        }
    }

    @Test
    fun deletedLockoutFileFailsClosedInsteadOfGrantingFreshAttempts() = runTest {
        val repo = newRepository()
        repo.initialize()
        repo.setUp(pin)
        repo.lock()
        // An attacker who can delete app-private files must not reset the guess counter.
        storage.delete(DefaultVaultRepository.LOCKOUT)
        assertIs<UnlockResult.LockedOut>(repo.unlock("1111"))
    }

    @Test
    fun recoveredHeicSequenceIsStillAnImage() = runTest {
        val repo = newRepository()
        repo.initialize()
        repo.setUp(pin)
        // ftyp box with brand 'heim' (HEIC image sequence), not plain 'heic'.
        val heic = byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'm'.code.toByte(), 0, 0, 0, 0)
        val added = repo.add(media("h"), ByteArraySource(heic), null)
        repo.lock()
        storage.delete(DefaultVaultRepository.INDEX)
        storage.delete(DefaultVaultRepository.INDEX_BACKUP)
        val repaired = newRepository()
        repaired.initialize()
        assertEquals(UnlockResult.Success, repaired.unlock(pin))
        val recovered = repaired.items.value.first { it.id == added.id }
        assertEquals("image/heic", recovered.mimeType)
        assertEquals(MediaType.IMAGE, recovered.mediaType)
    }
}