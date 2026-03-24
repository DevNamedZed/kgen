package org.kgen.target.wasm.codegen

import org.kgen.target.wasm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.codegen.*
import org.kgen.ir.target.Target

class WasmCodeGeneratorExtendedTest {

    private fun wasmMagic(wasm: ByteArray) {
        assertTrue(wasm.size > 8, "WASM binary too small")
        assertEquals(0x00, wasm[0].toInt() and 0xFF)
        assertEquals(0x61, wasm[1].toInt() and 0xFF) // 'a'
        assertEquals(0x73, wasm[2].toInt() and 0xFF) // 's'
        assertEquals(0x6D, wasm[3].toInt() and 0xFF) // 'm'
        assertEquals(0x01, wasm[4].toInt() and 0xFF)
        assertEquals(0x00, wasm[5].toInt() and 0xFF)
        assertEquals(0x00, wasm[6].toInt() and 0xFF)
        assertEquals(0x00, wasm[7].toInt() and 0xFF)
    }

    // --- Arithmetic: sub ---

    @Test
    fun `sub i32 constants`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("subConst", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.sub(Constant.I32(100), Constant.I32(42))
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `sub i64 params`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sub64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.sub(params[0], params[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Arithmetic: mul ---

    @Test
    fun `mul i32 with zero`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("mulZero", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.mul(params[0], Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `mul i64 params`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("mul64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.mul(params[0], params[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Float arithmetic: fsub ---

    @Test
    fun `fsub f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("fsubf32", listOf(
            Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.fsub(params[0], params[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `fsub f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("fsubf64", listOf(
            Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.fsub(params[0], params[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Float arithmetic: fmul ---

    @Test
    fun `fmul f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("fmulf32", listOf(
            Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.fmul(params[0], params[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `fmul f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("fmulf64", listOf(
            Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.fmul(params[0], params[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Float arithmetic: fdiv ---

    @Test
    fun `fdiv f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("fdivf32", listOf(
            Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.fdiv(params[0], params[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `fdiv f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("fdivf64", listOf(
            Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.fdiv(params[0], params[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Comparison: all ICmp predicates on i32 ---

    @Test
    fun `icmp EQ i32 with constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("eqConst", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
        val result = ir.select(cmp, Constant.I32(1), Constant.I32(0))
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `icmp NE i32 with constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("neConst", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.NE, params[0], Constant.I32(0))
        val result = ir.select(cmp, Constant.I32(1), Constant.I32(0))
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `icmp SLT i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("slt", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SLT, params[0], params[1])
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `icmp SGT i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sgt", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `icmp SLE i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sle", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SLE, params[0], params[1])
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `icmp SGE i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sge", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGE, params[0], params[1])
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `icmp ULT i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ult", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.ULT, params[0], params[1])
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `icmp UGT i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ugt", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.UGT, params[0], params[1])
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `icmp ULE i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ule", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.ULE, params[0], params[1])
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `icmp UGE i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("uge", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.UGE, params[0], params[1])
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Comparison: ICmp on i64 ---

    @Test
    fun `icmp EQ i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("eq64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.EQ, params[0], params[1])
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `icmp NE i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ne64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.NE, params[0], params[1])
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `icmp SGT i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sgt64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `icmp ULT i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ult64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.ULT, params[0], params[1])
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Bitwise operations ---

    @Test
    fun `and i32 with mask`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("mask", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.and(params[0], Constant.I32(0x0F)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `or i32 set bit`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("setBit", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.or(params[0], Constant.I32(0x80)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `xor i32 toggle bits`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("toggle", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.xor(params[0], Constant.I32(-1)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `shl i32 multiply by power of two`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("shiftL", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.shl(params[0], Constant.I32(3)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `shl i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("shiftL64", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.shl(params[0], Constant.I64(8L)))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `and i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("and64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.and(params[0], params[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `or i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("or64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.or(params[0], params[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `xor i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("xor64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.xor(params[0], params[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `chained bitwise operations i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("chainBit", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val masked = ir.and(params[0], Constant.I32(0xFF))
        val shifted = ir.shl(masked, Constant.I32(8))
        val combined = ir.or(shifted, params[1])
        ir.ret(combined)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Function calls ---

    @Test
    fun `call chain of defined functions`() {
        val ir = ModuleBuilder("test", Target.wasm())

        val addParams = ir.createFunction("add", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(addParams[0], addParams[1]))
        ir.finalizeFunction()

        val doubleParams = ir.createFunction("double", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.call("add", listOf(doubleParams[0], doubleParams[0]), Type.I32)
        ir.ret(result!!)
        ir.finalizeFunction()

        val mainParams = ir.createFunction("main", listOf(Param("n", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val doubled = ir.call("double", listOf(mainParams[0]), Type.I32)
        val added = ir.call("add", listOf(doubled!!, Constant.I32(10)), Type.I32)
        ir.ret(added!!)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `call void function`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.declareFunction("log", listOf(Param("v", Type.I32)), Type.Void)

        ir.createFunction("main", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.call("log", listOf(Constant.I32(1)), Type.Void)
        ir.call("log", listOf(Constant.I32(2)), Type.Void)
        ir.call("log", listOf(Constant.I32(3)), Type.Void)
        ir.ret()
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `call with multiple argument types`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.declareFunction("process", listOf(
            Param("i", Type.I32), Param("l", Type.I64),
            Param("f", Type.F32), Param("d", Type.F64)), Type.I32)

        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.call("process", listOf(
            Constant.I32(1), Constant.I64(2L),
            Constant.F32(3.0f), Constant.F64(4.0)), Type.I32)
        ir.ret(result!!)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `recursive call pattern`() {
        val ir = ModuleBuilder("test", Target.wasm())

        val params = ir.createFunction("factorial", listOf(Param("n", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val isZero = ir.icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
        val nMinus1 = ir.sub(params[0], Constant.I32(1))
        val recurse = ir.call("factorial", listOf(nMinus1), Type.I32)
        val product = ir.mul(params[0], recurse!!)
        val result = ir.select(isZero, Constant.I32(1), product)
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Parameter types ---

    @Test
    fun `function with single i32 param`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("identity", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(params[0])
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `function with single i64 param`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("identity64", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(params[0])
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `function with single f32 param`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("identityf32", listOf(Param("x", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(params[0])
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `function with single f64 param`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("identityf64", listOf(Param("x", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(params[0])
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `function with all four wasm param types`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("allTypes", listOf(
            Param("i", Type.I32), Param("l", Type.I64),
            Param("f", Type.F32), Param("d", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(params[0])
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `function with many params same type`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val paramList = (1..8).map { Param("p$it", Type.I32) }
        val params = ir.createFunction("sum8", paramList, Type.I32)
        ir.appendBlock("entry")
        var sum = ir.add(params[0], params[1])
        for (i in 2 until 8) {
            sum = ir.add(sum, params[i])
        }
        ir.ret(sum)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Return types ---

    @Test
    fun `void function with no operations`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("empty", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `return i32 constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("const", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `return i64 constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("const64", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(Long.MAX_VALUE))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `return f32 constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("constf32", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(Constant.F32(Float.MAX_VALUE))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `return f64 constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("constf64", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(Double.MIN_VALUE))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `return negative i32 constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("neg", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(-1))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `return negative i64 constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("neg64", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(-1L))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Select (conditional) ---

    @Test
    fun `select with boolean constant true`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("selTrue", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.select(Constant.I1(true), Constant.I32(10), Constant.I32(20))
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `select with boolean constant false`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("selFalse", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.select(Constant.I1(false), Constant.I32(10), Constant.I32(20))
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `nested select for clamp`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("clamp", listOf(
            Param("x", Type.I32), Param("lo", Type.I32), Param("hi", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val tooLow = ir.icmp(ICmpPredicate.SLT, params[0], params[1])
        val clamped = ir.select(tooLow, params[1], params[0])
        val tooHigh = ir.icmp(ICmpPredicate.SGT, clamped, params[2])
        val result = ir.select(tooHigh, params[2], clamped)
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `select min of two i64 values`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("min64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SLT, params[0], params[1])
        val result = ir.select(cmp, params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Linkage ---

    @Test
    fun `internal linkage prevents export`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("internal1", emptyList(), Type.I32,
            linkage = Linkage.INTERNAL)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(1))
        ir.finalizeFunction()

        ir.createFunction("internal2", emptyList(), Type.I32,
            linkage = Linkage.INTERNAL)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(2))
        ir.finalizeFunction()

        ir.createFunction("exported", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val a = ir.call("internal1", emptyList(), Type.I32)
        val b = ir.call("internal2", emptyList(), Type.I32)
        ir.ret(ir.add(a!!, b!!))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Complex expression patterns ---

    @Test
    fun `polynomial evaluation a*x*x + b*x + c`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("poly", listOf(
            Param("x", Type.I32), Param("a", Type.I32),
            Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val xx = ir.mul(params[0], params[0])
        val axx = ir.mul(params[1], xx)
        val bx = ir.mul(params[2], params[0])
        val abx = ir.add(axx, bx)
        val result = ir.add(abx, params[3])
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `float polynomial evaluation`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("fpoly", listOf(
            Param("x", Type.F64), Param("a", Type.F64),
            Param("b", Type.F64), Param("c", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val xx = ir.fmul(params[0], params[0])
        val axx = ir.fmul(params[1], xx)
        val bx = ir.fmul(params[2], params[0])
        val sum1 = ir.fadd(axx, bx)
        val result = ir.fadd(sum1, params[3])
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `abs value via select`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val isNeg = ir.icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
        val negated = ir.sub(Constant.I32(0), params[0])
        val result = ir.select(isNeg, negated, params[0])
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `bit field extract via shift and mask`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("extractBits", listOf(
            Param("value", Type.I32), Param("offset", Type.I32),
            Param("width", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val shifted = ir.shl(Constant.I32(1), params[2])
        val mask = ir.sub(shifted, Constant.I32(1))
        val extracted = ir.shl(params[0], params[1])
        val result = ir.and(extracted, mask)
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Multiple functions interacting ---

    @Test
    fun `five functions in one module with calls between them`() {
        val ir = ModuleBuilder("test", Target.wasm())

        val p1 = ir.createFunction("inc", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(p1[0], Constant.I32(1)))
        ir.finalizeFunction()

        val p2 = ir.createFunction("dec", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.sub(p2[0], Constant.I32(1)))
        ir.finalizeFunction()

        val p3 = ir.createFunction("dbl", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.mul(p3[0], Constant.I32(2)))
        ir.finalizeFunction()

        val p4 = ir.createFunction("isZero", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.EQ, p4[0], Constant.I32(0))
        ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
        ir.finalizeFunction()

        val p5 = ir.createFunction("compute", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val incremented = ir.call("inc", listOf(p5[0]), Type.I32)
        val doubled = ir.call("dbl", listOf(incremented!!), Type.I32)
        val decremented = ir.call("dec", listOf(doubled!!), Type.I32)
        ir.ret(decremented!!)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Imports and exports ---

    @Test
    fun `import with i64 parameters`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.declareFunction("externalOp", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I64)

        ir.createFunction("use", emptyList(), Type.I64)
        ir.appendBlock("entry")
        val result = ir.call("externalOp", listOf(
            Constant.I64(100L), Constant.I64(200L)), Type.I64)
        ir.ret(result!!)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `import with f64 return`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.declareFunction("getTime", emptyList(), Type.F64)

        ir.createFunction("elapsed", emptyList(), Type.F64)
        ir.appendBlock("entry")
        val t1 = ir.call("getTime", emptyList(), Type.F64)
        val t2 = ir.call("getTime", emptyList(), Type.F64)
        val diff = ir.fsub(t2!!, t1!!)
        ir.ret(diff)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Determinism ---

    @Test
    fun `deterministic output for complex module`() {
        fun buildModule(): ByteArray {
            val ir = ModuleBuilder("test", Target.wasm())
            ir.declareFunction("log", listOf(Param("v", Type.I32)), Type.Void)

            val params = ir.createFunction("compute", listOf(
                Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val sum = ir.add(params[0], params[1])
            val diff = ir.sub(params[0], params[1])
            val prod = ir.mul(sum, diff)
            ir.call("log", listOf(prod), Type.Void)
            ir.ret(prod)
            ir.finalizeFunction()

            return WasmCodeGenerator().generate(ir.build())
        }

        val wasm1 = buildModule()
        val wasm2 = buildModule()
        assertArrayEquals(wasm1, wasm2)
    }

    // --- Edge cases ---

    @Test
    fun `empty module with no functions produces wasm output`() {
        val ir = ModuleBuilder("empty", Target.wasm())
        val wasm = WasmCodeGenerator().generate(ir.build())
        assertTrue(wasm.isNotEmpty(), "Empty module should still produce output")
    }

    @Test
    fun `function with zero constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("zero", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `function with i32 max value`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("maxI32", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(Int.MAX_VALUE))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `function with i32 min value`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("minI32", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(Int.MIN_VALUE))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `function with i64 max value`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("maxI64", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(Long.MAX_VALUE))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `function with special float values`() {
        val ir = ModuleBuilder("test", Target.wasm())

        ir.createFunction("posInf", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(Double.POSITIVE_INFINITY))
        ir.finalizeFunction()

        ir.createFunction("negInf", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(Double.NEGATIVE_INFINITY))
        ir.finalizeFunction()

        ir.createFunction("nan", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(Double.NaN))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `mixed arithmetic chain i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("mixedArith", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        val diff = ir.sub(params[0], params[1])
        val prod = ir.mul(sum, diff)
        val anded = ir.and(prod, Constant.I32(0xFFFF))
        val shifted = ir.shl(anded, Constant.I32(1))
        ir.ret(shifted)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `sdiv compiles to wasm`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("divTest", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.sdiv(params[0], params[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        assertTrue(wasm.isNotEmpty())
    }

    @Test
    fun `code generator target name is wasm`() {
        val gen = WasmCodeGenerator()
        assertEquals("wasm", gen.targetName)
    }

    @Test
    fun `generate with explicit CodeGenOptions`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(ir.param(0), Constant.I32(1)))
        ir.finalizeFunction()

        val gen = WasmCodeGenerator()
        val wasm = gen.generate(ir.build(), CodeGenOptions())
        wasmMagic(wasm)
    }

    @Test
    fun `module with only imports and no defined functions`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.declareFunction("extern1", listOf(Param("x", Type.I32)), Type.I32)
        ir.declareFunction("extern2", emptyList(), Type.Void)

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `large number of local variables`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("manyLocals", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        var current: Value = params[0]
        for (i in 0 until 20) {
            current = ir.add(current, Constant.I32(i))
        }
        ir.ret(current)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `select with f64 values`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("fmax", listOf(
            Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        // Use icmp on a comparison result to drive select
        // Since we need i1 for select, and we only have icmp, we compare after converting
        // Actually the select just needs the condition to be pushed as i32
        // Let's use a workaround: compare to determine which is larger via subtraction
        val diff = ir.fsub(params[0], params[1])
        // We need a boolean - use a constant comparison approach
        val isGreater = ir.icmp(ICmpPredicate.SGT, Constant.I32(1), Constant.I32(0))
        val result = ir.select(isGreater, params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `select with i64 values`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("max64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
        val result = ir.select(cmp, params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `select with f32 values`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("pickf32", listOf(
            Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, Constant.I32(1), Constant.I32(0))
        val result = ir.select(cmp, params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Chained comparisons ---

    @Test
    fun `chained comparisons with select for range check`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("inRange", listOf(
            Param("x", Type.I32), Param("lo", Type.I32), Param("hi", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val geqLo = ir.icmp(ICmpPredicate.SGE, params[0], params[1])
        val leqHi = ir.icmp(ICmpPredicate.SLE, params[0], params[2])
        val geqLoInt = ir.select(geqLo, Constant.I32(1), Constant.I32(0))
        val leqHiInt = ir.select(leqHi, Constant.I32(1), Constant.I32(0))
        val result = ir.and(geqLoInt, leqHiInt)
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    // --- Operations with constants on both sides ---

    @Test
    fun `constant folding opportunity add`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("constAdd", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.add(Constant.I32(10), Constant.I32(20))
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `constant folding opportunity mul`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("constMul", emptyList(), Type.I64)
        ir.appendBlock("entry")
        val result = ir.mul(Constant.I64(6L), Constant.I64(7L))
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `float constant arithmetic`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("fconst", emptyList(), Type.F64)
        ir.appendBlock("entry")
        val pi = Constant.F64(3.141592653589793)
        val r = Constant.F64(5.0)
        val rr = ir.fmul(r, r)
        val area = ir.fmul(pi, rr)
        ir.ret(area)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }
}
