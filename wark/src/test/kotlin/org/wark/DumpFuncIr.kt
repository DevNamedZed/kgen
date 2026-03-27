package org.wark

import org.kgen.ir.target.Target
import org.kgen.pipeline.Mem2Reg
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.compile.WasmToIrCompiler
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val wasmBytes = Files.readAllBytes(Path.of("examples/assets/doom_wasm_debug.wasm"))
    val module = WasmModuleReader.read(wasmBytes)

    val funcIndex = 94 // P_PathTraverse
    val funcName = module.functionName(funcIndex + module.importedFunctionCount) ?: "func_$funcIndex"
    println("=== WASM disassembly: $funcName (func_$funcIndex) ===")
    val disasm = WasmDisassembler()
    val func = module.functions[funcIndex]
    val instructions = disasm.disassemble(func.body)
    for (inst in instructions) {
        println("  ${inst.text()}")
    }

    println()
    println("=== IR (before Mem2Reg): $funcName ===")
    val compiler = WasmToIrCompiler(Target.native(), module)
    val irModule = compiler.compileFunction(funcIndex, funcName)
    for (irFunc in irModule.functions) {
        for (block in irFunc.blocks) {
            println("${block.label}:")
            for (inst in block.instructions) {
                println("  $inst")
            }
        }
    }

    println()
    println("=== IR (after Mem2Reg): $funcName ===")
    val promoted = Mem2Reg().run(irModule)
    for (irFunc in promoted.functions) {
        for (block in irFunc.blocks) {
            println("${block.label}:")
            for (inst in block.instructions) {
                println("  $inst")
            }
        }
    }

    // Also dump the alloc map
    println()
    println("=== Alloc map ===")
    println(org.kgen.target.x86.codegen.X86CodeGenerator.dumpAlloc(funcName))

    // Compile via full module to get the alloc
    val fullCompiler = WasmToIrCompiler(Target.native(), module)
    var fullIr = fullCompiler.compileAll()
    fullIr = Mem2Reg().run(fullIr)
    val gen = org.kgen.target.x86.codegen.X86CodeGenerator()
    gen.generateObjectFile(fullIr)
    println()
    println("=== Alloc map (full module) ===")
    println(org.kgen.target.x86.codegen.X86CodeGenerator.dumpAlloc(funcName))
}
