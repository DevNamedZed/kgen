package org.kgen.tools

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class HexDumpExtendedTest {

    // -- format() --

    @Test
    fun emptyArrayReturnsEmpty() {
        assertEquals("", HexDump.format(byteArrayOf()))
    }

    @Test
    fun singleByteHasAddress() {
        val result = HexDump.format(byteArrayOf(0x41))
        assertTrue(result.startsWith("00000000"))
    }

    @Test
    fun singleByteContainsHex() {
        val result = HexDump.format(byteArrayOf(0x41))
        assertTrue(result.contains("41"))
    }

    @Test
    fun singleByteContainsAscii() {
        val result = HexDump.format(byteArrayOf(0x41))
        assertTrue(result.contains("|A|"))
    }

    @Test
    fun sixteenBytesOneLine() {
        val bytes = ByteArray(16) { 0x41 }
        val lines = HexDump.format(bytes).trim().lines()
        assertEquals(1, lines.size)
    }

    @Test
    fun seventeenBytesTwoLines() {
        val bytes = ByteArray(17) { 0x41 }
        val lines = HexDump.format(bytes).trim().lines()
        assertEquals(2, lines.size)
    }

    @Test
    fun thirtyTwoBytesTwoLines() {
        val bytes = ByteArray(32) { it.toByte() }
        val lines = HexDump.format(bytes).trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("00000000"))
        assertTrue(lines[1].startsWith("00000010"))
    }

    @Test
    fun fortyEightBytesThreeLines() {
        val bytes = ByteArray(48) { it.toByte() }
        val lines = HexDump.format(bytes).trim().lines()
        assertEquals(3, lines.size)
    }

    @Test
    fun baseAddressIsRespected() {
        val result = HexDump.format(byteArrayOf(0), baseAddress = 0x401000)
        assertTrue(result.startsWith("00401000"))
    }

    @Test
    fun baseAddressOnSecondLine() {
        val bytes = ByteArray(32) { 0 }
        val lines = HexDump.format(bytes, baseAddress = 0x1000).trim().lines()
        assertTrue(lines[0].startsWith("00001000"))
        assertTrue(lines[1].startsWith("00001010"))
    }

    @Test
    fun nonPrintableAsDots() {
        val bytes = byteArrayOf(0x00, 0x01, 0x7f.toByte(), 0x41)
        val result = HexDump.format(bytes)
        assertTrue(result.contains("|...A|"), "Non-printable should be dots: $result")
    }

    @Test
    fun printableRange() {
        val bytes = byteArrayOf(0x20, 0x7E)
        val result = HexDump.format(bytes)
        assertTrue(result.contains("| ~|"), "Space and tilde should be printable: $result")
    }

    @Test
    fun noAsciiWhenDisabled() {
        val result = HexDump.format(byteArrayOf(0x41), showAscii = false)
        assertFalse(result.contains("|"))
    }

    @Test
    fun customBytesPerLine() {
        val bytes = ByteArray(8) { it.toByte() }
        val lines = HexDump.format(bytes, bytesPerLine = 8).trim().lines()
        assertEquals(1, lines.size)
    }

    @Test
    fun customBytesPerLineSplitsCorrectly() {
        val bytes = ByteArray(16) { it.toByte() }
        val lines = HexDump.format(bytes, bytesPerLine = 8).trim().lines()
        assertEquals(2, lines.size)
    }

    @Test
    fun largeAddress() {
        val result = HexDump.format(byteArrayOf(0), baseAddress = 0xFFFFFF00L)
        assertTrue(result.startsWith("ffffff00"))
    }

    // -- formatBytes() --

    @Test
    fun formatBytesEmpty() {
        assertEquals("", HexDump.formatBytes(byteArrayOf()))
    }

    @Test
    fun formatBytesSingle() {
        assertEquals("de", HexDump.formatBytes(byteArrayOf(0xDE.toByte())))
    }

    @Test
    fun formatBytesMultiple() {
        val result = HexDump.formatBytes(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()))
        assertEquals("de ad be ef", result)
    }

    @Test
    fun formatBytesCustomSeparator() {
        val result = HexDump.formatBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()), separator = ":")
        assertEquals("ca:fe", result)
    }

    @Test
    fun formatBytesNoSeparator() {
        val result = HexDump.formatBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()), separator = "")
        assertEquals("cafe", result)
    }

    @Test
    fun formatBytesZeroByte() {
        assertEquals("00", HexDump.formatBytes(byteArrayOf(0x00)))
    }

    @Test
    fun formatBytesAllZeros() {
        assertEquals("00 00 00", HexDump.formatBytes(byteArrayOf(0, 0, 0)))
    }

    @Test
    fun formatBytesMaxByte() {
        assertEquals("ff", HexDump.formatBytes(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun formatBytesLowercase() {
        val result = HexDump.formatBytes(byteArrayOf(0xAB.toByte()))
        assertEquals("ab", result)
    }
}
