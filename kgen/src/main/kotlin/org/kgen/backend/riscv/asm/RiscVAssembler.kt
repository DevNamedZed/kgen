package org.kgen.backend.riscv.asm

import org.kgen.backend.riscv.*
import java.io.ByteArrayOutputStream

/**
 * RISC-V assembler for RV64I + M extension.
 *
 * All instructions are 32-bit (4 bytes) in little-endian format.
 * Supports label-based branching with automatic fixup.
 */
class RiscVAssembler {
    private val buf = ByteArrayOutputStream()
    private val labels = mutableMapOf<String, Int>()
    private val fixups = mutableListOf<Fixup>()

    private data class Fixup(val offset: Int, val label: String, val kind: FixupKind)
    private enum class FixupKind { BRANCH, JAL }

    fun toByteArray(): ByteArray {
        applyFixups()
        return buf.toByteArray()
    }

    fun bytes(): ByteArray {
        applyFixups()
        return buf.toByteArray()
    }

    val size: Int get() = buf.size()

    fun label(name: String) { labels[name] = buf.size() }

    fun unresolvedLabels(): List<Pair<Int, String>> {
        return fixups.filter { it.label !in labels }.map { it.offset to it.label }
    }

    // --- RV64I Base Integer Instructions ---

    // R-type: arithmetic
    fun add(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x0, 0x00, rd, rs1, rs2)
    fun sub(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x0, 0x20, rd, rs1, rs2)
    fun sll(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x1, 0x00, rd, rs1, rs2)
    fun slt(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x2, 0x00, rd, rs1, rs2)
    fun sltu(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x3, 0x00, rd, rs1, rs2)
    fun xor(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x4, 0x00, rd, rs1, rs2)
    fun srl(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x5, 0x00, rd, rs1, rs2)
    fun sra(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x5, 0x20, rd, rs1, rs2)
    fun or(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x6, 0x00, rd, rs1, rs2)
    fun and(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x7, 0x00, rd, rs1, rs2)

