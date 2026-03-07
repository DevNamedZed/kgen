package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PeDllLinkerTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun peHeaderOffset(buf: ByteBuffer): Int = readU32(buf, 0x3C)

    private fun makeSimpleLib(): ObjectFile {
        // add: lea eax, [rcx+rdx]; ret  (Windows x64 calling convention)
        val code = byteArrayOf(
            0x8D.toByte(), 0x04, 0x11, // lea eax, [rcx+rdx]
            0xC3.toByte(),
        )
        return ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("add", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
    }

    @Test
    fun `produces PE DLL with MZ and PE signatures`() {
        val binary = PeDllLinker(dllName = "math.dll").link(listOf(makeSimpleLib()))

        assertEquals('M'.code.toByte(), binary[0])
        assertEquals('Z'.code.toByte(), binary[1])

        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        assertEquals('P'.code.toByte(), binary[peOff])
        assertEquals('E'.code.toByte(), binary[peOff + 1])
    }

    @Test
    fun `sets IMAGE_FILE_DLL flag`() {
        val binary = PeDllLinker(dllName = "math.dll").link(listOf(makeSimpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)

        val characteristics = readU16(buf, peOff + 4 + 18)
        assertTrue(characteristics and PeConstants.IMAGE_FILE_DLL != 0,
            "Should have IMAGE_FILE_DLL flag (0x${characteristics.toString(16)})")
        assertTrue(characteristics and PeConstants.IMAGE_FILE_EXECUTABLE_IMAGE != 0,
            "Should have IMAGE_FILE_EXECUTABLE_IMAGE flag")
    }

    @Test
    fun `has export directory`() {
        val binary = PeDllLinker(dllName = "math.dll").link(listOf(makeSimpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)

        // Optional header starts at peOff + 4 + 20
        val optOff = peOff + 24
        // Export table data directory is at offset 112 in optional header
        val exportRVA = readU32(buf, optOff + 112)
        val exportSize = readU32(buf, optOff + 116)

        assertTrue(exportRVA > 0, "Should have export directory RVA")
        assertTrue(exportSize > 0, "Should have non-zero export directory size")
    }

    @Test
    fun `exports function names`() {
        val binary = PeDllLinker(dllName = "math.dll").link(listOf(makeSimpleLib()))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("add"), "Binary should contain exported function name 'add'")
        assertTrue(str.contains("math.dll"), "Binary should contain DLL name")
    }

    @Test
    fun `exports multiple functions from multiple objects`() {
        val addCode = byteArrayOf(0x8D.toByte(), 0x04, 0x11, 0xC3.toByte())
        val obj1 = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, addCode, align = 16)),
            symbols = listOf(
                Symbol("add", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val subCode = byteArrayOf(
            0x89.toByte(), 0xC8.toByte(), // mov eax, ecx
            0x29, 0xD0.toByte(),           // sub eax, edx
            0xC3.toByte(),
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

        val binary = PeDllLinker(dllName = "calc.dll").link(listOf(obj1, obj2))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("add"), "Should export 'add'")
        assertTrue(str.contains("sub"), "Should export 'sub'")
        assertTrue(str.contains("calc.dll"), "Should contain DLL name")
    }

    @Test
    fun `handles cross-object references`() {
        val wrapperCode = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call internal
            0xC3.toByte(),
        )
        val obj1 = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, wrapperCode, align = 16)),
            symbols = listOf(
                Symbol("wrapper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("internal_func", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "internal_func", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        val internalCode = byteArrayOf(0xB8.toByte(), 0x07, 0x00, 0x00, 0x00, 0xC3.toByte())
        val obj2 = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, internalCode, align = 16)),
            symbols = listOf(
                Symbol("internal_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj1, obj2))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val characteristics = readU16(buf, peOff + 4 + 18)
        assertTrue(characteristics and PeConstants.IMAGE_FILE_DLL != 0)
    }

    @Test
    fun `supports DLL imports`() {
        val code = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call printf
            0xC3.toByte(),
        )
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("my_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            imports = listOf(ImportEntry("printf", "ucrtbase.dll")),
            relocations = listOf(
                Relocation(offset = 1, symbol = "printf", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )

        val binary = PeDllLinker(dllName = "mylib.dll").link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("printf"), "Should contain imported function")
        assertTrue(str.contains("ucrtbase.dll"), "Should reference import DLL")
        assertTrue(str.contains("my_func"), "Should export defined function")
    }

    @Test
    fun `entry point is zero when no DllMain`() {
        val binary = PeDllLinker(dllName = "math.dll").link(listOf(makeSimpleLib()))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        val entryPoint = readU32(buf, optOff + 16)
        assertEquals(0, entryPoint, "Entry point should be 0 when no DllMain")
    }

    @Test
    fun `local symbols are not exported`() {
        val code = byteArrayOf(0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code + code, align = 16)),
            symbols = listOf(
                Symbol("public_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("private_helper", value = 1, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("public_func"), "Should export public symbol")
        assertFalse(str.contains("private_helper"), "Should not export local symbol")
    }
}
