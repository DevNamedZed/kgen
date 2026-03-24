package org.wark.examples.wasm4

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import org.wark.ExecutionMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Wasm4HostTest {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    @Test
    fun minimalWasm4Game() {
        val bytes = buildWasmBytes {
            importFunction("env", "rect", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "text", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "trace", listOf(WasmValueType.I32), emptyList())
            importFunction("env", "blit", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "blitSub", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "line", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "hline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "vline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "oval", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "tone", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "diskr", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "diskw", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "tracef", listOf(WasmValueType.I32, WasmValueType.I32), emptyList())

            memory("mem", 1, exported = true)

            function("start", emptyList(), emptyList(), exported = true) { func, asm ->
                asm.i32Const(1000)
                asm.i32Const(72)
                asm.i32Store8(0, 0)
                asm.i32Const(1001)
                asm.i32Const(105)
                asm.i32Store8(0, 0)
                asm.i32Const(1002)
                asm.i32Const(0)
                asm.i32Store8(0, 0)

                asm.i32Const(1000)
                asm.call(2)
            }

            function("update", emptyList(), emptyList(), exported = true) { func, asm ->
                asm.i32Const(10)
                asm.i32Const(10)
                asm.i32Const(20)
                asm.i32Const(20)
                asm.call(0)
            }
        }

        val runner = Wasm4Runner.load(bytes, ExecutionMode.INTERPRET)
        runner.start()

        assertTrue(runner.traceOutput().contains("Hi"))

        for (frame in 0 until 10) {
            runner.update()
        }
        assertEquals(10, runner.frameCount())
    }

    @Test
    fun wasm4CounterGame() {
        val bytes = buildWasmBytes {
            importFunction("env", "rect", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "text", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "trace", listOf(WasmValueType.I32), emptyList())
            importFunction("env", "blit", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "blitSub", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "line", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "hline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "vline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "oval", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "tone", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "diskr", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "diskw", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "tracef", listOf(WasmValueType.I32, WasmValueType.I32), emptyList())

            memory("mem", 1, exported = true)

            function("start", emptyList(), emptyList(), exported = true) { func, asm ->
                asm.i32Const(2000)
                asm.i32Const(0)
                asm.i32Store(0, 0)
            }

            function("update", emptyList(), emptyList(), exported = true) { func, asm ->
                asm.i32Const(2000)
                asm.i32Const(2000)
                asm.i32Load(0, 0)
                asm.i32Const(1)
                asm.i32Add()
                asm.i32Store(0, 0)
            }
        }

        val runner = Wasm4Runner.load(bytes, ExecutionMode.INTERPRET)
        runner.start()

        for (frame in 0 until 60) {
            runner.update()
        }

        assertEquals(60, runner.frameCount())
    }
}
