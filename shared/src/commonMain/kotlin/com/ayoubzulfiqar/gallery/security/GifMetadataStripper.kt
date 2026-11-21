package com.ayoubzulfiqar.gallery.security

import com.ayoubzulfiqar.gallery.core.io.ByteSink
import com.ayoubzulfiqar.gallery.core.io.ByteSource
import com.ayoubzulfiqar.gallery.core.io.readFully

/**
 * Removes comment extensions, plain-text extensions and non-looping application extensions
 * (e.g. "XMP Data01") from a GIF without touching pixel data. Graphic control extensions and the
 * NETSCAPE2.0 looping extension are kept so animations play exactly as before.
 */
object GifMetadataStripper {

    fun strip(source: ByteSource, sink: ByteSink): StripReport {
        val header = ByteArray(6)
        if (source.readFully(header) != 6 ||
            !(header.startsWithAscii(0, "GIF87a") || header.startsWithAscii(0, "GIF89a"))
        ) {
            throw UnsupportedImageFormatException("Not a GIF file")
        }
        sink.write(header)

        val screen = ByteArray(7)
        if (source.readFully(screen) != 7) throw UnsupportedImageFormatException("Truncated GIF header")
        sink.write(screen)
        val screenPacked = screen[4].toInt() and 0xFF
        if (screenPacked and 0x80 != 0) copyExactly(source, sink, 3L * (1 shl ((screenPacked and 7) + 1)))

        var removed = 0
        while (true) {
            when (val block = readByte(source)) {
                0x3B -> { // trailer
                    sink.write(byteArrayOf(0x3B))
                    break
                }
                0x21 -> { // extension
                    val label = readByte(source)
                    when (label) {
                        0xF9 -> { // graphic control: animation timing/disposal, keep
                            sink.write(byteArrayOf(0x21, 0xF9.toByte()))
                            copySubBlocks(source, sink)
                        }
                        0xFF -> { // application extension: fixed 11-byte header block
                            val headerSize = readByte(source)
                            if (headerSize != 11) throw UnsupportedImageFormatException("Invalid GIF application extension")
                            val identifier = ByteArray(11)
                            if (source.readFully(identifier) != 11) throw UnsupportedImageFormatException("Truncated GIF extension")
                            if (identifier.startsWithAscii(0, "NETSCAPE2.0")) {
                                sink.write(byteArrayOf(0x21, 0xFF.toByte(), 11))
                                sink.write(identifier)
                                copySubBlocks(source, sink)
                            } else {
                                skipSubBlocks(source)
                                removed++
                            }
                        }
                        0x01 -> { // plain-text extension: 12-byte header then sub-blocks
                            skipExactly(source, 12)
                            skipSubBlocks(source)
                            removed++
                        }
                        else -> { // comment (0xFE) and any unknown extension: drop
                            skipSubBlocks(source)
                            removed++
                        }
                    }
                }
                0x2C -> { // image descriptor + pixel data, kept byte-for-byte
                    sink.write(byteArrayOf(0x2C))
                    val descriptor = ByteArray(9)
                    if (source.readFully(descriptor) != 9) throw UnsupportedImageFormatException("Truncated GIF image descriptor")
                    sink.write(descriptor)
                    val packed = descriptor[8].toInt() and 0xFF
                    if (packed and 0x80 != 0) copyExactly(source, sink, 3L * (1 shl ((packed and 7) + 1)))
                    val minCode = readByte(source)
                    sink.write(byteArrayOf(minCode.toByte()))
                    copySubBlocks(source, sink)
                }
                else -> throw UnsupportedImageFormatException("Unexpected GIF block 0x${block.toString(16)}")
            }
        }

        var trailing = 0L
        val drain = ByteArray(64 * 1024)
        while (true) {
            val read = source.read(drain, 0, drain.size)
            if (read < 0) break
            trailing += read
        }
        if (trailing > 0) removed++
        sink.flush()
        return StripReport(removed, removedExifTags = 0, removedLocation = false, preservedOrientation = 1, removedTrailingBytes = trailing)
    }

    private fun readByte(source: ByteSource): Int {
        val one = ByteArray(1)
        if (source.readFully(one) != 1) throw UnsupportedImageFormatException("Truncated GIF")
        return one[0].toInt() and 0xFF
    }

    private fun copySubBlocks(source: ByteSource, sink: ByteSink) {
        while (true) {
            val length = readByte(source)
            sink.write(byteArrayOf(length.toByte()))
            if (length == 0) return
            copyExactly(source, sink, length.toLong())
        }
    }

    private fun skipSubBlocks(source: ByteSource) {
        while (true) {
            val length = readByte(source)
            if (length == 0) return
            skipExactly(source, length.toLong())
        }
    }

    private fun copyExactly(source: ByteSource, sink: ByteSink, count: Long) {
        val buffer = ByteArray(64 * 1024)
        var left = count
        while (left > 0) {
            val read = source.readFully(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read <= 0) throw UnsupportedImageFormatException("Truncated GIF data")
            sink.write(buffer, 0, read)
            left -= read
        }
    }

    private fun skipExactly(source: ByteSource, count: Long) {
        val buffer = ByteArray(64 * 1024)
        var left = count
        while (left > 0) {
            val read = source.readFully(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read <= 0) throw UnsupportedImageFormatException("Truncated GIF data")
            left -= read
        }
    }

    private fun ByteArray.startsWithAscii(offset: Int, text: String): Boolean {
        if (offset + text.length > size) return false
        for (i in text.indices) if (this[offset + i] != text[i].code.toByte()) return false
        return true
    }
}