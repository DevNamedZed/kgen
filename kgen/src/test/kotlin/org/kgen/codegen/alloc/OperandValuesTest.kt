package org.kgen.codegen.alloc

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*

class OperandValuesTest {

    private val paramA = Parameter("a", Type.I32, 0)
    private val paramB = Parameter("b", Type.I32, 1)
    private val paramC = Parameter("c", Type.I64, 2)
    private val refX = InstructionRef("x", Type.I32)
    private val refY = InstructionRef("y", Type.I32)
    private val refZ = InstructionRef("z", Type.I64)
    private val ptrRef = InstructionRef("ptr", Type.OpaquePointer)

    @Nested
    inner class ArithmeticOperands {

        @Test
        fun addExtractsBothOperands() {
            val inst = Add(refX, paramA, paramB)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(2, operands.size)
            assertTrue(operands.contains(paramA))
            assertTrue(operands.contains(paramB))
        }

        @Test
        fun subExtractsBothOperands() {
            val inst = Sub(refX, paramA, refY)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(2, operands.size)
            assertTrue(operands.contains(paramA))
            assertTrue(operands.contains(refY))
        }

        @Test
        fun mulExtractsBothOperands() {
            val inst = Mul(refX, refY, paramA)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(2, operands.size)
        }

        @Test
        fun negExtractsSingleOperand() {
            val inst = Neg(refX, paramA)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
            assertEquals(paramA, operands[0])
        }

        @Test
        fun divisionExtractsBothOperands() {
            val sdiv = SDiv(refX, paramA, paramB)
            val udiv = UDiv(refX, paramA, paramB)
            assertEquals(2, LivenessAnalysis.operandValues(sdiv).size)
            assertEquals(2, LivenessAnalysis.operandValues(udiv).size)
        }

        @Test
        fun remainderExtractsBothOperands() {
            val srem = SRem(refX, paramA, paramB)
            val urem = URem(refX, paramA, paramB)
            assertEquals(2, LivenessAnalysis.operandValues(srem).size)
            assertEquals(2, LivenessAnalysis.operandValues(urem).size)
        }
    }

    @Nested
    inner class FloatingPointOperands {

        @Test
        fun faddExtractsBothOperands() {
            val paramF1 = Parameter("f1", Type.F64, 0)
            val paramF2 = Parameter("f2", Type.F64, 1)
            val inst = FAdd(InstructionRef("r", Type.F64), paramF1, paramF2)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(2, operands.size)
        }

        @Test
        fun fnegExtractsSingleOperand() {
            val paramF = Parameter("f", Type.F64, 0)
            val inst = FNeg(InstructionRef("r", Type.F64), paramF)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
            assertEquals(paramF, operands[0])
        }

        @Test
        fun fcmpExtractsBothOperands() {
            val paramF1 = Parameter("f1", Type.F64, 0)
            val paramF2 = Parameter("f2", Type.F64, 1)
            val inst = FCmp(InstructionRef("r", Type.I1), FCmpPredicate.OEQ, paramF1, paramF2)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(2, operands.size)
        }
    }

    @Nested
    inner class BitwiseOperands {

        @Test
        fun andExtractsBothOperands() {
            val inst = And(refX, paramA, paramB)
            assertEquals(2, LivenessAnalysis.operandValues(inst).size)
        }

        @Test
        fun orExtractsBothOperands() {
            val inst = Or(refX, paramA, paramB)
            assertEquals(2, LivenessAnalysis.operandValues(inst).size)
        }

        @Test
        fun xorExtractsBothOperands() {
            val inst = Xor(refX, paramA, paramB)
            assertEquals(2, LivenessAnalysis.operandValues(inst).size)
        }

        @Test
        fun notExtractsSingleOperand() {
            val inst = Not(refX, paramA)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
            assertEquals(paramA, operands[0])
        }

        @Test
        fun shiftExtractsBothOperands() {
            val shl = Shl(refX, paramA, paramB)
            val lshr = LShr(refX, paramA, paramB)
            val ashr = AShr(refX, paramA, paramB)
            assertEquals(2, LivenessAnalysis.operandValues(shl).size)
            assertEquals(2, LivenessAnalysis.operandValues(lshr).size)
            assertEquals(2, LivenessAnalysis.operandValues(ashr).size)
        }
    }

