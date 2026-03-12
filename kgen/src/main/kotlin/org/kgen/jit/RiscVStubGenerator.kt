package org.kgen.jit

import org.kgen.reflect.NativeMemory
import org.kgen.target.riscv.*
import org.kgen.target.riscv.asm.RiscVAssembler

/**
 * RISC-V (RV64) stub generator. Uses the RISC-V assembler to emit proper
 * machine code into executable memory. Uses T0 (x5) as scratch.
 */
class RiscVStubGenerator : StubGenerator {

    override fun generateCallStub(targetAddress: Long, paramCount: Int): RuntimeStub {
        val asm = RiscVAssembler()
        // Prologue: save ra and fp
        asm.addi(SP, SP, -16)
        asm.sd(RA, RiscVMemory(SP, 8))
        asm.sd(FP, RiscVMemory(SP, 0))
        asm.addi(FP, SP, 16)

        // Call target
        asm.li(X5, targetAddress)
        asm.jalr(RA, X5, 0)

        // Epilogue: restore ra and fp
        asm.ld(RA, RiscVMemory(SP, 8))
        asm.ld(FP, RiscVMemory(SP, 0))
        asm.addi(SP, SP, 16)
        asm.ret()
        return emitStub("call_stub_$paramCount", asm)
    }

    override fun generateTrampoline(targetAddress: Long): RuntimeStub {
        val asm = RiscVAssembler()
        asm.li(X5, targetAddress)
        asm.jalr(X0, X5, 0) // tail call: don't save return address
        return emitStub("trampoline", asm)
    }

    override fun generateSavingCallStub(targetAddress: Long): RuntimeStub {
        val asm = RiscVAssembler()

        // Save ra, fp, and callee-saved s1-s11 (12 regs = 96 bytes, plus ra/fp = 112, round to 112)
        asm.addi(SP, SP, -112)
        asm.sd(RA, RiscVMemory(SP, 104))
        asm.sd(FP, RiscVMemory(SP, 96))
        asm.sd(X9, RiscVMemory(SP, 88))   // s1
        asm.sd(X18, RiscVMemory(SP, 80))  // s2
        asm.sd(X19, RiscVMemory(SP, 72))  // s3
        asm.sd(X20, RiscVMemory(SP, 64))  // s4
        asm.sd(X21, RiscVMemory(SP, 56))  // s5
        asm.sd(X22, RiscVMemory(SP, 48))  // s6
        asm.sd(X23, RiscVMemory(SP, 40))  // s7
        asm.sd(X24, RiscVMemory(SP, 32))  // s8
        asm.sd(X25, RiscVMemory(SP, 24))  // s9
        asm.sd(X26, RiscVMemory(SP, 16))  // s10
        asm.sd(X27, RiscVMemory(SP, 8))   // s11
        asm.addi(FP, SP, 112)

        // Call target
        asm.li(X5, targetAddress)
        asm.jalr(RA, X5, 0)

        // Restore callee-saved
        asm.ld(X27, RiscVMemory(SP, 8))
        asm.ld(X26, RiscVMemory(SP, 16))
        asm.ld(X25, RiscVMemory(SP, 24))
        asm.ld(X24, RiscVMemory(SP, 32))
        asm.ld(X23, RiscVMemory(SP, 40))
        asm.ld(X22, RiscVMemory(SP, 48))
        asm.ld(X21, RiscVMemory(SP, 56))
        asm.ld(X20, RiscVMemory(SP, 64))
        asm.ld(X19, RiscVMemory(SP, 72))
        asm.ld(X18, RiscVMemory(SP, 80))
        asm.ld(X9, RiscVMemory(SP, 88))
        asm.ld(FP, RiscVMemory(SP, 96))
        asm.ld(RA, RiscVMemory(SP, 104))
        asm.addi(SP, SP, 112)
        asm.ret()
        return emitStub("saving_call_stub", asm)
    }

    override fun generateSafepointPoll(pollAddress: Long, slowPath: Long): RuntimeStub {
        val asm = RiscVAssembler()
        asm.li(X5, pollAddress)
        asm.lw(X6, RiscVMemory(X5, 0)) // load poll flag
        asm.ret()
        return emitStub("safepoint_poll", asm)
    }

    private fun emitStub(name: String, asm: RiscVAssembler): RuntimeStub {
        val code = asm.toByteArray()
        val mem = NativeMemory.allocateExecutable(code.size.toLong().coerceAtLeast(64))
        mem.write(0, code)
        return RuntimeStub(name, mem)
    }
}
