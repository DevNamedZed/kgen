package org.kgen.target.arm64.asm

import org.kgen.target.arm64.Arm64Register
import org.kgen.target.arm64.Arm64Register.Companion.Q0
import org.kgen.target.arm64.Arm64Register.Companion.Q1
import org.kgen.target.arm64.Arm64Register.Companion.Q2
import org.kgen.target.arm64.Arm64Register.Companion.Q3
import org.kgen.target.arm64.Arm64Register.Companion.Q4
import org.kgen.target.arm64.Arm64Register.Companion.Q5
import org.kgen.target.arm64.Arm64Register.Companion.Q6
import org.kgen.target.arm64.Arm64Register.Companion.Q7
import org.kgen.target.arm64.Arm64Register.Companion.Q8
import org.kgen.target.arm64.Arm64Register.Companion.Q9
import org.kgen.target.arm64.Arm64Register.Companion.Q10
import org.kgen.target.arm64.Arm64Register.Companion.Q11
import org.kgen.target.arm64.Arm64Register.Companion.Q12
import org.kgen.target.arm64.Arm64Register.Companion.Q13
import org.kgen.target.arm64.Arm64Register.Companion.Q15
import org.kgen.target.arm64.Arm64Register.Companion.Q31
import org.kgen.target.arm64.Arm64Register.Companion.X0
import org.kgen.target.arm64.Arm64Register.Companion.X1
import org.kgen.target.arm64.Arm64Register.Companion.X2
import org.kgen.target.arm64.Arm64Register.Companion.X10
import org.kgen.target.arm64.VectorArrangement
import org.kgen.target.arm64.disasm.Arm64Disassembler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class Arm64NeonTest {

    private val arr = VectorArrangement

    private fun assemble(block: Arm64Assembler.() -> Unit): ByteArray {
        val asm = Arm64Assembler()
        asm.block()
        return asm.bytes()
    }

    private fun disassemble(bytes: ByteArray) = Arm64Disassembler().disassemble(bytes)

    private fun singleInstr(block: Arm64Assembler.() -> Unit): String {
        val instrs = disassemble(assemble(block))
        assertEquals(1, instrs.size)
        return "${instrs[0].mnemonic} ${instrs[0].operandsStr}".trim()
    }

    @Test
    fun addVec4S() {
        val s = singleInstr { addVec(VectorArrangement.S4, Q0, Q1, Q2) }
        assertEquals("add v0.4s, v1.4s, v2.4s", s)
    }

    @Test
    fun addVec16B() {
        val s = singleInstr { addVec(VectorArrangement.B16, Q3, Q4, Q5) }
        assertEquals("add v3.16b, v4.16b, v5.16b", s)
    }

    @Test
    fun addVec8B() {
        val s = singleInstr { addVec(VectorArrangement.B8, Q0, Q1, Q2) }
        assertEquals("add v0.8b, v1.8b, v2.8b", s)
    }

    @Test
    fun addVec2D() {
        val s = singleInstr { addVec(VectorArrangement.D2, Q10, Q11, Q12) }
        assertEquals("add v10.2d, v11.2d, v12.2d", s)
    }

    @Test
    fun subVec4S() {
        val s = singleInstr { subVec(VectorArrangement.S4, Q0, Q1, Q2) }
        assertEquals("sub v0.4s, v1.4s, v2.4s", s)
    }

    @Test
    fun subVec8H() {
        val s = singleInstr { subVec(VectorArrangement.H8, Q5, Q6, Q7) }
        assertEquals("sub v5.8h, v6.8h, v7.8h", s)
    }

    @Test
    fun mulVec4S() {
        val s = singleInstr { mulVec(VectorArrangement.S4, Q0, Q1, Q2) }
        assertEquals("mul v0.4s, v1.4s, v2.4s", s)
    }

    @Test
    fun mulVec16B() {
        val s = singleInstr { mulVec(VectorArrangement.B16, Q3, Q4, Q5) }
        assertEquals("mul v3.16b, v4.16b, v5.16b", s)
    }

    @Test
    fun mulVecRejectsD2() {
        assertThrows(IllegalArgumentException::class.java) {
            assemble { mulVec(VectorArrangement.D2, Q0, Q1, Q2) }
        }
    }

    @Test
    fun andVec16B() {
        val s = singleInstr { andVec(Q0, Q1, Q2) }
        assertEquals("and v0.16b, v1.16b, v2.16b", s)
    }

    @Test
    fun orrVec16B() {
        val s = singleInstr { orrVec(Q0, Q1, Q2) }
        assertEquals("orr v0.16b, v1.16b, v2.16b", s)
    }

    @Test
    fun eorVec16B() {
        val s = singleInstr { eorVec(Q0, Q1, Q2) }
        assertEquals("eor v0.16b, v1.16b, v2.16b", s)
    }

    @Test
    fun cmeq4S() {
        val s = singleInstr { cmeq(VectorArrangement.S4, Q0, Q1, Q2) }
        assertEquals("cmeq v0.4s, v1.4s, v2.4s", s)
    }

    @Test
    fun cmgt2D() {
        val s = singleInstr { cmgt(VectorArrangement.D2, Q10, Q11, Q12) }
        assertEquals("cmgt v10.2d, v11.2d, v12.2d", s)
    }

    @Test
    fun cmge8H() {
        val s = singleInstr { cmge(VectorArrangement.H8, Q0, Q1, Q2) }
        assertEquals("cmge v0.8h, v1.8h, v2.8h", s)
    }

    @Test
    fun cmhi4S() {
        val s = singleInstr { cmhi(VectorArrangement.S4, Q3, Q4, Q5) }
        assertEquals("cmhi v3.4s, v4.4s, v5.4s", s)
    }

    @Test
    fun cmhs16B() {
        val s = singleInstr { cmhs(VectorArrangement.B16, Q0, Q1, Q2) }
        assertEquals("cmhs v0.16b, v1.16b, v2.16b", s)
    }

    @Test
    fun faddVec4S() {
        val s = singleInstr { faddVec(VectorArrangement.S4, Q0, Q1, Q2) }
        assertEquals("fadd v0.4s, v1.4s, v2.4s", s)
    }

    @Test
    fun faddVec2D() {
        val s = singleInstr { faddVec(VectorArrangement.D2, Q0, Q1, Q2) }
        assertEquals("fadd v0.2d, v1.2d, v2.2d", s)
    }

    @Test
    fun fsubVec4S() {
        val s = singleInstr { fsubVec(VectorArrangement.S4, Q0, Q1, Q2) }
        assertEquals("fsub v0.4s, v1.4s, v2.4s", s)
    }

    @Test
    fun fmulVec4S() {
        val s = singleInstr { fmulVec(VectorArrangement.S4, Q0, Q1, Q2) }
        assertEquals("fmul v0.4s, v1.4s, v2.4s", s)
    }

    @Test
    fun fdivVec2D() {
        val s = singleInstr { fdivVec(VectorArrangement.D2, Q0, Q1, Q2) }
        assertEquals("fdiv v0.2d, v1.2d, v2.2d", s)
    }

    @Test
    fun faddVecRejects8H() {
        assertThrows(IllegalArgumentException::class.java) {
            assemble { faddVec(VectorArrangement.H8, Q0, Q1, Q2) }
        }
    }

    @Test
    fun ldrQ() {
        val s = singleInstr { ldrQ(Q0, X1) }
        assertEquals("ldr q0, [x1]", s)
    }

    @Test
    fun ldrQWithOffset() {
        val s = singleInstr { ldrQ(Q5, X10, 32) }
        assertEquals("ldr q5, [x10, #32]", s)
    }

    @Test
    fun strQ() {
        val s = singleInstr { strQ(Q0, X1) }
        assertEquals("str q0, [x1]", s)
    }

    @Test
    fun strQWithOffset() {
        val s = singleInstr { strQ(Q31, X0, 48) }
        assertEquals("str q31, [x0, #48]", s)
    }

    @Test
    fun ldrQAlignmentCheck() {
        assertThrows(IllegalArgumentException::class.java) {
            assemble { ldrQ(Q0, X1, 7) }
        }
    }

    @Test
    fun vectorArrangementFromEncoding() {
        assertEquals(VectorArrangement.B8, VectorArrangement.fromEncoding(0, 0))
        assertEquals(VectorArrangement.B16, VectorArrangement.fromEncoding(1, 0))
        assertEquals(VectorArrangement.H4, VectorArrangement.fromEncoding(0, 1))
        assertEquals(VectorArrangement.H8, VectorArrangement.fromEncoding(1, 1))
        assertEquals(VectorArrangement.S2, VectorArrangement.fromEncoding(0, 2))
        assertEquals(VectorArrangement.S4, VectorArrangement.fromEncoding(1, 2))
        assertEquals(VectorArrangement.D2, VectorArrangement.fromEncoding(1, 3))
    }

    @Test
    fun vectorArrangementProperties() {
        assertEquals(128, VectorArrangement.S4.totalBits)
        assertEquals(64, VectorArrangement.S2.totalBits)
        assertEquals(32, VectorArrangement.S4.elementBits)
        assertEquals(4, VectorArrangement.S4.lanes)
        assertEquals("4s", VectorArrangement.S4.suffix)
        assertEquals("2d", VectorArrangement.D2.suffix)
        assertEquals("16b", VectorArrangement.B16.suffix)
    }

    @Test
    fun registerAllQ() {
        val qRegs = Arm64Register.allQ()
        assertEquals(32, qRegs.size)
        assertEquals("q0", qRegs[0].name())
        assertEquals("q31", qRegs[31].name())
    }

    @Test
    fun registerByEncodingQ() {
        assertEquals(Q0, Arm64Register.byEncodingQ(0))
        assertEquals(Q15, Arm64Register.byEncodingQ(15))
        assertEquals(Q31, Arm64Register.byEncodingQ(31))
    }

    @Test
    fun multipleNeonInstructions() {
        val bytes = assemble {
            ldrQ(Q0, X0)
            ldrQ(Q1, X1)
            addVec(VectorArrangement.S4, Q2, Q0, Q1)
            fmulVec(VectorArrangement.S4, Q3, Q2, Q2)
            strQ(Q3, X2)
        }
        val instrs = disassemble(bytes)
        assertEquals(5, instrs.size)
        assertTrue(instrs[0].operandsStr.contains("q0"))
        assertEquals("add", instrs[2].mnemonic)
        assertEquals("fmul", instrs[3].mnemonic)
        assertTrue(instrs[4].operandsStr.contains("q3"))
    }

    @Test
    fun dupFromGp4S() {
        val s = singleInstr { dupFromGp(VectorArrangement.S4, Q0, X1) }
        assertEquals("dup v0.4s, w1", s)
    }

    @Test
    fun dupElement4S() {
        val s = singleInstr { dupElement(VectorArrangement.S4, Q0, Q1, 2) }
        assertEquals("dup v0.4s, v1.s[2]", s)
    }

    @Test
    fun addv4S() {
        val s = singleInstr { addv(VectorArrangement.S4, Q0, Q1) }
        assertEquals("addv s0, v1.4s", s)
    }

    @Test
    fun encodingRoundTrip() {
        val bytes = assemble {
            addVec(VectorArrangement.S4, Q0, Q1, Q2)
            subVec(VectorArrangement.D2, Q3, Q4, Q5)
            faddVec(VectorArrangement.S4, Q6, Q7, Q8)
            andVec(Q9, Q10, Q11)
            ldrQ(Q12, X0, 16)
            strQ(Q13, X1, 32)
        }
        assertEquals(24, bytes.size)
        val instrs = disassemble(bytes)
        assertEquals(6, instrs.size)
    }
}
