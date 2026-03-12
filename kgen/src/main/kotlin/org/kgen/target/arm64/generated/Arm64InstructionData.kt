// Generated — do not edit
package org.kgen.target.arm64

/**
 * Encoding metadata for a single ARM64 instruction form.
 *
 * @property base The 32-bit base opcode with all operand fields zeroed.
 * @property format The encoding format that determines operand bit positions.
 */
data class Arm64EncodingInfo(
    val base: Long,
    val format: Arm64Format,
)

/** Encoding format — determines how operands are packed into the 32-bit instruction word. */
enum class Arm64Format {
    ADR,
    BRANCH26,
    BRANCH_COND,
    CBRANCH,
    CSEL,
    EXCEPTION,
    FP_CMP,
    FP_CMP_ZERO,
    LDST_ACQUIRE,
    LDST_PAIR,
    LDST_SIMM9,
    LDST_UOFF,
    MOVE_WIDE,
    NO_OPERAND,
    RD_COND,
    RD_RM,
    REG1,
    REG2,
    REG2_IMM12,
    REG3,
    REG4,
    RN_IMM12,
    RN_RM,
    TBRANCH;
}

data class Arm64InstructionForm(
    val mnemonic: String,
    val operands: List<Arm64OperandType>,
    val encoding: Arm64EncodingInfo,
    val feature: Arm64Feature,
)

object Arm64InstructionData {
    val forms: List<Arm64InstructionForm> by lazy { buildForms() }

    val byMnemonic: Map<String, List<Arm64InstructionForm>> by lazy {
        forms.groupBy { it.mnemonic }
    }

    private fun buildForms(): List<Arm64InstructionForm> {
        val list = ArrayList<Arm64InstructionForm>(233)
        buildForms0(list)
        buildForms1(list)
        buildForms2(list)
        return list
    }

