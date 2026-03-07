package org.kgen.binary.macho

import org.kgen.binary.*
import java.io.ByteArrayOutputStream

/**
 * Writes Mach-O relocatable object files (.o) from the ObjectFile model.
 * Produces standard Mach-O objects compatible with Apple ld and lld.
 *
 * Layout of a Mach-O .o file:
 *   Mach-O header (32 bytes for 64-bit)
 *   Load commands:
 *     LC_SEGMENT_64 (with sections)
 *     LC_SYMTAB
 *   Section data
 *   Relocation entries
 *   Symbol table (nlist_64 entries)
 *   String table
 */
class MachOObjectWriter(
    private val cpuType: Int = MachO.CPU_TYPE_X86_64,
    private val cpuSubtype: Int = MachO.CPU_SUBTYPE_ALL,
) {

    fun write(obj: ObjectFile): ByteArray {
        val layout = MachOLayout(obj, cpuType, cpuSubtype)
        return layout.emit()
    }

    private class MachOLayout(
        private val obj: ObjectFile,
        private val cpuType: Int,
        private val cpuSubtype: Int,
    ) {
        private val HEADER_SIZE = 32
        private val SEGMENT_CMD_SIZE = 72
        private val SECTION_SIZE = 80
        private val SYMTAB_CMD_SIZE = 24
        private val NLIST_SIZE = 16
        private val RELOC_SIZE = 8

        private data class SectionInfo(
            val section: Section,
            val segName: String,
            val sectName: String,
            val flags: Int,
            var dataOffset: Int = 0,
            var relocOffset: Int = 0,
            val relocations: MutableList<MachORelocation> = mutableListOf(),
        )

        fun emit(): ByteArray {
            val sections = collectSections()
            val symtab = buildSymbolTable(sections)
            val stringTable = buildStringTable(symtab)

            // Compute sizes
            val numSections = sections.size
            val segmentCmdSize = SEGMENT_CMD_SIZE + numSections * SECTION_SIZE
            val loadCmdSize = segmentCmdSize + SYMTAB_CMD_SIZE
            val numLoadCmds = 2 // LC_SEGMENT_64 + LC_SYMTAB

            // Section data starts after header + load commands
            val dataStart = HEADER_SIZE + loadCmdSize
            var offset = dataStart

            for (sec in sections) {
                // Align section data
                val align = maxOf(sec.section.align, 1)
                offset = alignTo(offset, align)
                sec.dataOffset = offset
                offset += sec.section.data.size
            }

            // Relocations follow section data
            for (sec in sections) {
                if (sec.relocations.isNotEmpty()) {
                    sec.relocOffset = offset
                    offset += sec.relocations.size * RELOC_SIZE
                }
            }

            val symtabOffset = offset
            offset += symtab.size * NLIST_SIZE
            val strTabOffset = offset
            val strData = stringTable.data()
            val strTabSize = strData.size

            val sectionDataSize = offset - dataStart + strTabSize

            val buf = ByteArrayOutputStream()

            // Mach-O Header (32 bytes for 64-bit)
            writeU32(buf, MachO.MH_MAGIC_64.toInt())
            writeU32(buf, cpuType)
            writeU32(buf, cpuSubtype)
            writeU32(buf, MachO.MH_OBJECT)
            writeU32(buf, numLoadCmds)
            writeU32(buf, loadCmdSize)
            writeU32(buf, 0) // flags
            writeU32(buf, 0) // reserved (64-bit)

            // LC_SEGMENT_64
            writeU32(buf, MachO.LC_SEGMENT_64)
            writeU32(buf, segmentCmdSize)
            writePaddedString(buf, "", 16) // segment name (empty for .o files)
            writeU64(buf, 0) // vmaddr
            writeU64(buf, sectionDataSize.toLong()) // vmsize
            writeU64(buf, dataStart.toLong()) // fileoff
            writeU64(buf, sectionDataSize.toLong()) // filesize
            writeU32(buf, 7) // maxprot (rwx)
            writeU32(buf, 7) // initprot (rwx)
            writeU32(buf, numSections)
            writeU32(buf, 0) // flags

            // Section headers
            for (sec in sections) {
                writePaddedString(buf, sec.sectName, 16)
                writePaddedString(buf, sec.segName, 16)
                writeU64(buf, (sec.dataOffset - dataStart).toLong()) // addr (relative to segment)
                writeU64(buf, sec.section.data.size.toLong()) // size
                writeU32(buf, sec.dataOffset) // offset
                writeU32(buf, log2Align(sec.section.align)) // align (log2)
                writeU32(buf, if (sec.relocations.isNotEmpty()) sec.relocOffset else 0) // reloff
                writeU32(buf, sec.relocations.size) // nreloc
                writeU32(buf, sec.flags) // flags
                writeU32(buf, 0) // reserved1
                writeU32(buf, 0) // reserved2
                writeU32(buf, 0) // reserved3 (64-bit padding)
            }

            // LC_SYMTAB
            writeU32(buf, MachO.LC_SYMTAB)
            writeU32(buf, SYMTAB_CMD_SIZE)
            writeU32(buf, symtabOffset) // symoff
            writeU32(buf, symtab.size) // nsyms
            writeU32(buf, strTabOffset) // stroff
            writeU32(buf, strTabSize) // strsize

            // Section data
            for (sec in sections) {
                padTo(buf, sec.dataOffset)
                buf.write(sec.section.data)
            }

            // Relocations
            for (sec in sections) {
                for (rel in sec.relocations) {
                    val info = (rel.symbolIndex and 0x00FFFFFF) or
                        ((if (rel.pcRelative) 1 else 0) shl 24) or
                        (rel.length shl 25) or
                        ((if (rel.extern) 1 else 0) shl 27) or
                        (rel.type shl 28)
                    writeU32(buf, rel.address)
                    writeU32(buf, info)
                }
            }

            // Symbol table (nlist_64)
            for (sym in symtab) {
                writeU32(buf, stringTable.offsetFor(sym.name)) // n_strx
                buf.write(sym.type) // n_type
                buf.write(sym.sectionIndex) // n_sect
                writeU16(buf, sym.description) // n_desc
                writeU64(buf, sym.value) // n_value
            }

            // String table
            buf.write(strData)

            return buf.toByteArray()
        }

        private fun collectSections(): List<SectionInfo> {
            val result = mutableListOf<SectionInfo>()
            for (sec in obj.sections) {
                val (segName, sectName, flags) = when (sec.kind) {
                    SectionKind.TEXT -> Triple("__TEXT", "__text",
                        MachO.S_ATTR_PURE_INSTRUCTIONS or MachO.S_ATTR_SOME_INSTRUCTIONS)
                    SectionKind.DATA -> Triple("__DATA", "__data", MachO.S_REGULAR)
                    SectionKind.RODATA -> Triple("__TEXT", "__const", MachO.S_REGULAR)
                    SectionKind.BSS -> Triple("__DATA", "__bss", MachO.S_ZEROFILL)
                    else -> continue
                }
                result.add(SectionInfo(sec, segName, sectName, flags))
            }
            return result
        }

        private data class SymbolEntry(
            val name: String,
            val type: Int,
            val sectionIndex: Int, // 1-based, 0 = NO_SECT
            val description: Int,
            val value: Long,
        )

        private fun buildSymbolTable(sections: List<SectionInfo>): List<SymbolEntry> {
            val symbols = mutableListOf<SymbolEntry>()
            val symbolIndexMap = mutableMapOf<String, Int>()

            // User symbols
            for (sym in obj.symbols) {
                symbolIndexMap[sym.name] = symbols.size
                val secNum = if (sym.section != null) {
                    sections.indexOfFirst { it.section.name == sym.section } + 1
                } else 0
                val nType = if (secNum > 0) MachO.N_SECT else MachO.N_UNDF
                val ext = if (sym.binding != SymbolBinding.LOCAL) MachO.N_EXT else 0
                symbols.add(SymbolEntry(
                    name = sym.name,
                    type = nType or ext,
                    sectionIndex = secNum,
                    description = 0,
                    value = sym.value,
                ))
            }

            // Build relocations
            for (rel in obj.relocations) {
                val targetSection = sections.firstOrNull { it.section.name == rel.section } ?: continue
                val symIdx = symbolIndexMap[rel.symbol] ?: continue
                val (relocType, length, pcRel) = mapRelocation(rel.type)
                targetSection.relocations.add(MachORelocation(
                    address = rel.offset.toInt(),
                    symbolIndex = symIdx,
                    pcRelative = pcRel,
                    length = length,
                    extern = true,
                    type = relocType,
                ))
            }

            return symbols
        }

        private fun mapRelocation(type: RelocationType): Triple<Int, Int, Boolean> {
            return when (type) {
                is RelocationType.MachO_X86_64 -> when (type) {
                    RelocationType.MachO_X86_64.UNSIGNED -> Triple(0, 3, false) // X86_64_RELOC_UNSIGNED, 8 bytes
                    RelocationType.MachO_X86_64.SIGNED -> Triple(1, 2, true) // X86_64_RELOC_SIGNED, 4 bytes
                    RelocationType.MachO_X86_64.BRANCH -> Triple(2, 2, true) // X86_64_RELOC_BRANCH, 4 bytes
                    RelocationType.MachO_X86_64.GOT_LOAD -> Triple(3, 2, true)
                    RelocationType.MachO_X86_64.GOT -> Triple(4, 2, true)
                    else -> Triple(0, 2, false)
                }
                is RelocationType.MachO_ARM64 -> when (type) {
                    RelocationType.MachO_ARM64.UNSIGNED -> Triple(0, 3, false)
                    RelocationType.MachO_ARM64.BRANCH26 -> Triple(2, 2, true)
                    RelocationType.MachO_ARM64.PAGE21 -> Triple(3, 2, true)
                    RelocationType.MachO_ARM64.PAGEOFF12 -> Triple(4, 2, false)
                    RelocationType.MachO_ARM64.GOT_LOAD_PAGE21 -> Triple(5, 2, true)
                    RelocationType.MachO_ARM64.GOT_LOAD_PAGEOFF12 -> Triple(6, 2, false)
                    else -> Triple(0, 2, false)
                }
                // Map from ELF relocation types
                is RelocationType.X86_64 -> when (type) {
                    RelocationType.X86_64.PC32 -> Triple(1, 2, true) // SIGNED
                    RelocationType.X86_64.PLT32 -> Triple(2, 2, true) // BRANCH
                    RelocationType.X86_64.R_64 -> Triple(0, 3, false) // UNSIGNED
                    RelocationType.X86_64.R_32 -> Triple(0, 2, false) // UNSIGNED, 4 bytes
                    else -> Triple(0, 2, false)
                }
                is RelocationType.AArch64 -> when (type) {
                    RelocationType.AArch64.CALL26 -> Triple(2, 2, true) // ARM64_RELOC_BRANCH26
                    RelocationType.AArch64.ABS64 -> Triple(0, 3, false) // UNSIGNED
                    RelocationType.AArch64.ADR_PREL_PG_HI21 -> Triple(3, 2, true) // PAGE21
                    RelocationType.AArch64.ADD_ABS_LO12_NC -> Triple(4, 2, false) // PAGEOFF12
                    else -> Triple(0, 2, false)
                }
                else -> Triple(0, 2, false)
            }
        }

        private class MachOStringTable {
            private val buf = ByteArrayOutputStream()
            private val offsets = mutableMapOf<String, Int>()

            init {
                buf.write(0) // first byte is null (empty string)
            }

            fun offsetFor(name: String): Int {
                offsets[name]?.let { return it }
                val off = buf.size()
                buf.write(name.toByteArray(Charsets.US_ASCII))
                buf.write(0)
                offsets[name] = off
                return off
            }

            fun data(): ByteArray = buf.toByteArray()
        }

        private fun buildStringTable(symbols: List<SymbolEntry>): MachOStringTable {
            val table = MachOStringTable()
            for (sym in symbols) table.offsetFor(sym.name)
            return table
        }

        private fun writeU16(buf: ByteArrayOutputStream, value: Int) {
            buf.write(value and 0xFF)
            buf.write((value shr 8) and 0xFF)
        }

        private fun writeU32(buf: ByteArrayOutputStream, value: Int) {
            buf.write(value and 0xFF)
            buf.write((value shr 8) and 0xFF)
            buf.write((value shr 16) and 0xFF)
            buf.write((value shr 24) and 0xFF)
        }

        private fun writeU64(buf: ByteArrayOutputStream, value: Long) {
            for (i in 0..7) buf.write(((value shr (i * 8)) and 0xFF).toInt())
        }

        private fun writePaddedString(buf: ByteArrayOutputStream, str: String, size: Int) {
            val bytes = str.toByteArray(Charsets.US_ASCII)
            buf.write(bytes, 0, minOf(bytes.size, size))
            for (i in bytes.size until size) buf.write(0)
        }

        private fun padTo(buf: ByteArrayOutputStream, target: Int) {
            while (buf.size() < target) buf.write(0)
        }

        private fun alignTo(value: Int, align: Int): Int {
            if (align <= 1) return value
            return (value + align - 1) and (align - 1).inv()
        }

        private fun log2Align(align: Int): Int {
            if (align <= 1) return 0
            var n = 0
            var a = align
            while (a > 1) { a = a shr 1; n++ }
            return n
        }
    }
}
