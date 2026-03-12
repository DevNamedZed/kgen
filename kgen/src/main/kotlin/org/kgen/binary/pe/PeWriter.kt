package org.kgen.binary.pe

import org.kgen.binary.*
import java.io.ByteArrayOutputStream

/**
 * PE32+ (64-bit) writer supporting both executables and DLLs.
 *
 * For executables, produces a console executable with .text, optional .rdata, and .idata sections.
 * For DLLs, produces a DLL with .text, optional .rdata, .edata (export directory), and .idata sections.
 *
 * Import tables are built from the provided imports — any DLL and any set of functions.
 * The [write] method reads imports from ObjectFile.imports. For direct use, [writeExe] accepts
 * a map of DLL name to function names.
 *
 * The [write] method automatically selects DLL mode when the ObjectFile has [ObjectFlag.DLL] set or
 * contains export entries. Use [writeDll] directly for explicit DLL output.
 */
object PeWriter : ObjectFileWriter {

    override val format: ObjectFormat get() = ObjectFormat.PE_COFF

    override fun supportsArchitecture(arch: ArchType): Boolean = arch in setOf(
        ArchType.X86_64, ArchType.AARCH64, ArchType.X86, ArchType.ARM,
    )

    override fun write(obj: ObjectFile): ByteArray {
        val textSection = obj.sections.firstOrNull { it.kind == SectionKind.TEXT }
            ?: throw IllegalArgumentException("ObjectFile must have a TEXT section")
        val rodataSection = obj.sections.firstOrNull { it.kind == SectionKind.RODATA }
        val isDll = ObjectFlag.DLL in obj.metadata.flags || obj.exports.isNotEmpty()
        val imports = collectImports(obj)
        val delayImports = collectDelayImports(obj)
        if (isDll) {
            val exportNames = collectExportNames(obj)
            val dllName = obj.metadata.moduleName ?: "output.dll"
            return writeDll(textSection.data, rodataSection?.data ?: byteArrayOf(), exportNames, dllName, imports, delayImports)
        }
        if (imports.isEmpty() && delayImports.isEmpty()) {
            return writeFlat(textSection.data, rodataSection?.data ?: byteArrayOf())
        }
        return writeExe(textSection.data, rodataSection?.data ?: byteArrayOf(), imports, delayImports)
    }

    private const val FILE_ALIGNMENT = 0x200
    private const val SECTION_ALIGNMENT = 0x1000
    private const val IMAGE_BASE = 0x140000000L
    private const val DLL_IMAGE_BASE = 0x180000000L

    /**
     * Collects standard (non-delay) import entries from an ObjectFile, grouped by DLL name.
     */
    private fun collectImports(obj: ObjectFile): Map<String, List<String>> {
        if (obj.imports.isEmpty()) return emptyMap()
        return obj.imports
            .filter { !it.isDelayLoad }
            .groupBy { it.moduleName }
            .mapValues { (_, entries) -> entries.map { it.symbolName }.sorted() }
    }

    /**
     * Collects delay-load import entries from an ObjectFile, grouped by DLL name.
     */
    private fun collectDelayImports(obj: ObjectFile): Map<String, List<String>> {
        if (obj.imports.isEmpty()) return emptyMap()
        return obj.imports
            .filter { it.isDelayLoad }
            .groupBy { it.moduleName }
            .mapValues { (_, entries) -> entries.map { it.symbolName }.sorted() }
    }

    /**
     * Collects export names from an ObjectFile. Uses explicit exports if present,
     * otherwise collects all GLOBAL function/data symbols.
     */
    private fun collectExportNames(obj: ObjectFile): List<String> {
        if (obj.exports.isNotEmpty()) {
            return obj.exports.map { it.exportName ?: it.symbolName }.sorted()
        }
        return obj.symbols
            .filter { it.binding == SymbolBinding.GLOBAL && it.kind != SymbolKind.UNDEFINED && it.section != null }
            .map { it.name }
            .sorted()
    }

