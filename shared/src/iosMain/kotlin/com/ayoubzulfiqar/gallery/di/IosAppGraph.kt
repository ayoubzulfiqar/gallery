package com.ayoubzulfiqar.gallery.di

import com.ayoubzulfiqar.gallery.core.io.ByteArraySink
import com.ayoubzulfiqar.gallery.core.io.ByteArraySource
import com.ayoubzulfiqar.gallery.core.io.ByteSink
import com.ayoubzulfiqar.gallery.core.io.ByteSource
import com.ayoubzulfiqar.gallery.data.local.KeyValueStore
import com.ayoubzulfiqar.gallery.vault.storage.VaultStorage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionComplete
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * iOS object graph. The photo library is not yet integrated with PhotoKit, so the app runs on the
 * built-in demo library; preferences and the vault are real and persistent.
 */
object IosAppGraph {
    val container: AppContainer by lazy {
        AppContainer.demo(
            keyValueStore = UserDefaultsKeyValueStore(),
            vaultStorage = ProtectedFileVaultStorage(vaultDirectory())
        )
    }

    private fun vaultDirectory(): String {
        @Suppress("UNCHECKED_CAST")
        val base = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true) as List<String>).first()
        return "$base/vault"
    }
}

class UserDefaultsKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }
}

/**
 * Vault files with NSFileProtectionComplete: unreadable while the device is locked. Streams are
 * buffered in memory because the iOS build only vaults demo media; a PhotoKit integration should
 * replace this with NSFileHandle-based streaming.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class ProtectedFileVaultStorage(private val root: String) : VaultStorage {
    private val fileManager = NSFileManager.defaultManager

    private fun path(relative: String): String {
        require(!relative.contains("..")) { "Path escapes the vault directory" }
        return "$root/$relative"
    }

    private fun ensureParent(fullPath: String) {
        val parent = fullPath.substringBeforeLast('/')
        fileManager.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
    }

    override fun read(path: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(path(path)) ?: return null
        return data.toByteArray()
    }

    override fun writeAtomically(path: String, data: ByteArray) {
        val full = path(path)
        ensureParent(full)
        val ok = data.toNSData().writeToFile(full, options = NSDataWritingAtomic or NSDataWritingFileProtectionComplete, error = null)
        check(ok) { "Failed to write $path" }
    }

    override fun openSource(path: String): ByteSource =
        ByteArraySource(read(path) ?: throw NoSuchElementException("Missing vault file $path"))

    override fun openSink(path: String): ByteSink {
        val target = path
        return object : ByteSink {
            private val buffer = ByteArraySink()
            override fun write(buffer: ByteArray, offset: Int, length: Int) = this.buffer.write(buffer, offset, length)
            override fun close() = writeAtomically(target, buffer.toByteArray())
        }
    }

    override fun rename(from: String, to: String) {
        val source = path(from)
        val target = path(to)
        ensureParent(target)
        if (fileManager.fileExistsAtPath(target)) fileManager.removeItemAtPath(target, error = null)
        check(fileManager.moveItemAtPath(source, toPath = target, error = null)) { "Rename failed: $from -> $to" }
    }

    override fun delete(path: String) {
        fileManager.removeItemAtPath(path(path), error = null)
    }

    override fun exists(path: String): Boolean = fileManager.fileExistsAtPath(path(path))

    override fun list(directory: String): List<String> {
        @Suppress("UNCHECKED_CAST")
        val names = fileManager.contentsOfDirectoryAtPath(path(directory), error = null) as? List<String> ?: return emptyList()
        return names
    }

    override fun deleteAll() {
        fileManager.removeItemAtPath(root, error = null)
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
        return bytes
    }

    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) return NSData()
        return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
    }
}
