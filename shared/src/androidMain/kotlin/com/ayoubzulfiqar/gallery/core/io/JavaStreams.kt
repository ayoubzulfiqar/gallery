package com.ayoubzulfiqar.gallery.core.io

import java.io.InputStream
import java.io.OutputStream

class InputStreamSource(private val stream: InputStream) : ByteSource {
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = stream.read(buffer, offset, length)
    override fun close() = stream.close()
}

class OutputStreamSink(private val stream: OutputStream, private val closeStream: Boolean = true) : ByteSink {
    override fun write(buffer: ByteArray, offset: Int, length: Int) = stream.write(buffer, offset, length)
    override fun flush() = stream.flush()
    override fun close() {
        stream.flush()
        if (closeStream) stream.close()
    }
}

fun InputStream.asByteSource(): ByteSource = InputStreamSource(this)
fun OutputStream.asByteSink(closeStream: Boolean = true): ByteSink = OutputStreamSink(this, closeStream)
