package com.ayoubzulfiqar.gallery.vault.data

import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.VaultEntry
import com.ayoubzulfiqar.gallery.domain.model.VaultError
import com.ayoubzulfiqar.gallery.domain.repository.VaultRepository
import com.ayoubzulfiqar.gallery.vault.crypto.AesGcmCipher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultVaultRepository(
    private val cipher: AesGcmCipher = AesGcmCipher(),
    private var defaultPin: String = "1234"
) : VaultRepository {

    private val mutex = Mutex()
    private var isUnlocked = false
    private var derivedKey: ByteArray? = null

    // In-memory encrypted items map: vaultId -> (VaultEntry, EncryptedBytes)
    private val encryptedStore = mutableMapOf<String, Pair<VaultEntry, ByteArray>>()
    private val vaultedItems = mutableListOf<MediaItem>()

    override suspend fun isVaultUnlocked(): Boolean = mutex.withLock { isUnlocked }

    override suspend fun unlockVault(pin: String): Boolean = mutex.withLock {
        if (pin == defaultPin) {
            isUnlocked = true
            // Derive 32-byte key from PIN
            derivedKey = deriveKeyFromPin(pin)
            true
        } else {
            false
        }
    }

    override suspend fun lockVault(): Unit = mutex.withLock {
        isUnlocked = false
        derivedKey = null
    }

    override suspend fun getVaultItems(): List<MediaItem> = mutex.withLock {
        if (!isUnlocked) throw VaultError.VaultLocked()
        vaultedItems.toList()
    }

    override suspend fun moveToVault(mediaItem: MediaItem, plainTextBytes: ByteArray): VaultEntry = mutex.withLock {
        if (!isUnlocked || derivedKey == null) throw VaultError.VaultLocked()
        val key = derivedKey!!
        val encrypted = cipher.encrypt(plainTextBytes, key)

        val vaultId = "vault_${mediaItem.id}"
        val entry = VaultEntry(
            id = vaultId,
            originalId = mediaItem.id,
            encryptedFilePath = "sandbox://$vaultId.enc",
            originalDisplayName = mediaItem.displayName,
            mimeType = mediaItem.mimeType,
            vaultedAtMillis = 1730000000000L,
            sizeBytes = encrypted.size.toLong()
        )

        encryptedStore[vaultId] = Pair(entry, encrypted)
        vaultedItems.add(mediaItem.copy(isVaulted = true))
        entry
    }

    override suspend fun restoreFromVault(vaultId: String): ByteArray = mutex.withLock {
        if (!isUnlocked || derivedKey == null) throw VaultError.VaultLocked()
        val key = derivedKey!!
        val pair = encryptedStore[vaultId] ?: throw IllegalStateException("Vault item not found")
        val encrypted = pair.second
        val decrypted = cipher.decrypt(encrypted, key)

        encryptedStore.remove(vaultId)
        vaultedItems.removeAll { "vault_${it.id}" == vaultId }
        decrypted
    }

    private fun deriveKeyFromPin(pin: String): ByteArray {
        val key = ByteArray(32)
        val pinBytes = pin.encodeToByteArray()
        for (i in key.indices) {
            key[i] = pinBytes[i % pinBytes.size]
        }
        return key
    }
}
