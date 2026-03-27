package org.wark.examples.wasm4

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import java.nio.file.Files
import java.nio.file.Path

class WatrisCollisionTest {

    private val watrisPath = Path.of("../assets/wasm4/watris.wasm")

    @Test
    fun traceUpdateCallsAndReturns() {
        assumeTrue(Files.exists(watrisPath), "watris.wasm not found")
        val bytes = Files.readAllBytes(watrisPath)
        val host = Wasm4Host()
        val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate(host.buildImports())
        instance.setInstructionLimit(100_000_000)

        val importCount = instance.module.wasmModule.importedFunctionCount

        if (instance.exportedFunctions().contains("_initialize")) {
            instance.call("_initialize")
        }
        if (instance.exportedFunctions().contains("start")) {
            instance.call("start")
        }
        println("After start, memory[0xA0..0xA4]: ${instance.memory().readI32(0xA0)}")

        // Run 60 frames
        for (frame in 1..60) {
            try {
                instance.call("update")
            } catch (e: Exception) {
                println("Frame $frame ERROR: ${e.message}")
                break
            }
            host.readFramebuffer(instance)
            host.readFramebuffer(instance)
            val pixels = (0 until 160 * 160).count { host.getPixel(it % 160, it / 160) != 0 }
            if (frame <= 10 || frame % 10 == 0) {
                println("Frame $frame: $pixels pixels")
            }
        }
        // Check the typed block in func_9 (update) — read some game state
        // The game board is at memory after data segments (0x19A0+)
        val mem = instance.memory()
        println("Game state at 0x2A00-0x2A20:")
        for (addr in 0x2A00 until 0x2A20 step 4) {
            println("  [0x${addr.toString(16)}] = ${mem.readI32(addr)}")
        }
    }
}
