package org.kgen.ir.build.sets

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.InstructionRef
import org.kgen.ir.Type
import org.kgen.ir.Value
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.Add
import org.kgen.ir.instructions.Instruction

interface CustomInstructionSet : InstructionSet {
    fun customOp(value: Value): Value
}

private class CustomInstructionSetProvider(private val sink: InstructionSink) : CustomInstructionSet {
    override fun customOp(value: Value): Value {
        val ref = sink.nextRef(value.type)
        sink.emit(Add(ref, value, value))
        return ref
    }
}

interface UnknownInstructionSet : InstructionSet {
    fun noop()
}

interface ExtendedScope : ArithmeticInstructionSet, CustomInstructionSet

interface FullExtendedScope : ArithmeticInstructionSet, CustomInstructionSet, TerminatorInstructionSet

class InstructionSetRegistryTest {

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
    inner class BuiltInRegistration {

        @Test
        fun allBuiltInSetsAreRegistered() {
            val sink = TestSink()
            val builtInSets = listOf(
                ArithmeticInstructionSet::class.java,
                BitwiseInstructionSet::class.java,
                ComparisonInstructionSet::class.java,
                ConversionInstructionSet::class.java,
                MemoryInstructionSet::class.java,
                AtomicInstructionSet::class.java,
                VectorInstructionSet::class.java,
                AggregateInstructionSet::class.java,
                ExceptionInstructionSet::class.java,
                TerminatorInstructionSet::class.java,
                CallInstructionSet::class.java,
                SsaInstructionSet::class.java,
                DebugInstructionSet::class.java,
                IntrinsicInstructionSet::class.java,
                RuntimeInstructionSet::class.java,
                InteropInstructionSet::class.java,
                ObjectInstructionSet::class.java,
                ComputeInstructionSet::class.java,
                DeoptimizationInstructionSet::class.java,
            )

            for (setClass in builtInSets) {
                val instance = InstructionSetRegistry.createForInterface(setClass, sink)
                assertTrue(
                    setClass.isInstance(instance),
                    "${setClass.simpleName} should be registered and return a matching implementation"
                )
            }
        }
    }

    @Nested
    inner class ThirdPartyExtension {

        @Test
        fun registerAndUseThirdPartySet() {
            InstructionSetRegistry.register(CustomInstructionSet::class) { sink ->
                CustomInstructionSetProvider(sink)
            }

            val sink = TestSink()
            val builder = InstructionBuilder.create<ExtendedScope>(sink)

            assertTrue(builder is ArithmeticInstructionSet)
            assertTrue(builder is CustomInstructionSet)

            val value = InstructionRef("%x", Type.I32)
            builder.customOp(value)

            assertEquals(1, sink.emitted.size)
            assertTrue(sink.emitted[0] is Add)
        }

        @Test
        fun thirdPartyAlongsideBuiltIn() {
            InstructionSetRegistry.register(CustomInstructionSet::class) { sink ->
                CustomInstructionSetProvider(sink)
            }

            val sink = TestSink()
            val builder = InstructionBuilder.create<FullExtendedScope>(sink)

            val value = InstructionRef("%x", Type.I32)
            builder.add(value, value)
            builder.customOp(value)
            builder.ret(value)

            assertEquals(3, sink.emitted.size)
        }

        @Test
        fun registerWithJavaClassOverload() {
            InstructionSetRegistry.register(CustomInstructionSet::class.java) { sink ->
                CustomInstructionSetProvider(sink)
            }

            val sink = TestSink()
            val instance = InstructionSetRegistry.createForInterface(CustomInstructionSet::class.java, sink)
            assertTrue(instance is CustomInstructionSet)
        }
    }

    @Nested
    inner class UnregisteredSetHandling {

        @Test
        fun unregisteredSetThrows() {
            val sink = TestSink()
            assertThrows(IllegalStateException::class.java) {
                InstructionSetRegistry.createForInterface(UnknownInstructionSet::class.java, sink)
            }
        }
    }
}
