package org.kgen.integration.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.binary.ObjectFormat
import org.kgen.binary.elf.ElfObjectWriter
import org.kgen.binary.elf.ElfReader
import org.kgen.binary.elf.ElfStaticLinker
import org.kgen.binary.inspect.ElfInspector
import org.kgen.binary.inspect.Inspectors
import org.kgen.binary.mangling.ItaniumDemangler
import org.kgen.binary.mangling.UniversalDemangler
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.x86.disasm.X86Disassembler

/**
 * Binary analysis pipeline: build binary → inspect → demangle → disassemble → diff.
 * Tests kgen as a binary analysis toolkit (like pyelftools/lief but on JVM).
 */
class BinaryAnalysisPipelineTest {

    // -- Inspect generated ELF --

    @Test
    fun inspectElfObjectFile() {
        val module = buildModule()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val elfBytes = ElfObjectWriter().write(obj)

        val inspector = Inspectors.forBytes(elfBytes)
        assertTrue(inspector is ElfInspector)

        val headers = inspector.headers(elfBytes)
        assertEquals(ObjectFormat.ELF, headers.format)

        val sections = inspector.sections(elfBytes)
        assertTrue(sections.isNotEmpty())
        assertTrue(sections.any { it.name == ".text" }, "Should have .text: ${sections.map { it.name }}")

        val symbols = inspector.symbols(elfBytes)
        assertTrue(symbols.isNotEmpty())
        assertTrue(symbols.any { it.name == "compute" }, "Should have 'compute': ${symbols.map { it.name }}")
    }

    @Test
    fun inspectElfSectionData() {
        val module = buildModule()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val elfBytes = ElfObjectWriter().write(obj)
        val inspector = Inspectors.forBytes(elfBytes)

        val textData = inspector.sectionData(elfBytes, ".text")
        assertNotNull(textData)
        assertTrue(textData!!.isNotEmpty())

        // Disassemble the extracted section data
        val insts = X86Disassembler().disassembleRaw(textData)
        assertTrue(insts.isNotEmpty())
        assertEquals(textData.size, insts.sumOf { it.size }, "All bytes should decode")
    }

    @Test
    fun inspectRelocations() {
        val module = buildModuleWithExternalCall()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val elfBytes = ElfObjectWriter().write(obj)
        val inspector = Inspectors.forBytes(elfBytes)

        val relocs = inspector.relocations(elfBytes)
        assertTrue(relocs.isNotEmpty(), "Module with external call should have relocations")
    }

    // -- Inspect and project --

    @Test
    fun inspectThenProjectToObjectFile() {
        val module = buildModule()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val elfBytes = ElfObjectWriter().write(obj)
        val inspector = Inspectors.forBytes(elfBytes)

        val projected = inspector.inspect(elfBytes)
        assertEquals(ObjectFormat.ELF, projected.format)
        assertTrue(projected.sections.any { it.kind == SectionKind.TEXT })
        assertTrue(projected.symbols.any { it.name == "compute" })
    }

    // -- Demangling --

    @Test
    fun demangleItaniumSymbols() {
        val demangler = ItaniumDemangler()

        assertTrue(demangler.canDemangle("_Z7computei"))
        val result = demangler.demangle("_Z7computei")
        assertNotNull(result)
        assertTrue(result!!.contains("compute"), "Should contain 'compute': $result")
    }

    @Test
    fun universalDemanglerAutoDetects() {
        val demangler = UniversalDemangler()

        // Itanium-style
        assertTrue(demangler.canDemangle("_Z3foov"))
        val itanium = demangler.demangle("_Z3foov")
        assertNotNull(itanium)
        assertTrue(itanium!!.contains("foo"))

        // Plain symbol (not mangled) — should return null or the symbol itself
        assertFalse(demangler.canDemangle("plain_symbol"))
    }

    // -- Full pipeline: build → write → read → inspect → disassemble --

    @Test
    fun fullAnalysisPipeline() {
        // 1. Build IR
        val module = buildModule()

        // 2. Compile to x86
        val obj = X86CodeGenerator().generateObjectFile(module)

        // 3. Write to ELF
        val elfBytes = ElfObjectWriter().write(obj)

        // 4. Read back
        val elf = ElfReader.read(elfBytes)
        val roundTripped = ElfReader.toObjectFile(elf)

        // 5. Inspect
        val inspector = Inspectors.forBytes(elfBytes)
        val headers = inspector.headers(elfBytes)
        val symbols = inspector.symbols(elfBytes)
        val sections = inspector.sections(elfBytes)

        // 6. Disassemble code section
        val textData = inspector.sectionData(elfBytes, ".text")!!
        val insts = X86Disassembler().disassembleRaw(textData)

        // Verify the pipeline produced consistent results
        assertEquals(ObjectFormat.ELF, headers.format)
        assertTrue(symbols.any { it.name == "compute" })
        assertTrue(sections.any { it.name == ".text" })
        assertTrue(insts.isNotEmpty())
        assertEquals(textData.size, insts.sumOf { it.size })

        // Verify round-trip preserves symbols
        val rtSymNames = roundTripped.symbols.map { it.name }
        assertTrue("compute" in rtSymNames)
    }

    @Test
    fun linkedExecutableAnalysis() {
        val module = buildModule()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val startObj = obj.copy(
            symbols = obj.symbols.map { s ->
                if (s.name == "compute") s.copy(name = "_start") else s
            }
        )

        val exeBytes = ElfStaticLinker().link(listOf(startObj))

        // Verify it's a valid ELF
        assertTrue(ElfReader.canRead(exeBytes))
        val inspector = Inspectors.forBytes(exeBytes)
        val headers = inspector.headers(exeBytes)
        assertEquals(ObjectFormat.ELF, headers.format)
        assertNotNull(headers.entryPoint)
        assertTrue(headers.entryPoint!! > 0, "Entry point should be set")
    }

    @Test
    fun stringExtraction() {
        val ir = ModuleBuilder("str_test", Target.x86_64())
        val strType = Type.Array(Type.I8, 14)
        ir.addGlobal("msg", strType, Constant.StringConst("Hello, World!"),
            isConstant = true, linkage = Linkage.INTERNAL)
        ir.createFunction("_start", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val elfBytes = ElfObjectWriter().write(obj)
        val inspector = ElfInspector()

        val strings = inspector.strings(elfBytes, minLength = 5)
        assertTrue(strings.any { it.value.contains("Hello") },
            "Should find 'Hello' string: ${strings.map { it.value }}")
    }

    // -- Helpers --

    private fun buildModule(): Module {
        val ir = ModuleBuilder("analysis_test", Target.x86_64())
        val p = ir.createFunction("compute", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val doubled = ir.add(p[0], p[0])
        val result = ir.add(doubled, Constant.I64(1))
        ir.ret(result)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildModuleWithExternalCall(): Module {
        val ir = ModuleBuilder("reloc_test", Target.x86_64())
        ir.declareFunction("external_fn", listOf(Param("x", Type.I64)), Type.I64)
        ir.createFunction("caller", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val result = ir.call("external_fn", listOf(Parameter("x", Type.I64, 0)), Type.I64)
        ir.ret(result)
        ir.finalizeFunction()
        return ir.build()
    }
}
