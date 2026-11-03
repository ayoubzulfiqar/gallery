package com.ayoubzulfiqar.gallery.core.io

/**
 * Minimal platform-neutral streaming primitives.
 *
 * These exist so that crypto, metadata stripping and vault code can process arbitrarily
 * large media files in bounded memory from common code, while each platform adapts its
 * native stream types (java.io / NSInputStream / files) to them.
 */
interface ByteSource : AutoCloseable {
    /**
     * Reads up to [length] bytes into [buffer] starting at [offset].
     * Returns the number of bytes read, or -1 when the end of the stream has been reached.
     */
    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int
}

interface ByteSink : AutoCloseable {
    fun write(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset)
    fun flush() {}
}

class ByteArraySource(private val data: ByteArray) : ByteSource {
    private var position = 0

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position >= data.size) return -1
        val count = minOf(length, data.size - position)
        data.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }

    override fun close() {}
}

class ByteArraySink(initialCapacity: Int = 1024) : ByteSink {
    private var buffer = ByteArray(maxOf(16, initialCapacity))
    var size: Int = 0
        private set

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        ensureCapacity(size + length)
        buffer.copyInto(this.buffer, size, offset, offset + length)
        size += length
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensureCapacity(required: Int) {
        if (required <= buffer.size) return
        var newSize = buffer.size
        while (newSize < required) newSize = if (newSize > Int.MAX_VALUE / 2) Int.MAX_VALUE else newSize * 2
        buffer = buffer.copyOf(newSize)
    }

    override fun close() {}
}

/** Reads until [length] bytes are read or the stream ends. Returns the number of bytes read (0 at EOF). */
fun ByteSource.readFully(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int {
    var total = 0
    while (total < length) {
        val read = read(buffer, offset + total, length - total)
        if (read < 0) break
        total += read
    }
    return total
}

/** Copies all remaining bytes to [sink]; returns the number of bytes copied. */
fun ByteSource.copyTo(sink: ByteSink, bufferSize: Int = 64 * 1024): Long {
    val buffer = ByteArray(bufferSize)
    var total = 0L
    while (true) {
        val read = read(buffer, 0, buffer.size)
        if (read < 0) break
        if (read > 0) {
            sink.write(buffer, 0, read)
            total += read
        }
    }
    return total
}

/**
 * Reads the whole stream into memory, failing fast instead of exhausting memory when the
 * content is larger than [maxBytes].
 */
fun ByteSource.readAllBytes(maxBytes: Long = Long.MAX_VALUE): ByteArray {
    val sink = ByteArraySink()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = read(buffer, 0, buffer.size)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw ContentTooLargeException(maxBytes)
        sink.write(buffer, 0, read)
    }
    return sink.toByteArray()
}

class ContentTooLargeException(limit: Long) : IllegalStateException("Content exceeds the $limit byte limit")

/** Wraps a source and counts bytes read through it. */
class CountingSource(private val delegate: ByteSource) : ByteSource {
    var bytesRead: Long = 0
        private set

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = delegate.read(buffer, offset, length)
        if (read > 0) bytesRead += read
        return read
    }

    override fun close() = delegate.close()
}
