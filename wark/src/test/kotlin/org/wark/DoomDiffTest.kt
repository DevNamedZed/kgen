package org.wark

import java.io.File
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

    val outFile = File("build/doom-diff.txt")
    outFile.parentFile.mkdirs()

    println("Loading interpreter instance...")
    val interpImports = buildImports(wadBytes)
    val interpInstance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        .load(wasmBytes).instantiate(interpImports)

    println("Loading JIT instance...")
    val jitImports = buildImports(wadBytes)
    val jitInstance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
        .load(wasmBytes).instantiate(jitImports)

    println("Running initGame on both...")
    interpInstance.call("initGame")
    jitInstance.call("initGame")

    println("Comparing memory after initGame...")
    val interpMem = interpInstance.memory().readBytes(0, interpInstance.memory().sizeBytes())
    val jitMem = jitInstance.memory().readBytes(0, jitInstance.memory().sizeBytes())

    val sb = StringBuilder()
    var diffCount = 0
    val minSize = minOf(interpMem.size, jitMem.size)
    for (offset in 0 until minSize) {
        if (interpMem[offset] != jitMem[offset]) {
            if (diffCount < 200) {
                sb.appendLine("DIFF @ 0x${offset.toString(16)}: interp=0x${(interpMem[offset].toInt() and 0xFF).toString(16)} jit=0x${(jitMem[offset].toInt() and 0xFF).toString(16)}")
            }
            diffCount++
        }
    }
    sb.appendLine()
    sb.appendLine("Total diffs: $diffCount / $minSize bytes")
    sb.appendLine("Interp memory size: ${interpMem.size}")
    sb.appendLine("JIT memory size: ${jitMem.size}")

    outFile.writeText(sb.toString())
    println("Diffs: $diffCount (wrote to ${outFile.path})")
}

private fun buildImports(wadBytes: ByteArray): WarkImports {
    return WarkImports.builder()
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
}
