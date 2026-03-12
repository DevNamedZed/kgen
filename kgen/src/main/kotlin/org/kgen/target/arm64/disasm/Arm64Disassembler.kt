package org.kgen.target.arm64.disasm

import org.kgen.target.arm64.Arm64Condition
import org.kgen.target.arm64.Arm64Register
import org.kgen.target.arm64.VectorArrangement

/**
 * ARM64 (AArch64) disassembler. Decodes fixed-width 32-bit little-endian instructions.
 * Covers the core integer instruction set used by the assembler.
 */
class Arm64Disassembler {

    fun disassemble(code: ByteArray, baseAddress: Long = 0): List<Arm64Instruction> {
        val result = mutableListOf<Arm64Instruction>()
        var pos = 0
        while (pos + 4 <= code.size) {
            val inst = readLE32(code, pos)
            result.add(decode(inst, pos, baseAddress))
            pos += 4
        }
        return result
    }

    private fun decode(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val op0 = (inst ushr 25) and 0xF

        return when {
            // Data processing - immediate
            inst and 0x1F000000 == 0x11000000.toInt() -> decodeAddSubImm(inst, offset, baseAddress)
            // Data processing 3-source (MADD/MSUB/MUL) — must be before add/sub reg
            inst and 0x1F000000 == 0x1B000000 -> decodeDataProc3Src(inst, offset, baseAddress)
            // Data processing 2-source (shifts, div)
            inst and 0x5FE00000 == 0x1AC00000 -> decodeDataProc2Src(inst, offset, baseAddress)
            // Data processing - register
            inst and 0x0F000000 == 0x0B000000 -> decodeAddSubReg(inst, offset, baseAddress)
            // Logical shifted register
            inst and 0x1F000000 == 0x0A000000 -> decodeLogicalReg(inst, offset, baseAddress)
            // Move wide
            inst and 0x1F800000 == 0x12800000 -> decodeMoveWide(inst, offset, baseAddress)
            // Bitfield (SBFM/UBFM — SXTW, SXTB, SXTH, UXTB, UXTH)
            inst and 0x1F800000 == 0x13000000 -> decodeBitfield(inst, offset, baseAddress)
            // Unconditional branch (B, BL)
            inst and 0x7C000000 == 0x14000000 -> decodeUncondBranch(inst, offset, baseAddress)
            // Conditional branch (B.cond)
            inst and 0xFF000010.toInt() == 0x54000000 -> decodeCondBranch(inst, offset, baseAddress)
            // Compare and branch (CBZ, CBNZ)
            inst and 0x7E000000 == 0x34000000 -> decodeCompareAndBranch(inst, offset, baseAddress)
            // Unconditional branch register (BR, BLR, RET)
            inst and 0xFE000000.toInt() == 0xD6000000.toInt() -> decodeBranchReg(inst, offset, baseAddress)
            // Load/Store unsigned offset
            inst and 0x3B000000 == 0x39000000 -> decodeLdStUnsigned(inst, offset, baseAddress)
            // Load/Store unscaled
            inst and 0x3B200C00 == 0x38000000 -> decodeLdStUnscaled(inst, offset, baseAddress)
            // Load/Store pair
            inst and 0x3A000000 == 0x28000000 -> decodeLdStPair(inst, offset, baseAddress)
            // Conditional select
            inst and 0x1FE00000 == 0x1A800000 -> decodeCondSelect(inst, offset, baseAddress)
            // System (NOP, BRK, SVC)
            inst == 0xD503201F.toInt() -> Arm64Instruction(baseAddress + offset.toLong(), "nop", "", inst)
            inst and 0xFFE0001F.toInt() == 0xD4200000.toInt() -> {
                val imm = (inst ushr 5) and 0xFFFF
                Arm64Instruction(baseAddress + offset.toLong(), "brk", "#$imm", inst)
            }
            inst and 0xFFE0001F.toInt() == 0xD4000001.toInt() -> {
                val imm = (inst ushr 5) and 0xFFFF
                Arm64Instruction(baseAddress + offset.toLong(), "svc", "#$imm", inst)
            }
            // FP data processing (2-source): FADD, FSUB, FMUL, FDIV
            inst and 0x5F200C00 == 0x1E200800 -> decodeFpDataProc2(inst, offset, baseAddress)
            // FP data processing (1-source): FMOV, FNEG, FABS, FCVT
            inst and 0x5F207C00 == 0x1E204000 -> decodeFpDataProc1(inst, offset, baseAddress)
            // FP compare
            inst and 0x5F203C00 == 0x1E202000 -> decodeFpCompare(inst, offset, baseAddress)
            // FP conditional select
            inst and 0x5F200C00 == 0x1E200C00 -> decodeFpCondSelect(inst, offset, baseAddress)
            // FP <-> GP transfer: FMOV, SCVTF, UCVTF, FCVTZS, FCVTZU
            inst and 0x5F200000 == 0x1E200000 -> decodeFpFixedConv(inst, offset, baseAddress)
            // FP load/store unscaled (LDUR/STUR for D/S)
            inst and 0x3F200C00 == 0x3C000000 -> decodeFpLdStUnscaled(inst, offset, baseAddress)
            // FP load/store pair
            inst and 0x3E000000 == 0x2C000000 -> decodeFpLdStPair(inst, offset, baseAddress)
            // NEON shift immediate: 0_Q_U_011110_immh_immb_opcode_1_Rn_Rd
            inst and 0x9F800400.toInt() == 0x0F000400 -> decodeNeonShiftImm(inst, offset, baseAddress)
            // NEON DUP from GP: 0_Q_0_01110000_imm5_0_0011_1_Rn_Rd
            inst and 0xBFE0FC00.toInt() == 0x0E000C00 -> decodeNeonDupGp(inst, offset, baseAddress)
            // NEON DUP element: 0_Q_0_01110000_imm5_0_0000_1_Rn_Rd
            inst and 0xBFE0FC00.toInt() == 0x0E000400 -> decodeNeonDupElement(inst, offset, baseAddress)
            // NEON across lanes: 0_Q_U_01110_size_11000_opcode_10_Rn_Rd
            inst and 0x9F3E0C00.toInt() == 0x0E300800 -> decodeNeonAcross(inst, offset, baseAddress)
            // NEON NOT: 0_Q_1_01110_00_10000_00101_10_Rn_Rd
            inst and 0xBFFFFC00.toInt() == 0x2E205800 -> decodeNeonNot(inst, offset, baseAddress)
            // NEON 3-register same (all: integer, FP, logical)
            // bit[31]=0, bits[28:24]=01110, bit[21]=1, bit[10]=1
            inst and 0x9F200400.toInt() == 0x0E200400 -> decodeNeon3Same(inst, offset, baseAddress)
            // SIMD LDR/STR Q (128-bit load/store)
            inst and 0x3F000000 == 0x3D000000 && (inst ushr 30) and 3 == 0 -> decodeLdStQ(inst, offset, baseAddress)
            // ADR
            inst and 0x9F000000.toInt() == 0x10000000 -> {
                val rd = inst and 0x1F
                val immhi = (inst ushr 5) and 0x7FFFF
                val immlo = (inst ushr 29) and 0x3
                val imm = (immhi shl 2) or immlo
                val sf = if ((inst ushr 31) and 1 == 1) "adrp" else "adr"
                Arm64Instruction(baseAddress + offset.toLong(), sf, xReg(rd, true) + ", #$imm", inst)
            }
            else -> Arm64Instruction(baseAddress + offset.toLong(), ".word", "0x${inst.toUInt().toString(16)}", inst)
        }
    }

