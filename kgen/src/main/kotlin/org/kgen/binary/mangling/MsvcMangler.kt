package org.kgen.binary.mangling

/**
 * Mangles symbol names using the MSVC C++ decorated name convention.
 *
 * Produces `?` prefixed symbols compatible with MSVC on Windows.
 *
 * ```kotlin
 * val mangler = MsvcMangler()
 * mangler.mangleFunction("foo", listOf("int", "double"), "void")
 * // → "?foo@@YAXHN@Z"
 * ```
 */
class MsvcMangler {

    /**
     * Mangle a simple (non-class) function.
     *
     * @param name Function name.
     * @param paramTypes Parameter types.
     * @param returnType Return type (default "void").
     * @param callingConvention "cdecl" (default), "stdcall", "thiscall", "fastcall".
     */
    fun mangleFunction(
        name: String,
        paramTypes: List<String> = emptyList(),
        returnType: String = "void",
        callingConvention: String = "cdecl",
    ): String {
        val sb = StringBuilder("?")
        sb.append(name)
        sb.append("@@Y")
        sb.append(encodeCallingConvention(callingConvention))
        sb.append(encodeType(returnType))
        if (paramTypes.isEmpty()) {
            sb.append('X') // void parameter list
        } else {
            for (t in paramTypes) sb.append(encodeType(t))
            sb.append('@')
        }
        sb.append('Z')
        return sb.toString()
    }

    /**
     * Mangle a class method.
     *
     * @param className Fully qualified class name (use "::" for nesting).
     * @param methodName Method name.
     * @param paramTypes Parameter types.
     * @param returnType Return type.
     * @param access "public", "protected", "private".
     * @param isStatic Whether the method is static.
     * @param callingConvention Calling convention.
     */
    fun mangleMethod(
        className: String,
        methodName: String,
        paramTypes: List<String> = emptyList(),
        returnType: String = "void",
        access: String = "public",
        isStatic: Boolean = false,
        callingConvention: String = "thiscall",
    ): String {
        val sb = StringBuilder("?")
        sb.append(methodName)
        sb.append('@')
        val parts = className.split("::")
        for (p in parts) {
            sb.append(p)
            sb.append('@')
        }
        sb.append('@')
        sb.append(encodeAccess(access, isStatic))
        sb.append(encodeCallingConvention(callingConvention))
        sb.append(encodeType(returnType))
        if (paramTypes.isEmpty()) {
            sb.append('X')
        } else {
            for (t in paramTypes) sb.append(encodeType(t))
            sb.append('@')
        }
        sb.append('Z')
        return sb.toString()
    }

    /**
     * Mangle a global variable.
     */
    fun mangleVariable(name: String, type: String): String {
        val sb = StringBuilder("?")
        sb.append(name)
        sb.append("@@3")
        sb.append(encodeType(type))
        sb.append('A')
        return sb.toString()
    }

    fun encodeType(typeName: String): String {
        val trimmed = typeName.trim()
        if (trimmed.endsWith("*")) return "PE${encodeType(trimmed.dropLast(1).trim())}"
        if (trimmed.endsWith("&")) return "AE${encodeType(trimmed.dropLast(1).trim())}"
        if (trimmed.startsWith("const ")) return "${encodeType(trimmed.removePrefix("const ").trim())}B"
        if (trimmed.startsWith("unsigned ")) return encodeUnsignedType(trimmed.removePrefix("unsigned ").trim())

        return BUILTIN_TYPES[trimmed] ?: encodeUserType(trimmed)
    }

    private fun encodeUnsignedType(base: String): String = when (base) {
        "char" -> "E"
        "short" -> "G"
        "int" -> "I"
        "long" -> "K"
        "__int64", "long long" -> "_K"
        else -> encodeUserType("unsigned $base")
    }

    private fun encodeUserType(name: String): String {
        val parts = name.split("::")
        val sb = StringBuilder("V")
        for (p in parts) {
            sb.append(p)
            sb.append('@')
        }
        sb.append('@')
        return sb.toString()
    }

    private fun encodeCallingConvention(cc: String): String = when (cc) {
        "cdecl" -> "A"
        "stdcall" -> "G"
        "thiscall" -> "E"
        "fastcall" -> "I"
        "vectorcall" -> "Q"
        else -> "A"
    }

    private fun encodeAccess(access: String, isStatic: Boolean): String = when {
        access == "private" && !isStatic -> "A"
        access == "private" && isStatic -> "C"
        access == "protected" && !isStatic -> "I"
        access == "protected" && isStatic -> "K"
        access == "public" && !isStatic -> "Q"
        access == "public" && isStatic -> "S"
        else -> "Q"
    }

    companion object {
        private val BUILTIN_TYPES = mapOf(
            "void" to "X",
            "bool" to "_N",
            "char" to "D",
            "signed char" to "C",
            "short" to "F",
            "int" to "H",
            "long" to "J",
            "__int64" to "_J",
            "long long" to "_J",
            "float" to "M",
            "double" to "N",
            "long double" to "O",
            "wchar_t" to "_W",
            "i8" to "C",
            "i16" to "F",
            "i32" to "H",
            "i64" to "_J",
            "u8" to "E",
            "u16" to "G",
            "u32" to "I",
            "u64" to "_K",
            "f32" to "M",
            "f64" to "N",
        )
    }
}
