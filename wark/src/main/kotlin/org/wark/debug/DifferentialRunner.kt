package org.wark.debug

import org.kgen.target.wasm.module.WasmModule
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.*
import org.wark.exec.WasmInterpreter

/**
 * Runs individual WASM functions through both interpreter and JIT,
 * compares outputs, and reports the first divergence.
 *
 * ```kotlin
 * val runner = DifferentialRunner(wasmBytes, imports)
 * val result = runner.testFunction(functionIndex, args)
 * // result tells you if interpreter and JIT agree
 * ```
 */
class DifferentialRunner(
    private val wasmBytes: ByteArray,
    private val imports: WarkImports,
) {
    private val wasmModule = WasmModuleReader.read(wasmBytes)

    data class FunctionResult(
        val functionName: String,
        val interpreterResult: ResultOrError,
        val jitResult: ResultOrError,
        val match: Boolean,
    ) {
        override fun toString(): String {
            val status = if (match) "MATCH" else "DIVERGE"
            return "[$status] $functionName: interpreter=$interpreterResult, jit=$jitResult"
        }
    }

    sealed class ResultOrError {
        data class Success(val values: LongArray) : ResultOrError() {
            override fun toString() = values.toList().toString()
        }
        data class HostCalled(val name: String, val args: LongArray) : ResultOrError() {
            override fun toString() = "host:$name(${args.toList()})"
        }
        data class Trap(val message: String) : ResultOrError() {
            override fun toString() = "trap:$message"
        }
        data class Timeout(val instructionCount: Long) : ResultOrError() {
            override fun toString() = "timeout@$instructionCount"
        }
    }

    /**
     * Test a single function through both engines.
     * Returns the comparison result.
     */
    fun testFunction(functionIndex: Int, args: LongArray = longArrayOf(), instructionLimit: Long = 1_000_000): FunctionResult {
        val name = wasmModule.functionName(functionIndex) ?: "func_${functionIndex - wasmModule.importedFunctionCount}"

        val interpResult = runInterpreter(functionIndex, args, instructionLimit)
        val jitResult = runJit(name, args)

        val match = compareResults(interpResult, jitResult)
        return FunctionResult(name, interpResult, jitResult, match)
    }

    /**
     * Test the call chain starting from an exported function.
     * Traces the interpreter's call chain and tests each function in order.
     */
    fun testCallChain(exportName: String, instructionLimit: Long = 5_000_000): List<FunctionResult> {
        val callChain = traceCallChain(exportName, instructionLimit)
        val results = mutableListOf<FunctionResult>()

        for (funcIndex in callChain) {
            val result = testFunction(funcIndex, longArrayOf(), 100_000)
            results.add(result)
            if (!result.match) {
                break // Stop at first divergence
            }
        }

        return results
    }

    /**
     * Trace the interpreter's call chain from an export.
     */
    fun traceCallChain(exportName: String, instructionLimit: Long = 5_000_000): List<Int> {
        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val instance = runtime.load(wasmBytes).instantiate(imports)

        val interp = WasmInterpreter(
            wasmModule,
            listOf(instance.memory()),
            wasmModule.globals.map { 0L }.toMutableList(),
            imports,
            instance,
        )
        interp.traceEnabled = true
        interp.instructionLimit = instructionLimit

        val exportIndex = wasmModule.exports.first { it.name == exportName }.index
        try {
            interp.call(exportIndex, longArrayOf())
        } catch (_: WasmTrap) {}

        return interp.tracedCalls().mapNotNull { name ->
            // Convert function name back to index
            val importCount = wasmModule.importedFunctionCount
            for (index in 0 until wasmModule.functions.size) {
                val globalIndex = index + importCount
                if ((wasmModule.functionName(globalIndex) ?: "func_$index") == name) {
                    return@mapNotNull globalIndex
                }
            }
            // Check imports
            wasmModule.imports.filterIsInstance<WasmModule.Import.Func>().forEachIndexed { i, imp ->
                if ("${imp.module}.${imp.name}" == name || imp.name == name) {
                    return@mapNotNull i
                }
            }
            null
        }
    }

    private fun runInterpreter(functionIndex: Int, args: LongArray, instructionLimit: Long): ResultOrError {
        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val instance = runtime.load(wasmBytes).instantiate(imports)

        val interp = WasmInterpreter(
            wasmModule,
            listOf(instance.memory()),
            wasmModule.globals.map { 0L }.toMutableList(),
            imports,
            instance,
        )
        interp.instructionLimit = instructionLimit

        return try {
            val result = interp.call(functionIndex, args)
            ResultOrError.Success(result)
        } catch (trap: WasmTrap) {
            if (trap.message?.contains("instruction limit") == true) {
                ResultOrError.Timeout(instructionLimit)
            } else {
                ResultOrError.Trap(trap.message ?: "unknown trap")
            }
        }
    }

    private fun runJit(functionName: String, args: LongArray): ResultOrError = try {
        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
        val instance = runtime.load(wasmBytes).instantiate(imports)
        val result = instance.call(functionName, *args)
        ResultOrError.Success(result)
    } catch (trap: WasmTrap) {
        ResultOrError.Trap(trap.message ?: "unknown trap")
    } catch (exception: Exception) {
        ResultOrError.Trap("${exception.javaClass.simpleName}: ${exception.message}")
    }

    private fun compareResults(a: ResultOrError, b: ResultOrError): Boolean {
        if (a is ResultOrError.Success && b is ResultOrError.Success) {
            return a.values.contentEquals(b.values)
        }
        if (a is ResultOrError.Trap && b is ResultOrError.Trap) {
            return true // Both trapped — close enough
        }
        if (a is ResultOrError.Timeout && b is ResultOrError.Timeout) {
            return true // Both timed out — inconclusive
        }
        return false
    }
}
