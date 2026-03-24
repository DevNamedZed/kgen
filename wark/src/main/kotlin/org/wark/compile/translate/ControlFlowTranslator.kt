package org.wark.compile.translate

import org.kgen.ir.Constant
import org.kgen.ir.Type
import org.kgen.ir.Value
import org.kgen.target.wasm.WasmOpCode
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.disasm.WasmInstruction.Operands
import org.kgen.target.wasm.module.WasmModule
import org.wark.compile.CompilationContext
import org.wark.compile.WasmToIrCompiler

class ControlFlowTranslator(
    private val wasmModule: WasmModule,
) : InstructionTranslator {

    private val handled = setOf(
        WasmOpCode.RETURN, WasmOpCode.CALL, WasmOpCode.CALL_INDIRECT, WasmOpCode.UNREACHABLE,
        WasmOpCode.I32_WRAP_I64, WasmOpCode.I64_EXTEND_I32_S, WasmOpCode.I64_EXTEND_I32_U,
        WasmOpCode.F32_CONVERT_I32_S, WasmOpCode.F32_CONVERT_I32_U,
        WasmOpCode.F32_CONVERT_I64_S, WasmOpCode.F32_CONVERT_I64_U,
        WasmOpCode.F64_CONVERT_I32_S, WasmOpCode.F64_CONVERT_I32_U,
        WasmOpCode.F64_CONVERT_I64_S, WasmOpCode.F64_CONVERT_I64_U,
        WasmOpCode.I32_TRUNC_F32_S, WasmOpCode.I32_TRUNC_F32_U,
        WasmOpCode.I32_TRUNC_F64_S, WasmOpCode.I32_TRUNC_F64_U,
        WasmOpCode.I64_TRUNC_F32_S, WasmOpCode.I64_TRUNC_F32_U,
        WasmOpCode.I64_TRUNC_F64_S, WasmOpCode.I64_TRUNC_F64_U,
        WasmOpCode.F32_DEMOTE_F64, WasmOpCode.F64_PROMOTE_F32,
        WasmOpCode.I32_REINTERPRET_F32, WasmOpCode.F32_REINTERPRET_I32,
        WasmOpCode.I64_REINTERPRET_F64, WasmOpCode.F64_REINTERPRET_I64,
        WasmOpCode.F64_COPYSIGN,
        WasmOpCode.I32_TRUNC_SAT_F32_S, WasmOpCode.I32_TRUNC_SAT_F32_U,
        WasmOpCode.I32_TRUNC_SAT_F64_S, WasmOpCode.I32_TRUNC_SAT_F64_U,
        WasmOpCode.I64_TRUNC_SAT_F32_S, WasmOpCode.I64_TRUNC_SAT_F32_U,
        WasmOpCode.I64_TRUNC_SAT_F64_S, WasmOpCode.I64_TRUNC_SAT_F64_U,
        WasmOpCode.MEMORY_COPY, WasmOpCode.MEMORY_FILL, WasmOpCode.MEMORY_INIT, WasmOpCode.DATA_DROP,
    )

    override fun canHandle(opcode: WasmOpCode): Boolean = opcode in handled

    override fun translate(context: CompilationContext, instruction: WasmInstruction) {
        val builder = context.builder
        val stack = context.stack

        when (instruction.opcode) {
            WasmOpCode.RETURN -> {
                if (stack.isEmpty()) {
                    builder.ret()
                } else {
                    builder.ret(stack.pop())
                }
                builder.appendBlock(context.freshLabel("unreachable"))
            }

            WasmOpCode.CALL -> translateCall(context, instruction)
            WasmOpCode.CALL_INDIRECT -> translateCallIndirect(context, instruction)

            WasmOpCode.UNREACHABLE -> {
                builder.call("__wark_trap", listOf(Constant.I32(context.functionIndex)), Type.Void)
                builder.ret()
                builder.appendBlock(context.freshLabel("unreachable"))
            }

            WasmOpCode.I32_WRAP_I64 -> { stack.push(builder.trunc(stack.pop(), Type.I32)) }
            WasmOpCode.I64_EXTEND_I32_S -> { stack.push(builder.sext(stack.pop(), Type.I64)) }
            WasmOpCode.I64_EXTEND_I32_U -> { stack.push(builder.zext(stack.pop(), Type.I64)) }
            WasmOpCode.F32_CONVERT_I32_S -> { stack.push(builder.sitofp(stack.pop(), Type.F32)) }
            WasmOpCode.F32_CONVERT_I32_U -> { stack.push(builder.uitofp(stack.pop(), Type.F32)) }
            WasmOpCode.F32_CONVERT_I64_S -> { stack.push(builder.sitofp(stack.pop(), Type.F32)) }
            WasmOpCode.F32_CONVERT_I64_U -> { stack.push(builder.uitofp(stack.pop(), Type.F32)) }
            WasmOpCode.F64_CONVERT_I32_S -> { stack.push(builder.sitofp(stack.pop(), Type.F64)) }
            WasmOpCode.F64_CONVERT_I32_U -> { stack.push(builder.uitofp(stack.pop(), Type.F64)) }
            WasmOpCode.F64_CONVERT_I64_S -> { stack.push(builder.sitofp(stack.pop(), Type.F64)) }
            WasmOpCode.F64_CONVERT_I64_U -> { stack.push(builder.uitofp(stack.pop(), Type.F64)) }
            WasmOpCode.I32_TRUNC_F32_S, WasmOpCode.I32_TRUNC_F64_S -> { stack.push(builder.fptosi(stack.pop(), Type.I32)) }
            WasmOpCode.I32_TRUNC_F32_U, WasmOpCode.I32_TRUNC_F64_U -> { stack.push(builder.fptoui(stack.pop(), Type.I32)) }
            WasmOpCode.I64_TRUNC_F32_S, WasmOpCode.I64_TRUNC_F64_S -> { stack.push(builder.fptosi(stack.pop(), Type.I64)) }
            WasmOpCode.I64_TRUNC_F32_U, WasmOpCode.I64_TRUNC_F64_U -> { stack.push(builder.fptoui(stack.pop(), Type.I64)) }
            WasmOpCode.F32_DEMOTE_F64 -> { stack.push(builder.fptrunc(stack.pop(), Type.F32)) }
            WasmOpCode.F64_PROMOTE_F32 -> { stack.push(builder.fpext(stack.pop(), Type.F64)) }
            WasmOpCode.I32_REINTERPRET_F32 -> { stack.push(builder.bitcast(stack.pop(), Type.I32)) }
            WasmOpCode.F32_REINTERPRET_I32 -> { stack.push(builder.bitcast(stack.pop(), Type.F32)) }
            WasmOpCode.I64_REINTERPRET_F64 -> { stack.push(builder.bitcast(stack.pop(), Type.I64)) }
            WasmOpCode.F64_REINTERPRET_I64 -> { stack.push(builder.bitcast(stack.pop(), Type.F64)) }

            WasmOpCode.I32_TRUNC_SAT_F32_S, WasmOpCode.I32_TRUNC_SAT_F64_S -> {
                val value = stack.pop()
                val promoted = if (value.type == Type.F32) { builder.fpext(value, Type.F64) } else { value }
                stack.push(builder.call("__wark_i32_trunc_sat_s", listOf(promoted), Type.I32) ?: Constant.I32(0))
            }
            WasmOpCode.I32_TRUNC_SAT_F32_U, WasmOpCode.I32_TRUNC_SAT_F64_U -> {
                val value = stack.pop()
                val promoted = if (value.type == Type.F32) { builder.fpext(value, Type.F64) } else { value }
                stack.push(builder.call("__wark_i32_trunc_sat_u", listOf(promoted), Type.I32) ?: Constant.I32(0))
            }
            WasmOpCode.I64_TRUNC_SAT_F32_S, WasmOpCode.I64_TRUNC_SAT_F64_S -> {
                val value = stack.pop()
                val promoted = if (value.type == Type.F32) { builder.fpext(value, Type.F64) } else { value }
                stack.push(builder.call("__wark_i64_trunc_sat_s", listOf(promoted), Type.I64) ?: Constant.I64(0))
            }
            WasmOpCode.I64_TRUNC_SAT_F32_U, WasmOpCode.I64_TRUNC_SAT_F64_U -> {
                val value = stack.pop()
                val promoted = if (value.type == Type.F32) { builder.fpext(value, Type.F64) } else { value }
                stack.push(builder.call("__wark_i64_trunc_sat_u", listOf(promoted), Type.I64) ?: Constant.I64(0))
            }

            WasmOpCode.F64_COPYSIGN -> {
                val sign = stack.pop()
                val magnitude = stack.pop()
                stack.push(builder.call("__wark_f64_copysign", listOf(magnitude, sign), Type.F64) ?: magnitude)
            }

            WasmOpCode.MEMORY_COPY -> {
                val length = stack.pop(); val source = stack.pop(); val destination = stack.pop()
                builder.call("__wark_memory_copy", listOf(context.contextPointer, destination, source, length), Type.Void)
            }
            WasmOpCode.MEMORY_FILL -> {
                val length = stack.pop(); val value = stack.pop(); val destination = stack.pop()
                builder.call("__wark_memory_fill", listOf(context.contextPointer, destination, value, length), Type.Void)
            }
            WasmOpCode.MEMORY_INIT -> {
                val operands = instruction.operands as Operands.TwoIndex
                val length = stack.pop(); val source = stack.pop(); val destination = stack.pop()
                builder.call("__wark_memory_init", listOf(
                    context.contextPointer, Constant.I32(operands.first), destination, source, length
                ), Type.Void)
            }
            WasmOpCode.DATA_DROP -> {
                val operands = instruction.operands as Operands.Index
                builder.call("__wark_data_drop", listOf(context.contextPointer, Constant.I32(operands.value)), Type.Void)
            }
            else -> { }
        }
    }

    private fun translateCall(context: CompilationContext, instruction: WasmInstruction) {
        val builder = context.builder
        val stack = context.stack
        val functionIndex = (instruction.operands as Operands.Index).value
        val importCount = wasmModule.importedFunctionCount

        val calleeName: String
        val calleeType: WasmModule.FuncType
        if (functionIndex < importCount) {
            val importDecl = wasmModule.imports.filterIsInstance<WasmModule.Import.Func>()[functionIndex]
            calleeName = "${importDecl.module}_${importDecl.name}"
            calleeType = wasmModule.types[importDecl.typeIndex]
        } else {
            val localIndex = functionIndex - importCount
            calleeName = wasmModule.functionName(functionIndex) ?: "func_$localIndex"
            calleeType = wasmModule.types[wasmModule.functions[localIndex].typeIndex]
        }

        val args = mutableListOf<Value>()
        args.add(context.contextPointer)
        val paramArgs = mutableListOf<Value>()
        for (paramIndex in calleeType.params.indices) {
            paramArgs.add(stack.pop())
        }
        paramArgs.reverse()
        args.addAll(paramArgs)

        val returnType = if (calleeType.results.isEmpty()) {
            Type.Void
        } else {
            WasmToIrCompiler.wasmTypeToIr(calleeType.results[0])
        }

        val result: Value? = builder.call(calleeName, args, returnType)
        if (context.traceEnabled) {
            val calleeLocalIndex = if (functionIndex >= importCount) {
                functionIndex - importCount
            } else {
                functionIndex
            }
            val traceValue = if (result != null && result.type == Type.I32) {
                builder.zext(result, Type.I64)
            } else if (result != null) {
                result
            } else {
                Constant.I64(0)
            }
            builder.call("__wark_trace_return", listOf(
                context.contextPointer,
                Constant.I64(context.functionIndex.toLong()),
                Constant.I64(calleeLocalIndex.toLong()),
                traceValue,
            ), Type.Void)
        }
        if (returnType != Type.Void && result != null) {
            stack.push(result)
        }
    }

    private fun translateCallIndirect(context: CompilationContext, instruction: WasmInstruction) {
        val builder = context.builder
        val stack = context.stack
        val operands = instruction.operands as Operands.CallIndirect
        val calleeType = wasmModule.types[operands.typeIndex]
        val tableIndex = stack.pop()

        val args = mutableListOf<Value>()
        args.add(context.contextPointer)
        args.add(tableIndex)
        val paramArgs = mutableListOf<Value>()
        for (paramIndex in calleeType.params.indices) {
            paramArgs.add(stack.pop())
        }
        paramArgs.reverse()
        args.addAll(paramArgs)

        val wasmReturnType = if (calleeType.results.isEmpty()) {
            Type.Void
        } else {
            WasmToIrCompiler.wasmTypeToIr(calleeType.results[0])
        }

        val dispatchResult: Value? = builder.call(
            "__wark_call_indirect_type${operands.typeIndex}", args, wasmReturnType)
        if (context.traceEnabled && dispatchResult != null) {
            val traceValue = if (dispatchResult.type == Type.I32) {
                builder.zext(dispatchResult, Type.I64)
            } else {
                dispatchResult
            }
            builder.call("__wark_trace_return", listOf(
                context.contextPointer,
                Constant.I64(context.functionIndex.toLong()),
                Constant.I64(-1L),
                traceValue,
            ), Type.Void)
        }
        if (wasmReturnType != Type.Void && dispatchResult != null) {
            stack.push(dispatchResult)
        }
    }
}
