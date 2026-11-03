package com.ayoubzulfiqar.gallery.core.crypto

/**
 * Pure Kotlin SHA-256 (FIPS 180-4).
 *
 * Used for HMAC/PBKDF2/HKDF so that key derivation is bit-for-bit identical on every
 * platform. Instances are not thread-safe.
 */
class Sha256 {
    private val state = IntArray(8)
    private val block = ByteArray(BLOCK_SIZE)
    private val schedule = IntArray(64)
    private var blockLength = 0
    private var totalBytes = 0L

    init {
        reset()
    }

    fun reset() {
        INITIAL_STATE.copyInto(state)
        blockLength = 0
        totalBytes = 0L
    }

    /** Makes this instance an exact copy of [other] (used to reuse precomputed HMAC pads). */
    fun copyFrom(other: Sha256) {
        other.state.copyInto(state)
        other.block.copyInto(block)
        blockLength = other.blockLength
        totalBytes = other.totalBytes
    }

    fun update(input: ByteArray, offset: Int = 0, length: Int = input.size - offset) {
        require(offset >= 0 && length >= 0 && offset + length <= input.size) { "Invalid range" }
        var position = offset
        var remaining = length
        totalBytes += length
        if (blockLength > 0) {
            val take = minOf(remaining, BLOCK_SIZE - blockLength)
            input.copyInto(block, blockLength, position, position + take)
            blockLength += take
            position += take
            remaining -= take
            if (blockLength == BLOCK_SIZE) {
                compress(block, 0)
                blockLength = 0
            }
        }
        while (remaining >= BLOCK_SIZE) {
            compress(input, position)
            position += BLOCK_SIZE
            remaining -= BLOCK_SIZE
        }
        if (remaining > 0) {
            input.copyInto(block, 0, position, position + remaining)
            blockLength = remaining
        }
    }

    fun digest(): ByteArray {
        val out = ByteArray(DIGEST_SIZE)
        digestInto(out, 0)
        return out
    }

    /** Finalizes the hash into [out] at [offset] and resets this instance. */
    fun digestInto(out: ByteArray, offset: Int) {
        val bitLength = totalBytes * 8
        block[blockLength++] = 0x80.toByte()
        if (blockLength > BLOCK_SIZE - 8) {
            block.fill(0, blockLength, BLOCK_SIZE)
            compress(block, 0)
            blockLength = 0
        }
        block.fill(0, blockLength, BLOCK_SIZE - 8)
        for (i in 0 until 8) {
            block[BLOCK_SIZE - 1 - i] = (bitLength ushr (8 * i)).toByte()
        }
        compress(block, 0)
        for (i in 0 until 8) {
            val word = state[i]
            out[offset + i * 4] = (word ushr 24).toByte()
            out[offset + i * 4 + 1] = (word ushr 16).toByte()
            out[offset + i * 4 + 2] = (word ushr 8).toByte()
            out[offset + i * 4 + 3] = word.toByte()
        }
        reset()
    }

    private fun compress(input: ByteArray, offset: Int) {
        val w = schedule
        for (t in 0 until 16) {
            val i = offset + t * 4
            w[t] = ((input[i].toInt() and 0xFF) shl 24) or
                ((input[i + 1].toInt() and 0xFF) shl 16) or
                ((input[i + 2].toInt() and 0xFF) shl 8) or
                (input[i + 3].toInt() and 0xFF)
        }
        for (t in 16 until 64) {
            val x = w[t - 15]
            val y = w[t - 2]
            val s0 = x.rotateRight(7) xor x.rotateRight(18) xor (x ushr 3)
            val s1 = y.rotateRight(17) xor y.rotateRight(19) xor (y ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }

        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]

        for (t in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + K[t] + w[t]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj
            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }

    companion object {
        const val BLOCK_SIZE = 64
        const val DIGEST_SIZE = 32

        fun hash(data: ByteArray): ByteArray = Sha256().apply { update(data) }.digest()

        private val INITIAL_STATE = longArrayOf(
            0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
            0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
        ).map { it.toInt() }.toIntArray()

        private val K = longArrayOf(
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
        ).map { it.toInt() }.toIntArray()
    }
}

/** HMAC-SHA256 (RFC 2104) with precomputed inner/outer pad states. */
class HmacSha256(key: ByteArray) {
    private val innerPadState = Sha256()
    private val outerPadState = Sha256()
    private val working = Sha256()

