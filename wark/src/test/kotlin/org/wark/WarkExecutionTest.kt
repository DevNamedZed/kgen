package org.wark

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import kotlin.test.assertEquals

/**
 * End-to-end execution tests: WASM binary → parse → IR → JIT → FFM call → result.
 */
class WarkExecutionTest {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    @Test
    fun executeConstantFunction() {
        val bytes = buildWasmBytes {
            function("answer", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.i32Const(42)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        val result = instance.call("answer")
        assertEquals(42L, result[0])
    }

    @Test
    fun executeAddFunction() {
        val bytes = buildWasmBytes {
            function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Add()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        val result = instance.call("add", 3, 4)
        assertEquals(7L, result[0])
    }

    @Test
    fun executeSubtract() {
        val bytes = buildWasmBytes {
            function("sub", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Sub()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        val result = instance.call("sub", 10, 3)
        assertEquals(7L, result[0])
    }

    @Test
    fun executeMultiply() {
        val bytes = buildWasmBytes {
            function("mul", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Mul()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        val result = instance.call("mul", 6, 7)
        assertEquals(42L, result[0])
    }

    @Test
    fun executeI64Arithmetic() {
        val bytes = buildWasmBytes {
            function("add64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i64Add()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        val result = instance.call("add64", 1000000000L, 2000000000L)
        assertEquals(3000000000L, result[0])
    }

    @Test
    fun executeComparison() {
        val bytes = buildWasmBytes {
            function("isZero", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Eqz()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        val zeroResult = instance.call("isZero", 0)[0]
        val nonZeroResult = instance.call("isZero", 42)[0]
        assertEquals(1L, zeroResult, "isZero(0) expected 1 got $zeroResult")
        assertEquals(0L, nonZeroResult, "isZero(42) expected 0 got $nonZeroResult")
    }

    @Test
    fun executeLocalTee() {
        val bytes = buildWasmBytes {
            function("double", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(0))
                asm.i32Add()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        val result = instance.call("double", 15)
        assertEquals(30L, result[0])
    }

    @Test
    fun executeNegation() {
        val bytes = buildWasmBytes {
            function("negate", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.i32Const(0)
                asm.localGet(func.getParameter(0))
                asm.i32Sub()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        assertEquals(-5L and 0xFFFFFFFFL, instance.call("negate", 5)[0] and 0xFFFFFFFFL)
    }

    @Test
    fun executeSelect() {
        val bytes = buildWasmBytes {
            function("max", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32GtS()
                asm.select()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        assertEquals(10L, instance.call("max", 10, 5)[0])
        assertEquals(20L, instance.call("max", 3, 20)[0])
    }

    @Test
    fun executeMemoryLoadStore() {
        val bytes = buildWasmBytes {
            memory("mem", 1, exported = true)
            function("store_and_load", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Store(0, 0)
                asm.localGet(func.getParameter(0))
                asm.i32Load(0, 0)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        val result = instance.call("store_and_load", 0, 99)
        assertEquals(99L, result[0])
    }
}
