package org.kgen.target.riscv.asm

import org.kgen.target.riscv.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RiscVTypedLabelTest {

    private fun readU32(bytes: ByteArray, off: Int): Int =
        (bytes[off].toInt() and 0xFF) or
        ((bytes[off + 1].toInt() and 0xFF) shl 8) or
        ((bytes[off + 2].toInt() and 0xFF) shl 16) or
        ((bytes[off + 3].toInt() and 0xFF) shl 24)

    @Test
    fun forwardBeqResolvesCorrectly() {
        val asm = RiscVAssembler()
        val target = asm.label()
        asm.beq(X10, X11, target)
        asm.nop()
        asm.mark(target)
        asm.nop()

        val bytes = asm.bytes()
        assertEquals(12, bytes.size)
        val branchInsn = readU32(bytes, 0)
        val opcode = branchInsn and 0x7F
        assertEquals(0x63, opcode) // B-type opcode
    }

    @Test
    fun backwardBneResolvesCorrectly() {
        val asm = RiscVAssembler()
        val top = asm.mark()
        asm.nop()
        asm.bne(X10, X0, top)

        val bytes = asm.bytes()
        assertEquals(8, bytes.size)
        val branchInsn = readU32(bytes, 4)
        val opcode = branchInsn and 0x7F
        assertEquals(0x63, opcode)
    }

    @Test
    fun allBranchConditions() {
        val conditions = listOf(
            { asm: RiscVAssembler, label: RiscVAssembler.Label -> asm.beq(X10, X11, label) },
            { asm: RiscVAssembler, label: RiscVAssembler.Label -> asm.bne(X10, X11, label) },
            { asm: RiscVAssembler, label: RiscVAssembler.Label -> asm.blt(X10, X11, label) },
            { asm: RiscVAssembler, label: RiscVAssembler.Label -> asm.bge(X10, X11, label) },
            { asm: RiscVAssembler, label: RiscVAssembler.Label -> asm.bltu(X10, X11, label) },
            { asm: RiscVAssembler, label: RiscVAssembler.Label -> asm.bgeu(X10, X11, label) },
        )

        for (emitBranch in conditions) {
            val asm = RiscVAssembler()
            val target = asm.label()
            emitBranch(asm, target)
            asm.mark(target)

            val bytes = asm.bytes()
            assertEquals(4, bytes.size)
            val insn = readU32(bytes, 0)
            assertEquals(0x63, insn and 0x7F)
        }
    }

    @Test
    fun branchWithConditionEnum() {
        for (condition in RiscVCondition.entries) {
            val asm = RiscVAssembler()
            val target = asm.label()
            asm.branch(condition, X10, X11, target)
            asm.mark(target)

            val bytes = asm.bytes()
            assertEquals(4, bytes.size)
        }
    }

    @Test
    fun jalForward() {
        val asm = RiscVAssembler()
        val fn = asm.label()
        asm.jal(X1, fn)
        asm.nop()
        asm.mark(fn)
        asm.ret()

        val bytes = asm.bytes()
        assertEquals(12, bytes.size)
        val jalInsn = readU32(bytes, 0)
        assertEquals(0x6F, jalInsn and 0x7F) // JAL opcode
    }

    @Test
    fun jForward() {
        val asm = RiscVAssembler()
        val target = asm.label()
        asm.j(target)
        asm.nop()
        asm.mark(target)
        asm.nop()

        val bytes = asm.bytes()
        assertEquals(12, bytes.size)
        val jalInsn = readU32(bytes, 0)
        assertEquals(0x6F, jalInsn and 0x7F) // JAL opcode (j is jal x0)
        val rd = (jalInsn shr 7) and 0x1F
        assertEquals(0, rd) // x0 for j pseudo
    }

    @Test
    fun callForward() {
        val asm = RiscVAssembler()
        val fn = asm.label()
        asm.call(fn)
        asm.nop()
        asm.mark(fn)
        asm.ret()

        val bytes = asm.bytes()
        assertEquals(12, bytes.size)
        val jalInsn = readU32(bytes, 0)
        assertEquals(0x6F, jalInsn and 0x7F) // JAL opcode
        val rd = (jalInsn shr 7) and 0x1F
        assertEquals(1, rd) // x1 (ra) for call pseudo
    }

    @Test
    fun doubleMarkThrows() {
        val asm = RiscVAssembler()
        val label = asm.mark()
        assertThrows(IllegalStateException::class.java) {
            asm.mark(label)
        }
    }

    @Test
    fun labelProperties() {
        val asm = RiscVAssembler()
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
        val asm = RiscVAssembler()
        val typed = asm.label()
        asm.j("string_target")
        asm.j(typed)
        asm.label("string_target")
        asm.mark(typed)
        asm.ret()

        val bytes = asm.bytes()
        assertTrue(bytes.size > 0)
    }

    @Test
    fun loopPattern() {
        val asm = RiscVAssembler()
        asm.addi(X10, X0, 10) // i = 10
        val loop = asm.mark()
        asm.addi(X10, X10, -1) // i--
        asm.bne(X10, X0, loop) // if i != 0, loop
        asm.ret()

        val bytes = asm.bytes()
        assertEquals(16, bytes.size) // 4 instructions
    }

    @Test
    fun ifElsePattern() {
        val asm = RiscVAssembler()
        val elseLabel = asm.label()
        val end = asm.label()

        asm.beq(X10, X0, elseLabel)
        asm.addi(X11, X0, 1) // result = 1
        asm.j(end)
        asm.mark(elseLabel)
        asm.addi(X11, X0, 0) // result = 0
        asm.mark(end)
        asm.ret()

        val bytes = asm.bytes()
        assertEquals(20, bytes.size) // 5 instructions
    }

    @Test
    fun multipleLabelsInSequence() {
        val asm = RiscVAssembler()
        val first = asm.label()
        val second = asm.label()
        val third = asm.label()

        asm.j(first)
        asm.mark(first)
        asm.j(second)
        asm.mark(second)
        asm.j(third)
        asm.mark(third)
        asm.ret()

        val bytes = asm.bytes()
        assertEquals(16, bytes.size)
    }

    @Test
    fun conditionEnumInvert() {
        assertEquals(RiscVCondition.NE, RiscVCondition.EQ.invert())
        assertEquals(RiscVCondition.EQ, RiscVCondition.NE.invert())
        assertEquals(RiscVCondition.GE, RiscVCondition.LT.invert())
        assertEquals(RiscVCondition.LT, RiscVCondition.GE.invert())
        assertEquals(RiscVCondition.GEU, RiscVCondition.LTU.invert())
        assertEquals(RiscVCondition.LTU, RiscVCondition.GEU.invert())
    }

    @Test
    fun dslFibonacci() {
        val code = riscv {
            addi(X11, X0, 0)                   // a = 0
            addi(X12, X0, 1)                   // b = 1
            addi(X13, X0, 0)                   // i = 0

            val done = label()

            val loop = mark()
            bge(X13, X10, done)                // if i >= n, done

            add(X14, X11, X0)                  // temp = a
            add(X11, X12, X0)                  // a = b
            add(X12, X14, X12)                 // b = temp + b
            addi(X13, X13, 1)
            j(loop)

            mark(done)
            add(X10, X11, X0)                  // return a
            ret()
        }
        assertTrue(code.isNotEmpty())
        assertEquals(44, code.size) // 11 instructions * 4 bytes
    }

    @Test
    fun dslGcd() {
        val code = riscv {
            val done = label()

            val loop = mark()
            beq(X11, X0, done)                 // if b == 0, done
            rem(X12, X10, X11)                 // t = a % b
            add(X10, X11, X0)                  // a = b
            add(X11, X12, X0)                  // b = t
            j(loop)

            mark(done)
            ret()
        }
        assertTrue(code.isNotEmpty())
        assertEquals(24, code.size) // 6 instructions
    }

    @Test
    fun dslSumToN() {
        val code = riscv {
            addi(X11, X0, 0)                   // result = 0

            val done = label()

            val loop = mark()
            beq(X10, X0, done)                 // if n == 0, done
            add(X11, X11, X10)                 // result += n
            addi(X10, X10, -1)                 // n--
            j(loop)

            mark(done)
            add(X10, X11, X0)                  // return result
            ret()
        }
        assertEquals(28, code.size) // 7 instructions
    }

    @Test
    fun dslIfElse() {
        val code = riscv {
            val elseBlock = label()
            val end = label()

            beq(X10, X0, elseBlock)
            addi(X11, X0, 1)
            j(end)

            mark(elseBlock)
            addi(X11, X0, 0)

            mark(end)
            ret()
        }
        assertEquals(20, code.size)
    }

    @Test
    fun manyLabels() {
        val asm = RiscVAssembler()
        val labels = (0 until 50).map { asm.label() }

        for (label in labels) {
            asm.j(label)
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
