package org.kgen.target.riscv

import org.kgen.target.riscv.asm.RiscVAssembler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RiscVAssemblerComprehensiveTest {

    private fun asm(): RiscVAssembler = RiscVAssembler()

    private fun insn(block: RiscVAssembler.() -> Unit): Int {
        val a = asm()
        a.block()
        val bytes = a.toByteArray()
        assertEquals(4, bytes.size, "Expected single 32-bit instruction")
        return readU32(bytes, 0)
    }

    private fun insn16(block: RiscVAssembler.() -> Unit): Int {
        val a = asm()
        a.block()
        val bytes = a.toByteArray()
        assertEquals(2, bytes.size, "Expected single 16-bit instruction")
        return (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
    }

    private fun readU32(bytes: ByteArray, off: Int): Int =
        (bytes[off].toInt() and 0xFF) or
        ((bytes[off + 1].toInt() and 0xFF) shl 8) or
        ((bytes[off + 2].toInt() and 0xFF) shl 16) or
        ((bytes[off + 3].toInt() and 0xFF) shl 24)

    private fun opcode(i: Int) = i and 0x7F
    private fun rd(i: Int) = (i shr 7) and 0x1F
    private fun funct3(i: Int) = (i shr 12) and 0x7
    private fun rs1(i: Int) = (i shr 15) and 0x1F
    private fun rs2(i: Int) = (i shr 20) and 0x1F
    private fun funct7(i: Int) = (i shr 25) and 0x7F
    private fun funct5(i: Int) = (i shr 27) and 0x1F
    private fun immI(i: Int) = (i shr 20) and 0xFFF

    private fun rType(funct7: Int, rs2: Int, rs1: Int, funct3: Int, rd: Int, opcode: Int): Int =
        (funct7 shl 25) or (rs2 shl 20) or (rs1 shl 15) or (funct3 shl 12) or (rd shl 7) or opcode

    private fun iType(imm: Int, rs1: Int, funct3: Int, rd: Int, opcode: Int): Int =
        ((imm and 0xFFF) shl 20) or (rs1 shl 15) or (funct3 shl 12) or (rd shl 7) or opcode

    private fun sType(imm: Int, rs2: Int, rs1: Int, funct3: Int, opcode: Int): Int {
        val imm11_5 = (imm shr 5) and 0x7F
        val imm4_0 = imm and 0x1F
        return (imm11_5 shl 25) or (rs2 shl 20) or (rs1 shl 15) or (funct3 shl 12) or (imm4_0 shl 7) or opcode
    }

    // R-type: full field verification for add
    @Test
    fun `add full field verification`() {
        val i = insn { add(X1, X2, X3) }
        assertEquals(0x33, opcode(i))
        assertEquals(1, rd(i))
        assertEquals(0, funct3(i))
        assertEquals(2, rs1(i))
        assertEquals(3, rs2(i))
        assertEquals(0x00, funct7(i))
    }

    @Test
    fun `add with X0 as destination`() {
        val i = insn { add(X0, X10, X11) }
        assertEquals(rType(0x00, 11, 10, 0, 0, 0x33), i)
    }

    @Test
    fun `add with X31`() {
        val i = insn { add(X31, X31, X31) }
        assertEquals(rType(0x00, 31, 31, 0, 31, 0x33), i)
    }

    @Test
    fun `sub full field verification`() {
        val i = insn { sub(X15, X20, X25) }
        assertEquals(0x33, opcode(i))
        assertEquals(15, rd(i))
        assertEquals(0, funct3(i))
        assertEquals(20, rs1(i))
        assertEquals(25, rs2(i))
        assertEquals(0x20, funct7(i))
    }

    @Test
    fun `sll full encoding`() {
        val i = insn { sll(X5, X6, X7) }
        assertEquals(rType(0x00, 7, 6, 1, 5, 0x33), i)
    }

    @Test
    fun `slt full encoding`() {
        val i = insn { slt(X14, X15, X16) }
        assertEquals(rType(0x00, 16, 15, 2, 14, 0x33), i)
    }

    @Test
    fun `sltu full encoding`() {
        val i = insn { sltu(X20, X21, X22) }
        assertEquals(rType(0x00, 22, 21, 3, 20, 0x33), i)
    }

    @Test
    fun `xor full encoding`() {
        val i = insn { xor(X8, X9, X10) }
        assertEquals(rType(0x00, 10, 9, 4, 8, 0x33), i)
    }

    @Test
    fun `srl full encoding`() {
        val i = insn { srl(X13, X14, X15) }
        assertEquals(rType(0x00, 15, 14, 5, 13, 0x33), i)
    }

    @Test
    fun `sra full encoding`() {
        val i = insn { sra(X16, X17, X18) }
        assertEquals(rType(0x20, 18, 17, 5, 16, 0x33), i)
    }

    @Test
    fun `or full encoding`() {
        val i = insn { or(X19, X20, X21) }
        assertEquals(rType(0x00, 21, 20, 6, 19, 0x33), i)
    }

    @Test
    fun `and full encoding`() {
        val i = insn { and(X22, X23, X24) }
        assertEquals(rType(0x00, 24, 23, 7, 22, 0x33), i)
    }

    // RV64I word R-type
    @Test
    fun `addw full encoding`() {
        val i = insn { addw(X5, X6, X7) }
        assertEquals(rType(0x00, 7, 6, 0, 5, 0x3B), i)
    }

    @Test
    fun `subw full encoding`() {
        val i = insn { subw(X8, X9, X10) }
        assertEquals(rType(0x20, 10, 9, 0, 8, 0x3B), i)
    }

    @Test
    fun `sllw full encoding`() {
        val i = insn { sllw(X11, X12, X13) }
        assertEquals(rType(0x00, 13, 12, 1, 11, 0x3B), i)
    }

    @Test
    fun `srlw full encoding`() {
        val i = insn { srlw(X14, X15, X16) }
        assertEquals(rType(0x00, 16, 15, 5, 14, 0x3B), i)
    }

    @Test
    fun `sraw full encoding`() {
        val i = insn { sraw(X17, X18, X19) }
        assertEquals(rType(0x20, 19, 18, 5, 17, 0x3B), i)
    }

    // I-type arithmetic
    @Test
    fun `addi full field verification`() {
        val i = insn { addi(X5, X6, 100) }
        assertEquals(0x13, opcode(i))
        assertEquals(5, rd(i))
        assertEquals(0, funct3(i))
        assertEquals(6, rs1(i))
        assertEquals(100, immI(i))
    }

    @Test
    fun `addi max positive immediate`() {
        val i = insn { addi(X10, X11, 2047) }
        assertEquals(iType(2047, 11, 0, 10, 0x13), i)
    }

    @Test
    fun `addi min negative immediate`() {
        val i = insn { addi(X10, X11, -2048) }
        assertEquals(iType(-2048, 11, 0, 10, 0x13), i)
    }

    @Test
    fun `slti full encoding`() {
        val i = insn { slti(X5, X6, -10) }
        assertEquals(iType(-10, 6, 2, 5, 0x13), i)
    }

    @Test
    fun `sltiu full encoding`() {
        val i = insn { sltiu(X7, X8, 255) }
        assertEquals(iType(255, 8, 3, 7, 0x13), i)
    }

    @Test
    fun `xori full encoding`() {
        val i = insn { xori(X9, X10, 0x7FF) }
        assertEquals(iType(0x7FF, 10, 4, 9, 0x13), i)
    }

    @Test
    fun `ori full encoding`() {
        val i = insn { ori(X11, X12, 0x55) }
        assertEquals(iType(0x55, 12, 6, 11, 0x13), i)
    }

    @Test
    fun `andi full encoding`() {
        val i = insn { andi(X13, X14, 0x3F) }
        assertEquals(iType(0x3F, 14, 7, 13, 0x13), i)
    }

    // Shift immediates
    @Test
    fun `slli shamt 0`() {
        val i = insn { slli(X10, X11, 0) }
        assertEquals(iType(0, 11, 1, 10, 0x13), i)
    }

    @Test
    fun `slli shamt 63`() {
        val i = insn { slli(X10, X11, 63) }
        assertEquals(iType(63, 11, 1, 10, 0x13), i)
    }

    @Test
    fun `srli shamt 31`() {
        val i = insn { srli(X10, X11, 31) }
        assertEquals(iType(31, 11, 5, 10, 0x13), i)
    }

    @Test
    fun `srai shamt 1`() {
        val i = insn { srai(X10, X11, 1) }
        assertEquals(iType(0x401, 11, 5, 10, 0x13), i)
    }

    @Test
    fun `srai shamt 63`() {
        val i = insn { srai(X10, X11, 63) }
        assertEquals(iType(0x400 or 63, 11, 5, 10, 0x13), i)
    }

    // RV64I word shift immediates
    @Test
    fun `slliw full encoding`() {
        val i = insn { slliw(X5, X6, 15) }
        assertEquals(iType(15, 6, 1, 5, 0x1B), i)
    }

    @Test
    fun `srliw full encoding`() {
        val i = insn { srliw(X7, X8, 20) }
        assertEquals(iType(20, 8, 5, 7, 0x1B), i)
    }

    @Test
    fun `sraiw full encoding`() {
        val i = insn { sraiw(X9, X10, 10) }
        assertEquals(iType(0x400 or 10, 10, 5, 9, 0x1B), i)
    }

    @Test
    fun `addiw full encoding`() {
        val i = insn { addiw(X12, X13, -500) }
        assertEquals(iType(-500, 13, 0, 12, 0x1B), i)
    }

    // U-type
    @Test
    fun `lui full field verification`() {
        val i = insn { lui(X5, 0xABCDE) }
        assertEquals(0x37, opcode(i))
        assertEquals(5, rd(i))
        assertEquals(0xABCDE, (i shr 12) and 0xFFFFF)
    }

    @Test
    fun `lui with X0`() {
        val i = insn { lui(X0, 0) }
        assertEquals((0 shl 12) or (0 shl 7) or 0x37, i)
    }

    @Test
    fun `auipc full field verification`() {
        val i = insn { auipc(X15, 0x54321) }
        assertEquals(0x17, opcode(i))
        assertEquals(15, rd(i))
        assertEquals(0x54321, (i shr 12) and 0xFFFFF)
    }

    // Loads
    @Test
    fun `lb full encoding`() {
        val i = insn { lb(X5, RiscVMemory(X10, 100)) }
        assertEquals(iType(100, 10, 0, 5, 0x03), i)
    }

    @Test
    fun `lh full encoding`() {
        val i = insn { lh(X6, RiscVMemory(X11, -50)) }
        assertEquals(iType(-50, 11, 1, 6, 0x03), i)
    }

    @Test
    fun `lw full encoding`() {
        val i = insn { lw(X7, RiscVMemory(X12, 2047)) }
        assertEquals(iType(2047, 12, 2, 7, 0x03), i)
    }

    @Test
    fun `ld full encoding`() {
        val i = insn { ld(X8, RiscVMemory(X13, -2048)) }
        assertEquals(iType(-2048, 13, 3, 8, 0x03), i)
    }

    @Test
    fun `lbu full encoding`() {
        val i = insn { lbu(X9, RiscVMemory(X14, 0)) }
        assertEquals(iType(0, 14, 4, 9, 0x03), i)
    }

    @Test
    fun `lhu full encoding`() {
        val i = insn { lhu(X15, RiscVMemory(X16, 128)) }
        assertEquals(iType(128, 16, 5, 15, 0x03), i)
    }

    @Test
    fun `lwu full encoding`() {
        val i = insn { lwu(X17, RiscVMemory(X18, 64)) }
        assertEquals(iType(64, 18, 6, 17, 0x03), i)
    }

    // Stores
    @Test
    fun `sb full encoding`() {
        val i = insn { sb(X5, RiscVMemory(X10, 7)) }
        assertEquals(sType(7, 5, 10, 0, 0x23), i)
    }

    @Test
    fun `sh full encoding`() {
        val i = insn { sh(X6, RiscVMemory(X11, -4)) }
        assertEquals(sType(-4, 6, 11, 1, 0x23), i)
    }

    @Test
    fun `sw full encoding`() {
        val i = insn { sw(X7, RiscVMemory(X12, 2044)) }
        assertEquals(sType(2044, 7, 12, 2, 0x23), i)
    }

    @Test
    fun `sd full encoding`() {
        val i = insn { sd(X8, RiscVMemory(X13, -2048)) }
        assertEquals(sType(-2048, 8, 13, 3, 0x23), i)
    }

    @Test
    fun `sw with zero offset`() {
        val i = insn { sw(X1, RiscVMemory(X2, 0)) }
        assertEquals(sType(0, 1, 2, 2, 0x23), i)
    }

    // Branches with immediate
    @Test
    fun `beq full field verification`() {
        val i = insn { beq(X5, X6, 16) }
        assertEquals(0x63, opcode(i))
        assertEquals(0, funct3(i))
        assertEquals(5, rs1(i))
        assertEquals(6, rs2(i))
    }

    @Test
    fun `bne full encoding`() {
        val i = insn { bne(X7, X8, 32) }
        assertEquals(0x63, opcode(i))
        assertEquals(1, funct3(i))
        assertEquals(7, rs1(i))
        assertEquals(8, rs2(i))
    }

    @Test
    fun `blt full encoding`() {
        val i = insn { blt(X9, X10, 64) }
        assertEquals(0x63, opcode(i))
        assertEquals(4, funct3(i))
    }

    @Test
    fun `bge full encoding`() {
        val i = insn { bge(X11, X12, 128) }
        assertEquals(0x63, opcode(i))
        assertEquals(5, funct3(i))
    }

    @Test
    fun `bltu full encoding`() {
        val i = insn { bltu(X13, X14, 256) }
        assertEquals(0x63, opcode(i))
        assertEquals(6, funct3(i))
    }

    @Test
    fun `bgeu full encoding`() {
        val i = insn { bgeu(X15, X16, 512) }
        assertEquals(0x63, opcode(i))
        assertEquals(7, funct3(i))
    }

    @Test
    fun `beq negative offset`() {
        val i = insn { beq(X10, X0, -8) }
        assertEquals(0x63, opcode(i))
        // bit12 should be 1 for negative
        assertEquals(1, (i shr 31) and 1)
    }

    // B-type encoding bit extraction verification
    @Test
    fun `branch offset 4 encoding`() {
        // offset=4: bit12=0, bits10_5=0, bits4_1=0010, bit11=0
        val i = insn { beq(X10, X11, 4) }
        assertEquals(0x63, opcode(i))
        val bits4_1 = (i shr 8) and 0xF
        assertEquals(2, bits4_1) // offset 4 -> bits[4:1] = 0010
    }

    // JAL / JALR
    @Test
    fun `jal full field verification`() {
        val i = insn { jal(X1, 0) }
        assertEquals(0x6F, opcode(i))
        assertEquals(1, rd(i))
    }

    @Test
    fun `jal large positive offset`() {
        val i = insn { jal(X1, 1024) }
        assertEquals(0x6F, opcode(i))
        assertEquals(1, rd(i))
    }

    @Test
    fun `jal with X0 is j pseudo`() {
        val i = insn { j(0) }
        assertEquals(0x6F, opcode(i))
        assertEquals(0, rd(i))
    }

    @Test
    fun `jalr full field verification`() {
        val i = insn { jalr(X1, X5, 100) }
        assertEquals(0x67, opcode(i))
        assertEquals(1, rd(i))
        assertEquals(0, funct3(i))
        assertEquals(5, rs1(i))
        assertEquals(100, immI(i))
    }

    @Test
    fun `jalr with negative offset`() {
        val i = insn { jalr(X0, X1, -4) }
        assertEquals(0x67, opcode(i))
        assertEquals(0, rd(i))
        assertEquals(1, rs1(i))
        assertEquals((-4) and 0xFFF, immI(i))
    }

    // M Extension
    @Test
    fun `mul full encoding`() {
        val i = insn { mul(X5, X6, X7) }
        assertEquals(rType(0x01, 7, 6, 0, 5, 0x33), i)
    }

    @Test
    fun `mulh full encoding`() {
        val i = insn { mulh(X8, X9, X10) }
        assertEquals(rType(0x01, 10, 9, 1, 8, 0x33), i)
    }

    @Test
    fun `mulhsu full encoding`() {
        val i = insn { mulhsu(X11, X12, X13) }
        assertEquals(rType(0x01, 13, 12, 2, 11, 0x33), i)
    }

    @Test
    fun `mulhu full encoding`() {
        val i = insn { mulhu(X14, X15, X16) }
        assertEquals(rType(0x01, 16, 15, 3, 14, 0x33), i)
    }

    @Test
    fun `div full encoding`() {
        val i = insn { div(X17, X18, X19) }
        assertEquals(rType(0x01, 19, 18, 4, 17, 0x33), i)
    }

    @Test
    fun `divu full encoding`() {
        val i = insn { divu(X20, X21, X22) }
        assertEquals(rType(0x01, 22, 21, 5, 20, 0x33), i)
    }

    @Test
    fun `rem full encoding`() {
        val i = insn { rem(X23, X24, X25) }
        assertEquals(rType(0x01, 25, 24, 6, 23, 0x33), i)
    }

    @Test
    fun `remu full encoding`() {
        val i = insn { remu(X26, X27, X28) }
        assertEquals(rType(0x01, 28, 27, 7, 26, 0x33), i)
    }

    // M Extension word variants (RV64M)
    @Test
    fun `mulw full encoding`() {
        val i = insn { mulw(X5, X6, X7) }
        assertEquals(rType(0x01, 7, 6, 0, 5, 0x3B), i)
    }

    @Test
    fun `divw full encoding`() {
        val i = insn { divw(X8, X9, X10) }
        assertEquals(rType(0x01, 10, 9, 4, 8, 0x3B), i)
    }

    @Test
    fun `divuw full encoding`() {
        val i = insn { divuw(X11, X12, X13) }
        assertEquals(rType(0x01, 13, 12, 5, 11, 0x3B), i)
    }

    @Test
    fun `remw full encoding`() {
        val i = insn { remw(X14, X15, X16) }
        assertEquals(rType(0x01, 16, 15, 6, 14, 0x3B), i)
    }

    @Test
    fun `remuw full encoding`() {
        val i = insn { remuw(X17, X18, X19) }
        assertEquals(rType(0x01, 19, 18, 7, 17, 0x3B), i)
    }

    // A Extension - word atomics
    @Test
    fun `lrW full encoding no aq rl`() {
        val i = insn { lrW(X10, X11) }
        assertEquals(0x2F, opcode(i))
        assertEquals(2, funct3(i))
        assertEquals(10, rd(i))
        assertEquals(11, rs1(i))
        assertEquals(0, rs2(i))
        assertEquals(0x02, funct5(i))
        assertEquals(0, (i shr 25) and 1) // rl
        assertEquals(0, (i shr 26) and 1) // aq
    }

    @Test
    fun `lrW with aq only`() {
        val i = insn { lrW(X10, X11, aq = true) }
        val funct7val = funct7(i)
        assertEquals((0x02 shl 2) or 0x2, funct7val) // aq=1, rl=0
    }

    @Test
    fun `lrW with rl only`() {
        val i = insn { lrW(X10, X11, rl = true) }
        val funct7val = funct7(i)
        assertEquals((0x02 shl 2) or 0x1, funct7val) // aq=0, rl=1
    }

    @Test
    fun `lrW with both aq and rl`() {
        val i = insn { lrW(X10, X11, aq = true, rl = true) }
        val funct7val = funct7(i)
        assertEquals((0x02 shl 2) or 0x3, funct7val)
    }

    @Test
    fun `scW full encoding`() {
        val i = insn { scW(X10, X12, X11) }
        assertEquals(0x2F, opcode(i))
        assertEquals(2, funct3(i))
        assertEquals(10, rd(i))
        assertEquals(11, rs1(i))
        assertEquals(12, rs2(i))
        assertEquals(0x03, funct5(i))
    }

    @Test
    fun `scW with aq rl`() {
        val i = insn { scW(X10, X12, X11, aq = true, rl = true) }
        val funct7val = funct7(i)
        assertEquals((0x03 shl 2) or 0x3, funct7val)
    }

    @Test
    fun `amoswapW full encoding`() {
        val i = insn { amoswapW(X10, X12, X11) }
        assertEquals(0x2F, opcode(i))
        assertEquals(2, funct3(i))
        assertEquals(0x01, funct5(i))
        assertEquals(10, rd(i))
        assertEquals(11, rs1(i))
        assertEquals(12, rs2(i))
    }

    @Test
    fun `amoaddW full encoding`() {
        val i = insn { amoaddW(X10, X12, X11) }
        assertEquals(0x00, funct5(i))
        assertEquals(2, funct3(i))
    }

    @Test
    fun `amoxorW full encoding`() {
        val i = insn { amoxorW(X10, X12, X11) }
        assertEquals(0x04, funct5(i))
    }

    @Test
    fun `amoandW full encoding`() {
        val i = insn { amoandW(X10, X12, X11) }
        assertEquals(0x0C, funct5(i))
    }

    @Test
    fun `amoorW full encoding`() {
        val i = insn { amoorW(X10, X12, X11) }
        assertEquals(0x08, funct5(i))
    }

    @Test
    fun `amominW full encoding`() {
        val i = insn { amominW(X10, X12, X11) }
        assertEquals(0x10, funct5(i))
    }

    @Test
    fun `amomaxW full encoding`() {
        val i = insn { amomaxW(X10, X12, X11) }
        assertEquals(0x14, funct5(i))
    }

    @Test
    fun `amominuW full encoding`() {
        val i = insn { amominuW(X10, X12, X11) }
        assertEquals(0x18, funct5(i))
    }

    @Test
    fun `amomaxuW full encoding`() {
        val i = insn { amomaxuW(X10, X12, X11) }
        assertEquals(0x1C, funct5(i))
    }

    @Test
    fun `amoswapW with aq rl`() {
        val i = insn { amoswapW(X10, X12, X11, aq = true, rl = true) }
        assertEquals(0x01, funct5(i))
        assertEquals((0x01 shl 2) or 0x3, funct7(i))
    }

    // A Extension - doubleword atomics
    @Test
    fun `lrD full encoding`() {
        val i = insn { lrD(X10, X11) }
        assertEquals(0x2F, opcode(i))
        assertEquals(3, funct3(i))
        assertEquals(0x02, funct5(i))
        assertEquals(0, rs2(i))
    }

    @Test
    fun `scD full encoding`() {
        val i = insn { scD(X10, X12, X11) }
        assertEquals(3, funct3(i))
        assertEquals(0x03, funct5(i))
    }

    @Test
    fun `amoswapD full encoding`() {
        val i = insn { amoswapD(X10, X12, X11) }
        assertEquals(3, funct3(i))
        assertEquals(0x01, funct5(i))
    }

    @Test
    fun `amoaddD full encoding`() {
        val i = insn { amoaddD(X10, X12, X11) }
        assertEquals(3, funct3(i))
        assertEquals(0x00, funct5(i))
    }

    @Test
    fun `amoxorD full encoding`() {
        val i = insn { amoxorD(X10, X12, X11) }
        assertEquals(3, funct3(i))
        assertEquals(0x04, funct5(i))
    }

    @Test
    fun `amoandD full encoding`() {
        val i = insn { amoandD(X10, X12, X11) }
        assertEquals(3, funct3(i))
        assertEquals(0x0C, funct5(i))
    }

    @Test
    fun `amoorD full encoding`() {
        val i = insn { amoorD(X10, X12, X11) }
        assertEquals(3, funct3(i))
        assertEquals(0x08, funct5(i))
    }

    @Test
    fun `amominD full encoding`() {
        val i = insn { amominD(X10, X12, X11) }
        assertEquals(3, funct3(i))
        assertEquals(0x10, funct5(i))
    }

    @Test
    fun `amomaxD full encoding`() {
        val i = insn { amomaxD(X10, X12, X11) }
        assertEquals(3, funct3(i))
        assertEquals(0x14, funct5(i))
    }

    @Test
    fun `amominuD full encoding`() {
        val i = insn { amominuD(X10, X12, X11) }
        assertEquals(3, funct3(i))
        assertEquals(0x18, funct5(i))
    }

    @Test
    fun `amomaxuD full encoding`() {
        val i = insn { amomaxuD(X10, X12, X11) }
        assertEquals(3, funct3(i))
        assertEquals(0x1C, funct5(i))
    }

    @Test
    fun `lrD with aq rl`() {
        val i = insn { lrD(X10, X11, aq = true, rl = true) }
        assertEquals(3, funct3(i))
        assertEquals((0x02 shl 2) or 0x3, funct7(i))
    }

    @Test
    fun `scD with aq rl`() {
        val i = insn { scD(X10, X12, X11, aq = true, rl = true) }
        assertEquals((0x03 shl 2) or 0x3, funct7(i))
    }

    @Test
    fun `amomaxuD with aq rl`() {
        val i = insn { amomaxuD(X10, X12, X11, aq = true, rl = true) }
        assertEquals((0x1C shl 2) or 0x3, funct7(i))
    }

    // F Extension - Single precision arithmetic
    @Test
    fun `faddS full encoding`() {
        val i = insn { faddS(F5, F6, F7) }
        assertEquals(0x53, opcode(i))
        assertEquals(5, rd(i))
        assertEquals(7, funct3(i)) // dynamic rounding
        assertEquals(6, rs1(i))
        assertEquals(7, rs2(i))
        assertEquals(0x00, funct7(i))
    }

    @Test
    fun `fsubS full encoding`() {
        val i = insn { fsubS(F8, F9, F10) }
        assertEquals(0x04, funct7(i))
    }

    @Test
    fun `fmulS full encoding`() {
        val i = insn { fmulS(F11, F12, F13) }
        assertEquals(0x08, funct7(i))
    }

    @Test
    fun `fdivS full encoding`() {
        val i = insn { fdivS(F14, F15, F16) }
        assertEquals(0x0C, funct7(i))
    }

    @Test
    fun `fdivS with RNE rounding`() {
        val i = insn { fdivS(F10, F11, F12, rm = 0) }
        assertEquals(0, funct3(i)) // RNE
    }

    @Test
    fun `fdivS with RTZ rounding`() {
        val i = insn { fdivS(F10, F11, F12, rm = 1) }
        assertEquals(1, funct3(i)) // RTZ
    }

    @Test
    fun `fdivS with RDN rounding`() {
        val i = insn { fdivS(F10, F11, F12, rm = 2) }
        assertEquals(2, funct3(i)) // RDN
    }

    @Test
    fun `fdivS with RUP rounding`() {
        val i = insn { fdivS(F10, F11, F12, rm = 3) }
        assertEquals(3, funct3(i)) // RUP
    }

    @Test
    fun `fdivS with RMM rounding`() {
        val i = insn { fdivS(F10, F11, F12, rm = 4) }
        assertEquals(4, funct3(i)) // RMM
    }

    @Test
    fun `fsqrtS full encoding`() {
        val i = insn { fsqrtS(F17, F18) }
        assertEquals(0x2C, funct7(i))
        assertEquals(0, rs2(i))
    }

    // F Extension - sign injection
    @Test
    fun `fsgnjS full encoding`() {
        val i = insn { fsgnjS(F5, F6, F7) }
        assertEquals(0x10, funct7(i))
        assertEquals(0, funct3(i))
    }

    @Test
    fun `fsgnjnS full encoding`() {
        val i = insn { fsgnjnS(F5, F6, F7) }
        assertEquals(0x10, funct7(i))
        assertEquals(1, funct3(i))
    }

    @Test
    fun `fsgnjxS full encoding`() {
        val i = insn { fsgnjxS(F5, F6, F7) }
        assertEquals(0x10, funct7(i))
        assertEquals(2, funct3(i))
    }

    // F Extension - min/max
    @Test
    fun `fminS full encoding`() {
        val i = insn { fminS(F20, F21, F22) }
        assertEquals(0x14, funct7(i))
        assertEquals(0, funct3(i))
    }

    @Test
    fun `fmaxS full encoding`() {
        val i = insn { fmaxS(F23, F24, F25) }
        assertEquals(0x14, funct7(i))
        assertEquals(1, funct3(i))
    }

    // F Extension - comparisons
    @Test
    fun `feqS full encoding`() {
        val i = insn { feqS(X5, F6, F7) }
        assertEquals(0x50, funct7(i))
        assertEquals(2, funct3(i))
        assertEquals(5, rd(i))
    }

    @Test
    fun `fltS full encoding`() {
        val i = insn { fltS(X8, F9, F10) }
        assertEquals(0x50, funct7(i))
        assertEquals(1, funct3(i))
    }

    @Test
    fun `fleS full encoding`() {
        val i = insn { fleS(X11, F12, F13) }
        assertEquals(0x50, funct7(i))
        assertEquals(0, funct3(i))
    }

    // F Extension - conversions
    @Test
    fun `fcvtWS full encoding`() {
        val i = insn { fcvtWS(X10, F11) }
        assertEquals(0x60, funct7(i))
        assertEquals(0, rs2(i))
        assertEquals(7, funct3(i))
    }

    @Test
    fun `fcvtWuS full encoding`() {
        val i = insn { fcvtWuS(X10, F11) }
        assertEquals(0x60, funct7(i))
        assertEquals(1, rs2(i))
    }

    @Test
    fun `fcvtLS full encoding`() {
        val i = insn { fcvtLS(X10, F11) }
        assertEquals(0x60, funct7(i))
        assertEquals(2, rs2(i))
    }

    @Test
    fun `fcvtLuS full encoding`() {
        val i = insn { fcvtLuS(X10, F11) }
        assertEquals(0x60, funct7(i))
        assertEquals(3, rs2(i))
    }

    @Test
    fun `fcvtSW full encoding`() {
        val i = insn { fcvtSW(F10, X11) }
        assertEquals(0x68, funct7(i))
        assertEquals(0, rs2(i))
    }

    @Test
    fun `fcvtSWu full encoding`() {
        val i = insn { fcvtSWu(F10, X11) }
        assertEquals(0x68, funct7(i))
        assertEquals(1, rs2(i))
    }

    @Test
    fun `fcvtSL full encoding`() {
        val i = insn { fcvtSL(F10, X11) }
        assertEquals(0x68, funct7(i))
        assertEquals(2, rs2(i))
    }

    @Test
    fun `fcvtSLu full encoding`() {
        val i = insn { fcvtSLu(F10, X11) }
        assertEquals(0x68, funct7(i))
        assertEquals(3, rs2(i))
    }

    // F Extension - moves
    @Test
    fun `fmvXW full encoding`() {
        val i = insn { fmvXW(X10, F11) }
        assertEquals(0x70, funct7(i))
        assertEquals(0, funct3(i))
        assertEquals(0, rs2(i))
    }

    @Test
    fun `fmvWX full encoding`() {
        val i = insn { fmvWX(F10, X11) }
        assertEquals(0x78, funct7(i))
        assertEquals(0, funct3(i))
        assertEquals(0, rs2(i))
    }

    // F Extension - classify
    @Test
    fun `fclassS full encoding`() {
        val i = insn { fclassS(X10, F11) }
        assertEquals(0x70, funct7(i))
        assertEquals(1, funct3(i))
        assertEquals(0, rs2(i))
    }

    // F Extension - loads/stores
    @Test
    fun `flw full encoding`() {
        val i = insn { flw(F5, RiscVMemory(X10, 200)) }
        assertEquals(0x07, opcode(i))
        assertEquals(2, funct3(i))
        assertEquals(5, rd(i))
        assertEquals(10, rs1(i))
        assertEquals(200, immI(i))
    }

    @Test
    fun `flw negative offset`() {
        val i = insn { flw(F10, RiscVMemory(X2, -16)) }
        assertEquals(0x07, opcode(i))
        assertEquals((-16) and 0xFFF, immI(i))
    }

    @Test
    fun `fsw full encoding`() {
        val i = insn { fsw(F5, RiscVMemory(X10, 100)) }
        assertEquals(0x27, opcode(i))
        assertEquals(2, funct3(i))
    }

    // F Extension - pseudos
    @Test
    fun `fmvS is fsgnjS with same source`() {
        val i = insn { fmvS(F10, F11) }
        assertEquals(0x10, funct7(i))
        assertEquals(0, funct3(i))
        assertEquals(11, rs1(i))
        assertEquals(11, rs2(i)) // rs2 == rs1 for fmv pseudo
        assertEquals(10, rd(i))
    }

    @Test
    fun `fnegS is fsgnjnS with same source`() {
        val i = insn { fnegS(F10, F11) }
        assertEquals(0x10, funct7(i))
        assertEquals(1, funct3(i))
        assertEquals(11, rs1(i))
        assertEquals(11, rs2(i))
    }

    @Test
    fun `fabsS is fsgnjxS with same source`() {
        val i = insn { fabsS(F10, F11) }
        assertEquals(0x10, funct7(i))
        assertEquals(2, funct3(i))
        assertEquals(11, rs1(i))
        assertEquals(11, rs2(i))
    }

    // D Extension - arithmetic
    @Test
    fun `faddD full encoding`() {
        val i = insn { faddD(F5, F6, F7) }
        assertEquals(0x01, funct7(i))
    }

    @Test
    fun `fsubD full encoding`() {
        val i = insn { fsubD(F8, F9, F10) }
        assertEquals(0x05, funct7(i))
    }

    @Test
    fun `fmulD full encoding`() {
        val i = insn { fmulD(F11, F12, F13) }
        assertEquals(0x09, funct7(i))
    }

    @Test
    fun `fdivD full encoding`() {
        val i = insn { fdivD(F14, F15, F16) }
        assertEquals(0x0D, funct7(i))
    }

    @Test
    fun `fsqrtD full encoding`() {
        val i = insn { fsqrtD(F17, F18) }
        assertEquals(0x2D, funct7(i))
        assertEquals(0, rs2(i))
    }

    // D Extension - sign injection
    @Test
    fun `fsgnjD full encoding`() {
        val i = insn { fsgnjD(F5, F6, F7) }
        assertEquals(0x11, funct7(i))
        assertEquals(0, funct3(i))
    }

    @Test
    fun `fsgnjnD full encoding`() {
        val i = insn { fsgnjnD(F5, F6, F7) }
        assertEquals(0x11, funct7(i))
        assertEquals(1, funct3(i))
    }

    @Test
    fun `fsgnjxD full encoding`() {
        val i = insn { fsgnjxD(F5, F6, F7) }
        assertEquals(0x11, funct7(i))
        assertEquals(2, funct3(i))
    }

    // D Extension - min/max
    @Test
    fun `fminD full encoding`() {
        val i = insn { fminD(F20, F21, F22) }
        assertEquals(0x15, funct7(i))
        assertEquals(0, funct3(i))
    }

    @Test
    fun `fmaxD full encoding`() {
        val i = insn { fmaxD(F23, F24, F25) }
        assertEquals(0x15, funct7(i))
        assertEquals(1, funct3(i))
    }

    // D Extension - comparisons
    @Test
    fun `feqD full encoding`() {
        val i = insn { feqD(X5, F6, F7) }
        assertEquals(0x51, funct7(i))
        assertEquals(2, funct3(i))
    }

    @Test
    fun `fltD full encoding`() {
        val i = insn { fltD(X8, F9, F10) }
        assertEquals(0x51, funct7(i))
        assertEquals(1, funct3(i))
    }

    @Test
    fun `fleD full encoding`() {
        val i = insn { fleD(X11, F12, F13) }
        assertEquals(0x51, funct7(i))
        assertEquals(0, funct3(i))
    }

    // D Extension - float<->float conversions
    @Test
    fun `fcvtSD full encoding`() {
        val i = insn { fcvtSD(F10, F11) }
        assertEquals(0x20, funct7(i))
        assertEquals(1, rs2(i)) // D source
    }

    @Test
    fun `fcvtDS full encoding`() {
        val i = insn { fcvtDS(F10, F11) }
        assertEquals(0x21, funct7(i))
        assertEquals(0, rs2(i)) // S source
    }

    // D Extension - double<->int conversions
    @Test
    fun `fcvtWD full encoding`() {
        val i = insn { fcvtWD(X10, F11) }
        assertEquals(0x61, funct7(i))
        assertEquals(0, rs2(i))
    }

    @Test
    fun `fcvtWuD full encoding`() {
        val i = insn { fcvtWuD(X10, F11) }
        assertEquals(0x61, funct7(i))
        assertEquals(1, rs2(i))
    }

    @Test
    fun `fcvtLD full encoding`() {
        val i = insn { fcvtLD(X10, F11) }
        assertEquals(0x61, funct7(i))
        assertEquals(2, rs2(i))
    }

    @Test
    fun `fcvtLuD full encoding`() {
        val i = insn { fcvtLuD(X10, F11) }
        assertEquals(0x61, funct7(i))
        assertEquals(3, rs2(i))
    }

    @Test
    fun `fcvtDW full encoding`() {
        val i = insn { fcvtDW(F10, X11) }
        assertEquals(0x69, funct7(i))
        assertEquals(0, rs2(i))
    }

    @Test
    fun `fcvtDWu full encoding`() {
        val i = insn { fcvtDWu(F10, X11) }
        assertEquals(0x69, funct7(i))
        assertEquals(1, rs2(i))
    }

    @Test
    fun `fcvtDL full encoding`() {
        val i = insn { fcvtDL(F10, X11) }
        assertEquals(0x69, funct7(i))
        assertEquals(2, rs2(i))
    }

    @Test
    fun `fcvtDLu full encoding`() {
        val i = insn { fcvtDLu(F10, X11) }
        assertEquals(0x69, funct7(i))
        assertEquals(3, rs2(i))
    }

    // D Extension - moves
    @Test
    fun `fmvXD full encoding`() {
        val i = insn { fmvXD(X10, F11) }
        assertEquals(0x71, funct7(i))
        assertEquals(0, funct3(i))
    }

    @Test
    fun `fmvDX full encoding`() {
        val i = insn { fmvDX(F10, X11) }
        assertEquals(0x79, funct7(i))
        assertEquals(0, funct3(i))
    }

    // D Extension - classify
    @Test
    fun `fclassD full encoding`() {
        val i = insn { fclassD(X10, F11) }
        assertEquals(0x71, funct7(i))
        assertEquals(1, funct3(i))
    }

    // D Extension - loads/stores
    @Test
    fun `fld full encoding`() {
        val i = insn { fld(F5, RiscVMemory(X10, 128)) }
        assertEquals(0x07, opcode(i))
        assertEquals(3, funct3(i))
        assertEquals(5, rd(i))
        assertEquals(128, immI(i))
    }

    @Test
    fun `fsd full encoding`() {
        val i = insn { fsd(F5, RiscVMemory(X10, 64)) }
        assertEquals(0x27, opcode(i))
        assertEquals(3, funct3(i))
    }

    // D Extension - pseudos
    @Test
    fun `fmvD is fsgnjD with same source`() {
        val i = insn { fmvD(F10, F11) }
        assertEquals(0x11, funct7(i))
        assertEquals(0, funct3(i))
        assertEquals(11, rs1(i))
        assertEquals(11, rs2(i))
    }

    @Test
    fun `fnegD is fsgnjnD with same source`() {
        val i = insn { fnegD(F10, F11) }
        assertEquals(0x11, funct7(i))
        assertEquals(1, funct3(i))
        assertEquals(11, rs2(i))
    }

    @Test
    fun `fabsD is fsgnjxD with same source`() {
        val i = insn { fabsD(F10, F11) }
        assertEquals(0x11, funct7(i))
        assertEquals(2, funct3(i))
        assertEquals(11, rs2(i))
    }

    // System instructions
    @Test
    fun `ecall exact encoding`() {
        assertEquals(0x00000073, insn { ecall() })
    }

    @Test
    fun `ebreak exact encoding`() {
        assertEquals(0x00100073, insn { ebreak() })
    }

    @Test
    fun `fence exact encoding`() {
        assertEquals(0x0FF0000F, insn { fence() })
    }

    @Test
    fun `fenceI exact encoding`() {
        assertEquals(0x0000100F, insn { fenceI() })
    }

    // Pseudo-instructions
    @Test
    fun `nop is addi x0 x0 0`() {
        assertEquals(0x00000013, insn { nop() })
    }

    @Test
    fun `mv encoding`() {
        val i = insn { mv(X15, X20) }
        assertEquals(iType(0, 20, 0, 15, 0x13), i)
    }

    @Test
    fun `not encoding`() {
        val i = insn { not(X10, X11) }
        assertEquals(iType(-1, 11, 4, 10, 0x13), i)
    }

    @Test
    fun `neg encoding`() {
        val i = insn { neg(X10, X11) }
        assertEquals(rType(0x20, 11, 0, 0, 10, 0x33), i)
    }

    @Test
    fun `negw encoding`() {
        val i = insn { negw(X10, X11) }
        assertEquals(rType(0x20, 11, 0, 0, 10, 0x3B), i)
    }

    @Test
    fun `seqz encoding`() {
        val i = insn { seqz(X10, X11) }
        // sltiu x10, x11, 1
        assertEquals(iType(1, 11, 3, 10, 0x13), i)
    }

    @Test
    fun `snez encoding`() {
        val i = insn { snez(X10, X11) }
        // sltu x10, x0, x11
        assertEquals(rType(0x00, 11, 0, 3, 10, 0x33), i)
    }

    @Test
    fun `ret encoding`() {
        val i = insn { ret() }
        // jalr x0, x1, 0
        assertEquals(iType(0, 1, 0, 0, 0x67), i)
    }

    @Test
    fun `j with offset is jal x0`() {
        val i = insn { j(100) }
        assertEquals(0x6F, opcode(i))
        assertEquals(0, rd(i))
    }

    // li pseudo-instruction
    @Test
    fun `li zero`() {
        val i = insn { li(X10, 0) }
        assertEquals(iType(0, 0, 0, 10, 0x13), i)
    }

    @Test
    fun `li positive 12-bit`() {
        val i = insn { li(X10, 2047) }
        assertEquals(iType(2047, 0, 0, 10, 0x13), i)
    }

    @Test
    fun `li negative 12-bit`() {
        val i = insn { li(X10, -2048) }
        assertEquals(iType(-2048, 0, 0, 10, 0x13), i)
    }

    @Test
    fun `li negative 1`() {
        val i = insn { li(X10, -1) }
        assertEquals(iType(-1, 0, 0, 10, 0x13), i)
    }

    @Test
    fun `li 2048 needs lui plus addi`() {
        val a = asm()
        a.li(X10, 2048)
        val bytes = a.toByteArray()
        assertEquals(8, bytes.size)
        // lui x10, 1
        val lui = readU32(bytes, 0)
        assertEquals(0x37, opcode(lui))
        assertEquals(10, rd(lui))
        // addi x10, x10, -2048 (since 1 << 12 = 4096, 4096 + (-2048) = 2048)
        val addi = readU32(bytes, 4)
        assertEquals(0x13, opcode(addi))
    }

    @Test
    fun `li 4096 is lui only`() {
        val a = asm()
        a.li(X10, 4096)
        val bytes = a.toByteArray()
        // 4096 = 0x1000 -> upper = (4096 + 0x800) >>> 12 = 1, lower = 4096 - (1 << 12) = 0
        assertEquals(4, bytes.size) // lui only, no addi needed
        val lui = readU32(bytes, 0)
        assertEquals(0x37, opcode(lui))
    }

    @Test
    fun `li 0x12345 uses lui plus addi`() {
        val a = asm()
        a.li(X10, 0x12345)
        val bytes = a.toByteArray()
        assertEquals(8, bytes.size)
    }

    @Test
    fun `li large negative`() {
        val a = asm()
        a.li(X10, -100000)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 8)
    }

    @Test
    fun `li Int MAX_VALUE`() {
        val a = asm()
        a.li(X10, Int.MAX_VALUE)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 4)
    }

    @Test
    fun `li Int MIN_VALUE`() {
        val a = asm()
        a.li(X10, Int.MIN_VALUE)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 4)
    }

    // li Long variant
    @Test
    fun `li long small immediate`() {
        val i = insn { li(X10, 42L) }
        assertEquals(iType(42, 0, 0, 10, 0x13), i)
    }

    @Test
    fun `li long large value produces multiple instructions`() {
        val a = asm()
        a.li(X10, 0x1_0000_0000L) // > 32 bits
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 8, "64-bit value needs multiple instructions, got ${bytes.size} bytes")
    }

    // Branch label tests
    @Test
    fun `blt label forward`() {
        val a = asm()
        a.blt(X10, X11, "target")
        a.nop()
        a.label("target")
        a.nop()
        val bytes = a.toByteArray()
        assertEquals(12, bytes.size)
        val branchInsn = readU32(bytes, 0)
        assertEquals(0x63, opcode(branchInsn))
        assertEquals(4, funct3(branchInsn))
    }

    @Test
    fun `bgeu label backward`() {
        val a = asm()
        a.label("start")
        a.nop()
        a.bgeu(X10, X11, "start")
        val bytes = a.toByteArray()
        assertEquals(8, bytes.size)
        val branchInsn = readU32(bytes, 4)
        assertEquals(7, funct3(branchInsn))
    }

    @Test
    fun `bne label forward`() {
        val a = asm()
        a.bne(X5, X6, "end")
        a.addi(X10, X10, 1)
        a.addi(X10, X10, 2)
        a.label("end")
        a.ret()
        assertEquals(16, a.toByteArray().size)
    }

    @Test
    fun `call label`() {
        val a = asm()
        a.call("func")
        a.nop()
        a.label("func")
        a.ret()
        val bytes = a.toByteArray()
        assertEquals(12, bytes.size)
        val jalInsn = readU32(bytes, 0)
        assertEquals(0x6F, opcode(jalInsn))
        assertEquals(1, rd(jalInsn)) // ra
    }

    @Test
    fun `j label forward`() {
        val a = asm()
        a.j("end")
        a.nop()
        a.label("end")
        a.ret()
        val bytes = a.toByteArray()
        assertEquals(12, bytes.size)
        val jalInsn = readU32(bytes, 0)
        assertEquals(0, rd(jalInsn)) // x0
    }

    // C Extension compressed instructions
    @Test
    fun `cNop exact encoding`() {
        assertEquals(0x0001, insn16 { cNop() })
    }

    @Test
    fun `cEbreak exact encoding`() {
        assertEquals(0x9002, insn16 { cEbreak() })
    }

    @Test
    fun `cAddi field verification`() {
        val i = insn16 { cAddi(X10, 5) }
        assertEquals(0x01, i and 0x03) // quadrant 01
        assertEquals(10, (i shr 7) and 0x1F) // rd
        assertEquals(5, (i shr 2) and 0x1F) // imm[4:0]
        assertEquals(0, (i shr 12) and 1) // imm[5]
    }

    @Test
    fun `cAddi negative`() {
        val i = insn16 { cAddi(X10, -1) }
        assertEquals(0x01, i and 0x03)
        assertEquals(0x1F, (i shr 2) and 0x1F) // imm[4:0] = 11111
        assertEquals(1, (i shr 12) and 1) // imm[5] = 1
    }

    @Test
    fun `cAddiw field verification`() {
        val i = insn16 { cAddiw(X10, 3) }
        assertEquals(0x01, i and 0x03) // quadrant 01
        assertEquals(10, (i shr 7) and 0x1F) // rd
        assertEquals(3, (i shr 2) and 0x1F) // imm[4:0]
        assertEquals(1, (i shr 13) and 0x7) // funct3 = 001
    }

    @Test
    fun `cLi field verification`() {
        val i = insn16 { cLi(X15, 10) }
        assertEquals(0x01, i and 0x03)
        assertEquals(15, (i shr 7) and 0x1F)
        assertEquals(10, (i shr 2) and 0x1F)
    }

    @Test
    fun `cLi negative`() {
        val i = insn16 { cLi(X10, -5) }
        assertEquals(0x01, i and 0x03)
        assertEquals(1, (i shr 12) and 1) // sign bit
    }

    @Test
    fun `cLui field verification`() {
        val i = insn16 { cLui(X10, 0x1F000) }
        assertEquals(0x01, i and 0x03)
        assertEquals(10, (i shr 7) and 0x1F)
    }

    @Test
    fun `cMv field verification`() {
        val i = insn16 { cMv(X10, X11) }
        assertEquals(0x02, i and 0x03) // quadrant 10
        assertEquals(10, (i shr 7) and 0x1F)
        assertEquals(11, (i shr 2) and 0x1F)
    }

    @Test
    fun `cAdd field verification`() {
        val i = insn16 { cAdd(X10, X11) }
        assertEquals(0x02, i and 0x03) // quadrant 10
        assertEquals(10, (i shr 7) and 0x1F)
        assertEquals(11, (i shr 2) and 0x1F)
    }

    @Test
    fun `cJr field verification`() {
        val i = insn16 { cJr(X10) }
        assertEquals(0x02, i and 0x03)
        assertEquals(10, (i shr 7) and 0x1F)
        assertEquals(0, (i shr 2) and 0x1F) // rs2 = 0
    }

    @Test
    fun `cJalr field verification`() {
        val i = insn16 { cJalr(X5) }
        assertEquals(0x02, i and 0x03)
        assertEquals(5, (i shr 7) and 0x1F)
    }

    @Test
    fun `cSlli field verification`() {
        val i = insn16 { cSlli(X10, 8) }
        assertEquals(0x02, i and 0x03)
        assertEquals(10, (i shr 7) and 0x1F)
        assertEquals(8, (i shr 2) and 0x1F)
    }

    @Test
    fun `cSlli large shamt`() {
        val i = insn16 { cSlli(X10, 32) }
        assertEquals(0x02, i and 0x03)
        assertEquals(0, (i shr 2) and 0x1F) // low 5 bits of 32 = 0
        assertEquals(1, (i shr 12) and 1) // bit5 = 1
    }

    @Test
    fun `cSub field verification`() {
        val i = insn16 { cSub(X8, X9) }
        assertEquals(0x01, i and 0x03)
        // rd' = 0 (x8 - 8 = 0)
        assertEquals(0, (i shr 7) and 0x7)
    }

    @Test
    fun `cXor field verification`() {
        val i = insn16 { cXor(X9, X10) }
        assertEquals(0x01, i and 0x03)
        assertEquals(1, (i shr 7) and 0x7) // rd' = x9-8 = 1
    }

    @Test
    fun `cOr field verification`() {
        val i = insn16 { cOr(X10, X11) }
        assertEquals(0x01, i and 0x03)
        assertEquals(2, (i shr 7) and 0x7) // rd' = x10-8 = 2
    }

    @Test
    fun `cAnd field verification`() {
        val i = insn16 { cAnd(X11, X12) }
        assertEquals(0x01, i and 0x03)
    }

    @Test
    fun `cSubw field verification`() {
        val i = insn16 { cSubw(X8, X9) }
        assertEquals(0x01, i and 0x03)
    }

    @Test
    fun `cAddw field verification`() {
        val i = insn16 { cAddw(X8, X9) }
        assertEquals(0x01, i and 0x03)
    }

    @Test
    fun `cSrli field verification`() {
        val i = insn16 { cSrli(X8, 4) }
        assertEquals(0x01, i and 0x03)
        assertEquals(4, (i shr 2) and 0x1F)
    }

    @Test
    fun `cSrai field verification`() {
        val i = insn16 { cSrai(X8, 8) }
        assertEquals(0x01, i and 0x03)
        assertEquals(8, (i shr 2) and 0x1F)
    }

    @Test
    fun `cAndi field verification`() {
        val i = insn16 { cAndi(X8, 15) }
        assertEquals(0x01, i and 0x03)
        assertEquals(15, (i shr 2) and 0x1F)
    }

    @Test
    fun `cBeqz field verification`() {
        val i = insn16 { cBeqz(X8, 16) }
        assertEquals(0x01, i and 0x03)
    }

    @Test
    fun `cBnez field verification`() {
        val i = insn16 { cBnez(X8, 16) }
        assertEquals(0x01, i and 0x03)
    }

    @Test
    fun `cJ field verification`() {
        val i = insn16 { cJ(64) }
        assertEquals(0x01, i and 0x03)
    }

    @Test
    fun `cLwsp field verification`() {
        val i = insn16 { cLwsp(X10, 12) }
        assertEquals(0x02, i and 0x03) // quadrant 10
        assertEquals(10, (i shr 7) and 0x1F)
    }

    @Test
    fun `cLdsp field verification`() {
        val i = insn16 { cLdsp(X10, 24) }
        assertEquals(0x02, i and 0x03)
        assertEquals(10, (i shr 7) and 0x1F)
    }

    @Test
    fun `cSwsp field verification`() {
        val i = insn16 { cSwsp(X10, 12) }
        assertEquals(0x02, i and 0x03)
    }

    @Test
    fun `cSdsp field verification`() {
        val i = insn16 { cSdsp(X10, 24) }
        assertEquals(0x02, i and 0x03)
    }

    @Test
    fun `cAddi16sp field verification`() {
        val i = insn16 { cAddi16sp(-32) }
        assertEquals(0x01, i and 0x03)
        assertEquals(2, (i shr 7) and 0x1F) // rd = sp (x2)
    }

    @Test
    fun `cAddi16sp positive`() {
        val i = insn16 { cAddi16sp(16) }
        assertEquals(0x01, i and 0x03)
        assertEquals(2, (i shr 7) and 0x1F)
    }

    // C Extension - register-based loads/stores
    @Test
    fun `cLw field verification`() {
        val a = asm()
        a.cLw(X8, X9, 0)
        assertEquals(2, a.toByteArray().size)
    }

    @Test
    fun `cLd field verification`() {
        val a = asm()
        a.cLd(X8, X9, 0)
        assertEquals(2, a.toByteArray().size)
    }

    @Test
    fun `cSw field verification`() {
        val a = asm()
        a.cSw(X8, X9, 0)
        assertEquals(2, a.toByteArray().size)
    }

    @Test
    fun `cSd field verification`() {
        val a = asm()
        a.cSd(X8, X9, 0)
        assertEquals(2, a.toByteArray().size)
    }

    @Test
    fun `cLw with offset`() {
        val a = asm()
        a.cLw(X10, X11, 4)
        assertEquals(2, a.toByteArray().size)
    }

    @Test
    fun `cSd with offset`() {
        val a = asm()
        a.cSd(X10, X11, 8)
        assertEquals(2, a.toByteArray().size)
    }

    // Register encoding edge cases
    @Test
    fun `all GP registers encode correctly in add`() {
        val regs = listOf(X0, X1, X2, X3, X4, X5, X6, X7, X8, X9,
            X10, X11, X12, X13, X14, X15, X16, X17, X18, X19,
            X20, X21, X22, X23, X24, X25, X26, X27, X28, X29, X30, X31)
        for ((idx, reg) in regs.withIndex()) {
            assertEquals(idx, reg.encoding, "Register $reg should have encoding $idx")
        }
    }

    @Test
    fun `all FP registers encode correctly`() {
        val regs = listOf(F0, F1, F2, F3, F4, F5, F6, F7, F8, F9,
            F10, F11, F12, F13, F14, F15, F16, F17, F18, F19,
            F20, F21, F22, F23, F24, F25, F26, F27, F28, F29, F30, F31)
        for ((idx, reg) in regs.withIndex()) {
            assertEquals(idx, reg.encoding, "FP register $reg should have encoding $idx")
        }
    }

    @Test
    fun `add with every register as rd`() {
        val regs = listOf(X0, X1, X2, X3, X4, X5, X6, X7, X8, X9,
            X10, X11, X12, X13, X14, X15, X16, X17, X18, X19,
            X20, X21, X22, X23, X24, X25, X26, X27, X28, X29, X30, X31)
        for ((idx, reg) in regs.withIndex()) {
            val i = insn { add(reg, X0, X0) }
            assertEquals(idx, rd(i), "rd should be $idx for $reg")
        }
    }

    @Test
    fun `faddD with high FP registers`() {
        val i = insn { faddD(F28, F29, F30) }
        assertEquals(0x01, funct7(i))
        assertEquals(28, rd(i))
        assertEquals(29, rs1(i))
        assertEquals(30, rs2(i))
    }

    // Memory operand edge cases
    @Test
    fun `memory operand max offset`() {
        val mem = RiscVMemory(X2, 2047)
        assertEquals(2047, mem.offset)
        assertEquals(X2, mem.base)
    }

    @Test
    fun `memory operand min offset`() {
        val mem = RiscVMemory(X2, -2048)
        assertEquals(-2048, mem.offset)
    }

    @Test
    fun `memory operand rejects out of range positive`() {
        assertThrows(IllegalArgumentException::class.java) { RiscVMemory(X2, 2048) }
    }

    @Test
    fun `memory operand rejects out of range negative`() {
        assertThrows(IllegalArgumentException::class.java) { RiscVMemory(X2, -2049) }
    }

    // Mixed instruction size verification
    @Test
    fun `mixed 32-bit and 16-bit sizes add up`() {
        val a = asm()
        a.add(X10, X11, X12)   // 4
        a.cNop()                // 2
        a.sub(X10, X10, X11)   // 4
        a.cAddi(X10, 1)        // 2
        a.mul(X10, X10, X11)   // 4
        assertEquals(16, a.toByteArray().size)
    }

    @Test
    fun `size tracking after each instruction`() {
        val a = asm()
        assertEquals(0, a.size)
        a.add(X10, X11, X12)
        assertEquals(4, a.size)
        a.cNop()
        assertEquals(6, a.size)
        a.ld(X10, RiscVMemory(X2, 0))
        assertEquals(10, a.size)
    }

    // Unresolved label tracking
    @Test
    fun `multiple unresolved labels`() {
        val a = asm()
        a.beq(X10, X0, "a")
        a.bne(X11, X0, "b")
        a.j("c")
        val unresolved = a.unresolvedLabels()
        assertEquals(3, unresolved.size)
        assertEquals("a", unresolved[0].second)
        assertEquals("b", unresolved[1].second)
        assertEquals("c", unresolved[2].second)
    }

    @Test
    fun `resolved label is not in unresolved list`() {
        val a = asm()
        a.beq(X10, X0, "target")
        a.nop()
        a.label("target")
        a.nop()
        val unresolved = a.unresolvedLabels()
        assertEquals(0, unresolved.size)
    }

    // Multi-instruction sequences
    @Test
    fun `function prologue sequence`() {
        val a = asm()
        a.addi(X2, X2, -16)    // sp -= 16
        a.sd(X1, RiscVMemory(X2, 8))   // save ra
        a.sd(X8, RiscVMemory(X2, 0))   // save s0
        a.addi(X8, X2, 16)    // s0 = sp + 16
        assertEquals(16, a.toByteArray().size)
    }

    @Test
    fun `function epilogue sequence`() {
        val a = asm()
        a.ld(X1, RiscVMemory(X2, 8))   // restore ra
        a.ld(X8, RiscVMemory(X2, 0))   // restore s0
        a.addi(X2, X2, 16)    // sp += 16
        a.ret()
        assertEquals(16, a.toByteArray().size)
    }

    @Test
    fun `atomic compare and swap pattern`() {
        val a = asm()
        a.label("retry")
        a.lrW(X10, X11, aq = true)
        a.bne(X10, X12, "fail")
        a.scW(X10, X13, X11, rl = true)
        a.bne(X10, X0, "retry")
        a.label("fail")
        a.ret()
        val bytes = a.toByteArray()
        assertEquals(20, bytes.size) // 5 instructions
    }

    @Test
    fun `floating point computation sequence`() {
        val a = asm()
        a.fld(F10, RiscVMemory(X10, 0))
        a.fld(F11, RiscVMemory(X10, 8))
        a.fmulD(F12, F10, F11)
        a.faddD(F10, F12, F10)
        a.fsd(F10, RiscVMemory(X10, 0))
        assertEquals(20, a.toByteArray().size)
    }

    // ABI alias tests
    @Test
    fun `ZERO alias is X0`() { assertSame(X0, ZERO) }

    @Test
    fun `RA alias is X1`() { assertSame(X1, RA) }

    @Test
    fun `SP alias is X2`() { assertSame(X2, SP) }

    @Test
    fun `GP alias is X3`() { assertSame(X3, GP) }

    @Test
    fun `TP alias is X4`() { assertSame(X4, TP) }

    @Test
    fun `FP alias is X8`() { assertSame(X8, FP) }

    // Fence details
    @Test
    fun `fence opcode is 0x0F`() {
        val i = insn { fence() }
        assertEquals(0x0F, opcode(i))
    }

    @Test
    fun `fenceI opcode is 0x0F funct3 is 1`() {
        val i = insn { fenceI() }
        assertEquals(0x0F, opcode(i))
        assertEquals(1, funct3(i))
    }

    // Conversion with explicit rounding modes
    @Test
    fun `fcvtWS with RNE`() {
        val i = insn { fcvtWS(X10, F11, rm = 0) }
        assertEquals(0, funct3(i))
    }

    @Test
    fun `fcvtSW with RTZ`() {
        val i = insn { fcvtSW(F10, X11, rm = 1) }
        assertEquals(1, funct3(i))
    }

    @Test
    fun `fcvtWD with RDN`() {
        val i = insn { fcvtWD(X10, F11, rm = 2) }
        assertEquals(2, funct3(i))
    }

    @Test
    fun `fsqrtS with RUP`() {
        val i = insn { fsqrtS(F10, F11, rm = 3) }
        assertEquals(3, funct3(i))
    }

    @Test
    fun `faddS with RMM`() {
        val i = insn { faddS(F10, F11, F12, rm = 4) }
        assertEquals(4, funct3(i))
    }

    @Test
    fun `faddD with RNE`() {
        val i = insn { faddD(F10, F11, F12, rm = 0) }
        assertEquals(0, funct3(i))
    }

    @Test
    fun `fcvtDW with dynamic rounding`() {
        val i = insn { fcvtDW(F10, X11, rm = 7) }
        assertEquals(7, funct3(i))
    }

    // bytes() alias
    @Test
    fun `bytes produces same result as toByteArray`() {
        val a = asm()
        a.add(X10, X11, X12)
        a.sub(X13, X14, X15)
        assertArrayEquals(a.toByteArray(), a.bytes())
    }
}
