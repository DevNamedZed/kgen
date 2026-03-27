package org.wark

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import kotlin.test.assertEquals

class WarkInterpreterTest {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    private fun createInstance(bytes: ByteArray, imports: WarkImports = WarkImports.empty()): WarkInstance {
        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        return runtime.load(bytes).instantiate(imports)
    }

    @Test
    fun interpretConstant() {
        val bytes = buildWasmBytes {
            function("answer", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.i32Const(42)
            }
        }
        assertEquals(42L, createInstance(bytes).call("answer")[0])
    }

    @Test
    fun interpretAdd() {
        val bytes = buildWasmBytes {
            function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Add()
            }
        }
        assertEquals(7L, createInstance(bytes).call("add", 3, 4)[0])
    }

    @Test
    fun interpretComparison() {
        val bytes = buildWasmBytes {
            function("isZero", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Eqz()
            }
        }
        val instance = createInstance(bytes)
        assertEquals(1L, instance.call("isZero", 0)[0])
        assertEquals(0L, instance.call("isZero", 42)[0])
    }

    @Test
    fun interpretLoop() {
        val bytes = buildWasmBytes {
            val func = beginFunction("countTo5", emptyList(), listOf(WasmValueType.I32), exported = true)
            val counter = declareLocal(WasmValueType.I32)

            i32Const(0)
            localSet(counter)

            beginBlock()
            beginLoop()
            localGet(counter)
            i32Const(1)
            i32Add()
            localSet(counter)

            localGet(counter)
            i32Const(5)
            i32LtS()
            brIf(0)
            end()

            localGet(counter)
            endFunction()
        }
        assertEquals(5L, createInstance(bytes).call("countTo5")[0])
    }

    @Test
    fun interpretFibonacci() {
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
        val instance = createInstance(bytes)
        assertEquals(1L, instance.call("fib", 0)[0])
        assertEquals(1L, instance.call("fib", 1)[0])
        assertEquals(2L, instance.call("fib", 2)[0])
        assertEquals(89L, instance.call("fib", 10)[0])
    }

    @Test
    fun interpretMemory() {
        val bytes = buildWasmBytes {
            memory("mem", 1, exported = true)
            function("storeLoad", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Store(0, 0)
                asm.localGet(func.getParameter(0))
                asm.i32Load(0, 0)
            }
        }
        assertEquals(99L, createInstance(bytes).call("storeLoad", 0, 99)[0])
    }

    @Test
    fun interpretHostFunction() {
        val bytes = buildWasmBytes {
            importFunction("env", "getValue", emptyList(), listOf(WasmValueType.I32))
            function("callHost", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.call(0)
            }
        }
        val imports = WarkImports.builder()
            .function("env", "getValue") { instance, args -> longArrayOf(77L) }
            .build()
        assertEquals(77L, createInstance(bytes, imports).call("callHost")[0])
    }

    @Test
    fun interpretF64() {
        val bytes = buildWasmBytes {
            function("addF64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.f64Add()
            }
        }
        val instance = createInstance(bytes)
        val argA = java.lang.Double.doubleToRawLongBits(3.14)
        val argB = java.lang.Double.doubleToRawLongBits(2.86)
        val result = java.lang.Double.longBitsToDouble(instance.call("addF64", argA, argB)[0])
        assertEquals(6.0, result, 0.001)
    }

    @Test
    fun interpretSum1to10() {
        val bytes = buildWasmBytes {
            val func = beginFunction("sum", emptyList(), listOf(WasmValueType.I32), exported = true)
            val sum = declareLocal(WasmValueType.I32)
            val counter = declareLocal(WasmValueType.I32)

            i32Const(0)
            localSet(sum)
            i32Const(1)
            localSet(counter)

            beginBlock()
            beginLoop()
            localGet(sum)
            localGet(counter)
            i32Add()
            localSet(sum)

            localGet(counter)
            i32Const(1)
            i32Add()
            localSet(counter)

            localGet(counter)
            i32Const(11)
            i32LtS()
            brIf(0)
            end()

            localGet(sum)
            endFunction()
        }
        assertEquals(55L, createInstance(bytes).call("sum")[0])
    }

    @Test
    fun interpretFunctionCallBetweenWasmFunctions() {
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
        val instance = createInstance(bytes)
        assertEquals(8L, instance.call("double", 4)[0])
        assertEquals(16L, instance.call("quadruple", 4)[0])
    }
}