    /**
     * Write a PE32+ DLL with an export directory (.edata section) and optional imports (.idata section).
     *
     * Exported functions are listed by name and ordinal in the export directory table,
     * making them available for import by other PE binaries.
     *
     * @param code raw machine code for the .text section
     * @param rodata optional read-only data
     * @param exportNames list of function names to export (sorted alphabetically for binary search)
     * @param dllName the DLL filename embedded in the export directory (e.g., "mylib.dll")
     * @param imports map of DLL name to function names to import; empty for no imports
     */
    @JvmStatic
    @JvmOverloads
    fun writeDll(
        code: ByteArray,
        rodata: ByteArray = byteArrayOf(),
        exportNames: List<String>,
        dllName: String = "output.dll",
        imports: Map<String, List<String>> = emptyMap(),
        delayImports: Map<String, List<String>> = emptyMap(),
    ): ByteArray {
        val buf = ByteArrayOutputStream()
        val hasRodata = rodata.isNotEmpty()
        val hasExports = exportNames.isNotEmpty()
        val hasImports = imports.isNotEmpty()
        val hasDelayImports = delayImports.isNotEmpty()
        val numSections = 1 + (if (hasRodata) 1 else 0) + (if (hasExports) 1 else 0) + (if (hasImports) 1 else 0) + (if (hasDelayImports) 1 else 0)

        val headersSize = align(64 + 4 + 20 + 240 + 40 * numSections, FILE_ALIGNMENT)
        val textRVA = SECTION_ALIGNMENT
        val textFileOffset = headersSize
        val textRawSize = align(code.size, FILE_ALIGNMENT)

        var rdataRVA = 0; var rdataFileOffset = 0; var rdataRawSize = 0
        var nextRVA = textRVA + align(code.size, SECTION_ALIGNMENT)
        var nextFileOff = textFileOffset + textRawSize
        if (hasRodata) {
            rdataRVA = nextRVA; rdataFileOffset = nextFileOff
            rdataRawSize = align(rodata.size, FILE_ALIGNMENT)
            nextRVA += align(rodata.size, SECTION_ALIGNMENT)
            nextFileOff += rdataRawSize
        }

        var edataRVA = 0; var edataFileOffset = 0; var edataRawSize = 0
        var edataBytes = ByteArray(0)
        if (hasExports) {
            edataRVA = nextRVA; edataFileOffset = nextFileOff
            edataBytes = buildExportDirectory(edataRVA, textRVA, exportNames, code, dllName)
            edataRawSize = align(edataBytes.size, FILE_ALIGNMENT)
            nextRVA += align(edataBytes.size, SECTION_ALIGNMENT)
            nextFileOff += edataRawSize
        }

        var idataRVA = 0; var idataFileOffset = 0; var idataRawSize = 0
        var idataResult: ImportTableResult? = null
        if (hasImports) {
            idataRVA = nextRVA; idataFileOffset = nextFileOff
            idataResult = buildImportData(imports, idataRVA)
            idataRawSize = align(idataResult.data.size, FILE_ALIGNMENT)
            nextRVA += align(idataResult.data.size, SECTION_ALIGNMENT)
            nextFileOff += idataRawSize
        }

        var didataRVA = 0; var didataFileOffset = 0; var didataRawSize = 0
        var didataResult: DelayImportResult? = null
        if (hasDelayImports) {
            didataRVA = nextRVA; didataFileOffset = nextFileOff
            didataResult = buildDelayImportData(delayImports, didataRVA)
            didataRawSize = align(didataResult.data.size, FILE_ALIGNMENT)
            nextRVA += align(didataResult.data.size, SECTION_ALIGNMENT)
            nextFileOff += didataRawSize
        }

        val imageSize = align(nextRVA, SECTION_ALIGNMENT)

        // DOS header
        val dosHeader = ByteArray(64)
        dosHeader[0] = 'M'.code.toByte(); dosHeader[1] = 'Z'.code.toByte()
        putU32(dosHeader, 0x3C, 64)

        // COFF header
        val coffHeader = ByteArray(20)
        putU16(coffHeader, 0, 0x8664)
        putU16(coffHeader, 2, numSections)
        putU16(coffHeader, 16, 240)
        putU16(coffHeader, 18, PeConstants.IMAGE_FILE_EXECUTABLE_IMAGE or
            PeConstants.IMAGE_FILE_LARGE_ADDRESS_AWARE or
            PeConstants.IMAGE_FILE_DLL)

        // Optional header (PE32+)
        val optHeader = ByteArray(240)
        putU16(optHeader, 0, PeConstants.PE32PLUS_MAGIC)
        optHeader[2] = 14
        putU32(optHeader, 4, code.size)
        putU32(optHeader, 16, 0) // entry point (0 = no DllMain)
        putU32(optHeader, 20, textRVA)
        putU64(optHeader, 24, DLL_IMAGE_BASE)
        putU32(optHeader, 32, SECTION_ALIGNMENT)
        putU32(optHeader, 36, FILE_ALIGNMENT)
        putU16(optHeader, 40, 6); putU16(optHeader, 44, 6); putU16(optHeader, 48, 6)
        putU32(optHeader, 56, imageSize)
        putU32(optHeader, 60, headersSize)
        putU16(optHeader, 68, 3) // CONSOLE
        putU16(optHeader, 70, 0x0160) // NX_COMPAT | DYNAMIC_BASE | HIGH_ENTROPY_VA
        putU64(optHeader, 72, 0x100000); putU64(optHeader, 80, 0x1000)
        putU64(optHeader, 88, 0x100000); putU64(optHeader, 96, 0x1000)
        putU32(optHeader, 108, 16) // NumberOfRvaAndSizes

        // Data directories
        if (hasExports) {
            putU32(optHeader, 112, edataRVA)       // Export table RVA (index 0)
            putU32(optHeader, 116, edataBytes.size) // Export table size
        }
        if (hasImports) {
            putU32(optHeader, 112 + 8, idataRVA)                // Import table RVA (index 1)
            putU32(optHeader, 112 + 12, idataResult!!.data.size) // Import table size
            putU32(optHeader, 112 + 96, idataResult.iatRVA)      // IAT RVA (index 12)
            putU32(optHeader, 112 + 100, idataResult.iatSize)    // IAT size
        }
        if (hasDelayImports) {
            putU32(optHeader, 112 + 104, didataRVA)                  // data dir index 13
            putU32(optHeader, 112 + 108, didataResult!!.descriptorSize)
        }

        buf.write(dosHeader)
        buf.write(byteArrayOf('P'.code.toByte(), 'E'.code.toByte(), 0, 0))
        buf.write(coffHeader)
        buf.write(optHeader)

        writeSectionHeader(buf, ".text", code.size, textRVA, textRawSize, textFileOffset, 0x60000020)
        if (hasRodata) {
            writeSectionHeader(buf, ".rdata", rodata.size, rdataRVA, rdataRawSize, rdataFileOffset, 0x40000040)
        }
        if (hasExports) {
            writeSectionHeader(buf, ".edata", edataBytes.size, edataRVA, edataRawSize, edataFileOffset, 0x40000040)
        }
        if (hasImports) {
            writeSectionHeader(buf, ".idata", idataResult!!.data.size, idataRVA, idataRawSize, idataFileOffset, 0xC0000040.toInt())
        }
        if (hasDelayImports) {
            writeSectionHeader(buf, ".didata", didataResult!!.data.size, didataRVA, didataRawSize, didataFileOffset, 0xC0000040.toInt())
        }

        padTo(buf, headersSize)
        buf.write(code); padTo(buf, textFileOffset + textRawSize)
        if (hasRodata) { buf.write(rodata); padTo(buf, rdataFileOffset + rdataRawSize) }
        if (hasExports) { buf.write(edataBytes); padTo(buf, edataFileOffset + edataRawSize) }
        if (hasImports) { buf.write(idataResult!!.data); padTo(buf, idataFileOffset + idataRawSize) }
        if (hasDelayImports) { buf.write(didataResult!!.data); padTo(buf, didataFileOffset + didataRawSize) }

        return buf.toByteArray()
    }

