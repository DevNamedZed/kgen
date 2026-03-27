package org.wark

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarkAdvancedExecutionTest {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    @Test
    fun executeF64Arithmetic() {
        val bytes = buildWasmBytes {
            function("addF64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.f64Add()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val instance = runtime.load(bytes).instantiate()

        val argA = java.lang.Double.doubleToRawLongBits(3.14)
        val argB = java.lang.Double.doubleToRawLongBits(2.86)
        val result = java.lang.Double.longBitsToDouble(instance.call("addF64", argA, argB)[0])
        assertEquals(6.0, result, 0.001)
    }

    @Test
    fun executeMemoryWithOffset() {
        val bytes = buildWasmBytes {
            memory("mem", 1, exported = true)
            function("storeAt", listOf(WasmValueType.I32, WasmValueType.I32), emptyList(), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Store(0, 0)
            }
            function("loadAt", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Load(0, 0)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val instance = runtime.load(bytes).instantiate()

        instance.call("storeAt", 100, 42)
        val loaded = instance.call("loadAt", 100)
        assertEquals(42L, loaded[0])
    }

    @Test
    fun executeFunctionCallBetweenWasmFunctions() {
        val bytes = buildWasmBytes {
            function("double", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(0))
                asm.i32Add()
            }
            function("quadruple", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.call(0)
                asm.call(0)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val instance = runtime.load(bytes).instantiate()

        assertEquals(8L, instance.call("double", 4)[0])
        assertEquals(16L, instance.call("quadruple", 4)[0])
    }

    @Test
    fun executeFibonacci() {
        val bytes = buildWasmBytes {
            val func = beginFunction("fib", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
            val previous = declareLocal(WasmValueType.I32)
            val current = declareLocal(WasmValueType.I32)
            val counter = declareLocal(WasmValueType.I32)
            val temp = declareLocal(WasmValueType.I32)

            i32Const(0)
            localSet(previous)
            i32Const(1)
            localSet(current)
            i32Const(0)
            localSet(counter)

            beginBlock()
            beginLoop()
            localGet(counter)
            localGet(func.getParameter(0))
            i32GeS()
            brIf(1)

            localGet(current)
            localSet(temp)
            localGet(previous)
            localGet(current)
            i32Add()
            localSet(current)
            localGet(temp)
            localSet(previous)

            localGet(counter)
            i32Const(1)
            i32Add()
            localSet(counter)
            br(0)
            end()
            end()

            localGet(current)
            endFunction()
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val instance = runtime.load(bytes).instantiate()

        assertEquals(1L, instance.call("fib", 0)[0])
        assertEquals(1L, instance.call("fib", 1)[0])
        assertEquals(2L, instance.call("fib", 2)[0])
        assertEquals(8L, instance.call("fib", 5)[0])
        assertEquals(89L, instance.call("fib", 10)[0])
    }

    @Test
    fun executeHostFunctionCall() {
        val bytes = buildWasmBytes {
            importFunction("env", "getAnswer", emptyList(), listOf(WasmValueType.I32))
            function("callHost", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.call(0)
            }
        }

        var hostCalled = false
        val imports = WarkImports.builder()
            .function("env", "getAnswer") { instance, args ->
                hostCalled = true
                longArrayOf(42L)
            }
            .build()

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val instance = runtime.load(bytes).instantiate(imports)

        val result = instance.call("callHost")
        assertTrue(hostCalled)
        assertEquals(42L, result[0])
    }

    @Test
    fun executeMultipleMemoryOperations() {
        val bytes = buildWasmBytes {
            memory("mem", 1, exported = true)
            function("sumArray", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                val sum = declareLocal(WasmValueType.I32)
                val index = declareLocal(WasmValueType.I32)

                asm.i32Const(0)
                asm.localSet(sum)
                asm.i32Const(0)
                asm.localSet(index)

                asm.beginBlock()
                beginLoop()

                asm.localGet(sum)
                asm.localGet(func.getParameter(0))
                asm.localGet(index)
                asm.i32Const(4)
                asm.i32Mul()
                asm.i32Add()
                asm.i32Load(0, 0)
                asm.i32Add()
                asm.localSet(sum)

                asm.localGet(index)
                asm.i32Const(1)
                asm.i32Add()
                asm.localSet(index)

                asm.localGet(index)
                asm.localGet(func.getParameter(1))
                asm.i32LtS()
                asm.brIf(0)
                asm.end()
                asm.end()

                asm.localGet(sum)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val instance = runtime.load(bytes).instantiate()

        val memory = instance.memory()
        memory.writeI32(0, 10)
        memory.writeI32(4, 20)
        memory.writeI32(8, 30)

        val result = instance.call("sumArray", 0, 3)
        assertEquals(60L, result[0])
    }
}
