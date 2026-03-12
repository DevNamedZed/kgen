package org.kgen.binary.pe

import org.kgen.binary.pe.clr.ClrMetadata
import java.io.ByteArrayOutputStream

/**
 * Assembler for mixed-mode PE files containing both CLR (managed IL) and
 * native code in a single PE32+ executable.
 *
 * Mixed-mode assemblies allow managed code to call native code via IJW
 * (It Just Works) thunks, and native code to call back into managed code.
 * The resulting PE contains:
 * - `.text` section with native code
 * - `.cil` section with CLR metadata + IL method bodies
 * - `.idata` section with import tables (mscoree.dll for CLR bootstrap)
 * - `.reloc` section for base relocations
 *
 * ```java
 * var asm = new MixedModeAssembler("MyMixed");
 * asm.setNativeCode(machineCode);
 * asm.setClrMetadata(cilBuilder.toBytes());
 * asm.addNativeExport("nativeAdd", 0);
 * byte[] pe = asm.assemble();
 * ```
 */
class MixedModeAssembler(private val assemblyName: String) {

    private var nativeCode: ByteArray = byteArrayOf()
    private var clrMetadataBytes: ByteArray = byteArrayOf()
    private val nativeExports = mutableListOf<NativeExport>()
    private var entryPointRva: Int = 0
    private var subsystem: Int = SUBSYSTEM_CONSOLE

    data class NativeExport(val name: String, val rva: Int)

    /** Set the native machine code to embed in the .text section. */
    fun setNativeCode(code: ByteArray): MixedModeAssembler {
        nativeCode = code
        return this
    }

    /** Set the CLR metadata bytes (BSJB format from CilClassBuilder.toBytes()). */
    fun setClrMetadata(metadata: ByteArray): MixedModeAssembler {
        clrMetadataBytes = metadata
        return this
    }

    /** Add a native function export by name and offset within native code. */
    fun addNativeExport(name: String, offsetInNativeCode: Int): MixedModeAssembler {
        nativeExports.add(NativeExport(name, offsetInNativeCode))
        return this
    }

    /** Set the PE subsystem (default: CONSOLE). */
    fun setSubsystem(subsystem: Int): MixedModeAssembler {
        this.subsystem = subsystem
        return this
    }

