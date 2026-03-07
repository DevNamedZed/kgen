package org.kgen.tools

// Hex dump and binary display utilities

object HexDump {

    fun format(
        bytes: ByteArray,
        baseAddress: Long = 0,
        bytesPerLine: Int = 16,
        showAscii: Boolean = true,
    ): String {
        val sb = StringBuilder()
        var offset = 0
        while (offset < bytes.size) {
            val lineBytes = minOf(bytesPerLine, bytes.size - offset)
            val addr = baseAddress + offset

            // Address
            sb.append(String.format("%08x  ", addr))

            // Hex bytes
            for (i in 0 until bytesPerLine) {
                if (i < lineBytes) {
                    sb.append(String.format("%02x ", bytes[offset + i].toInt() and 0xFF))
                } else {
                    sb.append("   ")
                }
                if (i == bytesPerLine / 2 - 1) sb.append(" ")
            }

            // ASCII
            if (showAscii) {
                sb.append(" |")
                for (i in 0 until lineBytes) {
                    val b = bytes[offset + i].toInt() and 0xFF
                    sb.append(if (b in 0x20..0x7e) b.toChar() else '.')
                }
                sb.append("|")
            }

            sb.append('\n')
            offset += bytesPerLine
        }
        return sb.toString()
    }

    fun formatBytes(bytes: ByteArray, separator: String = " "): String =
        bytes.joinToString(separator) { String.format("%02x", it.toInt() and 0xFF) }
}
