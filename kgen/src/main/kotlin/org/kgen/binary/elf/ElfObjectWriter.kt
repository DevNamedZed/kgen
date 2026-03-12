package org.kgen.binary.elf

import org.kgen.binary.*
import org.kgen.binary.BinaryWriter.align
import org.kgen.binary.BinaryWriter.padTo
import org.kgen.binary.BinaryWriter.writeS64
import org.kgen.binary.BinaryWriter.writeU16
import org.kgen.binary.BinaryWriter.writeU32
import org.kgen.binary.BinaryWriter.writeU64
import java.io.ByteArrayOutputStream

/**
 * Writes ELF64 relocatable object files (.o) from the ObjectFile model.
 * Produces proper .text, .data, .rodata, .bss sections with .symtab, .strtab, .rela.
 */
class ElfObjectWriter(private val machine: Int = ElfMachine.X86_64.code) {

    fun write(obj: ObjectFile): ByteArray {
        val layout = ObjectLayout(obj, machine)
        layout.collectSections()
        layout.collectSymbols()
        layout.collectRelocations()
        layout.computeLayout()
        return layout.emit()
    }

    private class ObjectLayout(private val obj: ObjectFile, private val machine: Int) {

        private val strtab = StringTable()
        private val shstrtab = StringTable()

        data class SectionEntry(val section: Section, val name: String, val shType: Int, val shFlags: Long)
        data class SymEntry(val name: Int, val info: Int, val other: Int, val shndx: Int, val value: Long, val size: Long)
        data class RelaEntry(val offset: Long, val symIdx: Int, val type: Int, val addend: Long)

        val userSections = mutableListOf<SectionEntry>()
        val symbols = mutableListOf<SymEntry>()
        val sectionIndexMap = mutableMapOf<String, Int>()
        val relaSections = mutableMapOf<String, MutableList<RelaEntry>>()

        var firstGlobalIdx = 0

        private val shstrtabNames = mutableMapOf<String, Int>()
        private var symtabNameIdx = 0
        private var strtabNameIdx = 0
        private var shstrtabNameIdx = 0
        private val relaNameIndices = mutableMapOf<String, Int>()

        private var totalSections = 0
        private var shstrtabSectionIdx = 0
        private var strtabSectionIdx = 0
        private var symtabSectionIdx = 0

        private val sectionOffsets = mutableListOf<Long>()
        private val relaSectionOffsets = mutableMapOf<String, Long>()
        private var symtabOffset = 0L
        private var strtabOffset = 0L
        private var shstrtabOffset = 0L
        private var shoff = 0L
        private lateinit var strtabData: ByteArray
        private lateinit var shstrtabData: ByteArray
        private lateinit var relaSectionOrder: List<String>

        fun collectSections() {
            for (sec in obj.sections) {
                val (shType, shFlags) = when (sec.kind) {
                    SectionKind.TEXT -> ElfSectionType.PROGBITS.code to (ElfSectionFlags.ALLOC or ElfSectionFlags.EXECINSTR)
                    SectionKind.DATA -> ElfSectionType.PROGBITS.code to (ElfSectionFlags.ALLOC or ElfSectionFlags.WRITE)
                    SectionKind.RODATA -> ElfSectionType.PROGBITS.code to ElfSectionFlags.ALLOC
                    SectionKind.BSS -> ElfSectionType.NOBITS.code to (ElfSectionFlags.ALLOC or ElfSectionFlags.WRITE)
                    SectionKind.DEBUG_INFO, SectionKind.DEBUG_ABBREV, SectionKind.DEBUG_LINE,
                    SectionKind.DEBUG_STR, SectionKind.DEBUG_RANGES, SectionKind.DEBUG_LOC,
                    SectionKind.DEBUG_FRAME, SectionKind.DEBUG_ARANGES, SectionKind.DEBUG_PUBNAMES,
                    SectionKind.DEBUG_PUBTYPES, SectionKind.DEBUG_MACRO, SectionKind.DEBUG_LINE_STR,
                    SectionKind.DEBUG_STR_OFFSETS, SectionKind.DEBUG_ADDR, SectionKind.DEBUG_RNGLISTS,
                    SectionKind.DEBUG_LOCLISTS ->
                        ElfSectionType.PROGBITS.code to 0L
                    else -> continue
                }
                userSections.add(SectionEntry(sec, sec.name, shType, shFlags))
            }
            for ((i, sec) in userSections.withIndex()) {
                sectionIndexMap[sec.name] = i + 1
            }
        }

