package org.kgen.binary.elf

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfBigEndianTest {

    private fun buildBigEndianElf32(
        machine: Int = 0x0008, // MIPS
        type: Int = 0x0002,   // ET_EXEC
        entryPoint: Int = 0x00400000,
        sectionData: ByteArray = byteArrayOf(),
        sectionName: String = ".text",
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos) // DataOutputStream is big-endian by default

        // Build string table: "\0" + sectionName + "\0" + ".shstrtab\0"
        val shstrtab = ByteArrayOutputStream()
        shstrtab.write(0) // null byte at index 0
        val sectionNameIdx = shstrtab.size()
        shstrtab.write(sectionName.toByteArray())
        shstrtab.write(0)
        val shstrtabNameIdx = shstrtab.size()
        shstrtab.write(".shstrtab".toByteArray())
        shstrtab.write(0)
        val shstrtabData = shstrtab.toByteArray()

        // Pad section data to 4-byte alignment
        val paddedSectionData = sectionData + ByteArray((4 - sectionData.size % 4) % 4)
        val paddedShstrtab = shstrtabData + ByteArray((4 - shstrtabData.size % 4) % 4)

        // Offsets
        val ehdrSize = 52 // ELF32 header
        val sectionDataOff = ehdrSize
        val shstrtabOff = sectionDataOff + paddedSectionData.size
        val shdrOff = shstrtabOff + paddedShstrtab.size
        val shdrCount = 3 // NULL + .text + .shstrtab
        val shdrEntSize = 40

        // ELF header (big-endian)
        dos.write(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())) // e_ident magic
        dos.writeByte(1) // EI_CLASS = ELFCLASS32
        dos.writeByte(2) // EI_DATA = ELFDATA2MSB (big-endian)
        dos.writeByte(1) // EI_VERSION
        dos.writeByte(0) // EI_OSABI
        dos.write(ByteArray(8)) // EI_ABIVERSION + padding

        dos.writeShort(type) // e_type
        dos.writeShort(machine) // e_machine
        dos.writeInt(1) // e_version
        dos.writeInt(entryPoint) // e_entry
        dos.writeInt(0) // e_phoff
        dos.writeInt(shdrOff) // e_shoff
        dos.writeInt(0) // e_flags
        dos.writeShort(ehdrSize) // e_ehsize
        dos.writeShort(0) // e_phentsize
        dos.writeShort(0) // e_phnum
        dos.writeShort(shdrEntSize) // e_shentsize
        dos.writeShort(shdrCount) // e_shnum
        dos.writeShort(2) // e_shstrndx (index of .shstrtab section)

        // Section data
        dos.write(paddedSectionData)

        // .shstrtab data
        dos.write(paddedShstrtab)

        // Section headers
        // [0] NULL section
        dos.writeInt(0) // sh_name
        dos.writeInt(0) // sh_type = SHT_NULL
        dos.writeInt(0) // sh_flags
        dos.writeInt(0) // sh_addr
        dos.writeInt(0) // sh_offset
        dos.writeInt(0) // sh_size
        dos.writeInt(0) // sh_link
        dos.writeInt(0) // sh_info
        dos.writeInt(0) // sh_addralign
        dos.writeInt(0) // sh_entsize

        // [1] .text section
        dos.writeInt(sectionNameIdx) // sh_name
        dos.writeInt(1) // sh_type = SHT_PROGBITS
        dos.writeInt(6) // sh_flags = SHF_ALLOC | SHF_EXECINSTR
        dos.writeInt(entryPoint) // sh_addr
        dos.writeInt(sectionDataOff) // sh_offset
        dos.writeInt(sectionData.size) // sh_size
        dos.writeInt(0) // sh_link
        dos.writeInt(0) // sh_info
        dos.writeInt(4) // sh_addralign
        dos.writeInt(0) // sh_entsize

        // [2] .shstrtab section
        dos.writeInt(shstrtabNameIdx) // sh_name
        dos.writeInt(3) // sh_type = SHT_STRTAB
        dos.writeInt(0) // sh_flags
        dos.writeInt(0) // sh_addr
        dos.writeInt(shstrtabOff) // sh_offset
        dos.writeInt(shstrtabData.size) // sh_size
        dos.writeInt(0) // sh_link
        dos.writeInt(0) // sh_info
        dos.writeInt(1) // sh_addralign
        dos.writeInt(0) // sh_entsize

        dos.flush()
        return baos.toByteArray()
    }

    private fun buildBigEndianElf64(
        machine: Int = 0x0015, // SPARC64
        type: Int = 0x0002,   // ET_EXEC
        entryPoint: Long = 0x0000000000400000L,
        sectionData: ByteArray = byteArrayOf(),
        sectionName: String = ".text",
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        val shstrtab = ByteArrayOutputStream()
        shstrtab.write(0)
        val sectionNameIdx = shstrtab.size()
        shstrtab.write(sectionName.toByteArray())
        shstrtab.write(0)
        val shstrtabNameIdx = shstrtab.size()
        shstrtab.write(".shstrtab".toByteArray())
        shstrtab.write(0)
        val shstrtabData = shstrtab.toByteArray()

        val paddedSectionData = sectionData + ByteArray((8 - sectionData.size % 8) % 8)
        val paddedShstrtab = shstrtabData + ByteArray((8 - shstrtabData.size % 8) % 8)

        val ehdrSize = 64 // ELF64 header
        val sectionDataOff = ehdrSize
        val shstrtabOff = sectionDataOff + paddedSectionData.size
        val shdrOff = shstrtabOff + paddedShstrtab.size
        val shdrCount = 3
        val shdrEntSize = 64

        // ELF header
        dos.write(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
        dos.writeByte(2) // ELFCLASS64
        dos.writeByte(2) // ELFDATA2MSB
        dos.writeByte(1)
        dos.writeByte(0)
        dos.write(ByteArray(8))

        dos.writeShort(type)
        dos.writeShort(machine)
        dos.writeInt(1) // e_version
        dos.writeLong(entryPoint) // e_entry
        dos.writeLong(0) // e_phoff
        dos.writeLong(shdrOff.toLong()) // e_shoff
        dos.writeInt(0) // e_flags
        dos.writeShort(ehdrSize) // e_ehsize
        dos.writeShort(0) // e_phentsize
        dos.writeShort(0) // e_phnum
        dos.writeShort(shdrEntSize) // e_shentsize
        dos.writeShort(shdrCount) // e_shnum
        dos.writeShort(2) // e_shstrndx

        // Section data
        dos.write(paddedSectionData)
        dos.write(paddedShstrtab)

        // Section headers (64-bit)
        // [0] NULL
        dos.writeInt(0); dos.writeInt(0); dos.writeLong(0); dos.writeLong(0)
        dos.writeLong(0); dos.writeLong(0); dos.writeInt(0); dos.writeInt(0)
        dos.writeLong(0); dos.writeLong(0)

        // [1] .text
        dos.writeInt(sectionNameIdx); dos.writeInt(1) // name, type=PROGBITS
        dos.writeLong(6) // flags
        dos.writeLong(entryPoint) // addr
        dos.writeLong(sectionDataOff.toLong()) // offset
        dos.writeLong(sectionData.size.toLong()) // size
        dos.writeInt(0); dos.writeInt(0) // link, info
        dos.writeLong(8); dos.writeLong(0) // align, entsize

        // [2] .shstrtab
        dos.writeInt(shstrtabNameIdx); dos.writeInt(3) // name, type=STRTAB
        dos.writeLong(0) // flags
        dos.writeLong(0) // addr
        dos.writeLong(shstrtabOff.toLong()) // offset
        dos.writeLong(shstrtabData.size.toLong()) // size
        dos.writeInt(0); dos.writeInt(0)
        dos.writeLong(1); dos.writeLong(0)

        dos.flush()
        return baos.toByteArray()
    }

    @Test
    fun `reads big-endian ELF32 header`() {
        val bytes = buildBigEndianElf32(sectionData = byteArrayOf(0x00, 0x01, 0x02, 0x03))
        val elf = ElfReader.read(bytes)

        assertEquals(ElfClass.ELF32, elf.header.elfClass)
        assertEquals(ElfData.MSB, elf.header.dataEncoding)
        assertEquals(0x0008, elf.header.machine?.code ?: 0) // MIPS
        assertEquals(0x00400000L, elf.header.entryPoint)
    }

    @Test
    fun `reads big-endian ELF32 sections`() {
        val code = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val bytes = buildBigEndianElf32(sectionData = code)
        val elf = ElfReader.read(bytes)

        assertEquals(3, elf.sections.size)
        val text = elf.sections.first { it.name == ".text" }
        assertEquals(4, text.size)
        assertArrayEquals(code, text.data?.copyOf(4))
    }

    @Test
    fun `reads big-endian ELF32 section names`() {
        val bytes = buildBigEndianElf32(sectionData = byteArrayOf(0xCC.toByte()), sectionName = ".data")
        val elf = ElfReader.read(bytes)

        val names = elf.sections.map { it.name }
        assertTrue(".data" in names, "Should find .data section: $names")
        assertTrue(".shstrtab" in names, "Should find .shstrtab section: $names")
    }

    @Test
    fun `reads big-endian ELF64 header`() {
        val bytes = buildBigEndianElf64(sectionData = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val elf = ElfReader.read(bytes)

        assertEquals(ElfClass.ELF64, elf.header.elfClass)
        assertEquals(ElfData.MSB, elf.header.dataEncoding)
        assertEquals(0x0000000000400000L, elf.header.entryPoint)
    }

    @Test
    fun `reads big-endian ELF64 sections`() {
        val code = ByteArray(16) { it.toByte() }
        val bytes = buildBigEndianElf64(sectionData = code)
        val elf = ElfReader.read(bytes)

        assertEquals(3, elf.sections.size)
        val text = elf.sections.first { it.name == ".text" }
        assertEquals(16, text.size)
    }

    @Test
    fun `canRead accepts big-endian ELF`() {
        val bytes = buildBigEndianElf32(sectionData = byteArrayOf(0))
        assertTrue(ElfReader.canRead(bytes))
    }

    @Test
    fun `little-endian ELF still works`() {
        // Verify existing LE support isn't broken by reading a LE ELF
        // Use the object writer to produce one
        val objectWriter = ElfObjectWriter()
        val objBytes = objectWriter.write(org.kgen.binary.ObjectFile(
            format = org.kgen.binary.ObjectFormat.ELF,
            arch = org.kgen.binary.Architecture(org.kgen.binary.ArchType.X86_64),
            sections = listOf(org.kgen.binary.Section(
                name = ".text",
                data = byteArrayOf(0xCC.toByte()),
                kind = org.kgen.binary.SectionKind.TEXT,
            )),
            symbols = emptyList(),
            relocations = emptyList(),
            imports = emptyList(),
        ))
        val elf = ElfReader.read(objBytes)

        assertEquals(ElfData.LSB, elf.header.dataEncoding)
        assertTrue(elf.sections.any { it.name == ".text" })
    }
}
