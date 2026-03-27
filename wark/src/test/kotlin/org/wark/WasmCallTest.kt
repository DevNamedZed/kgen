package org.wark

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmRefType
import org.kgen.target.wasm.WasmValueType
import kotlin.test.assertEquals

class WasmCallTest : WasmTestBase() {

    companion object {
    }

    @Nested
    inner class DirectCalls {

        @Test
        fun `call between wasm functions`() {
            val bytes = buildWasmBytes {
                function("double", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(0))
                    asm.i32Add()
                }
                function("callDouble", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.call("double")
                }
            }
            assertBothEqual(10L, bytes, "callDouble", 5L)
            assertBothEqual(0L, bytes, "callDouble", 0L)
        }

        @Test
        fun `recursive fibonacci`() {
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
            assertBothEqual(1L, bytes, "fib", 0L)
            assertBothEqual(1L, bytes, "fib", 1L)
            assertBothEqual(2L, bytes, "fib", 2L)
            assertBothEqual(8L, bytes, "fib", 5L)
            assertBothEqual(89L, bytes, "fib", 10L)
        }
    }

    @Nested
    inner class CallIndirect {

        @Test
        fun `call_indirect simple dispatch`() {
            val bytes = buildWasmBytes {
                table("funcs", WasmRefType.FUNCREF, 2)

                function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Add()
                }
                function("mul", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Mul()
                }

                activeElement(0, 0, listOf(0, 1))

                function("dispatch", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(1))
                    asm.localGet(func.getParameter(2))
                    asm.localGet(func.getParameter(0))
                    asm.callIndirect(0, 0)
                }
            }
            assertBothEqual(7L, bytes, "dispatch", 0L, 3L, 4L)
            assertBothEqual(12L, bytes, "dispatch", 1L, 3L, 4L)
        }

        @Test
        fun `call_indirect with void return via memory side effect`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                table("funcs", WasmRefType.FUNCREF, 1)

                function("storeValue", listOf(WasmValueType.I32), emptyList()) { func, asm ->
                    asm.i32Const(0)
                    asm.localGet(func.getParameter(0))
                    asm.i32Store(0, 0)
                }

                activeElement(0, 0, listOf(0))

                function("indirectStore", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Const(0) // table index 0 = storeValue
                    asm.callIndirect(0, 0) // type 0 = (i32) -> void
                    asm.i32Const(0)
                    asm.i32Load(0, 0) // read back the stored value
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "indirectStore", mode, 99L)
                assertEquals(99, result[0].toInt(), "$mode: call_indirect void via memory")
            }
        }

        @Test
        fun `call_indirect with f64 params and return`() {
            val bytes = buildWasmBytes {
                table("funcs", WasmRefType.FUNCREF, 1)

                function("addF64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64)) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Add()
                }

                activeElement(0, 0, listOf(0))

                function("indirectAddF64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Const(0)
                    asm.callIndirect(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "indirectAddF64", mode, doubleBits(3.0), doubleBits(4.0))
                assertEquals(doubleBits(7.0), result[0], "$mode: call_indirect f64")
            }
        }
    }

    @Nested
    inner class Globals {

        @Test
        fun `global get and set mutable i32`() {
            val bytes = buildWasmBytes {
                global("counter", WasmValueType.I32, mutable = true, initValue = 0L, exported = true)

                function("increment", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.globalGet(0)
                    asm.i32Const(1)
                    asm.i32Add()
                    asm.globalSet(0)
                    asm.globalGet(0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val instance = WarkRuntime.create(WasmTarget.V2_0, mode).load(bytes).instantiate()
                if (mode == ExecutionMode.INTERPRET) {
                    instance.setInstructionLimit(Long.MAX_VALUE)
                }
                val first = instance.call("increment")
                val second = instance.call("increment")
                val third = instance.call("increment")
                assertEquals(1, first[0].toInt(), "$mode: first increment")
                assertEquals(2, second[0].toInt(), "$mode: second increment")
                assertEquals(3, third[0].toInt(), "$mode: third increment")
            }
        }

        @Test
        fun `global get and set mutable i64`() {
            val bytes = buildWasmBytes {
                global("big", WasmValueType.I64, mutable = true, initValue = 0L, exported = true)

                function("setAndGet", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.globalSet(0)
                    asm.globalGet(0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "setAndGet", mode, 0x123456789AL)
                assertEquals(0x123456789AL, result[0], "$mode: global i64")
            }
        }

        @Test
        fun `global get and set mutable f32`() {
            val bytes = buildWasmBytes {
                global("fval", WasmValueType.F32, mutable = true, initValue = floatBits(0.0f), exported = true)

                function("setAndGet", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.globalSet(0)
                    asm.globalGet(0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "setAndGet", mode, floatBits(3.14f))
                assertEquals(floatBits(3.14f), result[0] and 0xFFFFFFFFL, "$mode: global f32")
            }
        }

        @Test
        fun `global get and set mutable f64`() {
            val bytes = buildWasmBytes {
                global("dval", WasmValueType.F64, mutable = true, initValue = doubleBits(0.0), exported = true)

                function("setAndGet", listOf(WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.globalSet(0)
                    asm.globalGet(0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "setAndGet", mode, doubleBits(2.71828))
                assertEquals(doubleBits(2.71828), result[0], "$mode: global f64")
            }
        }
    }

    @Nested
    inner class CallPatterns {

        @Test
        fun `recursive countdown modifies global`() {
            val bytes = buildWasmBytes {
                global("counter", WasmValueType.I32, mutable = true, initValue = 0L, exported = true)

                val func = beginFunction("countdown", listOf(WasmValueType.I32), emptyList(), exported = true)

                globalGet(0)
                i32Const(1)
                i32Add()
                globalSet(0)

                localGet(func.getParameter(0))
                i32Const(1)
                i32LeS()
                beginIf()
                endIf()

                localGet(func.getParameter(0))
                i32Const(1)
                i32GtS()
                beginIf()
                localGet(func.getParameter(0))
                i32Const(1)
                i32Sub()
                call("countdown")
                endIf()

                endFunction()

                function("getCounter", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.globalGet(0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val instance = WarkRuntime.create(WasmTarget.V2_0, mode).load(bytes).instantiate()
                if (mode == ExecutionMode.INTERPRET) {
                    instance.setInstructionLimit(Long.MAX_VALUE)
                }
                instance.call("countdown", 5L)
                val result = instance.call("getCounter")
                assertEquals(5, result[0].toInt(), "$mode: recursive countdown global")
            }
        }

        @Test
        fun `call three functions and sum results`() {
            val bytes = buildWasmBytes {
                function("ten", emptyList(), listOf(WasmValueType.I32)) { func, asm ->
                    asm.i32Const(10)
                }
                function("twenty", emptyList(), listOf(WasmValueType.I32)) { func, asm ->
                    asm.i32Const(20)
                }
                function("thirty", emptyList(), listOf(WasmValueType.I32)) { func, asm ->
                    asm.i32Const(30)
                }
                function("sumAll", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.call("ten")
                    asm.call("twenty")
                    asm.i32Add()
                    asm.call("thirty")
                    asm.i32Add()
                }
            }
            assertBothEqual(60L, bytes, "sumAll")
        }

        @Test
        fun `call_indirect with table index from parameter`() {
            val bytes = buildWasmBytes {
                table("funcs", WasmRefType.FUNCREF, 3)

                function("returnOne", emptyList(), listOf(WasmValueType.I32)) { func, asm ->
                    asm.i32Const(1)
                }
                function("returnTwo", emptyList(), listOf(WasmValueType.I32)) { func, asm ->
                    asm.i32Const(2)
                }
                function("returnThree", emptyList(), listOf(WasmValueType.I32)) { func, asm ->
                    asm.i32Const(3)
                }

                activeElement(0, 0, listOf(0, 1, 2))

                function("callByIndex", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.callIndirect(0, 0)
                }
            }
            assertBothEqual(1L, bytes, "callByIndex", 0L)
            assertBothEqual(2L, bytes, "callByIndex", 1L)
            assertBothEqual(3L, bytes, "callByIndex", 2L)
        }
    }
}
