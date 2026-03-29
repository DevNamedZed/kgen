package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.wasi.WasiPreview1
import java.nio.file.Files
import java.nio.file.Path

class QuakeJitTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    @Test
    @EnabledIf("quakeWasmExists")
    fun jitCompileOnly() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val host = QuakeHost()
        val setjmpEmulation = SetjmpEmulation()
        val wasi = WasiPreview1.builder()
            .directory(gameDirectory)
            .build()

        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
        val module = runtime.load(wasmBytes)

        val builder = WarkImports.builder()
        host.registerImports(builder)
        wasi.registerImports(builder)
        setjmpEmulation.registerImports(builder)

        val instance = module.instantiate(builder.build())

        println("Triggering JIT compilation via _initialize call...")
        instance.global(0).setI32(instance.memory().sizeBytes() - 16)
        instance.call("_initialize")
        println("_initialize completed in JIT mode")

        wasi.fileTable().closeAll()
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun jitInitializeStepByStep() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val host = QuakeHost()
        val setjmpEmulation = SetjmpEmulation()
        val wasi = WasiPreview1.builder()
            .directory(gameDirectory)
            .build()

        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
        val module = runtime.load(wasmBytes)

        val builder = WarkImports.builder()
        host.registerImports(builder)
        wasi.registerImports(builder)
        setjmpEmulation.registerImports(builder)

        val instance = module.instantiate(builder.build())
        instance.global(0).setI32(instance.memory().sizeBytes() - 16)

        // No tracing — test raw JIT execution

        println("Step 1: _initialize (JIT with tracing)...")
        instance.call("_initialize")
        println("  OK")

        println("Step 2: populate_preopens...")
        val importCount = module.wasmModule.importedFunctionCount
        for ((localIndex, _) in module.wasmModule.functions.withIndex()) {
            val name = module.wasmModule.functionName(localIndex + importCount)
            if (name == "__wasilibc_populate_preopens") {
                instance.callByIndex(localIndex + importCount)
                println("  OK")
                break
            }
        }

        println("Stack pointer after init: ${instance.global(0).rawValue()}")
        println("Memory: ${instance.memory().sizeBytes()} bytes (${instance.memory().pages()} pages)")
        println("Step 3: q_init(32) (JIT with tracing)...")
        val result = instance.call("q_init", 32L)
        println("  OK, result=${result[0]}")
        println("Framebuffer: ${instance.call("q_get_framebuffer_width")[0]}x${instance.call("q_get_framebuffer_height")[0]}")

        wasi.fileTable().closeAll()
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun dumpFindRelpathIr() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val wasmModule = org.kgen.target.wasm.module.WasmModuleReader.read(wasmBytes)
        val target = org.kgen.ir.target.Target.native()
        val compiler = org.wark.compile.WasmToIrCompiler(target, wasmModule)
        val irModule = compiler.compileAll()

        for (function in irModule.functions) {
            if (function.name == "__wasilibc_find_relpath") {
                println("=== IR for __wasilibc_find_relpath ===")
                for (block in function.blocks) {
                    println("${block.label}:")
                    for (inst in block.instructions) {
                        println("  $inst")
                    }
                }
                break
            }
        }
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun checkFindRelpathFunction() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)
        val disasm = org.kgen.target.wasm.disasm.WasmDisassembler()

        val importCount = module.wasmModule.importedFunctionCount
        for ((localIndex, function) in module.wasmModule.functions.withIndex()) {
            val name = module.wasmModule.functionName(localIndex + importCount) ?: continue
            if (name == "__wasilibc_find_relpath") {
                val instructions = disasm.disassemble(function.body)
                val tryCount = instructions.count { it.opcode == org.kgen.target.wasm.WasmOpCode.TRY }
                val catchCount = instructions.count { it.opcode == org.kgen.target.wasm.WasmOpCode.CATCH }
                println("__wasilibc_find_relpath: ${instructions.size} instructions, $tryCount TRY, $catchCount CATCH")
                println("Params: ${module.wasmModule.types[function.typeIndex].params}")
                println("Results: ${module.wasmModule.types[function.typeIndex].results}")
                println("Locals: ${function.locals}")
                println("First 20 opcodes: ${instructions.take(20).map { it.opcode }}")
                break
            }
        }
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun jitInitializeWithInterpretFallback() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val host = QuakeHost()
        val setjmpEmulation = SetjmpEmulation()
        val wasi = WasiPreview1.builder()
            .directory(gameDirectory)
            .build()

        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)

        val builder = WarkImports.builder()
        host.registerImports(builder)
        wasi.registerImports(builder)
        setjmpEmulation.registerImports(builder)

        val instance = module.instantiate(builder.build())
        instance.global(0).setI32(instance.memory().sizeBytes() - 16)

        println("Running full init in INTERPRET mode as baseline...")
        instance.call("_initialize")

        val importCount = module.wasmModule.importedFunctionCount
        for ((localIndex, _) in module.wasmModule.functions.withIndex()) {
            val name = module.wasmModule.functionName(localIndex + importCount)
            if (name == "__wasilibc_populate_preopens") {
                instance.callByIndex(localIndex + importCount)
                break
            }
        }

        val result = instance.call("q_init", 32L)
        println("Interpret init result: ${result[0]}")
        println("Framebuffer: ${instance.call("q_get_framebuffer_width")[0]}x${instance.call("q_get_framebuffer_height")[0]}")

        wasi.fileTable().closeAll()
    }
}
