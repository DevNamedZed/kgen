package org.kgen.tools

/**
 * Demangles Itanium C++ ABI mangled names (GCC, Clang on Linux/macOS).
 *
 * Handles the most common patterns:
 * - Functions: `_Z3foov` → `foo()`
 * - Namespaces: `_ZN3foo3barEv` → `foo::bar()`
 * - Operators: `_ZN3fooplERKS_` → `foo::operator+(foo const&)`
 * - Templates (basic): `_Z3fooIiEvT_` → `void foo<int>(int)`
 * - Builtin types: void, bool, char, int, long, float, double, etc.
 * - Qualifiers: const, volatile, pointer, reference, rvalue reference
 *
 * This covers the most common symbols seen in real binaries. Full Itanium ABI
 * demangling is extremely complex — this handles the practical subset.
 */
class ItaniumDemangler : Demangler {

    override fun canDemangle(mangledName: String): Boolean =
        mangledName.startsWith("_Z") || mangledName.startsWith("__Z")

    override fun demangle(mangledName: String): String? {
        if (!canDemangle(mangledName)) return null
        val input = if (mangledName.startsWith("__Z")) mangledName.substring(1) else mangledName
        return try {
            val ctx = DemangleContext(input, 2) // skip "_Z"
            val result = ctx.parseMangledName()
            if (result != null) result else null
        } catch (_: Exception) {
            null
        }
    }

    private class DemangleContext(val s: String, var pos: Int) {
        val substitutions = mutableListOf<String>()

        fun remaining(): Int = s.length - pos
        fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'
        fun advance(): Char = s[pos++]
        fun expect(c: Char): Boolean = if (peek() == c) { pos++; true } else false

        fun parseMangledName(): String? {
            return if (peek() == 'N') {
                parseNestedName()
            } else {
                val name = parseUnqualifiedName() ?: return null
                val params = parseBareFunctionType()
                "$name($params)"
            }
        }

        fun parseNestedName(): String? {
            if (!expect('N')) return null
            val parts = mutableListOf<String>()
            while (pos < s.length && peek() != 'E') {
                val cv = parseCVQualifiers()
                val part = when (peek()) {
                    'S' -> parseSubstitution()
                    'I' -> {
                        // Template args
                        val args = parseTemplateArgs() ?: return null
                        if (parts.isNotEmpty()) {
                            parts[parts.size - 1] = parts.last() + args
                        }
                        continue
                    }
                    'D' -> parseSpecialName()
                    'C' -> parseCtorDtor()
                    else -> parseUnqualifiedName()
                } ?: return null
                parts.add(part)
                substitutions.add(parts.joinToString("::"))
            }
            expect('E')
            val qualifiedName = parts.joinToString("::")
            val params = parseBareFunctionType()
            return "$qualifiedName($params)"
        }

        fun parseUnqualifiedName(): String? {
            val c = peek()
            return when {
                c.isDigit() -> parseSourceName()
                c == 'o' && pos + 1 < s.length && s[pos + 1] == 'p' -> {
                    pos += 2
                    val op = parseOperatorName() ?: return null
                    "operator$op"
                }
                else -> null
            }
        }

        fun parseSourceName(): String? {
            val len = parseNumber() ?: return null
            if (pos + len > s.length) return null
            val name = s.substring(pos, pos + len)
            pos += len
            return name
        }

        fun parseNumber(): Int? {
            if (!peek().isDigit()) return null
            var n = 0
            while (pos < s.length && peek().isDigit()) {
                n = n * 10 + (advance() - '0')
            }
            return n
        }

        fun parseBareFunctionType(): String {
            if (pos >= s.length) return ""
            val params = mutableListOf<String>()
            while (pos < s.length) {
                val type = parseType() ?: break
                if (type != "void" || params.isNotEmpty()) {
                    params.add(type)
                }
            }
            return params.joinToString(", ")
        }

        fun parseType(): String? {
            if (pos >= s.length) return null
            val c = peek()

            // Qualifiers
            if (c == 'K') { pos++; val inner = parseType() ?: return null; return "$inner const" }
            if (c == 'V') { pos++; val inner = parseType() ?: return null; return "$inner volatile" }
            if (c == 'P') { pos++; val inner = parseType() ?: return null; return "$inner*" }
            if (c == 'R') { pos++; val inner = parseType() ?: return null; return "$inner&" }
            if (c == 'O') { pos++; val inner = parseType() ?: return null; return "$inner&&" }

            // Builtin types
            val builtin = parseBuiltinType()
            if (builtin != null) return builtin

            // Named type
            if (c.isDigit()) {
                val name = parseSourceName() ?: return null
                substitutions.add(name)
                return name
            }

            // Nested type
            if (c == 'N') {
                val nested = parseNestedTypeName() ?: return null
                return nested
            }

            // Substitution
            if (c == 'S') {
                return parseSubstitution()
            }

            return null
        }

        fun parseBuiltinType(): String? {
            val c = peek()
            val type = BUILTIN_TYPES[c] ?: return null
            pos++
            return type
        }

