package org.kgen.tools

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.pe.PeReader

object Inspectors {

    @JvmStatic
    fun forBytes(bytes: ByteArray): BinaryInspector = when {
        ElfReader.canRead(bytes) -> ElfInspector()
        PeReader.canRead(bytes) -> PeInspector()
        else -> throw IllegalArgumentException(
            "Unrecognized binary format (magic: ${bytes.take(4).joinToString(" ") { "0x%02x".format(it) }})"
        )
    }
}

internal fun extractStrings(data: ByteArray, minLength: Int): List<Pair<Long, String>> {
    val result = mutableListOf<Pair<Long, String>>()
    var start = -1
    for (i in data.indices) {
        val b = data[i].toInt() and 0xFF
        if (b in 0x20..0x7E) {
            if (start == -1) start = i
        } else {
            if (start != -1 && (i - start) >= minLength) {
                result.add(start.toLong() to String(data, start, i - start, Charsets.US_ASCII))
            }
            start = -1
        }
    }
    if (start != -1 && (data.size - start) >= minLength) {
        result.add(start.toLong() to String(data, start, data.size - start, Charsets.US_ASCII))
    }
    return result
}
