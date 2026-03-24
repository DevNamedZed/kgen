package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.sets.InstructionBuilder
import org.kgen.ir.build.sets.InstructionSet
import org.kgen.ir.target.Target
import org.kgen.ir.types.*

/**
 * Builder for constructing IR [Module]s. The primary entry point for building IR.
 *
 * Three usage styles:
 *
 * **Block-style (recommended)** — define classes and functions with lambdas:
 * ```java
 * var module = new ModuleBuilder("math", TargetProfile.NATIVE);
 * module.defineClass(NativeScope.class, "MathLib", cls -> {
 *     cls.defineStaticFunction("add", params, Type.I32, fn -> {
 *         NativeScope ins = fn.instructions();
 *         fn.ret(ins.add(fn.param(0), fn.param(1)));
 *     });
 * });
 * Module ir = module.build();
 * ```
 *
 * **Imperative** — manual lifecycle with create/end:
 * ```kotlin
 * val module = ModuleBuilder("math", TargetProfile.NATIVE)
 * val fn = module.createFunction(NativeScope::class.java, "add", params, Type.I32)
 * val ins = fn.instructions
 * fn.ret(ins.add(fn.param(0), fn.param(1)))
 * fn.end()
 * val ir = module.build()
 * ```
 *
 * **Low-level SSA** — full control over basic blocks:
 * ```java
 * var module = new ModuleBuilder("math", Target.x86_64());
 * module.createFunction("add", params, Type.I32);
 * module.appendBlock("entry");
 * module.ret(module.add(module.param(0), module.param(1)));
 * module.finalizeFunction();
 * ```
 *
 * @param moduleName the name embedded in the output [Module]; defaults to `"module"`
 * @param target the compilation target; defaults to WASM
 * @param allowedCategories optional category whitelist; `null` means all categories allowed
 *
 * @see FunctionBuilder for structured control flow with typed instruction proxy
 * @see ClassBuilder for defining classes with scoped methods
 * @see Module for the immutable output produced by [build]
 */
