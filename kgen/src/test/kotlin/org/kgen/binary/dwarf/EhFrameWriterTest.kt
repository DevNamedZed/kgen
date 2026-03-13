package org.kgen.binary.dwarf

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.instructions.*

class EhFrameWriterTest {

    @Test
    fun writeEmptyFdeListProducesTerminator() {
        val cie = CieEntry()
        val result = EhFrameWriter.write(cie, emptyList())
        // Should have CIE + terminator (4 zero bytes)
        assertTrue(result.size > 4)
        // Last 4 bytes should be the zero-length terminator
        val len = result.size
        assertEquals(0, result[len - 4].toInt())
        assertEquals(0, result[len - 3].toInt())
        assertEquals(0, result[len - 2].toInt())
        assertEquals(0, result[len - 1].toInt())
    }

    @Test
    fun writeCieHasCorrectVersion() {
        val cie = CieEntry(version = 1)
        val result = EhFrameWriter.write(cie, emptyList())
        // CIE layout: [4-byte length] [4-byte CIE ID = 0] [1-byte version]
        assertEquals(1, result[8].toInt())
    }

    @Test
    fun writeCieIdIsZero() {
        val cie = CieEntry()
        val result = EhFrameWriter.write(cie, emptyList())
        // CIE ID at bytes 4-7 should be 0 for .eh_frame
        assertEquals(0, readInt32(result, 4))
    }

    @Test
    fun writeSingleFdeProducesNonEmptyData() {
        val cie = CieEntry()
        val fde = FdeEntry("main", 0, 64, listOf(
            CfiInstruction.defCfa(7, 8),
            CfiInstruction.offset(16, 1),
        ))
        val result = EhFrameWriter.write(cie, listOf(fde))
        assertTrue(result.size > 20)
    }

    @Test
    fun writeMultipleFdesProducesLargerOutput() {
        val cie = CieEntry()
        val fde1 = FdeEntry("func1", 0, 32, listOf(CfiInstruction.defCfa(7, 8)))
        val fde2 = FdeEntry("func2", 32, 48, listOf(CfiInstruction.defCfa(7, 8)))
        val singleResult = EhFrameWriter.write(cie, listOf(fde1))
        val doubleResult = EhFrameWriter.write(cie, listOf(fde1, fde2))
        assertTrue(doubleResult.size > singleResult.size)
    }

    @Test
    fun writeCieAugmentationString() {
        val cie = CieEntry(augmentation = "zR")
        val result = EhFrameWriter.write(cie, emptyList())
        // After version byte (index 8), augmentation string starts at index 9
        assertEquals('z'.code.toByte(), result[9])
        assertEquals('R'.code.toByte(), result[10])
        assertEquals(0, result[11].toInt()) // null terminator
    }

    @Test
    fun writeX86Defaults() {
        val cie = CieEntry(
            codeAlignFactor = 1,
            dataAlignFactor = -8,
            returnAddressRegister = 16,
            initialInstructions = listOf(
                CfiInstruction.defCfa(7, 8),
                CfiInstruction.offset(16, 1),
            )
        )
        val fde = FdeEntry("test", 0, 100, listOf(
            CfiInstruction.advanceLoc(1),
            CfiInstruction.defCfaOffset(16),
            CfiInstruction.offset(6, 2),
            CfiInstruction.advanceLoc(3),
            CfiInstruction.defCfaRegister(6),
        ))
        val result = EhFrameWriter.write(cie, listOf(fde))
        assertTrue(result.isNotEmpty())
        // Verify the first record length is non-zero
        assertTrue(readInt32(result, 0) > 0)
    }

