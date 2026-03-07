package org.kgen.binary.macho

import org.kgen.binary.*
import java.io.ByteArrayOutputStream

/**
 * Links relocatable ObjectFiles into a Mach-O 64-bit executable.
 *
 * Produces a minimal static executable with:
 * - __PAGEZERO segment (null pointer guard)
 * - __TEXT segment (__text + __const sections)
 * - __DATA segment (__data section, if present)
 * - LC_MAIN (entry point)
 * - LC_SYMTAB (symbol table)
 *
 * All symbols must be resolved at link time — no dynamic linking.
 */
class MachOLinker(
    private val cpuType: Int = MachO.CPU_TYPE_X86_64,
    private val cpuSubtype: Int = MachO.CPU_SUBTYPE_ALL,
) {
    private companion object {
        const val HEADER_SIZE = 32
        const val SEGMENT_CMD_SIZE = 72
        const val SECTION_HEADER_SIZE = 80
        const val MAIN_CMD_SIZE = 24
        const val SYMTAB_CMD_SIZE = 24
        const val NLIST_SIZE = 16
        const val PAGE_SIZE = 0x1000
        const val TEXT_BASE = 0x100000000L
        const val PAGEZERO_SIZE = 0x100000000L
    }

    fun link(objects: List<ObjectFile>): ByteArray {
        require(objects.isNotEmpty()) { "No object files to link" }
        val merger = SectionMerger(objects)
        val symbols = SymbolResolver(objects, merger).resolve()
        val layout = ExecutableLayout(merger, symbols, cpuType)
        val relocated = RelocationApplier(objects, merger, symbols, layout).apply()
        return ExecutableEmitter(layout, relocated, merger, symbols, cpuType, cpuSubtype).emit()
    }

    private data class SectionPlacement(
        val objIdx: Int, val sectionName: String, val mergedOffset: Int, val size: Int,
    )

    private data class ResolvedSymbol(
        val name: String,
        val value: Long,
        val sectionKind: SectionKind,
        val objIdx: Int,
    )

    private class SectionMerger(private val objects: List<ObjectFile>) {
        val text = ByteArrayOutputStream()
        val data = ByteArrayOutputStream()
        val rodata = ByteArrayOutputStream()

        val textPlacements = mutableListOf<SectionPlacement>()
        val dataPlacements = mutableListOf<SectionPlacement>()
        val rodataPlacements = mutableListOf<SectionPlacement>()
        val sectionKindMap = mutableMapOf<Pair<Int, String>, SectionKind>()

        init {
            for ((objIdx, obj) in objects.withIndex()) {
                for (sec in obj.sections) {
                    val (stream, placements) = when (sec.kind) {
                        SectionKind.TEXT -> text to textPlacements
                        SectionKind.DATA -> data to dataPlacements
                        SectionKind.RODATA -> rodata to rodataPlacements
                        else -> continue
                    }
                    val aligned = alignStream(stream, maxOf(sec.align, 1))
                    placements.add(SectionPlacement(objIdx, sec.name, aligned, sec.data.size))
                    sectionKindMap[objIdx to sec.name] = sec.kind
                    stream.write(sec.data)
                }
            }
        }

        fun placementsFor(kind: SectionKind): List<SectionPlacement> = when (kind) {
            SectionKind.TEXT -> textPlacements
            SectionKind.DATA -> dataPlacements
            SectionKind.RODATA -> rodataPlacements
            else -> emptyList()
        }

        private fun alignStream(buf: ByteArrayOutputStream, alignment: Int): Int {
            val current = buf.size()
            val aligned = ((current + alignment - 1) / alignment) * alignment
            if (aligned > current) buf.write(ByteArray(aligned - current))
            return aligned
        }
    }

    private class SymbolResolver(
        private val objects: List<ObjectFile>,
        private val merger: SectionMerger,
    ) {
        val globalSymbols = mutableMapOf<String, ResolvedSymbol>()
        val undefinedSymbols = mutableSetOf<String>()

        fun resolve(): SymbolResolver {
            for ((objIdx, obj) in objects.withIndex()) {
                for (sym in obj.symbols) {
                    if (sym.section == null || sym.kind == SymbolKind.UNDEFINED) {
                        undefinedSymbols.add(sym.name)
                    } else {
                        val kind = merger.sectionKindMap[objIdx to sym.section] ?: continue
                        val placement = merger.placementsFor(kind)
                            .firstOrNull { it.objIdx == objIdx && it.sectionName == sym.section }
                            ?: continue
                        globalSymbols[sym.name] = ResolvedSymbol(
                            sym.name, sym.value + placement.mergedOffset, kind, objIdx,
                        )
                    }
                }
            }
            undefinedSymbols.removeAll(globalSymbols.keys)
            if (undefinedSymbols.isNotEmpty()) {
                throw IllegalStateException(
                    "Undefined symbols: ${undefinedSymbols.sorted().joinToString(", ")}",
                )
            }
            return this
        }

        val entrySymbol: ResolvedSymbol
            get() = globalSymbols["_main"] ?: globalSymbols["main"] ?: globalSymbols["_start"]
                ?: throw IllegalStateException("No _main, main, or _start symbol found")
    }

    private class ExecutableLayout(
        val merger: SectionMerger,
        val symbols: SymbolResolver,
        cpuType: Int,
    ) {
        val textBytes = merger.text.toByteArray()
        val rodataBytes = merger.rodata.toByteArray()
        val dataBytes = merger.data.toByteArray()
        val hasData = dataBytes.isNotEmpty()
        val hasRodata = rodataBytes.isNotEmpty()
        val isArm64 = cpuType == MachO.CPU_TYPE_ARM64

        // Count segments and sections for load command sizing
        val numTextSections = 1 + (if (hasRodata) 1 else 0) // __text + __const
        val numDataSections = if (hasData) 1 else 0
        val numSegments = 2 + (if (hasData) 1 else 0) // __PAGEZERO + __TEXT + optional __DATA
        val numLoadCmds = numSegments + 1 + 1 // segments + LC_MAIN + LC_SYMTAB

        val loadCmdSize: Int
        val textSegCmdSize = SEGMENT_CMD_SIZE + numTextSections * SECTION_HEADER_SIZE
        val dataSegCmdSize = if (hasData) SEGMENT_CMD_SIZE + numDataSections * SECTION_HEADER_SIZE else 0

        // File offsets and virtual addresses
        val textSectionFileOff: Int
        val textSectionVaddr: Long
        val rodataSectionFileOff: Int
        val rodataSectionVaddr: Long
        val textSegFileSize: Int
        val textSegVmSize: Long

        val dataSectionFileOff: Int
        val dataSectionVaddr: Long
        val dataSegFileOff: Int
        val dataSegVaddr: Long
        val dataSegFileSize: Int
        val dataSegVmSize: Long

        val symtabFileOff: Int
        val strtabFileOff: Int

        init {
            loadCmdSize = SEGMENT_CMD_SIZE + // __PAGEZERO
                textSegCmdSize +
                dataSegCmdSize +
                MAIN_CMD_SIZE +
                SYMTAB_CMD_SIZE

            val headerAndCmds = HEADER_SIZE + loadCmdSize

            // __TEXT segment starts at 0, includes header + commands + sections
            // Sections are page-aligned after headers
            val textSecStart = alignTo(headerAndCmds, 16)
            textSectionFileOff = textSecStart
            textSectionVaddr = TEXT_BASE + textSecStart

            var off = textSecStart + textBytes.size
            if (hasRodata) off = alignTo(off, 16)
            rodataSectionFileOff = off
            rodataSectionVaddr = TEXT_BASE + off
            off += rodataBytes.size

            textSegFileSize = alignTo(off, PAGE_SIZE)
            textSegVmSize = textSegFileSize.toLong()

            // __DATA segment
            if (hasData) {
                dataSegFileOff = textSegFileSize
                dataSegVaddr = TEXT_BASE + textSegFileSize
                dataSectionFileOff = dataSegFileOff
                dataSectionVaddr = dataSegVaddr
                dataSegFileSize = alignTo(dataBytes.size, PAGE_SIZE)
                dataSegVmSize = dataSegFileSize.toLong()
            } else {
                dataSegFileOff = 0; dataSegVaddr = 0
                dataSectionFileOff = 0; dataSectionVaddr = 0
                dataSegFileSize = 0; dataSegVmSize = 0
            }

            // Symbol table after all segments
            val afterSegs = textSegFileSize + dataSegFileSize
            symtabFileOff = afterSegs
            // strtab follows symtab (computed during emit)
            strtabFileOff = 0 // placeholder, computed in emitter
        }

        val sectionKindVaddr: Map<SectionKind, Long>
            get() = mapOf(
                SectionKind.TEXT to textSectionVaddr,
                SectionKind.RODATA to rodataSectionVaddr,
                SectionKind.DATA to dataSectionVaddr,
            )

        fun symbolVaddrs(): Map<String, Long> {
            val result = mutableMapOf<String, Long>()
            for ((name, resolved) in symbols.globalSymbols) {
                result[name] = (sectionKindVaddr[resolved.sectionKind] ?: 0L) + resolved.value
            }
            return result
        }

        fun entryFileOffset(): Long = textSectionFileOff + symbols.entrySymbol.value

        private fun alignTo(value: Int, align: Int): Int =
            ((value + align - 1) / align) * align
    }

    private class RelocationApplier(
        private val objects: List<ObjectFile>,
        private val merger: SectionMerger,
        private val symbols: SymbolResolver,
        private val layout: ExecutableLayout,
    ) {
        fun apply(): RelocatedSections {
            val symVaddrs = layout.symbolVaddrs()
            val textBytes = merger.text.toByteArray().copyOf()
            val dataBytes = merger.data.toByteArray().copyOf()

            for ((objIdx, obj) in objects.withIndex()) {
                for (rel in obj.relocations) {
                    if (rel.section == null) continue
                    val relKind = merger.sectionKindMap[objIdx to rel.section] ?: continue

                    val (targetBytes, placements, sectionVaddr) = when (relKind) {
                        SectionKind.TEXT -> Triple(textBytes, merger.textPlacements, layout.textSectionVaddr)
                        SectionKind.DATA -> Triple(dataBytes, merger.dataPlacements, layout.dataSectionVaddr)
                        else -> continue
                    }

                    val placement = placements
                        .firstOrNull { it.objIdx == objIdx && it.sectionName == rel.section }
                        ?: continue
                    val patchOffset = (placement.mergedOffset + rel.offset).toInt()
                    val patchVaddr = sectionVaddr + patchOffset

                    val targetVaddr = symVaddrs[rel.symbol]
                        ?: throw IllegalStateException("Undefined symbol: ${rel.symbol}")

                    applyRelocation(targetBytes, patchOffset, patchVaddr, targetVaddr, rel)
                }
            }
            return RelocatedSections(textBytes, dataBytes)
        }

        private fun applyRelocation(
            bytes: ByteArray, offset: Int, patchVaddr: Long, targetVaddr: Long, rel: Relocation,
        ) {
            when (rel.type) {
                // x86-64 ELF relocation types
                RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32 -> {
                    putI32(bytes, offset, (targetVaddr + rel.addend - patchVaddr).toInt())
                }
                RelocationType.X86_64.R_32, RelocationType.X86_64.R_32S -> {
                    putI32(bytes, offset, (targetVaddr + rel.addend).toInt())
                }
                RelocationType.X86_64.R_64 -> {
                    putI64(bytes, offset, targetVaddr + rel.addend)
                }
                // Mach-O x86-64 relocation types
                RelocationType.MachO_X86_64.SIGNED, RelocationType.MachO_X86_64.BRANCH -> {
                    putI32(bytes, offset, (targetVaddr + rel.addend - patchVaddr).toInt())
                }
                RelocationType.MachO_X86_64.UNSIGNED -> {
                    putI64(bytes, offset, targetVaddr + rel.addend)
                }
                // AArch64 relocations
                RelocationType.AArch64.CALL26, RelocationType.AArch64.JUMP26 -> {
                    val disp = ((targetVaddr + rel.addend - patchVaddr) shr 2).toInt() and 0x03FFFFFF
                    val insn = readI32(bytes, offset)
                    putI32(bytes, offset, (insn and 0xFC000000.toInt()) or disp)
                }
                RelocationType.AArch64.ADR_PREL_PG_HI21 -> {
                    val target = targetVaddr + rel.addend
                    val page = ((target and -4096) - (patchVaddr and -4096)) shr 12
                    val immlo = (page.toInt() and 0x3) shl 29
                    val immhi = ((page.toInt() shr 2) and 0x7FFFF) shl 5
                    val insn = readI32(bytes, offset)
                    putI32(bytes, offset, (insn and 0x9F00001F.toInt()) or immlo or immhi)
                }
                RelocationType.AArch64.ADD_ABS_LO12_NC -> {
                    val target = targetVaddr + rel.addend
                    val imm12 = (target.toInt() and 0xFFF) shl 10
                    val insn = readI32(bytes, offset)
                    putI32(bytes, offset, (insn and 0xFFC003FF.toInt()) or imm12)
                }
                // Mach-O ARM64 relocation types
                RelocationType.MachO_ARM64.BRANCH26 -> {
                    val disp = ((targetVaddr + rel.addend - patchVaddr) shr 2).toInt() and 0x03FFFFFF
                    val insn = readI32(bytes, offset)
                    putI32(bytes, offset, (insn and 0xFC000000.toInt()) or disp)
                }
                RelocationType.MachO_ARM64.PAGE21 -> {
                    val target = targetVaddr + rel.addend
                    val page = ((target and -4096) - (patchVaddr and -4096)) shr 12
                    val immlo = (page.toInt() and 0x3) shl 29
                    val immhi = ((page.toInt() shr 2) and 0x7FFFF) shl 5
                    val insn = readI32(bytes, offset)
                    putI32(bytes, offset, (insn and 0x9F00001F.toInt()) or immlo or immhi)
                }
                RelocationType.MachO_ARM64.PAGEOFF12 -> {
                    val target = targetVaddr + rel.addend
                    val imm12 = (target.toInt() and 0xFFF) shl 10
                    val insn = readI32(bytes, offset)
                    putI32(bytes, offset, (insn and 0xFFC003FF.toInt()) or imm12)
                }
                else -> throw IllegalStateException("Unsupported relocation type: ${rel.type}")
            }
        }

        private fun readI32(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

        private fun putI32(data: ByteArray, offset: Int, value: Int) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value shr 8) and 0xFF).toByte()
            data[offset + 2] = ((value shr 16) and 0xFF).toByte()
            data[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        private fun putI64(data: ByteArray, offset: Int, value: Long) {
            for (i in 0 until 8) {
                data[offset + i] = ((value shr (i * 8)) and 0xFF).toByte()
            }
        }
    }

    private data class RelocatedSections(val text: ByteArray, val data: ByteArray)

    private class ExecutableEmitter(
        private val layout: ExecutableLayout,
        private val relocated: RelocatedSections,
        private val merger: SectionMerger,
        private val symbols: SymbolResolver,
        private val cpuType: Int,
        private val cpuSubtype: Int,
    ) {
        fun emit(): ByteArray {
            val symtab = buildSymbolTable()
            val strtab = buildStringTable(symtab)
            val strtabData = strtab.toByteArray()
            val strtabFileOff = layout.symtabFileOff + symtab.size * NLIST_SIZE

            val buf = ByteArrayOutputStream()
            emitHeader(buf)
            emitLoadCommands(buf, symtab.size, strtabFileOff, strtabData.size)
            emitTextSegment(buf)
            if (layout.hasData) emitDataSegment(buf)
            emitSymtab(buf, symtab, strtab)
            buf.write(strtabData)
            return buf.toByteArray()
        }

        private fun emitHeader(buf: ByteArrayOutputStream) {
            writeU32(buf, MachO.MH_MAGIC_64.toInt())
            writeU32(buf, cpuType)
            writeU32(buf, cpuSubtype)
            writeU32(buf, MachO.MH_EXECUTE)
            writeU32(buf, layout.numLoadCmds)
            writeU32(buf, layout.loadCmdSize)
            writeU32(buf, MachO.MH_NOUNDEFS or MachO.MH_PIE)
            writeU32(buf, 0) // reserved
        }

        private fun emitLoadCommands(
            buf: ByteArrayOutputStream, nsyms: Int, strtabOff: Int, strtabSize: Int,
        ) {
            // LC_SEGMENT_64 __PAGEZERO
            writeU32(buf, MachO.LC_SEGMENT_64)
            writeU32(buf, SEGMENT_CMD_SIZE)
            writePaddedString(buf, "__PAGEZERO", 16)
            writeU64(buf, 0) // vmaddr
            writeU64(buf, PAGEZERO_SIZE) // vmsize
            writeU64(buf, 0) // fileoff
            writeU64(buf, 0) // filesize
            writeU32(buf, 0) // maxprot
            writeU32(buf, 0) // initprot
            writeU32(buf, 0) // nsects
            writeU32(buf, 0) // flags

            // LC_SEGMENT_64 __TEXT
            writeU32(buf, MachO.LC_SEGMENT_64)
            writeU32(buf, layout.textSegCmdSize)
            writePaddedString(buf, "__TEXT", 16)
            writeU64(buf, TEXT_BASE) // vmaddr
            writeU64(buf, layout.textSegVmSize) // vmsize
            writeU64(buf, 0) // fileoff (TEXT segment starts at 0)
            writeU64(buf, layout.textSegFileSize.toLong()) // filesize
            writeU32(buf, 5) // maxprot (r-x)
            writeU32(buf, 5) // initprot (r-x)
            writeU32(buf, layout.numTextSections) // nsects
            writeU32(buf, 0) // flags

            // __text section header
            writeSectionHeader(buf, "__text", "__TEXT",
                layout.textSectionVaddr, relocated.text.size.toLong(),
                layout.textSectionFileOff,
                MachO.S_ATTR_PURE_INSTRUCTIONS or MachO.S_ATTR_SOME_INSTRUCTIONS, 4)

            // __const section header (if rodata present)
            if (layout.hasRodata) {
                writeSectionHeader(buf, "__const", "__TEXT",
                    layout.rodataSectionVaddr, merger.rodata.toByteArray().size.toLong(),
                    layout.rodataSectionFileOff, MachO.S_REGULAR, 4)
            }

            // LC_SEGMENT_64 __DATA (if present)
            if (layout.hasData) {
                writeU32(buf, MachO.LC_SEGMENT_64)
                writeU32(buf, layout.dataSegCmdSize)
                writePaddedString(buf, "__DATA", 16)
                writeU64(buf, layout.dataSegVaddr) // vmaddr
                writeU64(buf, layout.dataSegVmSize) // vmsize
                writeU64(buf, layout.dataSegFileOff.toLong()) // fileoff
                writeU64(buf, layout.dataSegFileSize.toLong()) // filesize
                writeU32(buf, 3) // maxprot (rw-)
                writeU32(buf, 3) // initprot (rw-)
                writeU32(buf, layout.numDataSections) // nsects
                writeU32(buf, 0) // flags

                writeSectionHeader(buf, "__data", "__DATA",
                    layout.dataSectionVaddr, merger.data.toByteArray().size.toLong(),
                    layout.dataSectionFileOff, MachO.S_REGULAR, 3)
            }

            // LC_MAIN
            writeU32(buf, MachO.LC_MAIN)
            writeU32(buf, MAIN_CMD_SIZE)
            writeU64(buf, layout.entryFileOffset()) // entryoff
            writeU64(buf, 0) // stacksize

            // LC_SYMTAB
            writeU32(buf, MachO.LC_SYMTAB)
            writeU32(buf, SYMTAB_CMD_SIZE)
            writeU32(buf, layout.symtabFileOff) // symoff
            writeU32(buf, nsyms) // nsyms
            writeU32(buf, strtabOff) // stroff
            writeU32(buf, strtabSize) // strsize
        }

        private fun writeSectionHeader(
            buf: ByteArrayOutputStream, sectName: String, segName: String,
            addr: Long, size: Long, fileOff: Int, flags: Int, alignLog2: Int,
        ) {
            writePaddedString(buf, sectName, 16)
            writePaddedString(buf, segName, 16)
            writeU64(buf, addr)
            writeU64(buf, size)
            writeU32(buf, fileOff) // offset
            writeU32(buf, alignLog2) // align (log2)
            writeU32(buf, 0) // reloff
            writeU32(buf, 0) // nreloc
            writeU32(buf, flags)
            writeU32(buf, 0) // reserved1
            writeU32(buf, 0) // reserved2
            writeU32(buf, 0) // reserved3 (64-bit padding)
        }

        private fun emitTextSegment(buf: ByteArrayOutputStream) {
            padTo(buf, layout.textSectionFileOff)
            buf.write(relocated.text)
            if (layout.hasRodata) {
                padTo(buf, layout.rodataSectionFileOff)
                buf.write(merger.rodata.toByteArray())
            }
            // Pad to page boundary
            padTo(buf, layout.textSegFileSize)
        }

        private fun emitDataSegment(buf: ByteArrayOutputStream) {
            padTo(buf, layout.dataSectionFileOff)
            buf.write(relocated.data)
            padTo(buf, layout.dataSegFileOff + layout.dataSegFileSize)
        }

        private data class SymEntry(val name: String, val type: Int, val sect: Int, val value: Long)

        private fun buildSymbolTable(): List<SymEntry> {
            val syms = mutableListOf<SymEntry>()
            val symVaddrs = layout.symbolVaddrs()
            var sectIdx = 1 // 1-based
            val sectionIndexMap = mutableMapOf<SectionKind, Int>()
            sectionIndexMap[SectionKind.TEXT] = sectIdx++
            if (layout.hasRodata) sectionIndexMap[SectionKind.RODATA] = sectIdx++
            if (layout.hasData) sectionIndexMap[SectionKind.DATA] = sectIdx++

            for ((name, resolved) in symbols.globalSymbols) {
                val sect = sectionIndexMap[resolved.sectionKind] ?: 1
                val nType = MachO.N_SECT or MachO.N_EXT
                syms.add(SymEntry(name, nType, sect, symVaddrs[name]!!))
            }
            return syms
        }

        private fun buildStringTable(symtab: List<SymEntry>): MachOStringTable {
            val table = MachOStringTable()
            for (sym in symtab) table.add(sym.name)
            return table
        }

        private fun emitSymtab(buf: ByteArrayOutputStream, symtab: List<SymEntry>, strtab: MachOStringTable) {
            padTo(buf, layout.symtabFileOff)
            for (sym in symtab) {
                writeU32(buf, strtab.offsetOf(sym.name))
                buf.write(sym.type)
                buf.write(sym.sect)
                writeU16(buf, 0) // n_desc
                writeU64(buf, sym.value)
            }
        }

        private fun writeU16(buf: ByteArrayOutputStream, v: Int) {
            buf.write(v and 0xFF); buf.write((v shr 8) and 0xFF)
        }

        private fun writeU32(buf: ByteArrayOutputStream, v: Int) {
            buf.write(v and 0xFF); buf.write((v shr 8) and 0xFF)
            buf.write((v shr 16) and 0xFF); buf.write((v shr 24) and 0xFF)
        }

        private fun writeU64(buf: ByteArrayOutputStream, v: Long) {
            for (i in 0..7) buf.write(((v shr (i * 8)) and 0xFF).toInt())
        }

        private fun writePaddedString(buf: ByteArrayOutputStream, str: String, size: Int) {
            val bytes = str.toByteArray(Charsets.US_ASCII)
            buf.write(bytes, 0, minOf(bytes.size, size))
            for (i in bytes.size until size) buf.write(0)
        }

        private fun padTo(buf: ByteArrayOutputStream, target: Int) {
            while (buf.size() < target) buf.write(0)
        }
    }

    private class MachOStringTable {
        private val buf = ByteArrayOutputStream()
        private val offsets = mutableMapOf<String, Int>()

        init { buf.write(0) } // null byte at start

        fun add(name: String): Int {
            offsets[name]?.let { return it }
            val off = buf.size()
            buf.write(name.toByteArray(Charsets.US_ASCII))
            buf.write(0)
            offsets[name] = off
            return off
        }

        fun offsetOf(name: String): Int = offsets[name] ?: add(name)
        fun toByteArray(): ByteArray = buf.toByteArray()
    }
}
