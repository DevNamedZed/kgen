package org.kgen.target.clr

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CilOpCodeTest {

    @Test
    fun lookupSingleByte() {
        assertEquals(CilOpCode.NOP, CilOpCode.fromCode(0x00))
        assertEquals(CilOpCode.ADD, CilOpCode.fromCode(0x58))
        assertEquals(CilOpCode.RET, CilOpCode.fromCode(0x2A))
        assertEquals(CilOpCode.LDARG_0, CilOpCode.fromCode(0x02))
        assertEquals(CilOpCode.LDC_I4, CilOpCode.fromCode(0x20))
    }

    @Test
    fun lookupTwoByte() {
        assertEquals(CilOpCode.CEQ, CilOpCode.fromCode(0xFE01))
        assertEquals(CilOpCode.CGT, CilOpCode.fromCode(0xFE02))
        assertEquals(CilOpCode.CLT, CilOpCode.fromCode(0xFE04))
        assertEquals(CilOpCode.SIZEOF, CilOpCode.fromCode(0xFE1C))
    }

    @Test
    fun lookupByByte() {
        assertEquals(CilOpCode.ADD, CilOpCode.fromByte(0x58))
        assertNull(CilOpCode.fromByte(0x24)) // unused opcode
    }

    @Test
    fun lookupTwoByteSecondByte() {
        assertEquals(CilOpCode.CEQ, CilOpCode.fromTwoByte(0x01))
        assertEquals(CilOpCode.LOCALLOC, CilOpCode.fromTwoByte(0x0F))
    }

    @Test
    fun mnemonics() {
        assertEquals("add", CilOpCode.ADD.mnemonic())
        assertEquals("ldarg.0", CilOpCode.LDARG_0.mnemonic())
        assertEquals("ldc.i4.s", CilOpCode.LDC_I4_S.mnemonic())
        assertEquals("ceq", CilOpCode.CEQ.mnemonic())
        assertEquals("conv.ovf.i1.un", CilOpCode.CONV_OVF_I1_UN.mnemonic())
    }

    @Test
    fun isTwoByte() {
        assertFalse(CilOpCode.ADD.isTwoByte())
        assertFalse(CilOpCode.NOP.isTwoByte())
        assertTrue(CilOpCode.CEQ.isTwoByte())
        assertTrue(CilOpCode.SIZEOF.isTwoByte())
    }

    @Test
    fun operandTypes() {
        assertEquals(CilOperandType.NONE, CilOpCode.ADD.operandType)
        assertEquals(CilOperandType.I32, CilOpCode.LDC_I4.operandType)
        assertEquals(CilOperandType.I64, CilOpCode.LDC_I8.operandType)
        assertEquals(CilOperandType.TOKEN, CilOpCode.CALL.operandType)
        assertEquals(CilOperandType.I8, CilOpCode.LDC_I4_S.operandType)
        assertEquals(CilOperandType.U8, CilOpCode.LDARG_S.operandType)
        assertEquals(CilOperandType.SWITCH, CilOpCode.SWITCH.operandType)
    }

    @Test
    fun operandSizes() {
        assertEquals(0, CilOpCode.ADD.operandSize())
        assertEquals(4, CilOpCode.LDC_I4.operandSize())
        assertEquals(8, CilOpCode.LDC_I8.operandSize())
        assertEquals(4, CilOpCode.CALL.operandSize()) // token
        assertEquals(1, CilOpCode.LDC_I4_S.operandSize())
    }

    @Test
    fun stackEffects() {
        // add: pops 2, pushes 1
        assertEquals(1, CilOpCode.ADD.stackPush)
        assertEquals(2, CilOpCode.ADD.stackPop)

        // ldc.i4: pushes 1, pops 0
        assertEquals(1, CilOpCode.LDC_I4.stackPush)
        assertEquals(0, CilOpCode.LDC_I4.stackPop)

        // stloc.0: pushes 0, pops 1
        assertEquals(0, CilOpCode.STLOC_0.stackPush)
        assertEquals(1, CilOpCode.STLOC_0.stackPop)
    }

    @Test
    fun enumCount() {
        // Should have ~220 opcodes
        assertTrue(CilOpCode.entries.size >= 200, "Expected at least 200 opcodes, got ${CilOpCode.entries.size}")
    }

    @Test
    fun noDuplicateCodes() {
        val codes = CilOpCode.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "Duplicate opcode codes found")
    }

    @Test
    fun allCodesResolvable() {
        for (op in CilOpCode.entries) {
            val resolved = CilOpCode.fromCode(op.code)
            assertNotNull(resolved, "Failed to resolve ${op.name} with code 0x${op.code.toString(16)}")
            assertEquals(op, resolved)
        }
    }
}