    @Test
    fun writeArm64Defaults() {
        val cie = CieEntry(
            codeAlignFactor = 4,
            dataAlignFactor = -8,
            returnAddressRegister = 30,
            initialInstructions = listOf(CfiInstruction.defCfa(31, 0))
        )
        val fde = FdeEntry("arm64func", 0, 64, listOf(
            CfiInstruction.advanceLoc(1),
            CfiInstruction.defCfa(31, 48),
            CfiInstruction.offset(29, 6),
            CfiInstruction.offset(30, 5),
        ))
        val result = EhFrameWriter.write(cie, listOf(fde))
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun writeRiscVDefaults() {
        val cie = CieEntry(
            codeAlignFactor = 2,
            dataAlignFactor = -8,
            returnAddressRegister = 1,
            initialInstructions = listOf(CfiInstruction.defCfa(2, 0))
        )
        val fde = FdeEntry("rvfunc", 0, 32, listOf(
            CfiInstruction.advanceLoc(2),
            CfiInstruction.defCfaOffset(32),
        ))
        val result = EhFrameWriter.write(cie, listOf(fde))
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun cfiInstructionFactoryMethods() {
        assertEquals(CfiInstruction.DefCfa(7, 8), CfiInstruction.defCfa(7, 8))
        assertEquals(CfiInstruction.DefCfaOffset(16), CfiInstruction.defCfaOffset(16))
        assertEquals(CfiInstruction.DefCfaRegister(6), CfiInstruction.defCfaRegister(6))
        assertEquals(CfiInstruction.Offset(16, 1), CfiInstruction.offset(16, 1))
        assertEquals(CfiInstruction.AdvanceLoc(4), CfiInstruction.advanceLoc(4))
        assertEquals(CfiInstruction.Restore(6), CfiInstruction.restore(6))
        assertSame(CfiInstruction.RememberState, CfiInstruction.rememberState())
        assertSame(CfiInstruction.RestoreState, CfiInstruction.restoreState())
        assertSame(CfiInstruction.Nop, CfiInstruction.nop())
    }

    @Test
    fun advanceLocEncodingVariants() {
        val cie = CieEntry()
        // Small delta (< 64) uses inline encoding
        val fdeSmall = FdeEntry("f", 0, 10, listOf(CfiInstruction.advanceLoc(5)))
        val r1 = EhFrameWriter.write(cie, listOf(fdeSmall))

        // Medium delta (< 256) uses DW_CFA_advance_loc1
        val fdeMedium = FdeEntry("f", 0, 200, listOf(CfiInstruction.advanceLoc(100)))
        val r2 = EhFrameWriter.write(cie, listOf(fdeMedium))

        // Large delta (< 65536) uses DW_CFA_advance_loc2
        val fdeLarge = FdeEntry("f", 0, 50000, listOf(CfiInstruction.advanceLoc(30000)))
        val r3 = EhFrameWriter.write(cie, listOf(fdeLarge))

        // All should produce valid output
        assertTrue(r1.isNotEmpty())
        assertTrue(r2.isNotEmpty())
        assertTrue(r3.isNotEmpty())
    }

    @Test
    fun nopPaddingProducesAlignedBody() {
        val cie = CieEntry()
        val result = EhFrameWriter.write(cie, emptyList())
        // CIE body length should be a multiple of addrSize (8)
        val cieLen = readInt32(result, 0)
        assertEquals(0, cieLen % 8, "CIE body should be 8-byte aligned")
    }

    @Test
    fun rememberRestoreStateInstructions() {
        val cie = CieEntry()
        val fde = FdeEntry("f", 0, 50, listOf(
            CfiInstruction.defCfa(7, 8),
            CfiInstruction.rememberState(),
            CfiInstruction.defCfaOffset(16),
            CfiInstruction.restoreState(),
        ))
        val result = EhFrameWriter.write(cie, listOf(fde))
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun cieEntryDefaults() {
        val cie = CieEntry()
        assertEquals(1, cie.version)
        assertEquals(1, cie.codeAlignFactor)
        assertEquals(-8, cie.dataAlignFactor)
        assertEquals(16, cie.returnAddressRegister)
        assertEquals("zR", cie.augmentation)
        assertEquals(0x1B, cie.pointerEncoding)
    }

    @Test
    fun fdeEntryData() {
        val fde = FdeEntry("test", 100, 200, listOf(CfiInstruction.nop()))
        assertEquals("test", fde.functionName)
        assertEquals(100L, fde.codeOffset)
        assertEquals(200L, fde.codeLength)
        assertEquals(1, fde.instructions.size)
    }

    @Test
    fun write32BitAddressSize() {
        val cie = CieEntry()
        val fde = FdeEntry("f", 0, 32, listOf(CfiInstruction.defCfa(7, 8)))
        val result = EhFrameWriter.write(cie, listOf(fde), addrSize = 4)
        assertTrue(result.isNotEmpty())
        // 32-bit should produce smaller output than 64-bit
        val result64 = EhFrameWriter.write(cie, listOf(fde), addrSize = 8)
        assertTrue(result.size <= result64.size)
    }

    private fun readInt32(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
