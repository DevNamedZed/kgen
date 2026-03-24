package org.kgen.codegen.alloc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.instructions.Phi
import org.kgen.ir.target.Target
import org.kgen.jit.JitEngine
import org.kgen.target.x86.codegen.X86CodeGenerator

/**
 * Tests for register allocator coverage gaps: loop back-edge phi copies,
 * mixed-type spill pressure, values surviving multiple calls, and
 * multi-parameter functions with stack spills.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class SpillCoverageGapTest {

    private fun buildModule(block: ModuleBuilder.() -> Unit): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        builder.block()
        return builder.build()
    }

    private fun jitCall(module: Module, name: String, vararg args: Long): Long {
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        return engine.call(name, *args)
    }

    /**
     * Patches phi instructions in a named block to add back-edge incoming values.
     * Each entry maps a phi dest ref name to (value, blockRef) to append.
     */
    private fun patchPhis(
        module: Module,
        functionName: String,
        blockLabel: String,
        patches: Map<String, Pair<Value, BlockRef>>
    ): Module {
        val function = module.functions.first { it.name == functionName }
        val block = function.blocks.first { it.label == blockLabel }
        val patchedInstructions = block.instructions.map { instruction ->
            if (instruction is Phi && patches.containsKey(instruction.dest.name)) {
                val patch = patches[instruction.dest.name]!!
                instruction.copy(incoming = instruction.incoming + patch)
            } else {
                instruction
            }
        }
        val patchedBlock = block.copy(instructions = patchedInstructions)
        val patchedFunction = function.copy(
            blocks = function.blocks.map { if (it.label == blockLabel) patchedBlock else it }
        )
        return module.copy(
            functions = module.functions.map { if (it.name == functionName) patchedFunction else it }
        )
    }

    /**
     * Loop with 3 phi nodes where back-edge values depend on the phi destinations.
     * counter: 0,1,...,9. accumulator: sum of counters 0..9 = 45.
     * product: starts at 1, doubles each iteration = 1024.
     * Returns accumulator + product at iteration 10.
     */
    @Test
    fun phiCopiesAtLoopBackEdge() {
        val builder = ModuleBuilder("test", Target.x86_64())
        builder.createFunction("f", emptyList(), Type.I64)
        builder.appendBlock("entry")
        builder.br("loop")

        builder.appendBlock("loop")
        val counter = builder.phi(Type.I64, listOf(Constant.I64(0) to BlockRef("entry")))
        val accumulator = builder.phi(Type.I64, listOf(Constant.I64(0) to BlockRef("entry")))
        val product = builder.phi(Type.I64, listOf(Constant.I64(1) to BlockRef("entry")))
        val nextCounter = builder.add(counter, Constant.I64(1))
        val nextAccumulator = builder.add(accumulator, counter)
        val nextProduct = builder.add(product, product)
        val done = builder.icmp(ICmpPredicate.EQ, nextCounter, Constant.I64(10))
        builder.condBr(done, "exit", "loop")

        builder.appendBlock("exit")
        builder.ret(builder.add(nextAccumulator, nextProduct))
        builder.finalizeFunction()

        val module = patchPhis(builder.build(), "f", "loop", mapOf(
            counter.name to (nextCounter to BlockRef("loop")),
            accumulator.name to (nextAccumulator to BlockRef("loop")),
            product.name to (nextProduct to BlockRef("loop")),
        ))
        // counter goes 0..9, accumulator = 0+1+...+9 = 45, product = 2^10 = 1024
        assertEquals(1069L, jitCall(module, "f"))
    }

    /**
     * 8 I64 loads, 8 I32 loads, and 4 F64 loads all live simultaneously.
     * Forces mixed-type spilling across GP and FP register files.
     * Returns sum of all values converted to I64.
     */
    @Test
    fun mixedTypeSpillPressure() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("base", Type.I64)), Type.I64)
            appendBlock("entry")

            val longValues = (0 until 8).map { index ->
                load(Type.I64, add(params[0], Constant.I64(index.toLong() * 8)))
            }
            val intValues = (0 until 8).map { index ->
                load(Type.I32, add(params[0], Constant.I64(64 + index.toLong() * 4)))
            }
            val doubleValues = (0 until 4).map { index ->
                load(Type.F64, add(params[0], Constant.I64(96 + index.toLong() * 8)))
            }

            var longSum = longValues[0]
            for (index in 1 until longValues.size) { longSum = add(longSum, longValues[index]) }

            var intSum = intValues[0]
            for (index in 1 until intValues.size) { intSum = add(intSum, intValues[index]) }

            var doubleSum = doubleValues[0]
            for (index in 1 until doubleValues.size) { doubleSum = fadd(doubleSum, doubleValues[index]) }

            val intSumExtended = sext(intSum, Type.I64)
            val doubleSumAsLong = fptosi(doubleSum, Type.I64)
            ret(add(add(longSum, intSumExtended), doubleSumAsLong))
            finalizeFunction()
        }

        val arena = java.lang.foreign.Arena.ofShared()
        val memory = arena.allocate(128, 8)
        var expectedLongSum = 0L
        for (index in 0 until 8) {
            val value = (index + 1) * 100L
            memory.set(java.lang.foreign.ValueLayout.JAVA_LONG, index.toLong() * 8, value)
            expectedLongSum += value
        }
        var expectedIntSum = 0
        for (index in 0 until 8) {
            val value = (index + 1) * 10
            memory.set(java.lang.foreign.ValueLayout.JAVA_INT, 64 + index.toLong() * 4, value)
            expectedIntSum += value
        }
        var expectedDoubleSum = 0.0
        for (index in 0 until 4) {
            val value = (index + 1) * 1000.0
            memory.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 96 + index.toLong() * 8, value)
            expectedDoubleSum += value
        }
        // longSum=3600, intSum=360, doubleSum=10000 => total=13960
        val expected = expectedLongSum + expectedIntSum + expectedDoubleSum.toLong()
        assertEquals(expected, jitCall(module, "f", memory.address()))
        arena.close()
    }

    /**
     * A value loaded from memory must survive 3 consecutive function calls.
     * Each callee returns its argument + a constant. The original value
     * is used after all calls to compute the final sum.
     */
    @Test
    fun valuesLiveAcrossMultipleCalls() {
        val module = buildModule {
            for (index in 0 until 3) {
                val calleeParams = createFunction(
                    "callee_$index", listOf(Param("x", Type.I64)), Type.I64
                )
                appendBlock("entry")
                ret(add(calleeParams[0], Constant.I64((index + 1).toLong())))
                finalizeFunction()
            }

            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I64)
            appendBlock("entry")
            val original = load(Type.I64, params[0])
            val resultA = call("callee_0", listOf(Constant.I64(10)), Type.I64)
            val resultB = call("callee_1", listOf(Constant.I64(20)), Type.I64)
            val resultC = call("callee_2", listOf(Constant.I64(30)), Type.I64)
            ret(add(add(add(original, resultA!!), resultB!!), resultC!!))
            finalizeFunction()
        }

        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val memory = arena.allocate(8, 8)
        memory.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, 1000L)
        // original=1000, callee_0(10)=11, callee_1(20)=22, callee_2(30)=33
        assertEquals(1066L, engine.call("f", memory.address()))
        arena.close()
    }

    /**
     * 5 I64 parameters (4 in registers + 1 on stack on Win64, 5 in registers on SysV).
     * Sums all 5 and verifies the exact result.
     */
    @Test
    fun fiveParameterFunction() {
        val module = buildModule {
            val params = createFunction("f", listOf(
                Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64),
                Param("d", Type.I64), Param("e", Type.I64)
            ), Type.I64)
            appendBlock("entry")
            val ab = add(params[0], params[1])
            val abc = add(ab, params[2])
            val abcd = add(abc, params[3])
            ret(add(abcd, params[4]))
            finalizeFunction()
        }
        assertEquals(150L, jitCall(module, "f", 10, 20, 30, 40, 50))
        assertEquals(500_000_000_005L, jitCall(module, "f",
            100_000_000_001L, 100_000_000_001L, 100_000_000_001L,
            100_000_000_001L, 100_000_000_001L))
    }

    /**
     * 6 parameters with alternating types: (I64, I32, I64, I32, I64, I32).
     * Tests mixed-type parameter passing across register classes and stack slots.
     */
    @Test
    fun sixParameterMixedTypes() {
        val module = buildModule {
            val params = createFunction("f", listOf(
                Param("a", Type.I64), Param("b", Type.I32), Param("c", Type.I64),
                Param("d", Type.I32), Param("e", Type.I64), Param("g", Type.I32)
            ), Type.I64)
            appendBlock("entry")
            val bExtended = sext(params[1], Type.I64)
            val dExtended = sext(params[3], Type.I64)
            val gExtended = sext(params[5], Type.I64)
            val ab = add(params[0], bExtended)
            val abc = add(ab, params[2])
            val abcd = add(abc, dExtended)
            val abcde = add(abcd, params[4])
            ret(add(abcde, gExtended))
            finalizeFunction()
        }
        // 100 + 10 + 200 + 20 + 300 + 30 = 660
        assertEquals(660L, jitCall(module, "f", 100, 10, 200, 20, 300, 30))
    }
}
