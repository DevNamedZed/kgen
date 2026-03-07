package org.kgen.binary.macho

import org.kgen.binary.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MachOReader {

    @JvmStatic
    fun canRead(bytes: ByteArray): Boolean = MachO.isMachO(bytes)

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

        for (i in 0 until header.numberOfCommands) {
            val cmd = buf.getInt(offset)
            val cmdSize = buf.getInt(offset + 4)

            when (cmd) {
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
            }
            offset += cmdSize
        }

        val symbols = if (symtabCount > 0) parseSymbols(symtabOff, symtabCount, strtabOff) else emptyList()

        return MachOFile(
            header = header,
            segments = segments,
            symbols = symbols,
            dylibs = dylibs,
            uuid = uuid,
            mainEntryOffset = mainEntryOffset,
            sourceVersion = sourceVersion,
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

    private fun parseSymbols(symtabOff: Int, count: Int, strtabOff: Int): List<MachOSymbol> {
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
}
