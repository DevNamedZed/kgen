package org.wark

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests a WASM game loop pattern — the fundamental structure every game uses.
 * Uses headless mode (no SDL) — host functions stub the graphics calls.
 */
class WarkGameLoopTest {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    @Test
    fun gameLoopRunsNFrames() {
        val bytes = buildWasmBytes {
            importFunction("env", "render_frame", listOf(WasmValueType.I32), emptyList())
            importFunction("env", "get_ticks", emptyList(), listOf(WasmValueType.I32))
            memory("mem", 1, exported = true)

            val func = beginFunction("game_loop", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            val frameCount = declareLocal(WasmValueType.I32)
            val maxFrames = func.getParameter(0)

            i32Const(0)
            localSet(frameCount)

            beginBlock()
            beginLoop()

            localGet(frameCount)
            localGet(maxFrames)
            i32GeS()
            brIf(1)

            localGet(frameCount)
            call(0)

            localGet(frameCount)
            i32Const(1)
            i32Add()
            localSet(frameCount)

            br(0)
            end()
            end()

            localGet(frameCount)
            endFunction()
        }

        var framesRendered = 0
        val imports = WarkImports.builder()
            .function("env", "render_frame") { instance, args ->
                framesRendered++
                longArrayOf()
            }
            .function("env", "get_ticks") { instance, args ->
                longArrayOf((framesRendered * 16).toLong())
            }
            .build()

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate(imports)

        val result = instance.call("game_loop", 60)
        assertEquals(60L, result[0])
        assertEquals(60, framesRendered)
    }

    @Test
    fun entityUpdateLoop() {
        val bytes = buildWasmBytes {
            memory("mem", 1, exported = true)

            function("update_entities", listOf(WasmValueType.I32, WasmValueType.I32), emptyList(), exported = true) { func, asm ->
                val index = declareLocal(WasmValueType.I32)

                asm.i32Const(0)
                asm.localSet(index)

                asm.beginBlock()
                asm.beginLoop()

                asm.localGet(index)
                asm.localGet(func.getParameter(1))
                asm.i32GeS()
                asm.brIf(1)

                asm.localGet(func.getParameter(0))
                asm.localGet(index)
                asm.i32Const(8)
                asm.i32Mul()
                asm.i32Add()

                val entityAddress = declareLocal(WasmValueType.I32)
                asm.localTee(entityAddress)

                asm.localGet(entityAddress)
                asm.i32Load(0, 0)
                asm.localGet(entityAddress)
                asm.i32Load(0, 4)
                asm.i32Add()
                asm.i32Store(0, 0)

                asm.localGet(index)
                asm.i32Const(1)
                asm.i32Add()
                asm.localSet(index)

                asm.br(0)
                asm.end()
                asm.end()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate()

        val memory = instance.memory()
        memory.writeI32(0, 100)
        memory.writeI32(4, 5)
        memory.writeI32(8, 200)
        memory.writeI32(12, -3)
        memory.writeI32(16, 50)
        memory.writeI32(20, 10)

        instance.call("update_entities", 0, 3)

        assertEquals(105, memory.readI32(0))
        assertEquals(197, memory.readI32(8))
        assertEquals(60, memory.readI32(16))
    }

    @Test
    fun frameTimingAccumulator() {
        val bytes = buildWasmBytes {
            memory("mem", 1, exported = true)

            function("accumulate", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                val total = declareLocal(WasmValueType.I32)
                val index = declareLocal(WasmValueType.I32)

                asm.i32Const(0)
                asm.localSet(total)
                asm.i32Const(0)
                asm.localSet(index)

                asm.beginBlock()
                asm.beginLoop()
                asm.localGet(index)
                asm.localGet(func.getParameter(1))
                asm.i32GeS()
                asm.brIf(1)

                asm.localGet(total)
                asm.localGet(func.getParameter(0))
                asm.localGet(index)
                asm.i32Const(4)
                asm.i32Mul()
                asm.i32Add()
                asm.i32Load(0, 0)
                asm.i32Add()
                asm.localSet(total)

                asm.localGet(index)
                asm.i32Const(1)
                asm.i32Add()
                asm.localSet(index)
                asm.br(0)
                asm.end()
                asm.end()

                asm.localGet(total)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate()

        val memory = instance.memory()
        memory.writeI32(0, 16)
        memory.writeI32(4, 17)
        memory.writeI32(8, 16)
        memory.writeI32(12, 15)
        memory.writeI32(16, 18)

        val total = instance.call("accumulate", 0, 5)
        assertEquals(82L, total[0])
    }
}
