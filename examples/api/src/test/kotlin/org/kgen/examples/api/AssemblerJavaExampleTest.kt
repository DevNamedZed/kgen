package org.kgen.examples.api

import org.kgen.target.x86.disasm.X86Disassembler
import kotlin.test.Test
import kotlin.test.assertTrue

class AssemblerJavaExampleTest {

    @Test
    fun assembleAddMatchesKotlinVersion() {
        val javaCode = AssemblerJavaExample.assembleAddFunction()
        val kotlinCode = AssemblerExample.assembleAddFunction()
        assertTrue(javaCode.contentEquals(kotlinCode))
    }

    @Test
    fun assembleSumLoopMatchesKotlinVersion() {
        val javaCode = AssemblerJavaExample.assembleSumLoop()
        val kotlinCode = AssemblerExample.assembleSumLoop()
        assertTrue(javaCode.contentEquals(kotlinCode))
    }

    @Test
    fun codeGenProducesCompiledCode() {
        val compiled = AssemblerJavaExample.compileIrToMachineCode()
        assertTrue(compiled.textBytes.isNotEmpty())
        assertTrue(compiled.symbols.isNotEmpty())
    }
}
