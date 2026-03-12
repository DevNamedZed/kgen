package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class JvmHooksTest {

    @Test
    fun interceptMethodWithBeforeHook() {
        val method = String::class.java.getMethod("length")
        val calls = mutableListOf<String>()
        val hook = JvmHooks.intercept(
            target = method,
            before = { args -> calls.add("before") },
        )
        assertTrue(hook.isActive())
        assertEquals(method, hook.method())
        val result = hook.call("hello")
        assertEquals(5, result)
        assertEquals(listOf("before"), calls)
    }

    @Test
    fun interceptMethodWithAfterHook() {
        val method = String::class.java.getMethod("length")
        val results = mutableListOf<Any?>()
        val hook = JvmHooks.intercept(
            target = method,
            after = { result -> results.add(result) },
        )
        hook.call("test")
        assertEquals(listOf(4), results)
    }

    @Test
    fun interceptMethodWithBothHooks() {
        val method = String::class.java.getMethod("length")
        val log = mutableListOf<String>()
        val hook = JvmHooks.intercept(
            target = method,
            before = { log.add("before") },
            after = { log.add("after:$it") },
        )
        val result = hook.call("abc")
        assertEquals(3, result)
        assertEquals(listOf("before", "after:3"), log)
    }

    @Test
    fun interceptCallOriginalBypassesHooks() {
        val method = String::class.java.getMethod("length")
        val calls = mutableListOf<String>()
        val hook = JvmHooks.intercept(
            target = method,
            before = { calls.add("before") },
            after = { calls.add("after") },
        )
        val result = hook.callOriginal("hello")
        assertEquals(5, result)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun interceptUnhookDeactivates() {
        val method = String::class.java.getMethod("length")
        val hook = JvmHooks.intercept(target = method)
        assertTrue(hook.isActive())
        hook.unhook()
        assertFalse(hook.isActive())
    }

    @Test
    fun interceptNoHooksStillCallsMethod() {
        val method = String::class.java.getMethod("length")
        val hook = JvmHooks.intercept(target = method)
        val result = hook.call("world")
        assertEquals(5, result)
    }

    @Test
    fun wrapMethodHandleWithBeforeAfter() {
        val lookup = java.lang.invoke.MethodHandles.lookup()
        val handle = lookup.findVirtual(
            String::class.java, "length",
            java.lang.invoke.MethodType.methodType(Int::class.java),
        )
        val log = mutableListOf<String>()
        val wrapped = JvmHooks.wrapMethodHandle(
            target = handle,
            before = Runnable { log.add("before") },
            after = Runnable { log.add("after") },
        )
        val result = wrapped.invoke("test")
        assertEquals(4, result)
        assertEquals(listOf("before", "after"), log)
    }

    @Test
    fun wrapMethodHandleNoCallbacks() {
        val lookup = java.lang.invoke.MethodHandles.lookup()
        val handle = lookup.findVirtual(
            String::class.java, "length",
            java.lang.invoke.MethodType.methodType(Int::class.java),
        )
        val wrapped = JvmHooks.wrapMethodHandle(target = handle)
        val result = wrapped.invoke("abc")
        assertEquals(3, result)
    }

    @Test
    fun wrapMethodHandleReturnsOriginalHandle() {
        val lookup = java.lang.invoke.MethodHandles.lookup()
        val handle = lookup.findVirtual(
            String::class.java, "length",
            java.lang.invoke.MethodType.methodType(Int::class.java),
        )
        val wrapped = JvmHooks.wrapMethodHandle(target = handle)
        assertSame(handle, wrapped.handle())
    }

    @Test
    fun generateJumpProduces12Bytes() {
        val bytes = JvmHooks.generateJump(0x7F00DEADBEEF)
        assertEquals(12, bytes.size)
        // REX.W prefix
        assertEquals(0x48.toByte(), bytes[0])
        // mov rax, imm64
        assertEquals(0xB8.toByte(), bytes[1])
        // jmp rax
        assertEquals(0xFF.toByte(), bytes[10])
        assertEquals(0xE0.toByte(), bytes[11])
    }

    @Test
    fun generateJumpEncodesAddressLittleEndian() {
        val addr = 0x0102030405060708L
        val bytes = JvmHooks.generateJump(addr)
        assertEquals(0x08.toByte(), bytes[2])
        assertEquals(0x07.toByte(), bytes[3])
        assertEquals(0x06.toByte(), bytes[4])
        assertEquals(0x05.toByte(), bytes[5])
        assertEquals(0x04.toByte(), bytes[6])
        assertEquals(0x03.toByte(), bytes[7])
        assertEquals(0x02.toByte(), bytes[8])
        assertEquals(0x01.toByte(), bytes[9])
    }

    @Test
    fun generateBreakpointDefaultCount() {
        val bytes = JvmHooks.generateBreakpoint()
        assertEquals(1, bytes.size)
        assertEquals(0xCC.toByte(), bytes[0])
    }

    @Test
    fun generateBreakpointMultiple() {
        val bytes = JvmHooks.generateBreakpoint(5)
        assertEquals(5, bytes.size)
        for (b in bytes) {
            assertEquals(0xCC.toByte(), b)
        }
    }

    @Test
    fun generateNops() {
        val bytes = JvmHooks.generateNops(8)
        assertEquals(8, bytes.size)
        for (b in bytes) {
            assertEquals(0x90.toByte(), b)
        }
    }

    @Test
    fun jitPatchProperties() {
        val original = byteArrayOf(0x48, 0x89.toByte(), 0xE5.toByte())
        val patch = byteArrayOf(0x90.toByte(), 0x90.toByte(), 0x90.toByte())
        val jitPatch = JvmHooks.JitPatch(0x1000L, original, patch)
        assertTrue(jitPatch.isActive())
        assertEquals(0x1000L, jitPatch.address())
        assertArrayEquals(original, jitPatch.originalBytes())
        assertArrayEquals(patch, jitPatch.patchBytes())
    }

    @Test
    fun jitPatchOriginalBytesReturnsCopy() {
        val original = byteArrayOf(0x48, 0x89.toByte())
        val jitPatch = JvmHooks.JitPatch(0x1000L, original, byteArrayOf(0x90.toByte(), 0x90.toByte()))
        val copy = jitPatch.originalBytes()
        copy[0] = 0x00
        // Original should be unmodified
        assertEquals(0x48.toByte(), jitPatch.originalBytes()[0])
    }

    @Test
    fun methodInterceptionProperties() {
        val method = String::class.java.getMethod("length")
        val hook = JvmHooks.intercept(target = method)
        assertTrue(hook.isActive())
        assertEquals(method, hook.method())
    }

    @Test
    fun interceptMethodWithArguments() {
        val method = String::class.java.getMethod("substring", Int::class.java, Int::class.java)
        val capturedArgs = mutableListOf<Array<Any?>>()
        val hook = JvmHooks.intercept(
            target = method,
            before = { args -> capturedArgs.add(args.copyOf()) },
        )
        val result = hook.call("hello world", 0, 5)
        assertEquals("hello", result)
        assertEquals(1, capturedArgs.size)
    }
}
