package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.backend.x86.disasm.X86Disassembler
import org.kgen.backend.x86.disasm.X86Instruction
import org.kgen.backend.x86.disasm.X86Operand
import org.kgen.backend.arm64.disasm.Arm64Disassembler
import org.kgen.backend.arm64.disasm.Arm64Instruction

class InstructionExtendedTest {

    // -- X86Instruction construction and text formatting --

    @Test
    fun x86NopSizeIsOne() {
        val insn = X86Instruction(0, byteArrayOf(0x90.toByte()), "nop", emptyList())
        assertEquals(1, insn.size)
    }

    @Test
    fun x86InstructionAddress() {
        val insn = X86Instruction(0xDEAD, byteArrayOf(0x90.toByte()), "nop", emptyList())
        assertEquals(0xDEADL, insn.address)
    }

    @Test
    fun x86InstructionMnemonic() {
        val insn = X86Instruction(0, byteArrayOf(0xC3.toByte()), "ret", emptyList())
        assertEquals("ret", insn.mnemonic)
    }

    @Test
    fun x86OperandsTextEmpty() {
        val insn = X86Instruction(0, byteArrayOf(0x90.toByte()), "nop", emptyList())
        assertEquals("", insn.operandsText())
    }

    @Test
    fun x86TextNoOperands() {
        val insn = X86Instruction(0, byteArrayOf(0x90.toByte()), "nop", emptyList())
        assertEquals("nop", insn.text())
    }

    @Test
    fun x86TextWithRegisterOperands() {
        val insn = X86Instruction(0, byteArrayOf(0x48.toByte(), 0x89.toByte(), 0xC1.toByte()), "mov",
            listOf(X86Operand.Register("rcx"), X86Operand.Register("rax")))
        assertEquals("mov rcx, rax", insn.text())
    }

    @Test
    fun x86IntelTextIsDefault() {
        val insn = X86Instruction(0, byteArrayOf(0x48.toByte(), 0x01.toByte(), 0xC1.toByte()), "add",
            listOf(X86Operand.Register("rcx"), X86Operand.Register("rax")))
        assertEquals(insn.intelText(), insn.text())
    }

    @Test
    fun x86AttTextReversesOperands() {
        val insn = X86Instruction(0, byteArrayOf(0x48.toByte(), 0x89.toByte(), 0xC1.toByte()), "mov",
            listOf(X86Operand.Register("rcx"), X86Operand.Register("rax")))
        val att = insn.attText()
        assertTrue(att.contains("%rax"))
        assertTrue(att.contains("%rcx"))
    }

    @Test
    fun x86ToStringContainsAddress() {
        val insn = X86Instruction(0x1000, byteArrayOf(0x90.toByte()), "nop", emptyList())
        val str = insn.toString()
        assertTrue(str.contains("1000"), "toString should contain address: $str")
    }

    // -- X86Operand types --

    @Test
    fun x86RegisterOperandText() {
        val op = X86Operand.Register("rax")
        assertEquals("rax", op.text())
        assertEquals("%rax", op.attText())
    }

    @Test
    fun x86RegisterBits64() {
        assertEquals(64, X86Operand.Register("rax").operandBits())
        assertEquals(64, X86Operand.Register("rbx").operandBits())
    }

    @Test
    fun x86RegisterBits32() {
        assertEquals(32, X86Operand.Register("eax").operandBits())
    }

    @Test
    fun x86RegisterBits8() {
        assertEquals(8, X86Operand.Register("al").operandBits())
    }

    @Test
    fun x86RegisterBits128() {
        assertEquals(128, X86Operand.Register("xmm0").operandBits())
    }

    @Test
    fun x86ImmediateText() {
        val op = X86Operand.Immediate(42, 32)
        assertEquals("0x2a", op.text())
        assertEquals("\$0x2a", op.attText())
    }

    @Test
    fun x86ImmediateNegativeText() {
        val op = X86Operand.Immediate(-1, 32)
        assertEquals("-0x1", op.text())
    }

    @Test
    fun x86MemoryWithBaseOnly() {
        val op = X86Operand.Memory(64, "rax", null, 1, 0)
        val text = op.text()
        assertTrue(text.contains("[rax]"), "Memory text: $text")
    }

    @Test
    fun x86MemoryWithDisplacement() {
        val op = X86Operand.Memory(32, "rbp", null, 1, -8)
        val text = op.text()
        assertTrue(text.contains("rbp"), "Memory text: $text")
        assertTrue(text.contains("-0x8"), "Memory text: $text")
    }

