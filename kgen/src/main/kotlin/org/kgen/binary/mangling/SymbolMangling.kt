package org.kgen.binary.mangling

import org.kgen.ir.Type

/**
 * Applies symbol name mangling to a function name based on its parameter types.
 *
 * Maps IR [Type] to mangling-scheme-specific type strings, then delegates
 * to [ItaniumMangler] or [MsvcMangler].
 *
 * ```java
 * String mangled = SymbolMangling.mangleFunction(
 *     "add", List.of(Type.I32, Type.I32), Type.I32, ManglingScheme.ITANIUM);
 * // "_Z3addii"
 * ```
 */
object SymbolMangling {

    /**
     * Mangle a function name according to the given scheme.
     *
     * @param name Unqualified function name.
     * @param paramTypes IR parameter types.
     * @param returnType IR return type (used by MSVC, ignored by Itanium).
     * @param scheme The mangling scheme to apply.
     * @return The mangled name, or [name] unchanged for unsupported schemes.
     */
    @JvmStatic
    fun mangleFunction(
        name: String,
        paramTypes: List<Type>,
        returnType: Type = Type.Void,
        scheme: ManglingScheme,
    ): String {
        return when (scheme) {
            ManglingScheme.ITANIUM -> {
                val mangler = ItaniumMangler()
                mangler.mangleSimple(name, paramTypes.map { toItaniumType(it) })
            }
            ManglingScheme.MSVC -> {
                val mangler = MsvcMangler()
                mangler.mangleFunction(name, paramTypes.map { toMsvcType(it) }, toMsvcType(returnType))
            }
            else -> name
        }
    }

    /**
     * Mangle a qualified function name (with namespace/class).
     *
     * @param qualifiers Namespace and class qualifiers (e.g., ["std", "vector"]).
     * @param name The function name.
     * @param paramTypes IR parameter types.
     * @param returnType IR return type.
     * @param scheme The mangling scheme to apply.
     */
    @JvmStatic
    fun mangleQualifiedFunction(
        qualifiers: List<String>,
        name: String,
        paramTypes: List<Type>,
        returnType: Type = Type.Void,
        scheme: ManglingScheme,
    ): String {
        return when (scheme) {
            ManglingScheme.ITANIUM -> {
                val mangler = ItaniumMangler()
                mangler.mangleFunction(
                    qualifiers = qualifiers.toTypedArray(),
                    name = name,
                    paramTypes = paramTypes.map { toItaniumType(it) },
                )
            }
            ManglingScheme.MSVC -> {
                val mangler = MsvcMangler()
                val className = qualifiers.joinToString("::")
                mangler.mangleMethod(
                    className = className,
                    methodName = name,
                    paramTypes = paramTypes.map { toMsvcType(it) },
                    returnType = toMsvcType(returnType),
                )
            }
            else -> name
        }
    }

    /**
     * Maps an IR [Type] to an Itanium mangling type name.
     */
    @JvmStatic
    fun toItaniumType(type: Type): String = when (type) {
        Type.Void -> "void"
        Type.I1 -> "bool"
        Type.I8 -> "char"
        Type.I16 -> "short"
        Type.I32 -> "int"
        Type.I64 -> "long long"
        Type.I128 -> "__int128"
        Type.F16 -> "float"
        Type.F32 -> "float"
        Type.F64 -> "double"
        Type.F80 -> "long double"
        Type.F128 -> "__float128"
        Type.OpaquePointer -> "void*"
        is Type.Pointer -> "${toItaniumType(type.pointee)}*"
        else -> "void*"
    }

    /**
     * Maps an IR [Type] to an MSVC mangling type name.
     */
    @JvmStatic
    fun toMsvcType(type: Type): String = when (type) {
        Type.Void -> "void"
        Type.I1 -> "bool"
        Type.I8 -> "char"
        Type.I16 -> "short"
        Type.I32 -> "int"
        Type.I64 -> "__int64"
        Type.I128 -> "__int128"
        Type.F16 -> "float"
        Type.F32 -> "float"
        Type.F64 -> "double"
        Type.F80 -> "long double"
        Type.F128 -> "long double"
        Type.OpaquePointer -> "void*"
        is Type.Pointer -> "${toMsvcType(type.pointee)}*"
        else -> "void*"
    }
}
