package com.ayoubzulfiqar.gallery.domain.repository

import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.VaultEntry

interface VaultRepository {
    suspend fun isVaultUnlocked(): Boolean
    suspend fun unlockVault(pin: String): Boolean
    suspend fun lockVault()
    suspend fun getVaultItems(): List<MediaItem>
    suspend fun moveToVault(mediaItem: MediaItem, plainTextBytes: ByteArray): VaultEntry
    suspend fun restoreFromVault(vaultId: String): ByteArray
    suspend fun restoreItem(itemId: String): MediaItem?
    suspend fun deleteVaultItem(itemId: String): Boolean
}
