package org.kgen.binary

import java.io.ByteArrayOutputStream

/** Shared little-endian binary write helpers for ELF/PE writers. */
object BinaryWriter {
    fun writeU16(buf: ByteArrayOutputStream, v: Int) {
        buf.write(v and 0xFF); buf.write((v shr 8) and 0xFF)
    }

    fun writeU32(buf: ByteArrayOutputStream, v: Int) {
        buf.write(v and 0xFF); buf.write((v shr 8) and 0xFF)
        buf.write((v shr 16) and 0xFF); buf.write((v shr 24) and 0xFF)
    }

    fun writeU64(buf: ByteArrayOutputStream, v: Long) {
        writeU32(buf, v.toInt()); writeU32(buf, (v shr 32).toInt())
    }

    fun writeS64(buf: ByteArrayOutputStream, v: Long) = writeU64(buf, v)

    fun padTo(buf: ByteArrayOutputStream, target: Int) {
        val pad = target - buf.size()
        if (pad > 0) buf.write(ByteArray(pad))
    }

    fun align(v: Long, a: Long): Long = (v + a - 1) and (a - 1).inv()
}
