package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Target
import org.kgen.ir.verify.IrVerifier
import org.kgen.ir.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class FunctionBuilderTest {

    @Test
    fun `simple return`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("id", listOf(Param("x", Type.I32)), Type.I32)
        fn.ret(fn.param(0))
        fn.end()

        val mod = ir.build()
        assertEquals(1, mod.functions.size)
        assertEquals("id", mod.functions[0].name)
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `arithmetic operations`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("math", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val ins = fn.instructions
        val sum = ins.add(fn.param(0), fn.param(1))
        val diff = ins.sub(sum, fn.param(1))
        val prod = ins.mul(diff, fn.param(0))
        fn.ret(prod)
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `mutable variables`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("vars", emptyList(), Type.I32)
        val ins = fn.instructions
        val x = ins.variable(Type.i32(10))
        ins.set(x, ins.add(ins.get(x), Type.i32(5)))
        fn.ret(ins.get(x))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `ifThen`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("abs", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        val result = ins.variable(fn.param(0))
        fn.ifThen(ins.lt(fn.param(0), Type.i32(0))) {
            ins.set(result, ins.neg(ins.get(result)))
        }
        fn.ret(ins.get(result))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `ifElse`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val ins = fn.instructions
        val result = ins.variable(Type.i32(0))
        fn.ifElse(ins.gt(fn.param(0), fn.param(1)),
            { ins.set(result, param(0)) },
            { ins.set(result, param(1)) },
        )
        fn.ret(ins.get(result))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `whileLoop`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("sum_to_n", listOf(Param("n", Type.I32)), Type.I32)
        val ins = fn.instructions
        val sum = ins.variable(Type.i32(0))
        val i = ins.variable(Type.i32(1))
        fn.whileLoop(
            condition = { ins.le(ins.get(i), param(0)) },
            body = {
                ins.set(sum, ins.add(ins.get(sum), ins.get(i)))
                ins.set(i, ins.add(ins.get(i), Type.i32(1)))
            },
        )
        fn.ret(ins.get(sum))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `forLoop`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("factorial", listOf(Param("n", Type.I32)), Type.I32)
        val ins = fn.instructions
        val result = ins.variable(Type.i32(1))
        val i = ins.variable(Type.i32(0))
        fn.forLoop(
            init = { ins.set(i, Type.i32(1)) },
            condition = { ins.le(ins.get(i), param(0)) },
            update = { ins.set(i, ins.add(ins.get(i), Type.i32(1))) },
            body = { ins.set(result, ins.mul(ins.get(result), ins.get(i))) },
        )
        fn.ret(ins.get(result))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `doWhile`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("at_least_once", emptyList(), Type.I32)
        val ins = fn.instructions
        val x = ins.variable(Type.i32(0))
        fn.doWhile(
            body = { ins.set(x, ins.add(ins.get(x), Type.i32(1))) },
            condition = { ins.lt(ins.get(x), Type.i32(5)) },
        )
        fn.ret(ins.get(x))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `breakOut in loop`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("find_ten", emptyList(), Type.I32)
        val ins = fn.instructions
        val i = ins.variable(Type.i32(0))
        fn.whileLoop(
            condition = { ins.lt(ins.get(i), Type.i32(100)) },
            body = {
                ifThen(ins.eq(ins.get(i), Type.i32(10))) {
                    breakOut()
                }
                ins.set(i, ins.add(ins.get(i), Type.i32(1)))
            },
        )
        fn.ret(ins.get(i))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `continueOn in loop`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("skip_evens", emptyList(), Type.I32)
        val ins = fn.instructions
        val sum = ins.variable(Type.i32(0))
        val i = ins.variable(Type.i32(0))
        fn.whileLoop(
            condition = { ins.lt(ins.get(i), Type.i32(10)) },
            body = {
                ins.set(i, ins.add(ins.get(i), Type.i32(1)))
                ifThen(ins.eq(ins.rem(ins.get(i), Type.i32(2)), Type.i32(0))) {
                    continueOn()
                }
                ins.set(sum, ins.add(ins.get(sum), ins.get(i)))
            },
        )
        fn.ret(ins.get(sum))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `float operations`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("fmath", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        val ins = fn.instructions
        val sum = ins.fadd(fn.param(0), fn.param(1))
        val prod = ins.fmul(sum, fn.param(0))
        fn.ret(prod)
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `comparisons`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("cmp", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
        val ins = fn.instructions
        val result = ins.variable(Type.i1(false))
        fn.ifThen(ins.lt(fn.param(0), fn.param(1))) {
            ins.set(result, ins.eq(param(0), Type.i32(0)))
        }
        fn.ret(ins.get(result))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `intCast sign extend`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("widen", listOf(Param("x", Type.I32)), Type.I64)
        val ins = fn.instructions
        fn.ret(ins.intCast(fn.param(0), Type.I64))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `intCast truncate`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("narrow", listOf(Param("x", Type.I64)), Type.I32)
        val ins = fn.instructions
        fn.ret(ins.intCast(fn.param(0), Type.I32))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `select ternary`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("clamp", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        val clamped = fn.select(ins.gt(fn.param(0), Type.i32(100)), Type.i32(100), fn.param(0))
        fn.ret(clamped)
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `raw escape hatch`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("with_raw", listOf(Param("x", Type.I32)), Type.I32)
        var result: Value? = null
        fn.raw {
            result = add(fn.param(0), Type.i32(42))
        }
        fn.ret(result!!)
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `explicit getField and putField`() {
        val ir = ModuleBuilder("test")
        val cls = ClassDefinition("Counter", fields = listOf(FieldDefinition("count", Type.I32)))
        ir.addClass(cls)

        val fn = ir.function("inc", listOf(Param("self", Type.ClassRef("Counter"))), Type.I32)
        val ins = fn.instructions
        val current = ins.getField(fn.param(0), "Counter", "count", Type.I32)
        val next = ins.add(current, Type.i32(1))
        ins.putField(fn.param(0), "Counter", "count", Type.I32, next)
        fn.ret(next)
        fn.end()

        val mod = ir.build()
        assertEquals(1, mod.functions.size)
    }

    @Test
    fun `explicit virtualCall`() {
        val ir = ModuleBuilder("test")
        val cls = ClassDefinition("Greeter", methods = listOf(
            MethodDefinition("greet", emptyList(), Type.Void),
        ))
        ir.addClass(cls)

        val fn = ir.function("use_greeter", listOf(Param("g", Type.ClassRef("Greeter"))), Type.Void)
        val ins = fn.instructions
        val methodType = Type.Function(emptyList(), Type.Void)
        ins.virtualCall(fn.param(0), "Greeter", "greet", methodType, emptyList())
        fn.retVoid()
        fn.end()

        val mod = ir.build()
        assertEquals(1, mod.functions.size)
    }

    @Test
    fun `getField on unregistered class emits instruction with explicit args`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("explicit_get", listOf(Param("obj", Type.ClassRef("Missing"))), Type.I32)
        val ins = fn.instructions
        val result = ins.getField(fn.param(0), "Missing", "field", Type.I32)
        fn.ret(result)
        fn.end()

        val mod = ir.build()
        assertEquals(1, mod.functions.size)
    }

    @Test
    fun `getField with explicit args does not require class registration`() {
        val ir = ModuleBuilder("test")
        ir.addClass(ClassDefinition("Empty"))
        val fn = ir.function("explicit_get", listOf(Param("obj", Type.ClassRef("Empty"))), Type.I32)
        val ins = fn.instructions
        val result = ins.getField(fn.param(0), "Empty", "nonexistent", Type.I32)
        fn.ret(result)
        fn.end()

        val mod = ir.build()
        assertEquals(1, mod.functions.size)
    }

    @Test
    fun `retVoid`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("noop", emptyList(), Type.Void)
        fn.retVoid()
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `nested ifThen`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("nested", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        val result = ins.variable(Type.i32(0))
        fn.ifThen(ins.gt(fn.param(0), Type.i32(0))) {
            ifThen(ins.gt(param(0), Type.i32(10))) {
                ins.set(result, Type.i32(2))
            }
        }
        fn.ret(ins.get(result))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `ifElse both branches return`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("either", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        fn.ifElse(ins.gt(fn.param(0), Type.i32(0)),
            { ret(Type.i32(1)) },
            { ret(Type.i32(-1)) },
        )
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }
}
