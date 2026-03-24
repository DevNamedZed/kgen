package org.wark

import org.kgen.target.wasm.module.WasmModule
import org.kgen.target.wasm.module.WasmModuleReader
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val wasmBytes = Files.readAllBytes(Path.of("examples/assets/doom.wasm"))
    val module = WasmModuleReader.read(wasmBytes)

    val importCount = module.importedFunctionCount
    println("Imports: $importCount")
    println("Functions: ${module.functions.size}")
    println()

    // Element sections (function table)
    println("Element sections: ${module.elements.size}")
    for ((elemIdx, elem) in module.elements.withIndex()) {
        if (elem is WasmModule.Element.Active) {
            println("  elem[$elemIdx]: offset=${elem.offsetExpr}, ${elem.funcIndices.size} entries")
            for ((slot, funcIndex) in elem.funcIndices.withIndex()) {
                val globalIdx = funcIndex
                val name = module.functionName(globalIdx) ?: "func_${funcIndex - importCount}"
                println("    slot $slot -> func $funcIndex ($name)")
            }
        }
    }

    // Count call_indirect usage
    var callIndirectCount = 0
    for (func in module.functions) {
        val body = func.body
        var offset = 0
        while (offset < body.size) {
            if (body[offset].toInt() and 0xFF == 0x11) {
                callIndirectCount++
            }
            offset++
        }
    }
    println("\ncall_indirect occurrences (approx): $callIndirectCount")
    println()

    for ((index, func) in module.functions.withIndex()) {
        val globalIndex = index + importCount
        val name = module.functionName(globalIndex) ?: "func_$index"
        val funcType = module.types[func.typeIndex]
        val params = funcType.params.joinToString(", ") { it.name }
        val results = funcType.results.joinToString(", ") { it.name }
        val sig = "($params) -> $results"
        println("func_$index [global=$globalIndex] $name $sig (${func.body.size} bytes)")
    }
}
