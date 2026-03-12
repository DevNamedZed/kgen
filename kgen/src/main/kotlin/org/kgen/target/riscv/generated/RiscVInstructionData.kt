// Generated — do not edit
package org.kgen.target.riscv

/**
 * Encoding metadata for a single RISC-V instruction form.
 *
 * RISC-V encoding fields follow the ISA specification:
 * R-type: funct7[31:25] | rs2[24:20] | rs1[19:15] | funct3[14:12] | rd[11:7] | opcode[6:0]
 * I-type: imm[31:20] | rs1[19:15] | funct3[14:12] | rd[11:7] | opcode[6:0]
 * S-type: imm[31:25] | rs2[24:20] | rs1[19:15] | funct3[14:12] | imm[11:7] | opcode[6:0]
 * B-type: imm[31:25] | rs2[24:20] | rs1[19:15] | funct3[14:12] | imm[11:7] | opcode[6:0]
 * U-type: imm[31:12] | rd[11:7] | opcode[6:0]
 * J-type: imm[31:12] | rd[11:7] | opcode[6:0]
 */
data class RiscVEncodingInfo(
    val opcode: Int,
    val funct3: Int = 0,
    val funct7: Int = 0,
    val funct5: Int = 0,
    val fmt: Int = 0,
    val rs2Fixed: Int = -1,
    val immFixed: Int = -1,
    val format: RiscVFormat,
)

/** RISC-V instruction encoding format. */
enum class RiscVFormat {
    B,
    B_LABEL,
    I,
    I_LOAD,
    I_SHIFT,
    J,
    J_LABEL,
    R,
    R_ATOMIC,
    R_FP,
    R_FP_CMP,
    R_FP_CVT,
    R_FP_NOROUND,
    R_FP_UNARY,
    S,
    SYSTEM,
    U;
}

data class RiscVInstructionForm(
    val mnemonic: String,
    val operands: List<RiscVOperandType>,
    val encoding: RiscVEncodingInfo,
    val feature: RiscVFeature,
)

object RiscVInstructionData {
    val forms: List<RiscVInstructionForm> by lazy { buildForms() }

    val byMnemonic: Map<String, List<RiscVInstructionForm>> by lazy {
        forms.groupBy { it.mnemonic }
    }

    private fun buildForms(): List<RiscVInstructionForm> {
        val list = ArrayList<RiscVInstructionForm>(149)
        buildForms0(list)
        buildForms1(list)
        return list
    }