    private fun buildForms0(list: MutableList<Arm64InstructionForm>) {
        list.add(Arm64InstructionForm("add", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x8B000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("add", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x0B000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("add", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0x91000000L, Arm64Format.REG2_IMM12), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("add", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.IMM12), Arm64EncodingInfo(0x11000000L, Arm64Format.REG2_IMM12), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("adds", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xAB000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("adds", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x2B000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("adds", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xB1000000L, Arm64Format.REG2_IMM12), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("adds", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.IMM12), Arm64EncodingInfo(0x31000000L, Arm64Format.REG2_IMM12), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sub", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xCB000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sub", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x4B000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sub", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xD1000000L, Arm64Format.REG2_IMM12), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sub", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.IMM12), Arm64EncodingInfo(0x51000000L, Arm64Format.REG2_IMM12), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("subs", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xEB000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("subs", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x6B000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("subs", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xF1000000L, Arm64Format.REG2_IMM12), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("subs", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.IMM12), Arm64EncodingInfo(0x71000000L, Arm64Format.REG2_IMM12), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("adc", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9A000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("adc", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x1A000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("adcs", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xBA000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("adcs", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x3A000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sbc", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xDA000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sbc", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x5A000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sbcs", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xFA000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sbcs", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x7A000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("neg", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xCB0003E0L, Arm64Format.RD_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("neg", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x4B0003E0L, Arm64Format.RD_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("negs", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xEB0003E0L, Arm64Format.RD_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("negs", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x6B0003E0L, Arm64Format.RD_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("and", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x8A000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("and", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x0A000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ands", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xEA000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ands", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x6A000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("orr", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xAA000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("orr", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x2A000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("orn", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xAA200000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("orn", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x2A200000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("eor", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xCA000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("eor", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x4A000000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("eon", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xCA200000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("eon", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x4A200000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("bic", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x8A200000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("bic", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x0A200000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("bics", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xEA200000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("bics", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x6A200000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("mvn", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xAA2003E0L, Arm64Format.RD_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("mvn", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x2A2003E0L, Arm64Format.RD_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("lsl", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9AC02000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("lsl", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x1AC02000L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("lsr", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9AC02400L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("lsr", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x1AC02400L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("asr", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9AC02800L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("asr", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x1AC02800L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ror", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9AC02C00L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ror", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x1AC02C00L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("mul", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9B007C00L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("mul", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x1B007C00L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("madd", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9B000000L, Arm64Format.REG4), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("madd", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x1B000000L, Arm64Format.REG4), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("msub", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9B008000L, Arm64Format.REG4), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("msub", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x1B008000L, Arm64Format.REG4), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("mneg", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9B00FC00L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("mneg", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x1B00FC00L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("smulh", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9B407C00L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("umulh", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9BC07C00L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("smaddl", listOf(Arm64OperandType.X, Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.X), Arm64EncodingInfo(0x9B200000L, Arm64Format.REG4), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("umaddl", listOf(Arm64OperandType.X, Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.X), Arm64EncodingInfo(0x9BA00000L, Arm64Format.REG4), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sdiv", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9AC00C00L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sdiv", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x1AC00C00L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("udiv", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0x9AC00800L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("udiv", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x1AC00800L, Arm64Format.REG3), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("mov", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xAA0003E0L, Arm64Format.RD_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("mov", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x2A0003E0L, Arm64Format.RD_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("movz", listOf(Arm64OperandType.X, Arm64OperandType.IMM16), Arm64EncodingInfo(0xD2800000L, Arm64Format.MOVE_WIDE), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("movz", listOf(Arm64OperandType.W, Arm64OperandType.IMM16), Arm64EncodingInfo(0x52800000L, Arm64Format.MOVE_WIDE), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("movk", listOf(Arm64OperandType.X, Arm64OperandType.IMM16), Arm64EncodingInfo(0xF2800000L, Arm64Format.MOVE_WIDE), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("movk", listOf(Arm64OperandType.W, Arm64OperandType.IMM16), Arm64EncodingInfo(0x72800000L, Arm64Format.MOVE_WIDE), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("movn", listOf(Arm64OperandType.X, Arm64OperandType.IMM16), Arm64EncodingInfo(0x92800000L, Arm64Format.MOVE_WIDE), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("movn", listOf(Arm64OperandType.W, Arm64OperandType.IMM16), Arm64EncodingInfo(0x12800000L, Arm64Format.MOVE_WIDE), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sxtw", listOf(Arm64OperandType.X, Arm64OperandType.W), Arm64EncodingInfo(0x93407C00L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sxth", listOf(Arm64OperandType.X, Arm64OperandType.W), Arm64EncodingInfo(0x93403C00L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sxth", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x13003C00L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sxtb", listOf(Arm64OperandType.X, Arm64OperandType.W), Arm64EncodingInfo(0x93401C00L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("sxtb", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x13001C00L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("uxtb", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x53001C00L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("uxth", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x53003C00L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cls", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xDAC01400L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cls", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x5AC01400L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("clz", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xDAC01000L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("clz", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x5AC01000L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("rbit", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xDAC00000L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("rbit", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x5AC00000L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("rev", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xDAC00C00L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("rev", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x5AC00800L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("rev16", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xDAC00400L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("rev16", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x5AC00400L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("rev32", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xDAC00800L, Arm64Format.REG2), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cmp", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xEB00001FL, Arm64Format.RN_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cmp", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x6B00001FL, Arm64Format.RN_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cmp", listOf(Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xF100001FL, Arm64Format.RN_IMM12), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cmp", listOf(Arm64OperandType.W, Arm64OperandType.IMM12), Arm64EncodingInfo(0x7100001FL, Arm64Format.RN_IMM12), Arm64Feature.BASELINE))
    }

    private fun buildForms1(list: MutableList<Arm64InstructionForm>) {
        list.add(Arm64InstructionForm("cmn", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xAB00001FL, Arm64Format.RN_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cmn", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x2B00001FL, Arm64Format.RN_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("tst", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xEA00001FL, Arm64Format.RN_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("tst", listOf(Arm64OperandType.W, Arm64OperandType.W), Arm64EncodingInfo(0x6A00001FL, Arm64Format.RN_RM), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("csel", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.COND), Arm64EncodingInfo(0x9A800000L, Arm64Format.CSEL), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("csel", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.COND), Arm64EncodingInfo(0x1A800000L, Arm64Format.CSEL), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("csinc", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.COND), Arm64EncodingInfo(0x9A800400L, Arm64Format.CSEL), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("csinc", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.COND), Arm64EncodingInfo(0x1A800400L, Arm64Format.CSEL), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("csinv", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.COND), Arm64EncodingInfo(0xDA800000L, Arm64Format.CSEL), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("csinv", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.COND), Arm64EncodingInfo(0x5A800000L, Arm64Format.CSEL), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("csneg", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.COND), Arm64EncodingInfo(0xDA800400L, Arm64Format.CSEL), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("csneg", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.COND), Arm64EncodingInfo(0x5A800400L, Arm64Format.CSEL), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cset", listOf(Arm64OperandType.X, Arm64OperandType.COND), Arm64EncodingInfo(0x9A9F07E0L, Arm64Format.RD_COND), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cset", listOf(Arm64OperandType.W, Arm64OperandType.COND), Arm64EncodingInfo(0x1A9F07E0L, Arm64Format.RD_COND), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("csetm", listOf(Arm64OperandType.X, Arm64OperandType.COND), Arm64EncodingInfo(0xDA9F03E0L, Arm64Format.RD_COND), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("csetm", listOf(Arm64OperandType.W, Arm64OperandType.COND), Arm64EncodingInfo(0x5A9F03E0L, Arm64Format.RD_COND), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("b", listOf(Arm64OperandType.LABEL), Arm64EncodingInfo(0x14000000L, Arm64Format.BRANCH26), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("bl", listOf(Arm64OperandType.LABEL), Arm64EncodingInfo(0x94000000L, Arm64Format.BRANCH26), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("bCond", listOf(Arm64OperandType.COND, Arm64OperandType.LABEL), Arm64EncodingInfo(0x54000000L, Arm64Format.BRANCH_COND), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cbz", listOf(Arm64OperandType.X, Arm64OperandType.LABEL), Arm64EncodingInfo(0xB4000000L, Arm64Format.CBRANCH), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cbz", listOf(Arm64OperandType.W, Arm64OperandType.LABEL), Arm64EncodingInfo(0x34000000L, Arm64Format.CBRANCH), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cbnz", listOf(Arm64OperandType.X, Arm64OperandType.LABEL), Arm64EncodingInfo(0xB5000000L, Arm64Format.CBRANCH), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("cbnz", listOf(Arm64OperandType.W, Arm64OperandType.LABEL), Arm64EncodingInfo(0x35000000L, Arm64Format.CBRANCH), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("tbz", listOf(Arm64OperandType.X, Arm64OperandType.IMM, Arm64OperandType.LABEL), Arm64EncodingInfo(0x36000000L, Arm64Format.TBRANCH), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("tbz", listOf(Arm64OperandType.W, Arm64OperandType.IMM, Arm64OperandType.LABEL), Arm64EncodingInfo(0x36000000L, Arm64Format.TBRANCH), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("tbnz", listOf(Arm64OperandType.X, Arm64OperandType.IMM, Arm64OperandType.LABEL), Arm64EncodingInfo(0x37000000L, Arm64Format.TBRANCH), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("tbnz", listOf(Arm64OperandType.W, Arm64OperandType.IMM, Arm64OperandType.LABEL), Arm64EncodingInfo(0x37000000L, Arm64Format.TBRANCH), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("br", listOf(Arm64OperandType.X), Arm64EncodingInfo(0xD61F0000L, Arm64Format.REG1), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("blr", listOf(Arm64OperandType.X), Arm64EncodingInfo(0xD63F0000L, Arm64Format.REG1), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ret", listOf(Arm64OperandType.X), Arm64EncodingInfo(0xD65F0000L, Arm64Format.REG1), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldr", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xF9400000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldr", listOf(Arm64OperandType.W, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xB9400000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("str", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xF9000000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("str", listOf(Arm64OperandType.W, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xB9000000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldur", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0xF8400000L, Arm64Format.LDST_SIMM9), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldur", listOf(Arm64OperandType.W, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0xB8400000L, Arm64Format.LDST_SIMM9), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("stur", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0xF8000000L, Arm64Format.LDST_SIMM9), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("stur", listOf(Arm64OperandType.W, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0xB8000000L, Arm64Format.LDST_SIMM9), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldrb", listOf(Arm64OperandType.W, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0x39400000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("strb", listOf(Arm64OperandType.W, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0x39000000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldrh", listOf(Arm64OperandType.W, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0x79400000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("strh", listOf(Arm64OperandType.W, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0x79000000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldrsb", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0x39800000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldrsb", listOf(Arm64OperandType.W, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0x39C00000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldrsh", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0x79800000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldrsh", listOf(Arm64OperandType.W, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0x79C00000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldrsw", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xB9800000L, Arm64Format.LDST_UOFF), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("stp", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0xA9000000L, Arm64Format.LDST_PAIR), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("stp", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0x29000000L, Arm64Format.LDST_PAIR), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldp", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0xA9400000L, Arm64Format.LDST_PAIR), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldp", listOf(Arm64OperandType.W, Arm64OperandType.W, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0x29400000L, Arm64Format.LDST_PAIR), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("stpPre", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0xA9800000L, Arm64Format.LDST_PAIR), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldpPost", listOf(Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0xA8C00000L, Arm64Format.LDST_PAIR), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldar", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xC8DFFC00L, Arm64Format.LDST_ACQUIRE), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("ldar", listOf(Arm64OperandType.W, Arm64OperandType.X), Arm64EncodingInfo(0x88DFFC00L, Arm64Format.LDST_ACQUIRE), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("stlr", listOf(Arm64OperandType.X, Arm64OperandType.X), Arm64EncodingInfo(0xC89FFC00L, Arm64Format.LDST_ACQUIRE), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("stlr", listOf(Arm64OperandType.W, Arm64OperandType.X), Arm64EncodingInfo(0x889FFC00L, Arm64Format.LDST_ACQUIRE), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("fadd", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1E602800L, Arm64Format.REG3), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fadd", listOf(Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1E202800L, Arm64Format.REG3), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fsub", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1E603800L, Arm64Format.REG3), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fsub", listOf(Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1E203800L, Arm64Format.REG3), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmul", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1E600800L, Arm64Format.REG3), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmul", listOf(Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1E200800L, Arm64Format.REG3), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fdiv", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1E601800L, Arm64Format.REG3), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fdiv", listOf(Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1E201800L, Arm64Format.REG3), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fneg", listOf(Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1E614000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fneg", listOf(Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1E214000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fabs", listOf(Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1E60C000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fabs", listOf(Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1E20C000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fsqrt", listOf(Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1E61C000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fsqrt", listOf(Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1E21C000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmadd", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1F400000L, Arm64Format.REG4), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmadd", listOf(Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1F000000L, Arm64Format.REG4), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmsub", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1F408000L, Arm64Format.REG4), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmsub", listOf(Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1F008000L, Arm64Format.REG4), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmov", listOf(Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1E604000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmov", listOf(Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1E204000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmov", listOf(Arm64OperandType.D, Arm64OperandType.X), Arm64EncodingInfo(0x9E670000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmov", listOf(Arm64OperandType.S, Arm64OperandType.W), Arm64EncodingInfo(0x1E270000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmov", listOf(Arm64OperandType.X, Arm64OperandType.D), Arm64EncodingInfo(0x9E660000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmov", listOf(Arm64OperandType.W, Arm64OperandType.S), Arm64EncodingInfo(0x1E260000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcmp", listOf(Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1E602000L, Arm64Format.FP_CMP), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcmp", listOf(Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1E202000L, Arm64Format.FP_CMP), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcmpZero", listOf(Arm64OperandType.D), Arm64EncodingInfo(0x1E602008L, Arm64Format.FP_CMP_ZERO), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcmpZero", listOf(Arm64OperandType.S), Arm64EncodingInfo(0x1E202008L, Arm64Format.FP_CMP_ZERO), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcsel", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.COND), Arm64EncodingInfo(0x1E600C00L, Arm64Format.CSEL), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcsel", listOf(Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.COND), Arm64EncodingInfo(0x1E200C00L, Arm64Format.CSEL), Arm64Feature.FP))
        list.add(Arm64InstructionForm("scvtf", listOf(Arm64OperandType.D, Arm64OperandType.X), Arm64EncodingInfo(0x9E620000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("scvtf", listOf(Arm64OperandType.D, Arm64OperandType.W), Arm64EncodingInfo(0x1E620000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("scvtf", listOf(Arm64OperandType.S, Arm64OperandType.W), Arm64EncodingInfo(0x1E220000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("scvtf", listOf(Arm64OperandType.S, Arm64OperandType.X), Arm64EncodingInfo(0x9E220000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("ucvtf", listOf(Arm64OperandType.D, Arm64OperandType.X), Arm64EncodingInfo(0x9E630000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("ucvtf", listOf(Arm64OperandType.D, Arm64OperandType.W), Arm64EncodingInfo(0x1E630000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("ucvtf", listOf(Arm64OperandType.S, Arm64OperandType.W), Arm64EncodingInfo(0x1E230000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("ucvtf", listOf(Arm64OperandType.S, Arm64OperandType.X), Arm64EncodingInfo(0x9E230000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcvtzs", listOf(Arm64OperandType.X, Arm64OperandType.D), Arm64EncodingInfo(0x9E780000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcvtzs", listOf(Arm64OperandType.W, Arm64OperandType.S), Arm64EncodingInfo(0x1E380000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcvtzs", listOf(Arm64OperandType.W, Arm64OperandType.D), Arm64EncodingInfo(0x1E780000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcvtzs", listOf(Arm64OperandType.X, Arm64OperandType.S), Arm64EncodingInfo(0x9E380000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcvtzu", listOf(Arm64OperandType.X, Arm64OperandType.D), Arm64EncodingInfo(0x9E790000L, Arm64Format.REG2), Arm64Feature.FP))
    }

    private fun buildForms2(list: MutableList<Arm64InstructionForm>) {
        list.add(Arm64InstructionForm("fcvtzu", listOf(Arm64OperandType.W, Arm64OperandType.S), Arm64EncodingInfo(0x1E390000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcvtzu", listOf(Arm64OperandType.W, Arm64OperandType.D), Arm64EncodingInfo(0x1E790000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcvtzu", listOf(Arm64OperandType.X, Arm64OperandType.S), Arm64EncodingInfo(0x9E390000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcvt", listOf(Arm64OperandType.D, Arm64OperandType.S), Arm64EncodingInfo(0x1E22C000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fcvt", listOf(Arm64OperandType.S, Arm64OperandType.D), Arm64EncodingInfo(0x1E624000L, Arm64Format.REG2), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fldr", listOf(Arm64OperandType.D, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xFD400000L, Arm64Format.LDST_UOFF), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fldr", listOf(Arm64OperandType.S, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xBD400000L, Arm64Format.LDST_UOFF), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fstr", listOf(Arm64OperandType.D, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xFD000000L, Arm64Format.LDST_UOFF), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fstr", listOf(Arm64OperandType.S, Arm64OperandType.X, Arm64OperandType.IMM12), Arm64EncodingInfo(0xBD000000L, Arm64Format.LDST_UOFF), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fldur", listOf(Arm64OperandType.D, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0xFC400000L, Arm64Format.LDST_SIMM9), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fstur", listOf(Arm64OperandType.D, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0xFC000000L, Arm64Format.LDST_SIMM9), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fstp", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0x6D000000L, Arm64Format.LDST_PAIR), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fldp", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0x6D400000L, Arm64Format.LDST_PAIR), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fstpPre", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0x6D800000L, Arm64Format.LDST_PAIR), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fldpPost", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.X, Arm64OperandType.IMM), Arm64EncodingInfo(0x6CC00000L, Arm64Format.LDST_PAIR), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmin", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1E605800L, Arm64Format.REG3), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmin", listOf(Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1E205800L, Arm64Format.REG3), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmax", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1E604800L, Arm64Format.REG3), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fmax", listOf(Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1E204800L, Arm64Format.REG3), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fnmadd", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1F600000L, Arm64Format.REG4), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fnmadd", listOf(Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1F200000L, Arm64Format.REG4), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fnmsub", listOf(Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D, Arm64OperandType.D), Arm64EncodingInfo(0x1F608000L, Arm64Format.REG4), Arm64Feature.FP))
        list.add(Arm64InstructionForm("fnmsub", listOf(Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S, Arm64OperandType.S), Arm64EncodingInfo(0x1F208000L, Arm64Format.REG4), Arm64Feature.FP))
        list.add(Arm64InstructionForm("nop", listOf(), Arm64EncodingInfo(0xD503201FL, Arm64Format.NO_OPERAND), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("brk", listOf(Arm64OperandType.IMM16), Arm64EncodingInfo(0xD4200000L, Arm64Format.EXCEPTION), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("svc", listOf(Arm64OperandType.IMM16), Arm64EncodingInfo(0xD4000001L, Arm64Format.EXCEPTION), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("hlt", listOf(Arm64OperandType.IMM16), Arm64EncodingInfo(0xD4400000L, Arm64Format.EXCEPTION), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("adr", listOf(Arm64OperandType.X, Arm64OperandType.LABEL), Arm64EncodingInfo(0x10000000L, Arm64Format.ADR), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("adrp", listOf(Arm64OperandType.X, Arm64OperandType.LABEL), Arm64EncodingInfo(0x90000000L, Arm64Format.ADR), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("clrex", listOf(), Arm64EncodingInfo(0xD503305FL, Arm64Format.NO_OPERAND), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("isb", listOf(), Arm64EncodingInfo(0xD5033FDFL, Arm64Format.NO_OPERAND), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("dsb", listOf(), Arm64EncodingInfo(0xD503309FL, Arm64Format.NO_OPERAND), Arm64Feature.BASELINE))
        list.add(Arm64InstructionForm("dmb", listOf(), Arm64EncodingInfo(0xD50330BFL, Arm64Format.NO_OPERAND), Arm64Feature.BASELINE))
    }
}
