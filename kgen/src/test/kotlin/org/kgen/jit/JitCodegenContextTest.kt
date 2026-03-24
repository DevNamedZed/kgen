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
 * JIT tests for RuntimeContext pointer indirection pattern.
 *
 * DOOM's WASM functions receive a context pointer (I64) as the first parameter.
 * The context is a struct with memory_base at offset 0, memory_size at offset 8.
 * Every memory access loads the base from the context first:
 *   base = load i64, [context + 0]
 *   addr = add base, wasmOffset
 *   value = load i32, [addr]
 *
 * This tests the EXACT codegen pattern that DOOM's func_817 uses.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitCodegenContextTest {

    private fun buildModule(block: ModuleBuilder.() -> Unit): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        builder.block()
        return builder.build()
    }

    private fun createContext(memoryBase: Long, memorySize: Long): Pair<java.lang.foreign.Arena, java.lang.foreign.MemorySegment> {
        val arena = java.lang.foreign.Arena.ofShared()
        val ctx = arena.allocate(40, 8) // RuntimeContext: 5 * i64
        ctx.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, memoryBase)  // memory_base
        ctx.set(java.lang.foreign.ValueLayout.JAVA_LONG, 8, memorySize)  // memory_size
        return arena to ctx
    }

    // --- Basic context indirection ---

    @Test
    fun loadMemoryBaseFromContext() {
        // Load [context+0] → use as pointer → load I32 from it
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ctx", Type.I64)), Type.I32)
            appendBlock("entry")
            val base = load(Type.I64, params[0]) // load memory_base from context+0
            ret(load(Type.I32, base))             // load I32 from memory_base
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val memArena = java.lang.foreign.Arena.ofShared()
        val mem = memArena.allocate(256, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, 42)

        val (ctxArena, ctx) = createContext(mem.address(), 256)
        assertEquals(42L, engine.call("f", ctx.address()))
        ctxArena.close()
        memArena.close()
    }

    @Test
    fun loadFromContextWithOffset() {
        // base = load [ctx+0]; addr = add base, zext(wasmAddr); load [addr]
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ctx", Type.I64), Param("wasmAddr", Type.I32)), Type.I32)
            appendBlock("entry")
            val base = load(Type.I64, params[0])
            val ext = zext(params[1], Type.I64)
            val addr = add(base, ext)
            ret(load(Type.I32, addr))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val memArena = java.lang.foreign.Arena.ofShared()
        val mem = memArena.allocate(1024, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 77)

        val (ctxArena, ctx) = createContext(mem.address(), 1024)
        assertEquals(77L, engine.call("f", ctx.address(), 100))
        ctxArena.close()
        memArena.close()
    }

    // --- Global + context + memory (func_817 EXACT pattern) ---

    @Test
    fun globalOffsetContextMemoryLoad() {
        // global_offset → zext → add(context.memBase, offset) → load → compare → branch
        val module = buildModule {
            addGlobal("offset", Type.I32, Constant.I32(100), isConstant = true, linkage = Linkage.INTERNAL)

            val params = createFunction("check", listOf(Param("ctx", Type.I64)), Type.I32)
            appendBlock("entry")
            val offset = load(Type.I32, GlobalRef("offset", Type.I32))
            val ext = zext(offset, Type.I64)
            val base = load(Type.I64, params[0]) // load memory_base from context
            val addr = add(base, ext)
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

        val memArena = java.lang.foreign.Arena.ofShared()
        val mem = memArena.allocate(1024, 8)
        val (ctxArena, ctx) = createContext(mem.address(), 1024)

        // Memory zeroed → value at offset 100 is 0 → return 1
        for (i in 0 until 10) {
            assertEquals(1L, engine.call("check", ctx.address()), "call $i: zero → 1")
        }

        // Write non-zero → return 0
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 42)
        for (i in 0 until 10) {
            assertEquals(0L, engine.call("check", ctx.address()), "call $i: non-zero → 0")
        }

        ctxArena.close()
        memArena.close()
    }

    // --- Context indirection + store (func_817 guard set pattern) ---

    @Test
    fun contextLoadStoreGuard() {
        val module = buildModule {
            addGlobal("guard_off", Type.I32, Constant.I32(200), isConstant = true, linkage = Linkage.INTERNAL)

            val params = createFunction("init", listOf(Param("ctx", Type.I64)), Type.I32)
            appendBlock("entry")
            val offset = load(Type.I32, GlobalRef("guard_off", Type.I32))
            val ext = zext(offset, Type.I64)
            val base = load(Type.I64, params[0])
            val addr = add(base, ext)
            val guardVal = load(Type.I32, addr)
            val isZero = icmp(ICmpPredicate.EQ, guardVal, Constant.I32(0))
            val extended = zext(isZero, Type.I32)
            val check = icmp(ICmpPredicate.NE, extended, Constant.I32(0))
            condBr(check, "do_init", "already_done")

            appendBlock("do_init")
            store(Constant.I32(1), addr) // set guard
            ret(Constant.I32(1))

            appendBlock("already_done")
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val memArena = java.lang.foreign.Arena.ofShared()
        val mem = memArena.allocate(1024, 8)
        val (ctxArena, ctx) = createContext(mem.address(), 1024)

        assertEquals(1L, engine.call("init", ctx.address()), "First call: init")
        assertEquals(1, mem.get(java.lang.foreign.ValueLayout.JAVA_INT, 200), "Guard set")
        assertEquals(0L, engine.call("init", ctx.address()), "Second call: already done")

        ctxArena.close()
        memArena.close()
    }

    // --- Multiple context-indirected memory accesses ---

    @Test
    fun multipleLoadsFromSameContext() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ctx", Type.I64)), Type.I32)
            appendBlock("entry")
            val base = load(Type.I64, params[0])
            val v0 = load(Type.I32, base)
            val v1 = load(Type.I32, add(base, Constant.I64(4)))
            val v2 = load(Type.I32, add(base, Constant.I64(8)))
            ret(add(add(v0, v1), v2))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val memArena = java.lang.foreign.Arena.ofShared()
        val mem = memArena.allocate(256, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, 10)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 4, 20)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 8, 30)

        val (ctxArena, ctx) = createContext(mem.address(), 256)
        assertEquals(60L, engine.call("f", ctx.address()))
        ctxArena.close()
        memArena.close()
    }

    // --- Context base changes between calls (simulates memory.grow) ---

    @Test
    fun contextBaseChangesAfterCall() {
        // Caller loads base, calls a function that changes the context, loads base again
        val module = buildModule {
            // "mutateCtx" writes a new base address into the context
            val mutParams = createFunction("mutateCtx", listOf(Param("ctx", Type.I64), Param("newBase", Type.I64)), Type.Void)
            appendBlock("entry")
            store(mutParams[1], mutParams[0])
            ret()
            finalizeFunction()

            val params = createFunction("f", listOf(Param("ctx", Type.I64), Param("newBase", Type.I64)), Type.I32)
            appendBlock("entry")
            // First read: load base from context, read [base+0]
            val base1 = load(Type.I64, params[0])
            val val1 = load(Type.I32, base1)
            // Call mutateCtx which changes context's base pointer
            call("mutateCtx", listOf(params[0], params[1]), Type.Void)
            // Second read: reload base from context (should be new base), read [newBase+0]
            val base2 = load(Type.I64, params[0])
            val val2 = load(Type.I32, base2)
            ret(add(val1, val2))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val mem1 = arena.allocate(8, 8)
        val mem2 = arena.allocate(8, 8)
        mem1.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, 100)
        mem2.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, 200)

        val (ctxArena, ctx) = createContext(mem1.address(), 8)
        val result = engine.call("f", ctx.address(), mem2.address())
        // val1 = 100 (from mem1), val2 = 200 (from mem2 after context change)
        assertEquals(300L, result)

        ctxArena.close()
        arena.close()
    }

    // --- Context + add with I64 constant offset (DOOM large offsets) ---

    @Test
    fun contextWithLargeStaticOffset() {
        // base = load [ctx+0]; addr = add(base, zext(0)) + 0x4278c0; load [addr]
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ctx", Type.I64)), Type.I32)
            appendBlock("entry")
            val base = load(Type.I64, params[0])
            val addr = add(base, Constant.I64(0x4278c0))
            ret(load(Type.I32, addr))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val memArena = java.lang.foreign.Arena.ofShared()
        val mem = memArena.allocate(5 * 1024 * 1024, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 0x4278c0, 0xBEEF)

        val (ctxArena, ctx) = createContext(mem.address(), 5L * 1024 * 1024)
        assertEquals(0xBEEFL, engine.call("f", ctx.address()))
        ctxArena.close()
        memArena.close()
    }

    // --- Context in large module (500 functions) ---

    @Test
    fun contextInLargeModule() {
        val module = buildModule {
            addGlobal("g0", Type.I32, Constant.I32(4659040), isConstant = false, linkage = Linkage.INTERNAL)
            addGlobal("g1", Type.I32, Constant.I32(0), isConstant = true, linkage = Linkage.INTERNAL)

            for (idx in 0 until 200) {
                val p = createFunction("fn_$idx", listOf(Param("ctx", Type.I64), Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                ret(add(p[1], Constant.I32(idx)))
                finalizeFunction()
            }

            // func_817 pattern in a large module
            val params = createFunction("guard", listOf(Param("ctx", Type.I64)), Type.I32)
            appendBlock("entry")
            val offset = load(Type.I32, GlobalRef("g1", Type.I32))
            val ext = zext(offset, Type.I64)
            val ctxAdd = add(params[0], Constant.I64(0))
            val base = load(Type.I64, ctxAdd)
            val addr = add(base, add(ext, Constant.I64(100)))
            val value = load(Type.I32, addr)
            val isZero = icmp(ICmpPredicate.EQ, value, Constant.I32(0))
            condBr(isZero, "yes", "no")
            appendBlock("yes")
            ret(Constant.I32(1))
            appendBlock("no")
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val memArena = java.lang.foreign.Arena.ofShared()
        val mem = memArena.allocate(1024, 8)
        val (ctxArena, ctx) = createContext(mem.address(), 1024)

        for (i in 0 until 20) {
            assertEquals(1L, engine.call("guard", ctx.address()), "call $i")
        }

        ctxArena.close()
        memArena.close()
    }

    // --- Bounds check pattern: ICmp UGT + CondBr to trap ---

    @Test
    fun boundsCheckTrapsOnOob() {
        // Pattern: load from context, compute address, check bounds, access
        // When address is out of bounds, the function should return -1 (trap path)
        val module = buildModule {
            val params = createFunction("check", listOf(
                Param("ctx", Type.I64),
                Param("wasmAddr", Type.I32),
            ), Type.I32)
            appendBlock("entry")

            // endAddr = zext(wasmAddr) + 4
            val ext = zext(params[1], Type.I64)
            val endAddr = add(ext, Constant.I64(4))

            // memSize = load [ctx+8] (MEMORY_SIZE offset)
            val sizePtr = add(params[0], Constant.I64(8))
            val memSize = load(Type.I64, sizePtr)

            // oob = endAddr > memSize
            val oob = icmp(ICmpPredicate.UGT, endAddr, memSize)
            condBr(oob, "trap", "ok")

            appendBlock("trap")
            ret(Constant.I32(-1))

            appendBlock("ok")
            // Load memory base, compute effective address, read
            val base = load(Type.I64, params[0])
            val addr = add(base, ext)
            val value = load(Type.I32, addr)
            ret(value)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val memArena = java.lang.foreign.Arena.ofShared()
        val mem = memArena.allocate(1024, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 42)
        val (ctxArena, ctx) = createContext(mem.address(), 1024)

        // In-bounds: wasmAddr=100, endAddr=104, memSize=1024 → ok
        assertEquals(42L, engine.call("check", ctx.address(), 100))

        // Out-of-bounds: wasmAddr=1021, endAddr=1025, memSize=1024 → trap
        assertEquals(0xFFFFFFFFL, engine.call("check", ctx.address(), 1021))

        // Way out of bounds: wasmAddr=0x7FFFFFFF (huge I32), zext → 2GB
        assertEquals(0xFFFFFFFFL, engine.call("check", ctx.address(), 0x7FFFFFFF))

        ctxArena.close()
        memArena.close()
    }

    @Test
    fun boundsCheckWithLargeModule() {
        // Same bounds check pattern but in a module with 500 functions
        val module = buildModule {
            for (idx in 0 until 500) {
                val funcParams = createFunction("fn_$idx", listOf(Param("ctx", Type.I64), Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                ret(add(funcParams[1], Constant.I32(idx)))
                finalizeFunction()
            }

            val params = createFunction("check", listOf(
                Param("ctx", Type.I64),
                Param("wasmAddr", Type.I32),
            ), Type.I32)
            appendBlock("entry")
            val ext = zext(params[1], Type.I64)
            val endAddr = add(ext, Constant.I64(4))
            val sizePtr = add(params[0], Constant.I64(8))
            val memSize = load(Type.I64, sizePtr)
            val oob = icmp(ICmpPredicate.UGT, endAddr, memSize)
            condBr(oob, "trap", "ok")
            appendBlock("trap")
            ret(Constant.I32(-1))
            appendBlock("ok")
            val base = load(Type.I64, params[0])
            val addr = add(base, ext)
            val value = load(Type.I32, addr)
            ret(value)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val memArena = java.lang.foreign.Arena.ofShared()
        val mem = memArena.allocate(1024, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 42)
        val (ctxArena, ctx) = createContext(mem.address(), 1024)

        assertEquals(42L, engine.call("check", ctx.address(), 100))
        assertEquals(0xFFFFFFFFL, engine.call("check", ctx.address(), 1021))
        assertEquals(0xFFFFFFFFL, engine.call("check", ctx.address(), 0x7FFFFFFF))

        ctxArena.close()
        memArena.close()
    }

    // --- DOOM memory.grow pattern: param survives call in 500-function module ---

    @Test
    fun contextSurvivesCallInLargeModule() {
        val module = buildModule {
            // Generate 500 dummy functions to stress relocation offsets
            for (idx in 0 until 500) {
                val funcParams = createFunction("fn_$idx", listOf(Param("ctx", Type.I64), Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                ret(add(funcParams[1], Constant.I32(idx)))
                finalizeFunction()
            }

            // A callee that writes a new value into the context (simulates memory.grow)
            val mutParams = createFunction("growSim", listOf(Param("ctx", Type.I64), Param("newBase", Type.I64)), Type.I32)
            appendBlock("entry")
            store(mutParams[1], mutParams[0])
            ret(Constant.I32(72))
            finalizeFunction()

            // Main function: loads from context, calls growSim, loads from context again
            val mainParams = createFunction("main", listOf(Param("ctx", Type.I64), Param("newBase", Type.I64)), Type.I32)
            appendBlock("entry")
            // Load old base, read I32 from it
            val base1 = load(Type.I64, mainParams[0])
            val val1 = load(Type.I32, base1)
            // Call growSim which changes context's base pointer
            call("growSim", listOf(mainParams[0], mainParams[1]), Type.I32)
            // Reload base from context (should be newBase now)
            val base2 = load(Type.I64, mainParams[0])
            val val2 = load(Type.I32, base2)
            ret(add(val1, val2))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val mem1 = arena.allocate(8, 8)
        val mem2 = arena.allocate(8, 8)
        mem1.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, 100)
        mem2.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, 200)

        val (ctxArena, ctx) = createContext(mem1.address(), 8)
        val result = engine.call("main", ctx.address(), mem2.address())
        assertEquals(300L, result, "val1(100) + val2(200) = 300")

        ctxArena.close()
        arena.close()
    }
}
