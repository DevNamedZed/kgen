package org.wark

import org.kgen.target.wasm.module.WasmModuleReader
import org.kgen.ir.target.Target
import org.wark.compile.WasmToIrCompiler
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val wasmBytes = Files.readAllBytes(Path.of("examples/assets/doom.wasm"))
    val wasmModule = WasmModuleReader.read(wasmBytes)

    // Compile just func_2 (FixedMul)
    val compiler = WasmToIrCompiler(Target.native(), wasmModule)
    val irModule = compiler.compileFunction(2, "FixedMul")

    println("IR for FixedMul:")
    for (func in irModule.functions) {
        for (block in func.blocks) {
            println("${block.label}:")
            for (inst in block.instructions) {
                println("  $inst")
            }
        }
    }
    println()

    // Now test via full compilation + JIT
    val fullCompiler = WasmToIrCompiler(Target.native(), wasmModule)
    var fullIr = fullCompiler.compileAll()
    fullIr = org.kgen.pipeline.Mem2Reg().run(fullIr)

    val runtimeEngine = org.kgen.runtime.RuntimeEngine.create()

    // Register minimal stubs
    val linker = java.lang.foreign.Linker.nativeLinker()
    val arena = java.lang.foreign.Arena.ofShared()
    val lookup = java.lang.invoke.MethodHandles.lookup()
    val trapHandle = lookup.findStatic(FixedMulJitTestHelper::class.java, "onTrap",
        java.lang.invoke.MethodType.methodType(Void.TYPE, Int::class.java))
    val trapStub = linker.upcallStub(trapHandle,
        java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.JAVA_INT), arena)
    runtimeEngine.addSymbol("__wark_trap", trapStub.address())

    runtimeEngine.addModule(fullIr)

    // Allocate a dummy context (just needs memory_base and memory_size)
    val contextSegment = arena.allocate(64)
    val dummyMemory = arena.allocate(65536)
    contextSegment.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, dummyMemory.address())
    contextSegment.set(java.lang.foreign.ValueLayout.JAVA_LONG, 8, 65536L)
    val contextAddr = contextSegment.address()

    // Test FixedMul: (context, a, b) -> result
    // FixedMul(a, b) = (a * b) >>> 16
    val tests = listOf(
        Triple(0x10000, 0x10000, 0x10000),  // 1.0 * 1.0 = 1.0
        Triple(0x20000, 0x8000, 0x10000),   // 2.0 * 0.5 = 1.0
        Triple(0x10000, 0x20000, 0x20000),  // 1.0 * 2.0 = 2.0
        Triple(100, 200, 0),                // small values
        Triple(-0x10000, 0x10000, -0x10000), // -1.0 * 1.0 = -1.0 (via unsigned shift!)
        Triple(0x30000, 0x15555, 0x3FFFF),  // 3.0 * ~1.333 ≈ 4.0
    )

    println("FixedMul JIT tests:")
    for ((a, b, expected) in tests) {
        val actual = runtimeEngine.call("func_2", contextAddr, a.toLong(), b.toLong())
        val actualI32 = actual.toInt()
        // Compute expected: unsigned shift
        val expectedCalc = ((a.toLong() * b.toLong()) ushr 16).toInt()
        val status = if (actualI32 == expectedCalc) "OK" else "FAIL (expected=$expectedCalc)"
        println("  FixedMul(0x${a.toString(16)}, 0x${b.toString(16)}) = 0x${actualI32.toString(16)} ($actualI32) $status")
    }
}

object FixedMulJitTestHelper {
    @JvmStatic
    fun onTrap(funcIndex: Int) {
        println("TRAP in func_$funcIndex")
    }
}
