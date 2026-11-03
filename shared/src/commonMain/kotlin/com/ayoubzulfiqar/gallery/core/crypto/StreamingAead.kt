package com.ayoubzulfiqar.gallery.core.crypto

import com.ayoubzulfiqar.gallery.core.io.ByteSink
import com.ayoubzulfiqar.gallery.core.io.ByteSource
import com.ayoubzulfiqar.gallery.core.io.readFully

/**
 * Segmented AES-GCM for files of unbounded size in constant memory.
 *
 * Wire format:
 * ```
 * header  = "GVS1" | version:u8 | segmentSize:u32be | salt[16] | noncePrefix[7]      (32 bytes)
 * segment = AES-GCM(fileKey, noncePrefix || index:u32be || isLast:u8, plaintextChunk, aad = header)
 * ```
 * - `fileKey = HKDF-SHA256(masterKey, salt, "GalleryVault/stream/v1" || 0x00 || associatedData)` gives each
 *   file its own key, so nonces can never collide across files and a ciphertext cannot be
 *   transplanted to another entry (the entry id is part of [associatedData]).
 * - The segment index in the nonce prevents reordering; the `isLast` flag prevents truncation
 *   at a segment boundary and appending.
 *
 * Decryption emits plaintext segment by segment, so callers writing to a persistent location must
 * treat the output as provisional until [decrypt] returns successfully.
 */
class StreamingAead(
    private val aead: Aead = platformAead(),
    private val segmentSize: Int = DEFAULT_SEGMENT_SIZE
) {
    init {
        require(segmentSize in MIN_SEGMENT_SIZE..MAX_SEGMENT_SIZE) { "Unsupported segment size" }
    }

    /** Encrypts [source] into [sink]. Returns the plaintext length. */
    fun encrypt(masterKey: ByteArray, associatedData: ByteArray, source: ByteSource, sink: ByteSink): Long {
        val salt = secureRandomBytes(SALT_SIZE)
        val noncePrefix = secureRandomBytes(NONCE_PREFIX_SIZE)
        val header = buildHeader(segmentSize, salt, noncePrefix)
        sink.write(header)
        val fileKey = deriveFileKey(masterKey, salt, associatedData)
        try {
            var current = ByteArray(segmentSize)
            var next = ByteArray(segmentSize)
            var currentLength = source.readFully(current, 0, segmentSize)
            var index = 0L
            var total = 0L
            while (true) {
                val nextLength = if (currentLength == segmentSize) source.readFully(next, 0, segmentSize) else 0
                val isLast = nextLength == 0
                val chunk = if (currentLength == segmentSize) current else current.copyOf(currentLength)
                sink.write(aead.seal(fileKey, nonce(noncePrefix, index, isLast), chunk, header))
                total += currentLength
                if (isLast) break
                index++
                check(index <= MAX_SEGMENTS) { "File too large for streaming encryption" }
                val swap = current
                current = next
                next = swap
                currentLength = nextLength
            }
            sink.flush()
            return total
        } finally {
            fileKey.fill(0)
        }
    }

    /**
     * Decrypts [source] into [sink]. Returns the plaintext length.
     * @throws AeadException on any corruption, tampering, truncation or wrong key/associated data.
     */
    fun decrypt(masterKey: ByteArray, associatedData: ByteArray, source: ByteSource, sink: ByteSink): Long {
        val header = ByteArray(HEADER_SIZE)
        if (source.readFully(header) != HEADER_SIZE) throw AeadException("Encrypted file header is truncated")
        if (header[0] != 'G'.code.toByte() || header[1] != 'V'.code.toByte() ||
            header[2] != 'S'.code.toByte() || header[3] != '1'.code.toByte()
        ) {
            throw AeadException("Not a vault stream (bad magic)")
        }
        if (header[4].toInt() != VERSION) throw AeadException("Unsupported vault stream version ${header[4]}")
        val storedSegmentSize = AesEngine.readInt(header, 5)
        if (storedSegmentSize !in MIN_SEGMENT_SIZE..MAX_SEGMENT_SIZE) throw AeadException("Corrupted segment size")
        val salt = header.copyOfRange(9, 9 + SALT_SIZE)
        val noncePrefix = header.copyOfRange(9 + SALT_SIZE, HEADER_SIZE)
        val fileKey = deriveFileKey(masterKey, salt, associatedData)
        try {
            val encryptedSegmentSize = storedSegmentSize + Aead.TAG_SIZE
            var current = ByteArray(encryptedSegmentSize)
            var next = ByteArray(encryptedSegmentSize)
            var currentLength = source.readFully(current, 0, encryptedSegmentSize)
            if (currentLength < Aead.TAG_SIZE) throw AeadException("Encrypted file is truncated")
            var index = 0L
            var total = 0L
            while (true) {
                val nextLength = if (currentLength == encryptedSegmentSize) source.readFully(next, 0, encryptedSegmentSize) else 0
                val isLast = nextLength == 0
                if (!isLast && nextLength < Aead.TAG_SIZE) throw AeadException("Encrypted file is truncated")
                val chunk = if (currentLength == encryptedSegmentSize) current else current.copyOf(currentLength)
                val plain = aead.open(fileKey, nonce(noncePrefix, index, isLast), chunk, header)
                sink.write(plain)
                total += plain.size
                if (isLast) break
                index++
                if (index > MAX_SEGMENTS) throw AeadException("Encrypted file has too many segments")
                val swap = current
                current = next
                next = swap
                currentLength = nextLength
            }
            sink.flush()
            return total
        } finally {
            fileKey.fill(0)
        }
    }

    private fun deriveFileKey(masterKey: ByteArray, salt: ByteArray, associatedData: ByteArray): ByteArray {
        val info = INFO_PREFIX + byteArrayOf(0) + associatedData
        return Hkdf.sha256(masterKey, salt, info, Aead.KEY_SIZE)
    }

    private fun nonce(prefix: ByteArray, index: Long, isLast: Boolean): ByteArray {
        val nonce = ByteArray(Aead.NONCE_SIZE)
        prefix.copyInto(nonce)
        AesEngine.writeInt(nonce, NONCE_PREFIX_SIZE, index.toInt())
        nonce[Aead.NONCE_SIZE - 1] = if (isLast) 1 else 0
        return nonce
    }

    private fun buildHeader(segmentSize: Int, salt: ByteArray, noncePrefix: ByteArray): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        header[0] = 'G'.code.toByte()
        header[1] = 'V'.code.toByte()
        header[2] = 'S'.code.toByte()
        header[3] = '1'.code.toByte()
        header[4] = VERSION.toByte()
        AesEngine.writeInt(header, 5, segmentSize)
        salt.copyInto(header, 9)
        noncePrefix.copyInto(header, 9 + SALT_SIZE)
        return header
    }

    companion object {
        const val DEFAULT_SEGMENT_SIZE = 256 * 1024
        const val MIN_SEGMENT_SIZE = 16
        const val MAX_SEGMENT_SIZE = 8 * 1024 * 1024
        const val HEADER_SIZE = 32
        private const val VERSION = 1
        private const val SALT_SIZE = 16
        private const val NONCE_PREFIX_SIZE = 7
        private const val MAX_SEGMENTS = 0xFFFF_FFFFL
        private val INFO_PREFIX = "GalleryVault/stream/v1".encodeToByteArray()
    }
}
