package org.kgen.jit

import org.kgen.reflect.NativeMemory
import org.kgen.target.arm64.Arm64Register
import org.kgen.target.arm64.Arm64Register32
import org.kgen.target.arm64.Arm64Register64
import org.kgen.target.arm64.asm.Arm64Assembler

/**
 * ARM64 (AArch64) stub generator. Uses the ARM64 assembler to emit proper
 * machine code into executable memory. Uses X16 (IP0) as scratch.
 */
class Arm64StubGenerator : StubGenerator {

    override fun generateCallStub(targetAddress: Long, paramCount: Int): RuntimeStub {
        val asm = Arm64Assembler()
        asm.stpPre(Arm64Register.X29, Arm64Register.X30, Arm64Register.SP, -16)
        asm.mov(Arm64Register.X29, Arm64Register.SP)
        emitLoadImm64(asm, Arm64Register.X16, targetAddress)
        asm.blr(Arm64Register.X16)
        asm.ldpPost(Arm64Register.X29, Arm64Register.X30, Arm64Register.SP, 16)
        asm.ret()
        return emitStub("call_stub_$paramCount", asm)
    }

    override fun generateTrampoline(targetAddress: Long): RuntimeStub {
        val asm = Arm64Assembler()
        emitLoadImm64(asm, Arm64Register.X16, targetAddress)
        asm.br(Arm64Register.X16)
        return emitStub("trampoline", asm)
    }

    override fun generateSavingCallStub(targetAddress: Long): RuntimeStub {
        val asm = Arm64Assembler()

        asm.stpPre(Arm64Register.X29, Arm64Register.X30, Arm64Register.SP, -16)
        asm.mov(Arm64Register.X29, Arm64Register.SP)

        // Save callee-saved X19-X28 (5 pairs)
        asm.stpPre(Arm64Register.X19, Arm64Register.X20, Arm64Register.SP, -16)
        asm.stpPre(Arm64Register.X21, Arm64Register.X22, Arm64Register.SP, -16)
        asm.stpPre(Arm64Register.X23, Arm64Register.X24, Arm64Register.SP, -16)
        asm.stpPre(Arm64Register.X25, Arm64Register.X26, Arm64Register.SP, -16)
        asm.stpPre(Arm64Register.X27, Arm64Register.X28, Arm64Register.SP, -16)

        emitLoadImm64(asm, Arm64Register.X16, targetAddress)
        asm.blr(Arm64Register.X16)

        // Restore callee-saved (reverse order)
        asm.ldpPost(Arm64Register.X27, Arm64Register.X28, Arm64Register.SP, 16)
        asm.ldpPost(Arm64Register.X25, Arm64Register.X26, Arm64Register.SP, 16)
        asm.ldpPost(Arm64Register.X23, Arm64Register.X24, Arm64Register.SP, 16)
        asm.ldpPost(Arm64Register.X21, Arm64Register.X22, Arm64Register.SP, 16)
        asm.ldpPost(Arm64Register.X19, Arm64Register.X20, Arm64Register.SP, 16)

        asm.ldpPost(Arm64Register.X29, Arm64Register.X30, Arm64Register.SP, 16)
        asm.ret()
        return emitStub("saving_call_stub", asm)
    }

    override fun generateSafepointPoll(pollAddress: Long, slowPath: Long): RuntimeStub {
        val asm = Arm64Assembler()
        emitLoadImm64(asm, Arm64Register.X16, pollAddress)
        asm.ldr(Arm64Register.W17, Arm64Register.X16)
        asm.ret()
        return emitStub("safepoint_poll", asm)
    }

    private fun emitLoadImm64(asm: Arm64Assembler, rd: Arm64Register64, value: Long) {
        asm.movz(rd, (value and 0xFFFF).toInt(), 0)
        val h1 = ((value shr 16) and 0xFFFF).toInt()
        if (h1 != 0) asm.movk(rd, h1, 16)
        val h2 = ((value shr 32) and 0xFFFF).toInt()
        if (h2 != 0) asm.movk(rd, h2, 32)
        val h3 = ((value shr 48) and 0xFFFF).toInt()
        if (h3 != 0) asm.movk(rd, h3, 48)
    }

    private fun emitStub(name: String, asm: Arm64Assembler): RuntimeStub {
        val code = asm.bytes()
        val mem = NativeMemory.allocateExecutable(code.size.toLong().coerceAtLeast(64))
        mem.write(0, code)
        return RuntimeStub(name, mem)
    }
}
