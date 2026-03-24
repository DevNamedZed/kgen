package org.wark.compile.translate

import org.kgen.ir.Constant
import org.kgen.ir.Type
import org.kgen.target.wasm.WasmOpCode
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.disasm.WasmInstruction.Operands
import org.wark.compile.CompilationContext

class VariableTranslator : InstructionTranslator {

    private val handled = setOf(
        WasmOpCode.I32_CONST, WasmOpCode.I64_CONST, WasmOpCode.F32_CONST, WasmOpCode.F64_CONST,
        WasmOpCode.LOCAL_GET, WasmOpCode.LOCAL_SET, WasmOpCode.LOCAL_TEE,
        WasmOpCode.DROP, WasmOpCode.SELECT, WasmOpCode.SELECT_TYPED, WasmOpCode.NOP,
    )

    override fun canHandle(opcode: WasmOpCode): Boolean = opcode in handled

    override fun translate(context: CompilationContext, instruction: WasmInstruction) {
        val builder = context.builder
        val stack = context.stack

        when (instruction.opcode) {
            WasmOpCode.I32_CONST -> stack.push(Constant.I32((instruction.operands as Operands.I32).value))
            WasmOpCode.I64_CONST -> stack.push(Constant.I64((instruction.operands as Operands.I64).value))
            WasmOpCode.F32_CONST -> stack.push(Constant.F32((instruction.operands as Operands.F32).value))
            WasmOpCode.F64_CONST -> stack.push(Constant.F64((instruction.operands as Operands.F64).value))

            WasmOpCode.LOCAL_GET -> {
                val index = (instruction.operands as Operands.Index).value
                stack.push(builder.load(context.localType(index), context.locals[index]))
            }
            WasmOpCode.LOCAL_SET -> {
                val index = (instruction.operands as Operands.Index).value
                builder.store(stack.pop(), context.locals[index])
            }
            WasmOpCode.LOCAL_TEE -> {
                val index = (instruction.operands as Operands.Index).value
                builder.store(stack.peek(), context.locals[index])
            }

            WasmOpCode.DROP -> { stack.pop() }
            WasmOpCode.SELECT, WasmOpCode.SELECT_TYPED -> {
                val condition = stack.pop()
                val falseValue = stack.pop()
                val trueValue = stack.pop()
                stack.push(builder.select(condition, trueValue, falseValue))
            }
            WasmOpCode.NOP -> { }
            else -> { }
        }
    }
}
