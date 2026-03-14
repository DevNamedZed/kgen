package org.kgen.ir

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.instructions.*

class InstructionTest {

    @Nested
    inner class ArithmeticInstructions {

        @Test
        fun addHasArithmeticCategory() {
            val inst = Add(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(2))
            assertEquals(IrCategory.ARITHMETIC, inst.category)
        }

        @Test
        fun addDefaultFlagsAreFalse() {
            val inst = Add(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(2))
            assertFalse(inst.nuw)
            assertFalse(inst.nsw)
        }

        @Test
        fun addWithNuwNswFlags() {
            val inst = Add(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(2), nuw = true, nsw = true)
            assertTrue(inst.nuw)
            assertTrue(inst.nsw)
        }

        @Test
        fun addResultIsTheDest() {
            val dest = InstructionRef("sum", Type.I32)
            val inst = Add(dest, Constant.I32(1), Constant.I32(2))
            assertEquals(dest, inst.result)
        }

        @Test
        fun subHasArithmeticCategory() {
            val inst = Sub(InstructionRef("r", Type.I32), Constant.I32(3), Constant.I32(1))
            assertEquals(IrCategory.ARITHMETIC, inst.category)
        }

        @Test
        fun mulHasArithmeticCategory() {
            val inst = Mul(InstructionRef("r", Type.I32), Constant.I32(2), Constant.I32(3))
            assertEquals(IrCategory.ARITHMETIC, inst.category)
        }

        @Test
        fun sdivDefaultExactIsFalse() {
            val inst = SDiv(InstructionRef("r", Type.I32), Constant.I32(6), Constant.I32(3))
            assertFalse(inst.exact)
        }

        @Test
        fun sdivWithExactFlag() {
            val inst = SDiv(InstructionRef("r", Type.I32), Constant.I32(6), Constant.I32(3), exact = true)
            assertTrue(inst.exact)
        }

        @Test
        fun udivDefaultExactIsFalse() {
            val inst = UDiv(InstructionRef("r", Type.I32), Constant.I32(6), Constant.I32(3))
            assertFalse(inst.exact)
        }

        @Test
        fun negResultIsDest() {
            val dest = InstructionRef("neg", Type.I32)
            val inst = Neg(dest, Constant.I32(5))
            assertEquals(dest, inst.result)
        }

        @Test
        fun faddDefaultFastMathIsNone() {
            val inst = FAdd(InstructionRef("r", Type.F64), Constant.F64(1.0), Constant.F64(2.0))
            assertEquals(FastMathFlags.NONE, inst.fastMath)
        }

        @Test
        fun faddWithFastMathFlags() {
            val flags = FastMathFlags(noNaNs = true, noInfs = true)
            val inst = FAdd(InstructionRef("r", Type.F64), Constant.F64(1.0), Constant.F64(2.0), fastMath = flags)
            assertTrue(inst.fastMath.noNaNs)
            assertTrue(inst.fastMath.noInfs)
        }

        @Test
        fun absWithIntMinFlag() {
            val inst = Abs(InstructionRef("r", Type.I32), Constant.I32(-5), isIntMin = true)
            assertTrue(inst.isIntMin)
        }

        @Test
        fun sqrtHasArithmeticCategory() {
            val inst = Sqrt(InstructionRef("r", Type.F64), Constant.F64(4.0))
            assertEquals(IrCategory.ARITHMETIC, inst.category)
        }
    }

    @Nested
    inner class BitwiseInstructions {

        @Test
        fun andHasBitwiseCategory() {
            val inst = And(InstructionRef("r", Type.I32), Constant.I32(0xFF), Constant.I32(0x0F))
            assertEquals(IrCategory.BITWISE, inst.category)
        }

        @Test
        fun shlWithFlags() {
            val inst = Shl(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(3), nuw = true, nsw = false)
            assertTrue(inst.nuw)
            assertFalse(inst.nsw)
        }

        @Test
        fun lshrWithExact() {
            val inst = LShr(InstructionRef("r", Type.I32), Constant.I32(8), Constant.I32(3), exact = true)
            assertTrue(inst.exact)
        }

        @Test
        fun ashrWithExact() {
            val inst = AShr(InstructionRef("r", Type.I32), Constant.I32(-8), Constant.I32(2), exact = true)
            assertTrue(inst.exact)
        }

        @Test
        fun notResultIsDest() {
            val dest = InstructionRef("inv", Type.I32)
            val inst = Not(dest, Constant.I32(0xFF))
            assertEquals(dest, inst.result)
        }
    }