    init {
        val normalizedKey = if (key.size > Sha256.BLOCK_SIZE) Sha256.hash(key) else key
        val padded = ByteArray(Sha256.BLOCK_SIZE)
        normalizedKey.copyInto(padded)
        val inner = ByteArray(Sha256.BLOCK_SIZE) { (padded[it].toInt() xor 0x36).toByte() }
        val outer = ByteArray(Sha256.BLOCK_SIZE) { (padded[it].toInt() xor 0x5c).toByte() }
        innerPadState.update(inner)
        outerPadState.update(outer)
        padded.fill(0)
        inner.fill(0)
        outer.fill(0)
    }

    fun mac(vararg parts: ByteArray): ByteArray {
        val out = ByteArray(Sha256.DIGEST_SIZE)
        working.copyFrom(innerPadState)
        for (part in parts) working.update(part)
        working.digestInto(out, 0)
        working.copyFrom(outerPadState)
        working.update(out)
        working.digestInto(out, 0)
        return out
    }

    /** Allocation-free single-block MAC used by the PBKDF2 inner loop. */
    internal fun macInto(input: ByteArray, out: ByteArray) {
        working.copyFrom(innerPadState)
        working.update(input)
        working.digestInto(out, 0)
        working.copyFrom(outerPadState)
        working.update(out)
        working.digestInto(out, 0)
    }
}

object Pbkdf2 {
    /** PBKDF2-HMAC-SHA256 (RFC 8018). */
    fun hmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray {
        require(iterations >= 1) { "iterations must be >= 1" }
        require(keyLengthBytes in 1..(Sha256.DIGEST_SIZE * 1024)) { "Unsupported key length" }
        val prf = HmacSha256(password)
        val result = ByteArray(keyLengthBytes)
        val blockCount = (keyLengthBytes + Sha256.DIGEST_SIZE - 1) / Sha256.DIGEST_SIZE
        val u = ByteArray(Sha256.DIGEST_SIZE)
        val t = ByteArray(Sha256.DIGEST_SIZE)
        for (blockIndex in 1..blockCount) {
            val counter = byteArrayOf(
                (blockIndex ushr 24).toByte(), (blockIndex ushr 16).toByte(),
                (blockIndex ushr 8).toByte(), blockIndex.toByte()
            )
            prf.mac(salt, counter).copyInto(u)
            u.copyInto(t)
            for (i in 1 until iterations) {
                prf.macInto(u, u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val start = (blockIndex - 1) * Sha256.DIGEST_SIZE
            val count = minOf(Sha256.DIGEST_SIZE, keyLengthBytes - start)
            t.copyInto(result, start, 0, count)
        }
        u.fill(0)
        t.fill(0)
        return result
    }
}

object Hkdf {
    /** HKDF-SHA256 (RFC 5869). */
    fun sha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * Sha256.DIGEST_SIZE)) { "Invalid HKDF output length" }
        val prk = HmacSha256(if (salt.isEmpty()) ByteArray(Sha256.DIGEST_SIZE) else salt).mac(ikm)
        val expander = HmacSha256(prk)
        val okm = ByteArray(length)
        var previous = ByteArray(0)
        var produced = 0
        var counter = 1
        while (produced < length) {
            previous = expander.mac(previous, info, byteArrayOf(counter.toByte()))
            val count = minOf(previous.size, length - produced)
            previous.copyInto(okm, produced, 0, count)
            produced += count
            counter++
        }
        prk.fill(0)
        return okm
    }
}

/** Compares two arrays in time independent of where they differ. */
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
    return diff == 0
}

fun ByteArray.toHex(): String {
    val chars = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        chars[i * 2] = HEX_DIGITS[v ushr 4]
        chars[i * 2 + 1] = HEX_DIGITS[v and 0x0F]
    }
    return chars.concatToString()
}

fun String.hexToBytes(): ByteArray {
    val clean = filterNot { it.isWhitespace() }
    require(clean.length % 2 == 0) { "Hex string must have an even length" }
    return ByteArray(clean.length / 2) { i ->
        ((clean[i * 2].digitToInt(16) shl 4) or clean[i * 2 + 1].digitToInt(16)).toByte()
    }
}

private val HEX_DIGITS = "0123456789abcdef".toCharArray()