    /**
     * Assemble the mixed-mode PE file.
     * Returns the complete PE32+ binary with both native and managed sections.
     */
    fun assemble(): ByteArray {
        val buf = ByteArrayOutputStream()
        val hasNative = nativeCode.isNotEmpty()
        val hasClr = clrMetadataBytes.isNotEmpty()

        // Section count: .text (native) + .cil (CLR) + .idata (imports) + .reloc
        var numSections = 1 // .idata always present (mscoree.dll)
        if (hasNative) numSections++
        if (hasClr) numSections++
        numSections++ // .reloc

        // DOS header
        val dosHeader = ByteArray(64)
        dosHeader[0] = 'M'.code.toByte()
        dosHeader[1] = 'Z'.code.toByte()
        putU32(dosHeader, 0x3C, 64) // e_lfanew

        // Headers size
        val headersSize = align(64 + 4 + 20 + 240 + 40 * numSections, FILE_ALIGNMENT)
        var currentRVA = SECTION_ALIGNMENT
        var currentFileOffset = headersSize

        // Layout sections
        var textRVA = 0; var textFileOffset = 0; var textRawSize = 0
        if (hasNative) {
            textRVA = currentRVA
            textFileOffset = currentFileOffset
            textRawSize = align(nativeCode.size, FILE_ALIGNMENT)
            currentRVA += align(nativeCode.size, SECTION_ALIGNMENT)
            currentFileOffset += textRawSize
        }

        var cilRVA = 0; var cilFileOffset = 0; var cilRawSize = 0
        if (hasClr) {
            cilRVA = currentRVA
            cilFileOffset = currentFileOffset
            cilRawSize = align(clrMetadataBytes.size, FILE_ALIGNMENT)
            currentRVA += align(clrMetadataBytes.size, SECTION_ALIGNMENT)
            currentFileOffset += cilRawSize
        }

        val imports = buildMscoreeImportTable()
        val idataRVA = currentRVA
        val idataFileOffset = currentFileOffset
        val idataRawSize = align(imports.size, FILE_ALIGNMENT)
        currentRVA += align(imports.size, SECTION_ALIGNMENT)
        currentFileOffset += idataRawSize

        val relocData = buildRelocSection()
        val relocRVA = currentRVA
        val relocFileOffset = currentFileOffset
        val relocRawSize = align(relocData.size, FILE_ALIGNMENT)
        currentRVA += align(relocData.size, SECTION_ALIGNMENT)

        val imageSize = currentRVA

        // COFF header
        val coffHeader = ByteArray(20)
        putU16(coffHeader, 0, 0x8664) // x86-64
        putU16(coffHeader, 2, numSections)
        putU16(coffHeader, 16, 240) // optional header size
        putU16(coffHeader, 18, 0x22) // EXECUTABLE_IMAGE | LARGE_ADDRESS_AWARE

        // Optional header (PE32+)
        val optHeader = ByteArray(240)
        putU16(optHeader, 0, 0x20B) // PE32+
        optHeader[2] = 14 // linker major
        putU32(optHeader, 4, if (hasNative) nativeCode.size else 0) // code size
        putU32(optHeader, 16, if (hasNative) textRVA else cilRVA) // entry point
        putU32(optHeader, 20, if (hasNative) textRVA else cilRVA) // base of code
        putU64(optHeader, 24, IMAGE_BASE)
        putU32(optHeader, 32, SECTION_ALIGNMENT)
        putU32(optHeader, 36, FILE_ALIGNMENT)
        putU16(optHeader, 40, 6) // OS major
        putU16(optHeader, 44, 6) // subsystem major
        putU32(optHeader, 56, imageSize)
        putU32(optHeader, 60, headersSize)
        putU16(optHeader, 68, subsystem.toShort().toInt())
        putU16(optHeader, 70, 0x8160.toShort().toInt()) // DLL characteristics
        putU64(optHeader, 72, 0x100000) // stack reserve
        putU64(optHeader, 80, 0x1000) // stack commit
        putU64(optHeader, 88, 0x100000) // heap reserve
        putU64(optHeader, 96, 0x1000) // heap commit
        putU32(optHeader, 108, 16) // number of data directories

        // Data directories
        // Import table
        putU32(optHeader, 112 + 8, idataRVA)
        putU32(optHeader, 112 + 12, imports.size)

        // Base relocation
        putU32(optHeader, 112 + 40, relocRVA)
        putU32(optHeader, 112 + 44, relocData.size)

        // CLR runtime header (data directory 14)
        if (hasClr) {
            putU32(optHeader, 112 + 112, cilRVA) // CLR header at start of .cil
            putU32(optHeader, 112 + 116, CLR_HEADER_SIZE)
        }

        // IAT
        putU32(optHeader, 112 + 96, idataRVA + MSCOREE_IAT_OFFSET)
        putU32(optHeader, 112 + 100, 16)

        // Write headers
        buf.write(dosHeader)
        buf.write(byteArrayOf('P'.code.toByte(), 'E'.code.toByte(), 0, 0))
        buf.write(coffHeader)
        buf.write(optHeader)

        // Section headers
        if (hasNative) writeSectionHeader(buf, ".text\u0000\u0000\u0000", nativeCode.size, textRVA, textRawSize, textFileOffset, 0x60000020)
        if (hasClr) writeSectionHeader(buf, ".cil\u0000\u0000\u0000\u0000", clrMetadataBytes.size + CLR_HEADER_SIZE, cilRVA, cilRawSize, cilFileOffset, 0x40000040)
        writeSectionHeader(buf, ".idata\u0000\u0000", imports.size, idataRVA, idataRawSize, idataFileOffset, 0xC0000040.toInt())
        writeSectionHeader(buf, ".reloc\u0000\u0000", relocData.size, relocRVA, relocRawSize, relocFileOffset, 0x42000040)

        // Pad to end of headers
        padTo(buf, headersSize)

        // .text section
        if (hasNative) {
            buf.write(nativeCode)
            padTo(buf, textFileOffset + textRawSize)
        }

        // .cil section: CLR header + metadata
        if (hasClr) {
            val clrHeader = buildClrHeader(cilRVA, clrMetadataBytes.size)
            buf.write(clrHeader)
            buf.write(clrMetadataBytes)
            padTo(buf, cilFileOffset + cilRawSize)
        }

        // .idata section
        buf.write(fixupMscoreeImports(imports, idataRVA))
        padTo(buf, idataFileOffset + idataRawSize)

        // .reloc section
        buf.write(relocData)
        padTo(buf, relocFileOffset + relocRawSize)

        return buf.toByteArray()
    }

    /** Metadata about the assembled PE. */
    data class AssemblyLayout(
        val nativeCodeRVA: Int,
        val clrMetadataRVA: Int,
        val imageBase: Long,
        val hasNativeCode: Boolean,
        val hasClrMetadata: Boolean,
        val nativeExportCount: Int,
    )

    /** Get the layout of the assembly without building it. */
    fun layout(): AssemblyLayout {
        var currentRVA = SECTION_ALIGNMENT
        val nRVA = if (nativeCode.isNotEmpty()) { val r = currentRVA; currentRVA += align(nativeCode.size, SECTION_ALIGNMENT); r } else 0
        val cRVA = if (clrMetadataBytes.isNotEmpty()) { val r = currentRVA; currentRVA += align(clrMetadataBytes.size, SECTION_ALIGNMENT); r } else 0
        return AssemblyLayout(nRVA, cRVA, IMAGE_BASE, nativeCode.isNotEmpty(), clrMetadataBytes.isNotEmpty(), nativeExports.size)
    }

