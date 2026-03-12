package org.kgen.binary

import org.kgen.binary.elf.*
import org.kgen.binary.pe.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DebugPassthroughTest {

    private fun makeObjWithDebug(format: ObjectFormat = ObjectFormat.ELF): ObjectFile {
        val code = byteArrayOf(0xC3.toByte()) // ret
        val debugInfo = ByteArray(64) { (it + 0x10).toByte() }
        val debugAbbrev = ByteArray(32) { (it + 0x20).toByte() }
        val debugLine = ByteArray(48) { (it + 0x30).toByte() }

        return ObjectFile(
            format = format,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".debug_info", SectionKind.DEBUG_INFO, debugInfo, align = 1),
                Section(".debug_abbrev", SectionKind.DEBUG_ABBREV, debugAbbrev, align = 1),
                Section(".debug_line", SectionKind.DEBUG_LINE, debugLine, align = 1),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
    }

    // --- ELF Object Writer ---

    @Test
    fun `elf object writer preserves debug sections`() {
        val obj = makeObjWithDebug()
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)

        assertNotNull(elf.sectionByName(".debug_info"), ".debug_info should exist in ELF object")
        assertNotNull(elf.sectionByName(".debug_abbrev"), ".debug_abbrev should exist")
        assertNotNull(elf.sectionByName(".debug_line"), ".debug_line should exist")

        // Verify data round-trips
        val debugInfo = elf.sectionByName(".debug_info")!!
        assertEquals(64, debugInfo.size)
    }

    // --- COFF Object Writer ---

    @Test
    fun `coff object writer preserves debug sections`() {
        val obj = makeObjWithDebug(ObjectFormat.PE_COFF)
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertNotNull(pe.sectionByName(".debug_info"), ".debug_info should exist in COFF object")
        assertNotNull(pe.sectionByName(".debug_abbrev"), ".debug_abbrev should exist")
        assertNotNull(pe.sectionByName(".debug_line"), ".debug_line should exist")
    }

    // --- ELF Static Linker ---

    @Test
    fun `elf static linker passes through debug sections`() {
        val obj = makeObjWithDebug()
        val binary = ElfStaticLinker().link(listOf(obj))
        val elf = ElfReader.read(binary)

        assertNotNull(elf.sectionByName(".debug_info"), ".debug_info should survive static linking")
        assertNotNull(elf.sectionByName(".debug_abbrev"))
        assertNotNull(elf.sectionByName(".debug_line"))

        val debugInfo = elf.sectionByName(".debug_info")!!
        assertEquals(64, debugInfo.size, "Debug section size should be preserved")
    }

    @Test
    fun `elf static linker without debug produces no section headers`() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
            symbols = listOf(Symbol("_start", value = 0, size = 1, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            relocations = emptyList(),
        )
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN)
        val shoff = buf.getLong(40) // e_shoff
        assertEquals(0L, shoff, "No section headers without debug sections")
    }

    // --- ELF Dynamic Linker ---

    @Test
    fun `elf dynamic linker passes through debug sections`() {
        val obj = makeObjWithDebug()
        val binary = ElfLinker(sharedLibs = emptyList()).link(listOf(obj))
        val elf = ElfReader.read(binary)

        assertNotNull(elf.sectionByName(".debug_info"), ".debug_info should survive dynamic linking")
        assertNotNull(elf.sectionByName(".debug_abbrev"))
        assertNotNull(elf.sectionByName(".debug_line"))
    }

    // --- ELF Shared Linker ---

    @Test
    fun `elf shared linker passes through debug sections`() {
        val code = byteArrayOf(
            0x89.toByte(), 0xF8.toByte(), // mov eax, edi
            0xC3.toByte(),                 // ret
        )
        val debugInfo = ByteArray(32) { 0x42 }
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".debug_info", SectionKind.DEBUG_INFO, debugInfo, align = 1),
            ),
            symbols = listOf(
                Symbol("myFunc", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val binary = ElfSharedLinker(soname = "libtest.so").link(listOf(obj))
        val elf = ElfReader.read(binary)
        assertNotNull(elf.sectionByName(".debug_info"), ".debug_info should survive shared linking")
    }

    // --- PE Linker ---

    @Test
    fun `pe linker passes through debug sections`() {
        val code = byteArrayOf(
            0x48, 0x83.toByte(), 0xEC.toByte(), 0x28, // sub rsp, 28h
            0x31, 0xC9.toByte(),                        // xor ecx, ecx
            0xC3.toByte(),                              // ret
        )
        val debugInfo = ByteArray(64) { (it xor 0x55).toByte() }
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".debug_info", SectionKind.DEBUG_INFO, debugInfo, align = 1),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val binary = PeLinker().link(listOf(obj))
        val pe = PeReader.read(binary)
        val dbgSection = pe.sectionByName(".debug_in")
            ?: pe.sectionByName(".debug_i")
            ?: pe.sections.firstOrNull { it.name.startsWith(".debug") }

        assertNotNull(dbgSection, "Debug section should exist in linked PE")
    }

    @Test
    fun `pe dll linker passes through debug sections`() {
        val code = byteArrayOf(0xC3.toByte()) // ret
        val debugStr = ByteArray(16) { 0x41 }
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".debug_str", SectionKind.DEBUG_STR, debugStr, align = 1),
            ),
            symbols = listOf(
                Symbol("myExport", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val binary = PeDllLinker().link(listOf(obj))
        val pe = PeReader.read(binary)
        val dbgSection = pe.sections.firstOrNull { it.name.startsWith(".debug") }
        assertNotNull(dbgSection, "Debug section should exist in linked DLL")
    }

    // --- SectionKind.isDebug ---

    @Test
    fun `sectionKind isDebug returns true for debug kinds`() {
        assertTrue(SectionKind.DEBUG_INFO.isDebug)
        assertTrue(SectionKind.DEBUG_ABBREV.isDebug)
        assertTrue(SectionKind.DEBUG_LINE.isDebug)
        assertTrue(SectionKind.DEBUG_STR.isDebug)
        assertTrue(SectionKind.DEBUG_RANGES.isDebug)
        assertTrue(SectionKind.DEBUG_LOC.isDebug)
        assertTrue(SectionKind.DEBUG_FRAME.isDebug)
        assertTrue(SectionKind.DEBUG_ARANGES.isDebug)
        assertTrue(SectionKind.DEBUG_RNGLISTS.isDebug)
        assertTrue(SectionKind.DEBUG_LOCLISTS.isDebug)
    }

    @Test
    fun `sectionKind isDebug returns false for non-debug kinds`() {
        assertFalse(SectionKind.TEXT.isDebug)
        assertFalse(SectionKind.DATA.isDebug)
        assertFalse(SectionKind.RODATA.isDebug)
        assertFalse(SectionKind.BSS.isDebug)
        assertFalse(SectionKind.RSRC.isDebug)
        assertFalse(SectionKind.CUSTOM.isDebug)
    }

    // --- Multiple objects merge debug sections ---

    @Test
    fun `elf linker merges debug sections from multiple objects`() {
        val obj1 = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                Section(".debug_info", SectionKind.DEBUG_INFO, ByteArray(20) { 0x01 }, align = 1),
            ),
            symbols = listOf(Symbol("_start", value = 0, size = 1, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            relocations = emptyList(),
        )
        val obj2 = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0x90.toByte()), align = 16),
                Section(".debug_info", SectionKind.DEBUG_INFO, ByteArray(30) { 0x02 }, align = 1),
            ),
            symbols = listOf(Symbol("helper", value = 0, size = 1, section = ".text",
                binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION)),
            relocations = emptyList(),
        )
        val binary = ElfStaticLinker().link(listOf(obj1, obj2))
        val elf = ElfReader.read(binary)
        val debugInfo = elf.sectionByName(".debug_info")
        assertNotNull(debugInfo)
        // Merged: 20 + 30 = 50 bytes
        assertEquals(50, debugInfo!!.size, "Merged debug sections should have combined size")
    }
}
