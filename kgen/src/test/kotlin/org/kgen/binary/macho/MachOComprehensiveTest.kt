package org.kgen.binary.macho

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MachOComprehensiveTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun makeObjectFile(
        arch: Architecture = Architecture(ArchType.X86_64),
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
    ) = ObjectFile(
        format = ObjectFormat.MACH_O,
        arch = arch,
        sections = sections,
        symbols = symbols,
        relocations = relocations,
    )

    private fun writeAndRead(
        obj: ObjectFile,
        cpuType: Int = MachO.CPU_TYPE_X86_64,
        cpuSubtype: Int = MachO.CPU_SUBTYPE_ALL,
    ): MachOFile {
        val bytes = MachOObjectWriter(cpuType, cpuSubtype).write(obj)
        return MachOReader.read(bytes)
    }

    private fun writeAndProject(
        obj: ObjectFile,
        cpuType: Int = MachO.CPU_TYPE_X86_64,
        cpuSubtype: Int = MachO.CPU_SUBTYPE_ALL,
    ): ObjectFile {
        val bytes = MachOObjectWriter(cpuType, cpuSubtype).write(obj)
        return MachOReader.toObjectFile(MachOReader.read(bytes))
    }

    @Nested
    inner class HeaderFields {

        @Test
        fun `header magic is MH_MAGIC_64`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val bytes = MachOObjectWriter().write(obj)
            val buf = le(bytes)
            assertEquals(MachO.MH_MAGIC_64.toInt(), buf.getInt(0))
        }

        @Test
        fun `header cputype is X86_64 by default`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(MachO.CPU_TYPE_X86_64, macho.header.cpuType)
        }

        @Test
        fun `header cpusubtype is ALL by default`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(MachO.CPU_SUBTYPE_ALL, macho.header.cpuSubtype)
        }

        @Test
        fun `header filetype is MH_OBJECT`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(MachO.MH_OBJECT, macho.header.fileType)
        }

        @Test
        fun `header is64Bit returns true`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.header.is64Bit)
        }

        @Test
        fun `header ncmds is 2 for segment and symtab`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(2, macho.header.numberOfCommands)
        }

        @Test
        fun `header sizeOfCommands is positive`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.header.sizeOfCommands > 0)
        }

        @Test
        fun `header flags is zero for basic object`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(0, macho.header.flags)
        }

        @Test
        fun `isObject returns true for MH_OBJECT`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.isObject)
        }

        @Test
        fun `isExecutable returns false for MH_OBJECT`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertFalse(macho.isExecutable)
        }

        @Test
        fun `isDylib returns false for MH_OBJECT`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertFalse(macho.isDylib)
        }

        @Test
        fun `isBigEndian returns false for little-endian output`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertFalse(macho.header.isBigEndian)
        }

        @Test
        fun `empty object file still has valid header`() {
            val obj = makeObjectFile()
            val bytes = MachOObjectWriter().write(obj)
            val buf = le(bytes)
            assertEquals(MachO.MH_MAGIC_64.toInt(), buf.getInt(0))
            assertEquals(MachO.MH_OBJECT, buf.getInt(12))
        }

        @Test
        fun `header size is at least 32 bytes for 64-bit`() {
            val obj = makeObjectFile()
            val bytes = MachOObjectWriter().write(obj)
            assertTrue(bytes.size >= 32)
        }
    }

    @Nested
    inner class LoadCommands {

        @Test
        fun `LC_SEGMENT_64 present with one section`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(1, macho.segments.size)
        }

        @Test
        fun `LC_SEGMENT_64 contains correct number of sections`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1),
                )
            )
            val macho = writeAndRead(obj)
            assertEquals(2, macho.allSections.size)
        }

        @Test
        fun `LC_SEGMENT_64 has correct section count for three sections`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 4),
                    Section(".rodata", SectionKind.RODATA, byteArrayOf(0x01), align = 1),
                )
            )
            val macho = writeAndRead(obj)
            assertEquals(3, macho.allSections.size)
        }

        @Test
        fun `LC_SYMTAB present even with no symbols`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(2, macho.header.numberOfCommands)
        }

        @Test
        fun `segment vmsize is positive for non-empty sections`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 16))
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.segments[0].vmSize > 0)
        }

        @Test
        fun `segment fileOffset points past header and load commands`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.segments[0].fileOffset > 0)
        }

        @Test
        fun `segment protection includes read and execute`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.segments[0].maxProtection and 5 != 0) // r-x at minimum
        }

        @Test
        fun `segment name is empty for object files`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals("", macho.segments[0].name)
        }
    }

    @Nested
    inner class TextSection {

        @Test
        fun `text section name is __text`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals("__text", macho.allSections[0].sectionName)
        }

        @Test
        fun `text section segment is __TEXT`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals("__TEXT", macho.allSections[0].segmentName)
        }

        @Test
        fun `text section has pure instructions flag`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.allSections[0].isPureInstructions)
        }

        @Test
        fun `text section has S_ATTR_SOME_INSTRUCTIONS`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            val attrs = macho.allSections[0].attributes
            assertTrue(attrs and MachO.S_ATTR_SOME_INSTRUCTIONS != 0)
        }

        @Test
        fun `text section data matches input`() {
            val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 1))
            )
            val macho = writeAndRead(obj)
            assertArrayEquals(code, macho.allSections[0].data)
        }

        @Test
        fun `text section type is S_REGULAR`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(MachO.S_REGULAR, macho.allSections[0].type)
        }

        @Test
        fun `text section preserves large code block`() {
            val code = ByteArray(4096) { (it % 256).toByte() }
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16))
            )
            val macho = writeAndRead(obj)
            assertArrayEquals(code, macho.allSections.first { it.sectionName == "__text" }.data)
        }
    }

    @Nested
    inner class DataSection {

        @Test
        fun `data section name is __data`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals("__data", macho.allSections[0].sectionName)
        }

        @Test
        fun `data section segment is __DATA`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals("__DATA", macho.allSections[0].segmentName)
        }

        @Test
        fun `data section does not have pure instructions`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1))
            )
            val macho = writeAndRead(obj)
            assertFalse(macho.allSections[0].isPureInstructions)
        }

        @Test
        fun `data section data matches input`() {
            val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
            val obj = makeObjectFile(
                sections = listOf(Section(".data", SectionKind.DATA, data, align = 8))
            )
            val macho = writeAndRead(obj)
            assertArrayEquals(data, macho.allSections[0].data)
        }

        @Test
        fun `data section type is S_REGULAR`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(MachO.S_REGULAR, macho.allSections[0].type)
        }
    }

    @Nested
    inner class ConstSection {

        @Test
        fun `rodata maps to __const`() {
            val rodata = "Hello\u0000".toByteArray(Charsets.US_ASCII)
            val obj = makeObjectFile(
                sections = listOf(Section(".rodata", SectionKind.RODATA, rodata, align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals("__const", macho.allSections[0].sectionName)
        }

        @Test
        fun `rodata goes in __TEXT segment`() {
            val rodata = byteArrayOf(0x01, 0x02)
            val obj = makeObjectFile(
                sections = listOf(Section(".rodata", SectionKind.RODATA, rodata, align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals("__TEXT", macho.allSections[0].segmentName)
        }

        @Test
        fun `rodata does not have pure instructions`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".rodata", SectionKind.RODATA, byteArrayOf(0x01), align = 1))
            )
            val macho = writeAndRead(obj)
            assertFalse(macho.allSections[0].isPureInstructions)
        }

        @Test
        fun `rodata preserves content`() {
            val content = "Constant string data\u0000".toByteArray(Charsets.US_ASCII)
            val obj = makeObjectFile(
                sections = listOf(Section(".rodata", SectionKind.RODATA, content, align = 1))
            )
            val macho = writeAndRead(obj)
            assertArrayEquals(content, macho.allSections[0].data)
        }
    }

    @Nested
    inner class BssSection {

        @Test
        fun `BSS section maps to __bss`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(64), align = 8))
            )
            val macho = writeAndRead(obj)
            assertEquals("__bss", macho.allSections[0].sectionName)
        }

        @Test
        fun `BSS section goes in __DATA segment`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(32), align = 4))
            )
            val macho = writeAndRead(obj)
            assertEquals("__DATA", macho.allSections[0].segmentName)
        }

        @Test
        fun `BSS section has S_ZEROFILL type`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(16), align = 4))
            )
            val macho = writeAndRead(obj)
            assertEquals(MachO.S_ZEROFILL, macho.allSections[0].type)
        }

        @Test
        fun `BSS section data is all zeros`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(128), align = 16))
            )
            val macho = writeAndRead(obj)
            val data = macho.allSections[0].data
            assertTrue(data.all { it == 0.toByte() })
        }

        @Test
        fun `BSS section size matches`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(256), align = 8))
            )
            val macho = writeAndRead(obj)
            assertEquals(256L, macho.allSections[0].size)
        }

        @Test
        fun `BSS section does not have pure instructions`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(8), align = 4))
            )
            val macho = writeAndRead(obj)
            assertFalse(macho.allSections[0].isPureInstructions)
        }
    }

    @Nested
    inner class SymbolTableLocal {

        @Test
        fun `local symbol is not external`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(
                    Symbol("_helper", value = 0, section = ".text",
                        binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertFalse(macho.symbols[0].isExternal)
        }

        @Test
        fun `local symbol is in section`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(
                    Symbol("_local", value = 0, section = ".text",
                        binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.symbols[0].isInSection)
        }

        @Test
        fun `local symbol sectionIndex is 1 for first section`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(
                    Symbol("_local", value = 0, section = ".text",
                        binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(1, macho.symbols[0].sectionIndex)
        }

        @Test
        fun `local symbol preserves value`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 1)),
                symbols = listOf(
                    Symbol("_local", value = 42, section = ".text",
                        binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(42L, macho.symbols[0].value)
        }

        @Test
        fun `local symbol name preserved`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(
                    Symbol("_my_local_func", value = 0, section = ".text",
                        binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals("_my_local_func", macho.symbols[0].name)
        }
    }

    @Nested
    inner class SymbolTableGlobal {

        @Test
        fun `global symbol is external`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(
                    Symbol("_main", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.symbols[0].isExternal)
        }

        @Test
        fun `global symbol is N_SECT`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(
                    Symbol("_main", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.symbols[0].isInSection)
        }

        @Test
        fun `global symbol is not undefined`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(
                    Symbol("_main", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertFalse(macho.symbols[0].isUndefined)
        }

        @Test
        fun `global symbol value at offset`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 1)),
                symbols = listOf(
                    Symbol("_func", value = 32, size = 16, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(32L, macho.symbols[0].value)
        }

        @Test
        fun `weak symbol is external`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(
                    Symbol("_weak", value = 0, section = ".text",
                        binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.symbols[0].isExternal)
        }
    }

    @Nested
    inner class SymbolTableUndefined {

        @Test
        fun `undefined symbol is external`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(
                    Symbol("_printf", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.symbols[0].isExternal)
        }

        @Test
        fun `undefined symbol is N_UNDF`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(
                    Symbol("_printf", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.symbols[0].isUndefined)
        }

        @Test
        fun `undefined symbol sectionIndex is 0`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(
                    Symbol("_puts", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(0, macho.symbols[0].sectionIndex)
        }

        @Test
        fun `undefined symbol value is zero`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(
                    Symbol("_malloc", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(0L, macho.symbols[0].value)
        }

        @Test
        fun `undefined symbol is not in section`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(
                    Symbol("_free", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
            )
            val macho = writeAndRead(obj)
            assertFalse(macho.symbols[0].isInSection)
        }
    }

    @Nested
    inner class StringTable {

        @Test
        fun `symbol names round-trip correctly`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 1)),
                symbols = listOf(
                    Symbol("_alpha", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("_beta", value = 8, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("_gamma", value = 16, section = ".text",
                        binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(3, macho.symbols.size)
            assertEquals("_alpha", macho.symbols[0].name)
            assertEquals("_beta", macho.symbols[1].name)
            assertEquals("_gamma", macho.symbols[2].name)
        }

        @Test
        fun `long symbol names preserved`() {
            val longName = "_this_is_a_very_long_symbol_name_for_testing_purposes_1234567890"
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(
                    Symbol(longName, value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(longName, macho.symbols[0].name)
        }

        @Test
        fun `single character symbol name`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(
                    Symbol("_x", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals("_x", macho.symbols[0].name)
        }

        @Test
        fun `symbol names with digits`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 1)),
                symbols = listOf(
                    Symbol("_func0", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("_func9", value = 8, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertNotNull(macho.symbols.firstOrNull { it.name == "_func0" })
            assertNotNull(macho.symbols.firstOrNull { it.name == "_func9" })
        }
    }

    @Nested
    inner class RelocationsX86 {

        @Test
        fun `X86_64_RELOC_BRANCH relocation`() {
            val code = ByteArray(16)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
                symbols = listOf(
                    Symbol("_foo", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 4, symbol = "_foo",
                        type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(4, rel.address)
            assertEquals(2, rel.type) // X86_64_RELOC_BRANCH
            assertTrue(rel.pcRelative)
            assertTrue(rel.extern)
            assertEquals(2, rel.length) // 4 bytes
        }

        @Test
        fun `X86_64_RELOC_SIGNED relocation`() {
            val code = ByteArray(16)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
                symbols = listOf(
                    Symbol("_data", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 8, symbol = "_data",
                        type = RelocationType.MachO_X86_64.SIGNED, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(1, rel.type) // X86_64_RELOC_SIGNED
            assertTrue(rel.pcRelative)
            assertEquals(2, rel.length) // 4 bytes
        }

        @Test
        fun `X86_64_RELOC_UNSIGNED relocation`() {
            val code = ByteArray(16)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
                symbols = listOf(
                    Symbol("_var", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "_var",
                        type = RelocationType.MachO_X86_64.UNSIGNED, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(0, rel.type) // X86_64_RELOC_UNSIGNED
            assertFalse(rel.pcRelative)
            assertEquals(3, rel.length) // 8 bytes
        }

        @Test
        fun `X86_64_RELOC_GOT_LOAD relocation`() {
            val code = ByteArray(16)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
                symbols = listOf(
                    Symbol("_got_sym", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 4, symbol = "_got_sym",
                        type = RelocationType.MachO_X86_64.GOT_LOAD, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(3, rel.type) // X86_64_RELOC_GOT_LOAD
            assertTrue(rel.pcRelative)
        }

        @Test
        fun `X86_64_RELOC_GOT relocation`() {
            val code = ByteArray(16)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
                symbols = listOf(
                    Symbol("_got_ref", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 4, symbol = "_got_ref",
                        type = RelocationType.MachO_X86_64.GOT, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(4, rel.type) // X86_64_RELOC_GOT
            assertTrue(rel.pcRelative)
        }

        @Test
        fun `relocation offset preserved`() {
            val code = ByteArray(32)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
                symbols = listOf(
                    Symbol("_target", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 24, symbol = "_target",
                        type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(24, macho.allSections[0].relocations[0].address)
        }
    }

    @Nested
    inner class RelocationsARM64 {

        @Test
        fun `ARM64_RELOC_BRANCH26 relocation`() {
            val code = ByteArray(16)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
                symbols = listOf(
                    Symbol("_target", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "_target",
                        type = RelocationType.MachO_ARM64.BRANCH26, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj, cpuType = MachO.CPU_TYPE_ARM64, cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(2, rel.type) // ARM64_RELOC_BRANCH26
            assertTrue(rel.pcRelative)
        }

        @Test
        fun `ARM64_RELOC_PAGE21 relocation`() {
            val code = ByteArray(16)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
                symbols = listOf(
                    Symbol("_page_target", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "_page_target",
                        type = RelocationType.MachO_ARM64.PAGE21, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj, cpuType = MachO.CPU_TYPE_ARM64, cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(3, rel.type) // ARM64_RELOC_PAGE21
            assertTrue(rel.pcRelative)
        }

        @Test
        fun `ARM64_RELOC_PAGEOFF12 relocation`() {
            val code = ByteArray(16)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
                symbols = listOf(
                    Symbol("_pageoff", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 4, symbol = "_pageoff",
                        type = RelocationType.MachO_ARM64.PAGEOFF12, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj, cpuType = MachO.CPU_TYPE_ARM64, cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(4, rel.type) // ARM64_RELOC_PAGEOFF12
            assertFalse(rel.pcRelative)
        }

        @Test
        fun `ARM64_RELOC_UNSIGNED relocation`() {
            val code = ByteArray(16)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
                symbols = listOf(
                    Symbol("_abs_target", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "_abs_target",
                        type = RelocationType.MachO_ARM64.UNSIGNED, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj, cpuType = MachO.CPU_TYPE_ARM64, cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(0, rel.type) // ARM64_RELOC_UNSIGNED
            assertFalse(rel.pcRelative)
            assertEquals(3, rel.length) // 8 bytes
        }

        @Test
        fun `ARM64_RELOC_GOT_LOAD_PAGE21 relocation`() {
            val code = ByteArray(16)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
                symbols = listOf(
                    Symbol("_got_page", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "_got_page",
                        type = RelocationType.MachO_ARM64.GOT_LOAD_PAGE21, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj, cpuType = MachO.CPU_TYPE_ARM64, cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(5, rel.type) // ARM64_RELOC_GOT_LOAD_PAGE21
            assertTrue(rel.pcRelative)
        }

        @Test
        fun `ARM64_RELOC_GOT_LOAD_PAGEOFF12 relocation`() {
            val code = ByteArray(16)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
                symbols = listOf(
                    Symbol("_got_off", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 4, symbol = "_got_off",
                        type = RelocationType.MachO_ARM64.GOT_LOAD_PAGEOFF12, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj, cpuType = MachO.CPU_TYPE_ARM64, cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(6, rel.type) // ARM64_RELOC_GOT_LOAD_PAGEOFF12
            assertFalse(rel.pcRelative)
        }
    }

    @Nested
    inner class RoundTripEquivalence {

        @Test
        fun `text section data survives round-trip`() {
            val code = byteArrayOf(
                0x55, 0x48, 0x89.toByte(), 0xE5.toByte(),
                0x48, 0x83.toByte(), 0xEC.toByte(), 0x10,
                0xB8.toByte(), 0x00, 0x00, 0x00, 0x00,
                0x48, 0x83.toByte(), 0xC4.toByte(), 0x10,
                0x5D, 0xC3.toByte(),
            )
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            )
            val macho = writeAndRead(obj)
            assertArrayEquals(code, macho.allSections[0].data)
        }

        @Test
        fun `data section data survives round-trip`() {
            val data = ByteArray(128) { (it * 3).toByte() }
            val obj = makeObjectFile(
                sections = listOf(Section(".data", SectionKind.DATA, data, align = 16)),
            )
            val macho = writeAndRead(obj)
            assertArrayEquals(data, macho.allSections[0].data)
        }

        @Test
        fun `symbol count survives round-trip`() {
            val symbols = (0 until 10).map { i ->
                Symbol("_fn$i", value = i.toLong() * 8, size = 8, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            }
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(80), align = 16)),
                symbols = symbols,
            )
            val macho = writeAndRead(obj)
            assertEquals(10, macho.symbols.size)
        }

        @Test
        fun `symbol values survive round-trip`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 16)),
                symbols = listOf(
                    Symbol("_a", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("_b", value = 32, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(0L, macho.symbols.first { it.name == "_a" }.value)
            assertEquals(32L, macho.symbols.first { it.name == "_b" }.value)
        }

        @Test
        fun `relocation count survives round-trip`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
                symbols = listOf(
                    Symbol("_a", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                    Symbol("_b", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 4, symbol = "_a",
                        type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
                    Relocation(offset = 12, symbol = "_b",
                        type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(2, macho.allSections[0].relocations.size)
        }

        @Test
        fun `section count survives round-trip`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1),
                    Section(".rodata", SectionKind.RODATA, byteArrayOf(0x01), align = 1),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(3, macho.allSections.size)
        }

        @Test
        fun `section order survives round-trip`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1),
                    Section(".rodata", SectionKind.RODATA, byteArrayOf(0x01), align = 1),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals("__text", macho.allSections[0].sectionName)
            assertEquals("__data", macho.allSections[1].sectionName)
            assertEquals("__const", macho.allSections[2].sectionName)
        }

        @Test
        fun `canRead accepts writer output`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val bytes = MachOObjectWriter().write(obj)
            assertTrue(MachOReader.canRead(bytes))
        }

        @Test
        fun `ObjectFile projection has correct format`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val projected = writeAndProject(obj)
            assertEquals(ObjectFormat.MACH_O, projected.format)
        }

        @Test
        fun `ObjectFile projection has correct arch`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val projected = writeAndProject(obj)
            assertEquals(ArchType.X86_64, projected.arch.arch)
        }

        @Test
        fun `ObjectFile projection has RELOCATABLE flag`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val projected = writeAndProject(obj)
            assertTrue(ObjectFlag.RELOCATABLE in projected.metadata.flags)
        }

        @Test
        fun `ObjectFile projection section kinds correct`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1),
                ),
            )
            val projected = writeAndProject(obj)
            assertEquals(SectionKind.TEXT, projected.sections.first { it.name == "__text" }.kind)
            assertEquals(SectionKind.DATA, projected.sections.first { it.name == "__data" }.kind)
        }

        @Test
        fun `ObjectFile projection rodata classified as RODATA`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".rodata", SectionKind.RODATA, byteArrayOf(0x01), align = 1))
            )
            val projected = writeAndProject(obj)
            assertEquals(SectionKind.RODATA, projected.sections.first { it.name == "__const" }.kind)
        }

        @Test
        fun `ObjectFile projection BSS classified correctly`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(16), align = 4))
            )
            val projected = writeAndProject(obj)
            assertEquals(SectionKind.BSS, projected.sections.first { it.name == "__bss" }.kind)
        }

        @Test
        fun `ObjectFile projection preserves symbol names`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(
                    Symbol("_main", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val projected = writeAndProject(obj)
            assertTrue(projected.symbols.any { it.name == "_main" })
        }

        @Test
        fun `ObjectFile projection preserves symbol binding`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
                symbols = listOf(
                    Symbol("_global", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("_local", value = 8, section = ".text",
                        binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val projected = writeAndProject(obj)
            assertEquals(SymbolBinding.GLOBAL, projected.symbols.first { it.name == "_global" }.binding)
            assertEquals(SymbolBinding.LOCAL, projected.symbols.first { it.name == "_local" }.binding)
        }
    }

    @Nested
    inner class MultipleSectionsAndAlignment {

        @Test
        fun `two sections with different alignments`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
                    Section(".data", SectionKind.DATA, ByteArray(8), align = 8),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(2, macho.allSections.size)
        }

        @Test
        fun `three sections with varying alignments`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, ByteArray(64), align = 16),
                    Section(".data", SectionKind.DATA, ByteArray(32), align = 4),
                    Section(".rodata", SectionKind.RODATA, ByteArray(16), align = 1),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(3, macho.allSections.size)
            assertEquals(64L, macho.allSections[0].size)
            assertEquals(32L, macho.allSections[1].size)
            assertEquals(16L, macho.allSections[2].size)
        }

        @Test
        fun `four sections text data rodata bss`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, ByteArray(16), align = 16),
                    Section(".data", SectionKind.DATA, ByteArray(8), align = 8),
                    Section(".rodata", SectionKind.RODATA, ByteArray(4), align = 4),
                    Section(".bss", SectionKind.BSS, ByteArray(32), align = 8),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(4, macho.allSections.size)
            assertEquals("__text", macho.allSections[0].sectionName)
            assertEquals("__data", macho.allSections[1].sectionName)
            assertEquals("__const", macho.allSections[2].sectionName)
            assertEquals("__bss", macho.allSections[3].sectionName)
        }

        @Test
        fun `alignment 1 section`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(1, macho.allSections[0].data.size)
        }

        @Test
        fun `alignment 16 section preserves data`() {
            val code = ByteArray(48) { (it + 1).toByte() }
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16))
            )
            val macho = writeAndRead(obj)
            assertArrayEquals(code, macho.allSections[0].data)
        }

        @Test
        fun `section data sizes are exact`() {
            val textData = ByteArray(7)
            val dataData = ByteArray(13)
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, textData, align = 4),
                    Section(".data", SectionKind.DATA, dataData, align = 4),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(7L, macho.allSections[0].size)
            assertEquals(13L, macho.allSections[1].size)
        }
    }

    @Nested
    inner class ArchitectureVariants {

        @Test
        fun `x86_64 object file`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj, cpuType = MachO.CPU_TYPE_X86_64, cpuSubtype = MachO.CPU_SUBTYPE_ALL)
            assertEquals(MachO.CPU_TYPE_X86_64, macho.header.cpuType)
            assertEquals(MachO.CPU_SUBTYPE_ALL, macho.header.cpuSubtype)
        }

        @Test
        fun `ARM64 object file`() {
            val code = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()) // ret
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4))
            )
            val macho = writeAndRead(obj, cpuType = MachO.CPU_TYPE_ARM64, cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL)
            assertEquals(MachO.CPU_TYPE_ARM64, macho.header.cpuType)
            assertEquals(MachO.CPU_SUBTYPE_ARM64_ALL, macho.header.cpuSubtype)
        }

        @Test
        fun `ARM64E subtype`() {
            val code = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte())
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4))
            )
            val macho = writeAndRead(obj, cpuType = MachO.CPU_TYPE_ARM64, cpuSubtype = MachO.CPU_SUBTYPE_ARM64E)
            assertEquals(MachO.CPU_SUBTYPE_ARM64E, macho.header.cpuSubtype)
        }

        @Test
        fun `ARM64 projection gives AARCH64 arch`() {
            val code = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte())
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4))
            )
            val projected = writeAndProject(obj, cpuType = MachO.CPU_TYPE_ARM64, cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL)
            assertEquals(ArchType.AARCH64, projected.arch.arch)
        }

        @Test
        fun `x86_64 projection gives X86_64 arch`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val projected = writeAndProject(obj)
            assertEquals(ArchType.X86_64, projected.arch.arch)
        }

        @Test
        fun `ARM64 object is 64-bit`() {
            val code = byteArrayOf(0x00, 0x00, 0x00, 0x00)
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4))
            )
            val macho = writeAndRead(obj, cpuType = MachO.CPU_TYPE_ARM64, cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL)
            assertTrue(macho.header.is64Bit)
        }
    }

    @Nested
    inner class SegmentAndSectionFlags {

        @Test
        fun `text section flags include PURE_INSTRUCTIONS`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            val flags = macho.allSections[0].flags
            assertTrue(flags and MachO.S_ATTR_PURE_INSTRUCTIONS != 0)
        }

        @Test
        fun `text section flags include SOME_INSTRUCTIONS`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            val flags = macho.allSections[0].flags
            assertTrue(flags and MachO.S_ATTR_SOME_INSTRUCTIONS != 0)
        }

        @Test
        fun `data section flags are S_REGULAR only`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(MachO.S_REGULAR, macho.allSections[0].flags)
        }

        @Test
        fun `rodata section flags are S_REGULAR only`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".rodata", SectionKind.RODATA, byteArrayOf(0x01), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(MachO.S_REGULAR, macho.allSections[0].flags)
        }

        @Test
        fun `BSS section flags are S_ZEROFILL`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(16), align = 4))
            )
            val macho = writeAndRead(obj)
            assertEquals(MachO.S_ZEROFILL, macho.allSections[0].type)
        }

        @Test
        fun `section type masks correctly from flags`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(MachO.S_REGULAR, macho.allSections[0].type)
        }

        @Test
        fun `section attributes mask excludes type bits`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            val attrs = macho.allSections[0].attributes
            assertEquals(0, attrs and 0xFF)
        }

        @Test
        fun `projected text section has EXEC flag`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val projected = writeAndProject(obj)
            assertTrue(SectionFlag.EXEC in projected.sections[0].flags)
        }

        @Test
        fun `projected text section has ALLOC flag`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val projected = writeAndProject(obj)
            assertTrue(SectionFlag.ALLOC in projected.sections[0].flags)
        }

        @Test
        fun `projected text section has PURE_INSTRUCTIONS flag`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val projected = writeAndProject(obj)
            assertTrue(SectionFlag.PURE_INSTRUCTIONS in projected.sections[0].flags)
        }

        @Test
        fun `projected data section has WRITE flag`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1))
            )
            val projected = writeAndProject(obj)
            assertTrue(SectionFlag.WRITE in projected.sections[0].flags)
        }
    }

    @Nested
    inner class LargeSymbolTable {

        @Test
        fun `50 symbols round-trip`() {
            val symbols = (0 until 50).map { i ->
                Symbol("_sym_$i", value = i.toLong() * 4, size = 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            }
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(200), align = 16)),
                symbols = symbols,
            )
            val macho = writeAndRead(obj)
            assertEquals(50, macho.symbols.size)
        }

        @Test
        fun `100 symbols round-trip`() {
            val symbols = (0 until 100).map { i ->
                Symbol("_func_$i", value = i.toLong() * 8, size = 8, section = ".text",
                    binding = if (i % 2 == 0) SymbolBinding.GLOBAL else SymbolBinding.LOCAL,
                    kind = SymbolKind.FUNCTION)
            }
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(800), align = 16)),
                symbols = symbols,
            )
            val macho = writeAndRead(obj)
            assertEquals(100, macho.symbols.size)
            for (i in 0 until 100) {
                assertNotNull(macho.symbols.firstOrNull { it.name == "_func_$i" }, "Missing _func_$i")
            }
        }

        @Test
        fun `200 symbols all names preserved`() {
            val symbols = (0 until 200).map { i ->
                Symbol("_s$i", value = i.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            }
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(200), align = 1)),
                symbols = symbols,
            )
            val macho = writeAndRead(obj)
            assertEquals(200, macho.symbols.size)
            val names = macho.symbols.map { it.name }.toSet()
            for (i in 0 until 200) {
                assertTrue("_s$i" in names, "Missing _s$i")
            }
        }

        @Test
        fun `mixed local and global symbols`() {
            val symbols = listOf(
                Symbol("_local1", value = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                Symbol("_global1", value = 8, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_local2", value = 16, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                Symbol("_global2", value = 24, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_undef", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            )
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 1)),
                symbols = symbols,
            )
            val macho = writeAndRead(obj)
            assertEquals(5, macho.symbols.size)

            val local1 = macho.symbols.first { it.name == "_local1" }
            assertFalse(local1.isExternal)
            assertTrue(local1.isInSection)

            val global1 = macho.symbols.first { it.name == "_global1" }
            assertTrue(global1.isExternal)
            assertTrue(global1.isInSection)

            val undef = macho.symbols.first { it.name == "_undef" }
            assertTrue(undef.isUndefined)
            assertTrue(undef.isExternal)
        }

        @Test
        fun `symbols across multiple sections`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
                    Section(".data", SectionKind.DATA, ByteArray(16), align = 8),
                ),
                symbols = listOf(
                    Symbol("_code_func", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("_data_var", value = 0, section = ".data",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
                ),
            )
            val macho = writeAndRead(obj)
            val codeSym = macho.symbols.first { it.name == "_code_func" }
            val dataSym = macho.symbols.first { it.name == "_data_var" }
            assertEquals(1, codeSym.sectionIndex)
            assertEquals(2, dataSym.sectionIndex)
        }
    }

    @Nested
    inner class MultipleRelocations {

        @Test
        fun `two relocations in same section`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
                symbols = listOf(
                    Symbol("_a", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                    Symbol("_b", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 4, symbol = "_a",
                        type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
                    Relocation(offset = 16, symbol = "_b",
                        type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(2, macho.allSections[0].relocations.size)
        }

        @Test
        fun `three relocations with different types`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
                symbols = listOf(
                    Symbol("_call_target", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                    Symbol("_data_ref", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                    Symbol("_abs_ref", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 1, symbol = "_call_target",
                        type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
                    Relocation(offset = 8, symbol = "_data_ref",
                        type = RelocationType.MachO_X86_64.SIGNED, section = ".text"),
                    Relocation(offset = 16, symbol = "_abs_ref",
                        type = RelocationType.MachO_X86_64.UNSIGNED, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            val relocs = macho.allSections[0].relocations
            assertEquals(3, relocs.size)
        }

        @Test
        fun `five relocations all addresses preserved`() {
            val offsets = listOf(0L, 4L, 8L, 12L, 20L)
            val symbols = offsets.indices.map { i ->
                Symbol("_r$i", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)
            }
            val relocs = offsets.mapIndexed { i, off ->
                Relocation(offset = off, symbol = "_r$i",
                    type = RelocationType.MachO_X86_64.BRANCH, section = ".text")
            }
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
                symbols = symbols,
                relocations = relocs,
            )
            val macho = writeAndRead(obj)
            val addresses = macho.allSections[0].relocations.map { it.address }.sorted()
            assertEquals(listOf(0, 4, 8, 12, 20), addresses)
        }

        @Test
        fun `relocations reference correct symbols via extern`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
                symbols = listOf(
                    Symbol("_target", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 4, symbol = "_target",
                        type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            val rel = macho.allSections[0].relocations[0]
            assertTrue(rel.extern)
            // symbolIndex should point to the symbol
            assertTrue(rel.symbolIndex < macho.symbols.size)
        }
    }

    @Nested
    inner class FormatDetection {

        @Test
        fun `canRead accepts valid Mach-O`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val bytes = MachOObjectWriter().write(obj)
            assertTrue(MachOReader.canRead(bytes))
        }

        @Test
        fun `canRead rejects ELF magic`() {
            assertFalse(MachOReader.canRead(byteArrayOf(0x7f, 0x45, 0x4c, 0x46)))
        }

        @Test
        fun `canRead rejects PE magic`() {
            assertFalse(MachOReader.canRead(byteArrayOf(0x4d, 0x5a, 0x00, 0x00)))
        }

        @Test
        fun `canRead rejects empty bytes`() {
            assertFalse(MachOReader.canRead(byteArrayOf()))
        }

        @Test
        fun `canRead rejects short bytes`() {
            assertFalse(MachOReader.canRead(byteArrayOf(0x00, 0x00)))
        }

        @Test
        fun `canRead rejects zeroes`() {
            assertFalse(MachOReader.canRead(byteArrayOf(0, 0, 0, 0)))
        }

        @Test
        fun `detectFormat returns MACH_O`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val bytes = MachOObjectWriter().write(obj)
            assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
        }

        @Test
        fun `MachOObjectFileReader format is MACH_O`() {
            val reader = MachOObjectFileReader()
            assertEquals(ObjectFormat.MACH_O, reader.format)
        }

        @Test
        fun `MachOObjectFileReader canRead works`() {
            val reader = MachOObjectFileReader()
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val bytes = MachOObjectWriter().write(obj)
            assertTrue(reader.canRead(bytes))
        }

        @Test
        fun `MachOObjectFileReader read returns ObjectFile`() {
            val reader = MachOObjectFileReader()
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val bytes = MachOObjectWriter().write(obj)
            val result = reader.read(bytes)
            assertEquals(ObjectFormat.MACH_O, result.format)
        }

        @Test
        fun `MachOObjectFileReader rejects ELF`() {
            val reader = MachOObjectFileReader()
            assertFalse(reader.canRead(byteArrayOf(0x7f, 0x45, 0x4c, 0x46)))
        }
    }

    @Nested
    inner class SegmentSectionMapping {

        @Test
        fun `text in __TEXT segment`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals("__TEXT", macho.allSections.first { it.sectionName == "__text" }.segmentName)
        }

        @Test
        fun `data in __DATA segment`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals("__DATA", macho.allSections.first { it.sectionName == "__data" }.segmentName)
        }

        @Test
        fun `const in __TEXT segment`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".rodata", SectionKind.RODATA, byteArrayOf(0x01), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals("__TEXT", macho.allSections.first { it.sectionName == "__const" }.segmentName)
        }

        @Test
        fun `bss in __DATA segment`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(16), align = 4))
            )
            val macho = writeAndRead(obj)
            assertEquals("__DATA", macho.allSections.first { it.sectionName == "__bss" }.segmentName)
        }

        @Test
        fun `sectionByName finds text`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertNotNull(macho.sectionByName("__TEXT", "__text"))
        }

        @Test
        fun `sectionByName finds data`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1))
            )
            val macho = writeAndRead(obj)
            assertNotNull(macho.sectionByName("__DATA", "__data"))
        }

        @Test
        fun `sectionByName returns null for missing section`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertNull(macho.sectionByName("__DATA", "__data"))
        }
    }

    @Nested
    inner class UnsupportedSections {

        @Test
        fun `debug section kind is skipped by writer`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".debug_info", SectionKind.DEBUG_INFO, byteArrayOf(0x00), align = 1),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(1, macho.allSections.size)
            assertEquals("__text", macho.allSections[0].sectionName)
        }

        @Test
        fun `unknown section kind is skipped by writer`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".custom", SectionKind.UNKNOWN, byteArrayOf(0xFF.toByte()), align = 1),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(1, macho.allSections.size)
        }

        @Test
        fun `symtab section kind is skipped`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".symtab", SectionKind.SYMTAB, byteArrayOf(0x00), align = 1),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(1, macho.allSections.size)
        }

        @Test
        fun `strtab section kind is skipped`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".strtab", SectionKind.STRTAB, byteArrayOf(0x00), align = 1),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(1, macho.allSections.size)
        }
    }

    @Nested
    inner class ElfRelocCrossMapping {

        @Test
        fun `ELF PLT32 maps to X86_64_RELOC_BRANCH`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
                symbols = listOf(
                    Symbol("_call", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 4, symbol = "_call",
                        type = RelocationType.X86_64.PLT32, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(2, rel.type) // X86_64_RELOC_BRANCH
            assertTrue(rel.pcRelative)
        }

        @Test
        fun `ELF PC32 maps to X86_64_RELOC_SIGNED`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
                symbols = listOf(
                    Symbol("_ref", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 4, symbol = "_ref",
                        type = RelocationType.X86_64.PC32, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(1, rel.type) // X86_64_RELOC_SIGNED
            assertTrue(rel.pcRelative)
        }

        @Test
        fun `ELF R_64 maps to X86_64_RELOC_UNSIGNED`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
                symbols = listOf(
                    Symbol("_abs", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "_abs",
                        type = RelocationType.X86_64.R_64, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(0, rel.type) // X86_64_RELOC_UNSIGNED
            assertFalse(rel.pcRelative)
            assertEquals(3, rel.length) // 8 bytes
        }

        @Test
        fun `ELF CALL26 maps to ARM64_RELOC_BRANCH26`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 4)),
                symbols = listOf(
                    Symbol("_target", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "_target",
                        type = RelocationType.AArch64.CALL26, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj, cpuType = MachO.CPU_TYPE_ARM64, cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(2, rel.type) // ARM64_RELOC_BRANCH26
            assertTrue(rel.pcRelative)
        }

        @Test
        fun `ELF ADR_PREL_PG_HI21 maps to ARM64_RELOC_PAGE21`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 4)),
                symbols = listOf(
                    Symbol("_page", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "_page",
                        type = RelocationType.AArch64.ADR_PREL_PG_HI21, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj, cpuType = MachO.CPU_TYPE_ARM64, cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL)
            val rel = macho.allSections[0].relocations[0]
            assertEquals(3, rel.type) // ARM64_RELOC_PAGE21
        }
    }

    @Nested
    inner class LargeContent {

        @Test
        fun `8KB text section`() {
            val code = ByteArray(8192) { (it % 256).toByte() }
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16))
            )
            val macho = writeAndRead(obj)
            assertArrayEquals(code, macho.allSections[0].data)
        }

        @Test
        fun `32KB data section`() {
            val data = ByteArray(32768) { (it xor 0xAB).toByte() }
            val obj = makeObjectFile(
                sections = listOf(Section(".data", SectionKind.DATA, data, align = 8))
            )
            val macho = writeAndRead(obj)
            assertArrayEquals(data, macho.allSections[0].data)
        }

        @Test
        fun `multiple large sections`() {
            val text = ByteArray(4096) { 0x90.toByte() }
            val data = ByteArray(2048) { 0x42 }
            val rodata = ByteArray(1024) { 0x55 }
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, text, align = 16),
                    Section(".data", SectionKind.DATA, data, align = 8),
                    Section(".rodata", SectionKind.RODATA, rodata, align = 4),
                ),
            )
            val macho = writeAndRead(obj)
            assertArrayEquals(text, macho.allSections[0].data)
            assertArrayEquals(data, macho.allSections[1].data)
            assertArrayEquals(rodata, macho.allSections[2].data)
        }

        @Test
        fun `single byte section`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertEquals(1, macho.allSections[0].data.size)
            assertEquals(0xCC.toByte(), macho.allSections[0].data[0])
        }

        @Test
        fun `large BSS section`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(65536), align = 16))
            )
            val macho = writeAndRead(obj)
            assertEquals(65536L, macho.allSections[0].size)
        }
    }

    @Nested
    inner class MachOFileModel {

        @Test
        fun `MachOFile dylibs list empty for objects`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertTrue(macho.dylibs.isEmpty())
        }

        @Test
        fun `MachOFile uuid is null for basic objects`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertNull(macho.uuid)
        }

        @Test
        fun `MachOFile mainEntryOffset is null for objects`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertNull(macho.mainEntryOffset)
        }

        @Test
        fun `MachOFile sourceVersion is null for objects`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val macho = writeAndRead(obj)
            assertNull(macho.sourceVersion)
        }

        @Test
        fun `MachOFile segments list has exactly one segment`() {
            val obj = makeObjectFile(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                    Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(1, macho.segments.size)
        }

        @Test
        fun `MachOSymbol isAbsolute for N_UNDF is false`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(
                    Symbol("_ext", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
            )
            val macho = writeAndRead(obj)
            assertFalse(macho.symbols[0].isAbsolute)
        }

        @Test
        fun `MachOSymbol isPrivateExternal is false for basic symbols`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(
                    Symbol("_fn", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
            )
            val macho = writeAndRead(obj)
            assertFalse(macho.symbols[0].isPrivateExternal)
        }
    }

    @Nested
    inner class ObjectFileProjectionMetadata {

        @Test
        fun `projected osAbi is MACOS`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val projected = writeAndProject(obj)
            assertEquals(OsAbi.MACOS, projected.metadata.osAbi)
        }

        @Test
        fun `projected entryPoint is null for objects`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val projected = writeAndProject(obj)
            assertNull(projected.metadata.entryPoint)
        }

        @Test
        fun `projected dynamicInfo is null for basic objects`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val projected = writeAndProject(obj)
            assertNull(projected.dynamicInfo)
        }

        @Test
        fun `projected relocations from x86 branch`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
                symbols = listOf(
                    Symbol("_target", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 4, symbol = "_target",
                        type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
                ),
            )
            val projected = writeAndProject(obj)
            assertEquals(1, projected.relocations.size)
            assertEquals("_target", projected.relocations[0].symbol)
        }

        @Test
        fun `projected undefined symbol has UNDEFINED flag`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(
                    Symbol("_ext", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
            )
            val projected = writeAndProject(obj)
            val sym = projected.symbols.first { it.name == "_ext" }
            assertTrue(SymbolFlag.UNDEFINED in sym.flags)
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `empty sections list produces valid output`() {
            val obj = makeObjectFile()
            val bytes = MachOObjectWriter().write(obj)
            assertTrue(MachOReader.canRead(bytes))
        }

        @Test
        fun `object with only symbols and no sections`() {
            val obj = makeObjectFile(
                symbols = listOf(
                    Symbol("_ext", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
            )
            val bytes = MachOObjectWriter().write(obj)
            val macho = MachOReader.read(bytes)
            assertEquals(1, macho.symbols.size)
        }

        @Test
        fun `object with relocations targeting non-existent section ignored`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(
                    Symbol("_target", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "_target",
                        type = RelocationType.MachO_X86_64.BRANCH, section = ".nonexistent"),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(0, macho.allSections[0].relocations.size)
        }

        @Test
        fun `object with relocations targeting non-existent symbol ignored`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
                symbols = listOf(),
                relocations = listOf(
                    Relocation(offset = 0, symbol = "_missing",
                        type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
                ),
            )
            val macho = writeAndRead(obj)
            assertEquals(0, macho.allSections[0].relocations.size)
        }

        @Test
        fun `zero-size data section`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".data", SectionKind.DATA, ByteArray(0), align = 1))
            )
            val bytes = MachOObjectWriter().write(obj)
            assertTrue(MachOReader.canRead(bytes))
        }

        @Test
        fun `isMachO utility function works`() {
            val obj = makeObjectFile(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
            )
            val bytes = MachOObjectWriter().write(obj)
            assertTrue(MachO.isMachO(bytes))
        }

        @Test
        fun `isMachO rejects short input`() {
            assertFalse(MachO.isMachO(byteArrayOf(0xCF.toByte(), 0xFA.toByte())))
        }
    }
}
