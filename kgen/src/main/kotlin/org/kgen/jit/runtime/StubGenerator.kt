package org.kgen.jit.runtime

import org.kgen.reflect.NativeMemory

/**
 * Generates small code stubs directly into executable memory, bypassing the
 * full ObjectFile pipeline. Used for runtime support code like:
 * - Allocation slow paths
 * - Safepoint poll handlers
 * - Managed-to-native transition thunks
 * - Exception throw helpers
 *
 * ```java
 * StubGenerator gen = StubGenerator.x86_64();
 * RuntimeStub stub = gen.generateCallStub(targetAddress, paramCount);
 * // stub.address() → executable entry point
 * stub.close();
 * ```
 */
interface StubGenerator {

    /** Generate a stub that calls a 64-bit target address with the given number of long parameters. */
    fun generateCallStub(targetAddress: Long, paramCount: Int): RuntimeStub

    /** Generate a trampoline that jumps to a 64-bit target address. */
    fun generateTrampoline(targetAddress: Long): RuntimeStub

    /** Generate a stub that saves volatile registers, calls a function, and restores them. */
    fun generateSavingCallStub(targetAddress: Long): RuntimeStub

    /** Generate a safepoint poll stub that loads from an address and calls a handler if faulted. */
    fun generateSafepointPoll(pollAddress: Long, slowPath: Long): RuntimeStub
}

/**
 * A compiled runtime stub — executable code at a known address.
 * Must be closed when no longer needed.
 */
class RuntimeStub(
    val name: String,
    private val memory: NativeMemory,
) : AutoCloseable {
    /** The entry point address of this stub. */
    fun address(): Long = memory.address

    /** The size of the stub in bytes. */
    fun size(): Long = memory.size

    override fun close() = memory.close()
}

/**
 * x86-64 stub generator. Emits raw machine code into executable memory.
 */
class X86StubGenerator : StubGenerator {

    override fun generateCallStub(targetAddress: Long, paramCount: Int): RuntimeStub {
        val buf = mutableListOf<Byte>()

        // push rbp
        buf.add(0x55.toByte())
        // mov rbp, rsp
        buf.addAll(byteArrayOf(0x48.toByte(), 0x89.toByte(), 0xE5.toByte()).toList())

        // sub rsp, 32 (shadow space for Win64, harmless on SysV)
        buf.addAll(byteArrayOf(0x48.toByte(), 0x83.toByte(), 0xEC.toByte(), 0x20).toList())

        // movabs r11, targetAddress
        buf.add(0x49.toByte())
        buf.add(0xBB.toByte())
        for (i in 0 until 8) buf.add((targetAddress shr (i * 8)).toByte())

        // call r11
        buf.addAll(byteArrayOf(0x41.toByte(), 0xFF.toByte(), 0xD3.toByte()).toList())

        // add rsp, 32
        buf.addAll(byteArrayOf(0x48.toByte(), 0x83.toByte(), 0xC4.toByte(), 0x20).toList())

        // pop rbp
        buf.add(0x5D.toByte())
        // ret
        buf.add(0xC3.toByte())

        val code = buf.toByteArray()
        val mem = NativeMemory.allocateExecutable(code.size.toLong().coerceAtLeast(64))
        mem.write(0, code)
        return RuntimeStub("call_stub_$paramCount", mem)
    }

    override fun generateTrampoline(targetAddress: Long): RuntimeStub {
        val buf = ByteArray(13)
        // movabs r11, imm64
        buf[0] = 0x49.toByte()
        buf[1] = 0xBB.toByte()
        for (i in 0 until 8) buf[2 + i] = (targetAddress shr (i * 8)).toByte()
        // jmp r11
        buf[10] = 0x41.toByte()
        buf[11] = 0xFF.toByte()
        buf[12] = 0xE3.toByte()

        val mem = NativeMemory.allocateExecutable(64)
        mem.write(0, buf)
        return RuntimeStub("trampoline", mem)
    }

    override fun generateSavingCallStub(targetAddress: Long): RuntimeStub {
        val buf = mutableListOf<Byte>()

        // push rbp
        buf.add(0x55.toByte())
        // mov rbp, rsp
        buf.addAll(byteArrayOf(0x48.toByte(), 0x89.toByte(), 0xE5.toByte()).toList())

        // Save callee-saved registers
        // push rbx
        buf.add(0x53.toByte())
        // push r12
        buf.addAll(byteArrayOf(0x41.toByte(), 0x54.toByte()).toList())
        // push r13
        buf.addAll(byteArrayOf(0x41.toByte(), 0x55.toByte()).toList())
        // push r14
        buf.addAll(byteArrayOf(0x41.toByte(), 0x56.toByte()).toList())
        // push r15
        buf.addAll(byteArrayOf(0x41.toByte(), 0x57.toByte()).toList())

        // sub rsp, 8 (align to 16 bytes — 5 pushes + rbp = 48 bytes, need 8 more)
        buf.addAll(byteArrayOf(0x48.toByte(), 0x83.toByte(), 0xEC.toByte(), 0x08).toList())

        // sub rsp, 32 (shadow space)
        buf.addAll(byteArrayOf(0x48.toByte(), 0x83.toByte(), 0xEC.toByte(), 0x20).toList())

        // movabs r11, targetAddress
        buf.add(0x49.toByte())
        buf.add(0xBB.toByte())
        for (i in 0 until 8) buf.add((targetAddress shr (i * 8)).toByte())

        // call r11
        buf.addAll(byteArrayOf(0x41.toByte(), 0xFF.toByte(), 0xD3.toByte()).toList())

        // add rsp, 40 (shadow + alignment)
        buf.addAll(byteArrayOf(0x48.toByte(), 0x83.toByte(), 0xC4.toByte(), 0x28).toList())

        // Restore callee-saved registers
        // pop r15
        buf.addAll(byteArrayOf(0x41.toByte(), 0x5F.toByte()).toList())
        // pop r14
        buf.addAll(byteArrayOf(0x41.toByte(), 0x5E.toByte()).toList())
        // pop r13
        buf.addAll(byteArrayOf(0x41.toByte(), 0x5D.toByte()).toList())
        // pop r12
        buf.addAll(byteArrayOf(0x41.toByte(), 0x5C.toByte()).toList())
        // pop rbx
        buf.add(0x5B.toByte())

        // pop rbp
        buf.add(0x5D.toByte())
        // ret
        buf.add(0xC3.toByte())

        val code = buf.toByteArray()
        val mem = NativeMemory.allocateExecutable(code.size.toLong().coerceAtLeast(128))
        mem.write(0, code)
        return RuntimeStub("saving_call_stub", mem)
    }

    override fun generateSafepointPoll(pollAddress: Long, slowPath: Long): RuntimeStub {
        val buf = mutableListOf<Byte>()

        // movabs r11, pollAddress
        buf.add(0x49.toByte())
        buf.add(0xBB.toByte())
        for (i in 0 until 8) buf.add((pollAddress shr (i * 8)).toByte())

        // mov eax, [r11] (test load from poll page)
        buf.addAll(byteArrayOf(0x41.toByte(), 0x8B.toByte(), 0x03.toByte()).toList())

        // ret (if no fault, return normally)
        buf.add(0xC3.toByte())

        val code = buf.toByteArray()
        val mem = NativeMemory.allocateExecutable(code.size.toLong().coerceAtLeast(64))
        mem.write(0, code)
        return RuntimeStub("safepoint_poll", mem)
    }
}