    @Nested
    inner class ComparisonOperands {

        @Test
        fun icmpExtractsBothOperands() {
            val inst = ICmp(InstructionRef("cmp", Type.I1), ICmpPredicate.EQ, paramA, paramB)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(2, operands.size)
            assertTrue(operands.contains(paramA))
            assertTrue(operands.contains(paramB))
        }
    }

    @Nested
    inner class MemoryOperands {

        @Test
        fun loadExtractsPointer() {
            val inst = Load(refX, ptrRef, Type.I32)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
            assertEquals(ptrRef, operands[0])
        }

        @Test
        fun storeExtractsValueAndPointer() {
            val inst = Store(paramA, ptrRef)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(2, operands.size)
            assertTrue(operands.contains(paramA))
            assertTrue(operands.contains(ptrRef))
        }

        @Test
        fun allocaWithoutNumElementsHasNoOperands() {
            val inst = Alloca(ptrRef, Type.I32)
            val operands = LivenessAnalysis.operandValues(inst)
            assertTrue(operands.isEmpty())
        }

        @Test
        fun allocaWithNumElementsExtractsCount() {
            val inst = Alloca(ptrRef, Type.I32, numElements = paramA)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
            assertEquals(paramA, operands[0])
        }

        @Test
        fun gepExtractsPtrAndIndices() {
            val idx = InstructionRef("idx", Type.I64)
            val inst = GetElementPtr(InstructionRef("gep", Type.OpaquePointer), Type.I32, ptrRef, listOf(idx))
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(2, operands.size)
            assertTrue(operands.contains(ptrRef))
            assertTrue(operands.contains(idx))
        }
    }

    @Nested
    inner class ControlFlowOperands {

        @Test
        fun retWithValueExtractsOperand() {
            val inst = Ret(paramA)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
            assertEquals(paramA, operands[0])
        }

        @Test
        fun retVoidHasNoOperands() {
            val inst = Ret(null)
            val operands = LivenessAnalysis.operandValues(inst)
            assertTrue(operands.isEmpty())
        }

        @Test
        fun brHasNoOperands() {
            val inst = Br(BlockRef("target"))
            val operands = LivenessAnalysis.operandValues(inst)
            assertTrue(operands.isEmpty())
        }

        @Test
        fun condBrExtractsCondition() {
            val inst = CondBr(paramA, BlockRef("then"), BlockRef("else"))
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
            assertEquals(paramA, operands[0])
        }

        @Test
        fun switchExtractsValue() {
            val inst = Switch(paramA, BlockRef("default"), listOf(Constant.I32(1) to BlockRef("case1")))
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
            assertEquals(paramA, operands[0])
        }
    }

    @Nested
    inner class CallOperands {

        @Test
        fun callExtractsArguments() {
            val func = FunctionRef("foo", Type.Function(listOf(Type.I32, Type.I32), Type.I32))
            val inst = Call(refX, func, listOf(paramA, paramB), Type.I32)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(2, operands.size)
            assertTrue(operands.contains(paramA))
            assertTrue(operands.contains(paramB))
        }

        @Test
        fun callWithNoArgsHasNoOperands() {
            val func = FunctionRef("bar", Type.Function(emptyList(), Type.Void))
            val inst = Call(null, func, emptyList(), Type.Void)
            val operands = LivenessAnalysis.operandValues(inst)
            assertTrue(operands.isEmpty())
        }
    }

    @Nested
    inner class SsaOperands {

        @Test
        fun phiExtractsIncomingValues() {
            val inst = Phi(refX, listOf(paramA to BlockRef("block0"), paramB to BlockRef("block1")))
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(2, operands.size)
            assertTrue(operands.contains(paramA))
            assertTrue(operands.contains(paramB))
        }

        @Test
        fun selectExtractsAllThreeOperands() {
            val cond = InstructionRef("cond", Type.I1)
            val inst = Select(refX, cond, paramA, paramB)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(3, operands.size)
            assertTrue(operands.contains(cond))
            assertTrue(operands.contains(paramA))
            assertTrue(operands.contains(paramB))
        }
    }

