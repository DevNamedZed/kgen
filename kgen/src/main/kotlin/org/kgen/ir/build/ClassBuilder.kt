package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.build.sets.InstructionBuilder
import org.kgen.ir.build.sets.InstructionSet
import org.kgen.ir.types.ClassDefinition
import org.kgen.ir.types.FieldDefinition
import org.kgen.ir.types.MethodDefinition
import org.kgen.ir.types.Operator
import java.util.function.Consumer

/**
 * Builder for a class definition with scoped method creation.
 *
 * The scope type parameter [T] determines which instruction sets and extensions
 * are available in all functions created from this builder. Scope flows from
 * class to function.
 *
 * ```java
 * // Java
 * ClassBuilder<NativeScope> point = module.createClass(NativeScope.class, "Point");
 * point.field("x", Type.F64);
 * point.defineFunction("getX",
 *     List.of(Param.of("self", Type.OpaquePointer)), Type.F64, fn -> {
 *         NativeScope ins = fn.instructions();
 *         fn.ret(ins.load(fn.param(0), Type.F64));
 *     });
 * ClassDefinition pointDef = point.build();
 * ```
 *
 * Created by [ModuleBuilder.createClass] or the `defineClass` block-style factory.
 *
 * @param T the instruction set scope — determines what instructions are available in methods
 */
