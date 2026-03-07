package org.kgen.binary.pe.clr

import java.io.ByteArrayOutputStream

/**
 * Builders for CLR metadata heaps. Each builder accumulates entries and
 * deduplicates where appropriate, returning indices that can be used in
 * metadata table rows.
 *
 * ```kotlin
 * val strings = ClrStringHeapBuilder()
 * val nameIdx = strings.add("MyClass")
 * val nsIdx = strings.add("MyNamespace")
 * val heap = strings.build()
 * ```
 */
class ClrStringHeapBuilder {
    private val baos = ByteArrayOutputStream()
    private val map = HashMap<String, Int>()

    init {
        baos.write(0) // index 0 is always empty string
    }

    fun add(value: String): Int {
        if (value.isEmpty()) return 0
        return map.getOrPut(value) {
            val index = baos.size()
            baos.write(value.toByteArray(Charsets.UTF_8))
            baos.write(0) // null terminator
            index
        }
    }

    fun build(): ClrStringHeap = ClrStringHeap(baos.toByteArray())

    val size: Int get() = baos.size()
}

class ClrBlobHeapBuilder {
    private val baos = ByteArrayOutputStream()
    private val map = HashMap<BlobKey, Int>()

    init {
        baos.write(0) // index 0 is empty blob
    }

    fun add(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        val key = BlobKey(data)
        return map.getOrPut(key) {
            val index = baos.size()
            writeCompressedInt(baos, data.size)
            baos.write(data)
            index
        }
    }

    fun build(): ClrBlobHeap = ClrBlobHeap(baos.toByteArray())

    val size: Int get() = baos.size()

    private class BlobKey(val data: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is BlobKey && data.contentEquals(other.data)
        override fun hashCode(): Int = data.contentHashCode()
    }

    companion object {
        fun writeCompressedInt(out: ByteArrayOutputStream, value: Int) {
            when {
                value < 0x80 -> out.write(value)
                value < 0x4000 -> {
                    out.write(0x80 or (value shr 8))
                    out.write(value and 0xFF)
                }
                else -> {
                    out.write(0xC0 or (value shr 24))
                    out.write((value shr 16) and 0xFF)
                    out.write((value shr 8) and 0xFF)
                    out.write(value and 0xFF)
                }
            }
        }
    }
}

class ClrGuidHeapBuilder {
    private val guids = mutableListOf<ByteArray>()

    fun add(guid: ByteArray): Int {
        require(guid.size == 16) { "GUID must be 16 bytes" }
        guids.add(guid.copyOf())
        return guids.size // 1-based
    }

    fun build(): ClrGuidHeap {
        val baos = ByteArrayOutputStream(guids.size * 16)
        for (g in guids) baos.write(g)
        return ClrGuidHeap(baos.toByteArray())
    }

    val size: Int get() = guids.size * 16
}

class ClrUserStringHeapBuilder {
    private val baos = ByteArrayOutputStream()

    init {
        baos.write(0) // index 0
    }

    fun add(value: String): Int {
        if (value.isEmpty()) return 0
        val index = baos.size()
        // UTF-16LE encoding + terminal byte
        val charBytes = value.length * 2 + 1
        ClrBlobHeapBuilder.writeCompressedInt(baos, charBytes)
        var hasSpecial = false
        for (c in value) {
            baos.write(c.code and 0xFF)
            baos.write((c.code shr 8) and 0xFF)
            if (c.code > 0x7E || c.code in 0x01..0x08 || c.code in 0x0E..0x1F ||
                c == '\'' || c == '-') {
                hasSpecial = true
            }
        }
        baos.write(if (hasSpecial) 1 else 0)
        return index
    }

    fun build(): ClrUserStringHeap = ClrUserStringHeap(baos.toByteArray())

    val size: Int get() = baos.size()
}
