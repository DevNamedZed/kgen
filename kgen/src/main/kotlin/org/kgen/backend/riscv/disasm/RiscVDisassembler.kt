package org.kgen.backend.riscv.disasm

import org.kgen.backend.riscv.RiscVRegisters
import org.kgen.reflect.Instruction

/**
 * RISC-V disassembler for RV64I + M extension.
 * Decodes 32-bit little-endian instructions into human-readable text.
 */
class RiscVDisassembler {

    data class DisassembledInsn(
        override val address: Long,
        val rawWord: Int,
        override val mnemonic: String,
        val operandsStr: String,
        private val compressed: Boolean = false,
    ) : Instruction {
        override val bytes: ByteArray get() = if (compressed) {
            byteArrayOf(
                (rawWord and 0xFF).toByte(),
                ((rawWord shr 8) and 0xFF).toByte(),
            )
        } else {
            byteArrayOf(
                (rawWord and 0xFF).toByte(),
                ((rawWord shr 8) and 0xFF).toByte(),
                ((rawWord shr 16) and 0xFF).toByte(),
                ((rawWord shr 24) and 0xFF).toByte(),
            )
        }
        override fun operandsText(): String = operandsStr
        override fun text(): String = "$mnemonic $operandsStr".trim()
        override fun toString(): String = text()
    }

    fun disassemble(code: ByteArray, baseAddress: Long = 0): List<DisassembledInsn> {
        val result = mutableListOf<DisassembledInsn>()
        var off = 0
        while (off < code.size) {
            val first = code[off].toInt() and 0xFF
            if ((first and 0x3) != 0x3 && off + 1 < code.size) {
                // Compressed 16-bit instruction
                val insn16 = (code[off].toInt() and 0xFF) or ((code[off + 1].toInt() and 0xFF) shl 8)
                result.add(decodeCompressed(insn16, baseAddress + off))
                off += 2
            } else if (off + 3 < code.size) {
                val insn = readU32(code, off)
                result.add(decodeInsn(insn, baseAddress + off))
                off += 4
            } else {
                break
            }
        }
        return result
    }

    fun disassembleOne(insn: Int, address: Long = 0): DisassembledInsn {
        val result = decodeInsn(insn, address)
        return result.copy(address = address)
    }

    private fun decodeInsn(insn: Int, addr: Long): DisassembledInsn {
        val opcode = insn and 0x7F
        val result = when (opcode) {
            0x33 -> decodeRType(insn, false)
            0x3B -> decodeRType(insn, true)
            0x13 -> decodeIArith(insn, false)
            0x1B -> decodeIArith(insn, true)
            0x03 -> decodeLoad(insn)
            0x07 -> decodeFpLoad(insn)
            0x23 -> decodeStore(insn)
            0x27 -> decodeFpStore(insn)
            0x53 -> decodeFpOp(insn)
            0x2F -> decodeAtomic(insn)
            0x63 -> decodeBranch(insn, addr)
            0x37 -> decodeLui(insn)
            0x17 -> decodeAuipc(insn)
            0x6F -> decodeJal(insn, addr)
            0x67 -> decodeJalr(insn)
            0x73 -> decodeSystem(insn)
            0x0F -> DisassembledInsn(0, insn, "fence", "")
            else -> DisassembledInsn(0, insn, ".word", "0x${insn.toUInt().toString(16)}")
        }
        return result.copy(address = addr)
    }

    private fun decodeRType(insn: Int, isWord: Boolean): DisassembledInsn {
        val rd = gp((insn shr 7) and 0x1F)
        val rs1 = gp((insn shr 15) and 0x1F)
        val rs2 = gp((insn shr 20) and 0x1F)
        val funct3 = (insn shr 12) and 0x7
        val funct7 = (insn ushr 25) and 0x7F
        val suffix = if (isWord) "w" else ""

        val mnemonic = when {
            funct7 == 0x01 -> when (funct3) {
                0 -> "mul$suffix"
                1 -> if (!isWord) "mulh" else "mul$suffix"
                2 -> if (!isWord) "mulhsu" else "unknown"
                3 -> if (!isWord) "mulhu" else "unknown"
                4 -> "div$suffix"
                5 -> "divu$suffix"
                6 -> "rem$suffix"
                7 -> "remu$suffix"
                else -> "unknown"
            }
            funct7 == 0x20 -> when (funct3) {
                0 -> "sub$suffix"
                5 -> "sra$suffix"
                else -> "unknown"
            }
            funct7 == 0x00 -> when (funct3) {
                0 -> "add$suffix"
                1 -> "sll$suffix"
                2 -> if (!isWord) "slt" else "unknown"
                3 -> if (!isWord) "sltu" else "unknown"
                4 -> if (!isWord) "xor" else "unknown"
                5 -> "srl$suffix"
                6 -> if (!isWord) "or" else "unknown"
                7 -> if (!isWord) "and" else "unknown"
                else -> "unknown"
            }
            else -> "unknown"
        }

        // Detect pseudo-instructions
        if (!isWord && funct7 == 0x20 && funct3 == 0 && (insn shr 15) and 0x1F == 0) {
            return DisassembledInsn(0, insn, "neg", "$rd, $rs2")
        }
        if (isWord && funct7 == 0x20 && funct3 == 0 && (insn shr 15) and 0x1F == 0) {
            return DisassembledInsn(0, insn, "negw", "$rd, $rs2")
        }

        return DisassembledInsn(0, insn, mnemonic, "$rd, $rs1, $rs2")
    }

