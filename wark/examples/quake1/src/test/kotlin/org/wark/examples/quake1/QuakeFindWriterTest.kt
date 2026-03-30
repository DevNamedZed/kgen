package org.wark.examples.quake1

import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeFindWriterTest {

    private val wasmPath = Path.of("../assets/quake.wasm")

    fun run() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = org.wark.WarkRuntime.create(org.wark.WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)
        val importCount = module.wasmModule.importedFunctionCount
        val disasm = org.kgen.target.wasm.disasm.WasmDisassembler()

        val targetAddress = 0x4CC34
        val targetRange = (targetAddress - 16)..(targetAddress + 16)

        println("=== Finding functions that reference address range around 0x${Integer.toHexString(targetAddress)} ===")
        for ((localIndex, function) in module.wasmModule.functions.withIndex()) {
            val instructions = disasm.disassemble(function.body)
            for ((instrIndex, inst) in instructions.withIndex()) {
                if (inst.opcode.name.contains("STORE") || inst.opcode.name.contains("LOAD")) {
                    val memArg = inst.operands as org.kgen.target.wasm.disasm.WasmInstruction.Operands.MemArg
                    if (memArg.offset in targetRange) {
                        val name = module.wasmModule.functionName(localIndex + importCount) ?: "func_$localIndex"
                        println("  func $localIndex ($name): ${inst.opcode} offset=0x${Integer.toHexString(memArg.offset)} at instruction $instrIndex")
                    }
                }
                if (inst.opcode == org.kgen.target.wasm.WasmOpCode.I32_CONST) {
                    val value = (inst.operands as org.kgen.target.wasm.disasm.WasmInstruction.Operands.I32).value
                    if (value in targetRange) {
                        val name = module.wasmModule.functionName(localIndex + importCount) ?: "func_$localIndex"
                        println("  func $localIndex ($name): i32.const 0x${Integer.toHexString(value)} at instruction $instrIndex")
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val test = QuakeFindWriterTest()
            if (Files.exists(test.wasmPath)) {
                test.run()
            }
        }
    }
}
