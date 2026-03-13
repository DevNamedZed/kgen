package org.kgen.binary.macho

import org.kgen.binary.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads Mach-O binaries (macOS, iOS executables, .dylib, .o files) into a structured [MachOFile] model.
 *
 * Handles both 32-bit and 64-bit Mach-O, little-endian and big-endian (via magic detection).
 * Parses load commands, segments, sections, symbols, relocations, chained fixups, and dylib references.
 *
 * ```kotlin
 * val bytes = File("libfoo.dylib").readBytes()
 * if (MachOReader.canRead(bytes)) {
 *     val macho = MachOReader.read(bytes)
 *     println("CPU: ${macho.header.cpuType}")
 *     macho.segments.forEach { seg -> println("Segment: ${seg.name} (${seg.sections.size} sections)") }
 *
 *     // Convert to universal ObjectFile model for format-agnostic analysis
 *     val obj = MachOReader.toObjectFile(macho)
 *     obj.symbols.forEach { println(it.name) }
 * }
 * ```
 */
object MachOReader {

    /** Returns true if [bytes] starts with a Mach-O magic number (32-bit, 64-bit, or byte-swapped). */
    @JvmStatic
    fun canRead(bytes: ByteArray): Boolean = MachO.isMachO(bytes)

    /** Parse a Mach-O binary into a structured [MachOFile] with segments, symbols, and load commands. */
    @JvmStatic
    fun read(bytes: ByteArray): MachOFile {
        val buf = ByteBuffer.wrap(bytes)
        val magic = buf.getInt(0).toUInt()
        // CIGAM = byte-swapped magic = file is opposite endianness from default (big endian) read
        // If we read MH_CIGAM_64, the file is little endian; if MH_MAGIC_64, the file is big endian
        buf.order(if (magic == MachO.MH_CIGAM_64 || magic == MachO.MH_CIGAM_32)
            ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        return MachOFileParser(buf, bytes).parse()
    }

    @JvmStatic
    fun toObjectFile(macho: MachOFile): ObjectFile = MachOObjectFileProjection.project(macho)
}

class MachOObjectFileReader : ObjectFileReader {
    override val format: ObjectFormat = ObjectFormat.MACH_O
    override fun canRead(bytes: ByteArray): Boolean = MachOReader.canRead(bytes)
    override fun read(bytes: ByteArray): ObjectFile = MachOReader.toObjectFile(MachOReader.read(bytes))
}

private class MachOFileParser(private val buf: ByteBuffer, private val raw: ByteArray) {

    fun parse(): MachOFile {
        val header = parseHeader()
        var offset = if (header.is64Bit) 32 else 28 // header size
        val segments = mutableListOf<MachOSegment>()
        val dylibs = mutableListOf<String>()
        var uuid: ByteArray? = null
        var mainEntryOffset: Long? = null
        var sourceVersion: Long? = null
        var symtabOff = 0; var symtabCount = 0; var strtabOff = 0; var strtabSize = 0
        var chainedFixupsOff = -1; var chainedFixupsSize = 0

        for (i in 0 until header.numberOfCommands) {
            val cmd = buf.getInt(offset)
            val cmdSize = buf.getInt(offset + 4)

            when (cmd) {
                MachO.LC_SEGMENT -> segments.add(parseSegment32(offset))
                MachO.LC_SEGMENT_64 -> segments.add(parseSegment64(offset))
                MachO.LC_SYMTAB -> {
                    symtabOff = buf.getInt(offset + 8)
                    symtabCount = buf.getInt(offset + 12)
                    strtabOff = buf.getInt(offset + 16)
                    strtabSize = buf.getInt(offset + 20)
                }
                MachO.LC_LOAD_DYLIB, MachO.LC_REEXPORT_DYLIB, MachO.LC_LAZY_LOAD_DYLIB -> {
                    val nameOff = buf.getInt(offset + 8)
                    dylibs.add(readString(offset + nameOff))
                }
                MachO.LC_UUID -> {
                    uuid = ByteArray(16)
                    System.arraycopy(raw, offset + 8, uuid, 0, 16)
                }
                MachO.LC_MAIN -> {
                    mainEntryOffset = buf.getLong(offset + 8)
                }
                MachO.LC_SOURCE_VERSION -> {
                    sourceVersion = buf.getLong(offset + 8)
                }
                MachO.LC_DYLD_CHAINED_FIXUPS -> {
                    chainedFixupsOff = buf.getInt(offset + 8)
                    chainedFixupsSize = buf.getInt(offset + 12)
                }
            }
            offset += cmdSize
        }

        val symbols = if (symtabCount > 0) {
            if (header.is64Bit) parseSymbols64(symtabOff, symtabCount, strtabOff)
            else parseSymbols32(symtabOff, symtabCount, strtabOff)
        } else emptyList()
        val chainedFixups = if (chainedFixupsOff >= 0) {
            parseChainedFixups(chainedFixupsOff, chainedFixupsSize, segments.size)
        } else null

        return MachOFile(
            header = header,
            segments = segments,
            symbols = symbols,
            dylibs = dylibs,
            uuid = uuid,
            mainEntryOffset = mainEntryOffset,
            sourceVersion = sourceVersion,
            chainedFixups = chainedFixups,
        )
    }

