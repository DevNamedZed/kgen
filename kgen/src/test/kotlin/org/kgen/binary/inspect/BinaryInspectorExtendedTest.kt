package org.kgen.binary.inspect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.binary.elf.ElfReader
import org.kgen.binary.pe.PeReader
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator

class BinaryInspectorExtendedTest {

    private fun buildAddModule(): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildHelloModule(): Module {
        val ir = ModuleBuilder("hello", Target.x86_64())
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

    private fun buildTwoFuncModule(): Module {
        val ir = ModuleBuilder("twofunc", Target.x86_64())

        ir.createFunction("first", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(1))
        ir.finalizeFunction()

        ir.createFunction("second", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(2))
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

    // -- Inspectors auto-detection --

    @Test
    fun autoDetectsElfFormat() {
        val elf = generateElf(buildAddModule())
        assertTrue(Inspectors.forBytes(elf) is ElfInspector)
    }

    @Test
    fun autoDetectsPeFormat() {
        val pe = generatePe(buildHelloModule())
        assertTrue(Inspectors.forBytes(pe) is PeInspector)
    }

    @Test
    fun rejectsUnknownFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            Inspectors.forBytes(byteArrayOf(0, 0, 0, 0))
        }
    }

    @Test
    fun rejectsEmptyBytes() {
        assertThrows(Exception::class.java) {
            Inspectors.forBytes(byteArrayOf())
        }
    }

    // -- ELF Inspector headers --

    @Test
    fun elfHeaderFormatIsElf() {
        val elf = generateElf(buildAddModule())
        val h = ElfInspector().headers(elf)
        assertEquals(ObjectFormat.ELF, h.format)
    }

    @Test
    fun elfHeaderArchIsX8664() {
        val elf = generateElf(buildAddModule())
        val h = ElfInspector().headers(elf)
        assertEquals(ArchType.X86_64, h.arch.arch)
    }

    @Test
    fun elfHeaderTypeRelocatable() {
        val elf = generateElf(buildAddModule())
        val h = ElfInspector().headers(elf)
        assertEquals("relocatable", h.type)
    }

    @Test
    fun elfRelocatableNoEntryPoint() {
        val elf = generateElf(buildAddModule())
        val h = ElfInspector().headers(elf)
        assertNull(h.entryPoint)
    }

    @Test
    fun elfHeaderHasProperties() {
        val elf = generateElf(buildAddModule())
        val h = ElfInspector().headers(elf)
        assertTrue(h.properties.containsKey("class"))
        assertTrue(h.properties.containsKey("encoding"))
    }

    // -- ELF Inspector sections --

    @Test
    fun elfSectionsContainText() {
        val elf = generateElf(buildAddModule())
        val sections = ElfInspector().sections(elf)
        assertTrue(sections.any { it.name == ".text" })
    }

    @Test
    fun elfTextSectionIsText() {
        val elf = generateElf(buildAddModule())
        val text = ElfInspector().sections(elf).first { it.name == ".text" }
        assertEquals(SectionKind.TEXT, text.kind)
    }

    @Test
    fun elfTextSectionSizePositive() {
        val elf = generateElf(buildAddModule())
        val text = ElfInspector().sections(elf).first { it.name == ".text" }
        assertTrue(text.size > 0)
    }

    @Test
    fun elfSectionDataNotNull() {
        val elf = generateElf(buildAddModule())
        val data = ElfInspector().sectionData(elf, ".text")
        assertNotNull(data)
        assertTrue(data!!.isNotEmpty())
    }

    @Test
    fun elfSectionDataMissing() {
        val elf = generateElf(buildAddModule())
        val data = ElfInspector().sectionData(elf, ".nonexistent")
        assertNull(data)
    }

    // -- ELF Inspector symbols --

    @Test
    fun elfSymbolsContainAdd() {
        val elf = generateElf(buildAddModule())
        val syms = ElfInspector().symbols(elf)
        assertTrue(syms.any { it.name == "add" })
    }

    @Test
    fun elfAddSymbolIsFunction() {
        val elf = generateElf(buildAddModule())
        val add = ElfInspector().symbols(elf).first { it.name == "add" }
        assertEquals(SymbolKind.FUNCTION, add.kind)
    }

    @Test
    fun elfAddSymbolIsGlobal() {
        val elf = generateElf(buildAddModule())
        val add = ElfInspector().symbols(elf).first { it.name == "add" }
        assertEquals(SymbolBinding.GLOBAL, add.binding)
    }

    @Test
    fun elfTwoFuncBothSymbolsPresent() {
        val elf = generateElf(buildTwoFuncModule())
        val syms = ElfInspector().symbols(elf)
        assertTrue(syms.any { it.name == "first" })
        assertTrue(syms.any { it.name == "second" })
    }

