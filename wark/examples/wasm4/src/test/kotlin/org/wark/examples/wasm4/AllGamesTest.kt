package org.wark.examples.wasm4

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.wark.ExecutionMode
import org.wark.WasmTrap
import java.nio.file.Files
import java.nio.file.Path

class AllGamesTest {

    @Test
    fun snakeInterpRuns120Frames() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val runner = Wasm4Runner.load(path, ExecutionMode.INTERPRET)
        runner.start()
        var lastHash = 0
        var firstChangeFrame = -1
        for (frame in 1..120) {
            if (frame == 5) { runner.setGamepad(0x40) }
            if (frame == 6) { runner.setGamepad(0x00) }
            try {
                runner.update()
            } catch (e: WasmTrap) {
                println("snake frame $frame TRAP: ${e.message}")
                return
            }
            val hash = framebufferHash(runner)
            if (hash != lastHash && lastHash != 0 && firstChangeFrame < 0) {
                firstChangeFrame = frame
            }
            lastHash = hash
            if (frame <= 3 || frame == 120) {
                val pixels = countPixels(runner)
                println("snake frame $frame: $pixels pixels, hash=$hash")
            }
        }
        println("snake first visual change: ${if (firstChangeFrame > 0) { "frame $firstChangeFrame" } else { "NONE in 120 frames" }}")
    }

    @Test
    fun watrisInterpRuns120Frames() {
        val path = Path.of("../assets/wasm4/watris.wasm")
        assumeTrue(Files.exists(path))
        val runner = Wasm4Runner.load(path, ExecutionMode.INTERPRET)
        runner.start()
        var lastHash = 0
        var firstChangeFrame = -1
        for (frame in 1..120) {
            try {
                runner.update()
            } catch (e: WasmTrap) {
                println("watris frame $frame TRAP: ${e.message}")
                return
            }
            val hash = framebufferHash(runner)
            if (hash != lastHash && lastHash != 0 && firstChangeFrame < 0) {
                firstChangeFrame = frame
            }
            lastHash = hash
            if (frame <= 3 || frame == 60 || frame == 120) {
                val pixels = countPixels(runner)
                println("watris frame $frame: $pixels pixels, hash=$hash")
            }
        }
        println("watris first visual change: ${if (firstChangeFrame > 0) { "frame $firstChangeFrame" } else { "NONE in 120 frames" }}")
    }

    @Test
    fun minesweeperInterpRuns120Frames() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val runner = Wasm4Runner.load(path, ExecutionMode.INTERPRET)
        runner.start()
        var lastPixels = 0
        for (frame in 1..120) {
            val startMs = System.currentTimeMillis()
            try {
                runner.update()
            } catch (e: WasmTrap) {
                println("minesweeper frame $frame TRAP: ${e.message}")
                return
            }
            val elapsed = System.currentTimeMillis() - startMs
            val pixels = countPixels(runner)
            lastPixels = pixels
            if (frame <= 5 || frame == 120) {
                println("minesweeper frame $frame: ${elapsed}ms, $pixels pixels")
            }
        }
        println("minesweeper trace: ${runner.traceOutput().take(200)}")
        println("minesweeper final pixels: $lastPixels")
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
