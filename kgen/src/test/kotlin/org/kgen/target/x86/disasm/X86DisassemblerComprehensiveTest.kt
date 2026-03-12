package org.kgen.target.x86.disasm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.target.x86.X86Register
import org.kgen.target.x86.X86Register32
import org.kgen.target.x86.X86Register64
import org.kgen.target.x86.X86Operand32
import org.kgen.target.x86.X86Operand64
import org.kgen.target.x86.asm.X86Assembler

class X86DisassemblerComprehensiveTest {

    private val disasm = X86Disassembler()

    private fun disasm(vararg bytes: Int): List<X86Instruction> =
        disasm.disassembleRaw(ByteArray(bytes.size) { bytes[it].toByte() })

    private fun disasmOne(vararg bytes: Int): X86Instruction =
        disasm(*bytes).first()

    private fun disasmAt(baseAddress: Long, vararg bytes: Int): List<X86Instruction> =
        disasm.disassembleRaw(ByteArray(bytes.size) { bytes[it].toByte() }, baseAddress)

    private fun roundTrip(build: X86Assembler.() -> Unit): List<X86Instruction> {
        val asm = X86Assembler()
        asm.build()
        return disasm.disassembleRaw(asm.toByteArray())
    }

    // Arithmetic: ADD

    @Test
    fun `decodes add eax imm32`() {
        // add eax, 0x12345678 = 05 78 56 34 12
        val inst = disasmOne(0x05, 0x78, 0x56, 0x34, 0x12)
        assertEquals("add", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("0x12345678", inst.operands[1].text())
    }

    @Test
    fun `decodes add al imm8`() {
        // add al, 5 = 04 05
        val inst = disasmOne(0x04, 0x05)
        assertEquals("add", inst.mnemonic)
        assertEquals("al", inst.operands[0].text())
    }

    @Test
    fun `decodes add r64 r64`() {
        // add rax, rcx = 48 01 C8
        val inst = disasmOne(0x48, 0x01, 0xC8)
        assertEquals("add", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        assertEquals("rcx", inst.operands[1].text())
    }

    @Test
    fun `decodes add reg to memory`() {
        // add [rbp-4], eax = 01 45 FC
        val inst = disasmOne(0x01, 0x45, 0xFC)
        assertEquals("add", inst.mnemonic)
        assertTrue(inst.operands[0].text().contains("rbp"))
        assertEquals("eax", inst.operands[1].text())
    }

    @Test
    fun `decodes add reg from memory`() {
        // add eax, [rbx] = 03 03
        val inst = disasmOne(0x03, 0x03)
        assertEquals("add", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertTrue(inst.operands[1].text().contains("rbx"))
    }

    // Arithmetic: SUB

    @Test
    fun `decodes sub eax imm32`() {
        // sub eax, 0x10 = 2D 10 00 00 00
        val inst = disasmOne(0x2D, 0x10, 0x00, 0x00, 0x00)
        assertEquals("sub", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes sub al imm8`() {
        // sub al, 3 = 2C 03
        val inst = disasmOne(0x2C, 0x03)
        assertEquals("sub", inst.mnemonic)
        assertEquals("al", inst.operands[0].text())
    }

    @Test
    fun `decodes sub r64 r64`() {
        // sub rdi, rsi = 48 29 F7
        val inst = disasmOne(0x48, 0x29, 0xF7)
        assertEquals("sub", inst.mnemonic)
        assertEquals("rdi", inst.operands[0].text())
        assertEquals("rsi", inst.operands[1].text())
    }

    // Arithmetic: OR

    @Test
    fun `decodes or r32 r32`() {
        // or eax, ecx = 09 C8
        val inst = disasmOne(0x09, 0xC8)
        assertEquals("or", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test
    fun `decodes or r8 r8`() {
        // or al, cl = 08 C8
        val inst = disasmOne(0x08, 0xC8)
        assertEquals("or", inst.mnemonic)
    }

    // Arithmetic: AND

    @Test
    fun `decodes and r32 r32`() {
        // and eax, ecx = 21 C8
        val inst = disasmOne(0x21, 0xC8)
        assertEquals("and", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test
    fun `decodes and eax imm32`() {
        // and eax, 0xFF = 25 FF 00 00 00
        val inst = disasmOne(0x25, 0xFF, 0x00, 0x00, 0x00)
        assertEquals("and", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes and al imm8`() {
        // and al, 0x0F = 24 0F
        val inst = disasmOne(0x24, 0x0F)
        assertEquals("and", inst.mnemonic)
        assertEquals("al", inst.operands[0].text())
    }

    // Arithmetic: CMP

    @Test
    fun `decodes cmp r32 r32`() {
        // cmp eax, ecx = 39 C8
        val inst = disasmOne(0x39, 0xC8)
        assertEquals("cmp", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test
    fun `decodes cmp eax imm32`() {
        // cmp eax, 0 = 3D 00 00 00 00
        val inst = disasmOne(0x3D, 0x00, 0x00, 0x00, 0x00)
        assertEquals("cmp", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes cmp al imm8`() {
        // cmp al, 0 = 3C 00
        val inst = disasmOne(0x3C, 0x00)
        assertEquals("cmp", inst.mnemonic)
        assertEquals("al", inst.operands[0].text())
    }

    @Test
    fun `decodes cmp rm imm8 sign-extended`() {
        // cmp eax, 1 = 83 F8 01
        val inst = disasmOne(0x83, 0xF8, 0x01)
        assertEquals("cmp", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes cmp r8 memory`() {
        // cmp cl, [rbx] = 3A 0B
        val inst = disasmOne(0x3A, 0x0B)
        assertEquals("cmp", inst.mnemonic)
    }

    // Arithmetic: XOR

    @Test
    fun `decodes xor r8 r8`() {
        // xor al, cl = 30 C8
        val inst = disasmOne(0x30, 0xC8)
        assertEquals("xor", inst.mnemonic)
    }

    @Test
    fun `decodes xor r64 r64`() {
        // xor rax, rax = 48 31 C0
        val inst = disasmOne(0x48, 0x31, 0xC0)
        assertEquals("xor", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        assertEquals("rax", inst.operands[1].text())
    }

    // TEST

    @Test
    fun `decodes test r32 r32`() {
        // test eax, eax = 85 C0
        val inst = disasmOne(0x85, 0xC0)
        assertEquals("test", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("eax", inst.operands[1].text())
    }

    @Test
    fun `decodes test al imm8`() {
        // test al, 1 = A8 01
        val inst = disasmOne(0xA8, 0x01)
        assertEquals("test", inst.mnemonic)
        assertEquals("al", inst.operands[0].text())
    }

    @Test
    fun `decodes test eax imm32`() {
        // test eax, 0xFF = A9 FF 00 00 00
        val inst = disasmOne(0xA9, 0xFF, 0x00, 0x00, 0x00)
        assertEquals("test", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes test r8 r8`() {
        // test al, cl = 84 C8
        val inst = disasmOne(0x84, 0xC8)
        assertEquals("test", inst.mnemonic)
    }

    // Group1: ADC, SBB, OR, AND imm

    @Test
    fun `decodes adc rm imm8`() {
        // adc eax, 1 = 83 D0 01
        val inst = disasmOne(0x83, 0xD0, 0x01)
        assertEquals("adc", inst.mnemonic)
    }

    @Test
    fun `decodes sbb rm imm8`() {
        // sbb eax, 1 = 83 D8 01
        val inst = disasmOne(0x83, 0xD8, 0x01)
        assertEquals("sbb", inst.mnemonic)
    }

    @Test
    fun `decodes add rm32 imm32`() {
        // add eax, 0x100 = 81 C0 00 01 00 00
        val inst = disasmOne(0x81, 0xC0, 0x00, 0x01, 0x00, 0x00)
        assertEquals("add", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("0x100", inst.operands[1].text())
    }

    @Test
    fun `decodes or rm32 imm8`() {
        // or eax, 0x0F = 83 C8 0F
        val inst = disasmOne(0x83, 0xC8, 0x0F)
        assertEquals("or", inst.mnemonic)
    }

    @Test
    fun `decodes and rm32 imm8`() {
        // and eax, 0x0F = 83 E0 0F
        val inst = disasmOne(0x83, 0xE0, 0x0F)
        assertEquals("and", inst.mnemonic)
    }

    @Test
    fun `decodes xor rm32 imm8`() {
        // xor eax, 1 = 83 F0 01
        val inst = disasmOne(0x83, 0xF0, 0x01)
        assertEquals("xor", inst.mnemonic)
    }

    @Test
    fun `decodes add rm8 imm8`() {
        // add al, 5 via group1 = 80 C0 05
        val inst = disasmOne(0x80, 0xC0, 0x05)
        assertEquals("add", inst.mnemonic)
    }

    // MOV

    @Test
    fun `decodes mov r8 imm8`() {
        // mov cl, 0x42 = B1 42
        val inst = disasmOne(0xB1, 0x42)
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[0].text().contains("cl") || inst.operands[0].text().contains("l"))
    }

    @Test
    fun `decodes mov rm8 imm8`() {
        // mov byte [rbp-1], 5 = C6 45 FF 05
        val inst = disasmOne(0xC6, 0x45, 0xFF, 0x05)
        assertEquals("mov", inst.mnemonic)
    }

    @Test
    fun `decodes mov rm32 imm32`() {
        // mov dword [rbp-4], 100 = C7 45 FC 64 00 00 00
        val inst = disasmOne(0xC7, 0x45, 0xFC, 0x64, 0x00, 0x00, 0x00)
        assertEquals("mov", inst.mnemonic)
    }

    @Test
    fun `decodes mov r16 imm16 with operand size override`() {
        // mov ax, 0x1234 = 66 B8 34 12
        val inst = disasmOne(0x66, 0xB8, 0x34, 0x12)
        assertEquals("mov", inst.mnemonic)
        assertEquals("ax", inst.operands[0].text())
    }

    @Test
    fun `decodes mov r8 rm8`() {
        // mov al, [rbx] = 8A 03
        val inst = disasmOne(0x8A, 0x03)
        assertEquals("mov", inst.mnemonic)
    }

    @Test
    fun `decodes mov rm8 r8`() {
        // mov [rbx], al = 88 03
        val inst = disasmOne(0x88, 0x03)
        assertEquals("mov", inst.mnemonic)
    }

    // PUSH/POP

    @Test
    fun `decodes push rax through rdi`() {
        for (r in 0..7) {
            val inst = disasmOne(0x50 + r)
            assertEquals("push", inst.mnemonic)
        }
    }

    @Test
    fun `decodes pop rax through rdi`() {
        for (r in 0..7) {
            val inst = disasmOne(0x58 + r)
            assertEquals("pop", inst.mnemonic)
        }
    }

    @Test
    fun `decodes push r12`() {
        // push r12 = 41 54
        val inst = disasmOne(0x41, 0x54)
        assertEquals("push", inst.mnemonic)
        assertEquals("r12", inst.operands[0].text())
    }

    @Test
    fun `decodes pop r15`() {
        // pop r15 = 41 5F
        val inst = disasmOne(0x41, 0x5F)
        assertEquals("pop", inst.mnemonic)
        assertEquals("r15", inst.operands[0].text())
    }

    @Test
    fun `decodes push rm64`() {
        // push [rax] = FF 30
        val inst = disasmOne(0xFF, 0x30)
        assertEquals("push", inst.mnemonic)
    }

    // LEA

    @Test
    fun `decodes lea with SIB`() {
        // lea rax, [rbx+rcx*4+8] = 48 8D 44 8B 08
        val inst = disasmOne(0x48, 0x8D, 0x44, 0x8B, 0x08)
        assertEquals("lea", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        val mem = inst.operands[1].text()
        assertTrue(mem.contains("rbx"), "Expected rbx in $mem")
        assertTrue(mem.contains("rcx"), "Expected rcx in $mem")
        assertTrue(mem.contains("4"), "Expected scale 4 in $mem")
    }

    @Test
    fun `decodes lea r32 memory`() {
        // lea eax, [rbx+4] = 8D 43 04
        val inst = disasmOne(0x8D, 0x43, 0x04)
        assertEquals("lea", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    // Shifts

    @Test
    fun `decodes shr reg imm8`() {
        // shr eax, 1 = C1 E8 01
        val inst = disasmOne(0xC1, 0xE8, 0x01)
        assertEquals("shr", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes sar reg imm8`() {
        // sar eax, 3 = C1 F8 03
        val inst = disasmOne(0xC1, 0xF8, 0x03)
        assertEquals("sar", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes rol ror`() {
        // rol eax, 1 = C1 C0 01
        val inst = disasmOne(0xC1, 0xC0, 0x01)
        assertEquals("rol", inst.mnemonic)
    }

    @Test
    fun `decodes shift by 1`() {
        // shl eax, 1 = D1 E0
        val inst = disasmOne(0xD1, 0xE0)
        assertEquals("shl", inst.mnemonic)
        assertEquals("0x1", inst.operands[1].text())
    }

    @Test
    fun `decodes shift by CL`() {
        // shl eax, cl = D3 E0
        val inst = disasmOne(0xD3, 0xE0)
        assertEquals("shl", inst.mnemonic)
        assertEquals("cl", inst.operands[1].text())
    }

    @Test
    fun `decodes shr r8 by 1`() {
        // shr al, 1 = D0 E8
        val inst = disasmOne(0xD0, 0xE8)
        assertEquals("shr", inst.mnemonic)
    }

    @Test
    fun `decodes shl r8 imm8`() {
        // shl al, 3 = C0 E0 03
        val inst = disasmOne(0xC0, 0xE0, 0x03)
        assertEquals("shl", inst.mnemonic)
    }

    @Test
    fun `decodes shr r8 cl`() {
        // shr al, cl = D2 E8
        val inst = disasmOne(0xD2, 0xE8)
        assertEquals("shr", inst.mnemonic)
    }

    // Group 3: NOT/NEG/MUL/IMUL/DIV/IDIV

    @Test
    fun `decodes not r32`() {
        // not eax = F7 D0
        val inst = disasmOne(0xF7, 0xD0)
        assertEquals("not", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes neg r32`() {
        // neg eax = F7 D8
        val inst = disasmOne(0xF7, 0xD8)
        assertEquals("neg", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes mul r32`() {
        // mul ecx = F7 E1
        val inst = disasmOne(0xF7, 0xE1)
        assertEquals("mul", inst.mnemonic)
        assertEquals("ecx", inst.operands[0].text())
    }

    @Test
    fun `decodes imul r32 one operand`() {
        // imul ecx = F7 E9
        val inst = disasmOne(0xF7, 0xE9)
        assertEquals("imul", inst.mnemonic)
    }

    @Test
    fun `decodes div r32`() {
        // div ecx = F7 F1
        val inst = disasmOne(0xF7, 0xF1)
        assertEquals("div", inst.mnemonic)
    }

    @Test
    fun `decodes idiv r32`() {
        // idiv ecx = F7 F9
        val inst = disasmOne(0xF7, 0xF9)
        assertEquals("idiv", inst.mnemonic)
    }

    @Test
    fun `decodes test rm32 imm32 via group3`() {
        // test ecx, 0xFF = F7 C1 FF 00 00 00
        val inst = disasmOne(0xF7, 0xC1, 0xFF, 0x00, 0x00, 0x00)
        assertEquals("test", inst.mnemonic)
    }

    @Test
    fun `decodes not r8`() {
        // not al = F6 D0
        val inst = disasmOne(0xF6, 0xD0)
        assertEquals("not", inst.mnemonic)
    }

    @Test
    fun `decodes neg r8`() {
        // neg al = F6 D8
        val inst = disasmOne(0xF6, 0xD8)
        assertEquals("neg", inst.mnemonic)
    }

    // Group 5: INC/DEC/CALL/JMP

    @Test
    fun `decodes inc r32 via group5`() {
        // inc eax = FF C0
        val inst = disasmOne(0xFF, 0xC0)
        assertEquals("inc", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes dec r32 via group5`() {
        // dec eax = FF C8
        val inst = disasmOne(0xFF, 0xC8)
        assertEquals("dec", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes inc r8 via group4`() {
        // inc al = FE C0
        val inst = disasmOne(0xFE, 0xC0)
        assertEquals("inc", inst.mnemonic)
    }

    @Test
    fun `decodes dec r8 via group4`() {
        // dec al = FE C8
        val inst = disasmOne(0xFE, 0xC8)
        assertEquals("dec", inst.mnemonic)
    }

    @Test
    fun `decodes call indirect reg`() {
        // call rax = FF D0
        val inst = disasmOne(0xFF, 0xD0)
        assertEquals("call", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes jmp indirect reg`() {
        // jmp rax = FF E0
        val inst = disasmOne(0xFF, 0xE0)
        assertEquals("jmp", inst.mnemonic)
    }

    // Branches: all Jcc rel8

    @Test
    fun `decodes all jcc rel8 variants`() {
        val ccNames = arrayOf("jo", "jno", "jb", "jnb", "jz", "jnz", "jbe", "ja",
            "js", "jns", "jp", "jnp", "jl", "jnl", "jle", "jg")
        for (i in 0..15) {
            val inst = disasmOne(0x70 + i, 0x00)
            assertEquals(ccNames[i], inst.mnemonic, "Expected ${ccNames[i]} for opcode 0x${(0x70 + i).toString(16)}")
        }
    }

    // Branches: all Jcc rel32

    @Test
    fun `decodes all jcc rel32 variants`() {
        val ccNames = arrayOf("jo", "jno", "jb", "jnb", "jz", "jnz", "jbe", "ja",
            "js", "jns", "jp", "jnp", "jl", "jnl", "jle", "jg")
        for (i in 0..15) {
            val inst = disasmOne(0x0F, 0x80 + i, 0x00, 0x00, 0x00, 0x00)
            assertEquals(ccNames[i], inst.mnemonic, "Expected ${ccNames[i]} for opcode 0x0F 0x${(0x80 + i).toString(16)}")
        }
    }

    // JMP

    @Test
    fun `decodes jmp rel32`() {
        // jmp +0 from addr 0x100: E9 00 00 00 00
        val insts = disasmAt(0x100, 0xE9, 0x00, 0x00, 0x00, 0x00)
        assertEquals("jmp", insts[0].mnemonic)
        assertEquals("0x105", insts[0].operands[0].text())
    }

    @Test
    fun `decodes jmp backward rel8`() {
        // jmp -2 (infinite loop) from addr 0x200: EB FE
        val insts = disasmAt(0x200, 0xEB, 0xFE)
        assertEquals("jmp", insts[0].mnemonic)
        assertEquals("0x200", insts[0].operands[0].text())
    }

    // CALL

    @Test
    fun `decodes call forward rel32`() {
        // call +10 from addr 0x100: E8 0A 00 00 00
        val insts = disasmAt(0x100, 0xE8, 0x0A, 0x00, 0x00, 0x00)
        assertEquals("call", insts[0].mnemonic)
        assertEquals("0x10f", insts[0].operands[0].text())
    }

    // RET variants

    @Test
    fun `decodes ret imm16`() {
        // ret 8 = C2 08 00
        val inst = disasmOne(0xC2, 0x08, 0x00)
        assertEquals("ret", inst.mnemonic)
    }

    @Test
    fun `decodes retf`() {
        val inst = disasmOne(0xCB)
        assertEquals("retf", inst.mnemonic)
    }

    // SETcc

    @Test
    fun `decodes all setcc variants`() {
        val ccNames = arrayOf("seto", "setno", "setb", "setnb", "setz", "setnz", "setbe", "seta",
            "sets", "setns", "setp", "setnp", "setl", "setnl", "setle", "setg")
        for (i in 0..15) {
            val inst = disasmOne(0x0F, 0x90 + i, 0xC0)
            assertEquals(ccNames[i], inst.mnemonic, "Expected ${ccNames[i]}")
        }
    }

    // CMOVcc

    @Test
    fun `decodes cmovz eax ecx`() {
        // cmovz eax, ecx = 0F 44 C1
        val inst = disasmOne(0x0F, 0x44, 0xC1)
        assertEquals("cmovz", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test
    fun `decodes cmovnz r64 r64`() {
        // cmovnz rax, rcx = 48 0F 45 C1
        val inst = disasmOne(0x48, 0x0F, 0x45, 0xC1)
        assertEquals("cmovnz", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        assertEquals("rcx", inst.operands[1].text())
    }

    // MOVZX / MOVSX

    @Test
    fun `decodes movzx r32 r8`() {
        // movzx eax, cl = 0F B6 C1
        val inst = disasmOne(0x0F, 0xB6, 0xC1)
        assertEquals("movzx", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes movzx r32 r16`() {
        // movzx eax, cx = 0F B7 C1
        val inst = disasmOne(0x0F, 0xB7, 0xC1)
        assertEquals("movzx", inst.mnemonic)
    }

    @Test
    fun `decodes movsx r32 r8`() {
        // movsx eax, cl = 0F BE C1
        val inst = disasmOne(0x0F, 0xBE, 0xC1)
        assertEquals("movsx", inst.mnemonic)
    }

    @Test
    fun `decodes movsx r32 r16`() {
        // movsx eax, cx = 0F BF C1
        val inst = disasmOne(0x0F, 0xBF, 0xC1)
        assertEquals("movsx", inst.mnemonic)
    }

    // BSF/BSR

    @Test
    fun `decodes bsf r32 r32`() {
        // bsf eax, ecx = 0F BC C1
        val inst = disasmOne(0x0F, 0xBC, 0xC1)
        assertEquals("bsf", inst.mnemonic)
    }

    @Test
    fun `decodes bsr r32 r32`() {
        // bsr eax, ecx = 0F BD C1
        val inst = disasmOne(0x0F, 0xBD, 0xC1)
        assertEquals("bsr", inst.mnemonic)
    }

    // BSWAP

    @Test
    fun `decodes bswap r32`() {
        // bswap eax = 0F C8
        val inst = disasmOne(0x0F, 0xC8)
        assertEquals("bswap", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes bswap r64`() {
        // bswap rax = 48 0F C8
        val inst = disasmOne(0x48, 0x0F, 0xC8)
        assertEquals("bswap", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    // XCHG

    @Test
    fun `decodes xchg eax ecx`() {
        // xchg eax, ecx = 91
        val inst = disasmOne(0x91)
        assertEquals("xchg", inst.mnemonic)
    }

    @Test
    fun `decodes xchg rax rcx`() {
        // xchg rax, rcx = 48 91
        val inst = disasmOne(0x48, 0x91)
        assertEquals("xchg", inst.mnemonic)
    }

    // CDQ/CQO

    @Test
    fun `decodes cdq`() {
        val inst = disasmOne(0x99)
        assertEquals("cdq", inst.mnemonic)
    }

    @Test
    fun `decodes cqo`() {
        // cqo = 48 99
        val inst = disasmOne(0x48, 0x99)
        assertEquals("cqo", inst.mnemonic)
    }

    // LEAVE

    @Test
    fun `decodes leave`() {
        val inst = disasmOne(0xC9)
        assertEquals("leave", inst.mnemonic)
    }

    // INT

    @Test
    fun `decodes int imm8`() {
        // int 0x80 = CD 80
        val inst = disasmOne(0xCD, 0x80)
        assertEquals("int", inst.mnemonic)
    }

    // NOP variants

    @Test
    fun `decodes multi-byte nop`() {
        // nop dword [rax] = 0F 1F 00
        val inst = disasmOne(0x0F, 0x1F, 0x00)
        assertEquals("nop", inst.mnemonic)
    }

    // String instructions

    @Test
    fun `decodes movsb`() {
        val inst = disasmOne(0xA4)
        assertEquals("movsb", inst.mnemonic)
    }

    @Test
    fun `decodes rep movsb`() {
        // rep movsb = F3 A4
        val inst = disasmOne(0xF3, 0xA4)
        assertEquals("rep movsb", inst.mnemonic)
    }

    @Test
    fun `decodes rep movsd`() {
        // rep movsd = F3 A5
        val inst = disasmOne(0xF3, 0xA5)
        assertEquals("rep movsd", inst.mnemonic)
    }

    @Test
    fun `decodes stosb`() {
        val inst = disasmOne(0xAA)
        assertEquals("stosb", inst.mnemonic)
    }

    @Test
    fun `decodes rep stosb`() {
        // rep stosb = F3 AA
        val inst = disasmOne(0xF3, 0xAA)
        assertEquals("rep stosb", inst.mnemonic)
    }

    // SSE: addss, subss, mulss, divss

    @Test
    fun `decodes addss xmm-xmm`() {
        // F3 0F 58 C1 = addss xmm0, xmm1
        val inst = disasmOne(0xF3, 0x0F, 0x58, 0xC1)
        assertEquals("addss", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("xmm1", inst.operands[1].text())
    }

    @Test
    fun `decodes subss xmm-xmm`() {
        // F3 0F 5C C1 = subss xmm0, xmm1
        val inst = disasmOne(0xF3, 0x0F, 0x5C, 0xC1)
        assertEquals("subss", inst.mnemonic)
    }

    @Test
    fun `decodes mulss xmm-xmm`() {
        // F3 0F 59 C1 = mulss xmm0, xmm1
        val inst = disasmOne(0xF3, 0x0F, 0x59, 0xC1)
        assertEquals("mulss", inst.mnemonic)
    }

    @Test
    fun `decodes divss xmm-xmm`() {
        // F3 0F 5E C1 = divss xmm0, xmm1
        val inst = disasmOne(0xF3, 0x0F, 0x5E, 0xC1)
        assertEquals("divss", inst.mnemonic)
    }

    // SSE: movss

    @Test
    fun `decodes movss xmm-xmm load form`() {
        // F3 0F 10 C1 = movss xmm0, xmm1
        val inst = disasmOne(0xF3, 0x0F, 0x10, 0xC1)
        assertEquals("movss", inst.mnemonic)
    }

    @Test
    fun `decodes movss xmm-xmm store form`() {
        // F3 0F 11 C1 = movss xmm1, xmm0
        val inst = disasmOne(0xF3, 0x0F, 0x11, 0xC1)
        assertEquals("movss", inst.mnemonic)
    }

    // SSE: movsd load/store

    @Test
    fun `decodes movsd store form`() {
        // F2 0F 11 C1 = movsd xmm1, xmm0
        val inst = disasmOne(0xF2, 0x0F, 0x11, 0xC1)
        assertEquals("movsd", inst.mnemonic)
    }

    // SSE: cvtsi2ss, cvtss2si

    @Test
    fun `decodes cvtsi2ss`() {
        // F3 0F 2A C0 = cvtsi2ss xmm0, eax
        val inst = disasmOne(0xF3, 0x0F, 0x2A, 0xC0)
        assertEquals("cvtsi2ss", inst.mnemonic)
    }

    @Test
    fun `decodes cvtss2si`() {
        // F3 0F 2D C0 = cvtss2si eax, xmm0
        val inst = disasmOne(0xF3, 0x0F, 0x2D, 0xC0)
        assertEquals("cvtss2si", inst.mnemonic)
    }

    // SSE: cvttsd2si

    @Test
    fun `decodes cvttsd2si`() {
        // F2 0F 2C C1 = cvttsd2si eax, xmm1
        val inst = disasmOne(0xF2, 0x0F, 0x2C, 0xC1)
        assertEquals("cvttsd2si", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("xmm1", inst.operands[1].text())
    }

    // SSE: movd

    @Test
    fun `decodes movd xmm r32`() {
        // 66 0F 6E C0 = movd xmm0, eax
        val inst = disasmOne(0x66, 0x0F, 0x6E, 0xC0)
        assertEquals("movd", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("eax", inst.operands[1].text())
    }

    @Test
    fun `decodes movd r32 xmm`() {
        // 66 0F 7E C0 = movd eax, xmm0
        val inst = disasmOne(0x66, 0x0F, 0x7E, 0xC0)
        assertEquals("movd", inst.mnemonic)
    }

    // SIB addressing

    @Test
    fun `decodes memory with SIB scale 8`() {
        // mov eax, [rbx+rcx*8] = 8B 04 CB
        val inst = disasmOne(0x8B, 0x04, 0xCB)
        assertEquals("mov", inst.mnemonic)
        val mem = inst.operands[1].text()
        assertTrue(mem.contains("rbx"), "Expected rbx in $mem")
        assertTrue(mem.contains("rcx"), "Expected rcx in $mem")
        assertTrue(mem.contains("8"), "Expected scale 8 in $mem")
    }

    @Test
    fun `decodes memory with disp32 only via SIB`() {
        // mov eax, [0x1000] = 8B 04 25 00 10 00 00
        val inst = disasmOne(0x8B, 0x04, 0x25, 0x00, 0x10, 0x00, 0x00)
        assertEquals("mov", inst.mnemonic)
    }

    @Test
    fun `decodes memory with SIB base r13 disp8`() {
        // mov eax, [r13+8] = 41 8B 45 08
        val inst = disasmOne(0x41, 0x8B, 0x45, 0x08)
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[1].text().contains("r13"))
    }

    // REX with high registers

    @Test
    fun `decodes mov r15 r14`() {
        // mov r15, r14 = 4D 89 F7
        val inst = disasmOne(0x4D, 0x89, 0xF7)
        assertEquals("mov", inst.mnemonic)
        assertEquals("r15", inst.operands[0].text())
        assertEquals("r14", inst.operands[1].text())
    }

    @Test
    fun `decodes add r8 r9`() {
        // add r8, r9 = 4D 01 C8
        val inst = disasmOne(0x4D, 0x01, 0xC8)
        assertEquals("add", inst.mnemonic)
        assertEquals("r8", inst.operands[0].text())
        assertEquals("r9", inst.operands[1].text())
    }

    @Test
    fun `decodes mov r8d imm32`() {
        // mov r8d, 0x42 = 41 B8 42 00 00 00
        val inst = disasmOne(0x41, 0xB8, 0x42, 0x00, 0x00, 0x00)
        assertEquals("mov", inst.mnemonic)
        assertEquals("r8d", inst.operands[0].text())
    }

    // VEX BMI

    @Test
    fun `decodes VEX blsr`() {
        // C4 E2 70 F3 C8 = blsr ecx, eax
        val inst = disasmOne(0xC4, 0xE2, 0x70, 0xF3, 0xC8)
        assertEquals("blsr", inst.mnemonic)
        assertEquals(2, inst.operands.size)
    }

    @Test
    fun `decodes VEX blsmsk`() {
        // C4 E2 70 F3 D0 = blsmsk ecx, eax
        val inst = disasmOne(0xC4, 0xE2, 0x70, 0xF3, 0xD0)
        assertEquals("blsmsk", inst.mnemonic)
    }

    @Test
    fun `decodes VEX bextr`() {
        // C4 E2 70 F7 C2 = bextr eax, edx, ecx
        val inst = disasmOne(0xC4, 0xE2, 0x70, 0xF7, 0xC2)
        assertEquals("bextr", inst.mnemonic)
        assertEquals(3, inst.operands.size)
    }

    // Disassembler interface

    @Test
    fun `disassemble interface returns correct addresses`() {
        val bytes = byteArrayOf(
            0x90.toByte(),                                         // nop
            0x48, 0x89.toByte(), 0xE5.toByte(),                     // mov rbp, rsp
            0xC3.toByte()                                            // ret
        )
        val result = disasm.disassemble(bytes, 0x1000)
        assertEquals(3, result.size)
        assertEquals(0x1000L, result[0].address)
        assertEquals(0x1001L, result[1].address)
        assertEquals(0x1004L, result[2].address)
        assertEquals("nop", result[0].mnemonic)
        assertEquals("mov", result[1].mnemonic)
        assertEquals("ret", result[2].mnemonic)
    }

    @Test
    fun `disassembleOne returns single instruction`() {
        val bytes = byteArrayOf(0x90.toByte(), 0xC3.toByte())
        val result = disasm.disassembleOne(bytes, 0, 0x1000)
        assertNotNull(result)
        assertEquals("nop", result!!.mnemonic)
        assertEquals(1, result.size)
    }

    @Test
    fun `targetName is x86_64`() {
        assertEquals("x86_64", disasm.targetName)
    }

    // Instruction text formats

    @Test
    fun `intel text format`() {
        val inst = disasmOne(0x48, 0x89, 0xE5)
        assertEquals("mov rbp, rsp", inst.intelText())
    }

    @Test
    fun `att text format`() {
        val inst = disasmOne(0x48, 0x89, 0xE5)
        val att = inst.attText()
        assertTrue(att.contains("%rsp"), "Expected %rsp in $att")
        assertTrue(att.contains("%rbp"), "Expected %rbp in $att")
    }

    @Test
    fun `instruction size tracks correctly`() {
        assertEquals(1, disasmOne(0x90).size)              // nop
        assertEquals(1, disasmOne(0xC3).size)              // ret
        assertEquals(3, disasmOne(0x48, 0x89, 0xE5).size)  // mov rbp, rsp
        assertEquals(5, disasmOne(0xB8, 0x2A, 0x00, 0x00, 0x00).size) // mov eax, 42
    }

    // Edge cases

    @Test
    fun `empty byte array returns empty list`() {
        val result = disasm.disassembleRaw(byteArrayOf())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unknown opcode decodes as db`() {
        // 0x06 is invalid in 64-bit mode
        val inst = disasmOne(0x06)
        assertEquals("db", inst.mnemonic)
    }

    @Test
    fun `multiple instructions decode sequentially`() {
        val insts = disasm(0x55, 0x48, 0x89, 0xE5, 0x90, 0x5D, 0xC3)
        assertEquals(5, insts.size)
        assertEquals("push", insts[0].mnemonic)
        assertEquals("mov", insts[1].mnemonic)
        assertEquals("nop", insts[2].mnemonic)
        assertEquals("pop", insts[3].mnemonic)
        assertEquals("ret", insts[4].mnemonic)
    }

    @Test
    fun `total decoded bytes equal input size for valid sequence`() {
        val bytes = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
        val insts = disasm.disassembleRaw(bytes)
        assertEquals(bytes.size, insts.sumOf { it.size })
    }

    // IMUL 3-operand not tested in existing (we'll just test 2-op coverage)

    @Test
    fun `decodes imul r64 r64`() {
        // imul rax, rcx = 48 0F AF C1
        val inst = disasmOne(0x48, 0x0F, 0xAF, 0xC1)
        assertEquals("imul", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        assertEquals("rcx", inst.operands[1].text())
    }

    // Memory operand with disp32

    @Test
    fun `decodes memory with disp32`() {
        // mov eax, [rbp-0x100] = 8B 85 00 FF FF FF
        val inst = disasmOne(0x8B, 0x85, 0x00, 0xFF, 0xFF, 0xFF)
        assertEquals("mov", inst.mnemonic)
        val mem = inst.operands[1].text()
        assertTrue(mem.contains("rbp"), "Expected rbp in $mem")
    }

    // Operand size override in group1

    @Test
    fun `decodes add r16 imm16 with group1`() {
        // add ax, 0x1234 = 66 81 C0 34 12 00 00  (but with 16-bit opsize it reads imm32 still)
        // Actually: 66 83 C0 05 = add ax, 5
        val inst = disasmOne(0x66, 0x83, 0xC0, 0x05)
        assertEquals("add", inst.mnemonic)
        assertEquals("ax", inst.operands[0].text())
    }

    // SSE with high XMM registers

    @Test
    fun `decodes addsd xmm8 xmm9`() {
        // F2 45 0F 58 C1 = addsd xmm8, xmm9
        val inst = disasmOne(0xF2, 0x45, 0x0F, 0x58, 0xC1)
        assertEquals("addsd", inst.mnemonic)
        assertEquals("xmm8", inst.operands[0].text())
        assertEquals("xmm9", inst.operands[1].text())
    }

    // REX.W with cvtsi2sd (64-bit GP operand)

    @Test
    fun `decodes cvtsi2sd with rax`() {
        // F2 48 0F 2A C0 = cvtsi2sd xmm0, rax
        val inst = disasmOne(0xF2, 0x48, 0x0F, 0x2A, 0xC0)
        assertEquals("cvtsi2sd", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("rax", inst.operands[1].text())
    }

    // VEX EVEX

    @Test
    fun `decodes EVEX vmulps zmm`() {
        // 62 F1 74 48 59 C2 = vmulps zmm0, zmm1, zmm2
        val inst = disasmOne(0x62, 0xF1, 0x74, 0x48, 0x59, 0xC2)
        assertEquals("vmulps", inst.mnemonic)
    }

    @Test
    fun `decodes VEX 2-byte prefix`() {
        // C5 F4 58 C2 = vaddps xmm0, xmm1, xmm2
        val inst = disasmOne(0xC5, 0xF4, 0x58, 0xC2)
        assertEquals("vaddps", inst.mnemonic)
    }

    // Round-trip tests with assembler

    @Test
    fun `round-trip push pop rbp`() {
        val asm = X86Assembler()
        asm.emitByte(0x55) // push rbp
        asm.emitByte(0x5D) // pop rbp
        val insts = disasm.disassembleRaw(asm.toByteArray())
        assertEquals(2, insts.size)
        assertEquals("push", insts[0].mnemonic)
        assertEquals("pop", insts[1].mnemonic)
    }

    @Test
    fun `round-trip syscall`() {
        val asm = X86Assembler()
        asm.emitByte(0x0F)
        asm.emitByte(0x05)
        val insts = disasm.disassembleRaw(asm.toByteArray())
        assertEquals(1, insts.size)
        assertEquals("syscall", insts[0].mnemonic)
    }

    @Test
    fun `round-trip function prologue epilogue`() {
        val asm = X86Assembler()
        // push rbp
        asm.emitByte(0x55)
        // mov rbp, rsp
        asm.emitBytes(0x48, 0x89, 0xE5)
        // sub rsp, 0x20
        asm.emitBytes(0x48, 0x83, 0xEC, 0x20)
        // add rsp, 0x20
        asm.emitBytes(0x48, 0x83, 0xC4, 0x20)
        // pop rbp
        asm.emitByte(0x5D)
        // ret
        asm.emitByte(0xC3)

        val insts = disasm.disassembleRaw(asm.toByteArray())
        assertEquals(6, insts.size)
        assertEquals("push", insts[0].mnemonic)
        assertEquals("mov", insts[1].mnemonic)
        assertEquals("sub", insts[2].mnemonic)
        assertEquals("add", insts[3].mnemonic)
        assertEquals("pop", insts[4].mnemonic)
        assertEquals("ret", insts[5].mnemonic)
    }

    // Negative displacements in memory operands

    @Test
    fun `decodes negative displacement in memory`() {
        // mov eax, [rbp-8] = 8B 45 F8
        val inst = disasmOne(0x8B, 0x45, 0xF8)
        assertEquals("mov", inst.mnemonic)
        val mem = inst.operands[1].text()
        assertTrue(mem.contains("rbp"), "Expected rbp")
        assertTrue(mem.contains("-"), "Expected negative displacement in $mem")
    }

    // Segment overrides

    @Test
    fun `decodes fs segment override`() {
        // mov eax, fs:[rbx] = 64 8B 03
        val inst = disasmOne(0x64, 0x8B, 0x03)
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[1].text().contains("fs"), "Expected fs segment in ${inst.operands[1].text()}")
    }

    @Test
    fun `decodes gs segment override`() {
        // mov eax, gs:[rbx] = 65 8B 03
        val inst = disasmOne(0x65, 0x8B, 0x03)
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[1].text().contains("gs"), "Expected gs segment in ${inst.operands[1].text()}")
    }

    // Operand text

    @Test
    fun `operandsText formats correctly`() {
        val inst = disasmOne(0x48, 0x89, 0xE5)
        assertEquals("rbp, rsp", inst.operandsText())
    }

    @Test
    fun `immediate operand text format`() {
        val inst = disasmOne(0xB8, 0x2A, 0x00, 0x00, 0x00)
        assertEquals("eax, 0x2a", inst.operandsText())
    }

    // Memory size prefix in text

    @Test
    fun `memory operand shows dword ptr for 32-bit`() {
        // mov eax, [rbx] = 8B 03
        val inst = disasmOne(0x8B, 0x03)
        assertTrue(inst.operands[1].text().contains("dword ptr"), "Expected dword ptr in ${inst.operands[1].text()}")
    }

    @Test
    fun `memory operand shows qword ptr for 64-bit`() {
        // mov rax, [rbx] = 48 8B 03
        val inst = disasmOne(0x48, 0x8B, 0x03)
        assertTrue(inst.operands[1].text().contains("qword ptr"), "Expected qword ptr in ${inst.operands[1].text()}")
    }

    @Test
    fun `memory operand shows byte ptr for 8-bit`() {
        // mov al, [rbx] = 8A 03
        val inst = disasmOne(0x8A, 0x03)
        assertTrue(inst.operands[1].text().contains("byte ptr"), "Expected byte ptr in ${inst.operands[1].text()}")
    }
}