    /**
     * Builds the export directory (.edata) section bytes.
     *
     * Layout:
     * - IMAGE_EXPORT_DIRECTORY (40 bytes)
     * - Address table: one RVA per exported function
     * - Name pointer table: RVAs to name strings
     * - Ordinal table: index into address table for each name
     * - Name strings (null-terminated ASCII)
     * - DLL name string (null-terminated ASCII)
     */
    private fun buildExportDirectory(
        edataRVA: Int,
        textRVA: Int,
        exportNames: List<String>,
        code: ByteArray,
        dllName: String,
    ): ByteArray {
        val sorted = exportNames.sorted()
        val numFuncs = sorted.size
        val dirSize = 40
        val addrTableOff = dirSize
        val nameTableOff = addrTableOff + numFuncs * 4
        val ordTableOff = nameTableOff + numFuncs * 4
        var nameStrOff = ordTableOff + numFuncs * 2
        if (nameStrOff % 2 != 0) nameStrOff++

        val nameOffsets = mutableMapOf<String, Int>()
        var off = nameStrOff
        for (sym in sorted) {
            nameOffsets[sym] = off
            off += sym.length + 1
        }
        val dllNameOff = off
        off += dllName.length + 1
        val totalSize = off

        val buf = ByteArrayOutputStream()

        // IMAGE_EXPORT_DIRECTORY (40 bytes)
        writeU32(buf, 0)                          // Characteristics
        writeU32(buf, 0)                          // TimeDateStamp
        writeU16(buf, 0); writeU16(buf, 0)        // MajorVersion, MinorVersion
        writeU32(buf, edataRVA + dllNameOff)      // Name RVA
        writeU32(buf, 1)                          // OrdinalBase
        writeU32(buf, numFuncs)                   // NumberOfFunctions
        writeU32(buf, numFuncs)                   // NumberOfNames
        writeU32(buf, edataRVA + addrTableOff)    // AddressOfFunctions
        writeU32(buf, edataRVA + nameTableOff)    // AddressOfNames
        writeU32(buf, edataRVA + ordTableOff)     // AddressOfNameOrdinals

        // Address table: each exported function gets a sequential RVA in .text
        for (i in sorted.indices) {
            writeU32(buf, textRVA + i)
        }

        // Name pointer table
        for (sym in sorted) {
            writeU32(buf, edataRVA + nameOffsets[sym]!!)
        }

        // Ordinal table (index into address table)
        for (i in sorted.indices) {
            writeU16(buf, i)
        }

        // Pad to name strings offset
        while (buf.size() < nameStrOff) buf.write(0)

        // Name strings
        for (sym in sorted) {
            buf.write(sym.toByteArray(Charsets.US_ASCII))
            buf.write(0)
        }

        // DLL name
        buf.write(dllName.toByteArray(Charsets.US_ASCII))
        buf.write(0)

        while (buf.size() < totalSize) buf.write(0)
        return buf.toByteArray()
    }