    private fun buildForms0(list: MutableList<RiscVInstructionForm>) {
        list.add(RiscVInstructionForm("add", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 0, funct7 = 0, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("sub", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 0, funct7 = 32, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("sll", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 1, funct7 = 0, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("slt", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 2, funct7 = 0, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("sltu", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 3, funct7 = 0, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("xor", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 4, funct7 = 0, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("srl", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 5, funct7 = 0, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("sra", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 5, funct7 = 32, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("or", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 6, funct7 = 0, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("and", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 7, funct7 = 0, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("addw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 59, funct3 = 0, funct7 = 0, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("subw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 59, funct3 = 0, funct7 = 32, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("sllw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 59, funct3 = 1, funct7 = 0, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("srlw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 59, funct3 = 5, funct7 = 0, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("sraw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 59, funct3 = 5, funct7 = 32, format = RiscVFormat.R), RiscVFeature.I))
        list.add(RiscVInstructionForm("addi", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 19, funct3 = 0, format = RiscVFormat.I), RiscVFeature.I))
        list.add(RiscVInstructionForm("slti", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 19, funct3 = 2, format = RiscVFormat.I), RiscVFeature.I))
        list.add(RiscVInstructionForm("sltiu", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 19, funct3 = 3, format = RiscVFormat.I), RiscVFeature.I))
        list.add(RiscVInstructionForm("xori", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 19, funct3 = 4, format = RiscVFormat.I), RiscVFeature.I))
        list.add(RiscVInstructionForm("ori", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 19, funct3 = 6, format = RiscVFormat.I), RiscVFeature.I))
        list.add(RiscVInstructionForm("andi", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 19, funct3 = 7, format = RiscVFormat.I), RiscVFeature.I))
        list.add(RiscVInstructionForm("slli", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.SHAMT), RiscVEncodingInfo(opcode = 19, funct3 = 1, funct7 = 0, format = RiscVFormat.I_SHIFT), RiscVFeature.I))
        list.add(RiscVInstructionForm("srli", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.SHAMT), RiscVEncodingInfo(opcode = 19, funct3 = 5, funct7 = 0, format = RiscVFormat.I_SHIFT), RiscVFeature.I))
        list.add(RiscVInstructionForm("srai", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.SHAMT), RiscVEncodingInfo(opcode = 19, funct3 = 5, funct7 = 16, format = RiscVFormat.I_SHIFT), RiscVFeature.I))
        list.add(RiscVInstructionForm("addiw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 27, funct3 = 0, format = RiscVFormat.I), RiscVFeature.I))
        list.add(RiscVInstructionForm("slliw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.SHAMT), RiscVEncodingInfo(opcode = 27, funct3 = 1, funct7 = 0, format = RiscVFormat.I_SHIFT), RiscVFeature.I))
        list.add(RiscVInstructionForm("srliw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.SHAMT), RiscVEncodingInfo(opcode = 27, funct3 = 5, funct7 = 0, format = RiscVFormat.I_SHIFT), RiscVFeature.I))
        list.add(RiscVInstructionForm("sraiw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.SHAMT), RiscVEncodingInfo(opcode = 27, funct3 = 5, funct7 = 16, format = RiscVFormat.I_SHIFT), RiscVFeature.I))
        list.add(RiscVInstructionForm("lb", listOf(RiscVOperandType.GP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 3, funct3 = 0, format = RiscVFormat.I_LOAD), RiscVFeature.I))
        list.add(RiscVInstructionForm("lh", listOf(RiscVOperandType.GP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 3, funct3 = 1, format = RiscVFormat.I_LOAD), RiscVFeature.I))
        list.add(RiscVInstructionForm("lw", listOf(RiscVOperandType.GP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 3, funct3 = 2, format = RiscVFormat.I_LOAD), RiscVFeature.I))
        list.add(RiscVInstructionForm("ld", listOf(RiscVOperandType.GP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 3, funct3 = 3, format = RiscVFormat.I_LOAD), RiscVFeature.I))
        list.add(RiscVInstructionForm("lbu", listOf(RiscVOperandType.GP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 3, funct3 = 4, format = RiscVFormat.I_LOAD), RiscVFeature.I))
        list.add(RiscVInstructionForm("lhu", listOf(RiscVOperandType.GP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 3, funct3 = 5, format = RiscVFormat.I_LOAD), RiscVFeature.I))
        list.add(RiscVInstructionForm("lwu", listOf(RiscVOperandType.GP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 3, funct3 = 6, format = RiscVFormat.I_LOAD), RiscVFeature.I))
        list.add(RiscVInstructionForm("sb", listOf(RiscVOperandType.GP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 35, funct3 = 0, format = RiscVFormat.S), RiscVFeature.I))
        list.add(RiscVInstructionForm("sh", listOf(RiscVOperandType.GP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 35, funct3 = 1, format = RiscVFormat.S), RiscVFeature.I))
        list.add(RiscVInstructionForm("sw", listOf(RiscVOperandType.GP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 35, funct3 = 2, format = RiscVFormat.S), RiscVFeature.I))
        list.add(RiscVInstructionForm("sd", listOf(RiscVOperandType.GP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 35, funct3 = 3, format = RiscVFormat.S), RiscVFeature.I))
        list.add(RiscVInstructionForm("beq", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 99, funct3 = 0, format = RiscVFormat.B), RiscVFeature.I))
        list.add(RiscVInstructionForm("beq", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.LABEL), RiscVEncodingInfo(opcode = 99, funct3 = 0, format = RiscVFormat.B_LABEL), RiscVFeature.I))
        list.add(RiscVInstructionForm("bne", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 99, funct3 = 1, format = RiscVFormat.B), RiscVFeature.I))
        list.add(RiscVInstructionForm("bne", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.LABEL), RiscVEncodingInfo(opcode = 99, funct3 = 1, format = RiscVFormat.B_LABEL), RiscVFeature.I))
        list.add(RiscVInstructionForm("blt", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 99, funct3 = 4, format = RiscVFormat.B), RiscVFeature.I))
        list.add(RiscVInstructionForm("blt", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.LABEL), RiscVEncodingInfo(opcode = 99, funct3 = 4, format = RiscVFormat.B_LABEL), RiscVFeature.I))
        list.add(RiscVInstructionForm("bge", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 99, funct3 = 5, format = RiscVFormat.B), RiscVFeature.I))
        list.add(RiscVInstructionForm("bge", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.LABEL), RiscVEncodingInfo(opcode = 99, funct3 = 5, format = RiscVFormat.B_LABEL), RiscVFeature.I))
        list.add(RiscVInstructionForm("bltu", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 99, funct3 = 6, format = RiscVFormat.B), RiscVFeature.I))
        list.add(RiscVInstructionForm("bltu", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.LABEL), RiscVEncodingInfo(opcode = 99, funct3 = 6, format = RiscVFormat.B_LABEL), RiscVFeature.I))
        list.add(RiscVInstructionForm("bgeu", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 99, funct3 = 7, format = RiscVFormat.B), RiscVFeature.I))
        list.add(RiscVInstructionForm("bgeu", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.LABEL), RiscVEncodingInfo(opcode = 99, funct3 = 7, format = RiscVFormat.B_LABEL), RiscVFeature.I))
        list.add(RiscVInstructionForm("jal", listOf(RiscVOperandType.GP, RiscVOperandType.IMM), RiscVEncodingInfo(opcode = 111, format = RiscVFormat.J), RiscVFeature.I))
        list.add(RiscVInstructionForm("jal", listOf(RiscVOperandType.GP, RiscVOperandType.LABEL), RiscVEncodingInfo(opcode = 111, format = RiscVFormat.J_LABEL), RiscVFeature.I))
        list.add(RiscVInstructionForm("jalr", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.IMM12), RiscVEncodingInfo(opcode = 103, funct3 = 0, format = RiscVFormat.I), RiscVFeature.I))
        list.add(RiscVInstructionForm("lui", listOf(RiscVOperandType.GP, RiscVOperandType.IMM), RiscVEncodingInfo(opcode = 55, format = RiscVFormat.U), RiscVFeature.I))
        list.add(RiscVInstructionForm("auipc", listOf(RiscVOperandType.GP, RiscVOperandType.IMM), RiscVEncodingInfo(opcode = 23, format = RiscVFormat.U), RiscVFeature.I))
        list.add(RiscVInstructionForm("ecall", listOf(), RiscVEncodingInfo(opcode = 115, funct3 = 0, funct7 = 0, format = RiscVFormat.SYSTEM), RiscVFeature.I))
        list.add(RiscVInstructionForm("ebreak", listOf(), RiscVEncodingInfo(opcode = 115, funct3 = 0, funct7 = 0, immFixed = 1, format = RiscVFormat.SYSTEM), RiscVFeature.I))
        list.add(RiscVInstructionForm("fence", listOf(), RiscVEncodingInfo(opcode = 15, funct3 = 0, format = RiscVFormat.SYSTEM), RiscVFeature.I))
        list.add(RiscVInstructionForm("fenceI", listOf(), RiscVEncodingInfo(opcode = 15, funct3 = 1, format = RiscVFormat.SYSTEM), RiscVFeature.I))
        list.add(RiscVInstructionForm("mul", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 0, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("mulh", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 1, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("mulhsu", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 2, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("mulhu", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 3, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("div", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 4, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("divu", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 5, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("rem", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 6, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("remu", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 51, funct3 = 7, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("mulw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 59, funct3 = 0, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("divw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 59, funct3 = 4, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("divuw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 59, funct3 = 5, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("remw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 59, funct3 = 6, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("remuw", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 59, funct3 = 7, funct7 = 1, format = RiscVFormat.R), RiscVFeature.M))
        list.add(RiscVInstructionForm("flw", listOf(RiscVOperandType.FP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 7, funct3 = 2, format = RiscVFormat.I_LOAD), RiscVFeature.F))
        list.add(RiscVInstructionForm("fsw", listOf(RiscVOperandType.FP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 39, funct3 = 2, format = RiscVFormat.S), RiscVFeature.F))
        list.add(RiscVInstructionForm("faddS", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 0, fmt = 0, format = RiscVFormat.R_FP), RiscVFeature.F))
        list.add(RiscVInstructionForm("fsubS", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 4, fmt = 0, format = RiscVFormat.R_FP), RiscVFeature.F))
        list.add(RiscVInstructionForm("fmulS", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 8, fmt = 0, format = RiscVFormat.R_FP), RiscVFeature.F))
        list.add(RiscVInstructionForm("fdivS", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 12, fmt = 0, format = RiscVFormat.R_FP), RiscVFeature.F))
        list.add(RiscVInstructionForm("fsqrtS", listOf(RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 44, fmt = 0, format = RiscVFormat.R_FP_UNARY), RiscVFeature.F))
        list.add(RiscVInstructionForm("fsgnjS", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 16, fmt = 0, format = RiscVFormat.R_FP_NOROUND), RiscVFeature.F))
        list.add(RiscVInstructionForm("fsgnjnS", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 16, fmt = 0, format = RiscVFormat.R_FP_NOROUND), RiscVFeature.F))
        list.add(RiscVInstructionForm("fsgnjxS", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 2, funct5 = 16, fmt = 0, format = RiscVFormat.R_FP_NOROUND), RiscVFeature.F))
        list.add(RiscVInstructionForm("fminS", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 20, fmt = 0, format = RiscVFormat.R_FP_NOROUND), RiscVFeature.F))
        list.add(RiscVInstructionForm("fmaxS", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 20, fmt = 0, format = RiscVFormat.R_FP_NOROUND), RiscVFeature.F))
        list.add(RiscVInstructionForm("feqS", listOf(RiscVOperandType.GP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 2, funct5 = 80, fmt = 0, format = RiscVFormat.R_FP_CMP), RiscVFeature.F))
        list.add(RiscVInstructionForm("fltS", listOf(RiscVOperandType.GP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 80, fmt = 0, format = RiscVFormat.R_FP_CMP), RiscVFeature.F))
        list.add(RiscVInstructionForm("fleS", listOf(RiscVOperandType.GP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 80, fmt = 0, format = RiscVFormat.R_FP_CMP), RiscVFeature.F))
        list.add(RiscVInstructionForm("fcvtWS", listOf(RiscVOperandType.GP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 96, fmt = 0, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), RiscVFeature.F))
        list.add(RiscVInstructionForm("fcvtWuS", listOf(RiscVOperandType.GP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 96, fmt = 0, rs2Fixed = 1, format = RiscVFormat.R_FP_CVT), RiscVFeature.F))
        list.add(RiscVInstructionForm("fcvtLS", listOf(RiscVOperandType.GP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 96, fmt = 0, rs2Fixed = 2, format = RiscVFormat.R_FP_CVT), RiscVFeature.F))
        list.add(RiscVInstructionForm("fcvtLuS", listOf(RiscVOperandType.GP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 96, fmt = 0, rs2Fixed = 3, format = RiscVFormat.R_FP_CVT), RiscVFeature.F))
        list.add(RiscVInstructionForm("fcvtSW", listOf(RiscVOperandType.FP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 83, funct5 = 104, fmt = 0, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), RiscVFeature.F))
        list.add(RiscVInstructionForm("fcvtSWu", listOf(RiscVOperandType.FP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 83, funct5 = 104, fmt = 0, rs2Fixed = 1, format = RiscVFormat.R_FP_CVT), RiscVFeature.F))
        list.add(RiscVInstructionForm("fcvtSL", listOf(RiscVOperandType.FP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 83, funct5 = 104, fmt = 0, rs2Fixed = 2, format = RiscVFormat.R_FP_CVT), RiscVFeature.F))
        list.add(RiscVInstructionForm("fcvtSLu", listOf(RiscVOperandType.FP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 83, funct5 = 104, fmt = 0, rs2Fixed = 3, format = RiscVFormat.R_FP_CVT), RiscVFeature.F))
        list.add(RiscVInstructionForm("fmvXW", listOf(RiscVOperandType.GP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 112, fmt = 0, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), RiscVFeature.F))
        list.add(RiscVInstructionForm("fmvWX", listOf(RiscVOperandType.FP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 120, fmt = 0, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), RiscVFeature.F))
        list.add(RiscVInstructionForm("fclassS", listOf(RiscVOperandType.GP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 112, fmt = 0, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), RiscVFeature.F))
        list.add(RiscVInstructionForm("fld", listOf(RiscVOperandType.FP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 7, funct3 = 3, format = RiscVFormat.I_LOAD), RiscVFeature.D))
    }

