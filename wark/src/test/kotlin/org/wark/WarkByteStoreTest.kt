package org.wark

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import kotlin.test.assertEquals

/**
 * Tests for i32.store8 / i32.load8_u patterns — byte-level memory access.
 * DOOM func_34 uses store8 with computed addresses.
 */
class WarkByteStoreTest {

    private fun buildAndRun(mode: ExecutionMode, block: WasmAssembler.() -> Unit): WarkInstance {
        val asm = WasmAssembler.create()
        asm.block()
        return WarkRuntime.create(WasmTarget.MVP, mode).load(asm.assemble()).instantiate()
    }

    @Test
    fun store8AndLoad8URoundtrip() {
        for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
            val instance = buildAndRun(mode) {
                memory("mem", 1)
                beginFunction("f", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
                localGet(0) // address
                localGet(1) // value
                i32Store8(0, 0)
                localGet(0)
                i32Load8U(0, 0)
                endFunction()
            }
            assertEquals(0x41L, instance.call("f", 100, 0x41)[0], "$mode: store8/load8_u roundtrip")
            assertEquals(0xFFL, instance.call("f", 200, 0xFF)[0], "$mode: store8 0xFF")
            assertEquals(0x20L, instance.call("f", 300, 0x120)[0], "$mode: store8 truncates to low byte")
        }
    }

    @Test
    fun store8DoesNotCorruptAdjacentBytes() {
        for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
            val instance = buildAndRun(mode) {
                memory("mem", 1)
                beginFunction("f", emptyList(), listOf(WasmValueType.I32), exported = true)
                // Write 0x12345678 at offset 100
                i32Const(100); i32Const(0x12345678); i32Store(2, 0)
                // Overwrite just low byte with 0xAB
                i32Const(100); i32Const(0xAB); i32Store8(0, 0)
                // Read back full i32
                i32Const(100); i32Load(2, 0)
                endFunction()
            }
            assertEquals(0x123456ABL, instance.call("f")[0], "$mode: store8 only changes one byte")
        }
    }

    @Test
    fun store8WithMemoryOffset() {
        for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
            val instance = buildAndRun(mode) {
                memory("mem", 1)
                beginFunction("f", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
                // store8 with offset: mem[p0 + 10] = 0x42
                localGet(0)
                i32Const(0x42)
                i32Store8(0, 10)
                // load back: mem[p0 + 10]
                localGet(0)
                i32Load8U(0, 10)
                endFunction()
            }
            assertEquals(0x42L, instance.call("f", 100)[0], "$mode: store8 with offset")
            assertEquals(0x42L, instance.call("f", 200)[0], "$mode: store8 with offset at 200")
        }
    }

    @Test
    fun counterIncrementAndByteStorePattern() {
        // DOOM func_34 pattern: load counter, increment, store back, write byte at counter address
        for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
            val instance = buildAndRun(mode) {
                memory("mem", 1)
                val func = beginFunction("f", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
                val counter = declareLocal(WasmValueType.I32)
                val incremented = declareLocal(WasmValueType.I32)

                // Load counter from mem[200]
                i32Const(200); i32Load(2, 0); localSet(counter)
                // Increment
                localGet(counter); i32Const(1); i32Add(); localSet(incremented)
                // Store back
                i32Const(200); localGet(incremented); i32Store(2, 0)
                // Store byte (p0 & 0xFF) at mem[old counter value]
                localGet(counter)
                localGet(func.getParameter(0))
                i32Store8(0, 0)
                // Return new counter
                localGet(incremented)
                endFunction()
            }
            // Set counter at offset 200 to value 500
            instance.memory().writeI32(200, 500)
            val result = instance.call("f", 0x20)[0]
            assertEquals(501L, result, "$mode: counter incremented")
            assertEquals(0x20.toByte(), instance.memory().readByte(500), "$mode: byte at old counter addr")

            // Call again — counter now 501
            val result2 = instance.call("f", 0x41)[0]
            assertEquals(502L, result2, "$mode: counter 502")
            assertEquals(0x41.toByte(), instance.memory().readByte(501), "$mode: byte at 501")
            assertEquals(0x20.toByte(), instance.memory().readByte(500), "$mode: byte at 500 unchanged")
        }
    }
}
