// Generated — do not edit
package org.kgen.target.riscv

/**
 * Generated assembler dispatch methods for RISC-V (RV64IMAFDC).
 *
 * Each method corresponds to a RISC-V instruction mnemonic.
 * Overloads select the correct encoding based on operand types.
 * The abstract [encodeRiscV] method is implemented by the hand-written assembler.
 */
abstract class RiscVAssemblerOps {

    /** Encode and emit a single RISC-V instruction. Implemented by the assembler. */
    protected abstract fun encodeRiscV(info: RiscVEncodingInfo, vararg operands: Any)

    /** Add: gp, gp, gp */
    fun add(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 0, funct7 = 0, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Subtract: gp, gp, gp */
    fun sub(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 0, funct7 = 32, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Shift left logical: gp, gp, gp */
    fun sll(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 1, funct7 = 0, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Set less than (signed): gp, gp, gp */
    fun slt(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 2, funct7 = 0, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Set less than (unsigned): gp, gp, gp */
    fun sltu(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 3, funct7 = 0, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Bitwise XOR: gp, gp, gp */
    fun xor_(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 4, funct7 = 0, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Shift right logical: gp, gp, gp */
    fun srl(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 5, funct7 = 0, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Shift right arithmetic: gp, gp, gp */
    fun sra(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 5, funct7 = 32, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Bitwise OR: gp, gp, gp */
    fun or_(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 6, funct7 = 0, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Bitwise AND: gp, gp, gp */
    fun and_(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 7, funct7 = 0, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Add word (32-bit): gp, gp, gp */
    fun addw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 59, funct3 = 0, funct7 = 0, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Subtract word (32-bit): gp, gp, gp */
    fun subw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 59, funct3 = 0, funct7 = 32, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Shift left logical word (32-bit): gp, gp, gp */
    fun sllw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 59, funct3 = 1, funct7 = 0, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Shift right logical word (32-bit): gp, gp, gp */
    fun srlw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 59, funct3 = 5, funct7 = 0, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Shift right arithmetic word (32-bit): gp, gp, gp */
    fun sraw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 59, funct3 = 5, funct7 = 32, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Add immediate: gp, gp, imm12 */
    fun addi(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 19, funct3 = 0, format = RiscVFormat.I), rd, rs1, imm)
    }

    /** Set less than immediate (signed): gp, gp, imm12 */
    fun slti(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 19, funct3 = 2, format = RiscVFormat.I), rd, rs1, imm)
    }

    /** Set less than immediate (unsigned): gp, gp, imm12 */
    fun sltiu(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 19, funct3 = 3, format = RiscVFormat.I), rd, rs1, imm)
    }

    /** Bitwise XOR immediate: gp, gp, imm12 */
    fun xori(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 19, funct3 = 4, format = RiscVFormat.I), rd, rs1, imm)
    }

    /** Bitwise OR immediate: gp, gp, imm12 */
    fun ori(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 19, funct3 = 6, format = RiscVFormat.I), rd, rs1, imm)
    }

