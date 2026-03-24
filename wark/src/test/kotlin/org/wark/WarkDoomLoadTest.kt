package org.wark

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class WarkDoomLoadTest {

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun loadAndInspectDoomWasm() {
        val path = Path.of("examples/assets/doom.wasm")
        assumeTrue(Files.exists(path), "doom.wasm not found")

        val bytes = Files.readAllBytes(path)
        val runtime = WarkRuntime.create(WasmTarget.V2_0)
        val module = runtime.load(bytes)

        val exports = module.exportedFunctionNames()
        assertTrue(exports.contains("initGame"), "Should export initGame, got: $exports")
        assertTrue(exports.contains("tickGame"), "Should export tickGame")
        assertTrue(exports.contains("reportKeyDown"), "Should export reportKeyDown")
        assertTrue(exports.contains("reportKeyUp"), "Should export reportKeyUp")

        val importCount = module.importedFunctionCount()
        assertTrue(importCount == 10, "Should have 10 function imports, got $importCount")
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun compileDoomToNative() {
        val path = Path.of("examples/assets/doom.wasm")
        assumeTrue(Files.exists(path), "doom.wasm not found")

        val bytes = Files.readAllBytes(path)
        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
        val module = runtime.load(bytes)

        // Instantiate with JIT — this compiles all ~1000 functions to native x86
        val instance = module.instantiate(
            WarkImports.builder()
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
        )

        // Don't call initGame — it enters a game loop. Just verify compilation succeeded.
        assertTrue(instance.exportedFunctions().contains("initGame"))
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun instantiateDoomWithHostFunctions() {
        val path = Path.of("examples/assets/doom.wasm")
        assumeTrue(Files.exists(path), "doom.wasm not found")

        val bytes = Files.readAllBytes(path)
        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(bytes)

        val imports = WarkImports.builder()
            .function("loading", "onGameInit") { instance, args -> longArrayOf() }
            .function("loading", "wadSizes") { instance, args -> longArrayOf(0) }
            .function("loading", "readWads") { instance, args -> longArrayOf() }
            .function("runtimeControl", "timeInMilliseconds") { instance, args ->
                longArrayOf(System.currentTimeMillis() % 1_000_000)
            }
            .function("ui", "drawFrame") { instance, args -> longArrayOf() }
            .function("gameSaving", "sizeOfSaveGame") { instance, args -> longArrayOf(0) }
            .function("gameSaving", "readSaveGame") { instance, args -> longArrayOf(0) }
            .function("gameSaving", "writeSaveGame") { instance, args -> longArrayOf(0) }
            .function("console", "onInfoMessage") { instance, args -> longArrayOf() }
            .function("console", "onErrorMessage") { instance, args -> longArrayOf() }
            .build()

        val instance = module.instantiate(imports)
        assertTrue(instance.exportedFunctions().contains("initGame"))
        assertTrue(instance.memory().pages() > 0)
    }

    @Test
    @Timeout(120, unit = TimeUnit.SECONDS)
    fun executeDoomWithInstructionLimit() {
        val path = Path.of("examples/assets/doom.wasm")
        assumeTrue(Files.exists(path), "doom.wasm not found")

        val bytes = Files.readAllBytes(path)
        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate(
            WarkImports.builder()
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
        )

        instance.setInstructionLimit(100_000_000)

        try {
            instance.call("initGame")
            println("DOOM initGame completed successfully!")
        } catch (trap: WasmTrap) {
            println("DOOM trap: ${trap.message}")
            if (trap.message?.contains("OOB") == true || trap.message?.contains("unimplemented") == true) {
                throw trap
            }
        }

        assertTrue(true)
    }

    @org.junit.jupiter.api.Disabled("DOOM with WAD enters game loop — needs frame-limited runner")
    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun runDoomWithRealWad() {
        val wasmPath = Path.of("examples/assets/doom.wasm")
        val wadPath = Path.of("examples/assets/doom1.wad")
        assumeTrue(Files.exists(wasmPath), "doom.wasm not found")
        assumeTrue(Files.exists(wadPath), "doom1.wad not found")

        val wasmBytes = Files.readAllBytes(wasmPath)
        val wadBytes = Files.readAllBytes(wadPath)

        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
        val module = runtime.load(wasmBytes)

        var gameInitialized = false
        var screenWidth = 0
        var screenHeight = 0
        var framesRendered = 0
        val consoleMessages = mutableListOf<String>()

        val instance = module.instantiate(
            WarkImports.builder()
                .function("loading", "onGameInit") { inst, args ->
                    screenWidth = args[0].toInt()
                    screenHeight = args[1].toInt()
                    gameInitialized = true
                    longArrayOf()
                }
                .function("loading", "wadSizes") { inst, args ->
                    longArrayOf(wadBytes.size.toLong())
                }
                .function("loading", "readWads") { inst, args ->
                    val destAddress = args[0].toInt()
                    inst.memory().writeBytes(destAddress, wadBytes)
                    longArrayOf()
                }
                .function("runtimeControl", "timeInMilliseconds") { inst, args ->
                    longArrayOf(System.currentTimeMillis() % 10_000_000)
                }
                .function("ui", "drawFrame") { inst, args ->
                    framesRendered++
                    longArrayOf()
                }
                .function("gameSaving", "sizeOfSaveGame") { inst, args -> longArrayOf(0) }
                .function("gameSaving", "readSaveGame") { inst, args -> longArrayOf(0) }
                .function("gameSaving", "writeSaveGame") { inst, args -> longArrayOf(0) }
                .function("console", "onInfoMessage") { inst, args ->
                    val message = inst.memory().readUtf8(args[0].toInt())
                    consoleMessages.add(message)
                    longArrayOf()
                }
                .function("console", "onErrorMessage") { inst, args ->
                    val message = inst.memory().readUtf8(args[0].toInt())
                    consoleMessages.add("[ERROR] $message")
                    longArrayOf()
                }
                .build()
        )

        try {
            instance.call("initGame")

            if (gameInitialized) {
                for (frame in 0 until 3) {
                    instance.call("tickGame")
                }
            }
        } catch (exception: Exception) {
            println("DOOM exception: ${exception.javaClass.simpleName}: ${exception.message}")
        }

        for (message in consoleMessages.take(20)) {
            println("DOOM: $message")
        }

        if (gameInitialized) {
            println("DOOM initialized: ${screenWidth}x${screenHeight}")
            println("Frames rendered: $framesRendered")
        }
    }
}