    @Nested
    inner class ComparisonInstructions {

        @Test
        fun icmpHasComparisonCategory() {
            val inst = ICmp(InstructionRef("r", Type.I1), ICmpPredicate.EQ, Constant.I32(1), Constant.I32(1))
            assertEquals(IrCategory.COMPARISON, inst.category)
        }

        @Test
        fun icmpResultTypeIsI1() {
            val dest = InstructionRef("cmp", Type.I1)
            val inst = ICmp(dest, ICmpPredicate.SGT, Constant.I32(5), Constant.I32(3))
            assertEquals(Type.I1, inst.result!!.type)
        }

        @Test
        fun fcmpHasComparisonCategory() {
            val inst = FCmp(InstructionRef("r", Type.I1), FCmpPredicate.OEQ, Constant.F64(1.0), Constant.F64(1.0))
            assertEquals(IrCategory.COMPARISON, inst.category)
        }

        @Test
        fun fcmpDefaultFastMathIsNone() {
            val inst = FCmp(InstructionRef("r", Type.I1), FCmpPredicate.ULT, Constant.F64(1.0), Constant.F64(2.0))
            assertEquals(FastMathFlags.NONE, inst.fastMath)
        }
    }

    @Nested
    inner class MemoryInstructions {

        @Test
        fun allocaHasMemoryCategory() {
            val inst = Alloca(InstructionRef("p", Type.OpaquePointer), Type.I32)
            assertEquals(IrCategory.MEMORY, inst.category)
        }

        @Test
        fun allocaWithAlignment() {
            val inst = Alloca(InstructionRef("p", Type.OpaquePointer), Type.I64, align = 16)
            assertEquals(16, inst.align)
        }

        @Test
        fun loadDefaultsNotVolatile() {
            val inst = Load(InstructionRef("v", Type.I32), InstructionRef("p", Type.OpaquePointer), Type.I32)
            assertFalse(inst.volatile)
            assertNull(inst.ordering)
        }

        @Test
        fun loadVolatile() {
            val inst = Load(InstructionRef("v", Type.I32), InstructionRef("p", Type.OpaquePointer), Type.I32, volatile = true)
            assertTrue(inst.volatile)
        }

        @Test
        fun storeHasNoResult() {
            val inst = Store(Constant.I32(42), InstructionRef("p", Type.OpaquePointer))
            assertNull(inst.result)
        }

        @Test
        fun gepDefaultInBoundsIsTrue() {
            val inst = GetElementPtr(
                InstructionRef("gep", Type.OpaquePointer),
                Type.I32,
                InstructionRef("p", Type.OpaquePointer),
                listOf(Constant.I64(0)),
            )
            assertTrue(inst.inBounds)
        }

        @Test
        fun memCpyHasNoResult() {
            val inst = MemCpy(
                InstructionRef("dst", Type.OpaquePointer),
                InstructionRef("src", Type.OpaquePointer),
                Constant.I64(100),
            )
            assertNull(inst.result)
        }
    }

