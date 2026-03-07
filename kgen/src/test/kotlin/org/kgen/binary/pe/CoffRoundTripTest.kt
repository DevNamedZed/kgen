package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CoffRoundTripTest {

    private fun makeCoffObject(
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
    fun `round-trip preserves text section data`() {
        val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val text = pe.sections.first { it.name.trim('\u0000') == ".text" }
        assertTrue(text.isCode)
        // COFF section data should contain our code bytes
        for (i in code.indices) {
            assertEquals(code[i], text.data[i], "Byte $i mismatch")
        }
    }

    @Test
    fun `round-trip preserves data section`() {
        val data = byteArrayOf(0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49)
        val obj = makeCoffObject(
            sections = listOf(Section(".data", SectionKind.DATA, data, align = 8)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val dataSec = pe.sections.first { it.name.trim('\u0000') == ".data" }
        assertTrue(dataSec.isInitializedData)
        assertTrue(dataSec.isWritable)
        for (i in data.indices) {
            assertEquals(data[i], dataSec.data[i])
        }
    }

    @Test
    fun `round-trip preserves rodata section`() {
        val rodata = "Hello, World!\u0000".toByteArray(Charsets.US_ASCII)
        val obj = makeCoffObject(
            sections = listOf(Section(".rdata", SectionKind.RODATA, rodata, align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val rdataSec = pe.sections.first { it.name.trim('\u0000') == ".rdata" }
        assertTrue(rdataSec.isInitializedData)
        assertTrue(rdataSec.isReadable)
        assertFalse(rdataSec.isWritable)
    }

    @Test
    fun `round-trip preserves global function symbol`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("myFunc", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val sym = pe.symbols.firstOrNull { it.name == "myFunc" }
        assertNotNull(sym, "Should find myFunc symbol")
        assertTrue(sym!!.isExternal)
        assertTrue(sym.isFunction)
    }

    @Test
    fun `round-trip preserves local symbol`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol("localHelper", value = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val sym = pe.symbols.firstOrNull { it.name == "localHelper" }
        assertNotNull(sym)
        assertFalse(sym!!.isExternal)
    }

    @Test
    fun `round-trip preserves undefined symbol`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val sym = pe.symbols.firstOrNull { it.name == "printf" }
        assertNotNull(sym)
        assertTrue(sym!!.isExternal)
    }

    @Test
    fun `round-trip preserves multiple sections order`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                Section(".data", SectionKind.DATA, byteArrayOf(42), align = 8),
                Section(".rdata", SectionKind.RODATA, byteArrayOf(0, 0, 0, 0), align = 4),
                Section(".bss", SectionKind.BSS, ByteArray(64), align = 16),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(4, pe.sections.size)
        assertEquals(".text", pe.sections[0].name.trim('\u0000'))
        assertEquals(".data", pe.sections[1].name.trim('\u0000'))
        assertEquals(".rdata", pe.sections[2].name.trim('\u0000'))
        assertEquals(".bss", pe.sections[3].name.trim('\u0000'))
    }

    @Test
    fun `round-trip preserves relocation count`() {
        val code = ByteArray(16)
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 1)),
            symbols = listOf(
                Symbol("foo", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("bar", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "foo",
                    type = RelocationType.COFF_X86_64.REL32, section = ".text"),
                Relocation(offset = 8, symbol = "bar",
                    type = RelocationType.COFF_X86_64.REL32, section = ".text"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val text = pe.sections.first()
        assertEquals(2, text.numberOfRelocations)
    }

    @Test
    fun `round-trip complex COFF object`() {
        val textCode = byteArrayOf(
            0x55, 0x48, 0x89.toByte(), 0xE5.toByte(),
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x5D, 0xC3.toByte(),
        )
        val rodata = "Hello\u0000".toByteArray(Charsets.US_ASCII)

        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, textCode, align = 16),
                Section(".rdata", SectionKind.RODATA, rodata, align = 1),
            ),
            symbols = listOf(
                Symbol("main", value = 0, size = textCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "puts",
                    type = RelocationType.COFF_X86_64.REL32, section = ".text"),
            ),
        )

        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertFalse(pe.isPe, "Should be raw COFF, not full PE")
        assertEquals(PeConstants.MACHINE_AMD64, pe.coffHeader.machine)
        assertEquals(2, pe.sections.size)

        val mainSym = pe.symbols.firstOrNull { it.name == "main" }
        assertNotNull(mainSym)
        assertTrue(mainSym!!.isFunction)

        val putsSym = pe.symbols.firstOrNull { it.name == "puts" }
        assertNotNull(putsSym)
    }

    @Test
    fun `COFF with many symbols`() {
        val symbols = (0 until 30).map { i ->
            Symbol("func_$i", value = i.toLong() * 4, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
        }
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(120), align = 16)),
            symbols = symbols,
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        for (i in 0 until 30) {
            assertNotNull(
                pe.symbols.firstOrNull { it.name == "func_$i" },
                "Missing symbol func_$i"
            )
        }
    }

    @Test
    fun `COFF text section has execute and read characteristics`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val text = pe.sections.first()
        assertTrue(text.isCode)
        assertTrue(text.isExecutable)
        assertTrue(text.isReadable)
        assertFalse(text.isWritable)
    }

    @Test
    fun `COFF data section has write characteristic`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(1, 2), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val data = pe.sections.first()
        assertTrue(data.isInitializedData)
        assertTrue(data.isWritable)
        assertTrue(data.isReadable)
    }

    @Test
    fun `COFF empty section produces valid output`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(0), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        assertTrue(bytes.size >= 20, "Should at least have COFF header")

        val pe = PeReader.read(bytes)
        assertEquals(1, pe.sections.size)
    }

    @Test
    fun `COFF ELF reloc types get mapped to COFF types`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "target",
                    type = RelocationType.X86_64.PLT32, section = ".text"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        // PLT32 should be mapped to REL32
        val text = pe.sections.first()
        assertTrue(text.numberOfRelocations > 0)
    }
}
