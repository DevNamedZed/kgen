package org.kgen.target.jvm.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class JvmCodeGenInstructionTest {

    private class ByteArrayClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        fun defineClass(name: String, bytes: ByteArray): Class<*> {
            return defineClass(name, bytes, 0, bytes.size)
        }
    }

    private var counter = 0

    private fun nextClassName(): String = "Test${counter++}"

    private fun generateAndLoad(module: Module, className: String): Class<*> {
        val gen = JvmCodeGenerator()
        gen.className = className
        val bytes = gen.generate(module)
        assertTrue(bytes.isNotEmpty())
        val loader = ByteArrayClassLoader(this::class.java.classLoader)
        return loader.defineClass(className, bytes)
    }

    private fun buildI32BinOp(name: String, op: (ModuleBuilder, Value, Value) -> Value): Class<*> {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction(name, listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.I32, 0)
        val b = Parameter("b", Type.I32, 1)
        ir.ret(op(ir, a, b))
        ir.finalizeFunction()
        return generateAndLoad(ir.build(), cn)
    }

    private fun buildI64BinOp(name: String, op: (ModuleBuilder, Value, Value) -> Value): Class<*> {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction(name, listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.I64, 0)
        val b = Parameter("b", Type.I64, 1)
        ir.ret(op(ir, a, b))
        ir.finalizeFunction()
        return generateAndLoad(ir.build(), cn)
    }

    private fun buildF32BinOp(name: String, op: (ModuleBuilder, Value, Value) -> Value): Class<*> {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction(name, listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.F32, 0)
        val b = Parameter("b", Type.F32, 1)
        ir.ret(op(ir, a, b))
        ir.finalizeFunction()
        return generateAndLoad(ir.build(), cn)
    }

    private fun buildF64BinOp(name: String, op: (ModuleBuilder, Value, Value) -> Value): Class<*> {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction(name, listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.F64, 0)
        val b = Parameter("b", Type.F64, 1)
        ir.ret(op(ir, a, b))
        ir.finalizeFunction()
        return generateAndLoad(ir.build(), cn)
    }

    @Test
    fun `add i32`() {
        val clazz = buildI32BinOp("add") { ir, a, b -> ir.add(a, b) }
        val m = clazz.getMethod("add", Int::class.java, Int::class.java)
        assertEquals(7, m.invoke(null, 3, 4))
        assertEquals(-1, m.invoke(null, 0, -1))
    }

    @Test
    fun `add i64`() {
        val clazz = buildI64BinOp("add") { ir, a, b -> ir.add(a, b) }
        val m = clazz.getMethod("add", Long::class.java, Long::class.java)
        assertEquals(7L, m.invoke(null, 3L, 4L))
        assertEquals(4294967296L, m.invoke(null, 4294967295L, 1L))
    }

    @Test
    fun `sub i32`() {
        val clazz = buildI32BinOp("sub") { ir, a, b -> ir.sub(a, b) }
        val m = clazz.getMethod("sub", Int::class.java, Int::class.java)
        assertEquals(6, m.invoke(null, 10, 4))
        assertEquals(-5, m.invoke(null, 0, 5))
    }

    @Test
    fun `sub i64`() {
        val clazz = buildI64BinOp("sub") { ir, a, b -> ir.sub(a, b) }
        val m = clazz.getMethod("sub", Long::class.java, Long::class.java)
        assertEquals(6L, m.invoke(null, 10L, 4L))
    }

    @Test
    fun `mul i32`() {
        val clazz = buildI32BinOp("mul") { ir, a, b -> ir.mul(a, b) }
        val m = clazz.getMethod("mul", Int::class.java, Int::class.java)
        assertEquals(12, m.invoke(null, 3, 4))
        assertEquals(-6, m.invoke(null, -2, 3))
    }

    @Test
    fun `mul i64`() {
        val clazz = buildI64BinOp("mul") { ir, a, b -> ir.mul(a, b) }
        val m = clazz.getMethod("mul", Long::class.java, Long::class.java)
        assertEquals(12L, m.invoke(null, 3L, 4L))
    }

    @Test
    fun `sdiv i32`() {
        val clazz = buildI32BinOp("sdiv") { ir, a, b -> ir.sdiv(a, b) }
        val m = clazz.getMethod("sdiv", Int::class.java, Int::class.java)
        assertEquals(5, m.invoke(null, 10, 2))
        assertEquals(-3, m.invoke(null, -9, 3))
    }

    @Test
    fun `sdiv i64`() {
        val clazz = buildI64BinOp("sdiv") { ir, a, b -> ir.sdiv(a, b) }
        val m = clazz.getMethod("sdiv", Long::class.java, Long::class.java)
        assertEquals(5L, m.invoke(null, 10L, 2L))
    }

    @Test
    fun `udiv i32`() {
        val clazz = buildI32BinOp("udiv") { ir, a, b -> ir.udiv(a, b) }
        val m = clazz.getMethod("udiv", Int::class.java, Int::class.java)
        assertEquals(5, m.invoke(null, 10, 2))
    }

    @Test
    fun `udiv i64`() {
        val clazz = buildI64BinOp("udiv") { ir, a, b -> ir.udiv(a, b) }
        val m = clazz.getMethod("udiv", Long::class.java, Long::class.java)
        assertEquals(5L, m.invoke(null, 10L, 2L))
    }

    @Test
    fun `srem i32`() {
        val clazz = buildI32BinOp("srem") { ir, a, b -> ir.srem(a, b) }
        val m = clazz.getMethod("srem", Int::class.java, Int::class.java)
        assertEquals(1, m.invoke(null, 7, 3))
        assertEquals(-1, m.invoke(null, -7, 3))
    }

    @Test
    fun `srem i64`() {
        val clazz = buildI64BinOp("srem") { ir, a, b -> ir.srem(a, b) }
        val m = clazz.getMethod("srem", Long::class.java, Long::class.java)
        assertEquals(1L, m.invoke(null, 7L, 3L))
    }

    @Test
    fun `urem i32`() {
        val clazz = buildI32BinOp("urem") { ir, a, b -> ir.urem(a, b) }
        val m = clazz.getMethod("urem", Int::class.java, Int::class.java)
        assertEquals(1, m.invoke(null, 7, 3))
    }

    @Test
    fun `urem i64`() {
        val clazz = buildI64BinOp("urem") { ir, a, b -> ir.urem(a, b) }
        val m = clazz.getMethod("urem", Long::class.java, Long::class.java)
        assertEquals(1L, m.invoke(null, 7L, 3L))
    }

    @Test
    fun `and i32`() {
        val clazz = buildI32BinOp("bitand") { ir, a, b -> ir.and(a, b) }
        val m = clazz.getMethod("bitand", Int::class.java, Int::class.java)
        assertEquals(0x00FF, m.invoke(null, 0x0FFF, 0x00FF))
    }

    @Test
    fun `and i64`() {
        val clazz = buildI64BinOp("bitand") { ir, a, b -> ir.and(a, b) }
        val m = clazz.getMethod("bitand", Long::class.java, Long::class.java)
        assertEquals(0x00FFL, m.invoke(null, 0x0FFFL, 0x00FFL))
    }

    @Test
    fun `or i32`() {
        val clazz = buildI32BinOp("bitor") { ir, a, b -> ir.or(a, b) }
        val m = clazz.getMethod("bitor", Int::class.java, Int::class.java)
        assertEquals(0x0FFF, m.invoke(null, 0x0F00, 0x00FF))
    }

    @Test
    fun `or i64`() {
        val clazz = buildI64BinOp("bitor") { ir, a, b -> ir.or(a, b) }
        val m = clazz.getMethod("bitor", Long::class.java, Long::class.java)
        assertEquals(0x0FFFL, m.invoke(null, 0x0F00L, 0x00FFL))
    }

    @Test
    fun `xor i32`() {
        val clazz = buildI32BinOp("bitxor") { ir, a, b -> ir.xor(a, b) }
        val m = clazz.getMethod("bitxor", Int::class.java, Int::class.java)
        assertEquals(0x0FF0, m.invoke(null, 0x0FFF, 0x000F))
    }

    @Test
    fun `xor i64`() {
        val clazz = buildI64BinOp("bitxor") { ir, a, b -> ir.xor(a, b) }
        val m = clazz.getMethod("bitxor", Long::class.java, Long::class.java)
        assertEquals(0x0FF0L, m.invoke(null, 0x0FFFL, 0x000FL))
    }

    @Test
    fun `shl i32`() {
        val clazz = buildI32BinOp("shl") { ir, a, b -> ir.shl(a, b) }
        val m = clazz.getMethod("shl", Int::class.java, Int::class.java)
        assertEquals(8, m.invoke(null, 1, 3))
        assertEquals(16, m.invoke(null, 4, 2))
    }

    @Test
    fun `shl i64`() {
        val clazz = buildI64BinOp("shl") { ir, a, b -> ir.shl(a, b) }
        val m = clazz.getMethod("shl", Long::class.java, Long::class.java)
        assertEquals(8L, m.invoke(null, 1L, 3L))
    }

    @Test
    fun `lshr i32`() {
        val clazz = buildI32BinOp("lshr") { ir, a, b -> ir.lshr(a, b) }
        val m = clazz.getMethod("lshr", Int::class.java, Int::class.java)
        assertEquals(4, m.invoke(null, 16, 2))
        // unsigned shift: -1 >>> 1 = 0x7FFFFFFF
        assertEquals(Int.MAX_VALUE, m.invoke(null, -1, 1))
    }

    @Test
    fun `lshr i64`() {
        val clazz = buildI64BinOp("lshr") { ir, a, b -> ir.lshr(a, b) }
        val m = clazz.getMethod("lshr", Long::class.java, Long::class.java)
        assertEquals(4L, m.invoke(null, 16L, 2L))
        assertEquals(Long.MAX_VALUE, m.invoke(null, -1L, 1L))
    }

    @Test
    fun `ashr i32`() {
        val clazz = buildI32BinOp("ashr") { ir, a, b -> ir.ashr(a, b) }
        val m = clazz.getMethod("ashr", Int::class.java, Int::class.java)
        assertEquals(4, m.invoke(null, 16, 2))
        assertEquals(-1, m.invoke(null, -1, 1))
        assertEquals(-4, m.invoke(null, -8, 1))
    }

    @Test
    fun `ashr i64`() {
        val clazz = buildI64BinOp("ashr") { ir, a, b -> ir.ashr(a, b) }
        val m = clazz.getMethod("ashr", Long::class.java, Long::class.java)
        assertEquals(4L, m.invoke(null, 16L, 2L))
        assertEquals(-1L, m.invoke(null, -1L, 1L))
    }

    @Test
    fun `fadd f32`() {
        val clazz = buildF32BinOp("fadd") { ir, a, b -> ir.fadd(a, b) }
        val m = clazz.getMethod("fadd", Float::class.java, Float::class.java)
        assertEquals(5.5f, m.invoke(null, 2.5f, 3.0f))
    }

    @Test
    fun `fadd f64`() {
        val clazz = buildF64BinOp("fadd") { ir, a, b -> ir.fadd(a, b) }
        val m = clazz.getMethod("fadd", Double::class.java, Double::class.java)
        assertEquals(5.5, m.invoke(null, 2.5, 3.0))
    }

    @Test
    fun `fsub f32`() {
        val clazz = buildF32BinOp("fsub") { ir, a, b -> ir.fsub(a, b) }
        val m = clazz.getMethod("fsub", Float::class.java, Float::class.java)
        assertEquals(1.5f, m.invoke(null, 4.0f, 2.5f))
    }

    @Test
    fun `fsub f64`() {
        val clazz = buildF64BinOp("fsub") { ir, a, b -> ir.fsub(a, b) }
        val m = clazz.getMethod("fsub", Double::class.java, Double::class.java)
        assertEquals(1.5, m.invoke(null, 4.0, 2.5))
    }

    @Test
    fun `fmul f32`() {
        val clazz = buildF32BinOp("fmul") { ir, a, b -> ir.fmul(a, b) }
        val m = clazz.getMethod("fmul", Float::class.java, Float::class.java)
        assertEquals(7.5f, m.invoke(null, 2.5f, 3.0f))
    }

    @Test
    fun `fmul f64`() {
        val clazz = buildF64BinOp("fmul") { ir, a, b -> ir.fmul(a, b) }
        val m = clazz.getMethod("fmul", Double::class.java, Double::class.java)
        assertEquals(7.5, m.invoke(null, 2.5, 3.0))
    }

    @Test
    fun `fdiv f32`() {
        val clazz = buildF32BinOp("fdiv") { ir, a, b -> ir.fdiv(a, b) }
        val m = clazz.getMethod("fdiv", Float::class.java, Float::class.java)
        assertEquals(2.5f, m.invoke(null, 5.0f, 2.0f))
    }

    @Test
    fun `fdiv f64`() {
        val clazz = buildF64BinOp("fdiv") { ir, a, b -> ir.fdiv(a, b) }
        val m = clazz.getMethod("fdiv", Double::class.java, Double::class.java)
        assertEquals(2.5, m.invoke(null, 5.0, 2.0))
    }

    @Test
    fun `neg i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("neg", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.neg(Parameter("x", Type.I32, 0)))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("neg", Int::class.java)
        assertEquals(-5, m.invoke(null, 5))
        assertEquals(3, m.invoke(null, -3))
        assertEquals(0, m.invoke(null, 0))
    }

    @Test
    fun `neg i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("neg", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.neg(Parameter("x", Type.I64, 0)))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("neg", Long::class.java)
        assertEquals(-5L, m.invoke(null, 5L))
        assertEquals(3L, m.invoke(null, -3L))
    }

    @Test
    fun `not i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("bitnot", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.not(Parameter("x", Type.I32, 0)))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("bitnot", Int::class.java)
        assertEquals(-1, m.invoke(null, 0))
        assertEquals(0, m.invoke(null, -1))
        assertEquals(-6, m.invoke(null, 5))
    }

    @Test
    fun `not i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("bitnot", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.not(Parameter("x", Type.I64, 0)))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("bitnot", Long::class.java)
        assertEquals(-1L, m.invoke(null, 0L))
        assertEquals(0L, m.invoke(null, -1L))
    }

    @Test
    fun `fneg f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("fneg", listOf(Param("x", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.fneg(Parameter("x", Type.F32, 0)))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("fneg", Float::class.java)
        assertEquals(-3.5f, m.invoke(null, 3.5f))
        assertEquals(2.0f, m.invoke(null, -2.0f))
    }

    @Test
    fun `fneg f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("fneg", listOf(Param("x", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.fneg(Parameter("x", Type.F64, 0)))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("fneg", Double::class.java)
        assertEquals(-3.5, m.invoke(null, 3.5))
        assertEquals(2.0, m.invoke(null, -2.0))
    }

    @Test
    fun `icmp eq i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("eq", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.I32, 0)
        val b = Parameter("b", Type.I32, 1)
        val cmp = ir.icmp(ICmpPredicate.EQ, a, b)
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("eq", Int::class.java, Int::class.java)
        assertEquals(1, m.invoke(null, 5, 5))
        assertEquals(0, m.invoke(null, 5, 3))
    }

    @Test
    fun `icmp ne i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("ne", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.NE, Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("ne", Int::class.java, Int::class.java)
        assertEquals(0, m.invoke(null, 5, 5))
        assertEquals(1, m.invoke(null, 5, 3))
    }

    @Test
    fun `icmp slt i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("slt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SLT, Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("slt", Int::class.java, Int::class.java)
        assertEquals(1, m.invoke(null, 3, 5))
        assertEquals(0, m.invoke(null, 5, 3))
        assertEquals(0, m.invoke(null, 5, 5))
    }

    @Test
    fun `icmp sle i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sle", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SLE, Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("sle", Int::class.java, Int::class.java)
        assertEquals(1, m.invoke(null, 3, 5))
        assertEquals(0, m.invoke(null, 5, 3))
        assertEquals(1, m.invoke(null, 5, 5))
    }

    @Test
    fun `icmp sgt i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sgt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("sgt", Int::class.java, Int::class.java)
        assertEquals(0, m.invoke(null, 3, 5))
        assertEquals(1, m.invoke(null, 5, 3))
        assertEquals(0, m.invoke(null, 5, 5))
    }

    @Test
    fun `icmp sge i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sge", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGE, Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("sge", Int::class.java, Int::class.java)
        assertEquals(0, m.invoke(null, 3, 5))
        assertEquals(1, m.invoke(null, 5, 3))
        assertEquals(1, m.invoke(null, 5, 5))
    }

    @Test
    fun `icmp eq i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("eq", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.EQ, Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("eq", Long::class.java, Long::class.java)
        assertEquals(1, m.invoke(null, 5L, 5L))
        assertEquals(0, m.invoke(null, 5L, 3L))
    }

    @Test
    fun `icmp ne i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("ne", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.NE, Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("ne", Long::class.java, Long::class.java)
        assertEquals(0, m.invoke(null, 5L, 5L))
        assertEquals(1, m.invoke(null, 5L, 3L))
    }

    @Test
    fun `icmp slt i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("slt", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SLT, Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("slt", Long::class.java, Long::class.java)
        assertEquals(1, m.invoke(null, 3L, 5L))
        assertEquals(0, m.invoke(null, 5L, 3L))
    }

    @Test
    fun `icmp sle i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sle", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SLE, Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("sle", Long::class.java, Long::class.java)
        assertEquals(1, m.invoke(null, 3L, 5L))
        assertEquals(1, m.invoke(null, 5L, 5L))
    }

    @Test
    fun `icmp sgt i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sgt", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("sgt", Long::class.java, Long::class.java)
        assertEquals(1, m.invoke(null, 5L, 3L))
        assertEquals(0, m.invoke(null, 3L, 5L))
    }

    @Test
    fun `icmp sge i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sge", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGE, Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("sge", Long::class.java, Long::class.java)
        assertEquals(1, m.invoke(null, 5L, 3L))
        assertEquals(1, m.invoke(null, 5L, 5L))
        assertEquals(0, m.invoke(null, 3L, 5L))
    }

    @Test
    fun `fcmp oeq f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("oeq", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OEQ, Parameter("a", Type.F32, 0), Parameter("b", Type.F32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("oeq", Float::class.java, Float::class.java)
        assertEquals(1, m.invoke(null, 3.0f, 3.0f))
        assertEquals(0, m.invoke(null, 3.0f, 4.0f))
    }

    @Test
    fun `fcmp one f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("one", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.ONE, Parameter("a", Type.F32, 0), Parameter("b", Type.F32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("one", Float::class.java, Float::class.java)
        assertEquals(0, m.invoke(null, 3.0f, 3.0f))
        assertEquals(1, m.invoke(null, 3.0f, 4.0f))
    }

    @Test
    fun `fcmp olt f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("olt", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OLT, Parameter("a", Type.F32, 0), Parameter("b", Type.F32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("olt", Float::class.java, Float::class.java)
        assertEquals(1, m.invoke(null, 2.0f, 3.0f))
        assertEquals(0, m.invoke(null, 3.0f, 2.0f))
    }

    @Test
    fun `fcmp ole f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("ole", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OLE, Parameter("a", Type.F32, 0), Parameter("b", Type.F32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("ole", Float::class.java, Float::class.java)
        assertEquals(1, m.invoke(null, 2.0f, 3.0f))
        assertEquals(1, m.invoke(null, 3.0f, 3.0f))
        assertEquals(0, m.invoke(null, 4.0f, 3.0f))
    }

    @Test
    fun `fcmp ogt f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("ogt", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OGT, Parameter("a", Type.F32, 0), Parameter("b", Type.F32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("ogt", Float::class.java, Float::class.java)
        assertEquals(0, m.invoke(null, 2.0f, 3.0f))
        assertEquals(1, m.invoke(null, 4.0f, 3.0f))
    }

    @Test
    fun `fcmp oge f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("oge", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OGE, Parameter("a", Type.F32, 0), Parameter("b", Type.F32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("oge", Float::class.java, Float::class.java)
        assertEquals(0, m.invoke(null, 2.0f, 3.0f))
        assertEquals(1, m.invoke(null, 3.0f, 3.0f))
        assertEquals(1, m.invoke(null, 4.0f, 3.0f))
    }

    @Test
    fun `fcmp oeq f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("oeq", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OEQ, Parameter("a", Type.F64, 0), Parameter("b", Type.F64, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("oeq", Double::class.java, Double::class.java)
        assertEquals(1, m.invoke(null, 3.0, 3.0))
        assertEquals(0, m.invoke(null, 3.0, 4.0))
    }

    @Test
    fun `fcmp one f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("one", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.ONE, Parameter("a", Type.F64, 0), Parameter("b", Type.F64, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("one", Double::class.java, Double::class.java)
        assertEquals(0, m.invoke(null, 3.0, 3.0))
        assertEquals(1, m.invoke(null, 3.0, 4.0))
    }

    @Test
    fun `fcmp olt f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("olt", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OLT, Parameter("a", Type.F64, 0), Parameter("b", Type.F64, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("olt", Double::class.java, Double::class.java)
        assertEquals(1, m.invoke(null, 2.0, 3.0))
        assertEquals(0, m.invoke(null, 3.0, 2.0))
    }

    @Test
    fun `fcmp ogt f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("ogt", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OGT, Parameter("a", Type.F64, 0), Parameter("b", Type.F64, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("ogt", Double::class.java, Double::class.java)
        assertEquals(1, m.invoke(null, 4.0, 3.0))
        assertEquals(0, m.invoke(null, 2.0, 3.0))
    }

    @Test
    fun `fcmp ole f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("ole", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OLE, Parameter("a", Type.F64, 0), Parameter("b", Type.F64, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("ole", Double::class.java, Double::class.java)
        assertEquals(1, m.invoke(null, 2.0, 3.0))
        assertEquals(1, m.invoke(null, 3.0, 3.0))
        assertEquals(0, m.invoke(null, 4.0, 3.0))
    }

    @Test
    fun `fcmp oge f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("oge", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OGE, Parameter("a", Type.F64, 0), Parameter("b", Type.F64, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("oge", Double::class.java, Double::class.java)
        assertEquals(1, m.invoke(null, 4.0, 3.0))
        assertEquals(1, m.invoke(null, 3.0, 3.0))
        assertEquals(0, m.invoke(null, 2.0, 3.0))
    }

    @Test
    fun `fcmp false f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("ffalse", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.FALSE, Parameter("a", Type.F32, 0), Parameter("b", Type.F32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("ffalse", Float::class.java, Float::class.java)
        assertEquals(0, m.invoke(null, 1.0f, 1.0f))
    }

    @Test
    fun `fcmp true f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("ftrue", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.TRUE, Parameter("a", Type.F32, 0), Parameter("b", Type.F32, 1))
        ir.ret(cmp)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("ftrue", Float::class.java, Float::class.java)
        assertEquals(1, m.invoke(null, 1.0f, 2.0f))
    }

    @Test
    fun `sitofp i32 to f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.sitofp(Parameter("x", Type.I32, 0), Type.F32))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Int::class.java)
        assertEquals(42.0f, m.invoke(null, 42))
        assertEquals(-5.0f, m.invoke(null, -5))
    }

    @Test
    fun `sitofp i32 to f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.sitofp(Parameter("x", Type.I32, 0), Type.F64))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Int::class.java)
        assertEquals(42.0, m.invoke(null, 42))
    }

    @Test
    fun `sitofp i64 to f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.I64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.sitofp(Parameter("x", Type.I64, 0), Type.F64))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Long::class.java)
        assertEquals(100.0, m.invoke(null, 100L))
    }

    @Test
    fun `sitofp i64 to f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.I64)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.sitofp(Parameter("x", Type.I64, 0), Type.F32))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Long::class.java)
        assertEquals(100.0f, m.invoke(null, 100L))
    }

    @Test
    fun `fptosi f32 to i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fptosi(Parameter("x", Type.F32, 0), Type.I32))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Float::class.java)
        assertEquals(42, m.invoke(null, 42.9f))
        assertEquals(-5, m.invoke(null, -5.1f))
    }

    @Test
    fun `fptosi f64 to i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fptosi(Parameter("x", Type.F64, 0), Type.I32))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Double::class.java)
        assertEquals(42, m.invoke(null, 42.9))
    }

    @Test
    fun `fptosi f64 to i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.F64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.fptosi(Parameter("x", Type.F64, 0), Type.I64))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Double::class.java)
        assertEquals(42L, m.invoke(null, 42.9))
    }

    @Test
    fun `fptosi f32 to i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.F32)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.fptosi(Parameter("x", Type.F32, 0), Type.I64))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Float::class.java)
        assertEquals(42L, m.invoke(null, 42.9f))
    }

    @Test
    fun `fptoui f32 to i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fptoui(Parameter("x", Type.F32, 0), Type.I32))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Float::class.java)
        assertEquals(42, m.invoke(null, 42.9f))
    }

    @Test
    fun `fptoui f64 to i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.F64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.fptoui(Parameter("x", Type.F64, 0), Type.I64))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Double::class.java)
        assertEquals(42L, m.invoke(null, 42.9))
    }

    @Test
    fun `fpext f32 to f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.F32)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.fpext(Parameter("x", Type.F32, 0), Type.F64))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Float::class.java)
        val result = m.invoke(null, 3.5f) as Double
        assertEquals(3.5, result, 0.001)
    }

    @Test
    fun `fptrunc f64 to f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.F64)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.fptrunc(Parameter("x", Type.F64, 0), Type.F32))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Double::class.java)
        assertEquals(3.5f, m.invoke(null, 3.5))
    }

    @Test
    fun `sext i32 to i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.sext(Parameter("x", Type.I32, 0), Type.I64))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Int::class.java)
        assertEquals(42L, m.invoke(null, 42))
        assertEquals(-1L, m.invoke(null, -1))
    }

    @Test
    fun `zext i32 to i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.zext(Parameter("x", Type.I32, 0), Type.I64))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Int::class.java)
        assertEquals(42L, m.invoke(null, 42))
        assertEquals(0xFFFFFFFFL, m.invoke(null, -1))
    }

    @Test
    fun `trunc i64 to i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.trunc(Parameter("x", Type.I64, 0), Type.I32))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Long::class.java)
        assertEquals(42, m.invoke(null, 42L))
        assertEquals(-1, m.invoke(null, 0xFFFFFFFFL))
    }

    @Test
    fun `select i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sel", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.I32, 0)
        val b = Parameter("b", Type.I32, 1)
        val cmp = ir.icmp(ICmpPredicate.SGT, a, b)
        val result = ir.select(cmp, a, b)
        ir.ret(result)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("sel", Int::class.java, Int::class.java)
        assertEquals(5, m.invoke(null, 5, 3))
        assertEquals(5, m.invoke(null, 3, 5))
    }

    @Test
    fun `select i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sel", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.I64, 0)
        val b = Parameter("b", Type.I64, 1)
        val cmp = ir.icmp(ICmpPredicate.SGT, a, b)
        val result = ir.select(cmp, a, b)
        ir.ret(result)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("sel", Long::class.java, Long::class.java)
        assertEquals(10L, m.invoke(null, 10L, 3L))
        assertEquals(10L, m.invoke(null, 3L, 10L))
    }

    @Test
    fun `select f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sel", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.F32, 0)
        val b = Parameter("b", Type.F32, 1)
        val cmp = ir.fcmp(FCmpPredicate.OGT, a, b)
        val result = ir.select(cmp, a, b)
        ir.ret(result)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("sel", Float::class.java, Float::class.java)
        assertEquals(5.0f, m.invoke(null, 5.0f, 3.0f))
        assertEquals(5.0f, m.invoke(null, 3.0f, 5.0f))
    }

    @Test
    fun `call internal function`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("double_", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val x = Parameter("x", Type.I32, 0)
        ir.ret(ir.add(x, x))
        ir.finalizeFunction()

        ir.createFunction("quadruple", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val x2 = Parameter("x", Type.I32, 0)
        val doubled = ir.call("double_", listOf(x2), Type.I32)!!
        ir.ret(ir.call("double_", listOf(doubled), Type.I32)!!)
        ir.finalizeFunction()

        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(20, clazz.getMethod("quadruple", Int::class.java).invoke(null, 5))
    }

    @Test
    fun `call with i64 args and return`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("addLong", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.add(Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1)))
        ir.finalizeFunction()

        ir.createFunction("test", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.call("addLong", listOf(Constant.I64(100), Constant.I64(200)), Type.I64)!!)
        ir.finalizeFunction()

        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(300L, clazz.getMethod("test").invoke(null))
    }

    @Test
    fun `call void function`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("noop", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        ir.createFunction("test", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.call("noop", emptyList(), Type.Void)
        ir.ret()
        ir.finalizeFunction()

        val clazz = generateAndLoad(ir.build(), cn)
        assertNull(clazz.getMethod("test").invoke(null))
    }

    @Test
    fun `ret void`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("doNothing", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertNull(clazz.getMethod("doNothing").invoke(null))
    }

    @Test
    fun `ret i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(99))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(99, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `ret i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(999999999999L))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(999999999999L, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `ret f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(Constant.F32(3.14f))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(3.14f, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `ret f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(3.14159))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(3.14159, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `constant i32 iconst_m1`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(-1))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(-1, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `constant i32 iconst_0 through iconst_5`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        for (i in 0..5) {
            ir.createFunction("get$i", emptyList(), Type.I32)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(i))
            ir.finalizeFunction()
        }
        val clazz = generateAndLoad(ir.build(), cn)
        for (i in 0..5) {
            assertEquals(i, clazz.getMethod("get$i").invoke(null))
        }
    }

    @Test
    fun `constant i32 bipush range`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(100))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(100, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `constant i32 sipush range`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(10000))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(10000, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `constant i32 ldc range`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(100000))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(100000, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `constant i64 zero and one`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("zero", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(0))
        ir.finalizeFunction()
        ir.createFunction("one", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(1))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(0L, clazz.getMethod("zero").invoke(null))
        assertEquals(1L, clazz.getMethod("one").invoke(null))
    }

    @Test
    fun `constant i64 ldc2w`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(123456789012L))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(123456789012L, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `constant f32 special values`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("zero", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(Constant.F32(0.0f))
        ir.finalizeFunction()
        ir.createFunction("one", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(Constant.F32(1.0f))
        ir.finalizeFunction()
        ir.createFunction("two", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(Constant.F32(2.0f))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(0.0f, clazz.getMethod("zero").invoke(null))
        assertEquals(1.0f, clazz.getMethod("one").invoke(null))
        assertEquals(2.0f, clazz.getMethod("two").invoke(null))
    }

    @Test
    fun `constant f32 ldc`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(Constant.F32(3.14f))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(3.14f, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `constant f64 special values`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("zero", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(0.0))
        ir.finalizeFunction()
        ir.createFunction("one", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(1.0))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(0.0, clazz.getMethod("zero").invoke(null))
        assertEquals(1.0, clazz.getMethod("one").invoke(null))
    }

    @Test
    fun `constant f64 ldc2w`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(2.71828))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(2.71828, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `constant i1 true`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I1(true))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(1, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `constant i1 false`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I1(false))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(0, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `switch with three cases`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sw", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val x = Parameter("x", Type.I32, 0)
        ir.switch(x, "default", listOf(
            Constant.I32(1) to "case1",
            Constant.I32(2) to "case2",
            Constant.I32(3) to "case3",
        ))

        ir.appendBlock("case1")
        ir.ret(Constant.I32(10))
        ir.appendBlock("case2")
        ir.ret(Constant.I32(20))
        ir.appendBlock("case3")
        ir.ret(Constant.I32(30))
        ir.appendBlock("default")
        ir.ret(Constant.I32(-1))
        ir.finalizeFunction()

        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("sw", Int::class.java)
        assertEquals(10, m.invoke(null, 1))
        assertEquals(20, m.invoke(null, 2))
        assertEquals(30, m.invoke(null, 3))
        assertEquals(-1, m.invoke(null, 99))
    }

    @Test
    fun `switch with i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sw", listOf(Param("x", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val x = Parameter("x", Type.I64, 0)
        ir.switch(x, "default", listOf(
            Constant.I64(100) to "case1",
            Constant.I64(200) to "case2",
        ))

        ir.appendBlock("case1")
        ir.ret(Constant.I32(1))
        ir.appendBlock("case2")
        ir.ret(Constant.I32(2))
        ir.appendBlock("default")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("sw", Long::class.java)
        assertEquals(1, m.invoke(null, 100L))
        assertEquals(2, m.invoke(null, 200L))
        assertEquals(0, m.invoke(null, 999L))
    }

    @Test
    fun `uitofp i32 to f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.uitofp(Parameter("x", Type.I32, 0), Type.F64))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Int::class.java)
        assertEquals(42.0, m.invoke(null, 42))
    }

    @Test
    fun `uitofp i32 to f32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.uitofp(Parameter("x", Type.I32, 0), Type.F32))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Int::class.java)
        assertEquals(42.0f, m.invoke(null, 42))
    }

    @Test
    fun `chained arithmetic i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("compute", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val x = Parameter("x", Type.I32, 0)
        val doubled = ir.mul(x, Constant.I32(2))
        val plusOne = ir.add(doubled, Constant.I32(1))
        ir.ret(plusOne)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("compute", Int::class.java)
        assertEquals(11, m.invoke(null, 5))
        assertEquals(1, m.invoke(null, 0))
    }

    @Test
    fun `chained arithmetic f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("compute", listOf(Param("x", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val x = Parameter("x", Type.F64, 0)
        val doubled = ir.fmul(x, Constant.F64(2.0))
        val plusHalf = ir.fadd(doubled, Constant.F64(0.5))
        ir.ret(plusHalf)
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("compute", Double::class.java)
        assertEquals(10.5, m.invoke(null, 5.0))
    }

    @Test
    fun `multiple i32 parameters`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sum3", listOf(
            Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.I32, 0)
        val b = Parameter("b", Type.I32, 1)
        val c = Parameter("c", Type.I32, 2)
        val ab = ir.add(a, b)
        ir.ret(ir.add(ab, c))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("sum3", Int::class.java, Int::class.java, Int::class.java)
        assertEquals(6, m.invoke(null, 1, 2, 3))
    }

    @Test
    fun `mixed type parameters`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("combine", listOf(
            Param("i", Type.I32), Param("l", Type.I64), Param("f", Type.F32), Param("d", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val i = Parameter("i", Type.I32, 0)
        val l = Parameter("l", Type.I64, 1)
        val f = Parameter("f", Type.F32, 2)
        val d = Parameter("d", Type.F64, 3)
        val iAsD = ir.sitofp(i, Type.F64)
        val lAsD = ir.sitofp(l, Type.F64)
        val fAsD = ir.fpext(f, Type.F64)
        val sum1 = ir.fadd(iAsD, lAsD)
        val sum2 = ir.fadd(sum1, fAsD)
        ir.ret(ir.fadd(sum2, d))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("combine", Int::class.java, Long::class.java, Float::class.java, Double::class.java)
        val result = m.invoke(null, 1, 2L, 3.0f, 4.0) as Double
        assertEquals(10.0, result, 0.001)
    }

    @Test
    fun `negative constant i32`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(-128))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(-128, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `negative constant i64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(-100L))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(-100L, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `add with constant operands`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(Constant.I32(10), Constant.I32(20)))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(30, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `fadd with constant operands f64`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.fadd(Constant.F64(1.5), Constant.F64(2.5)))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(4.0, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `mul i32 overflow wraps`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("mul", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.mul(Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1)))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("mul", Int::class.java, Int::class.java)
        val result = m.invoke(null, Int.MAX_VALUE, 2) as Int
        assertEquals(Int.MAX_VALUE * 2, result)
    }

    @Test
    fun `select with constant condition true`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sel", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.select(Constant.I1(true), Constant.I32(10), Constant.I32(20)))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(10, clazz.getMethod("sel").invoke(null))
    }

    @Test
    fun `select with constant condition false`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("sel", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.select(Constant.I1(false), Constant.I32(10), Constant.I32(20)))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(20, clazz.getMethod("sel").invoke(null))
    }

    @Test
    fun `call with f32 args and return`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("addF", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.fadd(Parameter("a", Type.F32, 0), Parameter("b", Type.F32, 1)))
        ir.finalizeFunction()

        ir.createFunction("test", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.call("addF", listOf(Constant.F32(1.5f), Constant.F32(2.5f)), Type.F32)!!)
        ir.finalizeFunction()

        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(4.0f, clazz.getMethod("test").invoke(null))
    }

    @Test
    fun `call with f64 args and return`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("addD", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.fadd(Parameter("a", Type.F64, 0), Parameter("b", Type.F64, 1)))
        ir.finalizeFunction()

        ir.createFunction("test", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.call("addD", listOf(Constant.F64(1.5), Constant.F64(2.5)), Type.F64)!!)
        ir.finalizeFunction()

        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(4.0, clazz.getMethod("test").invoke(null))
    }

    @Test
    fun `sext i32 to i64 preserves negative`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.sext(Parameter("x", Type.I32, 0), Type.I64))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Int::class.java)
        assertEquals(-100L, m.invoke(null, -100))
    }

    @Test
    fun `zext i32 to i64 zero extends`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.zext(Parameter("x", Type.I32, 0), Type.I64))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        val m = clazz.getMethod("conv", Int::class.java)
        // -1 as unsigned = 0xFFFFFFFF = 4294967295
        assertEquals(4294967295L, m.invoke(null, -1))
    }

    @Test
    fun `i32 constant negative sipush`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(-1000))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(-1000, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `i32 max value`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(Int.MAX_VALUE))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(Int.MAX_VALUE, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `i32 min value`() {
        val cn = nextClassName()
        val ir = ModuleBuilder(cn, Target.jvm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(Int.MIN_VALUE))
        ir.finalizeFunction()
        val clazz = generateAndLoad(ir.build(), cn)
        assertEquals(Int.MIN_VALUE, clazz.getMethod("get").invoke(null))
    }
}
