package org.kgen.integration.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.reflect.*
import org.kgen.target.x86.X86Register
import org.kgen.target.x86.asm.X86Assembler

class HookTest {

    @Test
    fun inlineHookRedirectsExecution() {
        val targetAsm = X86Assembler()
        targetAsm.mov(X86Register.EAX, 1)
        targetAsm.ret()
        for (i in 0 until 8) targetAsm.emitByte(0x90)
        val targetCode = targetAsm.toByteArray()

        val replAsm = X86Assembler()
        replAsm.mov(X86Register.EAX, 42)
        replAsm.ret()
        val replacementCode = replAsm.toByteArray()

        NativeCode.loadBytes(targetCode, mapOf("target" to 0L)).use { target ->
            NativeCode.loadBytes(replacementCode, mapOf("repl" to 0L)).use { repl ->
                val targetAddr = target.symbolAddress("target")!!
                val replAddr = repl.symbolAddress("repl")!!

                assertEquals(1, target.callInt("target"))

                val hook = Hook.inlineHook(targetAddr, replAddr)
                assertEquals(42, target.callInt("target"))

                assertEquals(5, hook.originalBytes.size)
                assertEquals(0xB8.toByte(), hook.originalBytes[0])

                hook.unhook()
                assertEquals(1, target.callInt("target"))
            }
        }
    }

    @Test
    fun hookAutoCloseRestores() {
        val asm1 = X86Assembler()
        asm1.mov(X86Register.EAX, 1)
        asm1.ret()
        for (i in 0 until 8) asm1.emitByte(0x90)

        val asm2 = X86Assembler()
        asm2.mov(X86Register.EAX, -1)
        asm2.ret()

        NativeCode.loadBytes(asm1.toByteArray(), mapOf("f" to 0L)).use { module ->
            NativeCode.loadBytes(asm2.toByteArray(), mapOf("r" to 0L)).use { repl ->
                val fAddr = module.symbolAddress("f")!!
                val rAddr = repl.symbolAddress("r")!!

                Hook.inlineHook(fAddr, rAddr).use { hook ->
                    assertEquals(-1, module.callInt("f"))
                }
                assertEquals(1, module.callInt("f"))
            }
        }
    }
}
