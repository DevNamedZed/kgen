package org.kgen.integration.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.jit.FfmNativeLoader
import org.kgen.jit.JitEngine
import org.kgen.jit.SymbolResolver
import org.kgen.pass.OptLevel
import org.kgen.target.x86.codegen.X86CodeGenerator
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * JIT end-to-end: IR → compile → JIT load → execute → verify result.
 * Tests the full pipeline from IR construction through native execution.
 */
@EnabledOnOs(OS.LINUX, OS.WINDOWS)
class JitEndToEndTest {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private val loader = FfmNativeLoader()
    private val linker = Linker.nativeLinker()

    private fun generateObjectFile(block: IrBuilder.() -> Unit): org.kgen.binary.ObjectFile {
        val ir = IrBuilder("jit_e2e", Target.x86_64())
        if (isWindows) ir.targetTriple = "x86_64-unknown-windows-msvc"
        ir.block()
        return X86CodeGenerator().generateObjectFile(ir.build())
    }

    // -- Basic execution --

    @Test
    fun irToJitArithmetic() {
        JitEngine(X86CodeGenerator()).use { jit ->
            val ir = IrBuilder("add_mod", Target.x86_64())
            val p = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(ir.add(p[0], p[1]))
            ir.finalizeFunction()

            jit.addModule(ir.build())
            assertEquals(30L, jit.call("add", 10, 20))
            assertEquals(0L, jit.call("add", -5, 5))
            assertEquals(-10L, jit.call("add", -3, -7))
        }
    }

    @Test
    fun irToJitBranching() {
        JitEngine(X86CodeGenerator()).use { jit ->
            val ir = IrBuilder("max_mod", Target.x86_64())
            val p = ir.createFunction("max", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val cond = ir.icmp(ICmpPredicate.SGT, p[0], p[1])
            ir.condBr(cond, "ret_a", "ret_b")

            ir.positionAtEnd(ir.appendBlock("ret_a"))
            ir.ret(p[0])
            ir.positionAtEnd(ir.appendBlock("ret_b"))
            ir.ret(p[1])
            ir.finalizeFunction()

            jit.addModule(ir.build())
            assertEquals(10L, jit.call("max", 10, 5))
            assertEquals(10L, jit.call("max", 5, 10))
            assertEquals(0L, jit.call("max", 0, 0))
        }
    }

    @Test
    fun irToJitMultipleFunctions() {
        JitEngine(X86CodeGenerator()).use { jit ->
            val ir = IrBuilder("multi_mod", Target.x86_64())

            val addP = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(ir.add(addP[0], addP[1]))
            ir.finalizeFunction()

            val mulP = ir.createFunction("mul", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(ir.mul(mulP[0], mulP[1]))
            ir.finalizeFunction()

            jit.addModule(ir.build())

            // Chain: (3 + 4) * 5 = 35
            val sum = jit.call("add", 3, 4)
            val product = jit.call("mul", sum, 5)
            assertEquals(35L, product)
        }
    }

    // -- Optimized execution --

    @Test
    fun optimizedIrToJitExecution() {
        JitEngine(X86CodeGenerator()).use { jit ->
            val ir = IrBuilder("opt_mod", Target.x86_64())
            val p = ir.createFunction("compute", listOf(Param("x", Type.I64)), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))

            // x + 0 should be folded to just x
            val r1 = ir.add(p[0], Constant.I64(0))
            // x * 2
            val r2 = ir.add(r1, r1)
            ir.ret(r2)
            ir.finalizeFunction()

            val module = ir.build()
            val optimized = OptLevel.O2.pipeline().execute(module)
            jit.addModule(optimized)

            assertEquals(10L, jit.call("compute", 5))
            assertEquals(20L, jit.call("compute", 10))
            assertEquals(0L, jit.call("compute", 0))
        }
    }

    // -- Loader-based execution --

    @Test
    fun loaderExecutesCompiledObjectFile() {
        val obj = generateObjectFile {
            val p = createFunction("square", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(mul(p[0], p[0]))
            finalizeFunction()
        }

        loader.loadObjectFile(obj).use { module ->
            val fn = module.findFunction("square")!!
            assertEquals(25L, fn.callLong(5))
            assertEquals(0L, fn.callLong(0))
            assertEquals(100L, fn.callLong(10))
            assertEquals(1L, fn.callLong(-1))
        }
    }

    @Test
    fun loaderMultipleFunctions() {
        val obj = generateObjectFile {
            val addP = createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(add(addP[0], addP[1]))
            finalizeFunction()

            val negP = createFunction("negate", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(sub(Constant.I64(0), negP[0]))
            finalizeFunction()
        }

        loader.loadObjectFile(obj).use { module ->
            val addFn = module.findFunction("add")!!
            val negFn = module.findFunction("negate")!!

            assertEquals(7L, addFn.callLong(3, 4))
            assertEquals(-5L, negFn.callLong(5))

            // Compose: negate(add(10, 20)) should be -30
            val sum = addFn.callLong(10, 20)
            assertEquals(-30L, negFn.callLong(sum))
        }
    }

    // -- JIT with callbacks --

    @Test
    fun jitCallsKotlinCallback() {
        JitEngine(X86CodeGenerator()).use { jit ->
            val arena = Arena.ofShared()
            var callCount = 0L

            val callback = object {
                fun tick(): Long {
                    callCount++
                    return callCount
                }
            }

            val target = MethodHandles.lookup().bind(
                callback, "tick", MethodType.methodType(Long::class.java)
            )
            val upcall = linker.upcallStub(target, FunctionDescriptor.of(JAVA_LONG), arena)
            jit.addResolver(SymbolResolver.map(mapOf("tick" to upcall.address())))

            val ir = IrBuilder("callback_mod", Target.x86_64())
            ir.declareFunction("tick", emptyList(), Type.I64)
            ir.createFunction("call_tick_twice", emptyList(), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.call("tick", emptyList(), Type.I64)
            val result = ir.call("tick", emptyList(), Type.I64)
            ir.ret(result)
            ir.finalizeFunction()

            jit.addModule(ir.build())
            val r = jit.call("call_tick_twice")
            assertEquals(2L, r)
            assertEquals(2L, callCount)

            arena.close()
        }
    }

    // -- Module hot-swap --

    @Test
    fun jitModuleHotSwap() {
        JitEngine(X86CodeGenerator()).use { jit ->
            // V1: returns 1
            val ir1 = IrBuilder("v1", Target.x86_64())
            ir1.createFunction("getValue", emptyList(), Type.I64)
            ir1.positionAtEnd(ir1.appendBlock("entry"))
            ir1.ret(Constant.I64(1))
            ir1.finalizeFunction()

            val m1 = jit.addModule(ir1.build())
            assertEquals(1L, jit.call("getValue"))

            jit.removeModule(m1)

            // V2: returns 2
            val ir2 = IrBuilder("v2", Target.x86_64())
            ir2.createFunction("getValue", emptyList(), Type.I64)
            ir2.positionAtEnd(ir2.appendBlock("entry"))
            ir2.ret(Constant.I64(2))
            ir2.finalizeFunction()

            jit.addModule(ir2.build())
            assertEquals(2L, jit.call("getValue"))
        }
    }

    // -- Stress --

    @Test
    fun jitStressTest() {
        JitEngine(X86CodeGenerator()).use { jit ->
            val ir = IrBuilder("stress", Target.x86_64())
            val p = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(ir.add(p[0], p[1]))
            ir.finalizeFunction()
            jit.addModule(ir.build())

            for (i in 0L until 500) {
                assertEquals(i * 2, jit.call("add", i, i))
            }
        }
    }
}
