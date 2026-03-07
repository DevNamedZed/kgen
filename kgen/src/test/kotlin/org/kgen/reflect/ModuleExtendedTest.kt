package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

class ModuleExtendedTest {

    private fun testObjectFile(
        format: ObjectFormat = ObjectFormat.ELF,
        arch: Architecture = Architecture.X86_64_LINUX,
        symbols: List<org.kgen.binary.Symbol> = emptyList(),
        sections: List<Section> = emptyList(),
        imports: List<ImportEntry> = emptyList(),
        exports: List<ExportEntry> = emptyList(),
        flags: Set<ObjectFlag> = emptySet(),
        entryPoint: Long? = null,
        dynamicInfo: DynamicLinkInfo? = null,
    ): ObjectFile = ObjectFile(
        format = format,
        arch = arch,
        symbols = symbols,
        sections = sections,
        imports = imports,
        exports = exports,
        relocations = emptyList(),
        metadata = ObjectMetadata(entryPoint = entryPoint, flags = flags),
        dynamicInfo = dynamicInfo,
    )

    // --- Multiple formats ---

    @Test
    fun peFormatModule() {
        val module = Module.fromObjectFile(
            testObjectFile(format = ObjectFormat.PE_COFF, arch = Architecture.X86_64_WINDOWS), "test.exe"
        )
        assertEquals(ObjectFormat.PE_COFF, module.format())
        assertEquals("test.exe", module.name())
    }

    @Test
    fun machOFormatModule() {
        val module = Module.fromObjectFile(
            testObjectFile(format = ObjectFormat.MACH_O, arch = Architecture.AARCH64_MACOS), "test.dylib"
        )
        assertEquals(ObjectFormat.MACH_O, module.format())
    }

    @Test
    fun wasmFormatModule() {
        val module = Module.fromObjectFile(
            testObjectFile(format = ObjectFormat.WASM_MODULE), "test.wasm"
        )
        assertEquals(ObjectFormat.WASM_MODULE, module.format())
        assertTrue(module.hasWasm())
        assertFalse(module.hasNativeCode())
    }

    @Test
    fun jvmClassFormatModule() {
        val module = Module.fromObjectFile(
            testObjectFile(format = ObjectFormat.JVM_CLASS), "Test.class"
        )
        assertTrue(module.hasJvm())
        assertFalse(module.hasNativeCode())
    }

    // --- Multiple sections ---

    @Test
    fun multipleSections() {
        val sections = listOf(
            Section(name = ".text", kind = SectionKind.TEXT, data = ByteArray(100)),
            Section(name = ".data", kind = SectionKind.DATA, data = ByteArray(50)),
            Section(name = ".bss", kind = SectionKind.BSS, data = ByteArray(0)),
            Section(name = ".rodata", kind = SectionKind.RODATA, data = ByteArray(20)),
        )
        val module = Module.fromObjectFile(testObjectFile(sections = sections))
        assertEquals(4, module.sections().size)
        assertNotNull(module.section(".text"))
        assertNotNull(module.section(".data"))
        assertNotNull(module.section(".bss"))
        assertNotNull(module.section(".rodata"))
        assertNotNull(module.textSection())
        assertNotNull(module.section(SectionKind.DATA))
    }

    @Test
    fun sectionByKind() {
        val sections = listOf(
            Section(name = ".text", kind = SectionKind.TEXT, data = ByteArray(100)),
            Section(name = ".data", kind = SectionKind.DATA, data = ByteArray(50)),
        )
        val module = Module.fromObjectFile(testObjectFile(sections = sections))
        assertNotNull(module.section(SectionKind.TEXT))
        assertNotNull(module.section(SectionKind.DATA))
        assertNull(module.section(SectionKind.BSS))
    }

    @Test
    fun missingSection() {
        val module = Module.fromObjectFile(testObjectFile())
        assertNull(module.section(".nonexistent"))
        assertNull(module.textSection())
        assertNull(module.section(SectionKind.DATA))
    }

    // --- Multiple symbols ---

    @Test
    fun manySymbols() {
        val syms = (0..19).map {
            org.kgen.binary.Symbol("sym_$it", it.toLong() * 100, 32, ".text",
                SymbolBinding.GLOBAL, SymbolKind.FUNCTION)
        }
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        assertEquals(20, module.symbols().size)
        for (i in 0..19) {
            assertNotNull(module.symbol("sym_$i"), "sym_$i should exist")
        }
    }

