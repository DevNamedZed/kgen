package org.kgen.integration.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.reflect.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.X86Register
import org.kgen.target.x86.asm.X86Assembler
import org.kgen.target.x86.codegen.X86CodeGenerator

class NativeCodeTest {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    @Test
    fun assembleAndExecute() {
        val asm = X86Assembler()
        if (isWindows) {
            asm.mov(X86Register.RAX, X86Register.RCX)
            asm.imul(X86Register.RAX, X86Register.RCX)
        } else {
            asm.mov(X86Register.RAX, X86Register.RDI)
            asm.imul(X86Register.RAX, X86Register.RDI)
        }
        asm.ret()
        val code = asm.toByteArray()

        NativeCode.loadBytes(code, mapOf("square" to 0L)).use { module ->
            assertEquals(25L, module.call("square", 5))
            assertEquals(0L, module.call("square", 0))
            assertEquals(100L, module.call("square", -10))
        }
    }

    @Test
    fun irToExecutable() {
        val ir = ModuleBuilder("jit", Target.x86_64())
        if (isWindows) ir.targetTriple = "x86_64-unknown-windows-msvc"

        val params = ir.createFunction("add3",
            listOf(Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val ab = ir.add(params[0], params[1])
        ir.ret(ir.add(ab, params[2]))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        NativeCode.load(obj).use { module ->
            assertEquals(6L, module.call("add3", 1, 2, 3))
            assertEquals(0L, module.call("add3", 0, 0, 0))
            assertEquals(60L, module.call("add3", 10, 20, 30))
        }
    }

    @Test
    fun multipleFunctionsFromIr() {
        val ir = ModuleBuilder("multi", Target.x86_64())
        if (isWindows) ir.targetTriple = "x86_64-unknown-windows-msvc"

        val addParams = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.add(addParams[0], addParams[1]))
        ir.finalizeFunction()

        val subParams = ir.createFunction("sub", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.sub(subParams[0], subParams[1]))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        NativeCode.load(obj).use { module ->
            assertTrue("add" in module.symbols())
            assertTrue("sub" in module.symbols())
            assertNotNull(module.symbolAddress("add"))
            assertNotNull(module.symbolAddress("sub"))
            assertNull(module.symbolAddress("nonexistent"))

            assertEquals(7L, module.call("add", 3, 4))
            assertEquals(2L, module.call("sub", 5, 3))

            assertNotEquals(module.symbolAddress("add"), module.symbolAddress("sub"))
        }
    }
}
