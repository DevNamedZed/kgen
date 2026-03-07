package org.kgen.binary.jvm

/**
 * JVM access flag constants for classes, fields, methods, and inner classes.
 *
 * Flags can be combined with `or`:
 * ```kotlin
 * val flags = AccessFlags.PUBLIC or AccessFlags.STATIC or AccessFlags.FINAL
 * ```
 */
object AccessFlags {
    // Class, field, method
    const val PUBLIC       = 0x0001
    const val PRIVATE      = 0x0002
    const val PROTECTED    = 0x0004
    const val STATIC       = 0x0008
    const val FINAL        = 0x0010
    const val SYNCHRONIZED = 0x0020  // method
    const val SUPER        = 0x0020  // class (same bit, different meaning)
    const val BRIDGE       = 0x0040  // method
    const val VOLATILE     = 0x0040  // field
    const val VARARGS      = 0x0080  // method
    const val TRANSIENT    = 0x0080  // field
    const val NATIVE       = 0x0100  // method
    const val INTERFACE    = 0x0200  // class
    const val ABSTRACT     = 0x0400  // class, method
    const val STRICT       = 0x0800  // method (strictfp, removed in Java 17)
    const val SYNTHETIC    = 0x1000
    const val ANNOTATION   = 0x2000  // class
    const val ENUM         = 0x4000  // class, field
    const val MODULE       = 0x8000  // class (Java 9+)
    const val MANDATED     = 0x8000  // parameter, module

    fun toString(flags: Int, context: Context = Context.CLASS): String {
        val parts = mutableListOf<String>()
        if (flags and PUBLIC != 0) parts += "public"
        if (flags and PRIVATE != 0) parts += "private"
        if (flags and PROTECTED != 0) parts += "protected"
        if (flags and STATIC != 0) parts += "static"
        if (flags and FINAL != 0) parts += "final"
        when (context) {
            Context.CLASS -> {
                if (flags and SUPER != 0) parts += "super"
                if (flags and INTERFACE != 0) parts += "interface"
                if (flags and ABSTRACT != 0) parts += "abstract"
                if (flags and ANNOTATION != 0) parts += "annotation"
                if (flags and ENUM != 0) parts += "enum"
                if (flags and MODULE != 0) parts += "module"
            }
            Context.METHOD -> {
                if (flags and SYNCHRONIZED != 0) parts += "synchronized"
                if (flags and BRIDGE != 0) parts += "bridge"
                if (flags and VARARGS != 0) parts += "varargs"
                if (flags and NATIVE != 0) parts += "native"
                if (flags and ABSTRACT != 0) parts += "abstract"
                if (flags and STRICT != 0) parts += "strictfp"
            }
            Context.FIELD -> {
                if (flags and VOLATILE != 0) parts += "volatile"
                if (flags and TRANSIENT != 0) parts += "transient"
                if (flags and ENUM != 0) parts += "enum"
            }
        }
        if (flags and SYNTHETIC != 0) parts += "synthetic"
        return parts.joinToString(" ")
    }

    enum class Context { CLASS, METHOD, FIELD }
}
