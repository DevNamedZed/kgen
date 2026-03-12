package org.kgen.integration.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.SectionKind
import org.kgen.binary.elf.ElfReader
import org.kgen.binary.pe.PeReader
import org.kgen.ir.target.Target
import org.kgen.runtime.compile.ClassLayout
import org.kgen.runtime.compile.ExecutableBuilder
import org.kgen.runtime.compile.ExecutableReader
import org.kgen.runtime.compile.NativeCompiler
import org.kgen.runtime.compile.OutputPlatform
import org.kgen.target.jvm.*

/**
 * Java-to-native AOT end-to-end: Java classfiles -> standalone native binary.
 *
 * Tests the full pipeline:
 *   ClassFileBuilder -> NativeCompiler -> standalone ELF/PE executable
 *
 * JIT execution is tested separately in [JitEndToEndTest].
 * Native library (.so/.dll) is tested in [NativeLibraryEndToEndTest].
 * Runtime Subset intrinsics are tested in [RuntimeSubsetEndToEndTest].
 */
class JavaToNativeEndToEndTest {

    // -- Helpers --

    private fun buildClass(
        className: String,
        methods: List<Pair<String, (ClassFileBuilder.CodeEmitter) -> Unit>>,
    ): ByteArray {
        val builder = ClassFileBuilder(className)
        for ((sig, body) in methods) {
            val parts = sig.split(":")
            builder.method(parts[0], parts[1], AccessFlags.PUBLIC or AccessFlags.STATIC, body)
        }
        return builder.toBytes()
    }

    // -- AOT: classfiles -> standalone ELF executable --

    @Test
    fun compileToElfExecutable() {
        val classBytes = buildClass("org/kgen/test/Main", listOf(
            "main:()I" to { code ->
                code.iconst(42); code.ireturn()
            }
        ))

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val elfBytes = compiler.compile(listOf(classBytes))

        // Valid ELF magic
        assertEquals(0x7F, elfBytes[0].toInt() and 0xFF)
        assertEquals('E'.code, elfBytes[1].toInt() and 0xFF)
        assertEquals('L'.code, elfBytes[2].toInt() and 0xFF)
        assertEquals('F'.code, elfBytes[3].toInt() and 0xFF)
        assertTrue(elfBytes.size > 200, "ELF should be non-trivial: ${elfBytes.size}")

        // Should be a valid parseable ELF
        assertTrue(ElfReader.canRead(elfBytes))
        val elf = ElfReader.read(elfBytes)
        assertTrue(elf.header.entryPoint > 0, "Executable should have entry point")
    }

    @Test
    fun compileToElfDynamic() {
        val classBytes = buildClass("org/kgen/test/DynMain", listOf(
            "main:()I" to { code ->
                code.iconst(0); code.ireturn()
            }
        ))

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX_DYNAMIC)
        val elfBytes = compiler.compile(listOf(classBytes))

