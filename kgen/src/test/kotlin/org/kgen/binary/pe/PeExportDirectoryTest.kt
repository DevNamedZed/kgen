package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PeExportDirectoryTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun peHeaderOffset(buf: ByteBuffer): Int = readU32(buf, 0x3C)

    @Test
    fun `writeDll produces valid PE with DLL flag`() {
        val code = byteArrayOf(0xC3.toByte())
        val binary = PeWriter.writeDll(code, exportNames = listOf("myFunc"), dllName = "test.dll")
        val buf = le(binary)

        assertEquals('M'.code.toByte(), binary[0])
        assertEquals('Z'.code.toByte(), binary[1])

        val peOff = peHeaderOffset(buf)
        assertEquals('P'.code, binary[peOff].toInt() and 0xFF)
        assertEquals('E'.code, binary[peOff + 1].toInt() and 0xFF)

        val characteristics = readU16(buf, peOff + 4 + 18)
        assertTrue(characteristics and PeConstants.IMAGE_FILE_DLL != 0,
            "Should have IMAGE_FILE_DLL flag")
        assertTrue(characteristics and PeConstants.IMAGE_FILE_EXECUTABLE_IMAGE != 0,
            "Should have IMAGE_FILE_EXECUTABLE_IMAGE flag")
    }

    @Test
    fun `writeDll sets export data directory entry`() {
        val code = byteArrayOf(0xC3.toByte())
        val binary = PeWriter.writeDll(code, exportNames = listOf("add"), dllName = "math.dll")
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24

        val exportRVA = readU32(buf, optOff + 112)
        val exportSize = readU32(buf, optOff + 116)

        assertTrue(exportRVA > 0, "Export directory RVA should be nonzero")
        assertTrue(exportSize > 0, "Export directory size should be nonzero")
    }

    @Test
    fun `writeDll round trip via PeReader parses export directory`() {
        val code = byteArrayOf(
            0x8D.toByte(), 0x04, 0x11, // lea eax, [rcx+rdx]
            0xC3.toByte(),
        )
        val binary = PeWriter.writeDll(code, exportNames = listOf("add", "multiply"), dllName = "calc.dll")
        val pe = PeReader.read(binary)

        assertTrue(pe.isPe)
        assertTrue(pe.isDll)
        assertTrue(pe.isPe32Plus)

        val exportDir = pe.exportDirectory
        assertNotNull(exportDir, "Should have parsed export directory")
        assertEquals("calc.dll", exportDir!!.name)
        assertEquals(1, exportDir.ordinalBase)
        assertEquals(2, exportDir.entries.size)

        val names = exportDir.entries.mapNotNull { it.name }.sorted()
        assertEquals(listOf("add", "multiply"), names)
    }

    @Test
    fun `writeDll exports are sorted alphabetically`() {
        val code = byteArrayOf(0xC3.toByte(), 0xC3.toByte(), 0xC3.toByte())
        val exports = listOf("zeta", "alpha", "mid")
        val binary = PeWriter.writeDll(code, exportNames = exports, dllName = "test.dll")
        val pe = PeReader.read(binary)

        val exportDir = pe.exportDirectory!!
        val names = exportDir.entries.mapNotNull { it.name }
        assertEquals(listOf("alpha", "mid", "zeta"), names,
            "Exports should be sorted alphabetically for binary search lookup")
    }

    @Test
    fun `writeDll export ordinals are sequential from base`() {
        val code = byteArrayOf(0xC3.toByte(), 0xC3.toByte())
        val binary = PeWriter.writeDll(code, exportNames = listOf("func_a", "func_b"), dllName = "test.dll")
        val pe = PeReader.read(binary)

        val exportDir = pe.exportDirectory!!
        assertEquals(1, exportDir.ordinalBase)
        val ordinals = exportDir.entries.map { it.ordinal }
        assertEquals(listOf(1, 2), ordinals, "Ordinals should be sequential from ordinalBase")
    }

    @Test
    fun `writeDll with no exports produces no edata section`() {
        val code = byteArrayOf(0xC3.toByte())
        val binary = PeWriter.writeDll(code, exportNames = emptyList(), dllName = "empty.dll")
        val pe = PeReader.read(binary)

        assertTrue(pe.isDll, "Should still be a DLL")
        assertNull(pe.exportDirectory, "Should have no export directory")
        assertNull(pe.sectionByName(".edata"), "Should have no .edata section")
    }

    @Test
    fun `writeDll has edata section with correct characteristics`() {
        val code = byteArrayOf(0xC3.toByte())
        val binary = PeWriter.writeDll(code, exportNames = listOf("foo"), dllName = "test.dll")
        val pe = PeReader.read(binary)

        val edata = pe.sectionByName(".edata")
        assertNotNull(edata, "Should have .edata section")
        assertTrue(edata!!.isReadable, ".edata should be readable")
        assertTrue(edata.isInitializedData, ".edata should be initialized data")
        assertFalse(edata.isWritable, ".edata should not be writable")
        assertFalse(edata.isExecutable, ".edata should not be executable")
    }

    @Test
    fun `writeDll export function RVAs point into text section`() {
        val code = byteArrayOf(0xC3.toByte())
        val binary = PeWriter.writeDll(code, exportNames = listOf("entry"), dllName = "test.dll")
        val pe = PeReader.read(binary)

        val text = pe.sectionByName(".text")!!
        val exportDir = pe.exportDirectory!!
        for (entry in exportDir.entries) {
            assertTrue(entry.rva >= text.virtualAddress,
                "Export RVA ${entry.rva} should be >= text RVA ${text.virtualAddress}")
            assertTrue(entry.rva < text.virtualAddress + text.virtualSize,
                "Export RVA ${entry.rva} should be within text section")
        }
    }

    @Test
    fun `write ObjectFile with DLL flag produces DLL with exports`() {
        val code = byteArrayOf(0xC3.toByte(), 0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 1)),
            symbols = listOf(
                Symbol("public_fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("private_fn", value = 1, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
            exports = listOf(ExportEntry(symbolName = "public_fn")),
            metadata = ObjectMetadata(
                flags = setOf(ObjectFlag.DLL),
                moduleName = "mylib.dll",
            ),
        )
        val binary = PeWriter.write(obj)
        val pe = PeReader.read(binary)

        assertTrue(pe.isDll, "Should produce a DLL")
        assertNotNull(pe.exportDirectory, "Should have export directory")
        val names = pe.exportDirectory!!.entries.mapNotNull { it.name }
        assertTrue("public_fn" in names, "Should export public_fn")
    }

    @Test
    fun `write ObjectFile with exports list auto-selects DLL mode`() {
        val code = byteArrayOf(0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 1)),
            symbols = emptyList(),
            relocations = emptyList(),
            exports = listOf(ExportEntry(symbolName = "api_func")),
        )
        val binary = PeWriter.write(obj)
        val pe = PeReader.read(binary)

        assertTrue(pe.isDll, "Should auto-select DLL mode when exports present")
        assertNotNull(pe.exportDirectory)
        val names = pe.exportDirectory!!.entries.mapNotNull { it.name }
        assertTrue("api_func" in names)
    }

    @Test
    fun `PeDllLinker export directory round trip with multiple objects`() {
        val addCode = byteArrayOf(0x8D.toByte(), 0x04, 0x11, 0xC3.toByte())
        val subCode = byteArrayOf(0x89.toByte(), 0xC8.toByte(), 0x29, 0xD0.toByte(), 0xC3.toByte())

        val obj1 = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, addCode, align = 16)),
            symbols = listOf(
                Symbol("add", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val obj2 = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, subCode, align = 16)),
            symbols = listOf(
                Symbol("sub", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = PeDllLinker(dllName = "arith.dll").link(listOf(obj1, obj2))
        val pe = PeReader.read(binary)

        val exportDir = pe.exportDirectory!!
        assertEquals("arith.dll", exportDir.name)
        assertEquals(1, exportDir.ordinalBase)

        val names = exportDir.entries.mapNotNull { it.name }.sorted()
        assertEquals(listOf("add", "sub"), names)

        // Verify each export has a valid RVA pointing into .text
        val text = pe.sectionByName(".text")!!
        for (entry in exportDir.entries) {
            assertTrue(entry.rva >= text.virtualAddress,
                "Export '${entry.name}' RVA should be within .text")
        }
    }

    @Test
    fun `PeDllLinker local symbols excluded from exports`() {
        val code = byteArrayOf(0xC3.toByte(), 0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("exported_api", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("internal_helper", value = 1, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
        val pe = PeReader.read(binary)

        val names = pe.exportDirectory!!.entries.mapNotNull { it.name }
        assertTrue("exported_api" in names, "GLOBAL symbols should be exported")
        assertFalse("internal_helper" in names, "LOCAL symbols should not be exported")
    }

    @Test
    fun `PeDllLinker export directory structure is well-formed`() {
        val code = byteArrayOf(0xC3.toByte(), 0xC3.toByte(), 0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("alpha", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("beta", value = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("gamma", value = 2, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = PeDllLinker(dllName = "greek.dll").link(listOf(obj))
        val pe = PeReader.read(binary)

        val exportDir = pe.exportDirectory!!
        assertEquals("greek.dll", exportDir.name)
        assertEquals(3, exportDir.entries.size)
        assertEquals(0, exportDir.majorVersion)
        assertEquals(0, exportDir.minorVersion)

        // Names should be sorted for binary search
        val names = exportDir.entries.mapNotNull { it.name }
        assertEquals(listOf("alpha", "beta", "gamma"), names)

        // No forwarders in our output
        assertTrue(exportDir.entries.none { it.isForwarder },
            "Simple DLL should have no forwarder entries")
    }
}
