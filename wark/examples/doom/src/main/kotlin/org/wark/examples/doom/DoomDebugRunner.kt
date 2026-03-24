package org.wark.examples.doom

import org.wark.WarkImports
import org.wark.debug.DebuggerRepl
import org.wark.debug.WarkDebugger
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path

/**
 * DOOM-specific debug runner. Wires DOOM host imports and launches
 * the debugger REPL with DOOM ready to execute.
 *
 * ```
 * gradlew :wark:examples:doom:runDebug
 * wark> call initGame 1000000
 * wark> trace 20
 * wark> compare 55 0x366B 1 16 0x427848
 * ```
 */
object DoomDebugRunner {

    @JvmStatic
    fun main(args: Array<String>) {
        val filteredArgs = args.filter { it != "--bounds" }
        val boundsCheck = args.any { it == "--bounds" }
        val wasmPath = Path.of(filteredArgs.getOrNull(0) ?: "examples/assets/doom.wasm")
        val wadPath = filteredArgs.getOrNull(1)?.let { Path.of(it) }

        if (!Files.exists(wasmPath)) {
            System.err.println("doom.wasm not found at $wasmPath")
            System.err.println("Usage: DoomDebugRunner [doom.wasm] [doom1.wad]")
            return
        }

        val wadData = if (wadPath != null && Files.exists(wadPath)) {
            Files.readAllBytes(wadPath)
        } else {
            null
        }

        val host = DoomHost(wadData ?: ByteArray(0))
        val builder = WarkImports.builder()
        host.registerImports(builder)

        println("Loading $wasmPath...")
        val debugger = WarkDebugger(wasmPath, builder.build())
        debugger.load()

        if (boundsCheck) {
            debugger.boundsChecking = true
            println("Bounds checking: enabled")
        }

        println("Compiling JIT...")
        if (debugger.loadJit()) {
            println("JIT ready (${debugger.functionCount} functions)")
        } else {
            println("JIT compilation failed")
        }

        println()
        DebuggerRepl(debugger, PrintWriter(System.out, true), "doom").run()
    }
}
