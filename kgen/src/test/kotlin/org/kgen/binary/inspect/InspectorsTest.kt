package org.kgen.binary.inspect

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator

class InspectorsTest {

    private fun buildSimpleModule(): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.createFunction("noop", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildMainModule(): Module {
        val ir = ModuleBuilder("main", Target.x86_64())
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

    private fun generateElf(): ByteArray {
        return X86CodeGenerator().generate(buildSimpleModule(), CodeGenOptions(outputFormat = OutputFormat.OBJECT))
    }

    private fun generatePe(): ByteArray {
        val module = buildMainModule().copy(targetTriple = "x86_64-unknown-windows-msvc")
        return X86CodeGenerator().generate(module, CodeGenOptions(outputFormat = OutputFormat.BINARY))
    }

    @Nested
    inner class FormatDetection {
        @Test
        fun detectsElf() {
            val inspector = Inspectors.forBytes(generateElf())
            assertTrue(inspector is ElfInspector)
        }

        @Test
        fun detectsPe() {
            val inspector = Inspectors.forBytes(generatePe())
            assertTrue(inspector is PeInspector)
        }

        @Test
        fun rejectsZeroBytes() {
            assertThrows(IllegalArgumentException::class.java) {
                Inspectors.forBytes(byteArrayOf(0, 0, 0, 0))
            }
        }

        @Test
        fun rejectsRandomBytes() {
            assertThrows(IllegalArgumentException::class.java) {
                Inspectors.forBytes(byteArrayOf(0x12, 0x34, 0x56, 0x78))
            }
        }

        @Test
        fun rejectsEmptyArray() {
            assertThrows(Exception::class.java) {
                Inspectors.forBytes(byteArrayOf())
            }
        }

        @Test
        fun elfMagicDetected() {
            val elfMagic = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
            val elf = generateElf()
            assertEquals(elfMagic[0], elf[0])
            assertEquals(elfMagic[1], elf[1])
            assertEquals(elfMagic[2], elf[2])
            assertEquals(elfMagic[3], elf[3])
        }

        @Test
        fun peMagicDetected() {
            val pe = generatePe()
            assertEquals(0x4D.toByte(), pe[0])  // 'M'
            assertEquals(0x5A.toByte(), pe[1])  // 'Z'
        }
    }

    @Nested
    inner class ElfInspectorSections {
        @Test
        fun noDebugInfoForSimpleRelocatable() {
            val elf = generateElf()
            val inspector = ElfInspector()
            val debug = inspector.debugInfo(elf)
            assertNull(debug)
        }

        @Test
        fun lineInfoIsEmpty() {
            val elf = generateElf()
            val inspector = ElfInspector()
            val lines = inspector.lineInfo(elf)
            assertTrue(lines.isEmpty())
        }

        @Test
        fun dynamicSymbolsEmptyForRelocatable() {
            val elf = generateElf()
            val inspector = ElfInspector()
            val dynSyms = inspector.dynamicSymbols(elf)
            assertTrue(dynSyms.isEmpty())
        }

        @Test
        fun exportsEmptyForRelocatable() {
            val elf = generateElf()
            val inspector = ElfInspector()
            val exports = inspector.exports(elf)
            assertTrue(exports.isEmpty())
        }

        @Test
        fun dependenciesEmptyForRelocatable() {
            val elf = generateElf()
            val inspector = ElfInspector()
            val deps = inspector.dependencies(elf)
            assertTrue(deps.isEmpty())
        }

        @Test
        fun dynamicEntriesEmptyForRelocatable() {
            val elf = generateElf()
            val inspector = ElfInspector()
            val entries = inspector.dynamicEntries(elf)
            assertTrue(entries.isEmpty())
        }

        @Test
        fun importsEmptyForRelocatable() {
            val elf = generateElf()
            val inspector = ElfInspector()
            val imports = inspector.imports(elf)
            assertTrue(imports.isEmpty())
        }

        @Test
        fun sectionsExcludeNullSection() {
            val elf = generateElf()
            val inspector = ElfInspector()
            val sections = inspector.sections(elf)
            assertFalse(sections.any { it.index == 0 && it.name.isEmpty() })
        }

        @Test
        fun sectionFlagsContainAllocForText() {
            val elf = generateElf()
            val inspector = ElfInspector()
            val text = inspector.sections(elf).firstOrNull { it.name == ".text" }
            assertNotNull(text)
            assertTrue(text!!.flags.contains(SectionFlag.ALLOC))
        }

        @Test
        fun sectionFlagsContainExecForText() {
            val elf = generateElf()
            val inspector = ElfInspector()
            val text = inspector.sections(elf).first { it.name == ".text" }
            assertTrue(text.flags.contains(SectionFlag.EXEC))
        }
    }

    @Nested
    inner class PeInspectorEdgeCases {
        @Test
        fun dynamicSymbolsAlwaysEmpty() {
            val pe = generatePe()
            val inspector = PeInspector()
            assertTrue(inspector.dynamicSymbols(pe).isEmpty())
        }

        @Test
        fun debugInfoIsNull() {
            val pe = generatePe()
            val inspector = PeInspector()
            assertNull(inspector.debugInfo(pe))
        }

        @Test
        fun lineInfoIsEmpty() {
            val pe = generatePe()
            val inspector = PeInspector()
            assertTrue(inspector.lineInfo(pe).isEmpty())
        }

        @Test
        fun dynamicEntriesAlwaysEmpty() {
            val pe = generatePe()
            val inspector = PeInspector()
            assertTrue(inspector.dynamicEntries(pe).isEmpty())
        }

        @Test
        fun sectionDataForMissingSectionReturnsNull() {
            val pe = generatePe()
            val inspector = PeInspector()
            assertNull(inspector.sectionData(pe, ".nonexistent"))
        }

        @Test
        fun sectionDataForTextNotNull() {
            val pe = generatePe()
            val inspector = PeInspector()
            val data = inspector.sectionData(pe, ".text")
            assertNotNull(data)
            assertTrue(data!!.isNotEmpty())
        }

        @Test
        fun symbolsListNotNull() {
            val pe = generatePe()
            val inspector = PeInspector()
            val syms = inspector.symbols(pe)
            assertNotNull(syms)
        }

        @Test
        fun headersHasProperties() {
            val pe = generatePe()
            val inspector = PeInspector()
            val h = inspector.headers(pe)
            assertTrue(h.properties.containsKey("machine"))
            assertTrue(h.properties.containsKey("sections"))
            assertTrue(h.properties.containsKey("imageBase"))
        }

        @Test
        fun osIsWindows() {
            val pe = generatePe()
            val inspector = PeInspector()
            val h = inspector.headers(pe)
            assertEquals("windows", h.arch.os)
        }
    }
}
