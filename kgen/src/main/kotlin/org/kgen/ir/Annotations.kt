package org.kgen.ir

/** An annotation on a class, method, field, or parameter. */
data class AnnotationDefinition(
    val type: String,
    val values: Map<String, AnnotationValue> = emptyMap(),
    val retention: AnnotationRetention = AnnotationRetention.RUNTIME,
)

/** A value within an [AnnotationDefinition]. */
sealed interface AnnotationValue {
    data class StringVal(val value: String) : AnnotationValue
    data class IntVal(val value: Long) : AnnotationValue
    data class FloatVal(val value: Double) : AnnotationValue
    data class BoolVal(val value: Boolean) : AnnotationValue
    data class EnumVal(val type: String, val name: String) : AnnotationValue
    data class ClassVal(val type: Type) : AnnotationValue
    data class ArrayVal(val values: List<AnnotationValue>) : AnnotationValue
    data class AnnotationVal(val annotation: AnnotationDefinition) : AnnotationValue
}

/** When the annotation is retained/visible. */
enum class AnnotationRetention {
    SOURCE, CLASS, RUNTIME,
}
