package org.kgen.target.riscv

import org.kgen.target.riscv.asm.RiscVAssembler
import org.kgen.target.riscv.disasm.RiscVDisassembler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RiscVDisassemblerComprehensiveTest {

    private val disasm = RiscVDisassembler()

    private fun roundTrip(build: RiscVAssembler.() -> Unit): List<RiscVDisassembler.DisassembledInsn> {
        val asm = RiscVAssembler()
        asm.build()
        return disasm.disassemble(asm.toByteArray())
    }

    private fun disasmWord(word: Int): RiscVDisassembler.DisassembledInsn {
        val bytes = byteArrayOf(
            (word and 0xFF).toByte(),
            ((word shr 8) and 0xFF).toByte(),
            ((word shr 16) and 0xFF).toByte(),
            ((word shr 24) and 0xFF).toByte(),
        )
        return disasm.disassemble(bytes)[0]
    }

    // RV64I: R-type arithmetic

    @Test
    fun `disassembles add all arg regs`() {
        val insns = roundTrip { add(X10, X11, X12) }
        assertEquals("add", insns[0].mnemonic)
        assertEquals("a0, a1, a2", insns[0].operandsStr)
    }

    @Test
    fun `disassembles add with high regs`() {
        val insns = roundTrip { add(X28, X29, X30) }
        assertEquals("add", insns[0].mnemonic)
        assertEquals("t3, t4, t5", insns[0].operandsStr)
    }

    @Test
    fun `disassembles sub with saved regs`() {
        val insns = roundTrip { sub(X18, X19, X20) }
        assertEquals("sub", insns[0].mnemonic)
        assertEquals("s2, s3, s4", insns[0].operandsStr)
    }

    @Test
    fun `disassembles sll`() {
        val insns = roundTrip { sll(X10, X11, X12) }
        assertEquals("sll", insns[0].mnemonic)
    }

    @Test
    fun `disassembles srl`() {
        val insns = roundTrip { srl(X10, X11, X12) }
        assertEquals("srl", insns[0].mnemonic)
    }

    @Test
    fun `disassembles sra`() {
        val insns = roundTrip { sra(X10, X11, X12) }
        assertEquals("sra", insns[0].mnemonic)
    }

    @Test
    fun `disassembles slt`() {
        val insns = roundTrip { slt(X10, X11, X12) }
        assertEquals("slt", insns[0].mnemonic)
    }

    @Test
    fun `disassembles sltu`() {
        val insns = roundTrip { sltu(X10, X11, X12) }
        assertEquals("sltu", insns[0].mnemonic)
    }

    @Test
    fun `disassembles and`() {
        val insns = roundTrip { and(X10, X11, X12) }
        assertEquals("and", insns[0].mnemonic)
    }

    @Test
    fun `disassembles or`() {
        val insns = roundTrip { or(X10, X11, X12) }
        assertEquals("or", insns[0].mnemonic)
    }

    @Test
    fun `disassembles xor`() {
        val insns = roundTrip { xor(X10, X11, X12) }
        assertEquals("xor", insns[0].mnemonic)
    }

    // RV64I: Word (32-bit) R-type

    @Test
    fun `disassembles addw`() {
        val insns = roundTrip { addw(X10, X11, X12) }
        assertEquals("addw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles subw`() {
        val insns = roundTrip { subw(X10, X11, X12) }
        assertEquals("subw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles sllw`() {
        val insns = roundTrip { sllw(X10, X11, X12) }
        assertEquals("sllw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles srlw`() {
        val insns = roundTrip { srlw(X10, X11, X12) }
        assertEquals("srlw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles sraw`() {
        val insns = roundTrip { sraw(X10, X11, X12) }
        assertEquals("sraw", insns[0].mnemonic)
    }

    // I-type arithmetic

    @Test
    fun `disassembles addi positive`() {
        val insns = roundTrip { addi(X10, X11, 100) }
        assertEquals("addi", insns[0].mnemonic)
        assertEquals("a0, a1, 100", insns[0].operandsStr)
    }

    @Test
    fun `disassembles addi negative`() {
        val insns = roundTrip { addi(X10, X11, -50) }
        assertEquals("addi", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("-50"))
    }

    @Test
    fun `disassembles addi zero gives mv pseudo`() {
        val insns = roundTrip { addi(X10, X11, 0) }
        assertEquals("mv", insns[0].mnemonic)
        assertEquals("a0, a1", insns[0].operandsStr)
    }

    @Test
    fun `disassembles slti`() {
        val insns = roundTrip { slti(X10, X11, 5) }
        assertEquals("slti", insns[0].mnemonic)
    }

    @Test
    fun `disassembles sltiu with 1 gives seqz`() {
        val insns = roundTrip { sltiu(X10, X11, 1) }
        assertEquals("seqz", insns[0].mnemonic)
    }

    @Test
    fun `disassembles sltiu non-pseudo`() {
        val insns = roundTrip { sltiu(X10, X11, 5) }
        assertEquals("sltiu", insns[0].mnemonic)
    }

    @Test
    fun `disassembles xori`() {
        val insns = roundTrip { xori(X10, X11, 0xFF) }
        assertEquals("xori", insns[0].mnemonic)
    }

    @Test
    fun `disassembles xori -1 gives not pseudo`() {
        val insns = roundTrip { xori(X10, X11, -1) }
        assertEquals("not", insns[0].mnemonic)
        assertEquals("a0, a1", insns[0].operandsStr)
    }

    @Test
    fun `disassembles ori`() {
        val insns = roundTrip { ori(X10, X11, 0x0F) }
        assertEquals("ori", insns[0].mnemonic)
    }

    @Test
    fun `disassembles andi`() {
        val insns = roundTrip { andi(X10, X11, 0xFF) }
        assertEquals("andi", insns[0].mnemonic)
    }

    // Shift immediates

    @Test
    fun `disassembles slli`() {
        val insns = roundTrip { slli(X10, X11, 4) }
        assertEquals("slli", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("4"))
    }

    @Test
    fun `disassembles srli`() {
        val insns = roundTrip { srli(X10, X11, 8) }
        assertEquals("srli", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("8"))
    }

    @Test
    fun `disassembles srai`() {
        val insns = roundTrip { srai(X10, X11, 16) }
        assertEquals("srai", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("16"))
    }

    // Word shift immediates

    @Test
    fun `disassembles addiw`() {
        val insns = roundTrip { addiw(X10, X11, 7) }
        assertEquals("addiw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles slliw`() {
        val insns = roundTrip { slliw(X10, X11, 3) }
        assertEquals("slliw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles srliw`() {
        val insns = roundTrip { srliw(X10, X11, 5) }
        assertEquals("srliw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles sraiw`() {
        val insns = roundTrip { sraiw(X10, X11, 4) }
        assertEquals("sraiw", insns[0].mnemonic)
    }

    // U-type

    @Test
    fun `disassembles lui`() {
        val insns = roundTrip { lui(X10, 0x12345) }
        assertEquals("lui", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("12345"))
    }

    @Test
    fun `disassembles auipc`() {
        val insns = roundTrip { auipc(X10, 0xABCDE) }
        assertEquals("auipc", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("abcde"))
    }

    // Loads

    @Test
    fun `disassembles lb`() {
        val insns = roundTrip { lb(X10, RiscVMemory(X8, 0)) }
        assertEquals("lb", insns[0].mnemonic)
        assertEquals("a0, 0(s0)", insns[0].operandsStr)
    }

    @Test
    fun `disassembles lh`() {
        val insns = roundTrip { lh(X10, RiscVMemory(X8, 2)) }
        assertEquals("lh", insns[0].mnemonic)
    }

    @Test
    fun `disassembles lw`() {
        val insns = roundTrip { lw(X10, RiscVMemory(X8, 4)) }
        assertEquals("lw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles ld with offset`() {
        val insns = roundTrip { ld(X10, RiscVMemory(X2, 24)) }
        assertEquals("ld", insns[0].mnemonic)
        assertEquals("a0, 24(sp)", insns[0].operandsStr)
    }

    @Test
    fun `disassembles lbu`() {
        val insns = roundTrip { lbu(X10, RiscVMemory(X8, 0)) }
        assertEquals("lbu", insns[0].mnemonic)
    }

    @Test
    fun `disassembles lhu`() {
        val insns = roundTrip { lhu(X10, RiscVMemory(X8, 0)) }
        assertEquals("lhu", insns[0].mnemonic)
    }

    @Test
    fun `disassembles lwu`() {
        val insns = roundTrip { lwu(X10, RiscVMemory(X8, 0)) }
        assertEquals("lwu", insns[0].mnemonic)
    }

    @Test
    fun `disassembles ld negative offset`() {
        val insns = roundTrip { ld(X1, RiscVMemory(X2, -16)) }
        assertEquals("ld", insns[0].mnemonic)
        assertEquals("ra, -16(sp)", insns[0].operandsStr)
    }

    // Stores

    @Test
    fun `disassembles sb`() {
        val insns = roundTrip { sb(X10, RiscVMemory(X8, 0)) }
        assertEquals("sb", insns[0].mnemonic)
    }

    @Test
    fun `disassembles sh`() {
        val insns = roundTrip { sh(X10, RiscVMemory(X8, 2)) }
        assertEquals("sh", insns[0].mnemonic)
    }

    @Test
    fun `disassembles sw`() {
        val insns = roundTrip { sw(X10, RiscVMemory(X8, 4)) }
        assertEquals("sw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles sd`() {
        val insns = roundTrip { sd(X10, RiscVMemory(X2, 8)) }
        assertEquals("sd", insns[0].mnemonic)
        assertEquals("a0, 8(sp)", insns[0].operandsStr)
    }

    @Test
    fun `disassembles sd negative offset`() {
        val insns = roundTrip { sd(X1, RiscVMemory(X2, -8)) }
        assertEquals("sd", insns[0].mnemonic)
        assertEquals("ra, -8(sp)", insns[0].operandsStr)
    }

    // Branches

    @Test
    fun `disassembles beq`() {
        val insns = roundTrip { beq(X10, X11, 8) }
        assertEquals("beq", insns[0].mnemonic)
    }

    @Test
    fun `disassembles bne`() {
        val insns = roundTrip { bne(X10, X11, 8) }
        assertEquals("bne", insns[0].mnemonic)
    }

    @Test
    fun `disassembles blt`() {
        val insns = roundTrip { blt(X10, X11, 8) }
        assertEquals("blt", insns[0].mnemonic)
    }

    @Test
    fun `disassembles bge`() {
        val insns = roundTrip { bge(X10, X11, 8) }
        assertEquals("bge", insns[0].mnemonic)
    }

    @Test
    fun `disassembles bltu`() {
        val insns = roundTrip { bltu(X10, X11, 8) }
        assertEquals("bltu", insns[0].mnemonic)
    }

    @Test
    fun `disassembles bgeu`() {
        val insns = roundTrip { bgeu(X10, X11, 8) }
        assertEquals("bgeu", insns[0].mnemonic)
    }

    @Test
    fun `disassembles beqz pseudo`() {
        val insns = roundTrip { beq(X10, X0, 8) }
        assertEquals("beqz", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.startsWith("a0"))
    }

    @Test
    fun `disassembles bnez pseudo`() {
        val insns = roundTrip { bne(X10, X0, 8) }
        assertEquals("bnez", insns[0].mnemonic)
    }

    @Test
    fun `disassembles branch backward offset`() {
        val insns = roundTrip {
            beq(X10, X11, -4)
        }
        assertEquals("beq", insns[0].mnemonic)
    }

    // Jumps

    @Test
    fun `disassembles jal ra`() {
        val insns = roundTrip { jal(X1, 100) }
        assertEquals("jal", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("ra"))
    }

    @Test
    fun `disassembles j pseudo`() {
        val insns = roundTrip { j(100) }
        assertEquals("j", insns[0].mnemonic)
    }

    @Test
    fun `disassembles jalr`() {
        val insns = roundTrip { jalr(X1, X10, 0) }
        assertEquals("jalr", insns[0].mnemonic)
    }

    @Test
    fun `disassembles jalr with offset`() {
        val insns = roundTrip { jalr(X1, X10, 4) }
        assertEquals("jalr", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("4"))
    }

    @Test
    fun `disassembles ret pseudo`() {
        val insns = roundTrip { ret() }
        assertEquals("ret", insns[0].mnemonic)
        assertEquals("", insns[0].operandsStr)
    }

    @Test
    fun `disassembles jr pseudo`() {
        val insns = roundTrip { jalr(X0, X10, 0) }
        assertEquals("jr", insns[0].mnemonic)
        assertEquals("a0", insns[0].operandsStr)
    }

    // Pseudo-instructions

    @Test
    fun `disassembles nop`() {
        val insns = roundTrip { nop() }
        assertEquals("nop", insns[0].mnemonic)
        assertEquals("", insns[0].operandsStr)
    }

    @Test
    fun `disassembles mv`() {
        val insns = roundTrip { mv(X10, X11) }
        assertEquals("mv", insns[0].mnemonic)
        assertEquals("a0, a1", insns[0].operandsStr)
    }

    @Test
    fun `disassembles li small`() {
        val insns = roundTrip { li(X10, 42) }
        assertEquals("li", insns[0].mnemonic)
        assertEquals("a0, 42", insns[0].operandsStr)
    }

    @Test
    fun `disassembles neg`() {
        val insns = roundTrip { neg(X10, X11) }
        assertEquals("neg", insns[0].mnemonic)
        assertEquals("a0, a1", insns[0].operandsStr)
    }

    @Test
    fun `disassembles negw`() {
        val insns = roundTrip { negw(X10, X11) }
        assertEquals("negw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles not`() {
        val insns = roundTrip { not(X10, X11) }
        assertEquals("not", insns[0].mnemonic)
        assertEquals("a0, a1", insns[0].operandsStr)
    }

    @Test
    fun `disassembles seqz`() {
        val insns = roundTrip { seqz(X10, X11) }
        assertEquals("seqz", insns[0].mnemonic)
        assertEquals("a0, a1", insns[0].operandsStr)
    }

    @Test
    fun `disassembles snez`() {
        val insns = roundTrip { snez(X10, X11) }
        assertEquals("sltu", insns[0].mnemonic) // snez uses sltu x0, rs
    }

    // System

    @Test
    fun `disassembles ecall`() {
        val insns = roundTrip { ecall() }
        assertEquals("ecall", insns[0].mnemonic)
    }

    @Test
    fun `disassembles ebreak`() {
        val insns = roundTrip { ebreak() }
        assertEquals("ebreak", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fence`() {
        val insn = disasmWord(0x0000000F)
        assertEquals("fence", insn.mnemonic)
    }

    // M extension

    @Test
    fun `disassembles mul`() {
        val insns = roundTrip { mul(X10, X11, X12) }
        assertEquals("mul", insns[0].mnemonic)
    }

    @Test
    fun `disassembles mulh`() {
        val insns = roundTrip { mulh(X10, X11, X12) }
        assertEquals("mulh", insns[0].mnemonic)
    }

    @Test
    fun `disassembles mulhsu`() {
        val insns = roundTrip { mulhsu(X10, X11, X12) }
        assertEquals("mulhsu", insns[0].mnemonic)
    }

    @Test
    fun `disassembles mulhu`() {
        val insns = roundTrip { mulhu(X10, X11, X12) }
        assertEquals("mulhu", insns[0].mnemonic)
    }

    @Test
    fun `disassembles div`() {
        val insns = roundTrip { div(X10, X11, X12) }
        assertEquals("div", insns[0].mnemonic)
    }

    @Test
    fun `disassembles divu`() {
        val insns = roundTrip { divu(X10, X11, X12) }
        assertEquals("divu", insns[0].mnemonic)
    }

    @Test
    fun `disassembles rem`() {
        val insns = roundTrip { rem(X10, X11, X12) }
        assertEquals("rem", insns[0].mnemonic)
    }

    @Test
    fun `disassembles remu`() {
        val insns = roundTrip { remu(X10, X11, X12) }
        assertEquals("remu", insns[0].mnemonic)
    }

    @Test
    fun `disassembles mulw`() {
        val insns = roundTrip { mulw(X10, X11, X12) }
        assertEquals("mulw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles divw`() {
        val insns = roundTrip { divw(X10, X11, X12) }
        assertEquals("divw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles divuw`() {
        val insns = roundTrip { divuw(X10, X11, X12) }
        assertEquals("divuw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles remw`() {
        val insns = roundTrip { remw(X10, X11, X12) }
        assertEquals("remw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles remuw`() {
        val insns = roundTrip { remuw(X10, X11, X12) }
        assertEquals("remuw", insns[0].mnemonic)
    }

    // F extension: FP loads/stores

    @Test
    fun `disassembles flw`() {
        val insns = roundTrip { flw(F0, RiscVMemory(X8, 0)) }
        assertEquals("flw", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("ft0"))
    }

    @Test
    fun `disassembles fsw`() {
        val insns = roundTrip { fsw(F0, RiscVMemory(X8, 4)) }
        assertEquals("fsw", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fld`() {
        val insns = roundTrip { fld(F10, RiscVMemory(X2, 16)) }
        assertEquals("fld", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("fa0"))
    }

    @Test
    fun `disassembles fsd`() {
        val insns = roundTrip { fsd(F10, RiscVMemory(X2, -8)) }
        assertEquals("fsd", insns[0].mnemonic)
    }

    // F extension: FP arithmetic

    @Test
    fun `disassembles fadd_s`() {
        val insns = roundTrip { faddS(F0, F1, F2) }
        assertEquals("fadd.s", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("ft0"))
    }

    @Test
    fun `disassembles fsub_s`() {
        val insns = roundTrip { fsubS(F0, F1, F2) }
        assertEquals("fsub.s", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fmul_s`() {
        val insns = roundTrip { fmulS(F0, F1, F2) }
        assertEquals("fmul.s", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fdiv_s`() {
        val insns = roundTrip { fdivS(F0, F1, F2) }
        assertEquals("fdiv.s", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fsqrt_s`() {
        val insns = roundTrip { fsqrtS(F0, F1) }
        assertEquals("fsqrt.s", insns[0].mnemonic)
    }

    // D extension: FP arithmetic

    @Test
    fun `disassembles fadd_d`() {
        val insns = roundTrip { faddD(F10, F11, F12) }
        assertEquals("fadd.d", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("fa0"))
    }

    @Test
    fun `disassembles fsub_d`() {
        val insns = roundTrip { fsubD(F10, F11, F12) }
        assertEquals("fsub.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fmul_d`() {
        val insns = roundTrip { fmulD(F10, F11, F12) }
        assertEquals("fmul.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fdiv_d`() {
        val insns = roundTrip { fdivD(F10, F11, F12) }
        assertEquals("fdiv.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fsqrt_d`() {
        val insns = roundTrip { fsqrtD(F10, F11) }
        assertEquals("fsqrt.d", insns[0].mnemonic)
    }

    // FP sign injection

    @Test
    fun `disassembles fsgnj_s`() {
        val insns = roundTrip { fsgnjS(F0, F1, F2) }
        assertEquals("fsgnj.s", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fsgnjn_d`() {
        val insns = roundTrip { fsgnjnD(F0, F1, F2) }
        assertEquals("fsgnjn.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fsgnjx_s`() {
        val insns = roundTrip { fsgnjxS(F0, F1, F2) }
        assertEquals("fsgnjx.s", insns[0].mnemonic)
    }

    // FP min/max

    @Test
    fun `disassembles fmin_s`() {
        val insns = roundTrip { fminS(F0, F1, F2) }
        assertEquals("fmin.s", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fmax_s`() {
        val insns = roundTrip { fmaxS(F0, F1, F2) }
        assertEquals("fmax.s", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fmin_d`() {
        val insns = roundTrip { fminD(F10, F11, F12) }
        assertEquals("fmin.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fmax_d`() {
        val insns = roundTrip { fmaxD(F10, F11, F12) }
        assertEquals("fmax.d", insns[0].mnemonic)
    }

    // FP compare

    @Test
    fun `disassembles feq_s`() {
        val insns = roundTrip { feqS(X10, F0, F1) }
        assertEquals("feq.s", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("a0"))
    }

    @Test
    fun `disassembles flt_s`() {
        val insns = roundTrip { fltS(X10, F0, F1) }
        assertEquals("flt.s", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fle_s`() {
        val insns = roundTrip { fleS(X10, F0, F1) }
        assertEquals("fle.s", insns[0].mnemonic)
    }

    @Test
    fun `disassembles feq_d`() {
        val insns = roundTrip { feqD(X10, F10, F11) }
        assertEquals("feq.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles flt_d`() {
        val insns = roundTrip { fltD(X10, F10, F11) }
        assertEquals("flt.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fle_d`() {
        val insns = roundTrip { fleD(X10, F10, F11) }
        assertEquals("fle.d", insns[0].mnemonic)
    }

    // FP conversion

    @Test
    fun `disassembles fcvt_w_s`() {
        val insns = roundTrip { fcvtWS(X10, F0) }
        assertEquals("fcvt.w.s", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fcvt_s_w`() {
        val insns = roundTrip { fcvtSW(F0, X10) }
        assertEquals("fcvt.s.w", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fcvt_l_d`() {
        val insns = roundTrip { fcvtLD(X10, F0) }
        assertEquals("fcvt.l.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fcvt_d_l`() {
        val insns = roundTrip { fcvtDL(F0, X10) }
        assertEquals("fcvt.d.l", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fcvt_s_d`() {
        val insns = roundTrip { fcvtSD(F0, F1) }
        assertEquals("fcvt.s.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fcvt_d_s`() {
        val insns = roundTrip { fcvtDS(F0, F1) }
        assertEquals("fcvt.d.s", insns[0].mnemonic)
    }

    // FP move GP<->FP

    @Test
    fun `disassembles fmv_x_w`() {
        val insns = roundTrip { fmvXW(X10, F0) }
        assertEquals("fmv.x.w", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fmv_w_x`() {
        val insns = roundTrip { fmvWX(F0, X10) }
        assertEquals("fmv.w.x", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fmv_x_d`() {
        val insns = roundTrip { fmvXD(X10, F0) }
        assertEquals("fmv.x.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fmv_d_x`() {
        val insns = roundTrip { fmvDX(F0, X10) }
        assertEquals("fmv.d.x", insns[0].mnemonic)
    }

    // FP classify

    @Test
    fun `disassembles fclass_s`() {
        val insns = roundTrip { fclassS(X10, F0) }
        assertEquals("fclass.s", insns[0].mnemonic)
    }

    @Test
    fun `disassembles fclass_d`() {
        val insns = roundTrip { fclassD(X10, F0) }
        assertEquals("fclass.d", insns[0].mnemonic)
    }

    // Atomic extension

    @Test
    fun `disassembles lr_w`() {
        val insns = roundTrip { lrW(X10, X11) }
        assertEquals("lr.w", insns[0].mnemonic)
    }

    @Test
    fun `disassembles sc_w`() {
        val insns = roundTrip { scW(X10, X12, X11) }
        assertEquals("sc.w", insns[0].mnemonic)
    }

    @Test
    fun `disassembles lr_d`() {
        val insns = roundTrip { lrD(X10, X11) }
        assertEquals("lr.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles sc_d`() {
        val insns = roundTrip { scD(X10, X12, X11) }
        assertEquals("sc.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles amoswap_w`() {
        val insns = roundTrip { amoswapW(X10, X12, X11) }
        assertEquals("amoswap.w", insns[0].mnemonic)
    }

    @Test
    fun `disassembles amoadd_w`() {
        val insns = roundTrip { amoaddW(X10, X12, X11) }
        assertEquals("amoadd.w", insns[0].mnemonic)
    }

    @Test
    fun `disassembles amoxor_d`() {
        val insns = roundTrip { amoxorD(X10, X12, X11) }
        assertEquals("amoxor.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles amoand_d`() {
        val insns = roundTrip { amoandD(X10, X12, X11) }
        assertEquals("amoand.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles amoor_d`() {
        val insns = roundTrip { amoorD(X10, X12, X11) }
        assertEquals("amoor.d", insns[0].mnemonic)
    }

    @Test
    fun `disassembles lr_w with aq rl`() {
        val insns = roundTrip { lrW(X10, X11, aq = true, rl = true) }
        assertEquals("lr.w.aq.rl", insns[0].mnemonic)
    }

    @Test
    fun `disassembles amoswap_d with aq`() {
        val insns = roundTrip { amoswapD(X10, X12, X11, aq = true) }
        assertEquals("amoswap.d.aq", insns[0].mnemonic)
    }

    // Compressed instructions

    @Test
    fun `disassembles c_nop`() {
        val bytes = byteArrayOf(0x01, 0x00)
        val insns = disasm.disassemble(bytes)
        assertEquals(1, insns.size)
        assertEquals("c.nop", insns[0].mnemonic)
    }

    @Test
    fun `disassembles c_addi`() {
        // c.addi x10, 1 = 0x0505
        val bytes = byteArrayOf(0x05, 0x05)
        val insns = disasm.disassemble(bytes)
        assertEquals("c.addi", insns[0].mnemonic)
    }

    @Test
    fun `disassembles c_li`() {
        // c.li x10, 5 = 0x4515
        val bytes = byteArrayOf(0x15, 0x45)
        val insns = disasm.disassemble(bytes)
        assertEquals("c.li", insns[0].mnemonic)
    }

    @Test
    fun `disassembles c_mv`() {
        // c.mv x10, x11 = 0x852E
        val bytes = byteArrayOf(0x2E, 0x85.toByte())
        val insns = disasm.disassemble(bytes)
        assertEquals("c.mv", insns[0].mnemonic)
    }

    @Test
    fun `disassembles c_add`() {
        // c.add x10, x11 = 0x952E
        val bytes = byteArrayOf(0x2E, 0x95.toByte())
        val insns = disasm.disassemble(bytes)
        assertEquals("c.add", insns[0].mnemonic)
    }

    @Test
    fun `disassembles c_ebreak`() {
        val bytes = byteArrayOf(0x02, 0x90.toByte())
        val insns = disasm.disassemble(bytes)
        assertEquals("c.ebreak", insns[0].mnemonic)
    }

    @Test
    fun `disassembles c_ldsp`() {
        val insns = roundTrip { cLdsp(X10, 8) }
        assertEquals("c.ldsp", insns[0].mnemonic)
    }

    @Test
    fun `disassembles c_sdsp`() {
        val insns = roundTrip { cSdsp(X10, 8) }
        assertEquals("c.sdsp", insns[0].mnemonic)
    }

    @Test
    fun `disassembles c_lwsp`() {
        val insns = roundTrip { cLwsp(X10, 4) }
        assertEquals("c.lwsp", insns[0].mnemonic)
    }

    @Test
    fun `disassembles c_swsp`() {
        val insns = roundTrip { cSwsp(X10, 4) }
        assertEquals("c.swsp", insns[0].mnemonic)
    }

    // Edge cases

    @Test
    fun `empty byte array returns empty list`() {
        val result = disasm.disassemble(byteArrayOf())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single byte returns empty list`() {
        val result = disasm.disassemble(byteArrayOf(0x00))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `three bytes returns empty list`() {
        val result = disasm.disassemble(byteArrayOf(0x03, 0x00, 0x00))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unknown opcode decoded as word`() {
        // Non-standard opcode
        val insn = disasmWord(0x0000002B) // store opcode 0x2B with funct3=0 might be unknown
        assertNotNull(insn.mnemonic)
    }

    @Test
    fun `address tracking is correct`() {
        val insns = roundTrip {
            add(X10, X11, X12)
            sub(X13, X14, X15)
            ret()
        }
        assertEquals(3, insns.size)
        assertEquals(0L, insns[0].address)
        assertEquals(4L, insns[1].address)
        assertEquals(8L, insns[2].address)
    }

    @Test
    fun `address tracking with base address`() {
        val asm = RiscVAssembler()
        asm.add(X10, X11, X12)
        asm.ret()
        val insns = disasm.disassemble(asm.toByteArray(), 0x1000)
        assertEquals(0x1000L, insns[0].address)
        assertEquals(0x1004L, insns[1].address)
    }

    @Test
    fun `instruction text format`() {
        val insns = roundTrip { add(X10, X11, X12) }
        assertEquals("add a0, a1, a2", insns[0].text())
    }

    @Test
    fun `instruction toString same as text`() {
        val insns = roundTrip { nop() }
        assertEquals(insns[0].text(), insns[0].toString())
    }

    @Test
    fun `instruction bytes are 4 bytes for regular`() {
        val insns = roundTrip { add(X10, X11, X12) }
        assertEquals(4, insns[0].bytes.size)
    }

    @Test
    fun `disassembleOne works`() {
        val asm = RiscVAssembler()
        asm.add(X10, X11, X12)
        val bytes = asm.toByteArray()
        val word = (bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 24)
        val insn = disasm.disassembleOne(word, 0x2000)
        assertEquals("add", insn.mnemonic)
        assertEquals(0x2000L, insn.address)
    }

    // Full function round-trips

    @Test
    fun `round-trips leaf function add`() {
        val insns = roundTrip {
            add(X10, X10, X11)
            ret()
        }
        assertEquals(2, insns.size)
        assertEquals("add", insns[0].mnemonic)
        assertEquals("ret", insns[1].mnemonic)
    }

    @Test
    fun `round-trips function with stack frame`() {
        val insns = roundTrip {
            addi(X2, X2, -32)
            sd(X1, RiscVMemory(X2, 24))
            sd(X8, RiscVMemory(X2, 16))
            addi(X8, X2, 32)
            add(X10, X10, X11)
            ld(X8, RiscVMemory(X2, 16))
            ld(X1, RiscVMemory(X2, 24))
            addi(X2, X2, 32)
            ret()
        }
        assertEquals(9, insns.size)
        assertEquals("addi", insns[0].mnemonic)
        assertEquals("sd", insns[1].mnemonic)
        assertEquals("sd", insns[2].mnemonic)
        assertEquals("addi", insns[3].mnemonic)
        assertEquals("add", insns[4].mnemonic)
        assertEquals("ld", insns[5].mnemonic)
        assertEquals("ld", insns[6].mnemonic)
        assertEquals("addi", insns[7].mnemonic)
        assertEquals("ret", insns[8].mnemonic)
    }

    @Test
    fun `round-trips FP function`() {
        val insns = roundTrip {
            faddD(F10, F10, F11)
            fmulD(F10, F10, F12)
            ret()
        }
        assertEquals(3, insns.size)
        assertEquals("fadd.d", insns[0].mnemonic)
        assertEquals("fmul.d", insns[1].mnemonic)
        assertEquals("ret", insns[2].mnemonic)
    }

    @Test
    fun `round-trips mixed GP and FP`() {
        val insns = roundTrip {
            fcvtDW(F10, X10)
            faddD(F10, F10, F11)
            fcvtWD(X10, F10)
            ret()
        }
        assertEquals(4, insns.size)
        assertEquals("fcvt.d.w", insns[0].mnemonic)
        assertEquals("fadd.d", insns[1].mnemonic)
        assertEquals("fcvt.w.d", insns[2].mnemonic)
        assertEquals("ret", insns[3].mnemonic)
    }

    @Test
    fun `round-trips all R-type arithmetic`() {
        val insns = roundTrip {
            add(X10, X11, X12)
            sub(X10, X11, X12)
            sll(X10, X11, X12)
            srl(X10, X11, X12)
            sra(X10, X11, X12)
            slt(X10, X11, X12)
            sltu(X10, X11, X12)
            and(X10, X11, X12)
            or(X10, X11, X12)
            xor(X10, X11, X12)
        }
        assertEquals(10, insns.size)
        assertEquals("add", insns[0].mnemonic)
        assertEquals("sub", insns[1].mnemonic)
        assertEquals("sll", insns[2].mnemonic)
        assertEquals("srl", insns[3].mnemonic)
        assertEquals("sra", insns[4].mnemonic)
        assertEquals("slt", insns[5].mnemonic)
        assertEquals("sltu", insns[6].mnemonic)
        assertEquals("and", insns[7].mnemonic)
        assertEquals("or", insns[8].mnemonic)
        assertEquals("xor", insns[9].mnemonic)
    }

    @Test
    fun `round-trips all M extension`() {
        val insns = roundTrip {
            mul(X10, X11, X12)
            mulh(X10, X11, X12)
            mulhsu(X10, X11, X12)
            mulhu(X10, X11, X12)
            div(X10, X11, X12)
            divu(X10, X11, X12)
            rem(X10, X11, X12)
            remu(X10, X11, X12)
        }
        assertEquals(8, insns.size)
        assertEquals("mul", insns[0].mnemonic)
        assertEquals("mulh", insns[1].mnemonic)
        assertEquals("mulhsu", insns[2].mnemonic)
        assertEquals("mulhu", insns[3].mnemonic)
        assertEquals("div", insns[4].mnemonic)
        assertEquals("divu", insns[5].mnemonic)
        assertEquals("rem", insns[6].mnemonic)
        assertEquals("remu", insns[7].mnemonic)
    }

    @Test
    fun `total decoded bytes match input for valid sequence`() {
        val asm = RiscVAssembler()
        asm.add(X10, X11, X12)
        asm.sub(X13, X14, X15)
        asm.ret()
        val bytes = asm.toByteArray()
        val insns = disasm.disassemble(bytes)
        assertEquals(bytes.size, insns.size * 4)
    }
}