    private fun decodeIArith(insn: Int, isWord: Boolean): DisassembledInsn {
        val rd = gp((insn shr 7) and 0x1F)
        val rs1 = gp((insn shr 15) and 0x1F)
        val imm = signExtend(insn ushr 20, 12)
        val funct3 = (insn shr 12) and 0x7
        val suffix = if (isWord) "w" else ""

        // Shift instructions use imm[5:0] as shamt (or imm[4:0] for word)
        val shamtMask = if (isWord) 0x1F else 0x3F
        val shamt = imm and shamtMask

        val mnemonic = when (funct3) {
            0 -> "addi$suffix"
            1 -> "slli$suffix"
            2 -> if (!isWord) "slti" else "unknown"
            3 -> if (!isWord) "sltiu" else "unknown"
            4 -> if (!isWord) "xori" else "unknown"
            5 -> if ((imm and 0x400) != 0) "srai$suffix" else "srli$suffix"
            6 -> if (!isWord) "ori" else "unknown"
            7 -> if (!isWord) "andi" else "unknown"
            else -> "unknown"
        }

        // Pseudo-instructions
        if (!isWord && funct3 == 0 && (insn shr 15) and 0x1F == 0 && (insn shr 7) and 0x1F == 0 && imm == 0) {
            return DisassembledInsn(0, insn, "nop", "")
        }
        if (!isWord && funct3 == 0 && imm == 0) {
            return DisassembledInsn(0, insn, "mv", "$rd, $rs1")
        }
        if (!isWord && funct3 == 0 && (insn shr 15) and 0x1F == 0) {
            return DisassembledInsn(0, insn, "li", "$rd, $imm")
        }
        if (!isWord && funct3 == 4 && imm == -1) {
            return DisassembledInsn(0, insn, "not", "$rd, $rs1")
        }
        if (!isWord && funct3 == 3 && imm == 1) {
            return DisassembledInsn(0, insn, "seqz", "$rd, $rs1")
        }

        return when (funct3) {
            1, 5 -> DisassembledInsn(0, insn, mnemonic, "$rd, $rs1, $shamt")
            else -> DisassembledInsn(0, insn, mnemonic, "$rd, $rs1, $imm")
        }
    }

    private fun decodeLoad(insn: Int): DisassembledInsn {
        val rd = gp((insn shr 7) and 0x1F)
        val rs1 = gp((insn shr 15) and 0x1F)
        val imm = signExtend(insn ushr 20, 12)
        val funct3 = (insn shr 12) and 0x7
        val mnemonic = when (funct3) {
            0 -> "lb"; 1 -> "lh"; 2 -> "lw"; 3 -> "ld"
            4 -> "lbu"; 5 -> "lhu"; 6 -> "lwu"
            else -> "unknown"
        }
        return DisassembledInsn(0, insn, mnemonic, "$rd, $imm($rs1)")
    }

    private fun decodeStore(insn: Int): DisassembledInsn {
        val rs1 = gp((insn shr 15) and 0x1F)
        val rs2 = gp((insn shr 20) and 0x1F)
        val imm11_5 = (insn ushr 25) and 0x7F
        val imm4_0 = (insn shr 7) and 0x1F
        val imm = signExtend((imm11_5 shl 5) or imm4_0, 12)
        val funct3 = (insn shr 12) and 0x7
        val mnemonic = when (funct3) {
            0 -> "sb"; 1 -> "sh"; 2 -> "sw"; 3 -> "sd"
            else -> "unknown"
        }
        return DisassembledInsn(0, insn, mnemonic, "$rs2, $imm($rs1)")
    }

