package org.kgen.binary.inspect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.binary.elf.ElfReader
import org.kgen.binary.elf.ElfWriter
import org.kgen.binary.pe.PeReader
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator

class BinaryInspectorTest {

    private fun buildAddModule(): Module {
        val ir = IrBuilder("test", Target.x86_64())
        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildHelloModule(): Module {
        val ir = IrBuilder("hello", Target.x86_64())
        val strType = Type.Array(Type.I8, 14)
        val strRef = ir.addGlobal("hello_str", strType,
            Constant.StringConst("Hello, World!"), isConstant = true,
            linkage = Linkage.INTERNAL)
        ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)
        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.call("puts", listOf(strRef), Type.I32)
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun generateElf(module: Module): ByteArray {
        return X86CodeGenerator().generate(module, CodeGenOptions(outputFormat = OutputFormat.OBJECT))
    }

    private fun generatePe(module: Module): ByteArray {
        val winModule = module.copy(targetTriple = "x86_64-unknown-windows-msvc")
        return X86CodeGenerator().generate(winModule, CodeGenOptions(outputFormat = OutputFormat.BINARY))
    }

    // --- Inspectors auto-detection ---

    @Test
    fun `auto-detects ELF format`() {
        val elf = generateElf(buildAddModule())
        val inspector = Inspectors.forBytes(elf)
        assertTrue(inspector is ElfInspector)
    }

    @Test
    fun `auto-detects PE format`() {
        val pe = generatePe(buildHelloModule())
        val inspector = Inspectors.forBytes(pe)
        assertTrue(inspector is PeInspector)
    }

    @Test
    fun `rejects unknown format`() {
        assertThrows(IllegalArgumentException::class.java) {
            Inspectors.forBytes(byteArrayOf(0, 0, 0, 0))
        }
    }

    // --- ELF Inspector ---

    @Test
    fun `ELF headers`() {
        val elf = generateElf(buildAddModule())
        val inspector = ElfInspector()
        val h = inspector.headers(elf)
        assertEquals(ObjectFormat.ELF, h.format)
        assertEquals(ArchType.X86_64, h.arch.arch)
        assertEquals("relocatable", h.type)
        assertNull(h.entryPoint)
    }

    @Test
    fun `ELF sections`() {
        val elf = generateElf(buildAddModule())
        val inspector = ElfInspector()
        val sections = inspector.sections(elf)
        assertTrue(sections.any { it.name == ".text" })
        val text = sections.first { it.name == ".text" }
        assertEquals(SectionKind.TEXT, text.kind)
        assertTrue(text.size > 0)
    }

    @Test
    fun `ELF section data`() {
        val elf = generateElf(buildAddModule())
        val inspector = ElfInspector()
        val data = inspector.sectionData(elf, ".text")
        assertNotNull(data)
        assertTrue(data!!.isNotEmpty())
    }

    @Test
    fun `ELF symbols`() {
        val elf = generateElf(buildAddModule())
        val inspector = ElfInspector()
        val syms = inspector.symbols(elf)
        assertTrue(syms.any { it.name == "add" }, "Expected 'add' symbol: ${syms.map { it.name }}")
        val add = syms.first { it.name == "add" }
        assertEquals(SymbolBinding.GLOBAL, add.binding)
        assertEquals(SymbolKind.FUNCTION, add.kind)
    }

    @Test
    fun `ELF relocations`() {
        val elf = generateElf(buildHelloModule())
        val inspector = ElfInspector()
        val relocs = inspector.relocations(elf)
        assertTrue(relocs.isNotEmpty(), "Expected relocations for hello world")
    }

    @Test
    fun `ELF inspect returns ObjectFile`() {
        val elf = generateElf(buildAddModule())
        val inspector = ElfInspector()
        val obj = inspector.inspect(elf)
        assertEquals(ObjectFormat.ELF, obj.format)
        assertTrue(obj.sections.any { it.name == ".text" })
    }

    @Test
    fun `ELF strings`() {
        val elf = generateElf(buildHelloModule())
        val inspector = ElfInspector()
        val strings = inspector.strings(elf, 4)
        assertTrue(strings.any { it.value.contains("Hello") },
            "Expected 'Hello' in strings: ${strings.map { it.value }}")
    }

    // --- PE Inspector ---

    @Test
    fun `PE headers`() {
        val pe = generatePe(buildHelloModule())
        val inspector = PeInspector()
        val h = inspector.headers(pe)
        assertEquals(ObjectFormat.PE_COFF, h.format)
        assertEquals(ArchType.X86_64, h.arch.arch)
        assertEquals("executable", h.type)
        assertNotNull(h.entryPoint)
        assertTrue(h.flags.contains("PE32+"))
    }

    @Test
    fun `PE sections`() {
        val pe = generatePe(buildHelloModule())
        val inspector = PeInspector()
        val sections = inspector.sections(pe)
        assertTrue(sections.any { it.name == ".text" })
    }

    @Test
    fun `PE imports`() {
        val pe = generatePe(buildHelloModule())
        val inspector = PeInspector()
        val imports = inspector.imports(pe)
        assertTrue(imports.any { it.name == "puts" },
            "Expected 'puts' import: ${imports.map { "${it.module}:${it.name}" }}")
        assertTrue(imports.any { it.module == "ucrtbase.dll" })
    }

    @Test
    fun `PE dependencies`() {
        val pe = generatePe(buildHelloModule())
        val inspector = PeInspector()
        val deps = inspector.dependencies(pe)
        assertTrue(deps.contains("ucrtbase.dll"), "Expected ucrtbase.dll dependency: $deps")
    }

    @Test
    fun `PE inspect returns ObjectFile`() {
        val pe = generatePe(buildHelloModule())
        val inspector = PeInspector()
        val obj = inspector.inspect(pe)
        assertEquals(ObjectFormat.PE_COFF, obj.format)
    }

    @Test
    fun `PE strings`() {
        val pe = generatePe(buildHelloModule())
        val inspector = PeInspector()
        val strings = inspector.strings(pe, 4)
        assertTrue(strings.any { it.value.contains("Hello") },
            "Expected 'Hello' in strings: ${strings.map { it.value }}")
    }

    @Test
    fun `PE section flags`() {
        val pe = generatePe(buildHelloModule())
        val inspector = PeInspector()
        val sections = inspector.sections(pe)
        val text = sections.first { it.name == ".text" }
        assertTrue(text.flags.contains(SectionFlag.EXEC))
    }
}
