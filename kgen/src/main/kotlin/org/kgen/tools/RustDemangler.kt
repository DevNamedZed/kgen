package org.kgen.tools

/**
 * Demangles Rust symbol names.
 *
 * Handles two mangling schemes:
 * - **Legacy** (Rust < 1.37): `_ZN` prefix, uses Itanium-like encoding with hash suffix
 * - **v0** (Rust >= 1.37): `_R` prefix, Rust-specific encoding
 *
 * Common patterns:
 * - `_ZN4core3fmt5write17h...E` → `core::fmt::write`
 * - `_RNvNtCs...4core3fmt5write` → `core::fmt::write`
 */
class RustDemangler : Demangler {

    override fun canDemangle(mangledName: String): Boolean =
        mangledName.startsWith("_R") || isRustLegacy(mangledName)

    override fun demangle(mangledName: String): String? {
        if (!canDemangle(mangledName)) return null
        return try {
            if (mangledName.startsWith("_R")) {
                demangleV0(mangledName)
            } else {
                demangleLegacy(mangledName)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isRustLegacy(name: String): Boolean {
        if (!name.startsWith("_ZN") && !name.startsWith("__ZN")) return false
        if (!name.endsWith("E")) return false
        val inner = if (name.startsWith("__ZN")) name.substring(4, name.length - 1)
                    else name.substring(3, name.length - 1)
        return inner.contains("17h") || inner.matches(Regex(".*\\d+h[0-9a-f]{16}.*"))
    }

    private fun demangleLegacy(name: String): String? {
        val inner = if (name.startsWith("__ZN")) name.substring(4, name.length - 1)
                    else name.substring(3, name.length - 1)

        val parts = mutableListOf<String>()
        var pos = 0
        while (pos < inner.length) {
            if (!inner[pos].isDigit()) return null
            var numEnd = pos
            while (numEnd < inner.length && inner[numEnd].isDigit()) numEnd++
            val len = inner.substring(pos, numEnd).toIntOrNull() ?: return null
            pos = numEnd
            if (pos + len > inner.length) return null
            parts.add(inner.substring(pos, pos + len))
            pos += len
        }

        if (parts.isEmpty()) return null

        val last = parts.last()
        if (last.length == 17 && last.startsWith("h") && last.drop(1).all { it in '0'..'9' || it in 'a'..'f' }) {
            parts.removeAt(parts.size - 1)
        }

        return parts.joinToString("::")
    }

    private fun demangleV0(name: String): String? {
        val ctx = V0Context(name, 2) // skip _R
        return ctx.parsePath()
    }

    private class V0Context(val s: String, var pos: Int) {

        fun remaining() = s.length - pos
        fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'
        fun advance(): Char = s[pos++]
        fun expect(c: Char): Boolean = if (peek() == c) { pos++; true } else false

        fun parsePath(): String? {
            if (pos >= s.length) return null
            return when (peek()) {
                'C' -> parseCratePath()
                'N' -> parseNestedPath()
                'M' -> parseInherentImpl()
                'X' -> parseTraitImpl()
                'I' -> parseGenericPath()
                else -> null
            }
        }

        fun parseCratePath(): String? {
            if (!expect('C')) return null
            skipDisambiguator()
            return parseIdentifier()
        }

        fun parseNestedPath(): String? {
            if (!expect('N')) return null
            val namespace = parseNamespace()
            val parent = parsePath() ?: return null
            val name = parseIdentifier() ?: return parent
            return "$parent::$name"
        }

        fun parseNamespace(): Char {
            if (pos >= s.length) return ' '
            val c = peek()
            if (c.isLetter()) { pos++; return c }
            return ' '
        }

        fun parseInherentImpl(): String? {
            if (!expect('M')) return null
            skipDisambiguator()
            return parseType()
        }

        fun parseTraitImpl(): String? {
            if (!expect('X')) return null
            val selfType = parseType() ?: return null
            val traitPath = parsePath() ?: return null
            return "<$selfType as $traitPath>"
        }

        fun parseGenericPath(): String? {
            if (!expect('I')) return null
            val base = parsePath() ?: return null
            val args = mutableListOf<String>()
            while (pos < s.length && peek() != 'E') {
                val arg = parseGenericArg() ?: break
                args.add(arg)
            }
            expect('E')
            return if (args.isEmpty()) base else "$base<${args.joinToString(", ")}>"
        }

        fun parseGenericArg(): String? {
            if (pos >= s.length) return null
            return when (peek()) {
                'K' -> { pos++; "const" }
                'L' -> { pos++; parseNumber().toString() }
                else -> parseType()
            }
        }

        fun parseType(): String? {
            if (pos >= s.length) return null
            val c = peek()
            val basic = BASIC_TYPES[c]
            if (basic != null) { pos++; return basic }
            return when (c) {
                'R' -> { pos++; val inner = parseType() ?: return null; "&$inner" }
                'Q' -> { pos++; val inner = parseType() ?: return null; "&mut $inner" }
                'P' -> { pos++; val inner = parseType() ?: return null; "*const $inner" }
                'O' -> { pos++; val inner = parseType() ?: return null; "*mut $inner" }
                'S' -> { pos++; val inner = parseType() ?: return null; "[$inner]" }
                'T' -> parseTuple()
                'F' -> parseFnType()
                'A' -> parseArray()
                else -> parsePath()
            }
        }

        fun parseTuple(): String? {
            if (!expect('T')) return null
            val elems = mutableListOf<String>()
            while (pos < s.length && peek() != 'E') {
                val t = parseType() ?: break
                elems.add(t)
            }
            expect('E')
            return "(${elems.joinToString(", ")})"
        }

        fun parseFnType(): String? {
            if (!expect('F')) return null
            val isUnsafe = expect('U')
            val params = mutableListOf<String>()
            while (pos < s.length && peek() != 'E') {
                val t = parseType() ?: break
                params.add(t)
            }
            expect('E')
            val ret = if (params.isNotEmpty()) params.removeAt(params.size - 1) else "void"
            val prefix = if (isUnsafe) "unsafe " else ""
            return "${prefix}fn(${params.joinToString(", ")}) -> $ret"
        }

        fun parseArray(): String? {
            if (!expect('A')) return null
            val elemType = parseType() ?: return null
            val len = parseNumber()
            expect('_')
            return "[$elemType; $len]"
        }

        fun parseIdentifier(): String? {
            val isUnicode = expect('u')
            val len = parseDecimalNumber() ?: return null
            if (isUnicode && !expect('_')) return null
            if (pos + len > s.length) return null
            val ident = s.substring(pos, pos + len)
            pos += len
            return ident
        }

        fun skipDisambiguator() {
            if (peek() == 's') {
                pos++
                parseBase62()
            }
        }

        fun parseBase62(): Long {
            if (expect('_')) return 0
            var n = 0L
            while (pos < s.length && peek() != '_') {
                val c = advance()
                val digit = when {
                    c in '0'..'9' -> (c - '0').toLong()
                    c in 'a'..'z' -> (c - 'a' + 10).toLong()
                    c in 'A'..'Z' -> (c - 'A' + 36).toLong()
                    else -> return n
                }
                n = n * 62 + digit
            }
            expect('_')
            return n + 1
        }

        fun parseDecimalNumber(): Int? {
            if (pos >= s.length || !peek().isDigit()) return null
            var n = 0
            while (pos < s.length && peek().isDigit()) {
                n = n * 10 + (advance() - '0')
            }
            return n
        }

        fun parseNumber(): Long {
            if (expect('n')) return -parsePositiveNumber()
            return parsePositiveNumber()
        }

        fun parsePositiveNumber(): Long {
            if (peek() == '0') { pos++; return 0 }
            var n = 0L
            while (pos < s.length && peek().isDigit()) {
                n = n * 10 + (advance() - '0')
            }
            return n
        }

        companion object {
            val BASIC_TYPES = mapOf(
                'b' to "bool",
                'c' to "char",
                'e' to "str",
                'u' to "()",
                'a' to "i8",
                's' to "i16",
                'l' to "i32",
                'x' to "i64",
                'n' to "i128",
                'h' to "u8",
                't' to "u16",
                'm' to "u32",
                'y' to "u64",
                'o' to "u128",
                'f' to "f32",
                'd' to "f64",
                'z' to "!",
                'p' to "_",
                'v' to "...",
            )
        }
    }
}
