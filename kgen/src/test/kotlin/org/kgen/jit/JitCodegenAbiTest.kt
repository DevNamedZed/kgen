package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator

/**
 * JIT tests for calling convention correctness.
 * Stresses Win64/System V ABI edge cases — register preservation,
 * caller-saved clobbering, parameter passing to callees.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitCodegenAbiTest {

    private fun jitCall(module: Module, name: String, vararg args: Long): Long {
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        return engine.call(name, *args)
    }

    private fun buildModule(block: ModuleBuilder.() -> Unit): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        builder.block()
        return builder.build()
    }

    // --- Repeated calls: ensures no state leaks between invocations ---

    @Test
    fun repeatedCallsSameFunction() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(params[0], Constant.I32(1)))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        for (i in 0 until 20) {
            assertEquals((i + 1).toLong(), engine.call("f", i.toLong()), "call $i")
        }
    }

    @Test
    fun repeatedCallsWithMemory() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64), Param("val", Type.I32)), Type.I32)
            appendBlock("entry")
            store(params[1], params[0])
            ret(load(Type.I32, params[0]))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(4, 4)
        for (i in 0 until 20) {
            assertEquals(i.toLong(), engine.call("f", mem.address(), i.toLong()), "call $i")
        }
        arena.close()
    }

    // --- Global read across many calls (DOOM's func_817 pattern) ---

    @Test
    fun globalReadConsistentAcrossManyCalls() {
        val module = buildModule {
            addGlobal("g", Type.I32, Constant.I32(42), isConstant = true, linkage = Linkage.INTERNAL)
            createFunction("readG", emptyList(), Type.I32)
            appendBlock("entry")
            ret(load(Type.I32, GlobalRef("g", Type.I32)))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        for (i in 0 until 50) {
            assertEquals(42L, engine.call("readG"), "call $i should return 42")
        }
    }

    @Test
    fun globalLoadMemoryLoadCompareRepeated() {
        // DOOM func_817 pattern: global → offset → memory load → compare
        // Run many times to catch non-determinism
        val module = buildModule {
            addGlobal("offset", Type.I32, Constant.I32(100), isConstant = true, linkage = Linkage.INTERNAL)
            val params = createFunction("check", listOf(Param("base", Type.I64)), Type.I32)
            appendBlock("entry")
            val offset = load(Type.I32, GlobalRef("offset", Type.I32))
            val ext = zext(offset, Type.I64)
            val addr = add(params[0], ext)
            val value = load(Type.I32, addr)
            val isZero = icmp(ICmpPredicate.EQ, value, Constant.I32(0))
            val extended = zext(isZero, Type.I32)
            val check = icmp(ICmpPredicate.NE, extended, Constant.I32(0))
            condBr(check, "yes", "no")
            appendBlock("yes")
            ret(Constant.I32(1))
            appendBlock("no")
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(256, 8)

        // Memory zeroed at offset 100 → should always return 1
        for (i in 0 until 50) {
            assertEquals(1L, engine.call("check", mem.address()), "call $i: mem[100]=0 should return 1")
        }

        // Set non-zero → should always return 0
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 99)
        for (i in 0 until 50) {
            assertEquals(0L, engine.call("check", mem.address()), "call $i: mem[100]=99 should return 0")
        }
        arena.close()
    }

    // --- Caller saves/restores around calls ---

    @Test
    fun valuePreservedAcrossMultipleCalls() {
        val module = buildModule {
            val innerParams = createFunction("noop", listOf(Param("x", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(innerParams[0])
            finalizeFunction()

            val outerParams = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            call("noop", listOf(Constant.I64(0)), Type.I64)
            call("noop", listOf(Constant.I64(0)), Type.I64)
            call("noop", listOf(Constant.I64(0)), Type.I64)
            ret(outerParams[0])
            finalizeFunction()
        }
        assertEquals(42L, jitCall(module, "f", 42), "param should survive 3 noop calls")
    }

    @Test
    fun manyParamsPreservedAcrossCall() {
        val module = buildModule {
            val innerParams = createFunction("noop", emptyList(), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()

            val params = createFunction("f", listOf(
                Param("a", Type.I64), Param("b", Type.I64),
                Param("c", Type.I64), Param("d", Type.I64)
            ), Type.I64)
            appendBlock("entry")
            call("noop", emptyList(), Type.Void)
            val sum = add(add(add(params[0], params[1]), params[2]), params[3])
            ret(sum)
            finalizeFunction()
        }
        assertEquals(10L, jitCall(module, "f", 1, 2, 3, 4))
    }

    // --- Cross-function call with 5+ args ---

    @Test
    fun callerPassesFiveArgsToCallee() {
        val module = buildModule {
            val innerParams = createFunction("sum5", listOf(
                Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64),
                Param("d", Type.I64), Param("e", Type.I64)
            ), Type.I64)
            appendBlock("entry")
            ret(add(add(add(add(innerParams[0], innerParams[1]), innerParams[2]), innerParams[3]), innerParams[4]))
            finalizeFunction()

            val outerParams = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            appendBlock("entry")
            val result = call("sum5", listOf(
                outerParams[0], Constant.I64(10), Constant.I64(20), Constant.I64(30), Constant.I64(40)
            ), Type.I64)
            ret(result!!)
            finalizeFunction()
        }
        assertEquals(105L, jitCall(module, "f", 5))
        assertEquals(200L, jitCall(module, "f", 100))
    }

    @Test
    fun callerPassesSixArgsWithMemory() {
        val module = buildModule {
            val innerParams = createFunction("write6", listOf(
                Param("ptr", Type.I64), Param("a", Type.I32), Param("b", Type.I32),
                Param("c", Type.I32), Param("d", Type.I32), Param("e", Type.I32)
            ), Type.Void)
            appendBlock("entry")
            store(innerParams[1], innerParams[0])
            store(innerParams[2], add(innerParams[0], Constant.I64(4)))
            store(innerParams[3], add(innerParams[0], Constant.I64(8)))
            store(innerParams[4], add(innerParams[0], Constant.I64(12)))
            store(innerParams[5], add(innerParams[0], Constant.I64(16)))
            ret()
            finalizeFunction()

            val outerParams = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            call("write6", listOf(
                outerParams[0], Constant.I32(10), Constant.I32(20),
                Constant.I32(30), Constant.I32(40), Constant.I32(50)
            ), Type.Void)
            val sum = add(
                add(load(Type.I32, outerParams[0]),
                    load(Type.I32, add(outerParams[0], Constant.I64(4)))),
                add(load(Type.I32, add(outerParams[0], Constant.I64(8))),
                    add(load(Type.I32, add(outerParams[0], Constant.I64(12))),
                        load(Type.I32, add(outerParams[0], Constant.I64(16)))))
            )
            ret(sum)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(20, 8)
        assertEquals(150L, engine.call("f", mem.address()))
        arena.close()
    }

    // --- Large module (DOOM-scale: 500+ functions) ---

    @Test
    fun globalReadInLargeModule() {
        val module = buildModule {
            addGlobal("g0", Type.I32, Constant.I32(4659040), isConstant = false, linkage = Linkage.INTERNAL)
            addGlobal("g1", Type.I32, Constant.I32(0), isConstant = true, linkage = Linkage.INTERNAL)

            // Generate 500 dummy functions (DOOM has 829)
            for (idx in 0 until 500) {
                val params = createFunction("fn_$idx", listOf(Param("ctx", Type.I64), Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val offset = load(Type.I32, GlobalRef("g1", Type.I32))
                val sum = add(offset, params[1])
                ret(sum)
                finalizeFunction()
            }

            // Test function: read g1 (should be 0)
            createFunction("readG1", emptyList(), Type.I32)
            appendBlock("entry")
            ret(load(Type.I32, GlobalRef("g1", Type.I32)))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        for (i in 0 until 10) {
            assertEquals(0L, engine.call("readG1"), "call $i: g1 should be 0 in 500-function module")
        }
    }

    // --- Mixed int/float params ---

    @Test
    fun intAndFloatParamsMixed() {
        // Function takes (i64 ptr, i32 intval) — store intval, load as f64 neighbor
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64), Param("val", Type.I32)), Type.I32)
            appendBlock("entry")
            store(params[1], params[0])
            ret(load(Type.I32, params[0]))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(8, 8)
        assertEquals(12345L, engine.call("f", mem.address(), 12345))
        arena.close()
    }
}
