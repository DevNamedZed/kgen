package org.wark.compile.translate

import org.kgen.ir.*
import org.kgen.target.wasm.WasmOpCode
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.disasm.WasmInstruction.Operands
import org.wark.compile.CompilationContext

class BlockTranslator : InstructionTranslator {

    private val handled = setOf(
        WasmOpCode.BLOCK, WasmOpCode.LOOP, WasmOpCode.IF, WasmOpCode.ELSE, WasmOpCode.END,
        WasmOpCode.BR, WasmOpCode.BR_IF, WasmOpCode.BR_TABLE,
    )

    override fun canHandle(opcode: WasmOpCode): Boolean = opcode in handled

    override fun translate(context: CompilationContext, instruction: WasmInstruction) {
        val builder = context.builder
        val controlStack = context.controlStack

        when (instruction.opcode) {
            WasmOpCode.BLOCK -> {
                val endLabel = context.freshLabel("block_end")
                val resultCount = blockResultCount(instruction)
                context.stack.save()
                controlStack.push(ControlEntry(ControlKind.BLOCK, endLabel, resultCount = resultCount, resultType = blockResultType(instruction)))
            }

            WasmOpCode.LOOP -> {
                val headerLabel = context.freshLabel("loop_header")
                context.stack.save()
                builder.br(headerLabel)
                builder.appendBlock(headerLabel)
                context.currentBlockLabel = headerLabel
                context.emitBlockTrace()
                controlStack.push(ControlEntry(ControlKind.LOOP, headerLabel, resultCount = 0))
            }

            WasmOpCode.IF -> {
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
                context.currentBlockLabel = thenLabel
                context.emitBlockTrace()
                controlStack.push(ControlEntry(ControlKind.IF, endLabel, elseLabel, resultCount, blockResultType(instruction)))
            }

            WasmOpCode.ELSE -> {
                val entry = controlStack.peek()
                if (entry.kind == ControlKind.IF) {
                    val elseLabel = entry.elseLabel ?: return
                    recordResultsForEntry(context, entry)
                    context.stack.restore(entry.resultCount)
                    context.stack.save()
                    builder.br(entry.label)
                    builder.appendBlock(elseLabel)
                    context.currentBlockLabel = elseLabel
                    context.emitBlockTrace()
                    entry.elseLabel = null
                }
            }

            WasmOpCode.END -> {
                if (controlStack.isEmpty()) {
                    return
                }
                val entry = controlStack.pop()
                when (entry.kind) {
                    ControlKind.BLOCK -> {
                        recordResultsForEntry(context, entry)
                        context.stack.restore(entry.resultCount)
                        builder.br(entry.label)
                        builder.appendBlock(entry.label)
                        context.currentBlockLabel = entry.label
                        context.emitBlockTrace()
                        emitPhiResults(context, entry)
                    }
                    ControlKind.LOOP -> {
                        context.stack.restore(entry.resultCount)
                    }
                    ControlKind.IF -> {
                        recordResultsForEntry(context, entry)
                        context.stack.restore(entry.resultCount)
                        if (entry.elseLabel != null) {
                            builder.br(entry.label)
                            builder.appendBlock(entry.elseLabel!!)
                            context.currentBlockLabel = entry.elseLabel!!
                            context.emitBlockTrace()
                            if (entry.resultCount > 0) {
                                val defaultValue = org.wark.compile.WasmToIrCompiler.defaultValue(entry.resultType)
                                entry.pendingResults.add(defaultValue to entry.elseLabel!!)
                            }
                        }
                        builder.br(entry.label)
                        builder.appendBlock(entry.label)
                        context.currentBlockLabel = entry.label
                        context.emitBlockTrace()
                        emitPhiResults(context, entry)
                    }
                }
            }

            WasmOpCode.BR -> {
                val depth = (instruction.operands as Operands.Index).value
                val entry = controlStack.entryAt(depth)
                recordBranchResults(context, entry)
                val target = controlStack.targetAt(depth)
                builder.br(target)
                val unreachableLabel = context.freshLabel("unreachable")
                builder.appendBlock(unreachableLabel)
                context.currentBlockLabel = unreachableLabel
                context.emitBlockTrace()
            }

            WasmOpCode.BR_IF -> {
                val depth = (instruction.operands as Operands.Index).value
                val condition = context.stack.pop()
                val entry = controlStack.entryAt(depth)
                val target = controlStack.targetAt(depth)
                val continueLabel = context.freshLabel("br_if_cont")

                if (entry.resultCount > 0 && entry.kind != ControlKind.LOOP) {
                    val resultValue = context.stack.peek()
                    entry.pendingResults.add(resultValue to context.currentBlockLabel)
                }

                val i32Cond = if (condition.type == Type.I1) {
                    condition
                } else {
                    builder.icmp(ICmpPredicate.NE, condition, Constant.I32(0))
                }

                builder.condBr(i32Cond, target, continueLabel)
                builder.appendBlock(continueLabel)
                context.currentBlockLabel = continueLabel
                context.emitBlockTrace()
            }

            WasmOpCode.BR_TABLE -> {
                val operands = instruction.operands as Operands.BrTable
                val index = context.stack.pop()

                for ((tableIndex, depth) in operands.labels.withIndex()) {
                    val entry = controlStack.entryAt(depth)
                    recordBranchResults(context, entry)
                    val target = controlStack.targetAt(depth)
                    val nextLabel = context.freshLabel("br_table_next_$tableIndex")
                    val cmp = builder.icmp(ICmpPredicate.EQ, index, Constant.I32(tableIndex))
                    builder.condBr(cmp, target, nextLabel)
                    builder.appendBlock(nextLabel)
                    context.currentBlockLabel = nextLabel
                    context.emitBlockTrace()
                }

                val defaultEntry = controlStack.entryAt(operands.default)
                recordBranchResults(context, defaultEntry)
                val defaultTarget = controlStack.targetAt(operands.default)
                builder.br(defaultTarget)
                val unreachableLabel = context.freshLabel("br_table_unreachable")
                builder.appendBlock(unreachableLabel)
                context.currentBlockLabel = unreachableLabel
                context.emitBlockTrace()
            }
            else -> { }
        }
    }

