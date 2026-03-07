package org.kgen.binary.pe

import org.kgen.binary.pe.clr.ClrMetadata
import org.kgen.binary.pe.clr.ClrTableParser
import java.nio.ByteBuffer

/**
 * Parses PE/COFF headers and sections from raw bytes into [PeFile].
 */
internal class PeHeaderParser(private val buf: ByteBuffer, private val raw: ByteArray) {

    private var isPe = false
    private var coffOffset = 0

    private data class RawSection(
        val name: String, val virtualSize: Int, val virtualAddress: Int,
        val rawDataSize: Int, val rawDataOffset: Int,
        val relocOffset: Int, val relocCount: Int, val characteristics: Int,
    )

    private val rawSections = mutableListOf<RawSection>()

    fun parse(): PeFile {
        val (isPeFile, coffOff) = detectFormat()
        isPe = isPeFile
        coffOffset = coffOff

        val coffHeader = parseCoffHeader()
        val optionalHeader = if (isPe && coffHeader.optionalHeaderSize > 0) {
            parseOptionalHeader(coffOffset + 20)
        } else null

        val dataDirectories = optionalHeader?.let { parseDataDirectories(it) } ?: emptyList()
        parseSectionHeaders(coffOffset + 20 + coffHeader.optionalHeaderSize, coffHeader.numberOfSections)
        val sections = buildSections()
        val symbols = buildSymbols(coffHeader)

        val rvaResolver: (Int) -> Int? = { rva -> rvaToFileOffset(rva) }
        val importParser = PeImportParser(buf, raw, rvaResolver, optionalHeader?.magic == PeConstants.PE32PLUS_MAGIC)
        val imports = if (isPe) importParser.parseImports(dataDirectories) else emptyList()
        val delayImports = if (isPe) importParser.parseDelayImports(dataDirectories) else emptyList()
        val exports = if (isPe) PeExportParser(buf, raw, rvaResolver).parseExports(dataDirectories) else null
        val baseRelocs = if (isPe) parseBaseRelocations(dataDirectories) else emptyList()
        val clr = if (isPe) parseClr(dataDirectories) else null

        return PeFile(
            isPe = isPe,
            coffHeader = coffHeader,
            optionalHeader = optionalHeader,
            sections = sections,
            symbols = symbols,
            importDirectories = imports,
            delayImportDirectories = delayImports,
            exportDirectory = exports,
            baseRelocations = baseRelocs,
            clrMetadata = clr,
            dataDirectories = dataDirectories,
        )
    }

    private fun detectFormat(): Pair<Boolean, Int> {
        check(raw.size >= 2) { "File too short" }
        if (raw[0] == 0x4d.toByte() && raw[1] == 0x5a.toByte()) {
            val peOffset = buf.getInt(0x3C)
            check(peOffset + 4 <= raw.size) { "Invalid PE offset" }
            check(raw[peOffset] == 'P'.code.toByte() && raw[peOffset + 1] == 'E'.code.toByte() &&
                  raw[peOffset + 2] == 0.toByte() && raw[peOffset + 3] == 0.toByte()) {
                "Invalid PE signature"
            }
            return true to (peOffset + 4)
        }
        return false to 0
    }

    private fun parseCoffHeader(): CoffHeader {
        val off = coffOffset
        return CoffHeader(
            machine = buf.getShort(off).toInt() and 0xFFFF,
            numberOfSections = buf.getShort(off + 2).toInt() and 0xFFFF,
            timestamp = buf.getInt(off + 4),
            symbolTableOffset = buf.getInt(off + 8),
            numberOfSymbols = buf.getInt(off + 12),
            optionalHeaderSize = buf.getShort(off + 16).toInt() and 0xFFFF,
            characteristics = buf.getShort(off + 18).toInt() and 0xFFFF,
        )
    }

