package org.kgen.target.arm64.asm

import org.kgen.target.arm64.*
import org.kgen.target.arm64.Arm64Register.Companion.X0
import org.kgen.target.arm64.Arm64Register.Companion.X1
import org.kgen.target.arm64.Arm64Register.Companion.W0
import org.kgen.target.arm64.Arm64Register.Companion.W1
import org.kgen.target.arm64.Arm64Register.Companion.W2
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class Arm64TypedLabelTest {

    private fun readLE32(data: ByteArray, off: Int = 0): Int =
        (data[off].toInt() and 0xFF) or
        ((data[off + 1].toInt() and 0xFF) shl 8) or
        ((data[off + 2].toInt() and 0xFF) shl 16) or
        ((data[off + 3].toInt() and 0xFF) shl 24)

    @Test
    fun forwardBranchResolvesCorrectly() {
        val asm = Arm64Assembler()
        val target = asm.label()
        asm.b(target)
        asm.nop()
        asm.nop()
        asm.mark(target)
        asm.nop()

        val bytes = asm.bytes()
        assertEquals(16, bytes.size)
        val branchInsn = readLE32(bytes, 0)
        val opcode = branchInsn and 0xFC000000.toInt()
        assertEquals(0x14000000, opcode)
        val imm26 = branchInsn and 0x03FFFFFF
        assertEquals(3, imm26) // skip 3 instructions (b + 2 nops)
    }

    @Test
    fun backwardBranchResolvesCorrectly() {
        val asm = Arm64Assembler()
        val top = asm.mark()
        asm.nop()
        asm.b(top)

        val bytes = asm.bytes()
        assertEquals(8, bytes.size)
        val branchInsn = readLE32(bytes, 4)
        val imm26 = branchInsn and 0x03FFFFFF
        // backward offset = -1 instruction = 0x03FFFFFF (sign-extended 26-bit)
        assertEquals(0x03FFFFFF, imm26)
    }

    @Test
    fun blForwardResolvesCorrectly() {
        val asm = Arm64Assembler()
        val fn = asm.label()
        asm.bl(fn)
        asm.nop()
        asm.mark(fn)
        asm.ret()

        val bytes = asm.bytes()
        assertEquals(12, bytes.size)
        val blInsn = readLE32(bytes, 0)
        val opcode = blInsn and 0xFC000000.toInt()
        assertEquals(0x94000000.toInt(), opcode)
        val imm26 = blInsn and 0x03FFFFFF
        assertEquals(2, imm26)
    }

    @Test
    fun bCondForwardResolvesCorrectly() {
        val asm = Arm64Assembler()
        val target = asm.label()
        asm.bCond(Arm64Condition.EQ, target)
        asm.nop()
        asm.mark(target)
        asm.ret()

        val bytes = asm.bytes()
        assertEquals(12, bytes.size)
        val condInsn = readLE32(bytes, 0)
        // B.cond uses imm19 at bits [23:5]
        val imm19 = (condInsn shr 5) and 0x7FFFF
        assertEquals(2, imm19)
        // Condition code for EQ is 0
        val cond = condInsn and 0xF
        assertEquals(Arm64Condition.EQ.code, cond)
    }

    @Test
    fun bCondBackwardResolvesCorrectly() {
        val asm = Arm64Assembler()
        val loop = asm.mark()
        asm.nop()
        asm.bCond(Arm64Condition.NE, loop)

        val bytes = asm.bytes()
        assertEquals(8, bytes.size)
        val condInsn = readLE32(bytes, 4)
        val imm19 = (condInsn shr 5) and 0x7FFFF
        // backward by 1 instruction = -1 signed as 19-bit
        assertEquals(0x7FFFF, imm19)
    }

    @Test
    fun allConditionBranches() {
        for (cond in Arm64Condition.entries) {
            val asm = Arm64Assembler()
            val target = asm.label()
            asm.bCond(cond, target)
            asm.mark(target)

            val bytes = asm.bytes()
            val insn = readLE32(bytes, 0)
            assertEquals(cond.code, insn and 0xF, "condition code for $cond")
        }
    }

    @Test
    fun cbzForward64() {
        val asm = Arm64Assembler()
        val target = asm.label()
        asm.cbz(X0, target)
        asm.nop()
        asm.mark(target)

        val bytes = asm.bytes()
        assertEquals(8, bytes.size)
        val insn = readLE32(bytes, 0)
        val imm19 = (insn shr 5) and 0x7FFFF
        assertEquals(2, imm19)
        // sf=1 for 64-bit: bit 31 should be set
        assertTrue((insn and 0x80000000.toInt()) != 0)
    }

    @Test
    fun cbzForward32() {
        val asm = Arm64Assembler()
        val target = asm.label()
        asm.cbz(W0, target)
        asm.nop()
        asm.mark(target)

        val bytes = asm.bytes()
        val insn = readLE32(bytes, 0)
        val imm19 = (insn shr 5) and 0x7FFFF
        assertEquals(2, imm19)
        // sf=0 for 32-bit: bit 31 should be clear
        assertEquals(0, insn and 0x80000000.toInt())
    }

    @Test
    fun cbnzForward64() {
        val asm = Arm64Assembler()
        val target = asm.label()
        asm.cbnz(X1, target)
        asm.nop()
        asm.mark(target)

        val bytes = asm.bytes()
        val insn = readLE32(bytes, 0)
        val imm19 = (insn shr 5) and 0x7FFFF
        assertEquals(2, imm19)
        assertEquals(1, insn and 0x1F) // Rt = X1
    }

    @Test
    fun cbnzForward32() {
        val asm = Arm64Assembler()
        val target = asm.label()
        asm.cbnz(W2, target)
        asm.nop()
        asm.mark(target)

        val bytes = asm.bytes()
        val insn = readLE32(bytes, 0)
        assertEquals(2, insn and 0x1F) // Rt = W2
    }

    @Test
    fun doubleMarkThrows() {
        val asm = Arm64Assembler()
        val label = asm.mark()
        assertThrows(IllegalStateException::class.java) {
            asm.mark(label)
        }
    }

    @Test
    fun labelProperties() {
        val asm = Arm64Assembler()
        val forward = asm.label()
        assertFalse(forward.marked)
        assertEquals(0, forward.id)

        asm.mark(forward)
        assertTrue(forward.marked)

        val backward = asm.mark()
        assertTrue(backward.marked)
        assertEquals(1, backward.id)
    }

    @Test
    fun typedAndStringLabelsCoexist() {
        val asm = Arm64Assembler()
        val typed = asm.label()
        asm.b("string_target")
        asm.b(typed)
        asm.label("string_target")
        asm.mark(typed)
        asm.ret()

        val bytes = asm.bytes()
        assertTrue(bytes.size > 0)
    }

    @Test
    fun multipleLabelsInSequence() {
        val asm = Arm64Assembler()
        val first = asm.label()
        val second = asm.label()
        val third = asm.label()

        asm.b(first)
        asm.mark(first)
        asm.b(second)
        asm.mark(second)
        asm.b(third)
        asm.mark(third)
        asm.ret()

        val bytes = asm.bytes()
        assertEquals(16, bytes.size)
    }

    @Test
    fun loopWithCountdown() {
        val asm = Arm64Assembler()
        asm.sub(W0, W0, 1)
        val loop = asm.mark()
        asm.sub(W0, W0, 1)
        asm.cbnz(W0, loop)
        asm.ret()

        val bytes = asm.bytes()
        assertEquals(16, bytes.size)
    }

    @Test
    fun ifElsePattern() {
        val asm = Arm64Assembler()
        val elseLabel = asm.label()
        val end = asm.label()

        asm.cbz(X0, elseLabel)
        asm.nop()
        asm.b(end)
        asm.mark(elseLabel)
        asm.nop()
        asm.mark(end)
        asm.ret()

        val bytes = asm.bytes()
        assertEquals(20, bytes.size)
    }

    @Test
    fun dslForwardAndBackward() {
        val code = arm64 {
            val done = label()
            nop()
            bCond(Arm64Condition.EQ, done)
            nop()
            mark(done)
            ret()
        }
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun dslCountdownLoop() {
        val code = arm64 {
            val loop = mark()
            sub(W0, W0, 1)
            cbnz(W0, loop)
            ret()
        }
        assertEquals(12, code.size)
    }

    @Test
    fun dslCallAndReturn() {
        val code = arm64 {
            val fn = label()
            bl(fn)
            ret()
            mark(fn)
            nop()
            ret()
        }
        assertEquals(16, code.size)
    }

    @Test
    fun dslIfElseWithCbz() {
        val code = arm64 {
            val elseBlock = label()
            val end = label()

            cbz(X0, elseBlock)
            nop()
            b(end)

            mark(elseBlock)
            nop()

            mark(end)
            ret()
        }
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun dslLinkedListTraversal() {
        val code = arm64 {
            val done = label()
            cbz(X0, done)

            val walk = mark()
            add(W1, W1, 1)
            ldr(X0, X0, 8)
            cbnz(X0, walk)

            mark(done)
            ret()
        }
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun manyLabels() {
        val asm = Arm64Assembler()
        val labels = (0 until 50).map { asm.label() }

        for (label in labels) {
            asm.b(label)
        }
        for (label in labels) {
            asm.mark(label)
            asm.nop()
        }
        asm.ret()

        val bytes = asm.bytes()
        assertTrue(bytes.size > 0)
    }
}
