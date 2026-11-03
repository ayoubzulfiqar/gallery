package com.ayoubzulfiqar.gallery.core.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val secureRandom: SecureRandom by lazy { SecureRandom() }

actual fun secureRandomBytes(size: Int): ByteArray {
    require(size >= 0) { "size must be >= 0" }
    return ByteArray(size).also { if (size > 0) secureRandom.nextBytes(it) }
}

actual fun platformAead(): Aead = JcaAesGcm

/** AES-GCM backed by the platform provider (Conscrypt/BoringSSL on Android, hardware accelerated). */
internal object JcaAesGcm : Aead {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    override fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        require(nonce.size == Aead.NONCE_SIZE) { "AES-GCM nonce must be 12 bytes" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(Aead.TAG_SIZE * 8, nonce))
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
        return cipher.doFinal(plaintext)
    }

    override fun open(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, associatedData: ByteArray): ByteArray {
        require(nonce.size == Aead.NONCE_SIZE) { "AES-GCM nonce must be 12 bytes" }
        if (ciphertextAndTag.size < Aead.TAG_SIZE) throw AeadException("Ciphertext is shorter than the authentication tag")
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(Aead.TAG_SIZE * 8, nonce))
            if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertextAndTag)
        } catch (e: AEADBadTagException) {
            throw AeadException("Authentication tag mismatch: data is corrupted, tampered with or the key is wrong", e)
        } catch (e: GeneralSecurityException) {
            throw AeadException("Decryption failed", e)
        }
    }
}