    /**
     * Write a PE32+ executable with arbitrary imports.
     *
     * @param code raw machine code for the .text section
     * @param rodata optional read-only data
     * @param imports map of DLL name to list of function names (e.g., "kernel32.dll" to ["ExitProcess"])
     */
    @JvmStatic
    @JvmOverloads
    fun writeExe(
        code: ByteArray,
        rodata: ByteArray = byteArrayOf(),
        imports: Map<String, List<String>>,
        delayImports: Map<String, List<String>> = emptyMap(),
    ): ByteArray {
        val buf = ByteArrayOutputStream()
        val hasRodata = rodata.isNotEmpty()
        val hasImports = imports.isNotEmpty()
        val hasDelayImports = delayImports.isNotEmpty()
        val numSections = 1 + (if (hasRodata) 1 else 0) + (if (hasImports) 1 else 0) + (if (hasDelayImports) 1 else 0)

        // DOS header
        val dosHeader = ByteArray(64)
        dosHeader[0] = 'M'.code.toByte(); dosHeader[1] = 'Z'.code.toByte()
        putU32(dosHeader, 0x3C, 64)

        // COFF header
        val coffHeader = ByteArray(20)
        putU16(coffHeader, 0, 0x8664)
        putU16(coffHeader, 2, numSections)
        putU16(coffHeader, 16, 240)
        putU16(coffHeader, 18, 0x22)

        // Layout
        val headersSize = align(64 + 4 + 20 + 240 + 40 * numSections, FILE_ALIGNMENT)
        val textRVA = SECTION_ALIGNMENT
        val textFileOffset = headersSize
        val textRawSize = align(code.size, FILE_ALIGNMENT)

        var rdataRVA = 0; var rdataFileOffset = 0; var rdataRawSize = 0
        var nextRVA = textRVA + align(code.size, SECTION_ALIGNMENT)
        var nextFileOff = textFileOffset + textRawSize
        if (hasRodata) {
            rdataRVA = nextRVA; rdataFileOffset = nextFileOff
            rdataRawSize = align(rodata.size, FILE_ALIGNMENT)
            nextRVA += align(rodata.size, SECTION_ALIGNMENT)
            nextFileOff += rdataRawSize
        }

        var idataRVA = 0; var idataFileOffset = 0; var idataRawSize = 0
        var idataResult: ImportTableResult? = null
        if (hasImports) {
            idataRVA = nextRVA; idataFileOffset = nextFileOff
            idataResult = buildImportData(imports, idataRVA)
            idataRawSize = align(idataResult.data.size, FILE_ALIGNMENT)
            nextRVA += align(idataResult.data.size, SECTION_ALIGNMENT)
            nextFileOff += idataRawSize
        }

        var didataRVA = 0; var didataFileOffset = 0; var didataRawSize = 0
        var didataResult: DelayImportResult? = null
        if (hasDelayImports) {
            didataRVA = nextRVA; didataFileOffset = nextFileOff
            didataResult = buildDelayImportData(delayImports, didataRVA)
            didataRawSize = align(didataResult.data.size, FILE_ALIGNMENT)
            nextRVA += align(didataResult.data.size, SECTION_ALIGNMENT)
            nextFileOff += didataRawSize
        }

        val imageSize = align(nextRVA, SECTION_ALIGNMENT)

        // Optional header (PE32+)
        val optHeader = ByteArray(240)
        putU16(optHeader, 0, 0x20B)
        optHeader[2] = 14
        putU32(optHeader, 4, code.size)
        putU32(optHeader, 16, textRVA)
        putU32(optHeader, 20, textRVA)
        putU64(optHeader, 24, IMAGE_BASE)
        putU32(optHeader, 32, SECTION_ALIGNMENT)
        putU32(optHeader, 36, FILE_ALIGNMENT)
        putU16(optHeader, 40, 6)
        putU16(optHeader, 44, 6)
        putU32(optHeader, 56, imageSize)
        putU32(optHeader, 60, headersSize)
        putU16(optHeader, 68, 3) // CONSOLE
        putU16(optHeader, 70, 0x8160.toShort().toInt())
        putU64(optHeader, 72, 0x100000)
        putU64(optHeader, 80, 0x1000)
        putU64(optHeader, 88, 0x100000)
        putU64(optHeader, 96, 0x1000)
        putU32(optHeader, 108, 16)
        if (hasImports) {
            putU32(optHeader, 112 + 8, idataRVA)
            putU32(optHeader, 112 + 12, idataResult!!.data.size)
            putU32(optHeader, 112 + 96, idataResult.iatRVA)
            putU32(optHeader, 112 + 100, idataResult.iatSize)
        }
        if (hasDelayImports) {
            putU32(optHeader, 112 + 104, didataRVA)                  // data dir index 13
            putU32(optHeader, 112 + 108, didataResult!!.descriptorSize)
        }

        buf.write(dosHeader)
        buf.write(byteArrayOf('P'.code.toByte(), 'E'.code.toByte(), 0, 0))
        buf.write(coffHeader)
        buf.write(optHeader)

        writeSectionHeader(buf, ".text", code.size, textRVA, textRawSize, textFileOffset, 0x60000020)
        if (hasRodata) writeSectionHeader(buf, ".rdata", rodata.size, rdataRVA, rdataRawSize, rdataFileOffset, 0x40000040)
        if (hasImports) writeSectionHeader(buf, ".idata", idataResult!!.data.size, idataRVA, idataRawSize, idataFileOffset, 0xC0000040.toInt())
        if (hasDelayImports) writeSectionHeader(buf, ".didata", didataResult!!.data.size, didataRVA, didataRawSize, didataFileOffset, 0xC0000040.toInt())

        padTo(buf, headersSize)
        buf.write(code); padTo(buf, textFileOffset + textRawSize)
        if (hasRodata) { buf.write(rodata); padTo(buf, rdataFileOffset + rdataRawSize) }
        if (hasImports) { buf.write(idataResult!!.data); padTo(buf, idataFileOffset + idataRawSize) }
        if (hasDelayImports) { buf.write(didataResult!!.data); padTo(buf, didataFileOffset + didataRawSize) }

        return buf.toByteArray()
    }

