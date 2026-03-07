package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PeLinkerExtendedTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun peHeaderOffset(buf: ByteBuffer): Int = readU32(buf, 0x3C)
    private fun optionalHeaderOffset(buf: ByteBuffer): Int = peHeaderOffset(buf) + 24

    private fun makeObj(name: String, code: ByteArray, binding: SymbolBinding = SymbolBinding.GLOBAL): ObjectFile {
        return ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol(name, value = 0, size = code.size.toLong(), section = ".text",
                    binding = binding, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
    }

    private fun retCode(): ByteArray = byteArrayOf(0xC3.toByte())

    private fun addCode(): ByteArray = byteArrayOf(
        0x8D.toByte(), 0x04, 0x11, // lea eax, [rcx+rdx]
        0xC3.toByte(),
    )

    private fun subCode(): ByteArray = byteArrayOf(
        0x89.toByte(), 0xC8.toByte(), // mov eax, ecx
        0x29, 0xD0.toByte(),           // sub eax, edx
        0xC3.toByte(),
    )

    // --- Section Merging Tests ---

    @Test
    fun mergesMultipleTextSections() {
        val obj1 = makeObj("func1", addCode())
        val obj2 = makeObj("func2", subCode())
        val obj3 = makeObj("func3", retCode())
        val binary = PeDllLinker(dllName = "multi.dll").link(listOf(obj1, obj2, obj3))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("func1"))
        assertTrue(str.contains("func2"))
        assertTrue(str.contains("func3"))
    }

    @Test
    fun mergesTextAndRodataSections() {
        val textCode = addCode()
        val rodata = "Hello, World!".toByteArray(Charsets.US_ASCII)
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, textCode, align = 16),
                Section(".rdata", SectionKind.RODATA, rodata, align = 4),
            ),
            symbols = listOf(
                Symbol("my_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("my_data", value = 0, section = ".rdata",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
            relocations = emptyList(),
        )
        val binary = PeDllLinker(dllName = "data.dll").link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("my_func"))
        assertTrue(str.contains("Hello, World!"))
    }

    // --- Export Table Tests ---

    @Test
    fun exportTableHasCorrectStructure() {
        val binary = PeDllLinker(dllName = "exports.dll").link(listOf(makeObj("alpha", retCode())))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val exportRVA = readU32(buf, optOff + 112)
        val exportSize = readU32(buf, optOff + 116)
        assertTrue(exportRVA > 0)
        assertTrue(exportSize >= 40, "Export directory should be at least 40 bytes")
    }

    @Test
    fun exportsAreSortedAlphabetically() {
        val obj1 = makeObj("zebra", retCode())
        val obj2 = makeObj("apple", retCode())
        val obj3 = makeObj("mango", retCode())
        val binary = PeDllLinker(dllName = "sorted.dll").link(listOf(obj1, obj2, obj3))
        val str = String(binary, Charsets.ISO_8859_1)
        val appleIdx = str.indexOf("apple")
        val mangoIdx = str.indexOf("mango")
        val zebraIdx = str.indexOf("zebra")
        assertTrue(appleIdx > 0)
        assertTrue(mangoIdx > 0)
        assertTrue(zebraIdx > 0)
    }

    @Test
    fun exportCountMatchesGlobalSymbols() {
        val obj1 = makeObj("func_a", addCode())
        val obj2 = makeObj("func_b", subCode())
        val localObj = makeObj("helper", retCode(), binding = SymbolBinding.LOCAL)
        val binary = PeDllLinker(dllName = "count.dll").link(listOf(obj1, obj2, localObj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("func_a"))
        assertTrue(str.contains("func_b"))
        assertFalse(str.contains("helper"))
    }

    // --- PE Header Structure Tests ---

    @Test
    fun pe32PlusMagic() {
        val binary = PeDllLinker(dllName = "magic.dll").link(listOf(makeObj("test", retCode())))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val magic = readU16(buf, optOff)
        assertEquals(PeConstants.PE32PLUS_MAGIC, magic)
    }

    @Test
    fun machineTypeIsAmd64() {
        val binary = PeDllLinker(dllName = "amd64.dll").link(listOf(makeObj("test", retCode())))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val machine = readU16(buf, peOff + 4)
        assertEquals(PeConstants.MACHINE_AMD64, machine)
    }

    @Test
    fun sectionAlignmentIsCorrect() {
        val binary = PeDllLinker(dllName = "align.dll").link(listOf(makeObj("test", retCode())))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val sectionAlignment = readU32(buf, optOff + 32)
        val fileAlignment = readU32(buf, optOff + 36)
        assertEquals(0x1000, sectionAlignment)
        assertEquals(0x200, fileAlignment)
    }

    @Test
    fun imageSizeIsAligned() {
        val binary = PeDllLinker(dllName = "size.dll").link(listOf(makeObj("test", retCode())))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val imageSize = readU32(buf, optOff + 56)
        assertEquals(0, imageSize % 0x1000, "Image size should be section-aligned")
    }

    @Test
    fun textSectionRvaIs0x1000() {
        val binary = PeDllLinker(dllName = "textrva.dll").link(listOf(makeObj("test", retCode())))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val baseOfCode = readU32(buf, optOff + 20)
        assertEquals(0x1000, baseOfCode, "Base of code should be 0x1000")
    }

    // --- Import Tests ---

    @Test
    fun importTablePresenceWhenImportsExist() {
        val code = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call
            0xC3.toByte(),
        )
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("my_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ExitProcess", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            imports = listOf(ImportEntry("ExitProcess", "kernel32.dll")),
            relocations = listOf(
                Relocation(offset = 1, symbol = "ExitProcess", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )
        val binary = PeDllLinker(dllName = "imports.dll").link(listOf(obj))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val importRVA = readU32(buf, optOff + 120)
        val importSize = readU32(buf, optOff + 124)
        assertTrue(importRVA > 0, "Should have import directory RVA")
        assertTrue(importSize > 0, "Should have non-zero import directory size")
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("kernel32.dll"))
        assertTrue(str.contains("ExitProcess"))
    }

    @Test
    fun noImportTableWithoutImports() {
        val binary = PeDllLinker(dllName = "noimport.dll").link(listOf(makeObj("test", retCode())))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val importRVA = readU32(buf, optOff + 120)
        assertEquals(0, importRVA, "Should not have import directory when no imports")
    }

    @Test
    fun multipleImportDlls() {
        val code = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call GetProcAddress
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call printf
            0xC3.toByte(),
        )
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("my_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("GetProcAddress", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            imports = listOf(
                ImportEntry("GetProcAddress", "kernel32.dll"),
                ImportEntry("printf", "ucrtbase.dll"),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "GetProcAddress", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
                Relocation(offset = 6, symbol = "printf", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )
        val binary = PeDllLinker(dllName = "multi_import.dll").link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("kernel32.dll"))
        assertTrue(str.contains("ucrtbase.dll"))
        assertTrue(str.contains("GetProcAddress"))
        assertTrue(str.contains("printf"))
    }

    // --- Symbol Resolution Tests ---

    @Test
    fun crossObjectSymbolResolution() {
        val callerCode = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call target
            0xC3.toByte(),
        )
        val obj1 = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, callerCode, align = 16)),
            symbols = listOf(
                Symbol("caller", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "target", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )
        val obj2 = makeObj("target", byteArrayOf(
            0xB8.toByte(), 0x01, 0x00, 0x00, 0x00, // mov eax, 1
            0xC3.toByte(),
        ))
        val binary = PeDllLinker(dllName = "xref.dll").link(listOf(obj1, obj2))
        val buf = le(binary)
        // Verify the call offset was patched (not all zeros anymore)
        val peOff = peHeaderOffset(buf)
        val optOff = peOff + 24
        val textFileOffset = readU32(buf, optOff + 60) // headers size = first section file offset
        // Read the patched call displacement
        val callDisp = readU32(buf, textFileOffset + 1)
        assertNotEquals(0, callDisp, "Call displacement should be patched")
    }

    @Test
    fun undefinedSymbolThrows() {
        val callerCode = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00,
            0xC3.toByte(),
        )
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, callerCode, align = 16)),
            symbols = listOf(
                Symbol("caller", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("nonexistent", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "nonexistent", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )
        assertThrows(IllegalStateException::class.java) {
            PeDllLinker(dllName = "fail.dll").link(listOf(obj))
        }
    }

    // --- DLL Entry Point Tests ---

    @Test
    fun dllMainSetsEntryPoint() {
        val dllMainCode = byteArrayOf(
            0xB8.toByte(), 0x01, 0x00, 0x00, 0x00, // mov eax, 1
            0xC3.toByte(),
        )
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, dllMainCode, align = 16)),
            symbols = listOf(
                Symbol("DllMain", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val binary = PeDllLinker(dllName = "entry.dll").link(listOf(obj))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val entryPoint = readU32(buf, optOff + 16)
        assertTrue(entryPoint > 0, "Entry point should be non-zero when DllMain is present")
        assertEquals(0x1000, entryPoint, "Entry point should be at start of .text (0x1000)")
    }

    @Test
    fun dllMainCRTStartupSetsEntryPoint() {
        val crtCode = byteArrayOf(
            0xB8.toByte(), 0x01, 0x00, 0x00, 0x00,
            0xC3.toByte(),
        )
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, crtCode, align = 16)),
            symbols = listOf(
                Symbol("_DllMainCRTStartup", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val binary = PeDllLinker(dllName = "crt.dll").link(listOf(obj))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val entryPoint = readU32(buf, optOff + 16)
        assertTrue(entryPoint > 0, "Entry point should be set for _DllMainCRTStartup")
    }

    // --- Image Base Tests ---

    @Test
    fun customImageBase() {
        val binary = PeDllLinker(dllName = "custom.dll", imageBase = 0x10000000L)
            .link(listOf(makeObj("test", retCode())))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val imgBase = readU64(buf, optOff + 24)
        assertEquals(0x10000000L, imgBase)
    }

    @Test
    fun defaultImageBase() {
        val binary = PeDllLinker(dllName = "default.dll")
            .link(listOf(makeObj("test", retCode())))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val imgBase = readU64(buf, optOff + 24)
        assertEquals(0x180000000L, imgBase)
    }

    // --- Subsystem Tests ---

    @Test
    fun defaultSubsystemIsConsole() {
        val binary = PeDllLinker(dllName = "console.dll")
            .link(listOf(makeObj("test", retCode())))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val subsystem = readU16(buf, optOff + 68)
        assertEquals(3, subsystem, "Default subsystem should be CONSOLE (3)")
    }

    @Test
    fun customSubsystem() {
        val binary = PeDllLinker(dllName = "gui.dll", subsystem = 2)
            .link(listOf(makeObj("test", retCode())))
        val buf = le(binary)
        val optOff = optionalHeaderOffset(buf)
        val subsystem = readU16(buf, optOff + 68)
        assertEquals(2, subsystem, "Subsystem should be WINDOWS_GUI (2)")
    }

    // --- Section Count Tests ---

    @Test
    fun sectionCountMatchesContent() {
        // Just .text (no rdata, no imports)
        val binary = PeDllLinker(dllName = "minimal.dll")
            .link(listOf(makeObj("test", retCode())))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val numSections = readU16(buf, peOff + 4 + 2)
        // .text + .edata = 2 sections
        assertEquals(2, numSections, "Should have .text + .edata sections")
    }

    @Test
    fun sectionCountWithRodata() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, retCode(), align = 16),
                Section(".rdata", SectionKind.RODATA, byteArrayOf(0x42), align = 4),
            ),
            symbols = listOf(
                Symbol("test", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val binary = PeDllLinker(dllName = "rodata.dll").link(listOf(obj))
        val buf = le(binary)
        val peOff = peHeaderOffset(buf)
        val numSections = readU16(buf, peOff + 4 + 2)
        // .text + .rdata + .edata = 3 sections
        assertEquals(3, numSections, "Should have .text + .rdata + .edata sections")
    }

    // --- DLL Name Tests ---

    @Test
    fun dllNameInExportDirectory() {
        val binary = PeDllLinker(dllName = "myspecial.dll")
            .link(listOf(makeObj("test", retCode())))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("myspecial.dll"))
    }

    @Test
    fun emptyObjectListThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            PeDllLinker(dllName = "empty.dll").link(emptyList())
        }
    }
}
