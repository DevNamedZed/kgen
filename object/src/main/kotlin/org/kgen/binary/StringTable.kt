package org.kgen.binary

import java.io.ByteArrayOutputStream

/** Null-separated string table builder (first byte is always 0). Used by ELF writers. */
class StringTable {
    private val buf = ByteArrayOutputStream()
    private val offsets = mutableMapOf<String, Int>()

    init { buf.write(0) }

    fun add(s: String): Int {
        if (s.isEmpty()) return 0
        offsets[s]?.let { return it }
        val off = buf.size()
        buf.write(s.toByteArray(Charsets.US_ASCII))
        buf.write(0)
        offsets[s] = off
        return off
    }

    fun toByteArray(): ByteArray = buf.toByteArray()
}
