package org.kgen.examples.lang

class Lexer(private val source: String) {
    private var pos = 0
    private var line = 1
    private var col = 1

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (pos < source.length) {
            skipWhitespaceAndComments()
            if (pos >= source.length) break
            tokens.add(nextToken())
        }
        tokens.add(Token(Token.Kind.EOF, "", line, col))
        return tokens
    }

    private fun skipWhitespaceAndComments() {
        while (pos < source.length) {
            val ch = source[pos]
            if (ch == ' ' || ch == '\t' || ch == '\r') {
                advance()
            } else if (ch == '\n') {
                advance(); line++; col = 1
            } else if (ch == '/' && pos + 1 < source.length && source[pos + 1] == '/') {
                while (pos < source.length && source[pos] != '\n') advance()
            } else {
                break
            }
        }
    }

    private fun nextToken(): Token {
        val startLine = line
        val startCol = col
        val ch = source[pos]

        // Numbers
        if (ch.isDigit()) return readNumber(startLine, startCol)

        // Strings
        if (ch == '"') return readString(startLine, startCol)

        // Identifiers / keywords
        if (ch.isLetter() || ch == '_') return readIdent(startLine, startCol)

        // Annotations
        if (ch == '@') {
            advance()
            return readIdent(startLine, startCol)
        }

        // Two-char operators
        if (pos + 1 < source.length) {
            val two = source.substring(pos, pos + 2)
            val twoKind = twoCharOps[two]
            if (twoKind != null) {
                advance(); advance()
                return Token(twoKind, two, startLine, startCol)
            }
        }

        // Single-char operators
        val oneKind = oneCharOps[ch]
        if (oneKind != null) {
            advance()
            return Token(oneKind, ch.toString(), startLine, startCol)
        }

        throw LangError("Unexpected character: '$ch'", startLine, startCol)
    }

    private fun readNumber(line: Int, col: Int): Token {
        val start = pos
        while (pos < source.length && source[pos].isDigit()) advance()
        if (pos < source.length && source[pos] == '.' && pos + 1 < source.length && source[pos + 1].isDigit()) {
            advance() // skip '.'
            while (pos < source.length && source[pos].isDigit()) advance()
            return Token(Token.Kind.FLOAT_LIT, source.substring(start, pos), line, col)
        }
        return Token(Token.Kind.INT_LIT, source.substring(start, pos), line, col)
    }

    private fun readString(line: Int, col: Int): Token {
        advance() // skip opening "
        val sb = StringBuilder()
        while (pos < source.length && source[pos] != '"') {
            if (source[pos] == '\\' && pos + 1 < source.length) {
                advance()
                when (source[pos]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    else -> sb.append(source[pos])
                }
            } else {
                sb.append(source[pos])
            }
            advance()
        }
        if (pos >= source.length) throw LangError("Unterminated string", line, col)
        advance() // skip closing "
        return Token(Token.Kind.STRING_LIT, sb.toString(), line, col)
    }

    private fun readIdent(line: Int, col: Int): Token {
        val start = pos
        while (pos < source.length && (source[pos].isLetterOrDigit() || source[pos] == '_')) advance()
        val text = source.substring(start, pos)
        val kind = keywords[text] ?: Token.Kind.IDENT
        return Token(kind, text, line, col)
    }

    private fun advance() {
        pos++; col++
    }

    companion object {
        private val keywords = mapOf(
            "fun" to Token.Kind.FUN,
            "var" to Token.Kind.VAR,
            "val" to Token.Kind.VAL,
            "if" to Token.Kind.IF,
            "else" to Token.Kind.ELSE,
            "while" to Token.Kind.WHILE,
            "return" to Token.Kind.RETURN,
            "true" to Token.Kind.TRUE,
            "false" to Token.Kind.FALSE,
            "native" to Token.Kind.NATIVE,
            "managed" to Token.Kind.MANAGED,
            "extern" to Token.Kind.EXTERN,
            "int" to Token.Kind.INT,
            "float" to Token.Kind.FLOAT,
            "bool" to Token.Kind.BOOL,
            "string" to Token.Kind.STRING,
            "void" to Token.Kind.VOID,
            "and" to Token.Kind.AND,
            "or" to Token.Kind.OR,
            "not" to Token.Kind.NOT,
        )

        private val oneCharOps = mapOf(
            '+' to Token.Kind.PLUS,
            '-' to Token.Kind.MINUS,
            '*' to Token.Kind.STAR,
            '/' to Token.Kind.SLASH,
            '%' to Token.Kind.PERCENT,
            '(' to Token.Kind.LPAREN,
            ')' to Token.Kind.RPAREN,
            '{' to Token.Kind.LBRACE,
            '}' to Token.Kind.RBRACE,
            ',' to Token.Kind.COMMA,
            ':' to Token.Kind.COLON,
            ';' to Token.Kind.SEMICOLON,
            '<' to Token.Kind.LT,
            '>' to Token.Kind.GT,
            '=' to Token.Kind.ASSIGN,
            '!' to Token.Kind.NOT,
        )

        private val twoCharOps = mapOf(
            "==" to Token.Kind.EQ,
            "!=" to Token.Kind.NEQ,
            "<=" to Token.Kind.LTE,
            ">=" to Token.Kind.GTE,
            "&&" to Token.Kind.AND,
            "||" to Token.Kind.OR,
            "->" to Token.Kind.ARROW,
        )
    }
}

class LangError(message: String, val line: Int, val col: Int) :
    RuntimeException("$message at line $line, col $col")
