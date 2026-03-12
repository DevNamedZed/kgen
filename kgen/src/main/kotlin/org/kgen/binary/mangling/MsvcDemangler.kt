package org.kgen.binary.mangling

/**
 * Demangles MSVC decorated names (Visual C++ on Windows).
 *
 * Handles common patterns:
 * - Functions: `?foo@@YAHXZ` → `int foo(void)`
 * - Classes: `?foo@Bar@@QAEHXZ` → `int Bar::foo(void)`
 * - Calling conventions: __cdecl, __stdcall, __thiscall, __fastcall
 * - Access: public, protected, private
 * - Qualifiers: const, volatile, pointer, reference
 * - Basic types: void, bool, char, int, long, float, double, etc.
 */
class MsvcDemangler : DemanglerStrategy {

    override fun canDemangle(mangledName: String): Boolean =
        mangledName.startsWith("?")

    override fun demangle(mangledName: String): String? {
        if (!canDemangle(mangledName)) return null
        return try {
            val ctx = MsvcContext(mangledName, 1)
            ctx.parse()
        } catch (_: Exception) {
            null
        }
    }

    private class MsvcContext(val s: String, var pos: Int) {
        val nameFragments = mutableListOf<String>()

        fun remaining() = s.length - pos
        fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'
        fun advance(): Char = s[pos++]
        fun expect(c: Char): Boolean = if (peek() == c) { pos++; true } else false

        fun parse(): String? {
            val qualifiedName = parseQualifiedName() ?: return null
            if (pos >= s.length || peek() != '@') return qualifiedName
            expect('@')

            val accessAndKind = parseAccessSpecifier()
            val callingConv = parseCallingConvention()
            val returnType = parseType()
            val params = parseParams()

            val prefix = buildString {
                if (accessAndKind != null) append("$accessAndKind ")
                if (returnType != null) append("$returnType ")
            }

            return "${prefix.trimEnd()} $qualifiedName($params)".trim()
        }

        fun parseQualifiedName(): String? {
            val parts = mutableListOf<String>()
            while (pos < s.length && peek() != '@') {
                if (peek() == '?') {
                    pos++
                    val special = parseSpecialName()
                    if (special != null) parts.add(special)
                    continue
                }
                val name = parseName() ?: break
                parts.add(name)
                nameFragments.add(name)
                if (!expect('@')) break
            }
            return if (parts.isEmpty()) null else parts.reversed().joinToString("::")
        }

        fun parseName(): String? {
            if (pos >= s.length) return null
            val c = peek()
            if (c.isDigit()) {
                val idx = c - '0'
                pos++
                return nameFragments.getOrNull(idx)
            }
            val start = pos
            while (pos < s.length && peek() != '@' && peek() != '?') pos++
            return if (pos > start) s.substring(start, pos) else null
        }

        fun parseSpecialName(): String? {
            if (pos >= s.length) return null
            return when {
                peek() == '0' -> { pos++; nameFragments.lastOrNull()?.let { "$it" } ?: "ctor" }
                peek() == '1' -> { pos++; nameFragments.lastOrNull()?.let { "~$it" } ?: "dtor" }
                peek() == 'B' -> { pos++; "operator type" }
                peek() == 'H' -> { pos++; "operator++" }
                peek() == 'G' -> { pos++; "operator--" }
                else -> null
            }
        }

        fun parseAccessSpecifier(): String? {
            if (pos >= s.length) return null
            val c = peek()
            val result = when (c) {
                'A' -> "private"
                'B' -> "private"
                'C' -> "private: static"
                'D' -> "private: static"
                'E' -> "private: virtual"
                'F' -> "private: virtual"
                'I' -> "protected"
                'J' -> "protected"
                'K' -> "protected: static"
                'L' -> "protected: static"
                'M' -> "protected: virtual"
                'N' -> "protected: virtual"
                'Q' -> "public"
                'R' -> "public"
                'S' -> "public: static"
                'T' -> "public: static"
                'U' -> "public: virtual"
                'V' -> "public: virtual"
                'Y' -> null // non-member
                else -> return null
            }
            pos++
            return result
        }

        fun parseCallingConvention(): String? {
            if (pos >= s.length) return null
            val result = when (peek()) {
                'A' -> "__cdecl"
                'E' -> "__thiscall"
                'G' -> "__stdcall"
                'I' -> "__fastcall"
                else -> return null
            }
            pos++
            return result
        }

        fun parseType(): String? {
            if (pos >= s.length) return null
            val c = peek()
            val basic = BASIC_TYPES[c]
            if (basic != null) { pos++; return basic }

            return when (c) {
                'P' -> { pos++; val inner = parseType() ?: return null; "$inner*" }
                'A' -> { pos++; val inner = parseType() ?: return null; "$inner&" }
                'Q' -> { pos++; val inner = parseType() ?: return null; "$inner const*" }
                else -> null
            }
        }

        fun parseParams(): String {
            if (pos >= s.length) return ""
            val params = mutableListOf<String>()
            while (pos < s.length && peek() != '@' && peek() != 'Z') {
                val type = parseType() ?: break
                if (type != "void" || params.isNotEmpty()) params.add(type)
            }
            if (peek() == 'Z') pos++
            return params.joinToString(", ")
        }

        companion object {
            val BASIC_TYPES = mapOf(
                'X' to "void",
                'D' to "char",
                'C' to "signed char",
                'E' to "unsigned char",
                'F' to "short",
                'G' to "unsigned short",
                'H' to "int",
                'I' to "unsigned int",
                'J' to "long",
                'K' to "unsigned long",
                'M' to "float",
                'N' to "double",
                'O' to "long double",
                '_' to "bool",
                'Z' to "...",
            )
        }
    }
}
