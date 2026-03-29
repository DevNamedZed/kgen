package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import java.nio.file.Files
import java.nio.file.Path

class QuakeHostFrameTest {
    private val wasmPath = Path.of("../assets/quake.wasm")
    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    @Test
    @EnabledIf("quakeWasmExists")
    fun dumpHostFrameOpcodes() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)
        val disasm = org.kgen.target.wasm.disasm.WasmDisassembler()
        val importCount = module.wasmModule.importedFunctionCount

        for ((localIndex, function) in module.wasmModule.functions.withIndex()) {
            val name = module.wasmModule.functionName(localIndex + importCount) ?: continue
            if (name == "_Host_Frame") {
                val instructions = disasm.disassemble(function.body)
                val tryCount = instructions.count { it.opcode == org.kgen.target.wasm.WasmOpCode.TRY }
                val catchCount = instructions.count { it.opcode == org.kgen.target.wasm.WasmOpCode.CATCH }
                val catchAllCount = instructions.count { it.opcode == org.kgen.target.wasm.WasmOpCode.CATCH_ALL }
                val delegateCount = instructions.count { it.opcode == org.kgen.target.wasm.WasmOpCode.DELEGATE }
                println("_Host_Frame: ${instructions.size} instructions")
                println("  TRY=$tryCount, CATCH=$catchCount, CATCH_ALL=$catchAllCount, DELEGATE=$delegateCount")

                for ((i, inst) in instructions.withIndex()) {
                    if (inst.opcode in setOf(
                        org.kgen.target.wasm.WasmOpCode.TRY,
                        org.kgen.target.wasm.WasmOpCode.CATCH,
                        org.kgen.target.wasm.WasmOpCode.CATCH_ALL,
                        org.kgen.target.wasm.WasmOpCode.DELEGATE,
                        org.kgen.target.wasm.WasmOpCode.THROW,
                        org.kgen.target.wasm.WasmOpCode.CALL_INDIRECT,
                    )) {
                        println("  [$i] ${inst.opcode} ${inst.operands}")
                    }
                }
                break
            }
        }
    }
}
