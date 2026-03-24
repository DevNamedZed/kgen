package org.kgen.integration.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.elf.ElfObjectType
import org.kgen.binary.elf.ElfReader
import org.kgen.binary.pe.PeReader
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.runtime.compile.CHeaderGenerator
import org.kgen.runtime.compile.NativeLibraryCompiler
import org.kgen.runtime.compile.OutputPlatform
import org.kgen.target.jvm.*

/**
 * Native library end-to-end: Java classfiles -> shared library (.so/.dll) + C header.
 *
 * Tests the NativeLibraryCompiler pipeline:
 *   ClassFileBuilder -> NativeLibraryCompiler -> .so/.dll with exported C symbols
 */
class NativeLibraryEndToEndTest {

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

    // -- Shared library: classfiles -> .so --

    @Test
    fun compileToElfSharedLibrary() {
        val classBytes = buildClass("org/kgen/test/MathLib", listOf(
            "add:(II)I" to { code ->
                code.iload(0); code.iload(1); code.iadd(); code.ireturn()
            },
            "mul:(II)I" to { code ->
                code.iload(0); code.iload(1); code.imul(); code.ireturn()
            },
        ))

        val lib = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val soBytes = lib.compile(listOf(classBytes))

        // Valid ELF
        assertTrue(ElfReader.canRead(soBytes))
        val elf = ElfReader.read(soBytes)
        // Shared library is ET_DYN
        assertEquals(ElfObjectType.DYN, elf.header.type, "Should be ET_DYN (shared object)")
    }

    @Test
    fun compileToPeDll() {
        val classBytes = buildClass("org/kgen/test/WinLib", listOf(
            "compute:(I)I" to { code ->
                code.iload(0); code.iload(0); code.imul(); code.ireturn()
            },
        ))

        val lib = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val dllBytes = lib.compile(listOf(classBytes))

        assertTrue(PeReader.canRead(dllBytes))
        val pe = PeReader.read(dllBytes)
        assertTrue(pe.isPe)
    }

    // -- C header generation --

    @Test
    fun generateCHeaderFromIrModule() {
        // CHeaderGenerator works on IR modules with EXTERNAL linkage.
        // Build an IR module directly with exported functions.
        val ir = ModuleBuilder("header_test", Target.x86_64())
        val p = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32,
            linkage = Linkage.EXTERNAL)
        ir.appendBlock("entry")
        ir.ret(ir.add(p[0], p[1]))
        ir.finalizeFunction()

        ir.createFunction("negate", listOf(Param("x", Type.I32)), Type.I32,
            linkage = Linkage.EXTERNAL)
        ir.appendBlock("entry")
        ir.ret(ir.sub(Constant.I32(0), Parameter("x", Type.I32, 0)))
        ir.finalizeFunction()

        val module = ir.build()
        val header = CHeaderGenerator.generate(module)

        assertTrue(header.contains("#ifndef"), "Header should have include guard")
        assertTrue(header.contains("#define"), "Header should have include guard define")
        assertTrue(header.contains("add"), "Header should declare 'add': $header")
        assertTrue(header.contains("negate"), "Header should declare 'negate': $header")
        assertTrue(header.contains("int32_t"), "Header should use C types: $header")
    }

    @Test
    fun generateCHeaderWithCustomGuard() {
        val classBytes = buildClass("org/kgen/test/GuardLib", listOf(
            "foo:(I)I" to { code ->
                code.iload(0); code.ireturn()
            },
        ))

        val lib = NativeLibraryCompiler(Target.x86_64())
        val header = lib.generateHeader(listOf(classBytes), "MY_CUSTOM_GUARD_H")

        assertTrue(header.contains("MY_CUSTOM_GUARD_H"), "Should use custom guard name: $header")
    }

    // -- Multi-class library --

    @Test
    fun multiClassSharedLibrary() {
        val mathClass = buildClass("org/kgen/test/Math", listOf(
            "square:(I)I" to { code ->
                code.iload(0); code.iload(0); code.imul(); code.ireturn()
            }
        ))
        val strClass = buildClass("org/kgen/test/Str", listOf(
            "identity:(I)I" to { code ->
                code.iload(0); code.ireturn()
            }
        ))

        val lib = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val soBytes = lib.compile(listOf(mathClass, strClass))

        assertTrue(ElfReader.canRead(soBytes))
    }

    // -- Cross-architecture library --

    @Test
    fun compileToArm64SharedLibrary() {
        val classBytes = buildClass("org/kgen/test/ArmLib", listOf(
            "inc:(I)I" to { code ->
                code.iload(0); code.iconst(1); code.iadd(); code.ireturn()
            }
        ))

        val lib = NativeLibraryCompiler(Target.arm64(), OutputPlatform.LINUX)
        val soBytes = lib.compile(listOf(classBytes))
        assertTrue(ElfReader.canRead(soBytes))
    }
}