    @Nested
    inner class TerminatorInstructions {

        @Test
        fun retHasTerminatorCategory() {
            val inst = Ret(Constant.I32(0))
            assertEquals(IrCategory.TERMINATOR, inst.category)
        }

        @Test
        fun retVoidHasNullValue() {
            val inst = Ret(null)
            assertNull(inst.value)
        }

        @Test
        fun retHasNoResult() {
            val inst = Ret(Constant.I32(0))
            assertNull(inst.result)
        }

        @Test
        fun brHasTarget() {
            val inst = Br(BlockRef("next_block"))
            assertEquals(BlockRef("next_block"), inst.target)
        }

        @Test
        fun condBrHasBothTargets() {
            val inst = CondBr(InstructionRef("c", Type.I1), BlockRef("then"), BlockRef("else"))
            assertEquals(BlockRef("then"), inst.trueTarget)
            assertEquals(BlockRef("else"), inst.falseTarget)
        }

        @Test
        fun condBrDefaultWeightsAreZero() {
            val inst = CondBr(InstructionRef("c", Type.I1), BlockRef("then"), BlockRef("else"))
            assertEquals(0L, inst.trueWeight)
            assertEquals(0L, inst.falseWeight)
        }

        @Test
        fun switchHasCases() {
            val cases = listOf(Constant.I32(1) to BlockRef("one"), Constant.I32(2) to BlockRef("two"))
            val inst = Switch(InstructionRef("v", Type.I32), BlockRef("default"), cases)
            assertEquals(BlockRef("default"), inst.defaultTarget)
            assertEquals(2, inst.cases.size)
        }

        @Test
        fun indirectBrHasTargets() {
            val inst = IndirectBr(InstructionRef("addr", Type.OpaquePointer), listOf(BlockRef("a"), BlockRef("b"), BlockRef("c")))
            assertEquals(3, inst.targets.size)
        }

        @Test
        fun unreachableHasNoResult() {
            val inst = Unreachable()
            assertNull(inst.result)
        }

        @Test
        fun trapHasNoResult() {
            val inst = Trap()
            assertNull(inst.result)
        }
    }

    @Nested
    inner class CallInstructions {

        @Test
        fun callHasCallCategory() {
            val func = FunctionRef("foo", Type.Function(listOf(Type.I32), Type.I32))
            val inst = Call(InstructionRef("r", Type.I32), func, listOf(Constant.I32(1)), Type.I32)
            assertEquals(IrCategory.CALL, inst.category)
        }

        @Test
        fun callDefaultCallingConvIsC() {
            val func = FunctionRef("foo", Type.Function(emptyList(), Type.I32))
            val inst = Call(InstructionRef("r", Type.I32), func, emptyList(), Type.I32)
            assertEquals(CallingConvention.C, inst.callingConv)
        }

        @Test
        fun callDefaultTailCallIsNone() {
            val func = FunctionRef("foo", Type.Function(emptyList(), Type.I32))
            val inst = Call(InstructionRef("r", Type.I32), func, emptyList(), Type.I32)
            assertEquals(TailCallKind.NONE, inst.tailCall)
        }

        @Test
        fun invokeHasNormalAndUnwindDest() {
            val func = FunctionRef("bar", Type.Function(emptyList(), Type.I32))
            val inst = Invoke(InstructionRef("r", Type.I32), func, emptyList(), Type.I32, BlockRef("normal"), BlockRef("unwind"))
            assertEquals(BlockRef("normal"), inst.normalDest)
            assertEquals(BlockRef("unwind"), inst.unwindDest)
        }

        @Test
        fun callBrHasFallthroughAndIndirectDests() {
            val func = FunctionRef("baz", Type.Function(emptyList(), Type.Void))
            val inst = CallBr(null, func, emptyList(), Type.Void, BlockRef("fall"), listOf(BlockRef("ind1"), BlockRef("ind2")))
            assertEquals(BlockRef("fall"), inst.fallthrough)
            assertEquals(listOf(BlockRef("ind1"), BlockRef("ind2")), inst.indirectDests)
        }
    }

