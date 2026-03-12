package org.kgen.integration.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.jit.JitEngine
import org.kgen.jit.SymbolResolver
import org.kgen.runtime.*
import org.kgen.runtime.gc.*
import org.kgen.runtime.exec.*
import org.kgen.jit.X86StubGenerator
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * End-to-end integration tests: IR → x86 codegen → JIT load → native execution,
 * combined with the managed runtime (heap, GC, execution context, dispatch).
 */
@EnabledOnOs(OS.LINUX, OS.WINDOWS)
class JitRuntimeIntegrationTest {

    private val linker = Linker.nativeLinker()

    // ---- JIT + Runtime wiring ----

    @Test
    fun jitWithManagedRuntime() {
        DefaultManagedRuntime.create(8192).use { runtime ->
            JitEngine(X86CodeGenerator()).use { jit ->
                val module = buildAddModule()
                jit.addModule(module)

                val ctx = runtime.currentContext()
                ctx.pushFrame("main", 0)
                val result = jit.call("add", 10, 20)
                assertEquals(30L, result)
                ctx.popFrame()
            }
        }
    }

    @Test
    fun jitCallWithGCBetweenCalls() {
        DefaultManagedRuntime.create(8192).use { runtime ->
            JitEngine(X86CodeGenerator()).use { jit ->
                jit.addModule(buildAddModule())

                val layout = ObjectLayout("Box", 8, listOf(FieldDescriptor("value", 0, 8, false)), typeId = 1)
                runtime.typeRegistry().register(layout)

                val obj = runtime.heap().allocate(layout)
                runtime.heap().writeField(obj, 0, 100L)

                runtime.gc().addRootProvider { visitor -> visitor.visitRoot(obj) }

                // Call JIT'd function
                assertEquals(30L, jit.call("add", 10, 20))

                // GC
                runtime.gc().collect()

                // Data survives GC
                assertEquals(100L, runtime.heap().readField(obj, 0))

                // JIT'd function still works after GC
                assertEquals(50L, jit.call("add", 25, 25))
            }
        }
    }

