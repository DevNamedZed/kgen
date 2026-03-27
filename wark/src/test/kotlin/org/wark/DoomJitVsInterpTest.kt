package org.wark

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Runs DOOM initGame via the (now-correct) interpreter, collecting the
 * call trace. This establishes the ground truth for differential testing
 * against JIT.
 */
class DoomJitVsInterpTest {

    private val doomPath = Path.of("examples/assets/doom.wasm")

    private fun createImports(): WarkImports = WarkImports.builder()
        .function("loading", "onGameInit") { inst, args -> longArrayOf() }
        .function("loading", "wadSizes") { inst, args -> longArrayOf(0) }
        .function("loading", "readWads") { inst, args -> longArrayOf() }
        .function("runtimeControl", "timeInMilliseconds") { inst, args -> longArrayOf(0) }
        .function("ui", "drawFrame") { inst, args -> longArrayOf() }
        .function("gameSaving", "sizeOfSaveGame") { inst, args -> longArrayOf(0) }
        .function("gameSaving", "readSaveGame") { inst, args -> longArrayOf(0) }
        .function("gameSaving", "writeSaveGame") { inst, args -> longArrayOf(0) }
        .function("console", "onInfoMessage") { inst, args -> longArrayOf() }
        .function("console", "onErrorMessage") { inst, args -> longArrayOf() }
        .build()

    @Test
    @Timeout(120, unit = TimeUnit.SECONDS)
    fun interpreterCallTrace() {
        assumeTrue(Files.exists(doomPath))
        val wasmBytes = Files.readAllBytes(doomPath)

        val instance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
            .load(wasmBytes).instantiate(createImports())
        val calls = mutableListOf<String>()
        val interp = instance.interpreter()
        val importCount = instance.module.wasmModule.importedFunctionCount

        interp.onFunctionEntry = { funcIndex, args ->
            if (funcIndex >= importCount && calls.size < 5000) {
                calls.add(interp.functionName(funcIndex))
            }
        }

        instance.setInstructionLimit(100_000_000)
        try {
            instance.call("initGame")
        } catch (trap: WasmTrap) {
            // Expected: instruction limit
        }

        println("Interpreter call trace: ${calls.size} calls")

        // Find func_120 calls
        for ((index, call) in calls.withIndex()) {
            if (call.contains("func_120")) {
                val context = calls.subList(maxOf(0, index - 3), minOf(calls.size, index + 3))
                println("  func_120 at #$index: $context")
            }
        }

        // Write to file for later comparison with JIT trace
        val traceFile = java.io.File("build/doom-interp-trace.txt")
        traceFile.writeText(calls.joinToString("\n"))
    }
}
