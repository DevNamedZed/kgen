package org.wark.compile.translate

import org.kgen.ir.Constant
import org.kgen.ir.Type
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.disasm.WasmInstruction.Operands
import org.wark.compile.CompilationContext

class VariableTranslator : InstructionTranslator {

    private val handled = setOf(
        "i32.const", "i64.const", "f32.const", "f64.const",
        "local.get", "local.set", "local.tee",
        "drop", "select", "nop",
    )

    override fun canHandle(mnemonic: String): Boolean = mnemonic in handled

    override fun translate(context: CompilationContext, instruction: WasmInstruction) {
        val builder = context.builder
        val stack = context.stack

        when (instruction.opcode.mnemonic) {
            "i32.const" -> stack.push(Constant.I32((instruction.operands as Operands.I32).value))
            "i64.const" -> stack.push(Constant.I64((instruction.operands as Operands.I64).value))
            "f32.const" -> stack.push(Constant.F32((instruction.operands as Operands.F32).value))
            "f64.const" -> stack.push(Constant.F64((instruction.operands as Operands.F64).value))

            "local.get" -> {
                val index = (instruction.operands as Operands.Index).value
                stack.push(builder.load(context.localType(index), context.locals[index]))
            }
            "local.set" -> {
                val index = (instruction.operands as Operands.Index).value
                builder.store(stack.pop(), context.locals[index])
            }
            "local.tee" -> {
                val index = (instruction.operands as Operands.Index).value
                builder.store(stack.peek(), context.locals[index])
            }

            "drop" -> { stack.pop() }
            "select" -> {
                val condition = stack.pop()
                val falseValue = stack.pop()
                val trueValue = stack.pop()
                stack.push(builder.select(condition, trueValue, falseValue))
            }
            "nop" -> { }
        }
    }
}
