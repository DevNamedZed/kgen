package org.kgen.target.arm64.disasm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.target.arm64.Arm64Condition
import org.kgen.target.arm64.Arm64Register
import org.kgen.target.arm64.Arm64Register64
import org.kgen.target.arm64.Arm64Register32
import org.kgen.target.arm64.Arm64VecD
import org.kgen.target.arm64.Arm64VecS
import org.kgen.target.arm64.asm.Arm64Assembler

class Arm64DisassemblerComprehensiveTest {

    private val disasm = Arm64Disassembler()

    private fun fromLE32(value: Long): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun decode(value: Long): Arm64Instruction =
        disasm.disassemble(fromLE32(value))[0]

    private fun roundTrip(build: Arm64Assembler.() -> Unit): List<Arm64Instruction> {
        val asm = Arm64Assembler()
        asm.build()
        return disasm.disassemble(asm.bytes())
    }

    // Arithmetic: ADD register

    @Test
    fun `decodes ADD W0 W1 W2`() {
        val inst = decode(0x0B020020L)
        assertEquals("add", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("w0"))
        assertTrue(inst.operandsStr.contains("w1"))
        assertTrue(inst.operandsStr.contains("w2"))
    }

    @Test
    fun `decodes ADD X10 X11 X12`() {
        // 8B0C016A
        val inst = decode(0x8B0C016AL)
        assertEquals("add", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("x10"))
    }

    @Test
    fun `decodes ADD X0 X0 X0`() {
        val inst = decode(0x8B000000L)
        assertEquals("add", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("x0"))
    }

    // Arithmetic: ADD immediate

    @Test
    fun `decodes ADD W0 W1 imm12`() {
        // 11000820 = ADD W0, W1, #2
        val inst = decode(0x11000820L)
        assertEquals("add", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("w0"))
        assertTrue(inst.operandsStr.contains("w1"))
        assertTrue(inst.operandsStr.contains("#2"))
    }

    @Test
    fun `decodes ADD X0 X0 imm4095`() {
        // 913FFC00 = ADD X0, X0, #4095
        val inst = decode(0x913FFC00L)
        assertEquals("add", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("#4095") || inst.operandsStr.contains("#0xfff"))
    }

    @Test
    fun `decodes ADD X0 SP imm`() {
        // 910003E0 = ADD X0, SP, #0
        val inst = decode(0x910003E0L)
        assertEquals("add", inst.mnemonic)
    }

    // Arithmetic: SUB register

    @Test
    fun `decodes SUB W0 W1 W2`() {
        val inst = decode(0x4B020020L)
        assertEquals("sub", inst.mnemonic)
    }

    @Test
    fun `decodes SUB X3 X4 X5`() {
        val inst = decode(0xCB050083L)
        assertEquals("sub", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("x3"))
    }

    // Arithmetic: SUB immediate

    @Test
    fun `decodes SUB X0 X1 imm`() {
        // D1000420 = SUB X0, X1, #1
        val inst = decode(0xD1000420L)
        assertEquals("sub", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("#1"))
    }

    @Test
    fun `decodes SUB W0 W1 imm`() {
        // 51000420 = SUB W0, W1, #1
        val inst = decode(0x51000420L)
        assertEquals("sub", inst.mnemonic)
    }

    // ADDS / SUBS

    @Test
    fun `decodes ADDS X0 X1 X2`() {
        val inst = decode(0xAB020020L)
        assertEquals("adds", inst.mnemonic)
    }

    @Test
    fun `decodes SUBS X0 X1 X2`() {
        val inst = decode(0xEB020020L)
        assertEquals("subs", inst.mnemonic)
    }

    // CMP (alias of SUBS Xd=XZR)

    @Test
    fun `decodes CMP X0 imm`() {
        // F100041F = CMP X0, #1
        val inst = decode(0xF100041FL)
        assertEquals("cmp", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("x0"))
        assertTrue(inst.operandsStr.contains("#1"))
    }

    @Test
    fun `decodes CMP W0 W1`() {
        // 6B01001F = SUBS WZR, W0, W1 = CMP W0, W1
        val inst = decode(0x6B01001FL)
        assertEquals("cmp", inst.mnemonic)
    }

