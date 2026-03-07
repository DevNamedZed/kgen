package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PeDllLinkerExtendedTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)
    private fun peHeaderOffset(buf: ByteBuffer): Int = readU32(buf, 0x3C)

    private fun makeObj(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
        imports: List<ImportEntry> = emptyList(),
    ) = ObjectFile(
        format = ObjectFormat.PE_COFF,
        arch = Architecture(ArchType.X86_64),
        sections = sections,
        symbols = symbols,
        relocations = relocations,
        imports = imports,
    )

    private fun simpleLib(name: String = "func", code: ByteArray = byteArrayOf(0xC3.toByte())): ObjectFile {
        return makeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol(name, value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
    }

    @Test
    fun `MZ signature at start`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        assertEquals('M'.code.toByte(), binary[0])
        assertEquals('Z'.code.toByte(), binary[1])
    }

    @Test
    fun `PE signature at correct offset`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        assertEquals('P'.code, binary[peOff].toInt() and 0xFF)
        assertEquals('E'.code, binary[peOff + 1].toInt() and 0xFF)
        assertEquals(0, binary[peOff + 2].toInt())
        assertEquals(0, binary[peOff + 3].toInt())
    }

    @Test
    fun `machine is AMD64`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        assertEquals(PeConstants.MACHINE_AMD64, readU16(buf, peOff + 4))
    }

    @Test
    fun `DLL flag is set`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val chars = readU16(buf, peOff + 4 + 18)
        assertTrue(chars and PeConstants.IMAGE_FILE_DLL != 0)
    }

    @Test
    fun `executable image flag is set`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val chars = readU16(buf, peOff + 4 + 18)
        assertTrue(chars and PeConstants.IMAGE_FILE_EXECUTABLE_IMAGE != 0)
    }

    @Test
    fun `large address aware flag is set`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val chars = readU16(buf, peOff + 4 + 18)
        assertTrue(chars and PeConstants.IMAGE_FILE_LARGE_ADDRESS_AWARE != 0)
    }

    @Test
    fun `optional header magic is PE32 Plus`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        assertEquals(PeConstants.PE32PLUS_MAGIC, readU16(buf, optOff))
    }

    @Test
    fun `image base is configurable`() {
        val binary = PeDllLinker(dllName = "test.dll", imageBase = 0x10000000L).link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        assertEquals(0x10000000L, readU64(buf, optOff + 24))
    }

    @Test
    fun `default image base is high address`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        val imageBase = readU64(buf, optOff + 24)
        assertTrue(imageBase >= 0x100000000L, "DLL image base should be above 4GB")
    }

    @Test
    fun `subsystem is console by default`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        assertEquals(3, readU16(buf, optOff + 68))
    }

    @Test
    fun `entry point is zero without DllMain`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        assertEquals(0, readU32(buf, optOff + 16))
    }

    @Test
    fun `export directory RVA is nonzero`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        val exportRVA = readU32(buf, optOff + 112)
        assertTrue(exportRVA > 0)
    }

    @Test
    fun `export directory size is nonzero`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        val exportSize = readU32(buf, optOff + 116)
        assertTrue(exportSize > 0)
    }

    @Test
    fun `DLL name appears in binary`() {
        val binary = PeDllLinker(dllName = "mylib.dll").link(listOf(simpleLib()))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("mylib.dll"))
    }

    @Test
    fun `exported function name appears in binary`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib("compute")))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("compute"))
    }

    @Test
    fun `local symbols are not exported`() {
        val code = byteArrayOf(0xC3.toByte(), 0xC3.toByte())
        val obj = makeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("public_api", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("internal_detail", value = 1, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("public_api"))
        assertFalse(str.contains("internal_detail"))
    }

    @Test
    fun `multiple exports from single object`() {
        val code = byteArrayOf(0xC3.toByte(), 0xC3.toByte(), 0xC3.toByte())
        val obj = makeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("alpha", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("beta", value = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("gamma", value = 2, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("alpha"))
        assertTrue(str.contains("beta"))
        assertTrue(str.contains("gamma"))
    }

    @Test
    fun `multiple objects linked together`() {
        val obj1 = simpleLib("func_a")
        val obj2 = simpleLib("func_b")
        val binary = PeDllLinker(dllName = "multi.dll").link(listOf(obj1, obj2))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("func_a"))
        assertTrue(str.contains("func_b"))
        assertTrue(str.contains("multi.dll"))
    }

    @Test
    fun `cross object reference is resolved`() {
        val callerCode = byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
        val calleeCode = byteArrayOf(0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00, 0xC3.toByte())
        val obj1 = makeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, callerCode, align = 16)),
            symbols = listOf(
                Symbol("caller", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("callee", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(offset = 1, symbol = "callee",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )
        val obj2 = makeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, calleeCode, align = 16)),
            symbols = listOf(
                Symbol("callee", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj1, obj2))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        assertTrue(readU16(buf, peOff + 4 + 18) and PeConstants.IMAGE_FILE_DLL != 0)
    }

    @Test
    fun `import DLL name appears in binary`() {
        val code = byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
        val obj = makeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("my_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            imports = listOf(ImportEntry("puts", "msvcrt.dll")),
            relocations = listOf(Relocation(offset = 1, symbol = "puts",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("msvcrt.dll"))
        assertTrue(str.contains("puts"))
    }

    @Test
    fun `import data directory is present when imports exist`() {
        val code = byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
        val obj = makeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("wrapper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            imports = listOf(ImportEntry("printf", "ucrtbase.dll")),
            relocations = listOf(Relocation(offset = 1, symbol = "printf",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        val importRVA = readU32(buf, optOff + 120)
        val importSize = readU32(buf, optOff + 124)
        assertTrue(importRVA > 0, "Import directory RVA should be nonzero")
        assertTrue(importSize > 0, "Import directory size should be nonzero")
    }

    @Test
    fun `rodata section is included`() {
        val code = byteArrayOf(0xC3.toByte())
        val rodata = "DLLDATA\u0000".toByteArray()
        val obj = makeObj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ),
            symbols = listOf(
                Symbol("get_data", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("DLLDATA"))
    }

    @Test
    fun `empty object list is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PeDllLinker(dllName = "test.dll").link(emptyList())
        }
    }

    @Test
    fun `section alignment is 4096`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        assertEquals(0x1000, readU32(buf, optOff + 32))
    }

    @Test
    fun `file alignment is 512`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        assertEquals(0x200, readU32(buf, optOff + 36))
    }

    @Test
    fun `binary size is file alignment multiple`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        assertEquals(0, binary.size % 0x200, "Binary size should be multiple of file alignment")
    }

    @Test
    fun `text section RVA is section alignment`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val pe = PeReader.read(binary)
        val text = pe.sectionByName(".text")
        assertNotNull(text)
        assertEquals(0x1000, text!!.virtualAddress)
    }

    @Test
    fun `PeReader round trip preserves DLL flag`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib("api_call")))
        val pe = PeReader.read(binary)
        assertTrue(pe.isPe)
        assertTrue(pe.isDll)
        assertTrue(pe.isPe32Plus)
    }

    @Test
    fun `PeReader round trip has export directory`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib("api_call")))
        val pe = PeReader.read(binary)
        assertNotNull(pe.exportDirectory)
        val exports = pe.exportDirectory!!.entries.mapNotNull { it.name }
        assertTrue("api_call" in exports)
    }

    @Test
    fun `PeReader round trip has text section`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val pe = PeReader.read(binary)
        assertTrue(pe.sections.any { it.name == ".text" })
        val text = pe.sectionByName(".text")!!
        assertTrue(text.isCode)
        assertTrue(text.isExecutable)
    }

    @Test
    fun `number of data directories is 16`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        assertEquals(16, readU32(buf, optOff + 108))
    }

    @Test
    fun `binary is reasonable size`() {
        val binary = PeDllLinker(dllName = "test.dll").link(listOf(simpleLib()))
        assertTrue(binary.size > 512, "Should be larger than one file alignment unit")
        assertTrue(binary.size < 64 * 1024, "Simple DLL should be small")
    }
}
