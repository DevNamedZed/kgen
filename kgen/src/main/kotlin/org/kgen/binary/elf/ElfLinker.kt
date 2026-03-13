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
 * Links relocatable ELF64 ObjectFiles into a dynamically-linked executable.
 *
 * Supports:
 * - Symbol resolution across multiple object files
 * - Dynamic linking (PLT/GOT) for external library calls (libc, etc.)
 * - Proper segment layout with .interp, .dynamic, .dynsym, .dynstr, .rela.plt
 * - PC32 and PLT32 relocations for x86-64
 */
class ElfLinker(
    private val machine: Int = ElfMachine.X86_64.code,
    private val interpreter: String = "/lib64/ld-linux-x86-64.so.2",
    private val sharedLibs: List<String> = listOf("libc.so.6"),
) {
    private companion object {
        const val BASE_ADDR = 0x400000L
        const val PAGE_SIZE = 0x1000L
        const val PLT_ENTRY_SIZE = 16
        const val PLT0_SIZE = 16
        const val GOT_ENTRY_SIZE = 8
    }

    fun link(objects: List<ObjectFile>): ByteArray {
        val merger = SectionMerger(objects)
        val symbols = SymbolResolver(objects, merger).resolve()
        val layout = SegmentLayout(merger, symbols, interpreter, sharedLibs)
        val relocated = RelocationApplier(objects, merger, symbols, layout).apply()
        return ElfEmitter(layout, relocated, merger, symbols, machine).emit()
    }

    private data class SectionPlacement(val objIdx: Int, val sectionName: String, val mergedOffset: Int, val size: Int)

    private data class ResolvedSymbol(val name: String, val value: Long, val sectionKind: SectionKind, val objIdx: Int)

    private data class DebugSection(val name: String, val data: ByteArray)

    private class SectionMerger(private val objects: List<ObjectFile>) {
        val text = ByteArrayOutputStream()
        val data = ByteArrayOutputStream()
        val rodata = ByteArrayOutputStream()
        val ehFrame = ByteArrayOutputStream()
        val ehFrameHdr = ByteArrayOutputStream()
        val gccExceptTable = ByteArrayOutputStream()
        val tdata = ByteArrayOutputStream()
        var tdataAlign = 1

        val textPlacements = mutableListOf<SectionPlacement>()
        val dataPlacements = mutableListOf<SectionPlacement>()
        val rodataPlacements = mutableListOf<SectionPlacement>()
        val ehFramePlacements = mutableListOf<SectionPlacement>()
        val ehFrameHdrPlacements = mutableListOf<SectionPlacement>()
        val gccExceptTablePlacements = mutableListOf<SectionPlacement>()
        val tdataPlacements = mutableListOf<SectionPlacement>()
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
                        SectionKind.EH_FRAME -> ehFrame to ehFramePlacements
                        SectionKind.EH_FRAME_HDR -> ehFrameHdr to ehFrameHdrPlacements
                        SectionKind.GCC_EXCEPT_TABLE -> gccExceptTable to gccExceptTablePlacements
                        SectionKind.TDATA -> {
                            if (sec.align > tdataAlign) tdataAlign = sec.align
                            tdata to tdataPlacements
                        }
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
            SectionKind.EH_FRAME -> ehFramePlacements
            SectionKind.EH_FRAME_HDR -> ehFrameHdrPlacements
            SectionKind.GCC_EXCEPT_TABLE -> gccExceptTablePlacements
            SectionKind.TDATA -> tdataPlacements
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
        var needsStartStub = false

        fun resolve(): SymbolResolver {
            // Track binding strength so GLOBAL overrides WEAK
            val symbolBindings = mutableMapOf<String, SymbolBinding>()

            for ((objIdx, obj) in objects.withIndex()) {
                for (sym in obj.symbols) {
                    if (sym.section == null || sym.kind == SymbolKind.UNDEFINED) {
                        undefinedSymbols.add(sym.name)
                    } else {
                        val kind = merger.sectionKindMap[objIdx to sym.section] ?: continue
                        val placement = merger.placementsFor(kind)
                            .firstOrNull { it.objIdx == objIdx && it.sectionName == sym.section } ?: continue
                        val existing = symbolBindings[sym.name]
                        // GLOBAL always wins over WEAK; skip if existing is GLOBAL
                        if (existing == SymbolBinding.GLOBAL && sym.binding == SymbolBinding.WEAK) continue
                        globalSymbols[sym.name] = ResolvedSymbol(sym.name, sym.value + placement.mergedOffset, kind, objIdx)
                        symbolBindings[sym.name] = sym.binding
                    }
                }
            }
            undefinedSymbols.removeAll(globalSymbols.keys)

            // If there's no _start but there is main, generate a _start stub
            // that calls main directly and then exits via exit@plt
            if ("_start" !in globalSymbols && "main" in globalSymbols && undefinedSymbols.isNotEmpty()) {
                needsStartStub = true
                undefinedSymbols.add("exit")
            }

            return this
        }

        val entrySymbol: ResolvedSymbol
            get() = globalSymbols["_start"] ?: globalSymbols["main"]
                ?: throw IllegalStateException("No _start or main symbol found")

        val dynamicSymbols: List<String>
            get() = undefinedSymbols.toList().sorted()
    }

    private class SegmentLayout(
        val merger: SectionMerger,
        val symbols: SymbolResolver,
        interpreter: String,
        sharedLibs: List<String>,
    ) {
        val interpBytes = (interpreter + "\u0000").toByteArray(Charsets.US_ASCII)
        val dynamicSymbols = symbols.dynamicSymbols
        val numPltEntries = dynamicSymbols.size
        // _start stub: xor+and+call_main+mov+call_exit+hlt = 19 bytes, padded to 32
        val startStubSize = if (symbols.needsStartStub) 32 else 0

        val dynstr = StringTable()
        val dynstrNameOffsets = mutableMapOf<String, Int>()
        val dynSymNameOffsets = mutableMapOf<String, Int>()

        val dynsymBytes: ByteArray
        val hashBytes: ByteArray
        val dynstrBytes: ByteArray

        val hasTdata = merger.tdata.size() > 0
        val numPhdrs = 6 + (if (hasTdata) 1 else 0)
        val headerSize = Elf.EHDR64_SIZE + Elf.PHDR64_SIZE * numPhdrs

        // RX segment offsets and virtual addresses
        val interpOffset: Long
        val interpVaddr: Long
        val hashOffset: Long
        val hashVaddr: Long
        val dynsymOffset: Long
        val dynsymVaddr: Long
        val dynstrOffset: Long
        val dynstrVaddr: Long
        val relaPltOffset: Long
        val relaPltVaddr: Long
        val relaPltSize: Long
        val pltOffset: Long
        val pltVaddr: Long
        val pltSize: Int
        val textOffset: Long
        val textVaddr: Long
        val rodataOffset: Long
        val rodataVaddr: Long
        val ehFrameOffset: Long
        val ehFrameVaddr: Long
        val ehFrameHdrOffset: Long
        val ehFrameHdrVaddr: Long
        val gccExceptTableOffset: Long
        val gccExceptTableVaddr: Long
        val rxSegmentEnd: Long

        // RW segment
        val gotOffset: Long
        val gotVaddr: Long
        val dynamicOffset: Long
        val dynamicVaddr: Long
        val dynamicEntries: List<Pair<Long, Long>>
        val dynamicSize: Int
        val dataOffset: Long
        val dataVaddr: Long
        val rwVaddrBase: Long
        val tdataOffset: Long
        val tdataVaddr: Long
        val tdataFileSize: Long
        val tdataAlign: Int
        val rwSegmentFileSize: Long

        // Debug sections (non-loaded, after all segments)
        val hasDebugSections = merger.debugSections.isNotEmpty()
        val debugOffsets = mutableListOf<Long>()
        val shstrtabOffset: Long
        val shstrtabData: ByteArray
        val shoff: Long
        val numSections: Int

        init {
            for (lib in sharedLibs) dynstrNameOffsets[lib] = dynstr.add(lib)
            for (sym in dynamicSymbols) dynSymNameOffsets[sym] = dynstr.add(sym)
            dynstrBytes = dynstr.toByteArray()

            val dynsymBuf = ByteArrayOutputStream()
            writeSymEntry(dynsymBuf, 0, 0, 0, Elf.SHN_UNDEF, 0, 0)
            for (sym in dynamicSymbols) {
                writeSymEntry(dynsymBuf, dynSymNameOffsets[sym]!!,
                    Elf.stInfo(ElfSymbolBinding.GLOBAL, ElfSymbolType.FUNC),
                    ElfSymbolVisibility.DEFAULT.code, Elf.SHN_UNDEF, 0, 0)
            }
            dynsymBytes = dynsymBuf.toByteArray()
            hashBytes = buildSysvHash(dynamicSymbols, dynSymNameOffsets)

            val textBytes = merger.text.toByteArray()
            val rodataBytes = merger.rodata.toByteArray()
            val gotSize = (3 + numPltEntries) * GOT_ENTRY_SIZE
            pltSize = if (numPltEntries > 0) PLT0_SIZE + numPltEntries * PLT_ENTRY_SIZE else 0

            // Compute RX segment layout
            var off = headerSize.toLong()
            interpOffset = off; interpVaddr = BASE_ADDR + off; off += interpBytes.size
            off = align(off, 8); hashOffset = off; hashVaddr = BASE_ADDR + off; off += hashBytes.size
            off = align(off, 8); dynsymOffset = off; dynsymVaddr = BASE_ADDR + off; off += dynsymBytes.size
            dynstrOffset = off; dynstrVaddr = BASE_ADDR + off; off += dynstrBytes.size
            off = align(off, 8); relaPltOffset = off; relaPltVaddr = BASE_ADDR + off
            relaPltSize = numPltEntries.toLong() * Elf.RELA64_SIZE; off += relaPltSize
            off = align(off, 16); pltOffset = off; pltVaddr = BASE_ADDR + off; off += pltSize
            off = align(off, 16); textOffset = off; textVaddr = BASE_ADDR + off; off += startStubSize + textBytes.size
            if (rodataBytes.isNotEmpty()) off = align(off, 16)
            rodataOffset = off; rodataVaddr = BASE_ADDR + off; off += rodataBytes.size
            val ehFrameBytes = merger.ehFrame.toByteArray()
            if (ehFrameBytes.isNotEmpty()) off = align(off, 8)
            ehFrameOffset = off; ehFrameVaddr = BASE_ADDR + off; off += ehFrameBytes.size
            val ehFrameHdrBytes = merger.ehFrameHdr.toByteArray()
            if (ehFrameHdrBytes.isNotEmpty()) off = align(off, 4)
            ehFrameHdrOffset = off; ehFrameHdrVaddr = BASE_ADDR + off; off += ehFrameHdrBytes.size
            val gccExceptTableBytes = merger.gccExceptTable.toByteArray()
            if (gccExceptTableBytes.isNotEmpty()) off = align(off, 4)
            gccExceptTableOffset = off; gccExceptTableVaddr = BASE_ADDR + off; off += gccExceptTableBytes.size
            rxSegmentEnd = off

            // Compute RW segment layout
            val rwFileOff = align(rxSegmentEnd, PAGE_SIZE)
            rwVaddrBase = align(BASE_ADDR + rwFileOff, PAGE_SIZE)
            gotOffset = rwFileOff; gotVaddr = rwVaddrBase
            var rwOff = rwFileOff + gotSize
            rwOff = align(rwOff, 8)
            dynamicOffset = rwOff; dynamicVaddr = rwVaddrBase + (rwOff - gotOffset)

            val dynEntries = mutableListOf<Pair<Long, Long>>()
            for (lib in sharedLibs) dynEntries.add(ElfDynamicTag.NEEDED.code to dynstrNameOffsets[lib]!!.toLong())
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
            dynEntries.add(ElfDynamicTag.NULL.code to 0L)
            dynamicEntries = dynEntries
            dynamicSize = dynEntries.size * 16
            rwOff = dynamicOffset + dynamicSize

            val dataBytes = merger.data.toByteArray()
            if (dataBytes.isNotEmpty()) rwOff = align(rwOff, 8)
            dataOffset = rwOff; dataVaddr = rwVaddrBase + (rwOff - gotOffset)
            rwOff += dataBytes.size

            val tdataBytes = merger.tdata.toByteArray()
            tdataAlign = if (tdataBytes.isNotEmpty()) maxOf(merger.tdataAlign, 1) else 1
            if (tdataBytes.isNotEmpty()) rwOff = align(rwOff, maxOf(tdataAlign, 8).toLong())
            tdataOffset = rwOff; tdataVaddr = rwVaddrBase + (rwOff - gotOffset)
            tdataFileSize = tdataBytes.size.toLong()
            rwOff += tdataBytes.size
            rwSegmentFileSize = rwOff - gotOffset

            // Debug sections after loaded segments
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
                numSections = 1 + merger.debugSections.size + 1
                dbgOff = align(dbgOff, 8)
                shoff = dbgOff
            } else {
                shstrtabData = ByteArray(0)
                shstrtabOffset = 0
                shoff = 0
                numSections = 0
            }
        }

        val sectionKindVaddr: Map<SectionKind, Long>
            get() = mapOf(
                SectionKind.TEXT to textVaddr + startStubSize,
                SectionKind.RODATA to rodataVaddr,
                SectionKind.DATA to dataVaddr,
                SectionKind.TDATA to tdataVaddr,
                SectionKind.EH_FRAME to ehFrameVaddr,
                SectionKind.EH_FRAME_HDR to ehFrameHdrVaddr,
                SectionKind.GCC_EXCEPT_TABLE to gccExceptTableVaddr,
            )

        fun symbolVaddrs(): Map<String, Long> {
            val result = mutableMapOf<String, Long>()
            for ((name, resolved) in symbols.globalSymbols) {
                result[name] = (sectionKindVaddr[resolved.sectionKind] ?: 0L) + resolved.value
            }
            return result
        }

        fun entryVaddr(): Long {
            return if (symbols.needsStartStub) textVaddr else symbolVaddrs()[symbols.entrySymbol.name]!!
        }

        fun pltSymbolVaddrs(): Map<String, Long> {
            val result = mutableMapOf<String, Long>()
            for ((i, sym) in dynamicSymbols.withIndex()) {
                result[sym] = pltVaddr + PLT0_SIZE + i * PLT_ENTRY_SIZE
            }
            return result
        }

        private fun writeSymEntry(buf: ByteArrayOutputStream, name: Int, info: Int, other: Int,
                                  shndx: Int, value: Long, size: Long) {
            writeU32(buf, name); buf.write(info); buf.write(other)
            writeU16(buf, shndx); writeU64(buf, value); writeU64(buf, size)
        }

        private fun buildSysvHash(syms: List<String>, nameOffsets: Map<String, Int>): ByteArray {
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
        private val layout: SegmentLayout,
    ) {
        fun apply(): ByteArray {
            val symVaddrs = layout.symbolVaddrs()
            val pltVaddrs = layout.pltSymbolVaddrs()
            val textBytes = merger.text.toByteArray().copyOf()

            for ((objIdx, obj) in objects.withIndex()) {
                for (rel in obj.relocations) {
                    if (rel.section == null) continue
                    val relKind = merger.sectionKindMap[objIdx to rel.section] ?: continue
                    if (relKind != SectionKind.TEXT) continue

                    val placement = merger.textPlacements
                        .firstOrNull { it.objIdx == objIdx && it.sectionName == rel.section } ?: continue
                    val patchOffset = (placement.mergedOffset + rel.offset).toInt()
                    val patchVaddr = layout.textVaddr + layout.startStubSize + patchOffset

                    // Skip PLT32 to __tls_get_addr — eliminated by GD→LE relaxation
                    if (rel.symbol == "__tls_get_addr" && rel.type == RelocationType.X86_64.PLT32) {
                        continue
                    }

                    val targetVaddr = symVaddrs[rel.symbol] ?: pltVaddrs[rel.symbol]
                        ?: throw IllegalStateException("Undefined symbol: ${rel.symbol}")

                    applyRelocation(textBytes, patchOffset, patchVaddr, targetVaddr, rel)
                }
            }
            return textBytes
        }

        private fun applyRelocation(text: ByteArray, offset: Int, patchVaddr: Long, targetVaddr: Long, rel: Relocation) {
            when (rel.type) {
                RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32 -> {
                    putI32(text, offset, (targetVaddr + rel.addend - patchVaddr).toInt())
                }
                RelocationType.X86_64.R_32, RelocationType.X86_64.R_32S -> {
                    putI32(text, offset, (targetVaddr + rel.addend).toInt())
                }
                RelocationType.AArch64.CALL26, RelocationType.AArch64.JUMP26 -> {
                    val disp = ((targetVaddr + rel.addend - patchVaddr) shr 2).toInt() and 0x03FFFFFF
                    val insn = readI32(text, offset)
                    putI32(text, offset, (insn and 0xFC000000.toInt()) or disp)
                }
                RelocationType.AArch64.ADR_PREL_PG_HI21 -> {
                    val target = targetVaddr + rel.addend
                    val page = ((target and -4096) - (patchVaddr and -4096)) shr 12
                    val immlo = (page.toInt() and 0x3) shl 29
                    val immhi = ((page.toInt() shr 2) and 0x7FFFF) shl 5
                    val insn = readI32(text, offset)
                    putI32(text, offset, (insn and 0x9F00001F.toInt()) or immlo or immhi)
                }
                RelocationType.AArch64.ADD_ABS_LO12_NC -> {
                    val target = targetVaddr + rel.addend
                    val imm12 = (target.toInt() and 0xFFF) shl 10
                    val insn = readI32(text, offset)
                    putI32(text, offset, (insn and 0xFFC003FF.toInt()) or imm12)
                }
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

                // x86-64 TLS LOCAL_EXEC: TP points past TLS block
                RelocationType.X86_64.TPOFF32 -> {
                    val tpOff = (targetVaddr + rel.addend - (layout.tdataVaddr + layout.tdataFileSize)).toInt()
                    putI32(text, offset, tpOff)
                }
                // x86-64 TLS GENERAL_DYNAMIC → relaxed to LOCAL_EXEC:
                // Rewrite 16-byte GD sequence to: mov rax,fs:[0]; lea rax,[rax+tpoff32]
                RelocationType.X86_64.TLSGD -> {
                    val tpOff = (targetVaddr + rel.addend - (layout.tdataVaddr + layout.tdataFileSize)).toInt()
                    val seqStart = offset - 4
                    // mov rax, fs:[0] — 9 bytes
                    text[seqStart + 0] = 0x64.toByte()
                    text[seqStart + 1] = 0x48.toByte()
                    text[seqStart + 2] = 0x8B.toByte()
                    text[seqStart + 3] = 0x04.toByte()
                    text[seqStart + 4] = 0x25.toByte()
                    text[seqStart + 5] = 0x00
                    text[seqStart + 6] = 0x00
                    text[seqStart + 7] = 0x00
                    text[seqStart + 8] = 0x00
                    // lea rax, [rax+tpoff32] — 7 bytes
                    text[seqStart + 9] = 0x48.toByte()
                    text[seqStart + 10] = 0x8D.toByte()
                    text[seqStart + 11] = 0x80.toByte()
                    putI32(text, seqStart + 12, tpOff)
                }
                // x86-64 TLS INITIAL_EXEC → relaxed to LOCAL_EXEC:
                // Rewrite: add reg, [rip+disp32] → lea reg, [reg+disp32]
                RelocationType.X86_64.GOTTPOFF -> {
                    val tpOff = (targetVaddr + rel.addend - (layout.tdataVaddr + layout.tdataFileSize)).toInt()
                    text[offset - 2] = 0x8D.toByte()
                    val modRM = text[offset - 1].toInt() and 0xFF
                    val reg = (modRM shr 3) and 7
                    text[offset - 1] = (0x80 or (reg shl 3) or reg).toByte()
                    putI32(text, offset, tpOff)
                }

                // ARM64 TLS LOCAL_EXEC
                RelocationType.AArch64.TLSLE_ADD_TPREL_HI12 -> {
                    val tpOff = targetVaddr + rel.addend - layout.tdataVaddr
                    val imm12 = ((tpOff shr 12) and 0xFFF).toInt() shl 10
                    val insn = readI32(text, offset)
                    putI32(text, offset, (insn and 0xFFC003FF.toInt()) or imm12)
                }
                RelocationType.AArch64.TLSLE_ADD_TPREL_LO12,
                RelocationType.AArch64.TLSLE_ADD_TPREL_LO12_NC -> {
                    val tpOff = targetVaddr + rel.addend - layout.tdataVaddr
                    val imm12 = (tpOff.toInt() and 0xFFF) shl 10
                    val insn = readI32(text, offset)
                    putI32(text, offset, (insn and 0xFFC003FF.toInt()) or imm12)
                }

                // RISC-V TLS LOCAL_EXEC
                RelocationType.RiscV.TPREL_HI20 -> {
                    val tpOff = targetVaddr + rel.addend - layout.tdataVaddr
                    val hi = ((tpOff + 0x800) shr 12).toInt()
                    val existing = readI32(text, offset)
                    putI32(text, offset, (existing and 0xFFF) or (hi shl 12))
                }
                RelocationType.RiscV.TPREL_LO12_I -> {
                    val tpOff = (targetVaddr + rel.addend - layout.tdataVaddr).toInt() and 0xFFF
                    val existing = readI32(text, offset)
                    putI32(text, offset, (existing and 0x000FFFFF) or (tpOff shl 20))
                }
                RelocationType.RiscV.TPREL_LO12_S -> {
                    val tpOff = (targetVaddr + rel.addend - layout.tdataVaddr).toInt() and 0xFFF
                    val imm11_5 = (tpOff shr 5) and 0x7F
                    val imm4_0 = tpOff and 0x1F
                    val existing = readI32(text, offset)
                    val mask = (0x7F shl 25) or (0x1F shl 7)
                    putI32(text, offset, (existing and mask.inv()) or (imm11_5 shl 25) or (imm4_0 shl 7))
                }
                RelocationType.RiscV.TPREL_ADD -> { /* no-op */ }

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
    }

    private class ElfEmitter(
        private val layout: SegmentLayout,
        private val relocatedText: ByteArray,
        private val merger: SectionMerger,
        private val symbols: SymbolResolver,
        private val machine: Int,
    ) {
        fun emit(): ByteArray {
            val buf = ByteArrayOutputStream()
            emitElfHeader(buf)
            emitProgramHeaders(buf)
            emitRxSegmentData(buf)
            emitRwSegmentData(buf)
            if (layout.hasDebugSections) {
                emitDebugSections(buf)
                emitSectionHeaders(buf)
            }
            return buf.toByteArray()
        }

        private fun emitElfHeader(buf: ByteArrayOutputStream) {
            val entryVaddr = layout.entryVaddr()
            buf.write(Elf.MAGIC)
            buf.write(ElfClass.ELF64.code)
            buf.write(ElfData.LSB.code)
            buf.write(Elf.VERSION)
            buf.write(0)
            buf.write(ByteArray(8))
            writeU16(buf, ElfObjectType.EXEC.code)
            writeU16(buf, machine)
            writeU32(buf, Elf.VERSION)
            writeU64(buf, entryVaddr)
            writeU64(buf, Elf.EHDR64_SIZE.toLong())
            writeU64(buf, layout.shoff)
            writeU32(buf, 0)
            writeU16(buf, Elf.EHDR64_SIZE)
            writeU16(buf, Elf.PHDR64_SIZE)
            writeU16(buf, layout.numPhdrs)
            writeU16(buf, if (layout.hasDebugSections) Elf.SHDR64_SIZE else 0)
            writeU16(buf, layout.numSections)
            writeU16(buf, if (layout.hasDebugSections) layout.numSections - 1 else 0)
        }

        private fun emitProgramHeaders(buf: ByteArrayOutputStream) {
            writePhdr(buf, ElfSegmentType.PHDR.code, ElfSegmentFlags.R,
                Elf.EHDR64_SIZE.toLong(), BASE_ADDR + Elf.EHDR64_SIZE, BASE_ADDR + Elf.EHDR64_SIZE,
                (layout.numPhdrs * Elf.PHDR64_SIZE).toLong(), (layout.numPhdrs * Elf.PHDR64_SIZE).toLong(), 8)

            writePhdr(buf, ElfSegmentType.INTERP.code, ElfSegmentFlags.R,
                layout.interpOffset, layout.interpVaddr, layout.interpVaddr,
                layout.interpBytes.size.toLong(), layout.interpBytes.size.toLong(), 1)

            writePhdr(buf, ElfSegmentType.LOAD.code, ElfSegmentFlags.R or ElfSegmentFlags.X,
                0, BASE_ADDR, BASE_ADDR, layout.rxSegmentEnd, layout.rxSegmentEnd, PAGE_SIZE)

            writePhdr(buf, ElfSegmentType.LOAD.code, ElfSegmentFlags.R or ElfSegmentFlags.W,
                layout.gotOffset, layout.rwVaddrBase, layout.rwVaddrBase,
                layout.rwSegmentFileSize, layout.rwSegmentFileSize, PAGE_SIZE)

            writePhdr(buf, ElfSegmentType.DYNAMIC.code, ElfSegmentFlags.R or ElfSegmentFlags.W,
                layout.dynamicOffset, layout.dynamicVaddr, layout.dynamicVaddr,
                layout.dynamicSize.toLong(), layout.dynamicSize.toLong(), 8)

            writePhdr(buf, ElfSegmentType.GNU_STACK.code, ElfSegmentFlags.R or ElfSegmentFlags.W,
                0, 0, 0, 0, 0, 16)

            if (layout.hasTdata) {
                writePhdr(buf, ElfSegmentType.TLS.code, ElfSegmentFlags.R,
                    layout.tdataOffset, layout.tdataVaddr, layout.tdataVaddr,
                    layout.tdataFileSize, layout.tdataFileSize, layout.tdataAlign.toLong())
            }
        }

        private fun emitRxSegmentData(buf: ByteArrayOutputStream) {
            padTo(buf, layout.interpOffset.toInt()); buf.write(layout.interpBytes)
            padTo(buf, layout.hashOffset.toInt()); buf.write(layout.hashBytes)
            padTo(buf, layout.dynsymOffset.toInt()); buf.write(layout.dynsymBytes)
            padTo(buf, layout.dynstrOffset.toInt()); buf.write(layout.dynstrBytes)
            padTo(buf, layout.relaPltOffset.toInt()); buf.write(buildRelaPlt())
            padTo(buf, layout.pltOffset.toInt()); buf.write(buildPlt())
            padTo(buf, layout.textOffset.toInt())
            if (symbols.needsStartStub) {
                buf.write(buildStartStub())
            }
            buf.write(relocatedText)
            val rodataBytes = merger.rodata.toByteArray()
            if (rodataBytes.isNotEmpty()) {
                padTo(buf, layout.rodataOffset.toInt()); buf.write(rodataBytes)
            }
            val ehFrameBytes = merger.ehFrame.toByteArray()
            if (ehFrameBytes.isNotEmpty()) {
                padTo(buf, layout.ehFrameOffset.toInt()); buf.write(ehFrameBytes)
            }
            val ehFrameHdrBytes = merger.ehFrameHdr.toByteArray()
            if (ehFrameHdrBytes.isNotEmpty()) {
                padTo(buf, layout.ehFrameHdrOffset.toInt()); buf.write(ehFrameHdrBytes)
            }
            val gccExceptTableBytes = merger.gccExceptTable.toByteArray()
            if (gccExceptTableBytes.isNotEmpty()) {
                padTo(buf, layout.gccExceptTableOffset.toInt()); buf.write(gccExceptTableBytes)
            }
        }

        private fun buildStartStub(): ByteArray {
            val stub = ByteArrayOutputStream()
            val stubVaddr = layout.textVaddr
            val mainVaddr = layout.symbolVaddrs()[symbols.entrySymbol.name]!!
            val exitVaddr = layout.pltSymbolVaddrs()["exit"]!!

            when (machine) {
                ElfMachine.AARCH64.code -> {
                    // _start (ARM64):
                    // At process entry: sp points to argc, argv starts at sp+8
                    //   ldr w0, [sp]       ; argc → x0 (first arg)
                    writeArm64Insn(stub, 0xB94003E0.toInt())
                    //   add x1, sp, #8     ; argv → x1 (second arg)
                    writeArm64Insn(stub, 0x910023E1.toInt())
                    //   bl main
                    val mainOff = ((mainVaddr - (stubVaddr + 8)) shr 2).toInt()
                    writeArm64Insn(stub, 0x94000000.toInt() or (mainOff and 0x03FFFFFF))
                    //   bl exit            ; w0 already has return value
                    val exitOff = ((exitVaddr - (stubVaddr + 12)) shr 2).toInt()
                    writeArm64Insn(stub, 0x94000000.toInt() or (exitOff and 0x03FFFFFF))
                    //   brk #0
                    writeArm64Insn(stub, 0xD4200000.toInt())
                }
                else -> {
                    // _start (x86-64):
                    // At process entry, the kernel puts on the stack:
                    //   (%rsp) = argc, 8(%rsp) = argv[0], 16(%rsp) = argv[1], ...
                    //   xor ebp, ebp               ; clear frame pointer
                    stub.write(byteArrayOf(0x31, 0xED.toByte()))
                    //   mov edi, [rsp]             ; argc → first arg (RDI)
                    stub.write(byteArrayOf(0x8B.toByte(), 0x3C, 0x24))
                    //   lea rsi, [rsp+8]           ; argv → second arg (RSI)
                    stub.write(byteArrayOf(0x48, 0x8D.toByte(), 0x74, 0x24, 0x08))
                    //   and rsp, -16               ; align stack to 16 bytes
                    stub.write(byteArrayOf(0x48, 0x83.toByte(), 0xE4.toByte(), 0xF0.toByte()))
                    //   call main
                    val callMainPC = stubVaddr + stub.size() + 5
                    stub.write(0xE8.toByte().toInt())
                    writeU32(stub, (mainVaddr - callMainPC).toInt())
                    //   mov edi, eax               ; exit code = main's return value
                    stub.write(byteArrayOf(0x89.toByte(), 0xC7.toByte()))
                    //   call exit@plt
                    val callExitPC = stubVaddr + stub.size() + 5
                    stub.write(0xE8.toByte().toInt())
                    writeU32(stub, (exitVaddr - callExitPC).toInt())
                    //   hlt                        ; should never reach here
                    stub.write(0xF4.toByte().toInt())
                }
            }

            // Pad to startStubSize
            val pad = layout.startStubSize - stub.size()
            if (pad > 0) stub.write(ByteArray(pad))
            return stub.toByteArray()
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

        private fun emitRwSegmentData(buf: ByteArrayOutputStream) {
            padTo(buf, layout.gotOffset.toInt()); buf.write(buildGot())
            padTo(buf, layout.dynamicOffset.toInt())
            for ((tag, value) in layout.dynamicEntries) {
                writeU64(buf, tag); writeU64(buf, value)
            }
            val dataBytes = merger.data.toByteArray()
            if (dataBytes.isNotEmpty()) {
                padTo(buf, layout.dataOffset.toInt()); buf.write(dataBytes)
            }
            val tdataBytes = merger.tdata.toByteArray()
            if (tdataBytes.isNotEmpty()) {
                padTo(buf, layout.tdataOffset.toInt()); buf.write(tdataBytes)
            }
        }

        private fun buildGot(): ByteArray {
            val buf = ByteArrayOutputStream()
            writeU64(buf, layout.dynamicVaddr)
            writeU64(buf, 0)
            writeU64(buf, 0)
            for (i in 0 until layout.numPltEntries) {
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
            for (i in layout.dynamicSymbols.indices) {
                val gotEntryVaddr = layout.gotVaddr + (3 + i) * GOT_ENTRY_SIZE
                writeU64(buf, gotEntryVaddr)
                writeU64(buf, ((i + 1).toLong() shl 32) or jumpSlotType)
                writeS64(buf, 0)
            }
            return buf.toByteArray()
        }

        private fun buildPlt(): ByteArray {
            if (layout.numPltEntries == 0) return ByteArray(0)
            return when (machine) {
                ElfMachine.AARCH64.code -> buildPltArm64()
                ElfMachine.RISCV.code -> buildPltRiscV()
                else -> buildPltX86()
            }
        }

        private fun buildPltX86(): ByteArray {
            val buf = ByteArrayOutputStream()
            val got1Rip = layout.gotVaddr + 8 - (layout.pltVaddr + 6)
            val got2Rip = layout.gotVaddr + 16 - (layout.pltVaddr + 12)
            buf.write(byteArrayOf(0xFF.toByte(), 0x35))
            writeU32(buf, got1Rip.toInt())
            buf.write(byteArrayOf(0xFF.toByte(), 0x25))
            writeU32(buf, got2Rip.toInt())
            buf.write(ByteArray(4))
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
            val got0Page = (layout.gotVaddr and -4096L) - (layout.pltVaddr and -4096L)
            val got0Lo = (layout.gotVaddr and 0xFFF).toInt()
            writeArm64Insn(buf, 0xA9BF7BF0.toInt()) // stp x16, x30, [sp, #-16]!
            writeArm64Insn(buf, adrp(16, got0Page))
            writeArm64Insn(buf, ldrImm64(17, 16, got0Lo + 16))
            writeArm64Insn(buf, 0xD61F0220.toInt()) // br x17
            for (i in 0 until layout.numPltEntries) {
                val entryVaddr = layout.pltVaddr + PLT0_SIZE + i.toLong() * PLT_ENTRY_SIZE
                val gotEntryVaddr = layout.gotVaddr + (3 + i).toLong() * GOT_ENTRY_SIZE
                val page = (gotEntryVaddr and -4096L) - (entryVaddr and -4096L)
                val lo = (gotEntryVaddr and 0xFFF).toInt()
                writeArm64Insn(buf, adrp(16, page))
                writeArm64Insn(buf, ldrImm64(17, 16, lo))
                writeArm64Insn(buf, 0xD61F0220.toInt()) // br x17
                writeArm64Insn(buf, 0xD503201F.toInt()) // nop
            }
            return buf.toByteArray()
        }

        private fun buildPltRiscV(): ByteArray {
            val buf = ByteArrayOutputStream()
            // PLT0: auipc t2, %pcrel_hi(.got.plt) / ld t3, %pcrel_lo(1b)(t2) / addi t1, t2, lo / jalr t3
            val got0Delta = layout.gotVaddr - layout.pltVaddr
            val got0Hi = ((got0Delta + 0x800) shr 12).toInt()
            val got0Lo = got0Delta.toInt() and 0xFFF
            writeRiscVInsn(buf, riscvAuipc(7, got0Hi))       // auipc t2(x7), hi
            writeRiscVInsn(buf, riscvLd(28, 7, got0Lo + 16)) // ld t3(x28), [t2+lo+16]
            writeRiscVInsn(buf, riscvAddi(6, 7, got0Lo))     // addi t1(x6), t2, lo
            writeRiscVInsn(buf, riscvJalr(0, 28, 0))         // jalr zero, t3
            for (i in 0 until layout.numPltEntries) {
                val entryVaddr = layout.pltVaddr + PLT0_SIZE + i.toLong() * PLT_ENTRY_SIZE
                val gotEntryVaddr = layout.gotVaddr + (3 + i).toLong() * GOT_ENTRY_SIZE
                val delta = gotEntryVaddr - entryVaddr
                val hi = ((delta + 0x800) shr 12).toInt()
                val lo = delta.toInt() and 0xFFF
                writeRiscVInsn(buf, riscvAuipc(28, hi))  // auipc t3, hi
                writeRiscVInsn(buf, riscvLd(28, 28, lo)) // ld t3, lo(t3)
                writeRiscVInsn(buf, riscvJalr(6, 28, 0)) // jalr t1, t3 (t1 holds return addr for lazy resolver)
                writeRiscVInsn(buf, 0x00000013)           // nop (addi x0, x0, 0)
            }
            return buf.toByteArray()
        }

        private fun riscvAuipc(rd: Int, imm20: Int): Int =
            0x17 or (rd shl 7) or (imm20 shl 12)

        private fun riscvLd(rd: Int, rs1: Int, imm12: Int): Int =
            0x03 or (0x3 shl 12) or (rd shl 7) or (rs1 shl 15) or ((imm12 and 0xFFF) shl 20)

        private fun riscvAddi(rd: Int, rs1: Int, imm12: Int): Int =
            0x13 or (rd shl 7) or (rs1 shl 15) or ((imm12 and 0xFFF) shl 20)

        private fun riscvJalr(rd: Int, rs1: Int, imm12: Int): Int =
            0x67 or (rd shl 7) or (rs1 shl 15) or ((imm12 and 0xFFF) shl 20)

        private fun writeRiscVInsn(buf: ByteArrayOutputStream, insn: Int) {
            buf.write(insn and 0xFF)
            buf.write((insn shr 8) and 0xFF)
            buf.write((insn shr 16) and 0xFF)
            buf.write((insn shr 24) and 0xFF)
        }

        private fun adrp(rd: Int, pageOffset: Long): Int {
            val immHi = ((pageOffset shr 12) and 0x7FFFF).toInt()
            val immLo = ((pageOffset shr 12) shr 19 and 0x3).toInt()
            return 0x90000000.toInt() or rd or (immHi shl 5) or (immLo shl 29)
        }

        private fun ldrImm64(rt: Int, rn: Int, offset: Int): Int =
            0xF9400000.toInt() or rt or (rn shl 5) or ((offset / 8) shl 10)

        private fun writeArm64Insn(buf: ByteArrayOutputStream, insn: Int) {
            buf.write(insn and 0xFF)
            buf.write((insn shr 8) and 0xFF)
            buf.write((insn shr 16) and 0xFF)
            buf.write((insn shr 24) and 0xFF)
        }

        private fun writePhdr(buf: ByteArrayOutputStream, type: Int, flags: Int,
                              offset: Long, vaddr: Long, paddr: Long,
                              filesz: Long, memsz: Long, align: Long) {
            writeU32(buf, type); writeU32(buf, flags)
            writeU64(buf, offset); writeU64(buf, vaddr); writeU64(buf, paddr)
            writeU64(buf, filesz); writeU64(buf, memsz); writeU64(buf, align)
        }
    }
}
