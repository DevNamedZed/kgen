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
 * Links relocatable ELF64 ObjectFiles into a shared library (.so).
 *
 * Produces an ELF DYN binary with:
 * - Exported symbols in .dynsym/.dynstr/.hash
 * - PLT/GOT for calls to external (imported) symbols
 * - .rela.dyn for data relocations needing runtime fixup (R_RELATIVE)
 * - .dynamic section with SONAME
 * - No .interp (not an executable)
 */
class ElfSharedLinker(
    private val machine: Int = ElfMachine.X86_64.code,
    private val soname: String? = null,
    private val sharedLibs: List<String> = emptyList(),
) {
    private companion object {
        const val BASE_ADDR = 0L
        const val PAGE_SIZE = 0x1000L
        const val PLT_ENTRY_SIZE = 16
        const val PLT0_SIZE = 16
        const val GOT_ENTRY_SIZE = 8
    }

    fun link(objects: List<ObjectFile>): ByteArray {
        require(objects.isNotEmpty()) { "No object files to link" }
        val merger = SectionMerger(objects)
        val symbols = SymbolResolver(objects, merger).resolve()
        val layout = SharedLayout(merger, symbols, soname, sharedLibs, objects, machine)
        val applier = RelocationApplier(objects, merger, symbols, layout, machine)
        val relocated = applier.apply()
        return SharedEmitter(layout, relocated, applier.dataRelocations, merger, symbols, machine).emit()
    }

    private data class SectionPlacement(
        val objIdx: Int, val sectionName: String, val mergedOffset: Int, val size: Int,
    )

    private data class ResolvedSymbol(
        val name: String,
        val value: Long,
        val sectionKind: SectionKind,
        val objIdx: Int,
        val binding: SymbolBinding,
        val kind: SymbolKind,
        val size: Long,
    )

    private data class DebugSection(val name: String, val data: ByteArray)

    private class SectionMerger(private val objects: List<ObjectFile>) {
        val text = ByteArrayOutputStream()
        val data = ByteArrayOutputStream()
        val rodata = ByteArrayOutputStream()

        val textPlacements = mutableListOf<SectionPlacement>()
        val dataPlacements = mutableListOf<SectionPlacement>()
        val rodataPlacements = mutableListOf<SectionPlacement>()
        val sectionKindMap = mutableMapOf<Pair<Int, String>, SectionKind>()
        val debugSections = mutableListOf<DebugSection>()

        init {
            val debugBufs = mutableMapOf<String, ByteArrayOutputStream>()

            for ((objIdx, obj) in objects.withIndex()) {
                for (sec in obj.sections) {
                    if (sec.kind.isNonLoaded) {
                        debugBufs.getOrPut(sec.name) { ByteArrayOutputStream() }.write(sec.data)
                        continue
                    }
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

            for ((name, buf) in debugBufs) {
                debugSections.add(DebugSection(name, buf.toByteArray()))
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
                        val existing = globalSymbols[sym.name]
                        // GLOBAL always wins over WEAK; skip if existing is GLOBAL
                        if (existing != null && existing.binding == SymbolBinding.GLOBAL && sym.binding == SymbolBinding.WEAK) continue
                        globalSymbols[sym.name] = ResolvedSymbol(
                            sym.name, sym.value + placement.mergedOffset, kind, objIdx,
                            sym.binding, sym.kind, sym.size,
                        )
                    }
                }
            }
            undefinedSymbols.removeAll(globalSymbols.keys)
            return this
        }

        val exportedSymbols: List<ResolvedSymbol>
            get() = globalSymbols.values
                .filter { it.binding == SymbolBinding.GLOBAL || it.binding == SymbolBinding.WEAK }
                .sortedBy { it.name }

        val importedSymbols: List<String>
            get() = undefinedSymbols.toList().sorted()
    }

    private class SharedLayout(
        val merger: SectionMerger,
        val symbols: SymbolResolver,
        soname: String?,
        sharedLibs: List<String>,
        objects: List<ObjectFile>,
        machine: Int,
    ) {
        val importedSymbols = symbols.importedSymbols
        val exportedSymbols = symbols.exportedSymbols
        val numPltEntries = importedSymbols.size

        val dynstr = StringTable()
        val dynstrLibOffsets = mutableMapOf<String, Int>()
        val dynstrSymOffsets = mutableMapOf<String, Int>()
        val sonameOffset: Int

        val dynsymBytes: ByteArray
        val hashBytes: ByteArray
        val dynstrBytes: ByteArray

        // dynsym layout: [null] + [imports (1..numPlt)] + [exports (numPlt+1..)]
        val importSymIdxBase = 1
        val exportSymIdxBase = 1 + importedSymbols.size

        // Number of program headers: LOAD(RX) + LOAD(RW) + DYNAMIC + GNU_STACK
        val numPhdrs = 4
        val headerSize = Elf.EHDR64_SIZE + Elf.PHDR64_SIZE * numPhdrs

        val hashOffset: Long; val hashVaddr: Long
        val dynsymOffset: Long; val dynsymVaddr: Long
        val dynstrOffset: Long; val dynstrVaddr: Long
        val relaPltOffset: Long; val relaPltVaddr: Long; val relaPltSize: Long
        val relaDynOffset: Long; val relaDynVaddr: Long; val relaDynSize: Long
        val pltOffset: Long; val pltVaddr: Long; val pltSize: Int
        val textOffset: Long; val textVaddr: Long
        val rodataOffset: Long; val rodataVaddr: Long
        val rxSegmentEnd: Long

        val gotOffset: Long; val gotVaddr: Long
        val dynamicOffset: Long; val dynamicVaddr: Long
        val dynamicEntries: List<Pair<Long, Long>>
        val dynamicSize: Int
        val dataOffset: Long; val dataVaddr: Long
        val rwVaddrBase: Long; val rwSegmentFileSize: Long

        val hasDebugSections = merger.debugSections.isNotEmpty()
        val debugOffsets = mutableListOf<Long>()
        val shstrtabOffset: Long
        val shstrtabData: ByteArray
        val shoff: Long
        val numShdrs: Int

        // Data relocations that need R_RELATIVE at runtime
        val dataRelocations = mutableListOf<Long>()

        init {
            sonameOffset = if (soname != null) dynstr.add(soname) else 0
            for (lib in sharedLibs) dynstrLibOffsets[lib] = dynstr.add(lib)
            for (sym in importedSymbols) dynstrSymOffsets[sym] = dynstr.add(sym)
            for (sym in exportedSymbols) dynstrSymOffsets[sym.name] = dynstr.add(sym.name)
            dynstrBytes = dynstr.toByteArray()

            val dynsymBuf = ByteArrayOutputStream()
            // Null entry
            writeSymEntry(dynsymBuf, 0, 0, 0, Elf.SHN_UNDEF, 0, 0)
            // Imported symbols (undefined)
            for (sym in importedSymbols) {
                writeSymEntry(dynsymBuf, dynstrSymOffsets[sym]!!,
                    Elf.stInfo(ElfSymbolBinding.GLOBAL, ElfSymbolType.FUNC),
                    ElfSymbolVisibility.DEFAULT.code, Elf.SHN_UNDEF, 0, 0)
            }
            // Exported symbols (defined) — will patch value after layout
            for (sym in exportedSymbols) {
                val elfType = if (sym.kind == SymbolKind.FUNCTION) ElfSymbolType.FUNC else ElfSymbolType.OBJECT
                val elfBind = if (sym.binding == SymbolBinding.WEAK) ElfSymbolBinding.WEAK else ElfSymbolBinding.GLOBAL
                writeSymEntry(dynsymBuf, dynstrSymOffsets[sym.name]!!,
                    Elf.stInfo(elfBind, elfType),
                    ElfSymbolVisibility.DEFAULT.code,
                    1, // shndx placeholder (will be patched or ignored by loader)
                    0, // value placeholder — patched after layout
                    sym.size)
            }
            dynsymBytes = dynsymBuf.toByteArray()

            val allSymNames = importedSymbols + exportedSymbols.map { it.name }
            hashBytes = buildSysvHash(allSymNames)

            val textBytes = merger.text.toByteArray()
            val rodataBytes = merger.rodata.toByteArray()
            val gotReserved = 3 // GOT[0]=_DYNAMIC, GOT[1]=0, GOT[2]=0
            val gotSize = (gotReserved + numPltEntries) * GOT_ENTRY_SIZE
            pltSize = if (numPltEntries > 0) PLT0_SIZE + numPltEntries * PLT_ENTRY_SIZE else 0

            // Count absolute data relocations that need R_RELATIVE at runtime.
            // In a shared library, absolute relocations in .data become R_RELATIVE
            // so the dynamic linker can fix them up when the library is loaded at
            // a different base address.
            var numRelaDyn = 0
            for ((objIdx, obj) in objects.withIndex()) {
                for (rel in obj.relocations) {
                    if (rel.section == null) continue
                    val relKind = merger.sectionKindMap[objIdx to rel.section] ?: continue
                    if (relKind != SectionKind.DATA && relKind != SectionKind.RODATA) continue
                    val isAbsolute = when (machine) {
                        ElfMachine.X86_64.code -> rel.type == RelocationType.X86_64.R_64
                        ElfMachine.AARCH64.code -> rel.type == RelocationType.AArch64.ABS64
                        ElfMachine.RISCV.code -> rel.type == RelocationType.RiscV.R_64
                        else -> false
                    }
                    if (isAbsolute) numRelaDyn++
                }
            }
            relaDynSize = numRelaDyn.toLong() * Elf.RELA64_SIZE

            // RX segment layout
            var off = headerSize.toLong()
            off = align(off, 8); hashOffset = off; hashVaddr = BASE_ADDR + off; off += hashBytes.size
            off = align(off, 8); dynsymOffset = off; dynsymVaddr = BASE_ADDR + off; off += dynsymBytes.size
            dynstrOffset = off; dynstrVaddr = BASE_ADDR + off; off += dynstrBytes.size
            off = align(off, 8); relaPltOffset = off; relaPltVaddr = BASE_ADDR + off
            relaPltSize = numPltEntries.toLong() * Elf.RELA64_SIZE; off += relaPltSize
            off = align(off, 8); relaDynOffset = off; relaDynVaddr = BASE_ADDR + off; off += relaDynSize
            off = align(off, 16); pltOffset = off; pltVaddr = BASE_ADDR + off; off += pltSize
            off = align(off, 16); textOffset = off; textVaddr = BASE_ADDR + off; off += textBytes.size
            if (rodataBytes.isNotEmpty()) off = align(off, 16)
            rodataOffset = off; rodataVaddr = BASE_ADDR + off; off += rodataBytes.size
            rxSegmentEnd = off

            // RW segment
            val rwFileOff = align(rxSegmentEnd, PAGE_SIZE)
            rwVaddrBase = align(BASE_ADDR + rwFileOff, PAGE_SIZE)
            gotOffset = rwFileOff; gotVaddr = rwVaddrBase
            var rwOff = rwFileOff + gotSize
            rwOff = align(rwOff, 8)
            dynamicOffset = rwOff; dynamicVaddr = rwVaddrBase + (rwOff - gotOffset)

            val dynEntries = mutableListOf<Pair<Long, Long>>()
            if (soname != null) dynEntries.add(ElfDynamicTag.SONAME.code to sonameOffset.toLong())
            for (lib in sharedLibs) dynEntries.add(ElfDynamicTag.NEEDED.code to dynstrLibOffsets[lib]!!.toLong())
            dynEntries.add(ElfDynamicTag.STRTAB.code to dynstrVaddr)
            dynEntries.add(ElfDynamicTag.STRSZ.code to dynstrBytes.size.toLong())
            dynEntries.add(ElfDynamicTag.SYMTAB.code to dynsymVaddr)
            dynEntries.add(ElfDynamicTag.SYMENT.code to Elf.SYM64_SIZE.toLong())
            if (numPltEntries > 0) {
                dynEntries.add(ElfDynamicTag.PLTGOT.code to gotVaddr)
                dynEntries.add(ElfDynamicTag.PLTRELSZ.code to relaPltSize)
                dynEntries.add(ElfDynamicTag.PLTREL.code to ElfDynamicTag.RELA.code.toLong())
                dynEntries.add(ElfDynamicTag.JMPREL.code to relaPltVaddr)
            }
            if (relaDynSize > 0) {
                dynEntries.add(ElfDynamicTag.RELA.code to relaDynVaddr)
                dynEntries.add(ElfDynamicTag.RELASZ.code to relaDynSize)
                dynEntries.add(ElfDynamicTag.RELAENT.code to Elf.RELA64_SIZE.toLong())
            }
            dynEntries.add(ElfDynamicTag.NULL.code to 0L)
            dynamicEntries = dynEntries
            dynamicSize = dynEntries.size * 16
            rwOff = dynamicOffset + dynamicSize

            val dataBytes = merger.data.toByteArray()
            if (dataBytes.isNotEmpty()) rwOff = align(rwOff, 8)
            dataOffset = rwOff; dataVaddr = rwVaddrBase + (rwOff - gotOffset)
            rwOff += dataBytes.size
            rwSegmentFileSize = rwOff - gotOffset

            var dbgOff = rwOff
            if (hasDebugSections) {
                for (dbg in merger.debugSections) {
                    debugOffsets.add(dbgOff)
                    dbgOff += dbg.data.size
                }
                val shstrtabBuf = ByteArrayOutputStream()
                shstrtabBuf.write(0)
                for (dbg in merger.debugSections) {
                    shstrtabBuf.write(dbg.name.toByteArray(Charsets.US_ASCII))
                    shstrtabBuf.write(0)
                }
                shstrtabBuf.write(".shstrtab".toByteArray(Charsets.US_ASCII))
                shstrtabBuf.write(0)
                shstrtabData = shstrtabBuf.toByteArray()
                shstrtabOffset = dbgOff
                dbgOff += shstrtabData.size
                numShdrs = 1 + merger.debugSections.size + 1
                dbgOff = align(dbgOff, 8)
                shoff = dbgOff
            } else {
                shstrtabData = ByteArray(0)
                shstrtabOffset = 0
                shoff = 0
                numShdrs = 0
            }
        }

        val sectionKindVaddr: Map<SectionKind, Long>
            get() = mapOf(
                SectionKind.TEXT to textVaddr,
                SectionKind.RODATA to rodataVaddr,
                SectionKind.DATA to dataVaddr,
            )

        fun symbolVaddrs(): Map<String, Long> {
            val result = mutableMapOf<String, Long>()
            for ((name, resolved) in symbols.globalSymbols) {
                result[name] = (sectionKindVaddr[resolved.sectionKind] ?: 0L) + resolved.value
            }
            return result
        }

        fun pltSymbolVaddrs(): Map<String, Long> {
            val result = mutableMapOf<String, Long>()
            for ((i, sym) in importedSymbols.withIndex()) {
                result[sym] = pltVaddr + PLT0_SIZE + i * PLT_ENTRY_SIZE
            }
            return result
        }

        fun patchDynsymExportValues(): ByteArray {
            val patched = dynsymBytes.copyOf()
            val symVaddrs = symbolVaddrs()
            for ((i, sym) in exportedSymbols.withIndex()) {
                val symIdx = exportSymIdxBase + i
                val entryOff = symIdx * Elf.SYM64_SIZE
                val vaddr = symVaddrs[sym.name] ?: 0L
                // st_value is at offset 8 in Elf64_Sym
                for (b in 0 until 8) {
                    patched[entryOff + 8 + b] = ((vaddr shr (b * 8)) and 0xFF).toByte()
                }
            }
            return patched
        }

        private fun writeSymEntry(
            buf: ByteArrayOutputStream, name: Int, info: Int, other: Int,
            shndx: Int, value: Long, size: Long,
        ) {
            writeU32(buf, name); buf.write(info); buf.write(other)
            writeU16(buf, shndx); writeU64(buf, value); writeU64(buf, size)
        }

        private fun buildSysvHash(syms: List<String>): ByteArray {
            val nbucket = maxOf(syms.size, 1)
            val nchain = syms.size + 1
            val buckets = IntArray(nbucket)
            val chains = IntArray(nchain)
            for ((i, sym) in syms.withIndex()) {
                val symIdx = i + 1
                val h = elfHash(sym) % nbucket.toUInt()
                chains[symIdx] = buckets[h.toInt()]
                buckets[h.toInt()] = symIdx
            }
            val buf = ByteArrayOutputStream()
            writeU32(buf, nbucket); writeU32(buf, nchain)
            for (b in buckets) writeU32(buf, b)
            for (c in chains) writeU32(buf, c)
            return buf.toByteArray()
        }

        private fun elfHash(name: String): UInt {
            var h = 0u
            for (c in name) {
                h = (h shl 4) + c.code.toUInt()
                val g = h and 0xF0000000u
                if (g != 0u) h = h xor (g shr 24)
                h = h and g.inv()
            }
            return h
        }
    }

    private class RelocationApplier(
        private val objects: List<ObjectFile>,
        private val merger: SectionMerger,
        private val symbols: SymbolResolver,
        private val layout: SharedLayout,
        private val machine: Int,
    ) {
        /** Vaddrs of data slots that need R_RELATIVE dynamic relocations. */
        val dataRelocations = mutableListOf<Pair<Long, Long>>() // (vaddr, resolved value)

        /** Apply relocations to text and data, returning the relocated text bytes. */
        fun apply(): ByteArray {
            val symVaddrs = layout.symbolVaddrs()
            val pltVaddrs = layout.pltSymbolVaddrs()
            val textBytes = merger.text.toByteArray().copyOf()
            val dataBytes = merger.data.toByteArray().copyOf()

            for ((objIdx, obj) in objects.withIndex()) {
                for (rel in obj.relocations) {
                    if (rel.section == null) continue
                    val relKind = merger.sectionKindMap[objIdx to rel.section] ?: continue

                    val targetVaddr = symVaddrs[rel.symbol] ?: pltVaddrs[rel.symbol]
                        ?: throw IllegalStateException("Undefined symbol: ${rel.symbol}")

                    when (relKind) {
                        SectionKind.TEXT -> {
                            val placement = merger.textPlacements
                                .firstOrNull { it.objIdx == objIdx && it.sectionName == rel.section }
                                ?: continue
                            val patchOffset = (placement.mergedOffset + rel.offset).toInt()
                            val patchVaddr = layout.textVaddr + patchOffset
                            applyTextRelocation(textBytes, patchOffset, patchVaddr, targetVaddr, rel)
                        }
                        SectionKind.DATA -> {
                            val placement = merger.dataPlacements
                                .firstOrNull { it.objIdx == objIdx && it.sectionName == rel.section }
                                ?: continue
                            val patchOffset = (placement.mergedOffset + rel.offset).toInt()
                            val patchVaddr = layout.dataVaddr + patchOffset
                            applyDataRelocation(dataBytes, patchOffset, patchVaddr, targetVaddr, rel)
                        }
                        else -> continue
                    }
                }
            }

            // Copy relocated data back to merger's data stream
            val mergedData = merger.data.toByteArray()
            System.arraycopy(dataBytes, 0, mergedData, 0, minOf(dataBytes.size, mergedData.size))

            return textBytes
        }

        private fun applyTextRelocation(
            text: ByteArray, offset: Int, patchVaddr: Long, targetVaddr: Long, rel: Relocation,
        ) {
            when (machine) {
                ElfMachine.X86_64.code -> when (rel.type) {
                    RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32 ->
                        putI32(text, offset, (targetVaddr + rel.addend - patchVaddr).toInt())
                    RelocationType.X86_64.R_32, RelocationType.X86_64.R_32S ->
                        putI32(text, offset, (targetVaddr + rel.addend).toInt())
                    else -> error("Unsupported x86-64 text relocation: ${rel.type}")
                }
                ElfMachine.AARCH64.code -> when (rel.type) {
                    RelocationType.AArch64.CALL26, RelocationType.AArch64.JUMP26 -> {
                        val disp = ((targetVaddr + rel.addend - patchVaddr) shr 2).toInt()
                        val insn = readI32(text, offset)
                        putI32(text, offset, (insn and 0xFC000000.toInt()) or (disp and 0x03FFFFFF))
                    }
                    RelocationType.AArch64.ADR_PREL_PG_HI21 -> {
                        val page = ((targetVaddr + rel.addend) and -4096L) - (patchVaddr and -4096L)
                        val immHi = ((page shr 12) and 0x7FFFF).toInt()
                        val immLo = ((page shr 12) shr 19 and 0x3).toInt()
                        val insn = readI32(text, offset)
                        putI32(text, offset, (insn and 0x9F00001F.toInt()) or (immHi shl 5) or (immLo shl 29))
                    }
                    RelocationType.AArch64.ADD_ABS_LO12_NC -> {
                        val imm = ((targetVaddr + rel.addend) and 0xFFF).toInt()
                        val insn = readI32(text, offset)
                        putI32(text, offset, (insn and 0xFFC003FF.toInt()) or (imm shl 10))
                    }
                    else -> error("Unsupported ARM64 text relocation: ${rel.type}")
                }
                ElfMachine.RISCV.code -> when (rel.type) {
                    RelocationType.RiscV.CALL, RelocationType.RiscV.CALL_PLT -> {
                        val delta = targetVaddr + rel.addend - patchVaddr
                        val hi = ((delta + 0x800) shr 12).toInt()
                        val lo = (delta.toInt()) and 0xFFF
                        val auipc = readI32(text, offset)
                        putI32(text, offset, (auipc and 0xFFF) or (hi shl 12))
                        val jalr = readI32(text, offset + 4)
                        putI32(text, offset + 4, (jalr and 0x000FFFFF) or (lo shl 20))
                    }
                    RelocationType.RiscV.PCREL_HI20 -> {
                        val delta = targetVaddr + rel.addend - patchVaddr
                        val hi = ((delta + 0x800) shr 12).toInt()
                        val existing = readI32(text, offset)
                        putI32(text, offset, (existing and 0xFFF) or (hi shl 12))
                    }
                    RelocationType.RiscV.LO12_I -> {
                        val value = (targetVaddr + rel.addend).toInt() and 0xFFF
                        val existing = readI32(text, offset)
                        putI32(text, offset, (existing and 0x000FFFFF) or (value shl 20))
                    }
                    RelocationType.RiscV.LO12_S -> {
                        val value = (targetVaddr + rel.addend).toInt() and 0xFFF
                        val imm11_5 = (value shr 5) and 0x7F
                        val imm4_0 = value and 0x1F
                        val existing = readI32(text, offset)
                        val mask = (0x7F shl 25) or (0x1F shl 7)
                        putI32(text, offset, (existing and mask.inv()) or (imm11_5 shl 25) or (imm4_0 shl 7))
                    }
                    RelocationType.RiscV.JAL -> {
                        val delta = (targetVaddr + rel.addend - patchVaddr).toInt()
                        val existing = readI32(text, offset)
                        val imm20 = (delta shr 20) and 0x1
                        val imm10_1 = (delta shr 1) and 0x3FF
                        val imm11 = (delta shr 11) and 0x1
                        val imm19_12 = (delta shr 12) and 0xFF
                        val encoded = (imm20 shl 31) or (imm10_1 shl 21) or (imm11 shl 20) or (imm19_12 shl 12)
                        putI32(text, offset, (existing and 0xFFF) or encoded)
                    }
                    RelocationType.RiscV.BRANCH -> {
                        val delta = (targetVaddr + rel.addend - patchVaddr).toInt()
                        val existing = readI32(text, offset)
                        val imm12 = (delta shr 12) and 0x1
                        val imm10_5 = (delta shr 5) and 0x3F
                        val imm4_1 = (delta shr 1) and 0xF
                        val imm11 = (delta shr 11) and 0x1
                        val encoded = (imm12 shl 31) or (imm10_5 shl 25) or (imm4_1 shl 8) or (imm11 shl 7)
                        val mask = (0x1 shl 31) or (0x3F shl 25) or (0xF shl 8) or (0x1 shl 7)
                        putI32(text, offset, (existing and mask.inv()) or encoded)
                    }
                    RelocationType.RiscV.RELAX -> { /* no-op */ }
                    else -> error("Unsupported RISC-V text relocation: ${rel.type}")
                }
                else -> error("Unsupported machine for text relocation: $machine")
            }
        }

        private fun applyDataRelocation(
            data: ByteArray, offset: Int, patchVaddr: Long, targetVaddr: Long, rel: Relocation,
        ) {
            val isAbsolute = when (machine) {
                ElfMachine.X86_64.code -> rel.type == RelocationType.X86_64.R_64
                ElfMachine.AARCH64.code -> rel.type == RelocationType.AArch64.ABS64
                ElfMachine.RISCV.code -> rel.type == RelocationType.RiscV.R_64
                else -> false
            }
            if (isAbsolute) {
                val value = targetVaddr + rel.addend
                putI64(data, offset, value)
                // Record for R_RELATIVE dynamic relocation
                dataRelocations.add(patchVaddr to value)
            }
        }

        private fun putI32(data: ByteArray, offset: Int, value: Int) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value shr 8) and 0xFF).toByte()
            data[offset + 2] = ((value shr 16) and 0xFF).toByte()
            data[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        private fun putI64(data: ByteArray, offset: Int, value: Long) {
            for (b in 0 until 8) data[offset + b] = ((value shr (b * 8)) and 0xFF).toByte()
        }

        private fun readI32(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private class SharedEmitter(
        private val layout: SharedLayout,
        private val relocatedText: ByteArray,
        private val dataRelocations: List<Pair<Long, Long>>,
        private val merger: SectionMerger,
        private val symbols: SymbolResolver,
        private val machine: Int,
    ) {
        fun emit(): ByteArray {
            val buf = ByteArrayOutputStream()
            emitElfHeader(buf)
            emitProgramHeaders(buf)
            emitRxSegment(buf)
            emitRwSegment(buf)
            if (layout.hasDebugSections) {
                emitDebugSections(buf)
                emitSectionHeaders(buf)
            }
            return buf.toByteArray()
        }

        private fun emitElfHeader(buf: ByteArrayOutputStream) {
            buf.write(Elf.MAGIC)
            buf.write(ElfClass.ELF64.code)
            buf.write(ElfData.LSB.code)
            buf.write(Elf.VERSION)
            buf.write(0) // OS/ABI
            buf.write(ByteArray(8)) // padding
            writeU16(buf, ElfObjectType.DYN.code)
            writeU16(buf, machine)
            writeU32(buf, Elf.VERSION)
            writeU64(buf, 0) // entry point — shared libraries have no entry
            writeU64(buf, Elf.EHDR64_SIZE.toLong()) // phoff
            writeU64(buf, layout.shoff) // shoff
            writeU32(buf, 0) // flags
            writeU16(buf, Elf.EHDR64_SIZE)
            writeU16(buf, Elf.PHDR64_SIZE)
            writeU16(buf, layout.numPhdrs)
            writeU16(buf, if (layout.hasDebugSections) Elf.SHDR64_SIZE else 0)
            writeU16(buf, layout.numShdrs)
            writeU16(buf, if (layout.hasDebugSections) layout.numShdrs - 1 else 0)
        }

        private fun emitProgramHeaders(buf: ByteArrayOutputStream) {
            // LOAD RX
            writePhdr(buf, ElfSegmentType.LOAD.code, ElfSegmentFlags.R or ElfSegmentFlags.X,
                0, BASE_ADDR, BASE_ADDR, layout.rxSegmentEnd, layout.rxSegmentEnd, PAGE_SIZE)

            // LOAD RW
            writePhdr(buf, ElfSegmentType.LOAD.code, ElfSegmentFlags.R or ElfSegmentFlags.W,
                layout.gotOffset, layout.rwVaddrBase, layout.rwVaddrBase,
                layout.rwSegmentFileSize, layout.rwSegmentFileSize, PAGE_SIZE)

            // DYNAMIC
            writePhdr(buf, ElfSegmentType.DYNAMIC.code, ElfSegmentFlags.R or ElfSegmentFlags.W,
                layout.dynamicOffset, layout.dynamicVaddr, layout.dynamicVaddr,
                layout.dynamicSize.toLong(), layout.dynamicSize.toLong(), 8)

            // GNU_STACK
            writePhdr(buf, ElfSegmentType.GNU_STACK.code, ElfSegmentFlags.R or ElfSegmentFlags.W,
                0, 0, 0, 0, 0, 16)
        }

        private fun emitRxSegment(buf: ByteArrayOutputStream) {
            val patchedDynsym = layout.patchDynsymExportValues()

            padTo(buf, layout.hashOffset.toInt()); buf.write(layout.hashBytes)
            padTo(buf, layout.dynsymOffset.toInt()); buf.write(patchedDynsym)
            padTo(buf, layout.dynstrOffset.toInt()); buf.write(layout.dynstrBytes)
            if (layout.relaPltSize > 0) {
                padTo(buf, layout.relaPltOffset.toInt()); buf.write(buildRelaPlt())
            }
            if (layout.relaDynSize > 0) {
                padTo(buf, layout.relaDynOffset.toInt()); buf.write(buildRelaDyn())
            }
            if (layout.pltSize > 0) {
                padTo(buf, layout.pltOffset.toInt()); buf.write(buildPlt())
            }
            padTo(buf, layout.textOffset.toInt()); buf.write(relocatedText)
            val rodataBytes = merger.rodata.toByteArray()
            if (rodataBytes.isNotEmpty()) {
                padTo(buf, layout.rodataOffset.toInt()); buf.write(rodataBytes)
            }
        }

        private fun emitDebugSections(buf: ByteArrayOutputStream) {
            for ((i, dbg) in merger.debugSections.withIndex()) {
                padTo(buf, layout.debugOffsets[i].toInt())
                buf.write(dbg.data)
            }
            padTo(buf, layout.shstrtabOffset.toInt())
            buf.write(layout.shstrtabData)
        }

        private fun emitSectionHeaders(buf: ByteArrayOutputStream) {
            padTo(buf, layout.shoff.toInt())
            writeSectionHeader(buf, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            var nameOff = 1
            for ((i, dbg) in merger.debugSections.withIndex()) {
                writeSectionHeader(buf, nameOff, ElfSectionType.PROGBITS.code,
                    0, 0, layout.debugOffsets[i], dbg.data.size.toLong(),
                    0, 0, 1, 0)
                nameOff += dbg.name.length + 1
            }
            writeSectionHeader(buf, nameOff, ElfSectionType.STRTAB.code,
                0, 0, layout.shstrtabOffset, layout.shstrtabData.size.toLong(),
                0, 0, 1, 0)
        }

        private fun writeSectionHeader(
            buf: ByteArrayOutputStream,
            name: Int, type: Int, flags: Long, addr: Long, offset: Long, size: Long,
            link: Int, info: Int, addralign: Long, entsize: Long,
        ) {
            writeU32(buf, name); writeU32(buf, type)
            writeU64(buf, flags); writeU64(buf, addr)
            writeU64(buf, offset); writeU64(buf, size)
            writeU32(buf, link); writeU32(buf, info)
            writeU64(buf, addralign); writeU64(buf, entsize)
        }

        private fun emitRwSegment(buf: ByteArrayOutputStream) {
            padTo(buf, layout.gotOffset.toInt()); buf.write(buildGot())
            padTo(buf, layout.dynamicOffset.toInt())
            for ((tag, value) in layout.dynamicEntries) {
                writeU64(buf, tag); writeU64(buf, value)
            }
            val dataBytes = merger.data.toByteArray()
            if (dataBytes.isNotEmpty()) {
                padTo(buf, layout.dataOffset.toInt()); buf.write(dataBytes)
            }
        }

        private fun buildGot(): ByteArray {
            val buf = ByteArrayOutputStream()
            writeU64(buf, layout.dynamicVaddr) // GOT[0] = _DYNAMIC
            writeU64(buf, 0) // GOT[1] = link_map (filled by ld.so)
            writeU64(buf, 0) // GOT[2] = dl_resolve (filled by ld.so)
            for (i in 0 until layout.numPltEntries) {
                // Initially points to PLT stub's push instruction
                writeU64(buf, layout.pltVaddr + PLT0_SIZE + i * PLT_ENTRY_SIZE + 6)
            }
            return buf.toByteArray()
        }

        private fun buildRelaPlt(): ByteArray {
            val buf = ByteArrayOutputStream()
            val jumpSlotType = when (machine) {
                ElfMachine.AARCH64.code -> RelocationType.AArch64.JUMP_SLOT.value.toLong()
                ElfMachine.RISCV.code -> RelocationType.RiscV.JUMP_SLOT.value.toLong()
                else -> RelocationType.X86_64.JUMP_SLOT.value.toLong()
            }
            for (i in layout.importedSymbols.indices) {
                val gotEntryVaddr = layout.gotVaddr + (3 + i) * GOT_ENTRY_SIZE
                val symIdx = layout.importSymIdxBase + i
                writeU64(buf, gotEntryVaddr)
                writeU64(buf, (symIdx.toLong() shl 32) or jumpSlotType)
                writeS64(buf, 0)
            }
            return buf.toByteArray()
        }

        private fun buildRelaDyn(): ByteArray {
            val buf = ByteArrayOutputStream()
            val relativeType = when (machine) {
                ElfMachine.AARCH64.code -> RelocationType.AArch64.RELATIVE.value.toLong()
                ElfMachine.RISCV.code -> RelocationType.RiscV.RELATIVE.value.toLong()
                else -> RelocationType.X86_64.RELATIVE.value.toLong()
            }
            for ((vaddr, value) in dataRelocations) {
                writeU64(buf, vaddr)
                writeU64(buf, relativeType) // sym=0, type=R_RELATIVE
                writeS64(buf, value)        // addend = resolved absolute value
            }
            return buf.toByteArray()
        }

        private fun buildPlt(): ByteArray {
            if (layout.numPltEntries == 0) return ByteArray(0)
            return when (machine) {
                ElfMachine.AARCH64.code -> buildPltArm64()
                else -> buildPltX86()
            }
        }

        private fun buildPltX86(): ByteArray {
            val buf = ByteArrayOutputStream()
            // PLT0: push GOT[1]; jmp GOT[2]
            val got1Rip = layout.gotVaddr + 8 - (layout.pltVaddr + 6)
            val got2Rip = layout.gotVaddr + 16 - (layout.pltVaddr + 12)
            buf.write(byteArrayOf(0xFF.toByte(), 0x35))
            writeU32(buf, got1Rip.toInt())
            buf.write(byteArrayOf(0xFF.toByte(), 0x25))
            writeU32(buf, got2Rip.toInt())
            buf.write(ByteArray(4)) // padding
            // PLT entries: jmp *GOT[n]; push index; jmp PLT0
            for (i in 0 until layout.numPltEntries) {
                val entryVaddr = layout.pltVaddr + PLT0_SIZE + i * PLT_ENTRY_SIZE
                val gotEntryVaddr = layout.gotVaddr + (3 + i) * GOT_ENTRY_SIZE
                buf.write(byteArrayOf(0xFF.toByte(), 0x25))
                writeU32(buf, (gotEntryVaddr - (entryVaddr + 6)).toInt())
                buf.write(0x68)
                writeU32(buf, i)
                buf.write(0xE9.toByte().toInt())
                writeU32(buf, (layout.pltVaddr - (entryVaddr + 16)).toInt())
            }
            return buf.toByteArray()
        }

        private fun buildPltArm64(): ByteArray {
            val buf = ByteArrayOutputStream()
            // PLT0: stp x16, x30, [sp, #-16]!; adrp x16, GOT; ldr x17, [x16, #GOT@lo12]; add x16, x16, #GOT@lo12; br x17
            val got0Page = (layout.gotVaddr and -4096L) - (layout.pltVaddr and -4096L)
            val got0Lo = (layout.gotVaddr and 0xFFF).toInt()
            writeArm64Insn(buf, 0xA9BF7BF0.toInt()) // stp x16, x30, [sp, #-16]!
            writeArm64Insn(buf, adrp(16, got0Page))
            writeArm64Insn(buf, ldrImm64(17, 16, got0Lo + 16)) // GOT[2]
            writeArm64Insn(buf, 0xD61F0220.toInt())             // br x17
            // PLT entries: adrp x16, GOT[n]@page; ldr x17, [x16, GOT[n]@lo12]; br x17; nop
            for (i in 0 until layout.numPltEntries) {
                val entryVaddr = layout.pltVaddr + PLT0_SIZE + i.toLong() * PLT_ENTRY_SIZE
                val gotEntryVaddr = layout.gotVaddr + (3 + i).toLong() * GOT_ENTRY_SIZE
                val page = (gotEntryVaddr and -4096L) - (entryVaddr and -4096L)
                val lo = (gotEntryVaddr and 0xFFF).toInt()
                writeArm64Insn(buf, adrp(16, page))
                writeArm64Insn(buf, ldrImm64(17, 16, lo))
                writeArm64Insn(buf, 0xD61F0220.toInt()) // br x17
                writeArm64Insn(buf, 0xD503201F.toInt())          // nop
            }
            return buf.toByteArray()
        }

        private fun adrp(rd: Int, pageOffset: Long): Int {
            val immHi = ((pageOffset shr 12) and 0x7FFFF).toInt()
            val immLo = ((pageOffset shr 12) shr 19 and 0x3).toInt()
            return 0x90000000.toInt() or rd or (immHi shl 5) or (immLo shl 29)
        }

        private fun ldrImm64(rt: Int, rn: Int, offset: Int): Int =
            0xF9400000.toInt() or rt or (rn shl 5) or ((offset / 8) shl 10)

        private fun addImm(rd: Int, rn: Int, imm: Int): Int =
            0x91000000.toInt() or rd or (rn shl 5) or ((imm and 0xFFF) shl 10)

        private fun writeArm64Insn(buf: ByteArrayOutputStream, insn: Int) {
            buf.write(insn and 0xFF)
            buf.write((insn shr 8) and 0xFF)
            buf.write((insn shr 16) and 0xFF)
            buf.write((insn shr 24) and 0xFF)
        }

        private fun writePhdr(
            buf: ByteArrayOutputStream, type: Int, flags: Int,
            offset: Long, vaddr: Long, paddr: Long,
            filesz: Long, memsz: Long, align: Long,
        ) {
            writeU32(buf, type); writeU32(buf, flags)
            writeU64(buf, offset); writeU64(buf, vaddr); writeU64(buf, paddr)
            writeU64(buf, filesz); writeU64(buf, memsz); writeU64(buf, align)
        }
    }
}
