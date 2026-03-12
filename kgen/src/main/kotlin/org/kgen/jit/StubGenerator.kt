package org.kgen.jit

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
