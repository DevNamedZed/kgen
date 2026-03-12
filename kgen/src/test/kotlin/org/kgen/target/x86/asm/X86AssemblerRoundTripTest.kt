package org.kgen.target.x86.asm

import org.kgen.target.x86.*
import org.kgen.target.x86.disasm.X86Disassembler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Round-trip tests: assemble → disassemble → verify mnemonic and operands match.
 * This validates both the assembler encoding and disassembler decoding.
 */
class X86AssemblerRoundTripTest {

    private val disasm = X86Disassembler()

    private val eax = X86Register.EAX as X86Register32
    private val ecx = X86Register.ECX as X86Register32
    private val edx = X86Register.EDX as X86Register32

    private val rax = X86Register.RAX as X86Register64
    private val rcx = X86Register.RCX as X86Register64
    private val rdx = X86Register.RDX as X86Register64
    private val rbx = X86Register.RBX as X86Register64
    private val rsp = X86Register.RSP as X86Register64
    private val rbp = X86Register.RBP as X86Register64
    private val rdi = X86Register.RDI as X86Register64
    private val rsi = X86Register.RSI as X86Register64
    private val r8  = X86Register.R8  as X86Register64
    private val r12 = X86Register.R12 as X86Register64

    private val al = X86Register.AL as X86Register8

    private val xmm0 = X86Register.XMM0 as X86Xmm
    private val xmm1 = X86Register.XMM1 as X86Xmm

    private fun roundTrip(block: X86Assembler.() -> Unit): List<org.kgen.target.x86.disasm.X86Instruction> {
        val asm = X86Assembler()
        asm.block()
        val bytes = asm.toByteArray()
        return disasm.disassembleRaw(bytes)
    }

    @Test
    fun nopRoundTrip() {
        val insts = roundTrip { nop() }
        assertEquals(1, insts.size)
        assertEquals("nop", insts[0].mnemonic)
    }

    @Test
    fun retRoundTrip() {
        val insts = roundTrip { ret() }
        assertEquals(1, insts.size)
        assertEquals("ret", insts[0].mnemonic)
    }

    @Test
    fun pushPopRoundTrip() {
        val insts = roundTrip {
            push(rbp)
            pop(rbp)
        }
        assertEquals(2, insts.size)
        assertEquals("push", insts[0].mnemonic)
        assertEquals("rbp", insts[0].operands[0].text())
        assertEquals("pop", insts[1].mnemonic)
        assertEquals("rbp", insts[1].operands[0].text())
    }

    @Test
    fun pushPopExtendedRoundTrip() {
        val insts = roundTrip {
            push(r12)
            pop(r12)
        }
        assertEquals(2, insts.size)
        assertEquals("push", insts[0].mnemonic)
        assertEquals("r12", insts[0].operands[0].text())
        assertEquals("pop", insts[1].mnemonic)
        assertEquals("r12", insts[1].operands[0].text())
    }

    @Test
    fun movRegImm32RoundTrip() {
        val insts = roundTrip { mov(eax, 42) }
        assertEquals(1, insts.size)
        assertEquals("mov", insts[0].mnemonic)
        assertEquals("eax", insts[0].operands[0].text())
        assertEquals("0x2a", insts[0].operands[1].text())
    }

    @Test
    fun movRegRegRoundTrip() {
        val insts = roundTrip { mov(rax, rcx as X86Operand64) }
        assertEquals(1, insts.size)
        assertEquals("mov", insts[0].mnemonic)
    }

    @Test
    fun addRegRegRoundTrip() {
        val insts = roundTrip { add(rax, rcx as X86Operand64) }
        assertEquals(1, insts.size)
        assertEquals("add", insts[0].mnemonic)
    }

    @Test
    fun subRegImmRoundTrip() {
        val insts = roundTrip { sub(rsp, 0x28) }
        assertEquals(1, insts.size)
        assertEquals("sub", insts[0].mnemonic)
    }

    @Test
    fun xorSelfClearRoundTrip() {
        val insts = roundTrip { xor_(eax, eax as X86Operand32) }
        assertEquals(1, insts.size)
        assertEquals("xor", insts[0].mnemonic)
        assertEquals("eax", insts[0].operands[0].text())
        assertEquals("eax", insts[0].operands[1].text())
    }

    @Test
    fun imulRegRegRoundTrip() {
        val insts = roundTrip { imul(eax, ecx as X86Operand32) }
        assertEquals(1, insts.size)
        assertEquals("imul", insts[0].mnemonic)
        assertEquals("eax", insts[0].operands[0].text())
        assertEquals("ecx", insts[0].operands[1].text())
    }

