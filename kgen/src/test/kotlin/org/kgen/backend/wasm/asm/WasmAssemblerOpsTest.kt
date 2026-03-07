package org.kgen.backend.wasm.asm

import org.kgen.backend.wasm.*
import org.kgen.backend.wasm.WasmBlockType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for WASM assembler instruction operations: arithmetic, comparison,
 * conversion, and memory operations.
 */
class WasmAssemblerOpsTest {

    private fun assertValidWasm(bytes: ByteArray) {
        assertTrue(bytes.size >= 8, "Too short for a valid .wasm module")
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(0x61, bytes[1].toInt() and 0xFF)
        assertEquals(0x73, bytes[2].toInt() and 0xFF)
        assertEquals(0x6D, bytes[3].toInt() and 0xFF)
    }

    private fun assembleFunc(
        params: List<WasmValueType> = emptyList(),
        results: List<WasmValueType> = listOf(WasmValueType.I32),
        body: (WasmFunction, WasmAssembler) -> Unit
    ): ByteArray {
        val asm = WasmAssembler.create()
        asm.function("test", params, results, exported = true, body = body)
        return asm.assemble()
    }

    // ---- i32 arithmetic ----

    @Test
    fun i32Add() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32Sub() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Sub()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32Mul() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Mul()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32DivS() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32DivS()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32DivU() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32DivU()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32RemS() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32RemS()
        }
        assertValidWasm(wasm)
    }

    // ---- i32 bitwise ----

    @Test
    fun i32And() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32And()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32Or() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Or()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32Xor() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Xor()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32Shl() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Shl()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32ShrS() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32ShrS()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32ShrU() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32ShrU()
        }
        assertValidWasm(wasm)
    }

    // ---- i32 comparison ----

    @Test
    fun i32Eqz() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Eqz()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32Eq() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Eq()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32Ne() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Ne()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32LtS() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32LtS()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32GtS() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32GtS()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32LeS() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32LeS()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32GeS() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32GeS()
        }
        assertValidWasm(wasm)
    }

    // ---- i64 arithmetic ----

    @Test
    fun i64Add() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Add()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64Sub() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Sub()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64Mul() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Mul()
        }
        assertValidWasm(wasm)
    }

    // ---- f32 arithmetic ----

    @Test
    fun f32Add() {
        val wasm = assembleFunc(listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Add()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32Sub() {
        val wasm = assembleFunc(listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Sub()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32Mul() {
        val wasm = assembleFunc(listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Mul()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32Div() {
        val wasm = assembleFunc(listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Div()
        }
        assertValidWasm(wasm)
    }

    // ---- f64 arithmetic ----

    @Test
    fun f64Add() {
        val wasm = assembleFunc(listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Add()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64Sub() {
        val wasm = assembleFunc(listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Sub()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64Mul() {
        val wasm = assembleFunc(listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Mul()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64Div() {
        val wasm = assembleFunc(listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Div()
        }
        assertValidWasm(wasm)
    }

    // ---- Conversions ----

    @Test
    fun i32WrapI64() {
        val wasm = assembleFunc(listOf(WasmValueType.I64), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32WrapI64()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64ExtendI32S() {
        val wasm = assembleFunc(listOf(WasmValueType.I32), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64ExtendI32S()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64ExtendI32U() {
        val wasm = assembleFunc(listOf(WasmValueType.I32), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64ExtendI32U()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64ConvertI32S() {
        val wasm = assembleFunc(listOf(WasmValueType.I32), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64ConvertI32S()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32TruncF64S() {
        val wasm = assembleFunc(listOf(WasmValueType.F64), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32TruncF64S()
        }
        assertValidWasm(wasm)
    }

    // ---- Constants ----

    @Test
    fun i32Const() {
        val wasm = assembleFunc { _, a ->
            a.i32Const(42)
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32ConstNegative() {
        val wasm = assembleFunc { _, a ->
            a.i32Const(-1)
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64Const() {
        val wasm = assembleFunc(results = listOf(WasmValueType.I64)) { _, a ->
            a.i64Const(1234567890123L)
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32Const() {
        val wasm = assembleFunc(results = listOf(WasmValueType.F32)) { _, a ->
            a.f32Const(3.14f)
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64Const() {
        val wasm = assembleFunc(results = listOf(WasmValueType.F64)) { _, a ->
            a.f64Const(2.71828)
        }
        assertValidWasm(wasm)
    }

    // ---- Local variable operations ----

    @Test
    fun localTee() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            val tmp = a.declareLocal("tmp", WasmValueType.I32)
            a.localTee(tmp)
        }
        assertValidWasm(wasm)
    }

    // ---- Memory operations ----

    @Test
    fun i32Load() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("load", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Load(0, 0)
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun i32Store() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("store", listOf(WasmValueType.I32, WasmValueType.I32), emptyList(), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Store(0, 0)
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    // ---- Select ----

    @Test
    fun selectOp() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.localGet(fn.getParameter(2))
            a.select()
        }
        assertValidWasm(wasm)
    }

    // ---- Drop / Unreachable ----

    @Test
    fun dropOp() {
        val wasm = assembleFunc(results = emptyList()) { _, a ->
            a.i32Const(42)
            a.drop()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun unreachableOp() {
        val wasm = assembleFunc(results = emptyList()) { _, a ->
            a.unreachable()
        }
        assertValidWasm(wasm)
    }

    // ---- Unary operations ----

    @Test
    fun i32Clz() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Clz()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32Ctz() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Ctz()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32Popcnt() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Popcnt()
        }
        assertValidWasm(wasm)
    }

    // ---- Complex control flow ----

    @Test
    fun nestedIfElse() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(0)
            a.i32LtS()
            a.ifThenElse(WasmBlockType.I32,
                thenBody = {
                    a.i32Const(-1)
                },
                elseBody = {
                    a.localGet(fn.getParameter(0))
                    a.i32Const(0)
                    a.i32GtS()
                    a.ifThenElse(WasmBlockType.I32,
                        thenBody = { a.i32Const(1) },
                        elseBody = { a.i32Const(0) }
                    )
                }
            )
        }
        assertValidWasm(wasm)
    }

    @Test
    fun brTable() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            a.block(WasmBlockType.I32) { outer: WasmLabel ->
                a.block(WasmBlockType.Void) { case0: WasmLabel ->
                    a.block(WasmBlockType.Void) { case1: WasmLabel ->
                        a.localGet(fn.getParameter(0))
                        a.brTable(outer, case0, case1)
                    }
                    a.i32Const(10)
                    a.br(outer)
                }
                a.i32Const(20)
            }
        }
        assertValidWasm(wasm)
    }
}
