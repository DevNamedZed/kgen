package org.kgen.target.wasm.codegen

import org.kgen.target.wasm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.codegen.*
import org.kgen.ir.target.Target

class WasmCodeGenComprehensiveTest {

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

    private fun generateWasm(block: (IrBuilder) -> Unit): ByteArray {
        val ir = IrBuilder("test", Target.wasm())
        block(ir)
        return WasmCodeGenerator().generate(ir.build())
    }

    // I32 arithmetic

    @Test
    fun `i32 add`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.add(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 sub`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.sub(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 mul`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.mul(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 sdiv`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.sdiv(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 udiv`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.udiv(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 srem`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.srem(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 urem`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.urem(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 add with constant`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.add(p[0], Constant.I32(42)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 sub with constant`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.sub(p[0], Constant.I32(1)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 mul with constant`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.mul(p[0], Constant.I32(3)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 neg`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.neg(p[0]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 not`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.not(p[0]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // I64 arithmetic

    @Test
    fun `i64 add`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.add(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 sub`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.sub(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 mul`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.mul(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 sdiv`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.sdiv(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 udiv`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.udiv(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 srem`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.srem(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 urem`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.urem(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 add with constant`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.add(p[0], Constant.I64(100L)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 neg`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.neg(p[0]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 not`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.not(p[0]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // F32 arithmetic

    @Test
    fun `f32 add`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            ir.appendBlock("entry")
            ir.ret(ir.fadd(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 sub`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            ir.appendBlock("entry")
            ir.ret(ir.fsub(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 mul`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            ir.appendBlock("entry")
            ir.ret(ir.fmul(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 div`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            ir.appendBlock("entry")
            ir.ret(ir.fdiv(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 neg`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32)), Type.F32)
            ir.appendBlock("entry")
            ir.ret(ir.fneg(p[0]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 add with constant`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32)), Type.F32)
            ir.appendBlock("entry")
            ir.ret(ir.fadd(p[0], Constant.F32(1.5f)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 chain add sub mul div`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            ir.appendBlock("entry")
            val sum = ir.fadd(p[0], p[1])
            val diff = ir.fsub(sum, Constant.F32(1.0f))
            val prod = ir.fmul(diff, Constant.F32(2.0f))
            ir.ret(ir.fdiv(prod, p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // F64 arithmetic

    @Test
    fun `f64 add`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            ir.appendBlock("entry")
            ir.ret(ir.fadd(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f64 sub`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            ir.appendBlock("entry")
            ir.ret(ir.fsub(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f64 mul`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            ir.appendBlock("entry")
            ir.ret(ir.fmul(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f64 div`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            ir.appendBlock("entry")
            ir.ret(ir.fdiv(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f64 neg`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64)), Type.F64)
            ir.appendBlock("entry")
            ir.ret(ir.fneg(p[0]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f64 add with constant`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64)), Type.F64)
            ir.appendBlock("entry")
            ir.ret(ir.fadd(p[0], Constant.F64(3.14159)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f64 chain add sub mul div`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            ir.appendBlock("entry")
            val sum = ir.fadd(p[0], p[1])
            val diff = ir.fsub(sum, Constant.F64(1.0))
            val prod = ir.fmul(diff, Constant.F64(2.0))
            ir.ret(ir.fdiv(prod, p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // Bitwise operations I32

    @Test
    fun `i32 and`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.and(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 or`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.or(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 xor`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.xor(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 shl`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.shl(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 lshr`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.lshr(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 ashr`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.ashr(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 and with constant mask`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.and(p[0], Constant.I32(0xFF)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 shl with constant shift`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.shl(p[0], Constant.I32(4)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // Bitwise operations I64

    @Test
    fun `i64 and`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.and(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 or`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.or(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 xor`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.xor(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 shl`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.shl(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 lshr`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.lshr(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 ashr`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.ashr(p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // ICmp I32 predicates

    @Test
    fun `i32 icmp EQ`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.EQ, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 icmp NE`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.NE, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 icmp SLT`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.SLT, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 icmp SLE`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.SLE, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 icmp SGT`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.SGT, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 icmp SGE`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.SGE, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 icmp ULT`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.ULT, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 icmp ULE`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.ULE, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 icmp UGT`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.UGT, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 icmp UGE`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.UGE, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // ICmp I64 predicates

    @Test
    fun `i64 icmp EQ`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.EQ, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 icmp NE`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.NE, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 icmp SLT`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.SLT, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 icmp SGT`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.SGT, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 icmp ULT`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.ULT, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 icmp UGT`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.UGT, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // FCmp F32 predicates

    @Test
    fun `f32 fcmp OEQ`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.OEQ, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 fcmp ONE`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.ONE, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 fcmp OLT`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.OLT, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 fcmp OLE`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.OLE, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 fcmp OGT`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.OGT, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 fcmp OGE`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.OGE, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 fcmp UEQ`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.UEQ, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 fcmp UNE`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.UNE, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 fcmp FALSE`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.FALSE, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 fcmp TRUE`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.TRUE, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 fcmp ORD`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.ORD, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f32 fcmp UNO`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.UNO, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // FCmp F64 predicates

    @Test
    fun `f64 fcmp OEQ`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.OEQ, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f64 fcmp OLT`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.OLT, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f64 fcmp OGT`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.OGT, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f64 fcmp ORD`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.ORD, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f64 fcmp UNO`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.UNO, p[0], p[1])
            ir.ret(ir.select(cmp, Constant.I32(1), Constant.I32(0)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // Conversion operations

    @Test
    fun `zext i32 to i64`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.zext(p[0], Type.I64))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `sext i32 to i64`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.sext(p[0], Type.I64))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `trunc i64 to i32`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.trunc(p[0], Type.I32))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `sitofp i32 to f32`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.F32)
            ir.appendBlock("entry")
            ir.ret(ir.sitofp(p[0], Type.F32))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `sitofp i32 to f64`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.F64)
            ir.appendBlock("entry")
            ir.ret(ir.sitofp(p[0], Type.F64))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `sitofp i64 to f32`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64)), Type.F32)
            ir.appendBlock("entry")
            ir.ret(ir.sitofp(p[0], Type.F32))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `sitofp i64 to f64`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64)), Type.F64)
            ir.appendBlock("entry")
            ir.ret(ir.sitofp(p[0], Type.F64))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `uitofp i32 to f32`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.F32)
            ir.appendBlock("entry")
            ir.ret(ir.uitofp(p[0], Type.F32))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `uitofp i32 to f64`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.F64)
            ir.appendBlock("entry")
            ir.ret(ir.uitofp(p[0], Type.F64))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `uitofp i64 to f32`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64)), Type.F32)
            ir.appendBlock("entry")
            ir.ret(ir.uitofp(p[0], Type.F32))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `uitofp i64 to f64`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64)), Type.F64)
            ir.appendBlock("entry")
            ir.ret(ir.uitofp(p[0], Type.F64))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `fptosi f32 to i32`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.fptosi(p[0], Type.I32))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `fptosi f64 to i32`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.fptosi(p[0], Type.I32))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `fptosi f32 to i64`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.fptosi(p[0], Type.I64))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `fptosi f64 to i64`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.fptosi(p[0], Type.I64))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `fptoui f32 to i32`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.fptoui(p[0], Type.I32))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `fptoui f64 to i32`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.fptoui(p[0], Type.I32))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `fptoui f32 to i64`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.fptoui(p[0], Type.I64))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `fptoui f64 to i64`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64)), Type.I64)
            ir.appendBlock("entry")
            ir.ret(ir.fptoui(p[0], Type.I64))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `fpext f32 to f64`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32)), Type.F64)
            ir.appendBlock("entry")
            ir.ret(ir.fpext(p[0], Type.F64))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `fptrunc f64 to f32`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64)), Type.F32)
            ir.appendBlock("entry")
            ir.ret(ir.fptrunc(p[0], Type.F32))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // Select

    @Test
    fun `select i32 based on icmp`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.SGT, p[0], p[1])
            ir.ret(ir.select(cmp, p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `select i64 based on icmp`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.SGT, p[0], p[1])
            ir.ret(ir.select(cmp, p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `select f32 based on fcmp`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.OGT, p[0], p[1])
            ir.ret(ir.select(cmp, p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `select f64 based on fcmp`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            ir.appendBlock("entry")
            val cmp = ir.fcmp(FCmpPredicate.OGT, p[0], p[1])
            ir.ret(ir.select(cmp, p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `select with boolean constant condition`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("f", emptyList(), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.select(Constant.I1(true), Constant.I32(10), Constant.I32(20)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // Call

    @Test
    fun `call defined function with no args`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("helper", emptyList(), Type.I32)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(99))
            ir.finalizeFunction()

            ir.createFunction("main", emptyList(), Type.I32)
            ir.appendBlock("entry")
            val r = ir.call("helper", emptyList(), Type.I32)
            ir.ret(r!!)
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `call defined function with multiple args`() {
        val wasm = generateWasm { ir ->
            val hp = ir.createFunction("add3", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.add(ir.add(hp[0], hp[1]), hp[2]))
            ir.finalizeFunction()

            ir.createFunction("main", emptyList(), Type.I32)
            ir.appendBlock("entry")
            val r = ir.call("add3", listOf(Constant.I32(1), Constant.I32(2), Constant.I32(3)), Type.I32)
            ir.ret(r!!)
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `call imported function void return`() {
        val wasm = generateWasm { ir ->
            ir.declareFunction("log", listOf(Param("v", Type.I32)), Type.Void)
            ir.createFunction("main", emptyList(), Type.Void)
            ir.appendBlock("entry")
            ir.call("log", listOf(Constant.I32(42)), Type.Void)
            ir.ret()
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `call imported function with return value`() {
        val wasm = generateWasm { ir ->
            ir.declareFunction("getVal", emptyList(), Type.I32)
            ir.createFunction("main", emptyList(), Type.I32)
            ir.appendBlock("entry")
            val v = ir.call("getVal", emptyList(), Type.I32)
            ir.ret(v!!)
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `call with result used in arithmetic`() {
        val wasm = generateWasm { ir ->
            ir.declareFunction("getVal", emptyList(), Type.I32)
            ir.createFunction("main", emptyList(), Type.I32)
            ir.appendBlock("entry")
            val v = ir.call("getVal", emptyList(), Type.I32)
            val doubled = ir.mul(v!!, Constant.I32(2))
            ir.ret(ir.add(doubled, Constant.I32(1)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `multiple calls in sequence`() {
        val wasm = generateWasm { ir ->
            ir.declareFunction("getA", emptyList(), Type.I32)
            ir.declareFunction("getB", emptyList(), Type.I32)
            ir.createFunction("main", emptyList(), Type.I32)
            ir.appendBlock("entry")
            val a = ir.call("getA", emptyList(), Type.I32)
            val b = ir.call("getB", emptyList(), Type.I32)
            ir.ret(ir.add(a!!, b!!))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // Multiple functions

    @Test
    fun `five functions in one module`() {
        val wasm = generateWasm { ir ->
            for (i in 0 until 5) {
                val p = ir.createFunction("fn$i", listOf(Param("x", Type.I32)), Type.I32)
                ir.appendBlock("entry")
                ir.ret(ir.add(p[0], Constant.I32(i)))
                ir.finalizeFunction()
            }
        }
        wasmMagic(wasm)
    }

    @Test
    fun `ten functions with mixed types`() {
        val wasm = generateWasm { ir ->
            for (i in 0 until 5) {
                val p = ir.createFunction("intFn$i", listOf(Param("x", Type.I32)), Type.I32)
                ir.appendBlock("entry")
                ir.ret(ir.add(p[0], Constant.I32(i)))
                ir.finalizeFunction()
            }
            for (i in 0 until 5) {
                val p = ir.createFunction("floatFn$i", listOf(Param("x", Type.F64)), Type.F64)
                ir.appendBlock("entry")
                ir.ret(ir.fadd(p[0], Constant.F64(i.toDouble())))
                ir.finalizeFunction()
            }
        }
        wasmMagic(wasm)
    }

    // Imports and exports

    @Test
    fun `multiple imports from different modules`() {
        val wasm = generateWasm { ir ->
            ir.declareFunction("log_i32", listOf(Param("v", Type.I32)), Type.Void)
            ir.declareFunction("log_f64", listOf(Param("v", Type.F64)), Type.Void)
            ir.declareFunction("read_input", emptyList(), Type.I32)
            ir.createFunction("main", emptyList(), Type.Void)
            ir.appendBlock("entry")
            val v = ir.call("read_input", emptyList(), Type.I32)
            ir.call("log_i32", listOf(v!!), Type.Void)
            ir.call("log_f64", listOf(Constant.F64(3.14)), Type.Void)
            ir.ret()
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `internal linkage function not exported`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("internal_helper", emptyList(), Type.I32, linkage = Linkage.INTERNAL)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(7))
            ir.finalizeFunction()

            ir.createFunction("public_fn", emptyList(), Type.I32)
            ir.appendBlock("entry")
            val r = ir.call("internal_helper", emptyList(), Type.I32)
            ir.ret(r!!)
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `all internal functions`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("a", emptyList(), Type.I32, linkage = Linkage.INTERNAL)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(1))
            ir.finalizeFunction()

            ir.createFunction("b", emptyList(), Type.I32, linkage = Linkage.INTERNAL)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(2))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // Void functions

    @Test
    fun `void function with no args`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("noop", emptyList(), Type.Void)
            ir.appendBlock("entry")
            ir.ret()
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `void function with side effect call`() {
        val wasm = generateWasm { ir ->
            ir.declareFunction("log", listOf(Param("v", Type.I32)), Type.Void)
            ir.createFunction("main", emptyList(), Type.Void)
            ir.appendBlock("entry")
            ir.call("log", listOf(Constant.I32(1)), Type.Void)
            ir.call("log", listOf(Constant.I32(2)), Type.Void)
            ir.call("log", listOf(Constant.I32(3)), Type.Void)
            ir.ret()
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // Constants

    @Test
    fun `return i32 constant zero`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("f", emptyList(), Type.I32)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `return i32 constant negative`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("f", emptyList(), Type.I32)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(-1))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `return i32 constant max`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("f", emptyList(), Type.I32)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(Int.MAX_VALUE))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `return i32 constant min`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("f", emptyList(), Type.I32)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(Int.MIN_VALUE))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `return i64 constant large`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("f", emptyList(), Type.I64)
            ir.appendBlock("entry")
            ir.ret(Constant.I64(Long.MAX_VALUE))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `return f32 constant zero`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("f", emptyList(), Type.F32)
            ir.appendBlock("entry")
            ir.ret(Constant.F32(0.0f))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `return f64 constant zero`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("f", emptyList(), Type.F64)
            ir.appendBlock("entry")
            ir.ret(Constant.F64(0.0))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `return f64 constant negative`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("f", emptyList(), Type.F64)
            ir.appendBlock("entry")
            ir.ret(Constant.F64(-273.15))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // Complex expression chains

    @Test
    fun `chained i32 arithmetic expression`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val sum = ir.add(p[0], p[1])
            val prod = ir.mul(sum, p[2])
            val anded = ir.and(prod, Constant.I32(0xFFFF))
            val shifted = ir.shl(anded, Constant.I32(2))
            ir.ret(ir.or(shifted, Constant.I32(3)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `chained i64 arithmetic expression`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            val sum = ir.add(p[0], p[1])
            val diff = ir.sub(sum, Constant.I64(1L))
            val prod = ir.mul(diff, Constant.I64(3L))
            val xored = ir.xor(prod, p[1])
            ir.ret(ir.and(xored, Constant.I64(0xFFFFL)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `mixed integer and float conversion chain`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.F64)
            ir.appendBlock("entry")
            val f32val = ir.sitofp(p[0], Type.F32)
            val f64val = ir.fpext(f32val, Type.F64)
            ir.ret(ir.fadd(f64val, Constant.F64(0.5)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `conversion round trip i32 to f64 to i32`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val asFloat = ir.sitofp(p[0], Type.F64)
            val doubled = ir.fmul(asFloat, Constant.F64(2.0))
            ir.ret(ir.fptosi(doubled, Type.I32))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // Determinism

    @Test
    fun `deterministic output for identical IR`() {
        fun buildModule(): ByteArray {
            val ir = IrBuilder("test", Target.wasm())
            val p = ir.createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val sum = ir.add(p[0], p[1])
            val prod = ir.mul(sum, Constant.I32(2))
            ir.ret(ir.sub(prod, Constant.I32(1)))
            ir.finalizeFunction()
            return WasmCodeGenerator().generate(ir.build())
        }
        assertArrayEquals(buildModule(), buildModule())
    }

    @Test
    fun `deterministic output with imports`() {
        fun buildModule(): ByteArray {
            val ir = IrBuilder("test", Target.wasm())
            ir.declareFunction("ext", listOf(Param("v", Type.I32)), Type.I32)
            ir.createFunction("main", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val r = ir.call("ext", listOf(ir.param(0)), Type.I32)
            ir.ret(r!!)
            ir.finalizeFunction()
            return WasmCodeGenerator().generate(ir.build())
        }
        assertArrayEquals(buildModule(), buildModule())
    }

    // Mixed parameter types

    @Test
    fun `function with i32 i64 f32 f64 params`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I64),
                Param("c", Type.F32), Param("d", Type.F64)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(p[0])
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `function with six i32 params`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", (0 until 6).map { Param("p$it", Type.I32) }, Type.I32)
            ir.appendBlock("entry")
            var acc: Value = p[0]
            for (i in 1 until 6) acc = ir.add(acc, p[i])
            ir.ret(acc)
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    // Edge cases

    @Test
    fun `function that only returns a parameter`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("identity", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(p[0])
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `function with no parameters returning constant`() {
        val wasm = generateWasm { ir ->
            ir.createFunction("fortyTwo", emptyList(), Type.I32)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(42))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `binary output size is reasonable`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.add(p[0], Constant.I32(1)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
        assertTrue(wasm.size < 1000, "Simple function should produce small WASM binary")
        assertTrue(wasm.size > 8, "Binary should be more than just the header")
    }

    @Test
    fun `i32 full arithmetic chain add sub mul sdiv srem`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val sum = ir.add(p[0], p[1])
            val diff = ir.sub(sum, p[1])
            val prod = ir.mul(diff, Constant.I32(3))
            val quot = ir.sdiv(prod, Constant.I32(2))
            ir.ret(ir.srem(quot, Constant.I32(7)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i64 full arithmetic chain add sub mul sdiv srem`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            val sum = ir.add(p[0], p[1])
            val diff = ir.sub(sum, p[1])
            val prod = ir.mul(diff, Constant.I64(3L))
            val quot = ir.sdiv(prod, Constant.I64(2L))
            ir.ret(ir.srem(quot, Constant.I64(7L)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 bitwise chain and or xor shl lshr ashr`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val v1 = ir.and(p[0], p[1])
            val v2 = ir.or(v1, Constant.I32(0xFF))
            val v3 = ir.xor(v2, p[1])
            val v4 = ir.shl(v3, Constant.I32(2))
            val v5 = ir.lshr(v4, Constant.I32(1))
            ir.ret(ir.ashr(v5, Constant.I32(3)))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `function calling another function that calls import`() {
        val wasm = generateWasm { ir ->
            ir.declareFunction("ext", listOf(Param("v", Type.I32)), Type.I32)

            val hp = ir.createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val r = ir.call("ext", listOf(hp[0]), Type.I32)
            ir.ret(ir.add(r!!, Constant.I32(1)))
            ir.finalizeFunction()

            val mp = ir.createFunction("main", listOf(Param("n", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val v = ir.call("helper", listOf(mp[0]), Type.I32)
            ir.ret(v!!)
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `twenty functions same signature deduplicates type`() {
        val wasm = generateWasm { ir ->
            for (i in 0 until 20) {
                val p = ir.createFunction("fn$i", listOf(Param("x", Type.I32)), Type.I32)
                ir.appendBlock("entry")
                ir.ret(ir.add(p[0], Constant.I32(i)))
                ir.finalizeFunction()
            }
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 max function using select`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.SGT, p[0], p[1])
            ir.ret(ir.select(cmp, p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 min function using select`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("min", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.SLT, p[0], p[1])
            ir.ret(ir.select(cmp, p[0], p[1]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 abs function using neg and select`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("abs", listOf(Param("a", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val negated = ir.neg(p[0])
            val isNeg = ir.icmp(ICmpPredicate.SLT, p[0], Constant.I32(0))
            ir.ret(ir.select(isNeg, negated, p[0]))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `i32 clamp using two selects`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("clamp", listOf(
                Param("v", Type.I32), Param("lo", Type.I32), Param("hi", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val tooLow = ir.icmp(ICmpPredicate.SLT, p[0], p[1])
            val clamped1 = ir.select(tooLow, p[1], p[0])
            val tooHigh = ir.icmp(ICmpPredicate.SGT, clamped1, p[2])
            ir.ret(ir.select(tooHigh, p[2], clamped1))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }

    @Test
    fun `f64 lerp function`() {
        val wasm = generateWasm { ir ->
            val p = ir.createFunction("lerp", listOf(
                Param("a", Type.F64), Param("b", Type.F64), Param("t", Type.F64)), Type.F64)
            ir.appendBlock("entry")
            val diff = ir.fsub(p[1], p[0])
            val scaled = ir.fmul(diff, p[2])
            ir.ret(ir.fadd(p[0], scaled))
            ir.finalizeFunction()
        }
        wasmMagic(wasm)
    }
}
