package com.ayoubzulfiqar.gallery.security

import com.ayoubzulfiqar.gallery.core.io.ByteArraySink
import com.ayoubzulfiqar.gallery.core.io.ByteSink
import com.ayoubzulfiqar.gallery.core.io.ByteSource
import com.ayoubzulfiqar.gallery.core.io.readFully

/** Outcome of a lossless metadata strip. */
data class StripReport(
    /** Metadata containers removed (APPn/COM segments, text/EXIF chunks, trailers). */
    val removedBlocks: Int,
    /** Individual EXIF tags that were present in removed EXIF data. */
    val removedExifTags: Int,
    /** True when GPS/location data was present and removed. */
    val removedLocation: Boolean,
    /** EXIF orientation (1–8) preserved in the output so the picture is not rotated. */
    val preservedOrientation: Int,
    /** Bytes of data that followed the end of the image (e.g. motion-photo video) and were dropped. */
    val removedTrailingBytes: Long
)

class UnsupportedImageFormatException(message: String) : IllegalArgumentException(message)

enum class ImageContainer { JPEG, PNG, OTHER }

object ImageContainerSniffer {
    fun sniff(head: ByteArray): ImageContainer {
        if (head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()) {
            return ImageContainer.JPEG
        }
        if (head.size >= 8 && head.copyOf(8).contentEquals(PngMetadataStripper.SIGNATURE)) return ImageContainer.PNG
        return ImageContainer.OTHER
    }
}

/**
 * Removes every metadata segment from a baseline or progressive JPEG without touching image data:
 * APP1 (EXIF/XMP), APP3–APP15 (IPTC/Photoshop, maker data), COM, non-ICC APP2 (MPF) and any trailer
 * after EOI. ICC profiles (APP2) and JFIF/Adobe colour segments are kept so colours stay correct.
 * If the source had a non-default EXIF orientation, a minimal EXIF block containing only that tag is
 * written so viewers still display the picture upright.
 */
object JpegMetadataStripper {

    fun strip(source: ByteSource, sink: ByteSink): StripReport {
        val input = BufferedReader(source)
        if (input.read() != 0xFF || input.read() != 0xD8) throw UnsupportedImageFormatException("Not a JPEG file")

        val keptHeaderSegments = mutableListOf<ByteArray>()
        var removedBlocks = 0
        var exifInfo = ExifTiffParser.Info.EMPTY
        var hasLeadingJfif = false

        // Phase 1: header segments up to SOS are buffered (they are small) so the decision about the
        // orientation block can be made before anything is written.
        var sosSegment: ByteArray? = null
        while (sosSegment == null) {
            val marker = input.nextMarker() ?: throw UnsupportedImageFormatException("Truncated JPEG header")
            if (marker == 0xD9) throw UnsupportedImageFormatException("JPEG has no image data")
            if (isStandalone(marker)) {
                keptHeaderSegments += byteArrayOf(0xFF.toByte(), marker.toByte())
                continue
            }
            val segment = input.readSegment(marker)
            val payloadOffset = 4
            when {
                marker == 0xDA -> sosSegment = segment
                marker == 0xE0 -> {
                    if (keptHeaderSegments.isEmpty() && segment.startsWithAscii(payloadOffset, "JFIF\u0000")) hasLeadingJfif = true
                    keptHeaderSegments += segment
                }
                marker == 0xE1 -> {
                    if (segment.startsWithAscii(payloadOffset, "Exif\u0000\u0000")) {
                        exifInfo = exifInfo.merge(ExifTiffParser.parse(segment, payloadOffset + 6))
                    }
                    removedBlocks++
                }
                marker == 0xE2 -> {
                    if (segment.startsWithAscii(payloadOffset, "ICC_PROFILE\u0000")) keptHeaderSegments += segment else removedBlocks++
                }
                marker == 0xEE -> keptHeaderSegments += segment // Adobe colour transform
                marker in 0xE3..0xEF || marker == 0xFE -> removedBlocks++
                else -> keptHeaderSegments += segment // DQT, DHT, SOFn, DRI, DNL, ...
            }
        }

        // Phase 2: write SOI, optional JFIF, orientation block, remaining header and the scan.
        sink.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        var headerIndex = 0
        if (hasLeadingJfif) {
            sink.write(keptHeaderSegments[0])
            headerIndex = 1
        }
        val orientation = exifInfo.orientation.takeIf { it in 2..8 } ?: 1
        if (orientation != 1) sink.write(minimalOrientationExif(orientation))
        for (i in headerIndex until keptHeaderSegments.size) sink.write(keptHeaderSegments[i])
        sink.write(sosSegment)

        // Phase 3: entropy-coded data. Markers between progressive scans are filtered like headers.
        val trailingBytes = copyScanData(input, sink) { removedBlocks++ }
        if (trailingBytes > 0) removedBlocks++
        sink.flush()
        return StripReport(
            removedBlocks = removedBlocks,
            removedExifTags = exifInfo.tagCount,
            removedLocation = exifInfo.hasGps,
            preservedOrientation = orientation,
            removedTrailingBytes = trailingBytes
        )
    }