    /**
     * Write a minimal PE32+ executable from raw code and optional rodata bytes.
     * Generates default import tables for kernel32.dll (GetStdHandle, WriteFile, ExitProcess).
     *
     * For custom imports, use [writeExe] instead.
     */
    @JvmStatic
    fun writeFlat(code: ByteArray, rodata: ByteArray = byteArrayOf()): ByteArray {
        return writeExe(code, rodata, DEFAULT_IMPORTS)
    }

    /**
     * Returns a map of function name to IAT entry virtual address for the given imports.
     * Use these to compute RIP-relative call targets in generated code.
     *
     * @param codeSize size of the .text section in bytes
     * @param rodataSize size of the .rdata section in bytes (0 if none)
     * @param imports map of DLL name to function names; defaults to kernel32 imports
     */
    @JvmStatic
    @JvmOverloads
    fun iatEntryMap(
        codeSize: Int,
        rodataSize: Int = 0,
        imports: Map<String, List<String>> = DEFAULT_IMPORTS,
    ): Map<String, Long> {
        val idataRVA = SECTION_ALIGNMENT + align(codeSize, SECTION_ALIGNMENT) +
            (if (rodataSize > 0) align(rodataSize, SECTION_ALIGNMENT) else 0)
        val result = buildImportData(imports, idataRVA)
        return result.iatEntryRVAs.mapValues { (_, rva) -> IMAGE_BASE + rva.toLong() }
    }