    @Nested
    inner class ConversionOperands {

        @Test
        fun zextExtractsValue() {
            val inst = ZExt(refZ, paramA, Type.I64)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
            assertEquals(paramA, operands[0])
        }

        @Test
        fun sextExtractsValue() {
            val inst = SExt(refZ, paramA, Type.I64)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
        }

        @Test
        fun intTruncExtractsValue() {
            val inst = IntTrunc(InstructionRef("t", Type.I16), paramA, Type.I16)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
        }

        @Test
        fun bitCastExtractsValue() {
            val inst = BitCast(InstructionRef("bc", Type.F32), paramA, Type.F32)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
        }

        @Test
        fun ptrToIntExtractsValue() {
            val inst = PtrToInt(InstructionRef("p2i", Type.I64), ptrRef, Type.I64)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
        }

        @Test
        fun intToPtrExtractsValue() {
            val inst = IntToPtr(InstructionRef("i2p", Type.OpaquePointer), paramA, Type.OpaquePointer)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
        }
    }

    @Nested
    inner class AggregateOperands {

        @Test
        fun extractValueExtractsAggregate() {
            val agg = InstructionRef("agg", Type.Struct(null, listOf(Type.I32, Type.I64)))
            val inst = ExtractValue(refX, agg, listOf(0))
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
            assertEquals(agg, operands[0])
        }

        @Test
        fun insertValueExtractsAggregateAndElement() {
            val agg = InstructionRef("agg", Type.Struct(null, listOf(Type.I32, Type.I64)))
            val inst = InsertValue(InstructionRef("iv", Type.Struct(null, listOf(Type.I32, Type.I64))), agg, paramA, listOf(0))
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(2, operands.size)
            assertTrue(operands.contains(agg))
            assertTrue(operands.contains(paramA))
        }
    }

    @Nested
    inner class ConstantFiltering {

        @Test
        fun constantOperandsAreExcluded() {
            val inst = Add(refX, paramA, Constant.I32(42))
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
            assertEquals(paramA, operands[0])
        }

        @Test
        fun allConstantOperandsResultsInEmptyList() {
            val inst = Add(refX, Constant.I32(1), Constant.I32(2))
            val operands = LivenessAnalysis.operandValues(inst)
            assertTrue(operands.isEmpty())
        }
    }

    @Nested
    inner class VarArgOperands {

        @Test
        fun vaStartExtractsArgList() {
            val inst = VAStart(ptrRef)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
        }

        @Test
        fun vaEndExtractsArgList() {
            val inst = VAEnd(ptrRef)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
        }

        @Test
        fun vaCopyExtractsBothOperands() {
            val dst = InstructionRef("dst", Type.OpaquePointer)
            val src = InstructionRef("src", Type.OpaquePointer)
            val inst = VACopy(dst, src)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(2, operands.size)
        }

        @Test
        fun vaArgExtractsArgList() {
            val inst = VAArg(refX, ptrRef, Type.I32)
            val operands = LivenessAnalysis.operandValues(inst)
            assertEquals(1, operands.size)
        }
    }

    @Nested
    inner class FloatConversionOperands {

        @Test
        fun siToFpExtractsValue() {
            val inst = SIToFP(InstructionRef("r", Type.F64), paramA, Type.F64)
            assertEquals(1, LivenessAnalysis.operandValues(inst).size)
        }

        @Test
        fun uiToFpExtractsValue() {
            val inst = UIToFP(InstructionRef("r", Type.F64), paramA, Type.F64)
            assertEquals(1, LivenessAnalysis.operandValues(inst).size)
        }

        @Test
        fun fpToSiExtractsValue() {
            val paramF = Parameter("f", Type.F64, 0)
            val inst = FPToSI(InstructionRef("r", Type.I32), paramF, Type.I32)
            assertEquals(1, LivenessAnalysis.operandValues(inst).size)
        }

        @Test
        fun fpToUiExtractsValue() {
            val paramF = Parameter("f", Type.F64, 0)
            val inst = FPToUI(InstructionRef("r", Type.I32), paramF, Type.I32)
            assertEquals(1, LivenessAnalysis.operandValues(inst).size)
        }

        @Test
        fun fpTruncExtractsValue() {
            val paramF = Parameter("f", Type.F64, 0)
            val inst = FPTrunc(InstructionRef("r", Type.F32), paramF, Type.F32)
            assertEquals(1, LivenessAnalysis.operandValues(inst).size)
        }

        @Test
        fun fpExtExtractsValue() {
            val paramF = Parameter("f", Type.F32, 0)
            val inst = FPExt(InstructionRef("r", Type.F64), paramF, Type.F64)
            assertEquals(1, LivenessAnalysis.operandValues(inst).size)
        }
    }
}
