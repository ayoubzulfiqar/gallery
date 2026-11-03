package com.ayoubzulfiqar.gallery.core.crypto

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Differential tests: the portable implementations (shipped on iOS) must agree bit-for-bit
 * with the JDK's independent, audited implementations on random inputs.
 */
class JcaCrossCheckTest {

    @Test
    fun portableAesGcmAgreesWithJca() {
        val random = Random(2024)
        val portable = PureAesGcm()
        repeat(300) { round ->
            val key = random.nextBytes(if (round % 3 == 0) 16 else 32)
            val nonce = random.nextBytes(12)
            val plain = random.nextBytes(random.nextInt(0, 300))
            val aad = random.nextBytes(random.nextInt(0, 40))
            val viaJca = JcaAesGcm.seal(key, nonce, plain, aad)
            val viaPortable = portable.seal(key, nonce, plain, aad)
            assertContentEquals(viaJca, viaPortable, "round $round")
            assertContentEquals(plain, portable.open(key, nonce, viaJca, aad))
            assertContentEquals(plain, JcaAesGcm.open(key, nonce, viaPortable, aad))
        }
    }

    @Test
    fun portableHashesAgreeWithJca() {
        val random = Random(99)
        repeat(100) {
            val data = random.nextBytes(random.nextInt(0, 2000))
            assertContentEquals(MessageDigest.getInstance("SHA-256").digest(data), Sha256.hash(data))
            val key = random.nextBytes(random.nextInt(1, 130))
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
            assertContentEquals(mac.doFinal(data), HmacSha256(key).mac(data))
        }
    }

    @Test
    fun portablePbkdf2AgreesWithJcaForNumericPins() {
        val salt = Random(5).nextBytes(16)
        for (pin in listOf("0000", "1234", "987654", "31415926")) {
            val spec = PBEKeySpec(pin.toCharArray(), salt, 2000, 256)
            val expected = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            assertContentEquals(expected, Pbkdf2.hmacSha256(pin.encodeToByteArray(), salt, 2000, 32))
        }
    }
}
