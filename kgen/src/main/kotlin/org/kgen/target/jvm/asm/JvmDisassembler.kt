package org.kgen.target.jvm.asm

import org.kgen.target.jvm.JvmOpCode

/**
 * JVM bytecode disassembler. Decodes raw bytecode into structured instructions.
 *
 * ```java
 * var dis = new JvmDisassembler();
 * var instructions = dis.disassemble(codeBytes);
 * for (var insn : instructions) {
 *     System.out.println(insn);  // "0: iload_0"
 * }
 * ```
 */
class JvmDisassembler {

    /**
     * A disassembled JVM bytecode instruction.
     */
    data class JvmInstruction(
        val offset: Int,
        val opcode: JvmOpCode,
        val operands: String,
        val size: Int,
    ) {
        override fun toString(): String {
            val name = opcode.name.lowercase()
            return if (operands.isEmpty()) "$offset: $name" else "$offset: $name $operands"
        }
    }

    /**
     * Disassemble all instructions in [code].
     */
    fun disassemble(code: ByteArray): List<JvmInstruction> {
        val result = mutableListOf<JvmInstruction>()
        var offset = 0
        while (offset < code.size) {
            val insn = disassembleOne(code, offset) ?: break
            result.add(insn)
            offset += insn.size
        }
        return result
    }

