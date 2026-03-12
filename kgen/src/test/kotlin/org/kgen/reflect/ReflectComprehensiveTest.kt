package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.kgen.binary.*
import org.kgen.binary.mangling.ItaniumDemangler
import org.kgen.binary.mangling.ManglingScheme
import org.kgen.binary.mangling.MsvcDemangler
import org.kgen.binary.mangling.RustDemangler
import org.kgen.binary.mangling.UniversalDemangler
import java.nio.file.Path

class ReflectComprehensiveTest {

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

    private fun loadClassFixture(): Module {
        val url = javaClass.getResource("/fixtures/java/Calculator.class")!!
        return Module.fromFile(Path.of(url.toURI()).toString())
    }

    private fun loadShapeFixture(): Module {
        val url = javaClass.getResource("/fixtures/java/Shape.class")!!
        return Module.fromFile(Path.of(url.toURI()).toString())
    }

    private fun loadCircleFixture(): Module {
        val url = javaClass.getResource("/fixtures/java/Circle.class")!!
        return Module.fromFile(Path.of(url.toURI()).toString())
    }

    private fun loadColorFixture(): Module {
        val url = javaClass.getResource("/fixtures/java/Color.class")!!
        return Module.fromFile(Path.of(url.toURI()).toString())
    }

    private fun loadClrFixture(): Module {
        val url = javaClass.getResource("/fixtures/clr/TestLib.dll")!!
        return Module.fromFile(Path.of(url.toURI()).toString())
    }

    // -- Module creation from ObjectFile with various formats --

