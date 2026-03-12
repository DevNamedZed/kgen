package org.kgen.jit

import org.kgen.reflect.NativeMemory
import org.kgen.target.x86.X86Memory
import org.kgen.target.x86.X86Operand64
import org.kgen.target.x86.X86Register
import org.kgen.target.x86.asm.X86Assembler

/**
 * x86-64 stub generator. Uses the x86 assembler to emit proper
 * machine code into executable memory.
 */
class X86StubGenerator : StubGenerator {

    override fun generateCallStub(targetAddress: Long, paramCount: Int): RuntimeStub {
        val asm = X86Assembler()
        asm.push(X86Register.RBP)
        asm.mov(X86Register.RBP, X86Register.RSP)
        asm.sub(X86Register.RSP as X86Operand64, 32.toByte()) // shadow space (Win64), harmless on SysV
        asm.mov(X86Register.R11, targetAddress)
        asm.call(X86Register.R11)
        asm.add(X86Register.RSP as X86Operand64, 32.toByte())
        asm.pop(X86Register.RBP)
        asm.ret()
        return emitStub("call_stub_$paramCount", asm)
    }

    override fun generateTrampoline(targetAddress: Long): RuntimeStub {
        val asm = X86Assembler()
        asm.mov(X86Register.R11, targetAddress)
        asm.jmp(X86Register.R11)
        return emitStub("trampoline", asm)
    }

    override fun generateSavingCallStub(targetAddress: Long): RuntimeStub {
        val asm = X86Assembler()
        asm.push(X86Register.RBP)
        asm.mov(X86Register.RBP, X86Register.RSP)

        // Save callee-saved registers
        asm.push(X86Register.RBX)
        asm.push(X86Register.R12)
        asm.push(X86Register.R13)
        asm.push(X86Register.R14)
        asm.push(X86Register.R15)

        // Align to 16 bytes (5 pushes + rbp = 48, need 8 more) + shadow space
        asm.sub(X86Register.RSP as X86Operand64, 8.toByte())
        asm.sub(X86Register.RSP as X86Operand64, 32.toByte())

        asm.mov(X86Register.R11, targetAddress)
        asm.call(X86Register.R11)

        // Restore
        asm.add(X86Register.RSP as X86Operand64, 40.toByte()) // shadow + alignment
        asm.pop(X86Register.R15)
        asm.pop(X86Register.R14)
        asm.pop(X86Register.R13)
        asm.pop(X86Register.R12)
        asm.pop(X86Register.RBX)
        asm.pop(X86Register.RBP)
        asm.ret()
        return emitStub("saving_call_stub", asm)
    }

    override fun generateSafepointPoll(pollAddress: Long, slowPath: Long): RuntimeStub {
        val asm = X86Assembler()
        asm.mov(X86Register.R11, pollAddress)
        asm.mov(X86Register.EAX, X86Memory.base(X86Register.R11).offset(0))
        asm.ret()
        return emitStub("safepoint_poll", asm)
    }

    private fun emitStub(name: String, asm: X86Assembler): RuntimeStub {
        val code = asm.toByteArray()
        val mem = NativeMemory.allocateExecutable(code.size.toLong().coerceAtLeast(64))
        mem.write(0, code)
        return RuntimeStub(name, mem)
    }
}
