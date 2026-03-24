package org.wark

import org.kgen.target.wasm.module.WasmModuleReader
import org.kgen.target.wasm.disasm.WasmDisassembler
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val wasmBytes = Files.readAllBytes(Path.of("examples/assets/doom.wasm"))
    val module = WasmModuleReader.read(wasmBytes)
    val disasm = WasmDisassembler()
    val importCount = module.importedFunctionCount

    // Find FixedMul: (i32, i32) -> i32 with i64.mul pattern
    for ((index, func) in module.functions.withIndex()) {
        val funcType = module.types[func.typeIndex]
        if (funcType.params.size != 2) { continue }

        val instructions = disasm.disassemble(func.body)
        val mnemonics = instructions.map { it.opcode.mnemonic }
        val text = mnemonics.joinToString(" ")

        // FixedMul pattern: extend to i64, multiply, shift, wrap
        if ("i64.mul" in text && "i32.wrap_i64" in text && func.body.size < 50) {
            println("CANDIDATE FixedMul: func_$index (global=${index + importCount}) body=${func.body.size} bytes")
            println("  params: ${funcType.params}, results: ${funcType.results}")
            for (inst in instructions) { println("    ${inst.text()}") }
            println()
        }

        // P_Random pattern: no params, returns i32, loads byte, uses global
        if (funcType.params.isEmpty() && funcType.results.size == 1 && func.body.size < 80 && "i32.load8_u" in text) {
            println("CANDIDATE P_Random: func_$index (global=${index + importCount}) body=${func.body.size} bytes")
            println("  params: ${funcType.params}, results: ${funcType.results}")
            for (inst in instructions) { println("    ${inst.text()}") }
            println()
        }
    }
}
