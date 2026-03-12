package org.kgen.binary.pe

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PeResourceTlsTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun peHeaderOffset(buf: ByteBuffer): Int = readU32(buf, 0x3C)
    private fun optionalHeaderOffset(buf: ByteBuffer): Int = peHeaderOffset(buf) + 24

    private fun makeMainObj(): ObjectFile {
        val code = byteArrayOf(
            0x48, 0x83.toByte(), 0xEC.toByte(), 0x28,  // sub rsp, 28h
            0x31, 0xC9.toByte(),                        // xor ecx, ecx
            0xC3.toByte(),                              // ret
        )
        return ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
    }

    @Test
    fun `linker emits rsrc section when resources provided`() {
        val rsrcData = ByteArray(64) { (it % 256).toByte() }
        val linker = PeLinker(resources = rsrcData)
        val binary = linker.link(listOf(makeMainObj()))
        val buf = le(binary)

        // Verify the resource data directory is set (index 2)
        val optOff = optionalHeaderOffset(buf)
        val rsrcDirRVA = readU32(buf, optOff + 112 + 16)
        val rsrcDirSize = readU32(buf, optOff + 112 + 20)
        assertTrue(rsrcDirRVA > 0, "Resource directory RVA should be set")
        assertEquals(rsrcData.size, rsrcDirSize, "Resource directory size should match")

        // Verify .rsrc section exists in PE
        val pe = PeReader.read(binary)
        val rsrcSection = pe.sectionByName(".rsrc")
        assertNotNull(rsrcSection, ".rsrc section should exist")
        assertTrue(rsrcSection!!.data.size >= rsrcData.size)
    }

    @Test
    fun `linker emits tls section when tls data provided`() {
        val tlsData = ByteArray(32) { 0x42 }
        val linker = PeLinker(tlsData = tlsData)
        val binary = linker.link(listOf(makeMainObj()))
        val buf = le(binary)

        // Verify TLS data directory is set (index 9)
        val optOff = optionalHeaderOffset(buf)
        val tlsDirRVA = readU32(buf, optOff + 112 + 72)
        val tlsDirSize = readU32(buf, optOff + 112 + 76)
        assertTrue(tlsDirRVA > 0, "TLS directory RVA should be set")
        assertTrue(tlsDirSize > 0, "TLS directory size should be > 0")

        // Verify .tls section exists
        val pe = PeReader.read(binary)
        val tlsSection = pe.sectionByName(".tls")
        assertNotNull(tlsSection, ".tls section should exist")
    }

    @Test
    fun `tls directory is parseable from linked PE`() {
        val tlsData = ByteArray(16) { (it + 1).toByte() }
        val linker = PeLinker(tlsData = tlsData)
        val binary = linker.link(listOf(makeMainObj()))
        val pe = PeReader.read(binary)

        assertNotNull(pe.tlsDirectory, "TLS directory should be parsed")
        val tls = pe.tlsDirectory!!
        assertTrue(tls.rawDataStart > 0, "Raw data start should be set")
        assertTrue(tls.rawDataEnd > tls.rawDataStart, "Raw data end > start")
        assertEquals(tlsData.size.toLong(), tls.rawDataEnd - tls.rawDataStart, "TLS data size should match")
    }

    @Test
    fun `linker without resources produces no rsrc section`() {
        val linker = PeLinker()
        val binary = linker.link(listOf(makeMainObj()))
        val pe = PeReader.read(binary)
        assertNull(pe.sectionByName(".rsrc"), "No .rsrc section without resources")
    }

    @Test
    fun `linker without tls produces no tls section`() {
        val linker = PeLinker()
        val binary = linker.link(listOf(makeMainObj()))
        val pe = PeReader.read(binary)
        assertNull(pe.sectionByName(".tls"), "No .tls section without TLS data")
        assertNull(pe.tlsDirectory, "No TLS directory without TLS data")
    }

    @Test
    fun `resources from object file rsrc section`() {
        val rsrcData = ByteArray(128) { (it xor 0xAA).toByte() }
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                Section(".rsrc", SectionKind.RSRC, rsrcData, align = 4),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val binary = PeLinker().link(listOf(obj))
        val pe = PeReader.read(binary)
        assertNotNull(pe.sectionByName(".rsrc"), ".rsrc section should exist from ObjectFile")
    }

    @Test
    fun `resource directory model`() {
        val dir = PeResourceDirectory(entries = listOf(
            PeResourceEntry(id = PeResourceType.MANIFEST, directory = PeResourceDirectory(entries = listOf(
                PeResourceEntry(id = 1, directory = PeResourceDirectory(entries = listOf(
                    PeResourceEntry(id = 1033, data = PeResourceData(0x1000, 100, bytes = ByteArray(100)))
                )))
            )))
        ))
        assertEquals(1, dir.entries.size)
        assertEquals("#24", dir.entries[0].nameOrId)
        assertTrue(dir.entries[0].isDirectory)
        assertFalse(dir.entries[0].directory!!.entries[0].directory!!.entries[0].isDirectory)
    }

    @Test
    fun `resource type names`() {
        assertEquals("RT_MANIFEST", PeResourceType.nameOf(24))
        assertEquals("RT_VERSION", PeResourceType.nameOf(16))
        assertEquals("RT_ICON", PeResourceType.nameOf(3))
        assertEquals("RT_UNKNOWN(999)", PeResourceType.nameOf(999))
    }

    @Test
    fun `tls directory data class`() {
        val tls = TLSDirectory(
            rawDataStart = 0x140001000,
            rawDataEnd = 0x140001020,
            indexAddress = 0x140002000,
            callbacksAddress = 0x140002008,
        )
        assertEquals(0x140001000, tls.rawDataStart)
        assertEquals(0x140001020, tls.rawDataEnd)
        assertEquals(0L, tls.zeroFillSize)
        assertEquals(0, tls.characteristics)
    }

    @Test
    fun `pe file has resources field`() {
        val pe = PeFile(
            isPe = true,
            coffHeader = CoffHeader(0x8664, 1, 0, 0, 0, 0, 0),
            optionalHeader = null,
            sections = emptyList(),
            symbols = emptyList(),
            importDirectories = emptyList(),
            delayImportDirectories = emptyList(),
            exportDirectory = null,
            baseRelocations = emptyList(),
            clrMetadata = null,
            dataDirectories = emptyList(),
            resources = PeResourceDirectory(entries = emptyList()),
            tlsDirectory = null,
        )
        assertNotNull(pe.resources)
        assertEquals(0, pe.resources!!.entries.size)
    }
}