    private fun buildForms1(list: MutableList<RiscVInstructionForm>) {
        list.add(RiscVInstructionForm("fsd", listOf(RiscVOperandType.FP, RiscVOperandType.MEM), RiscVEncodingInfo(opcode = 39, funct3 = 3, format = RiscVFormat.S), RiscVFeature.D))
        list.add(RiscVInstructionForm("faddD", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 1, fmt = 1, format = RiscVFormat.R_FP), RiscVFeature.D))
        list.add(RiscVInstructionForm("fsubD", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 5, fmt = 1, format = RiscVFormat.R_FP), RiscVFeature.D))
        list.add(RiscVInstructionForm("fmulD", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 9, fmt = 1, format = RiscVFormat.R_FP), RiscVFeature.D))
        list.add(RiscVInstructionForm("fdivD", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 13, fmt = 1, format = RiscVFormat.R_FP), RiscVFeature.D))
        list.add(RiscVInstructionForm("fsqrtD", listOf(RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 45, fmt = 1, format = RiscVFormat.R_FP_UNARY), RiscVFeature.D))
        list.add(RiscVInstructionForm("fsgnjD", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 17, fmt = 1, format = RiscVFormat.R_FP_NOROUND), RiscVFeature.D))
        list.add(RiscVInstructionForm("fsgnjnD", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 17, fmt = 1, format = RiscVFormat.R_FP_NOROUND), RiscVFeature.D))
        list.add(RiscVInstructionForm("fsgnjxD", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 2, funct5 = 17, fmt = 1, format = RiscVFormat.R_FP_NOROUND), RiscVFeature.D))
        list.add(RiscVInstructionForm("fminD", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 21, fmt = 1, format = RiscVFormat.R_FP_NOROUND), RiscVFeature.D))
        list.add(RiscVInstructionForm("fmaxD", listOf(RiscVOperandType.FP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 21, fmt = 1, format = RiscVFormat.R_FP_NOROUND), RiscVFeature.D))
        list.add(RiscVInstructionForm("fcvtSD", listOf(RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 32, fmt = 0, rs2Fixed = 1, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("fcvtDS", listOf(RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 33, fmt = 1, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("feqD", listOf(RiscVOperandType.GP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 2, funct5 = 81, fmt = 1, format = RiscVFormat.R_FP_CMP), RiscVFeature.D))
        list.add(RiscVInstructionForm("fltD", listOf(RiscVOperandType.GP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 81, fmt = 1, format = RiscVFormat.R_FP_CMP), RiscVFeature.D))
        list.add(RiscVInstructionForm("fleD", listOf(RiscVOperandType.GP, RiscVOperandType.FP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 81, fmt = 1, format = RiscVFormat.R_FP_CMP), RiscVFeature.D))
        list.add(RiscVInstructionForm("fcvtWD", listOf(RiscVOperandType.GP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 97, fmt = 1, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("fcvtWuD", listOf(RiscVOperandType.GP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 97, fmt = 1, rs2Fixed = 1, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("fcvtLD", listOf(RiscVOperandType.GP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 97, fmt = 1, rs2Fixed = 2, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("fcvtLuD", listOf(RiscVOperandType.GP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct5 = 97, fmt = 1, rs2Fixed = 3, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("fcvtDW", listOf(RiscVOperandType.FP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 83, funct5 = 105, fmt = 1, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("fcvtDWu", listOf(RiscVOperandType.FP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 83, funct5 = 105, fmt = 1, rs2Fixed = 1, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("fcvtDL", listOf(RiscVOperandType.FP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 83, funct5 = 105, fmt = 1, rs2Fixed = 2, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("fcvtDLu", listOf(RiscVOperandType.FP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 83, funct5 = 105, fmt = 1, rs2Fixed = 3, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("fmvXD", listOf(RiscVOperandType.GP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 113, fmt = 1, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("fmvDX", listOf(RiscVOperandType.FP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 121, fmt = 1, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("fclassD", listOf(RiscVOperandType.GP, RiscVOperandType.FP), RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 113, fmt = 1, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), RiscVFeature.D))
        list.add(RiscVInstructionForm("lrW", listOf(RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 2, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("scW", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 3, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amoswapW", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 1, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amoaddW", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 0, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amoxorW", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 4, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amoandW", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 12, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amoorW", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 8, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amominW", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 16, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amomaxW", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 20, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amominuW", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 24, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amomaxuW", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 28, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("lrD", listOf(RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 2, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("scD", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 3, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amoswapD", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 1, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amoaddD", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 0, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amoxorD", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 4, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amoandD", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 12, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amoorD", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 8, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amominD", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 16, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amomaxD", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 20, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amominuD", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 24, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
        list.add(RiscVInstructionForm("amomaxuD", listOf(RiscVOperandType.GP, RiscVOperandType.GP, RiscVOperandType.GP), RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 28, format = RiscVFormat.R_ATOMIC), RiscVFeature.A))
    }
}