    private fun copyScanData(input: BufferedReader, sink: ByteSink, onRemoved: () -> Unit): Long {
        val out = SinkBuffer(sink)
        while (true) {
            val b = input.read()
            if (b < 0) {
                // Missing EOI: tolerate (common for truncated camera files) and terminate properly.
                out.write(0xFF); out.write(0xD9)
                out.flush()
                return 0
            }
            if (b != 0xFF) {
                out.write(b)
                continue
            }
            var next = input.read()
            while (next == 0xFF) next = input.read() // fill bytes
            when {
                next < 0 -> {
                    out.write(0xFF); out.write(0xD9)
                    out.flush()
                    return 0
                }
                next == 0x00 || next in 0xD0..0xD7 -> {
                    out.write(0xFF); out.write(next)
                }
                next == 0xD9 -> {
                    out.write(0xFF); out.write(0xD9)
                    out.flush()
                    return input.drainCount()
                }
                isStandalone(next) -> {
                    out.write(0xFF); out.write(next)
                }
                else -> {
                    val segment = input.readSegment(next)
                    if (next in 0xE1..0xEF || next == 0xFE || (next == 0xE2 && !segment.startsWithAscii(4, "ICC_PROFILE\u0000"))) {
                        onRemoved()
                    } else {
                        out.writeAll(segment)
                    }
                }
            }
        }
    }

    private fun isStandalone(marker: Int) = marker == 0x01 || marker in 0xD0..0xD8

    private fun minimalOrientationExif(orientation: Int): ByteArray {
        val payload = byteArrayOf(
            'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0,
            'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // TIFF header, IFD0 at 8
            0x00, 0x01, // one entry
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, orientation.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00 // no next IFD
        )
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length ushr 8).toByte(), length.toByte()) + payload
    }

    private class BufferedReader(private val source: ByteSource) {
        private val buffer = ByteArray(64 * 1024)
        private var position = 0
        private var limit = 0

        fun read(): Int {
            if (position == limit) {
                limit = source.read(buffer, 0, buffer.size)
                position = 0
                if (limit <= 0) {
                    limit = 0
                    return -1
                }
            }
            return buffer[position++].toInt() and 0xFF
        }

        /** Skips to the next marker in the header area, returning its code. */
        fun nextMarker(): Int? {
            var b = read()
            while (b >= 0 && b != 0xFF) b = read() // tolerate garbage between segments
            if (b < 0) return null
            var marker = read()
            while (marker == 0xFF) marker = read()
            return marker.takeIf { it >= 0 }
        }

        /** Reads a length-prefixed segment and returns it including the 0xFF marker prefix. */
        fun readSegment(marker: Int): ByteArray {
            val hi = read()
            val lo = read()
            if (hi < 0 || lo < 0) throw UnsupportedImageFormatException("Truncated JPEG segment")
            val length = (hi shl 8) or lo
            if (length < 2) throw UnsupportedImageFormatException("Invalid JPEG segment length")
            val segment = ByteArray(length + 2)
            segment[0] = 0xFF.toByte()
            segment[1] = marker.toByte()
            segment[2] = hi.toByte()
            segment[3] = lo.toByte()
            var filled = 4
            while (filled < segment.size) {
                if (position == limit) {
                    val read = source.readFully(segment, filled, segment.size - filled)
                    if (read < segment.size - filled) throw UnsupportedImageFormatException("Truncated JPEG segment")
                    filled = segment.size
                } else {
                    val count = minOf(limit - position, segment.size - filled)
                    buffer.copyInto(segment, filled, position, position + count)
                    position += count
                    filled += count
                }
            }
            return segment
        }

        fun drainCount(): Long {
            var count = (limit - position).toLong()
            position = limit
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read < 0) break
                count += read
            }
            limit = 0
            position = 0
            return count
        }
    }

    private class SinkBuffer(private val sink: ByteSink) {
        private val buffer = ByteArray(64 * 1024)
        private var size = 0

        fun write(b: Int) {
            if (size == buffer.size) flush()
            buffer[size++] = b.toByte()
        }

        fun writeAll(bytes: ByteArray) {
            flush()
            sink.write(bytes)
        }

        fun flush() {
            if (size > 0) sink.write(buffer, 0, size)
            size = 0
        }
    }
}

