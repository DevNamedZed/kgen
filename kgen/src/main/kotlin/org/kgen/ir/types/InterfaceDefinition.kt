package org.kgen.ir.types

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/** High-level interface definition. */
data class InterfaceDefinition(
    val name: String,
    val superInterfaces: List<String> = emptyList(),
    val methods: List<MethodDefinition> = emptyList(),
    val staticMethods: List<MethodDefinition> = emptyList(),
    val constants: List<FieldDefinition> = emptyList(),
    val typeParams: List<TypeParamDefinition> = emptyList(),
    val annotations: List<AnnotationDefinition> = emptyList(),
    val visibility: ClassVisibility = ClassVisibility.PUBLIC,
)

/**
 * Builder for [InterfaceDefinition] instances.
 */
class InterfaceDefinitionBuilder(private val name: String) {
    private val superInterfaces = mutableListOf<String>()
    private val methods = mutableListOf<MethodDefinition>()
    private val staticMethods = mutableListOf<MethodDefinition>()
    private val constants = mutableListOf<FieldDefinition>()
    private val typeParams = mutableListOf<TypeParamDefinition>()
    private val annotations = mutableListOf<AnnotationDefinition>()
    private var visibility = ClassVisibility.PUBLIC

    fun extends(iface: String): InterfaceDefinitionBuilder { superInterfaces += iface; return this }
    fun visibility(vis: ClassVisibility): InterfaceDefinitionBuilder { visibility = vis; return this }
    fun annotation(ann: AnnotationDefinition): InterfaceDefinitionBuilder { annotations += ann; return this }
    fun typeParam(name: String, index: Int, upperBounds: List<Type> = emptyList()): InterfaceDefinitionBuilder {
        typeParams += TypeParamDefinition(name, index, upperBounds); return this
    }

    fun method(method: MethodDefinition): InterfaceDefinitionBuilder { methods += method; return this }
    fun staticMethod(method: MethodDefinition): InterfaceDefinitionBuilder { staticMethods += method; return this }
    fun constant(name: String, type: Type, initializer: Constant): InterfaceDefinitionBuilder {
        constants += FieldDefinition(name, type, initializer = initializer); return this
    }

    fun build(): InterfaceDefinition = InterfaceDefinition(
        name, superInterfaces, methods, staticMethods, constants,
        typeParams, annotations, visibility,
    )
}
