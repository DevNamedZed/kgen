package org.kgen.binary.pe

import org.kgen.binary.*
import org.kgen.binary.BinaryWriter.writeU16
import org.kgen.binary.BinaryWriter.writeU32
import org.kgen.binary.BinaryWriter.padTo
import java.io.ByteArrayOutputStream

/**
 * Writes COFF relocatable object files (.obj) from the ObjectFile model.
 * Produces standard x86-64 COFF objects that can be linked with MSVC link.exe or lld-link.
 */
class CoffObjectWriter(private val machine: Int = PeConstants.MACHINE_AMD64) {

    fun write(obj: ObjectFile): ByteArray {
        val layout = CoffLayout(obj, machine)
        return layout.emit()
    }

    private class CoffLayout(private val obj: ObjectFile, private val machine: Int) {

        private val COFF_HEADER_SIZE = 20
        private val SECTION_HEADER_SIZE = 40
        private val SYMBOL_SIZE = 18
        private val RELOCATION_SIZE = 10

        private data class SectionInfo(
            val section: Section,
            val characteristics: Int,
            var dataOffset: Int = 0,
            var relocOffset: Int = 0,
            val relocations: MutableList<CoffRelocation> = mutableListOf(),
        )

        fun emit(): ByteArray {
            val sections = collectSections()
            val symbolTable = buildSymbolTable(sections)
            val stringTable = buildStringTable(symbolTable)

            // Compute layout offsets
            val dataStart = COFF_HEADER_SIZE + sections.size * SECTION_HEADER_SIZE
            var offset = dataStart

            for (sec in sections) {
                sec.dataOffset = offset
                offset += sec.section.data.size
            }
            for (sec in sections) {
                sec.relocOffset = if (sec.relocations.isNotEmpty()) offset else 0
                offset += sec.relocations.size * RELOCATION_SIZE
            }
            val symtabOffset = offset

            val buf = ByteArrayOutputStream()

            // COFF Header
            writeU16(buf, machine)
            writeU16(buf, sections.size)
            writeU32(buf, 0) // timestamp
            writeU32(buf, symtabOffset) // pointer to symbol table
            writeU32(buf, symbolTable.size) // number of symbols
            writeU16(buf, 0) // optional header size (0 for .obj)
            writeU16(buf, 0) // characteristics

            // Section Headers
            for (sec in sections) {
                writeSectionName(buf, sec.section.name, stringTable)
                writeU32(buf, 0) // virtual size
                writeU32(buf, 0) // virtual address
                writeU32(buf, sec.section.data.size) // raw data size
                writeU32(buf, if (sec.section.data.isNotEmpty()) sec.dataOffset else 0) // raw data pointer
                writeU32(buf, sec.relocOffset) // relocation pointer
                writeU32(buf, 0) // line number pointer
                writeU16(buf, sec.relocations.size) // number of relocations
                writeU16(buf, 0) // number of line numbers
                writeU32(buf, sec.characteristics)
            }

            // Section Data
            for (sec in sections) {
                buf.write(sec.section.data)
            }

            // Relocations
            for (sec in sections) {
                for (rel in sec.relocations) {
                    writeU32(buf, rel.virtualAddress)
                    writeU32(buf, rel.symbolIndex)
                    writeU16(buf, rel.type)
                }
            }

            // Symbol Table
            for (sym in symbolTable) {
                writeSymbol(buf, sym, stringTable)
            }

            // String Table (4-byte size prefix + data)
            val strData = stringTable.data()
            writeU32(buf, strData.size + 4)
            buf.write(strData)

            return buf.toByteArray()
        }

        private fun collectSections(): List<SectionInfo> {
            val sections = mutableListOf<SectionInfo>()
            for (sec in obj.sections) {
                val chars = when (sec.kind) {
                    SectionKind.TEXT -> PeConstants.IMAGE_SCN_CNT_CODE or
                        PeConstants.IMAGE_SCN_MEM_EXECUTE or PeConstants.IMAGE_SCN_MEM_READ
                    SectionKind.DATA -> PeConstants.IMAGE_SCN_CNT_INITIALIZED_DATA or
                        PeConstants.IMAGE_SCN_MEM_READ or PeConstants.IMAGE_SCN_MEM_WRITE
                    SectionKind.RODATA -> PeConstants.IMAGE_SCN_CNT_INITIALIZED_DATA or
                        PeConstants.IMAGE_SCN_MEM_READ
                    SectionKind.BSS -> PeConstants.IMAGE_SCN_CNT_UNINITIALIZED_DATA or
                        PeConstants.IMAGE_SCN_MEM_READ or PeConstants.IMAGE_SCN_MEM_WRITE
                    else -> continue
                } or alignCharacteristic(sec.align)
                sections.add(SectionInfo(sec, chars))
            }
            return sections
        }

        private fun alignCharacteristic(align: Int): Int = when {
            align <= 1 -> 0x00100000   // 1-byte
            align <= 2 -> 0x00200000   // 2-byte
            align <= 4 -> 0x00300000   // 4-byte
            align <= 8 -> 0x00400000   // 8-byte
            align <= 16 -> 0x00500000  // 16-byte
            align <= 32 -> 0x00600000  // 32-byte
            align <= 64 -> 0x00700000  // 64-byte
            else -> 0x00500000         // default 16-byte
        }