        fun collectSymbols() {
            symbols.add(SymEntry(0, 0, 0, Elf.SHN_UNDEF, 0, 0))
            for (sec in userSections) {
                symbols.add(SymEntry(0, Elf.stInfo(ElfSymbolBinding.LOCAL, ElfSymbolType.SECTION), 0, sectionIndexMap[sec.name]!!, 0, 0))
            }

            val localSymbols = mutableListOf<SymEntry>()
            val globalSymbols = mutableListOf<SymEntry>()
            val symbolIndexMap = mutableMapOf<String, Int>()

            for (sym in obj.symbols) {
                val nameIdx = strtab.add(sym.name)
                val shndx = if (sym.section != null) sectionIndexMap[sym.section] ?: Elf.SHN_UNDEF else Elf.SHN_UNDEF
                val bind = when (sym.binding) {
                    SymbolBinding.LOCAL -> ElfSymbolBinding.LOCAL
                    SymbolBinding.GLOBAL -> ElfSymbolBinding.GLOBAL
                    SymbolBinding.WEAK -> ElfSymbolBinding.WEAK
                    else -> ElfSymbolBinding.GLOBAL
                }
                val type = when (sym.kind) {
                    SymbolKind.FUNCTION -> ElfSymbolType.FUNC
                    SymbolKind.UNDEFINED -> ElfSymbolType.NOTYPE
                    else -> ElfSymbolType.NOTYPE
                }
                val vis = when (sym.visibility) {
                    SymbolVisibility.HIDDEN -> ElfSymbolVisibility.HIDDEN
                    SymbolVisibility.PROTECTED -> ElfSymbolVisibility.PROTECTED
                    else -> ElfSymbolVisibility.DEFAULT
                }
                val entry = SymEntry(nameIdx, Elf.stInfo(bind, type), vis.code, shndx, sym.value, sym.size)
                if (bind == ElfSymbolBinding.LOCAL) localSymbols.add(entry) else globalSymbols.add(entry)
            }

            firstGlobalIdx = symbols.size + localSymbols.size
            symbols.addAll(localSymbols)
            symbols.addAll(globalSymbols)

            var localIdx = 1 + userSections.size
            for (sym in obj.symbols) {
                if (sym.binding == SymbolBinding.LOCAL) symbolIndexMap[sym.name] = localIdx++
            }
            var globalIdx = firstGlobalIdx
            for (sym in obj.symbols) {
                if (sym.binding != SymbolBinding.LOCAL) symbolIndexMap[sym.name] = globalIdx++
            }

            for (rel in obj.relocations) {
                val targetSection = rel.section ?: continue
                val symIdx = symbolIndexMap[rel.symbol] ?: continue
                val relaType = when (rel.type) {
                    is RelocationType.X86_64 -> rel.type.value
                    is RelocationType.AArch64 -> rel.type.value
                    is RelocationType.RiscV -> rel.type.value
                    else -> error("Unsupported relocation type for ELF: ${rel.type}")
                }
                relaSections.getOrPut(targetSection) { mutableListOf() }
                    .add(RelaEntry(rel.offset, symIdx, relaType, rel.addend))
            }
        }

        fun collectRelocations() {
            shstrtab.add("")
            for (sec in userSections) {
                shstrtabNames[sec.name] = shstrtab.add(sec.name)
            }
            symtabNameIdx = shstrtab.add(".symtab")
            strtabNameIdx = shstrtab.add(".strtab")
            shstrtabNameIdx = shstrtab.add(".shstrtab")
            for (secName in relaSections.keys) {
                relaNameIndices[secName] = shstrtab.add(".rela$secName")
            }

            totalSections = 1 + userSections.size + relaSections.size + 3
            shstrtabSectionIdx = totalSections - 1
            strtabSectionIdx = totalSections - 2
            symtabSectionIdx = totalSections - 3
        }

        fun computeLayout() {
            var fileOffset = Elf.EHDR64_SIZE.toLong()

            for (sec in userSections) {
                fileOffset = align(fileOffset, maxOf(sec.section.align.toLong(), 1))
                sectionOffsets.add(fileOffset)
                if (sec.shType != ElfSectionType.NOBITS.code) {
                    fileOffset += sec.section.data.size
                }
            }

            relaSectionOrder = relaSections.keys.toList()
            for (secName in relaSectionOrder) {
                fileOffset = align(fileOffset, 8)
                relaSectionOffsets[secName] = fileOffset
                fileOffset += relaSections[secName]!!.size.toLong() * Elf.RELA64_SIZE
            }

            fileOffset = align(fileOffset, 8)
            symtabOffset = fileOffset
            fileOffset += symbols.size.toLong() * Elf.SYM64_SIZE

            strtabOffset = fileOffset
            strtabData = strtab.toByteArray()
            fileOffset += strtabData.size

            shstrtabOffset = fileOffset
            shstrtabData = shstrtab.toByteArray()
            fileOffset += shstrtabData.size

            fileOffset = align(fileOffset, 8)
            shoff = fileOffset
        }

        fun emit(): ByteArray {
            val buf = ByteArrayOutputStream()
            emitElfHeader(buf)
            emitSectionData(buf)
            emitRelocations(buf)
            emitSymbolTable(buf)
            emitStringTables(buf)
            emitSectionHeaders(buf)
            return buf.toByteArray()
        }

