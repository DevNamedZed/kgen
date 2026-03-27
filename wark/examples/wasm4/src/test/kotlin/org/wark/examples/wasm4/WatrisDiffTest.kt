package org.wark.examples.wasm4

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class WatrisDiffTest {

    private val watrisPath = Path.of("../assets/wasm4/watris.wasm")

    @Test
    fun watrisUpdateProducesSameMemory() {
        assumeTrue(Files.exists(watrisPath), "watris.wasm not found")
        val bytes = Files.readAllBytes(watrisPath)

        // Boot interpreter
        val interpHost = Wasm4Host()
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate(interpHost.buildImports())
        interpInstance.setInstructionLimit(100_000_000)

        // Boot JIT
        val jitHost = Wasm4Host()
        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(jitHost.buildImports())

        // Call start on both (already called during instantiation via start section)
        // Call exported start if it exists
        if (interpInstance.exportedFunctions().contains("start")) {
            interpInstance.call("start")
            jitInstance.call("start")
        }

        // Compare memory after start
        val memSize = minOf(interpInstance.memory().sizeBytes(), jitInstance.memory().sizeBytes())
        var initDiffs = 0
        for (addr in 0 until memSize step 4) {
            if (interpInstance.memory().readI32(addr) != jitInstance.memory().readI32(addr)) {
                initDiffs++
            }
        }
        println("After start: $initDiffs memory diffs")

        // Run 5 update frames on both
        for (frame in 1..5) {
            interpInstance.call("update")
            jitInstance.call("update")

            var diffs = 0
            val diffAddrs = mutableListOf<String>()
            for (addr in 0 until memSize step 4) {
                val iv = interpInstance.memory().readI32(addr)
                val jv = jitInstance.memory().readI32(addr)
                if (iv != jv) {
                    diffs++
                    if (diffAddrs.size < 5) {
                        diffAddrs.add("0x${addr.toString(16)}: interp=$iv jit=$jv")
                    }
                }
            }
            println("Frame $frame: $diffs diffs ${diffAddrs.joinToString(", ")}")

            // Compare globals
            val globalCount = interpInstance.module.wasmModule.globals.size
            for (g in 0 until globalCount) {
                val iv = interpInstance.global(g).rawValue()
                val jv = jitInstance.global(g).rawValue()
                if (iv != jv) {
                    println("  global[$g]: interp=$iv jit=$jv")
                }
            }
        }

        // After 5 frames, check framebuffer
        var interpPixels = 0
        var jitPixels = 0
        for (y in 0 until 160) {
            for (x in 0 until 160) {
                if (interpHost.getPixel(x, y) != 0) { interpPixels++ }
                if (jitHost.getPixel(x, y) != 0) { jitPixels++ }
            }
        }
        println("Framebuffer: interp=$interpPixels jit=$jitPixels non-zero pixels")

        // The key test: are they the same?
        assertEquals(0, initDiffs, "Memory should match after start")
    }
}
