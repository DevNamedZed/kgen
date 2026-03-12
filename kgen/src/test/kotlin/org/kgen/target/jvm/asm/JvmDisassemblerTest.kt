package org.kgen.target.jvm.asm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.target.jvm.JvmOpCode

class JvmDisassemblerTest {

    private val dis = JvmDisassembler()

    @Test
    fun disassembleEmpty() {
        val result = dis.disassemble(ByteArray(0))
        assertTrue(result.isEmpty())
    }

    @Test
    fun disassembleSimple() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.iadd()
        asm.ireturn()
        val code = asm.toByteArray()

        val instructions = dis.disassemble(code)
        assertEquals(4, instructions.size)
        assertEquals(JvmOpCode.ILOAD_0, instructions[0].opcode)
        assertEquals(JvmOpCode.ILOAD_1, instructions[1].opcode)
        assertEquals(JvmOpCode.IADD, instructions[2].opcode)
        assertEquals(JvmOpCode.IRETURN, instructions[3].opcode)
    }

    @Test
    fun disassembleBipush() {
        val asm = JvmAssembler()
        asm.bipush(42)
        val code = asm.toByteArray()

        val instructions = dis.disassemble(code)
        assertEquals(1, instructions.size)
        assertEquals(JvmOpCode.BIPUSH, instructions[0].opcode)
        assertEquals("42", instructions[0].operands)
        assertEquals(2, instructions[0].size)
    }

    @Test
    fun disassembleSipush() {
        val asm = JvmAssembler()
        asm.sipush(1000)
        val code = asm.toByteArray()

        val instructions = dis.disassemble(code)
        assertEquals(1, instructions.size)
        assertEquals(JvmOpCode.SIPUSH, instructions[0].opcode)
        assertEquals("1000", instructions[0].operands)
    }

    @Test
    fun disassembleLdc() {
        val asm = JvmAssembler()
        asm.ldc(5)
        asm.ldc(300) // ldc_w
        val code = asm.toByteArray()

        val instructions = dis.disassemble(code)
        assertEquals(2, instructions.size)
        assertEquals(JvmOpCode.LDC, instructions[0].opcode)
        assertEquals("#5", instructions[0].operands)
        assertEquals(JvmOpCode.LDC_W, instructions[1].opcode)
        assertEquals("#300", instructions[1].operands)
    }

    @Test
    fun disassembleBranch() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.ifeq("skip")
        asm.iconst1()
        asm.ireturn()
        asm.label("skip")
        asm.iconst0()
        asm.ireturn()
        val code = asm.toByteArray()

        val instructions = dis.disassemble(code)
        // Find the ifeq instruction
        val ifeq = instructions.first { it.opcode == JvmOpCode.IFEQ }
        // Its operand should be the target offset (skip label)
        assertEquals("6", ifeq.operands) // offset 1 (ifeq) + 5 = 6
    }

    @Test
    fun disassembleIinc() {
        val asm = JvmAssembler()
        asm.iinc(0, 1)
        val code = asm.toByteArray()

        val instructions = dis.disassemble(code)
        assertEquals(1, instructions.size)
        assertEquals(JvmOpCode.IINC, instructions[0].opcode)
        assertEquals("0, 1", instructions[0].operands)
    }

    @Test
    fun disassembleInvoke() {
        val asm = JvmAssembler()
        asm.invokestatic(10)
        asm.invokevirtual(20)
        val code = asm.toByteArray()

        val instructions = dis.disassemble(code)
        assertEquals(2, instructions.size)
        assertEquals(JvmOpCode.INVOKESTATIC, instructions[0].opcode)
        assertEquals("#10", instructions[0].operands)
        assertEquals(JvmOpCode.INVOKEVIRTUAL, instructions[1].opcode)
        assertEquals("#20", instructions[1].operands)
    }

    @Test
    fun roundTrip() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.iadd()
        asm.istore(2)
        asm.iload(2)
        asm.ireturn()
        val code = asm.toByteArray()

        val instructions = dis.disassemble(code)
        assertEquals(6, instructions.size)

        // Verify offsets are contiguous
        var expectedOffset = 0
        for (insn in instructions) {
            assertEquals(expectedOffset, insn.offset)
            expectedOffset += insn.size
        }
        assertEquals(code.size, expectedOffset)
    }

    @Test
    fun toStringFormat() {
        val insn = JvmDisassembler.JvmInstruction(0, JvmOpCode.IADD, "", 1)
        assertEquals("0: iadd", insn.toString())

        val insn2 = JvmDisassembler.JvmInstruction(5, JvmOpCode.BIPUSH, "42", 2)
        assertEquals("5: bipush 42", insn2.toString())
    }

    @Test
    fun disassembleConversions() {
        val asm = JvmAssembler()
        asm.i2l()
        asm.l2i()
        asm.i2f()
        asm.f2d()
        val code = asm.toByteArray()

        val instructions = dis.disassemble(code)
        assertEquals(4, instructions.size)
        assertEquals(JvmOpCode.I2L, instructions[0].opcode)
        assertEquals(JvmOpCode.L2I, instructions[1].opcode)
    }

    @Test
    fun disassembleComparisons() {
        val asm = JvmAssembler()
        asm.lcmp()
        asm.fcmpl()
        asm.dcmpg()
        val code = asm.toByteArray()

        val instructions = dis.disassemble(code)
        assertEquals(3, instructions.size)
    }

    @Test
    fun disassembleAllReturns() {
        val asm = JvmAssembler()
        asm.ireturn()
        asm.lreturn()
        asm.freturn()
        asm.dreturn()
        asm.areturn()
        asm.return_()
        val code = asm.toByteArray()

        val instructions = dis.disassemble(code)
        assertEquals(6, instructions.size)
    }

    @Test
    fun disassembleLoadVariants() {
        val asm = JvmAssembler()
        asm.iload(0)  // iload_0
        asm.lload(1)  // lload_1
        asm.fload(2)  // fload_2
        asm.dload(3)  // dload_3
        asm.aload(0)  // aload_0
        asm.iload(10) // iload 10
        val code = asm.toByteArray()

        val instructions = dis.disassemble(code)
        assertEquals(6, instructions.size)
        assertEquals(JvmOpCode.ILOAD_0, instructions[0].opcode)
        assertEquals(JvmOpCode.LLOAD_1, instructions[1].opcode)
        assertEquals(JvmOpCode.FLOAD_2, instructions[2].opcode)
        assertEquals(JvmOpCode.DLOAD_3, instructions[3].opcode)
        assertEquals(JvmOpCode.ALOAD_0, instructions[4].opcode)
        assertEquals(JvmOpCode.ILOAD, instructions[5].opcode)
        assertEquals("10", instructions[5].operands)
    }
}
