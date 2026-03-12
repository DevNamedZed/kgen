package org.kgen.integration.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.reflect.*
import org.kgen.target.x86.X86Register
import org.kgen.target.x86.asm.X86Assembler

class CodeViewTest {

    @Test
    fun disassembleLiveCode() {
        val asm = X86Assembler()
        asm.push(X86Register.RBP)
        asm.mov(X86Register.RBP, X86Register.RSP)
        asm.mov(X86Register.EAX, 42)
        asm.pop(X86Register.RBP)
        asm.ret()
        val code = asm.toByteArray()

        val mem = NativeMemory.allocateExecutable(code.size.toLong())
        mem.write(0, code)

        val insns = CodeView.disassembleX86(mem.address, code.size)
        assertEquals(5, insns.size)
        assertEquals("push", insns[0].mnemonic)
        assertEquals("mov", insns[1].mnemonic)
        assertEquals("mov", insns[2].mnemonic)
        assertEquals("pop", insns[3].mnemonic)
        assertEquals("ret", insns[4].mnemonic)

        assertEquals(mem.address, insns[0].address)
        assertEquals(mem.address + 1, insns[1].address)

        mem.close()
    }

    @Test
    fun disassembleFromByteArray() {
        val code = byteArrayOf(
            0x90.toByte(),
            0x48.toByte(), 0x31.toByte(), 0xC0.toByte(),
            0xC3.toByte(),
        )
        val insns = CodeView.disassembleX86(code, 0x1000)
        assertEquals(3, insns.size)
        assertEquals("nop", insns[0].mnemonic)
        assertEquals(0x1000L, insns[0].address)
        assertEquals("xor", insns[1].mnemonic)
        assertEquals(0x1001L, insns[1].address)
        assertEquals("ret", insns[2].mnemonic)
    }
}