    /**
     * Returns the IAT entry RVAs for the default kernel32 imports [GetStdHandle, WriteFile, ExitProcess].
     * Use these to compute RIP-relative call targets in generated code.
     *
     * For arbitrary imports, use [iatEntryMap] instead.
     */
    @JvmStatic
    fun iatEntryRVAs(codeSize: Int, rodataSize: Int = 0): LongArray {
        val map = iatEntryMap(codeSize, rodataSize, DEFAULT_IMPORTS)
        return longArrayOf(
            map["GetStdHandle"]!!,
            map["WriteFile"]!!,
            map["ExitProcess"]!!,
        )
    }

    /** Section alignment constant, useful for computing RVAs externally. */
    @JvmStatic fun sectionAlignment(): Int = SECTION_ALIGNMENT
    @JvmStatic fun imageBase(): Long = IMAGE_BASE

    /** Default kernel32.dll imports for backward compatibility with [writeFlat]. */
    @JvmStatic
    val DEFAULT_IMPORTS: Map<String, List<String>> = mapOf(
        "kernel32.dll" to listOf("GetStdHandle", "WriteFile", "ExitProcess"),
    )

    /**
     * Result of building an import table (.idata section).
     *
     * @param data the raw .idata section bytes
     * @param iatRVA the RVA of the IAT within .idata
     * @param iatSize the total size of the IAT in bytes
     * @param iatEntryRVAs map of function name to its IAT slot RVA
     */
    private class ImportTableResult(
        val data: ByteArray,
        val iatRVA: Int,
        val iatSize: Int,
        val iatEntryRVAs: Map<String, Int>,
    )

