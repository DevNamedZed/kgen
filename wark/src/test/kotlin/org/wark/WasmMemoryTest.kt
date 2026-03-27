package org.wark

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import kotlin.test.assertEquals

class WasmMemoryTest : WasmTestBase() {

    companion object {
    }

    @Nested
    inner class I32LoadStore {

        @Test
        fun `i32 store and load`() {
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
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "storeLoad", mode, 0L, 42L)
                assertEquals(42, result[0].toInt(), "$mode: i32 store/load")
            }
        }

        @Test
        fun `i32 store8 and load8_u`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Store8(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.i32Load8U(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, 0xFFL)
                assertEquals(0xFF, result[0].toInt(), "$mode: i32 store8/load8_u")
            }
        }

        @Test
        fun `i32 store8 and load8_s`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Store8(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.i32Load8S(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, 0x80L)
                assertEquals(-128, result[0].toInt(), "$mode: i32 store8/load8_s")
            }
        }

        @Test
        fun `i32 store16 and load16_u`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Store16(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.i32Load16U(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, 0xFFFFL)
                assertEquals(0xFFFF, result[0].toInt(), "$mode: i32 store16/load16_u")
            }
        }

        @Test
        fun `i32 store16 and load16_s`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Store16(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.i32Load16S(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, 0x8000L)
                assertEquals(-32768, result[0].toInt(), "$mode: i32 store16/load16_s")
            }
        }
    }

    @Nested
    inner class I64LoadStore {

        @Test
        fun `i64 store and load`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Store(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.i64Load(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, 0x123456789ABCDEFL)
                assertEquals(0x123456789ABCDEFL, result[0], "$mode: i64 store/load")
            }
        }

        @Test
        fun `i64 store8 and load8_u`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Store8(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.i64Load8U(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, 0xABL)
                assertEquals(0xABL, result[0], "$mode: i64 store8/load8_u")
            }
        }

        @Test
        fun `i64 store8 and load8_s`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Store8(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.i64Load8S(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, 0x80L)
                assertEquals(-128L, result[0], "$mode: i64 store8/load8_s")
            }
        }

        @Test
        fun `i64 store16 and load16_u`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Store16(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.i64Load16U(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, 0xABCDL)
                assertEquals(0xABCDL, result[0], "$mode: i64 store16/load16_u")
            }
        }

        @Test
        fun `i64 store16 and load16_s`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Store16(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.i64Load16S(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, 0x8000L)
                assertEquals(-32768L, result[0], "$mode: i64 store16/load16_s")
            }
        }

        @Test
        fun `i64 store32 and load32_u`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Store32(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.i64Load32U(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, 0xDEADBEEFL)
                assertEquals(0xDEADBEEFL, result[0], "$mode: i64 store32/load32_u")
            }
        }

        @Test
        fun `i64 store32 and load32_s`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Store32(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.i64Load32S(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, 0x80000000L)
                assertEquals(-2147483648L, result[0], "$mode: i64 store32/load32_s")
            }
        }
    }

    @Nested
    inner class F32F64LoadStore {

        @Test
        fun `f32 store and load`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Store(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.f32Load(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, floatBits(3.14f))
                assertEquals(floatBits(3.14f), result[0] and 0xFFFFFFFFL, "$mode: f32 store/load")
            }
        }

        @Test
        fun `f64 store and load`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("test", listOf(WasmValueType.I32, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Store(0, 0)
                    asm.localGet(func.getParameter(0))
                    asm.f64Load(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "test", mode, 0L, doubleBits(3.14159))
                assertEquals(doubleBits(3.14159), result[0], "$mode: f64 store/load")
            }
        }
    }

    @Nested
    inner class MemorySizeGrow {

        @Test
        fun `memory size initial`() {
            val bytes = buildWasmBytes {
                memory("mem", 2, exported = true)
                function("size", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.memorySize(0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "size", mode)
                assertEquals(2, result[0].toInt(), "$mode: memory.size initial")
            }
        }

        @Test
        fun `memory grow returns previous size`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("grow", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.memoryGrow(0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "grow", mode, 2L)
                assertEquals(1, result[0].toInt(), "$mode: memory.grow return value")
            }
        }

        @Test
        fun `memory grow increases size`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("growAndSize", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.i32Const(3)
                    asm.memoryGrow(0)
                    asm.drop()
                    asm.memorySize(0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val result = run(bytes, "growAndSize", mode)
                assertEquals(4, result[0].toInt(), "$mode: memory.size after grow")
            }
        }

        @Test
        fun `memory load with non-zero offset`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("storeAtOffset", listOf(WasmValueType.I32, WasmValueType.I32), emptyList(), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Store(0, 8)
                }
                function("loadAtOffset", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Load(0, 8)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val instance = WarkRuntime.create(WasmTarget.V2_0, mode).load(bytes).instantiate()
                if (mode == ExecutionMode.INTERPRET) {
                    instance.setInstructionLimit(Long.MAX_VALUE)
                }
                instance.call("storeAtOffset", 0L, 99L)
                val result = instance.call("loadAtOffset", 0L)
                assertEquals(99, result[0].toInt(), "$mode: memory with memarg offset")
            }
        }
    }

    @Nested
    inner class MemoryPatterns {

        @Test
        fun `store at memarg offset 100 and load back`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("store100", listOf(WasmValueType.I32), emptyList(), exported = true) { func, asm ->
                    asm.i32Const(0)
                    asm.localGet(func.getParameter(0))
                    asm.i32Store(0, 100)
                }
                function("load100", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.i32Const(0)
                    asm.i32Load(0, 100)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val instance = WarkRuntime.create(WasmTarget.V2_0, mode).load(bytes).instantiate()
                if (mode == ExecutionMode.INTERPRET) {
                    instance.setInstructionLimit(Long.MAX_VALUE)
                }
                instance.call("store100", 0xDEADBEEFL.toLong())
                val result = instance.call("load100")
                assertEquals(0xDEADBEEFL.toInt(), result[0].toInt(), "$mode: memarg offset 100")
            }
        }

        @Test
        fun `multiple stores and loads at different addresses`() {
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
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val instance = WarkRuntime.create(WasmTarget.V2_0, mode).load(bytes).instantiate()
                if (mode == ExecutionMode.INTERPRET) {
                    instance.setInstructionLimit(Long.MAX_VALUE)
                }
                instance.call("storeAt", 0L, 111L)
                instance.call("storeAt", 16L, 222L)
                instance.call("storeAt", 32L, 333L)

                val resultA = instance.call("loadAt", 0L)
                val resultB = instance.call("loadAt", 16L)
                val resultC = instance.call("loadAt", 32L)

                assertEquals(111, resultA[0].toInt(), "$mode: load from address 0")
                assertEquals(222, resultB[0].toInt(), "$mode: load from address 16")
                assertEquals(333, resultC[0].toInt(), "$mode: load from address 32")
            }
        }

        @Test
        fun `store i32 then load i64 reads adjacent bytes`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)
                function("storeI32", listOf(WasmValueType.I32, WasmValueType.I32), emptyList(), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Store(0, 0)
                }
                function("loadI64", listOf(WasmValueType.I32), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64Load(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val instance = WarkRuntime.create(WasmTarget.V2_0, mode).load(bytes).instantiate()
                if (mode == ExecutionMode.INTERPRET) {
                    instance.setInstructionLimit(Long.MAX_VALUE)
                }
                instance.call("storeI32", 0L, 0x12345678L)
                instance.call("storeI32", 4L, 0xAABBCCDDL.toLong())
                val result = instance.call("loadI64", 0L)
                val expected = 0x12345678L or (0xAABBCCDDL.toLong() shl 32)
                assertEquals(expected, result[0], "$mode: i32 store then i64 load")
            }
        }
    }

    @Nested
    inner class BulkMemory {

        @Test
        fun `memory fill writes byte pattern`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)

                function("fill", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList(), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.localGet(func.getParameter(2))
                    asm.memoryFill(0)
                }
                function("load", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Load8U(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val instance = WarkRuntime.create(WasmTarget.V2_0, mode).load(bytes).instantiate()
                if (mode == ExecutionMode.INTERPRET) { instance.setInstructionLimit(Long.MAX_VALUE) }
                instance.call("fill", 10L, 0xABL, 5L)
                for (offset in 10L..14L) {
                    val result = instance.call("load", offset)
                    assertEquals(0xABL, result[0], "$mode: byte at offset $offset")
                }
                val before = instance.call("load", 9L)
                assertEquals(0L, before[0], "$mode: byte before fill region")
                val after = instance.call("load", 15L)
                assertEquals(0L, after[0], "$mode: byte after fill region")
            }
        }

        @Test
        fun `memory copy overlapping forward`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)

                function("store", listOf(WasmValueType.I32, WasmValueType.I32), emptyList(), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Store8(0, 0)
                }
                function("copy", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList(), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.localGet(func.getParameter(2))
                    asm.memoryCopy(0, 0)
                }
                function("load", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Load8U(0, 0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val instance = WarkRuntime.create(WasmTarget.V2_0, mode).load(bytes).instantiate()
                if (mode == ExecutionMode.INTERPRET) { instance.setInstructionLimit(Long.MAX_VALUE) }
                instance.call("store", 0L, 0x11L)
                instance.call("store", 1L, 0x22L)
                instance.call("store", 2L, 0x33L)
                instance.call("store", 3L, 0x44L)
                instance.call("copy", 10L, 0L, 4L)
                assertEquals(0x11L, instance.call("load", 10L)[0], "$mode: copied byte 0")
                assertEquals(0x22L, instance.call("load", 11L)[0], "$mode: copied byte 1")
                assertEquals(0x33L, instance.call("load", 12L)[0], "$mode: copied byte 2")
                assertEquals(0x44L, instance.call("load", 13L)[0], "$mode: copied byte 3")
            }
        }

        @Test
        fun `nop has no effect`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.nop()
                    asm.nop()
                    asm.nop()
                    asm.i32Const(1)
                    asm.i32Add()
                }
            }
            assertBothEqual(6L, bytes, "test", 5L)
        }

        @Test
        fun `memory grow returns previous page count`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)

                function("grow", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.memoryGrow(0)
                }
                function("size", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.memorySize(0)
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                val instance = WarkRuntime.create(WasmTarget.V2_0, mode).load(bytes).instantiate()
                if (mode == ExecutionMode.INTERPRET) { instance.setInstructionLimit(Long.MAX_VALUE) }
                val sizeBeforeGrow = instance.call("size")
                assertEquals(1L, sizeBeforeGrow[0], "$mode: initial size")
                val prevSize = instance.call("grow", 2L)
                assertEquals(1L, prevSize[0], "$mode: grow returns previous size")
                val sizeAfterGrow = instance.call("size")
                assertEquals(3L, sizeAfterGrow[0], "$mode: size after growing by 2")
            }
        }

        @Test
        fun `memory fill then load i32`() {
            val bytes = buildWasmBytes {
                memory("mem", 1, exported = true)

                function("fillAndLoad", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.i32Const(0)
                    asm.localGet(func.getParameter(0))
                    asm.i32Const(4)
                    asm.memoryFill(0)
                    asm.i32Const(0)
                    asm.i32Load(0, 0)
                }
            }
            assertBothEqual(0x42424242L, bytes, "fillAndLoad", 0x42L)
        }
    }
}