    @Test
    fun `module from ELF object file`() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.ELF), "test.o")
        assertEquals("test.o", module.name())
        assertEquals(ObjectFormat.ELF, module.format())
        assertTrue(module.hasNativeCode())
        assertFalse(module.hasJvm())
        assertFalse(module.hasClr())
        assertFalse(module.hasWasm())
    }

    @Test
    fun `module from PE COFF object file`() {
        val module = Module.fromObjectFile(
            testObjectFile(format = ObjectFormat.PE_COFF, arch = Architecture.X86_64_WINDOWS),
            "test.obj"
        )
        assertEquals(ObjectFormat.PE_COFF, module.format())
        assertEquals(ArchType.X86_64, module.arch().arch)
        assertTrue(module.hasNativeCode())
    }

    @Test
    fun `module from Mach-O object file`() {
        val module = Module.fromObjectFile(
            testObjectFile(format = ObjectFormat.MACH_O, arch = Architecture.X86_64_MACOS),
            "test.dylib"
        )
        assertEquals(ObjectFormat.MACH_O, module.format())
        assertTrue(module.hasNativeCode())
    }

    @Test
    fun `module from WASM format`() {
        val module = Module.fromObjectFile(
            testObjectFile(format = ObjectFormat.WASM_MODULE, arch = Architecture.WASM32),
            "test.wasm"
        )
        assertEquals(ObjectFormat.WASM_MODULE, module.format())
        assertTrue(module.hasWasm())
        assertFalse(module.hasNativeCode())
        assertFalse(module.hasJvm())
    }

    @Test
    fun `module from JVM class format`() {
        val module = Module.fromObjectFile(
            testObjectFile(format = ObjectFormat.JVM_CLASS, arch = Architecture(ArchType.JVM)),
            "Test.class"
        )
        assertEquals(ObjectFormat.JVM_CLASS, module.format())
        assertTrue(module.hasJvm())
        assertFalse(module.hasNativeCode())
        assertFalse(module.hasClr())
    }

    @Test
    fun `module from MSIL assembly format`() {
        val module = Module.fromObjectFile(
            testObjectFile(format = ObjectFormat.MSIL_ASSEMBLY),
            "Test.dll"
        )
        assertTrue(module.hasClr())
        assertFalse(module.hasNativeCode())
        assertFalse(module.isMixedMode())
    }

    @Test
    fun `module from MSIL mixed mode`() {
        val module = Module.fromObjectFile(
            testObjectFile(format = ObjectFormat.MSIL_MIXED),
            "Mixed.dll"
        )
        assertTrue(module.hasClr())
        assertTrue(module.isMixedMode())
    }

    @Test
    fun `module path is null for object file creation`() {
        val module = Module.fromObjectFile(testObjectFile())
        assertNull(module.path())
    }

    @Test
    fun `module is not loaded for file-based creation`() {
        val module = Module.fromObjectFile(testObjectFile())
        assertFalse(module.isLoaded())
        assertEquals(0L, module.baseAddress())
    }

    @Test
    fun `module objectFile returns same instance`() {
        val obj = testObjectFile()
        val module = Module.fromObjectFile(obj)
        assertSame(obj, module.objectFile())
    }

    // -- Module from real JVM class files --

    @Test
    fun `module from JVM class file fixture`() {
        val module = loadClassFixture()
        assertEquals(ObjectFormat.JVM_CLASS, module.format())
        assertTrue(module.hasJvm())
        assertNotNull(module.classFile())
    }

    @Test
    fun `module from JVM class has symbols`() {
        val module = loadClassFixture()
        assertFalse(module.symbols().isEmpty())
    }

    @Test
    fun `module from JVM class has functions`() {
        val module = loadClassFixture()
        assertFalse(module.functions().isEmpty())
    }

    @Test
    fun `module bytes are non-empty for file-loaded module`() {
        val module = loadClassFixture()
        assertTrue(module.bytes().isNotEmpty())
    }

    @Test
    fun `module bytes start with CAFEBABE for JVM class`() {
        val module = loadClassFixture()
        val bytes = module.bytes()
        assertEquals(0xCA.toByte(), bytes[0])
        assertEquals(0xFE.toByte(), bytes[1])
        assertEquals(0xBA.toByte(), bytes[2])
        assertEquals(0xBE.toByte(), bytes[3])
    }

    // -- Module from CLR fixture --

    @Test
    fun `module from CLR DLL fixture`() {
        val module = loadClrFixture()
        assertTrue(module.hasClr() || module.format() == ObjectFormat.PE_COFF)
    }

    // -- Classification flags --

    @Test
    fun `executable flag classification`() {
        val exe = Module.fromObjectFile(testObjectFile(flags = setOf(ObjectFlag.EXECUTABLE)))
        assertTrue(exe.isExecutable())
        assertFalse(exe.isSharedLibrary())
        assertFalse(exe.isRelocatable())
    }

    @Test
    fun `shared library flag classification`() {
        val so = Module.fromObjectFile(testObjectFile(flags = setOf(ObjectFlag.SHARED_LIBRARY)))
        assertTrue(so.isSharedLibrary())
        assertFalse(so.isExecutable())
    }

    @Test
    fun `relocatable flag classification`() {
        val reloc = Module.fromObjectFile(testObjectFile(flags = setOf(ObjectFlag.RELOCATABLE)))
        assertTrue(reloc.isRelocatable())
    }

    // -- Sections --

    @Test
    fun `module with text section`() {
        val sec = Section(".text", SectionKind.TEXT, ByteArray(100))
        val module = Module.fromObjectFile(testObjectFile(sections = listOf(sec)))
        assertEquals(1, module.sections().size)
        assertNotNull(module.section(".text"))
        assertNotNull(module.section(SectionKind.TEXT))
        assertNotNull(module.textSection())
    }

    @Test
    fun `module section by name returns null for missing`() {
        val module = Module.fromObjectFile(testObjectFile())
        assertNull(module.section(".text"))
        assertNull(module.section(".data"))
    }

    @Test
    fun `module with multiple sections`() {
        val text = Section(".text", SectionKind.TEXT, ByteArray(100))
        val data = Section(".data", SectionKind.DATA, ByteArray(50))
        val bss = Section(".bss", SectionKind.BSS, ByteArray(0))
        val module = Module.fromObjectFile(testObjectFile(sections = listOf(text, data, bss)))
        assertEquals(3, module.sections().size)
        assertNotNull(module.section(".data"))
        assertNotNull(module.section(SectionKind.BSS))
    }

    // -- Symbol enumeration --

    @Test
    fun `symbols from object file`() {
        val syms = listOf(
            org.kgen.binary.Symbol("main", 0x1000, 64, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("data_var", 0x2000, 8, ".data", SymbolBinding.GLOBAL, SymbolKind.DATA),
            org.kgen.binary.Symbol("helper", 0x1100, 32, ".text", SymbolBinding.LOCAL, SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        assertEquals(3, module.symbols().size)
    }

    @Test
    fun `symbol lookup by name`() {
        val syms = listOf(
            org.kgen.binary.Symbol("main", 0x1000, 64, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        assertNotNull(module.symbol("main"))
        assertNull(module.symbol("nonexistent"))
    }

    @Test
    fun `symbol module back-reference`() {
        val syms = listOf(org.kgen.binary.Symbol("test", 0, 0))
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val sym = module.symbol("test")!!
        assertSame(module, sym.module())
    }

    @Test
    fun `symbol filtering by kind - function`() {
        val syms = listOf(
            org.kgen.binary.Symbol("func1", kind = SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("data1", kind = SymbolKind.DATA),
            org.kgen.binary.Symbol("func2", kind = SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val funcs = module.symbols().filter { it.isFunction() }
        assertEquals(2, funcs.size)
    }

    @Test
    fun `symbol filtering by kind - data`() {
        val syms = listOf(
            org.kgen.binary.Symbol("func1", kind = SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("data1", kind = SymbolKind.DATA),
            org.kgen.binary.Symbol("data2", kind = SymbolKind.DATA),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val dataSyms = module.symbols().filter { it.isData() }
        assertEquals(2, dataSyms.size)
    }

    @Test
    fun `symbol filtering by visibility - exported`() {
        val syms = listOf(
            org.kgen.binary.Symbol("pub", flags = setOf(SymbolFlag.EXPORTED)),
            org.kgen.binary.Symbol("priv", binding = SymbolBinding.LOCAL),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val exported = module.symbols().filter { it.isExported() }
        assertEquals(1, exported.size)
        assertEquals("pub", exported[0].name())
    }

    @Test
    fun `symbol filtering by binding - weak`() {
        val syms = listOf(
            org.kgen.binary.Symbol("strong", binding = SymbolBinding.GLOBAL),
            org.kgen.binary.Symbol("weak_sym", binding = SymbolBinding.WEAK),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val weak = module.symbols().filter { it.isWeak() }
        assertEquals(1, weak.size)
        assertEquals("weak_sym", weak[0].name())
    }

    @Test
    fun `symbol filtering by binding - local`() {
        val syms = listOf(
            org.kgen.binary.Symbol("global_sym", binding = SymbolBinding.GLOBAL),
            org.kgen.binary.Symbol("local_sym", binding = SymbolBinding.LOCAL),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val local = module.symbols().filter { it.isLocal() }
        assertEquals(1, local.size)
    }

    @Test
    fun `symbol is global`() {
        val raw = org.kgen.binary.Symbol("test", binding = SymbolBinding.GLOBAL)
        val sym = Symbol(raw, null)
        assertTrue(sym.isGlobal())
        assertFalse(sym.isLocal())
        assertFalse(sym.isWeak())
    }

    @Test
    fun `symbol kind accessors`() {
        val raw = org.kgen.binary.Symbol("test", kind = SymbolKind.TLS)
        val sym = Symbol(raw, null)
        assertTrue(sym.isTls())
        assertFalse(sym.isFunction())
        assertFalse(sym.isData())
    }

    @Test
    fun `symbol absolute and common kinds`() {
        val abs = Symbol(org.kgen.binary.Symbol("abs", kind = SymbolKind.ABSOLUTE), null)
        assertTrue(abs.isAbsolute())

        val common = Symbol(org.kgen.binary.Symbol("common", kind = SymbolKind.COMMON), null)
        assertTrue(common.isCommon())
    }

    @Test
    fun `symbol undefined by kind`() {
        val sym = Symbol(org.kgen.binary.Symbol("ext", kind = SymbolKind.UNDEFINED), null)
        assertTrue(sym.isUndefined())
    }

    @Test
    fun `symbol undefined by flag`() {
        val sym = Symbol(org.kgen.binary.Symbol("ext", flags = setOf(SymbolFlag.UNDEFINED)), null)
        assertTrue(sym.isUndefined())
    }

    @Test
    fun `symbol exported via DLL_EXPORT flag`() {
        val sym = Symbol(org.kgen.binary.Symbol("exp", flags = setOf(SymbolFlag.DLL_EXPORT)), null)
        assertTrue(sym.isExported())
    }

    @Test
    fun `symbol imported via DLL_IMPORT flag`() {
        val sym = Symbol(org.kgen.binary.Symbol("imp", flags = setOf(SymbolFlag.DLL_IMPORT)), null)
        assertTrue(sym.isImported())
    }

    @Test
    fun `symbol exported via WASM_EXPORT flag`() {
        val sym = Symbol(org.kgen.binary.Symbol("exp", flags = setOf(SymbolFlag.WASM_EXPORT)), null)
        assertTrue(sym.isExported())
    }

    @Test
    fun `symbol imported via WASM_IMPORT flag`() {
        val sym = Symbol(org.kgen.binary.Symbol("imp", flags = setOf(SymbolFlag.WASM_IMPORT)), null)
        assertTrue(sym.isImported())
    }

    @Test
    fun `symbol exported by global binding and default visibility`() {
        val sym = Symbol(org.kgen.binary.Symbol("g", binding = SymbolBinding.GLOBAL, visibility = SymbolVisibility.DEFAULT), null)
        assertTrue(sym.isExported())
    }

    @Test
    fun `symbol JVM access flags`() {
        val raw = org.kgen.binary.Symbol("m", flags = setOf(
            SymbolFlag.ACC_PUBLIC, SymbolFlag.ACC_STATIC, SymbolFlag.ACC_FINAL, SymbolFlag.DEPRECATED
        ))
        val sym = Symbol(raw, null)
        assertTrue(sym.isPublic())
        assertTrue(sym.isStatic())
        assertTrue(sym.isFinal())
        assertTrue(sym.isDeprecated())
        assertFalse(sym.isPrivate())
        assertFalse(sym.isProtected())
        assertFalse(sym.isAbstract())
        assertFalse(sym.isNative())
        assertFalse(sym.isSynthetic())
    }

    @Test
    fun `symbol offset and size`() {
        val sym = Symbol(org.kgen.binary.Symbol("test", 0x4000, 128), null)
        assertEquals(0x4000L, sym.offset())
        assertEquals(128L, sym.size())
    }

    @Test
    fun `symbol section name`() {
        val sym = Symbol(org.kgen.binary.Symbol("test", section = ".data"), null)
        assertEquals(".data", sym.sectionName())
    }

    @Test
    fun `symbol null section`() {
        val sym = Symbol(org.kgen.binary.Symbol("test", section = null), null)
        assertNull(sym.sectionName())
    }

    @Test
    fun `symbol import details`() {
        val raw = org.kgen.binary.Symbol(
            name = "CreateFileW",
            importModule = "kernel32.dll",
            importName = "CreateFileW",
            ordinal = 100,
        )
        val sym = Symbol(raw, null)
        assertEquals("kernel32.dll", sym.importModule())
        assertEquals("CreateFileW", sym.importName())
        assertEquals(100, sym.ordinal())
    }

    @Test
    fun `symbol raw access`() {
        val raw = org.kgen.binary.Symbol("test", 0x100, 20)
        val sym = Symbol(raw, null)
        assertSame(raw, sym.raw())
    }

    @Test
    fun `symbol toString includes kind`() {
        val sym = Symbol(
            org.kgen.binary.Symbol("main", kind = SymbolKind.FUNCTION, flags = setOf(SymbolFlag.EXPORTED)),
            null
        )
        val str = sym.toString()
        assertTrue(str.contains("main"))
        assertTrue(str.contains("function"))
        assertTrue(str.contains("exported"))
    }

    @Test
    fun `symbol equality`() {
        val raw = org.kgen.binary.Symbol("test", 0x1000, 10)
        val a = Symbol(raw, null)
        val b = Symbol(raw, null)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // -- Functions --

    @Test
    fun `functions filters only function symbols`() {
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
    fun `function from symbol navigation`() {
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
    fun `function name and qualified name`() {
        val raw = org.kgen.binary.Symbol("std::vector::push_back", kind = SymbolKind.FUNCTION)
        val func = Function(Symbol(raw, null))
        assertEquals("std::vector::push_back", func.name())
        assertEquals("push_back", func.qualifiedName().name())
    }

    @Test
    fun `function offset and size`() {
        val raw = org.kgen.binary.Symbol("test", 0x2000, 256, kind = SymbolKind.FUNCTION)
        val func = Function(Symbol(raw, null))
        assertEquals(0x2000L, func.offset())
        assertEquals(256L, func.size())
    }

    @Test
    fun `function without signature`() {
        val raw = org.kgen.binary.Symbol("test", kind = SymbolKind.FUNCTION)
        val func = Function(Symbol(raw, null))
        assertFalse(func.hasSignature())
        assertNull(func.signature())
        assertNull(func.returnType())
        assertTrue(func.parameterTypes().isEmpty())
        assertTrue(func.parameters().isEmpty())
    }

    @Test
    fun `function with signature`() {
        val sig = Signature.returning(TypeRef.I32)
            .param("x", TypeRef.I32)
            .param("y", TypeRef.I32)
            .build()
        val raw = org.kgen.binary.Symbol("add", kind = SymbolKind.FUNCTION)
        val func = Function(Symbol(raw, null), sig)
        assertTrue(func.hasSignature())
        assertEquals(TypeRef.I32, func.returnType())
        assertEquals(2, func.parameterTypes().size)
        assertEquals("x", func.parameters()[0].name)
    }

    @Test
    fun `function withSignature creates new instance`() {
        val raw = org.kgen.binary.Symbol("test", kind = SymbolKind.FUNCTION)
        val original = Function(Symbol(raw, null))
        val sig = Signature.LONG_TO_LONG
        val withSig = original.withSignature(sig)
        assertFalse(original.hasSignature())
        assertTrue(withSig.hasSignature())
        assertEquals("test", withSig.name())
    }

    @Test
    fun `function is native for FUNCTION kind`() {
        val raw = org.kgen.binary.Symbol("test", kind = SymbolKind.FUNCTION)
        val func = Function(Symbol(raw, null))
        assertTrue(func.isNative())
        assertFalse(func.isBytecode())
    }

    @Test
    fun `function is bytecode for METHOD kind`() {
        val raw = org.kgen.binary.Symbol("test", kind = SymbolKind.METHOD)
        val func = Function(Symbol(raw, null))
        assertTrue(func.isBytecode())
    }

    @Test
    fun `function symbol navigation`() {
        val raw = org.kgen.binary.Symbol("main", kind = SymbolKind.FUNCTION)
        val sym = Symbol(raw, null)
        val func = Function(sym)
        assertSame(sym, func.symbol())
        assertTrue(func.symbol().isFunction())
    }

    @Test
    fun `function module back-reference`() {
        val syms = listOf(
            org.kgen.binary.Symbol("main", 0x1000, 64, kind = SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        val func = module.function("main")!!
        assertSame(module, func.module())
    }

    @Test
    fun `function toString without signature`() {
        val raw = org.kgen.binary.Symbol("strlen", kind = SymbolKind.FUNCTION)
        val func = Function(Symbol(raw, null))
        assertEquals("strlen", func.toString())
    }

    @Test
    fun `function toString with signature`() {
        val sig = Signature.returning(TypeRef.I64)
            .param("s", TypeRef.POINTER)
            .build()
        val raw = org.kgen.binary.Symbol("strlen", kind = SymbolKind.FUNCTION)
        val func = Function(Symbol(raw, null), sig)
        assertEquals("strlen: (s: ptr) -> i64", func.toString())
    }

    @Test
    fun `function equality based on symbol`() {
        val raw = org.kgen.binary.Symbol("test", 0x1000, 10)
        val sym = Symbol(raw, null)
        val a = Function(sym, Signature.VOID)
        val b = Function(sym, Signature.LONG_TO_LONG)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // -- Symbol to Function navigation --

    @Test
    fun `symbol function navigation creates function`() {
        val raw = org.kgen.binary.Symbol("strlen", kind = SymbolKind.FUNCTION)
        val sym = Symbol(raw, null)
        val func = sym.function()
        assertNotNull(func)
        assertEquals("strlen", func!!.name())
        assertSame(sym, func.symbol())
    }

    @Test
    fun `symbol function navigation returns null for data`() {
        val raw = org.kgen.binary.Symbol("variable", kind = SymbolKind.DATA)
        val sym = Symbol(raw, null)
        assertNull(sym.function())
    }

    @Test
    fun `symbol function navigation works for METHOD kind`() {
        val raw = org.kgen.binary.Symbol("method", kind = SymbolKind.METHOD)
        val sym = Symbol(raw, null)
        assertNotNull(sym.function())
    }

    // -- Imports and Exports --

    @Test
    fun `module imports`() {
        val imports = listOf(
            ImportEntry("puts", "libc.so.6"),
            ImportEntry("printf", "libc.so.6"),
        )
        val module = Module.fromObjectFile(testObjectFile(imports = imports))
        assertEquals(2, module.imports().size)
        assertEquals("puts", module.imports()[0].symbolName)
        assertEquals("libc.so.6", module.imports()[0].moduleName)
    }

    @Test
    fun `module exports`() {
        val exports = listOf(
            ExportEntry("my_func"),
            ExportEntry("my_data"),
        )
        val module = Module.fromObjectFile(testObjectFile(exports = exports))
        assertEquals(2, module.exports().size)
        assertEquals("my_func", module.exports()[0].symbolName)
    }

    @Test
    fun `module with no imports or exports`() {
        val module = Module.fromObjectFile(testObjectFile())
        assertTrue(module.imports().isEmpty())
        assertTrue(module.exports().isEmpty())
    }

    // -- Dependencies --

    @Test
    fun `module dependencies from dynamic info`() {
        val dyn = DynamicLinkInfo(neededLibraries = listOf("libc.so.6", "libm.so.6", "libpthread.so.0"))
        val module = Module.fromObjectFile(testObjectFile(dynamicInfo = dyn))
        val deps = module.dependencies()
        assertEquals(3, deps.size)
        assertEquals("libc.so.6", deps[0].name())
        assertEquals("libm.so.6", deps[1].name())
        assertEquals("libpthread.so.0", deps[2].name())
    }

    @Test
    fun `module no dependencies`() {
        val module = Module.fromObjectFile(testObjectFile())
        assertTrue(module.dependencies().isEmpty())
    }

    @Test
    fun `dependency equality`() {
        val a = Dependency("libc.so.6")
        val b = Dependency("libc.so.6")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `dependency with version`() {
        val dep = Dependency("mscorlib", version = "4.0.0.0")
        assertEquals("mscorlib", dep.name())
        assertEquals("4.0.0.0", dep.version())
        assertTrue(dep.toString().contains("4.0.0.0"))
    }

    @Test
    fun `dependency with path`() {
        val dep = Dependency("libc.so.6", resolvedPath = "/usr/lib/libc.so.6")
        assertEquals("/usr/lib/libc.so.6", dep.path())
    }

    // -- Entry point --

    @Test
    fun `module entry point`() {
        val syms = listOf(
            org.kgen.binary.Symbol("_start", 0x1000, 32, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms, entryPoint = 0x1000))
        val entry = module.entryPoint()
        assertNotNull(entry)
        assertEquals("_start", entry!!.name())
    }

    @Test
    fun `module no entry point`() {
        val module = Module.fromObjectFile(testObjectFile())
        assertNull(module.entryPoint())
    }

    // -- Module toString --

    @Test
    fun `module toString contains name and format`() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.ELF), "binary.o")
        val str = module.toString()
        assertTrue(str.contains("binary.o"))
        assertTrue(str.contains("ELF"))
    }

    // -- Signature construction --

    @Test
    fun `signature VOID constant`() {
        val sig = Signature.VOID
        assertEquals(TypeRef.VOID, sig.returnType())
        assertEquals(0, sig.parameterCount())
        assertTrue(sig.parameters().isEmpty())
    }

    @Test
    fun `signature LONG_TO_LONG constant`() {
        val sig = Signature.LONG_TO_LONG
        assertEquals(TypeRef.I64, sig.returnType())
        assertEquals(1, sig.parameterCount())
        assertEquals(TypeRef.I64, sig.parameterTypes()[0])
    }

    @Test
    fun `signature LONG_LONG_TO_LONG constant`() {
        val sig = Signature.LONG_LONG_TO_LONG
        assertEquals(TypeRef.I64, sig.returnType())
        assertEquals(2, sig.parameterCount())
    }

    @Test
    fun `signature builder with named params`() {
        val sig = Signature.returning(TypeRef.F64)
            .param("x", TypeRef.F64)
            .param("y", TypeRef.F64)
            .build()
        assertEquals(TypeRef.F64, sig.returnType())
        assertEquals(2, sig.parameterCount())
        assertEquals("x", sig.parameters()[0].name)
        assertEquals("y", sig.parameters()[1].name)
    }

    @Test
    fun `signature builder with unnamed params`() {
        val sig = Signature.returning(TypeRef.I64)
            .param(TypeRef.POINTER)
            .param(TypeRef.I32)
            .build()
        assertNull(sig.parameters()[0].name)
        assertNull(sig.parameters()[1].name)
    }

    @Test
    fun `signature builder mixed named and unnamed`() {
        val sig = Signature.returning(TypeRef.I32)
            .param("buf", TypeRef.POINTER)
            .param(TypeRef.I64)
            .build()
        assertEquals("buf", sig.parameters()[0].name)
        assertNull(sig.parameters()[1].name)
    }

    @Test
    fun `signature returningVoid`() {
        val sig = Signature.returningVoid()
            .param(TypeRef.POINTER)
            .build()
        assertEquals(TypeRef.VOID, sig.returnType())
        assertEquals(1, sig.parameterCount())
    }

    @Test
    fun `signature factory of`() {
        val sig = Signature.of(TypeRef.I32, TypeRef.POINTER, TypeRef.I64)
        assertEquals(TypeRef.I32, sig.returnType())
        assertEquals(2, sig.parameterCount())
    }

    @Test
    fun `signature factory ofVoid`() {
        val sig = Signature.ofVoid(TypeRef.POINTER, TypeRef.I64)
        assertEquals(TypeRef.VOID, sig.returnType())
        assertEquals(2, sig.parameterCount())
    }

    @Test
    fun `signature equality`() {
        val a = Signature.of(TypeRef.I64, TypeRef.I64)
        val b = Signature.of(TypeRef.I64, TypeRef.I64)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `signature inequality different return type`() {
        val a = Signature.of(TypeRef.I64, TypeRef.I64)
        val b = Signature.of(TypeRef.I32, TypeRef.I64)
        assertNotEquals(a, b)
    }

    @Test
    fun `signature toString with params`() {
        val sig = Signature.returning(TypeRef.I64)
            .param("a", TypeRef.I64)
            .param("b", TypeRef.I64)
            .build()
        assertEquals("(a: i64, b: i64) -> i64", sig.toString())
    }

    @Test
    fun `signature toString no params`() {
        assertEquals("() -> void", Signature.VOID.toString())
    }

    @Test
    fun `signature toString unnamed params`() {
        val sig = Signature.of(TypeRef.I64, TypeRef.POINTER, TypeRef.I32)
        assertEquals("(ptr, i32) -> i64", sig.toString())
    }

    @Test
    fun `signatureParam toString named`() {
        assertEquals("buf: ptr", SignatureParam("buf", TypeRef.POINTER).toString())
    }

    @Test
    fun `signatureParam toString unnamed`() {
        assertEquals("i64", SignatureParam(null, TypeRef.I64).toString())
    }

    // -- TypeRef construction and comparison --

    @Test
    fun `TypeRef primitive constants`() {
        assertTrue(TypeRef.I32.isPrimitive())
        assertTrue(TypeRef.I64.isPrimitive())
        assertTrue(TypeRef.F32.isPrimitive())
        assertTrue(TypeRef.F64.isPrimitive())
        assertTrue(TypeRef.BOOL.isPrimitive())
        assertTrue(TypeRef.POINTER.isPrimitive())
        assertTrue(TypeRef.I8.isPrimitive())
        assertTrue(TypeRef.I16.isPrimitive())
        assertTrue(TypeRef.U8.isPrimitive())
        assertTrue(TypeRef.U16.isPrimitive())
        assertTrue(TypeRef.U32.isPrimitive())
        assertTrue(TypeRef.U64.isPrimitive())
    }

    @Test
    fun `TypeRef VOID`() {
        assertTrue(TypeRef.VOID.isVoid())
        assertEquals("void", TypeRef.VOID.fullName())
    }

    @Test
    fun `TypeRef LONG alias for I64`() {
        assertSame(TypeRef.I64, TypeRef.LONG)
    }

    @Test
    fun `TypeRef of named type`() {
        val ref = TypeRef.of("System.String")
        assertEquals("System.String", ref.fullName())
        assertEquals("String", ref.name())
        assertEquals("System", ref.namespace())
        assertFalse(ref.isPrimitive())
        assertFalse(ref.isVoid())
    }

    @Test
    fun `TypeRef of QualifiedName`() {
        val qname = QualifiedName.parse("java.lang.String")
        val ref = TypeRef.of(qname)
        assertEquals("java.lang.String", ref.fullName())
    }

    @Test
    fun `TypeRef arrayOf`() {
        val arr = TypeRef.arrayOf(TypeRef.I32)
        assertTrue(arr.isArray())
        assertFalse(arr.isPointer())
        assertEquals(TypeRef.I32, arr.elementType())
        assertEquals("i32[]", arr.fullName())
    }

    @Test
    fun `TypeRef pointerTo`() {
        val ptr = TypeRef.pointerTo(TypeRef.I32)
        assertTrue(ptr.isPointer())
        assertFalse(ptr.isArray())
        assertEquals(TypeRef.I32, ptr.elementType())
        assertEquals("i32*", ptr.fullName())
    }

    @Test
    fun `TypeRef byRef`() {
        val ref = TypeRef.byRef(TypeRef.I32)
        assertTrue(ref.isByRef())
        assertEquals(TypeRef.I32, ref.elementType())
        assertEquals("i32&", ref.fullName())
    }

    @Test
    fun `TypeRef genericParam`() {
        val param = TypeRef.genericParam("T")
        assertTrue(param.isGenericParameter())
        assertEquals("T", param.name())
    }

    @Test
    fun `TypeRef equality`() {
        val a = TypeRef.of("System.String")
        val b = TypeRef.of("System.String")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `TypeRef inequality`() {
        val a = TypeRef.of("System.String")
        val b = TypeRef.of("System.Int32")
        assertNotEquals(a, b)
    }

    @Test
    fun `TypeRef array equality`() {
        val a = TypeRef.arrayOf(TypeRef.I32)
        val b = TypeRef.arrayOf(TypeRef.I32)
        assertEquals(a, b)
    }

    @Test
    fun `TypeRef nested array`() {
        val arr = TypeRef.arrayOf(TypeRef.arrayOf(TypeRef.F64))
        assertTrue(arr.isArray())
        assertTrue(arr.elementType()!!.isArray())
        assertEquals("f64[][]", arr.fullName())
    }

    @Test
    fun `TypeRef pointer to pointer`() {
        val ptrptr = TypeRef.pointerTo(TypeRef.pointerTo(TypeRef.I8))
        assertTrue(ptrptr.isPointer())
        assertTrue(ptrptr.elementType()!!.isPointer())
        assertEquals("i8**", ptrptr.fullName())
    }

    @Test
    fun `TypeRef toString is fullName`() {
        assertEquals("i32", TypeRef.I32.toString())
        assertEquals("System.String", TypeRef.of("System.String").toString())
    }

    // -- TypeInfo hierarchy --

    @Test
    fun `TypeInfo class classification`() {
        val cls = TypeInfo.builder("MyClass").kind(TypeKind.CLASS).build()
        assertTrue(cls.isClass())
        assertFalse(cls.isInterface())
        assertFalse(cls.isEnum())
        assertFalse(cls.isStruct())
    }

    @Test
    fun `TypeInfo interface classification`() {
        val iface = TypeInfo.builder("IDisposable").kind(TypeKind.INTERFACE).build()
        assertTrue(iface.isInterface())
        assertFalse(iface.isClass())
    }

    @Test
    fun `TypeInfo enum classification`() {
        val enum = TypeInfo.builder("Color").kind(TypeKind.ENUM).build()
        assertTrue(enum.isEnum())
    }

    @Test
    fun `TypeInfo struct classification`() {
        val struct = TypeInfo.builder("Point").kind(TypeKind.STRUCT).build()
        assertTrue(struct.isStruct())
    }

    @Test
    fun `TypeInfo delegate classification`() {
        val del = TypeInfo.builder("Action").kind(TypeKind.DELEGATE).build()
        assertTrue(del.isDelegate())
    }

    @Test
    fun `TypeInfo annotation classification`() {
        val ann = TypeInfo.builder("Override").kind(TypeKind.ANNOTATION).build()
        assertTrue(ann.isAnnotation())
    }

    @Test
    fun `TypeInfo name and namespace`() {
        val type = TypeInfo.builder("System.Collections.Generic.List")
            .kind(TypeKind.CLASS)
            .build()
        assertEquals("List", type.name())
        assertEquals("System.Collections.Generic", type.namespace())
        assertEquals("System.Collections.Generic.List", type.fullName())
    }

    @Test
    fun `TypeInfo flags`() {
        val type = TypeInfo.builder("Test")
            .addFlag(TypeFlag.PUBLIC)
            .addFlag(TypeFlag.ABSTRACT)
            .addFlag(TypeFlag.SEALED)
            .build()
        assertTrue(type.isPublic())
        assertTrue(type.isAbstract())
        assertTrue(type.isSealed())
        assertFalse(type.isInternal())
    }

    @Test
    fun `TypeInfo base type`() {
        val base = TypeInfo.builder("System.Object").build()
        val derived = TypeInfo.builder("System.String")
            .baseType(base)
            .build()
        assertEquals(base, derived.baseType())
    }

    @Test
    fun `TypeInfo interfaces`() {
        val iface1 = TypeInfo.builder("IComparable").kind(TypeKind.INTERFACE).build()
        val iface2 = TypeInfo.builder("ICloneable").kind(TypeKind.INTERFACE).build()
        val type = TypeInfo.builder("MyClass")
            .addInterface(iface1)
            .addInterface(iface2)
            .build()
        assertEquals(2, type.interfaces().size)
    }

    @Test
    fun `TypeInfo nested types`() {
        val inner = TypeInfo.builder("InnerClass").build()
        val outer = TypeInfo.builder("OuterClass")
            .addNestedType(inner)
            .build()
        assertEquals(1, outer.nestedTypes().size)
        assertEquals(inner, outer.nestedTypes()[0])
    }

    @Test
    fun `TypeInfo generic type`() {
        val type = TypeInfo.builder("System.Collections.Generic.List`1")
            .addGenericArg(TypeRef.genericParam("T"))
            .build()
        assertTrue(type.isGeneric())
        assertEquals(1, type.genericArguments().size)
    }

    @Test
    fun `TypeInfo methods`() {
        val method = MethodInfo("DoWork", returnType = TypeRef.VOID)
        val type = TypeInfo.builder("Worker")
            .addMethod(method)
            .build()
        assertEquals(1, type.methods().size)
        assertEquals(method, type.method("DoWork"))
        assertNull(type.method("Missing"))
    }

    @Test
    fun `TypeInfo fields`() {
        val field = FieldInfo("count", TypeRef.I32, flags = setOf(FieldFlag.PRIVATE))
        val type = TypeInfo.builder("Counter")
            .addField(field)
            .build()
        assertEquals(1, type.fields().size)
        assertEquals(field, type.field("count"))
    }

    @Test
    fun `TypeInfo properties`() {
        val getter = MethodInfo("get_Name", returnType = TypeRef.of("System.String"))
        val setter = MethodInfo("set_Name")
        val prop = PropertyInfo("Name", TypeRef.of("System.String"), getter = getter, setter = setter)
        val type = TypeInfo.builder("Person")
            .addProperty(prop)
            .build()
        assertEquals(1, type.properties().size)
        assertFalse(type.property("Name")!!.isReadOnly())
    }

    @Test
    fun `TypeInfo events`() {
        val add = MethodInfo("add_Changed")
        val remove = MethodInfo("remove_Changed")
        val event = EventInfo("Changed", TypeRef.of("System.EventHandler"), addMethod = add, removeMethod = remove)
        val type = TypeInfo.builder("Observable")
            .addEvent(event)
            .build()
        assertEquals(1, type.events().size)
        assertEquals(event, type.event("Changed"))
    }

    @Test
    fun `TypeInfo constructors`() {
        val ctor = MethodInfo(".ctor", flags = setOf(MethodFlag.PUBLIC, MethodFlag.CONSTRUCTOR))
        val type = TypeInfo.builder("MyClass")
            .addConstructor(ctor)
            .build()
        assertEquals(1, type.constructors().size)
        assertTrue(type.constructors()[0].isConstructor())
    }

    @Test
    fun `TypeInfo attributes`() {
        val attr = AttributeInfo("Obsolete", namedArgs = mapOf("Message" to "Deprecated"))
        val type = TypeInfo.builder("OldClass")
            .addAttribute(attr)
            .build()
        assertEquals(1, type.attributes().size)
        assertEquals(attr, type.attribute("Obsolete"))
        assertNull(type.attribute("Missing"))
    }

    @Test
    fun `TypeInfo layout`() {
        val type = TypeInfo.builder("Vector2")
            .kind(TypeKind.STRUCT)
            .size(16)
            .packingSize(8)
            .build()
        assertEquals(16, type.size())
        assertEquals(8, type.packingSize())
    }

    @Test
    fun `TypeInfo toTypeRef`() {
        val type = TypeInfo.builder("System.Int32").build()
        val ref = type.toTypeRef()
        assertEquals("System.Int32", ref.fullName())
    }

    @Test
    fun `TypeInfo equality`() {
        val a = TypeInfo.builder("System.String").build()
        val b = TypeInfo.builder("System.String").build()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `TypeInfo inequality`() {
        val a = TypeInfo.builder("System.String").build()
        val b = TypeInfo.builder("System.Int32").build()
        assertNotEquals(a, b)
    }

    @Test
    fun `TypeInfo toString is fullName`() {
        val type = TypeInfo.builder("System.String").build()
        assertEquals("System.String", type.toString())
    }

    // -- MethodInfo --

    @Test
    fun `MethodInfo basic properties`() {
        val method = MethodInfo("Add",
            returnType = TypeRef.I32,
            params = listOf(
                ParameterInfo("a", TypeRef.I32, 0),
                ParameterInfo("b", TypeRef.I32, 1),
            ),
            flags = setOf(MethodFlag.PUBLIC, MethodFlag.STATIC),
        )
        assertEquals("Add", method.name())
        assertEquals(TypeRef.I32, method.returnType())
        assertEquals(2, method.parameterCount())
        assertTrue(method.isPublic())
        assertTrue(method.isStatic())
        assertFalse(method.isVirtual())
    }

    @Test
    fun `MethodInfo signature generation`() {
        val method = MethodInfo("Calculate",
            returnType = TypeRef.F64,
            params = listOf(
                ParameterInfo("x", TypeRef.F64, 0),
                ParameterInfo("y", TypeRef.F64, 1),
            ),
        )
        val sig = method.signature()
        assertEquals(TypeRef.F64, sig.returnType())
        assertEquals(2, sig.parameterCount())
        assertEquals("x", sig.parameters()[0].name)
    }

    @Test
    fun `MethodInfo classification flags`() {
        val method = MethodInfo("Dispose",
            flags = setOf(MethodFlag.PUBLIC, MethodFlag.VIRTUAL, MethodFlag.FINAL),
        )
        assertTrue(method.isPublic())
        assertTrue(method.isVirtual())
        assertTrue(method.isFinal())
        assertFalse(method.isAbstract())
        assertFalse(method.isNative())
        assertFalse(method.isSynchronized())
    }

    @Test
    fun `MethodInfo constructor flag`() {
        val ctor = MethodInfo(".ctor", flags = setOf(MethodFlag.PUBLIC, MethodFlag.CONSTRUCTOR))
        assertTrue(ctor.isConstructor())
    }

    @Test
    fun `MethodInfo generic method`() {
        val method = MethodInfo("Cast", genericArgs = listOf(TypeRef.genericParam("T")))
        assertTrue(method.isGeneric())
        assertEquals(1, method.genericArguments().size)
    }

    @Test
    fun `MethodInfo with IL body`() {
        val il = byteArrayOf(0x02, 0x03, 0x58, 0x2A)
        val method = MethodInfo("Add", ilBytes = il)
        assertTrue(method.hasBody())
        assertNotNull(method.il())
        assertNull(method.bytecode())
    }

    @Test
    fun `MethodInfo with bytecode body`() {
        val bc = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())
        val method = MethodInfo("add", bytecodeBytes = bc)
        assertTrue(method.hasBody())
        assertNotNull(method.bytecode())
        assertNull(method.il())
    }

    @Test
    fun `MethodInfo without body`() {
        val method = MethodInfo("abstractMethod", flags = setOf(MethodFlag.ABSTRACT))
        assertFalse(method.hasBody())
    }

    @Test
    fun `MethodInfo toString format`() {
        val method = MethodInfo("Add",
            returnType = TypeRef.I32,
            params = listOf(
                ParameterInfo("a", TypeRef.I32, 0),
                ParameterInfo("b", TypeRef.I32, 1),
            ),
        )
        assertEquals("Add(a: i32, b: i32): i32", method.toString())
    }

    // -- FieldInfo --

    @Test
    fun `FieldInfo basic properties`() {
        val field = FieldInfo("x", TypeRef.F64, flags = setOf(FieldFlag.PUBLIC))
        assertEquals("x", field.name())
        assertEquals(TypeRef.F64, field.fieldType())
        assertTrue(field.isPublic())
        assertFalse(field.isStatic())
    }

    @Test
    fun `FieldInfo const field`() {
        val field = FieldInfo("MAX", TypeRef.I32,
            flags = setOf(FieldFlag.PUBLIC, FieldFlag.STATIC, FieldFlag.CONST),
            constantVal = Int.MAX_VALUE,
        )
        assertTrue(field.isConst())
        assertTrue(field.isStatic())
        assertEquals(Int.MAX_VALUE, field.constantValue())
    }

    @Test
    fun `FieldInfo readonly`() {
        val field = FieldInfo("Length", TypeRef.I32, flags = setOf(FieldFlag.READONLY))
        assertTrue(field.isReadOnly())
        assertFalse(field.isConst())
    }

    @Test
    fun `FieldInfo volatile and transient`() {
        val field = FieldInfo("flag", TypeRef.BOOL, flags = setOf(FieldFlag.VOLATILE, FieldFlag.TRANSIENT))
        assertTrue(field.isVolatile())
        assertTrue(field.isTransient())
    }

    @Test
    fun `FieldInfo offset`() {
        val field = FieldInfo("x", TypeRef.F32, byteOffset = 16)
        assertEquals(16, field.offset())
    }

    @Test
    fun `FieldInfo toString`() {
        val field = FieldInfo("count", TypeRef.I32)
        assertEquals("count: i32", field.toString())
    }

    // -- ParameterInfo --

    @Test
    fun `ParameterInfo basic`() {
        val p = ParameterInfo("value", TypeRef.I32, 0)
        assertEquals("value", p.name())
        assertEquals(TypeRef.I32, p.type())
        assertEquals(0, p.position())
        assertFalse(p.isOptional())
        assertFalse(p.isOut())
        assertFalse(p.isRef())
        assertFalse(p.isParams())
    }

    @Test
    fun `ParameterInfo optional with default`() {
        val p = ParameterInfo("count", TypeRef.I32, 0,
            flags = setOf(ParameterFlag.OPTIONAL),
            defaultVal = 42,
        )
        assertTrue(p.isOptional())
        assertEquals(42, p.defaultValue())
    }

    @Test
    fun `ParameterInfo out and ref`() {
        val outP = ParameterInfo("result", TypeRef.byRef(TypeRef.I32), 0, flags = setOf(ParameterFlag.OUT))
        assertTrue(outP.isOut())

        val refP = ParameterInfo("value", TypeRef.byRef(TypeRef.I32), 0, flags = setOf(ParameterFlag.REF))
        assertTrue(refP.isRef())
    }

    @Test
    fun `ParameterInfo params flag`() {
        val p = ParameterInfo("args", TypeRef.arrayOf(TypeRef.of("System.Object")), 0,
            flags = setOf(ParameterFlag.PARAMS))
        assertTrue(p.isParams())
    }

    @Test
    fun `ParameterInfo toString named`() {
        val p = ParameterInfo("x", TypeRef.F64, 0)
        assertEquals("x: f64", p.toString())
    }

    @Test
    fun `ParameterInfo toString unnamed`() {
        val p = ParameterInfo(null, TypeRef.I32, 0)
        assertEquals("i32", p.toString())
    }

    // -- PropertyInfo --

    @Test
    fun `PropertyInfo read-write`() {
        val getter = MethodInfo("get_Name", returnType = TypeRef.of("System.String"))
        val setter = MethodInfo("set_Name")
        val prop = PropertyInfo("Name", TypeRef.of("System.String"), getter = getter, setter = setter)
        assertEquals("Name", prop.name())
        assertNotNull(prop.getter())
        assertNotNull(prop.setter())
        assertFalse(prop.isReadOnly())
    }

    @Test
    fun `PropertyInfo read-only`() {
        val getter = MethodInfo("get_Count", returnType = TypeRef.I32)
        val prop = PropertyInfo("Count", TypeRef.I32, getter = getter)
        assertTrue(prop.isReadOnly())
        assertNull(prop.setter())
    }

    @Test
    fun `PropertyInfo toString`() {
        val prop = PropertyInfo("Length", TypeRef.I32)
        assertEquals("Length: i32", prop.toString())
    }

    // -- EventInfo --

    @Test
    fun `EventInfo basic`() {
        val add = MethodInfo("add_Click")
        val remove = MethodInfo("remove_Click")
        val event = EventInfo("Click", TypeRef.of("System.EventHandler"),
            addMethod = add, removeMethod = remove)
        assertEquals("Click", event.name())
        assertEquals("System.EventHandler", event.eventType().fullName())
        assertNotNull(event.addMethod())
        assertNotNull(event.removeMethod())
        assertNull(event.raiseMethod())
    }

    @Test
    fun `EventInfo with raise method`() {
        val raise = MethodInfo("raise_Foo")
        val event = EventInfo("Foo", TypeRef.of("Handler"), raiseMethod = raise)
        assertNotNull(event.raiseMethod())
    }

    @Test
    fun `EventInfo toString`() {
        val event = EventInfo("Click", TypeRef.of("System.EventHandler"))
        assertEquals("event Click: System.EventHandler", event.toString())
    }

    // -- AttributeInfo --

    @Test
    fun `AttributeInfo basic`() {
        val attr = AttributeInfo("Serializable")
        assertEquals("Serializable", attr.name())
        assertTrue(attr.constructorArguments().isEmpty())
        assertTrue(attr.arguments().isEmpty())
    }

    @Test
    fun `AttributeInfo with constructor args`() {
        val attr = AttributeInfo("DllImport", ctorArgs = listOf("kernel32.dll"))
        assertEquals(1, attr.constructorArguments().size)
        assertEquals("kernel32.dll", attr.constructorArguments()[0])
    }

    @Test
    fun `AttributeInfo with named args`() {
        val attr = AttributeInfo("Obsolete",
            namedArgs = mapOf("Message" to "Use NewMethod", "IsError" to true))
        assertEquals("Use NewMethod", attr.arguments()["Message"])
        assertEquals(true, attr.arguments()["IsError"])
    }

    @Test
    fun `AttributeInfo toString`() {
        val attr = AttributeInfo("TestAttribute")
        assertEquals("[TestAttribute]", attr.toString())
    }

    // -- QualifiedName parsing and formatting --

    @Test
    fun `QualifiedName simple name`() {
        val name = QualifiedName.parse("strlen")
        assertEquals("strlen", name.name())
        assertNull(name.namespace())
        assertEquals("strlen", name.fullName())
        assertFalse(name.isGeneric())
        assertFalse(name.isNested())
    }

    @Test
    fun `QualifiedName empty throws`() {
        assertThrows<IllegalArgumentException> { QualifiedName.parse("") }
        assertThrows<IllegalArgumentException> { QualifiedName.parse("   ") }
    }

    @Test
    fun `QualifiedName CLR dot-separated`() {
        val name = QualifiedName.parse("System.Collections.Generic.Dictionary")
        assertEquals("Dictionary", name.name())
        assertEquals("System.Collections.Generic", name.namespace())
    }

    @Test
    fun `QualifiedName CLR generic arity`() {
        val name = QualifiedName.parse("System.Collections.Generic.List`1")
        assertEquals("List", name.name())
        assertEquals("List`1", name.rawName())
        assertTrue(name.isGeneric())
        assertEquals(1, name.genericArity())
    }

    @Test
    fun `QualifiedName CLR nested type`() {
        val name = QualifiedName.parse("System.Environment+SpecialFolder")
        assertTrue(name.isNested())
        assertEquals("SpecialFolder", name.name())
        assertEquals("SpecialFolder", name.innerName())
        val outer = name.outerType()!!
        assertEquals("Environment", outer.name())
        assertEquals("System", outer.namespace())
    }

    @Test
    fun `QualifiedName CLR deeply nested`() {
        val name = QualifiedName.parse("A.B+C+D")
        assertTrue(name.isNested())
        assertEquals("D", name.name())
        val mid = name.outerType()!!
        assertTrue(mid.isNested())
        assertEquals("C", mid.name())
        val outer = mid.outerType()!!
        assertFalse(outer.isNested())
        assertEquals("B", outer.name())
        assertEquals("A", outer.namespace())
    }

    @Test
    fun `QualifiedName JVM slash-separated`() {
        val name = QualifiedName.parse("java/lang/String")
        assertEquals("String", name.name())
        assertEquals("java.lang", name.namespace())
        assertEquals("java.lang.String", name.fullName())
    }

    @Test
    fun `QualifiedName JVM inner class`() {
        val name = QualifiedName.parse("java/util/Map\$Entry")
        assertTrue(name.isNested())
        assertEquals("Entry", name.name())
        val outer = name.outerType()!!
        assertEquals("Map", outer.name())
        assertEquals("java.util", outer.namespace())
    }

    @Test
    fun `QualifiedName C++ namespace`() {
        val name = QualifiedName.parse("std::vector")
        assertEquals("vector", name.name())
        assertEquals("std", name.namespace())
    }

    @Test
    fun `QualifiedName C++ deep namespace`() {
        val name = QualifiedName.parse("boost::asio::ip::tcp")
        assertEquals("tcp", name.name())
        assertEquals("boost::asio::ip", name.namespace())
    }

    @Test
    fun `QualifiedName C++ template`() {
        val name = QualifiedName.parse("std::vector<int>")
        assertEquals("vector", name.name())
        assertTrue(name.isGeneric())
        assertEquals(1, name.genericArity())
        assertEquals("int", name.genericArguments()[0].name())
    }

    @Test
    fun `QualifiedName C++ nested template`() {
        val name = QualifiedName.parse("std::map<std::string, int>")
        assertEquals("map", name.name())
        assertEquals(2, name.genericArity())
    }

    @Test
    fun `QualifiedName C++ template of templates`() {
        val name = QualifiedName.parse("std::vector<std::pair<int, double>>")
        assertEquals("vector", name.name())
        assertEquals(1, name.genericArity())
        val inner = name.genericArguments()[0]
        assertEquals("pair", inner.name())
        assertEquals(2, inner.genericArity())
    }

    @Test
    fun `QualifiedName factory of`() {
        val name = QualifiedName.of("System.Collections.Generic", "List`1")
        assertEquals("List", name.name())
        assertEquals("System.Collections.Generic", name.namespace())
        assertEquals(1, name.genericArity())
    }

    @Test
    fun `QualifiedName equality same format`() {
        val a = QualifiedName.parse("System.String")
        val b = QualifiedName.parse("System.String")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `QualifiedName equality cross format`() {
        val jvm = QualifiedName.parse("java/lang/String")
        val dot = QualifiedName.of("java.lang", "String")
        assertEquals(jvm, dot)
    }

    @Test
    fun `QualifiedName inequality`() {
        assertNotEquals(QualifiedName.parse("System.String"), QualifiedName.parse("System.Int32"))
    }

    @Test
    fun `QualifiedName segments CLR`() {
        val name = QualifiedName.parse("System.Collections.Generic.List`1")
        assertEquals(listOf("System", "Collections", "Generic", "List`1"), name.segments())
    }

    @Test
    fun `QualifiedName segments JVM`() {
        val name = QualifiedName.parse("java/lang/String")
        assertEquals(listOf("java", "lang", "String"), name.segments())
    }

    @Test
    fun `QualifiedName segments simple`() {
        assertEquals(listOf("strlen"), QualifiedName.parse("strlen").segments())
    }

    @Test
    fun `QualifiedName JVM descriptor primitives`() {
        assertEquals(QualifiedName.INT, QualifiedName.fromDescriptor("I"))
        assertEquals(QualifiedName.LONG, QualifiedName.fromDescriptor("J"))
        assertEquals(QualifiedName.BOOLEAN, QualifiedName.fromDescriptor("Z"))
        assertEquals(QualifiedName.BYTE, QualifiedName.fromDescriptor("B"))
        assertEquals(QualifiedName.CHAR, QualifiedName.fromDescriptor("C"))
        assertEquals(QualifiedName.SHORT, QualifiedName.fromDescriptor("S"))
        assertEquals(QualifiedName.FLOAT, QualifiedName.fromDescriptor("F"))
        assertEquals(QualifiedName.DOUBLE, QualifiedName.fromDescriptor("D"))
        assertEquals(QualifiedName.VOID, QualifiedName.fromDescriptor("V"))
    }

    @Test
    fun `QualifiedName JVM descriptor object`() {
        val name = QualifiedName.fromDescriptor("Ljava/lang/String;")
        assertEquals("String", name.name())
        assertEquals("java.lang", name.namespace())
    }

    @Test
    fun `QualifiedName JVM descriptor array`() {
        val name = QualifiedName.fromDescriptor("[I")
        assertTrue(name.isArray())
        assertEquals(1, name.arrayDimensions())
        assertEquals(QualifiedName.INT, name.elementType())
        assertEquals("int[]", name.fullName())
    }

    @Test
    fun `QualifiedName JVM descriptor multi-dim array`() {
        val name = QualifiedName.fromDescriptor("[[D")
        assertTrue(name.isArray())
        assertEquals(2, name.arrayDimensions())
        assertEquals("double[][]", name.fullName())
    }

    @Test
    fun `QualifiedName JVM descriptor object array`() {
        val name = QualifiedName.fromDescriptor("[Ljava/lang/String;")
        assertTrue(name.isArray())
        assertEquals("java.lang.String[]", name.fullName())
    }

    @Test
    fun `QualifiedName JVM descriptor invalid`() {
        assertThrows<IllegalArgumentException> { QualifiedName.fromDescriptor("") }
        assertThrows<IllegalArgumentException> { QualifiedName.fromDescriptor("X") }
    }

    @Test
    fun `QualifiedName JVM primitive constants`() {
        assertEquals("int", QualifiedName.INT.fullName())
        assertEquals("boolean", QualifiedName.BOOLEAN.fullName())
        assertEquals("void", QualifiedName.VOID.fullName())
        assertTrue(QualifiedName.INT.isPrimitive())
        assertNull(QualifiedName.INT.namespace())
    }

    @Test
    fun `QualifiedName JVM well-known types`() {
        assertEquals("java.lang.String", QualifiedName.JVM_STRING.fullName())
        assertEquals("java.lang.Object", QualifiedName.JVM_OBJECT.fullName())
        assertEquals("java.lang.Class", QualifiedName.JVM_CLASS.fullName())
    }

    @Test
    fun `QualifiedName CLR primitive constants`() {
        assertEquals("System.Int32", QualifiedName.CLR_INT32.fullName())
        assertEquals("System.Boolean", QualifiedName.CLR_BOOL.fullName())
        assertEquals("System.String", QualifiedName.CLR_STRING.fullName())
        assertEquals("System.Void", QualifiedName.CLR_VOID.fullName())
        assertTrue(QualifiedName.CLR_INT32.isPrimitive())
    }

    @Test
    fun `QualifiedName CLR alias resolution`() {
        assertEquals(QualifiedName.CLR_INT32, QualifiedName.fromClrAlias("int"))
        assertEquals(QualifiedName.CLR_BOOL, QualifiedName.fromClrAlias("bool"))
        assertEquals(QualifiedName.CLR_STRING, QualifiedName.fromClrAlias("string"))
        assertEquals(QualifiedName.CLR_DOUBLE, QualifiedName.fromClrAlias("double"))
        assertEquals(QualifiedName.CLR_VOID, QualifiedName.fromClrAlias("void"))
        assertNull(QualifiedName.fromClrAlias("nosuchtype"))
    }

    @Test
    fun `QualifiedName toString is fullName`() {
        assertEquals("System.String", QualifiedName.parse("System.String").toString())
    }

    // -- Demangler: Itanium --

    @Test
    fun `itanium demangle simple function`() {
        val itanium = ItaniumDemangler()
        assertEquals("foo()", itanium.demangle("_Z3foov"))
    }

    @Test
    fun `itanium demangle with int param`() {
        val itanium = ItaniumDemangler()
        val result = itanium.demangle("_Z3fooi")
        assertNotNull(result)
        assertEquals("foo(int)", result)
    }

    @Test
    fun `itanium demangle nested name`() {
        val itanium = ItaniumDemangler()
        val result = itanium.demangle("_ZN3foo3barEv")
        assertNotNull(result)
        assertTrue(result!!.contains("foo::bar"))
    }

    @Test
    fun `itanium demangle pointer type`() {
        val itanium = ItaniumDemangler()
        val result = itanium.demangle("_Z3fooPi")
        assertNotNull(result)
        assertTrue(result!!.contains("int*"))
    }

    @Test
    fun `itanium demangle const ref`() {
        val itanium = ItaniumDemangler()
        val result = itanium.demangle("_Z3fooRKi")
        assertNotNull(result)
        assertTrue(result!!.contains("const"))
    }

    @Test
    fun `itanium cannot demangle unmangled`() {
        val itanium = ItaniumDemangler()
        assertFalse(itanium.canDemangle("main"))
        assertNull(itanium.demangle("main"))
    }

    @Test
    fun `itanium handles double underscore prefix`() {
        val itanium = ItaniumDemangler()
        assertEquals("foo()", itanium.demangle("__Z3foov"))
    }

    @Test
    fun `itanium multiple params`() {
        val itanium = ItaniumDemangler()
        val result = itanium.demangle("_Z3fooidf")
        assertNotNull(result)
        assertTrue(result!!.contains("int"))
        assertTrue(result.contains("double"))
        assertTrue(result.contains("float"))
    }

    // -- Demangler: MSVC --

    @Test
    fun `msvc detects mangled names`() {
        val msvc = MsvcDemangler()
        assertTrue(msvc.canDemangle("?foo@@YAHXZ"))
        assertFalse(msvc.canDemangle("main"))
    }

    @Test
    fun `msvc demangle simple function`() {
        val msvc = MsvcDemangler()
        val result = msvc.demangle("?foo@@YAHXZ")
        assertNotNull(result)
        assertTrue(result!!.contains("foo"))
    }

    @Test
    fun `msvc demangle class method`() {
        val msvc = MsvcDemangler()
        val result = msvc.demangle("?method@MyClass@@QAEHXZ")
        assertNotNull(result)
        assertTrue(result!!.contains("MyClass"))
        assertTrue(result.contains("method"))
    }

    @Test
    fun `msvc cannot demangle unmangled`() {
        val msvc = MsvcDemangler()
        assertNull(msvc.demangle("main"))
    }

    // -- Demangler: Rust --

    @Test
    fun `rust demangle legacy`() {
        val rust = RustDemangler()
        val result = rust.demangle("_ZN4core3fmt5write17h1234567890abcdefE")
        assertNotNull(result)
        assertEquals("core::fmt::write", result)
    }

    @Test
    fun `rust legacy strips hash`() {
        val rust = RustDemangler()
        val result = rust.demangle("_ZN3std2io5stdio6_print17habcdef0123456789E")
        assertEquals("std::io::stdio::_print", result)
    }

    @Test
    fun `rust cannot demangle unmangled`() {
        val rust = RustDemangler()
        assertFalse(rust.canDemangle("main"))
        assertNull(rust.demangle("main"))
    }

    // -- Demangler: Universal --

    @Test
    fun `universal detects itanium scheme`() {
        val uni = UniversalDemangler()
        assertEquals(ManglingScheme.ITANIUM, uni.detect("_Z3foov"))
    }

    @Test
    fun `universal detects msvc scheme`() {
        val uni = UniversalDemangler()
        assertEquals(ManglingScheme.MSVC, uni.detect("?foo@@YAHXZ"))
    }

    @Test
    fun `universal detects rust scheme`() {
        val uni = UniversalDemangler()
        assertEquals(ManglingScheme.RUST, uni.detect("_RNvCs1234_4core3foo"))
        assertEquals(ManglingScheme.RUST, uni.detect("_ZN4core3fmt5write17h1234567890abcdefE"))
    }

    @Test
    fun `universal returns null for unmangled`() {
        val uni = UniversalDemangler()
        assertNull(uni.detect("main"))
        assertNull(uni.demangle("main"))
    }

    @Test
    fun `universal demangles itanium`() {
        val uni = UniversalDemangler()
        assertEquals("foo()", uni.demangle("_Z3foov"))
    }

    @Test
    fun `universal demangles rust`() {
        val uni = UniversalDemangler()
        assertEquals("core::fmt::write", uni.demangle("_ZN4core3fmt5write17h1234567890abcdefE"))
    }

    @Test
    fun `universal demangles msvc`() {
        val uni = UniversalDemangler()
        val result = uni.demangle("?foo@@YAHXZ")
        assertNotNull(result)
        assertTrue(result!!.contains("foo"))
    }

    // -- Symbol mangled name detection --

    @Test
    fun `symbol isMangled for itanium name`() {
        val sym = Symbol(org.kgen.binary.Symbol("_ZN3std6vectorIiE9push_backERKi"), null)
        assertTrue(sym.isMangled())
    }

    @Test
    fun `symbol isMangled for msvc name`() {
        val sym = Symbol(org.kgen.binary.Symbol("?foo@@YAHXZ"), null)
        assertTrue(sym.isMangled())
    }

    @Test
    fun `symbol not mangled for plain name`() {
        val sym = Symbol(org.kgen.binary.Symbol("strlen"), null)
        assertFalse(sym.isMangled())
        assertNull(sym.demangledName())
    }

    @Test
    fun `symbol demangledName returns demangled result`() {
        val sym = Symbol(org.kgen.binary.Symbol("_Z3foov"), null)
        assertTrue(sym.isMangled())
        val demangled = sym.demangledName()
        assertNotNull(demangled)
        assertEquals("foo()", demangled)
    }

    // -- Instruction interface --

    @Test
    fun `instruction interface properties`() {
        val insn = object : Instruction {
            override val address: Long = 0x1000
            override val bytes: ByteArray = byteArrayOf(0x90.toByte())
            override val mnemonic: String = "nop"
            override fun operandsText(): String = ""
            override fun text(): String = "nop"
        }
        assertEquals(0x1000L, insn.address)
        assertEquals("nop", insn.mnemonic)
        assertEquals(1, insn.size)
        assertEquals("", insn.operandsText())
        assertEquals("nop", insn.text())
    }

    @Test
    fun `instruction size defaults to bytes length`() {
        val insn = object : Instruction {
            override val address: Long = 0
            override val bytes: ByteArray = byteArrayOf(0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00)
            override val mnemonic: String = "mov"
            override fun operandsText(): String = "eax, 42"
            override fun text(): String = "mov eax, 42"
        }
        assertEquals(5, insn.size)
    }

    // -- Disassembly via Module --

    @Test
    fun `disassemble x86 bytes`() {
        val code = byteArrayOf(0x90.toByte(), 0xC3.toByte())
        val module = Module.fromObjectFile(testObjectFile())
        val insns = module.disassembleBytes(code)
        assertEquals(2, insns.size)
        assertEquals("nop", insns[0].mnemonic)
        assertEquals("ret", insns[1].mnemonic)
    }

    @Test
    fun `disassemble JVM bytes`() {
        val code = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())
        val module = Module.fromObjectFile(testObjectFile(
            format = ObjectFormat.JVM_CLASS,
            arch = Architecture(ArchType.JVM),
        ))
        val insns = module.disassembleBytes(code)
        assertEquals(4, insns.size)
        assertEquals("iload_0", insns[0].mnemonic)
        assertEquals("iadd", insns[2].mnemonic)
        assertEquals("ireturn", insns[3].mnemonic)
    }

    @Test
    fun `disassemble ARM64 bytes`() {
        val code = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte())
        val module = Module.fromObjectFile(testObjectFile(
            arch = Architecture(ArchType.AARCH64),
        ))
        val insns = module.disassembleBytes(code)
        assertEquals(1, insns.size)
        assertEquals("ret", insns[0].mnemonic)
    }

    @Test
    fun `disassemble RISC-V bytes`() {
        val code = byteArrayOf(0x67, 0x80.toByte(), 0x00, 0x00)
        val module = Module.fromObjectFile(testObjectFile(
            arch = Architecture(ArchType.RISCV64),
        ))
        val insns = module.disassembleBytes(code)
        assertEquals(1, insns.size)
        assertTrue(insns[0].mnemonic == "ret" || insns[0].mnemonic == "jalr")
    }

    @Test
    fun `disassemble named function in module`() {
        val code = byteArrayOf(0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00, 0xC3.toByte())
        val section = Section(".text", SectionKind.TEXT, code, address = 0)
        val syms = listOf(
            org.kgen.binary.Symbol("getFortyTwo", 0, code.size.toLong(), ".text",
                SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms, sections = listOf(section)))
        val insns = module.disassemble("getFortyTwo")
        assertNotNull(insns)
        assertTrue(insns!!.isNotEmpty())
        assertEquals("mov", insns[0].mnemonic)
    }

    @Test
    fun `disassemble nonexistent function returns null`() {
        val module = Module.fromObjectFile(testObjectFile())
        assertNull(module.disassemble("nonexistent"))
    }

    // -- Cross-format consistency --

    @Test
    fun `all formats have consistent symbol API`() {
        val formats = listOf(
            ObjectFormat.ELF to Architecture.X86_64_LINUX,
            ObjectFormat.PE_COFF to Architecture.X86_64_WINDOWS,
            ObjectFormat.MACH_O to Architecture.X86_64_MACOS,
            ObjectFormat.WASM_MODULE to Architecture.WASM32,
            ObjectFormat.JVM_CLASS to Architecture(ArchType.JVM),
        )
        for ((format, arch) in formats) {
            val syms = listOf(
                org.kgen.binary.Symbol("test_func", 0x100, 32, kind = SymbolKind.FUNCTION),
            )
            val module = Module.fromObjectFile(testObjectFile(format = format, arch = arch, symbols = syms))
            assertEquals(1, module.symbols().size, "Format $format should have 1 symbol")
            assertEquals("test_func", module.symbol("test_func")!!.name())
            assertTrue(module.symbol("test_func")!!.isFunction())
            assertEquals(1, module.functions().size, "Format $format should have 1 function")
        }
    }

    @Test
    fun `all formats return format-specific accessor`() {
        val elfModule = Module.fromObjectFile(testObjectFile(format = ObjectFormat.ELF))
        assertNull(elfModule.elf()) // no raw bytes, so cache returns null
        assertNull(elfModule.pe())
        assertNull(elfModule.machO())
        assertNull(elfModule.classFile())
        assertNull(elfModule.wasm())
    }

    @Test
    fun `JVM class file fixture has classFile accessor`() {
        val module = loadClassFixture()
        assertNotNull(module.classFile())
        assertNull(module.elf())
        assertNull(module.pe())
    }

    @Test
    fun `cross-format TypeRef is consistent`() {
        val i32 = TypeRef.I32
        val methodJvm = MethodInfo("add", returnType = i32, params = listOf(
            ParameterInfo("a", i32, 0),
            ParameterInfo("b", i32, 1),
        ))
        val methodClr = MethodInfo("Add", returnType = i32, params = listOf(
            ParameterInfo("a", i32, 0),
            ParameterInfo("b", i32, 1),
        ))
        assertEquals(methodJvm.returnType(), methodClr.returnType())
        assertEquals(methodJvm.parameters()[0].type(), methodClr.parameters()[0].type())
    }

    @Test
    fun `QualifiedName cross-format normalization`() {
        val jvm = QualifiedName.parse("java/lang/String")
        val dot = QualifiedName.parse("java.lang.String")
        assertEquals(jvm.fullName(), dot.fullName())
        assertEquals(jvm, dot)
    }

    // -- TypeInfo from real JVM fixtures --

    @Test
    fun `JVM class fixture produces types`() {
        val module = loadClassFixture()
        val types = module.types()
        assertFalse(types.isEmpty())
    }

    @Test
    fun `JVM class fixture type has methods`() {
        val module = loadClassFixture()
        val types = module.types()
        if (types.isNotEmpty()) {
            val type = types[0]
            assertNotNull(type.name())
            assertNotNull(type.fullName())
        }
    }

    @Test
    fun `Shape fixture is interface or abstract`() {
        val module = loadShapeFixture()
        val types = module.types()
        assertFalse(types.isEmpty())
    }

    @Test
    fun `Color fixture is enum`() {
        val module = loadColorFixture()
        val types = module.types()
        assertFalse(types.isEmpty())
    }

    // -- Complex TypeInfo builder scenarios --

    @Test
    fun `TypeInfo with full hierarchy`() {
        val objectType = TypeInfo.builder("System.Object").kind(TypeKind.CLASS).build()
        val iComparable = TypeInfo.builder("System.IComparable").kind(TypeKind.INTERFACE).build()
        val iCloneable = TypeInfo.builder("System.ICloneable").kind(TypeKind.INTERFACE).build()

        val stringType = TypeInfo.builder("System.String")
            .kind(TypeKind.CLASS)
            .addFlag(TypeFlag.PUBLIC)
            .addFlag(TypeFlag.SEALED)
            .baseType(objectType)
            .addInterface(iComparable)
            .addInterface(iCloneable)
            .addField(FieldInfo("Length", TypeRef.I32, flags = setOf(FieldFlag.PUBLIC, FieldFlag.READONLY)))
            .addMethod(MethodInfo("Contains", returnType = TypeRef.BOOL, params = listOf(
                ParameterInfo("value", TypeRef.of("System.String"), 0),
            ), flags = setOf(MethodFlag.PUBLIC)))
            .addMethod(MethodInfo("Substring", returnType = TypeRef.of("System.String"), params = listOf(
                ParameterInfo("startIndex", TypeRef.I32, 0),
            ), flags = setOf(MethodFlag.PUBLIC)))
            .addConstructor(MethodInfo(".ctor", flags = setOf(MethodFlag.PUBLIC, MethodFlag.CONSTRUCTOR)))
            .build()

        assertEquals("String", stringType.name())
        assertEquals("System", stringType.namespace())
        assertTrue(stringType.isPublic())
        assertTrue(stringType.isSealed())
        assertTrue(stringType.isClass())
        assertEquals(objectType, stringType.baseType())
        assertEquals(2, stringType.interfaces().size)
        assertEquals(1, stringType.fields().size)
        assertEquals(2, stringType.methods().size)
        assertEquals(1, stringType.constructors().size)
        assertNotNull(stringType.field("Length"))
        assertNotNull(stringType.method("Contains"))
        assertNotNull(stringType.method("Substring"))
    }

    @Test
    fun `TypeInfo enum with fields`() {
        val enumType = TypeInfo.builder("DayOfWeek")
            .kind(TypeKind.ENUM)
            .addFlag(TypeFlag.PUBLIC)
            .addField(FieldInfo("Monday", TypeRef.I32, flags = setOf(FieldFlag.PUBLIC, FieldFlag.STATIC, FieldFlag.CONST), constantVal = 1))
            .addField(FieldInfo("Tuesday", TypeRef.I32, flags = setOf(FieldFlag.PUBLIC, FieldFlag.STATIC, FieldFlag.CONST), constantVal = 2))
            .addField(FieldInfo("Wednesday", TypeRef.I32, flags = setOf(FieldFlag.PUBLIC, FieldFlag.STATIC, FieldFlag.CONST), constantVal = 3))
            .build()

        assertTrue(enumType.isEnum())
        assertEquals(3, enumType.fields().size)
        assertTrue(enumType.field("Monday")!!.isConst())
        assertEquals(1, enumType.field("Monday")!!.constantValue())
    }

    @Test
    fun `TypeInfo struct with layout`() {
        val structType = TypeInfo.builder("Vector3")
            .kind(TypeKind.STRUCT)
            .addFlag(TypeFlag.PUBLIC)
            .size(12)
            .packingSize(4)
            .addField(FieldInfo("x", TypeRef.F32, byteOffset = 0))
            .addField(FieldInfo("y", TypeRef.F32, byteOffset = 4))
            .addField(FieldInfo("z", TypeRef.F32, byteOffset = 8))
            .build()

        assertTrue(structType.isStruct())
        assertEquals(12, structType.size())
        assertEquals(4, structType.packingSize())
        assertEquals(3, structType.fields().size)
        assertEquals(0, structType.field("x")!!.offset())
        assertEquals(4, structType.field("y")!!.offset())
        assertEquals(8, structType.field("z")!!.offset())
    }

    @Test
    fun `TypeInfo generic type with multiple args`() {
        val type = TypeInfo.builder("System.Collections.Generic.Dictionary`2")
            .addGenericArg(TypeRef.genericParam("TKey"))
            .addGenericArg(TypeRef.genericParam("TValue"))
            .build()
        assertTrue(type.isGeneric())
        assertEquals(2, type.genericArguments().size)
    }

    @Test
    fun `TypeInfo with module back-reference`() {
        val module = Module.fromObjectFile(testObjectFile())
        val type = TypeInfo.builder("TestType")
            .module(module)
            .build()
        assertSame(module, type.module())
    }

    // -- Save and bytes --

    @Test
    fun `save empty module fails`() {
        val module = Module.fromObjectFile(testObjectFile())
        assertThrows<IllegalArgumentException> {
            module.save(Path.of("/tmp/should_not_exist.o"))
        }
    }

    @Test
    fun `toBytes returns copy`() {
        val module = loadClassFixture()
        val a = module.toBytes()
        val b = module.toBytes()
        assertArrayEquals(a, b)
        assertNotSame(a, b)
    }
}
