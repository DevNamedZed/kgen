package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

class ModuleTest {

    private fun testObjectFile(
        format: ObjectFormat = ObjectFormat.ELF,
        arch: Architecture = Architecture.X86_64_LINUX,
        symbols: List<org.kgen.binary.Symbol> = emptyList(),
        sections: List<Section> = emptyList(),
        imports: List<ImportEntry> = emptyList(),
        exports: List<ExportEntry> = emptyList(),
        flags: Set<ObjectFlag> = emptySet(),
        entryPoint: Long? = null,
    ): ObjectFile = ObjectFile(
        format = format,
        arch = arch,
        symbols = symbols,
        sections = sections,
        imports = imports,
        exports = exports,
        relocations = emptyList(),
        metadata = ObjectMetadata(entryPoint = entryPoint, flags = flags),
    )

    @Test
    fun fromObjectFile() {
        val obj = testObjectFile()
        val module = Module.fromObjectFile(obj, "test.o")
        assertEquals("test.o", module.name())
        assertNull(module.path())
        assertEquals(ObjectFormat.ELF, module.format())
    }

    @Test
    fun archAndFormat() {
        val module = Module.fromObjectFile(
            testObjectFile(format = ObjectFormat.PE_COFF, arch = Architecture.X86_64_WINDOWS),
            "test.dll"
        )
        assertEquals(ObjectFormat.PE_COFF, module.format())
        assertEquals(ArchType.X86_64, module.arch().arch)
    }

    @Test
    fun fileBasedNotLoaded() {
        val module = Module.fromObjectFile(testObjectFile())
        assertFalse(module.isLoaded())
        assertEquals(0L, module.baseAddress())
    }

    @Test
    fun classificationFlags() {
        val exe = Module.fromObjectFile(testObjectFile(flags = setOf(ObjectFlag.EXECUTABLE)))
        assertTrue(exe.isExecutable())
        assertFalse(exe.isSharedLibrary())

        val so = Module.fromObjectFile(testObjectFile(flags = setOf(ObjectFlag.SHARED_LIBRARY)))
        assertTrue(so.isSharedLibrary())
        assertFalse(so.isExecutable())

        val reloc = Module.fromObjectFile(testObjectFile(flags = setOf(ObjectFlag.RELOCATABLE)))
        assertTrue(reloc.isRelocatable())
    }

    @Test
    fun runtimeDetectionElf() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.ELF))
        assertFalse(module.hasClr())
        assertFalse(module.hasJvm())
        assertFalse(module.hasWasm())
        assertTrue(module.hasNativeCode())
        assertFalse(module.isMixedMode())
    }

    @Test
    fun runtimeDetectionClr() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.MSIL_ASSEMBLY))
        assertTrue(module.hasClr())
        assertFalse(module.hasNativeCode())
    }

    @Test
    fun runtimeDetectionMixedMode() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.MSIL_MIXED))
        assertTrue(module.hasClr())
        assertTrue(module.isMixedMode())
    }

    @Test
    fun runtimeDetectionJvm() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.JVM_CLASS))
        assertTrue(module.hasJvm())
        assertFalse(module.hasNativeCode())
    }

    @Test
    fun runtimeDetectionWasm() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.WASM_MODULE))
        assertTrue(module.hasWasm())
        assertFalse(module.hasNativeCode())
    }

    @Test
    fun sections() {
        val sec = Section(name = ".text", kind = SectionKind.TEXT, data = ByteArray(100))
        val module = Module.fromObjectFile(testObjectFile(sections = listOf(sec)))
        assertEquals(1, module.sections().size)
        assertNotNull(module.section(".text"))
        assertNotNull(module.section(SectionKind.TEXT))
        assertNotNull(module.textSection())
        assertNull(module.section(".data"))
    }

    @Test
    fun symbols() {
        val syms = listOf(
            org.kgen.binary.Symbol("main", 0x1000, 64, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("data", 0x2000, 8, ".data", SymbolBinding.GLOBAL, SymbolKind.DATA),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        assertEquals(2, module.symbols().size)
        assertNotNull(module.symbol("main"))
        assertNotNull(module.symbol("data"))
        assertNull(module.symbol("missing"))
    }

    @Test
    fun functions() {
        val syms = listOf(
            org.kgen.binary.Symbol("main", 0x1000, 64, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("data", 0x2000, 8, ".data", SymbolBinding.GLOBAL, SymbolKind.DATA),
            org.kgen.binary.Symbol("helper", 0x1100, 32, ".text", SymbolBinding.LOCAL, SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        assertEquals(2, module.functions().size)
        assertNotNull(module.function("main"))
        assertNotNull(module.function("helper"))
        assertNull(module.function("data"))
    }

    @Test
    fun functionFromSymbol() {
        val syms = listOf(
            org.kgen.binary.Symbol("strlen", 0x1000, 64, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val sym = module.symbol("strlen")!!
        val func = module.function(sym)
        assertNotNull(func)
        assertEquals("strlen", func!!.name())
    }

    @Test
    fun importsAndExports() {
        val imports = listOf(
            ImportEntry("puts", "libc.so.6"),
        )
        val exports = listOf(
            ExportEntry("my_func"),
        )
        val module = Module.fromObjectFile(testObjectFile(imports = imports, exports = exports))
        assertEquals(1, module.imports().size)
        assertEquals("puts", module.imports()[0].symbolName)
        assertEquals(1, module.exports().size)
        assertEquals("my_func", module.exports()[0].symbolName)
    }

    @Test
    fun dependencies() {
        val dyn = DynamicLinkInfo(neededLibraries = listOf("libc.so.6", "libz.so.1"))
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture.X86_64_LINUX,
            symbols = emptyList(),
            sections = emptyList(),
            relocations = emptyList(),
            dynamicInfo = dyn,
        )
        val module = Module.fromObjectFile(obj)
        val deps = module.dependencies()
        assertEquals(2, deps.size)
        assertEquals("libc.so.6", deps[0].name())
        assertEquals("libz.so.1", deps[1].name())
    }

    @Test
    fun noDependencies() {
        val module = Module.fromObjectFile(testObjectFile())
        assertTrue(module.dependencies().isEmpty())
    }

    @Test
    fun entryPoint() {
        val syms = listOf(
            org.kgen.binary.Symbol("_start", 0x1000, 32, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms, entryPoint = 0x1000))
        val entry = module.entryPoint()
        assertNotNull(entry)
        assertEquals("_start", entry!!.name())
    }

    @Test
    fun noEntryPoint() {
        val module = Module.fromObjectFile(testObjectFile())
        assertNull(module.entryPoint())
    }

    @Test
    fun objectFileAccess() {
        val obj = testObjectFile()
        val module = Module.fromObjectFile(obj)
        assertSame(obj, module.objectFile())
    }

    @Test
    fun toStringIncludesInfo() {
        val module = Module.fromObjectFile(testObjectFile(), "test.o")
        val str = module.toString()
        assertTrue(str.contains("test.o"))
        assertTrue(str.contains("ELF"))
    }

    @Test
    fun symbolModuleBackReference() {
        val syms = listOf(
            org.kgen.binary.Symbol("main", 0x1000, 64),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val sym = module.symbol("main")!!
        assertSame(module, sym.module())
    }

    @Test
    fun functionModuleBackReference() {
        val syms = listOf(
            org.kgen.binary.Symbol("main", 0x1000, 64, kind = SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val func = module.function("main")!!
        assertSame(module, func.module())
    }
}