    private fun parseHeader(): MachOHeader {
        val magic = buf.getInt(0).toUInt()
        val is64 = magic == MachO.MH_MAGIC_64 || magic == MachO.MH_CIGAM_64
        return MachOHeader(
            magic = magic,
            cpuType = buf.getInt(4),
            cpuSubtype = buf.getInt(8),
            fileType = buf.getInt(12),
            numberOfCommands = buf.getInt(16),
            sizeOfCommands = buf.getInt(20),
            flags = buf.getInt(24),
        )
    }

    private fun parseSegment64(offset: Int): MachOSegment {
        val name = readFixedString(offset + 8, 16)
        val vmAddr = buf.getLong(offset + 24)
        val vmSize = buf.getLong(offset + 32)
        val fileOff = buf.getLong(offset + 40)
        val fileSize = buf.getLong(offset + 48)
        val maxProt = buf.getInt(offset + 56)
        val initProt = buf.getInt(offset + 60)
        val nsects = buf.getInt(offset + 64)
        val flags = buf.getInt(offset + 68)

        val sections = mutableListOf<MachOSection>()
        var sectOff = offset + 72
        for (i in 0 until nsects) {
            sections.add(parseSection64(sectOff))
            sectOff += 80
        }

        return MachOSegment(
            name = name, vmAddress = vmAddr, vmSize = vmSize,
            fileOffset = fileOff, fileSize = fileSize,
            maxProtection = maxProt, initProtection = initProt,
            flags = flags, sections = sections,
        )
    }

    private fun parseSection64(offset: Int): MachOSection {
        val sectName = readFixedString(offset, 16)
        val segName = readFixedString(offset + 16, 16)
        val addr = buf.getLong(offset + 32)
        val size = buf.getLong(offset + 40)
        val fileOff = buf.getInt(offset + 48)
        val align = buf.getInt(offset + 52)
        val relocOff = buf.getInt(offset + 56)
        val nreloc = buf.getInt(offset + 60)
        val flags = buf.getInt(offset + 64)

        val data = if (flags and 0xFF == MachO.S_ZEROFILL) {
            ByteArray(size.toInt())
        } else if (fileOff > 0 && size > 0 && fileOff + size.toInt() <= raw.size) {
            raw.copyOfRange(fileOff, fileOff + size.toInt())
        } else {
            ByteArray(0)
        }

        val relocs = if (nreloc > 0 && relocOff > 0) parseRelocations(relocOff, nreloc) else emptyList()

        return MachOSection(
            sectionName = sectName, segmentName = segName,
            address = addr, size = size, offset = fileOff,
            align = align, relocationOffset = relocOff,
            numberOfRelocations = nreloc, flags = flags,
            data = data, relocations = relocs,
        )
    }

    private fun parseSegment32(offset: Int): MachOSegment {
        val name = readFixedString(offset + 8, 16)
        val vmAddr = buf.getInt(offset + 24).toLong() and 0xFFFFFFFFL
        val vmSize = buf.getInt(offset + 28).toLong() and 0xFFFFFFFFL
        val fileOff = buf.getInt(offset + 32).toLong() and 0xFFFFFFFFL
        val fileSize = buf.getInt(offset + 36).toLong() and 0xFFFFFFFFL
        val maxProt = buf.getInt(offset + 40)
        val initProt = buf.getInt(offset + 44)
        val nsects = buf.getInt(offset + 48)
        val flags = buf.getInt(offset + 52)

        val sections = mutableListOf<MachOSection>()
        var sectOff = offset + 56
        for (i in 0 until nsects) {
            sections.add(parseSection32(sectOff))
            sectOff += 68
        }

        return MachOSegment(
            name = name, vmAddress = vmAddr, vmSize = vmSize,
            fileOffset = fileOff, fileSize = fileSize,
            maxProtection = maxProt, initProtection = initProt,
            flags = flags, sections = sections,
        )
    }

