package org.wark.compile.translate

import org.kgen.ir.*
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.disasm.WasmInstruction.Operands
import org.wark.compile.CompilationContext

class BlockTranslator : InstructionTranslator {

    private val handled = setOf(
        "block", "loop", "if", "else", "end",
        "br", "br_if", "br_table",
    )

    override fun canHandle(mnemonic: String): Boolean = mnemonic in handled

    override fun translate(context: CompilationContext, instruction: WasmInstruction) {
        val builder = context.builder
        val controlStack = context.controlStack

        when (instruction.opcode.mnemonic) {
            "block" -> {
                val endLabel = context.freshLabel("block_end")
                val resultCount = blockResultCount(instruction)
                context.stack.save()
                controlStack.push(ControlEntry(ControlKind.BLOCK, endLabel, resultCount = resultCount))
            }

            "loop" -> {
                val headerLabel = context.freshLabel("loop_header")
                context.stack.save()
                builder.br(headerLabel)
                builder.appendBlock(headerLabel)
                context.emitBlockTrace()
                controlStack.push(ControlEntry(ControlKind.LOOP, headerLabel, resultCount = 0))
            }

            "if" -> {
                val condition = context.stack.pop()
                val thenLabel = context.freshLabel("if_then")
                val elseLabel = context.freshLabel("if_else")
                val endLabel = context.freshLabel("if_end")
                val resultCount = blockResultCount(instruction)

                val i32Cond = if (condition.type == Type.I1) {
                    condition
                } else {
                    builder.icmp(ICmpPredicate.NE, condition, Constant.I32(0))
                }

                context.stack.save()
                builder.condBr(i32Cond, thenLabel, elseLabel)
                builder.appendBlock(thenLabel)
                context.emitBlockTrace()
                controlStack.push(ControlEntry(ControlKind.IF, endLabel, elseLabel, resultCount))
            }

            "else" -> {
                val entry = controlStack.peek()
                if (entry.kind == ControlKind.IF) {
                    val elseLabel = entry.elseLabel ?: return
                    context.stack.restore(entry.resultCount)
                    context.stack.save()
                    builder.br(entry.label)
                    builder.appendBlock(elseLabel)
                    context.emitBlockTrace()
                    entry.elseLabel = null
                }
            }

            "end" -> {
                if (controlStack.isEmpty()) {
                    return
                }
                val entry = controlStack.pop()
                context.stack.restore(entry.resultCount)
                when (entry.kind) {
                    ControlKind.BLOCK -> {
                        builder.br(entry.label)
                        builder.appendBlock(entry.label)
                        context.emitBlockTrace()
                    }
                    ControlKind.LOOP -> {
                    }
                    ControlKind.IF -> {
                        if (entry.elseLabel != null) {
                            builder.br(entry.label)
                            builder.appendBlock(entry.elseLabel!!)
                            context.emitBlockTrace()
                        }
                        builder.br(entry.label)
                        builder.appendBlock(entry.label)
                        context.emitBlockTrace()
                    }
                }
            }

            "br" -> {
                val depth = (instruction.operands as Operands.Index).value
                val target = controlStack.targetAt(depth)
                builder.br(target)
                val unreachableLabel = context.freshLabel("unreachable")
                builder.appendBlock(unreachableLabel)
                context.emitBlockTrace()
            }

            "br_if" -> {
                val depth = (instruction.operands as Operands.Index).value
                val condition = context.stack.pop()
                val target = controlStack.targetAt(depth)
                val continueLabel = context.freshLabel("br_if_cont")

                val i32Cond = if (condition.type == Type.I1) {
                    condition
                } else {
                    builder.icmp(ICmpPredicate.NE, condition, Constant.I32(0))
                }

                builder.condBr(i32Cond, target, continueLabel)
                builder.appendBlock(continueLabel)
                context.emitBlockTrace()
            }

            "br_table" -> {
                val operands = instruction.operands as Operands.BrTable
                val index = context.stack.pop()

                for ((tableIndex, depth) in operands.labels.withIndex()) {
                    val target = controlStack.targetAt(depth)
                    val nextLabel = context.freshLabel("br_table_next_$tableIndex")
                    val cmp = builder.icmp(ICmpPredicate.EQ, index, Constant.I32(tableIndex))
                    builder.condBr(cmp, target, nextLabel)
                    builder.appendBlock(nextLabel)
                    context.emitBlockTrace()
                }

                val defaultTarget = controlStack.targetAt(operands.default)
                builder.br(defaultTarget)
                val unreachableLabel = context.freshLabel("br_table_unreachable")
                builder.appendBlock(unreachableLabel)
                context.emitBlockTrace()
            }
        }
    }

    private fun blockResultCount(instruction: WasmInstruction): Int {
        val operands = instruction.operands
        if (operands is Operands.BlockType) {
            val typeCode = operands.type
            return when (typeCode) {
                0x40 -> 0
                0x7F, 0x7E, 0x7D, 0x7C -> 1
                else -> if (typeCode >= 0) { 1 } else { 0 }
            }
        }
        return 0
    }
}

enum class ControlKind { BLOCK, LOOP, IF }

class ControlEntry(
    val kind: ControlKind,
    val label: String,
    var elseLabel: String? = null,
    val resultCount: Int = 0,
)

class ControlStack {
    private val entries = ArrayDeque<ControlEntry>()

    fun push(entry: ControlEntry) { entries.addLast(entry) }
    fun pop(): ControlEntry = entries.removeLast()
    fun peek(): ControlEntry = entries.last()
    fun isEmpty(): Boolean = entries.isEmpty()

    fun targetAt(depth: Int): String {
        val index = entries.size - 1 - depth
        val entry = entries[index]
        return entry.label
    }
}
