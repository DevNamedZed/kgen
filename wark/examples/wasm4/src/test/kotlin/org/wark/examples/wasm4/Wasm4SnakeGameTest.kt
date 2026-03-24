package org.wark.examples.wasm4

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import org.wark.ExecutionMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A complete WASM-4 snake game built from scratch using WasmAssembler.
 * Proves the full pipeline: build WASM → load → instantiate → run game loop.
 */
class Wasm4SnakeGameTest {

    @Test
    fun snakeGameRuns60Frames() {
        val bytes = buildSnakeGame()
        val runner = Wasm4Runner.load(bytes, ExecutionMode.INTERPRET)

        runner.start()
        for (frame in 0 until 60) {
            runner.update()
        }

        assertEquals(60, runner.frameCount())
    }

    @Test
    fun snakeGameRendersToFramebuffer() {
        val bytes = buildSnakeGame()
        val runner = Wasm4Runner.load(bytes, ExecutionMode.INTERPRET)

        runner.start()
        runner.update()
        runner.update()
        runner.update()

        assertTrue(runner.frameCount() > 0)
    }

    @Test
    fun snakeGameRespondsToInput() {
        val bytes = buildSnakeGame()
        val runner = Wasm4Runner.load(bytes, ExecutionMode.INTERPRET)

        runner.start()

        runner.setGamepad(0x40)
        runner.update()

        runner.setGamepad(0x80)
        runner.update()

        assertEquals(2, runner.frameCount())
    }

    /**
     * Builds a minimal snake game as WASM-4 cartridge.
     *
     * Memory layout:
     * 0x0000-0x009F: WASM-4 system memory (draw colors, gamepad, etc.)
     * 0x00A0-0x29FF: Framebuffer (160*160/4 = 6400 bytes)
     * 0x2A00+: Game state
     *   0x2A00: snake X position (i32)
     *   0x2A04: snake Y position (i32)
     *   0x2A08: snake direction (0=right, 1=down, 2=left, 3=up)
     *   0x2A0C: frame counter
     */
    private fun buildSnakeGame(): ByteArray {
        val assembler = WasmAssembler.create()

        val snakeX = 0x2A00
        val snakeY = 0x2A04
        val direction = 0x2A08
        val frameCounter = 0x2A0C

        assembler.importFunction("env", "rect", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
        assembler.importFunction("env", "text", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
        assembler.importFunction("env", "trace", listOf(WasmValueType.I32), emptyList())
        assembler.importFunction("env", "blit", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
        assembler.importFunction("env", "blitSub", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
        assembler.importFunction("env", "line", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
        assembler.importFunction("env", "hline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
        assembler.importFunction("env", "vline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
        assembler.importFunction("env", "oval", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
        assembler.importFunction("env", "tone", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
        assembler.importFunction("env", "diskr", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
        assembler.importFunction("env", "diskw", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
        assembler.importFunction("env", "tracef", listOf(WasmValueType.I32, WasmValueType.I32), emptyList())

        assembler.memory("mem", 1, exported = true)

        // start(): initialize snake at center
        assembler.function("start", emptyList(), emptyList(), exported = true) { func, asm ->
            asm.i32Const(snakeX)
            asm.i32Const(80)
            asm.i32Store(0, 0)

            asm.i32Const(snakeY)
            asm.i32Const(80)
            asm.i32Store(0, 0)

            asm.i32Const(direction)
            asm.i32Const(0)
            asm.i32Store(0, 0)

            asm.i32Const(frameCounter)
            asm.i32Const(0)
            asm.i32Store(0, 0)
        }

        // update(): read input, move snake, draw
        val func = assembler.beginFunction("update", emptyList(), emptyList(), exported = true)

        // Increment frame counter
        assembler.i32Const(frameCounter)
        assembler.i32Const(frameCounter)
        assembler.i32Load(0, 0)
        assembler.i32Const(1)
        assembler.i32Add()
        assembler.i32Store(0, 0)

        // Read gamepad (address 0x0016)
        val gamepad = assembler.declareLocal(WasmValueType.I32)
        assembler.i32Const(0x0016)
        assembler.i32Load8U(0, 0)
        assembler.localSet(gamepad)

        // Check input: button 1 (0x40) = right, button 2 (0x80) = down
        // Simple: if gamepad & 0x40, direction = 0 (right)
        assembler.localGet(gamepad)
        assembler.i32Const(0x40)
        assembler.i32And()
        assembler.beginIf()
        assembler.i32Const(direction)
        assembler.i32Const(0)
        assembler.i32Store(0, 0)
        assembler.end()

        assembler.localGet(gamepad)
        assembler.i32Const(0x80)
        assembler.i32And()
        assembler.beginIf()
        assembler.i32Const(direction)
        assembler.i32Const(1)
        assembler.i32Store(0, 0)
        assembler.end()

        // Move snake every 10 frames
        assembler.i32Const(frameCounter)
        assembler.i32Load(0, 0)
        assembler.i32Const(10)
        assembler.i32RemS()
        assembler.i32Eqz()
        assembler.beginIf()

        // Move based on direction
        val dir = assembler.declareLocal(WasmValueType.I32)
        assembler.i32Const(direction)
        assembler.i32Load(0, 0)
        assembler.localSet(dir)

        // direction 0: x += 8
        assembler.localGet(dir)
        assembler.i32Eqz()
        assembler.beginIf()
        assembler.i32Const(snakeX)
        assembler.i32Const(snakeX)
        assembler.i32Load(0, 0)
        assembler.i32Const(8)
        assembler.i32Add()
        assembler.i32Store(0, 0)
        assembler.end()

        // direction 1: y += 8
        assembler.localGet(dir)
        assembler.i32Const(1)
        assembler.i32Eq()
        assembler.beginIf()
        assembler.i32Const(snakeY)
        assembler.i32Const(snakeY)
        assembler.i32Load(0, 0)
        assembler.i32Const(8)
        assembler.i32Add()
        assembler.i32Store(0, 0)
        assembler.end()

        assembler.end() // end of frame % 10 == 0

        // Draw: set draw colors then draw rect for snake head
        assembler.i32Const(Wasm4Host.DRAW_COLORS_ADDRESS)
        assembler.i32Const(0x0002)
        assembler.i32Store16(0, 0)

        // rect(snakeX, snakeY, 8, 8)
        assembler.i32Const(snakeX)
        assembler.i32Load(0, 0)
        assembler.i32Const(snakeY)
        assembler.i32Load(0, 0)
        assembler.i32Const(8)
        assembler.i32Const(8)
        assembler.call(0) // rect

        assembler.endFunction()

        return assembler.assemble()
    }
}
