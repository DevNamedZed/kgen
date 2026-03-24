package org.kgen.ir.build.sets;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.kgen.ir.ICmpPredicate;
import org.kgen.ir.InstructionRef;
import org.kgen.ir.Type;
import org.kgen.ir.build.InstructionSink;
import org.kgen.ir.instructions.Add;
import org.kgen.ir.instructions.ICmp;
import org.kgen.ir.instructions.Instruction;
import org.kgen.ir.instructions.Ret;
import org.kgen.ir.instructions.Sub;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link InstructionScope} from Java.
 *
 * <p>Java callers use {@code InstructionBuilder.create(ManagedScope.class, sink)}
 * to get a typed proxy. The proxy implements the scope interface, giving
 * direct access to all methods from the constituent instruction sets.
 */
public class InstructionScopeJavaTest {

    private static class TestSink implements InstructionSink {
        final List<Instruction> emitted = new ArrayList<>();
        private int counter = 0;

        @Override
        public void emit(Instruction instruction) {
            emitted.add(instruction);
        }

        @Override
        public InstructionRef nextRef(Type type) {
            return new InstructionRef("%" + counter++, type);
        }
    }

    @Nested
    class ManagedScopeFromJava {

        @Test
        void createReturnsManagedScopeProxy() {
            var sink = new TestSink();
            ManagedScope builder = InstructionBuilder.create(ManagedScope.class, sink);

            assertInstanceOf(ManagedScope.class, builder);
            assertInstanceOf(ArithmeticInstructionSet.class, builder);
            assertInstanceOf(ObjectInstructionSet.class, builder);
            assertInstanceOf(TerminatorInstructionSet.class, builder);
        }

        @Test
        void managedScopeEmitsInstructions() {
            var sink = new TestSink();
            ManagedScope builder = InstructionBuilder.create(ManagedScope.class, sink);

            var lhs = new InstructionRef("%x", Type.I32.INSTANCE);
            var rhs = new InstructionRef("%y", Type.I32.INSTANCE);
            builder.add(lhs, rhs, false, false);
            builder.ret(null);

            assertEquals(2, sink.emitted.size());
            assertInstanceOf(Add.class, sink.emitted.get(0));
            assertInstanceOf(Ret.class, sink.emitted.get(1));
        }
    }

    @Nested
    class NativeScopeFromJava {

        @Test
        void createReturnsNativeScopeProxy() {
            var sink = new TestSink();
            NativeScope builder = InstructionBuilder.create(NativeScope.class, sink);

            assertInstanceOf(NativeScope.class, builder);
            assertInstanceOf(ArithmeticInstructionSet.class, builder);
            assertInstanceOf(MemoryInstructionSet.class, builder);
            assertInstanceOf(TerminatorInstructionSet.class, builder);
        }

        @Test
        void nativeScopeEmitsInstructions() {
            var sink = new TestSink();
            NativeScope builder = InstructionBuilder.create(NativeScope.class, sink);

            var lhs = new InstructionRef("%x", Type.I32.INSTANCE);
            var rhs = new InstructionRef("%y", Type.I32.INSTANCE);

            builder.sub(lhs, rhs, false, false);
            builder.icmp(ICmpPredicate.EQ, lhs, rhs);
            builder.ret(null);

            assertEquals(3, sink.emitted.size());
            assertInstanceOf(Sub.class, sink.emitted.get(0));
            assertInstanceOf(ICmp.class, sink.emitted.get(1));
            assertInstanceOf(Ret.class, sink.emitted.get(2));
        }
    }

    @Nested
    class FullScopeFromJava {

        @Test
        void createReturnsFullScopeProxy() {
            var sink = new TestSink();
            FullScope builder = InstructionBuilder.create(FullScope.class, sink);

            assertInstanceOf(FullScope.class, builder);
            assertInstanceOf(ArithmeticInstructionSet.class, builder);
            assertInstanceOf(MemoryInstructionSet.class, builder);
            assertInstanceOf(ObjectInstructionSet.class, builder);
            assertInstanceOf(ExceptionInstructionSet.class, builder);
        }
    }

    @Nested
    class SsaCounterSharingFromJava {

        @Test
        void ssaCounterIncrementsAcrossSets() {
            var sink = new TestSink();
            NativeScope builder = InstructionBuilder.create(NativeScope.class, sink);

            var lhs = new InstructionRef("%x", Type.I32.INSTANCE);
            var rhs = new InstructionRef("%y", Type.I32.INSTANCE);

            var addResult = builder.add(lhs, rhs, false, false);
            var allocResult = builder.alloca(Type.I32.INSTANCE, null, null);

            assertEquals("%0", addResult.getName());
            assertEquals("%1", allocResult.getName());
        }
    }
}
