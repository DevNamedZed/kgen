package org.wark.examples.wasm4

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import java.nio.file.Files
import java.nio.file.Path

class Wasm4RuntimeDiagnosticTest {

    @Test
    fun framebufferClearedBeforeEachUpdate() {
        val bytes = buildMinimalGame { asm ->
            // Draw a pixel at (0,0) with color 1 every frame
            asm.i32Const(Wasm4Host.DRAW_COLORS_ADDRESS)
            asm.i32Const(0x0002) // foreground = color 2
            asm.i32Store16(0, 0)

            // rect(0, 0, 1, 1) — single pixel
            asm.i32Const(0)
            asm.i32Const(0)
            asm.i32Const(1)
            asm.i32Const(1)
            asm.call(0) // rect
        }

        val runner = Wasm4Runner.load(bytes, ExecutionMode.INTERPRET)
        runner.start()

        // Frame 1: game draws pixel at (0,0)
        runner.update()
        val pixel = runner.getPixel(0, 0)
        assertTrue(pixel != 0, "pixel (0,0) should be drawn after update, got $pixel")

        // Before frame 2: framebuffer should be cleared, then game redraws
        // Verify by checking that a pixel NOT drawn by the game IS cleared
        runner.update()
        val undrawnPixel = runner.getPixel(80, 80)
        assertEquals(0, undrawnPixel, "undrawn pixel (80,80) should be 0 after framebuffer clearing")
    }

    @Test
    fun framebufferPreservedWhenFlagSet() {
        val bytes = buildMinimalGame { asm ->
            // Set SYSTEM_PRESERVE_FRAMEBUFFER flag
            asm.i32Const(Wasm4Host.SYSTEM_FLAGS_ADDRESS)
            asm.i32Const(0x01) // bit 0 = preserve framebuffer
            asm.i32Store8(0, 0)

            // Draw a pixel at (80,80) on first frame only (use a counter)
            asm.i32Const(0x2A00) // counter address
            asm.i32Const(0x2A00)
            asm.i32Load(0, 0)
            asm.i32Const(1)
            asm.i32Add()
            asm.i32Store(0, 0)

            // Only draw on frame 1
            asm.i32Const(0x2A00)
            asm.i32Load(0, 0)
            asm.i32Const(1)
            asm.i32Eq()
            asm.beginIf()
            asm.i32Const(Wasm4Host.DRAW_COLORS_ADDRESS)
            asm.i32Const(0x0003) // foreground = color 3
            asm.i32Store16(0, 0)
            asm.i32Const(80)
            asm.i32Const(80)
            asm.i32Const(1)
            asm.i32Const(1)
            asm.call(0) // rect at (80,80)
            asm.end()
        }

        val runner = Wasm4Runner.load(bytes, ExecutionMode.INTERPRET)
        runner.start()

        runner.update() // frame 1: draws pixel at (80,80)
        val pixelAfterFrame1 = runner.getPixel(80, 80)
        assertTrue(pixelAfterFrame1 != 0, "pixel should be drawn on frame 1")

        runner.update() // frame 2: doesn't draw at (80,80) but preserve flag is set
        val pixelAfterFrame2 = runner.getPixel(80, 80)
        assertTrue(pixelAfterFrame2 != 0, "pixel should be preserved with SYSTEM_PRESERVE_FRAMEBUFFER set")
    }

    @Test
    fun minesweeperRendersAfterFrame1() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val runner = Wasm4Runner.load(path, ExecutionMode.INTERPRET)
        runner.start()

        val pixelsPerFrame = mutableListOf<Int>()
        for (frame in 1..10) {
            runner.update()
            pixelsPerFrame.add(countPixels(runner))
        }

        println("minesweeper pixels per frame: $pixelsPerFrame")
        val maxPixels = pixelsPerFrame.max()
        assertTrue(maxPixels > 0, "minesweeper should render pixels within 10 frames, got all zeros")

