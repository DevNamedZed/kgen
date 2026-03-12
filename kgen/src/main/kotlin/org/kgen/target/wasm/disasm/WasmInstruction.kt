package org.kgen.target.wasm.disasm

import org.kgen.target.wasm.WasmOpCode
import org.kgen.reflect.Instruction

/**
 * A decoded WebAssembly instruction with its operands.
 *
 * Produced by [WasmDisassembler.disassemble].
 */
data class WasmInstruction(
    val offset: Int,
    val opcode: WasmOpCode,
    val operands: Operands = Operands.None,
) : Instruction {

    override val address: Long get() = offset.toLong()
    override val bytes: ByteArray get() = ByteArray(0)
    override val mnemonic: String get() = opcode.mnemonic

    override fun operandsText(): String = buildString {
        when (val op = operands) {
            is Operands.None -> {}
            is Operands.Index -> append("$${op.value}")
            is Operands.I32 -> append("${op.value}")
            is Operands.I64 -> append("${op.value}")
            is Operands.F32 -> append("${op.value}")
            is Operands.F64 -> append("${op.value}")
            is Operands.MemArg -> append("align=${op.align} offset=${op.offset}")
            is Operands.BlockType -> append("${op.type}")
            is Operands.BrTable -> {
                for (label in op.labels) append("$label ")
                append("${op.default}")
            }
            is Operands.CallIndirect -> append("type=${op.typeIndex} table=${op.tableIndex}")
            is Operands.TwoIndex -> append("${op.first} ${op.second}")
            is Operands.V128 -> append(op.bytes.joinToString(" ") { "0x${it.toInt() and 0xFF}" })
            is Operands.RefType -> append("${op.type}")
            is Operands.ValTypes -> {
                for ((i, t) in op.types.withIndex()) {
                    if (i > 0) append(" ")
                    append("$t")
                }
            }
        }
    }

    override fun text(): String = buildString {
        append(opcode.mnemonic)
        val ops = operandsText()
        if (ops.isNotEmpty()) {
            append(" ")
            append(ops)
        }
    }

    sealed interface Operands {
        data object None : Operands
        data class Index(val value: Int) : Operands
        data class I32(val value: Int) : Operands
        data class I64(val value: Long) : Operands
        data class F32(val value: Float) : Operands
        data class F64(val value: Double) : Operands
        data class MemArg(val align: Int, val offset: Int) : Operands
        data class BlockType(val type: Int) : Operands
        data class BrTable(val labels: List<Int>, val default: Int) : Operands
        data class CallIndirect(val typeIndex: Int, val tableIndex: Int) : Operands
        data class TwoIndex(val first: Int, val second: Int) : Operands
        data class V128(val bytes: ByteArray) : Operands {
            override fun equals(other: Any?) = this === other || (other is V128 && bytes.contentEquals(other.bytes))
            override fun hashCode(): Int = bytes.contentHashCode()
        }
        data class RefType(val type: Int) : Operands
        data class ValTypes(val types: List<Int>) : Operands
    }
}
