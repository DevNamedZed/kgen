package org.wark

import org.kgen.ir.target.Target
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.compile.WasmToIrCompiler
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val wasmBytes = Files.readAllBytes(Path.of("examples/assets/doom_wasm_debug.wasm"))
    val module = WasmModuleReader.read(wasmBytes)
    val importCount = module.importedFunctionCount

    // Find PTR_ShootTraverse and PTR_AimTraverse indices
    for ((index, func) in module.functions.withIndex()) {
        val name = module.functionName(index + importCount) ?: continue
        if ("PTR_" in name || "P_LineAttack" == name || "P_PathTraverse" == name) {
            val funcType = module.types[func.typeIndex]
            println("func_$index: $name  typeIndex=${func.typeIndex}  sig=(${funcType.params.joinToString(",")}) -> ${funcType.results.joinToString(",")}")
        }
    }

    // Check the element table for PTR_ShootTraverse
    println()
    println("Element table entries:")
    for (element in module.elements) {
        if (element is org.kgen.target.wasm.module.WasmModule.Element.Active) {
            for ((slot, funcIndex) in element.funcIndices.withIndex()) {
                val globalIndex = funcIndex
                val name = module.functionName(globalIndex)
                if (name != null && "PTR_" in name) {
                    val localIndex = funcIndex - importCount
                    val func = module.functions[localIndex]
                    println("  slot $slot: func_$localIndex ($name)  typeIndex=${func.typeIndex}")
                }
            }
        }
    }

    // Check type section for (I32) -> I32 signature
    println()
    println("Types matching (I32) -> I32:")
    for ((typeIndex, funcType) in module.types.withIndex()) {
        if (funcType.params.size == 1 && funcType.results.size == 1) {
            println("  type $typeIndex: (${funcType.params.joinToString(",")}) -> ${funcType.results.joinToString(",")}")
        }
    }

    // Compile and check dispatchers
    println()
    val compiler = WasmToIrCompiler(Target.native(), module)
    val irModule = compiler.compileAll()
    val dispatchers = irModule.functions.filter { it.name.startsWith("__wark_call_indirect") }
    println("Dispatchers: ${dispatchers.size}")
    for (dispatcher in dispatchers) {
        println("  ${dispatcher.name}: ${dispatcher.blocks.size} blocks")
    }
}
