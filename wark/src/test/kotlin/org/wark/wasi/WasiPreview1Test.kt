package org.wark.wasi

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkRuntime
import org.wark.WasmTarget
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WasiPreview1Test {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    @Test
    fun fdWriteToStdout() {
        val output = ByteArrayOutputStream()
        val wasi = WasiPreview1.builder()
            .stdout(output)
            .build()

        val bytes = buildWasmBytes {
            importFunction("wasi_snapshot_preview1", "fd_write",
                listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32),
                listOf(WasmValueType.I32))
            memory("mem", 1, exported = true)

            val func = beginFunction("_start", emptyList(), emptyList(), exported = true)

            i32Const(100)
            i32Const(0)
            i32Store(0, 0)
            i32Const(104)
            i32Const(13)
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
            i32Const(44)
            i32Store8(0, 0)
            i32Const(6)
            i32Const(32)
            i32Store8(0, 0)
            i32Const(7)
            i32Const(87)
            i32Store8(0, 0)
            i32Const(8)
            i32Const(111)
            i32Store8(0, 0)
            i32Const(9)
            i32Const(114)
            i32Store8(0, 0)
            i32Const(10)
            i32Const(108)
            i32Store8(0, 0)
            i32Const(11)
            i32Const(100)
            i32Store8(0, 0)
            i32Const(12)
            i32Const(10)
            i32Store8(0, 0)

            i32Const(1)
            i32Const(100)
            i32Const(1)
            i32Const(200)
            call(0)
            drop()

            endFunction()
        }

        val imports = wasi.registerImports(WarkImports.builder()).build()
        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate(imports)
        instance.call("_start")

        assertEquals("Hello, World\n", output.toString())
    }

    @Test
    fun argsSizesGet() {
        val wasi = WasiPreview1.builder()
            .args("program", "--verbose", "file.txt")
            .build()

        val bytes = buildWasmBytes {
            importFunction("wasi_snapshot_preview1", "args_sizes_get",
                listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            memory("mem", 1, exported = true)

            function("getArgCount", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.i32Const(0)
                asm.i32Const(4)
                asm.call(0)
                asm.drop()
                asm.i32Const(0)
                asm.i32Load(0, 0)
            }
        }

        val imports = wasi.registerImports(WarkImports.builder()).build()
        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate(imports)

        assertEquals(3L, instance.call("getArgCount")[0])
    }

    @Test
    fun clockTimeGet() {
        val wasi = WasiPreview1.builder().build()

        val bytes = buildWasmBytes {
            importFunction("wasi_snapshot_preview1", "clock_time_get",
                listOf(WasmValueType.I32, WasmValueType.I64, WasmValueType.I32), listOf(WasmValueType.I32))
            memory("mem", 1, exported = true)

            function("getTime", emptyList(), listOf(WasmValueType.I64), exported = true) { func, asm ->
                asm.i32Const(0)
                asm.i64Const(0)
                asm.i32Const(0)
                asm.call(0)
                asm.drop()
                asm.i32Const(0)
                asm.i64Load(0, 0)
            }
        }

        val imports = wasi.registerImports(WarkImports.builder()).build()
        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate(imports)

        val nanos = instance.call("getTime")[0]
        assertTrue(nanos > 0, "Clock time should be positive, got $nanos")
    }

    @Test
    fun procExit() {
        val wasi = WasiPreview1.builder().build()

        val bytes = buildWasmBytes {
            importFunction("wasi_snapshot_preview1", "proc_exit",
                listOf(WasmValueType.I32), emptyList())

            function("exit42", emptyList(), emptyList(), exported = true) { func, asm ->
                asm.i32Const(42)
                asm.call(0)
            }
        }

        val imports = wasi.registerImports(WarkImports.builder()).build()
        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate(imports)

        val exception = assertFailsWith<WasiExitException> {
            instance.call("exit42")
        }
        assertEquals(42, exception.exitCode)
    }
}