        private fun emitElfHeader(buf: ByteArrayOutputStream) {
            buf.write(Elf.MAGIC)
            buf.write(ElfClass.ELF64.code)
            buf.write(ElfData.LSB.code)
            buf.write(Elf.VERSION)
            buf.write(0)
            buf.write(ByteArray(8))
            writeU16(buf, ElfObjectType.REL.code)
            writeU16(buf, machine)
            writeU32(buf, Elf.VERSION)
            writeU64(buf, 0)
            writeU64(buf, 0)
            writeU64(buf, shoff)
            writeU32(buf, 0)
            writeU16(buf, Elf.EHDR64_SIZE)
            writeU16(buf, Elf.PHDR64_SIZE)
            writeU16(buf, 0)
            writeU16(buf, Elf.SHDR64_SIZE)
            writeU16(buf, totalSections)
            writeU16(buf, shstrtabSectionIdx)
        }

        private fun emitSectionData(buf: ByteArrayOutputStream) {
            for ((i, sec) in userSections.withIndex()) {
                padTo(buf, sectionOffsets[i].toInt())
                if (sec.shType != ElfSectionType.NOBITS.code) {
                    buf.write(sec.section.data)
                }
            }
        }

        private fun emitRelocations(buf: ByteArrayOutputStream) {
            for (secName in relaSectionOrder) {
                padTo(buf, relaSectionOffsets[secName]!!.toInt())
                for (rela in relaSections[secName]!!) {
                    writeU64(buf, rela.offset)
                    writeU64(buf, (rela.symIdx.toLong() shl 32) or rela.type.toLong())
                    writeS64(buf, rela.addend)
                }
            }
        }

        private fun emitSymbolTable(buf: ByteArrayOutputStream) {
            padTo(buf, symtabOffset.toInt())
            for (sym in symbols) {
                writeU32(buf, sym.name)
                buf.write(sym.info)
                buf.write(sym.other)
                writeU16(buf, sym.shndx)
                writeU64(buf, sym.value)
                writeU64(buf, sym.size)
            }
        }

        private fun emitStringTables(buf: ByteArrayOutputStream) {
            buf.write(strtabData)
            buf.write(shstrtabData)
        }

        private fun emitSectionHeaders(buf: ByteArrayOutputStream) {
            padTo(buf, shoff.toInt())

            writeSectionHeader(buf, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

            for ((i, sec) in userSections.withIndex()) {
                writeSectionHeader(buf,
                    name = shstrtabNames[sec.name]!!,
                    type = sec.shType, flags = sec.shFlags, addr = 0,
                    offset = sectionOffsets[i], size = sec.section.data.size.toLong(),
                    link = 0, info = 0,
                    addralign = maxOf(sec.section.align.toLong(), 1), entsize = 0,
                )
            }

            for (secName in relaSectionOrder) {
                writeSectionHeader(buf,
                    name = relaNameIndices[secName]!!,
                    type = ElfSectionType.RELA.code, flags = ElfSectionFlags.INFO_LINK,
                    addr = 0, offset = relaSectionOffsets[secName]!!,
                    size = relaSections[secName]!!.size.toLong() * Elf.RELA64_SIZE,
                    link = symtabSectionIdx, info = sectionIndexMap[secName]!!,
                    addralign = 8, entsize = Elf.RELA64_SIZE.toLong(),
                )
            }

            writeSectionHeader(buf,
                name = symtabNameIdx, type = ElfSectionType.SYMTAB.code, flags = 0,
                addr = 0, offset = symtabOffset,
                size = symbols.size.toLong() * Elf.SYM64_SIZE,
                link = strtabSectionIdx, info = firstGlobalIdx,
                addralign = 8, entsize = Elf.SYM64_SIZE.toLong(),
            )

            writeSectionHeader(buf,
                name = strtabNameIdx, type = ElfSectionType.STRTAB.code, flags = 0,
                addr = 0, offset = strtabOffset, size = strtabData.size.toLong(),
                link = 0, info = 0, addralign = 1, entsize = 0,
            )

            writeSectionHeader(buf,
                name = shstrtabNameIdx, type = ElfSectionType.STRTAB.code, flags = 0,
                addr = 0, offset = shstrtabOffset, size = shstrtabData.size.toLong(),
                link = 0, info = 0, addralign = 1, entsize = 0,
            )
        }

        private fun writeSectionHeader(
            buf: ByteArrayOutputStream,
            name: Int, type: Int, flags: Long, addr: Long, offset: Long, size: Long,
            link: Int, info: Int, addralign: Long, entsize: Long,
        ) {
            writeU32(buf, name)
            writeU32(buf, type)
            writeU64(buf, flags)
            writeU64(buf, addr)
            writeU64(buf, offset)
            writeU64(buf, size)
            writeU32(buf, link)
            writeU32(buf, info)
            writeU64(buf, addralign)
            writeU64(buf, entsize)
        }
    }
}
