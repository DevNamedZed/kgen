package org.kgen.jit

import org.kgen.binary.*
import org.kgen.ir.Module
import org.kgen.ir.codegen.CodeGenOptions
import org.kgen.ir.codegen.CodeGenerator
import org.kgen.ir.codegen.OutputFormat
import org.kgen.pass.PassPipeline
import org.kgen.reflect.NativeMemory
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
     * Compile an IR module and load it into executable memory.
     * Symbols from newer modules shadow older ones.
     */
    fun addModule(module: Module): JitModule {
        val obj = compileToObjectFile(module)
        return loadObjectFile(module.name, obj)
    }

    /**
     * Load a pre-compiled [ObjectFile] directly (skip compilation).
     */
    fun addObjectFile(name: String, obj: ObjectFile): JitModule {
        return loadObjectFile(name, obj)
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
        val descriptor = FunctionDescriptor.of(JAVA_LONG, *Array(args.size) { JAVA_LONG })
        val h = handle(name, descriptor)
        return invokeHandle(h, args) as Long
    }

    /**
     * Call a JIT'd function returning int.
     */
    fun callInt(name: String, vararg args: Long): Int {
        val descriptor = FunctionDescriptor.of(JAVA_INT, *Array(args.size) { JAVA_LONG })
        val h = handle(name, descriptor)
        return invokeHandle(h, args) as Int
    }

    /**
     * Call a JIT'd function returning void.
     */
    fun callVoid(name: String, vararg args: Long) {
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
        // Remove symbols that belonged to this module
        for (name in jitModule.symbolNames()) {
            val current = globalSymbols[name]
            if (current != null && jitModule.symbols[name]?.address == current.address) {
                globalSymbols.remove(name)
                // Re-expose symbol from an older module if any
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

    override fun close() {
        val modulesToClose = modules.toList()
        val stubsToClose = lazyStubs.values.toList()
        modules.clear()
        globalSymbols.clear()
        lazyStubs.clear()
        for (module in modulesToClose) {
            module.close()
        }
        for (stub in stubsToClose) {
            stub.close()
        }
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
        if (gen is org.kgen.backend.x86.codegen.X86CodeGenerator) {
            return gen.generateObjectFile(module)
        }
        throw IllegalStateException("Expected X86CodeGenerator for x86_64 target")
    }

    private fun compileArm64(module: Module): ObjectFile {
        val gen = codeGenerator
        if (gen is org.kgen.backend.arm64.codegen.Arm64CodeGenerator) {
            return gen.generateObjectFile(module)
        }
        throw IllegalStateException("Expected Arm64CodeGenerator for arm64 target")
    }

    private fun compileRiscV(module: Module): ObjectFile {
        val gen = codeGenerator
        if (gen is org.kgen.backend.riscv.codegen.RiscVCodeGenerator) {
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
        for (rel in obj.relocations) {
            // For PC-relative relocations, use trampoline if available
            val targetAddr = when (rel.type) {
                RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32 ->
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
                RelocationType.AArch64.ABS64 -> {
                    mem.writeLong(rel.offset, targetAddr + rel.addend)
                }
                else -> {
                    // Skip unsupported relocation types silently for now
                }
            }
        }

        // Also apply section-level relocations
        for (section in obj.sections) {
            if (section.kind != SectionKind.TEXT) continue
            for (rel in section.relocations) {
                val targetAddr = when (rel.type) {
                    RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32 ->
                        trampolines[rel.symbol]
                            ?: resolveSymbolForReloc(rel.symbol, localSymbols) ?: continue
                    else ->
                        resolveSymbolForReloc(rel.symbol, localSymbols) ?: continue
                }
                val patchAddr = mem.address + rel.offset
                when (rel.type) {
                    RelocationType.X86_64.PC32, RelocationType.X86_64.PLT32 -> {
                        mem.writeInt(rel.offset, (targetAddr + rel.addend - patchAddr).toInt())
                    }
                    RelocationType.X86_64.R_32, RelocationType.X86_64.R_32S -> {
                        mem.writeInt(rel.offset, (targetAddr + rel.addend).toInt())
                    }
                    RelocationType.X86_64.R_64 -> {
                        mem.writeLong(rel.offset, targetAddr + rel.addend)
                    }
                    else -> {}
                }
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
        // x86-64 stub layout (Windows & Linux):
        //   [0..13]  jmp [rip+0]; <8-byte upcall address>  — indirect jump to upcall
        // After compilation, this is patched to jump to the real function.
        val stubMem = NativeMemory.allocateExecutable(64)

        // FF 25 00 00 00 00 = jmp [rip+0] (the 8-byte address follows immediately)
        stubMem.writeByte(0, 0xFF.toByte())
        stubMem.writeByte(1, 0x25)
        stubMem.writeInt(2, 0)
        stubMem.writeLong(6, upcallStub.address())

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
        // Same encoding: FF 25 00 00 00 00 [8-byte address]
        val stubMem = stub.memory
        stubMem.writeByte(0, 0xFF.toByte())
        stubMem.writeByte(1, 0x25)
        stubMem.writeInt(2, 0)
        stubMem.writeLong(6, sym.address)

        // Update the global symbol to point to real code (not the stub)
        globalSymbols[name] = sym
        lazyStubs.remove(name)
        stub.arena.close()
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
                    org.kgen.backend.x86.codegen.X86CodeGenerator()
                arch.contains("aarch64") || arch.contains("arm64") ->
                    org.kgen.backend.arm64.codegen.Arm64CodeGenerator()
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
