package org.kgen.reflect

/**
 * A custom attribute (CLR) or annotation (JVM) attached to a type, method, field, etc.
 */
class AttributeInfo(
    private val name: String,
    private val type: TypeRef? = null,
    private val ctorArgs: List<Any?> = emptyList(),
    private val namedArgs: Map<String, Any?> = emptyMap(),
) {
    fun name(): String = name
    fun type(): TypeRef? = type
    fun constructorArguments(): List<Any?> = ctorArgs
    fun arguments(): Map<String, Any?> = namedArgs

    override fun toString(): String = "[$name]"
}
