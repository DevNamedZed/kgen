package org.kgen.target.jvm.asm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.kgen.target.jvm.JvmOpCode

class JvmAssemblerComprehensiveTest {

    private fun byte(code: ByteArray, index: Int): Int = code[index].toInt() and 0xFF
    private fun short(code: ByteArray, offset: Int): Int =
        ((code[offset].toInt() and 0xFF) shl 8) or (code[offset + 1].toInt() and 0xFF)
    private fun signedShort(code: ByteArray, offset: Int): Int {
        val v = short(code, offset)
        return if (v >= 0x8000) v - 0x10000 else v
    }

    @Test
    fun `nop emits correct opcode`() {
        val asm = JvmAssembler()
        asm.nop()
        val code = asm.toByteArray()
        assertEquals(1, code.size)
        assertEquals(0x00, byte(code, 0))
    }

    @Test
    fun `aconst null emits correct opcode`() {
        val asm = JvmAssembler()
        asm.aconstNull()
        val code = asm.toByteArray()
        assertEquals(1, code.size)
        assertEquals(0x01, byte(code, 0))
    }

    @Test
    fun `iconst m1 emits 0x02`() {
        val asm = JvmAssembler()
        asm.iconstM1()
        val code = asm.toByteArray()
        assertEquals(0x02, byte(code, 0))
    }

    @Test
    fun `iconst 0 through 5 emit correct opcodes`() {
        val asm = JvmAssembler()
        asm.iconst0()
        asm.iconst1()
        asm.iconst2()
        asm.iconst3()
        asm.iconst4()
        asm.iconst5()
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        for (i in 0..5) {
            assertEquals(0x03 + i, byte(code, i))
        }
    }

