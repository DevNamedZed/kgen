package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PeReaderTest {

    @Test
    fun `round-trip with PeWriter produces valid PE`() {
        val code = byteArrayOf(0xCC.toByte()) // int3
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        assertTrue(pe.isPe)
        assertTrue(pe.isExecutable)
        assertFalse(pe.isDll)
        assertTrue(pe.isPe32Plus)
        assertEquals(PeConstants.MACHINE_AMD64, pe.coffHeader.machine)
    }

    @Test
    fun `reads sections from PeWriter output`() {
        val code = byteArrayOf(0x90.toByte(), 0xC3.toByte()) // nop; ret
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        assertTrue(pe.sections.any { it.name == ".text" })
        assertTrue(pe.sections.any { it.name == ".idata" })

        val text = pe.sectionByName(".text")!!
        assertTrue(text.isCode)
        assertTrue(text.isExecutable)
    }

    @Test
    fun `reads imports from PeWriter output`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        assertEquals(1, pe.importDirectories.size)
        val dir = pe.importDirectories[0]
        assertEquals("kernel32.dll", dir.name)
        assertTrue(dir.entries.any { it.name == "ExitProcess" })
        assertTrue(dir.entries.any { it.name == "GetStdHandle" })
        assertTrue(dir.entries.any { it.name == "WriteFile" })
    }

    @Test
    fun `reads optional header fields`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        val opt = pe.optionalHeader!!
        assertEquals(PeConstants.PE32PLUS_MAGIC, opt.magic)
        assertTrue(opt.imageBase > 0)
        assertTrue(opt.sectionAlignment > 0)
        assertTrue(opt.fileAlignment > 0)
        assertEquals(3, opt.subsystem) // CONSOLE
    }

    @Test
    fun `reads data directories`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        assertTrue(pe.dataDirectories.size >= 2)
        assertFalse(pe.dataDirectories[PeDataDirectory.IMPORT].isEmpty)
    }

    @Test
    fun `toObjectFile projects correctly`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)

        assertEquals(ObjectFormat.PE_COFF, obj.format)
        assertEquals(ArchType.X86_64, obj.arch.arch)
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT })
        assertTrue(obj.imports.any { it.moduleName == "kernel32.dll" })
        assertTrue(obj.metadata.flags.contains(ObjectFlag.EXECUTABLE))
        assertEquals(OsAbi.WINDOWS, obj.metadata.osAbi)
    }

    @Test
    fun `canRead detects PE files`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        assertTrue(PeReader.canRead(bytes))
        assertFalse(PeReader.canRead(byteArrayOf(0x00, 0x00)))
    }

    @Test
    fun `sections with rodata`() {
        val code = byteArrayOf(0xCC.toByte())
        val rodata = "hello".toByteArray()
        val bytes = PeWriter.writeFlat(code, rodata)
        val pe = PeReader.read(bytes)

        assertTrue(pe.sections.any { it.name == ".rdata" })
        val rdata = pe.sectionByName(".rdata")!!
        assertTrue(rdata.data.size >= rodata.size)
    }

    @Test
    fun `no CLR metadata for native PE`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        assertNull(pe.clrMetadata)
        assertFalse(pe.isManagedAssembly)
    }

    @Test
    fun `sectionByRVA finds correct section`() {
        val code = byteArrayOf(0xCC.toByte())
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        val textSection = pe.sectionByName(".text")!!
        val found = pe.sectionByRVA(textSection.virtualAddress)
        assertNotNull(found)
        assertEquals(".text", found!!.name)
    }

    @Test
    fun `round-trip PeLinker output through PeReader`() {
        // Build a minimal ObjectFile with imports
        val textBytes = byteArrayOf(
            0x55, // push rbp
            0x48, 0x89.toByte(), 0xE5.toByte(), // mov rbp, rsp
            0x31, 0xC0.toByte(), // xor eax, eax
            0x5D, // pop rbp
            0xC3.toByte(), // ret
        )
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = listOf(
                Section(".text", SectionKind.TEXT, textBytes, align = 16),
                Section(".rodata", SectionKind.RODATA, "Hello\u0000".toByteArray(), align = 1),
            ),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("msg", value = 0, section = ".rodata", binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
            relocations = emptyList(),
            imports = listOf(ImportEntry(moduleName = "ucrtbase.dll", symbolName = "puts")),
        )

        val linked = PeLinker().link(listOf(obj))
        assertTrue(PeReader.canRead(linked))

        val pe = PeReader.read(linked)
        assertTrue(pe.isPe)
        assertTrue(pe.isExecutable)
        assertTrue(pe.isPe32Plus)
        assertEquals(PeConstants.MACHINE_AMD64, pe.coffHeader.machine)

        // Verify sections
        assertTrue(pe.sections.any { it.name == ".text" })
        assertTrue(pe.sections.any { it.name == ".rdata" })
        assertTrue(pe.sections.any { it.name == ".idata" })

        // Verify import was parsed back
        assertTrue(pe.importDirectories.any { it.name == "ucrtbase.dll" })
        val ucrt = pe.importDirectories.first { it.name == "ucrtbase.dll" }
        assertTrue(ucrt.entries.any { it.name == "puts" })

        // Verify ObjectFile projection round-trip
        val projected = PeReader.toObjectFile(pe)
        assertEquals(ObjectFormat.PE_COFF, projected.format)
        assertTrue(projected.imports.any { it.symbolName == "puts" && it.moduleName == "ucrtbase.dll" })
    }
}
