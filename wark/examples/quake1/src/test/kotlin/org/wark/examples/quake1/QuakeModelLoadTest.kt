package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeModelLoadTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    @Test
    @EnabledIf("quakeWasmExists")
    fun runMultipleFramesInterpreter() {
        val runner = QuakeRunner.load(wasmPath, gameDirectory, ExecutionMode.INTERPRET)
        runner.initialize()

        var errorCount = 0
        for (frame in 1..200) {
            try {
                runner.frame(1.0f / 60.0f)
            } catch (trap: org.wark.WasmTrap) {
                errorCount++
                if (errorCount <= 3) {
                    println("Frame $frame trap: ${trap.message}")
                }
            }
        }
        println("Completed 200 frames in INTERPRET mode, $errorCount traps")
        runner.shutdown()
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun runMultipleFramesJit() {
        val runner = QuakeRunner.load(wasmPath, gameDirectory, ExecutionMode.JIT)
        runner.initialize()

        var errorCount = 0
        var lastTime = System.nanoTime()
        for (frame in 1..600) {
            val now = System.nanoTime()
            val dt = (now - lastTime) / 1_000_000_000.0f
            lastTime = now
            try {
                runner.frame(dt.coerceAtMost(0.05f))
            } catch (trap: org.wark.WasmTrap) {
                errorCount++
                if (errorCount <= 5) {
                    println("Frame $frame trap: ${trap.message}")
                }
            }
        }
        println("Completed 600 frames in JIT mode, $errorCount traps")
        runner.shutdown()
    }
}
