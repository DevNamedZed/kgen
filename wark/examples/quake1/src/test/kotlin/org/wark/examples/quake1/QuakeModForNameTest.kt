package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import org.wark.WasmTrap
import java.nio.file.Files
import java.nio.file.Path

/**
 * Investigates why Mod_ForName receives a NULL name during e1m3 demo playback.
 * Uses memory watchpoint to catch the exact write that corrupts model_precache[2].
 */
class QuakeModForNameTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")
    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

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

    private fun findFunctionIndex(instance: org.wark.WarkInstance, name: String): Int {
        return findFunctionsBySubstring(instance, name)
            .firstOrNull { it.second == name }?.first ?: -1
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun traceModForNameCalls() {
        val runner = QuakeRunner.load(wasmPath, gameDirectory, ExecutionMode.INTERPRET)
        runner.initialize()

        val instance = runner.instance()
        val memory = instance.memory()

        // Find key functions
        val modForNameIndex = findFunctionIndex(instance, "Mod_ForName")
        val sysErrorIndex = findFunctionIndex(instance, "Sys_Error")
        val clParseServerInfoIndex = findFunctionIndex(instance, "CL_ParseServerInfo")
        val modLoadBrushModelIndex = findFunctionIndex(instance, "Mod_LoadBrushModel")

        println("=== Key function indices ===")
        println("  Mod_ForName:         $modForNameIndex")
        println("  CL_ParseServerInfo:  $clParseServerInfoIndex")
        println("  Mod_LoadBrushModel:  $modLoadBrushModelIndex")
        println("  Memory size: ${memory.sizeBytes()} (0x${memory.sizeBytes().toString(16)})")

        // Track current function for watchpoint correlation
        var currentFunction = ""
        val callStack = mutableListOf<String>()
        var modForNameCallCount = 0
        var errorHit = false
        var watchHitCount = 0

        // Set memory watchpoint on model_precache[2] (0x1ffbfb0)
        // This is where the second model name should be but gets corrupted
        val watchTarget = 0x1ffbfb0
        memory.watchAddress = watchTarget
        memory.watchCallback = { writeOffset, newValue, oldValue ->
            watchHitCount++
            if (watchHitCount <= 20) {
                println("  !!! WRITE to 0x${writeOffset.toString(16)}: " +
                    "old=0x${oldValue.toString(16)} new=0x${newValue.toString(16)} " +
                    "in: $currentFunction")
                println("      Call stack: ${callStack.takeLast(8).joinToString(" → ")}")
            }
        }

        // Build a name lookup table for the most common functions
        val wasmModule = instance.module.wasmModule
        val importCount = wasmModule.importedFunctionCount
        val totalFunctions = importCount + wasmModule.functions.size
        val functionNames = HashMap<Int, String>(totalFunctions)
        for (index in 0 until totalFunctions) {
            val name = wasmModule.functionName(index)
            if (name != null) {
                functionNames[index] = name
            }
        }

        instance.setFunctionEntryCallback { funcIndex, args ->
            val funcName = functionNames[funcIndex] ?: "func_$funcIndex"
            currentFunction = funcName

            // Maintain a bounded call stack approximation
            callStack.add(funcName)
            if (callStack.size > 30) {
                callStack.removeAt(0)
            }

            when (funcIndex) {
                clParseServerInfoIndex -> {
                    println("\n>>> CL_ParseServerInfo entered <<<")
                    // Read memory before to show it's clean
                    val beforeContent = try {
                        memory.readUtf8(watchTarget)
                    } catch (e: Exception) { "<read error>" }
                    println("  model_precache[2] before: \"$beforeContent\" " +
                        "(first byte: 0x${(memory.readBytes(watchTarget, 1)[0].toInt() and 0xFF).toString(16)})")
                }

                modForNameIndex -> {
                    modForNameCallCount++
                    val namePointer = args[0].toInt()
                    val crashFlag = if (args.size > 1) args[1].toInt() else -1
                    val modelName = if (namePointer != 0) {
                        try { memory.readUtf8(namePointer) } catch (e: Exception) { "<read error>" }
                    } else {
                        "<NULL ptr>"
                    }
                    println("Mod_ForName #$modForNameCallCount: ptr=0x${namePointer.toString(16)} " +
                        "crash=$crashFlag \"$modelName\"")

                    if (modelName.isEmpty()) {
                        println("  >>> EMPTY NAME — watchpoint hit count: $watchHitCount <<<")
                    }
                }

                sysErrorIndex -> {
                    if (!errorHit) {
                        errorHit = true
                        val formatPointer = args[0].toInt()
                        val errorMessage = if (formatPointer != 0) {
                            try { memory.readUtf8(formatPointer) } catch (e: Exception) { "<read error>" }
                        } else {
                            "<NULL>"
                        }
                        println(">>> Sys_Error: \"$errorMessage\" <<<")
                    }
                }
            }
        }

        println("\n=== Running frames for demo playback ===")
        val deltaTime = 1.0f / 30.0f
        for (frameIndex in 1..300) {
            try {
                runner.frame(deltaTime)
            } catch (trap: WasmTrap) {
                // continue
            }
            if (errorHit) {
                println("Error hit at frame $frameIndex, stopping.")
                break
            }
        }

        println("\n=== Summary ===")
        println("Mod_ForName calls: $modForNameCallCount")
        println("Watchpoint hits on 0x${watchTarget.toString(16)}: $watchHitCount")
        println("Memory size: ${memory.sizeBytes()} bytes (0x${memory.sizeBytes().toString(16)})")

        runner.shutdown()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val test = QuakeModForNameTest()
            if (!test.quakeWasmExists()) {
                println("quake.wasm not found")
                return
            }
            test.traceModForNameCalls()
        }
    }
}
