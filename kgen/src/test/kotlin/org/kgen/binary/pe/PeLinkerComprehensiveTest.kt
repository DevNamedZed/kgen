package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PeLinkerComprehensiveTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readI32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun peHeaderOffset(buf: ByteBuffer): Int = readU32(buf, 0x3C)

    private fun x86Obj(
        textCode: ByteArray,
        symbols: List<Symbol>,
        relocations: List<Relocation> = emptyList(),
        sections: List<Section>? = null,
        imports: List<ImportEntry> = emptyList(),
    ) = ObjectFile(
        format = ObjectFormat.PE_COFF,
        arch = Architecture(ArchType.X86_64),
        sections = sections ?: listOf(Section(".text", SectionKind.TEXT, textCode, align = 16)),
        symbols = symbols,
        relocations = relocations,
        imports = imports,
    )

    private fun retCode() = byteArrayOf(0xC3.toByte())
    private fun nopCode(n: Int) = ByteArray(n) { 0x90.toByte() }
    private fun movEaxImm(v: Int) = byteArrayOf(
        0xB8.toByte(),
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )

    private fun callPlaceholder() = byteArrayOf(
        0xE8.toByte(), 0x00, 0x00, 0x00, 0x00,
    )

    private fun leaRipPlaceholder() = byteArrayOf(
        0x48, 0x8D.toByte(), 0x3D, 0x00, 0x00, 0x00, 0x00,
    )

    private fun verifyMzSignature(binary: ByteArray) {
        assertEquals('M'.code.toByte(), binary[0])
        assertEquals('Z'.code.toByte(), binary[1])
    }

    private fun verifyPeSignature(binary: ByteArray) {
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        assertEquals('P'.code.toByte(), binary[peOff])
        assertEquals('E'.code.toByte(), binary[peOff + 1])
        assertEquals(0, binary[peOff + 2].toInt())
        assertEquals(0, binary[peOff + 3].toInt())
    }

    @Nested
    inner class `PE executable basic structure` {

        @Test
        fun `produces MZ signature`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            verifyMzSignature(binary)
        }

        @Test
        fun `produces PE signature`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            verifyPeSignature(binary)
        }

        @Test
        fun `machine type is AMD64`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            assertEquals(PeConstants.MACHINE_AMD64, readU16(buf, peOff + 4))
        }

        @Test
        fun `PE32+ magic present`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(PeConstants.PE32PLUS_MAGIC, readU16(buf, optOff))
        }

        @Test
        fun `has EXECUTABLE_IMAGE flag`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            val characteristics = readU16(buf, peOff + 4 + 18)
            assertTrue(characteristics and PeConstants.IMAGE_FILE_EXECUTABLE_IMAGE != 0)
        }

        @Test
        fun `does not have DLL flag`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            val characteristics = readU16(buf, peOff + 4 + 18)
            assertTrue(characteristics and PeConstants.IMAGE_FILE_DLL == 0)
        }

        @Test
        fun `section alignment is 0x1000`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(0x1000, readU32(buf, optOff + 32))
        }

        @Test
        fun `file alignment is 0x200`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(0x200, readU32(buf, optOff + 36))
        }

        @Test
        fun `image base is default 0x140000000`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(0x140000000L, readU64(buf, optOff + 24))
        }

        @Test
        fun `subsystem is CONSOLE by default`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(3, readU16(buf, optOff + 68))
        }

        @Test
        fun `custom subsystem windows gui`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker(subsystem = 2).link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(2, readU16(buf, optOff + 68))
        }

        @Test
        fun `text section RVA is 0x1000`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(0x1000, readU32(buf, optOff + 20))
        }

        @Test
        fun `binary size is file aligned`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            assertEquals(0, binary.size % 0x200, "Binary size should be file-aligned")
        }

        @Test
        fun `DOS header e_lfanew points to PE signature`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val peOff = readU32(buf, 0x3C)
            assertEquals(64, peOff)
        }
    }

    @Nested
    inner class `PE executable entry point` {

        @Test
        fun `entry point set for _start`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val ep = readU32(buf, optOff + 16)
            assertEquals(0x1000, ep)
        }

        @Test
        fun `entry point for main with ExitProcess generates stub`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val ep = readU32(buf, optOff + 16)
            assertEquals(0x1000, ep, "Entry stub at start of .text")
        }

        @Test
        fun `entry point non-zero`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val ep = readU32(buf, optOff + 16)
            assertTrue(ep > 0)
        }
    }

    @Nested
    inner class `PE import handling` {

        @Test
        fun `import directory present when imports exist`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("MessageBoxA", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "MessageBoxA", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("MessageBoxA", "user32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val importRVA = readU32(buf, optOff + 120)
            val importSize = readU32(buf, optOff + 124)
            assertTrue(importRVA > 0, "Import directory RVA should be non-zero")
            assertTrue(importSize > 0, "Import directory size should be non-zero")
        }

        @Test
        fun `imported function name in binary`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "ExitProcess", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("ExitProcess"))
        }

        @Test
        fun `imported DLL name in binary`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "ExitProcess", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("kernel32.dll"))
        }

        @Test
        fun `multiple imports from same DLL`() {
            val code = callPlaceholder() + callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("GetStdHandle", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("WriteFile", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(
                Relocation(1, "GetStdHandle", RelocationType.X86_64.PLT32, -4, ".text"),
                Relocation(6, "WriteFile", RelocationType.X86_64.PLT32, -4, ".text"),
            ), imports = listOf(
                ImportEntry("GetStdHandle", "kernel32.dll"),
                ImportEntry("WriteFile", "kernel32.dll"),
            ))
            val binary = PeLinker().link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("GetStdHandle"))
            assertTrue(str.contains("WriteFile"))
        }

        @Test
        fun `imports from multiple DLLs`() {
            val code = callPlaceholder() + callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("MessageBoxA", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(
                Relocation(1, "ExitProcess", RelocationType.X86_64.PLT32, -4, ".text"),
                Relocation(6, "MessageBoxA", RelocationType.X86_64.PLT32, -4, ".text"),
            ), imports = listOf(
                ImportEntry("ExitProcess", "kernel32.dll"),
                ImportEntry("MessageBoxA", "user32.dll"),
            ))
            val binary = PeLinker().link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("kernel32.dll"))
            assertTrue(str.contains("user32.dll"))
        }

        @Test
        fun `IAT directory entry present`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "ExitProcess", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val iatRVA = readU32(buf, optOff + 112 + 96)
            val iatSize = readU32(buf, optOff + 112 + 100)
            assertTrue(iatRVA > 0)
            assertTrue(iatSize > 0)
        }

        @Test
        fun `no import directory when no imports`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val importRVA = readU32(buf, optOff + 120)
            assertEquals(0, importRVA)
        }

        @Test
        fun `call to import is patched`() {
            val code = callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "ExitProcess", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val headersSize = readU32(buf, optOff + 60)
            val disp = readI32(buf, headersSize + 1)
            assertNotEquals(0, disp, "Call displacement should be patched")
        }
    }

    @Nested
    inner class `PE section handling` {

        @Test
        fun `text section header present`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains(".text"))
        }

        @Test
        fun `rdata section present when rodata exists`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, retCode(), align = 16),
                Section(".rodata", SectionKind.RODATA, "test\u0000".toByteArray(), align = 1),
            ))
            val binary = PeLinker().link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains(".rdata"))
        }

        @Test
        fun `rodata contents in binary`() {
            val rodata = "Hello PE World\u0000".toByteArray()
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, retCode(), align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ))
            val binary = PeLinker().link(listOf(obj))
            assertTrue(String(binary, Charsets.US_ASCII).contains("Hello PE World"))
        }

        @Test
        fun `idata section present with imports`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "ExitProcess", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains(".idata"))
        }

        @Test
        fun `multiple text sections merged from objects`() {
            val obj1 = x86Obj(nopCode(5) + retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val obj2 = x86Obj(movEaxImm(42) + retCode(), listOf(
                Symbol("func2", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj1, obj2))
            verifyMzSignature(binary)
            verifyPeSignature(binary)
        }

        @Test
        fun `multiple rodata sections merged`() {
            val obj1 = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, retCode(), align = 16),
                Section(".rodata", SectionKind.RODATA, "first\u0000".toByteArray(), align = 1),
            ))
            val obj2 = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, retCode(), align = 16),
                Section(".rodata", SectionKind.RODATA, "second\u0000".toByteArray(), align = 1),
            ))
            val binary = PeLinker().link(listOf(obj1, obj2))
            val str = String(binary, Charsets.US_ASCII)
            assertTrue(str.contains("first"))
            assertTrue(str.contains("second"))
        }

        @Test
        fun `section count correct without rodata or imports`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            val numSections = readU16(buf, peOff + 4 + 2)
            assertEquals(1, numSections, "Only .text section")
        }

        @Test
        fun `section count correct with rodata`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, retCode(), align = 16),
                Section(".rodata", SectionKind.RODATA, "data\u0000".toByteArray(), align = 1),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            val numSections = readU16(buf, peOff + 4 + 2)
            assertEquals(2, numSections, ".text + .rdata")
        }

        @Test
        fun `section count correct with imports`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "ExitProcess", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            val numSections = readU16(buf, peOff + 4 + 2)
            assertEquals(2, numSections, ".text + .idata")
        }
    }

    @Nested
    inner class `PE symbol resolution` {

        @Test
        fun `cross-object function call resolved`() {
            val code1 = callPlaceholder() + retCode()
            val code2 = movEaxImm(42) + retCode()
            val obj1 = x86Obj(code1, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "helper", RelocationType.X86_64.PC32, -4, ".text")))
            val obj2 = x86Obj(code2, listOf(
                Symbol("helper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj1, obj2))
            verifyMzSignature(binary)
            verifyPeSignature(binary)
        }

        @Test
        fun `relocation to rodata symbol`() {
            val code = leaRipPlaceholder() + retCode()
            val rodata = "msg\u0000".toByteArray()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("str", value = 0, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ), listOf(Relocation(3, "str", RelocationType.X86_64.PC32, -4, ".text")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".rodata", SectionKind.RODATA, rodata, align = 1),
                ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val headersSize = readU32(buf, optOff + 60)
            assertNotEquals(0, readI32(buf, headersSize + 3))
        }

        @Test
        fun `three objects linked with transitive calls`() {
            val code1 = callPlaceholder() + retCode()
            val code2 = callPlaceholder() + retCode()
            val code3 = movEaxImm(7) + retCode()
            val obj1 = x86Obj(code1, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("foo", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "foo", RelocationType.X86_64.PC32, -4, ".text")))
            val obj2 = x86Obj(code2, listOf(
                Symbol("foo", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("bar", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "bar", RelocationType.X86_64.PC32, -4, ".text")))
            val obj3 = x86Obj(code3, listOf(
                Symbol("bar", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj1, obj2, obj3))
            verifyMzSignature(binary)
        }

        @Test
        fun `undefined symbol throws`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("missing", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "missing", RelocationType.X86_64.PC32, -4, ".text")))
            assertThrows(IllegalStateException::class.java) {
                PeLinker().link(listOf(obj))
            }
        }

        @Test
        fun `symbol at offset within text section`() {
            val code = nopCode(16) + movEaxImm(99) + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val ep = readU32(buf, optOff + 16)
            assertTrue(ep >= 0x1000)
        }
    }

    @Nested
    inner class `DLL basic structure` {

        @Test
        fun `produces MZ and PE signatures`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            verifyMzSignature(binary)
            verifyPeSignature(binary)
        }

        @Test
        fun `has IMAGE_FILE_DLL flag`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            val characteristics = readU16(buf, peOff + 4 + 18)
            assertTrue(characteristics and PeConstants.IMAGE_FILE_DLL != 0)
        }

        @Test
        fun `has IMAGE_FILE_EXECUTABLE_IMAGE flag`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            val characteristics = readU16(buf, peOff + 4 + 18)
            assertTrue(characteristics and PeConstants.IMAGE_FILE_EXECUTABLE_IMAGE != 0)
        }

        @Test
        fun `DLL image base default is 0x180000000`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(0x180000000L, readU64(buf, optOff + 24))
        }

        @Test
        fun `custom DLL image base`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll", imageBase = 0x10000000L).link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(0x10000000L, readU64(buf, optOff + 24))
        }

        @Test
        fun `entry point zero when no DllMain`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(0, readU32(buf, optOff + 16))
        }

        @Test
        fun `entry point set when DllMain exists`() {
            val code = retCode()
            val obj = x86Obj(code, listOf(
                Symbol("DllMain", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val ep = readU32(buf, optOff + 16)
            assertTrue(ep > 0, "DllMain entry point should be set")
        }

        @Test
        fun `binary is file aligned`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            assertEquals(0, binary.size % 0x200)
        }
    }

    @Nested
    inner class `DLL export handling` {

        @Test
        fun `export directory present`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val exportRVA = readU32(buf, optOff + 112)
            val exportSize = readU32(buf, optOff + 116)
            assertTrue(exportRVA > 0)
            assertTrue(exportSize > 0)
        }

        @Test
        fun `exported function name in binary`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("my_function", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("my_function"))
        }

        @Test
        fun `DLL name in binary`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "mylib.dll").link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("mylib.dll"))
        }

        @Test
        fun `multiple exports`() {
            val code = retCode() + retCode() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("func_a", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("func_b", value = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("func_c", value = 2, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "multi.dll").link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("func_a"))
            assertTrue(str.contains("func_b"))
            assertTrue(str.contains("func_c"))
        }

        @Test
        fun `local symbols not exported`() {
            val code = retCode() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("public_fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("private_fn", value = 1, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("public_fn"))
            assertFalse(str.contains("private_fn"))
        }

        @Test
        fun `exports from multiple objects`() {
            val obj1 = x86Obj(retCode(), listOf(
                Symbol("add", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val obj2 = x86Obj(retCode(), listOf(
                Symbol("sub", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "calc.dll").link(listOf(obj1, obj2))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("add"))
            assertTrue(str.contains("sub"))
            assertTrue(str.contains("calc.dll"))
        }

        @Test
        fun `edata section present`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains(".edata"))
        }
    }

    @Nested
    inner class `DLL import handling` {

        @Test
        fun `DLL supports imports`() {
            val code = callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("my_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "printf", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("printf", "ucrtbase.dll")))
            val binary = PeDllLinker(dllName = "mylib.dll").link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("printf"))
            assertTrue(str.contains("ucrtbase.dll"))
            assertTrue(str.contains("my_func"))
        }

        @Test
        fun `DLL with both exports and imports`() {
            val code = callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("wrapper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("malloc", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "malloc", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("malloc", "ucrtbase.dll")))
            val binary = PeDllLinker(dllName = "alloc.dll").link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            // Has export directory
            assertTrue(readU32(buf, optOff + 112) > 0)
            // Has import directory
            assertTrue(readU32(buf, optOff + 120) > 0)
        }

        @Test
        fun `DLL imports from multiple DLLs`() {
            val code = callPlaceholder() + callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("my_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("HeapAlloc", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(
                Relocation(1, "HeapAlloc", RelocationType.X86_64.PLT32, -4, ".text"),
                Relocation(6, "printf", RelocationType.X86_64.PLT32, -4, ".text"),
            ), imports = listOf(
                ImportEntry("HeapAlloc", "kernel32.dll"),
                ImportEntry("printf", "ucrtbase.dll"),
            ))
            val binary = PeDllLinker(dllName = "mixed.dll").link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("kernel32.dll"))
            assertTrue(str.contains("ucrtbase.dll"))
        }
    }

    @Nested
    inner class `DLL cross-object references` {

        @Test
        fun `cross-object internal call resolved`() {
            val code1 = callPlaceholder() + retCode()
            val code2 = movEaxImm(7) + retCode()
            val obj1 = x86Obj(code1, listOf(
                Symbol("wrapper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("internal_func", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "internal_func", RelocationType.X86_64.PC32, -4, ".text")))
            val obj2 = x86Obj(code2, listOf(
                Symbol("internal_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj1, obj2))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            assertTrue(readU16(buf, peOff + 4 + 18) and PeConstants.IMAGE_FILE_DLL != 0)
        }

        @Test
        fun `three DLL objects with chain calls`() {
            val code1 = callPlaceholder() + retCode()
            val code2 = callPlaceholder() + retCode()
            val code3 = movEaxImm(99) + retCode()
            val obj1 = x86Obj(code1, listOf(
                Symbol("entry", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("mid", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "mid", RelocationType.X86_64.PC32, -4, ".text")))
            val obj2 = x86Obj(code2, listOf(
                Symbol("mid", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("leaf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "leaf", RelocationType.X86_64.PC32, -4, ".text")))
            val obj3 = x86Obj(code3, listOf(
                Symbol("leaf", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "chain.dll").link(listOf(obj1, obj2, obj3))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("entry"))
            assertTrue(str.contains("mid"))
            assertTrue(str.contains("leaf"))
        }
    }

    @Nested
    inner class `PE optional header fields` {

        @Test
        fun `optional header size is 240`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            assertEquals(240, readU16(buf, peOff + 4 + 16))
        }

        @Test
        fun `number of RVA and sizes is 16`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(16, readU32(buf, optOff + 108))
        }

        @Test
        fun `image size is section aligned`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val imageSize = readU32(buf, optOff + 56)
            assertEquals(0, imageSize % 0x1000, "Image size should be section-aligned")
        }

        @Test
        fun `headers size is file aligned`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val headersSize = readU32(buf, optOff + 60)
            assertEquals(0, headersSize % 0x200, "Headers size should be file-aligned")
        }

        @Test
        fun `major OS version is 6`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(6, readU16(buf, optOff + 40))
        }

        @Test
        fun `stack reserve size set`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val stackReserve = readU64(buf, optOff + 72)
            assertTrue(stackReserve > 0)
        }
    }

    @Nested
    inner class `PE relocation types` {

        @Test
        fun `PC32 relocation applied`() {
            val code = callPlaceholder() + retCode()
            val helperCode = movEaxImm(42) + retCode()
            val obj1 = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "helper", RelocationType.X86_64.PC32, -4, ".text")))
            val obj2 = x86Obj(helperCode, listOf(
                Symbol("helper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj1, obj2))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val headersSize = readU32(buf, optOff + 60)
            val disp = readI32(buf, headersSize + 1)
            assertNotEquals(0, disp)
        }

        @Test
        fun `PLT32 relocation to import thunk`() {
            val code = callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "ExitProcess", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val headersSize = readU32(buf, optOff + 60)
            val disp = readI32(buf, headersSize + 1)
            assertNotEquals(0, disp)
        }

        @Test
        fun `COFF REL32 relocation supported in DLL`() {
            val code = callPlaceholder() + retCode()
            val helperCode = retCode()
            val obj1 = x86Obj(code, listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "helper", RelocationType.COFF_X86_64.REL32, -4, ".text")))
            val obj2 = x86Obj(helperCode, listOf(
                Symbol("helper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj1, obj2))
            verifyPeSignature(binary)
        }
    }

    @Nested
    inner class `PE edge cases` {

        @Test
        fun `single byte function`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            verifyMzSignature(binary)
            verifyPeSignature(binary)
        }

        @Test
        fun `large text section`() {
            val code = nopCode(8192) + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            assertTrue(binary.size > 8192)
        }

        @Test
        fun `ten objects linked`() {
            val objs = mutableListOf<ObjectFile>()
            for (i in 0 until 10) {
                val name = if (i == 0) "_start" else "f$i"
                val nextName = if (i < 9) "f${i + 1}" else null
                val code = if (nextName != null) callPlaceholder() + retCode() else retCode()
                val syms = mutableListOf(Symbol(name, value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION))
                if (nextName != null) syms.add(Symbol(nextName, value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED))
                val rels = if (nextName != null)
                    listOf(Relocation(1, nextName, RelocationType.X86_64.PC32, -4, ".text"))
                else emptyList()
                objs.add(x86Obj(code, syms, rels))
            }
            val binary = PeLinker().link(objs)
            verifyMzSignature(binary)
        }

        @Test
        fun `many exported functions in DLL`() {
            val code = ByteArray(20) { 0xC3.toByte() }
            val symbols = (0 until 20).map {
                Symbol("export_$it", value = it.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            }
            val obj = x86Obj(code, symbols)
            val binary = PeDllLinker(dllName = "many.dll").link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            for (i in 0 until 20) {
                assertTrue(str.contains("export_$i"), "Should export export_$i")
            }
        }

        @Test
        fun `empty DLL with no exports`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("internal", value = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "empty.dll").link(listOf(obj))
            verifyMzSignature(binary)
            verifyPeSignature(binary)
        }

        @Test
        fun `DLL rejects empty object list`() {
            assertThrows(IllegalArgumentException::class.java) {
                PeDllLinker(dllName = "test.dll").link(emptyList())
            }
        }

        @Test
        fun `custom DLL name in export directory`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "my_custom_library.dll").link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("my_custom_library.dll"))
        }
    }

    @Nested
    inner class `PE with rodata` {

        @Test
        fun `DLL with rodata section`() {
            val code = leaRipPlaceholder() + retCode()
            val rodata = "dll string data\u0000".toByteArray()
            val obj = x86Obj(code, listOf(
                Symbol("get_str", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("str", value = 0, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ), listOf(Relocation(3, "str", RelocationType.X86_64.PC32, -4, ".text")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".rodata", SectionKind.RODATA, rodata, align = 1),
                ))
            val binary = PeDllLinker(dllName = "strlib.dll").link(listOf(obj))
            assertTrue(String(binary, Charsets.US_ASCII).contains("dll string data"))
        }

        @Test
        fun `executable with rodata relocation patched`() {
            val code = leaRipPlaceholder() + retCode()
            val rodata = "test\u0000".toByteArray()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("msg", value = 0, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ), listOf(Relocation(3, "msg", RelocationType.X86_64.PC32, -4, ".text")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".rodata", SectionKind.RODATA, rodata, align = 1),
                ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val headersSize = readU32(buf, optOff + 60)
            assertNotEquals(0, readI32(buf, headersSize + 3))
        }
    }

    @Nested
    inner class `PE size validation` {

        @Test
        fun `executable binary non-trivial`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            assertTrue(binary.size > 500)
            assertTrue(binary.size < 50000)
        }

        @Test
        fun `DLL binary non-trivial`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            assertTrue(binary.size > 500)
            assertTrue(binary.size < 50000)
        }

        @Test
        fun `DLL with imports larger than without`() {
            val codeNoImport = retCode()
            val codeWithImport = callPlaceholder() + retCode()
            val objNoImport = x86Obj(codeNoImport, listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val objWithImport = x86Obj(codeWithImport, listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "printf", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("printf", "ucrtbase.dll")))
            val binNoImport = PeDllLinker(dllName = "a.dll").link(listOf(objNoImport))
            val binWithImport = PeDllLinker(dllName = "b.dll").link(listOf(objWithImport))
            assertTrue(binWithImport.size > binNoImport.size)
        }
    }

    @Nested
    inner class `PE custom image base` {

        @Test
        fun `custom image base for executable`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker(imageBase = 0x400000L).link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(0x400000L, readU64(buf, optOff + 24))
        }

        @Test
        fun `custom image base for DLL`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll", imageBase = 0x70000000L).link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(0x70000000L, readU64(buf, optOff + 24))
        }
    }

    @Nested
    inner class `PE multi-object merging` {

        @Test
        fun `five objects merged correctly into executable`() {
            val objs = (0 until 5).map { i ->
                val name = if (i == 0) "_start" else "fn$i"
                x86Obj(movEaxImm(i) + retCode(), listOf(
                    Symbol(name, value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ))
            }
            val binary = PeLinker().link(objs)
            verifyMzSignature(binary)
            verifyPeSignature(binary)
        }

        @Test
        fun `five objects merged correctly into DLL`() {
            val objs = (0 until 5).map { i ->
                x86Obj(movEaxImm(i) + retCode(), listOf(
                    Symbol("export$i", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                ))
            }
            val binary = PeDllLinker(dllName = "big.dll").link(objs)
            val str = String(binary, Charsets.ISO_8859_1)
            for (i in 0 until 5) {
                assertTrue(str.contains("export$i"))
            }
        }

        @Test
        fun `imports collected from multiple objects`() {
            val obj1 = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("FuncA", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "FuncA", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("FuncA", "libA.dll")))
            val obj2 = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("fn2", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("FuncB", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "FuncB", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("FuncB", "libB.dll")))
            val binary = PeLinker().link(listOf(obj1, obj2))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("FuncA"))
            assertTrue(str.contains("FuncB"))
            assertTrue(str.contains("libA.dll"))
            assertTrue(str.contains("libB.dll"))
        }
    }

    @Nested
    inner class `PE thunk generation` {

        @Test
        fun `thunks appended after user code`() {
            val code = callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "ExitProcess", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val headersSize = readU32(buf, optOff + 60)
            // Thunk should be after the user code (FF 25 pattern)
            var foundThunk = false
            for (i in headersSize + code.size until binary.size - 2) {
                if (binary[i] == 0xFF.toByte() && binary[i + 1] == 0x25.toByte()) {
                    foundThunk = true
                    break
                }
            }
            assertTrue(foundThunk, "Should find a jmp [rip+disp32] thunk")
        }

        @Test
        fun `multiple thunks for multiple imports`() {
            val code = callPlaceholder() + callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("FuncA", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("FuncB", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(
                Relocation(1, "FuncA", RelocationType.X86_64.PLT32, -4, ".text"),
                Relocation(6, "FuncB", RelocationType.X86_64.PLT32, -4, ".text"),
            ), imports = listOf(
                ImportEntry("FuncA", "lib.dll"),
                ImportEntry("FuncB", "lib.dll"),
            ))
            val binary = PeLinker().link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("FuncA"))
            assertTrue(str.contains("FuncB"))
        }
    }

    @Nested
    inner class `DLL section counts` {

        @Test
        fun `DLL with text only has 2 sections`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            val numSections = readU16(buf, peOff + 4 + 2)
            assertEquals(2, numSections, ".text + .edata")
        }

        @Test
        fun `DLL with rodata has 3 sections`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, retCode(), align = 16),
                Section(".rodata", SectionKind.RODATA, "data\u0000".toByteArray(), align = 1),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            val numSections = readU16(buf, peOff + 4 + 2)
            assertEquals(3, numSections, ".text + .rdata + .edata")
        }

        @Test
        fun `DLL with imports has idata section`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("malloc", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "malloc", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("malloc", "ucrtbase.dll")))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains(".idata"))
        }
    }

    @Nested
    inner class `PE LARGE_ADDRESS_AWARE` {

        @Test
        fun `executable has LARGE_ADDRESS_AWARE`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            val characteristics = readU16(buf, peOff + 4 + 18)
            assertTrue(characteristics and PeConstants.IMAGE_FILE_LARGE_ADDRESS_AWARE != 0)
        }

        @Test
        fun `DLL has LARGE_ADDRESS_AWARE`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val peOff = peHeaderOffset(buf)
            val characteristics = readU16(buf, peOff + 4 + 18)
            assertTrue(characteristics and PeConstants.IMAGE_FILE_LARGE_ADDRESS_AWARE != 0)
        }
    }

    @Nested
    inner class `DLL _DllMainCRTStartup entry` {

        @Test
        fun `_DllMainCRTStartup used as entry point`() {
            val code = retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_DllMainCRTStartup", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val ep = readU32(buf, optOff + 16)
            assertTrue(ep > 0)
        }
    }

    @Nested
    inner class `PE multiple relocations same object` {

        @Test
        fun `multiple relocations to different symbols in same text`() {
            val code = callPlaceholder() + leaRipPlaceholder() + retCode()
            val rodata = "msg\u0000".toByteArray()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("str", value = 0, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(
                Relocation(1, "ExitProcess", RelocationType.X86_64.PLT32, -4, ".text"),
                Relocation(8, "str", RelocationType.X86_64.PC32, -4, ".text"),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ), imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val headersSize = readU32(buf, optOff + 60)
            assertNotEquals(0, readI32(buf, headersSize + 1), "Call patched")
            assertNotEquals(0, readI32(buf, headersSize + 8), "LEA patched")
        }
    }

    @Nested
    inner class `PE and DLL comparison` {

        @Test
        fun `exe and dll from same code have different characteristics`() {
            val code = retCode()
            val exeObj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val dllObj = x86Obj(code, listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val exeBin = PeLinker().link(listOf(exeObj))
            val dllBin = PeDllLinker(dllName = "test.dll").link(listOf(dllObj))
            val exeBuf = le(exeBin)
            val dllBuf = le(dllBin)
            val exeChars = readU16(exeBuf, peHeaderOffset(exeBuf) + 4 + 18)
            val dllChars = readU16(dllBuf, peHeaderOffset(dllBuf) + 4 + 18)
            assertTrue(dllChars and PeConstants.IMAGE_FILE_DLL != 0)
            assertTrue(exeChars and PeConstants.IMAGE_FILE_DLL == 0)
        }

        @Test
        fun `exe and dll have different image bases`() {
            val code = retCode()
            val exeObj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val dllObj = x86Obj(code, listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val exeBin = PeLinker().link(listOf(exeObj))
            val dllBin = PeDllLinker(dllName = "test.dll").link(listOf(dllObj))
            val exeBase = readU64(le(exeBin), peHeaderOffset(le(exeBin)) + 24 + 24)
            val dllBase = readU64(le(dllBin), peHeaderOffset(le(dllBin)) + 24 + 24)
            assertNotEquals(exeBase, dllBase)
        }
    }

    @Nested
    inner class `PE linker version` {

        @Test
        fun `linker major version set`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(14, binary[optOff + 2].toInt())
        }

        @Test
        fun `DLL linker major version set`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(14, binary[optOff + 2].toInt())
        }
    }

    @Nested
    inner class `PE code size in header` {

        @Test
        fun `size of code in optional header non-zero`() {
            val code = nopCode(100) + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val sizeOfCode = readU32(buf, optOff + 4)
            assertTrue(sizeOfCode > 0, "SizeOfCode should be > 0")
        }

        @Test
        fun `DLL size of code includes all text`() {
            val code = nopCode(50) + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val sizeOfCode = readU32(buf, optOff + 4)
            assertTrue(sizeOfCode >= code.size)
        }
    }

    @Nested
    inner class `PE data directories count` {

        @Test
        fun `export directory at index 0 for DLL`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeDllLinker(dllName = "test.dll").link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val exportRVA = readU32(buf, optOff + 112)
            assertTrue(exportRVA > 0)
        }

        @Test
        fun `import directory at index 1 for exe`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "ExitProcess", RelocationType.X86_64.PLT32, -4, ".text")),
                imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            val importRVA = readU32(buf, optOff + 120)
            assertTrue(importRVA > 0)
        }

        @Test
        fun `no export directory for exe`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = PeLinker().link(listOf(obj))
            val buf = le(binary)
            val optOff = peHeaderOffset(buf) + 24
            assertEquals(0, readU32(buf, optOff + 112))
        }
    }
}
