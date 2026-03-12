package org.kgen.target.clr

import org.junit.jupiter.api.Test
import org.kgen.target.clr.asm.CilAssembler
import org.kgen.target.clr.asm.CilToken
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CilAssemblerComprehensiveTest {

    private fun byte(code: ByteArray, index: Int): Int = code[index].toInt() and 0xFF

    private fun i32LE(code: ByteArray, offset: Int): Int =
        (byte(code, offset)) or
            (byte(code, offset + 1) shl 8) or
            (byte(code, offset + 2) shl 16) or
            (byte(code, offset + 3) shl 24)

    private fun i64LE(code: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) result = result or ((byte(code, offset + i).toLong()) shl (i * 8))
        return result
    }

    @Test
    fun `add emits 0x58`() {
        val asm = CilAssembler()
        asm.add()
        val code = asm.toByteArray()
        assertEquals(1, code.size)
        assertEquals(0x58, byte(code, 0))
    }

    @Test
    fun `sub emits 0x59`() {
        val asm = CilAssembler()
        asm.sub()
        assertEquals(0x59, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `mul emits 0x5A`() {
        val asm = CilAssembler()
        asm.mul()
        assertEquals(0x5A, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `div emits 0x5B`() {
        val asm = CilAssembler()
        asm.div()
        assertEquals(0x5B, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `div un emits 0x5C`() {
        val asm = CilAssembler()
        asm.divUn()
        assertEquals(0x5C, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `rem emits 0x5D`() {
        val asm = CilAssembler()
        asm.rem()
        assertEquals(0x5D, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `rem un emits 0x5E`() {
        val asm = CilAssembler()
        asm.remUn()
        assertEquals(0x5E, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `neg emits 0x65`() {
        val asm = CilAssembler()
        asm.neg()
        assertEquals(0x65, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `all arithmetic opcodes in sequence`() {
        val asm = CilAssembler()
        asm.add(); asm.sub(); asm.mul(); asm.div(); asm.divUn()
        asm.rem(); asm.remUn(); asm.neg()
        val code = asm.toByteArray()
        assertEquals(8, code.size)
        assertEquals(0x58, byte(code, 0))
        assertEquals(0x59, byte(code, 1))
        assertEquals(0x5A, byte(code, 2))
        assertEquals(0x5B, byte(code, 3))
        assertEquals(0x5C, byte(code, 4))
        assertEquals(0x5D, byte(code, 5))
        assertEquals(0x5E, byte(code, 6))
        assertEquals(0x65, byte(code, 7))
    }

    @Test
    fun `overflow arithmetic add ovf`() {
        val asm = CilAssembler()
        asm.addOvf()
        assertEquals(0xD6, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `overflow arithmetic add ovf un`() {
        val asm = CilAssembler()
        asm.addOvfUn()
        assertEquals(0xD7, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `overflow arithmetic sub ovf`() {
        val asm = CilAssembler()
        asm.subOvf()
        assertEquals(0xDA, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `overflow arithmetic sub ovf un`() {
        val asm = CilAssembler()
        asm.subOvfUn()
        assertEquals(0xDB, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `overflow arithmetic mul ovf`() {
        val asm = CilAssembler()
        asm.mulOvf()
        assertEquals(0xD8, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `overflow arithmetic mul ovf un`() {
        val asm = CilAssembler()
        asm.mulOvfUn()
        assertEquals(0xD9, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `and emits 0x5F`() {
        val asm = CilAssembler()
        asm.and()
        assertEquals(0x5F, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `or emits 0x60`() {
        val asm = CilAssembler()
        asm.or()
        assertEquals(0x60, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `xor emits 0x61`() {
        val asm = CilAssembler()
        asm.xor()
        assertEquals(0x61, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `not emits 0x66`() {
        val asm = CilAssembler()
        asm.not()
        assertEquals(0x66, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `shl emits 0x62`() {
        val asm = CilAssembler()
        asm.shl()
        assertEquals(0x62, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `shr emits 0x63`() {
        val asm = CilAssembler()
        asm.shr()
        assertEquals(0x63, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `shr un emits 0x64`() {
        val asm = CilAssembler()
        asm.shrUn()
        assertEquals(0x64, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `all bitwise opcodes in sequence`() {
        val asm = CilAssembler()
        asm.and(); asm.or(); asm.xor(); asm.not(); asm.shl(); asm.shr(); asm.shrUn()
        val code = asm.toByteArray()
        assertEquals(7, code.size)
        assertEquals(0x5F, byte(code, 0))
        assertEquals(0x60, byte(code, 1))
        assertEquals(0x61, byte(code, 2))
        assertEquals(0x66, byte(code, 3))
        assertEquals(0x62, byte(code, 4))
        assertEquals(0x63, byte(code, 5))
        assertEquals(0x64, byte(code, 6))
    }

    @Test
    fun `ceq emits two-byte 0xFE 0x01`() {
        val asm = CilAssembler()
        asm.ceq()
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x01, byte(code, 1))
    }

    @Test
    fun `cgt emits two-byte 0xFE 0x02`() {
        val asm = CilAssembler()
        asm.cgt()
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x02, byte(code, 1))
    }

    @Test
    fun `cgt un emits two-byte 0xFE 0x03`() {
        val asm = CilAssembler()
        asm.cgtUn()
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x03, byte(code, 1))
    }

    @Test
    fun `clt emits two-byte 0xFE 0x04`() {
        val asm = CilAssembler()
        asm.clt()
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x04, byte(code, 1))
    }

    @Test
    fun `clt un emits two-byte 0xFE 0x05`() {
        val asm = CilAssembler()
        asm.cltUn()
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x05, byte(code, 1))
    }

    @Test
    fun `all comparison opcodes in sequence`() {
        val asm = CilAssembler()
        asm.ceq(); asm.cgt(); asm.cgtUn(); asm.clt(); asm.cltUn()
        val code = asm.toByteArray()
        assertEquals(10, code.size)
        for (i in 0 until 5) assertEquals(0xFE, byte(code, i * 2))
        assertEquals(0x01, byte(code, 1))
        assertEquals(0x02, byte(code, 3))
        assertEquals(0x03, byte(code, 5))
        assertEquals(0x04, byte(code, 7))
        assertEquals(0x05, byte(code, 9))
    }

    @Test
    fun `conv i1 emits 0x67`() {
        val asm = CilAssembler()
        asm.convI1()
        assertEquals(0x67, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `conv i2 emits 0x68`() {
        val asm = CilAssembler()
        asm.convI2()
        assertEquals(0x68, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `conv i4 emits 0x69`() {
        val asm = CilAssembler()
        asm.convI4()
        assertEquals(0x69, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `conv i8 emits 0x6A`() {
        val asm = CilAssembler()
        asm.convI8()
        assertEquals(0x6A, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `conv r4 emits 0x6B`() {
        val asm = CilAssembler()
        asm.convR4()
        assertEquals(0x6B, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `conv r8 emits 0x6C`() {
        val asm = CilAssembler()
        asm.convR8()
        assertEquals(0x6C, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `conv u1 emits 0xD2`() {
        val asm = CilAssembler()
        asm.convU1()
        assertEquals(0xD2, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `conv u2 emits 0xD1`() {
        val asm = CilAssembler()
        asm.convU2()
        assertEquals(0xD1, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `conv u4 emits 0x6D`() {
        val asm = CilAssembler()
        asm.convU4()
        assertEquals(0x6D, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `conv u8 emits 0x6E`() {
        val asm = CilAssembler()
        asm.convU8()
        assertEquals(0x6E, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `conv i emits 0xD3`() {
        val asm = CilAssembler()
        asm.convI()
        assertEquals(0xD3, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `conv u emits 0xE0`() {
        val asm = CilAssembler()
        asm.convU()
        assertEquals(0xE0, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `conv r un emits 0x76`() {
        val asm = CilAssembler()
        asm.convRUn()
        assertEquals(0x76, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `all conversion opcodes in sequence`() {
        val asm = CilAssembler()
        asm.convI1(); asm.convI2(); asm.convI4(); asm.convI8()
        asm.convR4(); asm.convR8(); asm.convU4(); asm.convU8()
        val code = asm.toByteArray()
        assertEquals(8, code.size)
        assertEquals(0x67, byte(code, 0))
        assertEquals(0x68, byte(code, 1))
        assertEquals(0x69, byte(code, 2))
        assertEquals(0x6A, byte(code, 3))
        assertEquals(0x6B, byte(code, 4))
        assertEquals(0x6C, byte(code, 5))
        assertEquals(0x6D, byte(code, 6))
        assertEquals(0x6E, byte(code, 7))
    }

    @Test
    fun `ldc i4 m1 emits 0x15`() {
        val asm = CilAssembler()
        asm.ldcI4M1()
        assertEquals(0x15, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldc i4 0 emits 0x16`() {
        val asm = CilAssembler()
        asm.ldcI4_0()
        assertEquals(0x16, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldc i4 1 emits 0x17`() {
        val asm = CilAssembler()
        asm.ldcI4_1()
        assertEquals(0x17, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldc i4 2 emits 0x18`() {
        val asm = CilAssembler()
        asm.ldcI4_2()
        assertEquals(0x18, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldc i4 3 emits 0x19`() {
        val asm = CilAssembler()
        asm.ldcI4_3()
        assertEquals(0x19, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldc i4 4 emits 0x1A`() {
        val asm = CilAssembler()
        asm.ldcI4_4()
        assertEquals(0x1A, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldc i4 5 emits 0x1B`() {
        val asm = CilAssembler()
        asm.ldcI4_5()
        assertEquals(0x1B, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldc i4 6 emits 0x1C`() {
        val asm = CilAssembler()
        asm.ldcI4_6()
        assertEquals(0x1C, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldc i4 7 emits 0x1D`() {
        val asm = CilAssembler()
        asm.ldcI4_7()
        assertEquals(0x1D, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldc i4 8 emits 0x1E`() {
        val asm = CilAssembler()
        asm.ldcI4_8()
        assertEquals(0x1E, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldc i4 s emits opcode and signed byte`() {
        val asm = CilAssembler()
        asm.ldcI4S(42)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x1F, byte(code, 0))
        assertEquals(42, code[1].toInt())
    }

    @Test
    fun `ldc i4 s negative value`() {
        val asm = CilAssembler()
        asm.ldcI4S(-5)
        val code = asm.toByteArray()
        assertEquals(0x1F, byte(code, 0))
        assertEquals(-5, code[1].toInt())
    }

    @Test
    fun `ldc i4 emits opcode and 4-byte value`() {
        val asm = CilAssembler()
        asm.ldcI4(0x12345678)
        val code = asm.toByteArray()
        assertEquals(5, code.size)
        assertEquals(0x20, byte(code, 0))
        assertEquals(0x78, byte(code, 1))
        assertEquals(0x56, byte(code, 2))
        assertEquals(0x34, byte(code, 3))
        assertEquals(0x12, byte(code, 4))
    }

    @Test
    fun `ldc i4 auto selects m1 for minus one`() {
        val asm = CilAssembler()
        asm.ldcI4Auto(-1)
        assertEquals(1, asm.toByteArray().size)
        assertEquals(0x15, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldc i4 auto selects compact for 0 through 8`() {
        for (i in 0..8) {
            val asm = CilAssembler()
            asm.ldcI4Auto(i)
            val code = asm.toByteArray()
            assertEquals(1, code.size, "ldc.i4.$i should be 1 byte")
            assertEquals(0x16 + i, byte(code, 0))
        }
    }

    @Test
    fun `ldc i4 auto selects short for small values`() {
        val asm = CilAssembler()
        asm.ldcI4Auto(100)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x1F, byte(code, 0))
        assertEquals(100, code[1].toInt())
    }

    @Test
    fun `ldc i4 auto selects short for negative byte range`() {
        val asm = CilAssembler()
        asm.ldcI4Auto(-128)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x1F, byte(code, 0))
    }

    @Test
    fun `ldc i4 auto selects full for large values`() {
        val asm = CilAssembler()
        asm.ldcI4Auto(1000)
        val code = asm.toByteArray()
        assertEquals(5, code.size)
        assertEquals(0x20, byte(code, 0))
    }

    @Test
    fun `ldc i4 auto selects full for negative out of byte range`() {
        val asm = CilAssembler()
        asm.ldcI4Auto(-200)
        val code = asm.toByteArray()
        assertEquals(5, code.size)
        assertEquals(0x20, byte(code, 0))
    }

    @Test
    fun `ldc i8 emits opcode and 8-byte little endian`() {
        val asm = CilAssembler()
        asm.ldcI8(0x0102030405060708L)
        val code = asm.toByteArray()
        assertEquals(9, code.size)
        assertEquals(0x21, byte(code, 0))
        assertEquals(0x08, byte(code, 1))
        assertEquals(0x07, byte(code, 2))
        assertEquals(0x06, byte(code, 3))
        assertEquals(0x05, byte(code, 4))
        assertEquals(0x04, byte(code, 5))
        assertEquals(0x03, byte(code, 6))
        assertEquals(0x02, byte(code, 7))
        assertEquals(0x01, byte(code, 8))
    }

    @Test
    fun `ldc i8 zero`() {
        val asm = CilAssembler()
        asm.ldcI8(0L)
        val code = asm.toByteArray()
        assertEquals(9, code.size)
        assertEquals(0x21, byte(code, 0))
        assertEquals(0L, i64LE(code, 1))
    }

    @Test
    fun `ldc i8 max value`() {
        val asm = CilAssembler()
        asm.ldcI8(Long.MAX_VALUE)
        val code = asm.toByteArray()
        assertEquals(Long.MAX_VALUE, i64LE(code, 1))
    }

    @Test
    fun `ldc r4 emits float bits`() {
        val asm = CilAssembler()
        asm.ldcR4(3.14f)
        val code = asm.toByteArray()
        assertEquals(5, code.size)
        assertEquals(0x22, byte(code, 0))
        assertEquals(3.14f.toBits(), i32LE(code, 1))
    }

    @Test
    fun `ldc r8 emits double bits`() {
        val asm = CilAssembler()
        asm.ldcR8(3.14159265358979)
        val code = asm.toByteArray()
        assertEquals(9, code.size)
        assertEquals(0x23, byte(code, 0))
        assertEquals(3.14159265358979.toBits(), i64LE(code, 1))
    }

    @Test
    fun `ldc r4 zero`() {
        val asm = CilAssembler()
        asm.ldcR4(0.0f)
        val code = asm.toByteArray()
        assertEquals(0.0f.toBits(), i32LE(code, 1))
    }

    @Test
    fun `ldc r8 negative`() {
        val asm = CilAssembler()
        asm.ldcR8(-1.5)
        val code = asm.toByteArray()
        assertEquals((-1.5).toBits(), i64LE(code, 1))
    }

    @Test
    fun `ldnull emits 0x14`() {
        val asm = CilAssembler()
        asm.ldnull()
        assertEquals(0x14, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldloc 0 emits 0x06`() {
        val asm = CilAssembler()
        asm.ldloc(0)
        assertEquals(0x06, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldloc 1 emits 0x07`() {
        val asm = CilAssembler()
        asm.ldloc(1)
        assertEquals(0x07, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldloc 2 emits 0x08`() {
        val asm = CilAssembler()
        asm.ldloc(2)
        assertEquals(0x08, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldloc 3 emits 0x09`() {
        val asm = CilAssembler()
        asm.ldloc(3)
        assertEquals(0x09, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldloc s emits opcode and index for 4-255`() {
        val asm = CilAssembler()
        asm.ldloc(10)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x11, byte(code, 0))
        assertEquals(10, byte(code, 1))
    }

    @Test
    fun `ldloc s boundary index 255`() {
        val asm = CilAssembler()
        asm.ldloc(255)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x11, byte(code, 0))
        assertEquals(255, byte(code, 1))
    }

    @Test
    fun `ldloc long form for index above 255`() {
        val asm = CilAssembler()
        asm.ldloc(256)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x0C, byte(code, 1))
    }

    @Test
    fun `stloc 0 emits 0x0A`() {
        val asm = CilAssembler()
        asm.stloc(0)
        assertEquals(0x0A, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stloc 1 emits 0x0B`() {
        val asm = CilAssembler()
        asm.stloc(1)
        assertEquals(0x0B, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stloc 2 emits 0x0C`() {
        val asm = CilAssembler()
        asm.stloc(2)
        assertEquals(0x0C, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stloc 3 emits 0x0D`() {
        val asm = CilAssembler()
        asm.stloc(3)
        assertEquals(0x0D, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stloc s emits opcode and index`() {
        val asm = CilAssembler()
        asm.stloc(50)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x13, byte(code, 0))
        assertEquals(50, byte(code, 1))
    }

    @Test
    fun `stloc long form for index above 255`() {
        val asm = CilAssembler()
        asm.stloc(300)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x0E, byte(code, 1))
    }

    @Test
    fun `ldloca s emits 0x12 and index`() {
        val asm = CilAssembler()
        asm.ldloca(5)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x12, byte(code, 0))
        assertEquals(5, byte(code, 1))
    }

    @Test
    fun `ldloca long form for index above 255`() {
        val asm = CilAssembler()
        asm.ldloca(500)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x0D, byte(code, 1))
    }

    @Test
    fun `typed local ldloc stloc ldloca`() {
        val asm = CilAssembler()
        val local = asm.declareLocal(CilSigType.I4)
        asm.ldcI4Auto(42)
        asm.stloc(local)
        asm.ldloc(local)
        asm.ldloca(local)
        val code = asm.toByteArray()
        assertEquals(0x0A, byte(code, 2))  // stloc.0
        assertEquals(0x06, byte(code, 3))  // ldloc.0
        assertEquals(0x12, byte(code, 4))  // ldloca.s
        assertEquals(0, byte(code, 5))     // index 0
    }

    @Test
    fun `ldarg 0 emits 0x02`() {
        val asm = CilAssembler()
        asm.ldarg(0)
        assertEquals(0x02, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldarg 1 emits 0x03`() {
        val asm = CilAssembler()
        asm.ldarg(1)
        assertEquals(0x03, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldarg 2 emits 0x04`() {
        val asm = CilAssembler()
        asm.ldarg(2)
        assertEquals(0x04, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldarg 3 emits 0x05`() {
        val asm = CilAssembler()
        asm.ldarg(3)
        assertEquals(0x05, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldarg s emits 0x0E and index for 4-255`() {
        val asm = CilAssembler()
        asm.ldarg(4)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x0E, byte(code, 0))
        assertEquals(4, byte(code, 1))
    }

    @Test
    fun `ldarg long form for index above 255`() {
        val asm = CilAssembler()
        asm.ldarg(300)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x09, byte(code, 1))
    }

    @Test
    fun `starg s emits 0x10 and index`() {
        val asm = CilAssembler()
        asm.starg(7)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x10, byte(code, 0))
        assertEquals(7, byte(code, 1))
    }

    @Test
    fun `starg long form for index above 255`() {
        val asm = CilAssembler()
        asm.starg(400)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x0B, byte(code, 1))
    }

    @Test
    fun `ldarga s emits 0x0F and index`() {
        val asm = CilAssembler()
        asm.ldarga(3)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x0F, byte(code, 0))
        assertEquals(3, byte(code, 1))
    }

    @Test
    fun `ldarga long form for index above 255`() {
        val asm = CilAssembler()
        asm.ldarga(500)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x0A, byte(code, 1))
    }

    @Test
    fun `dup emits 0x25`() {
        val asm = CilAssembler()
        asm.dup()
        assertEquals(0x25, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `pop emits 0x26`() {
        val asm = CilAssembler()
        asm.pop()
        assertEquals(0x26, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `nop emits 0x00`() {
        val asm = CilAssembler()
        asm.nop()
        assertEquals(0x00, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stack operations sequence`() {
        val asm = CilAssembler()
        asm.nop(); asm.dup(); asm.pop()
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x00, byte(code, 0))
        assertEquals(0x25, byte(code, 1))
        assertEquals(0x26, byte(code, 2))
    }

    @Test
    fun `br s forward branch`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.brS(target)
        asm.nop()
        asm.markLabel(target)
        asm.ret()
        val code = asm.toByteArray()
        assertEquals(0x2B, byte(code, 0))
        assertEquals(1, code[1].toInt())
    }

    @Test
    fun `br long forward branch`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.br(target)
        asm.nop()
        asm.markLabel(target)
        asm.ret()
        val code = asm.toByteArray()
        assertEquals(0x38, byte(code, 0))
        assertEquals(1, i32LE(code, 1))
    }

    @Test
    fun `brfalse s emits 0x2C`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.brfalseS(target)
        asm.markLabel(target)
        val code = asm.toByteArray()
        assertEquals(0x2C, byte(code, 0))
    }

    @Test
    fun `brfalse emits 0x39`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.brfalse(target)
        asm.markLabel(target)
        val code = asm.toByteArray()
        assertEquals(0x39, byte(code, 0))
    }

    @Test
    fun `brtrue s emits 0x2D`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.brtrueS(target)
        asm.markLabel(target)
        val code = asm.toByteArray()
        assertEquals(0x2D, byte(code, 0))
    }

    @Test
    fun `brtrue emits 0x3A`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.brtrue(target)
        asm.markLabel(target)
        val code = asm.toByteArray()
        assertEquals(0x3A, byte(code, 0))
    }

    @Test
    fun `beq s emits 0x2E`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.beqS(target)
        asm.markLabel(target)
        assertEquals(0x2E, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `beq emits 0x3B`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.beq(target)
        asm.markLabel(target)
        assertEquals(0x3B, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `bne un s emits 0x33`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bneUnS(target)
        asm.markLabel(target)
        assertEquals(0x33, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `bne un emits 0x40`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bneUn(target)
        asm.markLabel(target)
        assertEquals(0x40, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `blt s emits 0x32`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bltS(target)
        asm.markLabel(target)
        assertEquals(0x32, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `blt emits 0x3F`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.blt(target)
        asm.markLabel(target)
        assertEquals(0x3F, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `bgt s emits 0x30`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bgtS(target)
        asm.markLabel(target)
        assertEquals(0x30, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `bgt emits 0x3D`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bgt(target)
        asm.markLabel(target)
        assertEquals(0x3D, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ble s emits 0x31`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bleS(target)
        asm.markLabel(target)
        assertEquals(0x31, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ble emits 0x3E`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.ble(target)
        asm.markLabel(target)
        assertEquals(0x3E, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `bge s emits 0x2F`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bgeS(target)
        asm.markLabel(target)
        assertEquals(0x2F, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `bge emits 0x3C`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bge(target)
        asm.markLabel(target)
        assertEquals(0x3C, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `blt un s emits 0x37`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bltUnS(target)
        asm.markLabel(target)
        assertEquals(0x37, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `blt un emits 0x44`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bltUn(target)
        asm.markLabel(target)
        assertEquals(0x44, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `bgt un s emits 0x35`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bgtUnS(target)
        asm.markLabel(target)
        assertEquals(0x35, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `bgt un emits 0x42`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bgtUn(target)
        asm.markLabel(target)
        assertEquals(0x42, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ble un s emits 0x36`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bleUnS(target)
        asm.markLabel(target)
        assertEquals(0x36, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ble un emits 0x43`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bleUn(target)
        asm.markLabel(target)
        assertEquals(0x43, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `bge un s emits 0x34`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bgeUnS(target)
        asm.markLabel(target)
        assertEquals(0x34, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `bge un emits 0x41`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.bgeUn(target)
        asm.markLabel(target)
        assertEquals(0x41, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `backward branch short computes negative offset`() {
        val asm = CilAssembler()
        val loop = asm.defineLabel()
        asm.markLabel(loop)
        asm.nop()
        asm.nop()
        asm.brS(loop)
        val code = asm.toByteArray()
        assertEquals(0x2B, byte(code, 2))
        assertTrue(code[3].toInt() < 0, "backward offset should be negative")
        assertEquals(-4, code[3].toInt()) // target=0, patch offset=3, base=3+1=4 => 0-4=-4
    }

    @Test
    fun `backward branch long computes negative offset`() {
        val asm = CilAssembler()
        val loop = asm.defineLabel()
        asm.markLabel(loop)
        asm.nop()
        asm.br(loop)
        val code = asm.toByteArray()
        assertEquals(0x38, byte(code, 1))
        val offset = i32LE(code, 2)
        assertTrue(offset < 0, "backward long offset should be negative")
    }

    @Test
    fun `forward branch offset calculation short`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.brtrueS(target)    // 0: opcode, 1: offset
        asm.nop()              // 2: nop
        asm.nop()              // 3: nop
        asm.markLabel(target)  // 4
        asm.ret()
        val code = asm.toByteArray()
        // offset = target(4) - (patchOffset(1) + 1) = 4 - 2 = 2
        assertEquals(2, code[1].toInt())
    }

    @Test
    fun `forward branch offset calculation long`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.brtrue(target)     // 0: opcode, 1-4: offset
        asm.nop()              // 5: nop
        asm.nop()              // 6: nop
        asm.markLabel(target)  // 7
        asm.ret()
        val code = asm.toByteArray()
        // offset = target(7) - (patchOffset(1) + 4) = 7 - 5 = 2
        assertEquals(2, i32LE(code, 1))
    }

    @Test
    fun `call emits 0x28 and token`() {
        val asm = CilAssembler()
        val token = CilToken.methodDef(1)
        asm.call(token)
        val code = asm.toByteArray()
        assertEquals(5, code.size)
        assertEquals(0x28, byte(code, 0))
        assertEquals(0x06000001, i32LE(code, 1))
    }

    @Test
    fun `callvirt emits 0x6F and token`() {
        val asm = CilAssembler()
        val token = CilToken.memberRef(5)
        asm.callvirt(token)
        val code = asm.toByteArray()
        assertEquals(5, code.size)
        assertEquals(0x6F, byte(code, 0))
        assertEquals(0x0A000005, i32LE(code, 1))
    }

    @Test
    fun `calli emits 0x29 and token`() {
        val asm = CilAssembler()
        val token = CilToken.fromRaw(0x11000001)
        asm.calli(token)
        val code = asm.toByteArray()
        assertEquals(0x29, byte(code, 0))
    }

    @Test
    fun `ret emits 0x2A`() {
        val asm = CilAssembler()
        asm.ret()
        assertEquals(0x2A, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `jmp emits 0x27 and token`() {
        val asm = CilAssembler()
        val token = CilToken.methodDef(3)
        asm.jmp(token)
        val code = asm.toByteArray()
        assertEquals(0x27, byte(code, 0))
        assertEquals(0x06000003, i32LE(code, 1))
    }

    @Test
    fun `tail prefix emits two-byte 0xFE 0x14`() {
        val asm = CilAssembler()
        asm.tail()
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x14, byte(code, 1))
    }

    @Test
    fun `tail call sequence`() {
        val asm = CilAssembler()
        val token = CilToken.methodDef(1)
        asm.tail()
        asm.call(token)
        asm.ret()
        val code = asm.toByteArray()
        assertEquals(8, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x14, byte(code, 1))
        assertEquals(0x28, byte(code, 2))
        assertEquals(0x2A, byte(code, 7))
    }

    @Test
    fun `newobj emits 0x73 and token`() {
        val asm = CilAssembler()
        val token = CilToken.memberRef(1)
        asm.newobj(token)
        val code = asm.toByteArray()
        assertEquals(5, code.size)
        assertEquals(0x73, byte(code, 0))
        assertEquals(0x0A000001, i32LE(code, 1))
    }

    @Test
    fun `castclass emits 0x74 and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(2)
        asm.castclass(token)
        val code = asm.toByteArray()
        assertEquals(0x74, byte(code, 0))
        assertEquals(0x01000002, i32LE(code, 1))
    }

    @Test
    fun `isinst emits 0x75 and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(3)
        asm.isinst(token)
        val code = asm.toByteArray()
        assertEquals(0x75, byte(code, 0))
        assertEquals(0x01000003, i32LE(code, 1))
    }

    @Test
    fun `box emits 0x8C and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.box(token)
        val code = asm.toByteArray()
        assertEquals(0x8C, byte(code, 0))
    }

    @Test
    fun `unbox emits 0x79 and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.unbox(token)
        val code = asm.toByteArray()
        assertEquals(0x79, byte(code, 0))
    }

    @Test
    fun `unbox any emits 0xA5 and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.unboxAny(token)
        val code = asm.toByteArray()
        assertEquals(0xA5, byte(code, 0))
    }

    @Test
    fun `ldstr emits 0x72 and token`() {
        val asm = CilAssembler()
        val token = CilToken.userString(1)
        asm.ldstr(token)
        val code = asm.toByteArray()
        assertEquals(0x72, byte(code, 0))
        assertEquals(0x70000001, i32LE(code, 1))
    }

    @Test
    fun `ldtoken emits 0xD0 and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.ldtoken(token)
        val code = asm.toByteArray()
        assertEquals(0xD0, byte(code, 0))
    }

    @Test
    fun `ldftn emits two-byte 0xFE 0x06 and token`() {
        val asm = CilAssembler()
        val token = CilToken.methodDef(1)
        asm.ldftn(token)
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x06, byte(code, 1))
    }

    @Test
    fun `ldvirtftn emits two-byte 0xFE 0x07 and token`() {
        val asm = CilAssembler()
        val token = CilToken.methodDef(1)
        asm.ldvirtftn(token)
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x07, byte(code, 1))
    }

    @Test
    fun `newarr emits 0x8D and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.newarr(token)
        val code = asm.toByteArray()
        assertEquals(0x8D, byte(code, 0))
    }

    @Test
    fun `ldlen emits 0x8E`() {
        val asm = CilAssembler()
        asm.ldlen()
        assertEquals(0x8E, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldelema emits 0x8F and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.ldelema(token)
        val code = asm.toByteArray()
        assertEquals(0x8F, byte(code, 0))
    }

    @Test
    fun `ldelem typed variants`() {
        val asm = CilAssembler()
        asm.ldelemI1(); asm.ldelemU1(); asm.ldelemI2(); asm.ldelemU2()
        asm.ldelemI4(); asm.ldelemU4(); asm.ldelemI8(); asm.ldelemI()
        asm.ldelemR4(); asm.ldelemR8(); asm.ldelemRef()
        val code = asm.toByteArray()
        assertEquals(11, code.size)
        assertEquals(0x90, byte(code, 0))
        assertEquals(0x91, byte(code, 1))
        assertEquals(0x92, byte(code, 2))
        assertEquals(0x93, byte(code, 3))
        assertEquals(0x94, byte(code, 4))
        assertEquals(0x95, byte(code, 5))
        assertEquals(0x96, byte(code, 6))
        assertEquals(0x97, byte(code, 7))
        assertEquals(0x98, byte(code, 8))
        assertEquals(0x99, byte(code, 9))
        assertEquals(0x9A, byte(code, 10))
    }

    @Test
    fun `ldelem with token emits 0xA3`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.ldelem(token)
        val code = asm.toByteArray()
        assertEquals(0xA3, byte(code, 0))
    }

    @Test
    fun `stelem typed variants`() {
        val asm = CilAssembler()
        asm.stelemI(); asm.stelemI1(); asm.stelemI2(); asm.stelemI4()
        asm.stelemI8(); asm.stelemR4(); asm.stelemR8(); asm.stelemRef()
        val code = asm.toByteArray()
        assertEquals(8, code.size)
        assertEquals(0x9B, byte(code, 0))
        assertEquals(0x9C, byte(code, 1))
        assertEquals(0x9D, byte(code, 2))
        assertEquals(0x9E, byte(code, 3))
        assertEquals(0x9F, byte(code, 4))
        assertEquals(0xA0, byte(code, 5))
        assertEquals(0xA1, byte(code, 6))
        assertEquals(0xA2, byte(code, 7))
    }

    @Test
    fun `stelem with token emits 0xA4`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.stelem(token)
        val code = asm.toByteArray()
        assertEquals(0xA4, byte(code, 0))
    }

    @Test
    fun `ldfld emits 0x7B and token`() {
        val asm = CilAssembler()
        val token = CilToken.field(1)
        asm.ldfld(token)
        val code = asm.toByteArray()
        assertEquals(0x7B, byte(code, 0))
        assertEquals(0x04000001, i32LE(code, 1))
    }

    @Test
    fun `stfld emits 0x7D and token`() {
        val asm = CilAssembler()
        val token = CilToken.field(2)
        asm.stfld(token)
        val code = asm.toByteArray()
        assertEquals(0x7D, byte(code, 0))
        assertEquals(0x04000002, i32LE(code, 1))
    }

    @Test
    fun `ldsfld emits 0x7E and token`() {
        val asm = CilAssembler()
        val token = CilToken.field(1)
        asm.ldsfld(token)
        val code = asm.toByteArray()
        assertEquals(0x7E, byte(code, 0))
    }

    @Test
    fun `stsfld emits 0x80 and token`() {
        val asm = CilAssembler()
        val token = CilToken.field(1)
        asm.stsfld(token)
        val code = asm.toByteArray()
        assertEquals(0x80, byte(code, 0))
    }

    @Test
    fun `ldflda emits 0x7C and token`() {
        val asm = CilAssembler()
        val token = CilToken.field(1)
        asm.ldflda(token)
        val code = asm.toByteArray()
        assertEquals(0x7C, byte(code, 0))
    }

    @Test
    fun `ldsflda emits 0x7F and token`() {
        val asm = CilAssembler()
        val token = CilToken.field(1)
        asm.ldsflda(token)
        val code = asm.toByteArray()
        assertEquals(0x7F, byte(code, 0))
    }

    @Test
    fun `ldind i1 emits 0x46`() {
        val asm = CilAssembler()
        asm.ldindI1()
        assertEquals(0x46, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldind u1 emits 0x47`() {
        val asm = CilAssembler()
        asm.ldindU1()
        assertEquals(0x47, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldind i2 emits 0x48`() {
        val asm = CilAssembler()
        asm.ldindI2()
        assertEquals(0x48, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldind u2 emits 0x49`() {
        val asm = CilAssembler()
        asm.ldindU2()
        assertEquals(0x49, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldind i4 emits 0x4A`() {
        val asm = CilAssembler()
        asm.ldindI4()
        assertEquals(0x4A, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldind u4 emits 0x4B`() {
        val asm = CilAssembler()
        asm.ldindU4()
        assertEquals(0x4B, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldind i8 emits 0x4C`() {
        val asm = CilAssembler()
        asm.ldindI8()
        assertEquals(0x4C, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldind i emits 0x4D`() {
        val asm = CilAssembler()
        asm.ldindI()
        assertEquals(0x4D, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldind r4 emits 0x4E`() {
        val asm = CilAssembler()
        asm.ldindR4()
        assertEquals(0x4E, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldind r8 emits 0x4F`() {
        val asm = CilAssembler()
        asm.ldindR8()
        assertEquals(0x4F, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldind ref emits 0x50`() {
        val asm = CilAssembler()
        asm.ldindRef()
        assertEquals(0x50, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `all ldind opcodes in sequence`() {
        val asm = CilAssembler()
        asm.ldindI1(); asm.ldindU1(); asm.ldindI2(); asm.ldindU2()
        asm.ldindI4(); asm.ldindU4(); asm.ldindI8(); asm.ldindI()
        asm.ldindR4(); asm.ldindR8(); asm.ldindRef()
        val code = asm.toByteArray()
        assertEquals(11, code.size)
        for (i in 0 until 11) assertEquals(0x46 + i, byte(code, i))
    }

    @Test
    fun `stind ref emits 0x51`() {
        val asm = CilAssembler()
        asm.stindRef()
        assertEquals(0x51, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stind i1 emits 0x52`() {
        val asm = CilAssembler()
        asm.stindI1()
        assertEquals(0x52, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stind i2 emits 0x53`() {
        val asm = CilAssembler()
        asm.stindI2()
        assertEquals(0x53, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stind i4 emits 0x54`() {
        val asm = CilAssembler()
        asm.stindI4()
        assertEquals(0x54, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stind i8 emits 0x55`() {
        val asm = CilAssembler()
        asm.stindI8()
        assertEquals(0x55, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stind r4 emits 0x56`() {
        val asm = CilAssembler()
        asm.stindR4()
        assertEquals(0x56, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stind r8 emits 0x57`() {
        val asm = CilAssembler()
        asm.stindR8()
        assertEquals(0x57, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stind i emits 0xDF`() {
        val asm = CilAssembler()
        asm.stindI()
        assertEquals(0xDF, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `all stind opcodes in sequence`() {
        val asm = CilAssembler()
        asm.stindRef(); asm.stindI1(); asm.stindI2(); asm.stindI4()
        asm.stindI8(); asm.stindR4(); asm.stindR8()
        val code = asm.toByteArray()
        assertEquals(7, code.size)
        for (i in 0 until 7) assertEquals(0x51 + i, byte(code, i))
    }

    @Test
    fun `throw emits 0x7A`() {
        val asm = CilAssembler()
        asm.throw_()
        assertEquals(0x7A, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `rethrow emits two-byte 0xFE 0x1A`() {
        val asm = CilAssembler()
        asm.rethrow()
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x1A, byte(code, 1))
    }

    @Test
    fun `leave emits 0xDD and offset`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.leave(target)
        asm.markLabel(target)
        val code = asm.toByteArray()
        assertEquals(0xDD, byte(code, 0))
    }

    @Test
    fun `leave s emits 0xDE and offset`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.leaveS(target)
        asm.markLabel(target)
        val code = asm.toByteArray()
        assertEquals(0xDE, byte(code, 0))
    }

    @Test
    fun `endfinally emits 0xDC`() {
        val asm = CilAssembler()
        asm.endfinally()
        assertEquals(0xDC, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `endfilter emits two-byte 0xFE 0x11`() {
        val asm = CilAssembler()
        asm.endfilter()
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x11, byte(code, 1))
    }

    @Test
    fun `exception handling sequence`() {
        val asm = CilAssembler()
        val handler = asm.defineLabel()
        asm.throw_()
        asm.markLabel(handler)
        asm.rethrow()
        asm.endfinally()
        val code = asm.toByteArray()
        assertEquals(0x7A, byte(code, 0))
        assertEquals(0xFE, byte(code, 1))
        assertEquals(0x1A, byte(code, 2))
        assertEquals(0xDC, byte(code, 3))
    }

    @Test
    fun `break emits 0x01`() {
        val asm = CilAssembler()
        asm.break_()
        assertEquals(0x01, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `size tracking starts at zero`() {
        val asm = CilAssembler()
        assertEquals(0, asm.size)
    }

    @Test
    fun `size tracking after single byte instructions`() {
        val asm = CilAssembler()
        asm.nop()
        assertEquals(1, asm.size)
        asm.add()
        assertEquals(2, asm.size)
        asm.ret()
        assertEquals(3, asm.size)
    }

    @Test
    fun `size tracking after two-byte opcodes`() {
        val asm = CilAssembler()
        asm.ceq()
        assertEquals(2, asm.size)
    }

    @Test
    fun `size tracking after ldc i4`() {
        val asm = CilAssembler()
        asm.ldcI4(100)
        assertEquals(5, asm.size)
    }

    @Test
    fun `size tracking after ldc i8`() {
        val asm = CilAssembler()
        asm.ldcI8(100L)
        assertEquals(9, asm.size)
    }

    @Test
    fun `size tracking after token instruction`() {
        val asm = CilAssembler()
        asm.call(CilToken.methodDef(1))
        assertEquals(5, asm.size)
    }

    @Test
    fun `declare local returns correct index`() {
        val asm = CilAssembler()
        val l0 = asm.declareLocal(CilSigType.I4)
        val l1 = asm.declareLocal(CilSigType.I8)
        val l2 = asm.declareLocal(CilSigType.R8)
        assertEquals(0, l0.index)
        assertEquals(1, l1.index)
        assertEquals(2, l2.index)
    }

    @Test
    fun `locals list reflects declared locals`() {
        val asm = CilAssembler()
        assertEquals(0, asm.localCount)
        asm.declareLocal(CilSigType.I4)
        assertEquals(1, asm.localCount)
        asm.declareLocal(CilSigType.BOOLEAN)
        assertEquals(2, asm.localCount)
        assertEquals(2, asm.locals.size)
    }

    @Test
    fun `declared local type is preserved`() {
        val asm = CilAssembler()
        val l = asm.declareLocal(CilSigType.R4)
        assertEquals(CilSigType.R4, l.type)
    }

    @Test
    fun `localloc emits two-byte 0xFE 0x0F`() {
        val asm = CilAssembler()
        asm.localloc()
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x0F, byte(code, 1))
    }

    @Test
    fun `sizeof emits two-byte 0xFE 0x1C and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.sizeof(token)
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x1C, byte(code, 1))
    }

    @Test
    fun `initobj emits two-byte 0xFE 0x15 and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeDef(1)
        asm.initobj(token)
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x15, byte(code, 1))
    }

    @Test
    fun `cpobj emits 0x70 and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.cpobj(token)
        assertEquals(0x70, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldobj emits 0x71 and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.ldobj(token)
        assertEquals(0x71, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `stobj emits 0x81 and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.stobj(token)
        assertEquals(0x81, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `cpblk emits two-byte 0xFE 0x17`() {
        val asm = CilAssembler()
        asm.cpblk()
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x17, byte(code, 1))
    }

    @Test
    fun `initblk emits two-byte 0xFE 0x18`() {
        val asm = CilAssembler()
        asm.initblk()
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x18, byte(code, 1))
    }

    @Test
    fun `ckfinite emits 0xC3`() {
        val asm = CilAssembler()
        asm.ckfinite()
        assertEquals(0xC3, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `volatile prefix emits two-byte 0xFE 0x13`() {
        val asm = CilAssembler()
        asm.volatile_()
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x13, byte(code, 1))
    }

    @Test
    fun `unaligned prefix emits two-byte and alignment`() {
        val asm = CilAssembler()
        asm.unaligned(1)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x12, byte(code, 1))
        assertEquals(1, byte(code, 2))
    }

    @Test
    fun `constrained prefix emits two-byte and token`() {
        val asm = CilAssembler()
        val token = CilToken.typeRef(1)
        asm.constrained(token)
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x16, byte(code, 1))
    }

    @Test
    fun `readonly prefix emits two-byte 0xFE 0x1E`() {
        val asm = CilAssembler()
        asm.readonly()
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x1E, byte(code, 1))
    }

    @Test
    fun `token little-endian encoding for field`() {
        val asm = CilAssembler()
        val token = CilToken.field(1)
        asm.ldsfld(token)
        val code = asm.toByteArray()
        assertEquals(0x01, byte(code, 1))
        assertEquals(0x00, byte(code, 2))
        assertEquals(0x00, byte(code, 3))
        assertEquals(0x04, byte(code, 4))
    }

    @Test
    fun `token little-endian encoding for method def`() {
        val asm = CilAssembler()
        val token = CilToken.methodDef(0x123)
        asm.call(token)
        val code = asm.toByteArray()
        assertEquals(0x23, byte(code, 1))
        assertEquals(0x01, byte(code, 2))
        assertEquals(0x00, byte(code, 3))
        assertEquals(0x06, byte(code, 4))
    }

    @Test
    fun `add then ret sequence`() {
        val asm = CilAssembler()
        asm.ldarg(0)
        asm.ldarg(1)
        asm.add()
        asm.ret()
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x02, byte(code, 0))
        assertEquals(0x03, byte(code, 1))
        assertEquals(0x58, byte(code, 2))
        assertEquals(0x2A, byte(code, 3))
    }

    @Test
    fun `conditional branch pattern`() {
        val asm = CilAssembler()
        val elseLabel = asm.defineLabel()
        val endLabel = asm.defineLabel()
        asm.ldarg(0)
        asm.brfalseS(elseLabel)
        asm.ldcI4Auto(1)
        asm.brS(endLabel)
        asm.markLabel(elseLabel)
        asm.ldcI4Auto(0)
        asm.markLabel(endLabel)
        asm.ret()
        val code = asm.toByteArray()
        assertEquals(0x02, byte(code, 0))
        assertEquals(0x2C, byte(code, 1))
        assertEquals(0x2B, byte(code, 4))
    }

    @Test
    fun `loop pattern with backward branch`() {
        val asm = CilAssembler()
        val loopStart = asm.defineLabel()
        val loopEnd = asm.defineLabel()
        asm.ldcI4Auto(0)
        asm.stloc(0)
        asm.markLabel(loopStart)
        asm.ldloc(0)
        asm.ldarg(0)
        asm.clt()
        asm.brfalseS(loopEnd)
        asm.ldloc(0)
        asm.ldcI4Auto(1)
        asm.add()
        asm.stloc(0)
        asm.brS(loopStart)
        asm.markLabel(loopEnd)
        asm.ldloc(0)
        asm.ret()
        val code = asm.toByteArray()
        assertTrue(code.size > 10)
    }

    @Test
    fun `multiple labels resolve correctly`() {
        val asm = CilAssembler()
        val a = asm.defineLabel()
        val b = asm.defineLabel()
        val c = asm.defineLabel()
        asm.brS(a)
        asm.markLabel(a)
        asm.brS(b)
        asm.markLabel(b)
        asm.brS(c)
        asm.markLabel(c)
        asm.ret()
        val code = asm.toByteArray()
        assertEquals(0, code[1].toInt())
        assertEquals(0, code[3].toInt())
        assertEquals(0, code[5].toInt())
    }

    @Test
    fun `field load store sequence`() {
        val asm = CilAssembler()
        val field = CilToken.field(1)
        asm.ldarg(0)
        asm.ldfld(field)
        asm.ldarg(0)
        asm.ldarg(1)
        asm.stfld(field)
        asm.ret()
        val code = asm.toByteArray()
        assertEquals(0x7B, byte(code, 1))
        assertEquals(0x7D, byte(code, 8))
    }

    @Test
    fun `static field access sequence`() {
        val asm = CilAssembler()
        val field = CilToken.field(1)
        asm.ldsfld(field)
        asm.ldcI4Auto(1)
        asm.add()
        asm.stsfld(field)
        asm.ret()
        val code = asm.toByteArray()
        assertEquals(0x7E, byte(code, 0))
        assertEquals(0x80, byte(code, 7))
    }

    @Test
    fun `box unbox sequence`() {
        val asm = CilAssembler()
        val intType = CilToken.typeRef(1)
        asm.ldcI4Auto(42)
        asm.box(intType)
        asm.unboxAny(intType)
        asm.ret()
        val code = asm.toByteArray()
        assertEquals(0x8C, byte(code, 2))
        assertEquals(0xA5, byte(code, 7))
    }

    @Test
    fun `array create and access sequence`() {
        val asm = CilAssembler()
        val elemType = CilToken.typeRef(1)
        asm.ldcI4Auto(10)
        asm.newarr(elemType)
        asm.dup()
        asm.ldlen()
        asm.pop()
        asm.dup()
        asm.ldcI4Auto(0)
        asm.ldelemI4()
        val code = asm.toByteArray()
        assertEquals(0x8D, byte(code, 2))
        assertEquals(0x25, byte(code, 7))
        assertEquals(0x8E, byte(code, 8))
    }

    @Test
    fun `try catch leave pattern`() {
        val asm = CilAssembler()
        val handler = asm.defineLabel()
        val end = asm.defineLabel()
        asm.nop()
        asm.leaveS(end)
        asm.markLabel(handler)
        asm.pop()
        asm.leaveS(end)
        asm.markLabel(end)
        asm.ret()
        val code = asm.toByteArray()
        assertEquals(0xDE, byte(code, 1))
        assertEquals(0xDE, byte(code, 4))
    }

    @Test
    fun `finally block pattern`() {
        val asm = CilAssembler()
        val end = asm.defineLabel()
        asm.nop()
        asm.leaveS(end)
        asm.endfinally()
        asm.markLabel(end)
        asm.ret()
        val code = asm.toByteArray()
        assertEquals(0xDC, byte(code, 3))
    }

    @Test
    fun `constrained callvirt pattern`() {
        val asm = CilAssembler()
        val typeToken = CilToken.typeRef(1)
        val methodToken = CilToken.memberRef(1)
        asm.constrained(typeToken)
        asm.callvirt(methodToken)
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x16, byte(code, 1))
        assertEquals(0x6F, byte(code, 6))
    }

    @Test
    fun `volatile ldfld pattern`() {
        val asm = CilAssembler()
        val field = CilToken.field(1)
        asm.volatile_()
        asm.ldarg(0)
        asm.ldfld(field)
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x13, byte(code, 1))
        assertEquals(0x7B, byte(code, 3))
    }

    @Test
    fun `empty method is zero bytes`() {
        val asm = CilAssembler()
        val code = asm.toByteArray()
        assertEquals(0, code.size)
    }

    @Test
    fun `toByteArray produces independent copies`() {
        val asm = CilAssembler()
        asm.nop()
        val a = asm.toByteArray()
        val b = asm.toByteArray()
        a[0] = 0xFF.toByte()
        assertEquals(0x00, byte(b, 0))
    }

    @Test
    fun `conv ovf opcode codes are correct`() {
        assertEquals(0xB3, CilOpCode.CONV_OVF_I1.code)
        assertEquals(0xB4, CilOpCode.CONV_OVF_U1.code)
        assertEquals(0xB5, CilOpCode.CONV_OVF_I2.code)
        assertEquals(0xB6, CilOpCode.CONV_OVF_U2.code)
        assertEquals(0xB7, CilOpCode.CONV_OVF_I4.code)
        assertEquals(0xB8, CilOpCode.CONV_OVF_U4.code)
        assertEquals(0xB9, CilOpCode.CONV_OVF_I8.code)
        assertEquals(0xBA, CilOpCode.CONV_OVF_U8.code)
    }

    @Test
    fun `opcode mnemonic lookup`() {
        assertEquals("add", CilOpCode.ADD.mnemonic())
        assertEquals("ceq", CilOpCode.CEQ.mnemonic())
        assertEquals("ldarg.0", CilOpCode.LDARG_0.mnemonic())
        assertEquals("ldc.i4.m1", CilOpCode.LDC_I4_M1.mnemonic())
    }

    @Test
    fun `opcode from code single byte`() {
        assertEquals(CilOpCode.ADD, CilOpCode.fromCode(0x58))
        assertEquals(CilOpCode.RET, CilOpCode.fromCode(0x2A))
        assertEquals(CilOpCode.NOP, CilOpCode.fromCode(0x00))
    }

    @Test
    fun `opcode from code two byte`() {
        assertEquals(CilOpCode.CEQ, CilOpCode.fromCode(0xFE01))
        assertEquals(CilOpCode.CGT, CilOpCode.fromCode(0xFE02))
        assertEquals(CilOpCode.CLT, CilOpCode.fromCode(0xFE04))
    }

    @Test
    fun `opcode isTwoByte`() {
        assertTrue(!CilOpCode.ADD.isTwoByte())
        assertTrue(!CilOpCode.RET.isTwoByte())
        assertTrue(CilOpCode.CEQ.isTwoByte())
        assertTrue(CilOpCode.CGT.isTwoByte())
        assertTrue(CilOpCode.RETHROW.isTwoByte())
        assertTrue(CilOpCode.TAIL.isTwoByte())
    }

    @Test
    fun `opcode operand size`() {
        assertEquals(0, CilOpCode.ADD.operandSize())
        assertEquals(0, CilOpCode.RET.operandSize())
        assertEquals(1, CilOpCode.LDARG_S.operandSize())
        assertEquals(1, CilOpCode.BR_S.operandSize())
        assertEquals(4, CilOpCode.BR.operandSize())
        assertEquals(4, CilOpCode.LDC_I4.operandSize())
        assertEquals(8, CilOpCode.LDC_I8.operandSize())
        assertEquals(4, CilOpCode.LDC_R4.operandSize())
        assertEquals(8, CilOpCode.LDC_R8.operandSize())
        assertEquals(4, CilOpCode.CALL.operandSize())
    }

    @Test
    fun `token construction and properties`() {
        val methodToken = CilToken.methodDef(5)
        assertEquals(0x06, methodToken.tableId)
        assertEquals(5, methodToken.rowIndex)
        assertTrue(methodToken.isMethodDef)

        val fieldToken = CilToken.field(3)
        assertEquals(0x04, fieldToken.tableId)
        assertEquals(3, fieldToken.rowIndex)
        assertTrue(fieldToken.isField)

        val typeRefToken = CilToken.typeRef(1)
        assertTrue(typeRefToken.isTypeRef)

        val memberRefToken = CilToken.memberRef(2)
        assertTrue(memberRefToken.isMemberRef)

        val userStringToken = CilToken.userString(7)
        assertTrue(userStringToken.isUserString)
    }

    @Test
    fun `token equality`() {
        val a = CilToken.methodDef(1)
        val b = CilToken.methodDef(1)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `label defines and marks correctly`() {
        val asm = CilAssembler()
        val label = asm.defineLabel()
        asm.nop()
        asm.markLabel(label)
        asm.ret()
        val code = asm.toByteArray()
        assertEquals(2, code.size)
    }

    @Test
    fun `unresolved label throws on toByteArray`() {
        val asm = CilAssembler()
        val label = asm.defineLabel()
        asm.brS(label)
        try {
            asm.toByteArray()
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Unresolved"))
        }
    }

    @Test
    fun `double mark label throws`() {
        val asm = CilAssembler()
        val label = asm.defineLabel()
        asm.markLabel(label)
        try {
            asm.markLabel(label)
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("already marked"))
        }
    }

    @Test
    fun `ldarg s boundary at index 255`() {
        val asm = CilAssembler()
        asm.ldarg(255)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x0E, byte(code, 0))
        assertEquals(255, byte(code, 1))
    }

    @Test
    fun `starg s boundary at index 255`() {
        val asm = CilAssembler()
        asm.starg(255)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x10, byte(code, 0))
        assertEquals(255, byte(code, 1))
    }

    @Test
    fun `complex method with locals args arithmetic and branching`() {
        val asm = CilAssembler()
        val sum = asm.declareLocal(CilSigType.I4)
        val i = asm.declareLocal(CilSigType.I4)
        val loopStart = asm.defineLabel()
        val loopEnd = asm.defineLabel()

        asm.ldcI4Auto(0)
        asm.stloc(sum)
        asm.ldcI4Auto(0)
        asm.stloc(i)

        asm.markLabel(loopStart)
        asm.ldloc(i)
        asm.ldarg(0)
        asm.clt()
        asm.brfalseS(loopEnd)

        asm.ldloc(sum)
        asm.ldloc(i)
        asm.add()
        asm.stloc(sum)

        asm.ldloc(i)
        asm.ldcI4Auto(1)
        asm.add()
        asm.stloc(i)
        asm.brS(loopStart)

        asm.markLabel(loopEnd)
        asm.ldloc(sum)
        asm.ret()

        val code = asm.toByteArray()
        assertTrue(code.size > 15)
        assertEquals(2, asm.localCount)
        assertEquals(0, sum.index)
        assertEquals(1, i.index)
    }

    @Test
    fun `ldc i4 negative value`() {
        val asm = CilAssembler()
        asm.ldcI4(-1000)
        val code = asm.toByteArray()
        assertEquals(5, code.size)
        assertEquals(0x20, byte(code, 0))
        assertEquals(-1000, i32LE(code, 1))
    }

    @Test
    fun `ldc i4 max value`() {
        val asm = CilAssembler()
        asm.ldcI4(Int.MAX_VALUE)
        val code = asm.toByteArray()
        assertEquals(Int.MAX_VALUE, i32LE(code, 1))
    }

    @Test
    fun `ldc i4 min value`() {
        val asm = CilAssembler()
        asm.ldcI4(Int.MIN_VALUE)
        val code = asm.toByteArray()
        assertEquals(Int.MIN_VALUE, i32LE(code, 1))
    }

    @Test
    fun `ldc i8 negative value`() {
        val asm = CilAssembler()
        asm.ldcI8(-1L)
        val code = asm.toByteArray()
        assertEquals(-1L, i64LE(code, 1))
    }

    @Test
    fun `ldc r4 infinity`() {
        val asm = CilAssembler()
        asm.ldcR4(Float.POSITIVE_INFINITY)
        val code = asm.toByteArray()
        assertEquals(Float.POSITIVE_INFINITY.toBits(), i32LE(code, 1))
    }

    @Test
    fun `ldc r8 NaN`() {
        val asm = CilAssembler()
        asm.ldcR8(Double.NaN)
        val code = asm.toByteArray()
        assertEquals(Double.NaN.toBits(), i64LE(code, 1))
    }

    @Test
    fun `newobj castclass isinst sequence`() {
        val asm = CilAssembler()
        val ctor = CilToken.memberRef(1)
        val typeToken = CilToken.typeRef(1)
        asm.newobj(ctor)
        asm.castclass(typeToken)
        asm.isinst(typeToken)
        val code = asm.toByteArray()
        assertEquals(15, code.size)
        assertEquals(0x73, byte(code, 0))
        assertEquals(0x74, byte(code, 5))
        assertEquals(0x75, byte(code, 10))
    }

    @Test
    fun `readonly ldelema pattern`() {
        val asm = CilAssembler()
        val elemType = CilToken.typeRef(1)
        asm.readonly()
        asm.ldelema(elemType)
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x1E, byte(code, 1))
        assertEquals(0x8F, byte(code, 2))
    }

    @Test
    fun `unaligned stind pattern`() {
        val asm = CilAssembler()
        asm.unaligned(2)
        asm.stindI4()
        val code = asm.toByteArray()
        assertEquals(0xFE, byte(code, 0))
        assertEquals(0x12, byte(code, 1))
        assertEquals(2, byte(code, 2))
        assertEquals(0x54, byte(code, 3))
    }
}
