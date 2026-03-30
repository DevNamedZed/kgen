package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeKeyTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    @Test
    @EnabledIf("quakeWasmExists")
    fun escapeKeyStopsDemo() {
        val runner = QuakeRunner.load(wasmPath, gameDirectory, ExecutionMode.INTERPRET)
        runner.initialize()

        // Run a few frames to start the demo
        for (i in 1..5) {
            runner.frame(1.0f / 60.0f)
        }
        println("Demo should be starting...")

        // Send Escape key
        println("Sending Escape key down...")
        runner.keyEvent(27, true)
        runner.frame(1.0f / 60.0f)
        runner.keyEvent(27, false)

        // Run more frames - should show menu
        for (i in 1..10) {
            runner.frame(1.0f / 60.0f)
        }
        println("After Escape - should be in menu")

        runner.shutdown()
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun escapeKeyJit() {
        val runner = QuakeRunner.load(wasmPath, gameDirectory, ExecutionMode.JIT)
        runner.initialize()

        for (i in 1..5) {
            runner.frame(1.0f / 60.0f)
        }
        println("Sending Escape key down (JIT)...")
        runner.keyEvent(27, true)
        runner.frame(1.0f / 60.0f)
        runner.keyEvent(27, false)

        for (i in 1..10) {
            runner.frame(1.0f / 60.0f)
        }
        println("After Escape (JIT) - done")

        runner.shutdown()
    }
}
