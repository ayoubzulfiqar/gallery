package com.ayoubzulfiqar.gallery.domain.model

/** Metadata of an encrypted vault item. Content and thumbnail are only accessible while unlocked. */
data class VaultItem(
    val id: String,
    val originalId: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: MediaType,
    val dateTakenMillis: Long,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val sizeBytes: Long,
    val durationMs: Long?,
    val bucketName: String?,
    val relativePath: String?,
    val vaultedAtMillis: Long,
    val hasThumbnail: Boolean
) {
    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
}

sealed interface VaultState {
    /** Persistent vault state is being read. */
    data object Loading : VaultState

    /** No PIN has been created yet. */
    data object NotSetUp : VaultState

    data class Locked(
        val pinLength: Int,
        val failedAttempts: Int,
        val lockedUntilMillis: Long
    ) : VaultState

    data object Unlocked : VaultState

    /** The vault exists but cannot be opened on this device (e.g. key store reset, corrupted header). */
    data class Unavailable(val reason: String) : VaultState
}

sealed interface UnlockResult {
    data object Success : UnlockResult
    data class WrongPin(val attemptsBeforeLockout: Int) : UnlockResult
    data class LockedOut(val untilMillis: Long) : UnlockResult
    data class Failure(val message: String) : UnlockResult
}

sealed class VaultError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class VaultLocked : VaultError("Vault is locked")
    class NotSetUp : VaultError("Vault has not been set up")
    class InvalidPin(message: String) : VaultError(message)
    class ItemNotFound(id: String) : VaultError("Vault item $id was not found")
    class IntegrityCompromised(message: String = "Encrypted data has been tampered with or corrupted", cause: Throwable? = null) :
        VaultError(message, cause)
    class StorageFailure(message: String, cause: Throwable? = null) : VaultError(message, cause)
}

/** PIN requirements: 4–8 digits, rejecting trivially guessable sequences. */
object PinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    sealed interface Validation {
        data object Valid : Validation
        data class Invalid(val reason: String) : Validation
    }

    fun validateNewPin(pin: String): Validation {
        if (pin.length !in MIN_LENGTH..MAX_LENGTH) return Validation.Invalid("Use $MIN_LENGTH–$MAX_LENGTH digits")
        if (!pin.all { it in '0'..'9' }) return Validation.Invalid("PIN may contain digits only")
        if (pin.all { it == pin[0] }) return Validation.Invalid("Avoid repeating the same digit")
        val ascending = pin.zipWithNext().all { (a, b) -> b - a == 1 }
        val descending = pin.zipWithNext().all { (a, b) -> a - b == 1 }
        if (ascending || descending) return Validation.Invalid("Avoid sequential digits like 1234")
        return Validation.Valid
    }

    fun isWellFormed(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }
}