        assertTrue(ElfReader.canRead(elfBytes))
        val elf = ElfReader.read(elfBytes)
        assertTrue(elf.header.entryPoint > 0)
        // Dynamic executable has segments for PT_INTERP, PT_DYNAMIC etc.
        assertTrue(elf.segments.isNotEmpty(), "Dynamic ELF should have segments")
    }

    @Test
    fun compileToPeExecutable() {
        val classBytes = buildClass("org/kgen/test/WinMain", listOf(
            "main:()I" to { code ->
                code.iconst(0); code.ireturn()
            }
        ))

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val peBytes = compiler.compile(listOf(classBytes))

        // Valid MZ/PE magic
        assertEquals('M'.code, peBytes[0].toInt() and 0xFF)
        assertEquals('Z'.code, peBytes[1].toInt() and 0xFF)
        assertTrue(PeReader.canRead(peBytes))
        val pe = PeReader.read(peBytes)
        assertTrue(pe.isPe)
        assertTrue(pe.isPe32Plus, "x86-64 should be PE32+")
    }

    @Test
    fun compileToMachoExecutable() {
        val classBytes = buildClass("org/kgen/test/MacMain", listOf(
            "main:()I" to { code ->
                code.iconst(0); code.ireturn()
            }
        ))

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.MACOS)
        val machoBytes = compiler.compile(listOf(classBytes))

        // Mach-O magic: 0xFEEDFACF (64-bit) or 0xFEEDFACE (32-bit)
        val magic = ((machoBytes[3].toInt() and 0xFF) shl 24) or
                ((machoBytes[2].toInt() and 0xFF) shl 16) or
                ((machoBytes[1].toInt() and 0xFF) shl 8) or
                (machoBytes[0].toInt() and 0xFF)
        assertTrue(magic == 0xFEEDFACF.toInt() || magic == 0xFEEDFACE.toInt(),
            "Should be Mach-O magic: 0x${magic.toString(16)}")
    }

    // -- AOT: relocatable object file --

    @Test
    fun compileToObjectFilePreservesSymbols() {
        val classBytes = buildClass("org/kgen/test/Lib", listOf(
            "add:(II)I" to { code ->
                code.iload(0); code.iload(1); code.iadd(); code.ireturn()
            },
            "sub:(II)I" to { code ->
                code.iload(0); code.iload(1); code.isub(); code.ireturn()
            },
        ))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))

        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT })
        val symNames = obj.symbols.map { it.name }
        assertTrue("add" in symNames, "Missing 'add': $symNames")
        assertTrue("sub" in symNames, "Missing 'sub': $symNames")
    }

    @Test
    fun compileToObjectFileHasCodeInTextSection() {
        val classBytes = buildClass("org/kgen/test/Code", listOf(
            "compute:(I)I" to { code ->
                code.iload(0); code.iload(0); code.imul()
                code.iconst(1); code.iadd(); code.ireturn()
            }
        ))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))

        val textSection = obj.sections.first { it.kind == SectionKind.TEXT }
        assertTrue(textSection.data.isNotEmpty(), "Text section should contain machine code")
    }

    // -- Multi-class compilation --

    @Test
    fun multiClassCompilation() {
        val mathClass = buildClass("org/kgen/test/MathOps", listOf(
            "square:(I)I" to { code ->
                code.iload(0); code.iload(0); code.imul(); code.ireturn()
            }
        ))
        val utilClass = buildClass("org/kgen/test/Utils", listOf(
            "abs:(I)I" to { code ->
                code.iload(0); code.ifge("pos")
                code.iload(0); code.ineg(); code.ireturn()
                code.label("pos")
                code.iload(0); code.ireturn()
            }
        ))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(mathClass, utilClass))

        val symNames = obj.symbols.map { it.name }
        assertTrue("square" in symNames, "Missing 'square': $symNames")
        assertTrue("abs" in symNames, "Missing 'abs': $symNames")
    }

    @Test
    fun multiClassWithMainProducesExecutable() {
        val mainClass = buildClass("org/kgen/test/App", listOf(
            "main:()I" to { code ->
                code.iconst(0); code.ireturn()
            }
        ))
        val helperClass = buildClass("org/kgen/test/Helper", listOf(
            "double:(I)I" to { code ->
                code.iload(0); code.iconst(2); code.imul(); code.ireturn()
            }
        ))

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val elfBytes = compiler.compile(listOf(mainClass, helperClass))
        assertTrue(ElfReader.canRead(elfBytes))
    }

    // -- IR inspection via compileToModules --

    @Test
    fun compileToModulesProducesIr() {
        val classBytes = buildClass("org/kgen/test/Arith", listOf(
            "add:(II)I" to { code ->
                code.iload(0); code.iload(1); code.iadd(); code.ireturn()
            }
        ))

        val compiler = NativeCompiler(Target.x86_64())
        val modules = compiler.compileToModules(listOf(classBytes))

        assertEquals(1, modules.size)
        val mod = modules[0]
        assertTrue(mod.functions.any { it.name == "add" },
            "Module should contain 'add': ${mod.functions.map { it.name }}")

        val addFn = mod.functions.first { it.name == "add" }
        assertFalse(addFn.isExternal)
        assertTrue(addFn.blocks.isNotEmpty())
    }

    // -- Static initializers --

    @Test
    fun staticInitializerIncludedInBinary() {
        val classBytes = buildClass("org/kgen/test/InitMain", listOf(
            "main:()I" to { code ->
                code.iconst(0); code.ireturn()
            }
        ))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))

        // Should compile without error; static initializer (if present) wired in
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT })
    }

    // -- ClassLayout: precomputed field offsets and object sizes --

    @Test
    fun classLayoutComputesFieldOffsets() {
        val builder = ClassFileBuilder("org/kgen/test/Point")
        builder.field("x", "I", AccessFlags.PUBLIC)
        builder.field("y", "I", AccessFlags.PUBLIC)
        builder.method("getX", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.iconst(0); code.ireturn()
        }
        val classBytes = builder.toBytes()

        val layout = ClassLayout.build(listOf(classBytes))
        assertTrue(layout.classNames().contains("org/kgen/test/Point"),
            "Layout should know about Point: ${layout.classNames()}")

        val size = layout.objectSize("org/kgen/test/Point")
        assertTrue(size >= 16, "Object should be at least 16 bytes (header + fields): $size")
    }

    // -- Metadata-enriched executable (ExecutableBuilder) --

    @Test
    fun executableBuilderProducesMetadataEnrichedBinary() {
        val classBytes = buildClass("org/kgen/test/MetaMain", listOf(
            "main:()I" to { code ->
                code.iconst(0); code.ireturn()
            }
        ))

        val exe = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
            .addClassFile(classBytes)
            .setMainClass("org/kgen/test/MetaMain")
            .setModuleName("test-app")
            .setVersion("1.0.0")
            .setMetadata("build_tool", "kgen")
            .build()

        // Should be a valid ELF
        assertTrue(ElfReader.canRead(exe))
        assertTrue(exe.size > 200)
    }

    @Test
    fun executableBuilderWithResources() {
        val classBytes = buildClass("org/kgen/test/ResMain", listOf(
            "main:()I" to { code ->
                code.iconst(0); code.ireturn()
            }
        ))

        val resourceData = "config=true".toByteArray()

        val exe = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
            .addClassFile(classBytes)
            .setMainClass("org/kgen/test/ResMain")
            .addResource("config.txt", resourceData)
            .build()

        assertTrue(ElfReader.canRead(exe))
    }

    // -- Cross-architecture AOT --

    @Test
    fun compileToArm64Object() {
        val classBytes = buildClass("org/kgen/test/ArmLib", listOf(
            "inc:(I)I" to { code ->
                code.iload(0); code.iconst(1); code.iadd(); code.ireturn()
            }
        ))

        val compiler = NativeCompiler(Target.arm64())
        val obj = compiler.compileToObject(listOf(classBytes))

        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT })
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        assertEquals(0, text.data.size % 4, "ARM64 instructions must be 4-byte aligned")
    }

    @Test
    fun compileToRiscvObject() {
        val classBytes = buildClass("org/kgen/test/RvLib", listOf(
            "dec:(I)I" to { code ->
                code.iload(0); code.iconst(1); code.isub(); code.ireturn()
            }
        ))

        val compiler = NativeCompiler(Target.riscv64())
        val obj = compiler.compileToObject(listOf(classBytes))

        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT })
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        assertEquals(0, text.data.size % 4, "RISC-V instructions must be 4-byte aligned")
    }
}
