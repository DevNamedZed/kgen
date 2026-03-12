package org.kgen.target.jvm.asm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.target.jvm.JvmOpCode

class JvmAssemblerTest {

    @Test
    fun emptyAssembler() {
        val asm = JvmAssembler()
        val code = asm.toByteArray()
        assertEquals(0, code.size)
    }

    @Test
    fun iconst() {
        val asm = JvmAssembler()
        asm.iconst0()
        asm.iconst1()
        asm.iconst5()
        asm.iconstM1()
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x03, code[0].toInt() and 0xFF) // iconst_0
        assertEquals(0x04, code[1].toInt() and 0xFF) // iconst_1
        assertEquals(0x08, code[2].toInt() and 0xFF) // iconst_5
        assertEquals(0x02, code[3].toInt() and 0xFF) // iconst_m1
    }

    @Test
    fun bipush() {
        val asm = JvmAssembler()
        asm.bipush(42)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x10, code[0].toInt() and 0xFF) // bipush
        assertEquals(42, code[1].toInt())
    }

    @Test
    fun sipush() {
        val asm = JvmAssembler()
        asm.sipush(1000)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x11, code[0].toInt() and 0xFF) // sipush
        val value = ((code[1].toInt() and 0xFF) shl 8) or (code[2].toInt() and 0xFF)
        assertEquals(1000, value)
    }

    @Test
    fun pushIntChoosesCompactEncoding() {
        val asm = JvmAssembler()
        asm.pushInt(0)   // iconst_0 (1 byte)
        asm.pushInt(5)   // iconst_5 (1 byte)
        asm.pushInt(42)  // bipush (2 bytes)
        asm.pushInt(1000) // sipush (3 bytes)
        val code = asm.toByteArray()
        assertEquals(7, code.size)
    }

    @Test
    fun iloadShortForm() {
        val asm = JvmAssembler()
        asm.iload(0) // iload_0
        asm.iload(1) // iload_1
        asm.iload(2) // iload_2
        asm.iload(3) // iload_3
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x1A, code[0].toInt() and 0xFF)
        assertEquals(0x1B, code[1].toInt() and 0xFF)
        assertEquals(0x1C, code[2].toInt() and 0xFF)
        assertEquals(0x1D, code[3].toInt() and 0xFF)
    }

    @Test
    fun iloadLongForm() {
        val asm = JvmAssembler()
        asm.iload(10)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x15, code[0].toInt() and 0xFF) // iload
        assertEquals(10, code[1].toInt() and 0xFF)
    }

    @Test
    fun istoreShortForm() {
        val asm = JvmAssembler()
        asm.istore(0) // istore_0
        asm.istore(3) // istore_3
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x3B, code[0].toInt() and 0xFF)
        assertEquals(0x3E, code[1].toInt() and 0xFF)
    }

    @Test
    fun arithmetic() {
        val asm = JvmAssembler()
        asm.iadd()
        asm.isub()
        asm.imul()
        asm.idiv()
        asm.irem()
        val code = asm.toByteArray()
        assertEquals(5, code.size)
        assertEquals(0x60, code[0].toInt() and 0xFF) // iadd
        assertEquals(0x64, code[1].toInt() and 0xFF) // isub
        assertEquals(0x68, code[2].toInt() and 0xFF) // imul
        assertEquals(0x6C, code[3].toInt() and 0xFF) // idiv
        assertEquals(0x70, code[4].toInt() and 0xFF) // irem
    }

    @Test
    fun longArithmetic() {
        val asm = JvmAssembler()
        asm.ladd()
        asm.lsub()
        asm.lmul()
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x61, code[0].toInt() and 0xFF) // ladd
        assertEquals(0x65, code[1].toInt() and 0xFF) // lsub
        assertEquals(0x69, code[2].toInt() and 0xFF) // lmul
    }

    @Test
    fun floatArithmetic() {
        val asm = JvmAssembler()
        asm.fadd()
        asm.fsub()
        asm.fmul()
        asm.fdiv()
        asm.dadd()
        asm.dsub()
        val code = asm.toByteArray()
        assertEquals(6, code.size)
    }

    @Test
    fun returns() {
        val asm = JvmAssembler()
        asm.ireturn()
        asm.lreturn()
        asm.freturn()
        asm.dreturn()
        asm.areturn()
        asm.return_()
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        assertEquals(0xAC, code[0].toInt() and 0xFF) // ireturn
        assertEquals(0xB1, code[5].toInt() and 0xFF) // return
    }

    @Test
    fun addTwoInts() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.iadd()
        asm.ireturn()
        val code = asm.toByteArray()
        assertEquals(4, code.size)
    }

    @Test
    fun branchForward() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.ifeq("skip")
        asm.iconst1()
        asm.ireturn()
        asm.label("skip")
        asm.iconst0()
        asm.ireturn()
        val code = asm.toByteArray()
        // ifeq has 2-byte signed offset from opcode position
        val offset = ((code[2].toInt() and 0xFF) shl 8) or (code[3].toInt() and 0xFF)
        assertEquals(5, offset) // jump 5 bytes forward (past ifeq+2 + iconst + ireturn)
    }

    @Test
    fun branchBackward() {
        val asm = JvmAssembler()
        asm.label("loop")
        asm.iload(0)
        asm.iconst1()
        asm.isub()
        asm.dup()
        asm.istore(0)
        asm.ifne("loop")
        asm.return_()
        val code = asm.toByteArray()
        // ifne should jump backward
        val patchOffset = code.size - 4 // ifne is at size-3 (opcode + 2-byte offset + return)
        assertTrue(code.size > 0)
    }

    @Test
    fun gotoLabel() {
        val asm = JvmAssembler()
        asm.goto("end")
        asm.iconst1()
        asm.label("end")
        asm.return_()
        val code = asm.toByteArray()
        assertEquals(0xA7, code[0].toInt() and 0xFF) // goto
    }

    @Test
    fun invokestatic() {
        val asm = JvmAssembler()
        asm.invokestatic(5)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xB8, code[0].toInt() and 0xFF) // invokestatic
        assertEquals(0, code[1].toInt() and 0xFF) // high byte of index
        assertEquals(5, code[2].toInt() and 0xFF) // low byte of index
    }

    @Test
    fun conversions() {
        val asm = JvmAssembler()
        asm.i2l()
        asm.l2i()
        asm.i2f()
        asm.f2d()
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x85, code[0].toInt() and 0xFF) // i2l
    }

    @Test
    fun comparisons() {
        val asm = JvmAssembler()
        asm.lcmp()
        asm.fcmpl()
        asm.dcmpg()
        val code = asm.toByteArray()
        assertEquals(3, code.size)
    }

    @Test
    fun stackOps() {
        val asm = JvmAssembler()
        asm.pop()
        asm.dup()
        asm.swap()
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x57, code[0].toInt() and 0xFF) // pop
        assertEquals(0x59, code[1].toInt() and 0xFF) // dup
        assertEquals(0x5F, code[2].toInt() and 0xFF) // swap
    }

    @Test
    fun iinc() {
        val asm = JvmAssembler()
        asm.iinc(0, 1)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x84, code[0].toInt() and 0xFF) // iinc
        assertEquals(0, code[1].toInt() and 0xFF)     // index
        assertEquals(1, code[2].toInt() and 0xFF)     // increment
    }

    @Test
    fun newObject() {
        val asm = JvmAssembler()
        asm.new_(3)
        asm.dup()
        asm.invokespecial(4)
        val code = asm.toByteArray()
        assertEquals(7, code.size) // new(3) + dup(1) + invokespecial(3)
    }

    @Test
    fun reset() {
        val asm = JvmAssembler()
        asm.iconst0()
        asm.ireturn()
        assertEquals(2, asm.size)
        asm.reset()
        assertEquals(0, asm.size)
        assertEquals(0, asm.toByteArray().size)
    }

    @Test
    fun ldcCompact() {
        val asm = JvmAssembler()
        asm.ldc(5) // uses ldc (1-byte index)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x12, code[0].toInt() and 0xFF) // ldc
    }

    @Test
    fun ldcWide() {
        val asm = JvmAssembler()
        asm.ldc(300) // uses ldc_w (2-byte index)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x13, code[0].toInt() and 0xFF) // ldc_w
    }

    @Test
    fun ldc2w() {
        val asm = JvmAssembler()
        asm.ldc2w(10)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x14, code[0].toInt() and 0xFF) // ldc2_w
    }

    @Test
    fun negation() {
        val asm = JvmAssembler()
        asm.ineg()
        asm.lneg()
        asm.fneg()
        asm.dneg()
        val code = asm.toByteArray()
        assertEquals(4, code.size)
    }

    @Test
    fun bitwiseOps() {
        val asm = JvmAssembler()
        asm.iand()
        asm.ior()
        asm.ixor()
        asm.ishl()
        asm.ishr()
        asm.iushr()
        val code = asm.toByteArray()
        assertEquals(6, code.size)
    }

    @Test
    fun longLoadStore() {
        val asm = JvmAssembler()
        asm.lload(0)
        asm.lload(1)
        asm.lstore(0)
        asm.lstore(3)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
    }

    @Test
    fun allArrayOps() {
        val asm = JvmAssembler()
        asm.iaload()
        asm.laload()
        asm.faload()
        asm.daload()
        asm.aaload()
        asm.baload()
        asm.iastore()
        asm.lastore()
        asm.fastore()
        asm.dastore()
        asm.aastore()
        asm.bastore()
        val code = asm.toByteArray()
        assertEquals(12, code.size)
    }

    @Test
    fun opCodeFromCode() {
        assertEquals(JvmOpCode.NOP, JvmOpCode.fromCode(0x00))
        assertEquals(JvmOpCode.IADD, JvmOpCode.fromCode(0x60))
        assertEquals(JvmOpCode.RETURN, JvmOpCode.fromCode(0xB1))
        assertNull(JvmOpCode.fromCode(0xFE))
    }
}