    // --- CLR Header ---

    private fun buildClrHeader(cilSectionRVA: Int, metadataSize: Int): ByteArray {
        val header = ByteArray(CLR_HEADER_SIZE)
        putU32(header, 0, CLR_HEADER_SIZE) // cb
        putU16(header, 4, 2) // MajorRuntimeVersion
        putU16(header, 6, 5) // MinorRuntimeVersion
        // MetaData RVA/Size — metadata starts after CLR header
        putU32(header, 8, cilSectionRVA + CLR_HEADER_SIZE)
        putU32(header, 12, metadataSize)
        // Flags: 0 = not IL-only (mixed mode)
        putU32(header, 16, 0)
        return header
    }

    // --- Import table for mscoree.dll ---

    private fun buildMscoreeImportTable(): ByteArray {
        val buf = ByteArrayOutputStream()
        buf.write(ByteArray(40)) // IDT: 1 entry + null
        buf.write(ByteArray(16)) // ILT: 1 + null
        buf.write(ByteArray(16)) // IAT: 1 + null
        // Hint/Name for _CorExeMain
        buf.write(ByteArray(2)) // hint
        buf.write("_CorExeMain".toByteArray(Charsets.US_ASCII))
        buf.write(0)
        // DLL name
        buf.write("mscoree.dll\u0000".toByteArray(Charsets.US_ASCII))
        return buf.toByteArray()
    }

    private fun fixupMscoreeImports(template: ByteArray, idataRVA: Int): ByteArray {
        val data = template.copyOf()
        val hintNameOffset = 72 // after IDT(40) + ILT(16) + IAT(16)
        val dllNameOffset = hintNameOffset + 2 + "_CorExeMain".length + 1

        // IDT entry
        putU32(data, 0, idataRVA + 40) // ILT RVA
        putU32(data, 12, idataRVA + dllNameOffset) // DLL name RVA
        putU32(data, 16, idataRVA + MSCOREE_IAT_OFFSET) // IAT RVA

        // ILT entry
        putU64(data, 40, (idataRVA + hintNameOffset).toLong())
        // IAT entry
        putU64(data, MSCOREE_IAT_OFFSET, (idataRVA + hintNameOffset).toLong())

        return data
    }

    private fun buildRelocSection(): ByteArray {
        // Minimal base relocation table (8-byte empty block)
        val buf = ByteArray(12)
        putU32(buf, 0, SECTION_ALIGNMENT) // page RVA
        putU32(buf, 4, 12) // block size
        return buf
    }

    // --- Helpers ---

    private fun writeSectionHeader(buf: ByteArrayOutputStream, name: String, virtualSize: Int, rva: Int, rawSize: Int, rawOffset: Int, characteristics: Int) {
        val header = ByteArray(40)
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        nameBytes.copyInto(header, 0, 0, minOf(nameBytes.size, 8))
        putU32(header, 8, virtualSize)
        putU32(header, 12, rva)
        putU32(header, 16, rawSize)
        putU32(header, 20, rawOffset)
        putU32(header, 36, characteristics)
        buf.write(header)
    }

    private fun padTo(buf: ByteArrayOutputStream, target: Int) {
        val current = buf.size()
        if (current < target) buf.write(ByteArray(target - current))
    }

    companion object {
        private const val FILE_ALIGNMENT = 0x200
        private const val SECTION_ALIGNMENT = 0x1000
        private const val IMAGE_BASE = 0x140000000L
        private const val SUBSYSTEM_CONSOLE = 3
        private const val SUBSYSTEM_GUI = 2
        private const val CLR_HEADER_SIZE = 72
        private const val MSCOREE_IAT_OFFSET = 56 // after IDT(40) + ILT(16)

        @JvmStatic fun fileAlignment(): Int = FILE_ALIGNMENT
        @JvmStatic fun sectionAlignment(): Int = SECTION_ALIGNMENT
        @JvmStatic fun imageBase(): Long = IMAGE_BASE

        private fun align(value: Int, alignment: Int): Int =
            (value + alignment - 1) and (alignment - 1).inv()

        private fun putU16(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = (value and 0xFF).toByte()
            buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        private fun putU32(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = (value and 0xFF).toByte()
            buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
            buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
            buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        private fun putU64(buf: ByteArray, offset: Int, value: Long) {
            putU32(buf, offset, value.toInt())
            putU32(buf, offset + 4, (value shr 32).toInt())
        }
    }
}
