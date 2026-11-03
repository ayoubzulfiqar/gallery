package com.ayoubzulfiqar.gallery.vault.crypto

expect class AesGcmCipher() {
    fun encrypt(plainText: ByteArray, key: ByteArray): ByteArray
    fun decrypt(cipherTextWithIvAndTag: ByteArray, key: ByteArray): ByteArray
}