        // Find first frame with pixels
        val firstDrawFrame = pixelsPerFrame.indexOfFirst { it > 0 } + 1
        println("minesweeper first non-zero frame: $firstDrawFrame with ${pixelsPerFrame[firstDrawFrame - 1]} pixels")
    }

    @Test
    fun snakeRespondsToDirectionalInput() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val runner = Wasm4Runner.load(path, ExecutionMode.INTERPRET)
        runner.start()

        // Run 5 frames without input, capture hash
        for (frame in 1..5) {
            runner.update()
        }
        val hashBeforeInput = framebufferHash(runner)

        // Send DPAD_UP for 1 frame, then release
        runner.setGamepad(0x40) // DPAD_UP
        runner.update()
        runner.setGamepad(0x00)

        // Run more frames and check if display changes
        var changed = false
        for (frame in 7..30) {
            runner.update()
            val currentHash = framebufferHash(runner)
            if (currentHash != hashBeforeInput) {
                println("snake display changed at frame $frame after DPAD_UP input")
                changed = true
                break
            }
        }
        assertTrue(changed, "snake should respond to DPAD_UP input within 30 frames")
    }

    @Test
    fun watrisAnimatesWithoutInput() {
        val path = Path.of("../assets/wasm4/watris.wasm")
        assumeTrue(Files.exists(path))
        val runner = Wasm4Runner.load(path, ExecutionMode.INTERPRET)
        runner.start()

        val hashes = mutableListOf<Int>()
        for (frame in 1..120) {
            runner.update()
            hashes.add(framebufferHash(runner))
        }

        val uniqueHashes = hashes.toSet().size
        println("watris: $uniqueHashes unique frames in 120 updates")
        assertTrue(uniqueHashes > 1, "watris should animate (pieces falling) within 120 frames without input")

        val firstChangeFrame = (1 until hashes.size).firstOrNull { hashes[it] != hashes[0] }
        if (firstChangeFrame != null) {
            println("watris: first visual change at frame ${firstChangeFrame + 1}")
        }
    }

    @Test
    fun watrisJitMatchesInterpreter() {
        val path = Path.of("../assets/wasm4/watris.wasm")
        assumeTrue(Files.exists(path))

        val interpRunner = Wasm4Runner.load(path, ExecutionMode.INTERPRET)
        interpRunner.start()

        val jitRunner = Wasm4Runner.load(path, ExecutionMode.JIT)
        jitRunner.start()

        var firstDivergence = -1
        for (frame in 1..60) {
            interpRunner.update()
            jitRunner.update()

            val interpHash = framebufferHash(interpRunner)
            val jitHash = framebufferHash(jitRunner)
            val interpPixels = countPixels(interpRunner)
            val jitPixels = countPixels(jitRunner)

            if (frame <= 3 || interpHash != jitHash) {
                println("watris frame $frame: interp=$interpPixels/$interpHash, jit=$jitPixels/$jitHash ${if (interpHash != jitHash) { "DIVERGED" } else { "" }}")
            }

            if (interpHash != jitHash && firstDivergence < 0) {
                firstDivergence = frame
            }
        }

        if (firstDivergence > 0) {
            println("watris JIT diverges from interpreter at frame $firstDivergence")
        } else {
            println("watris JIT matches interpreter for 60 frames")
        }
    }

    private fun buildMinimalGame(updateBody: (WasmAssembler) -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
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
        assembler.function("start", emptyList(), emptyList(), exported = true) { _, _ -> }
        assembler.beginFunction("update", emptyList(), emptyList(), exported = true)
        updateBody(assembler)
        assembler.endFunction()
        return assembler.assemble()
    }

    private fun countPixels(runner: Wasm4Runner): Int {
        var count = 0
        for (y in 0 until 160) {
            for (x in 0 until 160) {
                if (runner.getPixel(x, y) != 0) { count++ }
            }
        }
        return count
    }

    private fun framebufferHash(runner: Wasm4Runner): Int {
        var hash = 0
        for (y in 0 until 160) {
            for (x in 0 until 160) {
                hash = hash * 31 + runner.getPixel(x, y)
            }
        }
        return hash
    }
}
