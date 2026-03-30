package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeDrainKeysTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    @Test
    @EnabledIf("quakeWasmExists")
    fun analyzeKeySystem() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = org.wark.WarkRuntime.create(org.wark.WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)
        val importCount = module.wasmModule.importedFunctionCount

        println("=== Functions with Key/Drain/Queue in name ===")
        for ((li, function) in module.wasmModule.functions.withIndex()) {
            val name = module.wasmModule.functionName(li + importCount) ?: continue
            if (name.contains("Key", ignoreCase = true) || name.contains("Drain") || name.contains("Queue")) {
                val funcType = module.wasmModule.types[function.typeIndex]
                println("  func ${li + importCount}: $name  params=${funcType.params} results=${funcType.results}")
            }
        }

        println("\n=== signature_mismatch stubs in function table ===")
        for (element in module.wasmModule.elements) {
            if (element is org.kgen.target.wasm.module.WasmModule.Element.Active) {
                for ((tableSlot, funcIdx) in element.funcIndices.withIndex()) {
                    val name = module.wasmModule.functionName(funcIdx) ?: continue
                    if (name.contains("signature_mismatch")) {
                        val funcType = module.wasmModule.types[
                            if (funcIdx < importCount) {
                                module.wasmModule.imports.filterIsInstance<org.kgen.target.wasm.module.WasmModule.Import.Func>()[funcIdx].typeIndex
                            } else {
                                module.wasmModule.functions[funcIdx - importCount].typeIndex
                            }
                        ]
                        println("  table[$tableSlot] = func $funcIdx ($name)  type: params=${funcType.params} results=${funcType.results}")
                    }
                }
            }
        }

        println("\n=== All call_indirect types used ===")
        val disasm = org.kgen.target.wasm.disasm.WasmDisassembler()
        val callIndirectTypes = mutableSetOf<Int>()
        for (func in module.wasmModule.functions) {
            for (inst in disasm.disassemble(func.body)) {
                if (inst.opcode == org.kgen.target.wasm.WasmOpCode.CALL_INDIRECT) {
                    val operands = inst.operands as org.kgen.target.wasm.disasm.WasmInstruction.Operands.CallIndirect
                    callIndirectTypes.add(operands.typeIndex)
                }
            }
        }
        for (typeIdx in callIndirectTypes.sorted()) {
            val funcType = module.wasmModule.types[typeIdx]
            println("  type $typeIdx: params=${funcType.params} results=${funcType.results}")
        }

        println("\n=== Sys_SendKeyEvents body ===")
        for ((li, function) in module.wasmModule.functions.withIndex()) {
            val name = module.wasmModule.functionName(li + importCount) ?: continue
            if (name == "Sys_SendKeyEvents") {
                val instructions = disasm.disassemble(function.body)
                println("  ${instructions.size} instructions, type=${module.wasmModule.types[function.typeIndex]}")
                for ((i, inst) in instructions.withIndex()) {
                    println("  [$i] ${inst.opcode} ${inst.operands}")
                }
            }
        }

        println("\n=== WASM_QueueKeyEvent body ===")
        for ((li, function) in module.wasmModule.functions.withIndex()) {
            val name = module.wasmModule.functionName(li + importCount) ?: continue
            if (name == "WASM_QueueKeyEvent") {
                val instructions = disasm.disassemble(function.body)
                println("  ${instructions.size} instructions, type=${module.wasmModule.types[function.typeIndex]}")
                for ((i, inst) in instructions.withIndex()) {
                    println("  [$i] ${inst.opcode} ${inst.operands}")
                }
            }
        }
    }
}
