package org.kgen.target.x86.asm

import org.kgen.target.x86.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class X86AssemblerEncodingExtendedTest {

    private val eax = X86Register.EAX as X86Register32
    private val ecx = X86Register.ECX as X86Register32
    private val edx = X86Register.EDX as X86Register32
    private val ebx = X86Register.EBX as X86Register32
    private val esi = X86Register.ESI as X86Register32
    private val edi = X86Register.EDI as X86Register32
    private val r8d = X86Register.R8D as X86Register32
    private val r9d = X86Register.R9D as X86Register32
    private val r10d = X86Register.R10D as X86Register32
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

    private val al = X86Register.AL as X86Register8
    private val cl = X86Register.CL as X86Register8
    private val dl = X86Register.DL as X86Register8
    private val bl = X86Register.BL as X86Register8

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

    private fun assemble(block: X86Assembler.() -> Unit): ByteArray {
        val asm = X86Assembler()
        asm.block()
        return asm.toByteArray()
    }

    // --- BMI1 instructions ---

    @Test
    fun andn32() {
        val bytes = assemble { andn(eax, ecx, edx as X86Operand32) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun andn64() {
        val bytes = assemble { andn(rax, rcx, rdx as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun bextr32() {
        val bytes = assemble { bextr(eax, ecx as X86Operand32, edx) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun bextr64() {
        val bytes = assemble { bextr(rax, rcx as X86Operand64, rdx) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun blsi32() {
        val bytes = assemble { blsi(eax, ecx as X86Operand32) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun blsi64() {
        val bytes = assemble { blsi(rax, rcx as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun blsmsk32() {
        val bytes = assemble { blsmsk(eax, ecx as X86Operand32) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun blsmsk64() {
        val bytes = assemble { blsmsk(rax, rcx as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun blsr32() {
        val bytes = assemble { blsr(eax, ecx as X86Operand32) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun blsr64() {
        val bytes = assemble { blsr(rax, rcx as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- BMI2 instructions ---

    @Test
    fun bzhi32() {
        val bytes = assemble { bzhi(eax, ecx as X86Operand32, edx) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun bzhi64() {
        val bytes = assemble { bzhi(rax, rcx as X86Operand64, rdx) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun mulx32() {
        val bytes = assemble { mulx(eax, ecx, edx as X86Operand32) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun mulx64() {
        val bytes = assemble { mulx(rax, rcx, rdx as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun pdep32() {
        val bytes = assemble { pdep(eax, ecx, edx as X86Operand32) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun pdep64() {
        val bytes = assemble { pdep(rax, rcx, rdx as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun pext32() {
        val bytes = assemble { pext(eax, ecx, edx as X86Operand32) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun pext64() {
        val bytes = assemble { pext(rax, rcx, rdx as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun rorx32() {
        val bytes = assemble { rorx(eax, ecx as X86Operand32, 7) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun rorx64() {
        val bytes = assemble { rorx(rax, rcx as X86Operand64, 13) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun sarx32() {
        val bytes = assemble { sarx(eax, ecx as X86Operand32, edx) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun sarx64() {
        val bytes = assemble { sarx(rax, rcx as X86Operand64, rdx) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun shrx32() {
        val bytes = assemble { shrx(eax, ecx as X86Operand32, edx) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun shrx64() {
        val bytes = assemble { shrx(rax, rcx as X86Operand64, rdx) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun shlx32() {
        val bytes = assemble { shlx(eax, ecx as X86Operand32, edx) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun shlx64() {
        val bytes = assemble { shlx(rax, rcx as X86Operand64, rdx) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- Bit counting ---

    @Test
    fun popcnt32() {
        val bytes = assemble { popcnt(eax, ecx as X86Operand32) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun popcnt64() {
        val bytes = assemble { popcnt(rax, rcx as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun lzcnt32() {
        val bytes = assemble { lzcnt(eax, ecx as X86Operand32) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun lzcnt64() {
        val bytes = assemble { lzcnt(rax, rcx as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun tzcnt32() {
        val bytes = assemble { tzcnt(eax, ecx as X86Operand32) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun tzcnt64() {
        val bytes = assemble { tzcnt(rax, rcx as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- CMPXCHG ---

    @Test
    fun cmpxchg8() {
        val bytes = assemble { cmpxchg(al as X86Operand8, cl) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun cmpxchg32() {
        val bytes = assemble { cmpxchg(eax as X86Operand32, ecx) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun cmpxchg64() {
        val bytes = assemble { cmpxchg(rax as X86Operand64, rcx) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- XADD ---

    @Test
    fun xadd8() {
        val bytes = assemble { xadd(al as X86Operand8, cl) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun xadd32() {
        val bytes = assemble { xadd(eax as X86Operand32, ecx) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun xadd64() {
        val bytes = assemble { xadd(rax as X86Operand64, rcx) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- Fence instructions ---

    @Test
    fun mfenceEncoding() {
        val bytes = assemble { mfence() }
        assertEquals(3, bytes.size)
    }

    @Test
    fun lfenceEncoding() {
        val bytes = assemble { lfence() }
        assertEquals(3, bytes.size)
    }

    @Test
    fun sfenceEncoding() {
        val bytes = assemble { sfence() }
        assertEquals(3, bytes.size)
    }

    // --- RDRAND / RDSEED ---

    @Test
    fun rdrand32() {
        val bytes = assemble { rdrand(eax) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun rdrand64() {
        val bytes = assemble { rdrand(rax) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun rdseed32() {
        val bytes = assemble { rdseed(eax) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun rdseed64() {
        val bytes = assemble { rdseed(rax) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- VEX-encoded SSE instructions (YMM) ---

    @Test
    fun vaddpsYmm() {
        val bytes = assemble { vaddps(ymm0, ymm1, ymm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vsubpsYmm() {
        val bytes = assemble { vsubps(ymm0, ymm1, ymm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vmulpsYmm() {
        val bytes = assemble { vmulps(ymm0, ymm1, ymm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vdivpsYmm() {
        val bytes = assemble { vdivps(ymm0, ymm1, ymm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vaddpdYmm() {
        val bytes = assemble { vaddpd(ymm0, ymm1, ymm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vsubpdYmm() {
        val bytes = assemble { vsubpd(ymm0, ymm1, ymm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vmulpdYmm() {
        val bytes = assemble { vmulpd(ymm0, ymm1, ymm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vdivpdYmm() {
        val bytes = assemble { vdivpd(ymm0, ymm1, ymm2) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- VEX scalar ---

    @Test
    fun vaddssXmm() {
        val bytes = assemble { vaddss(xmm0, xmm1, xmm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vaddsdXmm() {
        val bytes = assemble { vaddsd(xmm0, xmm1, xmm2) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- VEX XMM packed ---

    @Test
    fun vaddpsXmm() {
        val bytes = assemble { vaddps(xmm0, xmm1, xmm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vsubpsXmm() {
        val bytes = assemble { vsubps(xmm0, xmm1, xmm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vmulpsXmm() {
        val bytes = assemble { vmulps(xmm0, xmm1, xmm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vdivpsXmm() {
        val bytes = assemble { vdivps(xmm0, xmm1, xmm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vaddpdXmm() {
        val bytes = assemble { vaddpd(xmm0, xmm1, xmm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vsubpdXmm() {
        val bytes = assemble { vsubpd(xmm0, xmm1, xmm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vmulpdXmm() {
        val bytes = assemble { vmulpd(xmm0, xmm1, xmm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vdivpdXmm() {
        val bytes = assemble { vdivpd(xmm0, xmm1, xmm2) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- VEX with different register combos ---

    @Test
    fun vaddpsHighRegs() {
        val bytes = assemble { vaddps(ymm3, ymm2, ymm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vaddsdHighXmm() {
        val bytes = assemble { vaddsd(xmm5, xmm6, xmm7) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- VEX logical ---

    @Test
    fun vxorpsXmm() {
        val bytes = assemble { vxorps(xmm0, xmm1, xmm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vxorpsYmm() {
        val bytes = assemble { vxorps(ymm0, ymm1, ymm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vxorpdXmm() {
        val bytes = assemble { vxorpd(xmm0, xmm1, xmm2) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vxorpdYmm() {
        val bytes = assemble { vxorpd(ymm0, ymm1, ymm2) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- 8-bit arithmetic ---

    @Test
    fun add8() {
        val bytes = assemble { add(al, cl as X86Operand8) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun sub8() {
        val bytes = assemble { sub(al, cl as X86Operand8) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun and8() {
        val bytes = assemble { and_(al, cl as X86Operand8) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun or8() {
        val bytes = assemble { or_(al, cl as X86Operand8) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun xor8() {
        val bytes = assemble { xor_(al, cl as X86Operand8) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun cmp8() {
        val bytes = assemble { cmp(al, cl as X86Operand8) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- 8-bit immediate arithmetic ---

    @Test
    fun addAlImm() {
        val bytes = assemble { add(al, 0x42.toByte()) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun subAlImm() {
        val bytes = assemble { sub(al, 0x42.toByte()) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun cmpAlImm() {
        val bytes = assemble { cmp(al, 0x42.toByte()) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- Memory operations with various addressing modes ---

    @Test
    fun movMemBaseOnly() {
        val mem = X86Memory.base(rbx).build()
        val bytes = assemble { mov(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movMemBaseDisp8() {
        val mem = X86Memory.base(rbp).offset(8)
        val bytes = assemble { mov(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movMemBaseDisp32() {
        val mem = X86Memory.base(rbx).offset(0x12345678)
        val bytes = assemble { mov(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movMemBaseIndex() {
        val mem = X86Memory.base(rbx).index(rcx, 1).build()
        val bytes = assemble { mov(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movMemBaseIndexScale2() {
        val mem = X86Memory.base(rbx).index(rcx, 2).build()
        val bytes = assemble { mov(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movMemBaseIndexScale4() {
        val mem = X86Memory.base(rbx).index(rcx, 4).build()
        val bytes = assemble { mov(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movMemBaseIndexScale8() {
        val mem = X86Memory.base(rbx).index(rcx, 8).build()
        val bytes = assemble { mov(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movMemBaseIndexDisp() {
        val mem = X86Memory.base(rbx).index(rcx, 4).offset(16)
        val bytes = assemble { mov(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movMemRspBase() {
        val mem = X86Memory.base(rsp).build()
        val bytes = assemble { mov(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movMemRbpBaseNoDisp() {
        val mem = X86Memory.base(rbp).build()
        val bytes = assemble { mov(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movMemR12Base() {
        val mem = X86Memory.base(r12).build()
        val bytes = assemble { mov(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movMemR13Base() {
        val mem = X86Memory.base(r13).build()
        val bytes = assemble { mov(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- Store to memory ---

    @Test
    fun storeR64ToMem() {
        val mem = X86Memory.base(rsp).offset(-8)
        val bytes = assemble { mov(mem, rax) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun storeR32ToMem() {
        val mem = X86Memory.base(rsp).offset(4)
        val bytes = assemble { mov(mem, eax) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- Extended registers ---

    @Test
    fun addExtendedR64() {
        val bytes = assemble { add(r8, r9 as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun addR8ToR15() {
        val bytes = assemble { add(r8, r15 as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun subR10R11() {
        val bytes = assemble { sub(r10, r11 as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun andR12R13() {
        val bytes = assemble { and_(r12, r13 as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun orR14R15() {
        val bytes = assemble { or_(r14, r15 as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun xorR9R10() {
        val bytes = assemble { xor_(r9, r10 as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movR8R15() {
        val bytes = assemble { mov(r8, r15 as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun addExtendedR32() {
        val bytes = assemble { add(r8d, r9d as X86Operand32) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- LEA with various addressing ---

    @Test
    fun leaBaseIndex() {
        val mem = X86Memory.base(rbx).index(rcx, 4).build()
        val bytes = assemble { lea(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun leaBaseDisp() {
        val mem = X86Memory.base(rsp).offset(16)
        val bytes = assemble { lea(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun leaBaseIndexDisp() {
        val mem = X86Memory.base(rbp).index(rsi, 8).offset(-32)
        val bytes = assemble { lea(rax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun leaR32() {
        val mem = X86Memory.base(rbx).offset(4)
        val bytes = assemble { lea(eax, mem) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- SSE packed operations ---

    @Test
    fun addps() {
        val bytes = assemble { addps(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun subps() {
        val bytes = assemble { subps(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun mulps() {
        val bytes = assemble { mulps(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun divps() {
        val bytes = assemble { divps(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun addpd() {
        val bytes = assemble { addpd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun subpd() {
        val bytes = assemble { subpd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun mulpd() {
        val bytes = assemble { mulpd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun divpd() {
        val bytes = assemble { divpd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- SSE comparison ---

    @Test
    fun ucomiss() {
        val bytes = assemble { ucomiss(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun ucomisd() {
        val bytes = assemble { ucomisd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- SSE moves ---

    @Test
    fun movaps() {
        val bytes = assemble { movaps(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movups() {
        val bytes = assemble { movups(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movapd() {
        val bytes = assemble { movapd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun movupd() {
        val bytes = assemble { movupd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- SSE sqrt ---

    @Test
    fun sqrtss() {
        val bytes = assemble { sqrtss(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun sqrtsd() {
        val bytes = assemble { sqrtsd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun sqrtps() {
        val bytes = assemble { sqrtps(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun sqrtpd() {
        val bytes = assemble { sqrtpd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- SSE min/max ---

    @Test
    fun minss() {
        val bytes = assemble { minss(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun maxss() {
        val bytes = assemble { maxss(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun minsd() {
        val bytes = assemble { minsd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun maxsd() {
        val bytes = assemble { maxsd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun minps() {
        val bytes = assemble { minps(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun maxps() {
        val bytes = assemble { maxps(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun minpd() {
        val bytes = assemble { minpd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun maxpd() {
        val bytes = assemble { maxpd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- SSE shuffle ---

    @Test
    fun shufps() {
        val bytes = assemble { shufps(xmm0, xmm1, 0x1B.toByte()) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun shufpd() {
        val bytes = assemble { shufpd(xmm0, xmm1, 0x01.toByte()) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- SSE unpack ---

    @Test
    fun unpcklps() {
        val bytes = assemble { unpcklps(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun unpckhps() {
        val bytes = assemble { unpckhps(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun unpcklpd() {
        val bytes = assemble { unpcklpd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun unpckhpd() {
        val bytes = assemble { unpckhpd(xmm0, xmm1) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- CLFLUSH ---

    @Test
    fun clflush() {
        val mem = X86Memory.base(rax).build()
        val bytes = assemble { clflush(mem) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- Encoding size checks for known instructions ---

    @Test
    fun nopIs1Byte() {
        val bytes = assemble { nop() }
        assertEquals(1, bytes.size)
        assertEquals(0x90.toByte(), bytes[0])
    }

    @Test
    fun retIs1Byte() {
        val bytes = assemble { ret() }
        assertEquals(1, bytes.size)
        assertEquals(0xC3.toByte(), bytes[0])
    }

    @Test
    fun syscallIs2Bytes() {
        val bytes = assemble { syscall() }
        assertEquals(2, bytes.size)
    }

    @Test
    fun pushR64RegisterEncoding() {
        val bytes = assemble { push(rax) }
        assertEquals(1, bytes.size)
        assertEquals(0x50.toByte(), bytes[0])
    }

    @Test
    fun pushRbpRegisterEncoding() {
        val bytes = assemble { push(rbp) }
        assertEquals(1, bytes.size)
        assertEquals(0x55.toByte(), bytes[0])
    }

    @Test
    fun popRbpRegisterEncoding() {
        val bytes = assemble { pop(rbp) }
        assertEquals(1, bytes.size)
        assertEquals(0x5D.toByte(), bytes[0])
    }

    @Test
    fun pushR8NeedsRex() {
        val bytes = assemble { push(r8) }
        assertEquals(2, bytes.size)
    }

    @Test
    fun pushR15NeedsRex() {
        val bytes = assemble { push(r15) }
        assertEquals(2, bytes.size)
    }

    @Test
    fun pushModrmEncoding() {
        val bytes = assemble { push(rax as X86Operand64) }
        assertEquals(2, bytes.size)
    }

    // --- Multiple instructions sequence encoding ---

    @Test
    fun prologEpilogSequence() {
        val bytes = assemble {
            push(rbp)
            mov(rbp, rsp as X86Operand64)
            sub(rsp, 32)
            add(rsp, 32)
            pop(rbp)
            ret()
        }
        assertTrue(bytes.size >= 6)
        assertEquals(0x55.toByte(), bytes[0])
        assertEquals(0xC3.toByte(), bytes[bytes.size - 1])
    }

    @Test
    fun arithmeticSequence() {
        val bytes = assemble {
            add(rax, rcx as X86Operand64)
            sub(rax, rdx as X86Operand64)
            and_(rax, rbx as X86Operand64)
            or_(rax, rsi as X86Operand64)
            xor_(rax, rdi as X86Operand64)
        }
        assertTrue(bytes.size >= 15)
    }

    @Test
    fun sseArithSequence() {
        val bytes = assemble {
            addsd(xmm0, xmm1)
            mulsd(xmm0, xmm2)
            subsd(xmm0, xmm3)
            divsd(xmm0, xmm4)
        }
        assertTrue(bytes.size >= 16)
    }

    // --- BMI with extended registers ---

    @Test
    fun andnExtRegs64() {
        val bytes = assemble { andn(r8, r9, r10 as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun bextrExtRegs64() {
        val bytes = assemble { bextr(r8, r9 as X86Operand64, r10) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun blsiExtReg64() {
        val bytes = assemble { blsi(r8, r9 as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun pextExtRegs64() {
        val bytes = assemble { pext(r8, r9, r10 as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun pdepExtRegs64() {
        val bytes = assemble { pdep(r8, r9, r10 as X86Operand64) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- VEX with XMM high registers ---

    @Test
    fun vaddssXmm4Xmm5Xmm6() {
        val bytes = assemble { vaddss(xmm4, xmm5, xmm6) }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vaddsdXmm3Xmm4Xmm5() {
        val bytes = assemble { vaddsd(xmm3, xmm4, xmm5) }
        assertTrue(bytes.isNotEmpty())
    }

    // --- Empty assembler ---

    @Test
    fun emptyAssemblerProducesNoBytes() {
        val bytes = assemble { }
        assertEquals(0, bytes.size)
    }
}
