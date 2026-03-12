package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.reflect.NativeMemory
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.*

/**
 * Tests that generated stubs produce correct executable code
 * by actually calling them via FFM.
 */
@EnabledOnOs(OS.LINUX, OS.WINDOWS)
class StubGeneratorExecutionTest {

    private val linker = Linker.nativeLinker()

    @Test
    fun trampolineCallsTarget() {
        // Create a simple "return 42" function in executable memory
        val returnFunc = createReturn42Stub()
        val gen = X86StubGenerator()
        val trampoline = gen.generateTrampoline(returnFunc.address())

        // Call the trampoline — it should jump to returnFunc and return 42
        val segment = MemorySegment.ofAddress(trampoline.address())
        val handle = linker.downcallHandle(segment, FunctionDescriptor.of(JAVA_LONG))
        val result = handle.invoke() as Long
        assertEquals(42L, result)

        trampoline.close()
        returnFunc.close()
    }

    @Test
    fun callStubCallsTarget() {
        val returnFunc = createReturn42Stub()
        val gen = X86StubGenerator()
        val stub = gen.generateCallStub(returnFunc.address(), 0)

        val segment = MemorySegment.ofAddress(stub.address())
        val handle = linker.downcallHandle(segment, FunctionDescriptor.of(JAVA_LONG))
        val result = handle.invoke() as Long
        assertEquals(42L, result)

        stub.close()
        returnFunc.close()
    }

    @Test
    fun savingCallStubCallsTarget() {
        val returnFunc = createReturn42Stub()
        val gen = X86StubGenerator()
        val stub = gen.generateSavingCallStub(returnFunc.address())

        val segment = MemorySegment.ofAddress(stub.address())
        val handle = linker.downcallHandle(segment, FunctionDescriptor.of(JAVA_LONG))
        val result = handle.invoke() as Long
        assertEquals(42L, result)

        stub.close()
        returnFunc.close()
    }

    @Test
    fun trampolineChainsWork() {
        val returnFunc = createReturn42Stub()
        val gen = X86StubGenerator()

        // Chain: trampoline1 → trampoline2 → returnFunc
        val t2 = gen.generateTrampoline(returnFunc.address())
        val t1 = gen.generateTrampoline(t2.address())

        val segment = MemorySegment.ofAddress(t1.address())
        val handle = linker.downcallHandle(segment, FunctionDescriptor.of(JAVA_LONG))
        val result = handle.invoke() as Long
        assertEquals(42L, result)

        t1.close()
        t2.close()
        returnFunc.close()
    }

    @Test
    fun trampolineWithDifferentReturnValues() {
        val gen = X86StubGenerator()
        for (expected in listOf(0L, 1L, -1L, 100L, 0x7FFFFFFFL)) {
            val func = createReturnValueStub(expected)
            val trampoline = gen.generateTrampoline(func.address())

            val segment = MemorySegment.ofAddress(trampoline.address())
            val handle = linker.downcallHandle(segment, FunctionDescriptor.of(JAVA_LONG))
            val result = handle.invoke() as Long
            assertEquals(expected, result, "Expected $expected")

            trampoline.close()
            func.close()
        }
    }

    @Test
    fun callStubWithReturnValues() {
        val gen = X86StubGenerator()
        for (expected in listOf(0L, 99L, -42L)) {
            val func = createReturnValueStub(expected)
            val stub = gen.generateCallStub(func.address(), 0)

            val segment = MemorySegment.ofAddress(stub.address())
            val handle = linker.downcallHandle(segment, FunctionDescriptor.of(JAVA_LONG))
            val result = handle.invoke() as Long
            assertEquals(expected, result)

            stub.close()
            func.close()
        }
    }

    /**
     * Create a stub that returns the constant 42:
     *   mov rax, 42
     *   ret
     */
    private fun createReturn42Stub(): RuntimeStub = createReturnValueStub(42L)

    /**
     * Create a stub that returns a given constant:
     *   movabs rax, <value>   ; 48 B8 <8 bytes>
     *   ret                   ; C3
     */
    private fun createReturnValueStub(value: Long): RuntimeStub {
        val code = ByteArray(11)
        code[0] = 0x48.toByte() // REX.W
        code[1] = 0xB8.toByte() // movabs rax
        for (i in 0 until 8) code[2 + i] = (value shr (i * 8)).toByte()
        code[10] = 0xC3.toByte() // ret

        val mem = NativeMemory.allocateExecutable(64)
        mem.write(0, code)
        return RuntimeStub("return_$value", mem)
    }
}
