package org.wark.compile.translate

import org.kgen.target.wasm.disasm.WasmInstruction
import org.wark.compile.CompilationContext

/**
 * Translates a category of WASM instructions to kgen IR.
 */
interface InstructionTranslator {
    fun canHandle(mnemonic: String): Boolean
    fun translate(context: CompilationContext, instruction: WasmInstruction)
}
