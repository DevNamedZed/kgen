package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PeWriterImportTest {

    @Test
    fun `writeExe with single DLL import`() {
        val imports = mapOf("user32.dll" to listOf("MessageBoxA"))
        val bytes = PeWriter.writeExe(byteArrayOf(0xCC.toByte()), imports = imports)
        val pe = PeReader.read(bytes)

        assertTrue(pe.isPe)
        val user32 = pe.importDirectories.firstOrNull { it.name == "user32.dll" }
        assertNotNull(user32)
        val names = user32!!.entries.mapNotNull { it.name }
        assertTrue("MessageBoxA" in names)
    }

    @Test
    fun `writeExe with multiple DLL imports`() {
        val imports = mapOf(
            "kernel32.dll" to listOf("ExitProcess", "GetModuleHandleA"),
            "user32.dll" to listOf("MessageBoxA", "ShowWindow"),
        )
        val bytes = PeWriter.writeExe(byteArrayOf(0xCC.toByte()), imports = imports)
        val pe = PeReader.read(bytes)

        assertTrue(pe.isPe)
        assertEquals(2, pe.importDirectories.size)

        val kernel32 = pe.importDirectories.first { it.name == "kernel32.dll" }
        val kernel32Names = kernel32.entries.mapNotNull { it.name }
        assertTrue("ExitProcess" in kernel32Names)
        assertTrue("GetModuleHandleA" in kernel32Names)

        val user32 = pe.importDirectories.first { it.name == "user32.dll" }
        val user32Names = user32.entries.mapNotNull { it.name }
        assertTrue("MessageBoxA" in user32Names)
        assertTrue("ShowWindow" in user32Names)
    }

    @Test
    fun `writeExe with three DLL imports`() {
        val imports = mapOf(
            "kernel32.dll" to listOf("ExitProcess"),
            "user32.dll" to listOf("MessageBoxA"),
            "gdi32.dll" to listOf("CreateFontW", "SelectObject"),
        )
        val bytes = PeWriter.writeExe(byteArrayOf(0xCC.toByte()), imports = imports)
        val pe = PeReader.read(bytes)

        assertEquals(3, pe.importDirectories.size)
        val dllNames = pe.importDirectories.map { it.name }.toSet()
        assertEquals(setOf("kernel32.dll", "user32.dll", "gdi32.dll"), dllNames)

        val gdi32 = pe.importDirectories.first { it.name == "gdi32.dll" }
        val gdi32Names = gdi32.entries.mapNotNull { it.name }
        assertTrue("CreateFontW" in gdi32Names)
        assertTrue("SelectObject" in gdi32Names)
    }

    @Test
    fun `writeExe with no imports produces no idata section`() {
        val bytes = PeWriter.writeExe(byteArrayOf(0xCC.toByte()), imports = emptyMap())
        val pe = PeReader.read(bytes)

        assertTrue(pe.isPe)
        assertNull(pe.sectionByName(".idata"))
        assertTrue(pe.importDirectories.isEmpty())
    }

    @Test
    fun `writeFlat still produces default kernel32 imports`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        val kernel32 = pe.importDirectories.first { it.name == "kernel32.dll" }
        val names = kernel32.entries.mapNotNull { it.name }
        assertTrue("ExitProcess" in names)
        assertTrue("GetStdHandle" in names)
        assertTrue("WriteFile" in names)
    }

    @Test
    fun `iatEntryRVAs backward compatibility`() {
        val rvas = PeWriter.iatEntryRVAs(1)
        assertEquals(3, rvas.size)
        // Sequential 8 bytes apart
        assertEquals(rvas[0] + 8, rvas[1])
        assertEquals(rvas[1] + 8, rvas[2])
    }

    @Test
    fun `iatEntryMap returns named entries`() {
        val imports = mapOf(
            "kernel32.dll" to listOf("ExitProcess"),
            "user32.dll" to listOf("MessageBoxA"),
        )
        val map = PeWriter.iatEntryMap(100, 0, imports)
        assertTrue("ExitProcess" in map)
        assertTrue("MessageBoxA" in map)
        assertEquals(2, map.size)
        // All addresses should be above IMAGE_BASE
        for ((_, addr) in map) {
            assertTrue(addr > PeWriter.imageBase())
        }
    }

    @Test
    fun `iatEntryMap with default imports matches iatEntryRVAs`() {
        val rvas = PeWriter.iatEntryRVAs(100)
        val map = PeWriter.iatEntryMap(100)
        assertEquals(rvas[0], map["GetStdHandle"])
        assertEquals(rvas[1], map["WriteFile"])
        assertEquals(rvas[2], map["ExitProcess"])
    }

    @Test
    fun `write ObjectFile with imports uses them`() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
            ),
            symbols = emptyList(),
            relocations = emptyList(),
            imports = listOf(
                ImportEntry("CreateFileA", "kernel32.dll"),
                ImportEntry("ReadFile", "kernel32.dll"),
                ImportEntry("MessageBoxA", "user32.dll"),
            ),
        )
        val bytes = PeWriter.write(obj)
        val pe = PeReader.read(bytes)

        assertTrue(pe.isPe)
        assertEquals(2, pe.importDirectories.size)

        val kernel32 = pe.importDirectories.first { it.name == "kernel32.dll" }
        val kernel32Names = kernel32.entries.mapNotNull { it.name }
        assertTrue("CreateFileA" in kernel32Names)
        assertTrue("ReadFile" in kernel32Names)

        val user32 = pe.importDirectories.first { it.name == "user32.dll" }
        val user32Names = user32.entries.mapNotNull { it.name }
        assertTrue("MessageBoxA" in user32Names)
    }

    @Test
    fun `write ObjectFile without imports falls back to default`() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
            ),
            symbols = emptyList(),
            relocations = emptyList(),
        )
        val bytes = PeWriter.write(obj)
        val pe = PeReader.read(bytes)

        assertTrue(pe.isPe)
        val kernel32 = pe.importDirectories.firstOrNull { it.name == "kernel32.dll" }
        assertNotNull(kernel32)
        val names = kernel32!!.entries.mapNotNull { it.name }
        assertTrue("ExitProcess" in names)
    }

    @Test
    fun `writeExe with rodata and custom imports`() {
        val rodata = "Hello\u0000".toByteArray()
        val imports = mapOf("msvcrt.dll" to listOf("printf", "exit"))
        val bytes = PeWriter.writeExe(byteArrayOf(0xCC.toByte()), rodata, imports)
        val pe = PeReader.read(bytes)

        assertTrue(pe.isPe)
        assertNotNull(pe.sectionByName(".rdata"))

        val msvcrt = pe.importDirectories.first { it.name == "msvcrt.dll" }
        val names = msvcrt.entries.mapNotNull { it.name }
        assertTrue("printf" in names)
        assertTrue("exit" in names)
    }

    @Test
    fun `writeDll with imports has both export and import directories`() {
        val imports = mapOf("kernel32.dll" to listOf("GetProcAddress", "LoadLibraryA"))
        val bytes = PeWriter.writeDll(
            code = byteArrayOf(0xC3.toByte()),
            exportNames = listOf("myFunc"),
            dllName = "test.dll",
            imports = imports,
        )
        val pe = PeReader.read(bytes)

        assertTrue(pe.isPe)
        assertTrue(pe.isDll)

        // Verify exports
        assertFalse(pe.dataDirectories[PeDataDirectory.EXPORT].isEmpty)

        // Verify imports
        val kernel32 = pe.importDirectories.firstOrNull { it.name == "kernel32.dll" }
        assertNotNull(kernel32)
        val names = kernel32!!.entries.mapNotNull { it.name }
        assertTrue("GetProcAddress" in names)
        assertTrue("LoadLibraryA" in names)
    }

    @Test
    fun `writeExe with many functions in one DLL`() {
        val functions = (1..20).map { "Function$it" }
        val imports = mapOf("big.dll" to functions)
        val bytes = PeWriter.writeExe(byteArrayOf(0xCC.toByte()), imports = imports)
        val pe = PeReader.read(bytes)

        val bigDll = pe.importDirectories.first { it.name == "big.dll" }
        val names = bigDll.entries.mapNotNull { it.name }
        assertEquals(20, names.size)
        for (func in functions) {
            assertTrue(func in names, "Missing import: $func")
        }
    }

    @Test
    fun `round-trip through ObjectFile preserves custom imports`() {
        val imports = mapOf(
            "advapi32.dll" to listOf("RegOpenKeyExA", "RegCloseKey"),
        )
        val bytes = PeWriter.writeExe(byteArrayOf(0xCC.toByte()), imports = imports)
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)

        assertTrue(obj.imports.any { it.symbolName == "RegOpenKeyExA" && it.moduleName == "advapi32.dll" })
        assertTrue(obj.imports.any { it.symbolName == "RegCloseKey" && it.moduleName == "advapi32.dll" })
    }

    @Test
    fun `write DLL ObjectFile with custom imports`() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
            ),
            symbols = listOf(
                Symbol("myExport", value = 0, size = 1, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
            imports = listOf(
                ImportEntry("HeapAlloc", "kernel32.dll"),
            ),
            exports = listOf(
                ExportEntry("myExport"),
            ),
            metadata = ObjectMetadata(flags = setOf(ObjectFlag.DLL), moduleName = "mylib.dll"),
        )
        val bytes = PeWriter.write(obj)
        val pe = PeReader.read(bytes)

        assertTrue(pe.isDll)
        // Should have exports
        assertFalse(pe.dataDirectories[PeDataDirectory.EXPORT].isEmpty)
        // Should have imports
        val kernel32 = pe.importDirectories.firstOrNull { it.name == "kernel32.dll" }
        assertNotNull(kernel32)
        val names = kernel32!!.entries.mapNotNull { it.name }
        assertTrue("HeapAlloc" in names)
    }

    @Test
    fun `DEFAULT_IMPORTS contains expected entries`() {
        val defaults = PeWriter.DEFAULT_IMPORTS
        assertEquals(1, defaults.size)
        assertTrue("kernel32.dll" in defaults)
        val funcs = defaults["kernel32.dll"]!!
        assertTrue("GetStdHandle" in funcs)
        assertTrue("WriteFile" in funcs)
        assertTrue("ExitProcess" in funcs)
    }
}
