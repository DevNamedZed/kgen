package org.wark.compile.translate

import org.kgen.ir.*
import org.kgen.target.wasm.WasmOpCode
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.disasm.WasmInstruction.Operands
import org.wark.compile.CompilationContext
import org.wark.compile.WasmToIrCompiler

class GlobalTranslator : InstructionTranslator {

    private val handled = setOf(
        WasmOpCode.GLOBAL_GET, WasmOpCode.GLOBAL_SET,
        WasmOpCode.MEMORY_SIZE, WasmOpCode.MEMORY_GROW,
    )

    override fun canHandle(opcode: WasmOpCode): Boolean = opcode in handled

    override fun translate(context: CompilationContext, instruction: WasmInstruction) {
        val builder = context.builder
        val stack = context.stack

        when (instruction.opcode) {
            WasmOpCode.GLOBAL_GET -> {
                val index = (instruction.operands as Operands.Index).value
                val globalName = "__wasm_global_$index"
                val wasmType = resolveGlobalType(context, index)
                val irType = WasmToIrCompiler.wasmTypeToIr(wasmType)
                val globalRef = GlobalRef(globalName, irType)
                stack.push(builder.load(irType, globalRef))
            }
            WasmOpCode.GLOBAL_SET -> {
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
            WasmOpCode.MEMORY_SIZE -> {
                val result = builder.call("__wark_memory_size", listOf(context.contextPointer), Type.I32)
                if (result != null) { stack.push(result) }
            }
            WasmOpCode.MEMORY_GROW -> {
                val delta = stack.pop()
                val result = builder.call("__wark_memory_grow", listOf(context.contextPointer, delta), Type.I32)
                if (result != null) { stack.push(result) }
                // Force a volatile reload of memory_base from context
                // to prevent any caching of the pre-grow base address
                context.loadMemoryBase()
            }
            else -> { }
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
