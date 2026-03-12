package org.kgen.target.arm64.asm

import org.kgen.target.arm64.*
import org.kgen.target.arm64.disasm.Arm64Disassembler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class Arm64AssemblerTest {

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

    @Test
    fun `ADD X0 X1 X2 encodes correctly`() {
        val bytes = assemble { add(X0, X1, X2) }
        assertEquals(4, bytes.size)
        // ADD X0, X1, X2: sf=1, op=0, S=0, 0b01011, shift=00, Rm=2, imm6=0, Rn=1, Rd=0
        // = 0x8B020020
        assertEquals(0x8B020020L, readLE32(bytes))
    }

    @Test
    fun `ADD W0 W1 W2 encodes correctly`() {
        val bytes = assemble { add(W0, W1, W2) }
        assertEquals(0x0B020020L, readLE32(bytes))
    }

    @Test
    fun `ADD X0 X1 imm encodes correctly`() {
        val bytes = assemble { add(X0, X1, 42) }
        // ADD X0, X1, #42: 0x91000000 | (42 << 10) | (1 << 5) | 0 = 0x9100A820
        assertEquals(0x9100A820L, readLE32(bytes))
    }

    @Test
    fun `SUB X0 X1 X2 encodes correctly`() {
        val bytes = assemble { sub(X0, X1, X2) }
        assertEquals(0xCB020020L, readLE32(bytes))
    }

    @Test
    fun `SUB X0 SP imm encodes correctly`() {
        val bytes = assemble { sub(X0, SP, 16) }
        // SUB X0, SP, #16: 0xD1000000 | (16 << 10) | (31 << 5) | 0 = 0xD10043E0
        assertEquals(0xD10043E0L, readLE32(bytes))
    }

    @Test
    fun `AND X0 X1 X2 encodes correctly`() {
        val bytes = assemble { and_(X0, X1, X2) }
        assertEquals(0x8A020020L, readLE32(bytes))
    }

    @Test
    fun `ORR X0 X1 X2 encodes correctly`() {
        val bytes = assemble { orr(X0, X1, X2) }
        assertEquals(0xAA020020L, readLE32(bytes))
    }

    @Test
    fun `EOR X0 X1 X2 encodes correctly`() {
        val bytes = assemble { eor(X0, X1, X2) }
        assertEquals(0xCA020020L, readLE32(bytes))
    }

    @Test
    fun `MOV X0 X1 encodes as ORR X0 XZR X1`() {
        val bytes = assemble { mov(X0, X1) }
        // ORR X0, XZR, X1: 0xAA000000 | (1 << 16) | (31 << 5) | 0 = 0xAA0103E0
        assertEquals(0xAA0103E0L, readLE32(bytes))
    }

    @Test
    fun `MOVZ X0 imm16 encodes correctly`() {
        val bytes = assemble { movz(X0, 0x1234) }
        // MOVZ X0, #0x1234: 0xD2800000 | (0x1234 << 5) | 0 = 0xD2824680
        assertEquals(0xD2824680L, readLE32(bytes))
    }

    @Test
    fun `MOVZ X0 imm16 with shift encodes correctly`() {
        val bytes = assemble { movz(X0, 0xABCD, shift = 16) }
        // hw=1, so 0xD2A00000 | (0xABCD << 5) | 0
        assertEquals(0xD2A00000L or (0xABCDL shl 5), readLE32(bytes))
    }

    @Test
    fun `MUL X0 X1 X2 encodes correctly`() {
        val bytes = assemble { mul(X0, X1, X2) }
        // MADD X0, X1, X2, XZR: 0x9B007C00 | (2 << 16) | (1 << 5) | 0 = 0x9B027C20
        assertEquals(0x9B027C20L, readLE32(bytes))
    }

    @Test
    fun `SDIV X0 X1 X2 encodes correctly`() {
        val bytes = assemble { sdiv(X0, X1, X2) }
        assertEquals(0x9AC20C20L, readLE32(bytes))
    }

    @Test
    fun `LSL X0 X1 X2 encodes correctly`() {
        val bytes = assemble { lsl(X0, X1, X2) }
        assertEquals(0x9AC22020L, readLE32(bytes))
    }

    @Test
    fun `CMP X0 X1 encodes as SUBS XZR X0 X1`() {
        val bytes = assemble { cmp(X0, X1) }
        // SUBS XZR, X0, X1: 0xEB000000 | (1 << 16) | (0 << 5) | 31 = 0xEB01001F
        assertEquals(0xEB01001FL, readLE32(bytes))
    }

    @Test
    fun `CMP X0 imm encodes correctly`() {
        val bytes = assemble { cmp(X0, 10) }
        // SUBS XZR, X0, #10: 0xF1000000 | (10 << 10) | (0 << 5) | 31 = 0xF100281F
        assertEquals(0xF100281FL, readLE32(bytes))
    }

    @Test
    fun `RET encodes correctly`() {
        val bytes = assemble { ret() }
        // RET (X30): 0xD65F0000 | (30 << 5) = 0xD65F03C0
        assertEquals(0xD65F03C0L, readLE32(bytes))
    }

    @Test
    fun `NOP encodes correctly`() {
        val bytes = assemble { nop() }
        assertEquals(0xD503201FL, readLE32(bytes))
    }

    @Test
    fun `BRK encodes correctly`() {
        val bytes = assemble { brk(1) }
        assertEquals(0xD4200020L, readLE32(bytes))
    }

    @Test
    fun `SVC encodes correctly`() {
        val bytes = assemble { svc(0) }
        assertEquals(0xD4000001L, readLE32(bytes))
    }

    @Test
    fun `LDR X0 from SP offset`() {
        val bytes = assemble { ldr(X0, SP, 16) }
        // LDR X0, [SP, #16]: 0xF9400000 | ((16/8) << 10) | (31 << 5) | 0 = 0xF9400BE0
        assertEquals(0xF9400BE0L, readLE32(bytes))
    }

    @Test
    fun `STR X0 to SP offset`() {
        val bytes = assemble { str(X0, SP, 8) }
        // STR X0, [SP, #8]: 0xF9000000 | ((8/8) << 10) | (31 << 5) | 0 = 0xF90007E0
        assertEquals(0xF90007E0L, readLE32(bytes))
    }

    @Test
    fun `LDR W0 from X1 offset`() {
        val bytes = assemble { ldr(W0, X1, 8) }
        // LDR W0, [X1, #8]: 0xB9400000 | ((8/4) << 10) | (1 << 5) | 0 = 0xB9400820
        assertEquals(0xB9400820L, readLE32(bytes))
    }

    @Test
    fun `STP X29 X30 to SP pre-index`() {
        val bytes = assemble { stpPre(X29, X30, SP, -16) }
        // STP X29, X30, [SP, #-16]!
        // opc=10, 0b101, indexMode=11 (pre-index), imm7=(-16/8)&0x7F=0x7E, Rt2=30, Rn=31, Rt1=29
        // 0xA9800000 | (0x7E << 15) | (30 << 10) | (31 << 5) | 29
        val expected = 0xA9800000L or (0x7EL shl 15) or (30L shl 10) or (31L shl 5) or 29L
        assertEquals(expected, readLE32(bytes))
    }

    @Test
    fun `LDP X29 X30 from SP post-index`() {
        val bytes = assemble { ldpPost(X29, X30, SP, 16) }
        // LDP X29, X30, [SP], #16
        val expected = 0xA8C00000L or (0x02L shl 15) or (30L shl 10) or (31L shl 5) or 29L
        assertEquals(expected, readLE32(bytes))
    }

    @Test
    fun `CSEL X0 X1 X2 EQ encodes correctly`() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.EQ) }
        // CSEL X0, X1, X2, EQ: 0x9A800000 | (2 << 16) | (0 << 12) | (1 << 5) | 0 = 0x9A820020
        assertEquals(0x9A820020L, readLE32(bytes))
    }

    @Test
    fun `SXTW X0 W1 encodes correctly`() {
        val bytes = assemble { sxtw(X0, W1) }
        // SBFM X0, X1, #0, #31: 0x93407C00 | (1 << 5) | 0 = 0x93407C20
        assertEquals(0x93407C20L, readLE32(bytes))
    }

    @Test
    fun `B label resolves forward reference`() {
        val bytes = assemble {
            b("target")
            nop()
            label("target")
            ret()
        }
        assertEquals(12, bytes.size)
        // B should jump +2 instructions = imm26 of 2
        val bInst = readLE32(bytes, 0)
        assertEquals(0x14000000L or 2L, bInst)
    }

    @Test
    fun `BL label resolves forward reference`() {
        val bytes = assemble {
            bl("func")
            label("func")
            ret()
        }
        val blInst = readLE32(bytes, 0)
        assertEquals(0x94000000L or 1L, blInst)
    }

    @Test
    fun `BR X8 encodes correctly`() {
        val bytes = assemble { br(X8) }
        assertEquals(0xD61F0100L, readLE32(bytes))
    }

    @Test
    fun `BLR X8 encodes correctly`() {
        val bytes = assemble { blr(X8) }
        assertEquals(0xD63F0100L, readLE32(bytes))
    }

    @Test
    fun `LDUR X0 from X1 negative offset`() {
        val bytes = assemble { ldur(X0, X1, -8) }
        // LDUR X0, [X1, #-8]: 0xF8400000 | ((-8 & 0x1FF) << 12) | (1 << 5) | 0
        val imm9 = (-8) and 0x1FF
        val expected = 0xF8400000L or (imm9.toLong() shl 12) or (1L shl 5)
        assertEquals(expected, readLE32(bytes))
    }

    @Test
    fun `assembler round-trip through disassembler`() {
        val bytes = assemble {
            // Function prologue
            stpPre(X29, X30, SP, -16)
            movSp(X29, SP)
            // Body
            add(X0, X1, X2)
            mul(X0, X0, X3)
            // Epilogue
            ldpPost(X29, X30, SP, 16)
            ret()
        }

        val disasm = Arm64Disassembler()
        val instructions = disasm.disassemble(bytes)
        assertEquals(6, instructions.size)
        assertEquals("stp", instructions[0].mnemonic)
        assertTrue(instructions[1].mnemonic == "add" || instructions[1].toString().contains("sp"))
        assertEquals("add", instructions[2].mnemonic)
        assertEquals("mul", instructions[3].mnemonic)
        assertEquals("ldp", instructions[4].mnemonic)
        assertEquals("ret", instructions[5].mnemonic)
    }

    // Shorthand for register access in tests
    private val X0 get() = Arm64Register.X0
    private val X1 get() = Arm64Register.X1
    private val X2 get() = Arm64Register.X2
    private val X3 get() = Arm64Register.X3
    private val X8 get() = Arm64Register.X8
    private val X29 get() = Arm64Register.X29
    private val X30 get() = Arm64Register.X30
    private val SP get() = Arm64Register.SP
    private val W0 get() = Arm64Register.W0
    private val W1 get() = Arm64Register.W1
    private val W2 get() = Arm64Register.W2
    private val D0 get() = Arm64Register.D0
    private val D1 get() = Arm64Register.D1
    private val D2 get() = Arm64Register.D2
    private val S0 get() = Arm64Register.S0
    private val S1 get() = Arm64Register.S1
    private val S2 get() = Arm64Register.S2

    // ── Floating Point Tests ──────────────────────────────────────────

    @Test
    fun `FADD D0 D1 D2 encodes correctly`() {
        val bytes = assemble { fadd(D0, D1, D2) }
        assertEquals(0x1E622820L, readLE32(bytes))
    }

    @Test
    fun `FSUB D0 D1 D2 encodes correctly`() {
        val bytes = assemble { fsub(D0, D1, D2) }
        assertEquals(0x1E623820L, readLE32(bytes))
    }

    @Test
    fun `FMUL D0 D1 D2 encodes correctly`() {
        val bytes = assemble { fmul(D0, D1, D2) }
        assertEquals(0x1E620820L, readLE32(bytes))
    }

    @Test
    fun `FDIV D0 D1 D2 encodes correctly`() {
        val bytes = assemble { fdiv(D0, D1, D2) }
        assertEquals(0x1E621820L, readLE32(bytes))
    }

    @Test
    fun `FNEG D0 D1 encodes correctly`() {
        val bytes = assemble { fneg(D0, D1) }
        assertEquals(0x1E614020L, readLE32(bytes))
    }

    @Test
    fun `FABS D0 D1 encodes correctly`() {
        val bytes = assemble { fabs(D0, D1) }
        assertEquals(0x1E60C020L, readLE32(bytes))
    }

    @Test
    fun `FCMP D0 D1 encodes correctly`() {
        val bytes = assemble { fcmp(D0, D1) }
        assertEquals(0x1E612000L, readLE32(bytes))
    }

    @Test
    fun `FCMP D0 zero encodes correctly`() {
        val bytes = assemble { fcmpZero(D0) }
        assertEquals(0x1E602008L, readLE32(bytes))
    }

    @Test
    fun `FMOV D0 D1 encodes correctly`() {
        val bytes = assemble { fmov(D0, D1) }
        assertEquals(0x1E604020L, readLE32(bytes))
    }

    @Test
    fun `FMOV D0 X0 (GP to FP) encodes correctly`() {
        val bytes = assemble { fmovFromGp64(D0, X0) }
        assertEquals(0x9E670000L, readLE32(bytes))
    }

    @Test
    fun `FMOV X0 D0 (FP to GP) encodes correctly`() {
        val bytes = assemble { fmovToGp64(X0, D0) }
        assertEquals(0x9E660000L, readLE32(bytes))
    }

    @Test
    fun `SCVTF D0 X1 encodes correctly`() {
        val bytes = assemble { scvtf(D0, X1) }
        assertEquals(0x9E620020L, readLE32(bytes))
    }

    @Test
    fun `FCVTZS X0 D1 encodes correctly`() {
        val bytes = assemble { fcvtzs(X0, D1) }
        assertEquals(0x9E780020L, readLE32(bytes))
    }

    @Test
    fun `FCVT D0 S1 (S to D) encodes correctly`() {
        val bytes = assemble { fcvtStoD(D0, S1) }
        assertEquals(0x1E22C020L, readLE32(bytes))
    }

    @Test
    fun `FCVT S0 D1 (D to S) encodes correctly`() {
        val bytes = assemble { fcvtDtoS(S0, D1) }
        assertEquals(0x1E624020L, readLE32(bytes))
    }

    @Test
    fun `FP LDR D0 from SP offset 16 encodes correctly`() {
        val bytes = assemble { fldr(D0, Arm64Register.SP, 16) }
        // FD400000 | (16/8 << 10) | (31 << 5) | 0 = FD400BE0
        assertEquals(0xFD400BE0L, readLE32(bytes))
    }

    @Test
    fun `FP STR D0 to SP offset 0 encodes correctly`() {
        val bytes = assemble { fstr(D0, Arm64Register.SP, 0) }
        assertEquals(0xFD0003E0L, readLE32(bytes))
    }

    @Test
    fun `FP STP pre-index encodes correctly`() {
        val bytes = assemble { fstpPre(D0, D1, Arm64Register.SP, -16) }
        // 6D800000 | ((-16/8) & 0x7F) << 15 | (1 << 10) | (31 << 5) | 0
        // = 6D800000 | (0x7E << 15) | (1 << 10) | (31 << 5) | 0
        // = 6DBF07E0
        assertEquals(0x6DBF07E0L, readLE32(bytes))
    }

    @Test
    fun `FCSEL D0 D1 D2 EQ encodes correctly`() {
        val bytes = assemble { fcsel(D0, D1, D2, Arm64Condition.EQ) }
        // 1E600C00 | (2 << 16) | (0 << 12) | (1 << 5) | 0
        assertEquals(0x1E620C20L, readLE32(bytes))
    }

    @Test
    fun `FP round-trip through disassembler`() {
        val disasm = Arm64Disassembler()
        val bytes = assemble {
            fadd(D0, D1, D2)
            fsub(D0, D1, D2)
            fmul(D0, D1, D2)
            fdiv(D0, D1, D2)
            fneg(D0, D1)
            fcmp(D0, D1)
            fmov(D0, D1)
            scvtf(D0, X0)
            fcvtzs(X0, D0)
        }
        val instrs = disasm.disassemble(bytes)
        assertEquals(9, instrs.size)
        assertEquals("fadd", instrs[0].mnemonic)
        assertEquals("fsub", instrs[1].mnemonic)
        assertEquals("fmul", instrs[2].mnemonic)
        assertEquals("fdiv", instrs[3].mnemonic)
        assertEquals("fneg", instrs[4].mnemonic)
        assertEquals("fcmp", instrs[5].mnemonic)
        assertEquals("fmov", instrs[6].mnemonic)
        assertEquals("scvtf", instrs[7].mnemonic)
        assertEquals("fcvtzs", instrs[8].mnemonic)
    }
}
