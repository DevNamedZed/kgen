package org.kgen.x86.disasm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.x86.codegen.X86CodeGenerator

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
        val ir = IrBuilder("test", Target.x86_64())
        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
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
        val ir = IrBuilder("hello", Target.x86_64())

        val strType = Type.Array(Type.I8, 14)
        val strRef = ir.addGlobal("hello_str", strType,
            Constant.StringConst("Hello, World!"), isConstant = true,
            linkage = Linkage.INTERNAL)

        ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)

        ir.createFunction("main", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
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
}
