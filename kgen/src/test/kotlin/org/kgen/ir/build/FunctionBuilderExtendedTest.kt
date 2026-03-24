package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.ir.verify.IrVerifier
import org.kgen.ir.types.*
import org.kgen.ir.text.IrPrinter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class FunctionBuilderExtendedTest {

    @Test
    fun nestedWhileLoops() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("nested_loops", emptyList(), Type.I32)
        val ins = fn.instructions
        val sum = ins.variable(Type.i32(0))
        val i = ins.variable(Type.i32(0))
        fn.whileLoop(
            condition = { ins.lt(ins.get(i), Type.i32(3)) },
            body = {
                val j = ins.variable(Type.i32(0))
                whileLoop(
                    condition = { ins.lt(ins.get(j), Type.i32(3)) },
                    body = {
                        ins.set(sum, ins.add(ins.get(sum), Type.i32(1)))
                        ins.set(j, ins.add(ins.get(j), Type.i32(1)))
                    }
                )
                ins.set(i, ins.add(ins.get(i), Type.i32(1)))
            }
        )
        fn.ret(ins.get(sum))
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun nestedForLoops() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("matrix_sum", listOf(Param("n", Type.I32)), Type.I32)
        val ins = fn.instructions
        val sum = ins.variable(Type.i32(0))
        val i = ins.variable(Type.i32(0))
        val j = ins.variable(Type.i32(0))
        fn.forLoop(
            init = { ins.set(i, Type.i32(0)) },
            condition = { ins.lt(ins.get(i), param(0)) },
            update = { ins.set(i, ins.add(ins.get(i), Type.i32(1))) },
            body = {
                forLoop(
                    init = { ins.set(j, Type.i32(0)) },
                    condition = { ins.lt(ins.get(j), param(0)) },
                    update = { ins.set(j, ins.add(ins.get(j), Type.i32(1))) },
                    body = { ins.set(sum, ins.add(ins.get(sum), ins.mul(ins.get(i), ins.get(j)))) }
                )
            }
        )
        fn.ret(ins.get(sum))
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun breakInNestedLoop() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("break_inner", emptyList(), Type.I32)
        val ins = fn.instructions
        val result = ins.variable(Type.i32(0))
        val i = ins.variable(Type.i32(0))
        fn.whileLoop(
            condition = { ins.lt(ins.get(i), Type.i32(5)) },
            body = {
                val j = ins.variable(Type.i32(0))
                whileLoop(
                    condition = { ins.lt(ins.get(j), Type.i32(5)) },
                    body = {
                        ifThen(ins.eq(ins.get(j), Type.i32(2))) {
                            breakOut()
                        }
                        ins.set(result, ins.add(ins.get(result), Type.i32(1)))
                        ins.set(j, ins.add(ins.get(j), Type.i32(1)))
                    }
                )
                ins.set(i, ins.add(ins.get(i), Type.i32(1)))
            }
        )
        fn.ret(ins.get(result))
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun continueInNestedLoop() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("continue_inner", emptyList(), Type.I32)
        val ins = fn.instructions
        val sum = ins.variable(Type.i32(0))
        val i = ins.variable(Type.i32(0))
        fn.whileLoop(
            condition = { ins.lt(ins.get(i), Type.i32(10)) },
            body = {
                ins.set(i, ins.add(ins.get(i), Type.i32(1)))
                ifThen(ins.eq(ins.rem(ins.get(i), Type.i32(3)), Type.i32(0))) {
                    continueOn()
                }
                ins.set(sum, ins.add(ins.get(sum), ins.get(i)))
            }
        )
        fn.ret(ins.get(sum))
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun deeplyNestedIfElse() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("classify", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        val result = ins.variable(Type.i32(0))
        fn.ifElse(ins.lt(fn.param(0), Type.i32(0)),
            { ins.set(result, Type.i32(-1)) },
            {
                ifElse(ins.eq(param(0), Type.i32(0)),
                    { ins.set(result, Type.i32(0)) },
                    {
                        ifElse(ins.lt(param(0), Type.i32(10)),
                            { ins.set(result, Type.i32(1)) },
                            { ins.set(result, Type.i32(2)) }
                        )
                    }
                )
            }
        )
        fn.ret(ins.get(result))
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun ifElseInLoop() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("count_sign", listOf(Param("n", Type.I32)), Type.I32)
        val ins = fn.instructions
        val pos = ins.variable(Type.i32(0))
        val neg = ins.variable(Type.i32(0))
        val i = ins.variable(Type.i32(0))
        fn.forLoop(
            init = { ins.set(i, ins.sub(Type.i32(0), param(0))) },
            condition = { ins.le(ins.get(i), param(0)) },
            update = { ins.set(i, ins.add(ins.get(i), Type.i32(1))) },
            body = {
                ifElse(ins.gt(ins.get(i), Type.i32(0)),
                    { ins.set(pos, ins.add(ins.get(pos), Type.i32(1))) },
                    { ins.set(neg, ins.add(ins.get(neg), Type.i32(1))) }
                )
            }
        )
        fn.ret(ins.sub(ins.get(pos), ins.get(neg)))
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun multipleFunctions() {
        val ir = ModuleBuilder("test")
        val fn1 = ir.function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val ins1 = fn1.instructions
        fn1.ret(ins1.add(fn1.param(0), fn1.param(1)))
        fn1.end()

        val fn2 = ir.function("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val ins2 = fn2.instructions
        fn2.ret(ins2.sub(fn2.param(0), fn2.param(1)))
        fn2.end()

        val fn3 = ir.function("mul", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val ins3 = fn3.instructions
        fn3.ret(ins3.mul(fn3.param(0), fn3.param(1)))
        fn3.end()

        val mod = ir.build()
        assertEquals(3, mod.functions.size)
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun doWhileWithBreak() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("do_break", emptyList(), Type.I32)
        val ins = fn.instructions
        val x = ins.variable(Type.i32(0))
        fn.doWhile(
            body = {
                ins.set(x, ins.add(ins.get(x), Type.i32(1)))
                ifThen(ins.eq(ins.get(x), Type.i32(3))) {
                    breakOut()
                }
            },
            condition = { ins.lt(ins.get(x), Type.i32(10)) }
        )
        fn.ret(ins.get(x))
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun doWhileWithContinue() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("do_continue", emptyList(), Type.I32)
        val ins = fn.instructions
        val sum = ins.variable(Type.i32(0))
        val x = ins.variable(Type.i32(0))
        fn.doWhile(
            body = {
                ins.set(x, ins.add(ins.get(x), Type.i32(1)))
                ifThen(ins.eq(ins.rem(ins.get(x), Type.i32(2)), Type.i32(0))) {
                    continueOn()
                }
                ins.set(sum, ins.add(ins.get(sum), ins.get(x)))
            },
            condition = { ins.lt(ins.get(x), Type.i32(6)) }
        )
        fn.ret(ins.get(sum))
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun i8Operations() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("byte_op", listOf(Param("a", Type.I8), Param("b", Type.I8)), Type.I8)
        val ins = fn.instructions
        val sum = ins.add(fn.param(0), fn.param(1))
        fn.ret(sum)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun i16Operations() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("short_op", listOf(Param("a", Type.I16), Param("b", Type.I16)), Type.I16)
        val ins = fn.instructions
        val diff = ins.sub(fn.param(0), fn.param(1))
        fn.ret(diff)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun f32Operations() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("float_op", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        val ins = fn.instructions
        val sum = ins.fadd(fn.param(0), fn.param(1))
        val prod = ins.fmul(sum, fn.param(1))
        val diff = ins.fsub(prod, fn.param(0))
        val quot = ins.fdiv(diff, fn.param(1))
        fn.ret(quot)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun i64Arithmetic() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("long_op", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        val ins = fn.instructions
        val sum = ins.add(fn.param(0), fn.param(1))
        val prod = ins.mul(sum, fn.param(1))
        fn.ret(prod)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun bitwiseOperations() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("bits", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val ins = fn.instructions
        val andResult = ins.and(fn.param(0), fn.param(1))
        val orResult = ins.or(andResult, fn.param(1))
        val xorResult = ins.xor(orResult, fn.param(0))
        fn.ret(xorResult)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun shiftOperations() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("shifts", listOf(Param("x", Type.I32), Param("n", Type.I32)), Type.I32)
        val ins = fn.instructions
        val left = ins.shl(fn.param(0), fn.param(1))
        val right = ins.shr(left, fn.param(1))
        val arith = ins.shr(right, Type.i32(1))
        fn.ret(arith)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun manyVariables() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("many_vars", emptyList(), Type.I32)
        val ins = fn.instructions
        val a = ins.variable(Type.i32(1))
        val b = ins.variable(Type.i32(2))
        val c = ins.variable(Type.i32(3))
        val d = ins.variable(Type.i32(4))
        val e = ins.variable(Type.i32(5))
        ins.set(a, ins.add(ins.get(a), ins.get(b)))
        ins.set(c, ins.mul(ins.get(c), ins.get(d)))
        ins.set(e, ins.sub(ins.get(a), ins.get(c)))
        fn.ret(ins.get(e))
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun voidFunctionWithSideEffects() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("side_effect", listOf(Param("x", Type.I32)), Type.Void)
        val ins = fn.instructions
        val v = ins.variable(fn.param(0))
        ins.set(v, ins.add(ins.get(v), Type.i32(1)))
        fn.retVoid()
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun selectI64() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("sel64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        val ins = fn.instructions
        val cond = ins.gt(fn.param(0), fn.param(1))
        val result = fn.select(cond, fn.param(0), fn.param(1))
        fn.ret(result)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun allComparisons() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("cmp_all", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val ins = fn.instructions
        val result = ins.variable(Type.i32(0))
        fn.ifThen(ins.eq(fn.param(0), fn.param(1))) { ins.set(result, ins.add(ins.get(result), Type.i32(1))) }
        fn.ifThen(ins.ne(fn.param(0), fn.param(1))) { ins.set(result, ins.add(ins.get(result), Type.i32(2))) }
        fn.ifThen(ins.lt(fn.param(0), fn.param(1))) { ins.set(result, ins.add(ins.get(result), Type.i32(4))) }
        fn.ifThen(ins.le(fn.param(0), fn.param(1))) { ins.set(result, ins.add(ins.get(result), Type.i32(8))) }
        fn.ifThen(ins.gt(fn.param(0), fn.param(1))) { ins.set(result, ins.add(ins.get(result), Type.i32(16))) }
        fn.ifThen(ins.ge(fn.param(0), fn.param(1))) { ins.set(result, ins.add(ins.get(result), Type.i32(32))) }
        fn.ret(ins.get(result))
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun intCastChain() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("cast_chain", listOf(Param("x", Type.I8)), Type.I64)
        val ins = fn.instructions
        val i16 = ins.intCast(fn.param(0), Type.I16)
        val i32 = ins.intCast(i16, Type.I32)
        val i64 = ins.intCast(i32, Type.I64)
        fn.ret(i64)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun intToFloatConversion() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("i2f", listOf(Param("x", Type.I32)), Type.F64)
        val ins = fn.instructions
        val f = ins.toFloat(fn.param(0), Type.F64)
        fn.ret(f)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun floatToIntConversion() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("f2i", listOf(Param("x", Type.F64)), Type.I32)
        val ins = fn.instructions
        val i = ins.toInt(fn.param(0), Type.I32)
        fn.ret(i)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun floatCast() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("fcast", listOf(Param("x", Type.F32)), Type.F64)
        val ins = fn.instructions
        val d = ins.floatCast(fn.param(0), Type.F64)
        fn.ret(d)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun printedOutputIsNonEmpty() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("hello", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        fn.ret(ins.add(fn.param(0), Type.i32(1)))
        fn.end()
        val mod = ir.build()
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("hello"))
        assertTrue(text.contains("add"))
        assertTrue(text.contains("ret"))
    }

    @Test
    fun moduleDslBasic() {
        val mod = module("dsl_test") {
            function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val sum = add(param(0), param(1))
                    ret(sum)
                }
            }
        }
        assertEquals(1, mod.functions.size)
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun moduleDslMultipleFunctions() {
        val mod = module("multi") {
            function("f1", emptyList(), Type.Void) {
                block("entry") { ret() }
            }
            function("f2", emptyList(), Type.Void) {
                block("entry") { ret() }
            }
            function("f3", emptyList(), Type.Void) {
                block("entry") { ret() }
            }
        }
        assertEquals(3, mod.functions.size)
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun moduleDslWithGlobal() {
        val mod = module("global_test") {
            global("counter", Type.I32, Type.i32(0))
            function("inc", emptyList(), Type.Void) {
                block("entry") { ret() }
            }
        }
        assertEquals(1, mod.globals.size)
        assertEquals("counter", mod.globals[0].name)
    }

    @Test
    fun moduleDslWithStruct() {
        val mod = module("struct_test") {
            struct("Point", listOf(Param("x", Type.F64), Param("y", Type.F64)))
            function("origin", emptyList(), Type.Void) {
                block("entry") { ret() }
            }
        }
        assertEquals(1, mod.structs.size)
        assertEquals("Point", mod.structs[0].name)
    }

    @Test
    fun emptyModule() {
        val ir = ModuleBuilder("empty")
        val mod = ir.build()
        assertEquals("empty", mod.name)
        assertEquals(0, mod.functions.size)
    }

    @Test
    fun functionWithManyParams() {
        val ir = ModuleBuilder("test")
        val params = (0 until 10).map { Param("p$it", Type.I32) }
        val fn = ir.function("many_params", params, Type.I32)
        val ins = fn.instructions
        var sum = fn.param(0)
        for (i in 1 until 10) {
            sum = ins.add(sum, fn.param(i))
        }
        fn.ret(sum)
        fn.end()
        val mod = ir.build()
        assertEquals(10, mod.functions[0].params.size)
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun negation() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("negate", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        val negated = ins.neg(fn.param(0))
        fn.ret(negated)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun remainderOperation() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("modulo", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val ins = fn.instructions
        val result = ins.rem(fn.param(0), fn.param(1))
        fn.ret(result)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun divisionOperation() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("divide", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val ins = fn.instructions
        val result = ins.div(fn.param(0), fn.param(1))
        fn.ret(result)
        fn.end()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }
}
