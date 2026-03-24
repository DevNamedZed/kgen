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
 * JIT execution tests for control flow: phi nodes, loops, multi-block,
 * function calls with memory, and cross-function interactions.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitCodegenControlFlowTest {

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

    // --- Phi nodes ---

    @Test
    fun phiFromTwoPredecessors() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("cond", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.NE, params[0], Constant.I32(0))
            condBr(cmp, "then", "else_")
            appendBlock("then")
            br("merge")
            appendBlock("else_")
            br("merge")
            appendBlock("merge")
            val phi = phi(Type.I32, listOf(
                Constant.I32(10) to BlockRef("then"),
                Constant.I32(20) to BlockRef("else_"),
            ))
            ret(phi)
            finalizeFunction()
        }
        assertEquals(10L, jitCall(module, "f", 1), "cond=1 → then → phi=10")
        assertEquals(20L, jitCall(module, "f", 0), "cond=0 → else → phi=20")
    }

    @Test
    fun phiWithParameterValues() {
        val module = buildModule {
            val params = createFunction("f", listOf(
                Param("cond", Type.I32), Param("a", Type.I32), Param("b", Type.I32)
            ), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.NE, params[0], Constant.I32(0))
            condBr(cmp, "then", "else_")
            appendBlock("then")
            br("merge")
            appendBlock("else_")
            br("merge")
            appendBlock("merge")
            val phi = phi(Type.I32, listOf(
                params[1] to BlockRef("then"),
                params[2] to BlockRef("else_"),
            ))
            ret(phi)
            finalizeFunction()
        }
        assertEquals(42L, jitCall(module, "f", 1, 42, 99), "cond=1 → a=42")
        assertEquals(99L, jitCall(module, "f", 0, 42, 99), "cond=0 → b=99")
    }

    @Test
    fun diamondControlFlow() {
        // entry → if → then/else → merge → return
        val module = buildModule {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SGT, params[0], Constant.I32(10))
            condBr(cmp, "big", "small")
            appendBlock("big")
            val bigVal = add(params[0], Constant.I32(100))
            br("merge")
            appendBlock("small")
            val smallVal = sub(params[0], Constant.I32(1))
            br("merge")
            appendBlock("merge")
            val result = phi(Type.I32, listOf(
                bigVal to BlockRef("big"),
                smallVal to BlockRef("small"),
            ))
            ret(result)
            finalizeFunction()
        }
        assertEquals(120L, jitCall(module, "f", 20), "20 > 10 → big → 20+100=120")
        assertEquals(4L, jitCall(module, "f", 5), "5 <= 10 → small → 5-1=4")
        assertEquals(0L, jitCall(module, "f", 1), "1 <= 10 → small → 1-1=0")
    }

    // --- Multi-block with memory ---

    @Test
    fun loadCompareStoreBranch() {
        // Load from [ptr], if zero: store 1, return 1. Else return 0.
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val loaded = load(Type.I32, params[0])
            val isZero = icmp(ICmpPredicate.EQ, loaded, Constant.I32(0))
            condBr(isZero, "first_time", "already_set")
            appendBlock("first_time")
            store(Constant.I32(1), params[0])
            ret(Constant.I32(1))
            appendBlock("already_set")
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(4, 4)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, 0)

        assertEquals(1L, engine.call("f", mem.address()), "First call: value is 0 → store 1, return 1")
        assertEquals(0L, engine.call("f", mem.address()), "Second call: value is 1 → return 0")
        assertEquals(1, mem.get(java.lang.foreign.ValueLayout.JAVA_INT, 0), "Memory should be 1")
        arena.close()
    }

    // --- Cross-function calls ---

    @Test
    fun calleeModifiesMemory() {
        val module = buildModule {
            val storeParams = createFunction("store_val", listOf(Param("ptr", Type.I64), Param("val", Type.I32)), Type.Void)
            appendBlock("entry")
            store(storeParams[1], storeParams[0])
            ret()
            finalizeFunction()

            val mainParams = createFunction("main", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            call("store_val", listOf(mainParams[0], Constant.I32(42)), Type.Void)
            val result = load(Type.I32, mainParams[0])
            ret(result)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(4, 4)
        assertEquals(42L, engine.call("main", mem.address()))
        arena.close()
    }

    @Test
    fun returnFifthParam() {
        // Just return the 5th parameter — test stack arg reading
        val module = buildModule {
            val params = createFunction("f", listOf(
                Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64),
                Param("d", Type.I64), Param("e", Type.I64)
            ), Type.I64)
            appendBlock("entry")
            ret(params[4])
            finalizeFunction()
        }

        // Dump ASM to see how 5th param is loaded
        val dumpEngine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        dumpEngine.addModule(module)
        val asmFile = java.io.File("build/5param-asm.log")
        asmFile.writeText(dumpEngine.inspector().dumpAsm("f") ?: "no ASM")

        assertEquals(55L, jitCall(module, "f", 11, 22, 33, 44, 55))
    }

    @Test
    fun callWithManyParams() {
        val module = buildModule {
            val params = createFunction("add5", listOf(
                Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64),
                Param("d", Type.I64), Param("e", Type.I64)
            ), Type.I64)
            appendBlock("entry")
            val ab = add(params[0], params[1])
            val abc = add(ab, params[2])
            val abcd = add(abc, params[3])
            val abcde = add(abcd, params[4])
            ret(abcde)
            finalizeFunction()
        }
        assertEquals(15L, jitCall(module, "add5", 1, 2, 3, 4, 5))
    }

    @Test
    fun callPreservesCalleeSavedRegisters() {
        // Caller has a value in a callee-saved register, calls a function, uses value after
        val module = buildModule {
            val innerParams = createFunction("inner", listOf(Param("x", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(add(innerParams[0], Constant.I64(100)))
            finalizeFunction()

            val outerParams = createFunction("outer", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val callResult = call("inner", listOf(outerParams[0]), Type.I64)
            val sum = add(callResult!!, outerParams[1])
            ret(sum)
            finalizeFunction()
        }
        // outer(10, 20) → inner(10) returns 110 → 110 + 20 = 130
        assertEquals(130L, jitCall(module, "outer", 10, 20))
    }

    @Test
    fun callWithSixParams() {
        val module = buildModule {
            val params = createFunction("add6", listOf(
                Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64),
                Param("d", Type.I64), Param("e", Type.I64), Param("f", Type.I64)
            ), Type.I64)
            appendBlock("entry")
            val ab = add(params[0], params[1])
            val cd = add(params[2], params[3])
            val ef = add(params[4], params[5])
            val abcd = add(ab, cd)
            ret(add(abcd, ef))
            finalizeFunction()
        }
        assertEquals(21L, jitCall(module, "add6", 1, 2, 3, 4, 5, 6))
    }

    @Test
    fun callerPassesFiveArgs() {
        // Inner function takes 5 args (5th on stack), caller calls it
        val module = buildModule {
            val innerParams = createFunction("inner", listOf(
                Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64),
                Param("d", Type.I64), Param("e", Type.I64)
            ), Type.I64)
            appendBlock("entry")
            ret(add(add(add(add(innerParams[0], innerParams[1]), innerParams[2]), innerParams[3]), innerParams[4]))
            finalizeFunction()

            val outerParams = createFunction("outer", listOf(Param("x", Type.I64)), Type.I64)
            appendBlock("entry")
            val result = call("inner", listOf(
                outerParams[0], Constant.I64(10), Constant.I64(20), Constant.I64(30), Constant.I64(40)
            ), Type.I64)
            ret(result!!)
            finalizeFunction()
        }
        assertEquals(105L, jitCall(module, "outer", 5))
    }

    @Test
    fun nestedCallsPreserveValues() {
        // outer calls middle, middle calls inner, values preserved across
        val module = buildModule {
            val p1 = createFunction("inner", listOf(Param("x", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(add(p1[0], Constant.I64(1)))
            finalizeFunction()

            val p2 = createFunction("middle", listOf(Param("x", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = call("inner", listOf(p2[0]), Type.I64)
            ret(add(r!!, p2[0]))
            finalizeFunction()

            val p3 = createFunction("outer", listOf(Param("x", Type.I64)), Type.I64)
            appendBlock("entry")
            val r2 = call("middle", listOf(p3[0]), Type.I64)
            ret(add(r2!!, p3[0]))
            finalizeFunction()
        }
        // outer(10): middle(10) → inner(10)=11 → 11+10=21 → 21+10=31
        assertEquals(31L, jitCall(module, "outer", 10))
    }

    // --- Global + memory + branch combined (DOOM func_817 pattern) ---

    @Test
    fun globalLoadMemoryAccessBranch() {
        val module = buildModule {
            addGlobal("offset", Type.I32, Constant.I32(100), isConstant = true, linkage = Linkage.INTERNAL)

            val params = createFunction("check", listOf(Param("base", Type.I64)), Type.I32)
            appendBlock("entry")
            val offset = load(Type.I32, GlobalRef("offset", Type.I32))
            val extOffset = zext(offset, Type.I64)
            val addr = add(params[0], extOffset)
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
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(256, 8)

        // Memory zeroed — value at offset 100 is 0 → return 1
        assertEquals(1L, engine.call("check", mem.address()))

        // Write non-zero at offset 100 → return 0
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 42)
        assertEquals(0L, engine.call("check", mem.address()))
        arena.close()
    }

    @Test
    fun globalLoadMemoryAccessBranchThenStore() {
        // Full DOOM guard pattern: load global offset → load from memory → compare →
        // if zero: store 1, continue; if nonzero: trap path
        val module = buildModule {
            addGlobal("guard_offset", Type.I32, Constant.I32(200), isConstant = true, linkage = Linkage.INTERNAL)

            val params = createFunction("init", listOf(Param("base", Type.I64)), Type.I32)
            appendBlock("entry")
            val offset = load(Type.I32, GlobalRef("guard_offset", Type.I32))
            val extOffset = zext(offset, Type.I64)
            val addr = add(params[0], extOffset)
            val guardVal = load(Type.I32, addr)
            val isZero = icmp(ICmpPredicate.EQ, guardVal, Constant.I32(0))
            val extended = zext(isZero, Type.I32)
            val check = icmp(ICmpPredicate.NE, extended, Constant.I32(0))
            condBr(check, "do_init", "already_done")

            appendBlock("do_init")
            store(Constant.I32(1), addr)
            ret(Constant.I32(1))

            appendBlock("already_done")
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(256, 8)

        // First call: guard=0 → do_init → store 1, return 1
        assertEquals(1L, engine.call("init", mem.address()), "First call")
        assertEquals(1, mem.get(java.lang.foreign.ValueLayout.JAVA_INT, 200), "Guard set")

        // Second call: guard=1 → already_done → return 0
        assertEquals(0L, engine.call("init", mem.address()), "Second call")
        arena.close()
    }
}
