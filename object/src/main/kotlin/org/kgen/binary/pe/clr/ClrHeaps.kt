package org.kgen.binary.pe.clr

class ClrStringHeap(private val data: ByteArray) {
    fun get(index: Int): String {
        if (index == 0 || index >= data.size) return ""
        var end = index
        while (end < data.size && data[end] != 0.toByte()) end++
        return String(data, index, end - index, Charsets.UTF_8)
    }
    val raw: ByteArray get() = data
}

class ClrBlobHeap(private val data: ByteArray) {
    fun get(index: Int): ByteArray {
        if (index == 0 || index >= data.size) return ByteArray(0)
        var off = index
        val (len, bytesRead) = readCompressedInt(data, off)
        off += bytesRead
        val end = minOf(off + len, data.size)
        return data.copyOfRange(off, end)
    }
    val raw: ByteArray get() = data

    companion object {
        fun readCompressedInt(data: ByteArray, offset: Int): Pair<Int, Int> {
            val b0 = data[offset].toInt() and 0xFF
            return when {
                b0 and 0x80 == 0 -> b0 to 1
                b0 and 0xC0 == 0x80 -> {
                    val b1 = data[offset + 1].toInt() and 0xFF
                    ((b0 and 0x3F) shl 8 or b1) to 2
                }
                else -> {
                    val b1 = data[offset + 1].toInt() and 0xFF
                    val b2 = data[offset + 2].toInt() and 0xFF
                    val b3 = data[offset + 3].toInt() and 0xFF
                    ((b0 and 0x1F) shl 24 or (b1 shl 16) or (b2 shl 8) or b3) to 4
                }
            }
        }
    }
}

class ClrGuidHeap(private val data: ByteArray) {
    fun get(index: Int): ByteArray {
        if (index == 0) return ByteArray(16)
        val off = (index - 1) * 16
        if (off + 16 > data.size) return ByteArray(16)
        return data.copyOfRange(off, off + 16)
    }
    val raw: ByteArray get() = data
}

class ClrUserStringHeap(private val data: ByteArray) {
    fun get(index: Int): String {
        if (index == 0 || index >= data.size) return ""
        var off = index
        val (len, bytesRead) = ClrBlobHeap.readCompressedInt(data, off)
        off += bytesRead
        val charCount = (len - 1) / 2
        if (charCount <= 0) return ""
        val chars = CharArray(charCount)
        for (i in 0 until charCount) {
            chars[i] = ((data[off + i * 2].toInt() and 0xFF) or ((data[off + i * 2 + 1].toInt() and 0xFF) shl 8)).toChar()
        }
        return String(chars)
    }
    val raw: ByteArray get() = data
}