    // R-type: 64-bit word ops (RV64I)
    fun addw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x3B, 0x0, 0x00, rd, rs1, rs2)
    fun subw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x3B, 0x0, 0x20, rd, rs1, rs2)
    fun sllw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x3B, 0x1, 0x00, rd, rs1, rs2)
    fun srlw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x3B, 0x5, 0x00, rd, rs1, rs2)
    fun sraw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x3B, 0x5, 0x20, rd, rs1, rs2)

    // I-type: arithmetic immediate
    fun addi(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) = emitI(0x13, 0x0, rd, rs1, imm)
    fun slti(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) = emitI(0x13, 0x2, rd, rs1, imm)
    fun sltiu(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) = emitI(0x13, 0x3, rd, rs1, imm)
    fun xori(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) = emitI(0x13, 0x4, rd, rs1, imm)
    fun ori(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) = emitI(0x13, 0x6, rd, rs1, imm)
    fun andi(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) = emitI(0x13, 0x7, rd, rs1, imm)

    // I-type: shift immediate (RV64I uses 6-bit shamt)
    fun slli(rd: RiscVGpReg, rs1: RiscVGpReg, shamt: Int) = emitI(0x13, 0x1, rd, rs1, shamt and 0x3F)
    fun srli(rd: RiscVGpReg, rs1: RiscVGpReg, shamt: Int) = emitI(0x13, 0x5, rd, rs1, shamt and 0x3F)
    fun srai(rd: RiscVGpReg, rs1: RiscVGpReg, shamt: Int) = emitI(0x13, 0x5, rd, rs1, (shamt and 0x3F) or 0x400)

    // I-type: 32-bit word immediate ops (RV64I)
    fun addiw(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) = emitI(0x1B, 0x0, rd, rs1, imm)
    fun slliw(rd: RiscVGpReg, rs1: RiscVGpReg, shamt: Int) = emitI(0x1B, 0x1, rd, rs1, shamt and 0x1F)
    fun srliw(rd: RiscVGpReg, rs1: RiscVGpReg, shamt: Int) = emitI(0x1B, 0x5, rd, rs1, shamt and 0x1F)
    fun sraiw(rd: RiscVGpReg, rs1: RiscVGpReg, shamt: Int) = emitI(0x1B, 0x5, rd, rs1, (shamt and 0x1F) or 0x400)

    // U-type: upper immediate
    fun lui(rd: RiscVGpReg, imm: Int) = emitU(0x37, rd, imm)
    fun auipc(rd: RiscVGpReg, imm: Int) = emitU(0x17, rd, imm)

    // Loads (I-type)
    fun lb(rd: RiscVGpReg, mem: RiscVMemory) = emitI(0x03, 0x0, rd, mem.base, mem.offset)
    fun lh(rd: RiscVGpReg, mem: RiscVMemory) = emitI(0x03, 0x1, rd, mem.base, mem.offset)
    fun lw(rd: RiscVGpReg, mem: RiscVMemory) = emitI(0x03, 0x2, rd, mem.base, mem.offset)
    fun ld(rd: RiscVGpReg, mem: RiscVMemory) = emitI(0x03, 0x3, rd, mem.base, mem.offset)
    fun lbu(rd: RiscVGpReg, mem: RiscVMemory) = emitI(0x03, 0x4, rd, mem.base, mem.offset)
    fun lhu(rd: RiscVGpReg, mem: RiscVMemory) = emitI(0x03, 0x5, rd, mem.base, mem.offset)
    fun lwu(rd: RiscVGpReg, mem: RiscVMemory) = emitI(0x03, 0x6, rd, mem.base, mem.offset)

    // Stores (S-type)
    fun sb(rs2: RiscVGpReg, mem: RiscVMemory) = emitS(0x23, 0x0, rs2, mem.base, mem.offset)
    fun sh(rs2: RiscVGpReg, mem: RiscVMemory) = emitS(0x23, 0x1, rs2, mem.base, mem.offset)
    fun sw(rs2: RiscVGpReg, mem: RiscVMemory) = emitS(0x23, 0x2, rs2, mem.base, mem.offset)
    fun sd(rs2: RiscVGpReg, mem: RiscVMemory) = emitS(0x23, 0x3, rs2, mem.base, mem.offset)

    // Branches (B-type) with immediate offset
    fun beq(rs1: RiscVGpReg, rs2: RiscVGpReg, offset: Int) = emitB(0x63, 0x0, rs1, rs2, offset)
    fun bne(rs1: RiscVGpReg, rs2: RiscVGpReg, offset: Int) = emitB(0x63, 0x1, rs1, rs2, offset)
    fun blt(rs1: RiscVGpReg, rs2: RiscVGpReg, offset: Int) = emitB(0x63, 0x4, rs1, rs2, offset)
    fun bge(rs1: RiscVGpReg, rs2: RiscVGpReg, offset: Int) = emitB(0x63, 0x5, rs1, rs2, offset)
    fun bltu(rs1: RiscVGpReg, rs2: RiscVGpReg, offset: Int) = emitB(0x63, 0x6, rs1, rs2, offset)
    fun bgeu(rs1: RiscVGpReg, rs2: RiscVGpReg, offset: Int) = emitB(0x63, 0x7, rs1, rs2, offset)

    // Branches with label
    fun beq(rs1: RiscVGpReg, rs2: RiscVGpReg, label: String) = emitBranchLabel(0x63, 0x0, rs1, rs2, label)
    fun bne(rs1: RiscVGpReg, rs2: RiscVGpReg, label: String) = emitBranchLabel(0x63, 0x1, rs1, rs2, label)
    fun blt(rs1: RiscVGpReg, rs2: RiscVGpReg, label: String) = emitBranchLabel(0x63, 0x4, rs1, rs2, label)
    fun bge(rs1: RiscVGpReg, rs2: RiscVGpReg, label: String) = emitBranchLabel(0x63, 0x5, rs1, rs2, label)
    fun bltu(rs1: RiscVGpReg, rs2: RiscVGpReg, label: String) = emitBranchLabel(0x63, 0x6, rs1, rs2, label)
    fun bgeu(rs1: RiscVGpReg, rs2: RiscVGpReg, label: String) = emitBranchLabel(0x63, 0x7, rs1, rs2, label)

    // Jump and link
    fun jal(rd: RiscVGpReg, offset: Int) = emitJ(0x6F, rd, offset)
    fun jal(rd: RiscVGpReg, label: String) { fixups.add(Fixup(buf.size(), label, FixupKind.JAL)); emitJ(0x6F, rd, 0) }
    fun jalr(rd: RiscVGpReg, rs1: RiscVGpReg, offset: Int) = emitI(0x67, 0x0, rd, rs1, offset)

    // --- M Extension (Multiply/Divide) ---
    fun mul(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x0, 0x01, rd, rs1, rs2)
    fun mulh(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x1, 0x01, rd, rs1, rs2)
    fun mulhsu(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x2, 0x01, rd, rs1, rs2)
    fun mulhu(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x3, 0x01, rd, rs1, rs2)
    fun div(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x4, 0x01, rd, rs1, rs2)
    fun divu(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x5, 0x01, rd, rs1, rs2)
    fun rem(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x6, 0x01, rd, rs1, rs2)
    fun remu(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x33, 0x7, 0x01, rd, rs1, rs2)

    // M extension: 32-bit word variants (RV64M)
    fun mulw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x3B, 0x0, 0x01, rd, rs1, rs2)
    fun divw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x3B, 0x4, 0x01, rd, rs1, rs2)
    fun divuw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x3B, 0x5, 0x01, rd, rs1, rs2)
    fun remw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x3B, 0x6, 0x01, rd, rs1, rs2)
    fun remuw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) = emitR(0x3B, 0x7, 0x01, rd, rs1, rs2)

    // --- F Extension: Single-Precision Floating-Point ---

    // Loads/Stores
    fun flw(rd: RiscVFpReg, mem: RiscVMemory) = emitI(0x07, 0x2, rd.encoding, mem.base, mem.offset)
    fun fsw(rs2: RiscVFpReg, mem: RiscVMemory) = emitS(0x27, 0x2, rs2.encoding, mem.base, mem.offset)

    // Arithmetic
    fun faddS(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg, rm: Int = 7) = emitR4(0x53, rm, 0x00, rd, rs1, rs2)
    fun fsubS(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg, rm: Int = 7) = emitR4(0x53, rm, 0x04, rd, rs1, rs2)
    fun fmulS(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg, rm: Int = 7) = emitR4(0x53, rm, 0x08, rd, rs1, rs2)
    fun fdivS(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg, rm: Int = 7) = emitR4(0x53, rm, 0x0C, rd, rs1, rs2)
    fun fsqrtS(rd: RiscVFpReg, rs1: RiscVFpReg, rm: Int = 7) = emitR4(0x53, rm, 0x2C, rd, rs1, RiscVFpReg0)

    // Sign injection
    fun fsgnjS(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitR4(0x53, 0x0, 0x10, rd, rs1, rs2)
    fun fsgnjnS(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitR4(0x53, 0x1, 0x10, rd, rs1, rs2)
    fun fsgnjxS(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitR4(0x53, 0x2, 0x10, rd, rs1, rs2)

    // Min/Max
    fun fminS(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitR4(0x53, 0x0, 0x14, rd, rs1, rs2)
    fun fmaxS(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitR4(0x53, 0x1, 0x14, rd, rs1, rs2)

    // Convert float<->int
    fun fcvtWS(rd: RiscVGpReg, rs1: RiscVFpReg, rm: Int = 7) = emitFpGp(0x53, rm, 0x60, rd, rs1, 0)
    fun fcvtWuS(rd: RiscVGpReg, rs1: RiscVFpReg, rm: Int = 7) = emitFpGp(0x53, rm, 0x60, rd, rs1, 1)
    fun fcvtLS(rd: RiscVGpReg, rs1: RiscVFpReg, rm: Int = 7) = emitFpGp(0x53, rm, 0x60, rd, rs1, 2)
    fun fcvtLuS(rd: RiscVGpReg, rs1: RiscVFpReg, rm: Int = 7) = emitFpGp(0x53, rm, 0x60, rd, rs1, 3)
    fun fcvtSW(rd: RiscVFpReg, rs1: RiscVGpReg, rm: Int = 7) = emitGpFp(0x53, rm, 0x68, rd, rs1, 0)
    fun fcvtSWu(rd: RiscVFpReg, rs1: RiscVGpReg, rm: Int = 7) = emitGpFp(0x53, rm, 0x68, rd, rs1, 1)
    fun fcvtSL(rd: RiscVFpReg, rs1: RiscVGpReg, rm: Int = 7) = emitGpFp(0x53, rm, 0x68, rd, rs1, 2)
    fun fcvtSLu(rd: RiscVFpReg, rs1: RiscVGpReg, rm: Int = 7) = emitGpFp(0x53, rm, 0x68, rd, rs1, 3)

    // Move float<->int
    fun fmvXW(rd: RiscVGpReg, rs1: RiscVFpReg) = emitFpGp(0x53, 0x0, 0x70, rd, rs1, 0)
    fun fmvWX(rd: RiscVFpReg, rs1: RiscVGpReg) = emitGpFp(0x53, 0x0, 0x78, rd, rs1, 0)

    // Compare
    fun feqS(rd: RiscVGpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitFpCmp(0x53, 0x2, 0x50, rd, rs1, rs2)
    fun fltS(rd: RiscVGpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitFpCmp(0x53, 0x1, 0x50, rd, rs1, rs2)
    fun fleS(rd: RiscVGpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitFpCmp(0x53, 0x0, 0x50, rd, rs1, rs2)

    // Classify
    fun fclassS(rd: RiscVGpReg, rs1: RiscVFpReg) = emitFpGp(0x53, 0x1, 0x70, rd, rs1, 0)

    // Pseudo-instructions
    fun fmvS(rd: RiscVFpReg, rs: RiscVFpReg) = fsgnjS(rd, rs, rs)
    fun fnegS(rd: RiscVFpReg, rs: RiscVFpReg) = fsgnjnS(rd, rs, rs)
    fun fabsS(rd: RiscVFpReg, rs: RiscVFpReg) = fsgnjxS(rd, rs, rs)

    // --- D Extension: Double-Precision Floating-Point ---

    // Loads/Stores
    fun fld(rd: RiscVFpReg, mem: RiscVMemory) = emitI(0x07, 0x3, rd.encoding, mem.base, mem.offset)
    fun fsd(rs2: RiscVFpReg, mem: RiscVMemory) = emitS(0x27, 0x3, rs2.encoding, mem.base, mem.offset)

    // Arithmetic
    fun faddD(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg, rm: Int = 7) = emitR4(0x53, rm, 0x01, rd, rs1, rs2)
    fun fsubD(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg, rm: Int = 7) = emitR4(0x53, rm, 0x05, rd, rs1, rs2)
    fun fmulD(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg, rm: Int = 7) = emitR4(0x53, rm, 0x09, rd, rs1, rs2)
    fun fdivD(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg, rm: Int = 7) = emitR4(0x53, rm, 0x0D, rd, rs1, rs2)
    fun fsqrtD(rd: RiscVFpReg, rs1: RiscVFpReg, rm: Int = 7) = emitR4(0x53, rm, 0x2D, rd, rs1, RiscVFpReg0)

    // Sign injection
    fun fsgnjD(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitR4(0x53, 0x0, 0x11, rd, rs1, rs2)
    fun fsgnjnD(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitR4(0x53, 0x1, 0x11, rd, rs1, rs2)
    fun fsgnjxD(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitR4(0x53, 0x2, 0x11, rd, rs1, rs2)

    // Min/Max
    fun fminD(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitR4(0x53, 0x0, 0x15, rd, rs1, rs2)
    fun fmaxD(rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitR4(0x53, 0x1, 0x15, rd, rs1, rs2)

    // Convert float<->float
    fun fcvtSD(rd: RiscVFpReg, rs1: RiscVFpReg, rm: Int = 7) = emitR4(0x53, rm, 0x20, rd, rs1, RiscVFpReg1)
    fun fcvtDS(rd: RiscVFpReg, rs1: RiscVFpReg, rm: Int = 7) = emitR4(0x53, rm, 0x21, rd, rs1, RiscVFpReg0)

    // Convert double<->int
    fun fcvtWD(rd: RiscVGpReg, rs1: RiscVFpReg, rm: Int = 7) = emitFpGp(0x53, rm, 0x61, rd, rs1, 0)
    fun fcvtWuD(rd: RiscVGpReg, rs1: RiscVFpReg, rm: Int = 7) = emitFpGp(0x53, rm, 0x61, rd, rs1, 1)
    fun fcvtLD(rd: RiscVGpReg, rs1: RiscVFpReg, rm: Int = 7) = emitFpGp(0x53, rm, 0x61, rd, rs1, 2)
    fun fcvtLuD(rd: RiscVGpReg, rs1: RiscVFpReg, rm: Int = 7) = emitFpGp(0x53, rm, 0x61, rd, rs1, 3)
    fun fcvtDW(rd: RiscVFpReg, rs1: RiscVGpReg, rm: Int = 7) = emitGpFp(0x53, rm, 0x69, rd, rs1, 0)
    fun fcvtDWu(rd: RiscVFpReg, rs1: RiscVGpReg, rm: Int = 7) = emitGpFp(0x53, rm, 0x69, rd, rs1, 1)
    fun fcvtDL(rd: RiscVFpReg, rs1: RiscVGpReg, rm: Int = 7) = emitGpFp(0x53, rm, 0x69, rd, rs1, 2)
    fun fcvtDLu(rd: RiscVFpReg, rs1: RiscVGpReg, rm: Int = 7) = emitGpFp(0x53, rm, 0x69, rd, rs1, 3)

    // Move double<->int (RV64D)
    fun fmvXD(rd: RiscVGpReg, rs1: RiscVFpReg) = emitFpGp(0x53, 0x0, 0x71, rd, rs1, 0)
    fun fmvDX(rd: RiscVFpReg, rs1: RiscVGpReg) = emitGpFp(0x53, 0x0, 0x79, rd, rs1, 0)

    // Compare
    fun feqD(rd: RiscVGpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitFpCmp(0x53, 0x2, 0x51, rd, rs1, rs2)
    fun fltD(rd: RiscVGpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitFpCmp(0x53, 0x1, 0x51, rd, rs1, rs2)
    fun fleD(rd: RiscVGpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) = emitFpCmp(0x53, 0x0, 0x51, rd, rs1, rs2)

    // Classify
    fun fclassD(rd: RiscVGpReg, rs1: RiscVFpReg) = emitFpGp(0x53, 0x1, 0x71, rd, rs1, 0)

    // Pseudo-instructions
    fun fmvD(rd: RiscVFpReg, rs: RiscVFpReg) = fsgnjD(rd, rs, rs)
    fun fnegD(rd: RiscVFpReg, rs: RiscVFpReg) = fsgnjnD(rd, rs, rs)
    fun fabsD(rd: RiscVFpReg, rs: RiscVFpReg) = fsgnjxD(rd, rs, rs)

    // --- A Extension: Atomic Operations ---

    fun lrW(rd: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x2, 0x02, rd, rs1, X0, aq, rl)
    fun scW(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x2, 0x03, rd, rs1, rs2, aq, rl)
    fun lrD(rd: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x3, 0x02, rd, rs1, X0, aq, rl)
    fun scD(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x3, 0x03, rd, rs1, rs2, aq, rl)

    fun amoswapW(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x2, 0x01, rd, rs1, rs2, aq, rl)
    fun amoaddW(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x2, 0x00, rd, rs1, rs2, aq, rl)
    fun amoxorW(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x2, 0x04, rd, rs1, rs2, aq, rl)
    fun amoandW(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x2, 0x0C, rd, rs1, rs2, aq, rl)
    fun amoorW(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x2, 0x08, rd, rs1, rs2, aq, rl)
    fun amominW(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x2, 0x10, rd, rs1, rs2, aq, rl)
    fun amomaxW(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x2, 0x14, rd, rs1, rs2, aq, rl)
    fun amominuW(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x2, 0x18, rd, rs1, rs2, aq, rl)
    fun amomaxuW(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x2, 0x1C, rd, rs1, rs2, aq, rl)

    // 64-bit atomics (RV64A)
    fun amoswapD(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x3, 0x01, rd, rs1, rs2, aq, rl)
    fun amoaddD(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x3, 0x00, rd, rs1, rs2, aq, rl)
    fun amoxorD(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x3, 0x04, rd, rs1, rs2, aq, rl)
    fun amoandD(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x3, 0x0C, rd, rs1, rs2, aq, rl)
    fun amoorD(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x3, 0x08, rd, rs1, rs2, aq, rl)
    fun amominD(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x3, 0x10, rd, rs1, rs2, aq, rl)
    fun amomaxD(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x3, 0x14, rd, rs1, rs2, aq, rl)
    fun amominuD(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x3, 0x18, rd, rs1, rs2, aq, rl)
    fun amomaxuD(rd: RiscVGpReg, rs2: RiscVGpReg, rs1: RiscVGpReg, aq: Boolean = false, rl: Boolean = false) =
        emitAtomic(0x2F, 0x3, 0x1C, rd, rs1, rs2, aq, rl)

    // --- C Extension: Compressed Instructions (16-bit) ---

    // Stack-based loads/stores (use sp as base)
    fun cLwsp(rd: RiscVGpReg, offset: Int) {
        val off5 = (offset shr 5) and 1
        val off4_2 = (offset shr 2) and 0x7
        val off7_6 = (offset shr 6) and 0x3
        emit16(0x4002 or (off5 shl 12) or (rd.encoding shl 7) or (off4_2 shl 4) or (off7_6 shl 2))
    }

    fun cLdsp(rd: RiscVGpReg, offset: Int) {
        val off5 = (offset shr 5) and 1
        val off4_3 = (offset shr 3) and 0x3
        val off8_6 = (offset shr 6) and 0x7
        emit16(0x6002 or (off5 shl 12) or (rd.encoding shl 7) or (off4_3 shl 5) or (off8_6 shl 2))
    }

    fun cSwsp(rs2: RiscVGpReg, offset: Int) {
        val off5_2 = (offset shr 2) and 0xF
        val off7_6 = (offset shr 6) and 0x3
        emit16(0xC002 or (off7_6 shl 11) or (off5_2 shl 7) or (rs2.encoding shl 2))
    }

    fun cSdsp(rs2: RiscVGpReg, offset: Int) {
        val off5_3 = (offset shr 3) and 0x7
        val off8_6 = (offset shr 6) and 0x7
        emit16(0xE002 or (off8_6 shl 10) or (off5_3 shl 7) or (rs2.encoding shl 2))
    }

    // Register-based loads/stores (compressed registers s0-s7 / a0-a5, encoding 0-7)
    fun cLw(rd: RiscVGpReg, rs1: RiscVGpReg, offset: Int) {
        val rd3 = (rd.encoding - 8) and 0x7
        val rs13 = (rs1.encoding - 8) and 0x7
        val off5_3 = (offset shr 3) and 0x7
        val off2 = (offset shr 2) and 1
        val off6 = (offset shr 6) and 1
        emit16(0x4000 or (off5_3 shl 10) or (rs13 shl 7) or (off2 shl 6) or (off6 shl 5) or (rd3 shl 2))
    }

    fun cLd(rd: RiscVGpReg, rs1: RiscVGpReg, offset: Int) {
        val rd3 = (rd.encoding - 8) and 0x7
        val rs13 = (rs1.encoding - 8) and 0x7
        val off5_3 = (offset shr 3) and 0x7
        val off7_6 = (offset shr 6) and 0x3
        emit16(0x6000 or (off5_3 shl 10) or (rs13 shl 7) or (off7_6 shl 5) or (rd3 shl 2))
    }

    fun cSw(rs2: RiscVGpReg, rs1: RiscVGpReg, offset: Int) {
        val rs23 = (rs2.encoding - 8) and 0x7
        val rs13 = (rs1.encoding - 8) and 0x7
        val off5_3 = (offset shr 3) and 0x7
        val off2 = (offset shr 2) and 1
        val off6 = (offset shr 6) and 1
        emit16(0xC000 or (off5_3 shl 10) or (rs13 shl 7) or (off2 shl 6) or (off6 shl 5) or (rs23 shl 2))
    }

    fun cSd(rs2: RiscVGpReg, rs1: RiscVGpReg, offset: Int) {
        val rs23 = (rs2.encoding - 8) and 0x7
        val rs13 = (rs1.encoding - 8) and 0x7
        val off5_3 = (offset shr 3) and 0x7
        val off7_6 = (offset shr 6) and 0x3
        emit16(0xE000 or (off5_3 shl 10) or (rs13 shl 7) or (off7_6 shl 5) or (rs23 shl 2))
    }

    // Arithmetic (register)
    fun cMv(rd: RiscVGpReg, rs2: RiscVGpReg) = emit16(0x8002 or (rd.encoding shl 7) or (rs2.encoding shl 2))
    fun cAdd(rd: RiscVGpReg, rs2: RiscVGpReg) = emit16(0x9002 or (rd.encoding shl 7) or (rs2.encoding shl 2))

    // Arithmetic immediate
    fun cAddi(rd: RiscVGpReg, imm: Int) {
        val bit5 = (imm shr 5) and 1
        val bits4_0 = imm and 0x1F
        emit16(0x0001 or (bit5 shl 12) or (rd.encoding shl 7) or (bits4_0 shl 2))
    }

    fun cAddiw(rd: RiscVGpReg, imm: Int) {
        val bit5 = (imm shr 5) and 1
        val bits4_0 = imm and 0x1F
        emit16(0x2001 or (bit5 shl 12) or (rd.encoding shl 7) or (bits4_0 shl 2))
    }

    fun cAddi16sp(imm: Int) {
        val bit9 = (imm shr 9) and 1
        val bit4 = (imm shr 4) and 1
        val bit6 = (imm shr 6) and 1
        val bits8_7 = (imm shr 7) and 0x3
        val bit5 = (imm shr 5) and 1
        emit16(0x6101 or (bit9 shl 12) or (bit4 shl 6) or (bit6 shl 5) or (bits8_7 shl 3) or (bit5 shl 2))
    }

    fun cLi(rd: RiscVGpReg, imm: Int) {
        val bit5 = (imm shr 5) and 1
        val bits4_0 = imm and 0x1F
        emit16(0x4001 or (bit5 shl 12) or (rd.encoding shl 7) or (bits4_0 shl 2))
    }

    fun cLui(rd: RiscVGpReg, imm: Int) {
        val bit17 = (imm shr 17) and 1
        val bits16_12 = (imm shr 12) and 0x1F
        emit16(0x6001 or (bit17 shl 12) or (rd.encoding shl 7) or (bits16_12 shl 2))
    }

    fun cSlli(rd: RiscVGpReg, shamt: Int) {
        val bit5 = (shamt shr 5) and 1
        val bits4_0 = shamt and 0x1F
        emit16(0x0002 or (bit5 shl 12) or (rd.encoding shl 7) or (bits4_0 shl 2))
    }

    // Compressed arithmetic on s0-a5 (3-bit register encoding)
    fun cSub(rd: RiscVGpReg, rs2: RiscVGpReg) {
        val rd3 = (rd.encoding - 8) and 0x7
        val rs23 = (rs2.encoding - 8) and 0x7
        emit16(0x8C01 or (rd3 shl 7) or (rs23 shl 2))
    }

    fun cXor(rd: RiscVGpReg, rs2: RiscVGpReg) {
        val rd3 = (rd.encoding - 8) and 0x7
        val rs23 = (rs2.encoding - 8) and 0x7
        emit16(0x8C21 or (rd3 shl 7) or (rs23 shl 2))
    }

    fun cOr(rd: RiscVGpReg, rs2: RiscVGpReg) {
        val rd3 = (rd.encoding - 8) and 0x7
        val rs23 = (rs2.encoding - 8) and 0x7
        emit16(0x8C41 or (rd3 shl 7) or (rs23 shl 2))
    }

    fun cAnd(rd: RiscVGpReg, rs2: RiscVGpReg) {
        val rd3 = (rd.encoding - 8) and 0x7
        val rs23 = (rs2.encoding - 8) and 0x7
        emit16(0x8C61 or (rd3 shl 7) or (rs23 shl 2))
    }

    fun cSubw(rd: RiscVGpReg, rs2: RiscVGpReg) {
        val rd3 = (rd.encoding - 8) and 0x7
        val rs23 = (rs2.encoding - 8) and 0x7
        emit16(0x9C01 or (rd3 shl 7) or (rs23 shl 2))
    }

    fun cAddw(rd: RiscVGpReg, rs2: RiscVGpReg) {
        val rd3 = (rd.encoding - 8) and 0x7
        val rs23 = (rs2.encoding - 8) and 0x7
        emit16(0x9C21 or (rd3 shl 7) or (rs23 shl 2))
    }

    // Shift/logic immediate on compressed registers
    fun cSrli(rd: RiscVGpReg, shamt: Int) {
        val rd3 = (rd.encoding - 8) and 0x7
        val bit5 = (shamt shr 5) and 1
        val bits4_0 = shamt and 0x1F
        emit16(0x8001 or (bit5 shl 12) or (rd3 shl 7) or (bits4_0 shl 2))
    }

    fun cSrai(rd: RiscVGpReg, shamt: Int) {
        val rd3 = (rd.encoding - 8) and 0x7
        val bit5 = (shamt shr 5) and 1
        val bits4_0 = shamt and 0x1F
        emit16(0x8401 or (bit5 shl 12) or (rd3 shl 7) or (bits4_0 shl 2))
    }

    fun cAndi(rd: RiscVGpReg, imm: Int) {
        val rd3 = (rd.encoding - 8) and 0x7
        val bit5 = (imm shr 5) and 1
        val bits4_0 = imm and 0x1F
        emit16(0x8801 or (bit5 shl 12) or (rd3 shl 7) or (bits4_0 shl 2))
    }

    // Branches (compressed)
    fun cBeqz(rs1: RiscVGpReg, offset: Int) {
        val rs13 = (rs1.encoding - 8) and 0x7
        val bit8 = (offset shr 8) and 1
        val bits4_3 = (offset shr 3) and 0x3
        val bits7_6 = (offset shr 6) and 0x3
        val bits2_1 = (offset shr 1) and 0x3
        val bit5 = (offset shr 5) and 1
        emit16(0xC001 or (bit8 shl 12) or (rs13 shl 7) or (bits4_3 shl 10) or
            (bits7_6 shl 5) or (bits2_1 shl 3) or (bit5 shl 2))
    }

    fun cBnez(rs1: RiscVGpReg, offset: Int) {
        val rs13 = (rs1.encoding - 8) and 0x7
        val bit8 = (offset shr 8) and 1
        val bits4_3 = (offset shr 3) and 0x3
        val bits7_6 = (offset shr 6) and 0x3
        val bits2_1 = (offset shr 1) and 0x3
        val bit5 = (offset shr 5) and 1
        emit16(0xE001 or (bit8 shl 12) or (rs13 shl 7) or (bits4_3 shl 10) or
            (bits7_6 shl 5) or (bits2_1 shl 3) or (bit5 shl 2))
    }

    // Jumps (compressed)
    fun cJ(offset: Int) {
        val bit11 = (offset shr 11) and 1
        val bit4 = (offset shr 4) and 1
        val bits9_8 = (offset shr 8) and 0x3
        val bit10 = (offset shr 10) and 1
        val bit6 = (offset shr 6) and 1
        val bit7 = (offset shr 7) and 1
        val bits3_1 = (offset shr 1) and 0x7
        val bit5 = (offset shr 5) and 1
        emit16(0xA001 or (bit11 shl 12) or (bit4 shl 11) or (bits9_8 shl 9) or
            (bit10 shl 8) or (bit6 shl 7) or (bit7 shl 6) or (bits3_1 shl 3) or (bit5 shl 2))
    }

    fun cJr(rs1: RiscVGpReg) = emit16(0x8002 or (rs1.encoding shl 7))
    fun cJalr(rs1: RiscVGpReg) = emit16(0x9002 or (rs1.encoding shl 7))

    // Misc
    fun cNop() = emit16(0x0001)
    fun cEbreak() = emit16(0x9002)

    // --- System Instructions ---
    fun ecall() = emit32(0x00000073)
    fun ebreak() = emit32(0x00100073)
    fun fence() = emit32(0x0FF0000F)
    fun fenceI() = emit32(0x0000100F)

    // --- Pseudo-instructions ---
    fun nop() = addi(X0, X0, 0)
    fun li(rd: RiscVGpReg, imm: Int) {
        if (imm in -2048..2047) {
            addi(rd, X0, imm)
        } else {
            val upper = (imm + 0x800) ushr 12
            val lower = imm - (upper shl 12)
            lui(rd, upper)
            if (lower != 0) addi(rd, rd, lower)
        }
    }
    fun li(rd: RiscVGpReg, imm: Long) {
        if (imm in -2048..2047) {
            addi(rd, X0, imm.toInt())
        } else if (imm in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            li(rd, imm.toInt())
        } else {
            // For 64-bit, use lui+addi for upper 32 bits, slli, then addi for lower
            val upper32 = (imm shr 32).toInt()
            val lower32 = imm.toInt()
            li(rd, upper32)
            slli(rd, rd, 32)
            if (lower32 != 0) {
                val upper20 = (lower32 + 0x800) ushr 12
                val lower12 = lower32 - (upper20 shl 12)
                if (upper20 != 0) {
                    lui(X5, upper20) // use t0 as scratch
                    add(rd, rd, X5)
                }
                if (lower12 != 0) addi(rd, rd, lower12)
            }
        }
    }
    fun mv(rd: RiscVGpReg, rs: RiscVGpReg) = addi(rd, rs, 0)
    fun not(rd: RiscVGpReg, rs: RiscVGpReg) = xori(rd, rs, -1)
    fun neg(rd: RiscVGpReg, rs: RiscVGpReg) = sub(rd, X0, rs)
    fun negw(rd: RiscVGpReg, rs: RiscVGpReg) = subw(rd, X0, rs)
    fun seqz(rd: RiscVGpReg, rs: RiscVGpReg) = sltiu(rd, rs, 1)
    fun snez(rd: RiscVGpReg, rs: RiscVGpReg) = sltu(rd, X0, rs)
    fun j(offset: Int) = jal(X0, offset)
    fun j(label: String) = jal(X0, label)
    fun call(label: String) = jal(X1, label)
    fun ret() = jalr(X0, X1, 0)

    // --- Encoding Helpers ---

    private fun emitR(opcode: Int, funct3: Int, funct7: Int, rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        val insn = (funct7 shl 25) or (rs2.encoding shl 20) or (rs1.encoding shl 15) or
            (funct3 shl 12) or (rd.encoding shl 7) or opcode
        emit32(insn)
    }

    private fun emitI(opcode: Int, funct3: Int, rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        val insn = ((imm and 0xFFF) shl 20) or (rs1.encoding shl 15) or
            (funct3 shl 12) or (rd.encoding shl 7) or opcode
        emit32(insn)
    }

    // I-type with raw register encodings (for FP loads)
    private fun emitI(opcode: Int, funct3: Int, rdEnc: Int, rs1: RiscVGpReg, imm: Int) {
        val insn = ((imm and 0xFFF) shl 20) or (rs1.encoding shl 15) or
            (funct3 shl 12) or (rdEnc shl 7) or opcode
        emit32(insn)
    }

    private fun emitS(opcode: Int, funct3: Int, rs2: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        val imm11_5 = (imm shr 5) and 0x7F
        val imm4_0 = imm and 0x1F
        val insn = (imm11_5 shl 25) or (rs2.encoding shl 20) or (rs1.encoding shl 15) or
            (funct3 shl 12) or (imm4_0 shl 7) or opcode
        emit32(insn)
    }

    // S-type with raw rs2 encoding (for FP stores)
    private fun emitS(opcode: Int, funct3: Int, rs2Enc: Int, rs1: RiscVGpReg, imm: Int) {
        val imm11_5 = (imm shr 5) and 0x7F
        val imm4_0 = imm and 0x1F
        val insn = (imm11_5 shl 25) or (rs2Enc shl 20) or (rs1.encoding shl 15) or
            (funct3 shl 12) or (imm4_0 shl 7) or opcode
        emit32(insn)
    }

    // R4-type: floating-point (funct7 = funct5 in bits 31:27, rs2 in 24:20)
    private fun emitR4(opcode: Int, funct3: Int, funct5: Int, rd: RiscVFpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) {
        val insn = (funct5 shl 25) or (rs2.encoding shl 20) or (rs1.encoding shl 15) or
            (funct3 shl 12) or (rd.encoding shl 7) or opcode
        emit32(insn)
    }

    // FP to GP (e.g., fcvt.w.s, fmv.x.w): rd=GP, rs1=FP, rs2=subop
    private fun emitFpGp(opcode: Int, funct3: Int, funct5: Int, rd: RiscVGpReg, rs1: RiscVFpReg, rs2Enc: Int) {
        val insn = (funct5 shl 25) or (rs2Enc shl 20) or (rs1.encoding shl 15) or
            (funct3 shl 12) or (rd.encoding shl 7) or opcode
        emit32(insn)
    }

    // GP to FP (e.g., fcvt.s.w, fmv.w.x): rd=FP, rs1=GP, rs2=subop
    private fun emitGpFp(opcode: Int, funct3: Int, funct5: Int, rd: RiscVFpReg, rs1: RiscVGpReg, rs2Enc: Int) {
        val insn = (funct5 shl 25) or (rs2Enc shl 20) or (rs1.encoding shl 15) or
            (funct3 shl 12) or (rd.encoding shl 7) or opcode
        emit32(insn)
    }

    // FP compare: rd=GP, rs1=FP, rs2=FP
    private fun emitFpCmp(opcode: Int, funct3: Int, funct5: Int, rd: RiscVGpReg, rs1: RiscVFpReg, rs2: RiscVFpReg) {
        val insn = (funct5 shl 25) or (rs2.encoding shl 20) or (rs1.encoding shl 15) or
            (funct3 shl 12) or (rd.encoding shl 7) or opcode
        emit32(insn)
    }

    // Atomic: R-type with aq/rl bits in funct7
    private fun emitAtomic(opcode: Int, funct3: Int, funct5: Int, rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg, aq: Boolean, rl: Boolean) {
        val aqBit = if (aq) 1 else 0
        val rlBit = if (rl) 1 else 0
        val funct7 = (funct5 shl 2) or (aqBit shl 1) or rlBit
        emitR(opcode, funct3, funct7, rd, rs1, rs2)
    }

    private fun emitB(opcode: Int, funct3: Int, rs1: RiscVGpReg, rs2: RiscVGpReg, imm: Int) {
        // B-type immediate: imm[12|10:5|4:1|11]
        val bit12 = (imm shr 12) and 1
        val bits10_5 = (imm shr 5) and 0x3F
        val bits4_1 = (imm shr 1) and 0xF
        val bit11 = (imm shr 11) and 1
        val insn = (bit12 shl 31) or (bits10_5 shl 25) or (rs2.encoding shl 20) or
            (rs1.encoding shl 15) or (funct3 shl 12) or (bits4_1 shl 8) or (bit11 shl 7) or opcode
        emit32(insn)
    }

    private fun emitU(opcode: Int, rd: RiscVGpReg, imm: Int) {
        val insn = (imm shl 12) or (rd.encoding shl 7) or opcode
        emit32(insn)
    }

    private fun emitJ(opcode: Int, rd: RiscVGpReg, imm: Int) {
        // J-type immediate: imm[20|10:1|11|19:12]
        val bit20 = (imm shr 20) and 1
        val bits10_1 = (imm shr 1) and 0x3FF
        val bit11 = (imm shr 11) and 1
        val bits19_12 = (imm shr 12) and 0xFF
        val insn = (bit20 shl 31) or (bits10_1 shl 21) or (bit11 shl 20) or
            (bits19_12 shl 12) or (rd.encoding shl 7) or opcode
        emit32(insn)
    }

    private fun emitBranchLabel(opcode: Int, funct3: Int, rs1: RiscVGpReg, rs2: RiscVGpReg, label: String) {
        fixups.add(Fixup(buf.size(), label, FixupKind.BRANCH))
        emitB(opcode, funct3, rs1, rs2, 0)
    }

    private fun emit32(value: Int) {
        buf.write(value and 0xFF)
        buf.write((value shr 8) and 0xFF)
        buf.write((value shr 16) and 0xFF)
        buf.write((value shr 24) and 0xFF)
    }

    private fun emit16(value: Int) {
        buf.write(value and 0xFF)
        buf.write((value shr 8) and 0xFF)
    }

    private fun applyFixups() {
        val bytes = buf.toByteArray()
        for (fixup in fixups) {
            val target = labels[fixup.label] ?: continue // skip unresolved (external symbols)
            val offset = target - fixup.offset
            val insn = readU32(bytes, fixup.offset)

            val patched = when (fixup.kind) {
                FixupKind.BRANCH -> patchBranch(insn, offset)
                FixupKind.JAL -> patchJal(insn, offset)
            }
            writeU32(bytes, fixup.offset, patched)
        }
        // Rebuild buffer with patched bytes
        buf.reset()
        buf.write(bytes)
    }

    private fun patchBranch(insn: Int, offset: Int): Int {
        // Keep rs2[24:20], rs1[19:15], funct3[14:12], opcode[6:0]
        val base = insn and 0x01FFF07F
        val bit12 = (offset shr 12) and 1
        val bits10_5 = (offset shr 5) and 0x3F
        val bits4_1 = (offset shr 1) and 0xF
        val bit11 = (offset shr 11) and 1
        return base or (bit12 shl 31) or (bits10_5 shl 25) or (bits4_1 shl 8) or (bit11 shl 7)
    }

    private fun patchJal(insn: Int, offset: Int): Int {
        val base = insn and 0x00000FFF // keep rd and opcode
        val bit20 = (offset shr 20) and 1
        val bits10_1 = (offset shr 1) and 0x3FF
        val bit11 = (offset shr 11) and 1
        val bits19_12 = (offset shr 12) and 0xFF
        return base or (bit20 shl 31) or (bits10_1 shl 21) or (bit11 shl 20) or (bits19_12 shl 12)
    }

    companion object {
        private fun readU32(bytes: ByteArray, off: Int): Int =
            (bytes[off].toInt() and 0xFF) or
            ((bytes[off + 1].toInt() and 0xFF) shl 8) or
            ((bytes[off + 2].toInt() and 0xFF) shl 16) or
            ((bytes[off + 3].toInt() and 0xFF) shl 24)

        private fun writeU32(bytes: ByteArray, off: Int, value: Int) {
            bytes[off] = (value and 0xFF).toByte()
            bytes[off + 1] = ((value shr 8) and 0xFF).toByte()
            bytes[off + 2] = ((value shr 16) and 0xFF).toByte()
            bytes[off + 3] = ((value shr 24) and 0xFF).toByte()
        }
    }
}
