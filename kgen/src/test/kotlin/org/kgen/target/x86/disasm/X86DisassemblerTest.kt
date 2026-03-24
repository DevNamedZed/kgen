package org.kgen.target.x86.disasm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator

class X86DisassemblerTest {

    private val disasm = X86Disassembler()

    private fun disasm(vararg bytes: Int): List<X86Instruction> =
        disasm.disassembleRaw(ByteArray(bytes.size) { bytes[it].toByte() })

    private fun disasmOne(vararg bytes: Int): X86Instruction =
        disasm(*bytes).first()

    @Test
    fun `decodes nop`() {
        val inst = disasmOne(0x90)
        assertEquals("nop", inst.mnemonic)
        assertEquals(1, inst.size)
    }

    @Test
    fun `decodes ret`() {
        val inst = disasmOne(0xC3)
        assertEquals("ret", inst.mnemonic)
    }

    @Test
    fun `decodes push and pop rbp`() {
        val insts = disasm(0x55, 0x5D) // push rbp, pop rbp
        assertEquals(2, insts.size)
        assertEquals("push", insts[0].mnemonic)
        assertEquals("rbp", insts[0].operands[0].text())
        assertEquals("pop", insts[1].mnemonic)
        assertEquals("rbp", insts[1].operands[0].text())
    }

    @Test
    fun `decodes push r8 through r15`() {
        // push r8 = 41 50, push r15 = 41 57
        val inst = disasmOne(0x41, 0x50)
        assertEquals("push", inst.mnemonic)
        assertEquals("r8", inst.operands[0].text())
    }

    @Test
    fun `decodes mov reg64 reg64 with REX`() {
        // mov rbp, rsp = 48 89 E5
        val inst = disasmOne(0x48, 0x89, 0xE5)
        assertEquals("mov", inst.mnemonic)
        assertEquals(2, inst.operands.size)
        assertEquals("rbp", inst.operands[0].text())
        assertEquals("rsp", inst.operands[1].text())
    }

