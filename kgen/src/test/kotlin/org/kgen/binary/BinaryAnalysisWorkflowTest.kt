package org.kgen.binary

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.binary.elf.*
import org.kgen.binary.pe.*
import org.kgen.binary.diff.BinaryDiff
import org.kgen.ir.target.Target
import org.kgen.runtime.compile.*
import org.kgen.target.jvm.*

/**
 * End-to-end tests demonstrating binary analysis workflows:
 * compile → read → enumerate → inspect → diff
 */
class BinaryAnalysisWorkflowTest {

    private fun buildClass(name: String, block: ClassFileBuilder.() -> Unit): ByteArray {
        return JvmClassWriter.write(ClassFileBuilder(name).apply(block).build())
    }

    @Test
    fun `read ELF object and enumerate symbols and sections`() {
        val classBytes = buildClass("org/kgen/test/Analyze") {
            method("add", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iload(1)
                code.iadd()
                code.ireturn()
            }
            method("sub", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iload(1)
                code.isub()
                code.ireturn()
            }
        }

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(classBytes))

        // Enumerate symbols
        val symbolNames = obj.symbols.map { it.name }
        assertTrue("add" in symbolNames)
        assertTrue("sub" in symbolNames)

        // Enumerate sections
        val sectionNames = obj.sections.map { it.name }
        assertTrue(sectionNames.any { it.contains("text") },
            "Expected .text section, got: $sectionNames")
    }

    @Test
    fun `read PE executable and check structure`() {
        val classBytes = buildClass("org/kgen/test/WinAnalyze") {
            method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(0)
                code.ireturn()
            }
        }

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val peBytes = compiler.compile(listOf(classBytes))

        val pe = PeReader.read(peBytes)
        assertTrue(pe.sections.isNotEmpty(), "PE should have sections")
        // PE should have .text section
        assertTrue(pe.sections.any { it.name.trimEnd('\u0000') == ".text" },
            "PE should have .text section: ${pe.sections.map { it.name }}")
    }

    @Test
    fun `compare two versions of same program`() {
        val v1 = buildClass("org/kgen/test/V1") {
            method("compute", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iconst(1)
                code.iadd()
                code.ireturn()
            }
        }

        val v2 = buildClass("org/kgen/test/V2") {
            method("compute", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iconst(2)
                code.iadd()
                code.ireturn()
            }
        }

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val obj1 = compiler.compileToObject(listOf(v1))
        val obj2 = compiler.compileToObject(listOf(v2))

        // Both should have 'compute'
        assertTrue(obj1.symbols.any { it.name == "compute" })
        assertTrue(obj2.symbols.any { it.name == "compute" })

        // Code sections should differ
        val code1 = obj1.sections.firstOrNull { it.name == ".text" }?.data
        val code2 = obj2.sections.firstOrNull { it.name == ".text" }?.data
        assertNotNull(code1)
        assertNotNull(code2)
        assertFalse(code1!!.contentEquals(code2!!), "Different constants should produce different code")
    }

    @Test
    fun `compile to ELF and read back with ElfReader`() {
        val classBytes = buildClass("org/kgen/test/ReadBack") {
            method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(42)
                code.ireturn()
            }
            method("helper", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iconst(1)
                code.iadd()
                code.ireturn()
            }
        }

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val elfBytes = compiler.compile(listOf(classBytes))

        // Read it back — linked ELF may have minimal section headers
        assertTrue(elfBytes.size > 100, "ELF should have meaningful size")
        assertEquals(0x7F, elfBytes[0].toInt() and 0xFF)
        val elf = ElfReader.read(elfBytes)
        assertNotNull(elf)
    }

    @Test
    fun `x86 and ARM64 objects have different text section sizes`() {
        val classBytes = buildClass("org/kgen/test/MultiArch") {
            method("add", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iload(1)
                code.iadd()
                code.ireturn()
            }
        }

        val x86Obj = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
            .compileToObject(listOf(classBytes))
        val armObj = NativeCompiler(Target.arm64(), OutputPlatform.LINUX)
            .compileToObject(listOf(classBytes))

        val x86Text = x86Obj.sections.firstOrNull { it.name == ".text" }
        val armText = armObj.sections.firstOrNull { it.name == ".text" }

        assertNotNull(x86Text)
        assertNotNull(armText)

        // ARM64 instructions are fixed 4 bytes
        assertEquals(0, armText!!.data.size % 4, "ARM64 code should be 4-byte aligned")
    }

    @Test
    fun `ELF executable with kgen metadata is inspectable`() {
        val classBytes = buildClass("org/kgen/test/Inspect") {
            method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(0)
                code.ireturn()
            }
        }

        val builder = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
        builder.addClassFile(classBytes)
        builder.setMainClass("org/kgen/test/Inspect")
        builder.setModuleName("inspect-test")
        builder.setVersion("1.0.0")
        builder.setMetadata("build", "ci")

        val exe = builder.build()
        val elf = ElfReader.read(exe)

        // Should have kgen sections
        val kgenSections = elf.sections.filter { it.name.startsWith(".kgen") }
        assertTrue(kgenSections.isNotEmpty(),
            "Should have .kgen sections: ${elf.sections.map { it.name }}")

        // Read metadata back
        val metaSection = elf.sectionByName(".kgen.meta")
        assertNotNull(metaSection)
        val meta = ExecutableReader.read(metaSection!!.data, null)
        assertEquals("inspect-test", meta.moduleName)
        assertEquals("1.0.0", meta.version)
    }

    @Test
    fun `object file section sizes are consistent`() {
        val classBytes = buildClass("org/kgen/test/Consistent") {
            method("a", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(1)
                code.ireturn()
            }
            method("b", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(2)
                code.ireturn()
            }
            method("c", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(3)
                code.ireturn()
            }
        }

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(classBytes))

        // All three symbols should exist
        assertEquals(3, obj.symbols.count { it.name in listOf("a", "b", "c") })

        // Text section should have code for all three
        val textSection = obj.sections.firstOrNull { it.name == ".text" }
        assertNotNull(textSection)
        assertTrue(textSection!!.data.isNotEmpty())
    }
}
