package org.kgen.backend.riscv

import org.kgen.backend.riscv.asm.RiscVAssembler
import org.kgen.backend.riscv.disasm.RiscVDisassembler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RiscVAssemblerExtendedTest {

    private fun insn(block: RiscVAssembler.() -> Unit): Int {
        val asm = RiscVAssembler()
        asm.block()
        val bytes = asm.toByteArray()
        assertEquals(4, bytes.size, "Expected single 32-bit instruction")
        return readU32(bytes, 0)
    }

    private fun readU32(bytes: ByteArray, off: Int): Int =
        (bytes[off].toInt() and 0xFF) or
        ((bytes[off + 1].toInt() and 0xFF) shl 8) or
        ((bytes[off + 2].toInt() and 0xFF) shl 16) or
        ((bytes[off + 3].toInt() and 0xFF) shl 24)

    // ---- R-type instructions not in base test ----

    @Test
    fun encodesXor() {
        val i = insn { xor(X10, X11, X12) }
        assertEquals(0x33, i and 0x7F)
        assertEquals(4, (i shr 12) and 0x7)
    }

    @Test
    fun encodesOr() {
        val i = insn { or(X10, X11, X12) }
        assertEquals(0x33, i and 0x7F)
        assertEquals(6, (i shr 12) and 0x7)
    }

    @Test
    fun encodesSrl() {
        val i = insn { srl(X10, X11, X12) }
        assertEquals(0x33, i and 0x7F)
        assertEquals(5, (i shr 12) and 0x7)
        assertEquals(0, (i shr 25) and 0x7F)
    }

    @Test
    fun encodesSra() {
        val i = insn { sra(X10, X11, X12) }
        assertEquals(0x33, i and 0x7F)
        assertEquals(5, (i shr 12) and 0x7)
        assertEquals(0x20, (i shr 25) and 0x7F)
    }

    @Test
    fun encodesSlt() {
        val i = insn { slt(X10, X11, X12) }
        assertEquals((0 shl 25) or (12 shl 20) or (11 shl 15) or (2 shl 12) or (10 shl 7) or 0x33, i)
    }

    @Test
    fun encodesSltu() {
        val i = insn { sltu(X10, X11, X12) }
        assertEquals(3, (i shr 12) and 0x7)
        assertEquals(0x33, i and 0x7F)
    }

    // ---- I-type immediates not in base test ----

    @Test
    fun encodesSlti() {
        val i = insn { slti(X10, X11, 42) }
        assertEquals((42 shl 20) or (11 shl 15) or (2 shl 12) or (10 shl 7) or 0x13, i)
    }

    @Test
    fun encodesSltiu() {
        val i = insn { sltiu(X10, X11, 1) }
        assertEquals(3, (i shr 12) and 0x7)
        assertEquals(0x13, i and 0x7F)
    }

    @Test
    fun encodesXori() {
        val i = insn { xori(X10, X11, 0xFF) }
        assertEquals(4, (i shr 12) and 0x7)
        assertEquals(0x13, i and 0x7F)
    }

    @Test
    fun encodesOri() {
        val i = insn { ori(X10, X11, 0x123) }
        assertEquals(6, (i shr 12) and 0x7)
        assertEquals(0x123, (i shr 20) and 0xFFF)
    }

    @Test
    fun encodesAndi() {
        val i = insn { andi(X10, X11, 0x7) }
        assertEquals(7, (i shr 12) and 0x7)
        assertEquals(0x13, i and 0x7F)
    }

    @Test
    fun encodesSrli() {
        val i = insn { srli(X10, X11, 5) }
        assertEquals(5, (i shr 12) and 0x7)
        assertEquals(5, (i shr 20) and 0x3F)
    }

    // ---- RV64I word ops ----

    @Test
    fun encodesSubw() {
        val i = insn { subw(X10, X11, X12) }
        assertEquals((0x20 shl 25) or (12 shl 20) or (11 shl 15) or (0 shl 12) or (10 shl 7) or 0x3B, i)
    }

    @Test
    fun encodesSllw() {
        val i = insn { sllw(X10, X11, X12) }
        assertEquals(0x3B, i and 0x7F)
        assertEquals(1, (i shr 12) and 0x7)
    }

    @Test
    fun encodesSrlw() {
        val i = insn { srlw(X10, X11, X12) }
        assertEquals(0x3B, i and 0x7F)
        assertEquals(5, (i shr 12) and 0x7)
        assertEquals(0, (i shr 25) and 0x7F)
    }

    @Test
    fun encodesSraw() {
        val i = insn { sraw(X10, X11, X12) }
        assertEquals(0x3B, i and 0x7F)
        assertEquals(5, (i shr 12) and 0x7)
        assertEquals(0x20, (i shr 25) and 0x7F)
    }

    @Test
    fun encodesSlliw() {
        val i = insn { slliw(X10, X11, 3) }
        assertEquals(0x1B, i and 0x7F)
        assertEquals(1, (i shr 12) and 0x7)
    }

    @Test
    fun encodesSrliw() {
        val i = insn { srliw(X10, X11, 3) }
        assertEquals(0x1B, i and 0x7F)
        assertEquals(5, (i shr 12) and 0x7)
    }

    @Test
    fun encodesSraiw() {
        val i = insn { sraiw(X10, X11, 4) }
        assertEquals(0x1B, i and 0x7F)
        assertEquals(5, (i shr 12) and 0x7)
        assertEquals(0x404, (i shr 20) and 0xFFF)
    }

    // ---- Load variants not in base test ----

    @Test
    fun encodesLb() {
        val i = insn { lb(X10, RiscVMemory(X11, 4)) }
        assertEquals(0x03, i and 0x7F)
        assertEquals(0, (i shr 12) and 0x7)
    }

    @Test
    fun encodesLh() {
        val i = insn { lh(X10, RiscVMemory(X11, 2)) }
        assertEquals(0x03, i and 0x7F)
        assertEquals(1, (i shr 12) and 0x7)
    }

    @Test
    fun encodesLbu() {
        val i = insn { lbu(X10, RiscVMemory(X11, 0)) }
        assertEquals(0x03, i and 0x7F)
        assertEquals(4, (i shr 12) and 0x7)
    }

    @Test
    fun encodesLhu() {
        val i = insn { lhu(X10, RiscVMemory(X11, 0)) }
        assertEquals(0x03, i and 0x7F)
        assertEquals(5, (i shr 12) and 0x7)
    }

    @Test
    fun encodesLwu() {
        val i = insn { lwu(X10, RiscVMemory(X11, 0)) }
        assertEquals(0x03, i and 0x7F)
        assertEquals(6, (i shr 12) and 0x7)
    }

    // ---- Store variants ----

    @Test
    fun encodesSb() {
        val i = insn { sb(X10, RiscVMemory(X11, 1)) }
        assertEquals(0x23, i and 0x7F)
        assertEquals(0, (i shr 12) and 0x7)
    }

    @Test
    fun encodesSh() {
        val i = insn { sh(X10, RiscVMemory(X11, 2)) }
        assertEquals(0x23, i and 0x7F)
        assertEquals(1, (i shr 12) and 0x7)
    }

    // ---- Branch variants ----

    @Test
    fun encodesBlt() {
        val i = insn { blt(X10, X11, 8) }
        assertEquals(0x63, i and 0x7F)
        assertEquals(4, (i shr 12) and 0x7)
    }

    @Test
    fun encodesBge() {
        val i = insn { bge(X10, X11, 8) }
        assertEquals(0x63, i and 0x7F)
        assertEquals(5, (i shr 12) and 0x7)
    }

    @Test
    fun encodesBltu() {
        val i = insn { bltu(X10, X11, 8) }
        assertEquals(0x63, i and 0x7F)
        assertEquals(6, (i shr 12) and 0x7)
    }

    @Test
    fun encodesBgeu() {
        val i = insn { bgeu(X10, X11, 8) }
        assertEquals(0x63, i and 0x7F)
        assertEquals(7, (i shr 12) and 0x7)
    }

    @Test
    fun branchLabelsBltu() {
        val asm = RiscVAssembler()
        asm.bltu(X10, X11, "target")
        asm.nop()
        asm.label("target")
        asm.nop()
        assertEquals(12, asm.toByteArray().size)
    }

    @Test
    fun branchLabelsBge() {
        val asm = RiscVAssembler()
        asm.bge(X10, X11, "target")
        asm.nop()
        asm.label("target")
        asm.nop()
        assertEquals(12, asm.toByteArray().size)
    }

    // ---- JALR ----

    @Test
    fun encodesJalr() {
        val i = insn { jalr(X1, X10, 0) }
        assertEquals(0x67, i and 0x7F)
        assertEquals(0, (i shr 12) and 0x7)
        assertEquals(1, (i shr 7) and 0x1F)
    }

    @Test
    fun encodesJalrWithOffset() {
        val i = insn { jalr(X1, X10, 16) }
        assertEquals(16, (i shr 20) and 0xFFF)
    }

    // ---- M extension: mulh, mulhsu, mulhu, divu, remu ----

    @Test
    fun encodesMulh() {
        val i = insn { mulh(X10, X11, X12) }
        assertEquals((0x01 shl 25) or (12 shl 20) or (11 shl 15) or (1 shl 12) or (10 shl 7) or 0x33, i)
    }

    @Test
    fun encodesMulhsu() {
        val i = insn { mulhsu(X10, X11, X12) }
        assertEquals(2, (i shr 12) and 0x7) // funct3
        assertEquals(1, (i shr 25) and 0x7F) // funct7
    }

    @Test
    fun encodesMulhu() {
        val i = insn { mulhu(X10, X11, X12) }
        assertEquals(3, (i shr 12) and 0x7)
    }

    @Test
    fun encodesDivu() {
        val i = insn { divu(X10, X11, X12) }
        assertEquals(5, (i shr 12) and 0x7)
        assertEquals(1, (i shr 25) and 0x7F)
    }

    @Test
    fun encodesRemu() {
        val i = insn { remu(X10, X11, X12) }
        assertEquals(7, (i shr 12) and 0x7)
        assertEquals(1, (i shr 25) and 0x7F)
    }

    @Test
    fun encodesDivuw() {
        val i = insn { divuw(X10, X11, X12) }
        assertEquals(0x3B, i and 0x7F)
        assertEquals(5, (i shr 12) and 0x7)
    }

    @Test
    fun encodesRemuw() {
        val i = insn { remuw(X10, X11, X12) }
        assertEquals(0x3B, i and 0x7F)
        assertEquals(7, (i shr 12) and 0x7)
    }

    // ---- FP D extension extras ----

    @Test
    fun encodesFsubD() {
        val i = insn { fsubD(F10, F11, F12) }
        assertEquals(0x53, i and 0x7F)
        assertEquals(0x05, (i shr 25) and 0x7F)
    }

    @Test
    fun encodesFdivD() {
        val i = insn { fdivD(F10, F11, F12) }
        assertEquals(0x53, i and 0x7F)
        assertEquals(0x0D, (i shr 25) and 0x7F)
    }

    @Test
    fun encodesFsqrtD() {
        val i = insn { fsqrtD(F10, F11) }
        assertEquals(0x53, i and 0x7F)
        assertEquals(0x2D, (i shr 25) and 0x7F)
    }

    @Test
    fun encodesFminD() {
        val i = insn { fminD(F10, F11, F12) }
        assertEquals(0x53, i and 0x7F)
        assertEquals(0x15, (i shr 25) and 0x7F)
        assertEquals(0, (i shr 12) and 0x7)
    }

    @Test
    fun encodesFmaxD() {
        val i = insn { fmaxD(F10, F11, F12) }
        assertEquals(0x53, i and 0x7F)
        assertEquals(0x15, (i shr 25) and 0x7F)
        assertEquals(1, (i shr 12) and 0x7)
    }

    @Test
    fun encodesFltD() {
        val i = insn { fltD(X10, F11, F12) }
        assertEquals(0x53, i and 0x7F)
        assertEquals(0x51, (i shr 25) and 0x7F)
        assertEquals(1, (i shr 12) and 0x7)
    }

    @Test
    fun encodesFleD() {
        val i = insn { fleD(X10, F11, F12) }
        assertEquals(0, (i shr 12) and 0x7)
    }

    @Test
    fun encodesFsd() {
        val i = insn { fsd(F10, RiscVMemory(X11, 8)) }
        assertEquals(0x27, i and 0x7F)
        assertEquals(3, (i shr 12) and 0x7) // funct3 for double
    }

    // ---- FP S extension extras ----

    @Test
    fun encodesFmaxS() {
        val i = insn { fmaxS(F10, F11, F12) }
        assertEquals(0x14, (i shr 25) and 0x7F)
        assertEquals(1, (i shr 12) and 0x7)
    }

    @Test
    fun encodesFleS() {
        val i = insn { fleS(X10, F11, F12) }
        assertEquals(0x50, (i shr 25) and 0x7F)
        assertEquals(0, (i shr 12) and 0x7)
    }

    @Test
    fun encodesFsw() {
        val i = insn { fsw(F10, RiscVMemory(X11, 4)) }
        assertEquals(0x27, i and 0x7F)
        assertEquals(2, (i shr 12) and 0x7)
    }

    @Test
    fun encodesFclassS() {
        val i = insn { fclassS(X10, F11) }
        assertEquals(0x70, (i shr 25) and 0x7F)
        assertEquals(1, (i shr 12) and 0x7)
    }

    @Test
    fun encodesFclassD() {
        val i = insn { fclassD(X10, F11) }
        assertEquals(0x71, (i shr 25) and 0x7F)
        assertEquals(1, (i shr 12) and 0x7)
    }

    // ---- FP conversion extras ----

    @Test
    fun encodesFcvtWuS() {
        val i = insn { fcvtWuS(X10, F11) }
        assertEquals(0x60, (i shr 25) and 0x7F)
        assertEquals(1, (i shr 20) and 0x1F) // rs2 = 1 (unsigned)
    }

    @Test
    fun encodesFcvtLS() {
        val i = insn { fcvtLS(X10, F11) }
        assertEquals(0x60, (i shr 25) and 0x7F)
        assertEquals(2, (i shr 20) and 0x1F) // rs2 = 2 (long)
    }

    @Test
    fun encodesFcvtSWu() {
        val i = insn { fcvtSWu(F10, X11) }
        assertEquals(0x68, (i shr 25) and 0x7F)
        assertEquals(1, (i shr 20) and 0x1F)
    }

    @Test
    fun encodesFcvtWD() {
        val i = insn { fcvtWD(X10, F11) }
        assertEquals(0x61, (i shr 25) and 0x7F)
        assertEquals(0, (i shr 20) and 0x1F)
    }

    @Test
    fun encodesFcvtDW() {
        val i = insn { fcvtDW(F10, X11) }
        assertEquals(0x69, (i shr 25) and 0x7F)
        assertEquals(0, (i shr 20) and 0x1F)
    }

    @Test
    fun encodesFmvWX() {
        val i = insn { fmvWX(F10, X11) }
        assertEquals(0x78, (i shr 25) and 0x7F)
    }

    @Test
    fun encodesFmvDX() {
        val i = insn { fmvDX(F10, X11) }
        assertEquals(0x79, (i shr 25) and 0x7F)
    }

    // ---- FP pseudos ----

    @Test
    fun encodesFmvS() {
        val i = insn { fmvS(F10, F11) }
        // fmvS is fsgnjS(rd, rs, rs)
        assertEquals(0x10, (i shr 25) and 0x7F) // fsgnj
        assertEquals(0, (i shr 12) and 0x7)
    }

    @Test
    fun encodesFnegS() {
        val i = insn { fnegS(F10, F11) }
        assertEquals(0x10, (i shr 25) and 0x7F)
        assertEquals(1, (i shr 12) and 0x7) // fsgnjn
    }

    @Test
    fun encodesFabsS() {
        val i = insn { fabsS(F10, F11) }
        assertEquals(0x10, (i shr 25) and 0x7F)
        assertEquals(2, (i shr 12) and 0x7) // fsgnjx
    }

    @Test
    fun encodesFmvD() {
        val i = insn { fmvD(F10, F11) }
        assertEquals(0x11, (i shr 25) and 0x7F)
    }

    @Test
    fun encodesFnegD() {
        val i = insn { fnegD(F10, F11) }
        assertEquals(0x11, (i shr 25) and 0x7F)
        assertEquals(1, (i shr 12) and 0x7)
    }

    @Test
    fun encodesFabsD() {
        val i = insn { fabsD(F10, F11) }
        assertEquals(0x11, (i shr 25) and 0x7F)
        assertEquals(2, (i shr 12) and 0x7)
    }

    // ---- Atomic A extension extras ----

    @Test
    fun encodesScW() {
        val i = insn { scW(X10, X12, X11) }
        assertEquals(0x2F, i and 0x7F)
        assertEquals(2, (i shr 12) and 0x7) // funct3 = word
    }

    @Test
    fun encodesScD() {
        val i = insn { scD(X10, X12, X11) }
        assertEquals(0x2F, i and 0x7F)
        assertEquals(3, (i shr 12) and 0x7) // funct3 = dword
    }

    @Test
    fun encodesAmoxorW() {
        val i = insn { amoxorW(X10, X12, X11) }
        assertEquals(0x2F, i and 0x7F)
        val funct5 = (i shr 27) and 0x1F
        assertEquals(0x04, funct5)
    }

    @Test
    fun encodesAmoandW() {
        val i = insn { amoandW(X10, X12, X11) }
        val funct5 = (i shr 27) and 0x1F
        assertEquals(0x0C, funct5)
    }

    @Test
    fun encodesAmoorW() {
        val i = insn { amoorW(X10, X12, X11) }
        val funct5 = (i shr 27) and 0x1F
        assertEquals(0x08, funct5)
    }

    @Test
    fun encodesAmominW() {
        val i = insn { amominW(X10, X12, X11) }
        val funct5 = (i shr 27) and 0x1F
        assertEquals(0x10, funct5)
    }

    @Test
    fun encodesAmomaxW() {
        val i = insn { amomaxW(X10, X12, X11) }
        val funct5 = (i shr 27) and 0x1F
        assertEquals(0x14, funct5)
    }

    @Test
    fun encodesAmominuW() {
        val i = insn { amominuW(X10, X12, X11) }
        val funct5 = (i shr 27) and 0x1F
        assertEquals(0x18, funct5)
    }

    @Test
    fun encodesAmomaxuW() {
        val i = insn { amomaxuW(X10, X12, X11) }
        val funct5 = (i shr 27) and 0x1F
        assertEquals(0x1C, funct5)
    }

    @Test
    fun encodesAmoswapD() {
        val i = insn { amoswapD(X10, X12, X11) }
        assertEquals(3, (i shr 12) and 0x7) // funct3 = dword
        val funct5 = (i shr 27) and 0x1F
        assertEquals(0x01, funct5)
    }

    @Test
    fun encodesAmoxorD() {
        val i = insn { amoxorD(X10, X12, X11) }
        assertEquals(3, (i shr 12) and 0x7)
        assertEquals(0x04, (i shr 27) and 0x1F)
    }

    // ---- Compressed extension extras ----

    @Test
    fun encodesCAddiw() {
        val asm = RiscVAssembler()
        asm.cAddiw(X10, 5)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
        val inst = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        assertEquals(0x01, inst and 0x03) // quadrant 01
    }

    @Test
    fun encodesCJalr() {
        val asm = RiscVAssembler()
        asm.cJalr(X1)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCLwsp() {
        val asm = RiscVAssembler()
        asm.cLwsp(X10, 8)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCLdsp() {
        val asm = RiscVAssembler()
        asm.cLdsp(X10, 16)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCSwsp() {
        val asm = RiscVAssembler()
        asm.cSwsp(X10, 8)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCBeqz() {
        val asm = RiscVAssembler()
        asm.cBeqz(X10, 8)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCBnez() {
        val asm = RiscVAssembler()
        asm.cBnez(X10, 8)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCJ() {
        val asm = RiscVAssembler()
        asm.cJ(32)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCSubW() {
        val asm = RiscVAssembler()
        asm.cSub(X10, X11)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCAndi() {
        val asm = RiscVAssembler()
        asm.cAndi(X10, 7)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCOr() {
        val asm = RiscVAssembler()
        asm.cOr(X10, X11)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCXor() {
        val asm = RiscVAssembler()
        asm.cXor(X10, X11)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCAddw() {
        val asm = RiscVAssembler()
        asm.cAddw(X10, X11)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCSubw() {
        val asm = RiscVAssembler()
        asm.cSubw(X10, X11)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCLui() {
        val asm = RiscVAssembler()
        asm.cLui(X10, 0x12000)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCAddi16sp() {
        val asm = RiscVAssembler()
        asm.cAddi16sp(-16)
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun encodesCEbreak() {
        val asm = RiscVAssembler()
        asm.cEbreak()
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size)
        val inst = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        assertEquals(0x9002, inst)
    }

    // ---- System instructions ----

    @Test
    fun encodesFence() {
        val i = insn { fence() }
        assertEquals(0x0FF0000F, i)
    }

    @Test
    fun encodesFenceI() {
        val i = insn { fenceI() }
        assertEquals(0x0000100F, i)
    }

    // ---- Pseudo-instructions ----

    @Test
    fun encodesNot() {
        val i = insn { not(X10, X11) }
        // xori x10, x11, -1
        assertEquals(0x13, i and 0x7F)
        assertEquals(4, (i shr 12) and 0x7)
        assertEquals(0xFFF, (i shr 20) and 0xFFF)
    }

    @Test
    fun encodesNegw() {
        val i = insn { negw(X10, X11) }
        // subw x10, x0, x11
        assertEquals(0x3B, i and 0x7F)
        assertEquals(0, (i shr 15) and 0x1F) // rs1 = x0
    }

    @Test
    fun encodesSnez() {
        val i = insn { snez(X10, X11) }
        // sltu x10, x0, x11
        assertEquals(0x33, i and 0x7F)
        assertEquals(3, (i shr 12) and 0x7)
        assertEquals(0, (i shr 15) and 0x1F) // rs1 = x0
    }

    @Test
    fun encodesJLabel() {
        val asm = RiscVAssembler()
        asm.j("target")
        asm.nop()
        asm.label("target")
        asm.ret()
        val bytes = asm.toByteArray()
        assertEquals(12, bytes.size)
    }

    @Test
    fun encodesLiLargeNegative() {
        val asm = RiscVAssembler()
        asm.li(X10, -100000)
        val bytes = asm.toByteArray()
        assertTrue(bytes.size >= 8, "Large negative needs lui + addi")
    }

    // ---- Disassembler round-trip ----

    @Test
    fun roundTripFpInstructions() {
        val disasm = RiscVDisassembler()
        val asm = RiscVAssembler()
        asm.faddS(F10, F11, F12)
        asm.fsubS(F10, F11, F12)
        asm.fmulS(F10, F11, F12)
        asm.fdivS(F10, F11, F12)
        asm.faddD(F10, F11, F12)
        asm.fsubD(F10, F11, F12)
        val insns = disasm.disassemble(asm.toByteArray())
        assertEquals(6, insns.size)
        // Verify all are decoded as FP instructions
        for (insn in insns) {
            assertTrue(insn.mnemonic.startsWith("f"), "Expected FP mnemonic but got: ${insn.mnemonic}")
        }
        // First should be fadd.s
        assertEquals("fadd.s", insns[0].mnemonic)
    }

    @Test
    fun roundTripAtomics() {
        val disasm = RiscVDisassembler()
        val asm = RiscVAssembler()
        asm.lrW(X10, X11)
        asm.scW(X10, X12, X11)
        asm.amoswapW(X10, X12, X11)
        asm.amoaddW(X10, X12, X11)
        val insns = disasm.disassemble(asm.toByteArray())
        assertEquals(4, insns.size)
        assertEquals("lr.w", insns[0].mnemonic)
        assertEquals("sc.w", insns[1].mnemonic)
        assertEquals("amoswap.w", insns[2].mnemonic)
        assertEquals("amoadd.w", insns[3].mnemonic)
    }

    @Test
    fun sizeTracking() {
        val asm = RiscVAssembler()
        assertEquals(0, asm.size)
        asm.nop()
        assertEquals(4, asm.size)
        asm.cNop()
        assertEquals(6, asm.size)
    }

    @Test
    fun unresolvedLabels() {
        val asm = RiscVAssembler()
        asm.beq(X10, X0, "missing")
        val unresolved = asm.unresolvedLabels()
        assertEquals(1, unresolved.size)
        assertEquals("missing", unresolved[0].second)
    }
}
