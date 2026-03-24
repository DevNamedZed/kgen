package org.kgen.runtime

import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.CodeGenerator
import org.kgen.codegen.OutputFormat
import org.kgen.ir.Module
import org.kgen.jit.JitEngine
import org.kgen.jit.SymbolResolver
import org.kgen.pipeline.Pipeline
import org.kgen.runtime.exec.*
import org.kgen.runtime.gc.*
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.riscv.codegen.RiscVCodeGenerator

/**
 * All-in-one runtime engine: JIT + GC + heap + safepoints + dispatch.
 *
 * The "easy mode" entry point for language runtimes. Auto-detects the host
 * platform, wires JIT + managed runtime, and exposes a simple call API.
 *
 * Every subsystem is pluggable — pass your own [HeapManager], [GarbageCollector],
 * [SafepointManager], or [CodeGenerator] through the builder. Defaults are
 * provided for everything.
 *
 * ```java
 * // Auto-detect platform, default everything
 * var engine = RuntimeEngine.create();
 * engine.addModule(module);
 * long result = engine.call("main");
 *
 * // Explicit code generator
 * var engine = RuntimeEngine.create(new X86CodeGenerator());
 *
 * // Full control
 * var engine = RuntimeEngine.builder()
 *     .codeGenerator(new Arm64CodeGenerator())
 *     .heap(new BumpHeap(64 * 1024 * 1024))
 *     .gc(myCustomGc)
 *     .safepoints(myCustomSafepointManager)
 *     .pipeline(OptLevel.O2.pipeline())
 *     .build();
 * ```
 *
 * @see JitEngine for JIT-only usage without managed runtime
 * @see ManagedRuntime for runtime-only usage without JIT
 */
class RuntimeEngine private constructor(
    private val jitEngine: JitEngine,
    private val managedRuntime: ManagedRuntime,
) : AutoCloseable {

    init {
        val runtimeSymbols = mutableMapOf<String, Long>()
        managedRuntime.installInto { name, address ->
            runtimeSymbols[name] = address
        }
        jitEngine.addResolver(SymbolResolver.map(runtimeSymbols))
        jitEngine.addResolver(SymbolResolver.host())
        jitEngine.setRuntime(managedRuntime)
    }

    fun addModule(module: Module) {
        jitEngine.addModule(module)
    }

    fun call(name: String, vararg args: Long): Long {
        return jitEngine.call(name, *args)
    }

    fun callInt(name: String, vararg args: Long): Int {
        return jitEngine.callInt(name, *args)
    }

    fun callVoid(name: String, vararg args: Long) {
        jitEngine.callVoid(name, *args)
    }

    fun registerType(layout: ObjectLayout): Int {
        val gc = managedRuntime.gc()
        if (gc is MarkSweepGC) {
            return (managedRuntime as? DefaultManagedRuntime)?.typeRegistry()?.register(layout) ?: 0
        }
        return 0
    }

    fun registerFinalizer(typeId: Int, finalizer: Finalizer) {
        managedRuntime.gc().registerFinalizer(typeId, finalizer)
    }

    fun addResolver(resolver: SymbolResolver) {
        jitEngine.addResolver(resolver)
    }

    fun addSymbol(name: String, address: Long) {
        jitEngine.addResolver(SymbolResolver.map(mapOf(name to address)))
    }

    fun setPipeline(pipeline: Pipeline) {
        jitEngine.setOptimizationPipeline(pipeline)
    }

    fun inspector(): org.kgen.jit.JitInspector = jitEngine.inspector()

    fun collectGarbage() {
        managedRuntime.gc().collect()
    }

    fun jit(): JitEngine = jitEngine

    fun runtime(): ManagedRuntime = managedRuntime

    fun heap(): HeapManager = managedRuntime.heap()

    fun gc(): GarbageCollector = managedRuntime.gc()

    override fun close() {
        jitEngine.close()
        managedRuntime.close()
    }

    class Builder {
        private var codeGenerator: CodeGenerator? = null
        private var heap: HeapManager? = null
        private var gc: GarbageCollector? = null
        private var safepoints: SafepointManager? = null
        private var dispatch: MethodDispatch? = null
        private var managedRuntime: ManagedRuntime? = null
        private var pipeline: Pipeline? = null

        fun codeGenerator(codeGenerator: CodeGenerator): Builder {
            this.codeGenerator = codeGenerator
            return this
        }

        fun heap(heap: HeapManager): Builder {
            this.heap = heap
            return this
        }

        fun gc(gc: GarbageCollector): Builder {
            this.gc = gc
            return this
        }

        fun safepoints(safepoints: SafepointManager): Builder {
            this.safepoints = safepoints
            return this
        }

        fun dispatch(dispatch: MethodDispatch): Builder {
            this.dispatch = dispatch
            return this
        }

        fun runtime(runtime: ManagedRuntime): Builder {
            this.managedRuntime = runtime
            return this
        }

        fun pipeline(pipeline: Pipeline): Builder {
            this.pipeline = pipeline
            return this
        }

        fun build(): RuntimeEngine {
            val codeGen = codeGenerator ?: detectCodeGenerator()
            val runtime = managedRuntime ?: DefaultManagedRuntime.create(16L * 1024 * 1024)
            val options = CodeGenOptions(outputFormat = OutputFormat.OBJECT)
            val jit = JitEngine(codeGen, options)
            pipeline?.let { jit.setOptimizationPipeline(it) }
            return RuntimeEngine(jit, runtime)
        }
    }

    companion object {
        /**
         * Create with auto-detected platform and default settings.
         */
        @JvmStatic
        fun create(): RuntimeEngine {
            return Builder().build()
        }

        /**
         * Create with an explicit code generator.
         */
        @JvmStatic
        fun create(codeGenerator: CodeGenerator): RuntimeEngine {
            return Builder().codeGenerator(codeGenerator).build()
        }

        /**
         * Create with an explicit managed runtime.
         */
        @JvmStatic
        fun create(codeGenerator: CodeGenerator, runtime: ManagedRuntime): RuntimeEngine {
            return Builder().codeGenerator(codeGenerator).runtime(runtime).build()
        }

        @JvmStatic
        fun builder(): Builder = Builder()

        private fun detectCodeGenerator(): CodeGenerator {
            val arch = System.getProperty("os.arch")?.lowercase() ?: "x86_64"
            return when {
                arch.contains("amd64") || arch.contains("x86_64") -> X86CodeGenerator()
                arch.contains("aarch64") || arch.contains("arm64") -> Arm64CodeGenerator()
                arch.contains("riscv") -> RiscVCodeGenerator()
                else -> X86CodeGenerator()
            }
        }
    }
}

/**
 * Kotlin DSL for building a [RuntimeEngine].
 *
 * ```kotlin
 * val engine = runtimeEngine {
 *     codeGenerator(X86CodeGenerator())
 *     heap(myHeap)
 *     gc(myGc)
 *     pipeline(OptLevel.O2.pipeline())
 * }
 * ```
 */
inline fun runtimeEngine(block: RuntimeEngine.Builder.() -> Unit): RuntimeEngine {
    return RuntimeEngine.builder().apply(block).build()
}