    @Test
    fun `lconst 0 and 1 emit correct opcodes`() {
        val asm = JvmAssembler()
        asm.lconst0()
        asm.lconst1()
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x09, byte(code, 0))
        assertEquals(0x0A, byte(code, 1))
    }

    @Test
    fun `fconst 0 1 2 emit correct opcodes`() {
        val asm = JvmAssembler()
        asm.fconst0()
        asm.fconst1()
        asm.fconst2()
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x0B, byte(code, 0))
        assertEquals(0x0C, byte(code, 1))
        assertEquals(0x0D, byte(code, 2))
    }

    @Test
    fun `dconst 0 and 1 emit correct opcodes`() {
        val asm = JvmAssembler()
        asm.dconst0()
        asm.dconst1()
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x0E, byte(code, 0))
        assertEquals(0x0F, byte(code, 1))
    }

    @Test
    fun `bipush encodes signed byte`() {
        val asm = JvmAssembler()
        asm.bipush(127)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x10, byte(code, 0))
        assertEquals(127, byte(code, 1))
    }

    @Test
    fun `bipush negative value`() {
        val asm = JvmAssembler()
        asm.bipush(-1)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x10, byte(code, 0))
        assertEquals(0xFF, byte(code, 1))
    }

    @Test
    fun `sipush encodes two byte value`() {
        val asm = JvmAssembler()
        asm.sipush(0x1234)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x11, byte(code, 0))
        assertEquals(0x12, byte(code, 1))
        assertEquals(0x34, byte(code, 2))
    }

    @Test
    fun `sipush negative value`() {
        val asm = JvmAssembler()
        asm.sipush(-1000)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x11, byte(code, 0))
        val value = signedShort(code, 1)
        assertEquals(-1000, value)
    }

    @Test
    fun `sipush max positive`() {
        val asm = JvmAssembler()
        asm.sipush(32767)
        val code = asm.toByteArray()
        assertEquals(short(code, 1), 32767)
    }

    @Test
    fun `ldc with index under 256 uses single byte form`() {
        val asm = JvmAssembler()
        asm.ldc(1)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x12, byte(code, 0))
        assertEquals(1, byte(code, 1))
    }

    @Test
    fun `ldc with index 255 still uses single byte form`() {
        val asm = JvmAssembler()
        asm.ldc(255)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x12, byte(code, 0))
        assertEquals(255, byte(code, 1))
    }

    @Test
    fun `ldc with index 256 uses ldc w`() {
        val asm = JvmAssembler()
        asm.ldc(256)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x13, byte(code, 0))
        assertEquals(256, short(code, 1))
    }

    @Test
    fun `ldc with large index uses ldc w`() {
        val asm = JvmAssembler()
        asm.ldc(0x1234)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x13, byte(code, 0))
        assertEquals(0x1234, short(code, 1))
    }

    @Test
    fun `ldc2w encodes two byte index`() {
        val asm = JvmAssembler()
        asm.ldc2w(42)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x14, byte(code, 0))
        assertEquals(42, short(code, 1))
    }

    @Test
    fun `ldc2w with large index`() {
        val asm = JvmAssembler()
        asm.ldc2w(0xABCD)
        val code = asm.toByteArray()
        assertEquals(0xABCD, short(code, 1))
    }

    @Test
    fun `pushInt uses iconst for minus one`() {
        val asm = JvmAssembler()
        asm.pushInt(-1)
        val code = asm.toByteArray()
        assertEquals(1, code.size)
        assertEquals(0x02, byte(code, 0))
    }

    @Test
    fun `pushInt uses iconst for 0 through 5`() {
        for (v in 0..5) {
            val asm = JvmAssembler()
            asm.pushInt(v)
            val code = asm.toByteArray()
            assertEquals(1, code.size, "pushInt($v) should be 1 byte")
            assertEquals(0x03 + v, byte(code, 0))
        }
    }

    @Test
    fun `pushInt uses bipush for 6`() {
        val asm = JvmAssembler()
        asm.pushInt(6)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x10, byte(code, 0))
    }

    @Test
    fun `pushInt uses bipush for negative values in range`() {
        val asm = JvmAssembler()
        asm.pushInt(-128)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x10, byte(code, 0))
    }

    @Test
    fun `pushInt uses sipush for 128`() {
        val asm = JvmAssembler()
        asm.pushInt(128)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x11, byte(code, 0))
    }

    @Test
    fun `pushInt uses sipush for negative 129`() {
        val asm = JvmAssembler()
        asm.pushInt(-129)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x11, byte(code, 0))
    }

    @Test
    fun `pushInt throws for values outside sipush range`() {
        val asm = JvmAssembler()
        assertThrows<IllegalStateException> { asm.pushInt(32768) }
        assertThrows<IllegalStateException> { asm.pushInt(-32769) }
    }

    @Test
    fun `iload short forms 0 through 3`() {
        val asm = JvmAssembler()
        for (i in 0..3) asm.iload(i)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x1A, byte(code, 0))
        assertEquals(0x1B, byte(code, 1))
        assertEquals(0x1C, byte(code, 2))
        assertEquals(0x1D, byte(code, 3))
    }

    @Test
    fun `iload long form for index 4`() {
        val asm = JvmAssembler()
        asm.iload(4)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x15, byte(code, 0))
        assertEquals(4, byte(code, 1))
    }

    @Test
    fun `iload long form for index 255`() {
        val asm = JvmAssembler()
        asm.iload(255)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x15, byte(code, 0))
        assertEquals(255, byte(code, 1))
    }

    @Test
    fun `iload wide form for index 256`() {
        val asm = JvmAssembler()
        asm.iload(256)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x15, byte(code, 1))
        assertEquals(256, short(code, 2))
    }

    @Test
    fun `lload short forms 0 through 3`() {
        val asm = JvmAssembler()
        for (i in 0..3) asm.lload(i)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x1E, byte(code, 0))
        assertEquals(0x1F, byte(code, 1))
        assertEquals(0x20, byte(code, 2))
        assertEquals(0x21, byte(code, 3))
    }

    @Test
    fun `lload long form for index 4`() {
        val asm = JvmAssembler()
        asm.lload(4)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x16, byte(code, 0))
        assertEquals(4, byte(code, 1))
    }

    @Test
    fun `lload wide form for index 300`() {
        val asm = JvmAssembler()
        asm.lload(300)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x16, byte(code, 1))
        assertEquals(300, short(code, 2))
    }

    @Test
    fun `fload short forms 0 through 3`() {
        val asm = JvmAssembler()
        for (i in 0..3) asm.fload(i)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x22, byte(code, 0))
        assertEquals(0x23, byte(code, 1))
        assertEquals(0x24, byte(code, 2))
        assertEquals(0x25, byte(code, 3))
    }

    @Test
    fun `fload long form for index 5`() {
        val asm = JvmAssembler()
        asm.fload(5)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x17, byte(code, 0))
        assertEquals(5, byte(code, 1))
    }

    @Test
    fun `dload short forms 0 through 3`() {
        val asm = JvmAssembler()
        for (i in 0..3) asm.dload(i)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x26, byte(code, 0))
        assertEquals(0x27, byte(code, 1))
        assertEquals(0x28, byte(code, 2))
        assertEquals(0x29, byte(code, 3))
    }

    @Test
    fun `dload long form for index 7`() {
        val asm = JvmAssembler()
        asm.dload(7)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x18, byte(code, 0))
        assertEquals(7, byte(code, 1))
    }

    @Test
    fun `aload short forms 0 through 3`() {
        val asm = JvmAssembler()
        for (i in 0..3) asm.aload(i)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x2A, byte(code, 0))
        assertEquals(0x2B, byte(code, 1))
        assertEquals(0x2C, byte(code, 2))
        assertEquals(0x2D, byte(code, 3))
    }

    @Test
    fun `aload long form for index 4`() {
        val asm = JvmAssembler()
        asm.aload(4)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x19, byte(code, 0))
        assertEquals(4, byte(code, 1))
    }

    @Test
    fun `aload wide form for index 500`() {
        val asm = JvmAssembler()
        asm.aload(500)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x19, byte(code, 1))
        assertEquals(500, short(code, 2))
    }

    @Test
    fun `iaload opcode`() {
        val asm = JvmAssembler()
        asm.iaload()
        assertEquals(0x2E, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `laload opcode`() {
        val asm = JvmAssembler()
        asm.laload()
        assertEquals(0x2F, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `faload opcode`() {
        val asm = JvmAssembler()
        asm.faload()
        assertEquals(0x30, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `daload opcode`() {
        val asm = JvmAssembler()
        asm.daload()
        assertEquals(0x31, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `aaload opcode`() {
        val asm = JvmAssembler()
        asm.aaload()
        assertEquals(0x32, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `baload opcode`() {
        val asm = JvmAssembler()
        asm.baload()
        assertEquals(0x33, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `istore short forms 0 through 3`() {
        val asm = JvmAssembler()
        for (i in 0..3) asm.istore(i)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x3B, byte(code, 0))
        assertEquals(0x3C, byte(code, 1))
        assertEquals(0x3D, byte(code, 2))
        assertEquals(0x3E, byte(code, 3))
    }

    @Test
    fun `istore long form for index 4`() {
        val asm = JvmAssembler()
        asm.istore(4)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x36, byte(code, 0))
        assertEquals(4, byte(code, 1))
    }

    @Test
    fun `istore wide form for index 256`() {
        val asm = JvmAssembler()
        asm.istore(256)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x36, byte(code, 1))
        assertEquals(256, short(code, 2))
    }

    @Test
    fun `lstore short forms 0 through 3`() {
        val asm = JvmAssembler()
        for (i in 0..3) asm.lstore(i)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x3F, byte(code, 0))
        assertEquals(0x40, byte(code, 1))
        assertEquals(0x41, byte(code, 2))
        assertEquals(0x42, byte(code, 3))
    }

    @Test
    fun `lstore long form for index 10`() {
        val asm = JvmAssembler()
        asm.lstore(10)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x37, byte(code, 0))
        assertEquals(10, byte(code, 1))
    }

    @Test
    fun `fstore short forms 0 through 3`() {
        val asm = JvmAssembler()
        for (i in 0..3) asm.fstore(i)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x43, byte(code, 0))
        assertEquals(0x44, byte(code, 1))
        assertEquals(0x45, byte(code, 2))
        assertEquals(0x46, byte(code, 3))
    }

    @Test
    fun `fstore long form for index 8`() {
        val asm = JvmAssembler()
        asm.fstore(8)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x38, byte(code, 0))
        assertEquals(8, byte(code, 1))
    }

    @Test
    fun `dstore short forms 0 through 3`() {
        val asm = JvmAssembler()
        for (i in 0..3) asm.dstore(i)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x47, byte(code, 0))
        assertEquals(0x48, byte(code, 1))
        assertEquals(0x49, byte(code, 2))
        assertEquals(0x4A, byte(code, 3))
    }

    @Test
    fun `dstore long form for index 6`() {
        val asm = JvmAssembler()
        asm.dstore(6)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x39, byte(code, 0))
        assertEquals(6, byte(code, 1))
    }

    @Test
    fun `astore short forms 0 through 3`() {
        val asm = JvmAssembler()
        for (i in 0..3) asm.astore(i)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x4B, byte(code, 0))
        assertEquals(0x4C, byte(code, 1))
        assertEquals(0x4D, byte(code, 2))
        assertEquals(0x4E, byte(code, 3))
    }

    @Test
    fun `astore long form for index 5`() {
        val asm = JvmAssembler()
        asm.astore(5)
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0x3A, byte(code, 0))
        assertEquals(5, byte(code, 1))
    }

    @Test
    fun `astore wide form for index 400`() {
        val asm = JvmAssembler()
        asm.astore(400)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x3A, byte(code, 1))
        assertEquals(400, short(code, 2))
    }

    @Test
    fun `iastore opcode`() {
        val asm = JvmAssembler()
        asm.iastore()
        assertEquals(0x4F, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `lastore opcode`() {
        val asm = JvmAssembler()
        asm.lastore()
        assertEquals(0x50, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `fastore opcode`() {
        val asm = JvmAssembler()
        asm.fastore()
        assertEquals(0x51, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `dastore opcode`() {
        val asm = JvmAssembler()
        asm.dastore()
        assertEquals(0x52, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `aastore opcode`() {
        val asm = JvmAssembler()
        asm.aastore()
        assertEquals(0x53, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `bastore opcode`() {
        val asm = JvmAssembler()
        asm.bastore()
        assertEquals(0x54, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `pop opcode`() {
        val asm = JvmAssembler()
        asm.pop()
        assertEquals(0x57, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `pop2 opcode`() {
        val asm = JvmAssembler()
        asm.pop2()
        assertEquals(0x58, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `dup opcode`() {
        val asm = JvmAssembler()
        asm.dup()
        assertEquals(0x59, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `dupX1 opcode`() {
        val asm = JvmAssembler()
        asm.dupX1()
        assertEquals(0x5A, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `dupX2 opcode`() {
        val asm = JvmAssembler()
        asm.dupX2()
        assertEquals(0x5B, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `dup2 opcode`() {
        val asm = JvmAssembler()
        asm.dup2()
        assertEquals(0x5C, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `swap opcode`() {
        val asm = JvmAssembler()
        asm.swap()
        assertEquals(0x5F, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `all stack ops in sequence`() {
        val asm = JvmAssembler()
        asm.pop()
        asm.pop2()
        asm.dup()
        asm.dupX1()
        asm.dupX2()
        asm.dup2()
        asm.swap()
        val code = asm.toByteArray()
        assertEquals(7, code.size)
        assertEquals(0x57, byte(code, 0))
        assertEquals(0x58, byte(code, 1))
        assertEquals(0x59, byte(code, 2))
        assertEquals(0x5A, byte(code, 3))
        assertEquals(0x5B, byte(code, 4))
        assertEquals(0x5C, byte(code, 5))
        assertEquals(0x5F, byte(code, 6))
    }

    @Test
    fun `iadd opcode`() {
        val asm = JvmAssembler()
        asm.iadd()
        assertEquals(0x60, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ladd opcode`() {
        val asm = JvmAssembler()
        asm.ladd()
        assertEquals(0x61, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `fadd opcode`() {
        val asm = JvmAssembler()
        asm.fadd()
        assertEquals(0x62, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `dadd opcode`() {
        val asm = JvmAssembler()
        asm.dadd()
        assertEquals(0x63, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `isub opcode`() {
        val asm = JvmAssembler()
        asm.isub()
        assertEquals(0x64, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `lsub opcode`() {
        val asm = JvmAssembler()
        asm.lsub()
        assertEquals(0x65, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `fsub opcode`() {
        val asm = JvmAssembler()
        asm.fsub()
        assertEquals(0x66, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `dsub opcode`() {
        val asm = JvmAssembler()
        asm.dsub()
        assertEquals(0x67, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `imul opcode`() {
        val asm = JvmAssembler()
        asm.imul()
        assertEquals(0x68, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `lmul opcode`() {
        val asm = JvmAssembler()
        asm.lmul()
        assertEquals(0x69, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `fmul opcode`() {
        val asm = JvmAssembler()
        asm.fmul()
        assertEquals(0x6A, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `dmul opcode`() {
        val asm = JvmAssembler()
        asm.dmul()
        assertEquals(0x6B, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `idiv opcode`() {
        val asm = JvmAssembler()
        asm.idiv()
        assertEquals(0x6C, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ldiv opcode`() {
        val asm = JvmAssembler()
        asm.ldiv()
        assertEquals(0x6D, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `fdiv opcode`() {
        val asm = JvmAssembler()
        asm.fdiv()
        assertEquals(0x6E, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ddiv opcode`() {
        val asm = JvmAssembler()
        asm.ddiv()
        assertEquals(0x6F, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `irem opcode`() {
        val asm = JvmAssembler()
        asm.irem()
        assertEquals(0x70, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `lrem opcode`() {
        val asm = JvmAssembler()
        asm.lrem()
        assertEquals(0x71, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `frem opcode`() {
        val asm = JvmAssembler()
        asm.frem()
        assertEquals(0x72, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `drem opcode`() {
        val asm = JvmAssembler()
        asm.drem()
        assertEquals(0x73, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ineg opcode`() {
        val asm = JvmAssembler()
        asm.ineg()
        assertEquals(0x74, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `lneg opcode`() {
        val asm = JvmAssembler()
        asm.lneg()
        assertEquals(0x75, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `fneg opcode`() {
        val asm = JvmAssembler()
        asm.fneg()
        assertEquals(0x76, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `dneg opcode`() {
        val asm = JvmAssembler()
        asm.dneg()
        assertEquals(0x77, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ishl opcode`() {
        val asm = JvmAssembler()
        asm.ishl()
        assertEquals(0x78, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `lshl opcode`() {
        val asm = JvmAssembler()
        asm.lshl()
        assertEquals(0x79, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ishr opcode`() {
        val asm = JvmAssembler()
        asm.ishr()
        assertEquals(0x7A, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `lshr opcode`() {
        val asm = JvmAssembler()
        asm.lshr()
        assertEquals(0x7B, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `iushr opcode`() {
        val asm = JvmAssembler()
        asm.iushr()
        assertEquals(0x7C, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `lushr opcode`() {
        val asm = JvmAssembler()
        asm.lushr()
        assertEquals(0x7D, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `iand opcode`() {
        val asm = JvmAssembler()
        asm.iand()
        assertEquals(0x7E, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `land opcode`() {
        val asm = JvmAssembler()
        asm.land()
        assertEquals(0x7F, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ior opcode`() {
        val asm = JvmAssembler()
        asm.ior()
        assertEquals(0x80, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `lor opcode`() {
        val asm = JvmAssembler()
        asm.lor()
        assertEquals(0x81, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ixor opcode`() {
        val asm = JvmAssembler()
        asm.ixor()
        assertEquals(0x82, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `lxor opcode`() {
        val asm = JvmAssembler()
        asm.lxor()
        assertEquals(0x83, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `iinc normal form`() {
        val asm = JvmAssembler()
        asm.iinc(5, 10)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x84, byte(code, 0))
        assertEquals(5, byte(code, 1))
        assertEquals(10, byte(code, 2))
    }

    @Test
    fun `iinc with negative increment`() {
        val asm = JvmAssembler()
        asm.iinc(0, -1)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x84, byte(code, 0))
        assertEquals(0, byte(code, 1))
        assertEquals(0xFF, byte(code, 2))
    }

    @Test
    fun `iinc with max byte index and increment`() {
        val asm = JvmAssembler()
        asm.iinc(255, 127)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0x84, byte(code, 0))
        assertEquals(255, byte(code, 1))
        assertEquals(127, byte(code, 2))
    }

    @Test
    fun `iinc wide form for large index`() {
        val asm = JvmAssembler()
        asm.iinc(256, 1)
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x84, byte(code, 1))
        assertEquals(256, short(code, 2))
        assertEquals(1, short(code, 4))
    }

    @Test
    fun `iinc wide form for large increment`() {
        val asm = JvmAssembler()
        asm.iinc(0, 128)
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x84, byte(code, 1))
    }

    @Test
    fun `iinc wide form for negative large increment`() {
        val asm = JvmAssembler()
        asm.iinc(0, -129)
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x84, byte(code, 1))
    }

    @Test
    fun `i2l opcode`() {
        val asm = JvmAssembler()
        asm.i2l()
        assertEquals(0x85, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `i2f opcode`() {
        val asm = JvmAssembler()
        asm.i2f()
        assertEquals(0x86, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `i2d opcode`() {
        val asm = JvmAssembler()
        asm.i2d()
        assertEquals(0x87, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `l2i opcode`() {
        val asm = JvmAssembler()
        asm.l2i()
        assertEquals(0x88, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `l2f opcode`() {
        val asm = JvmAssembler()
        asm.l2f()
        assertEquals(0x89, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `l2d opcode`() {
        val asm = JvmAssembler()
        asm.l2d()
        assertEquals(0x8A, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `f2i opcode`() {
        val asm = JvmAssembler()
        asm.f2i()
        assertEquals(0x8B, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `f2l opcode`() {
        val asm = JvmAssembler()
        asm.f2l()
        assertEquals(0x8C, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `f2d opcode`() {
        val asm = JvmAssembler()
        asm.f2d()
        assertEquals(0x8D, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `d2i opcode`() {
        val asm = JvmAssembler()
        asm.d2i()
        assertEquals(0x8E, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `d2l opcode`() {
        val asm = JvmAssembler()
        asm.d2l()
        assertEquals(0x8F, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `d2f opcode`() {
        val asm = JvmAssembler()
        asm.d2f()
        assertEquals(0x90, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `i2b opcode`() {
        val asm = JvmAssembler()
        asm.i2b()
        assertEquals(0x91, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `i2c opcode`() {
        val asm = JvmAssembler()
        asm.i2c()
        assertEquals(0x92, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `i2s opcode`() {
        val asm = JvmAssembler()
        asm.i2s()
        assertEquals(0x93, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `all conversions in sequence`() {
        val asm = JvmAssembler()
        asm.i2l(); asm.i2f(); asm.i2d()
        asm.l2i(); asm.l2f(); asm.l2d()
        asm.f2i(); asm.f2l(); asm.f2d()
        asm.d2i(); asm.d2l(); asm.d2f()
        asm.i2b(); asm.i2c(); asm.i2s()
        val code = asm.toByteArray()
        assertEquals(15, code.size)
        for (i in 0..14) {
            assertEquals(0x85 + i, byte(code, i))
        }
    }

    @Test
    fun `lcmp opcode`() {
        val asm = JvmAssembler()
        asm.lcmp()
        assertEquals(0x94, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `fcmpl opcode`() {
        val asm = JvmAssembler()
        asm.fcmpl()
        assertEquals(0x95, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `fcmpg opcode`() {
        val asm = JvmAssembler()
        asm.fcmpg()
        assertEquals(0x96, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `dcmpl opcode`() {
        val asm = JvmAssembler()
        asm.dcmpl()
        assertEquals(0x97, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `dcmpg opcode`() {
        val asm = JvmAssembler()
        asm.dcmpg()
        assertEquals(0x98, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `ifeq forward branch`() {
        val asm = JvmAssembler()
        asm.ifeq("target")
        asm.nop()
        asm.nop()
        asm.label("target")
        asm.return_()
        val code = asm.toByteArray()
        assertEquals(0x99, byte(code, 0))
        assertEquals(5, short(code, 1))
    }

    @Test
    fun `ifne forward branch`() {
        val asm = JvmAssembler()
        asm.ifne("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0x9A, byte(code, 0))
        assertEquals(3, short(code, 1))
    }

    @Test
    fun `iflt forward branch`() {
        val asm = JvmAssembler()
        asm.iflt("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0x9B, byte(code, 0))
        assertEquals(3, short(code, 1))
    }

    @Test
    fun `ifge forward branch`() {
        val asm = JvmAssembler()
        asm.ifge("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0x9C, byte(code, 0))
    }

    @Test
    fun `ifgt forward branch`() {
        val asm = JvmAssembler()
        asm.ifgt("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0x9D, byte(code, 0))
    }

    @Test
    fun `ifle forward branch`() {
        val asm = JvmAssembler()
        asm.ifle("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0x9E, byte(code, 0))
    }

    @Test
    fun `ifIcmpeq branch`() {
        val asm = JvmAssembler()
        asm.ifIcmpeq("target")
        asm.nop()
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0x9F, byte(code, 0))
        assertEquals(4, short(code, 1))
    }

    @Test
    fun `ifIcmpne branch`() {
        val asm = JvmAssembler()
        asm.ifIcmpne("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0xA0, byte(code, 0))
    }

    @Test
    fun `ifIcmplt branch`() {
        val asm = JvmAssembler()
        asm.ifIcmplt("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0xA1, byte(code, 0))
    }

    @Test
    fun `ifIcmpge branch`() {
        val asm = JvmAssembler()
        asm.ifIcmpge("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0xA2, byte(code, 0))
    }

    @Test
    fun `ifIcmpgt branch`() {
        val asm = JvmAssembler()
        asm.ifIcmpgt("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0xA3, byte(code, 0))
    }

    @Test
    fun `ifIcmple branch`() {
        val asm = JvmAssembler()
        asm.ifIcmple("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0xA4, byte(code, 0))
    }

    @Test
    fun `ifAcmpeq branch`() {
        val asm = JvmAssembler()
        asm.ifAcmpeq("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0xA5, byte(code, 0))
    }

    @Test
    fun `ifAcmpne branch`() {
        val asm = JvmAssembler()
        asm.ifAcmpne("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0xA6, byte(code, 0))
    }

    @Test
    fun `goto forward`() {
        val asm = JvmAssembler()
        asm.goto("end")
        asm.iconst0()
        asm.label("end")
        asm.return_()
        val code = asm.toByteArray()
        assertEquals(0xA7, byte(code, 0))
        assertEquals(4, short(code, 1))
    }

    @Test
    fun `goto backward`() {
        val asm = JvmAssembler()
        asm.label("top")
        asm.nop()
        asm.goto("top")
        val code = asm.toByteArray()
        assertEquals(0xA7, byte(code, 1))
        val offset = signedShort(code, 2)
        assertEquals(-1, offset)
    }

    @Test
    fun `ifnull branch`() {
        val asm = JvmAssembler()
        asm.ifnull("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0xC6, byte(code, 0))
        assertEquals(3, short(code, 1))
    }

    @Test
    fun `ifnonnull branch`() {
        val asm = JvmAssembler()
        asm.ifnonnull("target")
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(0xC7, byte(code, 0))
        assertEquals(3, short(code, 1))
    }

    @Test
    fun `backward branch offset is negative`() {
        val asm = JvmAssembler()
        asm.label("loop")
        asm.iconst0()
        asm.pop()
        asm.ifeq("loop")
        val code = asm.toByteArray()
        val offset = signedShort(code, 3)
        assertEquals(-2, offset)
    }

    @Test
    fun `multiple forward branches to same label`() {
        val asm = JvmAssembler()
        asm.ifeq("end")
        asm.ifne("end")
        asm.label("end")
        asm.return_()
        val code = asm.toByteArray()
        assertEquals(6, short(code, 1))
        assertEquals(3, short(code, 4))
    }

    @Test
    fun `branch with intermediate instructions`() {
        val asm = JvmAssembler()
        asm.goto("target")
        asm.iconst0()
        asm.iconst1()
        asm.iconst2()
        asm.iconst3()
        asm.iconst4()
        asm.label("target")
        val code = asm.toByteArray()
        assertEquals(8, short(code, 1))
    }

    @Test
    fun `undefined label throws on toByteArray`() {
        val asm = JvmAssembler()
        asm.goto("nonexistent")
        assertThrows<IllegalStateException> { asm.toByteArray() }
    }

    @Test
    fun `ireturn opcode`() {
        val asm = JvmAssembler()
        asm.ireturn()
        assertEquals(0xAC, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `lreturn opcode`() {
        val asm = JvmAssembler()
        asm.lreturn()
        assertEquals(0xAD, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `freturn opcode`() {
        val asm = JvmAssembler()
        asm.freturn()
        assertEquals(0xAE, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `dreturn opcode`() {
        val asm = JvmAssembler()
        asm.dreturn()
        assertEquals(0xAF, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `areturn opcode`() {
        val asm = JvmAssembler()
        asm.areturn()
        assertEquals(0xB0, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `return opcode`() {
        val asm = JvmAssembler()
        asm.return_()
        assertEquals(0xB1, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `all returns in sequence`() {
        val asm = JvmAssembler()
        asm.ireturn()
        asm.lreturn()
        asm.freturn()
        asm.dreturn()
        asm.areturn()
        asm.return_()
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        for (i in 0..5) {
            assertEquals(0xAC + i, byte(code, i))
        }
    }

    @Test
    fun `getstatic encoding`() {
        val asm = JvmAssembler()
        asm.getstatic(0x0102)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xB2, byte(code, 0))
        assertEquals(0x0102, short(code, 1))
    }

    @Test
    fun `putstatic encoding`() {
        val asm = JvmAssembler()
        asm.putstatic(0x0304)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xB3, byte(code, 0))
        assertEquals(0x0304, short(code, 1))
    }

    @Test
    fun `getfield encoding`() {
        val asm = JvmAssembler()
        asm.getfield(0x0506)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xB4, byte(code, 0))
        assertEquals(0x0506, short(code, 1))
    }

    @Test
    fun `putfield encoding`() {
        val asm = JvmAssembler()
        asm.putfield(0x0708)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xB5, byte(code, 0))
        assertEquals(0x0708, short(code, 1))
    }

    @Test
    fun `invokevirtual encoding`() {
        val asm = JvmAssembler()
        asm.invokevirtual(42)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xB6, byte(code, 0))
        assertEquals(42, short(code, 1))
    }

    @Test
    fun `invokespecial encoding`() {
        val asm = JvmAssembler()
        asm.invokespecial(7)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xB7, byte(code, 0))
        assertEquals(7, short(code, 1))
    }

    @Test
    fun `invokestatic encoding`() {
        val asm = JvmAssembler()
        asm.invokestatic(100)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xB8, byte(code, 0))
        assertEquals(100, short(code, 1))
    }

    @Test
    fun `invokestatic with large index`() {
        val asm = JvmAssembler()
        asm.invokestatic(0xFFFF)
        val code = asm.toByteArray()
        assertEquals(0xFFFF, short(code, 1))
    }

    @Test
    fun `invokeinterface encoding`() {
        val asm = JvmAssembler()
        asm.invokeinterface(10, 3)
        val code = asm.toByteArray()
        assertEquals(5, code.size)
        assertEquals(0xB9, byte(code, 0))
        assertEquals(10, short(code, 1))
        assertEquals(3, byte(code, 3))
        assertEquals(0, byte(code, 4))
    }

    @Test
    fun `invokeinterface with large index`() {
        val asm = JvmAssembler()
        asm.invokeinterface(0xABCD, 5)
        val code = asm.toByteArray()
        assertEquals(5, code.size)
        assertEquals(0xABCD, short(code, 1))
        assertEquals(5, byte(code, 3))
    }

    @Test
    fun `new encoding`() {
        val asm = JvmAssembler()
        asm.new_(15)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xBB, byte(code, 0))
        assertEquals(15, short(code, 1))
    }

    @Test
    fun `newarray encoding`() {
        val asm = JvmAssembler()
        asm.newarray(10) // T_INT
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0xBC, byte(code, 0))
        assertEquals(10, byte(code, 1))
    }

    @Test
    fun `newarray boolean type`() {
        val asm = JvmAssembler()
        asm.newarray(4) // T_BOOLEAN
        val code = asm.toByteArray()
        assertEquals(4, byte(code, 1))
    }

    @Test
    fun `newarray char type`() {
        val asm = JvmAssembler()
        asm.newarray(5) // T_CHAR
        val code = asm.toByteArray()
        assertEquals(5, byte(code, 1))
    }

    @Test
    fun `newarray float type`() {
        val asm = JvmAssembler()
        asm.newarray(6) // T_FLOAT
        val code = asm.toByteArray()
        assertEquals(6, byte(code, 1))
    }

    @Test
    fun `newarray double type`() {
        val asm = JvmAssembler()
        asm.newarray(7) // T_DOUBLE
        val code = asm.toByteArray()
        assertEquals(7, byte(code, 1))
    }

    @Test
    fun `newarray byte type`() {
        val asm = JvmAssembler()
        asm.newarray(8) // T_BYTE
        val code = asm.toByteArray()
        assertEquals(8, byte(code, 1))
    }

    @Test
    fun `newarray short type`() {
        val asm = JvmAssembler()
        asm.newarray(9) // T_SHORT
        val code = asm.toByteArray()
        assertEquals(9, byte(code, 1))
    }

    @Test
    fun `newarray long type`() {
        val asm = JvmAssembler()
        asm.newarray(11) // T_LONG
        val code = asm.toByteArray()
        assertEquals(11, byte(code, 1))
    }

    @Test
    fun `anewarray encoding`() {
        val asm = JvmAssembler()
        asm.anewarray(20)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xBD, byte(code, 0))
        assertEquals(20, short(code, 1))
    }

    @Test
    fun `arraylength opcode`() {
        val asm = JvmAssembler()
        asm.arraylength()
        assertEquals(0xBE, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `athrow opcode`() {
        val asm = JvmAssembler()
        asm.athrow()
        assertEquals(0xBF, byte(asm.toByteArray(), 0))
    }

    @Test
    fun `checkcast encoding`() {
        val asm = JvmAssembler()
        asm.checkcast(30)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xC0, byte(code, 0))
        assertEquals(30, short(code, 1))
    }

    @Test
    fun `instanceof encoding`() {
        val asm = JvmAssembler()
        asm.instanceof_(25)
        val code = asm.toByteArray()
        assertEquals(3, code.size)
        assertEquals(0xC1, byte(code, 0))
        assertEquals(25, short(code, 1))
    }

    @Test
    fun `emit raw bytes`() {
        val asm = JvmAssembler()
        asm.emit(0xC2, 0xC3) // monitorenter, monitorexit
        val code = asm.toByteArray()
        assertEquals(2, code.size)
        assertEquals(0xC2, byte(code, 0))
        assertEquals(0xC3, byte(code, 1))
    }

    @Test
    fun `size tracks emitted bytes`() {
        val asm = JvmAssembler()
        assertEquals(0, asm.size)
        asm.iconst0()
        assertEquals(1, asm.size)
        asm.bipush(42)
        assertEquals(3, asm.size)
        asm.invokestatic(5)
        assertEquals(6, asm.size)
    }

    @Test
    fun `reset clears everything`() {
        val asm = JvmAssembler()
        asm.label("foo")
        asm.iload(0)
        asm.goto("foo")
        asm.toByteArray()
        asm.reset()
        assertEquals(0, asm.size)
        assertEquals(0, asm.toByteArray().size)
        assertNull(asm.labelOffset("foo"))
    }

    @Test
    fun `labelOffset returns correct value`() {
        val asm = JvmAssembler()
        asm.label("start")
        assertEquals(0, asm.labelOffset("start"))
        asm.nop()
        asm.nop()
        asm.label("mid")
        assertEquals(2, asm.labelOffset("mid"))
    }

    @Test
    fun `labelOffset returns null for undefined label`() {
        val asm = JvmAssembler()
        assertNull(asm.labelOffset("missing"))
    }

    @Test
    fun `add two ints pattern`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.iadd()
        asm.ireturn()
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0x1A, byte(code, 0))
        assertEquals(0x1B, byte(code, 1))
        assertEquals(0x60, byte(code, 2))
        assertEquals(0xAC, byte(code, 3))
    }

    @Test
    fun `max of two ints pattern`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.ifIcmpge("first")
        asm.iload(1)
        asm.ireturn()
        asm.label("first")
        asm.iload(0)
        asm.ireturn()
        val code = asm.toByteArray()
        assertEquals(0x9F + 3, byte(code, 2)) // ifIcmpge = 0xA2
        val target = short(code, 3)
        assertEquals(5, target) // skip iload_1 + ireturn
    }

    @Test
    fun `loop counter pattern`() {
        val asm = JvmAssembler()
        asm.iconst0()
        asm.istore(0)
        asm.label("loop")
        asm.iload(0)
        asm.bipush(10)
        asm.ifIcmpge("done")
        asm.iinc(0, 1)
        asm.goto("loop")
        asm.label("done")
        asm.return_()
        val code = asm.toByteArray()
        assertTrue(code.size > 0)
        val gotoOffset = signedShort(code, code.size - 4)
        assertTrue(gotoOffset < 0, "goto should jump backward")
    }

    @Test
    fun `object creation pattern`() {
        val asm = JvmAssembler()
        asm.new_(3)
        asm.dup()
        asm.invokespecial(4)
        asm.astore(0)
        asm.aload(0)
        asm.invokevirtual(5)
        asm.return_()
        val code = asm.toByteArray()
        assertEquals(13, code.size)
    }

    @Test
    fun `field access pattern`() {
        val asm = JvmAssembler()
        asm.aload(0)
        asm.getfield(10)
        asm.iconst1()
        asm.iadd()
        asm.aload(0)
        asm.swap()
        asm.putfield(10)
        asm.return_()
        val code = asm.toByteArray()
        assertEquals(12, code.size)
    }

    @Test
    fun `static field access pattern`() {
        val asm = JvmAssembler()
        asm.getstatic(5)
        asm.iconst1()
        asm.iadd()
        asm.putstatic(5)
        asm.return_()
        val code = asm.toByteArray()
        assertEquals(9, code.size)
    }

    @Test
    fun `null check pattern`() {
        val asm = JvmAssembler()
        asm.aload(0)
        asm.ifnull("isNull")
        asm.aload(0)
        asm.invokevirtual(5)
        asm.ireturn()
        asm.label("isNull")
        asm.iconst0()
        asm.ireturn()
        val code = asm.toByteArray()
        assertEquals(0xC6, byte(code, 1))
    }

    @Test
    fun `array creation and store pattern`() {
        val asm = JvmAssembler()
        asm.bipush(10)
        asm.newarray(10) // T_INT
        asm.astore(0)
        asm.aload(0)
        asm.iconst0()
        asm.bipush(42)
        asm.iastore()
        asm.return_()
        val code = asm.toByteArray()
        assertEquals(11, code.size)
    }

    @Test
    fun `reference array pattern`() {
        val asm = JvmAssembler()
        asm.bipush(5)
        asm.anewarray(3)
        asm.astore(0)
        asm.aload(0)
        asm.arraylength()
        asm.ireturn()
        val code = asm.toByteArray()
        assertEquals(9, code.size)
    }

    @Test
    fun `type cast pattern`() {
        val asm = JvmAssembler()
        asm.aload(0)
        asm.instanceof_(10)
        asm.ifeq("notInstance")
        asm.aload(0)
        asm.checkcast(10)
        asm.areturn()
        asm.label("notInstance")
        asm.aconstNull()
        asm.areturn()
        val code = asm.toByteArray()
        assertEquals(0xC1, byte(code, 1))
        assertEquals(0xC0, byte(code, 8))
    }

    @Test
    fun `exception throw pattern`() {
        val asm = JvmAssembler()
        asm.new_(5)
        asm.dup()
        asm.ldc(10)
        asm.invokespecial(6)
        asm.athrow()
        val code = asm.toByteArray()
        assertEquals(0xBF, byte(code, code.size - 1))
    }

    @Test
    fun `long arithmetic full set`() {
        val asm = JvmAssembler()
        asm.ladd()
        asm.lsub()
        asm.lmul()
        asm.ldiv()
        asm.lrem()
        asm.lneg()
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        assertEquals(0x61, byte(code, 0))
        assertEquals(0x65, byte(code, 1))
        assertEquals(0x69, byte(code, 2))
        assertEquals(0x6D, byte(code, 3))
        assertEquals(0x71, byte(code, 4))
        assertEquals(0x75, byte(code, 5))
    }

    @Test
    fun `float arithmetic full set`() {
        val asm = JvmAssembler()
        asm.fadd()
        asm.fsub()
        asm.fmul()
        asm.fdiv()
        asm.frem()
        asm.fneg()
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        assertEquals(0x62, byte(code, 0))
        assertEquals(0x66, byte(code, 1))
        assertEquals(0x6A, byte(code, 2))
        assertEquals(0x6E, byte(code, 3))
        assertEquals(0x72, byte(code, 4))
        assertEquals(0x76, byte(code, 5))
    }

    @Test
    fun `double arithmetic full set`() {
        val asm = JvmAssembler()
        asm.dadd()
        asm.dsub()
        asm.dmul()
        asm.ddiv()
        asm.drem()
        asm.dneg()
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        assertEquals(0x63, byte(code, 0))
        assertEquals(0x67, byte(code, 1))
        assertEquals(0x6B, byte(code, 2))
        assertEquals(0x6F, byte(code, 3))
        assertEquals(0x73, byte(code, 4))
        assertEquals(0x77, byte(code, 5))
    }

    @Test
    fun `long bitwise ops`() {
        val asm = JvmAssembler()
        asm.land()
        asm.lor()
        asm.lxor()
        asm.lshl()
        asm.lshr()
        asm.lushr()
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        assertEquals(0x7F, byte(code, 0))
        assertEquals(0x81, byte(code, 1))
        assertEquals(0x83, byte(code, 2))
        assertEquals(0x79, byte(code, 3))
        assertEquals(0x7B, byte(code, 4))
        assertEquals(0x7D, byte(code, 5))
    }

    @Test
    fun `integer bitwise ops`() {
        val asm = JvmAssembler()
        asm.iand()
        asm.ior()
        asm.ixor()
        asm.ishl()
        asm.ishr()
        asm.iushr()
        val code = asm.toByteArray()
        assertEquals(6, code.size)
        assertEquals(0x7E, byte(code, 0))
        assertEquals(0x80, byte(code, 1))
        assertEquals(0x82, byte(code, 2))
        assertEquals(0x78, byte(code, 3))
        assertEquals(0x7A, byte(code, 4))
        assertEquals(0x7C, byte(code, 5))
    }

    @Test
    fun `multiple labels in sequence`() {
        val asm = JvmAssembler()
        asm.label("a")
        asm.label("b")
        asm.nop()
        asm.label("c")
        assertEquals(0, asm.labelOffset("a"))
        assertEquals(0, asm.labelOffset("b"))
        assertEquals(1, asm.labelOffset("c"))
    }

    @Test
    fun `branch to self creates zero offset`() {
        val asm = JvmAssembler()
        asm.label("here")
        asm.goto("here")
        val code = asm.toByteArray()
        assertEquals(0, signedShort(code, 1))
    }

    @Test
    fun `long double load store wide`() {
        val asm = JvmAssembler()
        asm.lload(256)
        asm.lstore(257)
        asm.dload(258)
        asm.dstore(259)
        val code = asm.toByteArray()
        assertEquals(16, code.size)
        for (i in listOf(0, 4, 8, 12)) {
            assertEquals(0xC4, byte(code, i))
        }
    }

    @Test
    fun `fload fstore wide`() {
        val asm = JvmAssembler()
        asm.fload(300)
        asm.fstore(301)
        val code = asm.toByteArray()
        assertEquals(8, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x17, byte(code, 1))
        assertEquals(300, short(code, 2))
        assertEquals(0xC4, byte(code, 4))
        assertEquals(0x38, byte(code, 5))
        assertEquals(301, short(code, 6))
    }

    @Test
    fun `complex if else chain`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iflt("negative")
        asm.iload(0)
        asm.ifgt("positive")
        asm.iconst0()
        asm.ireturn()
        asm.label("negative")
        asm.iconstM1()
        asm.ireturn()
        asm.label("positive")
        asm.iconst1()
        asm.ireturn()
        val code = asm.toByteArray()
        assertTrue(code.size > 0)
        assertEquals(0x9B, byte(code, 1))
        assertEquals(0x9D, byte(code, 5))
    }

    @Test
    fun `toByteArray is idempotent`() {
        val asm = JvmAssembler()
        asm.goto("end")
        asm.label("end")
        asm.return_()
        val first = asm.toByteArray()
        val second = asm.toByteArray()
        assertArrayEquals(first, second)
    }

    @Test
    fun `emit single raw byte`() {
        val asm = JvmAssembler()
        asm.emit(0x00)
        val code = asm.toByteArray()
        assertEquals(1, code.size)
        assertEquals(0x00, byte(code, 0))
    }

    @Test
    fun `emit multiple raw bytes`() {
        val asm = JvmAssembler()
        asm.emit(0xAA, 0xBB, 0xCC, 0xDD)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xAA, byte(code, 0))
        assertEquals(0xBB, byte(code, 1))
        assertEquals(0xCC, byte(code, 2))
        assertEquals(0xDD, byte(code, 3))
    }

    @Test
    fun `method invocation pattern with interface`() {
        val asm = JvmAssembler()
        asm.aload(0)
        asm.invokeinterface(12, 1)
        asm.ireturn()
        val code = asm.toByteArray()
        assertEquals(7, code.size)
        assertEquals(0x2A, byte(code, 0))
        assertEquals(0xB9, byte(code, 1))
        assertEquals(12, short(code, 2))
        assertEquals(1, byte(code, 4))
        assertEquals(0, byte(code, 5))
        assertEquals(0xAC, byte(code, 6))
    }

    @Test
    fun `checkcast with large index`() {
        val asm = JvmAssembler()
        asm.checkcast(0xFFFE)
        val code = asm.toByteArray()
        assertEquals(0xFFFE, short(code, 1))
    }

    @Test
    fun `instanceof with large index`() {
        val asm = JvmAssembler()
        asm.instanceof_(0x1234)
        val code = asm.toByteArray()
        assertEquals(0x1234, short(code, 1))
    }

    @Test
    fun `new with large index`() {
        val asm = JvmAssembler()
        asm.new_(0xABCD)
        val code = asm.toByteArray()
        assertEquals(0xABCD, short(code, 1))
    }

    @Test
    fun `anewarray with large index`() {
        val asm = JvmAssembler()
        asm.anewarray(0x5678)
        val code = asm.toByteArray()
        assertEquals(0x5678, short(code, 1))
    }

    @Test
    fun `getstatic with index 1`() {
        val asm = JvmAssembler()
        asm.getstatic(1)
        val code = asm.toByteArray()
        assertEquals(1, short(code, 1))
    }

    @Test
    fun `putfield with index 0`() {
        val asm = JvmAssembler()
        asm.putfield(0)
        val code = asm.toByteArray()
        assertEquals(0, short(code, 1))
    }

    @Test
    fun `wide iload for index 1000`() {
        val asm = JvmAssembler()
        asm.iload(1000)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x15, byte(code, 1))
        assertEquals(1000, short(code, 2))
    }

    @Test
    fun `wide istore for index 1000`() {
        val asm = JvmAssembler()
        asm.istore(1000)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x36, byte(code, 1))
        assertEquals(1000, short(code, 2))
    }

    @Test
    fun `wide lstore for index 500`() {
        val asm = JvmAssembler()
        asm.lstore(500)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x37, byte(code, 1))
        assertEquals(500, short(code, 2))
    }

    @Test
    fun `wide dload for index 400`() {
        val asm = JvmAssembler()
        asm.dload(400)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x18, byte(code, 1))
        assertEquals(400, short(code, 2))
    }

    @Test
    fun `wide dstore for index 600`() {
        val asm = JvmAssembler()
        asm.dstore(600)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x39, byte(code, 1))
        assertEquals(600, short(code, 2))
    }

    @Test
    fun `wide fload for index 700`() {
        val asm = JvmAssembler()
        asm.fload(700)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x17, byte(code, 1))
        assertEquals(700, short(code, 2))
    }

    @Test
    fun `wide fstore for index 800`() {
        val asm = JvmAssembler()
        asm.fstore(800)
        val code = asm.toByteArray()
        assertEquals(4, code.size)
        assertEquals(0xC4, byte(code, 0))
        assertEquals(0x38, byte(code, 1))
        assertEquals(800, short(code, 2))
    }

    @Test
    fun `try catch pattern with labels`() {
        val asm = JvmAssembler()
        val tryStart = asm.size
        asm.label("tryStart")
        asm.aload(0)
        asm.invokevirtual(5)
        asm.ireturn()
        asm.label("tryEnd")
        asm.label("handler")
        asm.pop()
        asm.iconst0()
        asm.ireturn()

        val code = asm.toByteArray()
        assertEquals(0, asm.labelOffset("tryStart"))
        assertEquals(5, asm.labelOffset("tryEnd"))
        assertEquals(5, asm.labelOffset("handler"))
        assertTrue(code.size > 0)
    }

    @Test
    fun `fibonacci pattern`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iconst2()
        asm.ifIcmpge("recurse")
        asm.iload(0)
        asm.ireturn()
        asm.label("recurse")
        asm.iload(0)
        asm.iconst1()
        asm.isub()
        asm.invokestatic(1)
        asm.iload(0)
        asm.iconst2()
        asm.isub()
        asm.invokestatic(1)
        asm.iadd()
        asm.ireturn()
        val code = asm.toByteArray()
        assertTrue(code.size > 10)
    }

    @Test
    fun `string concat pattern with invokevirtual`() {
        val asm = JvmAssembler()
        asm.new_(10)
        asm.dup()
        asm.invokespecial(11)
        asm.aload(0)
        asm.invokevirtual(12)
        asm.aload(1)
        asm.invokevirtual(12)
        asm.invokevirtual(13)
        asm.areturn()
        val code = asm.toByteArray()
        assertEquals(19, code.size)
    }

    @Test
    fun `pushInt boundary values`() {
        val asm = JvmAssembler()
        asm.pushInt(-1)
        asm.pushInt(0)
        asm.pushInt(5)
        asm.pushInt(-128)
        asm.pushInt(127)
        asm.pushInt(-129)
        asm.pushInt(128)
        asm.pushInt(-32768)
        asm.pushInt(32767)
        val code = asm.toByteArray()
        assertEquals(1 + 1 + 1 + 2 + 2 + 3 + 3 + 3 + 3, code.size)
    }

    @Test
    fun `multiple resets allow reuse`() {
        val asm = JvmAssembler()
        asm.iconst0()
        asm.ireturn()
        val first = asm.toByteArray()

        asm.reset()
        asm.iconst1()
        asm.ireturn()
        val second = asm.toByteArray()

        assertEquals(2, first.size)
        assertEquals(2, second.size)
        assertEquals(0x03, byte(first, 0))
        assertEquals(0x04, byte(second, 0))
    }

    @Test
    fun `JvmOpCode fromCode covers all opcodes`() {
        for (op in JvmOpCode.entries) {
            assertEquals(op, JvmOpCode.fromCode(op.code))
        }
    }

    @Test
    fun `JvmOpCode fromCode returns null for invalid codes`() {
        assertNull(JvmOpCode.fromCode(0xFE))
        assertNull(JvmOpCode.fromCode(0xFF))
        assertNull(JvmOpCode.fromCode(-1))
        assertNull(JvmOpCode.fromCode(0xCA))
    }

    @Test
    fun `JvmOpCode code values are unique`() {
        val codes = JvmOpCode.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `long conversion chain`() {
        val asm = JvmAssembler()
        asm.i2l()
        asm.l2f()
        asm.f2d()
        asm.d2i()
        asm.i2b()
        asm.i2c()
        asm.i2s()
        val code = asm.toByteArray()
        assertEquals(7, code.size)
        assertEquals(0x85, byte(code, 0))
        assertEquals(0x89, byte(code, 1))
        assertEquals(0x8D, byte(code, 2))
        assertEquals(0x8E, byte(code, 3))
        assertEquals(0x91, byte(code, 4))
        assertEquals(0x92, byte(code, 5))
        assertEquals(0x93, byte(code, 6))
    }

    @Test
    fun `switch pattern with gotos`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.ifeq("case0")
        asm.iload(0)
        asm.iconst1()
        asm.ifIcmpeq("case1")
        asm.goto("default")
        asm.label("case0")
        asm.bipush(10)
        asm.ireturn()
        asm.label("case1")
        asm.bipush(20)
        asm.ireturn()
        asm.label("default")
        asm.bipush(30)
        asm.ireturn()
        val code = asm.toByteArray()
        assertTrue(code.size > 0)
    }

    @Test
    fun `double comparison pattern`() {
        val asm = JvmAssembler()
        asm.dload(0)
        asm.dload(2)
        asm.dcmpl()
        asm.iflt("less")
        asm.dload(0)
        asm.dload(2)
        asm.dcmpg()
        asm.ifgt("greater")
        asm.iconst0()
        asm.ireturn()
        asm.label("less")
        asm.iconstM1()
        asm.ireturn()
        asm.label("greater")
        asm.iconst1()
        asm.ireturn()
        val code = asm.toByteArray()
        assertTrue(code.size > 0)
    }

    @Test
    fun `float comparison pattern`() {
        val asm = JvmAssembler()
        asm.fload(0)
        asm.fload(1)
        asm.fcmpl()
        asm.iflt("less")
        asm.fload(0)
        asm.fload(1)
        asm.fcmpg()
        asm.ifgt("greater")
        asm.iconst0()
        asm.ireturn()
        asm.label("less")
        asm.iconstM1()
        asm.ireturn()
        asm.label("greater")
        asm.iconst1()
        asm.ireturn()
        val code = asm.toByteArray()
        assertTrue(code.size > 0)
    }

    @Test
    fun `long comparison pattern`() {
        val asm = JvmAssembler()
        asm.lload(0)
        asm.lload(2)
        asm.lcmp()
        asm.ifeq("equal")
        asm.iconst0()
        asm.ireturn()
        asm.label("equal")
        asm.iconst1()
        asm.ireturn()
        val code = asm.toByteArray()
        assertEquals(0x94, byte(code, 2))
    }

    @Test
    fun `dup x1 usage pattern`() {
        val asm = JvmAssembler()
        asm.aload(0)
        asm.iload(1)
        asm.dupX1()
        asm.putfield(5)
        asm.ireturn()
        val code = asm.toByteArray()
        assertEquals(0x5A, byte(code, 2))
    }

    @Test
    fun `dup x2 usage pattern`() {
        val asm = JvmAssembler()
        asm.aload(0)
        asm.iload(1)
        asm.iload(2)
        asm.dupX2()
        val code = asm.toByteArray()
        assertEquals(0x5B, byte(code, 3))
    }

    @Test
    fun `large method with many labels`() {
        val asm = JvmAssembler()
        for (i in 0 until 20) {
            asm.label("label$i")
            asm.nop()
        }
        val code = asm.toByteArray()
        assertEquals(20, code.size)
        for (i in 0 until 20) {
            assertEquals(i, asm.labelOffset("label$i"))
        }
    }

    @Test
    fun `chained branches form linked list`() {
        val asm = JvmAssembler()
        asm.goto("a")
        asm.label("a")
        asm.goto("b")
        asm.label("b")
        asm.goto("c")
        asm.label("c")
        asm.return_()
        val code = asm.toByteArray()
        assertEquals(10, code.size)
        assertEquals(3, short(code, 1))
        assertEquals(3, short(code, 4))
        assertEquals(3, short(code, 7))
    }
}