    /** Bitwise AND immediate: gp, gp, imm12 */
    fun andi(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 19, funct3 = 7, format = RiscVFormat.I), rd, rs1, imm)
    }

    /** Shift left logical immediate: gp, gp, shamt */
    fun slli(rd: RiscVGpReg, rs1: RiscVGpReg, shamt: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 19, funct3 = 1, funct7 = 0, format = RiscVFormat.I_SHIFT), rd, rs1, shamt)
    }

    /** Shift right logical immediate: gp, gp, shamt */
    fun srli(rd: RiscVGpReg, rs1: RiscVGpReg, shamt: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 19, funct3 = 5, funct7 = 0, format = RiscVFormat.I_SHIFT), rd, rs1, shamt)
    }

    /** Shift right arithmetic immediate: gp, gp, shamt */
    fun srai(rd: RiscVGpReg, rs1: RiscVGpReg, shamt: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 19, funct3 = 5, funct7 = 16, format = RiscVFormat.I_SHIFT), rd, rs1, shamt)
    }

    /** Add immediate word (32-bit): gp, gp, imm12 */
    fun addiw(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 27, funct3 = 0, format = RiscVFormat.I), rd, rs1, imm)
    }

    /** Shift left logical immediate word (32-bit): gp, gp, shamt */
    fun slliw(rd: RiscVGpReg, rs1: RiscVGpReg, shamt: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 27, funct3 = 1, funct7 = 0, format = RiscVFormat.I_SHIFT), rd, rs1, shamt)
    }

    /** Shift right logical immediate word (32-bit): gp, gp, shamt */
    fun srliw(rd: RiscVGpReg, rs1: RiscVGpReg, shamt: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 27, funct3 = 5, funct7 = 0, format = RiscVFormat.I_SHIFT), rd, rs1, shamt)
    }

    /** Shift right arithmetic immediate word (32-bit): gp, gp, shamt */
    fun sraiw(rd: RiscVGpReg, rs1: RiscVGpReg, shamt: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 27, funct3 = 5, funct7 = 16, format = RiscVFormat.I_SHIFT), rd, rs1, shamt)
    }

    /** Load byte (sign-extend): gp, mem */
    fun lb(rd: RiscVGpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 3, funct3 = 0, format = RiscVFormat.I_LOAD), rd, mem)
    }

    /** Load halfword (sign-extend): gp, mem */
    fun lh(rd: RiscVGpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 3, funct3 = 1, format = RiscVFormat.I_LOAD), rd, mem)
    }

    /** Load word (sign-extend): gp, mem */
    fun lw(rd: RiscVGpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 3, funct3 = 2, format = RiscVFormat.I_LOAD), rd, mem)
    }

    /** Load doubleword: gp, mem */
    fun ld(rd: RiscVGpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 3, funct3 = 3, format = RiscVFormat.I_LOAD), rd, mem)
    }

    /** Load byte (zero-extend): gp, mem */
    fun lbu(rd: RiscVGpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 3, funct3 = 4, format = RiscVFormat.I_LOAD), rd, mem)
    }

    /** Load halfword (zero-extend): gp, mem */
    fun lhu(rd: RiscVGpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 3, funct3 = 5, format = RiscVFormat.I_LOAD), rd, mem)
    }

    /** Load word (zero-extend): gp, mem */
    fun lwu(rd: RiscVGpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 3, funct3 = 6, format = RiscVFormat.I_LOAD), rd, mem)
    }

    /** Store byte: gp, mem */
    fun sb(rd: RiscVGpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 35, funct3 = 0, format = RiscVFormat.S), rd, mem)
    }

    /** Store halfword: gp, mem */
    fun sh(rd: RiscVGpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 35, funct3 = 1, format = RiscVFormat.S), rd, mem)
    }

    /** Store word: gp, mem */
    fun sw(rd: RiscVGpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 35, funct3 = 2, format = RiscVFormat.S), rd, mem)
    }

    /** Store doubleword: gp, mem */
    fun sd(rd: RiscVGpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 35, funct3 = 3, format = RiscVFormat.S), rd, mem)
    }

    /** Branch if equal: gp, gp, imm12 */
    fun beq(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 99, funct3 = 0, format = RiscVFormat.B), rd, rs1, imm)
    }

    /** Branch if equal: gp, gp, label */
    fun beq(rd: RiscVGpReg, rs1: RiscVGpReg, label: String) {
        encodeRiscV(RiscVEncodingInfo(opcode = 99, funct3 = 0, format = RiscVFormat.B_LABEL), rd, rs1, label)
    }

    /** Branch if not equal: gp, gp, imm12 */
    fun bne(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 99, funct3 = 1, format = RiscVFormat.B), rd, rs1, imm)
    }

    /** Branch if not equal: gp, gp, label */
    fun bne(rd: RiscVGpReg, rs1: RiscVGpReg, label: String) {
        encodeRiscV(RiscVEncodingInfo(opcode = 99, funct3 = 1, format = RiscVFormat.B_LABEL), rd, rs1, label)
    }

    /** Branch if less than (signed): gp, gp, imm12 */
    fun blt(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 99, funct3 = 4, format = RiscVFormat.B), rd, rs1, imm)
    }

    /** Branch if less than (signed): gp, gp, label */
    fun blt(rd: RiscVGpReg, rs1: RiscVGpReg, label: String) {
        encodeRiscV(RiscVEncodingInfo(opcode = 99, funct3 = 4, format = RiscVFormat.B_LABEL), rd, rs1, label)
    }

    /** Branch if greater than or equal (signed): gp, gp, imm12 */
    fun bge(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 99, funct3 = 5, format = RiscVFormat.B), rd, rs1, imm)
    }

    /** Branch if greater than or equal (signed): gp, gp, label */
    fun bge(rd: RiscVGpReg, rs1: RiscVGpReg, label: String) {
        encodeRiscV(RiscVEncodingInfo(opcode = 99, funct3 = 5, format = RiscVFormat.B_LABEL), rd, rs1, label)
    }

    /** Branch if less than (unsigned): gp, gp, imm12 */
    fun bltu(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 99, funct3 = 6, format = RiscVFormat.B), rd, rs1, imm)
    }

    /** Branch if less than (unsigned): gp, gp, label */
    fun bltu(rd: RiscVGpReg, rs1: RiscVGpReg, label: String) {
        encodeRiscV(RiscVEncodingInfo(opcode = 99, funct3 = 6, format = RiscVFormat.B_LABEL), rd, rs1, label)
    }

    /** Branch if greater than or equal (unsigned): gp, gp, imm12 */
    fun bgeu(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 99, funct3 = 7, format = RiscVFormat.B), rd, rs1, imm)
    }

    /** Branch if greater than or equal (unsigned): gp, gp, label */
    fun bgeu(rd: RiscVGpReg, rs1: RiscVGpReg, label: String) {
        encodeRiscV(RiscVEncodingInfo(opcode = 99, funct3 = 7, format = RiscVFormat.B_LABEL), rd, rs1, label)
    }

    /** Jump and link: gp, imm */
    fun jal(rd: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 111, format = RiscVFormat.J), rd, imm)
    }

    /** Jump and link: gp, label */
    fun jal(rd: RiscVGpReg, label: String) {
        encodeRiscV(RiscVEncodingInfo(opcode = 111, format = RiscVFormat.J_LABEL), rd, label)
    }

    /** Jump and link register: gp, gp, imm12 */
    fun jalr(rd: RiscVGpReg, rs1: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 103, funct3 = 0, format = RiscVFormat.I), rd, rs1, imm)
    }

    /** Load upper immediate: gp, imm */
    fun lui(rd: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 55, format = RiscVFormat.U), rd, imm)
    }

    /** Add upper immediate to PC: gp, imm */
    fun auipc(rd: RiscVGpReg, imm: Int) {
        encodeRiscV(RiscVEncodingInfo(opcode = 23, format = RiscVFormat.U), rd, imm)
    }

    /** Environment call:  */
    fun ecall() {
        encodeRiscV(RiscVEncodingInfo(opcode = 115, funct3 = 0, funct7 = 0, format = RiscVFormat.SYSTEM))
    }

    /** Environment breakpoint:  */
    fun ebreak() {
        encodeRiscV(RiscVEncodingInfo(opcode = 115, funct3 = 0, funct7 = 0, immFixed = 1, format = RiscVFormat.SYSTEM))
    }

    /** Memory fence:  */
    fun fence() {
        encodeRiscV(RiscVEncodingInfo(opcode = 15, funct3 = 0, format = RiscVFormat.SYSTEM))
    }

    /** Instruction fence:  */
    fun fenceI() {
        encodeRiscV(RiscVEncodingInfo(opcode = 15, funct3 = 1, format = RiscVFormat.SYSTEM))
    }

    /** Multiply: gp, gp, gp */
    fun mul(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 0, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Multiply high (signed x signed): gp, gp, gp */
    fun mulh(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 1, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Multiply high (signed x unsigned): gp, gp, gp */
    fun mulhsu(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 2, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Multiply high (unsigned x unsigned): gp, gp, gp */
    fun mulhu(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 3, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Divide (signed): gp, gp, gp */
    fun div_(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 4, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Divide (unsigned): gp, gp, gp */
    fun divu(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 5, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Remainder (signed): gp, gp, gp */
    fun rem_(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 6, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Remainder (unsigned): gp, gp, gp */
    fun remu(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 51, funct3 = 7, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Multiply word (32-bit): gp, gp, gp */
    fun mulw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 59, funct3 = 0, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Divide word (signed, 32-bit): gp, gp, gp */
    fun divw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 59, funct3 = 4, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Divide word (unsigned, 32-bit): gp, gp, gp */
    fun divuw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 59, funct3 = 5, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Remainder word (signed, 32-bit): gp, gp, gp */
    fun remw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 59, funct3 = 6, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Remainder word (unsigned, 32-bit): gp, gp, gp */
    fun remuw(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 59, funct3 = 7, funct7 = 1, format = RiscVFormat.R), rd, rs1, rs2)
    }

    /** Load single-precision float: fp, mem */
    fun flw(fd: RiscVFpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 7, funct3 = 2, format = RiscVFormat.I_LOAD), fd, mem)
    }

    /** Store single-precision float: fp, mem */
    fun fsw(fd: RiscVFpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 39, funct3 = 2, format = RiscVFormat.S), fd, mem)
    }

    /** Single-precision add: fp, fp, fp */
    fun faddS(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 0, fmt = 0, format = RiscVFormat.R_FP), fd, fs1, fs2)
    }

    /** Single-precision subtract: fp, fp, fp */
    fun fsubS(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 4, fmt = 0, format = RiscVFormat.R_FP), fd, fs1, fs2)
    }

    /** Single-precision multiply: fp, fp, fp */
    fun fmulS(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 8, fmt = 0, format = RiscVFormat.R_FP), fd, fs1, fs2)
    }

    /** Single-precision divide: fp, fp, fp */
    fun fdivS(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 12, fmt = 0, format = RiscVFormat.R_FP), fd, fs1, fs2)
    }

    /** Single-precision square root: fp, fp */
    fun fsqrtS(fd: RiscVFpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 44, fmt = 0, format = RiscVFormat.R_FP_UNARY), fd, fs1)
    }

    /** Single-precision sign inject: fp, fp, fp */
    fun fsgnjS(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 16, fmt = 0, format = RiscVFormat.R_FP_NOROUND), fd, fs1, fs2)
    }

    /** Single-precision sign inject negate: fp, fp, fp */
    fun fsgnjnS(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 16, fmt = 0, format = RiscVFormat.R_FP_NOROUND), fd, fs1, fs2)
    }

    /** Single-precision sign inject XOR: fp, fp, fp */
    fun fsgnjxS(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 2, funct5 = 16, fmt = 0, format = RiscVFormat.R_FP_NOROUND), fd, fs1, fs2)
    }

    /** Single-precision minimum: fp, fp, fp */
    fun fminS(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 20, fmt = 0, format = RiscVFormat.R_FP_NOROUND), fd, fs1, fs2)
    }

    /** Single-precision maximum: fp, fp, fp */
    fun fmaxS(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 20, fmt = 0, format = RiscVFormat.R_FP_NOROUND), fd, fs1, fs2)
    }

    /** Single-precision compare equal: gp, fp, fp */
    fun feqS(rd: RiscVGpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 2, funct5 = 80, fmt = 0, format = RiscVFormat.R_FP_CMP), rd, fs1, fs2)
    }

    /** Single-precision compare less than: gp, fp, fp */
    fun fltS(rd: RiscVGpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 80, fmt = 0, format = RiscVFormat.R_FP_CMP), rd, fs1, fs2)
    }

    /** Single-precision compare less than or equal: gp, fp, fp */
    fun fleS(rd: RiscVGpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 80, fmt = 0, format = RiscVFormat.R_FP_CMP), rd, fs1, fs2)
    }

    /** Convert float to signed 32-bit integer: gp, fp */
    fun fcvtWS(rd: RiscVGpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 96, fmt = 0, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), rd, fs1)
    }

    /** Convert float to unsigned 32-bit integer: gp, fp */
    fun fcvtWuS(rd: RiscVGpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 96, fmt = 0, rs2Fixed = 1, format = RiscVFormat.R_FP_CVT), rd, fs1)
    }

    /** Convert float to signed 64-bit integer: gp, fp */
    fun fcvtLS(rd: RiscVGpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 96, fmt = 0, rs2Fixed = 2, format = RiscVFormat.R_FP_CVT), rd, fs1)
    }

    /** Convert float to unsigned 64-bit integer: gp, fp */
    fun fcvtLuS(rd: RiscVGpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 96, fmt = 0, rs2Fixed = 3, format = RiscVFormat.R_FP_CVT), rd, fs1)
    }

    /** Convert signed 32-bit integer to float: fp, gp */
    fun fcvtSW(fd: RiscVFpReg, rs1: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 104, fmt = 0, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), fd, rs1)
    }

    /** Convert unsigned 32-bit integer to float: fp, gp */
    fun fcvtSWu(fd: RiscVFpReg, rs1: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 104, fmt = 0, rs2Fixed = 1, format = RiscVFormat.R_FP_CVT), fd, rs1)
    }

    /** Convert signed 64-bit integer to float: fp, gp */
    fun fcvtSL(fd: RiscVFpReg, rs1: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 104, fmt = 0, rs2Fixed = 2, format = RiscVFormat.R_FP_CVT), fd, rs1)
    }

    /** Convert unsigned 64-bit integer to float: fp, gp */
    fun fcvtSLu(fd: RiscVFpReg, rs1: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 104, fmt = 0, rs2Fixed = 3, format = RiscVFormat.R_FP_CVT), fd, rs1)
    }

    /** Move float bits to integer register: gp, fp */
    fun fmvXW(rd: RiscVGpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 112, fmt = 0, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), rd, fs1)
    }

    /** Move integer bits to float register: fp, gp */
    fun fmvWX(fd: RiscVFpReg, rs1: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 120, fmt = 0, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), fd, rs1)
    }

    /** Classify single-precision float: gp, fp */
    fun fclassS(rd: RiscVGpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 112, fmt = 0, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), rd, fs1)
    }

    /** Load double-precision float: fp, mem */
    fun fld(fd: RiscVFpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 7, funct3 = 3, format = RiscVFormat.I_LOAD), fd, mem)
    }

    /** Store double-precision float: fp, mem */
    fun fsd(fd: RiscVFpReg, mem: RiscVMemory) {
        encodeRiscV(RiscVEncodingInfo(opcode = 39, funct3 = 3, format = RiscVFormat.S), fd, mem)
    }

    /** Double-precision add: fp, fp, fp */
    fun faddD(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 1, fmt = 1, format = RiscVFormat.R_FP), fd, fs1, fs2)
    }

    /** Double-precision subtract: fp, fp, fp */
    fun fsubD(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 5, fmt = 1, format = RiscVFormat.R_FP), fd, fs1, fs2)
    }

    /** Double-precision multiply: fp, fp, fp */
    fun fmulD(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 9, fmt = 1, format = RiscVFormat.R_FP), fd, fs1, fs2)
    }

    /** Double-precision divide: fp, fp, fp */
    fun fdivD(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 13, fmt = 1, format = RiscVFormat.R_FP), fd, fs1, fs2)
    }

    /** Double-precision square root: fp, fp */
    fun fsqrtD(fd: RiscVFpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 45, fmt = 1, format = RiscVFormat.R_FP_UNARY), fd, fs1)
    }

    /** Double-precision sign inject: fp, fp, fp */
    fun fsgnjD(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 17, fmt = 1, format = RiscVFormat.R_FP_NOROUND), fd, fs1, fs2)
    }

    /** Double-precision sign inject negate: fp, fp, fp */
    fun fsgnjnD(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 17, fmt = 1, format = RiscVFormat.R_FP_NOROUND), fd, fs1, fs2)
    }

    /** Double-precision sign inject XOR: fp, fp, fp */
    fun fsgnjxD(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 2, funct5 = 17, fmt = 1, format = RiscVFormat.R_FP_NOROUND), fd, fs1, fs2)
    }

    /** Double-precision minimum: fp, fp, fp */
    fun fminD(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 21, fmt = 1, format = RiscVFormat.R_FP_NOROUND), fd, fs1, fs2)
    }

    /** Double-precision maximum: fp, fp, fp */
    fun fmaxD(fd: RiscVFpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 21, fmt = 1, format = RiscVFormat.R_FP_NOROUND), fd, fs1, fs2)
    }

    /** Convert double to single: fp, fp */
    fun fcvtSD(fd: RiscVFpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 32, fmt = 0, rs2Fixed = 1, format = RiscVFormat.R_FP_CVT), fd, fs1)
    }

    /** Convert single to double: fp, fp */
    fun fcvtDS(fd: RiscVFpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 33, fmt = 1, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), fd, fs1)
    }

    /** Double-precision compare equal: gp, fp, fp */
    fun feqD(rd: RiscVGpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 2, funct5 = 81, fmt = 1, format = RiscVFormat.R_FP_CMP), rd, fs1, fs2)
    }

    /** Double-precision compare less than: gp, fp, fp */
    fun fltD(rd: RiscVGpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 81, fmt = 1, format = RiscVFormat.R_FP_CMP), rd, fs1, fs2)
    }

    /** Double-precision compare less than or equal: gp, fp, fp */
    fun fleD(rd: RiscVGpReg, fs1: RiscVFpReg, fs2: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 81, fmt = 1, format = RiscVFormat.R_FP_CMP), rd, fs1, fs2)
    }

    /** Convert double to signed 32-bit integer: gp, fp */
    fun fcvtWD(rd: RiscVGpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 97, fmt = 1, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), rd, fs1)
    }

    /** Convert double to unsigned 32-bit integer: gp, fp */
    fun fcvtWuD(rd: RiscVGpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 97, fmt = 1, rs2Fixed = 1, format = RiscVFormat.R_FP_CVT), rd, fs1)
    }

    /** Convert double to signed 64-bit integer: gp, fp */
    fun fcvtLD(rd: RiscVGpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 97, fmt = 1, rs2Fixed = 2, format = RiscVFormat.R_FP_CVT), rd, fs1)
    }

    /** Convert double to unsigned 64-bit integer: gp, fp */
    fun fcvtLuD(rd: RiscVGpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 97, fmt = 1, rs2Fixed = 3, format = RiscVFormat.R_FP_CVT), rd, fs1)
    }

    /** Convert signed 32-bit integer to double: fp, gp */
    fun fcvtDW(fd: RiscVFpReg, rs1: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 105, fmt = 1, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), fd, rs1)
    }

    /** Convert unsigned 32-bit integer to double: fp, gp */
    fun fcvtDWu(fd: RiscVFpReg, rs1: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 105, fmt = 1, rs2Fixed = 1, format = RiscVFormat.R_FP_CVT), fd, rs1)
    }

    /** Convert signed 64-bit integer to double: fp, gp */
    fun fcvtDL(fd: RiscVFpReg, rs1: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 105, fmt = 1, rs2Fixed = 2, format = RiscVFormat.R_FP_CVT), fd, rs1)
    }

    /** Convert unsigned 64-bit integer to double: fp, gp */
    fun fcvtDLu(fd: RiscVFpReg, rs1: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct5 = 105, fmt = 1, rs2Fixed = 3, format = RiscVFormat.R_FP_CVT), fd, rs1)
    }

    /** Move double bits to integer register: gp, fp */
    fun fmvXD(rd: RiscVGpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 113, fmt = 1, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), rd, fs1)
    }

    /** Move integer bits to double register: fp, gp */
    fun fmvDX(fd: RiscVFpReg, rs1: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 0, funct5 = 121, fmt = 1, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), fd, rs1)
    }

    /** Classify double-precision float: gp, fp */
    fun fclassD(rd: RiscVGpReg, fs1: RiscVFpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 83, funct3 = 1, funct5 = 113, fmt = 1, rs2Fixed = 0, format = RiscVFormat.R_FP_CVT), rd, fs1)
    }

    /** Load-reserved word: gp, gp */
    fun lrW(rd: RiscVGpReg, rs1: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 2, format = RiscVFormat.R_ATOMIC), rd, rs1)
    }

    /** Store-conditional word: gp, gp, gp */
    fun scW(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 3, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic swap word: gp, gp, gp */
    fun amoswapW(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 1, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic add word: gp, gp, gp */
    fun amoaddW(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 0, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic XOR word: gp, gp, gp */
    fun amoxorW(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 4, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic AND word: gp, gp, gp */
    fun amoandW(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 12, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic OR word: gp, gp, gp */
    fun amoorW(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 8, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic minimum word (signed): gp, gp, gp */
    fun amominW(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 16, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic maximum word (signed): gp, gp, gp */
    fun amomaxW(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 20, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic minimum word (unsigned): gp, gp, gp */
    fun amominuW(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 24, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic maximum word (unsigned): gp, gp, gp */
    fun amomaxuW(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 2, funct5 = 28, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Load-reserved doubleword: gp, gp */
    fun lrD(rd: RiscVGpReg, rs1: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 2, format = RiscVFormat.R_ATOMIC), rd, rs1)
    }

    /** Store-conditional doubleword: gp, gp, gp */
    fun scD(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 3, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic swap doubleword: gp, gp, gp */
    fun amoswapD(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 1, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic add doubleword: gp, gp, gp */
    fun amoaddD(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 0, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic XOR doubleword: gp, gp, gp */
    fun amoxorD(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 4, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic AND doubleword: gp, gp, gp */
    fun amoandD(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 12, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic OR doubleword: gp, gp, gp */
    fun amoorD(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 8, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic minimum doubleword (signed): gp, gp, gp */
    fun amominD(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 16, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic maximum doubleword (signed): gp, gp, gp */
    fun amomaxD(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 20, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic minimum doubleword (unsigned): gp, gp, gp */
    fun amominuD(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 24, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

    /** Atomic maximum doubleword (unsigned): gp, gp, gp */
    fun amomaxuD(rd: RiscVGpReg, rs1: RiscVGpReg, rs2: RiscVGpReg) {
        encodeRiscV(RiscVEncodingInfo(opcode = 47, funct3 = 3, funct5 = 28, format = RiscVFormat.R_ATOMIC), rd, rs1, rs2)
    }

}
