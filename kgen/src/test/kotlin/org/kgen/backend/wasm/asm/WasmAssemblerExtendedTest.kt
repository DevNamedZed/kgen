package org.kgen.backend.wasm.asm

import org.kgen.backend.wasm.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WasmAssemblerExtendedTest {

    private fun assertValidWasm(bytes: ByteArray) {
        assertTrue(bytes.size >= 8, "Too short for a valid .wasm module")
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(0x61, bytes[1].toInt() and 0xFF)
        assertEquals(0x73, bytes[2].toInt() and 0xFF)
        assertEquals(0x6D, bytes[3].toInt() and 0xFF)
        assertEquals(0x01, bytes[4].toInt() and 0xFF)
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

    // i32 remaining arithmetic ops

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

    // i32 unsigned comparisons

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

    // i64 full arithmetic and unary

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

    // i64 comparisons

    @Test
    fun i64Eqz() {
        val wasm = assembleFunc(listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Eqz()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64EqNe() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Eq()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Ne()
            a.i32Or()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64SignedComparisons() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64LtS()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64GtS()
            a.i32And()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64LeS()
            a.i32And()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64GeS()
            a.i32And()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i64UnsignedComparisons() {
        val wasm = assembleFunc(listOf(WasmValueType.I64, WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64LtU()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64GtU()
            a.i32And()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64LeU()
            a.i32And()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64GeU()
            a.i32And()
        }
        assertValidWasm(wasm)
    }

    // f32 unary operations

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
    fun f32CeilFloorTruncNearest() {
        val wasm = assembleFunc(listOf(WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32Ceil()
            a.f32Floor()
            a.f32Trunc()
            a.f32Nearest()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32MinMaxCopysign() {
        val wasm = assembleFunc(listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Min()
            a.localGet(fn.getParameter(1))
            a.f32Max()
            a.localGet(fn.getParameter(0))
            a.f32Copysign()
        }
        assertValidWasm(wasm)
    }

    // f32 comparisons

    @Test
    fun f32Comparisons() {
        val wasm = assembleFunc(listOf(WasmValueType.F32, WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Eq()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Ne()
            a.i32Or()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Lt()
            a.i32Or()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Gt()
            a.i32Or()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Le()
            a.i32Or()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Ge()
            a.i32Or()
        }
        assertValidWasm(wasm)
    }

    // f64 unary operations

    @Test
    fun f64AbsNegSqrt() {
        val wasm = assembleFunc(listOf(WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64Abs()
            a.f64Neg()
            a.f64Sqrt()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64CeilFloorTruncNearest() {
        val wasm = assembleFunc(listOf(WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64Ceil()
            a.f64Floor()
            a.f64Trunc()
            a.f64Nearest()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f64MinMaxCopysign() {
        val wasm = assembleFunc(listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Min()
            a.localGet(fn.getParameter(1))
            a.f64Max()
            a.localGet(fn.getParameter(0))
            a.f64Copysign()
        }
        assertValidWasm(wasm)
    }

    // f64 comparisons

    @Test
    fun f64Comparisons() {
        val wasm = assembleFunc(listOf(WasmValueType.F64, WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Eq()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Ne()
            a.i32Or()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Lt()
            a.i32Or()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Gt()
            a.i32Or()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Le()
            a.i32Or()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Ge()
            a.i32Or()
        }
        assertValidWasm(wasm)
    }

    // Conversions

    @Test
    fun i32TruncF32S() {
        val wasm = assembleFunc(listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32TruncF32S()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32TruncF32U() {
        val wasm = assembleFunc(listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32TruncF32U()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun i32TruncF64U() {
        val wasm = assembleFunc(listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32TruncF64U()
        }
        assertValidWasm(wasm)
    }

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
    fun f64ConvertI32U() {
        val wasm = assembleFunc(listOf(WasmValueType.I32), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64ConvertI32U()
        }
        assertValidWasm(wasm)
    }

    @Test
    fun f32ConvertI64S() {
        val wasm = assembleFunc(listOf(WasmValueType.I64), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32ConvertI64S()
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
    fun f32DemoteF64() {
        val wasm = assembleFunc(listOf(WasmValueType.F64), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32DemoteF64()
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
    fun reinterpretOps() {
        val asm = WasmAssembler.create()
        asm.function("i32ReinterpretF32", listOf(WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32ReinterpretF32()
        }
        asm.function("f32ReinterpretI32", listOf(WasmValueType.I32), listOf(WasmValueType.F32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32ReinterpretI32()
        }
        asm.function("i64ReinterpretF64", listOf(WasmValueType.F64), listOf(WasmValueType.I64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64ReinterpretF64()
        }
        asm.function("f64ReinterpretI64", listOf(WasmValueType.I64), listOf(WasmValueType.F64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64ReinterpretI64()
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun signExtensionOps() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Extend8S()
            a.i32Extend16S()
        }
        assertValidWasm(wasm)
    }

    // Memory operations: various sizes

    @Test
    fun i64LoadStore() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("test", listOf(WasmValueType.I32), listOf(WasmValueType.I64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Const(999L)
            a.i64Store(0, 0)
            a.localGet(fn.getParameter(0))
            a.i64Load(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun f32LoadStore() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("test", listOf(WasmValueType.I32), listOf(WasmValueType.F32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32Const(1.5f)
            a.f32Store(0, 0)
            a.localGet(fn.getParameter(0))
            a.f32Load(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun f64LoadStore() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("test", listOf(WasmValueType.I32), listOf(WasmValueType.F64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64Const(2.718)
            a.f64Store(0, 0)
            a.localGet(fn.getParameter(0))
            a.f64Load(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun i32SubByteLoads() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Load8S(0, 0)
            a.localGet(fn.getParameter(0))
            a.i32Load16S(0, 0)
            a.i32Add()
            a.localGet(fn.getParameter(0))
            a.i32Load16U(0, 0)
            a.i32Add()
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun i32SubByteStores() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("test", emptyList(), emptyList(), exported = true) { _, a ->
            a.i32Const(0)
            a.i32Const(42)
            a.i32Store8(0, 0)
            a.i32Const(4)
            a.i32Const(1000)
            a.i32Store16(0, 0)
        }
        assertValidWasm(asm.assemble())
    }

    @Test
    fun memorySizeAndGrow() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("test", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.memorySize(0)
            a.i32Const(1)
            a.memoryGrow(0)
            a.i32Add()
        }
        assertValidWasm(asm.assemble())
    }

    // Control flow: flat style

    @Test
    fun flatStyleBlockAndBranch() {
        val asm = WasmAssembler.create()
        val fn = asm.beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        val label = asm.beginBlock(WasmBlockType.I32)
        asm.localGet(fn.getParameter(0))
        asm.i32Eqz()
        asm.brIf(label)
        asm.localGet(fn.getParameter(0))
        asm.i32Const(10)
        asm.i32Mul()
        asm.endBlock()
        asm.endFunction()
        assertValidWasm(asm.assemble())
    }

    @Test
    fun flatStyleLoop() {
        val asm = WasmAssembler.create()
        val fn = asm.beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        val result = asm.declareLocal("result", WasmValueType.I32)
        asm.i32Const(0)
        asm.localSet(result)
        val loopLabel = asm.beginLoop()
        asm.localGet(fn.getParameter(0))
        asm.i32Const(0)
        asm.i32GtS()
        val ifLabel = asm.beginIf()
        asm.localGet(result)
        asm.localGet(fn.getParameter(0))
        asm.i32Add()
        asm.localSet(result)
        asm.localGet(fn.getParameter(0))
        asm.i32Const(1)
        asm.i32Sub()
        asm.localSet(fn.getParameter(0))
        asm.br(loopLabel)
        asm.endIf()
        asm.endLoop()
        asm.localGet(result)
        asm.endFunction()
        assertValidWasm(asm.assemble())
    }

    @Test
    fun flatStyleIfElse() {
        val asm = WasmAssembler.create()
        val fn = asm.beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(fn.getParameter(0))
        asm.i32Const(0)
        asm.i32GtS()
        asm.beginIf(WasmBlockType.I32)
        asm.i32Const(1)
        asm.beginElse()
        asm.i32Const(-1)
        asm.endIf()
        asm.endFunction()
        assertValidWasm(asm.assemble())
    }

    @Test
    fun flatAndCallbackStylesMatchForLoop() {
        val asmCallback = WasmAssembler.create()
        asmCallback.function("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.block(WasmBlockType.I32) { outer ->
                a.localGet(fn.getParameter(0))
                a.i32Const(0)
                a.i32LeS()
                a.brIf(outer)
                a.localGet(fn.getParameter(0))
                a.i32Const(2)
                a.i32Mul()
            }
        }
        val wasmCallback = asmCallback.assemble()

        val asmFlat = WasmAssembler.create()
        val fn = asmFlat.beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        val outer = asmFlat.beginBlock(WasmBlockType.I32)
        asmFlat.localGet(fn.getParameter(0))
        asmFlat.i32Const(0)
        asmFlat.i32LeS()
        asmFlat.brIf(outer)
        asmFlat.localGet(fn.getParameter(0))
        asmFlat.i32Const(2)
        asmFlat.i32Mul()
        asmFlat.endBlock()
        asmFlat.endFunction()
        val wasmFlat = asmFlat.assemble()

        assertArrayEquals(wasmCallback, wasmFlat)
    }

    // Control flow: return

    @Test
    fun returnFromFunction() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(0)
            a.i32GtS()
            a.ifThen {
                a.localGet(fn.getParameter(0))
                a.return_()
            }
            a.i32Const(-1)
        }
        assertValidWasm(wasm)
    }

    // Control flow: br_table with multiple targets

    @Test
    fun brTableMultipleTargets() {
        val wasm = assembleFunc(listOf(WasmValueType.I32)) { fn, a ->
            a.block(WasmBlockType.I32) { defaultBlock ->
                a.block(WasmBlockType.Void) { case0 ->
                    a.block(WasmBlockType.Void) { case1 ->
                        a.block(WasmBlockType.Void) { case2 ->
                            a.localGet(fn.getParameter(0))
                            a.brTable(defaultBlock, case0, case1, case2)
                        }
                        a.i32Const(200)
                        a.br(defaultBlock)
                    }
                    a.i32Const(100)
                    a.br(defaultBlock)
                }
                a.i32Const(0)
            }
        }
        assertValidWasm(wasm)
    }

    // Nop

    @Test
    fun nopInstruction() {
        val wasm = assembleFunc(results = emptyList()) { _, a ->
            a.nop()
            a.nop()
            a.nop()
        }
        assertValidWasm(wasm)
    }

    // Deeply nested blocks

    @Test
    fun deeplyNestedBlocks() {
        val wasm = assembleFunc { _, a ->
            a.block { _ ->
                a.block { _ ->
                    a.block { _ ->
                        a.block { _ ->
                            a.block { innermost ->
                                a.i32Const(1)
                                a.brIf(innermost)
                            }
                        }
                    }
                }
            }
            a.i32Const(42)
        }
        assertValidWasm(wasm)
    }

    // Empty function

    @Test
    fun emptyFunction() {
        val wasm = assembleFunc(results = emptyList()) { _, _ -> }
        assertValidWasm(wasm)
    }

    // Many locals

    @Test
    fun manyLocals() {
        val wasm = assembleFunc { _, a ->
            val locals = (0 until 20).map { i -> a.declareLocal("l$i", WasmValueType.I32) }
            for (i in locals.indices) {
                a.i32Const(i)
                a.localSet(locals[i])
            }
            a.i32Const(0)
            for (local in locals) {
                a.localGet(local)
                a.i32Add()
            }
        }
        assertValidWasm(wasm)
    }

    @Test
    fun mixedTypeLocals() {
        val asm = WasmAssembler.create()
        asm.function("test", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            val li = a.declareLocal("li", WasmValueType.I32)
            val ll = a.declareLocal("ll", WasmValueType.I64)
            val lf = a.declareLocal("lf", WasmValueType.F32)
            val ld = a.declareLocal("ld", WasmValueType.F64)
            a.i32Const(1)
            a.localSet(li)
            a.i64Const(2L)
            a.localSet(ll)
            a.f32Const(3.0f)
            a.localSet(lf)
            a.f64Const(4.0)
            a.localSet(ld)
            a.localGet(li)
        }
        assertValidWasm(asm.assemble())
    }

    // Multiple functions with cross-calls

    @Test
    fun multipleFunctionsWithChainedCalls() {
        val asm = WasmAssembler.create()
        asm.function("square", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(0))
            a.i32Mul()
        }
        asm.function("cube", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.call("square")
            a.localGet(fn.getParameter(0))
            a.i32Mul()
        }
        asm.function("sumOfCubes", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.call("cube")
            a.localGet(fn.getParameter(1))
            a.call("cube")
            a.i32Add()
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    // Function with multiple return values

    @Test
    fun multipleReturnValues() {
        val wasm = assembleFunc(
            listOf(WasmValueType.I32),
            listOf(WasmValueType.I32, WasmValueType.I32)
        ) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(1)
            a.i32Add()
            a.localGet(fn.getParameter(0))
            a.i32Const(1)
            a.i32Sub()
        }
        assertValidWasm(wasm)
    }

    // Global get/set by index

    @Test
    fun globalGetSetWithMultipleGlobals() {
        val asm = WasmAssembler.create()
        asm.global("a", WasmValueType.I32, mutable = true, initValue = 10, exported = true)
        asm.global("b", WasmValueType.I64, mutable = true, initValue = 20, exported = true)
        asm.function("swapGlobals", emptyList(), emptyList(), exported = true) { _, a ->
            a.globalGet(0)
            a.i64ExtendI32S()
            val tmp = a.declareLocal("tmp", WasmValueType.I64)
            a.localSet(tmp)
            a.globalGet(1)
            a.i32WrapI64()
            a.globalSet(0)
            a.localGet(tmp)
            a.globalSet(1)
        }
        assertValidWasm(asm.assemble())
    }

    // Immutable global

    @Test
    fun immutableGlobal() {
        val asm = WasmAssembler.create()
        asm.global("pi_approx", WasmValueType.I32, mutable = false, initValue = 3, exported = true)
        asm.function("getPi", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.globalGet(0)
        }
        assertValidWasm(asm.assemble())
    }

    // Import function and use with defined functions

    @Test
    fun importAndDefinedFunctionInteraction() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "print", listOf(WasmValueType.I32), emptyList())
        asm.importFunction("env", "readInput", emptyList(), listOf(WasmValueType.I32))
        asm.function("processAndPrint", emptyList(), emptyList(), exported = true) { _, a ->
            a.call("readInput")
            a.i32Const(2)
            a.i32Mul()
            a.call("print")
        }
        assertValidWasm(asm.assemble())
    }

    // Table declaration

    @Test
    fun tableDeclaration() {
        val asm = WasmAssembler.create()
        asm.table("table0", WasmRefType.FUNCREF, 10, 100, exported = true)
        asm.function("test", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.i32Const(42)
        }
        assertValidWasm(asm.assemble())
    }

    // Memory with max pages

    @Test
    fun memoryWithMaxPages() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, 10, exported = true)
        asm.function("test", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.memorySize(0)
        }
        assertValidWasm(asm.assemble())
    }

    // Passive data segment

    @Test
    fun passiveDataSegment() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.dataSegment("Hello, World!".toByteArray())
        asm.function("test", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.i32Const(0)
        }
        assertValidWasm(asm.assemble())
    }

    // Import global and memory

    @Test
    fun importGlobalAndMemory() {
        val asm = WasmAssembler.create()
        asm.importMemory("env", "memory", 1, 10)
        asm.importGlobal("env", "tableBase", WasmValueType.I32, mutable = false)
        asm.function("test", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.globalGet(0)
        }
        assertValidWasm(asm.assemble())
    }

    // Truncation saturating ops

    @Test
    fun truncSatOps() {
        val asm = WasmAssembler.create()
        asm.function("test", listOf(WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32TruncSatF64S()
        }
        assertValidWasm(asm.assemble())
    }
}
