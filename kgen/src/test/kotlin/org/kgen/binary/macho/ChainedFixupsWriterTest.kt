package org.kgen.binary.macho

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ChainedFixupsWriterTest {

    @Test
    fun writeEmptyFixups() {
        val fixups = ChainedFixups(
            fixupsVersion = 0,
            importsFormat = ChainedImportFormat.DYLD_CHAINED_IMPORT,
            symbolsFormat = 0,
            imports = emptyList(),
            segments = emptyList(),
        )
        val bytes = ChainedFixupsWriter.write(fixups)
        assertTrue(bytes.isNotEmpty())
        // Header is 28 bytes + starts-in-image (4 + 4 = 8) = 36 bytes min
        assertTrue(bytes.size >= 36)
    }

    @Test
    fun roundTripSingleImport() {
        val fixups = ChainedFixups(
            fixupsVersion = 0,
            importsFormat = ChainedImportFormat.DYLD_CHAINED_IMPORT,
            symbolsFormat = 0,
            imports = listOf(
                ChainedFixupImport("_printf", libOrdinal = 1, weakImport = false, addend = 0),
            ),
            segments = emptyList(),
        )
        val bytes = ChainedFixupsWriter.write(fixups)
        val macho = buildMachOWithFixupsPayload(bytes)
        val parsed = MachOReader.read(macho).chainedFixups!!

        assertEquals(0, parsed.fixupsVersion)
        assertEquals(ChainedImportFormat.DYLD_CHAINED_IMPORT, parsed.importsFormat)
        assertEquals(1, parsed.imports.size)
        assertEquals("_printf", parsed.imports[0].name)
        assertEquals(1, parsed.imports[0].libOrdinal)
        assertFalse(parsed.imports[0].weakImport)
    }

    @Test
    fun roundTripMultipleImports() {
        val fixups = ChainedFixups(
            fixupsVersion = 0,
            importsFormat = ChainedImportFormat.DYLD_CHAINED_IMPORT,
            symbolsFormat = 0,
            imports = listOf(
                ChainedFixupImport("_malloc", libOrdinal = 1, weakImport = false, addend = 0),
                ChainedFixupImport("_free", libOrdinal = 1, weakImport = false, addend = 0),
                ChainedFixupImport("_dlopen", libOrdinal = 2, weakImport = true, addend = 0),
            ),
            segments = emptyList(),
        )
        val bytes = ChainedFixupsWriter.write(fixups)
        val macho = buildMachOWithFixupsPayload(bytes)
        val parsed = MachOReader.read(macho).chainedFixups!!

        assertEquals(3, parsed.imports.size)
        assertEquals("_malloc", parsed.imports[0].name)
        assertEquals("_free", parsed.imports[1].name)
        assertEquals("_dlopen", parsed.imports[2].name)
        assertEquals(2, parsed.imports[2].libOrdinal)
        assertTrue(parsed.imports[2].weakImport)
    }

    @Test
    fun roundTripWithSegmentStarts() {
        val fixups = ChainedFixups(
            fixupsVersion = 0,
            importsFormat = ChainedImportFormat.DYLD_CHAINED_IMPORT,
            symbolsFormat = 0,
            imports = listOf(
                ChainedFixupImport("_foo", libOrdinal = 1, weakImport = false, addend = 0),
            ),
            segments = listOf(
                ChainedFixupSegment(
                    segmentIndex = 1,
                    pointerFormat = ChainedPointerFormat.DYLD_CHAINED_PTR_64,
                    pageSize = 0x4000,
                    segmentOffset = 0x8000L,
                    maxValidPointer = 0L,
                    pageStarts = listOf(0x0010, 0xFFFF, 0x0020),
                ),
            ),
        )
        val bytes = ChainedFixupsWriter.write(fixups)
        val macho = buildMachOWithFixupsPayload(bytes)
        val parsed = MachOReader.read(macho).chainedFixups!!

        assertEquals(1, parsed.segments.size)
        val seg = parsed.segments[0]
        assertEquals(1, seg.segmentIndex)
        assertEquals(ChainedPointerFormat.DYLD_CHAINED_PTR_64, seg.pointerFormat)
        assertEquals(0x4000, seg.pageSize)
        assertEquals(0x8000L, seg.segmentOffset)
        assertEquals(3, seg.pageStarts.size)
        assertEquals(0x0010, seg.pageStarts[0])
        assertEquals(0xFFFF, seg.pageStarts[1])
        assertEquals(0x0020, seg.pageStarts[2])
    }

    @Test
    fun roundTripMultipleSegments() {
        val fixups = ChainedFixups(
            fixupsVersion = 0,
            importsFormat = ChainedImportFormat.DYLD_CHAINED_IMPORT,
            symbolsFormat = 0,
            imports = emptyList(),
            segments = listOf(
                ChainedFixupSegment(
                    segmentIndex = 1,
                    pointerFormat = ChainedPointerFormat.DYLD_CHAINED_PTR_64,
                    pageSize = 0x4000,
                    segmentOffset = 0x4000L,
                    maxValidPointer = 0L,
                    pageStarts = listOf(0x0008),
                ),
                ChainedFixupSegment(
                    segmentIndex = 3,
                    pointerFormat = ChainedPointerFormat.DYLD_CHAINED_PTR_64,
                    pageSize = 0x4000,
                    segmentOffset = 0xC000L,
                    maxValidPointer = 0L,
                    pageStarts = listOf(0x0010, 0x0020),
                ),
            ),
        )
        val bytes = ChainedFixupsWriter.write(fixups)
        val macho = buildMachOWithFixupsPayload(bytes)
        val parsed = MachOReader.read(macho).chainedFixups!!

        assertEquals(2, parsed.segments.size)
        assertEquals(1, parsed.segments[0].segmentIndex)
        assertEquals(0x4000L, parsed.segments[0].segmentOffset)
        assertEquals(1, parsed.segments[0].pageStarts.size)
        assertEquals(3, parsed.segments[1].segmentIndex)
        assertEquals(0xC000L, parsed.segments[1].segmentOffset)
        assertEquals(2, parsed.segments[1].pageStarts.size)
    }

    @Test
    fun roundTripAddendFormat() {
        val fixups = ChainedFixups(
            fixupsVersion = 0,
            importsFormat = ChainedImportFormat.DYLD_CHAINED_IMPORT_ADDEND,
            symbolsFormat = 0,
            imports = listOf(
                ChainedFixupImport("_data", libOrdinal = 1, weakImport = false, addend = 16),
            ),
            segments = emptyList(),
        )
        val bytes = ChainedFixupsWriter.write(fixups)
        val macho = buildMachOWithFixupsPayload(bytes)
        val parsed = MachOReader.read(macho).chainedFixups!!

        assertEquals(ChainedImportFormat.DYLD_CHAINED_IMPORT_ADDEND, parsed.importsFormat)
        assertEquals(1, parsed.imports.size)
        assertEquals("_data", parsed.imports[0].name)
        assertEquals(16L, parsed.imports[0].addend)
    }

    @Test
    fun roundTripWeakImports() {
        val fixups = ChainedFixups(
            fixupsVersion = 0,
            importsFormat = ChainedImportFormat.DYLD_CHAINED_IMPORT,
            symbolsFormat = 0,
            imports = listOf(
                ChainedFixupImport("_strong", libOrdinal = 1, weakImport = false, addend = 0),
                ChainedFixupImport("_weak", libOrdinal = 1, weakImport = true, addend = 0),
            ),
            segments = emptyList(),
        )
        val bytes = ChainedFixupsWriter.write(fixups)
        val macho = buildMachOWithFixupsPayload(bytes)
        val parsed = MachOReader.read(macho).chainedFixups!!

        assertFalse(parsed.imports[0].weakImport)
        assertTrue(parsed.imports[1].weakImport)
    }

    @Test
    fun roundTripArm64ePointerFormat() {
        val fixups = ChainedFixups(
            fixupsVersion = 0,
            importsFormat = ChainedImportFormat.DYLD_CHAINED_IMPORT,
            symbolsFormat = 0,
            imports = listOf(
                ChainedFixupImport("_objc_msgSend", libOrdinal = 1, weakImport = false, addend = 0),
            ),
            segments = listOf(
                ChainedFixupSegment(
                    segmentIndex = 1,
                    pointerFormat = ChainedPointerFormat.DYLD_CHAINED_PTR_ARM64E,
                    pageSize = 0x4000,
                    segmentOffset = 0x8000L,
                    maxValidPointer = 0L,
                    pageStarts = listOf(0x0010),
                ),
            ),
        )
        val bytes = ChainedFixupsWriter.write(fixups)
        val macho = buildMachOWithFixupsPayload(bytes)
        val parsed = MachOReader.read(macho).chainedFixups!!

        assertEquals(ChainedPointerFormat.DYLD_CHAINED_PTR_ARM64E, parsed.segments[0].pointerFormat)
    }

    @Test
    fun headerLayoutIsCorrect() {
        val fixups = ChainedFixups(
            fixupsVersion = 0,
            importsFormat = ChainedImportFormat.DYLD_CHAINED_IMPORT,
            symbolsFormat = 0,
            imports = listOf(
                ChainedFixupImport("_test", libOrdinal = 1, weakImport = false, addend = 0),
            ),
            segments = emptyList(),
        )
        val bytes = ChainedFixupsWriter.write(fixups)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Verify header fields
        assertEquals(0, buf.getInt(0))  // fixups_version
        val startsOffset = buf.getInt(4)
        val importsOffset = buf.getInt(8)
        val symbolsOffset = buf.getInt(12)
        assertEquals(1, buf.getInt(16)) // imports_count
        assertEquals(1, buf.getInt(20)) // imports_format (DYLD_CHAINED_IMPORT)
        assertEquals(0, buf.getInt(24)) // symbols_format

        // Offsets should be increasing
        assertTrue(startsOffset < importsOffset)
        assertTrue(importsOffset < symbolsOffset)
        assertTrue(symbolsOffset < bytes.size)

        // Stars offset should be right after header
        assertEquals(28, startsOffset)
    }

    @Test
    fun symbolPoolContainsNullTerminatedNames() {
        val fixups = ChainedFixups(
            fixupsVersion = 0,
            importsFormat = ChainedImportFormat.DYLD_CHAINED_IMPORT,
            symbolsFormat = 0,
            imports = listOf(
                ChainedFixupImport("_abc", libOrdinal = 1, weakImport = false, addend = 0),
                ChainedFixupImport("_xyz", libOrdinal = 1, weakImport = false, addend = 0),
            ),
            segments = emptyList(),
        )
        val bytes = ChainedFixupsWriter.write(fixups)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val symbolsOffset = buf.getInt(12)

        // Read the symbol pool
        val poolBytes = bytes.sliceArray(symbolsOffset until bytes.size)
        val poolStr = String(poolBytes, Charsets.US_ASCII)
        assertTrue(poolStr.contains("_abc"))
        assertTrue(poolStr.contains("_xyz"))
        // Each name is null-terminated
        assertEquals(0, poolBytes[4]) // after "_abc"
        assertEquals(0, poolBytes[9]) // after "_xyz"
    }

    // -- Helper: wraps fixups payload into a minimal Mach-O for reading back --

    private fun buildMachOWithFixupsPayload(fixupsData: ByteArray): ByteArray {
        val headerSize = 32
        val segCmdSize = 72
        val chainedCmdSize = 16
        val loadCmdsSize = segCmdSize + chainedCmdSize
        val dataStart = ((headerSize + loadCmdsSize + 7) / 8) * 8
        val totalSize = dataStart + fixupsData.size

        val buf = ByteBuffer.allocate(totalSize + 256).order(ByteOrder.LITTLE_ENDIAN)

        // Mach-O header
        buf.putInt(MachO.MH_MAGIC_64.toInt())
        buf.putInt(MachO.CPU_TYPE_X86_64)
        buf.putInt(MachO.CPU_SUBTYPE_ALL)
        buf.putInt(MachO.MH_EXECUTE)
        buf.putInt(2) // ncmds
        buf.putInt(loadCmdsSize)
        buf.putInt(0) // flags
        buf.putInt(0) // reserved

        // LC_SEGMENT_64 (empty)
        buf.putInt(MachO.LC_SEGMENT_64)
        buf.putInt(72)
        for (i in 0 until 16) { buf.put(0) }
        buf.putLong(0); buf.putLong(0); buf.putLong(0); buf.putLong(0)
        buf.putInt(0); buf.putInt(0); buf.putInt(0); buf.putInt(0)

        // LC_DYLD_CHAINED_FIXUPS
        buf.putInt(MachO.LC_DYLD_CHAINED_FIXUPS)
        buf.putInt(chainedCmdSize)
        buf.putInt(dataStart)
        buf.putInt(fixupsData.size)

        // Pad to dataStart
        while (buf.position() < dataStart) { buf.put(0) }

        buf.put(fixupsData)

        val result = ByteArray(buf.position())
        buf.flip()
        buf.get(result)
        return result
    }
}
