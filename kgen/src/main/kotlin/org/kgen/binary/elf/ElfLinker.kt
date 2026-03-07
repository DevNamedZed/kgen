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
        return ElfEmitter(layout, relocated, merger, symbols).emit()
    }

    private data class SectionPlacement(val objIdx: Int, val sectionName: String, val mergedOffset: Int, val size: Int)

    private data class ResolvedSymbol(val name: String, val value: Long, val sectionKind: SectionKind, val objIdx: Int)

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
        var needsStartStub = false

        fun resolve(): SymbolResolver {
            for ((objIdx, obj) in objects.withIndex()) {
                for (sym in obj.symbols) {
                    if (sym.section == null || sym.kind == SymbolKind.UNDEFINED) {
                        undefinedSymbols.add(sym.name)
                    } else {
                        val kind = merger.sectionKindMap[objIdx to sym.section] ?: continue
                        val placement = merger.placementsFor(kind)
                            .firstOrNull { it.objIdx == objIdx && it.sectionName == sym.section } ?: continue
                        globalSymbols[sym.name] = ResolvedSymbol(sym.name, sym.value + placement.mergedOffset, kind, objIdx)
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

        val numPhdrs = 6
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
        val rwSegmentFileSize: Long

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
            rwSegmentFileSize = rwOff - gotOffset
        }

        val sectionKindVaddr: Map<SectionKind, Long>
            get() = mapOf(
                SectionKind.TEXT to textVaddr + startStubSize,
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

    private class ElfEmitter(
        private val layout: SegmentLayout,
        private val relocatedText: ByteArray,
        private val merger: SectionMerger,
        private val symbols: SymbolResolver,
    ) {
        fun emit(): ByteArray {
            val buf = ByteArrayOutputStream()
            emitElfHeader(buf)
            emitProgramHeaders(buf)
            emitRxSegmentData(buf)
            emitRwSegmentData(buf)
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
            writeU16(buf, ElfMachine.X86_64.code)
            writeU32(buf, Elf.VERSION)
            writeU64(buf, entryVaddr)
            writeU64(buf, Elf.EHDR64_SIZE.toLong())
            writeU64(buf, 0)
            writeU32(buf, 0)
            writeU16(buf, Elf.EHDR64_SIZE)
            writeU16(buf, Elf.PHDR64_SIZE)
            writeU16(buf, layout.numPhdrs)
            writeU16(buf, 0)
            writeU16(buf, 0)
            writeU16(buf, 0)
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
        }

        private fun buildStartStub(): ByteArray {
            val stub = ByteArrayOutputStream()
            val stubVaddr = layout.textVaddr
            val mainVaddr = layout.symbolVaddrs()[symbols.entrySymbol.name]!!
            val exitVaddr = layout.pltSymbolVaddrs()["exit"]!!

            // _start:
            //   xor ebp, ebp               ; clear frame pointer
            stub.write(byteArrayOf(0x31, 0xED.toByte()))
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

            // Pad to startStubSize
            val pad = layout.startStubSize - stub.size()
            if (pad > 0) stub.write(ByteArray(pad))
            return stub.toByteArray()
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
            for (i in layout.dynamicSymbols.indices) {
                val gotEntryVaddr = layout.gotVaddr + (3 + i) * GOT_ENTRY_SIZE
                writeU64(buf, gotEntryVaddr)
                writeU64(buf, ((i + 1).toLong() shl 32) or RelocationType.X86_64.JUMP_SLOT.value.toLong())
                writeS64(buf, 0)
            }
            return buf.toByteArray()
        }

        private fun buildPlt(): ByteArray {
            if (layout.numPltEntries == 0) return ByteArray(0)
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

        private fun writePhdr(buf: ByteArrayOutputStream, type: Int, flags: Int,
                              offset: Long, vaddr: Long, paddr: Long,
                              filesz: Long, memsz: Long, align: Long) {
            writeU32(buf, type); writeU32(buf, flags)
            writeU64(buf, offset); writeU64(buf, vaddr); writeU64(buf, paddr)
            writeU64(buf, filesz); writeU64(buf, memsz); writeU64(buf, align)
        }
    }
}
