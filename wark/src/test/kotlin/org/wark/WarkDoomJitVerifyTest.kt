package org.wark

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarkDoomJitVerifyTest {

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun jitGlobalGetReturnsCorrectValue() {
        val assembler = WasmAssembler.create()
        assembler.global("sp", WasmValueType.I32, true, 4659040)
        assembler.function("getSp", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
            asm.globalGet(0)
        }
        val bytes = assembler.assemble()
        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
        val instance = runtime.load(bytes).instantiate()
        assertEquals(4659040L, instance.call("getSp")[0])
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun jitGlobalSetAndGet() {
        val assembler = WasmAssembler.create()
        assembler.global("counter", WasmValueType.I32, true, 0)
        assembler.function("increment", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
            asm.globalGet(0)
            asm.i32Const(1)
            asm.i32Add()
            asm.globalSet(0)
            asm.globalGet(0)
        }
        val bytes = assembler.assemble()
        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
        val instance = runtime.load(bytes).instantiate()
        assertEquals(1L, instance.call("increment")[0])
        assertEquals(2L, instance.call("increment")[0])
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun jitMemoryLoadStore() {
        val assembler = WasmAssembler.create()
        assembler.memory("mem", 1, exported = true)
        assembler.function("storeAndLoad", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
            asm.localGet(func.getParameter(0))
            asm.localGet(func.getParameter(1))
            asm.i32Store(0, 0)
            asm.localGet(func.getParameter(0))
            asm.i32Load(0, 0)
        }
        val bytes = assembler.assemble()
        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
        val instance = runtime.load(bytes).instantiate()
        assertEquals(42L, instance.call("storeAndLoad", 100, 42)[0])
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun jitCallsHostFunction() {
        val assembler = WasmAssembler.create()
        assembler.importFunction("env", "callback", listOf(WasmValueType.I32, WasmValueType.I32), emptyList())
        assembler.function("callHost", emptyList(), emptyList(), exported = true) { func, asm ->
            asm.i32Const(640)
            asm.i32Const(400)
            asm.call(0)
        }
        val bytes = assembler.assemble()
        var called = false
        var argA = 0; var argB = 0
        val imports = WarkImports.builder()
            .function("env", "callback") { inst, args ->
                called = true; argA = args[0].toInt(); argB = args[1].toInt()
                longArrayOf()
            }.build()
        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
        val instance = runtime.load(bytes).instantiate(imports)
        instance.call("callHost")
        assertTrue(called)
        assertEquals(640, argA)
        assertEquals(400, argB)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun jitStackPointerPattern() {
        // Tests the exact pattern DOOM's func_379 uses:
        // global.get $0 → sub 16 → local.tee → global.set → call → restore → global.set
        val assembler = WasmAssembler.create()
        assembler.global("sp", WasmValueType.I32, true, 4659040)
        assembler.importFunction("env", "doWork", listOf(WasmValueType.I32), emptyList())
        assembler.function("withStack", emptyList(), emptyList(), exported = true) { func, asm ->
            val savedSp = asm.declareLocal(WasmValueType.I32)
            asm.globalGet(0)
            asm.i32Const(16)
            asm.i32Sub()
            asm.localTee(savedSp)
            asm.globalSet(0)
            // call host with the saved stack pointer
            asm.localGet(savedSp)
            asm.call(0)
            // restore stack pointer
            asm.localGet(savedSp)
            asm.i32Const(16)
            asm.i32Add()
            asm.globalSet(0)
        }
        val bytes = assembler.assemble()

        var receivedSp = 0
        val imports = WarkImports.builder()
            .function("env", "doWork") { inst, args ->
                receivedSp = args[0].toInt()
                longArrayOf()
            }.build()

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
        val instance = runtime.load(bytes).instantiate(imports)
        instance.call("withStack")
        assertEquals(4659040 - 16, receivedSp, "Host should receive sp-16")
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun jitCrossModuleCallWithGlobals() {
        // Two functions: A calls B, both use globals
        val assembler = WasmAssembler.create()
        assembler.global("sp", WasmValueType.I32, true, 1000)
        assembler.importFunction("env", "report", listOf(WasmValueType.I32), emptyList())

        assembler.function("inner", emptyList(), emptyList()) { func, asm ->
            asm.globalGet(0)
            asm.call(0) // report(sp)
        }
        assembler.function("outer", emptyList(), emptyList(), exported = true) { func, asm ->
            asm.globalGet(0)
            asm.i32Const(32)
            asm.i32Sub()
            asm.globalSet(0)
            asm.call(1) // inner()
            asm.globalGet(0)
            asm.i32Const(32)
            asm.i32Add()
            asm.globalSet(0)
        }
        val bytes = assembler.assemble()

        var reportedValue = -1
        val imports = WarkImports.builder()
            .function("env", "report") { inst, args ->
                reportedValue = args[0].toInt()
                longArrayOf()
            }.build()

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
        val instance = runtime.load(bytes).instantiate(imports)
        instance.call("outer")
        assertEquals(1000 - 32, reportedValue, "inner should see sp after outer's sub")
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun jitMemoryStoreAndHostRead() {
        // Simpler version: memory + import, no globals
        val assembler = WasmAssembler.create()
        assembler.importFunction("env", "onInit", listOf(WasmValueType.I32), emptyList())
        assembler.memory("mem", 1, exported = true)

        assembler.function("init", emptyList(), emptyList(), exported = true) { func, asm ->
            asm.i32Const(0)
            asm.i32Const(42)
            asm.i32Store(0, 0)
            asm.i32Const(0)
            asm.i32Load(0, 0)
            asm.call(0)
        }
        val bytes = assembler.assemble()

        var received = -1
        val imports = WarkImports.builder()
            .function("env", "onInit") { inst, args ->
                received = args[0].toInt()
                longArrayOf()
            }.build()

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
        val instance = runtime.load(bytes).instantiate(imports)
        instance.call("init")
        assertEquals(42, received)
    }
}