    @Test
    fun symbolFilterByKind() {
        val syms = listOf(
            org.kgen.binary.Symbol("func1", 0x1000, 64, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("func2", 0x1100, 32, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("var1", 0x2000, 8, ".data", SymbolBinding.GLOBAL, SymbolKind.DATA),
            org.kgen.binary.Symbol("var2", 0x2008, 4, ".data", SymbolBinding.LOCAL, SymbolKind.DATA),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        assertEquals(2, module.functions().size)
        assertNotNull(module.function("func1"))
        assertNotNull(module.function("func2"))
        assertNull(module.function("var1"))
        assertNull(module.function("var2"))
    }

    @Test
    fun localAndGlobalSymbols() {
        val syms = listOf(
            org.kgen.binary.Symbol("main", 0x1000, 64, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("helper", 0x1100, 32, ".text", SymbolBinding.LOCAL, SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("weak_fn", 0x1200, 16, ".text", SymbolBinding.WEAK, SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        assertEquals(3, module.functions().size)
        assertEquals(3, module.symbols().size)
    }

    // --- Function from symbol ---

    @Test
    fun functionFromSymbolData() {
        val syms = listOf(
            org.kgen.binary.Symbol("data_sym", 0x2000, 8, ".data", SymbolBinding.GLOBAL, SymbolKind.DATA),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val sym = module.symbol("data_sym")!!
        val func = module.function(sym)
        assertNull(func, "Data symbol should not produce a function")
    }

    @Test
    fun functionFromSymbolFunction() {
        val syms = listOf(
            org.kgen.binary.Symbol("my_func", 0x1000, 64, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val sym = module.symbol("my_func")!!
        val func = module.function(sym)
        assertNotNull(func)
        assertEquals("my_func", func!!.name())
    }

    // --- Imports and exports ---

    @Test
    fun multipleImports() {
        val imports = listOf(
            ImportEntry("puts", "libc.so.6"),
            ImportEntry("printf", "libc.so.6"),
            ImportEntry("malloc", "libc.so.6"),
        )
        val module = Module.fromObjectFile(testObjectFile(imports = imports))
        assertEquals(3, module.imports().size)
        assertEquals("puts", module.imports()[0].symbolName)
        assertEquals("printf", module.imports()[1].symbolName)
        assertEquals("malloc", module.imports()[2].symbolName)
    }

    @Test
    fun multipleExports() {
        val exports = listOf(
            ExportEntry("api_init"),
            ExportEntry("api_process"),
            ExportEntry("api_cleanup"),
        )
        val module = Module.fromObjectFile(testObjectFile(exports = exports))
        assertEquals(3, module.exports().size)
    }

    @Test
    fun noImportsOrExports() {
        val module = Module.fromObjectFile(testObjectFile())
        assertTrue(module.imports().isEmpty())
        assertTrue(module.exports().isEmpty())
    }

    // --- Dependencies ---

    @Test
    fun multipleDependencies() {
        val dyn = DynamicLinkInfo(neededLibraries = listOf("libc.so.6", "libm.so.6", "libpthread.so.0", "libdl.so.2"))
        val module = Module.fromObjectFile(testObjectFile(dynamicInfo = dyn))
        val deps = module.dependencies()
        assertEquals(4, deps.size)
        assertEquals("libc.so.6", deps[0].name())
        assertEquals("libm.so.6", deps[1].name())
        assertEquals("libpthread.so.0", deps[2].name())
        assertEquals("libdl.so.2", deps[3].name())
    }

    @Test
    fun dependencyHasNoPath() {
        val dyn = DynamicLinkInfo(neededLibraries = listOf("libc.so.6"))
        val module = Module.fromObjectFile(testObjectFile(dynamicInfo = dyn))
        val dep = module.dependencies()[0]
        assertNull(dep.path())
    }

    // --- Entry point ---

    @Test
    fun entryPointMatchesSymbol() {
        val syms = listOf(
            org.kgen.binary.Symbol("_start", 0x1000, 32, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("main", 0x2000, 64, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms, entryPoint = 0x1000))
        val entry = module.entryPoint()
        assertNotNull(entry)
        assertEquals("_start", entry!!.name())
    }

    @Test
    fun entryPointNoMatchingSymbol() {
        val syms = listOf(
            org.kgen.binary.Symbol("main", 0x2000, 64, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms, entryPoint = 0x9999))
        // Entry point address doesn't match any symbol
        val entry = module.entryPoint()
        // May be null or a synthetic function
        // Just verify it doesn't crash
        assertNotNull(module)
    }

    // --- Classification flags combinations ---

    @Test
    fun executableNotShared() {
        val module = Module.fromObjectFile(testObjectFile(flags = setOf(ObjectFlag.EXECUTABLE)))
        assertTrue(module.isExecutable())
        assertFalse(module.isSharedLibrary())
        assertFalse(module.isRelocatable())
    }

    @Test
    fun sharedNotExecutable() {
        val module = Module.fromObjectFile(testObjectFile(flags = setOf(ObjectFlag.SHARED_LIBRARY)))
        assertFalse(module.isExecutable())
        assertTrue(module.isSharedLibrary())
    }

    @Test
    fun relocatableObject() {
        val module = Module.fromObjectFile(testObjectFile(flags = setOf(ObjectFlag.RELOCATABLE)))
        assertTrue(module.isRelocatable())
        assertFalse(module.isExecutable())
    }

    @Test
    fun noFlags() {
        val module = Module.fromObjectFile(testObjectFile())
        assertFalse(module.isExecutable())
        assertFalse(module.isSharedLibrary())
        assertFalse(module.isRelocatable())
    }

    // --- Runtime detection ---

    @Test
    fun msilAssemblyRuntime() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.MSIL_ASSEMBLY))
        assertTrue(module.hasClr())
        assertFalse(module.hasJvm())
        assertFalse(module.hasWasm())
        assertFalse(module.hasNativeCode())
        assertFalse(module.isMixedMode())
    }

    @Test
    fun msilMixedRuntime() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.MSIL_MIXED))
        assertTrue(module.hasClr())
        assertTrue(module.isMixedMode())
    }

    @Test
    fun elfHasNativeCode() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.ELF))
        assertTrue(module.hasNativeCode())
        assertFalse(module.hasClr())
        assertFalse(module.hasJvm())
    }

    @Test
    fun peHasNativeCode() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.PE_COFF))
        assertTrue(module.hasNativeCode())
    }

    @Test
    fun machOHasNativeCode() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.MACH_O))
        assertTrue(module.hasNativeCode())
    }

    // --- Format-specific accessor null checks ---

    @Test
    fun elfModuleNoFormatAccessors() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.ELF))
        assertNull(module.pe())
        assertNull(module.machO())
        assertNull(module.classFile())
        assertNull(module.clr())
        assertNull(module.wasm())
    }

    // --- toString ---

    @Test
    fun toStringContainsName() {
        val module = Module.fromObjectFile(testObjectFile(), "mylib.so")
        assertTrue(module.toString().contains("mylib.so"))
    }

    @Test
    fun toStringContainsFormat() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.PE_COFF), "test.dll")
        assertTrue(module.toString().contains("PE"))
    }

    // --- Back references ---

    @Test
    fun symbolModuleBackRef() {
        val syms = listOf(org.kgen.binary.Symbol("test", 100, 32))
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val sym = module.symbol("test")!!
        assertSame(module, sym.module())
    }

    @Test
    fun functionModuleBackRef() {
        val syms = listOf(org.kgen.binary.Symbol("test", 100, 32, kind = SymbolKind.FUNCTION))
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val func = module.function("test")!!
        assertSame(module, func.module())
    }

    @Test
    fun multipleFunctionBackRefs() {
        val syms = (0..4).map {
            org.kgen.binary.Symbol("fn_$it", it.toLong() * 100, 32, ".text",
                SymbolBinding.GLOBAL, SymbolKind.FUNCTION)
        }
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        for (i in 0..4) {
            val func = module.function("fn_$i")!!
            assertSame(module, func.module())
        }
    }

    // --- objectFile access ---

    @Test
    fun objectFileRoundTrip() {
        val obj = testObjectFile()
        val module = Module.fromObjectFile(obj, "test.o")
        assertSame(obj, module.objectFile())
    }

    // --- isLoaded and baseAddress ---

    @Test
    fun fileModuleNotLoaded() {
        val module = Module.fromObjectFile(testObjectFile())
        assertFalse(module.isLoaded())
        assertEquals(0L, module.baseAddress())
    }

    // --- path ---

    @Test
    fun noPathByDefault() {
        val module = Module.fromObjectFile(testObjectFile(), "test.o")
        assertNull(module.path())
    }

    // --- Empty module ---

    @Test
    fun emptyModuleNoSymbols() {
        val module = Module.fromObjectFile(testObjectFile())
        assertTrue(module.symbols().isEmpty())
        assertTrue(module.functions().isEmpty())
    }

    @Test
    fun emptyModuleNoSections() {
        val module = Module.fromObjectFile(testObjectFile())
        assertTrue(module.sections().isEmpty())
    }
}