        private data class SymbolEntry(
            val name: String,
            val value: Int,
            val sectionNumber: Int, // 1-based, 0=undefined
            val type: Int,
            val storageClass: Int,
        )

        private class CoffStringTable {
            private val buf = ByteArrayOutputStream()
            private val offsets = mutableMapOf<String, Int>()

            fun offsetFor(name: String): Int {
                if (name.length <= 8) return -1 // inline in symbol name
                offsets[name]?.let { return it + 4 } // +4 because the 4-byte size prefix is part of the offset
                val off = buf.size()
                buf.write(name.toByteArray(Charsets.US_ASCII))
                buf.write(0)
                offsets[name] = off
                return off + 4
            }

            fun data(): ByteArray = buf.toByteArray()
        }

        private fun buildSymbolTable(sections: List<SectionInfo>): List<SymbolEntry> {
            val symbols = mutableListOf<SymbolEntry>()
            val symbolIndexMap = mutableMapOf<String, Int>()

            // Section symbols (one per section)
            for ((i, sec) in sections.withIndex()) {
                symbolIndexMap[sec.section.name] = symbols.size
                symbols.add(SymbolEntry(
                    name = sec.section.name,
                    value = 0,
                    sectionNumber = i + 1,
                    type = 0,
                    storageClass = PeConstants.IMAGE_SYM_CLASS_STATIC,
                ))
            }

            // User symbols
            for (sym in obj.symbols) {
                symbolIndexMap[sym.name] = symbols.size
                val secNum = if (sym.section != null) {
                    sections.indexOfFirst { it.section.name == sym.section } + 1
                } else 0
                val storageClass = when (sym.binding) {
                    SymbolBinding.LOCAL -> PeConstants.IMAGE_SYM_CLASS_STATIC
                    else -> PeConstants.IMAGE_SYM_CLASS_EXTERNAL
                }
                val type = if (sym.kind == SymbolKind.FUNCTION) 0x20 else 0
                symbols.add(SymbolEntry(sym.name, sym.value.toInt(), secNum, type, storageClass))
            }

            // Build relocations with resolved symbol indices
            for (rel in obj.relocations) {
                val targetSection = sections.firstOrNull { it.section.name == rel.section } ?: continue
                val symIdx = symbolIndexMap[rel.symbol] ?: continue
                val coffType = when (rel.type) {
                    is RelocationType.COFF_X86_64 -> rel.type.value
                    is RelocationType.X86_64 -> mapElfToCoffReloc(rel.type)
                    else -> continue
                }
                targetSection.relocations.add(CoffRelocation(
                    virtualAddress = rel.offset.toInt(),
                    symbolIndex = symIdx,
                    type = coffType,
                ))
            }

            return symbols
        }

        private fun mapElfToCoffReloc(type: RelocationType.X86_64): Int = when (type) {
            RelocationType.X86_64.PC32 -> RelocationType.COFF_X86_64.REL32.value
            RelocationType.X86_64.PLT32 -> RelocationType.COFF_X86_64.REL32.value
            RelocationType.X86_64.R_64 -> RelocationType.COFF_X86_64.ADDR64.value
            RelocationType.X86_64.R_32 -> RelocationType.COFF_X86_64.ADDR32.value
            RelocationType.X86_64.R_32S -> RelocationType.COFF_X86_64.ADDR32.value
            else -> RelocationType.COFF_X86_64.REL32.value
        }

        private fun buildStringTable(symbols: List<SymbolEntry>): CoffStringTable {
            val table = CoffStringTable()
            for (sym in symbols) table.offsetFor(sym.name)
            return table
        }

        private fun writeSectionName(buf: ByteArrayOutputStream, name: String, strTable: CoffStringTable) {
            if (name.length <= 8) {
                val nameBytes = name.toByteArray(Charsets.US_ASCII)
                buf.write(nameBytes)
                for (i in nameBytes.size until 8) buf.write(0)
            } else {
                // Long name: /offset format
                val offset = strTable.offsetFor(name)
                val str = "/$offset"
                val strBytes = str.toByteArray(Charsets.US_ASCII)
                buf.write(strBytes)
                for (i in strBytes.size until 8) buf.write(0)
            }
        }

        private fun writeSymbol(buf: ByteArrayOutputStream, sym: SymbolEntry, strTable: CoffStringTable) {
            if (sym.name.length <= 8) {
                val nameBytes = sym.name.toByteArray(Charsets.US_ASCII)
                buf.write(nameBytes)
                for (i in nameBytes.size until 8) buf.write(0)
            } else {
                writeU32(buf, 0) // zeroes (indicates string table reference)
                writeU32(buf, strTable.offsetFor(sym.name))
            }
            writeU32(buf, sym.value)
            writeU16(buf, sym.sectionNumber)
            writeU16(buf, sym.type)
            buf.write(sym.storageClass)
            buf.write(0) // number of aux symbols
        }
    }
}