/**
 * Removes textual and EXIF chunks (tEXt, zTXt, iTXt/XMP, eXIf, tIME and any unknown ancillary
 * chunk) from a PNG/APNG, keeping only chunks needed to render the image exactly.
 */
object PngMetadataStripper {
    val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private val keptAncillary = setOf(
        "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "bKGD", "pHYs", "acTL", "fcTL", "fdAT", "cICP", "mDCv", "cLLi"
    )

    fun strip(source: ByteSource, sink: ByteSink): StripReport {
        val signature = ByteArray(8)
        if (source.readFully(signature) != 8 || !signature.contentEquals(SIGNATURE)) {
            throw UnsupportedImageFormatException("Not a PNG file")
        }
        sink.write(signature)
        var removed = 0
        var exif = ExifTiffParser.Info.EMPTY
        val header = ByteArray(8)
        val copyBuffer = ByteArray(64 * 1024)
        while (true) {
            if (source.readFully(header) != 8) throw UnsupportedImageFormatException("Truncated PNG (missing IEND)")
            val length = ((header[0].toLong() and 0xFF) shl 24) or ((header[1].toLong() and 0xFF) shl 16) or
                ((header[2].toLong() and 0xFF) shl 8) or (header[3].toLong() and 0xFF)
            if (length > Int.MAX_VALUE) throw UnsupportedImageFormatException("Invalid PNG chunk length")
            val type = header.copyOfRange(4, 8).decodeToString()
            val isCritical = type[0].isUpperCase()
            val keep = isCritical || type in keptAncillary
            val remaining = length + 4 // data + CRC
            if (type == "eXIf" && length in 8..(4 * 1024 * 1024)) {
                val data = ByteArray(length.toInt())
                if (source.readFully(data) != data.size) throw UnsupportedImageFormatException("Truncated PNG chunk")
                exif = exif.merge(ExifTiffParser.parse(data, 0))
                skip(source, 4, copyBuffer)
                removed++
            } else if (keep) {
                sink.write(header)
                copy(source, sink, remaining, copyBuffer)
            } else {
                skip(source, remaining, copyBuffer)
                removed++
            }
            if (type == "IEND") break
        }
        var trailing = 0L
        while (true) {
            val read = source.read(copyBuffer, 0, copyBuffer.size)
            if (read < 0) break
            trailing += read
        }
        if (trailing > 0) removed++
        sink.flush()
        return StripReport(removed, exif.tagCount, exif.hasGps, preservedOrientation = 1, removedTrailingBytes = trailing)
    }

    private fun copy(source: ByteSource, sink: ByteSink, count: Long, buffer: ByteArray) {
        var left = count
        while (left > 0) {
            val read = source.readFully(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read <= 0) throw UnsupportedImageFormatException("Truncated PNG chunk")
            sink.write(buffer, 0, read)
            left -= read
        }
    }

    private fun skip(source: ByteSource, count: Long, buffer: ByteArray) {
        var left = count
        while (left > 0) {
            val read = source.readFully(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read <= 0) throw UnsupportedImageFormatException("Truncated PNG chunk")
            left -= read
        }
    }
}

