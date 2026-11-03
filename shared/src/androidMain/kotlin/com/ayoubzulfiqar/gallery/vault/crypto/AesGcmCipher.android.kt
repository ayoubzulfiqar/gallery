package com.ayoubzulfiqar.gallery.vault.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual class AesGcmCipher actual constructor() {
    private val secureRandom = SecureRandom()

    actual fun encrypt(plainText: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)

        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherText = cipher.doFinal(plainText)
        // Output format: [12-byte IV] + [Ciphertext + 16-byte Auth Tag]
        val result = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(cipherText, 0, result, iv.size, cipherText.size)
        return result
    }

    actual fun decrypt(cipherTextWithIvAndTag: ByteArray, key: ByteArray): ByteArray {
        require(cipherTextWithIvAndTag.size >= 12 + 16) {
            "Ciphertext too short to contain valid IV and GCM authentication tag"
        }

        val iv = ByteArray(12)
        System.arraycopy(cipherTextWithIvAndTag, 0, iv, 0, iv.size)

        val cipherTextSize = cipherTextWithIvAndTag.size - iv.size
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(cipherTextWithIvAndTag, iv.size, cipherText, 0, cipherTextSize)

        try {
            val secretKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            return cipher.doFinal(cipherText)
        } catch (e: Exception) {
            throw IllegalStateException("Decryption failed: corrupted or tampered ciphertext", e)
        }
    }
}
