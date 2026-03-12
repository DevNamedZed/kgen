package org.kgen.binary.patch

import org.kgen.binary.*
import org.kgen.binary.elf.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ElfBinaryPatcherTest {

    private val patcher = ElfBinaryPatcher()

    private fun makeSimpleElf(): ByteArray {
        val code = byteArrayOf(0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, data, align = 4),
            ),
            symbols = listOf(
                Symbol("main", value = 0, size = 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("myvar", value = 0, size = 4, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
            relocations = emptyList(),
        )
        return ElfObjectWriter().write(obj)
    }

    @Test
    fun `load and check format`() {
        val bin = patcher.load(makeSimpleElf())
        assertEquals(ObjectFormat.ELF, bin.format)
        assertEquals(ArchType.X86_64, bin.arch.arch)
    }

    @Test
    fun `read section data`() {
        val bin = patcher.load(makeSimpleElf())
        val text = bin.readSection(".text")
        assertNotNull(text)
        assertEquals(0x48.toByte(), text!![0])
        assertEquals(0xC3.toByte(), text[3])
    }

    @Test
    fun `read nonexistent section returns null`() {
        val bin = patcher.load(makeSimpleElf())
        assertNull(bin.readSection(".nonexistent"))
    }

    @Test
    fun `write section replaces data`() {
        val bin = patcher.load(makeSimpleElf())
        val newData = byteArrayOf(0x90.toByte(), 0x90.toByte(), 0x90.toByte(), 0x90.toByte())
        bin.writeSection(".text", newData)
        val text = bin.readSection(".text")
        assertArrayEquals(newData, text)
    }

    @Test
    fun `write section to nonexistent throws`() {
        val bin = patcher.load(makeSimpleElf())
        assertThrows(IllegalArgumentException::class.java) {
            bin.writeSection(".nonexistent", byteArrayOf(1))
        }
    }

    @Test
    fun `add section`() {
        val bin = patcher.load(makeSimpleElf())
        val custom = byteArrayOf(0xDE.toByte(), 0xAD.toByte())
        bin.addSection(".custom", SectionKind.RODATA, custom)
        val read = bin.readSection(".custom")
        assertArrayEquals(custom, read)
    }

    @Test
    fun `remove section`() {
        val bin = patcher.load(makeSimpleElf())
        assertNotNull(bin.readSection(".data"))
        bin.removeSection(".data")
        assertNull(bin.readSection(".data"))
    }

    @Test
    fun `rename section`() {
        val bin = patcher.load(makeSimpleElf())
        bin.renameSection(".data", ".mydata")
        assertNull(bin.readSection(".data"))
        assertNotNull(bin.readSection(".mydata"))
    }

    @Test
    fun `list symbols`() {
        val bin = patcher.load(makeSimpleElf())
        val syms = bin.symbols()
        val names = syms.map { it.name }
        assertTrue("main" in names)
        assertTrue("myvar" in names)
    }

    @Test
    fun `find symbol`() {
        val bin = patcher.load(makeSimpleElf())
        val main = bin.findSymbol("main")
        assertNotNull(main)
        assertEquals("main", main!!.name)
        assertEquals(SymbolKind.FUNCTION, main.kind)
    }

    @Test
    fun `find nonexistent symbol returns null`() {
        val bin = patcher.load(makeSimpleElf())
        assertNull(bin.findSymbol("doesnotexist"))
    }

    @Test
    fun `rename symbol`() {
        val bin = patcher.load(makeSimpleElf())
        bin.renameSymbol("main", "_start")
        assertNull(bin.findSymbol("main"))
        assertNotNull(bin.findSymbol("_start"))
    }

    @Test
    fun `add symbol`() {
        val bin = patcher.load(makeSimpleElf())
        bin.addSymbol(Symbol("newsym", value = 0, size = 0,
            binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION))
        assertNotNull(bin.findSymbol("newsym"))
    }

    @Test
    fun `remove symbol`() {
        val bin = patcher.load(makeSimpleElf())
        assertNotNull(bin.findSymbol("main"))
        bin.removeSymbol("main")
        assertNull(bin.findSymbol("main"))
    }

    @Test
    fun `set symbol visibility`() {
        val bin = patcher.load(makeSimpleElf())
        bin.setSymbolVisibility("main", SymbolVisibility.HIDDEN)
        val sym = bin.findSymbol("main")
        assertEquals(SymbolVisibility.HIDDEN, sym!!.visibility)
    }

    @Test
    fun `read and write bytes at offset`() {
        val bin = patcher.load(makeSimpleElf())
        val orig = bin.readBytes(".text", 0, 2)
        assertEquals(0x48.toByte(), orig[0])
        assertEquals(0x89.toByte(), orig[1])

        bin.writeBytes(".text", 1, byteArrayOf(0xFF.toByte()))
        val patched = bin.readBytes(".text", 0, 2)
        assertEquals(0x48.toByte(), patched[0])
        assertEquals(0xFF.toByte(), patched[1])
    }

    @Test
    fun `patch instruction`() {
        val bin = patcher.load(makeSimpleElf())
        bin.patchInstruction(".text", 0, byteArrayOf(0x90.toByte()))
        val b = bin.readBytes(".text", 0, 1)
        assertEquals(0x90.toByte(), b[0])
    }

    @Test
    fun `nop out range`() {
        val bin = patcher.load(makeSimpleElf())
        bin.nopOut(".text", 0, 4)
        val text = bin.readSection(".text")!!
        for (b in text) assertEquals(0x90.toByte(), b)
    }

    @Test
    fun `entry point`() {
        val bin = patcher.load(makeSimpleElf())
        bin.setEntryPoint(0x401000)
        assertEquals(0x401000L, bin.entryPoint())
    }

    @Test
    fun `assemble round-trip preserves sections`() {
        val bin = patcher.load(makeSimpleElf())
        bin.writeSection(".text", byteArrayOf(0x90.toByte(), 0x90.toByte(), 0x90.toByte(), 0x90.toByte()))
        val assembled = bin.assemble()

        val bin2 = patcher.load(assembled)
        val text = bin2.readSection(".text")
        assertNotNull(text)
        assertEquals(4, text!!.size)
        assertEquals(0x90.toByte(), text[0])
    }

    @Test
    fun `assemble round-trip preserves symbols`() {
        val bin = patcher.load(makeSimpleElf())
        bin.renameSymbol("main", "_start")
        val assembled = bin.assemble()

        val bin2 = patcher.load(assembled)
        assertNotNull(bin2.findSymbol("_start"))
        assertNull(bin2.findSymbol("main"))
    }
}
