package org.kgen.binary.pe

import java.nio.ByteBuffer

/**
 * Parses PE export directory from a PE binary.
 */
internal class PeExportParser(
    private val buf: ByteBuffer,
    private val raw: ByteArray,
    private val rvaResolver: (Int) -> Int?,
) {
    fun parseExports(dataDirectories: List<PeDataDirectory>): PeExportDirectory? {
        if (dataDirectories.size <= PeDataDirectory.EXPORT) return null
        val dd = dataDirectories[PeDataDirectory.EXPORT]
        if (dd.isEmpty) return null

        val off = rvaResolver(dd.rva) ?: return null
        if (off + 40 > raw.size) return null

        val timestamp = buf.getInt(off + 4)
        val majorVersion = buf.getShort(off + 8).toInt() and 0xFFFF
        val minorVersion = buf.getShort(off + 10).toInt() and 0xFFFF
        val nameRVA = buf.getInt(off + 12)
        val ordinalBase = buf.getInt(off + 16)
        val numberOfFunctions = buf.getInt(off + 20)
        val numberOfNames = buf.getInt(off + 24)
        val addressTableRVA = buf.getInt(off + 28)
        val namePointerRVA = buf.getInt(off + 32)
        val ordinalTableRVA = buf.getInt(off + 36)

        val dllName = readStringAtRVA(nameRVA)
        val nameToOrdinal = mutableMapOf<Int, String>()

        val npOff = rvaResolver(namePointerRVA)
        val otOff = rvaResolver(ordinalTableRVA)
        if (npOff != null && otOff != null) {
            for (i in 0 until numberOfNames) {
                if (npOff + (i + 1) * 4 > raw.size || otOff + (i + 1) * 2 > raw.size) break
                val nRVA = buf.getInt(npOff + i * 4)
                val ordIndex = buf.getShort(otOff + i * 2).toInt() and 0xFFFF
                nameToOrdinal[ordIndex] = readStringAtRVA(nRVA)
            }
        }

        val entries = mutableListOf<PeExportEntry>()
        val atOff = rvaResolver(addressTableRVA)
        if (atOff != null) {
            for (i in 0 until numberOfFunctions) {
                if (atOff + (i + 1) * 4 > raw.size) break
                val funcRVA = buf.getInt(atOff + i * 4)
                if (funcRVA == 0) continue
                val name = nameToOrdinal[i]
                val isForwarder = funcRVA >= dd.rva && funcRVA < dd.rva + dd.size
                entries.add(PeExportEntry(
                    name = name, ordinal = i + ordinalBase, rva = funcRVA,
                    forwarderName = if (isForwarder) readStringAtRVA(funcRVA) else null,
                ))
            }
        }

        return PeExportDirectory(
            name = dllName, ordinalBase = ordinalBase,
            timestamp = timestamp, majorVersion = majorVersion, minorVersion = minorVersion,
            entries = entries,
        )
    }

    private fun readStringAtRVA(rva: Int): String {
        val off = rvaResolver(rva) ?: return ""
        if (off >= raw.size) return ""
        var end = off
        while (end < raw.size && raw[end] != 0.toByte()) end++
        return String(raw, off, end - off, Charsets.US_ASCII)
    }
}
