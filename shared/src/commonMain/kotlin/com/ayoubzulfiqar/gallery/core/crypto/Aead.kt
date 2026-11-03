package com.ayoubzulfiqar.gallery.core.crypto

/**
 * Authenticated encryption with associated data, fixed to AES-GCM with 96-bit nonces and
 * 128-bit tags. Output of [seal] is `ciphertext || tag`.
 */
interface Aead {
    fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray = EMPTY_BYTES): ByteArray

    /** @throws AeadException when the key, nonce, ciphertext, tag or associated data do not match. */
    fun open(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, associatedData: ByteArray = EMPTY_BYTES): ByteArray

    companion object {
        const val NONCE_SIZE = 12
        const val TAG_SIZE = 16
        const val KEY_SIZE = 32
    }
}

/** Raised when authenticated decryption fails (wrong key, tampering, truncation, corruption). */
class AeadException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

val EMPTY_BYTES = ByteArray(0)

/** The fastest correct AES-GCM implementation available on the current platform. */
expect fun platformAead(): Aead

/** Cryptographically secure random bytes from the operating system CSPRNG. */
expect fun secureRandomBytes(size: Int): ByteArray

/**
 * Portable AES-GCM (NIST SP 800-38D) for 96-bit nonces.
 *
 * GHASH uses Shoup's 4-bit table method; [gfMultiplyReference] keeps the bit-serial
 * definition from the specification so tests can cross-check the optimized path.
 */
class PureAesGcm : Aead {

