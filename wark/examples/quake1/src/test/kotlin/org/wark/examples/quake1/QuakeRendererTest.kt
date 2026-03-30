package org.wark.examples.quake1

import org.wark.ExecutionMode
import org.wark.WasmTrap
import java.nio.file.Files
import java.nio.file.Path

/**
 * Investigates "R_RenderView: called without enough stack" during demo playback.
 * This error appears after fixing the Mod_ForName overlap — it was previously
 * masked by the earlier crash.
 */
class QuakeRendererTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    private fun findFunctionsBySubstring(
        instance: org.wark.WarkInstance,
        substring: String
    ): List<Pair<Int, String>> {
        val wasmModule = instance.module.wasmModule
        val importCount = wasmModule.importedFunctionCount
        val totalFunctions = importCount + wasmModule.functions.size
        val results = mutableListOf<Pair<Int, String>>()
        for (index in 0 until totalFunctions) {
            val name = wasmModule.functionName(index) ?: continue
            if (name.contains(substring, ignoreCase = true)) {
                results.add(index to name)
            }
        }
        return results
    }

    fun investigate() {
        val runner = QuakeRunner.load(wasmPath, gameDirectory, ExecutionMode.INTERPRET)
        val instance = runner.instance()
        val memory = instance.memory()

        println("Memory: ${memory.sizeBytes() / 1024 / 1024}MB")
        runner.initialize(32)
        println("Memory after init: ${memory.sizeBytes() / 1024 / 1024}MB")

        // List renderer functions
        println("\n=== Renderer functions ===")
        for ((index, name) in findFunctionsBySubstring(instance, "R_Render")) {
            println("  func[$index] = $name")
        }
        for ((index, name) in findFunctionsBySubstring(instance, "R_Edge")) {
            println("  func[$index] = $name")
        }
        for ((index, name) in findFunctionsBySubstring(instance, "R_NewMap")) {
            println("  func[$index] = $name")
        }
        for ((index, name) in findFunctionsBySubstring(instance, "R_Scan")) {
            println("  func[$index] = $name")
        }

        val sysErrorIndex = findFunctionsBySubstring(instance, "Sys_Error").firstOrNull()?.first ?: -1
        val rRenderViewIndex = findFunctionsBySubstring(instance, "R_RenderView").firstOrNull()?.first ?: -1

        println("\nSys_Error: func[$sysErrorIndex]")
        println("R_RenderView: func[$rRenderViewIndex]")

        // Track which functions lead to the error
        var lastRendererCall = ""
        var errorHit = false

        instance.setFunctionEntryCallback { funcIndex, args ->
            val name = instance.module.wasmModule.functionName(funcIndex) ?: return@setFunctionEntryCallback
            if (name.startsWith("R_") || name.startsWith("D_")) {
                lastRendererCall = name
            }
            if (funcIndex == sysErrorIndex && !errorHit) {
                errorHit = true
                val formatPointer = args[0].toInt()
                val errorMessage = if (formatPointer != 0) {
                    try { memory.readUtf8(formatPointer) } catch (e: Exception) { "<read error>" }
                } else {
                    "<NULL>"
                }
                println(">>> Sys_Error in $lastRendererCall: \"$errorMessage\" <<<")
            }
        }

        println("\n=== Running 60 frames ===")
        val deltaTime = 1.0f / 30.0f
        for (frameIndex in 1..60) {
            try {
                runner.frame(deltaTime)
            } catch (trap: WasmTrap) {
                // continue
            }
            if (errorHit) {
                println("Error hit at frame $frameIndex")
                break
            }
        }

        runner.shutdown()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val test = QuakeRendererTest()
            if (!Files.exists(test.wasmPath)) {
                println("quake.wasm not found")
                return
            }
            test.investigate()
        }
    }
}