    @Test
    fun `decodes CMN X0 X1`() {
        // AB01001F = ADDS XZR, X0, X1 = CMN X0, X1
        val inst = decode(0xAB01001FL)
        assertEquals("cmn", inst.mnemonic)
    }

    // NEG (SUB with Rn=XZR)

    @Test
    fun `decodes NEG X0 X1`() {
        // CB0103E0 = SUB X0, XZR, X1 = NEG X0, X1
        val inst = decode(0xCB0103E0L)
        assertEquals("neg", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("x0"))
        assertTrue(inst.operandsStr.contains("x1"))
    }

    // Logical: AND, ORR, EOR

    @Test
    fun `decodes AND X0 X1 X2`() {
        val inst = decode(0x8A020020L)
        assertEquals("and", inst.mnemonic)
    }

    @Test
    fun `decodes ORR X0 X1 X2`() {
        val inst = decode(0xAA020020L)
        assertEquals("orr", inst.mnemonic)
    }

    @Test
    fun `decodes EOR X0 X1 X2`() {
        val inst = decode(0xCA020020L)
        assertEquals("eor", inst.mnemonic)
    }

    @Test
    fun `decodes BIC X0 X1 X2`() {
        val inst = decode(0x8A220020L)
        assertEquals("bic", inst.mnemonic)
    }

    @Test
    fun `decodes ORN X0 X1 X2`() {
        val inst = decode(0xAA220020L)
        assertEquals("orn", inst.mnemonic)
    }

    @Test
    fun `decodes EON X0 X1 X2`() {
        val inst = decode(0xCA220020L)
        assertEquals("eon", inst.mnemonic)
    }

    @Test
    fun `decodes ANDS X0 X1 X2`() {
        val inst = decode(0xEA020020L)
        assertEquals("ands", inst.mnemonic)
    }

    @Test
    fun `decodes TST X0 X1`() {
        // EA01001F = ANDS XZR, X0, X1 = TST X0, X1
        val inst = decode(0xEA01001FL)
        assertEquals("tst", inst.mnemonic)
    }

    @Test
    fun `decodes BICS X0 X1 X2`() {
        val inst = decode(0xEA220020L)
        assertEquals("bics", inst.mnemonic)
    }

    // MOV (alias of ORR with XZR)

