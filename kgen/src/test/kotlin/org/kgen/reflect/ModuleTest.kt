package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.kgen.binary.*
import java.nio.file.Path

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

    // -- Disassembly --

    @Test
    fun disassembleX86Function() {
        // x86-64: mov eax, 42; ret (B8 2A 00 00 00  C3)
        val code = byteArrayOf(0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00, 0xC3.toByte())
        val section = Section(".text", SectionKind.TEXT, code, address = 0)
        val syms = listOf(
            org.kgen.binary.Symbol("getFortyTwo", 0, code.size.toLong(), ".text",
                SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
        )
        val module = Module.fromObjectFile(testObjectFile(
            symbols = syms,
            sections = listOf(section),
        ))

        val insns = module.disassemble("getFortyTwo")
        assertNotNull(insns)
        assertTrue(insns!!.isNotEmpty())
        assertEquals("mov", insns[0].mnemonic)
    }

    @Test
    fun disassembleNotFound() {
        val module = Module.fromObjectFile(testObjectFile())
        assertNull(module.disassemble("nonexistent"))
    }

    @Test
    fun disassembleBytesX86() {
        // nop; ret (90 C3)
        val code = byteArrayOf(0x90.toByte(), 0xC3.toByte())
        val module = Module.fromObjectFile(testObjectFile())
        val insns = module.disassembleBytes(code)
        assertEquals(2, insns.size)
        assertEquals("nop", insns[0].mnemonic)
        assertEquals("ret", insns[1].mnemonic)
    }

    @Test
    fun disassembleJvmBytes() {
        // iload_0, iload_1, iadd, ireturn
        val code = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())
        val module = Module.fromObjectFile(testObjectFile(
            format = ObjectFormat.JVM_CLASS,
            arch = Architecture(ArchType.JVM),
        ))
        val insns = module.disassembleBytes(code)
        assertEquals(4, insns.size)
        assertEquals("iload_0", insns[0].mnemonic)
        assertEquals("iload_1", insns[1].mnemonic)
        assertEquals("iadd", insns[2].mnemonic)
        assertEquals("ireturn", insns[3].mnemonic)
    }

    @Test
    fun disassembleArm64Bytes() {
        // ret (0xD65F03C0)
        val code = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte())
        val module = Module.fromObjectFile(testObjectFile(
            arch = Architecture(ArchType.AARCH64),
        ))
        val insns = module.disassembleBytes(code)
        assertEquals(1, insns.size)
        assertEquals("ret", insns[0].mnemonic)
    }

    @Test
    fun disassembleRiscvBytes() {
        // ret = jalr zero, ra, 0 (0x00008067)
        val code = byteArrayOf(0x67, 0x80.toByte(), 0x00, 0x00)
        val module = Module.fromObjectFile(testObjectFile(
            arch = Architecture(ArchType.RISCV64),
        ))
        val insns = module.disassembleBytes(code)
        assertEquals(1, insns.size)
        // May be "ret" pseudo or "jalr"
        assertTrue(insns[0].mnemonic == "ret" || insns[0].mnemonic == "jalr")
    }

    // -- Save / toBytes --

    private fun loadClassFixture(): Module {
        val url = javaClass.getResource("/fixtures/java/Calculator.class")!!
        return Module.fromFile(Path.of(url.toURI()).toString())
    }

    @Test
    fun saveCopiesRawBytes(@TempDir tmp: Path) {
        val module = loadClassFixture()
        val outPath = tmp.resolve("copy.class")
        module.save(outPath)
        val original = module.bytes()
        val saved = java.nio.file.Files.readAllBytes(outPath)
        assertArrayEquals(original, saved)
    }

    @Test
    fun saveStringOverload(@TempDir tmp: Path) {
        val module = loadClassFixture()
        val outPath = tmp.resolve("copy2.class").toString()
        module.save(outPath)
        val saved = java.nio.file.Files.readAllBytes(Path.of(outPath))
        assertArrayEquals(module.bytes(), saved)
    }

    @Test
    fun toBytesReturnsCopy() {
        val module = loadClassFixture()
        val a = module.toBytes()
        val b = module.toBytes()
        assertArrayEquals(a, b)
        assertNotSame(a, b)
    }

    @Test
    fun saveRewriteJvm(@TempDir tmp: Path) {
        val module = loadClassFixture()
        val outPath = tmp.resolve("rewritten.class")
        module.save(outPath, rewrite = true)
        // Rewritten class should be loadable
        val reloaded = Module.fromFile(outPath)
        assertEquals(ObjectFormat.JVM_CLASS, reloaded.format())
        assertTrue(reloaded.bytes().isNotEmpty())
    }

    @Test
    fun saveEmptyModuleFails(@TempDir tmp: Path) {
        val module = Module.fromObjectFile(testObjectFile())
        val outPath = tmp.resolve("empty.o")
        assertThrows(IllegalArgumentException::class.java) {
            module.save(outPath)
        }
    }

    @Test
    fun toBytesRewriteJvm() {
        val module = loadClassFixture()
        val rewritten = module.toBytes(rewrite = true)
        assertTrue(rewritten.isNotEmpty())
        // Should start with 0xCAFEBABE
        assertEquals(0xCA.toByte(), rewritten[0])
        assertEquals(0xFE.toByte(), rewritten[1])
        assertEquals(0xBA.toByte(), rewritten[2])
        assertEquals(0xBE.toByte(), rewritten[3])
    }
}
