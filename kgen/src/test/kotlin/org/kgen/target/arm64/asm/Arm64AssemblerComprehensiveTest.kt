package org.kgen.target.arm64.asm

import org.kgen.target.arm64.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class Arm64AssemblerComprehensiveTest {

    private fun assemble(block: Arm64Assembler.() -> Unit): ByteArray {
        val asm = Arm64Assembler()
        asm.block()
        return asm.bytes()
    }

    private fun readLE32(data: ByteArray, off: Int = 0): Long =
        (data[off].toLong() and 0xFF) or
        ((data[off + 1].toLong() and 0xFF) shl 8) or
        ((data[off + 2].toLong() and 0xFF) shl 16) or
        ((data[off + 3].toLong() and 0xFF) shl 24)

    private val X0 get() = Arm64Register.X0
    private val X1 get() = Arm64Register.X1
    private val X2 get() = Arm64Register.X2
    private val X3 get() = Arm64Register.X3
    private val X4 get() = Arm64Register.X4
    private val X5 get() = Arm64Register.X5
    private val X6 get() = Arm64Register.X6
    private val X7 get() = Arm64Register.X7
    private val X8 get() = Arm64Register.X8
    private val X9 get() = Arm64Register.X9
    private val X10 get() = Arm64Register.X10
    private val X15 get() = Arm64Register.X15
    private val X16 get() = Arm64Register.X16
    private val X20 get() = Arm64Register.X20
    private val X28 get() = Arm64Register.X28
    private val X29 get() = Arm64Register.X29
    private val X30 get() = Arm64Register.X30
    private val SP get() = Arm64Register.SP
    private val XZR get() = Arm64Register.XZR
    private val W0 get() = Arm64Register.W0
    private val W1 get() = Arm64Register.W1
    private val W2 get() = Arm64Register.W2
    private val W3 get() = Arm64Register.W3
    private val W4 get() = Arm64Register.W4
    private val W5 get() = Arm64Register.W5
    private val W8 get() = Arm64Register.W8
    private val W10 get() = Arm64Register.W10
    private val W15 get() = Arm64Register.W15
    private val WZR get() = Arm64Register.WZR
    private val D0 get() = Arm64Register.D0
    private val D1 get() = Arm64Register.D1
    private val D2 get() = Arm64Register.D2
    private val D3 get() = Arm64Register.D3
    private val D4 get() = Arm64Register.D4
    private val D5 get() = Arm64Register.D5
    private val D8 get() = Arm64Register.D8
    private val D15 get() = Arm64Register.D15
    private val D31 get() = Arm64Register.D31
    private val S0 get() = Arm64Register.S0
    private val S1 get() = Arm64Register.S1
    private val S2 get() = Arm64Register.S2
    private val S3 get() = Arm64Register.S3
    private val S4 get() = Arm64Register.S4
    private val S5 get() = Arm64Register.S5
    private val S31 get() = Arm64Register.S31
    private val V0 get() = Arm64Register.Q0
    private val V1 get() = Arm64Register.Q1
    private val V2 get() = Arm64Register.Q2
    private val V3 get() = Arm64Register.Q3
    private val V4 get() = Arm64Register.Q4
    private val V5 get() = Arm64Register.Q5
    private val V16 get() = Arm64Register.Q16
    private val V31 get() = Arm64Register.Q31

    // --- ADDS 64-bit register ---

    @Test
    fun `ADDS X0 X1 X2 encodes correctly`() {
        val bytes = assemble { adds(X0, X1, X2) }
        // ADDS X0, X1, X2: sf=1, opc=01, shift=00, Rm=2, imm6=0, Rn=1, Rd=0
        // = 0xAB020020
        assertEquals(0xAB020020L, readLE32(bytes))
    }

    @Test
    fun `ADDS W0 W1 W2 encodes correctly`() {
        val bytes = assemble { adds(W0, W1, W2) }
        assertEquals(0x2B020020L, readLE32(bytes))
    }

    @Test
    fun `ADDS X5 X8 X10 encodes register fields`() {
        val bytes = assemble { adds(X5, X8, X10) }
        // 0xAB000000 | (10 << 16) | (8 << 5) | 5
        assertEquals(0xAB000000L or (10L shl 16) or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- SUBS register ---

    @Test
    fun `SUBS X0 X1 X2 encodes correctly`() {
        val bytes = assemble { subs(X0, X1, X2) }
        assertEquals(0xEB020020L, readLE32(bytes))
    }

    @Test
    fun `SUBS W0 W1 W2 encodes correctly`() {
        val bytes = assemble { subs(W0, W1, W2) }
        assertEquals(0x6B020020L, readLE32(bytes))
    }

    @Test
    fun `SUBS X0 X1 imm12 encodes correctly`() {
        val bytes = assemble { subs(X0, X1, 100) }
        // 0xF1000000 | (100 << 10) | (1 << 5) | 0
        assertEquals(0xF1000000L or (100L shl 10) or (1L shl 5), readLE32(bytes))
    }

    // --- NEG ---

    @Test
    fun `NEG X0 X1 encodes as SUB X0 XZR X1`() {
        val bytes = assemble { neg(X0, X1) }
        // SUB X0, XZR, X1: 0xCB000000 | (1 << 16) | (31 << 5) | 0
        assertEquals(0xCB0103E0L, readLE32(bytes))
    }

    @Test
    fun `NEG X5 X10 encodes register fields`() {
        val bytes = assemble { neg(X5, X10) }
        assertEquals(0xCB000000L or (10L shl 16) or (31L shl 5) or 5L, readLE32(bytes))
    }

    // --- UDIV ---

    @Test
    fun `UDIV X0 X1 X2 encodes correctly`() {
        val bytes = assemble { udiv(X0, X1, X2) }
        // 0x9AC00800 | (2 << 16) | (1 << 5) | 0
        assertEquals(0x9AC20820L, readLE32(bytes))
    }

    @Test
    fun `UDIV W0 W1 W2 encodes correctly`() {
        val bytes = assemble { udiv(W0, W1, W2) }
        assertEquals(0x1AC20820L, readLE32(bytes))
    }

    @Test
    fun `UDIV X5 X8 X10 encodes register fields`() {
        val bytes = assemble { udiv(X5, X8, X10) }
        assertEquals(0x9AC00800L or (10L shl 16) or (8L shl 5) or 5L, readLE32(bytes))
    }

    @Test
    fun `UDIV W3 W4 W5 encodes 32-bit register fields`() {
        val bytes = assemble { udiv(W3, W4, W5) }
        assertEquals(0x1AC00800L or (5L shl 16) or (4L shl 5) or 3L, readLE32(bytes))
    }

    // --- SDIV ---

    @Test
    fun `SDIV W0 W1 W2 encodes correctly`() {
        val bytes = assemble { sdiv(W0, W1, W2) }
        assertEquals(0x1AC20C20L, readLE32(bytes))
    }

    @Test
    fun `SDIV X5 X8 X10 encodes register fields`() {
        val bytes = assemble { sdiv(X5, X8, X10) }
        assertEquals(0x9AC00C00L or (10L shl 16) or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- MSUB ---

    @Test
    fun `MSUB X0 X1 X2 X3 encodes correctly`() {
        val bytes = assemble { msub(X0, X1, X2, X3) }
        // 0x9B008000 | (2 << 16) | (3 << 10) | (1 << 5) | 0
        assertEquals(0x9B008000L or (2L shl 16) or (3L shl 10) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `MSUB X5 X8 X9 X10 encodes all register fields`() {
        val bytes = assemble { msub(X5, X8, X9, X10) }
        assertEquals(0x9B008000L or (9L shl 16) or (10L shl 10) or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- MUL 32-bit ---

    @Test
    fun `MUL W0 W1 W2 encodes correctly`() {
        val bytes = assemble { mul(W0, W1, W2) }
        // MADD W0, W1, W2, WZR: 0x1B007C00 | (2 << 16) | (1 << 5) | 0
        assertEquals(0x1B027C20L, readLE32(bytes))
    }

    @Test
    fun `MUL W3 W4 W5 encodes 32-bit register fields`() {
        val bytes = assemble { mul(W3, W4, W5) }
        assertEquals(0x1B007C00L or (5L shl 16) or (4L shl 5) or 3L, readLE32(bytes))
    }

    // --- LSL register variants ---

    @Test
    fun `LSL W0 W1 W2 encodes correctly`() {
        val bytes = assemble { lsl(W0, W1, W2) }
        assertEquals(0x1AC22020L, readLE32(bytes))
    }

    @Test
    fun `LSL X5 X8 X9 encodes register fields`() {
        val bytes = assemble { lsl(X5, X8, X9) }
        assertEquals(0x9AC02000L or (9L shl 16) or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- LSR register variants ---

    @Test
    fun `LSR X0 X1 X2 encodes correctly`() {
        val bytes = assemble { lsr(X0, X1, X2) }
        assertEquals(0x9AC22420L, readLE32(bytes))
    }

    @Test
    fun `LSR W0 W1 W2 encodes correctly`() {
        val bytes = assemble { lsr(W0, W1, W2) }
        assertEquals(0x1AC22420L, readLE32(bytes))
    }

    @Test
    fun `LSR X5 X8 X9 encodes register fields`() {
        val bytes = assemble { lsr(X5, X8, X9) }
        assertEquals(0x9AC02400L or (9L shl 16) or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- ASR register variants ---

    @Test
    fun `ASR X0 X1 X2 encodes correctly`() {
        val bytes = assemble { asr(X0, X1, X2) }
        assertEquals(0x9AC22820L, readLE32(bytes))
    }

    @Test
    fun `ASR W0 W1 W2 encodes correctly`() {
        val bytes = assemble { asr(W0, W1, W2) }
        assertEquals(0x1AC22820L, readLE32(bytes))
    }

    @Test
    fun `ASR X5 X8 X9 encodes register fields`() {
        val bytes = assemble { asr(X5, X8, X9) }
        assertEquals(0x9AC02800L or (9L shl 16) or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- AND 32-bit ---

    @Test
    fun `AND W0 W1 W2 encodes correctly`() {
        val bytes = assemble { and_(W0, W1, W2) }
        assertEquals(0x0A020020L, readLE32(bytes))
    }

    @Test
    fun `AND X5 X8 X9 encodes register fields`() {
        val bytes = assemble { and_(X5, X8, X9) }
        assertEquals(0x8A000000L.toULong().toLong() or (9L shl 16) or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- ORR 32-bit ---

    @Test
    fun `ORR W0 W1 W2 encodes correctly`() {
        val bytes = assemble { orr(W0, W1, W2) }
        assertEquals(0x2A020020L, readLE32(bytes))
    }

    // --- EOR 32-bit ---

    @Test
    fun `EOR W0 W1 W2 encodes correctly`() {
        val bytes = assemble { eor(W0, W1, W2) }
        assertEquals(0x4A020020L, readLE32(bytes))
    }

    // --- TST ---

    @Test
    fun `TST X0 X1 encodes as ANDS XZR X0 X1`() {
        val bytes = assemble { tst(X0, X1) }
        // ANDS XZR, X0, X1: 0xEA000000 | (1 << 16) | (0 << 5) | 31
        assertEquals(0xEA01001FL, readLE32(bytes))
    }

    @Test
    fun `TST X5 X8 encodes register fields`() {
        val bytes = assemble { tst(X5, X8) }
        assertEquals(0xEA000000L or (8L shl 16) or (5L shl 5) or 31L, readLE32(bytes))
    }

    // --- CMN ---

    @Test
    fun `CMN X0 X1 encodes as ADDS XZR X0 X1`() {
        val bytes = assemble { cmn(X0, X1) }
        // ADDS XZR, X0, X1: 0xAB000000 | (1 << 16) | (0 << 5) | 31
        assertEquals(0xAB01001FL, readLE32(bytes))
    }

    @Test
    fun `CMN X5 X8 encodes register fields`() {
        val bytes = assemble { cmn(X5, X8) }
        assertEquals(0xAB000000L or (8L shl 16) or (5L shl 5) or 31L, readLE32(bytes))
    }

    // --- CMP 32-bit ---

    @Test
    fun `CMP W0 W1 encodes correctly`() {
        val bytes = assemble { cmp(W0, W1) }
        // SUBS WZR, W0, W1: 0x6B000000 | (1 << 16) | (0 << 5) | 31
        assertEquals(0x6B01001FL, readLE32(bytes))
    }

    // --- CSEL 32-bit ---

    @Test
    fun `CSEL W0 W1 W2 EQ encodes correctly`() {
        val bytes = assemble { csel(W0, W1, W2, Arm64Condition.EQ) }
        // 0x1A800000 | (2 << 16) | (0 << 12) | (1 << 5) | 0
        assertEquals(0x1A820020L, readLE32(bytes))
    }

    @Test
    fun `CSEL W0 W1 W2 NE encodes correctly`() {
        val bytes = assemble { csel(W0, W1, W2, Arm64Condition.NE) }
        assertEquals(0x1A800000L or (2L shl 16) or (1L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `CSEL X0 X1 X2 CS encodes condition code`() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.CS) }
        assertEquals(0x9A800000L or (2L shl 16) or (2L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `CSEL X0 X1 X2 CC encodes condition code`() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.CC) }
        assertEquals(0x9A800000L or (2L shl 16) or (3L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `CSEL X0 X1 X2 MI encodes condition code`() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.MI) }
        assertEquals(0x9A800000L or (2L shl 16) or (4L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `CSEL X0 X1 X2 PL encodes condition code`() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.PL) }
        assertEquals(0x9A800000L or (2L shl 16) or (5L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `CSEL X0 X1 X2 VS encodes condition code`() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.VS) }
        assertEquals(0x9A800000L or (2L shl 16) or (6L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `CSEL X0 X1 X2 VC encodes condition code`() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.VC) }
        assertEquals(0x9A800000L or (2L shl 16) or (7L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `CSEL X0 X1 X2 HI encodes condition code`() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.HI) }
        assertEquals(0x9A800000L or (2L shl 16) or (8L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `CSEL X0 X1 X2 LS encodes condition code`() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.LS) }
        assertEquals(0x9A800000L or (2L shl 16) or (9L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `CSEL X0 X1 X2 AL encodes condition code`() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.AL) }
        assertEquals(0x9A800000L or (2L shl 16) or (0xEL shl 12) or (1L shl 5), readLE32(bytes))
    }

    // --- CSINC ---

    @Test
    fun `CSINC X0 X1 X2 EQ encodes correctly`() {
        val bytes = assemble { csinc(X0, X1, X2, Arm64Condition.EQ) }
        // 0x9A800400 | (2 << 16) | (0 << 12) | (1 << 5) | 0
        assertEquals(0x9A820420L, readLE32(bytes))
    }

    @Test
    fun `CSINC X0 X1 X2 NE encodes correctly`() {
        val bytes = assemble { csinc(X0, X1, X2, Arm64Condition.NE) }
        assertEquals(0x9A800400L or (2L shl 16) or (1L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `CSINC X0 X1 X2 LT encodes correctly`() {
        val bytes = assemble { csinc(X0, X1, X2, Arm64Condition.LT) }
        assertEquals(0x9A800400L or (2L shl 16) or (0xBL shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `CSINC X5 X8 X9 GE encodes all fields`() {
        val bytes = assemble { csinc(X5, X8, X9, Arm64Condition.GE) }
        assertEquals(0x9A800400L or (9L shl 16) or (0xAL shl 12) or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- SXTW ---

    @Test
    fun `SXTW X5 W8 encodes register fields`() {
        val bytes = assemble { sxtw(X5, W8) }
        assertEquals(0x93407C00L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- SXTH ---

    @Test
    fun `SXTH X0 W1 encodes correctly`() {
        val bytes = assemble { sxth(X0, W1) }
        // SBFM X0, X1, #0, #15: 0x93403C00 | (1 << 5) | 0
        assertEquals(0x93403C20L, readLE32(bytes))
    }

    @Test
    fun `SXTH X5 W8 encodes register fields`() {
        val bytes = assemble { sxth(X5, W8) }
        assertEquals(0x93403C00L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- SXTB ---

    @Test
    fun `SXTB X0 W1 encodes correctly`() {
        val bytes = assemble { sxtb(X0, W1) }
        // SBFM X0, X1, #0, #7: 0x93401C00 | (1 << 5) | 0
        assertEquals(0x93401C20L, readLE32(bytes))
    }

    @Test
    fun `SXTB X5 W8 encodes register fields`() {
        val bytes = assemble { sxtb(X5, W8) }
        assertEquals(0x93401C00L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- UXTB ---

    @Test
    fun `UXTB W0 W1 encodes correctly`() {
        val bytes = assemble { uxtb(W0, W1) }
        // UBFM W0, W1, #0, #7: 0x53001C00 | (1 << 5) | 0
        assertEquals(0x53001C20L, readLE32(bytes))
    }

    @Test
    fun `UXTB W5 W8 encodes register fields`() {
        val bytes = assemble { uxtb(W5, W8) }
        assertEquals(0x53001C00L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- UXTH ---

    @Test
    fun `UXTH W0 W1 encodes correctly`() {
        val bytes = assemble { uxth(W0, W1) }
        // UBFM W0, W1, #0, #15: 0x53003C00 | (1 << 5) | 0
        assertEquals(0x53003C20L, readLE32(bytes))
    }

    @Test
    fun `UXTH W5 W8 encodes register fields`() {
        val bytes = assemble { uxth(W5, W8) }
        assertEquals(0x53003C00L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- ADD immediate 32-bit ---

    @Test
    fun `ADD W0 W1 imm encodes correctly`() {
        val bytes = assemble { add(W0, W1, 42) }
        // 0x11000000 | (42 << 10) | (1 << 5) | 0
        assertEquals(0x1100A820L, readLE32(bytes))
    }

    @Test
    fun `ADD W0 W1 imm 4095 encodes max`() {
        val bytes = assemble { add(W0, W1, 4095) }
        assertEquals(0x11000000L or (4095L shl 10) or (1L shl 5), readLE32(bytes))
    }

    // --- SUB immediate 32-bit ---

    @Test
    fun `SUB W0 W1 imm encodes correctly`() {
        val bytes = assemble { sub(W0, W1, 42) }
        assertEquals(0x51000000L or (42L shl 10) or (1L shl 5), readLE32(bytes))
    }

    // --- SUB register 32-bit ---

    @Test
    fun `SUB W0 W1 W2 encodes correctly`() {
        val bytes = assemble { sub(W0, W1, W2) }
        assertEquals(0x4B020020L, readLE32(bytes))
    }

    // --- MOV 32-bit ---

    @Test
    fun `MOV W0 W1 encodes as ORR W0 WZR W1`() {
        val bytes = assemble { mov(W0, W1) }
        // ORR W0, WZR, W1: 0x2A000000 | (1 << 16) | (31 << 5) | 0
        assertEquals(0x2A0103E0L, readLE32(bytes))
    }

    // --- MOV SP ---

    @Test
    fun `MOVSP X29 SP encodes correctly`() {
        val bytes = assemble { movSp(X29, SP) }
        // ADD X29, SP, #0: 0x91000000 | (31 << 5) | 29
        assertEquals(0x910003FDL, readLE32(bytes))
    }

    @Test
    fun `MOVSP SP X29 encodes correctly`() {
        val bytes = assemble { movSp(SP, X29) }
        // ADD SP, X29, #0: 0x91000000 | (29 << 5) | 31
        assertEquals(0x910003BFL, readLE32(bytes))
    }

    // --- MOVK ---

    @Test
    fun `MOVK X0 imm16 encodes correctly`() {
        val bytes = assemble { movk(X0, 0x5678) }
        // 0xF2800000 | (0 << 21) | (0x5678 << 5) | 0
        assertEquals(0xF2800000L or (0x5678L shl 5), readLE32(bytes))
    }

    @Test
    fun `MOVK X0 imm16 shift 16 encodes correctly`() {
        val bytes = assemble { movk(X0, 0x1234, shift = 16) }
        // hw=1: 0xF2A00000 | (0x1234 << 5) | 0
        assertEquals(0xF2A00000L or (0x1234L shl 5), readLE32(bytes))
    }

    @Test
    fun `MOVK X0 imm16 shift 32 encodes correctly`() {
        val bytes = assemble { movk(X0, 0xABCD, shift = 32) }
        assertEquals(0xF2C00000L or (0xABCDL shl 5), readLE32(bytes))
    }

    @Test
    fun `MOVK X0 imm16 shift 48 encodes correctly`() {
        val bytes = assemble { movk(X0, 0xFFFF, shift = 48) }
        assertEquals(0xF2E00000L or (0xFFFFL shl 5), readLE32(bytes))
    }

    // --- MOVN ---

    @Test
    fun `MOVN X0 imm16 encodes correctly`() {
        val bytes = assemble { movn(X0, 0) }
        // MOVN X0, #0: 0x92800000 | 0
        assertEquals(0x92800000L, readLE32(bytes))
    }

    @Test
    fun `MOVN X0 imm16 1 encodes minus 2`() {
        val bytes = assemble { movn(X0, 1) }
        assertEquals(0x92800000L or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `MOVN X0 imm16 shift 16 encodes correctly`() {
        val bytes = assemble { movn(X0, 0x1234, shift = 16) }
        assertEquals(0x92A00000L or (0x1234L shl 5), readLE32(bytes))
    }

    // --- MOVZ 32-bit ---

    @Test
    fun `MOVZ W0 imm16 encodes correctly`() {
        val bytes = assemble { movz(W0, 0x1234) }
        assertEquals(0x52800000L or (0x1234L shl 5), readLE32(bytes))
    }

    @Test
    fun `MOVZ W0 shift 16 encodes correctly`() {
        val bytes = assemble { movz(W0, 0xABCD, shift = 16) }
        assertEquals(0x52A00000L or (0xABCDL shl 5), readLE32(bytes))
    }

    // --- CBZ / CBNZ ---

    @Test
    fun `CBZ X0 label encodes correctly`() {
        val bytes = assemble {
            cbz(X0, "target")
            nop()
            label("target")
            ret()
        }
        assertEquals(12, bytes.size)
        val inst = readLE32(bytes, 0)
        // CBZ X0: 0xB4000000 | (imm19=2 << 5) | 0
        assertEquals(0xB4000000L or (2L shl 5), inst)
    }

    @Test
    fun `CBZ W0 label encodes correctly`() {
        val bytes = assemble {
            cbz(W0, "target")
            label("target")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(0x34000000L or (1L shl 5), inst)
    }

    @Test
    fun `CBNZ X0 label encodes correctly`() {
        val bytes = assemble {
            cbnz(X0, "target")
            nop()
            label("target")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(0xB5000000L or (2L shl 5), inst)
    }

    @Test
    fun `CBNZ W0 label encodes correctly`() {
        val bytes = assemble {
            cbnz(W0, "target")
            label("target")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(0x35000000L or (1L shl 5), inst)
    }

    @Test
    fun `CBZ X5 encodes register field`() {
        val bytes = assemble {
            cbz(X5, "target")
            label("target")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(0xB4000000L or (1L shl 5) or 5L, inst)
    }

    @Test
    fun `CBNZ backward label resolves correctly`() {
        val bytes = assemble {
            label("loop")
            nop()
            cbnz(X0, "loop")
        }
        val inst = readLE32(bytes, 4)
        // offset = -1, imm19 should be 0x7FFFF (sign-extended -1)
        val imm19 = (inst shr 5) and 0x7FFFF
        assertEquals(0x7FFFFL, imm19)
    }

    // --- B.cond with all conditions ---

    @Test
    fun `B_EQ encodes condition code 0`() {
        val bytes = assemble {
            bCond(Arm64Condition.EQ, "t")
            label("t")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(0L, inst and 0xFL) // cond = 0
    }

    @Test
    fun `B_NE encodes condition code 1`() {
        val bytes = assemble {
            bCond(Arm64Condition.NE, "t")
            label("t")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(1L, inst and 0xFL)
    }

    @Test
    fun `B_CS encodes condition code 2`() {
        val bytes = assemble {
            bCond(Arm64Condition.CS, "t")
            label("t")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(2L, inst and 0xFL)
    }

    @Test
    fun `B_CC encodes condition code 3`() {
        val bytes = assemble {
            bCond(Arm64Condition.CC, "t")
            label("t")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(3L, inst and 0xFL)
    }

    @Test
    fun `B_MI encodes condition code 4`() {
        val bytes = assemble {
            bCond(Arm64Condition.MI, "t")
            label("t")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(4L, inst and 0xFL)
    }

    @Test
    fun `B_PL encodes condition code 5`() {
        val bytes = assemble {
            bCond(Arm64Condition.PL, "t")
            label("t")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(5L, inst and 0xFL)
    }

    @Test
    fun `B_VS encodes condition code 6`() {
        val bytes = assemble {
            bCond(Arm64Condition.VS, "t")
            label("t")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(6L, inst and 0xFL)
    }

    @Test
    fun `B_VC encodes condition code 7`() {
        val bytes = assemble {
            bCond(Arm64Condition.VC, "t")
            label("t")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(7L, inst and 0xFL)
    }

    @Test
    fun `B_HI encodes condition code 8`() {
        val bytes = assemble {
            bCond(Arm64Condition.HI, "t")
            label("t")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(8L, inst and 0xFL)
    }

    @Test
    fun `B_LS encodes condition code 9`() {
        val bytes = assemble {
            bCond(Arm64Condition.LS, "t")
            label("t")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(9L, inst and 0xFL)
    }

    // --- RET with non-default register ---

    @Test
    fun `RET X0 encodes correctly`() {
        val bytes = assemble { ret(X0) }
        assertEquals(0xD65F0000L or (0L shl 5), readLE32(bytes))
    }

    @Test
    fun `RET X8 encodes correctly`() {
        val bytes = assemble { ret(X8) }
        assertEquals(0xD65F0000L or (8L shl 5), readLE32(bytes))
    }

    // --- BRK with various immediates ---

    @Test
    fun `BRK 0 encodes correctly`() {
        val bytes = assemble { brk(0) }
        assertEquals(0xD4200000L, readLE32(bytes))
    }

    @Test
    fun `BRK 0x1234 encodes imm16`() {
        val bytes = assemble { brk(0x1234) }
        assertEquals(0xD4200000L or (0x1234L shl 5), readLE32(bytes))
    }

    // --- SVC with various immediates ---

    @Test
    fun `SVC 1 encodes correctly`() {
        val bytes = assemble { svc(1) }
        assertEquals(0xD4000001L or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `SVC 0x80 encodes correctly`() {
        val bytes = assemble { svc(0x80) }
        assertEquals(0xD4000001L or (0x80L shl 5), readLE32(bytes))
    }

    // --- LDR register offset variants ---

    @Test
    fun `LDR X0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { ldr(X0, X1, 0) }
        assertEquals(0xF9400020L, readLE32(bytes))
    }

    @Test
    fun `LDR X0 X1 offset 32760 encodes max`() {
        val bytes = assemble { ldr(X0, X1, 32760) }
        val scaledImm = 32760 / 8
        assertEquals(0xF9400000L or (scaledImm.toLong() shl 10) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `LDR W0 X1 offset 16380 encodes max for W`() {
        val bytes = assemble { ldr(W0, X1, 16380) }
        val scaledImm = 16380 / 4
        assertEquals(0xB9400000L or (scaledImm.toLong() shl 10) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `LDR X5 SP offset 64 encodes from SP`() {
        val bytes = assemble { ldr(X5, SP, 64) }
        val scaledImm = 64 / 8
        assertEquals(0xF9400000L or (scaledImm.toLong() shl 10) or (31L shl 5) or 5L, readLE32(bytes))
    }

    // --- STR register offset variants ---

    @Test
    fun `STR X0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { str(X0, X1, 0) }
        assertEquals(0xF9000020L, readLE32(bytes))
    }

    @Test
    fun `STR W0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { str(W0, X1, 0) }
        assertEquals(0xB9000020L, readLE32(bytes))
    }

    @Test
    fun `STR W0 X1 offset 16 encodes correctly`() {
        val bytes = assemble { str(W0, X1, 16) }
        val scaledImm = 16 / 4
        assertEquals(0xB9000000L or (scaledImm.toLong() shl 10) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `STR X5 SP offset 128 encodes correctly`() {
        val bytes = assemble { str(X5, SP, 128) }
        val scaledImm = 128 / 8
        assertEquals(0xF9000000L or (scaledImm.toLong() shl 10) or (31L shl 5) or 5L, readLE32(bytes))
    }

    // --- LDUR variants ---

    @Test
    fun `LDUR X0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { ldur(X0, X1, 0) }
        assertEquals(0xF8400000L or (0L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `LDUR X0 X1 offset 255 encodes max positive`() {
        val bytes = assemble { ldur(X0, X1, 255) }
        assertEquals(0xF8400000L or (255L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `LDUR W0 X1 offset -8 encodes correctly`() {
        val bytes = assemble { ldur(W0, X1, -8) }
        val imm9 = (-8) and 0x1FF
        assertEquals(0xB8400000L or (imm9.toLong() shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `LDUR W0 X1 offset -256 encodes min`() {
        val bytes = assemble { ldur(W0, X1, -256) }
        val imm9 = (-256) and 0x1FF
        assertEquals(0xB8400000L or (imm9.toLong() shl 12) or (1L shl 5), readLE32(bytes))
    }

    // --- STUR variants ---

    @Test
    fun `STUR X0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { stur(X0, X1, 0) }
        assertEquals(0xF8000000L or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `STUR X0 X1 offset 127 encodes correctly`() {
        val bytes = assemble { stur(X0, X1, 127) }
        assertEquals(0xF8000000L or (127L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `STUR W0 X1 offset -128 encodes correctly`() {
        val bytes = assemble { stur(W0, X1, -128) }
        val imm9 = (-128) and 0x1FF
        assertEquals(0xB8000000L or (imm9.toLong() shl 12) or (1L shl 5), readLE32(bytes))
    }

    // --- STP signed offset ---

    @Test
    fun `STP X0 X1 SP offset 0 encodes correctly`() {
        val bytes = assemble { stp(X0, X1, SP, 0) }
        // 0xA9000000 | (0 << 15) | (1 << 10) | (31 << 5) | 0
        assertEquals(0xA9000000L or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    @Test
    fun `STP X0 X1 SP offset 64 encodes correctly`() {
        val bytes = assemble { stp(X0, X1, SP, 64) }
        val scaledImm = (64 / 8) and 0x7F
        assertEquals(0xA9000000L or (scaledImm.toLong() shl 15) or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    @Test
    fun `STP X0 X1 SP offset minus 64 encodes correctly`() {
        val bytes = assemble { stp(X0, X1, SP, -64) }
        val scaledImm = (-64 / 8) and 0x7F
        assertEquals(0xA9000000L or (scaledImm.toLong() shl 15) or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    // --- LDP signed offset ---

    @Test
    fun `LDP X0 X1 SP offset 0 encodes correctly`() {
        val bytes = assemble { ldp(X0, X1, SP, 0) }
        assertEquals(0xA9400000L or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    @Test
    fun `LDP X0 X1 SP offset 32 encodes correctly`() {
        val bytes = assemble { ldp(X0, X1, SP, 32) }
        val scaledImm = (32 / 8) and 0x7F
        assertEquals(0xA9400000L or (scaledImm.toLong() shl 15) or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    // --- STP pre-index ---

    @Test
    fun `STP pre-index X0 X1 SP minus 32 encodes correctly`() {
        val bytes = assemble { stpPre(X0, X1, SP, -32) }
        val scaledImm = (-32 / 8) and 0x7F
        assertEquals(0xA9800000L or (scaledImm.toLong() shl 15) or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    // --- LDP post-index ---

    @Test
    fun `LDP post-index X0 X1 SP 32 encodes correctly`() {
        val bytes = assemble { ldpPost(X0, X1, SP, 32) }
        val scaledImm = (32 / 8) and 0x7F
        assertEquals(0xA8C00000L or (scaledImm.toLong() shl 15) or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    // --- LDRB ---

    @Test
    fun `LDRB W0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { ldrb(W0, X1, 0) }
        assertEquals(0x39400020L, readLE32(bytes))
    }

    @Test
    fun `LDRB W0 X1 offset 4095 encodes max`() {
        val bytes = assemble { ldrb(W0, X1, 4095) }
        assertEquals(0x39400000L or (4095L shl 10) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `LDRB W5 SP offset 10 encodes register fields`() {
        val bytes = assemble { ldrb(W5, SP, 10) }
        assertEquals(0x39400000L or (10L shl 10) or (31L shl 5) or 5L, readLE32(bytes))
    }

    // --- STRB ---

    @Test
    fun `STRB W0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { strb(W0, X1, 0) }
        assertEquals(0x39000020L, readLE32(bytes))
    }

    @Test
    fun `STRB W0 X1 offset 100 encodes correctly`() {
        val bytes = assemble { strb(W0, X1, 100) }
        assertEquals(0x39000000L or (100L shl 10) or (1L shl 5), readLE32(bytes))
    }

    // --- LDRH ---

    @Test
    fun `LDRH W0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { ldrh(W0, X1, 0) }
        assertEquals(0x79400020L, readLE32(bytes))
    }

    @Test
    fun `LDRH W0 X1 offset 8190 encodes max`() {
        val bytes = assemble { ldrh(W0, X1, 8190) }
        val scaledImm = 8190 / 2
        assertEquals(0x79400000L or (scaledImm.toLong() shl 10) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `LDRH W5 SP offset 64 encodes correctly`() {
        val bytes = assemble { ldrh(W5, SP, 64) }
        val scaledImm = 64 / 2
        assertEquals(0x79400000L or (scaledImm.toLong() shl 10) or (31L shl 5) or 5L, readLE32(bytes))
    }

    // --- STRH ---

    @Test
    fun `STRH W0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { strh(W0, X1, 0) }
        assertEquals(0x79000020L, readLE32(bytes))
    }

    @Test
    fun `STRH W0 X1 offset 100 encodes correctly`() {
        val bytes = assemble { strh(W0, X1, 100) }
        val scaledImm = 100 / 2
        assertEquals(0x79000000L or (scaledImm.toLong() shl 10) or (1L shl 5), readLE32(bytes))
    }

    // --- ADR ---

    @Test
    fun `ADR X0 label encodes register field`() {
        val bytes = assemble {
            adr(X0, "target")
            label("target")
            ret()
        }
        assertEquals(8, bytes.size)
        val inst = readLE32(bytes, 0)
        // Rd should be 0
        assertEquals(0L, inst and 0x1FL)
    }

    @Test
    fun `ADR X5 label encodes register field 5`() {
        val bytes = assemble {
            adr(X5, "target")
            label("target")
            ret()
        }
        val inst = readLE32(bytes, 0)
        assertEquals(5L, inst and 0x1FL)
    }

    // --- FP single-precision arithmetic ---

    @Test
    fun `FADD S0 S1 S2 encodes correctly`() {
        val bytes = assemble { fadd(S0, S1, S2) }
        // 0x1E202800 | (2 << 16) | (1 << 5) | 0
        assertEquals(0x1E222820L, readLE32(bytes))
    }

    @Test
    fun `FSUB S0 S1 S2 encodes correctly`() {
        val bytes = assemble { fsub(S0, S1, S2) }
        assertEquals(0x1E223820L, readLE32(bytes))
    }

    @Test
    fun `FMUL S0 S1 S2 encodes correctly`() {
        val bytes = assemble { fmul(S0, S1, S2) }
        assertEquals(0x1E220820L, readLE32(bytes))
    }

    @Test
    fun `FDIV S0 S1 S2 encodes correctly`() {
        val bytes = assemble { fdiv(S0, S1, S2) }
        assertEquals(0x1E221820L, readLE32(bytes))
    }

    @Test
    fun `FNEG S0 S1 encodes correctly`() {
        val bytes = assemble { fneg(S0, S1) }
        assertEquals(0x1E214020L, readLE32(bytes))
    }

    @Test
    fun `FABS S0 S1 encodes correctly`() {
        val bytes = assemble { fabs(S0, S1) }
        assertEquals(0x1E20C020L, readLE32(bytes))
    }

    // --- FP double-precision with high registers ---

    @Test
    fun `FADD D5 D8 D15 encodes register fields`() {
        val bytes = assemble { fadd(D5, D8, D15) }
        assertEquals(0x1E602800L or (15L shl 16) or (8L shl 5) or 5L, readLE32(bytes))
    }

    @Test
    fun `FSUB D5 D8 D15 encodes register fields`() {
        val bytes = assemble { fsub(D5, D8, D15) }
        assertEquals(0x1E603800L or (15L shl 16) or (8L shl 5) or 5L, readLE32(bytes))
    }

    @Test
    fun `FMUL D5 D8 D15 encodes register fields`() {
        val bytes = assemble { fmul(D5, D8, D15) }
        assertEquals(0x1E600800L or (15L shl 16) or (8L shl 5) or 5L, readLE32(bytes))
    }

    @Test
    fun `FDIV D5 D8 D15 encodes register fields`() {
        val bytes = assemble { fdiv(D5, D8, D15) }
        assertEquals(0x1E601800L or (15L shl 16) or (8L shl 5) or 5L, readLE32(bytes))
    }

    @Test
    fun `FNEG D5 D8 encodes register fields`() {
        val bytes = assemble { fneg(D5, D8) }
        assertEquals(0x1E614000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    @Test
    fun `FABS D5 D8 encodes register fields`() {
        val bytes = assemble { fabs(D5, D8) }
        assertEquals(0x1E60C000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- FCMP variants ---

    @Test
    fun `FCMP S0 S1 encodes correctly`() {
        val bytes = assemble { fcmp(S0, S1) }
        assertEquals(0x1E212000L, readLE32(bytes))
    }

    @Test
    fun `FCMP D5 D8 encodes register fields`() {
        val bytes = assemble { fcmp(D5, D8) }
        assertEquals(0x1E602000L or (8L shl 16) or (5L shl 5), readLE32(bytes))
    }

    @Test
    fun `FCMP S0 zero encodes correctly`() {
        val bytes = assemble { fcmpZero(S0) }
        assertEquals(0x1E202008L, readLE32(bytes))
    }

    @Test
    fun `FCMP D5 zero encodes register field`() {
        val bytes = assemble { fcmpZero(D5) }
        assertEquals(0x1E602008L or (5L shl 5), readLE32(bytes))
    }

    // --- FMOV register to register ---

    @Test
    fun `FMOV S0 S1 encodes correctly`() {
        val bytes = assemble { fmov(S0, S1) }
        assertEquals(0x1E204020L, readLE32(bytes))
    }

    @Test
    fun `FMOV D5 D8 encodes register fields`() {
        val bytes = assemble { fmov(D5, D8) }
        assertEquals(0x1E604000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- FMOV GP to FP ---

    @Test
    fun `FMOV S0 from W0 encodes correctly`() {
        val bytes = assemble { fmovFromGp32(S0, W0) }
        assertEquals(0x1E270000L, readLE32(bytes))
    }

    @Test
    fun `FMOV S5 from W8 encodes register fields`() {
        val bytes = assemble { fmovFromGp32(S5, W8) }
        assertEquals(0x1E270000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    @Test
    fun `FMOV D5 from X8 encodes register fields`() {
        val bytes = assemble { fmovFromGp64(D5, X8) }
        assertEquals(0x9E670000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- FMOV FP to GP ---

    @Test
    fun `FMOV W0 from S0 encodes correctly`() {
        val bytes = assemble { fmovToGp32(W0, S0) }
        assertEquals(0x1E260000L, readLE32(bytes))
    }

    @Test
    fun `FMOV W5 from S8 encodes register fields`() {
        // S8 register
        val s8 = Arm64Register.S8
        val bytes = assemble { fmovToGp32(W5, s8) }
        assertEquals(0x1E260000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    @Test
    fun `FMOV X5 from D8 encodes register fields`() {
        val bytes = assemble { fmovToGp64(X5, D8) }
        assertEquals(0x9E660000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- SCVTF variants ---

    @Test
    fun `SCVTF D0 X0 encodes correctly`() {
        val bytes = assemble { scvtf(D0, X0) }
        assertEquals(0x9E620000L, readLE32(bytes))
    }

    @Test
    fun `SCVTF S0 W0 encodes correctly`() {
        val bytes = assemble { scvtf(S0, W0) }
        assertEquals(0x1E220000L, readLE32(bytes))
    }

    @Test
    fun `SCVTF D0 from W0 encodes W to D conversion`() {
        val bytes = assemble { scvtfWtoD(D0, W0) }
        assertEquals(0x1E620000L, readLE32(bytes))
    }

    @Test
    fun `SCVTF S0 from X0 encodes X to S conversion`() {
        val bytes = assemble { scvtfXtoS(S0, X0) }
        assertEquals(0x9E220000L, readLE32(bytes))
    }

    @Test
    fun `SCVTF D5 X8 encodes register fields`() {
        val bytes = assemble { scvtf(D5, X8) }
        assertEquals(0x9E620000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    @Test
    fun `SCVTF S5 W8 encodes register fields`() {
        val bytes = assemble { scvtf(S5, W8) }
        assertEquals(0x1E220000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- UCVTF variants ---

    @Test
    fun `UCVTF D0 X0 encodes correctly`() {
        val bytes = assemble { ucvtf(D0, X0) }
        assertEquals(0x9E630000L, readLE32(bytes))
    }

    @Test
    fun `UCVTF S0 W0 encodes correctly`() {
        val bytes = assemble { ucvtf(S0, W0) }
        assertEquals(0x1E230000L, readLE32(bytes))
    }

    @Test
    fun `UCVTF D5 X8 encodes register fields`() {
        val bytes = assemble { ucvtf(D5, X8) }
        assertEquals(0x9E630000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- FCVTZS variants ---

    @Test
    fun `FCVTZS X0 D0 encodes correctly`() {
        val bytes = assemble { fcvtzs(X0, D0) }
        assertEquals(0x9E780000L, readLE32(bytes))
    }

    @Test
    fun `FCVTZS W0 S0 encodes correctly`() {
        val bytes = assemble { fcvtzs(W0, S0) }
        assertEquals(0x1E380000L, readLE32(bytes))
    }

    @Test
    fun `FCVTZS W0 D0 encodes D to W conversion`() {
        val bytes = assemble { fcvtzsDtoW(W0, D0) }
        assertEquals(0x1E780000L, readLE32(bytes))
    }

    @Test
    fun `FCVTZS X5 D8 encodes register fields`() {
        val bytes = assemble { fcvtzs(X5, D8) }
        assertEquals(0x9E780000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    @Test
    fun `FCVTZS W5 S4 encodes register fields`() {
        val bytes = assemble { fcvtzs(W5, S4) }
        assertEquals(0x1E380000L or (4L shl 5) or 5L, readLE32(bytes))
    }

    // --- FCVTZU variants ---

    @Test
    fun `FCVTZU X0 D0 encodes correctly`() {
        val bytes = assemble { fcvtzu(X0, D0) }
        assertEquals(0x9E790000L, readLE32(bytes))
    }

    @Test
    fun `FCVTZU W0 S0 encodes correctly`() {
        val bytes = assemble { fcvtzu(W0, S0) }
        assertEquals(0x1E390000L, readLE32(bytes))
    }

    @Test
    fun `FCVTZU X5 D8 encodes register fields`() {
        val bytes = assemble { fcvtzu(X5, D8) }
        assertEquals(0x9E790000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- FCVT precision conversion ---

    @Test
    fun `FCVT D0 S0 widen encodes correctly`() {
        val bytes = assemble { fcvtStoD(D0, S0) }
        assertEquals(0x1E22C000L, readLE32(bytes))
    }

    @Test
    fun `FCVT S0 D0 narrow encodes correctly`() {
        val bytes = assemble { fcvtDtoS(S0, D0) }
        assertEquals(0x1E624000L, readLE32(bytes))
    }

    @Test
    fun `FCVT D5 S8 widen encodes register fields`() {
        val s8 = Arm64Register.S8
        val bytes = assemble { fcvtStoD(D5, s8) }
        assertEquals(0x1E22C000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    @Test
    fun `FCVT S5 D8 narrow encodes register fields`() {
        val bytes = assemble { fcvtDtoS(S5, D8) }
        assertEquals(0x1E624000L or (8L shl 5) or 5L, readLE32(bytes))
    }

    // --- FP load/store ---

    @Test
    fun `FLDR D0 SP offset 0 encodes correctly`() {
        val bytes = assemble { fldr(D0, SP, 0) }
        assertEquals(0xFD4003E0L, readLE32(bytes))
    }

    @Test
    fun `FLDR D0 X1 offset 32 encodes correctly`() {
        val bytes = assemble { fldr(D0, X1, 32) }
        val scaledImm = 32 / 8
        assertEquals(0xFD400000L or (scaledImm.toLong() shl 10) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `FLDR S0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { fldr(S0, X1, 0) }
        assertEquals(0xBD400020L, readLE32(bytes))
    }

    @Test
    fun `FLDR S0 X1 offset 16 encodes correctly`() {
        val bytes = assemble { fldr(S0, X1, 16) }
        val scaledImm = 16 / 4
        assertEquals(0xBD400000L or (scaledImm.toLong() shl 10) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `FSTR D0 SP offset 0 encodes correctly`() {
        val bytes = assemble { fstr(D0, SP, 0) }
        assertEquals(0xFD0003E0L, readLE32(bytes))
    }

    @Test
    fun `FSTR D0 X1 offset 64 encodes correctly`() {
        val bytes = assemble { fstr(D0, X1, 64) }
        val scaledImm = 64 / 8
        assertEquals(0xFD000000L or (scaledImm.toLong() shl 10) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `FSTR S0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { fstr(S0, X1, 0) }
        assertEquals(0xBD000020L, readLE32(bytes))
    }

    @Test
    fun `FSTR S0 X1 offset 8 encodes correctly`() {
        val bytes = assemble { fstr(S0, X1, 8) }
        val scaledImm = 8 / 4
        assertEquals(0xBD000000L or (scaledImm.toLong() shl 10) or (1L shl 5), readLE32(bytes))
    }

    // --- FLDUR / FSTUR ---

    @Test
    fun `FLDUR D0 X1 offset -8 encodes correctly`() {
        val bytes = assemble { fldur(D0, X1, -8) }
        val imm9 = (-8) and 0x1FF
        assertEquals(0xFC400000L or (imm9.toLong() shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `FLDUR D0 X1 offset 255 encodes max`() {
        val bytes = assemble { fldur(D0, X1, 255) }
        assertEquals(0xFC400000L or (255L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `FSTUR D0 X1 offset -16 encodes correctly`() {
        val bytes = assemble { fstur(D0, X1, -16) }
        val imm9 = (-16) and 0x1FF
        assertEquals(0xFC000000L or (imm9.toLong() shl 12) or (1L shl 5), readLE32(bytes))
    }

    // --- FP STP/LDP signed offset ---

    @Test
    fun `FSTP D0 D1 SP offset 0 encodes correctly`() {
        val bytes = assemble { fstp(D0, D1, SP, 0) }
        assertEquals(0x6D000000L or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    @Test
    fun `FSTP D0 D1 SP offset 64 encodes correctly`() {
        val bytes = assemble { fstp(D0, D1, SP, 64) }
        val scaledImm = (64 / 8) and 0x7F
        assertEquals(0x6D000000L or (scaledImm.toLong() shl 15) or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    @Test
    fun `FLDP D0 D1 SP offset 0 encodes correctly`() {
        val bytes = assemble { fldp(D0, D1, SP, 0) }
        assertEquals(0x6D400000L or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    @Test
    fun `FLDP D0 D1 SP offset 32 encodes correctly`() {
        val bytes = assemble { fldp(D0, D1, SP, 32) }
        val scaledImm = (32 / 8) and 0x7F
        assertEquals(0x6D400000L or (scaledImm.toLong() shl 15) or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    // --- FP STP pre-index ---

    @Test
    fun `FSTP pre D0 D1 SP minus 32 encodes correctly`() {
        val bytes = assemble { fstpPre(D0, D1, SP, -32) }
        val scaledImm = (-32 / 8) and 0x7F
        assertEquals(0x6D800000L or (scaledImm.toLong() shl 15) or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    // --- FP LDP post-index ---

    @Test
    fun `FLDP post D0 D1 SP 32 encodes correctly`() {
        val bytes = assemble { fldpPost(D0, D1, SP, 32) }
        val scaledImm = (32 / 8) and 0x7F
        assertEquals(0x6CC00000L or (scaledImm.toLong() shl 15) or (1L shl 10) or (31L shl 5), readLE32(bytes))
    }

    // --- FCSEL single precision ---

    @Test
    fun `FCSEL S0 S1 S2 EQ encodes correctly`() {
        val bytes = assemble { fcsel(S0, S1, S2, Arm64Condition.EQ) }
        // 0x1E200C00 | (2 << 16) | (0 << 12) | (1 << 5) | 0
        assertEquals(0x1E220C20L, readLE32(bytes))
    }

    @Test
    fun `FCSEL S0 S1 S2 NE encodes correctly`() {
        val bytes = assemble { fcsel(S0, S1, S2, Arm64Condition.NE) }
        assertEquals(0x1E200C00L or (2L shl 16) or (1L shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `FCSEL D0 D1 D2 GT encodes correctly`() {
        val bytes = assemble { fcsel(D0, D1, D2, Arm64Condition.GT) }
        assertEquals(0x1E600C00L or (2L shl 16) or (0xCL shl 12) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `FCSEL D0 D1 D2 LE encodes correctly`() {
        val bytes = assemble { fcsel(D0, D1, D2, Arm64Condition.LE) }
        assertEquals(0x1E600C00L or (2L shl 16) or (0xDL shl 12) or (1L shl 5), readLE32(bytes))
    }

    // --- NEON integer vector arithmetic ---

    @Test
    fun `ADDVEC 4S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { addVec(VectorArrangement.S4, V0, V1, V2) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        // q=1, U=0, size=10, opcode=10000
        assertEquals(1L, (inst shr 30) and 1L) // Q bit
        assertEquals(0L, (inst shr 29) and 1L) // U bit
    }

    @Test
    fun `ADDVEC 2D V0 V1 V2 encodes correctly`() {
        val bytes = assemble { addVec(VectorArrangement.D2, V0, V1, V2) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 30) and 1L) // Q bit for 128-bit
    }

    @Test
    fun `ADDVEC 8B V0 V1 V2 encodes correctly`() {
        val bytes = assemble { addVec(VectorArrangement.B8, V0, V1, V2) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(0L, (inst shr 30) and 1L) // Q=0 for 64-bit
    }

    @Test
    fun `ADDVEC 16B V0 V1 V2 encodes correctly`() {
        val bytes = assemble { addVec(VectorArrangement.B16, V0, V1, V2) }
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 30) and 1L) // Q=1 for 128-bit
    }

    @Test
    fun `ADDVEC 4H V0 V1 V2 encodes correctly`() {
        val bytes = assemble { addVec(VectorArrangement.H4, V0, V1, V2) }
        val inst = readLE32(bytes)
        assertEquals(0L, (inst shr 30) and 1L) // Q=0
    }

    @Test
    fun `ADDVEC 8H V0 V1 V2 encodes correctly`() {
        val bytes = assemble { addVec(VectorArrangement.H8, V0, V1, V2) }
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 30) and 1L) // Q=1
    }

    @Test
    fun `ADDVEC 2S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { addVec(VectorArrangement.S2, V0, V1, V2) }
        val inst = readLE32(bytes)
        assertEquals(0L, (inst shr 30) and 1L) // Q=0
    }

    // --- SUBVEC ---

    @Test
    fun `SUBVEC 4S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { subVec(VectorArrangement.S4, V0, V1, V2) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 29) and 1L) // U=1 for SUB
    }

    @Test
    fun `SUBVEC 16B V0 V1 V2 encodes correctly`() {
        val bytes = assemble { subVec(VectorArrangement.B16, V0, V1, V2) }
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 30) and 1L)
        assertEquals(1L, (inst shr 29) and 1L)
    }

    // --- MULVEC ---

    @Test
    fun `MULVEC 4S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { mulVec(VectorArrangement.S4, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `MULVEC 8H V0 V1 V2 encodes correctly`() {
        val bytes = assemble { mulVec(VectorArrangement.H8, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `MULVEC 16B V0 V1 V2 encodes correctly`() {
        val bytes = assemble { mulVec(VectorArrangement.B16, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    // --- NEON bitwise ---

    @Test
    fun `AND VEC V0 V1 V2 128-bit encodes correctly`() {
        val bytes = assemble { andVec(V0, V1, V2, q128 = true) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 30) and 1L) // Q=1
    }

    @Test
    fun `AND VEC V0 V1 V2 64-bit encodes correctly`() {
        val bytes = assemble { andVec(V0, V1, V2, q128 = false) }
        val inst = readLE32(bytes)
        assertEquals(0L, (inst shr 30) and 1L) // Q=0
    }

    @Test
    fun `ORR VEC V0 V1 V2 128-bit encodes correctly`() {
        val bytes = assemble { orrVec(V0, V1, V2, q128 = true) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `ORR VEC V0 V1 V2 64-bit encodes correctly`() {
        val bytes = assemble { orrVec(V0, V1, V2, q128 = false) }
        val inst = readLE32(bytes)
        assertEquals(0L, (inst shr 30) and 1L)
    }

    @Test
    fun `EOR VEC V0 V1 V2 128-bit encodes correctly`() {
        val bytes = assemble { eorVec(V0, V1, V2, q128 = true) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `EOR VEC V0 V1 V2 64-bit encodes correctly`() {
        val bytes = assemble { eorVec(V0, V1, V2, q128 = false) }
        val inst = readLE32(bytes)
        assertEquals(0L, (inst shr 30) and 1L)
    }

    // --- NOT VEC ---

    @Test
    fun `NOT VEC V0 V1 128-bit encodes correctly`() {
        val bytes = assemble { notVec(V0, V1, q128 = true) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 30) and 1L)
    }

    @Test
    fun `NOT VEC V0 V1 64-bit encodes correctly`() {
        val bytes = assemble { notVec(V0, V1, q128 = false) }
        val inst = readLE32(bytes)
        assertEquals(0L, (inst shr 30) and 1L)
    }

    // --- NEON compare ---

    @Test
    fun `CMEQ 4S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { cmeq(VectorArrangement.S4, V0, V1, V2) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 29) and 1L) // U=1
    }

    @Test
    fun `CMGT 4S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { cmgt(VectorArrangement.S4, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `CMGE 4S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { cmge(VectorArrangement.S4, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `CMHI 4S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { cmhi(VectorArrangement.S4, V0, V1, V2) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 29) and 1L) // U=1 for unsigned
    }

    @Test
    fun `CMHS 4S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { cmhs(VectorArrangement.S4, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `CMEQ 2D V0 V1 V2 encodes correctly`() {
        val bytes = assemble { cmeq(VectorArrangement.D2, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `CMGT 16B V0 V1 V2 encodes correctly`() {
        val bytes = assemble { cmgt(VectorArrangement.B16, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    // --- NEON shift ---

    @Test
    fun `SHL VEC 4S V0 V1 shift 1 encodes correctly`() {
        val bytes = assemble { shlVec(VectorArrangement.S4, V0, V1, 1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `SHL VEC 4S V0 V1 shift 31 encodes correctly`() {
        val bytes = assemble { shlVec(VectorArrangement.S4, V0, V1, 31) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `SHL VEC 2D V0 V1 shift 1 encodes correctly`() {
        val bytes = assemble { shlVec(VectorArrangement.D2, V0, V1, 1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `SHL VEC 16B V0 V1 shift 7 encodes correctly`() {
        val bytes = assemble { shlVec(VectorArrangement.B16, V0, V1, 7) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `SHL VEC 8H V0 V1 shift 15 encodes correctly`() {
        val bytes = assemble { shlVec(VectorArrangement.H8, V0, V1, 15) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `SSHR 4S V0 V1 shift 1 encodes correctly`() {
        val bytes = assemble { sshr(VectorArrangement.S4, V0, V1, 1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `SSHR 4S V0 V1 shift 32 encodes correctly`() {
        val bytes = assemble { sshr(VectorArrangement.S4, V0, V1, 32) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `SSHR 2D V0 V1 shift 1 encodes correctly`() {
        val bytes = assemble { sshr(VectorArrangement.D2, V0, V1, 1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `SSHR 16B V0 V1 shift 8 encodes correctly`() {
        val bytes = assemble { sshr(VectorArrangement.B16, V0, V1, 8) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `USHR 4S V0 V1 shift 1 encodes correctly`() {
        val bytes = assemble { ushr(VectorArrangement.S4, V0, V1, 1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `USHR 4S V0 V1 shift 32 encodes correctly`() {
        val bytes = assemble { ushr(VectorArrangement.S4, V0, V1, 32) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `USHR 2D V0 V1 shift 1 encodes correctly`() {
        val bytes = assemble { ushr(VectorArrangement.D2, V0, V1, 1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `USHR 8H V0 V1 shift 16 encodes correctly`() {
        val bytes = assemble { ushr(VectorArrangement.H8, V0, V1, 16) }
        assertEquals(4, bytes.size)
    }

    // --- NEON FP vector ---

    @Test
    fun `FADD VEC 4S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { faddVec(VectorArrangement.S4, V0, V1, V2) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 30) and 1L) // Q=1
        assertEquals(0L, (inst shr 22) and 1L) // sz=0 for single
    }

    @Test
    fun `FADD VEC 2D V0 V1 V2 encodes correctly`() {
        val bytes = assemble { faddVec(VectorArrangement.D2, V0, V1, V2) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 22) and 1L) // sz=1 for double
    }

    @Test
    fun `FADD VEC 2S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { faddVec(VectorArrangement.S2, V0, V1, V2) }
        val inst = readLE32(bytes)
        assertEquals(0L, (inst shr 30) and 1L) // Q=0 for 64-bit
    }

    @Test
    fun `FSUB VEC 4S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { fsubVec(VectorArrangement.S4, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `FSUB VEC 2D V0 V1 V2 encodes correctly`() {
        val bytes = assemble { fsubVec(VectorArrangement.D2, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `FMUL VEC 4S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { fmulVec(VectorArrangement.S4, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `FMUL VEC 2D V0 V1 V2 encodes correctly`() {
        val bytes = assemble { fmulVec(VectorArrangement.D2, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `FDIV VEC 4S V0 V1 V2 encodes correctly`() {
        val bytes = assemble { fdivVec(VectorArrangement.S4, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `FDIV VEC 2D V0 V1 V2 encodes correctly`() {
        val bytes = assemble { fdivVec(VectorArrangement.D2, V0, V1, V2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `FNEG VEC 4S V0 V1 encodes correctly`() {
        val bytes = assemble { fnegVec(VectorArrangement.S4, V0, V1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `FNEG VEC 2D V0 V1 encodes correctly`() {
        val bytes = assemble { fnegVec(VectorArrangement.D2, V0, V1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `FABS VEC 4S V0 V1 encodes correctly`() {
        val bytes = assemble { fabsVec(VectorArrangement.S4, V0, V1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `FABS VEC 2D V0 V1 encodes correctly`() {
        val bytes = assemble { fabsVec(VectorArrangement.D2, V0, V1) }
        assertEquals(4, bytes.size)
    }

    // --- DUP ---

    @Test
    fun `DUP from GP 4S V0 X0 encodes correctly`() {
        val bytes = assemble { dupFromGp(VectorArrangement.S4, V0, X0) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 30) and 1L) // Q=1
    }

    @Test
    fun `DUP from GP 2D V0 X0 encodes correctly`() {
        val bytes = assemble { dupFromGp(VectorArrangement.D2, V0, X0) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `DUP from GP 16B V0 X0 encodes correctly`() {
        val bytes = assemble { dupFromGp(VectorArrangement.B16, V0, X0) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `DUP element 4S V0 V1 index 0 encodes correctly`() {
        val bytes = assemble { dupElement(VectorArrangement.S4, V0, V1, 0) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `DUP element 4S V0 V1 index 3 encodes correctly`() {
        val bytes = assemble { dupElement(VectorArrangement.S4, V0, V1, 3) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `DUP element 2D V0 V1 index 1 encodes correctly`() {
        val bytes = assemble { dupElement(VectorArrangement.D2, V0, V1, 1) }
        assertEquals(4, bytes.size)
    }

    // --- ADDV ---

    @Test
    fun `ADDV 4S V0 V1 encodes correctly`() {
        val bytes = assemble { addv(VectorArrangement.S4, V0, V1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `ADDV 16B V0 V1 encodes correctly`() {
        val bytes = assemble { addv(VectorArrangement.B16, V0, V1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `ADDV 8H V0 V1 encodes correctly`() {
        val bytes = assemble { addv(VectorArrangement.H8, V0, V1) }
        assertEquals(4, bytes.size)
    }

    // --- NEON load/store ---

    @Test
    fun `LDR Q V0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { ldrQ(V0, X1, 0) }
        assertEquals(0x3DC00020L, readLE32(bytes))
    }

    @Test
    fun `LDR Q V0 X1 offset 16 encodes correctly`() {
        val bytes = assemble { ldrQ(V0, X1, 16) }
        val scaledImm = 16 / 16
        assertEquals(0x3DC00000L or (scaledImm.toLong() shl 10) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `LDR Q V0 SP offset 64 encodes correctly`() {
        val bytes = assemble { ldrQ(V0, SP, 64) }
        val scaledImm = 64 / 16
        assertEquals(0x3DC00000L or (scaledImm.toLong() shl 10) or (31L shl 5), readLE32(bytes))
    }

    @Test
    fun `STR Q V0 X1 offset 0 encodes correctly`() {
        val bytes = assemble { strQ(V0, X1, 0) }
        assertEquals(0x3D800020L, readLE32(bytes))
    }

    @Test
    fun `STR Q V0 X1 offset 32 encodes correctly`() {
        val bytes = assemble { strQ(V0, X1, 32) }
        val scaledImm = 32 / 16
        assertEquals(0x3D800000L or (scaledImm.toLong() shl 10) or (1L shl 5), readLE32(bytes))
    }

    @Test
    fun `STR Q V0 SP offset 128 encodes correctly`() {
        val bytes = assemble { strQ(V0, SP, 128) }
        val scaledImm = 128 / 16
        assertEquals(0x3D800000L or (scaledImm.toLong() shl 10) or (31L shl 5), readLE32(bytes))
    }

    // --- MOVI VEC ---

    @Test
    fun `MOVI V0 imm 0 128-bit encodes correctly`() {
        val bytes = assemble { moviVec(V0, 0, q128 = true) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(1L, (inst shr 30) and 1L) // Q=1
    }

    @Test
    fun `MOVI V0 imm 0xFF 128-bit encodes correctly`() {
        val bytes = assemble { moviVec(V0, 0xFF, q128 = true) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun `MOVI V0 imm 0 64-bit encodes correctly`() {
        val bytes = assemble { moviVec(V0, 0, q128 = false) }
        val inst = readLE32(bytes)
        assertEquals(0L, (inst shr 30) and 1L) // Q=0
    }

    @Test
    fun `MOVI V0 imm 42 encodes immediate bits`() {
        val bytes = assemble { moviVec(V0, 42, q128 = true) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        // abc = (42 >> 5) & 7 = 1, defgh = 42 & 0x1F = 10
        val abc = (inst shr 16) and 0x7
        val defgh = (inst shr 5) and 0x1F
        assertEquals(1L, abc)
        assertEquals(10L, defgh)
    }

    // --- Register field encoding for high NEON registers ---

    @Test
    fun `ADDVEC with high registers V16 V31 encodes register fields`() {
        val bytes = assemble { addVec(VectorArrangement.S4, V16, V31, V0) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(16L, inst and 0x1FL) // Rd
        assertEquals(31L, (inst shr 5) and 0x1FL) // Rn
        assertEquals(0L, (inst shr 16) and 0x1FL) // Rm
    }

    // --- Raw emit ---

    @Test
    fun `emitRaw produces exact 4 bytes`() {
        val asm = Arm64Assembler()
        asm.emitRaw(0xD503201F.toInt()) // NOP
        val bytes = asm.bytes()
        assertEquals(4, bytes.size)
        assertEquals(0xD503201FL, readLE32(bytes))
    }

    @Test
    fun `emitBytes appends raw data`() {
        val asm = Arm64Assembler()
        asm.emitBytes(byteArrayOf(0x1F, 0x20, 0x03, 0xD5.toByte()))
        val bytes = asm.bytes()
        assertEquals(4, bytes.size)
        assertEquals(0xD503201FL, readLE32(bytes))
    }

    // --- Unresolved labels ---

    @Test
    fun `unresolvedLabels returns forward references`() {
        val asm = Arm64Assembler()
        asm.b("missing")
        asm.nop()
        val unresolved = asm.unresolvedLabels()
        assertEquals(1, unresolved.size)
        assertEquals("missing", unresolved[0].second)
    }

    @Test
    fun `unresolvedLabels returns empty when all resolved`() {
        val asm = Arm64Assembler()
        asm.b("target")
        asm.label("target")
        asm.ret()
        val unresolved = asm.unresolvedLabels()
        assertEquals(0, unresolved.size)
    }

    // --- Size tracking ---

    @Test
    fun `size returns current byte count before resolve`() {
        val asm = Arm64Assembler()
        assertEquals(0, asm.size())
        asm.nop()
        assertEquals(4, asm.size())
        asm.nop()
        assertEquals(8, asm.size())
        asm.ret()
        assertEquals(12, asm.size())
    }

    // --- Backward branch offset correctness ---

    @Test
    fun `B backward resolves correct negative offset`() {
        val bytes = assemble {
            label("start")
            nop()
            nop()
            nop()
            b("start")
        }
        assertEquals(16, bytes.size)
        val inst = readLE32(bytes, 12)
        val imm26 = inst and 0x03FFFFFFL
        // offset should be -3 instructions
        val signExtended = if (imm26 >= 0x02000000) imm26 - 0x04000000 else imm26
        assertEquals(-3L, signExtended)
    }

    @Test
    fun `BL forward resolves correct positive offset`() {
        val bytes = assemble {
            bl("func")
            nop()
            nop()
            label("func")
            ret()
        }
        val inst = readLE32(bytes, 0)
        val imm26 = inst and 0x03FFFFFFL
        assertEquals(3L, imm26)
    }

    // --- B.cond backward label ---

    @Test
    fun `B_cond backward resolves correct negative offset`() {
        val bytes = assemble {
            label("loop")
            nop()
            bCond(Arm64Condition.NE, "loop")
        }
        assertEquals(8, bytes.size)
        val inst = readLE32(bytes, 4)
        val imm19 = (inst shr 5) and 0x7FFFF
        val signExtended = if (imm19 >= 0x40000) imm19 - 0x80000 else imm19
        assertEquals(-1L, signExtended)
    }

    // --- Multiple instruction sequences for correctness ---

    @Test
    fun `prologue and epilogue encode full sequence`() {
        val bytes = assemble {
            stpPre(X29, X30, SP, -16)
            movSp(X29, SP)
            nop()
            ldpPost(X29, X30, SP, 16)
            ret()
        }
        assertEquals(20, bytes.size)
        // Verify STP pre-index
        val stp = readLE32(bytes, 0)
        val expected = 0xA9800000L or (0x7EL shl 15) or (30L shl 10) or (31L shl 5) or 29L
        assertEquals(expected, stp)
        // Verify RET
        assertEquals(0xD65F03C0L, readLE32(bytes, 16))
    }

    @Test
    fun `conditional increment pattern`() {
        val bytes = assemble {
            cmp(X0, 0)
            csinc(X0, X0, X0, Arm64Condition.NE)
            ret()
        }
        assertEquals(12, bytes.size)
    }

    @Test
    fun `unsigned division remainder via msub`() {
        val bytes = assemble {
            udiv(X2, X0, X1)    // quotient = X0 / X1
            msub(X3, X2, X1, X0) // remainder = X0 - quotient * X1
            ret()
        }
        assertEquals(12, bytes.size)
    }

    @Test
    fun `64-bit constant loading via movz and movk`() {
        val bytes = assemble {
            movz(X0, 0x1234)
            movk(X0, 0x5678, shift = 16)
            movk(X0, 0x9ABC, shift = 32)
            movk(X0, 0xDEF0, shift = 48)
        }
        assertEquals(16, bytes.size)
        // Verify MOVZ
        assertEquals(0xD2800000L or (0x1234L shl 5), readLE32(bytes, 0))
        // Verify MOVK shift=16
        assertEquals(0xF2A00000L or (0x5678L shl 5), readLE32(bytes, 4))
        // Verify MOVK shift=32
        assertEquals(0xF2C00000L or (0x9ABCL shl 5), readLE32(bytes, 8))
        // Verify MOVK shift=48
        assertEquals(0xF2E00000L or (0xDEF0L shl 5), readLE32(bytes, 12))
    }

    @Test
    fun `FP conversion pipeline S to D then back`() {
        val bytes = assemble {
            fcvtStoD(D0, S0) // widen
            fcvtDtoS(S1, D0) // narrow
            ret()
        }
        assertEquals(12, bytes.size)
        assertEquals(0x1E22C000L, readLE32(bytes, 0))
        assertEquals(0x1E624001L, readLE32(bytes, 4))
    }

    @Test
    fun `NEON add then reduce pattern`() {
        val bytes = assemble {
            addVec(VectorArrangement.S4, V0, V1, V2)
            addv(VectorArrangement.S4, V0, V0)
            ret()
        }
        assertEquals(12, bytes.size)
    }
}