    private fun decodeBranch(insn: Int, addr: Long): DisassembledInsn {
        val rs1 = gp((insn shr 15) and 0x1F)
        val rs2 = gp((insn shr 20) and 0x1F)
        val bit12 = (insn ushr 31) and 1
        val bits10_5 = (insn ushr 25) and 0x3F
        val bits4_1 = (insn shr 8) and 0xF
        val bit11 = (insn shr 7) and 1
        val imm = signExtend((bit12 shl 12) or (bit11 shl 11) or (bits10_5 shl 5) or (bits4_1 shl 1), 13)
        val target = addr + imm

        val funct3 = (insn shr 12) and 0x7
        val mnemonic = when (funct3) {
            0 -> "beq"; 1 -> "bne"; 4 -> "blt"; 5 -> "bge"; 6 -> "bltu"; 7 -> "bgeu"
            else -> "unknown"
        }

        // Pseudo: beqz/bnez
        if (funct3 == 0 && (insn shr 20) and 0x1F == 0) {
            return DisassembledInsn(0, insn, "beqz", "$rs1, 0x${target.toULong().toString(16)}")
        }
        if (funct3 == 1 && (insn shr 20) and 0x1F == 0) {
            return DisassembledInsn(0, insn, "bnez", "$rs1, 0x${target.toULong().toString(16)}")
        }

        return DisassembledInsn(0, insn, mnemonic, "$rs1, $rs2, 0x${target.toULong().toString(16)}")
    }

    private fun decodeLui(insn: Int): DisassembledInsn {
        val rd = gp((insn shr 7) and 0x1F)
        val imm = insn ushr 12
        return DisassembledInsn(0, insn, "lui", "$rd, 0x${imm.toString(16)}")
    }

    private fun decodeAuipc(insn: Int): DisassembledInsn {
        val rd = gp((insn shr 7) and 0x1F)
        val imm = insn ushr 12
        return DisassembledInsn(0, insn, "auipc", "$rd, 0x${imm.toString(16)}")
    }

    private fun decodeJal(insn: Int, addr: Long): DisassembledInsn {
        val rd = (insn shr 7) and 0x1F
        val bit20 = (insn ushr 31) and 1
        val bits10_1 = (insn ushr 21) and 0x3FF
        val bit11 = (insn ushr 20) and 1
        val bits19_12 = (insn ushr 12) and 0xFF
        val imm = signExtend(
            (bit20 shl 20) or (bits19_12 shl 12) or (bit11 shl 11) or (bits10_1 shl 1), 21)
        val target = addr + imm

        // Pseudo: j = jal x0, offset
        if (rd == 0) {
            return DisassembledInsn(0, insn, "j", "0x${target.toULong().toString(16)}")
        }
        // Pseudo: call is typically jal ra, offset but we keep as jal
        return DisassembledInsn(0, insn, "jal", "${gp(rd)}, 0x${target.toULong().toString(16)}")
    }

    private fun decodeJalr(insn: Int): DisassembledInsn {
        val rd = (insn shr 7) and 0x1F
        val rs1 = (insn shr 15) and 0x1F
        val imm = signExtend(insn ushr 20, 12)

        // Pseudo: ret = jalr x0, ra, 0
        if (rd == 0 && rs1 == 1 && imm == 0) {
            return DisassembledInsn(0, insn, "ret", "")
        }
        // Pseudo: jr rs1 = jalr x0, rs1, 0
        if (rd == 0 && imm == 0) {
            return DisassembledInsn(0, insn, "jr", gp(rs1))
        }

        return DisassembledInsn(0, insn, "jalr", "${gp(rd)}, ${gp(rs1)}, $imm")
    }

    private fun decodeSystem(insn: Int): DisassembledInsn {
        return when (insn) {
            0x00000073 -> DisassembledInsn(0, insn, "ecall", "")
            0x00100073 -> DisassembledInsn(0, insn, "ebreak", "")
            else -> DisassembledInsn(0, insn, "system", "0x${insn.toUInt().toString(16)}")
        }
    }

    private fun decodeFpLoad(insn: Int): DisassembledInsn {
        val rd = fp((insn shr 7) and 0x1F)
        val rs1 = gp((insn shr 15) and 0x1F)
        val imm = signExtend(insn ushr 20, 12)
        val funct3 = (insn shr 12) and 0x7
        val mnemonic = when (funct3) {
            2 -> "flw"
            3 -> "fld"
            else -> "unknown"
        }
        return DisassembledInsn(0, insn, mnemonic, "$rd, $imm($rs1)")
    }

