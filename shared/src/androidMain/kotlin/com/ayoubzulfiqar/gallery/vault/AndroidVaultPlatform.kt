package com.ayoubzulfiqar.gallery.vault

import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import com.ayoubzulfiqar.gallery.core.io.ByteSink
import com.ayoubzulfiqar.gallery.core.io.ByteSource
import com.ayoubzulfiqar.gallery.core.io.asByteSink
import com.ayoubzulfiqar.gallery.core.io.asByteSource
import com.ayoubzulfiqar.gallery.data.local.KeyValueStore
import com.ayoubzulfiqar.gallery.vault.storage.DeviceKeyProtector
import com.ayoubzulfiqar.gallery.vault.storage.VaultStorage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Vault files under an app-private, never-backed-up directory (`noBackupFilesDir`), so vault data
 * is excluded from cloud/device-transfer backups where its device-bound key could not follow.
 */
class FileVaultStorage(private val root: File) : VaultStorage {

    private fun file(path: String): File {
        val resolved = File(root, path).canonicalFile
        require(resolved.path.startsWith(root.canonicalPath)) { "Path escapes the vault directory" }
        return resolved
    }

    override fun read(path: String): ByteArray? = file(path).takeIf { it.isFile }?.readBytes()

    override fun writeAtomically(path: String, data: ByteArray) {
        val target = file(path)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temp).use { out ->
                out.write(data)
                out.fd.sync() // durable before the rename makes it visible
            }
            if (!temp.renameTo(target)) throw IOException("Atomic rename failed for $path")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    override fun openSource(path: String): ByteSource = file(path).inputStream().buffered(256 * 1024).asByteSource()

    override fun openSink(path: String): ByteSink {
        val target = file(path)
        target.parentFile?.mkdirs()
        val stream = FileOutputStream(target)
        val buffered = stream.buffered(256 * 1024)
        return object : ByteSink {
            override fun write(buffer: ByteArray, offset: Int, length: Int) = buffered.write(buffer, offset, length)
            override fun flush() = buffered.flush()
            override fun close() {
                buffered.flush()
                stream.fd.sync()
                buffered.close()
            }
        }
    }

    override fun rename(from: String, to: String) {
        val source = file(from)
        val target = file(to)
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) throw IOException("Rename failed: $from -> $to")
    }

    override fun delete(path: String) {
        file(path).delete()
    }

    override fun exists(path: String): Boolean = file(path).exists()

    override fun list(directory: String): List<String> =
        file(directory).listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()

    override fun deleteAll() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }
}

/**
 * Seals small secrets with a non-exportable AES-256-GCM key in the Android Keystore (hardware
 * backed on virtually all devices), binding the vault to this device.
 */
class KeystoreDeviceKeyProtector(private val alias: String = "gallery_vault_device_key_v1") : DeviceKeyProtector {

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    override val isHardwareBacked: Boolean by lazy {
        runCatching {
            val key = key()
            val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                @Suppress("DEPRECATION")
                info.isInsideSecureHardware
            }
        }.getOrDefault(false)
    }

    override fun protect(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plaintext)
        return byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + sealed
    }

    override fun unprotect(data: ByteArray): ByteArray {
        require(data.size > 2 && data[0] == FORMAT_VERSION) { "Unknown protected format" }
        val ivLength = data[1].toInt()
        require(ivLength in 12..16 && data.size > 2 + ivLength) { "Corrupted protected data" }
        val iv = data.copyOfRange(2, 2 + ivLength)
        val existing = keyStore.getKey(alias, null) as? SecretKey ?: throw IllegalStateException("Device key is missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, existing, GCMParameterSpec(128, iv))
        return cipher.doFinal(data, 2 + ivLength, data.size - 2 - ivLength)
    }

    override fun destroy() {
        runCatching { keyStore.deleteEntry(alias) }
    }

    @Synchronized
    private fun key(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
    }
}

class SharedPreferencesKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}