    @Test
    fun dispatchResolvesJitSymbols() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            JitEngine(X86CodeGenerator()).use { jit ->
                jit.addModule(buildAddModule())
                jit.addModule(buildMulModule())

                val dispatch = runtime.dispatch() as BasicMethodDispatch
                val addSym = jit.lookup("add")!!
                val mulSym = jit.lookup("mul")!!

                dispatch.register("add", addSym.address)
                dispatch.register("mul", mulSym.address)

                assertEquals(addSym.address, dispatch.resolve("add"))
                assertEquals(mulSym.address, dispatch.resolve("mul"))
            }
        }
    }

    @Test
    fun vtableDispatchWithJitFunctions() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            JitEngine(X86CodeGenerator()).use { jit ->
                // Two "classes" with different implementations of slot 0
                jit.addModule(buildConstantFunction("typeA_method", 100))
                jit.addModule(buildConstantFunction("typeB_method", 200))

                val addrA = jit.lookup("typeA_method")!!.address
                val addrB = jit.lookup("typeB_method")!!.address

                val dispatch = runtime.dispatch() as BasicMethodDispatch
                dispatch.registerVTable(1, VTable(1, longArrayOf(addrA)))
                dispatch.registerVTable(2, VTable(2, longArrayOf(addrB)))

                // Virtual dispatch: call slot 0 for type 1
                val entryA = dispatch.virtualLookup(1, 0)
                val handleA = linker.downcallHandle(
                    MemorySegment.ofAddress(entryA),
                    FunctionDescriptor.of(JAVA_LONG)
                )
                assertEquals(100L, handleA.invoke() as Long)

                // Virtual dispatch: call slot 0 for type 2
                val entryB = dispatch.virtualLookup(2, 0)
                val handleB = linker.downcallHandle(
                    MemorySegment.ofAddress(entryB),
                    FunctionDescriptor.of(JAVA_LONG)
                )
                assertEquals(200L, handleB.invoke() as Long)
            }
        }
    }

    @Test
    fun executionContextTrackingAcrossJitCalls() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            JitEngine(X86CodeGenerator()).use { jit ->
                jit.addModule(buildAddModule())

                val ctx = runtime.currentContext()
                ctx.pushFrame("main", 0)
                assertEquals(1, ctx.depth())

                ctx.pushFrame("compute", 0x100)
                assertEquals(2, ctx.depth())

                val result = jit.call("add", 5, 7)
                assertEquals(12L, result)

                ctx.popFrame()
                assertEquals(1, ctx.depth())

                ctx.popFrame()
                assertEquals(0, ctx.depth())
            }
        }
    }

    @Test
    fun heapAllocationDuringJitExecution() {
        DefaultManagedRuntime.create(16384).use { runtime ->
            JitEngine(X86CodeGenerator()).use { jit ->
                jit.addModule(buildAddModule())

                val layout = ObjectLayout("Result", 8, listOf(FieldDescriptor("value", 0, 8, false)), typeId = 1)
                runtime.typeRegistry().register(layout)

                // Simulate: JIT calls compute, we allocate a result object
                val ctx = runtime.currentContext()
                ctx.pushFrame("main", 0)

                for (i in 0 until 10) {
                    val result = jit.call("add", i.toLong(), 100)
                    val obj = runtime.heap().allocate(layout)
                    runtime.heap().writeField(obj, 0, result)
                    assertEquals((i + 100).toLong(), runtime.heap().readField(obj, 0))
                }

                ctx.popFrame()
            }
        }
    }

    // ---- JIT callback with runtime ----

    @Test
    fun jitCallsKotlinCallbackWithRuntime() {
        DefaultManagedRuntime.create(8192).use { runtime ->
            JitEngine(X86CodeGenerator()).use { jit ->
                val layout = ObjectLayout("Counter", 8, listOf(FieldDescriptor("count", 0, 8, false)), typeId = 1)
                runtime.typeRegistry().register(layout)

                val counter = runtime.heap().allocate(layout)
                runtime.heap().writeField(counter, 0, 0L)

                // Create a callback that increments the counter
                val arena = Arena.ofShared()
                val callback = object {
                    fun increment(): Long {
                        val current = runtime.heap().readField(counter, 0)
                        runtime.heap().writeField(counter, 0, current + 1)
                        return current + 1
                    }
                }

                val target = MethodHandles.lookup().bind(
                    callback, "increment",
                    MethodType.methodType(Long::class.java)
                )
                val upcall = linker.upcallStub(target, FunctionDescriptor.of(JAVA_LONG), arena)

                jit.addResolver(SymbolResolver.map(mapOf("increment" to upcall.address())))

                // IR module that calls increment() 3 times and returns the last result
                val module = buildCallExtern3Times("increment")
                jit.addModule(module)

                val result = jit.call("test_increment")
                assertEquals(3L, result)
                assertEquals(3L, runtime.heap().readField(counter, 0))

                arena.close()
            }
        }
    }

    // ---- Stub generator + JIT ----

    @Test
    fun stubGeneratorTrampolineToJitFunction() {
        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addModule(buildConstantFunction("getVal", 77))
            val addr = jit.lookup("getVal")!!.address

            val gen = X86StubGenerator()
            val trampoline = gen.generateTrampoline(addr)

            val handle = linker.downcallHandle(
                MemorySegment.ofAddress(trampoline.address()),
                FunctionDescriptor.of(JAVA_LONG)
            )
            assertEquals(77L, handle.invoke() as Long)

            trampoline.close()
        }
    }

    @Test
    fun multipleModulesWithRuntime() {
        DefaultManagedRuntime.create(8192).use { runtime ->
            JitEngine(X86CodeGenerator()).use { jit ->
                jit.addModule(buildAddModule())
                jit.addModule(buildMulModule())
                jit.addModule(buildConstantFunction("getBase", 1000))

                val ctx = runtime.currentContext()
                ctx.pushFrame("main", 0)

                val base = jit.call("getBase")
                val sum = jit.call("add", base, 234)
                val product = jit.call("mul", sum, 2)

                assertEquals(1000L, base)
                assertEquals(1234L, sum)
                assertEquals(2468L, product)

                ctx.popFrame()
            }
        }
    }

    @Test
    fun safepointManagerWithJitThreads() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val safepoints = runtime.safepoints() as BasicSafepointManager
            val ctx = runtime.currentContext()

            safepoints.registerThread(ctx)

            // Simulate stop-the-world
            ctx.enterSafepoint()
            safepoints.requestStop()
            safepoints.waitForAllStopped() // should complete since ctx is at safepoint
            runtime.gc().collect()
            safepoints.resumeAll()
            ctx.leaveSafepoint()

            assertFalse(safepoints.isStopRequested())
        }
    }

    @Test
    fun moduleHotSwapWithRuntime() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            JitEngine(X86CodeGenerator()).use { jit ->
                val m1 = jit.addModule(buildConstantFunction("getValue", 1))
                assertEquals(1L, jit.call("getValue"))

                // Hot-swap: remove old, add new
                jit.removeModule(m1)
                jit.addModule(buildConstantFunction("getValue", 2))
                assertEquals(2L, jit.call("getValue"))
            }
        }
    }

    // ---- Stress tests ----

    @Test
    fun manyJitCalls() {
        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addModule(buildAddModule())
            for (i in 0L until 1000) {
                assertEquals(i + i, jit.call("add", i, i))
            }
        }
    }

    @Test
    fun manyHeapAllocationsWithGC() {
        DefaultManagedRuntime.create(65536).use { runtime ->
            val layout = ObjectLayout("Box", 8, listOf(FieldDescriptor("v", 0, 8, false)), typeId = 1)
            runtime.typeRegistry().register(layout)

            val roots = mutableListOf<Long>()
            runtime.gc().addRootProvider { visitor ->
                for (r in roots) visitor.visitRoot(r)
            }

            for (i in 0 until 100) {
                val obj = runtime.heap().allocate(layout)
                runtime.heap().writeField(obj, 0, i.toLong())
                roots.add(obj)
            }

            runtime.gc().collect()

            for (i in 0 until 100) {
                assertEquals(i.toLong(), runtime.heap().readField(roots[i], 0))
            }
        }
    }

    @Test
    fun deepCallStackTracking() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val ctx = runtime.currentContext()

            for (i in 0 until 200) {
                ctx.pushFrame("frame_$i", i.toLong())
            }
            assertEquals(200, ctx.depth())

            val names = mutableListOf<String>()
            ctx.walkStack { f -> names.add(f.functionName) }
            assertEquals("frame_199", names.first())
            assertEquals("frame_0", names.last())

            for (i in 0 until 200) ctx.popFrame()
            assertEquals(0, ctx.depth())
        }
    }

    // ---- IR building helpers ----

    private fun buildAddModule(): Module {
        val ir = IrBuilder("add_module", Target.x86_64())
        ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.add(Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1)))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildMulModule(): Module {
        val ir = IrBuilder("mul_module", Target.x86_64())
        ir.createFunction("mul", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.mul(Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1)))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildConstantFunction(name: String, value: Long): Module {
        val ir = IrBuilder("${name}_module", Target.x86_64())
        ir.createFunction(name, emptyList(), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I64(value))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildCallExtern3Times(externName: String): Module {
        val ir = IrBuilder("test_module", Target.x86_64())
        ir.declareFunction(externName, emptyList(), Type.I64)

        ir.createFunction("test_increment", emptyList(), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.call(externName, emptyList(), Type.I64)
        ir.call(externName, emptyList(), Type.I64)
        val result = ir.call(externName, emptyList(), Type.I64)
        ir.ret(result)
        ir.finalizeFunction()
        return ir.build()
    }
}
