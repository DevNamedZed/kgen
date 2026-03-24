package org.kgen.compile

import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.CodeGenerator
import org.kgen.codegen.OptLevel
import org.kgen.codegen.TargetRegistry
import org.kgen.ir.Module
import org.kgen.ir.TargetProfile
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.pipeline.Pipeline
import org.kgen.pipeline.pipeline
import java.util.function.Consumer

/**
 * Top-level orchestrator for producing output files. Ties together IR construction,
 * pipeline transformations, code generation, and binary output.
 *
 * Lives in `org.kgen.compile` because it depends on all other packages:
 * `org.kgen.ir`, `org.kgen.pipeline`, `org.kgen.codegen`, and `org.kgen.binary`.
 *
 * Two ways to add modules:
 * - **Inline**: [createModule] / [defineModule] — build IR directly on the compiler
 * - **Pre-built**: [addModule] — add a Module built elsewhere
 *
 * ```java
 * // Java
 * var compiler = new Compiler("app");
 * compiler.defineModule("main", TargetProfile.NATIVE, module -> {
 *     module.defineClass(NativeScope.class, "Calculator", calc -> {
 *         calc.defineStaticFunction("add", params, Type.I32, fn -> {
 *             fn.ret(fn.instructions().add(fn.param(0), fn.param(1)));
 *         });
 *     });
 * });
 * byte[] exe = compiler.compile(Target.x86_64(), OptLevel.O2);
 * ```
 */
