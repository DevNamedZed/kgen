package org.kgen.ir.instructions

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*

class EffectOrderingTest {

    private val refX = InstructionRef("x", Type.I32)
    private val refY = InstructionRef("y", Type.I32)
    private val paramA = Parameter("a", Type.I32, 0)
    private val paramB = Parameter("b", Type.I32, 1)
    private val ptrRef = InstructionRef("ptr", Type.OpaquePointer)

    @Nested
    inner class Rule1SideEffects {

        @Test
        fun twoSideEffectInstructionsMustOrder() {
            val store1 = Store(paramA, ptrRef)
            val store2 = Store(paramB, ptrRef)
            assertTrue(EffectOrdering.mustOrder(store1, store2))
        }

        @Test
        fun sideEffectAndPureMustOrder() {
            val store = Store(paramA, ptrRef)
            val add = Add(refX, paramA, paramB)
            assertTrue(EffectOrdering.mustOrder(store, add))
            assertTrue(EffectOrdering.mustOrder(add, store))
        }
    }

    @Nested
    inner class Rule2SafepointReference {

        @Test
        fun safepointAndReferenceProducerMustOrder() {
            val safepoint = GCSafepoint()
            val objRef = InstructionRef("obj", Type.Reference(Type.ClassRef("Obj")))
            val getField = GetField(objRef, objRef, "Obj", "field", Type.I32)
            assertTrue(EffectOrdering.mustOrder(safepoint, getField))
        }

        @Test
        fun safepointAndPureIntegerCanStillBeOrdered() {
            // Safepoint has side effects so rule 1 fires regardless
            val safepoint = GCSafepoint()
            val add = Add(refX, paramA, paramB)
            assertTrue(EffectOrdering.mustOrder(safepoint, add))
        }
    }

    @Nested
    inner class Rule3BarrierAndHeap {

        @Test
        fun barrierAndHeapReadMustOrder() {
            val fence = Fence(AtomicOrdering.SEQ_CST)
            val load = Load(refX, ptrRef, Type.I32)
            // Fence has side effects, so rule 1 fires
            assertTrue(EffectOrdering.mustOrder(fence, load))
        }
    }

    @Nested
    inner class Rule4MemoryConflict {

        @Test
        fun twoLoadsMayReorder() {
            // Two pure loads with no side effects — but Load has readsHeap, not hasSideEffects
            // Rule 4 needs at least one write, two reads can commute
            val load1 = Load(refX, ptrRef, Type.I32)
            val load2 = Load(refY, ptrRef, Type.I32)
            // Rule 4 says both access memory but neither writes, so no conflict from rule 4
            // However, no other rules fire either (not side effect, not terminator, etc.)
            assertFalse(EffectOrdering.mustOrder(load1, load2))
        }

        @Test
        fun loadAndStoreMustOrder() {
            val load = Load(refX, ptrRef, Type.I32)
            val store = Store(paramA, ptrRef)
            // Store has side effects → rule 1 fires
            assertTrue(EffectOrdering.mustOrder(load, store))
        }
    }

    @Nested
    inner class Rule5ControlFlow {

        @Test
        fun terminatorMustOrderWithAnything() {
            val ret = Ret(paramA)
            val add = Add(refX, paramA, paramB)
            assertTrue(EffectOrdering.mustOrder(add, ret))
            assertTrue(EffectOrdering.mustOrder(ret, add))
        }

        @Test
        fun branchMustOrderWithAnything() {
            val br = Br(BlockRef("target"))
            val add = Add(refX, paramA, paramB)
            assertTrue(EffectOrdering.mustOrder(br, add))
        }
    }

    @Nested
    inner class Rule6ExceptionsAndTraps {

        @Test
        fun throwingInstructionMustOrder() {
            val exc = InstructionRef("exc", Type.ClassRef("Exception"))
            val throwInst = Throw(exc)
            val add = Add(refX, paramA, paramB)
            assertTrue(EffectOrdering.mustOrder(throwInst, add))
        }

        @Test
        fun trappingInstructionMustOrder() {
            val div = SDiv(refX, paramA, paramB)
            val add = Add(refX, paramA, paramB)
            // SDiv has canTrap=true, so rule 6 fires
            assertTrue(EffectOrdering.mustOrder(div, add))
        }
    }

    @Nested
    inner class Rule7Divergence {

        @Test
        fun divergentBranchAndComputeBarrierMustOrder() {
            val divergent = DivergentBranch(paramA, BlockRef("then"), BlockRef("else"))
            val barrier = ComputeBarrier(ComputeScope.BLOCK)
            assertTrue(EffectOrdering.mustOrder(divergent, barrier))
            assertTrue(EffectOrdering.mustOrder(barrier, divergent))
        }

        @Test
        fun divergentBranchAndComputeFenceMustOrder() {
            val divergent = DivergentBranch(paramA, BlockRef("then"), BlockRef("else"))
            val fence = ComputeFence(ComputeScope.WARP, MemorySpace.SHARED)
            assertTrue(EffectOrdering.mustOrder(divergent, fence))
        }
    }

    @Nested
    inner class PureInstructionsCommute {

        @Test
        fun twoPureArithmeticInstructionsCommute() {
            val add = Add(refX, paramA, paramB)
            val mul = Mul(refY, paramA, paramB)
            assertFalse(EffectOrdering.mustOrder(add, mul))
        }

        @Test
        fun pureConversionAndPureArithmeticCommute() {
            val ext = ZExt(InstructionRef("e", Type.I64), paramA, Type.I64)
            val add = Add(refX, paramA, paramB)
            assertFalse(EffectOrdering.mustOrder(ext, add))
        }
    }

    @Nested
    inner class RefinedOrdering {

        @Test
        fun primitiveWriteBarrierCommutesWithPureAfterRefinement() {
            val barrier = WriteBarrier(ptrRef, paramA, paramB)
            val add = Add(refX, paramA, paramB)
            // Static: mustOrder returns true (WriteBarrier has side effects)
            assertTrue(EffectOrdering.mustOrder(barrier, add))
            // Refined: primitive store eliminates barrier effects
            assertFalse(EffectOrdering.mustOrderRefined(barrier, add))
        }

        @Test
        fun referenceWriteBarrierStillOrdersAfterRefinement() {
            val refValue = InstructionRef("ref", Type.Reference(Type.ClassRef("Obj")))
            val barrier = WriteBarrier(ptrRef, paramA, refValue)
            val add = Add(refX, paramA, paramB)
            assertTrue(EffectOrdering.mustOrderRefined(barrier, add))
        }
    }

    @Nested
    inner class DivergentEffectBit {

        @Test
        fun divergentBranchHasDivergentEffect() {
            val inst = DivergentBranch(paramA, BlockRef("then"), BlockRef("else"))
            assertTrue(inst.effects.isDivergent())
            assertTrue(inst.effects.isTerminator())
            assertTrue(inst.effects.isBranch())
        }

        @Test
        fun divergentBranchPreset() {
            val effects = InstructionEffects.DIVERGENT_BRANCH
            assertTrue(effects.isDivergent())
            assertTrue(effects.isTerminator())
            assertTrue(effects.isBranch())
            assertTrue(effects.hasSideEffects())
        }

        @Test
        fun regularBranchIsNotDivergent() {
            val br = CondBr(paramA, BlockRef("then"), BlockRef("else"))
            assertFalse(br.effects.isDivergent())
        }

        @Test
        fun toStringIncludesDivergent() {
            val effects = InstructionEffects(InstructionEffects.IS_DIVERGENT)
            assertTrue(effects.toString().contains("divergent"))
        }
    }
}
