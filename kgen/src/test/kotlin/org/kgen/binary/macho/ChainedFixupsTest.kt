package org.kgen.binary.macho

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ChainedFixupsTest {

    @Test
    fun `parses chained fixups with single import`() {
        val bytes = buildMachOWithChainedFixups(
            imports = listOf(TestImport("_printf", libOrdinal = 1, weak = false)),
            segments = listOf(TestSegmentStarts(
                segIndex = 1,
                pointerFormat = ChainedPointerFormat.DYLD_CHAINED_PTR_64,
                pageSize = 0x4000,
                segmentOffset = 0x8000L,
                pageStarts = listOf(0x0010),
            )),
        )
        val macho = MachOReader.read(bytes)
        val fixups = macho.chainedFixups
        assertNotNull(fixups)
        assertEquals(0, fixups!!.fixupsVersion)
        assertEquals(ChainedImportFormat.DYLD_CHAINED_IMPORT, fixups.importsFormat)
        assertEquals(1, fixups.imports.size)
        assertEquals("_printf", fixups.imports[0].name)
        assertEquals(1, fixups.imports[0].libOrdinal)
        assertFalse(fixups.imports[0].weakImport)
        assertEquals(0L, fixups.imports[0].addend)
    }

    @Test
    fun `parses multiple imports`() {
        val bytes = buildMachOWithChainedFixups(
            imports = listOf(
                TestImport("_malloc", libOrdinal = 1, weak = false),
                TestImport("_free", libOrdinal = 1, weak = false),
                TestImport("_dlopen", libOrdinal = 2, weak = true),
            ),
            segments = emptyList(),
        )
        val macho = MachOReader.read(bytes)
        val fixups = macho.chainedFixups!!
        assertEquals(3, fixups.imports.size)
        assertEquals("_malloc", fixups.imports[0].name)
        assertEquals("_free", fixups.imports[1].name)
        assertEquals("_dlopen", fixups.imports[2].name)
        assertEquals(2, fixups.imports[2].libOrdinal)
        assertTrue(fixups.imports[2].weakImport)
    }

    @Test
    fun `parses segment starts`() {
        val bytes = buildMachOWithChainedFixups(
            imports = listOf(TestImport("_foo", libOrdinal = 1, weak = false)),
            segments = listOf(
                TestSegmentStarts(
                    segIndex = 1,
                    pointerFormat = ChainedPointerFormat.DYLD_CHAINED_PTR_64,
                    pageSize = 0x4000,
                    segmentOffset = 0x8000L,
                    pageStarts = listOf(0x0010, 0xFFFF, 0x0020),
                ),
            ),
        )
        val macho = MachOReader.read(bytes)
        val fixups = macho.chainedFixups!!
        assertEquals(1, fixups.segments.size)
        val seg = fixups.segments[0]
        assertEquals(1, seg.segmentIndex)
        assertEquals(ChainedPointerFormat.DYLD_CHAINED_PTR_64, seg.pointerFormat)
        assertEquals(0x4000, seg.pageSize)
        assertEquals(0x8000L, seg.segmentOffset)
        assertEquals(3, seg.pageStarts.size)
        assertTrue(seg.hasFixupsOnPage(0))
        assertFalse(seg.hasFixupsOnPage(1))
        assertTrue(seg.hasFixupsOnPage(2))
    }

    @Test
    fun `parses multiple segments`() {
        val bytes = buildMachOWithChainedFixups(
            imports = emptyList(),
            segments = listOf(
                TestSegmentStarts(
                    segIndex = 1,
                    pointerFormat = ChainedPointerFormat.DYLD_CHAINED_PTR_64,
                    pageSize = 0x4000,
                    segmentOffset = 0x4000L,
                    pageStarts = listOf(0x0008),
                ),
                TestSegmentStarts(
                    segIndex = 3,
                    pointerFormat = ChainedPointerFormat.DYLD_CHAINED_PTR_64,
                    pageSize = 0x4000,
                    segmentOffset = 0xC000L,
                    pageStarts = listOf(0x0010, 0x0020),
                ),
            ),
        )
        val macho = MachOReader.read(bytes)
        val fixups = macho.chainedFixups!!
        assertEquals(2, fixups.segments.size)
        assertEquals(1, fixups.segments[0].segmentIndex)
        assertEquals(0x4000L, fixups.segments[0].segmentOffset)
        assertEquals(3, fixups.segments[1].segmentIndex)
        assertEquals(0xC000L, fixups.segments[1].segmentOffset)
        assertEquals(2, fixups.segments[1].pageStarts.size)
    }

    @Test
    fun `parses weak import flag`() {
        val bytes = buildMachOWithChainedFixups(
            imports = listOf(
                TestImport("_strong", libOrdinal = 1, weak = false),
                TestImport("_weak", libOrdinal = 1, weak = true),
            ),
            segments = emptyList(),
        )
        val macho = MachOReader.read(bytes)
        val fixups = macho.chainedFixups!!
        assertFalse(fixups.imports[0].weakImport)
        assertTrue(fixups.imports[1].weakImport)
    }

    @Test
    fun `no chained fixups when load command absent`() {
        val bytes = buildMinimalMachO64()
        val macho = MachOReader.read(bytes)
        assertNull(macho.chainedFixups)
    }

    @Test
    fun `chained fixup imports project to ObjectFile symbols`() {
        val bytes = buildMachOWithChainedFixups(
            imports = listOf(
                TestImport("_imported_func", libOrdinal = 1, weak = false),
                TestImport("_weak_func", libOrdinal = 2, weak = true),
            ),
            segments = emptyList(),
        )
        val obj = MachOReader.toObjectFile(MachOReader.read(bytes))
        val imported = obj.symbols.firstOrNull { it.name == "_imported_func" }
        assertNotNull(imported)
        assertTrue(imported!!.flags.contains(org.kgen.binary.SymbolFlag.UNDEFINED))

        val weak = obj.symbols.firstOrNull { it.name == "_weak_func" }
        assertNotNull(weak)
        assertTrue(weak!!.flags.contains(org.kgen.binary.SymbolFlag.WEAK_REF))
    }

    @Test
    fun `parses addend format imports`() {
        val bytes = buildMachOWithChainedFixupsAddend(
            imports = listOf(TestImportAddend("_data", libOrdinal = 1, weak = false, addend = 16)),
        )
        val macho = MachOReader.read(bytes)
        val fixups = macho.chainedFixups!!
        assertEquals(ChainedImportFormat.DYLD_CHAINED_IMPORT_ADDEND, fixups.importsFormat)
        assertEquals(1, fixups.imports.size)
        assertEquals("_data", fixups.imports[0].name)
        assertEquals(16L, fixups.imports[0].addend)
    }

    @Test
    fun `parses ARM64E pointer format`() {
        val bytes = buildMachOWithChainedFixups(
            imports = listOf(TestImport("_objc_msgSend", libOrdinal = 1, weak = false)),
            segments = listOf(TestSegmentStarts(
                segIndex = 1,
                pointerFormat = ChainedPointerFormat.DYLD_CHAINED_PTR_ARM64E,
                pageSize = 0x4000,
                segmentOffset = 0x8000L,
                pageStarts = listOf(0x0010),
            )),
        )
        val macho = MachOReader.read(bytes)
        val seg = macho.chainedFixups!!.segments[0]
        assertEquals(ChainedPointerFormat.DYLD_CHAINED_PTR_ARM64E, seg.pointerFormat)
    }

    @Test
    fun `empty segments list when all seg offsets are zero`() {
        val bytes = buildMachOWithChainedFixups(
            imports = listOf(TestImport("_foo", libOrdinal = 1, weak = false)),
            segments = emptyList(),
        )
        val macho = MachOReader.read(bytes)
        val fixups = macho.chainedFixups!!
        assertTrue(fixups.segments.isEmpty())
    }

    private data class TestImport(val name: String, val libOrdinal: Int, val weak: Boolean)
    private data class TestImportAddend(val name: String, val libOrdinal: Int, val weak: Boolean, val addend: Int)
    private data class TestSegmentStarts(
        val segIndex: Int,
        val pointerFormat: ChainedPointerFormat,
        val pageSize: Int,
        val segmentOffset: Long,
        val pageStarts: List<Int>,
    )

    private fun buildMinimalMachO64(): ByteArray {
        val buf = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        writeMinimalHeader(buf, ncmds = 1, sizeOfCmds = 72)
        writeEmptySegment(buf)
        val result = ByteArray(buf.position())
        buf.flip()
        buf.get(result)
        return result
    }

    private fun buildMachOWithChainedFixups(
        imports: List<TestImport>,
        segments: List<TestSegmentStarts>,
    ): ByteArray {
        val fixupsData = buildChainedFixupsPayload(imports, segments)
        return assembleMachOWithFixups(fixupsData)
    }

    private fun buildMachOWithChainedFixupsAddend(
        imports: List<TestImportAddend>,
    ): ByteArray {
        val fixupsData = buildChainedFixupsPayloadAddend(imports)
        return assembleMachOWithFixups(fixupsData)
    }

    private fun assembleMachOWithFixups(fixupsData: ByteArray): ByteArray {
        val headerSize = 32
        val segCmdSize = 72
        val chainedCmdSize = 16
        val loadCmdsSize = segCmdSize + chainedCmdSize
        val dataStart = ((headerSize + loadCmdsSize + 7) / 8) * 8
        val totalSize = dataStart + fixupsData.size

        val buf = ByteBuffer.allocate(totalSize + 256).order(ByteOrder.LITTLE_ENDIAN)
        writeMinimalHeader(buf, ncmds = 2, sizeOfCmds = loadCmdsSize)
        writeEmptySegment(buf)

        // LC_DYLD_CHAINED_FIXUPS
        buf.putInt(MachO.LC_DYLD_CHAINED_FIXUPS)
        buf.putInt(chainedCmdSize)
        buf.putInt(dataStart)
        buf.putInt(fixupsData.size)

        // Pad to dataStart
        while (buf.position() < dataStart) buf.put(0)

        buf.put(fixupsData)

        val result = ByteArray(buf.position())
        buf.flip()
        buf.get(result)
        return result
    }

    private fun buildChainedFixupsPayload(
        imports: List<TestImport>,
        segments: List<TestSegmentStarts>,
    ): ByteArray {
        val symbolPool = buildSymbolPool(imports.map { it.name })
        val symbolOffsets = computeSymbolOffsets(imports.map { it.name })

        // Compute starts-in-image layout
        val maxSegIndex = if (segments.isEmpty()) 0 else segments.maxOf { it.segIndex } + 1
        val segCount = maxOf(maxSegIndex, 1)
        val startsHeaderSize = 4 + segCount * 4
        val segStartsData = segments.map { buildSegmentStartsData(it) }
        val segInfoOffsets = computeSegInfoOffsets(segments, segCount, startsHeaderSize, segStartsData)
        val startsSize = startsHeaderSize + segStartsData.sumOf { it.size }

        val headerSize = 28
        val startsOffset = headerSize
        val importsOffset = startsOffset + startsSize
        val symbolsOffset = importsOffset + imports.size * 4

        val buf = ByteBuffer.allocate(symbolsOffset + symbolPool.size + 256).order(ByteOrder.LITTLE_ENDIAN)

        // Header
        buf.putInt(0) // fixups_version
        buf.putInt(startsOffset)
        buf.putInt(importsOffset)
        buf.putInt(symbolsOffset)
        buf.putInt(imports.size)
        buf.putInt(ChainedImportFormat.DYLD_CHAINED_IMPORT.code)
        buf.putInt(0) // symbols_format

        // Starts-in-image
        buf.putInt(segCount)
        for (i in 0 until segCount) {
            buf.putInt(segInfoOffsets[i])
        }
        for (data in segStartsData) {
            buf.put(data)
        }

        // Imports (DYLD_CHAINED_IMPORT format: 4 bytes each)
        for ((idx, imp) in imports.withIndex()) {
            val packed = (imp.libOrdinal and 0xFF) or
                ((if (imp.weak) 1 else 0) shl 8) or
                ((symbolOffsets[idx] and 0x7FFFFF) shl 9)
            buf.putInt(packed)
        }

        // Symbol pool
        buf.put(symbolPool)

        val result = ByteArray(buf.position())
        buf.flip()
        buf.get(result)
        return result
    }

    private fun buildChainedFixupsPayloadAddend(imports: List<TestImportAddend>): ByteArray {
        val symbolPool = buildSymbolPool(imports.map { it.name })
        val symbolOffsets = computeSymbolOffsets(imports.map { it.name })

        val headerSize = 28
        val startsOffset = headerSize
        val startsSize = 4 + 4 // seg_count=1, one zero offset
        val importsOffset = startsOffset + startsSize
        val symbolsOffset = importsOffset + imports.size * 8

        val buf = ByteBuffer.allocate(symbolsOffset + symbolPool.size + 256).order(ByteOrder.LITTLE_ENDIAN)

        buf.putInt(0) // fixups_version
        buf.putInt(startsOffset)
        buf.putInt(importsOffset)
        buf.putInt(symbolsOffset)
        buf.putInt(imports.size)
        buf.putInt(ChainedImportFormat.DYLD_CHAINED_IMPORT_ADDEND.code)
        buf.putInt(0) // symbols_format

        // Starts-in-image: 1 segment, offset=0 (no starts)
        buf.putInt(1)
        buf.putInt(0)

        // Imports (DYLD_CHAINED_IMPORT_ADDEND format: 8 bytes each)
        for ((idx, imp) in imports.withIndex()) {
            val packed = (imp.libOrdinal and 0xFF) or
                ((if (imp.weak) 1 else 0) shl 8) or
                ((symbolOffsets[idx] and 0x7FFFFF) shl 9)
            buf.putInt(packed)
            buf.putInt(imp.addend)
        }

        buf.put(symbolPool)

        val result = ByteArray(buf.position())
        buf.flip()
        buf.get(result)
        return result
    }

    private fun buildSegmentStartsData(seg: TestSegmentStarts): ByteArray {
        val size = 22 + seg.pageStarts.size * 2
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(size)
        buf.putShort(seg.pageSize.toShort())
        buf.putShort(seg.pointerFormat.code.toShort())
        buf.putLong(seg.segmentOffset)
        buf.putInt(0) // max_valid_pointer
        buf.putShort(seg.pageStarts.size.toShort())
        for (ps in seg.pageStarts) {
            buf.putShort(ps.toShort())
        }
        buf.flip()
        val result = ByteArray(buf.remaining())
        buf.get(result)
        return result
    }

    private fun computeSegInfoOffsets(
        segments: List<TestSegmentStarts>,
        segCount: Int,
        startsHeaderSize: Int,
        segStartsData: List<ByteArray>,
    ): IntArray {
        val offsets = IntArray(segCount)
        var dataOffset = startsHeaderSize
        val segByIndex = segments.associateBy { it.segIndex }
        for (i in 0 until segCount) {
            val seg = segByIndex[i]
            if (seg != null) {
                offsets[i] = dataOffset
                val idx = segments.indexOf(seg)
                dataOffset += segStartsData[idx].size
            }
        }
        return offsets
    }

    private fun buildSymbolPool(names: List<String>): ByteArray {
        val pool = java.io.ByteArrayOutputStream()
        for (name in names) {
            pool.write(name.toByteArray(Charsets.US_ASCII))
            pool.write(0)
        }
        return pool.toByteArray()
    }

    private fun computeSymbolOffsets(names: List<String>): List<Int> {
        val offsets = mutableListOf<Int>()
        var pos = 0
        for (name in names) {
            offsets.add(pos)
            pos += name.length + 1
        }
        return offsets
    }

    private fun writeMinimalHeader(buf: ByteBuffer, ncmds: Int, sizeOfCmds: Int) {
        buf.putInt(MachO.MH_MAGIC_64.toInt())
        buf.putInt(MachO.CPU_TYPE_X86_64)
        buf.putInt(MachO.CPU_SUBTYPE_ALL)
        buf.putInt(MachO.MH_EXECUTE)
        buf.putInt(ncmds)
        buf.putInt(sizeOfCmds)
        buf.putInt(0) // flags
        buf.putInt(0) // reserved
    }

    private fun writeEmptySegment(buf: ByteBuffer) {
        buf.putInt(MachO.LC_SEGMENT_64)
        buf.putInt(72) // cmdsize (no sections)
        for (i in 0 until 16) buf.put(0) // segname
        buf.putLong(0) // vmaddr
        buf.putLong(0) // vmsize
        buf.putLong(0) // fileoff
        buf.putLong(0) // filesize
        buf.putInt(0) // maxprot
        buf.putInt(0) // initprot
        buf.putInt(0) // nsects
        buf.putInt(0) // flags
    }
}
