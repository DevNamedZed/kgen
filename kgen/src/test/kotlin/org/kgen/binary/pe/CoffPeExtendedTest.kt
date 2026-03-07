package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CoffPeExtendedTest {

    private fun makeCoffObject(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
        arch: Architecture = Architecture.X86_64_WINDOWS,
        imports: List<ImportEntry> = emptyList(),
    ) = ObjectFile(
        format = ObjectFormat.PE_COFF,
        arch = arch,
        sections = sections,
        symbols = symbols,
        relocations = relocations,
        imports = imports,
    )

    // --- COFF Object Writer: String Table ---

    @Test
    fun `COFF long symbol name over 8 chars is stored in string table`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("veryLongFunctionName", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val sym = pe.symbols.firstOrNull { it.name == "veryLongFunctionName" }
        assertNotNull(sym, "Long symbol name should survive string table round-trip")
        assertTrue(sym!!.isExternal)
        assertTrue(sym.isFunction)
    }

    @Test
    fun `COFF long section name over 8 chars uses string table offset`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".longSectionName", SectionKind.DATA, byteArrayOf(42), align = 1),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(2, pe.sections.size)
        // The long name should be resolved from string table
        val longSec = pe.sections[1]
        assertTrue(longSec.name.contains("longSectionName") || longSec.name.startsWith("/"),
            "Long section name should be in string table format or resolved: '${longSec.name}'")
    }

    @Test
    fun `COFF multiple long symbol names all resolve correctly`() {
        val symbols = (0 until 10).map { i ->
            Symbol("longSymbolName_function_$i", value = i.toLong() * 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
        }
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(40), align = 1)),
            symbols = symbols,
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        for (i in 0 until 10) {
            assertNotNull(
                pe.symbols.firstOrNull { it.name == "longSymbolName_function_$i" },
                "Missing long symbol longSymbolName_function_$i"
            )
        }
    }

    // --- COFF Object Writer: Multiple Sections ---

    @Test
    fun `COFF with 8 sections preserves order and count`() {
        val sections = listOf(
            Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
            Section(".data", SectionKind.DATA, byteArrayOf(1), align = 8),
            Section(".rdata", SectionKind.RODATA, byteArrayOf(2), align = 4),
            Section(".bss", SectionKind.BSS, ByteArray(64), align = 16),
            Section(".text2", SectionKind.TEXT, byteArrayOf(0x90.toByte()), align = 1),
            Section(".data2", SectionKind.DATA, byteArrayOf(3, 4), align = 4),
            Section(".rdata2", SectionKind.RODATA, byteArrayOf(5, 6, 7), align = 2),
            Section(".bss2", SectionKind.BSS, ByteArray(32), align = 8),
        )
        val obj = makeCoffObject(sections = sections)
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(8, pe.sections.size)
        assertEquals(8, pe.coffHeader.numberOfSections)
    }

    @Test
    fun `COFF section data sizes are preserved`() {
        val textData = ByteArray(100) { (it % 127).toByte() }
        val dataData = ByteArray(200) { (it % 251).toByte() }
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, textData, align = 16),
                Section(".data", SectionKind.DATA, dataData, align = 8),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val text = pe.sections[0]
        assertEquals(textData.size, text.rawDataSize)
        for (i in textData.indices) {
            assertEquals(textData[i], text.data[i], "Text byte $i mismatch")
        }

        val data = pe.sections[1]
        assertEquals(dataData.size, data.rawDataSize)
        for (i in dataData.indices) {
            assertEquals(dataData[i], data.data[i], "Data byte $i mismatch")
        }
    }

    // --- COFF Symbol Visibility ---

    @Test
    fun `COFF mixed global and local symbols have correct storage class`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 1)),
            symbols = listOf(
                Symbol("publicFn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("privateFn", value = 8, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                Symbol("externRef", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val pub = pe.symbols.first { it.name == "publicFn" }
        assertTrue(pub.isExternal)
        assertTrue(pub.isFunction)
        assertFalse(pub.isUndefined)

        val priv = pe.symbols.first { it.name == "privateFn" }
        assertTrue(priv.isStatic)
        assertFalse(priv.isExternal)

        val ext = pe.symbols.first { it.name == "externRef" }
        assertTrue(ext.isExternal)
        assertTrue(ext.isUndefined)
    }

    @Test
    fun `COFF data symbol is not a function`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".data", SectionKind.DATA, ByteArray(8), align = 4)),
            symbols = listOf(
                Symbol("globalVar", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val sym = pe.symbols.first { it.name == "globalVar" }
        assertTrue(sym.isExternal)
        assertFalse(sym.isFunction)
    }

    @Test
    fun `COFF section symbols are emitted for each section`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(1), align = 1),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        // Section symbols should be static
        val textSym = pe.symbols.firstOrNull { it.name == ".text" }
        assertNotNull(textSym, "Should have .text section symbol")
        assertTrue(textSym!!.isStatic)

        val dataSym = pe.symbols.firstOrNull { it.name == ".data" }
        assertNotNull(dataSym, "Should have .data section symbol")
        assertTrue(dataSym!!.isStatic)
    }

    // --- COFF Relocation Types ---

    @Test
    fun `COFF ADDR32NB relocation is preserved`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "target",
                    type = RelocationType.COFF_X86_64.ADDR32NB, section = ".text"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(1, pe.sections.first().numberOfRelocations)
    }

    @Test
    fun `COFF relocations across different sections`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(16), align = 1),
                Section(".data", SectionKind.DATA, ByteArray(16), align = 1),
            ),
            symbols = listOf(
                Symbol("fn_target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("data_target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 2, symbol = "fn_target",
                    type = RelocationType.COFF_X86_64.REL32, section = ".text"),
                Relocation(offset = 0, symbol = "data_target",
                    type = RelocationType.COFF_X86_64.ADDR64, section = ".data"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(1, pe.sections[0].numberOfRelocations, ".text should have 1 relocation")
        assertEquals(1, pe.sections[1].numberOfRelocations, ".data should have 1 relocation")
    }

    @Test
    fun `COFF many relocations on single section`() {
        val numRelocs = 20
        val symbols = (0 until numRelocs).map { i ->
            Symbol("sym_$i", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)
        }
        val relocations = (0 until numRelocs).map { i ->
            Relocation(offset = i.toLong() * 4, symbol = "sym_$i",
                type = RelocationType.COFF_X86_64.REL32, section = ".text")
        }
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(numRelocs * 4 + 4), align = 1)),
            symbols = symbols,
            relocations = relocations,
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(numRelocs, pe.sections.first().numberOfRelocations)
    }

    @Test
    fun `COFF ELF PC32 relocation is mapped to COFF REL32`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "target",
                    type = RelocationType.X86_64.PC32, section = ".text"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(1, pe.sections.first().numberOfRelocations,
            "PC32 should be mapped to COFF REL32")
    }

    @Test
    fun `COFF ELF R_64 relocation is mapped to COFF ADDR64`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".data", SectionKind.DATA, ByteArray(16), align = 8)),
            symbols = listOf(
                Symbol("ptr", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "ptr",
                    type = RelocationType.X86_64.R_64, section = ".data"),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(1, pe.sections.first().numberOfRelocations)
    }

    // --- COFF Architecture Variants ---

    @Test
    fun `COFF ARM64 object has correct machine type`() {
        val obj = makeCoffObject(
            arch = Architecture(ArchType.AARCH64, os = "windows"),
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()), align = 4)),
        )
        val bytes = CoffObjectWriter(machine = PeConstants.MACHINE_ARM64).write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(PeConstants.MACHINE_ARM64, pe.coffHeader.machine)
    }

    @Test
    fun `COFF i386 object has correct machine type`() {
        val obj = makeCoffObject(
            arch = Architecture(ArchType.X86, os = "windows"),
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = CoffObjectWriter(machine = PeConstants.MACHINE_I386).write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(PeConstants.MACHINE_I386, pe.coffHeader.machine)
    }

    // --- COFF Edge Cases ---

    @Test
    fun `COFF empty object with no sections symbols or relocations`() {
        val obj = makeCoffObject()
        val bytes = CoffObjectWriter().write(obj)
        assertTrue(bytes.size >= 20, "Should have COFF header")

        val pe = PeReader.read(bytes)
        assertEquals(0, pe.sections.size)
        // Should only have string table size (4 bytes for the size field)
    }

    @Test
    fun `COFF with only symbols and no sections`() {
        val obj = makeCoffObject(
            symbols = listOf(
                Symbol("unresolved", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(0, pe.sections.size)
        val sym = pe.symbols.firstOrNull { it.name == "unresolved" }
        assertNotNull(sym)
        assertTrue(sym!!.isUndefined)
    }

    @Test
    fun `COFF large code section round-trips correctly`() {
        val largeCode = ByteArray(65536) { (it % 251).toByte() }
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, largeCode, align = 16)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        val text = pe.sections.first()
        assertEquals(largeCode.size, text.rawDataSize)
        for (i in 0 until minOf(100, largeCode.size)) {
            assertEquals(largeCode[i], text.data[i], "Byte $i mismatch in large code")
        }
        // Verify last bytes too
        for (i in largeCode.size - 10 until largeCode.size) {
            assertEquals(largeCode[i], text.data[i], "Byte $i mismatch at end of large code")
        }
    }

    @Test
    fun `COFF with many sections and symbols produces valid output`() {
        val sections = (0 until 10).map { i ->
            Section(".sec$i", SectionKind.DATA, ByteArray(i + 1) { it.toByte() }, align = 4)
        }
        val symbols = (0 until 20).map { i ->
            Symbol("sym_$i", value = 0, section = ".sec${i % 10}",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA)
        }
        val obj = makeCoffObject(sections = sections, symbols = symbols)
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(10, pe.sections.size)
        for (i in 0 until 20) {
            assertNotNull(pe.symbols.firstOrNull { it.name == "sym_$i" }, "Missing sym_$i")
        }
    }

    // --- PE Writer Tests ---

    @Test
    fun `PE with large code has text section big enough`() {
        val code = ByteArray(32768) { (it % 256).toByte() }
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        val text = pe.sectionByName(".text")!!
        assertTrue(text.data.size >= code.size)
        for (i in code.indices) {
            assertEquals(code[i], text.data[i], "Code byte $i mismatch")
        }
    }

    @Test
    fun `PE sections are page-aligned`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        val opt = pe.optionalHeader!!
        for (sec in pe.sections) {
            assertEquals(0, sec.virtualAddress % opt.sectionAlignment,
                "Section ${sec.name} RVA 0x${sec.virtualAddress.toString(16)} not aligned to section alignment")
        }
    }

    @Test
    fun `PE file-aligned section offsets`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        val opt = pe.optionalHeader!!
        for (sec in pe.sections) {
            if (sec.rawDataOffset > 0) {
                assertEquals(0, sec.rawDataOffset % opt.fileAlignment,
                    "Section ${sec.name} raw offset not aligned to file alignment")
            }
        }
    }

    @Test
    fun `PE image size is section-aligned`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        val opt = pe.optionalHeader!!
        assertEquals(0, opt.sizeOfImage % opt.sectionAlignment,
            "Image size should be section-aligned")
    }

    @Test
    fun `PE with both code and rodata has correct section order`() {
        val code = byteArrayOf(0xCC.toByte())
        val rodata = "test data\u0000".toByteArray()
        val bytes = PeWriter.writeFlat(code, rodata)
        val pe = PeReader.read(bytes)

        val sectionNames = pe.sections.map { it.name }
        assertTrue(".text" in sectionNames)
        assertTrue(".rdata" in sectionNames)
        assertTrue(".idata" in sectionNames)

        // .text should come before .rdata, .rdata before .idata
        val textIdx = sectionNames.indexOf(".text")
        val rdataIdx = sectionNames.indexOf(".rdata")
        val idataIdx = sectionNames.indexOf(".idata")
        assertTrue(textIdx < rdataIdx, ".text should precede .rdata")
        assertTrue(rdataIdx < idataIdx, ".rdata should precede .idata")
    }

    @Test
    fun `PE has PE32Plus magic`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        assertTrue(pe.isPe32Plus)
        assertEquals(PeConstants.PE32PLUS_MAGIC, pe.optionalHeader!!.magic)
    }

    @Test
    fun `PE entry point RVA is in text section`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        val text = pe.sectionByName(".text")!!
        val entryRVA = pe.optionalHeader!!.entryPointRVA
        assertTrue(entryRVA >= text.virtualAddress,
            "Entry point should be within .text")
        assertTrue(entryRVA < text.virtualAddress + maxOf(text.virtualSize, text.rawDataSize),
            "Entry point should be within .text bounds")
    }

    // --- PE Import Table Tests ---

    @Test
    fun `PE import entries have hint values`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        val kernel32 = pe.importDirectories.first { it.name == "kernel32.dll" }
        for (entry in kernel32.entries) {
            assertFalse(entry.isOrdinal, "Default imports should be by name, not ordinal")
            assertNotNull(entry.name)
        }
    }

    @Test
    fun `PE has IAT data directory`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        assertTrue(pe.dataDirectories.size > PeDataDirectory.IAT)
        val iat = pe.dataDirectories[PeDataDirectory.IAT]
        assertFalse(iat.isEmpty, "IAT data directory should not be empty")
    }

    // --- PeLinker Tests ---

    @Test
    fun `PeLinker with rodata section round-trips`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT,
                    byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0x5D, 0xC3.toByte()),
                    align = 16),
                Section(".rodata", SectionKind.RODATA, "Hello\u0000".toByteArray(), align = 1),
            ),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            imports = listOf(ImportEntry(moduleName = "kernel32.dll", symbolName = "ExitProcess")),
        )

        val linked = PeLinker().link(listOf(obj))
        val pe = PeReader.read(linked)

        assertTrue(pe.isPe)
        assertNotNull(pe.sectionByName(".text"))
        assertNotNull(pe.sectionByName(".rdata"))
    }

    @Test
    fun `PeLinker multiple objects merged into single PE`() {
        val obj1 = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT,
                    byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0x5D, 0xC3.toByte()),
                    align = 16),
            ),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            imports = listOf(ImportEntry(moduleName = "kernel32.dll", symbolName = "ExitProcess")),
        )
        val obj2 = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT,
                    byteArrayOf(0xC3.toByte()),
                    align = 1),
            ),
            symbols = listOf(
                Symbol("helper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )

        val linked = PeLinker().link(listOf(obj1, obj2))
        val pe = PeReader.read(linked)

        assertTrue(pe.isPe)
        assertTrue(pe.isExecutable)
    }

    // --- PeDllLinker Tests ---

    @Test
    fun `PeDllLinker produces DLL with exports`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT,
                    byteArrayOf(0x31, 0xC0.toByte(), 0xC3.toByte()),
                    align = 16),
            ),
            symbols = listOf(
                Symbol("myExport", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )

        val linked = PeDllLinker(dllName = "test.dll").link(listOf(obj))
        val pe = PeReader.read(linked)

        assertTrue(pe.isPe)
        assertTrue(pe.isDll)
        assertNotNull(pe.exportDirectory)
        assertTrue(pe.exportDirectory!!.entries.any { it.name == "myExport" })
    }

    @Test
    fun `PeDllLinker with imports and exports`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(16), align = 16),
            ),
            symbols = listOf(
                Symbol("dllFunc", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            imports = listOf(
                ImportEntry(moduleName = "kernel32.dll", symbolName = "GetLastError"),
            ),
        )

        val linked = PeDllLinker(dllName = "mylib.dll").link(listOf(obj))
        val pe = PeReader.read(linked)

        assertTrue(pe.isDll)
        assertNotNull(pe.exportDirectory)
        assertTrue(pe.exportDirectory!!.entries.any { it.name == "dllFunc" })
        assertTrue(pe.importDirectories.any { it.name == "kernel32.dll" })
    }

    @Test
    fun `PeDllLinker export directory has correct DLL name`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
            ),
            symbols = listOf(
                Symbol("exportedFn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )

        val linked = PeDllLinker(dllName = "myDll.dll").link(listOf(obj))
        val pe = PeReader.read(linked)

        assertEquals("myDll.dll", pe.exportDirectory!!.name)
    }

    @Test
    fun `PeDllLinker multiple exports are sorted`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(30), align = 16),
            ),
            symbols = listOf(
                Symbol("zFunc", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("aFunc", value = 10, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("mFunc", value = 20, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )

        val linked = PeDllLinker(dllName = "sorted.dll").link(listOf(obj))
        val pe = PeReader.read(linked)

        val exportNames = pe.exportDirectory!!.entries.mapNotNull { it.name }
        assertEquals(listOf("aFunc", "mFunc", "zFunc"), exportNames)
    }

    // --- ObjectFile Projection Tests ---

    @Test
    fun `COFF toObjectFile has RELOCATABLE flag`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)
        val projected = PeReader.toObjectFile(pe)

        assertTrue(ObjectFlag.RELOCATABLE in projected.metadata.flags,
            "COFF object should have RELOCATABLE flag")
        assertFalse(ObjectFlag.EXECUTABLE in projected.metadata.flags,
            "COFF object should not have EXECUTABLE flag")
    }

    @Test
    fun `PE DLL toObjectFile has SHARED_LIBRARY flag`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
            ),
            symbols = listOf(
                Symbol("exportFn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )

        val linked = PeDllLinker(dllName = "test.dll").link(listOf(obj))
        val pe = PeReader.read(linked)
        val projected = PeReader.toObjectFile(pe)

        assertTrue(ObjectFlag.SHARED_LIBRARY in projected.metadata.flags)
    }

    @Test
    fun `PE toObjectFile dynamicInfo has correct imageBase`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val projected = PeReader.toObjectFile(pe)

        assertNotNull(projected.dynamicInfo)
        assertEquals(0x140000000L, projected.dynamicInfo!!.imageBase)
    }

    @Test
    fun `PE toObjectFile dynamicInfo has subsystem`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val projected = PeReader.toObjectFile(pe)

        assertNotNull(projected.dynamicInfo)
        assertNotNull(projected.dynamicInfo!!.subsystem)
    }

    @Test
    fun `DLL toObjectFile exports are projected`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT,
                    byteArrayOf(0x31, 0xC0.toByte(), 0xC3.toByte()), align = 1),
            ),
            symbols = listOf(
                Symbol("exportedFunc", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val linked = PeDllLinker(dllName = "exp.dll").link(listOf(obj))
        val pe = PeReader.read(linked)
        val projected = PeReader.toObjectFile(pe)

        assertTrue(projected.exports.any { it.symbolName == "exportedFunc" })
    }

    // --- PeReader.canRead Validation ---

    @Test
    fun `canRead detects COFF ARM64 object`() {
        val obj = makeCoffObject(
            arch = Architecture(ArchType.AARCH64, os = "windows"),
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0x00), align = 1)),
        )
        val bytes = CoffObjectWriter(machine = PeConstants.MACHINE_ARM64).write(obj)
        assertTrue(PeReader.canRead(bytes))
    }

    @Test
    fun `canRead rejects random bytes`() {
        assertFalse(PeReader.canRead(byteArrayOf(0x12, 0x34, 0x56, 0x78)))
    }

    // --- COFF Symbol Value Preservation ---

    @Test
    fun `COFF symbol values are preserved`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 1)),
            symbols = listOf(
                Symbol("fn_at_0", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("fn_at_16", value = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("fn_at_32", value = 32, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(0L, pe.symbols.first { it.name == "fn_at_0" }.value)
        assertEquals(16L, pe.symbols.first { it.name == "fn_at_16" }.value)
        assertEquals(32L, pe.symbols.first { it.name == "fn_at_32" }.value)
    }

    // --- COFF COFF Header Fields ---

    @Test
    fun `COFF header has zero optional header size`() {
        val obj = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        assertEquals(0, pe.coffHeader.optionalHeaderSize,
            "COFF object should have 0 optional header size")
    }

    @Test
    fun `COFF header symbol count includes section symbols and user symbols`() {
        val obj = makeCoffObject(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(8), align = 1),
                Section(".data", SectionKind.DATA, ByteArray(4), align = 1),
            ),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = CoffObjectWriter().write(obj)
        val pe = PeReader.read(bytes)

        // 2 section symbols + 1 user symbol = 3
        assertEquals(3, pe.coffHeader.numberOfSymbols)
    }

    // --- COFF Section Alignment Characteristic ---

    @Test
    fun `COFF section alignment characteristic reflects requested alignment`() {
        val obj16 = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
        )
        val bytes16 = CoffObjectWriter().write(obj16)
        val pe16 = PeReader.read(bytes16)

        // Alignment 16 → 0x00500000
        val alignBits16 = (pe16.sections.first().characteristics shr 20) and 0xF
        assertEquals(5, alignBits16, "16-byte alignment should encode as 5")

        val obj1 = makeCoffObject(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes1 = CoffObjectWriter().write(obj1)
        val pe1 = PeReader.read(bytes1)

        val alignBits1 = (pe1.sections.first().characteristics shr 20) and 0xF
        assertEquals(1, alignBits1, "1-byte alignment should encode as 1")
    }
}
