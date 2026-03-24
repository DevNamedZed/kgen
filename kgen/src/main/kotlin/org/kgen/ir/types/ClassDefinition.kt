package org.kgen.ir.types

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/** DSL entry point: `classDef("MyClass") { extends("Base"); field("x", Type.I32) }`. */
fun classDef(name: String, body: ClassDefinitionBuilder.() -> Unit): ClassDefinition =
    ClassDefinitionBuilder(name).apply(body).build()

/** DSL entry point: `interfaceDef("Drawable") { method(...) }`. */
fun interfaceDef(name: String, body: InterfaceDefinitionBuilder.() -> Unit): InterfaceDefinition =
    InterfaceDefinitionBuilder(name).apply(body).build()

/** High-level class definition for managed backends (JVM, WASM-GC). */
data class ClassDefinition(
    val name: String,
    val superClass: String? = null,
    val interfaces: List<String> = emptyList(),
    val fields: List<FieldDefinition> = emptyList(),
    val methods: List<MethodDefinition> = emptyList(),
    val constructors: List<MethodDefinition> = emptyList(),
    val staticFields: List<FieldDefinition> = emptyList(),
    val staticMethods: List<MethodDefinition> = emptyList(),
    val typeParams: List<TypeParamDefinition> = emptyList(),
    val isAbstract: Boolean = false,
    val isFinal: Boolean = false,
    val visibility: ClassVisibility = ClassVisibility.PUBLIC,
    val annotations: List<AnnotationDefinition> = emptyList(),
    val innerClasses: List<InnerClassRef> = emptyList(),
    val sourceFile: String? = null,
)

/** A field in a class or interface. */
data class FieldDefinition(
    val name: String,
    val type: Type,
    val visibility: MemberVisibility = MemberVisibility.PRIVATE,
    val isFinal: Boolean = false,
    val isVolatile: Boolean = false,
    val isTransient: Boolean = false,
    val initializer: Constant? = null,
    val annotations: List<AnnotationDefinition> = emptyList(),
    val offset: Int? = null,
)

/** A method in a class or interface. */
data class MethodDefinition(
    val name: String,
    val params: List<Param>,
    val returnType: Type,
    val body: List<BasicBlock> = emptyList(),
    val visibility: MemberVisibility = MemberVisibility.PUBLIC,
    val isAbstract: Boolean = false,
    val isFinal: Boolean = false,
    val isStatic: Boolean = false,
    val isSynchronized: Boolean = false,
    val isNative: Boolean = false,
    val isVarArg: Boolean = false,
    val typeParams: List<TypeParamDefinition> = emptyList(),
    val annotations: List<AnnotationDefinition> = emptyList(),
    val overrides: MethodRef? = null,
    val defaultImpl: Boolean = false,
)

/** Reference to a method, used for override tracking. */
data class MethodRef(
    val className: String,
    val methodName: String,
    val methodType: Type.Function,
)

/** Reference to an inner/nested class. */
data class InnerClassRef(
    val name: String,
    val outerClass: String,
    val visibility: ClassVisibility = ClassVisibility.PUBLIC,
    val isStatic: Boolean = false,
)

/**
 * Builder for [ClassDefinition] instances.
 */
class ClassDefinitionBuilder(private val name: String) {
    private var superClass: String? = null
    private val interfaces = mutableListOf<String>()
    private val fields = mutableListOf<FieldDefinition>()
    private val methods = mutableListOf<MethodDefinition>()
    private val constructors = mutableListOf<MethodDefinition>()
    private val staticFields = mutableListOf<FieldDefinition>()
    private val staticMethods = mutableListOf<MethodDefinition>()
    private val typeParams = mutableListOf<TypeParamDefinition>()
    private var isAbstract = false
    private var isFinal = false
    private var visibility = ClassVisibility.PUBLIC
    private val annotations = mutableListOf<AnnotationDefinition>()
    private var sourceFile: String? = null

    fun extends(superClass: String): ClassDefinitionBuilder { this.superClass = superClass; return this }
    fun implements(iface: String): ClassDefinitionBuilder { interfaces += iface; return this }
    fun isAbstract(value: Boolean = true): ClassDefinitionBuilder { isAbstract = value; return this }
    fun isFinal(value: Boolean = true): ClassDefinitionBuilder { isFinal = value; return this }
    fun visibility(vis: ClassVisibility): ClassDefinitionBuilder { visibility = vis; return this }
    fun sourceFile(file: String): ClassDefinitionBuilder { sourceFile = file; return this }
    fun annotation(ann: AnnotationDefinition): ClassDefinitionBuilder { annotations += ann; return this }
    fun typeParam(name: String, index: Int, upperBounds: List<Type> = emptyList()): ClassDefinitionBuilder {
        typeParams += TypeParamDefinition(name, index, upperBounds); return this
    }

    fun field(
        name: String, type: Type, visibility: MemberVisibility = MemberVisibility.PRIVATE,
        isFinal: Boolean = false, isVolatile: Boolean = false, initializer: Constant? = null,
    ): ClassDefinitionBuilder {
        fields += FieldDefinition(name, type, visibility, isFinal, isVolatile, initializer = initializer)
        return this
    }

    fun staticField(
        name: String, type: Type, visibility: MemberVisibility = MemberVisibility.PUBLIC,
        isFinal: Boolean = false, initializer: Constant? = null,
    ): ClassDefinitionBuilder {
        staticFields += FieldDefinition(name, type, visibility, isFinal, initializer = initializer)
        return this
    }

    fun method(method: MethodDefinition): ClassDefinitionBuilder { methods += method; return this }
    fun constructor(ctor: MethodDefinition): ClassDefinitionBuilder { constructors += ctor; return this }
    fun staticMethod(method: MethodDefinition): ClassDefinitionBuilder { staticMethods += method; return this }

    fun build(): ClassDefinition = ClassDefinition(
        name, superClass, interfaces, fields, methods, constructors,
        staticFields, staticMethods, typeParams, isAbstract, isFinal,
        visibility, annotations, sourceFile = sourceFile,
    )
}
