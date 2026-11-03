package com.ayoubzulfiqar.gallery.domain.model

data class VaultEntry(
    val id: String,
    val originalId: String,
    val encryptedFilePath: String,
    val originalDisplayName: String,
    val mimeType: String,
    val vaultedAtMillis: Long,
    val sizeBytes: Long
)

sealed class VaultError(message: String) : Throwable(message) {
    class AuthenticationFailed(message: String = "Biometric or PIN authentication failed") : VaultError(message)
    class IntegrityCompromised(message: String = "Encrypted ciphertext payload has been tampered with or corrupted") : VaultError(message)
    class VaultLocked(message: String = "Vault is currently locked") : VaultError(message)
}
