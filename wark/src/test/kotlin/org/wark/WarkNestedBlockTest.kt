package org.wark

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import kotlin.test.assertEquals

class WarkNestedBlockTest {

    @Test
    fun twelveNestedBlocksJit() {
        val asm = WasmAssembler.create()
        asm.beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)
        for (i in 0 until 12) { asm.beginBlock() }
        asm.i32Const(1)
        asm.brIf(0)
        for (i in 0 until 12) { asm.end() }
        asm.i32Const(42)
        asm.endFunction()
        val bytes = asm.assemble()
        val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT).load(bytes).instantiate()
        assertEquals(42L, instance.call("test")[0])
    }

    @Test
    fun memoryLoadLargeOffsetJit() {
        // Test DOOM's pattern: i32.const 0; i32.load offset=4591936
        val asm = WasmAssembler.create()
        asm.memory("mem", 72, exported = true) // 72 pages = 4.7MB like DOOM
        asm.beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)
        asm.i32Const(0)
        asm.i32Load(2, 4591936) // align=4, offset=4591936
        asm.endFunction()
        val bytes = asm.assemble()

        // Interpreter
        val interpInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET).load(bytes).instantiate()
        val interpResult = interpInst.call("test")[0]

        // JIT
        val jitInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT).load(bytes).instantiate()
        val jitResult = jitInst.call("test")[0]

        println("memoryLoadLargeOffset: interp=$interpResult jit=$jitResult")
        assertEquals(interpResult, jitResult)
    }

    @Test
    fun memoryStoreAndCallJit() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 72, exported = true)

        // func_a: takes (i32 x), returns x+1
        asm.beginFunction("add1", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0)
        asm.i32Const(1)
        asm.i32Add()
        asm.endFunction()

        // func_b: stores p0 to memory[100], stores p1 to memory[104], calls add1(p0), returns result
        asm.beginFunction("storeAndCall", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        // store p0 at offset 100
        asm.i32Const(0)
        asm.localGet(0)
        asm.i32Store(2, 100)
        // store p1 at offset 104
        asm.i32Const(0)
        asm.localGet(1)
        asm.i32Store(2, 104)
        // call add1(p0)
        asm.localGet(0)
        asm.call(0) // call add1
        asm.endFunction()

        val bytes = asm.assemble()
        val jitInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT).load(bytes).instantiate()
        val result = jitInst.call("storeAndCall", 42L, 99L)
        println("storeAndCall(42, 99) = ${result[0]}")
        assertEquals(43L, result[0])

        // Verify stores worked
        val mem = jitInst.memory()
        assertEquals(42, mem.readI32(100))
        assertEquals(99, mem.readI32(104))
    }

    @Test
    fun globalReadWriteJit() {
        val asm = WasmAssembler.create()
        asm.global("sp", WasmValueType.I32, true, 1000) // mutable global, initial=1000
        asm.beginFunction("getSp", emptyList(), listOf(WasmValueType.I32), exported = true)
        asm.globalGet(0)
        asm.endFunction()
        asm.beginFunction("allocate", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.globalGet(0) // old SP
        asm.localGet(0) // size
        asm.i32Sub() // new SP = old - size
        asm.globalSet(0) // write back
        asm.globalGet(0) // return new SP
        asm.endFunction()

        val bytes = asm.assemble()

        // Interpreter
        val interpInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET).load(bytes).instantiate()
        val interpSp = interpInst.call("getSp")[0]
        val interpAlloc = interpInst.call("allocate", 16L)[0]
        val interpSpAfter = interpInst.call("getSp")[0]
        println("Interp: getSp=$interpSp, allocate(16)=$interpAlloc, getSp=$interpSpAfter")

        // JIT
        val jitInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT).load(bytes).instantiate()
        val jitSp = jitInst.call("getSp")[0]
        val jitAlloc = jitInst.call("allocate", 16L)[0]
        val jitSpAfter = jitInst.call("getSp")[0]
        println("JIT:    getSp=$jitSp, allocate(16)=$jitAlloc, getSp=$jitSpAfter")

        assertEquals(interpSp, jitSp, "Initial SP should match")
        assertEquals(interpAlloc, jitAlloc, "Allocation result should match")
        assertEquals(interpSpAfter, jitSpAfter, "SP after allocation should match")
    }

    @Test
    fun unsignedLessThanJit() {
        val asm = WasmAssembler.create()
        asm.function("ltU", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, a ->
            a.localGet(func.getParameter(0))
            a.localGet(func.getParameter(1))
            a.i32LtU()
        }
        val bytes = asm.assemble()

        val jitInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT).load(bytes).instantiate()
        assertEquals(0L, jitInst.call("ltU", 4718592, 4659040)[0], "4718592 < 4659040 should be false")
        assertEquals(1L, jitInst.call("ltU", 100, 200)[0], "100 < 200 should be true")
    }
}