    private fun parseSection32(offset: Int): MachOSection {
        val sectName = readFixedString(offset, 16)
        val segName = readFixedString(offset + 16, 16)
        val addr = buf.getInt(offset + 32).toLong() and 0xFFFFFFFFL
        val size = buf.getInt(offset + 36).toLong() and 0xFFFFFFFFL
        val fileOff = buf.getInt(offset + 40)
        val align = buf.getInt(offset + 44)
        val relocOff = buf.getInt(offset + 48)
        val nreloc = buf.getInt(offset + 52)
        val flags = buf.getInt(offset + 56)

        val data = if (flags and 0xFF == MachO.S_ZEROFILL) {
            ByteArray(size.toInt())
        } else if (fileOff > 0 && size > 0 && fileOff + size.toInt() <= raw.size) {
            raw.copyOfRange(fileOff, fileOff + size.toInt())
        } else {
            ByteArray(0)
        }

        val relocs = if (nreloc > 0 && relocOff > 0) parseRelocations(relocOff, nreloc) else emptyList()

        return MachOSection(
            sectionName = sectName, segmentName = segName,
            address = addr, size = size, offset = fileOff,
            align = align, relocationOffset = relocOff,
            numberOfRelocations = nreloc, flags = flags,
            data = data, relocations = relocs,
        )
    }

    private fun parseRelocations(offset: Int, count: Int): List<MachORelocation> {
        val result = mutableListOf<MachORelocation>()
        for (i in 0 until count) {
            val off = offset + i * 8
            if (off + 8 > raw.size) break
            val rAddress = buf.getInt(off)
            val rInfo = buf.getInt(off + 4)
            result.add(MachORelocation(
                address = rAddress,
                symbolIndex = rInfo and 0x00FFFFFF,
                pcRelative = (rInfo ushr 24) and 1 == 1,
                length = (rInfo ushr 25) and 3,
                extern = (rInfo ushr 27) and 1 == 1,
                type = (rInfo ushr 28) and 0xF,
            ))
        }
        return result
    }

    private fun parseSymbols64(symtabOff: Int, count: Int, strtabOff: Int): List<MachOSymbol> {
        val result = mutableListOf<MachOSymbol>()
        for (i in 0 until count) {
            val off = symtabOff + i * 16 // nlist_64 is 16 bytes
            if (off + 16 > raw.size) break
            val nameIdx = buf.getInt(off)
            val type = raw[off + 4].toInt() and 0xFF
            val sect = raw[off + 5].toInt() and 0xFF
            val desc = buf.getShort(off + 6).toInt() and 0xFFFF
            val value = buf.getLong(off + 8)
            val name = readString(strtabOff + nameIdx)
            result.add(MachOSymbol(name = name, type = type, sectionIndex = sect,
                description = desc, value = value))
        }
        return result
    }

    private fun parseSymbols32(symtabOff: Int, count: Int, strtabOff: Int): List<MachOSymbol> {
        val result = mutableListOf<MachOSymbol>()
        for (i in 0 until count) {
            val off = symtabOff + i * 12 // nlist is 12 bytes
            if (off + 12 > raw.size) break
            val nameIdx = buf.getInt(off)
            val type = raw[off + 4].toInt() and 0xFF
            val sect = raw[off + 5].toInt() and 0xFF
            val desc = buf.getShort(off + 6).toInt() and 0xFFFF
            val value = buf.getInt(off + 8).toLong() and 0xFFFFFFFFL
            val name = readString(strtabOff + nameIdx)
            result.add(MachOSymbol(name = name, type = type, sectionIndex = sect,
                description = desc, value = value))
        }
        return result
    }

    private fun readString(offset: Int): String {
        if (offset < 0 || offset >= raw.size) return ""
        var end = offset
        while (end < raw.size && raw[end] != 0.toByte()) end++
        return String(raw, offset, end - offset, Charsets.US_ASCII)
    }

    private fun readFixedString(offset: Int, maxLen: Int): String {
        val end = minOf(offset + maxLen, raw.size)
        var len = 0
        for (i in offset until end) {
            if (raw[i] == 0.toByte()) break
            len++
        }
        return String(raw, offset, len, Charsets.US_ASCII)
    }

    private fun parseChainedFixups(dataOff: Int, dataSize: Int, segCount: Int): ChainedFixups {
        val header = parseChainedFixupsHeader(dataOff)
        val segments = parseChainedStarts(dataOff + header.startsOffset, segCount)
        val imports = parseChainedImports(
            dataOff + header.importsOffset,
            header.importsCount,
            ChainedImportFormat.fromCode(header.importsFormat),
            dataOff + header.symbolsOffset,
        )
        return ChainedFixups(
            fixupsVersion = header.fixupsVersion,
            importsFormat = ChainedImportFormat.fromCode(header.importsFormat),
            symbolsFormat = header.symbolsFormat,
            imports = imports,
            segments = segments,
        )
    }

