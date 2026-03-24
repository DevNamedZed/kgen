package org.wark.compile

import org.junit.jupiter.api.Test
import org.kgen.ir.target.Target
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import org.kgen.target.wasm.module.WasmModule
import org.kgen.target.wasm.module.WasmModuleReader
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WasmToIrCompilerTest {

    private fun buildWasmModule(block: WasmAssembler.() -> Unit): WasmModule {
        val assembler = WasmAssembler.create()
        assembler.block()
        val bytes = assembler.assemble()
        return WasmModuleReader.read(bytes)
    }

    @Test
    fun compileAddFunction() {
        val wasmModule = buildWasmModule {
            function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Add()
            }
        }

        val compiler = WasmToIrCompiler(Target.x86_64(), wasmModule)
        val irModule = compiler.compileFunction(0, "add")

        assertNotNull(irModule)
        val function = irModule.functions.firstOrNull { it.name == "add" }
        assertNotNull(function)
        assertEquals(3, function.params.size)
        assertTrue(function.blocks.isNotEmpty())
    }

    @Test
    fun compileConstantFunction() {
        val wasmModule = buildWasmModule {
            function("answer", emptyList(), listOf(WasmValueType.I32)) { func, asm ->
                asm.i32Const(42)
            }
        }

        val compiler = WasmToIrCompiler(Target.x86_64(), wasmModule)
        val irModule = compiler.compileFunction(0, "answer")

        val function = irModule.functions.first { it.name == "answer" }
        assertNotNull(function)
        assertTrue(function.blocks.isNotEmpty())
    }

    @Test
    fun compileI64Arithmetic() {
        val wasmModule = buildWasmModule {
            function("mul64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i64Mul()
            }
        }

        val compiler = WasmToIrCompiler(Target.x86_64(), wasmModule)
        val irModule = compiler.compileFunction(0, "mul64")

        val function = irModule.functions.first { it.name == "mul64" }
        assertNotNull(function)
    }

    @Test
    fun compileAllFunctions() {
        val wasmModule = buildWasmModule {
            function("first", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Const(1)
                asm.i32Add()
            }
            function("second", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Const(2)
                asm.i32Mul()
            }
        }

        val compiler = WasmToIrCompiler(Target.x86_64(), wasmModule)
        val irModule = compiler.compileAll()

        assertEquals(2, irModule.functions.size)
        val names = irModule.functions.map { it.name }.toSet()
        assertTrue(names.size == 2, "Expected 2 unique function names, got: $names")
    }

    @Test
    fun compileMemoryAccess() {
        val wasmModule = buildWasmModule {
            memory("mem", 1)
            function("load", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Load(0, 0)
            }
        }

        val compiler = WasmToIrCompiler(Target.x86_64(), wasmModule)
        val irModule = compiler.compileFunction(0, "load")

        val function = irModule.functions.first { it.name == "load" }
        assertNotNull(function)
    }

    @Test
    fun compileComparison() {
        val wasmModule = buildWasmModule {
            function("isPositive", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Const(0)
                asm.i32GtS()
            }
        }

        val compiler = WasmToIrCompiler(Target.x86_64(), wasmModule)
        val irModule = compiler.compileFunction(0, "isPositive")

        val function = irModule.functions.first { it.name == "isPositive" }
        assertNotNull(function)
    }

    @Test
    fun compileVoidFunction() {
        val wasmModule = buildWasmModule {
            memory("mem", 1)
            function("store", listOf(WasmValueType.I32, WasmValueType.I32), emptyList()) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Store(0, 0)
            }
        }

        val compiler = WasmToIrCompiler(Target.x86_64(), wasmModule)
        val irModule = compiler.compileFunction(0, "store")

        val function = irModule.functions.first { it.name == "store" }
        assertNotNull(function)
    }
}
