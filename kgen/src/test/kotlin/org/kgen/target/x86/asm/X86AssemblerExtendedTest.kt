package org.kgen.target.x86.asm

import org.kgen.target.x86.*

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class X86AssemblerExtendedTest {

    private val al = X86Register.AL as X86Register8
    private val cl = X86Register.CL as X86Register8
    private val dl = X86Register.DL as X86Register8
    private val bl = X86Register.BL as X86Register8
    private val r8b = X86Register.R8B as X86Register8
    private val r15b = X86Register.R15B as X86Register8

    private val ax = X86Register.AX as X86Register16
    private val cx = X86Register.CX as X86Register16

    private val eax = X86Register.EAX as X86Register32
    private val ecx = X86Register.ECX as X86Register32
    private val edx = X86Register.EDX as X86Register32
    private val ebx = X86Register.EBX as X86Register32
    private val esp = X86Register.ESP as X86Register32
    private val ebp = X86Register.EBP as X86Register32
    private val esi = X86Register.ESI as X86Register32
    private val edi = X86Register.EDI as X86Register32
    private val r8d = X86Register.R8D as X86Register32
    private val r9d = X86Register.R9D as X86Register32
    private val r10d = X86Register.R10D as X86Register32
    private val r12d = X86Register.R12D as X86Register32
    private val r15d = X86Register.R15D as X86Register32

    private val rax = X86Register.RAX as X86Register64
    private val rcx = X86Register.RCX as X86Register64
    private val rdx = X86Register.RDX as X86Register64
    private val rbx = X86Register.RBX as X86Register64
    private val rsp = X86Register.RSP as X86Register64
    private val rbp = X86Register.RBP as X86Register64
    private val rsi = X86Register.RSI as X86Register64
    private val rdi = X86Register.RDI as X86Register64
    private val r8 = X86Register.R8 as X86Register64
    private val r9 = X86Register.R9 as X86Register64
    private val r10 = X86Register.R10 as X86Register64
    private val r11 = X86Register.R11 as X86Register64
    private val r12 = X86Register.R12 as X86Register64
    private val r13 = X86Register.R13 as X86Register64
    private val r14 = X86Register.R14 as X86Register64
    private val r15 = X86Register.R15 as X86Register64

    private val xmm0 = X86Register.XMM0 as X86Xmm
    private val xmm1 = X86Register.XMM1 as X86Xmm
    private val xmm2 = X86Register.XMM2 as X86Xmm
    private val xmm3 = X86Register.XMM3 as X86Xmm
    private val xmm7 = X86Register.XMM7 as X86Xmm
    private val xmm8 = X86Register.XMM8 as X86Xmm

    private val ymm0 = X86Register.YMM0 as X86Ymm
    private val ymm1 = X86Register.YMM1 as X86Ymm
    private val ymm2 = X86Register.YMM2 as X86Ymm

    private val zmm0 = X86Register.ZMM0 as X86Zmm
    private val zmm1 = X86Register.ZMM1 as X86Zmm
    private val zmm2 = X86Register.ZMM2 as X86Zmm

    private fun asm() = X86Assembler()

    private fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "0x%02X".format(it) }

    // ---- REX prefix encoding ----

    @Test
    fun rexWBitForR64Operations() {
        // mov rax, rcx => REX.W=1 => 0x48, 0x8B, 0xC1
        val a = asm()
        a.mov(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0], "REX.W bit should be set: ${hex(bytes)}")
    }

    @Test
    fun rexRBitForExtendedRegInRegField() {
        // mov r8, rax => REX.WR (R8 in reg field, encoding >= 8)
        // REX = 0x40 | 0x08(W) | 0x04(R) = 0x4C
        val a = asm()
        a.mov(r8, rax as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x4C.toByte(), bytes[0], "REX.WR for r8 in reg field: ${hex(bytes)}")
    }

    @Test
    fun rexBBitForExtendedRegInRmField() {
        // mov rax, r8 => REX.WB (R8 in r/m field, encoding >= 8)
        // REX = 0x40 | 0x08(W) | 0x01(B) = 0x49
        val a = asm()
        a.mov(rax, r8 as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x49.toByte(), bytes[0], "REX.WB for r8 in r/m field: ${hex(bytes)}")
    }

    @Test
    fun rexRBBitsForTwoExtendedRegs() {
        // mov r8, r15 => REX.WRB
        // REX = 0x40 | 0x08(W) | 0x04(R) | 0x01(B) = 0x4D
        val a = asm()
        a.mov(r8, r15 as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x4D.toByte(), bytes[0], "REX.WRB for r8, r15: ${hex(bytes)}")
    }

    @Test
    fun rexBBitForPushExtendedReg() {
        // push r13 => REX.B + opcode
        // REX = 0x41, opcode = 0x50 + (13 & 7) = 0x55
        val a = asm()
        a.push(r13)
        val bytes = a.toByteArray()
        assertEquals(0x41.toByte(), bytes[0], "REX.B for push r13: ${hex(bytes)}")
        assertEquals(0x55.toByte(), bytes[1], "push +rd for r13: ${hex(bytes)}")
    }

    // ---- ModR/M byte encoding ----

    @Test
    fun modrmRegRegMode() {
        // add eax, ecx => 0x03 0xC1 (mod=11, reg=0, r/m=1)
        val a = asm()
        a.add(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x03.toByte(), bytes[0])
        assertEquals(0xC1.toByte(), bytes[1], "ModRM mod=11 reg=eax(0) rm=ecx(1): ${hex(bytes)}")
    }

    @Test
    fun modrmRegMemMode() {
        // mov eax, [rbx] => 0x8B 0x03 (mod=00, reg=0, r/m=3)
        val a = asm()
        val mem = X86Memory.base(rbx).build()
        a.mov(eax, mem as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x8B.toByte(), bytes[0])
        assertEquals(0x03.toByte(), bytes[1], "ModRM mod=00 reg=eax(0) rm=rbx(3): ${hex(bytes)}")
    }

    @Test
    fun modrmMemRegMode() {
        // mov [rbx], eax => 0x89 0x03 (mod=00, reg=0, r/m=3)
        val a = asm()
        val mem = X86Memory.base(rbx).build()
        a.mov(mem, eax)
        val bytes = a.toByteArray()
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x03.toByte(), bytes[1], "ModRM mod=00 reg=eax(0) rm=rbx(3): ${hex(bytes)}")
    }

    @Test
    fun modrmExtMode() {
        // inc eax => 0xFF 0xC0 (mod=11, ext=0, r/m=0)
        val a = asm()
        a.inc(eax as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xC0.toByte(), bytes[1], "ModRM mod=11 ext=0 rm=eax(0): ${hex(bytes)}")
    }

    // ---- SIB byte encoding ----

    @Test
    fun sibBaseIndexScale1() {
        // mov rax, [rbx + rcx*1] => REX.W 0x8B ModRM SIB
        // ModRM: mod=00, reg=0, rm=100(SIB)
        // SIB: scale=0, index=rcx(1), base=rbx(3) => 0x0B
        val a = asm()
        val mem = X86Memory.base(rbx).index(rcx, 1).build()
        a.mov(rax, mem as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0], "REX.W: ${hex(bytes)}")
        assertEquals(0x8B.toByte(), bytes[1], "MOV opcode: ${hex(bytes)}")
        assertEquals(0x04.toByte(), bytes[2], "ModRM mod=00 reg=0 rm=100(SIB): ${hex(bytes)}")
        assertEquals(0x0B.toByte(), bytes[3], "SIB scale=0 index=1(rcx) base=3(rbx): ${hex(bytes)}")
    }

    @Test
    fun sibBaseIndexScale4() {
        // mov rax, [rbx + rcx*4] => SIB scale=2
        val a = asm()
        val mem = X86Memory.base(rbx).index(rcx, 4).build()
        a.mov(rax, mem as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x8B.toByte(), bytes[1])
        assertEquals(0x04.toByte(), bytes[2], "ModRM with SIB: ${hex(bytes)}")
        // SIB: scale=2(4), index=1(rcx), base=3(rbx) => (2<<6)|(1<<3)|3 = 0x8B
        assertEquals(0x8B.toByte(), bytes[3], "SIB scale=2 index=rcx base=rbx: ${hex(bytes)}")
    }

    @Test
    fun sibBaseIndexScale8() {
        // mov rax, [rdx + rcx*8] => SIB scale=3
        val a = asm()
        val mem = X86Memory.base(rdx).index(rcx, 8).build()
        a.mov(rax, mem as X86Operand64)
        val bytes = a.toByteArray()
        // SIB: scale=3(8), index=1(rcx), base=2(rdx) => (3<<6)|(1<<3)|2 = 0xCA
        assertEquals(0xCA.toByte(), bytes[3], "SIB scale=3 index=rcx base=rdx: ${hex(bytes)}")
    }

    @Test
    fun sibRspBaseAlwaysSib() {
        // mov eax, [rsp] => needs SIB even without index
        // ModRM: mod=00, reg=0, rm=100(SIB) => 0x04
        // SIB: scale=0, index=100(none), base=100(rsp) => 0x24
        val a = asm()
        val mem = X86Memory.base(rsp).build()
        a.mov(eax, mem as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x8B.toByte(), bytes[0])
        assertEquals(0x04.toByte(), bytes[1], "ModRM rm=100 for RSP: ${hex(bytes)}")
        assertEquals(0x24.toByte(), bytes[2], "SIB no-index base=rsp: ${hex(bytes)}")
    }

    @Test
    fun sibWithExtendedBaseReg() {
        // mov rax, [r12] => REX.WB, needs SIB since r12 encoding & 7 == 4
        val a = asm()
        val mem = X86Memory.base(r12).build()
        a.mov(rax, mem as X86Operand64)
        val bytes = a.toByteArray()
        // REX = 0x49 (W=1, B=1 for r12)
        assertEquals(0x49.toByte(), bytes[0], "REX.WB for r12 base: ${hex(bytes)}")
        assertEquals(0x8B.toByte(), bytes[1])
        assertEquals(0x04.toByte(), bytes[2], "ModRM rm=100(SIB): ${hex(bytes)}")
        assertEquals(0x24.toByte(), bytes[3], "SIB no-index base=r12: ${hex(bytes)}")
    }

    @Test
    fun sibWithExtendedIndexReg() {
        // mov rax, [rbx + r9*2] => REX.WX (X for extended index)
        val a = asm()
        val mem = X86Memory.base(rbx).index(r9, 2).build()
        a.mov(rax, mem as X86Operand64)
        val bytes = a.toByteArray()
        // REX = 0x40 | 0x08(W) | 0x02(X) = 0x4A
        assertEquals(0x4A.toByte(), bytes[0], "REX.WX for r9 index: ${hex(bytes)}")
    }

    // ---- Displacement encoding ----

    @Test
    fun displacement8BitPositive() {
        // mov eax, [rbp+16] => mod=01 (disp8)
        val a = asm()
        val mem = X86Memory.base(rbp).offset(16)
        a.mov(eax, mem as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x8B.toByte(), bytes[0])
        // ModRM: mod=01, reg=0, rm=5(rbp) => 0x45
        assertEquals(0x45.toByte(), bytes[1], "ModRM mod=01 rm=rbp: ${hex(bytes)}")
        assertEquals(16.toByte(), bytes[2], "disp8=16: ${hex(bytes)}")
    }

    @Test
    fun displacement8BitNegative() {
        // mov eax, [rbp-128] => mod=01 (disp8), disp=-128 fits in signed byte
        val a = asm()
        val mem = X86Memory.base(rbp).offset(-128)
        a.mov(eax, mem as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x45.toByte(), bytes[1], "ModRM mod=01: ${hex(bytes)}")
        assertEquals(0x80.toByte(), bytes[2], "disp8=-128: ${hex(bytes)}")
    }

    @Test
    fun displacement32Bit() {
        // mov eax, [rbp+256] => mod=10 (disp32)
        val a = asm()
        val mem = X86Memory.base(rbp).offset(256)
        a.mov(eax, mem as X86Operand32)
        val bytes = a.toByteArray()
        // ModRM: mod=10, reg=0, rm=5(rbp) => 0x85
        assertEquals(0x85.toByte(), bytes[1], "ModRM mod=10 rm=rbp: ${hex(bytes)}")
        assertEquals(0x00.toByte(), bytes[2])
        assertEquals(0x01.toByte(), bytes[3])
        assertEquals(0x00.toByte(), bytes[4])
        assertEquals(0x00.toByte(), bytes[5])
    }

    @Test
    fun displacement32BitNegative() {
        // mov rax, [rbp-200] => needs disp32 since -200 < -128
        val a = asm()
        val mem = X86Memory.base(rbp).offset(-200)
        a.mov(rax, mem as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0], "REX.W: ${hex(bytes)}")
        // ModRM: mod=10, reg=0, rm=5(rbp) => 0x85
        assertEquals(0x85.toByte(), bytes[2], "ModRM mod=10 disp32: ${hex(bytes)}")
        // -200 in little-endian: 0x38 0xFF 0xFF 0xFF
        assertEquals(0x38.toByte(), bytes[3])
        assertEquals(0xFF.toByte(), bytes[4])
    }

    // ---- Immediate encoding ----

    @Test
    fun immediate8Bit() {
        // push 0x42 => 0x6A 0x42
        val a = asm()
        a.push(0x42.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x6A.toByte(), bytes[0])
        assertEquals(0x42.toByte(), bytes[1])
    }

    @Test
    fun immediate16Bit() {
        // ret 0x1234 => 0xC2 0x34 0x12
        val a = asm()
        a.ret(0x1234.toShort())
        val bytes = a.toByteArray()
        assertEquals(0xC2.toByte(), bytes[0])
        assertEquals(0x34.toByte(), bytes[1])
        assertEquals(0x12.toByte(), bytes[2])
    }

    @Test
    fun immediate32Bit() {
        // mov eax, 0xDEADBEEF => 0xB8 EF BE AD DE
        val a = asm()
        a.mov(eax, 0xDEADBEEF.toInt())
        val bytes = a.toByteArray()
        assertEquals(0xB8.toByte(), bytes[0])
        assertEquals(0xEF.toByte(), bytes[1])
        assertEquals(0xBE.toByte(), bytes[2])
        assertEquals(0xAD.toByte(), bytes[3])
        assertEquals(0xDE.toByte(), bytes[4])
    }

    @Test
    fun immediate64Bit() {
        // mov rax, 0x0123456789ABCDEF => REX.W 0xB8 + imm64
        val a = asm()
        a.mov(rax, 0x0123456789ABCDEFL)
        val bytes = a.toByteArray()
        assertEquals(10, bytes.size, "movabs rax, imm64 should be 10 bytes: ${hex(bytes)}")
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0xB8.toByte(), bytes[1])
        assertEquals(0xEF.toByte(), bytes[2])
        assertEquals(0xCD.toByte(), bytes[3])
        assertEquals(0xAB.toByte(), bytes[4])
        assertEquals(0x89.toByte(), bytes[5])
    }

    // ---- Arithmetic reg-reg, reg-imm, reg-mem, mem-reg, mem-imm ----

    @Test
    fun addRegMem() {
        // add eax, [rbx] => 0x03 0x03
        val a = asm()
        a.add(eax, X86Memory.base(rbx).build() as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x03.toByte(), bytes[0], "ADD r32, r/m32: ${hex(bytes)}")
        assertEquals(0x03.toByte(), bytes[1])
    }

    @Test
    fun addMemReg() {
        // add [rbx], eax => 0x01 0x03
        val a = asm()
        a.add(X86Memory.base(rbx).build(), eax)
        val bytes = a.toByteArray()
        assertEquals(0x01.toByte(), bytes[0], "ADD r/m32, r32: ${hex(bytes)}")
    }

    @Test
    fun addMemImm32() {
        // add dword [rbx], 100 => 0x81 ModRM(ext=0, rm=3) + imm32
        val a = asm()
        a.add(X86Memory.base(rbx).build() as X86Operand32, 100)
        val bytes = a.toByteArray()
        assertEquals(0x81.toByte(), bytes[0], "ADD r/m32, imm32: ${hex(bytes)}")
    }

    @Test
    fun subR64Imm32() {
        // sub rsp, 0x100 uses rm form: REX.W 0x81 ModRM(ext=5) SIB + imm32
        // But sub rax, imm uses short form: REX.W 0x2D + imm32
        val a = asm()
        a.sub(rax, 0x100)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0], "REX.W: ${hex(bytes)}")
        assertEquals(0x2D.toByte(), bytes[1], "SUB rax, imm32 short form: ${hex(bytes)}")
    }

    @Test
    fun subMemR64() {
        // sub [rdi], rax => REX.W 0x29 ModRM
        val a = asm()
        a.sub(X86Memory.base(rdi).build(), rax)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x29.toByte(), bytes[1], "SUB mem, r64: ${hex(bytes)}")
    }

    @Test
    fun andR64Imm() {
        // and rax, 0xFF => short accumulator form: REX.W 0x25 + imm32
        val a = asm()
        a.and_(rax, 0xFF)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x25.toByte(), bytes[1], "AND rax, imm32 short form: ${hex(bytes)}")
    }

    @Test
    fun orMemR32() {
        // or [rsi], ecx => 0x09 ModRM
        val a = asm()
        a.or_(X86Memory.base(rsi).build(), ecx)
        val bytes = a.toByteArray()
        assertEquals(0x09.toByte(), bytes[0], "OR mem, r32: ${hex(bytes)}")
    }

    @Test
    fun xorR64Imm() {
        // xor rax, 0x80 => short accumulator form: REX.W 0x35 + imm32
        val a = asm()
        a.xor_(rax, 0x80)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x35.toByte(), bytes[1], "XOR rax, imm32 short form: ${hex(bytes)}")
    }

    // ---- String instructions with rep prefixes ----

    @Test
    fun repMovsb() {
        // rep movsb => 0xF3 0xA4
        val a = asm()
        a.rep()
        a.movsb()
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0], "REP prefix: ${hex(bytes)}")
        assertEquals(0xA4.toByte(), bytes[1], "MOVSB: ${hex(bytes)}")
    }

    @Test
    fun repMovsq() {
        // rep movsq => 0xF3 REX.W 0xA5
        val a = asm()
        a.rep()
        a.movsq()
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x48.toByte(), bytes[1], "REX.W: ${hex(bytes)}")
        assertEquals(0xA5.toByte(), bytes[2])
    }

    @Test
    fun repeStosd() {
        // repe stosd => 0xF3 0xAB
        val a = asm()
        a.repe()
        a.stosd()
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0xAB.toByte(), bytes[1])
    }

    @Test
    fun repneScasb() {
        // repne scasb => 0xF2 0xAE
        val a = asm()
        a.repne()
        a.scasb()
        val bytes = a.toByteArray()
        assertEquals(0xF2.toByte(), bytes[0])
        assertEquals(0xAE.toByte(), bytes[1])
    }

    // ---- Jump/call with labels ----

    @Test
    fun forwardJumpLabelResolution() {
        val a = asm()
        a.jmpLabel("end")
        a.nop()
        a.nop()
        a.nop()
        a.label("end")
        a.ret()
        val bytes = a.toByteArray()
        assertEquals(0xE9.toByte(), bytes[0], "JMP near: ${hex(bytes)}")
        val rel = readInt32(bytes, 1)
        assertEquals(3, rel, "Relative offset should skip 3 nops")
    }

    @Test
    fun backwardJumpLabelResolution() {
        val a = asm()
        a.label("loop")
        a.inc(eax as X86Operand32) // 2 bytes
        a.cmp(eax, 10) // 5 bytes
        a.jmpLabel("loop")
        val bytes = a.toByteArray()
        val jmpPos = 7 // 2+5 = offset of jmp
        assertEquals(0xE9.toByte(), bytes[jmpPos])
        val rel = readInt32(bytes, jmpPos + 1)
        // target=0, instrEnd=jmpPos+5=12, rel=0-12=-12
        assertEquals(-12, rel, "Backward jump relative offset")
    }

    @Test
    fun jccForwardLabelResolution() {
        val a = asm()
        a.jccLabel(0x05, "skip") // jne
        a.nop()
        a.label("skip")
        a.ret()
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x85.toByte(), bytes[1], "JNE near: ${hex(bytes)}")
        val rel = readInt32(bytes, 2)
        assertEquals(1, rel, "Forward jne skips 1 nop")
    }

    @Test
    fun callForwardAndBackwardLabels() {
        val a = asm()
        a.callLabel("func")
        a.jmpLabel("end")
        a.label("func")
        a.nop()
        a.ret()
        a.label("end")
        val bytes = a.toByteArray()
        assertEquals(0xE8.toByte(), bytes[0], "CALL: ${hex(bytes)}")
        val callRel = readInt32(bytes, 1)
        // callLabel at 0, fixup at 1, instrEnd=5, target=10 => rel=10-5=5
        assertEquals(5, callRel, "CALL forward resolution")
    }

    @Test
    fun multipleLabelsWithBranching() {
        val a = asm()
        a.jccLabel(0x04, "true_branch") // je
        a.nop()
        a.jmpLabel("done")
        a.label("true_branch")
        a.nop()
        a.nop()
        a.label("done")
        a.ret()
        val bytes = a.toByteArray()
        // je target after the nop+jmp = 6+1+5 = at offset 12
        // jmp target = at offset 14 (end)
        val jeRel = readInt32(bytes, 2)
        assertEquals(6, jeRel, "je skips nop(1) + jmp(5)")
    }

    // ---- Conditional moves ----

    @Test
    fun cmovbR64R64() {
        // cmovb rax, rcx => 0x48 0x0F 0x42 ModRM
        val a = asm()
        a.cmovb(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x42.toByte(), bytes[2], "CMOVB opcode: ${hex(bytes)}")
    }

    @Test
    fun cmovaR32R32() {
        // cmova eax, ecx => 0x0F 0x47 ModRM
        val a = asm()
        a.cmova(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x47.toByte(), bytes[1], "CMOVA opcode: ${hex(bytes)}")
    }

    @Test
    fun cmovleR64R64() {
        // cmovle rax, rcx => 0x48 0x0F 0x4E ModRM
        val a = asm()
        a.cmovle(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x4E.toByte(), bytes[2], "CMOVLE opcode: ${hex(bytes)}")
    }

    @Test
    fun cmovsR32R32() {
        // cmovs eax, ecx => 0x0F 0x48 ModRM
        val a = asm()
        a.cmovs(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x48.toByte(), bytes[1], "CMOVS opcode: ${hex(bytes)}")
    }

    // ---- Bit manipulation ----

    @Test
    fun btR64Imm() {
        // bt rax, 7 => REX.W 0F BA ModRM(ext=4) 07
        val a = asm()
        a.bt(rax as X86Operand64, 7.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xBA.toByte(), bytes[2])
        assertEquals(7.toByte(), bytes[4], "BT imm8=7: ${hex(bytes)}")
    }

    @Test
    fun btrR32R32() {
        // btr eax, ecx => 0F B3 ModRM
        val a = asm()
        a.btr(eax as X86Operand32, ecx)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xB3.toByte(), bytes[1], "BTR r/m32, r32: ${hex(bytes)}")
    }

    @Test
    fun btcR64Imm() {
        // btc rax, 15 => REX.W 0F BA ModRM(ext=7) 0F
        val a = asm()
        a.btc(rax as X86Operand64, 15.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xBA.toByte(), bytes[2])
    }

    @Test
    fun bsfR64R64() {
        // bsf rax, rcx => REX.W 0F BC ModRM
        val a = asm()
        a.bsf(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xBC.toByte(), bytes[2], "BSF r64: ${hex(bytes)}")
    }

    @Test
    fun bsrR64R64() {
        // bsr rax, rcx => REX.W 0F BD ModRM
        val a = asm()
        a.bsr(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xBD.toByte(), bytes[2], "BSR r64: ${hex(bytes)}")
    }

    @Test
    fun popcntR64R64() {
        // popcnt rax, rcx => F3 REX.W 0F B8 ModRM
        val a = asm()
        a.popcnt(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x48.toByte(), bytes[1])
        assertEquals(0x0F.toByte(), bytes[2])
        assertEquals(0xB8.toByte(), bytes[3], "POPCNT r64: ${hex(bytes)}")
    }

    @Test
    fun lzcntR64R64() {
        // lzcnt rax, rcx => F3 REX.W 0F BD ModRM
        val a = asm()
        a.lzcnt(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x48.toByte(), bytes[1])
        assertEquals(0x0F.toByte(), bytes[2])
        assertEquals(0xBD.toByte(), bytes[3], "LZCNT r64: ${hex(bytes)}")
    }

    @Test
    fun tzcntR64R64() {
        // tzcnt rax, rcx => F3 REX.W 0F BC ModRM
        val a = asm()
        a.tzcnt(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x48.toByte(), bytes[1])
        assertEquals(0x0F.toByte(), bytes[2])
        assertEquals(0xBC.toByte(), bytes[3], "TZCNT r64: ${hex(bytes)}")
    }

    // ---- Shift/rotate instructions ----

    @Test
    fun shlR64ByCl() {
        // shl rax, cl => REX.W 0xD3 ModRM(ext=4, rm=0)
        val a = asm()
        a.shl(rax as X86Operand64, cl)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0xD3.toByte(), bytes[1], "SHL r/m64, CL: ${hex(bytes)}")
        // ModRM: mod=11, ext=4, rm=0 => 0xE0
        assertEquals(0xE0.toByte(), bytes[2], "ModRM ext=4: ${hex(bytes)}")
    }

    @Test
    fun shrR32ByImm() {
        // shr eax, 8 => 0xC1 ModRM(ext=5, rm=0) 0x08
        val a = asm()
        a.shr(eax as X86Operand32, 8.toByte())
        val bytes = a.toByteArray()
        assertEquals(0xC1.toByte(), bytes[0])
        // ModRM: mod=11, ext=5, rm=0 => 0xE8
        assertEquals(0xE8.toByte(), bytes[1], "SHR ModRM ext=5: ${hex(bytes)}")
        assertEquals(8.toByte(), bytes[2])
    }

    @Test
    fun rolR32ByImm() {
        // rol eax, 3 => 0xC1 ModRM(ext=0, rm=0) 0x03
        val a = asm()
        a.rol(eax as X86Operand32, 3.toByte())
        val bytes = a.toByteArray()
        assertEquals(0xC1.toByte(), bytes[0])
        // ModRM: mod=11, ext=0, rm=0 => 0xC0
        assertEquals(0xC0.toByte(), bytes[1], "ROL ModRM ext=0: ${hex(bytes)}")
        assertEquals(3.toByte(), bytes[2])
    }

    @Test
    fun rorR64ByCl() {
        // ror rax, cl => REX.W 0xD3 ModRM(ext=1, rm=0)
        val a = asm()
        a.ror(rax as X86Operand64, cl)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0xD3.toByte(), bytes[1])
        // ModRM: mod=11, ext=1, rm=0 => 0xC8
        assertEquals(0xC8.toByte(), bytes[2], "ROR ModRM ext=1: ${hex(bytes)}")
    }

    @Test
    fun sarR64ByImm() {
        // sar rax, 16 => REX.W 0xC1 ModRM(ext=7, rm=0) 0x10
        val a = asm()
        a.sar(rax as X86Operand64, 16.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0xC1.toByte(), bytes[1])
        // ModRM: mod=11, ext=7, rm=0 => 0xF8
        assertEquals(0xF8.toByte(), bytes[2], "SAR ModRM ext=7: ${hex(bytes)}")
        assertEquals(16.toByte(), bytes[3])
    }

    // ---- Stack operations ----

    @Test
    fun pushPopRegPairPreservesEncoding() {
        val a = asm()
        a.push(rax)
        a.push(rbx)
        a.push(rbp)
        a.pop(rbp)
        a.pop(rbx)
        a.pop(rax)
        val bytes = a.toByteArray()
        assertEquals(6, bytes.size, "6 single-byte push/pop: ${hex(bytes)}")
        assertEquals(0x50.toByte(), bytes[0]) // push rax
        assertEquals(0x53.toByte(), bytes[1]) // push rbx
        assertEquals(0x55.toByte(), bytes[2]) // push rbp
        assertEquals(0x5D.toByte(), bytes[3]) // pop rbp
        assertEquals(0x5B.toByte(), bytes[4]) // pop rbx
        assertEquals(0x58.toByte(), bytes[5]) // pop rax
    }

    @Test
    fun pushImm16() {
        // push 0x1234 (as Short) => 0x66 0x68 0x34 0x12
        val a = asm()
        a.push(0x1234.toShort())
        val bytes = a.toByteArray()
        assertEquals(0x66.toByte(), bytes[0], "Operand size prefix: ${hex(bytes)}")
        assertEquals(0x68.toByte(), bytes[1])
        assertEquals(0x34.toByte(), bytes[2])
        assertEquals(0x12.toByte(), bytes[3])
    }

    @Test
    fun pushImm32Large() {
        // push 0x7FFFFFFF => 0x68 + 4 bytes
        val a = asm()
        a.push(0x7FFFFFFF)
        val bytes = a.toByteArray()
        assertEquals(5, bytes.size)
        assertEquals(0x68.toByte(), bytes[0])
        assertEquals(0xFF.toByte(), bytes[1])
        assertEquals(0xFF.toByte(), bytes[2])
        assertEquals(0xFF.toByte(), bytes[3])
        assertEquals(0x7F.toByte(), bytes[4])
    }

    // ---- SSE instructions ----

    @Test
    fun movsdXmmXmm() {
        // movsd xmm0, xmm1 => F2 0F 10 C1
        val a = asm()
        a.movsd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF2.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x10.toByte(), bytes[2], "MOVSD xmm,xmm: ${hex(bytes)}")
    }

    @Test
    fun movssXmmXmm() {
        // movss xmm0, xmm1 => F3 0F 10 C1
        val a = asm()
        a.movss(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x10.toByte(), bytes[2], "MOVSS xmm,xmm: ${hex(bytes)}")
    }

    @Test
    fun sqrtsdXmmXmm() {
        // sqrtsd xmm0, xmm1 => F2 0F 51 C1
        val a = asm()
        a.sqrtsd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF2.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x51.toByte(), bytes[2], "SQRTSD: ${hex(bytes)}")
    }

    @Test
    fun cvtsi2sdFromR64() {
        // cvtsi2sd xmm0, rax => F2 REX.W 0F 2A C0
        val a = asm()
        a.cvtsi2sd(xmm0, rax as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0xF2.toByte(), bytes[0])
        assertEquals(0x48.toByte(), bytes[1], "REX.W: ${hex(bytes)}")
        assertEquals(0x0F.toByte(), bytes[2])
        assertEquals(0x2A.toByte(), bytes[3], "CVTSI2SD: ${hex(bytes)}")
    }

    @Test
    fun cvttsd2siToR32() {
        // cvttsd2si eax, xmm0 => F2 0F 2C C0
        val a = asm()
        a.cvttsd2si(eax, xmm0)
        val bytes = a.toByteArray()
        assertEquals(0xF2.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x2C.toByte(), bytes[2], "CVTTSD2SI: ${hex(bytes)}")
    }

    // ---- VEX instructions ----

    @Test
    fun vaddpdXmmThreeOperand() {
        // vaddpd xmm0, xmm1, xmm2 => VEX.NDS.128.66.0F.W0 58 /r
        // 2-byte VEX: C5 [R=1,vvvv=~1=0xE,L=0,pp=01] 58 ModRM
        // byte2: (1<<7)|(0xE<<3)|(0<<2)|1 = 0x80|0x70|0|1 = 0xF1
        val a = asm()
        a.vaddpd(xmm0, xmm1, xmm2)
        val bytes = a.toByteArray()
        assertEquals(0xC5.toByte(), bytes[0], "2-byte VEX: ${hex(bytes)}")
        assertEquals(0xF1.toByte(), bytes[1], "VEX byte2: ${hex(bytes)}")
        assertEquals(0x58.toByte(), bytes[2], "ADDPD opcode: ${hex(bytes)}")
    }

    @Test
    fun vmulpsYmmThreeOperand() {
        // vmulps ymm0, ymm1, ymm2 => VEX.NDS.256.0F.W0 59 /r
        // 2-byte VEX: C5 [R=1,vvvv=~1=0xE,L=1,pp=0]
        // byte2: 0x80|0x70|0x04|0 = 0xF4
        val a = asm()
        a.vmulps(ymm0, ymm1, ymm2)
        val bytes = a.toByteArray()
        assertEquals(0xC5.toByte(), bytes[0], "2-byte VEX: ${hex(bytes)}")
        assertEquals(0xF4.toByte(), bytes[1], "VEX L=1 for ymm: ${hex(bytes)}")
        assertEquals(0x59.toByte(), bytes[2], "MULPS opcode: ${hex(bytes)}")
    }

    @Test
    fun vxorpsXmmSelfClear() {
        // vxorps xmm0, xmm0, xmm0 => VEX.NDS.128.0F.W0 57 /r
        val a = asm()
        a.vxorps(xmm0, xmm0, xmm0)
        val bytes = a.toByteArray()
        assertEquals(0xC5.toByte(), bytes[0])
        assertEquals(0x57.toByte(), bytes[2], "XORPS opcode: ${hex(bytes)}")
        assertEquals(0xC0.toByte(), bytes[3], "ModRM xmm0,xmm0: ${hex(bytes)}")
    }

    // ---- EVEX instructions ----

    @Test
    fun evexVsubpsZmm() {
        // vsubps zmm0, zmm1, zmm2 => EVEX.NDS.512.0F.W0 5C /r
        val a = asm()
        a.vsubps_z(zmm0, zmm1, zmm2)
        val bytes = a.toByteArray()
        assertEquals(6, bytes.size, "EVEX instruction 6 bytes: ${hex(bytes)}")
        assertEquals(0x62.toByte(), bytes[0], "EVEX prefix: ${hex(bytes)}")
        assertEquals(0x5C.toByte(), bytes[4], "SUBPS opcode: ${hex(bytes)}")
    }

    @Test
    fun evexVmulpdZmm() {
        // vmulpd zmm0, zmm1, zmm2 => EVEX.NDS.512.66.0F.W1 59 /r
        val a = asm()
        a.vmulpd_z(zmm0, zmm1, zmm2)
        val bytes = a.toByteArray()
        assertEquals(0x62.toByte(), bytes[0])
        assertEquals(0x59.toByte(), bytes[4], "MULPD opcode: ${hex(bytes)}")
    }

    // ---- RIP-relative addressing ----

    @Test
    fun leaRipRelativeWithLabel() {
        // lea rdi, [rip+label] => REX.W 0x8D ModRM(mod=00, reg=7, rm=5) + disp32
        val a = asm()
        a.label("data")
        a.lea(rdi, X86Memory.ripRelative("data"))
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0], "REX.W: ${hex(bytes)}")
        assertEquals(0x8D.toByte(), bytes[1], "LEA opcode: ${hex(bytes)}")
        // ModRM: mod=00, reg=7(rdi), rm=5(RIP) => (7<<3)|5 = 0x3D
        assertEquals(0x3D.toByte(), bytes[2], "ModRM RIP-relative: ${hex(bytes)}")
    }

    @Test
    fun movFromRipRelative() {
        // mov rax, [rip+data] => REX.W 0x8B ModRM(mod=00, reg=0, rm=5)
        val a = asm()
        a.label("data")
        a.mov(rax, X86Memory.ripRelative("data") as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x8B.toByte(), bytes[1])
        // ModRM: mod=00, reg=0(rax), rm=5(RIP) => 0x05
        assertEquals(0x05.toByte(), bytes[2], "ModRM RIP-relative: ${hex(bytes)}")
    }

    // ---- Multi-instruction sequences ----

    @Test
    fun sysVCallingConventionProlog() {
        // Standard x86-64 SysV function prologue
        val a = asm()
        a.push(rbp)
        a.mov(rbp, rsp as X86Operand64)
        a.push(rbx)
        a.push(r12)
        a.push(r13)
        a.sub(rsp, 0x18)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 12, "Prologue should be at least 12 bytes: ${hex(bytes)}")
        assertEquals(0x55.toByte(), bytes[0], "push rbp")
    }

    @Test
    fun loopWithConditionalBranch() {
        // xor ecx, ecx
        // .loop:
        // add eax, ecx
        // inc ecx
        // cmp ecx, 10
        // jne .loop
        val a = asm()
        a.xor_(ecx, ecx as X86Operand32)
        a.label("loop")
        a.add(eax, ecx as X86Operand32)
        a.inc(ecx as X86Operand32)
        a.cmp(ecx, 10)
        a.jccLabel(0x05, "loop") // jne
        val bytes = a.toByteArray()
        assertTrue(bytes.size > 10, "Loop body should be substantial: ${hex(bytes)}")
        // Verify the jne points backward
        val jnePos = bytes.size - 6
        assertEquals(0x0F.toByte(), bytes[jnePos])
        assertEquals(0x85.toByte(), bytes[jnePos + 1])
        val rel = readInt32(bytes, jnePos + 2)
        assertTrue(rel < 0, "Backward jump should have negative displacement: $rel")
    }

    @Test
    fun fibonacciSequence() {
        // Computes fib(n) in eax
        // mov eax, 0    ; a = 0
        // mov ecx, 1    ; b = 1
        // mov edx, edi  ; n
        // .loop:
        // cmp edx, 0
        // je .done
        // mov ebx, eax
        // add eax, ecx
        // mov ecx, ebx
        // dec edx
        // jmp .loop
        // .done:
        // ret
        val a = asm()
        a.mov(eax, 0)
        a.mov(ecx, 1)
        a.mov(edx, edi as X86Operand32)
        a.label("loop")
        a.cmp(edx, 0)
        a.jccLabel(0x04, "done") // je
        a.mov(ebx, eax as X86Operand32)
        a.add(eax, ecx as X86Operand32)
        a.mov(ecx, ebx as X86Operand32)
        a.dec(edx as X86Operand32)
        a.jmpLabel("loop")
        a.label("done")
        a.ret()
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
        assertEquals(0xC3.toByte(), bytes.last(), "Function ends with ret")
    }

    @Test
    fun memcpySequence() {
        // push rdi, push rsi, push rcx
        // rep movsb
        // pop rcx, pop rsi, pop rdi, ret
        val a = asm()
        a.push(rdi)
        a.push(rsi)
        a.push(rcx)
        a.rep()
        a.movsb()
        a.pop(rcx)
        a.pop(rsi)
        a.pop(rdi)
        a.ret()
        val bytes = a.toByteArray()
        assertEquals(0x57.toByte(), bytes[0], "push rdi")
        assertEquals(0x56.toByte(), bytes[1], "push rsi")
        assertEquals(0x51.toByte(), bytes[2], "push rcx")
        assertEquals(0xF3.toByte(), bytes[3], "rep prefix")
        assertEquals(0xA4.toByte(), bytes[4], "movsb")
        assertEquals(0xC3.toByte(), bytes.last(), "ret")
    }

    // ---- Edge cases ----

    @Test
    fun rbpBaseNeedsDisplacement() {
        // [rbp] without displacement encodes as [rbp+0] (mod=01, disp8=0)
        // because mod=00, rm=5 is RIP-relative
        val a = asm()
        val mem = X86Memory.base(rbp).build()
        a.mov(eax, mem as X86Operand32)
        val bytes = a.toByteArray()
        // ModRM: mod=01, reg=0, rm=5 => 0x45 with disp8=0
        // Or mod=00, rm=5 + disp32=0 depending on impl
        assertTrue(bytes.size >= 3, "RBP base should have displacement: ${hex(bytes)}")
    }

    @Test
    fun r13BaseNeedsDisplacement() {
        // [r13] has same issue as [rbp] since encoding & 7 == 5
        val a = asm()
        val mem = X86Memory.base(r13).build()
        a.mov(rax, mem as X86Operand64)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 4, "R13 base should have extra bytes: ${hex(bytes)}")
    }

    @Test
    fun sibWithDisplacementAndExtendedRegs() {
        // mov rax, [r12 + r9*8 + 0x40]
        val a = asm()
        val mem = X86Memory.base(r12).index(r9, 8).offset(0x40)
        a.mov(rax, mem as X86Operand64)
        val bytes = a.toByteArray()
        // REX: W=1, X=1(r9), B=1(r12) => 0x4B
        assertEquals(0x4B.toByte(), bytes[0], "REX.WXB: ${hex(bytes)}")
        assertTrue(bytes.size >= 5, "SIB+disp8+REX: ${hex(bytes)}")
    }

    @Test
    fun absoluteAddressing() {
        // mov eax, [0x12345678] => uses SIB byte 0x25
        val a = asm()
        val mem = X86Memory.absolute(0x12345678L)
        a.mov(eax, mem as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x8B.toByte(), bytes[0])
        // ModRM: mod=00, reg=0, rm=100(SIB) => 0x04
        assertEquals(0x04.toByte(), bytes[1], "ModRM with SIB: ${hex(bytes)}")
        // SIB: 0x25 (no index, no base, disp32)
        assertEquals(0x25.toByte(), bytes[2], "SIB for absolute: ${hex(bytes)}")
    }

    @Test
    fun resetAndReuse() {
        val a = asm()
        a.nop()
        a.nop()
        a.nop()
        assertEquals(3, a.position())
        a.reset()
        assertEquals(0, a.position())
        a.mov(eax, 42)
        a.ret()
        val bytes = a.toByteArray()
        assertEquals(0xB8.toByte(), bytes[0], "After reset, new instruction: ${hex(bytes)}")
        assertEquals(0xC3.toByte(), bytes.last())
    }

    // ---- BMI instructions ----

    @Test
    fun blsmskR32() {
        // blsmsk ecx, edx => VEX.NDS.LZ.0F38.W0 F3 /2
        val a = asm()
        a.blsmsk(ecx, edx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0xC4.toByte(), bytes[0], "3-byte VEX: ${hex(bytes)}")
    }

    @Test
    fun blsrR64() {
        // blsr rax, rcx => VEX.NDS.LZ.0F38.W1 F3 /1
        val a = asm()
        a.blsr(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0xC4.toByte(), bytes[0], "3-byte VEX: ${hex(bytes)}")
    }

    @Test
    fun bzhiR32() {
        // bzhi eax, ecx, edx => VEX.NDS.LZ.0F38.W0 F5 /r
        val a = asm()
        a.bzhi(eax, ecx as X86Operand32, edx)
        val bytes = a.toByteArray()
        assertEquals(0xC4.toByte(), bytes[0], "3-byte VEX: ${hex(bytes)}")
    }

    // ---- SHLD/SHRD ----

    @Test
    fun shldR32R32Imm() {
        // shld eax, ecx, 4 => 0F A4 ModRM 04
        val a = asm()
        a.shld(eax as X86Operand32, ecx, 4.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xA4.toByte(), bytes[1], "SHLD opcode: ${hex(bytes)}")
    }

    @Test
    fun shrdR64R64Cl() {
        // shrd rax, rcx, cl => REX.W 0F AD ModRM
        val a = asm()
        a.shrd(rax as X86Operand64, rcx, cl)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xAD.toByte(), bytes[2], "SHRD by CL: ${hex(bytes)}")
    }

    // ---- SSE packed integer ----

    @Test
    fun paddqXmm() {
        // paddq xmm0, xmm1 => 66 0F D4 C1
        val a = asm()
        a.paddq(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0x66.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xD4.toByte(), bytes[2], "PADDQ opcode: ${hex(bytes)}")
    }

    @Test
    fun pxorXmmSelfClear() {
        // pxor xmm0, xmm0 => 66 0F EF C0
        val a = asm()
        a.pxor(xmm0, xmm0)
        val bytes = a.toByteArray()
        assertEquals(0x66.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xEF.toByte(), bytes[2], "PXOR opcode: ${hex(bytes)}")
        assertEquals(0xC0.toByte(), bytes[3], "ModRM xmm0,xmm0: ${hex(bytes)}")
    }

    @Test
    fun pcmpeqdXmm() {
        // pcmpeqd xmm0, xmm1 => 66 0F 76 C1
        val a = asm()
        a.pcmpeqd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0x66.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x76.toByte(), bytes[2], "PCMPEQD opcode: ${hex(bytes)}")
    }

    // ---- Misc single-byte instructions ----

    @Test
    fun leaveEncoding() {
        val a = asm()
        a.leave()
        assertArrayEquals(byteArrayOf(0xC9.toByte()), a.toByteArray())
    }

    @Test
    fun int3Encoding() {
        val a = asm()
        a.int3()
        assertArrayEquals(byteArrayOf(0xCC.toByte()), a.toByteArray())
    }

    @Test
    fun ud2Encoding() {
        val a = asm()
        a.ud2()
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x0B.toByte(), bytes[1])
    }

    @Test
    fun hltEncoding() {
        val a = asm()
        a.hlt()
        assertArrayEquals(byteArrayOf(0xF4.toByte()), a.toByteArray())
    }

    @Test
    fun stdEncoding() {
        val a = asm()
        a.std()
        assertArrayEquals(byteArrayOf(0xFD.toByte()), a.toByteArray())
    }

    private fun readInt32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
