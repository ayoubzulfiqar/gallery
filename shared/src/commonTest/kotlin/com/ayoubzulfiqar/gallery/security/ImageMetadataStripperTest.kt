package com.ayoubzulfiqar.gallery.security

import com.ayoubzulfiqar.gallery.core.io.ByteArraySink
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageMetadataStripperTest {

    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (length ushr 8).toByte(), length.toByte()) + payload
    }

    private fun ascii(text: String) = text.encodeToByteArray()

    private fun be16(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())
    private fun be32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun entry(tag: Int, type: Int, count: Int, value: ByteArray) = be16(tag) + be16(type) + be32(count) + value

    /** Big-endian TIFF with orientation, an EXIF sub-IFD (1 tag) and a GPS sub-IFD (2 tags). */
    private fun tiff(orientation: Int): ByteArray {
        val ifd0 = be16(3) +
            entry(0x0112, 3, 1, be16(orientation) + be16(0)) +
            entry(0x8769, 4, 1, be32(50)) +
            entry(0x8825, 4, 1, be32(68)) +
            be32(0)
        val exifIfd = be16(1) + entry(0x829A, 4, 1, be32(1)) + be32(0)
        val gpsIfd = be16(2) + entry(0x0001, 2, 2, ascii("N") + byteArrayOf(0, 0, 0)) +
            entry(0x0002, 5, 3, be32(200)) + be32(0)
        return ascii("MM") + be16(42) + be32(8) + ifd0 + exifIfd + gpsIfd
    }

    private val entropyData = byteArrayOf(0x12, 0x34, 0xFF.toByte(), 0x00, 0x56, 0xFF.toByte(), 0xD0.toByte(), 0x78, 0x9A.toByte())
    private val icc = segment(0xE2, ascii("ICC_PROFILE") + byteArrayOf(0, 1, 1) + ByteArray(20) { 7 })
    private val dqt = segment(0xDB, ByteArray(65) { it.toByte() })
    private val sof = segment(0xC0, byteArrayOf(8, 0, 16, 0, 16, 1, 1, 0x11, 0))
    private val dht = segment(0xC4, ByteArray(20) { 1 })
    private val sos = segment(0xDA, byteArrayOf(1, 1, 0, 0, 63, 0))

    private fun buildJpeg(orientation: Int, trailer: ByteArray = ByteArray(0)): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
            segment(0xE1, ascii("Exif") + byteArrayOf(0, 0) + tiff(orientation)) +
            segment(0xE1, ascii("http://ns.adobe.com/xap/1.0/") + byteArrayOf(0) + ascii("<x:xmpmeta GPSLatitude='1'/>")) +
            icc +
            segment(0xED, ascii("Photoshop 3.0") + byteArrayOf(0) + ascii("IPTC by-line")) +
            segment(0xFE, ascii("secret comment")) +
            dqt + sof + dht + sos + entropyData +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte()) + trailer

    private fun containsSequence(haystack: ByteArray, needle: ByteArray): Boolean =
        (0..haystack.size - needle.size).any { start -> needle.indices.all { haystack[start + it] == needle[it] } }

    @Test
    fun jpegStripRemovesMetadataButKeepsImageDataAndOrientation() {
        val original = buildJpeg(orientation = 6, trailer = ascii("MOTION-PHOTO-VIDEO-BYTES"))
        val (clean, report) = stripImageMetadata(original)

        assertTrue(report.removedLocation)
        assertEquals(6, report.removedExifTags)
        assertEquals(6, report.preservedOrientation)
        assertEquals(24, report.removedTrailingBytes)
        assertEquals(5, report.removedBlocks) // EXIF, XMP, IPTC, COM and the trailer

        val minimalExif = segment(
            0xE1,
            ascii("Exif") + byteArrayOf(0, 0) + ascii("MM") + be16(42) + be32(8) + be16(1) +
                entry(0x0112, 3, 1, be16(6) + be16(0)) + be32(0)
        )
        val expected = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + minimalExif + icc + dqt + sof + dht + sos +
            entropyData + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        assertContentEquals(expected, clean)
        assertFalse(containsSequence(clean, ascii("GPSLatitude")))
        assertFalse(containsSequence(clean, ascii("secret comment")))
        assertFalse(containsSequence(clean, ascii("IPTC")))
    }

    @Test
    fun jpegWithDefaultOrientationGetsNoExifAtAll() {
        val (clean, report) = stripImageMetadata(buildJpeg(orientation = 1))
        assertEquals(1, report.preservedOrientation)
        assertFalse(containsSequence(clean, ascii("Exif")))
        assertEquals(0L, report.removedTrailingBytes)
    }

    @Test
    fun progressiveJpegMarkersBetweenScansAreFiltered() {
        val secondSos = segment(0xDA, byteArrayOf(1, 1, 0, 1, 63, 0))
        val sneakyApp1 = segment(0xE1, ascii("Exif") + byteArrayOf(0, 0) + tiff(1))
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + dqt + sof + dht + sos + entropyData +
            dht + sneakyApp1 + secondSos + entropyData + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val (clean, _) = stripImageMetadata(jpeg)
        val expected = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + dqt + sof + dht + sos + entropyData +
            dht + secondSos + entropyData + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        assertContentEquals(expected, clean)
    }

    @Test
    fun strippingIsIdempotent() {
        val (once, _) = stripImageMetadata(buildJpeg(orientation = 8))
        val (twice, secondReport) = stripImageMetadata(once)
        assertContentEquals(once, twice)
        assertFalse(secondReport.removedLocation)
        assertEquals(8, secondReport.preservedOrientation)
    }

    private fun pngChunk(type: String, data: ByteArray) = be32(data.size) + ascii(type) + data + be32(0x01020304)

    @Test
    fun pngStripRemovesTextAndExifChunks() {
        val ihdr = pngChunk("IHDR", ByteArray(13) { 1 })
        val srgb = pngChunk("sRGB", byteArrayOf(0))
        val idat = pngChunk("IDAT", ByteArray(30) { 9 })
        val iend = pngChunk("IEND", ByteArray(0))
        val png = PngMetadataStripper.SIGNATURE + ihdr +
            pngChunk("tEXt", ascii("Author\u0000Jane")) +
            pngChunk("eXIf", tiff(3)) +
            srgb +
            pngChunk("iTXt", ascii("XML:com.adobe.xmp\u0000\u0000\u0000\u0000\u0000<gps/>")) +
            pngChunk("tIME", ByteArray(7)) +
            idat + iend + ascii("trailer")
        val (clean, report) = stripImageMetadata(png)
        assertContentEquals(PngMetadataStripper.SIGNATURE + ihdr + srgb + idat + iend, clean)
        assertEquals(5, report.removedBlocks)
        assertTrue(report.removedLocation)
        assertEquals(7L, report.removedTrailingBytes)
    }

    @Test
    fun rejectsUnsupportedOrTruncatedInput() {
        assertFailsWith<UnsupportedImageFormatException> { stripImageMetadata(ascii("GIF89a....")) }
        assertFailsWith<UnsupportedImageFormatException> {
            JpegMetadataStripper.strip(com.ayoubzulfiqar.gallery.core.io.ByteArraySource(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0x10)), ByteArraySink())
        }
        assertFailsWith<UnsupportedImageFormatException> {
            stripImageMetadata(PngMetadataStripper.SIGNATURE + pngChunk("IHDR", ByteArray(13)))
        }
    }

    @Test
    fun truncatedScanStillProducesTerminatedJpeg() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + dqt + sof + dht + sos + entropyData
        val (clean, _) = stripImageMetadata(jpeg)
        assertEquals(0xFF.toByte(), clean[clean.size - 2])
        assertEquals(0xD9.toByte(), clean[clean.size - 1])
    }
}
