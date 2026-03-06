package org.kgen.ir

/**
 * A named, typed parameter or field entry. Used in function signatures, method definitions,
 * struct fields, and enum variant fields.
 *
 * ```java
 * // Java
 * ir.function("main", List.of(new Param("argc", Type.I32)), Type.I32);
 *
 * // Kotlin
 * ir.function("main", listOf(Param("argc", Type.I32)), Type.I32)
 * ```
 */
data class Param(val name: String, val type: Type) {
    companion object {
        /** Factory for Java callers: `Param.of("name", Type.I32)`. */
        @JvmStatic
        fun of(name: String, type: Type) = Param(name, type)
    }
}
