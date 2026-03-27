package org.wark

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class WarkRunnerTest {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    @Test
    fun runSimpleWasiProgram() {
        val output = ByteArrayOutputStream()

        val bytes = buildHelloWorldWasm()

        val exitCode = WarkRunner.builder()
            .bytes(bytes)
            .stdout(output)
            .mode(ExecutionMode.INTERPRET)
            .build()
            .run()

        assertEquals(0, exitCode)
        assertEquals("Hi\n", output.toString())
    }

    @Test
    fun runWithArgs() {
        val output = ByteArrayOutputStream()

        val bytes = buildArgCountProgram()

        val exitCode = WarkRunner.builder()
            .bytes(bytes)
            .stdout(output)
            .args("program", "one", "two")
            .mode(ExecutionMode.INTERPRET)
            .build()
            .run()

        assertEquals(0, exitCode)
    }

    @Test
    fun runWithExitCode() {
        val bytes = buildExitProgram(42)

        val exitCode = WarkRunner.builder()
            .bytes(bytes)
            .mode(ExecutionMode.INTERPRET)
            .build()
            .run()

        assertEquals(42, exitCode)
    }

    private fun buildHelloWorldWasm(): ByteArray = buildWasmBytes {
        importFunction("wasi_snapshot_preview1", "fd_write",
            listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32),
            listOf(WasmValueType.I32))
        memory("mem", 1, exported = true)

        val func = beginFunction("_start", emptyList(), emptyList(), exported = true)

        i32Const(100)
        i32Const(0)
        i32Store(0, 0)
        i32Const(104)
        i32Const(3)
        i32Store(0, 0)

        i32Const(0)
        i32Const(72)
        i32Store8(0, 0)
        i32Const(1)
        i32Const(105)
        i32Store8(0, 0)
        i32Const(2)
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

    private fun buildArgCountProgram(): ByteArray = buildWasmBytes {
        importFunction("wasi_snapshot_preview1", "args_sizes_get",
            listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
        memory("mem", 1, exported = true)

        val func = beginFunction("_start", emptyList(), emptyList(), exported = true)
        i32Const(0)
        i32Const(4)
        call(0)
        drop()
        endFunction()
    }

    private fun buildExitProgram(code: Int): ByteArray = buildWasmBytes {
        importFunction("wasi_snapshot_preview1", "proc_exit",
            listOf(WasmValueType.I32), emptyList())

        val func = beginFunction("_start", emptyList(), emptyList(), exported = true)
        i32Const(code)
        call(0)
        endFunction()
    }
}
