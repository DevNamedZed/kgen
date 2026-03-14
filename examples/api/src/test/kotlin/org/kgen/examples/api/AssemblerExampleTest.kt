package org.kgen.examples.api

import org.kgen.target.x86.disasm.X86Disassembler
import kotlin.test.Test
import kotlin.test.assertTrue

class AssemblerExampleTest {

    @Test
    fun assembleAddProducesValidMachineCode() {
        val code = AssemblerExample.assembleAddFunction()
        assertTrue(code.isNotEmpty())

        val disassembler = X86Disassembler()
        val instructions = disassembler.disassemble(code, 0)
        assertTrue(instructions.isNotEmpty())

        val mnemonics = instructions.map { it.mnemonic }
        assertTrue(mnemonics.any { it == "lea" })
        assertTrue(mnemonics.any { it == "ret" })
    }

    @Test
    fun assembleSumLoopProducesValidMachineCode() {
        val code = AssemblerExample.assembleSumLoop()
        assertTrue(code.isNotEmpty())

        val disassembler = X86Disassembler()
        val instructions = disassembler.disassemble(code, 0)

        val mnemonics = instructions.map { it.mnemonic }
        assertTrue(mnemonics.any { it == "xor" })
        assertTrue(mnemonics.any { it == "add" })
        assertTrue(mnemonics.any { it == "dec" })
        assertTrue(mnemonics.any { it == "ret" })
    }

    @Test
    fun codeGenProducesCompiledCode() {
        val compiled = AssemblerExample.compileIrToMachineCode()

        assertTrue(compiled.textBytes.isNotEmpty())
        assertTrue(compiled.symbols.isNotEmpty())

        val multiplySymbol = compiled.symbols.find { it.name == "multiply" }
        assertTrue(multiplySymbol != null)
    }
}
