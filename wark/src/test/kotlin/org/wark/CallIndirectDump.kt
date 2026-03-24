package org.wark

import org.wark.compile.WasmToIrCompiler
import org.kgen.target.wasm.module.WasmModuleReader
import org.kgen.ir.target.Target
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val wasmBytes = Files.readAllBytes(Path.of("examples/assets/doom.wasm"))
    val wasmModule = WasmModuleReader.read(wasmBytes)
    val compiler = WasmToIrCompiler(Target.x86_64(), wasmModule)
    val irModule = compiler.compileAll()

    println("IR functions: ${irModule.functions.size}")
    val dispatchers = irModule.functions.filter { it.name.startsWith("__wark_call_indirect") }
    println("Dispatchers: ${dispatchers.size}")
    for (dispatcher in dispatchers) {
        val paramTypes = dispatcher.params.joinToString(", ") { "${it.name}: ${it.type}" }
        println("  ${dispatcher.name}($paramTypes) -> ${dispatcher.returnType}")
        println("    blocks: ${dispatcher.blocks.size}")
    }

    // Check which type indices are referenced by call_indirect in the WASM
    println()
    println("call_indirect type indices used in WASM:")
    val usedTypeIndices = mutableSetOf<Int>()
    for (func in wasmModule.functions) {
        val body = func.body
        var offset = 0
        while (offset < body.size - 2) {
            if ((body[offset].toInt() and 0xFF) == 0x11) {
                // call_indirect: next bytes are type_index (LEB128) and table_index
                var typeIdx = 0
                var shift = 0
                var pos = offset + 1
                while (pos < body.size) {
                    val byte = body[pos].toInt() and 0xFF
                    typeIdx = typeIdx or ((byte and 0x7F) shl shift)
                    pos++
                    if (byte and 0x80 == 0) { break }
                    shift += 7
                }
                usedTypeIndices.add(typeIdx)
            }
            offset++
        }
    }
    for (typeIdx in usedTypeIndices.sorted()) {
        val funcType = wasmModule.types[typeIdx]
        val params = funcType.params.joinToString(", ") { it.name }
        val results = funcType.results.joinToString(", ") { it.name }
        val dispatcherName = "__wark_call_indirect_type$typeIdx"
        val exists = dispatchers.any { it.name == dispatcherName }
        println("  type $typeIdx: ($params) -> $results  dispatcher=${if (exists) "YES" else "MISSING"}")
    }
}