    private fun parseOptionalHeader(off: Int): PeOptionalHeader {
        val magic = buf.getShort(off).toInt() and 0xFFFF
        val isPe32Plus = magic == PeConstants.PE32PLUS_MAGIC
        return PeOptionalHeader(
            magic = magic,
            linkerVersionMajor = raw[off + 2].toInt() and 0xFF,
            linkerVersionMinor = raw[off + 3].toInt() and 0xFF,
            sizeOfCode = buf.getInt(off + 4),
            sizeOfInitializedData = buf.getInt(off + 8),
            sizeOfUninitializedData = buf.getInt(off + 12),
            entryPointRVA = buf.getInt(off + 16),
            baseOfCode = buf.getInt(off + 20),
            imageBase = if (isPe32Plus) buf.getLong(off + 24) else (buf.getInt(off + 28).toLong() and 0xFFFFFFFFL),
            sectionAlignment = buf.getInt(off + 32),
            fileAlignment = buf.getInt(off + 36),
            osVersionMajor = buf.getShort(off + 40).toInt() and 0xFFFF,
            osVersionMinor = buf.getShort(off + 42).toInt() and 0xFFFF,
            imageVersionMajor = buf.getShort(off + 44).toInt() and 0xFFFF,
            imageVersionMinor = buf.getShort(off + 46).toInt() and 0xFFFF,
            subsystemVersionMajor = buf.getShort(off + 48).toInt() and 0xFFFF,
            subsystemVersionMinor = buf.getShort(off + 50).toInt() and 0xFFFF,
            sizeOfImage = buf.getInt(off + 56),
            sizeOfHeaders = buf.getInt(off + 60),
            checksum = buf.getInt(off + 64),
            subsystem = buf.getShort(off + 68).toInt() and 0xFFFF,
            dllCharacteristics = buf.getShort(off + 70).toInt() and 0xFFFF,
            sizeOfStackReserve = if (isPe32Plus) buf.getLong(off + 72) else (buf.getInt(off + 72).toLong() and 0xFFFFFFFFL),
            sizeOfStackCommit = if (isPe32Plus) buf.getLong(off + 80) else (buf.getInt(off + 76).toLong() and 0xFFFFFFFFL),
            sizeOfHeapReserve = if (isPe32Plus) buf.getLong(off + 88) else (buf.getInt(off + 80).toLong() and 0xFFFFFFFFL),
            sizeOfHeapCommit = if (isPe32Plus) buf.getLong(off + 96) else (buf.getInt(off + 84).toLong() and 0xFFFFFFFFL),
            numberOfDataDirectories = buf.getInt(if (isPe32Plus) off + 108 else off + 92),
        )
    }

    private fun parseDataDirectories(opt: PeOptionalHeader): List<PeDataDirectory> {
        val isPe32Plus = opt.magic == PeConstants.PE32PLUS_MAGIC
        val ddOffset = coffOffset + 20 + (if (isPe32Plus) 112 else 96)
        return (0 until minOf(opt.numberOfDataDirectories, 16)).map { i ->
            val off = ddOffset + i * 8
            PeDataDirectory(buf.getInt(off), buf.getInt(off + 4))
        }
    }

    private fun parseSectionHeaders(off: Int, count: Int) {
        for (i in 0 until count) {
            val sOff = off + i * 40
            val nameBytes = raw.copyOfRange(sOff, sOff + 8)
            rawSections.add(RawSection(
                name = parseSectionName(nameBytes),
                virtualSize = buf.getInt(sOff + 8),
                virtualAddress = buf.getInt(sOff + 12),
                rawDataSize = buf.getInt(sOff + 16),
                rawDataOffset = buf.getInt(sOff + 20),
                relocOffset = buf.getInt(sOff + 24),
                relocCount = buf.getShort(sOff + 32).toInt() and 0xFFFF,
                characteristics = buf.getInt(sOff + 36),
            ))
        }
    }

    private fun parseSectionName(nameBytes: ByteArray): String {
        if (nameBytes[0] == '/'.code.toByte()) {
            val numStr = String(nameBytes, 1, 7, Charsets.US_ASCII).trim('\u0000').trim()
            return numStr.toIntOrNull()?.let { readStringFromCoffStringTable(it) }
                ?: String(nameBytes, Charsets.US_ASCII).trimEnd('\u0000')
        }
        var end = 8
        for (k in 0 until 8) {
            if (nameBytes[k] == 0.toByte()) { end = k; break }
        }
        return String(nameBytes, 0, end, Charsets.US_ASCII)
    }

    private fun readStringFromCoffStringTable(offset: Int): String {
        val coff = parseCoffHeader()
        if (coff.symbolTableOffset == 0 || coff.numberOfSymbols == 0) return ""
        val absOff = coff.symbolTableOffset + coff.numberOfSymbols * 18 + offset
        return readNullTerminated(absOff)
    }

    private fun buildSections(): List<PeSection> = rawSections.map { s ->
        val data = if (s.rawDataSize > 0 && s.rawDataOffset > 0 && s.rawDataOffset + s.rawDataSize <= raw.size) {
            raw.copyOfRange(s.rawDataOffset, s.rawDataOffset + s.rawDataSize)
        } else {
            ByteArray(maxOf(s.virtualSize, 0))
        }
        PeSection(
            name = s.name, virtualSize = s.virtualSize, virtualAddress = s.virtualAddress,
            rawDataSize = s.rawDataSize, rawDataOffset = s.rawDataOffset,
            relocationsOffset = s.relocOffset, numberOfRelocations = s.relocCount,
            characteristics = s.characteristics, data = data,
        )
    }

