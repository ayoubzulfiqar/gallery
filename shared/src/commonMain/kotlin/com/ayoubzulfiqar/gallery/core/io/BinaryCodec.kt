package com.ayoubzulfiqar.gallery.core.io

/** Compact, versionable big-endian binary encoding used for vault headers and indexes. */
class BinaryWriter(initialCapacity: Int = 256) {
    private val sink = ByteArraySink(initialCapacity)
    private val scratch = ByteArray(8)

    fun byte(value: Int) = apply {
        scratch[0] = value.toByte()
        sink.write(scratch, 0, 1)
    }

    fun boolean(value: Boolean) = byte(if (value) 1 else 0)

    fun int(value: Int) = apply {
        for (i in 0 until 4) scratch[i] = (value ushr (24 - 8 * i)).toByte()
        sink.write(scratch, 0, 4)
    }

    fun long(value: Long) = apply {
        for (i in 0 until 8) scratch[i] = (value ushr (56 - 8 * i)).toByte()
        sink.write(scratch, 0, 8)
    }

    fun bytes(value: ByteArray) = apply {
        int(value.size)
        sink.write(value)
    }

    fun raw(value: ByteArray) = apply { sink.write(value) }

    fun string(value: String) = bytes(value.encodeToByteArray())

    fun nullableString(value: String?) = apply {
        boolean(value != null)
        if (value != null) string(value)
    }

    fun toByteArray(): ByteArray = sink.toByteArray()
}

class BinaryReader(private val data: ByteArray) {
    var position: Int = 0
        private set

    val remaining: Int get() = data.size - position

    fun byte(): Int {
        require(1)
        return data[position++].toInt() and 0xFF
    }

    fun boolean(): Boolean = when (byte()) {
        0 -> false
        1 -> true
        else -> throw MalformedDataException("Invalid boolean")
    }

    fun int(): Int {
        require(4)
        var value = 0
        for (i in 0 until 4) value = (value shl 8) or (data[position++].toInt() and 0xFF)
        return value
    }

    fun long(): Long {
        require(8)
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (data[position++].toLong() and 0xFF)
        return value
    }

    fun bytes(maxLength: Int = MAX_FIELD_LENGTH): ByteArray {
        val length = int()
        if (length < 0 || length > maxLength) throw MalformedDataException("Invalid field length $length")
        return raw(length)
    }

    fun raw(length: Int): ByteArray {
        require(length)
        return data.copyOfRange(position, position + length).also { position += length }
    }

    fun string(maxLength: Int = MAX_FIELD_LENGTH): String = bytes(maxLength).decodeToString()

    fun nullableString(): String? = if (boolean()) string() else null

    private fun require(count: Int) {
        if (count < 0 || position + count > data.size) throw MalformedDataException("Unexpected end of data")
    }

    companion object {
        const val MAX_FIELD_LENGTH = 16 * 1024 * 1024
    }
}

class MalformedDataException(message: String) : IllegalStateException(message)
