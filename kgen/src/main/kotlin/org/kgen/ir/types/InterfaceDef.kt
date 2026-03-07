package org.kgen.ir.types

import org.kgen.ir.*

/** High-level interface definition. */
data class InterfaceDef(
    val name: String,
    val superInterfaces: List<String> = emptyList(),
    val methods: List<MethodDef> = emptyList(),
    val staticMethods: List<MethodDef> = emptyList(),
    val constants: List<FieldDef> = emptyList(),
    val typeParams: List<TypeParamDef> = emptyList(),
    val annotations: List<AnnotationDef> = emptyList(),
    val visibility: ClassVisibility = ClassVisibility.PUBLIC,
)

/**
 * Builder for [InterfaceDef] instances.
 */
class InterfaceDefBuilder(private val name: String) {
    private val superInterfaces = mutableListOf<String>()
    private val methods = mutableListOf<MethodDef>()
    private val staticMethods = mutableListOf<MethodDef>()
    private val constants = mutableListOf<FieldDef>()
    private val typeParams = mutableListOf<TypeParamDef>()
    private val annotations = mutableListOf<AnnotationDef>()
    private var visibility = ClassVisibility.PUBLIC

    fun extends(iface: String): InterfaceDefBuilder { superInterfaces += iface; return this }
    fun visibility(vis: ClassVisibility): InterfaceDefBuilder { visibility = vis; return this }
    fun annotation(ann: AnnotationDef): InterfaceDefBuilder { annotations += ann; return this }
    fun typeParam(name: String, index: Int, upperBounds: List<Type> = emptyList()): InterfaceDefBuilder {
        typeParams += TypeParamDef(name, index, upperBounds); return this
    }

    fun method(method: MethodDef): InterfaceDefBuilder { methods += method; return this }
    fun staticMethod(method: MethodDef): InterfaceDefBuilder { staticMethods += method; return this }
    fun constant(name: String, type: Type, initializer: Constant): InterfaceDefBuilder {
        constants += FieldDef(name, type, initializer = initializer); return this
    }

    fun build(): InterfaceDef = InterfaceDef(
        name, superInterfaces, methods, staticMethods, constants,
        typeParams, annotations, visibility,
    )
}
