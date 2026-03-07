package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PeExtendedTest {

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

    // COFF round-trip: section combinations

    @Test
    fun `round-trip COFF with text data rodata bss preserves all sections`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                Section(".data", SectionKind.DATA, byteArrayOf(0x01, 0x02), align = 8),
                Section(".rdata", SectionKind.RODATA, "const\u0000".toByteArray(), align = 4),
                Section(".bss", SectionKind.BSS, ByteArray(128), align = 16),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(4, pe.sections.size)
        assertTrue(pe.sections[0].name.trim('\u0000') == ".text")
        assertTrue(pe.sections[1].name.trim('\u0000') == ".data")
        assertTrue(pe.sections[2].name.trim('\u0000') == ".rdata")
        assertTrue(pe.sections[3].name.trim('\u0000') == ".bss")
    }

    @Test
    fun `COFF text section data is preserved exactly`() {
        val code = byteArrayOf(
            0x55, 0x48, 0x89.toByte(), 0xE5.toByte(),
            0x31, 0xC0.toByte(),
            0x5D, 0xC3.toByte(),
        )
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val text = pe.sections.first()
        for (i in code.indices) {
            assertEquals(code[i], text.data[i], "Byte $i mismatch in .text")
        }
    }

    @Test
    fun `COFF rodata section is read-only`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".rdata", SectionKind.RODATA, byteArrayOf(1, 2, 3, 4), align = 4)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val rdata = pe.sections.first()
        assertTrue(rdata.isInitializedData)
        assertTrue(rdata.isReadable)
        assertFalse(rdata.isWritable)
        assertFalse(rdata.isExecutable)
    }

    @Test
    fun `COFF bss section has uninitialized data flag`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(256), align = 16)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val bss = pe.sections.first()
        assertTrue(bss.isUninitializedData)
        assertTrue(bss.isReadable)
        assertTrue(bss.isWritable)
    }

    // COFF symbol tests

    @Test
    fun `COFF global function symbol is external and function`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("myEntry", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val sym = pe.symbols.first { it.name == "myEntry" }
        assertTrue(sym.isExternal)
        assertTrue(sym.isFunction)
        assertFalse(sym.isUndefined)
    }

    @Test
    fun `COFF local symbol is static`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol("internal", value = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val sym = pe.symbols.first { it.name == "internal" }
        assertFalse(sym.isExternal)
        assertTrue(sym.isStatic)
    }

    @Test
    fun `COFF undefined symbol has section number 0`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val sym = pe.symbols.first { it.name == "ExitProcess" }
        assertTrue(sym.isExternal)
        assertTrue(sym.isUndefined)
    }

    @Test
    fun `COFF with 50 symbols preserves all`() {
        val symbols = (0 until 50).map { i ->
            Symbol("func_$i", value = i.toLong() * 2, size = 2, section = ".text",
                binding = if (i % 2 == 0) SymbolBinding.GLOBAL else SymbolBinding.LOCAL,
                kind = SymbolKind.FUNCTION)
        }
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(100), align = 16)),
            symbols = symbols,
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        for (i in 0 until 50) {
            assertNotNull(
                pe.symbols.firstOrNull { it.name == "func_$i" },
                "Missing symbol func_$i"
            )
        }
    }

    @Test
    fun `COFF symbols across multiple sections`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(16), align = 8),
            ),
            symbols = listOf(
                Symbol("code_fn", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("data_var", value = 0, size = 8, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val codeSym = pe.symbols.first { it.name == "code_fn" }
        assertEquals(1, codeSym.sectionNumber) // .text is section 1

        val dataSym = pe.symbols.first { it.name == "data_var" }
        assertEquals(2, dataSym.sectionNumber) // .data is section 2
    }

    // COFF relocation tests

    @Test
    fun `COFF REL32 relocation is preserved`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("target_fn", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "target_fn",
                    type = RelocationType.COFF_X86_64.REL32, section = ".text"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val text = pe.sections.first()
        assertEquals(1, text.numberOfRelocations)
    }

    @Test
    fun `COFF ADDR64 relocation is preserved`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".data", SectionKind.DATA, ByteArray(16), align = 8)),
            symbols = listOf(
                Symbol("global_addr", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "global_addr",
                    type = RelocationType.COFF_X86_64.ADDR64, section = ".data"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val data = pe.sections.first()
        assertEquals(1, data.numberOfRelocations)
    }

    @Test
    fun `COFF multiple relocations on same section`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 1)),
            symbols = listOf(
                Symbol("fn_a", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("fn_b", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("fn_c", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "fn_a",
                    type = RelocationType.COFF_X86_64.REL32, section = ".text"),
                Relocation(offset = 10, symbol = "fn_b",
                    type = RelocationType.COFF_X86_64.REL32, section = ".text"),
                Relocation(offset = 20, symbol = "fn_c",
                    type = RelocationType.COFF_X86_64.REL32, section = ".text"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(3, pe.sections.first().numberOfRelocations)
    }

    @Test
    fun `ELF reloc types are mapped to COFF types`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 1)),
            symbols = listOf(
                Symbol("ext_fn", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "ext_fn",
                    type = RelocationType.X86_64.PLT32, section = ".text"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertTrue(pe.sections.first().numberOfRelocations > 0,
            "PLT32 should be mapped to COFF REL32")
    }

    // PE full executable tests via PeWriter

    @Test
    fun `PeWriter writeFlat produces valid PE executable`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        assertTrue(pe.isPe)
        assertTrue(pe.isExecutable)
        assertFalse(pe.isDll)
        assertEquals(PeConstants.MACHINE_AMD64, pe.coffHeader.machine)
    }

    @Test
    fun `PE has correct subsystem`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        val opt = pe.optionalHeader!!
        assertEquals(3, opt.subsystem) // WINDOWS_CUI = console
    }

    @Test
    fun `PE has import directory data directory`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        assertTrue(pe.dataDirectories.size >= 2)
        val importDir = pe.dataDirectories[1] // index 1 = import directory
        assertFalse(importDir.isEmpty)
    }

    @Test
    fun `PE imports kernel32 functions`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        val kernel32 = pe.importDirectories.first { it.name == "kernel32.dll" }
        val names = kernel32.entries.map { it.name }
        assertTrue("ExitProcess" in names)
        assertTrue("GetStdHandle" in names)
        assertTrue("WriteFile" in names)
    }

    @Test
    fun `PE with rodata has rdata section`() {
        val code = byteArrayOf(0xC3.toByte())
        val rodata = "Hello from PE!\u0000".toByteArray(Charsets.US_ASCII)
        val bytes = PeWriter.writeFlat(code, rodata)
        val pe = PeReader.read(bytes)

        val rdata = pe.sectionByName(".rdata")
        assertNotNull(rdata)
        assertTrue(rdata!!.data.size >= rodata.size)
    }

    @Test
    fun `PE text section is executable and readable`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        val text = pe.sectionByName(".text")!!
        assertTrue(text.isCode)
        assertTrue(text.isExecutable)
        assertTrue(text.isReadable)
    }

    @Test
    fun `PE no CLR metadata for native executable`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        assertNull(pe.clrMetadata)
        assertFalse(pe.isManagedAssembly)
        assertFalse(pe.isILOnly)
        assertFalse(pe.isMixedMode)
    }

    // PE ObjectFile projection

    @Test
    fun `PE toObjectFile has correct format and arch`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)

        assertEquals(ObjectFormat.PE_COFF, obj.format)
        assertEquals(ArchType.X86_64, obj.arch.arch)
        assertEquals(OsAbi.WINDOWS, obj.metadata.osAbi)
    }

    @Test
    fun `PE toObjectFile has executable flag`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)

        assertTrue(ObjectFlag.EXECUTABLE in obj.metadata.flags)
    }

    @Test
    fun `PE toObjectFile imports are preserved`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)

        assertTrue(obj.imports.any { it.moduleName == "kernel32.dll" })
        assertTrue(obj.imports.any { it.symbolName == "ExitProcess" })
    }

    @Test
    fun `PE toObjectFile has text section`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)

        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT })
    }

    // PeLinker round-trip tests

    @Test
    fun `PeLinker output is valid PE with imports`() {
        val textCode = byteArrayOf(
            0x55, 0x48, 0x89.toByte(), 0xE5.toByte(),
            0x5D, 0xC3.toByte(),
        )
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = listOf(
                Section(".text", SectionKind.TEXT, textCode, align = 16),
            ),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
            imports = listOf(
                ImportEntry(moduleName = "msvcrt.dll", symbolName = "printf"),
            ),
        )

        val linked = PeLinker().link(listOf(obj))
        assertTrue(PeReader.canRead(linked))

        val pe = PeReader.read(linked)
        assertTrue(pe.isPe)
        assertTrue(pe.isExecutable)
        assertTrue(pe.importDirectories.any { it.name == "msvcrt.dll" })
    }

    @Test
    fun `PeLinker with multiple imports from same DLL`() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
            ),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
            imports = listOf(
                ImportEntry(moduleName = "kernel32.dll", symbolName = "CreateFileW"),
                ImportEntry(moduleName = "kernel32.dll", symbolName = "ReadFile"),
                ImportEntry(moduleName = "kernel32.dll", symbolName = "CloseHandle"),
            ),
        )

        val linked = PeLinker().link(listOf(obj))
        val pe = PeReader.read(linked)

        val k32 = pe.importDirectories.first { it.name == "kernel32.dll" }
        val importNames = k32.entries.map { it.name }
        assertTrue("CreateFileW" in importNames)
        assertTrue("ReadFile" in importNames)
        assertTrue("CloseHandle" in importNames)
    }

    @Test
    fun `PeLinker with imports from multiple DLLs`() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(16), align = 16),
            ),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
            imports = listOf(
                ImportEntry(moduleName = "kernel32.dll", symbolName = "ExitProcess"),
                ImportEntry(moduleName = "user32.dll", symbolName = "MessageBoxA"),
            ),
        )

        val linked = PeLinker().link(listOf(obj))
        val pe = PeReader.read(linked)

        assertTrue(pe.importDirectories.any { it.name == "kernel32.dll" })
        assertTrue(pe.importDirectories.any { it.name == "user32.dll" })
    }

    // COFF edge cases

    @Test
    fun `COFF with single empty section`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(0), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        assertTrue(bytes.size >= 20, "Should have at least COFF header")

        val pe = PeReader.read(bytes)
        assertEquals(1, pe.sections.size)
    }

    @Test
    fun `COFF with large data section`() {
        val largeData = ByteArray(16384) { (it % 253).toByte() }
        val obj = makeCoffObject(
            sections = listOf(Section(".data", SectionKind.DATA, largeData, align = 16)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val data = pe.sections.first()
        for (i in largeData.indices) {
            assertEquals(largeData[i], data.data[i], "Mismatch at byte $i")
        }
    }

    @Test
    fun `COFF is not a full PE`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertFalse(pe.isPe, "COFF object should not be detected as full PE")
        assertNull(pe.optionalHeader, "COFF object should not have optional header")
    }

    // PeReader.canRead

    @Test
    fun `PeReader canRead accepts PE files`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        assertTrue(PeReader.canRead(bytes))
    }

    @Test
    fun `PeReader canRead accepts COFF files`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        assertTrue(PeReader.canRead(bytes))
    }

    @Test
    fun `PeReader canRead rejects ELF files`() {
        assertFalse(PeReader.canRead(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())))
    }

    @Test
    fun `PeReader canRead rejects tiny input`() {
        assertFalse(PeReader.canRead(byteArrayOf(0x00)))
        assertFalse(PeReader.canRead(ByteArray(0)))
    }

    // detectFormat

    @Test
    fun `detectFormat returns PE_COFF for PE file`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        assertEquals(ObjectFormat.PE_COFF, detectFormat(bytes))
    }

    @Test
    fun `PeObjectFileReader interface`() {
        val reader = PeObjectFileReader()
        assertEquals(ObjectFormat.PE_COFF, reader.format)

        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        assertTrue(reader.canRead(bytes))

        val obj = reader.read(bytes)
        assertEquals(ObjectFormat.PE_COFF, obj.format)
    }

    @Test
    fun `sectionByRVA finds the correct section`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        val text = pe.sectionByName(".text")!!
        val found = pe.sectionByRVA(text.virtualAddress)
        assertNotNull(found)
        assertEquals(".text", found!!.name)
    }

    @Test
    fun `PE optional header has valid alignment values`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        val opt = pe.optionalHeader!!
        assertTrue(opt.sectionAlignment > 0)
        assertTrue(opt.fileAlignment > 0)
        assertTrue(opt.imageBase > 0)
        assertTrue(opt.sectionAlignment >= opt.fileAlignment,
            "Section alignment should be >= file alignment")
    }
}
