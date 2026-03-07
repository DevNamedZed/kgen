package org.kgen.binary.pe

import java.nio.ByteBuffer

/**
 * Parses PE import directories (standard and delay-load) from a PE binary.
 */
internal class PeImportParser(
    private val buf: ByteBuffer,
    private val raw: ByteArray,
    private val rvaResolver: (Int) -> Int?,
    private val isPe32Plus: Boolean,
) {
    fun parseImports(dataDirectories: List<PeDataDirectory>): List<PeImportDirectory> {
        if (dataDirectories.size <= PeDataDirectory.IMPORT) return emptyList()
        val dd = dataDirectories[PeDataDirectory.IMPORT]
        if (dd.isEmpty) return emptyList()
        return parseImportDirectory(dd.rva)
    }

    fun parseDelayImports(dataDirectories: List<PeDataDirectory>): List<PeImportDirectory> {
        if (dataDirectories.size <= PeDataDirectory.DELAY_IMPORT) return emptyList()
        val dd = dataDirectories[PeDataDirectory.DELAY_IMPORT]
        if (dd.isEmpty) return emptyList()
        return parseDelayImportDirectory(dd.rva)
    }

    private fun parseImportDirectory(rva: Int): List<PeImportDirectory> {
        val fileOff = rvaResolver(rva) ?: return emptyList()
        val result = mutableListOf<PeImportDirectory>()

        var idtOff = fileOff
        while (idtOff + 20 <= raw.size) {
            val iltRVA = buf.getInt(idtOff)
            val timestamp = buf.getInt(idtOff + 4)
            val forwarderChain = buf.getInt(idtOff + 8)
            val nameRVA = buf.getInt(idtOff + 12)
            val iatRVA = buf.getInt(idtOff + 16)
            if (iltRVA == 0 && nameRVA == 0 && iatRVA == 0) break

            val dllName = readStringAtRVA(nameRVA)
            val entries = parseLookupTable(if (iltRVA != 0) iltRVA else iatRVA)

            result.add(PeImportDirectory(
                name = dllName, entries = entries,
                importLookupTableRVA = iltRVA, importAddressTableRVA = iatRVA,
                timestamp = timestamp, forwarderChain = forwarderChain,
            ))
            idtOff += 20
        }
        return result
    }

    private fun parseDelayImportDirectory(rva: Int): List<PeImportDirectory> {
        val fileOff = rvaResolver(rva) ?: return emptyList()
        val result = mutableListOf<PeImportDirectory>()

        var off = fileOff
        while (off + 32 <= raw.size) {
            val nameRVA = buf.getInt(off + 4)
            val iatRVA = buf.getInt(off + 12)
            val intRVA = buf.getInt(off + 16)
            if (nameRVA == 0) break

            val dllName = readStringAtRVA(nameRVA)
            val entries = parseLookupTable(intRVA)

            result.add(PeImportDirectory(
                name = dllName, entries = entries,
                importLookupTableRVA = intRVA, importAddressTableRVA = iatRVA,
                timestamp = 0, forwarderChain = 0,
            ))
            off += 32
        }
        return result
    }

    private fun parseLookupTable(rva: Int): List<PeImportEntry> {
        val off = rvaResolver(rva) ?: return emptyList()
        val entries = mutableListOf<PeImportEntry>()
        val entrySize = if (isPe32Plus) 8 else 4
        var cursor = off

        while (cursor + entrySize <= raw.size) {
            val entry = if (isPe32Plus) buf.getLong(cursor) else (buf.getInt(cursor).toLong() and 0xFFFFFFFFL)
            if (entry == 0L) break

            val isOrdinal = if (isPe32Plus) entry and (1L shl 63) != 0L else entry and (1L shl 31) != 0L
            if (isOrdinal) {
                entries.add(PeImportEntry(name = null, ordinal = (entry and 0xFFFF).toInt(), hint = null, isOrdinal = true))
            } else {
                val hintNameRVA = (entry and 0x7FFFFFFFL).toInt()
                val hintNameOff = rvaResolver(hintNameRVA)
                if (hintNameOff != null && hintNameOff + 2 < raw.size) {
                    val hint = buf.getShort(hintNameOff).toInt() and 0xFFFF
                    val name = readStringAt(hintNameOff + 2)
                    entries.add(PeImportEntry(name = name, ordinal = null, hint = hint, isOrdinal = false))
                }
            }
            cursor += entrySize
        }
        return entries
    }

    private fun readStringAtRVA(rva: Int): String {
        val off = rvaResolver(rva) ?: return ""
        return readStringAt(off)
    }

    private fun readStringAt(offset: Int): String {
        if (offset >= raw.size) return ""
        var end = offset
        while (end < raw.size && raw[end] != 0.toByte()) end++
        return String(raw, offset, end - offset, Charsets.US_ASCII)
    }
}
