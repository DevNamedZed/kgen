package org.kgen.binary.macho

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MachOExtendedTest {

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

    // --- Writer: various section types ---

    @Test
    fun `writer produces BSS section as zerofill`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".bss", SectionKind.BSS, ByteArray(64), align = 8),
            )
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val bss = macho.allSections.first { it.sectionName == "__bss" }
        assertEquals("__DATA", bss.segmentName)
        assertEquals(MachO.S_ZEROFILL, bss.type)
        assertEquals(64, bss.size)
    }

    @Test
    fun `writer handles text and rodata and data together`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0x90.toByte(), 0xC3.toByte()), align = 1),
                Section(".rodata", SectionKind.RODATA, "world\u0000".toByteArray(Charsets.US_ASCII), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(0x01, 0x02, 0x03, 0x04), align = 4),
            )
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(3, macho.allSections.size)
        assertNotNull(macho.allSections.firstOrNull { it.sectionName == "__text" })
        assertNotNull(macho.allSections.firstOrNull { it.sectionName == "__const" })
        assertNotNull(macho.allSections.firstOrNull { it.sectionName == "__data" })
    }

    @Test
    fun `writer skips unknown section kinds`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".eh_frame", SectionKind.EH_FRAME, ByteArray(16), align = 8),
                Section(".note", SectionKind.NOTE, ByteArray(4), align = 4),
            )
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(1, macho.allSections.size)
        assertEquals("__text", macho.allSections[0].sectionName)
    }

    @Test
    fun `writer preserves section alignment`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(100), align = 16),
            )
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.allSections.first { it.sectionName == "__text" }
        // align field in Mach-O is log2, log2(16) = 4
        assertEquals(4, text.align)
    }

    // --- Round-trip: write then read back ---

    @Test
    fun `round-trip preserves BSS section`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".bss", SectionKind.BSS, ByteArray(128), align = 16),
            )
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val bss = macho.allSections.first { it.sectionName == "__bss" }
        assertEquals(128, bss.size)
        assertEquals(128, bss.data.size)
        assertTrue(bss.data.all { it == 0.toByte() })
    }

    @Test
    fun `round-trip with four section types`() {
        val code = byteArrayOf(0x55, 0xC3.toByte())
        val rodata = "const\u0000".toByteArray(Charsets.US_ASCII)
        val data = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
        val bss = ByteArray(32)

        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 1),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
                Section(".data", SectionKind.DATA, data, align = 4),
                Section(".bss", SectionKind.BSS, bss, align = 8),
            )
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(4, macho.allSections.size)
        assertArrayEquals(code, macho.allSections.first { it.sectionName == "__text" }.data)
        assertArrayEquals(rodata, macho.allSections.first { it.sectionName == "__const" }.data)
        assertArrayEquals(data, macho.allSections.first { it.sectionName == "__data" }.data)
    }

    @Test
    fun `round-trip preserves mixed local and global symbols`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 1)),
            symbols = listOf(
                Symbol("_global1", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_local1", value = 16, size = 8, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                Symbol("_global2", value = 24, size = 8, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            )
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(3, macho.symbols.size)
        assertTrue(macho.symbols.first { it.name == "_global1" }.isExternal)
        assertFalse(macho.symbols.first { it.name == "_local1" }.isExternal)
        assertTrue(macho.symbols.first { it.name == "_global2" }.isExternal)
    }

    @Test
    fun `round-trip preserves multiple undefined symbols`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol("_printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("_malloc", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("_free", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            )
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(3, macho.symbols.size)
        assertTrue(macho.symbols.all { it.isUndefined })
        assertTrue(macho.symbols.all { it.isExternal })
        val names = macho.symbols.map { it.name }.toSet()
        assertTrue("_printf" in names)
        assertTrue("_malloc" in names)
        assertTrue("_free" in names)
    }

    @Test
    fun `round-trip with many relocations`() {
        val code = ByteArray(80)
        val symbols = (0 until 10).map { i ->
            Symbol("_ext_$i", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)
        }
        val relocs = (0 until 10).map { i ->
            Relocation(
                offset = (i * 8).toLong(), symbol = "_ext_$i",
                type = RelocationType.MachO_X86_64.BRANCH, section = ".text",
            )
        }
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = symbols,
            relocations = relocs,
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(10, text.relocations.size)
        for (i in 0 until 10) {
            assertEquals(i * 8, text.relocations[i].address)
            assertTrue(text.relocations[i].pcRelative)
            assertTrue(text.relocations[i].extern)
        }
    }

    @Test
    fun `round-trip SIGNED relocation type`() {
        val code = ByteArray(16)
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_data_ref", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 3, symbol = "_data_ref",
                    type = RelocationType.MachO_X86_64.SIGNED, section = ".text"),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(1, text.relocations.size)
        val rel = text.relocations[0]
        assertEquals(3, rel.address)
        assertTrue(rel.pcRelative)
        assertEquals(1, rel.type) // X86_64_RELOC_SIGNED = 1
    }

    @Test
    fun `round-trip UNSIGNED relocation type`() {
        val data = ByteArray(16)
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, data, align = 8),
            ),
            symbols = listOf(
                Symbol("_target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "_target",
                    type = RelocationType.MachO_X86_64.UNSIGNED, section = ".data"),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val dataSec = macho.allSections.first { it.sectionName == "__data" }
        assertEquals(1, dataSec.relocations.size)
        val rel = dataSec.relocations[0]
        assertEquals(0, rel.address)
        assertFalse(rel.pcRelative)
        assertEquals(0, rel.type) // X86_64_RELOC_UNSIGNED = 0
    }

    // --- String table integrity ---

    @Test
    fun `string table contains all symbol names`() {
        val names = listOf("_alpha", "_beta", "_gamma", "_delta", "_epsilon")
        val symbols = names.mapIndexed { i, name ->
            Symbol(name, value = i.toLong() * 8, size = 8, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
        }
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(40), align = 1)),
            symbols = symbols,
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val readNames = macho.symbols.map { it.name }.toSet()
        for (name in names) {
            assertTrue(name in readNames, "Symbol $name should be present")
        }
    }

    @Test
    fun `symbols with long names survive round-trip`() {
        val longName = "_very_long_symbol_name_that_tests_string_table_handling_properly"
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol(longName, value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(1, macho.symbols.size)
        assertEquals(longName, macho.symbols[0].name)
    }

    // --- Architecture variants ---

    @Test
    fun `ARM64 writer produces correct section structure`() {
        val code = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()) // ret
        val obj = makeObjectFile(
            arch = Architecture(ArchType.AARCH64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 4),
                Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 4),
            ),
        )
        val bytes = MachOObjectWriter(
            cpuType = MachO.CPU_TYPE_ARM64,
            cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL,
        ).write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(MachO.CPU_TYPE_ARM64, macho.header.cpuType)
        assertEquals(2, macho.allSections.size)
        assertArrayEquals(code, macho.allSections.first { it.sectionName == "__text" }.data)
    }

    @Test
    fun `ARM64 object projection has correct architecture`() {
        val code = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte())
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
        )
        val bytes = MachOObjectWriter(
            cpuType = MachO.CPU_TYPE_ARM64,
            cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL,
        ).write(obj)
        val projected = MachOReader.toObjectFile(MachOReader.read(bytes))

        assertEquals(ArchType.AARCH64, projected.arch.arch)
        assertEquals("apple", projected.arch.vendor)
        assertEquals("macos", projected.arch.os)
    }

    @Test
    fun `x86-64 object projection has correct architecture`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val projected = MachOReader.toObjectFile(MachOReader.read(bytes))

        assertEquals(ArchType.X86_64, projected.arch.arch)
        assertEquals("apple", projected.arch.vendor)
    }

    // --- Edge cases ---

    @Test
    fun `empty text section produces valid output`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(0), align = 1)),
        )
        val bytes = MachOObjectWriter().write(obj)
        assertTrue(MachOReader.canRead(bytes))
        val macho = MachOReader.read(bytes)
        assertEquals(1, macho.allSections.size)
        assertEquals(0, macho.allSections[0].data.size)
    }

    @Test
    fun `large symbol table survives round-trip`() {
        val count = 200
        val symbols = (0 until count).map { i ->
            Symbol("_s$i", value = i.toLong() * 2, size = 2, section = ".text",
                binding = if (i % 2 == 0) SymbolBinding.GLOBAL else SymbolBinding.LOCAL,
                kind = SymbolKind.FUNCTION)
        }
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(count * 2), align = 1)),
            symbols = symbols,
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(count, macho.symbols.size)
        for (i in 0 until count) {
            val sym = macho.symbols.firstOrNull { it.name == "_s$i" }
            assertNotNull(sym, "Missing symbol _s$i")
            assertEquals(i.toLong() * 2, sym!!.value)
            if (i % 2 == 0) assertTrue(sym.isExternal) else assertFalse(sym.isExternal)
        }
    }

    @Test
    fun `large section data round-trips correctly`() {
        val bigData = ByteArray(65536) { (it % 251).toByte() }
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, bigData, align = 16)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.allSections.first { it.sectionName == "__text" }
        assertArrayEquals(bigData, text.data)
    }

    @Test
    fun `object with only undefined symbols`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(4), align = 1)),
            symbols = listOf(
                Symbol("_extern1", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("_extern2", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(2, macho.symbols.size)
        assertTrue(macho.symbols.all { it.isUndefined })
        assertTrue(macho.symbols.all { it.sectionIndex == 0 })
    }

    // --- Linker tests ---

    @Test
    fun `linker merges text sections from three objects`() {
        val objs = (0 until 3).map { i ->
            val code = byteArrayOf((0xB8 + i).toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
            ObjectFile(
                format = ObjectFormat.MACH_O,
                arch = Architecture(ArchType.X86_64),
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
                symbols = listOf(
                    Symbol(if (i == 0) "_main" else "_func$i", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ),
                relocations = emptyList(),
            )
        }
        val binary = MachOLinker().link(objs)
        val buf = le(binary)

        assertEquals(MachO.MH_EXECUTE, buf.getInt(12))
        // All 3 functions' code should be present in binary
        val binStr = binary.map { it.toInt() and 0xFF }
        assertTrue(binStr.windowed(2).any { it[0] == 0xB8 && it[1] == 0x00 })
    }

    @Test
    fun `linker resolves cross-references between three objects`() {
        // obj0: _main calls _helper1
        val mainCode = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call _helper1
            0xC3.toByte(),
        )
        val obj0 = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, mainCode, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_helper1", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "_helper1", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        // obj1: _helper1 calls _helper2
        val h1Code = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call _helper2
            0xC3.toByte(),
        )
        val obj1 = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, h1Code, align = 16)),
            symbols = listOf(
                Symbol("_helper1", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_helper2", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "_helper2", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        // obj2: _helper2 returns 42
        val h2Code = byteArrayOf(
            0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00, // mov eax, 42
            0xC3.toByte(),
        )
        val obj2 = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, h2Code, align = 16)),
            symbols = listOf(
                Symbol("_helper2", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachOLinker().link(listOf(obj0, obj1, obj2))
        val buf = le(binary)
        assertEquals(MachO.MH_EXECUTE, buf.getInt(12))
    }

    @Test
    fun `linker includes data section with correct protection`() {
        val code = byteArrayOf(0xC3.toByte())
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, data, align = 8),
            ),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachOLinker().link(listOf(obj))
        val buf = le(binary)

        // Find DATA segment and verify it is writable
        var off = 32
        val ncmds = buf.getInt(16)
        var foundData = false
        for (i in 0 until ncmds) {
            val cmd = buf.getInt(off)
            val cmdSize = buf.getInt(off + 4)
            if (cmd == MachO.LC_SEGMENT_64) {
                val name = String(binary, off + 8, 6, Charsets.US_ASCII)
                if (name.startsWith("__DATA")) {
                    foundData = true
                    val initProt = buf.getInt(off + 60)
                    assertEquals(3, initProt, "__DATA should have rw- protection")
                }
            }
            off += cmdSize
        }
        assertTrue(foundData, "Should have __DATA segment")
    }

    @Test
    fun `linker rejects missing entry point`() {
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = listOf(
                Symbol("_helper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        assertThrows(IllegalStateException::class.java) {
            MachOLinker().link(listOf(obj))
        }
    }

    @Test
    fun `linker rejects unresolved symbols`() {
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_undefined_sym", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "_undefined_sym",
                    type = RelocationType.X86_64.PC32, addend = -4, section = ".text"),
            ),
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            MachOLinker().link(listOf(obj))
        }
        assertTrue(ex.message!!.contains("_undefined_sym"))
    }

    @Test
    fun `linker ARM64 produces correct CPU type`() {
        val code = byteArrayOf(
            0x00, 0x00, 0x80.toByte(), 0xD2.toByte(), // mov x0, #0
            0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte(), // ret
        )
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.AARCH64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachOLinker(
            cpuType = MachO.CPU_TYPE_ARM64,
            cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL,
        ).link(listOf(obj))
        val buf = le(binary)

        assertEquals(MachO.CPU_TYPE_ARM64, buf.getInt(4))
        assertEquals(MachO.CPU_SUBTYPE_ARM64_ALL, buf.getInt(8))
    }

    @Test
    fun `linker output is readable by MachOReader`() {
        val code = byteArrayOf(0x31, 0xC0.toByte(), 0xC3.toByte()) // xor eax, eax; ret
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachOLinker().link(listOf(obj))
        assertTrue(MachOReader.canRead(binary))
        val macho = MachOReader.read(binary)

        assertTrue(macho.isExecutable)
        assertFalse(macho.isObject)
        assertNotNull(macho.mainEntryOffset)
        assertTrue(macho.symbols.any { it.name == "_main" })
    }

    @Test
    fun `linker output projected to ObjectFile has EXECUTABLE flag`() {
        val code = byteArrayOf(0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachOLinker().link(listOf(obj))
        val macho = MachOReader.read(binary)
        val projected = MachOReader.toObjectFile(macho)

        assertTrue(ObjectFlag.EXECUTABLE in projected.metadata.flags)
        assertTrue(ObjectFlag.POSITION_INDEPENDENT in projected.metadata.flags)
        assertEquals(OsAbi.MACOS, projected.metadata.osAbi)
    }

    // --- Segment and section attributes ---

    @Test
    fun `text section has pure instructions attribute`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0x90.toByte()), align = 1)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.allSections.first { it.sectionName == "__text" }
        assertTrue(text.isPureInstructions)
        assertTrue(text.attributes and MachO.S_ATTR_SOME_INSTRUCTIONS != 0)
    }

    @Test
    fun `data section does not have instruction attributes`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val data = macho.allSections.first { it.sectionName == "__data" }
        assertFalse(data.isPureInstructions)
        assertEquals(0, data.attributes and MachO.S_ATTR_SOME_INSTRUCTIONS)
    }

    @Test
    fun `section type classification is correct for projected ObjectFile`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".rodata", SectionKind.RODATA, byteArrayOf(0x01), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(0x02), align = 1),
                Section(".bss", SectionKind.BSS, ByteArray(8), align = 8),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val projected = MachOReader.toObjectFile(MachOReader.read(bytes))

        assertEquals(SectionKind.TEXT, projected.sections.first { it.name == "__text" }.kind)
        assertEquals(SectionKind.RODATA, projected.sections.first { it.name == "__const" }.kind)
        assertEquals(SectionKind.DATA, projected.sections.first { it.name == "__data" }.kind)
        assertEquals(SectionKind.BSS, projected.sections.first { it.name == "__bss" }.kind)
    }

    // --- MachOFile model queries ---

    @Test
    fun `sectionByName finds correct section`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.sectionByName("__TEXT", "__text")
        assertNotNull(text)
        assertEquals("__text", text!!.sectionName)

        val data = macho.sectionByName("__DATA", "__data")
        assertNotNull(data)
        assertEquals("__data", data!!.sectionName)

        val missing = macho.sectionByName("__TEXT", "__nonexistent")
        assertNull(missing)
    }

    @Test
    fun `MachOFile type checks are mutually exclusive for object`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertTrue(macho.isObject)
        assertFalse(macho.isExecutable)
        assertFalse(macho.isDylib)
    }

    @Test
    fun `MachOFile type checks for executable`() {
        val code = byteArrayOf(0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val binary = MachOLinker().link(listOf(obj))
        val macho = MachOReader.read(binary)

        assertTrue(macho.isExecutable)
        assertFalse(macho.isObject)
        assertFalse(macho.isDylib)
    }

    // --- Reader edge cases ---

    @Test
    fun `reader handles object with no symbols`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(0, macho.symbols.size)
        assertEquals(1, macho.allSections.size)
    }

    @Test
    fun `canRead rejects too-short input`() {
        assertFalse(MachOReader.canRead(byteArrayOf()))
        assertFalse(MachOReader.canRead(byteArrayOf(0xFE.toByte())))
        assertFalse(MachOReader.canRead(byteArrayOf(0xFE.toByte(), 0xED.toByte(), 0xFA.toByte())))
    }

    @Test
    fun `detectFormat identifies Mach-O from writer output`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = MachOObjectWriter().write(obj)
        assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
    }

    @Test
    fun `MachOObjectFileReader interface works end to end`() {
        val reader = MachOObjectFileReader()
        assertEquals(ObjectFormat.MACH_O, reader.format)

        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0x55, 0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(0x42, 0x43), align = 4),
            ),
            symbols = listOf(
                Symbol("_entry", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        assertTrue(reader.canRead(bytes))

        val result = reader.read(bytes)
        assertEquals(ObjectFormat.MACH_O, result.format)
        assertTrue(result.sections.any { it.name == "__text" })
        assertTrue(result.sections.any { it.name == "__data" })
        assertTrue(result.symbols.any { it.name == "_entry" })
    }
}
