package org.wark.debug

import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.module.WasmModule
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.*
import org.wark.exec.WasmInterpreter
import java.nio.file.Files
import java.nio.file.Path

/**
 * WASM debugger API. Module inspection, interpreter execution with
 * stepping and breakpoints, JIT native code inspection, and
 * differential comparison. No I/O — returns data.
 *
 * ```java
 * var debugger = new WarkDebugger(path, imports);
 * debugger.load();
 * debugger.loadJit();
 * debugger.call("initGame", 1_000_000);
 * var state = debugger.where();
 * ```
 */
class WarkDebugger(
    private val wasmPath: Path,
    private val imports: WarkImports,
) {
    lateinit var wasmModule: WasmModule
        private set
    private lateinit var wasmBytes: ByteArray
    private lateinit var instance: WarkInstance
    private lateinit var interpreter: WasmInterpreter
    private var jitInstance: WarkInstance? = null
    private val disassembler = WasmDisassembler()
    private val breakFunctions = mutableSetOf<String>()
    private var lastCallName: String? = null
    private var lastCallArgs: LongArray? = null

    val importCount get() = wasmModule.importedFunctionCount
    val functionCount get() = wasmModule.functions.size
    val jitAvailable get() = jitInstance != null

    // -- Lifecycle --

    fun load() {
        wasmBytes = Files.readAllBytes(wasmPath)
        wasmModule = WasmModuleReader.read(wasmBytes)

        instance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
            .load(wasmBytes).instantiate(imports)
        instance.setInstructionLimit(Long.MAX_VALUE)
        interpreter = instance.interpreter()
        interpreter.traceEnabled = true

        interpreter.onFunctionEntry = { funcIndex, _ ->
            val name = interpreter.functionName(funcIndex)
            if (name in breakFunctions) {
                throw WasmTrap("breakpoint: $name")
            }
        }
    }

    var boundsChecking = false

    fun loadJit(): Boolean = try {
        val jit = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
            .load(wasmBytes).instantiate(imports)
        if (boundsChecking) {
            jit.enableBoundsChecking()
        }
        jit.compiledIr()
        jitInstance = jit
        true
    } catch (exception: Exception) {
        false
    }

    // -- Module Inspection --

    fun moduleInfo(): ModuleInfo {
        val exportNames = wasmModule.exports
            .filter { it.kind == WasmModule.ExportKind.FUNCTION }
            .map { it.name }
        return ModuleInfo(
            functionCount = wasmModule.functions.size,
            importCount = importCount,
            totalFunctionCount = wasmModule.functions.size + importCount,
            typeCount = wasmModule.types.size,
            globalCount = wasmModule.globals.size,
            memoryPages = instance.memory().pages(),
            memoryBytes = instance.memory().sizeBytes(),
            exports = exportNames,
            totalInstructions = interpreter.totalInstructions,
        )
    }

    fun functionInfo(localIndex: Int): FuncInfo {
        val func = wasmModule.functions[localIndex]
        val funcType = wasmModule.types[func.typeIndex]
        val globalIndex = localIndex + importCount
        val name = functionName(globalIndex)
        val instructions = disassembler.disassemble(func.body)
        return FuncInfo(
            localIndex = localIndex,
            globalIndex = globalIndex,
            name = name,
            params = funcType.params.map { it.name },
            results = funcType.results.map { it.name },
            localCount = func.locals.size,
            bodySize = func.body.size,
            instructionCount = instructions.size,
        )
    }

    fun disassemble(localIndex: Int): List<WasmInstruction> = disassembler.disassemble(wasmModule.functions[localIndex].body)

    fun jitAsm(functionName: String): String? {
        val inspector = jitInstance?.inspector() ?: return null
        return inspector.dumpAsm(resolveFuncName(functionName))
    }

    fun jitIr(functionName: String): String? {
        val inspector = jitInstance?.inspector() ?: return null
        return inspector.dumpIr(resolveFuncName(functionName))
    }

    fun dumpAlloc(functionName: String): String = org.kgen.target.x86.codegen.X86CodeGenerator.dumpAlloc(resolveFuncName(functionName))

    fun exports(): List<WasmModule.Export> = wasmModule.exports.toList()

    fun imports(): List<WasmModule.Import> = wasmModule.imports.toList()

    fun types(): List<WasmModule.FuncType> = wasmModule.types.toList()

    fun elements(): List<WasmModule.Element> = wasmModule.elements.toList()

    // -- Interpreter Execution --

    fun call(name: String, instructionLimit: Long = Long.MAX_VALUE, vararg args: Long): CallResult {
        lastCallName = name
        lastCallArgs = args
        interpreter.instructionLimit = interpreter.totalInstructions + instructionLimit
        return try {
            val result = instance.call(name, *args)
            CallResult(result, completed = true, paused = false, trap = null)
        } catch (trap: WasmTrap) {
            if (trap.message?.contains("instruction limit") == true) {
                CallResult(longArrayOf(), completed = false, paused = true, trap = null)
            } else {
                CallResult(longArrayOf(), completed = false, paused = false, trap = trap.message)
            }
        }
    }

    fun callByIndex(globalIndex: Int, vararg args: Long): CallResult = try {
        val result = interpreter.call(globalIndex, args)
        CallResult(result, completed = true, paused = false, trap = null)
    } catch (trap: WasmTrap) {
        CallResult(longArrayOf(), completed = false, paused = false, trap = trap.message)
    }

    fun continueExecution(additionalInstructions: Long): CallResult {
        val name = lastCallName
            ?: return CallResult(longArrayOf(), false, false, "nothing to continue")
        interpreter.instructionLimit = interpreter.totalInstructions + additionalInstructions
        return try {
            val result = instance.call(name, *(lastCallArgs ?: longArrayOf()))
            CallResult(result, completed = true, paused = false, trap = null)
        } catch (trap: WasmTrap) {
            if (trap.message?.contains("instruction limit") == true) {
                CallResult(longArrayOf(), completed = false, paused = true, trap = null)
            } else {
                CallResult(longArrayOf(), completed = false, paused = false, trap = trap.message)
            }
        }
    }

    fun step(count: Long = 1): CallResult = continueExecution(count)

    fun runTo(functionName: String): CallResult {
        breakFunctions.add(functionName)
        val result = continueExecution(Long.MAX_VALUE)
        breakFunctions.remove(functionName)
        return result
    }

    // -- JIT Execution --

    fun jitCall(globalIndex: Int, vararg args: Long): CallResult {
        val jit = jitInstance
            ?: return CallResult(longArrayOf(), false, false, "JIT not available")
        return try {
            val result = jit.callJitByIndex(globalIndex, *args)
            CallResult(result, completed = true, paused = false, trap = null)
        } catch (exception: Exception) {
            CallResult(longArrayOf(), false, false,
                "${exception.javaClass.simpleName}: ${exception.message?.take(200)}")
        }
    }

    fun compare(globalIndex: Int, vararg args: Long): CompareResult {
        syncMemoryToJit()
        val interpreterResult = callByIndex(globalIndex, *args.clone())
        val jitResult = jitCall(globalIndex, *args.clone())
        val match = interpreterResult.result.toList() == jitResult.result.toList()
        return CompareResult(match, interpreterResult, jitResult)
    }

    fun syncMemoryToJit(): Int {
        val jit = jitInstance ?: return 0
        val interpreterMemory = instance.memory()
        val jitMemory = jit.memory()
        while (jitMemory.pages() < interpreterMemory.pages()) {
            jitMemory.grow(1)
        }
        val bytes = interpreterMemory.readBytes(0, interpreterMemory.sizeBytes())
        jitMemory.writeBytes(0, bytes)
        return interpreterMemory.sizeBytes()
    }

    // -- State Inspection --

    fun where(): ExecutionState = ExecutionState(
        totalInstructions = interpreter.totalInstructions,
        callDepth = interpreter.callDepth,
        functionsCalled = interpreter.tracedCalls().size,
    )

    fun trace(count: Int = 20): List<String> {
        val traced = interpreter.tracedCalls()
        val start = (traced.size - count).coerceAtLeast(0)
        return traced.subList(start, traced.size)
    }

    fun memory(): WarkMemory = instance.memory()

    fun readMemory(address: Int, length: Int): ByteArray {
        val memory = instance.memory()
        val safeLength = length.coerceAtMost(memory.sizeBytes() - address).coerceAtLeast(0)
        return if (safeLength > 0) { memory.readBytes(address, safeLength) } else { ByteArray(0) }
    }

    fun readI32(address: Int): Int = instance.memory().readI32(address)

    fun globals(): List<Pair<Int, Long>> {
        val result = mutableListOf<Pair<Int, Long>>()
        for (index in 0 until wasmModule.globals.size) {
            try {
                result.add(index to instance.global(index).rawValue())
            } catch (ignored: Exception) {
                // skip unreadable
            }
        }
        return result
    }

    // -- Breakpoints --

    fun breakOnFunction(name: String) {
        breakFunctions.add(name)
    }

    fun clearBreakpoints() {
        breakFunctions.clear()
    }

    fun breakpoints(): Set<String> = breakFunctions.toSet()

    // -- Utilities --

    fun resolveFunction(spec: String): Int? {
        val asNumber = spec.toIntOrNull()
        if (asNumber != null && asNumber < wasmModule.functions.size) {
            return asNumber
        }
        val localIndex = spec.removePrefix("func_").toIntOrNull()
        if (localIndex != null && localIndex < wasmModule.functions.size) {
            return localIndex
        }
        return null
    }

    fun resolveFuncName(spec: String): String {
        val localIndex = spec.removePrefix("func_").toIntOrNull() ?: spec.toIntOrNull()
        return if (localIndex != null) { "func_$localIndex" } else { spec }
    }

    fun functionName(globalIndex: Int): String {
        val localIndex = globalIndex - importCount
        return wasmModule.functionName(globalIndex) ?: "func_$localIndex"
    }
}
