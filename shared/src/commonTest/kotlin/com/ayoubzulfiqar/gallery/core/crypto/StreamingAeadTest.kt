package com.ayoubzulfiqar.gallery.core.crypto

import com.ayoubzulfiqar.gallery.core.io.ByteArraySink
import com.ayoubzulfiqar.gallery.core.io.ByteArraySource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StreamingAeadTest {

    private val key = ByteArray(32) { (it * 7).toByte() }
    private val aad = "blob:entry-1".encodeToByteArray()

    private fun encrypt(plain: ByteArray, streaming: StreamingAead, associatedData: ByteArray = aad): ByteArray {
        val sink = ByteArraySink()
        streaming.encrypt(key, associatedData, ByteArraySource(plain), sink)
        return sink.toByteArray()
    }

    private fun decrypt(cipher: ByteArray, streaming: StreamingAead, associatedData: ByteArray = aad): ByteArray {
        val sink = ByteArraySink()
        streaming.decrypt(key, associatedData, ByteArraySource(cipher), sink)
        return sink.toByteArray()
    }

    @Test
    fun roundTripsAcrossSegmentBoundaries() {
        val segment = 64
        val streaming = StreamingAead(PureAesGcm(), segmentSize = segment)
        for (size in listOf(0, 1, segment - 1, segment, segment + 1, segment * 3, segment * 3 + 17)) {
            val plain = Random(size).nextBytes(size)
            val cipher = encrypt(plain, streaming)
            val segments = if (size == 0) 1 else (size + segment - 1) / segment
            assertEquals(StreamingAead.HEADER_SIZE + size + segments * Aead.TAG_SIZE, cipher.size, "size=$size")
            assertContentEquals(plain, decrypt(cipher, streaming), "size=$size")
        }
    }

    @Test
    fun platformAeadInteroperatesWithPortableEngine() {
        val plain = Random(1).nextBytes(1000)
        val portable = StreamingAead(PureAesGcm(), segmentSize = 128)
        val platform = StreamingAead(platformAead(), segmentSize = 128)
        assertContentEquals(plain, decrypt(encrypt(plain, portable), platform))
        assertContentEquals(plain, decrypt(encrypt(plain, platform), portable))
    }

    @Test
    fun detectsTruncationAtSegmentBoundary() {
        val streaming = StreamingAead(PureAesGcm(), segmentSize = 32)
        val cipher = encrypt(Random(3).nextBytes(32 * 4), streaming)
        val oneSegment = 32 + Aead.TAG_SIZE
        val truncated = cipher.copyOf(cipher.size - oneSegment)
        assertFailsWith<AeadException> { decrypt(truncated, streaming) }
    }

    @Test
    fun detectsSegmentReorderingAndAppending() {
        val streaming = StreamingAead(PureAesGcm(), segmentSize = 32)
        val cipher = encrypt(Random(4).nextBytes(32 * 3 + 5), streaming)
        val seg = 32 + Aead.TAG_SIZE
        val h = StreamingAead.HEADER_SIZE
        val swapped = cipher.copyOf()
        cipher.copyInto(swapped, h, h + seg, h + 2 * seg)
        cipher.copyInto(swapped, h + seg, h, h + seg)
        assertFailsWith<AeadException> { decrypt(swapped, streaming) }
        assertFailsWith<AeadException> { decrypt(cipher + ByteArray(40), streaming) }
    }

    @Test
    fun bindsCiphertextToAssociatedData() {
        val streaming = StreamingAead(PureAesGcm(), segmentSize = 64)
        val cipher = encrypt("secret".encodeToByteArray(), streaming)
        assertFailsWith<AeadException> { decrypt(cipher, streaming, "blob:entry-2".encodeToByteArray()) }
    }

    @Test
    fun rejectsCorruptedHeaderAndBody() {
        val streaming = StreamingAead(PureAesGcm(), segmentSize = 64)
        val cipher = encrypt(Random(9).nextBytes(200), streaming)
        for (index in listOf(0, 4, 6, 12, 31, 40, cipher.size - 1)) {
            val corrupted = cipher.copyOf()
            corrupted[index] = (corrupted[index].toInt() xor 0x20).toByte()
            assertFailsWith<AeadException>("byte $index") { decrypt(corrupted, streaming) }
        }
        assertFailsWith<AeadException> { decrypt(cipher.copyOf(10), streaming) }
    }
}