    // -- ELF Inspector relocations --

    @Test
    fun elfRelocationsNonEmpty() {
        val elf = generateElf(buildHelloModule())
        val relocs = ElfInspector().relocations(elf)
        assertTrue(relocs.isNotEmpty())
    }

    @Test
    fun elfRelocationHasSymbol() {
        val elf = generateElf(buildHelloModule())
        val relocs = ElfInspector().relocations(elf)
        assertTrue(relocs.any { it.symbol.isNotEmpty() })
    }

    // -- ELF Inspector inspect --

    @Test
    fun elfInspectReturnsObjectFile() {
        val elf = generateElf(buildAddModule())
        val obj = ElfInspector().inspect(elf)
        assertEquals(ObjectFormat.ELF, obj.format)
    }

    @Test
    fun elfInspectHasTextSection() {
        val elf = generateElf(buildAddModule())
        val obj = ElfInspector().inspect(elf)
        assertTrue(obj.sections.any { it.name == ".text" })
    }

    // -- ELF Inspector strings --

    @Test
    fun elfStringsContainHello() {
        val elf = generateElf(buildHelloModule())
        val strings = ElfInspector().strings(elf, 4)
        assertTrue(strings.any { it.value.contains("Hello") })
    }

    @Test
    fun elfStringsHaveSection() {
        val elf = generateElf(buildHelloModule())
        val strings = ElfInspector().strings(elf, 4)
        for (s in strings) {
            assertNotNull(s.section)
        }
    }

    // -- PE Inspector headers --

    @Test
    fun peHeaderFormatIsPeCoff() {
        val pe = generatePe(buildHelloModule())
        val h = PeInspector().headers(pe)
        assertEquals(ObjectFormat.PE_COFF, h.format)
    }

    @Test
    fun peHeaderArchIsX8664() {
        val pe = generatePe(buildHelloModule())
        val h = PeInspector().headers(pe)
        assertEquals(ArchType.X86_64, h.arch.arch)
    }

    @Test
    fun peHeaderTypeExecutable() {
        val pe = generatePe(buildHelloModule())
        val h = PeInspector().headers(pe)
        assertEquals("executable", h.type)
    }

    @Test
    fun peHeaderHasEntryPoint() {
        val pe = generatePe(buildHelloModule())
        val h = PeInspector().headers(pe)
        assertNotNull(h.entryPoint)
    }

    @Test
    fun peHeaderFlagsPe32Plus() {
        val pe = generatePe(buildHelloModule())
        val h = PeInspector().headers(pe)
        assertTrue(h.flags.contains("PE32+"))
    }

    // -- PE Inspector sections --

    @Test
    fun peSectionsContainText() {
        val pe = generatePe(buildHelloModule())
        val sections = PeInspector().sections(pe)
        assertTrue(sections.any { it.name == ".text" })
    }

    @Test
    fun peTextSectionIsExecutable() {
        val pe = generatePe(buildHelloModule())
        val text = PeInspector().sections(pe).first { it.name == ".text" }
        assertTrue(text.flags.contains(SectionFlag.EXEC))
    }

    // -- PE Inspector imports --

    @Test
    fun peImportsContainPuts() {
        val pe = generatePe(buildHelloModule())
        val imports = PeInspector().imports(pe)
        assertTrue(imports.any { it.name == "puts" })
    }

    @Test
    fun peImportsFromUcrtbase() {
        val pe = generatePe(buildHelloModule())
        val imports = PeInspector().imports(pe)
        assertTrue(imports.any { it.module == "ucrtbase.dll" })
    }

    // -- PE Inspector dependencies --

    @Test
    fun peDependenciesContainUcrtbase() {
        val pe = generatePe(buildHelloModule())
        val deps = PeInspector().dependencies(pe)
        assertTrue(deps.contains("ucrtbase.dll"))
    }

    // -- PE Inspector inspect --

    @Test
    fun peInspectReturnsObjectFile() {
        val pe = generatePe(buildHelloModule())
        val obj = PeInspector().inspect(pe)
        assertEquals(ObjectFormat.PE_COFF, obj.format)
    }

    // -- PE Inspector strings --

    @Test
    fun peStringsContainHello() {
        val pe = generatePe(buildHelloModule())
        val strings = PeInspector().strings(pe, 4)
        assertTrue(strings.any { it.value.contains("Hello") })
    }

    @Test
    fun peStringsHaveOffsets() {
        val pe = generatePe(buildHelloModule())
        val strings = PeInspector().strings(pe, 4)
        for (s in strings) {
            assertTrue(s.offset >= 0)
        }
    }
}
