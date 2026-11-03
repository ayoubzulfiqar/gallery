package com.ayoubzulfiqar.gallery.core.crypto

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CryptoPrimitivesTest {

    @Test
    fun sha256MatchesFips180Vectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hash(ByteArray(0)).toHex()
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hash("abc".encodeToByteArray()).toHex()
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hash("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).toHex()
        )
    }

    @Test
    fun sha256IncrementalUpdatesMatchOneShot() {
        val data = Random(42).nextBytes(10_000)
        val incremental = Sha256()
        var offset = 0
        val chunkSizes = intArrayOf(1, 63, 64, 65, 127, 500, 3)
        var i = 0
        while (offset < data.size) {
            val size = minOf(chunkSizes[i++ % chunkSizes.size], data.size - offset)
            incremental.update(data, offset, size)
            offset += size
        }
        assertContentEquals(Sha256.hash(data), incremental.digest())
    }

    @Test
    fun hmacSha256MatchesRfc4231() {
        val key = ByteArray(20) { 0x0b }
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            HmacSha256(key).mac("Hi There".encodeToByteArray()).toHex()
        )
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            HmacSha256("Jefe".encodeToByteArray()).mac("what do ya want for nothing?".encodeToByteArray()).toHex()
        )
    }

    @Test
    fun pbkdf2MatchesPublishedVectors() {
        val password = "password".encodeToByteArray()
        val salt = "salt".encodeToByteArray()
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            Pbkdf2.hmacSha256(password, salt, 1, 32).toHex()
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            Pbkdf2.hmacSha256(password, salt, 2, 32).toHex()
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            Pbkdf2.hmacSha256(password, salt, 4096, 32).toHex()
        )
        assertEquals(
            "348c89dbcbd32b2f32d814b8116e84cf2b17347ebc1800181c4e2a1fb8dd53e1c635518c7dac47e9",
            Pbkdf2.hmacSha256(
                "passwordPASSWORDpassword".encodeToByteArray(),
                "saltSALTsaltSALTsaltSALTsaltSALTsalt".encodeToByteArray(),
                4096,
                40
            ).toHex()
        )
    }

    @Test
    fun hkdfMatchesRfc5869TestCase1() {
        val okm = Hkdf.sha256(
            ikm = ByteArray(22) { 0x0b },
            salt = "000102030405060708090a0b0c".hexToBytes(),
            info = "f0f1f2f3f4f5f6f7f8f9".hexToBytes(),
            length = 42
        )
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.toHex()
        )
    }

    @Test
    fun aesBlockMatchesFips197() {
        val plaintext = "00112233445566778899aabbccddeeff".hexToBytes()
        val vectors = listOf(
            "000102030405060708090a0b0c0d0e0f" to "69c4e0d86a7b0430d8cdb78070b4c55a",
            "000102030405060708090a0b0c0d0e0f1011121314151617" to "dda97ca4864cdfe06eaf70a0ec0d7191",
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" to "8ea2b7ca516745bfeafc49904b496089"
        )
        for ((key, expected) in vectors) {
            val out = ByteArray(16)
            AesEngine(key.hexToBytes()).encryptBlock(plaintext, 0, out, 0)
            assertEquals(expected, out.toHex(), "AES with ${key.length * 4}-bit key")
        }
        assertEquals(0x63, AesEngine.SBOX[0x00])
        assertEquals(0x7c, AesEngine.SBOX[0x01])
        assertEquals(0xed, AesEngine.SBOX[0x53])
        assertEquals(0x16, AesEngine.SBOX[0xff])
    }

    @Test
    fun gcmMatchesNistTestCases() {
        val gcm = PureAesGcm()
        // Test case 1 & 2 (AES-128)
        assertEquals(
            "58e2fccefa7e3061367f1d57a4e7455a",
            gcm.seal(ByteArray(16), ByteArray(12), ByteArray(0)).toHex()
        )
        assertEquals(
            "0388dace60b6a392f328c2b971b2fe78" + "ab6e47d42cec13bdf53a67b21257bddf",
            gcm.seal(ByteArray(16), ByteArray(12), ByteArray(16)).toHex()
        )
        // Test case 13 & 14 (AES-256)
        assertEquals(
            "530f8afbc74536b9a963b4f1c4cb738b",
            gcm.seal(ByteArray(32), ByteArray(12), ByteArray(0)).toHex()
        )
        assertEquals(
            "cea7403d4d606b6e074ec5d3baf39d18" + "d0d1c8a799996bf0265b98b5d48ab919",
            gcm.seal(ByteArray(32), ByteArray(12), ByteArray(16)).toHex()
        )
        // Test case 15 & 16 (AES-256 with data, and with AAD + partial block)
        val key = "feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308".hexToBytes()
        val iv = "cafebabefacedbaddecaf888".hexToBytes()
        val pt = ("d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
            "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255").hexToBytes()
        assertEquals(
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
                "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662898015ad" +
                "b094dac5d93471bdec1a502270e3cc6c",
            gcm.seal(key, iv, pt).toHex()
        )
        val aad = "feedfacedeadbeeffeedfacedeadbeefabaddad2".hexToBytes()
        val sealed = gcm.seal(key, iv, pt.copyOf(60), aad)
        assertEquals(
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
                "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662" +
                "76fc6ece0f4e1768cddf8853bb2d551b",
            sealed.toHex()
        )
        assertContentEquals(pt.copyOf(60), gcm.open(key, iv, sealed, aad))
    }

    @Test
    fun ghashTableMatchesBitSerialReference() {
        val random = Random(7)
        repeat(200) {
            val h = random.nextBytes(16)
            val x = random.nextBytes(16)
            val ghash = PureAesGcm.GHash(h)
            ghash.updateBlock(x, 0)
            assertContentEquals(PureAesGcm.gfMultiplyReference(x, h), ghash.result())
        }
    }

    @Test
    fun gcmRejectsEveryKindOfTampering() {
        val gcm = PureAesGcm()
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12) { (it * 3).toByte() }
        val aad = "header".encodeToByteArray()
        val sealed = gcm.seal(key, nonce, "Highly sensitive private photo".encodeToByteArray(), aad)

        for (index in sealed.indices) {
            val corrupted = sealed.copyOf()
            corrupted[index] = (corrupted[index].toInt() xor 0x01).toByte()
            assertFailsWith<AeadException> { gcm.open(key, nonce, corrupted, aad) }
        }
        assertFailsWith<AeadException> { gcm.open(key, nonce, sealed, "other".encodeToByteArray()) }
        assertFailsWith<AeadException> { gcm.open(key.copyOf().also { it[0] = 9 }, nonce, sealed, aad) }
        assertFailsWith<AeadException> { gcm.open(key, nonce, sealed.copyOf(sealed.size - 1), aad) }
        assertFailsWith<AeadException> { gcm.open(key, nonce, ByteArray(4), aad) }
    }

    @Test
    fun aesGcmCipherRoundTripsWithRandomNonces() {
        val cipher = AesGcmCipher()
        val key = secureRandomBytes(32)
        val payload = "Highly sensitive private photo byte payload".encodeToByteArray()
        val first = cipher.encrypt(payload, key)
        val second = cipher.encrypt(payload, key)
        assertFalse(first.contentEquals(second), "Nonces must be unique per encryption")
        assertContentEquals(payload, cipher.decrypt(first, key))

        first[first.size - 1] = (first[first.size - 1].toInt() xor 0xFF).toByte()
        assertFailsWith<AeadException> { cipher.decrypt(first, key) }
    }

    @Test
    fun secureRandomProducesDistinctOutput() {
        val a = secureRandomBytes(32)
        val b = secureRandomBytes(32)
        assertEquals(32, a.size)
        assertFalse(a.contentEquals(b))
        assertTrue(secureRandomBytes(0).isEmpty())
    }

    @Test
    fun constantTimeEqualsBehaves() {
        assertTrue(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2, 3)))
    }
}
