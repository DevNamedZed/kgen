package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.backend.x86.disasm.X86Disassembler
import org.kgen.backend.x86.disasm.X86Instruction
import org.kgen.backend.x86.disasm.X86Operand
import org.kgen.backend.arm64.disasm.Arm64Disassembler
import org.kgen.backend.arm64.disasm.Arm64Instruction

class InstructionTest {

    @Test
    fun x86InstructionImplementsInterface() {
        val insn: Instruction = X86Instruction(
            address = 0x1000,
            bytes = byteArrayOf(0x90.toByte()),
            mnemonic = "nop",
            operands = emptyList(),
        )
        assertEquals(0x1000L, insn.address)
        assertEquals("nop", insn.mnemonic)
        assertEquals(1, insn.size)
        assertEquals("nop", insn.text())
        assertEquals("", insn.operandsText())
    }

    @Test
    fun x86InstructionWithOperands() {
        val insn: Instruction = X86Instruction(
            address = 0x2000,
            bytes = byteArrayOf(0x48.toByte(), 0x89.toByte(), 0xC1.toByte()),
            mnemonic = "mov",
            operands = listOf(
                X86Operand.Register("rcx"),
                X86Operand.Register("rax"),
            ),
        )
        assertEquals("mov", insn.mnemonic)
        assertEquals(3, insn.size)
        assertEquals("mov rcx, rax", insn.text())
        assertEquals("rcx, rax", insn.operandsText())
    }

    @Test
    fun x86DisassemblerProducesInstructions() {
        val code = byteArrayOf(
            0x90.toByte(),                                   // nop
            0x48.toByte(), 0x31.toByte(), 0xC0.toByte(),    // xor rax, rax
            0xC3.toByte(),                                   // ret
        )
        val insns: List<Instruction> = X86Disassembler().disassembleRaw(code, 0x1000)
        assertEquals(3, insns.size)
        assertEquals("nop", insns[0].mnemonic)
        assertEquals(0x1000L, insns[0].address)
        assertEquals("xor", insns[1].mnemonic)
        assertEquals(0x1001L, insns[1].address)
        assertEquals("ret", insns[2].mnemonic)
    }

    @Test
    fun x86DowncastForArchSpecific() {
        val insn: Instruction = X86Instruction(
            address = 0,
            bytes = byteArrayOf(0x48.toByte(), 0x89.toByte(), 0xC1.toByte()),
            mnemonic = "mov",
            operands = listOf(X86Operand.Register("rcx"), X86Operand.Register("rax")),
        )
        assertTrue(insn is X86Instruction)
        val x86 = insn as X86Instruction
        assertEquals(2, x86.operands.size)
        assertTrue(x86.operands[0] is X86Operand.Register)
    }

    @Test
    fun arm64InstructionImplementsInterface() {
        val insn: Instruction = Arm64Instruction(
            address = 0x400,
            mnemonic = "nop",
            operandsStr = "",
            rawBytes = 0xD503201F.toInt(),
        )
        assertEquals(0x400L, insn.address)
        assertEquals("nop", insn.mnemonic)
        assertEquals(4, insn.size)
        assertEquals("nop", insn.text())
        assertEquals("", insn.operandsText())
    }

    @Test
    fun arm64DisassemblerProducesInstructions() {
        val code = byteArrayOf(
            0x1F, 0x20, 0x03, 0xD5.toByte(),  // nop
            0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte(),  // ret
        )
        val insns: List<Instruction> = Arm64Disassembler().disassemble(code, 0x1000)
        assertEquals(2, insns.size)
        assertEquals("nop", insns[0].mnemonic)
        assertEquals(0x1000L, insns[0].address)
        assertEquals("ret", insns[1].mnemonic)
        assertEquals(0x1004L, insns[1].address)
    }
}
