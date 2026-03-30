package org.wark.examples.quake1

import org.wark.ExecutionMode
import org.wark.WasmTrap
import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies the memory pre-growth fix for hunk/stack overlap.
 * The 32MB hunk previously overlapped with stack-allocated model_precache,
 * causing Cache_FreeHigh to zero model names during BSP loading.
 */
class QuakeHunkOverlapTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    fun testDemoPlayback() {
        println("=== Testing demo playback with 32MB hunk (pre-growth fix) ===")

        val runner = QuakeRunner.load(wasmPath, gameDirectory, ExecutionMode.INTERPRET)
        val instance = runner.instance()
        val memory = instance.memory()

        println("Memory before init: ${memory.sizeBytes() / 1024 / 1024}MB")
        runner.initialize(32)
        println("Memory after init: ${memory.sizeBytes() / 1024 / 1024}MB")

        // Run 60 frames — enough for demo to start, load e1m3, and play several frames.
        // Without the fix, "Mod_ForName: NULL name" fires during the BSP model loading
        // and results in a WasmTrap from the Sys_Error→longjmp recovery path.
        println("\n--- Running 60 frames ---")
        val deltaTime = 1.0f / 30.0f
        var trapCount = 0
        for (frameIndex in 1..60) {
            try {
                runner.frame(deltaTime)
            } catch (trap: WasmTrap) {
                trapCount++
                if (trapCount <= 5) {
                    println("Frame $frameIndex trap: ${trap.message}")
                }
            }
        }

        println("\nTraps: $trapCount")
        println("Result: ${if (trapCount == 0) "SUCCESS" else "FAILED ($trapCount traps)"}")

        runner.shutdown()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val test = QuakeHunkOverlapTest()
            if (!Files.exists(test.wasmPath)) {
                println("quake.wasm not found")
                return
            }
            test.testDemoPlayback()
        }
    }
}
