package org.kgen.target.clr.asm

import org.kgen.target.clr.CilOpCode
import org.kgen.target.clr.CilOperandType

/**
 * A decoded CIL instruction.
 */
data class CilInstruction(
    val offset: Int,
    val opcode: CilOpCode,
    val operand: Long = 0,
    val size: Int,
) {
    override fun toString(): String {
        val mnemonic = opcode.mnemonic()
        return when (opcode.operandType) {
            CilOperandType.NONE -> mnemonic
            CilOperandType.I8 -> {
                if (opcode in BRANCH_SHORT_OPS) "$mnemonic IL_%04x".format(offset + size + operand.toInt())
                else "$mnemonic $operand"
            }
            CilOperandType.U8 -> "$mnemonic $operand"
            CilOperandType.I16, CilOperandType.U16 -> "$mnemonic $operand"
            CilOperandType.I32 -> {
                if (opcode in BRANCH_LONG_OPS) "$mnemonic IL_%04x".format(offset + size + operand.toInt())
                else if (opcode.operandType == CilOperandType.I32 && opcode == CilOpCode.LDC_I4) "$mnemonic $operand"
                else "$mnemonic 0x%08x".format(operand.toInt())
            }
            CilOperandType.I64 -> "$mnemonic $operand"
            CilOperandType.F32 -> "$mnemonic ${Float.fromBits(operand.toInt())}"
            CilOperandType.F64 -> "$mnemonic ${Double.fromBits(operand)}"
            CilOperandType.TOKEN -> "$mnemonic 0x%08x".format(operand.toInt())
            CilOperandType.SWITCH -> "$mnemonic ($operand targets)"
        }
    }

    companion object {
        private val BRANCH_SHORT_OPS = setOf(
            CilOpCode.BR_S, CilOpCode.BRFALSE_S, CilOpCode.BRTRUE_S,
            CilOpCode.BEQ_S, CilOpCode.BGE_S, CilOpCode.BGT_S, CilOpCode.BLE_S, CilOpCode.BLT_S,
            CilOpCode.BNE_UN_S, CilOpCode.BGE_UN_S, CilOpCode.BGT_UN_S, CilOpCode.BLE_UN_S, CilOpCode.BLT_UN_S,
            CilOpCode.LEAVE_S,
        )
        private val BRANCH_LONG_OPS = setOf(
            CilOpCode.BR, CilOpCode.BRFALSE, CilOpCode.BRTRUE,
            CilOpCode.BEQ, CilOpCode.BGE, CilOpCode.BGT, CilOpCode.BLE, CilOpCode.BLT,
            CilOpCode.BNE_UN, CilOpCode.BGE_UN, CilOpCode.BGT_UN, CilOpCode.BLE_UN, CilOpCode.BLT_UN,
            CilOpCode.LEAVE,
        )
    }
}

/**
 * CIL bytecode disassembler. Decodes a CIL method body into structured instructions.
 *
 * ```java
 * var disasm = new CilDisassembler();
 * var instructions = disasm.disassemble(methodBody);
 * for (var inst : instructions) {
 *     System.out.printf("IL_%04x: %s%n", inst.offset(), inst);
 * }
 * ```
 */
class CilDisassembler {

    fun disassemble(code: ByteArray): List<CilInstruction> {
        return disassemble(code, 0, code.size)
    }

    fun disassemble(code: ByteArray, offset: Int, length: Int): List<CilInstruction> {
        val instructions = mutableListOf<CilInstruction>()
        var pc = offset
        val end = offset + length

        while (pc < end) {
            val startPc = pc
            val byte1 = code[pc].toInt() and 0xFF
            pc++

            val opcode: CilOpCode?
            if (byte1 == 0xFE) {
                if (pc >= end) break
                val byte2 = code[pc].toInt() and 0xFF
                pc++
                opcode = CilOpCode.fromTwoByte(byte2)
            } else {
                opcode = CilOpCode.fromByte(byte1)
            }

            if (opcode == null) {
                instructions.add(CilInstruction(startPc - offset, CilOpCode.NOP, byte1.toLong(), pc - startPc))
                continue
            }

            var operand = 0L
            when (opcode.operandType) {
                CilOperandType.NONE -> {}
                CilOperandType.I8 -> {
                    operand = code[pc].toLong()
                    pc++
                }
                CilOperandType.U8 -> {
                    operand = (code[pc].toInt() and 0xFF).toLong()
                    pc++
                }
                CilOperandType.I16 -> {
                    operand = readI16(code, pc).toLong()
                    pc += 2
                }
                CilOperandType.U16 -> {
                    operand = readU16(code, pc).toLong()
                    pc += 2
                }
                CilOperandType.I32, CilOperandType.TOKEN -> {
                    operand = readI32(code, pc).toLong()
                    pc += 4
                }
                CilOperandType.I64 -> {
                    operand = readI64(code, pc)
                    pc += 8
                }
                CilOperandType.F32 -> {
                    operand = readI32(code, pc).toLong()
                    pc += 4
                }
                CilOperandType.F64 -> {
                    operand = readI64(code, pc)
                    pc += 8
                }
                CilOperandType.SWITCH -> {
                    val count = readI32(code, pc)
                    operand = count.toLong()
                    pc += 4 + count * 4
                }
            }

            instructions.add(CilInstruction(startPc - offset, opcode, operand, pc - startPc))
        }

        return instructions
    }

    private fun readU16(code: ByteArray, offset: Int): Int =
        (code[offset].toInt() and 0xFF) or ((code[offset + 1].toInt() and 0xFF) shl 8)

    private fun readI16(code: ByteArray, offset: Int): Int {
        val v = readU16(code, offset)
        return if (v >= 0x8000) v - 0x10000 else v
    }

    private fun readI32(code: ByteArray, offset: Int): Int =
        (code[offset].toInt() and 0xFF) or
            ((code[offset + 1].toInt() and 0xFF) shl 8) or
            ((code[offset + 2].toInt() and 0xFF) shl 16) or
            ((code[offset + 3].toInt() and 0xFF) shl 24)

    private fun readI64(code: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((code[offset + i].toLong() and 0xFF) shl (i * 8))
        return v
    }
}