    private fun decodeFpStore(insn: Int): DisassembledInsn {
        val rs1 = gp((insn shr 15) and 0x1F)
        val rs2 = fp((insn shr 20) and 0x1F)
        val imm11_5 = (insn ushr 25) and 0x7F
        val imm4_0 = (insn shr 7) and 0x1F
        val imm = signExtend((imm11_5 shl 5) or imm4_0, 12)
        val funct3 = (insn shr 12) and 0x7
        val mnemonic = when (funct3) {
            2 -> "fsw"
            3 -> "fsd"
            else -> "unknown"
        }
        return DisassembledInsn(0, insn, mnemonic, "$rs2, $imm($rs1)")
    }

    private fun decodeFpOp(insn: Int): DisassembledInsn {
        val rd = (insn shr 7) and 0x1F
        val rs1 = (insn shr 15) and 0x1F
        val rs2 = (insn shr 20) and 0x1F
        val funct3 = (insn shr 12) and 0x7
        val funct5 = (insn ushr 27) and 0x1F

        val isDouble = when (funct5) {
            0x01, 0x05, 0x09, 0x0D, 0x11, 0x15, 0x21, 0x29, 0x2D, 0x31, 0x35, 0x39, 0x3D, 0x51, 0x61, 0x69, 0x71, 0x79 -> true
            else -> false
        }
        val suffix = if (isDouble) ".d" else ".s"

        return when (funct5) {
            0x00, 0x01 -> DisassembledInsn(0, insn, "fadd$suffix", "${fp(rd)}, ${fp(rs1)}, ${fp(rs2)}")
            0x04, 0x05 -> DisassembledInsn(0, insn, "fsub$suffix", "${fp(rd)}, ${fp(rs1)}, ${fp(rs2)}")
            0x08, 0x09 -> DisassembledInsn(0, insn, "fmul$suffix", "${fp(rd)}, ${fp(rs1)}, ${fp(rs2)}")
            0x0C, 0x0D -> DisassembledInsn(0, insn, "fdiv$suffix", "${fp(rd)}, ${fp(rs1)}, ${fp(rs2)}")
            0x2C, 0x2D -> DisassembledInsn(0, insn, "fsqrt$suffix", "${fp(rd)}, ${fp(rs1)}")
            0x10, 0x11 -> {
                val mn = when (funct3) {
                    0 -> "fsgnj$suffix"; 1 -> "fsgnjn$suffix"; 2 -> "fsgnjx$suffix"
                    else -> "unknown"
                }
                DisassembledInsn(0, insn, mn, "${fp(rd)}, ${fp(rs1)}, ${fp(rs2)}")
            }
            0x14, 0x15 -> {
                val mn = when (funct3) { 0 -> "fmin$suffix"; 1 -> "fmax$suffix"; else -> "unknown" }
                DisassembledInsn(0, insn, mn, "${fp(rd)}, ${fp(rs1)}, ${fp(rs2)}")
            }
            0x50, 0x51 -> {
                val mn = when (funct3) { 2 -> "feq$suffix"; 1 -> "flt$suffix"; 0 -> "fle$suffix"; else -> "unknown" }
                DisassembledInsn(0, insn, mn, "${gp(rd)}, ${fp(rs1)}, ${fp(rs2)}")
            }
            0x20 -> DisassembledInsn(0, insn, "fcvt.s.d", "${fp(rd)}, ${fp(rs1)}")
            0x21 -> DisassembledInsn(0, insn, "fcvt.d.s", "${fp(rd)}, ${fp(rs1)}")
            0x60, 0x61 -> {
                val fmt = if (funct5 == 0x60) "s" else "d"
                val cvt = when (rs2) { 0 -> "w"; 1 -> "wu"; 2 -> "l"; 3 -> "lu"; else -> "?" }
                DisassembledInsn(0, insn, "fcvt.$cvt.$fmt", "${gp(rd)}, ${fp(rs1)}")
            }
            0x68, 0x69 -> {
                val fmt = if (funct5 == 0x68) "s" else "d"
                val cvt = when (rs2) { 0 -> "w"; 1 -> "wu"; 2 -> "l"; 3 -> "lu"; else -> "?" }
                DisassembledInsn(0, insn, "fcvt.$fmt.$cvt", "${fp(rd)}, ${gp(rs1)}")
            }
            0x70 -> {
                if (funct3 == 0) DisassembledInsn(0, insn, "fmv.x.w", "${gp(rd)}, ${fp(rs1)}")
                else DisassembledInsn(0, insn, "fclass.s", "${gp(rd)}, ${fp(rs1)}")
            }
            0x71 -> {
                if (funct3 == 0) DisassembledInsn(0, insn, "fmv.x.d", "${gp(rd)}, ${fp(rs1)}")
                else DisassembledInsn(0, insn, "fclass.d", "${gp(rd)}, ${fp(rs1)}")
            }
            0x78 -> DisassembledInsn(0, insn, "fmv.w.x", "${fp(rd)}, ${gp(rs1)}")
            0x79 -> DisassembledInsn(0, insn, "fmv.d.x", "${fp(rd)}, ${gp(rs1)}")
            else -> DisassembledInsn(0, insn, "fp.unknown", "0x${insn.toUInt().toString(16)}")
        }
    }