class ModuleBuilder @JvmOverloads constructor(
    moduleName: String = "module",
    val target: Target = Target.wasm(),
    val allowedCategories: Set<IrCategory>? = null,
) : InstructionEmitter() {

    /**
     * Construct with a [TargetProfile] instead of a [Target].
     * Profile determines which instruction categories are legal.
     * Target defaults to [Target.native].
     */
    constructor(moduleName: String, profile: TargetProfile) : this(
        moduleName = moduleName,
        target = Target.native(),
        allowedCategories = profile.constraints,
    )

    /**
     * The highest [IrTier] present in [allowedCategories].
     *
     * Derived from the allowed category set and provided for backward compatibility with code
     * that reasons about tier ceilings rather than individual categories. When no constraint is
     * set (`allowedCategories == null`), this returns [IrTier.OBJECT] — the highest tier.
     */
    val maxTier: IrTier get() {
        val cats = allowedCategories ?: return IrTier.OBJECT
        return cats.maxOfOrNull { it.tier } ?: IrTier.STRUCTURAL
    }

    // Module state
    private val functions = mutableListOf<IrFunction>()
    private val globals = mutableListOf<Global>()
    private val structs = mutableListOf<StructDefinition>()
    private val classes = mutableListOf<ClassDefinition>()
    private val interfaces = mutableListOf<InterfaceDefinition>()
    private val enums = mutableListOf<EnumDefinition>()
    private val aliases = mutableListOf<TypeAlias>()
    private val metadata = mutableMapOf<String, MetadataValue>()
    private val targetFeatures = mutableSetOf<String>()
    private val globalCtors = mutableListOf<GlobalCtor>()
    private val globalDtors = mutableListOf<GlobalCtor>()
    private val ifuncs = mutableListOf<IFunc>()
    private val comdats = mutableListOf<ComdatDefinition>()
    private val moduleFlags = mutableMapOf<String, ModuleFlagValue>()
    /** The name of the module embedded in the output [Module]. Mutable so it can be set after construction. */
    var moduleName: String = moduleName

    /**
     * The target triple string for this module (e.g., `"x86_64-unknown-linux-gnu"`).
     * When set, it is recorded verbatim in the [Module] and passed through to binary formats
     * that embed a target triple (ELF, Mach-O, COFF). `null` means the triple is inherited
     * from [target] at code-generation time.
     */
    var targetTriple: String? = null

    /**
     * The data layout string describing pointer sizes, type alignments, and endianness
     * for the target (e.g., `"e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"`).
     * Required by some optimization passes that reason about memory layout. `null` means the
     * layout is inferred from [target] at code-generation time.
     */
    var dataLayout: String? = null

    /**
     * The source file path recorded in debug information for this module.
     * Stored in the output [Module] and emitted into DWARF `.debug_info` or CodeView PDB metadata.
     * `null` omits source-file attribution from the debug info.
     */
    var sourceFile: String? = null

    /**
     * Module-level inline assembly text inserted verbatim at the top of the translated output.
     * Appended before all function definitions, as in LLVM IR `module asm "..."` directives.
     * Use sparingly — prefer [InstructionEmitter.inlineAsm] for function-scoped inline assembly.
     */
    var moduleInlineAsm: String? = null

    // Submodule state
    private val submodules = mutableListOf<Submodule>()
    private var currentSubmoduleName: String? = null
    private var currentSubmoduleConstraints: Set<IrCategory>? = null
    private val currentSubmoduleFunctions = mutableListOf<String>()
    private val currentSubmoduleGlobals = mutableListOf<String>()

    // Current function state (low-level SSA path)
    private var currentFunctionName: String? = null
    private var currentParams: List<Parameter> = emptyList()
    private var currentReturnType: Type = Type.Void
    private var currentFunctionMeta = FunctionMeta()
    private var currentContext: FunctionContext? = null
    private var blockInstructions = linkedMapOf<String, MutableList<Instruction>>()
    private var insertPoint: String? = null
    private var nextId = 0
    private var nextBlockId = 0

    private data class FunctionMeta(
        val linkage: Linkage = Linkage.EXTERNAL,
        val visibility: Visibility = Visibility.DEFAULT,
        val callingConv: CallingConvention = CallingConvention.C,
        val attributes: Set<FnAttribute> = emptySet(),
        val isVarArg: Boolean = false,
        val section: String? = null,
        val align: Int? = null,
        val gc: String? = null,
        val personality: FunctionRef? = null,
    )

    override fun emit(instruction: Instruction) {
        val ctx = currentContext
        if (ctx != null) {
            ctx.emit(instruction)
        } else {
            val cats = currentSubmoduleConstraints ?: allowedCategories
            if (cats != null && instruction.category !in cats) {
                error("${instruction::class.simpleName} requires category ${instruction.category} " +
                    "which is not in the allowed set")
            }
            val block = insertPoint ?: error("No insertion point set. Call appendBlock() first.")
            blockInstructions.getOrPut(block) { mutableListOf() } += instruction
        }
    }

    override fun nextRef(type: Type): InstructionRef {
        val ctx = currentContext
        return if (ctx != null) {
            ctx.nextRef(type)
        } else {
            InstructionRef("%${nextId++}", type)
        }
    }

    // --- Module-level ---

    /**
     * Record a target feature string that the generated code requires.
     *
     * Feature strings are target-specific (e.g., `"+avx2"`, `"+neon"`, `"+simd128"`).
     * They are forwarded to the code generator so it can emit feature-gated instructions
     * and, where applicable, embedded in the binary's feature attribute section.
     *
     * @param feature the target feature string to enable (e.g., `"+avx2"`)
     */
    fun addTargetFeature(feature: String) { targetFeatures += feature }

    /**
     * Define a mutable or constant global variable and add it to the module.
     *
     * Globals are allocated in the data or BSS segment of the output binary. The returned
     * [GlobalRef] can be passed directly to [InstructionEmitter.load], [InstructionEmitter.store],
     * or [InstructionEmitter.gep] to generate PC-relative accesses in the function body.
     *
     * ```java
     * // Java — a zero-initialized mutable i32 counter
     * GlobalRef counter = ir.addGlobal("counter", Type.I32);
     *
     * // Java — a constant i64 initialized to 42
     * GlobalRef limit = ir.addGlobal(
     *     "LIMIT", Type.I64, new Constant.I64(42L), /*isConstant=*/ true
     * );
     * ```
     *
     * @param name the symbol name for the global; must be unique within the module
     * @param type the IR type of the stored value
     * @param initializer the compile-time constant initializer, or `null` for zero initialization
     * @param isConstant `true` to place the global in a read-only segment (analogous to `const`)
     * @param linkage controls linker visibility and duplicate-definition resolution
     * @param visibility ELF/Mach-O symbol visibility (`DEFAULT`, `HIDDEN`, `PROTECTED`)
     * @param threadLocal the TLS model to use, or `null` for a regular (non-TLS) global
     * @param section explicit output section name (e.g., `".data.hot"`), or `null` to use the default
     * @param align byte alignment override, or `null` to use the target default for [type]
     * @param addressSpace the address space number; `0` is the default data address space
     * @return a [GlobalRef] that can be used as a pointer value in instruction emission
     */
    @JvmOverloads fun addGlobal(
        name: String, type: Type, initializer: Constant? = null, isConstant: Boolean = false,
        linkage: Linkage = Linkage.EXTERNAL, visibility: Visibility = Visibility.DEFAULT,
        threadLocal: ThreadLocalMode? = null, section: String? = null,
        align: Int? = null, addressSpace: Int = 0,
    ): GlobalRef {
        globals += Global(name, type, initializer, isConstant, linkage, visibility, threadLocal, section, align, addressSpace)
        if (currentSubmoduleName != null) currentSubmoduleGlobals += name
        return GlobalRef(name, type)
    }
    /**
     * Define a low-level native struct type in the module.
     *
     * Struct types are used with [InstructionEmitter.gep], [InstructionEmitter.extractValue], and
     * [InstructionEmitter.insertValue] to model C-style record types on native backends (x86-64,
     * ARM64, RISC-V). For managed backends (JVM, WASM-GC), prefer [addClass].
     *
     * ```java
     * // Java — a two-field Point struct
     * ir.addStruct("Point", List.of(Param.of("x", Type.F64), Param.of("y", Type.F64)));
     * ```
     *
     * @param name the struct type name, referenced as `Type.Named("name")` in the IR
     * @param fields the ordered list of named, typed fields
     * @param packed `true` to suppress padding between fields (equivalent to `__attribute__((packed))`)
     * @param align explicit byte alignment for the struct as a whole, or `null` for the target default
     */
    @JvmOverloads fun addStruct(name: String, fields: List<Param>, packed: Boolean = false, align: Int? = null) { structs += StructDefinition(name, fields, packed, align) }

    /**
     * Register a managed class definition in the module.
     *
     * Class definitions drive code generation on managed backends (JVM, WASM-GC): the JVM backend
     * emits a `.class` file, the WASM-GC backend emits a `(type (struct ...))` definition, and the
     * native backend uses the class layout computed by `ClassLayout` for [IrCategory.OBJECT]
     * instruction lowering.
     *
     * Build a [ClassDefinition] with [classDef] (DSL) or `ClassDefinition(...)` (constructor), then pass it here.
     * After registration, [FunctionBuilder]'s smart-dispatch helpers (`callMethod`, `getField`) can
     * infer field and method types without requiring explicit signatures at every call site.
     *
     * @param cls the fully described class definition to register
     * @see findClass
     */
    fun addClass(cls: ClassDefinition) { classes += cls }

    /**
     * Look up a previously registered [ClassDefinition] by name.
     *
     * Used internally by [FunctionBuilder]'s smart-dispatch methods to resolve field and method
     * signatures. Returns `null` when no class with that name has been registered.
     *
     * @param name the class name to look up
     * @return the [ClassDefinition] if found, or `null`
     */
    fun findClass(name: String): ClassDefinition? = classes.find { it.name == name }

    /**
     * Register a managed interface definition in the module.
     *
     * Interface definitions are used for [IrCategory.OBJECT] instruction lowering (`InterfaceCall`,
     * `InstanceOf`) and for generating interface dispatch tables (ITables) in the native backend.
     * Build one with [interfaceDef] (DSL) or construct [InterfaceDefinition] directly.
     *
     * @param iface the fully described interface definition to register
     */
    fun addInterface(iface: InterfaceDefinition) { interfaces += iface }

    /**
     * Register an enum (or algebraic enum) definition in the module.
     *
     * On managed backends this maps to an enum class. On native backends, variants with fields
     * are lowered to tagged unions via [IrCategory.OBJECT] instructions (`ConstructVariant`,
     * `TagSwitch`, `GetTag`, `GetVariantField`).
     *
     * @param enumDef the enum definition to register, including all variants and optional methods
     */
    fun addEnum(enumDef: EnumDefinition) { enums += enumDef }

    /**
     * Define a named type alias in the module.
     *
     * Type aliases are recorded in the [Module] for use by IR printers, debuggers, and backends
     * that emit human-readable type names. They do not affect code generation — uses of the alias
     * are treated identically to the underlying [type].
     *
     * @param name the alias name
     * @param type the IR type this name stands for
     */
    fun addTypeAlias(name: String, type: Type) { aliases += TypeAlias(name, type) }

    /**
     * Attach a named metadata entry at module scope.
     *
     * Module metadata is a flexible key-value store for communicating pass-specific or
     * tool-specific annotations (e.g., `"llvm.ident"`, `"kgen.version"`). Passes and backends
     * that recognize a key consume it; unrecognized keys are ignored and passed through the
     * pipeline unchanged.
     *
     * @param key the metadata key string
     * @param value the metadata value (string, integer, list, or reference)
     */
    fun addMetadata(key: String, value: MetadataValue) { metadata[key] = value }

    /**
     * Register a global constructor function to be called before `main()`.
     *
     * Global constructors are called in ascending priority order when the binary is loaded.
     * The typical use case is static field initialization: the `<clinit>` method of each class
     * is registered as a global constructor with a unique priority.
     *
     * On ELF targets this populates `.init_array`; on PE targets `.CRT$XCU`;
     * on Mach-O targets `__mod_init_func`.
     *
     * @param function the name of the function to call (must be declared or defined in this module)
     * @param priority execution priority; lower numbers run first; `65535` is the default (last)
     */
    @JvmOverloads fun addGlobalCtor(function: String, priority: Int = 65535) { globalCtors += GlobalCtor(function, priority) }

    /**
     * Register a global destructor function to be called after `main()` returns.
     *
     * Destructors are called in descending priority order during process teardown. On ELF targets
     * this populates `.fini_array`; on Mach-O targets `__mod_term_func`.
     *
     * @param function the name of the function to call (must be declared or defined in this module)
     * @param priority execution priority; higher numbers run first; `65535` is the default (last)
     */
    @JvmOverloads fun addGlobalDtor(function: String, priority: Int = 65535) { globalDtors += GlobalCtor(function, priority) }

    /**
     * Define a COMDAT group in the module.
     *
     * COMDAT groups allow multiple translation units to define the same symbol (e.g., an inline
     * function or a template instantiation) and let the linker keep only one copy. The [kind]
     * controls the linker's duplicate-elimination policy.
     *
     * @param name the COMDAT group name (typically the symbol name of the anchor function or global)
     * @param kind the selection kind (e.g., `ANY`, `EXACT_MATCH`, `LARGEST`, `NO_DUPLICATES`, `SAME_SIZE`)
     */
    fun addComdat(name: String, kind: ComdatSelectionKind) { comdats += ComdatDefinition(name, kind) }

    /**
     * Set a named module flag that communicates capabilities or requirements to the linker or runtime.
     *
     * Module flags are a key-value dictionary embedded in the module (analogous to LLVM IR
     * `!llvm.module.flags`). Examples include `"PIC Level"`, `"PIE Level"`, `"stack-protector-guard"`,
     * or `"kgen.abi"`. Linkers and runtimes that recognize a key may act on it; unrecognized keys
     * are forwarded transparently.
     *
     * @param key the flag name
     * @param value the flag value (integer, string, or a merge-behavior-tagged variant)
     */
    fun addModuleFlag(key: String, value: ModuleFlagValue) { moduleFlags[key] = value }

    // --- Submodule management ---

    /**
     * Open a new submodule scope, grouping subsequently created functions and globals under a named unit.
     *
     * Every function and global defined after this call (and before the matching [endSubmodule]) is
     * recorded as belonging to the named submodule. Submodule constraints override the module-level
     * [allowedCategories] for all instructions emitted within the scope, allowing different parts of
     * the same module to have different IR tier ceilings.
     *
     * Submodules appear in the [Module] returned by [build] and are used by passes and backends to
     * apply per-submodule optimization policies or to emit separate object file sections.
     *
     * Submodule scopes cannot be nested — call [endSubmodule] before opening another one. A
     * function cannot be started ([createFunction]) while a submodule is open without first
     * closing it.
     *
     * @param name the submodule identifier; must be unique within the module
     * @param constraints the set of [IrCategory] values allowed inside this submodule; overrides
     *   the module-level [allowedCategories] for all instructions emitted in this scope
     * @throws IllegalStateException if a submodule is already open, or if a function is in progress
     */
    fun beginSubmodule(name: String, constraints: Set<IrCategory>) {
        check(currentSubmoduleName == null) { "Already in submodule '$currentSubmoduleName'. Call endSubmodule() first." }
        check(currentFunctionName == null) { "Cannot begin submodule while building function '$currentFunctionName'" }
        currentSubmoduleName = name
        currentSubmoduleConstraints = constraints
        currentSubmoduleFunctions.clear()
        currentSubmoduleGlobals.clear()
    }

    /**
     * Close the current submodule scope and record all collected functions and globals in it.
     *
     * The submodule is added to the module's submodule list and will appear in the [Module]
     * returned by [build]. After this call, newly defined functions and globals belong to the
     * module's global scope again (constrained only by the top-level [allowedCategories]).
     *
     * @throws IllegalStateException if no submodule scope is currently open
     */
    fun endSubmodule() {
        val name = currentSubmoduleName ?: error("No submodule in progress")
        submodules += Submodule(name, currentSubmoduleConstraints!!, currentSubmoduleFunctions.toList(), currentSubmoduleGlobals.toList())
        currentSubmoduleName = null
        currentSubmoduleConstraints = null
        currentSubmoduleFunctions.clear()
        currentSubmoduleGlobals.clear()
    }

    // --- Function management ---

    /**
     * Begin building a new function and return its parameters as usable [Value] references.
     *
     * This method initializes the builder's internal per-function state: it clears the block map,
     * resets the SSA counter, and records the function's signature and attributes. The returned
     * [Parameter] list represents the function's incoming arguments as SSA values — pass them
     * directly to instruction-emission methods.
     *
     * After calling this method, create at least one block with [appendBlock], emit instructions
     * using an instruction builder, and finalize the function with [finalizeFunction] before
     * starting the next one.
     *
     * For a higher-level API that hides SSA block management, use [function] instead.
     *
     * ```java
     * // Java — emit a function that squares an i32
     * List<Parameter> params = ir.createFunction(
     *     "square",
     *     List.of(Param.of("n", Type.I32)),
     *     Type.I32
     * );
     *
     * ir.appendBlock("entry");
     * Value result = ir.mul(params.get(0), params.get(0));
     * ir.ret(result);
     * ir.finalizeFunction();
     * ```
     *
     * @param name the function name, used as the linker symbol (subject to [linkage] and mangling)
     * @param params the ordered list of named, typed parameters; use [Param.of] from Java
     * @param returnType the function's return type; use [Type.Void] for void functions
     * @param linkage controls symbol visibility and linker duplicate-definition policy
     * @param visibility ELF/Mach-O symbol visibility (`DEFAULT`, `HIDDEN`, `PROTECTED`)
     * @param callingConv the calling convention for generated call sites and prologues/epilogues
     * @param attributes function-level attributes (e.g., `NOINLINE`, `ALWAYS_INLINE`, `NORETURN`)
     * @param isVarArg `true` to allow variable-argument calls to this function (C `...` style)
     * @param section explicit output section name for the function's code, or `null` for the default
     * @param align byte alignment for the function's entry point, or `null` for the target default
     * @param gc the garbage collector strategy name (e.g., `"statepoint-example"`), or `null`
     * @param personality the personality function reference for exception handling, or `null`
     * @return the function's parameters as a list of [Parameter] values in declaration order
     * @throws IllegalStateException if [finalizeFunction] has not been called after the previous function
     */
    @JvmOverloads fun createFunction(
        name: String, params: List<Param>, returnType: Type,
        linkage: Linkage = Linkage.EXTERNAL, visibility: Visibility = Visibility.DEFAULT,
        callingConv: CallingConvention = CallingConvention.C,
        attributes: Set<FnAttribute> = emptySet(), isVarArg: Boolean = false,
        section: String? = null, align: Int? = null, gc: String? = null,
        personality: FunctionRef? = null,
    ): List<Parameter> {
        check(currentFunctionName == null) { "Must call finalizeFunction() before starting a new function" }
        currentFunctionName = name
        val parameters = params.mapIndexed { i, p -> Parameter(p.name, p.type, i) }
        currentParams = parameters
        currentReturnType = returnType
        currentFunctionMeta = FunctionMeta(linkage, visibility, callingConv, attributes, isVarArg, section, align, gc, personality)
        val meta = FunctionContext.FunctionMeta(linkage, visibility, callingConv, attributes, isVarArg, section, align, gc, personality)
        currentContext = FunctionContext(name, parameters, returnType, effectiveConstraints(), meta)
        blockInstructions = linkedMapOf()
        insertPoint = null
        nextId = 0
        nextBlockId = 0
        return currentParams
    }

    /**
     * Begin building a function and return a [DefinedFunction] handle.
     *
     * The handle provides [DefinedFunction.param] access and implements [Value] so it can
     * be passed directly to `call` instructions. It also implements [AutoCloseable] for
     * try-with-resources scoping — closing the handle calls [finalizeFunction].
     *
     * ```java
     * try (DefinedFunction fn = ir.defineFunction("add",
     *         List.of(Param.of("a", Type.I32), Param.of("b", Type.I32)), Type.I32)) {
     *     ir.appendBlock("entry");
     *     Value sum = b.add(fn.param("a"), fn.param("b"));
     *     b.ret(sum);
     * }
     * ```
     *
     * @param name the function's symbol name (unique within the module)
     * @param params the function's formal parameter list
     * @param returnType the function's return type
     * @return a [DefinedFunction] handle for parameter access, call references, and scoping
     */
    @JvmOverloads fun defineFunction(
        name: String, params: List<Param>, returnType: Type,
        linkage: Linkage = Linkage.EXTERNAL, visibility: Visibility = Visibility.DEFAULT,
        callingConv: CallingConvention = CallingConvention.C,
        attributes: Set<FnAttribute> = emptySet(), isVarArg: Boolean = false,
        section: String? = null, align: Int? = null, gc: String? = null,
        personality: FunctionRef? = null,
    ): DefinedFunction {
        val parameters = createFunction(
            name, params, returnType, linkage, visibility, callingConv,
            attributes, isVarArg, section, align, gc, personality,
        )
        val ref = FunctionRef(name, Type.Function(params.map { it.type }, returnType, isVarArg))
        return DefinedFunction(this, parameters, ref)
    }

    /**
     * Begin building a function and return a [FunctionBuilder] for structured control flow.
     *
     * This is the preferred entry point when the target language has structured control flow
     * (loops, if/else) that maps naturally to the scope's helpers (`whileLoop`, `ifThen`,
     * `ifElse`, `forLoop`). The scope manages alloca-backed mutable variables, loop break/continue
     * targets, and auto-terminates the function with `unreachable` if [FunctionBuilder.end] is called
     * without a prior return.
     *
     * Calling this method is equivalent to calling [createFunction] followed by
     * `appendBlock("entry")` and wrapping the result in a [FunctionBuilder]. The
     * scope's [FunctionBuilder.end] method calls [finalizeFunction] automatically.
     *
     * ```kotlin
     * val fn = ir.function("factorial", listOf(Param("n", Type.I32)), Type.I32)
     * val ins = fn.instructions
     * val result = ins.variable(Type.i32(1))
     * val counter = ins.variable(Type.i32(1))
     * fn.whileLoop(
     *     condition = { ins.le(ins.get(counter), fn.param(0)) },
     *     body = {
     *         ins.set(result, ins.mul(ins.get(result), ins.get(counter)))
     *         ins.set(counter, ins.add(ins.get(counter), Type.i32(1)))
     *     },
     * )
     * fn.ret(ins.get(result))
     * fn.end()
     * ```
     *
     * @param name the function symbol name
     * @param params the ordered list of named, typed parameters
     * @param returnType the return type; use [Type.Void] for void functions
     * @param linkage controls linker symbol visibility
     * @param visibility ELF/Mach-O symbol visibility
     * @return a [FunctionBuilder] positioned at the function's entry block
     */
    @JvmOverloads fun function(
        name: String, params: List<Param>, returnType: Type,
        linkage: Linkage = Linkage.EXTERNAL, visibility: Visibility = Visibility.DEFAULT,
    ): FunctionBuilder<org.kgen.ir.build.scope.FullScope> {
        val parameters = params.mapIndexed { i, p -> Parameter(p.name, p.type, i) }
        val meta = FunctionContext.FunctionMeta(linkage = linkage, visibility = visibility)
        val context = FunctionContext(name, parameters, returnType, effectiveConstraints(), meta)
        context.appendBlock("entry")
        return FunctionBuilder(this, context, org.kgen.ir.build.scope.FullScope::class.java)
    }

    // --- Typed function creation (with scope) ---

    /**
     * Create a scoped function and return a typed [FunctionBuilder].
     *
     * ```java
     * FunctionBuilder<NativeScope> fn = module.createFunction(NativeScope.class, "add", params, Type.I32);
     * NativeScope ins = fn.instructions();
     * fn.ret(ins.add(fn.param(0), fn.param(1)));
     * fn.end();
     * ```
     */
    fun <T : InstructionSet> createFunction(
        scope: Class<T>, name: String, params: List<Param>, returnType: Type,
    ): FunctionBuilder<T> {
        val parameters = params.mapIndexed { i, p -> Parameter(p.name, p.type, i) }
        val context = FunctionContext(name, parameters, returnType, effectiveConstraints())
        context.appendBlock("entry")
        return FunctionBuilder(this, context, scope)
    }

    /**
     * Create a scoped function using a block that auto-ends on exit.
     *
     * ```java
     * // Java
     * module.defineFunction(NativeScope.class, "add", params, Type.I32, fn -> {
     *     NativeScope ins = fn.instructions();
     *     fn.ret(ins.add(fn.param(0), fn.param(1)));
     * });
     * ```
     *
     * ```kotlin
     * // Kotlin
     * module.defineFunction(NativeScope::class.java, "add", params, Type.I32) { fn ->
     *     val ins = fn.instructions!!
     *     fn.ret(ins.add(fn.param(0), fn.param(1)))
     * }
     * ```
     */
    fun <T : InstructionSet> defineFunction(
        scope: Class<T>, name: String, params: List<Param>, returnType: Type,
        block: java.util.function.Consumer<FunctionBuilder<T>>,
    ): FunctionRef {
        val functionBuilder = createFunction(scope, name, params, returnType)
        try {
            block.accept(functionBuilder)
        } finally {
            functionBuilder.end()
        }
        return FunctionRef(name, Type.Function(params.map { it.type }, returnType))
    }

    /**
     * Register a finalized [IrFunction] with this module. Called by [FunctionBuilder.end].
     */
    internal fun addFunction(irFunction: IrFunction) {
        functions += irFunction
        if (currentSubmoduleName != null) {
            currentSubmoduleFunctions += irFunction.name
        }
    }

    internal fun effectiveConstraints(): Set<IrCategory>? =
        currentSubmoduleConstraints ?: allowedCategories

    // --- Class builders ---

    /**
     * Create a [ClassBuilder] with the given scope type for building a class definition
     * with scoped methods.
     *
     * ```java
     * ClassBuilder<NativeScope> point = ir.createClass(NativeScope.class, "Point");
     * point.field("x", Type.F64);
     * // ... create functions ...
     * ClassDefinition pointDef = point.build();
     * ```
     *
     * @param T the instruction set scope for all functions created from this class
     * @param scope the scope class (Java API)
     * @param name the class name
     * @return a [ClassBuilder] ready for field and method definitions
     */
    fun <T : InstructionSet> createClass(scope: Class<T>, name: String): ClassBuilder<T> {
        return ClassBuilder(this, scope, name)
    }

    /**
     * Create a [ClassBuilder] with reified scope type (Kotlin API).
     *
     * ```kotlin
     * val point = ir.createClass<NativeScope>("Point")
     * ```
     */
    inline fun <reified T : InstructionSet> createClass(name: String): ClassBuilder<T> {
        return createClass(T::class.java, name)
    }

    /**
     * Create a class definition using a block that auto-builds on exit.
     *
     * ```java
     * ir.defineClass(NativeScope.class, "Point", point -> {
     *     point.field("x", Type.F64);
     *     point.defineFunction("getX", params, Type.F64, fn -> { ... });
     * });
     * ```
     *
     * @param T the instruction set scope
     * @param scope the scope class (Java API)
     * @param name the class name
     * @param block the builder block — class is auto-built when the block exits
     * @return the constructed [ClassDefinition]
     */
    fun <T : InstructionSet> defineClass(
        scope: Class<T>, name: String, block: java.util.function.Consumer<ClassBuilder<T>>,
    ): ClassDefinition {
        val builder = createClass(scope, name)
        try {
            block.accept(builder)
        } finally {
            if (!builder.isBuilt()) {
                builder.build()
            }
        }
        return findClass(name)!!
    }

    /**
     * Create a class definition using a block (Kotlin API with reified type).
     *
     * ```kotlin
     * ir.defineClass<NativeScope>("Point") { point ->
     *     point.field("x", Type.F64)
     * }
     * ```
     */
    inline fun <reified T : InstructionSet> defineClass(
        name: String, block: java.util.function.Consumer<ClassBuilder<T>>,
    ): ClassDefinition {
        return defineClass(T::class.java, name, block)
    }

    /**
     * Declare an external function with no body, making it callable from within this module.
     *
     * The declaration is added to the module's function list with `isExternal = true` and no
     * basic blocks. The linker is expected to resolve the symbol from another object file or
     * shared library. Use the returned [FunctionRef] as the target of [InstructionEmitter.call]
     * or [InstructionEmitter.invoke].
     *
     * ```java
     * // Java — declare printf from libc
     * FunctionRef printf = ir.declareFunction(
     *     "printf",
     *     List.of(Param.of("fmt", Type.OpaquePointer)),
     *     Type.I32,
     *     Linkage.EXTERNAL,
     *     CallingConvention.C,
     *     /*isVarArg=*/ true
     * );
     *
     * ir.call(printf, List.of(fmtPtr), Type.I32);
     * ```
     *
     * @param name the external symbol name (must match the linker-visible name exactly)
     * @param params the declared parameter list; used to build the [Type.Function] type
     * @param returnType the declared return type
     * @param linkage typically [Linkage.EXTERNAL] for imported symbols
     * @param callingConv the calling convention the external function uses
     * @param isVarArg `true` if the external function is variadic (e.g., `printf`)
     * @return a [FunctionRef] pointing to the declared symbol, ready to be passed to [InstructionEmitter.call]
     */
    @JvmOverloads fun declareFunction(
        name: String, params: List<Param>, returnType: Type,
        linkage: Linkage = Linkage.EXTERNAL, callingConv: CallingConvention = CallingConvention.C,
        isVarArg: Boolean = false,
    ): FunctionRef {
        val paramValues = params.mapIndexed { i, p -> Parameter(p.name, p.type, i) }
        functions += IrFunction(name, paramValues, returnType, emptyList(), isExternal = true, linkage = linkage, callingConv = callingConv, isVarArg = isVarArg)
        if (currentSubmoduleName != null) currentSubmoduleFunctions += name
        return FunctionRef(name, Type.Function(params.map { it.type }, returnType, isVarArg))
    }

    /**
     * Seal the current function and add it to the module's function list.
     *
     * All basic blocks accumulated since the last [createFunction] call are frozen into
     * immutable [BasicBlock] instances and the resulting [IrFunction] is appended to the module.
     * After this call the builder's per-function state is cleared and a new function can be started
     * with [createFunction] or [function].
     *
     * Every call to [createFunction] must have a matching call to [finalizeFunction] before [build]
     * is called. If you are using [function]/[FunctionBuilder], the scope's [FunctionBuilder.end] method
     * calls this automatically.
     *
     * @throws IllegalStateException if no function is currently in progress (i.e., [createFunction]
     *   has not been called, or [finalizeFunction] has already been called for the current function)
     */
    fun finalizeFunction() {
        val name = currentFunctionName ?: error("No function in progress")
        val ctx = currentContext
        if (ctx != null) {
            val irFunction = ctx.finalize()
            functions += irFunction
        } else {
            val meta = currentFunctionMeta
            val blocks = blockInstructions.map { (label, instrs) -> BasicBlock(label, instrs) }
            functions += IrFunction(
                name, currentParams, currentReturnType, blocks,
                isExternal = false, meta.linkage, meta.visibility, meta.callingConv,
                meta.attributes, meta.section, meta.align, meta.gc, meta.isVarArg,
                personality = meta.personality,
            )
        }
        if (currentSubmoduleName != null) { currentSubmoduleFunctions += name }
        currentFunctionName = null
        currentParams = emptyList()
        currentContext = null
        blockInstructions = linkedMapOf()
        insertPoint = null
    }

    /**
     * Create a [FunctionRef] that can be used as the callee argument of [InstructionEmitter.call]
     * or [InstructionEmitter.invoke].
     *
     * This is a lightweight factory for building a typed reference to a function by name without
     * declaring it first via [declareFunction]. Use it when the callee is known to exist at link
     * time but you do not want to record a formal declaration in the module (e.g., when calling
     * a runtime-injected helper function whose signature is guaranteed by convention).
     *
     * @param name the symbol name of the function to reference
     * @param type the function's type, including parameter types, return type, and vararg flag
     * @return a [FunctionRef] value whose type is [type]
     */
    fun functionRef(name: String, type: Type.Function): FunctionRef = FunctionRef(name, type)

    // --- Block management ---

    /**
     * Allocate a named block as a forward reference without positioning the insertion point.
     *
     * The block is registered with an empty instruction list and can be used immediately as a
     * branch target in terminator instructions (`condBr`, `br`, `switch`). Position it later
     * with [appendBlock] when ready to emit instructions into it.
     *
     * ```java
     * BlockRef merge = ir.createBlock("merge");
     * // ... emit predecessors that branch to merge ...
     * ir.appendBlock(merge);  // now position at merge and emit its body
     * ```
     *
     * @param label the block's label
     * @return a [BlockRef] for use in terminators and later calls to [appendBlock]
     */
    fun createBlock(label: String): BlockRef {
        val ctx = currentContext
        if (ctx != null) {
            return ctx.createBlock(label)
        }
        blockInstructions.getOrPut(label) { mutableListOf() }
        return BlockRef(label)
    }

    /**
     * Allocate an anonymous block as a forward reference without positioning.
     *
     * The block receives an auto-generated name (`bb0`, `bb1`, `bb2`, ...).
     *
     * @return a [BlockRef] with an auto-generated label
     */
    fun createBlock(): BlockRef {
        val ctx = currentContext
        if (ctx != null) {
            return ctx.createBlock()
        }
        val label = "bb${nextBlockId++}"
        blockInstructions.getOrPut(label) { mutableListOf() }
        return BlockRef(label)
    }

    /**
     * Create a named block and position the insertion point at its end.
     *
     * Equivalent to calling [createBlock] followed by positioning at the new block.
     * All subsequent instruction emission goes into this block until the next [appendBlock] call.
     *
     * ```java
     * BlockRef entry = ir.appendBlock("entry");
     * b.add(x, y);  // emitted into "entry"
     * ```
     *
     * @param label the block's label
     * @return a [BlockRef] for the newly created (or existing) block
     */
    fun appendBlock(label: String): BlockRef {
        val ctx = currentContext
        if (ctx != null) {
            ctx.appendBlock(label)
        } else {
            blockInstructions.getOrPut(label) { mutableListOf() }
            insertPoint = label
        }
        return BlockRef(label)
    }

    /**
     * Create an anonymous block and position the insertion point at its end.
     *
     * The block receives an auto-generated name (`bb0`, `bb1`, `bb2`, ...).
     *
     * @return a [BlockRef] for the newly created block
     */
    fun appendBlock(): BlockRef {
        val ctx = currentContext
        return if (ctx != null) {
            val label = ctx.appendBlock()
            BlockRef(label)
        } else {
            val label = "bb${nextBlockId++}"
            blockInstructions.getOrPut(label) { mutableListOf() }
            insertPoint = label
            BlockRef(label)
        }
    }

    /**
     * Switch the insertion point to an existing block.
     *
     * Use this to resume emitting instructions into a block that was previously created
     * with [createBlock] or an earlier [appendBlock] call.
     *
     * ```java
     * BlockRef merge = ir.createBlock("merge");
     * // ... emit predecessors ...
     * ir.appendBlock(merge);  // position at merge
     * Value result = b.phi(Type.I32, ...);
     * ```
     *
     * @param block the block to position at
     */
    fun appendBlock(block: BlockRef) {
        val ctx = currentContext
        if (ctx != null) {
            ctx.appendBlock(block)
        } else {
            blockInstructions.getOrPut(block.label) { mutableListOf() }
            insertPoint = block.label
        }
    }

    /** Allocate a block as a forward reference without positioning (alias for [createBlock]). */
    fun label(): BlockRef = createBlock()

    /** Allocate a named block as a forward reference without positioning (alias for [createBlock]). */
    fun label(name: String): BlockRef = createBlock(name)

    /** Allocate a block and position the insertion point at it (alias for [appendBlock]). */
    fun mark(): BlockRef = appendBlock()

    /** Allocate a named block and position the insertion point at it (alias for [appendBlock]). */
    fun mark(name: String): BlockRef = appendBlock(name)

    /** Position the insertion point at an existing block (alias for [appendBlock]). */
    fun mark(block: BlockRef) = appendBlock(block)

    /**
     * Return the label of the block that is currently receiving instructions, or `null` if no
     * insertion point has been set for the current function.
     *
     * @return the current insertion block label, or `null`
     */
    fun getInsertBlock(): String? = currentContext?.getInsertPoint() ?: insertPoint

    // --- Instruction builder factory ---

    /**
     * Create a typed instruction builder scoped to the given instruction set interface.
     *
     * The returned builder exposes only the instruction methods declared by the scope interface
     * and its super-interfaces. This prevents accidental use of instructions that don't belong
     * in the current compilation context — e.g., a native backend scope won't expose object
     * allocation instructions.
     *
     * The builder shares this [ModuleBuilder]'s instruction sink — instructions go into whatever
     * block is currently positioned. Create once and reuse across all functions in the module.
     *
     * ```java
     * NativeScope b = ir.createInstructionBuilder(NativeScope.class);
     * b.add(x, y);      // allowed — ArithmeticInstructionSet
     * b.load(ptr);       // allowed — MemoryInstructionSet
     * b.newObject("Foo"); // compile error — not in NativeScope
     * ```
     *
     * @param T the scope interface type (e.g., [org.kgen.ir.build.scope.NativeScope])
     * @param scope the scope interface class
     * @return a typed instruction builder implementing the scope
     */
    fun <T : InstructionSet> createInstructionBuilder(scope: Class<T>): T {
        return InstructionBuilder.create(scope, this)
    }

    /**
     * Create a typed instruction builder (Kotlin reified overload).
     *
     * ```kotlin
     * val b = ir.createInstructionBuilder<NativeScope>()
     * b.add(x, y)
     * b.load(ptr)
     * ```
     *
     * @param T the scope interface type
     * @return a typed instruction builder implementing the scope
     */
    inline fun <reified T : InstructionSet> createInstructionBuilder(): T {
        return InstructionBuilder.create(this)
    }

    // --- Parameter access ---

    /**
     * Return the [Parameter] at the given zero-based index in the current function's parameter list.
     *
     * The returned [Parameter] is a [Value] that can be passed directly to instruction-emission
     * methods. This is equivalent to indexing the list returned by [createFunction].
     *
     * @param index zero-based parameter position
     * @return the corresponding [Parameter] value
     * @throws IndexOutOfBoundsException if [index] is out of range
     */
    fun param(index: Int): Parameter = currentParams[index]

    /**
     * The number of parameters declared for the function currently being built.
     *
     * Valid only while a function is in progress (between [createFunction] and [finalizeFunction]).
     * Returns `0` outside of a function scope.
     */
    val paramCount: Int get() = currentParams.size

    // --- Build ---

    /**
     * Finalize the module and return the completed, immutable [Module].
     *
     * All functions, globals, type definitions, submodules, metadata, and module flags accumulated
     * since this builder was constructed are assembled into a single [Module] value object. The
     * builder remains usable after this call — invoking [build] again produces a new snapshot of
     * the current state, though modifying the builder after the first [build] call is uncommon.
     *
     * Before calling this method:
     * - Every [createFunction] call must have a matching [finalizeFunction] call (or be wrapped in a
     *   [FunctionBuilder] whose [FunctionBuilder.end] has been invoked).
     * - Every [beginSubmodule] call must have a matching [endSubmodule] call.
     *
     * The returned [Module] is the input to [org.kgen.codegen.CodeGenerator] for native code
     * generation, or to the JVM/WASM module writers for managed output.
     *
     * @return the assembled [Module] containing all registered functions, globals, and type defs
     * @throws IllegalStateException if a submodule scope is still open (i.e., [endSubmodule] was
     *   not called after the last [beginSubmodule])
     */
    fun build(): Module {
        check(currentSubmoduleName == null) { "Submodule '$currentSubmoduleName' still open. Call endSubmodule() first." }
        return Module(
            name = moduleName, targetTriple = targetTriple, dataLayout = dataLayout,
            functions = functions, globals = globals, structs = structs, classes = classes,
            interfaces = interfaces, enums = enums, aliases = aliases, metadata = metadata,
            sourceFile = sourceFile, targetFeatures = targetFeatures, globalCtors = globalCtors,
            globalDtors = globalDtors, ifuncs = ifuncs, comdats = comdats,
            moduleInlineAsm = moduleInlineAsm, moduleFlags = moduleFlags,
            constraints = allowedCategories, submodules = submodules.toList(),
            profile = TargetProfile.entries.firstOrNull { it.constraints == allowedCategories },
        )
    }
}
