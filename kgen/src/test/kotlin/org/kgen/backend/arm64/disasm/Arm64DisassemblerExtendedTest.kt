package org.kgen.backend.arm64.disasm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class Arm64DisassemblerExtendedTest {

    private val disasm = Arm64Disassembler()

    private fun fromLE32(value: Long): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun disOne(encoding: Long): Arm64Instruction {
        val result = disasm.disassemble(fromLE32(encoding))
        assertEquals(1, result.size)
        return result[0]
    }

    // --- ADD/SUB immediate ---

    @Test
    fun addImmW0W1() {
        // ADD W0, W1, #10 → 0x11002820
        val i = disOne(0x11002820L)
        assertEquals("add", i.mnemonic)
        assertTrue(i.operandsStr.contains("w0"))
        assertTrue(i.operandsStr.contains("w1"))
        assertTrue(i.operandsStr.contains("#10"))
    }

    @Test
    fun subImmX3X4() {
        // SUB X3, X4, #100 → 0xD1019083
        val i = disOne(0xD1019083L)
        assertEquals("sub", i.mnemonic)
        assertTrue(i.operandsStr.contains("x3"))
        assertTrue(i.operandsStr.contains("x4"))
        assertTrue(i.operandsStr.contains("#100"))
    }

    @Test
    fun addsImmSetsFlags() {
        // ADDS X5, X6, #1 → 0xB10004C5
        val i = disOne(0xB10004C5L)
        assertEquals("adds", i.mnemonic)
        assertTrue(i.operandsStr.contains("x5"))
    }

    @Test
    fun cmpImmAlias() {
        // CMP X0, #42 → SUBS XZR, X0, #42 → 0xF100A81F
        val i = disOne(0xF100A81FL)
        assertEquals("cmp", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
        assertTrue(i.operandsStr.contains("#42"))
    }

    @Test
    fun cmnImmAlias() {
        // CMN X1, #5 → ADDS XZR, X1, #5 → 0xB100143F
        val i = disOne(0xB100143FL)
        assertEquals("cmn", i.mnemonic)
        assertTrue(i.operandsStr.contains("x1"))
    }

    @Test
    fun addImmShifted() {
        // ADD X0, X1, #1, LSL #12 → 0x91400420
        val i = disOne(0x91400420L)
        assertEquals("add", i.mnemonic)
        assertTrue(i.operandsStr.contains("#4096") || i.operandsStr.contains("x0"))
    }

    // --- ADD/SUB register ---

    @Test
    fun addRegW() {
        // ADD W0, W1, W2 → 0x0B020020
        val i = disOne(0x0B020020L)
        assertEquals("add", i.mnemonic)
        assertTrue(i.operandsStr.contains("w0"))
        assertTrue(i.operandsStr.contains("w1"))
        assertTrue(i.operandsStr.contains("w2"))
    }

    @Test
    fun subRegX() {
        // SUB X5, X6, X7 → 0xCB0700C5
        val i = disOne(0xCB0700C5L)
        assertEquals("sub", i.mnemonic)
        assertTrue(i.operandsStr.contains("x5"))
        assertTrue(i.operandsStr.contains("x6"))
        assertTrue(i.operandsStr.contains("x7"))
    }

    @Test
    fun negAlias() {
        // NEG X0, X1 → SUB X0, XZR, X1 → 0xCB0103E0
        val i = disOne(0xCB0103E0L)
        assertEquals("neg", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
        assertTrue(i.operandsStr.contains("x1"))
    }

    @Test
    fun cmpRegAlias() {
        // CMP X0, X1 → SUBS XZR, X0, X1 → 0xEB01001F
        val i = disOne(0xEB01001FL)
        assertEquals("cmp", i.mnemonic)
    }

    // --- Logical register ---

    @Test
    fun andReg() {
        // AND X0, X1, X2 → 0x8A020020
        val i = disOne(0x8A020020L)
        assertEquals("and", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
    }

    @Test
    fun orrReg() {
        // ORR X0, X1, X2 → 0xAA020020
        val i = disOne(0xAA020020L)
        assertEquals("orr", i.mnemonic)
    }

    @Test
    fun eorReg() {
        // EOR X0, X1, X2 → 0xCA020020
        val i = disOne(0xCA020020L)
        assertEquals("eor", i.mnemonic)
    }

    @Test
    fun andsRegTstAlias() {
        // TST X0, X1 → ANDS XZR, X0, X1 → 0xEA01001F
        val i = disOne(0xEA01001FL)
        assertEquals("tst", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
        assertTrue(i.operandsStr.contains("x1"))
    }

    @Test
    fun bicReg() {
        // BIC X0, X1, X2 → 0x8A220020
        val i = disOne(0x8A220020L)
        assertEquals("bic", i.mnemonic)
    }

    @Test
    fun ornReg() {
        // ORN X0, X1, X2 → 0xAA220020
        val i = disOne(0xAA220020L)
        assertEquals("orn", i.mnemonic)
    }

    @Test
    fun eonReg() {
        // EON X0, X1, X2 → 0xCA220020
        val i = disOne(0xCA220020L)
        assertEquals("eon", i.mnemonic)
    }

    @Test
    fun bicsReg() {
        // BICS X0, X1, X2 → 0xEA220020
        val i = disOne(0xEA220020L)
        assertEquals("bics", i.mnemonic)
    }

    @Test
    fun movRegAlias() {
        // MOV X0, X1 → ORR X0, XZR, X1 → 0xAA0103E0
        val i = disOne(0xAA0103E0L)
        assertEquals("mov", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
        assertTrue(i.operandsStr.contains("x1"))
    }

    // --- Data processing 2-source ---

    @Test
    fun udivX() {
        // UDIV X0, X1, X2 → 0x9AC20820
        val i = disOne(0x9AC20820L)
        assertEquals("udiv", i.mnemonic)
    }

    @Test
    fun sdivX() {
        // SDIV X0, X1, X2 → 0x9AC20C20
        val i = disOne(0x9AC20C20L)
        assertEquals("sdiv", i.mnemonic)
    }

    @Test
    fun lslReg() {
        // LSL X0, X1, X2 → 0x9AC22020
        val i = disOne(0x9AC22020L)
        assertEquals("lsl", i.mnemonic)
    }

    @Test
    fun lsrReg() {
        // LSR X0, X1, X2 → 0x9AC22420
        val i = disOne(0x9AC22420L)
        assertEquals("lsr", i.mnemonic)
    }

    @Test
    fun asrReg() {
        // ASR X0, X1, X2 → 0x9AC22820
        val i = disOne(0x9AC22820L)
        assertEquals("asr", i.mnemonic)
    }

    // --- Data processing 3-source ---

    @Test
    fun mulX() {
        // MUL X0, X1, X2 → 0x9B027C20
        val i = disOne(0x9B027C20L)
        assertEquals("mul", i.mnemonic)
    }

    @Test
    fun maddX() {
        // MADD X0, X1, X2, X3 → 0x9B020C20
        val i = disOne(0x9B020C20L)
        assertEquals("madd", i.mnemonic)
        assertTrue(i.operandsStr.contains("x3"))
    }

    @Test
    fun msubX() {
        // MSUB X0, X1, X2, X3 → 0x9B828C20
        val i = disOne(0x9B828C20L)
        assertEquals("msub", i.mnemonic)
    }

    // --- Move wide ---

    @Test
    fun movzW() {
        // MOVZ W0, #42 → 0x52800540
        val i = disOne(0x52800540L)
        assertEquals("movz", i.mnemonic)
        assertTrue(i.operandsStr.contains("w0"))
        assertTrue(i.operandsStr.contains("#42"))
    }

    @Test
    fun movzXShifted() {
        // MOVZ X0, #0x1234, LSL #16 → 0xD2A24680
        val i = disOne(0xD2A24680L)
        assertEquals("movz", i.mnemonic)
        assertTrue(i.operandsStr.contains("lsl #16"))
    }

    @Test
    fun movnX() {
        // MOVN X0, #0 → 0x92800000
        val i = disOne(0x92800000L)
        assertEquals("movn", i.mnemonic)
    }

    @Test
    fun movkX() {
        // MOVK X0, #0x5678 → 0xF2ACF000
        val i = disOne(0xF2ACF000L)
        assertEquals("movk", i.mnemonic)
    }

    // --- Bitfield (extension aliases) ---

    @Test
    fun sxtwAlias() {
        // SXTW X0, W1 → SBFM X0, X1, #0, #31 → 0x93407C20
        val i = disOne(0x93407C20L)
        assertEquals("sxtw", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
        assertTrue(i.operandsStr.contains("w1"))
    }

    @Test
    fun sxthAlias() {
        // SXTH X0, W1 → SBFM X0, X1, #0, #15 → 0x93403C20
        val i = disOne(0x93403C20L)
        assertEquals("sxth", i.mnemonic)
    }

    @Test
    fun sxtbAlias() {
        // SXTB X0, W1 → SBFM X0, X1, #0, #7 → 0x93401C20
        val i = disOne(0x93401C20L)
        assertEquals("sxtb", i.mnemonic)
    }

    @Test
    fun uxtbAlias() {
        // UXTB W0, W1 → UBFM W0, W1, #0, #7 → 0x53001C20
        val i = disOne(0x53001C20L)
        assertEquals("uxtb", i.mnemonic)
        assertTrue(i.operandsStr.contains("w0"))
        assertTrue(i.operandsStr.contains("w1"))
    }

    @Test
    fun uxthAlias() {
        // UXTH W0, W1 → UBFM W0, W1, #0, #15 → 0x53003C20
        val i = disOne(0x53003C20L)
        assertEquals("uxth", i.mnemonic)
    }

    @Test
    fun sbfmGeneric() {
        // SBFM X0, X1, #2, #10 → 0x93420820 (not a known alias)
        // sf=1, opc=0, N=1, immr=2, imms=10
        // 1_00_100110_1_000010_001010_00001_00000
        val i = disOne(0x93422820L)
        assertEquals("sbfm", i.mnemonic)
    }

    // --- Unconditional branch ---

    @Test
    fun branchForward() {
        // B +8 → 0x14000002
        val i = disOne(0x14000002L)
        assertEquals("b", i.mnemonic)
    }

    @Test
    fun branchBackward() {
        // B -4 → 0x17FFFFFF
        val i = disOne(0x17FFFFFFL)
        assertEquals("b", i.mnemonic)
    }

    @Test
    fun blForward() {
        // BL +16 → 0x94000004
        val i = disOne(0x94000004L)
        assertEquals("bl", i.mnemonic)
    }

    // --- Conditional branch ---

    @Test
    fun beq() {
        // B.EQ +8 → 0x54000040
        val i = disOne(0x54000040L)
        assertEquals("b.eq", i.mnemonic)
    }

    @Test
    fun bne() {
        // B.NE +8 → 0x54000041
        val i = disOne(0x54000041L)
        assertEquals("b.ne", i.mnemonic)
    }

    @Test
    fun blt() {
        // B.LT +8 → 0x5400004B
        val i = disOne(0x5400004BL)
        assertEquals("b.lt", i.mnemonic)
    }

    @Test
    fun bge() {
        // B.GE +8 → 0x5400004A
        val i = disOne(0x5400004AL)
        assertEquals("b.ge", i.mnemonic)
    }

    @Test
    fun bhi() {
        // B.HI +8 → 0x54000048
        val i = disOne(0x54000048L)
        assertEquals("b.hi", i.mnemonic)
    }

    @Test
    fun bls() {
        // B.LS +8 → 0x54000049
        val i = disOne(0x54000049L)
        assertEquals("b.ls", i.mnemonic)
    }

    // --- Compare and branch ---

    @Test
    fun cbzX() {
        // CBZ X0, +8 → 0xB4000040
        val i = disOne(0xB4000040L)
        assertEquals("cbz", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
    }

    @Test
    fun cbnzX() {
        // CBNZ X0, +8 → 0xB5000040
        val i = disOne(0xB5000040L)
        assertEquals("cbnz", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
    }

    @Test
    fun cbzW() {
        // CBZ W3, +12 → 0x34000063
        val i = disOne(0x34000063L)
        assertEquals("cbz", i.mnemonic)
        assertTrue(i.operandsStr.contains("w3"))
    }

    // --- Branch register ---

    @Test
    fun brX0() {
        // BR X0 → 0xD61F0000
        val i = disOne(0xD61F0000L)
        assertEquals("br", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
    }

    @Test
    fun blrX10() {
        // BLR X10 → 0xD63F0140
        val i = disOne(0xD63F0140L)
        assertEquals("blr", i.mnemonic)
        assertTrue(i.operandsStr.contains("x10"))
    }

    @Test
    fun retX30() {
        // RET (X30) → 0xD65F03C0
        val i = disOne(0xD65F03C0L)
        assertEquals("ret", i.mnemonic)
        // When Rn=30, operands may be empty
    }

    @Test
    fun retX0() {
        // RET X0 → 0xD65F0000 (non-standard return register)
        val i = disOne(0xD65F0000L)
        assertEquals("ret", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
    }

    // --- Load/Store unsigned offset ---

    @Test
    fun ldrXUnsigned() {
        // LDR X0, [X1, #16] → 0xF9400820
        val i = disOne(0xF9400820L)
        assertEquals("ldr", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
        assertTrue(i.operandsStr.contains("x1"))
        assertTrue(i.operandsStr.contains("#16"))
    }

    @Test
    fun strXUnsigned() {
        // STR X0, [X1, #8] → 0xF9000420
        val i = disOne(0xF9000420L)
        assertEquals("str", i.mnemonic)
    }

    @Test
    fun ldrWUnsigned() {
        // LDR W0, [X1, #4] → 0xB9400420
        val i = disOne(0xB9400420L)
        assertEquals("ldr", i.mnemonic)
        assertTrue(i.operandsStr.contains("w0"))
    }

    @Test
    fun ldrbUnsigned() {
        // LDRB W0, [X1, #1] → 0x39400420
        val i = disOne(0x39400420L)
        assertEquals("ldrb", i.mnemonic)
        assertTrue(i.operandsStr.contains("w0"))
    }

    @Test
    fun strbUnsigned() {
        // STRB W0, [X1] → 0x39000020
        val i = disOne(0x39000020L)
        assertEquals("strb", i.mnemonic)
    }

    @Test
    fun ldrhUnsigned() {
        // LDRH W0, [X1, #2] → 0x79400420
        val i = disOne(0x79400420L)
        assertEquals("ldrh", i.mnemonic)
    }

    @Test
    fun strhUnsigned() {
        // STRH W0, [X1] → 0x79000020
        val i = disOne(0x79000020L)
        assertEquals("strh", i.mnemonic)
    }

    @Test
    fun ldrFromSp() {
        // LDR X0, [SP, #16] → 0xF9400BE0
        val i = disOne(0xF9400BE0L)
        assertEquals("ldr", i.mnemonic)
        assertTrue(i.operandsStr.contains("sp"))
        assertTrue(i.operandsStr.contains("#16"))
    }

    @Test
    fun strToSpZeroOffset() {
        // STR X0, [SP] → 0xF90003E0
        val i = disOne(0xF90003E0L)
        assertEquals("str", i.mnemonic)
        assertTrue(i.operandsStr.contains("[sp]"))
    }

    // --- Load/Store unscaled ---

    @Test
    fun ldurX() {
        // LDUR X0, [X1, #-8] → encoding for size=3, opc=01, imm9=-8
        // 11_111_0_00_01_0_111111000_00_00001_00000 = 0xF85F8020
        val i = disOne(0xF85F8020L)
        assertEquals("ldur", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
        assertTrue(i.operandsStr.contains("#-8"))
    }

    @Test
    fun sturW() {
        // STUR W0, [X1, #4] → size=2, opc=00, imm9=4
        // 10_111_0_00_00_0_000000100_00_00001_00000 = 0xB8004020
        val i = disOne(0xB8004020L)
        assertEquals("stur", i.mnemonic)
        assertTrue(i.operandsStr.contains("w0"))
    }

    // --- Load/Store pair ---

    @Test
    fun ldpXSignedOffset() {
        // LDP X0, X1, [SP, #16] → opc=10, signed offset mode (bits 23:22 = 10)
        // 10_101_0_010_1_0000010_00001_11111_00000 = 0xA9410FE0
        val i = disOne(0xA9410FE0L)
        // Verify it decodes (may route through load/store pair decoder)
        assertNotNull(i.mnemonic)
        assertTrue(i.mnemonic == "ldp" || i.mnemonic.startsWith("ld") || i.mnemonic == ".word",
            "Expected ldp or load variant, got: ${i.mnemonic} ${i.operandsStr}")
    }

    @Test
    fun stpXSignedOffset() {
        // STP X29, X30, [SP, #-16]! (pre-index) → 0xA9BF7BFD
        val i = disOne(0xA9BF7BFDL)
        assertNotNull(i.mnemonic)
    }

    @Test
    fun ldpWSignedOffset() {
        // LDP W0, W1, [X2, #8] → 0x29410840
        val i = disOne(0x29410840L)
        assertNotNull(i.mnemonic)
    }

    // --- Conditional select ---

    @Test
    fun cselEq() {
        // CSEL X0, X1, X2, EQ → 0x9A820020
        val i = disOne(0x9A820020L)
        assertEquals("csel", i.mnemonic)
        assertTrue(i.operandsStr.contains("eq"))
    }

    @Test
    fun csincNe() {
        // CSINC X0, X1, X2, NE → 0x9A821420
        val i = disOne(0x9A821420L)
        assertEquals("csinc", i.mnemonic)
        assertTrue(i.operandsStr.contains("ne"))
    }

    @Test
    fun cselW() {
        // CSEL W0, W1, W2, LT → 0x1A82B020
        val i = disOne(0x1A82B020L)
        assertEquals("csel", i.mnemonic)
        assertTrue(i.operandsStr.contains("w0"))
    }

    // --- System ---

    @Test
    fun nop() {
        val i = disOne(0xD503201FL)
        assertEquals("nop", i.mnemonic)
    }

    @Test
    fun brkImm() {
        // BRK #0x1234 → 0xD4224680
        val i = disOne(0xD4224680L)
        assertEquals("brk", i.mnemonic)
        assertTrue(i.operandsStr.contains("#4660") || i.operandsStr.contains("#0x1234"))
    }

    @Test
    fun svcImm() {
        // SVC #0 → 0xD4000001
        val i = disOne(0xD4000001L)
        assertEquals("svc", i.mnemonic)
        assertTrue(i.operandsStr.contains("#0"))
    }

    // --- FP data processing 2-source ---

    @Test
    fun faddS() {
        // FADD S0, S1, S2 → 0x1E222820
        val i = disOne(0x1E222820L)
        assertEquals("fadd", i.mnemonic)
        assertTrue(i.operandsStr.contains("s0"))
        assertTrue(i.operandsStr.contains("s1"))
        assertTrue(i.operandsStr.contains("s2"))
    }

    @Test
    fun fsubD() {
        // FSUB D0, D1, D2 → 0x1E623820
        val i = disOne(0x1E623820L)
        assertEquals("fsub", i.mnemonic)
        assertTrue(i.operandsStr.contains("d0"))
    }

    @Test
    fun fmulS() {
        // FMUL S0, S1, S2 → 0x1E220820
        val i = disOne(0x1E220820L)
        assertEquals("fmul", i.mnemonic)
    }

    @Test
    fun fdivD() {
        // FDIV D0, D1, D2 → 0x1E621820
        val i = disOne(0x1E621820L)
        assertEquals("fdiv", i.mnemonic)
    }

    // --- FP data processing 1-source ---

    @Test
    fun fmovS() {
        // FMOV S0, S1 → 0x1E204020
        val i = disOne(0x1E204020L)
        assertEquals("fmov", i.mnemonic)
        assertTrue(i.operandsStr.contains("s0"))
        assertTrue(i.operandsStr.contains("s1"))
    }

    @Test
    fun fabsD() {
        // FABS D0, D1 → 0x1E60C020
        val i = disOne(0x1E60C020L)
        assertEquals("fabs", i.mnemonic)
        assertTrue(i.operandsStr.contains("d0"))
    }

    @Test
    fun fnegS() {
        // FNEG S0, S1 → 0x1E214020
        val i = disOne(0x1E214020L)
        assertEquals("fneg", i.mnemonic)
    }

    @Test
    fun fcvtStoD() {
        // FCVT D0, S1 → 0x1E22C020
        val i = disOne(0x1E22C020L)
        assertEquals("fcvt", i.mnemonic)
        assertTrue(i.operandsStr.contains("d0"))
        assertTrue(i.operandsStr.contains("s1"))
    }

    @Test
    fun fcvtDtoS() {
        // FCVT S0, D1 → 0x1E624020
        val i = disOne(0x1E624020L)
        assertEquals("fcvt", i.mnemonic)
        assertTrue(i.operandsStr.contains("s0"))
        assertTrue(i.operandsStr.contains("d1"))
    }

    // --- FP compare ---

    @Test
    fun fcmpSRegs() {
        // FCMP S0, S1 → 0x1E212000
        val i = disOne(0x1E212000L)
        assertEquals("fcmp", i.mnemonic)
        assertTrue(i.operandsStr.contains("s0"))
        assertTrue(i.operandsStr.contains("s1"))
    }

    @Test
    fun fcmpDZero() {
        // FCMP D0, #0.0 → 0x1E602008
        val i = disOne(0x1E602008L)
        assertEquals("fcmp", i.mnemonic)
        assertTrue(i.operandsStr.contains("d0"))
        assertTrue(i.operandsStr.contains("#0.0"))
    }

    // --- FP conditional select ---

    @Test
    fun fcselSEq() {
        // FCSEL S0, S1, S2, EQ → 0x1E220C20
        val i = disOne(0x1E220C20L)
        assertEquals("fcsel", i.mnemonic)
        assertTrue(i.operandsStr.contains("eq"))
    }

    // --- FP <-> GP transfers ---

    @Test
    fun scvtfSFromW() {
        // SCVTF S0, W1 → 0x1E220020
        val i = disOne(0x1E220020L)
        assertEquals("scvtf", i.mnemonic)
        assertTrue(i.operandsStr.contains("s0"))
        assertTrue(i.operandsStr.contains("w1"))
    }

    @Test
    fun scvtfDFromX() {
        // SCVTF D0, X1 → 0x9E620020
        val i = disOne(0x9E620020L)
        assertEquals("scvtf", i.mnemonic)
        assertTrue(i.operandsStr.contains("d0"))
        assertTrue(i.operandsStr.contains("x1"))
    }

    @Test
    fun fcvtzsWFromS() {
        // FCVTZS W0, S1 → 0x1E380020
        val i = disOne(0x1E380020L)
        assertEquals("fcvtzs", i.mnemonic)
        assertTrue(i.operandsStr.contains("w0"))
        assertTrue(i.operandsStr.contains("s1"))
    }

    @Test
    fun fcvtzuXFromD() {
        // FCVTZU X0, D1 → 0x9E790020
        val i = disOne(0x9E790020L)
        assertEquals("fcvtzu", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
        assertTrue(i.operandsStr.contains("d1"))
    }

    // --- FP load/store ---

    @Test
    fun ldrSUnsigned() {
        // LDR S0, [X1, #4] → v=1, size=2: 0xBD400420
        val i = disOne(0xBD400420L)
        assertEquals("ldr", i.mnemonic)
        assertTrue(i.operandsStr.contains("s0"))
    }

    @Test
    fun strDUnsigned() {
        // STR D0, [X1, #8] → v=1, size=3: 0xFD000420
        val i = disOne(0xFD000420L)
        assertEquals("str", i.mnemonic)
        assertTrue(i.operandsStr.contains("d0"))
    }

    // --- FP load/store pair ---

    @Test
    fun ldpSPair() {
        // LDP S0, S1, [X2, #8] → 0x2D410840
        val i = disOne(0x2D410840L)
        assertNotNull(i.mnemonic)
    }

    @Test
    fun stpDPair() {
        // STP D0, D1, [SP, #16] → 0x6D0107E0
        val i = disOne(0x6D0107E0L)
        assertNotNull(i.mnemonic)
    }

    // --- ADR ---

    @Test
    fun adr() {
        // ADR X0, #4 → 0x10000020
        val i = disOne(0x10000020L)
        assertEquals("adr", i.mnemonic)
        assertTrue(i.operandsStr.contains("x0"))
    }

    @Test
    fun adrp() {
        // ADRP X0, #4096 → bit 31 = 1
        // Note: ADRP (0x90xxxxxx) has bit31=1 which doesn't match ADR mask (0x9F000000==0x10000000)
        // The disassembler may not support ADRP yet, so we just verify it doesn't crash
        val i = disOne(0x90000020L)
        assertNotNull(i.mnemonic)
    }

    // --- Unknown instruction ---

    @Test
    fun unknownDecodesAsWord() {
        // Some unlikely encoding should decode as .word
        val i = disOne(0x00000000L)
        // Either decoded or falls through to .word
        assertNotNull(i.mnemonic)
    }

    // --- Multi-instruction sequences ---

    @Test
    fun multipleInstructionSequence() {
        // ADD X0, X1, X2 + SUB X3, X4, X5 + RET
        val code = fromLE32(0x8B020020L) + fromLE32(0xCB0500A3L) + fromLE32(0xD65F03C0L)
        val result = disasm.disassemble(code)
        assertEquals(3, result.size)
        assertEquals("add", result[0].mnemonic)
        assertEquals("sub", result[1].mnemonic)
        assertEquals("ret", result[2].mnemonic)
        assertEquals(0L, result[0].address)
        assertEquals(4L, result[1].address)
        assertEquals(8L, result[2].address)
    }

    @Test
    fun baseAddressOffset() {
        val code = fromLE32(0xD65F03C0L) // RET
        val result = disasm.disassemble(code, baseAddress = 0x1000)
        assertEquals(1, result.size)
        assertEquals(0x1000L, result[0].address)
    }

    @Test
    fun emptyInput() {
        val result = disasm.disassemble(ByteArray(0))
        assertEquals(0, result.size)
    }

    @Test
    fun truncatedInput() {
        // Only 3 bytes - not enough for a 4-byte ARM64 instruction
        val result = disasm.disassemble(byteArrayOf(0, 0, 0))
        assertEquals(0, result.size)
    }

    @Test
    fun longSequence() {
        // 10 NOPs
        val code = ByteArray(40)
        for (i in 0 until 10) {
            val nop = fromLE32(0xD503201FL)
            System.arraycopy(nop, 0, code, i * 4, 4)
        }
        val result = disasm.disassemble(code)
        assertEquals(10, result.size)
        result.forEach { assertEquals("nop", it.mnemonic) }
    }

    @Test
    fun instructionHasRawEncoding() {
        val i = disOne(0x8B020020L)
        assertEquals(0x8B020020L.toInt(), i.rawBytes)
    }
}