/** Defensive TIFF/EXIF reader extracting what the strip report needs. Never throws. */
internal object ExifTiffParser {
    data class Info(val orientation: Int, val hasGps: Boolean, val tagCount: Int) {
        fun merge(other: Info) = Info(
            orientation = if (orientation in 2..8) orientation else other.orientation,
            hasGps = hasGps || other.hasGps,
            tagCount = tagCount + other.tagCount
        )

        companion object {
            val EMPTY = Info(orientation = 1, hasGps = false, tagCount = 0)
        }
    }

    private const val TAG_ORIENTATION = 0x0112
    private const val TAG_EXIF_IFD = 0x8769
    private const val TAG_GPS_IFD = 0x8825

    fun parse(data: ByteArray, tiffStart: Int): Info {
        return try {
            parseUnsafe(data, tiffStart)
        } catch (_: IndexOutOfBoundsException) {
            Info.EMPTY
        }
    }

    private fun parseUnsafe(data: ByteArray, base: Int): Info {
        if (data.size < base + 8) return Info.EMPTY
        val littleEndian = when {
            data[base] == 'I'.code.toByte() && data[base + 1] == 'I'.code.toByte() -> true
            data[base] == 'M'.code.toByte() && data[base + 1] == 'M'.code.toByte() -> false
            else -> return Info.EMPTY
        }

        fun u16(offset: Int): Int {
            val a = data[base + offset].toInt() and 0xFF
            val b = data[base + offset + 1].toInt() and 0xFF
            return if (littleEndian) (b shl 8) or a else (a shl 8) or b
        }

        fun u32(offset: Int): Long {
            val v0 = data[base + offset].toLong() and 0xFF
            val v1 = data[base + offset + 1].toLong() and 0xFF
            val v2 = data[base + offset + 2].toLong() and 0xFF
            val v3 = data[base + offset + 3].toLong() and 0xFF
            return if (littleEndian) (v3 shl 24) or (v2 shl 16) or (v1 shl 8) or v0 else (v0 shl 24) or (v1 shl 16) or (v2 shl 8) or v3
        }

        if (u16(2) != 42) return Info.EMPTY
        var orientation = 1
        var hasGps = false
        var tags = 0
        val visited = HashSet<Long>()

        fun walk(offset: Long, depth: Int, isIfd0: Boolean) {
            if (depth > 4 || offset <= 0 || offset > data.size - base - 2 || !visited.add(offset)) return
            val count = u16(offset.toInt())
            if (count > 1000) return
            tags += count
            for (i in 0 until count) {
                val entry = offset.toInt() + 2 + i * 12
                if (base + entry + 12 > data.size) return
                val tag = u16(entry)
                when (tag) {
                    TAG_ORIENTATION -> if (isIfd0) orientation = u16(entry + 8).takeIf { it in 1..8 } ?: 1
                    TAG_EXIF_IFD -> walk(u32(entry + 8), depth + 1, isIfd0 = false)
                    TAG_GPS_IFD -> {
                        hasGps = true
                        walk(u32(entry + 8), depth + 1, isIfd0 = false)
                    }
                }
            }
            if (isIfd0) {
                val nextOffsetPos = offset.toInt() + 2 + count * 12
                if (base + nextOffsetPos + 4 <= data.size) walk(u32(nextOffsetPos), depth + 1, isIfd0 = false)
            }
        }

        walk(u32(4), 0, isIfd0 = true)
        return Info(orientation, hasGps, tags)
    }
}

private fun ByteArray.startsWithAscii(offset: Int, text: String): Boolean {
    if (offset + text.length > size) return false
    for (i in text.indices) if (this[offset + i] != text[i].code.toByte()) return false
    return true
}

/** Convenience for in-memory use and tests. */
fun stripImageMetadata(bytes: ByteArray): Pair<ByteArray, StripReport> {
    val sink = ByteArraySink(bytes.size)
    val source = com.ayoubzulfiqar.gallery.core.io.ByteArraySource(bytes)
    val report = when (ImageContainerSniffer.sniff(bytes.copyOf(minOf(bytes.size, 16)))) {
        ImageContainer.JPEG -> JpegMetadataStripper.strip(source, sink)
        ImageContainer.PNG -> PngMetadataStripper.strip(source, sink)
        ImageContainer.OTHER -> throw UnsupportedImageFormatException("Only JPEG and PNG can be stripped losslessly")
    }
    return sink.toByteArray() to report
}
