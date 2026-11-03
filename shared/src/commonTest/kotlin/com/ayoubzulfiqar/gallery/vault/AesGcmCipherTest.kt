package com.ayoubzulfiqar.gallery.vault

import com.ayoubzulfiqar.gallery.vault.crypto.AesGcmCipher
import com.ayoubzulfiqar.gallery.vault.data.DefaultVaultRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AesGcmCipherTest {
    @Test
    fun testEncryptionDecryptionRoundtrip() {
        val cipher = AesGcmCipher()
        val key = ByteArray(32) { it.toByte() }
        val original = "Highly sensitive private photo byte payload".encodeToByteArray()

        val encrypted = cipher.encrypt(original, key)
        assertTrue(encrypted.size > original.size)

        val decrypted = cipher.decrypt(encrypted, key)
        assertContentEquals(original, decrypted)
    }

    @Test
    fun testTamperedCiphertextRejection() {
        val cipher = AesGcmCipher()
        val key = ByteArray(32) { 7.toByte() }
        val encrypted = cipher.encrypt("secret".encodeToByteArray(), key)
        // Tamper with the last byte (auth tag)
        encrypted[encrypted.size - 1] = ((encrypted[encrypted.size - 1].toInt() + 1) % 256).toByte()

        assertFailsWith<IllegalStateException> {
            cipher.decrypt(encrypted, key)
        }
    }
}
