package org.kgen.integration.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.reflect.*
import org.kgen.target.x86.X86Register
import org.kgen.target.x86.asm.X86Assembler

class CodePatchTest {

    @Test
    fun patchLiveFunction() {
        val asm = X86Assembler()
        asm.mov(X86Register.EAX, 1)
        asm.ret()
        val code = asm.toByteArray()

        NativeCode.loadBytes(code, mapOf("f" to 0L)).use { module ->
            assertEquals(1, module.callInt("f"))

            module.patchCode(1, byteArrayOf(0x63, 0x00, 0x00, 0x00))
            assertEquals(99, module.callInt("f"))

            val asm2 = X86Assembler()
            asm2.mov(X86Register.EAX, -1)
            asm2.ret()
            module.patchCode(0, asm2.toByteArray())
            assertEquals(-1, module.callInt("f"))
        }
    }

    @Test
    fun nopOutInstructions() {
        val code = byteArrayOf(
            0xB8.toByte(), 0x05, 0x00, 0x00, 0x00,
            0x83.toByte(), 0xC0.toByte(), 0x0A,
            0xC3.toByte(),
        )
        NativeCode.loadBytes(code, mapOf("f" to 0L)).use { module ->
            assertEquals(15, module.callInt("f"))

            CodePatch.writeNop(module.symbolAddress("f")!! + 5, 3)
            assertEquals(5, module.callInt("f"))
        }
    }
}
