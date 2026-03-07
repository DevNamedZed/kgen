package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PeReaderExtendedTest {

    @Test
    fun `PeWriter output is valid PE`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertTrue(pe.isPe)
    }

    @Test
    fun `PeWriter output is executable not DLL`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertTrue(pe.isExecutable)
        assertFalse(pe.isDll)
    }

    @Test
    fun `PeWriter output is PE32 Plus`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertTrue(pe.isPe32Plus)
    }

    @Test
    fun `COFF header has correct machine`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertEquals(PeConstants.MACHINE_AMD64, pe.coffHeader.machine)
    }

    @Test
    fun `COFF header section count matches sections list`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertEquals(pe.coffHeader.numberOfSections, pe.sections.size)
    }

    @Test
    fun `optional header has correct magic`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertNotNull(pe.optionalHeader)
        assertEquals(PeConstants.PE32PLUS_MAGIC, pe.optionalHeader!!.magic)
    }

    @Test
    fun `optional header has positive image base`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertTrue(pe.optionalHeader!!.imageBase > 0)
    }

    @Test
    fun `optional header alignment values`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val opt = pe.optionalHeader!!
        assertTrue(opt.sectionAlignment > 0)
        assertTrue(opt.fileAlignment > 0)
        assertTrue(opt.sectionAlignment >= opt.fileAlignment)
    }

    @Test
    fun `optional header subsystem is console`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertEquals(3, pe.optionalHeader!!.subsystem)
    }

    @Test
    fun `text section exists and is code`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0x90.toByte(), 0xC3.toByte()))
        val pe = PeReader.read(bytes)
        val text = pe.sectionByName(".text")
        assertNotNull(text)
        assertTrue(text!!.isCode)
        assertTrue(text.isExecutable)
        assertTrue(text.isReadable)
    }

    @Test
    fun `idata section exists`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val idata = pe.sectionByName(".idata")
        assertNotNull(idata)
    }

    @Test
    fun `import directory has kernel32`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertEquals(1, pe.importDirectories.size)
        assertEquals("kernel32.dll", pe.importDirectories[0].name)
    }

    @Test
    fun `import directory has ExitProcess`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val dir = pe.importDirectories[0]
        assertTrue(dir.entries.any { it.name == "ExitProcess" })
    }

    @Test
    fun `import directory has GetStdHandle`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val dir = pe.importDirectories[0]
        assertTrue(dir.entries.any { it.name == "GetStdHandle" })
    }

    @Test
    fun `import directory has WriteFile`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val dir = pe.importDirectories[0]
        assertTrue(dir.entries.any { it.name == "WriteFile" })
    }

    @Test
    fun `data directories list has at least 2 entries`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertTrue(pe.dataDirectories.size >= 2)
    }

    @Test
    fun `import data directory is not empty`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertFalse(pe.dataDirectories[PeDataDirectory.IMPORT].isEmpty)
    }

    @Test
    fun `export data directory is empty for non-DLL`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        if (pe.dataDirectories.size > PeDataDirectory.EXPORT) {
            assertTrue(pe.dataDirectories[PeDataDirectory.EXPORT].isEmpty)
        }
    }

    @Test
    fun `canRead accepts PE files`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        assertTrue(PeReader.canRead(bytes))
    }

    @Test
    fun `canRead rejects empty bytes`() {
        assertFalse(PeReader.canRead(byteArrayOf()))
    }

    @Test
    fun `canRead rejects single byte`() {
        assertFalse(PeReader.canRead(byteArrayOf(0x00)))
    }

    @Test
    fun `canRead rejects random bytes`() {
        assertFalse(PeReader.canRead(byteArrayOf(0x00, 0x00)))
    }

    @Test
    fun `no CLR metadata for native PE`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertNull(pe.clrMetadata)
        assertFalse(pe.isManagedAssembly)
        assertFalse(pe.isILOnly)
        assertFalse(pe.isMixedMode)
    }

    @Test
    fun `sectionByRVA finds text section`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val text = pe.sectionByName(".text")!!
        val found = pe.sectionByRVA(text.virtualAddress)
        assertNotNull(found)
        assertEquals(".text", found!!.name)
    }

    @Test
    fun `sectionByRVA returns null for invalid RVA`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertNull(pe.sectionByRVA(0x7FFFFFFF))
    }

    @Test
    fun `toObjectFile has PE COFF format`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)
        assertEquals(ObjectFormat.PE_COFF, obj.format)
    }

    @Test
    fun `toObjectFile has x86 64 arch`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)
        assertEquals(ArchType.X86_64, obj.arch.arch)
    }

    @Test
    fun `toObjectFile has text section`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT })
    }

    @Test
    fun `toObjectFile has imports`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)
        assertTrue(obj.imports.any { it.moduleName == "kernel32.dll" })
    }

    @Test
    fun `toObjectFile has executable flag`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)
        assertTrue(obj.metadata.flags.contains(ObjectFlag.EXECUTABLE))
    }

    @Test
    fun `toObjectFile has windows OS ABI`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)
        assertEquals(OsAbi.WINDOWS, obj.metadata.osAbi)
    }

    @Test
    fun `rodata section appears as rdata`() {
        val rodata = "TestRodata".toByteArray()
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()), rodata)
        val pe = PeReader.read(bytes)
        val rdata = pe.sectionByName(".rdata")
        assertNotNull(rdata)
        assertTrue(rdata!!.isReadable)
        assertFalse(rdata.isWritable)
        assertFalse(rdata.isCode)
    }

    @Test
    fun `rodata section data is at least as big as input`() {
        val rodata = "Hello World!".toByteArray()
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()), rodata)
        val pe = PeReader.read(bytes)
        val rdata = pe.sectionByName(".rdata")!!
        assertTrue(rdata.data.size >= rodata.size)
    }

    @Test
    fun `PeLinker output round trips through PeReader`() {
        val textBytes = byteArrayOf(0x31, 0xC0.toByte(), 0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = listOf(Section(".text", SectionKind.TEXT, textBytes, align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
            imports = listOf(ImportEntry(moduleName = "kernel32.dll", symbolName = "ExitProcess")),
        )
        val linked = PeLinker().link(listOf(obj))
        assertTrue(PeReader.canRead(linked))
        val pe = PeReader.read(linked)
        assertTrue(pe.isPe)
        assertTrue(pe.isExecutable)
        assertFalse(pe.isDll)
        assertTrue(pe.sections.any { it.name == ".text" })
    }

    @Test
    fun `PeDllLinker output round trips through PeReader`() {
        val code = byteArrayOf(0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("get_value", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val linked = PeDllLinker(dllName = "mylib.dll").link(listOf(obj))
        assertTrue(PeReader.canRead(linked))
        val pe = PeReader.read(linked)
        assertTrue(pe.isPe)
        assertTrue(pe.isDll)
        assertNotNull(pe.exportDirectory)
    }

    @Test
    fun `COFF obj file is not PE`() {
        val writer = CoffObjectWriter()
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = listOf(Symbol("fn", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            relocations = emptyList(),
        )
        val coffBytes = writer.write(obj)
        val pe = PeReader.read(coffBytes)
        assertFalse(pe.isPe)
    }

    @Test
    fun `COFF obj file has symbols`() {
        val writer = CoffObjectWriter()
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = listOf(Symbol("my_fn", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            relocations = emptyList(),
        )
        val coffBytes = writer.write(obj)
        val pe = PeReader.read(coffBytes)
        assertTrue(pe.symbols.any { it.name == "my_fn" })
    }

    @Test
    fun `image base accessor works`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertTrue(pe.imageBase > 0)
    }

    @Test
    fun `entry point RVA accessor works`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertTrue(pe.entryPointRVA > 0)
    }

    @Test
    fun `section virtual size is positive for text`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0x90.toByte(), 0xC3.toByte()))
        val pe = PeReader.read(bytes)
        val text = pe.sectionByName(".text")!!
        assertTrue(text.virtualSize > 0)
        assertTrue(text.virtualAddress > 0)
    }
}
