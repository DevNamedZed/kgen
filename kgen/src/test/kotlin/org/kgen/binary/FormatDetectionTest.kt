package org.kgen.binary

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.elf.ElfObjectWriter
import org.kgen.binary.pe.PeWriter
import org.kgen.binary.pe.CoffObjectWriter
import org.kgen.binary.macho.MachOObjectWriter

class FormatDetectionTest {

    @Test
    fun `detects ELF from magic bytes`() {
        val bytes = byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 0, 0)
        assertEquals(ObjectFormat.ELF, detectFormat(bytes))
    }

    @Test
    fun `detects PE from MZ header`() {
        val bytes = byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0, 0, 0, 0)
        assertEquals(ObjectFormat.PE_COFF, detectFormat(bytes))
    }

    @Test
    fun `detects Mach-O 64 little-endian`() {
        // MH_MAGIC_64 = 0xFEEDFACF stored little-endian
        val bytes = byteArrayOf(0xCF.toByte(), 0xFA.toByte(), 0xED.toByte(), 0xFE.toByte())
        assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
    }

    @Test
    fun `detects Mach-O 64 big-endian`() {
        // MH_CIGAM_64 = 0xCFFAEDFE stored as big-endian
        val bytes = byteArrayOf(0xFE.toByte(), 0xED.toByte(), 0xFA.toByte(), 0xCF.toByte())
        assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
    }

    @Test
    fun `detects Mach-O 32 little-endian`() {
        val bytes = byteArrayOf(0xCE.toByte(), 0xFA.toByte(), 0xED.toByte(), 0xFE.toByte())
        assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
    }

    @Test
    fun `detects Mach-O 32 big-endian`() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xED.toByte(), 0xFA.toByte(), 0xCE.toByte())
        assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
    }

    @Test
    fun `detects WASM from magic bytes`() {
        val bytes = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
        assertEquals(ObjectFormat.WASM_MODULE, detectFormat(bytes))
    }

    @Test
    fun `detects JVM class from CAFEBABE`() {
        val bytes = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        assertEquals(ObjectFormat.JVM_CLASS, detectFormat(bytes))
    }

    @Test
    fun `detects raw COFF from AMD64 machine type`() {
        // 0x8664 in little-endian
        val bytes = byteArrayOf(0x64, 0x86.toByte(), 0x00, 0x00, 0x00, 0x00)
        assertEquals(ObjectFormat.PE_COFF, detectFormat(bytes))
    }

    @Test
    fun `detects raw COFF from ARM64 machine type`() {
        // 0xAA64 in little-endian
        val bytes = byteArrayOf(0x64, 0xAA.toByte(), 0x00, 0x00)
        assertEquals(ObjectFormat.PE_COFF, detectFormat(bytes))
    }

    @Test
    fun `detects raw COFF from i386 machine type`() {
        // 0x014c in little-endian
        val bytes = byteArrayOf(0x4c, 0x01, 0x00, 0x00)
        assertEquals(ObjectFormat.PE_COFF, detectFormat(bytes))
    }

    @Test
    fun `returns null for empty bytes`() {
        assertNull(detectFormat(ByteArray(0)))
    }

    @Test
    fun `returns null for too short bytes`() {
        assertNull(detectFormat(byteArrayOf(0x00)))
        assertNull(detectFormat(byteArrayOf(0x00, 0x00)))
        assertNull(detectFormat(byteArrayOf(0x00, 0x00, 0x00)))
    }

    @Test
    fun `returns null for random bytes`() {
        assertNull(detectFormat(byteArrayOf(0x12, 0x34, 0x56, 0x78.toByte())))
    }

    @Test
    fun `detects format from ElfObjectWriter output`() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = emptyList(), relocations = emptyList(),
        )
        val bytes = ElfObjectWriter().write(obj)
        assertEquals(ObjectFormat.ELF, detectFormat(bytes))
    }

    @Test
    fun `detects format from PeWriter output`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        assertEquals(ObjectFormat.PE_COFF, detectFormat(bytes))
    }

    @Test
    fun `detects format from CoffObjectWriter output`() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture.X86_64_WINDOWS,
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = emptyList(), relocations = emptyList(),
        )
        val bytes = CoffObjectWriter().write(obj)
        assertEquals(ObjectFormat.PE_COFF, detectFormat(bytes))
    }

    @Test
    fun `detects format from MachOObjectWriter output`() {
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = emptyList(), relocations = emptyList(),
        )
        val bytes = MachOObjectWriter().write(obj)
        assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
    }

    @Test
    fun `all formats are mutually exclusive on their magic bytes`() {
        // Verify no ambiguity between format magic bytes
        val elfMagic = byteArrayOf(0x7f, 0x45, 0x4c, 0x46)
        val peMagic = byteArrayOf(0x4d, 0x5a, 0x00, 0x00)
        val wasmMagic = byteArrayOf(0x00, 0x61, 0x73, 0x6d)
        val jvmMagic = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())

        val formats = listOf(elfMagic, peMagic, wasmMagic, jvmMagic)
        val detected = formats.map { detectFormat(it) }
        assertEquals(detected.toSet().size, detected.size, "All formats should detect as distinct")
    }
}
