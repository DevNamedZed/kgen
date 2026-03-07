package org.kgen.binary.pe

import org.kgen.binary.*
import java.io.ByteArrayOutputStream

/**
 * Minimal PE32+ (64-bit) executable writer.
 * Produces a console executable with .text, optional .rdata, and .idata sections.
 * Imports kernel32.dll: GetStdHandle, WriteFile, ExitProcess.
 *
 * For full-featured PE/COFF output, use the ObjectFileWriter interface
 * (coming in the object-formats milestone).
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
        return writeFlat(textSection.data, rodataSection?.data ?: byteArrayOf())
    }

    private const val FILE_ALIGNMENT = 0x200
    private const val SECTION_ALIGNMENT = 0x1000
    private const val IMAGE_BASE = 0x140000000L

    /**
     * Write a minimal PE32+ executable from raw code and optional rodata bytes.
     * Automatically generates import tables for kernel32.dll.
     */
    @JvmStatic
    fun writeFlat(code: ByteArray, rodata: ByteArray = byteArrayOf()): ByteArray {
        val buf = ByteArrayOutputStream()
        val imports = buildImportTable()
        val hasRodata = rodata.isNotEmpty()
        val numSections = 2 + (if (hasRodata) 1 else 0)

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
        if (hasRodata) {
            rdataRVA = textRVA + align(code.size, SECTION_ALIGNMENT)
            rdataFileOffset = textFileOffset + textRawSize
            rdataRawSize = align(rodata.size, FILE_ALIGNMENT)
        }

        val idataRVA = if (hasRodata) rdataRVA + align(rodata.size, SECTION_ALIGNMENT)
                       else textRVA + align(code.size, SECTION_ALIGNMENT)
        val idataFileOffset = if (hasRodata) rdataFileOffset + rdataRawSize
                              else textFileOffset + textRawSize
        val idataRawSize = align(imports.size, FILE_ALIGNMENT)
        val imageSize = align(idataRVA + imports.size, SECTION_ALIGNMENT)

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
        putU32(optHeader, 112 + 8, idataRVA)
        putU32(optHeader, 112 + 12, imports.size)
        putU32(optHeader, 112 + 96, idataRVA + IAT_OFFSET)
        putU32(optHeader, 112 + 100, 32) // 3 entries + null

        buf.write(dosHeader)
        buf.write(byteArrayOf('P'.code.toByte(), 'E'.code.toByte(), 0, 0))
        buf.write(coffHeader)
        buf.write(optHeader)

        writeSectionHeader(buf, ".text\u0000\u0000\u0000", code.size, textRVA, textRawSize, textFileOffset, 0x60000020)
        if (hasRodata) writeSectionHeader(buf, ".rdata\u0000\u0000", rodata.size, rdataRVA, rdataRawSize, rdataFileOffset, 0x40000040)
        writeSectionHeader(buf, ".idata\u0000\u0000", imports.size, idataRVA, idataRawSize, idataFileOffset, 0xC0000040.toInt())

        padTo(buf, headersSize)
        buf.write(code); padTo(buf, textFileOffset + textRawSize)
        if (hasRodata) { buf.write(rodata); padTo(buf, rdataFileOffset + rdataRawSize) }
        buf.write(fixupImportTable(imports, idataRVA)); padTo(buf, idataFileOffset + idataRawSize)

        return buf.toByteArray()
    }

    private const val IAT_OFFSET = 72

    /**
     * Returns the IAT entry RVAs for [GetStdHandle, WriteFile, ExitProcess].
     * Use these to compute RIP-relative call targets in generated code.
     */
    @JvmStatic
    fun iatEntryRVAs(codeSize: Int, rodataSize: Int = 0): LongArray {
        val idataRVA = SECTION_ALIGNMENT + align(codeSize, SECTION_ALIGNMENT) +
            (if (rodataSize > 0) align(rodataSize, SECTION_ALIGNMENT) else 0)
        val iatBase = idataRVA + IAT_OFFSET
        return longArrayOf(
            IMAGE_BASE + iatBase,
            IMAGE_BASE + iatBase + 8,
            IMAGE_BASE + iatBase + 16,
        )
    }

    /** Section alignment constant, useful for computing RVAs externally. */
    @JvmStatic fun sectionAlignment(): Int = SECTION_ALIGNMENT
    @JvmStatic fun imageBase(): Long = IMAGE_BASE

    private val IMPORT_FUNCTIONS = listOf("GetStdHandle", "WriteFile", "ExitProcess")
    private const val DLL_NAME = "kernel32.dll\u0000"

    private fun buildImportTable(): ByteArray {
        val buf = ByteArrayOutputStream()
        buf.write(ByteArray(40)) // IDT: 1 entry + null
        buf.write(ByteArray(32)) // ILT: 3 + null
        buf.write(ByteArray(32)) // IAT: 3 + null
        for (func in IMPORT_FUNCTIONS) {
            buf.write(ByteArray(2)) // hint
            buf.write(func.toByteArray(Charsets.US_ASCII))
            buf.write(0)
            if (buf.size() % 2 != 0) buf.write(0)
        }
        buf.write(DLL_NAME.toByteArray(Charsets.US_ASCII))
        return buf.toByteArray()
    }

    private fun fixupImportTable(template: ByteArray, idataRVA: Int): ByteArray {
        val data = template.copyOf()
        var off = 104
        val hintNameOffsets = mutableListOf<Int>()
        for (func in IMPORT_FUNCTIONS) {
            hintNameOffsets.add(off)
            off += 2 + func.length + 1
            if (off % 2 != 0) off++
        }
        putU32(data, 0, idataRVA + 40)
        putU32(data, 12, idataRVA + off)
        putU32(data, 16, idataRVA + IAT_OFFSET)
        for (i in IMPORT_FUNCTIONS.indices) {
            putU64(data, 40 + i * 8, (idataRVA + hintNameOffsets[i]).toLong())
            putU64(data, IAT_OFFSET + i * 8, (idataRVA + hintNameOffsets[i]).toLong())
        }
        return data
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
