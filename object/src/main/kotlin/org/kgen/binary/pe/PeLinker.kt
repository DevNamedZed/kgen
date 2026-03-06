package org.kgen.binary.pe

import org.kgen.binary.*
import java.io.ByteArrayOutputStream

/**
 * Links relocatable ObjectFiles into a PE32+ (64-bit) executable with dynamic imports.
 *
 * Supports:
 * - Configurable DLL imports (resolved from ObjectFile.imports)
 * - .text, .rdata, .idata sections
 * - Import thunks for indirect calls via IAT
 * - Symbol relocation (PC32, PLT32)
 */
class PeLinker(
    private val subsystem: Int = 3, // CONSOLE
    private val imageBase: Long = 0x140000000L,
) {
    companion object {
        private const val FILE_ALIGNMENT = 0x200
        private const val SECTION_ALIGNMENT = 0x1000
        private const val THUNK_SIZE = 6 // FF 25 disp32
    }

    fun link(objects: List<ObjectFile>): ByteArray {
        val merged = SectionMerger(objects)
        val imports = ImportCollector(objects).collect()
        val layout = PeLayout(merged, imports, imageBase, subsystem)
        val text = RelocationApplier(objects, merged, layout).apply()
        return PeEmitter(layout, text, merged).emit()
    }

    private class SectionMerger(objects: List<ObjectFile>) {
        val textBuf = ByteArrayOutputStream()
        val rodataBuf = ByteArrayOutputStream()
        val textPlacements = mutableListOf<Placement>()
        val rodataPlacements = mutableListOf<Placement>()
        val symbolMap = mutableMapOf<String, ResolvedSymbol>()
        var needsEntryStub = false
        // Entry stub: sub rsp,28h + call main + mov ecx,eax + call ExitProcess = 20 bytes, padded to 32
        val entryStubSize = 32

        data class Placement(val objIdx: Int, val sectionName: String, val offset: Int, val size: Int)
        data class ResolvedSymbol(val name: String, val mergedOffset: Long, val kind: SectionKind)

        init {
            // Check if we need an entry stub (main exists, no _start, ExitProcess imported)
            val hasMain = objects.any { o -> o.symbols.any { it.name == "main" && it.kind != SymbolKind.UNDEFINED } }
            val hasStart = objects.any { o -> o.symbols.any { it.name == "_start" && it.kind != SymbolKind.UNDEFINED } }
            val hasExitProcess = objects.any { o -> o.imports.any { it.symbolName == "ExitProcess" } }
            needsEntryStub = hasMain && !hasStart && hasExitProcess
            val stubOffset = if (needsEntryStub) entryStubSize else 0

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
                    val placement = placements.firstOrNull { it.objIdx == objIdx && it.sectionName == sym.section } ?: continue
                    val offset = if (secKind == SectionKind.TEXT) stubOffset else 0
                    symbolMap[sym.name] = ResolvedSymbol(sym.name, sym.value + placement.offset + offset, secKind)
                }
            }
        }

        private fun alignStream(buf: ByteArrayOutputStream, alignment: Int): Int {
            val current = buf.size()
            val aligned = ((current + alignment - 1) / alignment) * alignment
            if (aligned > current) buf.write(ByteArray(aligned - current))
            return aligned
        }
    }

    private data class DllImport(val dllName: String, val functions: List<String>)

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

    private class PeLayout(
        val merger: SectionMerger,
        val imports: List<DllImport>,
        val imageBase: Long,
        val subsystem: Int,
    ) {
        val userTextBytes = merger.textBuf.toByteArray()
        val rodataBytes = merger.rodataBuf.toByteArray()
        val hasRodata = rodataBytes.isNotEmpty()
        val hasImports = imports.isNotEmpty()

        val allImportedFunctions = imports.flatMap { it.functions }
        val thunksSize = allImportedFunctions.size * THUNK_SIZE
        val entryStubSize = if (merger.needsEntryStub) merger.entryStubSize else 0

        // .text = entry stub + user code + import thunks (appended)
        val userTextSize = userTextBytes.size
        val thunksOffset = entryStubSize + userTextSize // offset within .text where thunks start
        val totalTextSize = entryStubSize + userTextSize + thunksSize

        val numSections = 1 + (if (hasRodata) 1 else 0) + (if (hasImports) 1 else 0)
        val headersSize = align(64 + 4 + 20 + 240 + 40 * numSections, FILE_ALIGNMENT)
        val textRVA = SECTION_ALIGNMENT
        val textFileOffset = headersSize
        val textRawSize = align(totalTextSize, FILE_ALIGNMENT)

        val rdataRVA: Int
        val rdataFileOffset: Int
        val rdataRawSize: Int

        val idataRVA: Int
        val idataFileOffset: Int
        val idataRawSize: Int
        val idataBytes: ByteArray

        val iatRVA: Int
        val iatSize: Int
        val imageSize: Int

        // Thunk RVA for each imported function (within .text section)
        val thunkRVAs = mutableMapOf<String, Int>()
        // IAT slot RVA for each imported function
        val iatEntryRVAs = mutableMapOf<String, Int>()

        val entryPointRVA: Int

        init {
            // Assign thunk RVAs
            for ((i, func) in allImportedFunctions.withIndex()) {
                thunkRVAs[func] = textRVA + thunksOffset + i * THUNK_SIZE
            }

            if (hasRodata) {
                rdataRVA = textRVA + align(totalTextSize, SECTION_ALIGNMENT)
                rdataFileOffset = textFileOffset + textRawSize
                rdataRawSize = align(rodataBytes.size, FILE_ALIGNMENT)
            } else {
                rdataRVA = 0; rdataFileOffset = 0; rdataRawSize = 0
            }

            val idataBaseRVA = if (hasRodata) rdataRVA + align(rodataBytes.size, SECTION_ALIGNMENT)
                               else textRVA + align(totalTextSize, SECTION_ALIGNMENT)

            if (hasImports) {
                val idata = buildImportData(idataBaseRVA)
                idataBytes = idata
                idataRVA = idataBaseRVA
                idataFileOffset = if (hasRodata) rdataFileOffset + rdataRawSize
                                  else textFileOffset + textRawSize
                idataRawSize = align(idataBytes.size, FILE_ALIGNMENT)
            } else {
                idataBytes = ByteArray(0); idataRVA = 0; idataFileOffset = 0; idataRawSize = 0
            }

            iatRVA = iatEntryRVAs.values.minOrNull() ?: 0
            iatSize = if (iatEntryRVAs.isEmpty()) 0 else {
                imports.sumOf { it.functions.size + 1 } * 8
            }

            val lastSectionEnd = when {
                hasImports -> idataRVA + idataBytes.size
                hasRodata -> rdataRVA + rodataBytes.size
                else -> textRVA + totalTextSize
            }
            imageSize = align(lastSectionEnd, SECTION_ALIGNMENT)

            // Entry point: _start stub (at beginning of .text) or explicit _start/main
            entryPointRVA = if (merger.needsEntryStub) {
                textRVA // stub is at the start of .text
            } else {
                val entry = merger.symbolMap["_start"] ?: merger.symbolMap["main"]
                    ?: throw IllegalStateException("No main or _start symbol found")
                textRVA + entry.mergedOffset.toInt()
            }
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

        fun buildEntryStub(): ByteArray {
            if (!merger.needsEntryStub) return ByteArray(0)
            val buf = ByteArrayOutputStream()
            val stubRVA = textRVA
            val mainRVA = merger.symbolMap["main"]!!.mergedOffset.toInt() + textRVA
            val exitThunkRVA = thunkRVAs["ExitProcess"]
                ?: throw IllegalStateException("ExitProcess thunk not found")

            // sub rsp, 0x28                ; 48 83 EC 28 (shadow space + alignment)
            buf.write(byteArrayOf(0x48, 0x83.toByte(), 0xEC.toByte(), 0x28))
            // call main                    ; E8 xx xx xx xx
            val callMainPC = stubRVA + buf.size() + 5
            buf.write(0xE8.toByte().toInt())
            writeI32(buf, mainRVA - callMainPC)
            // mov ecx, eax                 ; 89 C1 (return value → first arg for ExitProcess)
            buf.write(byteArrayOf(0x89.toByte(), 0xC1.toByte()))
            // call ExitProcess thunk       ; E8 xx xx xx xx
            val callExitPC = stubRVA + buf.size() + 5
            buf.write(0xE8.toByte().toInt())
            writeI32(buf, exitThunkRVA - callExitPC)
            // int3                         ; CC (should never reach)
            buf.write(0xCC.toByte().toInt())

            // Pad to entryStubSize
            val pad = entryStubSize - buf.size()
            if (pad > 0) buf.write(ByteArray(pad))
            return buf.toByteArray()
        }

        fun buildThunks(): ByteArray {
            val buf = ByteArrayOutputStream()
            for (func in allImportedFunctions) {
                val thunkRVA = thunkRVAs[func]!!
                val iatSlotRVA = iatEntryRVAs[func]!!
                // FF 25 disp32 — jmp [rip + disp32]
                // RIP after this instruction = thunkRVA + 6
                val disp = iatSlotRVA - (thunkRVA + THUNK_SIZE)
                buf.write(0xFF)
                buf.write(0x25)
                writeI32(buf, disp)
            }
            return buf.toByteArray()
        }

        private fun buildImportData(baseRVA: Int): ByteArray {
            val buf = ByteArrayOutputStream()
            val numDlls = imports.size

            val idtSize = (numDlls + 1) * 20
            val totalIltSlots = imports.sumOf { it.functions.size + 1 }
            val iltOffset = idtSize
            val iltSize = totalIltSlots * 8
            val iatOffset = iltOffset + iltSize

            // Hint/Name table offset
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
            var nameOffset = hintNameOffset
            for (dll in imports) {
                dllNameOffsets[dll.dllName] = nameOffset
                nameOffset += dll.dllName.length + 1
            }
            val totalSize = nameOffset

            // IDT
            var iltSlotOff = iltOffset
            var iatSlotOff = iatOffset
            for (dll in imports) {
                writeU32(buf, baseRVA + iltSlotOff)
                writeU32(buf, 0)
                writeU32(buf, 0)
                writeU32(buf, baseRVA + dllNameOffsets[dll.dllName]!!)
                writeU32(buf, baseRVA + iatSlotOff)
                iltSlotOff += (dll.functions.size + 1) * 8
                iatSlotOff += (dll.functions.size + 1) * 8
            }
            buf.write(ByteArray(20)) // null terminator

            // ILT
            for (dll in imports) {
                for (func in dll.functions) {
                    writeU64(buf, (baseRVA + hintNameOffsets[func]!!).toLong())
                }
                writeU64(buf, 0)
            }

            // IAT
            iatSlotOff = iatOffset
            for (dll in imports) {
                for (func in dll.functions) {
                    iatEntryRVAs[func] = baseRVA + iatSlotOff
                    writeU64(buf, (baseRVA + hintNameOffsets[func]!!).toLong())
                    iatSlotOff += 8
                }
                writeU64(buf, 0)
                iatSlotOff += 8
            }

            // Hint/Name entries
            padTo(buf, iatOffset + totalIltSlots * 8)
            for (dll in imports) {
                for (func in dll.functions) {
                    padTo(buf, hintNameOffsets[func]!!)
                    writeU16(buf, 0)
                    buf.write(func.toByteArray(Charsets.US_ASCII))
                    buf.write(0)
                    if (buf.size() % 2 != 0) buf.write(0)
                }
            }

            // DLL names
            for (dll in imports) {
                padTo(buf, dllNameOffsets[dll.dllName]!!)
                buf.write(dll.dllName.toByteArray(Charsets.US_ASCII))
                buf.write(0)
            }

            padTo(buf, totalSize)
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
        private fun writeI32(buf: ByteArrayOutputStream, v: Int) {
            writeU32(buf, v)
        }
    }

    private class RelocationApplier(
        private val objects: List<ObjectFile>,
        private val merger: SectionMerger,
        private val layout: PeLayout,
    ) {
        fun apply(): ByteArray {
            val text = layout.userTextBytes.copyOf()
            for ((objIdx, obj) in objects.withIndex()) {
                for (rel in obj.relocations) {
                    if (rel.section == null) continue
                    val sec = obj.sections.firstOrNull { it.name == rel.section } ?: continue
                    if (sec.kind != SectionKind.TEXT) continue

                    val placement = merger.textPlacements
                        .firstOrNull { it.objIdx == objIdx && it.sectionName == rel.section } ?: continue
                    val patchOffset = (placement.offset + rel.offset).toInt()
                    val patchVaddr = layout.imageBase + layout.textRVA + layout.entryStubSize + patchOffset

                    val targetVaddr = layout.symbolVaddr(rel.symbol)
                        ?: layout.thunkVaddr(rel.symbol)
                        ?: throw IllegalStateException("Undefined symbol: ${rel.symbol}")

                    when (rel.type) {
                        RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32 -> {
                            putI32(text, patchOffset, (targetVaddr + rel.addend - patchVaddr).toInt())
                        }
                        RelocationType.X86_64.R_32, RelocationType.X86_64.R_32S -> {
                            putI32(text, patchOffset, (targetVaddr + rel.addend).toInt())
                        }
                        else -> throw IllegalStateException("Unsupported relocation type: ${rel.type}")
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

    private class PeEmitter(
        private val layout: PeLayout,
        private val userText: ByteArray,
        private val merger: SectionMerger,
    ) {
        fun emit(): ByteArray {
            val buf = ByteArrayOutputStream()
            emitDosHeader(buf)
            emitPeSignature(buf)
            emitCoffHeader(buf)
            emitOptionalHeader(buf)
            emitSectionHeaders(buf)
            emitSectionData(buf)
            return buf.toByteArray()
        }

        private fun emitDosHeader(buf: ByteArrayOutputStream) {
            val dos = ByteArray(64)
            dos[0] = 'M'.code.toByte(); dos[1] = 'Z'.code.toByte()
            // Minimum required DOS header fields
            putU16(dos, 2, 0x0090)    // e_cblp: bytes on last page
            putU16(dos, 4, 0x0003)    // e_cp: pages in file
            putU16(dos, 8, 0x0004)    // e_minalloc
            putU16(dos, 10, 0xFFFF)   // e_maxalloc
            putU16(dos, 16, 0x00B8)   // e_sp: initial SP
            putU16(dos, 24, 0x0040)   // e_lfarlc: relocation table offset
            putU32(dos, 0x3C, 64)     // e_lfanew: PE header offset
            buf.write(dos)
        }

        private fun emitPeSignature(buf: ByteArrayOutputStream) {
            buf.write(byteArrayOf('P'.code.toByte(), 'E'.code.toByte(), 0, 0))
        }

        private fun emitCoffHeader(buf: ByteArrayOutputStream) {
            writeU16(buf, PeConstants.MACHINE_AMD64)
            writeU16(buf, layout.numSections)
            writeU32(buf, 0)
            writeU32(buf, 0)
            writeU32(buf, 0)
            writeU16(buf, 240)
            writeU16(buf, 0x22) // EXECUTABLE_IMAGE | LARGE_ADDRESS_AWARE
        }

        private fun emitOptionalHeader(buf: ByteArrayOutputStream) {
            val opt = ByteArray(240)
            putU16(opt, 0, PeConstants.PE32PLUS_MAGIC)
            opt[2] = 14
            putU32(opt, 4, layout.totalTextSize)
            putU32(opt, 16, layout.entryPointRVA) // AddressOfEntryPoint
            putU32(opt, 20, layout.textRVA)
            putU64(opt, 24, layout.imageBase)
            putU32(opt, 32, SECTION_ALIGNMENT)
            putU32(opt, 36, FILE_ALIGNMENT)
            putU16(opt, 40, 6)       // MajorOperatingSystemVersion
            putU16(opt, 44, 6)       // MajorImageVersion
            putU16(opt, 48, 6)       // MajorSubsystemVersion
            putU32(opt, 56, layout.imageSize)
            putU32(opt, 60, layout.headersSize)
            putU16(opt, 68, layout.subsystem)
            putU16(opt, 70, 0x0100) // NX_COMPAT only (no DYNAMIC_BASE without .reloc)
            putU64(opt, 72, 0x100000)
            putU64(opt, 80, 0x1000)
            putU64(opt, 88, 0x100000)
            putU64(opt, 96, 0x1000)
            putU32(opt, 108, 16)

            if (layout.hasImports) {
                putU32(opt, 112 + 8, layout.idataRVA)
                putU32(opt, 112 + 12, layout.idataBytes.size)
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
            if (layout.hasImports) {
                writeSectionHeader(buf, ".idata", layout.idataBytes.size, layout.idataRVA,
                    layout.idataRawSize, layout.idataFileOffset, 0xC0000040.toInt())
            }
        }

        private fun emitSectionData(buf: ByteArrayOutputStream) {
            padTo(buf, layout.headersSize)
            buf.write(layout.buildEntryStub())
            buf.write(userText)
            buf.write(layout.buildThunks())
            padTo(buf, layout.textFileOffset + layout.textRawSize)

            if (layout.hasRodata) {
                buf.write(layout.rodataBytes)
                padTo(buf, layout.rdataFileOffset + layout.rdataRawSize)
            }
            if (layout.hasImports) {
                buf.write(layout.idataBytes)
                padTo(buf, layout.idataFileOffset + layout.idataRawSize)
            }
        }

        private fun writeSectionHeader(buf: ByteArrayOutputStream, name: String, virtualSize: Int,
                                       rva: Int, rawSize: Int, rawOffset: Int, characteristics: Int) {
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
        private fun writeU64(buf: ByteArrayOutputStream, v: Long) {
            writeU32(buf, v.toInt()); writeU32(buf, (v shr 32).toInt())
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
