package org.wark.examples.wasm4

import org.kgen.ir.target.Target
import org.kgen.pipeline.Mem2Reg
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.compile.WasmToIrCompiler
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val wasmBytes = Files.readAllBytes(Path.of("../assets/wasm4/snake.wasm"))
    val wasmModule = WasmModuleReader.read(wasmBytes)
    val importCount = wasmModule.importedFunctionCount
    val disasm = WasmDisassembler()

    // func_17 = local 13
    val localIdx = 13
    val func = wasmModule.functions[localIdx]
    val instructions = disasm.disassemble(func.body)

    println("func_17 (local $localIdx): ${instructions.size} instructions")
    for ((idx, inst) in instructions.withIndex()) {
        println("  [$idx] ${inst.text()}")
    }

    // Also compile to IR and dump
    val compiler = WasmToIrCompiler(Target.x86_64(), wasmModule)
    var irModule = compiler.compileAll()
    irModule = Mem2Reg().run(irModule)

    val irFunc = irModule.functions.find { it.name == "func_$localIdx" }
    if (irFunc != null) {
        println("\nIR for func_$localIdx:")
        println("  ${irFunc.blocks.size} blocks, ${irFunc.params.size} params")
        for (block in irFunc.blocks) {
            println("${block.label}:")
            for (inst in block.instructions) {
                println("  $inst")
            }
        }
    }

    // Check which functions CALL func_17
    println("\nCallers of func_17 (global ${localIdx + importCount}):")
    for ((callerIdx, callerFunc) in wasmModule.functions.withIndex()) {
        val callerInsts = disasm.disassemble(callerFunc.body)
        for (inst in callerInsts) {
            if (inst.opcode.mnemonic == "call") {
                val target = (inst.operands as org.kgen.target.wasm.disasm.WasmInstruction.Operands.Index).value
                if (target == localIdx + importCount) {
                    println("  func_${callerIdx + importCount} (local $callerIdx) calls func_17")
                }
            }
        }
    }
}
