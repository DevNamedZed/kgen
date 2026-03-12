package org.kgen.target.clr

import org.junit.jupiter.api.Test
import org.kgen.target.clr.asm.CilAssembler
import org.kgen.target.clr.asm.CilToken
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CilAssemblerTest {

    @Test
    fun emitSimpleAdd() {
        val asm = CilAssembler()
        asm.ldarg(0)
        asm.ldarg(1)
        asm.add()
        asm.ret()

        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x02, code[0].toInt() and 0xFF) // ldarg.0
        assertEquals(0x03, code[1].toInt() and 0xFF) // ldarg.1
        assertEquals(0x58, code[2].toInt() and 0xFF) // add
        assertEquals(0x2A, code[3].toInt() and 0xFF) // ret
    }

    @Test
    fun emitLdcI4Variants() {
        val asm = CilAssembler()
        asm.ldcI4Auto(-1) // ldc.i4.m1
        asm.ldcI4Auto(0)  // ldc.i4.0
        asm.ldcI4Auto(8)  // ldc.i4.8
        asm.ldcI4Auto(42) // ldc.i4.s 42
        asm.ldcI4Auto(1000) // ldc.i4 1000

        val code = asm.toByteArray()
        assertEquals(0x15, code[0].toInt() and 0xFF) // ldc.i4.m1
        assertEquals(0x16, code[1].toInt() and 0xFF) // ldc.i4.0
        assertEquals(0x1E, code[2].toInt() and 0xFF) // ldc.i4.8
        assertEquals(0x1F, code[3].toInt() and 0xFF) // ldc.i4.s
        assertEquals(42, code[4].toInt())             // 42
        assertEquals(0x20, code[5].toInt() and 0xFF) // ldc.i4
    }

    @Test
    fun emitLocalAccess() {
        val asm = CilAssembler()
        asm.ldloc(0) // ldloc.0
        asm.ldloc(3) // ldloc.3
        asm.ldloc(10) // ldloc.s 10
        asm.stloc(0) // stloc.0
        asm.stloc(2) // stloc.2

        val code = asm.toByteArray()
        assertEquals(0x06, code[0].toInt() and 0xFF) // ldloc.0
        assertEquals(0x09, code[1].toInt() and 0xFF) // ldloc.3
        assertEquals(0x11, code[2].toInt() and 0xFF) // ldloc.s
        assertEquals(10, code[3].toInt() and 0xFF)
        assertEquals(0x0A, code[4].toInt() and 0xFF) // stloc.0
        assertEquals(0x0C, code[5].toInt() and 0xFF) // stloc.2
    }

    @Test
    fun emitTwoByteOpcode() {
        val asm = CilAssembler()
        asm.ldarg(0)
        asm.ldarg(1)
        asm.ceq() // 0xFE 0x01
        asm.ret()

        val code = asm.toByteArray()
        assertEquals(0xFE, code[2].toInt() and 0xFF) // prefix
        assertEquals(0x01, code[3].toInt() and 0xFF) // ceq second byte
        assertEquals(0x2A, code[4].toInt() and 0xFF) // ret
    }

    @Test
    fun emitBranchShort() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.ldarg(0)           // 0: ldarg.0
        asm.brtrueS(target)    // 1: brtrue.s +offset
        asm.ldcI4Auto(0)       // 3: ldc.i4.0
        asm.ret()              // 4: ret
        asm.markLabel(target)  // 5:
        asm.ldcI4Auto(1)       // 5: ldc.i4.1
        asm.ret()              // 6: ret

        val code = asm.toByteArray()
        assertEquals(0x2D, code[1].toInt() and 0xFF) // brtrue.s
        // Branch offset: target(5) - (patchOffset(2) + 1) = 5 - 3 = 2
        assertEquals(2, code[2].toInt())
    }

    @Test
    fun emitBranchLong() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.ldarg(0)          // 0: ldarg.0
        asm.brtrue(target)    // 1: brtrue +offset (5 bytes)
        asm.ldcI4Auto(0)      // 6: ldc.i4.0
        asm.ret()             // 7: ret
        asm.markLabel(target) // 8:
        asm.ldcI4Auto(1)      // 8: ldc.i4.1
        asm.ret()             // 9: ret

        val code = asm.toByteArray()
        assertEquals(0x3A, code[1].toInt() and 0xFF) // brtrue
        // Branch offset: target(8) - (patchOffset(2) + 4) = 8 - 6 = 2
        assertEquals(2, code[2].toInt())
    }

    @Test
    fun emitFieldAccess() {
        val asm = CilAssembler()
        val field1 = CilToken.field(1)
        val field2 = CilToken.field(2)
        asm.ldsfld(field1)
        asm.stsfld(field2)

        val code = asm.toByteArray()
        assertEquals(0x7E, code[0].toInt() and 0xFF) // ldsfld
        // Token bytes (little-endian): 01 00 00 04
        assertEquals(0x01, code[1].toInt() and 0xFF)
        assertEquals(0x00, code[2].toInt() and 0xFF)
        assertEquals(0x00, code[3].toInt() and 0xFF)
        assertEquals(0x04, code[4].toInt() and 0xFF)
    }

    @Test
    fun emitArithmetic() {
        val asm = CilAssembler()
        asm.add()
        asm.sub()
        asm.mul()
        asm.div()
        asm.rem()
        asm.neg()
        asm.and()
        asm.or()
        asm.xor()
        asm.not()
        asm.shl()
        asm.shr()
        asm.shrUn()

        val code = asm.toByteArray()
        assertEquals(13, code.size)
        assertEquals(0x58, code[0].toInt() and 0xFF) // add
        assertEquals(0x59, code[1].toInt() and 0xFF) // sub
        assertEquals(0x5A, code[2].toInt() and 0xFF) // mul
        assertEquals(0x5B, code[3].toInt() and 0xFF) // div
    }

    @Test
    fun emitConversions() {
        val asm = CilAssembler()
        asm.convI4()
        asm.convI8()
        asm.convR4()
        asm.convR8()

        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x69, code[0].toInt() and 0xFF) // conv.i4
        assertEquals(0x6A, code[1].toInt() and 0xFF) // conv.i8
        assertEquals(0x6B, code[2].toInt() and 0xFF) // conv.r4
        assertEquals(0x6C, code[3].toInt() and 0xFF) // conv.r8
    }

    @Test
    fun emitLdcI8() {
        val asm = CilAssembler()
        asm.ldcI8(0x0102030405060708L)

        val code = asm.toByteArray()
        assertEquals(9, code.size) // opcode + 8 bytes
        assertEquals(0x21, code[0].toInt() and 0xFF) // ldc.i8
        // Little-endian
        assertEquals(0x08, code[1].toInt() and 0xFF)
        assertEquals(0x07, code[2].toInt() and 0xFF)
        assertEquals(0x06, code[3].toInt() and 0xFF)
        assertEquals(0x05, code[4].toInt() and 0xFF)
    }

    @Test
    fun emitCallWithToken() {
        val asm = CilAssembler()
        val token = CilToken.memberRef(1)
        asm.call(token)

        val code = asm.toByteArray()
        assertEquals(5, code.size)
        assertEquals(0x28, code[0].toInt() and 0xFF) // call
    }

    @Test
    fun backwardBranch() {
        val asm = CilAssembler()
        val loop = asm.defineLabel()
        asm.markLabel(loop)
        asm.ldloc(0)
        asm.ldarg(0)
        asm.clt()
        asm.brtrueS(loop)

        val code = asm.toByteArray()
        assertEquals(0x2D, code[4].toInt() and 0xFF) // brtrue.s
        assertTrue(code[5].toInt() < 0, "Expected negative offset for backward branch")
    }

    @Test
    fun sizeTracking() {
        val asm = CilAssembler()
        assertEquals(0, asm.size)
        asm.nop()
        assertEquals(1, asm.size)
        asm.ldcI4(42)
        assertEquals(6, asm.size) // nop(1) + ldc.i4(1) + i32(4)
    }
}
