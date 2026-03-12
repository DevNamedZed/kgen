package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class PeComprehensiveTest {

    private fun makeCoffObject(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
        imports: List<ImportEntry> = emptyList(),
    ) = ObjectFile(
        format = ObjectFormat.PE_COFF,
        arch = Architecture.X86_64_WINDOWS,
        sections = sections,
        symbols = symbols,
        relocations = relocations,
        imports = imports,
    )

    private fun writeCoff(obj: ObjectFile): ByteArray = CoffObjectWriter().write(obj)

    private fun readCoff(bytes: ByteArray): PeFile = PeReader.read(bytes)

    private fun roundTrip(obj: ObjectFile): PeFile = readCoff(writeCoff(obj))

    private fun makePeFlat(code: ByteArray = byteArrayOf(0xCC.toByte()),
                           rodata: ByteArray = byteArrayOf()): PeFile =
        PeReader.read(PeWriter.writeFlat(code, rodata))

    @Nested
    inner class CoffHeaderFields {

        @Test
        fun `machine type is AMD64 for x86_64 windows`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertEquals(PeConstants.MACHINE_AMD64, pe.coffHeader.machine)
        }

        @Test
        fun `machine type is ARM64 when specified`() {
            val obj = ObjectFile(
                format = ObjectFormat.PE_COFF,
                arch = Architecture(ArchType.AARCH64, os = "windows"),
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0x00), align = 1)),
                symbols = emptyList(), relocations = emptyList(),
            )
            val bytes = CoffObjectWriter(machine = PeConstants.MACHINE_ARM64).write(obj)
            val pe = PeReader.read(bytes)
            assertEquals(PeConstants.MACHINE_ARM64, pe.coffHeader.machine)
        }

        @Test
        fun `section count matches number of sections`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1),
                ),
            ))
            assertEquals(2, pe.coffHeader.numberOfSections)
        }

        @Test
        fun `section count is zero for empty object`() {
            val pe = roundTrip(makeCoffObject())
            assertEquals(0, pe.coffHeader.numberOfSections)
        }

        @Test
        fun `timestamp is zero in writer output`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertEquals(0, pe.coffHeader.timestamp)
        }

        @Test
        fun `optional header size is zero for COFF obj`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertEquals(0, pe.coffHeader.optionalHeaderSize)
        }

        @Test
        fun `characteristics is zero for COFF obj`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertEquals(0, pe.coffHeader.characteristics)
        }

        @Test
        fun `COFF object is not PE`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertFalse(pe.isPe)
        }

        @Test
        fun `symbol table offset is non-zero when symbols present`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
                symbols = listOf(Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ))
            assertTrue(pe.coffHeader.symbolTableOffset > 0)
        }

        @Test
        fun `number of symbols includes section and user symbols`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
                symbols = listOf(Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ))
            // 1 section symbol + 1 user symbol = 2
            assertEquals(2, pe.coffHeader.numberOfSymbols)
        }

        @Test
        fun `five sections produces correct count`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".data", SectionKind.DATA, byteArrayOf(1), align = 1),
                    Section(".rdata", SectionKind.RODATA, byteArrayOf(2), align = 1),
                    Section(".bss", SectionKind.BSS, ByteArray(8), align = 1),
                    Section(".debug", SectionKind.DEBUG_INFO, byteArrayOf(3), align = 1),
                ),
            ))
            assertEquals(5, pe.coffHeader.numberOfSections)
        }
    }

    @Nested
    inner class SectionHeaders {

        @Test
        fun `text section has code characteristic`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            val sec = pe.sections.first()
            assertTrue(sec.isCode)
        }

        @Test
        fun `text section has execute characteristic`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertTrue(pe.sections.first().isExecutable)
        }

        @Test
        fun `text section has read characteristic`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertTrue(pe.sections.first().isReadable)
        }

        @Test
        fun `text section is not writable`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertFalse(pe.sections.first().isWritable)
        }

        @Test
        fun `data section has initialized data characteristic`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1)),
            ))
            assertTrue(pe.sections.first().isInitializedData)
        }

        @Test
        fun `data section is writable`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1)),
            ))
            assertTrue(pe.sections.first().isWritable)
        }

        @Test
        fun `data section is readable`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1)),
            ))
            assertTrue(pe.sections.first().isReadable)
        }

        @Test
        fun `data section is not executable`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1)),
            ))
            assertFalse(pe.sections.first().isExecutable)
        }

        @Test
        fun `rodata section is readable but not writable`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".rdata", SectionKind.RODATA, byteArrayOf(1, 2, 3), align = 1)),
            ))
            val sec = pe.sections.first()
            assertTrue(sec.isReadable)
            assertFalse(sec.isWritable)
            assertTrue(sec.isInitializedData)
        }

        @Test
        fun `bss section has uninitialized data characteristic`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(16), align = 1)),
            ))
            assertTrue(pe.sections.first().isUninitializedData)
        }

        @Test
        fun `bss section is writable and readable`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(16), align = 1)),
            ))
            val sec = pe.sections.first()
            assertTrue(sec.isWritable)
            assertTrue(sec.isReadable)
        }

        @Test
        fun `debug section is discardable`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".debug", SectionKind.DEBUG_INFO, byteArrayOf(0, 1), align = 1)),
            ))
            assertTrue(pe.sections.first().isDiscardable)
        }

        @Test
        fun `debug section is initialized data and readable`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".debug", SectionKind.DEBUG_INFO, byteArrayOf(0, 1), align = 1)),
            ))
            val sec = pe.sections.first()
            assertTrue(sec.isInitializedData)
            assertTrue(sec.isReadable)
        }

        @Test
        fun `section name is preserved`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".myname", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertEquals(".myname", pe.sections.first().name.trim('\u0000'))
        }

        @Test
        fun `section raw data size matches original data`() {
            val data = byteArrayOf(1, 2, 3, 4, 5)
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".data", SectionKind.DATA, data, align = 1)),
            ))
            assertEquals(data.size, pe.sections.first().rawDataSize)
        }
    }

    @Nested
    inner class AllSectionTypes {

        @Test
        fun `text section data round-trips`() {
            val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            ))
            val data = pe.sections.first().data
            for (i in code.indices) {
                assertEquals(code[i], data[i], "Byte $i mismatch")
            }
        }

        @Test
        fun `data section data round-trips`() {
            val expected = byteArrayOf(0x42, 0x43, 0x44, 0x45)
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".data", SectionKind.DATA, expected, align = 4)),
            ))
            for (i in expected.indices) {
                assertEquals(expected[i], pe.sections.first().data[i])
            }
        }

        @Test
        fun `rdata section preserves string content`() {
            val str = "Hello, World!\u0000".toByteArray(Charsets.US_ASCII)
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".rdata", SectionKind.RODATA, str, align = 1)),
            ))
            val data = pe.sections.first().data
            for (i in str.indices) {
                assertEquals(str[i], data[i])
            }
        }

        @Test
        fun `bss section round-trips`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(64), align = 16)),
            ))
            assertEquals(1, pe.sections.size)
            assertEquals(".bss", pe.sections[0].name.trim('\u0000'))
        }

        @Test
        fun `debug_info section round-trips`() {
            val debugData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".debug_info", SectionKind.DEBUG_INFO, debugData, align = 1)),
            ))
            assertEquals(1, pe.sections.size)
            assertTrue(pe.sections.first().isDiscardable)
        }

        @Test
        fun `debug_line section round-trips`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".debug_line", SectionKind.DEBUG_LINE, byteArrayOf(1, 2), align = 1)),
            ))
            assertEquals(1, pe.sections.size)
        }

        @Test
        fun `debug_abbrev section round-trips`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".debug_abbrev", SectionKind.DEBUG_ABBREV, byteArrayOf(1), align = 1)),
            ))
            assertEquals(1, pe.sections.size)
        }

        @Test
        fun `debug_str section round-trips`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".debug_str", SectionKind.DEBUG_STR, "hello\u0000".toByteArray(), align = 1)),
            ))
            assertEquals(1, pe.sections.size)
        }

        @Test
        fun `multiple section types in one object`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                    Section(".data", SectionKind.DATA, byteArrayOf(42), align = 8),
                    Section(".rdata", SectionKind.RODATA, byteArrayOf(0, 0, 0, 0), align = 4),
                    Section(".bss", SectionKind.BSS, ByteArray(64), align = 16),
                    Section(".debug", SectionKind.DEBUG_INFO, byteArrayOf(1, 2), align = 1),
                ),
            ))
            assertEquals(5, pe.sections.size)
            assertEquals(".text", pe.sections[0].name.trim('\u0000'))
            assertEquals(".data", pe.sections[1].name.trim('\u0000'))
            assertEquals(".rdata", pe.sections[2].name.trim('\u0000'))
            assertEquals(".bss", pe.sections[3].name.trim('\u0000'))
            assertEquals(".debug", pe.sections[4].name.trim('\u0000'))
        }

        @Test
        fun `empty section produces valid output`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(0), align = 1)),
            ))
            assertEquals(1, pe.sections.size)
        }

        @Test
        fun `large section data round-trips`() {
            val data = ByteArray(4096) { (it % 256).toByte() }
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".data", SectionKind.DATA, data, align = 16)),
            ))
            assertEquals(data.size, pe.sections.first().rawDataSize)
            for (i in data.indices) {
                assertEquals(data[i], pe.sections.first().data[i], "Byte $i mismatch in large section")
            }
        }
    }

    @Nested
    inner class SymbolTable {

        @Test
        fun `global function symbol is external`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ))
            val sym = pe.symbols.first { it.name == "main" }
            assertTrue(sym.isExternal)
            assertTrue(sym.isFunction)
        }

        @Test
        fun `local function symbol is static`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(Symbol("helper", value = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION)),
            ))
            val sym = pe.symbols.first { it.name == "helper" }
            assertTrue(sym.isStatic)
            assertFalse(sym.isExternal)
        }

        @Test
        fun `undefined symbol has section number 0`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            ))
            val sym = pe.symbols.first { it.name == "printf" }
            assertTrue(sym.isUndefined)
            assertTrue(sym.isExternal)
        }

        @Test
        fun `symbol value is preserved`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 1)),
                symbols = listOf(Symbol("func", value = 32, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ))
            val sym = pe.symbols.first { it.name == "func" }
            assertEquals(32L, sym.value)
        }

        @Test
        fun `data symbol has type 0 not 0x20`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".data", SectionKind.DATA, ByteArray(8), align = 1)),
                symbols = listOf(Symbol("myVar", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA)),
            ))
            val sym = pe.symbols.first { it.name == "myVar" }
            assertFalse(sym.isFunction)
        }

        @Test
        fun `section symbols are created for each section`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1),
                ),
            ))
            assertTrue(pe.symbols.any { it.name.trim('\u0000') == ".text" && it.isStatic })
            assertTrue(pe.symbols.any { it.name.trim('\u0000') == ".data" && it.isStatic })
        }

        @Test
        fun `multiple symbols across sections`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, ByteArray(32), align = 1),
                    Section(".data", SectionKind.DATA, ByteArray(16), align = 1),
                ),
                symbols = listOf(
                    Symbol("func1", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("func2", value = 16, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("var1", value = 0, section = ".data",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
                ),
            ))
            assertNotNull(pe.symbols.firstOrNull { it.name == "func1" })
            assertNotNull(pe.symbols.firstOrNull { it.name == "func2" })
            assertNotNull(pe.symbols.firstOrNull { it.name == "var1" })
        }

        @Test
        fun `symbol section number is correct for second section`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, ByteArray(8), align = 1),
                    Section(".data", SectionKind.DATA, ByteArray(8), align = 1),
                ),
                symbols = listOf(
                    Symbol("myData", value = 0, section = ".data",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
                ),
            ))
            val sym = pe.symbols.first { it.name == "myData" }
            assertEquals(2, sym.sectionNumber)
        }

        @Test
        fun `symbol in first section has section number 1`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(Symbol("fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ))
            val sym = pe.symbols.first { it.name == "fn" }
            assertEquals(1, sym.sectionNumber)
        }

        @Test
        fun `aux symbols count is zero for user symbols`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ))
            val sym = pe.symbols.first { it.name == "func" }
            assertEquals(0, sym.numberOfAuxSymbols)
        }

        @Test
        fun `storage class external for global binding`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(Symbol("fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ))
            assertEquals(PeConstants.IMAGE_SYM_CLASS_EXTERNAL, pe.symbols.first { it.name == "fn" }.storageClass)
        }

        @Test
        fun `storage class static for local binding`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(Symbol("helper", value = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION)),
            ))
            assertEquals(PeConstants.IMAGE_SYM_CLASS_STATIC, pe.symbols.first { it.name == "helper" }.storageClass)
        }
    }

    @Nested
    inner class StringTableLongNames {

        @Test
        fun `short name fits in 8 bytes`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
                symbols = listOf(Symbol("short", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ))
            assertNotNull(pe.symbols.firstOrNull { it.name == "short" })
        }

        @Test
        fun `exactly 8 char name fits inline`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
                symbols = listOf(Symbol("abcdefgh", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ))
            assertNotNull(pe.symbols.firstOrNull { it.name == "abcdefgh" })
        }

        @Test
        fun `9 char name uses string table`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
                symbols = listOf(Symbol("abcdefghi", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ))
            assertNotNull(pe.symbols.firstOrNull { it.name == "abcdefghi" })
        }

        @Test
        fun `long symbol name round-trips through string table`() {
            val longName = "very_long_function_name_that_exceeds_eight_bytes"
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(Symbol(longName, value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ))
            assertNotNull(pe.symbols.firstOrNull { it.name == longName },
                "Long symbol name should round-trip: ${pe.symbols.map { it.name }}")
        }

        @Test
        fun `multiple long names round-trip`() {
            val name1 = "first_long_symbol_name"
            val name2 = "second_long_symbol_name"
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(
                    Symbol(name1, value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol(name2, value = 8, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            ))
            assertNotNull(pe.symbols.firstOrNull { it.name == name1 })
            assertNotNull(pe.symbols.firstOrNull { it.name == name2 })
        }

        @Test
        fun `long section name uses string table`() {
            val longSectionName = ".long_debug_section_name"
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(longSectionName, SectionKind.DEBUG_INFO, byteArrayOf(1), align = 1)),
            ))
            assertEquals(1, pe.sections.size)
            assertEquals(longSectionName, pe.sections.first().name)
        }

        @Test
        fun `mix of short and long names`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 1)),
                symbols = listOf(
                    Symbol("fn", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("a_very_long_function_name", value = 8, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("short2", value = 16, section = ".text",
                        binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                ),
            ))
            assertNotNull(pe.symbols.firstOrNull { it.name == "fn" })
            assertNotNull(pe.symbols.firstOrNull { it.name == "a_very_long_function_name" })
            assertNotNull(pe.symbols.firstOrNull { it.name == "short2" })
        }
    }

    @Nested
    inner class CoffRelocations {

        @Test
        fun `REL32 relocation is preserved`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
                relocations = listOf(
                    Relocation(offset = 4, symbol = "target",
                        type = RelocationType.COFF_X86_64.REL32, section = ".text"),
                ),
            ))
            assertEquals(1, pe.sections.first().numberOfRelocations)
        }

        @Test
        fun `ADDR64 relocation maps correctly`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "target",
                        type = RelocationType.COFF_X86_64.ADDR64, section = ".text"),
                ),
            ))
            assertEquals(1, pe.sections.first().numberOfRelocations)
        }

        @Test
        fun `ADDR32 relocation maps correctly`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "target",
                        type = RelocationType.COFF_X86_64.ADDR32, section = ".text"),
                ),
            ))
            assertEquals(1, pe.sections.first().numberOfRelocations)
        }

        @Test
        fun `multiple relocations in one section`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 1)),
                symbols = listOf(
                    Symbol("foo", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                    Symbol("bar", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                    Symbol("baz", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 1, symbol = "foo",
                        type = RelocationType.COFF_X86_64.REL32, section = ".text"),
                    Relocation(offset = 8, symbol = "bar",
                        type = RelocationType.COFF_X86_64.REL32, section = ".text"),
                    Relocation(offset = 16, symbol = "baz",
                        type = RelocationType.COFF_X86_64.ADDR64, section = ".text"),
                ),
            ))
            assertEquals(3, pe.sections.first().numberOfRelocations)
        }

        @Test
        fun `ELF PLT32 maps to COFF REL32`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "target",
                        type = RelocationType.X86_64.PLT32, section = ".text"),
                ),
            ))
            assertTrue(pe.sections.first().numberOfRelocations > 0)
        }

        @Test
        fun `ELF PC32 maps to COFF REL32`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "target",
                        type = RelocationType.X86_64.PC32, section = ".text"),
                ),
            ))
            assertTrue(pe.sections.first().numberOfRelocations > 0)
        }

        @Test
        fun `ELF R_64 maps to COFF ADDR64`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "target",
                        type = RelocationType.X86_64.R_64, section = ".text"),
                ),
            ))
            assertTrue(pe.sections.first().numberOfRelocations > 0)
        }

        @Test
        fun `relocations only in targeted section`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, ByteArray(16), align = 1),
                    Section(".data", SectionKind.DATA, ByteArray(8), align = 1),
                ),
                symbols = listOf(Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "target",
                        type = RelocationType.COFF_X86_64.REL32, section = ".text"),
                ),
            ))
            assertTrue(pe.sections[0].numberOfRelocations > 0)
            assertEquals(0, pe.sections[1].numberOfRelocations)
        }

        @Test
        fun `no relocations when none specified`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            ))
            assertEquals(0, pe.sections.first().numberOfRelocations)
        }
    }

    @Nested
    inner class RoundTripObjectFileToCoff {

        @Test
        fun `simple function round-trips`() {
            val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
                symbols = listOf(Symbol("myfunc", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            )
            val bytes = writeCoff(obj)
            val pe = readCoff(bytes)
            val projected = PeReader.toObjectFile(pe)

            assertEquals(ObjectFormat.PE_COFF, projected.format)
            assertEquals(ArchType.X86_64, projected.arch.arch)
            assertTrue(projected.sections.any { it.kind == SectionKind.TEXT })
            assertTrue(projected.symbols.any { it.name == "myfunc" })
        }

        @Test
        fun `multi-section object round-trips to ObjectFile`() {
            val obj = makeCoffObject(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                    Section(".data", SectionKind.DATA, byteArrayOf(1, 2, 3), align = 8),
                    Section(".rdata", SectionKind.RODATA, "const\u0000".toByteArray(), align = 1),
                ),
                symbols = listOf(
                    Symbol("main", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val projected = PeReader.toObjectFile(roundTrip(obj))
            assertEquals(3, projected.sections.size)
            assertTrue(projected.sections.any { it.kind == SectionKind.TEXT })
            assertTrue(projected.sections.any { it.kind == SectionKind.DATA })
            assertTrue(projected.sections.any { it.kind == SectionKind.RODATA })
        }

        @Test
        fun `ObjectFile metadata has RELOCATABLE flag for COFF obj`() {
            val projected = PeReader.toObjectFile(roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            )))
            assertTrue(projected.metadata.flags.contains(ObjectFlag.RELOCATABLE))
        }

        @Test
        fun `ObjectFile metadata has WINDOWS osAbi`() {
            val projected = PeReader.toObjectFile(roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            )))
            assertEquals(OsAbi.WINDOWS, projected.metadata.osAbi)
        }

        @Test
        fun `ObjectFile arch is x86_64 for AMD64 COFF`() {
            val projected = PeReader.toObjectFile(roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            )))
            assertEquals(ArchType.X86_64, projected.arch.arch)
        }

        @Test
        fun `symbol binding projected correctly for global`() {
            val projected = PeReader.toObjectFile(roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(Symbol("fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            )))
            val sym = projected.symbols.first { it.name == "fn" }
            assertEquals(SymbolBinding.GLOBAL, sym.binding)
        }

        @Test
        fun `symbol binding projected correctly for local`() {
            val projected = PeReader.toObjectFile(roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(Symbol("helper", value = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION)),
            )))
            val sym = projected.symbols.first { it.name == "helper" }
            assertEquals(SymbolBinding.LOCAL, sym.binding)
        }

        @Test
        fun `undefined symbol projected as UNDEFINED kind`() {
            val projected = PeReader.toObjectFile(roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(Symbol("extern_fn", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            )))
            val sym = projected.symbols.first { it.name == "extern_fn" }
            assertEquals(SymbolKind.UNDEFINED, sym.kind)
        }
    }

    @Nested
    inner class PeOptionalHeaderTests {

        @Test
        fun `PE32Plus magic is 0x20B`() {
            val pe = makePeFlat()
            assertEquals(PeConstants.PE32PLUS_MAGIC, pe.optionalHeader!!.magic)
        }

        @Test
        fun `image base is positive`() {
            val pe = makePeFlat()
            assertTrue(pe.optionalHeader!!.imageBase > 0)
        }

        @Test
        fun `image base is 0x140000000`() {
            val pe = makePeFlat()
            assertEquals(0x140000000L, pe.optionalHeader!!.imageBase)
        }

        @Test
        fun `section alignment is 0x1000`() {
            val pe = makePeFlat()
            assertEquals(0x1000, pe.optionalHeader!!.sectionAlignment)
        }

        @Test
        fun `file alignment is 0x200`() {
            val pe = makePeFlat()
            assertEquals(0x200, pe.optionalHeader!!.fileAlignment)
        }

        @Test
        fun `subsystem is CONSOLE (3)`() {
            val pe = makePeFlat()
            assertEquals(3, pe.optionalHeader!!.subsystem)
        }

        @Test
        fun `size of image is positive`() {
            val pe = makePeFlat()
            assertTrue(pe.optionalHeader!!.sizeOfImage > 0)
        }

        @Test
        fun `size of headers is positive`() {
            val pe = makePeFlat()
            assertTrue(pe.optionalHeader!!.sizeOfHeaders > 0)
        }

        @Test
        fun `size of code is positive`() {
            val pe = makePeFlat()
            assertTrue(pe.optionalHeader!!.sizeOfCode > 0)
        }

        @Test
        fun `entry point RVA is in text section`() {
            val pe = makePeFlat()
            val entryRVA = pe.optionalHeader!!.entryPointRVA
            val textSection = pe.sectionByName(".text")!!
            assertTrue(entryRVA >= textSection.virtualAddress)
        }

        @Test
        fun `base of code equals text section RVA`() {
            val pe = makePeFlat()
            val textSection = pe.sectionByName(".text")!!
            assertEquals(textSection.virtualAddress, pe.optionalHeader!!.baseOfCode)
        }

        @Test
        fun `number of data directories is 16`() {
            val pe = makePeFlat()
            assertEquals(16, pe.optionalHeader!!.numberOfDataDirectories)
        }

        @Test
        fun `stack sizes are positive`() {
            val pe = makePeFlat()
            assertTrue(pe.optionalHeader!!.sizeOfStackReserve > 0)
            assertTrue(pe.optionalHeader!!.sizeOfStackCommit > 0)
        }

        @Test
        fun `heap sizes are positive`() {
            val pe = makePeFlat()
            assertTrue(pe.optionalHeader!!.sizeOfHeapReserve > 0)
            assertTrue(pe.optionalHeader!!.sizeOfHeapCommit > 0)
        }

        @Test
        fun `COFF obj has no optional header`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertNull(pe.optionalHeader)
        }
    }

    @Nested
    inner class ImportDirectoryTests {

        @Test
        fun `PeWriter imports kernel32`() {
            val pe = makePeFlat()
            assertEquals(1, pe.importDirectories.size)
            assertEquals("kernel32.dll", pe.importDirectories[0].name)
        }

        @Test
        fun `imports include ExitProcess`() {
            val pe = makePeFlat()
            val dir = pe.importDirectories.first()
            assertTrue(dir.entries.any { it.name == "ExitProcess" })
        }

        @Test
        fun `imports include GetStdHandle`() {
            val pe = makePeFlat()
            val dir = pe.importDirectories.first()
            assertTrue(dir.entries.any { it.name == "GetStdHandle" })
        }

        @Test
        fun `imports include WriteFile`() {
            val pe = makePeFlat()
            val dir = pe.importDirectories.first()
            assertTrue(dir.entries.any { it.name == "WriteFile" })
        }

        @Test
        fun `import entries have names not ordinals`() {
            val pe = makePeFlat()
            val dir = pe.importDirectories.first()
            for (entry in dir.entries) {
                assertNotNull(entry.name)
                assertFalse(entry.isOrdinal)
            }
        }

        @Test
        fun `PeLinker round-trip preserves import DLL name`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
                imports = listOf(ImportEntry(moduleName = "msvcrt.dll", symbolName = "printf")),
            )
            val linked = PeLinker().link(listOf(obj))
            val pe = PeReader.read(linked)
            assertTrue(pe.importDirectories.any { it.name == "msvcrt.dll" })
        }

        @Test
        fun `PeLinker round-trip preserves import function name`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
                imports = listOf(ImportEntry(moduleName = "msvcrt.dll", symbolName = "printf")),
            )
            val linked = PeLinker().link(listOf(obj))
            val pe = PeReader.read(linked)
            val msvcrt = pe.importDirectories.first { it.name == "msvcrt.dll" }
            assertTrue(msvcrt.entries.any { it.name == "printf" })
        }

        @Test
        fun `ObjectFile projection includes imports`() {
            val pe = makePeFlat()
            val projected = PeReader.toObjectFile(pe)
            assertTrue(projected.imports.any { it.moduleName == "kernel32.dll" })
        }

        @Test
        fun `COFF obj has no imports`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertTrue(pe.importDirectories.isEmpty())
        }
    }

    @Nested
    inner class ExportDirectoryTests {

        @Test
        fun `DLL linker produces export directory`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("myExport", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            )
            val dll = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val pe = PeReader.read(dll)
            assertNotNull(pe.exportDirectory)
        }

        @Test
        fun `export directory contains exported function`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("myExport", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            )
            val dll = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val pe = PeReader.read(dll)
            assertTrue(pe.exportDirectory!!.entries.any { it.name == "myExport" })
        }

        @Test
        fun `export directory DLL name matches`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            )
            val dll = PeDllLinker(dllName = "mylib.dll").link(listOf(obj))
            val pe = PeReader.read(dll)
            assertEquals("mylib.dll", pe.exportDirectory!!.name)
        }

        @Test
        fun `exported entries have ordinals`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            )
            val dll = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val pe = PeReader.read(dll)
            assertTrue(pe.exportDirectory!!.entries.all { it.ordinal >= 0 })
        }

        @Test
        fun `multiple exports are preserved`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
                symbols = listOf(
                    Symbol("add", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("sub", value = 8, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("mul", value = 16, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val dll = PeDllLinker(dllName = "math.dll").link(listOf(obj))
            val pe = PeReader.read(dll)
            val names = pe.exportDirectory!!.entries.mapNotNull { it.name }
            assertTrue("add" in names)
            assertTrue("sub" in names)
            assertTrue("mul" in names)
        }

        @Test
        fun `ObjectFile projection includes exports`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            )
            val dll = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val pe = PeReader.read(dll)
            val projected = PeReader.toObjectFile(pe)
            assertTrue(projected.exports.any { it.symbolName == "fn" })
        }

        @Test
        fun `native PE without exports has null export directory`() {
            val pe = makePeFlat()
            assertNull(pe.exportDirectory)
        }
    }

    @Nested
    inner class DataDirectories {

        @Test
        fun `PE has at least 2 data directories`() {
            val pe = makePeFlat()
            assertTrue(pe.dataDirectories.size >= 2)
        }

        @Test
        fun `import data directory is not empty`() {
            val pe = makePeFlat()
            assertFalse(pe.dataDirectories[PeDataDirectory.IMPORT].isEmpty)
        }

        @Test
        fun `IAT data directory is not empty`() {
            val pe = makePeFlat()
            assertFalse(pe.dataDirectories[PeDataDirectory.IAT].isEmpty)
        }

        @Test
        fun `export data directory is empty for exe`() {
            val pe = makePeFlat()
            assertTrue(pe.dataDirectories[PeDataDirectory.EXPORT].isEmpty)
        }

        @Test
        fun `CLR runtime data directory is empty for native exe`() {
            val pe = makePeFlat()
            assertTrue(pe.dataDirectories[PeDataDirectory.CLR_RUNTIME].isEmpty)
        }

        @Test
        fun `resource data directory is empty for minimal exe`() {
            val pe = makePeFlat()
            assertTrue(pe.dataDirectories[PeDataDirectory.RESOURCE].isEmpty)
        }

        @Test
        fun `16 data directories are parsed`() {
            val pe = makePeFlat()
            assertEquals(16, pe.dataDirectories.size)
        }

        @Test
        fun `data directory constants have correct indices`() {
            assertEquals(0, PeDataDirectory.EXPORT)
            assertEquals(1, PeDataDirectory.IMPORT)
            assertEquals(2, PeDataDirectory.RESOURCE)
            assertEquals(3, PeDataDirectory.EXCEPTION)
            assertEquals(4, PeDataDirectory.CERTIFICATE)
            assertEquals(5, PeDataDirectory.BASE_RELOCATION)
            assertEquals(6, PeDataDirectory.DEBUG)
            assertEquals(7, PeDataDirectory.ARCHITECTURE)
            assertEquals(8, PeDataDirectory.GLOBAL_PTR)
            assertEquals(9, PeDataDirectory.TLS)
            assertEquals(10, PeDataDirectory.LOAD_CONFIG)
            assertEquals(11, PeDataDirectory.BOUND_IMPORT)
            assertEquals(12, PeDataDirectory.IAT)
            assertEquals(13, PeDataDirectory.DELAY_IMPORT)
            assertEquals(14, PeDataDirectory.CLR_RUNTIME)
        }

        @Test
        fun `DLL has export data directory`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            )
            val dll = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val pe = PeReader.read(dll)
            assertFalse(pe.dataDirectories[PeDataDirectory.EXPORT].isEmpty)
        }
    }

    @Nested
    inner class SectionAlignmentAndFileAlignment {

        @Test
        fun `section alignment is power of 2`() {
            val pe = makePeFlat()
            val align = pe.optionalHeader!!.sectionAlignment
            assertTrue(align > 0 && (align and (align - 1)) == 0)
        }

        @Test
        fun `file alignment is power of 2`() {
            val pe = makePeFlat()
            val align = pe.optionalHeader!!.fileAlignment
            assertTrue(align > 0 && (align and (align - 1)) == 0)
        }

        @Test
        fun `section alignment is greater than or equal to file alignment`() {
            val pe = makePeFlat()
            assertTrue(pe.optionalHeader!!.sectionAlignment >= pe.optionalHeader!!.fileAlignment)
        }

        @Test
        fun `text section RVA is aligned to section alignment`() {
            val pe = makePeFlat()
            val text = pe.sectionByName(".text")!!
            val sa = pe.optionalHeader!!.sectionAlignment
            assertEquals(0, text.virtualAddress % sa)
        }

        @Test
        fun `raw data offset is aligned to file alignment`() {
            val pe = makePeFlat()
            val text = pe.sectionByName(".text")!!
            val fa = pe.optionalHeader!!.fileAlignment
            assertEquals(0, text.rawDataOffset % fa)
        }

        @Test
        fun `raw data size is aligned to file alignment`() {
            val pe = makePeFlat()
            val text = pe.sectionByName(".text")!!
            val fa = pe.optionalHeader!!.fileAlignment
            assertEquals(0, text.rawDataSize % fa)
        }

        @Test
        fun `size of headers is aligned to file alignment`() {
            val pe = makePeFlat()
            val fa = pe.optionalHeader!!.fileAlignment
            assertEquals(0, pe.optionalHeader!!.sizeOfHeaders % fa)
        }

        @Test
        fun `size of image is aligned to section alignment`() {
            val pe = makePeFlat()
            val sa = pe.optionalHeader!!.sectionAlignment
            assertEquals(0, pe.optionalHeader!!.sizeOfImage % sa)
        }
    }

    @Nested
    inner class PeCharacteristicsFlags {

        @Test
        fun `PE exe has EXECUTABLE_IMAGE flag`() {
            val pe = makePeFlat()
            assertTrue(pe.isExecutable)
        }

        @Test
        fun `PE exe is not a DLL`() {
            val pe = makePeFlat()
            assertFalse(pe.isDll)
        }

        @Test
        fun `PE exe has LARGE_ADDRESS_AWARE flag`() {
            val pe = makePeFlat()
            assertTrue(pe.coffHeader.characteristics and PeConstants.IMAGE_FILE_LARGE_ADDRESS_AWARE != 0)
        }

        @Test
        fun `DLL has DLL flag`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            )
            val dll = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val pe = PeReader.read(dll)
            assertTrue(pe.isDll)
        }

        @Test
        fun `DLL has EXECUTABLE_IMAGE flag`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            )
            val dll = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val pe = PeReader.read(dll)
            assertTrue(pe.isExecutable)
        }

        @Test
        fun `ObjectFile projection has EXECUTABLE flag for PE exe`() {
            val pe = makePeFlat()
            val projected = PeReader.toObjectFile(pe)
            assertTrue(projected.metadata.flags.contains(ObjectFlag.EXECUTABLE))
        }

        @Test
        fun `ObjectFile projection has SHARED_LIBRARY flag for DLL`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            )
            val dll = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val pe = PeReader.read(dll)
            val projected = PeReader.toObjectFile(pe)
            assertTrue(projected.metadata.flags.contains(ObjectFlag.SHARED_LIBRARY))
        }
    }

    @Nested
    inner class MultipleSectionsWithVariousFlags {

        @Test
        fun `sections preserve ordering`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1),
                    Section(".rdata", SectionKind.RODATA, byteArrayOf(0), align = 1),
                    Section(".bss", SectionKind.BSS, ByteArray(8), align = 1),
                ),
            ))
            assertEquals(".text", pe.sections[0].name.trim('\u0000'))
            assertEquals(".data", pe.sections[1].name.trim('\u0000'))
            assertEquals(".rdata", pe.sections[2].name.trim('\u0000'))
            assertEquals(".bss", pe.sections[3].name.trim('\u0000'))
        }

        @Test
        fun `each section has correct kind-based characteristics`() {
            val pe = roundTrip(makeCoffObject(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1),
                    Section(".rdata", SectionKind.RODATA, byteArrayOf(0), align = 1),
                    Section(".bss", SectionKind.BSS, ByteArray(8), align = 1),
                ),
            ))
            assertTrue(pe.sections[0].isCode && pe.sections[0].isExecutable)
            assertTrue(pe.sections[1].isInitializedData && pe.sections[1].isWritable)
            assertTrue(pe.sections[2].isInitializedData && !pe.sections[2].isWritable)
            assertTrue(pe.sections[3].isUninitializedData && pe.sections[3].isWritable)
        }

        @Test
        fun `PE sections include text and idata`() {
            val pe = makePeFlat()
            assertTrue(pe.sections.any { it.name == ".text" })
            assertTrue(pe.sections.any { it.name == ".idata" })
        }

        @Test
        fun `PE with rodata has rdata section`() {
            val pe = makePeFlat(rodata = "hello".toByteArray())
            assertTrue(pe.sections.any { it.name == ".rdata" })
        }

        @Test
        fun `idata section has read and write characteristics`() {
            val pe = makePeFlat()
            val idata = pe.sectionByName(".idata")!!
            assertTrue(idata.isReadable)
        }

        @Test
        fun `sectionByName finds existing section`() {
            val pe = makePeFlat()
            assertNotNull(pe.sectionByName(".text"))
        }

        @Test
        fun `sectionByName returns null for missing section`() {
            val pe = makePeFlat()
            assertNull(pe.sectionByName(".nonexistent"))
        }

        @Test
        fun `sectionByRVA finds text section`() {
            val pe = makePeFlat()
            val text = pe.sectionByName(".text")!!
            val found = pe.sectionByRVA(text.virtualAddress)
            assertNotNull(found)
            assertEquals(".text", found!!.name)
        }
    }

    @Nested
    inner class LargeSymbolTables {

        @Test
        fun `30 symbols round-trip`() {
            val symbols = (0 until 30).map { i ->
                Symbol("func_$i", value = i.toLong() * 4, size = 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            }
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(120), align = 16)),
                symbols = symbols,
            ))
            for (i in 0 until 30) {
                assertNotNull(pe.symbols.firstOrNull { it.name == "func_$i" }, "Missing func_$i")
            }
        }

        @Test
        fun `50 symbols round-trip with long names`() {
            val symbols = (0 until 50).map { i ->
                Symbol("very_long_function_name_$i", value = i.toLong() * 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            }
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(200), align = 16)),
                symbols = symbols,
            ))
            for (i in 0 until 50) {
                assertNotNull(pe.symbols.firstOrNull { it.name == "very_long_function_name_$i" },
                    "Missing very_long_function_name_$i")
            }
        }

        @Test
        fun `mixed local and global symbols`() {
            val symbols = (0 until 20).map { i ->
                Symbol("sym_$i", value = i.toLong() * 4, section = ".text",
                    binding = if (i % 2 == 0) SymbolBinding.GLOBAL else SymbolBinding.LOCAL,
                    kind = SymbolKind.FUNCTION)
            }
            val pe = roundTrip(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(80), align = 16)),
                symbols = symbols,
            ))
            for (i in 0 until 20) {
                val sym = pe.symbols.firstOrNull { it.name == "sym_$i" }
                assertNotNull(sym, "Missing sym_$i")
                if (i % 2 == 0) assertTrue(sym!!.isExternal) else assertTrue(sym!!.isStatic)
            }
        }
    }

    @Nested
    inner class PeEnumValues {

        @Test
        fun `MACHINE_AMD64 is 0x8664`() {
            assertEquals(0x8664, PeConstants.MACHINE_AMD64)
        }

        @Test
        fun `MACHINE_ARM64 is 0xAA64`() {
            assertEquals(0xAA64, PeConstants.MACHINE_ARM64)
        }

        @Test
        fun `MACHINE_I386 is 0x014c`() {
            assertEquals(0x014c, PeConstants.MACHINE_I386)
        }

        @Test
        fun `MACHINE_ARM is 0x01c0`() {
            assertEquals(0x01c0, PeConstants.MACHINE_ARM)
        }

        @Test
        fun `PE32_MAGIC is 0x10B`() {
            assertEquals(0x10B, PeConstants.PE32_MAGIC)
        }

        @Test
        fun `PE32PLUS_MAGIC is 0x20B`() {
            assertEquals(0x20B, PeConstants.PE32PLUS_MAGIC)
        }

        @Test
        fun `IMAGE_FILE_EXECUTABLE_IMAGE is 0x0002`() {
            assertEquals(0x0002, PeConstants.IMAGE_FILE_EXECUTABLE_IMAGE)
        }

        @Test
        fun `IMAGE_FILE_LARGE_ADDRESS_AWARE is 0x0020`() {
            assertEquals(0x0020, PeConstants.IMAGE_FILE_LARGE_ADDRESS_AWARE)
        }

        @Test
        fun `IMAGE_FILE_DLL is 0x2000`() {
            assertEquals(0x2000, PeConstants.IMAGE_FILE_DLL)
        }

        @Test
        fun `IMAGE_SCN_CNT_CODE is 0x20`() {
            assertEquals(0x00000020, PeConstants.IMAGE_SCN_CNT_CODE)
        }

        @Test
        fun `IMAGE_SCN_CNT_INITIALIZED_DATA is 0x40`() {
            assertEquals(0x00000040, PeConstants.IMAGE_SCN_CNT_INITIALIZED_DATA)
        }

        @Test
        fun `IMAGE_SCN_CNT_UNINITIALIZED_DATA is 0x80`() {
            assertEquals(0x00000080, PeConstants.IMAGE_SCN_CNT_UNINITIALIZED_DATA)
        }

        @Test
        fun `IMAGE_SCN_MEM_EXECUTE is 0x20000000`() {
            assertEquals(0x20000000, PeConstants.IMAGE_SCN_MEM_EXECUTE)
        }

        @Test
        fun `IMAGE_SCN_MEM_READ is 0x40000000`() {
            assertEquals(0x40000000, PeConstants.IMAGE_SCN_MEM_READ)
        }

        @Test
        fun `IMAGE_SCN_MEM_WRITE is 0x80000000`() {
            assertEquals(0x80000000.toInt(), PeConstants.IMAGE_SCN_MEM_WRITE)
        }

        @Test
        fun `IMAGE_SCN_MEM_DISCARDABLE is 0x02000000`() {
            assertEquals(0x02000000, PeConstants.IMAGE_SCN_MEM_DISCARDABLE)
        }

        @Test
        fun `IMAGE_SCN_LNK_COMDAT is 0x1000`() {
            assertEquals(0x00001000, PeConstants.IMAGE_SCN_LNK_COMDAT)
        }

        @Test
        fun `IMAGE_SYM_CLASS_EXTERNAL is 2`() {
            assertEquals(2, PeConstants.IMAGE_SYM_CLASS_EXTERNAL)
        }

        @Test
        fun `IMAGE_SYM_CLASS_STATIC is 3`() {
            assertEquals(3, PeConstants.IMAGE_SYM_CLASS_STATIC)
        }

        @Test
        fun `IMAGE_SYM_CLASS_FILE is 103`() {
            assertEquals(103, PeConstants.IMAGE_SYM_CLASS_FILE)
        }

        @Test
        fun `IMAGE_SYM_CLASS_SECTION is 104`() {
            assertEquals(104, PeConstants.IMAGE_SYM_CLASS_SECTION)
        }

        @Test
        fun `IMAGE_SYM_CLASS_WEAK_EXTERNAL is 105`() {
            assertEquals(105, PeConstants.IMAGE_SYM_CLASS_WEAK_EXTERNAL)
        }

        @Test
        fun `KNOWN_MACHINES contains all machine constants`() {
            assertTrue(PeConstants.MACHINE_AMD64 in PeConstants.KNOWN_MACHINES)
            assertTrue(PeConstants.MACHINE_ARM64 in PeConstants.KNOWN_MACHINES)
            assertTrue(PeConstants.MACHINE_I386 in PeConstants.KNOWN_MACHINES)
            assertTrue(PeConstants.MACHINE_ARM in PeConstants.KNOWN_MACHINES)
        }

        @Test
        fun `COFF_X86_64 relocation type values`() {
            assertEquals(0, RelocationType.COFF_X86_64.ABSOLUTE.value)
            assertEquals(1, RelocationType.COFF_X86_64.ADDR64.value)
            assertEquals(2, RelocationType.COFF_X86_64.ADDR32.value)
            assertEquals(3, RelocationType.COFF_X86_64.ADDR32NB.value)
            assertEquals(4, RelocationType.COFF_X86_64.REL32.value)
            assertEquals(5, RelocationType.COFF_X86_64.REL32_1.value)
            assertEquals(6, RelocationType.COFF_X86_64.REL32_2.value)
            assertEquals(7, RelocationType.COFF_X86_64.REL32_3.value)
            assertEquals(8, RelocationType.COFF_X86_64.REL32_4.value)
            assertEquals(9, RelocationType.COFF_X86_64.REL32_5.value)
            assertEquals(10, RelocationType.COFF_X86_64.SECTION.value)
            assertEquals(11, RelocationType.COFF_X86_64.SECREL.value)
            assertEquals(12, RelocationType.COFF_X86_64.SECREL7.value)
            assertEquals(13, RelocationType.COFF_X86_64.TOKEN.value)
            assertEquals(14, RelocationType.COFF_X86_64.SREL32.value)
            assertEquals(15, RelocationType.COFF_X86_64.PAIR.value)
            assertEquals(16, RelocationType.COFF_X86_64.SSPAN32.value)
        }

        @Test
        fun `COFF_X86_64 relocation names`() {
            assertEquals("IMAGE_REL_AMD64_REL32", RelocationType.COFF_X86_64.REL32.relocName)
            assertEquals("IMAGE_REL_AMD64_ADDR64", RelocationType.COFF_X86_64.ADDR64.relocName)
        }

        @Test
        fun `COFF_ARM64 relocation type values`() {
            assertEquals(0, RelocationType.COFF_ARM64.ABSOLUTE.value)
            assertEquals(1, RelocationType.COFF_ARM64.ADDR32.value)
            assertEquals(2, RelocationType.COFF_ARM64.ADDR32NB.value)
            assertEquals(3, RelocationType.COFF_ARM64.BRANCH26.value)
            assertEquals(4, RelocationType.COFF_ARM64.PAGEBASE_REL21.value)
            assertEquals(14, RelocationType.COFF_ARM64.ADDR64.value)
        }

        @Test
        fun `COFF_ARM64 relocation names`() {
            assertEquals("IMAGE_REL_ARM64_BRANCH26", RelocationType.COFF_ARM64.BRANCH26.relocName)
            assertEquals("IMAGE_REL_ARM64_ADDR64", RelocationType.COFF_ARM64.ADDR64.relocName)
        }

        @Test
        fun `base relocation entry type constants`() {
            assertEquals(0, PeBaseRelocationEntry.IMAGE_REL_BASED_ABSOLUTE)
            assertEquals(1, PeBaseRelocationEntry.IMAGE_REL_BASED_HIGH)
            assertEquals(2, PeBaseRelocationEntry.IMAGE_REL_BASED_LOW)
            assertEquals(3, PeBaseRelocationEntry.IMAGE_REL_BASED_HIGHLOW)
            assertEquals(10, PeBaseRelocationEntry.IMAGE_REL_BASED_DIR64)
        }
    }

    @Nested
    inner class PeReaderCanRead {

        @Test
        fun `canRead detects PE files`() {
            val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
            assertTrue(PeReader.canRead(bytes))
        }

        @Test
        fun `canRead detects COFF objects`() {
            val bytes = writeCoff(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertTrue(PeReader.canRead(bytes))
        }

        @Test
        fun `canRead rejects too-short data`() {
            assertFalse(PeReader.canRead(byteArrayOf(0x00)))
        }

        @Test
        fun `canRead rejects unknown machine type`() {
            assertFalse(PeReader.canRead(byteArrayOf(0x00, 0x00)))
        }

        @Test
        fun `canRead detects MZ header`() {
            assertTrue(PeReader.canRead(byteArrayOf(0x4d, 0x5a, 0x00, 0x00)))
        }
    }

    @Nested
    inner class PeFileProperties {

        @Test
        fun `isPe32Plus is true for PE32+ files`() {
            val pe = makePeFlat()
            assertTrue(pe.isPe32Plus)
        }

        @Test
        fun `isManagedAssembly is false for native PE`() {
            val pe = makePeFlat()
            assertFalse(pe.isManagedAssembly)
        }

        @Test
        fun `clrMetadata is null for native PE`() {
            val pe = makePeFlat()
            assertNull(pe.clrMetadata)
        }

        @Test
        fun `imageBase returns correct value`() {
            val pe = makePeFlat()
            assertEquals(0x140000000L, pe.imageBase)
        }

        @Test
        fun `entryPointRVA is positive for PE exe`() {
            val pe = makePeFlat()
            assertTrue(pe.entryPointRVA > 0)
        }

        @Test
        fun `base relocations are empty for minimal PE`() {
            val pe = makePeFlat()
            assertTrue(pe.baseRelocations.isEmpty())
        }

        @Test
        fun `delay import directories are empty for minimal PE`() {
            val pe = makePeFlat()
            assertTrue(pe.delayImportDirectories.isEmpty())
        }

        @Test
        fun `resources are null for minimal PE`() {
            val pe = makePeFlat()
            assertNull(pe.resources)
        }

        @Test
        fun `TLS directory is null for minimal PE`() {
            val pe = makePeFlat()
            assertNull(pe.tlsDirectory)
        }
    }

    @Nested
    inner class CoffSymbolProperties {

        @Test
        fun `isFunction checks type bit 0x20`() {
            val sym = CoffSymbol("fn", 0, 1, 0x20, PeConstants.IMAGE_SYM_CLASS_EXTERNAL, 0)
            assertTrue(sym.isFunction)
        }

        @Test
        fun `isFunction is false for type 0`() {
            val sym = CoffSymbol("data", 0, 1, 0, PeConstants.IMAGE_SYM_CLASS_EXTERNAL, 0)
            assertFalse(sym.isFunction)
        }

        @Test
        fun `isExternal checks storage class`() {
            val sym = CoffSymbol("fn", 0, 1, 0, PeConstants.IMAGE_SYM_CLASS_EXTERNAL, 0)
            assertTrue(sym.isExternal)
        }

        @Test
        fun `isStatic checks storage class`() {
            val sym = CoffSymbol("fn", 0, 1, 0, PeConstants.IMAGE_SYM_CLASS_STATIC, 0)
            assertTrue(sym.isStatic)
        }

        @Test
        fun `isUndefined checks section number 0`() {
            val sym = CoffSymbol("ext", 0, 0, 0, PeConstants.IMAGE_SYM_CLASS_EXTERNAL, 0)
            assertTrue(sym.isUndefined)
        }

        @Test
        fun `isAbsolute checks section number -1`() {
            val sym = CoffSymbol("abs", 42, -1, 0, PeConstants.IMAGE_SYM_CLASS_EXTERNAL, 0)
            assertTrue(sym.isAbsolute)
        }

        @Test
        fun `isUndefined is false for defined symbol`() {
            val sym = CoffSymbol("fn", 0, 1, 0, PeConstants.IMAGE_SYM_CLASS_EXTERNAL, 0)
            assertFalse(sym.isUndefined)
        }

        @Test
        fun `isAbsolute is false for normal symbol`() {
            val sym = CoffSymbol("fn", 0, 1, 0, PeConstants.IMAGE_SYM_CLASS_EXTERNAL, 0)
            assertFalse(sym.isAbsolute)
        }
    }

    @Nested
    inner class PeSectionProperties {

        @Test
        fun `isCode checks CNT_CODE flag`() {
            val sec = PeSection(".text", 0, 0, 0, 0, 0, 0,
                PeConstants.IMAGE_SCN_CNT_CODE, ByteArray(0))
            assertTrue(sec.isCode)
        }

        @Test
        fun `isInitializedData checks flag`() {
            val sec = PeSection(".data", 0, 0, 0, 0, 0, 0,
                PeConstants.IMAGE_SCN_CNT_INITIALIZED_DATA, ByteArray(0))
            assertTrue(sec.isInitializedData)
        }

        @Test
        fun `isUninitializedData checks flag`() {
            val sec = PeSection(".bss", 0, 0, 0, 0, 0, 0,
                PeConstants.IMAGE_SCN_CNT_UNINITIALIZED_DATA, ByteArray(0))
            assertTrue(sec.isUninitializedData)
        }

        @Test
        fun `isReadable checks MEM_READ flag`() {
            val sec = PeSection(".text", 0, 0, 0, 0, 0, 0,
                PeConstants.IMAGE_SCN_MEM_READ, ByteArray(0))
            assertTrue(sec.isReadable)
        }

        @Test
        fun `isWritable checks MEM_WRITE flag`() {
            val sec = PeSection(".data", 0, 0, 0, 0, 0, 0,
                PeConstants.IMAGE_SCN_MEM_WRITE, ByteArray(0))
            assertTrue(sec.isWritable)
        }

        @Test
        fun `isExecutable checks MEM_EXECUTE flag`() {
            val sec = PeSection(".text", 0, 0, 0, 0, 0, 0,
                PeConstants.IMAGE_SCN_MEM_EXECUTE, ByteArray(0))
            assertTrue(sec.isExecutable)
        }

        @Test
        fun `isDiscardable checks MEM_DISCARDABLE flag`() {
            val sec = PeSection(".debug", 0, 0, 0, 0, 0, 0,
                PeConstants.IMAGE_SCN_MEM_DISCARDABLE, ByteArray(0))
            assertTrue(sec.isDiscardable)
        }

        @Test
        fun `combined flags are correct`() {
            val chars = PeConstants.IMAGE_SCN_CNT_CODE or
                PeConstants.IMAGE_SCN_MEM_EXECUTE or PeConstants.IMAGE_SCN_MEM_READ
            val sec = PeSection(".text", 0, 0, 0, 0, 0, 0, chars, ByteArray(0))
            assertTrue(sec.isCode)
            assertTrue(sec.isExecutable)
            assertTrue(sec.isReadable)
            assertFalse(sec.isWritable)
            assertFalse(sec.isDiscardable)
        }
    }

    @Nested
    inner class PeDataDirectoryModel {

        @Test
        fun `isEmpty is true for zero RVA and size`() {
            val dd = PeDataDirectory(0, 0)
            assertTrue(dd.isEmpty)
        }

        @Test
        fun `isEmpty is false for non-zero RVA`() {
            val dd = PeDataDirectory(0x1000, 100)
            assertFalse(dd.isEmpty)
        }

        @Test
        fun `isEmpty is false for non-zero size with zero RVA`() {
            val dd = PeDataDirectory(0, 100)
            assertFalse(dd.isEmpty)
        }
    }

    @Nested
    inner class PeLinkerIntegration {

        @Test
        fun `linked PE is a valid PE file`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
                imports = listOf(ImportEntry(moduleName = "kernel32.dll", symbolName = "ExitProcess")),
            )
            val linked = PeLinker().link(listOf(obj))
            assertTrue(PeReader.canRead(linked))
            val pe = PeReader.read(linked)
            assertTrue(pe.isPe)
            assertTrue(pe.isExecutable)
        }

        @Test
        fun `linked PE has text section`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
                imports = listOf(ImportEntry(moduleName = "kernel32.dll", symbolName = "ExitProcess")),
            )
            val pe = PeReader.read(PeLinker().link(listOf(obj)))
            assertNotNull(pe.sectionByName(".text"))
        }

        @Test
        fun `linked PE has idata section for imports`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
                symbols = listOf(Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
                imports = listOf(ImportEntry(moduleName = "kernel32.dll", symbolName = "ExitProcess")),
            )
            val pe = PeReader.read(PeLinker().link(listOf(obj)))
            assertNotNull(pe.sectionByName(".idata"))
        }

        @Test
        fun `linked PE with rodata has rdata section`() {
            val obj = makeCoffObject(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                    Section(".rodata", SectionKind.RODATA, "test\u0000".toByteArray(), align = 1),
                ),
                symbols = listOf(Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
                imports = listOf(ImportEntry(moduleName = "kernel32.dll", symbolName = "ExitProcess")),
            )
            val pe = PeReader.read(PeLinker().link(listOf(obj)))
            assertNotNull(pe.sectionByName(".rdata"))
        }
    }

    @Nested
    inner class PeWriterUtilities {

        @Test
        fun `sectionAlignment returns 0x1000`() {
            assertEquals(0x1000, PeWriter.sectionAlignment())
        }

        @Test
        fun `imageBase returns expected value`() {
            assertEquals(0x140000000L, PeWriter.imageBase())
        }

        @Test
        fun `iatEntryRVAs returns 3 entries`() {
            val rvas = PeWriter.iatEntryRVAs(1)
            assertEquals(3, rvas.size)
        }

        @Test
        fun `iatEntryRVAs are sequential 8-byte-apart`() {
            val rvas = PeWriter.iatEntryRVAs(1)
            assertEquals(rvas[0] + 8, rvas[1])
            assertEquals(rvas[1] + 8, rvas[2])
        }

        @Test
        fun `iatEntryRVAs shift with rodata size`() {
            val rvasNoRodata = PeWriter.iatEntryRVAs(100)
            val rvasWithRodata = PeWriter.iatEntryRVAs(100, 200)
            assertTrue(rvasWithRodata[0] > rvasNoRodata[0])
        }

        @Test
        fun `writeFlat produces valid PE`() {
            val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
            assertTrue(bytes.size > 512)
            assertEquals('M'.code.toByte(), bytes[0])
            assertEquals('Z'.code.toByte(), bytes[1])
        }

        @Test
        fun `write via ObjectFileWriter interface`() {
            val obj = makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 1)),
            )
            val bytes = PeWriter.write(obj)
            assertTrue(PeReader.canRead(bytes))
        }

        @Test
        fun `supportsArchitecture for x86_64`() {
            assertTrue(PeWriter.supportsArchitecture(ArchType.X86_64))
        }

        @Test
        fun `supportsArchitecture for aarch64`() {
            assertTrue(PeWriter.supportsArchitecture(ArchType.AARCH64))
        }

        @Test
        fun `format is PE_COFF`() {
            assertEquals(ObjectFormat.PE_COFF, PeWriter.format)
        }
    }

    @Nested
    inner class CoffObjectWriterMachineTypes {

        @Test
        fun `default machine is AMD64`() {
            val bytes = CoffObjectWriter().write(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            ))
            assertEquals(0x64, bytes[0].toInt() and 0xFF)
            assertEquals(0x86.toByte(), bytes[1])
        }

        @Test
        fun `ARM64 machine type in header`() {
            val bytes = CoffObjectWriter(machine = PeConstants.MACHINE_ARM64).write(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0x00), align = 1)),
            ))
            val machine = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
            assertEquals(PeConstants.MACHINE_ARM64, machine)
        }

        @Test
        fun `I386 machine type in header`() {
            val bytes = CoffObjectWriter(machine = PeConstants.MACHINE_I386).write(makeCoffObject(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0x00), align = 1)),
            ))
            val machine = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
            assertEquals(PeConstants.MACHINE_I386, machine)
        }
    }
}