    private fun decodeAddSubImm(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val sf = (inst ushr 31) and 1
        val op = (inst ushr 30) and 1
        val s = (inst ushr 29) and 1
        val shift = (inst ushr 22) and 3
        val imm12 = (inst ushr 10) and 0xFFF
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F
        val is64 = sf == 1
        val imm = if (shift == 1) imm12 shl 12 else imm12

        val mnemonic = when {
            op == 0 && s == 0 -> "add"
            op == 0 && s == 1 -> if (rd == 31) "cmn" else "adds"
            op == 1 && s == 0 -> "sub"
            op == 1 && s == 1 -> if (rd == 31) "cmp" else "subs"
            else -> "?"
        }

        val operands = if (mnemonic == "cmp" || mnemonic == "cmn") {
            "${xReg(rn, is64)}, #$imm"
        } else {
            "${xReg(rd, is64)}, ${xReg(rn, is64)}, #$imm"
        }

        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, operands, inst)
    }

    private fun decodeAddSubReg(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val sf = (inst ushr 31) and 1
        val op = (inst ushr 30) and 1
        val s = (inst ushr 29) and 1
        val rm = (inst ushr 16) and 0x1F
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F
        val is64 = sf == 1

        val mnemonic = when {
            op == 0 && s == 0 -> if (rn == 31) "mov" else "add"
            op == 0 && s == 1 -> if (rd == 31) "cmn" else "adds"
            op == 1 && s == 0 -> if (rn == 31) "neg" else "sub"
            op == 1 && s == 1 -> if (rd == 31) "cmp" else "subs"
            else -> "?"
        }

        val operands = when (mnemonic) {
            "mov", "neg" -> "${xReg(rd, is64)}, ${xReg(rm, is64)}"
            "cmp", "cmn" -> "${xReg(rn, is64)}, ${xReg(rm, is64)}"
            else -> "${xReg(rd, is64)}, ${xReg(rn, is64)}, ${xReg(rm, is64)}"
        }

        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, operands, inst)
    }

    private fun decodeLogicalReg(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val sf = (inst ushr 31) and 1
        val opc = (inst ushr 29) and 3
        val n = (inst ushr 21) and 1
        val rm = (inst ushr 16) and 0x1F
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F
        val is64 = sf == 1

        val mnemonic = when {
            opc == 0 && n == 0 -> if (rn == 31 && rd != 31) "mov" else "and"
            opc == 0 && n == 1 -> "bic"
            opc == 1 && n == 0 -> if (rn == 31) "mov" else "orr"
            opc == 1 && n == 1 -> "orn"
            opc == 2 && n == 0 -> "eor"
            opc == 2 && n == 1 -> "eon"
            opc == 3 && n == 0 -> if (rd == 31) "tst" else "ands"
            opc == 3 && n == 1 -> "bics"
            else -> "?"
        }

        val operands = when (mnemonic) {
            "mov" -> "${xReg(rd, is64)}, ${xReg(rm, is64)}"
            "tst" -> "${xReg(rn, is64)}, ${xReg(rm, is64)}"
            else -> "${xReg(rd, is64)}, ${xReg(rn, is64)}, ${xReg(rm, is64)}"
        }

        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, operands, inst)
    }

    private fun decodeDataProc2Src(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val sf = (inst ushr 31) and 1
        val rm = (inst ushr 16) and 0x1F
        val opcode = (inst ushr 10) and 0x3F
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F
        val is64 = sf == 1

        val mnemonic = when (opcode) {
            0x02 -> "udiv"
            0x03 -> "sdiv"
            0x08 -> "lsl"
            0x09 -> "lsr"
            0x0A -> "asr"
            else -> "dp2src_$opcode"
        }

        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "${xReg(rd, is64)}, ${xReg(rn, is64)}, ${xReg(rm, is64)}", inst)
    }

    private fun decodeDataProc3Src(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val sf = (inst ushr 31) and 1
        val rm = (inst ushr 16) and 0x1F
        val o0 = (inst ushr 15) and 1
        val ra = (inst ushr 10) and 0x1F
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F
        val is64 = sf == 1

        val mnemonic = if (o0 == 0) {
            if (ra == 31) "mul" else "madd"
        } else {
            "msub"
        }

        val operands = if (mnemonic == "mul") {
            "${xReg(rd, is64)}, ${xReg(rn, is64)}, ${xReg(rm, is64)}"
        } else {
            "${xReg(rd, is64)}, ${xReg(rn, is64)}, ${xReg(rm, is64)}, ${xReg(ra, is64)}"
        }

        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, operands, inst)
    }

    private fun decodeMoveWide(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val sf = (inst ushr 31) and 1
        val opc = (inst ushr 29) and 3
        val hw = (inst ushr 21) and 3
        val imm16 = (inst ushr 5) and 0xFFFF
        val rd = inst and 0x1F
        val is64 = sf == 1

        val mnemonic = when (opc) {
            0 -> "movn"
            2 -> "movz"
            3 -> "movk"
            else -> "mov_wide?"
        }

        val shift = hw * 16
        val shiftStr = if (shift > 0) ", lsl #$shift" else ""
        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "${xReg(rd, is64)}, #$imm16$shiftStr", inst)
    }

    private fun decodeBitfield(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val sf = (inst ushr 31) and 1
        val opc = (inst ushr 29) and 3
        val immr = (inst ushr 16) and 0x3F
        val imms = (inst ushr 10) and 0x3F
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F
        val is64 = sf == 1

        // Detect common aliases
        if (opc == 0 && sf == 1) {
            // SBFM 64-bit
            return when {
                immr == 0 && imms == 31 -> Arm64Instruction(baseAddress + offset.toLong(), "sxtw", "${xReg(rd, true)}, ${wReg(rn)}", inst)
                immr == 0 && imms == 15 -> Arm64Instruction(baseAddress + offset.toLong(), "sxth", "${xReg(rd, true)}, ${wReg(rn)}", inst)
                immr == 0 && imms == 7 -> Arm64Instruction(baseAddress + offset.toLong(), "sxtb", "${xReg(rd, true)}, ${wReg(rn)}", inst)
                else -> Arm64Instruction(baseAddress + offset.toLong(), "sbfm", "${xReg(rd, is64)}, ${xReg(rn, is64)}, #$immr, #$imms", inst)
            }
        }
        if (opc == 2 && sf == 0) {
            // UBFM 32-bit
            return when {
                immr == 0 && imms == 7 -> Arm64Instruction(baseAddress + offset.toLong(), "uxtb", "${wReg(rd)}, ${wReg(rn)}", inst)
                immr == 0 && imms == 15 -> Arm64Instruction(baseAddress + offset.toLong(), "uxth", "${wReg(rd)}, ${wReg(rn)}", inst)
                else -> Arm64Instruction(baseAddress + offset.toLong(), "ubfm", "${xReg(rd, is64)}, ${xReg(rn, is64)}, #$immr, #$imms", inst)
            }
        }

        val base = when (opc) { 0 -> "sbfm"; 1 -> "bfm"; 2 -> "ubfm"; else -> "bf?" }
        return Arm64Instruction(baseAddress + offset.toLong(), base, "${xReg(rd, is64)}, ${xReg(rn, is64)}, #$immr, #$imms", inst)
    }

    private fun decodeUncondBranch(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val op = (inst ushr 31) and 1
        val imm26 = inst and 0x03FFFFFF
        val sext = if (imm26 and 0x02000000 != 0) imm26 or 0xFC000000.toInt() else imm26
        val target = baseAddress + offset + sext.toLong() * 4
        val mnemonic = if (op == 0) "b" else "bl"
        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "0x${target.toString(16)}", inst)
    }

    private fun decodeCondBranch(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val cond = inst and 0xF
        val imm19 = (inst ushr 5) and 0x7FFFF
        val sext = if (imm19 and 0x40000 != 0) imm19 or 0xFFF80000.toInt() else imm19
        val target = baseAddress + offset + sext.toLong() * 4
        val condName = Arm64Condition.entries.firstOrNull { it.code == cond }?.name?.lowercase() ?: "$cond"
        return Arm64Instruction(baseAddress + offset.toLong(), "b.$condName", "0x${target.toString(16)}", inst)
    }

    private fun decodeCompareAndBranch(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val sf = (inst ushr 31) and 1
        val op = (inst ushr 24) and 1
        val imm19 = (inst ushr 5) and 0x7FFFF
        val rt = inst and 0x1F
        val sext = if (imm19 and 0x40000 != 0) imm19 or 0xFFF80000.toInt() else imm19
        val target = baseAddress + offset + sext.toLong() * 4
        val mnemonic = if (op == 0) "cbz" else "cbnz"
        val is64 = sf == 1
        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "${xReg(rt, is64)}, 0x${target.toString(16)}", inst)
    }

    private fun decodeBranchReg(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val opc = (inst ushr 21) and 3
        val rn = (inst ushr 5) and 0x1F
        val mnemonic = when (opc) {
            0 -> "br"
            1 -> "blr"
            2 -> "ret"
            else -> "br_reg?"
        }
        val operands = if (mnemonic == "ret" && rn == 30) "" else xReg(rn, true)
        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, operands, inst)
    }

    private fun decodeLdStUnsigned(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val size = (inst ushr 30) and 3
        val v = (inst ushr 26) and 1
        val opc = (inst ushr 22) and 3
        val imm12 = (inst ushr 10) and 0xFFF
        val rn = (inst ushr 5) and 0x1F
        val rt = inst and 0x1F
        val isLoad = opc and 1 == 1

        if (v == 1) return decodeFpLdStUnsigned(inst, offset, baseAddress, size, opc, imm12, rn, rt)

        val scale = size
        val scaledOffset = imm12 shl scale

        val (mnemonic, regStr) = when {
            size == 0 && isLoad -> "ldrb" to wReg(rt)
            size == 0 && !isLoad -> "strb" to wReg(rt)
            size == 1 && isLoad -> "ldrh" to wReg(rt)
            size == 1 && !isLoad -> "strh" to wReg(rt)
            size == 2 && isLoad -> "ldr" to wReg(rt)
            size == 2 && !isLoad -> "str" to wReg(rt)
            size == 3 && isLoad -> "ldr" to xReg(rt, true)
            size == 3 && !isLoad -> "str" to xReg(rt, true)
            else -> "ldst?" to "?"
        }

        val baseStr = if (rn == 31) "sp" else "x$rn"
        val offsetStr = if (scaledOffset == 0) "[$baseStr]" else "[$baseStr, #$scaledOffset]"
        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "$regStr, $offsetStr", inst)
    }

    private fun decodeLdStUnscaled(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val size = (inst ushr 30) and 3
        val opc = (inst ushr 22) and 3
        val imm9 = (inst ushr 12) and 0x1FF
        val sext = if (imm9 and 0x100 != 0) imm9 or 0xFFFFFE00.toInt() else imm9
        val rn = (inst ushr 5) and 0x1F
        val rt = inst and 0x1F
        val isLoad = opc and 1 == 1

        val (mnemonic, regStr) = when {
            size == 2 && isLoad -> "ldur" to wReg(rt)
            size == 2 && !isLoad -> "stur" to wReg(rt)
            size == 3 && isLoad -> "ldur" to xReg(rt, true)
            size == 3 && !isLoad -> "stur" to xReg(rt, true)
            else -> "ldur_$size" to "?"
        }

        val baseStr = if (rn == 31) "sp" else "x$rn"
        val offsetStr = if (sext == 0) "[$baseStr]" else "[$baseStr, #$sext]"
        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "$regStr, $offsetStr", inst)
    }

    private fun decodeLdStPair(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val opc = (inst ushr 30) and 3
        val category = (inst ushr 22) and 0x7
        val isLoad = category and 1 == 1
        val imm7 = (inst ushr 15) and 0x7F
        val rt2 = (inst ushr 10) and 0x1F
        val rn = (inst ushr 5) and 0x1F
        val rt1 = inst and 0x1F

        val is64 = opc and 2 != 0
        val scale = if (is64) 8 else 4
        val sext = if (imm7 and 0x40 != 0) imm7 or 0xFFFFFF80.toInt() else imm7
        val scaledOffset = sext * scale

        val indexMode = (inst ushr 23) and 3
        val mnemonic = if (isLoad) "ldp" else "stp"
        val baseStr = if (rn == 31) "sp" else "x$rn"

        val addrStr = when (indexMode) {
            1 -> if (scaledOffset == 0) "[$baseStr]" else "[$baseStr, #$scaledOffset]!" // pre-index
            2 -> if (scaledOffset == 0) "[$baseStr]" else "[$baseStr, #$scaledOffset]"  // signed offset
            3 -> "[$baseStr], #$scaledOffset" // post-index
            else -> "[$baseStr, #$scaledOffset]"
        }

        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "${xReg(rt1, is64)}, ${xReg(rt2, is64)}, $addrStr", inst)
    }

    private fun decodeCondSelect(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val sf = (inst ushr 31) and 1
        val op2 = (inst ushr 10) and 3
        val rm = (inst ushr 16) and 0x1F
        val cond = (inst ushr 12) and 0xF
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F
        val is64 = sf == 1

        val mnemonic = when (op2) {
            0 -> "csel"
            1 -> "csinc"
            else -> "csel_$op2"
        }

        val condName = Arm64Condition.entries.firstOrNull { it.code == cond }?.name?.lowercase() ?: "$cond"
        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "${xReg(rd, is64)}, ${xReg(rn, is64)}, ${xReg(rm, is64)}, $condName", inst)
    }

    private fun decodeFpDataProc2(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val ftype = (inst ushr 22) and 3
        val rm = (inst ushr 16) and 0x1F
        val opcode = (inst ushr 12) and 0xF
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F
        val isDouble = ftype == 1

        val mnemonic = when (opcode) {
            0 -> "fmul"
            1 -> "fdiv"
            2 -> "fadd"
            3 -> "fsub"
            else -> "fp2_$opcode"
        }

        val reg = if (isDouble) "d" else "s"
        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "$reg$rd, $reg$rn, $reg$rm", inst)
    }

    private fun decodeFpDataProc1(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val ftype = (inst ushr 22) and 3
        val opcode = (inst ushr 15) and 0x3F
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F
        val isDouble = ftype == 1

        val reg = if (isDouble) "d" else "s"
        val mnemonic = when (opcode) {
            0 -> "fmov"
            1 -> "fabs"
            2 -> "fneg"
            4, 5 -> return decodeFpConvert(inst, offset, baseAddress, ftype, rn, rd) // FCVT between S/D/H
            else -> "fp1_$opcode"
        }

        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "$reg$rd, $reg$rn", inst)
    }

    private fun decodeFpConvert(inst: Int, offset: Int, baseAddress: Long, ftype: Int, rn: Int, rd: Int): Arm64Instruction {
        val opc = (inst ushr 15) and 0x3F
        return when {
            ftype == 0 && opc == 5 -> Arm64Instruction(baseAddress + offset.toLong(), "fcvt", "d$rd, s$rn", inst) // S→D
            ftype == 1 && opc == 4 -> Arm64Instruction(baseAddress + offset.toLong(), "fcvt", "s$rd, d$rn", inst) // D→S
            else -> Arm64Instruction(baseAddress + offset.toLong(), "fcvt", "?", inst)
        }
    }

    private fun decodeFpCompare(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val ftype = (inst ushr 22) and 3
        val rm = (inst ushr 16) and 0x1F
        val rn = (inst ushr 5) and 0x1F
        val opc = inst and 0x1F
        val isDouble = ftype == 1
        val reg = if (isDouble) "d" else "s"

        return if (opc and 0x8 != 0) {
            Arm64Instruction(baseAddress + offset.toLong(), "fcmp", "$reg$rn, #0.0", inst)
        } else {
            Arm64Instruction(baseAddress + offset.toLong(), "fcmp", "$reg$rn, $reg$rm", inst)
        }
    }

    private fun decodeFpCondSelect(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val ftype = (inst ushr 22) and 3
        val rm = (inst ushr 16) and 0x1F
        val cond = (inst ushr 12) and 0xF
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F
        val isDouble = ftype == 1
        val reg = if (isDouble) "d" else "s"
        val condName = Arm64Condition.entries.firstOrNull { it.code == cond }?.name?.lowercase() ?: "$cond"
        return Arm64Instruction(baseAddress + offset.toLong(), "fcsel", "$reg$rd, $reg$rn, $reg$rm, $condName", inst)
    }

    private fun decodeFpFixedConv(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val sf = (inst ushr 31) and 1
        val ftype = (inst ushr 22) and 3
        val rmode = (inst ushr 19) and 3
        val opcode = (inst ushr 16) and 7
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F
        val is64Gp = sf == 1
        val isDouble = ftype == 1
        val gpReg = if (is64Gp) "x$rd" else "w$rd"
        val fpReg = if (isDouble) "d" else "s"

        return when {
            // FMOV GP→FP: sf=x, ftype=xx, rmode=00, opcode=111
            rmode == 0 && opcode == 7 -> Arm64Instruction(baseAddress + offset.toLong(), "fmov", "$fpReg$rd, ${if (is64Gp) "x$rn" else "w$rn"}", inst)
            // FMOV FP→GP: sf=x, ftype=xx, rmode=00, opcode=110
            rmode == 0 && opcode == 6 -> Arm64Instruction(baseAddress + offset.toLong(), "fmov", "${if (is64Gp) "x$rd" else "w$rd"}, $fpReg$rn", inst)
            // SCVTF: rmode=00, opcode=010
            rmode == 0 && opcode == 2 -> Arm64Instruction(baseAddress + offset.toLong(), "scvtf", "$fpReg$rd, ${if (is64Gp) "x$rn" else "w$rn"}", inst)
            // UCVTF: rmode=00, opcode=011
            rmode == 0 && opcode == 3 -> Arm64Instruction(baseAddress + offset.toLong(), "ucvtf", "$fpReg$rd, ${if (is64Gp) "x$rn" else "w$rn"}", inst)
            // FCVTZS: rmode=11, opcode=000
            rmode == 3 && opcode == 0 -> Arm64Instruction(baseAddress + offset.toLong(), "fcvtzs", "$gpReg, $fpReg$rn", inst)
            // FCVTZU: rmode=11, opcode=001
            rmode == 3 && opcode == 1 -> Arm64Instruction(baseAddress + offset.toLong(), "fcvtzu", "$gpReg, $fpReg$rn", inst)
            else -> Arm64Instruction(baseAddress + offset.toLong(), "fp_conv", "op=$opcode rmode=$rmode", inst)
        }
    }

    private fun decodeFpLdStUnsigned(inst: Int, offset: Int, baseAddress: Long, size: Int, opc: Int, imm12: Int, rn: Int, rt: Int): Arm64Instruction {
        val isLoad = opc and 1 == 1
        // Q (128-bit) loads/stores: size=0, opc=1x
        val isQ = size == 0 && opc >= 2
        val scale = if (isQ) 4 else size
        val scaledOffset = imm12 shl scale
        val (mnemonic, regStr) = when {
            isQ && isLoad -> "ldr" to "q$rt"
            isQ && !isLoad -> "str" to "q$rt"
            size == 2 && isLoad -> "ldr" to "s$rt"
            size == 2 && !isLoad -> "str" to "s$rt"
            size == 3 && isLoad -> "ldr" to "d$rt"
            size == 3 && !isLoad -> "str" to "d$rt"
            size == 0 && isLoad -> "ldr" to "b$rt"
            size == 0 && !isLoad -> "str" to "b$rt"
            size == 1 && isLoad -> "ldr" to "h$rt"
            size == 1 && !isLoad -> "str" to "h$rt"
            else -> "fp_ldst?" to "?"
        }
        val baseStr = if (rn == 31) "sp" else "x$rn"
        val offsetStr = if (scaledOffset == 0) "[$baseStr]" else "[$baseStr, #$scaledOffset]"
        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "$regStr, $offsetStr", inst)
    }

    private fun decodeFpLdStUnscaled(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val size = (inst ushr 30) and 3
        val opc = (inst ushr 22) and 3
        val imm9 = (inst ushr 12) and 0x1FF
        val sext = if (imm9 and 0x100 != 0) imm9 or 0xFFFFFE00.toInt() else imm9
        val rn = (inst ushr 5) and 0x1F
        val rt = inst and 0x1F
        val isLoad = opc and 1 == 1

        val (mnemonic, regStr) = when {
            size == 3 && isLoad -> "ldur" to "d$rt"
            size == 3 && !isLoad -> "stur" to "d$rt"
            size == 2 && isLoad -> "ldur" to "s$rt"
            size == 2 && !isLoad -> "stur" to "s$rt"
            else -> "fp_ldur_$size" to "?"
        }
        val baseStr = if (rn == 31) "sp" else "x$rn"
        val offsetStr = if (sext == 0) "[$baseStr]" else "[$baseStr, #$sext]"
        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "$regStr, $offsetStr", inst)
    }

    private fun decodeFpLdStPair(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val opc = (inst ushr 30) and 3
        val category = (inst ushr 22) and 0x7
        val isLoad = category and 1 == 1
        val imm7 = (inst ushr 15) and 0x7F
        val rt2 = (inst ushr 10) and 0x1F
        val rn = (inst ushr 5) and 0x1F
        val rt1 = inst and 0x1F

        val isDouble = opc == 1
        val scale = if (isDouble) 8 else 4
        val sext = if (imm7 and 0x40 != 0) imm7 or 0xFFFFFF80.toInt() else imm7
        val scaledOffset = sext * scale
        val reg = if (isDouble) "d" else "s"

        val indexMode = (inst ushr 23) and 3
        val mnemonic = if (isLoad) "ldp" else "stp"
        val baseStr = if (rn == 31) "sp" else "x$rn"

        val addrStr = when (indexMode) {
            1 -> if (scaledOffset == 0) "[$baseStr]" else "[$baseStr, #$scaledOffset]!"
            2 -> if (scaledOffset == 0) "[$baseStr]" else "[$baseStr, #$scaledOffset]"
            3 -> "[$baseStr], #$scaledOffset"
            else -> "[$baseStr, #$scaledOffset]"
        }

        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "$reg$rt1, $reg$rt2, $addrStr", inst)
    }

    private fun decodeNeon3Same(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val q = (inst ushr 30) and 1
        val u = (inst ushr 29) and 1
        val size = (inst ushr 22) and 3
        val rm = (inst ushr 16) and 0x1F
        val opcode = (inst ushr 11) and 0x1F
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F

        // Logical ops use size for op selection, operands are always .8b/.16b
        if (opcode == 0b00011) {
            val suffix = if (q == 1) "16b" else "8b"
            val mnemonic = when {
                u == 0 && size == 0 -> "and"
                u == 0 && size == 2 -> "orr"
                u == 1 && size == 0 -> "eor"
                else -> "logic_u${u}_sz${size}"
            }
            return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "v$rd.$suffix, v$rn.$suffix, v$rm.$suffix", inst)
        }

        // FP vector ops: opcode >= 0b11000
        // For FP, bit 22 = sz (0=single, 1=double), bit 23 differentiates op pairs (e.g., FADD vs FSUB)
        if (opcode >= 0b11000) {
            val sz = size and 1
            val sizeHi = (size ushr 1) and 1
            val fpArr = if (sz == 1) {
                if (q == 1) VectorArrangement.D2 else null
            } else {
                if (q == 1) VectorArrangement.S4 else VectorArrangement.S2
            }
            val suffix = fpArr?.suffix ?: "fp_q${q}_sz${sz}"
            val mnemonic = when {
                u == 0 && opcode == 0b11010 && sizeHi == 0 -> "fadd"
                u == 0 && opcode == 0b11010 && sizeHi == 1 -> "fsub"
                u == 1 && opcode == 0b11011 -> "fmul"
                u == 1 && opcode == 0b11111 -> "fdiv"
                u == 0 && opcode == 0b11110 && sizeHi == 0 -> "fmax"
                u == 0 && opcode == 0b11000 && sizeHi == 0 -> "fmaxnm"
                u == 0 && opcode == 0b11000 && sizeHi == 1 -> "fminnm"
                u == 0 && opcode == 0b11110 && sizeHi == 1 -> "fmin"
                else -> "fpvec_u${u}_op${opcode}_sz${sizeHi}"
            }
            return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "v$rd.$suffix, v$rn.$suffix, v$rm.$suffix", inst)
        }

        // Integer vector ops
        val arr = try { VectorArrangement.fromEncoding(q, size) } catch (_: Exception) { null }
        val suffix = arr?.suffix ?: "v_q${q}_sz${size}"

        val mnemonic = when {
            u == 0 && opcode == 0b10000 -> "add"
            u == 1 && opcode == 0b10000 -> "sub"
            u == 0 && opcode == 0b10011 -> "mul"
            u == 1 && opcode == 0b10001 -> "cmeq"
            u == 0 && opcode == 0b00110 -> "cmgt"
            u == 0 && opcode == 0b00111 -> "cmge"
            u == 1 && opcode == 0b00110 -> "cmhi"
            u == 1 && opcode == 0b00111 -> "cmhs"
            u == 0 && opcode == 0b10111 -> "addp"
            u == 0 && opcode == 0b00001 -> "smax"
            u == 1 && opcode == 0b00001 -> "umax"
            u == 0 && opcode == 0b00010 -> "smin"
            u == 1 && opcode == 0b00010 -> "umin"
            else -> "simd3_u${u}_op${opcode}"
        }

        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "v$rd.$suffix, v$rn.$suffix, v$rm.$suffix", inst)
    }

    private fun decodeNeonNot(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val q = (inst ushr 30) and 1
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F
        val suffix = if (q == 1) "16b" else "8b"
        return Arm64Instruction(baseAddress + offset.toLong(), "not", "v$rd.$suffix, v$rn.$suffix", inst)
    }

    private fun decodeNeonShiftImm(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val q = (inst ushr 30) and 1
        val u = (inst ushr 29) and 1
        val immh = (inst ushr 19) and 0xF
        val immb = (inst ushr 16) and 0x7
        val opcode = (inst ushr 11) and 0x1F
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F

        val (elemBits, shift) = when {
            immh and 0b1000 != 0 -> 64 to ((immh shl 3) or immb)
            immh and 0b0100 != 0 -> 32 to (((immh and 3) shl 3) or immb)
            immh and 0b0010 != 0 -> 16 to (((immh and 1) shl 3) or immb)
            immh and 0b0001 != 0 -> 8 to immb
            else -> 0 to 0
        }

        val size = when (elemBits) { 8 -> 0; 16 -> 1; 32 -> 2; 64 -> 3; else -> 0 }
        val arr = try { VectorArrangement.fromEncoding(q, size) } catch (_: Exception) { null }
        val suffix = arr?.suffix ?: "${q}_${size}"

        val mnemonic: String
        val shiftVal: Int
        if (u == 0 && opcode == 0b01010) {
            mnemonic = "shl"
            shiftVal = shift - elemBits
        } else if (u == 0 && opcode == 0b00000) {
            mnemonic = "sshr"
            shiftVal = 2 * elemBits - shift
        } else if (u == 1 && opcode == 0b00000) {
            mnemonic = "ushr"
            shiftVal = 2 * elemBits - shift
        } else {
            mnemonic = "vshift_u${u}_op${opcode}"
            shiftVal = shift
        }

        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "v$rd.$suffix, v$rn.$suffix, #$shiftVal", inst)
    }

    private fun decodeNeonAcross(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val q = (inst ushr 30) and 1
        val u = (inst ushr 29) and 1
        val size = (inst ushr 22) and 3
        val opcode = (inst ushr 12) and 0x1F
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F

        val arr = try { VectorArrangement.fromEncoding(q, size) } catch (_: Exception) { null }
        val suffix = arr?.suffix ?: "${q}_${size}"

        val scalarReg = when (size) {
            0 -> "b$rd"; 1 -> "h$rd"; 2 -> "s$rd"; 3 -> "d$rd"
            else -> "v$rd"
        }

        val mnemonic = when {
            u == 0 && opcode == 0b11011 -> "addv"
            else -> "across_u${u}_op${opcode}"
        }

        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "$scalarReg, v$rn.$suffix", inst)
    }

    private fun decodeNeonDupGp(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val q = (inst ushr 30) and 1
        val imm5 = (inst ushr 16) and 0x1F
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F

        val (elemBits, _) = when {
            imm5 and 1 == 1 -> 8 to 0
            imm5 and 2 == 2 -> 16 to 0
            imm5 and 4 == 4 -> 32 to 0
            imm5 and 8 == 8 -> 64 to 0
            else -> 0 to 0
        }
        val size = when (elemBits) { 8 -> 0; 16 -> 1; 32 -> 2; 64 -> 3; else -> 0 }
        val arr = try { VectorArrangement.fromEncoding(q, size) } catch (_: Exception) { null }
        val suffix = arr?.suffix ?: "${q}_${size}"
        val gpReg = if (elemBits == 64) "x$rn" else "w$rn"

        return Arm64Instruction(baseAddress + offset.toLong(), "dup", "v$rd.$suffix, $gpReg", inst)
    }

    private fun decodeNeonDupElement(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val q = (inst ushr 30) and 1
        val imm5 = (inst ushr 16) and 0x1F
        val rn = (inst ushr 5) and 0x1F
        val rd = inst and 0x1F

        val (elemBits, index) = when {
            imm5 and 1 == 1 -> 8 to (imm5 shr 1)
            imm5 and 2 == 2 -> 16 to (imm5 shr 2)
            imm5 and 4 == 4 -> 32 to (imm5 shr 3)
            imm5 and 8 == 8 -> 64 to (imm5 shr 4)
            else -> 0 to 0
        }
        val size = when (elemBits) { 8 -> 0; 16 -> 1; 32 -> 2; 64 -> 3; else -> 0 }
        val arr = try { VectorArrangement.fromEncoding(q, size) } catch (_: Exception) { null }
        val suffix = arr?.suffix ?: "${q}_${size}"
        val srcSuffix = when (elemBits) { 8 -> "b"; 16 -> "h"; 32 -> "s"; 64 -> "d"; else -> "?" }

        return Arm64Instruction(baseAddress + offset.toLong(), "dup", "v$rd.$suffix, v$rn.$srcSuffix[$index]", inst)
    }

    private fun decodeLdStQ(inst: Int, offset: Int, baseAddress: Long): Arm64Instruction {
        val opc = (inst ushr 22) and 3
        val imm12 = (inst ushr 10) and 0xFFF
        val rn = (inst ushr 5) and 0x1F
        val rt = inst and 0x1F
        val isLoad = opc and 1 == 1

        val scaledOffset = imm12 * 16
        val mnemonic = if (isLoad) "ldr" else "str"
        val baseStr = if (rn == 31) "sp" else "x$rn"
        val offsetStr = if (scaledOffset == 0) "[$baseStr]" else "[$baseStr, #$scaledOffset]"

        return Arm64Instruction(baseAddress + offset.toLong(), mnemonic, "q$rt, $offsetStr", inst)
    }

    private fun xReg(enc: Int, is64: Boolean): String = when {
        enc == 31 && is64 -> "xzr"
        enc == 31 -> "wzr"
        is64 -> "x$enc"
        else -> "w$enc"
    }

    private fun wReg(enc: Int): String = if (enc == 31) "wzr" else "w$enc"

    private fun readLE32(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xFF) or
        ((data[off + 1].toInt() and 0xFF) shl 8) or
        ((data[off + 2].toInt() and 0xFF) shl 16) or
        ((data[off + 3].toInt() and 0xFF) shl 24)
}
