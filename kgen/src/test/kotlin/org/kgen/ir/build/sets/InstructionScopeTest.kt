package org.kgen.ir.build.sets

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.ICmpPredicate
import org.kgen.ir.InstructionRef
import org.kgen.ir.Type
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.Add
import org.kgen.ir.instructions.Br
import org.kgen.ir.instructions.ICmp
import org.kgen.ir.instructions.Instruction
import org.kgen.ir.instructions.Ret
import org.kgen.ir.instructions.Store
import org.kgen.ir.instructions.Sub

interface MinimalScope : ArithmeticInstructionSet, TerminatorInstructionSet

interface MemoryTerminatorScope : MemoryInstructionSet, TerminatorInstructionSet

interface ArithMemoryScope : ArithmeticInstructionSet, MemoryInstructionSet

interface EmptyScope : InstructionSet

class InstructionScopeTest {

    private class TestSink : InstructionSink {
        val emitted = mutableListOf<Instruction>()
        private var counter = 0

        override fun emit(instruction: Instruction) {
            emitted.add(instruction)
        }

        override fun nextRef(type: Type): InstructionRef {
            return InstructionRef("%${counter++}", type)
        }
    }

    @Nested
    inner class ManagedScopeCreation {

        @Test
        fun createReturnsManagedScopeProxy() {
            val sink = TestSink()
            val builder = InstructionBuilder.create<ManagedScope>(sink)

            assertTrue(builder is ManagedScope)
            assertTrue(builder is ArithmeticInstructionSet)
            assertTrue(builder is ObjectInstructionSet)
            assertTrue(builder is ComparisonInstructionSet)
            assertTrue(builder is TerminatorInstructionSet)
            assertTrue(builder is SsaInstructionSet)
            assertTrue(builder is DebugInstructionSet)
        }

        @Test
        fun managedScopeEmitsInstructions() {
            val sink = TestSink()
            val builder = InstructionBuilder.create<ManagedScope>(sink)

            val lhs = InstructionRef("%x", Type.I32)
            val rhs = InstructionRef("%y", Type.I32)
            builder.add(lhs, rhs)
            builder.ret(null)

            assertEquals(2, sink.emitted.size)
            assertTrue(sink.emitted[0] is Add)
            assertTrue(sink.emitted[1] is Ret)
        }
    }

    @Nested
    inner class NativeScopeCreation {

        @Test
        fun createReturnsNativeScopeProxy() {
            val sink = TestSink()
            val builder = InstructionBuilder.create<NativeScope>(sink)

            assertTrue(builder is NativeScope)
            assertTrue(builder is ArithmeticInstructionSet)
            assertTrue(builder is MemoryInstructionSet)
            assertTrue(builder is BitwiseInstructionSet)
            assertTrue(builder is ComparisonInstructionSet)
            assertTrue(builder is ConversionInstructionSet)
            assertTrue(builder is TerminatorInstructionSet)
            assertTrue(builder is SsaInstructionSet)
        }

        @Test
        fun nativeScopeEmitsInstructions() {
            val sink = TestSink()
            val builder = InstructionBuilder.create<NativeScope>(sink)

            val lhs = InstructionRef("%x", Type.I32)
            val rhs = InstructionRef("%y", Type.I32)
            builder.sub(lhs, rhs)
            builder.icmp(ICmpPredicate.EQ, lhs, rhs)
            builder.ret(null)

            assertEquals(3, sink.emitted.size)
            assertTrue(sink.emitted[0] is Sub)
            assertTrue(sink.emitted[1] is ICmp)
            assertTrue(sink.emitted[2] is Ret)
        }
    }

    @Nested
    inner class FullScopeCreation {

        @Test
        fun createReturnsFullScopeProxy() {
            val sink = TestSink()
            val builder = InstructionBuilder.create<FullScope>(sink)

            assertTrue(builder is FullScope)
            assertTrue(builder is ArithmeticInstructionSet)
            assertTrue(builder is ObjectInstructionSet)
            assertTrue(builder is MemoryInstructionSet)
            assertTrue(builder is BitwiseInstructionSet)
            assertTrue(builder is ComparisonInstructionSet)
            assertTrue(builder is ConversionInstructionSet)
            assertTrue(builder is TerminatorInstructionSet)
            assertTrue(builder is SsaInstructionSet)
            assertTrue(builder is DebugInstructionSet)
            assertTrue(builder is RuntimeInstructionSet)
            assertTrue(builder is InteropInstructionSet)
            assertTrue(builder is ExceptionInstructionSet)
        }
    }

    @Nested
    inner class ComputeScopeCreation {

        @Test
        fun createReturnsComputeScopeProxy() {
            val sink = TestSink()
            val builder = InstructionBuilder.create<ComputeScope>(sink)

            assertTrue(builder is ComputeScope)
            assertTrue(builder is ComputeInstructionSet)
            assertTrue(builder is ArithmeticInstructionSet)
        }
    }

    @Nested
    inner class CustomScopeCreation {

        @Test
        fun customScopeWorksWithTwoSets() {
            val sink = TestSink()
            val builder = InstructionBuilder.create<MinimalScope>(sink)

            assertTrue(builder is MinimalScope)
            assertTrue(builder is ArithmeticInstructionSet)
            assertTrue(builder is TerminatorInstructionSet)
            assertFalse(builder is MemoryInstructionSet)

            val lhs = InstructionRef("%a", Type.I32)
            val rhs = InstructionRef("%b", Type.I32)
            val sum = builder.add(lhs, rhs)
            builder.ret(sum)

            assertEquals(2, sink.emitted.size)
            assertTrue(sink.emitted[0] is Add)
            assertTrue(sink.emitted[1] is Ret)
        }

        @Test
        fun voidInstructionsEmitCorrectly() {
            val sink = TestSink()
            val builder = InstructionBuilder.create<MemoryTerminatorScope>(sink)

            builder.br(BlockRef("exit"))
            val ptr = InstructionRef("%ptr", Type.Pointer(Type.I32))
            val value = InstructionRef("%val", Type.I32)
            builder.store(value, ptr)

            assertEquals(2, sink.emitted.size)
            assertTrue(sink.emitted[0] is Br)
            assertTrue(sink.emitted[1] is Store)
        }
    }

    @Nested
    inner class SsaCounterSharing {

        @Test
        fun ssaCounterIncrementsAcrossSets() {
            val sink = TestSink()
            val builder = InstructionBuilder.create<ArithMemoryScope>(sink)

            val lhs = InstructionRef("%x", Type.I32)
            val rhs = InstructionRef("%y", Type.I32)

            val addResult = builder.add(lhs, rhs)
            val allocResult = builder.alloca(Type.I32)

            assertEquals("%0", addResult.name)
            assertEquals("%1", allocResult.name)
        }
    }

    @Nested
    inner class JavaClassOverload {

        @Test
        fun classOverloadProducesWorkingProxy() {
            val sink = TestSink()
            val builder = InstructionBuilder.create(ManagedScope::class.java, sink)

            assertTrue(builder is ManagedScope)
            assertTrue(builder is ArithmeticInstructionSet)
            assertTrue(builder is TerminatorInstructionSet)
        }
    }

    @Nested
    inner class InvalidScope {

        @Test
        fun emptyScopeThrows() {
            val sink = TestSink()
            assertThrows(IllegalArgumentException::class.java) {
                InstructionBuilder.create<EmptyScope>(sink)
            }
        }
    }
}
