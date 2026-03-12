package org.kgen.target.x86.asm

import org.kgen.target.x86.*
import org.kgen.target.x86.disasm.X86Disassembler
import org.kgen.target.x86.disasm.X86Instruction

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class X86AssemblerRoundTripComprehensiveTest {

    private val disasm = X86Disassembler()

    private val al = X86Register.AL as X86Register8
    private val cl = X86Register.CL as X86Register8
    private val dl = X86Register.DL as X86Register8
    private val bl = X86Register.BL as X86Register8

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
    private val xmm4 = X86Register.XMM4 as X86Xmm
    private val xmm5 = X86Register.XMM5 as X86Xmm
    private val xmm6 = X86Register.XMM6 as X86Xmm
    private val xmm7 = X86Register.XMM7 as X86Xmm

    private val ymm0 = X86Register.YMM0 as X86Ymm
    private val ymm1 = X86Register.YMM1 as X86Ymm
    private val ymm2 = X86Register.YMM2 as X86Ymm

    private fun roundTrip(block: X86Assembler.() -> Unit): List<X86Instruction> {
        val asm = X86Assembler()
        asm.block()
        val bytes = asm.toByteArray()
        return disasm.disassembleRaw(bytes)
    }

    private fun roundTripOne(block: X86Assembler.() -> Unit): X86Instruction {
        val insts = roundTrip(block)
        assertEquals(1, insts.size, "Expected exactly 1 instruction, got ${insts.size}: ${insts.map { it.text() }}")
        return insts[0]
    }

    private fun assertMnemonic(expected: String, block: X86Assembler.() -> Unit) {
        val inst = roundTripOne(block)
        assertEquals(expected, inst.mnemonic)
    }

    // SETcc - all 16 conditions

    @Test fun `seto r8`() = assertMnemonic("seto") { seto(al as X86Operand8) }

    @Test fun `setno r8`() = assertMnemonic("setno") { setno(al as X86Operand8) }

    @Test fun `setb r8`() = assertMnemonic("setb") { setb(al as X86Operand8) }

    @Test fun `setae r8`() {
        val inst = roundTripOne { setae(al as X86Operand8) }
        assertEquals("setnb", inst.mnemonic)
    }

    @Test fun `setbe r8`() = assertMnemonic("setbe") { setbe(al as X86Operand8) }

    @Test fun `seta r8`() {
        val inst = roundTripOne { seta(al as X86Operand8) }
        assertEquals("seta", inst.mnemonic)
    }

    @Test fun `sete r8`() {
        val inst = roundTripOne { sete(al as X86Operand8) }
        assertEquals("setz", inst.mnemonic)
    }

    @Test fun `setne r8`() {
        val inst = roundTripOne { setne(al as X86Operand8) }
        assertEquals("setnz", inst.mnemonic)
    }

    @Test fun `sets r8`() = assertMnemonic("sets") { sets(al as X86Operand8) }

    @Test fun `setns r8`() = assertMnemonic("setns") { setns(al as X86Operand8) }

    @Test fun `setp r8`() = assertMnemonic("setp") { setp(al as X86Operand8) }

    @Test fun `setnp r8`() = assertMnemonic("setnp") { setnp(al as X86Operand8) }

    @Test fun `setl r8`() = assertMnemonic("setl") { setl(al as X86Operand8) }

    @Test fun `setge r8`() {
        val inst = roundTripOne { setge(al as X86Operand8) }
        assertEquals("setnl", inst.mnemonic)
    }

    @Test fun `setle r8`() = assertMnemonic("setle") { setle(al as X86Operand8) }

    @Test fun `setg r8`() = assertMnemonic("setg") { setg(al as X86Operand8) }

    @Test fun `sete on cl`() {
        val inst = roundTripOne { sete(cl as X86Operand8) }
        assertEquals("setz", inst.mnemonic)
        assertEquals("cl", inst.operands[0].text())
    }

    @Test fun `setne on dl`() {
        val inst = roundTripOne { setne(dl as X86Operand8) }
        assertEquals("setnz", inst.mnemonic)
        assertEquals("dl", inst.operands[0].text())
    }

    @Test fun `setb on bl`() {
        val inst = roundTripOne { setb(bl as X86Operand8) }
        assertEquals("setb", inst.mnemonic)
        assertEquals("bl", inst.operands[0].text())
    }

    // CMOVcc - remaining conditions not covered by extended test

    @Test fun `cmova r64 r64`() {
        val inst = roundTripOne { cmova(rax, rcx as X86Operand64) }
        assertEquals("cmova", inst.mnemonic)
    }

    @Test fun `cmovbe r64 r64`() = assertMnemonic("cmovbe") { cmovbe(rax, rcx as X86Operand64) }

    @Test fun `cmovo r64 r64`() {
        val inst = roundTripOne { cmovo(rax, rcx as X86Operand64) }
        assertEquals("cmovo", inst.mnemonic)
    }

    @Test fun `cmovno r64 r64`() = assertMnemonic("cmovno") { cmovno(rax, rcx as X86Operand64) }

    @Test fun `cmovs r64 r64`() = assertMnemonic("cmovs") { cmovs(rax, rcx as X86Operand64) }

    @Test fun `cmovns r64 r64`() = assertMnemonic("cmovns") { cmovns(rax, rcx as X86Operand64) }

    @Test fun `cmovp r64 r64`() = assertMnemonic("cmovp") { cmovp(rax, rcx as X86Operand64) }

    @Test fun `cmovnp r64 r64`() = assertMnemonic("cmovnp") { cmovnp(rax, rcx as X86Operand64) }

    @Test fun `cmovl r64 r64`() = assertMnemonic("cmovl") { cmovl(rax, rcx as X86Operand64) }

    @Test fun `cmovge r64 r64`() {
        val inst = roundTripOne { cmovge(rax, rcx as X86Operand64) }
        assertEquals("cmovnl", inst.mnemonic)
    }

    @Test fun `cmova r32 r32`() {
        val inst = roundTripOne { cmova(eax, ecx as X86Operand32) }
        assertEquals("cmova", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test fun `cmovb r32 r32`() {
        val inst = roundTripOne { cmovb(eax, ecx as X86Operand32) }
        assertEquals("cmovb", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test fun `cmove r32 r32`() {
        val inst = roundTripOne { cmove(eax, ecx as X86Operand32) }
        assertEquals("cmovz", inst.mnemonic)
    }

    @Test fun `cmovne r32 r32`() {
        val inst = roundTripOne { cmovne(eax, ecx as X86Operand32) }
        assertEquals("cmovnz", inst.mnemonic)
    }

    @Test fun `cmovg r32 r32`() {
        val inst = roundTripOne { cmovg(eax, ecx as X86Operand32) }
        assertEquals("cmovg", inst.mnemonic)
    }

    @Test fun `cmovle r32 r32`() {
        val inst = roundTripOne { cmovle(eax, ecx as X86Operand32) }
        assertEquals("cmovle", inst.mnemonic)
    }

    // SSE scalar double precision

    @Test fun `addsd xmm2 xmm3`() {
        val inst = roundTripOne { addsd(xmm2, xmm3) }
        assertEquals("addsd", inst.mnemonic)
        assertEquals("xmm2", inst.operands[0].text())
        assertEquals("xmm3", inst.operands[1].text())
    }

    @Test fun `subsd xmm4 xmm5`() {
        val inst = roundTripOne { subsd(xmm4, xmm5) }
        assertEquals("subsd", inst.mnemonic)
        assertEquals("xmm4", inst.operands[0].text())
        assertEquals("xmm5", inst.operands[1].text())
    }

    @Test fun `mulsd xmm6 xmm7`() {
        val inst = roundTripOne { mulsd(xmm6, xmm7) }
        assertEquals("mulsd", inst.mnemonic)
        assertEquals("xmm6", inst.operands[0].text())
        assertEquals("xmm7", inst.operands[1].text())
    }

    @Test fun `divsd xmm3 xmm4`() {
        val inst = roundTripOne { divsd(xmm3, xmm4) }
        assertEquals("divsd", inst.mnemonic)
        assertEquals("xmm3", inst.operands[0].text())
        assertEquals("xmm4", inst.operands[1].text())
    }

    @Test fun `ucomisd xmm2 xmm5`() {
        val inst = roundTripOne { ucomisd(xmm2, xmm5) }
        assertEquals("ucomisd", inst.mnemonic)
        assertEquals("xmm2", inst.operands[0].text())
        assertEquals("xmm5", inst.operands[1].text())
    }

    // SSE scalar single precision

    @Test fun `addss xmm2 xmm3`() {
        val inst = roundTripOne { addss(xmm2, xmm3) }
        assertEquals("addss", inst.mnemonic)
        assertEquals("xmm2", inst.operands[0].text())
        assertEquals("xmm3", inst.operands[1].text())
    }

    @Test fun `subss xmm4 xmm5`() {
        val inst = roundTripOne { subss(xmm4, xmm5) }
        assertEquals("subss", inst.mnemonic)
    }

    @Test fun `mulss xmm6 xmm7`() {
        val inst = roundTripOne { mulss(xmm6, xmm7) }
        assertEquals("mulss", inst.mnemonic)
    }

    @Test fun `divss xmm3 xmm4`() {
        val inst = roundTripOne { divss(xmm3, xmm4) }
        assertEquals("divss", inst.mnemonic)
    }

    // SSE moves with different register combinations

    @Test fun `movsd xmm3 xmm4`() {
        val inst = roundTripOne { movsd(xmm3, xmm4) }
        assertEquals("movsd", inst.mnemonic)
        assertEquals("xmm3", inst.operands[0].text())
        assertEquals("xmm4", inst.operands[1].text())
    }

    @Test fun `movss xmm5 xmm6`() {
        val inst = roundTripOne { movss(xmm5, xmm6) }
        assertEquals("movss", inst.mnemonic)
        assertEquals("xmm5", inst.operands[0].text())
        assertEquals("xmm6", inst.operands[1].text())
    }

    // SSE conversion instructions

    @Test fun `cvtsi2sd xmm0 eax`() {
        val inst = roundTripOne { cvtsi2sd(xmm0, eax as X86Operand32) }
        assertEquals("cvtsi2sd", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("eax", inst.operands[1].text())
    }

    @Test fun `cvtsi2sd xmm0 rax`() {
        val inst = roundTripOne { cvtsi2sd(xmm0, rax as X86Operand64) }
        assertEquals("cvtsi2sd", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("rax", inst.operands[1].text())
    }

    @Test fun `cvtsi2ss xmm0 eax`() {
        val inst = roundTripOne { cvtsi2ss(xmm0, eax as X86Operand32) }
        assertEquals("cvtsi2ss", inst.mnemonic)
    }

    @Test fun `cvtsi2ss xmm0 rax`() {
        val inst = roundTripOne { cvtsi2ss(xmm0, rax as X86Operand64) }
        assertEquals("cvtsi2ss", inst.mnemonic)
    }

    @Test fun `cvtsd2si eax xmm0`() {
        val inst = roundTripOne { cvtsd2si(eax, xmm0) }
        assertEquals("cvtsd2si", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("xmm0", inst.operands[1].text())
    }

    @Test fun `cvtsd2si rax xmm0`() {
        val inst = roundTripOne { cvtsd2si(rax, xmm0) }
        assertEquals("cvtsd2si", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    @Test fun `cvtss2si eax xmm0`() {
        val inst = roundTripOne { cvtss2si(eax, xmm0) }
        assertEquals("cvtss2si", inst.mnemonic)
    }

    @Test fun `cvtss2si rax xmm0`() {
        val inst = roundTripOne { cvtss2si(rax, xmm0) }
        assertEquals("cvtss2si", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    @Test fun `cvttsd2si eax xmm0`() {
        val inst = roundTripOne { cvttsd2si(eax, xmm0) }
        assertEquals("cvttsd2si", inst.mnemonic)
    }

    @Test fun `cvttsd2si rax xmm0`() {
        val inst = roundTripOne { cvttsd2si(rax, xmm0) }
        assertEquals("cvttsd2si", inst.mnemonic)
    }

    @Test fun `cvtsd2ss xmm2 xmm3`() {
        val inst = roundTripOne { cvtsd2ss(xmm2, xmm3) }
        assertEquals("cvtsd2ss", inst.mnemonic)
    }

    @Test fun `cvtss2sd xmm2 xmm3`() {
        val inst = roundTripOne { cvtss2sd(xmm2, xmm3) }
        assertEquals("cvtss2sd", inst.mnemonic)
    }

    @Test fun `cvtsi2sd xmm2 ecx`() {
        val inst = roundTripOne { cvtsi2sd(xmm2, ecx as X86Operand32) }
        assertEquals("cvtsi2sd", inst.mnemonic)
        assertEquals("xmm2", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test fun `cvtsi2ss xmm3 edx`() {
        val inst = roundTripOne { cvtsi2ss(xmm3, edx as X86Operand32) }
        assertEquals("cvtsi2ss", inst.mnemonic)
    }

    // SSE2 packed integer pxor

    @Test fun `pxor xmm0 xmm0 self clear`() {
        val inst = roundTripOne { pxor(xmm0, xmm0) }
        assertEquals("pxor", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("xmm0", inst.operands[1].text())
    }

    @Test fun `pxor xmm1 xmm2`() {
        val inst = roundTripOne { pxor(xmm1, xmm2) }
        assertEquals("pxor", inst.mnemonic)
        assertEquals("xmm1", inst.operands[0].text())
        assertEquals("xmm2", inst.operands[1].text())
    }

    // MOVD/MOVQ

    @Test fun `movd xmm0 eax`() {
        val inst = roundTripOne { movd(xmm0, eax as X86Operand32) }
        assertEquals("movd", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("eax", inst.operands[1].text())
    }

    @Test fun `movq xmm0 rax`() {
        val inst = roundTripOne { movq(xmm0, rax as X86Operand64) }
        assertEquals("movq", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("rax", inst.operands[1].text())
    }

    @Test fun `movd xmm2 ecx`() {
        val inst = roundTripOne { movd(xmm2, ecx as X86Operand32) }
        assertEquals("movd", inst.mnemonic)
        assertEquals("xmm2", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test fun `movq xmm3 rcx`() {
        val inst = roundTripOne { movq(xmm3, rcx as X86Operand64) }
        assertEquals("movq", inst.mnemonic)
        assertEquals("xmm3", inst.operands[0].text())
        assertEquals("rcx", inst.operands[1].text())
    }

    // AVX (VEX-encoded) instructions

    @Test fun `vaddps xmm0 xmm1 xmm2`() {
        val inst = roundTripOne { vaddps(xmm0, xmm1, xmm2) }
        assertEquals("vaddps", inst.mnemonic)
    }

    @Test fun `vaddps ymm0 ymm1 ymm2`() {
        val inst = roundTripOne { vaddps(ymm0, ymm1, ymm2) }
        assertEquals("vaddps", inst.mnemonic)
    }

    @Test fun `vsubps xmm0 xmm1 xmm2`() {
        val inst = roundTripOne { vsubps(xmm0, xmm1, xmm2) }
        assertEquals("vsubps", inst.mnemonic)
    }

    @Test fun `vsubps ymm0 ymm1 ymm2`() {
        val inst = roundTripOne { vsubps(ymm0, ymm1, ymm2) }
        assertEquals("vsubps", inst.mnemonic)
    }

    @Test fun `vmulps xmm0 xmm1 xmm2`() {
        val inst = roundTripOne { vmulps(xmm0, xmm1, xmm2) }
        assertEquals("vmulps", inst.mnemonic)
    }

    @Test fun `vmulps ymm0 ymm1 ymm2`() {
        val inst = roundTripOne { vmulps(ymm0, ymm1, ymm2) }
        assertEquals("vmulps", inst.mnemonic)
    }

    @Test fun `vdivps xmm0 xmm1 xmm2`() {
        val inst = roundTripOne { vdivps(xmm0, xmm1, xmm2) }
        assertEquals("vdivps", inst.mnemonic)
    }

    @Test fun `vdivps ymm0 ymm1 ymm2`() {
        val inst = roundTripOne { vdivps(ymm0, ymm1, ymm2) }
        assertEquals("vdivps", inst.mnemonic)
    }

    @Test fun `vaddpd xmm0 xmm1 xmm2`() {
        val inst = roundTripOne { vaddpd(xmm0, xmm1, xmm2) }
        assertEquals("vaddpd", inst.mnemonic)
    }

    @Test fun `vaddpd ymm0 ymm1 ymm2`() {
        val inst = roundTripOne { vaddpd(ymm0, ymm1, ymm2) }
        assertEquals("vaddpd", inst.mnemonic)
    }

    @Test fun `vsubpd xmm0 xmm1 xmm2`() {
        val inst = roundTripOne { vsubpd(xmm0, xmm1, xmm2) }
        assertEquals("vsubpd", inst.mnemonic)
    }

    @Test fun `vsubpd ymm0 ymm1 ymm2`() {
        val inst = roundTripOne { vsubpd(ymm0, ymm1, ymm2) }
        assertEquals("vsubpd", inst.mnemonic)
    }

    @Test fun `vmulpd xmm0 xmm1 xmm2`() {
        val inst = roundTripOne { vmulpd(xmm0, xmm1, xmm2) }
        assertEquals("vmulpd", inst.mnemonic)
    }

    @Test fun `vmulpd ymm0 ymm1 ymm2`() {
        val inst = roundTripOne { vmulpd(ymm0, ymm1, ymm2) }
        assertEquals("vmulpd", inst.mnemonic)
    }

    @Test fun `vdivpd xmm0 xmm1 xmm2`() {
        val inst = roundTripOne { vdivpd(xmm0, xmm1, xmm2) }
        assertEquals("vdivpd", inst.mnemonic)
    }

    @Test fun `vdivpd ymm0 ymm1 ymm2`() {
        val inst = roundTripOne { vdivpd(ymm0, ymm1, ymm2) }
        assertEquals("vdivpd", inst.mnemonic)
    }

    // BMI1 instructions

    @Test fun `andn r32`() {
        val inst = roundTripOne { andn(eax, ecx, edx as X86Operand32) }
        assertEquals("andn", inst.mnemonic)
    }

    @Test fun `andn r64`() {
        val inst = roundTripOne { andn(rax, rcx, rdx as X86Operand64) }
        assertEquals("andn", inst.mnemonic)
    }

    @Test fun `bextr r32`() {
        val inst = roundTripOne { bextr(eax, ecx as X86Operand32, edx) }
        assertEquals("bextr", inst.mnemonic)
    }

    @Test fun `bextr r64`() {
        val inst = roundTripOne { bextr(rax, rcx as X86Operand64, rdx) }
        assertEquals("bextr", inst.mnemonic)
    }

    @Test fun `blsi r32`() {
        val inst = roundTripOne { blsi(eax, ecx as X86Operand32) }
        assertEquals("blsi", inst.mnemonic)
    }

    @Test fun `blsi r64`() {
        val inst = roundTripOne { blsi(rax, rcx as X86Operand64) }
        assertEquals("blsi", inst.mnemonic)
    }

    @Test fun `blsmsk r32`() {
        val inst = roundTripOne { blsmsk(eax, ecx as X86Operand32) }
        assertEquals("blsmsk", inst.mnemonic)
    }

    @Test fun `blsmsk r64`() {
        val inst = roundTripOne { blsmsk(rax, rcx as X86Operand64) }
        assertEquals("blsmsk", inst.mnemonic)
    }

    @Test fun `blsr r32`() {
        val inst = roundTripOne { blsr(eax, ecx as X86Operand32) }
        assertEquals("blsr", inst.mnemonic)
    }

    @Test fun `blsr r64`() {
        val inst = roundTripOne { blsr(rax, rcx as X86Operand64) }
        assertEquals("blsr", inst.mnemonic)
    }

    // BMI2 instructions

    @Test fun `bzhi r32`() {
        val inst = roundTripOne { bzhi(eax, ecx as X86Operand32, edx) }
        assertEquals("bzhi", inst.mnemonic)
    }

    @Test fun `bzhi r64`() {
        val inst = roundTripOne { bzhi(rax, rcx as X86Operand64, rdx) }
        assertEquals("bzhi", inst.mnemonic)
    }

    @Test fun `mulx r32`() {
        val inst = roundTripOne { mulx(eax, ecx, edx as X86Operand32) }
        assertEquals("mulx", inst.mnemonic)
    }

    @Test fun `mulx r64`() {
        val inst = roundTripOne { mulx(rax, rcx, rdx as X86Operand64) }
        assertEquals("mulx", inst.mnemonic)
    }

    @Test fun `pdep r32`() {
        val inst = roundTripOne { pdep(eax, ecx, edx as X86Operand32) }
        assertEquals("pdep", inst.mnemonic)
    }

    @Test fun `pdep r64`() {
        val inst = roundTripOne { pdep(rax, rcx, rdx as X86Operand64) }
        assertEquals("pdep", inst.mnemonic)
    }

    @Test fun `pext r32`() {
        val inst = roundTripOne { pext(eax, ecx, edx as X86Operand32) }
        assertEquals("pext", inst.mnemonic)
    }

    @Test fun `pext r64`() {
        val inst = roundTripOne { pext(rax, rcx, rdx as X86Operand64) }
        assertEquals("pext", inst.mnemonic)
    }

    @Test fun `rorx r32`() {
        val inst = roundTripOne { rorx(eax, ecx as X86Operand32, 7) }
        assertEquals("rorx", inst.mnemonic)
    }

    @Test fun `rorx r64`() {
        val inst = roundTripOne { rorx(rax, rcx as X86Operand64, 13) }
        assertEquals("rorx", inst.mnemonic)
    }

    @Test fun `sarx r32`() {
        val inst = roundTripOne { sarx(eax, ecx as X86Operand32, edx) }
        assertEquals("sarx", inst.mnemonic)
    }

    @Test fun `sarx r64`() {
        val inst = roundTripOne { sarx(rax, rcx as X86Operand64, rdx) }
        assertEquals("sarx", inst.mnemonic)
    }

    @Test fun `shrx r32`() {
        val inst = roundTripOne { shrx(eax, ecx as X86Operand32, edx) }
        assertEquals("shrx", inst.mnemonic)
    }

    @Test fun `shrx r64`() {
        val inst = roundTripOne { shrx(rax, rcx as X86Operand64, rdx) }
        assertEquals("shrx", inst.mnemonic)
    }

    @Test fun `shlx r32`() {
        val inst = roundTripOne { shlx(eax, ecx as X86Operand32, edx) }
        assertEquals("shlx", inst.mnemonic)
    }

    @Test fun `shlx r64`() {
        val inst = roundTripOne { shlx(rax, rcx as X86Operand64, rdx) }
        assertEquals("shlx", inst.mnemonic)
    }

    // Rotate instructions

    @Test fun `rol r32 imm`() = assertMnemonic("rol") { rol(eax as X86Operand32, 3.toByte()) }

    @Test fun `rol r64 imm`() = assertMnemonic("rol") { rol(rax as X86Operand64, 5.toByte()) }

    @Test fun `ror r32 imm`() = assertMnemonic("ror") { ror(eax as X86Operand32, 4.toByte()) }

    @Test fun `ror r64 imm`() = assertMnemonic("ror") { ror(rax as X86Operand64, 7.toByte()) }

    @Test fun `rcl r32 imm`() = assertMnemonic("rcl") { rcl(eax as X86Operand32, 2.toByte()) }

    @Test fun `rcl r64 imm`() = assertMnemonic("rcl") { rcl(rax as X86Operand64, 1.toByte()) }

    @Test fun `rcr r32 imm`() = assertMnemonic("rcr") { rcr(eax as X86Operand32, 3.toByte()) }

    @Test fun `rcr r64 imm`() = assertMnemonic("rcr") { rcr(rax as X86Operand64, 2.toByte()) }

    @Test fun `rol r64 by 1`() = assertMnemonic("rol") { rol(rax as X86Operand64) }

    @Test fun `ror r64 by 1`() = assertMnemonic("ror") { ror(rax as X86Operand64) }

    @Test fun `rol r64 by cl`() = assertMnemonic("rol") { rol(rax as X86Operand64, cl) }

    @Test fun `ror r64 by cl`() = assertMnemonic("ror") { ror(rax as X86Operand64, cl) }

    // Shift instructions with CL

    @Test fun `shl r64 by cl`() {
        val inst = roundTripOne { shl(rax as X86Operand64, cl) }
        assertEquals("shl", inst.mnemonic)
    }

    @Test fun `shr r64 by cl`() {
        val inst = roundTripOne { shr(rax as X86Operand64, cl) }
        assertEquals("shr", inst.mnemonic)
    }

    @Test fun `sar r64 by cl`() {
        val inst = roundTripOne { sar(rax as X86Operand64, cl) }
        assertEquals("sar", inst.mnemonic)
    }

    @Test fun `shl r32 by imm`() = assertMnemonic("shl") { shl(eax as X86Operand32, 8.toByte()) }

    @Test fun `shr r32 by imm`() = assertMnemonic("shr") { shr(eax as X86Operand32, 4.toByte()) }

    @Test fun `sar r32 by imm`() = assertMnemonic("sar") { sar(eax as X86Operand32, 2.toByte()) }

    // String operations

    @Test fun `movsb`() = assertMnemonic("movsb") { movsb() }

    @Test fun `movsq`() {
        val inst = roundTripOne { movsq() }
        assertEquals("movsq", inst.mnemonic)
    }

    @Test fun `stosb`() = assertMnemonic("stosb") { stosb() }

    @Test fun `stosd`() = assertMnemonic("stosd") { stosd() }

    @Test fun `stosq`() = assertMnemonic("stosq") { stosq() }

    @Test fun `rep movsb`() {
        val inst = roundTripOne { rep(); movsb() }
        assertEquals("rep movsb", inst.mnemonic)
    }

    @Test fun `rep stosb`() {
        val inst = roundTripOne { rep(); stosb() }
        assertEquals("rep stosb", inst.mnemonic)
    }

    @Test fun `rep movsq`() {
        val inst = roundTripOne { rep(); movsq() }
        assertEquals("rep movsq", inst.mnemonic)
    }

    @Test fun `rep stosq`() {
        val inst = roundTripOne { rep(); stosq() }
        assertEquals("rep stosq", inst.mnemonic)
    }

    // Conversion instructions

    @Test fun `cdq`() = assertMnemonic("cdq") { cdq() }

    @Test fun `cqo`() = assertMnemonic("cqo") { cqo() }

    @Test fun `leave`() = assertMnemonic("leave") { leave() }

    @Test fun `int3`() = assertMnemonic("int3") { int3() }

    // MOVZX/MOVSX with extended registers

    @Test fun `movzx r64 r8`() {
        val inst = roundTripOne { movzx(rax, al as X86Operand8) }
        assertEquals("movzx", inst.mnemonic)
    }

    @Test fun `movsx r64 r8`() {
        val inst = roundTripOne { movsx(rax, al as X86Operand8) }
        assertEquals("movsx", inst.mnemonic)
    }

    // XCHG

    @Test fun `xchg eax ecx`() {
        val inst = roundTripOne { xchg(ecx, eax) }
        assertEquals("xchg", inst.mnemonic)
    }

    @Test fun `xchg rax rcx`() {
        val inst = roundTripOne { xchg(rcx, rax) }
        assertEquals("xchg", inst.mnemonic)
    }

    // Addressing modes

    @Test fun `mov r64 mem base only`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rcx).offset(0) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[1].text().contains("rcx"))
    }

    @Test fun `mov r64 mem base disp8`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rbp).offset(16) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[1].text().contains("rbp"))
    }

    @Test fun `mov r64 mem base disp8 negative`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rbp).offset(-8) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[1].text().contains("rbp"))
    }

    @Test fun `mov r64 mem base disp32`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rbp).offset(0x1000) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
    }

    @Test fun `mov r64 mem base index scale 1`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rbx).index(rcx, 1).offset(0) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
    }

    @Test fun `mov r64 mem base index scale 2`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rbx).index(rcx, 2).offset(0) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
    }

    @Test fun `mov r64 mem base index scale 4`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rbx).index(rcx, 4).offset(0) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
    }

    @Test fun `mov r64 mem base index scale 8`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rbx).index(rcx, 8).offset(0) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
    }

    @Test fun `mov r64 mem base index scale 4 with disp`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rbp).index(rcx, 4).offset(16) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
    }

    @Test fun `lea r64 base index scale 8 disp`() {
        val inst = roundTripOne { lea(rax, X86Memory.base(rbx).index(rcx, 8).offset(0x100)) }
        assertEquals("lea", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    @Test fun `mov r64 mem rsp base`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rsp).offset(0) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[1].text().contains("rsp"))
    }

    @Test fun `mov r64 mem rsp base disp8`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rsp).offset(8) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
    }

    @Test fun `mov r64 mem r12 base`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(r12).offset(0) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[1].text().contains("r12"))
    }

    @Test fun `mov r64 mem r13 base disp8`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(r13).offset(8) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[1].text().contains("r13"))
    }

    @Test fun `mov r64 rip relative`() {
        val insts = roundTrip {
            label("mydata")
            mov(rax, X86Memory.ripRelative("mydata") as X86Operand64)
        }
        assertEquals(1, insts.size)
        assertEquals("mov", insts[0].mnemonic)
    }

    @Test fun `mov mem r64 base index scale`() {
        val inst = roundTripOne { mov(X86Memory.base(rbx).index(rcx, 4).offset(0), rax) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("rax", inst.operands[1].text())
    }

    @Test fun `add r64 mem base disp`() {
        val inst = roundTripOne { add(rax, X86Memory.base(rbp).offset(-16) as X86Operand64) }
        assertEquals("add", inst.mnemonic)
    }

    @Test fun `sub r64 mem base disp`() {
        val inst = roundTripOne { sub(rax, X86Memory.base(rbp).offset(-24) as X86Operand64) }
        assertEquals("sub", inst.mnemonic)
    }

    @Test fun `cmp r64 mem base disp`() {
        val inst = roundTripOne { cmp(rax, X86Memory.base(rbp).offset(-8) as X86Operand64) }
        assertEquals("cmp", inst.mnemonic)
    }

    @Test fun `lea r64 base only`() {
        val inst = roundTripOne { lea(rax, X86Memory.base(rcx).offset(0)) }
        assertEquals("lea", inst.mnemonic)
    }

    @Test fun `lea r64 disp8`() {
        val inst = roundTripOne { lea(rax, X86Memory.base(rbp).offset(64)) }
        assertEquals("lea", inst.mnemonic)
    }

    @Test fun `lea r64 disp32`() {
        val inst = roundTripOne { lea(rax, X86Memory.base(rbp).offset(0x80000)) }
        assertEquals("lea", inst.mnemonic)
    }

    @Test fun `lea r32 base index`() {
        val inst = roundTripOne { lea(eax, X86Memory.base(rbx).index(rcx, 4).offset(0)) }
        assertEquals("lea", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    // Extended registers in addressing

    @Test fun `mov r64 mem r8 base disp`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(r8).offset(16) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[1].text().contains("r8"))
    }

    @Test fun `mov r64 mem r14 base disp`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(r14).offset(-32) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[1].text().contains("r14"))
    }

    @Test fun `mov r64 mem base r9 index`() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rbx).index(r9, 4).offset(0) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
    }

    @Test fun `mov mem r8 r64 store`() {
        val inst = roundTripOne { mov(X86Memory.base(r8).offset(0), rax) }
        assertEquals("mov", inst.mnemonic)
    }

    // MUL (unsigned)

    @Test fun `mul r32`() {
        val inst = roundTripOne { mul(eax as X86Operand32) }
        assertEquals("mul", inst.mnemonic)
    }

    @Test fun `mul r64`() {
        val inst = roundTripOne { mul(rax as X86Operand64) }
        assertEquals("mul", inst.mnemonic)
    }

    // IMUL single operand

    @Test fun `imul single operand r64`() {
        val inst = roundTripOne { imul(rcx as X86Operand64) }
        assertEquals("imul", inst.mnemonic)
    }

    // DIV/IDIV with extended registers

    @Test fun `div r32`() {
        val inst = roundTripOne { div(ecx as X86Operand32) }
        assertEquals("div", inst.mnemonic)
        assertEquals("ecx", inst.operands[0].text())
    }

    @Test fun `idiv r32`() {
        val inst = roundTripOne { idiv(ecx as X86Operand32) }
        assertEquals("idiv", inst.mnemonic)
        assertEquals("ecx", inst.operands[0].text())
    }

    @Test fun `div r8`() {
        val inst = roundTripOne { div(al as X86Operand8) }
        assertEquals("div", inst.mnemonic)
    }

    // NEG/NOT with extended and 32-bit registers

    @Test fun `neg r32`() {
        val inst = roundTripOne { neg(eax as X86Operand32) }
        assertEquals("neg", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test fun `not r32`() {
        val inst = roundTripOne { not_(eax as X86Operand32) }
        assertEquals("not", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test fun `neg r64 extended`() {
        val inst = roundTripOne { neg(r12 as X86Operand64) }
        assertEquals("neg", inst.mnemonic)
        assertEquals("r12", inst.operands[0].text())
    }

    @Test fun `not r64 extended`() {
        val inst = roundTripOne { not_(r13 as X86Operand64) }
        assertEquals("not", inst.mnemonic)
        assertEquals("r13", inst.operands[0].text())
    }

    // INC/DEC with 32-bit registers

    @Test fun `inc r32`() {
        val inst = roundTripOne { inc(eax as X86Operand32) }
        assertEquals("inc", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test fun `dec r32`() {
        val inst = roundTripOne { dec(eax as X86Operand32) }
        assertEquals("dec", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    // AND/OR/XOR with immediates

    @Test fun `and r64 imm`() {
        val inst = roundTripOne { and_(rax as X86Operand64, 0xFF) }
        assertEquals("and", inst.mnemonic)
    }

    @Test fun `or r64 imm`() {
        val inst = roundTripOne { or_(rax as X86Operand64, 0x80) }
        assertEquals("or", inst.mnemonic)
    }

    @Test fun `xor r64 imm`() {
        val inst = roundTripOne { xor_(rax as X86Operand64, 0x55) }
        assertEquals("xor", inst.mnemonic)
    }

    @Test fun `and r32 imm`() {
        val inst = roundTripOne { and_(eax as X86Operand32, 0xFF) }
        assertEquals("and", inst.mnemonic)
    }

    @Test fun `or r32 imm`() {
        val inst = roundTripOne { or_(eax as X86Operand32, 0x80) }
        assertEquals("or", inst.mnemonic)
    }

    @Test fun `xor r32 imm`() {
        val inst = roundTripOne { xor_(eax as X86Operand32, 0x55) }
        assertEquals("xor", inst.mnemonic)
    }

    // ADD/SUB/CMP with 32-bit registers and immediates

    @Test fun `add r32 r32 extended`() {
        val inst = roundTripOne { add(r8d, r9d as X86Operand32) }
        assertEquals("add", inst.mnemonic)
        assertEquals("r8d", inst.operands[0].text())
        assertEquals("r9d", inst.operands[1].text())
    }

    @Test fun `cmp r32 imm`() {
        val inst = roundTripOne { cmp(eax, 100) }
        assertEquals("cmp", inst.mnemonic)
    }

    @Test fun `cmp r32 r32`() {
        val inst = roundTripOne { cmp(eax, ecx as X86Operand32) }
        assertEquals("cmp", inst.mnemonic)
    }

    @Test fun `sub r32 imm`() {
        val inst = roundTripOne { sub(esp as X86Operand32, 64) }
        assertEquals("sub", inst.mnemonic)
    }

    // BSF/BSR with 32-bit operands

    @Test fun `bsf r32 r32`() {
        val inst = roundTripOne { bsf(eax, ecx as X86Operand32) }
        assertEquals("bsf", inst.mnemonic)
    }

    @Test fun `bsr r32 r32`() {
        val inst = roundTripOne { bsr(eax, ecx as X86Operand32) }
        assertEquals("bsr", inst.mnemonic)
    }

    // BSWAP with extended registers

    @Test fun `bswap r64 extended`() {
        val inst = roundTripOne { bswap(r8) }
        assertEquals("bswap", inst.mnemonic)
        assertEquals("r8", inst.operands[0].text())
    }

    @Test fun `bswap r32 extended`() {
        val inst = roundTripOne { bswap(r8d) }
        assertEquals("bswap", inst.mnemonic)
        assertEquals("r8d", inst.operands[0].text())
    }

    // Conditional jump encoding for all conditions

    @Test fun `jo label`() {
        val insts = roundTrip {
            jccLabel(0x00, "target")
            label("target")
            ret()
        }
        assertEquals("jo", insts[0].mnemonic)
    }

    @Test fun `jno label`() {
        val insts = roundTrip {
            jccLabel(0x01, "target")
            label("target")
            ret()
        }
        assertEquals("jno", insts[0].mnemonic)
    }

    @Test fun `jb label`() {
        val insts = roundTrip {
            jccLabel(0x02, "target")
            label("target")
            ret()
        }
        assertEquals("jb", insts[0].mnemonic)
    }

    @Test fun `jnb label`() {
        val insts = roundTrip {
            jccLabel(0x03, "target")
            label("target")
            ret()
        }
        assertEquals("jnb", insts[0].mnemonic)
    }

    @Test fun `jz label`() {
        val insts = roundTrip {
            jccLabel(0x04, "target")
            label("target")
            ret()
        }
        assertEquals("jz", insts[0].mnemonic)
    }

    @Test fun `jnz label`() {
        val insts = roundTrip {
            jccLabel(0x05, "target")
            label("target")
            ret()
        }
        assertEquals("jnz", insts[0].mnemonic)
    }

    @Test fun `jbe label`() {
        val insts = roundTrip {
            jccLabel(0x06, "target")
            label("target")
            ret()
        }
        assertEquals("jbe", insts[0].mnemonic)
    }

    @Test fun `ja label`() {
        val insts = roundTrip {
            jccLabel(0x07, "target")
            label("target")
            ret()
        }
        assertEquals("ja", insts[0].mnemonic)
    }

    @Test fun `js label`() {
        val insts = roundTrip {
            jccLabel(0x08, "target")
            label("target")
            ret()
        }
        assertEquals("js", insts[0].mnemonic)
    }

    @Test fun `jns label`() {
        val insts = roundTrip {
            jccLabel(0x09, "target")
            label("target")
            ret()
        }
        assertEquals("jns", insts[0].mnemonic)
    }

    @Test fun `jp label`() {
        val insts = roundTrip {
            jccLabel(0x0A, "target")
            label("target")
            ret()
        }
        assertEquals("jp", insts[0].mnemonic)
    }

    @Test fun `jnp label`() {
        val insts = roundTrip {
            jccLabel(0x0B, "target")
            label("target")
            ret()
        }
        assertEquals("jnp", insts[0].mnemonic)
    }

    @Test fun `jl label`() {
        val insts = roundTrip {
            jccLabel(0x0C, "target")
            label("target")
            ret()
        }
        assertEquals("jl", insts[0].mnemonic)
    }

    @Test fun `jnl label`() {
        val insts = roundTrip {
            jccLabel(0x0D, "target")
            label("target")
            ret()
        }
        assertEquals("jnl", insts[0].mnemonic)
    }

    @Test fun `jle label`() {
        val insts = roundTrip {
            jccLabel(0x0E, "target")
            label("target")
            ret()
        }
        assertEquals("jle", insts[0].mnemonic)
    }

    @Test fun `jg label`() {
        val insts = roundTrip {
            jccLabel(0x0F, "target")
            label("target")
            ret()
        }
        assertEquals("jg", insts[0].mnemonic)
    }

    // MOV with 8-bit immediates and registers

    @Test fun `mov r8 imm8`() {
        val inst = roundTripOne { mov(al, 42.toByte()) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("al", inst.operands[0].text())
    }

    @Test fun `mov cl imm8`() {
        val inst = roundTripOne { mov(cl, 0x7F.toByte()) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("cl", inst.operands[0].text())
    }

    // MOV r/m, imm32

    @Test fun `mov mem imm32`() {
        val inst = roundTripOne { mov(X86Memory.base(rbp).offset(-8) as X86Operand64, 42) }
        assertEquals("mov", inst.mnemonic)
    }

    @Test fun `mov r64 imm32 sign extended`() {
        val inst = roundTripOne { mov(rax as X86Operand64, 42) }
        assertEquals("mov", inst.mnemonic)
    }

    // SSE conversion sequence

    @Test fun `conversion sequence double to int and back`() {
        val insts = roundTrip {
            cvtsi2sd(xmm0, eax as X86Operand32)
            addsd(xmm0, xmm1)
            cvttsd2si(eax, xmm0)
            ret()
        }
        assertEquals(4, insts.size)
        assertEquals("cvtsi2sd", insts[0].mnemonic)
        assertEquals("addsd", insts[1].mnemonic)
        assertEquals("cvttsd2si", insts[2].mnemonic)
        assertEquals("ret", insts[3].mnemonic)
    }

    @Test fun `conversion sequence float to double`() {
        val insts = roundTrip {
            cvtss2sd(xmm0, xmm1)
            addsd(xmm0, xmm2)
            cvtsd2ss(xmm0, xmm0)
            ret()
        }
        assertEquals(4, insts.size)
        assertEquals("cvtss2sd", insts[0].mnemonic)
        assertEquals("addsd", insts[1].mnemonic)
        assertEquals("cvtsd2ss", insts[2].mnemonic)
    }

    // BMI sequence

    @Test fun `bmi sequence isolate and reset lowest set bit`() {
        val insts = roundTrip {
            blsi(eax, ecx as X86Operand32)
            blsr(edx, ecx as X86Operand32)
            blsmsk(ebx, ecx as X86Operand32)
            ret()
        }
        assertEquals(4, insts.size)
        assertEquals("blsi", insts[0].mnemonic)
        assertEquals("blsr", insts[1].mnemonic)
        assertEquals("blsmsk", insts[2].mnemonic)
    }

    // Complex function with mixed instruction types

    @Test fun `mixed function with sse and gp ops`() {
        val insts = roundTrip {
            push(rbp)
            mov(rbp, rsp as X86Operand64)
            sub(rsp, 32)
            cvtsi2sd(xmm0, eax as X86Operand32)
            addsd(xmm0, xmm1)
            mulsd(xmm0, xmm2)
            cvttsd2si(eax, xmm0)
            mov(rsp, rbp as X86Operand64)
            pop(rbp)
            ret()
        }
        assertEquals(10, insts.size)
        assertEquals("push", insts[0].mnemonic)
        assertEquals("cvtsi2sd", insts[3].mnemonic)
        assertEquals("cvttsd2si", insts[6].mnemonic)
        assertEquals("ret", insts[9].mnemonic)
    }

    // VEX arithmetic sequence

    @Test fun `avx arithmetic sequence`() {
        val insts = roundTrip {
            vaddps(ymm0, ymm1, ymm2)
            vsubps(ymm0, ymm0, ymm1)
            vmulps(ymm0, ymm0, ymm2)
            vdivps(ymm0, ymm0, ymm1)
            ret()
        }
        assertEquals(5, insts.size)
        assertEquals("vaddps", insts[0].mnemonic)
        assertEquals("vsubps", insts[1].mnemonic)
        assertEquals("vmulps", insts[2].mnemonic)
        assertEquals("vdivps", insts[3].mnemonic)
    }

    // All conditional jumps in a chain

    @Test fun `all 16 jcc conditions chain`() {
        val insts = roundTrip {
            for (cc in 0..15) {
                jccLabel(cc, "target")
            }
            label("target")
            ret()
        }
        assertEquals(17, insts.size)
        val expectedMnemonics = listOf(
            "jo", "jno", "jb", "jnb", "jz", "jnz", "jbe", "ja",
            "js", "jns", "jp", "jnp", "jl", "jnl", "jle", "jg",
        )
        for (i in expectedMnemonics.indices) {
            assertEquals(expectedMnemonics[i], insts[i].mnemonic, "Condition code $i")
        }
        assertEquals("ret", insts[16].mnemonic)
    }

    // Byte consistency checks

    @Test fun `sse instruction bytes match`() {
        val asm = X86Assembler()
        asm.movsd(xmm0, xmm1)
        asm.addsd(xmm0, xmm2)
        asm.subsd(xmm0, xmm3)
        asm.mulsd(xmm0, xmm4)
        asm.divsd(xmm0, xmm5)
        asm.cvtsd2ss(xmm0, xmm6)
        asm.cvtss2sd(xmm0, xmm7)
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size },
            "SSE bytes should all be decoded: ${insts.map { it.text() }}")
    }

    @Test fun `vex instruction bytes match`() {
        val asm = X86Assembler()
        asm.vaddps(xmm0, xmm1, xmm2)
        asm.vsubps(xmm0, xmm1, xmm2)
        asm.vmulps(xmm0, xmm1, xmm2)
        asm.vdivps(xmm0, xmm1, xmm2)
        asm.vaddps(ymm0, ymm1, ymm2)
        asm.vsubps(ymm0, ymm1, ymm2)
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size },
            "VEX bytes should all be decoded: ${insts.map { it.text() }}")
    }

    @Test fun `bmi instruction bytes match`() {
        val asm = X86Assembler()
        asm.andn(eax, ecx, edx as X86Operand32)
        asm.blsi(eax, ecx as X86Operand32)
        asm.blsmsk(eax, ecx as X86Operand32)
        asm.blsr(eax, ecx as X86Operand32)
        asm.bextr(eax, ecx as X86Operand32, edx)
        asm.bzhi(eax, ecx as X86Operand32, edx)
        asm.rorx(eax, ecx as X86Operand32, 5)
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size },
            "BMI bytes should all be decoded: ${insts.map { it.text() }}")
    }

    @Test fun `setcc instruction bytes match`() {
        val asm = X86Assembler()
        asm.seto(al as X86Operand8)
        asm.setno(al as X86Operand8)
        asm.setb(al as X86Operand8)
        asm.setae(al as X86Operand8)
        asm.sete(al as X86Operand8)
        asm.setne(al as X86Operand8)
        asm.setbe(al as X86Operand8)
        asm.seta(al as X86Operand8)
        asm.sets(al as X86Operand8)
        asm.setns(al as X86Operand8)
        asm.setp(al as X86Operand8)
        asm.setnp(al as X86Operand8)
        asm.setl(al as X86Operand8)
        asm.setge(al as X86Operand8)
        asm.setle(al as X86Operand8)
        asm.setg(al as X86Operand8)
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size },
            "SETcc bytes should all be decoded: ${insts.map { it.text() }}")
    }

    @Test fun `cmovcc instruction bytes match`() {
        val asm = X86Assembler()
        asm.cmovo(rax, rcx as X86Operand64)
        asm.cmovno(rax, rcx as X86Operand64)
        asm.cmovb(rax, rcx as X86Operand64)
        asm.cmovae(rax, rcx as X86Operand64)
        asm.cmove(rax, rcx as X86Operand64)
        asm.cmovne(rax, rcx as X86Operand64)
        asm.cmovbe(rax, rcx as X86Operand64)
        asm.cmova(rax, rcx as X86Operand64)
        asm.cmovs(rax, rcx as X86Operand64)
        asm.cmovns(rax, rcx as X86Operand64)
        asm.cmovp(rax, rcx as X86Operand64)
        asm.cmovnp(rax, rcx as X86Operand64)
        asm.cmovl(rax, rcx as X86Operand64)
        asm.cmovge(rax, rcx as X86Operand64)
        asm.cmovle(rax, rcx as X86Operand64)
        asm.cmovg(rax, rcx as X86Operand64)
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size },
            "CMOVcc bytes should all be decoded: ${insts.map { it.text() }}")
    }

    @Test fun `addressing mode bytes match`() {
        val asm = X86Assembler()
        asm.mov(rax, X86Memory.base(rcx).offset(0) as X86Operand64)
        asm.mov(rax, X86Memory.base(rbp).offset(8) as X86Operand64)
        asm.mov(rax, X86Memory.base(rbp).offset(0x200) as X86Operand64)
        asm.mov(rax, X86Memory.base(rsp).offset(0) as X86Operand64)
        asm.mov(rax, X86Memory.base(r12).offset(0) as X86Operand64)
        asm.mov(rax, X86Memory.base(rbx).index(rcx, 4).offset(0) as X86Operand64)
        asm.mov(rax, X86Memory.base(rbx).index(rcx, 8).offset(16) as X86Operand64)
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size },
            "Addressing mode bytes should all be decoded: ${insts.map { it.text() }}")
    }

    @Test fun `string instruction bytes match`() {
        val asm = X86Assembler()
        asm.movsb()
        asm.movsq()
        asm.stosb()
        asm.stosd()
        asm.stosq()
        asm.rep(); asm.movsb()
        asm.rep(); asm.stosq()
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size },
            "String instruction bytes should all be decoded: ${insts.map { it.text() }}")
    }

    @Test fun `rotate instruction bytes match`() {
        val asm = X86Assembler()
        asm.rol(rax as X86Operand64, 3.toByte())
        asm.ror(rax as X86Operand64, 5.toByte())
        asm.rcl(rax as X86Operand64, 1.toByte())
        asm.rcr(rax as X86Operand64, 2.toByte())
        asm.rol(eax as X86Operand32, 4.toByte())
        asm.ror(eax as X86Operand32, 7.toByte())
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size },
            "Rotate bytes should all be decoded: ${insts.map { it.text() }}")
    }

    // Extended register combinations for MOV

    @Test fun `mov r8 r15`() {
        val inst = roundTripOne { mov(r8, r15 as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("r8", inst.operands[0].text())
        assertEquals("r15", inst.operands[1].text())
    }

    @Test fun `mov r12 r13`() {
        val inst = roundTripOne { mov(r12, r13 as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("r12", inst.operands[0].text())
        assertEquals("r13", inst.operands[1].text())
    }

    @Test fun `mov r14 rax`() {
        val inst = roundTripOne { mov(r14, rax as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("r14", inst.operands[0].text())
        assertEquals("rax", inst.operands[1].text())
    }

    @Test fun `mov rax r10`() {
        val inst = roundTripOne { mov(rax, r10 as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        assertEquals("r10", inst.operands[1].text())
    }

    // ADD/SUB with extended registers

    @Test fun `add r10 r11`() {
        val inst = roundTripOne { add(r10, r11 as X86Operand64) }
        assertEquals("add", inst.mnemonic)
        assertEquals("r10", inst.operands[0].text())
        assertEquals("r11", inst.operands[1].text())
    }

    @Test fun `sub r14 r15`() {
        val inst = roundTripOne { sub(r14, r15 as X86Operand64) }
        assertEquals("sub", inst.mnemonic)
        assertEquals("r14", inst.operands[0].text())
        assertEquals("r15", inst.operands[1].text())
    }

    // Shift with by-1 encoding

    @Test fun `shl r64 by 1`() {
        val inst = roundTripOne { shl(rax as X86Operand64) }
        assertEquals("shl", inst.mnemonic)
    }

    @Test fun `shr r64 by 1`() {
        val inst = roundTripOne { shr(rax as X86Operand64) }
        assertEquals("shr", inst.mnemonic)
    }

    @Test fun `sar r64 by 1`() {
        val inst = roundTripOne { sar(rax as X86Operand64) }
        assertEquals("sar", inst.mnemonic)
    }

    // TEST with various register sizes

    @Test fun `test r8 r8`() {
        val inst = roundTripOne { test(al as X86Operand8, al) }
        assertEquals("test", inst.mnemonic)
    }

    @Test fun `test r32 imm`() {
        val inst = roundTripOne { test(eax as X86Operand32, eax) }
        assertEquals("test", inst.mnemonic)
    }

    // Memory operations with 32-bit operands

    @Test fun `mov r32 mem base disp`() {
        val inst = roundTripOne { mov(eax, X86Memory.base(rbp).offset(-4) as X86Operand32) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test fun `mov mem r32 base disp`() {
        val inst = roundTripOne { mov(X86Memory.base(rbp).offset(-4), eax) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("eax", inst.operands[1].text())
    }

    @Test fun `add r32 mem`() {
        val inst = roundTripOne { add(eax, X86Memory.base(rbp).offset(-8) as X86Operand32) }
        assertEquals("add", inst.mnemonic)
    }

    // Comprehensive byte consistency for mixed instructions

    @Test fun `comprehensive mixed instruction byte consistency`() {
        val asm = X86Assembler()
        asm.push(rbp)
        asm.mov(rbp, rsp as X86Operand64)
        asm.sub(rsp, 64)
        asm.mov(eax, 0)
        asm.add(eax, 42)
        asm.shl(rax as X86Operand64, 4.toByte())
        asm.and_(rax as X86Operand64, 0xFF)
        asm.or_(rax as X86Operand64, 0x100)
        asm.xor_(rax, rax as X86Operand64)
        asm.bswap(eax)
        asm.bswap(rax)
        asm.neg(rax as X86Operand64)
        asm.not_(rax as X86Operand64)
        asm.inc(rax as X86Operand64)
        asm.dec(rax as X86Operand64)
        asm.cdq()
        asm.cqo()
        asm.leave()
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size },
            "Comprehensive bytes should all be decoded: ${insts.map { it.text() }}")
    }

    // IMUL r64 imm32

    @Test fun `imul r64 r64`() {
        val inst = roundTripOne { imul(rax, rcx as X86Operand64) }
        assertEquals("imul", inst.mnemonic)
    }

    @Test fun `imul r32 r32`() {
        val inst = roundTripOne { imul(eax, ecx as X86Operand32) }
        assertEquals("imul", inst.mnemonic)
    }

    // MOV r64 imm64

    @Test fun `mov r64 imm64`() {
        val inst = roundTripOne { mov(rax, 0x0123456789ABCDEFL) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    @Test fun `mov r64 imm64 extended reg`() {
        val inst = roundTripOne { mov(r12, -0x2152411035014542L) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("r12", inst.operands[0].text())
    }

    // Backward jumps

    @Test fun `backward jmp label`() {
        val insts = roundTrip {
            label("loop")
            inc(rax as X86Operand64)
            cmp(rax, 10)
            jccLabel(0x0C, "loop")
            ret()
        }
        assertTrue(insts.size >= 4)
        assertEquals("inc", insts[0].mnemonic)
        assertEquals("cmp", insts[1].mnemonic)
        assertEquals("jl", insts[2].mnemonic)
        assertEquals("ret", insts[3].mnemonic)
    }

    @Test fun `backward call label`() {
        val insts = roundTrip {
            label("func")
            ret()
            callLabel("func")
            ret()
        }
        assertEquals(3, insts.size)
        assertEquals("ret", insts[0].mnemonic)
        assertEquals("call", insts[1].mnemonic)
        assertEquals("ret", insts[2].mnemonic)
    }

    // CALL indirect

    @Test fun `call indirect r64`() {
        val inst = roundTripOne { call(rax as X86Operand64) }
        assertEquals("call", inst.mnemonic)
    }

    @Test fun `jmp indirect r64`() {
        val inst = roundTripOne { jmp(rax as X86Operand64) }
        assertEquals("jmp", inst.mnemonic)
    }

    // Multiple SSE regs

    @Test fun `sse uses all xmm0 through xmm7`() {
        val insts = roundTrip {
            movsd(xmm0, xmm1)
            addsd(xmm2, xmm3)
            subsd(xmm4, xmm5)
            mulsd(xmm6, xmm7)
            ret()
        }
        assertEquals(5, insts.size)
        assertEquals("xmm0", insts[0].operands[0].text())
        assertEquals("xmm1", insts[0].operands[1].text())
        assertEquals("xmm2", insts[1].operands[0].text())
        assertEquals("xmm3", insts[1].operands[1].text())
        assertEquals("xmm4", insts[2].operands[0].text())
        assertEquals("xmm5", insts[2].operands[1].text())
        assertEquals("xmm6", insts[3].operands[0].text())
        assertEquals("xmm7", insts[3].operands[1].text())
    }
}
