package org.kgen.target.x86.asm

import org.kgen.target.x86.*
import org.kgen.target.x86.disasm.X86Disassembler
import org.kgen.target.x86.disasm.X86Instruction

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class X86AssemblerRoundTripExtendedTest {

    private val disasm = X86Disassembler()

    private val eax = X86Register.EAX as X86Register32
    private val ecx = X86Register.ECX as X86Register32
    private val edx = X86Register.EDX as X86Register32
    private val ebx = X86Register.EBX as X86Register32
    private val edi = X86Register.EDI as X86Register32
    private val r8d = X86Register.R8D as X86Register32

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

    private val al = X86Register.AL as X86Register8

    private val xmm0 = X86Register.XMM0 as X86Xmm
    private val xmm1 = X86Register.XMM1 as X86Xmm
    private val xmm2 = X86Register.XMM2 as X86Xmm
    private val xmm3 = X86Register.XMM3 as X86Xmm
    private val xmm4 = X86Register.XMM4 as X86Xmm
    private val xmm5 = X86Register.XMM5 as X86Xmm

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

    // --- ADD round-trips ---

    @Test
    fun addR64R64() {
        val inst = roundTripOne { add(rax, rcx as X86Operand64) }
        assertEquals("add", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        assertEquals("rcx", inst.operands[1].text())
    }

    @Test
    fun addR64Imm() {
        val inst = roundTripOne { add(rax, 0x12345678) }
        assertEquals("add", inst.mnemonic)
    }

    @Test
    fun addExtR64R64() {
        val inst = roundTripOne { add(r8, r9 as X86Operand64) }
        assertEquals("add", inst.mnemonic)
        assertEquals("r8", inst.operands[0].text())
        assertEquals("r9", inst.operands[1].text())
    }

    @Test
    fun addR32R32() {
        val inst = roundTripOne { add(eax, ecx as X86Operand32) }
        assertEquals("add", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test
    fun addR32Imm() {
        val inst = roundTripOne { add(eax, 100) }
        assertEquals("add", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    // --- SUB round-trips ---

    @Test
    fun subR64R64() {
        val inst = roundTripOne { sub(rax, rcx as X86Operand64) }
        assertEquals("sub", inst.mnemonic)
    }

    @Test
    fun subR64Imm32() {
        val inst = roundTripOne { sub(rsp, 0x1000) }
        assertEquals("sub", inst.mnemonic)
    }

    @Test
    fun subR32R32() {
        val inst = roundTripOne { sub(eax, edx as X86Operand32) }
        assertEquals("sub", inst.mnemonic)
    }

    @Test
    fun subExtRegs() {
        val inst = roundTripOne { sub(r10, r11 as X86Operand64) }
        assertEquals("sub", inst.mnemonic)
        assertEquals("r10", inst.operands[0].text())
        assertEquals("r11", inst.operands[1].text())
    }

    // --- AND/OR/XOR round-trips ---

    @Test
    fun andR64R64() {
        val inst = roundTripOne { and_(rax, rcx as X86Operand64) }
        assertEquals("and", inst.mnemonic)
    }

    @Test
    fun orR64R64() {
        val inst = roundTripOne { or_(rax, rdx as X86Operand64) }
        assertEquals("or", inst.mnemonic)
    }

    @Test
    fun xorR64R64() {
        val inst = roundTripOne { xor_(rax, rbx as X86Operand64) }
        assertEquals("xor", inst.mnemonic)
    }

    @Test
    fun xorR32SelfClear() {
        val inst = roundTripOne { xor_(eax, eax as X86Operand32) }
        assertEquals("xor", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("eax", inst.operands[1].text())
    }

    @Test
    fun andExtRegs() {
        val inst = roundTripOne { and_(r12, r13 as X86Operand64) }
        assertEquals("and", inst.mnemonic)
        assertEquals("r12", inst.operands[0].text())
        assertEquals("r13", inst.operands[1].text())
    }

    // --- CMP ---

    @Test
    fun cmpR64R64() {
        val inst = roundTripOne { cmp(rax, rcx as X86Operand64) }
        assertEquals("cmp", inst.mnemonic)
    }

    @Test
    fun cmpR64Imm() {
        val inst = roundTripOne { cmp(rax, 42) }
        assertEquals("cmp", inst.mnemonic)
    }

    // --- TEST ---

    @Test
    fun testR64R64() {
        val inst = roundTripOne { test(rax as X86Operand64, rax) }
        assertEquals("test", inst.mnemonic)
    }

    @Test
    fun testR32R32() {
        val inst = roundTripOne { test(eax as X86Operand32, eax) }
        assertEquals("test", inst.mnemonic)
    }

    // --- IMUL ---

    @Test
    fun imulR32R32() {
        val inst = roundTripOne { imul(eax, ecx as X86Operand32) }
        assertEquals("imul", inst.mnemonic)
    }

    @Test
    fun imulR64R64() {
        val inst = roundTripOne { imul(rax, rcx as X86Operand64) }
        assertEquals("imul", inst.mnemonic)
    }

    // --- NEG/NOT ---

    @Test
    fun negR64() {
        val inst = roundTripOne { neg(rax as X86Operand64) }
        assertEquals("neg", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    @Test
    fun notR64() {
        val inst = roundTripOne { not_(rax as X86Operand64) }
        assertEquals("not", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    // --- INC/DEC ---

    @Test
    fun incR64() {
        val inst = roundTripOne { inc(rax as X86Operand64) }
        assertEquals("inc", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    @Test
    fun decR64() {
        val inst = roundTripOne { dec(rax as X86Operand64) }
        assertEquals("dec", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    @Test
    fun incExtReg() {
        val inst = roundTripOne { inc(r15 as X86Operand64) }
        assertEquals("inc", inst.mnemonic)
        assertEquals("r15", inst.operands[0].text())
    }

    @Test
    fun decExtReg() {
        val inst = roundTripOne { dec(r14 as X86Operand64) }
        assertEquals("dec", inst.mnemonic)
        assertEquals("r14", inst.operands[0].text())
    }

    // --- Shifts ---

    @Test
    fun shlR64() {
        val inst = roundTripOne { shl(rax as X86Operand64, 4.toByte()) }
        assertEquals("shl", inst.mnemonic)
    }

    @Test
    fun shrR64() {
        val inst = roundTripOne { shr(rax as X86Operand64, 1.toByte()) }
        assertEquals("shr", inst.mnemonic)
    }

    @Test
    fun sarR64() {
        val inst = roundTripOne { sar(rax as X86Operand64, 8.toByte()) }
        assertEquals("sar", inst.mnemonic)
    }

    // --- MOV variants ---

    @Test
    fun movR64R64Standard() {
        val inst = roundTripOne { mov(rdi, rsi as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("rdi", inst.operands[0].text())
        assertEquals("rsi", inst.operands[1].text())
    }

    @Test
    fun movR64R64Extended() {
        val inst = roundTripOne { mov(r8, r15 as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("r8", inst.operands[0].text())
        assertEquals("r15", inst.operands[1].text())
    }

    @Test
    fun movR32Imm32() {
        val inst = roundTripOne { mov(eax, 0x7FFFFFFF) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun movR64Imm64() {
        val inst = roundTripOne { mov(rax, 0x0102030405060708L) }
        assertEquals("mov", inst.mnemonic)
    }

    @Test
    fun movR32Zero() {
        val inst = roundTripOne { mov(eax, 0) }
        assertEquals("mov", inst.mnemonic)
    }

    // --- Memory operations ---

    @Test
    fun movR64MemBaseDisp() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rbp).offset(-16) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        assertTrue(inst.operands[1].text().contains("rbp"))
    }

    @Test
    fun movMemR64BaseDisp() {
        val inst = roundTripOne { mov(X86Memory.base(rbp).offset(-24), rax) }
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[0].text().contains("rbp"))
        assertEquals("rax", inst.operands[1].text())
    }

    @Test
    fun movR64MemBase() {
        val inst = roundTripOne { mov(rax, X86Memory.base(rcx).offset(0) as X86Operand64) }
        assertEquals("mov", inst.mnemonic)
    }

    @Test
    fun leaBaseIndex() {
        val inst = roundTripOne { lea(rax, X86Memory.base(rbx).index(rcx, 4).offset(0)) }
        assertEquals("lea", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    @Test
    fun leaDisp32() {
        val inst = roundTripOne { lea(rax, X86Memory.base(rbp).offset(0x100)) }
        assertEquals("lea", inst.mnemonic)
    }

    // --- PUSH/POP all standard regs ---

    @Test
    fun pushPopAllStandard() {
        val regs = listOf(rax, rcx, rdx, rbx, rbp, rsi, rdi)
        val insts = roundTrip {
            for (r in regs) push(r)
            for (r in regs.reversed()) pop(r)
        }
        assertEquals(regs.size * 2, insts.size)
        for (i in regs.indices) {
            assertEquals("push", insts[i].mnemonic)
        }
        for (i in regs.indices) {
            assertEquals("pop", insts[regs.size + i].mnemonic)
        }
    }

    @Test
    fun pushPopExtended() {
        val regs = listOf(r8, r9, r10, r11, r12, r13, r14, r15)
        val insts = roundTrip {
            for (r in regs) push(r)
            for (r in regs.reversed()) pop(r)
        }
        assertEquals(regs.size * 2, insts.size)
        for (i in regs.indices) {
            assertEquals("push", insts[i].mnemonic)
        }
    }

    // --- Conditional jumps (disassembler uses jz/jnz aliases) ---

    @Test
    fun jzLabelRoundTrip() {
        val insts = roundTrip {
            cmp(rax, 0)
            jccLabel(0x04, "target")
            nop()
            label("target")
            ret()
        }
        assertTrue(insts.size >= 4)
        assertEquals("cmp", insts[0].mnemonic)
        assertEquals("jz", insts[1].mnemonic)
        assertEquals("nop", insts[2].mnemonic)
        assertEquals("ret", insts[3].mnemonic)
    }

    @Test
    fun jnzLabelRoundTrip() {
        val insts = roundTrip {
            test(rax as X86Operand64, rax)
            jccLabel(0x05, "nonzero")
            ret()
            label("nonzero")
            nop()
            ret()
        }
        assertTrue(insts.size >= 4)
        assertEquals("test", insts[0].mnemonic)
        assertEquals("jnz", insts[1].mnemonic)
    }

    @Test
    fun jmpLabelRoundTrip() {
        val insts = roundTrip {
            jmpLabel("end")
            nop()
            label("end")
            ret()
        }
        assertTrue(insts.size >= 3)
        assertEquals("jmp", insts[0].mnemonic)
    }

    @Test
    fun jmpBackwardReference() {
        val insts = roundTrip {
            label("top")
            nop()
            jmpLabel("top")
        }
        assertEquals(2, insts.size)
        assertEquals("nop", insts[0].mnemonic)
        assertEquals("jmp", insts[1].mnemonic)
    }

    // --- CALL ---

    @Test
    fun callLabelRoundTrip() {
        val insts = roundTrip {
            callLabel("func")
            ret()
            label("func")
            ret()
        }
        assertTrue(insts.size >= 3)
        assertEquals("call", insts[0].mnemonic)
    }

    // --- SSE scalar double (well-supported by disassembler) ---

    @Test
    fun addsdRoundTrip() {
        val inst = roundTripOne { addsd(xmm0, xmm1) }
        assertEquals("addsd", inst.mnemonic)
    }

    @Test
    fun subsdRoundTrip() {
        val inst = roundTripOne { subsd(xmm2, xmm3) }
        assertEquals("subsd", inst.mnemonic)
    }

    @Test
    fun mulsdRoundTrip() {
        val inst = roundTripOne { mulsd(xmm0, xmm1) }
        assertEquals("mulsd", inst.mnemonic)
    }

    @Test
    fun divsdRoundTrip() {
        val inst = roundTripOne { divsd(xmm0, xmm1) }
        assertEquals("divsd", inst.mnemonic)
    }

    @Test
    fun ucomisdRoundTrip() {
        val inst = roundTripOne { ucomisd(xmm0, xmm1) }
        assertEquals("ucomisd", inst.mnemonic)
    }

    // --- SSE scalar single (well-supported by disassembler) ---

    @Test
    fun addssRoundTrip() {
        val inst = roundTripOne { addss(xmm0, xmm1) }
        assertEquals("addss", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("xmm1", inst.operands[1].text())
    }

    @Test
    fun subssRoundTrip() {
        val inst = roundTripOne { subss(xmm2, xmm3) }
        assertEquals("subss", inst.mnemonic)
    }

    @Test
    fun mulssRoundTrip() {
        val inst = roundTripOne { mulss(xmm4, xmm5) }
        assertEquals("mulss", inst.mnemonic)
    }

    @Test
    fun divssRoundTrip() {
        val inst = roundTripOne { divss(xmm0, xmm1) }
        assertEquals("divss", inst.mnemonic)
    }

    // --- SSE moves ---

    @Test
    fun movssRoundTrip() {
        val inst = roundTripOne { movss(xmm0, xmm1) }
        assertEquals("movss", inst.mnemonic)
    }

    @Test
    fun movsdRoundTrip() {
        val inst = roundTripOne { movsd(xmm0, xmm1) }
        assertEquals("movsd", inst.mnemonic)
    }

    // --- SSE conversions ---

    @Test
    fun cvtsi2sdRoundTrip() {
        val inst = roundTripOne { cvtsi2sd(xmm0, eax) }
        assertEquals("cvtsi2sd", inst.mnemonic)
    }

    @Test
    fun cvtsi2ssRoundTrip() {
        val inst = roundTripOne { cvtsi2ss(xmm0, eax) }
        assertEquals("cvtsi2ss", inst.mnemonic)
    }

    @Test
    fun cvtsd2siRoundTrip() {
        val inst = roundTripOne { cvtsd2si(eax, xmm0) }
        assertEquals("cvtsd2si", inst.mnemonic)
    }

    @Test
    fun cvtss2sdRoundTrip() {
        val inst = roundTripOne { cvtss2sd(xmm0, xmm1) }
        assertEquals("cvtss2sd", inst.mnemonic)
    }

    @Test
    fun cvtsd2ssRoundTrip() {
        val inst = roundTripOne { cvtsd2ss(xmm0, xmm1) }
        assertEquals("cvtsd2ss", inst.mnemonic)
    }

    // --- MOVZX/MOVSX ---

    @Test
    fun movzxR32R8() {
        val inst = roundTripOne { movzx(eax, al as X86Operand8) }
        assertEquals("movzx", inst.mnemonic)
    }

    @Test
    fun movsxR32R8() {
        val inst = roundTripOne { movsx(eax, al as X86Operand8) }
        assertEquals("movsx", inst.mnemonic)
    }

    // --- BSWAP ---

    @Test
    fun bswapR32RoundTrip() {
        val inst = roundTripOne { bswap(eax) }
        assertEquals("bswap", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun bswapR64RoundTrip() {
        val inst = roundTripOne { bswap(rax) }
        assertEquals("bswap", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    // --- BSF/BSR ---

    @Test
    fun bsfRoundTrip() {
        val inst = roundTripOne { bsf(rax, rcx as X86Operand64) }
        assertEquals("bsf", inst.mnemonic)
    }

    @Test
    fun bsrRoundTrip() {
        val inst = roundTripOne { bsr(rax, rcx as X86Operand64) }
        assertEquals("bsr", inst.mnemonic)
    }

    // --- DIV/IDIV ---

    @Test
    fun divR64RoundTrip() {
        val inst = roundTripOne { div(rcx as X86Operand64) }
        assertEquals("div", inst.mnemonic)
    }

    @Test
    fun idivR64RoundTrip() {
        val inst = roundTripOne { idiv(rcx as X86Operand64) }
        assertEquals("idiv", inst.mnemonic)
    }

    // --- VEX instructions ---

    @Test
    fun andnRoundTrip() {
        val inst = roundTripOne { andn(eax, ecx, edx as X86Operand32) }
        assertEquals("andn", inst.mnemonic)
    }

    @Test
    fun vaddpsYmmRoundTrip() {
        val inst = roundTripOne { vaddps(ymm0, ymm1, ymm2) }
        assertEquals("vaddps", inst.mnemonic)
    }

    @Test
    fun vsubpsXmmRoundTrip() {
        val inst = roundTripOne { vsubps(xmm0, xmm1, xmm2) }
        assertEquals("vsubps", inst.mnemonic)
    }

    @Test
    fun vmulpsXmmRoundTrip() {
        val inst = roundTripOne { vmulps(xmm0, xmm1, xmm2) }
        assertEquals("vmulps", inst.mnemonic)
    }

    @Test
    fun vdivpsXmmRoundTrip() {
        val inst = roundTripOne { vdivps(xmm0, xmm1, xmm2) }
        assertEquals("vdivps", inst.mnemonic)
    }

    // --- Conditional moves (disassembler uses cmovz/cmovnz/cmovnb aliases) ---

    @Test
    fun cmovzR64R64RoundTrip() {
        val inst = roundTripOne { cmove(rax, rcx as X86Operand64) }
        assertEquals("cmovz", inst.mnemonic)
    }

    @Test
    fun cmovnzR64R64RoundTrip() {
        val inst = roundTripOne { cmovne(rax, rcx as X86Operand64) }
        assertEquals("cmovnz", inst.mnemonic)
    }

    @Test
    fun cmovnbR64R64RoundTrip() {
        val inst = roundTripOne { cmovae(rax, rcx as X86Operand64) }
        assertEquals("cmovnb", inst.mnemonic)
    }

    @Test
    fun cmovbR64R64RoundTrip() {
        val inst = roundTripOne { cmovb(rax, rcx as X86Operand64) }
        assertEquals("cmovb", inst.mnemonic)
    }

    @Test
    fun cmovleR64R64RoundTrip() {
        val inst = roundTripOne { cmovle(rax, rcx as X86Operand64) }
        assertEquals("cmovle", inst.mnemonic)
    }

    @Test
    fun cmovgR64R64RoundTrip() {
        val inst = roundTripOne { cmovg(rax, rcx as X86Operand64) }
        assertEquals("cmovg", inst.mnemonic)
    }

    // --- System instructions ---

    @Test
    fun syscallRoundTrip() {
        val inst = roundTripOne { syscall() }
        assertEquals("syscall", inst.mnemonic)
    }

    // --- Complex sequences ---

    @Test
    fun fibonacciSequence() {
        val insts = roundTrip {
            xor_(eax, eax as X86Operand32)
            mov(ecx, 1)
            mov(edx, 10)
            label("loop")
            mov(ebx, eax as X86Operand32)
            add(eax, ecx as X86Operand32)
            mov(ecx, ebx as X86Operand32)
            dec(rdx as X86Operand64)
            jccLabel(0x05, "loop")
            ret()
        }
        assertTrue(insts.size >= 8)
        assertEquals("ret", insts.last().mnemonic)
    }

    @Test
    fun functionWithMemoryOps() {
        val insts = roundTrip {
            push(rbp)
            mov(rbp, rsp as X86Operand64)
            sub(rsp, 32)
            mov(X86Memory.base(rbp).offset(-8), rdi)
            mov(X86Memory.base(rbp).offset(-16), rsi)
            mov(rax, X86Memory.base(rbp).offset(-8) as X86Operand64)
            add(rax, X86Memory.base(rbp).offset(-16) as X86Operand64)
            mov(rsp, rbp as X86Operand64)
            pop(rbp)
            ret()
        }
        assertEquals(10, insts.size)
        assertEquals("push", insts[0].mnemonic)
        assertEquals("ret", insts[9].mnemonic)
    }

    @Test
    fun sseArithmeticSequence() {
        val insts = roundTrip {
            movsd(xmm0, xmm1)
            addsd(xmm0, xmm2)
            mulsd(xmm0, xmm3)
            subsd(xmm0, xmm4)
            divsd(xmm0, xmm5)
            ret()
        }
        assertEquals(6, insts.size)
        assertEquals("movsd", insts[0].mnemonic)
        assertEquals("addsd", insts[1].mnemonic)
        assertEquals("mulsd", insts[2].mnemonic)
        assertEquals("subsd", insts[3].mnemonic)
        assertEquals("divsd", insts[4].mnemonic)
        assertEquals("ret", insts[5].mnemonic)
    }

    @Test
    fun conditionalBranchChain() {
        val insts = roundTrip {
            cmp(rax, 0)
            jccLabel(0x04, "zero")
            cmp(rax, 1)
            jccLabel(0x04, "one")
            cmp(rax, 2)
            jccLabel(0x04, "two")
            mov(eax, -1)
            jmpLabel("end")
            label("zero")
            xor_(eax, eax as X86Operand32)
            jmpLabel("end")
            label("one")
            mov(eax, 100)
            jmpLabel("end")
            label("two")
            mov(eax, 200)
            label("end")
            ret()
        }
        assertTrue(insts.size >= 10)
        assertEquals("ret", insts.last().mnemonic)
    }

    // --- Byte size consistency ---

    @Test
    fun allDecodedBytesMatchAssembled() {
        val asm = X86Assembler()
        asm.push(rbp)
        asm.mov(rbp, rsp as X86Operand64)
        asm.sub(rsp, 0x28)
        asm.mov(eax, 42)
        asm.add(rax, rcx as X86Operand64)
        asm.xor_(edi, edi as X86Operand32)
        asm.imul(eax, ecx as X86Operand32)
        asm.neg(rax as X86Operand64)
        asm.not_(rcx as X86Operand64)
        asm.inc(rdx as X86Operand64)
        asm.dec(rbx as X86Operand64)
        asm.shl(rax as X86Operand64, 4.toByte())
        asm.nop()
        asm.pop(rbp)
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size },
            "All bytes should be decoded. Instructions: ${insts.map { it.text() }}")
    }

    @Test
    fun extendedRegsByteConsistency() {
        val asm = X86Assembler()
        asm.push(r12)
        asm.push(r13)
        asm.push(r14)
        asm.push(r15)
        asm.mov(r8, r9 as X86Operand64)
        asm.add(r10, r11 as X86Operand64)
        asm.sub(r12, r13 as X86Operand64)
        asm.pop(r15)
        asm.pop(r14)
        asm.pop(r13)
        asm.pop(r12)
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size })
    }

    // --- AT&T syntax output ---

    @Test
    fun attSyntaxAddR64() {
        val inst = roundTripOne { add(rax, rcx as X86Operand64) }
        val att = inst.attText()
        assertTrue(att.contains("%rax"), "AT&T should have %rax: $att")
        assertTrue(att.contains("%rcx"), "AT&T should have %rcx: $att")
    }

    @Test
    fun attSyntaxMov() {
        val inst = roundTripOne { mov(eax, 42) }
        val att = inst.attText()
        assertTrue(att.contains("$"), "AT&T imm should start with \$: $att")
    }

    @Test
    fun intelSyntaxMov() {
        val inst = roundTripOne { mov(eax, 42) }
        val intel = inst.intelText()
        assertTrue(intel.startsWith("mov"), "Intel should start with mov: $intel")
    }

    // --- toString ---

    @Test
    fun toStringContainsAddress() {
        val insts = roundTrip { nop() }
        val str = insts[0].toString()
        assertTrue(str.contains("0x"), "toString should contain address: $str")
        assertTrue(str.contains("nop"), "toString should contain mnemonic: $str")
    }

    // --- Multiple instruction round-trips verify total bytes ---

    @Test
    fun multiInstructionBytesMatch() {
        val asm = X86Assembler()
        asm.push(rbp)
        asm.mov(rbp, rsp as X86Operand64)
        asm.mov(eax, 1)
        asm.mov(rdi, rsi as X86Operand64)
        asm.syscall()
        asm.mov(eax, 60)
        asm.xor_(rdi, rdi as X86Operand64)
        asm.syscall()
        asm.pop(rbp)
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(10, insts.size)
        assertEquals(bytes.size, insts.sumOf { it.size })
    }

    // --- LEA with RIP-relative ---

    @Test
    fun leaRipRelRoundTrip() {
        val insts = roundTrip {
            label("data")
            lea(rdi, X86Memory.ripRelative("data"))
        }
        assertEquals(1, insts.size)
        assertEquals("lea", insts[0].mnemonic)
        assertEquals("rdi", insts[0].operands[0].text())
    }

    // --- Disassembler instruction properties ---

    @Test
    fun instructionSize() {
        val insts = roundTrip { nop() }
        assertEquals(1, insts[0].size)
        assertEquals(1, insts[0].bytes.size)
    }

    @Test
    fun instructionOperandsText() {
        val inst = roundTripOne { add(rax, rcx as X86Operand64) }
        val opText = inst.operandsText()
        assertTrue(opText.contains("rax"))
        assertTrue(opText.contains("rcx"))
    }

    @Test
    fun instructionTextMatchesIntel() {
        val inst = roundTripOne { add(rax, rcx as X86Operand64) }
        assertEquals(inst.text(), inst.intelText())
    }

    @Test
    fun pushR64Encoding() {
        val insts = roundTrip {
            push(rax)
            push(rcx)
            push(rdx)
            push(rbx)
            push(rsp)
            push(rbp)
            push(rsi)
            push(rdi)
        }
        assertEquals(8, insts.size)
        val names = listOf("rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi")
        for (i in insts.indices) {
            assertEquals("push", insts[i].mnemonic)
            assertEquals(names[i], insts[i].operands[0].text())
        }
    }

    @Test
    fun popR64Encoding() {
        val insts = roundTrip {
            pop(rax)
            pop(rcx)
            pop(rdx)
            pop(rbx)
            pop(rbp)
            pop(rsi)
            pop(rdi)
        }
        assertEquals(7, insts.size)
        val names = listOf("rax", "rcx", "rdx", "rbx", "rbp", "rsi", "rdi")
        for (i in insts.indices) {
            assertEquals("pop", insts[i].mnemonic)
            assertEquals(names[i], insts[i].operands[0].text())
        }
    }

    @Test
    fun pushExtendedR64Encoding() {
        val insts = roundTrip {
            push(r8)
            push(r9)
            push(r10)
            push(r11)
            push(r12)
            push(r13)
            push(r14)
            push(r15)
        }
        assertEquals(8, insts.size)
        val names = listOf("r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15")
        for (i in insts.indices) {
            assertEquals("push", insts[i].mnemonic)
            assertEquals(names[i], insts[i].operands[0].text())
        }
    }

    @Test
    fun movAllR64R64Combinations() {
        val pairs = listOf(
            rax to rcx, rcx to rdx, rdx to rbx,
            rbx to rsp, rsp to rbp, rbp to rsi,
            rsi to rdi, rdi to rax,
        )
        for ((dst, src) in pairs) {
            val inst = roundTripOne { mov(dst, src as X86Operand64) }
            assertEquals("mov", inst.mnemonic)
        }
    }

    @Test
    fun addAllR64R64Combinations() {
        val pairs = listOf(
            rax to rcx, rcx to rdx, rdx to rbx,
            rbx to rdi, rdi to rsi, rsi to rbp,
        )
        for ((dst, src) in pairs) {
            val inst = roundTripOne { add(dst, src as X86Operand64) }
            assertEquals("add", inst.mnemonic)
        }
    }

    @Test
    fun subR64ImmVariousValues() {
        val values = listOf(0x100, 0x1000, 0x10000, 0x7FFFFFFF)
        for (v in values) {
            val inst = roundTripOne { sub(rsp, v) }
            assertEquals("sub", inst.mnemonic)
        }
    }

    @Test
    fun memLoadVariousDispSizes() {
        val disps = listOf(0, 8, 16, 127, 128, 256, 4096)
        for (d in disps) {
            val inst = roundTripOne { mov(rax, X86Memory.base(rbp).offset(d) as X86Operand64) }
            assertEquals("mov", inst.mnemonic)
        }
    }

    @Test
    fun memStoreVariousDispSizes() {
        val disps = listOf(0, 8, -8, -128, 256)
        for (d in disps) {
            val inst = roundTripOne { mov(X86Memory.base(rbp).offset(d), rax) }
            assertEquals("mov", inst.mnemonic)
        }
    }
}
