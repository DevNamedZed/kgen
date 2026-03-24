package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Target
import org.kgen.ir.types.*

/**
 * DSL entry point: `module("name", Target.wasm()) { ... }`.
 */
fun module(name: String, target: Target = Target.wasm(), body: DslModuleBuilder.() -> Unit): Module =
    DslModuleBuilder(name, target).apply(body).build()

/** Kotlin convenience aliases for [Type] constant factories. */
fun i1(value: Boolean) = Type.i1(value)
fun i8(value: Int) = Type.i8(value)
fun i16(value: Int) = Type.i16(value)
fun i32(value: Int) = Type.i32(value)
fun i64(value: Long) = Type.i64(value)
fun i128(value: Long) = Type.i128(value)
fun f16(value: Float) = Type.f16(value)
fun bf16(value: Float) = Type.bf16(value)
fun f32(value: Float) = Type.f32(value)
fun f64(value: Double) = Type.f64(value)
fun f80(value: Double) = Constant.F80(value)
fun f128(value: Double) = Constant.F128(value)

/**
 * Builds a [Module] using the DSL.
 */
class DslModuleBuilder(private val name: String, private val target: Target = Target.wasm()) {
    private val functions = mutableListOf<IrFunction>()
    private val globals = mutableListOf<Global>()
    private val structs = mutableListOf<StructDefinition>()
    private val classes = mutableListOf<ClassDefinition>()
    private val interfaces = mutableListOf<InterfaceDefinition>()
    private val enums = mutableListOf<EnumDefinition>()
    private val aliases = mutableListOf<TypeAlias>()
    private val metadata = mutableMapOf<String, MetadataValue>()
    private var targetTriple: String? = null
    private var dataLayout: String? = null
    private var sourceFile: String? = null
    private val targetFeatures = mutableSetOf<String>()
    private val globalCtors = mutableListOf<GlobalCtor>()
    private val globalDtors = mutableListOf<GlobalCtor>()
    private val ifuncs = mutableListOf<IFunc>()
    private val comdats = mutableListOf<ComdatDefinition>()
    private var moduleInlineAsm: String? = null
    private val moduleFlags = mutableMapOf<String, ModuleFlagValue>()

    fun targetTriple(triple: String): DslModuleBuilder { targetTriple = triple; return this }
    fun dataLayout(layout: String): DslModuleBuilder { dataLayout = layout; return this }
    fun sourceFile(file: String): DslModuleBuilder { sourceFile = file; return this }
    fun targetFeature(feature: String): DslModuleBuilder { targetFeatures += feature; return this }
    fun moduleInlineAsm(asm: String): DslModuleBuilder { moduleInlineAsm = asm; return this }

    fun globalCtor(function: String, priority: Int = 65535): DslModuleBuilder { globalCtors += GlobalCtor(function, priority); return this }
    fun globalDtor(function: String, priority: Int = 65535): DslModuleBuilder { globalDtors += GlobalCtor(function, priority); return this }
    fun ifunc(name: String, resolver: String, type: Type.Function): DslModuleBuilder { ifuncs += IFunc(name, resolver, type); return this }
    fun comdat(name: String, kind: ComdatSelectionKind): DslModuleBuilder { comdats += ComdatDefinition(name, kind); return this }
    fun moduleFlag(key: String, value: ModuleFlagValue): DslModuleBuilder { moduleFlags[key] = value; return this }

    fun function(
        name: String,
        params: List<Param>,
        returnType: Type,
        isExternal: Boolean = false,
        linkage: Linkage = Linkage.EXTERNAL,
        visibility: Visibility = Visibility.DEFAULT,
        callingConv: CallingConvention = CallingConvention.C,
        attributes: Set<FnAttribute> = emptySet(),
        isVarArg: Boolean = false,
        section: String? = null,
        align: Int? = null,
        gc: String? = null,
        personality: FunctionRef? = null,
        body: DslFunctionBuilder.() -> Unit = {},
    ): DslModuleBuilder {
        val paramValues = params.mapIndexed { i, p -> Parameter(p.name, p.type, i) }
        val builder = DslFunctionBuilder(paramValues)
        if (!isExternal) builder.body()
        functions += IrFunction(
            name, paramValues, returnType, builder.build(),
            isExternal, linkage, visibility, callingConv,
            attributes, section, align, gc, isVarArg,
            personality = personality,
        )
        return this
    }

    fun global(
        name: String, type: Type, initializer: Constant? = null, isConstant: Boolean = false,
        linkage: Linkage = Linkage.EXTERNAL, visibility: Visibility = Visibility.DEFAULT,
        threadLocal: ThreadLocalMode? = null, section: String? = null,
        align: Int? = null, addressSpace: Int = 0,
    ): DslModuleBuilder {
        globals += Global(name, type, initializer, isConstant, linkage, visibility, threadLocal, section, align, addressSpace)
        return this
    }

    fun struct(name: String, fields: List<Param>, packed: Boolean = false, align: Int? = null): DslModuleBuilder {
        structs += StructDefinition(name, fields, packed, align); return this
    }

    fun classDef(cls: ClassDefinition): DslModuleBuilder { classes += cls; return this }
    fun interfaceDef(iface: InterfaceDefinition): DslModuleBuilder { interfaces += iface; return this }
    fun enumDef(enum: EnumDefinition): DslModuleBuilder { enums += enum; return this }
    fun typeAlias(name: String, type: Type): DslModuleBuilder { aliases += TypeAlias(name, type); return this }
    fun metadata(key: String, value: MetadataValue): DslModuleBuilder { metadata[key] = value; return this }

    fun build(): Module = Module(
        name, targetTriple, dataLayout, functions, globals, structs, classes, interfaces,
        enums, aliases, metadata, sourceFile, targetFeatures, globalCtors, globalDtors,
        ifuncs, comdats, moduleInlineAsm, moduleFlags,
    )
}

/**
 * Builds a function's block list. Used within [DslModuleBuilder.function].
 */
class DslFunctionBuilder(private val params: List<Parameter>) {
    private val blocks = mutableListOf<BasicBlock>()
    private var nextId = 0

    fun param(index: Int): Parameter = params[index]
    val paramCount: Int get() = params.size

    /** DSL-style block creation: `block("label") { add(a, b); ret(result) }`. */
    fun block(label: String, body: BlockBuilder.() -> Unit): DslFunctionBuilder {
        val builder = BlockBuilder(label) { nextName() }
        builder.body()
        blocks += builder.build()
        return this
    }

    /** Create a standalone block builder for imperative use within the DSL. */
    fun createBlock(label: String): BlockBuilder = BlockBuilder(label) { nextName() }

    /** Add a pre-built block (from [createBlock]). */
    fun addBlock(builder: BlockBuilder): DslFunctionBuilder { blocks += builder.build(); return this }
    fun addBlock(block: BasicBlock): DslFunctionBuilder { blocks += block; return this }

    internal fun nextName(): String = "%${nextId++}"
    fun build(): List<BasicBlock> = blocks
}

/**
 * Emits instructions into a single basic block. Used within [DslFunctionBuilder.block]
 * or standalone via [DslFunctionBuilder.createBlock].
 */
class BlockBuilder(
    private val label: String,
    private val nameGenerator: () -> String,
) : InstructionEmitter() {
    constructor(label: String, func: DslFunctionBuilder) : this(label, { func.nextName() })

    private val instructions = mutableListOf<Instruction>()

    override fun emit(instruction: Instruction) { instructions += instruction }
    override fun nextRef(type: Type) = InstructionRef(nameGenerator(), type)

    fun build(): BasicBlock = BasicBlock(label, instructions)
}