    override fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        require(nonce.size == Aead.NONCE_SIZE) { "AES-GCM nonce must be 12 bytes" }
        val engine = AesEngine(key)
        try {
            val ghash = GHash(hashSubkey(engine))
            val counter = initialCounter(nonce)
            val output = ByteArray(plaintext.size + Aead.TAG_SIZE)
            ctr(engine, counter, plaintext, output)
            val tag = computeTag(engine, ghash, nonce, associatedData, output, plaintext.size)
            tag.copyInto(output, plaintext.size)
            return output
        } finally {
            engine.destroy()
        }
    }

    override fun open(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, associatedData: ByteArray): ByteArray {
        require(nonce.size == Aead.NONCE_SIZE) { "AES-GCM nonce must be 12 bytes" }
        if (ciphertextAndTag.size < Aead.TAG_SIZE) throw AeadException("Ciphertext is shorter than the authentication tag")
        val engine = AesEngine(key)
        try {
            val ghash = GHash(hashSubkey(engine))
            val ciphertextLength = ciphertextAndTag.size - Aead.TAG_SIZE
            val expectedTag = computeTag(engine, ghash, nonce, associatedData, ciphertextAndTag, ciphertextLength)
            val actualTag = ciphertextAndTag.copyOfRange(ciphertextLength, ciphertextAndTag.size)
            if (!constantTimeEquals(expectedTag, actualTag)) {
                throw AeadException("Authentication tag mismatch: data is corrupted, tampered with or the key is wrong")
            }
            val plaintext = ByteArray(ciphertextLength)
            ctr(engine, initialCounter(nonce), ciphertextAndTag, plaintext, length = ciphertextLength)
            return plaintext
        } finally {
            engine.destroy()
        }
    }

    private fun hashSubkey(engine: AesEngine): ByteArray {
        val h = ByteArray(16)
        engine.encryptBlock(h, 0, h, 0)
        return h
    }

    private fun initialCounter(nonce: ByteArray): ByteArray {
        val j0 = ByteArray(16)
        nonce.copyInto(j0)
        j0[15] = 1
        return j0
    }

    /** CTR keystream starting at inc32(J0). */
    private fun ctr(engine: AesEngine, j0: ByteArray, input: ByteArray, output: ByteArray, length: Int = input.size) {
        val counter = j0.copyOf()
        val keystream = ByteArray(16)
        var offset = 0
        while (offset < length) {
            increment32(counter)
            engine.encryptBlock(counter, 0, keystream, 0)
            val count = minOf(16, length - offset)
            for (i in 0 until count) {
                output[offset + i] = (input[offset + i].toInt() xor keystream[i].toInt()).toByte()
            }
            offset += count
        }
    }

    private fun computeTag(
        engine: AesEngine,
        ghash: GHash,
        nonce: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
        ciphertextLength: Int
    ): ByteArray {
        ghash.updatePadded(aad, aad.size)
        ghash.updatePadded(ciphertext, ciphertextLength)
        val lengths = ByteArray(16)
        writeLong(lengths, 0, aad.size.toLong() * 8)
        writeLong(lengths, 8, ciphertextLength.toLong() * 8)
        ghash.updateBlock(lengths, 0)
        val s = ghash.result()
        val ekj0 = ByteArray(16)
        engine.encryptBlock(initialCounter(nonce), 0, ekj0, 0)
        for (i in 0 until 16) s[i] = (s[i].toInt() xor ekj0[i].toInt()).toByte()
        return s
    }

    private fun increment32(counter: ByteArray) {
        for (i in 15 downTo 12) {
            counter[i] = (counter[i] + 1).toByte()
            if (counter[i].toInt() != 0) return
        }
    }

    private fun writeLong(b: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) b[offset + i] = (value ushr (56 - 8 * i)).toByte()
    }

    /** GHASH accumulator with Shoup 4-bit multiplication tables. */
    internal class GHash(h: ByteArray) {
        private val hh = LongArray(16)
        private val hl = LongArray(16)
        private var zh = 0L
        private var zl = 0L
        private val scratch = ByteArray(16)

        init {
            var vh = readLong(h, 0)
            var vl = readLong(h, 8)
            hh[8] = vh
            hl[8] = vl
            var i = 4
            while (i > 0) {
                val carry = (vl and 1L) != 0L
                vl = (vh shl 63) or (vl ushr 1)
                vh = (vh ushr 1) xor (if (carry) R_HIGH else 0L)
                hh[i] = vh
                hl[i] = vl
                i = i shr 1
            }
            i = 2
            while (i <= 8) {
                val baseH = hh[i]
                val baseL = hl[i]
                for (j in 1 until i) {
                    hh[i + j] = baseH xor hh[j]
                    hl[i + j] = baseL xor hl[j]
                }
                i *= 2
            }
        }

        fun updatePadded(data: ByteArray, length: Int) {
            var offset = 0
            while (offset + 16 <= length) {
                updateBlock(data, offset)
                offset += 16
            }
            if (offset < length) {
                scratch.fill(0)
                data.copyInto(scratch, 0, offset, length)
                updateBlock(scratch, 0)
            }
        }

        fun updateBlock(block: ByteArray, offset: Int) {
            zh = zh xor readLong(block, offset)
            zl = zl xor readLong(block, offset + 8)
            multiplyByH()
        }

        fun result(): ByteArray {
            val out = ByteArray(16)
            for (i in 0 until 8) {
                out[i] = (zh ushr (56 - 8 * i)).toByte()
                out[8 + i] = (zl ushr (56 - 8 * i)).toByte()
            }
            return out
        }

        private fun multiplyByH() {
            val x = ByteArray(16)
            for (i in 0 until 8) {
                x[i] = (zh ushr (56 - 8 * i)).toByte()
                x[8 + i] = (zl ushr (56 - 8 * i)).toByte()
            }
            var lo = x[15].toInt() and 0x0F
            var rh = hh[lo]
            var rl = hl[lo]
            for (i in 15 downTo 0) {
                lo = x[i].toInt() and 0x0F
                val hi = (x[i].toInt() ushr 4) and 0x0F
                if (i != 15) {
                    val rem = (rl and 0x0F).toInt()
                    rl = (rh shl 60) or (rl ushr 4)
                    rh = (rh ushr 4) xor (LAST4[rem] shl 48)
                    rh = rh xor hh[lo]
                    rl = rl xor hl[lo]
                }
                val rem = (rl and 0x0F).toInt()
                rl = (rh shl 60) or (rl ushr 4)
                rh = (rh ushr 4) xor (LAST4[rem] shl 48)
                rh = rh xor hh[hi]
                rl = rl xor hl[hi]
            }
            zh = rh
            zl = rl
        }

        companion object {
            private const val R_HIGH = -0x1f00000000000000L // 0xE100000000000000
            private val LAST4 = longArrayOf(
                0x0000, 0x1c20, 0x3840, 0x2460, 0x7080, 0x6ca0, 0x48c0, 0x54e0,
                0xe100, 0xfd20, 0xd940, 0xc560, 0x9180, 0x8da0, 0xa9c0, 0xb5e0
            )
        }
    }

    internal companion object {
        fun readLong(b: ByteArray, offset: Int): Long {
            var result = 0L
            for (i in 0 until 8) result = (result shl 8) or (b[offset + i].toLong() and 0xFF)
            return result
        }

        /** Bit-serial GF(2^128) multiply exactly as written in SP 800-38D (Algorithm 1). */
        fun gfMultiplyReference(x: ByteArray, y: ByteArray): ByteArray {
            var zh = 0L
            var zl = 0L
            var vh = readLong(y, 0)
            var vl = readLong(y, 8)
            for (i in 0 until 128) {
                val bit = (x[i / 8].toInt() ushr (7 - i % 8)) and 1
                if (bit == 1) {
                    zh = zh xor vh
                    zl = zl xor vl
                }
                val lsb = vl and 1L
                vl = (vl ushr 1) or (vh shl 63)
                vh = vh ushr 1
                if (lsb == 1L) vh = vh xor -0x1f00000000000000L
            }
            val out = ByteArray(16)
            for (i in 0 until 8) {
                out[i] = (zh ushr (56 - 8 * i)).toByte()
                out[8 + i] = (zl ushr (56 - 8 * i)).toByte()
            }
            return out
        }
    }
}

/**
 * Convenience one-shot cipher producing `nonce || ciphertext || tag` with a fresh random nonce.
 * Suitable for small payloads (keys, indexes, thumbnails); use [StreamingAead] for media files.
 */
class AesGcmCipher(private val aead: Aead = platformAead()) {

    fun encrypt(plainText: ByteArray, key: ByteArray, associatedData: ByteArray = EMPTY_BYTES): ByteArray {
        val nonce = secureRandomBytes(Aead.NONCE_SIZE)
        val sealed = aead.seal(key, nonce, plainText, associatedData)
        return nonce + sealed
    }

    /** @throws AeadException if the payload was modified, truncated or the key is wrong. */
    fun decrypt(cipherTextWithIvAndTag: ByteArray, key: ByteArray, associatedData: ByteArray = EMPTY_BYTES): ByteArray {
        if (cipherTextWithIvAndTag.size < Aead.NONCE_SIZE + Aead.TAG_SIZE) {
            throw AeadException("Ciphertext too short to contain a nonce and authentication tag")
        }
        val nonce = cipherTextWithIvAndTag.copyOfRange(0, Aead.NONCE_SIZE)
        val body = cipherTextWithIvAndTag.copyOfRange(Aead.NONCE_SIZE, cipherTextWithIvAndTag.size)
        return aead.open(key, nonce, body, associatedData)
    }
}
