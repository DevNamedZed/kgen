package org.kgen.reflect

/**
 * Reflection info for an event (CLR).
 */
class EventInfo(
    private val name: String,
    private val eventType: TypeRef,
    private val declaringType: TypeInfo? = null,
    private val addMethod: MethodInfo? = null,
    private val removeMethod: MethodInfo? = null,
    private val raiseMethod: MethodInfo? = null,
    private val attrs: List<AttributeInfo> = emptyList(),
) {
    fun name(): String = name
    fun eventType(): TypeRef = eventType
    fun declaringType(): TypeInfo? = declaringType
    fun addMethod(): MethodInfo? = addMethod
    fun removeMethod(): MethodInfo? = removeMethod
    fun raiseMethod(): MethodInfo? = raiseMethod
    fun attributes(): List<AttributeInfo> = attrs

    override fun toString(): String = "event $name: $eventType"
}
