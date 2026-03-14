package org.kgen.ir.instructions

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*

class InstructionEffectsTest {

    private val refX = InstructionRef("x", Type.I32)
    private val refY = InstructionRef("y", Type.I32)
    private val paramA = Parameter("a", Type.I32, 0)
    private val paramB = Parameter("b", Type.I32, 1)
    private val ptrRef = InstructionRef("ptr", Type.OpaquePointer)

    @Nested
    inner class PurityChecks {

        @Test
        fun addIsPure() {
            val inst = Add(refX, paramA, paramB)
            assertTrue(inst.effects.isPure())
            assertTrue(inst.effects.commutes())
        }

        @Test
        fun subIsPureButNotCommutative() {
            val inst = Sub(refX, paramA, paramB)
            assertTrue(inst.effects.isPure())
            assertFalse(inst.effects.commutes())
        }

        @Test
        fun mulIsPureAndCommutative() {
            val inst = Mul(refX, paramA, paramB)
            assertTrue(inst.effects.isPure())
            assertTrue(inst.effects.commutes())
        }

        @Test
        fun divisionMayTrap() {
            val inst = SDiv(refX, paramA, paramB)
            assertFalse(inst.effects.isPure())
            assertTrue(inst.effects.canTrap())
            assertFalse(inst.effects.hasSideEffects())
        }

        @Test
        fun gepIsPure() {
            val inst = GetElementPtr(ptrRef, Type.I32, ptrRef, listOf(paramA))
            assertTrue(inst.effects.isPure())
        }

        @Test
        fun phiIsPure() {
            val inst = Phi(refX, listOf(paramA to BlockRef("block0"), paramB to BlockRef("block1")))
            assertTrue(inst.effects.isPure())
        }

        @Test
        fun selectIsPure() {
            val cond = InstructionRef("c", Type.I1)
            val inst = Select(refX, cond, paramA, paramB)
            assertTrue(inst.effects.isPure())
        }

        @Test
        fun bitwiseAndIsCommutative() {
            val inst = And(refX, paramA, paramB)
            assertTrue(inst.effects.isPure())
            assertTrue(inst.effects.commutes())
        }

        @Test
        fun conversionsArePure() {
            assertTrue(ZExt(InstructionRef("r", Type.I64), paramA, Type.I64).effects.isPure())
            assertTrue(SExt(InstructionRef("r", Type.I64), paramA, Type.I64).effects.isPure())
            assertTrue(IntTrunc(InstructionRef("r", Type.I16), paramA, Type.I16).effects.isPure())
        }
    }

    @Nested
    inner class MemoryEffects {

        @Test
        fun loadReadsMemory() {
            val inst = Load(refX, ptrRef, Type.I32)
            assertTrue(inst.effects.readsMemory())
            assertFalse(inst.effects.writesMemory())
            assertFalse(inst.effects.hasSideEffects())
        }

        @Test
        fun storeWritesMemory() {
            val inst = Store(paramA, ptrRef)
            assertTrue(inst.effects.writesMemory())
            assertTrue(inst.effects.hasSideEffects())
        }

        @Test
        fun allocaWritesStackMemory() {
            val inst = Alloca(ptrRef, Type.I32)
            assertTrue(inst.effects.writesStackMemory())
            assertTrue(inst.effects.hasSideEffects())
            assertFalse(inst.effects.readsMemory())
        }

        @Test
        fun memcpyReadsAndWritesMemory() {
            val inst = MemCpy(ptrRef, ptrRef, paramA, false)
            assertTrue(inst.effects.readsMemory())
            assertTrue(inst.effects.writesMemory())
            assertTrue(inst.effects.hasSideEffects())
        }
    }

