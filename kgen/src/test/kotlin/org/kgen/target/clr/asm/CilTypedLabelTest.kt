package org.kgen.target.clr.asm

import org.kgen.target.clr.CilCondition
import org.kgen.target.clr.CilSigType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CilTypedLabelTest {

    @Test
    fun labelAllocatesForwardReference() {
        val asm = CilAssembler()
        val target = asm.label()
        assertFalse(target.isMarked)
    }

    @Test
    fun markAllocatesAndBinds() {
        val asm = CilAssembler()
        val target = asm.mark()
        assertTrue(target.isMarked)
    }

    @Test
    fun markExistingBindsLabel() {
        val asm = CilAssembler()
        val target = asm.label()
        assertFalse(target.isMarked)
        asm.mark(target)
        assertTrue(target.isMarked)
    }

    @Test
    fun doubleMarkThrows() {
        val asm = CilAssembler()
        val label = asm.mark()
        assertThrows(IllegalArgumentException::class.java) {
            asm.mark(label)
        }
    }

    @Test
    fun forwardBranchResolvesCorrectly() {
        val asm = CilAssembler()
        val target = asm.label()
        asm.br(target)
        asm.nop()
        asm.mark(target)
        asm.ret()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
        assertEquals(0x2A, code[code.size - 1].toInt() and 0xFF) // ret
    }

    @Test
    fun backwardBranchResolvesCorrectly() {
        val asm = CilAssembler()
        val top = asm.mark()
        asm.nop()
        asm.br(top)

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun brtrueForward() {
        val asm = CilAssembler()
        val target = asm.label()
        asm.ldarg(0)
        asm.brtrue(target)
        asm.ldcI4Auto(0)
        asm.ret()
        asm.mark(target)
        asm.ldcI4Auto(1)
        asm.ret()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun brfalseForward() {
        val asm = CilAssembler()
        val target = asm.label()
        asm.ldarg(0)
        asm.brfalse(target)
        asm.nop()
        asm.mark(target)
        asm.ret()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun beqForward() {
        val asm = CilAssembler()
        val target = asm.label()
        asm.ldarg(0)
        asm.ldarg(1)
        asm.beq(target)
        asm.nop()
        asm.mark(target)
        asm.ret()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun labelAndDefineLabelAreEquivalent() {
        val asm = CilAssembler()
        val viaLabel = asm.label()
        val viaDefine = asm.defineLabel()

        assertFalse(viaLabel.isMarked)
        assertFalse(viaDefine.isMarked)
        assertNotEquals(viaLabel.id, viaDefine.id)
    }

    @Test
    fun markAndMarkLabelAreEquivalent() {
        val asm = CilAssembler()
        val label1 = asm.label()
        val label2 = asm.defineLabel()

        asm.mark(label1)
        asm.markLabel(label2)

        assertTrue(label1.isMarked)
        assertTrue(label2.isMarked)
    }

    @Test
    fun ifElsePattern() {
        val asm = CilAssembler()
        val elseLabel = asm.label()
        val end = asm.label()

        asm.ldarg(0)
        asm.brfalse(elseLabel)
        asm.ldcI4Auto(1)
        asm.br(end)
        asm.mark(elseLabel)
        asm.ldcI4Auto(0)
        asm.mark(end)
        asm.ret()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun loopPattern() {
        val asm = CilAssembler()
        val local = asm.declareLocal(CilSigType.I4)
        val loop = asm.mark()
        asm.ldloc(local)
        asm.ldcI4Auto(10)
        val exit = asm.label()
        asm.bge(exit)
        asm.ldloc(local)
        asm.ldcI4Auto(1)
        asm.add()
        asm.stloc(local)
        asm.br(loop)
        asm.mark(exit)
        asm.ldloc(local)
        asm.ret()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun multipleLabelsInSequence() {
        val asm = CilAssembler()
        val first = asm.label()
        val second = asm.label()
        val third = asm.label()

        asm.br(first)
        asm.mark(first)
        asm.br(second)
        asm.mark(second)
        asm.br(third)
        asm.mark(third)
        asm.ret()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun conditionEnumInvert() {
        assertEquals(CilCondition.BNE_UN, CilCondition.BEQ.invert())
        assertEquals(CilCondition.BEQ, CilCondition.BNE_UN.invert())
        assertEquals(CilCondition.BLT, CilCondition.BGE.invert())
        assertEquals(CilCondition.BGE, CilCondition.BLT.invert())
        assertEquals(CilCondition.BLE, CilCondition.BGT.invert())
        assertEquals(CilCondition.BGT, CilCondition.BLE.invert())
        assertEquals(CilCondition.BLT_UN, CilCondition.BGE_UN.invert())
        assertEquals(CilCondition.BGE_UN, CilCondition.BLT_UN.invert())
        assertEquals(CilCondition.BLE_UN, CilCondition.BGT_UN.invert())
        assertEquals(CilCondition.BGT_UN, CilCondition.BLE_UN.invert())
        assertEquals(CilCondition.BRFALSE, CilCondition.BRTRUE.invert())
        assertEquals(CilCondition.BRTRUE, CilCondition.BRFALSE.invert())
    }

    @Test
    fun dslFactorial() {
        val code = cil {
            val local = declareLocal(CilSigType.I4)
            ldarg(0)
            stloc(local)
            ldcI4Auto(1)     // result = 1

            val done = label()

            val loop = mark()
            ldloc(local)
            ldcI4Auto(1)
            ble(done)        // if n <= 1, done

            ldloc(local)
            mul()            // result *= n
            ldloc(local)
            ldcI4Auto(1)
            sub()
            stloc(local)     // n--
            br(loop)

            mark(done)
            ret()
        }
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun dslIfElse() {
        val code = cil {
            val elseBlock = label()
            val end = label()

            ldarg(0)
            brfalse(elseBlock)
            ldcI4Auto(1)
            br(end)

            mark(elseBlock)
            ldcI4Auto(0)

            mark(end)
            ret()
        }
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun dslMaxOfTwo() {
        val code = cil {
            val bIsLarger = label()
            val done = label()

            ldarg(0)
            ldarg(1)
            blt(bIsLarger)
            ldarg(0)
            br(done)

            mark(bIsLarger)
            ldarg(1)

            mark(done)
            ret()
        }
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun manyLabels() {
        val asm = CilAssembler()
        val labels = (0 until 50).map { asm.label() }

        for (label in labels) {
            asm.br(label)
        }
        for (label in labels) {
            asm.mark(label)
            asm.nop()
        }
        asm.ret()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
    }
}
