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
    private val machine: Int = PeConstants.MACHINE_AMD64,
    private val resources: ByteArray? = null,
    private val tlsData: ByteArray? = null,
    private val tlsCallbacks: List<Long> = emptyList(),
) {
    companion object {
        private const val FILE_ALIGNMENT = 0x200
        private const val SECTION_ALIGNMENT = 0x1000
        private const val THUNK_SIZE = 6 // FF 25 disp32
    }

    fun link(objects: List<ObjectFile>): ByteArray {
        val merged = SectionMerger(objects)
        val imports = ImportCollector(objects).collect()
        val rsrcFromObj = collectResources(objects)
        val tlsFromObj = collectTls(objects)
        val debugSections = collectDebugSections(objects)
        val layout = PeLayout(merged, imports, imageBase, subsystem,
            rsrcFromObj ?: resources, tlsFromObj ?: tlsData, tlsCallbacks, debugSections)
        val text = RelocationApplier(objects, merged, layout).apply()
        return PeEmitter(layout, text, merged, machine).emit()
    }

    private fun collectResources(objects: List<ObjectFile>): ByteArray? {
        for (obj in objects) {
            val rsrc = obj.sections.firstOrNull { it.kind == SectionKind.RSRC }
            if (rsrc != null && rsrc.data.isNotEmpty()) return rsrc.data
        }
        return null
    }

    private fun collectTls(objects: List<ObjectFile>): ByteArray? {
        for (obj in objects) {
            val tls = obj.sections.firstOrNull { it.kind == SectionKind.TLS || it.kind == SectionKind.TDATA }
            if (tls != null && tls.data.isNotEmpty()) return tls.data
        }
        return null
    }

    private data class DebugSectionData(val name: String, val data: ByteArray)

    private fun collectDebugSections(objects: List<ObjectFile>): List<DebugSectionData> {
        val bufs = mutableMapOf<String, ByteArrayOutputStream>()
        for (obj in objects) {
            for (sec in obj.sections) {
                if (sec.kind.isNonLoaded && sec.data.isNotEmpty()) {
                    bufs.getOrPut(sec.name) { ByteArrayOutputStream() }.write(sec.data)
                }
            }
        }
        return bufs.map { (name, buf) -> DebugSectionData(name, buf.toByteArray()) }
    }

    private class SectionMerger(objects: List<ObjectFile>) {
        val textBuf = ByteArrayOutputStream()
        val rodataBuf = ByteArrayOutputStream()
        val pdataBuf = ByteArrayOutputStream()
        val xdataBuf = ByteArrayOutputStream()
        val textPlacements = mutableListOf<Placement>()
        val rodataPlacements = mutableListOf<Placement>()
        val pdataPlacements = mutableListOf<Placement>()
        val xdataPlacements = mutableListOf<Placement>()
        val symbolMap = mutableMapOf<String, ResolvedSymbol>()
        private val symbolBindings = mutableMapOf<String, SymbolBinding>()
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
                        SectionKind.PDATA -> pdataBuf to pdataPlacements
                        SectionKind.XDATA -> xdataBuf to xdataPlacements
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
                        SectionKind.PDATA -> pdataPlacements
                        SectionKind.XDATA -> xdataPlacements
                        else -> continue
                    }
                    val placement = placements.firstOrNull { it.objIdx == objIdx && it.sectionName == sym.section } ?: continue
                    val offset = if (secKind == SectionKind.TEXT) stubOffset else 0
                    // GLOBAL always wins over WEAK; skip if existing is GLOBAL
                    val existing = symbolBindings[sym.name]
                    if (existing == SymbolBinding.GLOBAL && sym.binding == SymbolBinding.WEAK) continue
                    symbolMap[sym.name] = ResolvedSymbol(sym.name, sym.value + placement.offset + offset, secKind)
                    symbolBindings[sym.name] = sym.binding
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
        val rsrcBytes: ByteArray? = null,
        val tlsData: ByteArray? = null,
        val tlsCallbacks: List<Long> = emptyList(),
        val debugSections: List<DebugSectionData> = emptyList(),
    ) {
        val userTextBytes = merger.textBuf.toByteArray()
        val rodataBytes = merger.rodataBuf.toByteArray()
        val pdataBytes = merger.pdataBuf.toByteArray()
        val xdataBytes = merger.xdataBuf.toByteArray()
        val hasRodata = rodataBytes.isNotEmpty()
        val hasPdata = pdataBytes.isNotEmpty()
        val hasXdata = xdataBytes.isNotEmpty()
        val hasImports = imports.isNotEmpty()
        val hasResources = rsrcBytes != null && rsrcBytes.isNotEmpty()
        val hasTls = tlsData != null && tlsData.isNotEmpty()
        val hasDebug = debugSections.isNotEmpty()

        val allImportedFunctions = imports.flatMap { it.functions }
        val thunksSize = allImportedFunctions.size * THUNK_SIZE
        val entryStubSize = if (merger.needsEntryStub) merger.entryStubSize else 0

        // .text = entry stub + user code + import thunks (appended)
        val userTextSize = userTextBytes.size
        val thunksOffset = entryStubSize + userTextSize // offset within .text where thunks start
        val totalTextSize = entryStubSize + userTextSize + thunksSize

        // .reloc section for base relocations (ASLR support)
        val relocBytes = buildBaseRelocations()
        val hasReloc = relocBytes.isNotEmpty()

        val numSections = 1 + (if (hasRodata) 1 else 0) + (if (hasPdata) 1 else 0) + (if (hasXdata) 1 else 0) + (if (hasImports) 1 else 0) + (if (hasResources) 1 else 0) + (if (hasTls) 1 else 0) + (if (hasReloc) 1 else 0) + debugSections.size
        val headersSize = align(64 + 4 + 20 + 240 + 40 * numSections, FILE_ALIGNMENT)
        val textRVA = SECTION_ALIGNMENT
        val textFileOffset = headersSize
        val textRawSize = align(totalTextSize, FILE_ALIGNMENT)

        val rdataRVA: Int
        val rdataFileOffset: Int
        val rdataRawSize: Int

        val pdataRVA: Int
        val pdataFileOffset: Int
        val pdataRawSize: Int

        val xdataRVA: Int
        val xdataFileOffset: Int
        val xdataRawSize: Int

        val idataRVA: Int
        val idataFileOffset: Int
        val idataRawSize: Int
        val idataBytes: ByteArray

        val iatRVA: Int
        val iatSize: Int

        val rsrcRVA: Int
        val rsrcFileOffset: Int
        val rsrcRawSize: Int

        val tlsRVA: Int
        val tlsFileOffset: Int
        val tlsRawSize: Int
        val tlsDirBytes: ByteArray

        val relocRVA: Int
        val relocFileOffset: Int
        val relocRawSize: Int

        // Debug section offsets (parallel to debugSections list)
        val debugRVAs = mutableListOf<Int>()
        val debugFileOffsets = mutableListOf<Int>()
        val debugRawSizes = mutableListOf<Int>()

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

            var afterRdata = if (hasRodata) rdataRVA + align(rodataBytes.size, SECTION_ALIGNMENT)
                               else textRVA + align(totalTextSize, SECTION_ALIGNMENT)
            var afterRdataFile = if (hasRodata) rdataFileOffset + rdataRawSize
                                 else textFileOffset + textRawSize

            if (hasPdata) {
                pdataRVA = afterRdata
                pdataFileOffset = afterRdataFile
                pdataRawSize = align(pdataBytes.size, FILE_ALIGNMENT)
                afterRdata = pdataRVA + align(pdataBytes.size, SECTION_ALIGNMENT)
                afterRdataFile = pdataFileOffset + pdataRawSize
            } else {
                pdataRVA = 0; pdataFileOffset = 0; pdataRawSize = 0
            }

            if (hasXdata) {
                xdataRVA = afterRdata
                xdataFileOffset = afterRdataFile
                xdataRawSize = align(xdataBytes.size, FILE_ALIGNMENT)
                afterRdata = xdataRVA + align(xdataBytes.size, SECTION_ALIGNMENT)
                afterRdataFile = xdataFileOffset + xdataRawSize
            } else {
                xdataRVA = 0; xdataFileOffset = 0; xdataRawSize = 0
            }

            val idataBaseRVA = afterRdata

            if (hasImports) {
                val idata = buildImportData(idataBaseRVA)
                idataBytes = idata
                idataRVA = idataBaseRVA
                idataFileOffset = afterRdataFile
                idataRawSize = align(idataBytes.size, FILE_ALIGNMENT)
            } else {
                idataBytes = ByteArray(0); idataRVA = 0; idataFileOffset = 0; idataRawSize = 0
            }

            iatRVA = iatEntryRVAs.values.minOrNull() ?: 0
            iatSize = if (iatEntryRVAs.isEmpty()) 0 else {
                imports.sumOf { it.functions.size + 1 } * 8
            }

            // .rsrc section (after .idata or last previous section)
            var nextSectionRVA = when {
                hasImports -> idataRVA + align(idataBytes.size, SECTION_ALIGNMENT)
                else -> afterRdata
            }
            var nextFileOffset = when {
                hasImports -> idataFileOffset + idataRawSize
                else -> afterRdataFile
            }

            if (hasResources) {
                rsrcRVA = nextSectionRVA
                rsrcFileOffset = nextFileOffset
                rsrcRawSize = align(rsrcBytes!!.size, FILE_ALIGNMENT)
                nextSectionRVA = rsrcRVA + align(rsrcBytes.size, SECTION_ALIGNMENT)
                nextFileOffset = rsrcFileOffset + rsrcRawSize
            } else {
                rsrcRVA = 0; rsrcFileOffset = 0; rsrcRawSize = 0
            }

            // .tls section: TLS directory (40 bytes for PE32+) + TLS data + callback array
            if (hasTls) {
                tlsRVA = nextSectionRVA
                tlsFileOffset = nextFileOffset
                // TLS directory struct: 40 bytes (PE32+)
                // TLS data follows the directory
                // Callbacks array: null-terminated list of 8-byte addresses
                val callbackArraySize = (tlsCallbacks.size + 1) * 8
                val tlsDirSize = 40
                val totalTlsSize = tlsDirSize + tlsData!!.size + callbackArraySize
                tlsRawSize = align(totalTlsSize, FILE_ALIGNMENT)

                // Build TLS directory bytes
                val dir = ByteArray(totalTlsSize)
                val dataVA = imageBase + tlsRVA + tlsDirSize
                val callbacksVA = imageBase + tlsRVA + tlsDirSize + tlsData.size
                // RawDataStartVA
                putU64(dir, 0, dataVA)
                // RawDataEndVA
                putU64(dir, 8, dataVA + tlsData.size)
                // AddressOfIndex (set to 0 — runtime fills this)
                putU64(dir, 16, 0)
                // AddressOfCallBacks
                putU64(dir, 24, if (tlsCallbacks.isNotEmpty()) callbacksVA else 0)
                // SizeOfZeroFill
                putU32(dir, 32, 0)
                // Characteristics
                putU32(dir, 36, 0)
                // Copy TLS data
                System.arraycopy(tlsData, 0, dir, tlsDirSize, tlsData.size)
                // Write callbacks
                for ((i, cb) in tlsCallbacks.withIndex()) {
                    putU64(dir, tlsDirSize + tlsData.size + i * 8, cb)
                }
                // Null terminator already zero from ByteArray init

                tlsDirBytes = dir
                nextSectionRVA = tlsRVA + align(totalTlsSize, SECTION_ALIGNMENT)
            } else {
                tlsRVA = 0; tlsFileOffset = 0; tlsRawSize = 0; tlsDirBytes = ByteArray(0)
            }

            // .reloc section for base relocations
            if (hasReloc) {
                relocRVA = nextSectionRVA
                relocFileOffset = nextFileOffset
                relocRawSize = align(relocBytes.size, FILE_ALIGNMENT)
                nextSectionRVA = relocRVA + align(relocBytes.size, SECTION_ALIGNMENT)
                nextFileOffset = relocFileOffset + relocRawSize
            } else {
                relocRVA = 0; relocFileOffset = 0; relocRawSize = 0
            }

            // Debug sections
            for (dbg in debugSections) {
                debugRVAs.add(nextSectionRVA)
                debugFileOffsets.add(nextFileOffset)
                val rawSize = align(dbg.data.size, FILE_ALIGNMENT)
                debugRawSizes.add(rawSize)
                nextSectionRVA += align(dbg.data.size, SECTION_ALIGNMENT)
                nextFileOffset += rawSize
            }

            imageSize = align(nextSectionRVA, SECTION_ALIGNMENT)

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
                SectionKind.PDATA -> pdataRVA
                SectionKind.XDATA -> xdataRVA
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

        /**
         * Build PE base relocation entries (.reloc section) for ASLR support.
         * Groups relocations by 4KB page. Each block: page RVA (4B) + block size (4B) + entries (2B each).
         * Entry format: top 4 bits = type (10 = DIR64), bottom 12 bits = page offset.
         */
        private fun buildBaseRelocations(): ByteArray {
            // Collect RVAs that need base relocation fixups.
            // Thunks use RIP-relative addressing, so they don't need base relocs.
            // IAT entries are handled by the loader via import directory.
            // TLS directory contains absolute VAs that need relocation.
            val relocRVAs = mutableListOf<Int>()

            if (hasTls) {
                // TLS directory has absolute VAs at offsets 0, 8, 16, 24
                relocRVAs.add(tlsRVA + 0)   // RawDataStartVA
                relocRVAs.add(tlsRVA + 8)   // RawDataEndVA
                relocRVAs.add(tlsRVA + 24)  // AddressOfCallBacks
            }

            if (relocRVAs.isEmpty()) return ByteArray(0)

            // Group by page
            val byPage = relocRVAs.sorted().groupBy { it and 0xFFFFF000.toInt() }
            val buf = ByteArrayOutputStream()
            for ((pageRVA, entries) in byPage) {
                val blockEntries = entries.map { rva ->
                    val offset = rva - pageRVA
                    (10 shl 12) or offset // IMAGE_REL_BASED_DIR64
                }
                // Block size must be aligned to 4 bytes. Each entry is 2 bytes.
                val entriesSize = blockEntries.size * 2
                val blockSize = 8 + entriesSize
                val alignedBlockSize = align(blockSize, 4)

                writeU32(buf, pageRVA)
                writeU32(buf, alignedBlockSize)
                for (entry in blockEntries) writeU16(buf, entry)
                // Pad to alignment
                val padBytes = alignedBlockSize - blockSize
                if (padBytes > 0) buf.write(ByteArray(padBytes))
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
        private fun putU32(arr: ByteArray, off: Int, v: Int) {
            arr[off] = (v and 0xFF).toByte(); arr[off + 1] = ((v shr 8) and 0xFF).toByte()
            arr[off + 2] = ((v shr 16) and 0xFF).toByte(); arr[off + 3] = ((v shr 24) and 0xFF).toByte()
        }
        private fun putU64(arr: ByteArray, off: Int, v: Long) {
            putU32(arr, off, v.toInt()); putU32(arr, off + 4, (v shr 32).toInt())
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
        private val machine: Int,
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
            writeU16(buf, machine)
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
            putU16(opt, 70, if (layout.hasReloc) 0x0160 else 0x0100) // DYNAMIC_BASE | HIGH_ENTROPY_VA | NX_COMPAT (or NX_COMPAT only)
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
            if (layout.hasResources) {
                putU32(opt, 112 + 16, layout.rsrcRVA)
                putU32(opt, 112 + 20, layout.rsrcBytes!!.size)
            }
            if (layout.hasTls) {
                putU32(opt, 112 + 72, layout.tlsRVA)
                putU32(opt, 112 + 76, layout.tlsDirBytes.size)
            }
            if (layout.hasPdata) {
                putU32(opt, 112 + 24, layout.pdataRVA)           // EXCEPTION data directory
                putU32(opt, 112 + 28, layout.pdataBytes.size)
            }
            if (layout.hasReloc) {
                putU32(opt, 112 + 40, layout.relocRVA)           // BASE_RELOCATION data directory
                putU32(opt, 112 + 44, layout.relocBytes.size)
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
            if (layout.hasPdata) {
                writeSectionHeader(buf, ".pdata", layout.pdataBytes.size, layout.pdataRVA,
                    layout.pdataRawSize, layout.pdataFileOffset, 0x40000040) // MEM_READ | INITIALIZED_DATA
            }
            if (layout.hasXdata) {
                writeSectionHeader(buf, ".xdata", layout.xdataBytes.size, layout.xdataRVA,
                    layout.xdataRawSize, layout.xdataFileOffset, 0x40000040) // MEM_READ | INITIALIZED_DATA
            }
            if (layout.hasImports) {
                writeSectionHeader(buf, ".idata", layout.idataBytes.size, layout.idataRVA,
                    layout.idataRawSize, layout.idataFileOffset, 0xC0000040.toInt())
            }
            if (layout.hasResources) {
                writeSectionHeader(buf, ".rsrc", layout.rsrcBytes!!.size, layout.rsrcRVA,
                    layout.rsrcRawSize, layout.rsrcFileOffset, 0x40000040) // MEM_READ | INITIALIZED_DATA
            }
            if (layout.hasTls) {
                writeSectionHeader(buf, ".tls", layout.tlsDirBytes.size, layout.tlsRVA,
                    layout.tlsRawSize, layout.tlsFileOffset, 0xC0000040.toInt())
            }
            if (layout.hasReloc) {
                writeSectionHeader(buf, ".reloc", layout.relocBytes.size, layout.relocRVA,
                    layout.relocRawSize, layout.relocFileOffset, 0x42000040) // MEM_READ | MEM_DISCARDABLE | INITIALIZED_DATA
            }
            for ((i, dbg) in layout.debugSections.withIndex()) {
                writeSectionHeader(buf, dbg.name, dbg.data.size, layout.debugRVAs[i],
                    layout.debugRawSizes[i], layout.debugFileOffsets[i],
                    0x42000040) // MEM_READ | MEM_DISCARDABLE | INITIALIZED_DATA
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
            if (layout.hasPdata) {
                buf.write(layout.pdataBytes)
                padTo(buf, layout.pdataFileOffset + layout.pdataRawSize)
            }
            if (layout.hasXdata) {
                buf.write(layout.xdataBytes)
                padTo(buf, layout.xdataFileOffset + layout.xdataRawSize)
            }
            if (layout.hasImports) {
                buf.write(layout.idataBytes)
                padTo(buf, layout.idataFileOffset + layout.idataRawSize)
            }
            if (layout.hasResources) {
                buf.write(layout.rsrcBytes!!)
                padTo(buf, layout.rsrcFileOffset + layout.rsrcRawSize)
            }
            if (layout.hasTls) {
                buf.write(layout.tlsDirBytes)
                padTo(buf, layout.tlsFileOffset + layout.tlsRawSize)
            }
            if (layout.hasReloc) {
                buf.write(layout.relocBytes)
                padTo(buf, layout.relocFileOffset + layout.relocRawSize)
            }
            for ((i, dbg) in layout.debugSections.withIndex()) {
                buf.write(dbg.data)
                padTo(buf, layout.debugFileOffsets[i] + layout.debugRawSizes[i])
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
