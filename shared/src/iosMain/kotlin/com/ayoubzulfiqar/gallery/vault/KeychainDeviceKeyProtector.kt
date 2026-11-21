package com.ayoubzulfiqar.gallery.vault

import com.ayoubzulfiqar.gallery.core.crypto.Aead
import com.ayoubzulfiqar.gallery.core.crypto.PureAesGcm
import com.ayoubzulfiqar.gallery.core.crypto.secureRandomBytes
import com.ayoubzulfiqar.gallery.vault.storage.DeviceKeyProtector
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * Device-bound vault key for iOS: a random AES-256 key kept in the Keychain with
 * `kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly` — wrapped by hardware class keys, never
 * migratable to another device, and unreadable while the device is locked. The vault header is
 * sealed with AES-GCM under this key, so vault files copied off-device (including via backups)
 * cannot be brute-forced against the PIN alone.
 *
 * Requires a device passcode; without one the Keychain refuses the item and the vault reports
 * itself unavailable instead of silently falling back to PIN-only protection.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainDeviceKeyProtector(
    private val service: String = "com.ayoubzulfiqar.gallery.vault",
    private val account: String = "device-key-v1"
) : DeviceKeyProtector {

    private val aead = PureAesGcm()
    private val key: ByteArray by lazy { loadOrCreateKey() }

    override val isHardwareBacked: Boolean = true

    override fun protect(plaintext: ByteArray): ByteArray {
        val nonce = secureRandomBytes(Aead.NONCE_SIZE)
        return nonce + aead.seal(key, nonce, plaintext)
    }

    override fun unprotect(data: ByteArray): ByteArray {
        require(data.size >= Aead.NONCE_SIZE + Aead.TAG_SIZE) { "Corrupted protected data" }
        val nonce = data.copyOfRange(0, Aead.NONCE_SIZE)
        return aead.open(key, nonce, data.copyOfRange(Aead.NONCE_SIZE, data.size))
    }

    override fun destroy() {
        val query = baseQuery() ?: return
        try {
            SecItemDelete(query)
        } finally {
            CFRelease(query)
        }
    }

    private fun loadOrCreateKey(): ByteArray {
        copyKey()?.let { return it }
        val fresh = secureRandomBytes(Aead.KEY_SIZE)
        val query = baseQuery() ?: throw IllegalStateException("Keychain is unavailable")
        try {
            fresh.usePinned { pinned ->
                val data = CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret<UByteVar>(), fresh.size.toLong())
                CFDictionarySetValue(query, kSecValueData, data)
                CFRelease(data)
            }
            CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly)
            val status = SecItemAdd(query, null)
            check(status == errSecSuccess) {
                "Cannot create the vault device key (Keychain status $status). Set a device passcode, then try again."
            }
            return fresh
        } finally {
            CFRelease(query)
        }
    }

    private fun copyKey(): ByteArray? {
        val query = baseQuery() ?: return null
        try {
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
            return memScoped {
                val result = alloc<CFTypeRefVar>()
                if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) return@memScoped null
                @Suppress("UNCHECKED_CAST")
                val data = result.value as? CFDataRef ?: return@memScoped null
                try {
                    val length = CFDataGetLength(data)
                    val bytes = ByteArray(length.toInt())
                    if (length > 0L) {
                        bytes.usePinned { pinned ->
                            memcpy(pinned.addressOf(0), CFDataGetBytePtr(data), length.toULong())
                        }
                    }
                    bytes
                } finally {
                    CFRelease(data)
                }
            }
        } finally {
            CFRelease(query)
        }
    }

    private fun baseQuery(): CFMutableDictionaryRef? = memScoped {
        // The callbacks struct is only read during creation, so scoping its pointer here is safe.
        val query = CFDictionaryCreateMutable(
            kCFAllocatorDefault, 4, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr
        ) ?: return@memScoped null
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        setString(query, kSecAttrService, service)
        setString(query, kSecAttrAccount, account)
        query
    }

    private fun setString(query: CFMutableDictionaryRef, key: platform.CoreFoundation.CFStringRef?, value: String) {
        val cf = CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)
        try {
            CFDictionarySetValue(query, key, cf)
        } finally {
            CFRelease(cf)
        }
    }
}