    /**
     * Disassemble one instruction at [offset].
     */
    fun disassembleOne(code: ByteArray, offset: Int): JvmInstruction? {
        if (offset >= code.size) return null
        val byte = code[offset].toInt() and 0xFF
        val opcode = JvmOpCode.fromCode(byte) ?: return null

        return when (opcode) {
            // No operands (1 byte)
            JvmOpCode.NOP, JvmOpCode.ACONST_NULL,
            JvmOpCode.ICONST_M1, JvmOpCode.ICONST_0, JvmOpCode.ICONST_1, JvmOpCode.ICONST_2,
            JvmOpCode.ICONST_3, JvmOpCode.ICONST_4, JvmOpCode.ICONST_5,
            JvmOpCode.LCONST_0, JvmOpCode.LCONST_1,
            JvmOpCode.FCONST_0, JvmOpCode.FCONST_1, JvmOpCode.FCONST_2,
            JvmOpCode.DCONST_0, JvmOpCode.DCONST_1,
            JvmOpCode.ILOAD_0, JvmOpCode.ILOAD_1, JvmOpCode.ILOAD_2, JvmOpCode.ILOAD_3,
            JvmOpCode.LLOAD_0, JvmOpCode.LLOAD_1, JvmOpCode.LLOAD_2, JvmOpCode.LLOAD_3,
            JvmOpCode.FLOAD_0, JvmOpCode.FLOAD_1, JvmOpCode.FLOAD_2, JvmOpCode.FLOAD_3,
            JvmOpCode.DLOAD_0, JvmOpCode.DLOAD_1, JvmOpCode.DLOAD_2, JvmOpCode.DLOAD_3,
            JvmOpCode.ALOAD_0, JvmOpCode.ALOAD_1, JvmOpCode.ALOAD_2, JvmOpCode.ALOAD_3,
            JvmOpCode.IALOAD, JvmOpCode.LALOAD, JvmOpCode.FALOAD, JvmOpCode.DALOAD,
            JvmOpCode.AALOAD, JvmOpCode.BALOAD, JvmOpCode.CALOAD, JvmOpCode.SALOAD,
            JvmOpCode.ISTORE_0, JvmOpCode.ISTORE_1, JvmOpCode.ISTORE_2, JvmOpCode.ISTORE_3,
            JvmOpCode.LSTORE_0, JvmOpCode.LSTORE_1, JvmOpCode.LSTORE_2, JvmOpCode.LSTORE_3,
            JvmOpCode.FSTORE_0, JvmOpCode.FSTORE_1, JvmOpCode.FSTORE_2, JvmOpCode.FSTORE_3,
            JvmOpCode.DSTORE_0, JvmOpCode.DSTORE_1, JvmOpCode.DSTORE_2, JvmOpCode.DSTORE_3,
            JvmOpCode.ASTORE_0, JvmOpCode.ASTORE_1, JvmOpCode.ASTORE_2, JvmOpCode.ASTORE_3,
            JvmOpCode.IASTORE, JvmOpCode.LASTORE, JvmOpCode.FASTORE, JvmOpCode.DASTORE,
            JvmOpCode.AASTORE, JvmOpCode.BASTORE, JvmOpCode.CASTORE, JvmOpCode.SASTORE,
            JvmOpCode.POP, JvmOpCode.POP2, JvmOpCode.DUP, JvmOpCode.DUP_X1, JvmOpCode.DUP_X2,
            JvmOpCode.DUP2, JvmOpCode.DUP2_X1, JvmOpCode.DUP2_X2, JvmOpCode.SWAP,
            JvmOpCode.IADD, JvmOpCode.LADD, JvmOpCode.FADD, JvmOpCode.DADD,
            JvmOpCode.ISUB, JvmOpCode.LSUB, JvmOpCode.FSUB, JvmOpCode.DSUB,
            JvmOpCode.IMUL, JvmOpCode.LMUL, JvmOpCode.FMUL, JvmOpCode.DMUL,
            JvmOpCode.IDIV, JvmOpCode.LDIV, JvmOpCode.FDIV, JvmOpCode.DDIV,
            JvmOpCode.IREM, JvmOpCode.LREM, JvmOpCode.FREM, JvmOpCode.DREM,
            JvmOpCode.INEG, JvmOpCode.LNEG, JvmOpCode.FNEG, JvmOpCode.DNEG,
            JvmOpCode.ISHL, JvmOpCode.LSHL, JvmOpCode.ISHR, JvmOpCode.LSHR,
            JvmOpCode.IUSHR, JvmOpCode.LUSHR,
            JvmOpCode.IAND, JvmOpCode.LAND, JvmOpCode.IOR, JvmOpCode.LOR,
            JvmOpCode.IXOR, JvmOpCode.LXOR,
            JvmOpCode.I2L, JvmOpCode.I2F, JvmOpCode.I2D, JvmOpCode.L2I, JvmOpCode.L2F, JvmOpCode.L2D,
            JvmOpCode.F2I, JvmOpCode.F2L, JvmOpCode.F2D, JvmOpCode.D2I, JvmOpCode.D2L, JvmOpCode.D2F,
            JvmOpCode.I2B, JvmOpCode.I2C, JvmOpCode.I2S,
            JvmOpCode.LCMP, JvmOpCode.FCMPL, JvmOpCode.FCMPG, JvmOpCode.DCMPL, JvmOpCode.DCMPG,
            JvmOpCode.IRETURN, JvmOpCode.LRETURN, JvmOpCode.FRETURN, JvmOpCode.DRETURN,
            JvmOpCode.ARETURN, JvmOpCode.RETURN,
            JvmOpCode.ARRAYLENGTH, JvmOpCode.ATHROW, JvmOpCode.MONITORENTER, JvmOpCode.MONITOREXIT ->
                JvmInstruction(offset, opcode, "", 1)

            // 1-byte operand
            JvmOpCode.BIPUSH -> {
                val value = code[offset + 1].toInt()
                JvmInstruction(offset, opcode, "$value", 2)
            }
            JvmOpCode.LDC -> {
                val index = code[offset + 1].toInt() and 0xFF
                JvmInstruction(offset, opcode, "#$index", 2)
            }
            JvmOpCode.ILOAD, JvmOpCode.LLOAD, JvmOpCode.FLOAD, JvmOpCode.DLOAD, JvmOpCode.ALOAD,
            JvmOpCode.ISTORE, JvmOpCode.LSTORE, JvmOpCode.FSTORE, JvmOpCode.DSTORE, JvmOpCode.ASTORE,
            JvmOpCode.RET -> {
                val index = code[offset + 1].toInt() and 0xFF
                JvmInstruction(offset, opcode, "$index", 2)
            }
            JvmOpCode.NEWARRAY -> {
                val atype = code[offset + 1].toInt() and 0xFF
                val typeName = arrayTypeName(atype)
                JvmInstruction(offset, opcode, typeName, 2)
            }

            // 2-byte operand (short)
            JvmOpCode.SIPUSH -> {
                val value = readShort(code, offset + 1)
                JvmInstruction(offset, opcode, "$value", 3)
            }
            JvmOpCode.LDC_W, JvmOpCode.LDC2_W -> {
                val index = readUShort(code, offset + 1)
                JvmInstruction(offset, opcode, "#$index", 3)
            }
            JvmOpCode.GETSTATIC, JvmOpCode.PUTSTATIC, JvmOpCode.GETFIELD, JvmOpCode.PUTFIELD,
            JvmOpCode.INVOKEVIRTUAL, JvmOpCode.INVOKESPECIAL, JvmOpCode.INVOKESTATIC,
            JvmOpCode.NEW, JvmOpCode.ANEWARRAY, JvmOpCode.CHECKCAST, JvmOpCode.INSTANCEOF -> {
                val index = readUShort(code, offset + 1)
                JvmInstruction(offset, opcode, "#$index", 3)
            }

            // Branch instructions (2-byte offset)
            JvmOpCode.IFEQ, JvmOpCode.IFNE, JvmOpCode.IFLT, JvmOpCode.IFGE, JvmOpCode.IFGT, JvmOpCode.IFLE,
            JvmOpCode.IF_ICMPEQ, JvmOpCode.IF_ICMPNE, JvmOpCode.IF_ICMPLT, JvmOpCode.IF_ICMPGE,
            JvmOpCode.IF_ICMPGT, JvmOpCode.IF_ICMPLE, JvmOpCode.IF_ACMPEQ, JvmOpCode.IF_ACMPNE,
            JvmOpCode.GOTO, JvmOpCode.JSR, JvmOpCode.IFNULL, JvmOpCode.IFNONNULL -> {
                val branchOffset = readShort(code, offset + 1)
                val target = offset + branchOffset
                JvmInstruction(offset, opcode, "$target", 3)
            }

            // iinc (2-byte: index, const)
            JvmOpCode.IINC -> {
                val index = code[offset + 1].toInt() and 0xFF
                val increment = code[offset + 2].toInt()
                JvmInstruction(offset, opcode, "$index, $increment", 3)
            }

            // invokeinterface (4-byte: index, count, 0)
            JvmOpCode.INVOKEINTERFACE -> {
                val index = readUShort(code, offset + 1)
                val count = code[offset + 3].toInt() and 0xFF
                JvmInstruction(offset, opcode, "#$index, $count", 5)
            }

            // invokedynamic (4-byte: index, 0, 0)
            JvmOpCode.INVOKEDYNAMIC -> {
                val index = readUShort(code, offset + 1)
                JvmInstruction(offset, opcode, "#$index", 5)
            }

            // multianewarray (3-byte: index, dimensions)
            JvmOpCode.MULTIANEWARRAY -> {
                val index = readUShort(code, offset + 1)
                val dims = code[offset + 3].toInt() and 0xFF
                JvmInstruction(offset, opcode, "#$index, $dims", 4)
            }

            // goto_w / jsr_w (4-byte offset)
            JvmOpCode.GOTO_W, JvmOpCode.JSR_W -> {
                val branchOffset = readInt(code, offset + 1)
                val target = offset + branchOffset
                JvmInstruction(offset, opcode, "$target", 5)
            }

            // wide prefix
            JvmOpCode.WIDE -> {
                if (offset + 1 >= code.size) return null
                val wideOp = code[offset + 1].toInt() and 0xFF
                val wideOpCode = JvmOpCode.fromCode(wideOp) ?: return null
                if (wideOpCode == JvmOpCode.IINC) {
                    val index = readUShort(code, offset + 2)
                    val increment = readShort(code, offset + 4)
                    JvmInstruction(offset, opcode, "${wideOpCode.name.lowercase()} $index, $increment", 6)
                } else {
                    val index = readUShort(code, offset + 2)
                    JvmInstruction(offset, opcode, "${wideOpCode.name.lowercase()} $index", 4)
                }
            }

            // tableswitch and lookupswitch — variable length, skip for now
            JvmOpCode.TABLESWITCH, JvmOpCode.LOOKUPSWITCH -> {
                // Align to 4-byte boundary after opcode
                val pad = (4 - ((offset + 1) % 4)) % 4
                if (opcode == JvmOpCode.TABLESWITCH) {
                    val defaultOffset = readInt(code, offset + 1 + pad)
                    val low = readInt(code, offset + 5 + pad)
                    val high = readInt(code, offset + 9 + pad)
                    val count = high - low + 1
                    val size = 1 + pad + 12 + count * 4
                    JvmInstruction(offset, opcode, "$low to $high, default: ${offset + defaultOffset}", size)
                } else {
                    val defaultOffset = readInt(code, offset + 1 + pad)
                    val npairs = readInt(code, offset + 5 + pad)
                    val size = 1 + pad + 8 + npairs * 8
                    JvmInstruction(offset, opcode, "$npairs pairs, default: ${offset + defaultOffset}", size)
                }
            }
        }
    }

    private fun readShort(code: ByteArray, offset: Int): Int {
        return (code[offset].toInt() shl 8) or (code[offset + 1].toInt() and 0xFF)
    }

    private fun readUShort(code: ByteArray, offset: Int): Int {
        return ((code[offset].toInt() and 0xFF) shl 8) or (code[offset + 1].toInt() and 0xFF)
    }

    private fun readInt(code: ByteArray, offset: Int): Int {
        return ((code[offset].toInt() and 0xFF) shl 24) or
            ((code[offset + 1].toInt() and 0xFF) shl 16) or
            ((code[offset + 2].toInt() and 0xFF) shl 8) or
            (code[offset + 3].toInt() and 0xFF)
    }

    private fun arrayTypeName(atype: Int): String = when (atype) {
        4 -> "boolean"
        5 -> "char"
        6 -> "float"
        7 -> "double"
        8 -> "byte"
        9 -> "short"
        10 -> "int"
        11 -> "long"
        else -> "type$atype"
    }
}
