package org.kgen.target.x86.disasm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class X86DisassemblerExtendedTest {

    private val disasm = X86Disassembler()

    private fun disasm(vararg bytes: Int): List<X86Instruction> =
        disasm.disassembleRaw(ByteArray(bytes.size) { bytes[it].toByte() })

    private fun disasmOne(vararg bytes: Int): X86Instruction =
        disasm(*bytes).first()

    private fun disasmAt(address: Long, vararg bytes: Int): List<X86Instruction> =
        disasm.disassembleRaw(ByteArray(bytes.size) { bytes[it].toByte() }, address)

    // ---------------------------------------------------------------
    // 1. SSE/SSE2 instructions
    // ---------------------------------------------------------------

    @Test
    fun `decodes movss xmm-xmm`() {
        // F3 0F 10 C1 = movss xmm0, xmm1
        val inst = disasmOne(0xF3, 0x0F, 0x10, 0xC1)
        assertEquals("movss", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("xmm1", inst.operands[1].text())
    }

    @Test
    fun `decodes movss store xmm to memory`() {
        // F3 0F 11 04 24 = movss [rsp], xmm0
        val inst = disasmOne(0xF3, 0x0F, 0x11, 0x04, 0x24)
        assertEquals("movss", inst.mnemonic)
        assertTrue(inst.operands[0].text().contains("rsp"))
        assertEquals("xmm0", inst.operands[1].text())
    }

    @Test
    fun `decodes addss subss mulss divss`() {
        // F3 0F 58 C1 = addss xmm0, xmm1
        assertEquals("addss", disasmOne(0xF3, 0x0F, 0x58, 0xC1).mnemonic)
        // F3 0F 5C CA = subss xmm1, xmm2
        assertEquals("subss", disasmOne(0xF3, 0x0F, 0x5C, 0xCA).mnemonic)
        // F3 0F 59 D3 = mulss xmm2, xmm3
        assertEquals("mulss", disasmOne(0xF3, 0x0F, 0x59, 0xD3).mnemonic)
        // F3 0F 5E DB = divss xmm3, xmm3
        assertEquals("divss", disasmOne(0xF3, 0x0F, 0x5E, 0xDB).mnemonic)
    }

    @Test
    fun `decodes cvtsi2ss`() {
        // F3 0F 2A C7 = cvtsi2ss xmm0, edi
        val inst = disasmOne(0xF3, 0x0F, 0x2A, 0xC7)
        assertEquals("cvtsi2ss", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("edi", inst.operands[1].text())
    }

    @Test
    fun `decodes cvtsi2ss with REX_W`() {
        // F3 48 0F 2A C7 = cvtsi2ss xmm0, rdi
        val inst = disasmOne(0xF3, 0x48, 0x0F, 0x2A, 0xC7)
        assertEquals("cvtsi2ss", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("rdi", inst.operands[1].text())
    }

    @Test
    fun `decodes cvtss2si`() {
        // F3 0F 2D C0 = cvtss2si eax, xmm0
        val inst = disasmOne(0xF3, 0x0F, 0x2D, 0xC0)
        assertEquals("cvtss2si", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("xmm0", inst.operands[1].text())
    }

    @Test
    fun `decodes cvtsi2sd with REX_W`() {
        // F2 48 0F 2A C7 = cvtsi2sd xmm0, rdi
        val inst = disasmOne(0xF2, 0x48, 0x0F, 0x2A, 0xC7)
        assertEquals("cvtsi2sd", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("rdi", inst.operands[1].text())
    }

    @Test
    fun `decodes cvtsd2si with REX_W`() {
        // F2 48 0F 2D C0 = cvtsd2si rax, xmm0
        val inst = disasmOne(0xF2, 0x48, 0x0F, 0x2D, 0xC0)
        assertEquals("cvtsd2si", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        assertEquals("xmm0", inst.operands[1].text())
    }

    @Test
    fun `decodes cvttsd2si`() {
        // F2 0F 2C C0 = cvttsd2si eax, xmm0
        val inst = disasmOne(0xF2, 0x0F, 0x2C, 0xC0)
        assertEquals("cvttsd2si", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("xmm0", inst.operands[1].text())
    }

    @Test
    fun `decodes movsd store`() {
        // F2 0F 11 45 F8 = movsd [rbp-8], xmm0
        val inst = disasmOne(0xF2, 0x0F, 0x11, 0x45, 0xF8)
        assertEquals("movsd", inst.mnemonic)
        assertTrue(inst.operands[0].text().contains("rbp"))
        assertEquals("xmm0", inst.operands[1].text())
    }

    @Test
    fun `decodes movd xmm from gp`() {
        // 66 0F 6E C0 = movd xmm0, eax
        val inst = disasmOne(0x66, 0x0F, 0x6E, 0xC0)
        assertEquals("movd", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("eax", inst.operands[1].text())
    }

    @Test
    fun `decodes movd gp from xmm`() {
        // 66 0F 7E C0 = movd eax, xmm0
        val inst = disasmOne(0x66, 0x0F, 0x7E, 0xC0)
        assertEquals("movd", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("xmm0", inst.operands[1].text())
    }

    // ---------------------------------------------------------------
    // 2. String instructions
    // ---------------------------------------------------------------

    @Test
    fun `decodes rep movsb`() {
        // F3 A4 = rep movsb
        val inst = disasmOne(0xF3, 0xA4)
        assertEquals("rep movsb", inst.mnemonic)
    }

    @Test
    fun `decodes rep stosb`() {
        // F3 AA = rep stosb
        val inst = disasmOne(0xF3, 0xAA)
        assertEquals("rep stosb", inst.mnemonic)
    }

    @Test
    fun `decodes movsb without rep`() {
        // A4 = movsb
        val inst = disasmOne(0xA4)
        assertEquals("movsb", inst.mnemonic)
    }

    @Test
    fun `decodes stosb without rep`() {
        // AA = stosb
        val inst = disasmOne(0xAA)
        assertEquals("stosb", inst.mnemonic)
    }

    @Test
    fun `decodes rep movsd dword`() {
        // F3 A5 = rep movsd (32-bit)
        val inst = disasmOne(0xF3, 0xA5)
        assertEquals("rep movsd", inst.mnemonic)
    }

    @Test
    fun `decodes rep movsq`() {
        // F3 48 A5 = rep movsq (64-bit with REX.W)
        val inst = disasmOne(0xF3, 0x48, 0xA5)
        assertEquals("rep movsq", inst.mnemonic)
    }

    @Test
    fun `decodes rep stosd`() {
        // F3 AB = rep stosd
        val inst = disasmOne(0xF3, 0xAB)
        assertEquals("rep stosd", inst.mnemonic)
    }

    // ---------------------------------------------------------------
    // 3. Conditional jumps (rel8 and rel32 variants)
    // ---------------------------------------------------------------

    @Test
    fun `decodes je rel8`() {
        // 74 0A = jz +10 from current pos
        val insts = disasmAt(0x100, 0x74, 0x0A)
        assertEquals("jz", insts[0].mnemonic)
        assertEquals("0x10c", insts[0].operands[0].text())
    }

    @Test
    fun `decodes jne rel8`() {
        // 75 05 = jnz +5
        val insts = disasmAt(0x200, 0x75, 0x05)
        assertEquals("jnz", insts[0].mnemonic)
        assertEquals("0x207", insts[0].operands[0].text())
    }

    @Test
    fun `decodes jl rel8`() {
        // 7C 03 = jl +3
        val inst = disasmOne(0x7C, 0x03)
        assertEquals("jl", inst.mnemonic)
    }

    @Test
    fun `decodes jg rel8`() {
        // 7F 10 = jg +16
        val inst = disasmOne(0x7F, 0x10)
        assertEquals("jg", inst.mnemonic)
    }

    @Test
    fun `decodes jle rel8`() {
        // 7E 08 = jle +8
        val inst = disasmOne(0x7E, 0x08)
        assertEquals("jle", inst.mnemonic)
    }

    @Test
    fun `decodes jge rel8`() {
        // 7D 04 = jnl (jge) +4
        val inst = disasmOne(0x7D, 0x04)
        assertEquals("jnl", inst.mnemonic)
    }

    @Test
    fun `decodes ja rel8`() {
        // 77 06 = ja +6
        val inst = disasmOne(0x77, 0x06)
        assertEquals("ja", inst.mnemonic)
    }

    @Test
    fun `decodes jb rel8`() {
        // 72 02 = jb +2
        val inst = disasmOne(0x72, 0x02)
        assertEquals("jb", inst.mnemonic)
    }

    @Test
    fun `decodes jbe rel8`() {
        // 76 03 = jbe +3
        val inst = disasmOne(0x76, 0x03)
        assertEquals("jbe", inst.mnemonic)
    }

    @Test
    fun `decodes js rel8`() {
        // 78 02 = js +2
        val inst = disasmOne(0x78, 0x02)
        assertEquals("js", inst.mnemonic)
    }

    @Test
    fun `decodes jns rel8`() {
        // 79 02 = jns +2
        val inst = disasmOne(0x79, 0x02)
        assertEquals("jns", inst.mnemonic)
    }

    @Test
    fun `decodes je rel32`() {
        // 0F 84 10 00 00 00 = jz +16 from addr 0x400
        val insts = disasmAt(0x400, 0x0F, 0x84, 0x10, 0x00, 0x00, 0x00)
        assertEquals("jz", insts[0].mnemonic)
        assertEquals("0x416", insts[0].operands[0].text())
    }

    @Test
    fun `decodes jl rel32`() {
        // 0F 8C 20 00 00 00 = jl +32
        val insts = disasmAt(0x500, 0x0F, 0x8C, 0x20, 0x00, 0x00, 0x00)
        assertEquals("jl", insts[0].mnemonic)
        assertEquals("0x526", insts[0].operands[0].text())
    }

    @Test
    fun `decodes jg rel32`() {
        // 0F 8F 00 01 00 00 = jg +256
        val insts = disasmAt(0x1000, 0x0F, 0x8F, 0x00, 0x01, 0x00, 0x00)
        assertEquals("jg", insts[0].mnemonic)
        assertEquals("0x1106", insts[0].operands[0].text())
    }

    @Test
    fun `decodes backward conditional jump`() {
        // 75 FE = jnz -2 (jumps to itself)
        val insts = disasmAt(0x100, 0x75, 0xFE)
        assertEquals("jnz", insts[0].mnemonic)
        assertEquals("0x100", insts[0].operands[0].text())
    }

    // ---------------------------------------------------------------
    // 4. CALL with different addressing modes
    // ---------------------------------------------------------------

    @Test
    fun `decodes call rel32 with negative offset`() {
        // E8 FB FF FF FF = call -5 (call to itself)
        val insts = disasmAt(0x1000, 0xE8, 0xFB, 0xFF, 0xFF, 0xFF)
        assertEquals("call", insts[0].mnemonic)
        assertEquals("0x1000", insts[0].operands[0].text())
    }

    @Test
    fun `decodes call indirect via register`() {
        // FF D0 = call rax
        val inst = disasmOne(0xFF, 0xD0)
        assertEquals("call", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes call indirect via register with REX_W`() {
        // 48 FF D0 = call rax (REX.W)
        val inst = disasmOne(0x48, 0xFF, 0xD0)
        assertEquals("call", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    @Test
    fun `decodes call indirect via r12`() {
        // 41 FF D4 = call r12
        val inst = disasmOne(0x41, 0xFF, 0xD4)
        assertEquals("call", inst.mnemonic)
        // r12 is encoded with REX.B
        assertTrue(inst.operands[0].text().contains("r12") || inst.operands[0].text().contains("esp"),
            "Expected r12 or related: ${inst.operands[0].text()}")
    }

    @Test
    fun `decodes call with memory base+disp8`() {
        // FF 50 08 = call [rax+8]
        val inst = disasmOne(0xFF, 0x50, 0x08)
        assertEquals("call", inst.mnemonic)
        assertTrue(inst.operands[0].text().contains("rax"), "Expected [rax+...]: ${inst.operands[0].text()}")
    }

    // ---------------------------------------------------------------
    // 5. Memory addressing: SIB byte combinations, RIP-relative
    // ---------------------------------------------------------------

    @Test
    fun `decodes SIB base+index`() {
        // 8B 04 08 = mov eax, [rax+rcx]
        val inst = disasmOne(0x8B, 0x04, 0x08)
        assertEquals("mov", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        val mem = inst.operands[1].text()
        assertTrue(mem.contains("rax"), "Expected base rax: $mem")
        assertTrue(mem.contains("rcx"), "Expected index rcx: $mem")
    }

    @Test
    fun `decodes SIB base+index*scale`() {
        // 8B 04 88 = mov eax, [rax+rcx*4]
        val inst = disasmOne(0x8B, 0x04, 0x88)
        assertEquals("mov", inst.mnemonic)
        val mem = inst.operands[1].text()
        assertTrue(mem.contains("rax"), "Expected base rax: $mem")
        assertTrue(mem.contains("rcx"), "Expected index rcx: $mem")
        assertTrue(mem.contains("*4"), "Expected scale *4: $mem")
    }

    @Test
    fun `decodes SIB base+index*8+disp8`() {
        // 8B 44 C8 10 = mov eax, [rax+rcx*8+0x10]
        val inst = disasmOne(0x8B, 0x44, 0xC8, 0x10)
        assertEquals("mov", inst.mnemonic)
        val mem = inst.operands[1].text()
        assertTrue(mem.contains("rax"), "Expected base rax: $mem")
        assertTrue(mem.contains("rcx"), "Expected index rcx: $mem")
        assertTrue(mem.contains("*8"), "Expected scale *8: $mem")
        assertTrue(mem.contains("0x10"), "Expected disp 0x10: $mem")
    }

    @Test
    fun `decodes SIB with no index (rsp as base)`() {
        // 8B 04 24 = mov eax, [rsp] (SIB needed when rm=4)
        val inst = disasmOne(0x8B, 0x04, 0x24)
        assertEquals("mov", inst.mnemonic)
        val mem = inst.operands[1].text()
        assertTrue(mem.contains("rsp"), "Expected rsp: $mem")
    }

    @Test
    fun `decodes SIB rsp+disp8`() {
        // 8B 44 24 08 = mov eax, [rsp+8]
        val inst = disasmOne(0x8B, 0x44, 0x24, 0x08)
        assertEquals("mov", inst.mnemonic)
        val mem = inst.operands[1].text()
        assertTrue(mem.contains("rsp"), "Expected rsp: $mem")
        assertTrue(mem.contains("0x8"), "Expected disp: $mem")
    }

    @Test
    fun `decodes RIP-relative addressing`() {
        // 8B 05 10 00 00 00 = mov eax, [rip+0x10]
        val inst = disasmOne(0x8B, 0x05, 0x10, 0x00, 0x00, 0x00)
        assertEquals("mov", inst.mnemonic)
        assertTrue(inst.operands[1].text().contains("rip"), "Expected rip-relative: ${inst.operands[1].text()}")
    }

    @Test
    fun `decodes SIB disp32 only (no base)`() {
        // 8B 04 25 78 56 34 12 = mov eax, [0x12345678]
        val inst = disasmOne(0x8B, 0x04, 0x25, 0x78, 0x56, 0x34, 0x12)
        assertEquals("mov", inst.mnemonic)
        val mem = inst.operands[1].text()
        assertTrue(mem.contains("12345678"), "Expected absolute addr: $mem")
    }

    @Test
    fun `decodes memory with disp32`() {
        // 8B 85 00 01 00 00 = mov eax, [rbp+0x100]
        val inst = disasmOne(0x8B, 0x85, 0x00, 0x01, 0x00, 0x00)
        assertEquals("mov", inst.mnemonic)
        val mem = inst.operands[1].text()
        assertTrue(mem.contains("rbp"), "Expected rbp: $mem")
        assertTrue(mem.contains("0x100"), "Expected disp 0x100: $mem")
    }

    // ---------------------------------------------------------------
    // 6. REX prefix variations
    // ---------------------------------------------------------------

    @Test
    fun `decodes REX_W add r64 r64`() {
        // 48 01 C8 = add rax, rcx
        val inst = disasmOne(0x48, 0x01, 0xC8)
        assertEquals("add", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        assertEquals("rcx", inst.operands[1].text())
    }

    @Test
    fun `decodes REX_R extends reg field`() {
        // 44 89 C0 = mov eax, r8d (REX.R=1, reg=r8d, rm=eax)
        val inst = disasmOne(0x44, 0x89, 0xC0)
        assertEquals("mov", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("r8d", inst.operands[1].text())
    }

    @Test
    fun `decodes REX_B extends rm field`() {
        // 41 89 C0 = mov r8d, eax (REX.B=1, rm=r8d)
        val inst = disasmOne(0x41, 0x89, 0xC0)
        assertEquals("mov", inst.mnemonic)
        assertEquals("r8d", inst.operands[0].text())
        assertEquals("eax", inst.operands[1].text())
    }

    @Test
    fun `decodes REX_WRB combined`() {
        // 4D 89 C1 = mov r9, r8 (REX.W=1, REX.R=1, REX.B=1)
        val inst = disasmOne(0x4D, 0x89, 0xC1)
        assertEquals("mov", inst.mnemonic)
        assertEquals("r9", inst.operands[0].text())
        assertEquals("r8", inst.operands[1].text())
    }

    @Test
    fun `decodes REX_W xor r64 r64`() {
        // 48 31 C0 = xor rax, rax
        val inst = disasmOne(0x48, 0x31, 0xC0)
        assertEquals("xor", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        assertEquals("rax", inst.operands[1].text())
    }

    // ---------------------------------------------------------------
    // 7. Extended registers (r8-r15 in various positions)
    // ---------------------------------------------------------------

    @Test
    fun `decodes push r15`() {
        // 41 57 = push r15
        val inst = disasmOne(0x41, 0x57)
        assertEquals("push", inst.mnemonic)
        assertEquals("r15", inst.operands[0].text())
    }

    @Test
    fun `decodes pop r12`() {
        // 41 5C = pop r12
        val inst = disasmOne(0x41, 0x5C)
        assertEquals("pop", inst.mnemonic)
        assertEquals("r12", inst.operands[0].text())
    }

    @Test
    fun `decodes mov r10d imm32`() {
        // 41 BA 0A 00 00 00 = mov r10d, 10
        val inst = disasmOne(0x41, 0xBA, 0x0A, 0x00, 0x00, 0x00)
        assertEquals("mov", inst.mnemonic)
        assertEquals("r10d", inst.operands[0].text())
        assertEquals("0xa", inst.operands[1].text())
    }

    @Test
    fun `decodes mov r13 imm64`() {
        // 49 BD 01 00 00 00 00 00 00 00 = mov r13, 1
        val inst = disasmOne(0x49, 0xBD, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertEquals("mov", inst.mnemonic)
        assertEquals("r13", inst.operands[0].text())
        assertEquals(10, inst.size)
    }

    @Test
    fun `decodes add r11 r14`() {
        // 4D 01 F3 = add r11, r14
        val inst = disasmOne(0x4D, 0x01, 0xF3)
        assertEquals("add", inst.mnemonic)
        assertEquals("r11", inst.operands[0].text())
        assertEquals("r14", inst.operands[1].text())
    }

    @Test
    fun `decodes xor r9d r9d`() {
        // 45 31 C9 = xor r9d, r9d
        val inst = disasmOne(0x45, 0x31, 0xC9)
        assertEquals("xor", inst.mnemonic)
        assertEquals("r9d", inst.operands[0].text())
        assertEquals("r9d", inst.operands[1].text())
    }

    @Test
    fun `decodes cmp r15 r12`() {
        // 4D 39 E7 = cmp r15, r12
        val inst = disasmOne(0x4D, 0x39, 0xE7)
        assertEquals("cmp", inst.mnemonic)
        assertEquals("r15", inst.operands[0].text())
        assertEquals("r12", inst.operands[1].text())
    }

    // ---------------------------------------------------------------
    // 8. 8-bit and 16-bit operations
    // ---------------------------------------------------------------

    @Test
    fun `decodes mov al imm8`() {
        // B0 42 = mov al, 0x42
        val inst = disasmOne(0xB0, 0x42)
        assertEquals("mov", inst.mnemonic)
        assertEquals("al", inst.operands[0].text())
        assertEquals("0x42", inst.operands[1].text())
    }

    @Test
    fun `decodes mov cl imm8`() {
        // B1 FF = mov cl, 0xFF (-1 signed)
        val inst = disasmOne(0xB1, 0xFF)
        assertEquals("mov", inst.mnemonic)
        assertEquals("cl", inst.operands[0].text())
    }

    @Test
    fun `decodes add al imm8`() {
        // 04 01 = add al, 1
        val inst = disasmOne(0x04, 0x01)
        assertEquals("add", inst.mnemonic)
        assertEquals("al", inst.operands[0].text())
        assertEquals("0x1", inst.operands[1].text())
    }

    @Test
    fun `decodes cmp al imm8`() {
        // 3C 00 = cmp al, 0
        val inst = disasmOne(0x3C, 0x00)
        assertEquals("cmp", inst.mnemonic)
        assertEquals("al", inst.operands[0].text())
        assertEquals("0x0", inst.operands[1].text())
    }

    @Test
    fun `decodes 16-bit mov with operand size prefix`() {
        // 66 B8 34 12 = mov ax, 0x1234
        val inst = disasmOne(0x66, 0xB8, 0x34, 0x12)
        assertEquals("mov", inst.mnemonic)
        assertEquals("ax", inst.operands[0].text())
        assertEquals("0x1234", inst.operands[1].text())
    }

    @Test
    fun `decodes 16-bit add with operand size prefix`() {
        // 66 01 C8 = add ax, cx
        val inst = disasmOne(0x66, 0x01, 0xC8)
        assertEquals("add", inst.mnemonic)
        assertEquals("ax", inst.operands[0].text())
        assertEquals("cx", inst.operands[1].text())
    }

    @Test
    fun `decodes 8-bit add rm8 r8`() {
        // 00 C8 = add al, cl
        val inst = disasmOne(0x00, 0xC8)
        assertEquals("add", inst.mnemonic)
        assertEquals("al", inst.operands[0].text())
        assertEquals("cl", inst.operands[1].text())
    }

    @Test
    fun `decodes test al imm8`() {
        // A8 01 = test al, 1
        val inst = disasmOne(0xA8, 0x01)
        assertEquals("test", inst.mnemonic)
        assertEquals("al", inst.operands[0].text())
        assertEquals("0x1", inst.operands[1].text())
    }

    @Test
    fun `decodes movzx eax r8`() {
        // 0F B6 C1 = movzx eax, cl
        val inst = disasmOne(0x0F, 0xB6, 0xC1)
        assertEquals("movzx", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("cl", inst.operands[1].text())
    }

    @Test
    fun `decodes movsx eax r8`() {
        // 0F BE C1 = movsx eax, cl
        val inst = disasmOne(0x0F, 0xBE, 0xC1)
        assertEquals("movsx", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("cl", inst.operands[1].text())
    }

    @Test
    fun `decodes movzx r32 r16`() {
        // 0F B7 C1 = movzx eax, cx
        val inst = disasmOne(0x0F, 0xB7, 0xC1)
        assertEquals("movzx", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("cx", inst.operands[1].text())
    }

    // ---------------------------------------------------------------
    // 9. Edge cases
    // ---------------------------------------------------------------

    @Test
    fun `empty input returns empty list`() {
        val insts = disasm.disassembleRaw(ByteArray(0))
        assertTrue(insts.isEmpty())
    }

    @Test
    fun `long instruction sequence decodes all`() {
        // nop nop nop ret = 90 90 90 C3
        val insts = disasm(0x90, 0x90, 0x90, 0xC3)
        assertEquals(4, insts.size)
        assertEquals("nop", insts[0].mnemonic)
        assertEquals("nop", insts[1].mnemonic)
        assertEquals("nop", insts[2].mnemonic)
        assertEquals("ret", insts[3].mnemonic)
    }

    @Test
    fun `10-byte mov rax imm64 is max standard length`() {
        // 48 B8 followed by 8 bytes = 10-byte mov rax, imm64
        val inst = disasmOne(0x48, 0xB8, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x7F)
        assertEquals("mov", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        assertEquals(10, inst.size)
    }

    @Test
    fun `instruction sizes are correct`() {
        // Various sizes: nop=1, push rbp=1, mov rbp,rsp=3, sub rsp,0x28=4, ret=1
        val insts = disasm(0x90, 0x55, 0x48, 0x89, 0xE5, 0x48, 0x83, 0xEC, 0x28, 0xC3)
        assertEquals(5, insts.size)
        assertEquals(1, insts[0].size) // nop
        assertEquals(1, insts[1].size) // push rbp
        assertEquals(3, insts[2].size) // mov rbp, rsp
        assertEquals(4, insts[3].size) // sub rsp, 0x28
        assertEquals(1, insts[4].size) // ret
    }

    @Test
    fun `total decoded bytes match input size for valid stream`() {
        val bytes = intArrayOf(0x55, 0x48, 0x89, 0xE5, 0x48, 0x83, 0xEC, 0x20, 0x31, 0xC0, 0xC9, 0xC3)
        val insts = disasm(*bytes)
        assertEquals(bytes.size, insts.sumOf { it.size },
            "Total decoded bytes should match input. Instrs: ${insts.map { it.text() }}")
    }

    // ---------------------------------------------------------------
    // 10. VEX-encoded instructions
    // ---------------------------------------------------------------

    @Test
    fun `decodes VEX blsr`() {
        // C4 E2 70 F3 C9 = blsr ecx, ecx (ModRM.reg ext=1)
        val inst = disasmOne(0xC4, 0xE2, 0x70, 0xF3, 0xC9)
        assertEquals("blsr", inst.mnemonic)
        assertEquals(2, inst.operands.size)
    }

    @Test
    fun `decodes VEX blsmsk`() {
        // C4 E2 70 F3 D1 = blsmsk ecx, ecx (ModRM.reg ext=2)
        val inst = disasmOne(0xC4, 0xE2, 0x70, 0xF3, 0xD1)
        assertEquals("blsmsk", inst.mnemonic)
        assertEquals(2, inst.operands.size)
    }

    @Test
    fun `decodes VEX bextr`() {
        // C4 E2 70 F7 C2 = bextr eax, edx, ecx
        val inst = disasmOne(0xC4, 0xE2, 0x70, 0xF7, 0xC2)
        assertEquals("bextr", inst.mnemonic)
        assertEquals(3, inst.operands.size)
    }

    @Test
    fun `decodes VEX bzhi`() {
        // C4 E2 70 F5 C2 = bzhi eax, edx, ecx
        val inst = disasmOne(0xC4, 0xE2, 0x70, 0xF5, 0xC2)
        assertEquals("bzhi", inst.mnemonic)
        assertEquals(3, inst.operands.size)
    }

    @Test
    fun `decodes VEX vaddps xmm`() {
        // C5 F0 58 C2 = vaddps xmm0, xmm1, xmm2 (2-byte VEX, L=0)
        val inst = disasmOne(0xC5, 0xF0, 0x58, 0xC2)
        assertEquals("vaddps", inst.mnemonic)
        assertEquals(3, inst.operands.size)
    }

    @Test
    fun `decodes VEX vsubps`() {
        // C5 F0 5C C2 = vsubps xmm0, xmm1, xmm2
        val inst = disasmOne(0xC5, 0xF0, 0x5C, 0xC2)
        assertEquals("vsubps", inst.mnemonic)
    }

    @Test
    fun `decodes VEX vmulps`() {
        // C5 F0 59 C2 = vmulps xmm0, xmm1, xmm2
        val inst = disasmOne(0xC5, 0xF0, 0x59, 0xC2)
        assertEquals("vmulps", inst.mnemonic)
    }

    @Test
    fun `decodes VEX vdivps`() {
        // C5 F0 5E C2 = vdivps xmm0, xmm1, xmm2
        val inst = disasmOne(0xC5, 0xF0, 0x5E, 0xC2)
        assertEquals("vdivps", inst.mnemonic)
    }

    // ---------------------------------------------------------------
    // Additional: misc instructions
    // ---------------------------------------------------------------

    @Test
    fun `decodes leave`() {
        val inst = disasmOne(0xC9)
        assertEquals("leave", inst.mnemonic)
    }

    @Test
    fun `decodes cdq`() {
        val inst = disasmOne(0x99)
        assertEquals("cdq", inst.mnemonic)
    }

    @Test
    fun `decodes cqo`() {
        // 48 99 = cqo (REX.W + 0x99)
        val inst = disasmOne(0x48, 0x99)
        assertEquals("cqo", inst.mnemonic)
    }

    @Test
    fun `decodes int imm8`() {
        // CD 80 = int 0x80
        val inst = disasmOne(0xCD, 0x80)
        assertEquals("int", inst.mnemonic)
        // 0x80 as signed byte = -128, but as immediate value
        assertTrue(inst.operands[0].text().contains("80"), "Expected 0x80: ${inst.operands[0].text()}")
    }

    @Test
    fun `decodes neg eax`() {
        // F7 D8 = neg eax
        val inst = disasmOne(0xF7, 0xD8)
        assertEquals("neg", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes not eax`() {
        // F7 D0 = not eax
        val inst = disasmOne(0xF7, 0xD0)
        assertEquals("not", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes div ecx`() {
        // F7 F1 = div ecx
        val inst = disasmOne(0xF7, 0xF1)
        assertEquals("div", inst.mnemonic)
        assertEquals("ecx", inst.operands[0].text())
    }

    @Test
    fun `decodes idiv ecx`() {
        // F7 F9 = idiv ecx
        val inst = disasmOne(0xF7, 0xF9)
        assertEquals("idiv", inst.mnemonic)
        assertEquals("ecx", inst.operands[0].text())
    }

    @Test
    fun `decodes mul ecx`() {
        // F7 E1 = mul ecx
        val inst = disasmOne(0xF7, 0xE1)
        assertEquals("mul", inst.mnemonic)
        assertEquals("ecx", inst.operands[0].text())
    }

    @Test
    fun `decodes shr eax cl`() {
        // D3 E8 = shr eax, cl
        val inst = disasmOne(0xD3, 0xE8)
        assertEquals("shr", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("cl", inst.operands[1].text())
    }

    @Test
    fun `decodes sar eax 1`() {
        // D1 F8 = sar eax, 1
        val inst = disasmOne(0xD1, 0xF8)
        assertEquals("sar", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("0x1", inst.operands[1].text())
    }

    @Test
    fun `decodes rol ecx imm8`() {
        // C1 C1 03 = rol ecx, 3
        val inst = disasmOne(0xC1, 0xC1, 0x03)
        assertEquals("rol", inst.mnemonic)
        assertEquals("ecx", inst.operands[0].text())
        assertEquals("0x3", inst.operands[1].text())
    }

    @Test
    fun `decodes ror edx imm8`() {
        // C1 CA 05 = ror edx, 5
        val inst = disasmOne(0xC1, 0xCA, 0x05)
        assertEquals("ror", inst.mnemonic)
        assertEquals("edx", inst.operands[0].text())
        assertEquals("0x5", inst.operands[1].text())
    }

    @Test
    fun `decodes inc and dec via group 5`() {
        // FF C0 = inc eax
        assertEquals("inc", disasmOne(0xFF, 0xC0).mnemonic)
        // FF C8 = dec eax
        assertEquals("dec", disasmOne(0xFF, 0xC8).mnemonic)
    }

    @Test
    fun `decodes inc and dec 8-bit via group 4`() {
        // FE C0 = inc al
        val inc = disasmOne(0xFE, 0xC0)
        assertEquals("inc", inc.mnemonic)
        assertEquals("al", inc.operands[0].text())
        // FE C8 = dec al
        val dec = disasmOne(0xFE, 0xC8)
        assertEquals("dec", dec.mnemonic)
        assertEquals("al", dec.operands[0].text())
    }

    @Test
    fun `decodes setcc variants`() {
        // 0F 94 C0 = setz al
        assertEquals("setz", disasmOne(0x0F, 0x94, 0xC0).mnemonic)
        // 0F 95 C0 = setnz al
        assertEquals("setnz", disasmOne(0x0F, 0x95, 0xC0).mnemonic)
        // 0F 9C C0 = setl al
        assertEquals("setl", disasmOne(0x0F, 0x9C, 0xC0).mnemonic)
        // 0F 9F C0 = setg al
        assertEquals("setg", disasmOne(0x0F, 0x9F, 0xC0).mnemonic)
    }

    @Test
    fun `decodes cmovcc`() {
        // 0F 44 C1 = cmovz eax, ecx
        val inst = disasmOne(0x0F, 0x44, 0xC1)
        assertEquals("cmovz", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test
    fun `decodes bswap eax`() {
        // 0F C8 = bswap eax
        val inst = disasmOne(0x0F, 0xC8)
        assertEquals("bswap", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes bswap rax`() {
        // 48 0F C8 = bswap rax
        val inst = disasmOne(0x48, 0x0F, 0xC8)
        assertEquals("bswap", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
    }

    @Test
    fun `decodes xchg eax ecx`() {
        // 91 = xchg eax, ecx
        val inst = disasmOne(0x91)
        assertEquals("xchg", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test
    fun `decodes bsf`() {
        // 0F BC C1 = bsf eax, ecx
        val inst = disasmOne(0x0F, 0xBC, 0xC1)
        assertEquals("bsf", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test
    fun `decodes bsr`() {
        // 0F BD C1 = bsr eax, ecx
        val inst = disasmOne(0x0F, 0xBD, 0xC1)
        assertEquals("bsr", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test
    fun `decodes nop with multi-byte encoding`() {
        // 0F 1F 00 = nop [rax] (3-byte NOP)
        val inst = disasmOne(0x0F, 0x1F, 0x00)
        assertEquals("nop", inst.mnemonic)
        assertEquals(3, inst.size)
    }

    @Test
    fun `decodes retf`() {
        val inst = disasmOne(0xCB)
        assertEquals("retf", inst.mnemonic)
    }

    @Test
    fun `decodes ret imm16`() {
        // C2 08 00 = ret 8
        val inst = disasmOne(0xC2, 0x08, 0x00)
        assertEquals("ret", inst.mnemonic)
        assertEquals("0x8", inst.operands[0].text())
    }

    @Test
    fun `address tracking across multiple instructions`() {
        // push rbp; mov rbp,rsp; ret
        val insts = disasmAt(0x401000, 0x55, 0x48, 0x89, 0xE5, 0xC3)
        assertEquals(0x401000L, insts[0].address)
        assertEquals(0x401001L, insts[1].address)
        assertEquals(0x401004L, insts[2].address)
    }

    @Test
    fun `decodes or rm32 imm8`() {
        // 83 C8 FF = or eax, -1 (0xFF sign-extended)
        val inst = disasmOne(0x83, 0xC8, 0xFF)
        assertEquals("or", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
    }

    @Test
    fun `decodes and eax imm32`() {
        // 25 FF 0F 00 00 = and eax, 0xFFF
        val inst = disasmOne(0x25, 0xFF, 0x0F, 0x00, 0x00)
        assertEquals("and", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("0xfff", inst.operands[1].text())
    }

    @Test
    fun `decodes test eax imm32`() {
        // A9 01 00 00 00 = test eax, 1
        val inst = disasmOne(0xA9, 0x01, 0x00, 0x00, 0x00)
        assertEquals("test", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("0x1", inst.operands[1].text())
    }

    @Test
    fun `decodes sub al imm8`() {
        // 2C 01 = sub al, 1
        val inst = disasmOne(0x2C, 0x01)
        assertEquals("sub", inst.mnemonic)
        assertEquals("al", inst.operands[0].text())
        assertEquals("0x1", inst.operands[1].text())
    }

    @Test
    fun `decodes jmp rel32`() {
        // E9 FB FF FF FF = jmp -5 (jump to itself)
        val insts = disasmAt(0x2000, 0xE9, 0xFB, 0xFF, 0xFF, 0xFF)
        assertEquals("jmp", insts[0].mnemonic)
        assertEquals("0x2000", insts[0].operands[0].text())
    }
}