    @Nested
    inner class ControlFlowEffects {

        @Test
        fun retIsTerminatorAndReturn() {
            val inst = Ret(paramA)
            assertTrue(inst.effects.isTerminator())
            assertTrue(inst.effects.isReturn())
            assertTrue(inst.effects.hasSideEffects())
            assertFalse(inst.effects.isBranch())
        }

        @Test
        fun brIsBranchAndTerminator() {
            val inst = Br(BlockRef("target"))
            assertTrue(inst.effects.isTerminator())
            assertTrue(inst.effects.isBranch())
            assertFalse(inst.effects.isReturn())
        }

        @Test
        fun condBrIsBranch() {
            val inst = CondBr(paramA, BlockRef("then"), BlockRef("else"))
            assertTrue(inst.effects.isTerminator())
            assertTrue(inst.effects.isBranch())
        }

        @Test
        fun unreachableIsTerminator() {
            val inst = Unreachable()
            assertTrue(inst.effects.isTerminator())
            assertTrue(inst.effects.canTrap())
        }
    }

    @Nested
    inner class CallEffects {

        @Test
        fun callHasAllCallEffects() {
            val func = FunctionRef("foo", Type.Function(listOf(Type.I32), Type.I32))
            val inst = Call(refX, func, listOf(paramA), Type.I32)
            assertTrue(inst.effects.isCall())
            assertTrue(inst.effects.hasSideEffects())
            assertTrue(inst.effects.readsMemory())
            assertTrue(inst.effects.writesMemory())
            assertTrue(inst.effects.canThrow())
            assertTrue(inst.effects.isSafepoint())
            assertFalse(inst.effects.isTerminator())
        }

        @Test
        fun invokeIsCallAndTerminator() {
            val func = FunctionRef("foo", Type.Function(listOf(Type.I32), Type.I32))
            val inst = Invoke(refX, func, listOf(paramA), Type.I32, BlockRef("normal"), BlockRef("unwind"))
            assertTrue(inst.effects.isCall())
            assertTrue(inst.effects.isTerminator())
            assertTrue(inst.effects.canThrow())
        }
    }

    @Nested
    inner class AtomicEffects {

        @Test
        fun fenceIsBarrier() {
            val inst = Fence(AtomicOrdering.SEQ_CST)
            assertTrue(inst.effects.isBarrier())
            assertTrue(inst.effects.hasSideEffects())
            assertFalse(inst.effects.readsMemory())
        }

        @Test
        fun atomicRmwIsBarrierAndReadsWritesMemory() {
            val inst = AtomicRMW(refX, AtomicRMWOp.ADD, ptrRef, paramA, AtomicOrdering.SEQ_CST)
            assertTrue(inst.effects.isBarrier())
            assertTrue(inst.effects.readsMemory())
            assertTrue(inst.effects.writesMemory())
            assertTrue(inst.effects.hasSideEffects())
        }
    }

    @Nested
    inner class DebugEffects {

        @Test
        fun debugInstructionsHaveNoEffects() {
            assertTrue(DebugLoc(1, 5, "test.kt").effects.isPure())
            assertTrue(DebugValue("x", paramA).effects.isPure())
            assertTrue(DebugDeclare("y", ptrRef).effects.isPure())
        }
    }

