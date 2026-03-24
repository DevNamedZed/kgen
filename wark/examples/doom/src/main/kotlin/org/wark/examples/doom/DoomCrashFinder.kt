package org.wark.examples.doom

import org.wark.WarkImports
import org.wark.debug.WarkDebugger
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests JIT-compiled DOOM functions to find which one crashes.
 *
 * Usage:
 *   DoomCrashFinder              — test all functions with zero args
 *   DoomCrashFinder 55 1 2 3 4   — test func_55 with args 1,2,3,4
 *   DoomCrashFinder scan          — test functions 0-817 one by one
 */
object DoomCrashFinder {

    @JvmStatic
    fun main(args: Array<String>) {
        val wasmPath = Path.of("../assets/doom.wasm")
        if (!Files.exists(wasmPath)) {
            System.err.println("doom.wasm not found at $wasmPath")
            return
        }

        val host = DoomHost(ByteArray(0))
        val builder = WarkImports.builder()
        host.registerImports(builder)

        val debugger = WarkDebugger(wasmPath, builder.build())
        debugger.load()
        debugger.loadJit()

        if (args.isEmpty() || args[0] == "scan") {
            scanAllFunctions(debugger)
        } else {
            val localIndex = args[0].toInt()
            val funcArgs = args.drop(1).map { it.toLong() }.toLongArray()
            testSingleFunction(debugger, localIndex, funcArgs)
        }
    }

    private fun testSingleFunction(debugger: WarkDebugger, localIndex: Int, funcArgs: LongArray) {
        val globalIndex = localIndex + debugger.importCount
        val info = debugger.functionInfo(localIndex)
        println("Testing func_$localIndex (${info.name})")
        println("  Signature: (${info.params.joinToString(", ")}) -> (${info.results.joinToString(", ")})")
        println("  Args: ${funcArgs.toList()}")

        val interpResult = debugger.callByIndex(globalIndex, *funcArgs)
        println("  Interpreter: ${formatResult(interpResult)}")

        println("  Calling JIT...")
        System.out.flush()
        val jitResult = debugger.jitCall(globalIndex, *funcArgs)
        println("  JIT: ${formatResult(jitResult)}")

        if (interpResult.completed && jitResult.completed) {
            if (interpResult.result.toList() == jitResult.result.toList()) {
                println("  MATCH")
            } else {
                println("  MISMATCH: interp=${interpResult.result.toList()}, jit=${jitResult.result.toList()}")
            }
        }
    }

    private fun scanAllFunctions(debugger: WarkDebugger) {
        println("Scanning ${debugger.functionCount} functions with zero args...")
        var crashes = 0
        var mismatches = 0

        for (localIndex in 0 until debugger.functionCount) {
            val globalIndex = localIndex + debugger.importCount
            val info = debugger.functionInfo(localIndex)
            val argCount = info.params.size
            val testArgs = LongArray(argCount) { 0L }

            // Interpreter first (safe)
            val interpResult = debugger.callByIndex(globalIndex, *testArgs)

            // JIT (may crash JVM)
            System.out.flush()
            val jitResult = debugger.jitCall(globalIndex, *testArgs)

            val status = when {
                jitResult.trap != null -> "TRAP"
                !interpResult.completed && !jitResult.completed -> "both-trap"
                interpResult.completed && jitResult.completed &&
                    interpResult.result.toList() == jitResult.result.toList() -> "ok"
                interpResult.completed && jitResult.completed -> {
                    mismatches++
                    "MISMATCH"
                }
                else -> "?"
            }

            if (status == "MISMATCH") {
                println("  func_$localIndex: $status interp=${interpResult.result.toList()} jit=${jitResult.result.toList()}")
            }

            if (localIndex % 100 == 0) {
                System.out.println("  $localIndex/${debugger.functionCount} tested ($mismatches mismatches)")
                System.out.flush()
            }
        }

        println("\nDone. $mismatches mismatches, $crashes crashes.")
    }

    private fun formatResult(result: org.wark.debug.CallResult): String {
        return when {
            result.completed -> result.result.toList().toString()
            result.trap != null -> "TRAP: ${result.trap}"
            result.paused -> "paused"
            else -> "?"
        }
    }
}
