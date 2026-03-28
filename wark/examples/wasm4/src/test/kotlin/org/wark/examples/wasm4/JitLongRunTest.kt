package org.wark.examples.wasm4

import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.wark.ExecutionMode
import org.wark.WasmTrap
import java.nio.file.Files
import java.nio.file.Path

class JitLongRunTest {

    @Test
    fun watrisJitLongRun() {
        val path = Path.of("../assets/wasm4/watris.wasm")
        assumeTrue(Files.exists(path))

        val interpRunner = Wasm4Runner.load(path, ExecutionMode.INTERPRET)
        interpRunner.start()
        val jitRunner = Wasm4Runner.load(path, ExecutionMode.JIT)
        jitRunner.start()

        var lastInterpHash = 0
        var lastJitHash = 0
        var interpChanges = 0
        var jitChanges = 0
        var firstDivergence = -1

        for (frame in 1..300) {
            interpRunner.update()
            try {
                jitRunner.update()
            } catch (e: WasmTrap) {
                println("JIT trapped at frame $frame: ${e.message}")
                break
            }

            val interpHash = framebufferHash(interpRunner)
            val jitHash = framebufferHash(jitRunner)

            if (interpHash != lastInterpHash && lastInterpHash != 0) { interpChanges++ }
            if (jitHash != lastJitHash && lastJitHash != 0) { jitChanges++ }

            if (interpHash != jitHash && firstDivergence < 0) {
                firstDivergence = frame
            }

            if (frame <= 3 || frame % 60 == 0 || (interpHash != jitHash && firstDivergence == frame)) {
                val interpPx = countPixels(interpRunner)
                val jitPx = countPixels(jitRunner)
                val match = if (interpHash == jitHash) { "MATCH" } else { "DIVERGED" }
                println("frame $frame: interp=$interpPx/$interpHash, jit=$jitPx/$jitHash $match")
            }

            lastInterpHash = interpHash
            lastJitHash = jitHash
        }

        println("\ninterp visual changes: $interpChanges, jit visual changes: $jitChanges")
        if (firstDivergence > 0) {
            println("first divergence at frame $firstDivergence")
        } else {
            println("no divergence in 300 frames")
        }
    }

    @Test
    fun minesweeperJitLongRun() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))

        val interpRunner = Wasm4Runner.load(path, ExecutionMode.INTERPRET)
        interpRunner.start()
        val jitRunner = Wasm4Runner.load(path, ExecutionMode.JIT)
        jitRunner.start()

        var firstDivergence = -1
        for (frame in 1..60) {
            interpRunner.update()
            try {
                jitRunner.update()
            } catch (e: WasmTrap) {
                println("minesweeper JIT trapped at frame $frame: ${e.message}")
                break
            }

            val interpHash = framebufferHash(interpRunner)
            val jitHash = framebufferHash(jitRunner)

            if (interpHash != jitHash && firstDivergence < 0) {
                firstDivergence = frame
                val interpPx = countPixels(interpRunner)
                val jitPx = countPixels(jitRunner)
                println("DIVERGED at frame $frame: interp=$interpPx, jit=$jitPx")
            }
        }
        if (firstDivergence < 0) {
            println("minesweeper: JIT matches interpreter for 60 frames")
        }
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
