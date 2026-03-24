package org.wark.compile.translate

import org.kgen.ir.Constant
import org.kgen.ir.Type
import org.kgen.target.wasm.WasmOpCode
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.disasm.WasmInstruction.Operands
import org.wark.compile.CompilationContext

class MemoryTranslator : InstructionTranslator {

    private val handled = setOf(
        WasmOpCode.I32_LOAD, WasmOpCode.I32_STORE, WasmOpCode.I64_LOAD, WasmOpCode.I64_STORE,
        WasmOpCode.I32_LOAD8_S, WasmOpCode.I32_LOAD8_U, WasmOpCode.I32_LOAD16_S, WasmOpCode.I32_LOAD16_U,
        WasmOpCode.I32_STORE8, WasmOpCode.I32_STORE16,
        WasmOpCode.I64_LOAD8_S, WasmOpCode.I64_LOAD8_U, WasmOpCode.I64_LOAD16_S, WasmOpCode.I64_LOAD16_U,
        WasmOpCode.I64_LOAD32_S, WasmOpCode.I64_LOAD32_U,
        WasmOpCode.I64_STORE8, WasmOpCode.I64_STORE16, WasmOpCode.I64_STORE32,
        WasmOpCode.F32_LOAD, WasmOpCode.F32_STORE, WasmOpCode.F64_LOAD, WasmOpCode.F64_STORE,
    )

    override fun canHandle(opcode: WasmOpCode): Boolean = opcode in handled

    override fun translate(context: CompilationContext, instruction: WasmInstruction) {
        when (instruction.opcode) {
            WasmOpCode.I32_LOAD -> load(context, instruction, Type.I32)
            WasmOpCode.I64_LOAD -> load(context, instruction, Type.I64)
            WasmOpCode.F32_LOAD -> load(context, instruction, Type.F32)
            WasmOpCode.F64_LOAD -> load(context, instruction, Type.F64)

            WasmOpCode.I32_STORE, WasmOpCode.I64_STORE,
            WasmOpCode.F32_STORE, WasmOpCode.F64_STORE -> store(context, instruction)

            WasmOpCode.I32_LOAD8_S -> loadExtend(context, instruction, Type.I8, Type.I32, signed = true)
            WasmOpCode.I32_LOAD8_U -> loadExtend(context, instruction, Type.I8, Type.I32, signed = false)
            WasmOpCode.I32_LOAD16_S -> loadExtend(context, instruction, Type.I16, Type.I32, signed = true)
            WasmOpCode.I32_LOAD16_U -> loadExtend(context, instruction, Type.I16, Type.I32, signed = false)
            WasmOpCode.I64_LOAD8_S -> loadExtend(context, instruction, Type.I8, Type.I64, signed = true)
            WasmOpCode.I64_LOAD8_U -> loadExtend(context, instruction, Type.I8, Type.I64, signed = false)
            WasmOpCode.I64_LOAD16_S -> loadExtend(context, instruction, Type.I16, Type.I64, signed = true)
            WasmOpCode.I64_LOAD16_U -> loadExtend(context, instruction, Type.I16, Type.I64, signed = false)
            WasmOpCode.I64_LOAD32_S -> loadExtend(context, instruction, Type.I32, Type.I64, signed = true)
            WasmOpCode.I64_LOAD32_U -> loadExtend(context, instruction, Type.I32, Type.I64, signed = false)

            WasmOpCode.I32_STORE8 -> storeTrunc(context, instruction, Type.I8)
            WasmOpCode.I32_STORE16 -> storeTrunc(context, instruction, Type.I16)
            WasmOpCode.I64_STORE8 -> storeTrunc(context, instruction, Type.I8)
            WasmOpCode.I64_STORE16 -> storeTrunc(context, instruction, Type.I16)
            WasmOpCode.I64_STORE32 -> storeTrunc(context, instruction, Type.I32)
            else -> { }
        }
    }

    private fun effectiveAddress(context: CompilationContext, instruction: WasmInstruction, accessSize: Int = 4): org.kgen.ir.Value {
        val memArg = instruction.operands as Operands.MemArg
        val builder = context.builder
        val address = context.stack.pop()

        val extendedAddress = builder.zext(address, Type.I64)

        // Bounds check on the full WASM-level offset (I64 to avoid I32 overflow)
        val fullWasmOffset = if (memArg.offset != 0) {
            builder.add(extendedAddress, Constant.I64(memArg.offset.toLong()))
        } else {
            extendedAddress
        }
        context.emitBoundsCheck(fullWasmOffset, accessSize)
        val memoryBase = context.loadMemoryBase()
        val effectiveAddr = builder.add(memoryBase, extendedAddress)
        return if (memArg.offset != 0) {
            builder.add(effectiveAddr, Constant.I64(memArg.offset.toLong()))
        } else {
            effectiveAddr
        }
    }

    private fun typeSize(type: Type): Int = when (type) {
        Type.I8 -> 1; Type.I16 -> 2; Type.I32, Type.F32 -> 4; Type.I64, Type.F64 -> 8; else -> 4
    }

    private fun load(context: CompilationContext, instruction: WasmInstruction, type: Type) {
        val address = effectiveAddress(context, instruction, typeSize(type))
        context.stack.push(context.builder.load(type, address))
    }

    private fun store(context: CompilationContext, instruction: WasmInstruction) {
        val value = context.stack.pop()
        val accessSize = typeSize(value.type)
        val address = effectiveAddress(context, instruction, accessSize)
        context.builder.store(value, address)
    }

    private fun loadExtend(context: CompilationContext, instruction: WasmInstruction, loadType: Type, targetType: Type, signed: Boolean) {
        val address = effectiveAddress(context, instruction, typeSize(loadType))
        val loaded = context.builder.load(loadType, address)
        val extended = if (signed) {
            context.builder.sext(loaded, targetType)
        } else {
            context.builder.zext(loaded, targetType)
        }
        context.stack.push(extended)
    }

    private fun storeTrunc(context: CompilationContext, instruction: WasmInstruction, storeType: Type) {
        val value = context.stack.pop()
        val address = effectiveAddress(context, instruction, typeSize(storeType))
        val truncated = context.builder.trunc(value, storeType)
        context.builder.store(truncated, address)
    }
}
