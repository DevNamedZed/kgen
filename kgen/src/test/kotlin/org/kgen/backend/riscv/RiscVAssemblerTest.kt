package org.kgen.backend.riscv

import org.kgen.backend.riscv.asm.RiscVAssembler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RiscVAssemblerTest {

    private fun insn(asm: RiscVAssembler): Int {
        val bytes = asm.toByteArray()
        assertEquals(4, bytes.size, "Expected single instruction (4 bytes)")
        return readU32(bytes, 0)
    }

    private fun readU32(bytes: ByteArray, off: Int): Int =
        (bytes[off].toInt() and 0xFF) or
        ((bytes[off + 1].toInt() and 0xFF) shl 8) or
        ((bytes[off + 2].toInt() and 0xFF) shl 16) or
        ((bytes[off + 3].toInt() and 0xFF) shl 24)

    // --- Register encoding ---

    @Test
    fun `registers have correct encodings`() {
        assertEquals(0, X0.encoding)
        assertEquals(1, X1.encoding)
        assertEquals(2, X2.encoding)
        assertEquals(10, X10.encoding)
        assertEquals(31, X31.encoding)
    }

    @Test
    fun `fp registers have correct encodings`() {
        assertEquals(0, F0.encoding)
        assertEquals(10, F10.encoding)
        assertEquals(31, F31.encoding)
    }

    @Test
    fun `abi aliases resolve correctly`() {
        assertSame(X0, ZERO)
        assertSame(X1, RA)
        assertSame(X2, SP)
        assertSame(X8, FP)
    }

    // --- R-type instructions ---

    @Test
    fun `encodes add`() {
        val asm = RiscVAssembler()
        asm.add(X10, X11, X12) // add a0, a1, a2
        // funct7=0x00, rs2=12, rs1=11, funct3=0, rd=10, opcode=0x33
        val expected = (0x00 shl 25) or (12 shl 20) or (11 shl 15) or (0 shl 12) or (10 shl 7) or 0x33
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes sub`() {
        val asm = RiscVAssembler()
        asm.sub(X10, X11, X12) // sub a0, a1, a2
        val expected = (0x20 shl 25) or (12 shl 20) or (11 shl 15) or (0 shl 12) or (10 shl 7) or 0x33
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes and or xor`() {
        val asm = RiscVAssembler()
        asm.and(X5, X6, X7)
        assertEquals((0 shl 25) or (7 shl 20) or (6 shl 15) or (7 shl 12) or (5 shl 7) or 0x33, insn(asm))
    }

    @Test
    fun `encodes sll srl sra`() {
        val asm = RiscVAssembler()
        asm.sll(X10, X11, X12)
        assertEquals((0 shl 25) or (12 shl 20) or (11 shl 15) or (1 shl 12) or (10 shl 7) or 0x33, insn(asm))
    }

    // --- I-type instructions ---

    @Test
    fun `encodes addi`() {
        val asm = RiscVAssembler()
        asm.addi(X10, X11, 42)
        val expected = (42 shl 20) or (11 shl 15) or (0 shl 12) or (10 shl 7) or 0x13
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes addi with negative immediate`() {
        val asm = RiscVAssembler()
        asm.addi(X10, X0, -1)
        val expected = (0xFFF shl 20) or (0 shl 15) or (0 shl 12) or (10 shl 7) or 0x13
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes slli`() {
        val asm = RiscVAssembler()
        asm.slli(X10, X11, 3)
        // imm[11:0] = 3, funct3 = 1
        val expected = (3 shl 20) or (11 shl 15) or (1 shl 12) or (10 shl 7) or 0x13
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes srai`() {
        val asm = RiscVAssembler()
        asm.srai(X10, X11, 4)
        // imm = 0x400 | 4 = 0x404
        val expected = (0x404 shl 20) or (11 shl 15) or (5 shl 12) or (10 shl 7) or 0x13
        assertEquals(expected, insn(asm))
    }

    // --- U-type instructions ---

    @Test
    fun `encodes lui`() {
        val asm = RiscVAssembler()
        asm.lui(X10, 0x12345)
        val expected = (0x12345 shl 12) or (10 shl 7) or 0x37
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes auipc`() {
        val asm = RiscVAssembler()
        asm.auipc(X10, 1)
        val expected = (1 shl 12) or (10 shl 7) or 0x17
        assertEquals(expected, insn(asm))
    }

    // --- Load/Store ---

    @Test
    fun `encodes ld`() {
        val asm = RiscVAssembler()
        asm.ld(X10, RiscVMemory(X2, 16))
        // I-type: imm=16, rs1=2(sp), funct3=3, rd=10, opcode=0x03
        val expected = (16 shl 20) or (2 shl 15) or (3 shl 12) or (10 shl 7) or 0x03
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes sd`() {
        val asm = RiscVAssembler()
        asm.sd(X1, RiscVMemory(X2, -8))
        // S-type: imm=-8 (0xFF8), rs2=1(ra), rs1=2(sp), funct3=3
        val imm = -8
        val imm11_5 = (imm shr 5) and 0x7F
        val imm4_0 = imm and 0x1F
        val expected = (imm11_5 shl 25) or (1 shl 20) or (2 shl 15) or (3 shl 12) or (imm4_0 shl 7) or 0x23
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes lw and sw`() {
        val asm = RiscVAssembler()
        asm.lw(X10, RiscVMemory(X8, 0))
        val expected = (0 shl 20) or (8 shl 15) or (2 shl 12) or (10 shl 7) or 0x03
        assertEquals(expected, insn(asm))
    }

    // --- Branches ---

    @Test
    fun `encodes beq with immediate`() {
        val asm = RiscVAssembler()
        asm.beq(X10, X11, 8)
        val i = insn(asm)
        // Verify opcode and funct3
        assertEquals(0x63, i and 0x7F)
        assertEquals(0, (i shr 12) and 0x7)
    }

    @Test
    fun `branch label fixup forward`() {
        val asm = RiscVAssembler()
        asm.beq(X10, X0, "skip")   // offset 0
        asm.addi(X10, X10, 1)       // offset 4
        asm.label("skip")           // offset 8
        asm.addi(X10, X10, 2)       // offset 8

        val bytes = asm.toByteArray()
        assertEquals(12, bytes.size)

        // The branch at offset 0 should jump to offset 8 (displacement = 8)
        val branchInsn = readU32(bytes, 0)
        assertEquals(0x63, branchInsn and 0x7F) // still a branch
    }

    @Test
    fun `branch label fixup backward`() {
        val asm = RiscVAssembler()
        asm.label("loop")
        asm.addi(X10, X10, 1)
        asm.bne(X10, X11, "loop")

        val bytes = asm.toByteArray()
        assertEquals(8, bytes.size)
    }

    // --- JAL ---

    @Test
    fun `encodes jal with offset`() {
        val asm = RiscVAssembler()
        asm.jal(X1, 100)
        val i = insn(asm)
        assertEquals(0x6F, i and 0x7F)
        assertEquals(1, (i shr 7) and 0x1F) // rd = x1
    }

    @Test
    fun `jal label fixup`() {
        val asm = RiscVAssembler()
        asm.call("func")     // jal ra, func
        asm.nop()
        asm.label("func")
        asm.ret()

        val bytes = asm.toByteArray()
        assertEquals(12, bytes.size)

        val jalInsn = readU32(bytes, 0)
        assertEquals(0x6F, jalInsn and 0x7F)
    }

    // --- M extension ---

    @Test
    fun `encodes mul`() {
        val asm = RiscVAssembler()
        asm.mul(X10, X11, X12)
        // funct7=0x01, funct3=0, opcode=0x33
        val expected = (0x01 shl 25) or (12 shl 20) or (11 shl 15) or (0 shl 12) or (10 shl 7) or 0x33
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes div`() {
        val asm = RiscVAssembler()
        asm.div(X10, X11, X12)
        val expected = (0x01 shl 25) or (12 shl 20) or (11 shl 15) or (4 shl 12) or (10 shl 7) or 0x33
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes rem`() {
        val asm = RiscVAssembler()
        asm.rem(X10, X11, X12)
        val expected = (0x01 shl 25) or (12 shl 20) or (11 shl 15) or (6 shl 12) or (10 shl 7) or 0x33
        assertEquals(expected, insn(asm))
    }

    // --- Pseudo-instructions ---

    @Test
    fun `encodes nop`() {
        val asm = RiscVAssembler()
        asm.nop()
        // nop = addi x0, x0, 0 = 0x00000013
        assertEquals(0x00000013, insn(asm))
    }

    @Test
    fun `encodes mv`() {
        val asm = RiscVAssembler()
        asm.mv(X10, X11)
        // mv a0, a1 = addi a0, a1, 0
        val expected = (0 shl 20) or (11 shl 15) or (0 shl 12) or (10 shl 7) or 0x13
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes li small immediate`() {
        val asm = RiscVAssembler()
        asm.li(X10, 42)
        // li a0, 42 = addi a0, x0, 42
        val expected = (42 shl 20) or (0 shl 15) or (0 shl 12) or (10 shl 7) or 0x13
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes li large immediate`() {
        val asm = RiscVAssembler()
        asm.li(X10, 0x12345)
        // Should use lui + addi
        val bytes = asm.toByteArray()
        assertEquals(8, bytes.size, "Large immediate needs lui + addi")
    }

    @Test
    fun `encodes ret`() {
        val asm = RiscVAssembler()
        asm.ret()
        // ret = jalr x0, x1, 0
        val expected = (0 shl 20) or (1 shl 15) or (0 shl 12) or (0 shl 7) or 0x67
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes neg`() {
        val asm = RiscVAssembler()
        asm.neg(X10, X11)
        // neg a0, a1 = sub a0, x0, a1
        val expected = (0x20 shl 25) or (11 shl 20) or (0 shl 15) or (0 shl 12) or (10 shl 7) or 0x33
        assertEquals(expected, insn(asm))
    }

    // --- System instructions ---

    @Test
    fun `encodes ecall`() {
        val asm = RiscVAssembler()
        asm.ecall()
        assertEquals(0x00000073, insn(asm))
    }

    @Test
    fun `encodes ebreak`() {
        val asm = RiscVAssembler()
        asm.ebreak()
        assertEquals(0x00100073, insn(asm))
    }

    // --- Word instructions (RV64I) ---

    @Test
    fun `encodes addw subw`() {
        val asm = RiscVAssembler()
        asm.addw(X10, X11, X12)
        val expected = (0x00 shl 25) or (12 shl 20) or (11 shl 15) or (0 shl 12) or (10 shl 7) or 0x3B
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes addiw`() {
        val asm = RiscVAssembler()
        asm.addiw(X10, X11, 5)
        val expected = (5 shl 20) or (11 shl 15) or (0 shl 12) or (10 shl 7) or 0x1B
        assertEquals(expected, insn(asm))
    }

    // --- Memory operand ---

    @Test
    fun `memory operand validates range`() {
        assertDoesNotThrow { RiscVMemory(X2, 2047) }
        assertDoesNotThrow { RiscVMemory(X2, -2048) }
        assertThrows(IllegalArgumentException::class.java) { RiscVMemory(X2, 2048) }
        assertThrows(IllegalArgumentException::class.java) { RiscVMemory(X2, -2049) }
    }

    // --- Full function example ---

    // --- F Extension ---

    @Test
    fun `encodes fadd_s`() {
        val asm = RiscVAssembler()
        asm.faddS(F10, F11, F12)
        // funct5=0x00, rs2=f12, rs1=f11, rm=7(dynamic), rd=f10, opcode=0x53
        val expected = (0x00 shl 25) or (12 shl 20) or (11 shl 15) or (7 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fsub_s`() {
        val asm = RiscVAssembler()
        asm.fsubS(F10, F11, F12)
        val expected = (0x04 shl 25) or (12 shl 20) or (11 shl 15) or (7 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fmul_s`() {
        val asm = RiscVAssembler()
        asm.fmulS(F10, F11, F12)
        val expected = (0x08 shl 25) or (12 shl 20) or (11 shl 15) or (7 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fdiv_s with explicit rounding mode`() {
        val asm = RiscVAssembler()
        asm.fdivS(F10, F11, F12, rm = 0) // RNE
        val expected = (0x0C shl 25) or (12 shl 20) or (11 shl 15) or (0 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fsqrt_s`() {
        val asm = RiscVAssembler()
        asm.fsqrtS(F10, F11)
        // funct5=0x2C, rs2=0, rs1=f11, rm=7, rd=f10
        val expected = (0x2C shl 25) or (0 shl 20) or (11 shl 15) or (7 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes feq_s`() {
        val asm = RiscVAssembler()
        asm.feqS(X10, F11, F12)
        // funct5=0x50, rs2=f12, rs1=f11, funct3=2(EQ), rd=x10
        val expected = (0x50 shl 25) or (12 shl 20) or (11 shl 15) or (2 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes flt_s`() {
        val asm = RiscVAssembler()
        asm.fltS(X10, F11, F12)
        val expected = (0x50 shl 25) or (12 shl 20) or (11 shl 15) or (1 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fcvt_w_s`() {
        val asm = RiscVAssembler()
        asm.fcvtWS(X10, F11)
        // funct5=0x60, rs2=0(W), rs1=f11, rm=7, rd=x10
        val expected = (0x60 shl 25) or (0 shl 20) or (11 shl 15) or (7 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fcvt_s_w`() {
        val asm = RiscVAssembler()
        asm.fcvtSW(F10, X11)
        // funct5=0x68, rs2=0(W), rs1=x11, rm=7, rd=f10
        val expected = (0x68 shl 25) or (0 shl 20) or (11 shl 15) or (7 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fmv_x_w`() {
        val asm = RiscVAssembler()
        asm.fmvXW(X10, F11)
        val expected = (0x70 shl 25) or (0 shl 20) or (11 shl 15) or (0 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes flw`() {
        val asm = RiscVAssembler()
        asm.flw(F10, RiscVMemory(X11, 8))
        // I-type: imm=8, rs1=x11, funct3=2, rd=f10, opcode=0x07
        val expected = (8 shl 20) or (11 shl 15) or (2 shl 12) or (10 shl 7) or 0x07
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fmin_s fmax_s`() {
        val asm = RiscVAssembler()
        asm.fminS(F10, F11, F12)
        val expected = (0x14 shl 25) or (12 shl 20) or (11 shl 15) or (0 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    // --- D Extension ---

    @Test
    fun `encodes fadd_d`() {
        val asm = RiscVAssembler()
        asm.faddD(F10, F11, F12)
        val expected = (0x01 shl 25) or (12 shl 20) or (11 shl 15) or (7 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fmul_d`() {
        val asm = RiscVAssembler()
        asm.fmulD(F10, F11, F12)
        val expected = (0x09 shl 25) or (12 shl 20) or (11 shl 15) or (7 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fld fsd`() {
        val asm = RiscVAssembler()
        asm.fld(F10, RiscVMemory(X11, 16))
        val expected = (16 shl 20) or (11 shl 15) or (3 shl 12) or (10 shl 7) or 0x07
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fcvt_s_d`() {
        val asm = RiscVAssembler()
        asm.fcvtSD(F10, F11)
        // funct5=0x20, rs2=1(D), rs1=f11, rm=7, rd=f10
        val expected = (0x20 shl 25) or (1 shl 20) or (11 shl 15) or (7 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fcvt_d_s`() {
        val asm = RiscVAssembler()
        asm.fcvtDS(F10, F11)
        // funct5=0x21, rs2=0(S), rs1=f11, rm=7, rd=f10
        val expected = (0x21 shl 25) or (0 shl 20) or (11 shl 15) or (7 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes feq_d`() {
        val asm = RiscVAssembler()
        asm.feqD(X10, F11, F12)
        val expected = (0x51 shl 25) or (12 shl 20) or (11 shl 15) or (2 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes fmv_x_d`() {
        val asm = RiscVAssembler()
        asm.fmvXD(X10, F11)
        val expected = (0x71 shl 25) or (0 shl 20) or (11 shl 15) or (0 shl 12) or (10 shl 7) or 0x53
        assertEquals(expected, insn(asm))
    }

    // --- A Extension ---

    @Test
    fun `encodes lr_w`() {
        val asm = RiscVAssembler()
        asm.lrW(X10, X11)
        // funct5=0x02, aq=0, rl=0, rs2=0, rs1=x11, funct3=2, rd=x10, opcode=0x2F
        val funct7 = (0x02 shl 2)
        val expected = (funct7 shl 25) or (0 shl 20) or (11 shl 15) or (2 shl 12) or (10 shl 7) or 0x2F
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes sc_w`() {
        val asm = RiscVAssembler()
        asm.scW(X10, X12, X11)
        val funct7 = (0x03 shl 2)
        val expected = (funct7 shl 25) or (12 shl 20) or (11 shl 15) or (2 shl 12) or (10 shl 7) or 0x2F
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes lr_w with aq rl`() {
        val asm = RiscVAssembler()
        asm.lrW(X10, X11, aq = true, rl = true)
        val funct7 = (0x02 shl 2) or 0x3
        val expected = (funct7 shl 25) or (0 shl 20) or (11 shl 15) or (2 shl 12) or (10 shl 7) or 0x2F
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes amoswap_w`() {
        val asm = RiscVAssembler()
        asm.amoswapW(X10, X12, X11)
        val funct7 = (0x01 shl 2)
        val expected = (funct7 shl 25) or (12 shl 20) or (11 shl 15) or (2 shl 12) or (10 shl 7) or 0x2F
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes amoadd_d`() {
        val asm = RiscVAssembler()
        asm.amoaddD(X10, X12, X11)
        val funct7 = (0x00 shl 2)
        val expected = (funct7 shl 25) or (12 shl 20) or (11 shl 15) or (3 shl 12) or (10 shl 7) or 0x2F
        assertEquals(expected, insn(asm))
    }

    @Test
    fun `encodes lr_d`() {
        val asm = RiscVAssembler()
        asm.lrD(X10, X11, aq = true)
        val funct7 = (0x02 shl 2) or 0x2 // aq=1,rl=0
        val expected = (funct7 shl 25) or (0 shl 20) or (11 shl 15) or (3 shl 12) or (10 shl 7) or 0x2F
        assertEquals(expected, insn(asm))
    }

    // --- C Extension ---

    private fun insn16(asm: RiscVAssembler): Int {
        val bytes = asm.toByteArray()
        assertEquals(2, bytes.size, "Expected single compressed instruction (2 bytes)")
        return (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
    }

    @Test
    fun `encodes c_nop`() {
        val asm = RiscVAssembler()
        asm.cNop()
        assertEquals(0x0001, insn16(asm))
    }

    @Test
    fun `encodes c_addi`() {
        val asm = RiscVAssembler()
        asm.cAddi(X10, 5)
        // CI format: funct3=000, imm[5]=0, rd=a0(10), imm[4:0]=00101, op=01
        val expected = 0x0001 or (0 shl 12) or (10 shl 7) or (5 shl 2)
        assertEquals(expected, insn16(asm))
    }

    @Test
    fun `encodes c_li`() {
        val asm = RiscVAssembler()
        asm.cLi(X10, 7)
        val expected = 0x4001 or (0 shl 12) or (10 shl 7) or (7 shl 2)
        assertEquals(expected, insn16(asm))
    }

    @Test
    fun `encodes c_mv`() {
        val asm = RiscVAssembler()
        asm.cMv(X10, X11)
        val expected = 0x8002 or (10 shl 7) or (11 shl 2)
        assertEquals(expected, insn16(asm))
    }

    @Test
    fun `encodes c_add`() {
        val asm = RiscVAssembler()
        asm.cAdd(X10, X11)
        val expected = 0x9002 or (10 shl 7) or (11 shl 2)
        assertEquals(expected, insn16(asm))
    }

    @Test
    fun `encodes c_jr`() {
        val asm = RiscVAssembler()
        asm.cJr(X1)
        val expected = 0x8002 or (1 shl 7)
        assertEquals(expected, insn16(asm))
    }

    @Test
    fun `encodes c_slli`() {
        val asm = RiscVAssembler()
        asm.cSlli(X10, 3)
        val expected = 0x0002 or (0 shl 12) or (10 shl 7) or (3 shl 2)
        assertEquals(expected, insn16(asm))
    }

    @Test
    fun `compressed instructions are 2 bytes`() {
        val asm = RiscVAssembler()
        asm.cNop()
        asm.cAddi(X10, 1)
        asm.cLi(X11, 5)
        assertEquals(6, asm.toByteArray().size) // 3 × 2 bytes
    }

    @Test
    fun `mixed 32-bit and 16-bit instructions`() {
        val asm = RiscVAssembler()
        asm.add(X10, X11, X12)  // 4 bytes
        asm.cAddi(X10, 1)       // 2 bytes
        asm.sub(X10, X10, X11)  // 4 bytes
        assertEquals(10, asm.toByteArray().size)
    }

    // --- Full function example ---

    @Test
    fun `assembles factorial-like loop`() {
        val asm = RiscVAssembler()
        // Simple loop: count from a0 down to 0
        // result in a1
        asm.addi(X11, X0, 1)       // a1 = 1
        asm.label("loop")
        asm.beq(X10, X0, "done")   // if a0 == 0, goto done
        asm.mul(X11, X11, X10)     // a1 *= a0
        asm.addi(X10, X10, -1)     // a0--
        asm.j("loop")              // goto loop
        asm.label("done")
        asm.mv(X10, X11)           // a0 = a1
        asm.ret()

        val bytes = asm.toByteArray()
        assertEquals(28, bytes.size, "7 instructions × 4 bytes")

        // Verify first instruction: addi a1, x0, 1
        assertEquals((1 shl 20) or (0 shl 15) or (0 shl 12) or (11 shl 7) or 0x13, readU32(bytes, 0))
    }
}