class ClassBuilder<T : InstructionSet> internal constructor(
    private val irBuilder: ModuleBuilder,
    private val scopeClass: Class<T>,
    val name: String,
) : AutoCloseable {

    private var superClass: String? = null
    private val interfaces = mutableListOf<String>()
    private val fields = mutableListOf<FieldDefinition>()
    private val staticFields = mutableListOf<FieldDefinition>()
    private val instanceMethods = mutableListOf<MethodDefinition>()
    private val staticMethods = mutableListOf<MethodDefinition>()
    private val constructors = mutableListOf<MethodDefinition>()
    private var isAbstract = false
    private var isFinal = false
    private var visibility = ClassVisibility.PUBLIC
    private val annotations = mutableListOf<AnnotationDefinition>()
    private var sourceFile: String? = null
    private var built = false

    // --- Metadata (chainable) ---

    fun extends(superClass: String): ClassBuilder<T> {
        this.superClass = superClass
        return this
    }

    fun implements(iface: String): ClassBuilder<T> {
        interfaces += iface
        return this
    }

    fun isAbstract(value: Boolean = true): ClassBuilder<T> {
        isAbstract = value
        return this
    }

    fun isFinal(value: Boolean = true): ClassBuilder<T> {
        isFinal = value
        return this
    }

    fun visibility(visibility: ClassVisibility): ClassBuilder<T> {
        this.visibility = visibility
        return this
    }

    fun sourceFile(file: String): ClassBuilder<T> {
        sourceFile = file
        return this
    }

    fun annotation(annotation: AnnotationDefinition): ClassBuilder<T> {
        annotations += annotation
        return this
    }

    // --- Fields (chainable) ---

    @JvmOverloads
    fun field(
        name: String,
        type: Type,
        visibility: MemberVisibility = MemberVisibility.PRIVATE,
        isFinal: Boolean = false,
        isVolatile: Boolean = false,
        initializer: Constant? = null,
    ): ClassBuilder<T> {
        fields += FieldDefinition(name, type, visibility, isFinal, isVolatile, initializer = initializer)
        return this
    }

    @JvmOverloads
    fun staticField(
        name: String,
        type: Type,
        visibility: MemberVisibility = MemberVisibility.PUBLIC,
        isFinal: Boolean = false,
        initializer: Constant? = null,
    ): ClassBuilder<T> {
        staticFields += FieldDefinition(name, type, visibility, isFinal, initializer = initializer)
        return this
    }

    // --- Block-style function creation (define*) — safe lifecycle ---

    fun defineFunction(
        name: String,
        params: List<Param>,
        returnType: Type,
        block: Consumer<FunctionBuilder<T>>,
    ) {
        val functionBuilder = createFunction(name, params, returnType)
        try {
            block.accept(functionBuilder)
        } finally {
            functionBuilder.end()
        }
    }

    fun defineStaticFunction(
        name: String,
        params: List<Param>,
        returnType: Type,
        block: Consumer<FunctionBuilder<T>>,
    ) {
        val functionBuilder = createStaticFunction(name, params, returnType)
        try {
            block.accept(functionBuilder)
        } finally {
            functionBuilder.end()
        }
    }

    fun defineConstructor(
        params: List<Param>,
        block: Consumer<FunctionBuilder<T>>,
    ) {
        val functionBuilder = createConstructor(params)
        try {
            block.accept(functionBuilder)
        } finally {
            functionBuilder.end()
        }
    }

    // --- Imperative function creation (create*) — manual lifecycle ---

    fun createFunction(name: String, params: List<Param>, returnType: Type): FunctionBuilder<T> {
        val mangledName = "${this.name}_$name"
        instanceMethods += buildMethodDefinition(name, params, returnType, isStatic = false)
        return createFunctionBuilder(mangledName, params, returnType)
    }

    fun createStaticFunction(name: String, params: List<Param>, returnType: Type): FunctionBuilder<T> {
        val mangledName = "${this.name}_$name"
        staticMethods += buildMethodDefinition(name, params, returnType, isStatic = true)
        return createFunctionBuilder(mangledName, params, returnType)
    }

    fun createConstructor(params: List<Param>): FunctionBuilder<T> {
        val mangledName = "${name}_init"
        constructors += buildMethodDefinition("<init>", params, Type.Void, isStatic = false)
        return createFunctionBuilder(mangledName, params, Type.Void)
    }

    fun createOperator(operator: Operator, params: List<Param>, returnType: Type): FunctionBuilder<T> {
        val mangledName = "${name}_${operator.methodName}"
        instanceMethods += buildMethodDefinition(operator.methodName, params, returnType, isStatic = false)
        return createFunctionBuilder(mangledName, params, returnType)
    }

    fun createGetter(propertyName: String, type: Type): FunctionBuilder<T> {
        val mangledName = "${name}_get_$propertyName"
        instanceMethods += buildMethodDefinition("get_$propertyName", listOf(), type, isStatic = false)
        return createFunctionBuilder(mangledName, listOf(), type)
    }

    fun createSetter(propertyName: String, type: Type): FunctionBuilder<T> {
        val params = listOf(Param("value", type))
        val mangledName = "${name}_set_$propertyName"
        instanceMethods += buildMethodDefinition("set_$propertyName", params, Type.Void, isStatic = false)
        return createFunctionBuilder(mangledName, params, Type.Void)
    }

    // --- Block-style operator/getter/setter ---

    fun defineOperator(operator: Operator, params: List<Param>, returnType: Type, block: Consumer<FunctionBuilder<T>>) {
        val functionBuilder = createOperator(operator, params, returnType)
        try { block.accept(functionBuilder) } finally { functionBuilder.end() }
    }

    fun defineGetter(propertyName: String, type: Type, block: Consumer<FunctionBuilder<T>>) {
        val functionBuilder = createGetter(propertyName, type)
        try { block.accept(functionBuilder) } finally { functionBuilder.end() }
    }

    fun defineSetter(propertyName: String, type: Type, block: Consumer<FunctionBuilder<T>>) {
        val functionBuilder = createSetter(propertyName, type)
        try { block.accept(functionBuilder) } finally { functionBuilder.end() }
    }

    private fun createFunctionBuilder(mangledName: String, params: List<Param>, returnType: Type): FunctionBuilder<T> {
        val parameters = params.mapIndexed { i, p -> Parameter(p.name, p.type, i) }
        val context = FunctionContext(mangledName, parameters, returnType, irBuilder.effectiveConstraints())
        context.appendBlock("entry")
        return FunctionBuilder(irBuilder, context, scopeClass)
    }

    // --- Build ---

    fun isBuilt(): Boolean = built

    fun build(): ClassDefinition {
        check(!built) { "ClassBuilder for '$name' has already been built" }
        built = true
        val classDef = ClassDefinition(
            name = name,
            superClass = superClass,
            interfaces = interfaces.toList(),
            fields = fields.toList(),
            methods = instanceMethods.toList(),
            constructors = constructors.toList(),
            staticFields = staticFields.toList(),
            staticMethods = staticMethods.toList(),
            isAbstract = isAbstract,
            isFinal = isFinal,
            visibility = visibility,
            annotations = annotations.toList(),
            sourceFile = sourceFile,
        )
        irBuilder.addClass(classDef)
        return classDef
    }

    override fun close() {
        if (!built) {
            build()
        }
    }

    private fun buildMethodDefinition(
        name: String,
        params: List<Param>,
        returnType: Type,
        isStatic: Boolean,
    ): MethodDefinition {
        return MethodDefinition(
            name = name,
            params = params,
            returnType = returnType,
            isStatic = isStatic,
        )
    }
}
