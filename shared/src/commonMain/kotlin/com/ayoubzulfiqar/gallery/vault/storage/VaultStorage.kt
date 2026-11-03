package com.ayoubzulfiqar.gallery.vault.storage

import com.ayoubzulfiqar.gallery.core.io.ByteArraySink
import com.ayoubzulfiqar.gallery.core.io.ByteArraySource
import com.ayoubzulfiqar.gallery.core.io.ByteSink
import com.ayoubzulfiqar.gallery.core.io.ByteSource

/**
 * Private, non-backed-up file area holding the vault. Paths are relative and '/'-separated.
 * Implementations must create parent directories on demand and make [rename] an atomic replace.
 */
interface VaultStorage {
    fun read(path: String): ByteArray?
    fun writeAtomically(path: String, data: ByteArray)
    fun openSource(path: String): ByteSource
    fun openSink(path: String): ByteSink
    fun rename(from: String, to: String)
    fun delete(path: String)
    fun exists(path: String): Boolean
    fun list(directory: String): List<String>
    fun deleteAll()
}

/** Protects small secrets (the vault header) with a device-bound, non-exportable key when available. */
interface DeviceKeyProtector {
    val isHardwareBacked: Boolean
    fun protect(plaintext: ByteArray): ByteArray

    /** @throws Exception when the data cannot be authenticated with the device key. */
    fun unprotect(data: ByteArray): ByteArray

    /** Deletes the device key (used when the vault is reset). */
    fun destroy() {}
}

/** Used where no hardware key store is available; security then rests on the PIN-derived key alone. */
class PassthroughDeviceKeyProtector : DeviceKeyProtector {
    override val isHardwareBacked: Boolean = false
    override fun protect(plaintext: ByteArray): ByteArray = plaintext.copyOf()
    override fun unprotect(data: ByteArray): ByteArray = data.copyOf()
}

class InMemoryVaultStorage : VaultStorage {
    private val files = linkedMapOf<String, ByteArray>()

    val paths: Set<String> get() = files.keys.toSet()

    override fun read(path: String): ByteArray? = files[path]?.copyOf()

    override fun writeAtomically(path: String, data: ByteArray) {
        files[path] = data.copyOf()
    }

    override fun openSource(path: String): ByteSource =
        ByteArraySource(files[path] ?: throw NoSuchElementException("Missing vault file $path"))

    override fun openSink(path: String): ByteSink = object : ByteSink {
        private val buffer = ByteArraySink()
        override fun write(buffer: ByteArray, offset: Int, length: Int) = this.buffer.write(buffer, offset, length)
        override fun close() {
            files[path] = buffer.toByteArray()
        }
    }

    override fun rename(from: String, to: String) {
        val data = files.remove(from) ?: throw NoSuchElementException("Missing vault file $from")
        files[to] = data
    }

    override fun delete(path: String) {
        files.remove(path)
    }

    override fun exists(path: String): Boolean = path in files

    override fun list(directory: String): List<String> {
        val prefix = directory.trimEnd('/') + "/"
        return files.keys.filter { it.startsWith(prefix) && '/' !in it.removePrefix(prefix) }
            .map { it.removePrefix(prefix) }
    }

    override fun deleteAll() = files.clear()

    /** Test hook: flips a byte to simulate disk corruption or tampering. */
    fun corrupt(path: String, index: Int) {
        val data = files[path] ?: return
        val i = if (index < 0) data.size + index else index
        data[i] = (data[i].toInt() xor 0x5A).toByte()
    }
}
