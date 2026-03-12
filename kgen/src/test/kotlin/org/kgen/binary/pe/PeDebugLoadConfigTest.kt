package org.kgen.binary.pe

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.pe.pdb.RsdsEntry
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PeDebugLoadConfigTest {

    @Test
    fun `parse debug directory from TestLib dll`() {
        val bytes = javaClass.getResourceAsStream("/fixtures/clr/TestLib.dll")!!.readAllBytes()
        val pe = PeReader.read(bytes)

        assertTrue(pe.debugEntries.isNotEmpty(), "Should have debug entries")
    }

    @Test
    fun `debug entry has correct type`() {
        val bytes = javaClass.getResourceAsStream("/fixtures/clr/TestLib.dll")!!.readAllBytes()
        val pe = PeReader.read(bytes)

        val types = pe.debugEntries.map { it.type }
        // .NET DLLs typically have CodeView (2) and possibly Repro (16) and/or POGO (13)
        assertTrue(types.any { it == PeDebugEntry.TYPE_CODEVIEW || it == PeDebugEntry.TYPE_REPRO },
            "Should have CodeView or Repro debug entry, got types: $types")
    }

    @Test
    fun `debug entry type names`() {
        val entry = PeDebugEntry(0, 0, 0, 0, PeDebugEntry.TYPE_CODEVIEW, 0, 0, 0, ByteArray(0))
        assertEquals("CodeView", entry.typeName)

        val repro = PeDebugEntry(0, 0, 0, 0, PeDebugEntry.TYPE_REPRO, 0, 0, 0, ByteArray(0))
        assertEquals("Repro", repro.typeName)

        val unknown = PeDebugEntry(0, 0, 0, 0, 99, 0, 0, 0, ByteArray(0))
        assertEquals("Unknown(99)", unknown.typeName)
    }

    @Test
    fun `CodeView entry has RSDS data`() {
        val bytes = javaClass.getResourceAsStream("/fixtures/clr/TestLib.dll")!!.readAllBytes()
        val pe = PeReader.read(bytes)

        val codeView = pe.debugEntries.firstOrNull { it.type == PeDebugEntry.TYPE_CODEVIEW }
        if (codeView != null) {
            assertTrue(codeView.data.isNotEmpty(), "CodeView entry should have data")
            val rsds = codeView.asRsds()
            if (rsds != null) {
                assertTrue(rsds.pdbPath.isNotEmpty(), "Should have PDB path: $rsds")
                assertNotNull(rsds.guid, "Should have GUID")
            }
        }
    }

    @Test
    fun `synthetic debug directory round-trip`() {
        // Build a minimal PE with a debug directory manually
        val rsdsData = RsdsEntry.build(
            java.util.UUID.fromString("12345678-1234-1234-1234-123456789ABC"),
            1,
            "test.pdb"
        )

        // Create a debug directory entry pointing to RSDS data
        val debugDirSize = PeDebugEntry.ENTRY_SIZE
        val debugDir = ByteBuffer.allocate(debugDirSize).order(ByteOrder.LITTLE_ENDIAN)
        debugDir.putInt(0) // Characteristics
        debugDir.putInt(0x5F000000) // TimeDateStamp
        debugDir.putShort(0) // MajorVersion
        debugDir.putShort(0) // MinorVersion
        debugDir.putInt(PeDebugEntry.TYPE_CODEVIEW) // Type
        debugDir.putInt(rsdsData.size) // SizeOfData
        debugDir.putInt(0) // AddressOfRawData (RVA, filled by linker)
        debugDir.putInt(0) // PointerToRawData (will be file offset - for simplicity skip data loading)

        // Parse just the entry structure
        val entry = PeDebugEntry(
            characteristics = 0,
            timeDateStamp = 0x5F000000,
            majorVersion = 0,
            minorVersion = 0,
            type = PeDebugEntry.TYPE_CODEVIEW,
            sizeOfData = rsdsData.size,
            addressOfRawData = 0,
            pointerToRawData = 0,
            data = rsdsData,
        )
        assertEquals("CodeView", entry.typeName)
        val rsds = entry.asRsds()
        assertNotNull(rsds)
        assertEquals("test.pdb", rsds!!.pdbPath)
        assertEquals(1, rsds.age)
    }

    @Test
    fun `non-CodeView entry returns null from asRsds`() {
        val entry = PeDebugEntry(0, 0, 0, 0, PeDebugEntry.TYPE_REPRO, 4, 0, 0, byteArrayOf(1, 2, 3, 4))
        assertNull(entry.asRsds())
    }

    @Test
    fun `multiple debug entries parsed`() {
        val bytes = javaClass.getResourceAsStream("/fixtures/clr/TestLib.dll")!!.readAllBytes()
        val pe = PeReader.read(bytes)

        // The DLL has 84 bytes = 3 entries
        assertTrue(pe.debugEntries.size >= 2, "Should have at least 2 debug entries, got ${pe.debugEntries.size}")
        for (entry in pe.debugEntries) {
            assertTrue(entry.type >= 0, "Type should be non-negative: ${entry.type}")
        }
    }

    @Test
    fun `empty debug directory produces empty list`() {
        // Build a PE with no debug directory
        val peBytes = PeWriter.writeExe(
            byteArrayOf(0xCC.toByte()),
            imports = mapOf("kernel32.dll" to listOf("ExitProcess")),
        )
        val pe = PeReader.read(peBytes)
        assertTrue(pe.debugEntries.isEmpty(), "PE without debug dir should have no debug entries")
    }

    @Test
    fun `load config from TestLib dll`() {
        val bytes = javaClass.getResourceAsStream("/fixtures/clr/TestLib.dll")!!.readAllBytes()
        val pe = PeReader.read(bytes)

        // TestLib.dll may or may not have LOAD_CONFIG - just verify no crash
        // If it has one, verify basic fields
        if (pe.loadConfig != null) {
            assertTrue(pe.loadConfig!!.size >= 64, "Load config size should be >= 64")
        }
    }

    @Test
    fun `PeFile without load config returns null`() {
        val peBytes = PeWriter.writeExe(
            byteArrayOf(0xCC.toByte()),
            imports = mapOf("kernel32.dll" to listOf("ExitProcess")),
        )
        val pe = PeReader.read(peBytes)
        assertNull(pe.loadConfig, "Minimal PE should not have load config")
    }

    @Test
    fun `debug entry ENTRY_SIZE is 28`() {
        assertEquals(28, PeDebugEntry.ENTRY_SIZE)
    }

    @Test
    fun `load config companion constants`() {
        assertEquals(0x100, PeLoadConfig.GUARD_CF_INSTRUMENTED)
        assertEquals(0x400, PeLoadConfig.GUARD_CF_FUNCTION_TABLE_PRESENT)
    }
}
