package org.kgen.binary

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.diff.ElfBinaryDiff
import org.kgen.binary.elf.ElfObjectWriter
import org.kgen.binary.inspect.ElfInspector
import org.kgen.binary.inspect.Inspectors
import org.kgen.binary.patch.ElfBinaryPatcher
import org.kgen.binary.mangling.ItaniumDemangler
import org.kgen.binary.mangling.MsvcDemangler
import org.kgen.binary.mangling.RustDemangler
import org.kgen.binary.mangling.UniversalDemangler

class ToolsExtendedTest {

    // --- Helpers ---

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

    @Test
    fun `ELF inspector sections include data section`() {
        val elf = makeElf()
        val inspector = ElfInspector()
        val sections = inspector.sections(elf)
        assertTrue(sections.any { it.name == ".data" })
        val data = sections.first { it.name == ".data" }
        assertEquals(SectionKind.DATA, data.kind)
    }

    @Test
    fun `ELF inspector section data returns null for missing section`() {
        val elf = makeElf()
        val inspector = ElfInspector()
        assertNull(inspector.sectionData(elf, ".nonexistent"))
    }

    @Test
    fun `ELF inspector symbols include correct binding`() {
        val elf = makeElf(symbols = listOf(
            Symbol("func", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            Symbol("glob", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
        ))
        val inspector = ElfInspector()
        val syms = inspector.symbols(elf)
        val func = syms.firstOrNull { it.name == "func" }
        val glob = syms.firstOrNull { it.name == "glob" }
        assertNotNull(func)
        assertNotNull(glob)
        assertEquals(SymbolBinding.LOCAL, func!!.binding)
        assertEquals(SymbolBinding.GLOBAL, glob!!.binding)
        assertEquals(SymbolKind.DATA, glob.kind)
    }

    @Test
    fun `ELF inspect with multiple sections`() {
        val elf = makeElf(
            extraSections = listOf(
                Section(".rodata", SectionKind.RODATA, byteArrayOf(0x48, 0x65), align = 1),
            )
        )
        val inspector = ElfInspector()
        val obj = inspector.inspect(elf)
        assertTrue(obj.sections.any { it.name == ".rodata" })
        assertTrue(obj.sections.any { it.name == ".text" })
        assertTrue(obj.sections.any { it.name == ".data" })
    }

    @Test
    fun `ELF inspector strings with min length filtering`() {
        val longStr = "ABCDEFGHIJ".toByteArray()
        val elf = makeElf(data = longStr)
        val inspector = ElfInspector()
        val strings8 = inspector.strings(elf, 8)
        assertTrue(strings8.any { it.value.contains("ABCDEFGH") })
        val strings20 = inspector.strings(elf, 20)
        assertTrue(strings20.none { it.value.contains("ABCDEFGH") })
    }

    @Test
    fun `Inspectors rejects empty bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            Inspectors.forBytes(byteArrayOf())
        }
    }

    private val itanium = ItaniumDemangler()
    private val rust = RustDemangler()
    private val msvc = MsvcDemangler()
    private val universal = UniversalDemangler()

    @Test
    fun `itanium function with long param`() {
        val result = itanium.demangle("_Z3fool")
        assertNotNull(result)
        assertTrue(result!!.contains("long"))
    }

    @Test
    fun `itanium function with char param`() {
        val result = itanium.demangle("_Z3fooc")
        assertNotNull(result)
        assertTrue(result!!.contains("char"))
    }

    @Test
    fun `itanium function with bool param`() {
        val result = itanium.demangle("_Z3foob")
        assertNotNull(result)
        assertTrue(result!!.contains("bool"))
    }

    @Test
    fun `itanium deeply nested namespace`() {
        val result = itanium.demangle("_ZN3foo3bar3bazEv")
        assertNotNull(result)
        assertTrue(result!!.contains("foo::bar::baz"))
    }

    @Test
    fun `itanium canDemangle detects valid names`() {
        assertTrue(itanium.canDemangle("_Z3foov"))
        assertTrue(itanium.canDemangle("__Z3foov"))
        assertFalse(itanium.canDemangle("printf"))
        assertFalse(itanium.canDemangle("?foo@@YAHXZ"))
    }

    @Test
    fun `rust legacy with multiple segments`() {
        val result = rust.demangle("_ZN4test6nested5inner17h0123456789abcdefE")
        assertNotNull(result)
        assertEquals("test::nested::inner", result)
    }

    @Test
    fun `rust canDemangle detects legacy mangled names`() {
        assertTrue(rust.canDemangle("_ZN4core3fmt5write17h1234567890abcdefE"))
        assertFalse(rust.canDemangle("_Z3foov"))
    }

    @Test
    fun `msvc function with int return`() {
        val result = msvc.demangle("?bar@@YAHH@Z")
        assertNotNull(result)
        assertTrue(result!!.contains("bar"))
    }

    @Test
    fun `universal auto-demangles itanium nested`() {
        val result = universal.demangle("_ZN3foo3barEv")
        assertNotNull(result)
        assertTrue(result!!.contains("foo::bar"))
    }

    @Test
    fun `universal returns null for empty string`() {
        assertNull(universal.detect(""))
        assertNull(universal.demangle(""))
    }

    @Test
    fun `formats exactly 16 bytes as one line`() {
        val bytes = ByteArray(16) { (it + 0x30).toByte() }
        val lines = HexDump.format(bytes).trim().lines()
        assertEquals(1, lines.size)
    }

    @Test
    fun `formats 17 bytes as two lines`() {
        val bytes = ByteArray(17) { 0x41.toByte() }
        val lines = HexDump.format(bytes).trim().lines()
        assertEquals(2, lines.size)
    }

    @Test
    fun `custom bytes per line`() {
        val bytes = ByteArray(16) { it.toByte() }
        val lines = HexDump.format(bytes, bytesPerLine = 8).trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("00000000"))
        assertTrue(lines[1].startsWith("00000008"))
    }

    @Test
    fun `formatBytes empty array`() {
        assertEquals("", HexDump.formatBytes(byteArrayOf()))
    }

    @Test
    fun `formatBytes single byte`() {
        assertEquals("ff", HexDump.formatBytes(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun `hex dump handles all printable ascii`() {
        val bytes = ByteArray(95) { (it + 0x20).toByte() }
        val result = HexDump.format(bytes)
        assertFalse(result.isEmpty())
        assertTrue(result.contains("| !"))
    }

    private val patcher = ElfBinaryPatcher()

    @Test
    fun `patcher add and remove section round-trip`() {
        val bin = patcher.load(makeElf())
        bin.addSection(".custom", SectionKind.RODATA, byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        assertNotNull(bin.readSection(".custom"))
        bin.removeSection(".custom")
        assertNull(bin.readSection(".custom"))
    }

    @Test
    fun `patcher multiple symbol operations`() {
        val bin = patcher.load(makeElf())
        bin.addSymbol(Symbol("helper", value = 4, size = 0,
            binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION))
        bin.addSymbol(Symbol("data_sym", value = 0, size = 4,
            binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA))
        val syms = bin.symbols().map { it.name }
        assertTrue("helper" in syms)
        assertTrue("data_sym" in syms)
        bin.removeSymbol("helper")
        assertNull(bin.findSymbol("helper"))
        assertNotNull(bin.findSymbol("data_sym"))
    }

    @Test
    fun `patcher write bytes at end of section`() {
        val bin = patcher.load(makeElf())
        bin.writeBytes(".data", 3, byteArrayOf(0xFF.toByte()))
        val read = bin.readBytes(".data", 3, 1)
        assertEquals(0xFF.toByte(), read[0])
    }

    @Test
    fun `patcher nop out partial range`() {
        val bin = patcher.load(makeElf())
        bin.nopOut(".text", 1, 2)
        val text = bin.readSection(".text")!!
        assertEquals(0x48.toByte(), text[0])
        assertEquals(0x90.toByte(), text[1])
        assertEquals(0x90.toByte(), text[2])
        assertEquals(0xC3.toByte(), text[3])
    }

    @Test
    fun `patcher rename and patch section then assemble`() {
        val bin = patcher.load(makeElf())
        bin.renameSection(".data", ".mydata")
        bin.writeSection(".mydata", byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()))
        val assembled = bin.assemble()
        val bin2 = patcher.load(assembled)
        assertNull(bin2.readSection(".data"))
        val mydata = bin2.readSection(".mydata")
        assertNotNull(mydata)
        assertEquals(0xDE.toByte(), mydata!![0])
    }

    private val diff = ElfBinaryDiff()

    @Test
    fun `diff detects data section changes`() {
        val a = makeElf(data = byteArrayOf(0x01, 0x02, 0x03, 0x04))
        val b = makeElf(data = byteArrayOf(0x01, 0x02, 0xFF.toByte(), 0x04))
        val deltas = diff.diff(a, b)
        assertTrue(deltas.isNotEmpty())
        assertTrue(deltas.any { it.section == ".data" })
    }

    @Test
    fun `structural diff detects changed symbol binding`() {
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
    fun `structural diff no added or removed when same symbols`() {
        val syms = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
        )
        val a = makeElf(symbols = syms)
        val b = makeElf(symbols = syms)
        val sd = diff.structuralDiff(a, b)
        assertTrue(sd.addedSymbols.isEmpty())
        assertTrue(sd.removedSymbols.isEmpty())
    }

    @Test
    fun `diff bytes with no differences returns empty`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val deltas = ElfBinaryDiff.diffBytes(data, data, 0, ".test", emptyMap())
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `diff bytes with completely different data`() {
        val a = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val b = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val deltas = ElfBinaryDiff.diffBytes(a, b, 0x1000, ".text", emptyMap())
        assertEquals(1, deltas.size)
        assertEquals(0x1000L, deltas[0].offset)
    }
}