    @Nested
    inner class ObjectEffects {

        @Test
        fun newObjectAllocates() {
            val inst = NewObject(refX, "MyClass", emptyList())
            assertTrue(inst.effects.writesMemory())
            assertTrue(inst.effects.hasSideEffects())
            assertTrue(inst.effects.isSafepoint())
            assertTrue(inst.effects.canThrow())
        }

        @Test
        fun getFieldReadsMemory() {
            val obj = InstructionRef("obj", Type.ClassRef("MyClass"))
            val inst = GetField(refX, obj, "MyClass", "field", Type.I32)
            assertTrue(inst.effects.readsMemory())
            assertFalse(inst.effects.writesMemory())
            assertFalse(inst.effects.hasSideEffects())
        }

        @Test
        fun putFieldWritesMemory() {
            val obj = InstructionRef("obj", Type.ClassRef("MyClass"))
            val inst = PutField(obj, "MyClass", "field", Type.I32, paramA)
            assertTrue(inst.effects.writesMemory())
            assertTrue(inst.effects.hasSideEffects())
        }

        @Test
        fun instanceOfIsPure() {
            val obj = InstructionRef("obj", Type.ClassRef("Base"))
            val inst = InstanceOf(InstructionRef("r", Type.I1), obj, Type.ClassRef("Derived"))
            assertTrue(inst.effects.isPure())
        }

        @Test
        fun checkCastMayThrow() {
            val obj = InstructionRef("obj", Type.ClassRef("Base"))
            val inst = CheckCast(InstructionRef("r", Type.ClassRef("Derived")), obj, Type.ClassRef("Derived"))
            assertTrue(inst.effects.canThrow())
            assertTrue(inst.effects.hasSideEffects())
        }

        @Test
        fun throwIsTerminator() {
            val exc = InstructionRef("exc", Type.ClassRef("Exception"))
            val inst = Throw(exc)
            assertTrue(inst.effects.isTerminator())
            assertTrue(inst.effects.canThrow())
            assertTrue(inst.effects.hasSideEffects())
        }

        @Test
        fun virtualCallIsCall() {
            val obj = InstructionRef("obj", Type.ClassRef("MyClass"))
            val inst = VirtualCall(refX, obj, "MyClass", "method", Type.Function(emptyList(), Type.I32), emptyList())
            assertTrue(inst.effects.isCall())
            assertTrue(inst.effects.hasSideEffects())
        }
    }

    @Nested
    inner class RuntimeEffects {

        @Test
        fun gcAllocIsSafepointAndAllocates() {
            val inst = GCAlloc(refX, Type.ClassRef("Obj"), paramA)
            assertTrue(inst.effects.isSafepoint())
            assertTrue(inst.effects.writesMemory())
            assertTrue(inst.effects.canThrow())
        }

        @Test
        fun gcSafepointIsSafepoint() {
            val inst = GCSafepoint()
            assertTrue(inst.effects.isSafepoint())
            assertTrue(inst.effects.hasSideEffects())
        }

        @Test
        fun writeBarrierWritesMemory() {
            val inst = WriteBarrier(ptrRef, paramA, paramB)
            assertTrue(inst.effects.writesMemory())
            assertTrue(inst.effects.hasSideEffects())
        }
    }

    @Nested
    inner class EffectQueryApi {

        @Test
        fun pureEffectsToString() {
            assertEquals("InstructionEffects[PURE]", InstructionEffects.PURE.toString())
        }

        @Test
        fun callEffectsToStringContainsExpectedFlags() {
            val str = InstructionEffects.CALL.toString()
            assertTrue(str.contains("call"))
            assertTrue(str.contains("sideEffects"))
            assertTrue(str.contains("safepoint"))
        }

        @Test
        fun unionCombinesEffects() {
            val readOnly = InstructionEffects.READS_HEAP
            val writeOnly = InstructionEffects.WRITES_HEAP
            val combined = readOnly.union(writeOnly)
            assertTrue(combined.readsMemory())
            assertTrue(combined.writesMemory())
            assertTrue(combined.hasSideEffects())
        }

        @Test
        fun equalityWorks() {
            assertEquals(InstructionEffects.PURE, InstructionEffects.PURE)
            assertEquals(InstructionEffects.CALL, InstructionEffects.CALL)
            assertNotEquals(InstructionEffects.PURE, InstructionEffects.CALL)
        }

        @Test
        fun ofCreatesFromBits() {
            val effects = InstructionEffects.of(
                InstructionEffects.READS_HEAP_MEMORY or InstructionEffects.CAN_TRAP
            )
            assertTrue(effects.readsMemory())
            assertTrue(effects.canTrap())
            assertFalse(effects.writesMemory())
        }
    }
}
