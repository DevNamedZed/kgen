package org.wark

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

/**
 * Tests that simulate real-world C-compiled WASM patterns.
 */
class WarkRealWorldTest {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    @Test
    fun stringCopyWithMemory() {
        val bytes = buildWasmBytes {
            memory("mem", 1, exported = true)
            function("strlen", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                val length = declareLocal(WasmValueType.I32)
                asm.i32Const(0)
                asm.localSet(length)

                asm.beginBlock()
                asm.beginLoop()

                asm.localGet(func.getParameter(0))
                asm.localGet(length)
                asm.i32Add()
                asm.i32Load8U(0, 0)
                asm.i32Eqz()
                asm.brIf(1)

                asm.localGet(length)
                asm.i32Const(1)
                asm.i32Add()
                asm.localSet(length)
                asm.br(0)
                asm.end()
                asm.end()

                asm.localGet(length)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate()

        val memory = instance.memory()
        memory.writeUtf8(0, "Hello")

        assertEquals(5L, instance.call("strlen", 0)[0])
    }

    @Test
    fun mallocStyleBumpAllocator() {
        val bytes = buildWasmBytes {
            memory("mem", 1, exported = true)

            function("bump_alloc", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                val currentOffset = declareLocal(WasmValueType.I32)

                asm.i32Const(0)
                asm.i32Load(0, 0)
                asm.localSet(currentOffset)

                asm.i32Const(0)
                asm.localGet(currentOffset)
                asm.localGet(func.getParameter(0))
                asm.i32Add()
                asm.i32Store(0, 0)

                asm.localGet(currentOffset)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate()

        val memory = instance.memory()
        memory.writeI32(0, 1024)

        val first = instance.call("bump_alloc", 64)[0]
        val second = instance.call("bump_alloc", 128)[0]

        assertEquals(1024L, first)
        assertEquals(1088L, second)
    }

    @Test
    fun wasiHelloWorld() {
        val output = ByteArrayOutputStream()

        val bytes = buildWasmBytes {
            importFunction("wasi_snapshot_preview1", "fd_write",
                listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32),
                listOf(WasmValueType.I32))
            memory("mem", 1, exported = true)

            val func = beginFunction("_start", emptyList(), emptyList(), exported = true)

            i32Const(8)
            i32Const(0)
            i32Store(0, 0)
            i32Const(12)
            i32Const(6)
            i32Store(0, 0)

            i32Const(0)
            i32Const(72)
            i32Store8(0, 0)
            i32Const(1)
            i32Const(101)
            i32Store8(0, 0)
            i32Const(2)
            i32Const(108)
            i32Store8(0, 0)
            i32Const(3)
            i32Const(108)
            i32Store8(0, 0)
            i32Const(4)
            i32Const(111)
            i32Store8(0, 0)
            i32Const(5)
            i32Const(10)
            i32Store8(0, 0)

            i32Const(1)
            i32Const(8)
            i32Const(1)
            i32Const(100)
            call(0)
            drop()

            endFunction()
        }

        val exitCode = WarkRunner.builder()
            .bytes(bytes)
            .stdout(output)
            .mode(ExecutionMode.INTERPRET)
            .build()
            .run()

        assertEquals(0, exitCode)
        assertEquals("Hello\n", output.toString())
    }

    @Test
    fun matrixMultiply2x2() {
        val bytes = buildWasmBytes {
            memory("mem", 1, exported = true)

            function("matmul2x2", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList(), exported = true) { func, asm ->
                val resultAddress = func.getParameter(2)

                asm.localGet(resultAddress)
                asm.localGet(func.getParameter(0))
                asm.i32Load(0, 0)
                asm.localGet(func.getParameter(1))
                asm.i32Load(0, 0)
                asm.i32Mul()
                asm.localGet(func.getParameter(0))
                asm.i32Load(0, 4)
                asm.localGet(func.getParameter(1))
                asm.i32Load(0, 8)
                asm.i32Mul()
                asm.i32Add()
                asm.i32Store(0, 0)

                asm.localGet(resultAddress)
                asm.localGet(func.getParameter(0))
                asm.i32Load(0, 0)
                asm.localGet(func.getParameter(1))
                asm.i32Load(0, 4)
                asm.i32Mul()
                asm.localGet(func.getParameter(0))
                asm.i32Load(0, 4)
                asm.localGet(func.getParameter(1))
                asm.i32Load(0, 12)
                asm.i32Mul()
                asm.i32Add()
                asm.i32Store(0, 4)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate()
        val memory = instance.memory()

        memory.writeI32(0, 1)
        memory.writeI32(4, 2)
        memory.writeI32(8, 3)
        memory.writeI32(12, 4)

        memory.writeI32(16, 5)
        memory.writeI32(20, 6)
        memory.writeI32(24, 7)
        memory.writeI32(28, 8)

        instance.call("matmul2x2", 0, 16, 32)

        assertEquals(19, memory.readI32(32))
        assertEquals(22, memory.readI32(36))
    }

    @Test
    fun bitManipulation() {
        val bytes = buildWasmBytes {
            function("clz", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Clz()
            }
            function("ctz", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Ctz()
            }
            function("popcnt", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Popcnt()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate()

        assertEquals(24L, instance.call("clz", 0xFF)[0])
        assertEquals(8L, instance.call("ctz", 0x100)[0])
        assertEquals(8L, instance.call("popcnt", 0xFF)[0])
    }

    @Test
    fun unsignedArithmetic() {
        val bytes = buildWasmBytes {
            function("divU", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32DivU()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate()

        assertEquals(2L, instance.call("divU", 10, 5)[0])
    }
}