    @Test
    fun syscallRoundTrip() {
        val insts = roundTrip { syscall() }
        assertEquals(1, insts.size)
        assertEquals("syscall", insts[0].mnemonic)
    }

    @Test
    fun movMemRoundTrip() {
        val insts = roundTrip { mov(rax, X86Memory.base(rbp).offset(-8) as X86Operand64) }
        assertEquals(1, insts.size)
        assertEquals("mov", insts[0].mnemonic)
        assertEquals("rax", insts[0].operands[0].text())
        assertTrue(insts[0].operands[1].text().contains("rbp"), "Should reference rbp: ${insts[0].operands[1].text()}")
    }

    @Test
    fun leaRipRelRoundTrip() {
        val insts = roundTrip {
            label("data")
            lea(rdi, X86Memory.ripRelative("data"))
        }
        // label doesn't emit bytes, so just one instruction
        assertEquals(1, insts.size)
        assertEquals("lea", insts[0].mnemonic)
        assertEquals("rdi", insts[0].operands[0].text())
    }

    @Test
    fun functionPrologEpilogRoundTrip() {
        val insts = roundTrip {
            push(rbp)
            mov(rbp, rsp as X86Operand64)
            sub(rsp, 32)
            // ... function body ...
            mov(rsp, rbp as X86Operand64)
            pop(rbp)
            ret()
        }
        assertEquals(6, insts.size)
        assertEquals("push", insts[0].mnemonic)
        assertEquals("mov", insts[1].mnemonic)
        assertEquals("sub", insts[2].mnemonic)
        assertEquals("mov", insts[3].mnemonic)
        assertEquals("pop", insts[4].mnemonic)
        assertEquals("ret", insts[5].mnemonic)
    }

    @Test
    fun sseAddsdRoundTrip() {
        val insts = roundTrip { addsd(xmm0, xmm1) }
        assertEquals(1, insts.size)
        assertEquals("addsd", insts[0].mnemonic)
        assertEquals("xmm0", insts[0].operands[0].text())
        assertEquals("xmm1", insts[0].operands[1].text())
    }

    @Test
    fun sseSubsdRoundTrip() {
        val insts = roundTrip { subsd(xmm0, xmm1) }
        assertEquals(1, insts.size)
        assertEquals("subsd", insts[0].mnemonic)
    }

    @Test
    fun sseMulsdRoundTrip() {
        val insts = roundTrip { mulsd(xmm0, xmm1) }
        assertEquals(1, insts.size)
        assertEquals("mulsd", insts[0].mnemonic)
    }

    @Test
    fun sseDivsdRoundTrip() {
        val insts = roundTrip { divsd(xmm0, xmm1) }
        assertEquals(1, insts.size)
        assertEquals("divsd", insts[0].mnemonic)
    }

    @Test
    fun sseUcomisdRoundTrip() {
        val insts = roundTrip { ucomisd(xmm0, xmm1) }
        assertEquals(1, insts.size)
        assertEquals("ucomisd", insts[0].mnemonic)
    }

    @Test
    fun vexAndnRoundTrip() {
        val insts = roundTrip { andn(eax, ecx, edx as X86Operand32) }
        assertEquals(1, insts.size)
        assertEquals("andn", insts[0].mnemonic)
    }

    @Test
    fun bswapRoundTrip() {
        val insts = roundTrip { bswap(eax) }
        assertEquals(1, insts.size)
        assertEquals("bswap", insts[0].mnemonic)
        assertEquals("eax", insts[0].operands[0].text())
    }

    @Test
    fun multipleInstructionSequence() {
        val insts = roundTrip {
            push(rbp)
            mov(rbp, rsp as X86Operand64)
            mov(eax, 1)
            mov(rdi, rsi as X86Operand64)
            syscall()
            mov(eax, 60)
            xor_(rdi, rdi as X86Operand64)
            syscall()
            pop(rbp)
            ret()
        }
        assertEquals(10, insts.size)
        // Verify total decoded bytes match assembled bytes
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
        assertEquals(asm.position(), insts.sumOf { it.size })
    }

    @Test
    fun totalDecodedBytesMatchAssembled() {
        val asm = X86Assembler()
        asm.add(rax, rcx as X86Operand64)
        asm.sub(rax, 100)
        asm.imul(eax, ecx as X86Operand32)
        asm.nop()
        asm.ret()
        val bytes = asm.toByteArray()
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size },
            "All bytes should be decoded. Instructions: ${insts.map { it.text() }}")
    }
}
