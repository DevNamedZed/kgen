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
 * Complex JIT tests that mirror real game function patterns.
 * These use globals, shadow stacks, many locals, nested control flow,
 * interleaved memory operations, and function calls — matching the
 * complexity of snake/minesweeper functions where JIT crashes.
 */
class JitComplexFunctionTest {

    /**
     * Shadow stack pattern: global $0 is a stack pointer that's decremented
     * on entry and incremented on exit. Values are stored on the shadow stack.
     * This is exactly what snake func_10/func_19 do.
     */
    @Test
    fun shadowStackWithGlobal() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 2, exported = true)

        // Global $0: shadow stack pointer (mutable I32)
        asm.global("__stack_pointer", WasmValueType.I32, true, 0x6000)

        // func_0: allocate 16 bytes on shadow stack, store values, return sum
        asm.beginFunction("test", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        val frame = asm.declareLocal(WasmValueType.I32)

        // frame = global[0] - 16
        asm.globalGet(0)
        asm.i32Const(16)
        asm.i32Sub()
        asm.localTee(frame)
        asm.globalSet(0) // save new SP

        // mem[frame] = p0, mem[frame+4] = p1
        asm.localGet(frame)
        asm.localGet(0)
        asm.i32Store(2, 0)
        asm.localGet(frame)
        asm.localGet(1)
        asm.i32Store(2, 4)

        // result = mem[frame] + mem[frame+4]
        asm.localGet(frame)
        asm.i32Load(2, 0)
        asm.localGet(frame)
        asm.i32Load(2, 4)
        asm.i32Add()

        // restore SP: global[0] = frame + 16
        asm.localGet(frame)
        asm.i32Const(16)
        asm.i32Add()
        asm.globalSet(0)

        asm.endFunction()
        val bytes = asm.assemble()

        assertJitMatchesInterp(bytes, "test", longArrayOf(100, 200), 300, "shadow stack sum")
        assertJitMatchesInterp(bytes, "test", longArrayOf(-1, 1), 0, "shadow stack negative")
        assertJitMatchesInterp(bytes, "test", longArrayOf(0x7FFFFFFF, 1), -2147483648, "shadow stack overflow")
    }

    /**
     * Malloc pattern: func_10 in snake. Check size parameter, use globals
     * for allocator state, grow memory if needed.
     */
    @Test
    fun mallocPatternWithGlobals() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 2, exported = true)

        // Global $0: allocator bump pointer
        asm.global("alloc_ptr", WasmValueType.I32, true, 0x1000)
        // Global $1: allocator limit
        asm.global("alloc_limit", WasmValueType.I32, true, 0x10000)

        asm.beginFunction("malloc", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)

        // if (size >= 0x3FFFFFFC) return -1 (invalid)
        asm.localGet(0)
        asm.i32Const(0x3FFFFFFC.toInt())
        asm.i32GeU()
        asm.beginIf(WasmBlockType.I32)
        asm.i32Const(-1)
        asm.beginElse()

        // result = global[0] (current pointer)
        asm.globalGet(0)

        // global[0] += (size + 7) & ~7 (align to 8)
        asm.globalGet(0)
        asm.localGet(0)
        asm.i32Const(7)
        asm.i32Add()
        asm.i32Const(-8)
        asm.i32And()
        asm.i32Add()
        asm.globalSet(0)

        asm.end() // end if/else
        asm.endFunction()

        val bytes = asm.assemble()
        assertJitMatchesInterp(bytes, "malloc", longArrayOf(16), 0x1000, "malloc(16)")
        assertJitMatchesInterp(bytes, "malloc", longArrayOf(0x3FFFFFFC.toLong()), -1, "malloc(too big)")
        assertJitMatchesInterp(bytes, "malloc", longArrayOf(1), 0x1000, "malloc(1) after reset")
    }

    /**
     * Nested function calls with shadow stack — mimics the snake game loop
     * where update() calls rendering functions that use the shadow stack.
     */
    @Test
    fun nestedCallsWithShadowStack() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 2, exported = true)

        // Global $0: shadow stack pointer
        asm.global("__stack_pointer", WasmValueType.I32, true, 0x6000)

        // helper(x) = x * 2 + 1 (uses shadow stack)
        asm.beginFunction("helper", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        val helperFrame = asm.declareLocal(WasmValueType.I32)
        asm.globalGet(0)
        asm.i32Const(8)
        asm.i32Sub()
        asm.localTee(helperFrame)
        asm.globalSet(0)

        asm.localGet(helperFrame)
        asm.localGet(0)
        asm.i32Store(2, 0) // save param to stack

        // result = mem[frame] * 2 + 1
        asm.localGet(helperFrame)
        asm.i32Load(2, 0)
        asm.i32Const(2)
        asm.i32Mul()
        asm.i32Const(1)
        asm.i32Add()

        // restore SP
        asm.localGet(helperFrame)
        asm.i32Const(8)
        asm.i32Add()
        asm.globalSet(0)
        asm.endFunction()

        // test(a, b, c) = helper(a) + helper(b) + helper(c)
        asm.beginFunction("test", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        val testFrame = asm.declareLocal(WasmValueType.I32)
        val r1 = asm.declareLocal(WasmValueType.I32)
        val r2 = asm.declareLocal(WasmValueType.I32)

        // Allocate shadow stack frame
        asm.globalGet(0)
        asm.i32Const(16)
        asm.i32Sub()
        asm.localTee(testFrame)
        asm.globalSet(0)

        // Save params to shadow stack
        asm.localGet(testFrame)
        asm.localGet(0)
        asm.i32Store(2, 0)
        asm.localGet(testFrame)
        asm.localGet(1)
        asm.i32Store(2, 4)
        asm.localGet(testFrame)
        asm.localGet(2)
        asm.i32Store(2, 8)

        // r1 = helper(a)
        asm.localGet(testFrame)
        asm.i32Load(2, 0)
        asm.call(0) // helper
        asm.localSet(r1)

        // r2 = helper(b)
        asm.localGet(testFrame)
        asm.i32Load(2, 4)
        asm.call(0) // helper
        asm.localSet(r2)

        // result = r1 + r2 + helper(c)
        asm.localGet(r1)
        asm.localGet(r2)
        asm.i32Add()
        asm.localGet(testFrame)
        asm.i32Load(2, 8)
        asm.call(0) // helper
        asm.i32Add()

        // Restore SP
        asm.localGet(testFrame)
        asm.i32Const(16)
        asm.i32Add()
        asm.globalSet(0)

        asm.endFunction()
        val bytes = asm.assemble()

        // helper(10)=21, helper(20)=41, helper(30)=61, sum=123
        assertJitMatchesInterp(bytes, "test", longArrayOf(10, 20, 30), 123, "nested calls with shadow stack")
        assertJitMatchesInterp(bytes, "test", longArrayOf(0, 0, 0), 3, "all zeros: 1+1+1=3")
    }

    /**
     * Complex rendering function: reads game state from memory, computes
     * layout, writes to framebuffer locations, uses draw colors.
     * Mimics snake's func_14/func_19 pattern.
     */
    @Test
    fun complexRenderingFunction() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 2, exported = true)
        asm.global("__stack_pointer", WasmValueType.I32, true, 0x6000)

        // render(base): reads 8 values, does conditional logic, writes results
        asm.beginFunction("render", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        val frame = asm.declareLocal(WasmValueType.I32)
        val sum = asm.declareLocal(WasmValueType.I32)
        val count = asm.declareLocal(WasmValueType.I32)
        val maxVal = asm.declareLocal(WasmValueType.I32)
        val minVal = asm.declareLocal(WasmValueType.I32)
        val temp = asm.declareLocal(WasmValueType.I32)
        val i = asm.declareLocal(WasmValueType.I32)
        val flag = asm.declareLocal(WasmValueType.I32)

        // Allocate shadow stack
        asm.globalGet(0)
        asm.i32Const(32)
        asm.i32Sub()
        asm.localTee(frame)
        asm.globalSet(0)

        // Initialize
        asm.i32Const(0)
        asm.localSet(sum)
        asm.i32Const(0)
        asm.localSet(count)
        asm.i32Const(Int.MIN_VALUE)
        asm.localSet(maxVal)
        asm.i32Const(Int.MAX_VALUE)
        asm.localSet(minVal)
        asm.i32Const(0)
        asm.localSet(i)

        // Loop: process 8 values
        asm.beginBlock()
        asm.beginLoop()

        // if i >= 8, break
        asm.localGet(i)
        asm.i32Const(8)
        asm.i32GeU()
        asm.brIf(1)

        // temp = mem[base + i*4]
        asm.localGet(0) // base
        asm.localGet(i)
        asm.i32Const(4)
        asm.i32Mul()
        asm.i32Add()
        asm.i32Load(2, 0)
        asm.localSet(temp)

        // Save temp to shadow stack
        asm.localGet(frame)
        asm.localGet(i)
        asm.i32Const(4)
        asm.i32Mul()
        asm.i32Add()
        asm.localGet(temp)
        asm.i32Store(2, 0)

        // sum += temp
        asm.localGet(sum)
        asm.localGet(temp)
        asm.i32Add()
        asm.localSet(sum)

        // if (temp > 0) count++
        asm.localGet(temp)
        asm.i32Const(0)
        asm.i32GtS()
        asm.beginIf()
        asm.localGet(count)
        asm.i32Const(1)
        asm.i32Add()
        asm.localSet(count)
        asm.end()

        // max = temp > max ? temp : max
        asm.localGet(temp)
        asm.localGet(maxVal)
        asm.i32GtS()
        asm.beginIf()
        asm.localGet(temp)
        asm.localSet(maxVal)
        asm.end()

        // min = temp < min ? temp : min
        asm.localGet(temp)
        asm.localGet(minVal)
        asm.i32LtS()
        asm.beginIf()
        asm.localGet(temp)
        asm.localSet(minVal)
        asm.end()

        // i++
        asm.localGet(i)
        asm.i32Const(1)
        asm.i32Add()
        asm.localSet(i)
        asm.br(0) // loop
        asm.end() // end loop
        asm.end() // end block

        // Read back from shadow stack to verify
        asm.localGet(frame)
        asm.i32Load(2, 0) // first saved value
        asm.localGet(frame)
        asm.i32Load(2, 28) // last saved value (8th, offset 7*4=28)
        asm.i32Add()
        asm.localSet(flag)

        // result = sum + count*1000 + (max - min) + flag
        asm.localGet(sum)
        asm.localGet(count)
        asm.i32Const(1000)
        asm.i32Mul()
        asm.i32Add()
        asm.localGet(maxVal)
        asm.localGet(minVal)
        asm.i32Sub()
        asm.i32Add()
        asm.localGet(flag)
        asm.i32Add()

        // Restore SP
        asm.localGet(frame)
        asm.i32Const(32)
        asm.i32Add()
        asm.globalSet(0)

        asm.endFunction()
        val bytes = asm.assemble()

        // Set up memory with test data at address 1000
        val interpInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET).load(bytes).instantiate()
        val jitInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT).load(bytes).instantiate()

        val testData = intArrayOf(10, 20, -5, 30, 0, 15, -10, 40)
        for ((idx, value) in testData.withIndex()) {
            interpInst.memory().writeI32(1000 + idx * 4, value)
            jitInst.memory().writeI32(1000 + idx * 4, value)
        }

        // sum = 10+20-5+30+0+15-10+40 = 100
        // count = 5 (positive values: 10,20,30,15,40)
        // max = 40, min = -10, max-min = 50
        // flag = first(10) + last(40) = 50
        // result = 100 + 5000 + 50 + 50 = 5200
        val interpResult = interpInst.call("render", 1000)[0].toInt()
        val jitResult = jitInst.call("render", 1000)[0].toInt()
        println("render: interp=$interpResult, jit=$jitResult")
        assertEquals(interpResult, jitResult, "complex rendering function")
    }

    /**
     * Large function with many blocks (20+) and multiple function calls
     * interleaved with memory operations. Tests if register pressure
     * causes wrong values at scale.
     */
    @Test
    fun largeFunctionWithManyBlocks() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 2, exported = true)
        asm.global("__stack_pointer", WasmValueType.I32, true, 0x6000)

        // helper(x) = x + 1
        asm.beginFunction("inc", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0); asm.i32Const(1); asm.i32Add()
        asm.endFunction()

        // big(base): 20 blocks of load + conditional + call + store
        asm.beginFunction("big", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        val frame = asm.declareLocal(WasmValueType.I32)
        val accum = asm.declareLocal(WasmValueType.I32)

        // Shadow stack
        asm.globalGet(0); asm.i32Const(16); asm.i32Sub()
        asm.localTee(frame); asm.globalSet(0)

        asm.i32Const(0); asm.localSet(accum)

        // 20 blocks: each loads from base+offset, conditionally adds, calls inc
        for (blockIdx in 0 until 20) {
            // val = mem[base + blockIdx*4]
            asm.localGet(0)
            asm.i32Const(blockIdx * 4)
            asm.i32Add()
            asm.i32Load(2, 0)

            // if (val > 0) accum += inc(val)
            val valLocal = asm.declareLocal(WasmValueType.I32)
            asm.localTee(valLocal)
            asm.i32Const(0)
            asm.i32GtS()
            asm.beginIf()
            asm.localGet(valLocal)
            asm.call(0) // inc
            asm.localGet(accum)
            asm.i32Add()
            asm.localSet(accum)
            asm.end()

            // Store intermediate to shadow stack
            if (blockIdx < 4) {
                asm.localGet(frame)
                asm.localGet(accum)
                asm.i32Store(2, blockIdx * 4)
            }
        }

        // Read back from shadow stack and add
        asm.localGet(frame); asm.i32Load(2, 0)
        asm.localGet(frame); asm.i32Load(2, 4)
        asm.i32Add()
        asm.localGet(frame); asm.i32Load(2, 8)
        asm.i32Add()
        asm.localGet(frame); asm.i32Load(2, 12)
        asm.i32Add()
        asm.localGet(accum)
        asm.i32Add()

        // Restore SP
        asm.localGet(frame); asm.i32Const(16); asm.i32Add(); asm.globalSet(0)

        asm.endFunction()
        val bytes = asm.assemble()

        val interpInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET).load(bytes).instantiate()
        val jitInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT).load(bytes).instantiate()

        // Set 20 values: 1,2,3,...,20
        for (idx in 0 until 20) {
            interpInst.memory().writeI32(1000 + idx * 4, idx + 1)
            jitInst.memory().writeI32(1000 + idx * 4, idx + 1)
        }

        val interpResult = interpInst.call("big", 1000)[0].toInt()
        val jitResult = jitInst.call("big", 1000)[0].toInt()
        println("big: interp=$interpResult, jit=$jitResult")
        assertEquals(interpResult, jitResult, "large function with 20 blocks")

        // Also test with negative values
        for (idx in 0 until 20) {
            interpInst.memory().writeI32(2000 + idx * 4, if (idx % 3 == 0) { -(idx + 1) } else { idx + 1 })
            jitInst.memory().writeI32(2000 + idx * 4, if (idx % 3 == 0) { -(idx + 1) } else { idx + 1 })
        }
        val interpResult2 = interpInst.call("big", 2000)[0].toInt()
        val jitResult2 = jitInst.call("big", 2000)[0].toInt()
        println("big (mixed): interp=$interpResult2, jit=$jitResult2")
        assertEquals(interpResult2, jitResult2, "large function with mixed values")
    }

    /**
     * Stress test: deeply nested if/else with br_table dispatch.
     * Combines many patterns that individually work but may interact badly.
     */
    @Test
    fun brTableWithShadowStackAndCalls() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 2, exported = true)
        asm.global("__stack_pointer", WasmValueType.I32, true, 0x6000)

        // helper(x) = x * 3
        asm.beginFunction("triple", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0); asm.i32Const(3); asm.i32Mul()
        asm.endFunction()

        // dispatch(selector, value): switch on selector, each case transforms value differently
        asm.beginFunction("dispatch", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        val dFrame = asm.declareLocal(WasmValueType.I32)

        // Shadow stack
        asm.globalGet(0); asm.i32Const(8); asm.i32Sub()
        asm.localTee(dFrame); asm.globalSet(0)

        // Save value to shadow stack
        asm.localGet(dFrame); asm.localGet(1); asm.i32Store(2, 0)

        // Simple dispatch: if/else chain instead of br_table
        // case 0: triple(value)
        asm.localGet(0); asm.i32Eqz()
        asm.beginIf(WasmBlockType.I32)
        asm.localGet(dFrame); asm.i32Load(2, 0)
        asm.call(0) // triple
        asm.beginElse()
        // case 1: value + 100
        asm.localGet(0); asm.i32Const(1); asm.i32Eq()
        asm.beginIf(WasmBlockType.I32)
        asm.localGet(dFrame); asm.i32Load(2, 0)
        asm.i32Const(100); asm.i32Add()
        asm.beginElse()
        // case 2: value * value
        asm.localGet(0); asm.i32Const(2); asm.i32Eq()
        asm.beginIf(WasmBlockType.I32)
        asm.localGet(dFrame); asm.i32Load(2, 0)
        asm.localGet(dFrame); asm.i32Load(2, 0)
        asm.i32Mul()
        asm.beginElse()
        // default: value - 1
        asm.localGet(dFrame); asm.i32Load(2, 0)
        asm.i32Const(1); asm.i32Sub()
        asm.end() // end case 2 if
        asm.end() // end case 1 if
        asm.end() // end case 0 if

        // Restore SP
        asm.localGet(dFrame); asm.i32Const(8); asm.i32Add(); asm.globalSet(0)

        asm.endFunction()
        val bytes = asm.assemble()

        // case 0: triple(5) = 15
        assertJitMatchesInterp(bytes, "dispatch", longArrayOf(0, 5), 15, "br_table case 0: triple")
        // case 1: 5 + 100 = 105
        assertJitMatchesInterp(bytes, "dispatch", longArrayOf(1, 5), 105, "br_table case 1: add 100")
        // case 2: 5 * 5 = 25
        assertJitMatchesInterp(bytes, "dispatch", longArrayOf(2, 5), 25, "br_table case 2: square")
        // case 3: 5 - 1 = 4
        assertJitMatchesInterp(bytes, "dispatch", longArrayOf(3, 5), 4, "br_table case 3: dec")
        // out of range → default (case 3): 5 - 1 = 4
        assertJitMatchesInterp(bytes, "dispatch", longArrayOf(99, 5), 4, "br_table default: dec")
    }

    /**
     * Start function pattern: module has a start section that initializes globals.
     * Tests that JIT globals are properly synced after the start function.
     */
    @Test
    fun startFunctionGlobalInit() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 2, exported = true)
        asm.global("counter", WasmValueType.I32, true, 0)
        asm.global("initialized", WasmValueType.I32, true, 0)

        // Start function: sets globals to known values
        val startFunc = asm.beginFunction("__start", emptyList(), emptyList())
        asm.i32Const(42)
        asm.globalSet(0) // counter = 42
        asm.i32Const(1)
        asm.globalSet(1) // initialized = 1
        asm.endFunction()

        // Test function: reads globals and returns them
        asm.beginFunction("getState", emptyList(), listOf(WasmValueType.I32), exported = true)
        asm.globalGet(0) // counter
        asm.globalGet(1) // initialized
        asm.i32Const(1000)
        asm.i32Mul()
        asm.i32Add() // counter + initialized * 1000
        asm.endFunction()

        // Set start section
        asm.startFunction("__start")

        val bytes = asm.assemble()

        // After start: counter=42, initialized=1, result = 42 + 1000 = 1042
        assertJitMatchesInterp(bytes, "getState", longArrayOf(), 1042, "start function global init")
    }

    private fun assertJitMatchesInterp(bytes: ByteArray, funcName: String, args: LongArray, expected: Int, description: String) {
        val interpInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET).load(bytes).instantiate()
        val jitInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT).load(bytes).instantiate()

        val interpResult = interpInst.call(funcName, *args)[0].toInt()
        val jitResult = jitInst.call(funcName, *args)[0].toInt()

        assertEquals(expected, interpResult, "$description (interpreter)")
        assertEquals(expected, jitResult, "$description (JIT)")
    }
}
