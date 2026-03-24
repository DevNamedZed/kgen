package org.wark.compile.translate

import org.kgen.ir.*
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.disasm.WasmInstruction.Operands
import org.wark.compile.CompilationContext
import org.wark.compile.WasmToIrCompiler

/**
 * Translates global variable access and memory management instructions.
 *
 * Globals use the WASM type (I32/I64/F32/F64), stored as IR globals
 * named `__wasm_global_N`.
 */
class GlobalTranslator : InstructionTranslator {

    private val handled = setOf(
        "global.get", "global.set",
        "memory.size", "memory.grow",
    )

    override fun canHandle(mnemonic: String): Boolean = mnemonic in handled

    override fun translate(context: CompilationContext, instruction: WasmInstruction) {
        val builder = context.builder
        val stack = context.stack

        when (instruction.opcode.mnemonic) {
            "global.get" -> {
                val index = (instruction.operands as Operands.Index).value
                val globalName = "__wasm_global_$index"
                val wasmType = resolveGlobalType(context, index)
                val irType = WasmToIrCompiler.wasmTypeToIr(wasmType)
                val globalRef = GlobalRef(globalName, irType)
                stack.push(builder.load(irType, globalRef))
            }
            "global.set" -> {
                val index = (instruction.operands as Operands.Index).value
                val globalName = "__wasm_global_$index"
                val wasmType = resolveGlobalType(context, index)
                val irType = WasmToIrCompiler.wasmTypeToIr(wasmType)
                val globalRef = GlobalRef(globalName, irType)
                val value = stack.pop()
                val storeValue = if (value.type != irType) {
                    if (value.type == Type.I32 && irType == Type.I64) {
                        builder.zext(value, Type.I64)
                    } else if (value.type == Type.I64 && irType == Type.I32) {
                        builder.trunc(value, Type.I32)
                    } else {
                        value
                    }
                } else {
                    value
                }
                builder.store(storeValue, globalRef)
            }
            "memory.size" -> {
                val result = builder.call("__wark_memory_size", listOf(context.contextPointer), Type.I32)
                if (result != null) { stack.push(result) }
            }
            "memory.grow" -> {
                val delta = stack.pop()
                val result = builder.call("__wark_memory_grow", listOf(context.contextPointer, delta), Type.I32)
                if (result != null) { stack.push(result) }
                // Force a volatile reload of memory_base from context
                // to prevent any caching of the pre-grow base address
                context.loadMemoryBase()
            }
        }
    }

    private fun resolveGlobalType(context: CompilationContext, index: Int): WasmValueType {
        val importedGlobals = context.wasmModule.imports.filterIsInstance<org.kgen.target.wasm.module.WasmModule.Import.Global>()
        if (index < importedGlobals.size) {
            return importedGlobals[index].type
        }
        val localIndex = index - importedGlobals.size
        if (localIndex < context.wasmModule.globals.size) {
            return context.wasmModule.globals[localIndex].type
        }
        return WasmValueType.I32
    }
}
