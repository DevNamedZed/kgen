package org.kgen.codegen.alloc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.jit.JitEngine
import org.kgen.target.x86.codegen.X86CodeGenerator
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout

/**
 * Additional register allocator coverage: F64 param eviction, wide phi fan-in,
 * large memory offsets, and sub-word load/store patterns.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class SpillCoverageGapTest2 {

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
     * Two params: base pointer and scalePtr (pointer to F64).
     * 12 I64 live values force the loaded F64 out of XMM registers.
     * Then use the F64: load from memory, fmul with scale, fptosi, return.
     * scale=2.0, memory value=50.0 => 100.
     */
    @Test
    fun f64ParamEvicted() {
        val module = buildModule {
            val params = createFunction("f", listOf(
                Param("base", Type.I64), Param("scalePtr", Type.I64)
            ), Type.I64)
            appendBlock("entry")

            val liveValues = (0 until 12).map { index ->
                load(Type.I64, add(params[0], Constant.I64(index.toLong() * 8)))
            }

            val scale = load(Type.F64, params[1])
            val memoryValue = load(Type.F64, add(params[0], Constant.I64(96)))
            val product = fmul(memoryValue, scale)
            val productAsLong = fptosi(product, Type.I64)

            var liveSum = liveValues[0]
            for (index in 1 until liveValues.size) {
                liveSum = add(liveSum, liveValues[index])
            }

            val result = add(productAsLong, sub(liveSum, liveSum))
            ret(result)
            finalizeFunction()
        }

        val arena = Arena.ofShared()
        val memory = arena.allocate(128, 8)
        for (index in 0 until 12) {
            memory.set(ValueLayout.JAVA_LONG, index.toLong() * 8, (index + 1).toLong())
        }
        memory.set(ValueLayout.JAVA_DOUBLE, 96, 50.0)

        val scaleMemory = arena.allocate(8, 8)
        scaleMemory.set(ValueLayout.JAVA_DOUBLE, 0, 2.0)

        assertEquals(100L, jitCall(module, "f", memory.address(), scaleMemory.address()))
        arena.close()
    }

    /**
     * 8 entry blocks each set a different I32 constant, all branch to merge.
     * Merge block has a phi with 8 incoming edges. Uses condBr chain to select path.
     */
    @Test
    fun phiWith8IncomingEdges() {
        val constants = listOf(10, 20, 30, 40, 50, 60, 70, 80)
        val builder = ModuleBuilder("test", Target.x86_64())
        val params = builder.createFunction("f", listOf(Param("selector", Type.I32)), Type.I64)
        builder.appendBlock("entry")

        for (index in 0 until 7) {
            val matches = builder.icmp(ICmpPredicate.EQ, params[0], Constant.I32(index))
            builder.condBr(matches, "block_$index", "check_${index + 1}")
            builder.appendBlock("check_${index + 1}")
        }
        builder.br("block_7")

        val phiIncoming = mutableListOf<Pair<Value, BlockRef>>()
        for (index in 0 until 8) {
            builder.appendBlock("block_$index")
            phiIncoming.add(Constant.I64(constants[index].toLong()) to BlockRef("block_$index"))
            builder.br("merge")
        }

        builder.appendBlock("merge")
        val phiValue = builder.phi(Type.I64, phiIncoming)
        builder.ret(phiValue)
        builder.finalizeFunction()

        val module = builder.build()
        for (index in 0 until 8) {
            assertEquals(constants[index].toLong(), jitCall(module, "f", index.toLong()))
        }
    }

    /**
     * Load I32 from [base + 0x100000] (1MB offset). Verifies large constant offsets
     * are handled correctly by the register allocator and codegen.
     */
    @Test
    fun largeMemoryOffset() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("base", Type.I64)), Type.I64)
            appendBlock("entry")
            val address = add(params[0], Constant.I64(0x100000))
            val loaded = load(Type.I32, address)
            ret(sext(loaded, Type.I64))
            finalizeFunction()
        }

        val totalSize = 0x100000L + 4
        val arena = Arena.ofShared()
        val memory = arena.allocate(totalSize, 8)
        memory.set(ValueLayout.JAVA_INT, 0x100000L, 0x12345678)
        assertEquals(0x12345678L, jitCall(module, "f", memory.address()))
        arena.close()
    }

    /**
     * Store I16 via trunc+store, load via load I16 + zext.
     * Verifies zero-extension (0xFFFF loads as 0x0000FFFF, not sign-extended).
     * Also verifies store16 does not corrupt adjacent bytes.
     */
    @Test
    fun i32Load16UAndStore16() {
        val module = buildModule {
            val params = createFunction("f", listOf(
                Param("ptr", Type.I64), Param("value", Type.I64)
            ), Type.I64)
            appendBlock("entry")
            val valueAsI32 = trunc(params[1], Type.I32)
            val valueAsI16 = trunc(valueAsI32, Type.I16)
            store(valueAsI16, params[0])
            val loaded = load(Type.I16, params[0])
            val zeroExtended = zext(loaded, Type.I64)
            ret(zeroExtended)
            finalizeFunction()
        }

        val arena = Arena.ofShared()
        val memory = arena.allocate(8, 8)
        memory.set(ValueLayout.JAVA_LONG, 0, 0L)

        assertEquals(0x0000FFFFL, jitCall(module, "f", memory.address(), 0xFFFFL))

        val fullValue = memory.get(ValueLayout.JAVA_LONG, 0)
        assertEquals(0xFFFFL, fullValue, "store16 should not corrupt adjacent bytes")
        arena.close()
    }
}