    @Test
    fun x86MemoryWithIndexAndScale() {
        val op = X86Operand.Memory(64, "rax", "rcx", 4, 0)
        val text = op.text()
        assertTrue(text.contains("rax"))
        assertTrue(text.contains("rcx*4"))
    }

    @Test
    fun x86MemoryRipRelative() {
        val op = X86Operand.Memory(64, null, null, 1, 0x10, ripRelative = true)
        val text = op.text()
        assertTrue(text.contains("rip"), "RIP-relative memory: $text")
    }

    @Test
    fun x86RelativeOperand() {
        val op = X86Operand.Relative(0x1234)
        assertEquals("0x1234", op.text())
        assertNull(op.operandBits())
    }

    // -- X86 Disassembler --

    @Test
    fun x86DisassembleNop() {
        val insns = X86Disassembler().disassembleRaw(byteArrayOf(0x90.toByte()), 0)
        assertEquals(1, insns.size)
        assertEquals("nop", insns[0].mnemonic)
    }

    @Test
    fun x86DisassembleRet() {
        val insns = X86Disassembler().disassembleRaw(byteArrayOf(0xC3.toByte()), 0x100)
        assertEquals(1, insns.size)
        assertEquals("ret", insns[0].mnemonic)
        assertEquals(0x100L, insns[0].address)
    }

    @Test
    fun x86DisassembleMultiple() {
        val code = byteArrayOf(
            0x90.toByte(),
            0x90.toByte(),
            0xC3.toByte(),
        )
        val insns = X86Disassembler().disassembleRaw(code, 0)
        assertEquals(3, insns.size)
        assertEquals(0L, insns[0].address)
        assertEquals(1L, insns[1].address)
        assertEquals(2L, insns[2].address)
    }

    @Test
    fun x86DisassembleAddressesMonotonic() {
        val code = byteArrayOf(
            0x90.toByte(),
            0x48.toByte(), 0x31.toByte(), 0xC0.toByte(),
            0xC3.toByte(),
        )
        val insns = X86Disassembler().disassembleRaw(code, 0x1000)
        for (i in 1 until insns.size) {
            assertTrue(insns[i].address > insns[i - 1].address,
                "Addresses should be monotonically increasing")
        }
    }

    @Test
    fun x86InstructionImplementsInterface() {
        val insn: Instruction = X86Instruction(0, byteArrayOf(0x90.toByte()), "nop", emptyList())
        assertEquals(0L, insn.address)
        assertEquals("nop", insn.mnemonic)
    }

    // -- ARM64 Instruction --

    @Test
    fun arm64InstructionSizeAlways4() {
        val insn = Arm64Instruction(0, "nop", "", 0xD503201F.toInt())
        assertEquals(4, insn.size)
    }

    @Test
    fun arm64InstructionBytes() {
        val insn = Arm64Instruction(0, "nop", "", 0xD503201F.toInt())
        val bytes = insn.bytes
        assertEquals(4, bytes.size)
    }

    @Test
    fun arm64TextWithOperands() {
        val insn = Arm64Instruction(0, "mov", "x0, x1", 0)
        assertEquals("mov x0, x1", insn.text())
        assertEquals("x0, x1", insn.operandsText())
    }

    @Test
    fun arm64TextNoOperands() {
        val insn = Arm64Instruction(0, "nop", "", 0)
        assertEquals("nop", insn.text())
    }

    @Test
    fun arm64DisassembleNop() {
        val code = byteArrayOf(0x1F, 0x20, 0x03, 0xD5.toByte())
        val insns = Arm64Disassembler().disassemble(code, 0)
        assertEquals(1, insns.size)
        assertEquals("nop", insns[0].mnemonic)
    }

    @Test
    fun arm64DisassembleAddressIncrementsBy4() {
        val code = byteArrayOf(
            0x1F, 0x20, 0x03, 0xD5.toByte(),
            0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte(),
        )
        val insns = Arm64Disassembler().disassemble(code, 0x2000)
        assertEquals(2, insns.size)
        assertEquals(0x2000L, insns[0].address)
        assertEquals(0x2004L, insns[1].address)
    }

    @Test
    fun x86DowncastWorks() {
        val insn: Instruction = X86Instruction(0, byteArrayOf(0x90.toByte()), "nop", emptyList())
        assertTrue(insn is X86Instruction)
    }

    @Test
    fun arm64DowncastWorks() {
        val insn: Instruction = Arm64Instruction(0, "nop", "", 0)
        assertTrue(insn is Arm64Instruction)
    }
}
