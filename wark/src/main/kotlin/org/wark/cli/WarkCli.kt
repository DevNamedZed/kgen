package org.wark.cli

import org.wark.*
import org.wark.debug.DebuggerRepl
import org.wark.debug.WarkDebugger
import java.nio.file.Path

/**
 * Command-line interface for wark.
 *
 * ```
 * wark run hello.wasm
 * wark run --jit program.wasm -- arg1 arg2
 * wark run --target v2.0 game.wasm
 * ```
 */
object WarkCli {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }

        when (args[0]) {
            "run" -> runCommand(args.drop(1))
            "debug" -> debugCommand(args.drop(1))
            "version" -> println("wark 0.1.0")
            "help", "--help", "-h" -> printUsage()
            else -> {
                if (args[0].endsWith(".wasm")) {
                    runCommand(args.toList())
                } else {
                    System.err.println("Unknown command: ${args[0]}")
                    printUsage()
                }
            }
        }
    }

    private fun runCommand(args: List<String>) {
        var mode = ExecutionMode.INTERPRET
        var target = WasmTarget.V2_0
        var wasmFile: String? = null
        val programArgs = mutableListOf<String>()
        var parsingFlags = true

        var index = 0
        while (index < args.size) {
            val arg = args[index]
            if (parsingFlags) {
                when (arg) {
                    "--jit" -> mode = ExecutionMode.JIT
                    "--interpret" -> mode = ExecutionMode.INTERPRET
                    "--target" -> { index++; target = parseTarget(args.getOrElse(index) { "v2.0" }) }
                    "--" -> { parsingFlags = false }
                    else -> {
                        if (arg.startsWith("-")) {
                            System.err.println("Unknown flag: $arg")
                            return
                        }
                        wasmFile = arg
                        parsingFlags = false
                    }
                }
            } else {
                programArgs.add(arg)
            }
            index++
        }

        if (wasmFile == null) {
            System.err.println("No .wasm file specified")
            return
        }

        val exitCode = WarkRunner.builder()
            .file(Path.of(wasmFile))
            .mode(mode)
            .target(target)
            .args(programArgs)
            .build()
            .run()

        if (exitCode != 0) {
            System.exit(exitCode)
        }
    }

    private fun debugCommand(args: List<String>) {
        var wasmFile: String? = null
        var wadFile: String? = null
        var index = 0
        while (index < args.size) {
            when (args[index]) {
                "--wad" -> { index++; wadFile = args.getOrNull(index) }
                else -> {
                    if (!args[index].startsWith("-")) {
                        wasmFile = args[index]
                    }
                }
            }
            index++
        }
        if (wasmFile == null) {
            System.err.println("Usage: wark debug <file.wasm> [--wad <file.wad>]")
            return
        }

        val wasmPath = java.nio.file.Path.of(wasmFile)
        if (!java.nio.file.Files.exists(wasmPath)) {
            System.err.println("File not found: $wasmFile")
            return
        }

        val wadBytes = if (wadFile != null) {
            val path = java.nio.file.Path.of(wadFile)
            if (java.nio.file.Files.exists(path)) { java.nio.file.Files.readAllBytes(path) } else { null }
        } else {
            null
        }

        val imports = WarkImports.builder()
        if (wadBytes != null) {
            val wad = wadBytes
            imports.function("loading", "onGameInit", HostFunction { _, args -> println("  [host] onGameInit(${args.toList()})"); longArrayOf() })
            imports.function("loading", "wadSizes", HostFunction { _, _ -> longArrayOf(wad.size.toLong()) })
            imports.function("loading", "readWads", HostFunction { inst, args -> inst.memory().writeBytes(args[0].toInt(), wad); longArrayOf() })
            imports.function("runtimeControl", "timeInMilliseconds", HostFunction { _, _ -> longArrayOf(System.currentTimeMillis()) })
            imports.function("ui", "drawFrame", HostFunction { _, _ -> longArrayOf() })
            imports.function("gameSaving", "sizeOfSaveGame", HostFunction { _, _ -> longArrayOf(0) })
            imports.function("gameSaving", "readSaveGame", HostFunction { _, _ -> longArrayOf(0) })
            imports.function("gameSaving", "writeSaveGame", HostFunction { _, _ -> longArrayOf(0) })
            imports.function("console", "onInfoMessage", HostFunction { _, _ -> longArrayOf() })
            imports.function("console", "onErrorMessage", HostFunction { _, _ -> longArrayOf() })
        }

        val debugger = WarkDebugger(wasmPath, imports.build())
        debugger.load()
        debugger.loadJit()
        DebuggerRepl(debugger).run()
    }

    private fun parseTarget(value: String): WasmTarget = when (value.lowercase()) {
        "mvp", "1.0" -> WasmTarget.MVP
        "2.0", "v2.0" -> WasmTarget.V2_0
        "3.0", "v3.0" -> WasmTarget.V3_0
        "latest" -> WasmTarget.LATEST
        else -> {
            System.err.println("Unknown target: $value (using v2.0)")
            WasmTarget.V2_0
        }
    }

    private fun printUsage() {
        println("""
            |wark - WASM runtime
            |
            |Usage:
            |  wark run [options] <file.wasm> [-- args...]
            |  wark debug <file.wasm> [--wad <file.wad>]
            |  wark version
            |  wark help
            |
            |Run options:
            |  --jit         JIT compile (native speed, x86/ARM64)
            |  --interpret   Interpret bytecode (default, portable)
            |  --target      WASM target: mvp, 2.0, 3.0, latest
            |
            |Debug: interactive WASM debugger with stepping, breakpoints,
            |       memory inspection, and JIT comparison.
        """.trimMargin())
    }
}
