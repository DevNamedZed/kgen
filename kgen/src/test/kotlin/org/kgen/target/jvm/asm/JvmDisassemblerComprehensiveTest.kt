package org.kgen.target.jvm.asm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.target.jvm.JvmOpCode

class JvmDisassemblerComprehensiveTest {

    private val dis = JvmDisassembler()

    @Test
    fun `disassemble empty bytecode returns empty list`() {
        assertTrue(dis.disassemble(ByteArray(0)).isEmpty())
    }

    @Test
    fun `disassemble nop`() {
        val asm = JvmAssembler()
        asm.nop()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(1, insns.size)
        assertEquals(JvmOpCode.NOP, insns[0].opcode)
        assertEquals(0, insns[0].offset)
        assertEquals(1, insns[0].size)
        assertEquals("", insns[0].operands)
    }

    @Test
    fun `disassemble aconst_null`() {
        val asm = JvmAssembler()
        asm.aconstNull()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.ACONST_NULL, insns[0].opcode)
    }

    @Test
    fun `disassemble iconst_m1`() {
        val asm = JvmAssembler()
        asm.iconstM1()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.ICONST_M1, insns[0].opcode)
    }

    @Test
    fun `disassemble iconst_0 through iconst_5`() {
        val asm = JvmAssembler()
        asm.iconst0()
        asm.iconst1()
        asm.iconst2()
        asm.iconst3()
        asm.iconst4()
        asm.iconst5()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        assertEquals(JvmOpCode.ICONST_0, insns[0].opcode)
        assertEquals(JvmOpCode.ICONST_1, insns[1].opcode)
        assertEquals(JvmOpCode.ICONST_2, insns[2].opcode)
        assertEquals(JvmOpCode.ICONST_3, insns[3].opcode)
        assertEquals(JvmOpCode.ICONST_4, insns[4].opcode)
        assertEquals(JvmOpCode.ICONST_5, insns[5].opcode)
    }

    @Test
    fun `disassemble lconst_0 and lconst_1`() {
        val asm = JvmAssembler()
        asm.lconst0()
        asm.lconst1()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(2, insns.size)
        assertEquals(JvmOpCode.LCONST_0, insns[0].opcode)
        assertEquals(JvmOpCode.LCONST_1, insns[1].opcode)
    }

    @Test
    fun `disassemble fconst_0, fconst_1, fconst_2`() {
        val asm = JvmAssembler()
        asm.fconst0()
        asm.fconst1()
        asm.fconst2()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(3, insns.size)
        assertEquals(JvmOpCode.FCONST_0, insns[0].opcode)
        assertEquals(JvmOpCode.FCONST_1, insns[1].opcode)
        assertEquals(JvmOpCode.FCONST_2, insns[2].opcode)
    }

    @Test
    fun `disassemble dconst_0 and dconst_1`() {
        val asm = JvmAssembler()
        asm.dconst0()
        asm.dconst1()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(2, insns.size)
        assertEquals(JvmOpCode.DCONST_0, insns[0].opcode)
        assertEquals(JvmOpCode.DCONST_1, insns[1].opcode)
    }

    @Test
    fun `disassemble bipush positive`() {
        val asm = JvmAssembler()
        asm.bipush(42)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(1, insns.size)
        assertEquals(JvmOpCode.BIPUSH, insns[0].opcode)
        assertEquals("42", insns[0].operands)
        assertEquals(2, insns[0].size)
    }

    @Test
    fun `disassemble bipush negative`() {
        val asm = JvmAssembler()
        asm.bipush(-10)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.BIPUSH, insns[0].opcode)
        assertEquals("-10", insns[0].operands)
    }

    @Test
    fun `disassemble sipush positive`() {
        val asm = JvmAssembler()
        asm.sipush(1000)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.SIPUSH, insns[0].opcode)
        assertEquals("1000", insns[0].operands)
        assertEquals(3, insns[0].size)
    }

    @Test
    fun `disassemble sipush negative`() {
        val asm = JvmAssembler()
        asm.sipush(-500)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.SIPUSH, insns[0].opcode)
        assertEquals("-500", insns[0].operands)
    }

    @Test
    fun `disassemble ldc with small index`() {
        val asm = JvmAssembler()
        asm.ldc(5)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.LDC, insns[0].opcode)
        assertEquals("#5", insns[0].operands)
        assertEquals(2, insns[0].size)
    }

    @Test
    fun `disassemble ldc_w with large index`() {
        val asm = JvmAssembler()
        asm.ldc(300)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.LDC_W, insns[0].opcode)
        assertEquals("#300", insns[0].operands)
        assertEquals(3, insns[0].size)
    }

    @Test
    fun `disassemble ldc2_w`() {
        val asm = JvmAssembler()
        asm.ldc2w(7)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.LDC2_W, insns[0].opcode)
        assertEquals("#7", insns[0].operands)
        assertEquals(3, insns[0].size)
    }

    @Test
    fun `disassemble iload_0 through iload_3`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.iload(2)
        asm.iload(3)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.ILOAD_0, insns[0].opcode)
        assertEquals(JvmOpCode.ILOAD_1, insns[1].opcode)
        assertEquals(JvmOpCode.ILOAD_2, insns[2].opcode)
        assertEquals(JvmOpCode.ILOAD_3, insns[3].opcode)
    }

    @Test
    fun `disassemble iload with index above 3`() {
        val asm = JvmAssembler()
        asm.iload(10)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.ILOAD, insns[0].opcode)
        assertEquals("10", insns[0].operands)
        assertEquals(2, insns[0].size)
    }

    @Test
    fun `disassemble lload_0 through lload_3`() {
        val asm = JvmAssembler()
        asm.lload(0)
        asm.lload(1)
        asm.lload(2)
        asm.lload(3)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.LLOAD_0, insns[0].opcode)
        assertEquals(JvmOpCode.LLOAD_1, insns[1].opcode)
        assertEquals(JvmOpCode.LLOAD_2, insns[2].opcode)
        assertEquals(JvmOpCode.LLOAD_3, insns[3].opcode)
    }

    @Test
    fun `disassemble fload_0 through fload_3`() {
        val asm = JvmAssembler()
        asm.fload(0)
        asm.fload(1)
        asm.fload(2)
        asm.fload(3)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.FLOAD_0, insns[0].opcode)
        assertEquals(JvmOpCode.FLOAD_3, insns[3].opcode)
    }

    @Test
    fun `disassemble dload_0 through dload_3`() {
        val asm = JvmAssembler()
        asm.dload(0)
        asm.dload(1)
        asm.dload(2)
        asm.dload(3)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.DLOAD_0, insns[0].opcode)
        assertEquals(JvmOpCode.DLOAD_3, insns[3].opcode)
    }

    @Test
    fun `disassemble aload_0 through aload_3`() {
        val asm = JvmAssembler()
        asm.aload(0)
        asm.aload(1)
        asm.aload(2)
        asm.aload(3)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.ALOAD_0, insns[0].opcode)
        assertEquals(JvmOpCode.ALOAD_3, insns[3].opcode)
    }

    @Test
    fun `disassemble aload with index above 3`() {
        val asm = JvmAssembler()
        asm.aload(5)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.ALOAD, insns[0].opcode)
        assertEquals("5", insns[0].operands)
    }

    @Test
    fun `disassemble array load instructions`() {
        val asm = JvmAssembler()
        asm.iaload()
        asm.laload()
        asm.faload()
        asm.daload()
        asm.aaload()
        asm.baload()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        assertEquals(JvmOpCode.IALOAD, insns[0].opcode)
        assertEquals(JvmOpCode.LALOAD, insns[1].opcode)
        assertEquals(JvmOpCode.FALOAD, insns[2].opcode)
        assertEquals(JvmOpCode.DALOAD, insns[3].opcode)
        assertEquals(JvmOpCode.AALOAD, insns[4].opcode)
        assertEquals(JvmOpCode.BALOAD, insns[5].opcode)
    }

    @Test
    fun `disassemble istore_0 through istore_3`() {
        val asm = JvmAssembler()
        asm.istore(0)
        asm.istore(1)
        asm.istore(2)
        asm.istore(3)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.ISTORE_0, insns[0].opcode)
        assertEquals(JvmOpCode.ISTORE_1, insns[1].opcode)
        assertEquals(JvmOpCode.ISTORE_2, insns[2].opcode)
        assertEquals(JvmOpCode.ISTORE_3, insns[3].opcode)
    }

    @Test
    fun `disassemble istore with index above 3`() {
        val asm = JvmAssembler()
        asm.istore(10)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.ISTORE, insns[0].opcode)
        assertEquals("10", insns[0].operands)
    }

    @Test
    fun `disassemble lstore_0 through lstore_3`() {
        val asm = JvmAssembler()
        asm.lstore(0)
        asm.lstore(1)
        asm.lstore(2)
        asm.lstore(3)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.LSTORE_0, insns[0].opcode)
        assertEquals(JvmOpCode.LSTORE_3, insns[3].opcode)
    }

    @Test
    fun `disassemble fstore_0 through fstore_3`() {
        val asm = JvmAssembler()
        asm.fstore(0)
        asm.fstore(1)
        asm.fstore(2)
        asm.fstore(3)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.FSTORE_0, insns[0].opcode)
        assertEquals(JvmOpCode.FSTORE_3, insns[3].opcode)
    }

    @Test
    fun `disassemble dstore_0 through dstore_3`() {
        val asm = JvmAssembler()
        asm.dstore(0)
        asm.dstore(1)
        asm.dstore(2)
        asm.dstore(3)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.DSTORE_0, insns[0].opcode)
        assertEquals(JvmOpCode.DSTORE_3, insns[3].opcode)
    }

    @Test
    fun `disassemble astore_0 through astore_3`() {
        val asm = JvmAssembler()
        asm.astore(0)
        asm.astore(1)
        asm.astore(2)
        asm.astore(3)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.ASTORE_0, insns[0].opcode)
        assertEquals(JvmOpCode.ASTORE_3, insns[3].opcode)
    }

    @Test
    fun `disassemble astore with index above 3`() {
        val asm = JvmAssembler()
        asm.astore(7)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.ASTORE, insns[0].opcode)
        assertEquals("7", insns[0].operands)
    }

    @Test
    fun `disassemble array store instructions`() {
        val asm = JvmAssembler()
        asm.iastore()
        asm.lastore()
        asm.fastore()
        asm.dastore()
        asm.aastore()
        asm.bastore()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        assertEquals(JvmOpCode.IASTORE, insns[0].opcode)
        assertEquals(JvmOpCode.LASTORE, insns[1].opcode)
        assertEquals(JvmOpCode.FASTORE, insns[2].opcode)
        assertEquals(JvmOpCode.DASTORE, insns[3].opcode)
        assertEquals(JvmOpCode.AASTORE, insns[4].opcode)
        assertEquals(JvmOpCode.BASTORE, insns[5].opcode)
    }

    @Test
    fun `disassemble stack manipulation instructions`() {
        val asm = JvmAssembler()
        asm.pop()
        asm.pop2()
        asm.dup()
        asm.dupX1()
        asm.dupX2()
        asm.dup2()
        asm.swap()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(7, insns.size)
        assertEquals(JvmOpCode.POP, insns[0].opcode)
        assertEquals(JvmOpCode.POP2, insns[1].opcode)
        assertEquals(JvmOpCode.DUP, insns[2].opcode)
        assertEquals(JvmOpCode.DUP_X1, insns[3].opcode)
        assertEquals(JvmOpCode.DUP_X2, insns[4].opcode)
        assertEquals(JvmOpCode.DUP2, insns[5].opcode)
        assertEquals(JvmOpCode.SWAP, insns[6].opcode)
    }

    @Test
    fun `disassemble int arithmetic`() {
        val asm = JvmAssembler()
        asm.iadd()
        asm.isub()
        asm.imul()
        asm.idiv()
        asm.irem()
        asm.ineg()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        assertEquals(JvmOpCode.IADD, insns[0].opcode)
        assertEquals(JvmOpCode.ISUB, insns[1].opcode)
        assertEquals(JvmOpCode.IMUL, insns[2].opcode)
        assertEquals(JvmOpCode.IDIV, insns[3].opcode)
        assertEquals(JvmOpCode.IREM, insns[4].opcode)
        assertEquals(JvmOpCode.INEG, insns[5].opcode)
    }

    @Test
    fun `disassemble long arithmetic`() {
        val asm = JvmAssembler()
        asm.ladd()
        asm.lsub()
        asm.lmul()
        asm.ldiv()
        asm.lrem()
        asm.lneg()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        assertEquals(JvmOpCode.LADD, insns[0].opcode)
        assertEquals(JvmOpCode.LSUB, insns[1].opcode)
        assertEquals(JvmOpCode.LMUL, insns[2].opcode)
        assertEquals(JvmOpCode.LDIV, insns[3].opcode)
        assertEquals(JvmOpCode.LREM, insns[4].opcode)
        assertEquals(JvmOpCode.LNEG, insns[5].opcode)
    }

    @Test
    fun `disassemble float arithmetic`() {
        val asm = JvmAssembler()
        asm.fadd()
        asm.fsub()
        asm.fmul()
        asm.fdiv()
        asm.frem()
        asm.fneg()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        assertEquals(JvmOpCode.FADD, insns[0].opcode)
        assertEquals(JvmOpCode.FSUB, insns[1].opcode)
        assertEquals(JvmOpCode.FMUL, insns[2].opcode)
        assertEquals(JvmOpCode.FDIV, insns[3].opcode)
        assertEquals(JvmOpCode.FREM, insns[4].opcode)
        assertEquals(JvmOpCode.FNEG, insns[5].opcode)
    }

    @Test
    fun `disassemble double arithmetic`() {
        val asm = JvmAssembler()
        asm.dadd()
        asm.dsub()
        asm.dmul()
        asm.ddiv()
        asm.drem()
        asm.dneg()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        assertEquals(JvmOpCode.DADD, insns[0].opcode)
        assertEquals(JvmOpCode.DSUB, insns[1].opcode)
        assertEquals(JvmOpCode.DMUL, insns[2].opcode)
        assertEquals(JvmOpCode.DDIV, insns[3].opcode)
        assertEquals(JvmOpCode.DREM, insns[4].opcode)
        assertEquals(JvmOpCode.DNEG, insns[5].opcode)
    }

    @Test
    fun `disassemble int bitwise operations`() {
        val asm = JvmAssembler()
        asm.ishl()
        asm.ishr()
        asm.iushr()
        asm.iand()
        asm.ior()
        asm.ixor()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        assertEquals(JvmOpCode.ISHL, insns[0].opcode)
        assertEquals(JvmOpCode.ISHR, insns[1].opcode)
        assertEquals(JvmOpCode.IUSHR, insns[2].opcode)
        assertEquals(JvmOpCode.IAND, insns[3].opcode)
        assertEquals(JvmOpCode.IOR, insns[4].opcode)
        assertEquals(JvmOpCode.IXOR, insns[5].opcode)
    }

    @Test
    fun `disassemble long bitwise operations`() {
        val asm = JvmAssembler()
        asm.lshl()
        asm.lshr()
        asm.lushr()
        asm.land()
        asm.lor()
        asm.lxor()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        assertEquals(JvmOpCode.LSHL, insns[0].opcode)
        assertEquals(JvmOpCode.LSHR, insns[1].opcode)
        assertEquals(JvmOpCode.LUSHR, insns[2].opcode)
        assertEquals(JvmOpCode.LAND, insns[3].opcode)
        assertEquals(JvmOpCode.LOR, insns[4].opcode)
        assertEquals(JvmOpCode.LXOR, insns[5].opcode)
    }

    @Test
    fun `disassemble iinc`() {
        val asm = JvmAssembler()
        asm.iinc(0, 1)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(1, insns.size)
        assertEquals(JvmOpCode.IINC, insns[0].opcode)
        assertEquals("0, 1", insns[0].operands)
        assertEquals(3, insns[0].size)
    }

    @Test
    fun `disassemble iinc with negative increment`() {
        val asm = JvmAssembler()
        asm.iinc(2, -1)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.IINC, insns[0].opcode)
        assertEquals("2, -1", insns[0].operands)
    }

    @Test
    fun `disassemble int to long conversions`() {
        val asm = JvmAssembler()
        asm.i2l()
        asm.i2f()
        asm.i2d()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(3, insns.size)
        assertEquals(JvmOpCode.I2L, insns[0].opcode)
        assertEquals(JvmOpCode.I2F, insns[1].opcode)
        assertEquals(JvmOpCode.I2D, insns[2].opcode)
    }

    @Test
    fun `disassemble long to other type conversions`() {
        val asm = JvmAssembler()
        asm.l2i()
        asm.l2f()
        asm.l2d()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(3, insns.size)
        assertEquals(JvmOpCode.L2I, insns[0].opcode)
        assertEquals(JvmOpCode.L2F, insns[1].opcode)
        assertEquals(JvmOpCode.L2D, insns[2].opcode)
    }

    @Test
    fun `disassemble float to other type conversions`() {
        val asm = JvmAssembler()
        asm.f2i()
        asm.f2l()
        asm.f2d()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(3, insns.size)
        assertEquals(JvmOpCode.F2I, insns[0].opcode)
        assertEquals(JvmOpCode.F2L, insns[1].opcode)
        assertEquals(JvmOpCode.F2D, insns[2].opcode)
    }

    @Test
    fun `disassemble double to other type conversions`() {
        val asm = JvmAssembler()
        asm.d2i()
        asm.d2l()
        asm.d2f()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(3, insns.size)
        assertEquals(JvmOpCode.D2I, insns[0].opcode)
        assertEquals(JvmOpCode.D2L, insns[1].opcode)
        assertEquals(JvmOpCode.D2F, insns[2].opcode)
    }

    @Test
    fun `disassemble narrowing int conversions`() {
        val asm = JvmAssembler()
        asm.i2b()
        asm.i2c()
        asm.i2s()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(3, insns.size)
        assertEquals(JvmOpCode.I2B, insns[0].opcode)
        assertEquals(JvmOpCode.I2C, insns[1].opcode)
        assertEquals(JvmOpCode.I2S, insns[2].opcode)
    }

    @Test
    fun `disassemble comparison instructions`() {
        val asm = JvmAssembler()
        asm.lcmp()
        asm.fcmpl()
        asm.fcmpg()
        asm.dcmpl()
        asm.dcmpg()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(5, insns.size)
        assertEquals(JvmOpCode.LCMP, insns[0].opcode)
        assertEquals(JvmOpCode.FCMPL, insns[1].opcode)
        assertEquals(JvmOpCode.FCMPG, insns[2].opcode)
        assertEquals(JvmOpCode.DCMPL, insns[3].opcode)
        assertEquals(JvmOpCode.DCMPG, insns[4].opcode)
    }

    @Test
    fun `disassemble all return instructions`() {
        val asm = JvmAssembler()
        asm.ireturn()
        asm.lreturn()
        asm.freturn()
        asm.dreturn()
        asm.areturn()
        asm.return_()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        assertEquals(JvmOpCode.IRETURN, insns[0].opcode)
        assertEquals(JvmOpCode.LRETURN, insns[1].opcode)
        assertEquals(JvmOpCode.FRETURN, insns[2].opcode)
        assertEquals(JvmOpCode.DRETURN, insns[3].opcode)
        assertEquals(JvmOpCode.ARETURN, insns[4].opcode)
        assertEquals(JvmOpCode.RETURN, insns[5].opcode)
    }

    @Test
    fun `disassemble ifeq with forward branch`() {
        val asm = JvmAssembler()
        asm.iload(0)         // 0: iload_0
        asm.ifeq("target")   // 1: ifeq -> 6
        asm.iconst1()         // 4: iconst_1
        asm.ireturn()         // 5: ireturn
        asm.label("target")
        asm.iconst0()         // 6: iconst_0
        asm.ireturn()         // 7: ireturn
        val insns = dis.disassemble(asm.toByteArray())
        val ifeq = insns.first { it.opcode == JvmOpCode.IFEQ }
        assertEquals("6", ifeq.operands)
        assertEquals(3, ifeq.size)
    }

    @Test
    fun `disassemble ifne branch`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.ifne("target")
        asm.iconst0()
        asm.ireturn()
        asm.label("target")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        val ifne = insns.first { it.opcode == JvmOpCode.IFNE }
        assertEquals("6", ifne.operands)
    }

    @Test
    fun `disassemble iflt branch`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iflt("neg")
        asm.iconst0()
        asm.ireturn()
        asm.label("neg")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IFLT })
    }

    @Test
    fun `disassemble ifge branch`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.ifge("pos")
        asm.iconst0()
        asm.ireturn()
        asm.label("pos")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IFGE })
    }

    @Test
    fun `disassemble ifgt branch`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.ifgt("pos")
        asm.iconst0()
        asm.ireturn()
        asm.label("pos")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IFGT })
    }

    @Test
    fun `disassemble ifle branch`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.ifle("leq")
        asm.iconst0()
        asm.ireturn()
        asm.label("leq")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IFLE })
    }

    @Test
    fun `disassemble if_icmpeq branch`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.ifIcmpeq("eq")
        asm.iconst0()
        asm.ireturn()
        asm.label("eq")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IF_ICMPEQ })
    }

    @Test
    fun `disassemble if_icmpne branch`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.ifIcmpne("ne")
        asm.iconst0()
        asm.ireturn()
        asm.label("ne")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IF_ICMPNE })
    }

    @Test
    fun `disassemble if_icmplt branch`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.ifIcmplt("lt")
        asm.iconst0()
        asm.ireturn()
        asm.label("lt")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IF_ICMPLT })
    }

    @Test
    fun `disassemble if_icmpge branch`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.ifIcmpge("ge")
        asm.iconst0()
        asm.ireturn()
        asm.label("ge")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IF_ICMPGE })
    }

    @Test
    fun `disassemble if_icmpgt branch`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.ifIcmpgt("gt")
        asm.iconst0()
        asm.ireturn()
        asm.label("gt")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IF_ICMPGT })
    }

    @Test
    fun `disassemble if_icmple branch`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.ifIcmple("le")
        asm.iconst0()
        asm.ireturn()
        asm.label("le")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IF_ICMPLE })
    }

    @Test
    fun `disassemble goto with backward branch`() {
        val asm = JvmAssembler()
        asm.label("top")
        asm.iload(0)  // 0
        asm.ireturn()  // 1
        asm.goto("top") // 2: goto -> 0
        val insns = dis.disassemble(asm.toByteArray())
        val goto = insns.first { it.opcode == JvmOpCode.GOTO }
        assertEquals("0", goto.operands) // target offset = 0
    }

    @Test
    fun `disassemble ifnull and ifnonnull`() {
        val asm = JvmAssembler()
        asm.aload(0)
        asm.ifnull("isNull")
        asm.iconst1()
        asm.ireturn()
        asm.label("isNull")
        asm.iconst0()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IFNULL })
    }

    @Test
    fun `disassemble getstatic`() {
        val asm = JvmAssembler()
        asm.getstatic(15)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(1, insns.size)
        assertEquals(JvmOpCode.GETSTATIC, insns[0].opcode)
        assertEquals("#15", insns[0].operands)
        assertEquals(3, insns[0].size)
    }

    @Test
    fun `disassemble putstatic`() {
        val asm = JvmAssembler()
        asm.putstatic(20)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.PUTSTATIC, insns[0].opcode)
        assertEquals("#20", insns[0].operands)
    }

    @Test
    fun `disassemble getfield`() {
        val asm = JvmAssembler()
        asm.getfield(25)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.GETFIELD, insns[0].opcode)
        assertEquals("#25", insns[0].operands)
    }

    @Test
    fun `disassemble putfield`() {
        val asm = JvmAssembler()
        asm.putfield(30)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.PUTFIELD, insns[0].opcode)
        assertEquals("#30", insns[0].operands)
    }

    @Test
    fun `disassemble invokevirtual`() {
        val asm = JvmAssembler()
        asm.invokevirtual(10)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.INVOKEVIRTUAL, insns[0].opcode)
        assertEquals("#10", insns[0].operands)
        assertEquals(3, insns[0].size)
    }

    @Test
    fun `disassemble invokespecial`() {
        val asm = JvmAssembler()
        asm.invokespecial(12)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.INVOKESPECIAL, insns[0].opcode)
        assertEquals("#12", insns[0].operands)
    }

    @Test
    fun `disassemble invokestatic`() {
        val asm = JvmAssembler()
        asm.invokestatic(14)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.INVOKESTATIC, insns[0].opcode)
        assertEquals("#14", insns[0].operands)
    }

    @Test
    fun `disassemble invokeinterface`() {
        val asm = JvmAssembler()
        asm.invokeinterface(16, 2)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.INVOKEINTERFACE, insns[0].opcode)
        assertEquals("#16, 2", insns[0].operands)
        assertEquals(5, insns[0].size)
    }

    @Test
    fun `disassemble new`() {
        val asm = JvmAssembler()
        asm.new_(8)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.NEW, insns[0].opcode)
        assertEquals("#8", insns[0].operands)
        assertEquals(3, insns[0].size)
    }

    @Test
    fun `disassemble newarray with all primitive types`() {
        val typeNames = mapOf(
            4 to "boolean", 5 to "char", 6 to "float", 7 to "double",
            8 to "byte", 9 to "short", 10 to "int", 11 to "long"
        )
        for ((code, name) in typeNames) {
            val asm = JvmAssembler()
            asm.newarray(code)
            val insns = dis.disassemble(asm.toByteArray())
            assertEquals(JvmOpCode.NEWARRAY, insns[0].opcode)
            assertEquals(name, insns[0].operands, "newarray type $code should be $name")
            assertEquals(2, insns[0].size)
        }
    }

    @Test
    fun `disassemble anewarray`() {
        val asm = JvmAssembler()
        asm.anewarray(9)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.ANEWARRAY, insns[0].opcode)
        assertEquals("#9", insns[0].operands)
        assertEquals(3, insns[0].size)
    }

    @Test
    fun `disassemble arraylength`() {
        val asm = JvmAssembler()
        asm.arraylength()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.ARRAYLENGTH, insns[0].opcode)
        assertEquals(1, insns[0].size)
    }

    @Test
    fun `disassemble athrow`() {
        val asm = JvmAssembler()
        asm.athrow()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.ATHROW, insns[0].opcode)
        assertEquals(1, insns[0].size)
    }

    @Test
    fun `disassemble checkcast`() {
        val asm = JvmAssembler()
        asm.checkcast(11)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.CHECKCAST, insns[0].opcode)
        assertEquals("#11", insns[0].operands)
        assertEquals(3, insns[0].size)
    }

    @Test
    fun `disassemble instanceof`() {
        val asm = JvmAssembler()
        asm.instanceof_(13)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(JvmOpCode.INSTANCEOF, insns[0].opcode)
        assertEquals("#13", insns[0].operands)
        assertEquals(3, insns[0].size)
    }

    @Test
    fun `disassemble wide iload`() {
        // wide iload 256 (index > 255 triggers wide encoding)
        val code = byteArrayOf(
            0xC4.toByte(),    // wide
            0x15,             // iload
            0x01, 0x00,       // index 256
        )
        val insns = dis.disassemble(code)
        assertEquals(1, insns.size)
        assertEquals(JvmOpCode.WIDE, insns[0].opcode)
        assertTrue(insns[0].operands.contains("iload"))
        assertTrue(insns[0].operands.contains("256"))
        assertEquals(4, insns[0].size)
    }

    @Test
    fun `disassemble wide iinc`() {
        // wide iinc 300, 500
        val code = byteArrayOf(
            0xC4.toByte(),    // wide
            0x84.toByte(),    // iinc
            0x01, 0x2C,       // index 300
            0x01, 0xF4.toByte(), // increment 500
        )
        val insns = dis.disassemble(code)
        assertEquals(1, insns.size)
        assertEquals(JvmOpCode.WIDE, insns[0].opcode)
        assertTrue(insns[0].operands.contains("iinc"))
        assertTrue(insns[0].operands.contains("300"))
        assertTrue(insns[0].operands.contains("500"))
        assertEquals(6, insns[0].size)
    }

    @Test
    fun `disassemble wide istore`() {
        val code = byteArrayOf(
            0xC4.toByte(),    // wide
            0x36,             // istore
            0x01, 0x00,       // index 256
        )
        val insns = dis.disassemble(code)
        assertEquals(1, insns.size)
        assertEquals(JvmOpCode.WIDE, insns[0].opcode)
        assertTrue(insns[0].operands.contains("istore"))
        assertEquals(4, insns[0].size)
    }

    @Test
    fun `disassemble invokedynamic`() {
        // invokedynamic #5, 0, 0
        val code = byteArrayOf(
            0xBA.toByte(),    // invokedynamic
            0x00, 0x05,       // index 5
            0x00, 0x00,       // must be zero
        )
        val insns = dis.disassemble(code)
        assertEquals(1, insns.size)
        assertEquals(JvmOpCode.INVOKEDYNAMIC, insns[0].opcode)
        assertEquals("#5", insns[0].operands)
        assertEquals(5, insns[0].size)
    }

    @Test
    fun `disassemble multianewarray`() {
        // multianewarray #10, 2
        val code = byteArrayOf(
            0xC5.toByte(),    // multianewarray
            0x00, 0x0A,       // index 10
            0x02,             // 2 dimensions
        )
        val insns = dis.disassemble(code)
        assertEquals(1, insns.size)
        assertEquals(JvmOpCode.MULTIANEWARRAY, insns[0].opcode)
        assertEquals("#10, 2", insns[0].operands)
        assertEquals(4, insns[0].size)
    }

    @Test
    fun `disassemble tableswitch`() {
        // tableswitch at offset 0: need 3 bytes padding
        // opcode at 0, padding 1,2,3, default at 4, low at 8, high at 12, then offsets
        val code = byteArrayOf(
            0xAA.toByte(),    // tableswitch
            0, 0, 0,          // padding (align to 4)
            0, 0, 0, 20,      // default offset = 20
            0, 0, 0, 1,       // low = 1
            0, 0, 0, 3,       // high = 3
            0, 0, 0, 10,      // offset for case 1
            0, 0, 0, 15,      // offset for case 2
            0, 0, 0, 20,      // offset for case 3
        )
        val insns = dis.disassemble(code)
        assertEquals(1, insns.size)
        assertEquals(JvmOpCode.TABLESWITCH, insns[0].opcode)
        assertTrue(insns[0].operands.contains("1 to 3"))
        assertTrue(insns[0].operands.contains("default: 20"))
    }

    @Test
    fun `disassemble lookupswitch`() {
        // lookupswitch at offset 0: need 3 bytes padding
        val code = byteArrayOf(
            0xAB.toByte(),    // lookupswitch
            0, 0, 0,          // padding (align to 4)
            0, 0, 0, 30,      // default offset = 30
            0, 0, 0, 2,       // npairs = 2
            0, 0, 0, 1,       // match 1
            0, 0, 0, 20,      // offset 1
            0, 0, 0, 5,       // match 2
            0, 0, 0, 25,      // offset 2
        )
        val insns = dis.disassemble(code)
        assertEquals(1, insns.size)
        assertEquals(JvmOpCode.LOOKUPSWITCH, insns[0].opcode)
        assertTrue(insns[0].operands.contains("2 pairs"))
        assertTrue(insns[0].operands.contains("default: 30"))
    }

    @Test
    fun `disassemble tableswitch at non-zero offset`() {
        // Put a nop before tableswitch so it starts at offset 1
        // At offset 1: need 2 bytes padding to align to offset 4
        val code = byteArrayOf(
            0x00,             // nop at offset 0
            0xAA.toByte(),    // tableswitch at offset 1
            0, 0,             // padding (align to 4)
            0, 0, 0, 20,      // default offset = 20
            0, 0, 0, 0,       // low = 0
            0, 0, 0, 1,       // high = 1
            0, 0, 0, 10,      // offset for case 0
            0, 0, 0, 15,      // offset for case 1
        )
        val insns = dis.disassemble(code)
        assertEquals(2, insns.size)
        assertEquals(JvmOpCode.NOP, insns[0].opcode)
        assertEquals(JvmOpCode.TABLESWITCH, insns[1].opcode)
        assertEquals(1, insns[1].offset)
    }

    @Test
    fun `disassemble multi-instruction sequence add two ints`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.iadd()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.ILOAD_0, insns[0].opcode)
        assertEquals(JvmOpCode.ILOAD_1, insns[1].opcode)
        assertEquals(JvmOpCode.IADD, insns[2].opcode)
        assertEquals(JvmOpCode.IRETURN, insns[3].opcode)
    }

    @Test
    fun `disassemble multi-instruction sequence with stores`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.iload(1)
        asm.iadd()
        asm.istore(2)
        asm.iload(2)
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        assertEquals(JvmOpCode.ISTORE_2, insns[3].opcode)
        assertEquals(JvmOpCode.ILOAD_2, insns[4].opcode)
    }

    @Test
    fun `offsets are contiguous in multi-instruction sequence`() {
        val asm = JvmAssembler()
        asm.bipush(42)        // 2 bytes
        asm.sipush(1000)      // 3 bytes
        asm.iload(10)         // 2 bytes
        asm.invokestatic(20)  // 3 bytes
        asm.ireturn()         // 1 byte
        val code = asm.toByteArray()
        val insns = dis.disassemble(code)

        var expectedOffset = 0
        for (insn in insns) {
            assertEquals(expectedOffset, insn.offset)
            expectedOffset += insn.size
        }
        assertEquals(code.size, expectedOffset)
    }

    @Test
    fun `disassemble sequence with branch and label`() {
        val asm = JvmAssembler()
        asm.iload(0)
        asm.ifeq("zero")
        asm.iconst1()
        asm.goto("end")
        asm.label("zero")
        asm.iconst0()
        asm.label("end")
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        // ifeq should point to iconst_0 (the "zero" label)
        val ifeq = insns[1]
        assertEquals(JvmOpCode.IFEQ, ifeq.opcode)
        val gotoInsn = insns[3]
        assertEquals(JvmOpCode.GOTO, gotoInsn.opcode)
    }

    @Test
    fun `disassemble loop pattern`() {
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
        asm.iload(0)
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IF_ICMPGE })
        assertTrue(insns.any { it.opcode == JvmOpCode.GOTO })
        assertTrue(insns.any { it.opcode == JvmOpCode.IINC })
    }

    @Test
    fun `disassemble object creation pattern`() {
        val asm = JvmAssembler()
        asm.new_(5)
        asm.dup()
        asm.invokespecial(10)
        asm.areturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.NEW, insns[0].opcode)
        assertEquals(JvmOpCode.DUP, insns[1].opcode)
        assertEquals(JvmOpCode.INVOKESPECIAL, insns[2].opcode)
        assertEquals(JvmOpCode.ARETURN, insns[3].opcode)
    }

    @Test
    fun `disassemble field access pattern`() {
        val asm = JvmAssembler()
        asm.aload(0)
        asm.getfield(5)
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(3, insns.size)
        assertEquals(JvmOpCode.ALOAD_0, insns[0].opcode)
        assertEquals(JvmOpCode.GETFIELD, insns[1].opcode)
        assertEquals(JvmOpCode.IRETURN, insns[2].opcode)
    }

    @Test
    fun `disassemble static field write pattern`() {
        val asm = JvmAssembler()
        asm.bipush(42)
        asm.putstatic(7)
        asm.return_()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(3, insns.size)
        assertEquals(JvmOpCode.BIPUSH, insns[0].opcode)
        assertEquals(JvmOpCode.PUTSTATIC, insns[1].opcode)
        assertEquals(JvmOpCode.RETURN, insns[2].opcode)
    }

    @Test
    fun `disassemble instance field write pattern`() {
        val asm = JvmAssembler()
        asm.aload(0)
        asm.bipush(42)
        asm.putfield(5)
        asm.return_()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.PUTFIELD, insns[2].opcode)
    }

    @Test
    fun `toString format for instruction without operands`() {
        val insn = JvmDisassembler.JvmInstruction(0, JvmOpCode.IADD, "", 1)
        assertEquals("0: iadd", insn.toString())
    }

    @Test
    fun `toString format for instruction with operands`() {
        val insn = JvmDisassembler.JvmInstruction(5, JvmOpCode.BIPUSH, "42", 2)
        assertEquals("5: bipush 42", insn.toString())
    }

    @Test
    fun `toString format for invokestatic`() {
        val insn = JvmDisassembler.JvmInstruction(10, JvmOpCode.INVOKESTATIC, "#25", 3)
        assertEquals("10: invokestatic #25", insn.toString())
    }

    @Test
    fun `toString format for branch instruction`() {
        val insn = JvmDisassembler.JvmInstruction(3, JvmOpCode.IFEQ, "15", 3)
        assertEquals("3: ifeq 15", insn.toString())
    }

    @Test
    fun `disassembleOne returns null for empty code`() {
        val result = dis.disassembleOne(ByteArray(0), 0)
        assertNull(result)
    }

    @Test
    fun `disassembleOne returns null for out of bounds offset`() {
        val result = dis.disassembleOne(byteArrayOf(0x00), 5)
        assertNull(result)
    }

    @Test
    fun `disassembleOne returns single instruction`() {
        val asm = JvmAssembler()
        asm.iadd()
        asm.ireturn()
        val code = asm.toByteArray()
        val insn = dis.disassembleOne(code, 0)
        assertNotNull(insn)
        assertEquals(JvmOpCode.IADD, insn!!.opcode)
        assertEquals(0, insn.offset)
    }

    @Test
    fun `disassembleOne at non-zero offset`() {
        val asm = JvmAssembler()
        asm.iadd()
        asm.ireturn()
        val code = asm.toByteArray()
        val insn = dis.disassembleOne(code, 1)
        assertNotNull(insn)
        assertEquals(JvmOpCode.IRETURN, insn!!.opcode)
        assertEquals(1, insn.offset)
    }

    @Test
    fun `disassemble monitorenter and monitorexit via raw bytes`() {
        val code = byteArrayOf(
            0xC2.toByte(),  // monitorenter
            0xC3.toByte(),  // monitorexit
        )
        val insns = dis.disassemble(code)
        assertEquals(2, insns.size)
        assertEquals(JvmOpCode.MONITORENTER, insns[0].opcode)
        assertEquals(JvmOpCode.MONITOREXIT, insns[1].opcode)
    }

    @Test
    fun `disassemble caload and saload via raw bytes`() {
        val code = byteArrayOf(
            0x34,  // caload
            0x35,  // saload
        )
        val insns = dis.disassemble(code)
        assertEquals(2, insns.size)
        assertEquals(JvmOpCode.CALOAD, insns[0].opcode)
        assertEquals(JvmOpCode.SALOAD, insns[1].opcode)
    }

    @Test
    fun `disassemble castore and sastore via raw bytes`() {
        val code = byteArrayOf(
            0x55,  // castore
            0x56,  // sastore
        )
        val insns = dis.disassemble(code)
        assertEquals(2, insns.size)
        assertEquals(JvmOpCode.CASTORE, insns[0].opcode)
        assertEquals(JvmOpCode.SASTORE, insns[1].opcode)
    }

    @Test
    fun `disassemble dup2_x1 and dup2_x2 via raw bytes`() {
        val code = byteArrayOf(
            0x5D,  // dup2_x1
            0x5E,  // dup2_x2
        )
        val insns = dis.disassemble(code)
        assertEquals(2, insns.size)
        assertEquals(JvmOpCode.DUP2_X1, insns[0].opcode)
        assertEquals(JvmOpCode.DUP2_X2, insns[1].opcode)
    }

    @Test
    fun `disassemble assembler reset and reuse`() {
        val asm = JvmAssembler()
        asm.iadd()
        asm.ireturn()
        val code1 = asm.toByteArray()
        assertEquals(2, dis.disassemble(code1).size)

        asm.reset()
        asm.lconst0()
        asm.lreturn()
        val code2 = asm.toByteArray()
        val insns = dis.disassemble(code2)
        assertEquals(2, insns.size)
        assertEquals(JvmOpCode.LCONST_0, insns[0].opcode)
        assertEquals(JvmOpCode.LRETURN, insns[1].opcode)
    }

    @Test
    fun `disassemble complex sequence with mixed instruction sizes`() {
        val asm = JvmAssembler()
        asm.nop()              // 1 byte
        asm.bipush(10)         // 2 bytes
        asm.sipush(1000)       // 3 bytes
        asm.ldc(5)             // 2 bytes
        asm.ldc(300)           // 3 bytes (ldc_w)
        asm.iload(0)           // 1 byte
        asm.iload(10)          // 2 bytes
        asm.iinc(1, 5)         // 3 bytes
        asm.invokestatic(20)   // 3 bytes
        asm.ireturn()          // 1 byte
        val code = asm.toByteArray()
        val insns = dis.disassemble(code)
        assertEquals(10, insns.size)

        // Verify total size
        val totalSize = insns.sumOf { it.size }
        assertEquals(code.size, totalSize)
    }

    @Test
    fun `disassemble if_acmpeq via raw bytes`() {
        val asm = JvmAssembler()
        asm.aload(0)
        asm.aload(1)
        asm.ifAcmpeq("same")
        asm.iconst0()
        asm.ireturn()
        asm.label("same")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IF_ACMPEQ })
    }

    @Test
    fun `disassemble if_acmpne via raw bytes`() {
        val asm = JvmAssembler()
        asm.aload(0)
        asm.aload(1)
        asm.ifAcmpne("diff")
        asm.iconst0()
        asm.ireturn()
        asm.label("diff")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.IF_ACMPNE })
    }

    @Test
    fun `disassemble goto_w via raw bytes`() {
        // goto_w with 4-byte offset
        val code = byteArrayOf(
            0xC8.toByte(),       // goto_w
            0x00, 0x00, 0x00, 0x05, // offset = +5
        )
        val insns = dis.disassemble(code)
        assertEquals(1, insns.size)
        assertEquals(JvmOpCode.GOTO_W, insns[0].opcode)
        assertEquals("5", insns[0].operands) // target = 0 + 5
        assertEquals(5, insns[0].size)
    }

    @Test
    fun `JvmOpCode fromCode returns correct opcode`() {
        assertEquals(JvmOpCode.NOP, JvmOpCode.fromCode(0x00))
        assertEquals(JvmOpCode.ACONST_NULL, JvmOpCode.fromCode(0x01))
        assertEquals(JvmOpCode.ICONST_0, JvmOpCode.fromCode(0x03))
        assertEquals(JvmOpCode.IADD, JvmOpCode.fromCode(0x60))
        assertEquals(JvmOpCode.IRETURN, JvmOpCode.fromCode(0xAC))
        assertEquals(JvmOpCode.RETURN, JvmOpCode.fromCode(0xB1))
    }

    @Test
    fun `JvmOpCode fromCode returns null for invalid code`() {
        assertNull(JvmOpCode.fromCode(0xFE))
        assertNull(JvmOpCode.fromCode(0xFF))
    }

    @Test
    fun `JvmInstruction data class properties`() {
        val insn = JvmDisassembler.JvmInstruction(10, JvmOpCode.BIPUSH, "42", 2)
        assertEquals(10, insn.offset)
        assertEquals(JvmOpCode.BIPUSH, insn.opcode)
        assertEquals("42", insn.operands)
        assertEquals(2, insn.size)
    }

    @Test
    fun `JvmInstruction data class equality`() {
        val insn1 = JvmDisassembler.JvmInstruction(0, JvmOpCode.IADD, "", 1)
        val insn2 = JvmDisassembler.JvmInstruction(0, JvmOpCode.IADD, "", 1)
        assertEquals(insn1, insn2)
    }

    @Test
    fun `disassemble long mixed type method`() {
        // Simulate a method: long add(int a, long b) { return (long)a + b; }
        val asm = JvmAssembler()
        asm.iload(0)
        asm.i2l()
        asm.lload(1)
        asm.ladd()
        asm.lreturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(5, insns.size)
        assertEquals(JvmOpCode.ILOAD_0, insns[0].opcode)
        assertEquals(JvmOpCode.I2L, insns[1].opcode)
        assertEquals(JvmOpCode.LLOAD_1, insns[2].opcode)
        assertEquals(JvmOpCode.LADD, insns[3].opcode)
        assertEquals(JvmOpCode.LRETURN, insns[4].opcode)
    }

    @Test
    fun `disassemble float comparison and branch pattern`() {
        val asm = JvmAssembler()
        asm.fload(0)
        asm.fload(1)
        asm.fcmpl()
        asm.ifgt("greater")
        asm.iconst0()
        asm.ireturn()
        asm.label("greater")
        asm.iconst1()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertTrue(insns.any { it.opcode == JvmOpCode.FCMPL })
        assertTrue(insns.any { it.opcode == JvmOpCode.IFGT })
    }

    @Test
    fun `disassemble double comparison pattern`() {
        val asm = JvmAssembler()
        asm.dload(0)
        asm.dload(2)
        asm.dcmpg()
        asm.ireturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.DLOAD_0, insns[0].opcode)
        assertEquals(JvmOpCode.DLOAD_2, insns[1].opcode)
        assertEquals(JvmOpCode.DCMPG, insns[2].opcode)
    }

    @Test
    fun `disassemble constructor pattern`() {
        // Pattern: aload_0; invokespecial Object init; return
        val asm = JvmAssembler()
        asm.aload(0)
        asm.invokespecial(5) // Object.<init>
        asm.return_()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(3, insns.size)
        assertEquals(JvmOpCode.ALOAD_0, insns[0].opcode)
        assertEquals(JvmOpCode.INVOKESPECIAL, insns[1].opcode)
        assertEquals(JvmOpCode.RETURN, insns[2].opcode)
    }

    @Test
    fun `disassemble array creation and store pattern`() {
        val asm = JvmAssembler()
        asm.bipush(10)
        asm.newarray(10) // int array
        asm.dup()
        asm.iconst0()
        asm.bipush(42)
        asm.iastore()
        asm.areturn()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(7, insns.size)
        assertEquals(JvmOpCode.NEWARRAY, insns[1].opcode)
        assertEquals("int", insns[1].operands)
        assertEquals(JvmOpCode.IASTORE, insns[5].opcode)
    }

    @Test
    fun `disassemble exception handling pattern`() {
        // try { invokestatic } catch { athrow }
        val asm = JvmAssembler()
        asm.invokestatic(5)
        asm.return_()
        asm.aload(0) // exception handler: load exception
        asm.athrow()
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.INVOKESTATIC, insns[0].opcode)
        assertEquals(JvmOpCode.ATHROW, insns[3].opcode)
    }

    @Test
    fun `disassemble all fload and dload variants with high index`() {
        val asm = JvmAssembler()
        asm.fload(10)
        asm.dload(10)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(2, insns.size)
        assertEquals(JvmOpCode.FLOAD, insns[0].opcode)
        assertEquals("10", insns[0].operands)
        assertEquals(JvmOpCode.DLOAD, insns[1].opcode)
        assertEquals("10", insns[1].operands)
    }

    @Test
    fun `disassemble all lload, fstore, dstore, lstore variants with high index`() {
        val asm = JvmAssembler()
        asm.lload(10)
        asm.fstore(10)
        asm.dstore(10)
        asm.lstore(10)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.LLOAD, insns[0].opcode)
        assertEquals(JvmOpCode.FSTORE, insns[1].opcode)
        assertEquals(JvmOpCode.DSTORE, insns[2].opcode)
        assertEquals(JvmOpCode.LSTORE, insns[3].opcode)
    }

    @Test
    fun `disassemble getstatic putstatic getfield putfield sequence`() {
        val asm = JvmAssembler()
        asm.getstatic(1)
        asm.putstatic(2)
        asm.getfield(3)
        asm.putfield(4)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals("#1", insns[0].operands)
        assertEquals("#2", insns[1].operands)
        assertEquals("#3", insns[2].operands)
        assertEquals("#4", insns[3].operands)
    }

    @Test
    fun `disassemble all invoke types in sequence`() {
        val asm = JvmAssembler()
        asm.invokevirtual(1)
        asm.invokespecial(2)
        asm.invokestatic(3)
        asm.invokeinterface(4, 1)
        val insns = dis.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals(JvmOpCode.INVOKEVIRTUAL, insns[0].opcode)
        assertEquals(JvmOpCode.INVOKESPECIAL, insns[1].opcode)
        assertEquals(JvmOpCode.INVOKESTATIC, insns[2].opcode)
        assertEquals(JvmOpCode.INVOKEINTERFACE, insns[3].opcode)
        assertEquals(3, insns[0].size)
        assertEquals(3, insns[1].size)
        assertEquals(3, insns[2].size)
        assertEquals(5, insns[3].size)
    }

    @Test
    fun `disassemble pushInt compact encoding choices`() {
        // -1 -> iconst_m1
        val asm1 = JvmAssembler()
        asm1.pushInt(-1)
        assertEquals(JvmOpCode.ICONST_M1, dis.disassemble(asm1.toByteArray())[0].opcode)

        // 0-5 -> iconst_N
        for (i in 0..5) {
            val asm = JvmAssembler()
            asm.pushInt(i)
            val insns = dis.disassemble(asm.toByteArray())
            assertEquals(1, insns[0].size, "iconst_$i should be 1 byte")
        }

        // 6-127 -> bipush
        val asm6 = JvmAssembler()
        asm6.pushInt(100)
        assertEquals(JvmOpCode.BIPUSH, dis.disassemble(asm6.toByteArray())[0].opcode)

        // 128-32767 -> sipush
        val asm7 = JvmAssembler()
        asm7.pushInt(1000)
        assertEquals(JvmOpCode.SIPUSH, dis.disassemble(asm7.toByteArray())[0].opcode)
    }

    @Test
    fun `disassemble preserves all instruction data through assembler round trip`() {
        val asm = JvmAssembler()
        asm.bipush(42)
        asm.istore(5)
        asm.sipush(1000)
        asm.iload(5)
        asm.iadd()
        asm.ireturn()
        val code = asm.toByteArray()
        val insns = dis.disassemble(code)

        assertEquals("42", insns[0].operands)
        assertEquals("5", insns[1].operands)
        assertEquals("1000", insns[2].operands)
        assertEquals("5", insns[3].operands)
        assertEquals("", insns[4].operands)
        assertEquals("", insns[5].operands)
    }
}