    @Test
    fun `decodes mov reg32 imm32`() {
        // mov eax, 42 = B8 2A 00 00 00
        val inst = disasmOne(0xB8, 0x2A, 0x00, 0x00, 0x00)
        assertEquals("mov", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("0x2a", inst.operands[1].text())
    }

    @Test
    fun `decodes mov r64 imm64`() {
        // mov rax, 0x123456789ABCDEF0 = 48 B8 F0 DE BC 9A 78 56 34 12
        val inst = disasmOne(0x48, 0xB8, 0xF0, 0xDE, 0xBC, 0x9A, 0x78, 0x56, 0x34, 0x12)
        assertEquals("mov", inst.mnemonic)
        assertEquals("rax", inst.operands[0].text())
        assertEquals(10, inst.size)
    }

    @Test
    fun `decodes add reg32 reg32`() {
        // add eax, ecx = 01 C8
        val inst = disasmOne(0x01, 0xC8)
        assertEquals("add", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test
    fun `decodes sub reg32 imm8`() {
        // sub esp, 0x28 = 83 EC 28
        val inst = disasmOne(0x83, 0xEC, 0x28)
        assertEquals("sub", inst.mnemonic)
        assertEquals("esp", inst.operands[0].text())
        assertEquals("0x28", inst.operands[1].text())
    }

    @Test
    fun `decodes sub rsp imm8 with REX_W`() {
        // sub rsp, 0x28 = 48 83 EC 28
        val inst = disasmOne(0x48, 0x83, 0xEC, 0x28)
        assertEquals("sub", inst.mnemonic)
        assertEquals("rsp", inst.operands[0].text())
        assertEquals("0x28", inst.operands[1].text())
    }

    @Test
    fun `decodes xor eax eax`() {
        // xor eax, eax = 31 C0
        val inst = disasmOne(0x31, 0xC0)
        assertEquals("xor", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("eax", inst.operands[1].text())
    }

    @Test
    fun `decodes call rel32`() {
        // call +5 from addr 0x100: E8 00 00 00 00
        val insts = disasm.disassembleRaw(
            byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00), 0x100)
        val inst = insts[0]
        assertEquals("call", inst.mnemonic)
        // target = 0x100 + 5 + 0 = 0x105
        assertEquals("0x105", inst.operands[0].text())
    }

    @Test
    fun `decodes jmp rel8`() {
        // jmp +2 from addr 0x200: EB 00
        val insts = disasm.disassembleRaw(
            byteArrayOf(0xEB.toByte(), 0x00), 0x200)
        val inst = insts[0]
        assertEquals("jmp", inst.mnemonic)
        assertEquals("0x202", inst.operands[0].text())
    }

    @Test
    fun `decodes jcc rel8`() {
        // jz +4 from addr 0: 74 02
        val inst = disasmOne(0x74, 0x02)
        assertEquals("jz", inst.mnemonic)
    }

    @Test
    fun `decodes jcc rel32`() {
        // jnz +0 from addr 0x300: 0F 85 00 00 00 00
        val insts = disasm.disassembleRaw(
            byteArrayOf(0x0F, 0x85.toByte(), 0x00, 0x00, 0x00, 0x00), 0x300)
        assertEquals("jnz", insts[0].mnemonic)
        assertEquals("0x306", insts[0].operands[0].text())
    }

    @Test
    fun `decodes lea rip-relative`() {
        // lea rdi, [rip+0x10] = 48 8D 3D 10 00 00 00
        val inst = disasmOne(0x48, 0x8D, 0x3D, 0x10, 0x00, 0x00, 0x00)
        assertEquals("lea", inst.mnemonic)
        assertEquals("rdi", inst.operands[0].text())
        assertTrue(inst.operands[1].text().contains("rip"))
    }

    @Test
    fun `decodes memory operand with displacement`() {
        // mov eax, [rbp-4] = 8B 45 FC
        val inst = disasmOne(0x8B, 0x45, 0xFC)
        assertEquals("mov", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertTrue(inst.operands[1].text().contains("rbp"))
    }

    @Test
    fun `decodes syscall`() {
        val inst = disasmOne(0x0F, 0x05)
        assertEquals("syscall", inst.mnemonic)
    }

    @Test
    fun `decodes setcc`() {
        // setz al = 0F 94 C0
        val inst = disasmOne(0x0F, 0x94, 0xC0)
        assertEquals("setz", inst.mnemonic)
    }

    @Test
    fun `decodes int3`() {
        val inst = disasmOne(0xCC)
        assertEquals("int3", inst.mnemonic)
    }

    @Test
    fun `decodes imul reg reg`() {
        // imul eax, ecx = 0F AF C1
        val inst = disasmOne(0x0F, 0xAF, 0xC1)
        assertEquals("imul", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("ecx", inst.operands[1].text())
    }

    @Test
    fun `decodes shl reg imm8`() {
        // shl eax, 2 = C1 E0 02
        val inst = disasmOne(0xC1, 0xE0, 0x02)
        assertEquals("shl", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("0x2", inst.operands[1].text())
    }

    @Test
    fun `decodes call indirect via memory`() {
        // call [rip+disp32] = FF 15 XX XX XX XX
        val inst = disasmOne(0xFF, 0x15, 0x10, 0x00, 0x00, 0x00)
        assertEquals("call", inst.mnemonic)
        assertTrue(inst.operands[0].text().contains("rip"))
    }

    @Test
    fun `decodes jmp indirect via memory`() {
        // jmp [rip+disp32] = FF 25 XX XX XX XX
        val inst = disasmOne(0xFF, 0x25, 0x10, 0x00, 0x00, 0x00)
        assertEquals("jmp", inst.mnemonic)
        assertTrue(inst.operands[0].text().contains("rip"))
    }

    @Test
    fun `round-trips codegen output`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections.first { it.name == ".text" }.data

        val insts = disasm.disassembleRaw(code)
        assertTrue(insts.isNotEmpty(), "Should decode at least one instruction")

        // First instruction should be push rbp
        assertEquals("push", insts[0].mnemonic)
        assertEquals("rbp", insts[0].operands[0].text())

        // Last instruction should be ret
        assertEquals("ret", insts.last().mnemonic)

        // Total bytes decoded should match code size
        assertEquals(code.size, insts.sumOf { it.size },
            "Decoded bytes should match code size. Instructions: ${insts.map { it.text() }}")
    }

    @Test
    fun `disassembles hello world codegen output`() {
        val ir = ModuleBuilder("hello", Target.x86_64())

        val strType = Type.Array(Type.I8, 14)
        val strRef = ir.addGlobal("hello_str", strType,
            Constant.StringConst("Hello, World!"), isConstant = true,
            linkage = Linkage.INTERNAL)

        ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)

        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.call("puts", listOf(strRef), Type.I32)
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections.first { it.name == ".text" }.data

        val insts = disasm.disassembleRaw(code)
        assertTrue(insts.isNotEmpty())
        assertEquals("push", insts[0].mnemonic)
        assertEquals("ret", insts.last().mnemonic)

        // Should contain a LEA (for string ref) and a CALL
        assertTrue(insts.any { it.mnemonic == "lea" }, "Expected LEA for string ref: ${insts.map { it.text() }}")
        assertTrue(insts.any { it.mnemonic == "call" }, "Expected CALL for puts: ${insts.map { it.text() }}")
    }

    @Test
    fun `implements Disassembler interface`() {
        val bytes = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
        val result = disasm.disassemble(bytes, 0x401000)
        assertEquals(3, result.size)
        assertEquals(0x401000L, result[0].address)
        assertEquals("push", result[0].mnemonic)
        assertEquals(0x401001L, result[1].address)
        assertEquals("mov", result[1].mnemonic)
        assertEquals(0x401004L, result[2].address)
        assertEquals("ret", result[2].mnemonic)
    }

    @Test
    fun `toString format matches expected style`() {
        val bytes = byteArrayOf(0x55)
        val result = disasm.disassemble(bytes, 0x401000)
        assertTrue(result[0].toString().startsWith("0x00401000:"))
    }

    @Test
    fun `decodes addsd xmm-xmm`() {
        // F2 0F 58 C1 = addsd xmm0, xmm1
        val inst = disasmOne(0xF2, 0x0F, 0x58, 0xC1)
        assertEquals("addsd", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("xmm1", inst.operands[1].text())
    }

    @Test
    fun `decodes subsd mulsd divsd`() {
        // F2 0F 5C CA = subsd xmm1, xmm2
        assertEquals("subsd", disasmOne(0xF2, 0x0F, 0x5C, 0xCA).mnemonic)
        // F2 0F 59 D3 = mulsd xmm2, xmm3
        assertEquals("mulsd", disasmOne(0xF2, 0x0F, 0x59, 0xD3).mnemonic)
        // F2 0F 5E DB = divsd xmm3, xmm3
        assertEquals("divsd", disasmOne(0xF2, 0x0F, 0x5E, 0xDB).mnemonic)
    }

    @Test
    fun `decodes movsd xmm-xmm`() {
        // F2 0F 10 C1 = movsd xmm0, xmm1
        val inst = disasmOne(0xF2, 0x0F, 0x10, 0xC1)
        assertEquals("movsd", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("xmm1", inst.operands[1].text())
    }

    @Test
    fun `decodes cvtsi2sd`() {
        // F2 0F 2A C7 = cvtsi2sd xmm0, edi
        val inst = disasmOne(0xF2, 0x0F, 0x2A, 0xC7)
        assertEquals("cvtsi2sd", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("edi", inst.operands[1].text())
    }

    @Test
    fun `decodes cvtsd2si`() {
        // F2 0F 2D C0 = cvtsd2si eax, xmm0
        val inst = disasmOne(0xF2, 0x0F, 0x2D, 0xC0)
        assertEquals("cvtsd2si", inst.mnemonic)
        assertEquals("eax", inst.operands[0].text())
        assertEquals("xmm0", inst.operands[1].text())
    }

    @Test
    fun `decodes ucomisd`() {
        // 66 0F 2E C1 = ucomisd xmm0, xmm1
        val inst = disasmOne(0x66, 0x0F, 0x2E, 0xC1)
        assertEquals("ucomisd", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("xmm1", inst.operands[1].text())
    }

    @Test
    fun `decodes pxor`() {
        // 66 0F EF C0 = pxor xmm0, xmm0
        val inst = disasmOne(0x66, 0x0F, 0xEF, 0xC0)
        assertEquals("pxor", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("xmm0", inst.operands[1].text())
    }

    @Test
    fun `decodes movq xmm from gp`() {
        // 66 48 0F 6E C3 = movq xmm0, rbx
        val inst = disasmOne(0x66, 0x48, 0x0F, 0x6E, 0xC3)
        assertEquals("movq", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("rbx", inst.operands[1].text())
    }

    @Test
    fun `decodes high xmm registers`() {
        // F2 45 0F 58 FE = addsd xmm15, xmm14
        val inst = disasmOne(0xF2, 0x45, 0x0F, 0x58, 0xFE)
        assertEquals("addsd", inst.mnemonic)
        assertEquals("xmm15", inst.operands[0].text())
        assertEquals("xmm14", inst.operands[1].text())
    }

    @Test
    fun `decodes cvtss2sd`() {
        // F3 0F 5A C1 = cvtss2sd xmm0, xmm1
        val inst = disasmOne(0xF3, 0x0F, 0x5A, 0xC1)
        assertEquals("cvtss2sd", inst.mnemonic)
        assertEquals("xmm0", inst.operands[0].text())
        assertEquals("xmm1", inst.operands[1].text())
    }

    @Test
    fun `decodes cvtsd2ss`() {
        // F2 0F 5A C1 = cvtsd2ss xmm0, xmm1
        val inst = disasmOne(0xF2, 0x0F, 0x5A, 0xC1)
        assertEquals("cvtsd2ss", inst.mnemonic)
    }

    // --- VEX/EVEX decoding ---

    @Test
    fun `decodes VEX andn`() {
        // C4 E2 70 F2 C2 = andn eax, ecx, edx
        val inst = disasmOne(0xC4, 0xE2, 0x70, 0xF2, 0xC2)
        assertEquals("andn", inst.mnemonic)
        assertEquals(3, inst.operands.size)
    }

    @Test
    fun `decodes VEX blsi`() {
        // C4 E2 70 F3 DA = blsi ecx, edx
        val inst = disasmOne(0xC4, 0xE2, 0x70, 0xF3, 0xDA)
        assertEquals("blsi", inst.mnemonic)
        assertEquals(2, inst.operands.size)
    }

    @Test
    fun `VEX assembler round-trip`() {
        val asm = org.kgen.target.x86.asm.X86Assembler()
        val eax = org.kgen.target.x86.X86Register.EAX as org.kgen.target.x86.X86Register32
        val ecx = org.kgen.target.x86.X86Register.ECX as org.kgen.target.x86.X86Register32
        val edx = org.kgen.target.x86.X86Register.EDX as org.kgen.target.x86.X86Operand32
        asm.andn(eax, ecx, edx)
        val bytes = asm.toByteArray()
        val inst = disasm.disassembleRaw(bytes).first()
        assertEquals("andn", inst.mnemonic, "Round-trip: ${bytes.map { "0x%02X".format(it) }}")
    }

    @Test
    fun `decodes EVEX vaddps`() {
        // 62 F1 74 48 58 C2 = vaddps zmm0, zmm1, zmm2
        val inst = disasmOne(0x62, 0xF1, 0x74, 0x48, 0x58, 0xC2)
        assertEquals("vaddps", inst.mnemonic)
    }
}
