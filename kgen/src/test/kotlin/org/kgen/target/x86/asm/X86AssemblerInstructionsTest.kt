package org.kgen.target.x86.asm

import org.kgen.target.x86.*

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Comprehensive tests for x86 assembler instruction encoding.
 * Covers arithmetic, logic, stack, control flow, string, bit manipulation,
 * SSE, and other instruction categories.
 */
class X86AssemblerInstructionsTest {

    private val al = X86Register.AL as X86Register8
    private val cl = X86Register.CL as X86Register8
    private val ah = X86Register.AH as X86Register8

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

    private val ymm0 = X86Register.YMM0 as X86Ymm
    private val ymm1 = X86Register.YMM1 as X86Ymm
    private val ymm2 = X86Register.YMM2 as X86Ymm

    private fun asm() = X86Assembler()

    // ---- NOP / RET ----

    @Test
    fun nopEncoding() {
        val a = asm()
        a.nop()
        assertArrayEquals(byteArrayOf(0x90.toByte()), a.toByteArray())
    }

    @Test
    fun retEncoding() {
        val a = asm()
        a.ret()
        assertArrayEquals(byteArrayOf(0xC3.toByte()), a.toByteArray())
    }

    @Test
    fun retImmEncoding() {
        val a = asm()
        a.ret(8.toShort())
        val bytes = a.toByteArray()
        assertEquals(0xC2.toByte(), bytes[0])
        assertEquals(8.toByte(), bytes[1])
        assertEquals(0.toByte(), bytes[2])
    }

    // ---- PUSH / POP ----

    @Test
    fun pushR64() {
        val a = asm()
        a.push(rbp)
        assertArrayEquals(byteArrayOf(0x55), a.toByteArray())
    }

    @Test
    fun pushR64Extended() {
        val a = asm()
        a.push(r12)
        val bytes = a.toByteArray()
        // r12 needs REX.B prefix
        assertEquals(0x41.toByte(), bytes[0])
        assertEquals(0x54.toByte(), bytes[1])
    }

    @Test
    fun popR64() {
        val a = asm()
        a.pop(rbp)
        assertArrayEquals(byteArrayOf(0x5D), a.toByteArray())
    }

    @Test
    fun popR64Extended() {
        val a = asm()
        a.pop(r15)
        val bytes = a.toByteArray()
        assertEquals(0x41.toByte(), bytes[0])
        assertEquals(0x5F.toByte(), bytes[1])
    }

    @Test
    fun pushImm8() {
        val a = asm()
        a.push(42.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x6A.toByte(), bytes[0])
        assertEquals(42.toByte(), bytes[1])
    }

    @Test
    fun pushImm32() {
        val a = asm()
        a.push(0x12345678)
        val bytes = a.toByteArray()
        assertEquals(0x68.toByte(), bytes[0])
        assertEquals(0x78.toByte(), bytes[1])
        assertEquals(0x56.toByte(), bytes[2])
        assertEquals(0x34.toByte(), bytes[3])
        assertEquals(0x12.toByte(), bytes[4])
    }

    // ---- ADD ----

    @Test
    fun addR32R32() {
        val a = asm()
        a.add(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x03.toByte(), bytes[0])
        assertEquals(0xC1.toByte(), bytes[1])
    }

