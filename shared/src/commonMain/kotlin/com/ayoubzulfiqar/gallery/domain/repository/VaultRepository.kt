package com.ayoubzulfiqar.gallery.domain.repository

import com.ayoubzulfiqar.gallery.core.io.ByteSink
import com.ayoubzulfiqar.gallery.core.io.ByteSource
import com.ayoubzulfiqar.gallery.domain.model.MediaItem
import com.ayoubzulfiqar.gallery.domain.model.UnlockResult
import com.ayoubzulfiqar.gallery.domain.model.VaultItem
import com.ayoubzulfiqar.gallery.domain.model.VaultState
import kotlinx.coroutines.flow.StateFlow

interface VaultRepository {
    val state: StateFlow<VaultState>

    /** Decrypted index of vault items; always empty while locked. */
    val items: StateFlow<List<VaultItem>>

    /** Reads persistent state. Safe to call repeatedly. */
    suspend fun initialize()

    /** Creates a new vault protected by [pin] and leaves it unlocked. */
    suspend fun setUp(pin: String)

    suspend fun unlock(pin: String): UnlockResult

    /** Hides content immediately and wipes key material from memory. */
    suspend fun lock()

    /** True when vault secrets are bound to a hardware-backed device key. */
    val isDeviceBound: Boolean

    suspend fun changePin(currentPin: String, newPin: String): UnlockResult

    /** Irreversibly destroys every vaulted item and the PIN. */
    suspend fun reset()

    /** Encrypts [content] into the vault. Requires the vault to be unlocked. */
    suspend fun add(item: MediaItem, content: ByteSource, thumbnail: ByteArray?): VaultItem

    suspend fun remove(ids: Collection<String>)

    /** Decrypts an item into [sink]; throws [com.ayoubzulfiqar.gallery.domain.model.VaultError.IntegrityCompromised] on tampering. */
    suspend fun export(id: String, sink: ByteSink)

    suspend fun readThumbnail(id: String): ByteArray?

    /** Decrypts a small item fully into memory for in-app viewing. */
    suspend fun readContent(id: String, maxBytes: Long): ByteArray
}
