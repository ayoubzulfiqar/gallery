package com.ayoubzulfiqar.gallery.vault.crypto

actual class AesGcmCipher actual constructor() {
    actual fun encrypt(plainText: ByteArray, key: ByteArray): ByteArray {
        // Fallback / Stub implementation for iOS compilation
        val iv = ByteArray(12) { (it + 1).toByte() }
        val tag = ByteArray(16) { 0xFF.toByte() }
        val dummyCipher = ByteArray(plainText.size) { i -> (plainText[i].toInt() xor key[i % key.size].toInt()).toByte() }
        return iv + dummyCipher + tag
    }

    actual fun decrypt(cipherTextWithIvAndTag: ByteArray, key: ByteArray): ByteArray {
        require(cipherTextWithIvAndTag.size >= 12 + 16) {
            "Ciphertext too short"
        }
        val cipherLen = cipherTextWithIvAndTag.size - 28
        val plain = ByteArray(cipherLen)
        for (i in 0 until cipherLen) {
            val byte = cipherTextWithIvAndTag[12 + i]
            plain[i] = (byte.toInt() xor key[i % key.size].toInt()).toByte()
        }
        return plain
    }
}
