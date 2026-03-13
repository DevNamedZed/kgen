package org.kgen.binary.macho

import org.kgen.binary.*
import java.io.ByteArrayOutputStream

/**
 * Links relocatable ObjectFiles into a dynamically-linked Mach-O 64-bit executable.
 *
 * Produces an executable that links against shared libraries (dylibs) at runtime:
 * - __PAGEZERO segment (null pointer guard)
 * - __TEXT segment (__text + __const + __stubs + __stub_helper sections)
 * - __DATA segment (__data + __got sections)
 * - LC_LOAD_DYLINKER (dynamic linker path — /usr/lib/dyld)
 * - LC_LOAD_DYLIB (one per shared library)
 * - LC_DYLD_CHAINED_FIXUPS (modern import binding via chained fixups)
 * - LC_MAIN (entry point)
 * - LC_SYMTAB + LC_DYSYMTAB (symbol tables)
 *
 * Undefined symbols are matched against the provided shared library list and
 * bound via chained fixups at load time.
 *
 * ```java
 * var linker = new MachODynamicLinker(
 *     MachO.CPU_TYPE_X86_64,
 *     List.of("/usr/lib/libSystem.B.dylib")
 * );
 * byte[] exe = linker.link(List.of(objectFile));
 * ```
 */
class MachODynamicLinker(
    private val cpuType: Int = MachO.CPU_TYPE_X86_64,
    private val cpuSubtype: Int = MachO.CPU_SUBTYPE_ALL,
    private val sharedLibs: List<String> = listOf("/usr/lib/libSystem.B.dylib"),
) {
    private companion object {
        const val HEADER_SIZE = 32
        const val SEGMENT_CMD_SIZE = 72
        const val SECTION_HEADER_SIZE = 80
        const val MAIN_CMD_SIZE = 24
        const val SYMTAB_CMD_SIZE = 24
        const val DYSYMTAB_CMD_SIZE = 80
        const val LOAD_DYLIB_CMD_BASE_SIZE = 24
        const val LOAD_DYLINKER_CMD_BASE_SIZE = 12
        const val CHAINED_FIXUPS_CMD_SIZE = 16
        const val NLIST_SIZE = 16
        const val PAGE_SIZE = 0x4000 // 16K pages (modern macOS/arm64)
        const val TEXT_BASE = 0x100000000L
        const val PAGEZERO_SIZE = 0x100000000L
        const val DYLD_PATH = "/usr/lib/dyld"
        const val STUB_SIZE_X86 = 6    // jmp *GOT(%rip) = FF 25 xx xx xx xx
        const val STUB_SIZE_ARM64 = 12 // adrp x16, GOT; ldr x16, [x16, off]; br x16
        const val GOT_ENTRY_SIZE = 8
    }

    fun link(objects: List<ObjectFile>): ByteArray {
        require(objects.isNotEmpty()) { "No object files to link" }
        val merger = SectionMerger(objects)
        val symbols = SymbolResolver(objects, merger).resolve()
        val isArm64 = cpuType == MachO.CPU_TYPE_ARM64
        val stubSize = if (isArm64) { STUB_SIZE_ARM64 } else { STUB_SIZE_X86 }
        val layout = DynamicLayout(merger, symbols, cpuType, sharedLibs, stubSize)
        val relocated = RelocationApplier(objects, merger, symbols, layout).apply()
        return DynamicEmitter(layout, relocated, merger, symbols, cpuType, cpuSubtype, sharedLibs).emit()
    }

    // -- Section merging (same pattern as MachOLinker) --

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
            if (aligned > current) { buf.write(ByteArray(aligned - current)) }
            return aligned
        }
    }

    private class SymbolResolver(
        private val objects: List<ObjectFile>,
        private val merger: SectionMerger,
    ) {
        val globalSymbols = mutableMapOf<String, ResolvedSymbol>()
        val undefinedSymbols = mutableListOf<String>()

        fun resolve(): SymbolResolver {
            val undefined = mutableSetOf<String>()
            for ((objIdx, obj) in objects.withIndex()) {
                for (sym in obj.symbols) {
                    if (sym.section == null || sym.kind == SymbolKind.UNDEFINED) {
                        undefined.add(sym.name)
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
            undefined.removeAll(globalSymbols.keys)
            undefinedSymbols.addAll(undefined.sorted())
            return this
        }

        val entrySymbol: ResolvedSymbol
            get() = globalSymbols["_main"] ?: globalSymbols["main"] ?: globalSymbols["_start"]
                ?: throw IllegalStateException("No _main, main, or _start symbol found")
    }

    // -- Layout computation --

    private class DynamicLayout(
        val merger: SectionMerger,
        val symbols: SymbolResolver,
        cpuType: Int,
        sharedLibs: List<String>,
        stubSize: Int,
    ) {
        val textBytes = merger.text.toByteArray()
        val rodataBytes = merger.rodata.toByteArray()
        val dataBytes = merger.data.toByteArray()
        val hasData = dataBytes.isNotEmpty() || symbols.undefinedSymbols.isNotEmpty()
        val hasRodata = rodataBytes.isNotEmpty()
        val isArm64 = cpuType == MachO.CPU_TYPE_ARM64
        val imports = symbols.undefinedSymbols
        val hasImports = imports.isNotEmpty()

        // Stubs: one per import (in __TEXT,__stubs)
        val stubsSize = imports.size * stubSize

        // GOT: one 8-byte entry per import (in __DATA,__got)
        val gotSize = imports.size * GOT_ENTRY_SIZE

        // Sections in __TEXT: __text, optional __const, optional __stubs
        val numTextSections = 1 + (if (hasRodata) { 1 } else { 0 }) + (if (hasImports) { 1 } else { 0 })
        // Sections in __DATA: optional __data, optional __got
        val numDataSections = (if (dataBytes.isNotEmpty()) { 1 } else { 0 }) + (if (hasImports) { 1 } else { 0 })
        val numSegments = 2 + (if (hasData) { 1 } else { 0 }) // __PAGEZERO + __TEXT + optional __DATA

        // Load command count
        val numLoadCmds: Int
        val loadCmdSize: Int

        val textSegCmdSize = SEGMENT_CMD_SIZE + numTextSections * SECTION_HEADER_SIZE
        val dataSegCmdSize = if (hasData) { SEGMENT_CMD_SIZE + numDataSections * SECTION_HEADER_SIZE } else { 0 }

        // File offsets
        val textSectionFileOff: Int
        val textSectionVaddr: Long
        val rodataSectionFileOff: Int
        val rodataSectionVaddr: Long
        val stubsSectionFileOff: Int
        val stubsSectionVaddr: Long
        val textSegFileSize: Int
        val textSegVmSize: Long

        val dataSectionFileOff: Int
        val dataSectionVaddr: Long
        val gotSectionFileOff: Int
        val gotSectionVaddr: Long
        val dataSegFileOff: Int
        val dataSegVaddr: Long
        val dataSegFileSize: Int
        val dataSegVmSize: Long

        val fixupsPayloadFileOff: Int
        val symtabFileOff: Int

        init {
            // Compute load command sizes
            val dylinkerCmdSize = alignTo(LOAD_DYLINKER_CMD_BASE_SIZE + DYLD_PATH.length + 1, 8)
            val dylibCmdSizes = sharedLibs.sumOf { alignTo(LOAD_DYLIB_CMD_BASE_SIZE + it.length + 1, 8) }

            numLoadCmds = numSegments +
                1 + // LC_LOAD_DYLINKER
                sharedLibs.size + // LC_LOAD_DYLIB per lib
                (if (hasImports) { 1 } else { 0 }) + // LC_DYLD_CHAINED_FIXUPS
                1 + // LC_MAIN
                1 + // LC_SYMTAB
                1   // LC_DYSYMTAB

            loadCmdSize = SEGMENT_CMD_SIZE + // __PAGEZERO
                textSegCmdSize +
                dataSegCmdSize +
                dylinkerCmdSize +
                dylibCmdSizes +
                (if (hasImports) { CHAINED_FIXUPS_CMD_SIZE } else { 0 }) +
                MAIN_CMD_SIZE +
                SYMTAB_CMD_SIZE +
                DYSYMTAB_CMD_SIZE

            val headerAndCmds = HEADER_SIZE + loadCmdSize
            val textSecStart = alignTo(headerAndCmds, 16)
            textSectionFileOff = textSecStart
            textSectionVaddr = TEXT_BASE + textSecStart

            var off = textSecStart + textBytes.size
            if (hasRodata) { off = alignTo(off, 16) }
            rodataSectionFileOff = off
            rodataSectionVaddr = TEXT_BASE + off
            off += rodataBytes.size

            if (hasImports) { off = alignTo(off, 16) }
            stubsSectionFileOff = off
            stubsSectionVaddr = TEXT_BASE + off
            off += stubsSize

            textSegFileSize = alignTo(off, PAGE_SIZE)
            textSegVmSize = textSegFileSize.toLong()

            // __DATA segment
            if (hasData) {
                dataSegFileOff = textSegFileSize
                dataSegVaddr = TEXT_BASE + textSegFileSize
                dataSectionFileOff = dataSegFileOff
                dataSectionVaddr = dataSegVaddr

                var dataOff = if (dataBytes.isNotEmpty()) { dataBytes.size } else { 0 }
                if (hasImports) { dataOff = alignTo(dataOff, 8) }
                gotSectionFileOff = dataSegFileOff + dataOff
                gotSectionVaddr = dataSegVaddr + dataOff
                dataOff += gotSize

                dataSegFileSize = alignTo(dataOff, PAGE_SIZE)
                dataSegVmSize = dataSegFileSize.toLong()
            } else {
                dataSegFileOff = 0; dataSegVaddr = 0
                dataSectionFileOff = 0; dataSectionVaddr = 0
                gotSectionFileOff = 0; gotSectionVaddr = 0
                dataSegFileSize = 0; dataSegVmSize = 0
            }

            // Chained fixups payload follows data segment
            val afterSegs = textSegFileSize + dataSegFileSize
            fixupsPayloadFileOff = afterSegs
            // Symtab after fixups payload (computed in emitter after fixups payload is built)
            symtabFileOff = 0 // placeholder — computed during emit
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
            // Import stubs: map imported symbols to their stub vaddrs
            for ((idx, name) in imports.withIndex()) {
                val stubOffset = if (isArm64) { idx * STUB_SIZE_ARM64 } else { idx * STUB_SIZE_X86 }
                result[name] = stubsSectionVaddr + stubOffset
            }
            return result
        }

        /** GOT virtual address for a given import index. */
        fun gotEntryVaddr(importIdx: Int): Long = gotSectionVaddr + importIdx * GOT_ENTRY_SIZE

        fun entryFileOffset(): Long = textSectionFileOff + symbols.entrySymbol.value

        private fun alignTo(value: Int, align: Int): Int =
            ((value + align - 1) / align) * align
    }

    // -- Relocation application --

    private class RelocationApplier(
        private val objects: List<ObjectFile>,
        private val merger: SectionMerger,
        private val symbols: SymbolResolver,
        private val layout: DynamicLayout,
    ) {
        fun apply(): RelocatedSections {
            val symVaddrs = layout.symbolVaddrs()
            val textBytes = merger.text.toByteArray().copyOf()
            val dataBytes = merger.data.toByteArray().copyOf()

            for ((objIdx, obj) in objects.withIndex()) {
                for (rel in obj.relocations) {
                    if (rel.section == null) { continue }
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

                    // For imports, PC32/PLT32 relocations target the stub
                    val targetVaddr = symVaddrs[rel.symbol] ?: continue

                    applyRelocation(targetBytes, patchOffset, patchVaddr, targetVaddr, rel)
                }
            }

            // Build stubs
            val stubBytes = buildStubs(layout, symVaddrs)

            // Build GOT (all zeros — filled by dyld via chained fixups)
            val gotBytes = ByteArray(layout.gotSize)

            return RelocatedSections(textBytes, dataBytes, stubBytes, gotBytes)
        }

        private fun buildStubs(layout: DynamicLayout, symVaddrs: Map<String, Long>): ByteArray {
            if (layout.imports.isEmpty()) { return ByteArray(0) }

            val buf = ByteArrayOutputStream()
            for ((idx, name) in layout.imports.withIndex()) {
                val stubVaddr = layout.stubsSectionVaddr + buf.size()
                val gotVaddr = layout.gotEntryVaddr(idx)

                if (layout.isArm64) {
                    // ARM64 stub: adrp x16, GOT_page; ldr x16, [x16, GOT_offset]; br x16
                    val pageDiff = ((gotVaddr and -4096) - (stubVaddr and -4096)) shr 12
                    val pageOff = (gotVaddr and 0xFFF).toInt()

                    // adrp x16, page_diff
                    val immlo = (pageDiff.toInt() and 0x3) shl 29
                    val immhi = ((pageDiff.toInt() shr 2) and 0x7FFFF) shl 5
                    val adrp = 0x90000010.toInt() or immlo or immhi // adrp x16
                    writeI32(buf, adrp)

                    // ldr x16, [x16, #pageoff]
                    val ldrImm = (pageOff shr 3) shl 10 // scaled offset for 8-byte load
                    val ldr = 0xF9400210.toInt() or ldrImm // ldr x16, [x16, #off]
                    writeI32(buf, ldr)

                    // br x16
                    writeI32(buf, 0xD61F0200.toInt()) // br x16
                } else {
                    // x86-64 stub: jmp *GOT(%rip)
                    // FF 25 [32-bit RIP-relative offset to GOT entry]
                    buf.write(0xFF)
                    buf.write(0x25)
                    val ripRelGot = (gotVaddr - (stubVaddr + 6)).toInt() // +6 = instruction size
                    writeI32(buf, ripRelGot)
                }
            }
            return buf.toByteArray()
        }

        private fun applyRelocation(
            bytes: ByteArray, offset: Int, patchVaddr: Long, targetVaddr: Long, rel: Relocation,
        ) {
            when (rel.type) {
                RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32 -> {
                    putI32(bytes, offset, (targetVaddr + rel.addend - patchVaddr).toInt())
                }
                RelocationType.X86_64.R_32, RelocationType.X86_64.R_32S -> {
                    putI32(bytes, offset, (targetVaddr + rel.addend).toInt())
                }
                RelocationType.X86_64.R_64 -> {
                    putI64(bytes, offset, targetVaddr + rel.addend)
                }
                RelocationType.MachO_X86_64.SIGNED, RelocationType.MachO_X86_64.BRANCH -> {
                    putI32(bytes, offset, (targetVaddr + rel.addend - patchVaddr).toInt())
                }
                RelocationType.MachO_X86_64.UNSIGNED -> {
                    putI64(bytes, offset, targetVaddr + rel.addend)
                }
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
                else -> {} // skip unsupported
            }
        }

        private fun writeI32(buf: ByteArrayOutputStream, v: Int) {
            buf.write(v and 0xFF); buf.write((v shr 8) and 0xFF)
            buf.write((v shr 16) and 0xFF); buf.write((v shr 24) and 0xFF)
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

    private data class RelocatedSections(
        val text: ByteArray, val data: ByteArray, val stubs: ByteArray, val got: ByteArray,
    )

    // -- Emission --

    private class DynamicEmitter(
        private val layout: DynamicLayout,
        private val relocated: RelocatedSections,
        private val merger: SectionMerger,
        private val symbols: SymbolResolver,
        private val cpuType: Int,
        private val cpuSubtype: Int,
        private val sharedLibs: List<String>,
    ) {
        fun emit(): ByteArray {
            // Build chained fixups payload
            val fixupsPayload = if (layout.hasImports) { buildFixupsPayload() } else { ByteArray(0) }

            val fixupsEnd = layout.fixupsPayloadFileOff + fixupsPayload.size
            val symtabFileOff = alignTo(fixupsEnd, 8)

            val symtab = buildSymbolTable()
            val strtab = buildStringTable(symtab)
            val strtabData = strtab.toByteArray()
            val strtabFileOff = symtabFileOff + symtab.size * NLIST_SIZE

            val buf = ByteArrayOutputStream()
            emitHeader(buf)
            emitLoadCommands(buf, fixupsPayload.size, symtabFileOff, symtab.size, strtabFileOff, strtabData.size)
            emitTextSegment(buf)
            if (layout.hasData) { emitDataSegment(buf) }
            // Fixups payload
            if (fixupsPayload.isNotEmpty()) {
                padTo(buf, layout.fixupsPayloadFileOff)
                buf.write(fixupsPayload)
            }
            // Symbol table
            padTo(buf, symtabFileOff)
            emitSymtab(buf, symtab, strtab)
            buf.write(strtabData)
            return buf.toByteArray()
        }

        private fun buildFixupsPayload(): ByteArray {
            // Build chained fixups for import binding
            val imports = layout.imports.mapIndexed { idx, name ->
                ChainedFixupImport(
                    name = name,
                    libOrdinal = 1, // first shared lib ordinal (1-based)
                    weakImport = false,
                    addend = 0,
                )
            }

            // Segment 1 (DATA) with GOT page starts
            val gotRelativeOff = layout.gotSectionFileOff - layout.dataSegFileOff
            val segments = if (imports.isNotEmpty()) {
                listOf(
                    ChainedFixupSegment(
                        segmentIndex = if (layout.dataBytes.isNotEmpty() || layout.hasImports) { 2 } else { 1 }, // __DATA segment index
                        pointerFormat = ChainedPointerFormat.DYLD_CHAINED_PTR_64,
                        pageSize = PAGE_SIZE,
                        segmentOffset = layout.dataSegVaddr - TEXT_BASE, // segment offset from image base
                        maxValidPointer = 0L,
                        pageStarts = computePageStarts(gotRelativeOff, layout.gotSize),
                    ),
                )
            } else { emptyList() }

            val fixups = ChainedFixups(
                fixupsVersion = 0,
                importsFormat = ChainedImportFormat.DYLD_CHAINED_IMPORT,
                symbolsFormat = 0,
                imports = imports,
                segments = segments,
            )
            return ChainedFixupsWriter.write(fixups)
        }

        private fun computePageStarts(gotOffset: Int, gotSize: Int): List<Int> {
            if (gotSize == 0) { return emptyList() }
            val numPages = (gotOffset + gotSize + PAGE_SIZE - 1) / PAGE_SIZE
            val starts = mutableListOf<Int>()
            for (page in 0 until numPages) {
                val pageStart = page * PAGE_SIZE
                val pageEnd = pageStart + PAGE_SIZE
                // Find first GOT entry in this page
                val firstInPage = (gotOffset until (gotOffset + gotSize) step GOT_ENTRY_SIZE)
                    .firstOrNull { it in pageStart until pageEnd }
                if (firstInPage != null) {
                    starts.add(firstInPage - pageStart)
                } else {
                    starts.add(0xFFFF) // DYLD_CHAINED_PTR_START_NONE
                }
            }
            return starts
        }

        private fun emitHeader(buf: ByteArrayOutputStream) {
            writeU32(buf, MachO.MH_MAGIC_64.toInt())
            writeU32(buf, cpuType)
            writeU32(buf, cpuSubtype)
            writeU32(buf, MachO.MH_EXECUTE)
            writeU32(buf, layout.numLoadCmds)
            writeU32(buf, layout.loadCmdSize)
            writeU32(buf, MachO.MH_DYLDLINK or MachO.MH_TWOLEVEL or MachO.MH_PIE)
            writeU32(buf, 0) // reserved
        }

        private fun emitLoadCommands(
            buf: ByteArrayOutputStream,
            fixupsSize: Int,
            symtabOff: Int,
            nsyms: Int,
            strtabOff: Int,
            strtabSize: Int,
        ) {
            // LC_SEGMENT_64 __PAGEZERO
            writeU32(buf, MachO.LC_SEGMENT_64)
            writeU32(buf, SEGMENT_CMD_SIZE)
            writePaddedString(buf, "__PAGEZERO", 16)
            writeU64(buf, 0)
            writeU64(buf, PAGEZERO_SIZE)
            writeU64(buf, 0); writeU64(buf, 0)
            writeU32(buf, 0); writeU32(buf, 0)
            writeU32(buf, 0); writeU32(buf, 0)

            // LC_SEGMENT_64 __TEXT
            writeU32(buf, MachO.LC_SEGMENT_64)
            writeU32(buf, layout.textSegCmdSize)
            writePaddedString(buf, "__TEXT", 16)
            writeU64(buf, TEXT_BASE)
            writeU64(buf, layout.textSegVmSize)
            writeU64(buf, 0) // fileoff
            writeU64(buf, layout.textSegFileSize.toLong())
            writeU32(buf, 5) // maxprot (r-x)
            writeU32(buf, 5) // initprot (r-x)
            writeU32(buf, layout.numTextSections)
            writeU32(buf, 0)

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

            // __stubs section header (if imports present)
            if (layout.hasImports) {
                val stubFlags = MachO.S_SYMBOL_STUBS or MachO.S_ATTR_PURE_INSTRUCTIONS or MachO.S_ATTR_SOME_INSTRUCTIONS
                writeSectionHeader(buf, "__stubs", "__TEXT",
                    layout.stubsSectionVaddr, relocated.stubs.size.toLong(),
                    layout.stubsSectionFileOff, stubFlags, 2,
                    reserved1 = 0, // indirect symbol table index (stub #0)
                    reserved2 = if (layout.isArm64) { STUB_SIZE_ARM64 } else { STUB_SIZE_X86 })
            }

            // LC_SEGMENT_64 __DATA (if present)
            if (layout.hasData) {
                writeU32(buf, MachO.LC_SEGMENT_64)
                writeU32(buf, layout.dataSegCmdSize)
                writePaddedString(buf, "__DATA", 16)
                writeU64(buf, layout.dataSegVaddr)
                writeU64(buf, layout.dataSegVmSize)
                writeU64(buf, layout.dataSegFileOff.toLong())
                writeU64(buf, layout.dataSegFileSize.toLong())
                writeU32(buf, 3) // maxprot (rw-)
                writeU32(buf, 3) // initprot (rw-)
                writeU32(buf, layout.numDataSections)
                writeU32(buf, 0)

                if (layout.dataBytes.isNotEmpty()) {
                    writeSectionHeader(buf, "__data", "__DATA",
                        layout.dataSectionVaddr, layout.dataBytes.size.toLong(),
                        layout.dataSectionFileOff, MachO.S_REGULAR, 3)
                }

                if (layout.hasImports) {
                    writeSectionHeader(buf, "__got", "__DATA",
                        layout.gotSectionVaddr, layout.gotSize.toLong(),
                        layout.gotSectionFileOff, MachO.S_NON_LAZY_SYMBOL_POINTERS, 3)
                }
            }

            // LC_LOAD_DYLINKER
            val dylinkerCmdSize = alignTo(LOAD_DYLINKER_CMD_BASE_SIZE + DYLD_PATH.length + 1, 8)
            writeU32(buf, MachO.LC_LOAD_DYLINKER)
            writeU32(buf, dylinkerCmdSize)
            writeU32(buf, LOAD_DYLINKER_CMD_BASE_SIZE) // name offset
            val dyldBytes = DYLD_PATH.toByteArray(Charsets.US_ASCII)
            buf.write(dyldBytes)
            buf.write(0)
            val dylinkerPad = dylinkerCmdSize - LOAD_DYLINKER_CMD_BASE_SIZE - dyldBytes.size - 1
            if (dylinkerPad > 0) { buf.write(ByteArray(dylinkerPad)) }

            // LC_LOAD_DYLIB for each shared library
            for (lib in sharedLibs) {
                val libBytes = lib.toByteArray(Charsets.US_ASCII)
                val cmdSize = alignTo(LOAD_DYLIB_CMD_BASE_SIZE + libBytes.size + 1, 8)
                writeU32(buf, MachO.LC_LOAD_DYLIB)
                writeU32(buf, cmdSize)
                writeU32(buf, LOAD_DYLIB_CMD_BASE_SIZE) // name offset
                writeU32(buf, 0) // timestamp
                writeU32(buf, 0x00010000) // current_version (1.0.0)
                writeU32(buf, 0x00010000) // compat_version (1.0.0)
                buf.write(libBytes)
                buf.write(0)
                val pad = cmdSize - LOAD_DYLIB_CMD_BASE_SIZE - libBytes.size - 1
                if (pad > 0) { buf.write(ByteArray(pad)) }
            }

            // LC_DYLD_CHAINED_FIXUPS (if imports)
            if (layout.hasImports) {
                writeU32(buf, MachO.LC_DYLD_CHAINED_FIXUPS)
                writeU32(buf, CHAINED_FIXUPS_CMD_SIZE)
                writeU32(buf, layout.fixupsPayloadFileOff)
                writeU32(buf, fixupsSize)
            }

            // LC_MAIN
            writeU32(buf, MachO.LC_MAIN)
            writeU32(buf, MAIN_CMD_SIZE)
            writeU64(buf, layout.entryFileOffset()) // entryoff
            writeU64(buf, 0) // stacksize

            // LC_SYMTAB
            writeU32(buf, MachO.LC_SYMTAB)
            writeU32(buf, SYMTAB_CMD_SIZE)
            writeU32(buf, symtabOff)
            writeU32(buf, nsyms)
            writeU32(buf, strtabOff)
            writeU32(buf, strtabSize)

            // LC_DYSYMTAB
            val nLocalSyms = 0
            val nExtDefSyms = symbols.globalSymbols.size
            val nUndefSyms = symbols.undefinedSymbols.size
            writeU32(buf, MachO.LC_DYSYMTAB)
            writeU32(buf, DYSYMTAB_CMD_SIZE)
            writeU32(buf, 0) // ilocalsym
            writeU32(buf, nLocalSyms)
            writeU32(buf, nLocalSyms) // iextdefsym
            writeU32(buf, nExtDefSyms)
            writeU32(buf, nLocalSyms + nExtDefSyms) // iundefsym
            writeU32(buf, nUndefSyms)
            writeU32(buf, 0); writeU32(buf, 0) // tocoff, ntoc
            writeU32(buf, 0); writeU32(buf, 0) // modtaboff, nmodtab
            writeU32(buf, 0); writeU32(buf, 0) // extrefsymoff, nextrefsyms
            writeU32(buf, 0); writeU32(buf, 0) // indirectsymoff, nindirectsyms
            writeU32(buf, 0); writeU32(buf, 0) // extreloff, nextrel
            writeU32(buf, 0); writeU32(buf, 0) // locreloff, nlocrel
        }

        private fun writeSectionHeader(
            buf: ByteArrayOutputStream, sectName: String, segName: String,
            addr: Long, size: Long, fileOff: Int, flags: Int, alignLog2: Int,
            reserved1: Int = 0, reserved2: Int = 0,
        ) {
            writePaddedString(buf, sectName, 16)
            writePaddedString(buf, segName, 16)
            writeU64(buf, addr)
            writeU64(buf, size)
            writeU32(buf, fileOff)
            writeU32(buf, alignLog2)
            writeU32(buf, 0) // reloff
            writeU32(buf, 0) // nreloc
            writeU32(buf, flags)
            writeU32(buf, reserved1)
            writeU32(buf, reserved2)
            writeU32(buf, 0) // reserved3
        }

        private fun emitTextSegment(buf: ByteArrayOutputStream) {
            padTo(buf, layout.textSectionFileOff)
            buf.write(relocated.text)
            if (layout.hasRodata) {
                padTo(buf, layout.rodataSectionFileOff)
                buf.write(merger.rodata.toByteArray())
            }
            if (layout.hasImports) {
                padTo(buf, layout.stubsSectionFileOff)
                buf.write(relocated.stubs)
            }
            padTo(buf, layout.textSegFileSize)
        }

        private fun emitDataSegment(buf: ByteArrayOutputStream) {
            padTo(buf, layout.dataSectionFileOff)
            if (layout.dataBytes.isNotEmpty()) {
                buf.write(relocated.data)
            }
            if (layout.hasImports) {
                padTo(buf, layout.gotSectionFileOff)
                buf.write(relocated.got)
            }
            padTo(buf, layout.dataSegFileOff + layout.dataSegFileSize)
        }

        private data class SymEntry(val name: String, val type: Int, val sect: Int, val value: Long)

        private fun buildSymbolTable(): List<SymEntry> {
            val syms = mutableListOf<SymEntry>()
            val symVaddrs = layout.symbolVaddrs()
            var sectIdx = 1
            val sectionIndexMap = mutableMapOf<SectionKind, Int>()
            sectionIndexMap[SectionKind.TEXT] = sectIdx++
            if (layout.hasRodata) { sectionIndexMap[SectionKind.RODATA] = sectIdx++ }
            // stubs section is after __const but we don't index it for defined symbols
            if (layout.hasImports) { sectIdx++ }
            if (layout.dataBytes.isNotEmpty()) { sectionIndexMap[SectionKind.DATA] = sectIdx++ }

            // Defined (exported) symbols
            for ((name, resolved) in symbols.globalSymbols) {
                val sect = sectionIndexMap[resolved.sectionKind] ?: 1
                val nType = MachO.N_SECT or MachO.N_EXT
                syms.add(SymEntry(name, nType, sect, symVaddrs[name]!!))
            }
            // Undefined (imported) symbols
            for (name in symbols.undefinedSymbols) {
                syms.add(SymEntry(name, MachO.N_UNDF or MachO.N_EXT, 0, 0))
            }
            return syms
        }

        private fun buildStringTable(symtab: List<SymEntry>): MachOStringTable {
            val table = MachOStringTable()
            for (sym in symtab) { table.add(sym.name) }
            return table
        }

        private fun emitSymtab(buf: ByteArrayOutputStream, symtab: List<SymEntry>, strtab: MachOStringTable) {
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
            for (i in 0..7) { buf.write(((v shr (i * 8)) and 0xFF).toInt()) }
        }

        private fun writePaddedString(buf: ByteArrayOutputStream, str: String, size: Int) {
            val bytes = str.toByteArray(Charsets.US_ASCII)
            buf.write(bytes, 0, minOf(bytes.size, size))
            for (i in bytes.size until size) { buf.write(0) }
        }

        private fun padTo(buf: ByteArrayOutputStream, target: Int) {
            while (buf.size() < target) { buf.write(0) }
        }

        private fun alignTo(value: Int, align: Int): Int =
            ((value + align - 1) / align) * align
    }

    private class MachOStringTable {
        private val buf = ByteArrayOutputStream()
        private val offsets = mutableMapOf<String, Int>()

        init { buf.write(0) }

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
