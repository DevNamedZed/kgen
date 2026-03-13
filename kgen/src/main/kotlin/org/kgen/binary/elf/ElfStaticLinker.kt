package org.kgen.binary.elf

import org.kgen.binary.*
import org.kgen.binary.BinaryWriter.align
import org.kgen.binary.BinaryWriter.padTo
import org.kgen.binary.BinaryWriter.writeU16
import org.kgen.binary.BinaryWriter.writeU32
import org.kgen.binary.BinaryWriter.writeU64
import java.io.ByteArrayOutputStream

/**
 * Links one or more relocatable [ObjectFile]s into a fully static ELF64 executable.
 *
 * Produces a minimal ELF binary with no dynamic linking infrastructure --
 * no `.interp`, `.dynamic`, PLT, GOT, `.dynsym`, or `.dynstr`. All symbols must
 * be resolved at link time. The linker merges `.text`, `.rodata`, `.data`, and `.tdata`
 * sections from all inputs, resolves symbols (GLOBAL overrides WEAK), applies relocations,
 * and emits a ready-to-run executable.
 *
 * Supports relocations for x86-64, ARM64, and RISC-V, including TLS relocations
 * (LOCAL_EXEC, INITIAL_EXEC, GENERAL_DYNAMIC relaxed to LOCAL_EXEC for static linking).
 * Debug sections (DWARF) are passed through to the output.
 *
 * The entry point is resolved from `_start` or `main` symbols.
 *
 * ```java
 * var linker = new ElfStaticLinker(ElfMachine.X86_64.getCode());
 * byte[] exe = linker.link(List.of(objectFile));
 * Files.write(Path.of("a.out"), exe);
 * ```
 *
 * See `spec/roadmap.md` for the full list of supported relocation types.
 */
