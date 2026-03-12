package org.kgen.target.clr

import org.junit.jupiter.api.Test
import org.kgen.target.clr.asm.CilAssembler
import org.kgen.target.clr.asm.CilDisassembler
import org.kgen.target.clr.asm.CilToken
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CilDisassemblerTest {

    @Test
    fun disassembleSimpleAdd() {
        val asm = CilAssembler()
        asm.ldarg(0)
        asm.ldarg(1)
        asm.add()
        asm.ret()

        val instructions = CilDisassembler().disassemble(asm.toByteArray())
        assertEquals(4, instructions.size)
        assertEquals(CilOpCode.LDARG_0, instructions[0].opcode)
        assertEquals(CilOpCode.LDARG_1, instructions[1].opcode)
        assertEquals(CilOpCode.ADD, instructions[2].opcode)
        assertEquals(CilOpCode.RET, instructions[3].opcode)
    }

    @Test
    fun disassembleLdcI4() {
        val asm = CilAssembler()
        asm.ldcI4Auto(0)
        asm.ldcI4Auto(42)
        asm.ldcI4Auto(1000)

        val instructions = CilDisassembler().disassemble(asm.toByteArray())
        assertEquals(3, instructions.size)
        assertEquals(CilOpCode.LDC_I4_0, instructions[0].opcode)
        assertEquals(CilOpCode.LDC_I4_S, instructions[1].opcode)
        assertEquals(42, instructions[1].operand)
        assertEquals(CilOpCode.LDC_I4, instructions[2].opcode)
        assertEquals(1000, instructions[2].operand)
    }

    @Test
    fun disassembleTwoByteOpcode() {
        val asm = CilAssembler()
        asm.ceq()
        asm.cgt()
        asm.clt()

        val instructions = CilDisassembler().disassemble(asm.toByteArray())
        assertEquals(3, instructions.size)
        assertEquals(CilOpCode.CEQ, instructions[0].opcode)
        assertEquals(CilOpCode.CGT, instructions[1].opcode)
        assertEquals(CilOpCode.CLT, instructions[2].opcode)
    }

    @Test
    fun disassembleBranch() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.ldarg(0)
        asm.brtrueS(target)
        asm.ldcI4Auto(0)
        asm.ret()
        asm.markLabel(target)
        asm.ldcI4Auto(1)
        asm.ret()

        val instructions = CilDisassembler().disassemble(asm.toByteArray())
        assertEquals(6, instructions.size)
        assertEquals(CilOpCode.BRTRUE_S, instructions[1].opcode)
        // Branch target should display as IL_0005
        assertTrue(instructions[1].toString().contains("IL_0005"))
    }

    @Test
    fun disassembleCallWithToken() {
        val asm = CilAssembler()
        asm.call(CilToken.memberRef(1))

        val instructions = CilDisassembler().disassemble(asm.toByteArray())
        assertEquals(1, instructions.size)
        assertEquals(CilOpCode.CALL, instructions[0].opcode)
        assertEquals(0x0A000001, instructions[0].operand.toInt())
    }

    @Test
    fun disassembleLdcI8() {
        val asm = CilAssembler()
        asm.ldcI8(123456789L)

        val instructions = CilDisassembler().disassemble(asm.toByteArray())
        assertEquals(1, instructions.size)
        assertEquals(CilOpCode.LDC_I8, instructions[0].opcode)
        assertEquals(123456789L, instructions[0].operand)
    }

    @Test
    fun roundTripAssembleDisassemble() {
        val asm = CilAssembler()
        asm.nop()
        asm.ldarg(0)
        asm.ldarg(1)
        asm.add()
        asm.ldloc(5)
        asm.stloc(0)
        asm.ceq()
        asm.ret()

        val code = asm.toByteArray()
        val instructions = CilDisassembler().disassemble(code)

        assertEquals(8, instructions.size)
        assertEquals(CilOpCode.NOP, instructions[0].opcode)
        assertEquals(CilOpCode.LDARG_0, instructions[1].opcode)
        assertEquals(CilOpCode.LDARG_1, instructions[2].opcode)
        assertEquals(CilOpCode.ADD, instructions[3].opcode)
        assertEquals(CilOpCode.LDLOC_S, instructions[4].opcode)
        assertEquals(5, instructions[4].operand)
        assertEquals(CilOpCode.STLOC_0, instructions[5].opcode)
        assertEquals(CilOpCode.CEQ, instructions[6].opcode)
        assertEquals(CilOpCode.RET, instructions[7].opcode)
    }

    @Test
    fun offsets() {
        val asm = CilAssembler()
        asm.nop()         // 0: 1 byte
        asm.ldcI4(42)     // 1: 5 bytes
        asm.ceq()         // 6: 2 bytes (0xFE prefix)
        asm.ret()         // 8: 1 byte

        val instructions = CilDisassembler().disassemble(asm.toByteArray())
        assertEquals(0, instructions[0].offset)
        assertEquals(1, instructions[1].offset)
        assertEquals(6, instructions[2].offset)
        assertEquals(8, instructions[3].offset)
    }

    @Test
    fun instructionToString() {
        val asm = CilAssembler()
        asm.ldarg(0)
        asm.ldcI4Auto(42)
        asm.add()
        asm.call(CilToken.methodDef(1))
        asm.ret()

        val instructions = CilDisassembler().disassemble(asm.toByteArray())
        assertEquals("ldarg.0", instructions[0].toString())
        assertEquals("ldc.i4.s 42", instructions[1].toString())
        assertEquals("add", instructions[2].toString())
        assertTrue(instructions[3].toString().startsWith("call"))
        assertEquals("ret", instructions[4].toString())
    }
}
