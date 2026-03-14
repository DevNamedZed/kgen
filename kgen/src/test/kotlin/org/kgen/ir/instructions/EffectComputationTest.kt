package org.kgen.ir.instructions

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*

class EffectComputationTest {

    private val refX = InstructionRef("x", Type.I32)
    private val paramA = Parameter("a", Type.I32, 0)
    private val paramB = Parameter("b", Type.I32, 1)
    private val ptrRef = InstructionRef("ptr", Type.OpaquePointer)

    @Nested
    inner class WriteBarrierRefinement {

        @Test
        fun primitiveStoreEliminatesBarrier() {
            val barrier = WriteBarrier(ptrRef, paramA, paramB)
            val refined = EffectComputation.computeEffects(barrier)
            assertTrue(refined.isPure())
        }

        @Test
        fun referenceStorePreservesBarrier() {
            val refValue = InstructionRef("ref", Type.Reference(Type.ClassRef("Obj")))
            val barrier = WriteBarrier(ptrRef, paramA, refValue)
            val refined = EffectComputation.computeEffects(barrier)
            assertFalse(refined.isPure())
            assertTrue(refined.writesMemory())
            assertTrue(refined.hasSideEffects())
        }

        @Test
        fun weakReferenceStorePreservesBarrier() {
            val weakRef = InstructionRef("weak", Type.WeakReference(Type.ClassRef("Obj")))
            val barrier = WriteBarrier(ptrRef, paramA, weakRef)
            val refined = EffectComputation.computeEffects(barrier)
            assertFalse(refined.isPure())
        }
    }

    @Nested
    inner class DefaultRefinement {

        @Test
        fun addReturnsSameAsStatic() {
            val inst = Add(refX, paramA, paramB)
            val refined = EffectComputation.computeEffects(inst)
            assertEquals(inst.effects, refined)
        }

        @Test
        fun loadReturnsSameAsStatic() {
            val inst = Load(refX, ptrRef, Type.I32)
            val refined = EffectComputation.computeEffects(inst)
            assertEquals(inst.effects, refined)
        }

        @Test
        fun callReturnsSameAsStatic() {
            val func = FunctionRef("foo", Type.Function(listOf(Type.I32), Type.I32))
            val inst = Call(refX, func, listOf(paramA), Type.I32)
            val refined = EffectComputation.computeEffects(inst)
            assertEquals(inst.effects, refined)
        }
    }

    @Nested
    inner class NarrowMethod {

        @Test
        fun narrowClearsSpecifiedBits() {
            val effects = InstructionEffects.WRITE_BARRIER
            val narrowed = effects.narrow(InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
            assertTrue(narrowed.isPure())
        }

        @Test
        fun narrowDoesNotAffectUnspecifiedBits() {
            val effects = InstructionEffects(
                InstructionEffects.READS_HEAP_MEMORY or InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS
            )
            val narrowed = effects.narrow(InstructionEffects.WRITES_HEAP_MEMORY)
            assertTrue(narrowed.readsHeapMemory())
            assertFalse(narrowed.writesHeapMemory())
            assertTrue(narrowed.hasSideEffects())
        }
    }
}
