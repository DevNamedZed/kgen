package org.kgen.integration.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.reflect.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.backend.x86.codegen.X86CodeGenerator
import org.kgen.binary.elf.ElfSharedLinker
import java.io.File

class ProcessSymbolsTest {

    @Test
    fun lookupLibcSymbol() {
        val addr = ProcessSymbols.lookup("strlen")
        if (addr != null) {
            assertTrue(addr > 0)
            val insns = CodeView.disassembleX86(addr, 16)
            assertTrue(insns.isNotEmpty())
            assertNotEquals(0, insns[0].bytes.size)
        }
    }

    @Test
    fun lookupNonexistentSymbol() {
        val addr = ProcessSymbols.lookup("this_symbol_definitely_does_not_exist_12345")
        assertNull(addr)
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun buildAndLoadSharedLibrary() {
        val ir = IrBuilder("testlib", Target.x86_64())
        val addParams = ir.createFunction("kgen_add",
            listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.add(addParams[0], addParams[1]))
        ir.finalizeFunction()

        val mulParams = ir.createFunction("kgen_mul",
            listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.mul(mulParams[0], mulParams[1]))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val soBytes = ElfSharedLinker(soname = "libkgentest.so").link(listOf(obj))

        val soFile = File.createTempFile("libkgentest", ".so")
        soFile.deleteOnExit()
        soFile.writeBytes(soBytes)
        soFile.setExecutable(true)

        val lib = ProcessSymbols.loadLibrary(soFile.absolutePath)

        val addAddr = lib.find("kgen_add")
        val mulAddr = lib.find("kgen_mul")
        assertNotNull(addAddr)
        assertNotNull(mulAddr)

        NativeCode.loadBytes(byteArrayOf(0xC3.toByte()), mapOf("nop" to 0L)).use { dummy ->
            val addResult = dummy.callAt(addAddr!!, 3, 4)
            assertEquals(7L, addResult)

            val mulResult = dummy.callAt(mulAddr!!, 5, 6)
            assertEquals(30L, mulResult)
        }

        val insns = CodeView.disassembleX86(addAddr!!, 32)
        assertTrue(insns.isNotEmpty())

        lib.close()
        soFile.delete()
    }
}
