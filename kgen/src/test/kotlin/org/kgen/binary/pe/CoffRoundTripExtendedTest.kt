package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CoffRoundTripExtendedTest {

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
    fun singleByteCodeSection() {
        val code = byteArrayOf(0xC3.toByte())
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val text = pe.sections.first { it.name.trim('\u0000') == ".text" }
        assertEquals(1, text.data.size)
        assertEquals(0xC3.toByte(), text.data[0])
    }

    @Test
    fun largeTextSection() {
        val code = ByteArray(4096) { (it % 256).toByte() }
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val text = pe.sections.first { it.name.trim('\u0000') == ".text" }
        assertEquals(4096, text.rawDataSize)
        for (i in code.indices) {
            assertEquals(code[i], text.data[i], "Byte $i mismatch in large section")
        }
    }

    @Test
    fun bssSectionCharacteristics() {
        val obj = makeCoffObject(
            sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(256), align = 16)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val bss = pe.sections.first { it.name.trim('\u0000') == ".bss" }
        assertTrue(bss.isUninitializedData)
        assertTrue(bss.isReadable)
        assertTrue(bss.isWritable)
        assertFalse(bss.isCode)
    }

    @Test
    fun symbolNamePreservation() {
        val names = listOf("alpha", "beta", "gamma", "delta")
        val symbols = names.map { name ->
            Symbol(name, value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
        }
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 1)),
            symbols = symbols,
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        for (name in names) {
            assertNotNull(pe.symbols.firstOrNull { it.name == name }, "Missing symbol $name")
        }
    }

    @Test
    fun longSymbolNameViaStringTable() {
        val longName = "veryLongFunctionNameThatExceedsEightBytes"
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol(longName, value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val sym = pe.symbols.firstOrNull { it.name == longName }
        assertNotNull(sym, "Long symbol name should be preserved via string table")
    }

    @Test
    fun symbolValuePreservation() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 1)),
            symbols = listOf(
                Symbol("atOffset", value = 32, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val sym = pe.symbols.first { it.name == "atOffset" }
        assertEquals(32L, sym.value)
    }

    @Test
    fun multipleRelocationsInSection() {
        val code = ByteArray(32)
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 1)),
            symbols = listOf(
                Symbol("a", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("b", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("c", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "a",
                    type = RelocationType.COFF_X86_64.REL32, section = ".text"),
                Relocation(offset = 8, symbol = "b",
                    type = RelocationType.COFF_X86_64.REL32, section = ".text"),
                Relocation(offset = 16, symbol = "c",
                    type = RelocationType.COFF_X86_64.REL32, section = ".text"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        assertEquals(3, pe.sections.first().numberOfRelocations)
    }

    @Test
    fun addr64Relocation() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "target",
                    type = RelocationType.COFF_X86_64.ADDR64, section = ".text"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        assertEquals(1, pe.sections.first().numberOfRelocations)
    }

    @Test
    fun dataSymbolNotFunction() {
        val obj = makeCoffObject(
            sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(1, 2, 3, 4), align = 4)),
            symbols = listOf(
                Symbol("myGlobal", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val sym = pe.symbols.first { it.name == "myGlobal" }
        assertFalse(sym.isFunction)
        assertTrue(sym.isExternal)
    }

    @Test
    fun mixedLocalAndGlobalSymbols() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 1)),
            symbols = listOf(
                Symbol("publicFunc", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("privateHelper", value = 16, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val pub = pe.symbols.first { it.name == "publicFunc" }
        val priv = pe.symbols.first { it.name == "privateHelper" }
        assertTrue(pub.isExternal)
        assertFalse(priv.isExternal)
        assertTrue(priv.isStatic)
    }

    @Test
    fun exactDataContentPreserved() {
        val data = byteArrayOf(
            0x00, 0x01, 0x7F, 0x80.toByte(), 0xFF.toByte(),
            0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()
        )
        val obj = makeCoffObject(
            sections = listOf(Section(".data", SectionKind.DATA, data, align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val sec = pe.sections.first { it.name.trim('\u0000') == ".data" }
        assertArrayEquals(data, sec.data.copyOfRange(0, data.size))
    }

    @Test
    fun onlyExternalSymbols() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("malloc", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val printf = pe.symbols.first { it.name == "printf" }
        val malloc = pe.symbols.first { it.name == "malloc" }
        assertTrue(printf.isUndefined)
        assertTrue(malloc.isUndefined)
        assertTrue(printf.isExternal)
        assertTrue(malloc.isExternal)
    }

    @Test
    fun sixteenByteAlignment() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val text = pe.sections.first()
        // 16-byte alignment characteristic is 0x00500000
        assertTrue(text.characteristics and 0x00F00000 != 0)
    }

    @Test
    fun amd64Machine() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(1), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        assertEquals(PeConstants.MACHINE_AMD64, pe.coffHeader.machine)
    }

    @Test
    fun notPeExecutable() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(1), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        assertFalse(pe.isPe, "COFF object should not be a PE executable")
        assertFalse(pe.isExecutable)
        assertFalse(pe.isDll)
    }

    @Test
    fun noSectionsProducesValidCoff() {
        val obj = makeCoffObject()
        val bytes = CoffObjectWriter().write(obj)
        assertTrue(bytes.size >= 20, "Should have at least COFF header")
        val pe = PeReader.read(bytes)
        assertEquals(0, pe.sections.size)
    }

    @Test
    fun manySectionsPreserved() {
        val sections = (1..8).map { i ->
            Section(".s$i", SectionKind.DATA, byteArrayOf(i.toByte()), align = 1)
        }
        val obj = makeCoffObject(sections = sections)
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        assertEquals(8, pe.sections.size)
        for (i in 1..8) {
            val sec = pe.sections[i - 1]
            assertEquals(".s$i", sec.name.trim('\u0000'))
        }
    }

    @Test
    fun rodataIsReadOnly() {
        val obj = makeCoffObject(
            sections = listOf(Section(".rdata", SectionKind.RODATA, byteArrayOf(42), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val rdata = pe.sections.first()
        assertTrue(rdata.isReadable)
        assertFalse(rdata.isWritable)
        assertTrue(rdata.isInitializedData)
        assertFalse(rdata.isCode)
    }

    @Test
    fun textSectionIsExecutable() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val text = pe.sections.first()
        assertTrue(text.isExecutable)
        assertTrue(text.isReadable)
        assertTrue(text.isCode)
        assertFalse(text.isWritable)
    }

    @Test
    fun dataSectionIsWritable() {
        val obj = makeCoffObject(
            sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(0), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val data = pe.sections.first()
        assertTrue(data.isWritable)
        assertTrue(data.isReadable)
        assertTrue(data.isInitializedData)
        assertFalse(data.isExecutable)
    }

    @Test
    fun sectionOrderPreserved() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                Section(".rdata", SectionKind.RODATA, byteArrayOf(0), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(1), align = 4),
                Section(".bss", SectionKind.BSS, ByteArray(32), align = 8),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        assertEquals(".text", pe.sections[0].name.trim('\u0000'))
        assertEquals(".rdata", pe.sections[1].name.trim('\u0000'))
        assertEquals(".data", pe.sections[2].name.trim('\u0000'))
        assertEquals(".bss", pe.sections[3].name.trim('\u0000'))
    }

    @Test
    fun elfPC32MapsToRel32() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 2, symbol = "target",
                    type = RelocationType.X86_64.PC32, section = ".text"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        assertTrue(pe.sections.first().numberOfRelocations > 0)
    }

    @Test
    fun elfR64MapsToAddr64() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "target",
                    type = RelocationType.X86_64.R_64, section = ".text"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        assertTrue(pe.sections.first().numberOfRelocations > 0)
    }

    @Test
    fun fullProgramWithCallRelocation() {
        val textCode = byteArrayOf(
            0x55,
            0x48, 0x89.toByte(), 0xE5.toByte(),
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x5D,
            0xC3.toByte(),
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
        assertFalse(pe.isPe)
        assertEquals(2, pe.sections.size)
        assertNotNull(pe.symbols.firstOrNull { it.name == "main" })
        assertNotNull(pe.symbols.firstOrNull { it.name == "puts" })
        assertEquals(1, pe.sections[0].numberOfRelocations)
    }

    @Test
    fun stringInRodataPreserved() {
        val str = "Hello, COFF World!\u0000"
        val strBytes = str.toByteArray(Charsets.US_ASCII)
        val obj = makeCoffObject(
            sections = listOf(Section(".rdata", SectionKind.RODATA, strBytes, align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val sec = pe.sections.first()
        val restored = String(sec.data.copyOfRange(0, strBytes.size), Charsets.US_ASCII)
        assertEquals(str, restored)
    }

    @Test
    fun fiftySymbolsPreserved() {
        val symbols = (0 until 50).map { i ->
            Symbol("sym$i", value = i.toLong(), section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
        }
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(200), align = 1)),
            symbols = symbols,
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        for (i in 0 until 50) {
            assertNotNull(pe.symbols.firstOrNull { it.name == "sym$i" }, "Missing sym$i")
        }
    }

    @Test
    fun codeBytesPreservedAtEveryOffset() {
        val code = ByteArray(256) { i -> i.toByte() }
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val text = pe.sections.first()
        for (i in code.indices) {
            assertEquals(code[i], text.data[i], "Mismatch at offset $i")
        }
    }

    @Test
    fun coffHeaderSymbolCount() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(4), align = 1)),
            symbols = listOf(
                Symbol("f1", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("f2", value = 2, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        // 1 section symbol + 2 user symbols = 3
        assertEquals(3, pe.coffHeader.numberOfSymbols)
    }

    @Test
    fun coffHeaderSectionCount() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(1), align = 1),
                Section(".data", SectionKind.DATA, ByteArray(1), align = 1),
                Section(".rdata", SectionKind.RODATA, ByteArray(1), align = 1),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        assertEquals(3, pe.coffHeader.numberOfSections)
    }

    @Test
    fun undefinedSymbolHasSectionNumberZero() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(4), align = 1)),
            symbols = listOf(
                Symbol("extern_fn", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val sym = pe.symbols.first { it.name == "extern_fn" }
        assertTrue(sym.isUndefined)
        assertEquals(0, sym.sectionNumber)
    }

    @Test
    fun canReadDetectsCoff() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(1), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        assertTrue(PeReader.canRead(bytes))
    }

    @Test
    fun objectFileProjection() {
        val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("func", value = 0, size = 5, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val projected = PeReader.toObjectFile(pe)
        assertEquals(ObjectFormat.PE_COFF, projected.format)
        assertTrue(projected.symbols.any { it.name == "func" })
        assertTrue(projected.sections.any { it.name.contains("text") })
    }

    @Test
    fun twoByteAlignCharacteristic() {
        val obj = makeCoffObject(
            sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(1, 2), align = 2)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val data = pe.sections.first()
        // 2-byte alignment = 0x00200000
        val alignBits = data.characteristics and 0x00F00000
        assertEquals(0x00200000, alignBits)
    }

    @Test
    fun fourByteAlignCharacteristic() {
        val obj = makeCoffObject(
            sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(1, 2, 3, 4), align = 4)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val data = pe.sections.first()
        // 4-byte alignment = 0x00300000
        val alignBits = data.characteristics and 0x00F00000
        assertEquals(0x00300000, alignBits)
    }
}
