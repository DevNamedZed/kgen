package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PeWriterRoundTripTest {

    @Test
    fun `round-trip preserves MZ signature`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        assertEquals('M'.code.toByte(), bytes[0])
        assertEquals('Z'.code.toByte(), bytes[1])
    }

    @Test
    fun `round-trip preserves PE signature`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertTrue(pe.isPe)
    }

    @Test
    fun `round-trip text section contains code bytes`() {
        val code = byteArrayOf(
            0x55, 0x48, 0x89.toByte(), 0xE5.toByte(),
            0x31, 0xC0.toByte(), 0x5D, 0xC3.toByte(),
        )
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        val text = pe.sectionByName(".text")!!
        for (i in code.indices) {
            assertEquals(code[i], text.data[i], "Code byte $i mismatch")
        }
    }

    @Test
    fun `round-trip rodata section contains data`() {
        val rodata = "Hello, PE World!\u0000".toByteArray()
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()), rodata)
        val pe = PeReader.read(bytes)

        val rdata = pe.sectionByName(".rdata")!!
        for (i in rodata.indices) {
            assertEquals(rodata[i], rdata.data[i], "Rodata byte $i mismatch")
        }
    }

    @Test
    fun `round-trip without rodata has no rdata section`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertNull(pe.sectionByName(".rdata"))
    }

    @Test
    fun `round-trip has console subsystem`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertEquals(3, pe.optionalHeader!!.subsystem)
    }

    @Test
    fun `round-trip image base is correct`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertEquals(0x140000000L, pe.imageBase)
    }

    @Test
    fun `round-trip has import directory`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertFalse(pe.dataDirectories[PeDataDirectory.IMPORT].isEmpty)
    }

    @Test
    fun `round-trip import directory has kernel32 functions`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        val kernel32 = pe.importDirectories.first { it.name == "kernel32.dll" }
        val importNames = kernel32.entries.mapNotNull { it.name }
        assertTrue("ExitProcess" in importNames)
        assertTrue("GetStdHandle" in importNames)
        assertTrue("WriteFile" in importNames)
    }

    @Test
    fun `round-trip sections are not empty`() {
        val code = ByteArray(256) { (it % 256).toByte() }
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        val text = pe.sectionByName(".text")!!
        assertTrue(text.data.size >= code.size)
    }

    @Test
    fun `round-trip with large code`() {
        val code = ByteArray(8192) { (it % 256).toByte() }
        val bytes = PeWriter.writeFlat(code)
        val pe = PeReader.read(bytes)

        assertTrue(pe.isPe)
        val text = pe.sectionByName(".text")!!
        assertTrue(text.data.size >= code.size)
        for (i in code.indices) {
            assertEquals(code[i], text.data[i], "Byte $i mismatch in large code")
        }
    }

    @Test
    fun `round-trip ObjectFile write through ObjectFileWriter interface`() {
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
        assertTrue(pe.isExecutable)
        assertNotNull(pe.sectionByName(".text"))
    }

    @Test
    fun `round-trip text section characteristics`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        val text = pe.sectionByName(".text")!!
        assertTrue(text.isCode)
        assertTrue(text.isExecutable)
        assertTrue(text.isReadable)
        assertFalse(text.isWritable)
    }

    @Test
    fun `round-trip idata section characteristics`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)

        val idata = pe.sectionByName(".idata")!!
        assertTrue(idata.isInitializedData)
        assertTrue(idata.isReadable)
    }

    @Test
    fun `PeObjectFileReader round-trip`() {
        val reader = PeObjectFileReader()
        assertEquals(ObjectFormat.PE_COFF, reader.format)

        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        assertTrue(reader.canRead(bytes))

        val obj = reader.read(bytes)
        assertEquals(ObjectFormat.PE_COFF, obj.format)
        assertEquals(ArchType.X86_64, obj.arch.arch)
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT })
    }

    @Test
    fun `PeObjectFileReader rejects non-PE`() {
        val reader = PeObjectFileReader()
        assertFalse(reader.canRead(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) // ELF magic
        assertFalse(reader.canRead(byteArrayOf(0x00, 0x00)))
    }

    @Test
    fun `toObjectFile includes imports as ImportEntry`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)

        assertTrue(obj.imports.any { it.symbolName == "ExitProcess" && it.moduleName == "kernel32.dll" })
        assertTrue(obj.imports.any { it.symbolName == "GetStdHandle" && it.moduleName == "kernel32.dll" })
        assertTrue(obj.imports.any { it.symbolName == "WriteFile" && it.moduleName == "kernel32.dll" })
    }

    @Test
    fun `toObjectFile sets EXECUTABLE flag`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)

        assertTrue(ObjectFlag.EXECUTABLE in obj.metadata.flags)
    }

    @Test
    fun `toObjectFile sets WINDOWS osAbi`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        val obj = PeReader.toObjectFile(pe)

        assertEquals(OsAbi.WINDOWS, obj.metadata.osAbi)
    }

    @Test
    fun `sectionByRVA returns null for invalid RVA`() {
        val bytes = PeWriter.writeFlat(byteArrayOf(0xCC.toByte()))
        val pe = PeReader.read(bytes)
        assertNull(pe.sectionByRVA(0x7FFFFFFF))
    }
}
