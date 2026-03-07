package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CoffObjectWriterTest {

    @Test
    fun `writes valid COFF header`() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture.X86_64_WINDOWS,
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), 0, 1)),
            symbols = listOf(Symbol("main", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            relocations = emptyList(),
        )
        val bytes = CoffObjectWriter().write(obj)

        // First 2 bytes: machine type (0x8664 for AMD64)
        assertEquals(0x64, bytes[0].toInt() and 0xFF)
        assertEquals(0x86.toByte(), bytes[1])
        // Bytes 2-3: number of sections = 1
        assertEquals(1, bytes[2].toInt() and 0xFF)
        assertEquals(0, bytes[3].toInt() and 0xFF)
    }

    @Test
    fun `round-trip through PeReader`() {
        val code = byteArrayOf(0x90.toByte(), 0xC3.toByte()) // nop; ret
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture.X86_64_WINDOWS,
            sections = listOf(Section(".text", SectionKind.TEXT, code, 0, 16)),
            symbols = listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val bytes = CoffObjectWriter().write(obj)

        // PeReader should be able to read raw COFF objects
        val pe = PeReader.read(bytes)
        assertFalse(pe.isPe, "Should not be a full PE")
        assertEquals(PeConstants.MACHINE_AMD64, pe.coffHeader.machine)
        assertEquals(1, pe.sections.size)
        assertEquals(".text", pe.sections[0].name.trim('\u0000'))
    }

    @Test
    fun `includes symbols`() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture.X86_64_WINDOWS,
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), 0, 1)),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        // Should have section symbol + user symbols
        val mainSym = pe.symbols.firstOrNull { it.name == "main" }
        assertNotNull(mainSym, "Should have main symbol: ${pe.symbols.map { it.name }}")
        assertTrue(mainSym!!.isExternal, "main should be external")
        assertTrue(mainSym.isFunction, "main should be a function")
    }

    @Test
    fun `includes relocations`() {
        val code = ByteArray(10) // placeholder code
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture.X86_64_WINDOWS,
            sections = listOf(Section(".text", SectionKind.TEXT, code, 0, 1)),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(
                    offset = 5, symbol = "printf",
                    type = RelocationType.COFF_X86_64.REL32, addend = 0, section = ".text")
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val textSection = pe.sections.first()
        assertTrue(textSection.numberOfRelocations > 0,
            "Should have relocations: numReloc=${textSection.numberOfRelocations}")
    }

    @Test
    fun `writes multiple sections`() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture.X86_64_WINDOWS,
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), 0, 1),
                Section(".data", SectionKind.DATA, byteArrayOf(42), 0, 1),
                Section(".rdata", SectionKind.RODATA, byteArrayOf(0, 0, 0, 0), 0, 4),
            ),
            symbols = emptyList(),
            relocations = emptyList(),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(3, pe.sections.size)
        assertEquals(".text", pe.sections[0].name.trim('\u0000'))
        assertEquals(".data", pe.sections[1].name.trim('\u0000'))
        assertEquals(".rdata", pe.sections[2].name.trim('\u0000'))
    }

    @Test
    fun `x86 codegen can produce COFF obj`() {
        // Verify CoffObjectWriter works with X86CodeGenerator output
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture.X86_64_WINDOWS,
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(
                    0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), // push rbp; mov rbp,rsp
                    0x89.toByte(), 0xF8.toByte(),              // mov eax, edi
                    0x01, 0xF0.toByte(),                       // add eax, esi
                    0x5D, 0xC3.toByte(),                       // pop rbp; ret
                ), 0, 16)
            ),
            symbols = listOf(
                Symbol("add", value = 0, size = 10, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val bytes = CoffObjectWriter().write(obj)
        assertTrue(bytes.size > 20, "Should produce non-trivial output")

        val pe = PeReader.read(bytes)
        assertEquals(1, pe.sections.size)
        val addSym = pe.symbols.firstOrNull { it.name == "add" }
        assertNotNull(addSym)
    }
}
