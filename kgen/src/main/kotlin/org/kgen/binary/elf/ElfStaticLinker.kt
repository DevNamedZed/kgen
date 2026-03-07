package org.kgen.binary.elf

import org.kgen.binary.*
import org.kgen.binary.BinaryWriter.align
import org.kgen.binary.BinaryWriter.padTo
import org.kgen.binary.BinaryWriter.writeU16
import org.kgen.binary.BinaryWriter.writeU32
import org.kgen.binary.BinaryWriter.writeU64
import java.io.ByteArrayOutputStream

/**
 * Links relocatable ELF64 ObjectFiles into a fully static executable.
 *
 * Produces a minimal ELF binary with no dynamic linking infrastructure —
 * no .interp, .dynamic, PLT, GOT, .dynsym, or .dynstr. All symbols must
 * be resolved at link time.
 */
class ElfStaticLinker(
    private val machine: Int = ElfMachine.X86_64.code,
    private val baseAddr: Long = 0x400000L,
) {
    private companion object {
        const val PAGE_SIZE = 0x1000L
    }

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
            get() = globalSymbols["_start"] ?: globalSymbols["main"]
                ?: throw IllegalStateException("No _start or main symbol found")
    }

    private class StaticLayout(
        val merger: SectionMerger,
        val symbols: SymbolResolver,
        baseAddr: Long,
    ) {
        val numPhdrs = 3 // PHDR + LOAD(RX) + LOAD(RW), plus GNU_STACK if data exists
        val headerSize = Elf.EHDR64_SIZE + Elf.PHDR64_SIZE * numPhdrs

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

        init {
            val textBytes = merger.text.toByteArray()
            val rodataBytes = merger.rodata.toByteArray()
            val dataBytes = merger.data.toByteArray()

            // RX segment: ELF header + phdrs + .text + .rodata
            var off = align(headerSize.toLong(), 16)
            textOffset = off; textVaddr = baseAddr + off; off += textBytes.size
            if (rodataBytes.isNotEmpty()) off = align(off, 16)
            rodataOffset = off; rodataVaddr = baseAddr + off; off += rodataBytes.size
            rxSegmentEnd = off

            // RW segment: .data
            hasRwSegment = dataBytes.isNotEmpty()
            if (hasRwSegment) {
                val rwFileOff = align(rxSegmentEnd, PAGE_SIZE)
                rwVaddrBase = align(baseAddr + rwFileOff, PAGE_SIZE)
                dataOffset = rwFileOff; dataVaddr = rwVaddrBase
                rwSegmentFileSize = dataBytes.size.toLong()
            } else {
                dataOffset = 0; dataVaddr = 0; rwVaddrBase = 0; rwSegmentFileSize = 0
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
            writeU64(buf, 0) // shoff (no section headers)
            writeU32(buf, 0) // flags
            writeU16(buf, Elf.EHDR64_SIZE)
            writeU16(buf, Elf.PHDR64_SIZE)
            writeU16(buf, layout.numPhdrs)
            writeU16(buf, 0) // shentsize
            writeU16(buf, 0) // shnum
            writeU16(buf, 0) // shstrndx
        }

        private fun emitProgramHeaders(buf: ByteArrayOutputStream) {
            // LOAD RX: covers everything from file start through .text + .rodata
            writePhdr(buf, ElfSegmentType.LOAD.code, ElfSegmentFlags.R or ElfSegmentFlags.X,
                0, layout.textVaddr - layout.textOffset, layout.textVaddr - layout.textOffset,
                layout.rxSegmentEnd, layout.rxSegmentEnd, PAGE_SIZE)

            // LOAD RW: .data
            if (layout.hasRwSegment) {
                writePhdr(buf, ElfSegmentType.LOAD.code, ElfSegmentFlags.R or ElfSegmentFlags.W,
                    layout.dataOffset, layout.dataVaddr, layout.dataVaddr,
                    layout.rwSegmentFileSize, layout.rwSegmentFileSize, PAGE_SIZE)
            } else {
                // Empty placeholder so numPhdrs stays consistent
                writePhdr(buf, ElfSegmentType.LOAD.code, ElfSegmentFlags.R or ElfSegmentFlags.W,
                    0, 0, 0, 0, 0, PAGE_SIZE)
            }

            // GNU_STACK: no-exec stack
            writePhdr(buf, ElfSegmentType.GNU_STACK.code, ElfSegmentFlags.R or ElfSegmentFlags.W,
                0, 0, 0, 0, 0, 16)
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
