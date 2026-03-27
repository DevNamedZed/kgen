package org.wark

import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val wasmPath = Path.of("examples/assets/doom.wasm")
    val wadPath = Path.of("examples/assets/doom1.wad")
    if (!Files.exists(wasmPath) || !Files.exists(wadPath)) {
        println("doom.wasm or doom1.wad not found")
        return
    }
    val wasmBytes = Files.readAllBytes(wasmPath)
    val wadBytes = Files.readAllBytes(wadPath)

    println("=== call_indirect JIT vs Interpreter Test ===")
    println()

    // Create interpreter instance with full init
    println("Creating interpreter instance...")
    val interpInstance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        .load(wasmBytes).instantiate(buildTestImports(wadBytes))
    interpInstance.setInstructionLimit(Long.MAX_VALUE)
    println("Running initGame (interpreter)...")
    interpInstance.call("initGame")

    // Create JIT instance with full init
    println("Creating JIT instance...")
    val jitInstance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
        .load(wasmBytes).instantiate(buildTestImports(wadBytes))
    println("Running initGame (JIT)...")
    jitInstance.call("initGame")

    // Sync JIT memory from interpreter (so both have identical state)
    println("Syncing memory from interpreter to JIT...")
    val interpMem = interpInstance.memory()
    val jitMem = jitInstance.memory()
    while (jitMem.pages() < interpMem.pages()) {
        jitMem.grow(1)
    }
    val memBytes = interpMem.readBytes(0, interpMem.sizeBytes())
    jitMem.writeBytes(0, memBytes)

    // Now tick both a few times and compare
    println("Ticking both (5 ticks)...")
    for (tick in 1..5) {
        try {
            interpInstance.call("tickGame")
        } catch (exception: Exception) {
            println("  Interpreter tick $tick error: ${exception.message}")
        }
        try {
            jitInstance.call("tickGame")
        } catch (exception: Exception) {
            println("  JIT tick $tick error: ${exception.message}")
        }

        // Compare memory after each tick
        val interpBytes = interpInstance.memory().readBytes(0, interpInstance.memory().sizeBytes())
        val jitBytes = jitInstance.memory().readBytes(0, jitInstance.memory().sizeBytes())
        var diffs = 0
        val minLen = minOf(interpBytes.size, jitBytes.size)
        for (offset in 0 until minLen) {
            if (interpBytes[offset] != jitBytes[offset]) {
                diffs++
            }
        }
        println("  Tick $tick: $diffs byte differences")
    }
}

private fun buildTestImports(wadBytes: ByteArray): WarkImports = WarkImports.builder()
    .function("loading", "onGameInit") { _, _ -> longArrayOf() }
    .function("loading", "wadSizes") { _, _ -> longArrayOf(wadBytes.size.toLong()) }
    .function("loading", "readWads") { instance, args ->
        instance.memory().writeBytes(args[0].toInt(), wadBytes)
        longArrayOf()
    }
    .function("runtimeControl", "timeInMilliseconds") { _, _ -> longArrayOf(0) }
    .function("ui", "drawFrame") { _, _ -> longArrayOf() }
    .function("gameSaving", "sizeOfSaveGame") { _, _ -> longArrayOf(0) }
    .function("gameSaving", "readSaveGame") { _, _ -> longArrayOf(0) }
    .function("gameSaving", "writeSaveGame") { _, _ -> longArrayOf(0) }
    .function("console", "onInfoMessage") { _, _ -> longArrayOf() }
    .function("console", "onErrorMessage") { _, _ -> longArrayOf() }
    .build()
