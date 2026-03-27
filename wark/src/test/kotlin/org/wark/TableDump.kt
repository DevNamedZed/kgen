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

    // Use reflection to call buildFunctionTable (it's private)
    val compiler = WasmToIrCompiler(Target.native(), module)
    val method = compiler.javaClass.getDeclaredMethod("buildFunctionTable")
    method.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val table = method.invoke(compiler) as List<Int>

    println("Table size: ${table.size}")
    // Show slots 135-145
    for (slot in 135..145) {
        if (slot < table.size) {
            val funcIndex = table[slot]
            val name = if (funcIndex >= 0) {
                module.functionName(funcIndex) ?: "func_${funcIndex - importCount}"
            } else {
                "<empty>"
            }
            println("  OUR slot $slot: funcIndex=$funcIndex = $name")
        }
    }
}
