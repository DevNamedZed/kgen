package org.kgen.target.arm64.asm

import org.kgen.target.arm64.*
import org.kgen.target.arm64.disasm.Arm64Disassembler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class Arm64AssemblerExtendedTest {

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
    private val X8 get() = Arm64Register.X8
    private val X9 get() = Arm64Register.X9
    private val X10 get() = Arm64Register.X10
    private val X15 get() = Arm64Register.X15
    private val X16 get() = Arm64Register.X16
    private val X28 get() = Arm64Register.X28
    private val X29 get() = Arm64Register.X29
    private val X30 get() = Arm64Register.X30
    private val SP get() = Arm64Register.SP
    private val W0 get() = Arm64Register.W0
    private val W1 get() = Arm64Register.W1
    private val W2 get() = Arm64Register.W2
    private val W3 get() = Arm64Register.W3
    private val W15 get() = Arm64Register.W15
    private val D0 get() = Arm64Register.D0
    private val D1 get() = Arm64Register.D1
    private val D2 get() = Arm64Register.D2
    private val D3 get() = Arm64Register.D3
    private val S0 get() = Arm64Register.S0
    private val S1 get() = Arm64Register.S1
    private val S2 get() = Arm64Register.S2

    // --- ADD variations ---

    @Test
    fun addX0X0X0() {
        val bytes = assemble { add(X0, X0, X0) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun addHighRegisters() {
        val bytes = assemble { add(X28, X29, X30) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertTrue(inst and 0x80000000L != 0L, "sf bit should be set for 64-bit")
    }

    @Test
    fun addW0W1W2() {
        val bytes = assemble { add(W0, W1, W2) }
        assertEquals(4, bytes.size)
        val inst = readLE32(bytes)
        assertEquals(0L, inst and 0x80000000L, "sf bit should be clear for 32-bit")
    }

    @Test
    fun addImmZero() {
        val bytes = assemble { add(X0, X1, 0) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun addImmMax12Bit() {
        val bytes = assemble { add(X0, X1, 4095) }
        assertEquals(4, bytes.size)
    }

    // --- SUB variations ---

    @Test
    fun subW0W1W2() {
        val bytes = assemble { sub(W0, W1, W2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun subImmSmall() {
        val bytes = assemble { sub(X0, SP, 8) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun subImmLarge() {
        val bytes = assemble { sub(X0, SP, 4080) }
        assertEquals(4, bytes.size)
    }

    // --- Logical operations ---

    @Test
    fun andHighRegisters() {
        val bytes = assemble { and_(X10, X15, X28) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun orrHighRegisters() {
        val bytes = assemble { orr(X9, X10, X15) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun eorHighRegisters() {
        val bytes = assemble { eor(X5, X8, X16) }
        assertEquals(4, bytes.size)
    }

    // --- MOV variants ---

    @Test
    fun movHighRegs() {
        val bytes = assemble { mov(X28, X29) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun movzW0() {
        val bytes = assemble { movz(W0, 0xFFFF) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun movzShift32() {
        val bytes = assemble { movz(X0, 0x1234, shift = 32) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun movzShift48() {
        val bytes = assemble { movz(X0, 0xABCD, shift = 48) }
        assertEquals(4, bytes.size)
    }

    // --- MUL / SDIV / UDIV ---

    @Test
    fun mulHighRegs() {
        val bytes = assemble { mul(X10, X15, X28) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun sdivHighRegs() {
        val bytes = assemble { sdiv(X5, X8, X9) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun udivX0X1X2() {
        val bytes = assemble { udiv(X0, X1, X2) }
        assertEquals(4, bytes.size)
    }

    // --- Shifts ---

    @Test
    fun lslHighRegs() {
        val bytes = assemble { lsl(X10, X15, X28) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun lsrX0X1X2() {
        val bytes = assemble { lsr(X0, X1, X2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun asrX0X1X2() {
        val bytes = assemble { asr(X0, X1, X2) }
        assertEquals(4, bytes.size)
    }

    // --- CMP variants ---

    @Test
    fun cmpHighRegs() {
        val bytes = assemble { cmp(X15, X28) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun cmpImmZero() {
        val bytes = assemble { cmp(X0, 0) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun cmpImmLarge() {
        val bytes = assemble { cmp(X0, 4095) }
        assertEquals(4, bytes.size)
    }

    // --- Branch and link ---

    @Test
    fun bBackwardReference() {
        val bytes = assemble {
            label("loop")
            nop()
            b("loop")
        }
        assertEquals(8, bytes.size)
        val bInst = readLE32(bytes, 4)
        // should jump -1 instruction
        val offset = (bInst and 0x03FFFFFFL).let { if (it >= 0x02000000) it - 0x04000000 else it }
        assertEquals(-1L, offset)
    }

    @Test
    fun blBackwardReference() {
        val bytes = assemble {
            label("func")
            ret()
            bl("func")
        }
        assertEquals(8, bytes.size)
    }

    @Test
    fun bConditionalLabel() {
        val bytes = assemble {
            bCond(Arm64Condition.EQ, "target")
            nop()
            label("target")
            ret()
        }
        assertEquals(12, bytes.size)
    }

    @Test
    fun bConditionalNe() {
        val bytes = assemble {
            bCond(Arm64Condition.NE, "skip")
            nop()
            label("skip")
            ret()
        }
        assertEquals(12, bytes.size)
    }

    @Test
    fun bConditionalLt() {
        val bytes = assemble {
            bCond(Arm64Condition.LT, "neg")
            nop()
            label("neg")
            ret()
        }
        assertEquals(12, bytes.size)
    }

    // --- LDR/STR variants ---

    @Test
    fun ldrX0FromX1Offset0() {
        val bytes = assemble { ldr(X0, X1, 0) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun ldrX0FromX1Offset32760() {
        val bytes = assemble { ldr(X0, X1, 32760) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun strX0ToX1Offset0() {
        val bytes = assemble { str(X0, X1, 0) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun ldrW0FromX1Offset0() {
        val bytes = assemble { ldr(W0, X1, 0) }
        assertEquals(4, bytes.size)
    }

    // --- LDUR/STUR with negative offsets ---

    @Test
    fun ldurNeg1() {
        val bytes = assemble { ldur(X0, X1, -1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun ldurNeg256() {
        val bytes = assemble { ldur(X0, X1, -256) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun sturNeg8() {
        val bytes = assemble { stur(X0, X1, -8) }
        assertEquals(4, bytes.size)
    }

    // --- STP/LDP variants ---

    @Test
    fun stpPreHighRegs() {
        val bytes = assemble { stpPre(X28, X29, SP, -32) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun ldpPostHighRegs() {
        val bytes = assemble { ldpPost(X28, X29, SP, 32) }
        assertEquals(4, bytes.size)
    }

    // --- CSEL variants ---

    @Test
    fun cselNe() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.NE) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun cselLt() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.LT) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun cselGe() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.GE) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun cselGt() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.GT) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun cselLe() {
        val bytes = assemble { csel(X0, X1, X2, Arm64Condition.LE) }
        assertEquals(4, bytes.size)
    }

    // --- NOP / RET / BRK / SVC ---

    @Test
    fun multipleNops() {
        val bytes = assemble { nop(); nop(); nop() }
        assertEquals(12, bytes.size)
    }

    @Test
    fun brkHighImm() {
        val bytes = assemble { brk(0xFFFF) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun svcHighImm() {
        val bytes = assemble { svc(0x80) }
        assertEquals(4, bytes.size)
    }

    // --- BR / BLR ---

    @Test
    fun brHighReg() {
        val bytes = assemble { br(X28) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun blrHighReg() {
        val bytes = assemble { blr(X16) }
        assertEquals(4, bytes.size)
    }

    // --- Floating point single precision ---

    @Test
    fun faddS0S1S2() {
        val bytes = assemble { fadd(S0, S1, S2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun fsubS0S1S2() {
        val bytes = assemble { fsub(S0, S1, S2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun fmulS0S1S2() {
        val bytes = assemble { fmul(S0, S1, S2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun fdivS0S1S2() {
        val bytes = assemble { fdiv(S0, S1, S2) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun fnegS0S1() {
        val bytes = assemble { fneg(S0, S1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun fabsS0S1() {
        val bytes = assemble { fabs(S0, S1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun fcmpS0S1() {
        val bytes = assemble { fcmp(S0, S1) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun fcmpZeroS0() {
        val bytes = assemble { fcmpZero(S0) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun fmovS0S1() {
        val bytes = assemble { fmov(S0, S1) }
        assertEquals(4, bytes.size)
    }

    // --- Complete function sequences ---

    @Test
    fun leafFunction() {
        val bytes = assemble {
            add(X0, X0, X1)
            ret()
        }
        assertEquals(8, bytes.size)
        val disasm = Arm64Disassembler()
        val instrs = disasm.disassemble(bytes)
        assertEquals(2, instrs.size)
        assertEquals("add", instrs[0].mnemonic)
        assertEquals("ret", instrs[1].mnemonic)
    }

    @Test
    fun loopFunction() {
        val bytes = assemble {
            mov(X0, Arm64Register.XZR) // result = 0
            label("loop")
            add(X0, X0, X1) // result += step
            sub(X2, X2, 1) // count--
            cmp(X2, 0)
            bCond(Arm64Condition.GT, "loop")
            ret()
        }
        assertTrue(bytes.size >= 24)
    }

    @Test
    fun fpArithSequence() {
        val bytes = assemble {
            fadd(D0, D1, D2)
            fmul(D0, D0, D3)
            fsub(D0, D0, D1)
            ret()
        }
        assertEquals(16, bytes.size)
        val disasm = Arm64Disassembler()
        val instrs = disasm.disassemble(bytes)
        assertEquals(4, instrs.size)
    }

    @Test
    fun multipleLabels() {
        val bytes = assemble {
            cmp(X0, 0)
            bCond(Arm64Condition.EQ, "zero")
            cmp(X0, 1)
            bCond(Arm64Condition.EQ, "one")
            mov(X0, X2)
            b("end")
            label("zero")
            movz(X0, 0)
            b("end")
            label("one")
            movz(X0, 1)
            label("end")
            ret()
        }
        assertTrue(bytes.size > 0)
        assertTrue(bytes.size % 4 == 0, "ARM64 instructions are always 4 bytes")
    }

    @Test
    fun assemblerByteSizeTracking() {
        val asm = Arm64Assembler()
        asm.nop()
        val b1 = asm.bytes()
        assertEquals(4, b1.size)
        asm.nop()
        val b2 = asm.bytes()
        assertEquals(8, b2.size)
        asm.ret()
        val b3 = asm.bytes()
        assertEquals(12, b3.size)
    }

    @Test
    fun emptyAssemblerProducesNoBytes() {
        val bytes = assemble { }
        assertEquals(0, bytes.size)
    }

    @Test
    fun fcselNe() {
        val bytes = assemble { fcsel(D0, D1, D2, Arm64Condition.NE) }
        assertEquals(4, bytes.size)
    }

    @Test
    fun fcselLt() {
        val bytes = assemble { fcsel(D0, D1, D2, Arm64Condition.LT) }
        assertEquals(4, bytes.size)
    }
}
