package org.wark.compile.translate

import org.kgen.target.wasm.WasmOpCode
import org.kgen.target.wasm.disasm.WasmInstruction
import org.wark.compile.CompilationContext

interface InstructionTranslator {
    fun canHandle(opcode: WasmOpCode): Boolean
    fun translate(context: CompilationContext, instruction: WasmInstruction)
}
