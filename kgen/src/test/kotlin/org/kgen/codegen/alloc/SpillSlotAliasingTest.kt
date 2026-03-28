package org.kgen.codegen.alloc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.jit.JitEngine
import org.kgen.target.x86.codegen.X86CodeGenerator

/**
 * Tests for spill slot correctness in the register allocator.
 *
 * Bug 1 (slot aliasing): The codegen eagerly stores to spill slots at the
 * definition site. If the allocator reuses a slot whose previous occupant's
 * liveness overlaps, the eager store corrupts the value.
 *
 * Bug 2 (evicted param): A parameter initially in its ABI register but later
 * evicted to a spill slot is never stored in the prologue, leaving the slot
 * uninitialized.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class SpillSlotAliasingTest {

    private fun buildModule(block: ModuleBuilder.() -> Unit): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        builder.block()
        return builder.build()
    }

    // ── Bug 1: Spill slot aliasing ──────────────────────────────────

    /**
     * 15 simultaneously-live I64 values exceed the ~11 GP registers,
     * forcing aggressive spilling. Without acquireSafe, slots get reused
     * for overlapping values, corrupting the eager stores and producing
     * a wrong sum.
     */
    @Test
    fun manyLiveValuesDoNotAlias() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("base", Type.I64)), Type.I64)
            appendBlock("entry")
            val values = mutableListOf<Value>()
            for (index in 0 until 15) {
                val address = add(params[0], Constant.I64(index.toLong() * 8))
                values.add(load(Type.I64, address))
            }
            var sum = values[0]
            for (index in 1 until values.size) {
                sum = add(sum, values[index])
            }
            ret(sum)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val memory = arena.allocate(15 * 8, 8)
        var expectedSum = 0L
        for (index in 0 until 15) {
            val value = (index + 1) * 100L
            memory.set(java.lang.foreign.ValueLayout.JAVA_LONG, index.toLong() * 8, value)
            expectedSum += value
        }
        assertEquals(expectedSum, engine.call("f", memory.address()))
        arena.close()
    }

    /**
     * 20 simultaneously-live I32 values from different memory loads.
     * Tests that I32 spill slots also don't alias.
     */
    @Test
    fun manyLiveI32ValuesDoNotAlias() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("base", Type.I64)), Type.I32)
            appendBlock("entry")
            val values = mutableListOf<Value>()
            for (index in 0 until 20) {
                val address = add(params[0], Constant.I64(index.toLong() * 4))
                values.add(load(Type.I32, address))
            }
            var sum = values[0]
            for (index in 1 until values.size) {
                sum = add(sum, values[index])
            }
            ret(sum)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val memory = arena.allocate(20 * 4, 8)
        var expectedSum = 0
        for (index in 0 until 20) {
            val value = (index + 1) * 7
            memory.set(java.lang.foreign.ValueLayout.JAVA_INT, index.toLong() * 4, value)
            expectedSum += value
        }
        assertEquals(expectedSum.toLong(), engine.call("f", memory.address()))
        arena.close()
    }

    /**
     * DOOM func_122 pattern: context indirection with 8 struct field
     * loads, intermediate computations, then a store back. Each memory
     * access reloads the base from the context. All values exact.
     */
    @Test
    fun contextIndirectionWithExactVerification() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ctx", Type.I64), Param("p0", Type.I32)), Type.I32)
            appendBlock("entry")

            val base = load(Type.I64, params[0])
            val offset = zext(params[1], Type.I64)

            val fieldOffsets = listOf(4L, 8L, 16L, 20L, 24L, 40L, 44L, 60L)
            val loadedValues = mutableListOf<Value>()
            for (fieldOffset in fieldOffsets) {
                loadedValues.add(load(Type.I32, add(add(base, offset), Constant.I64(fieldOffset))))
            }

            val pairSums = mutableListOf<Value>()
            for (index in 0 until loadedValues.size - 1) {
                pairSums.add(add(loadedValues[index], loadedValues[index + 1]))
            }

            val base2 = load(Type.I64, params[0])
            val storeAddress = add(add(base2, offset), Constant.I64(16))

            var result = pairSums[0]
            for (index in 1 until pairSums.size) {
                result = add(result, pairSums[index])
            }
            store(result, storeAddress)
            ret(result)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val contextMemory = arena.allocate(16, 8)
        val dataMemory = arena.allocate(1024, 8)

        contextMemory.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, dataMemory.address())

        val structBase = 100
        val fieldValues = mapOf(4 to 40, 8 to 80, 16 to 160, 20 to 200, 24 to 240, 40 to 400, 44 to 440, 60 to 600)
        for ((fieldOffset, value) in fieldValues) {
            dataMemory.set(java.lang.foreign.ValueLayout.JAVA_INT, (structBase + fieldOffset).toLong(), value)
        }

        // pairSums: (40+80)=120, (80+160)=240, (160+200)=360, (200+240)=440,
        //           (240+400)=640, (400+440)=840, (440+600)=1040
        // result = 120+240+360+440+640+840+1040 = 3680
        val expectedResult = 3680
        val result = engine.call("f", contextMemory.address(), structBase.toLong())
        assertEquals(expectedResult.toLong(), result)

        val stored = dataMemory.get(java.lang.foreign.ValueLayout.JAVA_INT, (structBase + 16).toLong())
        assertEquals(expectedResult, stored, "Stored value at p0+16 must match")

        arena.close()
    }

    /**
     * Same context indirection pattern in a module with 800 filler functions.
     */
    @Test
    fun contextIndirectionInLargeModule() {
        val module = buildModule {
            for (idx in 0 until 800) {
                val fillerParams = createFunction("filler_$idx", listOf(Param("ctx", Type.I64), Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                ret(add(fillerParams[1], Constant.I32(idx)))
                finalizeFunction()
            }

            val params = createFunction("f", listOf(Param("ctx", Type.I64), Param("p0", Type.I32)), Type.I32)
            appendBlock("entry")
            val base = load(Type.I64, params[0])
            val offset = zext(params[1], Type.I64)
            val loadedValues = mutableListOf<Value>()
            for (fieldOffset in listOf(4L, 8L, 16L, 20L, 24L, 40L, 44L, 60L)) {
                loadedValues.add(load(Type.I32, add(add(base, offset), Constant.I64(fieldOffset))))
            }
            val pairSums = mutableListOf<Value>()
            for (index in 0 until loadedValues.size - 1) {
                pairSums.add(add(loadedValues[index], loadedValues[index + 1]))
            }
            val base2 = load(Type.I64, params[0])
            val storeAddress = add(add(base2, offset), Constant.I64(16))
            var result = pairSums[0]
            for (index in 1 until pairSums.size) {
                result = add(result, pairSums[index])
            }
            store(result, storeAddress)
            ret(result)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val contextMemory = arena.allocate(16, 8)
        val dataMemory = arena.allocate(1024, 8)
        contextMemory.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, dataMemory.address())
        val structBase = 100
        for ((fieldOffset, value) in mapOf(4 to 40, 8 to 80, 16 to 160, 20 to 200, 24 to 240, 40 to 400, 44 to 440, 60 to 600)) {
            dataMemory.set(java.lang.foreign.ValueLayout.JAVA_INT, (structBase + fieldOffset).toLong(), value)
        }
        assertEquals(3680L, engine.call("f", contextMemory.address(), structBase.toLong()))
        arena.close()
    }

    // ── Bug 2: Evicted parameter not stored ─────────────────────────

    /**
     * I64 parameter (context pointer) evicted from RCX due to register
     * pressure. Without the prologue eviction fix, [rbp-spill] is never
     * written, causing a garbage dereference.
     */
    @Test
    fun evictedI64ParameterPreserved() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ctx", Type.I64), Param("offset", Type.I32)), Type.I32)
            appendBlock("entry")

            // Burn all GP registers with intermediate I64 values
            val pressureValues = mutableListOf<Value>()
            for (index in 0 until 12) {
                pressureValues.add(add(params[0], Constant.I64((index + 1).toLong() * 1000)))
            }

            // Use ctx after it was evicted — load memory_base from context struct
            val base = load(Type.I64, params[0])
            val extendedOffset = zext(params[1], Type.I64)
            val address = add(base, extendedOffset)
            val loadedValue = load(Type.I32, address)

            // Force pressure values to stay live
            var pressureSum = pressureValues[0]
            for (index in 1 until pressureValues.size) {
                pressureSum = add(pressureSum, pressureValues[index])
            }

            // Return just the loaded value (truncate pressure sum to avoid it dominating)
            val truncated = trunc(pressureSum, Type.I32)
            val masked = and(truncated, Constant.I32(0))
            ret(add(loadedValue, masked))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val contextMemory = arena.allocate(16, 8)
        val dataMemory = arena.allocate(256, 8)
        contextMemory.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, dataMemory.address())
        dataMemory.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 42)

        // masked pressure sum is 0, so result should be exactly 42
        assertEquals(42L, engine.call("f", contextMemory.address(), 100))
        arena.close()
    }

    /**
     * I32 parameter evicted. The second parameter (EDX) is pushed out
     * by register pressure, then used later.
     */
    @Test
    fun evictedI32ParameterPreserved() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("base", Type.I64), Param("multiplier", Type.I32)), Type.I32)
            appendBlock("entry")

            // Load many I32 values to exhaust GP registers
            val loaded = mutableListOf<Value>()
            for (index in 0 until 12) {
                loaded.add(load(Type.I32, add(params[0], Constant.I64(index.toLong() * 4))))
            }

            // Use multiplier after it was evicted
            var sum = loaded[0]
            for (index in 1 until loaded.size) {
                sum = add(sum, loaded[index])
            }
            val result = mul(sum, params[1])
            ret(result)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val memory = arena.allocate(12 * 4, 8)
        for (index in 0 until 12) {
            memory.set(java.lang.foreign.ValueLayout.JAVA_INT, index.toLong() * 4, 1)
        }

        // sum of 12 ones = 12, multiplied by 5 = 60
        assertEquals(60L, engine.call("f", memory.address(), 5))
        // multiplied by 0 = 0 (catches case where multiplier is garbage)
        assertEquals(0L, engine.call("f", memory.address(), 0))
        arena.close()
    }

    /**
     * Leaf function (no calls, acrossCall=false for all params) with a
     * parameter evicted to spill. This is the exact DOOM func_38 pattern.
     */
    @Test
    fun leafFunctionEvictedParameter() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ctx", Type.I64), Param("p0", Type.I32)), Type.I32)
            appendBlock("entry")

            val offset = zext(params[1], Type.I64)

            // Multiple memory access patterns that create register pressure
            // Each loads base from context, adds offset, loads from memory
            val results = mutableListOf<Value>()
            for (fieldOffset in listOf(0L, 4L, 8L, 12L, 16L, 20L, 24L, 28L, 32L, 36L, 40L, 44L)) {
                val base = load(Type.I64, params[0])
                val address = add(add(base, offset), Constant.I64(fieldOffset))
                results.add(load(Type.I32, address))
            }

            // Check a condition and branch (like DOOM's guard pattern)
            val flagValue = and(results[0], Constant.I32(8))
            val isSet = icmp(ICmpPredicate.NE, flagValue, Constant.I32(0))
            condBr(isSet, "has_flag", "no_flag")

            appendBlock("has_flag")
            ret(results[0])

            appendBlock("no_flag")
            // Use all loaded values
            var sum = results[1]
            for (index in 2 until results.size) {
                sum = add(sum, results[index])
            }
            ret(sum)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val contextMemory = arena.allocate(16, 8)
        val dataMemory = arena.allocate(256, 8)
        contextMemory.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, dataMemory.address())

        val structBase = 48
        // field[0] = 0 (no flag) so we take no_flag path
        dataMemory.set(java.lang.foreign.ValueLayout.JAVA_INT, (structBase + 0).toLong(), 0)
        for (index in 1 until 12) {
            dataMemory.set(java.lang.foreign.ValueLayout.JAVA_INT, (structBase + index * 4).toLong(), index * 10)
        }

        // no_flag path: sum of fields 1..11 = 10+20+30+40+50+60+70+80+90+100+110 = 660
        assertEquals(660L, engine.call("f", contextMemory.address(), structBase.toLong()))

        // has_flag path: field[0] = 8 (bit 3 set)
        dataMemory.set(java.lang.foreign.ValueLayout.JAVA_INT, (structBase + 0).toLong(), 8)
        assertEquals(8L, engine.call("f", contextMemory.address(), structBase.toLong()))

        arena.close()
    }

    // ── Cross-block spill correctness ───────────────────────────────

    /**
     * Values defined in entry block used in two different branch targets.
     * Both paths must see correct spilled values.
     */
    @Test
    fun spillAcrossBranch() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("base", Type.I64), Param("flag", Type.I32)), Type.I32)
            appendBlock("entry")

            val base = load(Type.I64, params[0])
            val values = mutableListOf<Value>()
            for (index in 0 until 12) {
                values.add(load(Type.I32, add(base, Constant.I64(index.toLong() * 4))))
            }

            val isZero = icmp(ICmpPredicate.EQ, params[1], Constant.I32(0))
            condBr(isZero, "zero_path", "nonzero_path")

            appendBlock("zero_path")
            var sumA = values[0]
            for (index in 1 until 6) {
                sumA = add(sumA, values[index])
            }
            ret(sumA)

            appendBlock("nonzero_path")
            var sumB = values[6]
            for (index in 7 until 12) {
                sumB = add(sumB, values[index])
            }
            ret(sumB)

            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val contextMemory = arena.allocate(16, 8)
        val dataMemory = arena.allocate(48, 8)
        contextMemory.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, dataMemory.address())
        for (index in 0 until 12) {
            dataMemory.set(java.lang.foreign.ValueLayout.JAVA_INT, index.toLong() * 4, (index + 1) * 10)
        }

        // flag=0: values[0..5] = 10+20+30+40+50+60 = 210
        assertEquals(210L, engine.call("f", contextMemory.address(), 0))
        // flag=1: values[6..11] = 70+80+90+100+110+120 = 570
        assertEquals(570L, engine.call("f", contextMemory.address(), 1))
        arena.close()
    }

    /**
     * Snake crash pattern: load a value from memory, store it to another address,
     * then use a DIFFERENT value (local/param) for the next memory access.
     * The allocator must not confuse the loaded value with the local.
     *
     * Sequence: load palette → store palette → write draw_colors → load from gamePtr
     * The gamePtr must survive the store and draw_colors write.
     */
    @Test
    fun loadStoreThenReuseLocal() {
        val module = buildModule {
            val params = createFunction("f", listOf(
                Param("ctx", Type.I64),
                Param("gamePtr", Type.I32),
            ), Type.I32)
            appendBlock("entry")

            // Load memory base from context
            val memBase = load(Type.I64, params[0])

            // Load palette value from addr 4: palette = mem[4]
            val paletteAddr = add(memBase, Constant.I64(4))
            val paletteValue = load(Type.I32, paletteAddr)

            // Store palette to a different location: mem[100] = palette
            val storeAddr = add(memBase, Constant.I64(100))
            store(paletteValue, storeAddr)

            // Write draw colors constant: mem[20] = 2 (i16 store)
            val dcAddr = add(memBase, Constant.I64(20))
            val dcValue = trunc(Constant.I32(2), Type.I16)
            store(dcValue, dcAddr)

            // Now use gamePtr (the parameter) for a load — this is where the bug hits
            // gamePtr must NOT have been overwritten by paletteValue's register
            val memBase2 = load(Type.I64, params[0])
            val gameAddr = add(memBase2, zext(params[1], Type.I64))
            val gameValue = load(Type.I32, gameAddr)

            ret(gameValue)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val memory = arena.allocate(1024, 8)
        val contextMem = arena.allocate(8, 8)
        contextMem.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, memory.address())

        // Set palette at addr 4
        memory.set(java.lang.foreign.ValueLayout.JAVA_INT, 4, 0x00e0f8cf.toInt())
        // Set game value at addr 200
        memory.set(java.lang.foreign.ValueLayout.JAVA_INT, 200, 42)

        // gamePtr=200, should return mem[200]=42, NOT palette value
        val result = engine.call("f", contextMem.address(), 200L)
        assertEquals(42L, result, "Should load from gamePtr=200, not from palette address")
        arena.close()
    }

    /**
     * Extended snake pattern with more register pressure: 8 memory loads
     * interleaved with stores, then use params that must survive.
     */
    @Test
    fun manyLoadsStoresThenReuseParams() {
        val module = buildModule {
            val params = createFunction("f", listOf(
                Param("ctx", Type.I64),
                Param("p1", Type.I32),
                Param("p2", Type.I32),
                Param("p3", Type.I32),
            ), Type.I32)
            appendBlock("entry")

            val memBase = load(Type.I64, params[0])

            // Load 8 values from sequential addresses
            val loaded = mutableListOf<Value>()
            for (index in 0 until 8) {
                val addr = add(memBase, Constant.I64(index.toLong() * 4))
                loaded.add(load(Type.I32, addr))
            }

            // Store first 4 values to different locations
            for (index in 0 until 4) {
                val addr = add(memBase, Constant.I64((100 + index * 4).toLong()))
                store(loaded[index], addr)
            }

            // Write a constant (like draw_colors)
            val dcAddr = add(memBase, Constant.I64(20))
            store(Constant.I32(0x1234), dcAddr)

            // Now use p1, p2, p3 — all must have survived the stores
            val memBase2 = load(Type.I64, params[0])
            val addr1 = add(memBase2, zext(params[1], Type.I64))
            val val1 = load(Type.I32, addr1)
            val addr2 = add(memBase2, zext(params[2], Type.I64))
            val val2 = load(Type.I32, addr2)
            val addr3 = add(memBase2, zext(params[3], Type.I64))
            val val3 = load(Type.I32, addr3)

            // Also use loaded[4..7] which should have survived
            var result = add(val1, val2)
            result = add(result, val3)
            for (index in 4 until 8) {
                result = add(result, loaded[index])
            }

            ret(result)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val memory = arena.allocate(1024, 8)
        val contextMem = arena.allocate(8, 8)
        contextMem.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, memory.address())

        // Set sequential values at 0-31
        for (index in 0 until 8) {
            memory.set(java.lang.foreign.ValueLayout.JAVA_INT, index.toLong() * 4, (index + 1) * 10)
        }
        // Set values at p1=200, p2=204, p3=208
        memory.set(java.lang.foreign.ValueLayout.JAVA_INT, 200, 1000)
        memory.set(java.lang.foreign.ValueLayout.JAVA_INT, 204, 2000)
        memory.set(java.lang.foreign.ValueLayout.JAVA_INT, 208, 3000)

        // result = val1(1000) + val2(2000) + val3(3000) + loaded[4](50) + loaded[5](60) + loaded[6](70) + loaded[7](80)
        // = 6000 + 260 = 6260
        val result = engine.call("f", contextMem.address(), 200L, 204L, 208L)
        assertEquals(6260L, result)
        arena.close()
    }

    /**
     * Context indirection with function call in between — the function call
     * clobbers caller-saved registers. Values loaded BEFORE the call must
     * survive in callee-saved registers or be reloaded from spills.
     */
    @Test
    fun contextIndirectionAcrossCall() {
        val module = buildModule {
            declareFunction("helper", listOf(Param("ctx", Type.I64), Param("x", Type.I32)), Type.I32)

            val helperParams = createFunction("helper", listOf(Param("ctx", Type.I64), Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(helperParams[1], Constant.I32(1)))
            finalizeFunction()

            val params = createFunction("f", listOf(Param("ctx", Type.I64), Param("p0", Type.I32)), Type.I32)
            appendBlock("entry")

            // Load several values from memory BEFORE the call
            val memBase = load(Type.I64, params[0])
            val addr1 = add(memBase, zext(params[1], Type.I64))
            val val1 = load(Type.I32, addr1)
            val addr2 = add(memBase, add(zext(params[1], Type.I64), Constant.I64(4)))
            val val2 = load(Type.I32, addr2)
            val addr3 = add(memBase, add(zext(params[1], Type.I64), Constant.I64(8)))
            val val3 = load(Type.I32, addr3)

            // Call helper(ctx, val1) — clobbers caller-saved registers
            val callResult = call("helper", listOf(params[0], val1), Type.I32)

            // Use val2 and val3 AFTER the call — they must have survived
            var result = add(callResult!!, val2)
            result = add(result, val3)

            ret(result)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val memory = arena.allocate(1024, 8)
        val contextMem = arena.allocate(8, 8)
        contextMem.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, memory.address())

        // At offset 100: values 10, 20, 30
        memory.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 10)
        memory.set(java.lang.foreign.ValueLayout.JAVA_INT, 104, 20)
        memory.set(java.lang.foreign.ValueLayout.JAVA_INT, 108, 30)

        // helper(ctx, 10) = 11, result = 11 + 20 + 30 = 61
        val result = engine.call("f", contextMem.address(), 100L)
        assertEquals(61L, result)
        arena.close()
    }
}
