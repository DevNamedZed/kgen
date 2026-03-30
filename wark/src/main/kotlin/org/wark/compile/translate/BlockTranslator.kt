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
        WasmOpCode.TRY, WasmOpCode.CATCH, WasmOpCode.CATCH_ALL,
        WasmOpCode.THROW, WasmOpCode.RETHROW, WasmOpCode.DELEGATE,
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

            WasmOpCode.TRY -> {
                val endLabel = context.freshLabel("try_end")
                val catchLabel = context.freshLabel("try_catch")
                val resultCount = blockResultCount(instruction)
                context.stack.save()
                context.tryDepth++
                controlStack.push(ControlEntry(ControlKind.TRY, endLabel, catchLabel,
                    resultCount, blockResultType(instruction)))
            }

            WasmOpCode.CATCH -> {
                val entry = controlStack.peek()
                if (entry.kind == ControlKind.TRY) {
                    val catchLabel = entry.elseLabel ?: context.freshLabel("catch_body")
                    recordResultsForEntry(context, entry)
                    context.stack.restore(entry.resultCount)
                    context.stack.save()
                    context.tryDepth--
                    builder.br(entry.label)
                    builder.appendBlock(catchLabel)
                    context.currentBlockLabel = catchLabel
                    entry.elseLabel = null

                    val tagIndex = (instruction.operands as Operands.Index).value
                    emitExceptionCheck(context, entry, tagIndex)
                }
            }

            WasmOpCode.CATCH_ALL -> {
                val entry = controlStack.peek()
                if (entry.kind == ControlKind.TRY) {
                    val catchLabel = entry.elseLabel ?: context.freshLabel("catch_all")
                    recordResultsForEntry(context, entry)
                    context.stack.restore(entry.resultCount)
                    context.stack.save()
                    context.tryDepth--
                    builder.br(entry.label)
                    builder.appendBlock(catchLabel)
                    context.currentBlockLabel = catchLabel
                    entry.elseLabel = null
                    clearExceptionFlag(context)
                }
            }

            WasmOpCode.THROW -> {
                val tagIndex = (instruction.operands as Operands.Index).value
                emitThrow(context, tagIndex)
                val unreachableLabel = context.freshLabel("throw_unreachable")
                builder.appendBlock(unreachableLabel)
                context.currentBlockLabel = unreachableLabel
            }

            WasmOpCode.RETHROW -> {
                context.emitDefaultReturn()
                val unreachableLabel = context.freshLabel("rethrow_unreachable")
                builder.appendBlock(unreachableLabel)
                context.currentBlockLabel = unreachableLabel
            }

            WasmOpCode.DELEGATE -> {
                val entry = controlStack.pop()
                if (entry.kind == ControlKind.TRY) {
                    context.tryDepth--
                    recordResultsForEntry(context, entry)
                    context.stack.restore(entry.resultCount)
                    builder.br(entry.label)
                    builder.appendBlock(entry.label)
                    context.currentBlockLabel = entry.label
                    emitPhiResults(context, entry)
                }
            }

            WasmOpCode.LOOP -> {
                val headerLabel = context.freshLabel("loop_header")
                val resultCount = blockResultCount(instruction)
                context.stack.save()
                builder.br(headerLabel)
                builder.appendBlock(headerLabel)
                context.currentBlockLabel = headerLabel
                context.emitBlockTrace()
                controlStack.push(ControlEntry(ControlKind.LOOP, headerLabel, resultCount = resultCount, resultType = blockResultType(instruction)))
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
                        if (entry.resultCount > 0) {
                            recordResultsForEntry(context, entry)
                            context.stack.restore(entry.resultCount)
                            val endLabel = context.freshLabel("loop_end")
                            builder.br(endLabel)
                            builder.appendBlock(endLabel)
                            context.currentBlockLabel = endLabel
                            emitPhiResults(context, entry)
                        } else {
                            context.stack.restore(entry.resultCount)
                        }
                    }
                    ControlKind.TRY -> {
                        recordResultsForEntry(context, entry)
                        context.stack.restore(entry.resultCount)
                        if (entry.elseLabel != null) {
                            context.tryDepth--
                        }
                        builder.br(entry.label)
                        builder.appendBlock(entry.label)
                        context.currentBlockLabel = entry.label
                        context.emitBlockTrace()
                        emitPhiResults(context, entry)
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
                if (depth >= controlStack.size()) {
                    if (context.stack.isEmpty()) {
                        builder.ret()
                    } else {
                        builder.ret(context.stack.pop())
                    }
                } else {
                    val entry = controlStack.entryAt(depth)
                    recordBranchResults(context, entry)
                    val target = controlStack.targetAt(depth)
                    builder.br(target)
                }
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
                    if (depth >= controlStack.size()) {
                        val retLabel = context.freshLabel("br_table_ret_$tableIndex")
                        val nextLabel = context.freshLabel("br_table_next_$tableIndex")
                        val cmp = builder.icmp(ICmpPredicate.EQ, index, Constant.I32(tableIndex))
                        builder.condBr(cmp, retLabel, nextLabel)
                        builder.appendBlock(retLabel)
                        if (context.stack.isEmpty()) { builder.ret() } else { builder.ret(context.stack.peek()) }
                        builder.appendBlock(nextLabel)
                        context.currentBlockLabel = nextLabel
                        context.emitBlockTrace()
                    } else {
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
                }

                if (operands.default >= controlStack.size()) {
                    if (context.stack.isEmpty()) { builder.ret() } else { builder.ret(context.stack.peek()) }
                } else {
                    val defaultEntry = controlStack.entryAt(operands.default)
                    recordBranchResults(context, defaultEntry)
                    val defaultTarget = controlStack.targetAt(operands.default)
                    builder.br(defaultTarget)
                }
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

    private fun emitExceptionCheck(context: CompilationContext, entry: ControlEntry, tagIndex: Int) {
        val builder = context.builder

        val tagPtr = builder.add(context.contextPointer, Constant.I64(org.wark.RuntimeContextLayout.EXC_TAG))
        val caughtTag = builder.load(Type.I32, tagPtr)
        val tagMatch = builder.icmp(ICmpPredicate.EQ, caughtTag, Constant.I32(tagIndex))
        val matchLabel = context.freshLabel("catch_match")
        val mismatchLabel = context.freshLabel("catch_mismatch")
        builder.condBr(tagMatch, matchLabel, mismatchLabel)

        builder.appendBlock(mismatchLabel)
        context.emitDefaultReturn()

        builder.appendBlock(matchLabel)
        clearExceptionFlag(context)
        val tagType = context.wasmModule.imports
            .filterIsInstance<org.kgen.target.wasm.module.WasmModule.Import.Tag>()
            .getOrNull(tagIndex)
        if (tagType != null) {
            val funcType = context.wasmModule.types[tagType.typeIndex]
            for (paramIndex in funcType.params.indices) {
                val offset = org.wark.RuntimeContextLayout.EXC_VALUES + paramIndex * 8L
                val valuePtr = builder.add(context.contextPointer, Constant.I64(offset))
                val irType = org.wark.compile.WasmToIrCompiler.wasmTypeToIr(funcType.params[paramIndex])
                val value = builder.load(Type.I64, valuePtr)
                val typed = if (irType == Type.I32) {
                    builder.trunc(value, Type.I32)
                } else {
                    value
                }
                context.stack.push(typed)
            }
        }
    }

    private fun clearExceptionFlag(context: CompilationContext) {
        val builder = context.builder
        val excPtr = builder.add(context.contextPointer, Constant.I64(org.wark.RuntimeContextLayout.EXC_PENDING))
        builder.store(Constant.I32(0), excPtr)
    }

    private fun emitThrow(context: CompilationContext, tagIndex: Int) {
        val builder = context.builder
        val tagType = context.wasmModule.imports
            .filterIsInstance<org.kgen.target.wasm.module.WasmModule.Import.Tag>()
            .getOrNull(tagIndex)
        if (tagType != null) {
            val funcType = context.wasmModule.types[tagType.typeIndex]
            val values = mutableListOf<Value>()
            for (paramIndex in funcType.params.indices) {
                values.add(context.stack.pop())
            }
            values.reverse()
            for ((paramIndex, value) in values.withIndex()) {
                val offset = org.wark.RuntimeContextLayout.EXC_VALUES + paramIndex * 8L
                val valuePtr = builder.add(context.contextPointer, Constant.I64(offset))
                val value64 = if (value.type == Type.I32) {
                    builder.zext(value, Type.I64)
                } else {
                    value
                }
                builder.store(value64, valuePtr)
            }
        }
        val tagPtr = builder.add(context.contextPointer, Constant.I64(org.wark.RuntimeContextLayout.EXC_TAG))
        builder.store(Constant.I32(tagIndex), tagPtr)
        val excPtr = builder.add(context.contextPointer, Constant.I64(org.wark.RuntimeContextLayout.EXC_PENDING))
        builder.store(Constant.I32(1), excPtr)
        context.emitDefaultReturn()
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

enum class ControlKind { BLOCK, LOOP, IF, TRY }

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
    fun size(): Int = entries.size

    fun entryAt(depth: Int): ControlEntry {
        val index = entries.size - 1 - depth
        return entries[index]
    }

    fun targetAt(depth: Int): String = entryAt(depth).label
}
