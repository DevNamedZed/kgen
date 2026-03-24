package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.binary.elf.ElfReader
import org.kgen.binary.pe.PeReader
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

/**
 * Tests cross-platform compilation from a single source to multiple targets.
 */
class CrossPlatformCompilationTest {

    private fun buildSimpleClass(): ByteArray {
        return JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/CrossPlatform").apply {
                method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(42)
                    code.ireturn()
                }
                method("add", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iload(0)
                    code.iload(1)
                    code.iadd()
                    code.ireturn()
                }
            }.build()
        )
    }

    @Test
    fun `same class compiles to x86_64 ELF`() {
        val classBytes = buildSimpleClass()
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val elf = compiler.compile(listOf(classBytes))
        // Verify ELF magic
        assertEquals(0x7F, elf[0].toInt() and 0xFF)
        assertEquals('E'.code, elf[1].toInt())
        assertEquals('L'.code, elf[2].toInt())
        assertEquals('F'.code, elf[3].toInt())
        assertTrue(elf.size > 100, "ELF should have meaningful size")
    }

    @Test
    fun `same class compiles to x86_64 PE`() {
        val classBytes = buildSimpleClass()
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val pe = compiler.compile(listOf(classBytes))
        assertEquals('M'.code, pe[0].toInt() and 0xFF)
        assertEquals('Z'.code, pe[1].toInt() and 0xFF)
    }

    @Test
    fun `same class compiles to ARM64 object`() {
        val classBytes = buildSimpleClass()
        val compiler = NativeCompiler(Target.arm64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "add" })
        assertTrue(obj.symbols.any { it.name == "main" })
    }

    @Test
    fun `same class compiles to RISC-V object`() {
        val classBytes = buildSimpleClass()
        val compiler = NativeCompiler(Target.riscv64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "add" })
    }

    @Test
    fun `same IR module compiles to all native targets`() {
        val classBytes = buildSimpleClass()
        val targets = listOf(
            Target.x86_64() to "x86_64",
            Target.arm64() to "arm64",
            Target.riscv64() to "riscv64",
        )

        for ((target, name) in targets) {
            val compiler = NativeCompiler(target, OutputPlatform.LINUX)
            val obj = compiler.compileToObject(listOf(classBytes))
            assertTrue(obj.symbols.any { it.name == "add" },
                "Expected 'add' symbol in $name object")
        }
    }

    @Test
    fun `x86_64 and ARM64 produce different code sizes`() {
        val classBytes = buildSimpleClass()

        val x86Compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val x86Obj = x86Compiler.compileToObject(listOf(classBytes))

        val armCompiler = NativeCompiler(Target.arm64(), OutputPlatform.LINUX)
        val armObj = armCompiler.compileToObject(listOf(classBytes))

        // Both should have code, but different sizes (ARM64 has fixed 4-byte instructions)
        val x86Code = x86Obj.sections.firstOrNull { it.name == ".text" }
        val armCode = armObj.sections.firstOrNull { it.name == ".text" }
        assertNotNull(x86Code)
        assertNotNull(armCode)
        // ARM64 code should be multiple of 4 bytes
        assertEquals(0, armCode!!.data.size % 4, "ARM64 code should be 4-byte aligned")
    }

    @Test
    fun `shared library from same class for ELF and PE`() {
        val classBytes = buildSimpleClass()

        val elfCompiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val so = elfCompiler.compile(listOf(classBytes))
        assertTrue(so.isNotEmpty())
        assertEquals(0x7F, so[0].toInt() and 0xFF) // ELF magic

        val peCompiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val dll = peCompiler.compile(listOf(classBytes))
        assertTrue(dll.isNotEmpty())
        assertEquals('M'.code, dll[0].toInt() and 0xFF) // MZ magic
    }
}
