package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

/**
 * Tests for ICmp + CondBr codegen patterns via JIT.
 *
 * Covers fused CmpBranch, unfused comparisons, chained comparisons,
 * signed vs unsigned, comparison-after-memory-load, and select.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitCodegenBranchTest {

    private fun jitCall(module: Module, functionName: String, vararg args: Long): Long {
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        return engine.call(functionName, *args)
    }

    private fun buildModule(name: String, block: ModuleBuilder.() -> Unit): Module {
        val builder = ModuleBuilder(name, Target.x86_64())
        builder.block()
        return builder.build()
    }

    // --- ICmp EQ ---

    @Test
    fun icmpEqTrueReturns1() {
        val module = buildModule("test") {
            createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = Parameter("a", Type.I32, 0)
            val cmp = icmp(ICmpPredicate.EQ, a, Constant.I32(0))
            val result = zext(cmp, Type.I32)
            ret(result)
            finalizeFunction()
        }
        assertEquals(1L, jitCall(module, "f", 0))
        assertEquals(0L, jitCall(module, "f", 1))
        assertEquals(0L, jitCall(module, "f", -1))
    }

    @Test
    fun icmpEqBranch() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = params[0]
            val cmp = icmp(ICmpPredicate.EQ, a, Constant.I32(0))
            condBr(cmp, "then", "else_")
            appendBlock("then")
            ret(Constant.I32(100))
            appendBlock("else_")
            ret(Constant.I32(200))
            finalizeFunction()
        }
        assertEquals(100L, jitCall(module, "f", 0), "0 == 0 should go to then")
        assertEquals(200L, jitCall(module, "f", 1), "1 != 0 should go to else")
        assertEquals(200L, jitCall(module, "f", 42), "42 != 0 should go to else")
    }

    @Test
    fun icmpNeBranch() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.NE, params[0], Constant.I32(0))
            condBr(cmp, "then", "else_")
            appendBlock("then")
            ret(Constant.I32(100))
            appendBlock("else_")
            ret(Constant.I32(200))
            finalizeFunction()
        }
        assertEquals(200L, jitCall(module, "f", 0), "0 == 0 so NE is false → else")
        assertEquals(100L, jitCall(module, "f", 1), "1 != 0 so NE is true → then")
    }

    // --- Chained comparison: ICmp EQ → ZExt → ICmp NE → CondBr ---

    @Test
    fun chainedComparisonEqZextNeBranch() {
        // icmp eq 0 → zext to i32 → icmp ne 0 → condBr
        val module = buildModule("test") {
            val params = createFunction("guard", listOf(Param("val", Type.I32)), Type.I32)
            appendBlock("entry")
            val value = params[0]
            val isZero = icmp(ICmpPredicate.EQ, value, Constant.I32(0))
            val extended = zext(isZero, Type.I32)
            val check = icmp(ICmpPredicate.NE, extended, Constant.I32(0))
            condBr(check, "initialized", "trap")
            appendBlock("initialized")
            ret(Constant.I32(1))
            appendBlock("trap")
            ret(Constant.I32(0))
            finalizeFunction()
        }
        // When value=0: isZero=true, extended=1, check=true(1!=0) → initialized → return 1
        assertEquals(1L, jitCall(module, "guard", 0), "value=0 should go to initialized")
        // When value=1: isZero=false, extended=0, check=false(0==0) → trap → return 0
        assertEquals(0L, jitCall(module, "guard", 1), "value=1 should go to trap")
        assertEquals(0L, jitCall(module, "guard", 99), "value=99 should go to trap")
    }

    // --- Signed vs unsigned comparisons ---

    @Test
    fun icmpSltVsUlt() {
        val module = buildModule("test") {
            val params = createFunction("slt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SLT, params[0], params[1])
            ret(zext(cmp, Type.I32))
            finalizeFunction()

            val params2 = createFunction("ult", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp2 = icmp(ICmpPredicate.ULT, params2[0], params2[1])
            ret(zext(cmp2, Type.I32))
            finalizeFunction()
        }
        // -1 as i32 = 0xFFFFFFFF
        // Signed: -1 < 0 → true
        assertEquals(1L, jitCall(module, "slt", -1, 0), "signed: -1 < 0")
        // Unsigned: 0xFFFFFFFF > 0 → false
        assertEquals(0L, jitCall(module, "ult", -1, 0), "unsigned: 0xFFFFFFFF > 0")
    }

    // --- I64 comparisons ---

    @Test
    fun icmpEqI64() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.EQ, params[0], params[1])
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        }
        assertEquals(1L, jitCall(module, "f", 0, 0))
        assertEquals(0L, jitCall(module, "f", 0, 1))
        assertEquals(1L, jitCall(module, "f", Long.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test
    fun icmpEqI64Branch() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I64)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.EQ, params[0], Constant.I64(0))
            condBr(cmp, "yes", "no")
            appendBlock("yes")
            ret(Constant.I32(1))
            appendBlock("no")
            ret(Constant.I32(0))
            finalizeFunction()
        }
        assertEquals(1L, jitCall(module, "f", 0))
        assertEquals(0L, jitCall(module, "f", 1))
        assertEquals(0L, jitCall(module, "f", -1))
    }

    // --- Multiple comparisons in sequence (flags clobbering) ---

    @Test
    fun twoComparesInSequence() {
        // Two icmp+condBr in the same function — tests that flags from the first
        // don't leak into the second
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp1 = icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
            condBr(cmp1, "check_b", "fail")
            appendBlock("check_b")
            val cmp2 = icmp(ICmpPredicate.EQ, params[1], Constant.I32(0))
            condBr(cmp2, "pass", "fail")
            appendBlock("pass")
            ret(Constant.I32(1))
            appendBlock("fail")
            ret(Constant.I32(0))
            finalizeFunction()
        }
        assertEquals(1L, jitCall(module, "f", 0, 0), "both zero → pass")
        assertEquals(0L, jitCall(module, "f", 1, 0), "a nonzero → fail")
        assertEquals(0L, jitCall(module, "f", 0, 1), "b nonzero → fail")
        assertEquals(0L, jitCall(module, "f", 1, 1), "both nonzero → fail")
    }

    // --- Comparison with memory load (DOOM pattern) ---

    @Test
    fun compareAfterMemoryLoad() {
        // Load from memory via pointer, compare result, branch
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val loaded = load(Type.I32, params[0])
            val cmp = icmp(ICmpPredicate.EQ, loaded, Constant.I32(0))
            condBr(cmp, "zero", "nonzero")
            appendBlock("zero")
            ret(Constant.I32(10))
            appendBlock("nonzero")
            ret(Constant.I32(20))
            finalizeFunction()
        }

        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)

        // Allocate native memory, write 0, call function with pointer
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(8, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, 0)

        val result = engine.call("f", mem.address())
        assertEquals(10L, result, "memory value 0 → zero branch")

        // Write non-zero
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, 42)
        val result2 = engine.call("f", mem.address())
        assertEquals(20L, result2, "memory value 42 → nonzero branch")

        arena.close()
    }

    // --- Select instruction ---

    // --- Select ---

    @Test
    fun selectI64() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("cond", Type.I32), Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val condBool = icmp(ICmpPredicate.NE, params[0], Constant.I32(0))
            ret(select(condBool, params[1], params[2]))
            finalizeFunction()
        }
        assertEquals(100L, jitCall(module, "f", 1, 100, 200))
        assertEquals(200L, jitCall(module, "f", 0, 100, 200))
    }

    @Test
    fun selectF64() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("cond", Type.I32), Param("ptr", Type.I64)), Type.I64)
            appendBlock("entry")
            val a = load(Type.F64, params[1])
            val b = load(Type.F64, add(params[1], Constant.I64(8)))
            val condBool = icmp(ICmpPredicate.NE, params[0], Constant.I32(0))
            val selected = select(condBool, a, b)
            ret(bitcast(selected, Type.I64))
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(16, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 3.14)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 8, 2.71)
        val r1 = java.lang.Double.longBitsToDouble(engine.call("f", 1, mem.address()))
        assertEquals(3.14, r1, 0.001)
        val r2 = java.lang.Double.longBitsToDouble(engine.call("f", 0, mem.address()))
        assertEquals(2.71, r2, 0.001)
        arena.close()
    }

    @Test
    fun selectI32() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("cond", Type.I32), Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val condBool = icmp(ICmpPredicate.NE, params[0], Constant.I32(0))
            val result = select(condBool, params[1], params[2])
            ret(result)
            finalizeFunction()
        }
        assertEquals(10L, jitCall(module, "f", 1, 10, 20), "cond=1 → select a")
        assertEquals(20L, jitCall(module, "f", 0, 10, 20), "cond=0 → select b")
    }
}
