package org.kgen.binary.pe

import org.kgen.binary.*
import java.io.ByteArrayOutputStream

/**
 * Links relocatable ObjectFiles into a PE32+ DLL (.dll) with export tables.
 *
 * Produces a PE DLL with:
 * - Export directory (.edata) for all globally-bound symbols
 * - Import directory (.idata) for external DLL imports
 * - .text, .rdata sections
 * - IMAGE_FILE_DLL flag set
 * - Optional DllMain entry point
 */
class PeDllLinker(
    private val dllName: String = "output.dll",
    private val imageBase: Long = 0x180000000L,
    private val subsystem: Int = 3, // CONSOLE
) {
    companion object {
        private const val FILE_ALIGNMENT = 0x200
        private const val SECTION_ALIGNMENT = 0x1000
        private const val THUNK_SIZE = 6
    }

    fun link(objects: List<ObjectFile>): ByteArray {
        require(objects.isNotEmpty()) { "No object files to link" }
        val merger = SectionMerger(objects)
        val imports = ImportCollector(objects).collect()
        val layout = DllLayout(merger, imports, imageBase, dllName, subsystem)
        val text = RelocationApplier(objects, merger, layout).apply()
        return DllEmitter(layout, text, merger).emit()
    }

    private data class Placement(
        val objIdx: Int, val sectionName: String, val offset: Int, val size: Int,
    )

    private data class ResolvedSymbol(
        val name: String, val mergedOffset: Long, val kind: SectionKind,
    )

    private data class DllImport(val dllName: String, val functions: List<String>)

    private class SectionMerger(objects: List<ObjectFile>) {
        val textBuf = ByteArrayOutputStream()
        val rodataBuf = ByteArrayOutputStream()
        val textPlacements = mutableListOf<Placement>()
        val rodataPlacements = mutableListOf<Placement>()
        val symbolMap = mutableMapOf<String, ResolvedSymbol>()

        val exportedSymbols: List<String>

        init {
            val allExported = mutableListOf<String>()
            for ((objIdx, obj) in objects.withIndex()) {
                for (sec in obj.sections) {
                    val (buf, placements) = when (sec.kind) {
                        SectionKind.TEXT -> textBuf to textPlacements
                        SectionKind.RODATA -> rodataBuf to rodataPlacements
                        else -> continue
                    }
                    val aligned = alignStream(buf, maxOf(sec.align, 1))
                    placements.add(Placement(objIdx, sec.name, aligned, sec.data.size))
                    buf.write(sec.data)
                }
                for (sym in obj.symbols) {
                    if (sym.section == null || sym.kind == SymbolKind.UNDEFINED) continue
                    val secKind = obj.sections.firstOrNull { it.name == sym.section }?.kind ?: continue
                    val placements = when (secKind) {
                        SectionKind.TEXT -> textPlacements
                        SectionKind.RODATA -> rodataPlacements
                        else -> continue
                    }
                    val placement = placements.firstOrNull { it.objIdx == objIdx && it.sectionName == sym.section }
                        ?: continue
                    symbolMap[sym.name] = ResolvedSymbol(sym.name, sym.value + placement.offset, secKind)
                    if (sym.binding == SymbolBinding.GLOBAL) {
                        allExported.add(sym.name)
                    }
                }
            }
            exportedSymbols = allExported.sorted()
        }

        private fun alignStream(buf: ByteArrayOutputStream, alignment: Int): Int {
            val current = buf.size()
            val aligned = ((current + alignment - 1) / alignment) * alignment
            if (aligned > current) buf.write(ByteArray(aligned - current))
            return aligned
        }
    }

    private class ImportCollector(private val objects: List<ObjectFile>) {
        fun collect(): List<DllImport> {
            val byDll = mutableMapOf<String, MutableSet<String>>()
            for (obj in objects) {
                for (imp in obj.imports) {
                    byDll.getOrPut(imp.moduleName) { mutableSetOf() }.add(imp.symbolName)
                }
            }
            return byDll.map { (dll, funcs) -> DllImport(dll, funcs.sorted()) }
        }
    }

    private class DllLayout(
        val merger: SectionMerger,
        val imports: List<DllImport>,
        val imageBase: Long,
        val dllName: String,
        val subsystem: Int,
    ) {
        val userTextBytes = merger.textBuf.toByteArray()
        val rodataBytes = merger.rodataBuf.toByteArray()
        val hasRodata = rodataBytes.isNotEmpty()
        val hasImports = imports.isNotEmpty()
        val exportedSymbols = merger.exportedSymbols
        val hasExports = exportedSymbols.isNotEmpty()

        val allImportedFunctions = imports.flatMap { it.functions }
        val thunksSize = allImportedFunctions.size * THUNK_SIZE

        val userTextSize = userTextBytes.size
        val thunksOffset = userTextSize
        val totalTextSize = userTextSize + thunksSize

        // Section count: .text + optional .rdata + optional .edata + optional .idata
        val numSections = 1 +
            (if (hasRodata) 1 else 0) +
            (if (hasExports) 1 else 0) +
            (if (hasImports) 1 else 0)
        val headersSize = align(64 + 4 + 20 + 240 + 40 * numSections, FILE_ALIGNMENT)
        val textRVA = SECTION_ALIGNMENT
        val textFileOffset = headersSize
        val textRawSize = align(totalTextSize, FILE_ALIGNMENT)

        // Thunk + IAT maps
        val thunkRVAs = mutableMapOf<String, Int>()
        val iatEntryRVAs = mutableMapOf<String, Int>()

        val rdataRVA: Int; val rdataFileOffset: Int; val rdataRawSize: Int
        val edataRVA: Int; val edataFileOffset: Int; val edataRawSize: Int
        val edataBytes: ByteArray
        val idataRVA: Int; val idataFileOffset: Int; val idataRawSize: Int
        val idataBytes: ByteArray
        val iatRVA: Int; val iatSize: Int
        val imageSize: Int

        val entryPointRVA: Int

        init {
            for ((i, func) in allImportedFunctions.withIndex()) {
                thunkRVAs[func] = textRVA + thunksOffset + i * THUNK_SIZE
            }

            // .rdata
            var nextRVA = textRVA + align(totalTextSize, SECTION_ALIGNMENT)
            var nextFileOff = textFileOffset + textRawSize
            if (hasRodata) {
                rdataRVA = nextRVA; rdataFileOffset = nextFileOff
                rdataRawSize = align(rodataBytes.size, FILE_ALIGNMENT)
                nextRVA += align(rodataBytes.size, SECTION_ALIGNMENT)
                nextFileOff += rdataRawSize
            } else {
                rdataRVA = 0; rdataFileOffset = 0; rdataRawSize = 0
            }

            // .edata (export directory)
            if (hasExports) {
                edataRVA = nextRVA; edataFileOffset = nextFileOff
                edataBytes = buildExportData(edataRVA)
                edataRawSize = align(edataBytes.size, FILE_ALIGNMENT)
                nextRVA += align(edataBytes.size, SECTION_ALIGNMENT)
                nextFileOff += edataRawSize
            } else {
                edataRVA = 0; edataFileOffset = 0; edataRawSize = 0; edataBytes = ByteArray(0)
            }

            // .idata
            if (hasImports) {
                idataRVA = nextRVA; idataFileOffset = nextFileOff
                idataBytes = buildImportData(idataRVA)
                idataRawSize = align(idataBytes.size, FILE_ALIGNMENT)
                nextRVA += align(idataBytes.size, SECTION_ALIGNMENT)
                nextFileOff += idataRawSize
            } else {
                idataRVA = 0; idataFileOffset = 0; idataRawSize = 0; idataBytes = ByteArray(0)
            }

            iatRVA = iatEntryRVAs.values.minOrNull() ?: 0
            iatSize = if (iatEntryRVAs.isEmpty()) 0 else {
                imports.sumOf { it.functions.size + 1 } * 8
            }

            imageSize = align(nextRVA, SECTION_ALIGNMENT)

            // Entry point: DllMain if present, else 0
            val dllMain = merger.symbolMap["DllMain"] ?: merger.symbolMap["_DllMainCRTStartup"]
            entryPointRVA = if (dllMain != null) textRVA + dllMain.mergedOffset.toInt() else 0
        }

        fun symbolVaddr(name: String): Long? {
            val resolved = merger.symbolMap[name] ?: return null
            val sectionRVA = when (resolved.kind) {
                SectionKind.TEXT -> textRVA
                SectionKind.RODATA -> rdataRVA
                else -> return null
            }
            return imageBase + sectionRVA + resolved.mergedOffset
        }

        fun thunkVaddr(name: String): Long? {
            val rva = thunkRVAs[name] ?: return null
            return imageBase + rva
        }

        fun buildThunks(): ByteArray {
            val buf = ByteArrayOutputStream()
            for (func in allImportedFunctions) {
                val thunkRVA = thunkRVAs[func]!!
                val iatSlotRVA = iatEntryRVAs[func]!!
                buf.write(0xFF); buf.write(0x25)
                writeI32(buf, iatSlotRVA - (thunkRVA + THUNK_SIZE))
            }
            return buf.toByteArray()
        }

        private fun buildExportData(baseRVA: Int): ByteArray {
            val numFuncs = exportedSymbols.size
            // Export directory table: 40 bytes
            // Address table: numFuncs * 4
            // Name pointer table: numFuncs * 4
            // Ordinal table: numFuncs * 2
            // Names + DLL name
            val dirSize = 40
            val addrTableOff = dirSize
            val nameTableOff = addrTableOff + numFuncs * 4
            val ordTableOff = nameTableOff + numFuncs * 4
            var nameStrOff = ordTableOff + numFuncs * 2
            // Align name strings
            if (nameStrOff % 2 != 0) nameStrOff++

            val nameOffsets = mutableMapOf<String, Int>()
            var off = nameStrOff
            for (sym in exportedSymbols) {
                nameOffsets[sym] = off
                off += sym.length + 1
            }
            val dllNameOff = off
            off += dllName.length + 1
            val totalSize = off

            val buf = ByteArrayOutputStream()

            // Export directory table
            writeU32(buf, 0) // Characteristics
            writeU32(buf, 0) // TimeDateStamp
            writeU16(buf, 0); writeU16(buf, 0) // Version
            writeU32(buf, baseRVA + dllNameOff) // Name RVA
            writeU32(buf, 1) // OrdinalBase
            writeU32(buf, numFuncs) // NumberOfFunctions
            writeU32(buf, numFuncs) // NumberOfNames
            writeU32(buf, baseRVA + addrTableOff) // AddressOfFunctions
            writeU32(buf, baseRVA + nameTableOff) // AddressOfNames
            writeU32(buf, baseRVA + ordTableOff) // AddressOfNameOrdinals

            // Address table (RVAs of exported functions)
            for (sym in exportedSymbols) {
                val resolved = merger.symbolMap[sym]!!
                val rva = when (resolved.kind) {
                    SectionKind.TEXT -> textRVA + resolved.mergedOffset.toInt()
                    SectionKind.RODATA -> rdataRVA + resolved.mergedOffset.toInt()
                    else -> 0
                }
                writeU32(buf, rva)
            }

            // Name pointer table
            for (sym in exportedSymbols) {
                writeU32(buf, baseRVA + nameOffsets[sym]!!)
            }

            // Ordinal table
            for (i in exportedSymbols.indices) {
                writeU16(buf, i)
            }

            // Pad to nameStrOff
            while (buf.size() < nameStrOff) buf.write(0)

            // Name strings
            for (sym in exportedSymbols) {
                buf.write(sym.toByteArray(Charsets.US_ASCII))
                buf.write(0)
            }

            // DLL name
            buf.write(dllName.toByteArray(Charsets.US_ASCII))
            buf.write(0)

            while (buf.size() < totalSize) buf.write(0)
            return buf.toByteArray()
        }

        private fun buildImportData(baseRVA: Int): ByteArray {
            val buf = ByteArrayOutputStream()
            val numDlls = imports.size
            val idtSize = (numDlls + 1) * 20
            val totalIltSlots = imports.sumOf { it.functions.size + 1 }
            val iltOffset = idtSize
            val iatOffset = iltOffset + totalIltSlots * 8

            var hintNameOffset = iatOffset + totalIltSlots * 8
            val hintNameOffsets = mutableMapOf<String, Int>()
            for (dll in imports) {
                for (func in dll.functions) {
                    hintNameOffsets[func] = hintNameOffset
                    hintNameOffset += 2 + func.length + 1
                    if (hintNameOffset % 2 != 0) hintNameOffset++
                }
            }
            val dllNameOffsets = mutableMapOf<String, Int>()
            var nameOff = hintNameOffset
            for (dll in imports) {
                dllNameOffsets[dll.dllName] = nameOff
                nameOff += dll.dllName.length + 1
            }

            var iltSlotOff = iltOffset
            var iatSlotOff = iatOffset
            for (dll in imports) {
                writeU32(buf, baseRVA + iltSlotOff)
                writeU32(buf, 0); writeU32(buf, 0)
                writeU32(buf, baseRVA + dllNameOffsets[dll.dllName]!!)
                writeU32(buf, baseRVA + iatSlotOff)
                iltSlotOff += (dll.functions.size + 1) * 8
                iatSlotOff += (dll.functions.size + 1) * 8
            }
            buf.write(ByteArray(20))

            for (dll in imports) {
                for (func in dll.functions) {
                    writeU64(buf, (baseRVA + hintNameOffsets[func]!!).toLong())
                }
                writeU64(buf, 0)
            }

            iatSlotOff = iatOffset
            for (dll in imports) {
                for (func in dll.functions) {
                    iatEntryRVAs[func] = baseRVA + iatSlotOff
                    writeU64(buf, (baseRVA + hintNameOffsets[func]!!).toLong())
                    iatSlotOff += 8
                }
                writeU64(buf, 0); iatSlotOff += 8
            }

            padTo(buf, iatOffset + totalIltSlots * 8)
            for (dll in imports) {
                for (func in dll.functions) {
                    padTo(buf, hintNameOffsets[func]!!)
                    writeU16(buf, 0)
                    buf.write(func.toByteArray(Charsets.US_ASCII)); buf.write(0)
                    if (buf.size() % 2 != 0) buf.write(0)
                }
            }
            for (dll in imports) {
                padTo(buf, dllNameOffsets[dll.dllName]!!)
                buf.write(dll.dllName.toByteArray(Charsets.US_ASCII)); buf.write(0)
            }
            padTo(buf, nameOff)
            return buf.toByteArray()
        }

        private fun padTo(buf: ByteArrayOutputStream, target: Int) {
            val pad = target - buf.size()
            if (pad > 0) buf.write(ByteArray(pad))
        }

        private fun writeU16(buf: ByteArrayOutputStream, v: Int) {
            buf.write(v and 0xFF); buf.write((v shr 8) and 0xFF)
        }
        private fun writeU32(buf: ByteArrayOutputStream, v: Int) {
            buf.write(v and 0xFF); buf.write((v shr 8) and 0xFF)
            buf.write((v shr 16) and 0xFF); buf.write((v shr 24) and 0xFF)
        }
        private fun writeU64(buf: ByteArrayOutputStream, v: Long) {
            writeU32(buf, v.toInt()); writeU32(buf, (v shr 32).toInt())
        }
        private fun writeI32(buf: ByteArrayOutputStream, v: Int) = writeU32(buf, v)
    }

    private class RelocationApplier(
        private val objects: List<ObjectFile>,
        private val merger: SectionMerger,
        private val layout: DllLayout,
    ) {
        fun apply(): ByteArray {
            val text = layout.userTextBytes.copyOf()
            for ((objIdx, obj) in objects.withIndex()) {
                for (rel in obj.relocations) {
                    if (rel.section == null) continue
                    val sec = obj.sections.firstOrNull { it.name == rel.section } ?: continue
                    if (sec.kind != SectionKind.TEXT) continue

                    val placement = merger.textPlacements
                        .firstOrNull { it.objIdx == objIdx && it.sectionName == rel.section }
                        ?: continue
                    val patchOffset = (placement.offset + rel.offset).toInt()
                    val patchVaddr = layout.imageBase + layout.textRVA + patchOffset

                    val targetVaddr = layout.symbolVaddr(rel.symbol)
                        ?: layout.thunkVaddr(rel.symbol)
                        ?: throw IllegalStateException("Undefined symbol: ${rel.symbol}")

                    when (rel.type) {
                        RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32,
                        RelocationType.COFF_X86_64.REL32 -> {
                            putI32(text, patchOffset, (targetVaddr + rel.addend - patchVaddr).toInt())
                        }
                        else -> throw IllegalStateException("Unsupported relocation: ${rel.type}")
                    }
                }
            }
            return text
        }

        private fun putI32(data: ByteArray, offset: Int, value: Int) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value shr 8) and 0xFF).toByte()
            data[offset + 2] = ((value shr 16) and 0xFF).toByte()
            data[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
    }

    private class DllEmitter(
        private val layout: DllLayout,
        private val userText: ByteArray,
        private val merger: SectionMerger,
    ) {
        fun emit(): ByteArray {
            val buf = ByteArrayOutputStream()
            emitDosHeader(buf)
            buf.write(byteArrayOf('P'.code.toByte(), 'E'.code.toByte(), 0, 0))
            emitCoffHeader(buf)
            emitOptionalHeader(buf)
            emitSectionHeaders(buf)
            emitSectionData(buf)
            return buf.toByteArray()
        }

        private fun emitDosHeader(buf: ByteArrayOutputStream) {
            val dos = ByteArray(64)
            dos[0] = 'M'.code.toByte(); dos[1] = 'Z'.code.toByte()
            putU16(dos, 2, 0x0090); putU16(dos, 4, 0x0003)
            putU16(dos, 8, 0x0004); putU16(dos, 10, 0xFFFF)
            putU16(dos, 16, 0x00B8); putU16(dos, 24, 0x0040)
            putU32(dos, 0x3C, 64)
            buf.write(dos)
        }

        private fun emitCoffHeader(buf: ByteArrayOutputStream) {
            writeU16(buf, PeConstants.MACHINE_AMD64)
            writeU16(buf, layout.numSections)
            writeU32(buf, 0); writeU32(buf, 0); writeU32(buf, 0)
            writeU16(buf, 240)
            writeU16(buf, PeConstants.IMAGE_FILE_EXECUTABLE_IMAGE or
                PeConstants.IMAGE_FILE_LARGE_ADDRESS_AWARE or
                PeConstants.IMAGE_FILE_DLL)
        }

        private fun emitOptionalHeader(buf: ByteArrayOutputStream) {
            val opt = ByteArray(240)
            putU16(opt, 0, PeConstants.PE32PLUS_MAGIC)
            opt[2] = 14 // Linker version
            putU32(opt, 4, layout.totalTextSize)
            putU32(opt, 16, layout.entryPointRVA)
            putU32(opt, 20, layout.textRVA)
            putU64(opt, 24, layout.imageBase)
            putU32(opt, 32, SECTION_ALIGNMENT)
            putU32(opt, 36, FILE_ALIGNMENT)
            putU16(opt, 40, 6); putU16(opt, 44, 6); putU16(opt, 48, 6)
            putU32(opt, 56, layout.imageSize)
            putU32(opt, 60, layout.headersSize)
            putU16(opt, 68, layout.subsystem)
            putU16(opt, 70, 0x0160) // NX_COMPAT | DYNAMIC_BASE | HIGH_ENTROPY_VA
            putU64(opt, 72, 0x100000); putU64(opt, 80, 0x1000)
            putU64(opt, 88, 0x100000); putU64(opt, 96, 0x1000)
            putU32(opt, 108, 16) // NumberOfRvaAndSizes

            // Data directories (offset 112)
            // Export table (index 0)
            if (layout.hasExports) {
                putU32(opt, 112, layout.edataRVA)
                putU32(opt, 116, layout.edataBytes.size)
            }
            // Import table (index 1)
            if (layout.hasImports) {
                putU32(opt, 120, layout.idataRVA)
                putU32(opt, 124, layout.idataBytes.size)
            }
            // IAT (index 12)
            if (layout.hasImports) {
                putU32(opt, 112 + 96, layout.iatRVA)
                putU32(opt, 112 + 100, layout.iatSize)
            }

            buf.write(opt)
        }

        private fun emitSectionHeaders(buf: ByteArrayOutputStream) {
            writeSectionHeader(buf, ".text", layout.totalTextSize, layout.textRVA,
                layout.textRawSize, layout.textFileOffset, 0x60000020)
            if (layout.hasRodata) {
                writeSectionHeader(buf, ".rdata", layout.rodataBytes.size, layout.rdataRVA,
                    layout.rdataRawSize, layout.rdataFileOffset, 0x40000040)
            }
            if (layout.hasExports) {
                writeSectionHeader(buf, ".edata", layout.edataBytes.size, layout.edataRVA,
                    layout.edataRawSize, layout.edataFileOffset, 0x40000040)
            }
            if (layout.hasImports) {
                writeSectionHeader(buf, ".idata", layout.idataBytes.size, layout.idataRVA,
                    layout.idataRawSize, layout.idataFileOffset, 0xC0000040.toInt())
            }
        }

        private fun emitSectionData(buf: ByteArrayOutputStream) {
            padTo(buf, layout.headersSize)
            buf.write(userText)
            buf.write(layout.buildThunks())
            padTo(buf, layout.textFileOffset + layout.textRawSize)

            if (layout.hasRodata) {
                buf.write(layout.rodataBytes)
                padTo(buf, layout.rdataFileOffset + layout.rdataRawSize)
            }
            if (layout.hasExports) {
                buf.write(layout.edataBytes)
                padTo(buf, layout.edataFileOffset + layout.edataRawSize)
            }
            if (layout.hasImports) {
                buf.write(layout.idataBytes)
                padTo(buf, layout.idataFileOffset + layout.idataRawSize)
            }
        }

        private fun writeSectionHeader(
            buf: ByteArrayOutputStream, name: String, virtualSize: Int,
            rva: Int, rawSize: Int, rawOffset: Int, characteristics: Int,
        ) {
            val nameBytes = name.toByteArray(Charsets.US_ASCII)
            buf.write(nameBytes, 0, minOf(nameBytes.size, 8))
            if (nameBytes.size < 8) buf.write(ByteArray(8 - nameBytes.size))
            writeU32(buf, virtualSize); writeU32(buf, rva)
            writeU32(buf, rawSize); writeU32(buf, rawOffset)
            writeU32(buf, 0); writeU32(buf, 0)
            writeU16(buf, 0); writeU16(buf, 0)
            writeU32(buf, characteristics)
        }

        private fun padTo(buf: ByteArrayOutputStream, size: Int) {
            val pad = size - buf.size()
            if (pad > 0) buf.write(ByteArray(pad))
        }

        private fun writeU16(buf: ByteArrayOutputStream, v: Int) {
            buf.write(v and 0xFF); buf.write((v shr 8) and 0xFF)
        }
        private fun writeU32(buf: ByteArrayOutputStream, v: Int) {
            buf.write(v and 0xFF); buf.write((v shr 8) and 0xFF)
            buf.write((v shr 16) and 0xFF); buf.write((v shr 24) and 0xFF)
        }
        private fun putU16(arr: ByteArray, off: Int, v: Int) {
            arr[off] = (v and 0xFF).toByte(); arr[off + 1] = ((v shr 8) and 0xFF).toByte()
        }
        private fun putU32(arr: ByteArray, off: Int, v: Int) {
            arr[off] = (v and 0xFF).toByte(); arr[off + 1] = ((v shr 8) and 0xFF).toByte()
            arr[off + 2] = ((v shr 16) and 0xFF).toByte(); arr[off + 3] = ((v shr 24) and 0xFF).toByte()
        }
        private fun putU64(arr: ByteArray, off: Int, v: Long) {
            putU32(arr, off, v.toInt()); putU32(arr, off + 4, (v shr 32).toInt())
        }
    }
}

private fun align(value: Int, alignment: Int): Int =
    (value + alignment - 1) and (alignment - 1).inv()