    @Test
    fun addR64R64() {
        val a = asm()
        a.add(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
        assertEquals(0x03.toByte(), bytes[1])
        assertEquals(0xC1.toByte(), bytes[2])
    }

    @Test
    fun addR32Imm() {
        val a = asm()
        a.add(eax, 100)
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun addR64Imm() {
        val a = asm()
        a.add(rax, 100)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
    }

    // ---- SUB ----

    @Test
    fun subR64R64() {
        val a = asm()
        a.sub(rsp, 0x28)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 3)
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
    }

    @Test
    fun subR32R32() {
        val a = asm()
        a.sub(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(2, bytes.size)
    }

    // ---- AND / OR / XOR ----

    @Test
    fun andR32R32() {
        val a = asm()
        a.and_(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x23.toByte(), bytes[0])
    }

    @Test
    fun orR32R32() {
        val a = asm()
        a.or_(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x0B.toByte(), bytes[0])
    }

    @Test
    fun xorR64SelfClear() {
        val a = asm()
        a.xor_(rax, rax as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
        assertEquals(0x33.toByte(), bytes[1])
        assertEquals(0xC0.toByte(), bytes[2])
    }

    // ---- CMP / TEST ----

    @Test
    fun cmpR32R32() {
        val a = asm()
        a.cmp(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x3B.toByte(), bytes[0])
    }

    @Test
    fun cmpR64Imm() {
        val a = asm()
        a.cmp(rax, 0)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
    }

    @Test
    fun testR32R32() {
        val a = asm()
        a.test(eax as X86Operand32, eax)
        val bytes = a.toByteArray()
        assertEquals(0x85.toByte(), bytes[0])
        assertEquals(0xC0.toByte(), bytes[1])
    }

    @Test
    fun testR64R64() {
        val a = asm()
        a.test(rax as X86Operand64, rax)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x85.toByte(), bytes[1])
    }

    // ---- NEG / NOT ----

    @Test
    fun negR32() {
        val a = asm()
        a.neg(eax as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0xF7.toByte(), bytes[0])
        // ModR/M: mod=11, ext=3, rm=0 → 0xD8
        assertEquals(0xD8.toByte(), bytes[1])
    }

    @Test
    fun negR64() {
        val a = asm()
        a.neg(rax as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
        assertEquals(0xF7.toByte(), bytes[1])
    }

    @Test
    fun notR32() {
        val a = asm()
        a.not_(eax as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0xF7.toByte(), bytes[0])
        // ModR/M: mod=11, ext=2, rm=0 → 0xD0
        assertEquals(0xD0.toByte(), bytes[1])
    }

    // ---- MUL / IMUL / DIV / IDIV ----

    @Test
    fun imulR32R32() {
        val a = asm()
        a.imul(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        // 0F AF /r
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xAF.toByte(), bytes[1])
    }

    @Test
    fun imulR64R64() {
        val a = asm()
        a.imul(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xAF.toByte(), bytes[2])
    }

    @Test
    fun divR32() {
        val a = asm()
        a.div(ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0xF7.toByte(), bytes[0])
        // ext=6, rm=1 → 0xC0|0x30|1 = 0xF1
        assertEquals(0xF1.toByte(), bytes[1])
    }

    @Test
    fun idivR64() {
        val a = asm()
        a.idiv(rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0xF7.toByte(), bytes[1])
    }

    // ---- INC / DEC ----

    @Test
    fun incR32() {
        val a = asm()
        a.inc(eax as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0xFF.toByte(), bytes[0])
        // ext=0, rm=0 → 0xC0
        assertEquals(0xC0.toByte(), bytes[1])
    }

    @Test
    fun decR64() {
        val a = asm()
        a.dec(rax as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0xFF.toByte(), bytes[1])
        // ext=1, rm=0 → 0xC8
        assertEquals(0xC8.toByte(), bytes[2])
    }

    // ---- SHIFTS ----

    @Test
    fun shlR32Imm() {
        val a = asm()
        a.shl(eax as X86Operand32, 4.toByte())
        val bytes = a.toByteArray()
        assertEquals(0xC1.toByte(), bytes[0])
    }

    @Test
    fun shrR64Imm() {
        val a = asm()
        a.shr(rax as X86Operand64, 1.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
    }

    @Test
    fun sarR32Imm() {
        val a = asm()
        a.sar(eax as X86Operand32, 3.toByte())
        val bytes = a.toByteArray()
        assertEquals(0xC1.toByte(), bytes[0])
    }

    // ---- LEA ----

    @Test
    fun leaR64Mem() {
        val a = asm()
        val mem = X86Memory.base(rbp).offset(-16)
        a.lea(rax, mem)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
        assertEquals(0x8D.toByte(), bytes[1])
    }

    @Test
    fun leaR32Mem() {
        val a = asm()
        val mem = X86Memory.base(rbp).offset(8)
        a.lea(eax, mem)
        val bytes = a.toByteArray()
        assertEquals(0x8D.toByte(), bytes[0])
    }

    // ---- MEMORY with SIB ----

    @Test
    fun movR64MemBaseIndex() {
        val a = asm()
        val mem = X86Memory.base(rax).index(rcx, 4).build()
        a.mov(rdx, mem as X86Operand64)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 4)
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
        assertEquals(0x8B.toByte(), bytes[1]) // MOV opcode
    }

    @Test
    fun movMemR64Displacement32() {
        val a = asm()
        val mem = X86Memory.base(rsp).offset(256)
        a.mov(mem, rax)
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 7) // REX + opcode + ModRM + SIB + disp32
    }

    // ---- CONDITIONAL MOVES ----

    @Test
    fun cmoveR32R32() {
        val a = asm()
        a.cmove(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x44.toByte(), bytes[1])
    }

    @Test
    fun cmovneR64R64() {
        val a = asm()
        a.cmovne(rax, rcx as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x45.toByte(), bytes[2])
    }

    @Test
    fun cmovlR32R32() {
        val a = asm()
        a.cmovl(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x4C.toByte(), bytes[1])
    }

    @Test
    fun cmovgeR32R32() {
        val a = asm()
        a.cmovge(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x4D.toByte(), bytes[1])
    }

    // ---- BSWAP ----

    @Test
    fun bswapR32() {
        val a = asm()
        a.bswap(eax)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xC8.toByte(), bytes[1])
    }

    @Test
    fun bswapR64() {
        val a = asm()
        a.bswap(rax)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xC8.toByte(), bytes[2])
    }