    private fun decodeAtomic(insn: Int): DisassembledInsn {
        val rd = gp((insn shr 7) and 0x1F)
        val rs1 = gp((insn shr 15) and 0x1F)
        val rs2 = gp((insn shr 20) and 0x1F)
        val funct3 = (insn shr 12) and 0x7
        val funct5 = (insn ushr 27) and 0x1F
        val aq = (insn shr 26) and 1
        val rl = (insn shr 25) and 1

        val width = when (funct3) { 2 -> ".w"; 3 -> ".d"; else -> "" }
        val ordering = buildString {
            if (aq == 1) append(".aq")
            if (rl == 1) append(".rl")
        }

        val mnemonic = when (funct5) {
            0x02 -> "lr$width$ordering"
            0x03 -> "sc$width$ordering"
            0x01 -> "amoswap$width$ordering"
            0x00 -> "amoadd$width$ordering"
            0x04 -> "amoxor$width$ordering"
            0x0C -> "amoand$width$ordering"
            0x08 -> "amoor$width$ordering"
            0x10 -> "amomin$width$ordering"
            0x14 -> "amomax$width$ordering"
            0x18 -> "amominu$width$ordering"
            0x1C -> "amomaxu$width$ordering"
            else -> "atomic.unknown"
        }

        return if (funct5 == 0x02) {
            DisassembledInsn(0, insn, mnemonic, "$rd, ($rs1)")
        } else {
            DisassembledInsn(0, insn, mnemonic, "$rd, $rs2, ($rs1)")
        }
    }

    private fun decodeCompressed(insn: Int, addr: Long): DisassembledInsn {
        val op = insn and 0x3
        val funct3 = (insn shr 13) and 0x7

        val result = when (op) {
            0x0 -> decodeCompressedQ0(insn, funct3)
            0x1 -> decodeCompressedQ1(insn, funct3, addr)
            0x2 -> decodeCompressedQ2(insn, funct3)
            else -> DisassembledInsn(0, insn, "c.unknown", "0x${insn.toString(16)}")
        }
        return result.copy(address = addr, compressed = true)
    }

    private fun decodeCompressedQ0(insn: Int, funct3: Int): DisassembledInsn {
        val rd3 = gp(((insn shr 2) and 0x7) + 8)
        val rs13 = gp(((insn shr 7) and 0x7) + 8)
        return when (funct3) {
            0 -> DisassembledInsn(0, insn, "c.addi4spn", "$rd3, sp, ...")
            2 -> DisassembledInsn(0, insn, "c.lw", "$rd3, ...($rs13)")
            3 -> DisassembledInsn(0, insn, "c.ld", "$rd3, ...($rs13)")
            6 -> DisassembledInsn(0, insn, "c.sw", "$rd3, ...($rs13)")
            7 -> DisassembledInsn(0, insn, "c.sd", "$rd3, ...($rs13)")
            else -> DisassembledInsn(0, insn, "c.unknown", "0x${insn.toString(16)}")
        }
    }

