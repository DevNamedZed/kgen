package org.kgen.binary.elf

import org.kgen.binary.*
import java.io.ByteArrayOutputStream

/**
 * Minimal ELF64 static executable writer.
 * Produces a flat executable with a single LOAD segment — no sections, no dynamic linking.
 *
 * For full-featured ELF output with relocations, symbols, and sections, use
 * the ObjectFileWriter interface (coming in the object-formats milestone).
 */
object ElfWriter : ObjectFileWriter {

    override val format: ObjectFormat get() = ObjectFormat.ELF

    override fun supportsArchitecture(arch: ArchType): Boolean = arch in setOf(
        ArchType.X86_64, ArchType.AARCH64, ArchType.RISCV64, ArchType.RISCV32,
        ArchType.ARM, ArchType.X86, ArchType.MIPS, ArchType.MIPS64,
        ArchType.POWERPC, ArchType.POWERPC64, ArchType.S390X, ArchType.LOONGARCH64,
    )

    override fun write(obj: ObjectFile): ByteArray {
        val textSection = obj.sections.firstOrNull { it.kind == SectionKind.TEXT }
            ?: throw IllegalArgumentException("ObjectFile must have a TEXT section")
        val dataSection = obj.sections.firstOrNull { it.kind == SectionKind.DATA || it.kind == SectionKind.RODATA }
        val machine = when (obj.arch.arch) {
            ArchType.X86_64 -> 0x3E
            ArchType.AARCH64 -> 0xB7
            ArchType.RISCV32, ArchType.RISCV64 -> 0xF3
            ArchType.ARM -> 0x28
            ArchType.X86 -> 0x03
            else -> throw IllegalArgumentException("Unsupported ELF architecture: ${obj.arch.arch}")
        }
        return writeFlat(textSection.data, dataSection?.data ?: byteArrayOf(), machine)
    }

    private const val EHDR_SIZE = 64
    private const val PHDR_SIZE = 56
    private const val BASE_ADDR = 0x400000L

    /**
     * Write a flat ELF64 executable from raw code and data bytes.
     * This is the simplest path: headers + code + data, all in a single LOAD segment.
     */
    @JvmStatic
    fun writeFlat(code: ByteArray, data: ByteArray = byteArrayOf(), machine: Int = 0x3E): ByteArray {
        val buf = ByteArrayOutputStream()
        val numPhdrs = if (data.isEmpty()) 1 else 2
        val headerSize = EHDR_SIZE + PHDR_SIZE * numPhdrs
        val codeVaddr = BASE_ADDR + headerSize
        val dataVaddr = BASE_ADDR + headerSize + code.size

        // ELF header
        buf.write(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
        buf.write(2) // ELFCLASS64
        buf.write(1) // ELFDATA2LSB
        buf.write(1) // EV_CURRENT
        buf.write(0) // ELFOSABI_NONE
        buf.write(ByteArray(8))
        writeU16(buf, 2)        // ET_EXEC
        writeU16(buf, machine)  // e_machine
        writeU32(buf, 1)        // EV_CURRENT
        writeU64(buf, codeVaddr) // e_entry
        writeU64(buf, EHDR_SIZE.toLong()) // e_phoff
        writeU64(buf, 0)        // e_shoff
        writeU32(buf, 0)        // e_flags
        writeU16(buf, EHDR_SIZE)
        writeU16(buf, PHDR_SIZE)
        writeU16(buf, numPhdrs)
        writeU16(buf, 0)
        writeU16(buf, 0)
        writeU16(buf, 0)

        // LOAD segment: everything (RX)
        val loadSize = headerSize + code.size + data.size
        writeU32(buf, 1); writeU32(buf, 5) // PT_LOAD, PF_R|PF_X
        writeU64(buf, 0); writeU64(buf, BASE_ADDR); writeU64(buf, BASE_ADDR)
        writeU64(buf, loadSize.toLong()); writeU64(buf, loadSize.toLong())
        writeU64(buf, 0x1000)

        if (data.isNotEmpty()) {
            writeU32(buf, 1); writeU32(buf, 6) // PT_LOAD, PF_R|PF_W
            writeU64(buf, (headerSize + code.size).toLong())
            writeU64(buf, dataVaddr); writeU64(buf, dataVaddr)
            writeU64(buf, data.size.toLong()); writeU64(buf, data.size.toLong())
            writeU64(buf, 0x1000)
        }

        buf.write(code)
        if (data.isNotEmpty()) buf.write(data)
        return buf.toByteArray()
    }

    /** Virtual address where code starts for a flat executable. */
    @JvmStatic
    fun codeVaddr(dataSize: Int = 0): Long {
        val numPhdrs = if (dataSize == 0) 1 else 2
        return BASE_ADDR + EHDR_SIZE + PHDR_SIZE * numPhdrs
    }

    /** Virtual address where data starts for a flat executable. */
    @JvmStatic
    fun dataVaddr(codeSize: Int, dataSize: Int): Long {
        val numPhdrs = if (dataSize == 0) 1 else 2
        return BASE_ADDR + EHDR_SIZE + PHDR_SIZE * numPhdrs + codeSize
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
}
