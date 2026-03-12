package org.kgen.binary.mangling

/**
 * Mangles symbol names using the Itanium C++ ABI encoding.
 *
 * Produces `_Z` prefixed symbols compatible with GCC/Clang on Linux/macOS.
 *
 * ```kotlin
 * val mangler = ItaniumMangler()
 * mangler.mangleFunction("std", "vector", "push_back", listOf("int"))
 * // → "_ZNSt6vector9push_backEi"
 *
 * mangler.mangleSimple("foo", listOf("int", "double"))
 * // → "_Z3fooid"
 * ```
 */
class ItaniumMangler {

    /**
     * Mangle a simple (non-namespaced) function.
     *
     * @param name The unqualified function name.
     * @param paramTypes Parameter type names (e.g., "int", "double", "char*").
     */
    fun mangleSimple(name: String, paramTypes: List<String> = emptyList()): String {
        val sb = StringBuilder("_Z")
        sb.append(name.length)
        sb.append(name)
        if (paramTypes.isEmpty()) {
            sb.append('v') // void parameter list
        } else {
            for (t in paramTypes) sb.append(encodeType(t))
        }
        return sb.toString()
    }

    /**
     * Mangle a function with nested name (namespace/class qualification).
     *
     * @param qualifiers Namespace and class qualifiers (e.g., ["std", "vector"]).
     * @param name The function name.
     * @param paramTypes Parameter type names.
     */
    fun mangleFunction(vararg qualifiers: String, name: String, paramTypes: List<String> = emptyList()): String {
        val sb = StringBuilder("_Z")
        if (qualifiers.isNotEmpty()) {
            sb.append('N')
            for (q in qualifiers) {
                sb.append(q.length)
                sb.append(q)
            }
            sb.append(name.length)
            sb.append(name)
            sb.append('E')
        } else {
            sb.append(name.length)
            sb.append(name)
        }
        if (paramTypes.isEmpty()) {
            sb.append('v')
        } else {
            for (t in paramTypes) sb.append(encodeType(t))
        }
        return sb.toString()
    }

    /**
     * Mangle a constructor.
     *
     * @param qualifiers Namespace and class qualifiers.
     * @param paramTypes Constructor parameter types.
     * @param kind 1 = complete constructor (C1), 2 = base constructor (C2).
     */
    fun mangleConstructor(vararg qualifiers: String, paramTypes: List<String> = emptyList(), kind: Int = 1): String {
        val sb = StringBuilder("_ZN")
        for (q in qualifiers) {
            sb.append(q.length)
            sb.append(q)
        }
        sb.append("C$kind")
        sb.append('E')
        if (paramTypes.isEmpty()) {
            sb.append('v')
        } else {
            for (t in paramTypes) sb.append(encodeType(t))
        }
        return sb.toString()
    }

    /**
     * Mangle a destructor.
     *
     * @param qualifiers Namespace and class qualifiers.
     * @param kind 0 = deleting (D0), 1 = complete (D1), 2 = base (D2).
     */
    fun mangleDestructor(vararg qualifiers: String, kind: Int = 1): String {
        val sb = StringBuilder("_ZN")
        for (q in qualifiers) {
            sb.append(q.length)
            sb.append(q)
        }
        sb.append("D$kind")
        sb.append("Ev")
        return sb.toString()
    }

    /**
     * Mangle a variable (global or static member).
     */
    fun mangleVariable(vararg qualifiers: String, name: String): String {
        val sb = StringBuilder("_Z")
        if (qualifiers.isNotEmpty()) {
            sb.append('N')
            for (q in qualifiers) {
                sb.append(q.length)
                sb.append(q)
            }
            sb.append(name.length)
            sb.append(name)
            sb.append('E')
        } else {
            sb.append(name.length)
            sb.append(name)
        }
        return sb.toString()
    }

    /**
     * Encode a type name into Itanium mangling format.
     */
    fun encodeType(typeName: String): String {
        // Handle pointer/reference modifiers
        val trimmed = typeName.trim()
        if (trimmed.endsWith("*")) return "P${encodeType(trimmed.dropLast(1))}"
        if (trimmed.endsWith("&")) return "R${encodeType(trimmed.dropLast(1))}"
        if (trimmed.endsWith("&&")) return "O${encodeType(trimmed.dropLast(2))}"
        if (trimmed.startsWith("const ")) return "K${encodeType(trimmed.removePrefix("const "))}"
        if (trimmed.startsWith("unsigned ")) return encodeUnsignedType(trimmed.removePrefix("unsigned "))

        return BUILTIN_TYPES[trimmed] ?: encodeUserType(trimmed)
    }

    private fun encodeUnsignedType(base: String): String = when (base.trim()) {
        "char" -> "h"
        "short" -> "t"
        "int" -> "j"
        "long" -> "m"
        "long long" -> "y"
        "__int128" -> "o"
        else -> encodeUserType("unsigned $base")
    }

    private fun encodeUserType(name: String): String {
        val parts = name.split("::")
        return if (parts.size > 1) {
            val sb = StringBuilder("N")
            for (p in parts) {
                sb.append(p.length)
                sb.append(p)
            }
            sb.append('E')
            sb.toString()
        } else {
            "${name.length}$name"
        }
    }

    companion object {
        private val BUILTIN_TYPES = mapOf(
            "void" to "v",
            "bool" to "b",
            "char" to "c",
            "signed char" to "a",
            "short" to "s",
            "int" to "i",
            "long" to "l",
            "long long" to "x",
            "__int128" to "n",
            "float" to "f",
            "double" to "d",
            "long double" to "e",
            "__float128" to "g",
            "wchar_t" to "w",
            "char8_t" to "Du",
            "char16_t" to "Ds",
            "char32_t" to "Di",
            "..." to "z",
            "auto" to "Da",
            "decltype(auto)" to "Dc",
            "std::nullptr_t" to "Dn",
            "i8" to "a",
            "i16" to "s",
            "i32" to "i",
            "i64" to "x",
            "u8" to "h",
            "u16" to "t",
            "u32" to "j",
            "u64" to "y",
            "f32" to "f",
            "f64" to "d",
        )
    }
}
