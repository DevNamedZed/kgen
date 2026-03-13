package org.kgen.runtime.compile

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Generates C header files from kgen IR modules.
 *
 * Maps IR types to C types and produces function declarations
 * for all exported (non-external) functions.
 *
 * ```java
 * String header = CHeaderGenerator.generate(module, "MY_LIB_H");
 * Files.writeString(Path.of("mylib.h"), header);
 * ```
 */
object CHeaderGenerator {

    /**
     * Generate a C header for all exported functions in the module.
     */
    @JvmStatic
    @JvmOverloads
    fun generate(module: Module, guardName: String = "KGEN_EXPORTS_H"): String {
        val sb = StringBuilder()
        sb.appendLine("#ifndef $guardName")
        sb.appendLine("#define $guardName")
        sb.appendLine()
        sb.appendLine("#include <stdint.h>")
        sb.appendLine()
        sb.appendLine("#ifdef __cplusplus")
        sb.appendLine("extern \"C\" {")
        sb.appendLine("#endif")
        sb.appendLine()

        val exported = module.functions.filter {
            it.linkage == Linkage.EXTERNAL && it.blocks.isNotEmpty()
        }

        for (fn in exported) {
            val retType = toCType(fn.returnType)
            val params = if (fn.params.isEmpty()) {
                "void"
            } else {
                fn.params.joinToString(", ") { p ->
                    "${toCType(p.type)} ${sanitizeName(p.name)}"
                }
            }
            sb.appendLine("$retType ${fn.name}($params);")
        }

        sb.appendLine()
        sb.appendLine("#ifdef __cplusplus")
        sb.appendLine("}")
        sb.appendLine("#endif")
        sb.appendLine()
        sb.appendLine("#endif /* $guardName */")

        return sb.toString()
    }

    private fun toCType(type: Type): String = when (type) {
        Type.Void -> "void"
        Type.I1 -> "int8_t"
        Type.I8 -> "int8_t"
        Type.I16 -> "int16_t"
        Type.I32 -> "int32_t"
        Type.I64 -> "int64_t"
        Type.F32 -> "float"
        Type.F64 -> "double"
        is Type.Pointer -> "void*"
        Type.OpaquePointer -> "void*"
        is Type.Array -> "${toCType(type.element)}*"
        is Type.Function -> "void*"
        else -> "void*"
    }

    private fun sanitizeName(name: String): String {
        // C keywords and IR conventions
        return when (name) {
            "int", "long", "short", "char", "float", "double",
            "void", "const", "static", "extern", "struct",
            "union", "enum", "typedef", "return", "if", "else",
            "while", "for", "do", "switch", "case", "break",
            "continue", "default", "goto", "sizeof", "volatile",
            "register", "auto", "signed", "unsigned" -> "${name}_"
            else -> name
        }
    }
}