class ElfStaticLinker(
    private val machine: Int = ElfMachine.X86_64.code,
    private val baseAddr: Long = 0x400000L,
) {
    private companion object {
        const val PAGE_SIZE = 0x1000L
    }

    /**
     * Link the given relocatable object files into a static ELF64 executable.
     *
     * @param objects one or more relocatable [ObjectFile]s to link
     * @return the complete ELF executable as a byte array
     * @throws IllegalStateException if there are undefined symbols after resolution
     */
    fun link(objects: List<ObjectFile>): ByteArray {
        require(objects.isNotEmpty()) { "No object files to link" }
        val merger = SectionMerger(objects)
        val symbols = SymbolResolver(objects, merger).resolve()
        val layout = StaticLayout(merger, symbols, baseAddr)
        val relocated = RelocationApplier(objects, merger, symbols, layout).apply()
        return StaticEmitter(layout, relocated, merger, symbols, machine).emit()
    }

    private data class SectionPlacement(
        val objIdx: Int,
        val sectionName: String,
        val mergedOffset: Int,
        val size: Int,
    )

    private data class ResolvedSymbol(
        val name: String,
        val value: Long,
        val sectionKind: SectionKind,
        val objIdx: Int,
    )

    private data class DebugSection(val name: String, val data: ByteArray)

    private class SectionMerger(private val objects: List<ObjectFile>) {
        val text = ByteArrayOutputStream()
        val data = ByteArrayOutputStream()
        val rodata = ByteArrayOutputStream()
        val tdata = ByteArrayOutputStream()
        var tdataAlign = 1

        val textPlacements = mutableListOf<SectionPlacement>()
        val dataPlacements = mutableListOf<SectionPlacement>()
        val rodataPlacements = mutableListOf<SectionPlacement>()
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
                            .firstOrNull { it.objIdx == objIdx && it.sectionName == sym.section }
                            ?: continue
                        val existing = symbolBindings[sym.name]
                        // GLOBAL always wins over WEAK; skip if existing is GLOBAL
                        if (existing == SymbolBinding.GLOBAL && sym.binding == SymbolBinding.WEAK) continue
                        globalSymbols[sym.name] = ResolvedSymbol(
                            sym.name, sym.value + placement.mergedOffset, kind, objIdx,
                        )
                        symbolBindings[sym.name] = sym.binding
                    }
                }
            }
            undefinedSymbols.removeAll(globalSymbols.keys)

            // __tls_get_addr is eliminated by GD→LE relaxation in static linking
            val hasTlsgd = objects.any { obj -> obj.relocations.any { it.type == RelocationType.X86_64.TLSGD } }
            if (hasTlsgd) {
                undefinedSymbols.remove("__tls_get_addr")
            }

            if (undefinedSymbols.isNotEmpty()) {
                throw IllegalStateException(
                    "Undefined symbols: ${undefinedSymbols.sorted().joinToString(", ")}",
                )
            }
            return this
        }

        val entrySymbol: ResolvedSymbol
            get() = globalSymbols["_start"] ?: globalSymbols["main"]
                ?: throw IllegalStateException("No _start or main symbol found")
    }

    private class StaticLayout(
        val merger: SectionMerger,
        val symbols: SymbolResolver,
        baseAddr: Long,
    ) {
        val hasTdata = merger.tdata.size() > 0
        val numPhdrs = 3 + (if (hasTdata) 1 else 0)
        val headerSize = Elf.EHDR64_SIZE + Elf.PHDR64_SIZE * numPhdrs
        val hasDebugSections = merger.debugSections.isNotEmpty()

        val textOffset: Long
        val textVaddr: Long
        val rodataOffset: Long
        val rodataVaddr: Long
        val rxSegmentEnd: Long

        val dataOffset: Long
        val dataVaddr: Long
        val rwVaddrBase: Long
        val rwSegmentFileSize: Long
        val hasRwSegment: Boolean

        val tdataOffset: Long
        val tdataVaddr: Long
        val tdataFileSize: Long
        val tdataAlign: Int

        val debugOffsets = mutableListOf<Long>()
        val shstrtabOffset: Long
        val shstrtabData: ByteArray
        val shoff: Long
        val numSections: Int

        init {
            val textBytes = merger.text.toByteArray()
            val rodataBytes = merger.rodata.toByteArray()
            val dataBytes = merger.data.toByteArray()
            val tdataBytes = merger.tdata.toByteArray()

            var off = align(headerSize.toLong(), 16)
            textOffset = off; textVaddr = baseAddr + off; off += textBytes.size
            if (rodataBytes.isNotEmpty()) off = align(off, 16)
            rodataOffset = off; rodataVaddr = baseAddr + off; off += rodataBytes.size
            rxSegmentEnd = off

            val hasDataOrTdata = dataBytes.isNotEmpty() || tdataBytes.isNotEmpty()
            hasRwSegment = hasDataOrTdata
            if (hasRwSegment) {
                val rwFileOff = align(rxSegmentEnd, PAGE_SIZE)
                rwVaddrBase = align(baseAddr + rwFileOff, PAGE_SIZE)
                dataOffset = rwFileOff; dataVaddr = rwVaddrBase
                var rwOff = rwFileOff + dataBytes.size

                tdataAlign = if (tdataBytes.isNotEmpty()) merger.tdataAlign else 1
                if (tdataBytes.isNotEmpty()) rwOff = align(rwOff, maxOf(tdataAlign, 1).toLong())
                tdataOffset = rwOff; tdataVaddr = rwVaddrBase + (rwOff - rwFileOff)
                rwOff += tdataBytes.size
                tdataFileSize = tdataBytes.size.toLong()
                rwSegmentFileSize = rwOff - rwFileOff
                off = rwOff
            } else {
                dataOffset = 0; dataVaddr = 0; rwVaddrBase = 0; rwSegmentFileSize = 0
                tdataOffset = 0; tdataVaddr = 0; tdataFileSize = 0; tdataAlign = 1
            }

            if (hasDebugSections) {
                for (dbg in merger.debugSections) {
                    off = align(off, 1)
                    debugOffsets.add(off)
                    off += dbg.data.size
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
                shstrtabOffset = off
                off += shstrtabData.size

                numSections = 1 + merger.debugSections.size + 1
                off = align(off, 8)
                shoff = off
            } else {
                shstrtabData = ByteArray(0)
                shstrtabOffset = 0
                shoff = 0
                numSections = 0
            }
        }

        val sectionKindVaddr: Map<SectionKind, Long>
            get() = mapOf(
                SectionKind.TEXT to textVaddr,
                SectionKind.RODATA to rodataVaddr,
                SectionKind.DATA to dataVaddr,
                SectionKind.TDATA to tdataVaddr,
            )

        fun symbolVaddrs(): Map<String, Long> {
            val result = mutableMapOf<String, Long>()
            for ((name, resolved) in symbols.globalSymbols) {
                result[name] = (sectionKindVaddr[resolved.sectionKind] ?: 0L) + resolved.value
            }
            return result
        }

        fun entryVaddr(): Long = symbolVaddrs()[symbols.entrySymbol.name]!!
    }

    private class RelocationApplier(
        private val objects: List<ObjectFile>,
        private val merger: SectionMerger,
        private val symbols: SymbolResolver,
        private val layout: StaticLayout,
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
                        SectionKind.TEXT -> Triple(textBytes, merger.textPlacements, layout.textVaddr)
                        SectionKind.DATA -> Triple(dataBytes, merger.dataPlacements, layout.dataVaddr)
                        else -> continue
                    }

                    val placement = placements
                        .firstOrNull { it.objIdx == objIdx && it.sectionName == rel.section }
                        ?: continue
                    val patchOffset = (placement.mergedOffset + rel.offset).toInt()
                    val patchVaddr = sectionVaddr + patchOffset

                    // Skip PLT32 to __tls_get_addr — eliminated by GD→LE relaxation
                    if (rel.symbol == "__tls_get_addr" && rel.type == RelocationType.X86_64.PLT32) {
                        continue
                    }

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
                RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32 -> {
                    putI32(bytes, offset, (targetVaddr + rel.addend - patchVaddr).toInt())
                }
                RelocationType.X86_64.R_32, RelocationType.X86_64.R_32S -> {
                    putI32(bytes, offset, (targetVaddr + rel.addend).toInt())
                }
                RelocationType.X86_64.R_64 -> {
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
                RelocationType.AArch64.LDST64_ABS_LO12_NC -> {
                    val target = targetVaddr + rel.addend
                    val imm12 = ((target.toInt() and 0xFFF) shr 3) shl 10
                    val insn = readI32(bytes, offset)
                    putI32(bytes, offset, (insn and 0xFFC003FF.toInt()) or imm12)
                }
                RelocationType.AArch64.LDST32_ABS_LO12_NC -> {
                    val target = targetVaddr + rel.addend
                    val imm12 = ((target.toInt() and 0xFFF) shr 2) shl 10
                    val insn = readI32(bytes, offset)
                    putI32(bytes, offset, (insn and 0xFFC003FF.toInt()) or imm12)
                }

                // RISC-V relocations
                RelocationType.RiscV.R_64 -> {
                    putI64(bytes, offset, targetVaddr + rel.addend)
                }
                RelocationType.RiscV.R_32 -> {
                    putI32(bytes, offset, (targetVaddr + rel.addend).toInt())
                }
                RelocationType.RiscV.R_32_PCREL -> {
                    putI32(bytes, offset, (targetVaddr + rel.addend - patchVaddr).toInt())
                }
                RelocationType.RiscV.BRANCH -> {
                    val delta = (targetVaddr + rel.addend - patchVaddr).toInt()
                    val existing = readI32(bytes, offset)
                    val imm12 = (delta shr 12) and 0x1
                    val imm10_5 = (delta shr 5) and 0x3F
                    val imm4_1 = (delta shr 1) and 0xF
                    val imm11 = (delta shr 11) and 0x1
                    val encoded = (imm12 shl 31) or (imm10_5 shl 25) or (imm4_1 shl 8) or (imm11 shl 7)
                    val mask = (0x1 shl 31) or (0x3F shl 25) or (0xF shl 8) or (0x1 shl 7)
                    putI32(bytes, offset, (existing and mask.inv()) or encoded)
                }
                RelocationType.RiscV.JAL -> {
                    val delta = (targetVaddr + rel.addend - patchVaddr).toInt()
                    val existing = readI32(bytes, offset)
                    val imm20 = (delta shr 20) and 0x1
                    val imm10_1 = (delta shr 1) and 0x3FF
                    val imm11 = (delta shr 11) and 0x1
                    val imm19_12 = (delta shr 12) and 0xFF
                    val encoded = (imm20 shl 31) or (imm10_1 shl 21) or (imm11 shl 20) or (imm19_12 shl 12)
                    putI32(bytes, offset, (existing and 0xFFF) or encoded)
                }
                RelocationType.RiscV.CALL, RelocationType.RiscV.CALL_PLT -> {
                    val delta = targetVaddr + rel.addend - patchVaddr
                    val hi = ((delta + 0x800) shr 12).toInt()
                    val lo = (delta.toInt()) and 0xFFF
                    val auipc = readI32(bytes, offset)
                    putI32(bytes, offset, (auipc and 0xFFF) or (hi shl 12))
                    val jalr = readI32(bytes, offset + 4)
                    putI32(bytes, offset + 4, (jalr and 0x000FFFFF) or (lo shl 20))
                }
                RelocationType.RiscV.PCREL_HI20 -> {
                    val delta = targetVaddr + rel.addend - patchVaddr
                    val hi = ((delta + 0x800) shr 12).toInt()
                    val existing = readI32(bytes, offset)
                    putI32(bytes, offset, (existing and 0xFFF) or (hi shl 12))
                }
                RelocationType.RiscV.HI20 -> {
                    val value = targetVaddr + rel.addend
                    val hi = ((value + 0x800) shr 12).toInt()
                    val existing = readI32(bytes, offset)
                    putI32(bytes, offset, (existing and 0xFFF) or (hi shl 12))
                }
                RelocationType.RiscV.LO12_I -> {
                    val value = (targetVaddr + rel.addend).toInt() and 0xFFF
                    val existing = readI32(bytes, offset)
                    putI32(bytes, offset, (existing and 0x000FFFFF) or (value shl 20))
                }
                RelocationType.RiscV.LO12_S -> {
                    val value = (targetVaddr + rel.addend).toInt() and 0xFFF
                    val imm11_5 = (value shr 5) and 0x7F
                    val imm4_0 = value and 0x1F
                    val existing = readI32(bytes, offset)
                    val mask = (0x7F shl 25) or (0x1F shl 7)
                    putI32(bytes, offset, (existing and mask.inv()) or (imm11_5 shl 25) or (imm4_0 shl 7))
                }
                RelocationType.RiscV.PCREL_LO12_I -> {
                    val value = (targetVaddr + rel.addend - patchVaddr).toInt() and 0xFFF
                    val existing = readI32(bytes, offset)
                    putI32(bytes, offset, (existing and 0x000FFFFF) or (value shl 20))
                }
                RelocationType.RiscV.PCREL_LO12_S -> {
                    val value = (targetVaddr + rel.addend - patchVaddr).toInt() and 0xFFF
                    val imm11_5 = (value shr 5) and 0x7F
                    val imm4_0 = value and 0x1F
                    val existing = readI32(bytes, offset)
                    val mask = (0x7F shl 25) or (0x1F shl 7)
                    putI32(bytes, offset, (existing and mask.inv()) or (imm11_5 shl 25) or (imm4_0 shl 7))
                }
                RelocationType.RiscV.RELAX -> {
                    // Linker relaxation marker — no patching needed
                }

                // x86-64 TLS LOCAL_EXEC: TP points past TLS block, so offset is negative
                RelocationType.X86_64.TPOFF32 -> {
                    val tpOff = (targetVaddr + rel.addend - (layout.tdataVaddr + layout.tdataFileSize)).toInt()
                    putI32(bytes, offset, tpOff)
                }
                // x86-64 TLS GENERAL_DYNAMIC → relaxed to LOCAL_EXEC in static link:
                // Rewrite 16-byte GD sequence to: mov rax,fs:[0]; lea rax,[rax+tpoff32]
                RelocationType.X86_64.TLSGD -> {
                    val tpOff = (targetVaddr + rel.addend - (layout.tdataVaddr + layout.tdataFileSize)).toInt()
                    // The TLSGD relocation points at the disp32 of the LEA.
                    // The GD sequence starts 4 bytes before that (66 48 8d 3d).
                    val seqStart = offset - 4
                    // Write: 64 48 8b 04 25 00 00 00 00  (mov rax, fs:[0]) — 9 bytes
                    bytes[seqStart + 0] = 0x64.toByte()
                    bytes[seqStart + 1] = 0x48.toByte()
                    bytes[seqStart + 2] = 0x8B.toByte()
                    bytes[seqStart + 3] = 0x04.toByte()
                    bytes[seqStart + 4] = 0x25.toByte()
                    bytes[seqStart + 5] = 0x00
                    bytes[seqStart + 6] = 0x00
                    bytes[seqStart + 7] = 0x00
                    bytes[seqStart + 8] = 0x00
                    // Write: 48 8d 80 XX XX XX XX  (lea rax, [rax+tpoff32]) — 7 bytes
                    bytes[seqStart + 9] = 0x48.toByte()
                    bytes[seqStart + 10] = 0x8D.toByte()
                    bytes[seqStart + 11] = 0x80.toByte()
                    putI32(bytes, seqStart + 12, tpOff)
                }
                // x86-64 TLS INITIAL_EXEC → relaxed to LOCAL_EXEC in static link:
                // Rewrite: add reg, [rip+disp32] → lea reg, [reg+disp32] with TPOFF32
                RelocationType.X86_64.GOTTPOFF -> {
                    val tpOff = (targetVaddr + rel.addend - (layout.tdataVaddr + layout.tdataFileSize)).toInt()
                    // Rewrite opcode: 0x03 (ADD) → 0x8D (LEA)
                    bytes[offset - 2] = 0x8D.toByte()
                    // Rewrite ModRM: [rip+disp32] (0x05|reg<<3) → [reg+disp32] (0x80|reg<<3|reg)
                    val modRM = bytes[offset - 1].toInt() and 0xFF
                    val reg = (modRM shr 3) and 7
                    bytes[offset - 1] = (0x80 or (reg shl 3) or reg).toByte()
                    putI32(bytes, offset, tpOff)
                }

                // ARM64 TLS LOCAL_EXEC: TP-relative offset (TP points to start of TLS)
                RelocationType.AArch64.TLSLE_ADD_TPREL_HI12 -> {
                    val tpOff = targetVaddr + rel.addend - layout.tdataVaddr
                    val imm12 = ((tpOff shr 12) and 0xFFF).toInt() shl 10
                    val insn = readI32(bytes, offset)
                    putI32(bytes, offset, (insn and 0xFFC003FF.toInt()) or imm12)
                }
                RelocationType.AArch64.TLSLE_ADD_TPREL_LO12,
                RelocationType.AArch64.TLSLE_ADD_TPREL_LO12_NC -> {
                    val tpOff = targetVaddr + rel.addend - layout.tdataVaddr
                    val imm12 = (tpOff.toInt() and 0xFFF) shl 10
                    val insn = readI32(bytes, offset)
                    putI32(bytes, offset, (insn and 0xFFC003FF.toInt()) or imm12)
                }

                // RISC-V TLS LOCAL_EXEC: TP-relative offset
                RelocationType.RiscV.TPREL_HI20 -> {
                    val tpOff = targetVaddr + rel.addend - layout.tdataVaddr
                    val hi = ((tpOff + 0x800) shr 12).toInt()
                    val existing = readI32(bytes, offset)
                    putI32(bytes, offset, (existing and 0xFFF) or (hi shl 12))
                }
                RelocationType.RiscV.TPREL_LO12_I -> {
                    val tpOff = (targetVaddr + rel.addend - layout.tdataVaddr).toInt() and 0xFFF
                    val existing = readI32(bytes, offset)
                    putI32(bytes, offset, (existing and 0x000FFFFF) or (tpOff shl 20))
                }
                RelocationType.RiscV.TPREL_LO12_S -> {
                    val tpOff = (targetVaddr + rel.addend - layout.tdataVaddr).toInt() and 0xFFF
                    val imm11_5 = (tpOff shr 5) and 0x7F
                    val imm4_0 = tpOff and 0x1F
                    val existing = readI32(bytes, offset)
                    val mask = (0x7F shl 25) or (0x1F shl 7)
                    putI32(bytes, offset, (existing and mask.inv()) or (imm11_5 shl 25) or (imm4_0 shl 7))
                }
                RelocationType.RiscV.TPREL_ADD -> {
                    // Linker relaxation marker — no patching needed
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

    private class StaticEmitter(
        private val layout: StaticLayout,
        private val relocated: RelocatedSections,
        private val merger: SectionMerger,
        private val symbols: SymbolResolver,
        private val machine: Int,
    ) {
        fun emit(): ByteArray {
            val buf = ByteArrayOutputStream()
            emitElfHeader(buf)
            emitProgramHeaders(buf)
            emitRxSegment(buf)
            if (layout.hasRwSegment) emitRwSegment(buf)
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
            writeU16(buf, ElfObjectType.EXEC.code)
            writeU16(buf, machine)
            writeU32(buf, Elf.VERSION)
            writeU64(buf, layout.entryVaddr())
            writeU64(buf, Elf.EHDR64_SIZE.toLong()) // phoff
            writeU64(buf, layout.shoff) // shoff
            writeU32(buf, 0) // flags
            writeU16(buf, Elf.EHDR64_SIZE)
            writeU16(buf, Elf.PHDR64_SIZE)
            writeU16(buf, layout.numPhdrs)
            writeU16(buf, if (layout.hasDebugSections) Elf.SHDR64_SIZE else 0)
            writeU16(buf, layout.numSections)
            writeU16(buf, if (layout.hasDebugSections) layout.numSections - 1 else 0) // shstrndx
        }

        private fun emitProgramHeaders(buf: ByteArrayOutputStream) {
            writePhdr(buf, ElfSegmentType.LOAD.code, ElfSegmentFlags.R or ElfSegmentFlags.X,
                0, layout.textVaddr - layout.textOffset, layout.textVaddr - layout.textOffset,
                layout.rxSegmentEnd, layout.rxSegmentEnd, PAGE_SIZE)

            if (layout.hasRwSegment) {
                writePhdr(buf, ElfSegmentType.LOAD.code, ElfSegmentFlags.R or ElfSegmentFlags.W,
                    layout.dataOffset, layout.dataVaddr, layout.dataVaddr,
                    layout.rwSegmentFileSize, layout.rwSegmentFileSize, PAGE_SIZE)
            } else {
                writePhdr(buf, ElfSegmentType.LOAD.code, ElfSegmentFlags.R or ElfSegmentFlags.W,
                    0, 0, 0, 0, 0, PAGE_SIZE)
            }

            writePhdr(buf, ElfSegmentType.GNU_STACK.code, ElfSegmentFlags.R or ElfSegmentFlags.W,
                0, 0, 0, 0, 0, 16)

            if (layout.hasTdata) {
                writePhdr(buf, ElfSegmentType.TLS.code, ElfSegmentFlags.R,
                    layout.tdataOffset, layout.tdataVaddr, layout.tdataVaddr,
                    layout.tdataFileSize, layout.tdataFileSize, layout.tdataAlign.toLong())
            }
        }

        private fun emitRxSegment(buf: ByteArrayOutputStream) {
            padTo(buf, layout.textOffset.toInt())
            buf.write(relocated.text)
            val rodataBytes = merger.rodata.toByteArray()
            if (rodataBytes.isNotEmpty()) {
                padTo(buf, layout.rodataOffset.toInt())
                buf.write(rodataBytes)
            }
        }

        private fun emitRwSegment(buf: ByteArrayOutputStream) {
            padTo(buf, layout.dataOffset.toInt())
            buf.write(relocated.data)
            val tdataBytes = merger.tdata.toByteArray()
            if (tdataBytes.isNotEmpty()) {
                padTo(buf, layout.tdataOffset.toInt())
                buf.write(tdataBytes)
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
            // SHT_NULL
            writeSectionHeader(buf, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

            // Debug sections
            var nameOff = 1 // skip the leading null byte in .shstrtab
            for ((i, dbg) in merger.debugSections.withIndex()) {
                writeSectionHeader(buf,
                    name = nameOff,
                    type = ElfSectionType.PROGBITS.code,
                    flags = 0, addr = 0,
                    offset = layout.debugOffsets[i],
                    size = dbg.data.size.toLong(),
                    link = 0, info = 0, addralign = 1, entsize = 0)
                nameOff += dbg.name.length + 1
            }

            // .shstrtab
            writeSectionHeader(buf,
                name = nameOff,
                type = ElfSectionType.STRTAB.code,
                flags = 0, addr = 0,
                offset = layout.shstrtabOffset,
                size = layout.shstrtabData.size.toLong(),
                link = 0, info = 0, addralign = 1, entsize = 0)
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