    private fun recordResultsForEntry(context: CompilationContext, entry: ControlEntry) {
        if (entry.resultCount > 0 && context.stack.size() > 0) {
            val resultValue = context.stack.peek()
            entry.pendingResults.add(resultValue to context.currentBlockLabel)
        }
    }

    private fun recordBranchResults(context: CompilationContext, entry: ControlEntry) {
        if (entry.resultCount > 0 && entry.kind != ControlKind.LOOP && context.stack.size() > 0) {
            val resultValue = context.stack.peek()
            entry.pendingResults.add(resultValue to context.currentBlockLabel)
        }
    }

    private fun emitPhiResults(context: CompilationContext, entry: ControlEntry) {
        if (entry.resultCount > 0 && entry.pendingResults.size > 1) {
            val resultType = entry.pendingResults[0].first.type
            val incoming = entry.pendingResults.map { (value, label) ->
                value to org.kgen.ir.BlockRef(label)
            }
            val ref = context.builder.nextRef(resultType)
            context.builder.emit(org.kgen.ir.instructions.Phi(ref, incoming))
            context.stack.push(ref)
        } else if (entry.resultCount > 0 && entry.pendingResults.size == 1) {
            context.stack.push(entry.pendingResults[0].first)
        }
    }

    private fun blockResultType(instruction: WasmInstruction): org.kgen.ir.Type {
        val operands = instruction.operands
        if (operands is Operands.BlockType) {
            return when (operands.type) {
                -1 -> org.kgen.ir.Type.I32
                -2 -> org.kgen.ir.Type.I64
                -3 -> org.kgen.ir.Type.F32
                -4 -> org.kgen.ir.Type.F64
                else -> org.kgen.ir.Type.Void
            }
        }
        return org.kgen.ir.Type.Void
    }

    private fun blockResultCount(instruction: WasmInstruction): Int {
        val operands = instruction.operands
        if (operands is Operands.BlockType) {
            val typeCode = operands.type
            // Block type is decoded as signed LEB128:
            //   0x40 → -64 (void)
            //   0x7F → -1 (i32), 0x7E → -2 (i64), 0x7D → -3 (f32), 0x7C → -4 (f64)
            //   positive values → type index (multi-value)
            return when (typeCode) {
                -64 -> 0
                -1, -2, -3, -4 -> 1
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
    val resultType: org.kgen.ir.Type = org.kgen.ir.Type.Void,
    val pendingResults: MutableList<Pair<Value, String>> = mutableListOf(),
)

class ControlStack {
    private val entries = ArrayDeque<ControlEntry>()

    fun push(entry: ControlEntry) { entries.addLast(entry) }
    fun pop(): ControlEntry = entries.removeLast()
    fun peek(): ControlEntry = entries.last()
    fun isEmpty(): Boolean = entries.isEmpty()

    fun entryAt(depth: Int): ControlEntry {
        val index = entries.size - 1 - depth
        return entries[index]
    }

    fun targetAt(depth: Int): String = entryAt(depth).label
}