    private fun decodeCompressedQ1(insn: Int, funct3: Int, addr: Long): DisassembledInsn {
        val rd = gp((insn shr 7) and 0x1F)
        val rd3 = gp(((insn shr 7) and 0x7) + 8)
        val imm5 = (insn shr 12) and 1
        val imm4_0 = (insn shr 2) and 0x1F
        val imm = signExtend((imm5 shl 5) or imm4_0, 6)

        return when (funct3) {
            0 -> {
                if ((insn shr 7) and 0x1F == 0 && imm == 0) DisassembledInsn(0, insn, "c.nop", "")
                else DisassembledInsn(0, insn, "c.addi", "$rd, $imm")
            }
            1 -> DisassembledInsn(0, insn, "c.addiw", "$rd, $imm")
            2 -> DisassembledInsn(0, insn, "c.li", "$rd, $imm")
            3 -> {
                if ((insn shr 7) and 0x1F == 2) DisassembledInsn(0, insn, "c.addi16sp", "$imm")
                else DisassembledInsn(0, insn, "c.lui", "$rd, $imm")
            }
            4 -> {
                val funct2 = (insn shr 10) and 0x3
                val rs23 = gp(((insn shr 2) and 0x7) + 8)
                when (funct2) {
                    0 -> DisassembledInsn(0, insn, "c.srli", "$rd3, ${imm and 0x3F}")
                    1 -> DisassembledInsn(0, insn, "c.srai", "$rd3, ${imm and 0x3F}")
                    2 -> DisassembledInsn(0, insn, "c.andi", "$rd3, $imm")
                    3 -> {
                        val funct1 = (insn shr 12) and 1
                        val funct2b = (insn shr 5) and 0x3
                        val mn = when {
                            funct1 == 0 && funct2b == 0 -> "c.sub"
                            funct1 == 0 && funct2b == 1 -> "c.xor"
                            funct1 == 0 && funct2b == 2 -> "c.or"
                            funct1 == 0 && funct2b == 3 -> "c.and"
                            funct1 == 1 && funct2b == 0 -> "c.subw"
                            funct1 == 1 && funct2b == 1 -> "c.addw"
                            else -> "c.unknown"
                        }
                        DisassembledInsn(0, insn, mn, "$rd3, $rs23")
                    }
                    else -> DisassembledInsn(0, insn, "c.unknown", "")
                }
            }
            5 -> DisassembledInsn(0, insn, "c.j", "...")
            6 -> DisassembledInsn(0, insn, "c.beqz", "$rd3, ...")
            7 -> DisassembledInsn(0, insn, "c.bnez", "$rd3, ...")
            else -> DisassembledInsn(0, insn, "c.unknown", "0x${insn.toString(16)}")
        }
    }

    private fun decodeCompressedQ2(insn: Int, funct3: Int): DisassembledInsn {
        val rd = gp((insn shr 7) and 0x1F)
        val rs2 = gp((insn shr 2) and 0x1F)

        return when (funct3) {
            0 -> DisassembledInsn(0, insn, "c.slli", "$rd, ...")
            2 -> DisassembledInsn(0, insn, "c.lwsp", "$rd, ...")
            3 -> DisassembledInsn(0, insn, "c.ldsp", "$rd, ...")
            4 -> {
                val bit12 = (insn shr 12) and 1
                val rs2Enc = (insn shr 2) and 0x1F
                if (bit12 == 0) {
                    if (rs2Enc == 0) DisassembledInsn(0, insn, "c.jr", rd)
                    else DisassembledInsn(0, insn, "c.mv", "$rd, $rs2")
                } else {
                    if (rs2Enc == 0) {
                        if ((insn shr 7) and 0x1F == 0) DisassembledInsn(0, insn, "c.ebreak", "")
                        else DisassembledInsn(0, insn, "c.jalr", rd)
                    } else {
                        DisassembledInsn(0, insn, "c.add", "$rd, $rs2")
                    }
                }
            }
            6 -> DisassembledInsn(0, insn, "c.swsp", "$rs2, ...")
            7 -> DisassembledInsn(0, insn, "c.sdsp", "$rs2, ...")
            else -> DisassembledInsn(0, insn, "c.unknown", "0x${insn.toString(16)}")
        }
    }

    private fun gp(enc: Int): String = RiscVRegisters.gpRegs[enc].name
    private fun fp(enc: Int): String = RiscVRegisters.fpRegs[enc].name

    private fun signExtend(value: Int, bits: Int): Int {
        val shift = 32 - bits
        return (value shl shift) shr shift
    }

    companion object {
        private fun readU32(bytes: ByteArray, off: Int): Int =
            (bytes[off].toInt() and 0xFF) or
            ((bytes[off + 1].toInt() and 0xFF) shl 8) or
            ((bytes[off + 2].toInt() and 0xFF) shl 16) or
            ((bytes[off + 3].toInt() and 0xFF) shl 24)
    }
}