        fun parseNestedTypeName(): String? {
            if (!expect('N')) return null
            val parts = mutableListOf<String>()
            while (pos < s.length && peek() != 'E') {
                val part = when {
                    peek() == 'S' -> parseSubstitution()
                    peek().isDigit() -> parseSourceName()
                    else -> return null
                } ?: return null
                parts.add(part)
                substitutions.add(parts.joinToString("::"))
            }
            expect('E')
            return parts.joinToString("::")
        }

        fun parseSubstitution(): String? {
            if (!expect('S')) return null
            if (peek() == '_') {
                pos++
                return substitutions.firstOrNull() ?: "?"
            }
            // Standard substitutions
            return when (peek()) {
                't' -> { pos++; "std" }
                'a' -> { pos++; "std::allocator" }
                's' -> { pos++; "std::string" }
                'i' -> { pos++; "std::istream" }
                'o' -> { pos++; "std::ostream" }
                'd' -> { pos++; "std::iostream" }
                else -> {
                    // Sn_ where n is a base-36 index
                    val idx = parseBase36Index()
                    if (idx >= 0 && idx < substitutions.size) substitutions[idx]
                    else "?"
                }
            }
        }

        fun parseBase36Index(): Int {
            var n = 0
            while (pos < s.length && peek() != '_') {
                val c = advance()
                n = n * 36 + when {
                    c in '0'..'9' -> c - '0'
                    c in 'A'..'Z' -> c - 'A' + 10
                    else -> return -1
                }
            }
            expect('_')
            return n + 1 // S0_ is index 1, S_ is index 0
        }

        fun parseTemplateArgs(): String? {
            if (!expect('I')) return null
            val args = mutableListOf<String>()
            while (pos < s.length && peek() != 'E') {
                val arg = parseType() ?: return null
                args.add(arg)
            }
            expect('E')
            return "<${args.joinToString(", ")}>"
        }

        fun parseOperatorName(): String? = when {
            remaining() >= 2 -> {
                val op = s.substring(pos, pos + 2)
                val name = OPERATOR_NAMES[op]
                if (name != null) { pos += 2; name } else null
            }
            else -> null
        }

        fun parseCVQualifiers(): String {
            val quals = mutableListOf<String>()
            while (pos < s.length) {
                when (peek()) {
                    'K' -> { pos++; quals.add("const") }
                    'V' -> { pos++; quals.add("volatile") }
                    else -> break
                }
            }
            return quals.joinToString(" ")
        }

        fun parseCtorDtor(): String? {
            val c = advance()
            return when {
                c == 'C' -> {
                    advance() // ctor variant (1, 2, 3)
                    substitutions.lastOrNull() ?: "ctor"
                }
                c == 'D' -> {
                    advance() // dtor variant
                    "~${substitutions.lastOrNull() ?: "dtor"}"
                }
                else -> null
            }
        }

        fun parseSpecialName(): String? {
            if (peek() == 'D') {
                return parseCtorDtor()
            }
            return null
        }

        companion object {
            val BUILTIN_TYPES = mapOf(
                'v' to "void",
                'b' to "bool",
                'c' to "char",
                'a' to "signed char",
                'h' to "unsigned char",
                's' to "short",
                't' to "unsigned short",
                'i' to "int",
                'j' to "unsigned int",
                'l' to "long",
                'm' to "unsigned long",
                'x' to "long long",
                'y' to "unsigned long long",
                'f' to "float",
                'd' to "double",
                'e' to "long double",
                'w' to "wchar_t",
                'n' to "__int128",
                'o' to "__uint128",
                'z' to "...",
            )

            val OPERATOR_NAMES = mapOf(
                "nw" to " new",
                "na" to " new[]",
                "dl" to " delete",
                "da" to " delete[]",
                "ps" to "+",
                "ng" to "-",
                "ad" to "&",
                "de" to "*",
                "co" to "~",
                "pl" to "+",
                "mi" to "-",
                "ml" to "*",
                "dv" to "/",
                "rm" to "%",
                "an" to "&",
                "or" to "|",
                "eo" to "^",
                "aS" to "=",
                "pL" to "+=",
                "mI" to "-=",
                "mL" to "*=",
                "dV" to "/=",
                "rM" to "%=",
                "aN" to "&=",
                "oR" to "|=",
                "eO" to "^=",
                "ls" to "<<",
                "rs" to ">>",
                "lS" to "<<=",
                "rS" to ">>=",
                "eq" to "==",
                "ne" to "!=",
                "lt" to "<",
                "gt" to ">",
                "le" to "<=",
                "ge" to ">=",
                "ss" to "<=>",
                "nt" to "!",
                "aa" to "&&",
                "oo" to "||",
                "pp" to "++",
                "mm" to "--",
                "cm" to ",",
                "pm" to "->*",
                "pt" to "->",
                "cl" to "()",
                "ix" to "[]",
            )
        }
    }
}
