package org.kgen.target.jvm.asm

import org.kgen.target.jvm.JvmCondition
import org.kgen.target.jvm.JvmOpCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class JvmTypedLabelTest {

    @Test
    fun forwardGotoResolvesCorrectly() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.goto(target)
        asm.nop()
        asm.mark(target)
        asm.ireturn()

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.GOTO.code, code[0].toInt() and 0xFF)
        // goto operand is 2-byte signed offset from the opcode
        val offset = ((code[1].toInt() and 0xFF) shl 8) or (code[2].toInt() and 0xFF)
        assertEquals(4, offset) // goto(3 bytes) + nop(1 byte) = 4
    }

    @Test
    fun backwardGotoResolvesCorrectly() {
        val asm = JvmAssembler()
        val top = asm.mark()
        asm.nop()
        asm.goto(top)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.GOTO.code, code[1].toInt() and 0xFF)
        val offset = (code[2].toInt() shl 8) or (code[3].toInt() and 0xFF)
        assertEquals(-1, offset) // nop at 0, goto at 1, target at 0, offset = 0-1 = -1
    }

    @Test
    fun ifeqForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.iconst0()
        asm.ifeq(target)
        asm.nop()
        asm.mark(target)
        asm.ireturn()

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IFEQ.code, code[1].toInt() and 0xFF)
    }

    @Test
    fun ifneForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifne(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IFNE.code, code[0].toInt() and 0xFF)
        val offset = ((code[1].toInt() and 0xFF) shl 8) or (code[2].toInt() and 0xFF)
        assertEquals(3, offset) // jump to immediately after
    }

    @Test
    fun ifltForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.iflt(target)
        asm.nop()
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IFLT.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifgeForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifge(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IFGE.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifgtForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifgt(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IFGT.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifleForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifle(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IFLE.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifIcmpeqForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifIcmpeq(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IF_ICMPEQ.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifIcmpneForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifIcmpne(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IF_ICMPNE.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifIcmpltForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifIcmplt(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IF_ICMPLT.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifIcmpgeForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifIcmpge(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IF_ICMPGE.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifIcmpgtForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifIcmpgt(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IF_ICMPGT.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifIcmpleForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifIcmple(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IF_ICMPLE.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifAcmpeqForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifAcmpeq(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IF_ACMPEQ.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifAcmpneForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifAcmpne(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IF_ACMPNE.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifnullForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifnull(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IFNULL.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun ifnonnullForward() {
        val asm = JvmAssembler()
        val target = asm.label()
        asm.ifnonnull(target)
        asm.mark(target)

        val code = asm.toByteArray()
        assertEquals(JvmOpCode.IFNONNULL.code, code[0].toInt() and 0xFF)
    }

    @Test
    fun doubleMarkThrows() {
        val asm = JvmAssembler()
        val label = asm.mark()
        assertThrows(IllegalStateException::class.java) {
            asm.mark(label)
        }
    }

    @Test
    fun labelNotMarkedThrows() {
        val asm = JvmAssembler()
        val label = asm.label()
        asm.goto(label)
        assertThrows(IllegalStateException::class.java) {
            asm.toByteArray()
        }
    }

    @Test
    fun labelProperties() {
        val asm = JvmAssembler()
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
        val asm = JvmAssembler()
        val typed = asm.label()
        asm.goto("string_target")
        asm.goto(typed)
        asm.label("string_target")
        asm.mark(typed)
        asm.ireturn()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun loopPattern() {
        val asm = JvmAssembler()
        asm.iconst0()       // int i = 0
        asm.istore(1)
        val loop = asm.mark()
        asm.iload(1)
        asm.bipush(10)
        asm.ifIcmpge(asm.label().also { asm.iinc(1, 1); asm.goto(loop); asm.mark(it) })
        asm.iload(1)
        asm.ireturn()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun ifElsePattern() {
        val asm = JvmAssembler()
        val elseLabel = asm.label()
        val end = asm.label()

        asm.iload(0)
        asm.ifeq(elseLabel)
        asm.iconst1()       // true branch
        asm.goto(end)
        asm.mark(elseLabel)
        asm.iconst0()       // false branch
        asm.mark(end)
        asm.ireturn()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun multipleLabelsInSequence() {
        val asm = JvmAssembler()
        val first = asm.label()
        val second = asm.label()
        val third = asm.label()

        asm.goto(first)
        asm.mark(first)
        asm.goto(second)
        asm.mark(second)
        asm.goto(third)
        asm.mark(third)
        asm.ireturn()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun conditionEnumInvert() {
        assertEquals(JvmCondition.IFNE, JvmCondition.IFEQ.invert())
        assertEquals(JvmCondition.IFEQ, JvmCondition.IFNE.invert())
        assertEquals(JvmCondition.IFGE, JvmCondition.IFLT.invert())
        assertEquals(JvmCondition.IFLT, JvmCondition.IFGE.invert())
        assertEquals(JvmCondition.IFLE, JvmCondition.IFGT.invert())
        assertEquals(JvmCondition.IFGT, JvmCondition.IFLE.invert())
        assertEquals(JvmCondition.IF_ICMPNE, JvmCondition.IF_ICMPEQ.invert())
        assertEquals(JvmCondition.IF_ICMPEQ, JvmCondition.IF_ICMPNE.invert())
        assertEquals(JvmCondition.IF_ACMPNE, JvmCondition.IF_ACMPEQ.invert())
        assertEquals(JvmCondition.IF_ACMPEQ, JvmCondition.IF_ACMPNE.invert())
        assertEquals(JvmCondition.IFNONNULL, JvmCondition.IFNULL.invert())
        assertEquals(JvmCondition.IFNULL, JvmCondition.IFNONNULL.invert())
    }

    @Test
    fun dslCountdownLoop() {
        val code = jvm {
            val loop = mark()
            iload(0)
            iconst1()
            isub()
            istore(0)
            iload(0)
            ifgt(loop)
            ireturn()
        }
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun dslIfElse() {
        val code = jvm {
            val elseBlock = label()
            val end = label()

            iload(0)
            ifeq(elseBlock)
            iconst1()
            goto(end)

            mark(elseBlock)
            iconst0()

            mark(end)
            ireturn()
        }
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun dslMaxOfTwo() {
        val code = jvm {
            val bIsLarger = label()
            val done = label()

            iload(0)
            iload(1)
            ifIcmplt(bIsLarger)
            iload(0)
            goto(done)

            mark(bIsLarger)
            iload(1)

            mark(done)
            ireturn()
        }
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun dslSumLoop() {
        val code = jvm {
            iconst0()
            istore(1)          // sum = 0
            iconst0()
            istore(2)          // i = 0

            val end = label()

            val loop = mark()
            iload(2)
            iload(0)
            ifIcmpge(end)      // if i >= n, done

            iload(1)
            iload(2)
            iadd()
            istore(1)          // sum += i

            iinc(2, 1)         // i++
            goto(loop)

            mark(end)
            iload(1)
            ireturn()
        }
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun manyLabels() {
        val asm = JvmAssembler()
        val labels = (0 until 50).map { asm.label() }

        for (label in labels) {
            asm.goto(label)
        }
        for (label in labels) {
            asm.mark(label)
            asm.nop()
        }
        asm.ireturn()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }
}
