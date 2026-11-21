package com.ayoubzulfiqar.gallery.security

import com.ayoubzulfiqar.gallery.core.io.ByteArraySink
import com.ayoubzulfiqar.gallery.core.io.ByteArraySource
import com.ayoubzulfiqar.gallery.core.io.readFully
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * GIFs can carry XMP in application extensions and notes in comment extensions.
 * Sanitizing must drop those while keeping animation intact (NETSCAPE looping,
 * graphic control extensions, image data).
 */
class GifMetadataStripperTest {

    private fun subBlocks(vararg chunks: ByteArray): ByteArray {
        val out = ArrayList<Byte>()
        chunks.forEach { chunk ->
            out.add(chunk.size.toByte())
            chunk.forEach { out.add(it) }
        }
        out.add(0) // block terminator
        return out.toByteArray()
    }

    private fun appExtension(identifier: ByteArray, data: ByteArray): ByteArray =
        byteArrayOf(0x21, 0xFF.toByte(), 11) + identifier + subBlocks(data)

    private fun commentExtension(text: String): ByteArray =
        byteArrayOf(0x21, 0xFE.toByte()) + subBlocks(text.encodeToByteArray())

    private fun graphicControl(): ByteArray =
        byteArrayOf(0x21, 0xF9.toByte(), 4, 0x04, 10, 0, 0, 0)

    private fun imageBlock(): ByteArray =
        byteArrayOf(0x2C, 0, 0, 0, 0, 2, 0, 2, 0, 0) + // descriptor, 2x2, no LCT
            byteArrayOf(2) + subBlocks(byteArrayOf(0x84.toByte(), 0x1C, 0x0F)) // LZW min code + data

    private fun gif(vararg blocks: ByteArray): ByteArray =
        "GIF89a".encodeToByteArray() +
            byteArrayOf(2, 0, 2, 0, 0, 0, 0) + // 2x2 logical screen, no GCT
            blocks.fold(ByteArray(0)) { acc, b -> acc + b } +
            byteArrayOf(0x3B)

    private val netscape = appExtension("NETSCAPE2.0".encodeToByteArray(), byteArrayOf(1, 0, 0))
    private val xmp = appExtension("XMP Data001".encodeToByteArray(), "<x:xmpmeta>secret</x:xmpmeta>".encodeToByteArray())

    private fun strip(bytes: ByteArray): Pair<ByteArray, StripReport> {
        val sink = ByteArraySink()
        val report = GifMetadataStripper.strip(ByteArraySource(bytes), sink)
        return sink.toByteArray() to report
    }

    @Test
    fun removesCommentsAndXmpButKeepsLoopingAndPixels() {
        val image = imageBlock()
        val source = gif(commentExtension("private note"), netscape, xmp, graphicControl(), image)
        val (clean, report) = strip(source)
        assertEquals(2, report.removedBlocks)
        assertTrue(clean.contains(netscape), "NETSCAPE looping must survive")
        assertTrue(clean.contains(graphicControl()), "animation timing must survive")
        assertTrue(clean.contains(image), "image data must be untouched")
        assertTrue(clean.contains("GIF89a".encodeToByteArray()))
        assertTrue(clean.last() == 0x3B.toByte())
        assertFalse(clean.contains("private note".encodeToByteArray()))
        assertFalse(clean.contains("secret".encodeToByteArray()))
    }

    @Test
    fun strippingIsIdempotent() {
        val source = gif(commentExtension("note"), netscape, graphicControl(), imageBlock())
        val (once, _) = strip(source)
        val (twice, report) = strip(once)
        assertContentEquals(once, twice)
        assertEquals(0, report.removedBlocks)
    }

    @Test
    fun cleanGifPassesThroughUnchanged() {
        val source = gif(netscape, graphicControl(), imageBlock())
        val (clean, report) = strip(source)
        assertContentEquals(source, clean)
        assertEquals(0, report.removedBlocks)
    }

    @Test
    fun rejectsTruncatedOrNonGifInput() {
        assertFailsWith<UnsupportedImageFormatException> { strip("not a gif".encodeToByteArray()) }
        assertFailsWith<UnsupportedImageFormatException> { strip("GIF89a".encodeToByteArray() + byteArrayOf(1, 2)) }
    }

    private fun ByteArray.contains(other: ByteArray): Boolean {
        if (other.isEmpty() || other.size > size) return false
        outer@ for (start in 0..size - other.size) {
            for (i in other.indices) if (this[start + i] != other[i]) continue@outer
            return true
        }
        return false
    }
}