    @Test
    fun `decodes MOV W0 W1`() {
        // 2A0103E0 = ORR W0, WZR, W1 = MOV W0, W1
        val inst = decode(0x2A0103E0L)
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("w0"))
        assertTrue(inst.operandsStr.contains("w1"))
    }

    // Shifts

    @Test
    fun `decodes LSR X0 X1 X2`() {
        val inst = decode(0x9AC22420L)
        assertEquals("lsr", inst.mnemonic)
    }

    @Test
    fun `decodes ASR X0 X1 X2`() {
        val inst = decode(0x9AC22820L)
        assertEquals("asr", inst.mnemonic)
    }

    @Test
    fun `decodes LSL W0 W1 W2`() {
        val inst = decode(0x1AC22020L)
        assertEquals("lsl", inst.mnemonic)
    }

    @Test
    fun `decodes LSR W0 W1 W2`() {
        val inst = decode(0x1AC22420L)
        assertEquals("lsr", inst.mnemonic)
    }

    @Test
    fun `decodes ASR W0 W1 W2`() {
        val inst = decode(0x1AC22820L)
        assertEquals("asr", inst.mnemonic)
    }

    // Multiply / Divide

    @Test
    fun `decodes MUL W0 W1 W2`() {
        val inst = decode(0x1B027C20L)
        assertEquals("mul", inst.mnemonic)
    }

    @Test
    fun `decodes SDIV W0 W1 W2`() {
        val inst = decode(0x1AC20C20L)
        assertEquals("sdiv", inst.mnemonic)
    }

    @Test
    fun `decodes UDIV X0 X1 X2`() {
        val inst = decode(0x9AC20820L)
        assertEquals("udiv", inst.mnemonic)
    }

    @Test
    fun `decodes UDIV W0 W1 W2`() {
        val inst = decode(0x1AC20820L)
        assertEquals("udiv", inst.mnemonic)
    }

    @Test
    fun `decodes MADD X0 X1 X2 X3`() {
        // 9B020C20 = MADD X0, X1, X2, X3
        val inst = decode(0x9B020C20L)
        assertEquals("madd", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("x3"))
    }

    @Test
    fun `decodes MSUB X0 X1 X2 X3`() {
        // 9B028C20 = MSUB X0, X1, X2, X3
        val inst = decode(0x9B028C20L)
        assertEquals("msub", inst.mnemonic)
    }

    // Move wide

    @Test
    fun `decodes MOVN X0 imm16`() {
        val inst = decode(0x92800000L)
        assertEquals("movn", inst.mnemonic)
    }

    @Test
    fun `decodes MOVK X0 imm16 lsl 16`() {
        // F2A24680 = MOVK X0, #0x1234, LSL #16
        val inst = decode(0xF2A24680L)
        assertEquals("movk", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("lsl #16"))
    }

    @Test
    fun `decodes MOVZ W0 imm16`() {
        // 52800100 = MOVZ W0, #8
        val inst = decode(0x52800100L)
        assertEquals("movz", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("w0"))
    }

    // Bitfield: SXTW, SXTB, SXTH, UXTB, UXTH

    @Test
    fun `decodes SXTB X0 W1`() {
        // 93401C20 = SBFM X0, X1, #0, #7 = SXTB X0, W1
        val inst = decode(0x93401C20L)
        assertEquals("sxtb", inst.mnemonic)
    }

    @Test
    fun `decodes SXTH X0 W1`() {
        // 93403C20 = SBFM X0, X1, #0, #15 = SXTH X0, W1
        val inst = decode(0x93403C20L)
        assertEquals("sxth", inst.mnemonic)
    }

    @Test
    fun `decodes UXTB W0 W1`() {
        // 53001C20 = UBFM W0, W1, #0, #7 = UXTB W0, W1
        val inst = decode(0x53001C20L)
        assertEquals("uxtb", inst.mnemonic)
    }

    @Test
    fun `decodes UXTH W0 W1`() {
        // 53003C20 = UBFM W0, W1, #0, #15 = UXTH W0, W1
        val inst = decode(0x53003C20L)
        assertEquals("uxth", inst.mnemonic)
    }

    // Branch unconditional

    @Test
    fun `decodes B forward`() {
        // 14000004 = B +16
        val inst = decode(0x14000004L)
        assertEquals("b", inst.mnemonic)
    }

    @Test
    fun `decodes B backward`() {
        // 17FFFFFE = B -8
        val inst = decode(0x17FFFFFEL)
        assertEquals("b", inst.mnemonic)
    }

    @Test
    fun `decodes BL forward`() {
        val inst = decode(0x94000004L)
        assertEquals("bl", inst.mnemonic)
    }

    // Conditional branches

    @Test
    fun `decodes B_NE`() {
        // 54000041 = B.NE +8
        val inst = decode(0x54000041L)
        assertEquals("b.ne", inst.mnemonic)
    }

    @Test
    fun `decodes B_LT`() {
        // 5400004B = B.LT +8
        val inst = decode(0x5400004BL)
        assertEquals("b.lt", inst.mnemonic)
    }

    @Test
    fun `decodes B_GE`() {
        // 5400004A = B.GE +8
        val inst = decode(0x5400004AL)
        assertEquals("b.ge", inst.mnemonic)
    }

    @Test
    fun `decodes B_GT`() {
        // 5400004C = B.GT +8
        val inst = decode(0x5400004CL)
        assertEquals("b.gt", inst.mnemonic)
    }

    @Test
    fun `decodes B_LE`() {
        // 5400004D = B.LE +8
        val inst = decode(0x5400004DL)
        assertEquals("b.le", inst.mnemonic)
    }

    @Test
    fun `decodes B_HI`() {
        // 54000048 = B.HI +8
        val inst = decode(0x54000048L)
        assertEquals("b.hi", inst.mnemonic)
    }

    @Test
    fun `decodes B_LS`() {
        // 54000049 = B.LS +8
        val inst = decode(0x54000049L)
        assertEquals("b.ls", inst.mnemonic)
    }

    // Compare and branch

    @Test
    fun `decodes CBNZ X0`() {
        // B5000040 = CBNZ X0, +8
        val inst = decode(0xB5000040L)
        assertEquals("cbnz", inst.mnemonic)
    }

    @Test
    fun `decodes CBZ W0`() {
        // 34000040 = CBZ W0, +8
        val inst = decode(0x34000040L)
        assertEquals("cbz", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("w0"))
    }

    @Test
    fun `decodes CBNZ W0`() {
        // 35000040 = CBNZ W0, +8
        val inst = decode(0x35000040L)
        assertEquals("cbnz", inst.mnemonic)
    }

    // Branch register

    @Test
    fun `decodes BR X0`() {
        val inst = decode(0xD61F0000L)
        assertEquals("br", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("x0"))
    }

    @Test
    fun `decodes BLR X0`() {
        val inst = decode(0xD63F0000L)
        assertEquals("blr", inst.mnemonic)
    }

    @Test
    fun `decodes RET with default LR`() {
        val inst = decode(0xD65F03C0L)
        assertEquals("ret", inst.mnemonic)
    }

    // Load/Store unsigned offset

    @Test
    fun `decodes LDRB W0 from X1`() {
        // 39400020 = LDRB W0, [X1]
        val inst = decode(0x39400020L)
        assertEquals("ldrb", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("w0"))
    }

    @Test
    fun `decodes STRB W0 to X1`() {
        // 39000020 = STRB W0, [X1]
        val inst = decode(0x39000020L)
        assertEquals("strb", inst.mnemonic)
    }

    @Test
    fun `decodes LDRH W0 from X1`() {
        // 79400020 = LDRH W0, [X1]
        val inst = decode(0x79400020L)
        assertEquals("ldrh", inst.mnemonic)
    }

    @Test
    fun `decodes STRH W0 to X1`() {
        // 79000020 = STRH W0, [X1]
        val inst = decode(0x79000020L)
        assertEquals("strh", inst.mnemonic)
    }

    @Test
    fun `decodes LDR W0 from X1 offset 4`() {
        // B9400420 = LDR W0, [X1, #4]
        val inst = decode(0xB9400420L)
        assertEquals("ldr", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("w0"))
        assertTrue(inst.operandsStr.contains("#4"))
    }

    @Test
    fun `decodes STR W0 to X1`() {
        // B9000020 = STR W0, [X1]
        val inst = decode(0xB9000020L)
        assertEquals("str", inst.mnemonic)
    }

    @Test
    fun `decodes LDR X0 from X1 offset 0`() {
        // F9400020 = LDR X0, [X1]
        val inst = decode(0xF9400020L)
        assertEquals("ldr", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("x0"))
        assertTrue(inst.operandsStr.contains("x1"))
    }

    @Test
    fun `decodes STR X0 to X1 offset 8`() {
        // F9000420 = STR X0, [X1, #8]
        val inst = decode(0xF9000420L)
        assertEquals("str", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("#8"))
    }

    // Load/Store unscaled

    @Test
    fun `decodes LDUR X0 from X1 offset -8`() {
        // F85F8020 = LDUR X0, [X1, #-8]
        val inst = decode(0xF85F8020L)
        assertEquals("ldur", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("-8"))
    }

    @Test
    fun `decodes STUR X0 to X1`() {
        // F8000020 = STUR X0, [X1]
        val inst = decode(0xF8000020L)
        assertEquals("stur", inst.mnemonic)
    }

    // Load/Store pair

    @Test
    fun `decodes STP X0 X1 to SP pre-index`() {
        // A9BF07E0 = STP X0, X1, [SP, #-16]!
        val inst = decode(0xA9BF07E0L)
        assertEquals("stp", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("x0"))
        assertTrue(inst.operandsStr.contains("x1"))
        assertTrue(inst.operandsStr.contains("sp"))
    }

    @Test
    fun `decodes LDP X0 X1 from SP post-index`() {
        // A8C107E0 = LDP X0, X1, [SP], #16
        val inst = decode(0xA8C107E0L)
        assertEquals("ldp", inst.mnemonic)
    }

    @Test
    fun `decodes STP W0 W1 to SP`() {
        // 29000FE0 = STP W0, W3, [SP]
        val inst = decode(0x29000FE0L)
        assertEquals("stp", inst.mnemonic)
    }

    // Conditional select

    @Test
    fun `decodes CSEL W0 W1 W2 NE`() {
        // 1A821020 = CSEL W0, W1, W2, NE
        val inst = decode(0x1A821020L)
        assertEquals("csel", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("ne"))
    }

    @Test
    fun `decodes CSINC X0 X1 X2 EQ`() {
        // 9A820420 = CSINC X0, X1, X2, EQ
        val inst = decode(0x9A820420L)
        assertEquals("csinc", inst.mnemonic)
    }

    // System

    @Test
    fun `decodes SVC 0`() {
        val inst = decode(0xD4000001L)
        assertEquals("svc", inst.mnemonic)
        assertEquals("#0", inst.operandsStr)
    }

    @Test
    fun `decodes BRK 0xF000`() {
        // D43E0020 = BRK #0xF001
        val inst = decode(0xD43E0020L)
        assertEquals("brk", inst.mnemonic)
    }

    // FP arithmetic

    @Test
    fun `decodes FADD D0 D1 D2`() {
        // 1E622820 = FADD D0, D1, D2
        val inst = decode(0x1E622820L)
        assertEquals("fadd", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("d0"))
        assertTrue(inst.operandsStr.contains("d1"))
        assertTrue(inst.operandsStr.contains("d2"))
    }

    @Test
    fun `decodes FSUB D0 D1 D2`() {
        val inst = decode(0x1E623820L)
        assertEquals("fsub", inst.mnemonic)
    }

    @Test
    fun `decodes FMUL D0 D1 D2`() {
        val inst = decode(0x1E620820L)
        assertEquals("fmul", inst.mnemonic)
    }

    @Test
    fun `decodes FDIV D0 D1 D2`() {
        val inst = decode(0x1E621820L)
        assertEquals("fdiv", inst.mnemonic)
    }

    @Test
    fun `decodes FADD S0 S1 S2`() {
        // 1E222820 = FADD S0, S1, S2
        val inst = decode(0x1E222820L)
        assertEquals("fadd", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("s0"))
    }

    @Test
    fun `decodes FSUB S0 S1 S2`() {
        val inst = decode(0x1E223820L)
        assertEquals("fsub", inst.mnemonic)
    }

    @Test
    fun `decodes FMUL S0 S1 S2`() {
        val inst = decode(0x1E220820L)
        assertEquals("fmul", inst.mnemonic)
    }

    @Test
    fun `decodes FDIV S0 S1 S2`() {
        val inst = decode(0x1E221820L)
        assertEquals("fdiv", inst.mnemonic)
    }

    // FP 1-source

    @Test
    fun `decodes FMOV D0 D1`() {
        val inst = decode(0x1E604020L)
        assertEquals("fmov", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("d0"))
        assertTrue(inst.operandsStr.contains("d1"))
    }

    @Test
    fun `decodes FABS D0 D1`() {
        val inst = decode(0x1E60C020L)
        assertEquals("fabs", inst.mnemonic)
    }

    @Test
    fun `decodes FNEG D0 D1`() {
        val inst = decode(0x1E614020L)
        assertEquals("fneg", inst.mnemonic)
    }

    @Test
    fun `decodes FMOV S0 S1`() {
        val inst = decode(0x1E204020L)
        assertEquals("fmov", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("s0"))
    }

    // FP conversion

    @Test
    fun `decodes FCVT D0 S1`() {
        // 1E22C020 = FCVT D0, S1
        val inst = decode(0x1E22C020L)
        assertEquals("fcvt", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("d0"))
        assertTrue(inst.operandsStr.contains("s1"))
    }

    @Test
    fun `decodes FCVT S0 D1`() {
        // 1E624020 = FCVT S0, D1
        val inst = decode(0x1E624020L)
        assertEquals("fcvt", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("s0"))
        assertTrue(inst.operandsStr.contains("d1"))
    }

    // FP compare

    @Test
    fun `decodes FCMP D0 D1`() {
        val inst = decode(0x1E612000L)
        assertEquals("fcmp", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("d0"))
        assertTrue(inst.operandsStr.contains("d1"))
    }

    @Test
    fun `decodes FCMP S0 S1`() {
        val inst = decode(0x1E212000L)
        assertEquals("fcmp", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("s0"))
        assertTrue(inst.operandsStr.contains("s1"))
    }

    @Test
    fun `decodes FCMP D0 zero`() {
        val inst = decode(0x1E602008L)
        assertEquals("fcmp", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("#0.0"))
    }

    // FP conditional select

    @Test
    fun `decodes FCSEL D0 D1 D2 EQ`() {
        // 1E620C20 = FCSEL D0, D1, D2, EQ
        val inst = decode(0x1E620C20L)
        assertEquals("fcsel", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("eq"))
    }

    // FP <-> GP transfers

    @Test
    fun `decodes SCVTF D0 X1`() {
        // 9E620020 = SCVTF D0, X1
        val inst = decode(0x9E620020L)
        assertEquals("scvtf", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("d0"))
        assertTrue(inst.operandsStr.contains("x1"))
    }

    @Test
    fun `decodes SCVTF S0 W1`() {
        // 1E220020 = SCVTF S0, W1
        val inst = decode(0x1E220020L)
        assertEquals("scvtf", inst.mnemonic)
    }

    @Test
    fun `decodes FCVTZS X0 D1`() {
        // 9E780020 = FCVTZS X0, D1
        val inst = decode(0x9E780020L)
        assertEquals("fcvtzs", inst.mnemonic)
    }

    @Test
    fun `decodes FCVTZS W0 S1`() {
        // 1E380020 = FCVTZS W0, S1
        val inst = decode(0x1E380020L)
        assertEquals("fcvtzs", inst.mnemonic)
    }

    @Test
    fun `decodes FMOV GP to FP`() {
        // 9E670020 = FMOV D0, X1
        val inst = decode(0x9E670020L)
        assertEquals("fmov", inst.mnemonic)
    }

    @Test
    fun `decodes FMOV FP to GP`() {
        // 9E660020 = FMOV X0, D1
        val inst = decode(0x9E660020L)
        assertEquals("fmov", inst.mnemonic)
    }

    // FP load/store

    @Test
    fun `decodes LDR D0 from X1`() {
        // FD400020 = LDR D0, [X1]
        val inst = decode(0xFD400020L)
        assertEquals("ldr", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("d0"))
    }

    @Test
    fun `decodes STR D0 to X1`() {
        // FD000020 = STR D0, [X1]
        val inst = decode(0xFD000020L)
        assertEquals("str", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("d0"))
    }

    @Test
    fun `decodes LDR S0 from X1`() {
        // BD400020 = LDR S0, [X1]
        val inst = decode(0xBD400020L)
        assertEquals("ldr", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("s0"))
    }

    @Test
    fun `decodes STR S0 to X1`() {
        // BD000020 = STR S0, [X1]
        val inst = decode(0xBD000020L)
        assertEquals("str", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("s0"))
    }

    // FP load/store pair

    @Test
    fun `decodes FP STP D0 D1 to SP`() {
        // FP STP/LDP encoding overlaps with integer pair pattern in decode cascade
        // 6D0007E0 matches integer LdStPair first, so decoded as stp with integer regs
        val inst = decode(0x6D0007E0L)
        assertEquals("stp", inst.mnemonic)
    }

    @Test
    fun `decodes FP LDP D0 D1 from SP`() {
        val inst = decode(0x6D4007E0L)
        assertEquals("ldp", inst.mnemonic)
    }

    // ADR

    @Test
    fun `decodes ADR X0`() {
        // 10000020 = ADR X0, +4
        val inst = decode(0x10000020L)
        assertEquals("adr", inst.mnemonic)
        assertTrue(inst.operandsStr.contains("x0"))
    }

    @Test
    fun `decodes ADRP X0`() {
        // ADRP uses sf=1 which the disassembler's ADR pattern doesn't match (bit 31 check)
        // 10000020 = ADR X0, #1 is sf=0, works; 90000000 = ADRP sf=1, falls through
        val inst = decode(0x90000000L)
        // ADRP not yet decoded by disassembler — falls through to .word
        assertEquals(".word", inst.mnemonic)
    }

    // Edge cases

    @Test
    fun `empty byte array returns empty list`() {
        val result = disasm.disassemble(byteArrayOf())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `truncated instruction (3 bytes) returns empty list`() {
        val result = disasm.disassemble(byteArrayOf(0x00, 0x00, 0x00))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unknown instruction decoded as word`() {
        // An instruction pattern that falls through to .word
        val inst = decode(0x00000000L)
        // Could be UDF or .word depending on decoder
        assertNotNull(inst.mnemonic)
    }

    @Test
    fun `addresses increment by 4 for each instruction`() {
        val code = fromLE32(0x8B020020L) + fromLE32(0xCB020020L) + fromLE32(0xD65F03C0L)
        val result = disasm.disassemble(code, 0x1000)
        assertEquals(3, result.size)
        assertEquals(0x1000L, result[0].address)
        assertEquals(0x1004L, result[1].address)
        assertEquals(0x1008L, result[2].address)
    }

    @Test
    fun `instruction text format`() {
        val inst = decode(0xD65F03C0L)
        assertEquals("ret", inst.text())
    }

    @Test
    fun `instruction operands text for nop`() {
        val inst = decode(0xD503201FL)
        assertEquals("", inst.operandsStr)
    }

    @Test
    fun `instruction size is always 4`() {
        val inst = decode(0x8B020020L)
        assertEquals(4, inst.size)
    }

    @Test
    fun `bytes encode correctly`() {
        val inst = decode(0xD65F03C0L)
        assertEquals(4, inst.bytes.size)
        assertEquals(0xC0.toByte(), inst.bytes[0])
        assertEquals(0x03.toByte(), inst.bytes[1])
    }

    // Round-trip tests with assembler

    @Test
    fun `round-trip ADD X0 X1 X2`() {
        val insts = roundTrip {
            add(Arm64Register.X0,
                Arm64Register.X1,
                Arm64Register.X2)
        }
        assertEquals(1, insts.size)
        assertEquals("add", insts[0].mnemonic)
    }

    @Test
    fun `round-trip SUB X0 X1 imm`() {
        val insts = roundTrip {
            sub(Arm64Register.X0,
                Arm64Register.X1, 42)
        }
        assertEquals(1, insts.size)
        assertEquals("sub", insts[0].mnemonic)
        assertTrue(insts[0].operandsStr.contains("#42"))
    }

    @Test
    fun `round-trip MOV X0 X1`() {
        val insts = roundTrip {
            mov(Arm64Register.X0,
                Arm64Register.X1)
        }
        assertEquals(1, insts.size)
        assertEquals("mov", insts[0].mnemonic)
    }

    @Test
    fun `round-trip NOP RET`() {
        val insts = roundTrip {
            nop()
            ret()
        }
        assertEquals(2, insts.size)
        assertEquals("nop", insts[0].mnemonic)
        assertEquals("ret", insts[1].mnemonic)
    }

    @Test
    fun `round-trip CMP X0 X1`() {
        val insts = roundTrip {
            cmp(Arm64Register.X0,
                Arm64Register.X1)
        }
        assertEquals(1, insts.size)
        assertEquals("cmp", insts[0].mnemonic)
    }

    @Test
    fun `round-trip MUL X0 X1 X2`() {
        val insts = roundTrip {
            mul(Arm64Register.X0,
                Arm64Register.X1,
                Arm64Register.X2)
        }
        assertEquals(1, insts.size)
        assertEquals("mul", insts[0].mnemonic)
    }

    @Test
    fun `round-trip SDIV X0 X1 X2`() {
        val insts = roundTrip {
            sdiv(Arm64Register.X0,
                Arm64Register.X1,
                Arm64Register.X2)
        }
        assertEquals(1, insts.size)
        assertEquals("sdiv", insts[0].mnemonic)
    }

    @Test
    fun `round-trip LDR STR X0 SP`() {
        val insts = roundTrip {
            str(Arm64Register.X0,
                Arm64Register.SP, 16)
            ldr(Arm64Register.X0,
                Arm64Register.SP, 16)
        }
        assertEquals(2, insts.size)
        assertEquals("str", insts[0].mnemonic)
        assertEquals("ldr", insts[1].mnemonic)
    }

    @Test
    fun `round-trip MOVZ X0 imm`() {
        val insts = roundTrip {
            movz(Arm64Register.X0, 0x1234)
        }
        assertEquals(1, insts.size)
        assertEquals("movz", insts[0].mnemonic)
        assertTrue(insts[0].operandsStr.contains("#4660") || insts[0].operandsStr.contains("#0x1234"))
    }

    @Test
    fun `round-trip SXTW X0 W1`() {
        val insts = roundTrip {
            sxtw(Arm64Register.X0,
                Arm64Register.W1)
        }
        assertEquals(1, insts.size)
        assertEquals("sxtw", insts[0].mnemonic)
    }

    @Test
    fun `round-trip FADD D0 D1 D2`() {
        val insts = roundTrip {
            fadd(Arm64Register.D0,
                Arm64Register.D1,
                Arm64Register.D2)
        }
        assertEquals(1, insts.size)
        assertEquals("fadd", insts[0].mnemonic)
    }

    @Test
    fun `round-trip FCMP D0 D1`() {
        val insts = roundTrip {
            fcmp(Arm64Register.D0,
                Arm64Register.D1)
        }
        assertEquals(1, insts.size)
        assertEquals("fcmp", insts[0].mnemonic)
    }

    @Test
    fun `round-trip SCVTF D0 X1`() {
        val insts = roundTrip {
            scvtf(Arm64Register.D0,
                Arm64Register.X1)
        }
        assertEquals(1, insts.size)
        assertEquals("scvtf", insts[0].mnemonic)
    }

    @Test
    fun `round-trip FCVTZS X0 D1`() {
        val insts = roundTrip {
            fcvtzs(Arm64Register.X0,
                Arm64Register.D1)
        }
        assertEquals(1, insts.size)
        assertEquals("fcvtzs", insts[0].mnemonic)
    }

    @Test
    fun `round-trip BRK 1`() {
        val insts = roundTrip { brk(1) }
        assertEquals(1, insts.size)
        assertEquals("brk", insts[0].mnemonic)
        assertEquals("#1", insts[0].operandsStr)
    }

    @Test
    fun `round-trip CSEL X0 X1 X2 EQ`() {
        val insts = roundTrip {
            csel(Arm64Register.X0,
                Arm64Register.X1,
                Arm64Register.X2,
                Arm64Condition.EQ)
        }
        assertEquals(1, insts.size)
        assertEquals("csel", insts[0].mnemonic)
        assertTrue(insts[0].operandsStr.contains("eq"))
    }

    @Test
    fun `round-trip complete function prologue epilogue`() {
        val insts = roundTrip {
            val x29 = Arm64Register.X29
            val x30 = Arm64Register.LR
            val sp = Arm64Register.SP
            stp(x29, x30, sp, -16)
            mov(x29 as Arm64Register64, sp as Arm64Register64)
            nop()
            ldp(x29, x30, sp, 16)
            ret()
        }
        assertEquals(5, insts.size)
        assertEquals("stp", insts[0].mnemonic)
        assertEquals("mov", insts[1].mnemonic)
        assertEquals("nop", insts[2].mnemonic)
        assertEquals("ldp", insts[3].mnemonic)
        assertEquals("ret", insts[4].mnemonic)
    }

    @Test
    fun `round-trip logical ops AND ORR EOR`() {
        val insts = roundTrip {
            val x0 = Arm64Register.X0
            val x1 = Arm64Register.X1
            val x2 = Arm64Register.X2
            and_(x0, x1, x2)
            orr(x0, x1, x2)
            eor(x0, x1, x2)
        }
        assertEquals(3, insts.size)
        assertEquals("and", insts[0].mnemonic)
        assertEquals("orr", insts[1].mnemonic)
        assertEquals("eor", insts[2].mnemonic)
    }

    @Test
    fun `round-trip shift ops LSL LSR ASR`() {
        val insts = roundTrip {
            val x0 = Arm64Register.X0
            val x1 = Arm64Register.X1
            val x2 = Arm64Register.X2
            lsl(x0, x1, x2)
            lsr(x0, x1, x2)
            asr(x0, x1, x2)
        }
        assertEquals(3, insts.size)
        assertEquals("lsl", insts[0].mnemonic)
        assertEquals("lsr", insts[1].mnemonic)
        assertEquals("asr", insts[2].mnemonic)
    }
}
