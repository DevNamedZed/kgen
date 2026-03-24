package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator

/**
 * Reproduces DOOM func_34's exact IR pattern. This function:
 * 1. Loads a counter from a fixed WASM address
 * 2. Compares p0 with a value at another address
 * 3. If not equal: increments counter, stores counter back,
 *    then stores trunc(p0, I8) at mem[counter_old_value]
 *
 * The JIT bug: the byte store goes to the wrong address, or
 * the wrong value is stored.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitCodegenFunc34Test {

    private fun createContext(memoryBase: Long, memorySize: Long): Pair<java.lang.foreign.Arena, java.lang.foreign.MemorySegment> {
        val arena = java.lang.foreign.Arena.ofShared()
        val ctx = arena.allocate(40, 8)
        ctx.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, memoryBase)
        ctx.set(java.lang.foreign.ValueLayout.JAVA_LONG, 8, memorySize)
        return arena to ctx
    }

    /**
     * Exact reproduction of func_34's br_if_cont_2 path:
     * - Load counter from mem[counterAddr]
     * - Add 1 to counter
     * - Store incremented counter back to mem[counterAddr]
     * - ZExt old counter to I64
     * - Compute store address: membase + zext(old_counter)
     * - Trunc p0 to I8
     * - Store truncated byte at computed address
     */
    @Test
    fun byteStoreAtCounterAddress() {
        val module = ModuleBuilder("test", Target.x86_64()).apply {
            val params = createFunction("f", listOf(
                Param("ctx", Type.I64),
                Param("p0", Type.I32),
            ), Type.I32)
            appendBlock("entry")

            // Load counter from fixed address (like mem[0x4277DC])
            val counterWasmAddr = 100L // simplified
            val ctxPtr = add(params[0], Constant.I64(0))
            val membase = load(Type.I64, ctxPtr)
            val counterAddr = add(membase, Constant.I64(counterWasmAddr))
            val counter = load(Type.I32, counterAddr)

            // Increment counter and store back
            val incremented = add(counter, Constant.I32(1))
            store(incremented, counterAddr)

            // Compute byte store address: membase + zext(counter)
            val counterExt = zext(counter, Type.I64)
            val membase2 = load(Type.I64, add(params[0], Constant.I64(0)))
            val byteAddr = add(membase2, counterExt)

            // Truncate p0 to byte and store
            val byteValue = trunc(params[1], Type.I8)
            store(byteValue, byteAddr)

            ret(Constant.I32(0))
            finalizeFunction()
        }.build()

        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val memSize = 1024
        val mem = arena.allocate(memSize.toLong(), 8)

        // Set counter at offset 100 to value 200
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 200)
        // Set a marker at offset 200 (where byte should be stored)
        mem.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 200, 0xFF.toByte())
        // Set a marker at another offset that should NOT be modified
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 300, 0x12345678)

        val (ctxArena, ctx) = createContext(mem.address(), memSize.toLong())

        // Call f(ctx, 0x20) — p0=32
        engine.call("f", ctx.address(), 32)

        // Verify: counter at offset 100 should be 201
        val newCounter = mem.get(java.lang.foreign.ValueLayout.JAVA_INT, 100)
        assertEquals(201, newCounter, "Counter should be incremented to 201")

        // Verify: byte at offset 200 should be 0x20 (trunc(32, I8))
        val storedByte = mem.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 200)
        assertEquals(0x20.toByte(), storedByte, "Byte 0x20 should be stored at mem[old_counter]")

        // Verify: offset 300 should NOT be modified
        val marker = mem.get(java.lang.foreign.ValueLayout.JAVA_INT, 300)
        assertEquals(0x12345678, marker, "Other memory should not be modified")

        // Run again — counter should now be 202, byte at 201 should be 0x20
        engine.call("f", ctx.address(), 32)
        assertEquals(202, mem.get(java.lang.foreign.ValueLayout.JAVA_INT, 100), "Counter should be 202")
        assertEquals(0x20.toByte(), mem.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 201), "Byte 0x20 at offset 201")

        ctxArena.close()
        arena.close()
    }

    /**
     * Same pattern but in a large module (800 functions).
     * The DOOM bug only manifests at scale.
     */
    @Test
    fun byteStoreAtCounterAddressLargeModule() {
        val module = ModuleBuilder("test", Target.x86_64()).apply {
            // 800 filler functions with realistic register pressure
            for (idx in 0 until 800) {
                val fillerParams = createFunction("filler_$idx", listOf(Param("ctx", Type.I64), Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                // Create 10 live values to force spilling (like DOOM functions)
                val base = load(Type.I64, fillerParams[0])
                val values = mutableListOf<Value>()
                for (field in 0 until 10) {
                    values.add(load(Type.I32, add(base, Constant.I64(field.toLong() * 4))))
                }
                var sum = values[0]
                for (field in 1 until values.size) {
                    sum = add(sum, values[field])
                }
                ret(add(sum, fillerParams[1]))
                finalizeFunction()
            }

            // The test function — same as byteStoreAtCounterAddress
            val params = createFunction("f", listOf(
                Param("ctx", Type.I64),
                Param("p0", Type.I32),
            ), Type.I32)
            appendBlock("entry")

            val ctxPtr = add(params[0], Constant.I64(0))
            val membase = load(Type.I64, ctxPtr)
            val counterAddr = add(membase, Constant.I64(100))
            val counter = load(Type.I32, counterAddr)
            val incremented = add(counter, Constant.I32(1))
            store(incremented, counterAddr)

            val counterExt = zext(counter, Type.I64)
            val membase2 = load(Type.I64, add(params[0], Constant.I64(0)))
            val byteAddr = add(membase2, counterExt)
            val byteValue = trunc(params[1], Type.I8)
            store(byteValue, byteAddr)

            ret(Constant.I32(0))
            finalizeFunction()
        }.build()

        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val memSize = 1024
        val mem = arena.allocate(memSize.toLong(), 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 200)
        mem.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 200, 0xFF.toByte())
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 300, 0x12345678)

        val (ctxArena, ctx) = createContext(mem.address(), memSize.toLong())

        engine.call("f", ctx.address(), 32)

        assertEquals(201, mem.get(java.lang.foreign.ValueLayout.JAVA_INT, 100), "Counter incremented")
        assertEquals(0x20.toByte(), mem.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 200), "Byte stored correctly")
        assertEquals(0x12345678, mem.get(java.lang.foreign.ValueLayout.JAVA_INT, 300), "Other memory untouched")

        ctxArena.close()
        arena.close()
    }

    /**
     * Same test but with I8 store using a separately loaded value
     * (tests that trunc + store I8 produces the right byte at the right address)
     */
    @Test
    fun truncAndByteStore() {
        val module = ModuleBuilder("test", Target.x86_64()).apply {
            val params = createFunction("f", listOf(
                Param("base", Type.I64),
                Param("offset", Type.I32),
                Param("value", Type.I32),
            ), Type.Void)
            appendBlock("entry")

            val ext = zext(params[1], Type.I64)
            val addr = add(params[0], ext)
            val truncated = trunc(params[2], Type.I8)
            store(truncated, addr)
            ret()
            finalizeFunction()
        }.build()

        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(256, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 0x12345678)

        // Store byte 0x20 at offset 100 — should only change first byte
        engine.call("f", mem.address(), 100, 0x20)

        val result = mem.get(java.lang.foreign.ValueLayout.JAVA_INT, 100)
        // Little-endian: byte at offset 100 = 0x20, rest unchanged
        // 0x12345678 → bytes: 78 56 34 12 → after store: 20 56 34 12 = 0x12345620
        assertEquals(0x12345620, result, "Only low byte should be changed to 0x20")

        arena.close()
    }

    /**
     * Test that two stores to different addresses don't interfere.
     * func_34's pattern: store I32 to addr_A, then store I8 to addr_B.
     */
    @Test
    fun twoStoresToDifferentAddresses() {
        val module = ModuleBuilder("test", Target.x86_64()).apply {
            val params = createFunction("f", listOf(
                Param("ctx", Type.I64),
                Param("p0", Type.I32),
            ), Type.I32)
            appendBlock("entry")

            val membase = load(Type.I64, params[0])

            // Store 1: write I32 value to addr_A (offset 100)
            val addrA = add(membase, Constant.I64(100))
            val oldValue = load(Type.I32, addrA)
            val newValue = add(oldValue, Constant.I32(1))
            store(newValue, addrA)

            // Store 2: write I8 byte to addr_B (offset = old value from store 1)
            val addrBOffset = zext(oldValue, Type.I64)
            val membase2 = load(Type.I64, params[0])
            val addrB = add(membase2, addrBOffset)
            val byteVal = trunc(params[1], Type.I8)
            store(byteVal, addrB)

            // Also read from a third address to create register pressure
            val addrC = add(membase, Constant.I64(200))
            val readVal = load(Type.I32, addrC)

            ret(readVal)
            finalizeFunction()
        }.build()

        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(1024, 8)
        val (ctxArena, ctx) = createContext(mem.address(), 1024)

        // Setup: counter at offset 100 = 500, check area at offset 500
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 500)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 200, 42)
        mem.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 500, 0xFF.toByte())

        // Call: p0=65 (0x41 = 'A')
        val result = engine.call("f", ctx.address(), 65)

        // Verify store 1: counter incremented
        assertEquals(501, mem.get(java.lang.foreign.ValueLayout.JAVA_INT, 100), "Counter incremented")

        // Verify store 2: byte 0x41 written at offset 500
        assertEquals(0x41.toByte(), mem.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 500), "Byte stored at old counter address")

        // Verify return value
        assertEquals(42L, result, "Read from offset 200")

        // Verify nearby memory not corrupted
        assertEquals(0, mem.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 501).toInt(), "Offset 501 untouched")

        // Run again: counter now 501, byte at 501
        engine.call("f", ctx.address(), 66) // 0x42 = 'B'
        assertEquals(502, mem.get(java.lang.foreign.ValueLayout.JAVA_INT, 100), "Counter 502")
        assertEquals(0x42.toByte(), mem.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 501), "Byte 'B' at 501")
        assertEquals(0x41.toByte(), mem.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 500), "Previous byte at 500 unchanged")

        ctxArena.close()
        arena.close()
    }
}