    @Nested
    inner class ConversionInstructions {

        @Test
        fun zextHasConversionCategory() {
            val inst = ZExt(InstructionRef("r", Type.I64), Constant.I32(1), Type.I64)
            assertEquals(IrCategory.CONVERSION, inst.category)
        }

        @Test
        fun sextResultIsDest() {
            val dest = InstructionRef("extended", Type.I64)
            val inst = SExt(dest, Constant.I32(-1), Type.I64)
            assertEquals(dest, inst.result)
        }

        @Test
        fun intTruncToType() {
            val inst = IntTrunc(InstructionRef("r", Type.I8), Constant.I32(256), Type.I8)
            assertEquals(Type.I8, inst.toType)
        }

        @Test
        fun bitCastPreservesType() {
            val inst = BitCast(InstructionRef("r", Type.F32), Constant.I32(0), Type.F32)
            assertEquals(Type.F32, inst.toType)
        }

        @Test
        fun addrSpaceCastHasConversionCategory() {
            val inst = AddrSpaceCast(
                InstructionRef("r", Type.Pointer(Type.I8, addressSpace = 1)),
                InstructionRef("p", Type.OpaquePointer),
                Type.Pointer(Type.I8, addressSpace = 1),
            )
            assertEquals(IrCategory.CONVERSION, inst.category)
        }
    }

    @Nested
    inner class SsaInstructions {

        @Test
        fun phiHasSsaCategory() {
            val inst = Phi(InstructionRef("r", Type.I32), listOf(Constant.I32(0) to BlockRef("entry"), Constant.I32(1) to BlockRef("loop")))
            assertEquals(IrCategory.SSA, inst.category)
        }

        @Test
        fun phiIncomingPairs() {
            val inst = Phi(InstructionRef("r", Type.I32), listOf(Constant.I32(0) to BlockRef("a"), Constant.I32(1) to BlockRef("b")))
            assertEquals(2, inst.incoming.size)
            assertEquals(BlockRef("a"), inst.incoming[0].second)
            assertEquals(BlockRef("b"), inst.incoming[1].second)
        }

        @Test
        fun selectHasConditionAndBothValues() {
            val inst = Select(
                InstructionRef("r", Type.I32),
                InstructionRef("c", Type.I1),
                Constant.I32(1),
                Constant.I32(2),
            )
            assertNotNull(inst.condition)
            assertNotNull(inst.trueValue)
            assertNotNull(inst.falseValue)
        }

        @Test
        fun freezeResultIsDest() {
            val dest = InstructionRef("frozen", Type.I32)
            val inst = Freeze(dest, InstructionRef("v", Type.I32))
            assertEquals(dest, inst.result)
        }
    }

    @Nested
    inner class ExceptionInstructions {

        @Test
        fun landingPadHasExceptionCategory() {
            val inst = LandingPad(InstructionRef("lp", Type.Struct(null, listOf(Type.OpaquePointer, Type.I32))),
                Type.Struct(null, listOf(Type.OpaquePointer, Type.I32)), emptyList(), cleanup = true)
            assertEquals(IrCategory.EXCEPTION, inst.category)
        }

        @Test
        fun resumeHasNoResult() {
            val inst = Resume(InstructionRef("exc", Type.Struct(null, listOf(Type.OpaquePointer, Type.I32))))
            assertNull(inst.result)
        }

        @Test
        fun catchSwitchHasHandlers() {
            val inst = CatchSwitch(InstructionRef("cs", Type.Token), null, listOf(BlockRef("handler1"), BlockRef("handler2")), BlockRef("cleanup"))
            assertEquals(2, inst.handlers.size)
            assertEquals(BlockRef("cleanup"), inst.unwindDest)
        }
    }

    @Nested
    inner class DataClassSemantics {

        @Test
        fun equalInstructionsAreEqual() {
            val inst1 = Add(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(2))
            val inst2 = Add(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(2))
            assertEquals(inst1, inst2)
            assertEquals(inst1.hashCode(), inst2.hashCode())
        }

        @Test
        fun differentInstructionsAreNotEqual() {
            val inst1 = Add(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(2))
            val inst2 = Add(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(3))
            assertNotEquals(inst1, inst2)
        }

        @Test
        fun copyWithModifiedField() {
            val original = Add(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(2))
            val modified = original.copy(nsw = true)
            assertFalse(original.nsw)
            assertTrue(modified.nsw)
        }

        @Test
        fun instructionIsPolymorphic() {
            val add: Instruction = Add(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(2))
            val ret: Instruction = Ret(Constant.I32(0))
            assertNotNull(add.result)
            assertNull(ret.result)
        }
    }
}
