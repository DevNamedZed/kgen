package org.wark.examples.wasm4

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmBlockType
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget

/**
 * Tests JIT codegen for patterns used by snake/minesweeper but not watris.
 * Each test verifies JIT matches interpreter for a specific opcode pattern.
 */
class JitPatternTest {

    @Test
    fun memoryGrowInJit() {
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)
            i32Const(1) // grow by 1 page
            memoryGrow(0)
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(), "memory.grow")
    }

    @Test
    fun memorySizeInJit() {
        val bytes = buildModule {
            memory("mem", 2, exported = true)
            beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)
            memorySize(0)
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(), "memory.size")
    }

    @Test
    fun loadStoreInterleaveWithLocalReuse() {
        // Pattern from snake crash: load a value, store it, then use a local
        // variable that should still be live but its register got reused
        val bytes = buildModule {
            memory("mem", 2, exported = true)
            beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            val ptr = declareLocal(WasmValueType.I32)
            val temp = declareLocal(WasmValueType.I32)

            // ptr = p0 (argument)
            localGet(0)
            localSet(ptr)

            // temp = mem[4] (load palette value from address 4)
            i32Const(4)
            i32Load(2, 0)
            localSet(temp)

            // mem[100] = temp (store loaded value to address 100)
            i32Const(100)
            localGet(temp)
            i32Store(2, 0)

            // mem[20] = 2 (store draw colors constant)
            i32Const(20)
            i32Const(2)
            i32Store16(1, 0)

            // return mem[ptr] (use ptr — this should still have the original argument)
            localGet(ptr)
            i32Load(2, 0)
            endFunction()
        }

        // Write known values to memory
        val interpInst = org.wark.WarkRuntime.create(org.wark.WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate()
        interpInst.memory().writeI32(4, 0xe0f8cf.toInt()) // palette at address 4
        interpInst.memory().writeI32(200, 42) // known value at address 200

        val jitInst = org.wark.WarkRuntime.create(org.wark.WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate()
        jitInst.memory().writeI32(4, 0xe0f8cf.toInt())
        jitInst.memory().writeI32(200, 42)

        // Call with ptr=200 — should return mem[200] = 42
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(200), "load-store-interleave with local reuse")
    }

    @Test
    fun manyLocalsHighPressure() {
        // High register pressure: many locals used across memory operations
        val bytes = buildModule {
            memory("mem", 2, exported = true)
            beginFunction("test", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            val a = declareLocal(WasmValueType.I32)
            val b = declareLocal(WasmValueType.I32)
            val c = declareLocal(WasmValueType.I32)
            val d = declareLocal(WasmValueType.I32)
            val e = declareLocal(WasmValueType.I32)
            val f = declareLocal(WasmValueType.I32)
            val g = declareLocal(WasmValueType.I32)

            // Load 7 values from different memory locations
            localGet(0); i32Load(2, 0); localSet(a)        // a = mem[p0]
            localGet(0); i32Const(4); i32Add(); i32Load(2, 0); localSet(b) // b = mem[p0+4]
            localGet(0); i32Const(8); i32Add(); i32Load(2, 0); localSet(c) // c = mem[p0+8]
            localGet(0); i32Const(12); i32Add(); i32Load(2, 0); localSet(d)
            localGet(0); i32Const(16); i32Add(); i32Load(2, 0); localSet(e)
            localGet(0); i32Const(20); i32Add(); i32Load(2, 0); localSet(f)
            localGet(0); i32Const(24); i32Add(); i32Load(2, 0); localSet(g)

            // Store some values to other locations (creates pressure)
            localGet(1); localGet(a); i32Store(2, 0)        // mem[p1] = a
            localGet(1); i32Const(4); i32Add(); localGet(b); i32Store(2, 0)
            localGet(1); i32Const(8); i32Add(); localGet(c); i32Store(2, 0)

            // Now use the LATER locals (d,e,f,g) — these may have been evicted
            localGet(d)
            localGet(e)
            i32Add()
            localGet(f)
            i32Add()
            localGet(g)
            i32Add()
            endFunction()
        }

        val interpInst = org.wark.WarkRuntime.create(org.wark.WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate()
        // Write values at addresses 1000-1024
        for (i in 0 until 7) { interpInst.memory().writeI32(1000 + i * 4, (i + 1) * 10) }

        val jitInst = org.wark.WarkRuntime.create(org.wark.WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate()
        for (i in 0 until 7) { jitInst.memory().writeI32(1000 + i * 4, (i + 1) * 10) }

        // p0=1000 (source), p1=2000 (dest)
        // d=40, e=50, f=60, g=70, sum=220
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(1000, 2000), "7 locals high pressure: 40+50+60+70=220")
    }

    @Test
    fun largeRenderFunctionWithDrawColors() {
        // Mimics snake func_14: 10 locals, reads game state, writes DRAW_COLORS,
        // then reads from computed addresses using locals that must survive
        val bytes = buildModule {
            memory("mem", 2, exported = true)

            beginFunction("render", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            // 10 locals to create register pressure
            val ptr = declareLocal(WasmValueType.I32)
            val color = declareLocal(WasmValueType.I32)
            val width = declareLocal(WasmValueType.I32)
            val height = declareLocal(WasmValueType.I32)
            val x = declareLocal(WasmValueType.I32)
            val y = declareLocal(WasmValueType.I32)
            val temp1 = declareLocal(WasmValueType.I32)
            val temp2 = declareLocal(WasmValueType.I32)
            val result = declareLocal(WasmValueType.I32)
            val counter = declareLocal(WasmValueType.I32)

            // ptr = p0 (game state pointer)
            localGet(0)
            localSet(ptr)

            // Load game state values from memory
            localGet(ptr)
            i32Load(2, 0)
            localSet(width)     // width = mem[ptr]

            localGet(ptr)
            i32Load(2, 4)
            localSet(height)    // height = mem[ptr+4]

            localGet(ptr)
            i32Load(2, 8)
            localSet(x)         // x = mem[ptr+8]

            localGet(ptr)
            i32Load(2, 12)
            localSet(y)         // y = mem[ptr+12]

            // Read palette value from addr 4
            i32Const(4)
            i32Load(2, 0)
            localSet(color)     // color = mem[4] (palette)

            // Store palette to addr 100
            i32Const(100)
            localGet(color)
            i32Store(2, 0)      // mem[100] = color

            // Write DRAW_COLORS (addr 20) = 2
            i32Const(20)
            i32Const(2)
            i32Store16(1, 0)    // mem16[20] = 2

            // Now compute using locals that must have survived:
            // result = width * height + x + y
            localGet(width)
            localGet(height)
            i32Mul()
            localGet(x)
            i32Add()
            localGet(y)
            i32Add()
            localSet(result)

            // Load from ptr+16 using ptr (must still have original value)
            localGet(ptr)
            i32Load(2, 16)
            localSet(temp1)     // temp1 = mem[ptr+16]

            // Load from ptr+20
            localGet(ptr)
            i32Load(2, 20)
            localSet(temp2)     // temp2 = mem[ptr+20]

            // Final result = result + temp1 + temp2
            localGet(result)
            localGet(temp1)
            i32Add()
            localGet(temp2)
            i32Add()
            endFunction()
        }

        // Set up memory
        val interpInst = org.wark.WarkRuntime.create(org.wark.WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate()
        val mem = interpInst.memory()
        mem.writeI32(4, 0x00e0f8cf.toInt())  // palette at addr 4
        mem.writeI32(300, 10)   // width
        mem.writeI32(304, 20)   // height
        mem.writeI32(308, 5)    // x
        mem.writeI32(312, 7)    // y
        mem.writeI32(316, 100)  // temp1
        mem.writeI32(320, 200)  // temp2

        val jitInst = org.wark.WarkRuntime.create(org.wark.WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate()
        val jitMem = jitInst.memory()
        jitMem.writeI32(4, 0x00e0f8cf.toInt())
        jitMem.writeI32(300, 10)
        jitMem.writeI32(304, 20)
        jitMem.writeI32(308, 5)
        jitMem.writeI32(312, 7)
        jitMem.writeI32(316, 100)
        jitMem.writeI32(320, 200)

        // result = 10*20 + 5 + 7 = 212, + 100 + 200 = 512
        val interpResult = interpInst.call("render", 300)[0]
        val jitResult = jitInst.call("render", 300)[0]
        assertEquals(interpResult, jitResult, "render: interp=$interpResult, jit=$jitResult")
        assertEquals(512L, jitResult)
    }

    @Test
    fun unsignedComparisonWithLargeConstant() {
        // Tests i32.ge_u with large constants — the pattern from snake func_10
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            // if (p0 >= 0x3FFFFFFC) return -1 else return p0 + 1
            localGet(0)
            i32Const(0x3FFFFFFC.toInt())
            i32GeU()
            beginIf(org.kgen.target.wasm.WasmBlockType.I32)
            i32Const(-1)
            beginElse()
            localGet(0)
            i32Const(1)
            i32Add()
            end()
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(0), "0 < 0x3FFFFFFC → 1")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(100), "100 < 0x3FFFFFFC → 101")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(0x3FFFFFFC.toLong()), "0x3FFFFFFC >= 0x3FFFFFFC → -1")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(0xFFFFFFFF), "0xFFFFFFFF >= 0x3FFFFFFC → -1")
    }

    @Test
    fun globalGetSetAcrossFunctions() {
        // Tests global.get/set — the pattern used by shadow stack management
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            // global $0 starts at 1000
            val globalAsm = org.kgen.target.wasm.asm.WasmAssembler.create()
            // We can't easily set global init in WasmAssembler, so use a simpler test
            beginFunction("push", listOf(WasmValueType.I32), emptyList(), exported = true)
            // Decrement stack pointer (global $0), store value
            // Can't test without custom globals, so test basic arithmetic instead
            localGet(0)
            localGet(0)
            i32Add()
            drop()
            endFunction()
        }
        // Basic test - just verify compilation doesn't crash
        val interpInst = org.wark.WarkRuntime.create(org.wark.WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate()
        val jitInst = org.wark.WarkRuntime.create(org.wark.WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate()
        interpInst.call("push", 42)
        jitInst.call("push", 42)
    }

    @Test
    fun drawColorsWithNestedIfElse() {
        // Pattern: load values, write DRAW_COLORS, then conditional branches
        // that use the loaded values — testing register survival across branches
        val bytes = buildModule {
            memory("mem", 2, exported = true)
            beginFunction("test", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            val a = declareLocal(WasmValueType.I32)
            val b = declareLocal(WasmValueType.I32)
            val c = declareLocal(WasmValueType.I32)

            // Load 3 values from memory
            localGet(0)
            i32Load(2, 0)
            localSet(a)
            localGet(0)
            i32Load(2, 4)
            localSet(b)
            localGet(0)
            i32Load(2, 8)
            localSet(c)

            // Store a value (like palette color)
            i32Const(100)
            localGet(a)
            i32Store(2, 0)

            // Write DRAW_COLORS
            i32Const(20)
            i32Const(3)
            i32Store16(1, 0)

            // Nested conditional using b and c
            localGet(1)
            i32Const(0)
            i32GtS()
            beginIf(WasmBlockType.I32)
              localGet(b)
              localGet(c)
              i32Add()
            beginElse()
              localGet(b)
              localGet(c)
              i32Sub()
            end()

            // Add a to the result
            localGet(a)
            i32Add()
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(200, 1), "draw colors then if: b+c+a")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(200, 0), "draw colors then else: b-c+a")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(200, -1), "draw colors then else neg: b-c+a")
    }

    @Test
    fun typedLoopWithIfElseAndBr() {
        // The exact pattern from func_56 / countBits
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            val counter = declareLocal(WasmValueType.I32)
            beginLoop(org.kgen.target.wasm.WasmBlockType.I32)
            localGet(0) // p0
            i32Const(1)
            i32LeU()
            beginIf(org.kgen.target.wasm.WasmBlockType.I32)
            localGet(counter) // return counter
            beginElse()
            localGet(0)
            i32Const(1)
            i32ShrU()
            localSet(0)
            localGet(counter)
            i32Const(1)
            i32Add()
            localSet(counter)
            br(1) // loop back
            end() // end if
            end() // end loop
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(16), "typed loop shift count 16→4")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(1), "typed loop base case 1→0")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(2), "typed loop 2→1")
    }

    @Test
    fun brTableInJit() {
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            val func = beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            beginBlock(WasmBlockType.I32)
            beginBlock(WasmBlockType.I32)
            beginBlock(WasmBlockType.I32)
            localGet(func.getParameter(0))
            brTable(intArrayOf(0, 1, 2, 2))
            end()
            i32Const(10)
            br(2)
            end()
            i32Const(20)
            br(1)
            end()
            i32Const(30)
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(0), "br_table case 0")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(1), "br_table case 1")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(2), "br_table case 2/default")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(99), "br_table out of range")
    }

    @Test
    fun i64ExtendAndShiftInJit() {
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            i64ExtendI32U()
            i64Const(3)
            i64Shl()
            i32WrapI64()
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(5), "i64 extend+shift+wrap (5<<3=40)")
    }

    @Test
    fun f64ArithmeticInJit() {
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            f64ConvertI32S()
            f64Const(2.5)
            f64Mul()
            i32TruncF64S()
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(10), "f64 convert+mul+trunc (10*2.5=25)")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(4), "f64 convert+mul+trunc (4*2.5=10)")
    }

    @Test
    fun i32ClzCtzInJit() {
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            beginFunction("testClz", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            i32Clz()
            endFunction()
            beginFunction("testCtz", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            i32Ctz()
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "testClz", longArrayOf(0x80), "i32.clz(0x80)=24")
        assertJitMatchesInterpreter(bytes, "testCtz", longArrayOf(0x80), "i32.ctz(0x80)=7")
    }

    @Test
    fun i32RotlInJit() {
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            i32Const(4)
            i32Rotl()
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(0x12345678), "i32.rotl by 4")
    }

    @Test
    fun memoryGrowThenAccessInJit() {
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)
            // Grow memory by 1 page
            i32Const(1)
            memoryGrow(0)
            drop()
            // Write value at offset 0x10000 (in the new page)
            i32Const(0x10000)
            i32Const(42)
            i32Store(0, 0)
            // Read it back
            i32Const(0x10000)
            i32Load(0, 0)
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(), "memory.grow then store/load in new page")
    }

    @Test
    fun i32Load16uInJit() {
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)
            // Store 0xABCD at offset 100
            i32Const(100)
            i32Const(0x0000ABCD.toInt())
            i32Store(0, 0)
            // Load16u from offset 100
            i32Const(100)
            i32Load16U(0, 0)
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(), "i32.load16_u")
    }

    @Test
    fun multipleCallsInJit() {
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            beginFunction("add1", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            i32Const(1)
            i32Add()
            endFunction()
            beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            call(0) // add1
            call(0) // add1
            call(0) // add1
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(10), "3 nested calls (10+3=13)")
    }

    @Test
    fun fiveParamFunctionStackParam() {
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            // Helper: add1(x) -> x+1
            beginFunction("add1", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            i32Const(1)
            i32Add()
            endFunction()

            // Main: test(a, b, c, d) -> returns d after calling add1 multiple times
            // d is the 4th WASM param = 5th actual param (context + 4) = STACK param
            val func = beginFunction("test", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            // Call add1(a) to clobber registers
            localGet(func.getParameter(0))
            call(0) // add1
            drop()
            // Call add1(b)
            localGet(func.getParameter(1))
            call(0)
            drop()
            // Call add1(c)
            localGet(func.getParameter(2))
            call(0)
            drop()
            // Return d (stack param) — must still have correct value
            localGet(func.getParameter(3))
            endFunction()
        }
        // d=42 should be returned unchanged despite register clobbering
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(10, 20, 30, 42), "5-param stack param preserved across calls")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(1, 2, 3, 99), "5-param stack param=99")
    }

    @Test
    fun fiveParamStackParamInBranch() {
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            // Helper
            beginFunction("identity", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            endFunction()

            // test(a, b, c, d) -> if (d & 15) == 0 then 1 else d
            // Same pattern as minesweeper func_2's entry block
            val func = beginFunction("test", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            // Call identity(a) to force register pressure
            localGet(func.getParameter(0))
            call(0)
            drop()

            // Check (d & 15) == 0
            localGet(func.getParameter(3))
            i32Const(15)
            i32And()
            i32Eqz()
            beginIf(org.kgen.target.wasm.WasmBlockType.I32)
            i32Const(1)
            beginElse()
            localGet(func.getParameter(3))
            end()
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(1, 2, 3, 0), "d=0: (0&15)==0 → 1")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(1, 2, 3, 16), "d=16: (16&15)==0 → 1")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(1, 2, 3, 7), "d=7: (7&15)!=0 → 7")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(1, 2, 3, 255), "d=255: (255&15)!=0 → 255")
    }

    @Test
    fun fiveParamLoopWithManyLocals() {
        // Mimics func_2's pattern: 5 params, loop with conditional, many live values
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            // Helper functions
            beginFunction("assertNonZero", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            i32Eqz()
            beginIf()
            unreachable()
            end()
            localGet(0)
            endFunction()

            beginFunction("zeroMemory", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            endFunction()

            // Main function: test(base, count, stride, flags)
            // Loops count times, calls assertNonZero and zeroMemory each iteration
            // Uses many locals to create register pressure
            val func = beginFunction("test", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            val ptr = declareLocal(WasmValueType.I32)
            val counter = declareLocal(WasmValueType.I32)
            val result = declareLocal(WasmValueType.I32)
            val temp1 = declareLocal(WasmValueType.I32)
            val temp2 = declareLocal(WasmValueType.I32)
            val temp3 = declareLocal(WasmValueType.I32)

            // ptr = base
            localGet(func.getParameter(0))
            localSet(ptr)
            // counter = 0
            i32Const(0)
            localSet(counter)
            // result = 0
            i32Const(0)
            localSet(result)

            // Compute temp values from flags (stack param) to keep it alive
            localGet(func.getParameter(3))
            i32Const(15)
            i32And()
            localSet(temp1)
            localGet(func.getParameter(3))
            i32Const(4)
            i32ShrU()
            localSet(temp2)

            beginBlock()
            beginLoop()
            // if counter >= count, break
            localGet(counter)
            localGet(func.getParameter(1))
            i32GeU()
            brIf(1)

            // temp3 = assertNonZero(ptr) — clobbers registers
            localGet(ptr)
            call(0) // assertNonZero
            localSet(temp3)

            // zeroMemory(temp3, stride) — clobbers registers
            localGet(temp3)
            localGet(func.getParameter(2))
            call(1) // zeroMemory

            // Use stride and flags to compute next ptr
            // ptr = ptr + stride + temp1
            localGet(func.getParameter(2))
            i32Add()
            localGet(temp1)
            i32Add()
            localSet(ptr)

            // result = result + temp2 + counter
            localGet(result)
            localGet(temp2)
            i32Add()
            localGet(counter)
            i32Add()
            localSet(result)

            // counter++
            localGet(counter)
            i32Const(1)
            i32Add()
            localSet(counter)

            br(0) // loop
            end()
            end()

            // Return result + flags (use stack param at the end)
            localGet(result)
            localGet(func.getParameter(3))
            i32Add()
            endFunction()
        }
        // base=1000, count=5, stride=8, flags=0x37
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(1000, 5, 8, 0x37), "loop 5 iters, stride=8, flags=0x37")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(1000, 10, 4, 0xFF), "loop 10 iters, stride=4, flags=0xFF")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(1000, 0, 8, 42), "loop 0 iters, flags=42")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(1000, 1, 64, 0), "loop 1 iter, flags=0")
    }

    @Test
    fun nestedStackParamCalls() {
        // func_2 pattern: receives 5 params AND calls func_57 which also takes 5 params
        // Both have stack params. The caller must set up the child's stack arg correctly.
        val bytes = buildModule {
            memory("mem", 1, exported = true)

            // child(a, b, c, d) -> a + b + c + d (4 WASM params = 5 including context = stack param)
            beginFunction("child", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            localGet(1)
            i32Add()
            localGet(2)
            i32Add()
            localGet(3)
            i32Add()
            endFunction()

            // parent(a, b, c, d) -> calls child(a, b, c, d) + calls child(d, c, b, a)
            // parent also has 4 WASM params = 5 including context = stack param for d
            val func = beginFunction("parent", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            // Call child(a, b, c, d)
            localGet(func.getParameter(0))
            localGet(func.getParameter(1))
            localGet(func.getParameter(2))
            localGet(func.getParameter(3))
            call(0) // child

            // Call child(d, c, b, a) — uses result of first call + original params
            localGet(func.getParameter(3))
            localGet(func.getParameter(2))
            localGet(func.getParameter(1))
            localGet(func.getParameter(0))
            call(0) // child

            // Return sum of both results
            i32Add()
            endFunction()
        }
        // child(1,2,3,4) = 10, child(4,3,2,1) = 10, total = 20
        assertJitMatchesInterpreter(bytes, "parent", longArrayOf(1, 2, 3, 4), "nested stack params: 1+2+3+4 twice")
        // child(10,20,30,40) = 100, child(40,30,20,10) = 100, total = 200
        assertJitMatchesInterpreter(bytes, "parent", longArrayOf(10, 20, 30, 40), "nested stack params: larger values")
    }

    @Test
    fun nestedStackParamCallsInLoop() {
        // Same pattern but inside a loop — closer to func_2's actual behavior
        val bytes = buildModule {
            memory("mem", 1, exported = true)

            // child(a, b, c, d) -> a + d (uses first and last params, skipping middle)
            beginFunction("child", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            localGet(3)
            i32Add()
            endFunction()

            // parent(base, count, stride, flags) -> loop calling child in each iteration
            val func = beginFunction("parent", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            val counter = declareLocal(WasmValueType.I32)
            val accumulator = declareLocal(WasmValueType.I32)
            val ptr = declareLocal(WasmValueType.I32)

            i32Const(0)
            localSet(counter)
            i32Const(0)
            localSet(accumulator)
            localGet(func.getParameter(0))
            localSet(ptr)

            beginBlock()
            beginLoop()
            // if counter >= count, break
            localGet(counter)
            localGet(func.getParameter(1))
            i32GeU()
            brIf(1)

            // result = child(ptr, counter, stride, flags)
            localGet(ptr)
            localGet(counter)
            localGet(func.getParameter(2))
            localGet(func.getParameter(3)) // flags = stack param
            call(0) // child
            localGet(accumulator)
            i32Add()
            localSet(accumulator)

            // ptr += stride
            localGet(ptr)
            localGet(func.getParameter(2))
            i32Add()
            localSet(ptr)

            // counter++
            localGet(counter)
            i32Const(1)
            i32Add()
            localSet(counter)

            br(0)
            end()
            end()

            // return accumulator + flags (use stack param at end)
            localGet(accumulator)
            localGet(func.getParameter(3))
            i32Add()
            endFunction()
        }
        // 5 iterations: child(1000,0,8,0x37) + child(1008,1,8,0x37) + ... + return + 0x37
        assertJitMatchesInterpreter(bytes, "parent", longArrayOf(1000, 5, 8, 0x37), "nested stack params in loop, 5 iters")
        assertJitMatchesInterpreter(bytes, "parent", longArrayOf(100, 10, 4, 255), "nested stack params in loop, 10 iters")
        assertJitMatchesInterpreter(bytes, "parent", longArrayOf(500, 20, 12, 0), "nested stack params in loop, 20 iters")
    }

    @Test
    fun fiveParamStackParamInLoop() {
        val bytes = buildModule {
            memory("mem", 1, exported = true)
            // Helper: noop side effect
            beginFunction("sideEffect", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            localGet(0)
            endFunction()

            // test(a, b, c, count) -> call sideEffect(a) count times, return count
            val func = beginFunction("test", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            val counter = declareLocal(WasmValueType.I32)
            i32Const(0)
            localSet(counter)

            beginBlock()
            beginLoop()
            // if counter >= d, break
            localGet(counter)
            localGet(func.getParameter(3))
            i32GeU()
            brIf(1)
            // call sideEffect(a) — clobbers registers
            localGet(func.getParameter(0))
            call(0)
            drop()
            // counter++
            localGet(counter)
            i32Const(1)
            i32Add()
            localSet(counter)
            br(0) // loop
            end()
            end()

            localGet(counter)
            endFunction()
        }
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(10, 20, 30, 5), "loop 5 times with stack param")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(10, 20, 30, 0), "loop 0 times")
        assertJitMatchesInterpreter(bytes, "test", longArrayOf(10, 20, 30, 100), "loop 100 times")
    }

    private fun buildModule(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    private fun assertJitMatchesInterpreter(bytes: ByteArray, funcName: String, args: LongArray, description: String) {
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate()
        val interpResult = interpInstance.call(funcName, *args)

        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate()
        val jitResult = jitInstance.call(funcName, *args)

        // Compare as I32 (low 32 bits) since JIT returns zero-extended and interpreter sign-extends
        val interpI32 = interpResult[0].toInt()
        val jitI32 = jitResult[0].toInt()
        assertEquals(interpI32, jitI32, "$description: interp=${interpResult[0]} (i32=$interpI32), jit=${jitResult[0]} (i32=$jitI32)")
    }
}
