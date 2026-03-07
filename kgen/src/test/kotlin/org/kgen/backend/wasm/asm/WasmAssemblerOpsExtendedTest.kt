package org.kgen.backend.wasm.asm

import org.kgen.backend.wasm.*
import org.kgen.backend.wasm.WasmBlockType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WasmAssemblerOpsExtendedTest {

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

    // --- i32 remaining operations ---

    @Test
    fun i32RemU() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32RemU()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32Rotl() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Rotl()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32Rotr() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Rotr()
        }
        assertValidWasm(wasm)
    }

    // --- i32 unsigned comparisons ---

    @Test
    fun i32LtU() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32LtU()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32GtU() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32GtU()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32LeU() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32LeU()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32GeU() {
        val wasm = assembleFunc(listOf(WasmValueType.I32, WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32GeU()
        }
        assertValidWasm(wasm)
    }

    // --- i64 division and remainder ---

    @Test
    fun i64DivS() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64DivS()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64DivU() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64DivU()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64RemS() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64RemS()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64RemU() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64RemU()
        }
        assertValidWasm(wasm)
    }

    // --- i64 bitwise ---

    @Test
    fun i64And() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64And()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64Or() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Or()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64Xor() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Xor()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64Shl() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Shl()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64ShrS() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64ShrS()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64ShrU() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64ShrU()
        }
        assertValidWasm(wasm)
    }

    // --- i64 comparisons ---

    @Test
    fun i64Eqz() {
        val wasm = assembleFunc(listOf(WasmValueType.I64), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Eqz()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64Eq() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Eq()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64Ne() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Ne()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64LtS() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64LtS()
        }
        assertValidWasm(wasm)
    }

    // --- i64 unary ---

    @Test
    fun i64Clz() {
        val wasm = assembleFunc(listOf(WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Clz()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64Ctz() {
        val wasm = assembleFunc(listOf(WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Ctz()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64Popcnt() {
        val wasm = assembleFunc(listOf(WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Popcnt()
        }
        assertValidWasm(wasm)
    }

    // --- f32 comparison ---

    @Test
    fun f32Eq() {
        val wasm = assembleFunc(listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Eq()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32Ne() {
        val wasm = assembleFunc(listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Ne()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32Lt() {
        val wasm = assembleFunc(listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Lt()
        }
        assertValidWasm(wasm)
    }

    // --- f32 unary ---

    @Test
    fun f32Abs() {
        val wasm = assembleFunc(listOf(WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32Abs()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32Neg() {
        val wasm = assembleFunc(listOf(WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32Neg()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32Sqrt() {
        val wasm = assembleFunc(listOf(WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32Sqrt()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32Floor() {
        val wasm = assembleFunc(listOf(WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32Floor()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32Ceil() {
        val wasm = assembleFunc(listOf(WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32Ceil()
        }
        assertValidWasm(wasm)
    }

    // --- f64 comparison ---

    @Test
    fun f64Eq() {
        val wasm = assembleFunc(listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Eq()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64Ne() {
        val wasm = assembleFunc(listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Ne()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64Lt() {
        val wasm = assembleFunc(listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Lt()
        }
        assertValidWasm(wasm)
    }

    // --- f64 unary ---

    @Test
    fun f64Abs() {
        val wasm = assembleFunc(listOf(WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64Abs()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64Neg() {
        val wasm = assembleFunc(listOf(WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64Neg()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64Sqrt() {
        val wasm = assembleFunc(listOf(WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64Sqrt()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64Floor() {
        val wasm = assembleFunc(listOf(WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64Floor()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64Ceil() {
        val wasm = assembleFunc(listOf(WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64Ceil()
        }
        assertValidWasm(wasm)
    }

    // --- f64 min/max ---

    @Test
    fun f64Min() {
        val wasm = assembleFunc(listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Min()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64Max() {
        val wasm = assembleFunc(listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Max()
        }
        assertValidWasm(wasm)
    }

    // --- More conversions ---

    @Test
    fun f32ConvertI32S() {
        val wasm = assembleFunc(listOf(WasmValueType.I32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32ConvertI32S()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32ConvertI32U() {
        val wasm = assembleFunc(listOf(WasmValueType.I32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32ConvertI32U()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64ConvertI64S() {
        val wasm = assembleFunc(listOf(WasmValueType.I64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64ConvertI64S()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64PromoteF32() {
        val wasm = assembleFunc(listOf(WasmValueType.F32), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64PromoteF32()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32DemoteF64() {
        val wasm = assembleFunc(listOf(WasmValueType.F64), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32DemoteF64()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32TruncF32S() {
        val wasm = assembleFunc(listOf(WasmValueType.F32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32TruncF32S()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64TruncF64S() {
        val wasm = assembleFunc(listOf(WasmValueType.F64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64TruncF64S()
        }
        assertValidWasm(wasm)
    }

    // --- Reinterpret ---

    @Test
    fun i32ReinterpretF32() {
        val wasm = assembleFunc(listOf(WasmValueType.F32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32ReinterpretF32()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32ReinterpretI32() {
        val wasm = assembleFunc(listOf(WasmValueType.I32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32ReinterpretI32()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64ReinterpretF64() {
        val wasm = assembleFunc(listOf(WasmValueType.F64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64ReinterpretF64()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64ReinterpretI64() {
        val wasm = assembleFunc(listOf(WasmValueType.I64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64ReinterpretI64()
        }
        assertValidWasm(wasm)
    }

    // --- Memory operations ---

    @Test
    fun i64Load() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("load64", listOf(WasmValueType.I32), listOf(WasmValueType.I64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Load(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun i64Store() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("store64", listOf(WasmValueType.I32, WasmValueType.I64), emptyList(), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Store(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun i32Load8S() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("load8s", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Load8S(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun i32Load8U() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("load8u", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Load8U(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun i32Load16S() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("load16s", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Load16S(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun i32Load16U() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("load16u", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Load16U(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun i32Store8() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("store8", listOf(WasmValueType.I32, WasmValueType.I32), emptyList(), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Store8(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun i32Store16() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("store16", listOf(WasmValueType.I32, WasmValueType.I32), emptyList(), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Store16(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    // --- f32/f64 load/store ---

    @Test
    fun f32Load() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("fload32", listOf(WasmValueType.I32), listOf(WasmValueType.F32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32Load(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun f64Load() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("fload64", listOf(WasmValueType.I32), listOf(WasmValueType.F64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64Load(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun f32Store() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("fstore32", listOf(WasmValueType.I32, WasmValueType.F32), emptyList(), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Store(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun f64Store() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("fstore64", listOf(WasmValueType.I32, WasmValueType.F64), emptyList(), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Store(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    // --- Memory size / grow ---

    @Test
    fun memorySize() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("getsize", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.memorySize(0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun memoryGrow() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("grow", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.memoryGrow(0)
        }
        assertValidWasm(asm.assemble())
    }

    // --- Constants edge cases ---

    @Test
    fun i32ConstZero() {
        val wasm = assembleFunc { _, a -> a.i32Const(0) }
        assertValidWasm(wasm)
    }

    @Test
    fun i32ConstMaxValue() {
        val wasm = assembleFunc { _, a -> a.i32Const(Int.MAX_VALUE) }
        assertValidWasm(wasm)
    }

    @Test
    fun i32ConstMinValue() {
        val wasm = assembleFunc { _, a -> a.i32Const(Int.MIN_VALUE) }
        assertValidWasm(wasm)
    }

    @Test
    fun i64ConstZero() {
        val wasm = assembleFunc(results = listOf(WasmValueType.I64)) { _, a -> a.i64Const(0) }
        assertValidWasm(wasm)
    }

    @Test
    fun i64ConstMaxValue() {
        val wasm = assembleFunc(results = listOf(WasmValueType.I64)) { _, a -> a.i64Const(Long.MAX_VALUE) }
        assertValidWasm(wasm)
    }

    @Test
    fun i64ConstMinValue() {
        val wasm = assembleFunc(results = listOf(WasmValueType.I64)) { _, a -> a.i64Const(Long.MIN_VALUE) }
        assertValidWasm(wasm)
    }

    @Test
    fun f32ConstZero() {
        val wasm = assembleFunc(results = listOf(WasmValueType.F32)) { _, a -> a.f32Const(0.0f) }
        assertValidWasm(wasm)
    }

    @Test
    fun f32ConstNegative() {
        val wasm = assembleFunc(results = listOf(WasmValueType.F32)) { _, a -> a.f32Const(-1.5f) }
        assertValidWasm(wasm)
    }

    @Test
    fun f64ConstZero() {
        val wasm = assembleFunc(results = listOf(WasmValueType.F64)) { _, a -> a.f64Const(0.0) }
        assertValidWasm(wasm)
    }

    @Test
    fun f64ConstNegative() {
        val wasm = assembleFunc(results = listOf(WasmValueType.F64)) { _, a -> a.f64Const(-99.99) }
        assertValidWasm(wasm)
    }

    // --- Local variables ---

    @Test
    fun localSetAndGet() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            val local = a.declareLocal("x", WasmValueType.I32)
            a.i32Const(42)
            a.localSet(local)
            a.localGet(local)
        }
        assertValidWasm(wasm)
    }

    @Test
    fun multipleLocals() {
        val wasm = assembleFunc(results = listOf(WasmValueType.I32)) { _, a ->
            val a1 = a.declareLocal("a", WasmValueType.I32)
            val b1 = a.declareLocal("b", WasmValueType.I32)
            val c1 = a.declareLocal("c", WasmValueType.I64)
            a.i32Const(1)
            a.localSet(a1)
            a.i32Const(2)
            a.localSet(b1)
            a.i64Const(3)
            a.localSet(c1)
            a.localGet(a1)
            a.localGet(b1)
            a.i32Add()
        }
        assertValidWasm(wasm)
    }

    // --- Control flow: loop ---

    @Test
    fun simpleLoop() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            val counter = a.declareLocal("counter", WasmValueType.I32)
            a.localGet(fn.getParameter(0))
            a.localSet(counter)
            a.block(WasmBlockType.Void) { exit: WasmLabel ->
                a.loop(WasmBlockType.Void) { loop: WasmLabel ->
                    a.localGet(counter)
                    a.i32Eqz()
                    a.brIf(exit)
                    a.localGet(counter)
                    a.i32Const(1)
                    a.i32Sub()
                    a.localSet(counter)
                    a.br(loop)
                }
            }
            a.localGet(counter)
        }
        assertValidWasm(wasm)
    }

    // --- Multiple functions ---

    @Test
    fun multipleFunctions() {
        val asm = WasmAssembler.create()
        asm.function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }
        asm.function("sub", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Sub()
        }
        asm.function("mul", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Mul()
        }
        assertValidWasm(asm.assemble())
    }

    // --- Global variables ---

    @Test
    fun globalGetSet() {
        val asm = WasmAssembler.create()
        asm.global("counter", WasmValueType.I32, mutable = true, initValue = 0L, exported = true)
        asm.function("inc", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.globalGet(0)
            a.i32Const(1)
            a.i32Add()
            a.globalSet(0)
            a.globalGet(0)
        }
        assertValidWasm(asm.assemble())
    }

    // --- Void function ---

    @Test
    fun voidFunction() {
        val wasm = assembleFunc(results = emptyList()) { _, a ->
            a.nop()
        }
        assertValidWasm(wasm)
    }

    // --- Return ---

    @Test
    fun earlyReturn() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Eqz()
            a.ifThenElse(WasmBlockType.I32,
                thenBody = {
                    a.i32Const(0)
                    a.return_()
                },
                elseBody = {
                    a.localGet(fn.getParameter(0))
                }
            )
        }
        assertValidWasm(wasm)
    }
}