    private class FixupsHeader(
        val fixupsVersion: Int,
        val startsOffset: Int,
        val importsOffset: Int,
        val symbolsOffset: Int,
        val importsCount: Int,
        val importsFormat: Int,
        val symbolsFormat: Int,
    )

    private fun parseChainedFixupsHeader(dataOff: Int): FixupsHeader {
        return FixupsHeader(
            fixupsVersion = buf.getInt(dataOff),
            startsOffset = buf.getInt(dataOff + 4),
            importsOffset = buf.getInt(dataOff + 8),
            symbolsOffset = buf.getInt(dataOff + 12),
            importsCount = buf.getInt(dataOff + 16),
            importsFormat = buf.getInt(dataOff + 20),
            symbolsFormat = buf.getInt(dataOff + 24),
        )
    }

    private fun parseChainedStarts(startsOff: Int, segCountFromSegments: Int): List<ChainedFixupSegment> {
        val segCount = buf.getInt(startsOff)
        val segments = mutableListOf<ChainedFixupSegment>()
        for (i in 0 until segCount) {
            val segInfoOffset = buf.getInt(startsOff + 4 + i * 4)
            if (segInfoOffset == 0) continue
            segments.add(parseChainedStartsInSegment(startsOff + segInfoOffset, i))
        }
        return segments
    }

    private fun parseChainedStartsInSegment(off: Int, segIndex: Int): ChainedFixupSegment {
        val pageSize = buf.getShort(off + 4).toInt() and 0xFFFF
        val pointerFormat = buf.getShort(off + 6).toInt() and 0xFFFF
        val segmentOffset = buf.getLong(off + 8)
        val maxValidPointer = buf.getInt(off + 16).toLong() and 0xFFFFFFFFL
        val pageCount = buf.getShort(off + 20).toInt() and 0xFFFF

        val pageStarts = mutableListOf<Int>()
        for (p in 0 until pageCount) {
            pageStarts.add(buf.getShort(off + 22 + p * 2).toInt() and 0xFFFF)
        }

        return ChainedFixupSegment(
            segmentIndex = segIndex,
            pointerFormat = ChainedPointerFormat.fromCode(pointerFormat),
            pageSize = pageSize,
            segmentOffset = segmentOffset,
            maxValidPointer = maxValidPointer,
            pageStarts = pageStarts,
        )
    }

    private fun parseChainedImports(
        importsOff: Int,
        count: Int,
        format: ChainedImportFormat,
        symbolsOff: Int,
    ): List<ChainedFixupImport> {
        val imports = mutableListOf<ChainedFixupImport>()
        for (i in 0 until count) {
            imports.add(parseOneImport(importsOff, i, format, symbolsOff))
        }
        return imports
    }

    private fun parseOneImport(
        importsOff: Int,
        index: Int,
        format: ChainedImportFormat,
        symbolsOff: Int,
    ): ChainedFixupImport {
        return when (format) {
            ChainedImportFormat.DYLD_CHAINED_IMPORT -> {
                val off = importsOff + index * 4
                val packed = buf.getInt(off)
                val libOrdinal = (packed and 0xFF).toByte().toInt()
                val weakImport = (packed ushr 8) and 1 != 0
                val nameOffset = (packed ushr 9) and 0x7FFFFF
                ChainedFixupImport(
                    name = readString(symbolsOff + nameOffset),
                    libOrdinal = libOrdinal,
                    weakImport = weakImport,
                    addend = 0,
                )
            }
            ChainedImportFormat.DYLD_CHAINED_IMPORT_ADDEND -> {
                val off = importsOff + index * 8
                val packed = buf.getInt(off)
                val libOrdinal = (packed and 0xFF).toByte().toInt()
                val weakImport = (packed ushr 8) and 1 != 0
                val nameOffset = (packed ushr 9) and 0x7FFFFF
                val addend = buf.getInt(off + 4).toLong()
                ChainedFixupImport(
                    name = readString(symbolsOff + nameOffset),
                    libOrdinal = libOrdinal,
                    weakImport = weakImport,
                    addend = addend,
                )
            }
            ChainedImportFormat.DYLD_CHAINED_IMPORT_ADDEND64 -> {
                val off = importsOff + index * 16
                val packed = buf.getLong(off)
                val libOrdinal = (packed and 0xFFFF).toShort().toInt()
                val weakImport = (packed ushr 16) and 1L != 0L
                val nameOffset = ((packed ushr 17) and 0x7FFF).toInt()
                val addend = buf.getLong(off + 8)
                ChainedFixupImport(
                    name = readString(symbolsOff + nameOffset),
                    libOrdinal = libOrdinal,
                    weakImport = weakImport,
                    addend = addend,
                )
            }
        }
    }
}
