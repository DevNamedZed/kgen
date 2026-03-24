package org.kgen.target.wasm.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class WasmCodeGenInstructionTest {

    private fun wasmMagic(wasm: ByteArray) {
        assertTrue(wasm.size > 8, "WASM binary too small")
        assertEquals(0x00, wasm[0].toInt() and 0xFF)
        assertEquals(0x61, wasm[1].toInt() and 0xFF)
        assertEquals(0x73, wasm[2].toInt() and 0xFF)
        assertEquals(0x6D, wasm[3].toInt() and 0xFF)
        assertEquals(0x01, wasm[4].toInt() and 0xFF)
        assertEquals(0x00, wasm[5].toInt() and 0xFF)
        assertEquals(0x00, wasm[6].toInt() and 0xFF)
        assertEquals(0x00, wasm[7].toInt() and 0xFF)
    }

    private fun generateWasm(module: Module): ByteArray {
        val gen = WasmCodeGenerator()
        val wasm = gen.generate(module)
        wasmMagic(wasm)
        return wasm
    }

    private fun buildI32BinOp(op: (ModuleBuilder, Value, Value) -> Value): ByteArray {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("op", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(op(ir, params[0], params[1]))
        ir.finalizeFunction()
        return generateWasm(ir.build())
    }

    private fun buildI64BinOp(op: (ModuleBuilder, Value, Value) -> Value): ByteArray {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("op", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(op(ir, params[0], params[1]))
        ir.finalizeFunction()
        return generateWasm(ir.build())
    }

    private fun buildF32BinOp(op: (ModuleBuilder, Value, Value) -> Value): ByteArray {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("op", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(op(ir, params[0], params[1]))
        ir.finalizeFunction()
        return generateWasm(ir.build())
    }

    private fun buildF64BinOp(op: (ModuleBuilder, Value, Value) -> Value): ByteArray {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("op", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(op(ir, params[0], params[1]))
        ir.finalizeFunction()
        return generateWasm(ir.build())
    }

    @Test
    fun `add i32`() {
        buildI32BinOp { ir, a, b -> ir.add(a, b) }
    }

    @Test
    fun `add i64`() {
        buildI64BinOp { ir, a, b -> ir.add(a, b) }
    }

    @Test
    fun `sub i32`() {
        buildI32BinOp { ir, a, b -> ir.sub(a, b) }
    }

    @Test
    fun `sub i64`() {
        buildI64BinOp { ir, a, b -> ir.sub(a, b) }
    }

    @Test
    fun `mul i32`() {
        buildI32BinOp { ir, a, b -> ir.mul(a, b) }
    }

    @Test
    fun `mul i64`() {
        buildI64BinOp { ir, a, b -> ir.mul(a, b) }
    }

    @Test
    fun `sdiv i32`() {
        buildI32BinOp { ir, a, b -> ir.sdiv(a, b) }
    }

    @Test
    fun `sdiv i64`() {
        buildI64BinOp { ir, a, b -> ir.sdiv(a, b) }
    }

    @Test
    fun `udiv i32`() {
        buildI32BinOp { ir, a, b -> ir.udiv(a, b) }
    }

    @Test
    fun `udiv i64`() {
        buildI64BinOp { ir, a, b -> ir.udiv(a, b) }
    }

    @Test
    fun `srem i32`() {
        buildI32BinOp { ir, a, b -> ir.srem(a, b) }
    }

    @Test
    fun `srem i64`() {
        buildI64BinOp { ir, a, b -> ir.srem(a, b) }
    }

    @Test
    fun `urem i32`() {
        buildI32BinOp { ir, a, b -> ir.urem(a, b) }
    }

    @Test
    fun `urem i64`() {
        buildI64BinOp { ir, a, b -> ir.urem(a, b) }
    }

    @Test
    fun `and i32`() {
        buildI32BinOp { ir, a, b -> ir.and(a, b) }
    }

    @Test
    fun `and i64`() {
        buildI64BinOp { ir, a, b -> ir.and(a, b) }
    }

    @Test
    fun `or i32`() {
        buildI32BinOp { ir, a, b -> ir.or(a, b) }
    }

    @Test
    fun `or i64`() {
        buildI64BinOp { ir, a, b -> ir.or(a, b) }
    }

    @Test
    fun `xor i32`() {
        buildI32BinOp { ir, a, b -> ir.xor(a, b) }
    }

    @Test
    fun `xor i64`() {
        buildI64BinOp { ir, a, b -> ir.xor(a, b) }
    }

    @Test
    fun `shl i32`() {
        buildI32BinOp { ir, a, b -> ir.shl(a, b) }
    }

    @Test
    fun `shl i64`() {
        buildI64BinOp { ir, a, b -> ir.shl(a, b) }
    }

    @Test
    fun `lshr i32`() {
        buildI32BinOp { ir, a, b -> ir.lshr(a, b) }
    }

    @Test
    fun `lshr i64`() {
        buildI64BinOp { ir, a, b -> ir.lshr(a, b) }
    }

    @Test
    fun `ashr i32`() {
        buildI32BinOp { ir, a, b -> ir.ashr(a, b) }
    }

    @Test
    fun `ashr i64`() {
        buildI64BinOp { ir, a, b -> ir.ashr(a, b) }
    }

    @Test
    fun `fadd f32`() {
        buildF32BinOp { ir, a, b -> ir.fadd(a, b) }
    }

    @Test
    fun `fadd f64`() {
        buildF64BinOp { ir, a, b -> ir.fadd(a, b) }
    }

    @Test
    fun `fsub f32`() {
        buildF32BinOp { ir, a, b -> ir.fsub(a, b) }
    }

    @Test
    fun `fsub f64`() {
        buildF64BinOp { ir, a, b -> ir.fsub(a, b) }
    }

    @Test
    fun `fmul f32`() {
        buildF32BinOp { ir, a, b -> ir.fmul(a, b) }
    }

    @Test
    fun `fmul f64`() {
        buildF64BinOp { ir, a, b -> ir.fmul(a, b) }
    }

    @Test
    fun `fdiv f32`() {
        buildF32BinOp { ir, a, b -> ir.fdiv(a, b) }
    }

    @Test
    fun `fdiv f64`() {
        buildF64BinOp { ir, a, b -> ir.fdiv(a, b) }
    }

    @Test
    fun `neg i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("neg", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.neg(params[0]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `neg i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("neg", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.neg(params[0]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `not i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("bitnot", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.not(params[0]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `not i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("bitnot", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.not(params[0]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fneg f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("fneg", listOf(Param("x", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.fneg(params[0]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fneg f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("fneg", listOf(Param("x", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.fneg(params[0]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp eq i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("eq", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.EQ, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp ne i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ne", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.NE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp slt i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("slt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.SLT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp sle i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sle", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.SLE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp sgt i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sgt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.SGT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp sge i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sge", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.SGE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp ult i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ult", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.ULT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp ule i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ule", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.ULE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp ugt i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ugt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.UGT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp uge i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("uge", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.UGE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp eq i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("eq", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.EQ, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp ne i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ne", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.NE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp slt i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("slt", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.SLT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp sle i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sle", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.SLE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp sgt i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sgt", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.SGT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp sge i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sge", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.SGE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp ult i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ult", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.ULT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp ule i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ule", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.ULE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp ugt i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ugt", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.UGT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `icmp uge i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("uge", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.icmp(ICmpPredicate.UGE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp oeq f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("oeq", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.OEQ, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp one f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("one", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.ONE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp olt f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("olt", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.OLT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp ole f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ole", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.OLE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp ogt f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ogt", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.OGT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp oge f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("oge", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.OGE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp oeq f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("oeq", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.OEQ, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp one f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("one", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.ONE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp olt f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("olt", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.OLT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp ole f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ole", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.OLE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp ogt f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ogt", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.OGT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp oge f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("oge", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.OGE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp false f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ffalse", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.FALSE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp true f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ftrue", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.TRUE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp ord f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ord", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.ORD, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp uno f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("uno", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.UNO, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp ueq f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ueq", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.UEQ, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp une f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("une", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.UNE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp ult f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ult", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.ULT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp ule f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ule", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.ULE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp ugt f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("ugt", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.UGT, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fcmp uge f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("uge", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fcmp(FCmpPredicate.UGE, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `select i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sel", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
        ir.ret(ir.select(cmp, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `select i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sel", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
        ir.ret(ir.select(cmp, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `select f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sel", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OGT, params[0], params[1])
        ir.ret(ir.select(cmp, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `select f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sel", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OGT, params[0], params[1])
        ir.ret(ir.select(cmp, params[0], params[1]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `select with constant condition`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("sel", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.select(Constant.I1(true), Constant.I32(10), Constant.I32(20)))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `ret void`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("noop", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `ret i32 constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(42))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `ret i64 constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(999999999999L))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `ret f32 constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(Constant.F32(3.14f))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `ret f64 constant`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(2.71828))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `call internal function`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("double_", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(params[0], params[0]))
        ir.finalizeFunction()

        ir.createFunction("quadruple", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val x = Parameter("x", Type.I32, 0)
        val doubled = ir.call("double_", listOf(x), Type.I32)!!
        ir.ret(ir.call("double_", listOf(doubled), Type.I32)!!)
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `call void function`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.declareFunction("log", listOf(Param("v", Type.I32)), Type.Void)

        ir.createFunction("main", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.call("log", listOf(Constant.I32(42)), Type.Void)
        ir.ret()
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `call with i64 return`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("identity", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(params[0])
        ir.finalizeFunction()

        ir.createFunction("test", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.call("identity", listOf(Constant.I64(100)), Type.I64)!!)
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `call with f32 return`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("addF", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.fadd(params[0], params[1]))
        ir.finalizeFunction()

        ir.createFunction("main", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.call("addF", listOf(Constant.F32(1.5f), Constant.F32(2.5f)), Type.F32)!!)
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `call with f64 return`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("addD", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.fadd(params[0], params[1]))
        ir.finalizeFunction()

        ir.createFunction("main", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.call("addD", listOf(Constant.F64(1.5), Constant.F64(2.5)), Type.F64)!!)
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `call external import`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.declareFunction("ext", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)

        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.call("ext", listOf(Constant.I32(1), Constant.I32(2)), Type.I32)!!)
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `zext i32 to i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.zext(params[0], Type.I64))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `sext i32 to i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.sext(params[0], Type.I64))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `trunc i64 to i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.trunc(params[0], Type.I32))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `sitofp i32 to f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.sitofp(params[0], Type.F32))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `sitofp i32 to f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.sitofp(params[0], Type.F64))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `sitofp i64 to f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I64)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.sitofp(params[0], Type.F32))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `sitofp i64 to f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.sitofp(params[0], Type.F64))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `uitofp i32 to f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.uitofp(params[0], Type.F32))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `uitofp i32 to f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.uitofp(params[0], Type.F64))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `uitofp i64 to f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I64)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.uitofp(params[0], Type.F32))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `uitofp i64 to f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.uitofp(params[0], Type.F64))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fptosi f32 to i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fptosi(params[0], Type.I32))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fptosi f64 to i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fptosi(params[0], Type.I32))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fptosi f32 to i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.F32)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.fptosi(params[0], Type.I64))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fptosi f64 to i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.F64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.fptosi(params[0], Type.I64))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fptoui f32 to i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.F32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fptoui(params[0], Type.I32))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fptoui f64 to i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.fptoui(params[0], Type.I32))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fptoui f32 to i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.F32)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.fptoui(params[0], Type.I64))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fptoui f64 to i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.F64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.fptoui(params[0], Type.I64))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fpext f32 to f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.F32)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.fpext(params[0], Type.F64))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fptrunc f64 to f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.F64)), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.fptrunc(params[0], Type.F32))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant i32 zero`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant i32 positive`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(100000))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant i32 negative`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(-42))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant i64 zero`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(0))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant i64 large`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(123456789012L))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(Constant.F32(3.14f))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(2.71828))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant i1 true`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I1(true))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant i1 false`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I1(false))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `chained arithmetic i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("compute", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        val product = ir.mul(sum, Constant.I32(2))
        ir.ret(ir.sub(product, Constant.I32(1)))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `chained arithmetic f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("compute", listOf(Param("x", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val doubled = ir.fmul(params[0], Constant.F64(2.0))
        ir.ret(ir.fadd(doubled, Constant.F64(0.5)))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `multiple parameters`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("sum3", listOf(
            Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val ab = ir.add(params[0], params[1])
        ir.ret(ir.add(ab, params[2]))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `mixed type conversions chain`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.F64)
        ir.appendBlock("entry")
        val asF32 = ir.sitofp(params[0], Type.F32)
        ir.ret(ir.fpext(asF32, Type.F64))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `exported function`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("exported", listOf(Param("x", Type.I32)), Type.I32, Linkage.EXTERNAL)
        ir.appendBlock("entry")
        ir.ret(Parameter("x", Type.I32, 0))
        ir.finalizeFunction()
        val wasm = generateWasm(ir.build())
        assertTrue(wasm.size > 8)
    }

    @Test
    fun `add with constant operands i32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(Constant.I32(10), Constant.I32(20)))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `add with constant operands i64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.add(Constant.I64(10), Constant.I64(20)))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fadd with constant operands f32`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(ir.fadd(Constant.F32(1.5f), Constant.F32(2.5f)))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fadd with constant operands f64`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.fadd(Constant.F64(1.5), Constant.F64(2.5)))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `multiple functions in module`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("f1", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(1))
        ir.finalizeFunction()

        ir.createFunction("f2", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(2))
        ir.finalizeFunction()

        ir.createFunction("f3", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(3))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `neg with constant operand`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.neg(Constant.I32(42)))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `not with constant operand`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.not(Constant.I32(0)))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `fneg with constant operand`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.fneg(Constant.F64(3.14)))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `zext i32 to i64 noop case`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.zext(params[0], Type.I32))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `sext i32 to i32 noop case`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("conv", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.sext(params[0], Type.I32))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant i32 max value`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(Int.MAX_VALUE))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant i32 min value`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(Int.MIN_VALUE))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant i64 negative`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(-100L))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant f32 zero`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(Constant.F32(0.0f))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `constant f64 zero`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("get", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(0.0))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `call chain through multiple functions`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params1 = ir.createFunction("inc", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(params1[0], Constant.I32(1)))
        ir.finalizeFunction()

        val params2 = ir.createFunction("addTwo", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val once = ir.call("inc", listOf(params2[0]), Type.I32)!!
        ir.ret(ir.call("inc", listOf(once), Type.I32)!!)
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `complex expression with multiple ops`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("compute", listOf(
            Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        val diff = ir.sub(params[0], params[2])
        val product = ir.mul(sum, diff)
        ir.ret(product)
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `i64 arithmetic chain`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("compute", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val doubled = ir.mul(params[0], Constant.I64(2))
        val shifted = ir.shl(doubled, Constant.I64(1))
        ir.ret(ir.add(shifted, Constant.I64(1)))
        ir.finalizeFunction()
        generateWasm(ir.build())
    }

    @Test
    fun `f32 arithmetic chain`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("compute", listOf(Param("x", Type.F32), Param("y", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        val sum = ir.fadd(params[0], params[1])
        val product = ir.fmul(sum, Constant.F32(0.5f))
        ir.ret(product)
        ir.finalizeFunction()
        generateWasm(ir.build())
    }
}