    /**
     * Builds the import data (.idata section) for arbitrary DLL imports.
     *
     * Layout (PE32+ / 64-bit):
     * - Import Directory Table (IDT): one 20-byte entry per DLL + null terminator
     * - Import Lookup Table (ILT): 8-byte entries per function per DLL, null-terminated per DLL
     * - Import Address Table (IAT): mirror of ILT, patched by loader at runtime
     * - Hint/Name Table: 2-byte hint + null-terminated ASCII name per function
     * - DLL name strings: null-terminated ASCII
     */
    private fun buildImportData(imports: Map<String, List<String>>, baseRVA: Int): ImportTableResult {
        val dllList = imports.entries.sortedBy { it.key }
        val numDlls = dllList.size
        val buf = ByteArrayOutputStream()
        val iatEntryRVAs = mutableMapOf<String, Int>()

        // Calculate layout offsets
        val idtSize = (numDlls + 1) * 20
        val totalSlots = dllList.sumOf { it.value.size + 1 } // +1 for null terminator per DLL
        val iltOffset = idtSize
        val iltSize = totalSlots * 8
        val iatOffset = iltOffset + iltSize

        // Hint/Name table offset (after IAT)
        var hintNameOffset = iatOffset + totalSlots * 8
        val hintNameOffsets = mutableMapOf<String, Int>()
        for ((_, functions) in dllList) {
            for (func in functions) {
                if (func !in hintNameOffsets) {
                    hintNameOffsets[func] = hintNameOffset
                    hintNameOffset += 2 + func.length + 1
                    if (hintNameOffset % 2 != 0) hintNameOffset++
                }
            }
        }

        // DLL name string offsets
        val dllNameOffsets = mutableMapOf<String, Int>()
        var nameOffset = hintNameOffset
        for ((dllName, _) in dllList) {
            dllNameOffsets[dllName] = nameOffset
            nameOffset += dllName.length + 1
        }
        val totalSize = nameOffset

        // Write IDT entries
        var iltSlotOff = iltOffset
        var iatSlotOff = iatOffset
        for ((dllName, functions) in dllList) {
            writeU32(buf, baseRVA + iltSlotOff)              // OriginalFirstThunk (ILT RVA)
            writeU32(buf, 0)                                  // TimeDateStamp
            writeU32(buf, 0)                                  // ForwarderChain
            writeU32(buf, baseRVA + dllNameOffsets[dllName]!!) // Name RVA
            writeU32(buf, baseRVA + iatSlotOff)              // FirstThunk (IAT RVA)
            iltSlotOff += (functions.size + 1) * 8
            iatSlotOff += (functions.size + 1) * 8
        }
        // Null terminator IDT entry
        buf.write(ByteArray(20))

        // Write ILT entries
        for ((_, functions) in dllList) {
            for (func in functions) {
                writeU64(buf, (baseRVA + hintNameOffsets[func]!!).toLong())
            }
            writeU64(buf, 0) // null terminator
        }

        // Write IAT entries (mirror of ILT, but track RVAs)
        iatSlotOff = iatOffset
        for ((_, functions) in dllList) {
            for (func in functions) {
                iatEntryRVAs[func] = baseRVA + iatSlotOff
                writeU64(buf, (baseRVA + hintNameOffsets[func]!!).toLong())
                iatSlotOff += 8
            }
            writeU64(buf, 0) // null terminator
            iatSlotOff += 8
        }

        // Write Hint/Name entries
        padTo(buf, iatOffset + totalSlots * 8)
        val writtenHints = mutableSetOf<String>()
        for ((_, functions) in dllList) {
            for (func in functions) {
                if (func in writtenHints) continue
                writtenHints.add(func)
                padTo(buf, hintNameOffsets[func]!!)
                writeU16(buf, 0) // hint (0 = no hint)
                buf.write(func.toByteArray(Charsets.US_ASCII))
                buf.write(0)
                if (buf.size() % 2 != 0) buf.write(0)
            }
        }

        // Write DLL name strings
        for ((dllName, _) in dllList) {
            padTo(buf, dllNameOffsets[dllName]!!)
            buf.write(dllName.toByteArray(Charsets.US_ASCII))
            buf.write(0)
        }

        padTo(buf, totalSize)

        val iatRVA = iatEntryRVAs.values.minOrNull() ?: (baseRVA + iatOffset)
        val iatSize = totalSlots * 8

        return ImportTableResult(buf.toByteArray(), iatRVA, iatSize, iatEntryRVAs)
    }

    /**
     * Result of building a delay-load import section (.didata).
     */
    private class DelayImportResult(
        val data: ByteArray,
        val descriptorSize: Int,
        val iatRVA: Int,
        val iatSize: Int,
        val iatEntryRVAs: Map<String, Int>,
    )

