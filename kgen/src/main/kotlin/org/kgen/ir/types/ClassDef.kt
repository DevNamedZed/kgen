package org.kgen.ir.types

import org.kgen.ir.*

/** DSL entry point: `classDef("MyClass") { extends("Base"); field("x", Type.I32) }`. */
fun classDef(name: String, body: ClassDefBuilder.() -> Unit): ClassDef =
    ClassDefBuilder(name).apply(body).build()

/** DSL entry point: `interfaceDef("Drawable") { method(...) }`. */
fun interfaceDef(name: String, body: InterfaceDefBuilder.() -> Unit): InterfaceDef =
    InterfaceDefBuilder(name).apply(body).build()

/** High-level class definition for managed backends (JVM, WASM-GC). */
data class ClassDef(
    val name: String,
    val superClass: String? = null,
    val interfaces: List<String> = emptyList(),
    val fields: List<FieldDef> = emptyList(),
    val methods: List<MethodDef> = emptyList(),
    val constructors: List<MethodDef> = emptyList(),
    val staticFields: List<FieldDef> = emptyList(),
    val staticMethods: List<MethodDef> = emptyList(),
    val typeParams: List<TypeParamDef> = emptyList(),
    val isAbstract: Boolean = false,
    val isFinal: Boolean = false,
    val visibility: ClassVisibility = ClassVisibility.PUBLIC,
    val annotations: List<AnnotationDef> = emptyList(),
    val innerClasses: List<InnerClassRef> = emptyList(),
    val sourceFile: String? = null,
)

/** A field in a class or interface. */
data class FieldDef(
    val name: String,
    val type: Type,
    val visibility: MemberVisibility = MemberVisibility.PRIVATE,
    val isFinal: Boolean = false,
    val isVolatile: Boolean = false,
    val isTransient: Boolean = false,
    val initializer: Constant? = null,
    val annotations: List<AnnotationDef> = emptyList(),
    val offset: Int? = null,
)

/** A method in a class or interface. */
data class MethodDef(
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
    val typeParams: List<TypeParamDef> = emptyList(),
    val annotations: List<AnnotationDef> = emptyList(),
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
 * Builder for [ClassDef] instances.
 */
class ClassDefBuilder(private val name: String) {
    private var superClass: String? = null
    private val interfaces = mutableListOf<String>()
    private val fields = mutableListOf<FieldDef>()
    private val methods = mutableListOf<MethodDef>()
    private val constructors = mutableListOf<MethodDef>()
    private val staticFields = mutableListOf<FieldDef>()
    private val staticMethods = mutableListOf<MethodDef>()
    private val typeParams = mutableListOf<TypeParamDef>()
    private var isAbstract = false
    private var isFinal = false
    private var visibility = ClassVisibility.PUBLIC
    private val annotations = mutableListOf<AnnotationDef>()
    private var sourceFile: String? = null

    fun extends(superClass: String): ClassDefBuilder { this.superClass = superClass; return this }
    fun implements(iface: String): ClassDefBuilder { interfaces += iface; return this }
    fun isAbstract(value: Boolean = true): ClassDefBuilder { isAbstract = value; return this }
    fun isFinal(value: Boolean = true): ClassDefBuilder { isFinal = value; return this }
    fun visibility(vis: ClassVisibility): ClassDefBuilder { visibility = vis; return this }
    fun sourceFile(file: String): ClassDefBuilder { sourceFile = file; return this }
    fun annotation(ann: AnnotationDef): ClassDefBuilder { annotations += ann; return this }
    fun typeParam(name: String, index: Int, upperBounds: List<Type> = emptyList()): ClassDefBuilder {
        typeParams += TypeParamDef(name, index, upperBounds); return this
    }

    fun field(
        name: String, type: Type, visibility: MemberVisibility = MemberVisibility.PRIVATE,
        isFinal: Boolean = false, isVolatile: Boolean = false, initializer: Constant? = null,
    ): ClassDefBuilder {
        fields += FieldDef(name, type, visibility, isFinal, isVolatile, initializer = initializer)
        return this
    }

    fun staticField(
        name: String, type: Type, visibility: MemberVisibility = MemberVisibility.PUBLIC,
        isFinal: Boolean = false, initializer: Constant? = null,
    ): ClassDefBuilder {
        staticFields += FieldDef(name, type, visibility, isFinal, initializer = initializer)
        return this
    }

    fun method(method: MethodDef): ClassDefBuilder { methods += method; return this }
    fun constructor(ctor: MethodDef): ClassDefBuilder { constructors += ctor; return this }
    fun staticMethod(method: MethodDef): ClassDefBuilder { staticMethods += method; return this }

    fun build(): ClassDef = ClassDef(
        name, superClass, interfaces, fields, methods, constructors,
        staticFields, staticMethods, typeParams, isAbstract, isFinal,
        visibility, annotations, sourceFile = sourceFile,
    )
}
