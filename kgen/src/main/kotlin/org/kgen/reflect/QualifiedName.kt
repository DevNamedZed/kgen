package org.kgen.reflect

/**
 * An intelligent, navigable type name that understands CLR, JVM, and C++ naming conventions.
 *
 * Parses generic arity (CLR `List\`1`), nested types (CLR `+`, JVM `$`), namespace
 * separators (`.`, `/`, `::`), template arguments (C++ `<...>`), and JVM type descriptors
 * (`Ljava/lang/String;`, `[I`, `Z`, etc.).
 *
 * ```java
 * var name = QualifiedName.parse("System.Collections.Generic.List`1");
 * name.name();          // "List`1"
 * name.simpleName();    // "List"
 * name.namespace();     // "System.Collections.Generic"
 * name.genericArity();  // 1
 *
 * var name = QualifiedName.parse("java/util/Map$Entry");
 * name.isNested();      // true
 * name.outerType();     // QualifiedName("java.util.Map")
 * name.innerName();     // "Entry"
 *
 * // JVM descriptors
 * var name = QualifiedName.fromDescriptor("[Ljava/lang/String;");
 * name.isArray();       // true
 * name.elementType();   // QualifiedName("java.lang.String")
 * ```
 */
class QualifiedName private constructor(
    private val ns: String?,
    private val typeName: String,
    private val outer: QualifiedName?,
    private val arrayElement: QualifiedName? = null,
    private val arrayDimensions: Int = 0,
) {
    fun name(): String = typeName

    fun simpleName(): String {
        val backtick = typeName.indexOf('`')
        return if (backtick >= 0) typeName.substring(0, backtick) else typeName
    }

    fun namespace(): String? = ns

    fun fullName(): String = buildString {
        if (arrayElement != null) {
            append(arrayElement.fullName())
            repeat(arrayDimensions) { append("[]") }
        } else if (outer != null) {
            append(outer.fullName())
            append(".")
            append(typeName)
        } else {
            if (ns != null) {
                append(ns)
                append(".")
            }
            append(typeName)
        }
    }

    fun isGeneric(): Boolean = genericArity() > 0 || genericArguments().isNotEmpty()

    fun genericArity(): Int {
        val backtick = typeName.indexOf('`')
        if (backtick >= 0) {
            return typeName.substring(backtick + 1).toIntOrNull() ?: 0
        }
        return parsedGenericArgs?.size ?: 0
    }

    fun genericArguments(): List<QualifiedName> = parsedGenericArgs ?: emptyList()

    fun isNested(): Boolean = outer != null

    fun outerType(): QualifiedName? = outer

    fun innerName(): String? = if (outer != null) typeName else null

    fun isArray(): Boolean = arrayElement != null

    fun elementType(): QualifiedName? = arrayElement

    fun arrayDimensions(): Int = arrayDimensions

    fun isPrimitive(): Boolean = JVM_PRIMITIVES.containsValue(this) || CLR_PRIMITIVES.containsValue(this)

    fun segments(): List<String> {
        if (arrayElement != null) return arrayElement.segments()
        val result = mutableListOf<String>()
        if (ns != null) result.addAll(ns.split("."))
        if (outer != null) {
            result.addAll(outer.segments())
        }
        result.add(typeName)
        return result
    }

    fun matches(other: QualifiedName): Boolean = fullName() == other.fullName()

    private var parsedGenericArgs: List<QualifiedName>? = null

    override fun toString(): String = fullName()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QualifiedName) return false
        return fullName() == other.fullName()
    }

    override fun hashCode(): Int = fullName().hashCode()

    companion object {
        // JVM primitive type names
        @JvmField val BOOLEAN = QualifiedName(null, "boolean", null)
        @JvmField val BYTE = QualifiedName(null, "byte", null)
        @JvmField val CHAR = QualifiedName(null, "char", null)
        @JvmField val SHORT = QualifiedName(null, "short", null)
        @JvmField val INT = QualifiedName(null, "int", null)
        @JvmField val LONG = QualifiedName(null, "long", null)
        @JvmField val FLOAT = QualifiedName(null, "float", null)
        @JvmField val DOUBLE = QualifiedName(null, "double", null)
        @JvmField val VOID = QualifiedName(null, "void", null)

        // CLR built-in type aliases
        @JvmField val CLR_BOOL = QualifiedName("System", "Boolean", null)
        @JvmField val CLR_BYTE = QualifiedName("System", "Byte", null)
        @JvmField val CLR_SBYTE = QualifiedName("System", "SByte", null)
        @JvmField val CLR_CHAR = QualifiedName("System", "Char", null)
        @JvmField val CLR_INT16 = QualifiedName("System", "Int16", null)
        @JvmField val CLR_INT32 = QualifiedName("System", "Int32", null)
        @JvmField val CLR_INT64 = QualifiedName("System", "Int64", null)
        @JvmField val CLR_UINT16 = QualifiedName("System", "UInt16", null)
        @JvmField val CLR_UINT32 = QualifiedName("System", "UInt32", null)
        @JvmField val CLR_UINT64 = QualifiedName("System", "UInt64", null)
        @JvmField val CLR_FLOAT = QualifiedName("System", "Single", null)
        @JvmField val CLR_DOUBLE = QualifiedName("System", "Double", null)
        @JvmField val CLR_STRING = QualifiedName("System", "String", null)
        @JvmField val CLR_OBJECT = QualifiedName("System", "Object", null)
        @JvmField val CLR_VOID = QualifiedName("System", "Void", null)
        @JvmField val CLR_INTPTR = QualifiedName("System", "IntPtr", null)

        // JVM well-known types
        @JvmField val JVM_STRING = QualifiedName("java.lang", "String", null)
        @JvmField val JVM_OBJECT = QualifiedName("java.lang", "Object", null)
        @JvmField val JVM_CLASS = QualifiedName("java.lang", "Class", null)

        private val JVM_PRIMITIVES = mapOf(
            'Z' to BOOLEAN, 'B' to BYTE, 'C' to CHAR,
            'S' to SHORT, 'I' to INT, 'J' to LONG,
            'F' to FLOAT, 'D' to DOUBLE, 'V' to VOID,
        )

        private val CLR_PRIMITIVES = mapOf(
            "bool" to CLR_BOOL, "byte" to CLR_BYTE, "sbyte" to CLR_SBYTE,
            "char" to CLR_CHAR, "short" to CLR_INT16, "int" to CLR_INT32,
            "long" to CLR_INT64, "ushort" to CLR_UINT16, "uint" to CLR_UINT32,
            "ulong" to CLR_UINT64, "float" to CLR_FLOAT, "double" to CLR_DOUBLE,
            "string" to CLR_STRING, "object" to CLR_OBJECT, "void" to CLR_VOID,
            "nint" to CLR_INTPTR,
        )

        @JvmStatic
        fun of(fullName: String): QualifiedName = parse(fullName)

        @JvmStatic
        fun of(namespace: String, name: String): QualifiedName {
            return QualifiedName(namespace.ifEmpty { null }, name, null)
        }

        /**
         * Parses a JVM type descriptor like `Ljava/lang/String;`, `[I`, `[[D`, `Z`, etc.
         */
        @JvmStatic
        fun fromDescriptor(descriptor: String): QualifiedName {
            val trimmed = descriptor.trim()
            if (trimmed.isEmpty()) throw IllegalArgumentException("Empty descriptor")
            return parseDescriptor(trimmed, 0).first
        }

        private fun parseDescriptor(desc: String, pos: Int): Pair<QualifiedName, Int> {
            if (pos >= desc.length) throw IllegalArgumentException("Unexpected end of descriptor")
            return when (desc[pos]) {
                '[' -> {
                    var dims = 0
                    var i = pos
                    while (i < desc.length && desc[i] == '[') { dims++; i++ }
                    val (element, end) = parseDescriptor(desc, i)
                    QualifiedName(null, element.fullName(), null, element, dims) to end
                }
                'L' -> {
                    val semi = desc.indexOf(';', pos)
                    if (semi < 0) throw IllegalArgumentException("Missing ';' in descriptor: $desc")
                    val internal = desc.substring(pos + 1, semi)
                    parse(internal) to (semi + 1)
                }
                else -> {
                    val prim = JVM_PRIMITIVES[desc[pos]]
                        ?: throw IllegalArgumentException("Unknown descriptor char: ${desc[pos]}")
                    prim to (pos + 1)
                }
            }
        }

        /**
         * Resolves a CLR keyword alias (like `int`, `string`, `bool`) to its full System type name.
         * Returns null if the alias is not recognized.
         */
        @JvmStatic
        fun fromClrAlias(alias: String): QualifiedName? = CLR_PRIMITIVES[alias]

        @JvmStatic
        fun parse(input: String): QualifiedName {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) throw IllegalArgumentException("Empty qualified name")

            // C++ template: extract generic args from <...>
            val templateStart = findTemplateStart(trimmed)
            if (templateStart >= 0) {
                return parseCppTemplate(trimmed, templateStart)
            }

            // CLR nested type: Outer+Inner (split on last + for chained nesting)
            val plusIdx = trimmed.lastIndexOf('+')
            if (plusIdx >= 0) {
                val outerPart = trimmed.substring(0, plusIdx)
                val innerPart = trimmed.substring(plusIdx + 1)
                val outer = parse(outerPart)
                return QualifiedName(null, innerPart, outer)
            }

            // JVM inner class: Outer$Inner (only if also has /)
            if (trimmed.contains('/') && trimmed.contains('$')) {
                val dollarIdx = trimmed.indexOf('$')
                val outerPart = trimmed.substring(0, dollarIdx)
                val innerPart = trimmed.substring(dollarIdx + 1)
                val outer = parse(outerPart)
                return QualifiedName(null, innerPart, outer)
            }

            // JVM format: java/lang/String → namespace "java.lang", name "String"
            if (trimmed.contains('/')) {
                val lastSlash = trimmed.lastIndexOf('/')
                val ns = trimmed.substring(0, lastSlash).replace('/', '.')
                val name = trimmed.substring(lastSlash + 1)
                return QualifiedName(ns.ifEmpty { null }, name, null)
            }

            // C++ format: std::vector → namespace "std", name "vector"
            if (trimmed.contains("::")) {
                val lastSep = trimmed.lastIndexOf("::")
                val ns = trimmed.substring(0, lastSep)
                val name = trimmed.substring(lastSep + 2)
                return QualifiedName(ns.ifEmpty { null }, name, null)
            }

            // CLR / general dot format: System.String
            val lastDot = trimmed.lastIndexOf('.')
            if (lastDot >= 0) {
                val ns = trimmed.substring(0, lastDot)
                val name = trimmed.substring(lastDot + 1)
                return QualifiedName(ns.ifEmpty { null }, name, null)
            }

            // Simple name: "strlen"
            return QualifiedName(null, trimmed, null)
        }

        private fun findTemplateStart(input: String): Int {
            var depth = 0
            for (i in input.indices) {
                when (input[i]) {
                    '<' -> {
                        if (depth == 0) return i
                        depth++
                    }
                    '>' -> depth--
                }
            }
            return -1
        }

        private fun parseCppTemplate(input: String, templateStart: Int): QualifiedName {
            val basePart = input.substring(0, templateStart)
            val templateContent = input.substring(templateStart + 1, input.length - 1)

            val base = parse(basePart)
            val args = splitTemplateArgs(templateContent).map { parse(it.trim()) }
            base.parsedGenericArgs = args
            return base
        }

        private fun splitTemplateArgs(content: String): List<String> {
            val args = mutableListOf<String>()
            var depth = 0
            var start = 0
            for (i in content.indices) {
                when (content[i]) {
                    '<' -> depth++
                    '>' -> depth--
                    ',' -> if (depth == 0) {
                        args.add(content.substring(start, i))
                        start = i + 1
                    }
                }
            }
            if (start < content.length) {
                args.add(content.substring(start))
            }
            return args
        }
    }
}
