package org.wark.compile.translate

import org.kgen.ir.Constant
import org.kgen.ir.Type
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.disasm.WasmInstruction.Operands
import org.wark.compile.CompilationContext

class MemoryTranslator : InstructionTranslator {

    private val handled = setOf(
        "i32.load", "i32.store", "i64.load", "i64.store",
        "i32.load8_s", "i32.load8_u", "i32.load16_s", "i32.load16_u",
        "i32.store8", "i32.store16",
        "i64.load8_s", "i64.load8_u", "i64.load16_s", "i64.load16_u",
        "i64.load32_s", "i64.load32_u",
        "i64.store8", "i64.store16", "i64.store32",
        "f32.load", "f32.store", "f64.load", "f64.store",
    )

    override fun canHandle(mnemonic: String): Boolean = mnemonic in handled

    override fun translate(context: CompilationContext, instruction: WasmInstruction) {
        when (instruction.opcode.mnemonic) {
            "i32.load" -> load(context, instruction, Type.I32)
            "i64.load" -> load(context, instruction, Type.I64)
            "f32.load" -> load(context, instruction, Type.F32)
            "f64.load" -> load(context, instruction, Type.F64)

            "i32.store" -> store(context, instruction)
            "i64.store" -> store(context, instruction)
            "f32.store" -> store(context, instruction)
            "f64.store" -> store(context, instruction)

            "i32.load8_s" -> loadExtend(context, instruction, Type.I8, Type.I32, signed = true)
            "i32.load8_u" -> loadExtend(context, instruction, Type.I8, Type.I32, signed = false)
            "i32.load16_s" -> loadExtend(context, instruction, Type.I16, Type.I32, signed = true)
            "i32.load16_u" -> loadExtend(context, instruction, Type.I16, Type.I32, signed = false)
            "i64.load8_s" -> loadExtend(context, instruction, Type.I8, Type.I64, signed = true)
            "i64.load8_u" -> loadExtend(context, instruction, Type.I8, Type.I64, signed = false)
            "i64.load16_s" -> loadExtend(context, instruction, Type.I16, Type.I64, signed = true)
            "i64.load16_u" -> loadExtend(context, instruction, Type.I16, Type.I64, signed = false)
            "i64.load32_s" -> loadExtend(context, instruction, Type.I32, Type.I64, signed = true)
            "i64.load32_u" -> loadExtend(context, instruction, Type.I32, Type.I64, signed = false)

            "i32.store8" -> storeTrunc(context, instruction, Type.I8)
            "i32.store16" -> storeTrunc(context, instruction, Type.I16)
            "i64.store8" -> storeTrunc(context, instruction, Type.I8)
            "i64.store16" -> storeTrunc(context, instruction, Type.I16)
            "i64.store32" -> storeTrunc(context, instruction, Type.I32)
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
