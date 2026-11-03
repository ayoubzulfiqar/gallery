package com.ayoubzulfiqar.gallery.core.crypto

/**
 * Pure Kotlin AES block cipher (FIPS-197), encryption direction only.
 *
 * Only the forward cipher is required because GCM uses AES in counter mode. The S-box and
 * T-tables are derived at class-load time from the GF(2^8) definition instead of being
 * transcribed by hand, eliminating an entire class of typo bugs.
 *
 * Note: table-based AES is not constant-time with respect to cache timing. It is used only
 * on platforms that lack a public AES-GCM API (iOS / Kotlin Native), for at-rest encryption
 * of the user's own files where co-resident timing attackers are outside the threat model.
 */
internal class AesEngine(key: ByteArray) {
    private val rounds: Int
    private val roundKeys: IntArray

    init {
        require(key.size == 16 || key.size == 24 || key.size == 32) { "AES key must be 128, 192 or 256 bits" }
        val nk = key.size / 4
        rounds = nk + 6
        val total = 4 * (rounds + 1)
        roundKeys = IntArray(total)
        for (i in 0 until nk) roundKeys[i] = readInt(key, i * 4)
        var rcon = 1
        for (i in nk until total) {
            var temp = roundKeys[i - 1]
            if (i % nk == 0) {
                temp = subWord(temp.rotateLeft(8)) xor (rcon shl 24)
                rcon = xtime(rcon)
            } else if (nk > 6 && i % nk == 4) {
                temp = subWord(temp)
            }
            roundKeys[i] = roundKeys[i - nk] xor temp
        }
    }

    fun encryptBlock(input: ByteArray, inOffset: Int, output: ByteArray, outOffset: Int) {
        val rk = roundKeys
        var s0 = readInt(input, inOffset) xor rk[0]
        var s1 = readInt(input, inOffset + 4) xor rk[1]
        var s2 = readInt(input, inOffset + 8) xor rk[2]
        var s3 = readInt(input, inOffset + 12) xor rk[3]
        var k = 4
        for (round in 1 until rounds) {
            val t0 = TE0[s0 ushr 24] xor TE1[(s1 ushr 16) and 0xFF] xor TE2[(s2 ushr 8) and 0xFF] xor TE3[s3 and 0xFF] xor rk[k]
            val t1 = TE0[s1 ushr 24] xor TE1[(s2 ushr 16) and 0xFF] xor TE2[(s3 ushr 8) and 0xFF] xor TE3[s0 and 0xFF] xor rk[k + 1]
            val t2 = TE0[s2 ushr 24] xor TE1[(s3 ushr 16) and 0xFF] xor TE2[(s0 ushr 8) and 0xFF] xor TE3[s1 and 0xFF] xor rk[k + 2]
            val t3 = TE0[s3 ushr 24] xor TE1[(s0 ushr 16) and 0xFF] xor TE2[(s1 ushr 8) and 0xFF] xor TE3[s2 and 0xFF] xor rk[k + 3]
            s0 = t0; s1 = t1; s2 = t2; s3 = t3
            k += 4
        }
        val o0 = finalRound(s0, s1, s2, s3) xor rk[k]
        val o1 = finalRound(s1, s2, s3, s0) xor rk[k + 1]
        val o2 = finalRound(s2, s3, s0, s1) xor rk[k + 2]
        val o3 = finalRound(s3, s0, s1, s2) xor rk[k + 3]
        writeInt(output, outOffset, o0)
        writeInt(output, outOffset + 4, o1)
        writeInt(output, outOffset + 8, o2)
        writeInt(output, outOffset + 12, o3)
    }

    /** Overwrites round keys; the engine must not be used afterwards. */
    fun destroy() = roundKeys.fill(0)

    private fun finalRound(a: Int, b: Int, c: Int, d: Int): Int =
        (SBOX[a ushr 24] shl 24) or (SBOX[(b ushr 16) and 0xFF] shl 16) or
            (SBOX[(c ushr 8) and 0xFF] shl 8) or SBOX[d and 0xFF]

    private fun subWord(word: Int): Int =
        (SBOX[word ushr 24] shl 24) or (SBOX[(word ushr 16) and 0xFF] shl 16) or
            (SBOX[(word ushr 8) and 0xFF] shl 8) or SBOX[word and 0xFF]

    companion object {
        internal val SBOX: IntArray = IntArray(256).also { box ->
            var p = 1
            var q = 1
            do {
                // p := p * 3 in GF(2^8)
                p = (p xor (p shl 1) xor (if (p and 0x80 != 0) 0x1B else 0)) and 0xFF
                // q := q / 3 (multiplicative inverse walk)
                q = q xor (q shl 1)
                q = q xor (q shl 2)
                q = q xor (q shl 4)
                q = q and 0xFF
                if (q and 0x80 != 0) q = q xor 0x09
                val affine = q xor rotl8(q, 1) xor rotl8(q, 2) xor rotl8(q, 3) xor rotl8(q, 4)
                box[p] = (affine xor 0x63) and 0xFF
            } while (p != 1)
            box[0] = 0x63
        }

        private val TE0 = IntArray(256) { x ->
            val s = SBOX[x]
            val s2 = xtime(s)
            val s3 = s2 xor s
            (s2 shl 24) or (s shl 16) or (s shl 8) or s3
        }
        private val TE1 = IntArray(256) { TE0[it].rotateRight(8) }
        private val TE2 = IntArray(256) { TE0[it].rotateRight(16) }
        private val TE3 = IntArray(256) { TE0[it].rotateRight(24) }

        private fun rotl8(x: Int, shift: Int): Int = ((x shl shift) or (x ushr (8 - shift))) and 0xFF

        private fun xtime(x: Int): Int = ((x shl 1) xor (if (x and 0x80 != 0) 0x1B else 0)) and 0xFF

        internal fun readInt(b: ByteArray, offset: Int): Int =
            ((b[offset].toInt() and 0xFF) shl 24) or ((b[offset + 1].toInt() and 0xFF) shl 16) or
                ((b[offset + 2].toInt() and 0xFF) shl 8) or (b[offset + 3].toInt() and 0xFF)

        internal fun writeInt(b: ByteArray, offset: Int, value: Int) {
            b[offset] = (value ushr 24).toByte()
            b[offset + 1] = (value ushr 16).toByte()
            b[offset + 2] = (value ushr 8).toByte()
            b[offset + 3] = value.toByte()
        }
    }
}