    /**
     * Builds the delay-load import data (.didata section).
     *
     * Layout (PE32+ / 64-bit):
     * - Delay-load descriptor table: 32 bytes per DLL + 32-byte null terminator
     * - Module handle storage: 8 bytes per DLL (initialized to 0)
     * - Import Name Table (INT): 8-byte entries per function, null-terminated per DLL
     * - Import Address Table (IAT): 8-byte entries per function, null-terminated per DLL
     * - Hint/Name table: 2-byte hint + null-terminated ASCII name
     * - DLL name strings
     */
    private fun buildDelayImportData(imports: Map<String, List<String>>, baseRVA: Int): DelayImportResult {
        val dllList = imports.entries.sortedBy { it.key }
        val numDlls = dllList.size
        val buf = ByteArrayOutputStream()
        val iatEntryRVAs = mutableMapOf<String, Int>()

        // Calculate layout offsets
        val descriptorSize = (numDlls + 1) * 32
        val handleStorageOffset = descriptorSize
        val handleStorageSize = numDlls * 8
        val totalSlots = dllList.sumOf { it.value.size + 1 } // +1 null terminator per DLL
        val intOffset = handleStorageOffset + handleStorageSize
        val intSize = totalSlots * 8
        val iatOffset = intOffset + intSize
        val iatSize = totalSlots * 8

        // Hint/Name table
        var hintNameOffset = iatOffset + iatSize
        val hintNameOffsets = mutableMapOf<String, Int>()
        for ((_, functions) in dllList) {
            for (func in functions) {
                if (func !in hintNameOffsets) {
                    hintNameOffsets[func] = hintNameOffset
                    hintNameOffset += 2 + func.length + 1
                    if (hintNameOffset % 2 != 0) hintNameOffset++
                }
            }
        }

        // DLL name strings
        val dllNameOffsets = mutableMapOf<String, Int>()
        var nameOffset = hintNameOffset
        for ((dllName, _) in dllList) {
            dllNameOffsets[dllName] = nameOffset
            nameOffset += dllName.length + 1
        }
        val totalSize = nameOffset

        // Write delay-load descriptors (32 bytes each)
        var handleOff = handleStorageOffset
        var intSlotOff = intOffset
        var iatSlotOff = iatOffset
        for ((dllName, functions) in dllList) {
            writeU32(buf, 1)                                      // Attributes: 1 = RVA-based
            writeU32(buf, baseRVA + dllNameOffsets[dllName]!!)     // DllNameRVA
            writeU32(buf, baseRVA + handleOff)                    // ModuleHandleRVA
            writeU32(buf, baseRVA + iatSlotOff)                   // ImportAddressTableRVA
            writeU32(buf, baseRVA + intSlotOff)                   // ImportNameTableRVA
            writeU32(buf, 0)                                      // BoundImportAddressTableRVA
            writeU32(buf, 0)                                      // UnloadInformationTableRVA
            writeU32(buf, 0)                                      // TimeDateStamp
            handleOff += 8
            intSlotOff += (functions.size + 1) * 8
            iatSlotOff += (functions.size + 1) * 8
        }
        // Null terminator descriptor
        buf.write(ByteArray(32))

        // Module handle storage (one 8-byte slot per DLL, initialized to 0)
        buf.write(ByteArray(handleStorageSize))

        // Write INT entries (Import Name Table)
        for ((_, functions) in dllList) {
            for (func in functions) {
                writeU64(buf, (baseRVA + hintNameOffsets[func]!!).toLong())
            }
            writeU64(buf, 0) // null terminator
        }

        // Write IAT entries (initially same as INT — patched at runtime on first call)
        iatSlotOff = iatOffset
        for ((_, functions) in dllList) {
            for (func in functions) {
                iatEntryRVAs[func] = baseRVA + iatSlotOff
                writeU64(buf, (baseRVA + hintNameOffsets[func]!!).toLong())
                iatSlotOff += 8
            }
            writeU64(buf, 0) // null terminator
            iatSlotOff += 8
        }

        // Write Hint/Name entries
        padTo(buf, iatOffset + iatSize)
        val writtenHints = mutableSetOf<String>()
        for ((_, functions) in dllList) {
            for (func in functions) {
                if (func in writtenHints) continue
                writtenHints.add(func)
                padTo(buf, hintNameOffsets[func]!!)
                writeU16(buf, 0) // hint
                buf.write(func.toByteArray(Charsets.US_ASCII))
                buf.write(0)
                if (buf.size() % 2 != 0) buf.write(0)
            }
        }

        // Write DLL name strings
        for ((dllName, _) in dllList) {
            padTo(buf, dllNameOffsets[dllName]!!)
            buf.write(dllName.toByteArray(Charsets.US_ASCII))
            buf.write(0)
        }

        padTo(buf, totalSize)

        val iatRVA = iatEntryRVAs.values.minOrNull() ?: (baseRVA + iatOffset)
        return DelayImportResult(buf.toByteArray(), descriptorSize, iatRVA, iatSize, iatEntryRVAs)
    }

    private fun writeSectionHeader(buf: ByteArrayOutputStream, name: String, virtualSize: Int, rva: Int, rawSize: Int, rawOffset: Int, characteristics: Int) {
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

    private fun align(value: Int, alignment: Int): Int =
        (value + alignment - 1) and (alignment - 1).inv()

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