    private fun buildSymbols(coffHeader: CoffHeader): List<CoffSymbol> {
        if (coffHeader.symbolTableOffset == 0 || coffHeader.numberOfSymbols == 0) return emptyList()
        val symbols = mutableListOf<CoffSymbol>()
        var i = 0
        while (i < coffHeader.numberOfSymbols) {
            val off = coffHeader.symbolTableOffset + i * 18
            if (off + 18 > raw.size) break
            val name = readCoffSymbolName(off, coffHeader)
            val value = buf.getInt(off + 8).toLong() and 0xFFFFFFFFL
            val sectionNumber = buf.getShort(off + 12).toInt()
            val type = buf.getShort(off + 14).toInt() and 0xFFFF
            val storageClass = raw[off + 16].toInt() and 0xFF
            val numAux = raw[off + 17].toInt() and 0xFF

            if (storageClass != PeConstants.IMAGE_SYM_CLASS_FILE) {
                symbols.add(CoffSymbol(name, value, sectionNumber, type, storageClass, numAux))
            }
            i += 1 + numAux
        }
        return symbols
    }

    private fun readCoffSymbolName(off: Int, coffHeader: CoffHeader): String {
        return if (buf.getInt(off) == 0) {
            val strOff = buf.getInt(off + 4)
            val strTabStart = coffHeader.symbolTableOffset + coffHeader.numberOfSymbols * 18
            readNullTerminated(strTabStart + strOff)
        } else {
            var end = 8
            for (k in 0 until 8) {
                if (raw[off + k] == 0.toByte()) { end = k; break }
            }
            String(raw, off, end, Charsets.US_ASCII)
        }
    }

    private fun parseBaseRelocations(dataDirectories: List<PeDataDirectory>): List<PeBaseRelocation> {
        if (dataDirectories.size <= PeDataDirectory.BASE_RELOCATION) return emptyList()
        val dd = dataDirectories[PeDataDirectory.BASE_RELOCATION]
        if (dd.isEmpty) return emptyList()
        val fileOff = rvaToFileOffset(dd.rva) ?: return emptyList()

        val result = mutableListOf<PeBaseRelocation>()
        var cursor = fileOff
        val end = fileOff + dd.size
        while (cursor + 8 <= end && cursor + 8 <= raw.size) {
            val pageRVA = buf.getInt(cursor)
            val blockSize = buf.getInt(cursor + 4)
            if (blockSize < 8) break
            val entries = mutableListOf<PeBaseRelocationEntry>()
            for (j in 0 until (blockSize - 8) / 2) {
                val entry = buf.getShort(cursor + 8 + j * 2).toInt() and 0xFFFF
                val type = entry shr 12
                val offset = entry and 0x0FFF
                if (type != 0) entries.add(PeBaseRelocationEntry(type, offset))
            }
            result.add(PeBaseRelocation(pageRVA, entries))
            cursor += blockSize
        }
        return result
    }

    private fun parseClr(dataDirectories: List<PeDataDirectory>): ClrMetadata? {
        if (dataDirectories.size <= PeDataDirectory.CLR_RUNTIME) return null
        val dd = dataDirectories[PeDataDirectory.CLR_RUNTIME]
        if (dd.isEmpty) return null
        val off = rvaToFileOffset(dd.rva) ?: return null
        if (off + 72 > raw.size) return null

        val majorRtVer = buf.getShort(off + 4).toInt() and 0xFFFF
        val minorRtVer = buf.getShort(off + 6).toInt() and 0xFFFF
        val metadataRVA = buf.getInt(off + 8)
        val metadataSize = buf.getInt(off + 12)
        val flags = buf.getInt(off + 16)
        val entryPointToken = buf.getInt(off + 20)

        val metaOff = rvaToFileOffset(metadataRVA) ?: return null
        if (metaOff + metadataSize > raw.size) return null

        return ClrTableParser(buf, raw).parse(metaOff, metadataSize, majorRtVer, minorRtVer, flags, entryPointToken)
    }

    private fun rvaToFileOffset(rva: Int): Int? {
        if (!isPe) return rva
        for (s in rawSections) {
            if (rva >= s.virtualAddress && rva < s.virtualAddress + maxOf(s.virtualSize, s.rawDataSize)) {
                return s.rawDataOffset + (rva - s.virtualAddress)
            }
        }
        return null
    }

    private fun readNullTerminated(offset: Int): String {
        if (offset < 0 || offset >= raw.size) return ""
        var end = offset
        while (end < raw.size && raw[end] != 0.toByte()) end++
        return String(raw, offset, end - offset, Charsets.US_ASCII)
    }
}
