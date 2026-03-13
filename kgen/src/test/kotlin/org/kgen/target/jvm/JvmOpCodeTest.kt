package org.kgen.target.jvm

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmOpCodeTest {

    @Nested
    inner class FromCode {

        @Test
        fun lookupByValidCode() {
            assertEquals(JvmOpCode.NOP, JvmOpCode.fromCode(0x00))
            assertEquals(JvmOpCode.ACONST_NULL, JvmOpCode.fromCode(0x01))
            assertEquals(JvmOpCode.ICONST_M1, JvmOpCode.fromCode(0x02))
            assertEquals(JvmOpCode.ICONST_0, JvmOpCode.fromCode(0x03))
            assertEquals(JvmOpCode.ICONST_5, JvmOpCode.fromCode(0x08))
        }

        @Test
        fun lookupArithmetic() {
            assertEquals(JvmOpCode.IADD, JvmOpCode.fromCode(0x60))
            assertEquals(JvmOpCode.LADD, JvmOpCode.fromCode(0x61))
            assertEquals(JvmOpCode.ISUB, JvmOpCode.fromCode(0x64))
            assertEquals(JvmOpCode.IMUL, JvmOpCode.fromCode(0x68))
            assertEquals(JvmOpCode.IDIV, JvmOpCode.fromCode(0x6C))
            assertEquals(JvmOpCode.IREM, JvmOpCode.fromCode(0x70))
        }

        @Test
        fun lookupBranches() {
            assertEquals(JvmOpCode.IFEQ, JvmOpCode.fromCode(0x99))
            assertEquals(JvmOpCode.GOTO, JvmOpCode.fromCode(0xA7))
            assertEquals(JvmOpCode.IF_ICMPEQ, JvmOpCode.fromCode(0x9F))
            assertEquals(JvmOpCode.IFNULL, JvmOpCode.fromCode(0xC6))
            assertEquals(JvmOpCode.IFNONNULL, JvmOpCode.fromCode(0xC7))
        }

        @Test
        fun lookupReferences() {
            assertEquals(JvmOpCode.GETSTATIC, JvmOpCode.fromCode(0xB2))
            assertEquals(JvmOpCode.INVOKEVIRTUAL, JvmOpCode.fromCode(0xB6))
            assertEquals(JvmOpCode.INVOKESPECIAL, JvmOpCode.fromCode(0xB7))
            assertEquals(JvmOpCode.INVOKESTATIC, JvmOpCode.fromCode(0xB8))
            assertEquals(JvmOpCode.INVOKEINTERFACE, JvmOpCode.fromCode(0xB9))
            assertEquals(JvmOpCode.INVOKEDYNAMIC, JvmOpCode.fromCode(0xBA))
            assertEquals(JvmOpCode.NEW, JvmOpCode.fromCode(0xBB))
        }

        @Test
        fun lookupExtended() {
            assertEquals(JvmOpCode.WIDE, JvmOpCode.fromCode(0xC4))
            assertEquals(JvmOpCode.MULTIANEWARRAY, JvmOpCode.fromCode(0xC5))
            assertEquals(JvmOpCode.GOTO_W, JvmOpCode.fromCode(0xC8))
            assertEquals(JvmOpCode.JSR_W, JvmOpCode.fromCode(0xC9))
        }

        @Test
        fun returnsNullForInvalidCode() {
            assertNull(JvmOpCode.fromCode(0xFE))
            assertNull(JvmOpCode.fromCode(0xFF))
            assertNull(JvmOpCode.fromCode(-1))
            assertNull(JvmOpCode.fromCode(0x100))
        }

        @Test
        fun returnsNullForUnusedCodes() {
            assertNull(JvmOpCode.fromCode(0xCA))
            assertNull(JvmOpCode.fromCode(0xCB))
        }
    }

    @Nested
    inner class OperandSize {

        @Test
        fun zeroOperandInstructions() {
            assertEquals(0, JvmOpCode.NOP.operandSize)
            assertEquals(0, JvmOpCode.ACONST_NULL.operandSize)
            assertEquals(0, JvmOpCode.ICONST_0.operandSize)
            assertEquals(0, JvmOpCode.ICONST_M1.operandSize)
            assertEquals(0, JvmOpCode.IADD.operandSize)
            assertEquals(0, JvmOpCode.ISUB.operandSize)
            assertEquals(0, JvmOpCode.IRETURN.operandSize)
            assertEquals(0, JvmOpCode.RETURN.operandSize)
            assertEquals(0, JvmOpCode.POP.operandSize)
            assertEquals(0, JvmOpCode.DUP.operandSize)
            assertEquals(0, JvmOpCode.SWAP.operandSize)
            assertEquals(0, JvmOpCode.ATHROW.operandSize)
            assertEquals(0, JvmOpCode.ARRAYLENGTH.operandSize)
            assertEquals(0, JvmOpCode.MONITORENTER.operandSize)
            assertEquals(0, JvmOpCode.MONITOREXIT.operandSize)
        }

        @Test
        fun oneByteOperandInstructions() {
            assertEquals(1, JvmOpCode.BIPUSH.operandSize)
            assertEquals(1, JvmOpCode.LDC.operandSize)
            assertEquals(1, JvmOpCode.NEWARRAY.operandSize)
            assertEquals(1, JvmOpCode.ILOAD.operandSize)
            assertEquals(1, JvmOpCode.LLOAD.operandSize)
            assertEquals(1, JvmOpCode.FLOAD.operandSize)
            assertEquals(1, JvmOpCode.DLOAD.operandSize)
            assertEquals(1, JvmOpCode.ALOAD.operandSize)
            assertEquals(1, JvmOpCode.ISTORE.operandSize)
            assertEquals(1, JvmOpCode.LSTORE.operandSize)
            assertEquals(1, JvmOpCode.FSTORE.operandSize)
            assertEquals(1, JvmOpCode.DSTORE.operandSize)
            assertEquals(1, JvmOpCode.ASTORE.operandSize)
        }

        @Test
        fun twoByteOperandInstructions() {
            assertEquals(2, JvmOpCode.SIPUSH.operandSize)
            assertEquals(2, JvmOpCode.LDC_W.operandSize)
            assertEquals(2, JvmOpCode.LDC2_W.operandSize)
            assertEquals(2, JvmOpCode.IINC.operandSize)
            assertEquals(2, JvmOpCode.GETSTATIC.operandSize)
            assertEquals(2, JvmOpCode.PUTSTATIC.operandSize)
            assertEquals(2, JvmOpCode.GETFIELD.operandSize)
            assertEquals(2, JvmOpCode.PUTFIELD.operandSize)
            assertEquals(2, JvmOpCode.INVOKEVIRTUAL.operandSize)
            assertEquals(2, JvmOpCode.INVOKESPECIAL.operandSize)
            assertEquals(2, JvmOpCode.INVOKESTATIC.operandSize)
            assertEquals(2, JvmOpCode.NEW.operandSize)
            assertEquals(2, JvmOpCode.ANEWARRAY.operandSize)
            assertEquals(2, JvmOpCode.CHECKCAST.operandSize)
            assertEquals(2, JvmOpCode.INSTANCEOF.operandSize)
        }

        @Test
        fun branchInstructionsTwoByteOffset() {
            assertEquals(2, JvmOpCode.IFEQ.operandSize)
            assertEquals(2, JvmOpCode.IFNE.operandSize)
            assertEquals(2, JvmOpCode.IFLT.operandSize)
            assertEquals(2, JvmOpCode.IFGE.operandSize)
            assertEquals(2, JvmOpCode.IFGT.operandSize)
            assertEquals(2, JvmOpCode.IFLE.operandSize)
            assertEquals(2, JvmOpCode.IF_ICMPEQ.operandSize)
            assertEquals(2, JvmOpCode.IF_ICMPNE.operandSize)
            assertEquals(2, JvmOpCode.IF_ICMPLT.operandSize)
            assertEquals(2, JvmOpCode.IF_ICMPGE.operandSize)
            assertEquals(2, JvmOpCode.IF_ICMPGT.operandSize)
            assertEquals(2, JvmOpCode.IF_ICMPLE.operandSize)
            assertEquals(2, JvmOpCode.IF_ACMPEQ.operandSize)
            assertEquals(2, JvmOpCode.IF_ACMPNE.operandSize)
            assertEquals(2, JvmOpCode.GOTO.operandSize)
            assertEquals(2, JvmOpCode.JSR.operandSize)
            assertEquals(2, JvmOpCode.IFNULL.operandSize)
            assertEquals(2, JvmOpCode.IFNONNULL.operandSize)
        }

        @Test
        fun threeByteOperandInstructions() {
            assertEquals(3, JvmOpCode.MULTIANEWARRAY.operandSize)
        }

        @Test
        fun fourByteOperandInstructions() {
            assertEquals(4, JvmOpCode.INVOKEINTERFACE.operandSize)
            assertEquals(4, JvmOpCode.INVOKEDYNAMIC.operandSize)
            assertEquals(4, JvmOpCode.GOTO_W.operandSize)
            assertEquals(4, JvmOpCode.JSR_W.operandSize)
        }

        @Test
        fun variableLengthInstructions() {
            assertEquals(-1, JvmOpCode.TABLESWITCH.operandSize)
            assertEquals(-1, JvmOpCode.LOOKUPSWITCH.operandSize)
            assertEquals(-1, JvmOpCode.WIDE.operandSize)
        }

        @Test
        fun conversionInstructionsZeroOperands() {
            assertEquals(0, JvmOpCode.I2L.operandSize)
            assertEquals(0, JvmOpCode.I2F.operandSize)
            assertEquals(0, JvmOpCode.I2D.operandSize)
            assertEquals(0, JvmOpCode.L2I.operandSize)
            assertEquals(0, JvmOpCode.L2F.operandSize)
            assertEquals(0, JvmOpCode.L2D.operandSize)
            assertEquals(0, JvmOpCode.F2I.operandSize)
            assertEquals(0, JvmOpCode.F2L.operandSize)
            assertEquals(0, JvmOpCode.F2D.operandSize)
            assertEquals(0, JvmOpCode.D2I.operandSize)
            assertEquals(0, JvmOpCode.D2L.operandSize)
            assertEquals(0, JvmOpCode.D2F.operandSize)
            assertEquals(0, JvmOpCode.I2B.operandSize)
            assertEquals(0, JvmOpCode.I2C.operandSize)
            assertEquals(0, JvmOpCode.I2S.operandSize)
        }

        @Test
        fun comparisonInstructionsZeroOperands() {
            assertEquals(0, JvmOpCode.LCMP.operandSize)
            assertEquals(0, JvmOpCode.FCMPL.operandSize)
            assertEquals(0, JvmOpCode.FCMPG.operandSize)
            assertEquals(0, JvmOpCode.DCMPL.operandSize)
            assertEquals(0, JvmOpCode.DCMPG.operandSize)
        }

        @Test
        fun loadShortFormZeroOperands() {
            assertEquals(0, JvmOpCode.ILOAD_0.operandSize)
            assertEquals(0, JvmOpCode.ILOAD_3.operandSize)
            assertEquals(0, JvmOpCode.LLOAD_0.operandSize)
            assertEquals(0, JvmOpCode.FLOAD_0.operandSize)
            assertEquals(0, JvmOpCode.DLOAD_0.operandSize)
            assertEquals(0, JvmOpCode.ALOAD_0.operandSize)
        }

        @Test
        fun storeShortFormZeroOperands() {
            assertEquals(0, JvmOpCode.ISTORE_0.operandSize)
            assertEquals(0, JvmOpCode.ISTORE_3.operandSize)
            assertEquals(0, JvmOpCode.LSTORE_0.operandSize)
            assertEquals(0, JvmOpCode.FSTORE_0.operandSize)
            assertEquals(0, JvmOpCode.DSTORE_0.operandSize)
            assertEquals(0, JvmOpCode.ASTORE_0.operandSize)
        }

        @Test
        fun arrayLoadStoreZeroOperands() {
            assertEquals(0, JvmOpCode.IALOAD.operandSize)
            assertEquals(0, JvmOpCode.LALOAD.operandSize)
            assertEquals(0, JvmOpCode.FALOAD.operandSize)
            assertEquals(0, JvmOpCode.DALOAD.operandSize)
            assertEquals(0, JvmOpCode.AALOAD.operandSize)
            assertEquals(0, JvmOpCode.BALOAD.operandSize)
            assertEquals(0, JvmOpCode.CALOAD.operandSize)
            assertEquals(0, JvmOpCode.SALOAD.operandSize)
            assertEquals(0, JvmOpCode.IASTORE.operandSize)
            assertEquals(0, JvmOpCode.AASTORE.operandSize)
        }
    }

    @Nested
    inner class CodeProperty {

        @Test
        fun codesAreUnique() {
            val codes = JvmOpCode.entries.map { it.code }
            assertEquals(codes.size, codes.toSet().size, "All opcodes should have unique codes")
        }

        @Test
        fun allCodesInValidRange() {
            for (op in JvmOpCode.entries) {
                assertTrue(op.code in 0..0xC9, "${op.name} code ${op.code} out of expected range")
            }
        }

        @Test
        fun roundTripFromCode() {
            for (op in JvmOpCode.entries) {
                assertEquals(op, JvmOpCode.fromCode(op.code), "fromCode round-trip failed for ${op.name}")
            }
        }

        @Test
        fun specificCodes() {
            assertEquals(0x00, JvmOpCode.NOP.code)
            assertEquals(0xB1, JvmOpCode.RETURN.code)
            assertEquals(0xBF, JvmOpCode.ATHROW.code)
            assertEquals(0xC9, JvmOpCode.JSR_W.code)
        }
    }
}
