package org.kgen.tools

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class HexDumpTest {

    @Test
    fun `formats empty array`() {
        assertEquals("", HexDump.format(byteArrayOf()))
    }

    @Test
    fun `formats single byte`() {
        val result = HexDump.format(byteArrayOf(0x41))
        assertTrue(result.startsWith("00000000"), "Should start with address: $result")
        assertTrue(result.contains("41"), "Should contain hex byte: $result")
        assertTrue(result.contains("|A|"), "Should contain ASCII: $result")
    }

    @Test
    fun `formats 16 bytes per line`() {
        val bytes = ByteArray(32) { it.toByte() }
        val lines = HexDump.format(bytes).trim().lines()
        assertEquals(2, lines.size, "32 bytes should produce 2 lines")
        assertTrue(lines[0].startsWith("00000000"))
        assertTrue(lines[1].startsWith("00000010"))
    }

    @Test
    fun `respects base address`() {
        val result = HexDump.format(byteArrayOf(0), baseAddress = 0x401000)
        assertTrue(result.startsWith("00401000"), "Should use base address: $result")
    }

    @Test
    fun `shows non-printable as dots`() {
        val bytes = byteArrayOf(0x00, 0x01, 0x7f.toByte(), 0x41)
        val result = HexDump.format(bytes)
        assertTrue(result.contains("|...A|"), "Non-printable should be dots: $result")
    }

    @Test
    fun `formatBytes produces hex string`() {
        val result = HexDump.formatBytes(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()))
        assertEquals("de ad be ef", result)
    }

    @Test
    fun `formatBytes with custom separator`() {
        val result = HexDump.formatBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()), separator = ":")
        assertEquals("ca:fe", result)
    }

    @Test
    fun `no ascii when disabled`() {
        val result = HexDump.format(byteArrayOf(0x41), showAscii = false)
        assertFalse(result.contains("|"), "Should not contain ASCII column: $result")
    }
}