    // ---- BSF / BSR / POPCNT / LZCNT / TZCNT ----

    @Test
    fun bsfR32R32() {
        val a = asm()
        a.bsf(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xBC.toByte(), bytes[1])
    }

    @Test
    fun bsrR32R32() {
        val a = asm()
        a.bsr(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xBD.toByte(), bytes[1])
    }

    @Test
    fun popcntR32R32() {
        val a = asm()
        a.popcnt(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        // F3 0F B8 /r
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xB8.toByte(), bytes[2])
    }

    @Test
    fun lzcntR32R32() {
        val a = asm()
        a.lzcnt(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        // F3 0F BD /r
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xBD.toByte(), bytes[2])
    }

    @Test
    fun tzcntR32R32() {
        val a = asm()
        a.tzcnt(eax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        // F3 0F BC /r
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xBC.toByte(), bytes[2])
    }

    // ---- MOVZX / MOVSX ----

    @Test
    fun movzxR32R8() {
        val a = asm()
        a.movzx(eax, cl as X86Operand8)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xB6.toByte(), bytes[1])
    }

    @Test
    fun movzxR64R8() {
        val a = asm()
        a.movzx(rax, cl as X86Operand8)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0xB6.toByte(), bytes[2])
    }

    @Test
    fun movsxR32R8() {
        val a = asm()
        a.movsx(eax, cl as X86Operand8)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xBE.toByte(), bytes[1])
    }

    @Test
    fun movsxdR64R32() {
        val a = asm()
        a.movsxd(rax, ecx as X86Operand32)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
        assertEquals(0x63.toByte(), bytes[1])
    }

    // ---- XCHG ----

    @Test
    fun xchgR32R32() {
        val a = asm()
        a.xchg(eax, ecx)
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun xchgR64R64() {
        val a = asm()
        a.xchg(rax, rcx)
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
    }

    // ---- SET (byte from flags) ----

    @Test
    fun seteR8() {
        val a = asm()
        a.sete(al as X86Operand8)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x94.toByte(), bytes[1])
    }

    @Test
    fun setneR8() {
        val a = asm()
        a.setne(al as X86Operand8)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x95.toByte(), bytes[1])
    }

    @Test
    fun setlR8() {
        val a = asm()
        a.setl(al as X86Operand8)
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x9C.toByte(), bytes[1])
    }

    // ---- CALL / JMP labels ----

    @Test
    fun callLabelResolution() {
        val a = asm()
        a.callLabel("target")
        a.nop()
        a.label("target")
        a.ret()
        val bytes = a.toByteArray()
        // call target: E8 01 00 00 00 (offset = 1 byte nop after call)
        assertEquals(0xE8.toByte(), bytes[0])
        // relative offset should point past the nop
        val rel = (bytes[1].toInt() and 0xFF) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                ((bytes[3].toInt() and 0xFF) shl 16) or
                ((bytes[4].toInt() and 0xFF) shl 24)
        assertEquals(1, rel) // 1 byte nop between call end and target
    }

    @Test
    fun jccLabelForwardJump() {
        val a = asm()
        a.jccLabel(0x04, "end") // je
        a.nop()
        a.nop()
        a.label("end")
        a.ret()
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x84.toByte(), bytes[1])
        val rel = (bytes[2].toInt() and 0xFF) or
                ((bytes[3].toInt() and 0xFF) shl 8) or
                ((bytes[4].toInt() and 0xFF) shl 16) or
                ((bytes[5].toInt() and 0xFF) shl 24)
        assertEquals(2, rel) // 2 nops after jcc
    }

    // ---- CLC / STC / CMC / CLD / STD ----

    @Test
    fun flagInstructions() {
        val a = asm()
        a.clc()
        a.stc()
        a.cmc()
        a.cld()
        val bytes = a.toByteArray()
        assertEquals(0xF8.toByte(), bytes[0]) // CLC
        assertEquals(0xF9.toByte(), bytes[1]) // STC
        assertEquals(0xF5.toByte(), bytes[2]) // CMC
        assertEquals(0xFC.toByte(), bytes[3]) // CLD
    }

    // ---- SYSCALL ----

    @Test
    fun syscallEncoding() {
        val a = asm()
        a.syscall()
        assertArrayEquals(byteArrayOf(0x0F, 0x05), a.toByteArray())
    }

    // ---- CDQ / CQO / CBW / CDQE ----

    @Test
    fun cdqEncoding() {
        val a = asm()
        a.cdq()
        assertArrayEquals(byteArrayOf(0x99.toByte()), a.toByteArray())
    }

    @Test
    fun cqoEncoding() {
        val a = asm()
        a.cqo()
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
        assertEquals(0x99.toByte(), bytes[1])
    }

    @Test
    fun cbwEncoding() {
        val a = asm()
        a.cbw()
        val bytes = a.toByteArray()
        assertEquals(0x66.toByte(), bytes[0]) // operand size prefix
        assertEquals(0x98.toByte(), bytes[1])
    }

    @Test
    fun cdqeEncoding() {
        val a = asm()
        a.cdqe()
        val bytes = a.toByteArray()
        assertEquals(0x48.toByte(), bytes[0]) // REX.W
        assertEquals(0x98.toByte(), bytes[1])
    }

    // ---- SSE arithmetic ----

    @Test
    fun addsdXmm() {
        val a = asm()
        a.addsd(xmm0, xmm1)
        val bytes = a.toByteArray()
        // F2 0F 58 /r
        assertEquals(0xF2.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x58.toByte(), bytes[2])
    }

    @Test
    fun addssXmm() {
        val a = asm()
        a.addss(xmm0, xmm1)
        val bytes = a.toByteArray()
        // F3 0F 58 /r
        assertEquals(0xF3.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x58.toByte(), bytes[2])
    }

    @Test
    fun subsdXmm() {
        val a = asm()
        a.subsd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF2.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x5C.toByte(), bytes[2])
    }

    @Test
    fun mulsdXmm() {
        val a = asm()
        a.mulsd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF2.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x59.toByte(), bytes[2])
    }

    @Test
    fun divsdXmm() {
        val a = asm()
        a.divsd(xmm0, xmm1)
        val bytes = a.toByteArray()
        assertEquals(0xF2.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x5E.toByte(), bytes[2])
    }

    @Test
    fun addpdXmm() {
        val a = asm()
        a.addpd(xmm0, xmm1)
        val bytes = a.toByteArray()
        // 66 0F 58 /r
        assertEquals(0x66.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x58.toByte(), bytes[2])
    }

    @Test
    fun addpsXmm() {
        val a = asm()
        a.addps(xmm0, xmm1)
        val bytes = a.toByteArray()
        // 0F 58 /r (no prefix)
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x58.toByte(), bytes[1])
    }

    // ---- SSE comparison ----

    @Test
    fun ucomisdXmm() {
        val a = asm()
        a.ucomisd(xmm0, xmm1)
        val bytes = a.toByteArray()
        // 66 0F 2E /r
        assertEquals(0x66.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x2E.toByte(), bytes[2])
    }

    // ---- SSE moves ----

    @Test
    fun movapsXmm() {
        val a = asm()
        a.movaps(xmm0, xmm1)
        val bytes = a.toByteArray()
        // 0F 28 /r
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x28.toByte(), bytes[1])
    }

    @Test
    fun movupsXmm() {
        val a = asm()
        a.movups(xmm0, xmm1)
        val bytes = a.toByteArray()
        // 0F 10 /r
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x10.toByte(), bytes[1])
    }

    // ---- SSE XOR (zero register) ----

    @Test
    fun xorpsXmmSelf() {
        val a = asm()
        a.xorps(xmm0, xmm0)
        val bytes = a.toByteArray()
        // 0F 57 /r
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x57.toByte(), bytes[1])
        assertEquals(0xC0.toByte(), bytes[2]) // xmm0 xmm0
    }

    @Test
    fun xorpdXmmSelf() {
        val a = asm()
        a.xorpd(xmm0, xmm0)
        val bytes = a.toByteArray()
        assertEquals(0x66.toByte(), bytes[0])
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x57.toByte(), bytes[2])
    }

    // ---- VEX instructions ----

    @Test
    fun vaddpsYmm() {
        val a = asm()
        a.vaddps(ymm0, ymm1, ymm2)
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
        // Should start with VEX prefix (0xC5 or 0xC4)
        assertTrue(bytes[0] == 0xC5.toByte() || bytes[0] == 0xC4.toByte(),
            "VEX prefix expected: ${bytes.map { "0x%02X".format(it) }}")
    }

    @Test
    fun vsubpdXmm() {
        val a = asm()
        a.vsubpd(xmm0, xmm1, xmm2)
        val bytes = a.toByteArray()
        assertTrue(bytes[0] == 0xC5.toByte() || bytes[0] == 0xC4.toByte())
    }

    // ---- BT / BTS / BTR / BTC ----

    @Test
    fun btR32Imm() {
        val a = asm()
        a.bt(eax as X86Operand32, 5.toByte())
        val bytes = a.toByteArray()
        // 0F BA /4 ib
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xBA.toByte(), bytes[1])
    }

    @Test
    fun btsR32Imm() {
        val a = asm()
        a.bts(eax as X86Operand32, 3.toByte())
        val bytes = a.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xBA.toByte(), bytes[1])
    }

    // ---- CMPXCHG ----

    @Test
    fun cmpxchgR32R32() {
        val a = asm()
        a.cmpxchg(eax as X86Operand32, ecx)
        val bytes = a.toByteArray()
        // 0F B1 /r
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xB1.toByte(), bytes[1])
    }

    // ---- XADD ----

    @Test
    fun xaddR32R32() {
        val a = asm()
        a.xadd(eax as X86Operand32, ecx)
        val bytes = a.toByteArray()
        // 0F C1 /r
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0xC1.toByte(), bytes[1])
    }

    // ---- Multiple instructions assembled sequentially ----

    @Test
    fun functionProlog() {
        val a = asm()
        a.push(rbp)
        a.mov(rbp, rsp as X86Operand64)
        a.sub(rsp, 32)
        val bytes = a.toByteArray()
        assertEquals(0x55.toByte(), bytes[0]) // push rbp
        assertTrue(bytes.size >= 8) // at least push + mov + sub
    }

    @Test
    fun functionEpilog() {
        val a = asm()
        a.mov(rsp, rbp as X86Operand64)
        a.pop(rbp)
        a.ret()
        val bytes = a.toByteArray()
        assertTrue(bytes.size >= 5)
        assertEquals(0xC3.toByte(), bytes.last()) // ret
    }

    // ---- RESET ----

    @Test
    fun resetClearsBuffer() {
        val a = asm()
        a.nop()
        a.nop()
        assertEquals(2, a.position())
        a.reset()
        assertEquals(0, a.position())
        a.ret()
        assertArrayEquals(byteArrayOf(0xC3.toByte()), a.toByteArray())
    }

    // ---- Extended registers throughout ----

    @Test
    fun movExtendedRegToReg() {
        val a = asm()
        a.mov(r8, r15 as X86Operand64)
        val bytes = a.toByteArray()
        // REX prefix with W+R+B bits
        assertEquals(0x4D.toByte(), bytes[0])
    }

    @Test
    fun addExtendedRegs() {
        val a = asm()
        a.add(r10, r11 as X86Operand64)
        val bytes = a.toByteArray()
        assertEquals(0x4D.toByte(), bytes[0]) // REX.WRB
    }

    @Test
    fun pushPopAllExtended() {
        val a = asm()
        val extRegs = listOf(r8, r9, r10, r11, r12, r13, r14, r15)
        for (r in extRegs) {
            a.push(r)
        }
        for (r in extRegs.reversed()) {
            a.pop(r)
        }
        val bytes = a.toByteArray()
        // 8 pushes + 8 pops, each 2 bytes (REX + opcode)
        assertEquals(32, bytes.size)
    }

    // ---- PUSHFQ ----

    @Test
    fun pushfqEncoding() {
        val a = asm()
        a.pushfq()
        assertArrayEquals(byteArrayOf(0x9C.toByte()), a.toByteArray())
    }

    // ---- VZEROALL / VZEROUPPER ----

    @Test
    fun vzeroallEncoding() {
        val a = asm()
        a.vzeroall()
        val bytes = a.toByteArray()
        // VEX encoded: C5 FC 77
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun vzeroupperEncoding() {
        val a = asm()
        a.vzeroupper()
        val bytes = a.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }
}
