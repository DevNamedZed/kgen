package org.kgen.jit

import org.kgen.binary.*
import org.kgen.ir.Module
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.CodeGenerator
import org.kgen.codegen.CompiledCode
import org.kgen.codegen.OutputFormat
import org.kgen.ir.target.Target
import org.kgen.runtime.ManagedRuntime
import org.kgen.runtime.exec.SafepointManager
import org.kgen.runtime.gc.GarbageCollector
import org.kgen.pass.PassPipeline
import org.kgen.reflect.NativeMemory
import org.kgen.runtime.compile.RuntimeCompiler
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.*
import java.lang.invoke.MethodHandle

/**
 * Generic JIT compilation engine — compiles IR modules to native code,
 * loads them into executable memory, resolves symbols, and patches relocations.
 *
 * ```java
 * var jit = new JitEngine(codeGenerator);
 * jit.addResolver(SymbolResolver.host());
 *
 * jit.addModule(irModule);
 *
 * long result = jit.call("add", 3L, 4L);  // 7
 *
 * MethodHandle add = jit.handle("add",
 *     FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
 * long r2 = (long) add.invoke(10L, 20L);  // 30
 *
 * jit.close();
 * ```
 */
class JitEngine(
    private val codeGenerator: CodeGenerator,
    private val options: CodeGenOptions = CodeGenOptions(outputFormat = OutputFormat.OBJECT),
) : AutoCloseable {

    private val modules = mutableListOf<JitModule>()
    private val globalSymbols = mutableMapOf<String, JitSymbol>()
    private val resolvers = mutableListOf<SymbolResolver>()
    private val lazyStubs = mutableMapOf<String, LazyStub>()
    private var pipeline: PassPipeline? = null
    private var codeCache: CodeCache? = null
    private var tieredCompilation: TieredCompilation? = null
    private var debugInfo: JitDebugInfo? = null
    private var gc: GarbageCollector? = null
    private var safepointManager: SafepointManager? = null

    /**
     * Add a symbol resolver. Resolvers are searched in order when a symbol
     * cannot be found in JIT'd modules.
     */
    fun addResolver(resolver: SymbolResolver) {
        resolvers.add(resolver)
    }

    /**
     * Set an optimization pipeline. When set, IR modules are passed through
     * this pipeline before code generation.
     *
     * ```java
     * var pipeline = new PassPipeline();
     * pipeline.add(new ConstantFolding());
     * pipeline.add(new DeadCodeElimination());
     * jit.setOptimizationPipeline(pipeline);
     * ```
     */
    fun setOptimizationPipeline(pipeline: PassPipeline) {
        this.pipeline = pipeline
    }

    /**
     * Set a code cache with size limit and LRU eviction.
     * When the cache is full, least-recently-used modules are evicted.
     *
     * ```java
     * jit.setCodeCache(new CodeCache(64 * 1024 * 1024)); // 64 MB
     * ```
     */
    fun setCodeCache(cache: CodeCache) {
        this.codeCache = cache
    }

    /**
     * Enable tiered compilation. Functions are initially compiled with no/minimal
     * optimization, then recompiled at a higher tier after exceeding a call threshold.
     *
     * ```java
     * var tiered = new TieredCompilation();
     * tiered.setRecompileThreshold(100);
     * tiered.setTier1Pipeline(optimizingPipeline);
     * jit.setTieredCompilation(tiered);
     * ```
     */
    fun setTieredCompilation(tiered: TieredCompilation) {
        this.tieredCompilation = tiered
    }

    /**
     * Enable debug info output for JIT'd code. Makes function names visible
     * to profilers (perf) and debuggers.
     *
     * ```java
     * var debug = new JitDebugInfo();
     * debug.enablePerfMap();
     * jit.setDebugInfo(debug);
     * ```
     */
    fun setDebugInfo(debug: JitDebugInfo) {
        this.debugInfo = debug
    }

    /**
     * Set a garbage collector. When set, stack maps from compiled code are
     * automatically registered with the GC so it can find roots on the stack.
     *
     * ```java
     * jit.setGarbageCollector(runtime.gc());
     * ```
     */
    fun setGarbageCollector(gc: GarbageCollector) {
        this.gc = gc
    }

    /**
     * Set a safepoint manager for coordinating stop-the-world GC pauses.
     *
     * ```java
     * jit.setSafepointManager(runtime.safepoints());
     * ```
     */
    fun setSafepointManager(safepoints: SafepointManager) {
        this.safepointManager = safepoints
    }

    /**
     * Connect a managed runtime to this JIT engine. Wires up the GC and
     * safepoint manager so that compiled code integrates with the runtime.
     *
     * ```java
     * var runtime = DefaultManagedRuntime.create(heapSize);
     * jit.setRuntime(runtime);
     * jit.addModule(module); // stack maps auto-registered with GC
     * ```
     */
    fun setRuntime(runtime: ManagedRuntime) {
        this.gc = runtime.gc()
        this.safepointManager = runtime.safepoints()
    }

    /** The garbage collector, if set. */
    fun gc(): GarbageCollector? = gc

    /** The safepoint manager, if set. */
    fun safepointManager(): SafepointManager? = safepointManager

    /**
     * Compile an IR module and load it into executable memory.
     * Symbols from newer modules shadow older ones.
     *
     * Uses [CompiledCode] directly when the backend supports it,
     * bypassing [ObjectFile] construction for faster JIT compilation.
     */
    fun addModule(module: Module): JitModule {
        val adjusted = ensureTargetTriple(module)
        val optimized = pipeline?.execute(adjusted) ?: adjusted
        val jitModule = try {
            val code = codeGenerator.generateCode(optimized)
            loadCompiledCode(module.name, code)
        } catch (_: UnsupportedOperationException) {
            val obj = compileToObjectFile(optimized)
            loadObjectFile(module.name, obj)
        }
        tieredCompilation?.registerAllFunctions(module)
        trackInCache(jitModule)
        debugInfo?.notifyLoad(jitModule)
        return jitModule
    }

    /**
     * Load a pre-compiled [ObjectFile] directly (skip compilation).
     */
    fun addObjectFile(name: String, obj: ObjectFile): JitModule {
        val jitModule = loadObjectFile(name, obj)
        trackInCache(jitModule)
        debugInfo?.notifyLoad(jitModule)
        return jitModule
    }

    /**
     * Compile a `@KgenRuntime` classfile to native code and load it.
     *
     * Takes raw .class file bytes, validates the Runtime Subset rules,
     * lowers the bytecode to kgen IR, and compiles + loads the result.
     * All `@KgenExport` methods become callable JIT symbols.
     *
     * ```java
     * byte[] classBytes = Files.readAllBytes(Path.of("StringOps.class"));
     * jit.addRuntimeClass(classBytes);
     * long hash = jit.call("hash", strPtr, len);
     * ```
     */
    fun addRuntimeClass(classBytes: ByteArray): JitModule {
        val target = detectTarget()
        val compiler = RuntimeCompiler(target)
        val module = compiler.compile(classBytes)
        return addModule(module)
    }

    /**
     * Compile multiple `@KgenRuntime` classfiles and load them as a single module.
     * Cross-class calls between runtime methods are resolved within the module.
     */
    fun addRuntimeClasses(name: String, vararg classBytes: ByteArray): List<JitModule> {
        val target = detectTarget()
        val compiler = RuntimeCompiler(target)
        return classBytes.map { bytes ->
            val module = compiler.compile(bytes)
            addModule(module)
        }
    }

    private fun detectTarget(): Target {
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            arch.contains("amd64") || arch.contains("x86_64") -> Target.x86_64()
            arch.contains("aarch64") || arch.contains("arm64") -> Target.arm64()
            else -> Target.x86_64()
        }
    }

    /**
     * Look up a symbol by name. Searches JIT'd modules (newest first),
     * then external resolvers.
     */
    fun lookup(name: String): JitSymbol? {
        return globalSymbols[name] ?: resolveExternal(name)
    }

    /**
     * Get a typed [MethodHandle] for a JIT'd function.
     *
     * ```java
     * MethodHandle add = jit.handle("add",
     *     FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
     * long result = (long) add.invoke(3L, 4L);
     * ```
     */
    fun handle(name: String, descriptor: FunctionDescriptor): MethodHandle {
        val sym = lookup(name)
            ?: throw IllegalArgumentException("Symbol not found: $name")
        val segment = MemorySegment.ofAddress(sym.address)
        return linker.downcallHandle(segment, descriptor)
    }

    /**
     * Call a JIT'd function by name with long arguments, returning long.
     * Convenience method for the common case where all args and return are longs.
     */
    fun call(name: String, vararg args: Long): Long {
        checkTieredRecompilation(name)
        touchModuleForSymbol(name)
        val descriptor = FunctionDescriptor.of(JAVA_LONG, *Array(args.size) { JAVA_LONG })
        val h = handle(name, descriptor)
        return invokeHandle(h, args) as Long
    }

    /**
     * Call a JIT'd function returning int.
     */
    fun callInt(name: String, vararg args: Long): Int {
        checkTieredRecompilation(name)
        touchModuleForSymbol(name)
        val descriptor = FunctionDescriptor.of(JAVA_INT, *Array(args.size) { JAVA_LONG })
        val h = handle(name, descriptor)
        return invokeHandle(h, args) as Int
    }

    /**
     * Call a JIT'd function returning void.
     */
    fun callVoid(name: String, vararg args: Long) {
        checkTieredRecompilation(name)
        touchModuleForSymbol(name)
        val descriptor = FunctionDescriptor.ofVoid(*Array(args.size) { JAVA_LONG })
        val h = handle(name, descriptor)
        invokeHandle(h, args)
    }

    /**
     * Enable lazy compilation for a function. The function is not compiled
     * until it is first called. A stub is installed that triggers compilation.
     */
    fun addLazy(name: String, moduleProvider: () -> Module) {
        val stub = createLazyStub(name, moduleProvider)
        lazyStubs[name] = stub
        globalSymbols[name] = JitSymbol(name, stub.stubAddress, source = JitSymbol.SymbolSource.STUB)
    }

    /**
     * Remove a module and free its executable memory.
     * Symbols from this module are removed from the global table.
     * If another module defines the same symbol, it becomes visible again.
     */
    fun removeModule(jitModule: JitModule) {
        modules.remove(jitModule)
        codeCache?.remove(jitModule.name)
        debugInfo?.notifyUnload(jitModule)
        for (name in jitModule.symbolNames()) {
            val current = globalSymbols[name]
            if (current != null && jitModule.symbols[name]?.address == current.address) {
                globalSymbols.remove(name)
                for (m in modules.reversed()) {
                    val older = m.lookup(name)
                    if (older != null) {
                        globalSymbols[name] = older
                        break
                    }
                }
            }
        }
        jitModule.close()
    }

    /**
     * All currently defined symbol names.
     */
    fun symbolNames(): Set<String> = globalSymbols.keys + lazyStubs.keys

    /**
     * All loaded modules.
     */
    fun modules(): List<JitModule> = modules.toList()

    /** The code cache, if set. */
    fun codeCache(): CodeCache? = codeCache

    /** The tiered compilation config, if set. */
    fun tieredCompilation(): TieredCompilation? = tieredCompilation

    /** The debug info output, if set. */
    fun debugInfo(): JitDebugInfo? = debugInfo

    override fun close() {
        val modulesToClose = modules.toList()
        val stubsToClose = lazyStubs.values.toList()
        modules.clear()
        globalSymbols.clear()
        lazyStubs.clear()
        codeCache?.clear()
        tieredCompilation?.reset()
        debugInfo?.close()
        gc = null
        safepointManager = null
        for (module in modulesToClose) {
            module.close()
        }
        for (stub in stubsToClose) {
            stub.close()
        }
    }

    private fun touchModuleForSymbol(symbolName: String) {
        val cache = codeCache ?: return
        for (mod in modules) {
            if (mod.symbols.containsKey(symbolName)) {
                cache.touch(mod.name)
                return
            }
        }
    }

    private fun trackInCache(jitModule: JitModule) {
        val cache = codeCache ?: return
        val evicted = cache.track(jitModule)
        for (name in evicted) {
            val mod = modules.firstOrNull { it.name == name } ?: continue
            removeModule(mod)
        }
    }

    private fun checkTieredRecompilation(functionName: String) {
        val tiered = tieredCompilation ?: return
        if (!tiered.recordCall(functionName)) return
        val module = tiered.moduleForRecompilation(functionName) ?: return

        // Recompile with the next tier's pipeline
        val adjusted = ensureTargetTriple(module)
        val tierPipeline = tiered.pipelineForNextTier(functionName) ?: pipeline
        val optimized = tierPipeline?.execute(adjusted) ?: adjusted

        // Find and remove the old module containing this function
        val oldModule = modules.firstOrNull { it.symbols.containsKey(functionName) }
        if (oldModule != null) {
            removeModule(oldModule)
        }

        // Recompile and load
        val nextTier = tiered.currentTier(functionName) + 1
        val jitModule = try {
            val code = codeGenerator.generateCode(optimized)
            loadCompiledCode(module.name + "_tier$nextTier", code)
        } catch (_: UnsupportedOperationException) {
            val obj = compileToObjectFile(optimized)
            loadObjectFile(module.name + "_tier$nextTier", obj)
        }
        trackInCache(jitModule)
        tiered.markRecompiled(functionName)
    }

    private fun compileToObjectFile(module: Module): ObjectFile {
        val adjusted = ensureTargetTriple(module)
        val optimized = pipeline?.execute(adjusted) ?: adjusted
        val codeGenTarget = detectCodeGenTarget(optimized)
        return when (codeGenTarget) {
            "x86_64" -> compileX86(optimized)
            "arm64", "aarch64" -> compileArm64(optimized)
            "riscv" -> compileRiscV(optimized)
            else -> throw UnsupportedOperationException("JIT not supported for target: $codeGenTarget")
        }
    }

    private fun ensureTargetTriple(module: Module): Module {
        if (module.targetTriple != null) return module
        return module.copy(targetTriple = hostTriple)
    }

    private fun compileX86(module: Module): ObjectFile {
        val gen = codeGenerator
        if (gen is org.kgen.target.x86.codegen.X86CodeGenerator) {
            return gen.generateObjectFile(module)
        }
        throw IllegalStateException("Expected X86CodeGenerator for x86_64 target")
    }

    private fun compileArm64(module: Module): ObjectFile {
        val gen = codeGenerator
        if (gen is org.kgen.target.arm64.codegen.Arm64CodeGenerator) {
            return gen.generateObjectFile(module)
        }
        throw IllegalStateException("Expected Arm64CodeGenerator for arm64 target")
    }

    private fun compileRiscV(module: Module): ObjectFile {
        val gen = codeGenerator
        if (gen is org.kgen.target.riscv.codegen.RiscVCodeGenerator) {
            return gen.generateObjectFile(module)
        }
        throw IllegalStateException("Expected RiscVCodeGenerator for riscv target")
    }

    private fun detectCodeGenTarget(module: Module): String {
        val triple = module.targetTriple
        if (triple != null) {
            val lower = triple.lowercase()
            return when {
                lower.contains("x86_64") || lower.contains("x86-64") || lower.contains("amd64") -> "x86_64"
                lower.contains("aarch64") || lower.contains("arm64") -> "arm64"
                lower.contains("riscv") -> "riscv"
                else -> codeGenerator.targetName
            }
        }
        return codeGenerator.targetName
    }

    private fun loadObjectFile(name: String, obj: ObjectFile): JitModule {
        val textSection = obj.sections.firstOrNull { it.kind == SectionKind.TEXT }
            ?: throw IllegalArgumentException("No TEXT section in compiled module")
        val code = textSection.data
        if (code.isEmpty()) throw IllegalArgumentException("TEXT section is empty")

        val rodataSection = obj.sections.firstOrNull { it.kind == SectionKind.RODATA }

        // Collect external symbols that need trampolines (12 bytes each: movabs r11 + jmp r11)
        val externalNames = obj.symbols
            .filter { it.kind == SymbolKind.UNDEFINED }
            .map { it.name }
            .toSet()
        val trampolineSize = externalNames.size * TRAMPOLINE_ENTRY_SIZE

        val rodataSize = rodataSection?.data?.size?.toLong() ?: 0L
        val totalSize = code.size.toLong() + rodataSize + trampolineSize
        val mem = NativeMemory.allocateExecutable(maxOf(totalSize, 4096))

        mem.write(0, code)

        val rodataOffset = code.size.toLong()
        if (rodataSection != null && rodataSection.data.isNotEmpty()) {
            mem.write(rodataOffset, rodataSection.data)
        }

        val symbolMap = mutableMapOf<String, JitSymbol>()
        for (sym in obj.symbols) {
            if (sym.kind == SymbolKind.UNDEFINED) continue
            val addr = mem.address + sym.value
            val jitSym = JitSymbol(sym.name, addr, source = JitSymbol.SymbolSource.JIT)
            symbolMap[sym.name] = jitSym
        }

        // Build trampoline table for external symbols
        val trampolineBase = code.size.toLong() + rodataSize
        val trampolineAddrs = buildTrampolines(mem, trampolineBase, externalNames, symbolMap)

        applyRelocations(obj, mem, symbolMap, rodataOffset, trampolineAddrs)

        val jitModule = JitModule(name, mem, symbolMap)
        modules.add(jitModule)

        for ((symName, sym) in symbolMap) {
            globalSymbols[symName] = sym
        }

        return jitModule
    }

    private fun loadCompiledCode(name: String, code: CompiledCode): JitModule {
        val textBytes = code.textBytes
        if (textBytes.isEmpty()) throw IllegalArgumentException("No code in compiled module")

        val trampolineSize = code.externalSymbols.size * TRAMPOLINE_ENTRY_SIZE
        val rodataSize = code.rodataBytes.size.toLong()
        val totalSize = textBytes.size.toLong() + rodataSize + trampolineSize
        val mem = NativeMemory.allocateExecutable(maxOf(totalSize, 4096))

        mem.write(0, textBytes)

        val rodataOffset = textBytes.size.toLong()
        if (code.rodataBytes.isNotEmpty()) {
            mem.write(rodataOffset, code.rodataBytes)
        }

        val symbolMap = mutableMapOf<String, JitSymbol>()
        for (sym in code.symbols) {
            val addr = if (sym.rodataOffset >= 0) {
                mem.address + rodataOffset + sym.rodataOffset
            } else {
                mem.address + sym.offset
            }
            symbolMap[sym.name] = JitSymbol(sym.name, addr, source = JitSymbol.SymbolSource.JIT)
        }

        val trampolineBase = textBytes.size.toLong() + rodataSize
        val trampolineAddrs = buildTrampolines(mem, trampolineBase, code.externalSymbols, symbolMap)

        applyRelocationsFromList(code.relocations, mem, symbolMap, rodataOffset, trampolineAddrs)

        // Register stack maps with the GC
        val collector = gc
        if (collector != null) {
            for (stackMap in code.stackMaps) {
                collector.registerStackMap(stackMap)
            }
        }

        val jitModule = JitModule(name, mem, symbolMap)
        modules.add(jitModule)

        for ((symName, sym) in symbolMap) {
            globalSymbols[symName] = sym
        }

        return jitModule
    }

    /**
     * Build trampolines for external symbols. Each trampoline is:
     *   movabs r11, <64-bit address>   ; 49 BB <8 bytes>
     *   jmp r11                         ; 41 FF E3
     * Total: 13 bytes per entry, padded to 16 for alignment.
     */
    private fun buildTrampolines(
        mem: NativeMemory,
        baseOffset: Long,
        externalNames: Set<String>,
        localSymbols: MutableMap<String, JitSymbol>,
    ): Map<String, Long> {
        val trampolineAddrs = mutableMapOf<String, Long>()
        var offset = baseOffset
        for (name in externalNames) {
            val targetAddr = resolveSymbolForReloc(name, localSymbols)
            if (targetAddr != null) {
                val trampolineAddr = mem.address + offset
                trampolineAddrs[name] = trampolineAddr
                // movabs r11, imm64
                mem.writeByte(offset, 0x49.toByte())
                mem.writeByte(offset + 1, 0xBB.toByte())
                mem.writeLong(offset + 2, targetAddr)
                // jmp r11
                mem.writeByte(offset + 10, 0x41.toByte())
                mem.writeByte(offset + 11, 0xFF.toByte())
                mem.writeByte(offset + 12, 0xE3.toByte())
                offset += TRAMPOLINE_ENTRY_SIZE
            }
        }
        return trampolineAddrs
    }

    private fun applyRelocations(
        obj: ObjectFile,
        mem: NativeMemory,
        localSymbols: Map<String, JitSymbol>,
        rodataOffset: Long,
        trampolines: Map<String, Long> = emptyMap(),
    ) {
        applyRelocationsFromList(obj.relocations, mem, localSymbols, rodataOffset, trampolines)

        // Also apply section-level relocations
        for (section in obj.sections) {
            if (section.kind != SectionKind.TEXT) continue
            applyRelocationsFromList(section.relocations, mem, localSymbols, rodataOffset, trampolines)
        }
    }

    private fun applyRelocationsFromList(
        relocations: List<Relocation>,
        mem: NativeMemory,
        localSymbols: Map<String, JitSymbol>,
        rodataOffset: Long,
        trampolines: Map<String, Long> = emptyMap(),
    ) {
        for (rel in relocations) {
            val targetAddr = when (rel.type) {
                RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32,
                RelocationType.RiscV.CALL, RelocationType.RiscV.CALL_PLT ->
                    trampolines[rel.symbol]
                        ?: resolveSymbolForReloc(rel.symbol, localSymbols)
                        ?: continue
                else ->
                    resolveSymbolForReloc(rel.symbol, localSymbols)
                        ?: continue
            }

            val patchAddr = mem.address + rel.offset

            when (rel.type) {
                RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32 -> {
                    val value = (targetAddr + rel.addend - patchAddr).toInt()
                    mem.writeInt(rel.offset, value)
                }
                RelocationType.X86_64.R_32, RelocationType.X86_64.R_32S -> {
                    val value = (targetAddr + rel.addend).toInt()
                    mem.writeInt(rel.offset, value)
                }
                RelocationType.X86_64.R_64 -> {
                    val value = targetAddr + rel.addend
                    mem.writeLong(rel.offset, value)
                }
                RelocationType.AArch64.CALL26, RelocationType.AArch64.JUMP26 -> {
                    val delta = targetAddr + rel.addend - patchAddr
                    val existing = mem.readInt(rel.offset)
                    val imm26 = ((delta shr 2) and 0x03FFFFFFL).toInt()
                    mem.writeInt(rel.offset, (existing and 0xFC000000.toInt()) or imm26)
                }
                RelocationType.AArch64.ADR_PREL_PG_HI21 -> {
                    val target = targetAddr + rel.addend
                    val page = (target and 0xFFFFF000L) - (patchAddr and 0xFFFFF000L)
                    val immHi = ((page shr 12) and 0x1FFFFFL).toInt()
                    val immLo = (immHi and 0x3) shl 29
                    val immHiField = ((immHi shr 2) and 0x7FFFF) shl 5
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0x9F00001F.toInt()) or immLo or immHiField)
                }
                RelocationType.AArch64.ADD_ABS_LO12_NC,
                RelocationType.AArch64.LDST8_ABS_LO12_NC -> {
                    val imm12 = ((targetAddr + rel.addend) and 0xFFFL).toInt()
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFC003FF.toInt()) or (imm12 shl 10))
                }
                RelocationType.AArch64.LDST16_ABS_LO12_NC -> {
                    // imm12 for 16-bit loads/stores: offset is scaled by 2
                    val imm12 = (((targetAddr + rel.addend) and 0xFFFL).toInt() shr 1) and 0xFFF
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFC003FF.toInt()) or (imm12 shl 10))
                }
                RelocationType.AArch64.LDST32_ABS_LO12_NC -> {
                    // imm12 for 32-bit loads/stores: offset is scaled by 4
                    val imm12 = (((targetAddr + rel.addend) and 0xFFFL).toInt() shr 2) and 0xFFF
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFC003FF.toInt()) or (imm12 shl 10))
                }
                RelocationType.AArch64.LDST64_ABS_LO12_NC -> {
                    // imm12 for 64-bit loads/stores: offset is scaled by 8
                    val imm12 = (((targetAddr + rel.addend) and 0xFFFL).toInt() shr 3) and 0xFFF
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFC003FF.toInt()) or (imm12 shl 10))
                }
                RelocationType.AArch64.LDST128_ABS_LO12_NC -> {
                    // imm12 for 128-bit loads/stores: offset is scaled by 16
                    val imm12 = (((targetAddr + rel.addend) and 0xFFFL).toInt() shr 4) and 0xFFF
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFC003FF.toInt()) or (imm12 shl 10))
                }
                RelocationType.AArch64.ABS64 -> {
                    mem.writeLong(rel.offset, targetAddr + rel.addend)
                }
                RelocationType.AArch64.ABS32 -> {
                    mem.writeInt(rel.offset, (targetAddr + rel.addend).toInt())
                }
                RelocationType.AArch64.PREL32 -> {
                    // S + A - P, 32-bit PC-relative
                    val value = (targetAddr + rel.addend - patchAddr).toInt()
                    mem.writeInt(rel.offset, value)
                }
                RelocationType.AArch64.PREL64 -> {
                    // S + A - P, 64-bit PC-relative
                    val value = targetAddr + rel.addend - patchAddr
                    mem.writeLong(rel.offset, value)
                }
                RelocationType.AArch64.MOVW_UABS_G0, RelocationType.AArch64.MOVW_UABS_G0_NC -> {
                    // MOVZ/MOVK: bits [15:0] of absolute address → imm16 field [20:5]
                    val imm16 = ((targetAddr + rel.addend) and 0xFFFFL).toInt()
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFE0001F.toInt()) or (imm16 shl 5))
                }
                RelocationType.AArch64.MOVW_UABS_G1, RelocationType.AArch64.MOVW_UABS_G1_NC -> {
                    // MOVZ/MOVK: bits [31:16] of absolute address → imm16 field [20:5]
                    val imm16 = (((targetAddr + rel.addend) shr 16) and 0xFFFFL).toInt()
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFE0001F.toInt()) or (imm16 shl 5))
                }
                RelocationType.AArch64.MOVW_UABS_G2, RelocationType.AArch64.MOVW_UABS_G2_NC -> {
                    // MOVZ/MOVK: bits [47:32] of absolute address → imm16 field [20:5]
                    val imm16 = (((targetAddr + rel.addend) shr 32) and 0xFFFFL).toInt()
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFE0001F.toInt()) or (imm16 shl 5))
                }
                RelocationType.AArch64.MOVW_UABS_G3 -> {
                    // MOVZ/MOVK: bits [63:48] of absolute address → imm16 field [20:5]
                    val imm16 = (((targetAddr + rel.addend) shr 48) and 0xFFFFL).toInt()
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFE0001F.toInt()) or (imm16 shl 5))
                }
                RelocationType.AArch64.MOVW_SABS_G0 -> {
                    // Signed MOVZ/MOVN: bits [15:0] → imm16 field [20:5]
                    val imm16 = ((targetAddr + rel.addend) and 0xFFFFL).toInt()
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFE0001F.toInt()) or (imm16 shl 5))
                }
                RelocationType.AArch64.MOVW_SABS_G1 -> {
                    // Signed MOVZ/MOVN: bits [31:16] → imm16 field [20:5]
                    val imm16 = (((targetAddr + rel.addend) shr 16) and 0xFFFFL).toInt()
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFE0001F.toInt()) or (imm16 shl 5))
                }
                RelocationType.AArch64.MOVW_SABS_G2 -> {
                    // Signed MOVZ/MOVN: bits [47:32] → imm16 field [20:5]
                    val imm16 = (((targetAddr + rel.addend) shr 32) and 0xFFFFL).toInt()
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFE0001F.toInt()) or (imm16 shl 5))
                }
                RelocationType.AArch64.TSTBR14 -> {
                    // TBZ/TBNZ: 14-bit PC-relative offset in bits [18:5]
                    val delta = targetAddr + rel.addend - patchAddr
                    val imm14 = ((delta shr 2) and 0x3FFFL).toInt()
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFF8001F.toInt()) or (imm14 shl 5))
                }
                RelocationType.AArch64.CONDBR19 -> {
                    // B.cond / CBZ / CBNZ: 19-bit PC-relative offset in bits [23:5]
                    val delta = targetAddr + rel.addend - patchAddr
                    val imm19 = ((delta shr 2) and 0x7FFFFL).toInt()
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFF00001F.toInt()) or (imm19 shl 5))
                }
                RelocationType.AArch64.ADR_PREL_LO21 -> {
                    // ADR: 21-bit PC-relative, split into immlo [30:29] and immhi [23:5]
                    val delta = targetAddr + rel.addend - patchAddr
                    val imm = (delta and 0x1FFFFFL).toInt()
                    val immLo = (imm and 0x3) shl 29
                    val immHi = ((imm shr 2) and 0x7FFFF) shl 5
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0x9F00001F.toInt()) or immLo or immHi)
                }
                RelocationType.AArch64.ADR_PREL_PG_HI21_NC -> {
                    // Same as ADR_PREL_PG_HI21 but no overflow check
                    val target = targetAddr + rel.addend
                    val page = (target and 0xFFFFF000L) - (patchAddr and 0xFFFFF000L)
                    val immHi = ((page shr 12) and 0x1FFFFFL).toInt()
                    val immLo = (immHi and 0x3) shl 29
                    val immHiField = ((immHi shr 2) and 0x7FFFF) shl 5
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0x9F00001F.toInt()) or immLo or immHiField)
                }

                // RISC-V relocations
                RelocationType.RiscV.R_64 -> {
                    mem.writeLong(rel.offset, targetAddr + rel.addend)
                }
                RelocationType.RiscV.R_32 -> {
                    mem.writeInt(rel.offset, (targetAddr + rel.addend).toInt())
                }
                RelocationType.RiscV.R_32_PCREL -> {
                    val value = (targetAddr + rel.addend - patchAddr).toInt()
                    mem.writeInt(rel.offset, value)
                }
                RelocationType.RiscV.BRANCH -> {
                    // B-type: imm[12|10:5] | rs2 | rs1 | funct3 | imm[4:1|11] | opcode
                    val delta = (targetAddr + rel.addend - patchAddr).toInt()
                    val existing = mem.readInt(rel.offset)
                    val imm12 = (delta shr 12) and 0x1
                    val imm10_5 = (delta shr 5) and 0x3F
                    val imm4_1 = (delta shr 1) and 0xF
                    val imm11 = (delta shr 11) and 0x1
                    val encoded = (imm12 shl 31) or (imm10_5 shl 25) or (imm4_1 shl 8) or (imm11 shl 7)
                    val mask = (0x1 shl 31) or (0x3F shl 25) or (0xF shl 8) or (0x1 shl 7)
                    mem.writeInt(rel.offset, (existing and mask.inv()) or encoded)
                }
                RelocationType.RiscV.JAL -> {
                    // J-type: imm[20|10:1|11|19:12] | rd | opcode
                    val delta = (targetAddr + rel.addend - patchAddr).toInt()
                    val existing = mem.readInt(rel.offset)
                    val imm20 = (delta shr 20) and 0x1
                    val imm10_1 = (delta shr 1) and 0x3FF
                    val imm11 = (delta shr 11) and 0x1
                    val imm19_12 = (delta shr 12) and 0xFF
                    val encoded = (imm20 shl 31) or (imm10_1 shl 21) or (imm11 shl 20) or (imm19_12 shl 12)
                    mem.writeInt(rel.offset, (existing and 0xFFF) or encoded)
                }
                RelocationType.RiscV.CALL, RelocationType.RiscV.CALL_PLT -> {
                    // AUIPC+JALR pair: patch both instructions
                    val delta = targetAddr + rel.addend - patchAddr
                    // Upper 20 bits go to AUIPC, lower 12 to JALR
                    // Add 0x800 to upper for sign-extension compensation of the lower 12
                    val hi = ((delta + 0x800) shr 12).toInt()
                    val lo = (delta.toInt()) and 0xFFF
                    val auipc = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (auipc and 0xFFF) or (hi shl 12))
                    val jalr = mem.readInt(rel.offset + 4)
                    mem.writeInt(rel.offset + 4, (jalr and 0x000FFFFF) or (lo shl 20))
                }
                RelocationType.RiscV.PCREL_HI20 -> {
                    // U-type: upper 20 bits of PC-relative value into imm[31:12]
                    val delta = targetAddr + rel.addend - patchAddr
                    val hi = ((delta + 0x800) shr 12).toInt()
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFF) or (hi shl 12))
                }
                RelocationType.RiscV.HI20 -> {
                    // U-type: upper 20 bits of absolute value into imm[31:12]
                    val value = (targetAddr + rel.addend)
                    val hi = ((value + 0x800) shr 12).toInt()
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0xFFF) or (hi shl 12))
                }
                RelocationType.RiscV.LO12_I -> {
                    // I-type: lower 12 bits into imm[31:20]
                    val value = (targetAddr + rel.addend).toInt() and 0xFFF
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0x000FFFFF) or (value shl 20))
                }
                RelocationType.RiscV.LO12_S -> {
                    // S-type: lower 12 bits split into imm[31:25] and imm[11:7]
                    val value = (targetAddr + rel.addend).toInt() and 0xFFF
                    val imm11_5 = (value shr 5) and 0x7F
                    val imm4_0 = value and 0x1F
                    val existing = mem.readInt(rel.offset)
                    val mask = (0x7F shl 25) or (0x1F shl 7)
                    mem.writeInt(rel.offset, (existing and mask.inv()) or (imm11_5 shl 25) or (imm4_0 shl 7))
                }
                RelocationType.RiscV.PCREL_LO12_I -> {
                    // I-type: lower 12 bits of PC-relative into imm[31:20]
                    val value = (targetAddr + rel.addend - patchAddr).toInt() and 0xFFF
                    val existing = mem.readInt(rel.offset)
                    mem.writeInt(rel.offset, (existing and 0x000FFFFF) or (value shl 20))
                }
                RelocationType.RiscV.PCREL_LO12_S -> {
                    // S-type: lower 12 bits of PC-relative, split across imm[31:25] and imm[11:7]
                    val value = (targetAddr + rel.addend - patchAddr).toInt() and 0xFFF
                    val imm11_5 = (value shr 5) and 0x7F
                    val imm4_0 = value and 0x1F
                    val existing = mem.readInt(rel.offset)
                    val mask = (0x7F shl 25) or (0x1F shl 7)
                    mem.writeInt(rel.offset, (existing and mask.inv()) or (imm11_5 shl 25) or (imm4_0 shl 7))
                }
                RelocationType.RiscV.RELAX -> {
                    // Linker relaxation marker, no patching needed
                }
                else -> {}
            }
        }
    }

    private fun resolveSymbolForReloc(name: String, localSymbols: Map<String, JitSymbol>): Long? {
        localSymbols[name]?.let { return it.address }
        globalSymbols[name]?.let { return it.address }
        resolveExternal(name)?.let { return it.address }
        return null
    }

    private fun resolveExternal(name: String): JitSymbol? {
        for (resolver in resolvers) {
            val addr = resolver.resolve(name) ?: continue
            return JitSymbol(name, addr, source = JitSymbol.SymbolSource.HOST)
        }
        return null
    }

    private fun createLazyStub(name: String, moduleProvider: () -> Module): LazyStub {
        val arena = java.lang.foreign.Arena.ofShared()

        // Create an upcall stub: when native code calls this address,
        // it calls back into the JVM to compile the module and patch the stub.
        val upcallTarget = java.lang.invoke.MethodHandles.lookup()
            .bind(LazyCompileCallback(name, this), "compile",
                java.lang.invoke.MethodType.methodType(Long::class.java))
        val upcallStub = linker.upcallStub(
            upcallTarget,
            FunctionDescriptor.of(JAVA_LONG),
            arena,
        )

        // Allocate executable memory for the stub.
        // After compilation, this is patched to jump to the real function.
        val stubMem = NativeMemory.allocateExecutable(64)
        writeLazyStub(stubMem, upcallStub.address())

        return LazyStub(name, stubMem, stubMem.address, moduleProvider, this, arena)
    }

    internal fun materializeLazy(name: String) {
        val stub = lazyStubs[name]
            ?: throw IllegalStateException("No lazy stub for: $name")
        val module = stub.moduleProvider()
        addModule(module)
        val sym = globalSymbols[name]
            ?: throw IllegalStateException("Lazy compilation did not produce symbol: $name")

        // Patch the stub to jump directly to the compiled function.
        val stubMem = stub.memory
        writeLazyStub(stubMem, sym.address)

        // Update the global symbol to point to real code (not the stub)
        globalSymbols[name] = sym
        lazyStubs.remove(name)
        stub.arena.close()
    }

    private fun writeLazyStub(mem: NativeMemory, targetAddress: Long) {
        if (hostArch.contains("aarch64") || hostArch.contains("arm64")) {
            // ARM64 stub layout (16 bytes):
            //   [0..3]   LDR X16, [PC+8]   — load target address from literal pool
            //   [4..7]   BR X16            — branch to target
            //   [8..15]  <8-byte address>  — literal pool entry
            mem.writeInt(0, 0x58000050)     // LDR X16, #8 (PC-relative literal, 2 words ahead)
            mem.writeInt(4, 0xD61F0200.toInt()) // BR X16
            mem.writeLong(8, targetAddress)
        } else {
            // x86-64 stub layout (14 bytes):
            //   [0..5]   FF 25 00 00 00 00  — jmp [rip+0]
            //   [6..13]  <8-byte address>   — indirect target
            mem.writeByte(0, 0xFF.toByte())
            mem.writeByte(1, 0x25)
            mem.writeInt(2, 0)
            mem.writeLong(6, targetAddress)
        }
    }

    companion object {
        private val linker = Linker.nativeLinker()
        private const val TRAMPOLINE_ENTRY_SIZE = 16 // 13 bytes (movabs r11 + jmp r11), padded to 16
        private val isWindows = System.getProperty("os.name").lowercase().contains("win")
        private val hostArch = System.getProperty("os.arch").lowercase()

        internal val hostTriple: String = run {
            val arch = when {
                hostArch.contains("amd64") || hostArch.contains("x86_64") -> "x86_64"
                hostArch.contains("aarch64") || hostArch.contains("arm64") -> "aarch64"
                else -> hostArch
            }
            val os = when {
                isWindows -> "windows"
                System.getProperty("os.name").lowercase().contains("mac") -> "macos"
                else -> "linux"
            }
            "$arch-$os"
        }

        /**
         * Create a JIT engine for the current platform.
         */
        @JvmStatic
        fun forCurrentPlatform(): JitEngine {
            val arch = System.getProperty("os.arch").lowercase()
            val gen: CodeGenerator = when {
                arch.contains("amd64") || arch.contains("x86_64") ->
                    org.kgen.target.x86.codegen.X86CodeGenerator()
                arch.contains("aarch64") || arch.contains("arm64") ->
                    org.kgen.target.arm64.codegen.Arm64CodeGenerator()
                else -> throw UnsupportedOperationException("No JIT code generator for architecture: $arch")
            }
            return JitEngine(gen)
        }

        private fun invokeHandle(handle: MethodHandle, args: LongArray): Any? = when (args.size) {
            0 -> handle.invoke()
            1 -> handle.invoke(args[0])
            2 -> handle.invoke(args[0], args[1])
            3 -> handle.invoke(args[0], args[1], args[2])
            4 -> handle.invoke(args[0], args[1], args[2], args[3])
            5 -> handle.invoke(args[0], args[1], args[2], args[3], args[4])
            6 -> handle.invoke(args[0], args[1], args[2], args[3], args[4], args[5])
            else -> handle.invokeWithArguments(*args.map { it as Any }.toTypedArray())
        }
    }
}

internal class LazyStub(
    val name: String,
    val memory: NativeMemory,
    val stubAddress: Long,
    val moduleProvider: () -> Module,
    private val engine: JitEngine,
    val arena: java.lang.foreign.Arena,
) : AutoCloseable {
    override fun close() {
        memory.close()
        if (arena.scope().isAlive) arena.close()
    }
}

internal class LazyCompileCallback(
    private val name: String,
    private val engine: JitEngine,
) {
    fun compile(): Long {
        engine.materializeLazy(name)
        val sym = engine.lookup(name)
            ?: throw IllegalStateException("Lazy compilation did not produce symbol: $name")
        return sym.address
    }
}
