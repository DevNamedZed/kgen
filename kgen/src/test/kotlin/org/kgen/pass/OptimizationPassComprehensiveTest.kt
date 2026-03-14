package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class OptimizationPassComprehensiveTest {

    private val fold = ConstantFolding()
    private val dce = DeadCodeElimination()
    private val inliner = Inlining()
    private val mem2reg = Mem2Reg()
    private val gvn = GlobalValueNumbering()
    private val licm = LoopInvariantCodeMotion()
    private val jt = JumpThreading()
    private val instcombine = InstructionCombining()
    private val sroa = ScalarReplacementOfAggregates()

    private fun build(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return ir.build()
    }

    private fun retVal(module: Module, fnIndex: Int = 0): Ret {
        return module.functions[fnIndex].blocks.last().instructions.last() as Ret
    }

    private fun instCount(module: Module, fnIndex: Int = 0, blockIndex: Int = 0): Int {
        return module.functions[fnIndex].blocks[blockIndex].instructions.size
    }

    private fun blockCount(module: Module, fnIndex: Int = 0): Int {
        return module.functions[fnIndex].blocks.size
    }

    // Constant Folding: i32 arithmetic

    @Test
    fun `cf - folds i32 add`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(add(Constant.I32(3), Constant.I32(7)))
            finalizeFunction()
        })
        assertEquals(10, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i32 sub`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(sub(Constant.I32(100), Constant.I32(37)))
            finalizeFunction()
        })
        assertEquals(63, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i32 mul`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(mul(Constant.I32(13), Constant.I32(7)))
            finalizeFunction()
        })
        assertEquals(91, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i32 sdiv`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(sdiv(Constant.I32(99), Constant.I32(10)))
            finalizeFunction()
        })
        assertEquals(9, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i32 udiv`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(udiv(Constant.I32(255), Constant.I32(16)))
            finalizeFunction()
        })
        assertEquals(15, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i32 srem`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(srem(Constant.I32(17), Constant.I32(5)))
            finalizeFunction()
        })
        assertEquals(2, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i32 urem`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(urem(Constant.I32(17), Constant.I32(5)))
            finalizeFunction()
        })
        assertEquals(2, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - does not fold sdiv by zero`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(sdiv(Constant.I32(10), Constant.I32(0)))
            finalizeFunction()
        })
        assertTrue(m.functions[0].blocks[0].instructions.any { it is SDiv })
    }

    @Test
    fun `cf - does not fold udiv by zero`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(udiv(Constant.I32(10), Constant.I32(0)))
            finalizeFunction()
        })
        assertTrue(m.functions[0].blocks[0].instructions.any { it is UDiv })
    }

    @Test
    fun `cf - folds negative sdiv`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(sdiv(Constant.I32(-100), Constant.I32(7)))
            finalizeFunction()
        })
        assertEquals(-14, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds negative srem`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(srem(Constant.I32(-17), Constant.I32(5)))
            finalizeFunction()
        })
        assertEquals(-2, (retVal(m).value as Constant.I32).value)
    }

    // Constant Folding: i64 arithmetic

    @Test
    fun `cf - folds i64 add`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(add(Constant.I64(5000000000L), Constant.I64(3000000000L)))
            finalizeFunction()
        })
        assertEquals(8000000000L, (retVal(m).value as Constant.I64).value)
    }

    @Test
    fun `cf - folds i64 sub`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(sub(Constant.I64(9000000000L), Constant.I64(4000000000L)))
            finalizeFunction()
        })
        assertEquals(5000000000L, (retVal(m).value as Constant.I64).value)
    }

    @Test
    fun `cf - folds i64 mul`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(mul(Constant.I64(1000000L), Constant.I64(1000000L)))
            finalizeFunction()
        })
        assertEquals(1000000000000L, (retVal(m).value as Constant.I64).value)
    }

    @Test
    fun `cf - folds i64 sdiv`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(sdiv(Constant.I64(9000000000L), Constant.I64(3000000000L)))
            finalizeFunction()
        })
        assertEquals(3L, (retVal(m).value as Constant.I64).value)
    }

    @Test
    fun `cf - folds i64 srem`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(srem(Constant.I64(10000000003L), Constant.I64(10000000000L)))
            finalizeFunction()
        })
        assertEquals(3L, (retVal(m).value as Constant.I64).value)
    }

    // Constant Folding: comparisons

    @Test
    fun `cf - folds icmp eq true`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.EQ, Constant.I32(42), Constant.I32(42))
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        })
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds icmp eq false`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.EQ, Constant.I32(42), Constant.I32(43))
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        })
        assertEquals(0, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds icmp ne`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.NE, Constant.I32(1), Constant.I32(2))
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        })
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds icmp slt`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SLT, Constant.I32(-1), Constant.I32(0))
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        })
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds icmp sle`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SLE, Constant.I32(5), Constant.I32(5))
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        })
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds icmp sgt`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SGT, Constant.I32(10), Constant.I32(5))
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        })
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds icmp sge`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SGE, Constant.I32(5), Constant.I32(5))
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        })
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds icmp ult`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.ULT, Constant.I32(5), Constant.I32(10))
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        })
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds icmp ule`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.ULE, Constant.I32(10), Constant.I32(10))
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        })
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds icmp ugt`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.UGT, Constant.I32(10), Constant.I32(5))
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        })
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds icmp uge`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.UGE, Constant.I32(3), Constant.I32(3))
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        })
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    // Constant Folding: bitwise operations

    @Test
    fun `cf - folds i32 and`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(and(Constant.I32(0b1100), Constant.I32(0b1010)))
            finalizeFunction()
        })
        assertEquals(0b1000, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i32 or`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(or(Constant.I32(0b1100), Constant.I32(0b1010)))
            finalizeFunction()
        })
        assertEquals(0b1110, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i32 xor`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(xor(Constant.I32(0b1100), Constant.I32(0b1010)))
            finalizeFunction()
        })
        assertEquals(0b0110, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i64 and`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(and(Constant.I64(0xFFFF0000L), Constant.I64(0x0000FFFFL)))
            finalizeFunction()
        })
        assertEquals(0L, (retVal(m).value as Constant.I64).value)
    }

    @Test
    fun `cf - folds i64 or`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(or(Constant.I64(0xFF00L), Constant.I64(0x00FFL)))
            finalizeFunction()
        })
        assertEquals(0xFFFFL, (retVal(m).value as Constant.I64).value)
    }

    @Test
    fun `cf - folds i64 xor`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(xor(Constant.I64(0xFFL), Constant.I64(0xFFL)))
            finalizeFunction()
        })
        assertEquals(0L, (retVal(m).value as Constant.I64).value)
    }

    // Constant Folding: shifts

    @Test
    fun `cf - folds i32 shl`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(shl(Constant.I32(1), Constant.I32(8)))
            finalizeFunction()
        })
        assertEquals(256, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i32 lshr`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(lshr(Constant.I32(256), Constant.I32(4)))
            finalizeFunction()
        })
        assertEquals(16, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i32 ashr`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(ashr(Constant.I32(-16), Constant.I32(2)))
            finalizeFunction()
        })
        assertEquals(-4, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i64 shl`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(shl(Constant.I64(1L), Constant.I64(32)))
            finalizeFunction()
        })
        assertEquals(4294967296L, (retVal(m).value as Constant.I64).value)
    }

    @Test
    fun `cf - folds i64 lshr`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(lshr(Constant.I64(4294967296L), Constant.I64(16)))
            finalizeFunction()
        })
        assertEquals(65536L, (retVal(m).value as Constant.I64).value)
    }

    @Test
    fun `cf - folds i64 ashr`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(ashr(Constant.I64(-256L), Constant.I64(4)))
            finalizeFunction()
        })
        assertEquals(-16L, (retVal(m).value as Constant.I64).value)
    }

    // Constant Folding: type conversions

    @Test
    fun `cf - folds zext i32 to i64`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(zext(Constant.I32(42), Type.I64))
            finalizeFunction()
        })
        assertEquals(42L, (retVal(m).value as Constant.I64).value)
    }

    @Test
    fun `cf - folds sext i32 to i64`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(sext(Constant.I32(-5), Type.I64))
            finalizeFunction()
        })
        assertEquals(-5L, (retVal(m).value as Constant.I64).value)
    }

    @Test
    fun `cf - folds trunc i64 to i32`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(trunc(Constant.I64(42L), Type.I32))
            finalizeFunction()
        })
        assertEquals(42, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds zext i1 true to i32`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(zext(Constant.I1(true), Type.I32))
            finalizeFunction()
        })
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds sext i1 true to i32`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(sext(Constant.I1(true), Type.I32))
            finalizeFunction()
        })
        assertEquals(-1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds zext i8 to i32`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(zext(Constant.I8(200.toByte()), Type.I32))
            finalizeFunction()
        })
        assertEquals(200, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds sext i8 to i32`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(sext(Constant.I8((-10).toByte()), Type.I32))
            finalizeFunction()
        })
        assertEquals(-10, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds trunc i32 to i8`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I8)
            appendBlock("entry")
            ret(trunc(Constant.I32(255), Type.I8))
            finalizeFunction()
        })
        assertEquals((-1).toByte(), (retVal(m).value as Constant.I8).value)
    }

    // Constant Folding: floating point

    @Test
    fun `cf - folds f64 add`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            ret(fadd(Constant.F64(1.5), Constant.F64(2.25)))
            finalizeFunction()
        })
        assertEquals(3.75, (retVal(m).value as Constant.F64).value)
    }

    @Test
    fun `cf - folds f64 sub`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            ret(fsub(Constant.F64(10.0), Constant.F64(3.5)))
            finalizeFunction()
        })
        assertEquals(6.5, (retVal(m).value as Constant.F64).value)
    }

    @Test
    fun `cf - folds f64 mul`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            ret(fmul(Constant.F64(2.5), Constant.F64(4.0)))
            finalizeFunction()
        })
        assertEquals(10.0, (retVal(m).value as Constant.F64).value)
    }

    @Test
    fun `cf - folds f64 div`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            ret(fdiv(Constant.F64(10.0), Constant.F64(4.0)))
            finalizeFunction()
        })
        assertEquals(2.5, (retVal(m).value as Constant.F64).value)
    }

    @Test
    fun `cf - folds f64 neg`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            ret(fneg(Constant.F64(3.14)))
            finalizeFunction()
        })
        assertEquals(-3.14, (retVal(m).value as Constant.F64).value)
    }

    @Test
    fun `cf - folds i32 neg`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(neg(Constant.I32(99)))
            finalizeFunction()
        })
        assertEquals(-99, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds i64 neg`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(neg(Constant.I64(1000000000L)))
            finalizeFunction()
        })
        assertEquals(-1000000000L, (retVal(m).value as Constant.I64).value)
    }

    // Constant Folding: chained constants

    @Test
    fun `cf - folds chained add-mul-sub`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(3), Constant.I32(7))
            val b = mul(a, Constant.I32(5))
            val c = sub(b, Constant.I32(8))
            ret(c)
            finalizeFunction()
        })
        assertEquals(42, (retVal(m).value as Constant.I32).value)
        assertEquals(1, instCount(m))
    }

    @Test
    fun `cf - folds deeply chained constants`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(1), Constant.I32(1))
            val b = add(a, a)
            val c = mul(b, b)
            val d = sub(c, Constant.I32(1))
            ret(d)
            finalizeFunction()
        })
        assertEquals(15, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - preserves non-constant operands`() {
        val m = fold.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(params[0], Constant.I32(1)))
            finalizeFunction()
        })
        assertTrue(m.functions[0].blocks[0].instructions[0] is Add)
    }

    @Test
    fun `cf - does not fold calls`() {
        val m = fold.run(build {
            declareFunction("ext", listOf(Param("x", Type.I32)), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(call("ext", listOf(Constant.I32(42)), Type.I32)!!)
            finalizeFunction()
        })
        assertTrue(m.functions[1].blocks[0].instructions.any { it is Call })
    }

    // Dead Code Elimination

    @Test
    fun `dce - removes single unused instruction`() {
        val m = dce.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            add(Constant.I32(1), Constant.I32(2))
            ret(Constant.I32(0))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
    }

    @Test
    fun `dce - removes multiple unused instructions`() {
        val m = dce.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            add(Constant.I32(1), Constant.I32(2))
            mul(Constant.I32(3), Constant.I32(4))
            sub(Constant.I32(5), Constant.I32(6))
            ret(Constant.I32(0))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
    }

    @Test
    fun `dce - removes transitive dead chains`() {
        val m = dce.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(1), Constant.I32(2))
            val b = mul(a, Constant.I32(3))
            sub(b, Constant.I32(4))
            ret(Constant.I32(42))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
    }

    @Test
    fun `dce - preserves used values`() {
        val m = dce.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(params[0], Constant.I32(1))
            ret(a)
            finalizeFunction()
        })
        assertEquals(2, instCount(m))
    }

    @Test
    fun `dce - preserves calls with unused results`() {
        val m = dce.run(build {
            declareFunction("sideeffect", emptyList(), Type.I32)
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            call("sideeffect", emptyList(), Type.I32)
            ret(null)
            finalizeFunction()
        })
        assertTrue(m.functions[1].blocks[0].instructions.any { it is Call })
    }

    @Test
    fun `dce - preserves stores`() {
        val m = dce.run(build {
            val params = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            store(Constant.I32(42), params[0])
            ret(null)
            finalizeFunction()
        })
        assertTrue(m.functions[0].blocks[0].instructions.any { it is Store })
    }

    @Test
    fun `dce - preserves branch instructions`() {
        val m = dce.run(build {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(params[0], BlockRef("a"), BlockRef("b"))
            appendBlock("a")
            ret(Constant.I32(1))
            appendBlock("b")
            ret(Constant.I32(0))
            finalizeFunction()
        })
        assertTrue(m.functions[0].blocks[0].instructions.any { it is CondBr })
    }

    @Test
    fun `dce - removes unused arithmetic but keeps used`() {
        val m = dce.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val used = add(params[0], Constant.I32(1))
            mul(params[0], Constant.I32(2)) // dead
            ret(used)
            finalizeFunction()
        })
        assertEquals(2, instCount(m))
        assertTrue(m.functions[0].blocks[0].instructions[0] is Add)
    }

    @Test
    fun `dce - preserves external functions`() {
        val m = dce.run(build {
            declareFunction("ext", emptyList(), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(Constant.I32(0))
            finalizeFunction()
        })
        assertEquals(2, m.functions.size)
        assertTrue(m.functions[0].isExternal)
    }

    // Inlining

    @Test
    fun `inlining - inlines simple function`() {
        val m = inliner.run(build {
            val p = createFunction("addOne", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p[0], Constant.I32(1)))
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            ret(call("addOne", listOf(Constant.I32(5)), Type.I32)!!)
            finalizeFunction()
        })
        assertFalse(m.functions[1].blocks[0].instructions.any { it is Call })
    }

    @Test
    fun `inlining - does not inline recursive function`() {
        val m = inliner.run(build {
            val p = createFunction("rec", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(call("rec", listOf(p[0]), Type.I32)!!)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            ret(call("rec", listOf(Constant.I32(5)), Type.I32)!!)
            finalizeFunction()
        })
        assertTrue(m.functions[1].blocks[0].instructions.any { it is Call })
    }

    @Test
    fun `inlining - does not inline external functions`() {
        val m = inliner.run(build {
            declareFunction("ext", listOf(Param("x", Type.I32)), Type.I32)
            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            ret(call("ext", listOf(Constant.I32(5)), Type.I32)!!)
            finalizeFunction()
        })
        assertTrue(m.functions[1].blocks[0].instructions.any { it is Call })
    }

    @Test
    fun `inlining - respects size limit`() {
        val smallInliner = Inlining(maxInstructionCount = 2)
        val m = smallInliner.run(build {
            val p = createFunction("big", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(p[0], Constant.I32(1))
            val b = add(a, Constant.I32(2))
            val c = add(b, Constant.I32(3))
            ret(c)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            ret(call("big", listOf(Constant.I32(0)), Type.I32)!!)
            finalizeFunction()
        })
        assertTrue(m.functions[1].blocks[0].instructions.any { it is Call })
    }

    @Test
    fun `inlining - inlines multiple call sites`() {
        val m = inliner.run(build {
            val p = createFunction("inc", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p[0], Constant.I32(1)))
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val a = call("inc", listOf(Constant.I32(0)), Type.I32)!!
            val b = call("inc", listOf(a), Type.I32)!!
            ret(b)
            finalizeFunction()
        })
        val mainInsts = m.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertEquals(2, mainInsts.filterIsInstance<Add>().size)
    }

    @Test
    fun `inlining - inlines void function`() {
        val m = inliner.run(build {
            createFunction("noop", emptyList(), Type.Void)
            appendBlock("entry")
            ret(null)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            call("noop", emptyList(), Type.Void)
            ret(Constant.I32(0))
            finalizeFunction()
        })
        assertFalse(m.functions[1].blocks[0].instructions.any { it is Call })
    }

    @Test
    fun `inlining - preserves callee`() {
        val m = inliner.run(build {
            val p = createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(p[0])
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            ret(call("helper", listOf(Constant.I32(42)), Type.I32)!!)
            finalizeFunction()
        })
        assertEquals(2, m.functions.size)
    }

    @Test
    fun `inlining - substitutes arguments correctly`() {
        val m = inliner.run(build {
            val p = createFunction("double", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p[0], p[0]))
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            ret(call("double", listOf(Constant.I32(21)), Type.I32)!!)
            finalizeFunction()
        })
        val addInst = m.functions[1].blocks[0].instructions.filterIsInstance<Add>().first()
        assertEquals("21", addInst.lhs.name)
        assertEquals("21", addInst.rhs.name)
    }

    @Test
    fun `inlining - return value used in computation`() {
        val m = inliner.run(build {
            val p = createFunction("sq", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(mul(p[0], p[0]))
            finalizeFunction()

            val p2 = createFunction("main", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = call("sq", listOf(p2[0]), Type.I32)!!
            ret(add(r, Constant.I32(1)))
            finalizeFunction()
        })
        assertFalse(m.functions[1].blocks[0].instructions.any { it is Call })
        assertTrue(m.functions[1].blocks[0].instructions.any { it is Mul })
    }

    // Mem2Reg

    @Test
    fun `mem2reg - promotes simple store-load`() {
        val m = mem2reg.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr)
            ret(load(Type.I32, ptr))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertEquals(42, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `mem2reg - promotes parameter store-load`() {
        val m = mem2reg.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(p[0], ptr)
            ret(load(Type.I32, ptr))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `mem2reg - promotes multiple allocas`() {
        val m = mem2reg.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = alloca(Type.I32)
            val b = alloca(Type.I32)
            store(Constant.I32(10), a)
            store(Constant.I32(20), b)
            ret(add(load(Type.I32, a), load(Type.I32, b)))
            finalizeFunction()
        })
        assertEquals(2, instCount(m))
        val addInst = m.functions[0].blocks[0].instructions[0] as Add
        assertEquals(10, (addInst.lhs as Constant.I32).value)
        assertEquals(20, (addInst.rhs as Constant.I32).value)
    }

    @Test
    fun `mem2reg - last store wins in single block`() {
        val m = mem2reg.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(1), ptr)
            store(Constant.I32(2), ptr)
            store(Constant.I32(3), ptr)
            ret(load(Type.I32, ptr))
            finalizeFunction()
        })
        assertEquals(3, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `mem2reg - inserts phi for diamond control flow`() {
        val m = mem2reg.run(build {
            val p = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(0), ptr)
            condBr(p[0], BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            store(Constant.I32(10), ptr)
            br(BlockRef("merge"))

            appendBlock("else")
            store(Constant.I32(20), ptr)
            br(BlockRef("merge"))

            appendBlock("merge")
            ret(load(Type.I32, ptr))
            finalizeFunction()
        })
        val mergeBlock = m.functions[0].blocks[3]
        assertTrue(mergeBlock.instructions.any { it is Phi })
        assertFalse(mergeBlock.instructions.any { it is Load })
        val phi = mergeBlock.instructions.first { it is Phi } as Phi
        val values = phi.incoming.map { (v, _) -> (v as Constant.I32).value }.toSet()
        assertEquals(setOf(10, 20), values)
    }

    @Test
    fun `mem2reg - load before store gives zero`() {
        val m = mem2reg.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            ret(load(Type.I32, ptr))
            finalizeFunction()
        })
        assertEquals(0, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `mem2reg - does not promote address-escaped alloca`() {
        val m = mem2reg.run(build {
            declareFunction("use_ptr", listOf(Param("p", Type.Pointer(Type.I32))), Type.Void)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr)
            call("use_ptr", listOf(ptr), Type.Void)
            ret(load(Type.I32, ptr))
            finalizeFunction()
        })
        val insts = m.functions[1].blocks[0].instructions
        assertTrue(insts.any { it is Alloca })
        assertTrue(insts.any { it is Load })
    }

    @Test
    fun `mem2reg - does not promote volatile load`() {
        val m = mem2reg.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr)
            ret(load(Type.I32, ptr, volatile = true))
            finalizeFunction()
        })
        assertTrue(m.functions[0].blocks[0].instructions.any { it is Alloca })
    }

    @Test
    fun `mem2reg - promotes i64 alloca`() {
        val m = mem2reg.run(build {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val ptr = alloca(Type.I64)
            store(Constant.I64(999L), ptr)
            ret(load(Type.I64, ptr))
            finalizeFunction()
        })
        assertEquals(999L, (retVal(m).value as Constant.I64).value)
    }

    @Test
    fun `mem2reg - promotes f64 alloca`() {
        val m = mem2reg.run(build {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            val ptr = alloca(Type.F64)
            store(Constant.F64(3.14), ptr)
            ret(load(Type.F64, ptr))
            finalizeFunction()
        })
        assertEquals(3.14, (retVal(m).value as Constant.F64).value)
    }

    @Test
    fun `mem2reg - load used in computation`() {
        val m = mem2reg.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(10), ptr)
            ret(add(load(Type.I32, ptr), p[0]))
            finalizeFunction()
        })
        assertEquals(2, instCount(m))
        val addInst = m.functions[0].blocks[0].instructions[0] as Add
        assertEquals(10, (addInst.lhs as Constant.I32).value)
    }

    // GVN

    @Test
    fun `gvn - eliminates redundant add`() {
        val m = gvn.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(p[0], p[1])
            val b = add(p[0], p[1])
            ret(add(a, b))
            finalizeFunction()
        })
        // One add replaced with the other, so 3 -> 2 adds (a, a+a)
        val insts = m.functions[0].blocks[0].instructions
        val adds = insts.filterIsInstance<Add>()
        assertEquals(2, adds.size, "Should eliminate one redundant add: $insts")
    }

    @Test
    fun `gvn - eliminates redundant mul`() {
        val m = gvn.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = mul(p[0], p[1])
            val b = mul(p[0], p[1])
            ret(add(a, b))
            finalizeFunction()
        })
        val muls = m.functions[0].blocks[0].instructions.filterIsInstance<Mul>()
        assertEquals(1, muls.size)
    }

    @Test
    fun `gvn - eliminates redundant sub`() {
        val m = gvn.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = sub(p[0], p[1])
            val b = sub(p[0], p[1])
            ret(add(a, b))
            finalizeFunction()
        })
        val subs = m.functions[0].blocks[0].instructions.filterIsInstance<Sub>()
        assertEquals(1, subs.size)
    }

    @Test
    fun `gvn - does not eliminate different operations`() {
        val m = gvn.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(p[0], p[1])
            val b = sub(p[0], p[1])
            ret(add(a, b))
            finalizeFunction()
        })
        val insts = m.functions[0].blocks[0].instructions
        assertTrue(insts.any { it is Sub })
    }

    @Test
    fun `gvn - does not eliminate operations with different operands`() {
        val m = gvn.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(p[0], p[1])
            val b = add(p[1], p[0])
            ret(add(a, b))
            finalizeFunction()
        })
        // add(x,y) != add(y,x) for GVN (no commutativity)
        val adds = m.functions[0].blocks[0].instructions.filterIsInstance<Add>()
        assertEquals(3, adds.size)
    }

    @Test
    fun `gvn - eliminates redundant bitwise and`() {
        val m = gvn.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = and(p[0], p[1])
            val b = and(p[0], p[1])
            ret(or(a, b))
            finalizeFunction()
        })
        val ands = m.functions[0].blocks[0].instructions.filterIsInstance<And>()
        assertEquals(1, ands.size)
    }

    @Test
    fun `gvn - eliminates redundant comparison`() {
        val m = gvn.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = icmp(ICmpPredicate.SLT, p[0], p[1])
            val b = icmp(ICmpPredicate.SLT, p[0], p[1])
            val za = zext(a, Type.I32)
            val zb = zext(b, Type.I32)
            ret(add(za, zb))
            finalizeFunction()
        })
        val cmps = m.functions[0].blocks[0].instructions.filterIsInstance<ICmp>()
        assertEquals(1, cmps.size)
    }

    @Test
    fun `gvn - does not eliminate calls`() {
        val m = gvn.run(build {
            declareFunction("rand", emptyList(), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = call("rand", emptyList(), Type.I32)!!
            val b = call("rand", emptyList(), Type.I32)!!
            ret(add(a, b))
            finalizeFunction()
        })
        val calls = m.functions[1].blocks[0].instructions.filterIsInstance<Call>()
        assertEquals(2, calls.size)
    }

    @Test
    fun `gvn - eliminates redundant conversion`() {
        val m = gvn.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I64)
            appendBlock("entry")
            val a = zext(p[0], Type.I64)
            val b = zext(p[0], Type.I64)
            ret(add(a, b))
            finalizeFunction()
        })
        val exts = m.functions[0].blocks[0].instructions.filterIsInstance<ZExt>()
        assertEquals(1, exts.size)
    }

    // LICM

    @Test
    fun `licm - hoists loop-invariant add`() {
        val m = licm.run(build {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            br(BlockRef("header"))

            appendBlock("header")
            val i = phi(Type.I32, listOf(Constant.I32(0) to BlockRef("entry"), Constant.I32(0) to BlockRef("body")))
            val cond = icmp(ICmpPredicate.SLT, i, Constant.I32(10))
            condBr(cond, BlockRef("body"), BlockRef("exit"))

            appendBlock("body")
            val invariant = add(p[0], p[1]) // loop-invariant: both from params
            br(BlockRef("header"))

            appendBlock("exit")
            ret(Constant.I32(0))
            finalizeFunction()
        })
        // invariant add should be in the preheader, not in "body"
        val bodyBlock = m.functions[0].blocks.find { it.label == "body" }!!
        assertFalse(bodyBlock.instructions.any { it is Add },
            "Invariant add should be hoisted from body: ${bodyBlock.instructions}")
    }

    @Test
    fun `licm - does not hoist loop-varying computation`() {
        val m = licm.run(build {
            val p = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            br(BlockRef("header"))

            appendBlock("header")
            val i = phi(Type.I32, listOf(Constant.I32(0) to BlockRef("entry"), Constant.I32(0) to BlockRef("body")))
            val cond = icmp(ICmpPredicate.SLT, i, p[0])
            condBr(cond, BlockRef("body"), BlockRef("exit"))

            appendBlock("body")
            add(i, Constant.I32(1)) // not invariant: uses phi 'i'
            br(BlockRef("header"))

            appendBlock("exit")
            ret(Constant.I32(0))
            finalizeFunction()
        })
        val bodyBlock = m.functions[0].blocks.find { it.label == "body" }!!
        assertTrue(bodyBlock.instructions.any { it is Add },
            "Non-invariant add should stay in body")
    }

    @Test
    fun `licm - does not hoist stores`() {
        val m = licm.run(build {
            val p = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            br(BlockRef("header"))

            appendBlock("header")
            val i = phi(Type.I32, listOf(Constant.I32(0) to BlockRef("entry"), Constant.I32(0) to BlockRef("body")))
            val cond = icmp(ICmpPredicate.SLT, i, Constant.I32(10))
            condBr(cond, BlockRef("body"), BlockRef("exit"))

            appendBlock("body")
            store(Constant.I32(42), p[0])
            br(BlockRef("header"))

            appendBlock("exit")
            ret(null)
            finalizeFunction()
        })
        val bodyBlock = m.functions[0].blocks.find { it.label == "body" }!!
        assertTrue(bodyBlock.instructions.any { it is Store },
            "Stores should not be hoisted")
    }

    @Test
    fun `licm - does not hoist calls`() {
        val m = licm.run(build {
            declareFunction("ext", emptyList(), Type.I32)
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            br(BlockRef("header"))

            appendBlock("header")
            val i = phi(Type.I32, listOf(Constant.I32(0) to BlockRef("entry"), Constant.I32(0) to BlockRef("body")))
            val cond = icmp(ICmpPredicate.SLT, i, Constant.I32(10))
            condBr(cond, BlockRef("body"), BlockRef("exit"))

            appendBlock("body")
            call("ext", emptyList(), Type.I32)
            br(BlockRef("header"))

            appendBlock("exit")
            ret(null)
            finalizeFunction()
        })
        val bodyBlock = m.functions[1].blocks.find { it.label == "body" }!!
        assertTrue(bodyBlock.instructions.any { it is Call },
            "Calls should not be hoisted")
    }

    @Test
    fun `licm - hoists chained invariant computations`() {
        val m = licm.run(build {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            br(BlockRef("header"))

            appendBlock("header")
            val i = phi(Type.I32, listOf(Constant.I32(0) to BlockRef("entry"), Constant.I32(0) to BlockRef("body")))
            val cond = icmp(ICmpPredicate.SLT, i, Constant.I32(10))
            condBr(cond, BlockRef("body"), BlockRef("exit"))

            appendBlock("body")
            val x = add(p[0], p[1])
            mul(x, Constant.I32(2)) // also invariant: uses x (invariant) and constant
            br(BlockRef("header"))

            appendBlock("exit")
            ret(Constant.I32(0))
            finalizeFunction()
        })
        val bodyBlock = m.functions[0].blocks.find { it.label == "body" }!!
        assertFalse(bodyBlock.instructions.any { it is Add },
            "Invariant add should be hoisted")
        assertFalse(bodyBlock.instructions.any { it is Mul },
            "Invariant mul should be hoisted")
    }

    @Test
    fun `licm - no-op on function without loops`() {
        val m = licm.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p[0], Constant.I32(1)))
            finalizeFunction()
        })
        assertEquals(2, instCount(m))
    }

    // Jump Threading

    @Test
    fun `jt - folds constant true condition`() {
        val m = jt.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            ret(Constant.I32(1))

            appendBlock("else")
            ret(Constant.I32(0))
            finalizeFunction()
        })
        assertEquals(1, blockCount(m))
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `jt - folds constant false condition`() {
        val m = jt.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(false), BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            ret(Constant.I32(1))

            appendBlock("else")
            ret(Constant.I32(0))
            finalizeFunction()
        })
        assertEquals(1, blockCount(m))
        assertEquals(0, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `jt - removes unreachable blocks`() {
        val m = jt.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            br(BlockRef("live"))

            appendBlock("dead")
            ret(Constant.I32(-1))

            appendBlock("live")
            ret(Constant.I32(42))
            finalizeFunction()
        })
        assertFalse(m.functions[0].blocks.any { it.label == "dead" })
    }

    @Test
    fun `jt - merges single-successor single-predecessor blocks`() {
        val m = jt.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("a")
            br(BlockRef("b"))

            appendBlock("b")
            br(BlockRef("c"))

            appendBlock("c")
            ret(Constant.I32(42))
            finalizeFunction()
        })
        assertEquals(1, blockCount(m))
        assertEquals(42, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `jt - folds same-target condBr`() {
        val m = jt.run(build {
            val p = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(p[0], BlockRef("target"), BlockRef("target"))

            appendBlock("target")
            ret(Constant.I32(42))
            finalizeFunction()
        })
        // condBr with same targets should become br, then merge
        assertEquals(1, blockCount(m))
    }

    @Test
    fun `jt - preserves non-constant condBr`() {
        val m = jt.run(build {
            val p = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(p[0], BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            ret(Constant.I32(1))

            appendBlock("else")
            ret(Constant.I32(0))
            finalizeFunction()
        })
        assertEquals(3, blockCount(m))
    }

    @Test
    fun `jt - chains of unconditional branches collapse`() {
        val m = jt.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("a")
            br(BlockRef("b"))
            appendBlock("b")
            br(BlockRef("c"))
            appendBlock("c")
            br(BlockRef("d"))
            appendBlock("d")
            ret(Constant.I32(99))
            finalizeFunction()
        })
        assertEquals(1, blockCount(m))
        assertEquals(99, (retVal(m).value as Constant.I32).value)
    }

    // Instruction Combining

    @Test
    fun `ic - simplifies add zero right`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p[0], Constant.I32(0)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies add zero left`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(Constant.I32(0), p[0]))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies sub zero`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(sub(p[0], Constant.I32(0)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies sub self to zero`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(sub(p[0], p[0]))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertEquals(0, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `ic - simplifies mul one right`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(mul(p[0], Constant.I32(1)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies mul one left`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(mul(Constant.I32(1), p[0]))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies mul zero`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(mul(p[0], Constant.I32(0)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertEquals(0, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `ic - simplifies and zero`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(and(p[0], Constant.I32(0)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertEquals(0, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `ic - simplifies and all-ones`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(and(p[0], Constant.I32(-1)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies and self`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(and(p[0], p[0]))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies or zero`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(or(p[0], Constant.I32(0)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies or self`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(or(p[0], p[0]))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies xor zero`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(xor(p[0], Constant.I32(0)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies xor self to zero`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(xor(p[0], p[0]))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertEquals(0, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `ic - simplifies shl by zero`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(shl(p[0], Constant.I32(0)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies lshr by zero`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(lshr(p[0], Constant.I32(0)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies ashr by zero`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(ashr(p[0], Constant.I32(0)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies select true`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(select(Constant.I1(true), p[0], p[1]))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
        assertEquals("x", retVal(m).value!!.name)
    }

    @Test
    fun `ic - simplifies select false`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(select(Constant.I1(false), p[0], p[1]))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
        assertEquals("y", retVal(m).value!!.name)
    }

    @Test
    fun `ic - simplifies select same values`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("c", Type.I1), Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(select(p[0], p[1], p[1]))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies i64 add zero`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(add(p[0], Constant.I64(0L)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - simplifies i64 mul one`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(mul(p[0], Constant.I64(1L)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    @Test
    fun `ic - chain of identities collapses`() {
        val m = instcombine.run(build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(p[0], Constant.I32(0))
            val b = mul(a, Constant.I32(1))
            val c = sub(b, Constant.I32(0))
            ret(c)
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertTrue(retVal(m).value is Parameter)
    }

    // SROA

    @Test
    fun `sroa - decomposes struct alloca`() {
        val structType = Type.Struct(null, listOf(Type.I32, Type.I64))
        val m = sroa.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(structType)
            val field0Ptr = gep(structType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(42), field0Ptr)
            val v = load(Type.I32, field0Ptr)
            ret(v)
            finalizeFunction()
        })
        val insts = m.functions[0].blocks[0].instructions
        // The struct alloca should be decomposed into scalar allocas
        val allocas = insts.filterIsInstance<Alloca>()
        assertTrue(allocas.all { !isAggregate(it.allocType) },
            "All remaining allocas should be scalar: $allocas")
    }

    @Test
    fun `sroa - decomposes array alloca`() {
        val arrayType = Type.Array(Type.I32, 4)
        val m = sroa.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(arrayType)
            val elemPtr = gep(arrayType, ptr, Constant.I32(0), Constant.I32(2))
            store(Constant.I32(99), elemPtr)
            val v = load(Type.I32, elemPtr)
            ret(v)
            finalizeFunction()
        })
        val insts = m.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertTrue(allocas.all { it.allocType !is Type.Array },
            "Array alloca should be decomposed: $allocas")
    }

    @Test
    fun `sroa - preserves non-aggregate alloca`() {
        val m = sroa.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr)
            ret(load(Type.I32, ptr))
            finalizeFunction()
        })
        val allocas = m.functions[0].blocks[0].instructions.filterIsInstance<Alloca>()
        assertEquals(1, allocas.size)
        assertEquals(Type.I32, allocas[0].allocType)
    }

    @Test
    fun `sroa - preserves alloca with escaped address`() {
        val structType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val m = sroa.run(build {
            declareFunction("use_ptr", listOf(Param("p", Type.OpaquePointer)), Type.Void)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(structType)
            call("use_ptr", listOf(ptr), Type.Void) // address escapes
            ret(Constant.I32(0))
            finalizeFunction()
        })
        val allocas = m.functions[1].blocks[0].instructions.filterIsInstance<Alloca>()
        assertTrue(allocas.any { it.allocType == structType },
            "Escaped alloca should be preserved: $allocas")
    }

    @Test
    fun `sroa then mem2reg fully promotes struct`() {
        val structType = Type.Struct(null, listOf(Type.I32, Type.I64))
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(structType)
            val field0Ptr = gep(structType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(42), field0Ptr)
            val v = load(Type.I32, field0Ptr)
            ret(v)
            finalizeFunction()
        }
        val result = PassPipeline()
            .add(ScalarReplacementOfAggregates())
            .add(Mem2Reg())
            .execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertFalse(insts.any { it is Alloca }, "All allocas should be promoted: $insts")
        assertFalse(insts.any { it is Load }, "All loads should be promoted: $insts")
    }

    // Pass Pipeline: ordering effects

    @Test
    fun `pipeline - fold then DCE removes dead folded code`() {
        val m = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(build {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val a = add(Constant.I32(10), Constant.I32(20))
                mul(Constant.I32(3), Constant.I32(4)) // dead
                ret(a)
                finalizeFunction()
            })
        assertEquals(1, instCount(m))
        assertEquals(30, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `pipeline - instcombine enables constant folding`() {
        val m = PassPipeline()
            .add(InstructionCombining())
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(build {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val a = add(Constant.I32(3), Constant.I32(7))
                val b = mul(a, Constant.I32(1)) // instcombine removes mul-by-1
                ret(b)
                finalizeFunction()
            })
        assertEquals(1, instCount(m))
        assertEquals(10, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `pipeline - fold enables jump threading`() {
        val m = PassPipeline()
            .add(ConstantFolding())
            .add(JumpThreading())
            .execute(build {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val cond = icmp(ICmpPredicate.SGT, Constant.I32(10), Constant.I32(5))
                condBr(cond, BlockRef("then"), BlockRef("else"))

                appendBlock("then")
                ret(Constant.I32(1))

                appendBlock("else")
                ret(Constant.I32(0))
                finalizeFunction()
            })
        assertEquals(1, blockCount(m))
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `pipeline - inlining enables constant folding`() {
        val m = PassPipeline()
            .add(Inlining())
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(build {
                val p = createFunction("add2", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                ret(add(p[0], p[1]))
                finalizeFunction()

                createFunction("main", emptyList(), Type.I32)
                appendBlock("entry")
                ret(call("add2", listOf(Constant.I32(10), Constant.I32(20)), Type.I32)!!)
                finalizeFunction()
            })
        val mainInsts = m.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
    }

    @Test
    fun `pipeline - GVN enables DCE`() {
        val m = PassPipeline()
            .add(GlobalValueNumbering())
            .add(DeadCodeElimination())
            .execute(build {
                val p = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
                appendBlock("entry")
                val a = add(p[0], p[1])
                val b = add(p[0], p[1]) // GVN replaces uses of b with a
                ret(add(a, b))
                finalizeFunction()
            })
        val insts = m.functions[0].blocks[0].instructions
        assertEquals(3, insts.size, "GVN+DCE should leave 3 instructions: $insts")
    }

    @Test
    fun `pipeline - mem2reg then fold simplifies stack variable`() {
        val m = PassPipeline()
            .add(Mem2Reg())
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(build {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val ptr = alloca(Type.I32)
                store(Constant.I32(10), ptr)
                val v = load(Type.I32, ptr)
                ret(add(v, Constant.I32(5)))
                finalizeFunction()
            })
        assertEquals(1, instCount(m))
        assertEquals(15, (retVal(m).value as Constant.I32).value)
    }

    // O0/O1/O2 pipeline levels

    @Test
    fun `O0 does not optimize`() {
        val m = OptLevel.O0.pipeline().execute(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(add(Constant.I32(1), Constant.I32(2)))
            finalizeFunction()
        })
        assertEquals(2, instCount(m))
        assertTrue(m.functions[0].blocks[0].instructions[0] is Add)
    }

    @Test
    fun `O1 folds and eliminates`() {
        val m = OptLevel.O1.pipeline().execute(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(10), Constant.I32(20))
            add(Constant.I32(3), Constant.I32(4)) // dead
            ret(a)
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertEquals(30, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `O2 collapses constant branch`() {
        val m = OptLevel.O2.pipeline().execute(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(5), Constant.I32(5))
            val cond = icmp(ICmpPredicate.SGT, a, Constant.I32(0))
            condBr(cond, BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            ret(Constant.I32(1))

            appendBlock("else")
            ret(Constant.I32(0))
            finalizeFunction()
        })
        assertEquals(1, blockCount(m))
        assertEquals(1, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `O2 handles mem2reg plus constant folding`() {
        val m = OptLevel.O2.pipeline().execute(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(21), ptr)
            val v = load(Type.I32, ptr)
            ret(mul(v, Constant.I32(2)))
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertEquals(42, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `Os is same as O1`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(10), Constant.I32(20))
            add(Constant.I32(3), Constant.I32(4))
            ret(a)
            finalizeFunction()
        }
        val o1 = OptLevel.O1.pipeline().execute(module)
        val os = OptLevel.Os.pipeline().execute(module)
        assertEquals(
            o1.functions[0].blocks[0].instructions.size,
            os.functions[0].blocks[0].instructions.size
        )
    }

    // Idempotency

    @Test
    fun `constant folding is idempotent`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(add(Constant.I32(3), Constant.I32(7)))
            finalizeFunction()
        }
        val once = fold.run(module)
        val twice = fold.run(once)
        assertEquals(
            (retVal(once).value as Constant.I32).value,
            (retVal(twice).value as Constant.I32).value
        )
    }

    @Test
    fun `DCE is idempotent`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            add(Constant.I32(1), Constant.I32(2))
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val once = dce.run(module)
        val twice = dce.run(once)
        assertEquals(instCount(once), instCount(twice))
    }

    @Test
    fun `instruction combining is idempotent`() {
        val module = build {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p[0], Constant.I32(0)))
            finalizeFunction()
        }
        val once = instcombine.run(module)
        val twice = instcombine.run(once)
        assertEquals(instCount(once), instCount(twice))
    }

    @Test
    fun `GVN is idempotent`() {
        val module = build {
            val p = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(p[0], p[1])
            val b = add(p[0], p[1])
            ret(add(a, b))
            finalizeFunction()
        }
        val once = gvn.run(module)
        val twice = gvn.run(once)
        assertEquals(instCount(once), instCount(twice))
    }

    @Test
    fun `jump threading is idempotent`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("then"), BlockRef("else"))
            appendBlock("then")
            ret(Constant.I32(1))
            appendBlock("else")
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val once = jt.run(module)
        val twice = jt.run(once)
        assertEquals(blockCount(once), blockCount(twice))
    }

    // Empty/minimal cases

    @Test
    fun `empty pipeline is identity`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(add(Constant.I32(1), Constant.I32(2)))
            finalizeFunction()
        }
        val result = PassPipeline().execute(module)
        assertEquals(2, instCount(result))
    }

    @Test
    fun `pipeline preserves external functions`() {
        val m = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(build {
                declareFunction("ext", listOf(Param("x", Type.I32)), Type.I32)
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                ret(Constant.I32(0))
                finalizeFunction()
            })
        assertEquals(2, m.functions.size)
        assertTrue(m.functions[0].isExternal)
    }

    @Test
    fun `multiple functions optimized independently`() {
        val m = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(build {
                createFunction("f1", emptyList(), Type.I32)
                appendBlock("entry")
                ret(add(Constant.I32(1), Constant.I32(2)))
                finalizeFunction()

                createFunction("f2", emptyList(), Type.I32)
                appendBlock("entry")
                ret(mul(Constant.I32(3), Constant.I32(4)))
                finalizeFunction()
            })
        assertEquals(3, (retVal(m, 0).value as Constant.I32).value)
        assertEquals(12, (retVal(m, 1).value as Constant.I32).value)
    }

    @Test
    fun `void function passes through all passes`() {
        val m = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .add(InstructionCombining())
            .add(JumpThreading())
            .execute(build {
                createFunction("f", emptyList(), Type.Void)
                appendBlock("entry")
                ret(null)
                finalizeFunction()
            })
        assertEquals(1, instCount(m))
        assertTrue(m.functions[0].blocks[0].instructions[0] is Ret)
    }

    // Floating point constant folding: f32

    @Test
    fun `cf - folds f32 add`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.F32)
            appendBlock("entry")
            ret(fadd(Constant.F32(1.5f), Constant.F32(2.5f)))
            finalizeFunction()
        })
        assertEquals(4.0f, (retVal(m).value as Constant.F32).value)
    }

    @Test
    fun `cf - folds f32 neg`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.F32)
            appendBlock("entry")
            ret(fneg(Constant.F32(7.0f)))
            finalizeFunction()
        })
        assertEquals(-7.0f, (retVal(m).value as Constant.F32).value)
    }

    // Additional constant folding edge cases

    @Test
    fun `cf - folds i32 overflow wraps`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(add(Constant.I32(Int.MAX_VALUE), Constant.I32(1)))
            finalizeFunction()
        })
        assertEquals(Int.MIN_VALUE, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `cf - folds trunc i64 to i1`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I1)
            appendBlock("entry")
            ret(trunc(Constant.I64(3L), Type.I1))
            finalizeFunction()
        })
        assertTrue((retVal(m).value as Constant.I1).value)
    }

    @Test
    fun `cf - folds zext i16 to i32`() {
        val m = fold.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(zext(Constant.I16(1000.toShort()), Type.I32))
            finalizeFunction()
        })
        assertEquals(1000, (retVal(m).value as Constant.I32).value)
    }

    // Comprehensive pipeline integration

    @Test
    fun `pipeline - sroa then mem2reg then fold fully optimizes struct access`() {
        val structType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val m = PassPipeline()
            .add(ScalarReplacementOfAggregates())
            .add(Mem2Reg())
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(build {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val ptr = alloca(structType)
                val f0 = gep(structType, ptr, Constant.I32(0), Constant.I32(0))
                val f1 = gep(structType, ptr, Constant.I32(0), Constant.I32(1))
                store(Constant.I32(10), f0)
                store(Constant.I32(20), f1)
                val v0 = load(Type.I32, f0)
                val v1 = load(Type.I32, f1)
                ret(add(v0, v1))
                finalizeFunction()
            })
        assertEquals(1, instCount(m))
        assertEquals(30, (retVal(m).value as Constant.I32).value)
    }

    @Test
    fun `pipeline - all passes on complex function`() {
        val m = OptLevel.O2.pipeline().execute(build {
            val p = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(0), ptr)
            val constExpr = add(Constant.I32(5), Constant.I32(5)) // folds to 10
            val identity = mul(constExpr, Constant.I32(1)) // instcombine to 10
            store(identity, ptr)
            val v = load(Type.I32, ptr) // mem2reg promotes
            ret(v)
            finalizeFunction()
        })
        assertEquals(1, instCount(m))
        assertEquals(10, (retVal(m).value as Constant.I32).value)
    }

    private fun isAggregate(type: Type): Boolean = when (type) {
        is Type.Struct -> true
        is Type.Array -> true
        else -> false
    }
}
