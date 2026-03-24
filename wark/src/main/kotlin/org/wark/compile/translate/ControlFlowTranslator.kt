package org.wark.compile.translate

import org.kgen.ir.Constant
import org.kgen.ir.Type
import org.kgen.ir.Value
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.disasm.WasmInstruction.Operands
import org.kgen.target.wasm.module.WasmModule
import org.wark.compile.CompilationContext
import org.wark.compile.WasmToIrCompiler

class ControlFlowTranslator(
    private val wasmModule: WasmModule,
) : InstructionTranslator {

    private val handled = setOf(
        "return", "call", "call_indirect", "unreachable",
        "i32.wrap_i64", "i64.extend_i32_s", "i64.extend_i32_u",
        "f32.convert_i32_s", "f32.convert_i32_u",
        "f64.convert_i32_s", "f64.convert_i32_u",
        "f64.convert_i64_s", "f64.convert_i64_u",
        "i32.trunc_f32_s", "i32.trunc_f64_s", "i32.trunc_f64_u",
        "f32.demote_f64", "f64.promote_f32",
        "i64.reinterpret_f64", "f64.reinterpret_i64",
        "f64.copysign",
        "memory.copy", "memory.fill", "memory.init", "data.drop",
    )

    override fun canHandle(mnemonic: String): Boolean = mnemonic in handled

    override fun translate(context: CompilationContext, instruction: WasmInstruction) {
        val builder = context.builder
        val stack = context.stack

        when (instruction.opcode.mnemonic) {
            "return" -> {
                if (stack.isEmpty()) {
                    builder.ret()
                } else {
                    builder.ret(stack.pop())
                }
                builder.appendBlock(context.freshLabel("unreachable"))
            }

            "call" -> {
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

                val args = mutableListOf<org.kgen.ir.Value>()
                args.add(context.contextPointer)
                val paramArgs = mutableListOf<org.kgen.ir.Value>()
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

            "call_indirect" -> {
                val operands = instruction.operands as Operands.CallIndirect
                val calleeType = wasmModule.types[operands.typeIndex]
                val tableIndex = stack.pop()

                val args = mutableListOf<org.kgen.ir.Value>()
                args.add(context.contextPointer)
                args.add(tableIndex)
                val paramArgs = mutableListOf<org.kgen.ir.Value>()
                for (paramIndex in calleeType.params.indices) {
                    val arg = stack.pop()
                    if (arg.type == Type.I32) {
                        paramArgs.add(builder.zext(arg, Type.I64))
                    } else {
                        paramArgs.add(arg)
                    }
                }
                paramArgs.reverse()
                args.addAll(paramArgs)

                val wasmReturnType = if (calleeType.results.isEmpty()) {
                    Type.Void
                } else {
                    WasmToIrCompiler.wasmTypeToIr(calleeType.results[0])
                }

                // Dispatch via arity-specific dispatcher; always returns I64
                val arity = calleeType.params.size
                val dispatchResult: Value? = builder.call("__wark_call_indirect_$arity", args, Type.I64)
                if (context.traceEnabled && dispatchResult != null) {
                    builder.call("__wark_trace_return", listOf(
                        context.contextPointer,
                        Constant.I64(context.functionIndex.toLong()),
                        Constant.I64(-1L),
                        dispatchResult,
                    ), Type.Void)
                }
                if (wasmReturnType != Type.Void && dispatchResult != null) {
                    if (wasmReturnType == Type.I32) {
                        stack.push(builder.trunc(dispatchResult, Type.I32))
                    } else {
                        stack.push(dispatchResult)
                    }
                }
            }

            "unreachable" -> {
                builder.call("__wark_trap", listOf(Constant.I32(context.functionIndex)), Type.Void)
                builder.ret()
                builder.appendBlock(context.freshLabel("unreachable"))
            }

            "i32.wrap_i64" -> { stack.push(builder.trunc(stack.pop(), Type.I32)) }
            "i64.extend_i32_s" -> { stack.push(builder.sext(stack.pop(), Type.I64)) }
            "i64.extend_i32_u" -> { stack.push(builder.zext(stack.pop(), Type.I64)) }
            "f32.convert_i32_s", "f32.convert_i32_u" -> { stack.push(builder.sitofp(stack.pop(), Type.F32)) }
            "f64.convert_i32_s", "f64.convert_i32_u" -> { stack.push(builder.sitofp(stack.pop(), Type.F64)) }
            "f64.convert_i64_s", "f64.convert_i64_u" -> { stack.push(builder.sitofp(stack.pop(), Type.F64)) }
            "i32.trunc_f32_s", "i32.trunc_f64_s" -> { stack.push(builder.fptosi(stack.pop(), Type.I32)) }
            "i32.trunc_f64_u" -> { stack.push(builder.fptosi(stack.pop(), Type.I32)) }
            "f32.demote_f64" -> { stack.push(builder.fptrunc(stack.pop(), Type.F32)) }
            "f64.promote_f32" -> { stack.push(builder.fpext(stack.pop(), Type.F64)) }
            "i64.reinterpret_f64" -> { stack.push(builder.bitcast(stack.pop(), Type.I64)) }
            "f64.reinterpret_i64" -> { stack.push(builder.bitcast(stack.pop(), Type.F64)) }
            "f64.copysign" -> {
                val sign = stack.pop()
                val magnitude = stack.pop()
                stack.push(builder.call("__wark_f64_copysign", listOf(magnitude, sign), Type.F64) ?: magnitude)
            }
            "memory.copy" -> {
                val length = stack.pop()
                val source = stack.pop()
                val destination = stack.pop()
                builder.call("__wark_memory_copy", listOf(context.contextPointer, destination, source, length), Type.Void)
            }
            "memory.fill" -> {
                val length = stack.pop()
                val value = stack.pop()
                val destination = stack.pop()
                builder.call("__wark_memory_fill", listOf(context.contextPointer, destination, value, length), Type.Void)
            }
            "memory.init" -> {
                val operands = instruction.operands as org.kgen.target.wasm.disasm.WasmInstruction.Operands.TwoIndex
                val length = stack.pop()
                val source = stack.pop()
                val destination = stack.pop()
                builder.call("__wark_memory_init", listOf(
                    context.contextPointer, Constant.I32(operands.first), destination, source, length
                ), Type.Void)
            }
            "data.drop" -> {
                val operands = instruction.operands as org.kgen.target.wasm.disasm.WasmInstruction.Operands.Index
                builder.call("__wark_data_drop", listOf(
                    context.contextPointer, Constant.I32(operands.value)
                ), Type.Void)
            }
        }
    }
}
