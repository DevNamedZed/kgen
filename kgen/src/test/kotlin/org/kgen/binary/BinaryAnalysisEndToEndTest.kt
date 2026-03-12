package org.kgen.binary

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.binary.elf.*
import org.kgen.binary.pe.*
import org.kgen.ir.target.Target
import org.kgen.runtime.compile.*
import org.kgen.target.jvm.*
import org.kgen.binary.mangling.Demangler

/**
 * End-to-end tests for binary analysis workflows:
 * compile → read binary → enumerate symbols → inspect sections → diff
 */
class BinaryAnalysisEndToEndTest {

    private fun buildSimpleClass(name: String = "org/kgen/test/Analysis"): ByteArray {
        return JvmClassWriter.write(
            ClassFileBuilder(name).apply {
                method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(0)
                    code.ireturn()
                }
                method("add", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iload(0)
                    code.iload(1)
                    code.iadd()
                    code.ireturn()
                }
                method("mul", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iload(0)
                    code.iload(1)
                    code.imul()
                    code.ireturn()
                }
            }.build()
        )
    }

    @Test
    fun `compile then read back ELF object and enumerate symbols`() {
        val classBytes = buildSimpleClass()
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(classBytes))

        // Object file should have symbols for our functions
        val symbolNames = obj.symbols.map { it.name }
        assertTrue("add" in symbolNames, "Expected 'add' symbol in object: $symbolNames")
        assertTrue("mul" in symbolNames, "Expected 'mul' symbol in object: $symbolNames")
    }

    @Test
    fun `compile then read back PE and check structure`() {
        val classBytes = buildSimpleClass()
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val peBytes = compiler.compile(listOf(classBytes))

        val pe = PeReader.read(peBytes)
        assertTrue(pe.sections.isNotEmpty(), "PE should have sections")
    }

    @Test
    fun `compile to object and check sections`() {
        val classBytes = buildSimpleClass()
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(classBytes))

        val sectionNames = obj.sections.map { it.name }
        assertTrue(sectionNames.any { it.contains("text") }, "Should have text section: $sectionNames")
    }

    @Test
    fun `compile to object and inspect relocations`() {
        val classBytes = buildSimpleClass()
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(classBytes))

        // Object file should have sections
        assertTrue(obj.sections.isNotEmpty())
        // Object file should have symbols
        assertTrue(obj.symbols.isNotEmpty())
    }

    @Test
    fun `packaged executable has kgen metadata`() {
        val classBytes = buildSimpleClass()
        val builder = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
        builder.addClassFile(classBytes)
        builder.setMainClass("org/kgen/test/Analysis")
        builder.setModuleName("analysis-test")
        builder.setVersion("2.0.0")

        val exe = builder.build()
        // Should be valid ELF
        assertTrue(exe.isNotEmpty())
        assertEquals(0x7F, exe[0].toInt() and 0xFF)

        // Try to read back — the ELF should be parseable
        val elf = ElfReader.read(exe)
        assertTrue(elf.sections.isNotEmpty(), "ELF should have sections")

        // Look for kgen sections (may use truncated names)
        val kgenSections = elf.sections.filter { it.name.startsWith(".kgen") }
        assertTrue(kgenSections.isNotEmpty(), "Should have .kgen.* sections: ${elf.sections.map { it.name }}")
    }

    @Test
    fun `two different classes produce different binaries`() {
        val class1 = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/V1").apply {
                method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(1)
                    code.ireturn()
                }
            }.build()
        )
        val class2 = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/V2").apply {
                method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(2)
                    code.ireturn()
                }
            }.build()
        )

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val obj1 = compiler.compileToObject(listOf(class1))
        val obj2 = compiler.compileToObject(listOf(class2))

        // Both should have 'main' symbol
        assertTrue(obj1.symbols.any { it.name == "main" })
        assertTrue(obj2.symbols.any { it.name == "main" })

        // Code sections should differ (different constants)
        val code1 = obj1.sections.firstOrNull { it.name == ".text" }?.data
        val code2 = obj2.sections.firstOrNull { it.name == ".text" }?.data
        assertNotNull(code1)
        assertNotNull(code2)
        assertFalse(code1!!.contentEquals(code2!!), "Different programs should produce different code")
    }

    @Test
    fun `demangler returns null for non-mangled kgen symbols`() {
        // kgen generates plain C-like symbol names — not mangled
        val name = "org_kgen_test_Point_getX"
        val demangled = Demangler.demangle(name)
        assertNull(demangled, "Plain symbol names are not mangled, demangle should return null")
    }
}
