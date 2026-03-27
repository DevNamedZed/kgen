package org.wark

import org.kgen.ir.Module
import org.kgen.ir.target.Target
import org.kgen.pipeline.Mem2Reg
import org.kgen.runtime.RuntimeEngine
import org.kgen.target.wasm.module.WasmModule
import org.wark.compile.WasmToIrCompiler
import org.wark.exec.WasmInterpreter

/**
 * An instantiated WASM module — ready to execute.
 *
 * Created by [WarkModule.instantiate]. Links imports, initializes memory
 * and globals, initializes data segments, and runs the start function.
 *
 * ```java
 * var instance = module.instantiate(imports);
 * long result = instance.call("add", 3L, 4L);
 * var memory = instance.memory();
 * ```
 */
class WarkInstance(
    val module: WarkModule,
    val imports: WarkImports,
) {
    private val memories = mutableListOf<WarkMemory>()
    private val globals = mutableListOf<WarkGlobal>()
    private val exportMap = mutableMapOf<String, WasmModule.Export>()
    private val functionTable = mutableListOf<Int>()
    private var engine: RuntimeEngine? = null
    private var compiledModule: Module? = null
    private var interpreter: WasmInterpreter? = null
    private var traceCallback: ((Int, String) -> Unit)? = null
    private var boundsChecking = false
    private val functionNames = mutableMapOf<Int, String>()
    private val dataSegmentStore = mutableMapOf<Int, ByteArray>()
    private val contextArena = java.lang.foreign.Arena.ofShared()
    private val contextSegment: java.lang.foreign.MemorySegment =
        contextArena.allocate(RuntimeContextLayout.SIZE, 8)
    private val upcallArena = java.lang.foreign.Arena.ofShared()

    init {
        linkImports()
        initializeMemories()
        initializeGlobals()
        initializeDataSegments()
        initializeDataSegmentStore()
        initializeElementSegments()
        buildExportMap()
        updateRuntimeContext()
        runStartFunction()
    }

    private fun updateRuntimeContext() {
        if (memories.isNotEmpty()) {
            val memory = memories[0]
            contextSegment.set(java.lang.foreign.ValueLayout.JAVA_LONG, RuntimeContextLayout.MEMORY_BASE, memory.baseAddress())
            contextSegment.set(java.lang.foreign.ValueLayout.JAVA_LONG, RuntimeContextLayout.MEMORY_SIZE, memory.sizeBytes().toLong())
        }
    }

    fun contextAddress(): Long = contextSegment.address()

    /**
     * Call an exported function by name.
     */
    fun callByIndex(functionIndex: Int, vararg args: Long): LongArray {
        val mode = module.runtime.executionMode
        if (mode == ExecutionMode.INTERPRET || mode == ExecutionMode.TIERED) {
            return callInterpreted(functionIndex, args)
        }
        return callJit(functionIndex, args)
    }

    fun callJitByIndex(functionIndex: Int, vararg args: Long): LongArray = callJit(functionIndex, args)

    fun call(functionName: String, vararg args: Long): LongArray {
        val export = exportMap[functionName]
            ?: throw WasmTrap("undefined export: $functionName")
        if (export.kind != WasmModule.ExportKind.FUNCTION) {
            throw WasmTrap("export '$functionName' is not a function")
        }

        val functionIndex = export.index
        val importedFuncCount = module.wasmModule.importedFunctionCount

        if (functionIndex < importedFuncCount) {
            val importDecl = module.wasmModule.imports
                .filterIsInstance<WasmModule.Import.Func>()[functionIndex]
            val hostFunc = imports.resolveFunction(importDecl.module, importDecl.name)
                ?: throw WasmTrap("unresolved import: ${importDecl.module}.${importDecl.name}")
            return hostFunc.call(this, args)
        }

        val mode = module.runtime.executionMode
        if (mode == ExecutionMode.INTERPRET || mode == ExecutionMode.TIERED) {
            return callInterpreted(functionIndex, args)
        }
        return callJit(functionIndex, args)
    }

    /**
     * Get a JIT inspector for examining compiled code.
     * Only available after JIT compilation (call a function first or call ensureCompiled).
     */
    fun inspector(): org.kgen.jit.JitInspector? {
        ensureCompiled()
        return engine?.inspector()
    }

    /**
     * Get the IR module (after WASM→IR compilation + Mem2Reg).
     */
    fun compiledIr(): org.kgen.ir.Module? {
        ensureCompiled()
        return compiledModule
    }

    /**
     * Enable function call tracing for JIT execution.
     * The callback receives (localFunctionIndex, functionName) on each function entry.
     * Must be called BEFORE the first JIT call.
     */
    fun enableTracing(callback: (Int, String) -> Unit) {
        this.traceCallback = callback
        val importCount = module.wasmModule.importedFunctionCount
        for ((index, func) in module.wasmModule.functions.withIndex()) {
            val globalIndex = index + importCount
            functionNames[index] = module.wasmModule.functionName(globalIndex) ?: "func_$index"
        }
    }

    fun enableBoundsChecking() {
        this.boundsChecking = true
    }

    fun setInstructionLimit(limit: Long) {
        val interp = interpreter ?: run {
            val globalValues = globals.map { it.rawValue() }.toMutableList()
            val newInterp = WasmInterpreter(module.wasmModule, memories, globalValues, imports, this)
            interpreter = newInterp
            newInterp
        }
        interp.instructionLimit = limit
    }

    fun interpreter(): WasmInterpreter = interpreter ?: run {
        val globalValues = globals.map { it.rawValue() }.toMutableList()
        val newInterp = WasmInterpreter(module.wasmModule, memories, globalValues, imports, this)
        interpreter = newInterp
        newInterp
    }

    fun memory(): WarkMemory {
        if (memories.isEmpty()) {
            throw WasmTrap("module has no memory")
        }
        return memories[0]
    }

    fun memory(index: Int): WarkMemory {
        if (index >= memories.size) {
            throw WasmTrap("memory index out of bounds: $index")
        }
        return memories[index]
    }

    fun global(index: Int): WarkGlobal {
        if (index >= globals.size) {
            throw WasmTrap("global index out of bounds: $index")
        }
        return globals[index]
    }

    fun global(name: String): WarkGlobal {
        val export = exportMap[name]
            ?: throw WasmTrap("undefined export: $name")
        if (export.kind != WasmModule.ExportKind.GLOBAL) {
            throw WasmTrap("export '$name' is not a global")
        }
        return globals[export.index]
    }

    fun exportedFunctions(): List<String> = exportMap.entries
        .filter { it.value.kind == WasmModule.ExportKind.FUNCTION }
        .map { it.key }

    private fun linkImports() {
        for (import in module.wasmModule.imports) {
            when (import) {
                is WasmModule.Import.Memory -> {
                    val memory = imports.resolveMemory(import.module, import.name)
                        ?: WarkMemory.create(import.min, import.max ?: 65536)
                    memories.add(memory)
                }
                is WasmModule.Import.Global -> {
                    val global = imports.resolveGlobal(import.module, import.name)
                        ?: WarkGlobal.immutableI64(0)
                    globals.add(global)
                }
                is WasmModule.Import.Func -> { }
                is WasmModule.Import.Table -> { }
            }
        }
    }

    private fun initializeMemories() {
        for (memoryDef in module.wasmModule.memories) {
            memories.add(WarkMemory.create(memoryDef.min, memoryDef.max ?: 65536))
        }
    }

    private fun initializeGlobals() {
        for (globalDef in module.wasmModule.globals) {
            val initial = evaluateInitExpr(globalDef.initExpr)
            globals.add(WarkGlobal(globalDef.mutable, initial))
        }
    }

    private fun initializeDataSegments() {
        for (segment in module.wasmModule.dataSegments) {
            if (segment is WasmModule.DataSegment.Active) {
                val memoryIndex = segment.memoryIndex
                if (memoryIndex < memories.size) {
                    val offset = evaluateInitExpr(segment.offsetExpr).toInt()
                    val memory = memories[memoryIndex]
                    if (offset >= 0 && offset + segment.data.size <= memory.sizeBytes()) {
                        memory.writeBytes(offset, segment.data)
                    }
                }
            }
        }
    }

    private fun initializeElementSegments() {
        val wasmModule = module.wasmModule
        for (table in wasmModule.tables) {
            for (index in 0 until table.min) {
                functionTable.add(-1)
            }
        }
        if (functionTable.isEmpty()) {
            for (index in 0 until 1024) {
                functionTable.add(-1)
            }
        }
        for (element in wasmModule.elements) {
            if (element is WasmModule.Element.Active) {
                val offset = evaluateInitExpr(element.offsetExpr).toInt()
                for ((slotIndex, funcIndex) in element.funcIndices.withIndex()) {
                    val tableSlot = offset + slotIndex
                    while (functionTable.size <= tableSlot) {
                        functionTable.add(-1)
                    }
                    functionTable[tableSlot] = funcIndex
                }
            }
        }
    }

    private fun buildExportMap() {
        for (export in module.wasmModule.exports) {
            exportMap[export.name] = export
        }
    }

    private fun runStartFunction() {
        val startIndex = module.wasmModule.start ?: return
        // Use interpreter for the start function since JIT isn't ready during construction
        val globalValues = globals.map { it.rawValue() }.toMutableList()
        val interp = org.wark.exec.WasmInterpreter(module.wasmModule, memories, globalValues, imports, this)
        interp.call(startIndex, longArrayOf())
        // Write back globals that the start function may have modified
        for ((index, value) in globalValues.withIndex()) {
            if (index < globals.size && globals[index].mutable) {
                globals[index].setI64(value)
            }
        }
    }

    private fun callJit(functionIndex: Int, args: LongArray): LongArray {
        ensureCompiled()
        val importedFuncCount = module.wasmModule.importedFunctionCount
        val localIndex = functionIndex - importedFuncCount
        val name = module.wasmModule.functionName(functionIndex) ?: "func_$localIndex"

        val runtimeEngine = engine ?: throw WasmTrap("runtime engine not initialized")
        updateRuntimeContext()

        val funcType = module.wasmModule.types[module.wasmModule.functions[localIndex].typeIndex]
        val jitEngine = runtimeEngine.jit()

        val jl = java.lang.foreign.ValueLayout.JAVA_LONG
        val jd = java.lang.foreign.ValueLayout.JAVA_DOUBLE
        val jf = java.lang.foreign.ValueLayout.JAVA_FLOAT

        // Build FFM descriptor matching the actual WASM param/return types
        // IR params: (context: i64, p0, p1, ...) — context is always i64
        val paramLayouts = mutableListOf<java.lang.foreign.MemoryLayout>(jl) // context
        for (wasmParam in funcType.params) {
            paramLayouts.add(when (wasmParam) {
                org.kgen.target.wasm.WasmValueType.F32 -> jf
                org.kgen.target.wasm.WasmValueType.F64 -> jd
                else -> jl
            })
        }

        val returnLayout = if (funcType.results.isEmpty()) {
            null
        } else {
            when (funcType.results[0]) {
                org.kgen.target.wasm.WasmValueType.F32 -> jf
                org.kgen.target.wasm.WasmValueType.F64 -> jd
                else -> jl
            }
        }

        val descriptor = if (returnLayout != null) {
            java.lang.foreign.FunctionDescriptor.of(returnLayout, *paramLayouts.toTypedArray())
        } else {
            java.lang.foreign.FunctionDescriptor.ofVoid(*paramLayouts.toTypedArray())
        }

        val handle = jitEngine.handle(name, descriptor)

        // Build typed args: context (Long) + WASM params (typed)
        val typedArgs = mutableListOf<Any>()
        typedArgs.add(contextAddress())
        for ((index, wasmParam) in funcType.params.withIndex()) {
            val rawBits = args[index]
            typedArgs.add(when (wasmParam) {
                org.kgen.target.wasm.WasmValueType.F32 -> java.lang.Float.intBitsToFloat(rawBits.toInt())
                org.kgen.target.wasm.WasmValueType.F64 -> java.lang.Double.longBitsToDouble(rawBits)
                else -> rawBits
            })
        }

        val result = handle.invokeWithArguments(typedArgs)
        checkPendingTrap()

        // Convert return value back to Long
        return when {
            funcType.results.isEmpty() -> longArrayOf(0)
            funcType.results[0] == org.kgen.target.wasm.WasmValueType.F32 -> longArrayOf(java.lang.Float.floatToRawIntBits(result as Float).toLong())
            funcType.results[0] == org.kgen.target.wasm.WasmValueType.F64 -> longArrayOf(java.lang.Double.doubleToRawLongBits(result as Double))
            else -> longArrayOf(result as Long)
        }
    }

    private fun callInterpreted(functionIndex: Int, args: LongArray): LongArray {
        val interp = interpreter ?: run {
            val globalValues = globals.map { it.rawValue() }.toMutableList()
            val newInterp = WasmInterpreter(module.wasmModule, memories, globalValues, imports, this)
            interpreter = newInterp
            newInterp
        }
        return interp.call(functionIndex, args)
    }

    private fun ensureCompiled() {
        if (compiledModule != null) {
            return
        }
        val target = Target.native()
        val compiler = WasmToIrCompiler(target, module.wasmModule)
        if (traceCallback != null) {
            compiler.traceEnabled = true
            System.err.println("[DIAG] traceEnabled=true for WASM->IR compilation")
        }
        if (boundsChecking) {
            compiler.boundsCheckEnabled = true
        }
        var irModule = compiler.compileAll()
        irModule = Mem2Reg().run(irModule)
        compiledModule = irModule

        // Count trace_return calls in the IR
        var traceReturnCount = 0
        for (function in irModule.functions) {
            for (block in function.blocks) {
                for (instruction in block.instructions) {
                    if (instruction is org.kgen.ir.instructions.Call) {
                        if (instruction.function.name == "__wark_trace_return") {
                            traceReturnCount++
                        }
                    }
                }
            }
        }
        System.err.println("[DIAG] IR contains $traceReturnCount __wark_trace_return calls")

        val runtimeEngine = RuntimeEngine.create()
        registerHostImportsInJit(runtimeEngine)
        registerTrapStub(runtimeEngine)
        registerMemoryStubs(runtimeEngine)
        registerBulkMemoryStubs(runtimeEngine)
        registerArithmeticStubs(runtimeEngine)
        if (boundsChecking) {
            registerOobTrapStub(runtimeEngine)
        }
        if (traceCallback != null) {
            registerTraceStub(runtimeEngine)
            System.err.println("[DIAG] trace stubs registered (enter + arg + return)")
        }
        runtimeEngine.addModule(irModule)
        engine = runtimeEngine

        // Log unresolved symbols
        val unresolvedSymbols = findUnresolvedSymbols(irModule, runtimeEngine)
        if (unresolvedSymbols.isNotEmpty()) {
            val traceFile = java.io.File("build/doom-trace.log")
            traceFile.appendText("UNRESOLVED SYMBOLS (${unresolvedSymbols.size}):\n")
            for (symbol in unresolvedSymbols.take(20)) {
                traceFile.appendText("  $symbol\n")
            }
        }
    }

    private fun findUnresolvedSymbols(irModule: Module, runtimeEngine: RuntimeEngine): List<String> {
        val definedFunctions = irModule.functions.map { it.name }.toHashSet()
        val externalCalls = mutableSetOf<String>()
        for (function in irModule.functions) {
            for (block in function.blocks) {
                for (instruction in block.instructions) {
                    if (instruction is org.kgen.ir.instructions.Call) {
                        val targetName = instruction.function.name
                        if (targetName !in definedFunctions) {
                            externalCalls.add(targetName)
                        }
                    }
                }
            }
        }
        val jitEngine = runtimeEngine.jit()
        return externalCalls.filter { name ->
            jitEngine.lookup(name) == null
        }
    }

    private fun registerBoundsCheck(runtimeEngine: RuntimeEngine) {
        val linker = java.lang.foreign.Linker.nativeLinker()
        val lookup = java.lang.invoke.MethodHandles.lookup()
        val handle = lookup.bind(this, "boundsCheck",
            java.lang.invoke.MethodType.methodType(Void.TYPE, Long::class.java, Long::class.java, Int::class.java))
        val descriptor = java.lang.foreign.FunctionDescriptor.ofVoid(
            java.lang.foreign.ValueLayout.JAVA_LONG,
            java.lang.foreign.ValueLayout.JAVA_LONG,
            java.lang.foreign.ValueLayout.JAVA_INT,
        )
        val stub = linker.upcallStub(handle, descriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_bounds_check", stub.address())
    }

    private var accessLog: java.io.PrintWriter? = null
    private var accessCount = 0

    fun enableAccessLogging(logFile: java.io.File) {
        this.accessLog = java.io.PrintWriter(java.io.BufferedWriter(java.io.FileWriter(logFile)), true)
    }

    fun boundsCheck(contextPointer: Long, wasmAddress: Long, accessSize: Int) {
        val log = accessLog
        if (log != null && accessCount < 2000) {
            accessCount++
            log.println("[$accessCount] 0x${java.lang.Long.toHexString(wasmAddress)} sz=$accessSize")
        }
        val memSize = if (memories.isNotEmpty()) { memories[0].sizeBytes() } else { 0 }
        val endAddress = wasmAddress + accessSize
        if (endAddress > memSize || wasmAddress < 0) {
            log?.println("OOB! addr=0x${java.lang.Long.toHexString(wasmAddress)} sz=$accessSize memSz=$memSize")
            log?.flush()
            System.err.println("WASM OOB: addr=0x${java.lang.Long.toHexString(wasmAddress)}, sz=$accessSize, memSz=$memSize")
            System.err.flush()
            throw org.wark.WasmTrap("OOB: addr=0x${java.lang.Long.toHexString(wasmAddress)}, sz=$accessSize, memSz=$memSize")
        }
    }

    private fun registerOobTrapStub(runtimeEngine: RuntimeEngine) {
        val linker = java.lang.foreign.Linker.nativeLinker()
        val lookup = java.lang.invoke.MethodHandles.lookup()
        val handle = lookup.findStatic(
            WarkInstance::class.java, "onOobTrap",
            java.lang.invoke.MethodType.methodType(Void.TYPE, Long::class.java, Long::class.java)
        )
        val descriptor = java.lang.foreign.FunctionDescriptor.ofVoid(
            java.lang.foreign.ValueLayout.JAVA_LONG,
            java.lang.foreign.ValueLayout.JAVA_LONG,
        )
        val stub = linker.upcallStub(handle, descriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_oob_trap", stub.address())
    }

    private fun registerMemoryStubs(runtimeEngine: RuntimeEngine) {
        val linker = java.lang.foreign.Linker.nativeLinker()
        val lookup = java.lang.invoke.MethodHandles.lookup()
        val jl = java.lang.foreign.ValueLayout.JAVA_LONG
        val ji = java.lang.foreign.ValueLayout.JAVA_INT

        // Block trace stub for func_123 debugging
        val blockHandle = lookup.findStatic(WarkInstance::class.java, "traceBlock",
            java.lang.invoke.MethodType.methodType(Void.TYPE, Int::class.java))
        val blockDescriptor = java.lang.foreign.FunctionDescriptor.ofVoid(ji)
        val blockStub = linker.upcallStub(blockHandle, blockDescriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_trace_block", blockStub.address())

        val growHandle = lookup.bind(this, "memoryGrow",
            java.lang.invoke.MethodType.methodType(Int::class.java, Long::class.java, Int::class.java))
        val growDescriptor = java.lang.foreign.FunctionDescriptor.of(ji, jl, ji)
        val growStub = linker.upcallStub(growHandle, growDescriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_memory_grow", growStub.address())

        val sizeHandle = lookup.bind(this, "memorySize",
            java.lang.invoke.MethodType.methodType(Int::class.java, Long::class.java))
        val sizeDescriptor = java.lang.foreign.FunctionDescriptor.of(ji, jl)
        val sizeStub = linker.upcallStub(sizeHandle, sizeDescriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_memory_size", sizeStub.address())
    }

    fun memoryGrow(contextPointer: Long, deltaPages: Int): Int {
        val traceFile = java.io.File("build/doom-trace.log")
        traceFile.appendText("GROW: delta=$deltaPages, current=${memories[0].pages()}\n")
        val result = memories[0].grow(deltaPages)
        if (result >= 0) {
            updateRuntimeContext()
            traceFile.appendText("GROW OK: ${memories[0].pages()} pages, base=0x${java.lang.Long.toHexString(memories[0].baseAddress())}, size=${memories[0].sizeBytes()}\n")
        } else {
            traceFile.appendText("GROW FAILED\n")
        }
        return result
    }

    fun memorySize(contextPointer: Long): Int = memories[0].pages()

    fun memoryCopy(contextPointer: Long, destination: Int, source: Int, length: Int) {
        if (memories.isNotEmpty()) {
            memories[0].copy(destination, source, length)
        }
    }

    private fun initializeDataSegmentStore() {
        for ((index, segment) in module.wasmModule.dataSegments.withIndex()) {
            dataSegmentStore[index] = segment.data
        }
    }

    fun memoryInit(contextPointer: Long, segmentIndex: Int, destination: Int, source: Int, length: Int) {
        val segmentData = dataSegmentStore[segmentIndex] ?: return
        if (length > 0 && memories.isNotEmpty()) {
            val slice = segmentData.copyOfRange(source, source + length)
            memories[0].writeBytes(destination, slice)
        }
    }

    fun dataDrop(contextPointer: Long, segmentIndex: Int) {
        dataSegmentStore.remove(segmentIndex)
    }

    fun memoryFill(contextPointer: Long, destination: Int, value: Int, length: Int) {
        if (memories.isNotEmpty()) {
            memories[0].fill(destination, value.toByte(), length)
        }
    }

    private fun registerBulkMemoryStubs(runtimeEngine: RuntimeEngine) {
        val linker = java.lang.foreign.Linker.nativeLinker()
        val lookup = java.lang.invoke.MethodHandles.lookup()
        val jl = java.lang.foreign.ValueLayout.JAVA_LONG
        val ji = java.lang.foreign.ValueLayout.JAVA_INT

        val copyHandle = lookup.bind(this, "memoryCopy",
            java.lang.invoke.MethodType.methodType(Void.TYPE, Long::class.java, Int::class.java, Int::class.java, Int::class.java))
        val copyDescriptor = java.lang.foreign.FunctionDescriptor.ofVoid(jl, ji, ji, ji)
        val copyStub = linker.upcallStub(copyHandle, copyDescriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_memory_copy", copyStub.address())

        val fillHandle = lookup.bind(this, "memoryFill",
            java.lang.invoke.MethodType.methodType(Void.TYPE, Long::class.java, Int::class.java, Int::class.java, Int::class.java))
        val fillDescriptor = java.lang.foreign.FunctionDescriptor.ofVoid(jl, ji, ji, ji)
        val fillStub = linker.upcallStub(fillHandle, fillDescriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_memory_fill", fillStub.address())

        val initHandle = lookup.bind(this, "memoryInit",
            java.lang.invoke.MethodType.methodType(Void.TYPE, Long::class.java, Int::class.java, Int::class.java, Int::class.java, Int::class.java))
        val initDescriptor = java.lang.foreign.FunctionDescriptor.ofVoid(jl, ji, ji, ji, ji)
        val initStub = linker.upcallStub(initHandle, initDescriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_memory_init", initStub.address())

        val dropHandle = lookup.bind(this, "dataDrop",
            java.lang.invoke.MethodType.methodType(Void.TYPE, Long::class.java, Int::class.java))
        val dropDescriptor = java.lang.foreign.FunctionDescriptor.ofVoid(jl, ji)
        val dropStub = linker.upcallStub(dropHandle, dropDescriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_data_drop", dropStub.address())
    }

    private fun registerArithmeticStubs(runtimeEngine: RuntimeEngine) {
        val linker = java.lang.foreign.Linker.nativeLinker()
        val lookup = java.lang.invoke.MethodHandles.lookup()
        val ji = java.lang.foreign.ValueLayout.JAVA_INT
        val jl = java.lang.foreign.ValueLayout.JAVA_LONG
        val jf = java.lang.foreign.ValueLayout.JAVA_FLOAT
        val jd = java.lang.foreign.ValueLayout.JAVA_DOUBLE

        registerStaticStub(runtimeEngine, linker, lookup, "i32Clz", "__wark_i32_clz", ji, ji)
        registerStaticStub(runtimeEngine, linker, lookup, "i32Ctz", "__wark_i32_ctz", ji, ji)
        registerStaticStub(runtimeEngine, linker, lookup, "i32Popcnt", "__wark_i32_popcnt", ji, ji)
        registerStaticStub(runtimeEngine, linker, lookup, "i64Clz", "__wark_i64_clz", jl, jl)
        registerStaticStub(runtimeEngine, linker, lookup, "i64Ctz", "__wark_i64_ctz", jl, jl)
        registerStaticStub(runtimeEngine, linker, lookup, "i64Popcnt", "__wark_i64_popcnt", jl, jl)

        registerStaticStub(runtimeEngine, linker, lookup, "f32Abs", "__wark_f32_abs", jf, jf)
        registerStaticStub(runtimeEngine, linker, lookup, "f64Abs", "__wark_f64_abs", jd, jd)
        registerStaticStub(runtimeEngine, linker, lookup, "f32Sqrt", "__wark_f32_sqrt", jf, jf)
        registerStaticStub(runtimeEngine, linker, lookup, "f64Sqrt", "__wark_f64_sqrt", jd, jd)
        registerStaticStub(runtimeEngine, linker, lookup, "f32Ceil", "__wark_f32_ceil", jf, jf)
        registerStaticStub(runtimeEngine, linker, lookup, "f64Ceil", "__wark_f64_ceil", jd, jd)
        registerStaticStub(runtimeEngine, linker, lookup, "f32Floor", "__wark_f32_floor", jf, jf)
        registerStaticStub(runtimeEngine, linker, lookup, "f64Floor", "__wark_f64_floor", jd, jd)
        registerStaticStub(runtimeEngine, linker, lookup, "f32Trunc", "__wark_f32_trunc", jf, jf)
        registerStaticStub(runtimeEngine, linker, lookup, "f64Trunc", "__wark_f64_trunc", jd, jd)
        registerStaticStub(runtimeEngine, linker, lookup, "f32Nearest", "__wark_f32_nearest", jf, jf)
        registerStaticStub(runtimeEngine, linker, lookup, "f64Nearest", "__wark_f64_nearest", jd, jd)

        registerStaticStub2(runtimeEngine, linker, lookup, "f32Min", "__wark_f32_min", jf, jf, jf)
        registerStaticStub2(runtimeEngine, linker, lookup, "f64Min", "__wark_f64_min", jd, jd, jd)
        registerStaticStub2(runtimeEngine, linker, lookup, "f32Max", "__wark_f32_max", jf, jf, jf)
        registerStaticStub2(runtimeEngine, linker, lookup, "f64Max", "__wark_f64_max", jd, jd, jd)
        registerStaticStub2(runtimeEngine, linker, lookup, "f32Copysign", "__wark_f32_copysign", jf, jf, jf)
        registerStaticStub2(runtimeEngine, linker, lookup, "f64Copysign", "__wark_f64_copysign", jd, jd, jd)

        registerStaticStub(runtimeEngine, linker, lookup, "i32TruncSatS", "__wark_i32_trunc_sat_s", ji, jd)
        registerStaticStub(runtimeEngine, linker, lookup, "i32TruncSatU", "__wark_i32_trunc_sat_u", ji, jd)
        registerStaticStub(runtimeEngine, linker, lookup, "i64TruncSatS", "__wark_i64_trunc_sat_s", jl, jd)
        registerStaticStub(runtimeEngine, linker, lookup, "i64TruncSatU", "__wark_i64_trunc_sat_u", jl, jd)
    }

    private fun registerStaticStub(
        runtimeEngine: RuntimeEngine,
        linker: java.lang.foreign.Linker,
        lookup: java.lang.invoke.MethodHandles.Lookup,
        methodName: String,
        symbolName: String,
        returnLayout: java.lang.foreign.ValueLayout,
        paramLayout: java.lang.foreign.ValueLayout,
    ) {
        val returnClass = layoutToClass(returnLayout)
        val paramClass = layoutToClass(paramLayout)
        val handle = lookup.findStatic(WarkInstance::class.java, methodName,
            java.lang.invoke.MethodType.methodType(returnClass, paramClass))
        val descriptor = java.lang.foreign.FunctionDescriptor.of(returnLayout, paramLayout)
        val stub = linker.upcallStub(handle, descriptor, upcallArena)
        runtimeEngine.addSymbol(symbolName, stub.address())
    }

    private fun registerStaticStub2(
        runtimeEngine: RuntimeEngine,
        linker: java.lang.foreign.Linker,
        lookup: java.lang.invoke.MethodHandles.Lookup,
        methodName: String,
        symbolName: String,
        returnLayout: java.lang.foreign.ValueLayout,
        param1Layout: java.lang.foreign.ValueLayout,
        param2Layout: java.lang.foreign.ValueLayout,
    ) {
        val returnClass = layoutToClass(returnLayout)
        val param1Class = layoutToClass(param1Layout)
        val param2Class = layoutToClass(param2Layout)
        val handle = lookup.findStatic(WarkInstance::class.java, methodName,
            java.lang.invoke.MethodType.methodType(returnClass, param1Class, param2Class))
        val descriptor = java.lang.foreign.FunctionDescriptor.of(returnLayout, param1Layout, param2Layout)
        val stub = linker.upcallStub(handle, descriptor, upcallArena)
        runtimeEngine.addSymbol(symbolName, stub.address())
    }

    private fun layoutToClass(layout: java.lang.foreign.ValueLayout): Class<*> = when (layout) {
        java.lang.foreign.ValueLayout.JAVA_INT -> Int::class.java
        java.lang.foreign.ValueLayout.JAVA_LONG -> Long::class.java
        java.lang.foreign.ValueLayout.JAVA_FLOAT -> Float::class.java
        java.lang.foreign.ValueLayout.JAVA_DOUBLE -> Double::class.java
        else -> Long::class.java
    }

    private fun registerTrapStub(runtimeEngine: RuntimeEngine) {
        val linker = java.lang.foreign.Linker.nativeLinker()
        val lookup = java.lang.invoke.MethodHandles.lookup()
        val handle = lookup.findStatic(
            WarkInstance::class.java, "onTrap",
            java.lang.invoke.MethodType.methodType(Void.TYPE, Int::class.java)
        )
        val descriptor = java.lang.foreign.FunctionDescriptor.ofVoid(
            java.lang.foreign.ValueLayout.JAVA_INT
        )
        val stub = linker.upcallStub(handle, descriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_trap", stub.address())
    }

    companion object {
        @Volatile @JvmField var boundsCheckCount = 0L

        private val bitopLog = java.io.File("build/bitop-trace.txt").also { it.parentFile?.mkdirs() }
        private val blockLog = java.io.File("build/block-trace.txt").also { it.parentFile?.mkdirs() }

        @Volatile private var ctzCallCount = 0

        @Volatile private var memoryForDiag: WarkMemory? = null

        @JvmStatic
        fun setDiagMemory(memory: WarkMemory) {
            memoryForDiag = memory
        }

        @JvmStatic
        fun traceBlock(blockId: Int) {
            blockLog.appendText("BLOCK $blockId\n")
        }

        @JvmStatic
        fun i32Clz(value: Int): Int {
            val result = Integer.numberOfLeadingZeros(value)
            bitopLog.appendText("clz(0x${value.toUInt().toString(16)}) = $result\n")
            return result
        }

        @JvmStatic
        fun i32Ctz(value: Int): Int {
            ctzCallCount++
            val result = Integer.numberOfTrailingZeros(value)
            bitopLog.appendText("ctz(0x${value.toUInt().toString(16)}) = $result\n")
            if (ctzCallCount <= 3) {
                val mem = memoryForDiag
                if (mem != null) {
                    bitopLog.appendText("  === WASM state at ctz call #$ctzCallCount ===\n")
                    for ((label, addr) in listOf(
                        "treemap" to 0x46112C,
                        "smallmap" to 0x461140,
                        "dvsize" to 0x461180,
                        "topsize" to 0x461188,
                        "top" to 0x4612E8,
                        "magic" to 0x4612DC,
                        "mflags" to 0x4612D8,
                        "seg_base" to 0x4612EC,
                        "seg_size" to 0x4612F0,
                    )) {
                        if (addr + 4 <= mem.sizeBytes()) {
                            val v = mem.readI32(addr)
                            bitopLog.appendText("    $label [0x${addr.toString(16)}] = 0x${v.toUInt().toString(16)} ($v)\n")
                        }
                    }
                }
            }
            return result
        }

        @JvmStatic
        fun i32Popcnt(value: Int): Int = Integer.bitCount(value)

        @JvmStatic
        fun i64Clz(value: Long): Long = java.lang.Long.numberOfLeadingZeros(value).toLong()

        @JvmStatic
        fun i64Ctz(value: Long): Long = java.lang.Long.numberOfTrailingZeros(value).toLong()

        @JvmStatic
        fun i64Popcnt(value: Long): Long = java.lang.Long.bitCount(value).toLong()

        @JvmStatic
        fun f32Abs(value: Float): Float = kotlin.math.abs(value)

        @JvmStatic
        fun f64Abs(value: Double): Double = kotlin.math.abs(value)

        @JvmStatic
        fun f32Sqrt(value: Float): Float = kotlin.math.sqrt(value.toDouble()).toFloat()

        @JvmStatic
        fun f64Sqrt(value: Double): Double = kotlin.math.sqrt(value)

        @JvmStatic
        fun f32Ceil(value: Float): Float = kotlin.math.ceil(value.toDouble()).toFloat()

        @JvmStatic
        fun f64Ceil(value: Double): Double = kotlin.math.ceil(value)

        @JvmStatic
        fun f32Floor(value: Float): Float = kotlin.math.floor(value.toDouble()).toFloat()

        @JvmStatic
        fun f64Floor(value: Double): Double = kotlin.math.floor(value)

        @JvmStatic
        fun f32Trunc(value: Float): Float {
            if (value.isNaN() || value.isInfinite()) { return value }
            return if (value >= 0f) { kotlin.math.floor(value.toDouble()).toFloat() } else { kotlin.math.ceil(value.toDouble()).toFloat() }
        }

        @JvmStatic
        fun f64Trunc(value: Double): Double {
            if (value.isNaN() || value.isInfinite()) { return value }
            return if (value >= 0.0) { kotlin.math.floor(value) } else { kotlin.math.ceil(value) }
        }

        @JvmStatic
        fun f32Nearest(value: Float): Float = Math.rint(value.toDouble()).toFloat()

        @JvmStatic
        fun f64Nearest(value: Double): Double = Math.rint(value)

        @JvmStatic
        fun f32Min(a: Float, b: Float): Float = kotlin.math.min(a, b)

        @JvmStatic
        fun f64Min(a: Double, b: Double): Double = kotlin.math.min(a, b)

        @JvmStatic
        fun f32Max(a: Float, b: Float): Float = kotlin.math.max(a, b)

        @JvmStatic
        fun f64Max(a: Double, b: Double): Double = kotlin.math.max(a, b)

        @JvmStatic
        fun f32Copysign(magnitude: Float, sign: Float): Float = Math.copySign(magnitude, sign)

        @JvmStatic
        fun f64Copysign(magnitude: Double, sign: Double): Double = Math.copySign(magnitude, sign)

        @JvmStatic
        fun i32TruncSatS(value: Double): Int {
            if (value.isNaN()) { return 0 }
            if (value >= Int.MAX_VALUE.toDouble()) { return Int.MAX_VALUE }
            if (value <= Int.MIN_VALUE.toDouble()) { return Int.MIN_VALUE }
            return value.toInt()
        }

        @JvmStatic
        fun i32TruncSatU(value: Double): Int {
            if (value.isNaN()) { return 0 }
            if (value >= 4294967295.0) { return -1 }
            if (value <= 0.0) { return 0 }
            return value.toLong().toInt()
        }

        @JvmStatic
        fun i64TruncSatS(value: Double): Long {
            if (value.isNaN()) { return 0L }
            if (value >= Long.MAX_VALUE.toDouble()) { return Long.MAX_VALUE }
            if (value <= Long.MIN_VALUE.toDouble()) { return Long.MIN_VALUE }
            return value.toLong()
        }

        @JvmStatic
        fun i64TruncSatU(value: Double): Long {
            if (value.isNaN()) { return 0L }
            if (value <= 0.0) { return 0L }
            if (value >= 18446744073709551615.0) { return -1L }
            return value.toLong()
        }

        @JvmStatic
        fun onOobTrap(wasmAddress: Long, memorySize: Long) {
            val traceFile = java.io.File("build/doom-trace.log")
            traceFile.appendText("OOB TRAP: addr=0x${java.lang.Long.toHexString(wasmAddress)}, memSize=$memorySize, after $boundsCheckCount successful checks\n")
            System.err.println("WASM OOB: addr=0x${java.lang.Long.toHexString(wasmAddress)}, memSize=$memorySize, checks=$boundsCheckCount")
            System.err.flush()
            throw org.wark.WasmTrap("OOB trap: addr=0x${java.lang.Long.toHexString(wasmAddress)}")
        }

        @Volatile @JvmField var lastTrapInstance: WarkInstance? = null

        @Volatile @JvmField var pendingTrapFunctionIndex: Int = -1

        @JvmStatic
        fun onTrap(functionIndex: Int) {
            pendingTrapFunctionIndex = functionIndex
        }

        fun checkPendingTrap() {
            val funcIdx = pendingTrapFunctionIndex
            if (funcIdx >= 0) {
                pendingTrapFunctionIndex = -1
                throw WasmTrap("unreachable trap in func_$funcIdx")
            }
        }
    }

    private fun registerTraceStub(runtimeEngine: RuntimeEngine) {
        val callback = traceCallback ?: return
        val bridge = TraceBridge(callback, functionNames, this)
        val linker = java.lang.foreign.Linker.nativeLinker()
        val lookup = java.lang.invoke.MethodHandles.lookup()
        val handle = lookup.bind(bridge, "trace",
            java.lang.invoke.MethodType.methodType(Void.TYPE, Long::class.java, Long::class.java))
        val descriptor = java.lang.foreign.FunctionDescriptor.ofVoid(
            java.lang.foreign.ValueLayout.JAVA_LONG,
            java.lang.foreign.ValueLayout.JAVA_LONG,
        )
        val stub = linker.upcallStub(handle, descriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_trace_enter", stub.address())

        // Register arg tracing stub
        val argBridge = ArgTraceBridge(this, callback)
        val argHandle = lookup.bind(argBridge, "traceArg",
            java.lang.invoke.MethodType.methodType(Void.TYPE, Long::class.java, Long::class.java, Long::class.java))
        val argDescriptor = java.lang.foreign.FunctionDescriptor.ofVoid(
            java.lang.foreign.ValueLayout.JAVA_LONG,
            java.lang.foreign.ValueLayout.JAVA_LONG,
            java.lang.foreign.ValueLayout.JAVA_LONG,
        )
        val argStub = linker.upcallStub(argHandle, argDescriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_trace_arg", argStub.address())

        // Register return tracing stub
        val returnBridge = ReturnTraceBridge(this, callback)
        val returnHandle = lookup.bind(returnBridge, "traceReturn",
            java.lang.invoke.MethodType.methodType(
                Void.TYPE, Long::class.java, Long::class.java, Long::class.java, Long::class.java))
        val returnDescriptor = java.lang.foreign.FunctionDescriptor.ofVoid(
            java.lang.foreign.ValueLayout.JAVA_LONG,
            java.lang.foreign.ValueLayout.JAVA_LONG,
            java.lang.foreign.ValueLayout.JAVA_LONG,
            java.lang.foreign.ValueLayout.JAVA_LONG,
        )
        val returnStub = linker.upcallStub(returnHandle, returnDescriptor, upcallArena)
        runtimeEngine.addSymbol("__wark_trace_return", returnStub.address())
    }

    class TraceBridge(
        private val callback: (Int, String) -> Unit,
        private val names: Map<Int, String>,
        private val instance: WarkInstance,
    ) {
        fun trace(contextPointer: Long, functionId: Long) {
            val id = functionId.toInt()
            val name = names[id] ?: "func_$id"
            callback(id, name)

            // Dump RuntimeContext health for key functions
            if (contextPointer != 0L) {
                try {
                    val ctx = java.lang.foreign.MemorySegment.ofAddress(contextPointer).reinterpret(16)
                    val memoryBase = ctx.get(java.lang.foreign.ValueLayout.JAVA_LONG, 0)
                    val memorySize = ctx.get(java.lang.foreign.ValueLayout.JAVA_LONG, 8)
                    val actualSize = if (instance.memories.isNotEmpty()) { instance.memories[0].sizeBytes().toLong() } else { 0L }
                    val actualBase = if (instance.memories.isNotEmpty()) { instance.memories[0].baseAddress() } else { 0L }

                    if (memoryBase != actualBase || memorySize != actualSize) {
                        callback(id, "  !!! CONTEXT MISMATCH: ctx.base=0x${memoryBase.toULong().toString(16)} actual=0x${actualBase.toULong().toString(16)}, ctx.size=$memorySize actual=$actualSize")
                    }
                } catch (ignored: Throwable) {
                    // safe
                }
            }
        }
    }

    class ArgTraceBridge(
        private val instance: WarkInstance,
        private val callback: (Int, String) -> Unit,
    ) {
        fun traceArg(contextPointer: Long, functionId: Long, argValue: Long) {
            val funcId = functionId.toInt()
            val argI32 = argValue.toInt()
            val memSize = if (instance.memories.isNotEmpty()) { instance.memories[0].sizeBytes() } else { 0 }
            val inBounds = argI32 >= 0 && argI32 < memSize
            callback(funcId, "  ARG func_$funcId: p0=0x${argI32.toUInt().toString(16)} ($argI32), memSize=$memSize, inBounds=$inBounds")
            if (!inBounds && argI32 != 0) {
                callback(funcId, "  *** OUT OF BOUNDS p0 for func_$funcId! ***")
            }
        }
    }

    class ReturnTraceBridge(
        private val instance: WarkInstance,
        private val callback: (Int, String) -> Unit,
    ) {
        fun traceReturn(contextPointer: Long, callerFuncId: Long, calleeFuncId: Long, returnValue: Long) {
            val callerId = callerFuncId.toInt()
            val calleeId = calleeFuncId.toInt()
            val resultI32 = returnValue.toInt()

            val calleeLabel = if (calleeId < 0) { "indirect" } else { "func_$calleeId" }
            callback(calleeId, "  <<< $calleeLabel returned 0x${resultI32.toUInt().toString(16)} ($resultI32) to func_$callerId")

            // (BAD MALLOC check removed — was false positive on debug binary)
        }
    }

    private fun registerHostImportsInJit(runtimeEngine: RuntimeEngine) {
        val linker = java.lang.foreign.Linker.nativeLinker()
        val lookup = java.lang.invoke.MethodHandles.lookup()
        val jl = java.lang.foreign.ValueLayout.JAVA_LONG

        for (importDecl in module.wasmModule.imports) {
            if (importDecl is WasmModule.Import.Func) {
                val hostFunc = imports.resolveFunction(importDecl.module, importDecl.name) ?: continue
                val symbolName = "${importDecl.module}_${importDecl.name}"
                val funcType = module.wasmModule.types[importDecl.typeIndex]
                val totalParams = 1 + funcType.params.size // memoryBase + wasm params
                val hasResult = funcType.results.isNotEmpty()

                val bridge = HostBridge(this, hostFunc, funcType.params.size)

                // Create method handle for the exact number of parameters
                val methodName = "call$totalParams"
                val paramTypes = List(totalParams) { Long::class.java }
                val handle = lookup.bind(bridge, methodName,
                    java.lang.invoke.MethodType.methodType(Long::class.java, paramTypes))

                val paramLayouts = Array(totalParams) { jl as java.lang.foreign.MemoryLayout }
                val descriptor = java.lang.foreign.FunctionDescriptor.of(jl, *paramLayouts)

                val stub = linker.upcallStub(handle, descriptor, upcallArena)
                runtimeEngine.addSymbol(symbolName, stub.address())
            }
        }
    }

    class HostBridge(
        private val instance: WarkInstance,
        private val hostFunction: HostFunction,
        private val wasmParamCount: Int,
    ) {
        private fun dispatch(vararg allArgs: Long): Long {
            val wasmArgs = if (wasmParamCount > 0 && allArgs.size > 1) {
                LongArray(wasmParamCount) { index -> if (index + 1 < allArgs.size) allArgs[index + 1] else 0L }
            } else {
                longArrayOf()
            }
            val results = hostFunction.call(instance, wasmArgs)
            return if (results.isNotEmpty()) results[0] else 0L
        }

        fun call1(a0: Long): Long = dispatch(a0)
        fun call2(a0: Long, a1: Long): Long = dispatch(a0, a1)
        fun call3(a0: Long, a1: Long, a2: Long): Long = dispatch(a0, a1, a2)
        fun call4(a0: Long, a1: Long, a2: Long, a3: Long): Long = dispatch(a0, a1, a2, a3)
        fun call5(a0: Long, a1: Long, a2: Long, a3: Long, a4: Long): Long = dispatch(a0, a1, a2, a3, a4)
        fun call6(a0: Long, a1: Long, a2: Long, a3: Long, a4: Long, a5: Long): Long = dispatch(a0, a1, a2, a3, a4, a5)
        fun call7(a0: Long, a1: Long, a2: Long, a3: Long, a4: Long, a5: Long, a6: Long): Long = dispatch(a0, a1, a2, a3, a4, a5, a6)
        fun call8(a0: Long, a1: Long, a2: Long, a3: Long, a4: Long, a5: Long, a6: Long, a7: Long): Long = dispatch(a0, a1, a2, a3, a4, a5, a6, a7)
        fun call9(a0: Long, a1: Long, a2: Long, a3: Long, a4: Long, a5: Long, a6: Long, a7: Long, a8: Long): Long = dispatch(a0, a1, a2, a3, a4, a5, a6, a7, a8)
        fun call10(a0: Long, a1: Long, a2: Long, a3: Long, a4: Long, a5: Long, a6: Long, a7: Long, a8: Long, a9: Long): Long = dispatch(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)
    }

    private fun evaluateInitExpr(expr: ByteArray): Long {
        if (expr.isEmpty()) {
            return 0L
        }
        return when (expr[0].toInt() and 0xFF) {
            0x41 -> decodeLeb128I32(expr, 1).toLong()
            0x42 -> decodeLeb128I64(expr, 1)
            0x43 -> if (expr.size >= 5) {
                val bits = (expr[1].toInt() and 0xFF) or
                    ((expr[2].toInt() and 0xFF) shl 8) or
                    ((expr[3].toInt() and 0xFF) shl 16) or
                    ((expr[4].toInt() and 0xFF) shl 24)
                bits.toLong()
            } else { 0L }
            0x44 -> if (expr.size >= 9) {
                var value = 0L
                for (index in 0 until 8) {
                    value = value or ((expr[1 + index].toLong() and 0xFF) shl (index * 8))
                }
                value
            } else { 0L }
            else -> 0L
        }
    }

    private fun decodeLeb128I32(data: ByteArray, start: Int): Int {
        var result = 0
        var shift = 0
        var position = start
        while (position < data.size) {
            val byte = data[position].toInt() and 0xFF
            result = result or ((byte and 0x7F) shl shift)
            shift += 7
            position++
            if (byte and 0x80 == 0) {
                if (shift < 32 && byte and 0x40 != 0) {
                    result = result or ((-1) shl shift)
                }
                break
            }
        }
        return result
    }

    private fun decodeLeb128I64(data: ByteArray, start: Int): Long {
        var result = 0L
        var shift = 0
        var position = start
        while (position < data.size) {
            val byte = data[position].toInt() and 0xFF
            result = result or ((byte.toLong() and 0x7F) shl shift)
            shift += 7
            position++
            if (byte and 0x80 == 0) {
                if (shift < 64 && byte and 0x40 != 0) {
                    result = result or (-1L shl shift)
                }
                break
            }
        }
        return result
    }
}
