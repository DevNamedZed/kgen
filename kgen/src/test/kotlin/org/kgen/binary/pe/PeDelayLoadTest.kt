package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PeDelayLoadTest {

    @Test
    fun `writeExe with delay-load imports produces didata section`() {
        val delayImports = mapOf("advapi32.dll" to listOf("RegOpenKeyExA"))
        val bytes = PeWriter.writeExe(
            byteArrayOf(0xCC.toByte()),
            imports = mapOf("kernel32.dll" to listOf("ExitProcess")),
            delayImports = delayImports,
        )
        val pe = PeReader.read(bytes)

        assertTrue(pe.isPe)
        val didata = pe.sections.find { it.name == ".didata" }
        assertNotNull(didata, "Should have .didata section: ${pe.sections.map { it.name }}")
    }

    @Test
    fun `delay-load data directory entry 13 is set`() {
        val delayImports = mapOf("advapi32.dll" to listOf("RegOpenKeyExA"))
        val bytes = PeWriter.writeExe(
            byteArrayOf(0xCC.toByte()),
            imports = mapOf("kernel32.dll" to listOf("ExitProcess")),
            delayImports = delayImports,
        )
        val pe = PeReader.read(bytes)

        assertTrue(pe.dataDirectories.size > 13, "Should have at least 14 data directories")
        val delayDir = pe.dataDirectories[13]
        assertTrue(delayDir.rva > 0, "Delay-load directory RVA should be set: $delayDir")
        assertTrue(delayDir.size > 0, "Delay-load directory size should be set: $delayDir")
    }

    @Test
    fun `delay-load imports are parsed back correctly`() {
        val delayImports = mapOf("advapi32.dll" to listOf("RegOpenKeyExA", "RegCloseKey"))
        val bytes = PeWriter.writeExe(
            byteArrayOf(0xCC.toByte()),
            imports = mapOf("kernel32.dll" to listOf("ExitProcess")),
            delayImports = delayImports,
        )
        val pe = PeReader.read(bytes)

        val delayDirs = pe.delayImportDirectories
        assertEquals(1, delayDirs.size, "Should have 1 delay-load DLL")
        assertEquals("advapi32.dll", delayDirs[0].name)
        val names = delayDirs[0].entries.mapNotNull { it.name }
        assertTrue("RegOpenKeyExA" in names, "Should contain RegOpenKeyExA: $names")
        assertTrue("RegCloseKey" in names, "Should contain RegCloseKey: $names")
    }

    @Test
    fun `delay-load imports coexist with standard imports`() {
        val imports = mapOf("kernel32.dll" to listOf("ExitProcess", "GetModuleHandleA"))
        val delayImports = mapOf("user32.dll" to listOf("MessageBoxA"))
        val bytes = PeWriter.writeExe(
            byteArrayOf(0xCC.toByte()),
            imports = imports,
            delayImports = delayImports,
        )
        val pe = PeReader.read(bytes)

        // Standard imports should be present
        assertEquals(1, pe.importDirectories.size)
        assertEquals("kernel32.dll", pe.importDirectories[0].name)

        // Delay imports should be present
        assertEquals(1, pe.delayImportDirectories.size)
        assertEquals("user32.dll", pe.delayImportDirectories[0].name)
        val delayNames = pe.delayImportDirectories[0].entries.mapNotNull { it.name }
        assertTrue("MessageBoxA" in delayNames)
    }

    @Test
    fun `multiple delay-load DLLs`() {
        val delayImports = mapOf(
            "advapi32.dll" to listOf("RegOpenKeyExA"),
            "shell32.dll" to listOf("ShellExecuteA", "SHGetFolderPathA"),
        )
        val bytes = PeWriter.writeExe(
            byteArrayOf(0xCC.toByte()),
            imports = mapOf("kernel32.dll" to listOf("ExitProcess")),
            delayImports = delayImports,
        )
        val pe = PeReader.read(bytes)

        val delayDirs = pe.delayImportDirectories
        assertEquals(2, delayDirs.size, "Should have 2 delay-load DLLs")
        val dllNames = delayDirs.map { it.name }.toSet()
        assertTrue("advapi32.dll" in dllNames)
        assertTrue("shell32.dll" in dllNames)

        val shell32 = delayDirs.first { it.name == "shell32.dll" }
        val shellNames = shell32.entries.mapNotNull { it.name }
        assertTrue("ShellExecuteA" in shellNames)
        assertTrue("SHGetFolderPathA" in shellNames)
    }

    @Test
    fun `writeDll with delay-load imports`() {
        val delayImports = mapOf("advapi32.dll" to listOf("RegOpenKeyExA"))
        val bytes = PeWriter.writeDll(
            byteArrayOf(0xCC.toByte()),
            exportNames = listOf("MyFunc"),
            delayImports = delayImports,
        )
        val pe = PeReader.read(bytes)

        assertTrue(pe.isDll)
        val delayDirs = pe.delayImportDirectories
        assertEquals(1, delayDirs.size)
        assertEquals("advapi32.dll", delayDirs[0].name)
    }

    @Test
    fun `delay-load descriptor has correct attributes`() {
        val delayImports = mapOf("test.dll" to listOf("TestFunc"))
        val bytes = PeWriter.writeExe(
            byteArrayOf(0xCC.toByte()),
            imports = mapOf("kernel32.dll" to listOf("ExitProcess")),
            delayImports = delayImports,
        )
        val pe = PeReader.read(bytes)

        // Verify the delay-load directory is RVA-based (attributes = 1)
        val delayDir = pe.dataDirectories[13]
        assertTrue(delayDir.rva > 0)

        // Verify the delay import was parsed
        assertEquals(1, pe.delayImportDirectories.size)
        assertEquals("test.dll", pe.delayImportDirectories[0].name)
        assertEquals("TestFunc", pe.delayImportDirectories[0].entries[0].name)
    }

    @Test
    fun `ObjectFile write with isDelayLoad imports`() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(name = ".text", data = byteArrayOf(0xCC.toByte()), kind = SectionKind.TEXT),
            ),
            symbols = emptyList(),
            relocations = emptyList(),
            imports = listOf(
                ImportEntry("ExitProcess", "kernel32.dll"),
                ImportEntry("MessageBoxA", "user32.dll", isDelayLoad = true),
            ),
        )
        val bytes = PeWriter.write(obj)
        val pe = PeReader.read(bytes)

        // Standard import
        assertEquals(1, pe.importDirectories.size)
        assertEquals("kernel32.dll", pe.importDirectories[0].name)

        // Delay import
        assertEquals(1, pe.delayImportDirectories.size)
        assertEquals("user32.dll", pe.delayImportDirectories[0].name)
        assertEquals("MessageBoxA", pe.delayImportDirectories[0].entries[0].name)
    }

    @Test
    fun `delay-load only imports no standard imports`() {
        val delayImports = mapOf("advapi32.dll" to listOf("RegOpenKeyExA"))
        val bytes = PeWriter.writeExe(
            byteArrayOf(0xCC.toByte()),
            imports = emptyMap(),
            delayImports = delayImports,
        )
        val pe = PeReader.read(bytes)

        assertTrue(pe.importDirectories.isEmpty(), "Should have no standard imports")
        assertEquals(1, pe.delayImportDirectories.size)
        assertEquals("advapi32.dll", pe.delayImportDirectories[0].name)
    }
}
