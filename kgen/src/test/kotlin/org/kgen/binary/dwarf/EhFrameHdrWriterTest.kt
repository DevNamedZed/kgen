package org.kgen.binary.dwarf

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class EhFrameHdrWriterTest {

    @Test
    fun headerVersionIsOne() {
        val result = EhFrameHdrWriter.write(emptyList(), 100, emptyList())
        assertEquals(1, result[0].toInt())
    }

    @Test
    fun headerEncodingsAreCorrect() {
        val result = EhFrameHdrWriter.write(emptyList(), 100, emptyList())
        // eh_frame_ptr encoding: DW_EH_PE_pcrel | DW_EH_PE_sdata4 = 0x1B
        assertEquals(0x1B, result[1].toInt() and 0xFF)
        // fde_count encoding: DW_EH_PE_udata4 = 0x03
        assertEquals(0x03, result[2].toInt() and 0xFF)
        // table encoding: DW_EH_PE_datarel | DW_EH_PE_sdata4 = 0x3B
        assertEquals(0x3B, result[3].toInt() and 0xFF)
    }

    @Test
    fun emptyFdeListProducesHeaderOnly() {
        val result = EhFrameHdrWriter.write(emptyList(), 100, emptyList())
        // Header (4) + eh_frame_ptr (4) + fde_count (4) = 12 bytes
        assertEquals(12, result.size)
        // fde_count should be 0
        assertEquals(0, readI32(result, 8))
    }

    @Test
    fun ehFramePtrIsRelativeToField() {
        val ehFrameOffset = 200
        val result = EhFrameHdrWriter.write(emptyList(), ehFrameOffset, emptyList())
        // eh_frame_ptr at byte 4, value = ehFrameOffset - 4 (pc-relative from field position)
        assertEquals(ehFrameOffset - 4, readI32(result, 4))
    }

    @Test
    fun singleFdeEntryProducesOneTableEntry() {
        val fde = FdeEntry("func", codeOffset = 0x1000, codeLength = 64, instructions = emptyList())
        val result = EhFrameHdrWriter.write(listOf(fde), 300, listOf(48))
        // Header (4) + eh_frame_ptr (4) + fde_count (4) + 1 entry (8) = 20 bytes
        assertEquals(20, result.size)
        // fde_count = 1
        assertEquals(1, readI32(result, 8))
        // table[0].initial_location = code offset
        assertEquals(0x1000, readI32(result, 12))
        // table[0].fde_address = ehFrameOffset + fdeOffset[0]
        assertEquals(300 + 48, readI32(result, 16))
    }

    @Test
    fun multipleFdeEntriesProduceMultipleTableEntries() {
        val fdes = listOf(
            FdeEntry("a", codeOffset = 0x100, codeLength = 32, instructions = emptyList()),
            FdeEntry("b", codeOffset = 0x200, codeLength = 64, instructions = emptyList()),
            FdeEntry("c", codeOffset = 0x300, codeLength = 16, instructions = emptyList()),
        )
        val offsets = listOf(48, 80, 112)
        val result = EhFrameHdrWriter.write(fdes, 500, offsets)
        // Header (4) + eh_frame_ptr (4) + fde_count (4) + 3 entries (24) = 36 bytes
        assertEquals(36, result.size)
        assertEquals(3, readI32(result, 8))
        // Verify each entry
        for (i in fdes.indices) {
            val base = 12 + i * 8
            assertEquals(fdes[i].codeOffset.toInt(), readI32(result, base))
            assertEquals(500 + offsets[i], readI32(result, base + 4))
        }
    }

    @Test
    fun simplifiedWriteComputesFdeOffsets() {
        val cie = CieEntry()
        val fde = FdeEntry("func", codeOffset = 0, codeLength = 100, instructions = emptyList())
        val result = EhFrameHdrWriter.write(cie, listOf(fde), 0)
        // Should produce valid output with 1 FDE
        assertTrue(result.size >= 20)
        assertEquals(1, readI32(result, 8))
    }

    @Test
    fun simplifiedWriteMatchesExplicitOffsets() {
        val cie = CieEntry()
        val fdes = listOf(
            FdeEntry("a", codeOffset = 0, codeLength = 50, instructions = emptyList()),
            FdeEntry("b", codeOffset = 50, codeLength = 50, instructions = emptyList()),
        )
        val simplified = EhFrameHdrWriter.write(cie, fdes, 100)
        // Manually compute offsets
        val cieBytes = EhFrameWriter.writeCieOnly(cie)
        val fdeOffsets = mutableListOf<Int>()
        var offset = cieBytes.size
        for (fde in fdes) {
            fdeOffsets.add(offset)
            offset += EhFrameWriter.writeFdeOnly(fde, cieBytes.size).size
        }
        val explicit = EhFrameHdrWriter.write(fdes, 100, fdeOffsets)
        assertArrayEquals(explicit, simplified)
    }

    @Test
    fun mismatchedSizesThrows() {
        val fde = FdeEntry("func", codeOffset = 0, codeLength = 100, instructions = emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            EhFrameHdrWriter.write(listOf(fde), 0, emptyList())
        }
    }

    private fun readI32(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