class Compiler(
    val name: String,
) {
    private val modules = mutableListOf<Module>()
    private val resources = mutableMapOf<String, ByteArray>()
    private var entryPoint: String? = null
    private val modulePipelines = mutableMapOf<String, Pipeline>()

    // --- Module creation (block-style) ---

    /**
     * Create a module using a block that auto-builds on exit.
     */
    fun defineModule(name: String, profile: TargetProfile, block: Consumer<ModuleBuilder>): Module {
        val builder = ModuleBuilder(name, profileToDefaultTarget(profile))
        block.accept(builder)
        val module = builder.build()
        modules += module
        return module
    }

    // --- Module creation (imperative) ---

    /**
     * Create a [ModuleBuilder]. Caller must call [ModuleBuilder.build] and then
     * [addModule] with the result, or use [defineModule] instead.
     */
    fun createModule(name: String, profile: TargetProfile): ModuleBuilder {
        return ModuleBuilder(name, profileToDefaultTarget(profile))
    }

    /**
     * Add a pre-built module.
     */
    fun addModule(module: Module): Compiler {
        modules += module
        return this
    }

    // --- Resources ---

    fun addResource(name: String, data: ByteArray): Compiler {
        resources[name] = data
        return this
    }

    // --- Metadata ---

    fun setEntryPoint(qualifiedName: String): Compiler {
        entryPoint = qualifiedName
        return this
    }

    // --- Pipeline configuration ---

    /**
     * Set a custom pipeline for a specific module.
     */
    fun setPipeline(moduleName: String, pipeline: Pipeline): Compiler {
        modulePipelines[moduleName] = pipeline
        return this
    }

    // --- Compile ---

    /**
     * Compile all modules to binary output for the given target.
     *
     * @param target the target architecture and CPU
     * @param optLevel optimization level (used for default pipeline if none configured per-module)
     * @param options additional code generation options
     * @return the compiled binary bytes
     */
    @JvmOverloads
    fun compile(
        target: Target,
        optLevel: OptLevel = OptLevel.O0,
        options: CodeGenOptions = CodeGenOptions(optimizationLevel = optLevel),
    ): ByteArray {
        require(modules.isNotEmpty()) {
            "No modules to compile. Add modules via createModule(), defineModule(), or addModule()."
        }

        val resolvedOptions = resolveOptions(options, target)
        val codeGenerator = resolveCodeGenerator(target)

        val optimizedModules = modules.map { module ->
            val pipeline = modulePipelines[module.name] ?: optLevel.pipeline()
            pipeline.execute(module)
        }

        val merged = prepareForCodegen(mergeModules(optimizedModules), resolvedOptions)
        return codeGenerator.generate(merged, resolvedOptions)
    }

    /**
     * Compile with a custom pipeline applied to all modules (unless overridden per-module).
     */
    fun compile(target: Target, pipeline: Pipeline, options: CodeGenOptions = CodeGenOptions()): ByteArray {
        require(modules.isNotEmpty()) { "No modules to compile." }

        val resolvedOptions = resolveOptions(options, target)
        val codeGenerator = resolveCodeGenerator(target)

        val optimizedModules = modules.map { module ->
            val modulePipeline = modulePipelines[module.name] ?: pipeline
            modulePipeline.execute(module)
        }

        val merged = prepareForCodegen(mergeModules(optimizedModules), resolvedOptions)
        return codeGenerator.generate(merged, resolvedOptions)
    }

    private fun mergeModules(modules: List<Module>): Module {
        if (modules.size == 1) {
            return modules.first()
        }
        val first = modules.first()
        return first.copy(
            functions = modules.flatMap { it.functions },
            globals = modules.flatMap { it.globals },
            structs = modules.flatMap { it.structs },
            classes = modules.flatMap { it.classes },
            interfaces = modules.flatMap { it.interfaces },
            enums = modules.flatMap { it.enums },
            aliases = modules.flatMap { it.aliases },
            globalCtors = modules.flatMap { it.globalCtors },
            globalDtors = modules.flatMap { it.globalDtors },
        )
    }

    /**
     * Prepare the merged module for code generation. Validates entry point
     * and sets targetTriple if needed (for backward compat with codegen backends
     * that read it from the module).
     */
    private fun prepareForCodegen(module: Module, options: CodeGenOptions): Module {
        var result = module

        // Set targetTriple on the module if the codegen target provides one
        if (result.targetTriple == null && options.target != null) {
            result = result.copy(targetTriple = options.target!!.tripleString())
        }

        // Embed resources as global constants
        if (resources.isNotEmpty()) {
            val resourceGlobals = resources.map { (name, data) ->
                org.kgen.ir.Global(
                    name = "__resource_$name",
                    type = org.kgen.ir.Type.Array(org.kgen.ir.Type.I8, data.size.toLong()),
                    initializer = org.kgen.ir.Constant.ArrayConst(
                        org.kgen.ir.Type.Array(org.kgen.ir.Type.I8, data.size.toLong()),
                        data.map { org.kgen.ir.Constant.I8(it) },
                    ),
                    isConstant = true,
                    linkage = org.kgen.ir.Linkage.PRIVATE,
                )
            }
            result = result.copy(globals = result.globals + resourceGlobals)
        }

        // Validate entry point exists (only for executable output — object files don't need it)
        val ep = options.entryPoint
        if (ep != null && options.outputFormat == org.kgen.codegen.OutputFormat.BINARY) {
            val functionNames = result.functions.map { it.name }.toSet()
            require(ep in functionNames || "main" in functionNames || "_start" in functionNames) {
                "Entry point '$ep' not found in module. Available functions: ${functionNames.sorted().joinToString()}"
            }
        }

        return result
    }

    private fun resolveOptions(options: CodeGenOptions, target: Target): CodeGenOptions {
        var resolved = options
        if (resolved.target == null) {
            resolved = resolved.copy(target = target)
        }
        if (resolved.entryPoint == null && entryPoint != null) {
            resolved = resolved.copy(entryPoint = entryPoint)
        }
        return resolved
    }

    private fun resolveCodeGenerator(target: Target): CodeGenerator {
        val registry = TargetRegistry()
        registerBuiltinGenerators(registry, target)
        return registry.generator(target.arch.name.lowercase())
    }

    private fun registerBuiltinGenerators(registry: TargetRegistry, target: Target) {
        val className = when (target.arch.name.lowercase()) {
            "x86_64" -> "org.kgen.target.x86.codegen.X86CodeGenerator"
            "arm64" -> "org.kgen.target.arm64.codegen.Arm64CodeGenerator"
            "riscv64" -> "org.kgen.target.riscv.codegen.RiscVCodeGenerator"
            "wasm32", "wasm64" -> "org.kgen.target.wasm.codegen.WasmCodeGenerator"
            "jvm" -> "org.kgen.target.jvm.codegen.JvmCodeGenerator"
            "msil", "msil_mixed" -> "org.kgen.target.clr.codegen.CilCodeGenerator"
            else -> error("No code generator for architecture: ${target.arch}")
        }
        try {
            val generatorClass = Class.forName(className)
            val generator = generatorClass.getDeclaredConstructor().newInstance() as CodeGenerator
            registry.registerGenerator(generator)
        } catch (e: ClassNotFoundException) {
            error("Code generator not found for target ${target.arch}. Ensure the backend is on the classpath.")
        }
    }

    companion object {
        private fun profileToDefaultTarget(profile: TargetProfile): Target = when (profile) {
            TargetProfile.MANAGED_VM -> Target.jvm()
            TargetProfile.COMPUTE -> Target.wasm()
            else -> Target.native()
        }
    }
}
