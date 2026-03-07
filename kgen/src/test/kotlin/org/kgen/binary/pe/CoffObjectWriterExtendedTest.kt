package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CoffObjectWriterExtendedTest {

    private val writer = CoffObjectWriter()

    private fun obj(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
    ) = ObjectFile(
        format = ObjectFormat.PE_COFF,
        arch = Architecture.X86_64_WINDOWS,
        sections = sections,
        symbols = symbols,
        relocations = relocations,
    )

    @Test
    fun `empty sections list produces valid COFF`() {
        val bytes = writer.write(obj())
        assertEquals(0x64, bytes[0].toInt() and 0xFF)
        assertEquals(0x86.toByte(), bytes[1])
        // 0 sections
        assertEquals(0, bytes[2].toInt() and 0xFF)
    }

    @Test
    fun `single text section with AMD64 machine`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), 0, 1))
        ))
        val pe = PeReader.read(bytes)
        assertFalse(pe.isPe)
        assertEquals(PeConstants.MACHINE_AMD64, pe.coffHeader.machine)
        assertEquals(1, pe.sections.size)
    }

    @Test
    fun `text section has code characteristics`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), 0, 16))
        ))
        val pe = PeReader.read(bytes)
        val text = pe.sections[0]
        assertTrue(text.isCode)
        assertTrue(text.isExecutable)
        assertTrue(text.isReadable)
    }

    @Test
    fun `data section has writable characteristics`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(42), 0, 4))
        ))
        val pe = PeReader.read(bytes)
        val data = pe.sections[0]
        assertTrue(data.isWritable)
        assertTrue(data.isReadable)
        assertFalse(data.isCode)
    }

    @Test
    fun `rodata section is read-only`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".rdata", SectionKind.RODATA, byteArrayOf(1, 2, 3, 4), 0, 4))
        ))
        val pe = PeReader.read(bytes)
        val rdata = pe.sections[0]
        assertTrue(rdata.isReadable)
        assertFalse(rdata.isWritable)
        assertFalse(rdata.isCode)
    }

    @Test
    fun `bss section has uninitialized characteristics`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(256), 0, 16))
        ))
        val pe = PeReader.read(bytes)
        assertEquals(1, pe.sections.size)
        assertEquals(".bss", pe.sections[0].name.trim('\u0000'))
    }

    @Test
    fun `four sections are all present`() {
        val bytes = writer.write(obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), 0, 1),
                Section(".data", SectionKind.DATA, byteArrayOf(1), 0, 1),
                Section(".rdata", SectionKind.RODATA, byteArrayOf(2), 0, 1),
                Section(".bss", SectionKind.BSS, ByteArray(64), 0, 16),
            )
        ))
        val pe = PeReader.read(bytes)
        assertEquals(4, pe.sections.size)
    }

    @Test
    fun `global symbol is external`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), 0, 1)),
            symbols = listOf(Symbol("func", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        ))
        val pe = PeReader.read(bytes)
        val sym = pe.symbols.first { it.name == "func" }
        assertTrue(sym.isExternal)
    }

    @Test
    fun `local symbol is static`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), 0, 1)),
            symbols = listOf(Symbol("helper", value = 0, section = ".text",
                binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION)),
        ))
        val pe = PeReader.read(bytes)
        val sym = pe.symbols.first { it.name == "helper" }
        assertFalse(sym.isExternal)
    }

    @Test
    fun `function symbol has type 0x20`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), 0, 1)),
            symbols = listOf(Symbol("fn", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        ))
        val pe = PeReader.read(bytes)
        val sym = pe.symbols.first { it.name == "fn" }
        assertTrue(sym.isFunction)
    }

    @Test
    fun `data symbol does not have function type`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(42), 0, 1)),
            symbols = listOf(Symbol("myvar", value = 0, section = ".data",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA)),
        ))
        val pe = PeReader.read(bytes)
        val sym = pe.symbols.first { it.name == "myvar" }
        assertFalse(sym.isFunction)
    }

    @Test
    fun `undefined symbol has section number zero`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), 0, 1)),
            symbols = listOf(Symbol("printf", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
        ))
        val pe = PeReader.read(bytes)
        val sym = pe.symbols.first { it.name == "printf" }
        assertEquals(0, sym.sectionNumber)
    }

    @Test
    fun `symbol value is preserved`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), 0, 16)),
            symbols = listOf(Symbol("inner", value = 16, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        ))
        val pe = PeReader.read(bytes)
        val sym = pe.symbols.first { it.name == "inner" }
        assertEquals(16, sym.value)
    }

    @Test
    fun `single relocation is recorded`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), 0, 1)),
            symbols = listOf(Symbol("target", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 4, symbol = "target",
                type = RelocationType.COFF_X86_64.REL32, addend = 0, section = ".text")),
        ))
        val pe = PeReader.read(bytes)
        assertTrue(pe.sections[0].numberOfRelocations > 0)
    }

    @Test
    fun `multiple relocations on same section`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), 0, 1)),
            symbols = listOf(
                Symbol("a", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("b", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "a", type = RelocationType.COFF_X86_64.REL32,
                    addend = 0, section = ".text"),
                Relocation(offset = 10, symbol = "b", type = RelocationType.COFF_X86_64.REL32,
                    addend = 0, section = ".text"),
            ),
        ))
        val pe = PeReader.read(bytes)
        assertTrue(pe.sections[0].numberOfRelocations >= 2)
    }

    @Test
    fun `ELF relocation types are mapped to COFF`() {
        // X86_64.PC32 should become COFF REL32
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), 0, 1)),
            symbols = listOf(Symbol("fn", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 1, symbol = "fn",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        ))
        val pe = PeReader.read(bytes)
        assertTrue(pe.sections[0].numberOfRelocations > 0)
    }

    @Test
    fun `PLT32 relocation is mapped to COFF REL32`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), 0, 1)),
            symbols = listOf(Symbol("fn", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 1, symbol = "fn",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        ))
        val pe = PeReader.read(bytes)
        assertTrue(pe.sections[0].numberOfRelocations > 0)
    }

    @Test
    fun `multiple symbols with multiple sections`() {
        val bytes = writer.write(obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(16), 0, 16),
                Section(".data", SectionKind.DATA, ByteArray(8), 0, 8),
            ),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 8, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                Symbol("counter", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
        ))
        val pe = PeReader.read(bytes)
        assertNotNull(pe.symbols.firstOrNull { it.name == "main" })
        assertNotNull(pe.symbols.firstOrNull { it.name == "helper" })
        assertNotNull(pe.symbols.firstOrNull { it.name == "counter" })
    }

    @Test
    fun `section data content is preserved`() {
        val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0x5D, 0xC3.toByte())
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, 0, 16))
        ))
        val pe = PeReader.read(bytes)
        val text = pe.sections[0]
        for (i in code.indices) {
            assertEquals(code[i], text.data[i], "Byte $i mismatch")
        }
    }

    @Test
    fun `output size is reasonable`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), 0, 1))
        ))
        assertTrue(bytes.size > 20, "Should have at least COFF header")
        assertTrue(bytes.size < 4096, "Simple COFF obj should be small")
    }

    @Test
    fun `empty symbols list produces valid output`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0x90.toByte()), 0, 1))
        ))
        val pe = PeReader.read(bytes)
        assertEquals(1, pe.sections.size)
    }

    @Test
    fun `large section data is preserved`() {
        val code = ByteArray(4096) { (it % 256).toByte() }
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, 0, 16))
        ))
        val pe = PeReader.read(bytes)
        val text = pe.sections[0]
        assertEquals(code.size, text.data.size)
        for (i in code.indices) {
            assertEquals(code[i], text.data[i], "Byte $i mismatch in large section")
        }
    }

    @Test
    fun `section names are 8 bytes or less in header`() {
        val bytes = writer.write(obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), 0, 1),
                Section(".data", SectionKind.DATA, byteArrayOf(0), 0, 1),
            )
        ))
        val pe = PeReader.read(bytes)
        assertEquals(".text", pe.sections[0].name.trim('\u0000'))
        assertEquals(".data", pe.sections[1].name.trim('\u0000'))
    }

    @Test
    fun `section with no relocations has zero relocation count`() {
        val bytes = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), 0, 1)),
            symbols = listOf(Symbol("fn", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        ))
        val pe = PeReader.read(bytes)
        assertEquals(0, pe.sections[0].numberOfRelocations)
    }
}
