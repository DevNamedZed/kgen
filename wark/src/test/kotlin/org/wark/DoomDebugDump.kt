package org.wark

import org.kgen.target.wasm.module.WasmModuleReader
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val wasmBytes = Files.readAllBytes(Path.of("examples/assets/doom_wasm_debug.wasm"))
    val module = WasmModuleReader.read(wasmBytes)
    val importCount = module.importedFunctionCount

    println("Imports: $importCount, Functions: ${module.functions.size}")

    var named = 0
    for ((index, func) in module.functions.withIndex()) {
        val globalIndex = index + importCount
        val name = module.functionName(globalIndex)
        if (name != null) { named++ }
    }
    println("Named: $named / ${module.functions.size}")
    println()

    // Print damage path functions
    for ((index, func) in module.functions.withIndex()) {
        val globalIndex = index + importCount
        val name = module.functionName(globalIndex) ?: continue
        if (index in listOf(123, 122, 124, 125) ||
            listOf("malloc", "dlmalloc", "sbrk", "Damage", "Random", "Shoot", "Attack",
                "Line", "Aim", "Traverse", "Fixed", "P_", "A_Fire", "PTR_", "Gun",
                "Player", "Think", "Psprite", "Weapon", "BuildTiccmd", "Ticker",
                "Reborn", "Spawn", "SetPsprite").any { name.contains(it, ignoreCase = true) }) {
            println("func_$index: $name (${func.body.size} bytes)")
        }
    }
}
