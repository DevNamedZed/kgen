package org.kgen.backend.arm64.disasm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class Arm64DisassemblerTest {

    private val disasm = Arm64Disassembler()

    private fun inst(vararg bytes: Int): ByteArray {
        val result = ByteArray(bytes.size)
        for (i in bytes.indices) result[i] = bytes[i].toByte()
        return result
    }

    private fun fromLE32(value: Long): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    @Test
    fun `decodes ADD X0 X1 X2`() {
        val result = disasm.disassemble(fromLE32(0x8B020020L))
        assertEquals(1, result.size)
        assertEquals("add", result[0].mnemonic)
        assertTrue(result[0].operandsStr.contains("x0"))
        assertTrue(result[0].operandsStr.contains("x1"))
        assertTrue(result[0].operandsStr.contains("x2"))
    }

    @Test
    fun `decodes SUB X0 X1 X2`() {
        val result = disasm.disassemble(fromLE32(0xCB020020L))
        assertEquals("sub", result[0].mnemonic)
    }

    @Test
    fun `decodes ADD X0 X1 imm`() {
        val result = disasm.disassemble(fromLE32(0x9100A820L))
        assertEquals("add", result[0].mnemonic)
        assertTrue(result[0].operandsStr.contains("#42"))
    }

    @Test
    fun `decodes MOV X0 X1 as ORR alias`() {
        val result = disasm.disassemble(fromLE32(0xAA0103E0L))
        assertEquals("mov", result[0].mnemonic)
        assertTrue(result[0].operandsStr.contains("x0"))
        assertTrue(result[0].operandsStr.contains("x1"))
    }

    @Test
    fun `decodes MOVZ X0 imm16`() {
        val result = disasm.disassemble(fromLE32(0xD2824680L))
        assertEquals("movz", result[0].mnemonic)
        assertTrue(result[0].operandsStr.contains("#4660") || result[0].operandsStr.contains("#0x1234"))
    }

    @Test
    fun `decodes MUL X0 X1 X2`() {
        val result = disasm.disassemble(fromLE32(0x9B027C20L))
        assertEquals("mul", result[0].mnemonic)
    }

    @Test
    fun `decodes SDIV X0 X1 X2`() {
        val result = disasm.disassemble(fromLE32(0x9AC20C20L))
        assertEquals("sdiv", result[0].mnemonic)
    }

    @Test
    fun `decodes CMP X0 X1`() {
        val result = disasm.disassemble(fromLE32(0xEB01001FL))
        assertEquals("cmp", result[0].mnemonic)
    }

    @Test
    fun `decodes RET`() {
        val result = disasm.disassemble(fromLE32(0xD65F03C0L))
        assertEquals("ret", result[0].mnemonic)
    }

    @Test
    fun `decodes NOP`() {
        val result = disasm.disassemble(fromLE32(0xD503201FL))
        assertEquals("nop", result[0].mnemonic)
    }

    @Test
    fun `decodes BRK`() {
        val result = disasm.disassemble(fromLE32(0xD4200020L))
        assertEquals("brk", result[0].mnemonic)
        assertEquals("#1", result[0].operandsStr)
    }

    @Test
    fun `decodes LDR X0 from SP`() {
        val result = disasm.disassemble(fromLE32(0xF9400BE0L))
        assertEquals("ldr", result[0].mnemonic)
        assertTrue(result[0].operandsStr.contains("x0"))
        assertTrue(result[0].operandsStr.contains("sp"))
        assertTrue(result[0].operandsStr.contains("#16"))
    }

    @Test
    fun `decodes STR X0 to SP`() {
        val result = disasm.disassemble(fromLE32(0xF90007E0L))
        assertEquals("str", result[0].mnemonic)
    }

    @Test
    fun `decodes SXTW X0 W1`() {
        val result = disasm.disassemble(fromLE32(0x93407C20L))
        assertEquals("sxtw", result[0].mnemonic)
    }

    @Test
    fun `decodes B unconditional`() {
        // B +8 (imm26 = 2): 0x14000002
        val result = disasm.disassemble(fromLE32(0x14000002L))
        assertEquals("b", result[0].mnemonic)
    }

    @Test
    fun `decodes BL`() {
        val result = disasm.disassemble(fromLE32(0x94000001L))
        assertEquals("bl", result[0].mnemonic)
    }

    @Test
    fun `decodes conditional branch`() {
        // B.EQ +8: 0x54000040
        val result = disasm.disassemble(fromLE32(0x54000040L))
        assertEquals("b.eq", result[0].mnemonic)
    }

    @Test
    fun `decodes BR X8`() {
        val result = disasm.disassemble(fromLE32(0xD61F0100L))
        assertEquals("br", result[0].mnemonic)
        assertTrue(result[0].operandsStr.contains("x8"))
    }

    @Test
    fun `decodes BLR X8`() {
        val result = disasm.disassemble(fromLE32(0xD63F0100L))
        assertEquals("blr", result[0].mnemonic)
    }

    @Test
    fun `decodes CSEL X0 X1 X2 EQ`() {
        val result = disasm.disassemble(fromLE32(0x9A820020L))
        assertEquals("csel", result[0].mnemonic)
        assertTrue(result[0].operandsStr.contains("eq"))
    }

    @Test
    fun `decodes multiple instructions`() {
        // ADD X0,X1,X2 + RET
        val code = fromLE32(0x8B020020L) + fromLE32(0xD65F03C0L)
        val result = disasm.disassemble(code)
        assertEquals(2, result.size)
        assertEquals("add", result[0].mnemonic)
        assertEquals("ret", result[1].mnemonic)
        assertEquals(0L, result[0].address)
        assertEquals(4L, result[1].address)
    }

    @Test
    fun `decodes LSL X0 X1 X2`() {
        val result = disasm.disassemble(fromLE32(0x9AC22020L))
        assertEquals("lsl", result[0].mnemonic)
    }

    @Test
    fun `decodes CBZ X0 label`() {
        // CBZ X0, +8: 0xB4000040
        val result = disasm.disassemble(fromLE32(0xB4000040L))
        assertEquals("cbz", result[0].mnemonic)
    }
}
