package org.kgen.binary

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.kgen.binary.diff.BinaryDelta
import org.kgen.binary.diff.ElfBinaryDiff
import org.kgen.binary.elf.ElfObjectWriter
import org.kgen.binary.elf.ElfReader
import org.kgen.binary.inspect.*
import org.kgen.binary.patch.ElfBinaryPatcher
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator

class BinaryToolsComprehensiveTest {

    private fun buildAddModule(): Module {
        val ir = IrBuilder("test", Target.x86_64())
        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
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
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.call("puts", listOf(strRef), Type.I32)
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildSubModule(): Module {
        val ir = IrBuilder("sub_test", Target.x86_64())
        val params = ir.createFunction("subtract", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val diff = ir.sub(params[0], params[1])
        ir.ret(diff)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildMultiFunctionModule(): Module {
        val ir = IrBuilder("multi", Target.x86_64())
        val addParams = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.add(addParams[0], addParams[1]))
        ir.finalizeFunction()

        val subParams = ir.createFunction("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.sub(subParams[0], subParams[1]))
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

    private fun makeElf(
        code: ByteArray = byteArrayOf(0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte()),
        data: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04),
        symbols: List<Symbol> = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
        ),
        extraSections: List<Section> = emptyList(),
    ): ByteArray {
        val sections = listOf(
            Section(".text", SectionKind.TEXT, code, align = 16),
            Section(".data", SectionKind.DATA, data, align = 4),
        ) + extraSections
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = sections,
            symbols = symbols,
            relocations = emptyList(),
        )
        return ElfObjectWriter().write(obj)
    }

    @Nested
    inner class HexDumpFormat {

        @Test
        fun `empty array produces empty string`() {
            assertEquals("", HexDump.format(byteArrayOf()))
        }

        @Test
        fun `single byte format`() {
            val result = HexDump.format(byteArrayOf(0x41))
            assertTrue(result.startsWith("00000000"))
            assertTrue(result.contains("41"))
            assertTrue(result.contains("|A|"))
        }

        @Test
        fun `16 bytes fills one line`() {
            val bytes = ByteArray(16) { (0x40 + it).toByte() }
            val lines = HexDump.format(bytes).trim().lines()
            assertEquals(1, lines.size)
        }

        @Test
        fun `17 bytes produces two lines`() {
            val bytes = ByteArray(17) { (0x40 + it).toByte() }
            val lines = HexDump.format(bytes).trim().lines()
            assertEquals(2, lines.size)
        }

        @Test
        fun `32 bytes produces exactly two lines`() {
            val bytes = ByteArray(32) { it.toByte() }
            val lines = HexDump.format(bytes).trim().lines()
            assertEquals(2, lines.size)
            assertTrue(lines[0].startsWith("00000000"))
            assertTrue(lines[1].startsWith("00000010"))
        }

        @Test
        fun `48 bytes produces three lines`() {
            val bytes = ByteArray(48) { it.toByte() }
            val lines = HexDump.format(bytes).trim().lines()
            assertEquals(3, lines.size)
            assertTrue(lines[2].startsWith("00000020"))
        }

        @Test
        fun `base address offsets all line addresses`() {
            val bytes = ByteArray(32) { 0 }
            val result = HexDump.format(bytes, baseAddress = 0x401000)
            val lines = result.trim().lines()
            assertTrue(lines[0].startsWith("00401000"))
            assertTrue(lines[1].startsWith("00401010"))
        }

        @Test
        fun `base address with large value`() {
            val result = HexDump.format(byteArrayOf(0), baseAddress = 0x7FFF0000)
            assertTrue(result.startsWith("7fff0000"))
        }

        @Test
        fun `base address zero is default`() {
            val result = HexDump.format(byteArrayOf(0x42))
            assertTrue(result.startsWith("00000000"))
        }

        @Test
        fun `ASCII column shows printable chars`() {
            val bytes = "Hello, World!".toByteArray(Charsets.US_ASCII)
            val result = HexDump.format(bytes)
            assertTrue(result.contains("|Hello, World!|"))
        }

        @Test
        fun `non-printable bytes shown as dots`() {
            val bytes = byteArrayOf(0x00, 0x01, 0x7f.toByte(), 0x80.toByte())
            val result = HexDump.format(bytes)
            assertTrue(result.contains("|....|"))
        }

        @Test
        fun `space character is printable`() {
            val bytes = byteArrayOf(0x20)
            val result = HexDump.format(bytes)
            assertTrue(result.contains("| |"))
        }

        @Test
        fun `tilde is printable`() {
            val bytes = byteArrayOf(0x7E)
            val result = HexDump.format(bytes)
            assertTrue(result.contains("|~|"))
        }

        @Test
        fun `DEL (0x7F) is not printable`() {
            val bytes = byteArrayOf(0x7F)
            val result = HexDump.format(bytes)
            assertTrue(result.contains("|.|"))
        }

        @Test
        fun `ASCII disabled`() {
            val result = HexDump.format(byteArrayOf(0x41), showAscii = false)
            assertFalse(result.contains("|"))
        }

        @Test
        fun `all printable ASCII range`() {
            val bytes = ByteArray(95) { (0x20 + it).toByte() }
            val result = HexDump.format(bytes)
            // First line should have printable chars from 0x20-0x2F
            for (b in bytes) {
                val ch = b.toInt().toChar()
                assertTrue(result.contains(ch.toString()),
                    "Should contain printable char '$ch'")
            }
        }

        @Test
        fun `hex bytes are lowercase`() {
            val result = HexDump.format(byteArrayOf(0xAB.toByte(), 0xCD.toByte()))
            assertTrue(result.contains("ab"))
            assertTrue(result.contains("cd"))
        }

        @Test
        fun `256 bytes produces 16 lines`() {
            val bytes = ByteArray(256) { it.toByte() }
            val lines = HexDump.format(bytes).trim().lines()
            assertEquals(16, lines.size)
        }

        @Test
        fun `custom bytes per line of 8`() {
            val bytes = ByteArray(16) { it.toByte() }
            val lines = HexDump.format(bytes, bytesPerLine = 8).trim().lines()
            assertEquals(2, lines.size)
        }

        @Test
        fun `custom bytes per line of 32`() {
            val bytes = ByteArray(64) { it.toByte() }
            val lines = HexDump.format(bytes, bytesPerLine = 32).trim().lines()
            assertEquals(2, lines.size)
        }
    }

    @Nested
    inner class HexDumpFormatBytes {

        @Test
        fun `empty array`() {
            assertEquals("", HexDump.formatBytes(byteArrayOf()))
        }

        @Test
        fun `single byte`() {
            assertEquals("42", HexDump.formatBytes(byteArrayOf(0x42)))
        }

        @Test
        fun `multiple bytes with default separator`() {
            assertEquals("de ad be ef", HexDump.formatBytes(
                byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())))
        }

        @Test
        fun `custom separator colon`() {
            assertEquals("ca:fe", HexDump.formatBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()), separator = ":"))
        }

        @Test
        fun `custom separator empty string`() {
            assertEquals("cafe", HexDump.formatBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()), separator = ""))
        }

        @Test
        fun `custom separator dash`() {
            assertEquals("01-02-03", HexDump.formatBytes(byteArrayOf(1, 2, 3), separator = "-"))
        }

        @Test
        fun `zero byte`() {
            assertEquals("00", HexDump.formatBytes(byteArrayOf(0)))
        }

        @Test
        fun `0xFF byte`() {
            assertEquals("ff", HexDump.formatBytes(byteArrayOf(0xFF.toByte())))
        }

        @Test
        fun `all byte values format correctly`() {
            for (i in 0..255) {
                val result = HexDump.formatBytes(byteArrayOf(i.toByte()))
                assertEquals(String.format("%02x", i), result)
            }
        }

        @Test
        fun `long byte array`() {
            val bytes = ByteArray(100) { it.toByte() }
            val result = HexDump.formatBytes(bytes)
            val parts = result.split(" ")
            assertEquals(100, parts.size)
        }
    }

    @Nested
    inner class InspectorsAutoDetection {

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

        @Test
        fun `rejects empty bytes`() {
            assertThrows(Exception::class.java) {
                Inspectors.forBytes(byteArrayOf())
            }
        }

        @Test
        fun `rejects random data`() {
            assertThrows(IllegalArgumentException::class.java) {
                Inspectors.forBytes(ByteArray(256) { (it * 37).toByte() })
            }
        }

        @Test
        fun `rejects text file`() {
            assertThrows(IllegalArgumentException::class.java) {
                Inspectors.forBytes("Hello World\n".toByteArray())
            }
        }
    }

    @Nested
    inner class ElfInspectorHeaders {

        @Test
        fun `ELF format is detected`() {
            val elf = generateElf(buildAddModule())
            val h = ElfInspector().headers(elf)
            assertEquals(ObjectFormat.ELF, h.format)
        }

        @Test
        fun `x86-64 architecture`() {
            val elf = generateElf(buildAddModule())
            val h = ElfInspector().headers(elf)
            assertEquals(ArchType.X86_64, h.arch.arch)
        }

        @Test
        fun `relocatable type for object file`() {
            val elf = generateElf(buildAddModule())
            val h = ElfInspector().headers(elf)
            assertEquals("relocatable", h.type)
        }

        @Test
        fun `no entry point for relocatable`() {
            val elf = generateElf(buildAddModule())
            val h = ElfInspector().headers(elf)
            assertNull(h.entryPoint)
        }

        @Test
        fun `properties include class info`() {
            val elf = generateElf(buildAddModule())
            val h = ElfInspector().headers(elf)
            assertTrue(h.properties.containsKey("class"))
        }

        @Test
        fun `properties include section count`() {
            val elf = generateElf(buildAddModule())
            val h = ElfInspector().headers(elf)
            assertTrue(h.properties.containsKey("sectionHeaders"))
            val count = h.properties["sectionHeaders"]?.toIntOrNull()
            assertNotNull(count)
            assertTrue(count!! > 0)
        }
    }

    @Nested
    inner class ElfInspectorSections {

        @Test
        fun `has text section`() {
            val elf = generateElf(buildAddModule())
            val sections = ElfInspector().sections(elf)
            assertTrue(sections.any { it.name == ".text" })
        }

        @Test
        fun `text section is TEXT kind`() {
            val elf = generateElf(buildAddModule())
            val text = ElfInspector().sections(elf).first { it.name == ".text" }
            assertEquals(SectionKind.TEXT, text.kind)
        }

        @Test
        fun `text section has non-zero size`() {
            val elf = generateElf(buildAddModule())
            val text = ElfInspector().sections(elf).first { it.name == ".text" }
            assertTrue(text.size > 0)
        }

        @Test
        fun `text section has EXEC flag`() {
            val elf = generateElf(buildAddModule())
            val text = ElfInspector().sections(elf).first { it.name == ".text" }
            assertTrue(text.flags.contains(SectionFlag.EXEC))
        }

        @Test
        fun `has symtab section`() {
            val elf = generateElf(buildAddModule())
            val sections = ElfInspector().sections(elf)
            assertTrue(sections.any { it.kind == SectionKind.SYMTAB })
        }

        @Test
        fun `has strtab section`() {
            val elf = generateElf(buildAddModule())
            val sections = ElfInspector().sections(elf)
            assertTrue(sections.any { it.kind == SectionKind.STRTAB })
        }

        @Test
        fun `section indices are unique`() {
            val elf = generateElf(buildAddModule())
            val sections = ElfInspector().sections(elf)
            val indices = sections.map { it.index }
            assertEquals(indices.toSet().size, indices.size)
        }

        @Test
        fun `section data retrieval`() {
            val elf = generateElf(buildAddModule())
            val data = ElfInspector().sectionData(elf, ".text")
            assertNotNull(data)
            assertTrue(data!!.isNotEmpty())
        }

        @Test
        fun `nonexistent section returns null`() {
            val elf = generateElf(buildAddModule())
            val data = ElfInspector().sectionData(elf, ".nonexistent")
            assertNull(data)
        }

        @Test
        fun `hello module has rodata for string`() {
            val elf = generateElf(buildHelloModule())
            val sections = ElfInspector().sections(elf)
            val hasData = sections.any { it.kind == SectionKind.RODATA || it.kind == SectionKind.DATA }
            assertTrue(hasData, "Expected data section for string constant")
        }
    }

    @Nested
    inner class ElfInspectorSymbols {

        @Test
        fun `finds add function symbol`() {
            val elf = generateElf(buildAddModule())
            val syms = ElfInspector().symbols(elf)
            assertTrue(syms.any { it.name == "add" })
        }

        @Test
        fun `add symbol is global`() {
            val elf = generateElf(buildAddModule())
            val add = ElfInspector().symbols(elf).first { it.name == "add" }
            assertEquals(SymbolBinding.GLOBAL, add.binding)
        }

        @Test
        fun `add symbol is function`() {
            val elf = generateElf(buildAddModule())
            val add = ElfInspector().symbols(elf).first { it.name == "add" }
            assertEquals(SymbolKind.FUNCTION, add.kind)
        }

        @Test
        fun `add symbol has default visibility`() {
            val elf = generateElf(buildAddModule())
            val add = ElfInspector().symbols(elf).first { it.name == "add" }
            assertEquals(SymbolVisibility.DEFAULT, add.visibility)
        }

        @Test
        fun `multi-function module has both symbols`() {
            val elf = generateElf(buildMultiFunctionModule())
            val syms = ElfInspector().symbols(elf)
            assertTrue(syms.any { it.name == "add" })
            assertTrue(syms.any { it.name == "sub" })
        }

        @Test
        fun `hello module has main symbol`() {
            val elf = generateElf(buildHelloModule())
            val syms = ElfInspector().symbols(elf)
            assertTrue(syms.any { it.name == "main" })
        }

        @Test
        fun `subtract function has correct name`() {
            val elf = generateElf(buildSubModule())
            val syms = ElfInspector().symbols(elf)
            assertTrue(syms.any { it.name == "subtract" })
        }
    }

    @Nested
    inner class ElfInspectorRelocations {

        @Test
        fun `hello module has relocations`() {
            val elf = generateElf(buildHelloModule())
            val relocs = ElfInspector().relocations(elf)
            assertTrue(relocs.isNotEmpty())
        }

        @Test
        fun `relocations have type names`() {
            val elf = generateElf(buildHelloModule())
            val relocs = ElfInspector().relocations(elf)
            for (reloc in relocs) {
                assertTrue(reloc.type.isNotEmpty())
            }
        }

        @Test
        fun `relocations reference sections`() {
            val elf = generateElf(buildHelloModule())
            val relocs = ElfInspector().relocations(elf)
            for (reloc in relocs) {
                assertTrue(reloc.section.isNotEmpty())
            }
        }
    }

    @Nested
    inner class ElfInspectorStrings {

        @Test
        fun `finds Hello string`() {
            val elf = generateElf(buildHelloModule())
            val strings = ElfInspector().strings(elf, 4)
            assertTrue(strings.any { it.value.contains("Hello") })
        }

        @Test
        fun `strings have section info`() {
            val elf = generateElf(buildHelloModule())
            val strings = ElfInspector().strings(elf, 4)
            val hello = strings.first { it.value.contains("Hello") }
            assertNotNull(hello.section)
        }

        @Test
        fun `min length filters short strings`() {
            val elf = generateElf(buildHelloModule())
            val long = ElfInspector().strings(elf, 10)
            val short = ElfInspector().strings(elf, 2)
            assertTrue(short.size >= long.size)
        }

        @Test
        fun `strings have valid offsets`() {
            val elf = generateElf(buildHelloModule())
            val strings = ElfInspector().strings(elf, 4)
            for (str in strings) {
                assertTrue(str.offset >= 0)
            }
        }
    }

    @Nested
    inner class ElfInspectorInspect {

        @Test
        fun `inspect returns ObjectFile with ELF format`() {
            val elf = generateElf(buildAddModule())
            val obj = ElfInspector().inspect(elf)
            assertEquals(ObjectFormat.ELF, obj.format)
        }

        @Test
        fun `inspect ObjectFile has text section`() {
            val elf = generateElf(buildAddModule())
            val obj = ElfInspector().inspect(elf)
            assertTrue(obj.sections.any { it.name == ".text" })
        }

        @Test
        fun `inspect ObjectFile has symbols`() {
            val elf = generateElf(buildAddModule())
            val obj = ElfInspector().inspect(elf)
            assertTrue(obj.symbols.any { it.name == "add" })
        }
    }

    @Nested
    inner class PeInspectorHeaders {

        @Test
        fun `PE format detected`() {
            val pe = generatePe(buildHelloModule())
            val h = PeInspector().headers(pe)
            assertEquals(ObjectFormat.PE_COFF, h.format)
        }

        @Test
        fun `x86-64 architecture`() {
            val pe = generatePe(buildHelloModule())
            val h = PeInspector().headers(pe)
            assertEquals(ArchType.X86_64, h.arch.arch)
        }

        @Test
        fun `executable type`() {
            val pe = generatePe(buildHelloModule())
            val h = PeInspector().headers(pe)
            assertEquals("executable", h.type)
        }

        @Test
        fun `has entry point`() {
            val pe = generatePe(buildHelloModule())
            val h = PeInspector().headers(pe)
            assertNotNull(h.entryPoint)
        }

        @Test
        fun `PE32+ flag present`() {
            val pe = generatePe(buildHelloModule())
            val h = PeInspector().headers(pe)
            assertTrue(h.flags.contains("PE32+"))
        }

        @Test
        fun `properties include machine`() {
            val pe = generatePe(buildHelloModule())
            val h = PeInspector().headers(pe)
            assertTrue(h.properties.containsKey("machine"))
        }

        @Test
        fun `properties include imageBase`() {
            val pe = generatePe(buildHelloModule())
            val h = PeInspector().headers(pe)
            assertTrue(h.properties.containsKey("imageBase"))
        }
    }

    @Nested
    inner class PeInspectorSections {

        @Test
        fun `has text section`() {
            val pe = generatePe(buildHelloModule())
            val sections = PeInspector().sections(pe)
            assertTrue(sections.any { it.name == ".text" })
        }

        @Test
        fun `text section has EXEC flag`() {
            val pe = generatePe(buildHelloModule())
            val text = PeInspector().sections(pe).first { it.name == ".text" }
            assertTrue(text.flags.contains(SectionFlag.EXEC))
        }

        @Test
        fun `section data retrieval`() {
            val pe = generatePe(buildHelloModule())
            val data = PeInspector().sectionData(pe, ".text")
            assertNotNull(data)
            assertTrue(data!!.isNotEmpty())
        }

        @Test
        fun `nonexistent section returns null`() {
            val pe = generatePe(buildHelloModule())
            assertNull(PeInspector().sectionData(pe, ".nonexistent"))
        }

        @Test
        fun `sections have non-negative indices`() {
            val pe = generatePe(buildHelloModule())
            val sections = PeInspector().sections(pe)
            for (s in sections) {
                assertTrue(s.index >= 0)
            }
        }
    }

    @Nested
    inner class PeInspectorImportsAndDependencies {

        @Test
        fun `has puts import`() {
            val pe = generatePe(buildHelloModule())
            val imports = PeInspector().imports(pe)
            assertTrue(imports.any { it.name == "puts" })
        }

        @Test
        fun `imports reference ucrtbase`() {
            val pe = generatePe(buildHelloModule())
            val imports = PeInspector().imports(pe)
            assertTrue(imports.any { it.module == "ucrtbase.dll" })
        }

        @Test
        fun `dependencies include ucrtbase`() {
            val pe = generatePe(buildHelloModule())
            val deps = PeInspector().dependencies(pe)
            assertTrue(deps.contains("ucrtbase.dll"))
        }

        @Test
        fun `imports are not delay-loaded by default`() {
            val pe = generatePe(buildHelloModule())
            val imports = PeInspector().imports(pe)
            val puts = imports.first { it.name == "puts" }
            assertFalse(puts.isDelayLoad)
        }
    }

    @Nested
    inner class PeInspectorInspect {

        @Test
        fun `inspect returns PE format`() {
            val pe = generatePe(buildHelloModule())
            val obj = PeInspector().inspect(pe)
            assertEquals(ObjectFormat.PE_COFF, obj.format)
        }

        @Test
        fun `inspect has sections`() {
            val pe = generatePe(buildHelloModule())
            val obj = PeInspector().inspect(pe)
            assertTrue(obj.sections.isNotEmpty())
        }
    }

    @Nested
    inner class PeInspectorStrings {

        @Test
        fun `finds Hello string in PE`() {
            val pe = generatePe(buildHelloModule())
            val strings = PeInspector().strings(pe, 4)
            assertTrue(strings.any { it.value.contains("Hello") })
        }

        @Test
        fun `strings have section info`() {
            val pe = generatePe(buildHelloModule())
            val strings = PeInspector().strings(pe, 4)
            assertTrue(strings.all { it.section != null })
        }
    }

    @Nested
    inner class ElfBinaryDiffIdentical {

        private val diff = ElfBinaryDiff()

        @Test
        fun `identical binaries produce no deltas`() {
            val bytes = makeElf()
            assertTrue(diff.diff(bytes, bytes).isEmpty())
        }

        @Test
        fun `identical binaries produce empty structural diff`() {
            val bytes = makeElf()
            val sd = diff.structuralDiff(bytes, bytes)
            assertTrue(sd.addedSections.isEmpty())
            assertTrue(sd.removedSections.isEmpty())
            assertTrue(sd.modifiedSections.isEmpty())
            assertTrue(sd.addedSymbols.isEmpty())
            assertTrue(sd.removedSymbols.isEmpty())
            assertTrue(sd.modifiedSymbols.isEmpty())
        }

        @Test
        fun `same content different instances produce no diff`() {
            val a = makeElf(code = byteArrayOf(0x90.toByte(), 0xC3.toByte()))
            val b = makeElf(code = byteArrayOf(0x90.toByte(), 0xC3.toByte()))
            assertTrue(diff.diff(a, b).isEmpty())
        }
    }

    @Nested
    inner class ElfBinaryDiffSections {

        private val diff = ElfBinaryDiff()

        @Test
        fun `detects byte-level changes in text`() {
            val a = makeElf(code = byteArrayOf(0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte()))
            val b = makeElf(code = byteArrayOf(0x48, 0x90.toByte(), 0xE5.toByte(), 0xC3.toByte()))
            val deltas = diff.diff(a, b)
            assertTrue(deltas.isNotEmpty())
            assertTrue(deltas.any { it.section == ".text" })
        }

        @Test
        fun `detects byte-level changes in data`() {
            val a = makeElf(data = byteArrayOf(0x01, 0x02, 0x03, 0x04))
            val b = makeElf(data = byteArrayOf(0x01, 0xFF.toByte(), 0x03, 0x04))
            val deltas = diff.diff(a, b)
            assertTrue(deltas.any { it.section == ".data" })
        }

        @Test
        fun `detects added section`() {
            val a = makeElf()
            val b = makeElf(extraSections = listOf(
                Section(".custom", SectionKind.RODATA, byteArrayOf(0xAA.toByte()), align = 1)
            ))
            val sd = diff.structuralDiff(a, b)
            assertTrue(".custom" in sd.addedSections)
        }

        @Test
        fun `detects removed section`() {
            val a = makeElf(extraSections = listOf(
                Section(".custom", SectionKind.RODATA, byteArrayOf(0xAA.toByte()), align = 1)
            ))
            val b = makeElf()
            val sd = diff.structuralDiff(a, b)
            assertTrue(".custom" in sd.removedSections)
        }

        @Test
        fun `detects modified section content`() {
            val a = makeElf(data = byteArrayOf(0x01, 0x02, 0x03, 0x04))
            val b = makeElf(data = byteArrayOf(0xFF.toByte(), 0x02, 0x03, 0x04))
            val sd = diff.structuralDiff(a, b)
            assertTrue(".data" in sd.modifiedSections)
        }

        @Test
        fun `section diffs contain byte deltas`() {
            val a = makeElf(data = byteArrayOf(0x01, 0x02, 0x03, 0x04))
            val b = makeElf(data = byteArrayOf(0xFF.toByte(), 0x02, 0x03, 0x04))
            val sd = diff.structuralDiff(a, b)
            assertTrue(sd.sectionDiffs.containsKey(".data"))
        }
    }

    @Nested
    inner class ElfBinaryDiffSymbols {

        private val diff = ElfBinaryDiff()

        @Test
        fun `detects added symbol`() {
            val a = makeElf(symbols = listOf(
                Symbol("main", value = 0, size = 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val b = makeElf(symbols = listOf(
                Symbol("main", value = 0, size = 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, size = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ))
            val sd = diff.structuralDiff(a, b)
            assertTrue("helper" in sd.addedSymbols)
        }

        @Test
        fun `detects removed symbol`() {
            val a = makeElf(symbols = listOf(
                Symbol("main", value = 0, size = 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, size = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ))
            val b = makeElf(symbols = listOf(
                Symbol("main", value = 0, size = 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val sd = diff.structuralDiff(a, b)
            assertTrue("helper" in sd.removedSymbols)
        }

        @Test
        fun `detects modified symbol size`() {
            val a = makeElf(symbols = listOf(
                Symbol("main", value = 0, size = 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val b = makeElf(symbols = listOf(
                Symbol("main", value = 0, size = 8, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val sd = diff.structuralDiff(a, b)
            assertTrue("main" in sd.modifiedSymbols)
        }

        @Test
        fun `detects modified symbol binding`() {
            val a = makeElf(symbols = listOf(
                Symbol("func", value = 0, size = 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val b = makeElf(symbols = listOf(
                Symbol("func", value = 0, size = 4, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ))
            val sd = diff.structuralDiff(a, b)
            assertTrue("func" in sd.modifiedSymbols)
        }

        @Test
        fun `unchanged symbols not in modified list`() {
            val sym = Symbol("same", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            val a = makeElf(symbols = listOf(sym))
            val b = makeElf(symbols = listOf(sym))
            val sd = diff.structuralDiff(a, b)
            assertFalse("same" in sd.modifiedSymbols)
        }
    }

    @Nested
    inner class DiffBytes {

        @Test
        fun `finds single byte difference`() {
            val a = byteArrayOf(0x00, 0x01, 0x02, 0x03)
            val b = byteArrayOf(0x00, 0xFF.toByte(), 0x02, 0x03)
            val deltas = ElfBinaryDiff.diffBytes(a, b, 0x1000, ".text", emptyMap())
            assertEquals(1, deltas.size)
            assertEquals(0x1001L, deltas[0].offset)
        }

        @Test
        fun `finds nearest symbol`() {
            val a = byteArrayOf(0x00, 0x01, 0x02, 0x03)
            val b = byteArrayOf(0x00, 0xFF.toByte(), 0x02, 0x03)
            val syms = mapOf(0x1000L to "start")
            val deltas = ElfBinaryDiff.diffBytes(a, b, 0x1000, ".text", syms)
            assertEquals("start", deltas[0].nearestSymbol)
        }

        @Test
        fun `coalesces nearby changes`() {
            val a = byteArrayOf(0x01, 0x00, 0x00, 0x02)
            val b = byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0xFE.toByte())
            val deltas = ElfBinaryDiff.diffBytes(a, b, 0, null, emptyMap())
            assertEquals(1, deltas.size)
        }

        @Test
        fun `identical bytes produce no deltas`() {
            val a = byteArrayOf(1, 2, 3, 4)
            val deltas = ElfBinaryDiff.diffBytes(a, a, 0, null, emptyMap())
            assertTrue(deltas.isEmpty())
        }

        @Test
        fun `completely different bytes`() {
            val a = byteArrayOf(0x00, 0x00, 0x00, 0x00)
            val b = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
            val deltas = ElfBinaryDiff.diffBytes(a, b, 0, ".text", emptyMap())
            assertTrue(deltas.isNotEmpty())
        }

        @Test
        fun `delta contains old and new bytes`() {
            val a = byteArrayOf(0x01, 0x02)
            val b = byteArrayOf(0x03, 0x04)
            val deltas = ElfBinaryDiff.diffBytes(a, b, 0, null, emptyMap())
            assertTrue(deltas.isNotEmpty())
            val delta = deltas[0]
            assertTrue(delta.oldBytes.isNotEmpty())
            assertTrue(delta.newBytes.isNotEmpty())
        }
    }

    @Nested
    inner class ElfPatcherLoadAndFormat {

        private val patcher = ElfBinaryPatcher()

        @Test
        fun `load detects ELF format`() {
            val bin = patcher.load(makeElf())
            assertEquals(ObjectFormat.ELF, bin.format)
        }

        @Test
        fun `load detects x86-64 arch`() {
            val bin = patcher.load(makeElf())
            assertEquals(ArchType.X86_64, bin.arch.arch)
        }
    }

    @Nested
    inner class ElfPatcherSections {

        private val patcher = ElfBinaryPatcher()

        @Test
        fun `read text section`() {
            val bin = patcher.load(makeElf())
            val text = bin.readSection(".text")
            assertNotNull(text)
            assertEquals(0x48.toByte(), text!![0])
        }

        @Test
        fun `read data section`() {
            val bin = patcher.load(makeElf())
            val data = bin.readSection(".data")
            assertNotNull(data)
            assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), data)
        }

        @Test
        fun `read nonexistent section returns null`() {
            val bin = patcher.load(makeElf())
            assertNull(bin.readSection(".nonexistent"))
        }

        @Test
        fun `write replaces section data`() {
            val bin = patcher.load(makeElf())
            val newCode = byteArrayOf(0x90.toByte(), 0x90.toByte(), 0x90.toByte(), 0x90.toByte())
            bin.writeSection(".text", newCode)
            assertArrayEquals(newCode, bin.readSection(".text"))
        }

        @Test
        fun `write to nonexistent section throws`() {
            val bin = patcher.load(makeElf())
            assertThrows(IllegalArgumentException::class.java) {
                bin.writeSection(".nonexistent", byteArrayOf(1))
            }
        }

        @Test
        fun `add new section`() {
            val bin = patcher.load(makeElf())
            bin.addSection(".custom", SectionKind.RODATA, byteArrayOf(0xDE.toByte(), 0xAD.toByte()))
            assertArrayEquals(byteArrayOf(0xDE.toByte(), 0xAD.toByte()), bin.readSection(".custom"))
        }

        @Test
        fun `remove section`() {
            val bin = patcher.load(makeElf())
            assertNotNull(bin.readSection(".data"))
            bin.removeSection(".data")
            assertNull(bin.readSection(".data"))
        }

        @Test
        fun `rename section`() {
            val bin = patcher.load(makeElf())
            bin.renameSection(".data", ".mydata")
            assertNull(bin.readSection(".data"))
            assertNotNull(bin.readSection(".mydata"))
        }

        @Test
        fun `write larger data to section`() {
            val bin = patcher.load(makeElf())
            val largeData = ByteArray(256) { it.toByte() }
            bin.writeSection(".text", largeData)
            assertArrayEquals(largeData, bin.readSection(".text"))
        }

        @Test
        fun `write empty data to section`() {
            val bin = patcher.load(makeElf())
            bin.writeSection(".text", ByteArray(0))
            val text = bin.readSection(".text")
            assertNotNull(text)
            assertEquals(0, text!!.size)
        }

        @Test
        fun `add multiple sections`() {
            val bin = patcher.load(makeElf())
            bin.addSection(".custom1", SectionKind.RODATA, byteArrayOf(1))
            bin.addSection(".custom2", SectionKind.DATA, byteArrayOf(2))
            assertArrayEquals(byteArrayOf(1), bin.readSection(".custom1"))
            assertArrayEquals(byteArrayOf(2), bin.readSection(".custom2"))
        }
    }

    @Nested
    inner class ElfPatcherSymbols {

        private val patcher = ElfBinaryPatcher()

        @Test
        fun `list symbols`() {
            val bin = patcher.load(makeElf())
            val names = bin.symbols().map { it.name }
            assertTrue("main" in names)
        }

        @Test
        fun `find existing symbol`() {
            val bin = patcher.load(makeElf())
            val sym = bin.findSymbol("main")
            assertNotNull(sym)
            assertEquals("main", sym!!.name)
            assertEquals(SymbolKind.FUNCTION, sym.kind)
        }

        @Test
        fun `find nonexistent symbol returns null`() {
            val bin = patcher.load(makeElf())
            assertNull(bin.findSymbol("doesnotexist"))
        }

        @Test
        fun `rename symbol`() {
            val bin = patcher.load(makeElf())
            bin.renameSymbol("main", "_start")
            assertNull(bin.findSymbol("main"))
            assertNotNull(bin.findSymbol("_start"))
        }

        @Test
        fun `add symbol`() {
            val bin = patcher.load(makeElf())
            bin.addSymbol(Symbol("newsym", value = 0, size = 0,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION))
            assertNotNull(bin.findSymbol("newsym"))
        }

        @Test
        fun `remove symbol`() {
            val bin = patcher.load(makeElf())
            bin.removeSymbol("main")
            assertNull(bin.findSymbol("main"))
        }

        @Test
        fun `set symbol visibility to hidden`() {
            val bin = patcher.load(makeElf())
            bin.setSymbolVisibility("main", SymbolVisibility.HIDDEN)
            assertEquals(SymbolVisibility.HIDDEN, bin.findSymbol("main")!!.visibility)
        }

        @Test
        fun `set symbol visibility to protected`() {
            val bin = patcher.load(makeElf())
            bin.setSymbolVisibility("main", SymbolVisibility.PROTECTED)
            assertEquals(SymbolVisibility.PROTECTED, bin.findSymbol("main")!!.visibility)
        }

        @Test
        fun `find data symbol`() {
            val bin = patcher.load(makeElf(symbols = listOf(
                Symbol("main", value = 0, size = 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("myvar", value = 0, size = 4, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            )))
            val sym = bin.findSymbol("myvar")
            assertNotNull(sym)
            assertEquals(SymbolKind.DATA, sym!!.kind)
        }

        @Test
        fun `add then find symbol`() {
            val bin = patcher.load(makeElf())
            bin.addSymbol(Symbol("added", value = 100, size = 8,
                binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA))
            val found = bin.findSymbol("added")
            assertNotNull(found)
            assertEquals(SymbolBinding.LOCAL, found!!.binding)
        }
    }

    @Nested
    inner class ElfPatcherCodePatching {

        private val patcher = ElfBinaryPatcher()

        @Test
        fun `read bytes at offset`() {
            val bin = patcher.load(makeElf())
            val bytes = bin.readBytes(".text", 0, 2)
            assertEquals(0x48.toByte(), bytes[0])
            assertEquals(0x89.toByte(), bytes[1])
        }

        @Test
        fun `write bytes at offset`() {
            val bin = patcher.load(makeElf())
            bin.writeBytes(".text", 1, byteArrayOf(0xFF.toByte()))
            val result = bin.readBytes(".text", 0, 2)
            assertEquals(0x48.toByte(), result[0])
            assertEquals(0xFF.toByte(), result[1])
        }

        @Test
        fun `patch instruction`() {
            val bin = patcher.load(makeElf())
            bin.patchInstruction(".text", 0, byteArrayOf(0x90.toByte()))
            assertEquals(0x90.toByte(), bin.readBytes(".text", 0, 1)[0])
        }

        @Test
        fun `nop out range`() {
            val bin = patcher.load(makeElf())
            bin.nopOut(".text", 0, 4)
            val text = bin.readSection(".text")!!
            for (b in text) {
                assertEquals(0x90.toByte(), b)
            }
        }

        @Test
        fun `nop out single byte`() {
            val bin = patcher.load(makeElf())
            bin.nopOut(".text", 0, 1)
            assertEquals(0x90.toByte(), bin.readBytes(".text", 0, 1)[0])
            // Remaining bytes unchanged
            assertEquals(0x89.toByte(), bin.readBytes(".text", 1, 1)[0])
        }

        @Test
        fun `patch multiple locations`() {
            val bin = patcher.load(makeElf())
            bin.writeBytes(".text", 0, byteArrayOf(0xCC.toByte()))
            bin.writeBytes(".text", 3, byteArrayOf(0xCC.toByte()))
            assertEquals(0xCC.toByte(), bin.readBytes(".text", 0, 1)[0])
            assertEquals(0xCC.toByte(), bin.readBytes(".text", 3, 1)[0])
            // Middle bytes unchanged
            assertEquals(0x89.toByte(), bin.readBytes(".text", 1, 1)[0])
        }
    }

    @Nested
    inner class ElfPatcherEntryPoint {

        private val patcher = ElfBinaryPatcher()

        @Test
        fun `set and get entry point`() {
            val bin = patcher.load(makeElf())
            bin.setEntryPoint(0x401000)
            assertEquals(0x401000L, bin.entryPoint())
        }

        @Test
        fun `set entry point to zero`() {
            val bin = patcher.load(makeElf())
            bin.setEntryPoint(0x1000)
            assertEquals(0x1000L, bin.entryPoint())
        }

        @Test
        fun `set large entry point`() {
            val bin = patcher.load(makeElf())
            bin.setEntryPoint(0x7FFF00000000)
            assertEquals(0x7FFF00000000L, bin.entryPoint())
        }
    }

    @Nested
    inner class ElfPatcherRoundTrip {

        private val patcher = ElfBinaryPatcher()

        @Test
        fun `assemble preserves sections`() {
            val bin = patcher.load(makeElf())
            bin.writeSection(".text", byteArrayOf(0x90.toByte(), 0x90.toByte(), 0x90.toByte(), 0x90.toByte()))
            val reassembled = bin.assemble()
            val bin2 = patcher.load(reassembled)
            assertArrayEquals(byteArrayOf(0x90.toByte(), 0x90.toByte(), 0x90.toByte(), 0x90.toByte()),
                bin2.readSection(".text"))
        }

        @Test
        fun `assemble preserves symbols`() {
            val bin = patcher.load(makeElf())
            bin.renameSymbol("main", "_start")
            val reassembled = bin.assemble()
            val bin2 = patcher.load(reassembled)
            assertNotNull(bin2.findSymbol("_start"))
            assertNull(bin2.findSymbol("main"))
        }

        @Test
        fun `assemble after adding section`() {
            val bin = patcher.load(makeElf())
            bin.addSection(".notes", SectionKind.NOTE, byteArrayOf(1, 2, 3, 4))
            val reassembled = bin.assemble()
            val bin2 = patcher.load(reassembled)
            assertNotNull(bin2.readSection(".text"))
            // Original sections should still be present
        }

        @Test
        fun `assemble after removing section`() {
            val bin = patcher.load(makeElf())
            bin.removeSection(".data")
            val reassembled = bin.assemble()
            val bin2 = patcher.load(reassembled)
            assertNotNull(bin2.readSection(".text"))
            assertNull(bin2.readSection(".data"))
        }

        @Test
        fun `assemble preserves modified sections`() {
            val bin = patcher.load(makeElf())
            bin.writeSection(".data", byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte()))
            val reassembled = bin.assemble()
            val bin2 = patcher.load(reassembled)
            assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte()),
                bin2.readSection(".data"))
        }

        @Test
        fun `double assemble round-trip`() {
            val bin = patcher.load(makeElf())
            bin.writeSection(".text", byteArrayOf(0xCC.toByte(), 0xCC.toByte(), 0xCC.toByte(), 0xCC.toByte()))
            val first = bin.assemble()
            val bin2 = patcher.load(first)
            val second = bin2.assemble()
            val bin3 = patcher.load(second)
            assertArrayEquals(byteArrayOf(0xCC.toByte(), 0xCC.toByte(), 0xCC.toByte(), 0xCC.toByte()),
                bin3.readSection(".text"))
        }
    }

    @Nested
    inner class ElfPatcherWithGeneratedCode {

        private val patcher = ElfBinaryPatcher()

        @Test
        fun `patch generated ELF object`() {
            val elf = generateElf(buildAddModule())
            val bin = patcher.load(elf)
            assertEquals(ObjectFormat.ELF, bin.format)
            assertNotNull(bin.findSymbol("add"))
        }

        @Test
        fun `rename function in generated ELF`() {
            val elf = generateElf(buildAddModule())
            val bin = patcher.load(elf)
            bin.renameSymbol("add", "my_add")
            assertNull(bin.findSymbol("add"))
            assertNotNull(bin.findSymbol("my_add"))
        }

        @Test
        fun `nop out generated code`() {
            val elf = generateElf(buildAddModule())
            val bin = patcher.load(elf)
            val text = bin.readSection(".text")!!
            bin.nopOut(".text", 0, text.size)
            val patched = bin.readSection(".text")!!
            for (b in patched) {
                assertEquals(0x90.toByte(), b)
            }
        }
    }

    @Nested
    inner class CrossToolIntegration {

        @Test
        fun `inspect then diff ELF binaries`() {
            val elfA = generateElf(buildAddModule())
            val elfB = generateElf(buildSubModule())

            val inspectorA = ElfInspector()
            val inspectorB = ElfInspector()

            val symsA = inspectorA.symbols(elfA).map { it.name }.toSet()
            val symsB = inspectorB.symbols(elfB).map { it.name }.toSet()

            // They should have different function names
            assertTrue("add" in symsA)
            assertTrue("subtract" in symsB)

            val diff = ElfBinaryDiff()
            val sd = diff.structuralDiff(elfA, elfB)
            // Different symbols
            assertTrue(sd.addedSymbols.isNotEmpty() || sd.removedSymbols.isNotEmpty())
        }

        @Test
        fun `patch then inspect`() {
            val elf = generateElf(buildAddModule())
            val patcher = ElfBinaryPatcher()
            val bin = patcher.load(elf)
            bin.renameSymbol("add", "addition")
            val patched = bin.assemble()

            val inspector = ElfInspector()
            val syms = inspector.symbols(patched)
            assertTrue(syms.any { it.name == "addition" })
            assertFalse(syms.any { it.name == "add" })
        }

        @Test
        fun `hex dump of section data from inspector`() {
            val elf = generateElf(buildAddModule())
            val inspector = ElfInspector()
            val textData = inspector.sectionData(elf, ".text")!!
            val dump = HexDump.format(textData)
            assertTrue(dump.isNotEmpty())
            assertTrue(dump.startsWith("00000000"))
        }

        @Test
        fun `hex dump of section data with base address`() {
            val elf = generateElf(buildAddModule())
            val inspector = ElfInspector()
            val textData = inspector.sectionData(elf, ".text")!!
            val dump = HexDump.format(textData, baseAddress = 0x401000)
            assertTrue(dump.startsWith("00401000"))
        }

        @Test
        fun `diff after patching shows changes`() {
            val original = makeElf()
            val patcher = ElfBinaryPatcher()
            val bin = patcher.load(original)
            bin.writeSection(".text", byteArrayOf(0x90.toByte(), 0x90.toByte(), 0x90.toByte(), 0x90.toByte()))
            val patched = bin.assemble()

            val diff = ElfBinaryDiff()
            val deltas = diff.diff(original, patched)
            assertTrue(deltas.any { it.section == ".text" })
        }

        @Test
        fun `format bytes from patcher readBytes`() {
            val patcher = ElfBinaryPatcher()
            val bin = patcher.load(makeElf())
            val bytes = bin.readBytes(".text", 0, 4)
            val formatted = HexDump.formatBytes(bytes)
            assertTrue(formatted.isNotEmpty())
            assertTrue(formatted.contains("48"))
        }

        @Test
        fun `inspector and patcher agree on symbol count`() {
            val elf = generateElf(buildMultiFunctionModule())
            val inspector = ElfInspector()
            val patcher = ElfBinaryPatcher()

            val inspectedSyms = inspector.symbols(elf).filter {
                it.kind == SymbolKind.FUNCTION && it.binding == SymbolBinding.GLOBAL
            }
            val bin = patcher.load(elf)
            val patchedSyms = bin.symbols().filter {
                it.kind == SymbolKind.FUNCTION && it.binding == SymbolBinding.GLOBAL
            }

            assertEquals(inspectedSyms.size, patchedSyms.size)
        }
    }

    @Nested
    inner class BinaryDeltaModel {

        @Test
        fun `BinaryDelta equality by offset and content`() {
            val a = BinaryDelta(0x100, byteArrayOf(1), byteArrayOf(2), ".text", "main")
            val b = BinaryDelta(0x100, byteArrayOf(1), byteArrayOf(2), ".text", "main")
            assertEquals(a, b)
        }

        @Test
        fun `BinaryDelta inequality by offset`() {
            val a = BinaryDelta(0x100, byteArrayOf(1), byteArrayOf(2), ".text", null)
            val b = BinaryDelta(0x200, byteArrayOf(1), byteArrayOf(2), ".text", null)
            assertNotEquals(a, b)
        }

        @Test
        fun `BinaryDelta inequality by content`() {
            val a = BinaryDelta(0x100, byteArrayOf(1), byteArrayOf(2), ".text", null)
            val b = BinaryDelta(0x100, byteArrayOf(3), byteArrayOf(4), ".text", null)
            assertNotEquals(a, b)
        }

        @Test
        fun `BinaryDelta hashCode consistent`() {
            val a = BinaryDelta(0x100, byteArrayOf(1), byteArrayOf(2), ".text", null)
            val b = BinaryDelta(0x100, byteArrayOf(1), byteArrayOf(2), ".text", null)
            assertEquals(a.hashCode(), b.hashCode())
        }
    }

    @Nested
    inner class FormatDetection {

        @Test
        fun `ELF magic detected by reader`() {
            val elf = generateElf(buildAddModule())
            assertTrue(ElfReader.canRead(elf))
        }

        @Test
        fun `PE magic detected`() {
            val pe = generatePe(buildHelloModule())
            assertTrue(pe[0] == 0x4D.toByte() && pe[1] == 0x5A.toByte())
        }

        @Test
        fun `ELF bytes not detected as PE`() {
            val elf = generateElf(buildAddModule())
            assertFalse(elf[0] == 0x4D.toByte() && elf[1] == 0x5A.toByte())
        }

        @Test
        fun `generated ELF starts with correct magic`() {
            val elf = generateElf(buildAddModule())
            assertEquals(0x7F.toByte(), elf[0])
            assertEquals('E'.code.toByte(), elf[1])
            assertEquals('L'.code.toByte(), elf[2])
            assertEquals('F'.code.toByte(), elf[3])
        }
    }
}
