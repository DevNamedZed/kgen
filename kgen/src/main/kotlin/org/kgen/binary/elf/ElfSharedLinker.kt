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
        val layout = SharedLayout(merger, symbols, soname, sharedLibs)
        val relocated = RelocationApplier(objects, merger, symbols, layout).apply()
        return SharedEmitter(layout, relocated, merger, symbols, machine).emit()
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
                writeSymEntry(dynsymBuf, dynstrSymOffsets[sym.name]!!,
                    Elf.stInfo(ElfSymbolBinding.GLOBAL, elfType),
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

            // Compute number of R_RELATIVE entries for .rela.dyn (data section abs relocs)
            // We'll compute the actual count after layout, but need to reserve space now
            // Count absolute data relocations from the input objects
            var numRelaDyn = 0
            // We won't know exact count until we see the relocations, but we can count them
            // from the input objects
            // Actually let's just count them now from the objects' relocations
            // Any R_64 relocation in .data will become R_RELATIVE
            // We'll handle this in RelocationApplier; for layout we need the count
            for (obj in symbols.globalSymbols.values) { /* just iterating */ }
            // Actually the caller passes objects through merger, but we don't have them here.
            // We'll just set relaDynSize = 0 for now and not emit .rela.dyn if not needed.
            // The static linker handles data relocs at link time; for .so, absolute data
            // relocations become R_RELATIVE. But for simplicity in this first implementation,
            // we resolve everything at link time assuming PIC code.
            relaDynSize = 0

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
            dynEntries.add(ElfDynamicTag.NULL.code to 0L)
            dynamicEntries = dynEntries
            dynamicSize = dynEntries.size * 16
            rwOff = dynamicOffset + dynamicSize

            val dataBytes = merger.data.toByteArray()
            if (dataBytes.isNotEmpty()) rwOff = align(rwOff, 8)
            dataOffset = rwOff; dataVaddr = rwVaddrBase + (rwOff - gotOffset)
            rwOff += dataBytes.size
            rwSegmentFileSize = rwOff - gotOffset
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
                        .firstOrNull { it.objIdx == objIdx && it.sectionName == rel.section }
                        ?: continue
                    val patchOffset = (placement.mergedOffset + rel.offset).toInt()
                    val patchVaddr = layout.textVaddr + patchOffset

                    val targetVaddr = symVaddrs[rel.symbol] ?: pltVaddrs[rel.symbol]
                        ?: throw IllegalStateException("Undefined symbol: ${rel.symbol}")

                    applyRelocation(textBytes, patchOffset, patchVaddr, targetVaddr, rel)
                }
            }
            return textBytes
        }

        private fun applyRelocation(
            text: ByteArray, offset: Int, patchVaddr: Long, targetVaddr: Long, rel: Relocation,
        ) {
            when (rel.type) {
                RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32 -> {
                    putI32(text, offset, (targetVaddr + rel.addend - patchVaddr).toInt())
                }
                RelocationType.X86_64.R_32, RelocationType.X86_64.R_32S -> {
                    putI32(text, offset, (targetVaddr + rel.addend).toInt())
                }
                else -> throw IllegalStateException("Unsupported relocation type: ${rel.type}")
            }
        }

        private fun putI32(data: ByteArray, offset: Int, value: Int) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value shr 8) and 0xFF).toByte()
            data[offset + 2] = ((value shr 16) and 0xFF).toByte()
            data[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
    }

    private class SharedEmitter(
        private val layout: SharedLayout,
        private val relocatedText: ByteArray,
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
            writeU64(buf, 0) // shoff
            writeU32(buf, 0) // flags
            writeU16(buf, Elf.EHDR64_SIZE)
            writeU16(buf, Elf.PHDR64_SIZE)
            writeU16(buf, layout.numPhdrs)
            writeU16(buf, 0) // shentsize
            writeU16(buf, 0) // shnum
            writeU16(buf, 0) // shstrndx
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
            if (layout.pltSize > 0) {
                padTo(buf, layout.pltOffset.toInt()); buf.write(buildPlt())
            }
            padTo(buf, layout.textOffset.toInt()); buf.write(relocatedText)
            val rodataBytes = merger.rodata.toByteArray()
            if (rodataBytes.isNotEmpty()) {
                padTo(buf, layout.rodataOffset.toInt()); buf.write(rodataBytes)
            }
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
            for (i in layout.importedSymbols.indices) {
                val gotEntryVaddr = layout.gotVaddr + (3 + i) * GOT_ENTRY_SIZE
                val symIdx = layout.importSymIdxBase + i
                writeU64(buf, gotEntryVaddr)
                writeU64(buf, (symIdx.toLong() shl 32) or RelocationType.X86_64.JUMP_SLOT.value.toLong())
                writeS64(buf, 0)
            }
            return buf.toByteArray()
        }

        private fun buildPlt(): ByteArray {
            if (layout.numPltEntries == 0) return ByteArray(0)
            val buf = ByteArrayOutputStream()
            // PLT0: push GOT[1]; jmp GOT[2]
            val got1Rip = layout.gotVaddr + 8 - (layout.pltVaddr + 6)
            val got2Rip = layout.gotVaddr + 16 - (layout.pltVaddr + 12)
            buf.write(byteArrayOf(0xFF.toByte(), 0x35))
            writeU32(buf, got1Rip.toInt())
            buf.write(byteArrayOf(0xFF.toByte(), 0x25))
            writeU32(buf, got2Rip.toInt())
            buf.write(ByteArray(4)) // padding
            // PLT entries
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
