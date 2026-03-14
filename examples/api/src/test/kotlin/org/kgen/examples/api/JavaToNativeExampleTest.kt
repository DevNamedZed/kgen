package org.kgen.examples.api

import org.kgen.binary.elf.ElfReader
import org.kgen.ir.text.IrPrinter
import org.kgen.ir.target.Target
import org.kgen.runtime.compile.NativeCompiler
import org.kgen.runtime.compile.OutputPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

class JavaToNativeExampleTest {

    @Test
    fun buildCalculatorClassProducesValidClassFile() {
        val classBytes = JavaToNativeExample.buildCalculatorClass()
        assertTrue(classBytes.isNotEmpty())

        // Verify it starts with the class file magic number (0xCAFEBABE)
        assertTrue(classBytes[0] == 0xCA.toByte())
        assertTrue(classBytes[1] == 0xFE.toByte())
        assertTrue(classBytes[2] == 0xBA.toByte())
        assertTrue(classBytes[3] == 0xBE.toByte())
    }

    @Test
    fun compileToModulesProducesIr() {
        val classBytes = JavaToNativeExample.buildCalculatorClass()

        val compiler = NativeCompiler(
            Target.x86_64(),
            OutputPlatform.LINUX
        )
        val modules = compiler.compileToModules(listOf(classBytes))
        assertTrue(modules.isNotEmpty())

        val irText = IrPrinter.print(modules[0])
        assertTrue(irText.contains("define"))
    }

    @Test
    fun compileToNativeProducesExecutable() {
        val classBytes = JavaToNativeExample.buildCalculatorClass()
        val executable = JavaToNativeExample.compileToNative(classBytes)

        assertTrue(executable.isNotEmpty())
        assertTrue(executable.size > 100)
    }
}
