package org.kgen.backend.x86.asm

import org.kgen.backend.x86.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class X86AssemblerInstructionsExtendedTest {

    private val al = X86Register.AL as X86Register8
    private val cl = X86Register.CL as X86Register8
    private val dl = X86Register.DL as X86Register8
    private val bl = X86Register.BL as X86Register8

    private val ax = X86Register.AX as X86Register16
    private val cx = X86Register.CX as X86Register16
    private val dx = X86Register.DX as X86Register16

    private val eax = X86Register.EAX as X86Register32
    private val ecx = X86Register.ECX as X86Register32
    private val edx = X86Register.EDX as X86Register32
    private val ebx = X86Register.EBX as X86Register32
    private val esp = X86Register.ESP as X86Register32
    private val ebp = X86Register.EBP as X86Register32
    private val esi = X86Register.ESI as X86Register32
    private val edi = X86Register.EDI as X86Register32

    private val rax = X86Register.RAX as X86Register64
    private val rcx = X86Register.RCX as X86Register64
    private val rdx = X86Register.RDX as X86Register64
    private val rbx = X86Register.RBX as X86Register64
    private val rsp = X86Register.RSP as X86Register64
    private val rbp = X86Register.RBP as X86Register64
    private val rsi = X86Register.RSI as X86Register64
    private val rdi = X86Register.RDI as X86Register64
    private val r8  = X86Register.R8  as X86Register64
    private val r9  = X86Register.R9  as X86Register64
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
    private val xmm4 = X86Register.XMM4 as X86Xmm
    private val xmm5 = X86Register.XMM5 as X86Xmm
    private val xmm6 = X86Register.XMM6 as X86Xmm
    private val xmm7 = X86Register.XMM7 as X86Xmm

    private val ymm0 = X86Register.YMM0 as X86Ymm
    private val ymm1 = X86Register.YMM1 as X86Ymm
    private val ymm2 = X86Register.YMM2 as X86Ymm
    private val ymm3 = X86Register.YMM3 as X86Ymm

    private fun asm() = X86Assembler()

    // --- ADD extended ---

    @Test
    fun addR64ExtR64Ext() {
        val a = asm()
        a.add(r8, r9 as X86Operand64)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 3)
        // REX with W+R+B
        assertEquals(0x4D.toByte(), bytes[0])
    }

    @Test
    fun addR64ImmSmall() {
        val a = asm()
        a.add(rdi, 1)
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun addR32ImmLarge() {
        val a = asm()
        a.add(eax, 0x7FFFFFFF)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 5)
    }

    // --- SUB extended ---

    @Test
    fun subR64ExtImm() {
        val a = asm()
        a.sub(r12, 64)
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun subR32R32AllPairs() {
        for (dst in listOf(eax, ecx, edx, ebx)) {
            for (src in listOf(eax, ecx, edx, ebx)) {
                val a = asm()
                a.sub(dst, src as X86Operand32)
                val bytes = a.toByteArray()
                assertEquals(2, bytes.size, "sub $dst, $src")
            }
        }
    }

    // --- AND/OR/XOR extended ---

    @Test
    fun andR64R64() {
        val a = asm()
        a.and_(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
    }

    @Test
    fun orR64R64() {
        val a = asm()
        a.or_(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
    }

    @Test
    fun xorR32DifferentRegs() {
        val a = asm()
        a.xor_(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(2, bytes.size)
    }

    @Test
    fun xorR64ExtRegs() {
        val a = asm()
        a.xor_(r8, r9 as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x4D.toByte(), bytes[0])
    }

    // --- CMP extended ---

    @Test
    fun cmpR64R64() {
        val a = asm()
        a.cmp(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
    }

    @Test
    fun cmpR32ImmSmall() {
        val a = asm()
        a.cmp(eax, 42)
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    // --- TEST extended ---

    @Test
    fun testR32R32Different() {
        val a = asm()
        a.test(eax as X86Operand32, ecx)
        val bytes = a.toByteArray()
        assertEquals(0x85.toByte(), bytes[0])
    }

    @Test
    fun testR64ExtR64Ext() {
        val a = asm()
        a.test(r8 as X86Operand64, r8)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 3)
    }

    // --- NEG/NOT extended ---

    @Test
    fun negR64Ext() {
        val a = asm()
        a.neg(r12 as X86Operand64)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 3)
    }

    @Test
    fun notR64() {
        val a = asm()
        a.not_(rax as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0xF7.toByte(), bytes[1])
    }

    @Test
    fun notR64Ext() {
        val a = asm()
        a.not_(r15 as X86Operand64)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 3)
    }

    // --- IMUL extended ---

    @Test
    fun imulR64ExtR64Ext() {
        val a = asm()
        a.imul(r8, r9 as X86Operand64)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 4)
    }

    @Test
    fun imulR32Imm() {
        val a = asm()
        a.imul(eax, ecx as X86Operand32, 10)
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    // --- DIV/IDIV extended ---

    @Test
    fun divR64() {
        val a = asm()
        a.div(rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0xF7.toByte(), bytes[1])
    }

    @Test
    fun idivR32() {
        val a = asm()
        a.idiv(ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0xF7.toByte(), bytes[0])
    }

    // --- INC/DEC extended ---

    @Test
    fun incR64() {
        val a = asm()
        a.inc(rax as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0xFF.toByte(), bytes[1])
    }

    @Test
    fun incR64Ext() {
        val a = asm()
        a.inc(r15 as X86Operand64)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 3)
    }

    @Test
    fun decR32() {
        val a = asm()
        a.dec(ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0xFF.toByte(), bytes[0])
    }

    @Test
    fun decR64Ext() {
        val a = asm()
        a.dec(r8 as X86Operand64)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 3)
    }

    // --- SHIFTS extended ---

    @Test
    fun shlR64Imm() {
        val a = asm()
        a.shl(rax as X86Operand64, 8.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
    }

    @Test
    fun shrR32Imm() {
        val a = asm()
        a.shr(eax as X86Operand32, 4.toByte())
        val bytes = a.toByteArray()
        assertEquals(0xC1.toByte(), bytes[0])
    }

    @Test
    fun sarR64Imm() {
        val a = asm()
        a.sar(rax as X86Operand64, 16.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
    }

    // --- LEA extended ---

    @Test
    fun leaR64MemScaledIndex() {
        val a = asm()
        val mem = X86Memory.base(rbp).index(rcx, 8).build()
        a.lea(rax, mem)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 4)
    }

    @Test
    fun leaR64MemDisp32() {
        val a = asm()
        val mem = X86Memory.base(rsp).offset(1024)
        a.lea(rax, mem)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 5)
    }

    // --- MOV extended ---

    @Test
    fun movR64Imm() {
        val a = asm()
        a.mov(rax, 0x12345678L)
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movR32Mem() {
        val a = asm()
        val mem = X86Memory.base(rbp).offset(-4)
        a.mov(eax, mem as X86Operand32)
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movMemR32() {
        val a = asm()
        val mem = X86Memory.base(rbp).offset(-4)
        a.mov(mem, eax)
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    // --- Conditional moves extended ---

    @Test
    fun cmoveR64R64() {
        val a = asm()
        a.cmove(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x44.toByte(), bytes[2])
    }

    @Test
    fun cmovbR32R32() {
        val a = asm()
        a.cmovb(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x42.toByte(), bytes[1])
    }

    @Test
    fun cmovaeR32R32() {
        val a = asm()
        a.cmovae(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x43.toByte(), bytes[1])
    }

    @Test
    fun cmovleR32R32() {
        val a = asm()
        a.cmovle(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x4E.toByte(), bytes[1])
    }

    @Test
    fun cmovgR32R32() {
        val a = asm()
        a.cmovg(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x4F.toByte(), bytes[1])
    }

    // --- SET extended ---

    @Test
    fun setbR8() {
        val a = asm()
        a.setb(al as X86Operand8)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x92.toByte(), bytes[1])
    }

    @Test
    fun setaeR8() {
        val a = asm()
        a.setae(al as X86Operand8)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x93.toByte(), bytes[1])
    }

    @Test
    fun setleR8() {
        val a = asm()
        a.setle(al as X86Operand8)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x9E.toByte(), bytes[1])
    }

    @Test
    fun setgR8() {
        val a = asm()
        a.setg(al as X86Operand8)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x9F.toByte(), bytes[1])
    }

    @Test
    fun setgeR8() {
        val a = asm()
        a.setge(al as X86Operand8)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x9D.toByte(), bytes[1])
    }

    // --- BSWAP extended ---

    @Test
    fun bswapR32Ecx() {
        val a = asm()
        a.bswap(ecx)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xC9.toByte(), bytes[1])
    }

    @Test
    fun bswapR64Ext() {
        val a = asm()
        a.bswap(r8)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 3)
    }

    // --- BSF/BSR extended ---

    @Test
    fun bsfR64R64() {
        val a = asm()
        a.bsf(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xBC.toByte(), bytes[2])
    }

    @Test
    fun bsrR64R64() {
        val a = asm()
        a.bsr(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xBD.toByte(), bytes[2])
    }

    // --- POPCNT/LZCNT/TZCNT extended ---

    @Test
    fun popcntR64R64() {
        val a = asm()
        a.popcnt(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
    }

    @Test
    fun lzcntR64R64() {
        val a = asm()
        a.lzcnt(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
    }

    @Test
    fun tzcntR64R64() {
        val a = asm()
        a.tzcnt(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
    }

    // --- MOVZX / MOVSX extended ---

    @Test
    fun movzxR32R16() {
        val a = asm()
        a.movzx(eax, cx as X86Operand16)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xB7.toByte(), bytes[1])
    }

    @Test
    fun movsxR64R8() {
        val a = asm()
        a.movsx(rax, cl as X86Operand8)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
    }

    @Test
    fun movsxR32R16() {
        val a = asm()
        a.movsx(eax, cx as X86Operand16)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xBF.toByte(), bytes[1])
    }

    // --- XCHG extended ---

    @Test
    fun xchgR64ExtR64Ext() {
        val a = asm()
        a.xchg(r8, r9)
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    // --- SSE extended ---

    @Test
    fun subssXmm() {
        val a = asm()
        a.subss(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x5C.toByte(), bytes[2])
    }

    @Test
    fun mulssXmm() {
        val a = asm()
        a.mulss(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x59.toByte(), bytes[2])
    }

    @Test
    fun divssXmm() {
        val a = asm()
        a.divss(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x5E.toByte(), bytes[2])
    }

    @Test
    fun subpdXmm() {
        val a = asm()
        a.subpd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0x66.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x5C.toByte(), bytes[2])
    }

    @Test
    fun mulpdXmm() {
        val a = asm()
        a.mulpd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0x66.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x59.toByte(), bytes[2])
    }

    @Test
    fun divpdXmm() {
        val a = asm()
        a.divpd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0x66.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x5E.toByte(), bytes[2])
    }

    @Test
    fun subpsXmm() {
        val a = asm()
        a.subps(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x5C.toByte(), bytes[1])
    }

    @Test
    fun mulpsXmm() {
        val a = asm()
        a.mulps(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x59.toByte(), bytes[1])
    }

    @Test
    fun divpsXmm() {
        val a = asm()
        a.divps(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x5E.toByte(), bytes[1])
    }

    // --- SSE comparison extended ---

    @Test
    fun ucomissXmm() {
        val a = asm()
        a.ucomiss(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x2E.toByte(), bytes[1])
    }

    // --- SSE move extended ---

    @Test
    fun movapdXmm() {
        val a = asm()
        a.movapd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0x66.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x28.toByte(), bytes[2])
    }

    @Test
    fun movupdXmm() {
        val a = asm()
        a.movupd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0x66.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x10.toByte(), bytes[2])
    }

    @Test
    fun movsdXmm() {
        val a = asm()
        a.movsd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF2.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x10.toByte(), bytes[2])
    }

    @Test
    fun movssXmm() {
        val a = asm()
        a.movss(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x10.toByte(), bytes[2])
    }

    // --- SSE conversion ---

    @Test
    fun cvtsi2sdR64() {
        val a = asm()
        a.cvtsi2sd(xmm0, rax)
        val bytes = a.toByteArray()
        assertEquals(0xF2.toByte(), bytes[0])
    }

    @Test
    fun cvtsi2ssR32() {
        val a = asm()
        a.cvtsi2ss(xmm0, eax)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
    }

    @Test
    fun cvtsd2siR64() {
        val a = asm()
        a.cvtsd2si(rax, xmm0)
        val bytes = a.toByteArray()
        assertEquals(0xF2.toByte(), bytes[0])
    }

    @Test
    fun cvtss2sdXmm() {
        val a = asm()
        a.cvtss2sd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
    }

    @Test
    fun cvtsd2ssXmm() {
        val a = asm()
        a.cvtsd2ss(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF2.toByte(), bytes[0])
    }

    // --- VEX instructions extended ---

    @Test
    fun vsubpsYmm() {
        val a = asm()
        a.vsubps(ymm0, ymm1, ymm2)
        val bytes = a.toByteArray()
        assertTrue(bytes[0] == 0xC5.toByte() || bytes[0] == 0xC4.toByte())
    }

    @Test
    fun vmulpsYmm() {
        val a = asm()
        a.vmulps(ymm0, ymm1, ymm2)
        val bytes = a.toByteArray()
        assertTrue(bytes[0] == 0xC5.toByte() || bytes[0] == 0xC4.toByte())
    }

    @Test
    fun vdivpsYmm() {
        val a = asm()
        a.vdivps(ymm0, ymm1, ymm2)
        val bytes = a.toByteArray()
        assertTrue(bytes[0] == 0xC5.toByte() || bytes[0] == 0xC4.toByte())
    }

    @Test
    fun vaddpdXmm() {
        val a = asm()
        a.vaddpd(xmm0, xmm1, xmm2)
        val bytes = a.toByteArray()
        assertTrue(bytes[0] == 0xC5.toByte() || bytes[0] == 0xC4.toByte())
    }

    // --- BT / BTS / BTR / BTC extended ---

    @Test
    fun btR64Imm() {
        val a = asm()
        a.bt(rax as X86Operand64, 63.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
    }

    @Test
    fun btrR32Imm() {
        val a = asm()
        a.btr(eax as X86Operand32, 5.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xBA.toByte(), bytes[1])
    }

    @Test
    fun btcR32Imm() {
        val a = asm()
        a.btc(eax as X86Operand32, 7.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xBA.toByte(), bytes[1])
    }

    // --- CMPXCHG / XADD extended ---

    @Test
    fun cmpxchgR64R64() {
        val a = asm()
        a.cmpxchg(rax as X86Operand64, rcx)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xB1.toByte(), bytes[2])
    }

    @Test
    fun xaddR64R64() {
        val a = asm()
        a.xadd(rax as X86Operand64, rcx)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xC1.toByte(), bytes[2])
    }

    // --- Complex sequences ---

    @Test
    fun multipleInstructionSequence() {
        val a = asm()
        a.push(rbp)
        a.mov(rbp, rsp as X86Operand64)
        a.sub(rsp, 32)
        a.xor_(eax, eax as X86Operand32)
        a.add(rsp, 32)
        a.pop(rbp)
        a.ret()
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 15)
        assertEquals(0x55.toByte(), bytes[0]) // push rbp
        assertEquals(0xC3.toByte(), bytes.last()) // ret
    }

    @Test
    fun sseArithSequence() {
        val a = asm()
        a.movsd(xmm0, xmm1)
        a.addsd(xmm0, xmm2)
        a.mulsd(xmm0, xmm3)
        a.subsd(xmm0, xmm4)
        a.divsd(xmm0, xmm5)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 20)
    }

    @Test
    fun pushPopSequencePreservesSize() {
        val a = asm()
        val regs = listOf(rax, rbx, rcx, rdx, rsi, rdi)
        for (r in regs) a.push(r)
        for (r in regs.reversed()) a.pop(r)
        val bytes = a.toByteArray()
        // Each push/pop is 1 byte for non-extended regs
        assertEquals(12, bytes.size)
    }

    @Test
    fun callAndRetSequence() {
        val a = asm()
        a.callLabel("func")
        a.ret()
        a.label("func")
        a.push(rbp)
        a.mov(rbp, rsp as X86Operand64)
        a.pop(rbp)
        a.ret()
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 10)
        assertEquals(0xE8.toByte(), bytes[0])
    }

    @Test
    fun jmpLabelForward() {
        val a = asm()
        a.jmpLabel("end")
        a.nop()
        a.nop()
        a.label("end")
        a.ret()
        val bytes = a.toByteArray()
        assertEquals(0xE9.toByte(), bytes[0])
    }

    @Test
    fun jmpLabelBackward() {
        val a = asm()
        a.label("loop")
        a.nop()
        a.jmpLabel("loop")
        val bytes = a.toByteArray()
        assertEquals(0xE9.toByte(), bytes[1]) // after the nop
    }

    // --- Flag instructions extended ---

    @Test
    fun stdEncoding() {
        val a = asm()
        a.std()
        assertArrayEquals(byteArrayOf(0xFD.toByte()), a.toByteArray())
    }

    @Test
    fun popfqEncoding() {
        val a = asm()
        a.popfq()
        assertArrayEquals(byteArrayOf(0x9D.toByte()), a.toByteArray())
    }

    // --- PAUSE / CPUID / RDTSC ---

    @Test
    fun pauseEncoding() {
        val a = asm()
        a.pause()
        val bytes = a.toByteArray()
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x90.toByte(), bytes[1])
    }

    @Test
    fun cpuidEncoding() {
        val a = asm()
        a.cpuid()
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xA2.toByte(), bytes[1])
    }

    @Test
    fun rdtscEncoding() {
        val a = asm()
        a.rdtsc()
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x31.toByte(), bytes[1])
    }

    // --- Position tracking ---

    @Test
    fun positionAfterMultipleInstructions() {
        val a = asm()
        assertEquals(0, a.position())
        a.nop()
        assertEquals(1, a.position())
        a.ret()
        assertEquals(2, a.position())
        a.push(rbp)
        assertEquals(3, a.position())
    }

    @Test
    fun resetAndReassemble() {
        val a = asm()
        a.nop()
        a.nop()
        a.nop()
        assertEquals(3, a.position())
        a.reset()
        assertEquals(0, a.position())
        a.ret()
        assertEquals(1, a.position())
        assertArrayEquals(byteArrayOf(0xC3.toByte()), a.toByteArray())
    }
}
