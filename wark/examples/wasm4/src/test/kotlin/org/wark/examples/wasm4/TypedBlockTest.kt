package org.wark.examples.wasm4

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import kotlin.test.assertEquals

class TypedBlockTest {

    @Test
    fun typedIfElseReturnsCorrectBranch() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, exported = true)

        // fn(x) -> if x != 0 then 42 else 99
        asm.beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0)
        asm.beginIf(org.kgen.target.wasm.WasmBlockType.I32)
        asm.i32Const(42)
        asm.beginElse()
        asm.i32Const(99)
        asm.end()
        asm.endFunction()

        val bytes = asm.assemble()

        // Test with interpreter
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate()
        assertEquals(42L, interpInstance.call("test", 1L)[0], "interp: true branch")
        assertEquals(99L, interpInstance.call("test", 0L)[0], "interp: false branch")

        // Test with JIT
        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate()
        assertEquals(42L, jitInstance.call("test", 1L)[0], "jit: true branch")
        assertEquals(99L, jitInstance.call("test", 0L)[0], "jit: false branch")
    }

    @Test
    fun typedIfElseUsedInComputation() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, exported = true)

        // fn(x) -> (if x > 10 then x else 10) + 5
        asm.beginFunction("clampAdd", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0)
        asm.i32Const(10)
        asm.i32GtS()
        asm.beginIf(org.kgen.target.wasm.WasmBlockType.I32)
        asm.localGet(0)
        asm.beginElse()
        asm.i32Const(10)
        asm.end()
        asm.i32Const(5)
        asm.i32Add()
        asm.endFunction()

        val bytes = asm.assemble()

        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate()
        assertEquals(20L, jitInstance.call("clampAdd", 15L)[0], "15 > 10 → 15 + 5 = 20")
        assertEquals(15L, jitInstance.call("clampAdd", 5L)[0], "5 <= 10 → 10 + 5 = 15")
        assertEquals(15L, jitInstance.call("clampAdd", 10L)[0], "10 <= 10 → 10 + 5 = 15")
    }

    @Test
    fun typedBlockResult() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, exported = true)

        // fn(x) -> block(i32) { x * 2; br 0 } + 1
        asm.beginFunction("blockResult", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.beginBlock(org.kgen.target.wasm.WasmBlockType.I32)
        asm.localGet(0)
        asm.i32Const(2)
        asm.i32Mul()
        asm.br(0)
        asm.end()
        asm.i32Const(1)
        asm.i32Add()
        asm.endFunction()

        val bytes = asm.assemble()

        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate()
        assertEquals(11L, jitInstance.call("blockResult", 5L)[0], "5*2+1=11")
        assertEquals(21L, jitInstance.call("blockResult", 10L)[0], "10*2+1=21")
    }

    @Test
    fun nestedTypedBlocks() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, exported = true)

        // fn(x) -> if x > 0 then (if x > 10 then 3 else 2) else 1
        asm.beginFunction("nested", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0)
        asm.i32Const(0)
        asm.i32GtS()
        asm.beginIf(org.kgen.target.wasm.WasmBlockType.I32)
          asm.localGet(0)
          asm.i32Const(10)
          asm.i32GtS()
          asm.beginIf(org.kgen.target.wasm.WasmBlockType.I32)
            asm.i32Const(3)
          asm.beginElse()
            asm.i32Const(2)
          asm.end()
        asm.beginElse()
          asm.i32Const(1)
        asm.end()
        asm.endFunction()

        val bytes = asm.assemble()

        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate()
        assertEquals(1L, jitInstance.call("nested", -5L)[0], "neg → 1")
        assertEquals(2L, jitInstance.call("nested", 5L)[0], "0<x<=10 → 2")
        assertEquals(3L, jitInstance.call("nested", 15L)[0], "x>10 → 3")
    }
}
